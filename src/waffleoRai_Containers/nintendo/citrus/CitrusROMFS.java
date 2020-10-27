package waffleoRai_Containers.nintendo.citrus;

import java.io.IOException;

import waffleoRai_Utils.FileBuffer;
import waffleoRai_Files.tree.DirectoryNode;
import waffleoRai_Files.tree.FileNode;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;

//https://www.3dbrew.org/wiki/RomFS

public class CitrusROMFS {

	/*----- Constants -----*/
	
	public static final String MAGIC = "IVFC";
	
	/*----- Instance Variables -----*/
	
	//Not sure what the level 1 and 2 stuff is for, so just looking at level 3?
	//And just the file system, I guess, won't bother with the hash table?
	
	private DirectoryNode root;
	
	private byte[][] master_hash_table;
	
	private long l1_logical_off;
	private long l1_hashtable_size;
	private int l1_block_size;
	
	private long l2_logical_off;
	private long l2_hashtable_size;
	private int l2_block_size;
	
	private long l3_logical_off;
	private long l3_hashtable_size;
	private int l3_block_size;
	
	/*----- Construction/Parsing -----*/
	
	private CitrusROMFS(){}
	
	public static CitrusROMFS readROMFS(FileBuffer data, long stpos) throws UnsupportedFileTypeException, IOException{

		data.setEndian(false);
		
		//Look for "IVFC"
		long mpos = data.findString(stpos, stpos+0x10, MAGIC);
		if(mpos < 0) throw new FileBuffer.UnsupportedFileTypeException("RomFS magic number (IVFC) not found!"); 
		long cpos = mpos + 8;
		
		//Instantiate
		CitrusROMFS romfs = new CitrusROMFS();
		int master_hash_size = data.intFromFile(cpos); cpos+=4;
		
		//Parse the header
		romfs.l1_logical_off = data.longFromFile(cpos); cpos+=8;
		romfs.l1_hashtable_size = data.longFromFile(cpos); cpos+=8;
		romfs.l1_block_size = 1 << data.intFromFile(cpos); cpos+=8;
		
		romfs.l2_logical_off = data.longFromFile(cpos); cpos+=8;
		romfs.l2_hashtable_size = data.longFromFile(cpos); cpos+=8;
		romfs.l2_block_size = 1 << data.intFromFile(cpos); cpos+=8;
		
		romfs.l3_logical_off = data.longFromFile(cpos); cpos+=8;
		romfs.l3_hashtable_size = data.longFromFile(cpos); cpos+=8;
		romfs.l3_block_size = 1 << data.intFromFile(cpos); cpos+=8;
		
		//Master hash table
		cpos = mpos + 0x60;
		int shacount = master_hash_size/0x20;
		romfs.master_hash_table = new byte[shacount][0x20];
		for(int i = 0; i < shacount; i++){
			for(int j = 0; j < 0x20; j++) romfs.master_hash_table[i][j] = data.getByte(cpos++);
		}
		
		//Determine where the file data starts (I think it's the next lvl 3 block?)...
		long l3_off = mpos + romfs.l3_block_size; //Usually 0x1000?.
		cpos = l3_off + 4; //Skip header size field
		cpos += 8; //Skip dir hashtable stuff
		long dtbl_off = Integer.toUnsignedLong(data.intFromFile(cpos)); cpos+=4;
		long dtbl_sz = Integer.toUnsignedLong(data.intFromFile(cpos)); cpos+=4;
		cpos += 8; //Skip file hashtable stuff
		long ftbl_off = Integer.toUnsignedLong(data.intFromFile(cpos)); cpos+=4;
		long ftbl_sz = Integer.toUnsignedLong(data.intFromFile(cpos)); cpos+=4;
		long fdat_off = Integer.toUnsignedLong(data.intFromFile(cpos)); cpos+=4;
		
		//System.err.println("dtbl_off: 0x" + Long.toHexString(dtbl_off));
		//System.err.println("dtbl_sz: 0x" + Long.toHexString(dtbl_sz));
		//System.err.println("ftbl_off: 0x" + Long.toHexString(ftbl_off));
		//System.err.println("ftbl_sz: 0x" + Long.toHexString(ftbl_sz));
		//System.err.println("fdat_off: 0x" + Long.toHexString(fdat_off));
		
		fdat_off += l3_off; //Offset to file data start from beginning of RomFS
		dtbl_off += l3_off; ftbl_off += l3_off;
		FileBuffer dtbl = data.createReadOnlyCopy(dtbl_off, dtbl_off + dtbl_sz);
		FileBuffer ftbl = data.createReadOnlyCopy(ftbl_off, ftbl_off + ftbl_sz);
		romfs.root = buildDir(null, dtbl, ftbl, 0, fdat_off);
		dtbl.dispose();
		ftbl.dispose();
		
		return romfs;
	}
	
