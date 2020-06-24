package waffleoRai_Executable.nintendo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import waffleoRai_Executable.BinInst;
import waffleoRai_Executable.BincodeTypeDef;
import waffleoRai_Files.FileClass;

public class DolExe {
	
	private static DolExeDef exe_def;
	public static int TYPE_ID = 0x7c646f6c;
	public static String DEFO_ENG_STR = "Nintendo Dolphin Executable";
	
	public static class DolExeDef extends BincodeTypeDef{
		
		private static String[] EXT_LIST = {"dol"};
		
		private String desc;
		
		public DolExeDef(){
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
		public String getDefaultExtension() {return "dol";}
		
	}

	public static DolExeDef getDefinition(){
		if(exe_def == null) exe_def = new DolExeDef();
		return exe_def;
	}
	

}
