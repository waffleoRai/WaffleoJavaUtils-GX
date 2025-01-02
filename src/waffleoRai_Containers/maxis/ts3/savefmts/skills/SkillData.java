/*-----------------------------------------------------
 * Autogenerated Java class from XML definition.
 * Created Wed, 1 Jan 2025 13:14:46 -0600
 *-----------------------------------------------------*/

//Last check pass: 2025/01/01 23:13

package waffleoRai_Containers.maxis.ts3.savefmts.skills;

import java.io.IOException;
import java.io.Writer;

import org.w3c.dom.Element;

import waffleoRai_Containers.maxis.MaxisPropertyStream;
import waffleoRai_Containers.maxis.MaxisTypes;
import waffleoRai_Containers.maxis.ts3.savefmts.TS3Saveable;
import waffleoRai_Files.XMLReader;
import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.StringUtils;

public class SkillData extends TS3Saveable{
//TS3 Script: Sims3.Gameplay.Skills.Skill

	public static final int PSID_TYPENAME = 0x94061B0D;
	public static final int PSID_GUID = 0x34DCF303;

	private static final String XMLKEY_TYPENAME = "TypeName";
	private static final String XMLKEY_GUID = "Guid";
	private static final String XMLKEY_SKILLINFO = "SkillInfo";

	public String typeName;
	public int guid;
	public Skill skillInfo;

	public SkillData() {
		xmlNodeName = "SkillData";
		baseSize = 8;
	}
	
	/*----- Read -----*/
	
	protected boolean readBinary_internal(BufferReference dat) {
		if(dat == null) return false;
		
		typeName = MaxisTypes.readMaxisString(dat);
		guid = dat.nextInt();
		skillInfo = Skill.readBinary(dat);

		return true;
	}
	
	protected boolean readXMLNode_internal(Element xml_element) {
		if(xml_element == null) return false;
		String nn = xml_element.getNodeName();
		if(nn == null) return false;
		if(!nn.equals(xmlNodeName)) return false;
		
		String aval = null;
		Element child = null;
		aval = xml_element.getAttribute(XMLKEY_TYPENAME);
		if(aval != null) typeName = aval;
		aval = xml_element.getAttribute(XMLKEY_GUID);
		if(aval != null) guid = StringUtils.parseUnsignedInt(aval);
		child = XMLReader.getFirstChildElementWithTagAndAttribute(xml_element, "Skill", "VarName", XMLKEY_SKILLINFO);
		if(child != null) skillInfo = Skill.readXMLNode(child);

		return true;
	}
	
	protected boolean readPropertyStream_internal(MaxisPropertyStream stream) {
		if(stream == null) return false;
		typeName = stream.getFieldAsString(PSID_TYPENAME);
		guid = stream.getFieldAsInt(PSID_GUID);
		skillInfo = Skill.readPropertyStream(stream);
		return true;
	}
	
	public static SkillData readBinary(BufferReference dat) {
		if(dat == null) return null;
		SkillData str = new SkillData();
		if(!str.readBinary_internal(dat)) return null;
		return str;
	}
	
	public static SkillData readXMLNode(Element xml_element) {
		if(xml_element == null) return null;
		SkillData str = new SkillData();
		if(!str.readXMLNode_internal(xml_element)) return null;
		return str;
	}
	
	public static SkillData readPropertyStream(BufferReference dat, boolean byteOrder, int verFieldSize) {
		if(dat == null) return null;
		MaxisPropertyStream stream = MaxisPropertyStream.openForRead(dat, byteOrder, verFieldSize);
		return readPropertyStream(stream);
	}
	
	public static SkillData readPropertyStream(MaxisPropertyStream stream) {
		if(stream == null) return null;
		SkillData str = new SkillData();
		if(!str.readPropertyStream_internal(stream));
		return str;
	}
	
	/*----- Write -----*/
	
	public int getBinarySize() {
		int size = baseSize;
		size += (typeName.length() << 1);
		size += skillInfo.getBinarySize();
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
		
		MaxisTypes.serializeMaxisStringTo( typeName, target);
		target.addToFile(guid);
		skillInfo.writeBinaryTo(target);

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
		out.write(String.format(" %s=\"%s\"", XMLKEY_TYPENAME, typeName));
		out.write(String.format(" %s=\"0x%08x\"", XMLKEY_GUID, guid));
		out.write(">\n");
		skillInfo.writeXMLNode(out, indent + "\t", XMLKEY_SKILLINFO);
		out.write(indent);
		out.write(String.format("</%s>\n", xmlNodeName));

	}

	public void addToPropertyStream(MaxisPropertyStream ps) {	
		if(ps == null) return;
		ps.addString(typeName, PSID_TYPENAME);
		ps.addInt(guid, PSID_GUID);
		skillInfo.addToPropertyStream(ps);
	}
	
}
