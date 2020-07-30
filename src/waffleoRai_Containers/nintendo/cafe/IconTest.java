package waffleoRai_Containers.nintendo.cafe;

import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

import waffleoRai_Image.files.TGAFile;

public class IconTest {

	public static void main(String[] args) {

		String icon_in = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\cafetest\\-GM00050000101C4D00000000000100-meta-iconTex.tga";
		String icon_out = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\cafetest\\-GM00050000101C4D00000000000100-meta-iconTex.png";

		try{
			
			TGAFile tga = TGAFile.readTGA(icon_in);
			BufferedImage img = tga.getImage();
			ImageIO.write(img, "png", new File(icon_out));
			
		}
		catch(Exception x){
			x.printStackTrace();
			System.exit(1);
		}
		
	}

}
