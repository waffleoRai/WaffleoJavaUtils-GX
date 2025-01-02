package waffleoRai_Containers.maxis.ts3.savefmts.xml2java;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

import org.w3c.dom.Element;

import waffleoRai_Utils.StringUtils;

public class DataField {
	
	//TODO I think primitive arrays for PS fields are wrong? Make sure those are calling correct funcs.
	
	public Element xmlNode;
	public String xmlNodeName;
	
	public String name;
	public String description;
	public String psidPseudoEnumName;
	public String xmlKeyName;
	public int basePSID;
	public String listPSID;
	
	public int fieldType = TS3XJ.FIELD_TYPE_UNK;
	public String typeName;
	public String javaTypeName;
	
	public boolean optional = false;
	public boolean arrayAsOne;
	
	public String len;
	
	public boolean isArrayDef() {
		return (fieldType & TS3XJ.FIELD_TYPE_ISARRAY) != 0;
	}
	
	public int getArrayType() {
		return (fieldType & ~TS3XJ.FIELD_TYPE_ISARRAY);
	}
	
	public String getArrLenString() {
		if(len == null) return "NULL_LEN";
		if(len.startsWith("Var:")) {
			String[] spl = len.split(":");
			if(spl.length < 2) return len;
			else return StringUtils.uncapitalize(spl[1].trim());
		}
		else return len;
	}
	
	public int getArrLenAsInt() {
		if(len == null) return -1;
		if(len.startsWith("Var:")) return -1;
		try {
			if(len.startsWith("0x")) {
				return Integer.parseUnsignedInt(len.substring(2), 16);
			}
			else return Integer.parseInt(len);
		}
		catch(NumberFormatException ex) {
			ex.printStackTrace();
			return -1;
		}
	}
	
	public void checkPrimType(String rawTypeString) {
		typeName = rawTypeString;
		if(rawTypeString.equals("bool") || rawTypeString.equals("boolean")) {
			fieldType = TS3XJ.FIELD_TYPE_PRIM;
			javaTypeName = "boolean";
		}
		else if(rawTypeString.equals("u8") || rawTypeString.equals("s8")) {
			fieldType = TS3XJ.FIELD_TYPE_PRIM;
			javaTypeName = "byte";
		}
		else if(rawTypeString.equals("u16") || rawTypeString.equals("s16")) {
			fieldType = TS3XJ.FIELD_TYPE_PRIM;
			javaTypeName = "short";
		}
		else if(rawTypeString.equals("u32") || rawTypeString.equals("s32")) {
			fieldType = TS3XJ.FIELD_TYPE_PRIM;
			javaTypeName = "int";
		}
		else if(rawTypeString.equals("u64") || rawTypeString.equals("s64")) {
			fieldType = TS3XJ.FIELD_TYPE_PRIM;
			javaTypeName = "long";
		}
		else if(rawTypeString.equals("f32")) {
			fieldType = TS3XJ.FIELD_TYPE_PRIM;
			javaTypeName = "float";
		}
		else if(rawTypeString.equals("f64")) {
			fieldType = TS3XJ.FIELD_TYPE_PRIM;
			javaTypeName = "double";
		}
		else if(rawTypeString.equals("TS3String") || rawTypeString.equals("String")) {
			fieldType = TS3XJ.FIELD_TYPE_STRING;
			javaTypeName = "String";
		}
	}
	
	public static int getPrimSizeBytes(String primType) {
		if(primType == null) return 0;
		if(primType.equals("bool")) return 1;
		if(primType.equals("s8") || primType.equals("u8")) return 1;
		if(primType.equals("s16") || primType.equals("u16")) return 2;
		if(primType.equals("s32") || primType.equals("u32")) return 4;
		if(primType.equals("s64") || primType.equals("u64")) return 8;
		if(primType.equals("f32")) return 4;
		if(primType.equals("f64")) return 8;
		return 0;
	}
	
