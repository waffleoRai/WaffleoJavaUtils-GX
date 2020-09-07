package waffleoRai_Containers.nintendo;

import java.io.File;
import java.io.IOException;

import waffleoRai_Encryption.AES;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;
import waffleoRai_Files.tree.FileNode;

public class WiiSaveDataFile {
	
	/*----- Constants -----*/
	
	public static final long BANNER_OFFSET = 0x20;
	public static final long BKHD_OFFSET = 0xF0C0;
	public static final long FILES_OFFSET = 0xF140;
	
	public static final long FILEHDR_SIZE = 0x80;
	
	public static final short BKHDR_MAGIC = 0x426b;
	public static final int FILEHDR_MAGIC = 0x03adf17e;
	
	/*----- Static Variables -----*/
	
	protected static byte[] sd_key;
	protected static byte[] sd_iv;
	protected static byte[] sd_md5;
	
	/*----- Instance Variables -----*/
	
	private FileBuffer header_bin;
	private FileBuffer banner_bin;
	
	private long savegame_id;
	private long banner_sz;
	
	private int bkhd_version;
	private int ng_id;
	private int file_count;
	private int file_size;
	
	private String src_path;
	private FileHeader files[];
	
	/*----- Structs -----*/
	
	public static class FileHeader{
		
		public long offset; //After header
		public long size;
		public boolean isDir;
		public String filename;
		public byte[] iv;
		
	}
	
	/*----- Construction/Parsing -----*/
	
	private WiiSaveDataFile(){}
	
	public static WiiSaveDataFile readDataBin(FileBuffer data, boolean verbose) throws UnsupportedFileTypeException{
		
		//Instantiate
		WiiSaveDataFile savedat = new WiiSaveDataFile();
		savedat.src_path = data.getPath();
		
		//Attempt header & banner
		if(savedat.isDecryptable()){
			byte[] enc = data.getBytes(0, BKHD_OFFSET);
			AES aes = new AES(sd_key);
			byte[] dec = aes.decrypt(sd_iv, enc);
			
			//Wrap.
			savedat.header_bin = new FileBuffer((int)BANNER_OFFSET, true);
			for(int i = 0; i < BANNER_OFFSET; i++) savedat.header_bin.addToFile(dec[i]);
			
			//Parse header.
			savedat.savegame_id = savedat.header_bin.longFromFile(0L);
			savedat.banner_sz = Integer.toUnsignedLong(savedat.header_bin.intFromFile(8L));
			
			//Now that we have the banner size, wrap banner.
			savedat.banner_bin = new FileBuffer((int)savedat.banner_sz, true);
			for(int i = 0; i < savedat.banner_sz; i++) savedat.banner_bin.addToFile(dec[i+(int)BANNER_OFFSET]);
		}
		else if(verbose){
			System.err.println("WiiSaveDataFile.readDataBin || Warning: Keys have not been set -- encrypted data cannot be read!");
		}

		//Read Bk
		data.setCurrentPosition(BKHD_OFFSET);
		int bkhd_size = data.nextInt();
		short bkhd_mag = data.nextShort();
		if(bkhd_mag != BKHDR_MAGIC) throw new UnsupportedFileTypeException("WiiSaveDataFile.readDataBin || Bk header not found!");
		savedat.bkhd_version = (int)data.nextShort();
		savedat.ng_id = data.nextInt();
		savedat.file_count = data.nextInt();
		savedat.file_size = data.nextInt();
		
		//Copy file info
		data.setCurrentPosition(BKHD_OFFSET + bkhd_size + 0x10);
		savedat.files = new FileHeader[savedat.file_count];
		for(int i = 0; i < savedat.file_count; i++){
			FileHeader fh = new FileHeader();
			savedat.files[i] = fh;
			
			//Check magic...
			long cpos = data.getCurrentPosition();
			int fmag = data.nextInt();
			if(fmag == FILEHDR_MAGIC){
				fh.size = Integer.toUnsignedLong(data.nextInt());
				data.skipBytes(2); //Skip permissions and attributes for now.
				//Type
				byte t = data.nextByte();
				if(t == 2) fh.isDir = true;
				else fh.isDir = false;
				//Name
				fh.filename = data.readEncoded_string(WiiSaveBannerFile.STRENCODE, data.getCurrentPosition(), "\0");
				data.setCurrentPosition(cpos + 0x50);
				fh.iv = new byte[16];
				for(int j = 0; j < 16; j++) fh.iv[j] = data.nextByte();
				data.skipBytes(0x20);
				fh.offset = data.getCurrentPosition();
				
				data.skipBytes(fh.size);
			}
			else{
				if(verbose){
					System.err.println("WiiSaveDataFile.readDataBin || File header " + i + " could not be read! (Magic number misaligned)");
				}
				data.skipBytes(0x7c);
			}
			
		}
		
		return savedat;
	}
	
