package waffleoRai_Compression.lz77;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

import waffleoRai_Compression.ArrayWindow;
import waffleoRai_Compression.definitions.AbstractCompDef;
import waffleoRai_Compression.lz77.LZCompCore.RunMatch;
import waffleoRai_Utils.ByteBufferStreamer;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.StreamWrapper;

/*
 * Encoding Scheme...
 * I think of it as a "control stream" and "data stream" interleaved.
 * If the decoder needs another control bit, then the next byte pulled is
 * part of the control stream. If the decoder needs data, then the next
 * byte pulled is part of the data stream.
 * 
 * The control stream is interpreted one bit at a time.
 * If the first bit pulled for an instruction is set, this means copy the next
 * byte from the data stream to the output stream. If it is unset, this is
 * a backreference. The reference may be encoded using a combination of the
 * control and data streams.
 * 
 * Commands...
 * [1] - Copy next byte as-is.
 * 
 * [01] - Long reference encoding
 * Pulls 2-3 bytes from data stream to determine offset/runlen
 * oooo oooo oooo olll (llll llll)
 * If lll in the second byte is 0, then read the third byte.
 * 
 * offset = (b0 << 5) | (b1 >> 3)
 * Offsets will be negative numbers, they are relative to the output stream position.
 * 
 * if ((b1 & 0x7) != 0)
 * 	len = (b1 & 0x7) + 2
 * else
 * 	len = b2 + 1
 * This means theoretically the shortest run that can be encoded is 1, but obviously
 * that would be stupid to use three data bytes to encode one.
 * Min length is 3 for the 2 byte variation.
 * Practically, min length is probably 4 for the 3 byte variation.
 * 
 * [00nn] - Short reference encoding
 * Pulls one byte from data stream to determine offset.
 * Runlen is encoded in the command in the control stream (as n above)
 * 	len = nn + 2 (Range is 2-5)
 * 	offset = b0
 * Offset is also negative here
 * 
 */

public class LZMu {
	
	/*--- Constants ---*/
	
	//public static final int BACK_WINDOW_SIZE = 0x1FFF;
	public static final int BACK_WINDOW_SIZE = 0x1000; //If treated as signed 13 bit, then -4096 is most negative value
	public static final int MIN_RUN_SIZE = 0x2;
	public static final int MAX_RUN_SIZE_SMALL = 0x5;
	public static final int MAX_RUN_SIZE_LARGE = 0xFF;
	
	public static final int CTRLTYPE_NONE = 0;
	public static final int CTRLTYPE_LITERAL = 1;
	public static final int CTRLTYPE_REFSHORT = 2;
	public static final int CTRLTYPE_REFLONG = 3;
	
	public static final int COMP_STRAT_DEFAULT = 0;
	public static final int COMP_STRAT_FAST = 1;
	public static final int COMP_LOOKAHEAD_SCANALL_GREEDY = 2;
	
	/*--- Instance Variables ---*/
	
	private int comp_appr = COMP_STRAT_DEFAULT;
	private int comp_lookahead = 2;
	
	private ArrayWindow back_window;
	private StreamWrapper output;
	
	private ByteBuffer dataBuffer;
	private FileBuffer output_fb;
	
	//Control stream...
	private int ctrlbyte;
	private int bitmask;
	
	private long bytesRead;
	private long bytesWritten;
	private long decompSize;
	
	/*--- General ---*/
	
	/*--- Debug ---*/
	
	private LZCompressionEvent dbg_last_event; //For combining literals easier
	private List<LZCompressionEvent> dbg_events;
	
	/*private Set<Integer> offsets_short;
	private Set<Integer> offsets_long;
	private Set<Integer> runs_long;
	private int debug_counter;
	//private int last_ctrl_type;
	
	public Set<Integer> getOffsetsShort(){return offsets_short;}
	public Set<Integer> getOffsetsLong(){return offsets_long;}
	public Set<Integer> getRunsLong(){return runs_long;}
	public int getDebugCounter(){return debug_counter;}*/
		
	private void debug_logEvent(LZCompressionEvent event){
		if(dbg_events == null) dbg_events = new LinkedList<LZCompressionEvent>();
		dbg_events.add(event);
		dbg_last_event = event;
	}
	