	public static String getPrimReadBinFuncCall(String primType, String argName) {
		if(primType == null) return null;
		if(primType.equals("bool")) return "MaxisTypes.readBinaryBool(" + argName + ")";
		if(primType.equals("s8") || primType.equals("u8")) return argName + ".nextByte()";
		if(primType.equals("s16") || primType.equals("u16")) return argName + ".nextShort()";
		if(primType.equals("s32") || primType.equals("u32")) return argName + ".nextInt()";
		if(primType.equals("s64") || primType.equals("u64")) return argName + ".nextLong()";
		if(primType.equals("f32")) return "Float.intBitsToFloat(" + argName + ".nextInt())";
		if(primType.equals("f64")) return "Double.longBitsToDouble(" + argName + ".nextLong())";
		if(primType.equals("TS3String")) return "MaxisTypes.readMaxisString(" + argName + ")";
		return null;
	}
	
	public static String getPrimReadXmlFuncCall(String primType, String argName) {
		if(primType == null) return null;
		if(primType.equals("bool")) return "Boolean.parseBoolean(" + argName + ")";
		if(primType.equals("s8") || primType.equals("u8")) return "(byte)StringUtils.parseUnsignedInt(" + argName + ")";
		if(primType.equals("s16") || primType.equals("u16")) return "(short)StringUtils.parseUnsignedInt(" + argName + ")";
		if(primType.equals("s32")) return "StringUtils.parseSignedInt(" + argName + ")";
		if(primType.equals("u32")) return "StringUtils.parseUnsignedInt(" + argName + ")";
		if(primType.equals("s64")) return "StringUtils.parseSignedLong(" + argName + ")";
		if(primType.equals("u64")) return "StringUtils.parseUnsignedLong(" + argName + ")";
		if(primType.equals("f32")) return "(float)Double.parseDouble(" + argName + ")";
		if(primType.equals("f64")) return "Double.parseDouble(" + argName + ")";
		if(primType.equals("TS3String")) return argName;
		return null;
	}
	
	public static String getPrimReadPSFuncCall(String primType, String argName, String psidString) {
		if(primType == null) return null;
		if(primType.equals("bool")) return argName + ".getFieldAsBool(" + psidString + ")";
		if(primType.equals("s8") || primType.equals("u8")) return argName + ".getFieldAsByte(" + psidString + ")";
		if(primType.equals("s16") || primType.equals("u16")) return argName + ".getFieldAsShort(" + psidString + ")";
		if(primType.equals("s32") || primType.equals("u32")) return argName + ".getFieldAsInt(" + psidString + ")";
		if(primType.equals("s64") || primType.equals("u64")) return argName + ".getFieldAsLong(" + psidString + ")";
		if(primType.equals("f32")) return argName + ".getFieldAsFloat(" + psidString + ")";
		if(primType.equals("f64")) return argName + ".getFieldAsDouble(" + psidString + ")";
		if(primType.equals("TS3String")) return argName + ".getFieldAsString(" + psidString + ")";
		return null;
	}
	
	public static String getPrimArrayReadPSFuncCall(String primType, String argName, String psidString) {
		if(primType == null) return null;
		if(primType.equals("bool")) return argName + ".getFieldAsBoolArray(" + psidString + ")";
		if(primType.equals("s8") || primType.equals("u8")) return argName + ".getFieldAsByteArray(" + psidString + ")";
		if(primType.equals("s16") || primType.equals("u16")) return argName + ".getFieldAsShortArray(" + psidString + ")";
		if(primType.equals("s32") || primType.equals("u32")) return argName + ".getFieldAsIntArray(" + psidString + ")";
		if(primType.equals("s64") || primType.equals("u64")) return argName + ".getFieldAsLongArray(" + psidString + ")";
		if(primType.equals("f32")) return argName + ".getFieldAsFloatArray(" + psidString + ")";
		if(primType.equals("f64")) return argName + ".getFieldAsDoubleArray(" + psidString + ")";
		if(primType.equals("TS3String")) return argName + ".getFieldAsStringArray(" + psidString + ")";
		return null;
	}
	
