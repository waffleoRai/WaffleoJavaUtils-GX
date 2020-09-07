package waffleoRai_Containers.nintendo.nx;

import java.io.IOException;
import java.io.Writer;
import java.security.MessageDigest;
import java.util.Collection;

import waffleoRai_Encryption.FileCryptRecord;
import waffleoRai_Encryption.nintendo.NinCryptTable;
import waffleoRai_Files.FileTypeDefNode;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;
import waffleoRai_fdefs.nintendo.NXSysDefs;
import waffleoRai_Files.tree.DirectoryNode;
import waffleoRai_Files.tree.FileNode;

public class NXCartImage {
	
	/*----- Constants -----*/
	
	public static final String MAGIC_CARTHEAD = "HEAD";
	
	public static final int SECMODE_T1 = 1;
	public static final int SECMODE_T2 = 2;
	
	public static final int CARTSIZE_1GB = 0xFA;
	public static final int CARTSIZE_2GB = 0xF8;
	public static final int CARTSIZE_4GB = 0xF0;
	public static final int CARTSIZE_8GB = 0xE0;
	public static final int CARTSIZE_16GB = 0xE1;
	public static final int CARTSIZE_32GB = 0xE2;
	
	public static final int TREEBUILD_COMPLEXITY_ALL = 0;
	public static final int TREEBUILD_COMPLEXITY_NORMAL = 1;
	public static final int TREEBUILD_COMPLEXITY_MERGED = 2;
	
	/*----- Instance Variables -----*/
	
	private String src_path;
	//private String dec_dir;
	
	private byte[] rsa_sig; //For cart HEAD
	
	private long secure_addr;
	private long backup_addr;
	
	private int idx_tkd; //TitleKeyDec Index
	private int idx_kek;
	
	private int cart_size; //Enum
	private int header_ver;
	private int cart_flags;
	
	private long packageID;
	private long data_end_addr; //Valid data end address
	private long normal_end_addr;
	private byte[] gc_info_iv;
	
	private long hfs0_off;
	private long hfs0_headsize;
	
	private byte[] sha_hfshead;
	private byte[] sha_initdat;
	
	private int security_mode; //Enum
	private int idx_t1k; //T1 Key index
	private int idx_key; //Key Index
	
	//GC Info (Encrypted) -- Add fields if I feel like it
	
	//Contents
	
	private SwitchHFS rootHFS;
	
	/*----- Construction/Parsing -----*/
	
	private NXCartImage(){}
	
	public static NXCartImage readXCI(String path) throws IOException, UnsupportedFileTypeException{
	
		FileBuffer dat = FileBuffer.createBuffer(path, false);
		dat.setCurrentPosition(0L);
		
		//Check for "HEAD" magic
		if(dat.findString(0x100, 0x110, MAGIC_CARTHEAD) != 0x100) throw new UnsupportedFileTypeException("NX Cart HEAD magic not found!");
		
		NXCartImage xci = new NXCartImage();
		xci.src_path = path;
		
		xci.rsa_sig = new byte[0x100];
		for (int i = 0; i < 0x100; i++) xci.rsa_sig[i] = dat.nextByte();
		dat.skipBytes(4L);
		xci.secure_addr = Integer.toUnsignedLong(dat.nextInt());
		xci.backup_addr = Integer.toUnsignedLong(dat.nextInt());
		int val = Byte.toUnsignedInt(dat.nextByte());
		xci.idx_tkd = (val >>> 4) & 0xF;
		xci.idx_kek = val & 0xF;
		xci.cart_size = Byte.toUnsignedInt(dat.nextByte());
		xci.header_ver = Byte.toUnsignedInt(dat.nextByte());
		xci.cart_flags = Byte.toUnsignedInt(dat.nextByte());
		xci.packageID = dat.nextLong();
		xci.data_end_addr = dat.nextLong() << 9;
		xci.gc_info_iv = new byte[16];
		for(int i = 15; i >= 0; i--) xci.gc_info_iv[i] = dat.nextByte();
		xci.hfs0_off = dat.nextLong();
		xci.hfs0_headsize = dat.nextLong();
		
		xci.sha_hfshead = new byte[0x20];
		for(int i = 0; i < 0x20; i++) xci.sha_hfshead[i] = dat.nextByte();
		xci.sha_initdat = new byte[0x20];
		for(int i = 0; i < 0x20; i++) xci.sha_initdat[i] = dat.nextByte();
		
		xci.security_mode = dat.nextInt();
		xci.idx_t1k = dat.nextInt();
		xci.idx_key = dat.nextInt();
		xci.normal_end_addr = dat.nextInt() << 9;
		
		//Check HFS header hash
		try{
			MessageDigest sha = MessageDigest.getInstance("SHA-256");
			long st = xci.hfs0_off; long ed = st + xci.hfs0_headsize;
			sha.update(dat.getBytes(st, ed));
			byte[] hfs_head_sha = sha.digest();
			if (!MessageDigest.isEqual(hfs_head_sha, xci.sha_hfshead)){
				System.err.println("HFS0 Header Checksum (0x" + Long.toHexString(st) + " - 0x" +
								Long.toHexString(ed) + "): " + NXCrypt.printHash(hfs_head_sha));
				System.err.println("Expected Checksum: " + NXCrypt.printHash(xci.sha_hfshead));
				throw new FileBuffer.UnsupportedFileTypeException("Root HFS header SHA-256 does not match!");
			}
		}
		catch(Exception x){
			x.printStackTrace();
			throw new FileBuffer.UnsupportedFileTypeException("Root HFS header checksum failed!");
		}
		
		//Parse root HFS header
		//System.err.println("NXCartImage.readXCI || Now parsing root HFS @ 0x" + Long.toHexString(xci.hfs0_off));
		xci.rootHFS = SwitchHFS.readHFS(dat, xci.hfs0_off);
		
		return xci;
	}
	
