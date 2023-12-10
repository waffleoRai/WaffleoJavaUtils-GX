package waffleoRai_Image.psx;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.imageio.ImageIO;

import waffleoRai_Files.Converter;
import waffleoRai_Files.FileClass;
import waffleoRai_Files.FileTypeDefinition;
import waffleoRai_Files.tree.FileNode;
import waffleoRai_Image.BmpFile;
import waffleoRai_Image.PaletteGen;
import waffleoRai_Image.nintendo.NDSGraphics;
import waffleoRai_Image.psx.CLUTCompare.CLUTImgMatch;
import waffleoRai_Image.psx.CLUTCompare.CLUTMatch;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;
import waffleoRai_Utils.MultiFileBuffer;

public class QXImage {
	
	/*
	 * Little-Endian
	 * [2] Mystery flags?
	 * 		4 bit image appear to have 0x71 flagged
	 * 		8 bit have 0x51
	 * [2]	Frame count
	 * 
	 * Per Frame --
	 * [4] Bitmap offset (from file start, I think?)
	 * [4]	CLUT index
	 * [2]	Width (pix)
	 * [2]	Height (pix)
	 * [2] 	Scale factor x
	 * [2]	Scale factor y
	 * 
	 * Palette Bank --
	 * [4] Palette count
	 * 
	 * Palettes...
	 * Bitmaps...
	 * 
	 * If 4 bit, lower nibble comes first
	 * 
	 */
	
	/*----- Constants -----*/
	
	public static final int TILE_DIM = 32;
	
	public static final String FNMETAKEY_TILED = "PS1WSQX_TILED";
	
	public static final int PALETTE_FMT_RAW16 = 0;
	public static final int PALETTE_FMT_ARGB = 1;
	
	public static final int HDR_FLAG_UNK06 = 1 << 6; //0x40
	public static final int HDR_FLAG_4BIT = 1 << 5; //0x20
	public static final int HDR_FLAG_UNK04 = 1 << 4; //0x10
	public static final int HDR_FLAG_UNK00 = 1 << 0; //0x01
	
	//Defaults to just assigning to the requested index. Sets to 0 if input is -1.
	//This is useful if CLUT is in separate file and was loaded beforehand.
	public static final int CLUT_IMPORT_OP_NONE = 0;
	
	//Tries to read palette stored in image file and assigns that palette
	//	to requested CLUT slot.
	//Only applicable if input image is same bitdepth as target.
	public static final int CLUT_IMPORT_OP_IMGFILE_CLUT = 1;
	
	/*Tries to find existing CLUT that best works for input image and
	 * assigns that to imported frame.
	 * No new or overwritten CLUTs.
	 */
	public static final int CLUT_IMPORT_OP_TRY_CLUT_MATCH = 2;
	
	/*Tries to generate a new CLUT from the import image.
	 * If CLUT index is specified, will overwrite that CLUT.
	 * Otherwise, makes a new one.
	 */
	public static final int CLUT_IMPORT_OP_TRY_CLUT_GEN = 3;
	
	/*----- Instance Variables -----*/
	
	private int bitdepth;
	private ArrayList<Bitmap> bitmaps;
	private ArrayList<QXCLUT> palettes;
	
	/*----- Structures -----*/
	
	private static class Bitmap {
		public int palette_idx;
		public int[][] data;
		public int scale_x;
		public int scale_y;
		
		public Bitmap(){}
		
		public Bitmap(int[][] dat, int pidx){
			data = dat;
			palette_idx = pidx;
		}
	}
	
	private static class QXCLUT{
		public int[] palette;
		public int paletteFmt = PALETTE_FMT_RAW16;
		
		public QXCLUT(int bitdepth){
			if (bitdepth == 4){
				palette = new int[16];
			}
			else if(bitdepth == 8){
				palette = new int[256];
			}
		}
		
		public int getARGB(int index){
			if(index < 0 || index >= palette.length) return 0;
			
			switch(paletteFmt){
			case PALETTE_FMT_RAW16:
				return abgr16_to_argb32(palette[index]);
			case PALETTE_FMT_ARGB:
				return palette[index];
			}
			
			int pix = 0;
			pix = 0xff << 24;
			pix |= (index & 0xff) << 16;
			pix |= (index & 0xff) << 8;
			pix |= (index & 0xff);
			return pix;
		}
		
		public short getRaw16(int index){
			if(index < 0 || index >= palette.length) return 0;
			
			switch(paletteFmt){
			case PALETTE_FMT_RAW16:
				return (short)palette[index];
			case PALETTE_FMT_ARGB:
				return argb32_to_abgr16(palette[index]);
			}
			
			int pix = 0;
			pix |= (index & 0x1f) << 15;
			pix |= (index & 0x1f) << 10;
			pix |= (index & 0x1f);
			return (short)pix;
		}
		
		public void setARGB(int argb, int index){
			if(index < 0 || index >= palette.length) return ;
			
			switch(paletteFmt){
			case PALETTE_FMT_RAW16:
				palette[index] = Short.toUnsignedInt(argb32_to_abgr16(argb));
				break;
			case PALETTE_FMT_ARGB:
				palette[index] = argb;
				break;
			}
		}
		
		public void setRaw16(short value, int index){
			if(index < 0 || index >= palette.length) return ;
			
			switch(paletteFmt){
			case PALETTE_FMT_RAW16:
				palette[index] = Short.toUnsignedInt(value);
				break;
			case PALETTE_FMT_ARGB:
				palette[index] = abgr16_to_argb32(value);
				break;
			}
		}
	}
	
	/*----- Read -----*/
	
	public QXImage(int frameCount, int bitDepth){
		//frames = new Picture[frameCount];
		bitmaps = new ArrayList<Bitmap>(frameCount);
		bitdepth = bitDepth;
		palettes = new ArrayList<QXCLUT>(4);
		
		//if(bitdepth == 4) palette = new FourBitPalette();
		//else if(bitdepth == 8) palette = new EightBitPalette();
	}
	
