package waffleoRai_Files.maxis.xml2java;

import java.io.IOException;
import java.io.Writer;

import org.w3c.dom.Element;

public class EnumField extends DataField{
	
	public String enumSubtype;
	public String enumSubtypeJava;

	public EnumField() {
		super.fieldType = TS3XJ.FIELD_TYPE_ENUM;
	}
	
	public void initFromXmlElement(Element xmlElement) {
		super.initFromXmlElement(xmlElement);
		boolean isArray = super.isArrayDef();
		
		if(typeName.startsWith("Enum:")) {
			String[] spl = typeName.split(":");
			if(spl.length > 1) {
				String ename = spl[1].trim();
				if(spl.length > 2) {
					String subtype = spl[2].trim();
					super.checkPrimType(subtype);
					enumSubtype = typeName;
					enumSubtypeJava = javaTypeName;
				}
				else {
					enumSubtype = null;
					enumSubtypeJava = null;
				}
				typeName = ename;
				javaTypeName = ename;
			}
		}
		fieldType = TS3XJ.FIELD_TYPE_ENUM;
		if(isArray) fieldType |= TS3XJ.FIELD_TYPE_ISARRAY;
	}
	
	public boolean hasFixedSize() {return true;}
	
	public int getBaseSize() {
		if(enumSubtype != null) {
			return DataField.getPrimSizeBytes(enumSubtype);
		}
		return 0;
	}
	
	public String getVarSizeString() {
		if(enumSubtype != null) {
			Integer.toString(DataField.getPrimSizeBytes(enumSubtype));
		}
		return "SIZEOF(" + typeName + ")";
	}
	
	public void writeVarSizeAdditionSingle(Writer out, String indent, String sizeVarName) throws IOException {
		if(enumSubtype != null) return;
		else out.write(indent + sizeVarName + " += " + "SIZEOF(" + typeName + ")" + ";\n");
	}
	
	public void writeVarSizeAddition(Writer out, String indent, String sizeVarName) throws IOException {
		if(isArrayDef()) {
			String useIndent = indent;
			if(optional) {
				useIndent += "\t";
				out.write(indent + "if(" + name + " != null){\n");
			}
			
			if(enumSubtype != null) {
				int primSize = getPrimSizeBytes(enumSubtype);
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
		out.write(indent + "public ");
		if(enumSubtype != null) {
			out.write(enumSubtypeJava);
			if(isArrayDef()) out.write("[]");
			out.write(" " + name + ";\n");
		}
		else {
			out.write(javaTypeName);
			if(isArrayDef()) out.write("[]");
			out.write(" " + name + ";\n");
		}
	}
	
	public void writeReadBinCode(Writer out, String indent, String inputName) throws IOException{
		if(isArrayDef()) {
			String tname = javaTypeName;
			if(enumSubtype != null) tname = this.enumSubtypeJava;
			String lenStr = getArrLenString();
			out.write(indent + name + " = new " + tname + "[" + lenStr + "];\n");
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
	
	public void writeReadBinCodeList(Writer out, String inputName) throws IOException{
		if(enumSubtype != null) {
			out.write(DataField.getPrimReadBinFuncCall(enumSubtype, inputName));
		}
		else {
			out.write(javaTypeName + ".readBinary(" + inputName + ")");
		}
	}
	
	public void writeWriteBinCodeList(Writer out, String bufferName) throws IOException{
		if(enumSubtype != null) {
			String useName = name;
			if(isArrayDef()) useName += "[i]";
			out.write(DataField.getPrimWriteBinFuncCall(enumSubtype, bufferName, useName));
		}
		else {
			out.write(javaTypeName + ".writeBinary(" + bufferName + ")");
		}
	}
	
	public void writeReadXmlListCode(Writer out, String indent, String inputName, String trgName) throws IOException{
		out.write(indent + "aval = " + inputName + ".getAttribute(" + xmlKeyName + ");\n");
		out.write(indent + "if(aval != null) ");
		out.write(trgName + " = " + javaTypeName + ".valueFromString(aval);\n");
	}
	
	public void writeReadXmlCode(Writer out, String indent, String inputName) throws IOException{
		if(isArrayDef()) {
			String tname = javaTypeName;
			if(enumSubtype != null) tname = this.enumSubtypeJava;
			String lenStr = getArrLenString();
			out.write(indent + "child = XMLReader.getFirstChildElementWithTagAndAttribute(");
			out.write(inputName + ", \"Array\", \"VarName\", " + xmlKeyName + "");
			out.write(");\n");
			
			out.write(indent + "if(child != null){\n");
			out.write(indent + "\t" + name + " = new " + tname + "[" + lenStr + "];\n");
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
			out.write("\"\\t\\t<ArrayMember Value=\\\"%s\\\"");
			out.write("/>\\n\", " + javaTypeName + ".stringFromValue(" + name + "[i])));\n");
			out.write(useIndent + "}\n");
			writeMultiXmlOutBottomLine(out, useIndent, outputName, "Array");
			if(optional) {
				out.write(indent + "}\n");
			}
		}
		else {
			out.write(indent + outputName + ".write(String.format(\" %s=\\\"%s\\\"");
			out.write("\", " + xmlKeyName + ", ");
			writeWriteXmlListCode(out);	
			out.write("));\n");
		}
	}
	
	public void writeWriteXmlListCode(Writer out) throws IOException {
		out.write(javaTypeName + ".stringFromValue(" + name + ")");
	}
	
	public void writeReadPSCode(Writer out, String indent, String inputName) throws IOException{
		if(isArrayDef()) {
			if(arrayAsOne && (enumSubtype != null)) {
				out.write(indent + name + " = " + getPrimArrayReadPSFuncCall(enumSubtype, inputName, psidPseudoEnumName) + ";\n");
			}
			else {
				String lenStr = getArrLenString();
				out.write(indent + name + " = new " + javaTypeName + "[" + lenStr + "];\n");
				out.write(indent + "for(int i = 0; i < " + lenStr + "; i++){");
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
		if(enumSubtype != null) {
			out.write(DataField.getPrimReadPSFuncCall(enumSubtype, inputName, psidString));
		}
		else {
			out.write(javaTypeName + ".readBinary(" + inputName + ".getFieldAsBlob(" + psidString + "))");
		}
	}
	
	public void writeWritePSCode(Writer out, String indent, String outputName) throws IOException{
		if(isArrayDef()) {
			if(arrayAsOne && (enumSubtype != null)) {
				String fcall = DataField.getPrimArrayWritePSFuncCall(enumSubtype, outputName, name, psidPseudoEnumName);
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
		if(enumSubtype != null) {
			out.write(DataField.getPrimWritePSFuncCall(enumSubtype, outputName, name, psidString));
		}
		else {
			out.write(outputName + ".addBlob(" +  javaTypeName + ".writeBinary(byteOrder), " + psidString + ")");
		}
	}
	
}
