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
#include <string.h>
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
    GMainLoop *loop;
} RenderContext;

/* ------------------------------------------------------------------ */
/*  Load                                                               */
/* ------------------------------------------------------------------ */

static char *read_file(const char *path, GError **error) {
    g_autofree char *content = NULL;
    gsize len = 0;
    if (!g_file_get_contents(path, &content, &len, error)) return NULL;
    return g_strndup(content, len);
}

static GtkBuilder *load_ui_file(const char *in_path, GError **error) {
    g_autofree char *raw = read_file(in_path, error);
    if (!raw) return NULL;

    g_autofree char *filtered = ui_filter_xml(raw, error);
    if (!filtered) return NULL;

    GtkBuilder *builder = gtk_builder_new();
    if (!gtk_builder_add_from_string(builder, filtered, -1, error)) {
        g_printerr("render: %s\n", (*error)->message);
        g_object_unref(builder);
        return NULL;
    }
    return builder;
}

/* ------------------------------------------------------------------ */
/*  Present                                                            */
/* ------------------------------------------------------------------ */

static GtkWidget *get_first_widget(GtkBuilder *builder) {
    GSList *widget_list = gtk_builder_get_objects(builder);
    GSList *iterator = widget_list;
    GtkWidget *widget = NULL;

    for (; iterator; iterator = g_slist_next(iterator)) {
        if (!GTK_IS_WIDGET(iterator->data)) continue;
        if (!widget) widget = GTK_WIDGET(iterator->data);
        if (GTK_IS_WINDOW(widget)) break;
    }

    g_slist_free(widget_list);
    return widget;
}

static GtkWidget *resolve_render_root(GtkBuilder *builder, const char *in_path) {
    GtkWidget *root = get_first_widget(builder);
    if (!root) {
        g_printerr("render: no widget found in %s\n", in_path);
        return NULL;
    }

    if (GTK_IS_WINDOW(root)) return root;
    if (ADW_IS_DIALOG(root)) {
        g_printerr("render: %s is a presenter dialog; presenting (best-effort)\n", in_path);
        adw_dialog_present(ADW_DIALOG(root), NULL);
        return root;
    }

    GtkWidget *frame = gtk_window_new();
    gtk_window_set_child(GTK_WINDOW(frame), root);
    return frame;
}

/* ------------------------------------------------------------------ */
/*  Snapshot                                                           */
/* ------------------------------------------------------------------ */

static gboolean screenshot_frame(GtkWidget *widget, GdkFrameClock *clock, gpointer data) {
    const RenderContext *ctx = data;
    (void) clock;

    /* First tick: let the frame clock run through at least one full Paint
     * cycle so the widget tree is laid out and rendered. */
    static int tick_count = 0;
    if (tick_count++ == 0)
        return G_SOURCE_CONTINUE;

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

static gboolean watchdog_timeout(gpointer data) {
    g_printerr("render: timed out waiting for a frame; nothing could be rendered\n");
    g_main_loop_quit((GMainLoop *) data);
    return G_SOURCE_REMOVE;
}

static void initialize_main_loop(GtkWidget *window, RenderContext *ctx) {
    gtk_widget_add_tick_callback(window, screenshot_frame, ctx, NULL);

    gtk_widget_set_size_request(window, ctx->width, ctx->height);
    gtk_widget_set_visible(window, TRUE);

    ctx->loop = g_main_loop_new(NULL, FALSE);
    guint watchdog = g_timeout_add_seconds(5, watchdog_timeout, ctx->loop);
    g_main_loop_run(ctx->loop);
    g_source_remove(watchdog);
    g_main_loop_unref(ctx->loop);
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

    GtkWidget *root = resolve_render_root(builder, in_path);
    if (!root) {
        g_clear_object(&builder);
        g_set_error(error, G_IO_ERROR, G_IO_ERROR_FAILED,
                    "no renderable widget found in %s", in_path);
        return FALSE;
    }

    RenderContext ctx = {
        .output_path = out_path,
        .width = width,
        .height = height,
    };
    initialize_main_loop(root, &ctx);

    g_clear_object(&builder);
    return TRUE;
}
