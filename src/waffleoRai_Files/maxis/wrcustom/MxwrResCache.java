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

import waffleoRai_Compression.definitions.CompDefNode;
import waffleoRai_Compression.lz77.MaxisLZ;
import waffleoRai_Containers.maxis.MaxisDBPFPackage;
import waffleoRai_Containers.maxis.MaxisResKey;
import waffleoRai_Containers.maxis.MaxisResourceEntry;
import waffleoRai_Files.maxis.MaxisTypeIds;
import waffleoRai_Files.tree.FileNode;
import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;

public class MxwrResCache {
	
	public static final int WRITE_VERSION = 1;
	
	/*----- Instance Variables -----*/
	
	private String cachePath;
	private Map<MaxisResKey, FileNode> locMap;
	private ArrayList<String> pathTable;
	
	private Map<Integer, TypeIndexBlock> typeIndex;
	
	/*----- Inner Classes -----*/
	
	private static class TypeIndexBlock{
		public static final long IDX_ENTRY_SER_SIZE = 16;
		
		public int typeId;
		public int groupCount;
		public long offset;
		
		public Map<Integer, GroupIndexBlock> groupIndex;
		
		public TypeIndexBlock() {
			groupIndex = new HashMap<Integer, GroupIndexBlock>();
		}
		
		public static TypeIndexBlock read(BufferReference data) {
			if(data == null) return null;
			TypeIndexBlock block = new TypeIndexBlock();
			block.typeId = data.nextInt();
			block.groupCount = data.nextInt();
			block.offset = data.nextLong();
			return block;
		}
		
		public void serializeTo(FileBuffer target) {
			if(target == null) return;
			groupCount = groupIndex.size();
			target.addToFile(typeId);
			target.addToFile(groupCount);
			target.addToFile(offset);
		}
	
		public long getContentsSizeBytes() {
			long size = (long)groupIndex.size() * GroupIndexBlock.IDX_ENTRY_SER_SIZE;
			for(GroupIndexBlock gib : groupIndex.values()) {
				size += gib.getContentsSizeBytes();
			}
			return size;
		}
		
		public long updateOffsets(long base) {
			offset = base;
			//Start of instance entries... (after all group entries)
			long cpos = offset + (groupIndex.size() * GroupIndexBlock.IDX_ENTRY_SER_SIZE);
			List<Integer> gids = new ArrayList<Integer>(groupIndex.size()+1);
			gids.addAll(groupIndex.keySet());
			Collections.sort(gids);
			for(Integer groupId : gids) {
				GroupIndexBlock gib  = groupIndex.get(groupId);
				gib.offset = cpos;
				cpos += gib.getContentsSizeBytes();
			}
			return cpos;
		}
		
		public void serializeContentsTo(FileBuffer target, Map<String, Integer> pathIndexMap) {
			List<Integer> gids = new ArrayList<Integer>(groupIndex.size()+1);
			gids.addAll(groupIndex.keySet());
			Collections.sort(gids);
			for(Integer groupId : gids) {
				GroupIndexBlock gib  = groupIndex.get(groupId);
				gib.serializeTo(target);
			}
			for(Integer groupId : gids) {
				GroupIndexBlock gib  = groupIndex.get(groupId);
				gib.serializeContentsTo(target, pathIndexMap);
			}
		}
		
	}
	
	private static class GroupIndexBlock{
		
		public static final long IDX_ENTRY_SER_SIZE = 16;
		
		public int groupId;
		public int entryCount;
		public long offset;
		
		public Map<Long, InstanceEntry> contents;
		
		public GroupIndexBlock() {
			contents = new HashMap<Long, InstanceEntry>();
		}
		
		public static GroupIndexBlock read(BufferReference data) {
			if(data == null) return null;
			GroupIndexBlock block = new GroupIndexBlock();
			block.groupId = data.nextInt();
			block.entryCount = data.nextInt();
			block.offset = data.nextLong();
			return block;
		}
		
		public long getContentsSizeBytes() {
			return (long)contents.size() * (long)InstanceEntry.SER_SIZE;
		}
				
		public void serializeTo(FileBuffer target) {
			if(target == null) return;
			entryCount = contents.size();
			
			target.addToFile(groupId);
			target.addToFile(entryCount);
			target.addToFile(offset);
		}
		
