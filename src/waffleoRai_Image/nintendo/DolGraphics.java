package waffleoRai_Image.nintendo;

public class DolGraphics {

	public static int RGB5A3_to_ARGB(short color)
	{
		return RGB5A3_to_ARGB(Short.toUnsignedInt(color));
	}
	
	public static int RGB5A3_to_ARGB(int color)
	{
		//TODO Not sure which bits are which.
		
		boolean flag = (color & 0x8000) != 0;
		if(flag)
		{
			//ABGR???
			int a = (color >>> 12) & 0x7;
			int b = (color >>> 8) & 0xF;
			int g = (color >>> 4) & 0xF;
			int r = (color) & 0xF;
			
			int blue = upscale4(b);
			int green = upscale4(g);
			int red = upscale4(r);
			int alpha = upscale3(a);
			int argb = (alpha << 24) | (red << 16) | (green << 8) | blue;
			return argb;
		}
		else
		{
			//BGR???
			int b = (color >>> 10) & 0x1F;
			int g = (color >>> 5) & 0x1F;
			int r = (color) & 0x1F;
			
			int blue = upscale5(b);
			int green = upscale5(g);
			int red = upscale5(r);
			int alpha = 255;
			int argb = (alpha << 24) | (red << 16) | (green << 8) | blue;
			return argb;
		}
	}
	
	public static short ARGB_to_RGB5A3(int color)
	{
		//TODO Not sure which bits are which.
		
		int alpha = (color >>> 24) & 0xFF;
		int red = (color >>> 16) & 0xFF;
		int green = (color >>> 8) & 0xFF;
		int blue = (color) & 0xFF;
		
		int out = 0;
		if(alpha != 255)
		{
			out |= 0x8000;
			int a = downscale3(alpha);
			int r = downscale4(red);
			int g = downscale4(green);
			int b = downscale4(blue);
			
			out |= (r & 0xF);
			out |= (g & 0xF) << 4;
			out |= (b & 0xF) << 8;
			out |= (a & 0x7) << 12;
		}
		else
		{
			int r = downscale5(red);
			int g = downscale5(green);
			int b = downscale5(blue);
			
			out |= (r & 0x1F);
			out |= (g & 0x1F) << 5;
			out |= (b & 0x1F) << 10;
		}
		
		return (short)out;
	}
	
	public static int upscale3(int val)
	{
		//Equation: y = 2 * log2(2x)
		if (val < 0 || val >= 8) return val;
		if (val == 0) return 0;
		if (val == 7) return 255;
		double l2 = Math.log10((double)val * 2.0) / Math.log10(2.0);
		l2 *= 2.0;
		//Now scale to 255
		double factor = 256.0/8.0;
		double temp = factor * l2;
		return (int)Math.round(temp);
	}
	
	public static int upscale4(int val)
	{
		//Equation: y = 4 * log2(x)
		if (val < 0 || val >= 16) return val;
		if (val == 0) return 0;
		if (val == 1) return 32;
		if (val == 15) return 255;
		double l2 = Math.log10((double)val) / Math.log10(2.0);
		l2 *= 4.0;
		//Now scale to 255
		double factor = 256.0/16.0;
		double temp = factor * l2;
		return (int)Math.round(temp);
	}
	
	public static int upscale5(int val)
	{
		//PSX to PC Scaling formula: y = 8* log2(x/2)
		if (val < 0 || val >= 32) return val;
		if (val == 0) return 0;
		if (val == 1) return 8;
		if (val == 2) return 16;
		if (val == 31) return 255;
		double l2 = Math.log10((double)val / 2.0) / Math.log10(2.0);
		l2 *= 8.0;
		//Now scale to 255
		double factor = 256.0/32.0;
		double temp = factor * l2;
		return (int)Math.round(temp);
	}
	
	public static int upscale6(int val)
	{
		//Equation: y = 16 * log2(x/4)
		if (val < 0 || val >= 64) return val;
		if (val == 0) return 0;
		if (val == 1) return 4;
		if (val == 2) return 8;
		if (val == 3) return 12;
		if (val == 4) return 16;
		if (val == 63) return 255;
		double l2 = Math.log10((double)val / 4.0) / Math.log10(2.0);
		l2 *= 16.0;
		//Now scale to 255
		double factor = 256.0/64.0;
		double temp = factor * l2;
		return (int)Math.round(temp);
	}
	
	public static int downscale3(int val)
	{
		//Equation: x = 2^(y/kr) * 1/c
		//k = 2, c = 2, r = 32 (256/8)
		if(val == 0) return 0;
		if(val == 255) return 7;
		double kr = 64.0;
		double ic = 0.5;
		double out = (double)val/kr;
		out = Math.pow(2.0, out);
		out *= ic;
		
		return (int)Math.floor(out);
	}
	
	public static int downscale4(int val)
	{
		//Equation: x = 2^(y/kr) * 1/c
		//k = 4, c = 1, r = 16 (256/16)
		if(val == 0) return 0;
		if(val == 255) return 15;
		double kr = 64.0;
		//double ic = 0.5;
		double out = (double)val/kr;
		out = Math.pow(2.0, out);
		//out *= ic;
		
		return (int)Math.floor(out);
	}
	
	public static int downscale5(int val)
	{
		//Equation: x = 2^(y/kr) * 1/c
		//k = 8, c = 0.5, r = 8 (256/32)
		if(val == 0) return 0;
		if(val == 255) return 31;
		double kr = 64.0;
		double ic = 2.0;
		double out = (double)val/kr;
		out = Math.pow(2.0, out);
		out *= ic;
		
		return (int)Math.floor(out);
	}
	
	public static int downscale6(int val)
	{
		//Equation: x = 2^(y/kr) * 1/c
		//k = 16, c = 0.25, r = 4 (256/64)
		if(val == 0) return 0;
		if(val == 255) return 31;
		double kr = 64.0;
		double ic = 4.0;
		double out = (double)val/kr;
		out = Math.pow(2.0, out);
		out *= ic;
		
		return (int)Math.floor(out);
	}
	
}
