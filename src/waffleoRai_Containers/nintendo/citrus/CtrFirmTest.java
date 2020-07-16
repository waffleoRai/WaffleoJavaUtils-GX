package waffleoRai_Containers.nintendo.citrus;

import waffleoRai_Utils.FileBuffer;

public class CtrFirmTest {

	public static void main(String[] args) {
		
		String boot9_path = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\3ds_test\\boot9.bin";
		String otp_path = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\3ds_test\\otp.mem";
		String firm_path = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\3ds_test\\firm1.bin";
		
		String test_dir = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\3ds_test";
		
		try{
			
			CitrusFirmImage firm = CitrusFirmImage.readFirmImage(firm_path);
			firm.printToStdErr();
			
			for(int i = 0; i < 4; i++){
				String outpath = test_dir + "\\firm1_sec_" + i + ".bin";
				firm.getSection(i, true).writeFile(outpath);
			}
			
		}
		catch(Exception x){
			x.printStackTrace();
			System.exit(1);
		}
	}

}
