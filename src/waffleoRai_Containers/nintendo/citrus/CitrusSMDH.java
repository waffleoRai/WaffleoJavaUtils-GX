package waffleoRai_Containers.nintendo.citrus;

import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import waffleoRai_Files.FileClass;
import waffleoRai_Files.FileTypeDefinition;
import waffleoRai_Image.RecursiveTile;
import waffleoRai_Image.Tile;
import waffleoRai_Image.nintendo.NDSGraphics;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;

public class CitrusSMDH {
	
	//3DS icon file
	
	/*----- Constants -----*/
	
	public static final String MAGIC = "SMDH";
	
	public static final String STR_ENCODING = "UnicodeLittleUnmarked";
	
	public static final int LANIDX_JPN = 0;
	public static final int LANIDX_ENG = 1;
	public static final int LANIDX_FRA = 2;
	public static final int LANIDX_GER = 3;
	public static final int LANIDX_ITA = 4;
	public static final int LANIDX_SPA = 5;
	public static final int LANIDX_CHS = 6;
	public static final int LANIDX_KOR = 7;
	public static final int LANIDX_DCH = 8;
	public static final int LANIDX_PRT = 9;
	public static final int LANIDX_RUS = 10;
	public static final int LANIDX_CHT = 11;
	
	public static final int REGBIT_IDX_JPN = 0;
	public static final int REGBIT_IDX_USA = 1;
	public static final int REGBIT_IDX_EUR = 2;
	public static final int REGBIT_IDX_AUS = 3;
	public static final int REGBIT_IDX_CHN = 4;
	public static final int REGBIT_IDX_KOR = 5;
	public static final int REGBIT_IDX_TWN = 6;
	
	/*----- Instance Variables -----*/
	
	private String[][] titles; //Short desc, long desc, publisher
	private byte[] age_ratings;
	private int reg_flags;
	
	private int id_matchmaker;
	private long id_matchmaker_bit;
	
	private int flags;
	
	private int eula_ver_major;
	private int eula_ver_minor;
	
	private int bnr_opt_frame;
	
	private int cec_id;
	
	private BufferedImage ico_small;
	private BufferedImage ico;
	
	/*----- Construction/Parsing -----*/
	
	private CitrusSMDH(){}
	
	public static CitrusSMDH readSMDH(FileBuffer data, long stpos) throws UnsupportedFileTypeException{

		//Check magic
		long mpos = data.findString(stpos, stpos+0x10, MAGIC);
		if(mpos != stpos) throw new FileBuffer.UnsupportedFileTypeException("SMDH magic number not found!");
		
		//Initialize
		CitrusSMDH icon = new CitrusSMDH();
		long cpos = mpos + 8;
		data.setEndian(false);
		
		//Read titles
		icon.titles = new String[16][3]; //Short desc, long desc, pub
		for(int i = 0; i < 16; i++){
			
			//Test first byte
			byte test = data.getByte(cpos);
			if(test != 0){
				icon.titles[i][0] = data.readEncoded_string(STR_ENCODING, cpos, cpos + 0x80);
			}
			cpos += 0x80;
			
			test = data.getByte(cpos);
			if(test != 0){
				icon.titles[i][1] = data.readEncoded_string(STR_ENCODING, cpos, cpos + 0x100);
			}
			cpos += 0x100;
			
			test = data.getByte(cpos);
			if(test != 0){
				icon.titles[i][2] = data.readEncoded_string(STR_ENCODING, cpos, cpos + 0x80);
			}
			cpos += 0x80;
		}
		
		//Region info
		icon.age_ratings = new byte[16];
		for(int i = 0; i < 16; i++) icon.age_ratings[i] = data.getByte(cpos++);
		icon.reg_flags = data.intFromFile(cpos); cpos+=4;
		
		icon.id_matchmaker = data.intFromFile(cpos); cpos += 4;
		icon.id_matchmaker_bit = data.longFromFile(cpos); cpos += 8;
		icon.flags = data.intFromFile(cpos); cpos+=4;
		icon.eula_ver_minor = Byte.toUnsignedInt(data.getByte(cpos++));
		icon.eula_ver_major = Byte.toUnsignedInt(data.getByte(cpos++));
		cpos += 2;
		icon.bnr_opt_frame = data.intFromFile(cpos); cpos+=4;
		icon.cec_id = data.intFromFile(cpos); cpos += 4;
		cpos += 8;
		
		icon.ico_small = readImage(data, cpos, 24); cpos += 0x480;
		icon.ico = readImage(data, cpos, 48); cpos += 0x1200;
		
		return icon;
	}
	
