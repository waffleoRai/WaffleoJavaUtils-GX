package waffleoRai_Files.maxis.xml2java;

import java.io.IOException;
import java.io.Writer;
import java.util.LinkedList;
import java.util.List;

import org.w3c.dom.Element;

import waffleoRai_Files.XMLReader;
import waffleoRai_Utils.StringUtils;

public class BitfieldField extends DataField{
	
	public List<BitfieldMember> members;
	
	public static class BitfieldMember{
		public String name = null;
		public String xmlName = null;
		public int lowestBit = -1;
		public int sizeBits = 0;
	}
	
	public BitfieldField() {
		members = new LinkedList<BitfieldMember>();
	}
	
	public boolean hasFixedSize() {return true;}
	public boolean canBeNull() {return false;}
	
	public void initFromXmlElement(Element xmlElement) {
		super.initFromXmlElement(xmlElement);
		
		//Read members but treat as primitive for now
		List<Element> children = XMLReader.getChildElementsWithTag(xmlElement, "Bitfield");
		for(Element child : children) {
			BitfieldMember mem = new BitfieldMember();
			String aval = child.getAttribute("LowestBit");
			if(aval != null) mem.lowestBit = StringUtils.parseSignedInt(aval);
			aval = child.getAttribute("NumBits");
			if(aval != null) mem.sizeBits = StringUtils.parseSignedInt(aval);
			aval = child.getAttribute("Name");
			if(aval != null) {
				mem.name = aval;
				mem.xmlName = StringUtils.capitalize(mem.name);
			}
		}
		
		fieldType = TS3XJ.FIELD_TYPE_BITFIELD;
	}
	
	public static int getBitmask(int bitCount) {
		int n = ~0;
		n <<= bitCount;
		return ~n;
	}
	
	public void writeReadXmlListCode(Writer out, String indent, String inputName, String trgName) throws IOException{
		out.write(indent + "child = XMLReader.getFirstChildElementWithTag(");
		out.write(inputName + ", \"" + javaTypeName + "\");\n");
		out.write(indent + "if(child != null){\n");
		out.write(indent + "\t" + trgName + " = 0;\n");
		for(BitfieldMember mem : members) {
			out.write(indent + "\taval = child.getAttribute(\"" + mem.xmlName + "\");\n");
			out.write(indent + "\tif(aval != null){\n");
			if(mem.sizeBits > 1) {
				out.write(indent + "\t\tint n = StringUtils.parseUnsignedInt(aval);\n");
				out.write(indent + "\t\tn &= " + String.format("0x%x", getBitmask(mem.sizeBits)) + ";\n");
				out.write(indent + "\t\t" + trgName + " |= n << " + mem.lowestBit + ";\n");
			}
			else {
				out.write(indent + "\t\tboolean b = Boolean.parseBoolean(aval);\n");
				out.write(indent + "\t\tif(b) ");
				out.write(trgName + " |= 1 << " + mem.lowestBit + ";\n");
			}
			out.write(indent + "\t}\n");
		}
		out.write(indent + "}\n");
	}
	
	/*public void writeReadXmlCode(Writer out, String indent, String inputName) throws IOException{
		//TODO
	}*/
	
	public void writeWriteXmlListCode(Writer out) throws IOException {
		//TODO
	}
	
	public void writeWriteXmlCode(Writer out, String indent, String outputName) throws IOException{
		String useIndent = indent;
		if(condition != null) {
			out.write(indent + "if(" + condition + "){\n");
			useIndent += "\t";
		}
		out.write(useIndent + outputName + ".write(String.format(indent + \"\\t<%s\", " + xmlKeyName + "));\n");
		for(BitfieldMember mem : members) {
			out.write(useIndent + outputName + ".write(String.format(\" " + mem.xmlName);
			out.write("=\"");
			if(mem.sizeBits > 1) out.write("0x%x\"");
			else out.write("%b\"");
			out.write(", ((" + name + " >>> " + mem.lowestBit + ") & ");
			out.write(String.format("0x%x", getBitmask(mem.sizeBits)));
			out.write(")));\n");
		}
		out.write(useIndent + outputName + ".write(\"/>\\n\");\n");
		if(condition != null) out.write(indent + "}\n");
	}

}
