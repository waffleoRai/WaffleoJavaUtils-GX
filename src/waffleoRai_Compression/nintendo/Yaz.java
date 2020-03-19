package waffleoRai_Compression.nintendo;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedList;

import waffleoRai_Compression.ArrayWindow;
import waffleoRai_Utils.ByteBufferStreamer;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileInputStreamer;
import waffleoRai_Utils.FileOutputStreamer;
import waffleoRai_Utils.StreamWrapper;

public class Yaz {
	
	/*--- Constants ---*/
	
	public static final int MAGIC = 0x59617A30;
	public static final String MAGIC_STR = "Yaz0";
	public static final byte[] MAGIC_BYTES = {0x59, 0x61, 0x7a, 0x30};
	
	public static final int BACK_WINDOW_SIZE = 0xFFF;
	public static final int MIN_RUN_SIZE_SMALL = 0x3;
	public static final int MAX_RUN_SIZE_SMALL = 0x11;
	public static final int MIN_RUN_SIZE_LARGE = 0x12;
	public static final int MAX_RUN_SIZE_LARGE = 0x111;
	
	/*--- Instance Variables ---*/
	
	private LinkedList<String> tempfiles;
	private LinkedList<Byte> outbuffer;
	private ArrayWindow back_window;
	
	private int decomp_size;
	private StreamWrapper output;
	
	private boolean allow_3byte_subs;
	private boolean reverse_flags;
	
	/*--- General ---*/
	
	public Yaz()
	{
		back_window = new ArrayWindow(BACK_WINDOW_SIZE);
		tempfiles = new LinkedList<String>();
		outbuffer = new LinkedList<Byte>();
		allow_3byte_subs = true;
	}

	protected void reset()
	{
		try{clearTempData();}
		catch(Exception e){e.printStackTrace();}
		//position = 0;
		back_window = new ArrayWindow(BACK_WINDOW_SIZE);
		outbuffer.clear();
		output = null;
		decomp_size = -1;
	}
	
	protected void dumpOutputWindow(boolean full)
	{
		//Dumps front of the output window to output so the window doesn't get too big
		if(full)
		{
			while(!outbuffer.isEmpty()) output.put(outbuffer.pop());
			return;
		}
		int sz = outbuffer.size();
		int movecount = sz - BACK_WINDOW_SIZE;
		if(sz > 0)
		{
			for(int i = 0; i < movecount; i++) output.put(outbuffer.pop());
		}
	}
	
	public int getDecompressedSize()
	{
		return decomp_size;
	}
	
	protected void setOutputTarget(StreamWrapper trg)
	{
		output = trg;
	}

	public static int readYazHeader(StreamWrapper src)
	{
		//Looks for Yaz header and returns the decompressed file size
		//If header is present, header bytes are chopped off
		
		//If header is not present, this function returns -1
		if(src == null) return -1;
		
		//Look for header MAGIC
		byte[] mag = new byte[MAGIC_BYTES.length];
		for(int i = 0; i < MAGIC_BYTES.length; i++)
		{
			mag[i] = src.get();
			if(mag[i] != MAGIC_BYTES[i])
			{
				//Push all read bytes back
				//Return -1
				//for(int j = i; j >= 0; j--) src.push(mag[j]);
				return -1;
			}
		}
		//Assumed magic no match
		//Read next field
		int sz = 0;
		for(int i = 0; i < 4; i++)
		{
			sz = sz << 8;
			sz |= src.getFull();
		}
		
		//Skip 8 bytes (remainder of header)
		for(int i = 0; i < 8; i++) src.get();
		
		return sz;
	}
	
	public void setAllow3Bytes(boolean b)
	{
		allow_3byte_subs = b;
	}
	
	public void setReverseFlags(boolean b)
	{
		this.reverse_flags = b;
	}
	
	/*--- Decode ---*/
	
