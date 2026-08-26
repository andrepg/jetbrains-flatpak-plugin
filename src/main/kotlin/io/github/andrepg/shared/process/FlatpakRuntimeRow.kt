package io.github.andrepg.shared.process

/** One row of `flatpak list --runtime --columns=application,branch,installation`. */
data class FlatpakRuntimeRow(
    val appId: String,
    val branch: String,
    val installation: String,
)

/**
 * Parses tab-separated `flatpak list --runtime` output.
 *
 * @param output raw stdout of the flatpak command
 * @return the parsed runtime rows, skipping blank/partial lines
 */
fun parseFlatpakRuntimeList(output: String): List<FlatpakRuntimeRow> =
    output
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { line ->
            val columns = line.split('\t')
            if (columns.size < 2) return@mapNotNull null
            FlatpakRuntimeRow(columns[0], columns[1], columns.getOrNull(2).orEmpty())
        }.toList()
