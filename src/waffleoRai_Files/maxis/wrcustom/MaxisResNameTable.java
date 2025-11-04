package waffleoRai_Files.maxis.wrcustom;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import waffleoRai_Containers.maxis.MaxisDBPFPackage;
import waffleoRai_Containers.maxis.MaxisResKey;
import waffleoRai_Containers.maxis.MaxisResourceEntry;
import waffleoRai_DataContainers.MultiValMap;
import waffleoRai_Files.maxis.MaxisResRef;
import waffleoRai_Files.maxis.MaxisTypeIds;
import waffleoRai_Utils.BinFieldSize;
import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;

public class MaxisResNameTable {
	
	private Map<String, MaxisResRef> nameMap;
	private MultiValMap<String, MaxisResRef> rawNameMap;
	
	public MaxisResNameTable() {
		nameMap = new HashMap<String, MaxisResRef>();
		rawNameMap = new MultiValMap<String, MaxisResRef>();
	}
	
	public MaxisResRef getRefByUniqueName(String name) {
		return nameMap.get(name);
	}
	
	private void updateUniqueName(MaxisResRef entry) {
		MaxisResKey key = entry.getKey();
		String u = entry.getName();
		u += "_t" + MaxisTypeIds.stringFromValue(key.getTypeId());
		u += String.format("_g%08X", key.getGroupId());
		if(nameMap.containsKey(u)) {
			u += String.format("_i%016X", key.getInstanceId());
		}
		
		//Replace bad characters
		u = u.replace("/", "");
		u = u.replace(" ", "_");
		u = u.replace("\\", "");
		u = u.replace(":", "");
		u = u.replace(";", "");
		
		entry.setUniqueName(u);
		nameMap.put(entry.getUniqueName(), entry);
	}
	
	public void loadPackage(String pkgPath, String deltaPath) throws IOException, UnsupportedFileTypeException {
		MaxisDBPFPackage pkg = MaxisDBPFPackage.read(pkgPath);
		if(deltaPath != null) pkg.loadDelta(deltaPath, false);
		
		Collection<MaxisResKey> keys = pkg.getAllResourceKeys();
		for(MaxisResKey key : keys) {
			MaxisResRef ref = new MaxisResRef(key);
			MaxisResourceEntry res = pkg.getResourceInfo(key);
			if(res != null) {
				String name = res.getName();
				if(name != null) {
					ref.setName(name);
					List<MaxisResRef> sameNames = rawNameMap.getValues(name);
					if(sameNames != null && !sameNames.isEmpty()) {
						nameMap.remove(name);
						for(MaxisResRef other : sameNames) {
							String u = other.getUniqueName();
							if(u.equals(name)) {
								updateUniqueName(other);
							}
						}
						updateUniqueName(ref);
					}
					else {
						ref.setUniqueName(name);
						nameMap.put(ref.getUniqueName(), ref);
					}
				}
				nameMap.put(ref.getUniqueName(), ref);
			}
			
			addRef(ref);
		}
	}
	
	public void addRef(MaxisResRef ref) {
		if(ref == null) return;
		String uname = ref.getUniqueName();
		nameMap.put(uname, ref);
		
		String rawname = ref.getName();
		if(rawname != null) {
			rawNameMap.addValue(rawname, ref);
		}
	}
	
	public void clear() {
		nameMap.clear();
		rawNameMap.clearValues();
	}
	
	public void updateMappings() {
		if(nameMap.isEmpty()) return;
		List<MaxisResRef> refs = new ArrayList<MaxisResRef>(nameMap.size());
		refs.addAll(nameMap.values());
		nameMap.clear();
		rawNameMap.clearValues();
		for(MaxisResRef ref : refs) {
			String uname = ref.getUniqueName();
			nameMap.put(uname, ref);
			String rname = ref.getName();
			if(rname != null) {
				rawNameMap.addValue(rname, ref);
			}
		}
	}
	
