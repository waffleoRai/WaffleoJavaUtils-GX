package waffleoRai_Compression.nintendo;

import waffleoRai_Compression.definitions.AbstractCompDef;
import waffleoRai_Utils.StreamWrapper;

public class NinHuff {
	
	public static final int COMPDEF_ID = 0xdff68236;

	private static CompDef static_def;
	
	public static CompDef getDefinition()
	{
		if(static_def != null) return static_def;
		static_def = new CompDef();
		return static_def;
	}
	
	
	public static class CompDef extends AbstractCompDef{
		
		public static final String DEFO_ENG_STR = "Nintendo DS Huffman Compression";
		public static final String[] EXT_LIST = {};
		
		public CompDef()
		{
			super(DEFO_ENG_STR);
			for(String e : EXT_LIST) super.extensions.add(e);
		}

		public int getDefinitionID()
		{
			return COMPDEF_ID;
		}
		
		public StreamWrapper decompress(StreamWrapper input)
		{
			//TODO
			return null;
		}
		
		public String decompressToDiskBuffer(StreamWrapper input)
		{
			//TODO
			return null;
		}
		
	}
	
	
}
