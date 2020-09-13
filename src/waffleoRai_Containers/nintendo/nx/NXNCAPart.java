package waffleoRai_Containers.nintendo.nx;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;

import waffleoRai_Containers.nintendo.nx.SwitchNCA.BKTREntry;
import waffleoRai_Containers.nintendo.nx.SwitchNCA.FSHashInfo;
import waffleoRai_Containers.nintendo.nx.SwitchNCA.IVFCHashInfo;
import waffleoRai_Containers.nintendo.nx.SwitchNCA.ShaHashInfo;
import waffleoRai_Encryption.AES;
import waffleoRai_Encryption.FileCryptRecord;
import waffleoRai_Encryption.FileCryptTable;
import waffleoRai_Encryption.nintendo.NinCTRCryptRecord;
import waffleoRai_Encryption.nintendo.NinCrypto;
import waffleoRai_Encryption.nintendo.NinXTSCryptRecord;
import waffleoRai_Files.EncryptionDefinition;
import waffleoRai_Files.FileNodeModifierCallback;
import waffleoRai_Files.FileTypeDefNode;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;
import waffleoRai_fdefs.nintendo.NXSysDefs;
import waffleoRai_fdefs.nintendo.NinDefs;
import waffleoRai_Files.tree.DirectoryNode;
import waffleoRai_Files.tree.FileNode;

public class NXNCAPart {
	
	/*----- Constants -----*/
	
	/*----- Instance Variables -----*/
	
	private int version;
	private int fs_type;
	private int hash_type;
	private int enc_type;
	
	private FSHashInfo hash_info;
	private BKTREntry[] patch_info;
	
	private int generation;
	private int secure_val;
	private byte[] sparse_info;
	
	private long offset; //Relative to NCA start
	private long size;
	
	private byte[] key;
	
	private DirectoryNode root;
	
	private NXPatchInfo patchdat; //Only if applicable
	
	private String container_path; //This is mostly used for making unique
	
	/*----- Getters -----*/
	
	public long getOffset(){return offset;}
	public long getSize(){return size;}
	public int getFSType(){return fs_type;}
	public int getHashType(){return hash_type;}
	public int getEncryptionType(){return enc_type;}
	public FSHashInfo getHashInfo(){return hash_info;}
	public BKTREntry[] getPatchInfo(){return patch_info;}
	public byte[] getSparseInfo(){return sparse_info;}
	public byte[] getKey(){return key;}
	
	public boolean isBKTRPartition(){
		if(patch_info == null) return false;
		if(patch_info.length < 2) return false;
		for(int i = 0; i < 2; i++){
			if(patch_info[i] == null) return false;
			if(patch_info[i].size <= 0L) return false;
		}
		return enc_type == SwitchNCA.NCA_CRYPTTYPE_AESCTR_BKTR;
	}
	
	public boolean isRomFS(){
		if(hash_info == null) return false;
		return hash_info instanceof SwitchNCA.IVFCHashInfo;
	}
	
	public long getDataOffset(){
		if(hash_info instanceof SwitchNCA.IVFCHashInfo){
			IVFCHashInfo hashdat = (IVFCHashInfo)hash_info;
			int levels = hashdat.offsets.length;
			int lowest = levels;
			for(int i = levels-1; i >= 0; i--){
				if(hashdat.sizes[i] > 0){lowest = i; break;}
			}
			
			return hashdat.offsets[lowest];
		}
		else if(hash_info instanceof SwitchNCA.ShaHashInfo){
			ShaHashInfo hashdat = (ShaHashInfo)hash_info;
			return hashdat.rel_offset;
		}
		return 0L;
	}
	
	public NXPatchInfo getPatchData(){return this.patchdat;}
	
	/*----- Setters -----*/
	
	public void setVersion(int val){version = val;}
	public void setFSType(int val){fs_type = val;}
	public void setHashType(int val){hash_type = val;}
	public void setEncryptionType(int val){enc_type = val;}
	
	public void setHashInfo(FSHashInfo info){hash_info = info;}
	public void setPatchInfo(BKTREntry[] info){patch_info = info;}
	
