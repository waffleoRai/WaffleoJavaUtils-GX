package waffleoRai_Compression.lz77;

import java.io.IOException;
import java.io.InputStream;

import waffleoRai_Compression.definitions.AbstractCompDef;
import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileInputStreamer;
import waffleoRai_Utils.StreamWrapper;

public class MaxisLZ {
	
	public static final int MIN_RUNLEN = 3;
	public static final int MAX_RUNLEN = 1028;
	public static final int MAX_BACKLOOK = 131072;
	
	/*--- Instance Variables ---*/
	
	private int max_backlook = MAX_BACKLOOK; //Allow user to set lower to make compression faster.
	
	/*--- Encode ---*/
	
	public static class MaxisLZCompCore extends LZCompCore{

		public MaxisLZCompCore(){
			super.min_run_len = MIN_RUNLEN;
			super.max_run_len = MAX_RUNLEN;
			super.max_back_len = MAX_BACKLOOK;
		}
		
		@Override
		protected boolean currentMatchEncodable() {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		protected boolean matchEncodable(RunMatch match) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		protected int scoreRun(RunMatch match) {
			// TODO Auto-generated method stub
			return 0;
		}
		
		protected int bytesToEncode(RunMatch match){
			//TODO
			return 0;
		}
		
	}
	
	public static FileBuffer compress(FileBuffer input){
		//TODO
		return null;
	}
	
	/*--- Decode ---*/
	
	//Clumsy temp solution...
	private static interface DecByteSource{
		public int next();
		public byte nextByte();
	}
	
	private static class BufferRefByteSource implements DecByteSource{
		private BufferReference ref;
		
		public int next() {
			return Byte.toUnsignedInt(ref.nextByte());
		}
		
		public byte nextByte() {return ref.nextByte();}
	}
	
	private static class StreamerByteSource implements DecByteSource{
		private StreamWrapper ref;
		
		public int next() {
			return ref.getFull();
		}
		
		public byte nextByte() {return ref.get();}
	}
	
	private static void decCycle(DecByteSource src, FileBuffer target) {
		int read_plain = -1;
		int backread_count = -1;
		int backread_off = -1;
		
		//Next control character
		int cc = src.next();
		if((cc & 0x80) == 0){
			//2 bytes
			//Read plain range: 0 - 3
			//Streak range: 3 - 10
			//Backoff range: 1- 1024
			read_plain = cc & 0x3;
			backread_count = ((cc & 0x1c) >>> 2) + 3;
			
			int b1 = src.next();
			backread_off = ((cc & 0x60) << 3) | b1;
		}
		else if((cc & 0x40) == 0){
			//3 bytes
			//Read plain range: 0 - 3
			//Streak range: 4 - 67
			//Backoff range: 1- 16384
			backread_count = (cc & 0x3f) + 4;
			
			int b1 = src.next();
			read_plain = (b1 & 0xc0) >>> 6;
			
			int b2 = src.next();
			backread_off = ((b1 & 0x3f) << 8) | b2;
		}
		else if((cc & 0x20) == 0){
			//4 bytes
			//Read plain range: 0 - 3
			//Streak range: 5 - 1028
			//Backoff range: 1 - 131072
			read_plain = cc & 0x3;
			
			int b1 = src.next();
			int b2 = src.next();
			backread_off = ((cc & 0x10) << 12) | (b1 << 8) | b2;
			
			int b3 = src.next();
			backread_count = (((cc & 0xc) << 6) | b3) + 5;
		}
		else{
			//1 byte
			//Read plain range: 0 - 3 (0xfc+) or ((1-28) * 4) (0xfb-)
			//Streak range: 0
			//Backoff range: 0
			backread_off = 0;
			backread_count = 0;
			if(cc >= 0xfc) read_plain = cc & 0x3;
			else read_plain = ((cc & 0x1f) << 2) + 4;
		}
		
		for(int i = 0; i < read_plain; i++){
			target.addToFile(src.nextByte());
		}
		
		target.setCurrentPosition(target.getFileSize() - backread_off - 1);
		for(int i = 0; i < backread_count; i++){
			target.addToFile(target.nextByte());
		}
		
	}
	
