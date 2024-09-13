package waffleoRai_Compression.nintendo;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import waffleoRai_Compression.definitions.AbstractCompDef;
import waffleoRai_Compression.definitions.CompressionDefs;
import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.ByteBufferStreamer;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBufferStreamer;
import waffleoRai_Utils.FileInputStreamer;
import waffleoRai_Utils.FileOutputStreamer;
import waffleoRai_Utils.StreamWrapper;

public class DSRLE {
	
	public static final int COMPDEF_ID = 0x98573947;
	
	private static void decode(StreamWrapper input, StreamWrapper out, int wmax){
		int wcount = 0;
		while(!input.isEmpty() && (wmax < 0 || wcount < wmax)){
			//Read info byte...
			int header = input.getFull();
			boolean comp = (header & 0x80) != 0;
			int amt = (header & 0x7F);
			if(comp){
				amt += 3;
				byte copybyte = input.get();
				for(int i = 0; i < amt; i++) out.put(copybyte);
			}
			else{
				amt++;
				for(int i = 0; i < amt; i++) out.put(input.get());
			}
			wcount += amt;
		}
		
	}
	
	public static StreamWrapper decode(ByteBuffer input, int alloc){
		ByteBufferStreamer in = ByteBufferStreamer.wrap(input); //Init Input
		ByteBufferStreamer output = new ByteBufferStreamer(alloc);
		
		decode(in, output, alloc);
		output.rewind();
		return output;
	}
	
	public static StreamWrapper decode(FileBuffer input, int alloc){
		ByteBufferStreamer in = ByteBufferStreamer.wrap(input); //Init Input
		ByteBufferStreamer output = new ByteBufferStreamer(alloc);
		
		decode(in, output, alloc);
		output.rewind();
		return output;
	}
		
	public static StreamWrapper decode(StreamWrapper input, int alloc){
		ByteBufferStreamer output = new ByteBufferStreamer(alloc);
		
		decode(input, output, alloc);
		output.rewind();
		return output;
	}

	public static boolean decodeTo(StreamWrapper input, OutputStream output){
		if(output == null) return false;
		if(input == null) return false;
		decode(input, new FileOutputStreamer(output), -1);
		
		return true;
	}
	
	private static CompDef static_def;
	
	public static CompDef getDefinition(){
		if(static_def != null) return static_def;
		static_def = new CompDef();
		return static_def;
	}
	
	public static class CompDef extends AbstractCompDef{

		public static final String DEFO_ENG_STR = "Nintendo DS RLE";
		public static final String[] EXT_LIST = {};
		
		public CompDef(){
			super(DEFO_ENG_STR);
			for(String e : EXT_LIST) super.extensions.add(e);
		}

		public int getDefinitionID() {return COMPDEF_ID;}

		public boolean decompressToDiskBuffer(StreamWrapper input, String bufferPath, int options) {
			if((options & CompressionDefs.DECOMP_OP_HEADERLESS) == 0){
				for(int i = 0; i < 4; i++) input.get();
			}
			try{
				BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(bufferPath));
				DSRLE.decode(input, new FileOutputStreamer(bos), -1);
				bos.close();
			}
			catch(IOException ex){
				ex.printStackTrace();
				return false;
			}
			
			return true;
		}

		public boolean decompressToDiskBuffer(InputStream input, String bufferPath, int options) {
			try{
				if((options & CompressionDefs.DECOMP_OP_HEADERLESS) == 0){
					for(int i = 0; i < 4; i++) input.read();
				}
				
				BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(bufferPath));
				DSRLE.decode(new FileInputStreamer(input), new FileOutputStreamer(bos), -1);
				bos.close();
			}
			catch(IOException ex){
				ex.printStackTrace();
				return false;
			}
			return true;
		}

		public boolean decompressToDiskBuffer(BufferReference input, String bufferPath, int options) {
			if((options & CompressionDefs.DECOMP_OP_HEADERLESS) == 0){
				for(int i = 0; i < 4; i++) input.nextByte();
			}
			try{
				BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(bufferPath));
				FileBuffer inbuff = input.getBuffer();
				inbuff.setCurrentPosition(input.getBufferPosition());
				DSRLE.decode(new FileBufferStreamer(inbuff), new FileOutputStreamer(bos), -1);
				bos.close();
			}
			catch(IOException ex){
				ex.printStackTrace();
				return false;
			}
			
			return true;
		}

		public FileBuffer decompressToMemory(StreamWrapper input, int allocAmount, int options) {
			if((options & CompressionDefs.DECOMP_OP_HEADERLESS) == 0){
				FileBuffer dshead = new FileBuffer(4, false);
				for(int i = 0; i < 4; i++) dshead.addToFile(input.get());
				
				DSCompHeader chead = DSCompHeader.read(dshead, 0);
				if(allocAmount <= 0) allocAmount = chead.getDecompressedSize();
			}
			FileBuffer buff = new FileBuffer(allocAmount, false);
			DSRLE.decode(input, new FileBufferStreamer(buff), allocAmount);
			
			return buff;
		}

		public FileBuffer decompressToMemory(InputStream input, int allocAmount, int options) {
			if((options & CompressionDefs.DECOMP_OP_HEADERLESS) == 0){
				FileBuffer dshead = new FileBuffer(4, false);
				try{
					for(int i = 0; i < 4; i++) dshead.addToFile(input.read());
				}
				catch(IOException ex){
					ex.printStackTrace();
					return null;
				}
				
				DSCompHeader chead = DSCompHeader.read(dshead, 0);
				if(allocAmount <= 0) allocAmount = chead.getDecompressedSize();
			}
			FileBuffer buff = new FileBuffer(allocAmount, false);
			DSRLE.decode(new FileInputStreamer(input), new FileBufferStreamer(buff), allocAmount);
			
			return buff;
		}

		public FileBuffer decompressToMemory(BufferReference input, int allocAmount, int options) {
			if((options & CompressionDefs.DECOMP_OP_HEADERLESS) == 0){
				FileBuffer dshead = new FileBuffer(4, false);
				for(int i = 0; i < 4; i++) dshead.addToFile(Byte.toUnsignedInt(input.nextByte()));
				
				DSCompHeader chead = DSCompHeader.read(dshead, 0);
				if(allocAmount <= 0) allocAmount = chead.getDecompressedSize();
			}
			FileBuffer buff = new FileBuffer(allocAmount, false);
			FileBuffer inbuff = input.getBuffer();
			inbuff.setCurrentPosition(input.getBufferPosition());
			DSRLE.decode(new FileBufferStreamer(inbuff), new FileBufferStreamer(buff), allocAmount);
			
			return buff;
		}
		
	}
	
}
