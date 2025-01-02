package waffleoRai_Containers.maxis.ts3.savefmts.xml2java;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

import org.w3c.dom.Element;

import waffleoRai_Files.XMLReader;
import waffleoRai_Utils.StringUtils;

public class ListField extends DataField{

	public DataField elementType;
	
	public int lenInt = 0;
	public String lenString = null;
	
	public ListField() {
		fieldType = TS3XJ.FIELD_TYPE_LIST;
	}
	
	public void initFromXmlElement(Element xmlElement) {
		super.initFromXmlElement(xmlElement);
		fieldType = TS3XJ.FIELD_TYPE_LIST;

		lenInt = getArrLenAsInt();
		lenString = getArrLenString();
		
		List<Element> children = XMLReader.getChildElements(xmlElement);
		int childCount = children.size();
		if(childCount < 2) {
			//May or may not be an anonymous struct
			Element child = children.get(0);
			elementType = TS3XJ.parseXmlNode(child);
		}
		else {
			//Definitely a anonymous structure
			elementType = AnonStructField.readAnonStructNodes(xmlElement, children);
		}
		
		javaTypeName = elementType.javaTypeName;
		typeName = elementType.typeName;
		if(elementType.name == null || elementType.name.isEmpty()) {
			elementType.name = "listmem";
		}
		
		if(elementType.fieldType == TS3XJ.FIELD_TYPE_PRIM) {
			javaTypeName = StringUtils.capitalize(elementType.javaTypeName);
			if(javaTypeName.equals("Int")) javaTypeName = "Integer";
		}
		
		if(listPSID == null || listPSID.isEmpty()) {
			listPSID = xmlNode.getAttribute("PSIDBase");
			try {
				basePSID = StringUtils.parseUnsignedInt(xmlNode.getAttribute("PSIDBase"));
			}
			catch(Exception ex) {
				listPSID = xmlNode.getAttribute("PSIDBase");
			}	
		}
	}
	
	public boolean hasFixedSize() {return false;}
	public boolean canBeNull() {return false;}
	
	public boolean isXmlMultiLine() {return true;}
	
	public int getBaseSize() {return 0;}
	
	public void writeSizeUpdate(Writer out, String indent) throws IOException {
		if(super.len.startsWith("Var:")) {
			out.write(indent + lenString + " = " + name + ".size();\n");
		}
	}
	
	public void writeVarSizeAddition(Writer out, String indent, String sizeVarName) throws IOException {
		if(elementType.fieldType == TS3XJ.FIELD_TYPE_PRIM) {
			int eSize = DataField.getPrimSizeBytes(elementType.typeName);
			out.write(indent + sizeVarName + " += ");
			if(eSize > 1) {
				out.write("(" + name + ".size() * " + eSize + ");\n");
			}
			else out.write(name + ".size();\n");
		}
		else if(elementType.fieldType == TS3XJ.FIELD_TYPE_ENUM) {
			//TODO
		}
		else {
			out.write(indent + "for(" + javaTypeName + " " + elementType.name + " : " + name + "){\n");
			elementType.writeVarSizeAddition(out, indent + "\t", sizeVarName);
			out.write(indent + "}\n");
		}
	}
	
	public void writeVarDeclaration(Writer out, String indent) throws IOException {
		out.write(indent + "public ArrayList<" + javaTypeName + "> " + name);
		out.write(" = new ArrayList<" + javaTypeName + ">();\n");
	}
	
	public void writeReadBinCode(Writer out, String indent, String inputName) throws IOException{
		out.write(indent + name + ".ensureCapacity(" + lenString + ");\n");
		out.write(indent + "for(int i = 0; i < " + lenString + "; i++){\n");
		out.write(indent + "\t" + javaTypeName + " " + elementType.name +  " = ");
		elementType.writeReadBinCodeList(out, inputName);
		out.write(";\n");
		out.write(indent + "\tif(" + elementType.name + " != null) ");
		out.write(name + ".add(" + elementType.name + ");\n");
		out.write(indent + "}\n");
	}
	
	public void writeWriteBinCode(Writer out, String indent, String bufferName) throws IOException{
		String useIndent = indent;
		if(optional) {
			useIndent += "\t";
			out.write(indent + "if ((" + name + " != null) && !" + name + ".isEmpty()){\n");
		}
		
		out.write(useIndent + "for(" + javaTypeName + " " + elementType.name + " : " + name + "){\n");
		out.write(useIndent + "\t");
		elementType.writeWriteBinCodeList(out, bufferName);
		out.write(";\n");
		out.write(useIndent + "}\n");
		
		if(optional) {
			out.write(indent + "}\n");
		}
	}
	
