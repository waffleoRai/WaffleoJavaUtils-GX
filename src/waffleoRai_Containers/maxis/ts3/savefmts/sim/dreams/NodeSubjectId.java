package waffleoRai_Containers.maxis.ts3.savefmts.sim.dreams;

import java.io.IOException;
import java.io.Writer;

import org.w3c.dom.Element;

import waffleoRai_Containers.maxis.MaxisPropertyStream;
import waffleoRai_Containers.maxis.MaxisTypes;
import waffleoRai_Files.XMLReader;
import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.StringUtils;

public class NodeSubjectId{
	
	public static final String XML_NODE_NAME = "NodeSubjectId";
	
	public static final int PSID_ENUMVAL = 0x087EB6E5;
	public static final int PSID_KEYVAL = 0x087EB95F;
	public static final int PSID_OBJ = 0x087EB966;

	private static final String XMLKEY_ENUMVAL = "EnumValue";
	private static final String XMLKEY_KEYVAL = "KeyValue";
	private static final String XMLKEY_OBJ = "ObjectId";
	private static final String XMLKEY_SIMDESC = "SimDesc";
	private static final String XMLKEY_IDTYPE = "IdType";
	
	private int dreamSubjectType = DreamInputSubjectType.IDGROUP_NONE;
	
	private long mEnumValue;
	private String mKeyValue;
	private long mObjectId;
	private NodeSubjectSimDesc mSimDescription;

	protected boolean readBinary_internal(BufferReference dat) {
		switch(dreamSubjectType) {
		case DreamInputSubjectType.IDGROUP_ENUM:
			mEnumValue = dat.nextLong();
			break;
		case DreamInputSubjectType.IDGROUP_STRKEY:
			mKeyValue = MaxisTypes.readMaxisString(dat);
			break;
		case DreamInputSubjectType.IDGROUP_OBJID:
			mObjectId = dat.nextLong();
			break;
		case DreamInputSubjectType.IDGROUP_SIMID:
			mSimDescription = NodeSubjectSimDesc.readBinary(dat);
			break;
		default:
			return false;
		}
		
		return true;
	}

	protected boolean readXMLNode_internal(Element xml_element) {
		if(xml_element == null) return false;
		String nn = xml_element.getNodeName();
		if(nn == null) return false;
		if(!nn.equals(XML_NODE_NAME)) return false;
		
		String aval = null;
		Element child = null;
		aval = xml_element.getAttribute(XMLKEY_IDTYPE);
		if(aval != null) {
			if(aval.equals("Enum")) {
				aval = xml_element.getAttribute(XMLKEY_ENUMVAL);
				if(aval == null) return false;
				mEnumValue = StringUtils.parseUnsignedLong(aval);
				dreamSubjectType = DreamInputSubjectType.IDGROUP_ENUM;
			}
			else if(aval.equals("StringKey")) {
				aval = xml_element.getAttribute(XMLKEY_KEYVAL);
				if(aval == null) return false;
				mKeyValue = aval;
				dreamSubjectType = DreamInputSubjectType.IDGROUP_STRKEY;
			}
			else if(aval.equals("ObjectId")) {
				aval = xml_element.getAttribute(XMLKEY_OBJ);
				if(aval == null) return false;
				mObjectId = StringUtils.parseUnsignedLong(aval);
				dreamSubjectType = DreamInputSubjectType.IDGROUP_OBJID;
			}
			else if(aval.equals("SimId")) {
				child = XMLReader.getFirstChildElementWithTagAndAttribute(xml_element, "NodeSubjectSimDesc", "VarName", XMLKEY_SIMDESC);
				if(child == null) return false;
				mSimDescription = NodeSubjectSimDesc.readXMLNode(child);
				dreamSubjectType = DreamInputSubjectType.IDGROUP_SIMID;
			}
		}
		else return false;
		
		return true;
	}

	protected boolean readPropertyStream_internal(MaxisPropertyStream stream) {
		switch(dreamSubjectType) {
		case DreamInputSubjectType.IDGROUP_ENUM:
			mEnumValue = stream.getFieldAsLong(PSID_ENUMVAL);
			break;
		case DreamInputSubjectType.IDGROUP_STRKEY:
			mKeyValue = stream.getFieldAsString(PSID_KEYVAL);
			break;
		case DreamInputSubjectType.IDGROUP_OBJID:
			mObjectId = stream.getFieldAsLong(PSID_OBJ);
			break;
		case DreamInputSubjectType.IDGROUP_SIMID:
			mSimDescription = NodeSubjectSimDesc.readPropertyStream(stream);
			break;
		default:
			return false;
		}
		
		return true;
	}
	
	public static NodeSubjectId readBinary(BufferReference dat, int type) {
		NodeSubjectId str = new NodeSubjectId();
		str.dreamSubjectType = type;
		if(!str.readBinary_internal(dat)) return null;
		return str;
	}
	
