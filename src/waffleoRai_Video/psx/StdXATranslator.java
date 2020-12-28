package waffleoRai_Video.psx;

import java.util.Deque;
import java.util.LinkedList;

import waffleoRai_Utils.BinTree;
import waffleoRai_Utils.FileBuffer;

//http://kenai.com/projects/jpsxdec/
//http://jpsxdec.blogspot.com/


public abstract class StdXATranslator implements XA2MDECTranslator{
	
	//Functions common to version 2 AND 3 video frames
	//READ STREAM AS LE HALFWORDS!!!
	
	/* ----- Constants ----- */
	
	public static final int BLOCK_NONE = 0;
	public static final int BLOCK_CR = 1;
	public static final int BLOCK_CB = 2;
	public static final int BLOCK_Y1 = 3;
	public static final int BLOCK_Y2 = 4;
	public static final int BLOCK_Y3 = 5;
	public static final int BLOCK_Y4 = 6;
	
	public static final String EOF_V2 = "01111111110";
	public static final String EOF_V3 = "11111111110";
	
	public static final int EOF_V2_HI_TEN = 0x1ff;
	public static final int EOF_V3_INT = 0x7fe;
	
	public static final int EOB = -3;
	public static final int INVALID_CODE = -2;
	public static final int ESCAPE_CODE = -1;
	
	//AC Table
	public static final String[] AC_CODES_STR = {"11", "011", "0100", "0101", "00101", "00110", "00111", 
			"000100", "000101", "000110", "000111", "0000100", "0000101", "0000110", 
			"0000111", "00100000", "00100001", "00100010", "00100011", "00100100", 
			"00100101", "00100110", "00100111", "0000001000", "0000001001", 
			"0000001010", "0000001011", "0000001100", "0000001101", "0000001110", 
			"0000001111", "000000010000", "000000010001", "000000010010", "000000010011", 
			"000000010100", "000000010101", "000000010110", "000000010111", "000000011000", 
			"000000011001", "000000011010", "000000011011", "000000011100", "000000011101", 
			"000000011110", "000000011111", "0000000010000", "0000000010001", 
			"0000000010010", "0000000010011", "0000000010100", "0000000010101", 
			"0000000010110", "0000000010111", "0000000011000", "0000000011001", 
			"0000000011010", "0000000011011", "0000000011100", "0000000011101", 
			"0000000011110", "0000000011111", "00000000010000", "00000000010001", 
			"00000000010010", "00000000010011", "00000000010100", "00000000010101", 
			"00000000010110", "00000000010111", "00000000011000", "00000000011001", 
			"00000000011010", "00000000011011", "00000000011100", "00000000011101", 
			"00000000011110", "00000000011111", "000000000010000", "000000000010001", 
			"000000000010010", "000000000010011", "000000000010100", "000000000010101", 
			"000000000010110", "000000000010111", "000000000011000", "000000000011001", 
			"000000000011010", "000000000011011", "000000000011100", "000000000011101", 
			"000000000011110", "000000000011111", "0000000000010000", "0000000000010001", 
			"0000000000010010", "0000000000010011", "0000000000010100", "0000000000010101", 
			"0000000000010110", "0000000000010111", "0000000000011000", "0000000000011001", 
			"0000000000011010", "0000000000011011", "0000000000011100", "0000000000011101", 
			"0000000000011110", "0000000000011111", "000001","10"};
	
