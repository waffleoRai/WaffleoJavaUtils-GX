package waffleoRai_Containers.nintendo.revolution;

import java.io.IOException;
import java.util.ArrayList;

import waffleoRai_Files.FragFileNode;
import waffleoRai_Utils.DirectoryNode;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.MultiFileBuffer;

//https://wiibrew.org/wiki/Hardware/NAND

public class WiiNAND {
	
	//TODO
	/*
	 * Class for reading a BackupMii Wii NAND image.
	 */
	
	/*----- Constants -----*/
	
	public static final int PAGE_SIZE = 2048;
	public static final int ECC_SIZE = 64;
	public static final int TOTAL_PAGE_SIZE = PAGE_SIZE + ECC_SIZE;
	
	public static final int PAGES_PER_BLOCK = 8;
	public static final int BLOCKS_PER_CLUSTER = 8;
	public static final int CLUSTER_COUNT = 4096;
	
	public static final int BLOCK_COUNT = CLUSTER_COUNT * BLOCKS_PER_CLUSTER;
	
	public static final int TOTAL_BLOCK_SIZE = TOTAL_PAGE_SIZE * PAGES_PER_BLOCK;
	public static final int TOTAL_CLUSTER_SIZE = TOTAL_BLOCK_SIZE * BLOCKS_PER_CLUSTER;
	
	public static final int DATA_OFFSET_BLOCK = 0x40; //First cluster of FS data
	public static final int FS_OFFSET_BLOCK = 0x7F00; //First cluster of FS metadata
	
	public static final int FS_OFFSET = FS_OFFSET_BLOCK * TOTAL_BLOCK_SIZE;
	public static final int FS_SUPERBLOCKS = 16;
	public static final int FS_BLOCKS_PER_SUPERBLOCK = 16;
	public static final int FS_SUPERBLOCKSIZE = FS_BLOCKS_PER_SUPERBLOCK * TOTAL_BLOCK_SIZE;
	
	public static final int FS_FAT_OFFSET = 0xc;
	public static final short FS_FAT_ENDMARKER = (short)0xFFFB;
	
	public static final int TOTAL_SIZE = CLUSTER_COUNT * TOTAL_CLUSTER_SIZE;
	
	/*----- Instance Variables -----*/
	
	private String src_path;
	private String temp_buffer_path;
	
	private FileBuffer src_file;
	
	private DirectoryNode root;
	
	/*----- Structs -----*/
	
	public static class FSTEntry{
		public String name;
		public boolean isDir;
		public short sub;
		public short sib;
		public int len;
		public int uid;
		public short gid;
	}
	
	/*----- Construction/Parsing -----*/
	
	public void decryptData(){
		//TODO
	}
	
	private void readFileSystem() throws IOException{
		//Scan each of the 16 FS superblocks to determine which is most recent.
		FileBuffer data = getSourceFile();
		int sb_idx = 0;
		int max = 0;
		for(int i = 0; i < FS_SUPERBLOCKS; i++){
			long sboff = getOffsetOfFSSuperblock(i);
			//Check the generation number
			int gennum = data.intFromFile(sboff + 4);
			if(gennum > max){
				sb_idx = i;
				max = gennum;
			}
		}
		
		//Read from the specified superblock...
		root = readFSSuperblock(data, sb_idx);
		root.setSourcePathForTree(src_path);
	}
	
	private static FileBuffer stripECC(FileBuffer in){
		int pgs = (int)in.getFileSize()/TOTAL_PAGE_SIZE;
		MultiFileBuffer out = new MultiFileBuffer(pgs);
		long off = 0;
		for(int i = 0; i < pgs; i++){
			out.addToFile(in.createReadOnlyCopy(off, off+PAGE_SIZE));
			off += TOTAL_PAGE_SIZE;
		}
		
		return out;
	}
	
