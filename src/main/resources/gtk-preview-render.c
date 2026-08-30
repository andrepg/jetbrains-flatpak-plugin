/*
 * gtk-preview-render.c — Headless GTK4 .ui → PNG renderer
 *
 * Build:  cc $(pkg-config --cflags --libs gtk4 libadwaita-1 graphene-1.0) -o gtk-preview-render gtk-preview-render.c
 * Build:  flatpak run --command=bash --filesystem=$PWD --filesystem=/tmp org.gnome.Sdk//50 -c 'cc $(pkg-config --cflags --libs gtk4 libadwaita-1 graphene-1.0) -o /tmp/gtk-preview-render gtk-preview-render.c'
 * Run:    gtk-preview-render <input.ui> <output.png> [width] [height]
 *
 * Language-agnostic preview: renders the GTK/Adwaita surfaces of a .ui file.
 * Application-specific classes (DoItMainWindow, TaskForm, ...) are never loaded
 * (they need the app's Vala/Python/JS/C code), so any <object> whose class is
 * not in the GTK (Gtk*) or libadwaita (Adw*) namespaces is dropped. This
 * namespace rule needs no type registry: it is robust to GTK4's lazy type
 * registration and needs no generated allowlist. <template>-rooted files are
 * rewritten to their parent class so they render standalone. No app build or
 * runtime is required.
 */

#include <stdlib.h>
#include <string.h>
#include <adwaita.h>
#include <glib.h>
#include <graphene.h>

typedef struct {
    const char *output_path;
    int width;
    int height;
    GMainLoop *loop;
} RenderContext;

/* A .ui class is treated as a real, renderable surface when it lives in the
 * GTK (Gtk*) or libadwaita (Adw*) namespaces. Everything else is an
 * application-specific class (loaded only by app code, never by this process)
 * and is dropped. This namespace rule is robust to GTK4 registering widget
 * types lazily, so no type registry or allowlist is needed. */
static gboolean is_builtin_class(const char *cls) {
    return strncmp(cls, "Gtk", 3) == 0 || strncmp(cls, "Adw", 3) == 0;
}

/* ------------------------------------------------------------------ */
/*  XML preprocessing: template rewrite + unregistered-object removal  */
/* ------------------------------------------------------------------ */

/* Extract the value of the attribute `name` from a start-tag string `tag`
 * (the full text including the leading '<' and trailing '>'). On success
 * stores a newly allocated value in *out and returns TRUE. */
