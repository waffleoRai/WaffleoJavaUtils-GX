/*-----------------------------------------------------
 * Autogenerated Java class from XML definition.
 * Created Wed, 1 Jan 2025 13:14:46 -0600
 *-----------------------------------------------------*/

//Last check pass: 2025/01/01 23:23

package waffleoRai_Containers.maxis.ts3.savefmts.skills;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.w3c.dom.Element;

import waffleoRai_Containers.maxis.MaxisPropertyStream;
import waffleoRai_Containers.maxis.ts3.savefmts.TS3Saveable;
import waffleoRai_Files.XMLReader;
import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.StringUtils;

public class SkillManager extends TS3Saveable{
//TS3 Script: Sims3.Gameplay.Skills.SkillManager

	public static final int PSID_COUNT = 0xCACA460C;
	public static final int PSID_SKILLS = 0x34DCF303;
	public static final int PSID_MROBOTLEARNEDSKILLS = 0x0DC94A1E;
	public static final int PSID_MSKILLMODIFIERSKEYS = 0x0A00F578;
	public static final int PSID_MSKILLMODIFIERSVALUES = 0x0A00F57D;

	private static final String XMLKEY_COUNT = "Count";
	private static final String XMLKEY_SKILLS = "Skills";
	private static final String XMLKEY_MROBOTLEARNEDSKILLS = "MRobotLearnedSkills";
	private static final String XMLKEY_MSKILLMODIFIERSKEYS = "MSkillModifiersKeys";
	private static final String XMLKEY_MSKILLMODIFIERSVALUES = "MSkillModifiersValues";

	public int count;
	public ArrayList<SkillData> skills = new ArrayList<SkillData>();
	public RobotSkillList mRobotLearnedSkills;
	public short[] mSkillModifiersKeys;
	public float[] mSkillModifiersValues;

	public SkillManager() {
		xmlNodeName = "SkillManager";
		baseSize = 10;
	}
	
	/*----- Inner Classes -----*/

	public static class RobotSkillList extends TS3Saveable{

		public static final int PSID_COUNT = 0xCACA460C;
		public static final int PSID_SKILLS = 0x34DCF303;

		private static final String XMLKEY_COUNT = "Count";
		private static final String XMLKEY_SKILLS = "Skills";

		public int count;
		public ArrayList<SkillData> skills = new ArrayList<SkillData>();

		public RobotSkillList() {
			xmlNodeName = "RobotSkillList";
			baseSize = 4;
		}
		
		/*----- Read -----*/
		
		protected boolean readBinary_internal(BufferReference dat) {
			if(dat == null) return false;
			
			count = dat.nextInt();
			skills.ensureCapacity(count);
			for(int i = 0; i < count; i++){
				SkillData skillData = SkillData.readBinary(dat);
				if(skillData != null) skills.add(skillData);
			}

			return true;
		}
		
		protected boolean readXMLNode_internal(Element xml_element) {
			if(xml_element == null) return false;
			String nn = xml_element.getNodeName();
			if(nn == null) return false;
			if(!nn.equals(xmlNodeName)) return false;
			
			String aval = null;
			Element child = null;
			aval = xml_element.getAttribute(XMLKEY_COUNT);
			if(aval != null) count = StringUtils.parseUnsignedInt(aval);
			child = XMLReader.getFirstChildElementWithTagAndAttribute(xml_element, "List", "VarName", XMLKEY_SKILLS);
			if(child != null){
				skills.ensureCapacity(count);
				List<Element> gclist = XMLReader.getChildElementsWithTag(child, "SkillData");
				for(Element gc : gclist){
					skills.add(SkillData.readXMLNode(gc));
				}
			}

			return true;
		}
		
