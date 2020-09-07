package waffleoRai_Containers.nintendo.cafe;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.List;

import waffleoRai_Containers.nintendo.wiidisc.WiiContent;
import waffleoRai_Containers.nintendo.wiidisc.WiiTMD;
import waffleoRai_Encryption.AES;
import waffleoRai_Files.tree.DirectoryNode;
import waffleoRai_Files.tree.FileNode;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;

public class WudPartition {
	
	/*----- Constants -----*/
	
	public static final int PART_TYPE_SI = 0;
	public static final int PART_TYPE_GM = 1;
	
	/*----- Instance Variables -----*/
	
	private byte[] part_key;
	private WiiTMD tmd;
	
	private String name;
	private long wud_offset; //Offset of partition in container
	
	private CafeFST fst;
	private DirectoryNode tree; //Relative to decrypted partition buffer
	
	private String src_path; //Path on local system to source container
	private String dec_stem; //Path on local system to decrypted partition buffer
	
	//private volatile long prog_pos;
	
	/*----- Construction/Parsing -----*/
	
	private WudPartition(){}
	
	public static WudPartition readWUDPartition(String srcPath, long partitionOffset, byte[] key, WiiTMD tmd) throws IOException, UnsupportedFileTypeException{
		//Checks. If can't decrypt, then nothing really to do?
		if(srcPath == null) return null;
		if(key == null) throw new FileBuffer.UnsupportedFileTypeException("Key is required for partition file system decryption!");
		
		//TMD can be null - use null TMD for partitions like SI
		
		WudPartition part = new WudPartition();
		part.part_key = key;
		part.src_path = srcPath;
		part.wud_offset = partitionOffset;
		part.tmd = tmd;
		
		//Header block is plaintext?
		//Assume that FST starts at 0x8000?
		//Decrypt the FST - should be small enough to just keep in memory?
		FileBuffer fst_buff = part.loadFST();

		//Read FST
		if(fst_buff != null){
			part.fst = CafeFST.readWiiUFST(fst_buff, 0);
			
			//Set tree to encrypted version...
			//part.tree = part.fst.getTree(false);
			part.tree = part.fst.getTree();
			//part.tree.setSourcePathForTree(srcPath);
			//part.tree.incrementTreeOffsetsBy(partitionOffset);
		}
		
		return part;
	}
	
	public static WudPartition loadPredecedPartition(String wudpath, long part_offset, byte[] key, WiiTMD tmd, String pname, String decdir) throws UnsupportedFileTypeException, IOException{

		if(decdir == null) throw new UnsupportedFileTypeException("Load failed: Decryption target path not set!");
		if(key == null) throw new UnsupportedFileTypeException("Load failed: WUD unique key not set!");
		if(wudpath == null) throw new UnsupportedFileTypeException("Load failed: WUD source path not set!");
		
		WudPartition part = new WudPartition();
		part.part_key = key;
		part.src_path = wudpath;
		part.wud_offset = part_offset;
		part.tmd = tmd;
		part.name = pname;
		part.dec_stem = decdir + File.separator + part.name;
		
		//FST
		String fst_path = decdir + File.separator + "fst_" + part.name + ".bin";
		FileBuffer fst_file = null;
		if (FileBuffer.fileExists(fst_path)) fst_file = FileBuffer.createBuffer(fst_path, true);
		else{
			if(WiiUDisc.getCommonKey() == null) throw new UnsupportedFileTypeException("Load failed: Wii U common key not set!");
			fst_file = part.loadFST();
		}
		if(fst_file == null) throw new UnsupportedFileTypeException("Load failed: FST could not be decrypted!");
		part.fst = CafeFST.readWiiUFST(fst_file, 0);
		
		part.updateTreePaths();
		
		return part;
	}
	
