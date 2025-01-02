/*-----------------------------------------------------
 * Autogenerated Java class from XML definition.
 * Created Wed, 1 Jan 2025 17:53:23 -0600
 *-----------------------------------------------------*/

//Last check pass: 2025/01/01 18:45

package waffleoRai_Containers.maxis.ts3.savefmts.social;

import java.io.IOException;
import java.io.Writer;
import org.w3c.dom.Element;

import waffleoRai_Containers.maxis.MaxisPropertyStream;
import waffleoRai_Containers.maxis.MaxisTypes;
import waffleoRai_Containers.maxis.ts3.savefmts.TS3Saveable;
import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.StringUtils;

public class LongTermRelationship extends TS3Saveable{
//TS3 Script: Sims3.Gameplay.Socializing.LongTermRelationship

	public static final int PSID_MLIKING = 0x67234595;
	public static final int PSID_MLASTLIKINGCHANGEWHEN = 0x48A4F08D;
	public static final int PSID_MLASTCHANGE = 0xA96BEC19;
	public static final int PSID_MHIGHESTLIKING = 0xA96BEC20;
	public static final int PSID_MWHENSTATESTARTED = 0x84D623F3;
	public static final int PSID_MCURRENTLTRSTRING = 0x18A005B2;
	public static final int PSID_MINTERACTION = 0x6F625B7B;

	private static final String XMLKEY_MLIKING = "Liking";
	private static final String XMLKEY_MLASTLIKINGCHANGEWHEN = "LastLikingChangeWhen";
	private static final String XMLKEY_MLASTCHANGE = "LastChange";
	private static final String XMLKEY_MHIGHESTLIKING = "HighestLiking";
	private static final String XMLKEY_MWHENSTATESTARTED = "WhenStateStarted";
	private static final String XMLKEY_MCURRENTLTRSTRING = "CurrentLtrString";
	private static final String XMLKEY_MINTERACTION = "Interaction";

	public float mLiking;
	public int mLastLikingChangeWhen;
	public float mLastChange;
	public float mHighestLiking;
	public long mWhenStateStarted;
	public String mCurrentLtrString;
	public int mInteraction;

	public LongTermRelationship() {
		xmlNodeName = "LongTermRelationship";
		baseSize = 32;
	}
	
	/*----- Read -----*/
	
	protected boolean readBinary_internal(BufferReference dat) {
		if(dat == null) return false;
		
		mLiking = Float.intBitsToFloat(dat.nextInt());
		mLastLikingChangeWhen = dat.nextInt();
		mLastChange = Float.intBitsToFloat(dat.nextInt());
		mHighestLiking = Float.intBitsToFloat(dat.nextInt());
		mWhenStateStarted = dat.nextLong();
		mCurrentLtrString = MaxisTypes.readMaxisString(dat);
		mInteraction = dat.nextInt();

		return true;
	}
	
	protected boolean readXMLNode_internal(Element xml_element) {
		if(xml_element == null) return false;
		String nn = xml_element.getNodeName();
		if(nn == null) return false;
		if(!nn.equals(xmlNodeName)) return false;
		
		String aval = null;
		aval = xml_element.getAttribute(XMLKEY_MLIKING);
		if(aval != null) mLiking = (float)Double.parseDouble(aval);
		aval = xml_element.getAttribute(XMLKEY_MLASTLIKINGCHANGEWHEN);
		if(aval != null) mLastLikingChangeWhen = StringUtils.parseSignedInt(aval);
		aval = xml_element.getAttribute(XMLKEY_MLASTCHANGE);
		if(aval != null) mLastChange = (float)Double.parseDouble(aval);
		aval = xml_element.getAttribute(XMLKEY_MHIGHESTLIKING);
		if(aval != null) mHighestLiking = (float)Double.parseDouble(aval);
		aval = xml_element.getAttribute(XMLKEY_MWHENSTATESTARTED);
		if(aval != null) mWhenStateStarted = StringUtils.parseSignedLong(aval);
		aval = xml_element.getAttribute(XMLKEY_MCURRENTLTRSTRING);
		if(aval != null) mCurrentLtrString = aval;
		aval = xml_element.getAttribute(XMLKEY_MINTERACTION);
		if(aval != null) mInteraction = LTRInteractionBits.valueFromString(aval);

		return true;
	}
	
