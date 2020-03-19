package waffleoRai_Sound.nintendo;

//Again, copied from VGMTrans NDSInstrSet.cpp

public class NinIMAADPCM {
	
	public static final int MIN_IMA_INDEX = 0;
	public static final int MAX_IMA_INDEX = 88;
	
	private int first_sample;
	private int init_tbl_index;
	
	private int loop_sample;
	private int loop_tbl_index;
	
	private int last_sample;
	private int tbl_index;
	
	public NinIMAADPCM(int init_samp, int init_step){
		first_sample = last_sample = init_samp;
		init_tbl_index = tbl_index = init_step;
	}
	
	public static int saturate(int in){
		if(in > 0x7FFF) return 0x7FFF;
		if(in < -0x7FFF) return -0x7FFF;
		return in;
	}

	public static int clampIndex(int i){
		if(i > MAX_IMA_INDEX) return MAX_IMA_INDEX;
		if(i < MIN_IMA_INDEX) return MIN_IMA_INDEX;
		return i;
	}
	
	public int decompressNybble(int nybble, boolean looppoint){
		if(looppoint){
			loop_sample = last_sample;
			loop_tbl_index = tbl_index;
		}
		
		int diff = NinSound.IMA_ADPCM_TABLE[tbl_index] >> 3; //Divided by 8
		if((nybble & 0x1) != 0) diff += NinSound.IMA_ADPCM_TABLE[tbl_index] >> 2; //Div by 4
		if((nybble & 0x2) != 0) diff += NinSound.IMA_ADPCM_TABLE[tbl_index] >> 1; //Div by 2
		if((nybble & 0x4) != 0) diff += NinSound.IMA_ADPCM_TABLE[tbl_index]; //Div by 1
		
		int out = last_sample; 
		if((nybble & 0x8) != 0) out = saturate(last_sample + diff);
		else out = saturate(last_sample - diff);
		
		last_sample = out;
		tbl_index = clampIndex(tbl_index + NinSound.IMA_INDEX_TABLE[nybble & 0x7]);
		
		return out;
	}
	
	public void resetToStart(){last_sample = first_sample; tbl_index = init_tbl_index;}
	public void resetToLoop(){last_sample = loop_sample; tbl_index = loop_tbl_index;}
	
	public NinIMAADPCM createCopy(){
		NinIMAADPCM copy = new NinIMAADPCM(first_sample, init_tbl_index);
		copy.loop_sample = loop_sample; copy.loop_tbl_index = loop_tbl_index;
		return copy;
	}
	
	public void setState(int init_samp, int init_step){
		last_sample = init_samp;
		tbl_index = init_step;
	}
	
	public void setState(int init_step){
		tbl_index = init_step;
	}
	
}
