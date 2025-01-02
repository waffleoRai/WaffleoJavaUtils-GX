package waffleoRai_Containers.maxis.ts3.savefmts.xml2java;

import java.util.LinkedList;
import java.util.List;

import org.w3c.dom.Element;

import waffleoRai_Files.XMLReader;
import waffleoRai_Utils.StringUtils;

public class AnonStructField extends StructField{
	
	public List<DataField> members;
	
	public AnonStructField() {
		fieldType = TS3XJ.FIELD_TYPE_STRUCT_ANON;
		members = new LinkedList<DataField>();
	}
	
	public void initFromXmlElement(Element xmlElement) {
		super.initFromXmlElement(xmlElement);
		typeName = String.format("AnonClass%08X", name.hashCode()); //These can be ctrl+f replaced if desired
		javaTypeName = typeName;
		
		//Check for specified name
		String aval = xmlElement.getAttribute("InnerStructName");
		if(aval != null && !aval.isEmpty()) {
			typeName = aval;
			javaTypeName = StringUtils.capitalize(typeName);
		}
		
		//Read members
		List<Element> children = XMLReader.getChildElements(xmlElement);
		for(Element child : children) {
			DataField member = TS3XJ.parseXmlNode(child);
			if(member != null) members.add(member);
		}
		fieldType = TS3XJ.FIELD_TYPE_STRUCT_ANON;
	}
	
	public static AnonStructField readAnonStructNodes(Element parent, List<Element> children) {
		AnonStructField myStruct = new AnonStructField();
		
		//Copy properties from parent
		myStruct.initFromXmlElement(parent);
		
		//Read members first, then derive remaining properties.
		/*int someHash = 0;
		for(Element child : children) {
			DataField member = TS3XJ.parseXmlNode(child);
			if(member != null) {
				myStruct.members.add(member);
				someHash ^= member.name.hashCode();
			}
		}
		myStruct.fieldType = TS3XJ.FIELD_TYPE_STRUCT_ANON;
		
		myStruct.xmlNodeName = "PSChild";
		myStruct.name = String.format("Anon%08X", someHash);
		myStruct.typeName = String.format("AnonClass%08X", myStruct.name.hashCode());
		myStruct.javaTypeName = myStruct.typeName;
		myStruct.name = StringUtils.uncapitalize(myStruct.name);
		myStruct.psidPseudoEnumName = "PSID_" + myStruct.name.toUpperCase();
		myStruct.xmlKeyName = "XMLKEY_" + myStruct.name.toUpperCase();*/
		
		return myStruct;
	}
	
	public static AnonStructField readAnonStructNode(Element node) {
		AnonStructField myStruct = new AnonStructField();
		myStruct.initFromXmlElement(node);
		return myStruct;
	}

	public void getAnonStructNodes(List<DataField> list) {
		if(list == null) return;
		list.add(this);
		for(DataField member : members) {
			member.getAnonStructNodes(list);
		}
	}
	
}
