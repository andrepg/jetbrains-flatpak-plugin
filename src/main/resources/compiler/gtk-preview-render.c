/*
 * gtk-preview-render.c — CLI entry point for GTK .ui preview renderer
 *
 * Build:  make            (inside the GNOME SDK; see Makefile)
 *
 * Run:    gtk-preview-render <input.ui> <output.png> [width] [height]
 *         gtk-preview-render --dump-xml <input.ui>   (debug: print filtered XML)
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

static int dump_xml(const char *input_path) {
    GError *error = NULL;
    char *raw_content = NULL;
    size_t content_length = 0;

    if (!g_file_get_contents(input_path, &raw_content, &content_length, &error)) {
        g_printerr("render: %s\n", error->message);
        g_error_free(error);
        return EXIT_FAILURE;
    }

    g_autofree char *source_xml = g_strndup(raw_content, content_length);
    g_free(raw_content);

    g_autofree char *filtered_xml = ui_filter_xml(source_xml, &error);
    if (!filtered_xml) {
        g_printerr("render: %s\n", error->message);
        g_error_free(error);
        return EXIT_FAILURE;
    }

    g_print("%s\n", filtered_xml);
    return EXIT_SUCCESS;
}

static int render(const char *input_path, const char *output_path,
                  int width, int height) {
    GError *error = NULL;
    if (!preview_render_to_png(input_path, output_path, width, height, &error)) {
        if (error) {
            g_printerr("render: %s\n", error->message);
            g_error_free(error);
        }
        return EXIT_FAILURE;
    }
    return EXIT_SUCCESS;
}

int main(int argc, char *argv[]) {
    if (argc < 3) {
        g_printerr("usage: %s <input.ui> <output.png> [width] [height]\n"
                   "       %s --dump-xml <input.ui>\n",
                   argv[0], argv[0]);
        return EXIT_FAILURE;
    }

    /* --dump-xml: filtered XML only (no GTK init required). */
    if (g_strcmp0(argv[1], "--dump-xml") == 0)
        return dump_xml(argv[2]);

    int width = 600;
    int height = 800;
    if (argc > 3) width = atoi(argv[3]);
    if (argc > 4) height = atoi(argv[4]);

    return render(argv[1], argv[2], width, height);
}