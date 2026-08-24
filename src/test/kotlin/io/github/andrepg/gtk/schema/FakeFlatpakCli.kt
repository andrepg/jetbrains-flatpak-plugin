package io.github.andrepg.gtk.schema

import java.io.File

/**
 * Test double for the flatpak CLI used by the schema tests: a tiny POSIX shell
 * script whose canned outputs are baked in at creation time. Invocations whose
 * first argument is `list` (i.e. `flatpak list --runtime …`) print [runtimes];
 * every other invocation prints [location]'s path, mimicking
 * `flatpak info --show-location` (empty output when [location] is null).
 */
object FakeFlatpakCli {
    fun install(
        dir: File,
        runtimes: String = "",
        location: File? = null,
    ): File {
        val rows = dir.resolve("flatpak-list.txt").apply { writeText(runtimes) }
        val info = dir.resolve("flatpak-info.txt").apply { writeText(location?.absolutePath.orEmpty()) }
        return dir.resolve("flatpak").apply {
            writeText(
                """
                #!/bin/sh
                if [ "${'$'}1" = "list" ]; then
                  cat '${rows.absolutePath}'
                else
                  cat '${info.absolutePath}'
                fi
                """.trimIndent(),
            )
            setExecutable(true)
        }
    }

    /**
     * Creates `<root>/files/share/gir-1.0`, the layout `flatpak info
     * --show-location` points at; pass the returned dir's parent as [location].
     */
    fun girRoot(root: File): File = root.resolve("files/share/gir-1.0").apply { mkdirs() }

    /** Copies the hermetic GIR fixtures from `test-data/gir` into [girDir]. */
    fun copyFixtures(girDir: File) {
        File("test-data/gir").listFiles()?.forEach { girDir.resolve(it.name).writeText(it.readText()) }
    }
}
