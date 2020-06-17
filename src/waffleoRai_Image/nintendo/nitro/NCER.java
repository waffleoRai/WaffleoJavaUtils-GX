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
import waffleoRai_Image.PalettedImageDef;
import waffleoRai_Image.Tile;
import waffleoRai_Image.Tileset;
import waffleoRai_Image.TilesetDef;
import waffleoRai_Image.nintendo.NDSGraphics;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileNode;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;

public class NCER extends NDKDSFile{
	
	/*----- Constants -----*/
	
	public static final int TYPE_ID = 0x4e434552;
	public static final String DEFO_ENG_STR = "Nitro Cell Resource";
	
	public static final String MAGIC = "RECN";
	
	public static final String MAGIC_KBEC = "KBEC";
	public static final String MAGIC_LBAL = "LBAL";
	public static final String MAGIC_TXEU = "TXEU";
	
	public static final int[][][] DIM_TBL = {{{8,8}, {16,16}, {32,32}, {64,64}},
										     {{16,8}, {32,8}, {32,16}, {64,32}},
										     {{8,16}, {8,32}, {16,32}, {32,64}},
										     {{8,8}, {16,16}, {32,32}, {64,64}}};
	
	public static final String METAKEY_CELLRES_UID = "NCERUID";
	public static final String METAKEY_TSLINK = "TILESETLINK";
	public static final String METAKEY_TSUID = "TILESETID";
	public static final String METAKEY_PLTLINK = "PALETTELINK";
	public static final String METAKEY_PLTUID = "PALETTEID";
	public static final String METAKEY_PLTIDX = "PALETTEIDX";
	
	/*----- Instance Variables -----*/
	
	private int boundary_size;
	private ImgStruct[] imgs;
	private String label;
	private boolean uext_flag;
	
	/*----- Construction -----*/
	
	private NCER(){super(null);}
	
	private NCER(FileBuffer src){super(src);}
	
	/*----- Structs -----*/
	
	private static class ImgStruct{
		public CellStruct[] cells;
		
		public void renderTo(BufferedImage img, Tileset tiles, Palette plt){
			if(cells == null) return;
			for(int i = 0; i < cells.length; i++){
				if(cells[i] != null){
					//System.err.println("Rendering cell " + i);
					cells[i].renderTo(img, tiles, plt);
				}
			}
		}
		
		public void renderTo(BufferedImage img, Tileset tiles, Palette plt, int x_add, int y_add){
			if(cells == null) return;
			for(int i = 0; i < cells.length; i++){
				if(cells[i] != null){
					cells[i].renderTo(img, tiles, plt, x_add, y_add);
				}
			}
		}
	}
	
	private static class CellStruct{

		public int row;
		public int col;
		public int x_off;
		public int y_off;
		public int w;
		public int h;
		
		public boolean v_flag;
		public boolean h_flag;
		
		public int tile_off;
		
		public void renderTo(BufferedImage img, Tileset tiles, Palette plt){
			renderTo(img, tiles, plt, 0, 0);
		}
		
		public void renderTo(BufferedImage img, Tileset tiles, Palette plt, int x_add, int y_add){

			int x_st = 0; int y_st = 0;
			
			//Debug
			//System.err.println("\tRow: " + row);
			//System.err.println("\tCol: " + col);
			//System.err.println("\tOffset: " + x_off + "," + y_off);
			//System.err.println("\tVFlag: " + v_flag);
			//System.err.println("\tHFlag: " + h_flag);
			
			//Rows and columns of 64. In order 2,3,0,1
			x_st = ((col ^ 2) << 6) + x_off + x_add;
			y_st = ((row ^ 2) << 6) + y_off + y_add;
			if(h_flag && ((col^2) == 3)) x_st -= 256;
			if(v_flag && ((row^2) == 3)) x_st -= 256;
			
			int[] dim = DIM_TBL[w][h];
			int cw = dim[0] >>> 3;
			int ch = dim[1] >>> 3;
			
			//More debug
			//System.err.println("\tStart: " + x_st + "," + y_st);
			//System.err.println("\tDim: " + cw + "," + ch);
			
			int t = tile_off << 1; int x = x_st; int y = y_st;
			for(int r = 0; r < ch; r++){
				for(int l = 0; l < cw; l++){
					Tile tile = tiles.getTile(t++);
					tile.copyTo(img, x, y, false, false, plt);
					x += 8;
				}
				x = x_st; y += 8;
			}
			
		}
		
	}
	
