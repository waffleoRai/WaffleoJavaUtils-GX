package waffleoRai_Video.psx;

import waffleoRai_Utils.BinTree;

public class StdXAV3Translator extends StdXATranslator{
	
	public static final String[] DC_COLOR_CODES_STR = {"11111110", "1111110", "111110", "11110",
													   "1110", "110", "10", "01", "00", EOF_V3};
	public static final int[] DC_COLOR_CODES_BITS = {8,7,6,5,4,3,2,1,0,-1};
	
	public static final String[] DC_LUM_CODES_STR = {"1111110", "111110", "11110", "1110",
			   						                 "110", "101", "01", "00", "100", EOF_V3};
	public static final int[] DC_LUM_CODES_BITS = {8,7,6,5,4,3,2,1,0,-1};
	
	protected BinTree<Integer> dc_tree_clr;
	protected BinTree<Integer> dc_tree_lum;
	
	protected int last_cr;
	protected int last_cb;
	protected int last_lum;
	
	public StdXAV3Translator(){
		super();
		buildTrees();
	}
	
	private void buildTrees(){
		dc_tree_clr = new BinTree<Integer>(0);
		dc_tree_lum = new BinTree<Integer>(0);
		
		for(int i = 0; i < DC_COLOR_CODES_STR.length; i++){
			String code = DC_COLOR_CODES_STR[i];
			int val = DC_COLOR_CODES_BITS[i];
			
			//This is not an efficient way to build a tree.
			//But I'm lazy
			dc_tree_clr.moveToRoot();
			int clen = code.length();
			for(int j = 0; j < clen; j++){
				char bit = code.charAt(j);
				if(bit == '0'){
					if(!dc_tree_clr.moveLeft()){
						dc_tree_clr.insertChildNode(0, false);
						dc_tree_clr.moveLeft();
					}
				}
				else{
					if(!dc_tree_clr.moveRight()){
						dc_tree_clr.insertChildNode(0, true);
						dc_tree_clr.moveRight();
					}
				}
			}
			dc_tree_clr.setCurrentData(val);
		}
		
		for(int i = 0; i < DC_LUM_CODES_STR.length; i++){
			String code = DC_LUM_CODES_STR[i];
			int val = DC_LUM_CODES_BITS[i];
			
			//This is not an efficient way to build a tree.
			//But I'm lazy
			dc_tree_lum.moveToRoot();
			int clen = code.length();
			for(int j = 0; j < clen; j++){
				char bit = code.charAt(j);
				if(bit == '0'){
					if(!dc_tree_lum.moveLeft()){
						dc_tree_lum.insertChildNode(0, false);
						dc_tree_lum.moveLeft();
					}
				}
				else{
					if(!dc_tree_lum.moveRight()){
						dc_tree_lum.insertChildNode(0, true);
						dc_tree_lum.moveRight();
					}
				}
			}
			dc_tree_lum.setCurrentData(val);
		}
	}

	protected static int readRawDC(boolean sign, int bits, int val){
		//INCLUDES the x4 multiplication
		int out = 1 << (bits-1);
		out |= val;
		out = out << 2;
		
		if(sign) out *= -1;
		
		return out;
	}
	
	protected int[] readNextDC() throws EndOfFrameException {
		int bcount = 0;
		int[] out = new int[2];
		
		//Need to know what block it is...
		int bits = 0;
		int val = 0;
		try{
			switch(current_block){
			case BLOCK_CR:
			case BLOCK_CB:
				dc_tree_clr.moveToRoot();
				while(!dc_tree_clr.currentIsLeaf()){
					if(nextBit()) dc_tree_clr.moveRight();
					else dc_tree_clr.moveLeft();
					bcount++;
				}
				bits = dc_tree_clr.getCurrentData();
				if(bits < 0) throw new EndOfFrameException();
				if(bits > 0){
					boolean sign = nextBit(); bcount++;
					if(bits > 1){
						val = nextBits(bits-1, false);
						bcount += bits-1;
					}
					val = readRawDC(sign, bits, val);
				}
				
				if(current_block == BLOCK_CR){
					out[0] = (last_cr + val) & 0x3FF;
					last_cr = out[0];
				}
				else{
					//Cb
					out[0] = (last_cb + val) & 0x3FF;
					last_cb = out[0];
				}

				break;
			case BLOCK_Y1:
			case BLOCK_Y2:
			case BLOCK_Y3:
			case BLOCK_Y4:
				dc_tree_lum.moveToRoot();
				while(!dc_tree_lum.currentIsLeaf()){
					if(nextBit()) dc_tree_lum.moveRight();
					else dc_tree_lum.moveLeft();
					bcount++;
				}
				bits = dc_tree_lum.getCurrentData();
				if(bits < 0) throw new EndOfFrameException();
				if(bits > 0){
					boolean sign = nextBit(); bcount++;
					if(bits > 1){
						val = nextBits(bits-1, false);
						bcount += bits-1;
					}
					val = readRawDC(sign, bits, val);
				}
				
				if(current_block != BLOCK_Y1){
					out[0] = (last_lum + val) & 0x3FF;
					last_lum = out[0];
				}
				else{
					out[0] = val & 0x3FF;
					last_lum = out[0];
				}
				break;
			default: return null;
			}
		}
		catch(NoBitsAvailableException x){
			super.pushBackBits(bcount);
			return null;
		}
		
		out[1] = bcount;
		return out;
	}

}
