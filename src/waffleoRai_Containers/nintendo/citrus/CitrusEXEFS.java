package waffleoRai_Containers.nintendo.citrus;

import waffleoRai_Utils.DirectoryNode;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileNode;

public class CitrusEXEFS {
	
	/*----- Constants -----*/
	
	/*----- Instance Variables -----*/
	
	private String[] names;
	private long[] offsets;
	private long[] lengths;
	private byte[][] hashes;
	
	/*----- Construction/Parsing -----*/
	
	private CitrusEXEFS(){
		names = new String[10];
		offsets = new long[10];
		lengths = new long[10];
		hashes = new byte[10][0x20];
		
	};
	
	public static CitrusEXEFS readExeFSHeader(FileBuffer data, long offset){

		CitrusEXEFS exefs = new CitrusEXEFS();
		data.setEndian(false);
		
		long cpos = offset;
		for(int i = 0; i < 10; i++){
			byte check = data.getByte(cpos);
			if(check == 0) {cpos += 16; continue;}
			
			exefs.names[i] = data.getASCII_string(cpos, 8); cpos += 8;
			exefs.offsets[i] = Integer.toUnsignedLong(data.intFromFile(cpos)); cpos+=4;
			exefs.lengths[i] = Integer.toUnsignedLong(data.intFromFile(cpos)); cpos+=4;
		}
		
		cpos += 0x20;
		
		//Remember, hashes are stored in reverse order...
		for(int i = 9; i >= 0; i--){
			for(int j = 0; j < 0x20; j++) exefs.hashes[i][j] = data.getByte(cpos++);
		}
		
		return exefs;
	}
	
	/*----- Getters -----*/
	
	public DirectoryNode getFileTree(){
		DirectoryNode root = new DirectoryNode(null, "ExeFS");
		
		for(int i = 0; i < 10; i++){
			String name = names[i];
			if(name == null || name.isEmpty()) continue;
			FileNode fn = new FileNode(root, name);
			fn.setOffset(offsets[i]);
			fn.setLength(lengths[i]);
		}
		
		return root;
	}
	
	public byte[] getFileHash(String filename){

		for(int i = 0; i < 10; i++){
			if(names[i] == null) continue;
			if(names[i].equalsIgnoreCase(filename)) return hashes[i];
		}
		
		return null;
	}
	
	/*----- Setters -----*/

}
