package waffleoRai_Containers.nintendo;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import waffleoRai_Image.EightBitPalette;
import waffleoRai_Image.Palette;
import waffleoRai_Image.Pixel_RGBA;
import waffleoRai_Image.nintendo.DolGraphics;
import waffleoRai_Utils.FileBuffer;

public class GCMemCard {
	
	//http://hitmen.c02.at/files/yagcd/yagcd/frames.html
	
	/*----- Constants -----*/
	
	//0010 0000 0000 0000
	
	public static final int OFFSET_DIR = 0x2000;
	public static final int OFFSET_BAM = 0x6000;
	public static final int OFFSET_DATA = 0xA000;
	
	public static final int SIZE_DIRENTRY = 0x40;
	public static final int BAM_ENTRY_COUNT = 0xFFC;
	
	public static final int COLORENC_RGB5A3 = 2;
	public static final int COLORENC_CI8_PLT = 3;
	public static final int COLORENC_CI8_COMMONPLT = 1;
	public static final int COLORENC_CI8 = 4;
	
	public static final int BLOCK_SIZE = 0x2000;
	
	public static final int ICO_FRAME_MILLIS = 30;
	
	/*----- Instance Variables -----*/
	
	private boolean enc_JPN; //If false, ASCII, if true... Shift-JIS?

	private DirEntry[] directory;
	private BlockMap blockMap;
	
	private String srcpath;
	private Map<String, GCMemCardFile> files;
	
	/*----- Structs -----*/

	public static class DirEntry{
		public String gamecode;
		public String makercode;
		
		public boolean anim_pingpong;
		public boolean hasBanner;
		public int banner_colorenc;
		
		public String filename;
		public OffsetDateTime modTime;
		public int img_dat_offset;
		
		public int[] icon_fr_colorenc;
		public int[] icon_fr_len;
		
		public int perm;
		public int copy_count;
		
		public int stBlock;
		public int blockCount;
		
		public int comment_offset;
		
		public static DirEntry readDirEntry(FileBuffer file, long offset){
			DirEntry e = new DirEntry();
			long cpos = offset;
			
			e.gamecode = file.getASCII_string(cpos, 4); cpos+=4;
			e.makercode = file.getASCII_string(cpos, 2); cpos+=2;
			cpos++;
			int flags = Byte.toUnsignedInt(file.getByte(cpos++));
			e.anim_pingpong = (flags & 0x4) != 0;
			e.hasBanner = (flags & 0x2) != 0;
			if((flags & 0x1) != 0) e.banner_colorenc = COLORENC_CI8;
			else e.banner_colorenc = COLORENC_RGB5A3;
			e.filename = file.getASCII_string(cpos, 0x20); cpos+=0x20;
			int modsecs = file.intFromFile(cpos); cpos+=4;
			e.modTime = OffsetDateTime.of(2000, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
			e.modTime = e.modTime.plusSeconds(modsecs);
			e.img_dat_offset = file.intFromFile(cpos); cpos+=4;
			flags = Short.toUnsignedInt(file.shortFromFile(cpos)); cpos+=2;
			//System.err.println(e.filename + " -- iconcolors: 0x" + String.format("%04x", flags));
			int shift = 14;
			e.icon_fr_colorenc = new int[8];
			for(int i = 0; i < 8; i++){
				e.icon_fr_colorenc[i] = (flags >>> shift) & 0x3;
				shift-=2;
			}
			flags = Short.toUnsignedInt(file.shortFromFile(cpos)); cpos+=2;
			///System.err.println(e.filename + " -- framelengths: 0x" + String.format("%04x", flags));
			shift = 14;
			e.icon_fr_len = new int[8];
			for(int i = 0; i < 8; i++){
				e.icon_fr_len[i] = ((flags >>> shift) & 0x3) << 2;
				shift-=2;
			}
			e.perm = Byte.toUnsignedInt(file.getByte(cpos++));
			e.copy_count = Byte.toUnsignedInt(file.getByte(cpos++)) << 1;
			e.stBlock = Short.toUnsignedInt(file.shortFromFile(cpos)); cpos+=2;
			e.blockCount = Short.toUnsignedInt(file.shortFromFile(cpos)); cpos+=2;
			cpos+=2;
			e.comment_offset = file.intFromFile(cpos);
			
			return e;
		}
		
		public void debugPrint(){
			System.err.println("----- " + filename + " -----");
			System.err.println("Game: " + gamecode + makercode);
			System.err.println("Banner: " + hasBanner + " | enc = " + banner_colorenc);
			System.err.println("Last Modified: " + modTime.format(DateTimeFormatter.RFC_1123_DATE_TIME));
			System.err.println("Icon Ping-Pong Animation: " + anim_pingpong);
			System.err.println("Image Data Offset: 0x" + Integer.toHexString(img_dat_offset));
			
			System.err.println("Image Frames ---");
			System.err.print("Colors: ");
			for(int i = 0; i < 8; i++){
				System.err.print(String.format("%01d ", icon_fr_colorenc[i]));
			}
			System.err.println();
			System.err.print("Length: ");
			for(int i = 0; i < 8; i++){
				System.err.print(String.format("%02d ", icon_fr_len[i]));
			}
			System.err.println();
			
			System.err.println("Permissions: 0x" + String.format("%02x", perm));
			System.err.println("Copy Count: " + copy_count);
			System.err.println("Start Block: " + stBlock);
			System.err.println("Block Count: " + blockCount);
			System.err.println("Comment Offset: 0x" + Integer.toHexString(comment_offset));
		}
		
	}
	
