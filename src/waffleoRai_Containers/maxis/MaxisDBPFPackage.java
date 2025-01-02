package waffleoRai_Containers.maxis;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import waffleoRai_Files.tree.DirectoryNode;
import waffleoRai_Files.tree.FileNode;
import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;

public class MaxisDBPFPackage {
	
	/*
	 * Needs to handle...
	 * - Regular DBPFs
	 * - DBPPs
	 * - Deltas
	 */
	
	/*----- Constants -----*/
	
	public static final String MAGIC_DBPF = "DBPF";
	public static final String MAGIC_DBPP = "DBPP";
	
	/*----- Instance Variables -----*/
	
	private Map<MaxisResKey, MaxisResourceEntry> index;
	private DirectoryNode rootDir;
	
	/*----- Init -----*/
	
	public MaxisDBPFPackage() {
		index = new HashMap<MaxisResKey, MaxisResourceEntry>();
	}
	
	/*----- Getters -----*/
	
	public MaxisResourceEntry getResourceInfo(MaxisResKey key) {
		if(key == null) return null;
		return index.get(key);
	}
	
	public FileBuffer loadResourceData(MaxisResKey key) throws IOException {
		if(key == null) return null;
		
		MaxisResourceEntry entry = index.get(key);
		if(entry == null) return null;
		FileNode src = entry.getDataSource();
		if(src == null) return null;
		
		return src.loadDecompressedData();
	}
	
	/*----- Setters -----*/
	
	public MaxisResourceEntry addResource(MaxisResKey key, FileNode dataSource) {
		if(key == null) return null;
		MaxisResourceEntry entry = new MaxisResourceEntry();
		entry.setKey(key);
		entry.setDataSource(dataSource);
		index.put(entry.getKey(), entry);
		return entry;
	}
	
	/*----- Read -----*/
	
	private static class SearchStreak implements Comparable<SearchStreak>{
		public int offset;
		public int spacing;
		public int count;
		
		public boolean equals(Object o) {
			if(o == null) return false;
			if(o == this) return true;
			if(!(o instanceof SearchStreak)) return false;
			
			SearchStreak other = (SearchStreak)o;
			if(this.offset != other.offset) return false;
			if(this.spacing != other.spacing) return false;
			if(this.count != other.count) return false;
			
			return true;
		}
		
		public int hashCode() {
			return (offset << 16) | (count + spacing);
		}

		public int compareTo(SearchStreak o) {
			if(o == null) return -1;
			
			if(this.count != o.count) return this.count - o.count;
			
			return this.offset - o.offset;
		}
	}
	
	private static int[] findDBPPIndex(FileBuffer data){
		//"Bruteforce" method. Just looks for streaks of 0x1ffff and 0x10000 spaced evenly apart.
		//Returns offset and entry count, if found
		LinkedList<SearchStreak> foundList = new LinkedList<SearchStreak>();
		
		long fSize = data.getFileSize();
		for(long i = 0; i < fSize; i++) {
			int readWord = data.intFromFile(i);
			if((readWord == MaxisResourceEntry.COMPFLAGS_LZSS) || (readWord == MaxisResourceEntry.COMPFLAGS_NOCOMP)) {
				for(int frame = 4; frame <= 32; frame += 4) {
					SearchStreak streak = new SearchStreak();
					streak.offset = (int)i;
					streak.spacing = frame;
					streak.count = 1;
					
					for(long j = (i+frame); j < fSize; j += frame) {
						readWord = data.intFromFile(j);
						if((readWord == MaxisResourceEntry.COMPFLAGS_LZSS) || (readWord == MaxisResourceEntry.COMPFLAGS_NOCOMP)) {
							streak.count++;
						}
						else break;
					}
					foundList.add(streak);
				}
			}
		}
		
		if(foundList.isEmpty()) return null;
		
		Collections.sort(foundList);
		Collections.reverse(foundList);
		
		while(!foundList.isEmpty()) {
			int[] res = new int[2];
			SearchStreak best = foundList.pop();
			
			res[0] = best.offset - 32; //Common fields + first entry fields + first bitfield
			res[1] = best.count;
			return res;
		}
		
		return null;
	}
	