		public void serializeContentsTo(FileBuffer target, Map<String, Integer> pathIndexMap) {
			if(contents.isEmpty()) return;
			List<Long> idlist = new ArrayList<Long>(contents.size()+1);
			idlist.addAll(contents.keySet());
			Collections.sort(idlist);
			for(Long iid : idlist) {
				InstanceEntry ie = contents.get(iid);
				ie.serializeTo(target, pathIndexMap);
			}
		}
	
	}
	
	private static class InstanceEntry{
		public static final int SER_SIZE = 24;
		
		public long instanceId;
		public boolean compr;
		public int memSize; //Not really needed for Java impl, but important to keep
		public FileNode locData;
		
		public static InstanceEntry read(BufferReference data, List<String> pathTable) {
			if(data == null) return null;
			InstanceEntry entry = new InstanceEntry();

			int flags = Short.toUnsignedInt(data.nextShort());
			int pathIndex = (int)data.nextShort();
			int offset = data.nextInt();
			entry.instanceId = data.nextLong();
			int diskSize = data.nextInt();
			entry.memSize = data.nextInt();
			
			entry.compr = ((flags & 0x8000) != 0) || (entry.memSize > diskSize);
			entry.locData = new FileNode(null, String.format("%016x", entry.instanceId));
			
			if(pathTable != null) {
				if((pathIndex >= 0) & (pathIndex < pathTable.size())) {
					entry.locData.setSourcePath(pathTable.get(pathIndex));
				}
			}
			entry.locData.setOffset(Integer.toUnsignedLong(offset));
			entry.locData.setLength(Integer.toUnsignedLong(diskSize));
			if(entry.compr) {
				//Add compression node
				entry.locData.addTypeChainNode(new CompDefNode(MaxisLZ.getDefinition()));
			}
			
			return entry;
		}
		
		public void serializeTo(FileBuffer target, Map<String, Integer> pathIndexMap) {
			if(target == null) return;

			compr = locData.hasCompression();
			int flags = 0;
			if(compr) flags |= 0x8000;
			
			target.addToFile((short)flags);
			int pathIdx = -1;
			Integer n = pathIndexMap.get(locData.getSourcePath());
			if(n == null) {
				//Add.
				pathIdx = pathIndexMap.size();
				pathIndexMap.put(locData.getSourcePath(), pathIdx);
			}
			else pathIdx = n;
			target.addToFile((short)pathIdx);
			target.addToFile((int)locData.getOffset());
			target.addToFile(instanceId);
			target.addToFile((int)locData.getLength());
			target.addToFile(memSize);
		}
		
	}
	
	/*----- Init -----*/
	
	private MxwrResCache() {
		locMap = new HashMap<MaxisResKey, FileNode>();
		typeIndex = new HashMap<Integer, TypeIndexBlock>();
		pathTable = new ArrayList<String>(16);
	}
	
	/*----- Getters -----*/
	
	public FileNode getResourceNode(MaxisResKey key) {
		return locMap.get(key);
	}
	
	public String getPath() {return cachePath;}
	
	/*----- Setters -----*/
	
	public int indexPackage(String pkgPath, String deltaPath) throws IOException, UnsupportedFileTypeException {
		MaxisDBPFPackage pkg = MaxisDBPFPackage.read(pkgPath);
		if(deltaPath != null) pkg.loadDelta(deltaPath, false);
		
		int added = 0;
		Collection<MaxisResKey> keys = pkg.getAllResourceKeys();
		for(MaxisResKey key : keys) {
			MaxisResourceEntry entry = pkg.getResourceInfo(key);
			int typeId = key.getTypeId();
			TypeIndexBlock tib = typeIndex.get(typeId);
			if(tib == null) {
				tib = new TypeIndexBlock();
				tib.typeId = typeId;
				typeIndex.put(typeId, tib);
			}
			
			int groupId = key.getGroupId();
			GroupIndexBlock gib = tib.groupIndex.get(groupId);
			if(gib == null) {
				gib = new GroupIndexBlock();
				gib.entryCount = 0;
				gib.groupId = groupId;
				tib.groupIndex.put(groupId, gib);
			}
			
			InstanceEntry ie = new InstanceEntry();
			ie.locData = entry.getDataSource();
			ie.instanceId = key.getInstanceId();
			ie.memSize = entry.getSizeMem();
			ie.compr = (ie.memSize > (int)ie.locData.getLength());
			gib.contents.put(ie.instanceId, ie);
			
			locMap.put(key, ie.locData);
		}

		updateFilePathTable();
		return added;
	}
	
