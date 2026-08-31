/*
 * gtk-preview-render.c — CLI entry point for GTK .ui preview renderer
 *
 * Build:  cc $(pkg-config --cflags --libs gtk4 libadwaita-1 graphene-1.0) \
 *           gtk-preview-render.c preview-render.c ui-xml.c -o gtk-preview-render
 *
 * Run:    gtk-preview-render <input.ui> <output.png> [width] [height]
 *         gtk-preview-render --dump-xml <input.ui>        (debug: print filtered XML)
 *
 * Language-agnostic preview: renders the GTK/Adwaita surfaces of a .ui file.
 * Application-specific classes (DoItMainWindow, TaskForm, ...) are never loaded
 * (they need the app's Vala/Python/JS/C code), so any <object> whose class is
 * not in the GTK (Gtk*) or libadwaita (Adw*) namespaces is dropped. <template>
 * -rooted files are rewritten to their parent class so they render standalone.
 * No app build or runtime is required.
 */

#include <stdlib.h>
#include <string.h>
#include <glib.h>
#include "ui-xml.h"
#include "preview-render.h"

int main(const int argc, char *argv[]) {
    if (argc < 3) {
        return EXIT_FAILURE;
    }

    const char *in_path = argv[1];

    /* --dump-xml: filtered XML only (no GTK init required) */
    if (argc > 2 && strcmp(argv[1], "--dump-xml") == 0) {
        GError *err = NULL;
        g_autofree char *content = NULL;
        gsize len = 0;
        if (!g_file_get_contents(argv[2], &content, &len, &err)) {
            g_printerr("render: %s\n", err->message);
            g_error_free(err);
            return EXIT_FAILURE;
        }
        g_autofree char *src = g_strndup(content, len);
        g_autofree char *filtered = ui_filter_xml(src, &err);
        if (!filtered) {
            g_printerr("render: %s\n", err->message);
            g_error_free(err);
            return EXIT_FAILURE;
        }
        g_print("%s\n", filtered);
        return EXIT_SUCCESS;
    }

    /* Normal render path */
    int width = 600;
    int height = 800;
    if (argc > 3) width = atoi(argv[3]);
    if (argc > 4) height = atoi(argv[4]);

    GError *error = NULL;
    const char *out_path = argv[2];
    if (!preview_render_to_png(in_path, out_path, width, height, &error)) {
        if (error) {
            g_printerr("render: %s\n", error->message);
            g_error_free(error);
        }
        return EXIT_FAILURE;
    }

    return EXIT_SUCCESS;
}
