package waffleoRai_fdefs.nintendo;

import waffleoRai_Files.FileClass;
import waffleoRai_Files.GenericSystemDef;

public class NinDefs {
	
	private static SHA256Table sha256_def;
	
	public static SHA256Table getSHA256Def(){
		if(sha256_def == null) sha256_def = new SHA256Table();
		return sha256_def;
	}
	
	public static class SHA256Table extends GenericSystemDef{
		
		private static String DEFO_ENG_DESC = "SHA-256 Hash Table";
		public static int TYPE_ID = 0x53484132;
		
		public SHA256Table(){
			super(DEFO_ENG_DESC, TYPE_ID);
		}
		
		public FileClass getFileClass(){return FileClass.DAT_HASHTABLE;}
		
	}

}
