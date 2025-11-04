/*-----------------------------------------------------
 * Created Sat, 18 Jan 2025 20:12:53 -0600
 *-----------------------------------------------------*/

package waffleoRai_Files.maxis.res.gfx;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;

import waffleoRai_Files.XMLReader;
import waffleoRai_Files.maxis.ts3enum.CASAgeGenderFlags;
import waffleoRai_Files.maxis.ts3enum.cas.CASPartType;
import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.StringUtils;

public class BlendGeometry{

	private static final String XMLKEY_MAGICNO = "MagicNo";
	private static final String XMLKEY_FMTVERSION = "FmtVersion";
	//private static final String XMLKEY_INFOSECTIONCOUNT = "InfoSectionCount";
	//private static final String XMLKEY_INFOENTRYCOUNT = "InfoEntryCount";
	//private static final String XMLKEY_VERTEXINDEXENTRYCOUNT = "VertexIndexEntryCount";
	//private static final String XMLKEY_VERTEXCOORDENTRYCOUNT = "VertexCoordEntryCount";
	private static final String XMLKEY_INFOHEADERSIZE = "InfoHeaderSize";
	private static final String XMLKEY_INFOENTRYSIZE = "InfoEntrySize";
	private static final String XMLKEY_INFOSECOFFSET = "InfoSecOffset";
	private static final String XMLKEY_VERTEXINDEXSECOFFSET = "VertexIndexSecOffset";
	private static final String XMLKEY_VERTEXCOORDSECOFFSET = "VertexCoordSecOffset";
	private static final String XMLKEY_INFOSECTIONS = "InfoSections";
	private static final String XMLKEY_VERTEXINDEXSEC = "VertexIndexSec";
	private static final String XMLKEY_VERTEXCOORDSEC = "VertexCoordSec";

	public byte[] magicNo;
	public int fmtVersion;
	public int infoSectionCount;
	public int infoEntryCount;
	public int vertexIndexEntryCount;
	public int vertexCoordEntryCount;
	public int infoHeaderSize;
	public int infoEntrySize;
	public int infoSecOffset;
	public int vertexIndexSecOffset;
	public int vertexCoordSecOffset;
	public ArrayList<InfoSection> infoSections = new ArrayList<InfoSection>();
	public short[] vertexIndexSec;
	public ArrayList<VertexCoordSet> vertexCoordSec = new ArrayList<VertexCoordSet>();

	private String xmlNodeName;
	private int baseSize;

	public BlendGeometry() {
		xmlNodeName = "BlendGeometry";
		baseSize = 41;
	}
	
	/*----- Inner Classes -----*/

	public static class InfoSection{

		private static final String XMLKEY_AGEGENDERFLAGS = "AgeGenderFlags";
		private static final String XMLKEY_FACEBODYREGION = "FaceBodyRegion";
		private static final String XMLKEY_LODDATA = "LodData";
		
		public int ageGenderFlags;
		public int faceBodyRegion;
		public ArrayList<LodDataEntry> lodData = new ArrayList<LodDataEntry>();

		private String xmlNodeName;
		private int baseSize;
		
		private int entryCount;
	
		public InfoSection(int entries) {
			xmlNodeName = "InfoSection";
			baseSize = 8;
			entryCount = entries;
		}
		
		public void padLodEntriesTo(int eCount) {
			while(lodData.size() < eCount) {
				lodData.add(new LodDataEntry());
			}
			entryCount = lodData.size();
		}
		
		/*----- Read -----*/
		
