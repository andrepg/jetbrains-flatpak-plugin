package io.github.andrepg.gtk.schema.gir.patches

data object SignalElementProperties : XsdPatch {
    override val id = "signal-element"

    override val description = "Signals expose handler/object/swapped/after alongside the name enum."

    override val fragment =
        $$"""
        <xs:element name="signal">
          <xs:complexType mixed="true">
            <xs:attribute name="name" use="required">
              <xs:simpleType>
                <xs:restriction base="xs:string">
                ${signalEnums}
                </xs:restriction>
              </xs:simpleType>
            </xs:attribute>
            <xs:attribute name="handler" type="xs:string"/>
            <xs:attribute name="object" type="xs:string"/>
            <xs:attribute name="swapped">
              <xs:simpleType>
                <xs:restriction base="xs:string">
                  <xs:enumeration value="yes"/>
                  <xs:enumeration value="no"/>
                  <xs:enumeration value="true"/>
                  <xs:enumeration value="false"/>
                </xs:restriction>
              </xs:simpleType>
            </xs:attribute>
            <xs:attribute name="after">
              <xs:simpleType>
                <xs:restriction base="xs:string">
                  <xs:enumeration value="yes"/>
                  <xs:enumeration value="no"/>
                  <xs:enumeration value="true"/>
                  <xs:enumeration value="false"/>
                </xs:restriction>
              </xs:simpleType>
            </xs:attribute>
          </xs:complexType>
        </xs:element>
        """.trimIndent()
}