	private FileBuffer loadFST() throws IOException{

		long fst_buff_size = 0x8000;
		byte[] fst_iv = new byte[16];
		if(tmd != null){
			WiiContent c0 = tmd.getContent(0);
			fst_buff_size = c0.getSize();
			int idx = c0.getIndex();
			fst_iv[0] = (byte)((idx >>> 8) & 0xFF);
			fst_iv[1] = (byte)(idx & 0xFF);
		}
		FileBuffer fst_buff = new FileBuffer((int)fst_buff_size, true);
		int blocks = (int)(fst_buff_size/0x8000);
		AES aes = new AES(part_key);
		aes.initDecrypt(fst_iv);
		
		BufferedInputStream bis = new BufferedInputStream(new FileInputStream(src_path));
		bis.skip(wud_offset + 0x8000);
		byte[] enc = new byte[0x8000];
		for(int b = 0; b < blocks; b++){
			bis.read(enc);
			byte[] dec = aes.decryptBlock(enc, b == blocks-1);
			for(int i = 0; i < dec.length; i++) fst_buff.addToFile(dec[i]);
		}
		bis.close();
		
		return fst_buff;
	}
	
	/*----- Getters -----*/
	
	public String getName(){return name;}
	public long getWUDOffset(){return wud_offset;}
	public byte[] getPartitionKey(){return part_key;}
	public WiiTMD getTMD(){return tmd;}
	public CafeFST getFST(){return fst;}
	public DirectoryNode getTree(){return tree;}
	public String getWUDPath(){return src_path;}
	public String getDecryptBufferPathStem(){return dec_stem;}
	
	/*----- Setters -----*/
	
	public void setPartitionName(String s){name = s;}
	
	public void setDecryptBufferPathStem(String path){
		dec_stem = path;
	}
	
	/*----- Tree -----*/
	
	private void updateTreePathsDir(DirectoryNode dir){
		
		List<FileNode> children = dir.getChildren();
		for(FileNode child : children){
			if(child instanceof DirectoryNode){
				updateTreePathsDir((DirectoryNode)child);
			}
			else{
				String spath = dec_stem + "_" + child.getSourcePath() + ".bin";
				child.setSourcePath(spath);
			}
		}
		
	}
	
	private void updateTreePaths(){
		tree = fst.getTree();
		updateTreePathsDir(tree);
	}
	
	/*----- Crypto -----*/
	
	protected void decryptCluster(int cidx, CafeCryptListener l) throws IOException{
		if(l != null) l.onClusterStart(cidx);
		
		//System.err.println("Partition key: " + CitrusNCC.printHash(part_key));];
		int flags = 0;
		
		if(tmd != null){
			flags = tmd.getContent(cidx).getType();
		}
		boolean hashflag = (flags & 0x02) != 0;
		if(hashflag) decryptHashCluster(cidx, l);
		else decryptHashlessCluster(cidx, l);
	}

	private void decryptHashlessCluster(int cidx, CafeCryptListener l) throws IOException{

		AES aes = new AES(part_key);
		long offset = fst.getClusterOffset(cidx);
		long size = fst.getClusterSize(cidx);
		int content_index = 0;
		
		if(tmd != null){
			content_index = tmd.getContent(cidx).getIndex(); //Used for IV???
			size = tmd.getContent(cidx).getSize();
		}
		if (size < 1) return;
		
		byte[] iv = new byte[16];
		iv[0] = (byte)((content_index >>> 8) & 0xFF);
		iv[1] = (byte)(content_index & 0xFF);
		aes.initDecrypt(iv);
		
		String stem = this.dec_stem + "_" + String.format("%04d", cidx);
		String cpath = stem + ".bin";
		BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(cpath));
		BufferedInputStream bis = new BufferedInputStream(new FileInputStream(src_path));
		bis.skip(wud_offset);
		bis.skip(offset);
		//System.err.println("Total Cluster offset: 0x" + Long.toHexString(wud_offset + offset));
		