	private void debug_logLiteralEvent(long event_addr, int amt){
		if(dbg_last_event != null && !dbg_last_event.isReference()){
			dbg_last_event.setCopyAmount(dbg_last_event.getCopyAmount() + amt);
		}
		else{
			LZCompressionEvent event = new LZCompressionEvent();
			event.setIsReference(false);
			event.setCopyAmount(amt);
			event.setPlaintextPosition(event_addr);
			debug_logEvent(event);
		}
	}
	
	private void debug_logReferenceEvent(long event_addr, long ref_addr, int amt){
		LZCompressionEvent event = new LZCompressionEvent();
		event.setIsReference(true);
		event.setCopyAmount(amt);
		event.setPlaintextPosition(event_addr);
		event.setRefPosition(ref_addr);
		debug_logEvent(event);
	}
	
	public void debug_dumpLoggedEvents(String outpath) throws IOException{
		BufferedWriter bw = new BufferedWriter(new FileWriter(outpath));
		for(LZCompressionEvent event : dbg_events){
			event.printTo(bw);
			bw.write("\n");
		}
		bw.close();
		dbg_events.clear();
	}
	
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
	
	private void decode(StreamWrapper input){
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
		
		while(!input.isEmpty() && (bytesWritten < decompSize)){
			//Get the next bit of the ctrlbyte to decide what to do
			boolean bit = nextControlBit(input);
			if(bit){
				/*--- DEBUG LOG ---*/
				debug_logLiteralEvent(bytesWritten, 1);
				/*--- DEBUG LOG ---*/
				
				//Just copy the next byte to output
				addByteToOutput(input.get());
				bytesRead++;
				//last_ctrl_type = CTRLTYPE_LITERAL;
			}
			else{
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
				if(bit){
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
					
					/*--- DEBUG LOG ---*/
					long mypos = bytesWritten - n;
					long backpos = mypos - backset - 1;
					debug_logReferenceEvent(mypos, backpos, n);
					/*--- DEBUG LOG ---*/
					
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
					for(int i = 0; i < n; i++){
						byte b = back_window.getFromBack(backset);
						addByteToOutput(b);
					}
					//offsets_short.add(backset);
					//last_ctrl_type = CTRLTYPE_REFSHORT;
					
					/*--- DEBUG LOG ---*/
					long mypos = bytesWritten - n;
					long backpos = mypos - backset - 1;
					debug_logReferenceEvent(mypos, backpos, n);
					/*--- DEBUG LOG ---*/
					
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
	
	public static class MuLZCompCore extends LZCompCore{

		public MuLZCompCore(){
			super.min_run_len = MIN_RUN_SIZE;
			super.max_run_len = MAX_RUN_SIZE_LARGE;
			super.max_back_len = BACK_WINDOW_SIZE;
		}
		
		protected boolean currentMatchEncodable() {
			long backoff = super.current_pos - super.match_pos;
			if(super.match_run < 3){
				//Can't be more than 128 bytes back.
				//Byte before current pos is -1
				if(backoff > 128) return false;
			}
			if(backoff > 4096) return false;
			if(match_run > 255) return false;
			
			return true;
		}

		protected boolean matchEncodable(RunMatch match) {
			long backoff = match.encoder_pos - match.match_pos;
			if(match.match_run < 3){
				//Can't be more than 128 bytes back.
				//Byte before current pos is -1
				if(backoff > 128) return false;
			}
			if(backoff > 4096) return false;
			if(match.match_run > 255) return false;
			
			return true;
		}
		
	}
	
	public long getLastDecompressedSize(){
		return this.decompSize;
	}
	
	public long getBytesWritten(){
		return this.bytesWritten;
	}
	
	public void setCompressionStrategy(int val){this.comp_appr = val;}
	public void setCompressionLookahead(int bytes){this.comp_lookahead = bytes;}
	
	private void dumpDataBuffer(){
		dataBuffer.rewind();
		while(dataBuffer.hasRemaining()){
			output_fb.addToFile(dataBuffer.get());
			bytesWritten++;
		}
		dataBuffer.clear();
	}
	
	private void writeControlBit(boolean bit){
		if(bit) ctrlbyte |= bitmask;
		bitmask >>= 1;
		if(bitmask < 1){
			//Dump
			output_fb.addToFile((byte)ctrlbyte);
			bytesWritten++;
			dumpDataBuffer();
			
			ctrlbyte = 0;
			bitmask = 0x80;
		}
	}
	
	private void writeDataByte(byte b){
		dataBuffer.put(b);
	}
	
	private void encodeNext(FileBuffer input, MuLZCompCore compressor){
		int runlen = compressor.getMatchRun();
		if(runlen < 2){
			//Copy as is
			writeControlBit(true);
			writeDataByte(input.getByte(compressor.current_pos));
			
			/*--- DEBUG LOG ---*/
			debug_logLiteralEvent(compressor.current_pos, 1);
			/*--- DEBUG LOG ---*/
		}
		else{
			/*--- DEBUG LOG ---*/
			debug_logReferenceEvent(compressor.current_pos, compressor.match_pos, compressor.match_run);
			/*--- DEBUG LOG ---*/
			
			//Backref
			int pdiff = (int)(compressor.current_pos - compressor.match_pos);
			if(runlen <= 5){
				//See if short encoding is an option
				//Offset must be <= 128 in magnitude.
				if(pdiff <= 128){
					writeControlBit(false);
					writeControlBit(false);
					
					runlen -= 2;
					writeControlBit((runlen & 0x2)!= 0);
					writeControlBit((runlen & 0x1)!= 0);
					
					writeDataByte((byte)(-pdiff));
					
					return;
				}
			}
			
			//Long reference
			writeControlBit(false);
			writeControlBit(true);
			
			int b0 = ((-pdiff) >> 5) & 0xff; //sra
			int b1 = ((-pdiff) << 3) & 0xff;
			writeDataByte((byte)b0);
			if(runlen > 9){
				//Need third byte
				writeDataByte((byte)b1);
				writeDataByte((byte)runlen);
			}
			else{
				runlen -= 2;
				b1 |= (runlen & 0x7);
				writeDataByte((byte)b1);
			}
		}
	}
	
	private void encodeNext(FileBuffer input, RunMatch match){
		//This one needs to read the match's copy amount and encode all those bytes too.
		for(int i = 0; i < match.copy_amt; i++){
			writeControlBit(true);
			writeDataByte(input.getByte(match.encoder_pos + i));
		}
		
		/*--- DEBUG LOG ---*/
		if(match.copy_amt > 0){
			debug_logLiteralEvent(match.encoder_pos, match.copy_amt);
		}
		/*--- DEBUG LOG ---*/
	
		int runlen = match.match_run;
		if(runlen < 2){
			//Copy as is
			writeControlBit(true);
			writeDataByte(input.getByte(match.encoder_pos));
			
			/*--- DEBUG LOG ---*/
			debug_logLiteralEvent(match.encoder_pos, 1);
			/*--- DEBUG LOG ---*/
		}
		else{
			/*--- DEBUG LOG ---*/
			debug_logReferenceEvent(match.encoder_pos, match.match_pos, match.match_run);
			/*--- DEBUG LOG ---*/
			
			//Backref
			int pdiff = (int)(match.encoder_pos - match.match_pos);
			if(runlen <= 5){
				//See if short encoding is an option
				//Offset must be <= 128 in magnitude.
				if(pdiff <= 128){
					writeControlBit(false);
					writeControlBit(false);
					
					runlen -= 2;
					writeControlBit((runlen & 0x2)!= 0);
					writeControlBit((runlen & 0x1)!= 0);
					
					writeDataByte((byte)(-pdiff));
					
					return;
				}
			}
			
			//Long reference
			writeControlBit(false);
			writeControlBit(true);
			
			int b0 = ((-pdiff) >> 5) & 0xff; //sra
			int b1 = ((-pdiff) << 3) & 0xff;
			writeDataByte((byte)b0);
			if(runlen > 9){
				//Need third byte
				writeDataByte((byte)b1);
				writeDataByte((byte)runlen);
			}
			else{
				runlen -= 2;
				b1 |= (runlen & 0x7);
				writeDataByte((byte)b1);
			}
		}
	}
	
	public FileBuffer encode(FileBuffer input){
		if(input == null) return null;
		int alloc = (int)input.getFileSize();
		output_fb = new FileBuffer(alloc, false);
		output_fb.addToFile(alloc); //First word is decomped size
		this.decompSize = alloc;
		
		bytesRead = 0;
		bytesWritten = 4;
		ctrlbyte = 0;
		bitmask = 0x80;
		
		MuLZCompCore compressor = new MuLZCompCore();
		compressor.setData(input);
		
		dataBuffer = ByteBuffer.allocate(128);
		
		if(comp_appr == COMP_LOOKAHEAD_SCANALL_GREEDY){
			//FOR set of lookahead bytes until end...
			while(!compressor.atEnd()){
				List<RunMatch> finds = compressor.findMatchesLookaheadGreedy(comp_lookahead);
				for(RunMatch match : finds){
					encodeNext(input, match);
				}
				compressor.incrementPos(comp_lookahead);
				bytesRead += comp_lookahead;
				if(bytesRead >= decompSize) bytesRead = decompSize;
			}
		}
		else if(comp_appr == COMP_STRAT_FAST){
			while(!compressor.atEnd()){
				//Find match
				compressor.firstMeetsReq();
				bytesRead++;
				
				//Encode
				encodeNext(input, compressor);
				compressor.incrementPos(1);
			}
		}
		else{
			//Default
			while(!compressor.atEnd()){
				//Find match
				compressor.findMatchCurrentPosOnly();
				bytesRead++;
				
				//Encode
				encodeNext(input, compressor);
				compressor.incrementPos(1);
			}
		}
		
		return output_fb;
	}
	
	/*--- Encode Old ---*/
	
	//private int debug_ct;
	
	private boolean longEncode_old(int back_off, int run_len){
		if(back_off > 0xFF){
			//Can't be short
			//At least 3 bytes for run length
			if(run_len < 3) return false;
			return true;
		}
		else{
			//Only if run length is more than 5
			return (run_len > 5);
		}
	}
	
	private void writeEncode_old(int ctrlchar, LinkedList<Byte> outbuffer){
		//debug_ct++;
		output.put((byte)ctrlchar); bytesWritten++;
		while(!outbuffer.isEmpty()) {output.put(outbuffer.pop()); bytesWritten++;}
		//System.err.println("Group written. Control Byte: 0x" + String.format("%02x", ctrlchar) + " Position: 0x" + Long.toHexString(bytesWritten));
		//if(debug_ct >= 2) System.exit(2);
	}
	
	private void encode_old(StreamWrapper in){
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
					if(bitmask == 0) writeEncode_old(ctrlchar, outbuffer);
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
					writeEncode_old(ctrlchar, outbuffer);
					bitmask = 0x80; ctrlchar = 0;
				}
			}
			else
			{
				//if(max_idx >= 0xFF || max_run > MAX_RUN_SIZE_SMALL)
				if(longEncode_old(max_idx, max_run))
				{
					//Write 01 to ctrlchar
					bitmask = bitmask >>> 1;
					if(bitmask == 0)
					{
						writeEncode_old(ctrlchar, outbuffer);
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
						writeEncode_old(ctrlchar, outbuffer);
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
						writeEncode_old(ctrlchar, outbuffer);
						bitmask = 0x80; ctrlchar = 0;
					}
					//Second 0...
					//System.err.println("bitmask: " + String.format("%02x", bitmask));
					bitmask = bitmask >>> 1;
					if(bitmask == 0)
					{
						writeEncode_old(ctrlchar, outbuffer);
						bitmask = 0x80; ctrlchar = 0;
					}
					
					int n = max_run - 2;
					//System.err.println("n = " + n);
					//System.err.println("bitmask: " + String.format("%02x", bitmask));
					if((n & 0x2) != 0) ctrlchar |= bitmask;
					bitmask = bitmask >>> 1;
					if(bitmask == 0)
					{
						writeEncode_old(ctrlchar, outbuffer);
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
						writeEncode_old(ctrlchar, outbuffer);
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
		
	public StreamWrapper encode_old(StreamWrapper input, int allocate){
		decompSize = 0;
		output = new ByteBufferStreamer(allocate);
		encode_old(input);
		
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
			int dec_sz = 0;
			for(int i = 0; i < 4; i++){
				int shamt = i << 3;
				int b = input.getFull();
				b = (b & 0xFF) << shamt;
				dec_sz |= b;
				//System.err.println("Dec Size: 0x" + Integer.toHexString(dec_sz));
				//System.err.println("b: 0x" + Integer.toHexString(b));
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