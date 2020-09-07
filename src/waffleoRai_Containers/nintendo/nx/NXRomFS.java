package waffleoRai_Containers.nintendo.nx;

import java.util.Collection;

import waffleoRai_Encryption.FileCryptRecord;
import waffleoRai_Encryption.FileCryptTable;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Files.tree.DirectoryNode;
import waffleoRai_Files.tree.FileNode;

public class NXRomFS{
	
	/*----- Constants -----*/
	
	/*----- Structs -----*/
	
	private static class DirRecord{
		public DirectoryNode dir;
		public int sib;
	}
	
	/*----- Instance Variables -----*/
	
	//private String src_path;
	//private long src_offset;
	
	private long hdr_size;
	
	private long dtbl_off;
	private long dtbl_size;
	private long ftbl_off;
	private long ftbl_size;
	
	private long fdat_off;
	
	private DirectoryNode root;
	
	/*----- Construction/Parsing -----*/
	
	private NXRomFS(){}
	
	public static NXRomFS readNXRomFSHeader(FileBuffer data, long offset){
		NXRomFS romfs = new NXRomFS();
		
		//Note that these are a guess. Should probably override if this'll be a problem later on...
		//romfs.src_path = data.getPath();
		//romfs.src_offset = offset;
		
		data.setEndian(false);
		
		//Header
		data.setCurrentPosition(offset);
		romfs.hdr_size = data.nextLong();
		data.skipBytes(16);
		romfs.dtbl_off = data.nextLong();
		romfs.dtbl_size = data.nextLong();
		data.skipBytes(16);
		romfs.ftbl_off = data.nextLong();
		romfs.ftbl_size = data.nextLong();
		romfs.fdat_off = data.nextLong();
		
		/*System.err.println("Header Size: 0x" + romfs.hdr_size);
		System.err.println("Dir Table Offset: 0x" + romfs.dtbl_off);
		System.err.println("Dir Table Size: 0x" + romfs.dtbl_size);
		System.err.println("File Table Offset: 0x" + romfs.ftbl_off);
		System.err.println("File Table Size: 0x" + romfs.ftbl_size);
		System.err.println("File Data Offset: 0x" + romfs.fdat_off);*/
		
		return romfs;
	}
	
	private DirRecord parseDir(FileBuffer data, long rec_off, long doff, long foff, DirectoryNode parent){
		
		//System.err.println("Directory @ 0x" + Long.toHexString(rec_off));
		
		//Read entry
		DirRecord rec = new DirRecord();
		data.setCurrentPosition(doff + rec_off + 4L); //Skip parent dir
		rec.sib = data.nextInt();
		int dch_off = data.nextInt();
		int fch_off = data.nextInt();
		data.skipBytes(4L);
		int nlen = data.nextInt();
		
		long pos = data.getCurrentPosition();
		String name = "";
		if(nlen > 0) name = data.readEncoded_string("UTF8", pos, pos+nlen);
		rec.dir = new DirectoryNode(parent, name);
		
		//Handle children
		while(fch_off != -1){
			//Files
			//System.err.println("File @ 0x" + Long.toHexString(fch_off));
			data.setCurrentPosition(foff + fch_off + 4L);
			fch_off = data.nextInt();
			long offset = data.nextLong();
			long size = data.nextLong();
			data.skipBytes(4L);
			nlen = data.nextInt();
			
			pos = data.getCurrentPosition();
			name = data.readEncoded_string("UTF8", pos, pos+nlen);
			FileNode fn = new FileNode(rec.dir, name);
			fn.setOffset(offset + fdat_off);
			fn.setLength(size);
			
			//Generate a UID
			/*long fuid = offset + fdat_off;
			fuid ^= size;
			fuid |= ((long)name.hashCode()) << 32;
			fn.setUID(fuid);
			fn.setMetadataValue(NinCrypto.METAKEY_FILEUID, Long.toHexString(fuid));*/
		}
		while(dch_off != -1){
			//Child directories
			DirRecord crec = parseDir(data, dch_off, doff, foff, rec.dir);
			dch_off = crec.sib;
		}
		
		return rec;
	}
	
	public void readTree(FileBuffer data, long stoff, long romfs_off){
		//stoff is the start offset in the data
		//romfs_off is the value relative to the RomFS start that stoff represents
		
		data.setEndian(false);
		
		long doff = dtbl_off - romfs_off + stoff;
		long foff = ftbl_off - romfs_off + stoff;
		
		//System.err.println("doff = 0x" + Long.toHexString(doff));
		//System.err.println("foff = 0x" + Long.toHexString(foff));
		
		//Looks like it's the same as 3DS
		root = parseDir(data, 0L, doff, foff, null).dir;
	}
	
	/*----- Getters -----*/
	
	public long getHeaderSize(){return hdr_size;}
	public long getDirTableOffset(){return dtbl_off;}
	public long getDirTableSize(){return dtbl_size;}
	public long getFileTableOffset(){return ftbl_off;}
	public long getFileTableSize(){return ftbl_size;}
	
	public DirectoryNode getFileTree(){
		return root;
	}
	
	public Collection<FileCryptRecord> addEncryptionInfo(FileCryptTable table){
		//Handled by partition
		return null;
	}
	
	/*----- Setters -----*/
	
	/*public void setSource(String path, long offset){
		src_path = path;
		src_offset = offset;
	}*/
	
	/*----- Extraction -----*/
	

}