	public static String getPrimWriteBinFuncCall(String primType, String trgName, String fieldName) {
		if(primType == null) return null;
		if(primType.equals("bool")) return "MaxisTypes.writeBinaryBool(" + trgName + ", " + fieldName + ")";
		if(primType.equals("s8") || primType.equals("u8")) return trgName + ".addToFile(" + fieldName + ")";
		if(primType.equals("s16") || primType.equals("u16")) return trgName + ".addToFile(" + fieldName + ")";
		if(primType.equals("s32") || primType.equals("u32")) return trgName + ".addToFile(" + fieldName + ")";
		if(primType.equals("s64") || primType.equals("u64")) return trgName + ".addToFile(" + fieldName + ")";
		if(primType.equals("f32")) return trgName + ".addToFile(Float.floatToRawIntBits(" + fieldName + "))";
		if(primType.equals("f64")) return trgName + ".addToFile(Double.doubleToRawLongBits(" + fieldName + "))";
		//if(primType.equals("TS3String")) return trgName + ".addToFile(MaxisTypes.serializeMaxisString(" + fieldName + ", byteOrder))";
		if(primType.equals("TS3String")) return "MaxisTypes.serializeMaxisStringTo( " + fieldName + ", " + trgName + ")";
		return null;
	}
	
	public static String getPrimWriteXmlStringFmt(String primType) {
		if(primType == null) return null;
		if(primType.equals("bool")) return "%b";
		if(primType.equals("s8")) return "%d";
		if(primType.equals("u8")) return "0x%02x";
		if(primType.equals("s16")) return "%d";
		if(primType.equals("u16")) return "0x%04x";
		if(primType.equals("s32")) return "%d";
		if(primType.equals("u32")) return "0x%08x";
		if(primType.equals("s64")) return "%d";
		if(primType.equals("u64")) return "0x%016x";
		if(primType.equals("f32")) return "%.3f";
		if(primType.equals("f64")) return "%.5f";
		if(primType.equals("TS3String")) return "%s";
		return null;
	}
	
	public static String getPrimWritePSFuncCall(String primType, String psName, String fieldName, String psidString) {
		if(primType == null) return null;
		if(primType.equals("bool")) return psName + ".addBool(" + fieldName + ", " + psidString + ")";
		if(primType.equals("s8") || primType.equals("u8")) return psName + ".addByte(" + fieldName + ", " + psidString + ")";
		if(primType.equals("s16") || primType.equals("u16")) return psName + ".addShort(" + fieldName + ", " + psidString + ")";
		if(primType.equals("s32") || primType.equals("u32")) return psName + ".addInt(" + fieldName + ", " + psidString + ")";
		if(primType.equals("s64") || primType.equals("u64")) return psName + ".addLong(" + fieldName + ", " + psidString + ")";
		if(primType.equals("f32")) return psName + ".addFloat(" + fieldName + ", " + psidString + ")";
		if(primType.equals("f64")) return psName + ".addDouble(" + fieldName + ", " + psidString + ")";
		if(primType.equals("TS3String")) return psName + ".addString(" + fieldName + ", " + psidString + ")";
		return null;
	}
	
	public static String getPrimArrayWritePSFuncCall(String primType, String psName, String fieldName, String psidString) {
		if(primType == null) return null;
		if(primType.equals("bool")) return psName + ".addBoolArray(" + fieldName + ", " + psidString + ")";
		if(primType.equals("s8") || primType.equals("u8")) return psName + ".addByteArray(" + fieldName + ", " + psidString + ")";
		if(primType.equals("s16") || primType.equals("u16")) return psName + ".addShortArray(" + fieldName + ", " + psidString + ")";
		if(primType.equals("s32") || primType.equals("u32")) return psName + ".addIntArray(" + fieldName + ", " + psidString + ")";
		if(primType.equals("s64") || primType.equals("u64")) return psName + ".addLongArray(" + fieldName + ", " + psidString + ")";
		if(primType.equals("f32")) return psName + ".addFloatArray(" + fieldName + ", " + psidString + ")";
		if(primType.equals("f64")) return psName + ".addDoubleArray(" + fieldName + ", " + psidString + ")";
		if(primType.equals("TS3String")) return psName + ".addStringArray(" + fieldName + ", " + psidString + ")";
		return null;
	}

