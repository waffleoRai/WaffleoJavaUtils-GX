package waffleoRai_Image.nintendo.nitro;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.imageio.ImageIO;

import waffleoRai_Containers.nintendo.NDKDSFile;
import waffleoRai_Files.Converter;
import waffleoRai_Files.FileClass;
import waffleoRai_Files.FileTypeNode;
import waffleoRai_Files.MetaResLinks;
import waffleoRai_Files.MetaResLinks.WeightFactor;
import waffleoRai_Files.NodeMatchCallback;
import waffleoRai_Image.Palette;
import waffleoRai_Image.PaletteFileDef;
import waffleoRai_Image.PalettedImageDef;
import waffleoRai_Image.Tile;
import waffleoRai_Image.Tileset;
import waffleoRai_Image.TilesetDef;
import waffleoRai_Image.nintendo.NDSGraphics;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;
import waffleoRai_Files.tree.FileNode;

public class NSCR extends NDKDSFile{
	
	/*----- Constants -----*/
	
	public static final int TYPE_ID = 0x4e534352;
	public static final String DEFO_ENG_STR = "Nitro Screen Resource";
	
	public static final String MAGIC = "RCSN";
	
	public static final String MAGIC_NRCS = "NRCS";
	
	/*----- Instance Variables -----*/
	
	private int width;
	private int height;
	
	private TileInfo[] tiles;
	
	/*----- Construction -----*/
	
	private NSCR(){super(null);}
	
	private NSCR(FileBuffer src){super(src);}
	
	/*----- Structs -----*/
	
	public static class TileInfo{
		
		public int plt_id;
		public int tile_id;
		public boolean flipx;
		public boolean flipy;
		
	}
	
	/*----- Parse -----*/
	
	public static NSCR readNSCR(FileBuffer data){
		NSCR ncsr = new NSCR(data);
		
		FileBuffer nrcs = ncsr.getSectionData(MAGIC_NRCS);
		
		ncsr.width = nrcs.shortFromFile(0x08);
		ncsr.height = nrcs.shortFromFile(0x0A);
		int datsize = nrcs.intFromFile(0x10);
		nrcs.setCurrentPosition(0x14);
		int tcount = datsize >>> 1;
		
		ncsr.tiles = new TileInfo[tcount];
		for(int i = 0; i < tcount; i++){
			int tdat = Short.toUnsignedInt(nrcs.nextShort());
			TileInfo tinf = new TileInfo();
			ncsr.tiles[i] = tinf;
			
			tinf.plt_id = (tdat >>> 12) & 0xF;
			tinf.tile_id = tdat & 0x3FF;
			tinf.flipy = (tdat & 0x0800) != 0;
			tinf.flipx = (tdat & 0x0400) != 0;
		}
		
		return ncsr;
	}
	
	/*----- Getters -----*/
	
	public int getWidth(){return width;}
	public int getHeight(){return height;}
	
	public BufferedImage renderImage(Palette[] plt_dat, Tileset tle_dat){
		
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Tile tile = null;
		Palette defoplt = null;
		if(tle_dat.is4Bit()) defoplt = Palette.get4BitGreyscalePalette();
		else defoplt = Palette.get8BitGreyscalePalette();
		
		int tdim = tle_dat.getTileDimension();
		//int tw = width/tdim;
		//int th = height/tdim;
		
		int t = 0;
		int x = 0; int y = 0;
		while(y < height){
			
			TileInfo tinf = tiles[t++];
			tile = tle_dat.getTile(tinf.tile_id);
			if(plt_dat == null || tinf.plt_id < 0 || tinf.plt_id > plt_dat.length || plt_dat[tinf.plt_id] == null){
				tile.copyTo(img, x, y, tinf.flipx, tinf.flipy, defoplt);
			}
			else tile.copyTo(img, x, y, tinf.flipx, tinf.flipy, plt_dat[tinf.plt_id]);
			
			x+=tdim;
			if(x >= width){
				x = 0; y += tdim;
			}
			
		}
		
		return img;
	}
	
	public BufferedImage renderImage(Tileset tle_dat){
		return renderImage(null, tle_dat);
	}
	
	/*----- Setters -----*/
	
	/*----- Resource Matching -----*/
	
	public int getMaxTileIndex(){
		int max = 0;
		for(int i = 0; i < tiles.length; i++){
			if(tiles[i] != null){
				if(tiles[i].tile_id > max) max = tiles[i].tile_id;
			}
		}
		return max;
	}
	