	public static NodeSubjectId readXMLNode(Element xml_element) {
		if(xml_element == null) return null;
		NodeSubjectId str = new NodeSubjectId();
		if(!str.readXMLNode_internal(xml_element)) return null;
		return str;
	}
	
	public static NodeSubjectId readPropertyStream(MaxisPropertyStream stream, int type){
		NodeSubjectId str = new NodeSubjectId();
		str.dreamSubjectType = type;
		if(!str.readPropertyStream_internal(stream)) return null;
		return str;
	}

	public FileBuffer writeBinary(boolean byteOrder) {
		FileBuffer buff = new FileBuffer(getBinarySize(), byteOrder);
		writeBinaryTo(buff);
		return buff;
	}
	
	public int writeBinaryTo(FileBuffer target) {
		if(target == null) return 0;
		long stPos = target.getFileSize();
		switch(dreamSubjectType) {
		case DreamInputSubjectType.IDGROUP_ENUM:
			target.addToFile(mEnumValue);
			break;
		case DreamInputSubjectType.IDGROUP_STRKEY:
			MaxisTypes.serializeMaxisStringTo(mKeyValue, target);
			break;
		case DreamInputSubjectType.IDGROUP_OBJID:
			target.addToFile(mObjectId);
			break;
		case DreamInputSubjectType.IDGROUP_SIMID:
			if(mSimDescription != null) {
				mSimDescription.writeBinaryTo(target);
			}
			break;
		}
		return (int)(target.getFileSize() - stPos);
	}

	public void writeXMLNode(Writer out, String indent, String varName) throws IOException {
		if(out == null) return;
		if(indent == null) indent = "";
		
		out.write(indent);
		out.write(String.format("<%s", XML_NODE_NAME));
		if(varName != null){
			out.write(String.format(" VarName=\"%s\"", varName));
		}
		
		switch(dreamSubjectType) {
		case DreamInputSubjectType.IDGROUP_ENUM:
			out.write(String.format(" %s=\"Enum\"", XMLKEY_IDTYPE));
			out.write(String.format(" %s=\"0x%016x\"", XMLKEY_ENUMVAL, mEnumValue));
			break;
		case DreamInputSubjectType.IDGROUP_STRKEY:
			out.write(String.format(" %s=\"StringKey\"", XMLKEY_IDTYPE));
			out.write(String.format(" %s=\"%s\"", PSID_KEYVAL, mKeyValue));
			break;
		case DreamInputSubjectType.IDGROUP_OBJID:
			out.write(String.format(" %s=\"ObjectId\"", XMLKEY_IDTYPE));
			out.write(String.format(" %s=\"0x%016x\"", XMLKEY_OBJ, mObjectId));
			break;
		case DreamInputSubjectType.IDGROUP_SIMID:
			out.write(String.format(" %s=\"SimId\"", XMLKEY_IDTYPE));
			out.write("/>\n");
			if(mSimDescription != null) {
				mSimDescription.writeXMLNode(out, indent + "\t", XMLKEY_SIMDESC);
			}
			out.write(indent);
			out.write(String.format("</%s>\n", XML_NODE_NAME));
			break;
		}
	}
	
	public void writeXMLNode(Writer out, String indent) throws IOException {
		writeXMLNode(out, indent, null);
	}

	public int getBinarySize() {
		switch(dreamSubjectType) {
		case DreamInputSubjectType.IDGROUP_ENUM: return 8;
		case DreamInputSubjectType.IDGROUP_STRKEY:
			if(mKeyValue == null) return 4;
			return 4 + (mKeyValue.length() << 1);
		case DreamInputSubjectType.IDGROUP_OBJID: return 8;
		case DreamInputSubjectType.IDGROUP_SIMID:
			if(mSimDescription == null) return 0;
			return mSimDescription.getBinarySize();
		default:
			return 0;
		}
	}
	
	public MaxisPropertyStream toPropertyStream(boolean byte_order, int verFieldSize) {
		MaxisPropertyStream ps = MaxisPropertyStream.openForWrite(byte_order, verFieldSize);
		addToPropertyStream(ps);
		return ps;
	}
	
	public void addToPropertyStream(MaxisPropertyStream ps) {	
		if(ps == null) return;
		switch(dreamSubjectType) {
		case DreamInputSubjectType.IDGROUP_ENUM:
			ps.addLong(mEnumValue, PSID_ENUMVAL);
			break;
		case DreamInputSubjectType.IDGROUP_STRKEY:
			ps.addString(mKeyValue, PSID_KEYVAL);
			break;
		case DreamInputSubjectType.IDGROUP_OBJID:
			ps.addLong(mObjectId, PSID_OBJ);
			break;
		case DreamInputSubjectType.IDGROUP_SIMID:
			if(mSimDescription != null) mSimDescription.addToPropertyStream(ps);
			break;
		}
	}
	
}