	private static void readBitmap4(FileBuffer file, int offset, boolean tiled, int[][]bitmap)
	{
		long cpos = Integer.toUnsignedLong(offset);
		//System.err.println("Start offset: 0x" + Long.toHexString(offset));
		
		int w = bitmap.length;
		int h = bitmap[0].length;
		//System.err.println("Predicted size: 0x" + Integer.toHexString((w*h)/2));
		
		if(tiled)
		{
			int x_tiles = w/TILE_DIM;
			int y_tiles = h/TILE_DIM;
			for(int ty = 0; ty < y_tiles; ty++)
			{
				int base_y = ty*TILE_DIM;
				for(int tx = 0; tx < x_tiles; tx++)
				{
					int base_x = tx*TILE_DIM;
					for(int y = 0; y < TILE_DIM; y++)
					{
						for(int x = 0; x < TILE_DIM; x+=2)
						{
							int b = Byte.toUnsignedInt(file.getByte(cpos)); cpos++;
							
							int v0 = (b & 0xF);
							int v1 = (b >>> 4) & 0xF;
							
							bitmap[x+base_x][y+base_y] = v0;
							bitmap[x+base_x+1][y+base_y] = v1;
						}
					}
				}
			}
		}
		else
		{
			int y = 0;
			int x = 0;
			int pix_ct = (w * h) >>> 1;
			
			for(int p = 0; p < pix_ct; p++)
			{
				int b = Byte.toUnsignedInt(file.getByte(cpos)); cpos++;
				
				int v0 = (b & 0xF);
				int v1 = (b >>> 4) & 0xF;
				
				bitmap[x][y] = v0; x++;
				if(x >= w){y++; x = 0;}
				bitmap[x][y] = v1; x++;
				if(x >= w){y++; x = 0;}
			}
		}
		
	}
	
	private static void readBitmap8(FileBuffer file, int offset, boolean tiled, int[][]bitmap)
	{
		long cpos = Integer.toUnsignedLong(offset);
		
		int w = bitmap.length;
		int h = bitmap[0].length;
		
		if(tiled)
		{
			int x_tiles = w/TILE_DIM;
			int y_tiles = h/TILE_DIM;
			for(int ty = 0; ty < y_tiles; ty++)
			{
				int base_y = ty*TILE_DIM;
				for(int tx = 0; tx < x_tiles; tx++)
				{
					int base_x = tx*TILE_DIM;
					for(int y = 0; y < TILE_DIM; y++)
					{
						for(int x = 0; x < TILE_DIM; x++)
						{
							int b = Byte.toUnsignedInt(file.getByte(cpos)); cpos++;
							bitmap[x+base_x][y+base_y] = b;
						}
					}
				}
			}
		}
		else
		{
			for(int y = 0; y < h; y++)
			{
				for(int x = 0; x < w; x++)
				{
					int b = Byte.toUnsignedInt(file.getByte(cpos)); cpos++;
					bitmap[x][y] = b;
				}
			}
		}
	}
	
	private static void readBitmap16(FileBuffer file, int offset, boolean tiled, int[][]bitmap)
	{
		long cpos = Integer.toUnsignedLong(offset);
		
		int w = bitmap.length;
		int h = bitmap[0].length;
		
		if(tiled)
		{
			int x_tiles = w/TILE_DIM;
			int y_tiles = h/TILE_DIM;
			for(int ty = 0; ty < y_tiles; ty++)
			{
				int base_y = ty*TILE_DIM;
				for(int tx = 0; tx < x_tiles; tx++)
				{
					int base_x = tx*TILE_DIM;
					for(int y = 0; y < TILE_DIM; y++)
					{
						for(int x = 0; x < TILE_DIM; x++)
						{
							int b = Short.toUnsignedInt(file.shortFromFile(cpos)); cpos+=2;
							bitmap[x+base_x][y+base_y] = b;
						}
					}
				}
			}
		}
		else
		{
			for(int y = 0; y < h; y++)
			{
				for(int x = 0; x < w; x++)
				{
					int b = Short.toUnsignedInt(file.shortFromFile(cpos)); cpos+=2;
					bitmap[x][y] = b;
				}
			}
		}
	}
	
	public static QXImage readImageData(String path, boolean tiled) throws IOException{
		FileBuffer file = FileBuffer.createBuffer(path, false);
		return readImageData(file, tiled);
	}
	
	public static QXImage readImageData(FileBuffer file, boolean tiled) throws IOException {
		file.setEndian(false);
		long cpos = 0;
		//I think the first two bytes are flags maybe????
		//Or just version info???
		//Anyway, they look like QX, Q_, qX or q_ in ASCII which is where the name comes from
		int flag0 = Byte.toUnsignedInt(file.getByte(cpos)); cpos += 2;
		int bd = 16;
		if((flag0 & HDR_FLAG_4BIT) != 0) bd = 4; //0x71 usually
		else bd = 8; //0x51 most of the time, but not sure which flag means 8 bit
		
		int frames = Short.toUnsignedInt(file.shortFromFile(cpos)); cpos+=2;
		//System.err.println("Frame count: " + frames);
		int[][] dims = new int[frames][6];
		for(int i = 0; i < frames; i++){
			int off = file.intFromFile(cpos); cpos += 4;
			dims[i][3] = Byte.toUnsignedInt(file.getByte(cpos)); cpos++;
			cpos+=3; //Unknown
			int w = Short.toUnsignedInt(file.shortFromFile(cpos)); cpos+=2;
			int h = Short.toUnsignedInt(file.shortFromFile(cpos)); cpos+=2;
			dims[i][0] = off;
			dims[i][1] = w;
			dims[i][2] = h;
			//I don't know what this last field is either...
			//cpos+=4; //Unknown field...
			dims[i][4] = Short.toUnsignedInt(file.shortFromFile(cpos)); cpos+=2;
			dims[i][5] = Short.toUnsignedInt(file.shortFromFile(cpos)); cpos+=2;
		}
		
		//Calculate how big the bitmaps together should be
		/*for(int i = 0; i < frames; i++)
		{
			int stpos = dims[i][0];
			int edpos = (int)file.getFileSize();
			if(i+1 < frames) edpos = dims[i+1][0];
			
			int w = dims[i][1];
			int h = dims[i][2];
			int pix_count = w*h;
			System.err.println("Image " + i + " --------------");
			System.err.println("\tPixel count: 0x" + Integer.toHexString(pix_count));
			int sz = edpos - stpos;
			System.err.println("\tBitmap Size: 0x" + Integer.toHexString(sz));
			System.err.println("\tBitmap/Pixels: " + (double)sz/(double)pix_count);
		}
		System.exit(2);*/
		
		//Read palette
		//First, palette header
		int p_entry_count = file.intFromFile(cpos); cpos+=4;
		int clut_count = 0;
		int entries_per_clut = 0;
		if(bd == 4){
			clut_count = p_entry_count >>> 4;
			entries_per_clut = 1 << 4;
		}
		else if(bd == 8){
			clut_count = p_entry_count >>> 8;
			entries_per_clut = 1 << 8;
		}
				
		QXImage img = new QXImage(frames, bd);
		if(clut_count > 4) img.palettes.ensureCapacity(clut_count);

		if(bd < 16){
			for(int i = 0; i < clut_count; i++){
				QXCLUT clut = new QXCLUT(bd);
				img.palettes.add(clut);
				for(int j = 0; j < entries_per_clut; j++){
					clut.palette[j] = Short.toUnsignedInt(file.shortFromFile(cpos));
					cpos += 2;
				}
			}
		}
		
		//Read bitmaps
		for(int i = 0; i < frames; i++){
			int off = dims[i][0];
			int w = dims[i][1];
			int h = dims[i][2];
			
			int[][] bmp = new int[w][h];
			switch(img.bitdepth){
			case 4:
				readBitmap4(file, off, tiled, bmp); break;
			case 8:
				readBitmap8(file, off, tiled, bmp); break;
			case 16:
				readBitmap16(file, off, tiled, bmp); break;
			}
			Bitmap b = new Bitmap(bmp, dims[i][3]);
			img.bitmaps.add(b);
			b.scale_x = dims[i][4];
			b.scale_y = dims[i][5];
		}
		
		return img;
	}
	
