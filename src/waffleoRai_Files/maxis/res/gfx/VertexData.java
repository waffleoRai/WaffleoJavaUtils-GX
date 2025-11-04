package waffleoRai_Files.maxis.res.gfx;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

import org.w3c.dom.Element;

import waffleoRai_Files.XMLReader;
import waffleoRai_Files.maxis.ts3enum.gfx.MeshVertexDataType;
import waffleoRai_Files.maxis.ts3enum.gfx.MeshVertexDataTypeGroup;
import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.StringUtils;

public class VertexData {
	
	public static abstract class VertexDataPoint{
		public abstract int getDataType();
		public abstract int getSubType();
		public abstract int getElementCount();
		
		protected abstract boolean readBinary_internal(BufferReference dat);
		protected abstract boolean readXMLNode_internal(Element xml_element);
		
		public abstract int getBinarySize();
		public abstract int writeBinaryTo(FileBuffer target);
		public abstract void writeXMLNode(Writer out, String indent, String varName) throws IOException;
		
		public FileBuffer writeBinary(boolean byteOrder) {
			FileBuffer buff = new FileBuffer(getBinarySize(), byteOrder);
			writeBinaryTo(buff);
			return buff;
		}
		
		public void writeXMLNode(Writer out, String indent) throws IOException {
			writeXMLNode(out, indent, null);
		}
	}
	
	public static class VtxPosData extends VertexDataPoint{
		
		public static final String XML_NODE_NAME = "VtxPosData";
		public static final int BASE_SIZE = 12;

		private static final String XMLKEY_X = "XCoord";
		private static final String XMLKEY_Z = "ZCoord";
		private static final String XMLKEY_Y = "YCoord";

		public float x;
		public float z;
		public float y;
		
		/*----- Info -----*/
		
		public int getDataType(){return MeshVertexDataType.Position;}
		public int getSubType(){return MeshVertexDataTypeGroup.D_F32;}
		public int getElementCount(){return 3;}

		/*----- Read -----*/
		
		protected boolean readBinary_internal(BufferReference dat) {
			if(dat == null) return false;
			
			x = Float.intBitsToFloat(dat.nextInt());
			z = Float.intBitsToFloat(dat.nextInt());
			y = Float.intBitsToFloat(dat.nextInt());

			return true;
		}
		
		protected boolean readXMLNode_internal(Element xml_element) {
			if(xml_element == null) return false;
			String nn = xml_element.getNodeName();
			if(nn == null) return false;
			if(!nn.equals(XML_NODE_NAME)) return false;
			
			String aval = null;
			aval = xml_element.getAttribute(XMLKEY_X);
			if(aval != null) x = (float)Double.parseDouble(aval);
			aval = xml_element.getAttribute(XMLKEY_Z);
			if(aval != null) z = (float)Double.parseDouble(aval);
			aval = xml_element.getAttribute(XMLKEY_Y);
			if(aval != null) y = (float)Double.parseDouble(aval);

			return true;
		}
		
		public static VtxPosData readBinary(BufferReference dat) {
			if(dat == null) return null;
			VtxPosData str = new VtxPosData();
			if(!str.readBinary_internal(dat)) return null;
			return str;
		}
		
		public static VtxPosData readXMLNode(Element xml_element) {
			if(xml_element == null) return null;
			VtxPosData str = new VtxPosData();
			if(!str.readXMLNode_internal(xml_element)) return null;
			return str;
		}
		
		/*----- Write -----*/
		
		public int getBinarySize() {return BASE_SIZE;}
		
		public int writeBinaryTo(FileBuffer target) {
			if(target == null) return 0;
			long stPos = target.getFileSize();
			
			target.addToFile(Float.floatToRawIntBits(x));
			target.addToFile(Float.floatToRawIntBits(z));
			target.addToFile(Float.floatToRawIntBits(y));

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
			out.write(String.format(" %s=\"%.3f\"", XMLKEY_X, x));
			out.write(String.format(" %s=\"%.3f\"", XMLKEY_Z, z));
			out.write(String.format(" %s=\"%.3f\"", XMLKEY_Y, y));
			out.write("/>\n");

		}

	}

	public static class VtxNormalData extends VertexDataPoint{
		
		public static final String XML_NODE_NAME = "VtxNormalData";
		public static final int BASE_SIZE = 12;

		private static final String XMLKEY_X = "XCoord";
		private static final String XMLKEY_Z = "ZCoord";
		private static final String XMLKEY_Y = "YCoord";

		public float x;
		public float z;
		public float y;
		
		/*----- Info -----*/
		
