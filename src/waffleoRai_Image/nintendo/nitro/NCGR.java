package waffleoRai_Image.nintendo.nitro;

import java.awt.image.BufferedImage;
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
import waffleoRai_Files.FileClass;
import waffleoRai_Files.FileTypeNode;
import waffleoRai_Files.MetaResLinks;
import waffleoRai_Files.NodeMatchCallback;
import waffleoRai_Image.Palette;
import waffleoRai_Image.PaletteFileDef;
import waffleoRai_Image.Tile;
import waffleoRai_Image.Tileset;
import waffleoRai_Image.TilesetDef;
import waffleoRai_Image.nintendo.NDSGraphics;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;
import waffleoRai_Files.tree.FileNode;

public class NCGR extends NDKDSFile{
	
	/*----- Constants -----*/
	
	public static final int TYPE_ID = 0x4e434752;
	public static final String DEFO_ENG_STR = "Nitro Character Graphics Resource";
	
	public static final String MAGIC = "RGCN";
	
	public static final String MAGIC_RAHC = "RAHC";
	public static final String MAGIC_SOPC = "SOPC";
	
	/*----- Instance Variables -----*/
	
	private Tileset tiles;
	
	/*----- Construction -----*/
	
	private NCGR(){super(null);}
	
	private NCGR(FileBuffer src){super(src);}
	
	/*----- Parse -----*/
	
	public static NCGR readNCGR(FileBuffer data){
		NCGR me = new NCGR(data);
		
		//Read RAHC
		FileBuffer rahc = me.getSectionData(MAGIC_RAHC);
		rahc.setCurrentPosition(0x8);
		int w = rahc.nextShort();
		int h = rahc.nextShort();
		int bd = rahc.nextInt();
		
		boolean bit8 = false;
		if(bd == 4) bit8 = true;

		int pcount = rahc.intFromFile(0x18);
		if(!bit8)pcount = pcount << 1;
		rahc.setCurrentPosition(0x20);
		//System.err.println("Pixel Count: 0x" + Integer.toHexString(pcount));

		int r = 0; int l = 0;
		int dim = 8;
		int tcount = pcount >>> 6;
		//System.err.println("Tile Size: " + dim);
		//System.err.println("Tile Count: " + tcount);
		
		int bits = 8;
		if(!bit8) bits = 4;
		me.tiles = new Tileset(bits, dim, tcount);
		me.tiles.setDimensionInTiles(w, h);

		int t = 0;
		Tile tile = null;
		if(bit8){
			for(int i = 0; i < pcount; i++){
				
				if(tile == null){
					tile = me.tiles.getTile(t++);
				}
				
				int val = Byte.toUnsignedInt(rahc.nextByte());
				tile.setValueAt(l++, r, val);
				
				if(l >= dim){
					l = 0; r++;
					if(r >= dim){
						r = 0;
						tile = null;
					}
				}
			}
		}
		else{
			for(int i = 0; i < pcount; i+=2){
				
				if(tile == null){
					tile = me.tiles.getTile(t++);
				}
				
				int val = Byte.toUnsignedInt(rahc.nextByte());
				int p0 = val & 0xF;
				int p1 = (val >>> 4) & 0xF;
				tile.setValueAt(l++, r, p0);
				tile.setValueAt(l++, r, p1);
				
				if(l >= dim){
					l = 0; r++;
					if(r >= dim){
						r = 0;
						tile = null;
					}
				}
			}
		}
		
		return me;
		
	}
		
	/*----- Getters -----*/
	
	public Tileset getTileset(){
		return tiles;
	}
	
	public int getTileCount(){
		return tiles.getTileCount();
	}
	
	/*----- Setters -----*/
	
	public void setPalette(Palette p){
		tiles.setLinkedPalette(p);
	}
	
	/*----- Metadata -----*/
	
	public static boolean linkPalette(FileNode ts_node, FileNode plt_node, int idx){
		if(!MetaResLinks.linkResource(ts_node, plt_node, 
				NDSGraphics.METAKEY_PLTLINK, NDSGraphics.METAKEY_PALETTEID)) return false;
		ts_node.setMetadataValue(NDSGraphics.METAKEY_PLTIDX, Integer.toString(idx));
		return true;
	}
	