	public void setGeneration(int val){generation = val;}
	public void setSecureValue(int val){secure_val = val;}
	public void setSparseInfo(byte[] val){sparse_info = val;}
	
	public void setOffset(long val){offset = val;}
	public void setSize(long val){size = val;}
	
	public void setKey(byte[] k){key = k;}
	public void setContainerPath(String s){this.container_path = s;}
	
	/*----- Crypto -----*/
	
	public long updateCryptRegUID(){
		long val = 0L;
		if(hash_info != null) val = hash_info.getQuickHash();
		if(container_path != null) val ^= container_path.hashCode();
		return val ^ ((offset << 32) ^ size);
	}
	
	public byte[] genCTR(long part_offset){

		byte[] ctr = new byte[16];
		int shamt = 24;
		for(int i = 0; i < 4; i++){
			ctr[i] = (byte)((secure_val >>> shamt) & 0xFF);
			ctr[i+4] = (byte)((generation >>> shamt) & 0xFF);
			shamt -= 8;
		}
		
		long ofs = (offset + part_offset) >>> 4;
		//long ofs = (part_offset) >>> 4;
		for(int i = 0; i < 8; i++){
			ctr[16-i-1] = (byte)(ofs & 0xFF);
			ofs = ofs >>> 8;
		}
		
		return ctr;
	}
	
	public boolean decryptRegion(InputStream input, OutputStream output, long part_offset, long length) throws IOException{
		if(key == null && enc_type != SwitchNCA.NCA_CRYPTTYPE_NONE) {
			System.err.println("NXNCAPart.decryptRegion || No key set for partition!");
			return false;
		}

		byte[] iv = null;
		
		//Generate the decryptor object...
		if(enc_type == SwitchNCA.NCA_CRYPTTYPE_AESCTR || enc_type == SwitchNCA.NCA_CRYPTTYPE_AESCTR_BKTR){
			AES aes = new AES(key);
			aes.setCTR();
			iv = genCTR(part_offset);
			long row_offset = part_offset & (~0xF);
			long pos = row_offset;
			
			aes.initDecrypt(iv);
			long remaining = length;
			byte[] enc = new byte[16];
			while(remaining > 0){
				input.read(enc);
				byte[] dec = aes.decryptBlock(enc, (remaining <= 16));
				
				int off = 0;
				int len = 16;
				if(pos < part_offset){
					//Trim bytes off the front
					off = (int)(part_offset - pos);
					len -= off;
				}
				if(remaining < len){
					len = (int)remaining;
				}
				
				output.write(dec, off, len);
				remaining -= len;
				pos += len;
			}
		}
		else if(enc_type == SwitchNCA.NCA_CRYPTTYPE_AESXTS){
			//Do 0x200 at a time
			long sec = part_offset >>> 9;
			long scount = length >>> 9;
			byte[] enc = new byte[0x200];
			long pos = part_offset & (~0x1FF);
			long remaining = length;
			for(int s = 0; s < scount; s++){
				input.read(enc);
				byte[] dec = NXCrypt.decrypt_AESXTS_sector(key, enc, sec++);
				
				int off = 0;
				int len = 0x200;
				if(pos < part_offset){
					//Trim bytes off the front
					off = (int)(part_offset - pos);
					len -= off;
				}
				if(remaining < len){
					len = (int)remaining;
				}
				
				output.write(dec, off, len);
				remaining -= len;
				pos += len;
			}
		}
		else if (enc_type == SwitchNCA.NCA_CRYPTTYPE_NONE){
			//Copy
			for(long i = 0; i < length; i++) output.write(input.read());
		}
		
		return true;
	}
	
