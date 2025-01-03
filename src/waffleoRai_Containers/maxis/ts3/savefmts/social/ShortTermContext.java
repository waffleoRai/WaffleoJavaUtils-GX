/*-----------------------------------------------------
 * Autogenerated Java class from XML definition.
 * Created Wed, 1 Jan 2025 17:53:23 -0600
 *-----------------------------------------------------*/

//Last check pass: 2025/01/01 18:48

package waffleoRai_Containers.maxis.ts3.savefmts.social;

import java.io.IOException;
import java.io.Writer;

import org.w3c.dom.Element;

import waffleoRai_Containers.maxis.MaxisPropertyStream;
import waffleoRai_Containers.maxis.MaxisTypes;
import waffleoRai_Containers.maxis.ts3.savefmts.TS3Saveable;
import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.FileBuffer;

public class ShortTermContext extends TS3Saveable{
//TS3 Script: Sims3.Gameplay.Socializing.ShortTermContext

	public static final int PSID_MCURRENTSTCSTRING = 0x2A61B934;
	public static final int PSID_MCURRENTCOMMODITYTYPESTRING = 0xFF2AC9DF;
	public static final int PSID_MCURRENTCOMMODITYVALUE = 0xEBCEBF46;

	private static final String XMLKEY_MCURRENTSTCSTRING = "CurrentStcString";
	private static final String XMLKEY_MCURRENTCOMMODITYTYPESTRING = "CurrentCommodityTypeString";
	private static final String XMLKEY_MCURRENTCOMMODITYVALUE = "CurrentCommodityValue";

	public String mCurrentStcString;
	public String mCurrentCommodityTypeString;
	public float mCurrentCommodityValue;

	public ShortTermContext() {
		xmlNodeName = "ShortTermContext";
		baseSize = 12;
	}
	
	/*----- Read -----*/
	
	protected boolean readBinary_internal(BufferReference dat) {
		if(dat == null) return false;
		
		mCurrentStcString = MaxisTypes.readMaxisString(dat);
		mCurrentCommodityTypeString = MaxisTypes.readMaxisString(dat);
		mCurrentCommodityValue = Float.intBitsToFloat(dat.nextInt());

		return true;
	}
	
	protected boolean readXMLNode_internal(Element xml_element) {
		if(xml_element == null) return false;
		String nn = xml_element.getNodeName();
		if(nn == null) return false;
		if(!nn.equals(xmlNodeName)) return false;
		
		String aval = null;
		aval = xml_element.getAttribute(XMLKEY_MCURRENTSTCSTRING);
		if(aval != null) mCurrentStcString = aval;
		aval = xml_element.getAttribute(XMLKEY_MCURRENTCOMMODITYTYPESTRING);
		if(aval != null) mCurrentCommodityTypeString = aval;
		aval = xml_element.getAttribute(XMLKEY_MCURRENTCOMMODITYVALUE);
		if(aval != null) mCurrentCommodityValue = (float)Double.parseDouble(aval);

		return true;
	}
	
	protected boolean readPropertyStream_internal(MaxisPropertyStream stream) {
		if(stream == null) return false;
		
		mCurrentStcString = stream.getFieldAsString(PSID_MCURRENTSTCSTRING);
		mCurrentCommodityTypeString = stream.getFieldAsString(PSID_MCURRENTCOMMODITYTYPESTRING);
		mCurrentCommodityValue = stream.getFieldAsFloat(PSID_MCURRENTCOMMODITYVALUE);

		return true;
	}
	
	public static ShortTermContext readBinary(BufferReference dat) {
		if(dat == null) return null;
		ShortTermContext str = new ShortTermContext();
		if(!str.readBinary_internal(dat)) return null;
		return str;
	}
	
	public static ShortTermContext readXMLNode(Element xml_element) {
		if(xml_element == null) return null;
		ShortTermContext str = new ShortTermContext();
		if(!str.readXMLNode_internal(xml_element)) return null;
		return str;
	}
	
	public static ShortTermContext readPropertyStream(BufferReference dat, boolean byteOrder, int verFieldSize) {
		if(dat == null) return null;
		MaxisPropertyStream stream = MaxisPropertyStream.openForRead(dat, byteOrder, verFieldSize);
		return readPropertyStream(stream);
	}
	
	public static ShortTermContext readPropertyStream(MaxisPropertyStream stream) {
		if(stream == null) return null;
		ShortTermContext str = new ShortTermContext();
		if(!str.readPropertyStream_internal(stream));
		return str;
	}
	
	/*----- Write -----*/
	
	public int getBinarySize() {
		int size = baseSize;
		if(mCurrentStcString != null) size += (mCurrentStcString.length() << 1);
		if(mCurrentCommodityTypeString != null) size += (mCurrentCommodityTypeString.length() << 1);
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
		
		MaxisTypes.serializeMaxisStringTo( mCurrentStcString, target);
		MaxisTypes.serializeMaxisStringTo( mCurrentCommodityTypeString, target);
		target.addToFile(Float.floatToRawIntBits(mCurrentCommodityValue));

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
		out.write(String.format(" %s=\"%s\"", XMLKEY_MCURRENTSTCSTRING, mCurrentStcString));
		out.write(String.format(" %s=\"%s\"", XMLKEY_MCURRENTCOMMODITYTYPESTRING, mCurrentCommodityTypeString));
		out.write(String.format(" %s=\"%.3f\"", XMLKEY_MCURRENTCOMMODITYVALUE, mCurrentCommodityValue));
		out.write("/>\n");

	}

	public void addToPropertyStream(MaxisPropertyStream ps) {	
		if(ps == null) return;
		ps.addString(mCurrentStcString, PSID_MCURRENTSTCSTRING);
		ps.addString(mCurrentCommodityTypeString, PSID_MCURRENTCOMMODITYTYPESTRING);
		ps.addFloat(mCurrentCommodityValue, PSID_MCURRENTCOMMODITYVALUE);
	}
	
}