	public static final int[][] AC_CODES_VALS = {{0,1},{1,1},{0,2},{2,1},{0,3},{4,1},
			{3,1},{7,1},{6,1},{1,2},{5,1},{2,2},{9,1},{0,4},{8,1},{13,1},{0,6},{12,1},
			{11,1},{3,2},{1,3},{0,5},{10,1},{16,1},{5,2},{0,7},{2,3},{1,4},{15,1},
			{14,1},{4,2},{0,11},{8,2},{4,3},{0,10},{2,4},{7,2},{21,1},{20,1},{0,9},
			{19,1},{18,1},{1,5},{3,3},{0,8},{6,2},{17,1},{10,2},{9,2},{5,3},{3,4},
			{2,5},{1,7},{1,6},{0,15},{0,14},{0,13},{0,12},{26,1},{25,1},{24,1},
			{23,1},{22,1},{0,31},{0,30},{0,29},{0,28},{0,27},{0,26},{0,25},{0,24},
			{0,23},{0,22},{0,21},{0,20},{0,19},{0,18},{0,17},{0,16},{0,40},{0,39},
			{0,38},{0,37},{0,36},{0,35},{0,34},{0,33},{0,32},{1,14},{1,13},{1,12},
			{1,11},{1,10},{1,9},{1,8},{1,18},{1,17},{1,16},{1,15},{6,3},{16,2},
			{15,2},{14,2},{13,2},{12,2},{11,2},{31,1},{30,1},{29,1},{28,1},{27,1},{-1,-1},
			{-3,-3}};
	
	/* ----- Instance Variables ----- */
	
	//Queues
	protected Deque<Integer> in_queue;
	protected Deque<Integer> out_queue;
	
	//Output
	protected int out_bit_pos;
	protected int out_word;
	
	//Frame State
	protected int current_block; //Cr, Cb, Y1, Y2, Y3, Y4
	protected boolean eof_flag;
	protected boolean b_flag; //Advance block # when acknowledged. True if new block, false if mid-block.
	//protected int dc;
	
	//Decoding
	protected BinTree<int[]> ac_tree;
	protected int fqs; //Frame Quant Scale
	
	/* ----- Structs ----- */
	
	/* ----- Initialization ----- */
	
	protected StdXATranslator(){
		in_queue = new LinkedList<Integer>();
		out_queue = new LinkedList<Integer>();
		bit_cache = new LinkedList<Boolean>();
		
		current_hw = 0;
		bit_pos = 16;
		current_block = BLOCK_CR;
		b_flag = true;
		
		buildACTree();
	}
	
	protected void buildACTree(){
		ac_tree = new BinTree<int[]>(null);
		
		for(int i = 0; i < AC_CODES_STR.length; i++){
			String code = AC_CODES_STR[i];
			int[] val = AC_CODES_VALS[i];
			
			//This is not an efficient way to build a tree.
			//But I'm lazy
			ac_tree.moveToRoot();
			int clen = code.length();
			for(int j = 0; j < clen; j++){
				char bit = code.charAt(j);
				if(bit == '0'){
					if(!ac_tree.moveLeft()){
						ac_tree.insertChildNode(new int[]{-2}, false);
						ac_tree.moveLeft();
					}
				}
				else{
					if(!ac_tree.moveRight()){
						ac_tree.insertChildNode(new int[]{-2}, true);
						ac_tree.moveRight();
					}
				}
			}
			ac_tree.setCurrentData(val);
		}
	}
	
	/* ----- BitStream ----- */
	
	private int current_hw;
	private int bit_pos; //Pull new hw when hits 16
	
	private Deque<Boolean> bit_cache; //Last few read bits
	
	protected int nextBits(int bitCount, boolean signed) throws NoBitsAvailableException{
		int value = 0;
		
		for(int i = 0; i < bitCount; i++){
			value = value << 1;
			try {value |= nextBit_num();}
			catch(NoBitsAvailableException e){
				this.pushBackBits(i);
				throw e;
			}
		}
		
		if(signed){
			int shamt = 32-bitCount;
			value = (value << shamt) >> shamt;
		}
		
		return value;
	}
	
	protected boolean nextBit() throws NoBitsAvailableException{
		if(bit_pos >= 16){
			//Advance to next halfword
			
			//Want to leave it at 16 so returns false when asked if input available
			if(in_queue.isEmpty()) throw new NoBitsAvailableException(); 
			
			current_hw = in_queue.pop();
			//System.err.println("StdXATranslator.nextBit || New Word: 0x" + String.format("%04x", current_hw));
			bit_pos = 0;
		}
		
		//Just read the bit in position 16 
		boolean bit = (current_hw & 0x8000) != 0;
		current_hw = current_hw << 1;
		bit_cache.add(bit);
		bit_pos++;
		
		return bit;
	}
	
