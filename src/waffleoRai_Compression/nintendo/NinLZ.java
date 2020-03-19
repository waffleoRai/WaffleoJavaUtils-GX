package waffleoRai_Compression.nintendo;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.time.OffsetDateTime;

import waffleoRai_Compression.definitions.AbstractCompDef;
import waffleoRai_Compression.definitions.CompressionDefs;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.StreamWrapper;

/*It's f***ing yaz
 * I'm just making a Yaz wrapper for easy use, but it is Yaz*/

public class NinLZ {
	
	/*--- Constants ---*/
	
	public static final int COMPDEF_ID = 0xb812dba9;
	
	/*--- Instance Variables ---*/
	
	private Yaz core;
	
	/*--- General ---*/
	
	public NinLZ()
	{
		core = new Yaz();
		core.setAllow3Bytes(false);
		core.setReverseFlags(true);
	}

	protected void reset()
	{
		core.reset();
	}

	public int getDecompressedSize()
	{
		return core.getDecompressedSize();
	}
	
	protected void setOutputTarget(StreamWrapper trg)
	{
		core.setOutputTarget(trg);
	}

	/*--- Decode ---*/
	
	public StreamWrapper decode(ByteBuffer input, int allocate)
	{
		return core.decode(input, allocate);
	}
	
	public StreamWrapper decode(FileBuffer input, int allocate)
	{
		return core.decode(input, allocate);
	}
	
	public StreamWrapper decode(InputStream input) throws IOException
	{
		return core.decode(input);
	}
	
	public boolean decodeTo(StreamWrapper input, OutputStream output)
	{
		return core.decodeTo(input, output);
	}
	
	/*--- Encode ---*/
	
	public StreamWrapper encode(ByteBuffer input, int allocate)
	{
		return core.encode(input, allocate);
	}
	
	public StreamWrapper encode(FileBuffer input, int allocate)
	{
		return core.encode(input, allocate);
	}
	
	public StreamWrapper encode(InputStream input) throws IOException
	{
		return core.encode(input);
	}
	
	public void clearTempData() throws IOException
	{
		core.clearTempData();
	}
	
	/*--- Comp Definitions ---*/
	
	private static CompDef_YazDS static_def;
	
	public static CompDef_YazDS getDefinition()
	{
		if(static_def != null) return static_def;
		static_def = new CompDef_YazDS();
		return static_def;
	}
	
	public static class CompDef_YazDS extends AbstractCompDef{
		
		public static final String DEFO_ENG_STR = "Nintendo Yaz/LZ77 Compression";
		public static final String[] EXT_LIST = {"lz"};
		
		public CompDef_YazDS()
		{
			super(DEFO_ENG_STR);
			for(String e : EXT_LIST) super.extensions.add(e);
		}

		public int getDefinitionID()
		{
			return NinLZ.COMPDEF_ID;
		}
		
		public StreamWrapper decompress(StreamWrapper input)
		{
			Yaz yazzy = new Yaz();
			yazzy.setAllow3Bytes(false);
			yazzy.setReverseFlags(true);

			//Grab the DS header...
			FileBuffer dshead = new FileBuffer(4, false);
			for(int i = 0; i < 4; i++) dshead.addToFile(input.get());
			
			DSCompHeader chead = DSCompHeader.read(dshead, 0);
			
			
			return yazzy.decode(input, chead.getDecompressedSize());
		}
		
		public String decompressToDiskBuffer(StreamWrapper input)
		{
			//Prep Yaz decoder
			Yaz yazzy = new Yaz();
			yazzy.setAllow3Bytes(false);
			yazzy.setReverseFlags(true);
			
			//Skip DS compression info header
			for(int i = 0; i < 4; i++) input.get();
			
			//Generate path
			String tempdir = CompressionDefs.getCompressionTempDir();
			String temppath = tempdir + File.separator + Long.toHexString(OffsetDateTime.now().toEpochSecond()) + ".tmp";
			
			try
			{
				BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(temppath));
				yazzy.decodeTo(input, bos);
				bos.close();
			}
			catch(IOException x)
			{
				x.printStackTrace();
				return null;
			}
			
			
			return temppath;
		}
		
	}
	
}