	/*----- Getters -----*/
	
	public int getFrameCount(){return bitmaps.size();}
	
	public int getBitDepth(){return bitdepth;}
	
	public int getPaletteCount(){
		if(palettes == null) return 0;
		return palettes.size();
	}
	
	public int[] getFrameScalingFactors(int frame_index){
		int[] out = new int[2];
		Bitmap frame = this.bitmaps.get(frame_index);
		out[0] = frame.scale_x;
		out[1] = frame.scale_y;
		return out;
	}
	
	public int getClutIndexForFrame(int frame_index){
		Bitmap frame = this.bitmaps.get(frame_index);
		return frame.palette_idx;
	}
	
	/*----- Setters -----*/
	
	public void allocCLUTs(int CLUT_count){
		if(bitdepth > 8) return;
		palettes.ensureCapacity(CLUT_count);
		while(palettes.size() < CLUT_count){
			palettes.add(new QXCLUT(bitdepth));
		}
	}
	
	public boolean loadCLUT(short[] raw_16, int index){
		if(bitdepth > 8) return false;
		if(index < 0) return false;
		if(raw_16 == null) return false;
		if(index > palettes.size()) return false;
		
		QXCLUT clut = palettes.get(index);
		Arrays.fill(clut.palette, 0);
		for(int j = 0; j < raw_16.length; j++){
			if(j >= clut.palette.length) break;
			clut.setRaw16(raw_16[j], index);
		}
		return true;
	}
	
	public boolean loadCLUT(int[] argb, int index){
		if(bitdepth > 8) return false;
		if(index < 0) return false;
		if(argb == null) return false;
		if(index > palettes.size()) return false;
		
		QXCLUT clut = palettes.get(index);
		Arrays.fill(clut.palette, 0);
		for(int j = 0; j < argb.length; j++){
			if(j >= clut.palette.length) break;
			clut.setARGB(argb[j], index);
		}
		return true;
	}
	
	private boolean importTryClutMatch(int[][] inputImage, Bitmap target){
		//ARGB input
		QXCLUT clut = null;
		int clutIndex = 0;
		int pcount = palettes.size();
		CLUTImgMatch bestMatch = null;
		for(int p = 0; p < pcount; p++){
			clut = palettes.get(p);
			
			int[] clutref = Arrays.copyOf(clut.palette, clut.palette.length);
			if(clut.paletteFmt == PALETTE_FMT_RAW16){
				for(int i = 0; i < clutref.length; i++){
					clutref[i] = abgr16_to_argb32(clutref[i]);
				}
			}
			
			CLUTImgMatch match = CLUTCompare.checkImgCLUTMatch(clutref, inputImage);
			if(match != null){
				if(bestMatch != null){
					if(match.score < bestMatch.score){
						bestMatch = match;
						clutIndex = p;
					}
				}
				else bestMatch = match;
			}
		}
		
		if(clutIndex < 0) clutIndex = 0;
		target.palette_idx = clutIndex;
		//Remap image data.
		if(bestMatch != null){
			for(int i = 0; i < inputImage.length; i++){
				for(int j = 0; j < inputImage[i].length; j++){
					inputImage[i][j] = bestMatch.map.get(inputImage[i][j]);
				}
			}
		}
		target.data = inputImage;
		
		return true;
	}
	
	private boolean importTryClutMatch(int[][] inputImage, int[] inputCLUT, Bitmap target){
		//For 4 and 8 bit sources
		QXCLUT clut = null;
		int clutIndex = 0;
		int pcount = palettes.size();
		CLUTMatch bestMatch = null;
		for(int p = 0; p < pcount; p++){
			clut = palettes.get(p);
			
			//Make sure it's in ARGB format so can compare
			int[] clutref = Arrays.copyOf(clut.palette, clut.palette.length);
			if(clut.paletteFmt == PALETTE_FMT_RAW16){
				for(int i = 0; i < clutref.length; i++){
					clutref[i] = abgr16_to_argb32(clutref[i]);
				}
			}
			
			//Check match
			CLUTMatch match = CLUTCompare.checkCLUTMatch(clutref, inputCLUT, inputImage);
			if(match != null){
				if(bestMatch != null){
					if(match.score < bestMatch.score){
						bestMatch = match;
						clutIndex = p;
					}
				}
				else bestMatch = match;
			}
		}
		
		if(clutIndex < 0) clutIndex = 0;
		target.palette_idx = clutIndex;
		//Remap image data.
		if(bestMatch != null){
			for(int i = 0; i < inputImage.length; i++){
				for(int j = 0; j < inputImage[i].length; j++){
					inputImage[i][j] = bestMatch.map[inputImage[i][j]];
				}
			}
		}
		target.data = inputImage;
		
		return true;
	}
	
	private boolean importFrame_4bit(int[][] inputImage, int[] inputCLUT, Bitmap target, int clutIndex, int clutImportOption){
		if(inputImage == null) return false;
		if(inputCLUT == null) return false;
		
		//Source is 4 bits
		QXCLUT clut = null;
		if(bitdepth <= 8){
			//4 -> 4 and 4 -> 8 behave the same
			//Just doesn't use full range of palette if scaling up to 8
			switch(clutImportOption){
			case CLUT_IMPORT_OP_NONE:
				//Does not try to remap. Takes your word for it.
				if(clutIndex < 0) clutIndex = 0;
				target.palette_idx = clutIndex;
				target.data = inputImage;
				break;
			case CLUT_IMPORT_OP_IMGFILE_CLUT:
			case CLUT_IMPORT_OP_TRY_CLUT_GEN: //No point in generating. Already has one.
				target.data = inputImage;
				if(clutIndex >= 0 && clutIndex < palettes.size()){
					clut = palettes.get(clutIndex);
				}
				else{
					clutIndex = palettes.size();
					clut = new QXCLUT(bitdepth);
					palettes.add(clut);
				}
				target.palette_idx = clutIndex;
				
				clut.palette = inputCLUT; //Already copy
				clut.paletteFmt = PALETTE_FMT_ARGB;
				break;
			case CLUT_IMPORT_OP_TRY_CLUT_MATCH:
				return importTryClutMatch(inputImage, inputCLUT, target);
			}
		}
		else{
			//4 -> 16
			target.palette_idx = -1;
			for(int i = 0; i < inputImage.length; i++){
				for(int j = 0; j < inputImage[i].length; j++){
					inputImage[i][j] = argb32_to_abgr16(inputCLUT[inputImage[i][j]]);
				}
			}
		}
		
		return true;
	}
	
