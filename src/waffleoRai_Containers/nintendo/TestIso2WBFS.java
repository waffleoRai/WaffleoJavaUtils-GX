package waffleoRai_Containers.nintendo;

import waffleoRai_Utils.FileBuffer;

public class TestIso2WBFS {

	public static void main(String[] args) 
	{
		//String inpath = "E:\\Library\\Games\\Console\\RVL_RSBE_USA.iso";
		//String outpath = "E:\\Library\\Games\\Console\\RVL_RSBE_USA.wbfs";
		
		//String inpath = "E:\\Library\\Games\\Console\\Xenoblade\\Xenoblade Chronicles [NTSC-U].iso";
		//String outpath = "E:\\Library\\Games\\Console\\RVL_SX4E_USZ.wbfs";
		
		String inpath = "E:\\Library\\Games\\Console\\WUP_AZAE_USZ.wud";
		String outpath = "E:\\Library\\Games\\Console\\WUP_AZAE_USZ.wuci";
		String keypath = "E:\\Library\\Games\\Console\\dumplogs\\WUP_P_AZAE\\game.key";
		
		try
		{
			//WBFSImage.writeToWBFS(inpath, outpath, true);
			FileBuffer keybuff = new FileBuffer(keypath);
			byte[] key = keybuff.getBytes();
			WUCImage.writeWUci(inpath, outpath, key, true);
		}
		catch(Exception e)
		{
			System.err.println("Error Message: " + e.getMessage());
			e.printStackTrace();
			System.exit(1);
		}
	}

}
