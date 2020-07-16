package waffleoRai_Containers.nintendo.citrus;

import java.io.IOException;
import java.security.MessageDigest;

import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;

public class CitrusFirmImage {

	/*----- Constants -----*/
	
	public static final String MAGIC = "FIRM";
	
	/*----- Structs -----*/
	
	/*----- Instance Variables -----*/
	
	private FileBuffer loaded_data;
	
	private int boot_pri;
	private long arm11_entry;
	private long arm9_entry;
	
	private long[] sec_offset;
	private long[] sec_loadaddr;
	private long[] sec_size;
	private int[] sec_copytype;
	private byte[][] sec_hash;
	
	private byte[] firm_header_rsa;
	
	/*----- Construction/Parsing -----*/
	
	private CitrusFirmImage(){};
	
	public static CitrusFirmImage readFirmImage(String filepath) throws IOException, UnsupportedFileTypeException{
		CitrusFirmImage img = new CitrusFirmImage();
		img.loaded_data = FileBuffer.createBuffer(filepath, false);
		
		//Read FIRM header
		long mpos = img.loaded_data.findString(0, 0x10, MAGIC);
		if(mpos != 0) throw new FileBuffer.UnsupportedFileTypeException("3DS Firmware FIRM magic number not found!");
		
		FileBuffer dat = img.loaded_data;
		dat.setCurrentPosition(0x4);
		img.boot_pri = dat.nextInt();
		img.arm11_entry = Integer.toUnsignedLong(dat.nextInt());
		img.arm9_entry = Integer.toUnsignedLong(dat.nextInt());
		dat.skipBytes(0x30);
		
		img.sec_offset = new long[4];
		img.sec_loadaddr = new long[4];
		img.sec_size = new long[4];
		img.sec_copytype = new int[4];
		img.sec_hash = new byte[4][32];
		for(int i = 0; i < 4; i++){
			img.sec_offset[i] = Integer.toUnsignedLong(dat.nextInt());
			img.sec_loadaddr[i] = Integer.toUnsignedLong(dat.nextInt());
			img.sec_size[i] = Integer.toUnsignedLong(dat.nextInt());
			img.sec_copytype[i] = dat.nextInt();
			for(int j = 0; j < 32; j++){
				img.sec_hash[i][j] = dat.nextByte();
			}
		}
		
		img.firm_header_rsa = new byte[0x100];
		for(int i = 0; i < 0x100; i++) img.firm_header_rsa[i] = dat.nextByte();
		
		return img;
	}
	
	/*----- Getters -----*/
	
	public FileBuffer getSection(int idx, boolean checkHash) throws IOException{

		FileBuffer sec = loaded_data.createCopy(sec_offset[idx], sec_offset[idx] + sec_size[idx]);
		
		if(checkHash){
			byte[] bytes = sec.getBytes();
			try{
				MessageDigest sha = MessageDigest.getInstance("SHA-256");
				sha.update(bytes);
				byte[] hashbuff = sha.digest();
				if(!MessageDigest.isEqual(sec_hash[idx], hashbuff)){
					System.err.println("Hash check for section " + idx + " failed!");
					System.err.print("Reference Hash: ");
					for(int i = 0; i < sec_hash[idx].length; i++) System.err.print(String.format("%02x", sec_hash[idx]));
					System.err.println();
					System.err.print("Section Hash: ");
					for(int i = 0; i < hashbuff.length; i++) System.err.print(String.format("%02x", hashbuff));
					System.err.println();
					return null;
				}
			}
			catch(Exception x){
				x.printStackTrace();
				return null;
			}
		}
		
		
		return sec;
	}
	
	/*----- Setters -----*/
	
	/*----- Debug -----*/
	
	public void printToStdErr(){

		System.err.println("=== 3DS FIRM Image ===");
		System.err.println("Boot Priority: " + this.boot_pri);
		System.err.println("ARM9 Entry: 0x" + String.format("%08x", arm9_entry));
		System.err.println("ARM11 Entry: 0x" + String.format("%08x", arm11_entry));
		
		for(int i = 0; i < 4; i++){
			System.err.println();
			System.err.println("-> Section " + i);
			String st = "0x" + Long.toHexString(sec_offset[i]);
			String ed = "0x" + Long.toHexString(sec_offset[i] + sec_size[i]);
			System.err.println("FIRM Location: " + st + " - " + ed);
			st = "0x" + String.format("%08x", sec_loadaddr[i]);
			ed = "0x" + String.format("%08x", (sec_loadaddr[i] + sec_size[i]));
			System.err.println("Memory Target: " + st + " - " + ed);
		}
		
	}
	
}