	protected int nextBit_num() throws NoBitsAvailableException{
		if(bit_pos >= 16){
			//Advance to next halfword
			
			//Want to leave it at 16 so returns false when asked if input available
			if(in_queue.isEmpty()) throw new NoBitsAvailableException();  
			
			current_hw = in_queue.pop();
			//System.err.println("StdXATranslator.nextBit_num || New Word: 0x" + String.format("%04x", current_hw));
			bit_pos = 0;
		}
		
		//Just read the bit in position 16 
		int bit = (current_hw & 0x8000) >>> 15;
		current_hw = current_hw << 1;
		bit_cache.add(bit!=0);
		bit_pos++;
		
		return bit;
	}
	
	protected boolean inputAvailable(){
		if(bit_pos >= 0 && bit_pos < 16) return true;
		return !in_queue.isEmpty();
	}
	
	protected boolean peekBit() throws NoBitsAvailableException{
		if(bit_pos >= 16){
			//Advance to next halfword
			
			//Want to leave it at 16 so returns false when asked if input available
			if(in_queue.isEmpty()) throw new NoBitsAvailableException(); 
			
			current_hw = in_queue.pop();
			bit_pos = 0;
		}
		
		//Just read the bit in position 16 
		boolean bit = (current_hw & 0x8000) != 0;
		return bit;
	}
	
	protected int pushBackBits(int bcount){
		//For when bits popped, but not enough, so return to input stream.
		int c = 0;
		for(int i = 0; i < bcount; i++){
			if (bit_cache.isEmpty()) return c;
			boolean bit = bit_cache.pollLast();
			c++;
			
			if(bit_pos == 0){
				in_queue.push(current_hw);
				current_hw = 0;
				bit_pos = 15;
			}
			
			current_hw = current_hw >>> 1;
			if(bit) current_hw |= 0x8000;
			bit_pos--;
		}
		
		return c;
	}
	
	protected void flushBitCache(){
		bit_cache.clear();
	}
	
	public static class NoBitsAvailableException extends Exception{
		private static final long serialVersionUID = -5423499103408365343L;
	}
	
	public static class EndOfFrameException extends Exception{
		private static final long serialVersionUID = 2300257810982294492L;
	}
	
	/* ----- Processing ----- */
	
	protected int[] readNextAC(){
		//Need to recognize End of Block and End of Frame!
		/*
		 * Returns:
		 * 	0 - # zero-value coefs
		 * 	1 - Non-zero coeff value
		 * 	2 - Total bits
		 */
		//Don't forget sign bit, and also escape code!
		int[] output = new int[3];
		int bitcount = 0;
		ac_tree.moveToRoot();
		int[] myvals = null;
		
		try{
			//String bitcode = "";
			while(myvals == null || myvals.length != 2){
				//Next bit
				boolean nbit = nextBit(); bitcount++;
				if(nbit) ac_tree.moveRight();
				else ac_tree.moveLeft();
				myvals = ac_tree.getCurrentData();
				
				//if(nbit) bitcode += "1";
				//else bitcode += "0";
			}
			//System.err.println("StdXATranslator.readNextAC || Bitcode read: " + bitcode);
		
			//Check if escape
			if(myvals[0] == ESCAPE_CODE){
				//Six bits (unsigned) for zero count
				//Ten bits (signed) for non-zero
				output[0] = nextBits(6, false);
				output[1] = nextBits(10, true);
				bitcount+=16;
				
				//System.err.println("StdXATranslator.readNextAC || Escape Code!");
			}
			else{
				//Check sign bit
				output[0] = myvals[0];
				output[1] = myvals[1];
				if(output[0] != EOB){
					if(nextBit()) output[1] *= -1;
					bitcount++;
					
					//System.err.println("StdXATranslator.readNextAC || Sign: " + (output[1] < 0));	
				}
			}
		}
		catch(NoBitsAvailableException x){
			//Not enough. Put back what was read and return null.
			pushBackBits(bitcount);
			return null;
		}
		
		output[2] = bitcount;
		flushBitCache();
		return output;
	}
	