	protected int decodeNextGroup(StreamWrapper in, int header)
	{
		//System.err.println("New Group! Header Byte: " + String.format("%02x", header));
		int mask = 0x80;
		int read = 0;
		for(int i = 0; i < 8; i++)
		{
			boolean flag = ((header & mask) != 0);
			if(reverse_flags) flag = !flag;
			if(flag)
			{
				//Copy next byte as is
				byte b = in.get();
				output.put(b);
				if(back_window.isFull()) back_window.pop();
				back_window.put(b);
				decomp_size++;
				read++;
				//System.err.println("\tFlag set. Copy byte: " + String.format("%02x", b));
			}
			else
			{
				//RLE nonsense
				//System.err.println("\tFlag cleared. RLE time!");
				int b0 = in.getFull();
				int b1 = in.getFull();
				read+=2;
				//Get backpos
				int backpos = (b0 & 0xF) << 8;
				backpos |= b1;
				//Get byte number
				int bcount = (b0 & 0xF0) >>> 4;
				if(allow_3byte_subs)
				{
					if(bcount == 0)
					{
						bcount = in.getFull() + 0x12;
						read++;
					}
					else bcount += 2;
				}
				else bcount += 3;
				//System.err.println("\t\tCopying " + bcount + " bytes from " + backpos + " back...");
				
				for (int j = 0; j < bcount; j++)
				{
					//System.err.println("j = " + j);
					byte b = back_window.getFromBack(backpos);
					output.put(b);
					if(back_window.isFull()) back_window.pop();
					back_window.put(b);
					decomp_size++;
				}
				
				/*int winsz = outwindow.size();
				ListIterator<Byte> witr = outwindow.listIterator(winsz-backpos-1);
				for(int j = 0; j < bcount; j++)
				{
					outwindow.add(witr.next());
				}*/
				
			}
			mask = mask >>> 1;
			if(in.isEmpty()) return read;
		}
		//dumpOutputWindow(false);
		return read;
	}
	
	private void decode(StreamWrapper in)
	{
		while(!in.isEmpty())
		{
			byte b = in.get();
			decodeNextGroup(in, Byte.toUnsignedInt(b));
		}
		//dumpOutputWindow(true);
	}
	
	public StreamWrapper decode(ByteBuffer input, int allocate)
	{
		reset();

		ByteBufferStreamer in = ByteBufferStreamer.wrap(input); //Init Input
		output = new ByteBufferStreamer(allocate); //Init Output
		
		decode(in);
		output.rewind();
		return output;
	}
	
	public StreamWrapper decode(FileBuffer input, int allocate)
	{
		reset();

		ByteBufferStreamer in = ByteBufferStreamer.wrap(input); //Init Input
		output = new ByteBufferStreamer(allocate); //Init Output
		
		decode(in);
		output.rewind();
		return output;
	}
	
	public StreamWrapper decode(InputStream input) throws IOException
	{
		reset();
		
		FileInputStreamer in = new FileInputStreamer(input);
		String temppath = FileBuffer.generateTemporaryPath("yaz_decode");
		output = new FileOutputStreamer(temppath);
		tempfiles.add(temppath);
		
		decode(in);
		
		output.close();
		
		//Read back in
		output = new FileInputStreamer(temppath);
		
		return output;
	}
	
	public StreamWrapper decode(StreamWrapper input, int allocate)
	{
		output = new ByteBufferStreamer(allocate); //Init Output
		decode(input);
		
		return output;
	}
	
	public boolean decodeTo(StreamWrapper input, OutputStream output)
	{
		if(output == null) return false;
		if(input == null) return false;
		this.output = new FileOutputStreamer(output);
		decode(input);
		
		return true;
	}
	
	/*--- Encode ---*/
	
