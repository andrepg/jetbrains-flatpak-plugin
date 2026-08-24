package io.github.andrepg.gtk.schema.gir.patches

/**
 * Class/parent attributes accept known GIR classes or any app-defined class
 * name (identifier pattern) — GtkBuilder allows custom widget classes that no
 * GIR file can enumerate.
 */
data object ClassNameUnion : XsdPatch {
    override val id = "class-name-union"

    override val description =
        "class/parent attributes accept known GIR classes or any app-defined class name " +
            "(identifier pattern)."

    override val fragment =
        $$"""
        <xs:simpleType name="className">
          <xs:union>
            <xs:simpleType>
              <xs:restriction base="xs:string">
                ${classEnums}
              </xs:restriction>
            </xs:simpleType>
            <xs:simpleType>
              <xs:restriction base="xs:string">
                <xs:pattern value="[A-Za-z_][A-Za-z0-9_.]*"/>
              </xs:restriction>
            </xs:simpleType>
          </xs:union>
        </xs:simpleType>
        """
}
