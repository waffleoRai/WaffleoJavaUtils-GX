/*-----------------------------------------------------
 * Autogenerated Java class from XML definition.
 * Created Wed, 1 Jan 2025 13:25:45 -0600
 *-----------------------------------------------------*/

//Last check pass: 2025/01/01 23:25

package waffleoRai_Containers.maxis.ts3.savefmts.travel;

import java.io.IOException;
import java.io.Writer;

import org.w3c.dom.Element;

import waffleoRai_Containers.maxis.MaxisPropertyStream;
import waffleoRai_Containers.maxis.ts3.savefmts.TS3Saveable;
import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.StringUtils;

public class Visa extends TS3Saveable{
//TS3 Script: Sims3.Gameplay.Visa.Visa

	public static final int PSID_VISALEVEL = 0x08742A73;
	public static final int PSID_VISAPOINTS = 0x08742A7A;

	private static final String XMLKEY_VISALEVEL = "VisaLevel";
	private static final String XMLKEY_VISAPOINTS = "VisaPoints";

	public int visaLevel;
	public float visaPoints;

	public Visa() {
		xmlNodeName = "Visa";
		baseSize = 8;
	}
	
	/*----- Read -----*/
	
	protected boolean readBinary_internal(BufferReference dat) {
		if(dat == null) return false;
		
		visaLevel = dat.nextInt();
		visaPoints = Float.intBitsToFloat(dat.nextInt());

		return true;
	}
	
	protected boolean readXMLNode_internal(Element xml_element) {
		if(xml_element == null) return false;
		String nn = xml_element.getNodeName();
		if(nn == null) return false;
		if(!nn.equals(xmlNodeName)) return false;
		
		String aval = null;
		aval = xml_element.getAttribute(XMLKEY_VISALEVEL);
		if(aval != null) visaLevel = StringUtils.parseSignedInt(aval);
		aval = xml_element.getAttribute(XMLKEY_VISAPOINTS);
		if(aval != null) visaPoints = (float)Double.parseDouble(aval);

		return true;
	}
	
	protected boolean readPropertyStream_internal(MaxisPropertyStream stream) {
		if(stream == null) return false;
		
		visaLevel = stream.getFieldAsInt(PSID_VISALEVEL);
		visaPoints = stream.getFieldAsFloat(PSID_VISAPOINTS);

		return true;
	}
	
	public static Visa readBinary(BufferReference dat) {
		if(dat == null) return null;
		Visa str = new Visa();
		if(!str.readBinary_internal(dat)) return null;
		return str;
	}
	
	public static Visa readXMLNode(Element xml_element) {
		if(xml_element == null) return null;
		Visa str = new Visa();
		if(!str.readXMLNode_internal(xml_element)) return null;
		return str;
	}
	
	public static Visa readPropertyStream(BufferReference dat, boolean byteOrder, int verFieldSize) {
		if(dat == null) return null;
		MaxisPropertyStream stream = MaxisPropertyStream.openForRead(dat, byteOrder, verFieldSize);
		return readPropertyStream(stream);
	}
	
	public static Visa readPropertyStream(MaxisPropertyStream stream) {
		if(stream == null) return null;
		Visa str = new Visa();
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
		
		target.addToFile(visaLevel);
		target.addToFile(Float.floatToRawIntBits(visaPoints));

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
		out.write(String.format(" %s=\"%d\"", XMLKEY_VISALEVEL, visaLevel));
		out.write(String.format(" %s=\"%.3f\"", XMLKEY_VISAPOINTS, visaPoints));
		out.write("/>\n");

	}

	public void addToPropertyStream(MaxisPropertyStream ps) {	
		if(ps == null) return;
		ps.addInt(visaLevel, PSID_VISALEVEL);
		ps.addFloat(visaPoints, PSID_VISAPOINTS);
	}
	
}