package waffleoRai_Containers.nintendo.nx;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import waffleoRai_Encryption.FileCryptRecord;
import waffleoRai_Encryption.FileCryptTable;
import waffleoRai_Encryption.nintendo.NinCrypto;
import waffleoRai_Encryption.nintendo.NinXTSCryptRecord;
import waffleoRai_Files.FileTypeDefNode;
import waffleoRai_Utils.DirectoryNode;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;
import waffleoRai_fdefs.nintendo.NXSysDefs;
import waffleoRai_Utils.FileNode;

public class SwitchNCA implements NXContainer{

	/*----- Constants -----*/
	
	public static final int MAGIC_24 = 0x4e4341; //"NCA"
	public static final int MAGIC_24_LE = 0x41434e; //"NCA"
	
	public static final int NCA_HASHTYPE_HIERSHA256 = 2;
	public static final int NCA_HASHTYPE_IVFC = 3;
	
	public static final int NCA_CRYPTTYPE_AUTO = 0;
	public static final int NCA_CRYPTTYPE_NONE = 1;
	public static final int NCA_CRYPTTYPE_AESXTS = 2;
	public static final int NCA_CRYPTTYPE_AESCTR = 3;
	public static final int NCA_CRYPTTYPE_AESCTR_BKTR = 4;
	
	public static final int NCA_DIST_TYPE_SYS = 0;
	public static final int NCA_DIST_TYPE_GAME = 1;
	
	public static final int NCA_CONTENT_TYPE_PROG = 0;
	public static final int NCA_CONTENT_TYPE_META = 1;
	public static final int NCA_CONTENT_TYPE_CONTROL = 2;
	public static final int NCA_CONTENT_TYPE_MANUAL = 3;
	public static final int NCA_CONTENT_TYPE_DATA = 4;
	public static final int NCA_CONTENT_TYPE_PUBLICDAT = 5;
	
	public static final int KAK_KEYTYPE_APP = 0;
	public static final int KAK_KEYTYPE_OCEAN = 1;
	public static final int KAK_KEYTYPE_SYSTEM = 2;
	
	/*----- Static Variables -----*/
	
	private static byte[] header_key_common;
	
	/*----- Instance Variables -----*/
	
	private String src_path;
	private long src_off;
	
	private byte[] rsa_common;
	private byte[] rsa_npdm;
	
	private int nca_ver;
	private int dist_type;
	private int content_type;
	
	private int keygen; //Subtract 1 to get indices (gen 0 and 1 both use idx 0)
	private int header1_sig_keygen;
	private int key_area_key_idx;
	
	private long content_size;
	private long content_id;
	private int content_idx;
	private int sdk_addon_ver;
	
	private byte[] rightsID;
	
	private NXNCAPart[] fs_entries;
	private byte[][] fs_head_hashes;
	
	private byte[] enc_key_area;
	
	//If no rights ID, then it's decrypted key area.
	//0x40, First two rows are XTS key, then CTR key (3rd row), dunno what the last is
	//If has rightsID, then this is the decrypted titlekey
	private byte[] dec_key_dat; 
	
	private DirectoryNode root;

	/*----- Structs -----*/
		
	protected static interface FSHashInfo{
		public long getQuickHash();
		public void printMe(int tabs);
	}
	
	protected static class ShaHashInfo implements FSHashInfo{
		
		public byte[] sha_hash;
		public long block_size;
		public long hash_table_offset;
		public long hash_table_size;
		public long rel_offset;
		public long pfs_size;
		
		public void printMe(int tabs){
			String indent = "";
			if(tabs > 0){
				StringBuilder sb = new StringBuilder(tabs);
				for(int i = 0; i < tabs; i++)sb.append('\t');
				indent = sb.toString();
			}
			
			System.err.println(indent + "Master Hash: " + NXCrypt.printHash(sha_hash));
			System.err.println(indent + "Block Size: 0x" + Long.toHexString(block_size));
			System.err.println(indent + "Hash Table Offset: 0x" + Long.toHexString(hash_table_offset));
			System.err.println(indent + "Hash Table Size: 0x" + Long.toHexString(hash_table_size));
			System.err.println(indent + "PFS Offset: 0x" + Long.toHexString(rel_offset));
			System.err.println(indent + "PFS Size: 0x" + Long.toHexString(pfs_size));
		}
		
