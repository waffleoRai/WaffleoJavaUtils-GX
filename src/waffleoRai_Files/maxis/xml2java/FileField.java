package waffleoRai_Files.maxis.xml2java;

import java.io.IOException;
import java.io.Writer;

import org.w3c.dom.Element;

public class FileField extends StructField{
	
	public void initFromXmlElement(Element xmlElement) {
		super.initFromXmlElement(xmlElement);
		
		if(typeName != null && !typeName.isEmpty()) {
			if(typeName.startsWith("File:")) {
				String[] spl = typeName.split(":");
				if(spl.length > 1) {
					String sname = spl[1].trim();
					sname += "InteriorFile";
					typeName = sname;
					javaTypeName = sname;
				}
			}	
		}
		fieldType = TS3XJ.FIELD_TYPE_FILE;
	}
	
	public boolean hasFixedSize() {return false;}
	public int getBaseSize() {return 0;}
	public boolean canBeNull() {return true;}
	public boolean isXmlMultiLine() {return false;}
	
	public void writeReadBinCodeList(Writer out, String inputName) throws IOException{
		if(condition != null) out.write("if (" + condition + ") ");
		out.write(javaTypeName + ".readBinary(" + inputName + ", " + getArrLenString() + ")");
	}

}