		protected boolean readPropertyStream_internal(MaxisPropertyStream stream) {
			if(stream == null) return false;
			
			count = stream.getFieldAsInt(PSID_COUNT);
			skills.ensureCapacity(count);
			
			Set<Integer> allPsid = stream.getAllFieldKeys();
			for(Integer psid : allPsid) {
				if(psid == PSID_COUNT) continue;
				SkillData skillData = SkillData.readPropertyStream(stream.getChildStream(psid));
				if(skillData != null) {
					if((psid - skillData.guid) == PSID_SKILLS) {
						skills.add(skillData);
					}
				}
			}

			return true;
		}
		
		public static RobotSkillList readBinary(BufferReference dat) {
			if(dat == null) return null;
			RobotSkillList str = new RobotSkillList();
			if(!str.readBinary_internal(dat)) return null;
			return str;
		}
		
		public static RobotSkillList readXMLNode(Element xml_element) {
			if(xml_element == null) return null;
			RobotSkillList str = new RobotSkillList();
			if(!str.readXMLNode_internal(xml_element)) return null;
			return str;
		}
		
		public static RobotSkillList readPropertyStream(BufferReference dat, boolean byteOrder, int verFieldSize) {
			if(dat == null) return null;
			MaxisPropertyStream stream = MaxisPropertyStream.openForRead(dat, byteOrder, verFieldSize);
			return readPropertyStream(stream);
		}
		
		public static RobotSkillList readPropertyStream(MaxisPropertyStream stream) {
			if(stream == null) return null;
			RobotSkillList str = new RobotSkillList();
			if(!str.readPropertyStream_internal(stream));
			return str;
		}
		
		/*----- Write -----*/
		
		public int getBinarySize() {
			int size = baseSize;
			for(SkillData skillData : skills){
				size += skillData.getBinarySize();
			}
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
			
			count = skills.size();
			target.addToFile(count);
			for(SkillData skillData : skills){
				skillData.writeBinaryTo(target);
			}

			return (int)(target.getFileSize() - stPos);
		}
	
		public void writeXMLNode(Writer out, String indent) throws IOException {
			writeXMLNode(out, indent, null);
		}
		
		public void writeXMLNode(Writer out, String indent, String varName) throws IOException {
			if(out == null) return;
			if(indent == null) indent = "";
			
			count = skills.size();
			out.write(indent);
			out.write(String.format("<%s", xmlNodeName));
			if(varName != null){
				out.write(String.format(" VarName=\"%s\"", varName));
			}
			out.write(String.format(" %s=\"0x%08x\"", XMLKEY_COUNT, count));
			out.write(">\n");
			out.write(indent + "\t<List ");
			out.write(String.format(" VarName=\"%s\">\n", XMLKEY_SKILLS));
			for(SkillData skillData : skills){
				skillData.writeXMLNode(out, indent + "\t\t", null);
			}
			out.write(indent + "\t</List>\n");
			out.write(indent);
			out.write(String.format("</%s>\n", xmlNodeName));
		}
	
		public void addToPropertyStream(MaxisPropertyStream ps) {	
			if(ps == null) return;
			boolean byte_order = ps.getByteOrder();
			int verFieldSize = ps.getVersionFieldSize();
			count = skills.size();
			ps.addInt(count, PSID_COUNT);
			for(SkillData skillData : skills){
				ps.addChildStream(skillData.toPropertyStream(byte_order, verFieldSize), PSID_SKILLS + skillData.guid);
			}
		}
		
	}

	/*----- Read -----*/
	
	protected boolean readBinary_internal(BufferReference dat) {
		if(dat == null) return false;
		
		count = dat.nextInt();
		skills.ensureCapacity(count);
		for(int i = 0; i < count; i++){
			SkillData skillData = SkillData.readBinary(dat);
			if(skillData != null) skills.add(skillData);
		}
		mRobotLearnedSkills = RobotSkillList.readBinary(dat);
		mSkillModifiersKeys = new short[count];
		for(int i = 0; i < count; i++){
			mSkillModifiersKeys[i] = dat.nextShort();
		}
		mSkillModifiersValues = new float[count];
		for(int i = 0; i < count; i++){
			mSkillModifiersValues[i] = Float.intBitsToFloat(dat.nextInt());
		}

		return true;
	}
	