		public long getQuickHash(){
			long val = 0L;
			int shamt = 0;
			for(int i = 0; i < 8; i++){
				val |= Byte.toUnsignedLong(sha_hash[i+3]) << shamt;
				shamt += 8;
			}
			return val;
		}
		
	}
	
	protected static class IVFCHashInfo implements FSHashInfo{
		
		public long master_hash_size;
		public byte[] hash;
		
		//Levels
		public long[] offsets;
		public long[] sizes;
		public int[] bsz_shamt; //SRL by this amt to get block size
		
		public void allocLevels(int lvlcount){
			offsets = new long[lvlcount];
			sizes = new long[lvlcount];
			bsz_shamt = new int[lvlcount];
		}
		
		public void printMe(int tabs){
			String indent = "";
			if(tabs > 0){
				StringBuilder sb = new StringBuilder(tabs);
				for(int i = 0; i < tabs; i++)sb.append('\t');
				indent = sb.toString();
			}
			
			System.err.println(indent + "Master Hash Size: 0x" + Long.toHexString(master_hash_size));
			System.err.println(indent + "Master Hash: " + NXCrypt.printHash(hash));
			System.err.println(indent + "Levels: " + offsets.length);
			for(int i = 0; i < offsets.length; i++){
				System.err.print(indent + "\tLevel " + i + ": ");
				System.err.print("Off = 0x" + Long.toHexString(offsets[i]) + " | ");
				System.err.print("Size = 0x" + Long.toHexString(sizes[i]) + " | ");
				System.err.print("Block Size = 0x" + Long.toHexString(1L << bsz_shamt[i]) + "\n");
			}
		}
		
		public long getQuickHash(){
			long val = 0L;
			int shamt = 0;
			for(int i = 0; i < 8; i++){
				val |= Byte.toUnsignedLong(hash[i+5]) << shamt;
				shamt += 8;
			}
			return val;
		}
		
	}
	
	protected static class BKTREntry{
		
		public long offset;
		public long size;
		public int u32;
		public int s32;
		
	}
	
	/*----- Construction/Parsing -----*/
	
	private SwitchNCA(){}
	
	public static SwitchNCA readNCA(FileBuffer dat, long offset) throws UnsupportedFileTypeException, IOException{
		//Don't forget to detect whether encrypted
		dat.setEndian(false);
		
		int mcheck = dat.shortishFromFile(offset + 0x200);
		FileBuffer hdr = null;
		if(mcheck != MAGIC_24_LE){
			//Assume encrypted!
			hdr = decryptHeader(dat, offset);
		}
		else{
			hdr = dat.createReadOnlyCopy(offset, offset + 0xc00);
		}
		//System.err.println("SwitchNCA.readNCA || NCA magic # detected: " + hdr.getASCII_string(0x200, 4));
		
		SwitchNCA nca = new SwitchNCA();
		nca.readNCAHeader(hdr);
		hdr.dispose();
		
		nca.src_path = dat.getPath();
		nca.src_off = offset;
		
		return nca;
	}
	
