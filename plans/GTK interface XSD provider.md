# Plan: GTK interface XSD provider

> **Status: IMPLEMENTED** (see `../src/main/kotlin/io/github/andrepg/flatpak/schemas/providers/GtkInterfaceXmlSchemaProvider.kt` + `plugin.xml`). This document is kept as the design record.

Serve the generated GtkBuilder XSD to `.ui`/`.glade` files through the XML plugin's `XmlSchemaProvider` EP, so the files are recognized as XML and get schema-driven completion/validation.

## Goal
Make GtkBuilder `.ui` (and Glade `.glade`) files open as **XML** (not plain text) and auto-apply the bundled `gtk-ui.xsd` for code completion and inline validation of classes, properties and signals.

## Current state (what exists)
- **Artifacts committed**: `../src/main/resources/schemas/gtk-ui.xsd` (116 KB, **no target namespace**, root `<interface>`, generated from GNOME 50 GIR) and `gtk-ui-schema.json` (JSON Schema draft-07, generator artifact only).
- **Removed**: `GtkInterfaceSchemaProvider` + `GtkInterfaceSchemaProviderFactory` and their `JavaScript.JsonSchema.ProviderFactory` registration — the JSON schema backend (`com.jetbrains.jsonSchema`) has **no XML support** (verified: no `XmlSchemaProvider`/`XmlFile` code in `intellij.json.backend.jar`), so a JSON-schema provider can never affect XML files.
- **Registered**: `<standardResource url="urn:io.github.andrepg:flatpak-support:schemas:gtk-ui" path="schemas/gtk-ui.xsd" version="1"/>` (valid form — `url` + `path` are both `@RequiredElement` on `StandardResourceEP`; `path` is the bundled resource path, `url` a canonical identifier). Auxiliary only: standard resources resolve documents that *reference* `url`, and GtkBuilder `.ui` files carry no namespace/URL.
- **Broken UX**: `.ui`/`.glade` files open as Plain Text; no schema features are consulted.

## Decisions (confirmed)
1. **XSD via `XmlSchemaProvider`**, not JSON schema — the XML plugin's `com.intellij.xml.schemaProvider` EP (`XmlSchemaProvider`, in `module-intellij.xml.psi.jar`) is the only mechanism that applies schemas to XML files.
2. **`<fileType name="XML" extensions="ui;glade"/>`** — merges with the built-in XML file type (documented EP behavior) so the files are recognized as XML (highlighting, structure view, tag matching). Safe: `.ui` is also used by Qt Designer, `.glade` by Glade — both XML.
3. **No namespace in the XSD by design** — GtkBuilder `.ui` files are namespace-less; keep it that way and associate via provider filename matching, not namespace.
4. **Keep `gtk-ui-schema.json`** on the classpath (generator artifact; not registered).

## Steps
1. **`plugin.xml` — XML dependencies and file type**:
   - Add `<depends>com.intellij.modules.xml</depends>`.
   - Inside `<extensions defaultExtensionNs="com.intellij">`: `<fileType name="XML" extensions="ui;glade"/>`.
   - Register `<xml.schemaProvider implementation="io.github.andrepg.flatpak.schemas.providers.GtkInterfaceXmlSchemaProvider" id="flatpak-gtk-interface"/>` (EP example: `javaee-cdi` registers `<xml.schemaProvider implementation="com.intellij.cdi.model.CdiBeansXmlSchemaProvider" id="cdi_beans_xml"/>`).
   - Keep the existing `<standardResource .../>` registration as-is (already valid).
2. **New provider** `../src/main/kotlin/io/github/andrepg/flatpak/schemas/providers/GtkInterfaceXmlSchemaProvider.kt`:
   ```kotlin
   class GtkInterfaceXmlSchemaProvider : XmlSchemaProvider() {
       private val uiFileRegex = Regex(""".*\.(ui|glade)$""", RegexOption.IGNORE_CASE)

       override fun isAvailable(file: XmlFile): Boolean = file.name.matches(uiFileRegex)

       override fun getSchema(namespace: String, module: Module, file: PsiFile): XmlFile? {
           val resourceUrl = javaClass.getResource("/schemas/gtk-ui.xsd") ?: return null
           val virtualFile = VirtualFileManager.getInstance().findFileByUrl(resourceUrl.toString()) ?: return null
           return PsiManager.getInstance(module.project).findFile(virtualFile) as? XmlFile
       }

       override fun getAvailableNamespaces(file: XmlFile, namespace: String?): Set<String> =
           if (isAvailable(file)) setOf(Namespaces.GTK_INTERFACE) else emptySet()
   }
   ```
   - Namespace marker: pick a constant (e.g. `"https://glade.gnome.org/glade-3.0"` or a plugin URN). Prefer a value never present in real files so the schema is offered without binding namespace-less files to a namespace. Implementer to confirm behavior in `runIde` (see Validation).
   - Imports: `com.intellij.xml.XmlSchemaProvider`, `com.intellij.psi.xml.XmlFile`, `com.intellij.psi.PsiFile`, `com.intellij.openapi.module.Module`, `com.intellij.openapi.vfs.VirtualFileManager`, `com.intellij.psi.PsiManager`.
3. **Remove leftovers**: delete the now-empty `GtkInterface*` references if any remain in comments/docs; keep `FlatpakSchemaProviderFactory` (manifests are JSON — that path works).
4. **Housekeeping**:
   - Update `AGENTS.md` "GNOME/Adwaita UI support" to reflect the final wiring (already covers the approach; adjust "not yet implemented" wording once the provider is in).
   - Regenerate nothing — `gtk-ui.xsd` is committed and stable (`./gradlew extractGtkSchema` → `git diff --exit-code` clean).
   - Commit as a single `feat(schemas): serve gtk-ui.xsd to .ui/.glade via XmlSchemaProvider` (schema artifacts already committed separately).

## Risks
- **No-namespace auto-association** is the main unknown: the XML plugin's schema resolution for namespace-less files goes through `getAvailableNamespaces`/`getSchema` — verify empirically in `runIde`. If completion does not kick in:
  1. Check `isDumbAware`/indexing (schema PSI must be ready in dumb mode; override `isDumbAware()` if needed).
  2. Fall back to the "Schemas and DTDs" mapping: the `standardResource` registration makes `gtk-ui.xsd` assignable manually; report back if that is the only working path.
- `PsiManager.findFile` returns null before/after indexing for fresh resources — schema is a classpath resource, always present.
- `jar://` vs classpath URL: `javaClass.getResource()` returns a `jar:file:` URL inside the packed plugin; `VirtualFileManager.findFileByUrl` handles it (same util `JsonSchemaProviderFactory.getResourceFile` uses).

## Validation
- `./gradlew compileKotlin`, `./gradlew test` (existing 10 tests stay green).
- `./gradlew runIde` with a `test-data/sample.ui`:
  - File opens as XML: XML icon + highlighting + structure view (not Plain Text).
  - Completion: `<object class="GtkButton">` class variants, `<property name="label">`, `<signal name="clicked">`.
  - Validation: typo in `class`/`property name` flagged inline.
  - Daemon log free of `PluginException` on open.
- Optionally add a unit test: `isAvailable` regex matches `*.ui`/`*.glade` and rejects `*.json`/`*.xml`.

## Notes
- `.xml` files are intentionally **not** matched (too broad) — GtkBuilder `.xml` sniffing is out of scope; revisit if needed.
- Working tree may contain unrelated uncommitted user edits — keep this commit scoped to the schema wiring.
