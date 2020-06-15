package waffleoRai_Image.nintendo;

import waffleoRai_Image.EightBitPalette;
import waffleoRai_Image.FourBitPalette;
import waffleoRai_Image.Palette;
import waffleoRai_Image.PaletteRaster;
import waffleoRai_Image.Picture;
import waffleoRai_Image.Pixel_RGBA;
import waffleoRai_Utils.FileBuffer;

public class NDSGraphics {
	
	public static final String METAKEY_PALETTEID = "PLTUID"; //UID of Palette linked - stored in metadata of a NSCR
	//public static final String METAKEY_NCLRID = "NCLRID"; //UID of a NCLR file - stored in metadata of NCLR
	
	public static final String METAKEY_PLTLINK = "PLTLINK"; //Path to linked Palette - stored in metadata of a NSCR
	public static final String METAKEY_PLTIDX = "PLTIDX"; 
	
	public static final String METAKEY_TLELINK = "TLELINK"; //Path to linked tile set - stored in metadata of NSCR
	public static final String METAKEY_TLEUID = "TLEUID"; //UID of linked tile set - stored in metadata of NSCR
	//public static final String METAKEY_NCGRID = "NCGRID"; //UID of a NCGR file - stored in metadata of NCGR
	
	public static Palette readPalette(FileBuffer in, long stpos, boolean color256)
	{
		long cpos = stpos;
		int colors = 16;
		Palette p = null;
		
		if(color256)
		{
			colors = 256;
			p = new EightBitPalette();
		}
		else p = new FourBitPalette();
		
		for(int i = 0; i < colors; i++)
		{
			int val = Short.toUnsignedInt(in.shortFromFile(cpos)); cpos += 2;
			int alpha = 255;
			if((val & 0x8000) != 0) alpha = 0;
			int b5 = (val >>> 10) & 0x1F;
			int g5 = (val >>> 5) & 0x1F;
			int r5 = val & 0x1F;
			
			int blue = scale5Bit_to_8Bit(b5);
			int green = scale5Bit_to_8Bit(g5);
			int red = scale5Bit_to_8Bit(r5);
			
			Pixel_RGBA pix = new Pixel_RGBA(red, blue, green, alpha);
			p.setPixel(pix, i);
		}
		
		return p;
	}

	public static Picture readBitmap_4bit(FileBuffer in, long stpos, int w, int h, Palette palette)
	{
		long cpos = stpos;
		PaletteRaster pic = new PaletteRaster(w,h,palette);
		
		int tw = w >>> 3;
		int th = h >>> 3;
		
		for(int y = 0; y < th; y++)
		{
			int base_r = y << 3;
			for(int x = 0; x < tw; x++)
			{
				int base_l = x << 3;
				for(int r = 0; r < 8; r++)
				{
					for(int l = 0; l < 8; l+=2)
					{
						int raw = Byte.toUnsignedInt(in.getByte(cpos)); cpos++;
						int p1 = (raw >>> 4) & 0xF;
						int p2 = raw & 0xF;
						
						int row = base_r + r;
						int col = base_l + l;
						
						pic.setPixelAt(row, col, p2);
						pic.setPixelAt(row, col+1, p1);
					}
				}
			}
		}

		return pic;
	}
	
 	public static int scale5Bit_to_8Bit(int five)
	{
		//PSX to PC Scaling formula: y = 8* log2(x/2)
		if (five < 0 || five >= 32) return five;
		if (five == 0) return 0;
		if (five == 1) return 8;
		if (five == 2) return 16;
		if (five == 31) return 255;
		double l2 = Math.log10((double)five / 2.0) / Math.log10(2.0);
		l2 *= 8.0;
		//Now scale to 255
		double factor = 255.0/32.0;
		double temp = factor * l2;
		return (int)Math.round(temp);
	}
	
	public static int scale8Bit_to_5Bit(int eight)
	{
		//I have no idea if this is updated...
		if (eight == 0) return 0;
		if (eight < 37) return 1;
		if (eight == 255) return 31;
		double PC = ((double)eight * 32.0) / 255.0;
		PC /= 8.0;
		PC = Math.pow(2.0, PC);
		PC *= 2;
		return (int)Math.round(PC);
	}
	

}
