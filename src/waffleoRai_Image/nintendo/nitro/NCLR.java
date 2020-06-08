package waffleoRai_Image.nintendo.nitro;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.imageio.ImageIO;

import waffleoRai_Containers.nintendo.NDKDSFile;
import waffleoRai_Files.Converter;
import waffleoRai_Image.Palette;
import waffleoRai_Image.PaletteFileDef;
import waffleoRai_Image.nintendo.NDSGraphics;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileNode;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;

public class NCLR extends NDKDSFile{
	
	/*----- Constants -----*/
	
	public static final int TYPE_ID = 0x4e434c52;
	public static final String DEFO_ENG_STR = "Nitro Color Resource";
	
	public static final String MAGIC = "RLCN";
	
	public static final String MAGIC_TTLP = "TTLP";
	public static final String MAGIC_PMCP = "PMCP";
	
	/*----- Instance Variables -----*/
	
	private Palette[] pal_arr;
	private List<short[]> rawColors;
	
	/*----- Construction -----*/
	
	private NCLR(){super(null);}
	
	private NCLR(FileBuffer src){super(src);}
	
	public static NCLR wrapPalette(Palette p){
		NCLR nclr = newNCLR(1);
		nclr.setPalette(0, p);
		return nclr;
	}
	
	/*----- Parse -----*/
	
	public static NCLR readNCLR(FileBuffer data){
		NCLR me = new NCLR(data);
		
		//Read PCMP
		FileBuffer pmcp = me.getSectionData(MAGIC_PMCP);
		int pcount = 1;
		if(pmcp != null){
			pmcp.skipBytes(8);
			pcount = pmcp.nextShort(); pmcp.skipBytes(6);
			int[] ids = new int[pcount];
			for(int i = 0; i < pcount; i++)ids[i] = pmcp.nextShort();	
		}
		
		//Read Palette Data
		FileBuffer ttlp = me.getSectionData(MAGIC_TTLP);
		ttlp.skipBytes(8);
		int bid = ttlp.nextInt();
		boolean bit8 = false;
		if(bid == 3) bit8 = false;
		else if (bid == 4) bit8 = true;
		ttlp.skipBytes(4);
		int datsz = ttlp.nextInt();
		if((0x200 - datsz) > 0) datsz = 0x200-datsz;
		long cpos = ttlp.getCurrentPosition() + 4;
		
		me.pal_arr = new Palette[pcount];
		//me.pal_map = new HashMap<Integer, Palette>();
		me.rawColors = new ArrayList<short[]>(pcount + 1);
		for(int i = 0; i < pcount; i++){
			me.pal_arr[i] = NDSGraphics.readPalette(data, cpos, bit8);
			//Now copy the raw values...
				if(bit8){
					short[] raws = new short[256];
					for(int j = 0; j < 256; j++){
						raws[j] = data.shortFromFile(cpos);
						cpos+=2;
					}
					me.rawColors.add(raws);
				}
				else{
					short[] raws = new short[16];
					for(int j = 0; j < 16; j++){
						raws[j] = data.shortFromFile(cpos);
						cpos+=2;
					}
					me.rawColors.add(raws);
				}
			//
			//if(bit8) cpos += 0x200;
			//else cpos += 0x20;
		}
		
		return me;
	}
	
	public static NCLR newNCLR(int plt_slots){
		
		if(plt_slots <= 0) return null;
		
		NCLR nclr = new NCLR();
		nclr.pal_arr = new Palette[plt_slots];
		
		return nclr;
	}
	
	/*----- Getters -----*/
	
	public Palette getPalette(int idx){
		if(idx < 0 || pal_arr == null || idx >= pal_arr.length) return null;
		return pal_arr[idx];
	}

	public int getPaletteCount(){
		return pal_arr.length;
	}
	
	public short[] getRawColors(int plt_idx){
		if(rawColors == null) return null;
		return rawColors.get(plt_idx);
	}
	
