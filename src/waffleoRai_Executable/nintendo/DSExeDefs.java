package waffleoRai_Executable.nintendo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import waffleoRai_Executable.BinInst;
import waffleoRai_Executable.BincodeTypeDef;
import waffleoRai_Files.FileClass;

public class DSExeDefs {
	
	public static class DSARM7ExeDef extends BincodeTypeDef{
		
		private static int TYPE_ID = 0x7ADE7679;
		
		private static String DEFO_ENG_STR = "Nintendo DS ARM7 Executable";
		private static String[] EXT_LIST = {"arm7", "ARM7"};
		
		private String desc;
		
		public DSARM7ExeDef(){
			desc = DEFO_ENG_STR;
		}
		
		public FileClass getFileClass(){return FileClass.EXECUTABLE;}
		public boolean canDisassemble(){return false;}
		public Collection<BinInst> disassemble(){return null;}
		
		public Collection<String> getExtensions() {
			List<String> elist = new ArrayList<String>(EXT_LIST.length);
			for(int i = 0; i < EXT_LIST.length; i++) elist.add(EXT_LIST[i]);
			
			return elist;
		}

		public String getDescription() {return desc;}

		public int getTypeID() {return TYPE_ID;}

		public void setDescriptionString(String s) {desc = s;}
		
		public static int getTypeIDStatic(){return TYPE_ID;}

		public String getDefaultExtension() {return "arm7";}
		
	}
	
	public static class DSARM9ExeDef extends BincodeTypeDef{
		
		private static int TYPE_ID = 0x4ADE9605;
		
		private static String DEFO_ENG_STR = "Nintendo DS ARM9 Executable";
		private static String[] EXT_LIST = {"arm9", "ARM9"};
		
		private String desc;
		
		public DSARM9ExeDef(){
			desc = DEFO_ENG_STR;
		}
		
		public FileClass getFileClass(){return FileClass.EXECUTABLE;}
		public boolean canDisassemble(){return false;}
		public Collection<BinInst> disassemble(){return null;}
		
		public Collection<String> getExtensions() {
			List<String> elist = new ArrayList<String>(EXT_LIST.length);
			for(int i = 0; i < EXT_LIST.length; i++) elist.add(EXT_LIST[i]);
			
			return elist;
		}

		public String getDescription() {return desc;}

		public int getTypeID() {return TYPE_ID;}

		public void setDescriptionString(String s) {desc = s;}
		
		public static int getTypeIDStatic(){return TYPE_ID;}
		
		public String getDefaultExtension() {return "arm9";}
	}

	public static class DSARM7iExeDef extends BincodeTypeDef{
		
		private static int TYPE_ID = 0x7AD17123;
		
		private static String DEFO_ENG_STR = "Nintendo DSi ARM7i Executable";
		private static String[] EXT_LIST = {"arm7i", "ARM7i"};
		
		private String desc;
		
		public DSARM7iExeDef(){
			desc = DEFO_ENG_STR;
		}
		
		public FileClass getFileClass(){return FileClass.EXECUTABLE;}
		public boolean canDisassemble(){return false;}
		public Collection<BinInst> disassemble(){return null;}
		
		public Collection<String> getExtensions() {
			List<String> elist = new ArrayList<String>(EXT_LIST.length);
			for(int i = 0; i < EXT_LIST.length; i++) elist.add(EXT_LIST[i]);
			
			return elist;
		}

		public String getDescription() {return desc;}

		public int getTypeID() {return TYPE_ID;}

		public void setDescriptionString(String s) {desc = s;}
		
		public static int getTypeIDStatic(){return TYPE_ID;}
		
		public String getDefaultExtension() {return "arm7i";}
		
	}
	
	public static class DSARM9iExeDef extends BincodeTypeDef{
		
		private static int TYPE_ID = 0x1AD19932;
		
		private static String DEFO_ENG_STR = "Nintendo DSi ARM9i Executable";
		private static String[] EXT_LIST = {"arm9i", "ARM9i"};
		
		private String desc;
		
		public DSARM9iExeDef(){
			desc = DEFO_ENG_STR;
		}
		
		public FileClass getFileClass(){return FileClass.EXECUTABLE;}
		public boolean canDisassemble(){return false;}
		public Collection<BinInst> disassemble(){return null;}
		
		public Collection<String> getExtensions() {
			List<String> elist = new ArrayList<String>(EXT_LIST.length);
			for(int i = 0; i < EXT_LIST.length; i++) elist.add(EXT_LIST[i]);
			
			return elist;
		}

		public String getDescription() {return desc;}

		public int getTypeID() {return TYPE_ID;}

		public void setDescriptionString(String s) {desc = s;}
		
		public static int getTypeIDStatic(){return TYPE_ID;}
		
		public String getDefaultExtension() {return "arm9i";}
		
	}

	private static DSARM7ExeDef arm7_def;
	private static DSARM9ExeDef arm9_def;
	private static DSARM7iExeDef arm7i_def;
	private static DSARM9iExeDef arm9i_def;
	
	public static DSARM7ExeDef getDefARM7(){
		if(arm7_def == null) arm7_def = new DSARM7ExeDef();
		return arm7_def;
	}
	
	public static DSARM9ExeDef getDefARM9(){
		if(arm9_def == null) arm9_def = new DSARM9ExeDef();
		return arm9_def;
	}
	
	public static DSARM7iExeDef getDefARM7i(){
		if(arm7i_def == null) arm7i_def = new DSARM7iExeDef();
		return arm7i_def;
	}
	
	public static DSARM9iExeDef getDefARM9i(){
		if(arm9i_def == null) arm9i_def = new DSARM9iExeDef();
		return arm9i_def;
	}
	
}