	private static void readFSTDirectory(ArrayList<FSTEntry> fst, short[] raw_fat, DirectoryNode dir, int dir_fst_idx){

		FSTEntry dentry = fst.get(dir_fst_idx);
		int next_child = (int)dentry.sub;
		while(next_child != -1){
			//break when sib is -1
			FSTEntry centry = fst.get(next_child);
			if(centry.isDir){
				DirectoryNode cdir = new DirectoryNode(dir, centry.name);
				readFSTDirectory(fst, raw_fat, cdir, next_child);
			}
			else{
				FragFileNode child = new FragFileNode(dir, centry.name);
				//Read from FAT...
				short next_block = centry.sub;
				while(next_block != FS_FAT_ENDMARKER){
					//Split into 8 pages...
					long boff = getOffsetOfBlock(next_block);
					for(int i = 0; i < PAGES_PER_BLOCK; i++){
						child.addBlock(boff, PAGE_SIZE);
						boff += TOTAL_PAGE_SIZE;
					}
					//Advance to next block...
					next_block = raw_fat[next_block];
				}
			}
			next_child = (int)centry.sib;
		}
		
	}
	
	private static DirectoryNode readFSSuperblock(FileBuffer rawdata, int sb_idx) throws IOException{
		//Strip ECC
		long sb_off = getOffsetOfFSSuperblock(sb_idx);
		FileBuffer sb = rawdata.createReadOnlyCopy(sb_off, sb_off+FS_SUPERBLOCKSIZE);
		FileBuffer data = stripECC(sb);
		
		short[] raw_fat = new short[BLOCK_COUNT];
		
		//Read FAT
		data.setCurrentPosition(FS_FAT_OFFSET);
		for(int i = 0; i < BLOCK_COUNT; i++){
			raw_fat[i] = data.nextShort();
		}
		
		//Read the FST
		//Scan down until entry starts with '\0'
		//File names appear to be ASCII?
		ArrayList<FSTEntry> fst = new ArrayList<FSTEntry>(8192);
		boolean fstend = false;
		while(!fstend){
			FSTEntry e = new FSTEntry();
			
			//Check the name...
			long nowoff = data.getCurrentPosition();
			e.name = data.getASCII_string(nowoff, 12);
			if(e.name == null || e.name.isEmpty()) {fstend = true; break;}
			data.skipBytes(12);
			
			int mode = Byte.toUnsignedInt(data.nextByte());
			if((mode & 0x3) == 2) e.isDir = true;
			data.nextByte();
			e.sub = data.nextShort();
			e.sib = data.nextShort();
			e.len = data.nextInt();
			e.uid = data.nextInt();
			e.gid = data.nextShort();
			data.nextInt();
			
			fst.add(e);
		}
		
		//Piece back together into a tree
		//Find root.
		DirectoryNode root = new DirectoryNode(null, "");
		int idx = 0;
		while(!fst.get(idx).name.equals("/")) idx++;
		readFSTDirectory(fst, raw_fat, root, idx);
		
		//Clean up
		data.dispose();
		sb.dispose();
		
		return null;
	}
	
	/*----- Keys -----*/
	
	/*----- Address Calculation -----*/
	
	public static long getOffsetOfCluster(int cluster_idx){
		return cluster_idx * TOTAL_CLUSTER_SIZE;
	}

	public static long getOffsetOfBlock(int block_idx){
		return block_idx * TOTAL_BLOCK_SIZE;
	}
	
	public static long getOffsetOfFSSuperblock(int sb_idx){
		return FS_OFFSET + (sb_idx * FS_SUPERBLOCKSIZE);
	}
	
	/*----- Memory/File Access Management -----*/
	
	private FileBuffer getSourceFile() throws IOException{
		if(src_file == null){
			src_file = FileBuffer.createBuffer(src_path, true);
		}
		return src_file;
	}
	
	public void close(){
		//TODO
		//Frees source file buffer & deletes temp buffer
	}
	
}
