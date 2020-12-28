package waffleoRai_Compression.lz77;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Set;

import waffleoRai_Compression.ArrayWindow;
import waffleoRai_Compression.definitions.AbstractCompDef;
import waffleoRai_Utils.ByteBufferStreamer;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.StreamWrapper;

public class LZMu {
	
	/*--- Constants ---*/
	
	public static final int BACK_WINDOW_SIZE = 0x1FFF;
	public static final int MIN_RUN_SIZE = 0x2;
	public static final int MAX_RUN_SIZE_SMALL = 0x5;
	public static final int MAX_RUN_SIZE_LARGE = 0xFF;
	
	public static final int CTRLTYPE_NONE = 0;
	public static final int CTRLTYPE_LITERAL = 1;
	public static final int CTRLTYPE_REFSHORT = 2;
	public static final int CTRLTYPE_REFLONG = 3;
	
	/*--- Instance Variables ---*/
	
	private ArrayWindow back_window;
	private StreamWrapper output;
	
	//Control stream...
	private int ctrlbyte;
	private int bitmask;
	
	private long bytesRead;
	private long bytesWritten;
	private long decompSize;
	
	/*--- General ---*/
	
	/*--- Debug ---*/
	
	private Set<Integer> offsets_short;
	private Set<Integer> offsets_long;
	private Set<Integer> runs_long;
	private int debug_counter;
	//private int last_ctrl_type;
	
	public Set<Integer> getOffsetsShort(){return offsets_short;}
	public Set<Integer> getOffsetsLong(){return offsets_long;}
	public Set<Integer> getRunsLong(){return runs_long;}
	public int getDebugCounter(){return debug_counter;}
	
	/*--- Decode ---*/
	
	public long getBytesRead(){return bytesRead;}
	
	private boolean nextControlBit(StreamWrapper input)
	{
		if(bitmask == 0)
		{
			bitmask = 0x80;
			ctrlbyte = input.getFull();
			//System.err.println("New Control Byte: 0x" + String.format("%02x", ctrlbyte) + " at 0x" + Long.toHexString(bytesRead));
			bytesRead++;
		}
		
		boolean bit = (ctrlbyte & bitmask) != 0;
		bitmask = bitmask >>> 1;
		
		return bit;
	}
	
	private void addByteToOutput(byte b)
	{
		if(bytesWritten >= decompSize) return;
		output.put(b);
		if(back_window.isFull()) back_window.pop();
		back_window.put(b);
		bytesWritten++;
		/*if(bytesWritten >= 0x3d7)
		{
			System.err.println("Compressed Position: 0x" + Long.toHexString(bytesRead));
			System.exit(2);
		}*/
		//System.err.println("Byte Added: 0x" + String.format("%02x", b));
	}
	
	private void decode(StreamWrapper input)
	{
		/*offsets_short = new TreeSet<Integer>();
		offsets_long = new TreeSet<Integer>();
		runs_long = new TreeSet<Integer>();
		debug_counter = 0;
		last_ctrl_type = CTRLTYPE_NONE;*/
		

		bytesRead = 0;
		if(input == null) return;
		back_window = new ArrayWindow(8192);
		
		ctrlbyte = input.getFull();
		bytesRead++;
		bitmask = 0x80;
		
		while(!input.isEmpty() && (bytesWritten < decompSize))
		{
			//Get the next bit of the ctrlbyte to decide what to do
			boolean bit = nextControlBit(input);
			if(bit)
			{
				//Just copy the next byte to output
				addByteToOutput(input.get());
				bytesRead++;
				//last_ctrl_type = CTRLTYPE_LITERAL;
			}
			else
			{
				//Debug
				/*if(bitmask == 0) //The last bit we read was the last of the previous ctrl byte
				{
					//debug_counter++;
					if (last_ctrl_type == CTRLTYPE_REFSHORT || last_ctrl_type == CTRLTYPE_REFLONG)
					{
						//The previous instruction was also a reference
						debug_counter++;
					}
					
					if(last_ctrl_type == CTRLTYPE_LITERAL) debug_counter++;
				}*/
				
				//Blegh
				//System.err.println("ref");
				bit = nextControlBit(input);
				if(bit)
				{
					//Okay. So this is annoying.
					//I think it's 13 bits for offset (wow)
					//Then 3 bits (+2) for n...
					//Or if the next 3 bits are all 0, then the next byte (+1)
					int b0 = (int)input.get(); bytesRead++;
					int b1 = Byte.toUnsignedInt(input.get()); bytesRead++;
					b0 = b0 << 5;
					b0 |= (b1 >>> 3) & 0x1F;
					int backset = ~b0 & 0x1FFF;
					int n = (b1 & 0x7);
					if(n == 0) {n = input.getFull() + 1; bytesRead++;}
					else n += 2;
					for(int i = 0; i < n; i++)
					{
						byte b = back_window.getFromBack(backset);
						addByteToOutput(b);
					}
					//offsets_long.add(backset);
					//runs_long.add(n);
					//last_ctrl_type = CTRLTYPE_REFLONG;
				}
				else
				{
					//Next two bits are number to copy -2
					//Next byte is backset
					int n = 2;
					if(nextControlBit(input))n+=2;
					if(nextControlBit(input))n+=1;
					
					int backset = (int)input.get(); //It's negative
					bytesRead++;
					backset = ~backset & 0xFF;
					//System.err.println("\tBackset: " + backset);
					//System.err.println("\tn: " + n);
					for(int i = 0; i < n; i++)
					{
						byte b = back_window.getFromBack(backset);
						addByteToOutput(b);
					}
					//offsets_short.add(backset);
					//last_ctrl_type = CTRLTYPE_REFSHORT;
				}
			}
			
		}
		
	}
	
