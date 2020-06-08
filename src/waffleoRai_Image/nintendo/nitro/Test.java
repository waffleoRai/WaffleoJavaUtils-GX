package waffleoRai_Image.nintendo.nitro;

import java.io.File;

import javax.imageio.ImageIO;

import waffleoRai_Image.Palette;
import waffleoRai_Utils.FileBuffer;

public class Test {

	public static void main(String[] args) {
		String tdir = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\ds_test";
		
		try{
			
			/*String tpath = tdir + "\\adae_clr2.nclr";
			
			NCLR clr = NCLR.readNCLR(FileBuffer.createBuffer(tpath));
			clr.printToStderr();
			
			String ostem = tdir + "\\adae_clr2_p";
			for(int i = 0; i < clr.getPaletteCount(); i++){
				Palette p = clr.getPalette(i);
				String opath = ostem + i + ".png";
				ImageIO.write(p.renderVisual(), "png", new File(opath));
			}*/
			
			String tpath = tdir + "\\ground0.ncgr";
			NCGR ncgr = NCGR.readNCGR(FileBuffer.createBuffer(tpath));
			String opath = tdir + "\\ground0_tiles.png";
			ImageIO.write(ncgr.renderImageData(32), "png", new File(opath));
			
			String clrpath = tdir + "\\ground0.nclr";
			NCLR nclr = NCLR.readNCLR(FileBuffer.createBuffer(clrpath));
			
			/*String scrpath = tdir + "\\ground0.nscr";
			NSCR nscr = NSCR.readNSCR(FileBuffer.createBuffer(scrpath));
			opath = tdir + "\\ground0_screen.png";
			ImageIO.write(nscr.renderImage(nclr, ncgr), "png", new File(opath));*/
		}
		catch(Exception e){
			e.printStackTrace();
			System.exit(1);
		}

	}

}
