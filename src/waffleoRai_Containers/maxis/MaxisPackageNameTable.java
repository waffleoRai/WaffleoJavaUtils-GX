package waffleoRai_Containers.maxis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.FileBuffer;

public class MaxisPackageNameTable {
	
	private Map<Long, String> table;
	
	public MaxisPackageNameTable() {
		table = new HashMap<Long, String>();
	}
	
	public String get(long instanceId) {return table.get(instanceId);}
	public void put(long instanceId, String name) {
		if(name != null) {
			table.put(instanceId, name);
		}
	}
	public void clear() {table.clear();}
	
	public List<Long> getAllInstanceIds(){
		List<Long> list = new ArrayList<Long>(table.size()+1);
		list.addAll(table.keySet());
		Collections.sort(list);
		return list;
	}
	
	public static MaxisPackageNameTable read(BufferReference data) {
		if(data == null) return null;
		
		MaxisPackageNameTable tbl = new MaxisPackageNameTable();
		
		int entryCount = data.nextInt();
		MaxisTypes.setUTFByteOrder(data.getBuffer().isBigEndian());
		for(int i = 0; i < entryCount; i++) {
			long id = data.nextLong();
			String name = MaxisTypes.readMaxisString(data);
			tbl.table.put(id, name);
		}
		
		return tbl;
	}
	
	public static FileBuffer write() {
		//TODO
		return null;
	}
	

}
