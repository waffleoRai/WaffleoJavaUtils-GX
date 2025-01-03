/*-----------------------------------------------------
 * Autogenerated Java class from XML definition.
 * Created Tue, 31 Dec 2024 18:02:27 -0600
 *-----------------------------------------------------*/

//Last check pass: 2025/01/01 22:56

package waffleoRai_Containers.maxis.ts3.savefmts.sim.traits;

import java.io.IOException;
import java.io.Writer;

import org.w3c.dom.Element;

import waffleoRai_Containers.maxis.MaxisPropertyStream;
import waffleoRai_Containers.maxis.MaxisTypes;
import waffleoRai_Containers.maxis.ts3.savefmts.TS3Saveable;
import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.FileBuffer;

public class TraitManager extends TS3Saveable{
//TS3 Script: Sims3.Gameplay.CAS.TraitManager

	public static final int PSID_INBOOKCLUB = 0x093E24F9;
	public static final int PSID_MUNIVERSITYGRADUATETRAITENABLED = 0x9DC9BE5B;
	public static final int PSID_MUNIVERSITYGRADUATETRAITGUID = 0x9BE87A40;
	public static final int PSID_MSOCIALGROUPTRAITENABLED = 0xCEB3F8DD;
	public static final int PSID_MSOCIALGROUPTRAITGUID = 0x41B0C62E;

	private static final String XMLKEY_INBOOKCLUB = "InBookClub";
	private static final String XMLKEY_MUNIVERSITYGRADUATETRAITENABLED = "UniversityGraduateTraitEnabled";
	private static final String XMLKEY_MUNIVERSITYGRADUATETRAITGUID = "UniversityGraduateTraitGuid";
	private static final String XMLKEY_MSOCIALGROUPTRAITENABLED = "SocialGroupTraitEnabled";
	private static final String XMLKEY_MSOCIALGROUPTRAITGUID = "SocialGroupTraitGuid";

	public boolean inBookClub;
	public boolean mUniversityGraduateTraitEnabled;
	public long mUniversityGraduateTraitGuid;
	public boolean mSocialGroupTraitEnabled;
	public long mSocialGroupTraitGuid;

	public TraitManager() {
		xmlNodeName = "TraitManager";
		baseSize = 3;
	}
	
	/*----- Read -----*/
	
	protected boolean readBinary_internal(BufferReference dat) {
		if(dat == null) return false;
		
		inBookClub = MaxisTypes.readBinaryBool(dat);
		mUniversityGraduateTraitEnabled = MaxisTypes.readBinaryBool(dat);
		mUniversityGraduateTraitGuid = dat.nextLong();
		mSocialGroupTraitEnabled = MaxisTypes.readBinaryBool(dat);
		mSocialGroupTraitGuid = dat.nextLong();

		return true;
	}
	
	protected boolean readXMLNode_internal(Element xml_element) {
		if(xml_element == null) return false;
		String nn = xml_element.getNodeName();
		if(nn == null) return false;
		if(!nn.equals(xmlNodeName)) return false;
		
		String aval = null;
		aval = xml_element.getAttribute(XMLKEY_INBOOKCLUB);
		if(aval != null) inBookClub = Boolean.parseBoolean(aval);
		aval = xml_element.getAttribute(XMLKEY_MUNIVERSITYGRADUATETRAITENABLED);
		if(aval != null) mUniversityGraduateTraitEnabled = Boolean.parseBoolean(aval);
		aval = xml_element.getAttribute(XMLKEY_MUNIVERSITYGRADUATETRAITGUID);
		if(aval != null) mUniversityGraduateTraitGuid = TraitNames.valueFromString(aval);
		aval = xml_element.getAttribute(XMLKEY_MSOCIALGROUPTRAITENABLED);
		if(aval != null) mSocialGroupTraitEnabled = Boolean.parseBoolean(aval);
		aval = xml_element.getAttribute(XMLKEY_MSOCIALGROUPTRAITGUID);
		if(aval != null) mSocialGroupTraitGuid = TraitNames.valueFromString(aval);

		return true;
	}
	
	protected boolean readPropertyStream_internal(MaxisPropertyStream stream) {
		if(stream == null) return false;
		
		inBookClub = stream.getFieldAsBool(PSID_INBOOKCLUB);
		mUniversityGraduateTraitEnabled = stream.getFieldAsBool(PSID_MUNIVERSITYGRADUATETRAITENABLED);
		mUniversityGraduateTraitGuid = stream.getFieldAsLong(PSID_MUNIVERSITYGRADUATETRAITGUID);
		mSocialGroupTraitEnabled = stream.getFieldAsBool(PSID_MSOCIALGROUPTRAITENABLED);
		mSocialGroupTraitGuid = stream.getFieldAsLong(PSID_MSOCIALGROUPTRAITGUID);

		return true;
	}
	
	public static TraitManager readBinary(BufferReference dat) {
		if(dat == null) return null;
		TraitManager str = new TraitManager();
		if(!str.readBinary_internal(dat)) return null;
		return str;
	}
	
	public static TraitManager readXMLNode(Element xml_element) {
		if(xml_element == null) return null;
		TraitManager str = new TraitManager();
		if(!str.readXMLNode_internal(xml_element)) return null;
		return str;
	}
	
	public static TraitManager readPropertyStream(BufferReference dat, boolean byteOrder, int verFieldSize) {
		if(dat == null) return null;
		MaxisPropertyStream stream = MaxisPropertyStream.openForRead(dat, byteOrder, verFieldSize);
		return readPropertyStream(stream);
	}
	
	public static TraitManager readPropertyStream(MaxisPropertyStream stream) {
		if(stream == null) return null;
		TraitManager str = new TraitManager();
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
		
		MaxisTypes.writeBinaryBool(target, inBookClub);
		MaxisTypes.writeBinaryBool(target, mUniversityGraduateTraitEnabled);
		target.addToFile(mUniversityGraduateTraitGuid);
		MaxisTypes.writeBinaryBool(target, mSocialGroupTraitEnabled);
		target.addToFile(mSocialGroupTraitGuid);

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
		out.write(String.format(" %s=\"%b\"", XMLKEY_INBOOKCLUB, inBookClub));
		out.write(String.format(" %s=\"%b\"", XMLKEY_MUNIVERSITYGRADUATETRAITENABLED, mUniversityGraduateTraitEnabled));
		out.write(String.format(" %s=\"%s\"", XMLKEY_MUNIVERSITYGRADUATETRAITGUID, TraitNames.stringFromValue(mUniversityGraduateTraitGuid)));
		out.write(String.format(" %s=\"%b\"", XMLKEY_MSOCIALGROUPTRAITENABLED, mSocialGroupTraitEnabled));
		out.write(String.format(" %s=\"%s\"", XMLKEY_MSOCIALGROUPTRAITGUID, TraitNames.stringFromValue(mSocialGroupTraitGuid)));
		out.write("/>\n");

	}

	public void addToPropertyStream(MaxisPropertyStream ps) {	
		if(ps == null) return;
		ps.addBool(inBookClub, PSID_INBOOKCLUB);
		ps.addBool(mUniversityGraduateTraitEnabled, PSID_MUNIVERSITYGRADUATETRAITENABLED);
		ps.addLong(mUniversityGraduateTraitGuid, PSID_MUNIVERSITYGRADUATETRAITGUID);
		ps.addBool(mSocialGroupTraitEnabled, PSID_MSOCIALGROUPTRAITENABLED);
		ps.addLong(mSocialGroupTraitGuid, PSID_MSOCIALGROUPTRAITGUID);
	}
	
}
