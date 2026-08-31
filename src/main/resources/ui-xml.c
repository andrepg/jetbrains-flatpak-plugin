/*
 * ui-xml.c — GTK .ui XML preprocessing library
 *
 * Pure-GLib module with no GTK/adwaita dependency.  Transforms a `.ui` XML
 * string so that <template>-rooted files load via gtk_builder_add_from_string
 * and application-specific classes are stripped, leaving only real GTK4 /
 * libadwaita surfaces for preview rendering.
 *
 * Compile (inside GNOME SDK 50):
 *   cc -c $(pkg-config --cflags glib-2.0) -o ui-xml.o ui-xml.c
 */

#include <string.h>
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
static gboolean is_builtin_class(const char *cls) {
    return strncmp(cls, "Gtk", 3) == 0 || strncmp(cls, "Adw", 3) == 0;
}

/* ------------------------------------------------------------------ */
/*  Low-level tag helpers                                              */
/* ------------------------------------------------------------------ */

/* Extract the value of the attribute `name` from a start-tag string `tag`
 * (the full text including the leading '<' and trailing '>'). On success
 * stores a newly allocated value in *out and returns TRUE. */
static gboolean attr_value(const char *tag, const char *name, char **out) {
    const char *p = tag;
    const size_t nlen = strlen(name);

    while ((p = strstr(p, name)) != NULL) {
        if (p != tag && !g_ascii_isspace((guchar) *(p - 1))) {
            p++;
            continue;
        }
        const char *q = p + nlen;
        while (*q == ' ' || *q == '\t') q++;
        if (*q != '=') { p = q; continue; }
        q++;
        while (*q == ' ' || *q == '\t') q++;
        if (*q != '"' && *q != '\'') { p = q; continue; }
        const char quote = *q;
        const char *val_start = ++q;
        while (*q && *q != quote) q++;
        if (*q != quote) { p = q; continue; }
        *out = g_strndup(val_start, q - val_start);
        return TRUE;
    }
    return FALSE;
}

/* Append ` key="value"` pairs parsed from an element body to `out`, skipping
 * any attribute named `skip1`/`skip2` (when non-NULL). `name_end` points just
 * past the element name. */
static void append_attrs_skipping(GString *out, const char *name_end,
                                  const char *skip1, const char *skip2) {
    const char *p = name_end;
    while (1) {
        while (*p == ' ' || *p == '\t') p++;
        if (*p == '/' || *p == '\0') break;

        const char *an_start = p;
        while (*p && *p != '=' && *p != ' ' && *p != '\t' && *p != '/') p++;
        const char *an_end = p;
        if (an_end == an_start) break;

        while (*p == ' ' || *p == '\t') p++;
        char *value = NULL;
        char quote = '"';
        if (*p == '=') {
            p++;
            while (*p == ' ' || *p == '\t') p++;
            if (*p == '"' || *p == '\'') {
                quote = *p;
                const char *vs = ++p;
                while (*p && *p != quote) p++;
                value = g_strndup(vs, p - vs);
                if (*p == quote) p++;
            } else {
                const char *vs = p;
                while (*p && *p != ' ' && *p != '\t') p++;
                value = g_strndup(vs, p - vs);
            }
        }

        const size_t alen = (size_t) (an_end - an_start);
        gboolean skip =
            (skip1 && strncmp(an_start, skip1, alen) == 0 && strlen(skip1) == alen) ||
            (skip2 && strncmp(an_start, skip2, alen) == 0 && strlen(skip2) == alen);

        if (!skip) {
            g_string_append_c(out, ' ');
            g_string_append_len(out, an_start, alen);
            if (value) {
                g_string_append_c(out, '=');
                g_string_append_c(out, quote);
                g_string_append(out, value);
                g_string_append_c(out, quote);
            }
        }
        g_free(value);
    }
}

/* ------------------------------------------------------------------ */
/*  Streaming filter                                                   */
/* ------------------------------------------------------------------ */

/* A stack frame tracking an open element, whether it was dropped, and where
 * its opening tag starts in the output buffer (so a parent <property> whose
 * only child is dropped can be retroactively removed too). */
typedef struct {
    char *name;
    gboolean bad;
    gsize open_pos;
} Elem;

static void free_elem(gpointer data) {
    Elem *e = data;
    if (!e) return;
    g_free(e->name);
    g_free(e);
}

/* Returns a newly allocated copy of `src` with:
 *   - the top-level <template ...> rewritten to <object class="<parent>">;
 *   - <object class="X"> subtrees removed when X is not a built-in
 *     GTK/libadwaita class (application-specific classes).
 * Returns NULL with `error` set on malformed input. */
