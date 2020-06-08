package waffleoRai_Image.nintendo.nitro;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import javax.imageio.ImageIO;

import waffleoRai_Containers.nintendo.NDKDSFile;
import waffleoRai_Files.Converter;
import waffleoRai_Files.FileClass;
import waffleoRai_Image.Palette;
import waffleoRai_Image.PalettedImageDef;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileNode;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;

public class NCGR extends NDKDSFile{
	
	/*----- Constants -----*/
	
	public static final int TYPE_ID = 0x4e434752;
	public static final String DEFO_ENG_STR = "Nitro Character Graphics Resource";
	
	public static final String MAGIC = "RGCN";
	
	public static final String MAGIC_RAHC = "RAHC";
	public static final String MAGIC_SOPC = "SOPC";
	
	/*----- Instance Variables -----*/
	
	private boolean bit8;
	private int tiledim;
	private int supertile_dim;
	//private int[][] data; //x,y
	
	private DSTile[] tiles;
	
	private Palette linked_plt;
	
	/*----- Construction -----*/
	
	private NCGR(){super(null);}
	
	private NCGR(FileBuffer src){super(src);}
	
	/*----- Parse -----*/
	
	public static NCGR readNCGR(FileBuffer data){
		NCGR me = new NCGR(data);
		
		//Read RAHC
		FileBuffer rahc = me.getSectionData(MAGIC_RAHC);
		rahc.setCurrentPosition(0x8);
		me.supertile_dim = rahc.nextShort();
		int tsize = rahc.nextShort();
		int bd = rahc.nextInt();
		me.bit8 = false;
		if(bd == 4) me.bit8 = true;
		//me.bit8 = true;
		int pcount = rahc.intFromFile(0x18);
		if(!me.bit8)pcount = pcount << 1;
		rahc.setCurrentPosition(0x20);
		//System.err.println("Pixel Count: 0x" + Integer.toHexString(pcount));

		int r = 0; int l = 0;
		int dim = 8;
		int tcount = 0;
		if(tsize != -1){
			if(!me.bit8) tsize = tsize << 1;
			dim = (int)Math.round(Math.sqrt(tsize));
			tcount = pcount/tsize;
			//tcount = pcount/(dim*dim);
			
			//dim = tsize;
			//tcount = pcount/(tsize * tsize);
		}
		else tcount = pcount >>> 6;
		me.tiledim = dim;
		//System.err.println("Tile Size: " + dim);
		//System.err.println("Tile Count: " + tcount);
		
		me.tiles = new DSTile[tcount];
		int t = 0;
		int[][] dat = null;
		
		if(me.bit8){
			for(int i = 0; i < pcount; i++){
				if(dat == null){
					me.tiles[t] = new DSTile(8,dim);
					dat = me.tiles[t++].getData();
				}
				
				int b = Byte.toUnsignedInt(rahc.nextByte());
				dat[l++][r] = b;
				if(l >= dim){
					r++; l = 0;
					if(r >= dim){
						r = 0;
						dat = null;
					}
				}
				
			}
		}
		else{
			for(int i = 0; i < pcount; i+=2){
				if(dat == null){
					me.tiles[t] = new DSTile(4,dim);
					dat = me.tiles[t++].getData();
				}
				
				int b = Byte.toUnsignedInt(rahc.nextByte());
				dat[l++][r] = (b & 0xF);
				dat[l++][r] = ((b >>> 4) & 0xF);
				
				if(l >= dim){
					r++; l = 0;
					if(r >= dim){
						r = 0;
						dat = null;
					}
				}
			}	
			
		}

		return me;
		
	}
	
	/*----- Getters -----*/
	
	public boolean is8Bit(){
		return bit8;
	}
	
	public int getTileDimension(){
		return tiledim;
	}
	
	public int getTileCount(){
		return tiles.length;
	}
	
	public int getSupertileDimension(){
		return this.supertile_dim;
	}
	
	public DSTile getTile(int idx){
		return tiles[idx];
	}
	
	public List<BufferedImage> renderTileData(){

		List<BufferedImage> list = new LinkedList<BufferedImage>();
		for(int i = 0; i < tiles.length; i++){
			list.add(renderTileData(i));
		}
		
		return list;
	}
	
 	public BufferedImage renderImage(int tilewidth){
		if(linked_plt == null) return renderImageData(tilewidth); 
		
		int w = tilewidth * tiledim;
		int h = (tiles.length/tilewidth) * tiledim;

		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);int l = 0;
		int x = 0; int y = 0;
		for(int t = 0; t < tiles.length; t++){
			tiles[t].copyTo(img, x, y, false, false, linked_plt);
			
			x += tiledim;
			
			if(++l >= tilewidth){
				l = 0; x = 0;
				y += tiledim;
			}
			
		}
		