	public FileCryptRecord addEncryptionInfo(FileCryptTable table){
		
		FileCryptRecord rec = null;
		long cuid = this.updateCryptRegUID();
		
		int kidx = -1;
		switch(enc_type){
		case SwitchNCA.NCA_CRYPTTYPE_AESCTR:
		case SwitchNCA.NCA_CRYPTTYPE_AESCTR_BKTR:
			rec = new NinCTRCryptRecord(cuid);
			rec.setIV(genCTR(0L));
			rec.setKeyType(NinCrypto.KEYTYPE_128);
			kidx = table.getIndexOfKey(NinCrypto.KEYTYPE_128, key);
			if(kidx == -1) kidx = table.addKey(NinCrypto.KEYTYPE_128, key);
			rec.setKeyIndex(kidx);
			break;
		case SwitchNCA.NCA_CRYPTTYPE_NONE: return null;
		case SwitchNCA.NCA_CRYPTTYPE_AESXTS:
			NinXTSCryptRecord xtsr = new NinXTSCryptRecord(cuid);
			xtsr.setSector(0L);
			rec = xtsr;
			rec.setKeyType(NinCrypto.KEYTYPE_256);
			kidx = table.getIndexOfKey(NinCrypto.KEYTYPE_256, key);
			if(kidx == -1) kidx = table.addKey(NinCrypto.KEYTYPE_256, key);
			rec.setKeyIndex(kidx);
			break;
		}
		
		rec.setCryptOffset(0); //For now, relative to partition start. Should be updated as needed.
		//This field as used here is to indicate start of encrypted region.
		//That way offsets of files can be used to generate tweak/CTR as needed.
		
		table.addRecord(cuid, rec);
		
		//tag all file tree nodes with this UID
		if(root != null) root.setMetaValueForTree(NinCrypto.METAKEY_CRYPTGROUPUID, Long.toHexString(cuid));
		
		return rec;
	}
	
	public boolean readPatchInfo(String src_path, long part_off) throws IOException{

		if(patch_info == null) return false;
		
		long sz1 = patch_info[0].size;
		long sz2 = patch_info[1].size;
		if(sz1 <= 0 || sz2 <= 0) return false;
		
		long off1 = patch_info[0].offset;
		long off2 = patch_info[1].offset;
		
		//Figure out chunk to decrypt...
		long ed1 = off1 + sz1;
		long ed2 = off2 + sz2;
		
		long st = off1<off2?off1:off2;
		long ed = ed1>ed2?ed1:ed2;
		
		//Grab encrypted data
		FileBuffer src = FileBuffer.createBuffer(src_path, false);
		byte[] indat = src.getBytes(part_off + st, part_off + ed);
		
		//Create Streams
		InputStream in = new ByteArrayInputStream(indat);
		ByteArrayOutputStream out = new ByteArrayOutputStream((int)(ed-st));
		
		if(!decryptRegion(in, out, st, (ed-st))) return false;
		
		//Wrap the byte array from out and parse.
		FileBuffer pdat = FileBuffer.wrap(out.toByteArray());
		patchdat = NXPatchInfo.readFromNCA(pdat, off1-st, off2-st);
		
		return (patchdat != null);
	}
	
	/*----- Tree -----*/
	
