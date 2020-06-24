package waffleoRai_Containers.nintendo;

import waffleoRai_Utils.DirectoryNode;

public class TestGCRead {

	public static void main(String[] args) {
		//String testpath = "E:\\Library\\Games\\Console\\DOL_GMPE_USA.gcm";
		//String testpath = "E:\\Library\\Games\\Console\\DOL_GXXE_USA.gcm";
		//String testpath = "E:\\Library\\Games\\Console\\DOL_GM2E_USA.gcm";
		
		String mcpath = "C:\\Users\\Blythe\\Documents\\Game Stuff\\saves\\gcmem\\charmander\\0251b_2020_01Jan_07_19-50-29.raw";
		
		try{
			
			/*GCWiiDisc gcdisc = new GCWiiDisc(testpath);
			GCWiiHeader hdr = gcdisc.getHeader();
			hdr.printInfo();
			DirectoryNode root = gcdisc.getDiscTree();
			root.printMeToStdErr(0);*/
			GCMemCard mc = GCMemCard.readRawMemoryCardFile(mcpath);
			mc.debugPrint();
		}
		catch(Exception x){
			x.printStackTrace();
			System.exit(1);
		}
		
	}

}