	private void readNCAHeader(FileBuffer hdr_dec){

		hdr_dec.setCurrentPosition(0L);
		
		rsa_common = new byte[0x100];
		for(int i = 0; i < 0x100; i++) rsa_common[i] = hdr_dec.nextByte();
		rsa_npdm = new byte[0x100];
		for(int i = 0; i < 0x100; i++) rsa_npdm[i] = hdr_dec.nextByte();
		hdr_dec.skipBytes(4L);
		
		String nca_mag = hdr_dec.getASCII_string(0x200, 4);
		nca_ver = nca_mag.charAt(3) - '0';
		
		dist_type = Byte.toUnsignedInt(hdr_dec.nextByte());
		content_type = Byte.toUnsignedInt(hdr_dec.nextByte());
		keygen = Byte.toUnsignedInt(hdr_dec.nextByte());
		key_area_key_idx = Byte.toUnsignedInt(hdr_dec.nextByte());
		
		content_size = hdr_dec.nextLong();
		content_id = hdr_dec.nextLong();
		content_idx = hdr_dec.nextInt();
		sdk_addon_ver = hdr_dec.nextInt();
		
		if(keygen >= 0x02) keygen = Byte.toUnsignedInt(hdr_dec.nextByte());
		else hdr_dec.nextByte();
		header1_sig_keygen = Byte.toUnsignedInt(hdr_dec.nextByte());
		hdr_dec.skipBytes(0xE);
		
		rightsID = new byte[16];
		for(int i = 0; i < 16; i++) rightsID[i] = hdr_dec.nextByte();
		
		fs_entries = new NXNCAPart[4];
		for(int i = 0; i < 4; i++){
			long stoff = Integer.toUnsignedLong(hdr_dec.nextInt()) << 9;
			long edoff = Integer.toUnsignedLong(hdr_dec.nextInt()) << 9;
			
			fs_entries[i] = new NXNCAPart();
			fs_entries[i].setOffset(stoff);
			fs_entries[i].setSize(edoff-stoff);
			
			hdr_dec.skipBytes(8);
		}
		
		fs_head_hashes = new byte[4][0x20];
		for(int i = 0; i < 4; i++){
			for(int j = 0; j < 0x20; j++) fs_head_hashes[i][j] = hdr_dec.nextByte();
		}
		
		enc_key_area = new byte[0x40];
		for(int j = 0; j < 0x40; j++) enc_key_area[j] = hdr_dec.nextByte();
		
		int sec = 0;
		for(int i = 0; i < 4; i++){
			hdr_dec.setCurrentPosition(0x400 + (sec << 9));
			NXNCAPart fse = fs_entries[i];
			fse.setVersion(Short.toUnsignedInt(hdr_dec.nextShort()));
			fse.setFSType(Byte.toUnsignedInt(hdr_dec.nextByte()));
			fse.setHashType(Byte.toUnsignedInt(hdr_dec.nextByte()));
			fse.setEncryptionType(Byte.toUnsignedInt(hdr_dec.nextByte()));
			hdr_dec.skipBytes(3);
			
			//Hash Info
			switch(fse.getHashType()){
			case 0: 
				hdr_dec.skipBytes(0xF8);
				break;
			case NCA_HASHTYPE_HIERSHA256:
				ShaHashInfo hinfo2 = new ShaHashInfo();
				hinfo2.sha_hash = new byte[0x20];
				for(int j = 0; j < 0x20; j++) hinfo2.sha_hash[j] = hdr_dec.nextByte();
				hinfo2.block_size = Integer.toUnsignedLong(hdr_dec.nextInt());
				hdr_dec.nextInt();
				hinfo2.hash_table_offset = hdr_dec.nextLong();
				hinfo2.hash_table_size = hdr_dec.nextLong();
				hinfo2.rel_offset = hdr_dec.nextLong();
				hinfo2.pfs_size = hdr_dec.nextLong();
				hdr_dec.skipBytes(0xB0);
				fse.setHashInfo(hinfo2);
				break;
			case NCA_HASHTYPE_IVFC: 
				IVFCHashInfo hinfo3 = new IVFCHashInfo();
				hdr_dec.skipBytes(8);
				hinfo3.master_hash_size = Integer.toUnsignedLong(hdr_dec.nextInt());
				int lvlcount = hdr_dec.nextInt();
				hinfo3.allocLevels(lvlcount);
				
				long cpos = hdr_dec.getCurrentPosition();
				for(int j = 0; j < lvlcount; j++){
					hinfo3.offsets[j] = hdr_dec.longFromFile(cpos); cpos+=8;
					hinfo3.sizes[j] = hdr_dec.longFromFile(cpos); cpos+=8;
					hinfo3.bsz_shamt[j] = hdr_dec.intFromFile(cpos); cpos+=4;
					cpos+=4;
				}
				
				hdr_dec.skipBytes(0xB0);
				hinfo3.hash = new byte[0x20];
				for(int j = 0; j < 0x20; j++) hinfo3.hash[j] = hdr_dec.nextByte();
				
				hdr_dec.skipBytes(0x18);
				fse.setHashInfo(hinfo3);
				break;
			}
			
			//Patch Info
			BKTREntry[] patch_info = new BKTREntry[2];
			fse.setPatchInfo(patch_info);
			for(int j = 0; j < 2; j++){
				BKTREntry pi = new BKTREntry();
				pi.offset = hdr_dec.nextLong();
				pi.size = hdr_dec.nextLong();
				hdr_dec.skipBytes(4);
				pi.u32 = hdr_dec.nextInt();
				pi.s32 = hdr_dec.nextInt();
				hdr_dec.skipBytes(4);
				
				patch_info[j] = pi;
			}
			
			//The rest...
			fse.setGeneration(hdr_dec.nextInt());
			fse.setSecureValue(hdr_dec.nextInt());
			byte[] sparse_info = new byte[0x30];
			fse.setSparseInfo(sparse_info);
			for(int j = 0; j < 0x30; j++) sparse_info[j] = hdr_dec.nextByte();
			
			sec++;
		}

	}
	