	private boolean importFrame_8bit(int[][] inputImage, int[] inputCLUT, Bitmap target, int clutIndex, int clutImportOption){
		if(inputImage == null) return false;
		if(inputCLUT == null) return false;
		
		//Source is 8 bits
		QXCLUT clut = null;
		if(bitdepth == 8){
			//8 -> 8
			switch(clutImportOption){
			case CLUT_IMPORT_OP_NONE:
				//Does not try to remap. Takes your word for it.
				if(clutIndex < 0) clutIndex = 0;
				target.palette_idx = clutIndex;
				target.data = inputImage;
				break;
			case CLUT_IMPORT_OP_IMGFILE_CLUT:
			case CLUT_IMPORT_OP_TRY_CLUT_GEN: //No point in generating. Already has one.
				target.data = inputImage;
				if(clutIndex >= 0 && clutIndex < palettes.size()){
					clut = palettes.get(clutIndex);
				}
				else{
					clutIndex = palettes.size();
					clut = new QXCLUT(bitdepth);
					palettes.add(clut);
				}
				target.palette_idx = clutIndex;
				
				clut.palette = inputCLUT; //Already copy
				clut.paletteFmt = PALETTE_FMT_ARGB;
				break;
			case CLUT_IMPORT_OP_TRY_CLUT_MATCH:
				return importTryClutMatch(inputImage, inputCLUT, target);
			}
		}
		else if(bitdepth == 4){
			//8 -> 4
			switch(clutImportOption){
			case CLUT_IMPORT_OP_NONE:
				//Does not try to remap. Takes your word for it.
				if(clutIndex < 0) clutIndex = 0;
				target.palette_idx = clutIndex;
				
				for(int i = 0; i < inputImage.length; i++){
					for(int j = 0; j < inputImage[i].length; j++){
						inputImage[i][j] >>>= 4;
					}
				}
				target.data = inputImage;
				break;
			case CLUT_IMPORT_OP_IMGFILE_CLUT:
				return false; //Incompatible
			case CLUT_IMPORT_OP_TRY_CLUT_GEN:
				//Generate a new smaller CLUT
				if(clutIndex >= 0 && clutIndex < palettes.size()){
					clut = palettes.get(clutIndex);
				}
				else{
					clutIndex = palettes.size();
					clut = new QXCLUT(bitdepth);
					palettes.add(clut);
				}
				target.palette_idx = clutIndex;
				
				int I = inputImage.length;
				int J = inputImage[0].length;
				int[][] inputARGB = new int[I][J];
				for(int i = 0; i < inputImage.length; i++){
					for(int j = 0; j < inputImage[i].length; j++){
						inputARGB[i][j] = inputCLUT[inputImage[i][j]];
					}
				}
				
				PaletteGen gen = new PaletteGen(bitdepth, false);
				gen.processImage(inputARGB);
				clut.palette = gen.generatePalette();
				clut.paletteFmt = PALETTE_FMT_ARGB;
				gen.flush();
				
				target.data = CLUTCompare.remapImageToCLUT(clut.palette, inputCLUT, inputImage);
				break;
			case CLUT_IMPORT_OP_TRY_CLUT_MATCH:
				return importTryClutMatch(inputImage, inputCLUT, target);
			}
		}
		else{
			//8 -> 16
			target.palette_idx = -1;
			for(int i = 0; i < inputImage.length; i++){
				for(int j = 0; j < inputImage[i].length; j++){
					inputImage[i][j] = argb32_to_abgr16(inputCLUT[inputImage[i][j]]);
				}
			}
		}
		
		return true;
	}
	
	private boolean importFrame_16bit(int[][] inputImage, Bitmap target, int clutIndex, int clutImportOption){
		if(inputImage == null) return false;
		
		//Source is 16 bits
		if(bitdepth <= 8){
			//16 -> 4 or 16 -> 8
			
			int I = inputImage.length;
			int J = inputImage[0].length;
			int[][] inputARGB = new int[I][J];
			for(int i = 0; i < inputImage.length; i++){
				for(int j = 0; j < inputImage[i].length; j++){
					inputARGB[i][j] = abgr16_to_argb32(inputImage[i][j]);
				}
			}
			
			QXCLUT clut = null;
			switch(clutImportOption){
			case CLUT_IMPORT_OP_IMGFILE_CLUT:
				return false; //Incompatible
			case CLUT_IMPORT_OP_NONE:
				//Forcibly assign requested CLUT
				if(clutIndex >= 0 && clutIndex < palettes.size()){
					clut = palettes.get(clutIndex);
				}
				else clutIndex = 0;
				target.palette_idx = clutIndex;
				
				int[] clutref = Arrays.copyOf(clut.palette, clut.palette.length);
				if(clut.paletteFmt == PALETTE_FMT_RAW16){
					for(int i = 0; i < clutref.length; i++){
						clutref[i] = abgr16_to_argb32(clutref[i]);
					}
				}
				
				target.data = CLUTCompare.remapImageToCLUT(clutref, inputARGB);
				break;
			case CLUT_IMPORT_OP_TRY_CLUT_GEN:
				//Generate a new CLUT from input data
				if(clutIndex >= 0 && clutIndex < palettes.size()){
					clut = palettes.get(clutIndex);
				}
				else{
					clutIndex = palettes.size();
					clut = new QXCLUT(bitdepth);
					palettes.add(clut);
				}
				target.palette_idx = clutIndex;
				
				PaletteGen gen = new PaletteGen(bitdepth, false);
				gen.processImage(inputARGB);
				clut.palette = gen.generatePalette();
				clut.paletteFmt = PALETTE_FMT_ARGB;
				gen.flush();
				
				target.data = CLUTCompare.remapImageToCLUT(clut.palette, inputARGB);
				break;
			case CLUT_IMPORT_OP_TRY_CLUT_MATCH:
				return importTryClutMatch(inputARGB, target);
			}
		}
		else{
			//16 -> 16
			target.palette_idx = -1;
			target.data = inputImage;
		}
		
		return true;
	}
	
