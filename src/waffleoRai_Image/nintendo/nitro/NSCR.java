package waffleoRai_Image.nintendo.nitro;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import javax.imageio.ImageIO;

import waffleoRai_Containers.nintendo.NDKDSFile;
import waffleoRai_Files.Converter;
import waffleoRai_Files.FileClass;
import waffleoRai_Files.FileTypeNode;
import waffleoRai_Files.NodeMatchCallback;
import waffleoRai_Image.Palette;
import waffleoRai_Image.PalettedImageDef;
import waffleoRai_Image.nintendo.NDSGraphics;
import waffleoRai_Utils.DirectoryNode;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileNode;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;

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
	
	private class SearchRes implements Comparable<SearchRes>{
		public FileNode node;
		public int diff;
		public int str_weight;
		
		public SearchRes(FileNode fn, int c, int w){
			node = fn; diff = c; str_weight = w;
		}
		
		public int hashCode(){
			return node.hashCode();
		}
		
		public boolean equals(Object o){
			return this == o;
		}
		
		public int compareTo(SearchRes o) {
			if(o == null) return -1;
			
			//Check element difference...
			if(this.diff != o.diff) return this.diff - o.diff;
			
			return this.str_weight - o.str_weight;
		}
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
	
	public BufferedImage renderImage(NCLR plt_dat, NCGR tle_dat){
		
		if(plt_dat == null) return renderImage(tle_dat);
		
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		DSTile tile = null;
		Palette plt = null;
		
		int bigdim = tle_dat.getSupertileDimension(); //Tiles per side of big tile
		if(bigdim <= 0) bigdim = 1;
		int dim = tle_dat.getTileDimension();
		int t = 0;
		int tw = width/dim; //Width in tiles
		int th = height/dim; //Height in tiles
		int btw = tw/bigdim; //Width in big tiles
		int bth = th/bigdim; //Height in big tiles
		int x = 0; int y = 0;
		
		for(int tr = 0; tr < bth; tr++){ //In big tiles
			//System.err.println("Supertile Row: " + tr + " x,y = " + x + "," + y);
			int y0 = y;
			for(int tl = 0; tl < btw; tl++){
				//Do bigtile
				//System.err.println("Supertile Column: " + tl + " x,y = " + x + "," + y);
				int x0 = x;
				for(int r = 0; r < bigdim; r++){
					for(int l = 0; l < bigdim; l++){
						//Draw tile
						TileInfo info = tiles[t++];
						plt = plt_dat.getPalette(info.plt_id);
						tile = tle_dat.getTile(info.tile_id);
						
						if(plt != null) tile.copyTo(img, x, y, info.flipx, info.flipy, plt);
						else tile.copyTo(img, x, y, info.flipx, info.flipy);
						
						x += dim;
					}
					y += dim; x = x0;
				}
				x += dim * bigdim;
				y = y0;
			}
			y += dim * bigdim; x = 0;
		}
		
		return img;
	}
	
	public BufferedImage renderImage(NCGR tle_dat){
		
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		DSTile tile = null;
		
		int bigdim = tle_dat.getSupertileDimension(); //Tiles per side of big tile
		if(bigdim <= 0) bigdim = 1;
		int dim = tle_dat.getTileDimension();
		int t = 0;
		int tw = width/dim; //Width in tiles
		int th = height/dim; //Height in tiles
		int btw = tw/bigdim; //Width in big tiles
		int bth = th/bigdim; //Height in big tiles
		int x = 0; int y = 0;
		
		for(int tr = 0; tr < bth; tr++){ //In big tiles
			//System.err.println("Supertile Row: " + tr + " x,y = " + x + "," + y);
			int y0 = y;
			for(int tl = 0; tl < btw; tl++){
				//Do bigtile
				//System.err.println("Supertile Column: " + tl + " x,y = " + x + "," + y);
				int x0 = x;
				for(int r = 0; r < bigdim; r++){
					for(int l = 0; l < bigdim; l++){
						//Draw tile
						TileInfo info = tiles[t++];
						tile = tle_dat.getTile(info.tile_id);
						tile.copyTo(img, x, y, info.flipx, info.flipy);
						
						x += dim;
					}
					y += dim; x = x0;
				}
				x += dim * bigdim;
				y = y0;
			}
			y += dim * bigdim; x = 0;
		}
		
		return img;
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
	
	public List<FileNode> searchForTilesets(FileNode nscr_node) throws IOException{
		//Searches the directory for candidate tile sets
		//Files nearer to the NSCR in name are prioritized
		//Files closest to exact number of tiles nscr calls for are prioritized
		
		List<SearchRes> passlist = new LinkedList<SearchRes>();
		DirectoryNode mydir = nscr_node.getParent();
		if(mydir == null) return new LinkedList<FileNode>();
		
		int tle_used = getMaxTileIndex();
		List<FileNode> sibs = mydir.getChildren();
		Collections.sort(sibs); //Make sure sorted by name
		int i = 0;
		int myidx = -1;
		for(FileNode sib : sibs){
			//If directory, toss
			if(sib.isDirectory()) continue;
			int idx = i++;
			if(sib == nscr_node){
				myidx = idx; continue;
			}
			//If not an NCGR, toss (will eventually introduce NBGR?)
			FileTypeNode type = sib.getTypeChainTail();
			if(type.getTypeID() != NCGR.TYPE_ID) continue;
			
			//Read in as NCGR
			NCGR tileset = NCGR.readNCGR(sib.loadDecompressedData());
			
			//Toss if doesn't have enough tiles
			if(tileset.getTileCount() < tle_used) continue;
			passlist.add(new SearchRes(sib, Math.abs(tle_used - tileset.getTileCount()), idx));
		}
		
		//Weigh name distance
		for(SearchRes r : passlist) r.str_weight = Math.abs(myidx - r.str_weight);
		
		Collections.sort(passlist);
		List<FileNode> list = new LinkedList<FileNode>();
		for(SearchRes r : passlist) list.add(r.node);
		
		return list;
	}
	
	public List<FileNode> searchForPalettes(FileNode nscr_node, boolean exclude4Bit) throws IOException{
		//Searches the directory for candidate tile sets
		//Files nearer to the NSCR in name are prioritized
		//Files closest to exact number of palettes nscr calls for are prioritized
		
		List<SearchRes> passlist = new LinkedList<SearchRes>();
		DirectoryNode mydir = nscr_node.getParent();
		if(mydir == null) return new LinkedList<FileNode>();
		
		int plt_used = getMaxPaletteIndex();
		List<FileNode> sibs = mydir.getChildren();
		Collections.sort(sibs); //Make sure sorted by name
		int i = 0;
		int myidx = -1;
		for(FileNode sib : sibs){
			//If directory, toss
			if(sib.isDirectory()) continue;
			//Mark indices
			int idx = i++;
			if(sib == nscr_node){
				myidx = idx; continue;
			}
			//If not an NCLR, toss
			FileTypeNode type = sib.getTypeChainTail();
			if(type.getTypeID() != NCLR.TYPE_ID) continue;
			
			//Read in as NCGR
			NCLR pltset = NCLR.readNCLR(sib.loadDecompressedData());
			
			//Toss if doesn't have enough tiles
			if(pltset.getPaletteCount() < plt_used) continue;
			
			if(exclude4Bit && !pltset.is8Bit()) continue;
			
			passlist.add(new SearchRes(sib, Math.abs(pltset.getPaletteCount() - plt_used), idx));
		}
		
		//Weigh name distance
		for(SearchRes r : passlist) r.str_weight = Math.abs(myidx - r.str_weight);
		
		Collections.sort(passlist);
		List<FileNode> list = new LinkedList<FileNode>();
		for(SearchRes r : passlist) list.add(r.node);
		
		return list;
	}
	
	/*----- Metadata -----*/
	
	public static void linkPaletteNode(FileNode nscr, FileNode nclr){
		if(nscr == null || nclr == null) return;
		
		//See if nclr already has UID. Assign one if not.
		String puid = nclr.getMetadataValue(NDSGraphics.METAKEY_NCLRID);
		if(puid == null){
			int i = nclr.getFullPath().hashCode();
			puid = Integer.toHexString(i);
			nclr.setMetadataValue(NDSGraphics.METAKEY_NCLRID, puid);
		}
		
		//Link UID and index to NCGR
		nscr.setMetadataValue(NDSGraphics.METAKEY_PALETTEID, puid);
		
		//Convert to relative link
		String rellink = nscr.findNodeThat(new NodeMatchCallback(){

			public boolean meetsCondition(FileNode n) {
				return n == nclr;
			}
			
		});
		
		if(rellink == null) rellink = nclr.getFullPath();
		nscr.setMetadataValue(NDSGraphics.METAKEY_PLTLINK, rellink);
	}
	
	public static NCLR loadLinkedPalette(FileNode nscr) throws IOException{
		if(nscr.getParent() == null) return null;
		
		//Look for link
		String pltlink = nscr.getMetadataValue(NDSGraphics.METAKEY_PLTLINK);
		if(pltlink != null){
			//Look for NCLR at that path
			FileNode pnode = nscr.getParent().getNodeAt(pltlink);
			if(pnode != null){
				NCLR nclr = NCLR.readNCLR(pnode.loadDecompressedData());
				return nclr;
			}
		}
		
		//No link or link broken
		//Match UID
		String puid = nscr.getMetadataValue(NDSGraphics.METAKEY_PALETTEID);
		if(puid != null){
			pltlink = nscr.findNodeThat(new NodeMatchCallback(){

				public boolean meetsCondition(FileNode n) {
					String mypuid = n.getMetadataValue(NDSGraphics.METAKEY_NCLRID);
					if(mypuid == null) return false;
					return mypuid.equals(puid);
				}
				
			});	
			
			//If find match...
			if(pltlink != null){
				nscr.setMetadataValue(NDSGraphics.METAKEY_PLTLINK, pltlink);
				//Load new link
				FileNode pnode = nscr.getParent().getNodeAt(pltlink);
				if(pnode != null){
					NCLR nclr = NCLR.readNCLR(pnode.loadDecompressedData());
					return nclr;
				}
			}
		}
		
		//UID wasn't matched either.
		//Attempt an auto-detect
		NSCR dat = NSCR.readNSCR(nscr.loadDecompressedData());
		List<FileNode> pcand = dat.searchForPalettes(nscr, false);
		if(pcand== null || pcand.isEmpty()) return null;
		
		FileNode pal = pcand.get(0);
		linkPaletteNode(nscr, pal);
		NCLR nclr = NCLR.readNCLR(pal.loadDecompressedData());
		return nclr;
	}
	
	public static void linkTilesetNode(FileNode nscr, FileNode ncgr){
		if(nscr == null || ncgr == null) return;
		
		//See if nclr already has UID. Assign one if not.
		String puid = ncgr.getMetadataValue(NDSGraphics.METAKEY_NCGRID);
		if(puid == null){
			int i = ncgr.getFullPath().hashCode();
			puid = Integer.toHexString(i);
			ncgr.setMetadataValue(NDSGraphics.METAKEY_NCGRID, puid);
		}
		
		//Link UID and index to NCGR
		nscr.setMetadataValue(NDSGraphics.METAKEY_TLEUID, puid);
		
		//Convert to relative link
		String rellink = nscr.findNodeThat(new NodeMatchCallback(){

			public boolean meetsCondition(FileNode n) {
				return n == ncgr;
			}
			
		});
		
		if(rellink == null) rellink = ncgr.getFullPath();
		nscr.setMetadataValue(NDSGraphics.METAKEY_TLELINK, rellink);
	}
	
	public static NCGR loadLinkedTileset(FileNode nscr) throws IOException{
		if(nscr.getParent() == null) return null;
		
		//Look for link
		String pltlink = nscr.getMetadataValue(NDSGraphics.METAKEY_TLELINK);
		if(pltlink != null){
			//Look for NCGR at that path
			FileNode pnode = nscr.getParent().getNodeAt(pltlink);
			if(pnode != null){
				NCGR ncgr = NCGR.readNCGR(pnode.loadDecompressedData());
				return ncgr;
			}
		}
		
		//No link or link broken
		//Match UID
		String puid = nscr.getMetadataValue(NDSGraphics.METAKEY_TLEUID);
		if(puid != null){
			pltlink = nscr.findNodeThat(new NodeMatchCallback(){

				public boolean meetsCondition(FileNode n) {
					String mypuid = n.getMetadataValue(NDSGraphics.METAKEY_NCGRID);
					if(mypuid == null) return false;
					return mypuid.equals(puid);
				}
				
			});	
			
			//If find match...
			if(pltlink != null){
				nscr.setMetadataValue(NDSGraphics.METAKEY_TLELINK, pltlink);
				//Load new link
				FileNode pnode = nscr.getParent().getNodeAt(pltlink);
				if(pnode != null){
					NCGR ncgr = NCGR.readNCGR(pnode.loadDecompressedData());
					return ncgr;
				}
			}
		}
		
		//UID wasn't matched either.
		//Attempt an auto-detect
		NSCR dat = NSCR.readNSCR(nscr.loadDecompressedData());
		List<FileNode> tscand = dat.searchForTilesets(nscr);
		if(tscand== null || tscand.isEmpty()) return null;
		
		//Otherwise, we assume the first is the best match.
		FileNode ts = tscand.get(0);
		linkTilesetNode(nscr, ts);
		NCGR ncgr = NCGR.readNCGR(ts.loadDecompressedData());
		return ncgr;
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
				NCLR nclr = NSCR.loadLinkedPalette(src);
				NCGR ncgr = NSCR.loadLinkedTileset(src);
				
				if(ncgr == null) return null;
				return nscr.renderImage(nclr, ncgr);
			}
			catch(Exception x){
				x.printStackTrace();
			}
			
			return null;
		}
		
		public BufferedImage renderWithPalette(FileNode src, Palette plt){

			try{
				NSCR nscr = NSCR.readNSCR(src.loadDecompressedData());
				NCLR nclr = NCLR.wrapPalette(plt);
				NCGR ncgr = NSCR.loadLinkedTileset(src);
				
				if(ncgr == null) return null;
				return nscr.renderImage(nclr, ncgr);
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
			NCLR nclr = NSCR.loadLinkedPalette(node);
			NCGR ncgr = NSCR.loadLinkedTileset(node);
				
			if(ncgr == null) throw new IOException("Linked NCGR resource could not be found!");
			
			BufferedImage img = nscr.renderImage(nclr, ncgr);
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