	/*----- Parse -----*/
	
	public static NCER readNCER(FileBuffer data){

		NCER ncer = new NCER(data);
		
		//Read KBEC
		FileBuffer kbec = ncer.getSectionData(MAGIC_KBEC);
		int icount = kbec.intFromFile(0x8);
		ncer.imgs = new ImgStruct[icount];
		//ncer.boundary_size = kbec.intFromFile(0x10) << 6;
		ncer.boundary_size = kbec.intFromFile(0x10);
		
		//Cell table
		kbec.setCurrentPosition(0x20); //Jump to cell table
		int[][] celltbl = new int[icount][2];
		for(int i = 0; i < icount; i++){
			celltbl[i][0] = kbec.nextShort();
			kbec.nextShort();
			celltbl[i][1] = kbec.nextInt();
		}
		
		//Cell data
		long cdat_start = kbec.getCurrentPosition();
		for(int i = 0; i < icount; i++){
			long cpos = cdat_start + celltbl[i][1];
			int ccount = celltbl[i][0];
			ncer.imgs[i] = new ImgStruct();
			ncer.imgs[i].cells = new CellStruct[ccount];
			for(int j = 0; j < ccount; j++){
				CellStruct cell = new CellStruct();
				ncer.imgs[i].cells[j] = cell;
				
				int b = Byte.toUnsignedInt(kbec.getByte(cpos++));
				cell.row = (b & 0xC0) >>> 6;
				cell.y_off = (b & 0x3C);
				
				b = Byte.toUnsignedInt(kbec.getByte(cpos++));
				cell.w = (b & 0xC0) >>> 6;
				cell.v_flag = (b & 0x3) != 0;
				
				b = Byte.toUnsignedInt(kbec.getByte(cpos++));
				cell.col = (b & 0xC0) >>> 6;
				cell.x_off = (b & 0x3C);
				
				b = Byte.toUnsignedInt(kbec.getByte(cpos++));
				cell.h = (b & 0xC0) >>> 6;
				cell.h_flag = (b & 0x3) != 0;
				
				cell.tile_off = kbec.shortFromFile(cpos); cpos += 2;
			}
		}
		
		//LBAL
		FileBuffer lbal = ncer.getSectionData(MAGIC_LBAL);
		if(lbal != null){
			ncer.label = lbal.getASCII_string(0xC, '\0');
		}
		
		//TXEU
		FileBuffer txeu = ncer.getSectionData(MAGIC_TXEU);
		if(txeu != null){
			ncer.uext_flag = txeu.intFromFile(0x8) != 0;
		}
		
		return ncer;
	}
	
	/*----- Getters -----*/
	
	public int getBoundSize(){return boundary_size;}
	public String getLabel(){return label;}
	public boolean getUEXTFlag(){return this.uext_flag;}
	
	public int getImageCount(){
		if(imgs == null) return 0;
		return imgs.length;
	}
	
	public BufferedImage renderImage(int idx, Tileset tiles){
		Palette p = null;
		if(tiles.is4Bit()) p = Palette.get4BitGreyscalePalette();
		else p = Palette.get8BitGreyscalePalette();
		
		return renderImage(idx, tiles, p);
	}
	
