package waffleoRai_Containers.nintendo.cafe;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import waffleoRai_Containers.nintendo.wiidisc.WiiContent;
import waffleoRai_Containers.nintendo.wiidisc.WiiTMD;
import waffleoRai_Encryption.AES;
import waffleoRai_Utils.DirectoryNode;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;
import waffleoRai_Utils.FileNode;

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
	private String dec_path; //Path on local system to decrypted partition buffer
	
	private volatile long prog_pos;
	
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
			part.tree = part.fst.getTree(false);
			part.tree.setSourcePathForTree(srcPath);
			part.tree.incrementTreeOffsetsBy(partitionOffset);
		}
		
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
	public String getDecryptBufferPath(){return dec_path;}
	
	/*----- Setters -----*/
	
	public void setPartitionName(String s){name = s;}
	
	public void setDecryptBufferPath(String path){
		dec_path = path;
	}
	
	/*----- Crypto -----*/

	public void decryptToBuffer(CafeCryptListener l) throws UnsupportedFileTypeException, IOException{
		
		if(dec_path == null) throw new UnsupportedFileTypeException("Decryption failed: Decryption target path not set!");
		if(src_path == null) throw new UnsupportedFileTypeException("Decryption failed: WUD source path not set!");
		if(part_key == null) throw new UnsupportedFileTypeException("Decryption failed: Partition key not set!");
		
		//First, dump the fst to a separate file
		String decdir = FileBuffer.chopPathToDir(dec_path);
		String fst_outpath = decdir + File.separator + "fst_" + name + ".bin";
		FileBuffer fstbuff = loadFST();
		if(fstbuff != null){
			fstbuff.writeFile(fst_outpath);
			if(fst == null) fst = CafeFST.readWiiUFST(fstbuff, 0);
		}
		
		//Now, decrypt files...
		AES aes = new AES(part_key);
		tree = fst.getTree(true);
		tree.setSourcePathForTree(dec_path);
		List<FileNode> list = fst.getList(false);
		if(l != null) l.setFileCount(list.size());
		
		//Calculate partition size to send to listener...
		if(l != null){
			long total_size = 0;
			for(FileNode fn : list) total_size += fn.getLength();
			if(l != null) l.setPartitionSize(total_size);
		}

		//fst.printToStderr();
		
		Runnable worker = new Runnable(){

			public void run() {
				
				try{
					prog_pos = 0L;
					int fidx = 0;
					
					BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(dec_path));
					//long pos = 0; //DEBUG
					for(FileNode fn : list){
						//DEBUG
						//System.err.println("Decrypting " + fn.getFullPath() + " to 0x" + Long.toHexString(pos));
						//System.err.println("Partition offset: 0x" + Long.toHexString(fn.getOffset()));
						if(l != null) l.onFileStart(fn.getFullPath());
						
						//List has raw offsets...
						//Open stream
						long offset = fn.getOffset(); //Offset from partition start.
						offset += wud_offset; //Offset from wud start
						BufferedInputStream bis = new BufferedInputStream(new FileInputStream(src_path));
						bis.skip(offset);
						
						//System.err.println("WUD offset: 0x" + Long.toHexString(offset));
						//System.err.println("Length: 0x" + Long.toHexString(fn.getLength()));
						
						//Get IV
						//long ivlo = fn.getOffset() >>> 16;
						String ivlo_str = fn.getMetadataValue(CafeFST.METAKEY_IVLO);
						long ivlo = Long.parseUnsignedLong(ivlo_str, 16);
						byte[] iv = new byte[16];
						long mask = 0xFFL << 56;
						int shift = 56;
						for(int i = 0; i < 8; i++){
							iv[i+8] = (byte)((ivlo & mask) >>> shift);
							mask = mask >>> 8;
							shift -= 8;
						}
						aes.initDecrypt(iv);
						
						//Decrypt to buffer
						long len = fn.getLength();
						long remain = len;
						boolean last = false;
						while(remain > 0){
							int bsz = 0x8000;
							if(remain <= bsz){
								bsz = (int)remain;
								last = true;
							}
							while((bsz % 0x10) != 0) bsz++;
							byte[] enc = new byte[bsz];
							bis.read(enc);
							
							byte[] dec = aes.decryptBlock(enc, last);
							bos.write(dec);
							
							remain -= bsz;
							//pos+=bsz;
							prog_pos += bsz;
						}
						bis.close();
						if(l != null) {l.onFileComplete(fidx++);}
					}
					bos.close();	
				}
				catch(IOException x){
					x.printStackTrace();
					return;
				}	
			}
			
		};
		
		Thread worker_thread = new Thread(worker);
		worker_thread.setName("WUDPartition_Decrypt_Worker");
		worker_thread.setDaemon(true);
		worker_thread.start();
		
		long sleeplen = 5000;
		if(l != null) sleeplen = l.getUpdateInterval();
		while(worker_thread.isAlive()){
			if(l != null) l.setCurrentPosition(prog_pos);
			try {Thread.sleep(sleeplen);} 
			catch (InterruptedException e) {
				System.err.println("Unexpected interrupt! Ignoring...");
				e.printStackTrace();
			}
		}
		
		
	}
	
}
