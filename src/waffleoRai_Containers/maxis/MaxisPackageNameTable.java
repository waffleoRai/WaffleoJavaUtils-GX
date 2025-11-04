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
		
		data.add(4L); //Version?
		int entryCount = data.nextInt();
		//MaxisTypes.setUTFByteOrder(data.getBuffer().isBigEndian());
		for(int i = 0; i < entryCount; i++) {
			long id = data.nextLong();
			//String name = MaxisTypes.readMaxisString(data);
			//tbl.table.put(id, name);
			int strlen = data.nextInt();
			String name = data.nextASCIIString(strlen);
			tbl.table.put(id, name);
		}
		
		return tbl;
	}
	
	public FileBuffer write() {
		int ecount = table.size();
		List<Long> idlist = new ArrayList<Long>(ecount+1);
		idlist.addAll(table.keySet());
		
		int alloc = 8 + (12 * ecount);
		for(String n : table.values()) {
			alloc += n.length();
		}
		
		FileBuffer out = new FileBuffer(alloc, false);
		out.addToFile(1);
		out.addToFile(ecount);
		for(Long id : idlist) {
			String name = table.get(id);
			out.addToFile(id);
			out.printASCIIToFile(name);
		}
		
		return out;
	}
	

}
