/*-----------------------------------------------------
 * Autogenerated Java class from XML definition.
 * Created Tue, 31 Dec 2024 23:04:11 -0600
 *-----------------------------------------------------*/

//Last check pass: 2025/01/01 19:57

package waffleoRai_Containers.maxis.ts3.savefmts.sim;

import java.io.IOException;
import java.io.Writer;

import org.w3c.dom.Element;

import waffleoRai_Containers.maxis.MaxisPropertyStream;
import waffleoRai_Containers.maxis.ts3.savefmts.TS3Saveable;
import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.StringUtils;

public class CelebrityManager extends TS3Saveable{
//TS3 Script: Sims3.Gameplay.CelebritySystem.CelebrityManager

	public static final int PSID_MLEVEL = 0x097AB22D;
	public static final int PSID_MPOINTS = 0x097AB22E;
	public static final int PSID_MBABYLEVEL = 0x097AB22F;
	public static final int PSID_MFALSELYACCUSEDCOUNT = 0x0A67D80C;
	public static final int PSID_MPUBLICLYDISGRACEDCOUNT = 0x0A67D81B;
	public static final int PSID_MDISCOUNTCOUNT = 0x097AB23B;
	public static final int PSID_MFREEBIESCOUNT = 0x097AB23F;
	public static final int PSID_MMONEYSAVED = 0x097AB23A;
	public static final int PSID_MBARSVISITEDCOUNT = 0x0A67D81C;
	public static final int PSID_MNUMBEROFTIMESPHOTOGRAPHED = 0x0A67D81D;
	public static final int PSID_MAUTOGRAPHSSIGNEDCOUNT = 0x0A67D81E;

	private static final String XMLKEY_MLEVEL = "Level";
	private static final String XMLKEY_MPOINTS = "Points";
	private static final String XMLKEY_MBABYLEVEL = "BabyLevel";
	private static final String XMLKEY_MFALSELYACCUSEDCOUNT = "FalselyAccusedCount";
	private static final String XMLKEY_MPUBLICLYDISGRACEDCOUNT = "PubliclyDisgracedCount";
	private static final String XMLKEY_MDISCOUNTCOUNT = "DiscountCount";
	private static final String XMLKEY_MFREEBIESCOUNT = "FreebiesCount";
	private static final String XMLKEY_MMONEYSAVED = "MoneySaved";
	private static final String XMLKEY_MBARSVISITEDCOUNT = "BarsVisitedCount";
	private static final String XMLKEY_MNUMBEROFTIMESPHOTOGRAPHED = "NumberOfTimesPhotographed";
	private static final String XMLKEY_MAUTOGRAPHSSIGNEDCOUNT = "AutographsSignedCount";

	public int mLevel;
	public int mPoints;
	public int mBabyLevel;
	public int mFalselyAccusedCount;
	public int mPubliclyDisgracedCount;
	public int mDiscountCount;
	public int mFreebiesCount;
	public int mMoneySaved;
	public int mBarsVisitedCount;
	public int mNumberOfTimesPhotographed;
	public int mAutographsSignedCount;

	public CelebrityManager() {
		xmlNodeName = "CelebrityManager";
		baseSize = 44;
	}
	
	/*----- Read -----*/
	
	protected boolean readBinary_internal(BufferReference dat) {
		if(dat == null) return false;
		
		mLevel = dat.nextInt();
		mPoints = dat.nextInt();
		mBabyLevel = dat.nextInt();
		mFalselyAccusedCount = dat.nextInt();
		mPubliclyDisgracedCount = dat.nextInt();
		mDiscountCount = dat.nextInt();
		mFreebiesCount = dat.nextInt();
		mMoneySaved = dat.nextInt();
		mBarsVisitedCount = dat.nextInt();
		mNumberOfTimesPhotographed = dat.nextInt();
		mAutographsSignedCount = dat.nextInt();

		return true;
	}
	
