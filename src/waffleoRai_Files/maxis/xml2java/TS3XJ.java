package waffleoRai_Files.maxis.xml2java;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.IOException;
import java.io.Writer;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.List;

import org.w3c.dom.Element;

import waffleoRai_Files.XMLReader;
import waffleoRai_Utils.StringUtils;

public class TS3XJ {
	
	/*
	 * Pending issues:
	 * 	- Still has problems correctly generating read/write for inner PS's (particularly when anon structs are involved)
	 * 
	 */
	
	public static final String[] DEF_TYPES = {"Struct", "PropertyStreamStruct", "TS3ResourceFileStruct"};
	
	public static final int FIELD_TYPE_UNK = -1;
	public static final int FIELD_TYPE_PRIM = 0;
	public static final int FIELD_TYPE_STRING = 1;
	public static final int FIELD_TYPE_ENUM = 2;
	public static final int FIELD_TYPE_STRUCT = 3;
	public static final int FIELD_TYPE_LIST = 4;
	public static final int FIELD_TYPE_STRUCT_ANON = 5;
	public static final int FIELD_TYPE_RESKEY = 6;
	public static final int FIELD_TYPE_PSEUDO_UNION = 7;
	public static final int FIELD_TYPE_BITFIELD = 8;
	public static final int FIELD_TYPE_FILE = 9;
	
	public static final int FIELD_TYPE_ISARRAY = 0x80;
	
	public static DataField parseXmlNode(Element element) {
		String nodeName = element.getNodeName();
		if(nodeName == null) return null;
		if(nodeName.equals("List")) {
			ListField lf = new ListField();
			lf.initFromXmlElement(element);
			return lf;
		}
		else if(nodeName.equals("PSChild")) {
			String rawType = element.getAttribute("Type");
			if((rawType != null) && !rawType.isEmpty()) {
				if(rawType.startsWith("Struct:")) {
					StructField fdef = new StructField();
					fdef.initFromXmlElement(element);
					return fdef;
				}
				else if(rawType.startsWith("Enum:")) {
					EnumField fdef = new EnumField();
					fdef.initFromXmlElement(element);
					return fdef;
				}
				else {
					DataField fdef = new DataField();
					fdef.initFromXmlElement(element);
					return fdef;
				}
			}
			else {
				return AnonStructField.readAnonStructNode(element);
			}
		}
		else if(nodeName.equals("PSField") || nodeName.equals("StructField") || nodeName.equals("DataField") || nodeName.equals("UnionOption")) {
			String rawType = element.getAttribute("Type");
			if((rawType != null) && !rawType.isEmpty()) {
				if(rawType.startsWith("Struct:")) {
					//Check if reskey
					if(rawType.endsWith("ResKey")) {
						ResKeyField rkdef = new ResKeyField();
						rkdef.initFromXmlElement(element);
						return rkdef;
					}
					
					StructField fdef = new StructField();
					fdef.initFromXmlElement(element);
					return fdef;
				}
				else if(rawType.startsWith("Enum:")) {
					EnumField fdef = new EnumField();
					fdef.initFromXmlElement(element);
					return fdef;
				}
				else if(rawType.startsWith("File:")) {
					FileField fdef = new FileField();
					fdef.initFromXmlElement(element);
					return fdef;
				}
				else {
					DataField fdef = new DataField();
					fdef.initFromXmlElement(element);
					return fdef;
				}
			}
			else {
				//Assume anon struct
				return AnonStructField.readAnonStructNode(element);
			}
		}
		else if(nodeName.equals("PseudoUnion")) {
			PseudoUnionField fdef = new PseudoUnionField();
			fdef.initFromXmlElement(element);
			return fdef;
		}
		else if(nodeName.equals("StructBitfield")) {
			BitfieldField fdef = new BitfieldField();
			fdef.initFromXmlElement(element);
			return fdef;
		}
		
		return null;
	}

