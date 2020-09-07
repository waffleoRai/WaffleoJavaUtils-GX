package waffleoRai_Containers.nintendo.citrus;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;

import waffleoRai_Encryption.AES;
import waffleoRai_Files.FileTypeDefNode;
import waffleoRai_Files.GenericSystemDef;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;
import waffleoRai_Files.tree.DirectoryNode;
import waffleoRai_Files.tree.FileNode;

/*
 * Decrypt Buffer
 * Just a big mashup of decrypted regions from an NCCH container.
 * 
 * Extended Header (If present)
 * ExeFS
 * RomFS
 * 
 */

public class CitrusNCC {

	/*----- Constants -----*/
	
	public static final String MAGIC = "NCCH";
	
	public static final int IV_TYPE_EXHEADER = 1;
	public static final int IV_TYPE_EXEFS = 2;
	public static final int IV_TYPE_ROMFS = 3;
	
	public static final int EXHEADER_OFFSET = 0x200;
	
	public static final String METAKEY_ENC = "NCC_USEASIS";
	
	/*----- Instance Variables -----*/
	
	//NCC Header
	private FileNode src;
	
	private byte[] rsa_sig; //Main RSA
	private long ncsd_size;
	
	private long part_id;
	private String maker_code;
	private int part_ver;
	private long program_id;
	
	private String product_code;
	
	private byte[] hash_logo;
	private byte[] hash_exHeader;
	private int exHeader_size;
	
	private int crypto_type;
	private boolean is_new3ds;
	private int content_type_mask;
	private int content_unit_size;
	private int bitmask;
	
	private long plain_offset;
	private long plain_len;
	private long logo_offset;
	private long logo_len;
	
	private long exefs_offset;
	private long exefs_len;
	private long exefs_hashregsize;
	private byte[] exefs_hash;
	
	private long romfs_offset;
	private long romfs_len;
	private long romfs_hashregsize;
	private byte[] romfs_hash;
	
	//Contents
	private CitrusNCCExHeader exheader; //Only in cxi
	private CitrusEXEFS exefs; //Only in cxi
	private CitrusROMFS romfs;
	
	//Additional
	private byte[] romid; //Needs to be dumped from ROM separately
	//private CitrusCrypt crypto;
	private byte[] secondary_keyY; //Should be set by NCSD or seedDB
	private FileNode decbuffer;
	
	/*----- Construction/Parsing -----*/
	
	private CitrusNCC(){}
	
	public static CitrusNCC readNCC(FileNode source) throws UnsupportedFileTypeException, IOException{
		
		//Open
		FileBuffer data = source.loadDecompressedData();
		
		CitrusNCC ncc = readNCC(data, 0);
		ncc.src = source;
		return ncc;
	}
	
