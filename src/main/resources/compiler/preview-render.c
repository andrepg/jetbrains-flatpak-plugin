/*
 * preview-render.c — GTK4 headless rendering engine
 *
 * Loads a `.ui` XML file via ui_filter_xml (which rewrites templates and
 * drops custom classes), resolves a renderable root widget, presents it
 * in a headless GTK4/libadwaita display, waits for a painted frame, and
 * saves a PNG snapshot.
 *
 * Compile (inside GNOME SDK 50):
 *   cc -c $(pkg-config --cflags gtk4 libadwaita-1) -o preview-render.o preview-render.c
 */

#include <stdlib.h>
#include <adwaita.h>
#include <graphene.h>
#include "ui-xml.h"
#include "preview-render.h"

/* ------------------------------------------------------------------ */
/*  Internal types                                                     */
/* ------------------------------------------------------------------ */

typedef struct {
    const char *output_path;
    GtkWidget *root;
    int width;
    int height;
    int tick_count;
    gboolean done;
    GMainLoop *loop;
    GError **error;
} RenderContext;

/* ------------------------------------------------------------------ */
/*  Load                                                               */
/* ------------------------------------------------------------------ */

static char *read_file_contents(const char *path, GError **error) {
    char *content = NULL;
    size_t length = 0;
    if (!g_file_get_contents(path, &content, &length, error)) return NULL;
    return g_strndup(content, length);
}

static GtkBuilder *load_ui_file(const char *in_path, GError **error) {
    g_autofree char *raw_xml = read_file_contents(in_path, error);
    if (!raw_xml) return NULL;

    g_autofree char *filtered_xml = ui_filter_xml(raw_xml, error);
    if (!filtered_xml) return NULL;

    GtkBuilder *builder = gtk_builder_new();
    if (!gtk_builder_add_from_string(builder, filtered_xml, -1, error)) {
        g_object_unref(builder);
        return NULL;
    }

    return builder;
}

/* ------------------------------------------------------------------ */
/*  Present                                                            */
/* ------------------------------------------------------------------ */

/* Return the first renderable toplevel widget from the builder, preferring
 * a GtkWindow when one exists (it can be presented standalone without
 * a wrapper). Parented widgets (GtkBuilder child objects reachable through
 * `gtk_builder_get_objects`) are skipped, so the picked root is always
 * parentless and safe to set as a window child. */
static GtkWidget *get_first_widget(GtkBuilder *builder) {
    GSList *object_list = gtk_builder_get_objects(builder);
    GtkWidget *first_widget = NULL;

    for (GSList *iter = object_list; iter; iter = g_slist_next(iter)) {
        if (!GTK_IS_WIDGET(iter->data)) continue;
        GtkWidget *widget = GTK_WIDGET(iter->data);
        if (gtk_widget_get_parent(widget))
            continue;
        if (!first_widget)
            first_widget = widget;
        if (GTK_IS_WINDOW(widget)) {
            first_widget = widget;
            break;
        }
    }

    g_slist_free(object_list);
    return first_widget;
}

/* Resolve the root widget for rendering. Windows are returned directly;
 * everything else is wrapped in a transient GtkWindow so GTK can lay it
 * out. Adwaita presenter dialogs are unwrapped to their child. */
static GtkWidget *resolve_render_root(GtkBuilder *builder,
                                      const char *in_path,
                                      GError **error) {
    GtkWidget *root = get_first_widget(builder);
    if (!root) {
        g_set_error(error, G_IO_ERROR, G_IO_ERROR_FAILED,
                    "no widget found in %s", in_path);
        return NULL;
    }

    if (GTK_IS_WINDOW(root)) return root;

    if (ADW_IS_DIALOG(root)) {
        GtkWidget *content = adw_dialog_get_child(ADW_DIALOG(root));
        if (!content) {
            g_set_error(error, G_IO_ERROR, G_IO_ERROR_FAILED,
                        "%s is an empty AdwDialog", in_path);
            return NULL;
        }
        g_printerr("render: %s roots at an AdwDialog; "
                   "rendering its child (best-effort)\n", in_path);
        root = content;
    }

    GtkWidget *wrapper_window = gtk_window_new();
    gtk_window_set_decorated(GTK_WINDOW(wrapper_window), FALSE);
    gtk_window_set_resizable(GTK_WINDOW(wrapper_window), FALSE);
    gtk_window_set_child(GTK_WINDOW(wrapper_window), root);
    return wrapper_window;
}