	private static List<DataField> processChildren(Element defNode){
		List<DataField> childFields = new LinkedList<DataField>();
		List<Element> childList = XMLReader.getChildElements(defNode);
		for(Element child : childList) {
			DataField fdef = parseXmlNode(child);
			if(fdef != null) childFields.add(fdef);
		}
		return childFields;
	}
	
	private static String getBaseSizeString(List<DataField> children) {
		int iVal = 0;
		List<String> addStrings = new LinkedList<String>();
		
		for(DataField child : children) {
			iVal += child.getBaseSize();
		}
		
		String ret = Integer.toString(iVal);
		for(String addOn : addStrings) {
			ret += " + " + addOn;
		}
		
		return ret;
	}
	
	public static String parseCondition(Element xmlNode) {
		String aval = xmlNode.getAttribute("Condition");
		if(aval != null && !aval.isEmpty()) return aval;
		
		//Check for version condition
		aval = xmlNode.getAttribute("Version");
		if(aval != null && !aval.isEmpty()) {
			aval = aval.trim();
			if(aval.endsWith("-")) {
				aval = aval.substring(0, aval.length()-1).trim();
				int val = StringUtils.parseSignedInt(aval);
				return "fileVersion <= " + val;
			}
			else if(aval.endsWith("+")) {
				aval = aval.substring(0, aval.length()-1).trim();
				int val = StringUtils.parseSignedInt(aval);
				return "fileVersion >= " + val;
			}
			else {
				if(aval.contains("-")) {
					String[] spl = aval.split("-");
					int min = StringUtils.parseSignedInt(spl[0].trim());
					int max = StringUtils.parseSignedInt(spl[1].trim());
					return "(fileVersion >= " + min + ") && (fileVersion <= " + max + ")";
				}
				else {
					int val = Integer.parseInt(aval);
					return "fileVersion == " + val;
				}
			}
		}
			
		return null;
	}
	
	private static void writePSIDList(Writer out, List<DataField> children, String indent) throws IOException {
		for(DataField child : children) {
			if(child.psidPseudoEnumName == null) continue;
			out.write(indent + "public static final int " + child.psidPseudoEnumName);
			out.write(String.format(" = 0x%08X;\n", child.basePSID));
		}
	}
	
	private static void writeXMLKeyList(Writer out, List<DataField> children, String indent) throws IOException {
		for(DataField child : children) {
			if(child.xmlKeyName == null) continue;
			String nn = StringUtils.capitalize(child.name);
			out.write(indent + "private static final String " + child.xmlKeyName);
			out.write(String.format(" = \"%s\";\n", nn));
		}
	}
	
	private static void writeInstanceVariableList(Writer out, List<DataField> children, String indent) throws IOException {
		for(DataField child : children) {
			child.writeVarDeclaration(out, indent);
		}
	}
	
	private static void writeReadBinBody(Writer out, List<DataField> children, String argName, String indent) throws IOException {
		for(DataField child : children) {
			child.writeReadBinCode(out, indent + "\t", argName);
		}
	}
	
	private static void writeWriteBinBody(Writer out, List<DataField> children, String argName, String indent) throws IOException {
		for(DataField child : children) {
			child.writeSizeUpdate(out, indent + "\t");
		}
		for(DataField child : children) {
			child.writeWriteBinCode(out, indent + "\t", argName);
		}
	}
	
	private static void writeReadXmlBody(Writer out, List<DataField> children, String argName, String indent) throws IOException {
		out.write(indent + "\tString aval = null;\n");
		
		//Scan to see if need to instantiate "child" node too.
		boolean broken = false;
		for(DataField child : children) {
			if(child.isArrayDef()) {
				out.write(indent + "\tElement child = null;\n");
				break;
			}
			
			switch(child.fieldType) {
			case FIELD_TYPE_STRUCT:
			case FIELD_TYPE_LIST:	
			case FIELD_TYPE_STRUCT_ANON:
				out.write(indent + "\tElement child = null;\n");
				broken = true;
				break;
			}
			if(broken) break;
		}
		
		for(DataField child : children) {
			child.writeReadXmlCode(out, indent + "\t", argName);
		}
	}
	
