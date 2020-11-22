package waffleoRai_fdefs.nintendo;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import waffleoRai_Files.FileClass;
import waffleoRai_Files.FileTypeDefinition;
import waffleoRai_Files.GenericSystemDef;

public class PowerGCSysFileDefs {
	
	private static GCHeaderDef header_def;
	private static GCFSTDef fst_def;
	private static GCBi2Def bi2_def;
	private static GCApploaderDef appl_def;
	
	private static WiiRSADef rsa_def;
	private static WiiPartTableDef pt_def;
	private static WiiRegInfoDef reg_def;
	private static WiiTicketDef ticket_def;
	private static WiiTMDDef tmd_def;
	private static WiiH3Def h3_def;
	
	private static WiiUPartTableDef pt_def_u;
	private static WiiUPartHeaderDef ph_def_u;
	private static WiiUPartFSTDef fst_def_u;
	private static WiiUDiscHeaderDef dh_def_u;
	private static WiiUExeDef rpx_def;
	private static WiiURPLDef rpl_def;
	
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
	
	public static WiiPartTableDef getPartTableDef(){
		if(pt_def == null) pt_def = new WiiPartTableDef();
		return pt_def;
	}
	
	public static WiiUPartTableDef getWiiUPartTableDef(){
		if(pt_def_u == null) pt_def_u = new WiiUPartTableDef();
		return pt_def_u;
	}
	
	public static WiiRegInfoDef getRegInfoDef(){
		if(reg_def == null) reg_def = new WiiRegInfoDef();
		return reg_def;
	}
	
	public static WiiTicketDef getWiiTicketDef(){
		if(ticket_def == null) ticket_def = new WiiTicketDef();
		return ticket_def;
	}
	
	public static WiiTMDDef getWiiTMDDef(){
		if(tmd_def == null) tmd_def = new WiiTMDDef();
		return tmd_def;
	}
	
	public static WiiH3Def getWiiH3Def(){
		if(h3_def == null) h3_def = new WiiH3Def();
		return h3_def;
	}
	
	public static WiiUPartHeaderDef getWiiUPartHeaderDef(){
		if(ph_def_u == null) ph_def_u = new WiiUPartHeaderDef();
		return ph_def_u;
	}
	
	public static WiiUPartFSTDef getWiiUFSTDef(){
		if(fst_def_u == null) fst_def_u = new WiiUPartFSTDef();
		return fst_def_u;
	}
	
	public static WiiUDiscHeaderDef getWiiUDiscHeaderDef(){
		if(dh_def_u == null) dh_def_u = new WiiUDiscHeaderDef();
		return dh_def_u;
	}
	
	public static WiiUExeDef getWiiUExeDef(){
		if(rpx_def == null) rpx_def = new WiiUExeDef();
		return rpx_def;
	}
	
	public static WiiURPLDef getWiiURPLDef(){
		if(rpl_def == null) rpl_def = new WiiURPLDef();
		return rpl_def;
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
		public String toString(){return FileTypeDefinition.stringMe(this);}
		
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
		public String toString(){return FileTypeDefinition.stringMe(this);}
		
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
		public String toString(){return FileTypeDefinition.stringMe(this);}
		
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
		public String toString(){return FileTypeDefinition.stringMe(this);}
		
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
			slist.add("bin");
			return slist;
		}

		public String getDescription() {return str;}
		public FileClass getFileClass() {return FileClass.SYSTEM;}
		public int getTypeID() {return TYPE_ID;}
		public void setDescriptionString(String s) {str = s;}
		
