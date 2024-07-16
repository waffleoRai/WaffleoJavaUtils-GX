package waffleoRai_Compression.lz77;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

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
 * Offset is also negative here.
 * If the highest bit of the offset value is 0, it is STILL negative when bit extended.
 * ie. 0x2c is -212
 * 
 */

public class LZMu {
	
	/*--- Constants ---*/
	
	private static final boolean DEBUG_ON = true;
	
	public static final int BACK_WINDOW_SIZE = 0x2000; //Treated as unsigned 13 bit
	//public static final int BACK_WINDOW_SIZE = 0x1000; //If treated as signed 13 bit, then -4096 is most negative value
	public static final int MIN_RUN_SIZE = 2;
	public static final int MAX_RUN_SIZE_SMALL = 5;
	public static final int MIN_RUN_SIZE_LARGE = 3;
	public static final int MAX_RUN_SIZE_LARGE = 256;
	public static final int MAX_RUN_SIZE_LARGE_2BYTE = 9;
	public static final int MAX_BACKSET_SMALL = 256;
	
	public static final int CTRLTYPE_NONE = 0;
	public static final int CTRLTYPE_LITERAL = 1;
	public static final int CTRLTYPE_REFSHORT = 2;
	public static final int CTRLTYPE_REFLONG = 3;
	
	public static final int COMP_STRAT_DEFAULT = 0;
	public static final int COMP_STRAT_FAST = 1;
	public static final int COMP_LOOKAHEAD_SCANALL_GREEDY = 2;
	public static final int COMP_LOOKAHEAD_QUICK = 3;
	public static final int COMP_LOOKAHEAD_REC = 4;
	
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
	private boolean hardBreak = false;
	
	private int overflowSize = 0;
	private byte[] overflowData;
	
	/*--- General ---*/
	
	/*--- Debug ---*/
	
	private int lastCtrlBitPos = 0;
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
	
	private void debug_logLiteralEvent(long event_addr, long comp_addr, int amt, int cbit){
		if(dbg_last_event != null && !dbg_last_event.isReference()){
			dbg_last_event.setCopyAmount(dbg_last_event.getCopyAmount() + amt);
		}
		else{
			LZCompressionEvent event = new LZCompressionEvent();
			event.setIsReference(false);
			event.setCopyAmount(amt);
			event.setCompressedPosition(comp_addr);
			event.setPlaintextPosition(event_addr);
			event.setControlBytePos(cbit);
			debug_logEvent(event);
		}
	}
	