	protected boolean readXMLNode_internal(Element xml_element) {
		if(xml_element == null) return false;
		String nn = xml_element.getNodeName();
		if(nn == null) return false;
		if(!nn.equals(xmlNodeName)) return false;
		
		String aval = null;
		Element child = null;
		aval = xml_element.getAttribute(XMLKEY_COUNT);
		if(aval != null) count = StringUtils.parseUnsignedInt(aval);
		child = XMLReader.getFirstChildElementWithTagAndAttribute(xml_element, "List", "VarName", XMLKEY_SKILLS);
		if(child != null){
			skills.ensureCapacity(count);
			List<Element> gclist = XMLReader.getChildElementsWithTag(child, "SkillData");
			for(Element gc : gclist){
				skills.add(SkillData.readXMLNode(gc));
			}
		}
		child = XMLReader.getFirstChildElementWithTagAndAttribute(xml_element, "RobotSkillList", "VarName", XMLKEY_MROBOTLEARNEDSKILLS);
		if(child != null) mRobotLearnedSkills = RobotSkillList.readXMLNode(child);
		child = XMLReader.getFirstChildElementWithTagAndAttribute(xml_element, "Array", "VarName", XMLKEY_MSKILLMODIFIERSKEYS);
		if(child != null){
			mSkillModifiersKeys = new short[count];
			List<Element> gclist = XMLReader.getChildElementsWithTag(child, "ArrayMember");
			int i = 0;
			for(Element gc : gclist){
				aval = gc.getAttribute("Value");
				if(aval != null) mSkillModifiersKeys[i] = (short)StringUtils.parseUnsignedInt(aval);
				i++;
			}
		}
		child = XMLReader.getFirstChildElementWithTagAndAttribute(xml_element, "Array", "VarName", XMLKEY_MSKILLMODIFIERSVALUES);
		if(child != null){
			mSkillModifiersValues = new float[count];
			List<Element> gclist = XMLReader.getChildElementsWithTag(child, "ArrayMember");
			int i = 0;
			for(Element gc : gclist){
				aval = gc.getAttribute("Value");
				if(aval != null) mSkillModifiersValues[i] = (float)Double.parseDouble(aval);
				i++;
			}
		}

		return true;
	}
	
	protected boolean readPropertyStream_internal(MaxisPropertyStream stream) {
		if(stream == null) return false;
		
		count = stream.getFieldAsInt(PSID_COUNT);
		skills.ensureCapacity(count);
		
		Set<Integer> allPsid = stream.getAllFieldKeys();
		for(Integer psid : allPsid) {
			if(psid == PSID_COUNT) continue;
			SkillData skillData = SkillData.readPropertyStream(stream.getChildStream(psid));
			if(skillData != null) {
				if((psid - skillData.guid) == PSID_SKILLS) {
					skills.add(skillData);
				}
			}
		}

		mRobotLearnedSkills = RobotSkillList.readPropertyStream(stream.getChildStream(PSID_MROBOTLEARNEDSKILLS));
		mSkillModifiersKeys = stream.getFieldAsShortArray(PSID_MSKILLMODIFIERSKEYS);
		mSkillModifiersValues = stream.getFieldAsFloatArray(PSID_MSKILLMODIFIERSVALUES);

		return true;
	}
	
	public static SkillManager readBinary(BufferReference dat) {
		if(dat == null) return null;
		SkillManager str = new SkillManager();
		if(!str.readBinary_internal(dat)) return null;
		return str;
	}
	
	public static SkillManager readXMLNode(Element xml_element) {
		if(xml_element == null) return null;
		SkillManager str = new SkillManager();
		if(!str.readXMLNode_internal(xml_element)) return null;
		return str;
	}
	
	public static SkillManager readPropertyStream(BufferReference dat, boolean byteOrder, int verFieldSize) {
		if(dat == null) return null;
		MaxisPropertyStream stream = MaxisPropertyStream.openForRead(dat, byteOrder, verFieldSize);
		return readPropertyStream(stream);
	}
	