	public int getMaxPaletteIndex(){
		int max = 0;
		for(int i = 0; i < tiles.length; i++){
			if(tiles[i] != null){
				if(tiles[i].plt_id > max) max = tiles[i].plt_id;
			}
		}
		return max;
	}
	
	public static List<FileNode> findPaletteCandidates(FileNode scr_node) throws IOException{
		
		NSCR nscr = NSCR.readNSCR(scr_node.loadDecompressedData());
		int pcount = nscr.getMaxPaletteIndex()+1;
		NodeMatchCallback filter = new NodeMatchCallback(){

			public boolean meetsCondition(FileNode n) {
				if(n == null) return false;
				FileTypeNode tail = n.getTypeChainTail();
				if(tail == null) return false;
				
				if(tail.getTypeDefinition() instanceof PaletteFileDef){
					//See if has enough palettes
					int ct = ((PaletteFileDef)tail.getTypeDefinition()).countPalettes(n);
					if(ct < pcount) return false;
					return true;
				}
				
				return false;
			}
			
		};
		List<NodeMatchCallback> filters = new ArrayList<NodeMatchCallback>(1);
		filters.add(filter);
		
		WeightFactor factor = new WeightFactor(){

			public int compareNodes(FileNode t1, FileNode t2) {
				
				int ct1 = 0; int ct2 = 0;
				FileTypeNode tail = t1.getTypeChainTail();
				if(tail != null && tail.getTypeDefinition() instanceof PaletteFileDef){
					ct1 = ((PaletteFileDef)tail.getTypeDefinition()).countPalettes(t1);
				}
				tail = t2.getTypeChainTail();
				if(tail != null && tail.getTypeDefinition() instanceof PaletteFileDef){
					ct2 = ((PaletteFileDef)tail.getTypeDefinition()).countPalettes(t2);
				}
				
				int diff1 = ct1 - pcount;
				int diff2 = ct2 - pcount;
				
				return diff1 - diff2;
			}
			
		};
		List<WeightFactor> factors = new ArrayList<WeightFactor>(1);
		factors.add(factor);
		
		List<FileNode> candidates = MetaResLinks.findMatchCandidates(scr_node, filters, factors);
		return candidates;
	}
	
	public static List<FileNode> findTilesetCandidates(FileNode scr_node) throws IOException{
		
		NSCR nscr = NSCR.readNSCR(scr_node.loadDecompressedData());
		int tcount = nscr.getMaxTileIndex()+1;
		NodeMatchCallback filter = new NodeMatchCallback(){

			public boolean meetsCondition(FileNode n) {
				if(n == null) return false;
				FileTypeNode tail = n.getTypeChainTail();
				if(tail == null) return false;
				
				if(tail.getTypeDefinition() instanceof TilesetDef){
					//See if has enough tiles
					int ct = ((TilesetDef)tail.getTypeDefinition()).countTiles(n);
					if(ct < tcount) return false;
					return true;
				}
				
				return false;
			}
			
		};
		List<NodeMatchCallback> filters = new ArrayList<NodeMatchCallback>(1);
		filters.add(filter);
		
		WeightFactor factor = new WeightFactor(){

			public int compareNodes(FileNode t1, FileNode t2) {
				
				int ct1 = 0; int ct2 = 0;
				FileTypeNode tail = t1.getTypeChainTail();
				if(tail != null && tail.getTypeDefinition() instanceof TilesetDef){
					ct1 = ((TilesetDef)tail.getTypeDefinition()).countTiles(t1);
				}
				tail = t2.getTypeChainTail();
				if(tail != null && tail.getTypeDefinition() instanceof TilesetDef){
					ct2 = ((TilesetDef)tail.getTypeDefinition()).countTiles(t2);
				}
				
				int diff1 = ct1 - tcount;
				int diff2 = ct2 - tcount;
				
				return diff1 - diff2;
			}
			
		};
		List<WeightFactor> factors = new ArrayList<WeightFactor>(1);
		factors.add(factor);
		
		List<FileNode> candidates = MetaResLinks.findMatchCandidates(scr_node, filters, factors);
		return candidates;
	}
	
	/*----- Metadata -----*/
	
	public static boolean linkTileset(FileNode scr_node, FileNode ts_node){
		return MetaResLinks.linkResource(scr_node, ts_node, 
				NDSGraphics.METAKEY_TLELINK, NDSGraphics.METAKEY_TLEUID);
	}
	
