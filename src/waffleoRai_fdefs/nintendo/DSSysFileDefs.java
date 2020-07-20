package waffleoRai_fdefs.nintendo;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import waffleoRai_Files.FileClass;
import waffleoRai_Files.FileTypeDefinition;

public class DSSysFileDefs {
	
	private static DSHeaderDef header_def;
	private static DSBannerFileDef banner_def;
	private static DSiCertDef cert_def;
	
	public static DSHeaderDef getHeaderDef(){
		if(header_def == null) header_def = new DSHeaderDef();
		return header_def;
	}
	
	public static DSBannerFileDef getBannerDef(){
		if(banner_def == null) banner_def = new DSBannerFileDef();
		return banner_def;
	}
	
	public static DSiCertDef getRSACertDef(){
		if(cert_def == null) cert_def = new DSiCertDef();
		return cert_def;
	}
	
	public static class DSHeaderDef implements FileTypeDefinition{
		
		private static String DEFO_ENG_DESC = "Nintendo DS/DSi Software Header";
		public static int TYPE_ID = 0x6357bc67;
		
		private String str;
		
		public DSHeaderDef(){
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
	
	public static class DSBannerFileDef implements FileTypeDefinition{

		private static String DEFO_ENG_DESC = "Nintendo DS/DSi Banner Data File";
		public static int TYPE_ID = 0x31c0d654;
		
		private String str;
		
		public DSBannerFileDef(){
			str = DEFO_ENG_DESC;
		}

		public Collection<String> getExtensions() {
			List<String> slist = new LinkedList<String>();
			slist.add("bin");
			return slist;
		}

		public String getDescription() {return str;}
		public FileClass getFileClass() {return FileClass.DAT_BANNER;}
		public int getTypeID() {return TYPE_ID;}
		public void setDescriptionString(String s) {str = s;}
		
		public String getDefaultExtension(){return "bin";}
		
	}
	
	public static class DSiCertDef implements FileTypeDefinition{

		private static String DEFO_ENG_DESC = "Nintendo DSi RSA Certificate";
		public static int TYPE_ID = 0x263baf53;
		
		private String str;
		
		public DSiCertDef(){
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

}
