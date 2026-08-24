package io.github.andrepg.gtk.schema.gir.patches

data object ElementPropertyValues : XsdPatch {
    override val id = "property-element"

    override val description =
        "Widget-valued properties may contain a nested <object>; translatable properties " +
            "carry translatable/context/comments."

    override val fragment =
        $$"""
        <xs:element name="property">                                     
          <xs:complexType mixed="true">                                  
            <xs:sequence minOccurs="0" maxOccurs="1">                    
              <xs:element ref="object"/>                                 
            </xs:sequence>                                               
            <xs:attribute name="name" use="required">                    
              <xs:simpleType>                                            
                <xs:restriction base="xs:string">                        
                  ${propertyEnums}                                       
                </xs:restriction>                                        
              </xs:simpleType>                                           
            </xs:attribute>                                              
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
            <xs:attribute name="comments" type="xs:string"/>             
          </xs:complexType>                                              
        </xs:element>                                                    
        """.trimIndent()
}
