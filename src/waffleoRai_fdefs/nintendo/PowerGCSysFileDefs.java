package waffleoRai_fdefs.nintendo;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import waffleoRai_Files.FileClass;
import waffleoRai_Files.FileTypeDefinition;

public class PowerGCSysFileDefs {
	
	private static GCHeaderDef header_def;
	private static GCFSTDef fst_def;
	private static GCBi2Def bi2_def;
	private static GCApploaderDef appl_def;
	private static WiiRSADef rsa_def;
	
	public static GCHeaderDef getHeaderDef(){
		if(header_def == null) header_def = new GCHeaderDef();
		return header_def;
	}
	
	public static GCFSTDef getFSTDef(){
		if(fst_def == null) fst_def = new GCFSTDef();
		return fst_def;
	}
	
	public static GCBi2Def getBi2Def(){
		if(bi2_def == null) bi2_def = new GCBi2Def();
		return bi2_def;
	}
	
	public static GCApploaderDef getApploaderDef(){
		if(appl_def == null) appl_def = new GCApploaderDef();
		return appl_def;
	}
	
	public static WiiRSADef getRSADef(){
		if(rsa_def == null) rsa_def = new WiiRSADef();
		return rsa_def;
	}
	
	public static class GCHeaderDef implements FileTypeDefinition{
		
		private static String DEFO_ENG_DESC = "Nintendo GameCube Family Disk Header";
		public static int TYPE_ID = 0x7c486472;
		
		private String str;
		
		public GCHeaderDef(){
			str = DEFO_ENG_DESC;
		}

		public Collection<String> getExtensions() {
			List<String> slist = new LinkedList<String>();
			slist.add("bin");
			return slist;
		}

		public String getDescription() {return str;}
		public FileClass getFileClass() {return FileClass.SYSTEM;}
		public int getTypeID() {return TYPE_ID;}
		public void setDescriptionString(String s) {str = s;}
		
		public String getDefaultExtension(){return "bin";}
		
	}
	
	public static class GCFSTDef implements FileTypeDefinition{
		
		private static String DEFO_ENG_DESC = "Nintendo GameCube Family File System Table";
		public static int TYPE_ID = 0x7c667342;
		
		private String str;
		
		public GCFSTDef(){
			str = DEFO_ENG_DESC;
		}

		public Collection<String> getExtensions() {
			List<String> slist = new LinkedList<String>();
			slist.add("bin");
			return slist;
		}

		public String getDescription() {return str;}
		public FileClass getFileClass() {return FileClass.SYSTEM;}
		public int getTypeID() {return TYPE_ID;}
		public void setDescriptionString(String s) {str = s;}
		
		public String getDefaultExtension(){return "bin";}
		
	}
	
	public static class GCBi2Def implements FileTypeDefinition{
		
		private static String DEFO_ENG_DESC = "Nintendo GameCube Family Disk SubHeader Data";
		public static int TYPE_ID = 0x7c626932;
		
		private String str;
		
		public GCBi2Def(){
			str = DEFO_ENG_DESC;
		}

		public Collection<String> getExtensions() {
			List<String> slist = new LinkedList<String>();
			slist.add("bin");
			return slist;
		}

		public String getDescription() {return str;}
		public FileClass getFileClass() {return FileClass.SYSTEM;}
		public int getTypeID() {return TYPE_ID;}
		public void setDescriptionString(String s) {str = s;}
		
		public String getDefaultExtension(){return "bin";}
		
	}
	
	public static class GCApploaderDef implements FileTypeDefinition{
		
		private static String DEFO_ENG_DESC = "Nintendo GameCube Family Apploader";
		public static int TYPE_ID = 0x7c61704c;
		
		private String str;
		
		public GCApploaderDef(){
			str = DEFO_ENG_DESC;
		}

		public Collection<String> getExtensions() {
			List<String> slist = new LinkedList<String>();
			slist.add("img");
			return slist;
		}

		public String getDescription() {return str;}
		public FileClass getFileClass() {return FileClass.SYSTEM;}
		public int getTypeID() {return TYPE_ID;}
		public void setDescriptionString(String s) {str = s;}
		
		public String getDefaultExtension(){return "img";}
		
	}
	
	public static class WiiRSADef implements FileTypeDefinition{
		
		private static String DEFO_ENG_DESC = "Nintendo Wii RSA Certificate Chain";
		public static int TYPE_ID = 0x7c525341;
		
		private String str;
		
		public WiiRSADef(){
			str = DEFO_ENG_DESC;
		}

		public Collection<String> getExtensions() {
			List<String> slist = new LinkedList<String>();
			slist.add("img");
			return slist;
		}

		public String getDescription() {return str;}
		public FileClass getFileClass() {return FileClass.SYSTEM;}
		public int getTypeID() {return TYPE_ID;}
		public void setDescriptionString(String s) {str = s;}
		
		public String getDefaultExtension(){return "img";}
		
	}
		
}
