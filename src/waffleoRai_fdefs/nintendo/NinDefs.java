package waffleoRai_fdefs.nintendo;

import waffleoRai_Files.FileClass;
import waffleoRai_Files.GenericSystemDef;

public class NinDefs {
	
	private static SHA256TableDef sha256_def;
	private static SHA1TableDef sha1_def;
	
	public static SHA256TableDef getSHA256Def(){
		if(sha256_def == null) sha256_def = new SHA256TableDef();
		return sha256_def;
	}
	
	public static SHA1TableDef getSHA1Def(){
		if(sha1_def == null) sha1_def = new SHA1TableDef();
		return sha1_def;
	}
	
	public static class SHA256TableDef extends GenericSystemDef{
		
		private static String DEFO_ENG_DESC = "SHA-256 Hash Table";
		public static int TYPE_ID = 0x53484132;
		
		public SHA256TableDef(){
			super(DEFO_ENG_DESC, TYPE_ID);
		}
		
		public FileClass getFileClass(){return FileClass.DAT_HASHTABLE;}
		
	}
	
	public static class SHA1TableDef extends GenericSystemDef{
		
		private static String DEFO_ENG_DESC = "SHA-1 Hash Table";
		public static int TYPE_ID = 0x53484131;
		
		public SHA1TableDef(){
			super(DEFO_ENG_DESC, TYPE_ID);
		}
		
		public FileClass getFileClass(){return FileClass.DAT_HASHTABLE;}
		
	}

	
	
}