		protected boolean readBinary_internal(BufferReference dat) {
			if(dat == null) return false;
			
			ageGenderFlags = dat.nextInt();
			faceBodyRegion = dat.nextInt();
			lodData.ensureCapacity(entryCount);
			for(int i = 0; i < entryCount; i++){
				LodDataEntry lodEntry = LodDataEntry.readBinary(dat);
				if(lodEntry != null) lodData.add(lodEntry);
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
			aval = xml_element.getAttribute(XMLKEY_AGEGENDERFLAGS);
			if(aval != null) ageGenderFlags = CASAgeGenderFlags.valueFromString(aval);
			aval = xml_element.getAttribute(XMLKEY_FACEBODYREGION);
			if(aval != null) faceBodyRegion = CASPartType.valueFromString(aval);
			
			child = XMLReader.getFirstChildElementWithTagAndAttribute(xml_element, "List", "VarName", XMLKEY_LODDATA);
			if(child != null){
				List<Element> gclist = XMLReader.getChildElementsWithTag(child, "LodDataEntry");
				entryCount = gclist.size();
				if(entryCount > 0) {
					lodData.ensureCapacity(entryCount);
					for(Element gc : gclist){
						lodData.add(LodDataEntry.readXMLNode(gc));
					}	
				}
			}

			return true;
		}
		
		public static InfoSection readBinary(BufferReference dat, int entries) {
			if(dat == null) return null;
			InfoSection str = new InfoSection(entries);
			if(!str.readBinary_internal(dat)) return null;
			return str;
		}
		
		public static InfoSection readXMLNode(Element xml_element) {
			if(xml_element == null) return null;
			InfoSection str = new InfoSection(0);
			if(!str.readXMLNode_internal(xml_element)) return null;
			return str;
		}
		
		/*----- Write -----*/
		
		public int getBinarySize() {
			int size = baseSize;
			for(LodDataEntry lodData : lodData){
				size += lodData.getBinarySize();
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
			
			target.addToFile(ageGenderFlags);
			target.addToFile(faceBodyRegion);
			for(LodDataEntry lodData : lodData){
				lodData.writeBinaryTo(target);
			}

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
			out.write(String.format(" %s=\"%s\"", XMLKEY_AGEGENDERFLAGS, CASAgeGenderFlags.stringFromValue(ageGenderFlags)));
			out.write(String.format(" %s=\"%s\"", XMLKEY_FACEBODYREGION, CASPartType.stringFromValue(faceBodyRegion)));
			out.write(">\n");
			out.write(indent + "\t<List ");
			out.write(String.format(" VarName=\"%s\">\n", XMLKEY_LODDATA));
			for(LodDataEntry lodData : lodData){
				lodData.writeXMLNode(out, indent + "\t\t", null);
			}
			out.write(indent + "\t</List>\n");
			out.write(indent);
			out.write(String.format("</%s>\n", xmlNodeName));
		}
	
	}

	public static class LodDataEntry{

		private static final String XMLKEY_FIRSTVERTEXID = "FirstVertexId";
		private static final String XMLKEY_LODVERTEXCOUNT = "LodVertexCount";
		private static final String XMLKEY_COORDSETCOUNT = "CoordSetCount";

		public int firstVertexId = -1;
		public int lodVertexCount = 0;
		public int coordSetCount = 0;

		private String xmlNodeName;
		private int baseSize;
	
		public LodDataEntry() {
			xmlNodeName = "LodDataEntry";
			baseSize = 12;
		}
		
		/*----- Read -----*/
		
		protected boolean readBinary_internal(BufferReference dat) {
			if(dat == null) return false;
			
			firstVertexId = dat.nextInt();
			lodVertexCount = dat.nextInt();
			coordSetCount = dat.nextInt();

			return true;
		}
		
		protected boolean readXMLNode_internal(Element xml_element) {
			if(xml_element == null) return false;
			String nn = xml_element.getNodeName();
			if(nn == null) return false;
			if(!nn.equals(xmlNodeName)) return false;
			
			String aval = null;
			aval = xml_element.getAttribute(XMLKEY_FIRSTVERTEXID);
			if(aval != null) firstVertexId = StringUtils.parseUnsignedInt(aval);
			aval = xml_element.getAttribute(XMLKEY_LODVERTEXCOUNT);
			if(aval != null) lodVertexCount = StringUtils.parseUnsignedInt(aval);
			aval = xml_element.getAttribute(XMLKEY_COORDSETCOUNT);
			if(aval != null) coordSetCount = StringUtils.parseUnsignedInt(aval);

			return true;
		}
		
		public static LodDataEntry readBinary(BufferReference dat) {
			if(dat == null) return null;
			LodDataEntry str = new LodDataEntry();
			if(!str.readBinary_internal(dat)) return null;
			return str;
		}
		
		public static LodDataEntry readXMLNode(Element xml_element) {
			if(xml_element == null) return null;
			LodDataEntry str = new LodDataEntry();
			if(!str.readXMLNode_internal(xml_element)) return null;
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
			
			target.addToFile(firstVertexId);
			target.addToFile(lodVertexCount);
			target.addToFile(coordSetCount);

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
			out.write(String.format(" %s=\"0x%08x\"", XMLKEY_FIRSTVERTEXID, firstVertexId));
			out.write(String.format(" %s=\"0x%08x\"", XMLKEY_LODVERTEXCOUNT, lodVertexCount));
			out.write(String.format(" %s=\"0x%08x\"", XMLKEY_COORDSETCOUNT, coordSetCount));
			out.write("/>\n");

		}
	
	}

	public static class VertexCoordSet{

		private static final String XMLKEY_X = "XCoord";
		private static final String XMLKEY_Y = "YCoord";
		private static final String XMLKEY_Z = "ZCoord";

		public short x;
		public short y;
		public short z;

		private String xmlNodeName;
		private int baseSize;
	
		public VertexCoordSet() {
			xmlNodeName = "VertexCoordSet";
			baseSize = 6;
		}
		
		/*----- Read -----*/
		
		protected boolean readBinary_internal(BufferReference dat) {
			if(dat == null) return false;
			
			x = dat.nextShort();
			y = dat.nextShort();
			z = dat.nextShort();

			return true;
		}
		
		protected boolean readXMLNode_internal(Element xml_element) {
			if(xml_element == null) return false;
			String nn = xml_element.getNodeName();
			if(nn == null) return false;
			if(!nn.equals(xmlNodeName)) return false;
			
			String aval = null;
			aval = xml_element.getAttribute(XMLKEY_X);
			if(aval != null) x = (short)StringUtils.parseUnsignedInt(aval);
			aval = xml_element.getAttribute(XMLKEY_Y);
			if(aval != null) y = (short)StringUtils.parseUnsignedInt(aval);
			aval = xml_element.getAttribute(XMLKEY_Z);
			if(aval != null) z = (short)StringUtils.parseUnsignedInt(aval);

			return true;
		}
		
		public static VertexCoordSet readBinary(BufferReference dat) {
			if(dat == null) return null;
			VertexCoordSet str = new VertexCoordSet();
			if(!str.readBinary_internal(dat)) return null;
			return str;
		}
		
		public static VertexCoordSet readXMLNode(Element xml_element) {
			if(xml_element == null) return null;
			VertexCoordSet str = new VertexCoordSet();
			if(!str.readXMLNode_internal(xml_element)) return null;
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
			
			target.addToFile(x);
			target.addToFile(y);
			target.addToFile(z);

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
			out.write(String.format(" %s=\"0x%04x\"", XMLKEY_X, x));
			out.write(String.format(" %s=\"0x%04x\"", XMLKEY_Y, y));
			out.write(String.format(" %s=\"0x%04x\"", XMLKEY_Z, z));
			out.write("/>\n");

		}
	
	}
	
	/*----- Read -----*/
	
	protected boolean readBinary_internal(BufferReference dat) {
		if(dat == null) return false;
		
		magicNo = new byte[4];
		for(int i = 0; i < 4; i++){
			magicNo[i] = dat.nextByte();
		}
		fmtVersion = dat.nextInt();
		infoSectionCount = dat.nextInt();
		infoEntryCount = dat.nextInt();
		vertexIndexEntryCount = dat.nextInt();
		vertexCoordEntryCount = dat.nextInt();
		infoHeaderSize = dat.nextInt();
		infoEntrySize = dat.nextInt();
		infoSecOffset = dat.nextInt();
		vertexIndexSecOffset = dat.nextInt();
		vertexCoordSecOffset = dat.nextInt();
		infoSections.ensureCapacity(infoSectionCount);
		for(int i = 0; i < infoSectionCount; i++){
			InfoSection infoSection = InfoSection.readBinary(dat, infoEntryCount);
			if(infoSection != null) infoSections.add(infoSection);
		}
		if(vertexIndexEntryCount > 0) {
			vertexIndexSec = new short[vertexIndexEntryCount];
			for(int i = 0; i < vertexIndexEntryCount; i++){
				vertexIndexSec[i] = dat.nextShort();
			}
		}
		else vertexIndexSec = null;

		vertexCoordSec.ensureCapacity(vertexCoordEntryCount);
		for(int i = 0; i < vertexCoordEntryCount; i++){
			VertexCoordSet vertexCoord = VertexCoordSet.readBinary(dat);
			if(vertexCoord != null) vertexCoordSec.add(vertexCoord);
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
		child = XMLReader.getFirstChildElementWithTagAndAttribute(xml_element, "Array", "VarName", XMLKEY_MAGICNO);
		if(child != null){
			magicNo = new byte[4];
			List<Element> gclist = XMLReader.getChildElementsWithTag(child, "ArrayMember");
			int i = 0;
			for(Element gc : gclist){
				aval = gc.getAttribute("Value");
				if(aval != null) magicNo[i] = (byte)StringUtils.parseUnsignedInt(aval);
				i++;
			}
		}
		aval = xml_element.getAttribute(XMLKEY_FMTVERSION);
		if(aval != null) fmtVersion = StringUtils.parseUnsignedInt(aval);
		//aval = xml_element.getAttribute(XMLKEY_INFOSECTIONCOUNT);
		//if(aval != null) infoSectionCount = StringUtils.parseSignedInt(aval);
		//aval = xml_element.getAttribute(XMLKEY_INFOENTRYCOUNT);
		//if(aval != null) infoEntryCount = StringUtils.parseSignedInt(aval);
		//aval = xml_element.getAttribute(XMLKEY_VERTEXINDEXENTRYCOUNT);
		//if(aval != null) vertexIndexEntryCount = StringUtils.parseSignedInt(aval);
		//aval = xml_element.getAttribute(XMLKEY_VERTEXCOORDENTRYCOUNT);
		//if(aval != null) vertexCoordEntryCount = StringUtils.parseSignedInt(aval);
		aval = xml_element.getAttribute(XMLKEY_INFOHEADERSIZE);
		if(aval != null) infoHeaderSize = StringUtils.parseSignedInt(aval);
		aval = xml_element.getAttribute(XMLKEY_INFOENTRYSIZE);
		if(aval != null) infoEntrySize = StringUtils.parseSignedInt(aval);
		aval = xml_element.getAttribute(XMLKEY_INFOSECOFFSET);
		if(aval != null) infoSecOffset = StringUtils.parseUnsignedInt(aval);
		aval = xml_element.getAttribute(XMLKEY_VERTEXINDEXSECOFFSET);
		if(aval != null) vertexIndexSecOffset = StringUtils.parseUnsignedInt(aval);
		aval = xml_element.getAttribute(XMLKEY_VERTEXCOORDSECOFFSET);
		if(aval != null) vertexCoordSecOffset = StringUtils.parseUnsignedInt(aval);
		
		infoEntryCount = 0;
		child = XMLReader.getFirstChildElementWithTagAndAttribute(xml_element, "List", "VarName", XMLKEY_INFOSECTIONS);
		if(child != null){
			List<Element> gclist = XMLReader.getChildElementsWithTag(child, "InfoSection");
			infoSectionCount = gclist.size();
			if(infoSectionCount > 0) {
				infoSections.ensureCapacity(infoSectionCount);
				for(Element gc : gclist){
					InfoSection sec = InfoSection.readXMLNode(gc);
					int ecount = sec.lodData.size();
					if(ecount > infoEntryCount) infoEntryCount = ecount;
					infoSections.add(sec);
				}	
			}
		}
		
		child = XMLReader.getFirstChildElementWithTagAndAttribute(xml_element, "List", "VarName", XMLKEY_VERTEXINDEXSEC);
		if(child != null){
			List<Element> gclist = XMLReader.getChildElementsWithTag(child, "VertexFlags");
			vertexIndexEntryCount = gclist.size();
			if(vertexIndexEntryCount > 0) {
				int i = 0;
				vertexIndexSec = new short[vertexIndexEntryCount];
				for(Element gc : gclist){
					int val = 0;
					
					aval = gc.getAttribute("PosDataExists");
					if(aval != null) {
						boolean b = Boolean.parseBoolean(aval);
						if(b) val |= 0x1;
					}
					
					aval = gc.getAttribute("NormalDataExists");
					if(aval != null) {
						boolean b = Boolean.parseBoolean(aval);
						if(b) val |= 0x2;
					}
					
					aval = gc.getAttribute("PosDataOffset");
					if(aval != null) {
						int n = StringUtils.parseSignedInt(aval);
						val |= (n & 0x3fff) << 2;
					}
					
					vertexIndexSec[i] = (short)val;
					i++;
				}
			}
			else vertexIndexSec = null;
		}
		
		child = XMLReader.getFirstChildElementWithTagAndAttribute(xml_element, "List", "VarName", XMLKEY_VERTEXCOORDSEC);
		if(child != null){
			List<Element> gclist = XMLReader.getChildElementsWithTag(child, "VertexCoordSet");
			vertexCoordEntryCount = gclist.size();
			if(vertexCoordEntryCount > 0) {
				vertexCoordSec.ensureCapacity(vertexCoordEntryCount);
				for(Element gc : gclist){
					vertexCoordSec.add(VertexCoordSet.readXMLNode(gc));
				}	
			}
		}

		return true;
	}
	
	public static BlendGeometry readBinary(BufferReference dat) {
		if(dat == null) return null;
		BlendGeometry str = new BlendGeometry();
		if(!str.readBinary_internal(dat)) return null;
		return str;
	}
	
	public static BlendGeometry readXMLNode(Element xml_element) {
		if(xml_element == null) return null;
		BlendGeometry str = new BlendGeometry();
		if(!str.readXMLNode_internal(xml_element)) return null;
		return str;
	}
	
	/*----- Write -----*/
	
	public int getBinarySize() {
		int size = baseSize;
		size += magicNo.length;
		for(InfoSection infoSections : infoSections){
			size += infoSections.getBinarySize();
		}
		if(vertexIndexSec != null) size += vertexIndexSec.length << 1;
		for(VertexCoordSet vertexCoordSec : vertexCoordSec){
			size += vertexCoordSec.getBinarySize();
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
		
		if(vertexIndexSec != null) vertexIndexEntryCount = vertexIndexSec.length;
		else vertexIndexEntryCount = 0;
		infoSectionCount = infoSections.size();
		vertexCoordEntryCount = vertexCoordSec.size();
		
		//Determine info section entry count
		infoEntryCount = 0;
		for(InfoSection isec : infoSections) {
			int ecount = isec.lodData.size();
			if(ecount > infoEntryCount) infoEntryCount = ecount;
		}
		
		for(int i = 0; i < 4; i++){
			target.addToFile(magicNo[i]);
		}
		target.addToFile(fmtVersion);
		target.addToFile(infoSectionCount);
		target.addToFile(infoEntryCount);
		target.addToFile(vertexIndexEntryCount);
		target.addToFile(vertexCoordEntryCount);
		target.addToFile(infoHeaderSize);
		target.addToFile(infoEntrySize);
		target.addToFile(infoSecOffset);
		target.addToFile(vertexIndexSecOffset);
		target.addToFile(vertexCoordSecOffset);
		for(InfoSection infoSection : infoSections){
			infoSection.padLodEntriesTo(infoEntryCount);
			infoSection.writeBinaryTo(target);
		}
		for(short vertexPtrFlags : vertexIndexSec){
			target.addToFile(vertexPtrFlags);
		}
		for(VertexCoordSet vertexCoordSec : vertexCoordSec){
			vertexCoordSec.writeBinaryTo(target);
		}

		return (int)(target.getFileSize() - stPos);
	}

	public void writeXMLNode(Writer out, String indent) throws IOException {
		writeXMLNode(out, indent, null);
	}
	
	public void writeXMLNode(Writer out, String indent, String varName) throws IOException {
		if(out == null) return;
		if(indent == null) indent = "";
		
		if(vertexIndexSec != null) vertexIndexEntryCount = vertexIndexSec.length;
		else vertexIndexEntryCount = 0;
		infoSectionCount = infoSections.size();
		vertexCoordEntryCount = vertexCoordSec.size();
		
		//Determine info section entry count
		infoEntryCount = 0;
		for(InfoSection isec : infoSections) {
			int ecount = isec.lodData.size();
			if(ecount > infoEntryCount) infoEntryCount = ecount;
		}
		
		out.write(indent);
		out.write(String.format("<%s", xmlNodeName));
		if(varName != null){
			out.write(String.format(" VarName=\"%s\"", varName));
		}
		out.write(String.format(" %s=\"0x%08x\"", XMLKEY_FMTVERSION, fmtVersion));
		//out.write(String.format(" %s=\"%d\"", XMLKEY_INFOSECTIONCOUNT, infoSectionCount));
		//out.write(String.format(" %s=\"%d\"", XMLKEY_INFOENTRYCOUNT, infoEntryCount));
		//out.write(String.format(" %s=\"%d\"", XMLKEY_VERTEXINDEXENTRYCOUNT, vertexIndexEntryCount));
		//out.write(String.format(" %s=\"%d\"", XMLKEY_VERTEXCOORDENTRYCOUNT, vertexCoordEntryCount));
		out.write(String.format(" %s=\"%d\"", XMLKEY_INFOHEADERSIZE, infoHeaderSize));
		out.write(String.format(" %s=\"%d\"", XMLKEY_INFOENTRYSIZE, infoEntrySize));
		out.write(String.format(" %s=\"0x%08x\"", XMLKEY_INFOSECOFFSET, infoSecOffset));
		out.write(String.format(" %s=\"0x%08x\"", XMLKEY_VERTEXINDEXSECOFFSET, vertexIndexSecOffset));
		out.write(String.format(" %s=\"0x%08x\"", XMLKEY_VERTEXCOORDSECOFFSET, vertexCoordSecOffset));
		out.write(">\n");
		out.write(indent + String.format("\t<Array VarName=\"%s\">\n", XMLKEY_MAGICNO));
		for(int i = 0; i < 4; i++){
			out.write(indent + String.format("\t\t<ArrayMember Value=\"%c\"/>\n", magicNo[i]));
		}
		out.write(indent + "\t</Array>\n");
		out.write(indent + "\t<List ");
		out.write(String.format(" VarName=\"%s\">\n", XMLKEY_INFOSECTIONS));
		for(InfoSection infoSections : infoSections){
			infoSections.writeXMLNode(out, indent + "\t\t", null);
		}
		out.write(indent + "\t</List>\n");
		out.write(indent + "\t<List ");
		out.write(String.format(" VarName=\"%s\">\n", XMLKEY_VERTEXINDEXSEC));
		for(short vertexPtrFlags : vertexIndexSec){
			int flags = Short.toUnsignedInt(vertexPtrFlags);
			
			out.write(indent + "\t\t<VertexFlags");
			boolean b = (flags & 0x1) != 0;
			out.write(String.format(" PosDataExists=\"%b\"", b));
			b = (flags & 0x2) != 0;
			out.write(String.format(" NormalDataExists=\"%b\"", b));
			int n = (flags >>> 2) & 0x3fff;
			out.write(String.format(" PosDataOffset=\"%d\"", n));
			
			out.write("/>\n");
		}
		out.write(indent + "\t</List>\n");
		out.write(indent + "\t<List ");
		out.write(String.format(" VarName=\"%s\">\n", XMLKEY_VERTEXCOORDSEC));
		for(VertexCoordSet vertexCoordSec : vertexCoordSec){
			vertexCoordSec.writeXMLNode(out, indent + "\t\t", null);
		}
		out.write(indent + "\t</List>\n");
		out.write(indent);
		out.write(String.format("</%s>\n", xmlNodeName));

	}

}
