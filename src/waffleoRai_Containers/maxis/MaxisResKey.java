package waffleoRai_Containers.maxis;

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
	
}
