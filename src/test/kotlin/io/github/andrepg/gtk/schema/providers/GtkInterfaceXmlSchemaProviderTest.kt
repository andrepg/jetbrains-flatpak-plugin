package io.github.andrepg.gtk.schema.providers

import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
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
    fun `matches xml files whose root element is the interface root`() {
        assertTrue(provider.isAvailable(xmlFile("main.xml", rootTag = "interface")))
        assertTrue(provider.isAvailable(xmlFile("window.Interface.xml", rootTag = "interface")))
    }

    @Test
    fun `rejects non interface files`() {
        assertFalse(provider.isAvailable(xmlFile("manifest.json")))
        assertFalse(provider.isAvailable(xmlFile("interface.xml")))
        assertFalse(provider.isAvailable(xmlFile("window.txt")))
    }

    @Test
    fun `rejects xml files with a different root element`() {
        assertFalse(provider.isAvailable(xmlFile("pom.xml", rootTag = "project")))
        assertFalse(provider.isAvailable(xmlFile("web.xml", rootTag = "web-app")))
        assertFalse(provider.isAvailable(xmlFile("broken.xml")))
    }

    private fun xmlFile(name: String, rootTag: String? = null): XmlFile {
        val root = rootTag?.let { tag ->
            Proxy.newProxyInstance(
                XmlTag::class.java.classLoader,
                arrayOf(XmlTag::class.java)
            ) { _, method, _ ->
                if (method.name == "getName") tag else null
            } as XmlTag
        }
        return Proxy.newProxyInstance(
            XmlFile::class.java.classLoader,
            arrayOf(XmlFile::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getName" -> name
                "getRootTag" -> root
                else -> null
            }
        } as XmlFile
    }
}