	public void initFromXmlElement(Element xmlElement) {
		xmlNode = xmlElement;
		xmlNodeName = xmlElement.getNodeName();
		typeName = xmlNode.getAttribute("Type");
		boolean array = typeName.endsWith("[]");
		typeName = typeName.replace("[]", "");
		
		name = xmlNode.getAttribute("Name");
		name = StringUtils.uncapitalize(name);
		description = xmlNode.getAttribute("Description");
		psidPseudoEnumName = "PSID_" + name.toUpperCase();
		xmlKeyName = "XMLKEY_" + name.toUpperCase();
		listPSID = xmlNode.getAttribute("PSID");
		try {
			basePSID = StringUtils.parseUnsignedInt(xmlNode.getAttribute("PSID"));
		}
		catch(Exception ex) {
			listPSID = xmlNode.getAttribute("PSID");
		}
		
		String aval = xmlNode.getAttribute("Optional");
		if(aval != null) optional = Boolean.parseBoolean(aval);
		aval = xmlNode.getAttribute("ArrayAsOne");
		if(aval != null) arrayAsOne = Boolean.parseBoolean(aval);
		javaTypeName = typeName;
		checkPrimType(typeName);
		if(array) fieldType |= TS3XJ.FIELD_TYPE_ISARRAY;
		
		aval = xmlNode.getAttribute("Length");
		if(aval != null && !aval.isEmpty()) len = aval;
	}
	
	public boolean hasFixedSize() {return fieldType == TS3XJ.FIELD_TYPE_PRIM;}
	public boolean canBeNull() {return fieldType == TS3XJ.FIELD_TYPE_STRING;}
	
	public boolean isXmlMultiLine() {return isArrayDef();}
	
	public int getBaseSize() {
		if(isArrayDef()) {
			int alen = getArrLenAsInt();
			if(alen > 0) {
				if(fieldType == TS3XJ.FIELD_TYPE_PRIM) return (alen * DataField.getPrimSizeBytes(typeName));
				if(fieldType == TS3XJ.FIELD_TYPE_STRING) return (alen << 2);
			}
		}
		else {
			if(fieldType == TS3XJ.FIELD_TYPE_PRIM) return DataField.getPrimSizeBytes(typeName);
			if(fieldType == TS3XJ.FIELD_TYPE_STRING) return 4;
		}
		return DataField.getPrimSizeBytes(typeName);
	}
	
	public void writeSizeUpdate(Writer out, String indent) throws IOException {
		if(isArrayDef()) {
			if(len != null && len.startsWith("Var:")) {
				out.write(indent + getArrLenString() + " = " + name + ".length;\n");
			}
		}
	}
	
	public void writeVarSizeAdditionSingle(Writer out, String indent, String sizeVarName) throws IOException {
		int aval = getArrayType();
		if(aval == TS3XJ.FIELD_TYPE_STRING) {
			out.write(indent + sizeVarName + " += (" + name + ".length() << 1);\n");
		}
		else if(aval == TS3XJ.FIELD_TYPE_PRIM) return;
		else out.write(indent + sizeVarName + " += " + "SIZEOF(" + typeName + ")" + ";\n");
	}
	
	public void writeVarSizeAddition(Writer out, String indent, String sizeVarName) throws IOException {
		if(isArrayDef()) {
			String useIndent = indent;
			if(optional) {
				useIndent += "\t";
				out.write(indent + "if(" + name + " != null){\n");
			}
			
			int aval = getArrayType();
			if(aval == TS3XJ.FIELD_TYPE_PRIM) {
				int primSize = getPrimSizeBytes(typeName);
				if(primSize == 1) {
					out.write(useIndent + sizeVarName + " += " + name + ".length;\n");	
				}
				else {
					out.write(useIndent + sizeVarName + " += (" + name + ".length * " + primSize + ");\n");	
				}
			}
			else {
				out.write(useIndent + "for(int i = 0; i < " + name + ".length; i++){\n");
				writeVarSizeAdditionSingle(out, useIndent + "\t", sizeVarName);
				out.write(useIndent + "}\n");	
			}
					
			if(optional) {
				out.write(indent + "}\n");
			}
		}
		else {
			writeVarSizeAdditionSingle(out, indent, sizeVarName);
		}
	}
	
