package waffleoRai_Containers.maxis.ts3.savefmts.sim;

import java.io.IOException;
import java.io.Writer;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import waffleoRai_Containers.maxis.MaxisPropertyStream;
import waffleoRai_Containers.maxis.MaxisResKey;
import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.FileBuffer;

public class FacialBlend {
	
	public MaxisResKey key;
	public float amount;
	
	public FacialBlend() {
		key = new MaxisResKey();
	}
	
	public static FacialBlend readBinary(BufferReference dat) {
		if(dat == null) return null;
		FacialBlend str = new FacialBlend();
		str.key.setGroupId(dat.nextInt());
		str.key.setTypeId(dat.nextInt());
		str.key.setInstanceId(dat.nextLong());
		str.amount = Float.intBitsToFloat(dat.nextInt());
		return str;
	}
	
	public static FacialBlend readXMLNode(Element xml_element) {
		if(xml_element == null) return null;
		FacialBlend str = new FacialBlend();

		String aval = xml_element.getAttribute("Amount");
		if(aval != null) {
			str.amount = (float)(Double.parseDouble(aval));
		}
		
		NodeList children = xml_element.getElementsByTagName("ResourceKey");
		if(children != null) {
			int len = children.getLength();
			for(int i = 0; i < len; i++) {
				Node child = children.item(i);
				if(child.getNodeType() == Node.ELEMENT_NODE) {
					str.key = MaxisResKey.readXMLNode((Element)child);
					break;
				}
			}
		}
		
		return str;
	}
	
	public static FacialBlend readFromPropertyStream(MaxisPropertyStream ps, int baseId) {
		if(ps == null) return null;
		FacialBlend str = new FacialBlend();
		
		str.key.setGroupId(ps.getFieldAsInt(baseId));
		str.key.setTypeId(ps.getFieldAsInt(baseId+1));
		str.key.setInstanceId(ps.getFieldAsLong(baseId+2));
		str.amount = ps.getFieldAsFloat(baseId+3);
		
		return str;
	}
	
	public int getBinarySize() {return 20;}
	
	public void serializeTo(FileBuffer target) {
		target.addToFile(key.getGroupId());
		target.addToFile(key.getTypeId());
		target.addToFile(key.getInstanceId());
		target.addToFile(Float.floatToRawIntBits(amount));
	}
	
	public FileBuffer writeBinary(boolean byteOrder) {
		FileBuffer buff = new FileBuffer(20, byteOrder);
		buff.addToFile(key.getGroupId());
		buff.addToFile(key.getTypeId());
		buff.addToFile(key.getInstanceId());
		buff.addToFile(Float.floatToRawIntBits(amount));
		return buff;
	}

	public void writeXMLNode(Writer out, String indent) throws IOException {
		if(out == null) return;
		if(indent == null) indent = "";
		
		out.write(indent);
		out.write("<FacialBlend");
		out.write(String.format(" Amount=\"%.3f\"", amount));
		out.write(">\n");
		key.writeXMLNode(out, indent + "\t");
		out.write(indent + "</FacialBlend>\\n");
	}

	public void writeToPropertyStream(MaxisPropertyStream ps, int baseId) {
		if(ps == null) return;
		
		ps.addInt(key.getGroupId(), baseId);
		ps.addInt(key.getTypeId(), baseId+1);
		ps.addLong(key.getInstanceId(), baseId+2);
		ps.addFloat(amount, baseId+3);
	}
	
	
}
