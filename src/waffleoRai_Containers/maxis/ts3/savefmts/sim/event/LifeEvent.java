/*-----------------------------------------------------
 * Autogenerated Java class from XML definition.
 * Created Wed, 1 Jan 2025 12:41:16 -0600
 *-----------------------------------------------------*/

//Last check pass: 2025/01/01 21:34

package waffleoRai_Containers.maxis.ts3.savefmts.sim.event;

import java.io.IOException;
import java.io.Writer;

import org.w3c.dom.Element;

import waffleoRai_Containers.maxis.MaxisPropertyStream;
import waffleoRai_Containers.maxis.MaxisTypes;
import waffleoRai_Containers.maxis.ts3.savefmts.TS3Saveable;
import waffleoRai_Containers.maxis.ts3.savefmts.sim.dreams.NodeSubject;
import waffleoRai_Files.XMLReader;
import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.StringUtils;

public class LifeEvent extends TS3Saveable{
//TS3 Script: Sims3.Gameplay.ActorSystems.LifeEventManager.LifeEvent

	public static final int PSID_MID = 0x4876EE06;
	public static final int PSID_TIMESINCEEVENT = 0x86DD0E3F;
	public static final int PSID_MNATURE = 0xA2A87B34;
	public static final int PSID_MISVISIBLE = 0xE04CB2BD;
	public static final int PSID_MISSHARED = 0xD980D35E;
	public static final int PSID_MINPUTNUMBER = 0x8B372878;
	public static final int PSID_MCUSTOMNAME = 0x9886023D;
	public static final int PSID_MCUSTOMDESCRIPTION = 0x1B10E542;
	public static final int PSID_MNODESUBJECT = 0xF054A46F;
	public static final int PSID_MSNAPSHOT = 0x38E2FA7D;

	private static final String XMLKEY_MID = "Id";
	private static final String XMLKEY_TIMESINCEEVENT = "TimeSinceEvent";
	private static final String XMLKEY_MNATURE = "Nature";
	private static final String XMLKEY_MISVISIBLE = "IsVisible";
	private static final String XMLKEY_MISSHARED = "IsShared";
	private static final String XMLKEY_MINPUTNUMBER = "InputNumber";
	private static final String XMLKEY_MCUSTOMNAME = "CustomName";
	private static final String XMLKEY_MCUSTOMDESCRIPTION = "CustomDescription";
	private static final String XMLKEY_MNODESUBJECT = "NodeSubject";
	private static final String XMLKEY_MSNAPSHOT = "Snapshot";

	public int mId;
	public float timeSinceEvent;
	public int mNature;
	public boolean mIsVisible;
	public boolean mIsShared;
	public int mInputNumber;
	public String mCustomName;
	public String mCustomDescription;
	public NodeSubject mNodeSubject;
	public int mSnapshot;

	public LifeEvent() {
		xmlNodeName = "LifeEvent";
		baseSize = 38;
	}
	
	/*----- Read -----*/
	
	protected boolean readBinary_internal(BufferReference dat) {
		if(dat == null) return false;
		
		mId = dat.nextInt();
		timeSinceEvent = Float.intBitsToFloat(dat.nextInt());
		mNature = dat.nextInt();
		mIsVisible = MaxisTypes.readBinaryBool(dat);
		mIsShared = MaxisTypes.readBinaryBool(dat);
		mInputNumber = dat.nextInt();
		mCustomName = MaxisTypes.readMaxisString(dat);
		mCustomDescription = MaxisTypes.readMaxisString(dat);
		mNodeSubject = NodeSubject.readBinary(dat);
		mSnapshot = dat.nextInt();

		return true;
	}
	
	protected boolean readXMLNode_internal(Element xml_element) {
		if(xml_element == null) return false;
		String nn = xml_element.getNodeName();
		if(nn == null) return false;
		if(!nn.equals(xmlNodeName)) return false;
		
		String aval = null;
		Element child = null;
		aval = xml_element.getAttribute(XMLKEY_MID);
		if(aval != null) mId = StringUtils.parseUnsignedInt(aval);
		aval = xml_element.getAttribute(XMLKEY_TIMESINCEEVENT);
		if(aval != null) timeSinceEvent = (float)Double.parseDouble(aval);
		aval = xml_element.getAttribute(XMLKEY_MNATURE);
		if(aval != null) mNature = LifeEventNature.valueFromString(aval);
		aval = xml_element.getAttribute(XMLKEY_MISVISIBLE);
		if(aval != null) mIsVisible = Boolean.parseBoolean(aval);
		aval = xml_element.getAttribute(XMLKEY_MISSHARED);
		if(aval != null) mIsShared = Boolean.parseBoolean(aval);
		aval = xml_element.getAttribute(XMLKEY_MINPUTNUMBER);
		if(aval != null) mInputNumber = StringUtils.parseSignedInt(aval);
		aval = xml_element.getAttribute(XMLKEY_MCUSTOMNAME);
		if(aval != null) mCustomName = aval;
		aval = xml_element.getAttribute(XMLKEY_MCUSTOMDESCRIPTION);
		if(aval != null) mCustomDescription = aval;
		child = XMLReader.getFirstChildElementWithTagAndAttribute(xml_element, "NodeSubject", "VarName", XMLKEY_MNODESUBJECT);
		if(child != null) mNodeSubject = NodeSubject.readXMLNode(child);
		aval = xml_element.getAttribute(XMLKEY_MSNAPSHOT);
		if(aval != null) mSnapshot = StringUtils.parseSignedInt(aval);

		return true;
	}
	
