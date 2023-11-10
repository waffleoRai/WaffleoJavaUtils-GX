package waffleoRai_Compression.nintendo;

import java.io.InputStream;

import waffleoRai_Compression.definitions.AbstractCompDef;
import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.FileBuffer;
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
		
		public CompDef(){
			super(DEFO_ENG_STR);
			for(String e : EXT_LIST) super.extensions.add(e);
		}

		public int getDefinitionID(){
			return COMPDEF_ID;
		}

		@Override
		public boolean decompressToDiskBuffer(StreamWrapper input, String bufferPath, int options) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean decompressToDiskBuffer(InputStream input, String bufferPath, int options) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean decompressToDiskBuffer(BufferReference input, String bufferPath, int options) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public FileBuffer decompressToMemory(StreamWrapper input, int allocAmount, int options) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public FileBuffer decompressToMemory(InputStream input, int allocAmount, int options) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public FileBuffer decompressToMemory(BufferReference input, int allocAmount, int options) {
			// TODO Auto-generated method stub
			return null;
		}
		
	}
	
	
}
