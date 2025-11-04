package waffleoRai_Files.maxis.xml2java;

import java.util.List;

import org.w3c.dom.Element;

public class PseudoUnionField extends AnonStructField{
	
	//May or may not actually be anonymous, but this allows easier inner struct generation
	
	//private List<DataField> options = new ArrayList<DataField>();
	public boolean isAnon = true;
	
	public boolean hasFixedSize() {return false;}
	public boolean canBeNull() {return true;}
	public boolean isXmlMultiLine() {return true;}
	
	public void initFromXmlElement(Element xmlElement) {
		//String aval = xmlElement.getAttribute("Name");
		//if(aval != null && !aval.isEmpty()) isAnon = false;
		super.initFromXmlElement(xmlElement);
		fieldType = TS3XJ.FIELD_TYPE_PSEUDO_UNION;
	}
	
	public void getAnonStructNodes(List<DataField> list) {
		if(list == null) return;
		if(isAnon) list.add(this);
		for(DataField member : members) {
			member.getAnonStructNodes(list);
		}
	}
	
	public void getAnonStructNodesInner(List<DataField> list) {
		if(list == null) return;
		for(DataField member : members) {
			member.getAnonStructNodes(list);
		}
	}
	
}
