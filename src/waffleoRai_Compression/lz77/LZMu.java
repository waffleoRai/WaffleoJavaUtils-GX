package waffleoRai_Compression.lz77;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

import waffleoRai_Compression.ArrayWindow;
import waffleoRai_Compression.definitions.AbstractCompDef;
import waffleoRai_Compression.lz77.LZCompCore.RunMatch;
import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.ByteBufferStreamer;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBufferStreamer;
import waffleoRai_Utils.FileInputStreamer;
import waffleoRai_Utils.FileOutputStreamer;
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
	public static final int MIN_RUN_SIZE = 2;
	public static final int MAX_RUN_SIZE_SMALL = 5;
	public static final int MAX_RUN_SIZE_LARGE = 256;
	
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
	private int dataBufferUsed = 0;
	
	//Control stream...
	private int ctrlbyte;
	private int bitmask;
	
	private long bytesRead;
	private long bytesWritten;
	private long decompSize;
	private long lastCtrlCharPos = -1L; //In compressed stream
	
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
	
	private void debug_logLiteralEvent(long event_addr, long comp_addr, int amt){
		if(dbg_last_event != null && !dbg_last_event.isReference()){
			dbg_last_event.setCopyAmount(dbg_last_event.getCopyAmount() + amt);
		}
		else{
			LZCompressionEvent event = new LZCompressionEvent();
			event.setIsReference(false);
			event.setCopyAmount(amt);
			event.setCompressedPosition(comp_addr);
			event.setPlaintextPosition(event_addr);
			debug_logEvent(event);
		}
	}
	
	private void debug_logReferenceEvent(long event_addr, long comp_addr, long ref_addr, int amt){
		LZCompressionEvent event = new LZCompressionEvent();
		event.setIsReference(true);
		event.setCopyAmount(amt);
		event.setPlaintextPosition(event_addr);
		event.setCompressedPosition(comp_addr);
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
	
	private boolean nextControlBit(StreamWrapper input){
		if(bitmask == 0){
			bitmask = 0x80;
			ctrlbyte = input.getFull();
			//System.err.println("New Control Byte: 0x" + String.format("%02x", ctrlbyte) + " at 0x" + Long.toHexString(bytesRead));
			lastCtrlCharPos = bytesRead + 4;
			bytesRead++;
		}
		
		boolean bit = (ctrlbyte & bitmask) != 0;
		bitmask = bitmask >>> 1;
		
		return bit;
	}
	
	private void addByteToOutput(byte b){
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
		lastCtrlCharPos = 4;
		
		while(!input.isEmpty() && (bytesWritten < decompSize)){
			//Get the next bit of the ctrlbyte to decide what to do
			boolean bit = nextControlBit(input);
			if(bit){
				/*--- DEBUG LOG ---*/
				debug_logLiteralEvent(bytesWritten, lastCtrlCharPos, 1);
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
					debug_logReferenceEvent(mypos, lastCtrlCharPos, backpos, n);
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
					debug_logReferenceEvent(mypos, lastCtrlCharPos, backpos, n);
					/*--- DEBUG LOG ---*/
					
				}
			}
			
		}
	}
	
	public StreamWrapper decode(StreamWrapper input, int allocate) {
		decompSize = allocate;
		output = new ByteBufferStreamer(allocate); //Init Output
		decode(input);
		
		output.rewind();
		return output;
	}
	
	public FileBuffer decodeToBuffer(StreamWrapper input, int allocate) {
		decompSize = allocate;
		FileBuffer buff = new FileBuffer(allocate);
		output = new FileBufferStreamer(buff); //Init Output
		decode(input);
		return buff;
	}
	
	public boolean decodeToDisk(StreamWrapper input, String path){
		for(int i = 0; i < 4; i++) input.get();
		
		try{
			BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(path));
			output = new FileOutputStreamer(bos);
			decode(input);
			bos.close();
		}
		catch(IOException ex){
			ex.printStackTrace();
			return false;
		}
		return true;
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
			if(match_run > MAX_RUN_SIZE_LARGE) return false;
			
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
			if(match.match_run > MAX_RUN_SIZE_LARGE) return false;
			
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
		while(dataBufferUsed-- > 0){
			output_fb.addToFile(dataBuffer.get());
			bytesWritten++;
		}
		dataBuffer.clear();
		dataBufferUsed = 0;
	}
	
	private boolean writeControlBit(boolean bit, boolean cmdend){
		if(bit) ctrlbyte |= bitmask;
		bitmask >>= 1;
		if(bitmask < 1){
			//Dump
			lastCtrlCharPos = output_fb.getFileSize();
			output_fb.addToFile((byte)ctrlbyte);
			bytesWritten++;
			
			//if cmdend, hold flush...
			if(!cmdend) dumpDataBuffer();
			
			ctrlbyte = 0;
			bitmask = 0x80;
			return true;
		}
		return false;
	}
	
	private void writeDataByte(byte b){
		dataBuffer.put(b);
		dataBufferUsed++;
	}
	
	private void finishWrite(){
		//TODO Fills and flushes the last command, tidies up the output
		if(bitmask == 0x80){
			//Just pad to 4
			while((output_fb.getFileSize() & 0x3) != 0) output_fb.addToFile(FileBuffer.ZERO_BYTE);
		}
		else{
			//TODO This is probably going to have to be tweaked a lot to match.
			//Command stream needs to be padded out too, I guess
			int total_size = dataBufferUsed + 1 + (int)output_fb.getFileSize();
			int data_pad = (4 - (total_size & 0x3)) & 0x3;
			int ctrl_bits_rem = 0;
			int mask = bitmask;
			while(mask > 0){
				ctrl_bits_rem++;
				mask >>>= 1;
			}
			
			if((data_pad == 3) && (ctrl_bits_rem >= 2)){
				bitmask >>>= 1;
				ctrlbyte |= bitmask;
				bitmask >>>= 1;
				for(int i = 0; i < 3; i++){
					writeDataByte((byte)0);
				}
			}
			
			output_fb.addToFile((byte)ctrlbyte);
			dumpDataBuffer();
		}
	}
	
	private void encodeNext(FileBuffer input, MuLZCompCore compressor){
		int runlen = compressor.getMatchRun();
		boolean mflush = false;
		if(runlen < 2){
			//Copy as is
			mflush = writeControlBit(true, true);
			writeDataByte(input.getByte(compressor.current_pos));
			if(mflush) dumpDataBuffer();
			
			/*--- DEBUG LOG ---*/
			debug_logLiteralEvent(compressor.current_pos, lastCtrlCharPos, 1);
			/*--- DEBUG LOG ---*/
		}
		else{
			/*--- DEBUG LOG ---*/
			debug_logReferenceEvent(compressor.current_pos, lastCtrlCharPos, compressor.match_pos, compressor.match_run);
			/*--- DEBUG LOG ---*/
			
			//Backref
			int pdiff = (int)(compressor.current_pos - compressor.match_pos);
			if(runlen <= 5){
				//See if short encoding is an option
				//Offset must be <= 128 in magnitude.
				if(pdiff <= 128){
					writeControlBit(false, false);
					writeControlBit(false, false);
					
					runlen -= 2;
					writeControlBit((runlen & 0x2)!= 0, false);
					mflush = writeControlBit((runlen & 0x1)!= 0, true);
					
					writeDataByte((byte)(-pdiff));
					if(mflush) dumpDataBuffer();
					
					return;
				}
			}
			
			//Long reference
			writeControlBit(false, false);
			mflush = writeControlBit(true, true);
			
			int b0 = ((-pdiff) >> 5) & 0xff; //sra
			int b1 = ((-pdiff) << 3) & 0xff;
			writeDataByte((byte)b0);
			if(runlen > 9){
				//Need third byte
				writeDataByte((byte)b1);
				writeDataByte((byte)(runlen-1));
			}
			else{
				runlen -= 2;
				b1 |= (runlen & 0x7);
				writeDataByte((byte)b1);
			}
			if(mflush) dumpDataBuffer();
		}
	}
	
	private void encodeNext(FileBuffer input, RunMatch match){
		//TODO mflush
		//This one needs to read the match's copy amount and encode all those bytes too.
		for(int i = 0; i < match.copy_amt; i++){
			writeControlBit(true, true);
			writeDataByte(input.getByte(match.encoder_pos + i));
		}
		
		/*--- DEBUG LOG ---*/
		if(match.copy_amt > 0){
			debug_logLiteralEvent(match.encoder_pos, lastCtrlCharPos, match.copy_amt);
		}
		/*--- DEBUG LOG ---*/
	
		int runlen = match.match_run;
		if(runlen < 2){
			//Copy as is
			writeControlBit(true, true);
			writeDataByte(input.getByte(match.encoder_pos));
			
			/*--- DEBUG LOG ---*/
			debug_logLiteralEvent(match.encoder_pos, lastCtrlCharPos, 1);
			/*--- DEBUG LOG ---*/
		}
		else{
			/*--- DEBUG LOG ---*/
			debug_logReferenceEvent(match.encoder_pos, lastCtrlCharPos, match.match_pos, match.match_run);
			/*--- DEBUG LOG ---*/
			
			//Backref
			int pdiff = (int)(match.encoder_pos - match.match_pos);
			if(runlen <= 5){
				//See if short encoding is an option
				//Offset must be <= 128 in magnitude.
				if(pdiff <= 128){
					writeControlBit(false, false);
					writeControlBit(false, false);
					
					runlen -= 2;
					writeControlBit((runlen & 0x2)!= 0, false);
					writeControlBit((runlen & 0x1)!= 0, true);
					
					writeDataByte((byte)(-pdiff));
					
					return;
				}
			}
			
			//Long reference
			writeControlBit(false, false);
			writeControlBit(true,true);
			
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
				int amt = 0;
				for(RunMatch match : finds){
					if(match.match_run > 1) amt += match.match_run;
					else amt++;
					encodeNext(input, match);
				}
				compressor.incrementPos(amt);
				bytesRead += amt;
				if(bytesRead >= decompSize) bytesRead = decompSize;
			}
		}
		else if(comp_appr == COMP_STRAT_FAST){
			while(!compressor.atEnd()){
				//Find match
				compressor.firstMeetsReq();
				
				//Determine size
				int amt = compressor.getMatchRun();
				if(amt < 2) amt = 1;
				bytesRead += amt;
				
				//Encode
				encodeNext(input, compressor);
				compressor.incrementPos(amt);
			}
		}
		else{
			//Default
			while(!compressor.atEnd()){
				//Find match
				compressor.findMatchCurrentPosOnly();
				
				//Determine size
				int amt = compressor.getMatchRun();
				if(amt < 2) amt = 1;
				bytesRead += amt;
				
				//Encode
				encodeNext(input, compressor);
				compressor.incrementPos(amt);
			}
			finishWrite();
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

		public int getDefinitionID() {return DEF_ID;}
		
		public boolean decompressToDiskBuffer(StreamWrapper input, String bufferPath, int options) {
			LZMu decer = new LZMu();
			return decer.decodeToDisk(input, bufferPath);
		}

		public boolean decompressToDiskBuffer(InputStream input, String bufferPath, int options) {
			LZMu decer = new LZMu();
			return decer.decodeToDisk(new FileInputStreamer(input), bufferPath);
		}

		public boolean decompressToDiskBuffer(BufferReference input, String bufferPath, int options) {
			LZMu decer = new LZMu();
			FileBuffer buff = input.getBuffer();
			buff.setCurrentPosition(input.getBufferPosition());
			return decer.decodeToDisk(new FileBufferStreamer(buff), bufferPath);
		}

		public FileBuffer decompressToMemory(StreamWrapper input, int allocAmount, int options) {
			int dec_sz = 0;
			for(int i = 0; i < 4; i++){
				int shamt = i << 3;
				int b = input.getFull();
				b = (b & 0xFF) << shamt;
				dec_sz |= b;
			}
			
			LZMu decer = new LZMu();
			return decer.decodeToBuffer(input, dec_sz);
		}

		public FileBuffer decompressToMemory(InputStream input, int allocAmount, int options) {
			int dec_sz = 0;
			for(int i = 0; i < 4; i++){
				int shamt = i << 3;
				int b;
				try {b = input.read();} 
				catch (IOException e) {
					e.printStackTrace();
					return null;
				}
				b = (b & 0xFF) << shamt;
				dec_sz |= b;
			}
			
			LZMu decer = new LZMu();
			return decer.decodeToBuffer(new FileInputStreamer(input), dec_sz);
		}

		public FileBuffer decompressToMemory(BufferReference input, int allocAmount, int options) {
			int dec_sz = 0;
			for(int i = 0; i < 4; i++){
				int shamt = i << 3;
				int b = Byte.toUnsignedInt(input.nextByte());
				b = (b & 0xFF) << shamt;
				dec_sz |= b;
			}
			
			LZMu decer = new LZMu();
			FileBuffer buff = input.getBuffer();
			buff.setCurrentPosition(input.getBufferPosition());
			return decer.decodeToBuffer(new FileBufferStreamer(buff), dec_sz);
		}
		
	}
	
	public static LZMuDef getDefinition(){
		if(stat_def == null) stat_def = new LZMuDef();
		return stat_def;
	}
	
}