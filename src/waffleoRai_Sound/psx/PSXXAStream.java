package waffleoRai_Sound.psx;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import waffleoRai_Files.FileClass;
import waffleoRai_Files.FileTypeDefinition;
import waffleoRai_Files.psx.XAStreamFile;
import waffleoRai_Files.tree.FileNode;
import waffleoRai_Sound.Sound;
import waffleoRai_Sound.SoundFileDefinition;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;

public class PSXXAStream {
	
	/*----- Constants -----*/
	
	public static final int SEC_SIZE = 2352; //Full sector size (for skipping)
	public static final byte[] SYNC_PATTERN = {      0x00, (byte)0xFF, (byte)0xFF, (byte)0xFF,
											   (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF,
											   (byte)0xFF, (byte)0xFF, (byte)0xFF,       0x00};
	
	public static final int STYPE_VIDEO = 1;
	public static final int STYPE_AUDIO = 2;
	public static final int STYPE_DATA = 3;
	
	public static final int MAX_CHANNELS = 32;
	
	/*----- Instance Variables -----*/
	
	private ArrayList<XAStreamFile> files;
	
	/*----- Construction/Parsing -----*/
	
	public PSXXAStream(){
		this(16);
	}
	
	public PSXXAStream(int alloc){
		files = new ArrayList<XAStreamFile>(alloc);
	}
	
	public static PSXXAStream readStream(FileNode src) throws IOException, UnsupportedFileTypeException{

		FileBuffer data = src.loadData();
		
		//Check for sync pattern - will reject if not found
		if(data == null) throw new IOException("Data buffer provided is null!");
		long scheck = data.findString(0, 0x10, SYNC_PATTERN);
		if(scheck != 0) throw new UnsupportedFileTypeException("ISO Sync Pattern must match up to buffer beginning!");
		
		//Some tools will add a RIFF header to the extracted image. Make sure this is trimmed before feeding to this function!
		
		//Nab file locations
		long cpos = 0;
		long len = data.getFileSize();
		int scount = (int)(len/SEC_SIZE);
		int s = 0;
		LinkedList<XAStreamFile> flist = new LinkedList<XAStreamFile>();
		int last_st = 0;
		do{
			//Get flag byte
			int flags = Byte.toUnsignedInt(data.getByte(cpos + 0x12));
			if((flags & 0x81) != 0){
				//EOF set. Generate new file
				XAStreamFile f = new XAStreamFile(last_st, s);
				flist.add(f);
				last_st = s;
			}
			
			cpos += SEC_SIZE;
		}while(++s < scount);
		
		//Load into PSXXAStream object
		PSXXAStream str = new PSXXAStream(flist.size());
		for(XAStreamFile f : flist){
			f.scanStartPoints(data);
			f.setSource(src);
			str.files.add(f);
		}
		
		data.dispose();
		return str;
	}
	
	/*----- Getters -----*/
	
	public int countFiles(){return files.size();}
	
	public XAStreamFile getFile(int idx){return files.get(idx);}
	
	/*----- Setters -----*/
	
	/*----- Debug -----*/
		
	/*--- Definition ---*/
	
	public static final int DEF_ID_A = 0x11584161;
	private static final String DEFO_ENG_STR_A = "eXtended Architecture Audio Stream";
	
	public static final int DEF_ID_V = 0x11584162;
	private static final String DEFO_ENG_STR_V = "eXtended Architecture Video Stream";
	
	public static final int DEF_ID_AV = 0x11584163;
	private static final String DEFO_ENG_STR_AV = "eXtended Architecture Multimedia Stream";
	
	private static PSXXAAudioDef stat_def;
	private static PSXXAVideoDef stat_def_v;
	private static PSXXAMultimediaDef stat_def_av;
	
	public static class PSXXAAudioDef extends SoundFileDefinition{
		
		private String desc = DEFO_ENG_STR_A;
		
		public Collection<String> getExtensions() {
			List<String> list = new ArrayList<String>(2);
			list.add("str");
			list.add("xa");
			return list;
		}

		public String getDescription() {return desc;}
		public FileClass getFileClass() {return FileClass.SOUND_STREAM;}

		public int getTypeID() {return DEF_ID_A;}
		public void setDescriptionString(String s) {desc = s;}
		public String getDefaultExtension() {return "str";}

		public Sound readSound(FileNode file) {
			//TODO
			return null;
		}
		
	}
	
	public static class PSXXAVideoDef implements FileTypeDefinition{
		
		private String desc = DEFO_ENG_STR_V;
		
		public Collection<String> getExtensions() {
			List<String> list = new ArrayList<String>(2);
			list.add("str");
			list.add("xa");
			return list;
		}
		
		public String getDescription() {return desc;}
		public FileClass getFileClass() {return FileClass.MOV_VIDEO;}

		public int getTypeID() {return DEF_ID_V;}
		public void setDescriptionString(String s) {desc = s;}
		public String getDefaultExtension() {return "str";}
		
		public String toString(){return FileTypeDefinition.stringMe(this);}
		
	}
	
	public static class PSXXAMultimediaDef implements FileTypeDefinition{
		
		private String desc = DEFO_ENG_STR_AV;
		
		public Collection<String> getExtensions() {
			List<String> list = new ArrayList<String>(2);
			list.add("str");
			list.add("xa");
			return list;
		}
		
		public String getDescription() {return desc;}
		public FileClass getFileClass() {return FileClass.MOV_MOVIE;}

		public int getTypeID() {return DEF_ID_AV;}
		public void setDescriptionString(String s) {desc = s;}
		public String getDefaultExtension() {return "str";}
		
		public String toString(){return FileTypeDefinition.stringMe(this);}
		
	}
	
	public static PSXXAAudioDef getAudioDefinition(){
		if(stat_def == null) stat_def = new PSXXAAudioDef();
		return stat_def;
	}
	
	public static PSXXAVideoDef getVideoDefinition(){
		if(stat_def_v == null) stat_def_v = new PSXXAVideoDef();
		return stat_def_v;
	}
	
	public static PSXXAMultimediaDef getMultimediaDefinition(){
		if(stat_def_av == null) stat_def_av = new PSXXAMultimediaDef();
		return stat_def_av;
	}

}