	/*----- Getters -----*/
	
	public DirectoryNode getFileTree(NXCrypt cryptstate, int complexity_level) throws IOException{
		unlock(cryptstate);
		return getFileTree(complexity_level);
	}
	
	public DirectoryNode getFileTree(int complexity_level) throws IOException{
		//Build
		if(rootHFS == null) return null;
		FileBuffer me = FileBuffer.createBuffer(src_path, false);
		rootHFS.buildFileTree(me, hfs0_off, complexity_level);
		
		DirectoryNode root = new DirectoryNode(null, "");
		DirectoryNode hfs = rootHFS.getFileTree();
		hfs.incrementTreeOffsetsBy(hfs0_off);
		
		if(complexity_level == TREEBUILD_COMPLEXITY_ALL){
			//Make this HFS a separate directory and save the cart header
			hfs.setFileName("XCIROOT");
			hfs.setParent(root);
			
			FileNode fn = new FileNode(root, "xciheader.bin");
			fn.setTypeChainHead(new FileTypeDefNode(NXSysDefs.getXCIHeaderDef()));
			fn.setOffset(0L);
			fn.setLength(0x340);
		}
		else{
			//Make this HFS the root
			root = hfs;
		}
		
		root.setSourcePathForTree(src_path);
		return root;
	}
	
	public NinCryptTable generateCryptTable(){

		NinCryptTable tbl = new NinCryptTable();
		if(rootHFS != null){
			Collection<FileCryptRecord> records = rootHFS.addEncryptionInfo(tbl);
			for(FileCryptRecord r : records){
				r.setCryptOffset(r.getCryptOffset() + hfs0_off);
			}
		}
		
		return tbl;
	}
	
	public long getPackageID(){
		return this.packageID;
	}
	
	public SwitchNCA getNCAByName(String nca_name){
		//Scans full file system for correct NCA
		return rootHFS.getNCAByName(nca_name);
	}
	
	public FileNode getNCANodeByName(String nca_name){
		//Scans full file system for correct NCA
		FileNode node = rootHFS.getNCANodeByName(nca_name);
		if(node == null) return null;
		
		//Add HFS offset
		node.setOffset(node.getOffset() + hfs0_off);
		
		return node;
	}
	
	/*----- Setters -----*/
	
	/*----- Crypto -----*/
	
	public boolean unlock(NXCrypt cryptstate){
		if(rootHFS == null) return false;
		return rootHFS.unlock(cryptstate);
	}
	
	/*----- Util -----*/
	
	/*----- Debug -----*/
	
	public void printInfo(Writer out) throws IOException{
		out.write("============== Nintendo Switch (NX) Cartridge Image (.xci) ==============\n");
		
		out.write("RSA Signature: \n");
		int k = 0;
		for(int i = 0; i < 16; i++){
			for(int j = 0; j < 16; j++){
				out.write(String.format("%02x ", rsa_sig[k++]));
			}
			out.write("\n");
		}
		out.write("\n");
		
		out.write("Secure Address: 0x" + Long.toHexString(secure_addr) + "\n");
		out.write("Backup Address: 0x" + Long.toHexString(backup_addr) + "\n");
		out.write("TitleKeyDec Index: " + idx_tkd + "\n");
		out.write("KEK Index: " + idx_kek + "\n");
		out.write("Header Version: " + header_ver + "\n");
		out.write("Cartridge Capacity: ");
		switch(cart_size){
		case CARTSIZE_1GB: out.write("1 GB\n"); break;
		case CARTSIZE_2GB: out.write("2 GB\n"); break;
		case CARTSIZE_4GB: out.write("4 GB\n"); break;
		case CARTSIZE_8GB: out.write("8 GB\n"); break;
		case CARTSIZE_16GB: out.write("16 GB\n"); break;
		case CARTSIZE_32GB: out.write("32 GB\n"); break;
		}
		out.write("Cartridge Flags: 0x" + Integer.toHexString(cart_flags) + "\n");
		
		out.write("Package ID: " + Long.toHexString(packageID) + "\n");
		out.write("Data End Address: 0x" + Long.toHexString(data_end_addr) + "\n");
		out.write("Normal End Address: 0x" + Long.toHexString(normal_end_addr) + "\n");
		out.write("Gamecard Info Area IV: \n");
		for(int i = 0; i < 16; i++){
			out.write(String.format("%02x", gc_info_iv[i]));
		}
		out.write("\n");
		
		out.write("Root HFS Offset: 0x" + Long.toHexString(hfs0_off) + "\n");
		out.write("Root HFS Header Size: 0x" + Long.toHexString(hfs0_headsize) + "\n");
		out.write("Root HFS Header SHA-256: " + NXCrypt.printHash(sha_hfshead) + "\n");
		out.write("InitData SHA-256: " + NXCrypt.printHash(sha_initdat) + "\n");
		
		out.write("Security Mode: " + security_mode + "\n");
		out.write("T1 Key Index: " + idx_t1k + "\n");
		out.write("Key Index: " + idx_key + "\n");
		
		out.write("\n");
		if(rootHFS != null) rootHFS.printInfo(out);
		else out.write("<Root HFS is null or unparsed!>\n");
		
	}
	
	public void dumpRawNCAs(String dirpath) throws IOException{
		if(rootHFS != null){
			rootHFS.extractRawNCAsTo(dirpath);
		}
	}

}