	public StreamWrapper decode(StreamWrapper input, int allocate)
	{
		decompSize = allocate;
		output = new ByteBufferStreamer(allocate); //Init Output
		decode(input);
		
		output.rewind();
		return output;
	}
	
	/*--- Encode ---*/
	
	//private int debug_ct;
	
	private boolean longEncode(int back_off, int run_len)
	{
		if(back_off > 0xFF)
		{
			//Can't be short
			//At least 3 bytes for run length
			if(run_len < 3) return false;
			return true;
		}
		else
		{
			//Only if run length is more than 5
			return (run_len > 5);
		}
	}
	
	private void writeEncode(int ctrlchar, LinkedList<Byte> outbuffer)
	{
		//debug_ct++;
		output.put((byte)ctrlchar); bytesWritten++;
		while(!outbuffer.isEmpty()) {output.put(outbuffer.pop()); bytesWritten++;}
		//System.err.println("Group written. Control Byte: 0x" + String.format("%02x", ctrlchar) + " Position: 0x" + Long.toHexString(bytesWritten));
		//if(debug_ct >= 2) System.exit(2);
	}
	
	private void encode(StreamWrapper in)
	{
		LinkedList<Byte> outbuffer = new LinkedList<Byte>();
		decompSize = 0;
		bytesWritten = 0;
		
		ArrayWindow f_win = new ArrayWindow(MAX_RUN_SIZE_LARGE);
		ArrayWindow b_win = new ArrayWindow(BACK_WINDOW_SIZE);
		
		//Populate front window
		while(!f_win.isFull())
		{
			if(in.isEmpty()) break;
			f_win.put(in.get());
			decompSize++;
		}
		//System.err.println("Front window initial fill done. Read: 0x" + Long.toHexString(decompSize));
		
		int ctrlchar = 0;
		int bitmask = 0x80;
		boolean remaining = true;
		while(remaining)
		{
			//Determine longest run...
			
			if(f_win.isEmpty())
			{
				remaining = false;
				//Finish up this ctrl char with 0 copies.
				while(bitmask != 0)
				{
					ctrlchar |= bitmask;
					b_win.put((byte)0);
					outbuffer.add((byte)0);
					
					bitmask = bitmask >>> 1;
					if(bitmask == 0) writeEncode(ctrlchar, outbuffer);
				}
				continue;
			}
			
			int[] run_lens = new int[BACK_WINDOW_SIZE];
			int b_win_size = b_win.getSize();
			//System.err.println("b_win_size = 0x" + Integer.toHexString(b_win_size));
			//System.err.println("f_win_size = 0x" + Integer.toHexString(f_win.getSize()));
			for(int i = 0; i < b_win_size; i++)
			{
				//System.err.println("back i = " + i);
				f_win.rewind();
				int b_pos = i;
				int f_pos = 0;
				int ct = 0;
				
				byte bb = b_win.getFromBack(b_pos);
				byte fb = f_win.get();
				//System.err.println("\tbb = 0x" + String.format("%02x", bb));
				//System.err.println("\tfb = 0x" + String.format("%02x", fb));
				while(bb == fb)
				{
					ct++;
					if(!f_win.canGet()) break;
					fb = f_win.get();
					
					if(b_pos > 0) bb = b_win.getFromBack(--b_pos);
					else bb = f_win.getFromFront(f_pos++);
				}
				run_lens[i] = ct;
			}
			f_win.rewind();
			
			int max_run = 0;
			int max_idx = -1;
			for(int j = run_lens.length -1; j >= 0; j--)
			{
				if (run_lens[j] >= max_run)
				{
					max_run = run_lens[j];
					max_idx = j;
				}
			}
			//System.err.println("\tmax_run = 0x" + Integer.toHexString(max_run));
			//System.err.println("\tmax_idx = 0x" + Integer.toHexString(max_idx));
			
			if(max_run < MIN_RUN_SIZE || (max_idx > 0xFF && max_run < 3))
			{
				//No runs found (that were long enough)!
				ctrlchar |= bitmask;
				byte b = f_win.pop();
				b_win.put(b);
				outbuffer.add(b);
				
				bitmask = bitmask >>> 1;
				if(bitmask == 0)
				{
					writeEncode(ctrlchar, outbuffer);
					bitmask = 0x80; ctrlchar = 0;
				}
			}
			else
			{
				//if(max_idx >= 0xFF || max_run > MAX_RUN_SIZE_SMALL)
				if(longEncode(max_idx, max_run))
				{
					//Write 01 to ctrlchar
					bitmask = bitmask >>> 1;
					if(bitmask == 0)
					{
						writeEncode(ctrlchar, outbuffer);
						bitmask = 0x80; ctrlchar = 0;
					}
					
					ctrlchar |= bitmask;
					
					//Encode back ref
					int word1 = (~max_idx & 0x1FFF) << 3;
					if(max_run <= 9) word1 |= (max_run - 2);
					
					outbuffer.add((byte)((word1 >>> 8) & 0xFF));
					outbuffer.add((byte)(word1 & 0xFF));
					if(max_run > 9) outbuffer.add((byte)(max_run-1));
					
					//Advance bitmask and dump if needed
					bitmask = bitmask >>> 1;
					if(bitmask == 0)
					{
						writeEncode(ctrlchar, outbuffer);
						bitmask = 0x80; ctrlchar = 0;
					}
				}
				else
				{
					//Write ctrl char bits...
					//First 0...
					//System.err.println("bitmask: " + String.format("%02x", bitmask));
					bitmask = bitmask >>> 1;
					if(bitmask == 0)
					{
						writeEncode(ctrlchar, outbuffer);
						bitmask = 0x80; ctrlchar = 0;
					}
					//Second 0...
					//System.err.println("bitmask: " + String.format("%02x", bitmask));
					bitmask = bitmask >>> 1;
					if(bitmask == 0)
					{
						writeEncode(ctrlchar, outbuffer);
						bitmask = 0x80; ctrlchar = 0;
					}
					
					int n = max_run - 2;
					//System.err.println("n = " + n);
					//System.err.println("bitmask: " + String.format("%02x", bitmask));
					if((n & 0x2) != 0) ctrlchar |= bitmask;
					bitmask = bitmask >>> 1;
					if(bitmask == 0)
					{
						writeEncode(ctrlchar, outbuffer);
						bitmask = 0x80; ctrlchar = 0;
					}
					
					//System.err.println("bitmask: " + String.format("%02x", bitmask));
					if((n & 0x1) != 0) ctrlchar |= bitmask;
					bitmask = bitmask >>> 1;
					
					//Write offset
					int off = ~max_idx;
					outbuffer.add((byte)off);
					//System.err.println("off : " + String.format("%02x", (byte)off));
					
					if(bitmask == 0)
					{
						writeEncode(ctrlchar, outbuffer);
						bitmask = 0x80; ctrlchar = 0;
					}
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
				f_win.forcePut(in.get());
				decompSize++;
			}
			
		}
	}
	
	public long getLastDecompressedSize()
	{
		return this.decompSize;
	}
	
	public long getBytesWritten()
	{
		return this.bytesWritten;
	}
	
	public StreamWrapper encode(StreamWrapper input, int allocate)
	{
		decompSize = 0;
		output = new ByteBufferStreamer(allocate);
		encode(input);
		
		output.rewind();
		return output;
	}
	
	/*--- Definitions ---*/
	
	public static final int DEF_ID = 0xb12c7a90;
	public static final String DEFO_ENG_STR = "Lempel-Ziv 77 Compression - PS1 Variant Mu";
	
	private static LZMuDef stat_def;
	
	public static class LZMuDef extends AbstractCompDef{

		protected LZMuDef() {
			super(DEFO_ENG_STR);
			super.extensions.add("lz");
		}

		public StreamWrapper decompress(StreamWrapper input) {
			//Assumes this is a full file, so checks for size in first word
			int dec_sz = -1;
			for(int i = 0; i < 4; i++){
				int shamt = i << 3;
				int b = input.getFull();
				b = (b & 0xFF) << shamt;
				dec_sz |= b;
			}
			
			LZMu decer = new LZMu();
			return decer.decode(input, dec_sz);
		}

		public String decompressToDiskBuffer(StreamWrapper input) {
			//Because this is a PS1 compression routine, assumes output is small enough to hold in mem...
			
			StreamWrapper out = decompress(input);
			
			//Write to a temp file.
			try {
				String tpath = FileBuffer.generateTemporaryPath("lzmu_def_autodecomp");
				BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(tpath));
				while(!out.isEmpty()) bos.write(out.getFull());
				bos.close();
				return tpath;
			} 
			catch (IOException e) {
				e.printStackTrace();
			}
			
			return null;
		}

		public int getDefinitionID() {return DEF_ID;}
		
	}
	
	public static LZMuDef getDefinition(){
		if(stat_def == null) stat_def = new LZMuDef();
		return stat_def;
	}
	
}