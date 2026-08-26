/*
 * gtk-preview-render.c — Headless GTK4 .ui → PNG renderer
 *
 * Build:  cc $(pkg-config --cflags --libs gtk4 libadwaita-1 graphene-1.0) -o gtk-preview-render gtk-preview-render.c
 * Build:  flatpak run --command=bash --filesystem=$PWD --filesystem=/tmp org.gnome.Sdk//50 -c 'cc $(pkg-config --cflags --libs gtk4 libadwaita-1 graphene-1.0) -o /tmp/gtk-preview-render gtk-preview-render.c'
 * Run:    gtk-preview-render <input.ui> <output.png> [width] [height]
 */

#include <stdlib.h>
#include <adwaita.h>
#include <glib.h>
#include <graphene.h>

typedef struct {
    const char *output_path;
    int width;
    int height;
    GMainLoop *loop;
} RenderContext;

/**
 * Parses and load a single GTK interface from a specific file,
 * allowing us to further render the UI to preview
 *
 * @param in_path input file to read interface
 * @param error error returned by add_from_file
 * @return GTKBuilder with loaded interface
 */
static GtkBuilder *load_ui_file(const char *in_path, GError **error) {
    GtkBuilder *builder = gtk_builder_new();

    if (!gtk_builder_add_from_file(builder, in_path, error)) {
        g_printerr("render: %s\n", (*error)->message);
        return NULL;
    }

    return builder;
}

/**
 * Search across the GtkBuilder for a paintable widget
 * or a Window object and return it
 *
 * @param builder Builder with current UI loaded
 * @return The first widget found while looping
 */
static GtkWidget *get_first_widget(GtkBuilder *builder) {
    GSList *widget_list = gtk_builder_get_objects(builder);
    GSList *iterator = widget_list;
    GtkWidget *widget = NULL;

    for (; iterator; iterator = g_slist_next(iterator)) {
        if (!GTK_IS_WIDGET(iterator->data)) continue;

        // Checks if widget was already set - to do it just once
        if (!widget) widget = GTK_WIDGET(iterator->data);

        if (GTK_IS_WINDOW(widget)) break;
    }

    g_slist_free(widget_list);

    return widget;
}

/**
 * Wraps the current GTK Builder inside a Window instance,
 * allowing our renderer to receive the correct XML struct
 *
 * @param builder loader used to process the UI
 * @param in_path path from the UI file loaded
 * @return a Window containing the entire interface
 */
static GtkWidget *wrap_root_window(GtkBuilder *builder, const char *in_path) {
    GtkWidget *window = get_first_widget(builder);

    if (!window) {
        g_printerr("render: no widget found in %s\n", in_path);
        return NULL;
    }

    // Just slap/wrap a window when we do not detect one
    if (!GTK_IS_WINDOW(window)) {
        GtkWindow *frame = GTK_WINDOW(gtk_window_new());
        gtk_window_set_child(frame, window);
        window = GTK_WIDGET(frame);
    }

    return window;
}

/**
 * Renders a single widget and screenshot its state before
 * cleaning itself and the renderer
 *
 * @param widget widget to draw and snapshot
 * @param clock frame clock used internally by toolkit
 * @param data pointer data to pass parameters
 * @return a value to remove current renderer from tree
 */
static gboolean screenshot_frame(GtkWidget *widget, GdkFrameClock *clock, gpointer data) {
    const RenderContext *ctx = data;

    // First tick: let the frame clock run through at least one full
    // Paint cycle so the widget tree is laid out and rendered.
    static int tick_count = 0;
    if (tick_count++ == 0)
        return G_SOURCE_CONTINUE;

    // Populate the snapshot by calling the widget's snapshot vfunc
    GtkSnapshot *snapshot = gtk_snapshot_new();
    const GtkWidgetClass *klass = GTK_WIDGET_GET_CLASS(widget);
    klass->snapshot(widget, snapshot);

    GskRenderNode *node = gtk_snapshot_to_node(snapshot);
    if (!node) {
        g_printerr("render: no render node found\n");
        g_object_unref(snapshot);
        return G_SOURCE_REMOVE;
    }

    GskRenderer *renderer = gtk_native_get_renderer(gtk_widget_get_native(widget));
    const graphene_rect_t bounds = GRAPHENE_RECT_INIT(0, 0, ctx->width, ctx->height);
    GdkTexture *texture = gsk_renderer_render_texture(renderer, node, &bounds);

    gsk_render_node_unref(node);

    if (!texture) {
        g_printerr("render: no texture found\n");
        g_object_unref(snapshot);
        return G_SOURCE_REMOVE;
    }

    gdk_texture_save_to_png(texture, ctx->output_path);
    g_clear_object(&texture);
    g_object_unref(snapshot);

    g_main_loop_quit(ctx->loop);
    return G_SOURCE_REMOVE;
}

/**
 * Initialize the GTK/Adwaita main loop and asks for
 * a tick callback to be called right after the
 * first app/window paint
 *
 * @param window Current window to be screenshot
 * @param ctx Render context with dimensions and output path
 */
static void initialize_main_loop(GtkWidget *window, RenderContext *ctx) {
    gtk_widget_add_tick_callback(window, screenshot_frame, ctx, NULL);

    gtk_widget_set_size_request(window, ctx->width, ctx->height);
    gtk_widget_set_visible(window, TRUE);

    ctx->loop = g_main_loop_new(NULL, FALSE);
    g_main_loop_run(ctx->loop);
    g_main_loop_unref(ctx->loop);
}

int main(const int argc, char *argv[]) {
    // Just fail with we do not have at least three arguments
    if (argc < 3) {
        return EXIT_FAILURE;
    }

    RenderContext ctx = {
        .output_path = argv[2],
        .width = 600,
        .height = 800,
    };

    // Collect input file and output path
    const char *in_path = argv[1];

    // Desired dimensions to screenshot
    if (argc > 3) ctx.width = atoi(argv[3]);
    if (argc > 4) ctx.height = atoi(argv[4]);

    adw_init();

    GError *error = NULL;
    GtkBuilder *builder = load_ui_file(in_path, &error);

    if (!builder) { return EXIT_FAILURE; }

    GtkWidget *window = wrap_root_window(builder, in_path);
    if (!window) {
        g_clear_object(&builder);
        return EXIT_FAILURE;
    }

    initialize_main_loop(window, &ctx);
    g_clear_object(&builder);

    return EXIT_SUCCESS;
}