	public void writeVarDeclaration(Writer out, String indent) throws IOException {
		out.write(indent + "public " + javaTypeName);
		if(isArrayDef()) out.write("[]");
		out.write(" " + name + ";\n");
	}
	
	public void writeReadBinCodeList(Writer out, String inputName) throws IOException{
		String call = DataField.getPrimReadBinFuncCall(typeName, inputName);
		if(call != null) out.write(call);
	}
	
	public void writeReadBinCode(Writer out, String indent, String inputName) throws IOException{
		if(isArrayDef()) {
			String lenStr = getArrLenString();
			out.write(indent + name + " = new " + javaTypeName + "[" + lenStr + "];\n");
			out.write(indent + "for(int i = 0; i < " + lenStr + "; i++){\n");
			out.write(indent + "\t" + name + "[i] = ");
			writeReadBinCodeList(out, inputName);
			out.write(";\n");
			out.write(indent + "}\n");
		}
		else {
			out.write(indent + name + " = ");
			writeReadBinCodeList(out, inputName);
			out.write(";\n");
		}
	}
	
	public void writeWriteBinCodeList(Writer out, String bufferName) throws IOException{
		String useName = name;
		if(isArrayDef()) useName += "[i]";
		String call = DataField.getPrimWriteBinFuncCall(typeName, bufferName, useName);
		if(call != null) out.write(call);
	}
	
	public void writeWriteBinCode(Writer out, String indent, String bufferName) throws IOException{
		if(isArrayDef()) {
			String lenStr = getArrLenString();
			out.write(indent + "for(int i = 0; i < " + lenStr + "; i++){\n");
			out.write(indent + "\t");
			writeWriteBinCodeList(out, bufferName);
			out.write(";\n");
			out.write(indent + "}\n");
		}
		else {
			out.write(indent);
			writeWriteBinCodeList(out, bufferName);
			out.write(";\n");	
		}
	}
	
	public void writeReadXmlListCode(Writer out, String indent, String inputName, String trgName) throws IOException{
		String key = xmlKeyName;
		if(isArrayDef()) key = "\"Value\"";
		out.write(indent + "aval = " + inputName + ".getAttribute(" + key + ");\n");
		out.write(indent + "if(aval != null) ");
		out.write(trgName + " = " + DataField.getPrimReadXmlFuncCall(typeName, "aval") + ";\n");
	}
	
	public void writeReadXmlCode(Writer out, String indent, String inputName) throws IOException{
		if(isArrayDef()) {
			String lenStr = getArrLenString();
			out.write(indent + "child = XMLReader.getFirstChildElementWithTagAndAttribute(");
			out.write(inputName + ", \"Array\", \"VarName\", " + xmlKeyName + "");
			out.write(");\n");
			
			out.write(indent + "if(child != null){\n");
			out.write(indent + "\t" + name + " = new " + javaTypeName + "[" + lenStr + "];\n");
			out.write(indent + "\tList<Element> gclist = XMLReader.getChildElementsWithTag(child, \"ArrayMember\");\n");
			out.write(indent + "\tint i = 0;\n");
			out.write(indent + "\tfor(Element gc : gclist){\n");
			writeReadXmlListCode(out, indent + "\t\t", "gc", name + "[i]");
			out.write(indent + "\t\ti++;\n");
			out.write(indent + "\t}\n");
			out.write(indent + "}\n");
		}
		else {
			writeReadXmlListCode(out, indent, inputName, name);
		}
	}
	
	public void writeWriteXmlListCode(Writer out) throws IOException {
		String fmt = DataField.getPrimWriteXmlStringFmt(typeName);
		out.write("\\\"" + fmt + "\\\"");
	}
	