	public static CitrusNCC readNCC(FileBuffer data, long offset) throws UnsupportedFileTypeException, IOException{
		
		//Set byte order
		data.setEndian(false);

		//Instantiate
		CitrusNCC ncc = new CitrusNCC();
		//ncc.crypto = crypto;

		//Header
		long cpos = offset;
		ncc.rsa_sig = data.getBytes(cpos, cpos + 0x100); cpos += 0x100;
		long moff = data.findString(cpos, cpos + 16, MAGIC);
		if(moff != cpos) throw new FileBuffer.UnsupportedFileTypeException("NCC magic number was not found!");
		cpos += 4;
		ncc.ncsd_size = Integer.toUnsignedLong(data.intFromFile(cpos)) << 9; cpos += 4;
		ncc.part_id = data.longFromFile(cpos); cpos+=8;
		
		ncc.maker_code = data.getASCII_string(cpos, 2); cpos+=2;
		ncc.part_ver = Short.toUnsignedInt(data.shortFromFile(cpos)); cpos+=2;
		cpos+=4;
		ncc.program_id = data.longFromFile(cpos); cpos+=8;
		cpos+=0x10;
		ncc.hash_logo = data.getBytes(cpos, cpos + 0x20); cpos += 0x20;
		ncc.product_code = data.getASCII_string(cpos, 0x10); cpos+=0x10;
		ncc.hash_exHeader = data.getBytes(cpos, cpos + 0x20); cpos += 0x20;
		ncc.exHeader_size = data.intFromFile(cpos); cpos+=4;
		cpos+=4;
		
		cpos+=3;
		ncc.crypto_type = Byte.toUnsignedInt(data.getByte(cpos++));
		int val = Byte.toUnsignedInt(data.getByte(cpos++));
		if(val == 2) ncc.is_new3ds = true;
		else ncc.is_new3ds = false;
		ncc.content_type_mask = Byte.toUnsignedInt(data.getByte(cpos++));
		val = Byte.toUnsignedInt(data.getByte(cpos++));
		ncc.content_unit_size = (0x1 << val) << 9;
		ncc.bitmask = Byte.toUnsignedInt(data.getByte(cpos++));
		
		ncc.plain_offset = Integer.toUnsignedLong(data.intFromFile(cpos)) << 9; cpos+=4;
		ncc.plain_len = Integer.toUnsignedLong(data.intFromFile(cpos)) << 9; cpos+=4;
		ncc.logo_offset = Integer.toUnsignedLong(data.intFromFile(cpos)) << 9; cpos+=4;
		ncc.logo_len = Integer.toUnsignedLong(data.intFromFile(cpos)) << 9; cpos+=4;
		
		ncc.exefs_offset = Integer.toUnsignedLong(data.intFromFile(cpos)) << 9; cpos+=4;
		ncc.exefs_len = Integer.toUnsignedLong(data.intFromFile(cpos)) << 9; cpos+=4;
		ncc.exefs_hashregsize = Integer.toUnsignedLong(data.intFromFile(cpos)) << 9; cpos+=8;
		ncc.romfs_offset = Integer.toUnsignedLong(data.intFromFile(cpos)) << 9; cpos+=4;
		ncc.romfs_len = Integer.toUnsignedLong(data.intFromFile(cpos)) << 9; cpos+=4;
		ncc.romfs_hashregsize = Integer.toUnsignedLong(data.intFromFile(cpos)) << 9; cpos+=8;
		
		ncc.exefs_hash = data.getBytes(cpos, cpos+0x20); cpos+=0x20;
		ncc.romfs_hash = data.getBytes(cpos, cpos+0x20); cpos+=0x20;
		
		return ncc;
	}
	
	private FileBuffer readExHeader(FileBuffer data, long offset, CitrusCrypt crypto) throws UnsupportedFileTypeException, IOException{
		if(!isCXI()) return null;
		
		//Decrypt
		byte[] enc = data.getBytes(offset, offset + exHeader_size);
		byte[] iv = genIV(IV_TYPE_EXHEADER);
		byte[] key = genPrimaryKey(crypto);
		
		AES aes = new AES(key);
		aes.setCTR();
		byte[] dec = aes.decrypt(iv, enc);
		
		//Check hash
		try{
			MessageDigest sha = MessageDigest.getInstance("SHA-256");
			sha.update(dec);
			byte[] hashbuff = sha.digest();
			if(!MessageDigest.isEqual(hash_exHeader, hashbuff)) {
				throw new UnsupportedFileTypeException("Extended header decryption failed (mismatched hash)!");	
			}
			
			//System.err.println("Ref Hash: " + printHash(hash_exHeader));
			//System.err.println("DecBuffer Hash: " + printHash(hashbuff));
		}
		catch(Exception x){
			x.printStackTrace();
			throw new UnsupportedFileTypeException("Hash of extended header failed!");
		}
		
		//Wrap and Set
		FileBuffer eheader = FileBuffer.wrap(dec);
		exheader = CitrusNCCExHeader.readExtendedHeader(eheader, 0);

		//If exheader size is 0x400, nab the accessdesc separately
		if(exHeader_size < 0x800 && data.getFileSize() >= 0x800){
			//I do not know if these need to be decrypted separately.
			//The hash seems to only match what is specified within exHeader_size, so maybe not?
			
			data.setCurrentPosition(offset + 0x400);
			byte[] barr = new byte[0x100];
			for(int i = 0; i < 0x100; i++) barr[i] = data.nextByte();
			exheader.setAccessDescSig(barr);
			
			//Copy RSA Key
			barr = new byte[0x100];
			for(int i = 0; i < 0x100; i++) barr[i] = data.nextByte();		
			exheader.setPublicRSAKey(barr);
		}

		return eheader;
	}
		
	private File readExeFS(FileBuffer data, long offset, CitrusCrypt crypto) throws IOException{

		byte[] iv = genIV(IV_TYPE_EXEFS);
		byte[] key = genPrimaryKey(crypto);
		
		AES aes = new AES(key);
		aes.setCTR();
		aes.initDecrypt(iv);
		
		String temppath = FileBuffer.generateTemporaryPath("ctrexefs_dec");
		BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(temppath));
		
