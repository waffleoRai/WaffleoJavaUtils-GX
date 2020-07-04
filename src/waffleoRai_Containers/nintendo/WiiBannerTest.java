package waffleoRai_Containers.nintendo;

import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

import waffleoRai_Image.Animation;
import waffleoRai_Utils.FileBuffer;

public class WiiBannerTest {

	public static void main(String[] args) {
		
		String dir = "E:\\Library\\Games\\Console\\data";
		//String bnr1 = dir + "\\RVL_SX4E_USZ\\banner.bin";
		//String bnr2 = dir + "\\RVL_SOUE_USA\\banner.bin";
		String bnr3 = dir + "\\RVL_RPBE_USZ\\data.bin";
		
		String keypath = "E:\\Library\\Games\\Console\\keys\\rvl_sd_common.bin";
		String ivpath = "E:\\Library\\Games\\Console\\keys\\rvl_sd_iv.bin";
		
		String outdir = "E:\\Library\\Games\\Console\\test\\wiisavebanner";
		
		try{
			
			/*WiiSaveBannerFile banner = WiiSaveBannerFile.readBannerBin(FileBuffer.createBuffer(bnr1, true));
			System.err.println("BANNER 1 ---");
			System.err.println(banner.getTitle());
			System.err.println(banner.getSubtitle());
			BufferedImage img = banner.getBanner();
			String outpath = outdir + "\\SX4E_banner.png";
			ImageIO.write(img, "png", new File(outpath));
			Animation ico = banner.getIcon();
			int fcount = ico.getNumberFrames();
			for(int i = 0; i < fcount; i++){
				img = ico.getFrameImage(i);
				outpath = outdir + "\\SX4E_ico_f" + i + ".png";
				ImageIO.write(img, "png", new File(outpath));
			}
			
			banner = WiiSaveBannerFile.readBannerBin(FileBuffer.createBuffer(bnr2, true));
			System.err.println("BANNER 2 ---");
			System.err.println(banner.getTitle());
			System.err.println(banner.getSubtitle());
			img = banner.getBanner();
			outpath = outdir + "\\SOUE_banner.png";
			ImageIO.write(img, "png", new File(outpath));
			ico = banner.getIcon();
			fcount = ico.getNumberFrames();
			for(int i = 0; i < fcount; i++){
				img = ico.getFrameImage(i);
				outpath = outdir + "\\SOUE_ico_f" + i + ".png";
				ImageIO.write(img, "png", new File(outpath));
			}*/
			
			byte[] key = FileBuffer.createBuffer(keypath).getBytes();
			byte[] iv = FileBuffer.createBuffer(ivpath).getBytes();
			WiiSaveDataFile.set_sdKey(key);
			WiiSaveDataFile.set_sdIV(iv);
			WiiSaveDataFile datfile = WiiSaveDataFile.readDataBin(FileBuffer.createBuffer(bnr3, true), true);
			
			WiiSaveBannerFile banner = datfile.getBanner();
			System.err.println("BANNER 3 ---");
			System.err.println(banner.getTitle());
			System.err.println(banner.getSubtitle());
			BufferedImage img = banner.getBanner();
			String outpath = outdir + "\\RPBE_banner.png";
			ImageIO.write(img, "png", new File(outpath));
			Animation ico = banner.getIcon();
			int fcount = ico.getNumberFrames();
			for(int i = 0; i < fcount; i++){
				img = ico.getFrameImage(i);
				outpath = outdir + "\\RPBE_ico_f" + i + ".png";
				ImageIO.write(img, "png", new File(outpath));
			}
			
		}
		catch(Exception x){
			x.printStackTrace();
			System.exit(1);
		}
		
	}

}