	protected boolean readPropertyStream_internal(MaxisPropertyStream stream) {
		if(stream == null) return false;
		
		mId = stream.getFieldAsInt(PSID_MID);
		timeSinceEvent = stream.getFieldAsFloat(PSID_TIMESINCEEVENT);
		mNature = stream.getFieldAsInt(PSID_MNATURE);
		mIsVisible = stream.getFieldAsBool(PSID_MISVISIBLE);
		mIsShared = stream.getFieldAsBool(PSID_MISSHARED);
		mInputNumber = stream.getFieldAsInt(PSID_MINPUTNUMBER);
		mCustomName = stream.getFieldAsString(PSID_MCUSTOMNAME);
		mCustomDescription = stream.getFieldAsString(PSID_MCUSTOMDESCRIPTION);
		mNodeSubject = NodeSubject.readPropertyStream(stream.getChildStream(PSID_MNODESUBJECT));
		mSnapshot = stream.getFieldAsInt(PSID_MSNAPSHOT);

		return true;
	}
	
	public static LifeEvent readBinary(BufferReference dat) {
		if(dat == null) return null;
		LifeEvent str = new LifeEvent();
		if(!str.readBinary_internal(dat)) return null;
		return str;
	}
	
	public static LifeEvent readXMLNode(Element xml_element) {
		if(xml_element == null) return null;
		LifeEvent str = new LifeEvent();
		if(!str.readXMLNode_internal(xml_element)) return null;
		return str;
	}
	
	public static LifeEvent readPropertyStream(BufferReference dat, boolean byteOrder, int verFieldSize) {
		if(dat == null) return null;
		MaxisPropertyStream stream = MaxisPropertyStream.openForRead(dat, byteOrder, verFieldSize);
		return readPropertyStream(stream);
	}
	
	public static LifeEvent readPropertyStream(MaxisPropertyStream stream) {
		if(stream == null) return null;
		LifeEvent str = new LifeEvent();
		if(!str.readPropertyStream_internal(stream));
		return str;
	}
	
	/*----- Write -----*/
	
	public int getBinarySize() {
		int size = baseSize;
		size += (mCustomName.length() << 1);
		size += (mCustomDescription.length() << 1);
		 if(mNodeSubject != null) size += mNodeSubject.getBinarySize();
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
		
		target.addToFile(mId);
		target.addToFile(Float.floatToRawIntBits(timeSinceEvent));
		target.addToFile(mNature);
		MaxisTypes.writeBinaryBool(target, mIsVisible);
		MaxisTypes.writeBinaryBool(target, mIsShared);
		target.addToFile(mInputNumber);
		MaxisTypes.serializeMaxisStringTo( mCustomName, target);
		MaxisTypes.serializeMaxisStringTo( mCustomDescription, target);
		mNodeSubject.writeBinaryTo(target);
		target.addToFile(mSnapshot);

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
		out.write(String.format(" %s=\"0x%08x\"", XMLKEY_MID, mId));
		out.write(String.format(" %s=\"%.3f\"", XMLKEY_TIMESINCEEVENT, timeSinceEvent));
		out.write(String.format(" %s=\"%s\"", XMLKEY_MNATURE, LifeEventNature.stringFromValue(mNature)));
		out.write(String.format(" %s=\"%b\"", XMLKEY_MISVISIBLE, mIsVisible));
		out.write(String.format(" %s=\"%b\"", XMLKEY_MISSHARED, mIsShared));
		out.write(String.format(" %s=\"%d\"", XMLKEY_MINPUTNUMBER, mInputNumber));
		out.write(String.format(" %s=\"%s\"", XMLKEY_MCUSTOMNAME, mCustomName));
		out.write(String.format(" %s=\"%s\"", XMLKEY_MCUSTOMDESCRIPTION, mCustomDescription));
		out.write(String.format(" %s=\"%d\"", XMLKEY_MSNAPSHOT, mSnapshot));
		out.write(">\n");
		if(mNodeSubject != null) mNodeSubject.writeXMLNode(out, indent + "\t", XMLKEY_MNODESUBJECT);
		out.write(indent);
		out.write(String.format("</%s>\n", xmlNodeName));

	}

	public void addToPropertyStream(MaxisPropertyStream ps) {	
		if(ps == null) return;
		boolean byte_order = ps.getByteOrder();
		int verFieldSize = ps.getVersionFieldSize();
		
		ps.addInt(mId, PSID_MID);
		ps.addFloat(timeSinceEvent, PSID_TIMESINCEEVENT);
		ps.addInt(mNature, PSID_MNATURE);
		ps.addBool(mIsVisible, PSID_MISVISIBLE);
		ps.addBool(mIsShared, PSID_MISSHARED);
		ps.addInt(mInputNumber, PSID_MINPUTNUMBER);
		ps.addString(mCustomName, PSID_MCUSTOMNAME);
		ps.addString(mCustomDescription, PSID_MCUSTOMDESCRIPTION);
		ps.addChildStream(mNodeSubject.toPropertyStream(byte_order, verFieldSize), PSID_MNODESUBJECT);
		ps.addInt(mSnapshot, PSID_MSNAPSHOT);
	}
	
}