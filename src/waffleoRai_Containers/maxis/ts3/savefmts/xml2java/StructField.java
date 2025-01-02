package waffleoRai_Containers.maxis.ts3.savefmts.xml2java;

import java.io.IOException;
import java.io.Writer;

import org.w3c.dom.Element;

public class StructField extends DataField{
	
	public StructField() {
		fieldType = TS3XJ.FIELD_TYPE_STRUCT;
	}
	
	public void initFromXmlElement(Element xmlElement) {
		super.initFromXmlElement(xmlElement);
		
		if(typeName != null && !typeName.isEmpty()) {
			if(typeName.startsWith("Struct:")) {
				String[] spl = typeName.split(":");
				if(spl.length > 1) {
					String sname = spl[1].trim();
					typeName = sname;
					javaTypeName = sname;
				}
			}	
		}
		fieldType = TS3XJ.FIELD_TYPE_STRUCT;
	}
	
	public boolean hasFixedSize() {
		//Depends. Have it call its own serial size function.
		return false;
	}
	
	public int getBaseSize() {return 0;}
	public boolean canBeNull() {return true;}
	
	public boolean isXmlMultiLine() {return true;}
	
	public void writeVarSizeAdditionSingle(Writer out, String indent, String sizeVarName) throws IOException {
		out.write(indent);
		if(optional) out.write(" if(" + name + " != null) ");
		out.write(sizeVarName + " += " + name + ".getBinarySize();\n");
	}
	
	public void writeReadBinCodeList(Writer out, String inputName) throws IOException{
		out.write(javaTypeName + ".readBinary(" + inputName + ")");
	}
	
	public void writeWriteBinCodeList(Writer out, String bufferName) throws IOException{
		String useName = name;
		if(isArrayDef()) useName += "[i]";
		//out.write(bufferName + ".addToFile(");
		//out.write(useName + ".writeBinary(byteOrder))");
		out.write(useName + ".writeBinaryTo(" + bufferName + ")");
	}
	
	public void writeReadXmlListCode(Writer out, String indent, String inputName, String trgName) throws IOException{
		out.write(indent + "child = XMLReader.getFirstChildElementWithTagAndAttribute(");
		out.write(inputName + ", \"" + javaTypeName + "\", ");
		out.write("\"VarName\", " + xmlKeyName + ");\n");
		out.write(indent + "if(child != null) " + trgName + " = " + javaTypeName + ".readXMLNode(child);\n");
	}
	
	public void writeWriteXmlCode(Writer out, String indent, String outputName) throws IOException{
		if(this.isArrayDef()) {
			//TODO
		}
		else {
			out.write(indent);
			if(optional) {
				out.write("if(" + name + " != null) ");
			}
			out.write(name + ".writeXMLNode(" + outputName + ", indent + \"\\t\", " + xmlKeyName + ");\n");	
		}
	}
	
	public void writeReadPSListCode(Writer out, String inputName, String psidString) throws IOException{
		if(xmlNodeName.equals("PSChild")) {
			out.write(typeName + ".readPropertyStream(");
			out.write(inputName + ".getChildStream(" + psidString + ")");
			out.write(")");
		}
		else {
			out.write(typeName + ".readBinary(");
			out.write(inputName + ".getFieldAsBlob(" + psidString + ")");
			out.write(")");
		}
	}
	
	public void writeWritePSListCode(Writer out, String outputName, String psidString) throws IOException{
		if(xmlNodeName.equals("PSChild")) {
			out.write(outputName + ".addChildStream(");
			out.write(name + ".toPropertyStream(byte_order, verFieldSize)");
			out.write(", " + psidString + ")");
		}
		else {
			out.write(outputName + ".addBlob(");
			out.write(name + ".writeBinary(byteOrder)");
			out.write(", " + psidString + ")");
		}
	}
	
}