		return img;
	}
	
	public BufferedImage renderImageData(int tilewidth){

		int w = tilewidth * tiledim;
		int h = (tiles.length/tilewidth) * tiledim;

		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);int l = 0;
		int x = 0; int y = 0;
		for(int t = 0; t < tiles.length; t++){
			tiles[t].copyTo(img, x, y, false, false);
			
			x += tiledim;
			
			if(++l >= tilewidth){
				l = 0; x = 0;
				y += tiledim;
			}
			
		}
		
		return img;
	}
	
	public BufferedImage renderTile(int idx){
		if(linked_plt == null) return renderTileData(idx); 
		
		BufferedImage img = new BufferedImage(tiledim, tiledim, BufferedImage.TYPE_INT_ARGB);
		tiles[idx].copyTo(img, 0, 0, false, false, linked_plt);
		
		return img;
	}
	
	public BufferedImage renderTileData(int idx){

		BufferedImage img = new BufferedImage(tiledim, tiledim, BufferedImage.TYPE_INT_ARGB);
		tiles[idx].copyTo(img, 0, 0, false, false);
		
		return img;
	}
		
	/*----- Setters -----*/
	
	public void linkPalette(Palette plt){
		linked_plt = plt;
	}
	
	/*----- Metadata -----*/
	
	/*public static void linkPaletteNode(FileNode ncgr, FileNode nclr, int pidx){
		if(ncgr == null || nclr == null) return;
		
		//See if nclr already has UID. Assign one if not.
		String puid = nclr.getMetadataValue(NDSGraphics.METAKEY_NCLRID);
		if(puid == null){
			int i = nclr.getFullPath().hashCode();
			puid = Integer.toHexString(i);
			nclr.setMetadataValue(NDSGraphics.METAKEY_NCLRID, puid);
		}
		
		//Link UID and index to NCGR
		ncgr.setMetadataValue(NDSGraphics.METAKEY_PALETTEID, puid);
		ncgr.setMetadataValue(NDSGraphics.METAKEY_PLTIDX, Integer.toString(pidx));
		
		//Convert to relative link
		String rellink = ncgr.findNodeThat(new NodeMatchCallback(){

			public boolean meetsCondition(FileNode n) {
				return n == nclr;
			}
			
		});
		
		if(rellink == null) rellink = nclr.getFullPath();
		ncgr.setMetadataValue(NDSGraphics.METAKEY_PLTLINK, rellink);
	}
	
	public static Palette loadLinkedPalette(FileNode ncgr){
		if(ncgr.getParent() == null) return null;
		
		//Make sure index is valid
		String rawidx = ncgr.getMetadataValue(NDSGraphics.METAKEY_PLTIDX);
		if(rawidx == null) return null;
		
		int pidx = 0;
		try{pidx = Integer.parseInt(rawidx);}
		catch(NumberFormatException x){
			ncgr.setMetadataValue(NDSGraphics.METAKEY_PLTIDX, null);
			return null;
		}
		
		//Look for link
		String pltlink = ncgr.getMetadataValue(NDSGraphics.METAKEY_PLTLINK);
		if(pltlink != null){
			//Look for NCLR at that path
			FileNode pnode = ncgr.getParent().getNodeAt(pltlink);
			if(pnode != null){
				try{
					NCLR nclr = NCLR.readNCLR(pnode.loadDecompressedData());
					return nclr.getPalette(pidx);
				}
				catch(Exception x){
					x.printStackTrace();
				}
			}
		}
		
		//No link or link broken
		//Match UID
		String puid = ncgr.getMetadataValue(NDSGraphics.METAKEY_PALETTEID);
		if(puid != null){
			pltlink = ncgr.findNodeThat(new NodeMatchCallback(){

				public boolean meetsCondition(FileNode n) {
					String mypuid = n.getMetadataValue(NDSGraphics.METAKEY_NCLRID);
					if(mypuid == null) return false;
					return mypuid.equals(puid);
				}
				
			});	
			
			//If find match...
			if(pltlink != null){
				ncgr.setMetadataValue(NDSGraphics.METAKEY_PLTLINK, pltlink);
				//Load new link
				FileNode pnode = ncgr.getParent().getNodeAt(pltlink);
				if(pnode != null){
					try{
						NCLR nclr = NCLR.readNCLR(pnode.loadDecompressedData());
						return nclr.getPalette(pidx);
					}
					catch(Exception x){
						x.printStackTrace();
					}
				}
			}
		}
		
		//UID wasn't matched either. No auto detect at the moment.
		
		return null;
	}*/
	
	/*----- Definition -----*/

	private static TypeDef static_def;
	private static StdConverter static_conv;
	
	public static class TypeDef extends PalettedImageDef
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
				return ncgr.renderImageData(8);
			}
			catch(Exception e){
				e.printStackTrace();
			}
			
			return null;
		}
		
		public BufferedImage renderWithPalette(FileNode src, Palette plt){
			try{
				NCGR ncgr = NCGR.readNCGR(src.loadDecompressedData());
				ncgr.linkPalette(plt);
				return ncgr.renderImage(8);
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
				BufferedImage img = ncgr.renderTile(i);
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
				BufferedImage img = ncgr.renderTile(i);
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
