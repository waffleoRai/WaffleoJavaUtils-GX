package waffleoRai_Containers.maxis;

import java.io.IOException;
import java.io.Writer;

import org.w3c.dom.Element;

import waffleoRai_Utils.StringUtils;

public class MaxisResKey implements Comparable<MaxisResKey>{
	
	private int typeId;
	private int groupId;
	private long instanceId;
	
	public int getTypeId(){return typeId;}
	public int getGroupId(){return groupId;}
	public long getInstanceId(){return instanceId;}
	
	public int getInstanceIdHi(){
		return (int)(instanceId >>> 32);
	}
	
	public int getInstanceIdLo(){
		return (int)(instanceId & 0xffffffffL);
	}
	
	public void setTypeId(int val){typeId = val;}
	public void setGroupId(int val){groupId = val;}
	public void setInstanceId(long val){instanceId = val;}
	
	public int hashCode(){
		return typeId ^ groupId ^ getInstanceIdHi() ^ getInstanceIdLo();
	}
	
	public boolean equals(Object o){
		if(o == null) return false;
		if(o == this) return true;
		if(!(o instanceof MaxisResKey)) return false;
		
		MaxisResKey other = (MaxisResKey)o;
		if(instanceId != other.instanceId) return false;
		if(typeId != other.typeId) return false;
		if(groupId != other.groupId) return false;
		
		return true;
	}
	
	public int compareTo(MaxisResKey o) {
		if(o == null) return -1;
		
		if(this.typeId != o.typeId){
			return Integer.compareUnsigned(this.typeId, o.typeId);
		}
		
		if(this.groupId != o.groupId){
			return Integer.compareUnsigned(this.groupId, o.groupId);
		}
		
		if(this.instanceId != o.instanceId){
			return Long.compareUnsigned(this.instanceId, o.instanceId);
		}
		
		return 0;
	}
	
	public static MaxisResKey readXMLNode(Element xml_element) {
		if(xml_element == null) return null;
		MaxisResKey str = new MaxisResKey();

		String aval = xml_element.getAttribute("TypeId");
		if(aval != null) {
			str.typeId = StringUtils.parseUnsignedInt(aval);
		}
		
		aval = xml_element.getAttribute("GroupId");
		if(aval != null) {
			str.groupId = StringUtils.parseUnsignedInt(aval);
		}
		
		aval = xml_element.getAttribute("InstanceId");
		if(aval != null) {
			str.instanceId = StringUtils.parseUnsignedLong(aval);
		}
		return str;
	}
	
	public void writeXMLNode(Writer out, String indent) throws IOException {
		if(out == null) return;
		if(indent == null) indent = "";
		
		out.write(indent);
		out.write("<ResourceKey");
		out.write(String.format(" TypeId=\"0x%08x\"", typeId));
		out.write(String.format(" GroupId=\"0x%08x\"", groupId));
		out.write(String.format(" InstanceId=\"0x%016x\"", instanceId));
		out.write("/>\n");
	}
	
}