	public boolean buildFileTree(FileBuffer src, long offset, boolean includeFSFiles){
		//Assumes offset is the start of the partition
		if(key == null && enc_type != SwitchNCA.NCA_CRYPTTYPE_NONE) {
			System.err.println("NXNCAPart.decryptRegion || No key set for partition!");
			return false;
		}
		
		//All offsets are relative to partition
		root = new DirectoryNode(null, "");
		
		//If it's a patch, just divide into patch lumps...
		if(enc_type == SwitchNCA.NCA_CRYPTTYPE_AESCTR_BKTR){
			long cuid = updateCryptRegUID();
			long pstart = patch_info[0].offset;
			long pend = patch_info[0].offset + patch_info[0].size;
			
			FileNode fn = new FileNode(root, "reloc.bktr1");
			fn.setOffset(patch_info[0].offset);
			fn.setLength(patch_info[0].size);
			fn.addEncryption(NXSysDefs.getCTRCryptoDef());
			root.setMetaValueForTree(NinCrypto.METAKEY_CRYPTGROUPUID, Long.toHexString(cuid));
			
			fn = new FileNode(root, "subsec.bktr2");
			fn.setOffset(patch_info[1].offset);
			fn.setLength(patch_info[1].size);
			fn.addEncryption(NXSysDefs.getCTRCryptoDef());
			root.setMetaValueForTree(NinCrypto.METAKEY_CRYPTGROUPUID, Long.toHexString(cuid));
			if(patch_info[1].offset < pstart) pstart = patch_info[1].offset;
			long p2end = patch_info[1].offset + patch_info[1].size;
			if(p2end > pend) pend = p2end;
			
			fn = new FileNode(root, "patchdat.aes");
			if(pstart > 0L){
				fn.setOffset(0L);
				fn.setLength(pstart);
				
				if(pend < size){
					//Ends before end
					fn = new FileNode(root, "patchdat1.aes");
					fn.setOffset(pend);
					fn.setLength(size - pend);
				}
				
			}
			else{
				fn.setOffset(pend);
				fn.setLength(size - pend);
			}
			
			return true;
		}
		
		//Nab hash table(s) & adjust offset
		long datoff = 0L;
		if(hash_info instanceof SwitchNCA.IVFCHashInfo){
			IVFCHashInfo hashdat = (IVFCHashInfo)hash_info;
			int levels = hashdat.offsets.length;
			int lowest = levels;
			for(int i = levels-1; i >= 0; i--){
				if(hashdat.sizes[i] > 0){lowest = i; break;}
			}
			
			datoff = hashdat.offsets[lowest];
			if(includeFSFiles){
				for(int i = 0; i < lowest; i++){
					FileNode fn = new FileNode(root, "ivfc_hashlvl_" + i + ".sha256");
					fn.setTypeChainHead(new FileTypeDefNode(NinDefs.getSHA256Def()));
					fn.setOffset(hashdat.offsets[i]);
					fn.setLength(hashdat.sizes[i]);
				}	
			}
			//System.err.println("Lowest level: " + lowest + " offset = 0x" + Long.toHexString(datoff));
		}
		else if(hash_info instanceof SwitchNCA.ShaHashInfo){
			ShaHashInfo hashdat = (ShaHashInfo)hash_info;
			datoff = hashdat.rel_offset;
			
			if(includeFSFiles){
				FileNode fn = new FileNode(root, "pfs_hashtbl.sha256");
				fn.setTypeChainHead(new FileTypeDefNode(NinDefs.getSHA256Def()));
				fn.setOffset(hashdat.hash_table_offset);
				fn.setLength(hashdat.hash_table_size);	
			}
		}
		
		if(fs_type == 0){
			//RomFS
			//Give it 0x200 (though it's usually 0x50)
			long foff = offset + datoff;
			byte[] enc = src.getBytes(foff, foff+0x200);
			byte[] dec = null;
			//System.err.println("Data offset: 0x" + Long.toHexString(datoff));
			//System.err.println("Net offset: 0x" + Long.toHexString(foff));
			
			switch(enc_type){
			case SwitchNCA.NCA_CRYPTTYPE_AESCTR:
			case SwitchNCA.NCA_CRYPTTYPE_AESCTR_BKTR:
				byte[] ctr = genCTR(datoff);
				AES aes = new AES(key);
				aes.setCTR();
				dec = aes.decrypt(ctr, enc);
				break;
			case SwitchNCA.NCA_CRYPTTYPE_NONE:
				dec = enc;
				break;
			case SwitchNCA.NCA_CRYPTTYPE_AESXTS:
				long sector = datoff >>> 9;
				dec = NXCrypt.decrypt_AESXTS_sector(key, enc, sector);
				break;
			}
			
			FileBuffer header = FileBuffer.wrap(dec);
			header.setEndian(false);
			NXRomFS romfs = NXRomFS.readNXRomFSHeader(header, 0L);
			
			//Nab the boring stuff from the romfs
			FileNode fn = null;
			if(includeFSFiles){
				fn = new FileNode(root, "romfsh.bin");
				fn.setTypeChainHead(new FileTypeDefNode(NXSysDefs.getRomFSHeaderDef()));
				fn.setOffset(datoff);
				fn.setLength(romfs.getHeaderSize());	
			}
			
			long dtoff = romfs.getDirTableOffset();
			long ftoff = romfs.getFileTableOffset();
			long dtsz = romfs.getDirTableSize();
			long ftsz = romfs.getFileTableSize();
			
			if(includeFSFiles){
				fn = new FileNode(root, "dirtable.bin");
				fn.setTypeChainHead(new FileTypeDefNode(NXSysDefs.getRomFSTableDef()));
				fn.setOffset(datoff+dtoff);
				fn.setLength(dtsz);
				fn = new FileNode(root, "filetable.bin");
				fn.setTypeChainHead(new FileTypeDefNode(NXSysDefs.getRomFSTableDef()));
				fn.setOffset(datoff+ftoff);
				fn.setLength(ftsz);	
			}
			
			//Read the actual FS
			long stoff = dtoff;
			if(ftoff < stoff) stoff = ftoff;
			
			long ded = dtoff + dtsz;
			long fed = ftoff + ftsz;
			long edoff = (ded > fed)?ded:fed;

			//System.err.println("stoff = 0x" + Long.toHexString(stoff));
			//System.err.println("edoff = 0x" + Long.toHexString(edoff));
			long stoff_a = stoff;
			
			switch(enc_type){
			case SwitchNCA.NCA_CRYPTTYPE_AESCTR:
			case SwitchNCA.NCA_CRYPTTYPE_AESCTR_BKTR:
				stoff_a = stoff & ~0xF;
				enc = src.getBytes(foff + stoff_a, foff+edoff);
				
				byte[] ctr = genCTR(datoff + stoff_a);
				AES aes = new AES(key);
				aes.setCTR();
				dec = aes.decrypt(ctr, enc);
				break;
			case SwitchNCA.NCA_CRYPTTYPE_NONE:
				dec = enc;
				break;
			case SwitchNCA.NCA_CRYPTTYPE_AESXTS:
				stoff_a = stoff & ~0x1FF;
				long edoff_a = (edoff+1) & ~0x1FF;
				enc = src.getBytes(foff + stoff_a, foff+edoff_a);
				
				long sector = (datoff + stoff_a) >>> 9;
				dec = NXCrypt.decrypt_AESXTS_sector(key, enc, sector);
				break;
			}
			
			FileBuffer tabledat = FileBuffer.wrap(dec);
			tabledat.setEndian(false);
			
			long start = stoff - stoff_a;
			romfs.readTree(tabledat, start, stoff);
			
			DirectoryNode fsroot = romfs.getFileTree();
			fsroot.incrementTreeOffsetsBy(datoff);
			if(includeFSFiles){
				fsroot.setFileName("RomFS");
				fsroot.setParent(root);	
			}
			else root = fsroot;
		}
		else{
			//PFS
			long foff = offset + datoff;
			byte[] enc = src.getBytes(foff, foff+0x200);
			byte[] dec = null;
			
			switch(enc_type){
			case SwitchNCA.NCA_CRYPTTYPE_AESCTR:
			case SwitchNCA.NCA_CRYPTTYPE_AESCTR_BKTR:
				byte[] ctr = genCTR(datoff);
				AES aes = new AES(key);
				aes.setCTR();
				dec = aes.decrypt(ctr, enc);
				break;
			case SwitchNCA.NCA_CRYPTTYPE_NONE:
				dec = enc;
				break;
			case SwitchNCA.NCA_CRYPTTYPE_AESXTS:
				long sector = datoff >>> 9;
				dec = NXCrypt.decrypt_AESXTS_sector(key, enc, sector);
				break;
			}
			
			//Check the first row to see how bit this header will be...
			FileBuffer fsdat = FileBuffer.wrap(dec);
			fsdat.setEndian(false);
			int fcount = fsdat.intFromFile(4L);
			int stblsize = fsdat.intFromFile(8L);
			
			long hsize = 16 + (0x18 * fcount) + stblsize;
			long hdrsize = hsize;
			if(hsize > 0x200){
				//Redecrypt a bigger chunk
				hsize = ++hsize & (~0x1FF);
				
				enc = src.getBytes(foff, foff+hsize);

				switch(enc_type){
				case SwitchNCA.NCA_CRYPTTYPE_AESCTR:
				case SwitchNCA.NCA_CRYPTTYPE_AESCTR_BKTR:
					byte[] ctr = genCTR(datoff);
					AES aes = new AES(key);
					aes.setCTR();
					dec = aes.decrypt(ctr, enc);
					break;
				case SwitchNCA.NCA_CRYPTTYPE_NONE:
					dec = enc;
					break;
				case SwitchNCA.NCA_CRYPTTYPE_AESXTS:
					long sector = datoff >>> 9;
					dec = NXCrypt.decrypt_AESXTS_sector(key, enc, sector);
					break;
				}
				
				fsdat = FileBuffer.wrap(dec);
				fsdat.setEndian(false);
			}
			
			//Now parse
			try{
				NXPFS pfs = NXPFS.readPFS(fsdat, 0);
				
				DirectoryNode fsroot = pfs.getFileTree();
				fsroot.incrementTreeOffsetsBy(datoff);
				if(includeFSFiles){
					FileNode fn = new FileNode(root, "pfsheader.bin");
					fn.setTypeChainHead(new FileTypeDefNode(NXSysDefs.getPFSHeaderDef()));
					fn.setOffset(datoff);
					fn.setLength(hdrsize);
					
					fsroot.setFileName("PFS0");
					fsroot.setParent(root);
				}
				else root = fsroot;
				
			}
			catch(UnsupportedFileTypeException x){
				x.printStackTrace();
				return false;
			}
		}
		
		if(enc_type != SwitchNCA.NCA_CRYPTTYPE_NONE) root.setMetaValueForTree(NinCrypto.METAKEY_CRYPTGROUPUID, Long.toHexString(updateCryptRegUID()));
		
		//Mark with enc def
		EncryptionDefinition edef = null;
		switch(enc_type){
		case SwitchNCA.NCA_CRYPTTYPE_AESCTR:
		case SwitchNCA.NCA_CRYPTTYPE_AESCTR_BKTR:
			edef = NXSysDefs.getCTRCryptoDef();
			break;
		case SwitchNCA.NCA_CRYPTTYPE_AESXTS:
			edef = NXSysDefs.getXTSCryptoDef();
			break;
		}
		
		if(edef != null){
			EncryptionDefinition edef_final = edef; //Eat shit, JAva
			root.doForTree(new FileNodeModifierCallback(){

				public void doToNode(FileNode node) {
					//node.setEncryption(edef_final);
					node.addEncryption(edef_final);
				}
				
			});	
		}
		
		return true;
	}
	
