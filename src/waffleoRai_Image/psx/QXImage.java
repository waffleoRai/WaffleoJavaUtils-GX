package waffleoRai_Image.psx;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.imageio.ImageIO;

import waffleoRai_Files.Converter;
import waffleoRai_Files.FileClass;
import waffleoRai_Files.FileTypeDefinition;
import waffleoRai_Files.tree.FileNode;
import waffleoRai_Image.nintendo.NDSGraphics;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;

public class QXImage {
	
	/*----- Constants -----*/
	
	public static final int TILE_DIM = 32;
	
	public static final String FNMETAKEY_TILED = "PS1WSQX_TILED";
	
	/*----- Instance Variables -----*/
	
	//private Picture[] frames;
	private int bitdepth;
	private List<Bitmap> bitmaps;
	private int [] argb_palette;
	//private Palette palette;
	
	/*----- Structures -----*/
	
	private static class Bitmap
	{
		public int palette_idx;
		public int[][] data;
		public int scale_x;
		public int scale_y;
		
		public Bitmap(int[][] dat, int pidx)
		{
			data = dat;
			palette_idx = pidx;
		}
	}
	
	/*----- Read -----*/
	
	public QXImage(int frameCount, int bitDepth)
	{
		//frames = new Picture[frameCount];
		bitmaps = new ArrayList<Bitmap>(frameCount);
		bitdepth = bitDepth;
		
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
	
	public static QXImage readImageData(FileBuffer file, boolean tiled) throws IOException
	{
		file.setEndian(false);
		long cpos = 0;
		//I think the first two bytes are flags maybe????
		//Or just version info???
		//Anyway, they look like QX, Q_, qX or q_ in ASCII which is where the name comes from
		int flag0 = Byte.toUnsignedInt(file.getByte(cpos)); cpos += 2;
		int bd = 16;
		if(flag0 == 0x71) bd = 4;
		else if(flag0 == 0x51) bd = 8;
		int frames = Short.toUnsignedInt(file.shortFromFile(cpos)); cpos+=2;
		//System.err.println("Frame count: " + frames);
		int[][] dims = new int[frames][6];
		for(int i = 0; i < frames; i++)
		{
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
		int pcount = file.intFromFile(cpos); cpos+=4;
		/*int bd = 16;
		if(pheader == 0x100) bd = 8;
		else if (pheader == 0x10) bd = 4;*/
		QXImage img = new QXImage(frames, bd);
		img.argb_palette = new int[pcount];
		
		if(bd < 16)
		{
			for(int i = 0; i < pcount; i++)
			{
				int rawval = Short.toUnsignedInt(file.shortFromFile(cpos)); cpos+=2;
				img.argb_palette[i] = abgr16_to_argb32(rawval);
			}
		}
		
		//Read bitmaps
		for(int i = 0; i < frames; i++)
		{
			int off = dims[i][0];
			int w = dims[i][1];
			int h = dims[i][2];
			
			int[][] bmp = new int[w][h];
			switch(img.bitdepth)
			{
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
	
	/*----- View -----*/
	
	public static int abgr16_to_argb32(int val16)
	{
		int color = 0xFF000000;
		
		if((val16 & 0x8000) != 0) color = 0x00000000;
		int r = NDSGraphics.scale5Bit_to_8Bit(val16 & 0x1F);
		int g = NDSGraphics.scale5Bit_to_8Bit((val16 >>> 5) & 0x1F);
		int b = NDSGraphics.scale5Bit_to_8Bit((val16 >>> 10) & 0x1F);
		
		return color | (r << 16) | (g << 8) | b;
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
	
	public BufferedImage getFrame(int idx, boolean rescale)
	{
		Bitmap bmp = bitmaps.get(idx);
		int[][] bitmap = bmp.data;
		int w = bitmap.length;
		int h = bitmap[0].length;
		
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		for(int y = 0; y < h; y++)
		{
			for(int x = 0; x < w; x++)
			{
				int val = bitmap[x][y];
				int pix = 0;
				//if(palette != null) pix = palette.getPixel(val).getARGB();
				if(this.argb_palette != null){
					int offset = bmp.palette_idx * (1 << bitdepth);
					pix = argb_palette[val + offset];
				}
				else pix = abgr16_to_argb32(val);
				img.setRGB(x, y, pix);
			}
		}
		
		int scale_x = bmp.scale_x;
		int scale_y = bmp.scale_y;
		
		if(rescale && (scale_x != 0 || scale_y != 0))
		{
			int tw = w + (scale_x << 1);
			int th = h + (scale_y << 1);
			img = scale(img, tw, th);
		}
		
		return img;
	}
	
	/*----- Conversion -----*/
	
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