	public void writeWriteXmlCode(Writer out, String indent, String outputName) throws IOException{
		if(isArrayDef()) {
			String useIndent = indent;
			String lenStr = getArrLenString();
			if(optional) {
				useIndent += "\t";
				out.write(indent + "if(" + name + " != null){\n");
			}
			writeMultiXmlOutTopLine(out, useIndent, outputName, "Array");
			out.write(useIndent + "for(int i = 0; i < " + lenStr + "; i++){\n");
			out.write(useIndent + "\t" + outputName + ".write(indent + ");
			out.write("String.format(");
			out.write("\"\\t\\t<ArrayMember Value=");
			writeWriteXmlListCode(out);
			out.write("/>\\n\", " + name + "[i]));\n");
			out.write(useIndent + "}\n");
			writeMultiXmlOutBottomLine(out, useIndent, outputName, "Array");
			if(optional) {
				out.write(indent + "}\n");
			}
		}
		else {
			out.write(indent + outputName + ".write(String.format(\" %s=");
			writeWriteXmlListCode(out);	
			out.write("\", " + xmlKeyName + ", " + name + ")");
			out.write(");\n");
		}
	}
	
	public void writeReadPSCode(Writer out, String indent, String inputName) throws IOException{
		if(isArrayDef()) {
			if(arrayAsOne) {
				out.write(indent + name + " = " + getPrimArrayReadPSFuncCall(typeName, inputName, psidPseudoEnumName) + ";\n");
			}
			else {
				String lenStr = getArrLenString();
				out.write(indent + name + " = new " + javaTypeName + "[" + lenStr + "];\n");
				out.write(indent + "for(int i = 0; i < " + lenStr + "; i++){\n");
				out.write(indent + "\t" + name + "[i] = ");
				writeReadPSListCode(out, inputName, psidPseudoEnumName);
				out.write(";\n");
				out.write(indent + "}\n");	
			}
		}
		else {
			out.write(indent + name + " = ");
			writeReadPSListCode(out, inputName, psidPseudoEnumName);
			out.write(";\n");	
		}
	}
	
	public void writeReadPSListCode(Writer out, String inputName, String psidString) throws IOException{
		String fcall = DataField.getPrimReadPSFuncCall(typeName, inputName, psidString);
		if(fcall != null) out.write(fcall);
	}
	
	public void writeWritePSCode(Writer out, String indent, String outputName) throws IOException{
		if(isArrayDef()) {
			if(arrayAsOne) {
				String fcall = DataField.getPrimArrayWritePSFuncCall(typeName, outputName, name, psidPseudoEnumName);
				out.write(indent + fcall + ";\n");
			}
			else {
				String lenStr = getArrLenString();
				out.write(indent + "for(int i = 0; i < " + lenStr + "; i++{\n");
				out.write(indent + "\t");
				writeWritePSListCode(out, outputName, psidPseudoEnumName);
				out.write(indent + "}\n");	
			}
		}
		else {
			out.write(indent);
			writeWritePSListCode(out, outputName, psidPseudoEnumName);
			out.write(";\n");	
		}
	}
	
	public void writeWritePSListCode(Writer out, String outputName, String psidString) throws IOException{
		String fcall = DataField.getPrimWritePSFuncCall(typeName, outputName, name, psidPseudoEnumName);
		if(fcall != null) out.write(fcall);
	}
	
	protected void writeMultiXmlOutTopLine(Writer out, String indent, String outputName, String tag) throws IOException {
		out.write(indent + outputName + ".write(indent + ");
		out.write("String.format(\"\\t<" + tag + " VarName=\\\"%s\\\">\\n\", ");
		out.write(xmlKeyName + "));\n");
		
		//out.write(indent + outputName + ".write(indent + \"\\t<" + tag + " VarName=" + xmlKeyName + ">\\n\"\n");
	}
	
	protected void writeMultiXmlOutBottomLine(Writer out, String indent, String outputName, String tag) throws IOException {
		out.write(indent + outputName + ".write(indent + \"\\t</" + tag + ">\\n\");\n");
	}
	
	public void getAnonStructNodes(List<DataField> list) {}

}
