package io.github.andrepg.gtk.schema.gir.builder

/**
 * Static GtkBuilder XSD skeleton — the declarative base document that
 * [SchemaPatches][io.github.andrepg.gtk.schema.gir.SchemaPatches] patches.
 *
 * Kept as data instead of StringBuilder assembly so the grammar reads and
 * diffs like the XSD it is. The `gb-patch:*` marker lines mark where
 * GIR-derived fragments are spliced in at generation time (see
 * `XsdPatch.id`); they must stay in sync with `SchemaPatches.xsdPatches`.
 */
internal object XsdSkeleton {
    val RAW: String =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
          <xs:annotation>
            <xs:documentation>
              GtkBuilder UI layout schema for GTK 4 + Libadwaita + GtkSource-5.
              Generated from the GObject Introspection (GIR) files of the GNOME SDK
              by Flatpak DevTools at schema generation time.
              No target namespace: applies to plain (namespace-less) GtkBuilder .ui files.
            </xs:documentation>
          </xs:annotation>
          <xs:element name="interface">
            <xs:complexType>
              <xs:choice minOccurs="0" maxOccurs="unbounded">
                <xs:element ref="requires"/>
                <xs:element ref="object"/>
                <xs:element ref="template"/>
                <xs:element ref="menu"/>
              </xs:choice>
            </xs:complexType>
          </xs:element>
          <xs:element name="requires">
            <xs:complexType>
              <xs:attribute name="lib" type="xs:string" use="required"/>
              <xs:attribute name="version" type="xs:string" use="required"/>
            </xs:complexType>
          </xs:element>
          <xs:element name="object" type="objectType"/>

