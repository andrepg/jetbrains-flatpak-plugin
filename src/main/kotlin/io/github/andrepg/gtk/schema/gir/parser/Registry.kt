package io.github.andrepg.gtk.schema.gir.parser

/**
 * Index of all parsed types, keyed by `namespace.name`, with helpers to
 * resolve inheritance/interface chains and flatten their members.
 */
class Registry(
    entries: List<TypeEntry>,
) {
    private val byKey = LinkedHashMap<String, TypeEntry>()

    init {
        entries.forEach { byKey[it.key] = it }
    }

    /** Resolves a parent/implements reference against [fromNamespace]. */
    fun resolve(
        fromNamespace: String,
        ref: String,
    ): TypeEntry? = byKey[if ('.' in ref) ref else "$fromNamespace.$ref"]

    /** All types, deduplicated and merged by C type, sorted by C type. */
    fun allTypes(): List<TypeEntry> {
        val byCType = LinkedHashMap<String, TypeEntry>()
        for (entry in byKey.values) {
            val previous = byCType[entry.cType]
            byCType[entry.cType] = previous?.copy(
                properties = previous.properties + entry.properties,
                signals = previous.signals + entry.signals,
            )
                ?: entry
        }
        return byCType.values.sortedBy { it.cType }
    }

    /**
     * Collects [pick] (properties or signals) from [root] and everything it
     * inherits or implements, walking parents and interface references
     * recursively across namespaces.
     */
    fun flattened(
        root: TypeEntry,
        pick: (TypeEntry) -> Set<String>,
    ): List<String> {
        val visited = mutableSetOf<String>()
        val result = LinkedHashSet<String>()

        fun walk(entry: TypeEntry) {
            if (!visited.add(entry.key)) return
            result += pick(entry)
            entry.parent?.let { resolve(entry.namespace, it)?.let(::walk) }
            entry.requires.forEach { resolve(entry.namespace, it)?.let(::walk) }
        }

        walk(root)
        return result.sorted()
    }
}