	public boolean buildFileTree(FileBuffer dat, long offset, int complexity_level){
		//Assumed offset is start of NCA
		//Header has presumably already been read.
		//If part key not decrypted, will just generate a .aes file for partition
		
		//Output offsets will be relative to NCA start
		//printMe();
		
		root = new DirectoryNode(null, "");
		if(complexity_level == NXCartImage.TREEBUILD_COMPLEXITY_ALL){
			//Add header
			FileNode fn = new FileNode(root, "ncaheader.bin");
			fn.setTypeChainHead(new FileTypeDefNode(NXSysDefs.getNCAHeaderDef()));
			fn.setOffset(0L); fn.setLength(0xC00);
			fn.setMetadataValue(NinCrypto.METAKEY_CRYPTGROUPUID, Long.toHexString(updateCryptRegUID()));
		}
		
		boolean b = true;
		for(int i = 0; i < 4; i++){
			NXNCAPart part = fs_entries[i];
			if(part == null) continue;
			if(part.getSize() <= 0L) continue;
			//System.err.println("SwitchNCA.buildFileTree || Now parsing partition " + i);
			
			boolean success = part.buildFileTree(dat, offset + part.getOffset(), complexity_level == NXCartImage.TREEBUILD_COMPLEXITY_ALL);
			b = b && success;
			if(success){
				//Mount tree
				DirectoryNode partroot = part.getFileTree();
				partroot.incrementTreeOffsetsBy(part.getOffset());
				if(complexity_level == NXCartImage.TREEBUILD_COMPLEXITY_MERGED){
					//Mount part children directly to root
					List<FileNode> children = partroot.getChildren();
					for(FileNode child : children){
						child.setParent(root);
					}
				}
				else{
					partroot.setFileName("p" + String.format("%02d", i));
					partroot.setParent(root);	
				}
			}
			else{
				//Mount as .aes file
				String name = "p" + String.format("%02d", i) + ".aes";
				if(complexity_level == NXCartImage.TREEBUILD_COMPLEXITY_MERGED){
					name = Long.toHexString(updateCryptRegUID()) + "_" + name;
				}
				FileNode fn = new FileNode(root, name);
				fn.setOffset(part.getOffset());
				fn.setLength(part.getSize());
			}
		}
		
		return b;
	}
	
	public Collection<FileCryptRecord> addEncryptionInfo(FileCryptTable table){
		
		List<FileCryptRecord> rlist = new LinkedList<FileCryptRecord>();
		
		long cuid = this.updateCryptRegUID();
		NinXTSCryptRecord rec = new NinXTSCryptRecord(cuid);
		rec.setSector(0L);
		rec.setKeyType(NinCrypto.KEYTYPE_256);
		int kidx = table.getIndexOfKey(NinCrypto.KEYTYPE_256, SwitchNCA.getCommonHeaderKey());
		if(kidx == -1) kidx = table.addKey(NinCrypto.KEYTYPE_256, SwitchNCA.getCommonHeaderKey());
		rec.setKeyIndex(kidx);

		rec.setCryptOffset(0); //Relative to NCA start
		table.addRecord(cuid, rec);
		rlist.add(rec);
		
		//Now do partitions...
		for(int i = 0; i < 4; i++){
			NXNCAPart part = fs_entries[i];
			if(part == null) continue;
			if(part.getSize() <= 0) continue;
			
			FileCryptRecord prec = part.addEncryptionInfo(table);
			if(prec != null){prec.setCryptOffset(prec.getCryptOffset() + part.getOffset()); rlist.add(prec);}
		}
		
		return rlist;
	}
	
	/*----- Getters -----*/
	
	public long updateCryptRegUID(){
		long l = 0L;
		int shamt = 56;
		for(int i = 0; i < 8; i++){
			l |= Byte.toUnsignedLong(rsa_common[i]) << shamt;
			shamt -= 8;
		}
		
		return l;
	}
	
	public static byte[] getCommonHeaderKey(){
		return header_key_common;
	}
	
	public boolean hasRightsID(){
		if(rightsID == null) return false;
		for(int i= 0; i < rightsID.length; i++){
			if(rightsID[i] != 0) return true;
		}
		return false;
	}
	
	public DirectoryNode getFileTree(){
		return root;
	}
	
	/*----- Setters -----*/
	
