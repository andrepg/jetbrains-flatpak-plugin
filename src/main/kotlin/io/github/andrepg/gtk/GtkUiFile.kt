package io.github.andrepg.gtk

/**
 * Shared file-name predicate for the GNOME/Adwaita `.ui`/`.glade` feature
 * surface, used by the preview factory, the render action and the XML schema
 * provider so every entry point recognizes the same files.
 */
fun isGtkUiFile(name: String): Boolean {
    val ext = name.substringAfterLast('.', "").lowercase()
    return ext == "ui" || ext == "glade"
}