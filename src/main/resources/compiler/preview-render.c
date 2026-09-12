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

/* Return the first renderable widget from the builder, preferring a
 * GtkWindow when one exists (it can be presented standalone without
 * a wrapper). */
static GtkWidget *get_first_widget(GtkBuilder *builder) {
    GSList *object_list = gtk_builder_get_objects(builder);
    GtkWidget *first_widget = NULL;

    for (GSList *iter = object_list; iter; iter = g_slist_next(iter)) {
        if (!GTK_IS_WIDGET(iter->data)) continue;
        if (!first_widget)
            first_widget = GTK_WIDGET(iter->data);
        if (GTK_IS_WINDOW(iter->data)) {
            first_widget = GTK_WIDGET(iter->data);
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

static gboolean render_frame_callback(GtkWidget *widget,
                                      GdkFrameClock *clock,
                                      gpointer user_data) {
    RenderContext *context = user_data;
    (void) clock;

    /* First tick: let the frame clock run through at least one full Paint
     * cycle so the widget tree is laid out and rendered. */
    if (context->tick_count++ == 0)
        return G_SOURCE_CONTINUE;

    capture_and_save(widget, context);
    g_main_loop_quit(context->loop);
    return G_SOURCE_REMOVE;
}

static gboolean watchdog_timeout(gpointer user_data) {
    g_printerr("render: timed out waiting for a frame; "
               "nothing could be rendered\n");
    g_main_loop_quit((GMainLoop *) user_data);
    return G_SOURCE_REMOVE;
}

static void initialize_main_loop(GtkWidget *window, RenderContext *context) {
    gtk_widget_add_tick_callback(window, render_frame_callback, context, NULL);

    gtk_widget_set_size_request(window, context->width, context->height);
    gtk_widget_set_visible(window, TRUE);

    context->loop = g_main_loop_new(NULL, FALSE);
    guint watchdog_id = g_timeout_add_seconds(5, watchdog_timeout, context->loop);
    g_main_loop_run(context->loop);
    g_source_remove(watchdog_id);
    g_main_loop_unref(context->loop);
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
        .width = width,
        .height = height,
        .tick_count = 0,
        .done = FALSE,
        .error = error,
    };
    initialize_main_loop(root, &context);

    g_clear_object(&builder);
    return context.done;
}