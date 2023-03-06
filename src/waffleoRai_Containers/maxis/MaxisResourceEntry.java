package waffleoRai_Containers.maxis;

import java.io.IOException;

import waffleoRai_Compression.definitions.CompDefNode;
import waffleoRai_Compression.lz77.MaxisLZ;
import waffleoRai_Files.tree.DirectoryNode;
import waffleoRai_Files.tree.FileNode;
import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.FileBuffer;

public class MaxisResourceEntry {
	
	public static final int COMPFLAGS_NOCOMP = 0x10000;
	public static final int COMPFLAGS_LZSS = 0x1ffff;

	private MaxisResKey key;
	
	private int offset = -1;
	private int sizeDisk = 0;
	private int sizeMem = 0; //Checks sizes to see if compressed.
	
	private String name;
	
	//For writing
	private FileNode data_source;
	private boolean compress;
	
	public MaxisResourceEntry(){
		key = new MaxisResKey();
	}
	
	public MaxisResKey getKey(){return key;}
	public int getOffset(){return offset;}
	public int getSizeDisk(){return sizeDisk;}
	public int getSizeMem(){return sizeMem;}
	public String getName(){return name;}
	public boolean isCompressed(){return sizeMem > sizeDisk;}
	public FileNode getWriteDataSource(){return data_source;}
	public boolean compressOnWrite(){return compress;}
	
	public void setOffset(int val){offset = val;}
	public void setSizeDisk(int val){sizeDisk = val;}
	public void setSizeMem(int val){sizeMem = val;}
	public void setName(String val){name = val;}
	
	public void setWritableDataSource(FileNode src){data_source = src;}
	public void setCompressOnWrite(boolean b){compress = b;}
	
	public FileNode generateFileNode(DirectoryNode parent, String pkgpath){
		String usename = name;
		if(name == null){
			usename = String.format("%08x_%08x_%016x", key.getTypeId(), key.getGroupId(), key.getInstanceId());
		}
		FileNode fn = new FileNode(parent, usename);
		fn.setSourcePath(pkgpath);
		fn.setOffset(offset);
		fn.setLength(sizeDisk);
		if(sizeMem > sizeDisk){
			fn.addTypeChainNode(new CompDefNode(MaxisLZ.getDefinition()));
		}
		return fn;
	}
	
	public FileBuffer loadData(String pkgpath) throws IOException{
		FileBuffer dat = FileBuffer.createBuffer(pkgpath, offset, offset+sizeDisk, false);
		if(sizeMem > sizeDisk){
			//Decompress
			dat = MaxisLZ.decompress(dat);
		}
		return dat;
	}
	
	public static MaxisResourceEntry readFromIndex(BufferReference input, int inclFlags){
		int mask = 0x1;
		int[] words = new int[8];
		MaxisResourceEntry entry = new MaxisResourceEntry();
		for(int i = 0; i < 8; i++){
			if((inclFlags & mask) != 0){
				words[i] = input.nextInt();
			}
			mask <<= 1;
		}
		
		entry.key.setTypeId(words[0]);
		entry.key.setGroupId(words[1]);
		long instanceId = Integer.toUnsignedLong(words[2]) << 32;
		instanceId |= Integer.toUnsignedLong(words[3]);
		entry.key.setInstanceId(instanceId);
		entry.offset = words[4];
		entry.sizeDisk = words[5] & 0x7fffffff;
		entry.sizeMem = words[6];
		
		//Honestly, don't really see the point in reading the compression flag?
		return entry;
	}
	
	public int writeToIndex(FileBuffer target, int inclFlags){
		int mask = 0x1;
		int written = 0;
		
		if((mask & inclFlags) != 0) {
			target.addToFile(key.getTypeId());
			written += 4;
		}
		mask <<= 1;
		
		if((mask & inclFlags) != 0) {
			target.addToFile(key.getGroupId());
			written += 4;
		}
		mask <<= 1;
		
		if((mask & inclFlags) != 0) {
			int insthi = (int)(key.getInstanceId() >>> 32);
			target.addToFile(insthi);
			written += 4;
		}
		mask <<= 1;
		
		if((mask & inclFlags) != 0) {
			int instlo = (int)(key.getInstanceId() & 0xffffffffL);
			target.addToFile(instlo);
			written += 4;
		}
		mask <<= 1;
		
		if((mask & inclFlags) != 0) {
			target.addToFile(offset);
			written += 4;
		}
		mask <<= 1;
		
		if((mask & inclFlags) != 0) {
			target.addToFile(sizeDisk | 0x80000000);
			written += 4;
		}
		mask <<= 1;
		
		
		if((mask & inclFlags) != 0) {
			target.addToFile(sizeMem);
			written += 4;
		}
		mask <<= 1;
		
		if((mask & inclFlags) != 0) {
			if(sizeMem > sizeDisk) target.addToFile(COMPFLAGS_LZSS);
			else target.addToFile(COMPFLAGS_NOCOMP);
			written += 4;
		}
		
		return written;
	}
	
}