	public static Palette loadLinkedPalette(FileNode ts_node){
		FileNode plt_node = MetaResLinks.findLinkedResource(ts_node, NDSGraphics.METAKEY_PLTLINK, NDSGraphics.METAKEY_PALETTEID);
		if(plt_node == null){
			//Look for match
			NodeMatchCallback filter = new NodeMatchCallback(){

				public boolean meetsCondition(FileNode n) {
					if(n == null) return false;
					FileTypeNode tail = n.getTypeChainTail();
					if(tail == null) return false;
					return (tail.getTypeDefinition() instanceof PaletteFileDef);
				}
				
			};
			List<NodeMatchCallback> filters = new ArrayList<NodeMatchCallback>(1);
			filters.add(filter);
			
			List<FileNode> candidates = MetaResLinks.findMatchCandidates(ts_node, filters);
			if(candidates == null || candidates.isEmpty()) return null;
			plt_node = candidates.get(0);
		}
		
		//Parse index
		String istr = ts_node.getMetadataValue(NDSGraphics.METAKEY_PLTIDX);
		int idx = 0;
		try{idx = Integer.parseInt(istr);}
		catch(NumberFormatException x){
			ts_node.setMetadataValue(NDSGraphics.METAKEY_PLTIDX, "0");
		}
		
		//Load palette data
		if(plt_node == null) return null;
		FileTypeNode tail = plt_node.getTypeChainTail();
		if(tail == null) return null;
		if(tail.getTypeDefinition() instanceof PaletteFileDef){
			PaletteFileDef pdef = (PaletteFileDef)tail.getTypeDefinition();
			Palette[] pals = pdef.getPalette(plt_node);
			if(pals.length < idx) return pals[idx];
			else{
				ts_node.setMetadataValue(NDSGraphics.METAKEY_PLTIDX, "0");
				return pals[0];
			}
		}
		
		return null;
	}
	
	/*----- Definition -----*/

	private static TypeDef static_def;
	private static StdConverter static_conv;
	
	public static class TypeDef extends TilesetDef
	{
		
		public static final String[] EXT_LIST = {"ncgr"};
		
		private String str;
		
		public TypeDef(){
			str = DEFO_ENG_STR;
		}

		public Collection<String> getExtensions() {
			List<String> extlist = new ArrayList<String>(EXT_LIST.length);
			for(String s : EXT_LIST)extlist.add(s);
			return extlist;
		}

		public String getDescription() {return str;}

		public int getTypeID() {return TYPE_ID;}

		public void setDescriptionString(String s) {str = s;}
		
		public String getDefaultExtension() {return "ncgr";}

		public BufferedImage renderData(FileNode src){
			
			try{
				NCGR ncgr = NCGR.readNCGR(src.loadDecompressedData());
				return ncgr.tiles.renderImageData();
			}
			catch(Exception e){
				e.printStackTrace();
			}
			
			return null;
		}
		
		public BufferedImage renderWithPalette(FileNode src, Palette plt){
			try{
				NCGR ncgr = NCGR.readNCGR(src.loadDecompressedData());
				ncgr.setPalette(plt);
				return ncgr.tiles.renderImage();
			}
			catch(Exception e){
				e.printStackTrace();
			}
			
			return null;
		}

		public FileClass getFileClass() {
			//return FileClass.IMG_TILE;
			return FileClass.IMG_TILE;
		}

		
		public Tileset getTileset(FileNode src) {

			try {
				NCGR ncgr = NCGR.readNCGR(src.loadDecompressedData());
				return ncgr.getTileset();
			} 
			catch (IOException e) {
				e.printStackTrace();
				return null;
			}

		}

		
		public int countTiles(FileNode src) {

			FileBuffer data;
			try {
				data = src.loadDecompressedData();
				
				//Look for RAHC
				long cpos = data.findString(0, 0x1000, MAGIC_RAHC);
				if(cpos < 0) return 0;
				int w = Short.toUnsignedInt(data.shortFromFile(cpos+8));
				int h = Short.toUnsignedInt(data.shortFromFile(cpos+10));
				
				return w*h;
			} 
			catch (IOException e) {
				e.printStackTrace();
				return 0;
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
			
			NCGR ncgr = NCGR.readNCGR(input);
			int tcount = ncgr.getTileCount();
			for(int i = 0; i < tcount; i++){
				String opath = outpath + File.separator + "tile" + String.format("%05d", i) + ".png";
				BufferedImage img = ncgr.tiles.renderTile(i);
				ImageIO.write(img, "png", new File(opath));
			}

		}
		
		public void writeAsTargetFormat(FileNode node, String outpath) 
				throws IOException, UnsupportedFileTypeException{
			
			NCGR ncgr = NCGR.readNCGR(node.loadDecompressedData());
			//Find linked palette
			//ncgr.linkPalette(NCGR.loadLinkedPalette(node));
			int tcount = ncgr.getTileCount();
			for(int i = 0; i < tcount; i++){
				String opath = outpath + File.separator + "tile" + String.format("%05d", i) + ".png";
				BufferedImage img = ncgr.tiles.renderTile(i);
				ImageIO.write(img, "png", new File(opath));
			}
			
		}
		
		public String changeExtension(String path){
			int lastdot = path.lastIndexOf('.');
			if(lastdot >= 0) return path.substring(0, lastdot) + ".png";
			return path + ".png";
		}
		
	}
	
	public static StdConverter getConverter()
	{
		if(static_conv == null) static_conv = new StdConverter();
		return static_conv;
	}
	
	
}
