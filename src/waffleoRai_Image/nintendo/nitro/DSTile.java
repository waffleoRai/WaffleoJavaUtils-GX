package waffleoRai_Image.nintendo.nitro;

import java.awt.image.BufferedImage;

import waffleoRai_Image.Palette;
import waffleoRai_Image.Pixel;

public class DSTile {
	
	private int bitdepth;
	private int[][] data;
	
	public DSTile(int bits, int dim){
		bitdepth = bits;
		data = new int[dim][dim];
	}
	
	public int[][] getData(){
		return data;
	}
	
	public int getBitDepth(){
		return bitdepth;
	}
	
	public boolean is4Bit(){
		return bitdepth == 4;
	}

	public boolean is8Bit(){
		return bitdepth == 8;
	}
	
	public void copyTo(BufferedImage img, int x, int y, boolean flipx, boolean flipy){
		Palette p = null;
		if(is8Bit()) p = Palette.get8BitGreyscalePalette();
		else p = Palette.get4BitGreyscalePalette();

		copyTo(img, x, y, flipx, flipy, p);
	}
	
	public void copyTo(BufferedImage img, int x, int y, boolean flipx, boolean flipy, Palette p){

		if(data == null || data[0] == null) return;
		
		int w = data.length;
		int h = data[0].length;
		
		for(int r = 0; r < h; r++){
			for(int l = 0; l < w; l++){
				int xget = l; int yget = r;
				if(flipy) yget = h-r;
				if(flipx) xget = w-l;
				
				Pixel px = p.getPixel(data[xget][yget]);
				img.setRGB(x+l, y+r, px.getARGB());
			}
		}
		
	}
	
}