	public static void setCommonHeaderKey(byte[] key){
		header_key_common = key;
	}
	
	public void setSourceLocation(String path, long offset){
		src_path = path;
		src_off = offset;
	}
	
	/*----- Crypto -----*/
	
	public int getKeyIndex(){
		int ki = keygen-1;
		return ki >= 0?ki:0;
	}
	
	public void decryptPartKeys(NXCrypt cryptostate){
		//Check rights ID
		if(hasRightsID()){
			dec_key_dat = cryptostate.getTitleKey(rightsID, getKeyIndex());
		}
		else{
			dec_key_dat = cryptostate.decrypt_keyArea(getKeyIndex(), key_area_key_idx, enc_key_area);
		}
	}
	
	private static FileBuffer decryptHeader(FileBuffer dat, long offset) throws UnsupportedFileTypeException{
		if(header_key_common == null) throw new UnsupportedFileTypeException("Header Key has not been set!");
		
		FileBuffer header = new FileBuffer(0xc00, false);
		
		//First two sectors
		long sector = 0L;
		for(int i = 0; i < 2; i++){
			byte[] enc = dat.getBytes(offset, offset+0x200); offset += 0x200;
			byte[] dec = NXCrypt.decrypt_AESXTS_sector(header_key_common, enc, sector++);
			for(int j = 0; j < 0x200; j++) header.addToFile(dec[j]);	
		}
		
		//Stop to check NCA version
		String magic = header.getASCII_string(0x200, 4);
		char n = magic.charAt(3);
		switch(n){
		case '3': 
			//Just read the remaining sectors the normal way.
			for(int i = 0; i < 4; i++){
				byte[] enc = dat.getBytes(offset, offset+0x200); offset += 0x200;
				byte[] dec = NXCrypt.decrypt_AESXTS_sector(header_key_common, enc, sector++);
				for(int j = 0; j < 0x200; j++) header.addToFile(dec[j]);	
			}
			break;
		case '2':
		case '1':
		case '0': 
			//I think use a 0 tweak for each of the remaining four?
			for(int i = 0; i < 4; i++){
				byte[] enc = dat.getBytes(offset, offset+0x200); offset += 0x200;
				byte[] dec = NXCrypt.decrypt_AESXTS_sector(header_key_common, enc, 0L);
				for(int j = 0; j < 0x200; j++) header.addToFile(dec[j]);	
			}
			break;
		}
		
		try{header.writeFile("C:\\Users\\Blythe\\Documents\\Desktop\\out\\nxtest\\decryptheadertest.bin");}
		catch(Exception x){x.printStackTrace();}
		return header;
	}
	
	public boolean unlock(NXCrypt cryptstate){
		if(cryptstate == null) return false;
		decryptPartKeys(cryptstate);
		
		for(int i = 0; i < 4; i++){
			NXNCAPart part = fs_entries[i];
			byte[] key = null;
			if(dec_key_dat.length > 0x10){
				//Key area.
				int etype = part.getEncryptionType();
				switch(etype){
				case SwitchNCA.NCA_CRYPTTYPE_AESXTS:
					key = Arrays.copyOfRange(dec_key_dat, 0x0, 0x20);
					break;
				case SwitchNCA.NCA_CRYPTTYPE_AESCTR:
				case SwitchNCA.NCA_CRYPTTYPE_AESCTR_BKTR:
					key = Arrays.copyOfRange(dec_key_dat, 0x20, 0x30);
					break;
				}
			}
			else key = dec_key_dat;
			part.setKey(key);
		}
		
		return true;
	}
	
	/*----- Extraction -----*/
	
	public void extractPartitionDataTo(int pidx, long offset, long length, String outpath) throws IOException{
		if(pidx < 0 || pidx >= 4) return;
		
		NXNCAPart part = fs_entries[pidx];
		byte[] key = null;
		if(dec_key_dat.length > 0x10){
			//Key area.
			int etype = part.getEncryptionType();
			switch(etype){
			case SwitchNCA.NCA_CRYPTTYPE_AESXTS:
				key = Arrays.copyOfRange(dec_key_dat, 0x0, 0x20);
				break;
			case SwitchNCA.NCA_CRYPTTYPE_AESCTR:
			case SwitchNCA.NCA_CRYPTTYPE_AESCTR_BKTR:
				key = Arrays.copyOfRange(dec_key_dat, 0x20, 0x30);
				break;
			}
		}
		else key = dec_key_dat;
		part.setKey(key);
		
		BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(outpath));
		BufferedInputStream input = new BufferedInputStream(new FileInputStream(src_path));
		long skip = src_off + part.getOffset() + (offset & (~0xF));
		//System.err.println("src_off = 0x" + Long.toHexString(src_off));
		//System.err.println("part offset = 0x" + Long.toHexString(part.getOffset()));
		//System.err.println("offset row = 0x" + Long.toHexString(offset & (~0xF)));
		//System.err.println("offset = 0x" + Long.toHexString(offset));
		//System.err.println("skip = 0x" + Long.toHexString(skip));
		input.skip(skip);
		
