#ifndef PREVIEW_RENDER_H
#define PREVIEW_RENDER_H

#include <glib.h>

/**
 * Loads a `.ui` file and renders it to a PNG.
 *
 * Performs the full pipeline: parse the file (rewriting templates, dropping
 * unresolvable custom classes), resolve a renderable root widget, present it
 * in a headless GTK4 display, wait for a frame, and save the snapshot.
 *
 * @param in_path  path to a GTK `.ui` XML file.
 * @param out_path destination path for the resulting PNG.
 * @param width    render width  in pixels (default 600).
 * @param height   render height in pixels (default 800).
 * @param error    set on GTK or file errors.
 * @return         TRUE on success, FALSE with @error set on failure.
 */
gboolean preview_render_to_png(const char *in_path,
                                const char *out_path,
                                int width,
                                int height,
                                GError **error);

#endif /* PREVIEW_RENDER_H */
