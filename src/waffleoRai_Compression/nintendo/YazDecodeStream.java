package waffleoRai_Compression.nintendo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import waffleoRai_Utils.ByteBufferStreamer;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileInputStreamer;
import waffleoRai_Utils.LinkedBytesStreamer;
import waffleoRai_Utils.StreamWrapper;

public class YazDecodeStream extends InputStream{

	/*--- Instance Variables ---*/
	
	//private ByteBuffer buffer;
	private LinkedBytesStreamer buffer;
	
	private Yaz decoder;
	
	private StreamWrapper src;
	private boolean eos;
	
	private int expected_decomp_size;
	private long bytesRead;
	private long bytesDecoded;
	private boolean headerCheck;
	
	/*--- Construction ---*/
	
	private YazDecodeStream()
	{
		bytesRead = 0;
		//buffer = ByteBuffer.allocate(Yaz.MAX_RUN_SIZE_LARGE * 16); //Up to 2 groups
		buffer = new LinkedBytesStreamer();
		eos = false;
		decoder = new Yaz();
		//decoder.setOutputTarget(ByteBufferStreamer.wrap(buffer));
		decoder.setOutputTarget(buffer);
		headerCheck = true;
		expected_decomp_size = -1;
	}
	
	public static YazDecodeStream getDecoderStream(InputStream source)
	{
		YazDecodeStream str = new YazDecodeStream();
		str.src = new FileInputStreamer(source);
		
		return str;
	}
	
	public static YazDecodeStream getDecoderStream(FileBuffer source)
	{
		YazDecodeStream str = new YazDecodeStream();
		str.src = ByteBufferStreamer.wrap(source);
		
		return str;
	}
	
	public static YazDecodeStream getDecoderStream(ByteBuffer source)
	{
		YazDecodeStream str = new YazDecodeStream();
		str.src = ByteBufferStreamer.wrap(source);
		
		return str;
	}
	
	/*--- Yaz File Format ---*/
	
	public void setHeaderCheck(boolean b)
	{
		headerCheck = b;
	}
	
	private void readHeader(byte b0)
	{
		//System.err.println("Reading header!");
		//ByteBuffer tbuff = ByteBuffer.allocate(8*3);
		LinkedBytesStreamer tbuff = new LinkedBytesStreamer();
		//tbuff.put(b0);
		
		boolean hmatch = true;
		byte[] h = new byte[4];
		for(int i = 1; i < Yaz.MAGIC_BYTES.length; i++)
		{
			byte b = src.get();
			bytesRead++;
			tbuff.put(b);
			h[i] = b;
			
			if(b != Yaz.MAGIC_BYTES[i]) hmatch = false;
		}
		
		if(hmatch)
		{
			//System.err.println("Header found!");
			//Read the next 12 bytes as tho yaz file header
			expected_decomp_size = 0;
			for(int i = 0; i < 4; i++)
			{
				expected_decomp_size = expected_decomp_size << 8;
				expected_decomp_size |= src.getFull();
				bytesRead++;
			}
			//System.err.println("Expected decompressed size: 0x" + Integer.toHexString(expected_decomp_size));
			
			//8 reserved
			for(int i = 0; i < 8; i++)
			{
				src.get();
				bytesRead++;
			}
		}
		else
		{
			//Decode as group
			//To do that, need to read into the dummy in-buffer first
			int mask = 0x80;
			int p = -3;
			int hb = Byte.toUnsignedInt(b0);
			for(int i = 0; i < 8; i++)
			{
				if((hb & mask) != 0)
				{
					//One byte
					if(p < 0) tbuff.put(h[h.length + p]);
					else tbuff.put(src.get());
					p++;
				}
				else
				{
					//2-3 bytes
					int b1 = 0;
					if(p < 0) b1 = Byte.toUnsignedInt(h[h.length + p]);
					else b1 = src.getFull();
					p++;
					tbuff.put((byte)b1);
					
					if(p < 0) tbuff.put(h[h.length + p]);
					else tbuff.put(src.get());
					p++;
					
					if(((b1 >>> 4) & 0xF) == 0)
					{
						//It's 3 bytes
						if(p < 0) tbuff.put(h[h.length + p]);
						else tbuff.put(src.get());
						p++;
					}
					
				}
				mask = mask >>> 1;
			}
			
			//bytesRead += decoder.decodeNextGroup(ByteBufferStreamer.wrap(tbuff), hb);
			bytesRead += decoder.decodeNextGroup(tbuff, hb);
		}
		
	}
	
	/*--- Stream Methods ---*/
	
	@Override
	public int read() throws IOException 
	{
		if(eos) return -1;
		if(expected_decomp_size > 0 && (bytesDecoded >= expected_decomp_size))
		{
			eos = true;
			return -1;
		}
		if(bytesRead == 0 && headerCheck)
		{
			byte h1 = src.get();
			bytesRead++;
			//System.err.println("Header check! Byte 1: " + String.format("%02x", h1));
			if(h1 == Yaz.MAGIC_BYTES[0]) readHeader(h1);
			else
			{
				int gh = Byte.toUnsignedInt(h1);
				bytesRead += decoder.decodeNextGroup(src, gh);
				if(buffer.isEmpty()){eos = true; return -1;}
				bytesDecoded++;
				return Byte.toUnsignedInt(buffer.get());
			}
		}
		
		if(!buffer.isEmpty())
		{
			bytesDecoded++;
			return Byte.toUnsignedInt(buffer.get());
		}
		
		//Otherwise, re-buffer by decoding next group
		if(src.isEmpty()){eos = true; return -1;}
		int gh = src.getFull();
		//System.err.println("New Group! Header Byte: " + String.format("%02x", gh));
		bytesRead++;
		bytesRead += decoder.decodeNextGroup(src, gh);
		
		if(buffer.isEmpty()){eos = true; return -1;}
		bytesDecoded++;
		return Byte.toUnsignedInt(buffer.get());
	}
	
	@Override
	public int read(byte[] b) throws IOException 
	{
		if(b == null) return 0;
		int ct = 0;
		for(int i = 0; i < b.length; i++)
		{
			int ib = read();
			if(ib == -1) return ct;
			b[i] = (byte)ib;
			ct++;
		}
		
		return ct;
	}

	@Override
	public int read(byte[]b, int off, int len) throws IOException 
	{
		if(b == null) return 0;
		if(len < 1) return 0;
		
		int ct = 0;
		for(int i = 0; i < len; i++)
		{
			int ib = read();
			if(ib == -1) return ct;
			b[off+i] = (byte)ib;
			ct++;
		}
		
		return ct;
	}
	
	@Override
	public int available() 
	{
		return buffer.size();
	}
	
	public void close()
	{
		src.close();
		buffer.close();
		//buffer.clear();
		buffer = null;
		decoder.reset();
		decoder = null;
	}
	
	public synchronized void mark(int readlimit)
	{
		//Do nothing
	}
	
	public boolean markSupported()
	{
		return false;
	}
	
	public synchronized void reset()
	{
		//Do nothing
	}
	
	public long skip(long n)
	{
		long skipped = 0;
		for(long i = 0; i < n; i++)
		{
			try
			{
				int bi = read();
				if(bi == -1) return skipped;
				skipped++;
			}
			catch(IOException e)
			{
				e.printStackTrace();
				return skipped;
			}
		}
		return skipped;
	}
	
	
}
