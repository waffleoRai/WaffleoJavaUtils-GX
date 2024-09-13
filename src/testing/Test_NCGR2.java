package testing;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;

import javax.imageio.ImageIO;

import waffleoRai_Image.ImageDataReader;
import waffleoRai_Utils.FileBuffer;

public class Test_NCGR2 {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		
		String indir = "C:\\Users\\Blythe\\Documents\\out\\dstest";
		String outdir = "C:\\Users\\Blythe\\Documents\\out\\dstest\\ncgr_test2";
		
		int width = 160;
		int height = 80;
		
		String[][] pairs = {{"NARC_RAWFILE_0008.bin", "item_0008.bin"}, 
				{"NARC_RAWFILE_0644.bin", "item_1604.bin"}};
		
		String ref = "0008_pokedspic.png";
		
		try {
			String refpath = indir + File.separator + ref;
			BufferedImage refimg = ImageIO.read(new File(refpath));
			int w = refimg.getWidth();
			int h = refimg.getHeight();
			FileBuffer refout = new FileBuffer((w*h)/2, false);
			for(int r = 0; r < h; r++) {
				for(int l = 0; l < w; l+=2) {
					int argb0 = refimg.getRGB(l, r);
					int argb1 = refimg.getRGB(l+1, r);
					int p = (argb0 & 0xf0) >>> 4;
					p |= (argb1 & 0xf0);
					refout.addToFile((byte)p);
				}
			}
			String refbinpath = indir + File.separator + "ref.bin";
			refout.writeFile(refbinpath);
			
			for(int i = 0; i < pairs.length; i++) {

				//1. Just try converting to png on default palette
				String rawpath = indir + File.separator + pairs[i][0];
				String procpath = indir + File.separator + pairs[i][1];
				
				FileBuffer rawDat = FileBuffer.createBuffer(rawpath, 0x30, false);
				FileBuffer procDat = FileBuffer.createBuffer(procpath, 0x30, false);
				
				ImageDataReader rdr = new ImageDataReader();
				rdr.setBitDepth(4);
				rdr.setReadBitsMSBFirst(false);
				rdr.setTileSize(0, 0);
				
				BufferedImage iraw = rdr.parseImageData(rawDat.getReferenceAt(0L), width, height);
				BufferedImage iproc = rdr.parseImageData(procDat.getReferenceAt(0L), width, height);
				
				String rawout = outdir + File.separator + pairs[i][0] + ".png";
				String procout = outdir + File.separator + pairs[i][1] + ".png";
				ImageIO.write(iraw, "png", new File(rawout));
				ImageIO.write(iproc, "png", new File(procout));
			}
		}
		catch(Exception ex) {
			ex.printStackTrace();
			System.exit(1);
		}

	}

}