	public static class BlockMap{
		
		public short checksum1;
		public short checksum2;
		public int updateCounter;
		public int freeBlocks;
		public int lastBlock;
		
		public int[] map;
	}
	
	/*----- Construction -----*/
	
	private GCMemCard(){};
	
	/*----- Parsing -----*/
	
	public static GCMemCard readRawMemoryCardFile(String filepath) throws IOException{
		GCMemCard mc = new GCMemCard();
		mc.srcpath = filepath;
		
		FileBuffer file = FileBuffer.createBuffer(filepath, true);
		
		//Read header
		short enc = file.shortFromFile(0x24);
		if(enc != 0) mc.enc_JPN = true;
		
		//Read directory
		long cpos = OFFSET_DIR;
		int check = file.intFromFile(cpos);
		int i = 0;
		mc.directory = new DirEntry[128];
		while(i < 128 && check != 0xFFFFFFFF){
			mc.directory[i++] = DirEntry.readDirEntry(file, cpos);
			cpos+=SIZE_DIRENTRY;
			check = file.intFromFile(cpos);
		}
		
		//Read block map
		mc.blockMap = new BlockMap();
		cpos = OFFSET_BAM;
		mc.blockMap.checksum1 = file.shortFromFile(cpos); cpos+=2;
		mc.blockMap.checksum2 = file.shortFromFile(cpos); cpos+=2;
		mc.blockMap.updateCounter = Short.toUnsignedInt(file.shortFromFile(cpos)); cpos+=2;
		mc.blockMap.freeBlocks = Short.toUnsignedInt(file.shortFromFile(cpos)); cpos+=2;
		mc.blockMap.lastBlock = Short.toUnsignedInt(file.shortFromFile(cpos)); cpos+=2;
		
		mc.blockMap.map = new int[BAM_ENTRY_COUNT + 5];
		for(int j = 0; j < BAM_ENTRY_COUNT; j++){
			mc.blockMap.map[j+5] = Short.toUnsignedInt(file.shortFromFile(cpos)); cpos += 2;
		}
		
		//Read files
		mc.files = new HashMap<String, GCMemCardFile>();
		for(int j = 0; j < mc.directory.length; j++){
			mc.readMemCardFile(j, false);
		}
		
		return mc;
	}
	