	private void debug_logReferenceEvent(long event_addr, long comp_addr, long ref_addr, int amt, int cbit){
		LZCompressionEvent event = new LZCompressionEvent();
		event.setIsReference(true);
		event.setCopyAmount(amt);
		event.setPlaintextPosition(event_addr);
		event.setCompressedPosition(comp_addr);
		event.setRefPosition(ref_addr);
		event.setControlBytePos(cbit);
		
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
	
	public void debug_dumpLoggedEventsToMap(Map<Long, LZCompressionEvent> map) {
		if(map == null || dbg_events == null) return;
		for(LZCompressionEvent event : dbg_events){
			map.put(event.getPlaintextPosition(), event);
		}
	}
	
	/*--- Decode ---*/
	
	public int getOverflowSize(){return overflowSize;}
	public long getBytesRead(){return bytesRead;}
	
	public byte[] getOverflowContents(){
		if(overflowData == null) return null;
		if(overflowSize < 1) return null;
		byte[] data = Arrays.copyOf(overflowData, overflowSize);
		return data;
	}
	
	private boolean nextControlBit(StreamWrapper input){
		/*DEBUG*/
		//Records what the position in byte was BEFORE this bit was read.
		if(--lastCtrlBitPos < 0) {
			lastCtrlBitPos = 7;
		}
		
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
		if(bytesWritten >= decompSize){
			if(overflowData == null){
				overflowData = new byte[256];
			}
			if(overflowSize < overflowData.length){
				overflowData[overflowSize] = b;
			}
			overflowSize++;
		}
		else{
			output.put(b);
			bytesWritten++;
		}
		if(back_window.isFull()) back_window.pop();
		back_window.put(b);
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
		overflowSize = 0;
		overflowData = null;
		if(input == null) return;
		back_window = new ArrayWindow(8192);
		
		ctrlbyte = input.getFull();
		bytesRead++;
		bitmask = 0x80;
		lastCtrlCharPos = 4;
		
		int stBitPos = 7;
		int nowBitPos = 7;
		
		//while(!input.isEmpty() && (bytesWritten < decompSize)){
		while(!input.isEmpty() && !hardBreak){
			if(DEBUG_ON){
				stBitPos = nowBitPos;
			}
			
			//Get the next bit of the ctrlbyte to decide what to do
			boolean bit = nextControlBit(input);
			if(bit){
				/*--- DEBUG LOG ---*/
				if(DEBUG_ON){
					debug_logLiteralEvent(bytesWritten, lastCtrlCharPos, 1, stBitPos);
					if(--nowBitPos < 0) nowBitPos = 7;
				}
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
				if(DEBUG_ON){
					if(--nowBitPos < 0) nowBitPos = 7;
					if(--nowBitPos < 0) nowBitPos = 7;
				}
				if(bit){
					//Okay. So this is annoying.
					//I think it's 13 bits for offset (wow)
					//Then 3 bits (+2) for n...
					//Or if the next 3 bits are all 0, then the next byte (+1)
					int b0 = (int)input.get(); bytesRead++;
					int b1 = Byte.toUnsignedInt(input.get()); bytesRead++;
					if((bytesWritten >= decompSize) && (b0 == 0) && (b1 == 0)){
						//Garbage. Break.
						hardBreak = true;
						return;
					}
					
					b0 = b0 << 5;
					b0 |= (b1 >>> 3) & 0x1F;
					int backset = ~b0 & 0x1FFF;
					int n = (b1 & 0x7);
					if(n == 0) {n = input.getFull() + 1; bytesRead++;}
					else n += 2;
					for(int i = 0; i < n; i++){
						byte b = back_window.getFromBack(backset);
						addByteToOutput(b);
					}
					//offsets_long.add(backset);
					//runs_long.add(n);
					//last_ctrl_type = CTRLTYPE_REFLONG;
					
					/*--- DEBUG LOG ---*/
					if(DEBUG_ON){
						long mypos = (bytesWritten + overflowSize) - n;
						long backpos = mypos - backset - 1;
						debug_logReferenceEvent(mypos, lastCtrlCharPos, backpos, n, stBitPos);
					}
					/*--- DEBUG LOG ---*/
					
				}
				else{
					//Next two bits are number to copy -2
					//Next byte is backset
					int n = 2;
					if(nextControlBit(input))n+=2;
					if(nextControlBit(input))n+=1;
					
					if(DEBUG_ON){
						if(--nowBitPos < 0) nowBitPos = 7;
						if(--nowBitPos < 0) nowBitPos = 7;
					}
					
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
					if(DEBUG_ON){
						long mypos = (bytesWritten + overflowSize) - n;
						long backpos = mypos - backset - 1;
						debug_logReferenceEvent(mypos, lastCtrlCharPos, backpos, n, stBitPos);
					}
					/*--- DEBUG LOG ---*/
					
				}
			}
			
		}
	}
	
	public StreamWrapper decode(StreamWrapper input, int allocate) {
		hardBreak = false;
		decompSize = allocate;
		output = new ByteBufferStreamer(allocate); //Init Output
		decode(input);
		
		output.rewind();
		return output;
	}
	
	public FileBuffer decodeToBuffer(StreamWrapper input, int allocate) {
		decompSize = allocate;
		hardBreak = false;
		FileBuffer buff = new FileBuffer(allocate);
		output = new FileBufferStreamer(buff); //Init Output
		decode(input);
		return buff;
	}
	
	public boolean decodeToDisk(StreamWrapper input, String path){
		for(int i = 0; i < 4; i++) input.get();
		
		try{
			hardBreak = false;
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
	
	protected static class MuRunMatch extends RunMatch{
		private boolean scoreMode = true; //False bytes, true bits
		
		public int compareTo(RunMatch o) {
			if(o == null) return -1;
			
			if(!LZCompCore.matchSortOrder){
				int myscore = 0;
				int oscore = 0;
				if(scoreMode){
					myscore = MuLZCompCore.scoreRunStaticBits(this);
					oscore = MuLZCompCore.scoreRunStaticBits(o);
				}
				else{
					myscore = MuLZCompCore.scoreRunStaticBytes(this);
					oscore = MuLZCompCore.scoreRunStaticBytes(o);
				}
				return myscore - oscore;
			}
			else{
				//Position
				if(this.encoder_pos != o.encoder_pos){
					return (int)(this.encoder_pos - o.encoder_pos);
				}
			}
			
			return 0;
		}
	
		public int sizeAsAllLiteral(){
			//In bits.
			int amt = copy_amt;
			if(match_run > 1){
				amt += match_run;
			}
			else{
				if(copy_amt == 0) amt++;
			}
			
			return amt * 9;
		}
	
	}
	
	public static class MuLZCompCore extends LZCompCore{
		
		private boolean scoreMode = true; //False bytes, true bits

		public MuLZCompCore(){
			super.min_run_len = MIN_RUN_SIZE;
			super.max_run_len = MAX_RUN_SIZE_LARGE;
			super.max_back_len = BACK_WINDOW_SIZE;
		}
		
		protected boolean currentMatchEncodable() {
			long backoff = super.current_pos - super.match_pos;
			if(super.match_run < MIN_RUN_SIZE_LARGE){
				//Can't be more than 255 bytes back.
				//Byte before current pos is -1
				if(backoff > MAX_BACKSET_SMALL) return false;
			}
			if(backoff > BACK_WINDOW_SIZE) return false;
			if(match_run > MAX_RUN_SIZE_LARGE) return false;
			
			return true;
		}

		protected boolean matchEncodable(RunMatch match) {
			long backoff = (match.encoder_pos + match.copy_amt) - match.match_pos;
			if(match.match_run < MIN_RUN_SIZE_LARGE){
				//Can't be more than 255 bytes back.
				//Byte before current pos is -1
				if(backoff > MAX_BACKSET_SMALL) return false;
			}
			if(backoff > BACK_WINDOW_SIZE) return false;
			if(match.match_run > MAX_RUN_SIZE_LARGE) return false;
			
			return true;
		}

		protected static int scoreRunStaticBits(RunMatch match) {
			if(match == null) return 0;
			
			//Amount versus decompressed
			//Net bits added. Negative means bits have been saved
			/*int add = match.copy_amt;
			if(match.match_run > 1){
				long backoff = (match.encoder_pos + match.copy_amt) - match.match_pos;
				if(backoff > 255 || match.match_run > 5){
					//Long run
					add = 18;
					if(match.match_run > 9) add += 8;
				}
				else{
					//Short run
					add = 12; //1 byte + 4 bits
				}	
			}
			else{
				if(match.copy_amt < 1) add++; //Sometimes lit1 is just encoded as no copy, no match
			}
			
			return add - (match.match_run << 3);*/
			
			//Amount versus literal
			int litsize = match.sizeAsAllLiteral();
			int trgsize = (match.copy_amt * 9);
			if(match.match_run > 1){
				long backoff = (match.encoder_pos + match.copy_amt) - match.match_pos;
				if(backoff > MAX_BACKSET_SMALL || match.match_run > MAX_RUN_SIZE_SMALL){
					//Long run
					trgsize += 18; //2 bytes + 2 bits
					if(match.match_run > MAX_RUN_SIZE_LARGE_2BYTE) trgsize += 8;
				}
				else{
					//Short run
					trgsize += 12; //1 byte + 4 bits
				}
			}
			else{
				if(match.copy_amt == 0) trgsize += 9;
			}
			
			return trgsize - litsize;
		}
		
		protected static int scoreRunStaticBytes(RunMatch match) {
			if(match == null) return 0;
			
			int litsize = match.copy_amt + match.match_run;
			int trgsize = match.copy_amt;
			if(match.match_run < 1 && match.copy_amt < 1) {
				litsize++;
				trgsize++;
			}
			
			if(match.match_run > 1){
				long backoff = (match.encoder_pos + match.copy_amt) - match.match_pos;
				if(backoff > MAX_BACKSET_SMALL || match.match_run > MAX_RUN_SIZE_SMALL){
					//Long run
					trgsize += 2;
					if(match.match_run > MAX_RUN_SIZE_LARGE_2BYTE){
						trgsize += 1;
					}
				}
				else{
					//Short run
					trgsize += 1;
				}
			}
			
			return trgsize - litsize;
		}
		
		public int scoreRun(RunMatch match) {
			if(scoreMode) return scoreRunStaticBits(match);
			return scoreRunStaticBytes(match);
		}
		
		protected int bytesToEncode(RunMatch match){
			if(match == null) return 0;
			int total = match.copy_amt;
			if(match.match_run > 2){
				long off = match.encoder_pos + match.copy_amt - match.match_pos;
				if(off <= MAX_RUN_SIZE_LARGE && match.match_run <= MAX_RUN_SIZE_SMALL){
					//Short
					total++;
				}
				else{
					total += 2;
					if(match.match_run > MAX_RUN_SIZE_LARGE_2BYTE) total++;
				}
			}
			else{
				if(match.copy_amt == 0) total++;
			}

			return total;
		}
		
		protected RunMatch newRunMatch(){return new MuRunMatch();}
		
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
		//Fill out last control byte.
		if(bitmask != 0x80){
			int ctrl_bits_rem = 0;
			int mask = bitmask;
			while(mask > 0){
				ctrl_bits_rem++;
				mask >>>= 1;
			}
			
			if(ctrl_bits_rem >= 2){
				//Write another imaginary 01 command
				bitmask >>>= 1;
				ctrlbyte |= bitmask;
				bitmask >>>= 1;
				ctrl_bits_rem -= 2;
				
				/*if(ctrl_bits_rem == 2){
					for(int i = 0; i < 3; i++){
						writeDataByte((byte)0x00);
					}
				}
				else{
					writeDataByte((byte)0xff);
					writeDataByte((byte)0xf8);
					writeDataByte((byte)0xff);
				}*/
				for(int i = 0; i < 3; i++){
					writeDataByte((byte)0x00);
				}
				
				output_fb.addToFile((byte)ctrlbyte);
				dumpDataBuffer();
			}
			else{
				output_fb.addToFile((byte)ctrlbyte);
				dumpDataBuffer();
				if(ctrl_bits_rem == 1){
					output_fb.addToFile((byte)0x80);
					for(int i = 0; i < 3; i++){
						output_fb.addToFile((byte)0x00);
					}
				}
			}
			
			//If odd number of remaining bits, hallucinate one more 01
			/*if(ctrl_bits_rem == 1){
				output_fb.addToFile((byte)0x80);
				for(int i = 0; i < 3; i++){
					output_fb.addToFile((byte)0x00);
				}
			}*/
		}
		else{
			//Last command and data batch has already been dumped to stream.
			//Hallucinate one more 01 command
			output_fb.addToFile((byte)0x40);
			for(int i = 0; i < 3; i++) output_fb.addToFile((byte)0x00);
		}
		
		//Pad to 4
		int total_size = (int)output_fb.getFileSize();
		int data_pad = (4 - (total_size & 0x3)) & 0x3;
		for(int i = 0; i < data_pad; i++){
			output_fb.addToFile((byte)0x00);
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
			if(DEBUG_ON){
				debug_logLiteralEvent(compressor.current_pos, lastCtrlCharPos, 1, lastCtrlBitPos);
			}
			/*--- DEBUG LOG ---*/
		}
		else{
			/*--- DEBUG LOG ---*/
			if(DEBUG_ON){
				debug_logReferenceEvent(compressor.current_pos, lastCtrlCharPos, compressor.match_pos, compressor.match_run, lastCtrlBitPos);
			}
			/*--- DEBUG LOG ---*/
			
			//Backref
			int pdiff = (int)(compressor.current_pos - compressor.match_pos);
			if(runlen <= MAX_RUN_SIZE_SMALL){
				//See if short encoding is an option
				//Offset must be <= 255 in magnitude.
				if(pdiff <= MAX_BACKSET_SMALL){
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
			if(runlen > MAX_RUN_SIZE_LARGE_2BYTE){
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
		boolean mflush = false;
		long epos = match.encoder_pos;
		
		//This one needs to read the match's copy amount and encode all those bytes too.
		for(int i = 0; i < match.copy_amt; i++){
			mflush = writeControlBit(true, true);
			writeDataByte(input.getByte(epos + i));
			if(mflush) dumpDataBuffer();
		}
		
		/*--- DEBUG LOG ---*/
		if(DEBUG_ON){
			if(match.copy_amt > 0){
				debug_logLiteralEvent(epos, lastCtrlCharPos, match.copy_amt, lastCtrlBitPos);
			}
		}
		/*--- DEBUG LOG ---*/
	
		epos += match.copy_amt;
		int runlen = match.match_run;
		if(runlen < 2){
			//Copy as is
			mflush = writeControlBit(true, true);
			writeDataByte(input.getByte(epos));
			if(mflush) dumpDataBuffer();
			
			/*--- DEBUG LOG ---*/
			if(DEBUG_ON){
				debug_logLiteralEvent(epos, lastCtrlCharPos, 1, lastCtrlBitPos);
			}
			/*--- DEBUG LOG ---*/
		}
		else{
			/*--- DEBUG LOG ---*/
			if(DEBUG_ON){
				debug_logReferenceEvent(epos, lastCtrlCharPos, match.match_pos, match.match_run, lastCtrlBitPos);
			}
			/*--- DEBUG LOG ---*/
			
			//Backref
			int pdiff = (int)(epos - match.match_pos);
			if(runlen <= MAX_RUN_SIZE_SMALL){
				//See if short encoding is an option
				//Offset must be <= 255 in magnitude.
				if(pdiff <= MAX_BACKSET_SMALL){
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
			if(mflush) dumpDataBuffer();
			
			int b0 = ((-pdiff) >> 5) & 0xff; //sra
			int b1 = ((-pdiff) << 3) & 0xff;
			writeDataByte((byte)b0);
			if(runlen > MAX_RUN_SIZE_LARGE_2BYTE){
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
	
	public FileBuffer encode(FileBuffer input){
		return encode(input, 0);
	}
	
	public FileBuffer encode(FileBuffer input, int overflow){
		if(input == null) return null;
		int alloc = (int)input.getFileSize() - overflow;
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
				if(finds != null){
					for(RunMatch match : finds){
						if(match.match_run > 1){
							amt += match.match_run;
						}
						amt += match.copy_amt;
						encodeNext(input, match);
					}
				}
				else{
					//Literal
					RunMatch match = new MuRunMatch();
					match.encoder_pos = compressor.current_pos;
					match.copy_amt = 0;
					encodeNext(input, match);
					amt++;
				}
				compressor.incrementPos(amt);
				bytesRead += amt;
				if(bytesRead >= decompSize) bytesRead = decompSize;
			}
			finishWrite();
		}
		else if(comp_appr == COMP_LOOKAHEAD_REC){
			compressor.scoreMode = true;
			LinkedList<RunMatch> matches = new LinkedList<RunMatch>();
			while(!compressor.atEnd()){
				//Find match
				int amt = 0;
				compressor.findMatchLookaheadRecursive(matches);
				if(!matches.isEmpty()){
					for(RunMatch match : matches){
						encodeNext(input, match);
						amt += match.copy_amt + match.match_run;
					}
					
					matches.clear();
				}
				else{
					//Immediate literal
					RunMatch match = new MuRunMatch();
					match.encoder_pos = compressor.current_pos;
					match.copy_amt = 0;
					encodeNext(input, match);
					amt++;
				}
				bytesRead += amt;
				compressor.incrementPos(amt);
			}
			finishWrite();
		}
		else if(comp_appr == COMP_LOOKAHEAD_QUICK){
			compressor.scoreMode = true;
			while(!compressor.atEnd()){
				
				//Find match
				RunMatch match = compressor.findMatchLookaheadQuick();
				//System.err.println("0x" + Long.toHexString(compressor.current_pos) + ": " + Integer.toHexString(compressor.lastPick));
				
				int amt = 0;
				if(match != null){
					encodeNext(input, match);
					amt += match.copy_amt + match.match_run;
					if(match.match_run == 256){
						compressor.resetSkipFlag();
					}
				}
				else{
					match = new MuRunMatch();
					match.encoder_pos = compressor.current_pos;
					match.copy_amt = 0;
					encodeNext(input, match);
					amt++;
				}
				bytesRead += amt;
				compressor.incrementPos(amt);
			}
			
			finishWrite();
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
			finishWrite();
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