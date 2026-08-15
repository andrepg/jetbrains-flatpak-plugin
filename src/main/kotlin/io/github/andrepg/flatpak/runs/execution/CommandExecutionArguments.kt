package io.github.andrepg.flatpak.runs.execution

import java.io.File

data object CommandExecutionArguments {
    val ENABLE_PORTALS =
        setOf(
            "--talk-name=org.freedesktop.portal.*",
            "--device=dri",
            "--env=GTK_USE_PORTAL=1",
        )

    val ENABLE_THEMES =
        setOf(
            "--filesystem=xdg-config/gtk-3.0:ro",
            "--filesystem=xdg-data/icons:ro",
            "--filesystem=xdg-data/themes:ro",
            "--filesystem=xdg-config/glib-2.0",
        )

    val ENABLE_AUDIO =
        setOf(
            "--socket=pulseaudio",
        )

    val ENABLE_WAYLAND =
        setOf(
            "--socket=wayland",
        )

    /**
     * D-Bus sockets exposed to the app: without them a GNOME app has no
     * session or system bus at all (flatpak-builder's `--run` starts with an
     * empty context). `flatpak run` maps `--socket=session-bus` to the host's
     * flatpak bus proxy, so the sockets only work when the host actually
     * exposes one (see [hostHasFlatpakBus]). [RunCommandFactory] adds them
     * before the opt-in flags when the bus is available, and skips them
     * otherwise — the app then gets flatpak's filtered default bus, matching
     * what GNOME Builder does.
     */
    val DEFAULT_BUS =
        setOf(
            "--socket=session-bus",
            "--socket=system-bus",
        )

    /**
     * Whether the host exposes the flatpak session bus proxy that
     * `--socket=session-bus`/`--socket=system-bus` bind-mount into the sandbox.
     * Missing in rootless/flatpak-in-flatpak setups (e.g. a dev container),
     * where bwrap aborts with `Can't find source path /run/flatpak/bus`.
     */
    fun hostHasFlatpakBus(): Boolean = File("/run/flatpak/bus").exists()

    val FORCE_CLEAN =
        setOf(
            "--force-clean",
        )
}
