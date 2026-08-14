package io.github.andrepg.flatpak.runs.execution

data object CommandExecutionArguments {
    val ENABLE_PORTALS = setOf(
        "--talk-name=org.freedesktop.portal.*",
        "--device=dri",
        "--env=GTK_USE_PORTAL=1"
    )

    val ENABLE_THEMES = setOf(
        "--filesystem=xdg-config/gtk-3.0:ro",
        "--filesystem=xdg-data/icons:ro",
        "--filesystem=xdg-data/themes:ro",
        "--filesystem=xdg-config/glib-2.0",
    )

    val ENABLE_AUDIO = setOf(
        "--socket=pulseaudio",
    )

    val ENABLE_WAYLAND = setOf(
        "--socket=wayland",
    )

    val FORCE_CLEAN = setOf(
        "--force-clean",
    )
}