	public BufferedImage renderImage(int idx, Tileset tiles, Palette plt){
		int dim = boundary_size << 6;
		BufferedImage img = new BufferedImage(dim, dim, BufferedImage.TYPE_INT_ARGB);
		if(imgs[idx] != null) imgs[idx].renderTo(img, tiles, plt);
		return img;
	}
	
	public BufferedImage[] renderImages(Tileset tiles){
		Palette p = null;
		if(tiles.is4Bit()) p = Palette.get4BitGreyscalePalette();
		else p = Palette.get8BitGreyscalePalette();
		
		return renderImages(tiles, p);
	}
	
	public BufferedImage[] renderImages(Tileset tiles, Palette plt){
		if(imgs == null) return null;
		
		BufferedImage[] images = new BufferedImage[imgs.length];
		int dim = boundary_size << 8; //??
		//int dim = 256;
		//System.err.println("Image dimensions: " + dim);
		for(int i = 0; i < imgs.length; i++){
			//System.err.println("Rendering image " + i);
			BufferedImage img = new BufferedImage(dim, dim, BufferedImage.TYPE_INT_ARGB);
			images[i] = img;
			if(imgs[i] != null) imgs[i].renderTo(img, tiles, plt);
		}
		
		return images;
	}
	
	public BufferedImage[] renderImages(Tileset tiles, Palette[] plt){
		if(imgs == null) return null;
		
		BufferedImage[] images = new BufferedImage[imgs.length];
		int dim = boundary_size << 8; //??
		//int dim = 256;
		//System.err.println("Image dimensions: " + dim);
		for(int i = 0; i < imgs.length; i++){
			//System.err.println("Rendering image " + i);
			BufferedImage img = new BufferedImage(dim, dim, BufferedImage.TYPE_INT_ARGB);
			images[i] = img;
			if(imgs[i] != null) imgs[i].renderTo(img, tiles, plt[i]);
		}
		
		return images;
	}
	
	public BufferedImage renderImageStrip(Tileset tiles){
		Palette p = null;
		if(tiles.is4Bit()) p = Palette.get4BitGreyscalePalette();
		else p = Palette.get8BitGreyscalePalette();
		
		return renderImageStrip(tiles, p);
	}
	
	public BufferedImage renderImageStrip(Tileset tiles, Palette plt){
		if(imgs == null) return null;
		
		int dim = boundary_size << 6;
		final int spacer = 8;
		int width = (dim+spacer) * imgs.length;
		width -= spacer;
		BufferedImage img = new BufferedImage(width, dim, BufferedImage.TYPE_INT_ARGB);
		
		int x_add = 0;
		for(int i = 0; i < imgs.length; i++){
			imgs[i].renderTo(img, tiles, plt, x_add, 0);
			x_add += dim;
			//Transparent spacer
			for(int r = 0; r < dim; r++){
				for(int l = 0; l < spacer; l++){
					img.setRGB(x_add+l, r, 0x00000000);
				}
			}
			x_add += spacer;
		}
		
		return img;
	}
	
	public BufferedImage renderImageStrip(Tileset tiles, Palette[] plt){
		if(imgs == null) return null;
		
		int dim = boundary_size << 6;
		final int spacer = 8;
		int width = (dim+spacer) * imgs.length;
		width -= spacer;
		BufferedImage img = new BufferedImage(width, dim, BufferedImage.TYPE_INT_ARGB);
		
		int x_add = 0;
		for(int i = 0; i < imgs.length; i++){
			imgs[i].renderTo(img, tiles, plt[i], x_add, 0);
			x_add += dim;
			//Transparent spacer
			for(int r = 0; r < dim; r++){
				for(int l = 0; l < spacer; l++){
					img.setRGB(x_add+l, r, 0x00000000);
				}
			}
			x_add += spacer;
		}
		
		return img;
	}
	
	/*----- Setters -----*/
	
	/*----- Resource Matching -----*/
	
	public static boolean linkTileset(FileNode cell_node, FileNode ts_node){
		return MetaResLinks.linkResource(cell_node, ts_node, 
				NDSGraphics.METAKEY_TLELINK, NDSGraphics.METAKEY_TLEUID);
	}
	