	public List<short[]> getRawColors(){
		if(rawColors == null) return null;
		List<short[]> copy = new ArrayList<short[]>(rawColors.size()+1);
		copy.addAll(rawColors);
		return copy;
	}
	
	public boolean is8Bit(){
		for(Palette p : pal_arr){
			if(p.getBitDepth() != 8) return false;
		}
		return true;
	}
	
	/*----- Setters -----*/
	
	public void setPalette(int idx, Palette plt){
		if(idx < 0) return;
		if(idx >= pal_arr.length) return;
		
		pal_arr[idx] = plt;
	}
	
	/*----- Metadata -----*/
	
	/*----- Definition -----*/
	
	private static TypeDef static_def;
	private static StdConverter static_conv;
	
	public static class TypeDef extends PaletteFileDef
	{
		
		public static final String[] EXT_LIST = {"nclr"};
		
		private String str;
		
		public TypeDef(){
			str = DEFO_ENG_STR;
		}

		@Override
		public Collection<String> getExtensions() {
			List<String> extlist = new ArrayList<String>(EXT_LIST.length);
			for(String s : EXT_LIST)extlist.add(s);
			return extlist;
		}

		@Override
		public String getDescription() {return str;}

		@Override
		public int getTypeID() {return TYPE_ID;}

		@Override
		public void setDescriptionString(String s) {str = s;}
		
		public String getDefaultExtension() {return "nclr";}

		public Palette getPalette(FileNode src, int pidx) {
			
			try{
				NCLR nclr = NCLR.readNCLR(src.loadDecompressedData());
				return nclr.getPalette(pidx);
			}
			catch(Exception e){
				e.printStackTrace();
				return null;
			}
		}
		
	}
	
	public static TypeDef getTypeDef()
	{
		if(static_def != null) return static_def;
		static_def = new TypeDef();
		return static_def;
	}
	
	public static class StdConverter implements Converter
	{
		
		private String from_str;
		private String to_str;
		
		public StdConverter(){
			from_str = DEFO_ENG_STR;
			to_str = "Portable Network Graphics Image (.png)";
		}

		public String getFromFormatDescription() {return from_str;}
		public String getToFormatDescription() {return to_str;}
		public void setFromFormatDescription(String s) {from_str = s;}
		public void setToFormatDescription(String s) {to_str = s;}

		public void writeAsTargetFormat(String inpath, String outpath) throws IOException, UnsupportedFileTypeException {
			writeAsTargetFormat(FileBuffer.createBuffer(inpath), outpath);
		}
		
		public void writeAsTargetFormat(FileBuffer input, String outpath) throws IOException, UnsupportedFileTypeException{
			if(!FileBuffer.directoryExists(outpath)){
				Files.createDirectories(Paths.get(outpath));
			}
			
			NCLR nclr = NCLR.readNCLR(input);
			
			for(int i = 0; i < nclr.pal_arr.length; i++){
				String path = outpath + File.separator + String.format("nclr_%03d", i);
				ImageIO.write(nclr.pal_arr[i].renderVisual(), "png", new File(path));
			}
			
		}
		
		public void writeAsTargetFormat(FileNode node, String outpath) 
				throws IOException, UnsupportedFileTypeException{
			writeAsTargetFormat(node.loadDecompressedData(), outpath);
		}
		
		public String changeExtension(String path){
			/*int lastdot = path.lastIndexOf('.');
			if(lastdot >= 0) return path.substring(0, lastdot) + ".png";
			return path + ".png";*/
			return path;
		}
		
	}
	
	public static StdConverter getConverter()
	{
		if(static_conv == null) static_conv = new StdConverter();
		return static_conv;
	}
	
	/*----- Debug -----*/
	
	public void printToStderr(){

		System.err.println("===== NCLR =====");
		System.err.println("Palette Count: " + pal_arr.length);
		
		for(int i = 0; i < pal_arr.length; i++){
			System.err.println("-> Palette " + i);
			Palette pal = pal_arr[i];
			pal.printMe();
			System.err.println();
		}
		
		
	}
	
}
