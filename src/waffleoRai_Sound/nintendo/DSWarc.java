package waffleoRai_Sound.nintendo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import waffleoRai_Containers.nintendo.NDKDSFile;
import waffleoRai_Files.Converter;
import waffleoRai_Sound.Sound;
import waffleoRai_Sound.WaveArcDef;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileNode;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;

public class DSWarc extends WaveArchive{
	
	public static final int TYPE_ID = 0x4e574152;
	public static final String MAGIC = "SWAR";
	
	private long[] ptr_tbl;
	
	public static DSWarc readSWAR(FileBuffer file, long pos) throws UnsupportedFileTypeException{

		if(pos != 0) file = file.createReadOnlyCopy(pos, file.getFileSize());
		//Check for magic number
		long cpos = file.findString(0, 0x10, MAGIC);
		if(cpos != 0) throw new FileBuffer.UnsupportedFileTypeException("DSWarc.readSWAR || Magic number \"SWAR\" not found!");
		
		//Break into sections
		NDKDSFile dsfile = NDKDSFile.readDSFile(file);
		FileBuffer data = dsfile.getSectionData("DATA");
		if(data == null) throw new FileBuffer.UnsupportedFileTypeException("DSWarc.readSWAR || DATA section could not be found!");

		//Instantiate archive
		DSWarc swar = new DSWarc();
		
		//Read DATA
		data.setCurrentPosition(8 + (4 * 8)); //Skips section header & reserved
		int wavcount = data.nextInt();
		swar.sounds = new ArrayList<NinWave>(wavcount);
		
		//Read pointer table (offsets are relative to start of SWAR)
		swar.ptr_tbl = new long[wavcount+1];
		for(int i = 0; i < wavcount; i++) swar.ptr_tbl[i] = Integer.toUnsignedLong(data.nextInt());
		
		//Read SWAVs
		long len = dsfile.getFileLength();
		swar.ptr_tbl[wavcount] = len;
		for(int i = 0; i < wavcount; i++){
			
			long stoff = swar.ptr_tbl[i];
			long edoff = len;
			if(i < swar.ptr_tbl.length-1) edoff = swar.ptr_tbl[i+1];
			//System.err.println("Reading wav " + i + " (0x" + Long.toHexString(stoff) + " - 0x" + Long.toHexString(edoff) + ")");
			
			FileBuffer wavfile = file.createReadOnlyCopy(stoff, edoff);
			DSWave wav = DSWave.readInternalSWAV(wavfile, 0);
			swar.addSound(wav);
		}
		
		return swar;
	}

	public long getStartOffsetOfSWAV(int index){
		return ptr_tbl[index];
	}
	
	public long getEndOffsetOfSWAV(int index){
		return ptr_tbl[index+1];
	}
	
	/*--- Definition ---*/
	
	private static NitroWarcDef static_def;
	
	public static NitroWarcDef getDefinition(){
		if(static_def == null) static_def = new NitroWarcDef();
		return static_def;
	}
	
	public static class NitroWarcDef extends WaveArcDef{

		private static final String DEFO_ENG_STR = "Nitro Soundwave Archive";
		private static final String[] EXT_LIST = {"swar", "SWAR", "nwar", "bnwar"};
		
		private String str;
		
		public NitroWarcDef(){
			str = DEFO_ENG_STR;
		}
		
		public Collection<String> getExtensions() {
			List<String> list = new ArrayList<String>(EXT_LIST.length);
			for(String s : EXT_LIST)list.add(s);
			return list;
		}

		public String getDescription() {return str;}
		public int getTypeID() {return TYPE_ID;}
		public void setDescriptionString(String s) {str = s;}
		public String getDefaultExtension() {return "swar";}

		public List<Sound> getContents(FileNode file) {
			try 
			{
				FileBuffer dat = file.loadDecompressedData();
				DSWarc arc = readSWAR(dat, 0);
				List<Sound> list = new ArrayList<Sound>(arc.countSounds()+1);
				for(NinWave snd : arc.sounds) list.add(snd);
				return list;
			} 
			catch (IOException e) {
				e.printStackTrace();
				return null;
			} 
			catch (UnsupportedFileTypeException e) {
				e.printStackTrace();
				return null;
			}
		}

		
		
	}
	
	/*--- Converter ---*/
	
	private static SWARConverter cdef;
	
	public static SWARConverter getDefaultConverter(){
		if(cdef == null) cdef = new SWARConverter();
		return cdef;
	}
	
	public static class SWARConverter implements Converter{

		public static final String DEFO_ENG_FROM = "Nitro Wave Archive (.swar)";
		public static final String DEFO_ENG_TO = "File System Directory";
		
		private String from_desc;
		private String to_desc;
		
		public SWARConverter(){
			from_desc = DEFO_ENG_FROM;
			to_desc = DEFO_ENG_TO;
		}
		
		public String getFromFormatDescription() {return from_desc;}
		public String getToFormatDescription() {return to_desc;}
		public void setFromFormatDescription(String s) {from_desc = s;}
		public void setToFormatDescription(String s) {to_desc = s;}

		public void writeAsTargetFormat(String inpath, String outpath)
				throws IOException, UnsupportedFileTypeException {
			writeAsTargetFormat(FileBuffer.createBuffer(inpath), outpath);
		}

		public void writeAsTargetFormat(FileBuffer input, String outpath)
				throws IOException, UnsupportedFileTypeException {

			if(!FileBuffer.directoryExists(outpath)){
				Files.createDirectories(Paths.get(outpath));
			}
			
			DSWarc warc = readSWAR(input, 0);
			int i = 0;
			for(NinWave wave : warc.sounds){
				wave.writeWAV(outpath + File.separator + "swav_" + String.format("%03d", i) + ".wav");
				i++;
			}
			
		}
		
		public void writeAsTargetFormat(FileNode node, String outpath) 
				throws IOException, UnsupportedFileTypeException{
			FileBuffer dat = node.loadDecompressedData();
			writeAsTargetFormat(dat, outpath);
		}

		public String changeExtension(String path) {
			if(path == null) return null;
			if(path.isEmpty()) return path;
			
			int lastdot = path.lastIndexOf('.');
			if(lastdot < 0) return path;
			return path.substring(0, lastdot);
		}
		
	}
	
	
}
