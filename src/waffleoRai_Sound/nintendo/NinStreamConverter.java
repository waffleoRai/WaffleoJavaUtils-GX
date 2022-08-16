package waffleoRai_Sound.nintendo;

import java.io.IOException;

import waffleoRai_Files.Converter;
import waffleoRai_Files.tree.FileNode;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;

public abstract class NinStreamConverter implements Converter{

	public static final String DEFO_ENG_TO_DESC = "System Directory/Uncompressed Wave Audio File (.wav)";
	
	private String from_desc;
	private String to_desc;
	
	private String stm_name;
	
	protected NinStreamConverter(String src_desc){
		from_desc = src_desc;
		to_desc = DEFO_ENG_TO_DESC;
	}
	
	protected abstract NinStream readMe(FileBuffer data);
	
	public String getFromFormatDescription() {return from_desc;}
	public String getToFormatDescription() {return to_desc;}
	public void setFromFormatDescription(String s) {from_desc = s;}
	public void setToFormatDescription(String s) {to_desc = s;}
	public void resetStreamName(){stm_name = null;}
	
	public void writeAsTargetFormat(String inpath, String outpath)
			throws IOException, UnsupportedFileTypeException {
		writeAsTargetFormat(FileBuffer.createBuffer(inpath), outpath);
	}
	
	public void writeAsTargetFormat(FileNode node, String outpath) 
			throws IOException, UnsupportedFileTypeException{
		stm_name = removeExtensions(node.getFileName());
		FileBuffer dat = node.loadDecompressedData();
		writeAsTargetFormat(dat, outpath);
	}
	
	public void writeAsTargetFormat(FileBuffer input, String outpath)
			throws IOException, UnsupportedFileTypeException {
		NinStream stream = readMe(input);
		stream.dumpAllToWAV(outpath, stm_name);
	}
	
	public static String removeExtensions(String path){
		if(path == null) return null;
		if(path.isEmpty()) return path;
		
		int lastdot = path.lastIndexOf('.');
		path = path.substring(0, lastdot);
		if(path.endsWith(".dspadpcm")){
			//Do it again.
			lastdot = path.lastIndexOf('.');
			path = path.substring(0, lastdot);
		}
		return path;
	}
	
	public String changeExtension(String path) {
		if(path == null) return null;
		if(path.isEmpty()) return path;
		
		int lastdot = path.lastIndexOf('.');
		if(lastdot < 0) return path + ".wav";
		return path.substring(0, lastdot) + ".wav";
	}
	
}