	/*----- Getters -----*/
	
	public boolean isDecryptable(){
		return (sd_key != null && sd_iv != null);
	}
	
	public long getSavegameID(){return savegame_id;}
	public long getBkHeaderVersion(){return bkhd_version;}
	public long getNGID(){return ng_id;}
	public long getFileCount(){return file_count;}
	public long getFileDataSize(){return file_size;}
	
	public FileBuffer getDecryptedHeader(){
		return this.header_bin;
	}
	
	public FileBuffer getDecryptedBanner(){
		return this.banner_bin;
	}
	
	public WiiSaveBannerFile getBanner() throws UnsupportedFileTypeException{
		if(!isDecryptable()) throw new UnsupportedFileTypeException("Decryption keys are unknown. Banner cannot be decrypted!");
		return WiiSaveBannerFile.readBannerBin(banner_bin);
	}
	
	public FileNode getFileInfo(int idx){
		if(files == null) throw new IndexOutOfBoundsException("Index " + idx + "invalid for data with no save files!");
		if(idx < 0 || idx >= files.length) throw new IndexOutOfBoundsException("Index " + idx + "invalid for set of " + files.length + " files!");
		
		FileHeader fh = files[idx];
		if(fh == null) return null;
		
		FileNode fn = new FileNode(null, fh.filename);
		fn.setOffset(fh.offset);
		fn.setLength(fh.size);
		StringBuilder ivstr = new StringBuilder(32);
		for(int i = 0; i < 16; i++) ivstr.append(String.format("%02x", fh.iv[i]));
		fn.setMetadataValue("SAVEIV", ivstr.toString());
		
		return fn;
	}
	
	public FileBuffer readFile(int idx) throws UnsupportedFileTypeException, IOException{
		if(!isDecryptable()) throw new UnsupportedFileTypeException("Decryption keys are unknown. File cannot be decrypted!");
		
		if(files == null) throw new IndexOutOfBoundsException("Index " + idx + "invalid for data with no save files!");
		if(idx < 0 || idx >= files.length) throw new IndexOutOfBoundsException("Index " + idx + "invalid for set of " + files.length + " files!");
		
		FileHeader fh = files[idx];
		if(fh == null) return null;
		
		FileBuffer dat = FileBuffer.createBuffer(src_path, fh.offset, fh.offset + fh.size, true);
		byte[] enc = dat.getBytes();
		
		AES aes = new AES(sd_key);
		byte[] dec = aes.decrypt(fh.iv, enc);
		
		return FileBuffer.wrap(dec);
	}
	
	/*----- Setters -----*/
	
	public static void set_sdKey(byte[] key){sd_key =key;}
	public static void set_sdIV(byte[] iv){sd_iv =iv;}
	public static void set_sdMD5Blanker(byte[] key){sd_md5 =key;}

	public void setSourcePath(String path){src_path = path;}
	
	/*----- Dump -----*/
	
	public boolean dumpFiles(String outdir) throws IOException, UnsupportedFileTypeException{
		if(!isDecryptable()) return false;
		
		String outpath = outdir + File.separator + "banner.bin";
		banner_bin.writeFile(outpath);
		
		if(files != null){
			for(int i = 0; i < files.length; i++){
				FileBuffer buff = readFile(i);
				FileHeader fh = files[i];
				outpath = outdir + File.separator + fh.filename;
				if(buff != null) buff.writeFile(outpath);
			}
		}
		
		return true;
	}
	
}