	protected boolean readPropertyStream_internal(MaxisPropertyStream stream) {
		if(stream == null) return false;
		
		mLiking = stream.getFieldAsFloat(PSID_MLIKING);
		mLastLikingChangeWhen = stream.getFieldAsInt(PSID_MLASTLIKINGCHANGEWHEN);
		mLastChange = stream.getFieldAsFloat(PSID_MLASTCHANGE);
		mHighestLiking = stream.getFieldAsFloat(PSID_MHIGHESTLIKING);
		mWhenStateStarted = stream.getFieldAsLong(PSID_MWHENSTATESTARTED);
		mCurrentLtrString = stream.getFieldAsString(PSID_MCURRENTLTRSTRING);
		mInteraction = stream.getFieldAsInt(PSID_MINTERACTION);

		return true;
	}
	
	public static LongTermRelationship readBinary(BufferReference dat) {
		if(dat == null) return null;
		LongTermRelationship str = new LongTermRelationship();
		if(!str.readBinary_internal(dat)) return null;
		return str;
	}
	
	public static LongTermRelationship readXMLNode(Element xml_element) {
		if(xml_element == null) return null;
		LongTermRelationship str = new LongTermRelationship();
		if(!str.readXMLNode_internal(xml_element)) return null;
		return str;
	}
	
	public static LongTermRelationship readPropertyStream(BufferReference dat, boolean byteOrder, int verFieldSize) {
		if(dat == null) return null;
		MaxisPropertyStream stream = MaxisPropertyStream.openForRead(dat, byteOrder, verFieldSize);
		return readPropertyStream(stream);
	}
	
	public static LongTermRelationship readPropertyStream(MaxisPropertyStream stream) {
		if(stream == null) return null;
		LongTermRelationship str = new LongTermRelationship();
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
		
		target.addToFile(Float.floatToRawIntBits(mLiking));
		target.addToFile(mLastLikingChangeWhen);
		target.addToFile(Float.floatToRawIntBits(mLastChange));
		target.addToFile(Float.floatToRawIntBits(mHighestLiking));
		target.addToFile(mWhenStateStarted);
		MaxisTypes.serializeMaxisStringTo( mCurrentLtrString, target);
		target.addToFile(mInteraction);

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
		out.write(String.format(" %s=\"%.3f\"", XMLKEY_MLIKING, mLiking));
		out.write(String.format(" %s=\"%d\"", XMLKEY_MLASTLIKINGCHANGEWHEN, mLastLikingChangeWhen));
		out.write(String.format(" %s=\"%.3f\"", XMLKEY_MLASTCHANGE, mLastChange));
		out.write(String.format(" %s=\"%.3f\"", XMLKEY_MHIGHESTLIKING, mHighestLiking));
		out.write(String.format(" %s=\"%d\"", XMLKEY_MWHENSTATESTARTED, mWhenStateStarted));
		out.write(String.format(" %s=\"%s\"", XMLKEY_MCURRENTLTRSTRING, mCurrentLtrString));
		out.write(String.format(" %s=\"%s\"", XMLKEY_MINTERACTION, LTRInteractionBits.stringFromValue(mInteraction)));
		out.write("/>\n");

	}

	public void addToPropertyStream(MaxisPropertyStream ps) {	
		if(ps == null) return;
		ps.addFloat(mLiking, PSID_MLIKING);
		ps.addInt(mLastLikingChangeWhen, PSID_MLASTLIKINGCHANGEWHEN);
		ps.addFloat(mLastChange, PSID_MLASTCHANGE);
		ps.addFloat(mHighestLiking, PSID_MHIGHESTLIKING);
		ps.addLong(mWhenStateStarted, PSID_MWHENSTATESTARTED);
		ps.addString(mCurrentLtrString, PSID_MCURRENTLTRSTRING);
		ps.addInt(mInteraction, PSID_MINTERACTION);
	}
	
}