	public static Tileset loadLinkedTileset(FileNode cell_node){
		FileNode ts_node = MetaResLinks.findLinkedResource(cell_node, NDSGraphics.METAKEY_TLELINK, NDSGraphics.METAKEY_TLEUID);
		if(ts_node == null){
			//Look for match
			NodeMatchCallback filter = new NodeMatchCallback(){

				public boolean meetsCondition(FileNode n) {
					if(n == null) return false;
					FileTypeNode tail = n.getTypeChainTail();
					if(tail == null) return false;
					return (tail.getTypeDefinition() instanceof TilesetDef);
				}
				
			};
			List<NodeMatchCallback> filters = new ArrayList<NodeMatchCallback>(1);
			filters.add(filter);
			
			List<FileNode> candidates = MetaResLinks.findMatchCandidates(cell_node, filters);
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
	
	public static boolean linkPalette(FileNode cell_node, FileNode plt_node){
		if(!MetaResLinks.linkResource(cell_node, plt_node, 
				NDSGraphics.METAKEY_PLTLINK, NDSGraphics.METAKEY_PALETTEID)) return false;
		//cell_node.setMetadataValue(NDSGraphics.METAKEY_PLTIDX, Integer.toString(idx));
		return true;
	}
	
	public static Palette[] loadLinkedPalette(FileNode cell_node){
		FileNode plt_node = MetaResLinks.findLinkedResource(cell_node, NDSGraphics.METAKEY_PLTLINK, NDSGraphics.METAKEY_PALETTEID);
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
			
			List<FileNode> candidates = MetaResLinks.findMatchCandidates(cell_node, filters);
			if(candidates == null || candidates.isEmpty()) return null;
			plt_node = candidates.get(0);
		}
		
		//Parse index
		/*String istr = cell_node.getMetadataValue(NDSGraphics.METAKEY_PLTIDX);
		int idx = 0;
		try{idx = Integer.parseInt(istr);}
		catch(NumberFormatException x){
			cell_node.setMetadataValue(NDSGraphics.METAKEY_PLTIDX, "0");
		}*/
		
		//Load palette data
		if(plt_node == null) return null;
		FileTypeNode tail = plt_node.getTypeChainTail();
		if(tail == null) return null;
		if(tail.getTypeDefinition() instanceof PaletteFileDef){
			PaletteFileDef pdef = (PaletteFileDef)tail.getTypeDefinition();
			Palette[] pals = pdef.getPalette(plt_node);
			/*if(pals.length < idx) return pals[idx];
			else{
				cell_node.setMetadataValue(NDSGraphics.METAKEY_PLTIDX, "0");
				return pals[0];
			}*/
			return pals;
		}
		
		return null;
	}
	
	/*----- Conversion -----*/
	
	public void imageToPNG(int imgidx, String outpath, Tileset tiles) throws IOException{
		BufferedImage img = renderImage(imgidx, tiles);
		ImageIO.write(img, "png", new File(outpath));
	}
	
	public void imageToPNG(int imgidx, String outpath, Tileset tiles, Palette plt) throws IOException{
		BufferedImage img = renderImage(imgidx, tiles, plt);
		ImageIO.write(img, "png", new File(outpath));
	}
	
	public void imagesToPNG(String out_prefix, Tileset tiles) throws IOException{
		String outdir = out_prefix;
		int lastslash = outdir.lastIndexOf(File.separator);
		if(lastslash >= 0) outdir = outdir.substring(0, lastslash);
		if(!FileBuffer.directoryExists(outdir)) Files.createDirectories(Paths.get(outdir));
		
		BufferedImage[] imgs = renderImages(tiles);
		for(int i = 0; i < imgs.length; i++){
			String outpath = out_prefix + String.format("img%03d", i) + ".png";
			ImageIO.write(imgs[i], "png", new File(outpath));
		}
	}
	
	public void imagesToPNG(String out_prefix, Tileset tiles, Palette plt) throws IOException{
		String outdir = out_prefix;
		int lastslash = outdir.lastIndexOf(File.separator);
		if(lastslash >= 0) outdir = outdir.substring(0, lastslash);
		if(!FileBuffer.directoryExists(outdir)) Files.createDirectories(Paths.get(outdir));
		
		BufferedImage[] imgs = renderImages(tiles, plt);
		for(int i = 0; i < imgs.length; i++){
			String outpath = out_prefix + String.format("img%03d", i) + ".png";
			ImageIO.write(imgs[i], "png", new File(outpath));
		}
	}
	
	public void imagesToPNG(String out_prefix, Tileset tiles, Palette[] plt) throws IOException{
		String outdir = out_prefix;
		int lastslash = outdir.lastIndexOf(File.separator);
		if(lastslash >= 0) outdir = outdir.substring(0, lastslash);
		if(!FileBuffer.directoryExists(outdir)) Files.createDirectories(Paths.get(outdir));
		
		BufferedImage[] imgs = renderImages(tiles, plt);
		for(int i = 0; i < imgs.length; i++){
			String outpath = out_prefix + String.format("img%03d", i) + ".png";
			ImageIO.write(imgs[i], "png", new File(outpath));
		}
	}
	
	/*----- Definition -----*/
	
	private static TypeDef static_def;
	private static StdConverter static_conv;
	
	public static class TypeDef extends PalettedImageDef
	{
		
		public static final String[] EXT_LIST = {"ncer"};
		
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
		
		public String getDefaultExtension() {return "ncer";}

		public BufferedImage renderData(FileNode src){

			try{
				NCER ncer = NCER.readNCER(src.loadDecompressedData());
				Tileset tiles = NCER.loadLinkedTileset(src);
				Palette[] plt = NCER.loadLinkedPalette(src);
				
				if(tiles == null) return null;
				if(plt == null){
					Palette plt0 = null;
					if(tiles.is4Bit()) plt0 = Palette.get4BitGreyscalePalette();
					else plt0 = Palette.get8BitGreyscalePalette();
					plt = new Palette[ncer.imgs.length];
					for(int i = 0; i < plt.length; i++) plt[i] = plt0;
				}
				return ncer.renderImageStrip(tiles, plt);
			}
			catch(Exception x){
				x.printStackTrace();
			}
			
			return null;
		}
		
		public BufferedImage renderWithPalette(FileNode src, Palette plt){

			try{
				NCER ncer = NCER.readNCER(src.loadDecompressedData());
				Tileset tiles = NCER.loadLinkedTileset(src);
				
				if(tiles == null) return null;
				if(plt == null){
					if(tiles.is4Bit()) plt = Palette.get4BitGreyscalePalette();
					else plt = Palette.get8BitGreyscalePalette();
				}
				return ncer.renderImageStrip(tiles, plt);
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
			System.err.println("NCER is a tile map format. Meaningless to render without tile data.");
		}
		
		public void writeAsTargetFormat(FileNode node, String outpath) 
				throws IOException, UnsupportedFileTypeException{
			
			NCER ncer = NCER.readNCER(node.loadDecompressedData());
			Tileset tiles = NCER.loadLinkedTileset(node);
			Palette[] plt = NCER.loadLinkedPalette(node);
			
				
			if(tiles == null) throw new IOException("Linked tileset resource could not be found!");
			
			if(plt == null){
				Palette plt0 = null;
				if(tiles.is4Bit()) plt0 = Palette.get4BitGreyscalePalette();
				else plt0 = Palette.get8BitGreyscalePalette();
				plt = new Palette[ncer.imgs.length];
				for(int i = 0; i < plt.length; i++) plt[i] = plt0;
			}
			
			ncer.imagesToPNG(outpath, tiles, plt);
			
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
	

}