        <!-- gb-patch:class-name-union -->
          <xs:complexType name="objectType">
            <xs:choice minOccurs="0" maxOccurs="unbounded">
              <xs:element ref="condition"/>
              <xs:element ref="setter"/>
              <xs:element ref="property"/>
              <xs:element ref="signal"/>
              <xs:element ref="child"/>
              <xs:element ref="layout"/>
              <xs:element ref="packing"/>
              <xs:element ref="accessibility"/>
              <xs:element ref="style"/>
              <xs:element ref="attributes"/>
            </xs:choice>
            <xs:attribute name="class" type="className" use="required"/>
            <xs:attribute name="id" type="xs:string"/>
          </xs:complexType>
        <!-- gb-patch:property-element -->
        <!-- gb-patch:signal-element -->
          <xs:element name="child">
            <xs:complexType>
              <xs:sequence>
                <xs:element ref="object" minOccurs="1" maxOccurs="1"/>
                <xs:element ref="layout" minOccurs="0"/>
                <xs:element ref="packing" minOccurs="0"/>
              </xs:sequence>
              <xs:attribute name="type" type="xs:string"/>
            </xs:complexType>
          </xs:element>
          <xs:element name="template">
            <xs:complexType>
              <xs:sequence>
                <xs:element ref="property" minOccurs="0" maxOccurs="unbounded"/>
                <xs:element ref="signal" minOccurs="0" maxOccurs="unbounded"/>
                <xs:element ref="child" minOccurs="0" maxOccurs="unbounded"/>
              </xs:sequence>
              <xs:attribute name="class" type="xs:string" use="required"/>
              <xs:attribute name="parent" type="className" use="required"/>
            </xs:complexType>
          </xs:element>
          <xs:element name="layout">
            <xs:complexType>
              <xs:sequence>
                <xs:element ref="property" minOccurs="0" maxOccurs="unbounded"/>
              </xs:sequence>
            </xs:complexType>
          </xs:element>
          <xs:element name="packing">
            <xs:complexType>
              <xs:sequence>
                <xs:element ref="property" minOccurs="0" maxOccurs="unbounded"/>
              </xs:sequence>
            </xs:complexType>
          </xs:element>
          <xs:element name="accessibility">
            <xs:complexType>
              <xs:sequence>
                <xs:element ref="property" minOccurs="0" maxOccurs="unbounded"/>
                <xs:element ref="relation" minOccurs="0" maxOccurs="unbounded"/>
              </xs:sequence>
            </xs:complexType>
          </xs:element>
          <xs:element name="relation">
            <xs:complexType mixed="true">
              <xs:attribute name="target" type="xs:string"/>
            </xs:complexType>
          </xs:element>
          <xs:element name="condition">
            <xs:complexType mixed="true"/>
          </xs:element>
          <xs:element name="setter">
            <xs:complexType mixed="true">
              <xs:attribute name="object" type="xs:string"/>
              <xs:attribute name="property" type="xs:string" use="required"/>
            </xs:complexType>
          </xs:element>
          <xs:element name="style">
            <xs:complexType>
              <xs:sequence>
                <xs:element ref="class" minOccurs="0" maxOccurs="unbounded"/>
                <xs:element ref="node" minOccurs="0" maxOccurs="unbounded"/>
              </xs:sequence>
            </xs:complexType>
          </xs:element>
          <xs:element name="node">
            <xs:complexType>
              <xs:sequence>
                <xs:element ref="class" minOccurs="0" maxOccurs="unbounded"/>
              </xs:sequence>
              <xs:attribute name="id" type="xs:string"/>
            </xs:complexType>
          </xs:element>
          <xs:element name="class">
            <xs:complexType>
              <xs:attribute name="name" type="xs:string" use="required"/>
            </xs:complexType>
          </xs:element>
          <xs:element name="menu">
            <xs:complexType>
              <xs:sequence>
                <xs:element ref="attribute" minOccurs="0" maxOccurs="unbounded"/>
                <xs:element ref="item" minOccurs="0" maxOccurs="unbounded"/>
                <xs:element ref="section" minOccurs="0" maxOccurs="unbounded"/>
              </xs:sequence>
              <xs:attribute name="id" type="xs:string"/>
            </xs:complexType>
          </xs:element>
          <xs:element name="item">
            <xs:complexType>
              <xs:sequence>
                <xs:element ref="attribute" minOccurs="0" maxOccurs="unbounded"/>
                <xs:element ref="item" minOccurs="0" maxOccurs="unbounded"/>
                <xs:element ref="submenu" minOccurs="0" maxOccurs="unbounded"/>
                <xs:element ref="section" minOccurs="0" maxOccurs="unbounded"/>
              </xs:sequence>
            </xs:complexType>
          </xs:element>
          <xs:element name="submenu">
            <xs:complexType>
              <xs:sequence>
                <xs:element ref="item" minOccurs="0" maxOccurs="unbounded"/>
                <xs:element ref="section" minOccurs="0" maxOccurs="unbounded"/>
              </xs:sequence>
            </xs:complexType>
          </xs:element>
          <xs:element name="section">
            <xs:complexType>
              <xs:sequence>
                <xs:element ref="attribute" minOccurs="0" maxOccurs="unbounded"/>
                <xs:element ref="item" minOccurs="0" maxOccurs="unbounded"/>
              </xs:sequence>
            </xs:complexType>
          </xs:element>
          <xs:element name="attributes">
            <xs:complexType>
              <xs:sequence>
                <xs:element ref="attribute" minOccurs="0" maxOccurs="unbounded"/>
              </xs:sequence>
            </xs:complexType>
          </xs:element>
          <xs:element name="attribute">
            <xs:complexType mixed="true">
              <xs:attribute name="name" type="xs:string" use="required"/>
              <xs:attribute name="value" type="xs:string"/>
              <xs:attribute name="translatable">
                <xs:simpleType>
                  <xs:restriction base="xs:string">
                    <xs:enumeration value="yes"/>
                    <xs:enumeration value="no"/>
                    <xs:enumeration value="true"/>
                    <xs:enumeration value="false"/>
                  </xs:restriction>
                </xs:simpleType>
              </xs:attribute>
              <xs:attribute name="context" type="xs:string"/>
            </xs:complexType>
          </xs:element>
        </xs:schema>
        """.trimIndent()
}