	public static Tileset loadLinkedTileset(FileNode scr_node) throws IOException{
		FileNode ts_node = MetaResLinks.findLinkedResource(scr_node, NDSGraphics.METAKEY_TLELINK, NDSGraphics.METAKEY_TLEUID);
		if(ts_node == null){
			//Look for match
			List<FileNode> candidates = findTilesetCandidates(scr_node);
			if(candidates == null || candidates.isEmpty()) return null;
			ts_node = candidates.get(0);
		}
		
		if(ts_node == null) return null;
		FileTypeNode tail = ts_node.getTypeChainTail();
		if(tail == null) return null;
		if(tail.getTypeDefinition() instanceof TilesetDef){
			return ((TilesetDef)tail.getTypeDefinition()).getTileset(ts_node);
		}
		
		return null;
	}
	
	public static boolean linkPalette(FileNode scr_node, FileNode plt_node){
		if(!MetaResLinks.linkResource(scr_node, plt_node, 
				NDSGraphics.METAKEY_PLTLINK, NDSGraphics.METAKEY_PALETTEID)) return false;
		return true;
	}
	
	public static Palette[] loadLinkedPalette(FileNode scr_node) throws IOException{
		FileNode plt_node = MetaResLinks.findLinkedResource(scr_node, NDSGraphics.METAKEY_PLTLINK, NDSGraphics.METAKEY_PALETTEID);
		if(plt_node == null){
			//Look for match
			
			List<FileNode> candidates = findPaletteCandidates(scr_node);
			if(candidates == null || candidates.isEmpty()) return null;
			plt_node = candidates.get(0);
		}
		
		//Load palette data
		if(plt_node == null) return null;
		FileTypeNode tail = plt_node.getTypeChainTail();
		if(tail == null) return null;
		if(tail.getTypeDefinition() instanceof PaletteFileDef){
			PaletteFileDef pdef = (PaletteFileDef)tail.getTypeDefinition();
			return pdef.getPalette(plt_node);
		}
		
		return null;
	}
	
	/*----- Definition -----*/
	
	private static TypeDef static_def;
	private static StdConverter static_conv;
	
	public static class TypeDef extends PalettedImageDef
	{
		
		public static final String[] EXT_LIST = {"nscr"};
		
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
		
		public String getDefaultExtension() {return "nscr";}

		public BufferedImage renderData(FileNode src){

			try{
				NSCR nscr = NSCR.readNSCR(src.loadDecompressedData());
				//NCLR nclr = NSCR.loadLinkedPalette(src);
				//NCGR ncgr = NSCR.loadLinkedTileset(src);
				Tileset ts = NSCR.loadLinkedTileset(src);
				Palette[] pals = NSCR.loadLinkedPalette(src);
				
				if(ts == null) return null;
				return nscr.renderImage(pals, ts);
			}
			catch(Exception x){
				x.printStackTrace();
			}
			
			return null;
		}
		
		public BufferedImage renderWithPalette(FileNode src, Palette plt){

			try{
				NSCR nscr = NSCR.readNSCR(src.loadDecompressedData());
				Tileset ts = NSCR.loadLinkedTileset(src);
				
				if(ts == null) return null;
				
				Palette[] pals = new Palette[nscr.getMaxPaletteIndex()+1];
				for(int i = 0; i < pals.length; i++) pals[i] = plt;
				return nscr.renderImage(pals, ts);
			}
			catch(Exception x){
				x.printStackTrace();
			}
			
			return null;
		}

		public FileClass getFileClass() {
			//return FileClass.IMG_TILE;
			return FileClass.IMG_TILEMAP;
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
			//Not much to do without the other data.
			//Does nothing for now.
			System.err.println("NSCR is a tile map format. Meaningless to render without tile data.");
		}
		
		public void writeAsTargetFormat(FileNode node, String outpath) 
				throws IOException, UnsupportedFileTypeException{
			
			NSCR nscr = NSCR.readNSCR(node.loadDecompressedData());
			Tileset ts = NSCR.loadLinkedTileset(node);
			Palette[] pals = NSCR.loadLinkedPalette(node);
				
			if(ts == null) throw new IOException("Linked tileset resource could not be found!");
			
			BufferedImage img = nscr.renderImage(pals, ts);
			ImageIO.write(img, "png", new File(outpath));
			
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
