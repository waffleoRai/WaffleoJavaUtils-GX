package waffleoRai_Containers.nintendo.nx;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import waffleoRai_Utils.DirectoryNode;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;

public class SwitchHFS implements NXContainer{

	/*----- Constants -----*/
	
	public static final String MAGIC = "HFS0";
	
	/*----- Structs -----*/
	
	private static class FileEntry{
		
		public long offset;
		public long size;
		public String name;
		public long hashSize;
		public byte[] hash; //SHA-256
		
	}
	
	/*----- Instance Variables -----*/
	
	private String src_path;
	
	private long base_addr; //Of files...
	private List<FileEntry> filelist;
	
	private NXContainer[] contents;
	
	/*----- Construction/Parsing -----*/
	
	public SwitchHFS(long addr, int entry_count){
		base_addr = addr;
		filelist = new ArrayList<FileEntry>(entry_count+1);
		//contents = new HashMap<String, NXContainer>();
		contents = new NXContainer[entry_count];
	}

	public static SwitchHFS readHFS(FileBuffer data, long offset) throws UnsupportedFileTypeException, IOException{
		if(data == null) throw new IOException("Provided data reference is null!");
		if(data.findString(offset, offset+0x10, MAGIC) != 0L) throw new UnsupportedFileTypeException("HFS0 magic number not found!");
		
		data.setCurrentPosition(offset);
		int fcount = data.nextInt();
		long strtbl_sz = Integer.toUnsignedLong(data.nextInt());
		data.skipBytes(4);
		
		long soff = offset + (fcount * 0x40);
		long base = soff + strtbl_sz; //Base address of files...
		SwitchHFS hfs = new SwitchHFS(base, fcount);
		hfs.src_path = data.getPath();
		for(int i = 0; i < fcount; i++){
			FileEntry fe = new FileEntry();
			fe.offset = data.nextLong();
			fe.size = data.nextLong();
			long stroff = Integer.toUnsignedLong(data.nextInt());
			fe.hashSize = Integer.toUnsignedLong(data.nextInt());
			data.skipBytes(8);
			fe.hash = new byte[32];
			for(int j = 0; j < 32; j++) fe.hash[j] = data.nextByte();
			
			//Get name
			fe.name = data.getASCII_string(soff + stroff, '\0');
			
			hfs.filelist.add(fe);
		}
		
		return hfs;
	}
	
	public void readContents(){
		//TODO
	}
	
	/*----- Getters -----*/
	
	public DirectoryNode getFileTree(){
		//TODO
		return null;
	}
	
	/*----- Setters -----*/
	
	/*----- Debug -----*/
	
}
