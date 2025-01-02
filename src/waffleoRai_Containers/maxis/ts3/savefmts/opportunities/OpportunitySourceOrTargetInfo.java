/*-----------------------------------------------------
 * Autogenerated Java class from XML definition.
 * Created Wed, 1 Jan 2025 12:53:27 -0600
 *-----------------------------------------------------*/

//Last check pass: 2025/01/01 19:14

package waffleoRai_Containers.maxis.ts3.savefmts.opportunities;

import java.io.IOException;
import java.io.Writer;

import org.w3c.dom.Element;

import waffleoRai_Containers.maxis.MaxisPropertyStream;
import waffleoRai_Containers.maxis.ts3.savefmts.TS3Saveable;
import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.StringUtils;

public class OpportunitySourceOrTargetInfo extends TS3Saveable{
//TS3 Script: Sims3.Gameplay.Opportunities.Opportunity

	public static final int PSID_WORLDID = 0x08F71780;
	public static final int PSID_OBJECTID = 0x08F71791;

	private static final String XMLKEY_WORLDID = "WorldId";
	private static final String XMLKEY_OBJECTID = "ObjectId";

	public int worldId;
	public long objectId;

	public OpportunitySourceOrTargetInfo() {
		xmlNodeName = "OpportunitySourceOrTargetInfo";
		baseSize = 12;
	}
	
	/*----- Read -----*/
	
	protected boolean readBinary_internal(BufferReference dat) {
		if(dat == null) return false;
		
		worldId = dat.nextInt();
		objectId = dat.nextLong();

		return true;
	}
	
	protected boolean readXMLNode_internal(Element xml_element) {
		if(xml_element == null) return false;
		String nn = xml_element.getNodeName();
		if(nn == null) return false;
		if(!nn.equals(xmlNodeName)) return false;
		
		String aval = null;
		aval = xml_element.getAttribute(XMLKEY_WORLDID);
		if(aval != null) worldId = StringUtils.parseSignedInt(aval);
		aval = xml_element.getAttribute(XMLKEY_OBJECTID);
		if(aval != null) objectId = StringUtils.parseUnsignedLong(aval);

		return true;
	}
	
	protected boolean readPropertyStream_internal(MaxisPropertyStream stream) {
		if(stream == null) return false;
		
		worldId = stream.getFieldAsInt(PSID_WORLDID);
		objectId = stream.getFieldAsLong(PSID_OBJECTID);

		return true;
	}
	
	public static OpportunitySourceOrTargetInfo readBinary(BufferReference dat) {
		if(dat == null) return null;
		OpportunitySourceOrTargetInfo str = new OpportunitySourceOrTargetInfo();
		if(!str.readBinary_internal(dat)) return null;
		return str;
	}
	
	public static OpportunitySourceOrTargetInfo readXMLNode(Element xml_element) {
		if(xml_element == null) return null;
		OpportunitySourceOrTargetInfo str = new OpportunitySourceOrTargetInfo();
		if(!str.readXMLNode_internal(xml_element)) return null;
		return str;
	}
	
	public static OpportunitySourceOrTargetInfo readPropertyStream(BufferReference dat, boolean byteOrder, int verFieldSize) {
		if(dat == null) return null;
		MaxisPropertyStream stream = MaxisPropertyStream.openForRead(dat, byteOrder, verFieldSize);
		return readPropertyStream(stream);
	}
	
	public static OpportunitySourceOrTargetInfo readPropertyStream(MaxisPropertyStream stream) {
		if(stream == null) return null;
		OpportunitySourceOrTargetInfo str = new OpportunitySourceOrTargetInfo();
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
		
		target.addToFile(worldId);
		target.addToFile(objectId);

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
		out.write(String.format(" %s=\"%d\"", XMLKEY_WORLDID, worldId));
		out.write(String.format(" %s=\"0x%016x\"", XMLKEY_OBJECTID, objectId));
		out.write("/>\n");

	}

	public void addToPropertyStream(MaxisPropertyStream ps) {	
		if(ps == null) return;
		ps.addInt(worldId, PSID_WORLDID);
		ps.addLong(objectId, PSID_OBJECTID);
	}
	
}
