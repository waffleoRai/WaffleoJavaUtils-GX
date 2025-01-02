/*-----------------------------------------------------
 * Autogenerated Java class from XML definition.
 * Created Wed, 1 Jan 2025 13:05:47 -0600
 *-----------------------------------------------------*/

//Last check pass: 2025/01/01 19:55

package waffleoRai_Containers.maxis.ts3.savefmts.pets;

import java.io.IOException;
import java.io.Writer;

import org.w3c.dom.Element;

import waffleoRai_Containers.maxis.MaxisPropertyStream;
import waffleoRai_Containers.maxis.ts3.savefmts.TS3Saveable;
import waffleoRai_Files.XMLReader;
import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.FileBuffer;

public class PetManager extends TS3Saveable{
//TS3 Script: Sims3.Gameplay.ActorSystems.PetManager

	private static final String XMLKEY_MPETREINFORCEMENTMANAGER = "PetReinforcementManager";

	public PetReinforcementManager mPetReinforcementManager;

	public PetManager() {
		xmlNodeName = "PetManager";
		baseSize = 0;
	}
	
	/*----- Read -----*/
	
	protected boolean readBinary_internal(BufferReference dat) {
		if(dat == null) return false;
		mPetReinforcementManager = PetReinforcementManager.readBinary(dat);
		return true;
	}
	
	protected boolean readXMLNode_internal(Element xml_element) {
		if(xml_element == null) return false;
		String nn = xml_element.getNodeName();
		if(nn == null) return false;
		if(!nn.equals(xmlNodeName)) return false;
		
		Element child = null;
		child = XMLReader.getFirstChildElementWithTagAndAttribute(xml_element, "PetReinforcementManager", "VarName", XMLKEY_MPETREINFORCEMENTMANAGER);
		if(child != null) mPetReinforcementManager = PetReinforcementManager.readXMLNode(child);

		return true;
	}
	
	protected boolean readPropertyStream_internal(MaxisPropertyStream stream) {
		if(stream == null) return false;
		mPetReinforcementManager = PetReinforcementManager.readPropertyStream(stream);
		return true;
	}
	
	public static PetManager readBinary(BufferReference dat) {
		if(dat == null) return null;
		PetManager str = new PetManager();
		if(!str.readBinary_internal(dat)) return null;
		return str;
	}
	
	public static PetManager readXMLNode(Element xml_element) {
		if(xml_element == null) return null;
		PetManager str = new PetManager();
		if(!str.readXMLNode_internal(xml_element)) return null;
		return str;
	}
	
	public static PetManager readPropertyStream(BufferReference dat, boolean byteOrder, int verFieldSize) {
		if(dat == null) return null;
		MaxisPropertyStream stream = MaxisPropertyStream.openForRead(dat, byteOrder, verFieldSize);
		return readPropertyStream(stream);
	}
	
	public static PetManager readPropertyStream(MaxisPropertyStream stream) {
		if(stream == null) return null;
		PetManager str = new PetManager();
		if(!str.readPropertyStream_internal(stream));
		return str;
	}
	
	/*----- Write -----*/
	
	public int getBinarySize() {
		int size = baseSize;
		if(mPetReinforcementManager != null) size += mPetReinforcementManager.getBinarySize();
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
		mPetReinforcementManager.writeBinaryTo(target);
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
		out.write(">\n");
		if(mPetReinforcementManager != null) mPetReinforcementManager.writeXMLNode(out, indent + "\t", XMLKEY_MPETREINFORCEMENTMANAGER);
		out.write(indent);
		out.write(String.format("</%s>\n", xmlNodeName));

	}

	public void addToPropertyStream(MaxisPropertyStream ps) {	
		if(ps == null) return;
		if(mPetReinforcementManager != null) mPetReinforcementManager.addToPropertyStream(ps);
	}
	
}