	private int readIndexV2(BufferReference data, int entry_count){
		int commonMask = data.nextInt();
		int[] commonFields = new int[8];
		
		int mask = 0x1;
		for(int i = 0; i < 8; i++) {
			if((mask & commonMask) != 0) {
				commonFields[i] = data.nextInt();
			}
			mask <<= 1;
		}
		
		mask = 0x1;
		int added = 0;
		for(int i = 0; i < entry_count; i++) {
			MaxisResourceEntry entry = MaxisResourceEntry.readFromIndexV2(data, ~commonMask);
			for(int j = 0; j < 8; i++) {
				if((mask & commonMask) != 0) {
					switch(j) {
					case 0: entry.setTypeId(commonFields[j]); break;
					case 1: entry.setGroupId(commonFields[j]); break;
					case 2: entry.setInstanceIdHi(commonFields[j]); break;
					case 3: entry.setInstanceIdLo(commonFields[j]); break;
					case 4: entry.setOffset(commonFields[j]); break;
					case 5: entry.setSizeDisk(commonFields[j] & 0x7fffffff); break;
					case 6: entry.setSizeMem(commonFields[j]); break;
					}
				}
				mask <<= 1;
			}
			index.put(entry.getKey(), entry);
			added++;
		}
		
		return added;
	}
	
	public static MaxisDBPFPackage read(String pkgPath) throws IOException, UnsupportedFileTypeException{
		return read(pkgPath, 0L, -1L);
	}
	
	public static MaxisDBPFPackage read(String pkgPath, long offset, long len) throws IOException, UnsupportedFileTypeException{
		//Read data
		FileBuffer dataBuff = null;
		if(len < 0L) dataBuff = FileBuffer.createBuffer(pkgPath, offset, false);
		else dataBuff = FileBuffer.createBuffer(pkgPath, offset, offset + len, false);
		MaxisDBPFPackage pack = read(dataBuff);
		
		//Add load sources
		if(pack != null) {
			pack.rootDir = new DirectoryNode(null, "DBPF");
			for(MaxisResourceEntry entry : pack.index.values()) {
				FileNode fn = entry.generateFileNode(pack.rootDir, pkgPath);
				if(offset > 0) fn.setOffset(fn.getOffset() + offset);
				entry.setDataSource(fn);
			}
		}
		dataBuff.dispose();
		
		return pack;
	}
	