	private void encode(StreamWrapper in)
	{
		outbuffer = new LinkedList<Byte>();
		decomp_size = 0;
		
		ArrayWindow f_win = new ArrayWindow(MAX_RUN_SIZE_LARGE);
		if(!this.allow_3byte_subs) f_win = new ArrayWindow(MAX_RUN_SIZE_SMALL);
		ArrayWindow b_win = new ArrayWindow(BACK_WINDOW_SIZE);
		
		//Populate front window
		while(!f_win.isFull())
		{
			if(in.isEmpty()) break;
			f_win.put(in.get());
			decomp_size++;
		}
		
		boolean remaining = true;
		while(remaining)
		{
			int gHeader = 0;
			for(int c = 0; c < 8; c++)
			{
				gHeader = gHeader << 1;
				
				//Check to see if forward window is empty
				if(f_win.isEmpty())
				{
					remaining = false;
					//Add a 0 byte and continue
					gHeader |= 0x1;
					outbuffer.add((byte)0);
					continue;
				}
				
				//Determine best run
				int[] run_lens = new int[BACK_WINDOW_SIZE];
				int b_win_size = b_win.getSize();
				for(int i = 0; i < b_win_size; i++)
				{
					f_win.rewind();
					int b_pos = i;
					int f_pos = 0;
					int ct = 0;
					
					byte bb = b_win.getFromBack(b_pos);
					byte fb = f_win.get();
					while(bb == fb)
					{
						ct++;
						if(!f_win.canGet()) break;
						fb = f_win.get();
						
						if(b_pos > 0) bb = b_win.getFromBack(--b_pos);
						else fb = f_win.getFromFront(f_pos++);
					}
					run_lens[i] = ct;
				}
				f_win.rewind();
				
				
				int max_run = 0;
				int max_idx = -1;
				for(int j = run_lens.length -1; j >= 0; j--)
				{
					if (run_lens[j] > max_run)
					{
						max_run = run_lens[j];
						max_idx = j;
					}
				}
				
				if(max_run < MIN_RUN_SIZE_SMALL)
				{
					//No runs found (that were long enough)!
					if(!reverse_flags) gHeader |= 0x1;
					byte b = f_win.pop();
					b_win.put(b);
					outbuffer.add(b);
				}
				else
				{
					if(reverse_flags) gHeader |= 0x1;
					if(max_run > MAX_RUN_SIZE_SMALL && allow_3byte_subs)
					{
						//Need 3 bytes
						int N = max_run - MIN_RUN_SIZE_LARGE;
						int R = max_idx;
						int b2 = (R >>> 8) & 0xF;
						int b1 = R & 0xFF;
						int b0 = N & 0xFF;
						outbuffer.add((byte)b2);
						outbuffer.add((byte)b1);
						outbuffer.add((byte)b0);
					}
					else
					{
						//2 bytes
						int N = max_run - 2;
						int R = max_idx;
						int b1 = ((N & 0xF) << 4) | ((R >>> 8) & 0xF);
						int b0 = R & 0xFF;
						outbuffer.add((byte)b1);
						outbuffer.add((byte)b0);
					}
					//Slide windows...
					for(int i = 0; i < max_run; i++)
					{
						if(b_win.isFull()) b_win.pop();
						b_win.put(f_win.pop());
					}
				}
				
				//Refill forward window
				while(!f_win.isFull())
				{
					if(in.isEmpty()) break;
					f_win.put(in.get());
					decomp_size++;
				}
			}
			
			//Dump header and outwindow to stream
			output.put((byte)gHeader);
			while(!outbuffer.isEmpty()) output.put(outbuffer.poll());
		}
		
	}
	
	public StreamWrapper encode(ByteBuffer input, int allocate)
	{
		reset();
		ByteBufferStreamer in = ByteBufferStreamer.wrap(input); //Init Input
		output = new ByteBufferStreamer(allocate); //Init Output
		
		encode(in);
		
		return output;
	}
	
	public StreamWrapper encode(FileBuffer input, int allocate)
	{
		reset();

		ByteBufferStreamer in = ByteBufferStreamer.wrap(input); //Init Input
		output = new ByteBufferStreamer(allocate); //Init Output
		
		encode(in);
		
		return output;
	}
	
	public StreamWrapper encode(InputStream input) throws IOException
	{
		reset();
		
		FileInputStreamer in = new FileInputStreamer(input);
		String temppath = FileBuffer.generateTemporaryPath("yaz_encode");
		output = new FileOutputStreamer(temppath);
		tempfiles.add(temppath);
		
		encode(in);
		
		output.close();
		
		//Read back in
		output = new FileInputStreamer(temppath);
		
		return output;
	}
	
	public void clearTempData() throws IOException
	{
		if(tempfiles.isEmpty()) return;
		for(String p : tempfiles)
		{
			Files.deleteIfExists(Paths.get(p));
		}
		tempfiles.clear();
	}
	
}