		final int blocksize = 0x200;
		long remain = exefs_len;
		long pos = 0;
		boolean last = false;
		while(remain > 0){
			int bsz = blocksize;
			if(remain <= blocksize){
				bsz = (int)remain;
				last = true;
			}
			
			byte[] eblock = data.getBytes(offset+pos, offset+pos+bsz);
			byte[] dblock = aes.decryptBlock(eblock, last);
			
			bos.write(dblock);
			
			remain -= bsz;
			pos += bsz;
		}
		
		bos.close();
		
		//Read back in (to get file tree...)
		exefs = CitrusEXEFS.readExeFSHeader(FileBuffer.createBuffer(temppath, 0, 0x200, false), 0);
		
		return new File(temppath);
	}
	
	private void readRomFS() throws IOException, UnsupportedFileTypeException{
		if(!this.isDecrypted()) return;
		
		//Determine where the RomFS is...
		long romfs_off = 0;
		if(isCXI()) romfs_off += 0x400; //ExHeader
		romfs_off += exefs_len;
		
		FileBuffer dat = FileBuffer.createBuffer(decbuffer.getSourcePath(), romfs_off, romfs_off + romfs_len, false);
		
		romfs = CitrusROMFS.readROMFS(dat, 0);
	}
	
	/*----- Getters -----*/
	
	public boolean isCXI(){
		return (this.content_type_mask & 0x02) != 0;
	}
		
	public long getPartitionID(){return this.part_id;}
	public String getProductID(){return this.product_code;}
	public String getMakerCode(){return this.maker_code;}
	public byte[] getLogoHash(){return hash_logo;}
	public byte[] getExeFSHash(){return exefs_hash;}
	public byte[] getRomFSHash(){return romfs_hash;}
	
	public long getExeFSOffset(){return this.exefs_offset;}
	public long getExeFSSize(){return this.exefs_len;}
	public long getRomFSOffset(){return this.romfs_offset;}
	public long getRomFSSize(){return this.romfs_len;}
	
	public byte[] getSetRomID(){return romid;}
	
	public DirectoryNode getFileTree(){

		String rname = Long.toHexString(part_id);
		boolean cxi = isCXI();
		if(cxi) rname += ".cxi";
		else rname += ".cfa";
		DirectoryNode root = new DirectoryNode(null, rname);
		
		boolean isdec = false;
		String dpath = null;
		if(decbuffer != null){
			isdec = FileBuffer.fileExists(decbuffer.getSourcePath());
			dpath = decbuffer.getSourcePath();
		}
		
		String epath = src.getSourcePath();
		long eoff = src.getOffset();
		
		//System: ncch.bin, exheader.bin, accessdesc.bin
		FileNode fn = new FileNode(root, "ncch.bin");
		fn.setOffset(eoff); fn.setLength(0x200); fn.setSourcePath(epath);
		fn.setMetadataValue(METAKEY_ENC, "true");
		fn.setTypeChainHead(new FileTypeDefNode(getHeaderDef()));
		
		if(cxi){
			fn = new FileNode(root, "accessdesc.bin");
			fn.setOffset(eoff + 0x600); fn.setLength(0x400); fn.setSourcePath(epath);
			fn.setMetadataValue(METAKEY_ENC, "true");
			fn.setTypeChainHead(new FileTypeDefNode(getAccessDescDef()));
			
			if(isdec){
				fn = new FileNode(root, "exheader.bin");
				fn.setOffset(0); fn.setLength(0x400); fn.setSourcePath(dpath);
				fn.setTypeChainHead(new FileTypeDefNode(getExHeaderDef()));
			}
			else{
				fn = new FileNode(root, "exheader.aes");
				fn.setOffset(eoff + 0x200); fn.setLength(0x400); fn.setSourcePath(epath);
				fn.setMetadataValue(METAKEY_ENC, "true");
			}
			
			//Plain region
			if(plain_len > 0){
				fn = new FileNode(root, "plaindat.bin");
				fn.setOffset(eoff+ plain_offset); fn.setLength(plain_len); fn.setSourcePath(epath);	
				fn.setMetadataValue(METAKEY_ENC, "true");
				fn.setTypeChainHead(new FileTypeDefNode(getPlainRegDef()));
			}
		}
		
		//ExeFS
		if(exefs != null){
			DirectoryNode exe_root = exefs.getFileTree();
			exe_root.setFileName("ExeFS");
			exe_root.setParent(root);
			exe_root.incrementTreeOffsetsBy(0x600); //Offset of exefs in dec buffer
			exe_root.setSourcePathForTree(dpath);
		}
		else{
			//Definitely encrypted...
			if(exefs_len > 0){
				fn = new FileNode(root, "exefs.aes");
				fn.setOffset(eoff + exefs_offset); fn.setLength(exefs_len); fn.setSourcePath(epath);
				fn.setMetadataValue(METAKEY_ENC, "true");	
			}
		}
		
		//RomFS
		if(romfs != null){
			DirectoryNode rom_root = romfs.getFileTree();
			rom_root.setFileName("RomFS");
			rom_root.setParent(root);
			rom_root.incrementTreeOffsetsBy(0x400 + exefs_len); //Offset of romfs in dec buffer
			rom_root.setSourcePathForTree(dpath);
		}
		else{
			fn = new FileNode(root, "romfs.aes");
			fn.setOffset(eoff + romfs_offset); fn.setLength(romfs_len); fn.setSourcePath(epath);
			fn.setMetadataValue(METAKEY_ENC, "true");
		}
		
		return root;
	}
	
	/*----- Setters -----*/
	
	public void setSource(FileNode node){
		src = node;
	}

	public void setDecBufferLocation(String path) throws IOException{
		//Also moves the file to new location if present
		if(decbuffer != null){
			String oldpath = decbuffer.getSourcePath();
			if(FileBuffer.fileExists(oldpath)){
				Files.deleteIfExists(Paths.get(path));
				Files.move(Paths.get(oldpath), Paths.get(path));
			}
		}
		
		decbuffer = new FileNode(null, "decbuff");
		decbuffer.setSourcePath(path);
	}
	
	public void setNCSD_keyY_seed(byte[] seed){
		this.secondary_keyY = seed;
	}
	
	/*----- Crypto -----*/
	
	public boolean isDecrypted(){
		if(decbuffer == null) return false;
		return (FileBuffer.fileExists(decbuffer.getSourcePath()));
	}
	
	public byte[] genPrimaryKey(CitrusCrypt crypto){
		//byte[] keyX = crypto.getKeyX(CitrusCrypt.KEYSLOT_NCC_0);
		byte[] keyY = new byte[16];
		for(int i = 0; i < 16; i++) keyY[i] = rsa_sig[i];
		crypto.setKeyY(CitrusCrypt.KEYSLOT_NCC_0, keyY);
		
		return crypto.getNormalKey(CitrusCrypt.KEYSLOT_NCC_0);
	}
	
	public byte[] genSecondaryKey(CitrusCrypt crypto){
		
		//Determine key...
		byte[] keyY = new byte[16];
		for(int i = 0; i < 16; i++) keyY[i] = rsa_sig[i];
		
		if((bitmask & 0x20) != 0){
			//This NCCH hates you.
			byte[] temp = new byte[0x20];
			for(int i = 0; i < 0x10; i++) temp[i] = rsa_sig[i];
			for(int i = 0x10; i < 0x20; i++) temp[i] = secondary_keyY[i];
			
			try{
				MessageDigest sha = MessageDigest.getInstance("SHA-256");
				sha.update(temp);
				byte[] hashbuff = sha.digest();
				for(int i = 0; i < 16; i++) keyY[i] = hashbuff[i];
			}
			catch(Exception x){
				x.printStackTrace();
			}
			
			//System.err.println("Evil 2ndary key >:(");
		}
		
		//Determine slot... (and check if apprioriate X is set...)
		switch(crypto_type){
		case 0x0:
			if(crypto.slotXEmpty(0x2C)) return null;
			crypto.setKeyY(0x2C, keyY);
			return crypto.getNormalKey(0x2C);
		case 0x1:
			if(crypto.slotXEmpty(0x25)) return null;
			crypto.setKeyY(0x25, keyY);
			return crypto.getNormalKey(0x25);
		case 0xA:
			if(crypto.slotXEmpty(0x18)) return null;
			crypto.setKeyY(0x18, keyY);
			return crypto.getNormalKey(0x18);
		case 0xB:
			if(crypto.slotXEmpty(0x1B)) return null;
			crypto.setKeyY(0x1B, keyY);
			return crypto.getNormalKey(0x1B);
		}
		
		return null;
	}
	
	public byte[] genIV(int type){
		byte[] iv = new byte[16];
		byte[] idbytes = FileBuffer.numToByStr(part_id);
		if(part_ver == 0 || part_ver == 2){
			for(int i = 0; i < 8; i++) iv[i] = idbytes[7-i];
			iv[8] = (byte)type;
		}
		else if(part_ver == 1){
			for(int i = 0; i < 8; i++) iv[i] = idbytes[i];
			//Last four are offset from NCCH to encrypted region
			int offval = 0;
			switch(type){
				case IV_TYPE_EXHEADER: offval = 0x200; break;
				case IV_TYPE_EXEFS: offval = (int)exefs_offset; break;
				case IV_TYPE_ROMFS: offval = (int)romfs_offset; break;
			}
			byte[] valbytes = FileBuffer.numToByStr(offval);
			for(int i = 0; i < 4; i++){
				iv[i+12] = valbytes[3-i];
			}
		}
		
		return iv;
	}
	
	public byte[] genIV(int type, long offset){
		
		byte[] iv = genIV(type);
		long add = offset >>> 4;
			
		System.err.println("Input IV: " + printHash(iv));
		System.err.println("Offset: 0x" + Long.toHexString(offset));
		System.err.println("Offset Units: 0x" + Long.toHexString(add));
		
		byte[] out = new byte[16];
		
		long mask = 0xFF;
		int shift = 0;
		boolean carry = false;
		for(int i = 15; i >= 0; i--){
			int a1 = Byte.toUnsignedInt(iv[i]);
			int a2 = (int)((add & mask) >>> shift);
			int sum = a1 + a2;
			if(carry) sum++;
			if(sum > 0xFF) carry = true;
			else carry = false;
			
			out[i] = (byte)sum;
			shift += 8;
			mask = mask << 8;
		}
		
		System.err.println("Output IV: " + printHash(out));
		
		return out;
	}
	
	public byte[] genSaveKeyY_Firm7(CitrusCrypt crypto, boolean firm){
		//TODO
		
		/*
		 * 1. Generate a mashup of...
		 * 	Start of CXI accessdesc sig [0x8] (at 0x400 in plaintext exheader)
		 * 	GetRomId [0x40] (0x10 bytes from the card followed by 0x30 of 0xFF bytes)
		 * 		I'm not 100% sure, but this appears to be extractable from gm9 via
		 * 		the "PARTID-v#-priv.bin" files
		 *  CXI Program ID [0x8] (That's in the NCC header, I think)
		 *  ExeFS .code hash [0x20] (Nab from parsed decrypted ExeFS metadata)
		 *  
		 *  ExeFS header and extended header need to be decrypted for this.
		 */
		
		/*
		 * 2. SHA-256 hash our mashup
		 */
		
		/*
		 * 3. Do a AES-CMAC over this hash.
		 * This key is generated by the firmware from the RSA engine :(
		 */
		
		return null;
	}
	
	public byte[] decryptData(FileBuffer data, CitrusCrypt crypto, int ivtype, boolean key2){
		//Setup
		byte[] iv = genIV(ivtype);
		byte[] key = null;
		if(!key2) key = genPrimaryKey(crypto);
		else key = genSecondaryKey(crypto);
		
		/*System.err.print("IV: ");
		for(int i = 0; i < 16; i++) System.err.print(String.format("%02x ", iv[i]));
		System.err.println();
		System.err.print("Key: ");
		for(int i = 0; i < 16; i++) System.err.print(String.format("%02x ", key[i]));
		System.err.println();*/
		
		AES aes = new AES(key);
		aes.setCTR();
		byte[] dec = aes.decrypt(iv, data.getBytes());
		
		return dec;
	}
	
	public boolean refreshDecBuffer(CitrusCrypt crypto, boolean verbose) throws IOException, UnsupportedFileTypeException{

		if(src == null) return false;
		if(decbuffer == null) return false;
		String decpath = decbuffer.getSourcePath();
		BufferedOutputStream dstr = new BufferedOutputStream(new FileOutputStream(decpath));
		boolean b = true;
		
		//Extended header (if applicable)
		long cpos = 0;
		if(isCXI()){
			if(verbose) System.err.println("Now decrypting extended header...");
			FileBuffer eheader_enc = src.loadData(EXHEADER_OFFSET, exHeader_size);
			FileBuffer eheader_dec = readExHeader(eheader_enc, 0, crypto);
			eheader_dec.writeToStream(dstr, 0, 0x400);
			cpos += 0x400;
			
			//Copy accessdesc and RSA key...
			FileBuffer rawdat = src.loadData(EXHEADER_OFFSET+0x400, 0x400);
			byte[] barr = new byte[0x100];
			for(int i = 0; i < 0x100; i++) barr[i] = rawdat.nextByte();
			exheader.setAccessDescSig(barr);
			
			//Copy RSA Key
			barr = new byte[0x100];
			for(int i = 0; i < 0x100; i++) barr[i] = rawdat.nextByte();		
			exheader.setPublicRSAKey(barr);
		}
		
		//ExeFS
		//Check if ExeFS is present
		byte[] key2 = genSecondaryKey(crypto);
		if(exefs_len > 0){
			if(verbose) System.err.println("Now decrypting ExeFS...");
			
			File tempfile = readExeFS(src.loadData(exefs_offset, exefs_len), 0, crypto);
			
			//Copy the header to the buffer
			String temppath = tempfile.getAbsolutePath();
			FileBuffer.createBuffer(temppath, 0, 0x200).writeToStream(dstr);
			
			//Nab the file tree...
			DirectoryNode exefs_root = exefs.getFileTree();
			List<FileNode> contents = exefs_root.getChildren();
			FileNode.setSortOrder(FileNode.SORT_ORDER_LOCATION);
			Collections.sort(contents);
			FileNode.setSortOrder(FileNode.SORT_ORDER_NORMAL);
			
			//Go through files in order
			//Decrypt (if needed), hash check, and copy so that offset relative to header is the same.
			long pos = 0;
			for(FileNode fn : contents){
				long myoff = fn.getOffset();
				while(pos < myoff) {dstr.write(0); pos++;}
				
				//Load file.
				long toff = myoff + 0x200;
				FileBuffer file_enc = FileBuffer.createBuffer(temppath, toff, toff+fn.getLength(), false);
				FileBuffer file_dec = null;
				String ftemppath = FileBuffer.generateTemporaryPath("exefs_file_decrypt");
				
				byte[] refhash = exefs.getFileHash(fn.getFileName());
				byte[] hashbuff = null;
				boolean goodhash = true;
				if(fn.getFileName().equals("banner") || fn.getFileName().equals("icon")){
					//It gets decrypted with initial ExeFS pass.
					//So just check hash and copy...
					try{
						MessageDigest sha = MessageDigest.getInstance("SHA-256");
						sha.update(file_enc.getBytes());
						hashbuff = sha.digest();
						goodhash = MessageDigest.isEqual(refhash, hashbuff);
						file_dec = file_enc;
					}
					catch(Exception x){
						x.printStackTrace();
						goodhash = false;
					}
				}
				else{
					//Needs to be decrypted again with secondary key
					if(key2 != null){
						file_enc = src.loadData(exefs_offset + 0x200 + fn.getOffset(), fn.getLength());
						AES aes = new AES(key2);
						aes.setCTR();
						aes.initDecrypt(genIV(IV_TYPE_EXEFS, 0x200 + fn.getOffset()));
						
						MessageDigest sha = null;
						try{sha = MessageDigest.getInstance("SHA-256");}
						catch(Exception x){
							x.printStackTrace();
							goodhash = false;
						}
						
						BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(ftemppath));
						long remain = fn.getLength();
						long fpos = 0;
						boolean last = false;
						while(remain > 0){
							int bsz = 0x200;
							if(remain < bsz){
								bsz = (int)remain;
								last = true;
							}
							
							byte[] in = file_enc.getBytes(fpos, fpos+bsz);
							byte[] out = aes.decryptBlock(in, last);
							sha.update(out);
							bos.write(out);
							
							remain -= bsz; fpos += bsz;
						}
						
						bos.close();
						
						hashbuff = sha.digest();
						goodhash = MessageDigest.isEqual(refhash, hashbuff);
						
						file_dec = FileBuffer.createBuffer(ftemppath);	
					}
					else goodhash = false;
				}
				
				if(!goodhash){
					//If fail, then write encrypted data to buffer and add ".aes" to file name
					if(verbose){
						System.err.println("ExeFS File \"" + fn.getFileName() + "\" failed hash check or otherwise could not be decrypted.");
						System.err.println("Ref Hash: " + printHash(refhash));
						System.err.println("DecBuffer Hash: " + printHash(hashbuff));
					}
					b = false;
					fn.setFileName(fn.getFileName() + ".aes");
					file_enc.writeToStream(dstr);
					//dstr.write(decdat);
				}
				else{
					//If pass, write decrypted data to buffer
					file_dec.writeToStream(dstr);
					if(verbose) System.err.println("Successfully decrypted ExeFS/" + fn.getFileName());
				}
				
				//Delete temp file, if present
				Files.deleteIfExists(Paths.get(ftemppath));
				
				//Update file node
				fn.setSourcePath(decpath);
				fn.setOffset(fn.getOffset() + cpos + 0x200);
				
				//Advance position
				pos += fn.getLength();
			}
			
			//Delete temp file
			Files.deleteIfExists(Paths.get(temppath));
			
			while(pos < (exefs_len-0x200)) {dstr.write(0); pos++;}
			cpos += this.exefs_len;
		}

		//RomFS
		if(romfs_len > 0 && (key2 != null)){
			if(verbose) System.err.println("Now decrypting RomFS...");
			System.err.println("RomFS Hash Size: 0x" + Long.toHexString(this.romfs_hashregsize));
			
			//I think just the entire section is encrypted with the secondary key.
			//So don't need to break it up like with ExeFS
			//I think I can get away with doing it block by block (since don't want to load all into memory)
			
			//FileBuffer romfs_raw = FileBuffer.createBuffer(src.getSourcePath(), romfs_offset, romfs_offset+romfs_len, false);
			//FileBuffer romfs_raw = src.loadData(romfs_offset, romfs_offset+romfs_len);
			BufferedInputStream romfs_is = new BufferedInputStream(new FileInputStream(src.getSourcePath()));
			romfs_is.skip(src.getOffset() + romfs_offset);
			final int blocksize = 0x200;
			
			//Prepare decryption stuff
			byte[] iv = genIV(IV_TYPE_ROMFS);
			AES aes = new AES(key2);
			aes.setCTR();
			aes.initDecrypt(iv);
			
			//Prepare hash stuff
			MessageDigest sha = null;
			try{sha = MessageDigest.getInstance("SHA-256");}
			catch(Exception x){
				System.err.println("Error instantiating SHA256 generator... Hash will not be checked!");
				x.printStackTrace();
			}
			
			long pos = 0;
			long remain = romfs_len;
			boolean last = false;
			boolean goodhash = true;
			while(remain > 0){
				int bsz = blocksize;
				if(remain < blocksize){
					bsz = (int)remain;
					last = true;
				}
				
				//byte[] encdat = romfs_raw.getBytes(pos, pos+bsz);
				byte[] encdat = new byte[bsz];
				romfs_is.read(encdat);
				byte[] decdat = aes.decryptBlock(encdat, last);
				dstr.write(decdat);
				
				pos += bsz;
				remain -= bsz;
				
				//Check hash
				sha.update(decdat);
				if(pos == romfs_hashregsize){
					byte[] outhash = sha.digest();
					if(verbose){
						System.err.println("RomFS SHA region processed ---");
						System.err.println("Ref Hash: " + printHash(romfs_hash));
						System.err.println("DecBuffer Hash: " + printHash(outhash));
					}
					
					//Compare
					if(!MessageDigest.isEqual(romfs_hash, outhash)){
						//Stop - key is wrong. No point in continuing.
						if(verbose) System.err.println("RomFS decryption failed! (Incorrect hash). Terminating RomFS decryption...");
						goodhash = false; b = false;
						break;
					}
				}
			}
			
			if(goodhash){
				//Parse RomFS
				if(verbose) System.err.println("RomFS decryption complete! Now parsing file system...");
				readRomFS();
			}
			
		}
		
		dstr.close();
		return b;
	}
	
	/*----- Definitions -----*/
	
	private static NCCHeaderDef header_def;
	private static NCCAccessDescDef accessdesc_def;
	private static NCCExHeaderDef exheader_def;
	private static NCCPlainDatDef plaindat_def;
	
	public static NCCHeaderDef getHeaderDef(){
		if(header_def == null) header_def = new NCCHeaderDef();
		return header_def;
	}
	
	public static NCCAccessDescDef getAccessDescDef(){
		if(accessdesc_def == null) accessdesc_def = new NCCAccessDescDef();
		return accessdesc_def;
	}
	
	public static NCCExHeaderDef getExHeaderDef(){
		if(exheader_def == null) exheader_def = new NCCExHeaderDef();
		return exheader_def;
	}
	
	public static NCCPlainDatDef getPlainRegDef(){
		if(plaindat_def == null) plaindat_def = new NCCPlainDatDef();
		return plaindat_def;
	}
	
	public static class NCCHeaderDef extends GenericSystemDef{
		
		private static String DEFO_ENG_DESC = "Nintendo 3DS NCCH Header";
		public static int TYPE_ID = 0x3dcc6903;

		public NCCHeaderDef(){
			super(DEFO_ENG_DESC, TYPE_ID);
		}

	}
	
	public static class NCCAccessDescDef extends GenericSystemDef{
		
		private static String DEFO_ENG_DESC = "Nintendo 3DS NCCH Access Descriptor";
		public static int TYPE_ID = 0x3dacce55;

		public NCCAccessDescDef(){super(DEFO_ENG_DESC, TYPE_ID);}

	}
	
	public static class NCCExHeaderDef extends GenericSystemDef{
		
		private static String DEFO_ENG_DESC = "Nintendo 3DS CXI Extended Header";
		public static int TYPE_ID = 0x3de75628;

		public NCCExHeaderDef(){super(DEFO_ENG_DESC, TYPE_ID);}

	}
	
	public static class NCCPlainDatDef extends GenericSystemDef{
		
		private static String DEFO_ENG_DESC = "Nintendo 3DS CXI Plain Binary Data";
		public static int TYPE_ID = 0x3db1b148;

		public NCCPlainDatDef(){super(DEFO_ENG_DESC, TYPE_ID);}

	}
	
	/*----- Debug -----*/
	
	public void printToStdErr(){
		System.err.println("===== Citrus NCC Container =====");
		System.err.println("Partition ID: " + Long.toHexString(part_id));
		System.err.println("Size: 0x" + Long.toHexString(ncsd_size));
		System.err.println("Program ID: " + Long.toHexString(program_id));
		System.err.println("Product Code: " + product_code);
		System.err.println("Maker Code: " + maker_code);
		System.err.println("Partition Version: " + part_ver);
		System.err.println("Extended Header Size: 0x" + Long.toHexString(exHeader_size));
		System.err.println("Crypto Type: " + crypto_type);
		System.err.println("For New3DS: " + is_new3ds);
		System.err.println("Data Flag: " + ((content_type_mask & 0x01) != 0));
		System.err.println("Executable Flag: " + ((content_type_mask & 0x02) != 0));
		System.err.println("System Update Flag: " + ((content_type_mask & 0x04) != 0));
		System.err.println("Manual Flag: " + ((content_type_mask & 0x08) != 0));
		System.err.println("Trial Flag: " + ((content_type_mask & 0x10) != 0));
		System.err.println("Content Size: 0x" + Long.toHexString(content_unit_size));
		System.err.println("Bitmask: 0x" + String.format("%02x", bitmask));
		
		System.err.println("Plain Region: 0x" + Long.toHexString(plain_offset) + " - 0x" + Long.toHexString(plain_offset+plain_len));
		System.err.println("Logo Region: 0x" + Long.toHexString(logo_offset) + " - 0x" + Long.toHexString(logo_offset+logo_len));
		System.err.println("ExeFS Region: 0x" + Long.toHexString(exefs_offset) + " - 0x" + Long.toHexString(exefs_offset+exefs_len));
		System.err.println("RomFS Region: 0x" + Long.toHexString(romfs_offset) + " - 0x" + Long.toHexString(romfs_offset+romfs_len));
		
		System.err.println("ExeFS Hash Region Size: 0x" + Long.toHexString(exefs_hashregsize));
		System.err.println("RomFS Hash Region Size: 0x" + Long.toHexString(romfs_hashregsize));
	}

	public static String printHash(byte[] hash){
		if(hash == null) return "<NULL>";
		
		int chars = hash.length << 1;
		StringBuilder sb = new StringBuilder(chars+2);
		
		for(int i = 0; i < hash.length; i++) sb.append(String.format("%02x", hash[i]));
		
		return sb.toString();
	}
	
}
