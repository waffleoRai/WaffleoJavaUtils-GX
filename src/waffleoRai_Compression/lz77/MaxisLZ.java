package waffleoRai_Compression.lz77;

import java.io.InputStream;

import waffleoRai_Compression.definitions.AbstractCompDef;
import waffleoRai_Compression.lz77.LZCompCore.RunMatch;
import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.FileBuffer;
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
	
	public static FileBuffer decompress(FileBuffer input){
		//Read the header...
		int flags1 = Byte.toUnsignedInt(input.nextByte());
		int decsize = 0;
		long cpos = 0L;
		if((flags1 & 0x80) != 0){
			decsize = input.intFromFile(2L);
			cpos = 6;
		}
		else{
			decsize = input.shortishFromFile(2L);
			cpos = 5;
		}
		long edpos = input.getFileSize();
		
		int read_plain = -1;
		int backread_count = -1;
		int backread_off = -1;
		FileBuffer output = new FileBuffer(decsize, input.isBigEndian());
		while(cpos < edpos){
			//Next control character
			int cc = Byte.toUnsignedInt(input.getByte(cpos++));
			if((cc & 0x80) == 0){
				//2 bytes
				//Read plain range: 0 - 3
				//Streak range: 3 - 10
				//Backoff range: 1- 1024
				read_plain = cc & 0x3;
				backread_count = ((cc & 0x1c) >>> 2) + 3;
				
				int b1 = Byte.toUnsignedInt(input.getByte(cpos++));
				backread_off = ((cc & 0x60) << 3) | b1;
			}
			else if((cc & 0x40) == 0){
				//3 bytes
				//Read plain range: 0 - 3
				//Streak range: 4 - 67
				//Backoff range: 1- 16384
				backread_count = (cc & 0x3f) + 4;
				
				int b1 = Byte.toUnsignedInt(input.getByte(cpos++));
				read_plain = (b1 & 0xc0) >>> 6;
				
				int b2 = Byte.toUnsignedInt(input.getByte(cpos++));
				backread_off = ((b1 & 0x3f) << 8) | b2;
			}
			else if((cc & 0x20) == 0){
				//4 bytes
				//Read plain range: 0 - 3
				//Streak range: 5 - 1028
				//Backoff range: 1 - 131072
				read_plain = cc & 0x3;
				
				int b1 = Byte.toUnsignedInt(input.getByte(cpos++));
				int b2 = Byte.toUnsignedInt(input.getByte(cpos++));
				backread_off = ((cc & 0x10) << 12) | (b1 << 8) | b2;
				
				int b3 = Byte.toUnsignedInt(input.getByte(cpos++));
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
				else read_plain = ((cc & 0x1f) << 4) + 4;
			}
			
			for(int i = 0; i < read_plain; i++){
				output.addToFile(input.getByte(cpos++));
			}
			
			output.setCurrentPosition(output.getFileSize() - backread_off - 1);
			for(int i = 0; i < backread_count; i++){
				output.addToFile(output.nextByte());
			}
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
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean decompressToDiskBuffer(InputStream input, String bufferPath, int options) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean decompressToDiskBuffer(BufferReference input, String bufferPath, int options) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public FileBuffer decompressToMemory(StreamWrapper input, int allocAmount, int options) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public FileBuffer decompressToMemory(InputStream input, int allocAmount, int options) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public FileBuffer decompressToMemory(BufferReference input, int allocAmount, int options) {
			// TODO Auto-generated method stub
			return null;
		}
		
	}
	
	public static MaxisLZDef getDefinition(){
		if(stat_def == null) stat_def = new MaxisLZDef();
		return stat_def;
	}

}
