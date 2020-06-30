package waffleoRai_Containers.nintendo;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Collection;

import javax.imageio.ImageIO;

import waffleoRai_Image.Animation;

public class TestGCRead {

	public static void main(String[] args) {
		//String testpath = "E:\\Library\\Games\\Console\\DOL_GMPE_USA.gcm";
		//String testpath = "E:\\Library\\Games\\Console\\DOL_GXXE_USA.gcm";
		//String testpath = "E:\\Library\\Games\\Console\\DOL_GM2E_USA.gcm";
		
		String mcpath = "C:\\Users\\Blythe\\Documents\\Game Stuff\\saves\\gcmem\\charmander\\0251b_2020_01Jan_07_19-50-29.raw";
		String icodir = "C:\\Users\\Blythe\\Documents\\Game Stuff\\saves\\gcmem\\charmander\\icons";
		
		try{
			
			/*GCWiiDisc gcdisc = new GCWiiDisc(testpath);
			GCWiiHeader hdr = gcdisc.getHeader();
			hdr.printInfo();
			DirectoryNode root = gcdisc.getDiscTree();
			root.printMeToStdErr(0);*/
			GCMemCard mc = GCMemCard.readRawMemoryCardFile(mcpath);
			mc.debugPrint();
			
			Collection<GCMemCardFile> files = mc.getFiles();
			for(GCMemCardFile file : files){
				String iconstem = icodir + File.separator + file.getFileName() + "_";
				Animation a = file.getRawIcon();
				for(int i = 0; i < a.getNumberFrames(); i++){
					String outpath = iconstem + String.format("f%02d", i) + ".png";
					BufferedImage img = a.getFrameImage(i);
					if(img != null){
						ImageIO.write(img, "png", new File(outpath));
					}
				}
				BufferedImage img = file.getBanner();
				if(img != null){
					String outpath = iconstem + "banner.png";
					ImageIO.write(img, "png", new File(outpath));
				}
				//FileBuffer f = file.loadFile();
				//f.writeFile(iconstem + "data.mci");
			}
			
		}
		catch(Exception x){
			x.printStackTrace();
			System.exit(1);
		}
		
	}

}