	private static BufferedImage readImage(FileBuffer dat, long stpos, int dim){
		
		BufferedImage img = new BufferedImage(dim, dim, BufferedImage.TYPE_INT_ARGB);
		
		//Tiling?
		//I think it goes 2x2, then 4x4, then 8x8?
		dat.setCurrentPosition(stpos);
		
		//Layer 1... (2x2)
		int pixcount = dim * dim;
		int t1_count = pixcount >>> 2;
		Tile[] t1 = new Tile[t1_count];
		for(int t = 0; t < t1_count; t++){
			Tile tile = new Tile(32, 2);
			for(int r = 0; r < 2; r++){
				for(int l = 0; l < 2; l++){
					int pval = NDSGraphics.RGB565LE_to_ARGB(dat.nextShort());
					tile.setValueAt(l, r, pval);
				}
			}
			t1[t] = tile;
		}
		
		//Layer 2...
		int t2_count = t1_count >>> 2;
		RecursiveTile[] t2 = new RecursiveTile[t2_count];
		int i = 0;
		for(int t = 0; t < t2_count; t++){
			RecursiveTile tile = new RecursiveTile(32, 2, 2);
			for(int r = 0; r < 2; r++){
				for(int l = 0; l < 2; l++){
					tile.setTileAt(l, r, t1[i++]);
				}
			}
			t2[t] = tile;
		}
		
		//Layer 3...
		int t3_count = t2_count >>> 2;
		RecursiveTile[] t3 = new RecursiveTile[t3_count];
		i = 0;
		for(int t = 0; t < t3_count; t++){
			RecursiveTile tile = new RecursiveTile(32, 4, 2);
			for(int r = 0; r < 2; r++){
				for(int l = 0; l < 2; l++){
					tile.setTileAt(l, r, t2[i++]);
				}
			}
			t3[t] = tile;
		}
		
		//Render to image
		i = 0;
		int x = 0; int y = 0;
		int big_tile_dim = dim >>> 3;
		for(int r = 0; r < big_tile_dim; r++){
			for(int l = 0; l < big_tile_dim; l++){
				t3[i++].copyTo(img, x, y, false, false, null);
				x+=8;
			}
			x = 0; y+=8;
		}
		
		return img;
	}
	
	/*----- Getters -----*/
	
	public String getShortDescription(int lan_idx){return titles[lan_idx][0];}
	public String getLongDescription(int lan_idx){return titles[lan_idx][1];}
	public String getPublisherName(int lan_idx){return titles[lan_idx][2];}
	public byte getAgeRate(int rating_idx){return age_ratings[rating_idx];}
	public boolean checkRegionFlag(int idx){return (reg_flags & (1 << idx)) != 0;}
	public int getMatchmakerID(){return id_matchmaker;}
	public long getMatchmakerBITID(){return id_matchmaker_bit;}
	public int getSystemFlags(){return flags;}
	public int getEULAVersionMajor(){return eula_ver_major;}
	public int getEULAVersionMinor(){return eula_ver_minor;}
	public int getOptimalBannerFrame(){return bnr_opt_frame;}
	public int getCECID(){return cec_id;}
	
	public BufferedImage getSmallIcon(){return this.ico_small;}
	public BufferedImage getIcon(){return this.ico;}
	
	/*----- Setters -----*/
	
	/*----- Definition -----*/
	
	private static CitrusIconDef icon_def;
	
	public static CitrusIconDef getIconDef(){
		if(icon_def == null) icon_def = new CitrusIconDef();
		return icon_def;
	}
	
	public static class CitrusIconDef implements FileTypeDefinition{

		private static String DEFO_ENG_DESC = "Nintendo 3DS System Icon";
		public static int TYPE_ID = 0x3d1c01c0;
		
		private String str;
		
		public CitrusIconDef(){
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

}
