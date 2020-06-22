package waffleoRai_Image.psx;

import waffleoRai_Image.FourBitPalette;
import waffleoRai_Image.Palette;
import waffleoRai_Image.PaletteRaster;
import waffleoRai_Image.Pixel;
import waffleoRai_Image.Pixel_RGBA;
import waffleoRai_Utils.BitStreamer;
import waffleoRai_Utils.FileBuffer;

public class PSXImages {
	
	public static int scale5BitColor(int PSXval)
	{
		//PSX to PC Scaling formula: y = 8* log2(x/2)
		if (PSXval < 0 || PSXval >= 32) return PSXval;
		if (PSXval == 0) return 0;
		if (PSXval == 1) return 8;
		if (PSXval == 2) return 16;
		if (PSXval == 31) return 255;
		double l2 = Math.log10((double)PSXval / 2.0) / Math.log10(2.0);
		l2 *= 8.0;
		//Now scale to 255
		double factor = 255.0/32.0;
		double temp = factor * l2;
		return (int)Math.round(temp);
	}
	
	public static int scale16ToRGBA(int PSX555Color, boolean includeAlpha, boolean tpOn)
	{
		boolean aBit = BitStreamer.readABit(PSX555Color, 16);
		int r = (PSX555Color) & 0x1F;
		int g = (PSX555Color >>> 5) & 0x1F;
		int b = (PSX555Color >>> 10) & 0x1F;
		
		int red = scale5BitColor(r);
		int green = scale5BitColor(g);
		int blue = scale5BitColor(b);
		int alpha = 255;
		if (includeAlpha)
		{
			if ((PSX555Color & 0x7FFF) == 0)
			{
				if(!aBit) alpha = 0;
			}
			else if (tpOn && aBit) alpha = 0;
		}
		
		int RGBA = 0;
		int R = red << 24;
		int G = green << 16;
		int B = blue << 8;
		int A = alpha;
		RGBA |= R | G | B | A;
		
		return RGBA;
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
	
	public static int getRGBA(int R8, int G8, int B8, int A8)
	{
		int RGBA = 0;
		int R = R8 << 24;
		int G = G8 << 16;
		int B = B8 << 8;
		int A = A8;
		RGBA |= R | G | B | A;
		
		return RGBA;
	}
	
	public static Palette parse16ColorPalette(FileBuffer source, int stPos, boolean enableSemiTransparency)
	{
		Palette myPalette = new FourBitPalette();
		for (int i = 0; i < 16; i++)
		{
			short color = source.shortFromFile(stPos + (2*i));
			int blue = 0;
			int red = 0;
			int green = 0;
			int alpha = 0;
			if (color != 0)
			{
				if (enableSemiTransparency)
				{
					boolean stFlag = BitStreamer.readABit(color, 15);
					if (stFlag) alpha = 127;
					else alpha = 255;	
				}
				else alpha = 255;
				blue = scale5BitColor((color >> 10) & 0x1F);
				green = scale5BitColor((color >> 5) & 0x1F);
				red = scale5BitColor(color & 0x1F);
			}
			Pixel p = new Pixel_RGBA(red, blue, green, alpha);
			myPalette.setPixel(p, i);
		}
		return myPalette;
	}
	
	public static PaletteRaster parseBitMap4(FileBuffer source, Palette plt, long stPos, int width, int height)
	{
		PaletteRaster img = new PaletteRaster(width, height, plt);
		long i = 0;
		for (int r = 0; r < height; r++)
		{
			for (int l = 0; l < width; l += 2)
			{
				byte b = source.getByte(stPos + i);
				//System.out.println("PSXImages.parseBitMap4 || Byte offset: 0x" + Long.toHexString(stPos + i) + " byte: 0x" + String.format("%02x", b));
				int c1 = (Byte.toUnsignedInt(b) >> 4) & 0x0F;
				int c2 = Byte.toUnsignedInt(b) & 0x0F;
				img.setPixelAt(r, l+1, c1);
				img.setPixelAt(r, l, c2);
				i++;
			}
		}
		
		return img;
	}

	public static FileBuffer serialize16ColorPalette(FourBitPalette plt)
	{
		FileBuffer pal = new FileBuffer(256, false);
		for (int i = 0; i < 16; i++)
		{
			int addition = 0;
			Pixel p = plt.getPixel(i);
			int a = p.getAlpha();
			int b = p.getBlue();
			int g = p.getGreen();
			int r = p.getRed();
			if (a > 0 && a <= 127)
			{
				addition = BitStreamer.writeABit(addition, true, 15);
			}
			b = scale8Bit_to_5Bit(b) << 10;
			g = scale8Bit_to_5Bit(g) << 5;
			r = scale8Bit_to_5Bit(r);
			addition = addition | b | g | r;
			pal.addToFile((short)addition);
		}
		return pal;
	}
	
	public static FileBuffer serializeBitMap4(PaletteRaster img)
	{
		FileBuffer bmp = new FileBuffer(img.getHeight() * img.getWidth(), false);
		for (int r = 0; r < img.getHeight(); r++)
		{
			for (int l = 0; l < img.getWidth(); l += 2)
			{
				byte wb = 0;
				int prep = 0;
				int c1 = img.getColorAt(r, l);
				int c2 = img.getColorAt(r, l+1);
				prep = prep | (c1 << 4) | c2;
				wb = (byte)prep;
				bmp.addToFile(wb);
			}
		}
		return bmp;
	}
	
}

