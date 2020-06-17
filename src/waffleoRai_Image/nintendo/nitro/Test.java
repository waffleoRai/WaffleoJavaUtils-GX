package waffleoRai_Image.nintendo.nitro;

import java.io.File;

import javax.imageio.ImageIO;

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
			
			String tpath = tdir + "\\smptm_koori.NCGR";
			NCGR ncgr = NCGR.readNCGR(FileBuffer.createBuffer(tpath));
			String opath = tdir + "\\smptm_koori_tiles.png";
			ImageIO.write(ncgr.getTileset().renderImageData(4), "png", new File(opath));
			
			tpath = tdir + "\\smptm_koori.NCLR";
			NCLR nclr = NCLR.readNCLR(FileBuffer.createBuffer(tpath));
			//System.err.println("# of palettes: " + nclr.getPaletteCount());
			nclr.getPalette(0).printMe();
			
			tpath = tdir + "\\smptm_koori.NCER";
			NCER ncer = NCER.readNCER(FileBuffer.createBuffer(tpath));
			opath = tdir + "\\smptm_koori_cells";
			ncer.imagesToPNG(opath, ncgr.getTileset(), nclr.getPalettes());
			//ncer.imagesToPNG(opath, ncgr.getTileset());
			
		}
		catch(Exception e){
			e.printStackTrace();
			System.exit(1);
		}

	}

}
