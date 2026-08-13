package io.github.andrepg.flatpak.schemas.providers

import com.intellij.psi.xml.XmlFile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class GtkInterfaceXmlSchemaProviderTest {

    private val provider = GtkInterfaceXmlSchemaProvider()

    @Test
    fun `matches GtkBuilder and Glade interface files`() {
        assertTrue(provider.isAvailable(xmlFile("window.ui")))
        assertTrue(provider.isAvailable(xmlFile("MainWindow.ui")))
        assertTrue(provider.isAvailable(xmlFile("preferences.glade")))
        assertTrue(provider.isAvailable(xmlFile("dialog.GLADE")))
    }

    @Test
    fun `rejects non interface files`() {
        assertFalse(provider.isAvailable(xmlFile("manifest.json")))
        assertFalse(provider.isAvailable(xmlFile("interface.xml")))
        assertFalse(provider.isAvailable(xmlFile("window.txt")))
    }

    private fun xmlFile(name: String): XmlFile =
        Proxy.newProxyInstance(
            XmlFile::class.java.classLoader,
            arrayOf(XmlFile::class.java)
        ) { _, method, _ ->
            if (method.name == "getName") name else null
        } as XmlFile
}