	public void writeReadXmlCode(Writer out, String indent, String inputName) throws IOException{
		out.write(indent + "child = XMLReader.getFirstChildElementWithTagAndAttribute(" + inputName);
		out.write(", \"List\", \"VarName\", " + xmlKeyName + ");\n");
		out.write(indent + "if(child != null){\n");
		out.write(indent + "\t" + name + ".ensureCapacity(" + lenString + ");\n");
		
		if(elementType.fieldType == TS3XJ.FIELD_TYPE_PRIM) {
			out.write(indent + "\tList<Element> gclist = XMLReader.getChildElementsWithTag(child, \"ListMember\");\n\"");
			out.write(indent + "\tfor(Element gc : gclist){\n");
			out.write(indent + "\t\taval = gc.getAttribute(\"Value\");\n");
			out.write(indent + "\t\tif((aval != null) && !aval.isEmpty()) ");
			out.write(name + ".add(");
			String fcall = DataField.getPrimReadXmlFuncCall(elementType.typeName, "aval");
			out.write(fcall + ");\n");
			out.write(indent + "\t}\n");
		}
		else if(elementType.fieldType == TS3XJ.FIELD_TYPE_ENUM) {
			out.write(indent + "\tList<Element> gclist = XMLReader.getChildElementsWithTag(child, \"ListMember\");\n\"");
			out.write(indent + "\tfor(Element gc : gclist){\n");
			out.write(indent + "\t\taval = gc.getAttribute(\"Value\");\n");
			out.write(indent + "\t\tif((aval != null) && !aval.isEmpty()) ");
			out.write(name + ".add(");
			String fcall = elementType.javaTypeName + ".stringToValue(aval)";
			out.write(fcall + ");\n");
			out.write(indent + "\t}\n");
		}
		else {
			out.write(indent + "\tList<Element> gclist = XMLReader.getChildElementsWithTag(child, \"" + javaTypeName + "\");\n");
			out.write(indent + "\tfor(Element gc : gclist){\n");
			out.write(indent + "\t\t" + name + ".add(" + javaTypeName + ".readXMLNode(gc));\n");
			out.write(indent + "\t}\n");
		}

		out.write(indent + "}\n");
	}
	
	public void writeWriteXmlCode(Writer out, String indent, String outputName) throws IOException{
		String useIndent = indent;
		if(optional) {
			useIndent += "\t";
			out.write(indent + "if ((" + name + " != null) && !" + name + ".isEmpty()){\n");
		}
		
		out.write(useIndent + outputName + ".write(indent + \"\\t<List \");\n");
		out.write(useIndent + outputName + ".write(String.format(\" VarName=\\\"%s\\\">\\n\", " + xmlKeyName);
		out.write("));\n");
		out.write(useIndent + "for(" + javaTypeName + " " + elementType.name + " : " + name + "){\n");
		if(elementType.fieldType == TS3XJ.FIELD_TYPE_PRIM) {
			String fmt = DataField.getPrimWriteXmlStringFmt(elementType.typeName);
			out.write(useIndent + "\t" + outputName + ".write(String.format(");
			out.write("indent + \"\\t\\t<ListMember Value=\\\"" + fmt + "\\\"/>\\n\"");
			out.write(", " + elementType.name);
			out.write("));\n");
		}
		else if(elementType.fieldType == TS3XJ.FIELD_TYPE_ENUM) {
			out.write(useIndent + "\t" + outputName + ".write(String.format(");
			out.write("indent + \"\\t\\t<ListMember Value=\\\"%s\\\"/>\\n\"");
			out.write(", " + javaTypeName + ".valueToString(" + elementType.name + ")");
			out.write("));\n");
		}
		else {
			out.write(useIndent + "\t" + elementType.name + ".writeXMLNode(" + outputName + ", indent + \"\\t\\t\"" + ", null);\n");	
		}
		out.write(useIndent + "}\n");
		out.write(useIndent + outputName + ".write(indent + \"\\t</List>\\n\");\n");
		
		if(optional) {
			out.write(indent + "}\n");
		}
	}
	
	public void writeReadPSCode(Writer out, String indent, String inputName) throws IOException{
		//TODO
		String psidStr = elementType.listPSID;
		if(psidStr != null) {
			psidStr = psidStr.replace("PSIDBase", psidPseudoEnumName);
		}
		else {
			psidStr = "LIST_PSID";
		}
		
		out.write(indent + name + ".ensureCapacity(" + lenString + ");\n");
		out.write(indent + "for(int i = 0; i < " + lenString + "; i++){\n");
		out.write(indent + "\t" + javaTypeName + " " + elementType.name +  " = ");
		elementType.writeReadPSListCode(out, inputName, psidStr);
		out.write(";\n");
		out.write(indent + "\tif(" + elementType.name + " != null) ");
		out.write(name + ".add(" + elementType.name + ");\n");
		out.write(indent + "}\n");
	}
	
	public void writeWritePSCode(Writer out, String indent, String outputName) throws IOException{
		//TODO Add i counter for PSID increments
		String psidStr = elementType.listPSID;
		if(psidStr != null) {
			psidStr = psidStr.replace("PSIDBase", psidPseudoEnumName);
		}
		else {
			psidStr = "LIST_PSID";
		}
		
		String useIndent = indent;
		if(optional) {
			useIndent += "\t";
			out.write(indent + "if ((" + name + " != null) && !" + name + ".isEmpty()){\n");
		}
		
		out.write(useIndent + "i = 0;\n");
		out.write(useIndent + "for(" + javaTypeName + " " + elementType.name + " : " + name + "){\n");
		out.write(useIndent + "\t");
		elementType.writeWritePSListCode(out, outputName, psidStr);
		out.write(";\n");
		out.write(useIndent + "\ti++;\n");
		out.write(useIndent + "}\n");
		
		if(optional) {
			out.write(indent + "}\n");
		}
	}
	
	public void getAnonStructNodes(List<DataField> list) {
		if(list == null) return;
		if(elementType != null) {
			elementType.getAnonStructNodes(list);
		}
	}
	
}
