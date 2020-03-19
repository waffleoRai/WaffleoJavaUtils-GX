package waffleoRai_Compression.nintendo;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.time.OffsetDateTime;

import waffleoRai_Compression.definitions.AbstractCompDef;
import waffleoRai_Compression.definitions.CompressionDefs;
import waffleoRai_Utils.ByteBufferStreamer;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileOutputStreamer;
import waffleoRai_Utils.StreamWrapper;

public class DSRLE {
	
	public static final int COMPDEF_ID = 0x98573947;
	
	private static void decode(StreamWrapper input, StreamWrapper out)
	{
		while(!input.isEmpty())
		{
			//Read info byte...
			int header = input.getFull();
			boolean comp = (header & 0x80) != 0;
			int amt = (header & 0x7F);
			if(comp)
			{
				amt += 3;
				byte copybyte = input.get();
				for(int i = 0; i < amt; i++) out.put(copybyte);
			}
			else
			{
				amt++;
				for(int i = 0; i < amt; i++) out.put(input.get());
			}
		}
		
	}
	
	public static StreamWrapper decode(ByteBuffer input, int alloc)
	{
		ByteBufferStreamer in = ByteBufferStreamer.wrap(input); //Init Input
		ByteBufferStreamer output = new ByteBufferStreamer(alloc);
		
		decode(in, output);
		output.rewind();
		return output;
	}
	
	public static StreamWrapper decode(FileBuffer input, int alloc)
	{
		ByteBufferStreamer in = ByteBufferStreamer.wrap(input); //Init Input
		ByteBufferStreamer output = new ByteBufferStreamer(alloc);
		
		decode(in, output);
		output.rewind();
		return output;
	}
		
	public static StreamWrapper decode(StreamWrapper input, int alloc)
	{
		ByteBufferStreamer output = new ByteBufferStreamer(alloc);
		
		decode(input, output);
		output.rewind();
		return output;
	}

	public static boolean decodeTo(StreamWrapper input, OutputStream output)
	{
		if(output == null) return false;
		if(input == null) return false;
		decode(input, new FileOutputStreamer(output));
		
		return true;
	}
	
	private static CompDef static_def;
	
	public static CompDef getDefinition()
	{
		if(static_def != null) return static_def;
		static_def = new CompDef();
		return static_def;
	}
	
	public static class CompDef extends AbstractCompDef{

		public static final String DEFO_ENG_STR = "Nintendo DS RLE";
		public static final String[] EXT_LIST = {};
		
		public CompDef()
		{
			super(DEFO_ENG_STR);
			for(String e : EXT_LIST) super.extensions.add(e);
		}

		public StreamWrapper decompress(StreamWrapper input)
		{
			FileBuffer dshead = new FileBuffer(4, false);
			for(int i = 0; i < 4; i++) dshead.addToFile(input.get());
			
			DSCompHeader chead = DSCompHeader.read(dshead, 0);
			
			
			return DSRLE.decode(input, chead.getDecompressedSize());
		}
		
		public String decompressToDiskBuffer(StreamWrapper input)
		{
			for(int i = 0; i < 4; i++) input.get();
			
			//Generate path
			String tempdir = CompressionDefs.getCompressionTempDir();
			String temppath = tempdir + File.separator + Long.toHexString(OffsetDateTime.now().toEpochSecond()) + ".tmp";
			
			try
			{
				BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(temppath));
				DSRLE.decodeTo(input, bos);
				bos.close();
			}
			catch(IOException x)
			{
				x.printStackTrace();
				return null;
			}
			
			return temppath;
		}

		public int getDefinitionID() {return COMPDEF_ID;}
		
	}
	
}