static char *filter_ui_xml(const char *src, GError **error) {
    GString *out = g_string_new(NULL);
    GQueue *stack = g_queue_new();
    int suppress = 0;
    const char *p = src;

    while (*p) {
        if (*p != '<') {
            if (suppress == 0) g_string_append_c(out, *p);
            p++;
            continue;
        }

        if (strncmp(p, "<!--", 4) == 0) {
            const char *end = strstr(p, "-->");
            if (!end) { g_set_error(error, G_MARKUP_ERROR, G_MARKUP_ERROR_PARSE, "unterminated comment"); goto fail; }
            if (suppress == 0) g_string_append_len(out, p, end + 3 - p);
            p = end + 3;
            continue;
        }
        if (strncmp(p, "<?", 2) == 0) {
            const char *end = strstr(p, "?>");
            if (!end) { g_set_error(error, G_MARKUP_ERROR, G_MARKUP_ERROR_PARSE, "unterminated processing instruction"); goto fail; }
            if (suppress == 0) g_string_append_len(out, p, end + 2 - p);
            p = end + 2;
            continue;
        }
        if (strncmp(p, "<![CDATA[", 9) == 0) {
            const char *end = strstr(p, "]]>");
            if (!end) { g_set_error(error, G_MARKUP_ERROR, G_MARKUP_ERROR_PARSE, "unterminated CDATA"); goto fail; }
            if (suppress == 0) g_string_append_len(out, p, end + 3 - p);
            p = end + 3;
            continue;
        }

        /* Element tag. */
        const char *gt = strchr(p, '>');
        if (!gt) { g_set_error(error, G_MARKUP_ERROR, G_MARKUP_ERROR_PARSE, "unterminated tag"); goto fail; }

        g_autofree char *body = g_strndup(p + 1, (size_t) (gt - (p + 1)));
        const char *b = body;
        while (*b && g_ascii_isspace((guchar) *b)) b++;
        const char *name_start = b;

        if (*b == '/') {
            /* End tag </name> */
            const char *name = b + 1;
            while (*name && g_ascii_isspace((guchar) *name)) name++;
            gboolean was_bad = FALSE;
            gboolean is_template = FALSE;
            if (!g_queue_is_empty(stack)) {
                Elem *top = g_queue_pop_head(stack);
                is_template = (strcmp(top->name, "template") == 0);
                was_bad = top->bad;
                if (was_bad) suppress--;
                g_free(top->name);
                g_free(top);
            }
            if (suppress == 0 && !was_bad) {
                if (is_template) {
                    g_string_append(out, "</object>");
                } else {
                    g_string_append_len(out, p, (size_t) (gt - p) + 1);
                }
            }
            p = gt + 1;
            continue;
        }

        /* Start or self-closing tag. */
        while (*b && !g_ascii_isspace((guchar) *b) && *b != '/') b++;
        const char *name_end = b;
        g_autofree char *name = g_strndup(name_start, (size_t) (name_end - name_start));

        gboolean self_closing = FALSE;
        {
            const char *t = body + strlen(body);
            while (t > body && g_ascii_isspace((guchar) *(t - 1))) t--;
            if (t > body && *(t - 1) == '/') self_closing = TRUE;
        }

        g_autofree char *rawtag = g_strndup(p, (size_t) (gt - p) + 1);

        gboolean is_object = (strcmp(name, "object") == 0);
        gboolean bad = FALSE;
        g_autofree char *cls = NULL;
        if (is_object && attr_value(rawtag, "class", &cls)) {
            bad = !is_builtin_class(cls);
        }

        if (is_object && bad) {
            g_printerr("render: dropping object class=\"%s\" (not a GTK/libadwaita class)\n",
                       cls ? cls : "(no class)");
            /* If this object is the sole content of a parent <property>, drop
             * that property too so we don't leave an empty, unparseable
             * <property name="..."></property>. */
            if (self_closing && !g_queue_is_empty(stack)) {
                Elem *parent = g_queue_peek_head(stack);
                if (parent && !parent->bad && strcmp(parent->name, "property") == 0 &&
                    parent->open_pos <= out->len) {
                    g_string_truncate(out, parent->open_pos);
                    parent->bad = TRUE;
                    suppress++;
                }
            }
            /* Drop this object (and its subtree, for non-self-closing ones). */
            if (!self_closing) {
                Elem *e = g_new0(Elem, 1);
                e->name = g_strdup("object");
                e->bad = TRUE;
                g_queue_push_head(stack, e);
                suppress++;
            }
            p = gt + 1;
            continue;
        }

        /* Emit decision taken with current suppress level. */
        gsize open_pos = out->len;
        if (suppress == 0) {
            if (strcmp(name, "template") == 0) {
                /* Rewrite template -> object with parent (or class) as class. */
                g_autofree char *cls = NULL;
                g_autofree char *parent = NULL;
                const char *nc = NULL;
                if (attr_value(rawtag, "parent", &parent)) nc = parent;
                else if (attr_value(rawtag, "class", &cls)) nc = cls;
                g_string_append(out, "<object");
                if (nc) {
                    g_string_append_c(out, ' ');
                    g_string_append_printf(out, "class=\"%s\"", nc);
                }
                append_attrs_skipping(out, name_end, "class", "parent");
                g_string_append(out, self_closing ? " />" : ">");
            } else {
                g_string_append_len(out, p, (size_t) (gt - p) + 1);
            }
        }

        if (!self_closing) {
            Elem *e = g_new0(Elem, 1);
            e->name = g_strdup(name);
            e->bad = FALSE;
            e->open_pos = open_pos;
            g_queue_push_head(stack, e);
        }

        p = gt + 1;
    }

    if (suppress != 0 || !g_queue_is_empty(stack)) {
        g_set_error(error, G_MARKUP_ERROR, G_MARKUP_ERROR_PARSE, "unbalanced XML");
        goto fail;
    }

    g_queue_free_full(stack, (GDestroyNotify) free_elem);
    return g_string_free(out, FALSE);

fail:
    g_queue_free_full(stack, (GDestroyNotify) free_elem);
    g_string_free(out, TRUE);
    return NULL;
}

/* ------------------------------------------------------------------ */
/*  Public API                                                         */
/* ------------------------------------------------------------------ */

char *ui_filter_xml(const char *src, GError **error) {
    return filter_ui_xml(src, error);
}
