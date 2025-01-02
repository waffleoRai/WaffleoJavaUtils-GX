/*-----------------------------------------------------
 * Autogenerated Java class from XML definition.
 * Created Wed, 1 Jan 2025 13:25:45 -0600
 *-----------------------------------------------------*/

//Last check pass: 2025/01/01 23:24

package waffleoRai_Containers.maxis.ts3.savefmts.travel;

import java.io.IOException;
import java.io.Writer;

import org.w3c.dom.Element;

import waffleoRai_Containers.maxis.MaxisPropertyStream;
import waffleoRai_Containers.maxis.ts3.savefmts.TS3Saveable;
import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.StringUtils;

public class CollectableRelicJournalData extends TS3Saveable{
//TS3 Script: Sims3.Gameplay.Skills.CollectableRelicJournalData

	public static final int PSID_MGUID = 0x089FD764;
	public static final int PSID_MNUMFOUND = 0x089FD791;
	public static final int PSID_MOLDESTAGE = 0x089FD795;
	public static final int PSID_MHIGHESTVALUE = 0x089FD799;

	private static final String XMLKEY_MGUID = "Guid";
	private static final String XMLKEY_MNUMFOUND = "NumFound";
	private static final String XMLKEY_MOLDESTAGE = "OldestAge";
	private static final String XMLKEY_MHIGHESTVALUE = "HighestValue";

	public int mGuid;
	public int mNumFound;
	public int mOldestAge;
	public int mHighestValue;

	public CollectableRelicJournalData() {
		xmlNodeName = "CollectableRelicJournalData";
		baseSize = 16;
	}
	
	/*----- Read -----*/
	
	protected boolean readBinary_internal(BufferReference dat) {
		if(dat == null) return false;
		
		mGuid = dat.nextInt();
		mNumFound = dat.nextInt();
		mOldestAge = dat.nextInt();
		mHighestValue = dat.nextInt();

		return true;
	}
	
	protected boolean readXMLNode_internal(Element xml_element) {
		if(xml_element == null) return false;
		String nn = xml_element.getNodeName();
		if(nn == null) return false;
		if(!nn.equals(xmlNodeName)) return false;
		
		String aval = null;
		aval = xml_element.getAttribute(XMLKEY_MGUID);
		if(aval != null) mGuid = CollectableRelicGuid.valueFromString(aval);
		aval = xml_element.getAttribute(XMLKEY_MNUMFOUND);
		if(aval != null) mNumFound = StringUtils.parseSignedInt(aval);
		aval = xml_element.getAttribute(XMLKEY_MOLDESTAGE);
		if(aval != null) mOldestAge = StringUtils.parseSignedInt(aval);
		aval = xml_element.getAttribute(XMLKEY_MHIGHESTVALUE);
		if(aval != null) mHighestValue = StringUtils.parseSignedInt(aval);

		return true;
	}
	
	protected boolean readPropertyStream_internal(MaxisPropertyStream stream) {
		if(stream == null) return false;
		
		mGuid = stream.getFieldAsInt(PSID_MGUID);
		mNumFound = stream.getFieldAsInt(PSID_MNUMFOUND);
		mOldestAge = stream.getFieldAsInt(PSID_MOLDESTAGE);
		mHighestValue = stream.getFieldAsInt(PSID_MHIGHESTVALUE);

		return true;
	}
	
	public static CollectableRelicJournalData readBinary(BufferReference dat) {
		if(dat == null) return null;
		CollectableRelicJournalData str = new CollectableRelicJournalData();
		if(!str.readBinary_internal(dat)) return null;
		return str;
	}
	
	public static CollectableRelicJournalData readXMLNode(Element xml_element) {
		if(xml_element == null) return null;
		CollectableRelicJournalData str = new CollectableRelicJournalData();
		if(!str.readXMLNode_internal(xml_element)) return null;
		return str;
	}
	
	public static CollectableRelicJournalData readPropertyStream(BufferReference dat, boolean byteOrder, int verFieldSize) {
		if(dat == null) return null;
		MaxisPropertyStream stream = MaxisPropertyStream.openForRead(dat, byteOrder, verFieldSize);
		return readPropertyStream(stream);
	}
	
	public static CollectableRelicJournalData readPropertyStream(MaxisPropertyStream stream) {
		if(stream == null) return null;
		CollectableRelicJournalData str = new CollectableRelicJournalData();
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
		
		target.addToFile(mGuid);
		target.addToFile(mNumFound);
		target.addToFile(mOldestAge);
		target.addToFile(mHighestValue);

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
		out.write(String.format(" %s=\"%s\"", XMLKEY_MGUID, CollectableRelicGuid.stringFromValue(mGuid)));
		out.write(String.format(" %s=\"%d\"", XMLKEY_MNUMFOUND, mNumFound));
		out.write(String.format(" %s=\"%d\"", XMLKEY_MOLDESTAGE, mOldestAge));
		out.write(String.format(" %s=\"%d\"", XMLKEY_MHIGHESTVALUE, mHighestValue));
		out.write("/>\n");

	}

	public void addToPropertyStream(MaxisPropertyStream ps) {	
		if(ps == null) return;
		ps.addInt(mGuid, PSID_MGUID);
		ps.addInt(mNumFound, PSID_MNUMFOUND);
		ps.addInt(mOldestAge, PSID_MOLDESTAGE);
		ps.addInt(mHighestValue, PSID_MHIGHESTVALUE);
	}
	
}
