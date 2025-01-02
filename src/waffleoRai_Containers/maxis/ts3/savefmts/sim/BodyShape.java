/*-----------------------------------------------------
 * Autogenerated Java class from XML definition.
 * Created Wed, 1 Jan 2025 13:57:49 -0600
 *-----------------------------------------------------*/

//Last check pass: 2025/01/01 19:56

package waffleoRai_Containers.maxis.ts3.savefmts.sim;

import java.io.IOException;
import java.io.Writer;

import org.w3c.dom.Element;

import waffleoRai_Containers.maxis.MaxisPropertyStream;
import waffleoRai_Containers.maxis.ts3.savefmts.TS3Saveable;
import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.FileBuffer;

public class BodyShape extends TS3Saveable{
//TS3 Script: Sims3.Gameplay.CAS.SimDescriptionCore

	public static final int PSID_FAT = 0x468F679A;
	public static final int PSID_FIT = 0x3E8F5B22;
	public static final int PSID_THIN = 0xBE38DAD8;

	private static final String XMLKEY_FAT = "Fat";
	private static final String XMLKEY_FIT = "Fit";
	private static final String XMLKEY_THIN = "Thin";

	public float fat;
	public float fit;
	public float thin;

	public BodyShape() {
		xmlNodeName = "BodyShape";
		baseSize = 12;
	}
	
	/*----- Read -----*/
	
	protected boolean readBinary_internal(BufferReference dat) {
		if(dat == null) return false;
		
		fat = Float.intBitsToFloat(dat.nextInt());
		fit = Float.intBitsToFloat(dat.nextInt());
		thin = Float.intBitsToFloat(dat.nextInt());

		return true;
	}
	
	protected boolean readXMLNode_internal(Element xml_element) {
		if(xml_element == null) return false;
		String nn = xml_element.getNodeName();
		if(nn == null) return false;
		if(!nn.equals(xmlNodeName)) return false;
		
		String aval = null;
		aval = xml_element.getAttribute(XMLKEY_FAT);
		if(aval != null) fat = (float)Double.parseDouble(aval);
		aval = xml_element.getAttribute(XMLKEY_FIT);
		if(aval != null) fit = (float)Double.parseDouble(aval);
		aval = xml_element.getAttribute(XMLKEY_THIN);
		if(aval != null) thin = (float)Double.parseDouble(aval);

		return true;
	}
	
	protected boolean readPropertyStream_internal(MaxisPropertyStream stream) {
		if(stream == null) return false;
		
		fat = stream.getFieldAsFloat(PSID_FAT);
		fit = stream.getFieldAsFloat(PSID_FIT);
		thin = stream.getFieldAsFloat(PSID_THIN);

		return true;
	}
	
	public static BodyShape readBinary(BufferReference dat) {
		if(dat == null) return null;
		BodyShape str = new BodyShape();
		if(!str.readBinary_internal(dat)) return null;
		return str;
	}
	
	public static BodyShape readXMLNode(Element xml_element) {
		if(xml_element == null) return null;
		BodyShape str = new BodyShape();
		if(!str.readXMLNode_internal(xml_element)) return null;
		return str;
	}
	
	public static BodyShape readPropertyStream(BufferReference dat, boolean byteOrder, int verFieldSize) {
		if(dat == null) return null;
		MaxisPropertyStream stream = MaxisPropertyStream.openForRead(dat, byteOrder, verFieldSize);
		return readPropertyStream(stream);
	}
	
	public static BodyShape readPropertyStream(MaxisPropertyStream stream) {
		if(stream == null) return null;
		BodyShape str = new BodyShape();
		if(!str.readPropertyStream_internal(stream));
		return str;
	}
	
	/*----- Write -----*/
	
	public int getBinarySize() {
		int size = baseSize;
		return size;
	}
	
	public FileBuffer writeBinary(boolean byteOrder) {
		FileBuffer buff = new FileBuffer(getBinarySize(), byteOrder);
		writeBinaryTo(buff);
		return buff;
	}
	
	public int writeBinaryTo(FileBuffer target) {
		if(target == null) return 0;
		long stPos = target.getFileSize();
		
		target.addToFile(Float.floatToRawIntBits(fat));
		target.addToFile(Float.floatToRawIntBits(fit));
		target.addToFile(Float.floatToRawIntBits(thin));

		return (int)(target.getFileSize() - stPos);
	}

	public void writeXMLNode(Writer out, String indent) throws IOException {
		writeXMLNode(out, indent, null);
	}
	
	public void writeXMLNode(Writer out, String indent, String varName) throws IOException {
		if(out == null) return;
		if(indent == null) indent = "";
		
		out.write(indent);
		out.write(String.format("<%s", xmlNodeName));
		if(varName != null){
			out.write(String.format(" VarName=\"%s\"", varName));
		}
		out.write(String.format(" %s=\"%.3f\"", XMLKEY_FAT, fat));
		out.write(String.format(" %s=\"%.3f\"", XMLKEY_FIT, fit));
		out.write(String.format(" %s=\"%.3f\"", XMLKEY_THIN, thin));
		out.write("/>\n");

	}

	public void addToPropertyStream(MaxisPropertyStream ps) {	
		if(ps == null) return;
		ps.addFloat(fat, PSID_FAT);
		ps.addFloat(fit, PSID_FIT);
		ps.addFloat(thin, PSID_THIN);
	}
	
}