		long remain = size;
		while((remain % 0x10) != 0) remain++;
		if(l != null){l.setClusterSize(remain); l.setClusterPosition(0L);}
		boolean last = false;
		long pos = 0;
		while(remain > 0){
			
			int bsz = 0x8000;
			if(remain <= bsz){
				bsz = (int)remain;
				last = true;
			}
			
			byte[] enc = new byte[bsz];
			bis.read(enc);
			byte[] dec = aes.decryptBlock(enc, last);
			bos.write(dec);
			
			remain -= bsz;
			pos += bsz;
			if(l != null) l.setClusterPosition(pos);
		}
		bis.close();
		bos.close();
		
	}
	
	private void decryptHashCluster(int cidx, CafeCryptListener l) throws IOException{
		
		AES aes = new AES(part_key);
		long offset = fst.getClusterOffset(cidx);
		long size = fst.getClusterSize(cidx);
		boolean allgood = true;
		
		if(tmd != null){
			size = tmd.getContent(cidx).getSize();
		}
		if (size < 1) return;

		String stem = this.dec_stem + "_" + String.format("%04d", cidx);
		
		//TODO copy h3
		//String h3path = stem + "_h3.bin";
		
		String cpath = stem + ".bin";
		BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(cpath));
		BufferedInputStream bis = new BufferedInputStream(new FileInputStream(src_path));
		bis.skip(wud_offset);
		bis.skip(offset);
		//System.err.println("Total Cluster offset: 0x" + Long.toHexString(wud_offset + offset));
		
		long remain = size;
		while((remain % 0x10) != 0) remain++;
		if(l != null){l.setClusterSize(remain); l.setClusterPosition(0L);}
		long pos = 0;
		int h0_idx = 0;
		while(remain > 0){
			
			int bsz = 0x10000;

			//Read the hash table
			byte[] iv = new byte[16];
			byte[] htenc = new byte[0x400];
			bis.read(htenc);
			byte[] htdec = aes.decrypt(iv, htenc);
			
			//Get expected hash for block and IV
			int h0_off = h0_idx * 20;
			byte[] h0 = new byte[20];
			for(int i = 0; i < 20; i++) h0[i] = htdec[h0_off + i];
			for(int i = 0; i < 16; i++) iv[i] = h0[i]; //Dunno if that's true.
			
			//DEBUG---
			//bos.write(htdec);
			
			//Do data
			byte[] enc = new byte[bsz-0x400];
			bis.read(enc);
			byte[] dec = aes.decrypt(iv, enc);
			
			//Check hash
			try{
				MessageDigest sha = MessageDigest.getInstance("SHA1");
				sha.update(dec);
				byte[] hashbuff = sha.digest();
				if(!MessageDigest.isEqual(h0, hashbuff)){
					//I'll suppress the warning for now because I still don't know the IV...
					//System.err.println("WARNING: Section @ 0x" + Long.toHexString(pos) + " failed h0 hash check --");
					allgood = false;
				}
			}
			catch(Exception x){
				x.printStackTrace();
			}
			bos.write(dec);
			
			//Advance position
			remain -= bsz;
			pos += bsz;
			if(l != null) l.setClusterPosition(pos);
			if(++h0_idx >= 16) h0_idx = 0;
		}
		bis.close();
		bos.close();
		
		if(!allgood) System.err.println("WARNING: At least one section in this cluster did not pass h0 hash check!");
	}
	
	public void decryptToBuffer(CafeCryptListener l) throws UnsupportedFileTypeException, IOException{
		
		if(dec_stem == null) throw new UnsupportedFileTypeException("Decryption failed: Decryption target path not set!");
		if(src_path == null) throw new UnsupportedFileTypeException("Decryption failed: WUD source path not set!");
		if(part_key == null) throw new UnsupportedFileTypeException("Decryption failed: Partition key not set!");
		
		//First, dump the fst to a separate file
		String decdir = FileBuffer.chopPathToDir(dec_stem);
		String fst_outpath = decdir + File.separator + "fst_" + name + ".bin";
		FileBuffer fstbuff = loadFST();
		if(fstbuff != null){
			fstbuff.writeFile(fst_outpath);
			if(fst == null) fst = CafeFST.readWiiUFST(fstbuff, 0);
		}
		
		//Count the clusters...
		int ccount = fst.getClusterCount();
		if(l != null) l.setClusterCount(ccount);
		
		//Now, decrypt files...
		for(int i = 1; i < ccount; i++){
			decryptCluster(i, l);
		}
		
		this.updateTreePaths();
	}
	
	public static String printHash(byte[] hash){
		if(hash == null) return "<NULL>";
		
		int chars = hash.length << 1;
		StringBuilder sb = new StringBuilder(chars+2);
		
		for(int i = 0; i < hash.length; i++) sb.append(String.format("%02x", hash[i]));
		
		return sb.toString();
	}
	
}