	private boolean importFrame_RGB(int[][] inputImage, Bitmap target, int clutIndex, int clutImportOption, boolean alpha){
		if(inputImage == null) return false;
		
		if(!alpha){
			for(int i = 0; i < inputImage.length; i++){
				for(int j = 0; j < inputImage[i].length; j++){
					inputImage[i][j] |= 0xFF000000;
				}
			}
		}
		
		//Source is 24 or 32 bits
		if(bitdepth <= 8){
			//32 -> 4 or 32 -> 8
			
			QXCLUT clut = null;
			switch(clutImportOption){
			case CLUT_IMPORT_OP_IMGFILE_CLUT:
				return false; //Incompatible
			case CLUT_IMPORT_OP_NONE:
				//Forcibly assign requested CLUT
				if(clutIndex >= 0 && clutIndex < palettes.size()){
					clut = palettes.get(clutIndex);
				}
				else clutIndex = 0;
				target.palette_idx = clutIndex;
				
				int[] clutref = Arrays.copyOf(clut.palette, clut.palette.length);
				if(clut.paletteFmt == PALETTE_FMT_RAW16){
					for(int i = 0; i < clutref.length; i++){
						clutref[i] = abgr16_to_argb32(clutref[i]);
					}
				}
				
				target.data = CLUTCompare.remapImageToCLUT(clutref, inputImage);
				break;
			case CLUT_IMPORT_OP_TRY_CLUT_GEN:
				//Generate a new CLUT from input data
				if(clutIndex >= 0 && clutIndex < palettes.size()){
					clut = palettes.get(clutIndex);
				}
				else{
					clutIndex = palettes.size();
					clut = new QXCLUT(bitdepth);
					palettes.add(clut);
				}
				target.palette_idx = clutIndex;
				
				PaletteGen gen = new PaletteGen(bitdepth, alpha);
				gen.processImage(inputImage);
				clut.palette = gen.generatePalette();
				clut.paletteFmt = PALETTE_FMT_ARGB;
				gen.flush();
				
				target.data = CLUTCompare.remapImageToCLUT(clut.palette, inputImage);
				break;
			case CLUT_IMPORT_OP_TRY_CLUT_MATCH:
				return importTryClutMatch(inputImage, target);
			}
		}
		else{
			//32 -> 16
			target.palette_idx = -1;
			
			int I = inputImage.length;
			int J = inputImage[0].length;
			int[][] input16 = new int[I][J];
			for(int i = 0; i < inputImage.length; i++){
				for(int j = 0; j < inputImage[i].length; j++){
					input16[i][j] = argb32_to_abgr16(inputImage[i][j]);
				}
			}
			
			target.data = input16;
		}
		return true;
	}
	
	public boolean importFrameFromBMP(FileBuffer bmpdata, int frameIndex, int clutIndex, int clutImportOption) throws UnsupportedFileTypeException{
		if(bmpdata == null) return false;
		if(frameIndex < 0) return false;
		if(frameIndex > bitmaps.size()) return false; //Can add 1 new one, but not more than 1 at once.
		
		Bitmap target = null;
		if(frameIndex >= 0 && frameIndex < bitmaps.size()){
			target = bitmaps.get(frameIndex);
		}
		else{
			target = new Bitmap();
			bitmaps.add(target);
		}
		
		BmpFile bmp = BmpFile.readBMP(bmpdata);
		switch(bmp.getBitDepth()){
		case 4:
			return importFrame_4bit(bmp.getImageDataRaw(), bmp.getPaletteRaw(), target, clutIndex, clutImportOption);
		case 8:
			return importFrame_8bit(bmp.getImageDataRaw(), bmp.getPaletteRaw(), target, clutIndex, clutImportOption);
		case 16:
			return importFrame_16bit(bmp.getImageDataRaw(), target, clutIndex, clutImportOption);
		case 24:
			return importFrame_RGB(bmp.getImageDataRaw(), target, clutIndex, clutImportOption, false);
		case 32:
			return importFrame_RGB(bmp.getImageDataRaw(), target, clutIndex, clutImportOption, true);
		}
		
		return false;
	}
	
	public boolean importFrameFromFile_ARGB(InputStream filestream, int frameIndex, int clutIndex, int clutImportOption) throws IOException{
		if(filestream == null) return false;
		if(frameIndex < 0) return false;
		if(frameIndex > bitmaps.size()) return false; //Can add 1 new one, but not more than 1 at once.
		
		Bitmap target = null;
		if(frameIndex >= 0 && frameIndex < bitmaps.size()){
			target = bitmaps.get(frameIndex);
		}
		else{
			target = new Bitmap();
			bitmaps.add(target);
		}
		
		BufferedImage img = ImageIO.read(filestream);
		int w = img.getWidth();
		int h = img.getHeight();
		int[][] argb = new int[h][w];
		
		for(int y = 0; y < h; y++){
			for(int x = 0; x < w; x++){
				argb[y][x] = img.getRGB(x, y);
			}
		}
		
		return importFrame_RGB(argb, target, clutIndex, clutImportOption, true);
	}
	
	/*----- View -----*/
	
	public static short argb32_to_abgr16(int val32){
		int r8 = (val32 >> 16) & 0xff;
		int g8 = (val32 >> 8) & 0xff;
		int b8 = val32 & 0xff;
		
		int r5 = PSXImages.scale8Bit_to_5Bit(r8);
		int g5 = PSXImages.scale8Bit_to_5Bit(g8);
		int b5 = PSXImages.scale8Bit_to_5Bit(b8);
		
		int outraw = (b5 << 10) | (g5 << 5) | (r5);
		if((val32 & 0xff000000) == 0){
			outraw |= 0x8000;
		}
		
		return (short)outraw;
	}
	
	public static int abgr16_to_argb32(int val16)
	{
		int color = 0xFF000000;
		
		if((val16 & 0x8000) != 0) color = 0x00000000;
		int r = NDSGraphics.scale5Bit_to_8Bit(val16 & 0x1F);
		int g = NDSGraphics.scale5Bit_to_8Bit((val16 >>> 5) & 0x1F);
		int b = NDSGraphics.scale5Bit_to_8Bit((val16 >>> 10) & 0x1F);
		
		return color | (r << 16) | (g << 8) | b;
	}
	
	public static int abgr16_to_rgba32(int val16)
	{
		int color = 0x000000FF;
		
		if((val16 & 0x8000) != 0) color = 0x00000000;
		int r = NDSGraphics.scale5Bit_to_8Bit(val16 & 0x1F);
		int g = NDSGraphics.scale5Bit_to_8Bit((val16 >>> 5) & 0x1F);
		int b = NDSGraphics.scale5Bit_to_8Bit((val16 >>> 10) & 0x1F);
		
		return (r << 24) | (g << 16) | (b << 8) | color;
	}
	