	public static GCMemCardFile readGCIFile(String filepath) throws IOException{
		//GCIs appear to have a copy of the directory entry in the first 0x40 bytes
		//(thankfully)
		GCMemCard mc = new GCMemCard();
		mc.srcpath = filepath;
		
		FileBuffer file = FileBuffer.createBuffer(filepath, true);
		//Determine block count from size...
		long fsz = file.getFileSize();
		int blocks = (int)((fsz-0x40)/0x2000);
		
		mc.directory = new DirEntry[16];
		mc.blockMap = new BlockMap();
		mc.blockMap.freeBlocks = 0;
		mc.blockMap.lastBlock = blocks-1;
		
		//Read directory entry
		mc.directory[0] = DirEntry.readDirEntry(file, 0x00);
		
		//Read File
		GCMemCardFile mcf = mc.readMemCardFile(0, true);
		
		return mcf;
	}
	
	/*----- Image Parsing -----*/
	
	private GCMemCardFile readMemCardFile(int fidx, boolean gci) throws IOException{
		DirEntry e = directory[fidx];
		if(e == null) return null;
		GCMemCardFile mcf = new GCMemCardFile(e.filename, srcpath, e.blockCount);
		mcf.setGamecode(e.gamecode);
		mcf.setMakercode(e.makercode);
		
		//System.err.println("Reading file: " + e.filename);
		//e.debugPrint();
		
		//Block map
		if(gci){
			mcf.setGCI(true);
		}
		else{
			//System.err.println("Copying block table for file: " + e.filename);
			int k = 0;
			//System.err.println("Block -- " + e.stBlock);
			mcf.setBlockMapping(k++, e.stBlock);
			int b = e.stBlock;
			while(k < e.blockCount){
				b = blockMap.map[b];
				//System.err.println("Block -- " + b);
				mcf.setBlockMapping(k++, b);
				
			}
		}
		
		//Load Icon/Banner/Title data
		FileBuffer mcfile = mcf.loadFile();
		long fpos = e.img_dat_offset;
		//if(e.hasBanner){
			if(e.banner_colorenc == COLORENC_RGB5A3){
				BufferedImage img = readRGB5A3(mcfile, fpos, 96, 32);
				mcf.setBanner(img);
				fpos += 0x1800;
			}
			else{
				long pltoff = fpos + 0x0c00;
				Palette plt = readPalette(mcfile, pltoff);
				BufferedImage img = readCI8(mcfile, fpos, 96, 32, plt);
				mcf.setBanner(img);
				fpos += 0xc00 + 0x200;
			}
		//}
		
		//Icon Frames
		//Read common palette, if present
		boolean common_plt = false;
		long iend = fpos;
		for(int k = 0; k < 8; k++){
			switch(e.icon_fr_colorenc[k]){
			case COLORENC_CI8_COMMONPLT:
				common_plt = true;
				iend += 0x400;
				break;
			case COLORENC_CI8_PLT:
				iend += 0x600;
				break;
			case COLORENC_RGB5A3:
				iend += 0x800;
				break;
			}
		}
		
		Palette cmnplt = null;
		if(common_plt){
			cmnplt = readPalette(mcfile, iend);
		}
		
		for(int k = 0; k < 8; k++){
			if(e.icon_fr_colorenc[k] == 0) continue;
			BufferedImage img = null;
			switch(e.icon_fr_colorenc[k]){
			case COLORENC_CI8_COMMONPLT:
				img = readCI8(mcfile, fpos, 32, 32, cmnplt);
				fpos += 0x400;
				break;
			case COLORENC_CI8_PLT:
				Palette plt = readPalette(mcfile, fpos+0x400);
				img = readCI8(mcfile, fpos, 32, 32, plt);
				fpos += 0x600;
				break;
			case COLORENC_RGB5A3:
				img = readRGB5A3(mcfile, fpos, 32, 32);
				fpos += 0x800;
				break;
			}
			
			mcf.setIconFrame(k, img, e.icon_fr_len[k]);
		}
		mcf.setPingpongAnimation(e.anim_pingpong);
		
		//Comment strings
		String encname = "ASCII";
		if(enc_JPN) encname = "SJIS";
		long cmtoff = Integer.toUnsignedLong(e.comment_offset);
		mcf.setComment1(mcfile.readEncoded_string(encname, cmtoff, cmtoff+0x20));
		mcf.setComment2(mcfile.readEncoded_string(encname, cmtoff + 0x20, cmtoff+0x40));
		
		//Add to map
		if(files != null) files.put(e.filename, mcf);
		return mcf;
	}
	