static gboolean attr_value(const char *tag, const char *name, char **out) {
    const char *p = tag;
    const size_t nlen = strlen(name);

    while ((p = strstr(p, name)) != NULL) {
        // Name must start at an attribute boundary (whitespace before it,
        // or it is the very first token after the element name).
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
 * any attribute named `skip1`/`skip2` (when non-NULL). `body` is the text
 * inside a start tag excluding the element name; `name_end` points just past
 * the element name. */
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

/* A stack frame tracking an open element, whether it was dropped, and where
 * its opening tag starts in the output buffer (so a parent <property> whose
 * only child is dropped can be retroactively removed too). */
typedef struct {
    char *name;
    gboolean bad;
    gsize open_pos;
} Elem;

static void free_elem(gpointer data);

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

        // Element tag.
        const char *gt = strchr(p, '>');
        if (!gt) { g_set_error(error, G_MARKUP_ERROR, G_MARKUP_ERROR_PARSE, "unterminated tag"); goto fail; }

        g_autofree char *body = g_strndup(p + 1, (size_t) (gt - (p + 1)));
        const char *b = body;
        while (*b && g_ascii_isspace((guchar) *b)) b++;
        const char *name_start = b;

        if (*b == '/') {
            // End tag </name>
            const char *name = b + 1;
            while (*name && g_ascii_isspace((guchar) *name)) name++;
            // Pop matching frame (lenient: pop top).
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

        // Start or self-closing tag.
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
            // If this object is the sole content of a parent <property>, drop
            // that property too, so we don't emit an empty, unparseable
            // <property name="..."></property>.
            if (self_closing && !g_queue_is_empty(stack)) {
                Elem *parent = g_queue_peek_head(stack);
                if (parent && !parent->bad && strcmp(parent->name, "property") == 0 &&
                    parent->open_pos <= out->len) {
                    g_string_truncate(out, parent->open_pos);
                    parent->bad = TRUE;
                    suppress++;
                }
            }
            // Drop this object (and its subtree, for non-self-closing ones).
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

        // Emit decision taken with current suppress level.
        gsize open_pos = out->len;  // start of this opening tag in `out`
        if (suppress == 0) {
            if (strcmp(name, "template") == 0) {
                // Rewrite template -> object with parent (or class) as the class.
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

static void free_elem(gpointer data) {
    Elem *e = data;
    if (!e) return;
    g_free(e->name);
    g_free(e);
}

/* ------------------------------------------------------------------ */
/*  Loading                                                             */
/* ------------------------------------------------------------------ */

/** Reads a whole file into a NUL-terminated string, or NULL on error. */
static char *read_file(const char *path, GError **error) {
    g_autofree char *content = NULL;
    gsize len = 0;
    if (!g_file_get_contents(path, &content, &len, error)) return NULL;
    return g_strndup(content, len);
}

/**
 * Loads a single GTK interface from a file into a builder, handling both
 * <object>-rooted and <template>-rooted files, and dropping application-
 * specific object nodes.
 */
static GtkBuilder *load_ui_file(const char *in_path, GError **error) {
    g_autofree char *raw = read_file(in_path, error);
    if (!raw) return NULL;

    g_autofree char *filtered = filter_ui_xml(raw, error);
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

/**
 * Resolves the widget to snapshot:
 *   - a GtkWindow is used directly;
 *   - a presenter dialog (AdwDialog, e.g. AdwShortcutsDialog) is rendered
 *     standalone (it cannot be added to a window);
 *   - any other widget is wrapped in a GtkWindow.
 */
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
/*  Snapshot                                                            */
/* ------------------------------------------------------------------ */

static gboolean screenshot_frame(GtkWidget *widget, GdkFrameClock *clock, gpointer data) {
    const RenderContext *ctx = data;

    // First tick: let the frame clock run through at least one full Paint
    // cycle so the widget tree is laid out and rendered.
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
    // Fail fast instead of hanging when a root never maps a frame clock.
    guint watchdog = g_timeout_add_seconds(5, watchdog_timeout, ctx->loop);
    g_main_loop_run(ctx->loop);
    g_source_remove(watchdog);
    g_main_loop_unref(ctx->loop);
}

/* ------------------------------------------------------------------ */

int main(const int argc, char *argv[]) {
    if (argc < 3) {
        return EXIT_FAILURE;
    }

    RenderContext ctx = {
        .output_path = argv[2],
        .width = 600,
        .height = 800,
    };

    const char *in_path = argv[1];
    if (argc > 3) ctx.width = atoi(argv[3]);
    if (argc > 4) ctx.height = atoi(argv[4]);

    adw_init();

    if (argc > 2 && strcmp(argv[1], "--dump-xml") == 0) {
        GError *err = NULL;
        g_autofree char *raw = read_file(argv[2], &err);
        if (!raw) { g_printerr("render: %s\n", err->message); return EXIT_FAILURE; }
        g_autofree char *filtered = filter_ui_xml(raw, &err);
        if (!filtered) { g_printerr("render: %s\n", err->message); return EXIT_FAILURE; }
        g_print("%s\n", filtered);
        return EXIT_SUCCESS;
    }

    GError *error = NULL;
    GtkBuilder *builder = load_ui_file(in_path, &error);
    if (!builder) {
        if (error) g_printerr("render: %s\n", error->message);
        return EXIT_FAILURE;
    }

    GtkWidget *window = resolve_render_root(builder, in_path);
    if (!window) {
        g_clear_object(&builder);
        return EXIT_FAILURE;
    }

    initialize_main_loop(window, &ctx);
    g_clear_object(&builder);

    return EXIT_SUCCESS;
}