	public DirectoryNode getFileTree(){
		return root;
	}

	/*----- Debug -----*/
	
	public void printMe(int tabs){
		String indent = "";
		if(tabs > 0){
			StringBuilder sb = new StringBuilder(tabs);
			for(int i = 0; i < tabs; i++)sb.append('\t');
			indent = sb.toString();
		}
		
		//System.err.println(indent + "------------- FSEntry -------------");
		System.err.println(indent + "Version: " + version);
		
		String s = "PartitionFS";
		if(fs_type == 0) s = "RomFS";
		System.err.println(indent + "Type: " + s);
		
		s = "Auto";
		switch(hash_type){
		case SwitchNCA.NCA_HASHTYPE_HIERSHA256: 
			s = "Hierarchical SHA-256";
			break;
		case SwitchNCA.NCA_HASHTYPE_IVFC:
			s = "IVFC";
			break;
		}
		System.err.println(indent + "Hash Type: " + s);
		
		switch(enc_type){
		case SwitchNCA.NCA_CRYPTTYPE_AUTO: 
			s = "Auto";
			break;
		case SwitchNCA.NCA_CRYPTTYPE_NONE: 
			s = "None";
			break;
		case SwitchNCA.NCA_CRYPTTYPE_AESXTS: 
			s = "AES-XTS (Older Firmware)";
			break;
		case SwitchNCA.NCA_CRYPTTYPE_AESCTR: 
			s = "AES-CTR";
			break;
		case SwitchNCA.NCA_CRYPTTYPE_AESCTR_BKTR: 
			s = "AES-CTR (BKTR)";
			break;
		}
		System.err.println(indent + "Encryption Type: " + s);
		
		if(hash_type != 0){
			System.err.println(indent + "Hash Info: ");
			hash_info.printMe(tabs+1);
		}
		
		if(patch_info != null){
			for(int i = 0; i < patch_info.length; i++){
				System.err.print(indent + "BKTR " + i + ": ");
				BKTREntry bktr = patch_info[i];
				if(bktr != null){
					long ed = bktr.offset + bktr.size;
					System.err.print("0x" + Long.toHexString(bktr.offset) + " - ");
					System.err.print("0x" + Long.toHexString(ed) + "\n");
				}
				else System.err.print("<NULL>\n");
			}
		}
		
		System.err.println(indent + "Key Generation: " + generation);
		System.err.println(indent + "Secure Value: 0x" + Integer.toHexString(secure_val));
		System.err.println(indent + "Offset: 0x" + Long.toHexString(offset));
		System.err.println(indent + "Size: 0x" + Long.toHexString(size));
	}
	
