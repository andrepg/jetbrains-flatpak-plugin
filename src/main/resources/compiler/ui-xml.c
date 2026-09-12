/*
 * ui-xml.c — GTK .ui XML preprocessing library
 *
 * Pure-GLib module with no GTK/adwaita dependency. Transforms a `.ui` XML
 * string so that <template>-rooted files load via gtk_builder_add_from_string
 * and application-specific classes are stripped, leaving only real GTK4 /
 * libadwaita surfaces for preview rendering.
 *
 * The transform is driven by GMarkupParser (GLib's SAX parser) instead of a
 * hand-rolled byte scanner. Two advantages:
 *
 *   - entities (`&lt;`, `&amp;`, ...) and CDATA are handled by GLib, so the
 *     filter can no longer mis-tokenize a `<` that was escaped in the input.
 *   - whole `<property>` elements whose only content is a dropped object are
 *     never emitted at all (deferred-emission), instead of the old
 *     "truncate the output buffer" hack.
 *
 * Compile (inside GNOME SDK):
 *   cc -c $(pkg-config --cflags glib-2.0) -o ui-xml.o ui-xml.c
 */

#include <glib.h>
#include "ui-xml.h"

/* ------------------------------------------------------------------ */
/*  Namespace policy                                                   */
/* ------------------------------------------------------------------ */

/* A .ui class is treated as a real, renderable surface when it lives in the
 * GTK (Gtk*) or libadwaita (Adw*) namespaces. Everything else is an
 * application-specific class (loaded only by app code, never by this process)
 * and is dropped. This namespace rule is robust to GTK4 registering widget
 * types lazily, so no type registry or allowlist is needed. */
static gboolean is_builtin_class(const char *class_name) {
    return strncmp(class_name, "Gtk", 3) == 0
        || strncmp(class_name, "Adw", 3) == 0;
}

/* ------------------------------------------------------------------ */
/*  Output escaping                                                    */
/* ------------------------------------------------------------------ */

static void append_escaped(GString *out, const char *value) {
    for (const char *cursor = value; *cursor; cursor++) {
        switch (*cursor) {
            case '&':  g_string_append(out, "&amp;");  break;
            case '<':  g_string_append(out, "&lt;");   break;
            case '>':  g_string_append(out, "&gt;");   break;
            default:   g_string_append_c(out, *cursor);
        }
    }
}