	public static BufferedImage scale(BufferedImage src, int w, int h)
	{
		
		Image image = src.getScaledInstance(w, h, BufferedImage.SCALE_AREA_AVERAGING);
		if(image instanceof BufferedImage) return(BufferedImage)image;
		
		BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2 = bi.createGraphics();
		g2.drawImage(image, 0, 0, null);
		g2.dispose();
		
		return bi;
	}
	
	public BufferedImage getFrame(int idx, boolean rescale){
		Bitmap bmp = bitmaps.get(idx);
		int[][] bitmap = bmp.data;
		int w = bitmap.length;
		int h = bitmap[0].length;
		
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		for(int y = 0; y < h; y++){
			for(int x = 0; x < w; x++){
				int val = bitmap[x][y];
				int pix = 0;
				if(bmp.palette_idx >= 0 && bmp.palette_idx < palettes.size()){
					QXCLUT clut = palettes.get(bmp.palette_idx);
					pix = clut.getARGB(val);
				}
				else{
					pix = 0xff << 24;
					pix |= (val & 0xff) << 16;
					pix |= (val & 0xff) << 8;
					pix |= (val & 0xff);
				}
				img.setRGB(x, y, pix);
			}
		}
		
		int scale_x = bmp.scale_x;
		int scale_y = bmp.scale_y;
		
		if(rescale && (scale_x != 0 || scale_y != 0)){
			int tw = w + (scale_x << 1);
			int th = h + (scale_y << 1);
			img = scale(img, tw, th);
		}
		
		return img;
	}
	
	/*----- Conversion -----*/
	
	private static FileBuffer writeBMPPixelData_4(Bitmap src){
		int w = src.data.length;
		int h = src.data[0].length;
		int bw = w/2;
		
		if((w % 2) != 0) bw++;
		
		int bw_ceil = (bw + 3) & ~0x3;
		int rowpad = bw_ceil - bw;
		int bmalloc = bw_ceil * h;
		FileBuffer idata = new FileBuffer(bmalloc, false);
		
		for(int y = h-1; y >= 0; y--){
			//BMPs are stored bottom-up.
			for(int x = 0; x < w; x+=2){
				//Left pixel is more significant nibble
				int b = 0;
				b = src.data[x][y] << 4;
				if(x+1 < w){
					b |= src.data[x+1][y];
				}
				idata.addToFile((byte)b);
			}
			//Pad to 4 bytes
			for(int j = 0; j < rowpad; j++) idata.addToFile(FileBuffer.ZERO_BYTE);
		}
		
		return idata;
	}
	
	private static FileBuffer writeBMPPixelData_8(Bitmap src){
		int w = src.data.length;
		int h = src.data[0].length;

		int w_ceil = (w + 3) & ~0x3;
		int rowpad = w_ceil - w;
		int bmalloc = w_ceil * h;
		FileBuffer idata = new FileBuffer(bmalloc, false);
		
		for(int y = h-1; y >= 0; y--){
			//BMPs are stored bottom-up.
			for(int x = 0; x < w; x++){
				int b = src.data[x][y];
				idata.addToFile((byte)b);
			}
			//Pad to 4 bytes
			for(int j = 0; j < rowpad; j++) idata.addToFile(FileBuffer.ZERO_BYTE);
		}
		
		return idata;
	}
	
	private static FileBuffer writeBMPPixelData_16(Bitmap src){
		int w = src.data.length;
		int h = src.data[0].length;
		int bw = w << 1;

		int bw_ceil = (bw + 3) & ~0x3;
		int rowpad = bw_ceil - bw;
		int bmalloc = bw_ceil * h;
		FileBuffer idata = new FileBuffer(bmalloc, false);
		
		for(int y = h-1; y >= 0; y--){
			//BMPs are stored bottom-up.
			for(int x = 0; x < w; x++){
				int val = src.data[x][y];
				idata.addToFile((short)val);
			}
			//Pad to 4 bytes
			for(int j = 0; j < rowpad; j++) idata.addToFile(FileBuffer.ZERO_BYTE);
		}
		
		return idata;
	}
	
 	public void writeToPNG(String path, boolean rescale) throws IOException
	{
		//Just the first frame
		ImageIO.write(getFrame(0, rescale), "png", new File(path));
	}
	
	public void writeToMultiPNG(String prefix, boolean rescale) throws IOException
	{
		int fcount = this.getFrameCount();
		for(int i = 0; i < fcount; i++)
		{
			String path = prefix + "_" + String.format("%04d", i) + ".png";
			ImageIO.write(getFrame(i, rescale), "png", new File(path));
		}
	}
	
	public void writeToGIF(String path)
	{
		//TODO
	}
	
	public boolean writeFrameToBMPFile(String path, int frame) throws IOException{
		if(frame < 0) return false;
		if(frame >= bitmaps.size()) return false;
		Bitmap myframe = bitmaps.get(frame);
		
		final int DIB_SIZE = 108;
		FileBuffer bmp_header = new FileBuffer(14, false);
		FileBuffer dib_header = new FileBuffer(DIB_SIZE, false); //v4
		FileBuffer color_table = null;
		
		int pcount = palettes.size();
		QXCLUT clut = null;
		if(myframe.palette_idx >= 0 && myframe.palette_idx < pcount){
			clut = palettes.get(myframe.palette_idx);
		}
		
		if(bitdepth == 4){
			color_table = new FileBuffer(16 << 2, false);
			if(clut != null){
				for(int i = 0; i < 16; i++){
					color_table.addToFile(clut.getARGB(i));
				}
			}
			else{
				for(int i = 0; i < 16; i++){
					int ii = i << 4;
					int pix = 0xFF << 24;
					pix |= ii << 16;
					pix |= ii << 8;
					pix |= ii;
					color_table.addToFile(pix);
				}
			}
		}
		else if(bitdepth == 8){
			color_table = new FileBuffer(256 << 2, false);
			if(clut != null){
				for(int i = 0; i < 256; i++){
					color_table.addToFile(clut.getARGB(i));
				}
			}
			else{
				for(int i = 0; i < 256; i++){
					int pix = 0xFF << 24;
					pix |= i << 16;
					pix |= i << 8;
					pix |= i;
					color_table.addToFile(pix);
				}
			}
		}
		
		int w = myframe.data.length;
		int h = myframe.data[0].length;

		FileBuffer idata = null;
		
		//Data
		if(bitdepth == 4) idata = writeBMPPixelData_4(myframe);
		else if(bitdepth == 8) idata = writeBMPPixelData_8(myframe);
		else if(bitdepth == 16) idata = writeBMPPixelData_16(myframe);
		
		//DIB header
		dib_header.addToFile(DIB_SIZE);
		dib_header.addToFile(w);
		dib_header.addToFile(h);
		dib_header.addToFile((short)1);
		dib_header.addToFile((short)bitdepth);
		dib_header.addToFile(0);
		dib_header.addToFile((int)idata.getFileSize());
		dib_header.addToFile(2048);
		dib_header.addToFile(2048);
		if(bitdepth == 4) dib_header.addToFile(16);
		else if(bitdepth == 8) dib_header.addToFile(256);
		else dib_header.addToFile(0);
		for(int i = 0; i < 5; i++) dib_header.addToFile(0);
		dib_header.printASCIIToFile("BGRs");
		while(dib_header.getFileSize() < DIB_SIZE) dib_header.addToFile(FileBuffer.ZERO_BYTE);
		
		//Main header
		int size = 0;
		bmp_header.printASCIIToFile("BM");
		if(color_table != null){
			size = 14 + DIB_SIZE + (int)color_table.getFileSize() + (int)idata.getFileSize();
		}
		else{
			size = 14 + DIB_SIZE + (int)idata.getFileSize();
		}
		bmp_header.addToFile(size);
		//I'm gonna use these two reserved fields for x scale and y scale
		bmp_header.addToFile((short)myframe.scale_x);
		bmp_header.addToFile((short)myframe.scale_y);
		bmp_header.addToFile((int)(size - (int)idata.getFileSize()));
		
		//Output to disk
		BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(path));
		bmp_header.writeToStream(bos);
		dib_header.writeToStream(bos);
		if(color_table != null){
			color_table.writeToStream(bos);
		}
		idata.writeToStream(bos);
		bos.close();
		