	public static MaxisResNameTable loadFromFile(String path) throws IOException {
		if(path == null) return null;
		FileBuffer indat = FileBuffer.createBuffer(path, false);
		BufferReference ref = indat.getReferenceAt(0L);
		
		MaxisResNameTable tbl = new MaxisResNameTable();
		int count = ref.nextInt();
		
		for(int i = 0; i < count; i++) {
			MaxisResKey key = new MaxisResKey();
			key.setTypeId(ref.nextInt());
			key.setGroupId(ref.nextInt());
			key.setInstanceId(ref.nextLong());
			
			MaxisResRef resref = new MaxisResRef(key);
			String s = ref.nextVariableLengthString(BinFieldSize.WORD, 2);
			if(s != null && !s.isEmpty()) {
				resref.setUniqueName(s);
			}
			s = ref.nextVariableLengthString(BinFieldSize.WORD, 2);
			if(s != null && !s.isEmpty()) {
				resref.setName(s);
			}
			s = ref.nextVariableLengthString(BinFieldSize.WORD, 2);
			if(s != null && !s.isEmpty()) {
				resref.setPath(s);
			}
			
			tbl.nameMap.put(resref.getUniqueName(), resref);
			String rname = resref.getName();
			if(rname != null) tbl.rawNameMap.addValue(rname, resref);
		}
		
		indat.dispose();
		return tbl;
	}
	
	public void saveToFile(String path) throws IOException {
		BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(path));
		
		int sz = nameMap.size();
		FileBuffer buff = new FileBuffer(4, false);
		buff.addToFile(sz);
		buff.writeToStream(bos);
		
		for(MaxisResRef ref : nameMap.values()) {
			String name = ref.getName();
			String uname = ref.getUniqueName();
			MaxisResKey key = ref.getKey();
			String rpath = ref.getPath();
			
			int size = 16 + 6;
			if(name != null) size += name.length() + 1;
			if(uname != null) size += uname.length() + 1;
			if(rpath != null) size += rpath.length() + 1;
			buff = new FileBuffer(size, false);
			buff.addToFile(key.getTypeId());
			buff.addToFile(key.getGroupId());
			buff.addToFile(key.getInstanceId());
			if(uname != null) {
				buff.addVariableLengthString(uname, BinFieldSize.WORD, 2);
			}
			else buff.addToFile((short)0);
			if(name != null) {
				buff.addVariableLengthString(name, BinFieldSize.WORD, 2);
			}
			else buff.addToFile((short)0);
			if(rpath != null) {
				buff.addVariableLengthString(rpath, BinFieldSize.WORD, 2);
			}
			else buff.addToFile((short)0);
			buff.writeToStream(bos);
			
			buff.dispose();
		}
		bos.close();
	}

	public void writeTsv(String path) throws IOException {
		//Remap by key so have nice order
		List<MaxisResKey> keys = new ArrayList<MaxisResKey>(nameMap.size() + 1);
		Map<MaxisResKey, MaxisResRef> keymap = new HashMap<MaxisResKey, MaxisResRef>();
		for(MaxisResRef ref : nameMap.values()) {
			MaxisResKey key = ref.getKey();
			keymap.put(key, ref);
			keys.add(key);
		}
		Collections.sort(keys);
		
		BufferedWriter bw = new BufferedWriter(new FileWriter(path));
		bw.write("#TYPE\tGROUP\tINSTANCE\tUNIQUE_NAME\tNAME\tIMPPATH\n");
		for(MaxisResKey key : keys) {
			bw.write(MaxisTypeIds.stringFromValue(key.getTypeId()));
			bw.write(String.format("\t%08X", key.getGroupId()));
			bw.write(String.format("\t%016X", key.getInstanceId()));
			
			MaxisResRef ref = keymap.get(key);
			bw.write("\t" + ref.getUniqueName());
			bw.write("\t" + ref.getName());
			bw.write("\t" + ref.getPath());
			
			bw.write("\n");
		}
		bw.close();
	}
	
}