static void append_attr_escaped(GString *out, const char *value) {
    for (const char *cursor = value; *cursor; cursor++) {
        switch (*cursor) {
            case '&':  g_string_append(out, "&amp;");  break;
            case '<':  g_string_append(out, "&lt;");   break;
            case '>':  g_string_append(out, "&gt;");   break;
            case '"':  g_string_append(out, "&quot;"); break;
            default:   g_string_append_c(out, *cursor);
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Element stack                                                      */
/* ------------------------------------------------------------------ */

typedef struct {
    char    *name;    /* element name; "object" for rewritten <template>s */
    char    *start_tag; /* reconstructed start tag, held while pending */
    gboolean dropped;  /* entire subtree suppressed (custom class) */
    gboolean pending;  /* property whose opening tag is not emitted yet */
} Element;

static void free_element(gpointer data) {
    Element *element = data;
    g_free(element->name);
    g_free(element->start_tag);
    g_free(element);
}

typedef struct {
    GString *output;
    GPtrArray *stack;  /* Element *; top of stack is the last element */
    guint suppress_depth;  /* number of open dropped elements */
} FilterState;

static void push_element(FilterState *state, Element *element) {
    g_ptr_array_add(state->stack, element);
}

static Element *peek_element(FilterState *state) {
    return (state->stack->len > 0)
        ? g_ptr_array_index(state->stack, state->stack->len - 1)
        : NULL;
}

static Element *pop_element(FilterState *state) {
    return (state->stack->len > 0)
        ? g_ptr_array_remove_index(state->stack, state->stack->len - 1)
        : NULL;
}

/* Emit the deferred opening tags of every pending <property> that is open at
 * the top of the stack (pending frames are always a contiguous run ending at
 * the top, since a pending property cannot have had content). */
static void flush_pending(FilterState *state) {
    GString *output = state->output;
    for (gsize i = state->stack->len; i > 0; i--) {
        Element *element = g_ptr_array_index(state->stack, i - 1);
        if (!element->pending) break;
        g_string_append(output, element->start_tag);
        g_clear_pointer(&element->start_tag, g_free);
        element->pending = FALSE;
    }
}

/* ------------------------------------------------------------------ */
/*  GMarkupParser callbacks                                            */
/* ------------------------------------------------------------------ */

static gboolean is_whitespace_only(const char *text) {
    for (const char *cursor = text; *cursor;) {
        gunichar ch = g_utf8_get_char(cursor);
        if (!g_unichar_isspace(ch)) return FALSE;
        cursor = g_utf8_next_char(cursor);
    }
    return TRUE;
}

/* Look the `class` attribute up in the GMarkup attribute array. */
static const char *attr_value(const gchar **names, const gchar **values,
                              const char *wanted) {
    for (int i = 0; names[i]; i++)
        if (strcmp(names[i], wanted) == 0) return values[i];
    return NULL;
}

/* Append all attributes except those named in `skip1`/`skip2` (non-NULL). */
static void append_attrs_skipping(GString *out,
                                  const gchar **names, const gchar **values,
                                  const char *skip1, const char *skip2) {
    for (int i = 0; names[i]; i++) {
        const char *name = names[i];
        if ((skip1 && strcmp(name, skip1) == 0) ||
            (skip2 && strcmp(name, skip2) == 0))
            continue;
        g_string_append_c(out, ' ');
        g_string_append(out, name);
        g_string_append_c(out, '=');
        g_string_append_c(out, '"');
        append_attr_escaped(out, values[i]);
        g_string_append_c(out, '"');
    }
}

static void on_start_element(GMarkupParseContext *context,
                             const gchar *element_name,
                             const gchar **attribute_names,
                             const gchar **attribute_values,
                             gpointer user_data,
                             GError **error) {
    FilterState *state = user_data;
    GString *output = state->output;
    (void) context;
    (void) error;

    const gboolean is_object = (strcmp(element_name, "object") == 0);
    const gboolean is_property = (strcmp(element_name, "property") == 0);
    const gboolean is_template = (strcmp(element_name, "template") == 0);

    /* 1. If inside a dropped subtree, suppress everything (even valid Gtk
     * elements nested inside a dropped application type). Every element
     * pushed here counts against suppress_depth so it stays balanced. */
    if (state->suppress_depth > 0) {
        Element *suppressed = g_new0(Element, 1);
        suppressed->name = g_strdup(element_name);
        suppressed->dropped = TRUE;
        push_element(state, suppressed);
        state->suppress_depth++;
        return;
    }

    /* 2. Drop <object class="X"> subtrees whose class is not a built-in
     * GTK/libadwaita class (application-specific types). */
    const char *class_name =
        is_object ? attr_value(attribute_names, attribute_values, "class") : NULL;
    if (class_name && !is_builtin_class(class_name)) {
        g_printerr("render: dropping object class=\"%s\" "
                   "(not a GTK/libadwaita class)\n", class_name);
        Element *dropped = g_new0(Element, 1);
        dropped->name = g_strdup("object");
        dropped->dropped = TRUE;
        push_element(state, dropped);
        state->suppress_depth++;
        return;
    }

    /* Reconstruct the start tag. <template> is rewritten to <object> with its
     * parent class (so the builder loads it standalone), dropping the
     * 'class' and 'parent' attributes. Properties are deferred: their
     * opening tag is only emitted once they have real content. */
    GString *start_tag = g_string_new(NULL);
    if (is_template) {
        const char *parent = attr_value(attribute_names, attribute_values, "parent");
        const char *tpl_class = attr_value(attribute_names, attribute_values, "class");
        const char *new_class = parent ? parent : tpl_class;
        g_string_append(start_tag, "<object");
        if (new_class) {
            g_string_append_c(start_tag, ' ');
            g_string_append(start_tag, "class=\"");
            append_attr_escaped(start_tag, new_class);
            g_string_append_c(start_tag, '"');
        }
        append_attrs_skipping(start_tag, attribute_names, attribute_values,
                              "class", "parent");
        g_string_append_c(start_tag, '>');
    } else {
        g_string_append_c(start_tag, '<');
        g_string_append(start_tag, element_name);
        append_attrs_skipping(start_tag, attribute_names, attribute_values,
                              NULL, NULL);
        g_string_append_c(start_tag, '>');
    }

    if (is_property) {
        /* Deferred property: emit nothing yet. */
        Element *property = g_new0(Element, 1);
        property->name = g_strdup(element_name);
        property->start_tag = g_string_free(start_tag, FALSE);
        property->pending = TRUE;
        push_element(state, property);
    } else {
        /* Real element: flush any pending properties above it, then emit. */
        flush_pending(state);
        g_string_append(output, start_tag->str);
        g_string_free(start_tag, TRUE);
        Element *element = g_new0(Element, 1);
        element->name = g_strdup(is_template ? "object" : element_name);
        push_element(state, element);
    }
}

static void on_end_element(GMarkupParseContext *context,
                           const gchar *element_name,
                           gpointer user_data,
                           GError **error) {
    FilterState *state = user_data;
    (void) context;
    (void) element_name;
    (void) error;

    Element *element = pop_element(state);
    if (!element) return;

    if (element->dropped) {
        state->suppress_depth--;
    } else if (element->pending) {
        /* Property never had content: nothing was emitted, drop it. */
        g_clear_pointer(&element->start_tag, g_free);
    } else {
        g_string_append(state->output, "</");
        g_string_append(state->output, element->name);
        g_string_append_c(state->output, '>');
    }
    free_element(element);
}

static void on_text(GMarkupParseContext *context,
                    const gchar *text,
                    gsize text_len,
                    gpointer user_data,
                    GError **error) {
    FilterState *state = user_data;
    (void) context;
    (void) text_len;
    (void) error;

    if (state->suppress_depth > 0) return;

    const Element *top = peek_element(state);
    if (top && top->pending) {
        /* Inside an unopened property: whitespace-only runs are skipped so a
         * property whose children are all dropped collapses to nothing. */
        if (is_whitespace_only(text)) return;
        flush_pending(state);
    }

    append_escaped(state->output, text);
}

static void on_passthrough(GMarkupParseContext *context,
                           const gchar *passthrough_text,
                           gsize text_len,
                           gpointer user_data,
                           GError **error) {
    FilterState *state = user_data;
    (void) context;
    (void) error;

    if (state->suppress_depth > 0) return;

    /* Comments, processing instructions and CDATA inside an unopened
     * property are not content: skip them so the property can collapse. */
    const Element *top = peek_element(state);
    if (top && top->pending) return;

    g_string_append_len(state->output, passthrough_text, text_len);
}

static const GMarkupParser PARSER = {
    .start_element = on_start_element,
    .end_element = on_end_element,
    .text = on_text,
    .passthrough = on_passthrough,
    .error = NULL,
};

/* ------------------------------------------------------------------ */
/*  Public API                                                         */
/* ------------------------------------------------------------------ */

char *ui_filter_xml(const char *source, GError **error) {
    FilterState state = {
        .output = g_string_new(NULL),
        /* No free func on the array: popped elements are owned by the
         * caller (on_end_element frees them); leftover elements are freed
         * manually on the error path. */
        .stack = g_ptr_array_new(),
        .suppress_depth = 0,
    };

    GMarkupParseContext *context =
        g_markup_parse_context_new(&PARSER, G_MARKUP_PREFIX_ERROR_POSITION,
                                   &state, NULL);

    gboolean ok = g_markup_parse_context_parse(
        context, source, (gssize) strlen(source), error);

    if (ok)
        ok = g_markup_parse_context_end_parse(context, error);

    g_markup_parse_context_free(context);

    if (!ok) {
        for (guint i = 0; i < state.stack->len; i++)
            free_element(g_ptr_array_index(state.stack, i));
        g_ptr_array_free(state.stack, TRUE);
        g_string_free(state.output, TRUE);
        return NULL;
    }

    char *result = g_string_free(state.output, FALSE);
    g_ptr_array_free(state.stack, TRUE);
    return result;
}