		return true;
	}
	
	/*----- Write -----*/
	
	private static FileBuffer serializeBitmap_4(Bitmap bmp, boolean tile){
		int w = bmp.data.length;
		int h = bmp.data[0].length;
		
		if(tile){
			int ww = (w + 0x1f) & ~0x1f;
			int hh = (h + 0x1f) & ~0x1f;
			int alloc = (ww*hh + 4) >>> 1;
			FileBuffer out = new FileBuffer(alloc, false);
			int wt = ww >>> 5;
			int ht = hh >>> 5;
				
			for(int yt = 0; yt < ht; yt++){
				//Per tile row
				for(int xt = 0; xt < wt; xt++){
					//Per tile
					int x0 = xt << 5;
					int y0 = yt << 5;
					for(int y = 0; y < TILE_DIM; y++){
						int yy = y0 + y;
						for(int x = 0; x < TILE_DIM; x+=2){
							int xx = x0 + x;
							int b = 0;
							if(yy < h && xx < w){
								b = (bmp.data[xx][yy] & 0xf);
								if((xx + 1) < w){
									b |= (bmp.data[xx+1][yy] & 0xf) << 4;
								}
							}
							out.addToFile((byte)b);
						}
					}
				}
			}
				
			return out;
		}
		else{
			int alloc = (w*h + 4) >>> 1;
			FileBuffer out = new FileBuffer(alloc, false);
			
			for(int y = 0; y < h; y++){
				for(int x = 0; x < w; x+=2){
					int b = 0;
					b = bmp.data[x][y] & 0xf;
					if(x+1 < w){
						b |= (bmp.data[x+1][y] & 0xf) << 4;
					}
					out.addToFile((byte)b);
				}
			}
			
			return out;
		}
	}
	
	private static FileBuffer serializeBitmap_8(Bitmap bmp, boolean tile){
		int w = bmp.data.length;
		int h = bmp.data[0].length;
		
		if(tile){
			int ww = (w + 0x1f) & ~0x1f;
			int hh = (h + 0x1f) & ~0x1f;
			int alloc = ww*hh + 4;
			FileBuffer out = new FileBuffer(alloc, false);
			int wt = ww >>> 5;
			int ht = hh >>> 5;
				
			for(int yt = 0; yt < ht; yt++){
				//Per tile row
				for(int xt = 0; xt < wt; xt++){
					//Per tile
					int x0 = xt << 5;
					int y0 = yt << 5;
					for(int y = 0; y < TILE_DIM; y++){
						int yy = y0 + y;
						for(int x = 0; x < TILE_DIM; x++){
							int xx = x0 + x;
							int b = 0;
							if(yy < h && xx < w){
								b = bmp.data[xx][yy] & 0xff;
							}
							out.addToFile((byte)b);
						}
					}
				}
			}
				
			return out;
		}
		else{
			int alloc = w*h + 4;
			FileBuffer out = new FileBuffer(alloc, false);
			
			for(int y = 0; y < h; y++){
				for(int x = 0; x < w; x++){
					int b = bmp.data[x][y];
					out.addToFile((byte)b);
				}
			}
			
			return out;
		}
	}
	
	private static FileBuffer serializeBitmap_16(Bitmap bmp, boolean tile){
		int w = bmp.data.length;
		int h = bmp.data[0].length;
		
		if(tile){
			int ww = (w + 0x1f) & ~0x1f;
			int hh = (h + 0x1f) & ~0x1f;
			int alloc = (ww*hh + 4) << 1;
			FileBuffer out = new FileBuffer(alloc, false);
			int wt = ww >>> 5;
			int ht = hh >>> 5;
				
			for(int yt = 0; yt < ht; yt++){
				//Per tile row
				for(int xt = 0; xt < wt; xt++){
					//Per tile
					int x0 = xt << 5;
					int y0 = yt << 5;
					for(int y = 0; y < TILE_DIM; y++){
						int yy = y0 + y;
						for(int x = 0; x < TILE_DIM; x++){
							int xx = x0 + x;
							if(yy < h && xx < w){
								out.addToFile((short)bmp.data[xx][yy]);
							}
							else out.addToFile((short)0);
						}
					}
				}
			}
				
			return out;
		}
		else{
			int alloc = (w*h + 4) << 1;
			FileBuffer out = new FileBuffer(alloc, false);
			
			for(int y = 0; y < h; y++){
				for(int x = 0; x < w; x++){
					out.addToFile((byte)bmp.data[x][y]);
				}
			}
			
			return out;
		}
	}
	
	public boolean serializeTo(OutputStream output, boolean tile){
		int frame_count = bitmaps.size();
		
		//Serialize bitmaps...
		MultiFileBuffer ser_bmp = new MultiFileBuffer(frame_count);
		int[] bmp_offsets = new int[frame_count];
		int size = 0;
		for(int f = 0; f < frame_count; f++){
			bmp_offsets[f] = size;
			FileBuffer bmpbuff = null;
			switch(bitdepth){
			case 4:
				bmpbuff = serializeBitmap_4(bitmaps.get(f), tile);
				break;
			case 8:
				bmpbuff = serializeBitmap_8(bitmaps.get(f), tile);
				break;
			default:
				bmpbuff = serializeBitmap_16(bitmaps.get(f), tile);
				break;
			}
			size += (int)bmpbuff.getFileSize();
			ser_bmp.addToFile(bmpbuff);
		}
		
		//Palette
		int palette_count = getPaletteCount();
		int palette_entries = 1 << bitdepth;
		FileBuffer palbuff = null;
		int psize = 0;
		if(palette_count > 0){
			int alloc = 4;
			alloc += (palette_entries << 1) * palette_count;
			palbuff = new FileBuffer(alloc, false);
			palbuff.addToFile(palette_count << bitdepth);
			
			for(int i = 0; i < palette_count; i++){
				QXCLUT clut = palettes.get(i);
				for(int j = 0; j < palette_entries; j++){
					palbuff.addToFile(clut.getRaw16(j));
				}
			}
			psize = alloc;
		}
		
		int alloc = (frame_count << 4) + 4;
		FileBuffer header = new FileBuffer(alloc, false);
		
		//Update bitmap offsets to add the header size
		for(int i = 0; i < frame_count; i++){
			bmp_offsets[i] += alloc + psize;
		}
		
		int flags = 0;
		flags |= HDR_FLAG_UNK00 | HDR_FLAG_UNK04 | HDR_FLAG_UNK06;
		if(bitdepth == 4){
			flags |= HDR_FLAG_4BIT;
		}
		header.addToFile((short)flags);
		for(int f = 0; f < frame_count; f++){
			header.addToFile(bmp_offsets[f]);
			Bitmap frame = bitmaps.get(f);
			header.addToFile(frame.palette_idx);
			if(tile){
				int dim = (frame.data.length + 0x1f) & ~0x1f;
				header.addToFile((short)dim);
				dim = (frame.data[0].length + 0x1f) & ~0x1f;
				header.addToFile((short)dim);
			}
			else{
				header.addToFile((short)frame.data.length);
				header.addToFile((short)frame.data[0].length);
			}
			header.addToFile((short)frame.scale_x);
			header.addToFile((short)frame.scale_y);
		}
		
		
		try {
			header.writeToStream(output);
			if(palbuff != null) palbuff.writeToStream(output);
			ser_bmp.writeToStream(output);
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		
		return true;
	}
	
	public boolean outputCLUTBinary(String path, int index) throws IOException{
		if(palettes == null) return false;
		if(index < 0) return false;
		if(index >= palettes.size()) return false;
		int ccount = 1 << bitdepth;
		
		QXCLUT clut = palettes.get(index);
		FileBuffer out = new FileBuffer(ccount << 1, false);
		for(int i = 0; i < ccount; i++){
			out.addToFile(clut.getRaw16(i));
		}
		out.writeFile(path);
		
		return true;
	}
	
	/*----- Definitions -----*/
	
	public static final int DEF_ID = 0x02185158;
	public static final String DEFO_ENG_STR = "PlayStation 1 GPU Sprite/Image Set";
	
	private static QXSpriteDef stat_def;
	private static QX2PNGConv conv_def;
	
	public static class QXSpriteDef implements FileTypeDefinition{

		private String desc = DEFO_ENG_STR;
		
		public Collection<String> getExtensions() {
			List<String> list = new ArrayList<String>(2);
			list.add("qx");
			list.add("*");
			return list;
		}

		public String getDescription() {return desc;}
		public FileClass getFileClass() {return FileClass.IMG_SPRITE_SHEET;}
		public int getTypeID() {return DEF_ID;}
		public void setDescriptionString(String s) {desc = s;}
		public String getDefaultExtension() {return "qx";}
		public String toString(){return FileTypeDefinition.stringMe(this);}
		
	}
	
	public static class QX2PNGConv implements Converter{
		
		private String desc_from = DEFO_ENG_STR;
		private String desc_to = "Portable Network Graphics Image (.png)";

		public String getFromFormatDescription() {return desc_from;}
		public String getToFormatDescription() {return desc_to;}
		public void setFromFormatDescription(String s) {desc_from = s;}
		public void setToFormatDescription(String s) {desc_to = s;}

		public void writeAsTargetFormat(String inpath, String outpath)
				throws IOException, UnsupportedFileTypeException {
			FileBuffer dat = FileBuffer.createBuffer(inpath, false);
			writeAsTargetFormat(dat, outpath);
		}

		public void writeAsTargetFormat(FileBuffer input, String outpath)
				throws IOException, UnsupportedFileTypeException {
			QXImage img = QXImage.readImageData(input, false);
			writeOut(img, outpath);
		}

		public void writeAsTargetFormat(FileNode node, String outpath)
				throws IOException, UnsupportedFileTypeException {
			//This one is ideal as it can check meta to see if tiled
			if(node == null) return;
			
			boolean tiled = false;
			String tmeta = node.getMetadataValue(FNMETAKEY_TILED);
			if(tmeta != null && tmeta.equalsIgnoreCase("true")) tiled = true;
			
			QXImage img = QXImage.readImageData(node.loadDecompressedData(), tiled);
			writeOut(img, outpath);
		}
		
		private void writeOut(QXImage img, String outpath) throws IOException{
			//If there are multiple images, then write to a directory
			int fcount = img.getFrameCount();
			if(fcount > 1){
				//Derive prefix
				String prefix = outpath;
				int lastdot = outpath.lastIndexOf('.');
				if(lastdot >= 0) prefix = prefix.substring(0, lastdot);
				img.writeToMultiPNG(prefix, false);
			}
			else{
				//Just write as PNG
				img.writeToPNG(outpath, false);
			}
			
		}

		public String changeExtension(String path) {
			
			if(path == null) return null;
			int lastdot = path.lastIndexOf('.');
			if(lastdot >= 0){
				return path.substring(0, lastdot) + ".png";
			}
			else return path + ".png";
		}
		
	}
	
	public static QXSpriteDef getDefinition(){
		if(stat_def == null) stat_def = new QXSpriteDef();
		return stat_def;
	}
	
	public static QX2PNGConv getConverter(){
		if(conv_def == null) conv_def = new QX2PNGConv();
		return conv_def;
	}
	
	/*----- Test -----*/
	
 	public static void main(String[] args) 
	{
		try
		{
			String inpath = "C:\\Users\\Blythe\\Documents\\Game Stuff\\PSX\\GameData\\SLPM87176_conv\\UNIT\\raw\\UNIT_104.qx";
			String outpath = "C:\\Users\\Blythe\\Documents\\Game Stuff\\PSX\\GameData\\SLPM87176_conv\\UNIT\\UNIT_104\\QX";
		
			QXImage qx = QXImage.readImageData(inpath, false);
			qx.writeToMultiPNG(outpath, false);
			
		}
		catch(Exception e)
		{
			e.printStackTrace();
			System.exit(1);
		}
	}

}
