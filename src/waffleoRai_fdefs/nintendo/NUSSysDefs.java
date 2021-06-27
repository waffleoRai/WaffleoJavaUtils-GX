package waffleoRai_fdefs.nintendo;

import waffleoRai_Files.FileClass;
import waffleoRai_Files.GenericSystemDef;

public class NUSSysDefs {

	private static NUSHeaderDef hdr_def;
	private static NUSBootCodeDef bootcode_def;
	private static NUSGameDataDef gamedat_def;
	
	public static NUSHeaderDef getNUSHeaderDef(){
		if(hdr_def == null) hdr_def = new NUSHeaderDef();
		return hdr_def;
	}
	
	public static NUSBootCodeDef getBootCodeDef(){
		if(bootcode_def == null) bootcode_def = new NUSBootCodeDef();
		return bootcode_def;
	}
	
	public static NUSGameDataDef getGameROMDef(){
		if(gamedat_def == null) gamedat_def = new NUSGameDataDef();
		return gamedat_def;
	}
	
	public static class NUSHeaderDef extends GenericSystemDef{
		
		private static String DEFO_ENG_DESC = "Nintendo 64 Game Pak Header";
		public static int TYPE_ID = 0x64123780;
		
		public NUSHeaderDef(){
			super(DEFO_ENG_DESC, TYPE_ID);
		}
		
	}
	
	public static class NUSBootCodeDef extends GenericSystemDef{
		
		private static String DEFO_ENG_DESC = "Nintendo 64 ROM Boot Code";
		public static int TYPE_ID = 0x64123781;
		
		public NUSBootCodeDef(){
			super(DEFO_ENG_DESC, TYPE_ID);
		}
		
		public FileClass getFileClass(){
			return FileClass.EXECUTABLE;
		}
		
	}

	public static class NUSGameDataDef extends GenericSystemDef{
	
		private static String DEFO_ENG_DESC = "Nintendo 64 ROM Game Data";
		public static int TYPE_ID = 0x64123782;
	
		public NUSGameDataDef(){
			super(DEFO_ENG_DESC, TYPE_ID);
		}
	
	}
	
}