	/*----- Read -----*/
	
	public static MxwrResCache openCache(String path) throws IOException, UnsupportedFileTypeException {
		//If path file does not exist, then just spawns a new one.
		//For now, don't bother with disk caching. I'll add later.
		
		MxwrResCache cache = new MxwrResCache();
		cache.cachePath = path;
		if(!FileBuffer.fileExists(path)) return cache; //Assumed new
		
		FileBuffer input = FileBuffer.createBuffer(path, true);
		String tag = input.getASCII_string(0L, 4);
		
		//Use magic to determine byte order
		if(tag.equals("COLr")) input.setEndian(false);
		else {
			if(!tag.equals("rLOC")) throw new UnsupportedFileTypeException("MxwrResCache.openCache || Magic number not found!");
		}
		
		//Skip flags and version for now
		BufferReference pos = input.getReferenceAt(8L);
		//Skip entry count for now
		pos.add(4L);
		
		//Path table
		int pathTableEntries = pos.nextInt();
		cache.pathTable = new ArrayList<String>(pathTableEntries+4);
		pos.add(4L); //Skip size in bytes
		for(int i = 0; i < pathTableEntries; i++) {
			//Try to read raw UTF-16...
			int strBytes = Short.toUnsignedInt(pos.nextShort());
			int charCount = strBytes >>> 1;
			StringBuilder sb = new StringBuilder(charCount + 4);
			for(int j = 0; j < charCount; j++) {
				int cc = Short.toUnsignedInt(pos.nextShort());
				sb.append(Character.toChars(cc));
			}
			cache.pathTable.add(sb.toString());
			//I have a "padding to 2" field, but if it's UTF16 I'm not sure why??
		}
		//Padding to 4 bytes
		long cpos = pos.getBufferPosition();
		while((cpos & 0x3) != 0) {
			pos.add(1L); cpos++;
		}
		
		//Type offset table
		int tentries = pos.nextInt();
		for(int i = 0; i < tentries; i++) {
			TypeIndexBlock tib = TypeIndexBlock.read(pos);
			cache.typeIndex.put(tib.typeId, tib);
		}
		
		List<Integer> allTypes = new ArrayList<Integer>(tentries+1);
		allTypes.addAll(cache.typeIndex.keySet());
		for(Integer tid : allTypes) {
			TypeIndexBlock tib = cache.typeIndex.get(tid);
			pos = input.getReferenceAt(tib.offset);
			for(int j = 0; j < tib.groupCount; j++) {
				GroupIndexBlock gib = GroupIndexBlock.read(pos);
				tib.groupIndex.put(gib.groupId, gib);
			}
			for(GroupIndexBlock gib : tib.groupIndex.values()) {
				pos = input.getReferenceAt(gib.offset);
				for(int j = 0; j < gib.entryCount; j++) {
					InstanceEntry ie = InstanceEntry.read(pos, cache.pathTable);
					gib.contents.put(ie.instanceId, ie);
					MaxisResKey reskey = new MaxisResKey();
					reskey.setTypeId(tid);
					reskey.setGroupId(gib.groupId);
					reskey.setInstanceId(ie.instanceId);
					cache.locMap.put(reskey, ie.locData);
				}
			}
		}

		return cache;
	}
	
	/*----- Write -----*/
	
	private void updateFilePathTable() {
		for(FileNode fn : locMap.values()) {
			String path = fn.getSourcePath();
			if(!pathTable.contains(path)) pathTable.add(path);
		}
	}
	