	public void printInfo(Writer out, int indents) throws IOException{

		String indent = "";
		if(indents > 0){
			StringBuilder sb = new StringBuilder(indents);
			for(int i = 0; i < indents; i++)sb.append('\t');
			indent = sb.toString();
		}
		
		//System.err.println(indent + "------------- FSEntry -------------");
		out.write(indent + "Version: " + version + "\n");
		
		String s = "PartitionFS (PFS)";
		if(fs_type == 0) s = "RomFS";
		out.write(indent + "Type: " + s + "\n");
		
		s = "Auto";
		switch(hash_type){
		case SwitchNCA.NCA_HASHTYPE_HIERSHA256: 
			s = "Hierarchical SHA-256";
			break;
		case SwitchNCA.NCA_HASHTYPE_IVFC:
			s = "IVFC";
			break;
		}
		out.write(indent + "Hash Type: " + s + "\n");
		
		switch(enc_type){
		case SwitchNCA.NCA_CRYPTTYPE_AUTO: 
			s = "Auto";
			break;
		case SwitchNCA.NCA_CRYPTTYPE_NONE: 
			s = "None";
			break;
		case SwitchNCA.NCA_CRYPTTYPE_AESXTS: 
			s = "AES-XTS (Older Firmware)";
			break;
		case SwitchNCA.NCA_CRYPTTYPE_AESCTR: 
			s = "AES-CTR";
			break;
		case SwitchNCA.NCA_CRYPTTYPE_AESCTR_BKTR: 
			s = "AES-CTR (BKTR)";
			break;
		}
		out.write(indent + "Encryption Type: " + s + "\n");
		if(key != null){
			out.write(indent + "Key: " + NXCrypt.printHash(key) + "\n");
		}
		else{
			out.write(indent + "Key: <Unset or N/A>\n");
		}
		
		if(hash_type != 0){
			out.write(indent + "Hash Info: \n");
			hash_info.printInfo(out, indents+1);
		}
		
		if(patch_info != null){
			for(int i = 0; i < patch_info.length; i++){
				out.write(indent + "BKTR " + i + ": ");
				BKTREntry bktr = patch_info[i];
				if(bktr != null){
					long ed = bktr.offset + bktr.size;
					out.write("0x" + Long.toHexString(bktr.offset) + " - ");
					out.write("0x" + Long.toHexString(ed) + "\n");
				}
				else out.write("<NULL>\n");
			}
		}
		
		out.write(indent + "Key Generation: " + generation + "\n");
		out.write(indent + "Secure Value: 0x" + Integer.toHexString(secure_val) + "\n");
		out.write(indent + "Offset: 0x" + Long.toHexString(offset) + "\n");
		out.write(indent + "Size: 0x" + Long.toHexString(size) + "\n");
	}
	
}