	private static void writeWriteXmlBody(Writer out, List<DataField> children, String indent) throws IOException {
		boolean oneLiner = true;
		for(DataField child : children) {
			if(child.isXmlMultiLine()) {
				oneLiner = false;
				break;
			}
		}
		
		for(DataField child : children) {
			child.writeSizeUpdate(out, indent + "\t");
		}
		
		out.write(indent + "\tout.write(indent);\n");
		out.write(indent + "\tout.write(String.format(\"<%s\", xmlNodeName));\n");
		
		out.write(indent + "\tif(varName != null){\n");
		out.write(indent + "\t\tout.write(");
		out.write("String.format(\" VarName=\\\"%s\\\"\"");
		out.write(", varName));\n");
		out.write(indent + "\t}\n");
		
		//Simple fields
		for(DataField child : children) {
			if(!child.isXmlMultiLine()) {
				child.writeWriteXmlCode(out, indent + "\t", "out");
			}
		}
		
		//Complex fields
		if(!oneLiner) {
			out.write(indent + "\tout.write(\">\\n\");\n");
			for(DataField child : children) {
				if(child.isXmlMultiLine()) {
					child.writeWriteXmlCode(out, indent + "\t", "out");
				}
			}
		}
		
		//Also write closing...
		if(oneLiner) {
			out.write(indent + "\tout.write(\"/>\\n\");\n");
		}
		else {
			out.write(indent + "\tout.write(indent);\n");
			out.write(indent + "\tout.write(String.format(\"</%s>\\n\", xmlNodeName));\n");
		}
	}
	
	private static void writeReadPSBody(Writer out, List<DataField> children, String psArgName, String indent) throws IOException {
		for(DataField child : children) {
			child.writeReadPSCode(out, indent + "\t", psArgName);
		}
	}
	
	private static void writeWritePSBody(Writer out, List<DataField> children, String psArgName, String indent) throws IOException {
		boolean hasLists = false;
		for(DataField child : children) {
			child.writeSizeUpdate(out, indent + "\t");
			if(child instanceof ListField) hasLists = true;
		}
		if(hasLists) out.write(indent + "\tint i = 0;\n");
		for(DataField child : children) {
			child.writeWritePSCode(out, indent + "\t", psArgName);
		}
	}
	
	private static void writeInnerStructsBody(BufferedWriter out, List<DataField> children, String templatePath, String indent) throws IOException {
		LinkedList<DataField> anonStructs = new LinkedList<DataField>();
		for(DataField child : children) {
			child.getAnonStructNodes(anonStructs);
		}
		
		if(anonStructs.isEmpty()) return;
		
		out.write("\t/*----- Inner Classes -----*/\n\n");
		
		while(!anonStructs.isEmpty()) {
			DataField cdef = anonStructs.pop();
			if(cdef instanceof AnonStructField) {
				AnonStructField asfield = (AnonStructField)cdef;
				writeClassTo(null, asfield, out, templatePath, true, anonStructs);
				out.write("\n");
			}
		}
		
	}
	
	private static void writeCalcBinsizeBody(Writer out, List<DataField> children, String indent) throws IOException{
		out.write(indent + "\tint size = baseSize;\n");
		for(DataField child : children) {
			child.writeVarSizeAddition(out, indent + "\t", "size");
		}
		out.write(indent + "\treturn size;");
	}
	