		part.decryptRegion(input, output, offset, length);
		
		input.close();
		output.close();
		
	}
	
	/*----- Debug -----*/
	
	public void extractPartitionsTo(String dirpath, NXCrypt cryptostate, long sizeLimit) throws IOException{

		if(!FileBuffer.directoryExists(dirpath)) Files.createDirectories(Paths.get(dirpath));
		if(dec_key_dat == null) decryptPartKeys(cryptostate);
		
		for(int i = 0; i < 4; i++){
			NXNCAPart part = fs_entries[i];
			if(part == null) continue;
			long psz = part.getSize();
			if(psz <= 0L) continue;
			
			String outpath = dirpath + File.separator + "partition" + String.format("%02d", i) + ".bin";
			BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(outpath));
			BufferedInputStream input = new BufferedInputStream(new FileInputStream(src_path));
			input.skip(src_off + part.getOffset());
			
			//Key
			byte[] key = null;
			if(dec_key_dat.length > 0x10){
				//Key area.
				int etype = part.getEncryptionType();
				switch(etype){
				case SwitchNCA.NCA_CRYPTTYPE_AESXTS:
					key = Arrays.copyOfRange(dec_key_dat, 0x0, 0x20);
					break;
				case SwitchNCA.NCA_CRYPTTYPE_AESCTR:
				case SwitchNCA.NCA_CRYPTTYPE_AESCTR_BKTR:
					key = Arrays.copyOfRange(dec_key_dat, 0x20, 0x30);
					break;
				}
			}
			else key = dec_key_dat;
			part.setKey(key);
			
			long outlen = psz > sizeLimit?sizeLimit:psz;
			//long outlen = psz;
			part.decryptRegion(input, output, 0L, outlen);
			
			output.close();
			input.close();
		}
		
	}
	
	public void printMe(){

		System.err.println("------------ NX Content Archive ------------");
		System.err.println("NCA Version: " + this.nca_ver);
		
		String s = "System";
		if(dist_type == NCA_DIST_TYPE_GAME) s = "Application";
		System.err.println("Distribution Type: " + s);
		
		s = "Unknown";
		switch(content_type){
		case NCA_CONTENT_TYPE_PROG: s = "Program"; break;
		case NCA_CONTENT_TYPE_META: s = "Metadata"; break;
		case NCA_CONTENT_TYPE_CONTROL: s = "Control"; break;
		case NCA_CONTENT_TYPE_MANUAL: s = "Manual"; break;
		case NCA_CONTENT_TYPE_DATA: s = "Data"; break;
		case NCA_CONTENT_TYPE_PUBLICDAT: s = "Public Data"; break;
		}
		System.err.println("Content Type: " + s);
		
		System.err.println("Key Generation: " + keygen);
		System.err.println("Sig Key Generation: " + header1_sig_keygen);
		
		s = "Invalid";
		switch(key_area_key_idx){
		case KAK_KEYTYPE_APP: s = "Application"; break;
		case KAK_KEYTYPE_OCEAN: s = "Ocean"; break;
		case KAK_KEYTYPE_SYSTEM: s = "System"; break;
		}
		System.err.println("Key Area Keyset: " + s);
		
		System.err.println("Content Size: 0x" + Long.toHexString(content_size));
		System.err.println("Content ID: 0x" + Long.toHexString(content_id));
		System.err.println("Content Index: " + content_idx);
		System.err.println("SDK Add-on Version: " + sdk_addon_ver);
		
		System.err.println("Rights ID: " + NXCrypt.printHash(rightsID));
		
		for(int i = 0; i < 4; i++){
			System.err.println("-> Partition " + i);
			if(fs_entries == null || fs_entries[i] == null){
				System.err.println("\t<null>");
			}
			else{
				fs_entries[i].printMe(1);
			}
		}
		
	}
	
}
