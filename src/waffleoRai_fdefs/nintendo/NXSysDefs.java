package waffleoRai_fdefs.nintendo;

import waffleoRai_Files.GenericSystemDef;

public class NXSysDefs {
	
	private static NXNCAHeaderDef ncah_def;
	private static NXRomFSHeaderDef romfsh_def;
	private static NXRomFSTDef romfst_def;
	
	private static NXPFSHeaderDef pfsh_def;
	private static NXHFSHeaderDef hfsh_def;
	private static NXXCIHeaderDef xcih_def;
	
	public static NXNCAHeaderDef getNCAHeaderDef(){
		if(ncah_def == null) ncah_def = new NXNCAHeaderDef();
		return ncah_def;
	}
	
	public static NXRomFSHeaderDef getRomFSHeaderDef(){
		if(romfsh_def == null) romfsh_def = new NXRomFSHeaderDef();
		return romfsh_def;
	}
	
	public static NXRomFSTDef getRomFSTableDef(){
		if(romfst_def == null) romfst_def = new NXRomFSTDef();
		return romfst_def;
	}
	
	public static NXPFSHeaderDef getPFSHeaderDef(){
		if(pfsh_def == null) pfsh_def = new NXPFSHeaderDef();
		return pfsh_def;
	}
	
	public static NXHFSHeaderDef getHFSHeaderDef(){
		if(hfsh_def == null) hfsh_def = new NXHFSHeaderDef();
		return hfsh_def;
	}
	
	public static NXXCIHeaderDef getXCIHeaderDef(){
		if(xcih_def == null) xcih_def = new NXXCIHeaderDef();
		return xcih_def;
	}

	public static class NXNCAHeaderDef extends GenericSystemDef{
		
		private static String DEFO_ENG_DESC = "Nintendo Switch NCA Header";
		public static int TYPE_ID = 0x4e434168;
		
		public NXNCAHeaderDef(){
			super(DEFO_ENG_DESC, TYPE_ID);
		}
		
	}
	
	public static class NXRomFSHeaderDef extends GenericSystemDef{
		
		private static String DEFO_ENG_DESC = "Nintendo Switch RomFS Header";
		public static int TYPE_ID = 0x6e78524d;
		
		public NXRomFSHeaderDef(){
			super(DEFO_ENG_DESC, TYPE_ID);
		}
		
	}
	
	public static class NXRomFSTDef extends GenericSystemDef{
		
		private static String DEFO_ENG_DESC = "Nintendo Switch RomFS File System Table";
		public static int TYPE_ID = 0x58526673;
		
		public NXRomFSTDef(){
			super(DEFO_ENG_DESC, TYPE_ID);
		}
		
	}
	
	public static class NXPFSHeaderDef extends GenericSystemDef{
		
		private static String DEFO_ENG_DESC = "Nintendo Switch Partition File System Header";
		public static int TYPE_ID = 0x50465330;
		
		public NXPFSHeaderDef(){
			super(DEFO_ENG_DESC, TYPE_ID);
		}
		
	}
	
	public static class NXHFSHeaderDef extends GenericSystemDef{
		
		private static String DEFO_ENG_DESC = "Nintendo Switch HFS Header";
		public static int TYPE_ID = 0x48465330;
		
		public NXHFSHeaderDef(){
			super(DEFO_ENG_DESC, TYPE_ID);
		}
		
	}
	
	public static class NXXCIHeaderDef extends GenericSystemDef{
		
		private static String DEFO_ENG_DESC = "NX Cartridge Image Header";
		public static int TYPE_ID = 0x6e784354;
		
		public NXXCIHeaderDef(){
			super(DEFO_ENG_DESC, TYPE_ID);
		}
		
	}
	
}