	private static DirectoryNode buildDir(DirectoryNode parent, FileBuffer dtbl, FileBuffer ftbl, long doff, long fdat_base_off){

		//Directory Data
		long cpos = doff + 4;
		int siboff = dtbl.intFromFile(cpos); cpos+=4;
		int dchildoff = dtbl.intFromFile(cpos); cpos+=4;
		int fchildoff = dtbl.intFromFile(cpos); cpos+=8;
		int nlen = dtbl.intFromFile(cpos); cpos += 4;
		String name = "";
		if(nlen != 0) name = dtbl.readEncoded_string("UnicodeLittleUnmarked", cpos, cpos+nlen);
		
		DirectoryNode dir = new DirectoryNode(parent, name);
		//Mark sib
		dir.setOffset(Integer.toUnsignedLong(siboff)); //A field to hold it...
		//System.err.println("Directory found: " + dir.getFullPath());
		
		//Do file children
		long nextfile = Integer.toUnsignedLong(fchildoff);
		while(nextfile != 0xffffffffL){
			cpos = nextfile + 4;
			nextfile = Integer.toUnsignedLong(ftbl.intFromFile(cpos)); cpos += 4; //File sib
			long foff = ftbl.longFromFile(cpos); cpos += 8;
			long flen = ftbl.longFromFile(cpos); cpos += 12;
			nlen = ftbl.intFromFile(cpos); cpos+=4; 
			if(nlen != 0) name = ftbl.readEncoded_string("UnicodeLittleUnmarked", cpos, cpos+nlen);
			
			foff += fdat_base_off;
			FileNode fn = new FileNode(dir, name);
			fn.setOffset(foff); fn.setLength(flen);
			
			//Debug
			//System.err.println("File found: " + fn.getFullPath() + " (" + fn.getLocationString() + ")");
		}
		
		//Do directory children
		nextfile = Integer.toUnsignedLong(dchildoff);
		while(nextfile != 0xffffffffL){
			DirectoryNode child = buildDir(dir, dtbl, ftbl, nextfile, fdat_base_off);
			nextfile = child.getOffset();
			//child.setOffset(-1L);
		}
		
		return dir;
	}
	
	/*----- Getters -----*/
	
	public long getLogicalOffset(int level){
		switch(level){
		case 1: return l1_logical_off;
		case 2: return l2_logical_off;
		case 3: return l3_logical_off;
		}
		return -1;
	}
	
	public long getLevelHashtableSize(int level){
		switch(level){
		case 1: return l1_hashtable_size;
		case 2: return l2_hashtable_size;
		case 3: return l3_hashtable_size;
		}
		return -1;
	}
	
	public int getLevelBlockSize(int level){
		switch(level){
		case 1: return l1_block_size;
		case 2: return l2_block_size;
		case 3: return l3_block_size;
		}
		return -1;
	}
	
	public DirectoryNode getFileTree(){
		return root;
	}
	
	/*----- Setters -----*/
	
	/*----- Serialization -----*/
	
	/*----- Definition -----*/
	//In case it's left as a bundled file because it can't be decrypted
	
}
