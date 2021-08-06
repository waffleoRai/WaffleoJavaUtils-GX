package waffleoRai_Sound.nintendo;

import waffleoRai_Sound.ADPCMCoder;

public class Z64ADPCM implements ADPCMCoder{
	
	//This is from N64SoundListTool's N64AIFCAudio.cpp
	
	private ADPCMTable table;
	
	private int[] lastBlock;
	//private int[] lastBlock_loop;
	
	private int[] thisBlock;
	private int[] thisBlock_raw;
	private int sampleIndex;
	
	private int cidx;
	private int shamt;
	
	public Z64ADPCM(ADPCMTable adpcm_table){
		table = adpcm_table;
		//s0 = 0; s1 = 0;
		lastBlock = new int[8];
		thisBlock = new int[8];
		thisBlock_raw = new int[8];
		sampleIndex = 0;
		cidx = 0; shamt = 0;
	}
	
	public ADPCMTable getTable() {return table;}
	public int getSamplesPerBlock() {return 16;}
	public int getBytesPerBlock() {return 9;}
	public boolean getHiNybbleFirst() {return true;}

	public void reset() {
		for(int i = 0; i < 8; i++){
			lastBlock[i] = 0;
			thisBlock[i] = 0;
			thisBlock_raw[i] = 0;
		}
		sampleIndex = 0;
		cidx = 0; shamt = 0;
	}

	public void setToLoop(int loopIndex) {
		reset();
		sampleIndex = loopIndex;
		setControlByte(table.getLoopPS());
		lastBlock[7] = table.getLoopHistorySample1();
		lastBlock[6] = table.getLoopHistorySample2();
	}

	@Override
	public void setControlByte(int val) {
		//shamt is high
		shamt = (val >>> 4) & 0xF;
		cidx = val & 0xF;
	}

	public boolean newBlock() {
		return (sampleIndex>= 16);
	}

	@Override
	public int decompressNextNybble(int raw_sample) {
		//Sign extend
		int in = raw_sample;
		if(raw_sample >= 8) in = raw_sample - 16;
		int subidx = sampleIndex % 8;
		
		//Prepare block
		if(subidx == 0){
			//New block.
			int[] temp = lastBlock;
			lastBlock = thisBlock;
			thisBlock = temp;
			for(int i = 0; i < 8; i++){
				thisBlock[i] = 0;
			}
			if(sampleIndex >= 16) sampleIndex = 0;
		}
		
		int p1_base = cidx << 4;
		int p2_base = p1_base + 8;
		
		in <<= shamt;
		thisBlock_raw[subidx] = in;
		
		int add = lastBlock[6] * table.getCoefficient(p1_base + subidx);
		add += lastBlock[7] * table.getCoefficient(p2_base + subidx);
		
		for(int i = subidx; i > 0; i--){
			int j = i-1;
			add += (thisBlock_raw[subidx-1-j] * table.getCoefficient(p2_base + j));
		}
		
		int out = in << 11;
		out += add;
		out >>= 11;
		
		//Clamp
		out = Math.max(-32767, out);
		out = Math.min(32767, out);
		
		//Save sample
		thisBlock[subidx] = out;
		sampleIndex++;
		
		return out;
	}

}
