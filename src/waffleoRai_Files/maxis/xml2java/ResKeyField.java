package waffleoRai_Files.maxis.xml2java;

import java.io.IOException;
import java.io.Writer;

import org.w3c.dom.Element;

public class ResKeyField extends DataField{
	
	public ResKeyField() {
		fieldType = TS3XJ.FIELD_TYPE_RESKEY;
	}
	
	public void initFromXmlElement(Element xmlElement) {
		super.initFromXmlElement(xmlElement);
		if(typeName != null && !typeName.isEmpty()) {
			if(typeName.startsWith("Struct:")) {
				String[] spl = typeName.split(":");
				if(spl.length > 1) {
					String sname = spl[1].trim();
					typeName = sname;
					javaTypeName = "MaxisResKey";
				}
			}	
		}
		fieldType = TS3XJ.FIELD_TYPE_RESKEY;
	}
	
	public boolean hasFixedSize() {return true;}
	public int getBaseSize() {return 16;}
	public boolean canBeNull() {return true;}
	
	public boolean isXmlMultiLine() {return false;}
	
	public void writeReadBinCodeList(Writer out, String inputName) throws IOException{
		if(out == null) return;
		if(condition != null) out.write("if (" + condition + ")");
		if(typeName.equals("TGIResKey")) {
			out.write("MaxisResKey.readBinTGI(" + inputName + ")");
		}
		else if(typeName.equals("IGTResKey")) {
			out.write("MaxisResKey.readBinIGT(" + inputName + ")");
		}
		else if(typeName.equals("ITGResKey")) {
			out.write("MaxisResKey.readBinITG(" + inputName + ")");
		}
		else out.write(javaTypeName + ".readBinary(" + inputName + ")");
	}
	
	public void writeReadXmlListCode(Writer out, String indent, String inputName, String trgName) throws IOException{
		if(out == null) return;
		String useName = name;
		if(isArrayDef()) useName += "[i]";
		if(condition != null) out.write("if (" + condition + ")");
		out.write(useName + " = MaxisResKey.readXMLNode(" + inputName + ")");
	}
	
	public void writeWriteBinCodeList(Writer out, String bufferName) throws IOException{
		if(out == null) return;
		if(condition != null) out.write("if (" + condition + ")");
		if(typeName.equals("TGIResKey")) {
			out.write(name + ".writeBinTGI(" + bufferName + ")");
		}
		else if(typeName.equals("IGTResKey")) {
			out.write(name + ".writeBinIGT(" + bufferName + ")");
		}
		else if(typeName.equals("ITGResKey")) {
			out.write(name + ".writeBinITG(" + bufferName + ")");
		}
		else out.write(javaTypeName + ".writeBinaryTo(" + bufferName + ")");
	}
	
	public void writeWriteXmlCode(Writer out, String indent, String outputName) throws IOException{
		if(out == null) return;
		String useName = name;
		if(isArrayDef()) useName += "[i]";
		if(condition != null) out.write("if (" + condition + ")");
		out.write(indent + useName + ".writeXMLNode(" + outputName + ");");
	}
	
	public void writeReadPSListCode(Writer out, String inputName, String psidString) throws IOException{
		if(condition != null) out.write("if (" + condition + ")");
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
		if(condition != null) out.write("if (" + condition + ")");
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