	public static Palette readPalette(FileBuffer file, long offset){

		Palette p = new EightBitPalette();
		long cpos = offset;
		for(int i = 0; i < 256; i++){
			int val = Short.toUnsignedInt(file.shortFromFile(cpos)); cpos+=2;
			int argb = DolGraphics.RGB5A3_to_ARGB(val);
			int rgba = (argb << 8) | ((argb >>> 24) & 0xFF);
			
			p.setPixel(new Pixel_RGBA(rgba), i);
		}
		
		return p;
	}
	
	public static BufferedImage readRGB5A3(FileBuffer file, long offset, int w, int h){
		//4x4 tiles
		BufferedImage img = new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
		
		int tw = w >>> 2;
		int th = h >>> 2;
		int x = 0;
		int y = 0;
		
		long cpos = offset;
		for(int tr = 0; tr < th; tr++){
			for(int tl = 0; tl < tw; tl++){
				
				//Grab the next four pixels &  copy to image
				for(int r = 0; r<4; r++){
					for(int l = 0; l < 4; l++){
						int val = Short.toUnsignedInt(file.shortFromFile(cpos)); cpos+=2;
						int argb = DolGraphics.RGB5A3_to_ARGB(val);
						img.setRGB(x+l, y+r, argb);
					}
				}
	
				//Increment
				x+=4;
			}
			x=0; y+=4;
		}
		
		return img;
	}
	
	public static BufferedImage readCI8(FileBuffer file, long offset, int w, int h, Palette plt){
		//8x4 tiles
		BufferedImage img = new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
		
		int tw = w >>> 3;
		int th = h >>> 2;
		int x = 0;
		int y = 0;
		
		long cpos = offset;
		for(int tr = 0; tr < th; tr++){
			for(int tl = 0; tl < tw; tl++){
				
				//Grab the next four pixels &  copy to image
				for(int r = 0; r<4; r++){
					for(int l = 0; l < 8; l++){
						int val = Byte.toUnsignedInt(file.getByte(cpos++));
						img.setRGB(x+l, y+r, plt.getPixel(val).getARGB());
					}
				}
	
				//Increment
				x+=8;
			}
			x=0; y+=4;
		}
		
		return img;
	}
	
	/*----- Getters -----*/
	
	public GCMemCardFile getFileOfName(String fname){
		return files.get(fname);
	}
	
	public Collection<GCMemCardFile> getFilesFromGame(String gamecode4){
		List<GCMemCardFile> list = new LinkedList<GCMemCardFile>();
		if(files == null) return list;
		String gc = gamecode4.toUpperCase();
		for(GCMemCardFile mcf : files.values()){
			if(mcf.getGameCode().equals(gc)) list.add(mcf);
		}
		
		return list;
	}
	
	public Collection<GCMemCardFile> getFiles(){
		List<GCMemCardFile> list = new LinkedList<GCMemCardFile>();
		if(files == null) return list;
		list.addAll(files.values());
		return list;
	}
	
	/*----- Debug -----*/
	
	public void debugPrint(){
		if(directory == null){
			System.err.println("[No entries]");
			return;
		}
		
		for(int i = 0; i < directory.length; i++){
			if(directory[i] != null){
				directory[i].debugPrint();
				System.err.println();
			}
			//System.err.println();
		}

	}
	
}