		public int getDataType(){return MeshVertexDataType.Normal;}
		public int getSubType(){return MeshVertexDataTypeGroup.D_F32;}
		public int getElementCount(){return 3;}

		/*----- Read -----*/
		
		protected boolean readBinary_internal(BufferReference dat) {
			if(dat == null) return false;
			
			x = Float.intBitsToFloat(dat.nextInt());
			z = Float.intBitsToFloat(dat.nextInt());
			y = Float.intBitsToFloat(dat.nextInt());

			return true;
		}
		
		protected boolean readXMLNode_internal(Element xml_element) {
			if(xml_element == null) return false;
			String nn = xml_element.getNodeName();
			if(nn == null) return false;
			if(!nn.equals(XML_NODE_NAME)) return false;
			
			String aval = null;
			aval = xml_element.getAttribute(XMLKEY_X);
			if(aval != null) x = (float)Double.parseDouble(aval);
			aval = xml_element.getAttribute(XMLKEY_Z);
			if(aval != null) z = (float)Double.parseDouble(aval);
			aval = xml_element.getAttribute(XMLKEY_Y);
			if(aval != null) y = (float)Double.parseDouble(aval);

			return true;
		}
		
		public static VtxNormalData readBinary(BufferReference dat) {
			if(dat == null) return null;
			VtxNormalData str = new VtxNormalData();
			if(!str.readBinary_internal(dat)) return null;
			return str;
		}
		
		public static VtxNormalData readXMLNode(Element xml_element) {
			if(xml_element == null) return null;
			VtxNormalData str = new VtxNormalData();
			if(!str.readXMLNode_internal(xml_element)) return null;
			return str;
		}
		
		/*----- Write -----*/
		
		public int getBinarySize() {return BASE_SIZE;}
		
		public int writeBinaryTo(FileBuffer target) {
			if(target == null) return 0;
			long stPos = target.getFileSize();
			
			target.addToFile(Float.floatToRawIntBits(x));
			target.addToFile(Float.floatToRawIntBits(z));
			target.addToFile(Float.floatToRawIntBits(y));

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
			out.write(String.format(" %s=\"%.3f\"", XMLKEY_X, x));
			out.write(String.format(" %s=\"%.3f\"", XMLKEY_Z, z));
			out.write(String.format(" %s=\"%.3f\"", XMLKEY_Y, y));
			out.write("/>\n");
		}
	
	}

	public static class VtxUVData extends VertexDataPoint{
		
		public static final String XML_NODE_NAME = "VtxUVData";
		public static final int BASE_SIZE = 8;

		private static final String XMLKEY_U = "UCoord";
		private static final String XMLKEY_V = "VCoord";

		public float u;
		public float v;
		
		/*----- Info -----*/
		
		public int getDataType(){return MeshVertexDataType.UV;}
		public int getSubType(){return MeshVertexDataTypeGroup.D_F32;}
		public int getElementCount(){return 2;}

		/*----- Read -----*/
		
		protected boolean readBinary_internal(BufferReference dat) {
			if(dat == null) return false;
			
			u = Float.intBitsToFloat(dat.nextInt());
			v = Float.intBitsToFloat(dat.nextInt());

			return true;
		}
		
		protected boolean readXMLNode_internal(Element xml_element) {
			if(xml_element == null) return false;
			String nn = xml_element.getNodeName();
			if(nn == null) return false;
			if(!nn.equals(XML_NODE_NAME)) return false;
			
			String aval = null;
			aval = xml_element.getAttribute(XMLKEY_U);
			if(aval != null) u = (float)Double.parseDouble(aval);
			aval = xml_element.getAttribute(XMLKEY_V);
			if(aval != null) v = (float)Double.parseDouble(aval);

			return true;
		}
		
		public static VtxUVData readBinary(BufferReference dat) {
			if(dat == null) return null;
			VtxUVData str = new VtxUVData();
			if(!str.readBinary_internal(dat)) return null;
			return str;
		}
		
		public static VtxUVData readXMLNode(Element xml_element) {
			if(xml_element == null) return null;
			VtxUVData str = new VtxUVData();
			if(!str.readXMLNode_internal(xml_element)) return null;
			return str;
		}
		
		/*----- Write -----*/
		
		public int getBinarySize() {return BASE_SIZE;}
		
		public int writeBinaryTo(FileBuffer target) {
			if(target == null) return 0;
			long stPos = target.getFileSize();
			
			target.addToFile(Float.floatToRawIntBits(u));
			target.addToFile(Float.floatToRawIntBits(v));

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
			out.write(String.format(" %s=\"%.3f\"", XMLKEY_U, u));
			out.write(String.format(" %s=\"%.3f\"", XMLKEY_V, v));
			out.write("/>\n");
		}
	
	}
	
