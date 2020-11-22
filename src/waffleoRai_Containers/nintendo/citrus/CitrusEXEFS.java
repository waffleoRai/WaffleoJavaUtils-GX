package waffleoRai_Containers.nintendo.citrus;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import waffleoRai_Executable.BinInst;
import waffleoRai_Executable.BincodeTypeDef;
import waffleoRai_Files.FileClass;
import waffleoRai_Files.FileTypeDefNode;
import waffleoRai_Files.FileTypeDefinition;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Files.tree.DirectoryNode;
import waffleoRai_Files.tree.FileNode;

public class CitrusEXEFS {
	
	/*----- Constants -----*/
	
	/*----- Instance Variables -----*/
	
	private String[] names;
	private long[] offsets;
	private long[] lengths;
	private byte[][] hashes;
	
	/*----- Construction/Parsing -----*/
	
	private CitrusEXEFS(){
		names = new String[10];
		offsets = new long[10];
		lengths = new long[10];
		hashes = new byte[10][0x20];
		
	};
	
	public static CitrusEXEFS readExeFSHeader(FileBuffer data, long offset){

		CitrusEXEFS exefs = new CitrusEXEFS();
		data.setEndian(false);
		
		long cpos = offset;
		for(int i = 0; i < 10; i++){
			byte check = data.getByte(cpos);
			if(check == 0) {cpos += 16; continue;}
			
			exefs.names[i] = data.getASCII_string(cpos, 8); cpos += 8;
			exefs.offsets[i] = Integer.toUnsignedLong(data.intFromFile(cpos)); cpos+=4;
			exefs.lengths[i] = Integer.toUnsignedLong(data.intFromFile(cpos)); cpos+=4;
		}
		
		cpos += 0x20;
		
		//Remember, hashes are stored in reverse order...
		for(int i = 9; i >= 0; i--){
			for(int j = 0; j < 0x20; j++) exefs.hashes[i][j] = data.getByte(cpos++);
		}
		
		return exefs;
	}
	
	/*----- Getters -----*/
	
	public DirectoryNode getFileTree(){
		DirectoryNode root = new DirectoryNode(null, "ExeFS");
		
		for(int i = 0; i < 10; i++){
			String name = names[i];
			if(name == null || name.isEmpty()) continue;
			FileNode fn = new FileNode(root, name);
			fn.setOffset(offsets[i]);
			fn.setLength(lengths[i]);
			
			//Type
			if(name.equals(".code")) fn.setTypeChainHead(new FileTypeDefNode(getMainExeDef()));
			else if(name.equals("banner")) fn.setTypeChainHead(new FileTypeDefNode(getBannerDef()));
			else if(name.equals("icon")) fn.setTypeChainHead(new FileTypeDefNode(CitrusSMDH.getIconDef()));
		}
		
		return root;
	}
	
	public byte[] getFileHash(String filename){

		for(int i = 0; i < 10; i++){
			if(names[i] == null) continue;
			if(names[i].equalsIgnoreCase(filename)) return hashes[i];
		}
		
		return null;
	}
	
	/*----- Setters -----*/
	
	/*----- Definition -----*/
	
	private static CitrusBannerDef bnr_def;
	private static CxiMainExeDef code_def;
	
	public static CitrusBannerDef getBannerDef(){
		if(bnr_def == null) bnr_def = new CitrusBannerDef();
		return bnr_def;
	}
	
	public static CxiMainExeDef getMainExeDef(){
		if(code_def == null) code_def = new CxiMainExeDef();
		return code_def;
	}
	
	public static class CitrusBannerDef implements FileTypeDefinition{

		private static String DEFO_ENG_DESC = "Nintendo 3DS System Banner";
		public static int TYPE_ID = 0x3d5987b0;
		
		private String str;
		
		public CitrusBannerDef(){
			str = DEFO_ENG_DESC;
		}

		public Collection<String> getExtensions() {
			List<String> slist = new LinkedList<String>();
			//slist.add("bin");
			return slist;
		}

		public String getDescription() {return str;}
		public FileClass getFileClass() {return FileClass.DAT_BANNER;}
		public int getTypeID() {return TYPE_ID;}
		public void setDescriptionString(String s) {str = s;}
		
		public String getDefaultExtension(){return "";}
		public String toString(){return FileTypeDefinition.stringMe(this);}
		
	}
	
	public static class CxiMainExeDef extends BincodeTypeDef{

		private static String DEFO_ENG_DESC = "Nintendo 3DS CXI ARM11 Executable";
		public static int TYPE_ID = 0x3dc0de00;
		
		private String str;
		
		public CxiMainExeDef(){
			str = DEFO_ENG_DESC;
		}

		public Collection<String> getExtensions() {
			List<String> slist = new LinkedList<String>();
			//slist.add("bin");
			return slist;
		}

		public String getDescription() {return str;}
		public FileClass getFileClass() {return FileClass.EXECUTABLE;}
		public int getTypeID() {return TYPE_ID;}
		public void setDescriptionString(String s) {str = s;}
		
		public String getDefaultExtension(){return "";}
		
		public boolean canDisassemble(){return false;}
		public Collection<BinInst> disassemble(){return null;}
		
	}

}