	public static MaxisDBPFPackage read(FileBuffer data) throws UnsupportedFileTypeException, IOException{
		int indexOffset = 0;
		int indexEntryCount = 0;
		int versionMajor = 0;
		int versionMinor = 0;
		int indexVersionMajor = 0;
		int indexVersionMinor = 0;
		
		//Header
		String magic = data.getASCII_string(0L, 4);
		data.setCurrentPosition(4L);
		if(magic.equals(MAGIC_DBPF)) {
			versionMajor = data.nextInt();
			versionMinor = data.nextInt();
			data.skipBytes(12L); //Unknown
			data.skipBytes(8L); //Timestamps
			indexVersionMajor = data.nextInt();
			indexEntryCount = data.nextInt();
			indexOffset = data.nextInt();
			data.skipBytes(16L); //Index size in bytes, hole information
			indexVersionMinor = data.nextInt();
			
			if(versionMajor >= 2) {
				indexOffset = data.nextInt();
			}
		}
		else if(magic.equals(MAGIC_DBPP)) {
			//Bruteforce find index if needed
			int[] iloc = findDBPPIndex(data);
			if(iloc == null) {
				throw new UnsupportedFileTypeException("MaxisDBPFPackage.read || Could not find DBPP index!");
			}
			
			//Assumed version 2
			indexVersionMajor = 7;
			indexVersionMinor = 3;
			versionMajor = 2;
			indexOffset = iloc[0];
			indexEntryCount = iloc[1];
		}
		else {
			throw new UnsupportedFileTypeException("MaxisDBPFPackage.read || DBPF package magic not found!");
		}
		
		
		//Read in index
		if(indexVersionMajor != 7) {
			throw new UnsupportedFileTypeException("MaxisDBPFPackage.read || Expected index version of 7!");
		}
		
		MaxisDBPFPackage pack = new MaxisDBPFPackage();
		BufferReference indexRef = data.getReferenceAt(indexOffset);
		switch(indexVersionMinor) {
		case 0:
			for(int i = 0; i < indexEntryCount; i++) {
				MaxisResourceEntry entry = MaxisResourceEntry.readFromIndexV7_0(indexRef);
				pack.index.put(entry.getKey(), entry);
			}
			break;
		case 1:
			for(int i = 0; i < indexEntryCount; i++) {
				MaxisResourceEntry entry = MaxisResourceEntry.readFromIndexV7_1(indexRef);
				pack.index.put(entry.getKey(), entry);
			}
			break;
		case 3:
			pack.readIndexV2(indexRef, indexEntryCount);
			break;
		default:
			throw new UnsupportedFileTypeException("MaxisDBPFPackage.read || Index version " + indexVersionMajor + "." + indexVersionMinor + " not recognized!");
		}
		
		List<MaxisResKey> allKeys = new ArrayList<MaxisResKey>(pack.index.size()+1);
		allKeys.addAll(pack.index.keySet());
		Collections.sort(allKeys);
		
		Map<Long, MaxisResourceEntry> instanceIdMap = new HashMap<Long, MaxisResourceEntry>();
		for(MaxisResKey key : allKeys) {
			long iid = key.getInstanceId();
			MaxisResourceEntry entry = pack.index.get(key);
			instanceIdMap.put(iid, entry);
		}
		
		//Read in DIRs (if applicable)
		if(versionMajor < 2) {	
			boolean dirFound = false;
			for(MaxisResKey key : allKeys) {
				int typeId = key.getTypeId();
				if(typeId == MaxisTypeIds._DIR) {
					dirFound = true;
					MaxisResourceEntry entry = pack.index.get(key);

					long offset = Integer.toUnsignedLong(entry.getOffset());
					FileBuffer dirData = data.createReadOnlyCopy(offset, offset + entry.getSizeDisk());
					
					BufferReference dirPos = dirData.getReferenceAt(0L);
					MaxisResKey childKey = new MaxisResKey();
					while(dirPos.hasRemaining()) {

						childKey.setTypeId(dirPos.nextInt());
						childKey.setGroupId(dirPos.nextInt());

						long childInstance = 0L;
						if(indexVersionMinor < 1) {
							childInstance = Integer.toUnsignedLong(dirPos.nextInt());
						}
						else {
							childInstance = Integer.toUnsignedLong(dirPos.nextInt()) << 32;
							childInstance |= Integer.toUnsignedLong(dirPos.nextInt());
						}
						childKey.setInstanceId(childInstance);
						
						MaxisResourceEntry childEntry = pack.index.get(childKey);
						if(childEntry != null) {
							childEntry.setSizeMem(dirPos.nextInt());
						}
					}
					
					dirData.dispose();
				}
				else {
					if(dirFound) break;
				}
			}
		}
		
		//Read in file names, if applicable
		boolean nameTableFound = false;
		for(MaxisResKey key : allKeys) {
			int typeId = key.getTypeId();
			if(typeId == MaxisTypeIds.NAME_INDEX) {
				nameTableFound = true;
				MaxisResourceEntry entry = pack.index.get(key);
				
				long offset = Integer.toUnsignedLong(entry.getOffset());
				FileBuffer tblData = data.createReadOnlyCopy(offset, offset + entry.getSizeDisk());
				MaxisPackageNameTable ntbl = MaxisPackageNameTable.read(tblData.getReferenceAt(0L));
				
				List<Long> memberIds = ntbl.getAllInstanceIds();
				for(Long iid : memberIds) {
					MaxisResourceEntry target = instanceIdMap.get(iid);
					if(target != null) {
						target.setName(ntbl.get(iid));
					}
				}
				
				tblData.dispose();
			}
			else {
				if(nameTableFound) break;
			}
		}
		
		return pack;
	}
	
	public boolean loadDelta(String deltaPath, boolean keepDel) throws IOException, UnsupportedFileTypeException{
		MaxisDBPFPackage delta = MaxisDBPFPackage.read(deltaPath);
		if(delta == null) return false;
		if(delta.index.isEmpty()) return false;
		
		List<MaxisResKey> allKeys = new ArrayList<MaxisResKey>(delta.index.size()+1);
		Collections.sort(allKeys);
		for(MaxisResKey key : allKeys) {
			MaxisResourceEntry deltaEntry = delta.index.get(key);
			if((deltaEntry.getSizeDisk() < 1) && !keepDel) {
				index.remove(key);
			}
			else index.put(key, deltaEntry);
		}
		
		return true;
	}
	
	public boolean loadDelta(FileBuffer deltaData, boolean keepDel) throws UnsupportedFileTypeException, IOException{
		MaxisDBPFPackage delta = MaxisDBPFPackage.read(deltaData);
		if(delta == null) return false;
		if(delta.index.isEmpty()) return false;
		
		List<MaxisResKey> allKeys = new ArrayList<MaxisResKey>(delta.index.size()+1);
		Collections.sort(allKeys);
		for(MaxisResKey key : allKeys) {
			MaxisResourceEntry deltaEntry = delta.index.get(key);
			if((deltaEntry.getSizeDisk() < 1) && !keepDel) {
				index.remove(key);
			}
			else index.put(key, deltaEntry);
		}
		
		return true;
	}
	
	/*----- Write -----*/
	
	public boolean writeV2To(String outpath){
		//TODO
		return false;
	}
	
	/*----- Debug -----*/
	

}
