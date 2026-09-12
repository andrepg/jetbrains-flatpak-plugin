package io.github.andrepg.gtk.preview

import io.github.andrepg.shared.log.Log
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Expands project-defined GtkBuilder templates into plain `<object>` elements
 * so the C renderer can display custom widget classes.
 *
 * A `.ui` file that defines:
 * ```xml
 * <template class="MyWidget" parent="GtkBox">
 *   <property name="spacing">4</property>
 *   <child>…</child>
 * </template>
 * ```
 * can be referenced from the target as:
 * ```xml
 * <object class="MyWidget" id="w">
 *   <property name="margin">8</property>
 * </object>
 * ```
 * and this resolver will replace it with:
 * ```xml
 * <object class="GtkBox" id="w">
 *   <property name="spacing">4</property>
 *   <property name="margin">8</property>
 *   <child>…</child>
 * </object>
 * ```
 *
 * Template properties are inherited unless the target overrides them by name.
 * Inner objects from templates have their `id` attributes stripped (matching
 * the Cambalache approach) so duplicate ids are avoided when a template is
 * used more than once.
 *
 * The resolver is pure JDK — no IntelliJ platform imports — so it stays
 * unit-testable outside the IDE.
 */
object UiTemplateResolver {
    private val log = Log.getInstance(UiTemplateResolver::class.java)

    /**
     * Maximum number of recursive template expansions before giving up.
     * Prevents runaway expansion from deeply nested or pathological template
     * graphs.  Practical UIs never exceed this.
     */
    private const val MAX_DEPTH = 5

    private val SKIP_DIRS =
        setOf("_build", "build", ".gradle", ".git", ".flatpak-builder", "node_modules")

    private val UI_EXTENSIONS = setOf("ui", "glade")

    // `internal` so same-module tests can inspect the registry directly.
    internal data class TemplateInfo(
        val parent: String,
        val sourcePath: Path,
    )

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Scans [projectBasePath] for `.ui`/`.glade` files containing
     * `<template class="…" parent="…">` elements, then expands every
     * `<object class="…">` reference in [uiContent] whose class is in the
     * template registry.
     *
     * If the project contains no template definitions the original content
     * is returned unchanged (fast path).
     *
     * @param uiContent       the raw XML string of the `.ui` file to resolve
     * @param projectBasePath root of the project (template files are scanned from here)
     * @return resolved XML string (or the original on parse failure / no templates)
     */
    fun resolve(
        uiContent: String,
        projectBasePath: Path,
    ): String {
        val registry = buildTemplateRegistry(projectBasePath)
        if (registry.isEmpty()) return uiContent

        val doc = parseXml(uiContent) ?: return uiContent
        val changed = processElement(doc.documentElement, registry, mutableSetOf(), 0)
        // Avoid re-serializing (reformatting) when nothing was expanded.
        return if (changed) serialize(doc) else uiContent
    }

    // ------------------------------------------------------------------
    // Template registry
    // ------------------------------------------------------------------

    internal fun buildTemplateRegistry(projectBasePath: Path): Map<String, TemplateInfo> {
        if (!Files.isDirectory(projectBasePath)) return emptyMap()

        val registry = mutableMapOf<String, TemplateInfo>()
        Files.walk(projectBasePath).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .filter { isUiFile(it) }
                .forEach { file ->
                    try {
                        val content = Files.readString(file, Charsets.UTF_8)
                        val doc = parseXml(content) ?: return@forEach
                        val templates = doc.getElementsByTagName("template")
                        for (i in 0 until templates.length) {
                            val el = templates.item(i) as? Element ?: continue
                            val cls = el.getAttribute("class")
                            val parent = el.getAttribute("parent")
                            if (cls.isNotEmpty() && parent.isNotEmpty()) {
                                // Keep first occurrence; later duplicates shadowed by putIfAbsent.
                                registry.putIfAbsent(cls, TemplateInfo(parent, file))
                            }
                        }
                    } catch (e: Exception) {
                        log.debug("Skipping unreadable UI file: $file (${e.message})")
                    }
                }
        }
        return registry
    }

    // ------------------------------------------------------------------
    // Tree walker + expander
    // ------------------------------------------------------------------