	protected boolean readXMLNode_internal(Element xml_element) {
		if(xml_element == null) return false;
		String nn = xml_element.getNodeName();
		if(nn == null) return false;
		if(!nn.equals(xmlNodeName)) return false;
		
		String aval = null;
		aval = xml_element.getAttribute(XMLKEY_MLEVEL);
		if(aval != null) mLevel = StringUtils.parseUnsignedInt(aval);
		aval = xml_element.getAttribute(XMLKEY_MPOINTS);
		if(aval != null) mPoints = StringUtils.parseUnsignedInt(aval);
		aval = xml_element.getAttribute(XMLKEY_MBABYLEVEL);
		if(aval != null) mBabyLevel = StringUtils.parseUnsignedInt(aval);
		aval = xml_element.getAttribute(XMLKEY_MFALSELYACCUSEDCOUNT);
		if(aval != null) mFalselyAccusedCount = StringUtils.parseUnsignedInt(aval);
		aval = xml_element.getAttribute(XMLKEY_MPUBLICLYDISGRACEDCOUNT);
		if(aval != null) mPubliclyDisgracedCount = StringUtils.parseUnsignedInt(aval);
		aval = xml_element.getAttribute(XMLKEY_MDISCOUNTCOUNT);
		if(aval != null) mDiscountCount = StringUtils.parseUnsignedInt(aval);
		aval = xml_element.getAttribute(XMLKEY_MFREEBIESCOUNT);
		if(aval != null) mFreebiesCount = StringUtils.parseUnsignedInt(aval);
		aval = xml_element.getAttribute(XMLKEY_MMONEYSAVED);
		if(aval != null) mMoneySaved = StringUtils.parseUnsignedInt(aval);
		aval = xml_element.getAttribute(XMLKEY_MBARSVISITEDCOUNT);
		if(aval != null) mBarsVisitedCount = StringUtils.parseUnsignedInt(aval);
		aval = xml_element.getAttribute(XMLKEY_MNUMBEROFTIMESPHOTOGRAPHED);
		if(aval != null) mNumberOfTimesPhotographed = StringUtils.parseUnsignedInt(aval);
		aval = xml_element.getAttribute(XMLKEY_MAUTOGRAPHSSIGNEDCOUNT);
		if(aval != null) mAutographsSignedCount = StringUtils.parseUnsignedInt(aval);

		return true;
	}
	
	protected boolean readPropertyStream_internal(MaxisPropertyStream stream) {
		if(stream == null) return false;
		
		mLevel = stream.getFieldAsInt(PSID_MLEVEL);
		mPoints = stream.getFieldAsInt(PSID_MPOINTS);
		mBabyLevel = stream.getFieldAsInt(PSID_MBABYLEVEL);
		mFalselyAccusedCount = stream.getFieldAsInt(PSID_MFALSELYACCUSEDCOUNT);
		mPubliclyDisgracedCount = stream.getFieldAsInt(PSID_MPUBLICLYDISGRACEDCOUNT);
		mDiscountCount = stream.getFieldAsInt(PSID_MDISCOUNTCOUNT);
		mFreebiesCount = stream.getFieldAsInt(PSID_MFREEBIESCOUNT);
		mMoneySaved = stream.getFieldAsInt(PSID_MMONEYSAVED);
		mBarsVisitedCount = stream.getFieldAsInt(PSID_MBARSVISITEDCOUNT);
		mNumberOfTimesPhotographed = stream.getFieldAsInt(PSID_MNUMBEROFTIMESPHOTOGRAPHED);
		mAutographsSignedCount = stream.getFieldAsInt(PSID_MAUTOGRAPHSSIGNEDCOUNT);

		return true;
	}
	
	public static CelebrityManager readBinary(BufferReference dat) {
		if(dat == null) return null;
		CelebrityManager str = new CelebrityManager();
		if(!str.readBinary_internal(dat)) return null;
		return str;
	}
	