	public static void writeClassTo(Element defNode, AnonStructField altInput, BufferedWriter bw, String templatePath, boolean inner, LinkedList<DataField> anonStructList) throws IOException {
		String defName = null;
		String defScript = null;
		List<DataField> children = null;
		
		if(defNode != null) {
			defName = defNode.getAttribute("Name");
			defName = StringUtils.capitalize(defName);
			defScript = defNode.getAttribute("GameScript");
			
			children = processChildren(defNode);
		}
		else if(altInput != null) {
			defName = altInput.javaTypeName;
			children = altInput.members;
		}
		
		if(!inner) {
			ZonedDateTime nowtime = ZonedDateTime.now();
			bw.write("/*-----------------------------------------------------\n");
			bw.write(" * Autogenerated Java class from XML definition.\n");
			bw.write(" * Created " + nowtime.format(DateTimeFormatter.RFC_1123_DATE_TIME) + "\n");
			bw.write(" *-----------------------------------------------------*/\n\n");
		}
		
		BufferedReader br = new BufferedReader(new FileReader(templatePath));
		String classContentIndent = "\t";
		if(inner) classContentIndent += "\t";
		
		String line = null;
		boolean innerStart = false;
		while((line = br.readLine()) != null) {
			if(inner && !innerStart) {
				if(line.startsWith("public class")) {
					innerStart = true;
					line = line.replace("public class", "public static class");
				}
				else continue;
			}
			
			if(line.contains("#")) {
				while(line.contains("#")) {
					//Determine what substitution is.
					int stPound = line.indexOf('#');
					int edPound = line.indexOf('#', stPound+1);
					String subKey = line.substring(stPound, edPound+1);
					if(subKey.equals("#PACKAGE_NAME#")) {
						line = line.replace(subKey, "YOUR_PKG_HERE");
					}
					else if(subKey.equals("#CLASS_NAME#")) {
						line = line.replace(subKey, defName);
					}
					else if(subKey.equals("#SCRIPT_COMMENT#")) {
						if(defScript != null) {
							line = line.replace(subKey, "//TS3 Script: " + defScript);
						}
						else line = "";
					}
					else if(subKey.equals("#BASE_SIZE#")) {
						line = line.replace(subKey, getBaseSizeString(children));
					}
					else if(subKey.equals("#PSID_PSEUDOENUMS#")) {
						line = "";
						writePSIDList(bw, children, classContentIndent);
					}
					else if(subKey.equals("#XML_KEYS#")) {
						line = "";
						writeXMLKeyList(bw, children, classContentIndent);
					}
					else if(subKey.equals("#INSTANCE_VARIABLES#")) {
						line = "";
						writeInstanceVariableList(bw, children, classContentIndent);
					}
					else if(subKey.equals("#BINREAD_BODY#")) {
						line = "";
						writeReadBinBody(bw, children, "dat", classContentIndent);
					}
					else if(subKey.equals("#XMLREAD_BODY#")) {
						line = "";
						writeReadXmlBody(bw, children, "xml_element", classContentIndent);
					}
					else if(subKey.equals("#PSREAD_BODY#")) {
						line = "";
						writeReadPSBody(bw, children, "stream", classContentIndent);
					}
					else if(subKey.equals("#BINWRITE_BODY#")) {
						line = "";
						writeWriteBinBody(bw, children, "target", classContentIndent);
					}
					else if(subKey.equals("#XMLWRITE_BODY#")) {
						line = "";
						writeWriteXmlBody(bw, children, classContentIndent);
					}
					else if(subKey.equals("#PSWRITE_BODY#")) {
						line = "";
						writeWritePSBody(bw, children, "ps", classContentIndent);
					}
					else if(subKey.equals("#INNER_STRUCTS#")) {
						line = "";
						if(!inner) {
							writeInnerStructsBody(bw, children, templatePath, classContentIndent);
						}
						/*else {
							//Recurse if there are any anon structs within this anon struct
							for(DataField child : children) {
								child.getAnonStructNodes(anonStructList);
							}
						}*/
					}
					else if(subKey.equals("#BINSIZE_BODY#")) {
						line = "";
						writeCalcBinsizeBody(bw, children, classContentIndent);
					}
				}
				if(!line.isEmpty()) {
					if(inner) bw.write("\t");
					bw.write(line);
				}
			}
			else {
				if(inner) bw.write("\t");
				bw.write(line);
			}
			bw.write("\n");
		}
		br.close();
	}
	
}