/**
     * Depth-first walk.  For each `<object>` element whose `class` attribute
     * is in the template registry (and not currently being expanded — cycle
     * guard) the element is replaced with the template's parent class,
     * inherited properties and inlined children.  After expansion the newly
     * inserted children are processed for further template references at
     * depth + 1.
     *
     * @return `true` if at least one element was expanded (document mutated).
     */
    private fun processElement(
        element: Element,
        registry: Map<String, TemplateInfo>,
        expanding: MutableSet<String>,
        depth: Int,
    ): Boolean {
        var expandedClass: String? = null

        if (element.tagName == "object" && element.hasAttribute("class")) {
            val cls = element.getAttribute("class")
            if (cls.isNotEmpty() && cls in registry && cls !in expanding && depth < MAX_DEPTH) {
                expanding.add(cls)
                expandElement(element, registry.getValue(cls))
                expandedClass = cls
            }
        }

        // Snapshot children AFTER potential expansion so the newly inserted
        // template children are included.  mapNotNull avoids ConcurrentModification
        // issues since childNodes is a live NodeList.
        val childNodes = element.childNodes
        val children = (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }
        val childDepth = if (expandedClass != null) depth + 1 else depth
        var anyChanged = expandedClass != null
        for (child in children) {
            anyChanged = processElement(child, registry, expanding, childDepth) || anyChanged
        }

        if (expandedClass != null) {
            expanding.remove(expandedClass)
        }
        return anyChanged
    }

    // ------------------------------------------------------------------
    // DOM mutation
    // ------------------------------------------------------------------

    /**
     * Replaces the class and children of [el] with the content of the
     * matching `<template>` element in [templateInfo]'s source file.
     *
     * Property inheritance: the template root's `<property>` elements are
     * prepended unless the target object already defines a property with the
     * same `name` (target wins).  The `<child>` and other content elements
     * from the template are deep-cloned and appended before the target's own
     * children.  All imported elements have their `id` attributes stripped.
     */
    private fun expandElement(
        el: Element,
        templateInfo: TemplateInfo,
    ) {
        val doc = el.ownerDocument
        val className = el.getAttribute("class")

        val templateDoc =
            runCatching {
                parseXml(Files.readString(templateInfo.sourcePath, Charsets.UTF_8))
            }.getOrNull() ?: return
        val templateEl = findTemplateElement(templateDoc, className) ?: return

        // Partition template's element children into properties and body.
        val templateProps = mutableListOf<Element>()
        val templateBody = mutableListOf<Element>()
        for (node in elementChildren(templateEl)) {
            if (node.tagName == "property") templateProps.add(node) else templateBody.add(node)
        }

        // Target's existing properties (for override check).
        val targetProps = mutableListOf<Element>()
        val targetBody = mutableListOf<Element>()
        for (node in elementChildren(el)) {
            if (node.tagName == "property") targetProps.add(node) else targetBody.add(node)
        }
        val targetPropNames = targetProps.map { it.getAttribute("name") }.toSet()

        // Replace the class with the template's parent.
        el.setAttribute("class", templateInfo.parent)

        // Clear all children.
        while (el.childNodes.length > 0) el.removeChild(el.firstChild)

        // Rebuild: inherited props → target props → template body → target body.
        for (prop in templateProps) {
            if (prop.getAttribute("name") !in targetPropNames) {
                el.appendChild(doc.importNode(prop, true))
            }
        }
        for (prop in targetProps) {
            el.appendChild(prop)
        }
        for (child in templateBody) {
            val imported = doc.importNode(child, true) as Element
            stripIds(imported)
            el.appendChild(imported)
        }
        for (child in targetBody) {
            el.appendChild(child)
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun findTemplateElement(
        doc: Document,
        className: String,
    ): Element? {
        val templates = doc.getElementsByTagName("template")
        for (i in 0 until templates.length) {
            val el = templates.item(i) as? Element ?: continue
            if (el.getAttribute("class") == className) return el
        }
        return null
    }

    private fun elementChildren(parent: Element): List<Element> {
        val result = mutableListOf<Element>()
        val nodes = parent.childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node is Element) result.add(node)
        }
        return result
    }

    /**
     * Recursively strips `id` attributes from [el] and its descendants so
     * inlined template children never collide with target ids.
     */
    private fun stripIds(el: Element) {
        if (el.hasAttribute("id")) el.removeAttribute("id")
        val nodes = el.childNodes
        for (i in 0 until nodes.length) {
            val child = nodes.item(i)
            if (child is Element) stripIds(child)
        }
    }

    private fun isUiFile(path: Path): Boolean {
        val pathStr = path.toString().replace('\\', '/')
        for (dir in SKIP_DIRS) {
            if (pathStr.contains("/$dir/")) return false
        }
        val name = path.fileName.toString().lowercase()
        return UI_EXTENSIONS.any { name.endsWith(".$it") }
    }

    // ------------------------------------------------------------------
    // XML I/O
    // ------------------------------------------------------------------

    private fun parseXml(content: String): Document? =
        try {
            val factory =
                DocumentBuilderFactory.newInstance().apply {
                    isNamespaceAware = false
                    setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                    // Prevent XXE.
                    setFeature("http://xml.org/sax/features/external-general-entities", false)
                    setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                    setFeature(
                        "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                        false,
                    )
                }
            factory.newDocumentBuilder().parse(InputSource(StringReader(content)))
        } catch (e: Exception) {
            log.debug("XML parse failed: ${e.message}")
            null
        }

    private fun serialize(doc: Document): String {
        val tf =
            TransformerFactory.newInstance().apply {
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            }
        val transformer =
            tf.newTransformer().apply {
                setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
                setOutputProperty(OutputKeys.ENCODING, "UTF-8")
                setOutputProperty(OutputKeys.INDENT, "no")
                setOutputProperty(OutputKeys.STANDALONE, "yes")
            }
        val writer = StringWriter()
        transformer.transform(DOMSource(doc), StreamResult(writer))
        return writer.toString()
    }
}