	public int processInputBuffer(){
		//Returns number of output halfwords
		int wcount = 0;
		//System.err.println("StdXATranslator.processInputBuffer || --DEBUG-- CHECK 1");
		
		while(inputAvailable()){
			//It expects to start on a sector AFTER both the video header
			//	AND the repeat of the macroblock command (?? the 0x3800 one) and scale/ver fields
			//In other words, it starts right on the data stream.
			//**Note that the cmd/scale/ver words only come at start of frame, so not in every sector
			
			//Also don't forget to read EOFs
			if(b_flag){
				//Start of new block. Look for DC or EOF
				//Some block flags...
				if(++current_block > BLOCK_Y4) current_block = BLOCK_CR;
				b_flag = false;
				
				//If encounters an EOF, returns (should warn caller that EOF was found in case input buffer should be flushed)
				int[] dc = null;
				try{
					dc = readNextDC();
					if(dc == null){
						//We're out of input. Return.
						return wcount;
					}
					//System.err.println("StdXATranslator.processInputBuffer || DC Found: " + dc[0]);
					//System.err.println("StdXATranslator.processInputBuffer || Quant Scale: " + fqs);
					//System.err.println("StdXATranslator.processInputBuffer || BitPos: " + bit_pos);
					
					//Set values and stuff...
					int hw = (fqs & 0x3F) << 10;
					hw |= dc[0] & 0x3FF;
					out_queue.add(hw);
					wcount++;	
				}
				catch(EndOfFrameException x){
					//Set flags and return
					eof_flag = true;
					return wcount;
				}
				
			}
			else{
				//Middle of block. Look for EOB or ACs
				int[] ac = readNextAC();
				if(ac == null || ac.length < 3) return wcount;
				
				//Check if EOB
				int ac_z = ac[0];
				int ac_val = ac[1];
				//System.err.println("StdXATranslator.processInputBuffer || AC Found: " + ac_z + "," + ac_val);
				//System.err.println("StdXATranslator.processInputBuffer || BitPos: " + bit_pos);
				
				int hw = 0;
				if(ac_z == EOB){
					hw = 0xFE00;
					b_flag = true;
				}
				else{
					hw = (ac_z & 0x3F) << 10;
					hw |= (ac_val & 0x3FF);
				}
				
				out_queue.add(hw);
				wcount++;
			}
			
			//DEBUG
			//if(out_queue.size() >= 32) return wcount;
		}
		
		return wcount;
	}
	
	/* ----- Input ----- */
	
	protected abstract int[] readNextDC() throws EndOfFrameException; //{value, #bits read}
	
	public int feedBytes(byte[] data){
		if(data == null) return 0;
		FileBuffer wrap = FileBuffer.wrap(data);
		wrap.setEndian(false);
		return feedBytes(wrap);
	}
	
	public int feedBytes(FileBuffer data){
		if(data == null) return 0;
		long sz = data.getFileSize();
		long cpos = 0;
		if(sz % 2 != 0) sz--;
		
		int ct = 0;
		while(cpos < sz){
			int hw = Short.toUnsignedInt(data.shortFromFile(cpos));
			in_queue.add(hw);
			
			cpos += 2;
			ct += 2;
		}
		
		return ct;
	}
	
	public boolean feedHalfword(int hw){
		in_queue.add(hw);
		return true;
	}
	
	public void setFrameQuantScale(int value){
		fqs = value;
	}
	
	public void flushInput(){
		//Also clears EOF flag
		current_hw = 0;
		bit_pos = 16;
		in_queue.clear();
		eof_flag = false;
		b_flag = true;
		current_block = BLOCK_CR;
		
	}
	
	public boolean eof_flag(){
		return this.eof_flag;
	}
	
	/* ----- Output ----- */
	
	public int outputWordsAvailable(){
		return out_queue.size();
	}
	
	public int nextOutputWord(){
		if(out_queue.isEmpty()) return 0;
		return out_queue.pop();
	}

}
