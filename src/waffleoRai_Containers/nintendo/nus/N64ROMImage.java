package waffleoRai_Containers.nintendo.nus;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

import waffleoRai_Files.WriterPrintable;
import waffleoRai_Utils.EncryptedFileBuffer;
import waffleoRai_Utils.FileBuffer;

public class N64ROMImage implements WriterPrintable{

	/*----- Constants -----*/
	
	public static final int MAGIC = 0x80371240;
	public static final int MAGIC_REVERSE = 0x40123780;
	public static final int MAGIC_HWS_LE = 0x12408037;
	public static final int MAGIC_HWS_BE = 0x37804012;
	public static final long OFFSET_BOOTCODE = 0x40;
	public static final long OFFSET_GAMECODE = 0x1000;

	public static final int ORDER_UNK = 0;
	public static final int ORDER_P64 = 1;
	public static final int ORDER_Z64 = 2;
	public static final int ORDER_N64 = 3;
	
	/*----- Instance Variables -----*/
	
	private int ordering;
	private int entry_point;
	private int crc1;
	private int crc2;
	private String name;
	private String gamecode;
	
	private NUSCICType cic_type;
	
	/*----- Initialization -----*/
	
	private N64ROMImage(){}
	
	public static N64ROMImage readROMHeader(String path) throws IOException{
		//Oh just switch it back to BE...
		FileBuffer dat = FileBuffer.createBuffer(path, 0, OFFSET_GAMECODE, true);
		
		//Check magic number for byte ordering.
		int magicno = dat.intFromFile(0);
		int order = ORDER_UNK;
		switch(magicno){
		case MAGIC:
			order = ORDER_Z64;
			break;
		case MAGIC_REVERSE:
			order = ORDER_P64;
			dat = new EncryptedFileBuffer(dat, new NUSDescrambler.NUS_Z64_ByteswapMethod());
			break;
		case MAGIC_HWS_BE:
			order = ORDER_N64;
			dat = new EncryptedFileBuffer(dat, new NUSDescrambler.NUS_N64_2BE_ByteswapMethod());
			break;
		default: return null;
		}
		
		N64ROMImage rom = new N64ROMImage();
		rom.ordering = order;
		rom.entry_point = dat.intFromFile(0x8L);
		rom.crc1 = dat.intFromFile(0x10L);
		rom.crc2 = dat.intFromFile(0x14L);
		
		rom.name = dat.getASCII_string(0x20L, 20);
		/*StringBuilder sb = new StringBuilder(24);
		boolean end = false;
		long off = 0x20L;
		for(int i = 0; i < 5; i++){
			if(end) break;
			for(int j = 3; j >= 0; j--){
				char c = (char)dat.getByte(off+j);
				sb.append(c);
				if(c == '\0'){
					end = true;
					break;
				}
			}
			off+=4L;
		}
		rom.name = sb.toString();*/
		
		//Gamecode
		rom.gamecode = dat.getASCII_string(0x3bL, 4);
		/*sb = new StringBuilder(8);
		sb.append((char)dat.getByte(0x38));
		sb.append((char)dat.getByte(0x3f));
		sb.append((char)dat.getByte(0x3e));
		sb.append((char)dat.getByte(0x3d));
		rom.gamecode = sb.toString();*/
		
		//CIC Type
		rom.cic_type = NUSCICType.determineCICType(dat);
		
		return rom;
	}
	
	public static FileBuffer loadROMasZ64(String path) throws IOException{
		
		FileBuffer dat = FileBuffer.createBuffer(path, true);
		
		//Check magic number for byte ordering.
		int magicno = dat.intFromFile(0);
		switch(magicno){
		case MAGIC:
			break;
		case MAGIC_REVERSE:
			dat = new EncryptedFileBuffer(dat, new NUSDescrambler.NUS_Z64_ByteswapMethod());
			break;
		case MAGIC_HWS_BE:
			dat = new EncryptedFileBuffer(dat, new NUSDescrambler.NUS_N64_2BE_ByteswapMethod());
			break;
		default: return null;
		}
		
		return dat;
	}
	
	public static FileBuffer adjustByteOrderToZ64(FileBuffer rom){
		int magicno = rom.intFromFile(0);
		switch(magicno){
		case MAGIC:
			break;
		case MAGIC_REVERSE:
			rom = new EncryptedFileBuffer(rom, new NUSDescrambler.NUS_Z64_ByteswapMethod());
			break;
		case MAGIC_HWS_BE:
			rom = new EncryptedFileBuffer(rom, new NUSDescrambler.NUS_N64_2BE_ByteswapMethod());
			break;
		default: return null;
		}
		return rom;
	}
	
	/*----- Getters -----*/
	
	public int getOrdering(){return ordering;}
	public int getEntryAddress(){return entry_point;}
	public int getCRC1(){return crc1;}
	public int getCRC2(){return crc2;}
	public NUSCICType getCICType(){return cic_type;}
	public String getName(){return name;}
	public String getGamecode(){return gamecode;}
	
	/*----- Debug -----*/
	
	public void printToStdErr(){
		try{
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(System.err));
			printMeTo(bw);
			bw.flush();
		}
		catch(IOException ex){
			ex.printStackTrace();
		}
	}
	
	public void printMeTo(Writer writer) throws IOException{
		writer.write("Name: " + name + "\n");
		writer.write("Gamecode: " + gamecode + "\n");
		writer.write("CRC1: " + String.format("%08x", crc1) + "\n");
		writer.write("CRC2: " + String.format("%08x", crc2) + "\n");
		writer.write("CIC Type: " + cic_type.name() + "\n");
		writer.write("Entry Addr: " + String.format("%08x", entry_point) + "\n");
		writer.write("Byte Ordering: ");
		switch(ordering){
			case ORDER_UNK: writer.write("<Unknown>"); break;
			case ORDER_P64: writer.write("Little-Endian MIPS Words (Project64 Style)"); break;
			case ORDER_Z64: writer.write("Big-Endian (z64 Style)"); break;
			case ORDER_N64: writer.write("Little-Endian Swapped Halfwords (v64/n64 Style)"); break;
		}
		writer.write("\n");
	}
	
	public static void main(String[] args){
		String rompath = "C:\\Users\\Blythe\\Documents\\Game Stuff\\N64\\Games\\MajorasMask_CollectorsEdition.z64";
		
		try{
			N64ROMImage hdr = N64ROMImage.readROMHeader(rompath);
			hdr.printToStdErr();
		}
		catch(Exception ex){
			ex.printStackTrace();
			System.exit(1);
		}
	}
	
}