	public boolean saveAndClose(boolean byteOrder) throws IOException {
		if(cachePath == null) return false;
		
		//Serialize path table
		int alloc = 0;
		int i = 0;
		updateFilePathTable();
		Map<String, Integer> pathIndexMap = new HashMap<String, Integer>();
		for(String path : pathTable) {
			alloc += (path.length() << 1) + 2;
			pathIndexMap.put(path, i++);
		}
		alloc += 4;
		
		FileBuffer fileTableSer = new FileBuffer(alloc, byteOrder);
		for(String path : pathTable) {
			int strlen = path.length();
			int byteLen = strlen << 1;
			fileTableSer.addToFile((short)byteLen);
			for(int j = 0; j < strlen; j++) {
				int cp = path.codePointAt(j);
				fileTableSer.addToFile((short)cp);
			}
		}
		while((fileTableSer.getFileSize() & 0x3L) != 0) fileTableSer.addToFile(FileBuffer.ZERO_BYTE);
		
		//Generate and output header
		long pathTblSize = fileTableSer.getFileSize();
		FileBuffer header = new FileBuffer(20, byteOrder);
		if(byteOrder) header.printASCIIToFile("rLOC");
		else header.printASCIIToFile("COLr");
		header.addToFile((short)0); //Flags (Reserved for now)
		header.addToFile((short)WRITE_VERSION);
		header.addToFile(locMap.size()); //Total entries
		header.addToFile(pathTable.size());
		header.addToFile((int)pathTblSize);
		long cpos = header.getFileSize() + pathTblSize;
		
		//Sort Type IDs
		int typeCount = typeIndex.size();
		List<Integer> allTypes = new ArrayList<Integer>(typeCount+1);
		allTypes.addAll(typeIndex.keySet());
		Collections.sort(allTypes);
		
		BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(cachePath));
		header.writeToStream(bos); header.dispose();
		fileTableSer.writeToStream(bos); fileTableSer.dispose();
		
		//Update offset and write type index
		FileBuffer buff = new FileBuffer(4, byteOrder);
		buff.addToFile(typeCount);
		buff.writeToStream(bos); buff.dispose();
		long typeTableSize = (long)typeCount * TypeIndexBlock.IDX_ENTRY_SER_SIZE + 4;
		cpos += typeTableSize;
		buff = new FileBuffer((int)typeTableSize, byteOrder);
		for(Integer tid : allTypes) {
			TypeIndexBlock tib = typeIndex.get(tid);
			cpos = tib.updateOffsets(cpos);
			tib.serializeTo(buff);
		}
		buff.writeToStream(bos); buff.dispose();
		
		//Write contents
		for(Integer tid : allTypes) {
			TypeIndexBlock tib = typeIndex.get(tid);
			alloc = (int)tib.getContentsSizeBytes();
			buff = new FileBuffer(alloc, byteOrder);
			tib.serializeContentsTo(buff, pathIndexMap);
			buff.writeToStream(bos); buff.dispose();
		}
		
		bos.close();
		return true;
	}
	
	public void writeTsv(String path) throws IOException {
		BufferedWriter bw = new BufferedWriter(new FileWriter(path));
		bw.write("#TYPE\tGROUP\tINSTANCE\tPATH\tOFFSET\tDISK_SIZE\tMEM_SIZE\n");
		
		int typeCount = typeIndex.size();
		List<Integer> allTypes = new ArrayList<Integer>(typeCount+1);
		allTypes.addAll(typeIndex.keySet());
		Collections.sort(allTypes);
		for(Integer tid : allTypes) {
			TypeIndexBlock tib = typeIndex.get(tid);
			List<Integer> groupIds = new ArrayList<Integer>(tib.groupIndex.size()+1);
			groupIds.addAll(tib.groupIndex.keySet());
			Collections.sort(groupIds);
			for(Integer gid : groupIds) {
				GroupIndexBlock gib = tib.groupIndex.get(gid);
				List<Long> iids = new ArrayList<Long>(gib.contents.size()+1);
				iids.addAll(gib.contents.keySet());
				for(Long iid : iids) {
					InstanceEntry ie = gib.contents.get(iid);
					bw.write(MaxisTypeIds.stringFromValue(tid));
					bw.write(String.format("\t0x%08x", gid));
					bw.write(String.format("\t0x%016x", iid));
					bw.write("\t" + ie.locData.getSourcePath());
					bw.write(String.format("\t0x%08x", ie.locData.getOffset()));
					bw.write(String.format("\t0x%08x", ie.locData.getLength()));
					bw.write(String.format("\t0x%08x", ie.memSize));
					bw.write("\n");
				}
			}
		}
		
		bw.close();
	}
	

}