	public static class VtxBoneAssign extends VertexDataPoint{
		
		public static final String XML_NODE_NAME = "VtxBoneAssign";
		public static final int BASE_SIZE = 4;

		private static final String XMLKEY_BONEID = "BoneId";

		public int boneId;
		
		/*----- Info -----*/
		
		public int getDataType(){return MeshVertexDataType.BoneAssignment;}
		public int getSubType(){return MeshVertexDataTypeGroup.D_U32;}
		public int getElementCount(){return 1;}

		/*----- Read -----*/
		
		protected boolean readBinary_internal(BufferReference dat) {
			if(dat == null) return false;	
			boneId = dat.nextInt();
			return true;
		}
		
		protected boolean readXMLNode_internal(Element xml_element) {
			if(xml_element == null) return false;
			String nn = xml_element.getNodeName();
			if(nn == null) return false;
			if(!nn.equals(XML_NODE_NAME)) return false;
			
			String aval = null;
			aval = xml_element.getAttribute(XMLKEY_BONEID);
			if(aval != null) boneId = StringUtils.parseUnsignedInt(aval);

			return true;
		}
		
		public static VtxBoneAssign readBinary(BufferReference dat) {
			if(dat == null) return null;
			VtxBoneAssign str = new VtxBoneAssign();
			if(!str.readBinary_internal(dat)) return null;
			return str;
		}
		
		public static VtxBoneAssign readXMLNode(Element xml_element) {
			if(xml_element == null) return null;
			VtxBoneAssign str = new VtxBoneAssign();
			if(!str.readXMLNode_internal(xml_element)) return null;
			return str;
		}
		
		/*----- Write -----*/
		
		public int getBinarySize() {return BASE_SIZE;}
		
		public int writeBinaryTo(FileBuffer target) {
			if(target == null) return 0;
			long stPos = target.getFileSize();
			target.addToFile(boneId);
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
			out.write(String.format(" %s=\"%0x%08x\"", XML_NODE_NAME, boneId));
			out.write("/>\n");
		}
	
	}
	
	public static class VtxWeightData extends VertexDataPoint{
		
		public static final String XML_NODE_NAME = "VtxWeightData";
		public static final int BASE_SIZE = 16;

		private static final String XMLKEY_WEIGHTS = "Weights";

		public float[] weights;
		
		/*----- Info -----*/
		
		public int getDataType(){return MeshVertexDataType.Weights;}
		public int getSubType(){return MeshVertexDataTypeGroup.D_F32;}
		public int getElementCount(){return 4;}

		/*----- Read -----*/
		