/* ------------------------------------------------------------------ */
/*  Capture                                                            */
/* ------------------------------------------------------------------ */

/* Snapshot + render the widget tree into a GdkTexture, save to PNG, and
 * quit the main loop. Returns TRUE once a texture was saved. */
static void capture_and_save(GtkWidget *widget, RenderContext *context) {
    GtkSnapshot *snapshot = gtk_snapshot_new();
    GdkPaintable *paintable = gtk_widget_paintable_new(widget);
    gdk_paintable_snapshot(paintable, snapshot, context->width, context->height);
    g_object_unref(paintable);

    GskRenderNode *render_node = gtk_snapshot_to_node(snapshot);
    g_object_unref(snapshot);

    if (!render_node) {
        g_set_error(context->error, G_IO_ERROR, G_IO_ERROR_FAILED,
                    "no render node produced for the widget tree");
        return;
    }

    GdkTexture *texture = NULL;
    {
        GskRenderer *renderer =
            gtk_native_get_renderer(gtk_widget_get_native(widget));
        const graphene_rect_t bounds =
            GRAPHENE_RECT_INIT(0, 0, context->width, context->height);
        texture = gsk_renderer_render_texture(renderer, render_node, &bounds);
    }

    gsk_render_node_unref(render_node);

    if (!texture) {
        g_set_error(context->error, G_IO_ERROR, G_IO_ERROR_FAILED,
                    "no texture produced for the widget tree");
        return;
    }

    gdk_texture_save_to_png(texture, context->output_path);
    g_object_unref(texture);
    context->done = TRUE;
}

/* Captures the widget tree synchronously with the frame clock's after-paint
 * phase (i.e. after a real paint cycle laid out and rendered the tree), then
 * withdraws the toplevel window and quits the main loop. */
static void render_after_paint(GdkFrameClock *clock, gpointer user_data) {
    RenderContext *context = user_data;
    g_signal_handlers_disconnect_by_func(clock, render_after_paint, context);

    capture_and_save(context->root, context);

    /* Withdraw the toplevel right away so no window remains mapped on the
     * headless compositor when we quit. */
    gtk_widget_set_visible(context->root, FALSE);

    g_main_loop_quit(context->loop);
}

/* Frame-clock tick: let two frames go by so the initial layout + paint cycle
 * completes, then hook the capture into the after-paint phase of the next
 * frame and stop ticking. */
static gboolean render_frame_callback(GtkWidget *widget,
                                      GdkFrameClock *clock,
                                      gpointer user_data) {
    RenderContext *context = user_data;
    (void) widget;

    if (context->tick_count++ < 2)
        return G_SOURCE_CONTINUE;

    g_signal_connect(clock, "after-paint",
                     G_CALLBACK(render_after_paint), context);
    return G_SOURCE_REMOVE;
}

static gboolean watchdog_timeout(gpointer user_data) {
    g_printerr("render: timed out waiting for a frame; "
               "nothing could be rendered\n");
    g_main_loop_quit((GMainLoop *) user_data);
    return G_SOURCE_REMOVE;
}

static void initialize_main_loop(RenderContext *context) {
    gtk_widget_add_tick_callback(context->root, render_frame_callback, context, NULL);

    gtk_widget_set_size_request(context->root, context->width, context->height);
    gtk_widget_set_visible(context->root, TRUE);

    context->loop = g_main_loop_new(NULL, FALSE);
    guint watchdog_id = g_timeout_add_seconds(5, watchdog_timeout, context->loop);
    g_main_loop_run(context->loop);
    g_source_remove(watchdog_id);
    g_main_loop_unref(context->loop);
    context->loop = NULL;
}

/* ------------------------------------------------------------------ */
/*  Public API                                                         */
/* ------------------------------------------------------------------ */

gboolean preview_render_to_png(const char *in_path,
                                const char *out_path,
                                int width,
                                int height,
                                GError **error) {
    adw_init();

    GtkBuilder *builder = load_ui_file(in_path, error);
    if (!builder) return FALSE;

    GtkWidget *root = resolve_render_root(builder, in_path, error);
    if (!root) {
        g_clear_object(&builder);
        return FALSE;
    }

    RenderContext context = {
        .output_path = out_path,
        .root = root,
        .width = width,
        .height = height,
        .tick_count = 0,
        .done = FALSE,
        .error = error,
    };
    initialize_main_loop(&context);

    g_clear_object(&builder);
    return context.done;
}