	public static SkillManager readPropertyStream(MaxisPropertyStream stream) {
		if(stream == null) return null;
		SkillManager str = new SkillManager();
		if(!str.readPropertyStream_internal(stream));
		return str;
	}
	
	/*----- Write -----*/
	
	public int getBinarySize() {
		int size = baseSize;
		for(SkillData skillData : skills){
			size += skillData.getBinarySize();
		}
		size += mRobotLearnedSkills.getBinarySize();
		size += (mSkillModifiersKeys.length * 2);
		size += (mSkillModifiersValues.length * 4);
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
		
		count = skills.size();
		target.addToFile(count);
		for(SkillData skillData : skills){
			skillData.writeBinaryTo(target);
		}
		mRobotLearnedSkills.writeBinaryTo(target);
		for(int i = 0; i < count; i++){
			target.addToFile(mSkillModifiersKeys[i]);
		}
		for(int i = 0; i < count; i++){
			target.addToFile(Float.floatToRawIntBits(mSkillModifiersValues[i]));
		}

		return (int)(target.getFileSize() - stPos);
	}

	public void writeXMLNode(Writer out, String indent) throws IOException {
		writeXMLNode(out, indent, null);
	}
	
	public void writeXMLNode(Writer out, String indent, String varName) throws IOException {
		if(out == null) return;
		if(indent == null) indent = "";
		
		count = skills.size();
		out.write(indent);
		out.write(String.format("<%s", xmlNodeName));
		if(varName != null){
			out.write(String.format(" VarName=\"%s\"", varName));
		}
		out.write(String.format(" %s=\"0x%08x\"", XMLKEY_COUNT, count));
		out.write(">\n");
		out.write(indent + "\t<List ");
		out.write(String.format(" VarName=\"%s\">\n", XMLKEY_SKILLS));
		for(SkillData skillData : skills){
			skillData.writeXMLNode(out, indent + "\t\t", null);
		}
		out.write(indent + "\t</List>\n");
		mRobotLearnedSkills.writeXMLNode(out, indent + "\t", XMLKEY_MROBOTLEARNEDSKILLS);
		out.write(indent + String.format("\t<Array VarName=\"%s\">\n", XMLKEY_MSKILLMODIFIERSKEYS));
		for(int i = 0; i < mSkillModifiersKeys.length; i++){
			out.write(indent + String.format("\t\t<ArrayMember Value=\"0x%04x\"/>\n", mSkillModifiersKeys[i]));
		}
		out.write(indent + "\t</Array>\n");
		out.write(indent + String.format("\t<Array VarName=\"%s\">\n", XMLKEY_MSKILLMODIFIERSVALUES));
		for(int i = 0; i < mSkillModifiersValues.length; i++){
			out.write(indent + String.format("\t\t<ArrayMember Value=\"%.3f\"/>\n", mSkillModifiersValues[i]));
		}
		out.write(indent + "\t</Array>\n");
		out.write(indent);
		out.write(String.format("</%s>\n", xmlNodeName));

	}

	public void addToPropertyStream(MaxisPropertyStream ps) {	
		if(ps == null) return;
		boolean byte_order = ps.getByteOrder();
		int verFieldSize = ps.getVersionFieldSize();
		
		count = skills.size();
		ps.addInt(count, PSID_COUNT);
		for(SkillData skillData : skills){
			ps.addChildStream(skillData.toPropertyStream(byte_order, verFieldSize), PSID_SKILLS + skillData.guid);
		}
		ps.addChildStream(mRobotLearnedSkills.toPropertyStream(byte_order, verFieldSize), PSID_MROBOTLEARNEDSKILLS);
		ps.addShortArray(mSkillModifiersKeys, PSID_MSKILLMODIFIERSKEYS);
		ps.addFloatArray(mSkillModifiersValues, PSID_MSKILLMODIFIERSVALUES);
	}
	
}