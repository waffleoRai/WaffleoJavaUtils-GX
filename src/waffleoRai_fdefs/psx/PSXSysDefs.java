package waffleoRai_fdefs.psx;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import waffleoRai_Files.FileClass;
import waffleoRai_Files.FileTypeDefinition;

public class PSXSysDefs {
	
	private static PSXCfgDef cfgdef;
	private static PSXEXEDef exedef;
	
	public static PSXCfgDef getConfigDef(){
		if(cfgdef == null) cfgdef = new PSXCfgDef();
		return cfgdef;
	}
	
	public static PSXEXEDef getExeDef(){
		if(exedef == null) exedef = new PSXEXEDef();
		return exedef;
	}
	
	public static class PSXCfgDef implements FileTypeDefinition{
		
		private static String DEFO_ENG_DESC = "PlayStation 1 Boot Config File";
		public static int TYPE_ID = 0x11434647;
		
		private String str;
		
		public PSXCfgDef(){
			str = DEFO_ENG_DESC;
		}

		public Collection<String> getExtensions() {
			List<String> slist = new LinkedList<String>();
			slist.add("cnf");
			return slist;
		}

		public String getDescription() {return str;}
		public FileClass getFileClass() {return FileClass.SYSTEM;}
		public int getTypeID() {return TYPE_ID;}
		public void setDescriptionString(String s) {str = s;}
		
		public String getDefaultExtension(){return "CNF";}
		public String toString(){return FileTypeDefinition.stringMe(this);}
		
	}
	
	public static class PSXEXEDef implements FileTypeDefinition{
		
		private static String DEFO_ENG_DESC = "PlayStation 1 Executable File";
		public static int TYPE_ID = 0x11505845;
		
		private String str;
		
		public PSXEXEDef(){
			str = DEFO_ENG_DESC;
		}

		public Collection<String> getExtensions() {
			List<String> slist = new LinkedList<String>();
			//slist.add("");
			return slist;
		}

		public String getDescription() {return str;}
		public FileClass getFileClass() {return FileClass.EXECUTABLE;}
		public int getTypeID() {return TYPE_ID;}
		public void setDescriptionString(String s) {str = s;}
		
		public String getDefaultExtension(){return "psxexe";}
		public String toString(){return FileTypeDefinition.stringMe(this);}
		
	}
	
}
