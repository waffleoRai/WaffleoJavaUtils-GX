package waffleoRai_Files.maxis;

import java.io.IOException;
import java.io.Writer;

import org.w3c.dom.Element;

import waffleoRai_Containers.maxis.MaxisResKey;
import waffleoRai_Files.XMLReader;

public class MaxisResRef {
	
	private MaxisResKey key;
	private String resName;
	private String uniqueName;
	private String resMainPath; //If it requires multiple files, then the file in this path points to the others

	private MaxisResRef() {}
	
	public MaxisResRef(MaxisResKey refKey) {
		key = refKey;
		uniqueName = String.format("%s_%08x_%016x", MaxisTypeIds.stringFromValue(key.getTypeId()), key.getGroupId(), key.getInstanceId());
		resName = uniqueName;
	}
	
	public MaxisResKey getKey() {return key;}
	public String getName() {return resName;}
	public String getUniqueName() {return uniqueName;}
	public String getPath() {return resMainPath;}
	
	public void setName(String val) {resName = val;}
	public void setPath(String val) {resMainPath = val;}
	
	public void setUniqueName(String val) {
		val = val.replace("/", "");
		val = val.replace(" ", "_");
		val = val.replace("\\", "");
		val = val.replace(":", "");
		val = val.replace(";", "");
		uniqueName = val;
	}
	
	public static MaxisResRef fromXMLNode(Element xml_element) {
		if(xml_element == null) return null;
		String nn = xml_element.getNodeName();
		if(!nn.equals("MaxisResReference")) return null;
		
		MaxisResRef entry = new MaxisResRef();
		String aval = xml_element.getAttribute("Name");
		if(aval != null) entry.resName = aval;
		aval = xml_element.getAttribute("UniqueName");
		if(aval != null) entry.uniqueName = aval;
		
		Element child = XMLReader.getFirstChildElementWithTag(xml_element, "Data");
		if(child != null) {
			aval = child.getAttribute("FilePath");
			if(aval != null) entry.resMainPath = aval;
		}
		
		child = XMLReader.getFirstChildElementWithTag(xml_element, "ResourceKey");
		if(child != null) entry.key = MaxisResKey.readXMLNode(child);
		
		if(entry.resName == null) {
			entry.resName = String.format("%08x_%08x_%016x", entry.key.getTypeId(), entry.key.getGroupId(), entry.key.getInstanceId());
		}
		
		return entry;
	}
	
	public void writeXMLNode(Writer out, String indent) throws IOException {
		if(indent == null) indent = "";
		out.write(indent + "<MaxisResReference");
		out.write(String.format(" Name=\"%s\"", resName));
		out.write(String.format(" UniqueName=\"%s\"", uniqueName));
		out.write(">\n");
		
		key.writeXMLNode(out, indent + "\t");
		if(resMainPath != null) {
			out.write(indent + "\t<Data FilePath=\"");
			out.write(resMainPath);
			out.write("\"/>\n");
		}
		
		out.write(indent + "</MaxisResReference>\n");
	}

}