		protected boolean readBinary_internal(BufferReference dat) {
			if(dat == null) return false;
			
			weights = new float[4];
			for(int i = 0; i < 4; i++){
				weights[i] = Float.intBitsToFloat(dat.nextInt());
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
			child = XMLReader.getFirstChildElementWithTagAndAttribute(xml_element, "Array", "VarName", XMLKEY_WEIGHTS);
			if(child != null){
				weights = new float[4];
				List<Element> gclist = XMLReader.getChildElementsWithTag(child, "ArrayMember");
				int i = 0;
				for(Element gc : gclist){
					aval = gc.getAttribute("Value");
					if(aval != null) weights[i] = (float)Double.parseDouble(aval);
					i++;
				}
			}

			return true;
		}
		
		public static VtxWeightData readBinary(BufferReference dat) {
			if(dat == null) return null;
			VtxWeightData str = new VtxWeightData();
			if(!str.readBinary_internal(dat)) return null;
			return str;
		}
		
		public static VtxWeightData readXMLNode(Element xml_element) {
			if(xml_element == null) return null;
			VtxWeightData str = new VtxWeightData();
			if(!str.readXMLNode_internal(xml_element)) return null;
			return str;
		}
		
		/*----- Write -----*/
		
		public int getBinarySize() {return BASE_SIZE;}
		
		public int writeBinaryTo(FileBuffer target) {
			if(target == null) return 0;
			long stPos = target.getFileSize();
			
			for(int i = 0; i < 4; i++){
				target.addToFile(Float.floatToRawIntBits(weights[i]));
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
			out.write(">\n");
			out.write(indent + String.format("\t<Array VarName=\"%s\">\n", XMLKEY_WEIGHTS));
			for(int i = 0; i < 4; i++){
				out.write(indent + String.format("\t\t<ArrayMember Value=\"%.3f\"/>\n", weights[i]));
			}
			out.write(indent + "\t</Array>\n");
			out.write(indent);
			out.write(String.format("</%s>\n", XML_NODE_NAME));
		}
	
	}
	
	public static class VtxTangentData extends VertexDataPoint{

		public static final String XML_NODE_NAME = "VtxTangentData";
		public static final int BASE_SIZE = 12;
		
		private static final String XMLKEY_X = "X";
		private static final String XMLKEY_Z = "Z";
		private static final String XMLKEY_Y = "Y";

		public float x;
		public float z;
		public float y;
		
		/*----- Info -----*/
		
		public int getDataType(){return MeshVertexDataType.TangentNormal;}
		public int getSubType(){return MeshVertexDataTypeGroup.D_F32;}
		public int getElementCount(){return 4;}
		
		/*----- Read -----*/
		
		protected boolean readBinary_internal(BufferReference dat) {
			if(dat == null) return false;
			
			x = Float.intBitsToFloat(dat.nextInt());
			z = Float.intBitsToFloat(dat.nextInt());
			y = Float.intBitsToFloat(dat.nextInt());

			return true;
		}
		
		protected boolean readXMLNode_internal(Element xml_element) {
			if(xml_element == null) return false;
			String nn = xml_element.getNodeName();
			if(nn == null) return false;
			if(!nn.equals(XML_NODE_NAME)) return false;
			
			String aval = null;
			aval = xml_element.getAttribute(XMLKEY_X);
			if(aval != null) x = (float)Double.parseDouble(aval);
			aval = xml_element.getAttribute(XMLKEY_Z);
			if(aval != null) z = (float)Double.parseDouble(aval);
			aval = xml_element.getAttribute(XMLKEY_Y);
			if(aval != null) y = (float)Double.parseDouble(aval);

			return true;
		}
		
		public static VtxTangentData readBinary(BufferReference dat) {
			if(dat == null) return null;
			VtxTangentData str = new VtxTangentData();
			if(!str.readBinary_internal(dat)) return null;
			return str;
		}
		
		public static VtxTangentData readXMLNode(Element xml_element) {
			if(xml_element == null) return null;
			VtxTangentData str = new VtxTangentData();
			if(!str.readXMLNode_internal(xml_element)) return null;
			return str;
		}
		
		/*----- Write -----*/
		
		public int getBinarySize() {return BASE_SIZE;}
		
		public int writeBinaryTo(FileBuffer target) {
			if(target == null) return 0;
			long stPos = target.getFileSize();
			
			target.addToFile(Float.floatToRawIntBits(x));
			target.addToFile(Float.floatToRawIntBits(z));
			target.addToFile(Float.floatToRawIntBits(y));

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
			out.write(String.format(" %s=\"%.3f\"", XMLKEY_X, x));
			out.write(String.format(" %s=\"%.3f\"", XMLKEY_Z, z));
			out.write(String.format(" %s=\"%.3f\"", XMLKEY_Y, y));
			out.write("/>\n");
		}
	
	}
	
	public static class VtxTagValData extends VertexDataPoint{
		
		public static final String XML_NODE_NAME = "VtxTagValData";
		public static final int BASE_SIZE = 4;

		private static final String XMLKEY_TAGVAL = "TagVal";

		public int tagVal;
		
		/*----- Info -----*/
		
		public int getDataType(){return MeshVertexDataType.TagVal;}
		public int getSubType(){return MeshVertexDataTypeGroup.D_Color;}
		public int getElementCount(){return 1;}
		
		/*----- Read -----*/
		
		protected boolean readBinary_internal(BufferReference dat) {
			if(dat == null) return false;
			tagVal = dat.nextInt();
			return true;
		}
		
		protected boolean readXMLNode_internal(Element xml_element) {
			if(xml_element == null) return false;
			String nn = xml_element.getNodeName();
			if(nn == null) return false;
			if(!nn.equals(XML_NODE_NAME)) return false;
			
			String aval = null;
			aval = xml_element.getAttribute(XMLKEY_TAGVAL);
			if(aval != null) tagVal = StringUtils.parseUnsignedInt(aval);

			return true;
		}
		
		public static VtxTagValData readBinary(BufferReference dat) {
			if(dat == null) return null;
			VtxTagValData str = new VtxTagValData();
			if(!str.readBinary_internal(dat)) return null;
			return str;
		}
		
		public static VtxTagValData readXMLNode(Element xml_element) {
			if(xml_element == null) return null;
			VtxTagValData str = new VtxTagValData();
			if(!str.readXMLNode_internal(xml_element)) return null;
			return str;
		}
		
		/*----- Write -----*/
		
		public int getBinarySize() {return BASE_SIZE;}
		
		public int writeBinaryTo(FileBuffer target) {
			if(target == null) return 0;
			long stPos = target.getFileSize();
			target.addToFile(tagVal);
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
			out.write(String.format(" %s=\"0x%08x\"", XMLKEY_TAGVAL, tagVal));
			out.write("/>\n");

		}
	
	}
	
	public static class VtxIdData extends VertexDataPoint{
		
		public static final String XML_NODE_NAME = "VtxIdData";
		public static final int BASE_SIZE = 4;

		private static final String XMLKEY_VERTEXIDVALUE = "VertexIdValue";

		public int vertexIdValue;
		
		/*----- Info -----*/
		
		public int getDataType(){return MeshVertexDataType.VertexId;}
		public int getSubType(){return MeshVertexDataTypeGroup.D_U32;}
		public int getElementCount(){return 1;}
		
		/*----- Read -----*/
		
		protected boolean readBinary_internal(BufferReference dat) {
			if(dat == null) return false;
			vertexIdValue = dat.nextInt();
			return true;
		}
		
		protected boolean readXMLNode_internal(Element xml_element) {
			if(xml_element == null) return false;
			String nn = xml_element.getNodeName();
			if(nn == null) return false;
			if(!nn.equals(XML_NODE_NAME)) return false;
			
			String aval = null;
			aval = xml_element.getAttribute(XMLKEY_VERTEXIDVALUE);
			if(aval != null) vertexIdValue = StringUtils.parseUnsignedInt(aval);

			return true;
		}
		
		public static VtxIdData readBinary(BufferReference dat) {
			if(dat == null) return null;
			VtxIdData str = new VtxIdData();
			if(!str.readBinary_internal(dat)) return null;
			return str;
		}
		
		public static VtxIdData readXMLNode(Element xml_element) {
			if(xml_element == null) return null;
			VtxIdData str = new VtxIdData();
			if(!str.readXMLNode_internal(xml_element)) return null;
			return str;
		}
		
		/*----- Write -----*/
		
		public int getBinarySize() {return BASE_SIZE;}
		
		public int writeBinaryTo(FileBuffer target) {
			if(target == null) return 0;
			long stPos = target.getFileSize();
			target.addToFile(vertexIdValue);
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
			out.write(String.format(" %s=\"0x%08x\"", XMLKEY_VERTEXIDVALUE, vertexIdValue));
			out.write("/>\n");
		}
	
	}
	
	public static VertexDataPoint readVertexDataBlock(BufferReference ref, int type) {
		if(ref == null) return null;
		switch(type) {
		case MeshVertexDataType.Position:
			return VtxPosData.readBinary(ref);
		case MeshVertexDataType.Normal:
			return VtxNormalData.readBinary(ref);
		case MeshVertexDataType.BoneAssignment:
			return VtxBoneAssign.readBinary(ref);
		case MeshVertexDataType.TagVal:
			return VtxTagValData.readBinary(ref);
		case MeshVertexDataType.TangentNormal:
			return VtxTangentData.readBinary(ref);
		case MeshVertexDataType.UV:
			return VtxUVData.readBinary(ref);
		case MeshVertexDataType.VertexId:
			return VtxIdData.readBinary(ref);
		case MeshVertexDataType.Weights:
			return VtxWeightData.readBinary(ref);
		}
		return null;
	}
	
	public static VertexDataPoint readXMLNode(Element xml_element) {
		if(xml_element == null) return null;
		String nn = xml_element.getNodeName();
		if(nn.equals(VtxPosData.XML_NODE_NAME)) {
			return VtxPosData.readXMLNode(xml_element);
		}
		else if(nn.equals(VtxNormalData.XML_NODE_NAME)) {
			return VtxNormalData.readXMLNode(xml_element);
		}
		else if(nn.equals(VtxUVData.XML_NODE_NAME)) {
			return VtxUVData.readXMLNode(xml_element);
		}
		else if(nn.equals(VtxBoneAssign.XML_NODE_NAME)) {
			return VtxBoneAssign.readXMLNode(xml_element);
		}
		else if(nn.equals(VtxWeightData.XML_NODE_NAME)) {
			return VtxWeightData.readXMLNode(xml_element);
		}
		else if(nn.equals(VtxTangentData.XML_NODE_NAME)) {
			return VtxTangentData.readXMLNode(xml_element);
		}
		else if(nn.equals(VtxTagValData.XML_NODE_NAME)) {
			return VtxTagValData.readXMLNode(xml_element);
		}
		else if(nn.equals(VtxIdData.XML_NODE_NAME)) {
			return VtxIdData.readXMLNode(xml_element);
		}
		
		return null;
	}
	
}