	public static CelebrityManager readXMLNode(Element xml_element) {
		if(xml_element == null) return null;
		CelebrityManager str = new CelebrityManager();
		if(!str.readXMLNode_internal(xml_element)) return null;
		return str;
	}
	
	public static CelebrityManager readPropertyStream(BufferReference dat, boolean byteOrder, int verFieldSize) {
		if(dat == null) return null;
		MaxisPropertyStream stream = MaxisPropertyStream.openForRead(dat, byteOrder, verFieldSize);
		return readPropertyStream(stream);
	}
	
	public static CelebrityManager readPropertyStream(MaxisPropertyStream stream) {
		if(stream == null) return null;
		CelebrityManager str = new CelebrityManager();
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
		
		target.addToFile(mLevel);
		target.addToFile(mPoints);
		target.addToFile(mBabyLevel);
		target.addToFile(mFalselyAccusedCount);
		target.addToFile(mPubliclyDisgracedCount);
		target.addToFile(mDiscountCount);
		target.addToFile(mFreebiesCount);
		target.addToFile(mMoneySaved);
		target.addToFile(mBarsVisitedCount);
		target.addToFile(mNumberOfTimesPhotographed);
		target.addToFile(mAutographsSignedCount);

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
		out.write(String.format(" %s=\"0x%08x\"", XMLKEY_MLEVEL, mLevel));
		out.write(String.format(" %s=\"0x%08x\"", XMLKEY_MPOINTS, mPoints));
		out.write(String.format(" %s=\"0x%08x\"", XMLKEY_MBABYLEVEL, mBabyLevel));
		out.write(String.format(" %s=\"0x%08x\"", XMLKEY_MFALSELYACCUSEDCOUNT, mFalselyAccusedCount));
		out.write(String.format(" %s=\"0x%08x\"", XMLKEY_MPUBLICLYDISGRACEDCOUNT, mPubliclyDisgracedCount));
		out.write(String.format(" %s=\"0x%08x\"", XMLKEY_MDISCOUNTCOUNT, mDiscountCount));
		out.write(String.format(" %s=\"0x%08x\"", XMLKEY_MFREEBIESCOUNT, mFreebiesCount));
		out.write(String.format(" %s=\"0x%08x\"", XMLKEY_MMONEYSAVED, mMoneySaved));
		out.write(String.format(" %s=\"0x%08x\"", XMLKEY_MBARSVISITEDCOUNT, mBarsVisitedCount));
		out.write(String.format(" %s=\"0x%08x\"", XMLKEY_MNUMBEROFTIMESPHOTOGRAPHED, mNumberOfTimesPhotographed));
		out.write(String.format(" %s=\"0x%08x\"", XMLKEY_MAUTOGRAPHSSIGNEDCOUNT, mAutographsSignedCount));
		out.write("/>\n");

	}

	public void addToPropertyStream(MaxisPropertyStream ps) {	
		if(ps == null) return;
		ps.addInt(mLevel, PSID_MLEVEL);
		ps.addInt(mPoints, PSID_MPOINTS);
		ps.addInt(mBabyLevel, PSID_MBABYLEVEL);
		ps.addInt(mFalselyAccusedCount, PSID_MFALSELYACCUSEDCOUNT);
		ps.addInt(mPubliclyDisgracedCount, PSID_MPUBLICLYDISGRACEDCOUNT);
		ps.addInt(mDiscountCount, PSID_MDISCOUNTCOUNT);
		ps.addInt(mFreebiesCount, PSID_MFREEBIESCOUNT);
		ps.addInt(mMoneySaved, PSID_MMONEYSAVED);
		ps.addInt(mBarsVisitedCount, PSID_MBARSVISITEDCOUNT);
		ps.addInt(mNumberOfTimesPhotographed, PSID_MNUMBEROFTIMESPHOTOGRAPHED);
		ps.addInt(mAutographsSignedCount, PSID_MAUTOGRAPHSSIGNEDCOUNT);
	}
	
}
