package waffleoRai_Image.nintendo.nitro;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import waffleoRai_Image.Anim2DDef;
import waffleoRai_Image.Animation;

/*
 * KNBA Format
 * 	0x00	"KNBA" [4]
 * 	0x04	Chunk Size [4]
 * 	0x08	Anim Count [2]
 * 	0x0A	Frame Count [2]
 * 	0x0C	[Unknown, usually 0x18]	[4]
 * 	0x10	[Unknown]	[4]
 * 	0x14	Data Size [4]
 * 	0x18	(Padding)	[8]
 * 	0x20 	Animation Blocks... [n*16]
 * 		0x00	Frame Count [4]
 * 		0x04	[Unknown]	[2]
 * 		0x06	[Unknown]	[2] --0x01
 * 		0x08	[Unknown]	[4] -- 0x01
 * 		0x0C	Offset of first frame block [4]
 * 				(From end of the animation blocks chunk)
 * 	Varies	Frame Blocks... [n*8]
 * 		0x00	[Unknown]	[4]
 * 		0x04	Frame Width	[2]
 * 		0x06	(Marker 0xBEEF)	[2]
 * 	Varies	Frame Data... [n*2]
 * 		0x00	[Unknown]	[2]
 */

public class NANR {
	
	/*----- Constants -----*/
	
	public static final int TYPE_ID = 0x4e414e52;
	public static final String DEFO_ENG_STR = "Nitro Animation Resource";
	
	public static final String MAGIC = "RNAN";
	
	public static final String MAGIC_KNBA = "KNBA";
	public static final String MAGIC_LBAL = "LBAL";
	public static final String MAGIC_TXEU = "TXEU";

	/*----- Instance Variables -----*/
	
	private AnimStruct[] anims;
	
	/*----- Construction -----*/
	
	
	/*----- Structs -----*/
	
	private static class AnimStruct{
		private FrameStruct[] frames;
	}
	
	private static class FrameStruct{
		
	}
	
	/*----- Parse -----*/
	
	/*----- Getters -----*/
	
	/*----- Setters -----*/
	
	/*----- Resource Matching -----*/
	
	/*----- Conversion -----*/
	
	/*----- Definition -----*/
	
	private static TypeDef static_def;
	
	public static class TypeDef extends Anim2DDef
	{
		
		public static final String[] EXT_LIST = {"nanr"};
		
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
		
		public String getDefaultExtension() {return "nanr";}

		public Animation getAnimation() {
			// TODO Auto-generated method stub
			return null;
		}

		public int countFrames() {
			// TODO Auto-generated method stub
			return 0;
		}

	}
	
	public static TypeDef getTypeDef()
	{
		if(static_def != null) return static_def;
		static_def = new TypeDef();
		return static_def;
	}
	
	
}
