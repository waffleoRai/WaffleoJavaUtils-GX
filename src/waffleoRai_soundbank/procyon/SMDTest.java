package waffleoRai_soundbank.procyon;

import waffleoRai_Utils.FileBuffer;

public class SMDTest {

	public static void main(String[] args) {
		
		String smdpath = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\ds_test\\pokedun_bgm\\bgm0028.smd";
		String swdpath = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\ds_test\\pokedun_bgm\\bgm0028.swd";
		String swdarcpath = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\ds_test\\pokedun_bgm\\bgm.swd";
		
		String wavdir = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\ds_test\\pokedun_bgm\\bgm_wav";
		String sf2path = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\ds_test\\bgm0028.sf2";

		try{
			
			//I guess just test the parsers for now
			
			FileBuffer dat0 = FileBuffer.createBuffer(swdarcpath, false);
			SWD swd_war = SWD.readSWD(dat0, 0);
			//swd_war.debugPrint();
			
			//Let's try extracting contents to wav
			//swd_war.dumpWavs(wavdir);
			
			FileBuffer dat1 = FileBuffer.createBuffer(swdpath, false);
			SWD swd = SWD.readSWD(dat1, 0);
			swd.debugPrint();
			
			swd.loadSoundDataFrom(swd_war);
			//swd.writeSF2(sf2path);
		}
		catch(Exception e){
			e.printStackTrace();
			System.exit(1);
		}
		
	}

}