		public String getDefaultExtension(){return "bin";}
		public String toString(){return FileTypeDefinition.stringMe(this);}
		
	}
	
	public static class WiiPartTableDef implements FileTypeDefinition{
		
		private static String DEFO_ENG_DESC = "Nintendo Wii Disc Partition Table";
		public static int TYPE_ID = 0x7c59782b;
		
		private String str;
		
		public WiiPartTableDef(){
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
		public String toString(){return FileTypeDefinition.stringMe(this);}
		
	}
	
	public static class WiiUPartTableDef implements FileTypeDefinition{
		
		private static String DEFO_ENG_DESC = "Nintendo Wii U Disc Partition Table";
		public static int TYPE_ID = 0x7c59782c;
		
		private String str;
		
		public WiiUPartTableDef(){
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
		public String toString(){return FileTypeDefinition.stringMe(this);}
		
	}
	
	public static class WiiRegInfoDef implements FileTypeDefinition{
		
		private static String DEFO_ENG_DESC = "Nintendo Wii Region Info";
		public static int TYPE_ID = 0x7c6803b8;
		
		private String str;
		
		public WiiRegInfoDef(){
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
		public String toString(){return FileTypeDefinition.stringMe(this);}
		
	}
	
	public static class WiiTicketDef implements FileTypeDefinition{
		
		private static String DEFO_ENG_DESC = "Nintendo Wii/Wii U Partition Ticket";
		public static int TYPE_ID = 0x7cd498ee;
		
		private String str;
		
		public WiiTicketDef(){
			str = DEFO_ENG_DESC;
		}

		public Collection<String> getExtensions() {
			List<String> slist = new LinkedList<String>();
			slist.add("tik");
			slist.add("bin");
			return slist;
		}

		public String getDescription() {return str;}
		public FileClass getFileClass() {return FileClass.SYSTEM;}
		public int getTypeID() {return TYPE_ID;}
		public void setDescriptionString(String s) {str = s;}
		
		public String getDefaultExtension(){return "tik";}
		public String toString(){return FileTypeDefinition.stringMe(this);}
		
	}
	
	public static class WiiTMDDef implements FileTypeDefinition{
		
		private static String DEFO_ENG_DESC = "Nintendo Wii/Wii U Title Metadata";
		public static int TYPE_ID = 0x7c286a84;
		
		private String str;
		
		public WiiTMDDef(){
			str = DEFO_ENG_DESC;
		}

		public Collection<String> getExtensions() {
			List<String> slist = new LinkedList<String>();
			slist.add("tmd");
			slist.add("bin");
			return slist;
		}

		public String getDescription() {return str;}
		public FileClass getFileClass() {return FileClass.SYSTEM;}
		public int getTypeID() {return TYPE_ID;}
		public void setDescriptionString(String s) {str = s;}
		
		public String getDefaultExtension(){return "tmd";}
		public String toString(){return FileTypeDefinition.stringMe(this);}
	}
	
	public static class WiiH3Def implements FileTypeDefinition{
		
		private static String DEFO_ENG_DESC = "Nintendo Wii h3 Hashtable";
		public static int TYPE_ID = 0x7c093560;
		
		private String str;
		
		public WiiH3Def(){
			str = DEFO_ENG_DESC;
		}

		public Collection<String> getExtensions() {
			List<String> slist = new LinkedList<String>();
			slist.add("bin");
			return slist;
		}

		public String getDescription() {return str;}
		public FileClass getFileClass() {return FileClass.DAT_HASHTABLE;}
		public int getTypeID() {return TYPE_ID;}
		public void setDescriptionString(String s) {str = s;}
		
		public String getDefaultExtension(){return "bin";}
		public String toString(){return FileTypeDefinition.stringMe(this);}
	}
		
	public static class WiiUPartHeaderDef extends GenericSystemDef{
		
		private static String DEFO_ENG_DESC = "Nintendo Wii U Disc Partition Header";
		public static int TYPE_ID = 0x7c59782d;
		
		public WiiUPartHeaderDef(){
			super(DEFO_ENG_DESC, TYPE_ID);
		}
		
	}
	
	public static class WiiUPartFSTDef extends GenericSystemDef{
		
		private static String DEFO_ENG_DESC = "Nintendo Wii U Disc Partition File System Table";
		public static int TYPE_ID = 0x7c59782e;
		
		public WiiUPartFSTDef(){
			super(DEFO_ENG_DESC, TYPE_ID);
		}
		
	}
	
	public static class WiiUDiscHeaderDef extends GenericSystemDef{
		
		private static String DEFO_ENG_DESC = "Nintendo Wii U Disc Header";
		public static int TYPE_ID = 0x7c59782f;
		
		public WiiUDiscHeaderDef(){
			super(DEFO_ENG_DESC, TYPE_ID);
		}
		
	}
	
	public static class WiiUExeDef implements FileTypeDefinition{
		
		private static String DEFO_ENG_DESC = "Cafe OS ELF - Main Executable";
		public static int TYPE_ID = 0x7c437699;
		
		private String str;
		
		public WiiUExeDef(){
			str = DEFO_ENG_DESC;
		}

		public Collection<String> getExtensions() {
			List<String> slist = new LinkedList<String>();
			slist.add("rpx");
			return slist;
		}

		public String getDescription() {return str;}
		public FileClass getFileClass() {return FileClass.EXECUTABLE;}
		public int getTypeID() {return TYPE_ID;}
		public void setDescriptionString(String s) {str = s;}
		
		public String getDefaultExtension(){return "rpx";}
		public String toString(){return FileTypeDefinition.stringMe(this);}
		
	}
	
	public static class WiiURPLDef implements FileTypeDefinition{
		
		private static String DEFO_ENG_DESC = "Cafe OS ELF - DLL";
		public static int TYPE_ID = 0x7c43769a;
		
		private String str;
		
		public WiiURPLDef(){
			str = DEFO_ENG_DESC;
		}

		public Collection<String> getExtensions() {
			List<String> slist = new LinkedList<String>();
			slist.add("rpl");
			return slist;
		}

		public String getDescription() {return str;}
		public FileClass getFileClass() {return FileClass.CODELIB;}
		public int getTypeID() {return TYPE_ID;}
		public void setDescriptionString(String s) {str = s;}
		
		public String getDefaultExtension(){return "rpl";}
		public String toString(){return FileTypeDefinition.stringMe(this);}
		
	}
	
}
