#ifndef UI_XML_H
#define UI_XML_H

#include <glib.h>

/**
 * Transforms a GTK `.ui` XML string for preview rendering.
 *
 * Two rules are applied:
 *   - `<template class="X" parent="Y">` is rewritten to `<object class="Y">`
 *     so the builder can load it standalone (templates are only valid inside a
 *     widget class, not when loaded from a file).
 *   - `<object class="X">` subtrees where X is not a built-in GTK
 *     (Gtk*) or libadwaita (Adw*) class are removed — these are
 *     application-specific classes whose types are unavailable in a
 *     standalone renderer process.
 *
 * @param src   NUL-terminated XML string (not modified).
 * @param error set on malformed input.
 * @return      newly allocated transformed XML, or NULL on error.
 */
char *ui_filter_xml(const char *src, GError **error);

#endif /* UI_XML_H */