	public static FileBuffer decompress(FileBuffer input){
		return decompress(input.getReferenceAt(0L));
	}
	
	public static FileBuffer decompress(BufferReference input){
		//Read the header...
		input.setByteOrder(false);
		int flags1 = Short.toUnsignedInt(input.nextShort());
		int decsize = 0;
		boolean b = input.getBuffer().isBigEndian();
		input.setByteOrder(true);
		if((flags1 & 0x80) != 0){
			decsize = input.nextInt();
		}
		else{
			decsize = input.next24Bits();
		}
		input.setByteOrder(b);
		
		FileBuffer output = new FileBuffer(decsize, input.getBuffer().isBigEndian());
		BufferRefByteSource src = new BufferRefByteSource();
		src.ref = input;
		while(src.ref.hasRemaining()){
			decCycle(src, output);
		}
		
		return output;
	}
	
	public static FileBuffer decompress(StreamWrapper input){
		//Read the header...
		int flags1 = input.getFull();
		input.getFull(); //Skip second byte of flags for now.
		int decsize = 0;
		
		//Size seems to always be BE
		if((flags1 & 0x80) != 0){
			for(int i = 0; i < 4; i++) {
				decsize <<= 8;
				decsize |= input.getFull();
			}
		}
		else{
			for(int i = 0; i < 3; i++) {
				decsize <<= 8;
				decsize |= input.getFull();
			}
		}
		
		FileBuffer output = new FileBuffer(decsize, false);
		StreamerByteSource src = new StreamerByteSource();
		src.ref = input;
		while(!src.ref.isEmpty()){
			decCycle(src, output);
		}
		
		return output;
	}
	
	/*--- Definitions ---*/
	
	public static final int DEF_ID = 0xdf439fa3;
	public static final String DEFO_ENG_STR = "Maxis LZSS";
	
	private static MaxisLZDef stat_def;
	
	public static class MaxisLZDef extends AbstractCompDef{

		protected MaxisLZDef() {
			super(DEFO_ENG_STR);
			super.extensions.add("lz");
		}

		public int getDefinitionID() {return DEF_ID;}

		@Override
		public boolean decompressToDiskBuffer(StreamWrapper input, String bufferPath, int options) {
			//TODO
			FileBuffer buff = decompressToMemory(input, 0, options);
			if(buff != null) {
				try {
					buff.writeFile(bufferPath);
					buff.dispose();
					return true;
				} 
				catch (IOException e) {
					e.printStackTrace();
					return false;
				}
			}
			return false;
		}

		@Override
		public boolean decompressToDiskBuffer(InputStream input, String bufferPath, int options) {
			//TODO
			FileBuffer buff = decompressToMemory(input, 0, options);
			if(buff != null) {
				try {
					buff.writeFile(bufferPath);
					buff.dispose();
					return true;
				} 
				catch (IOException e) {
					e.printStackTrace();
					return false;
				}
			}
			return false;
		}

		@Override
		public boolean decompressToDiskBuffer(BufferReference input, String bufferPath, int options) {
			// TODO Auto-generated method stub
			FileBuffer buff = decompressToMemory(input, 0, options);
			if(buff != null) {
				try {
					buff.writeFile(bufferPath);
					buff.dispose();
					return true;
				} 
				catch (IOException e) {
					e.printStackTrace();
					return false;
				}
			}
			return false;
		}

		@Override
		public FileBuffer decompressToMemory(StreamWrapper input, int allocAmount, int options) {
			return decompress(input);
		}

		@Override
		public FileBuffer decompressToMemory(InputStream input, int allocAmount, int options) {
			FileInputStreamer str = new FileInputStreamer(input);
			return decompressToMemory(str, allocAmount, options);
		}

		@Override
		public FileBuffer decompressToMemory(BufferReference input, int allocAmount, int options) {
			return decompress(input);
		}
		
	}
	
	public static MaxisLZDef getDefinition(){
		if(stat_def == null) stat_def = new MaxisLZDef();
		return stat_def;
	}

}
