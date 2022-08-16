package waffleoRai_Sound.nintendo;

import waffleoRai_Sound.ADPCMTable;

public class NinADPCM {
	
	private ADPCMTable init_table;
	
	//private int nowps;
	private int now_shamt;
	private int now_cidx;
	private int olderSample;
	private int oldSample;
	
	private int sampleIndex;
	private int blockSize;
	
	//From: https://github.com/libertyernie/brawltools/blob/master/BrawlLib/Wii/Audio/ADPCMState.cs
	
	public NinADPCM(ADPCMTable table, int samplesPerBlock){
		init_table = table;
		blockSize = samplesPerBlock;
		reset();
	}
	
	public void reset(){
		olderSample = init_table.getStartBackSample(1);
		oldSample = init_table.getStartBackSample(0);
		//nowps = init_table.getPS();
		now_shamt = init_table.getStartShift();
		now_cidx = init_table.getStartCoeffIndex();
		sampleIndex = 0;
	}
	
	public void setToLoop(int loopIndex){
		olderSample = init_table.getLoopBackSample(1);
		oldSample = init_table.getLoopBackSample(0);
		now_shamt = init_table.getLoopShift();
		now_cidx = init_table.getLoopCoeffIndex();
		sampleIndex = loopIndex;
	}
	
	public void setShift(int val){
		now_shamt = val;
	}
	
	public void setPredictor(int val){
		now_cidx = val;
	}
	
	public void setPS(int val){
		now_shamt = val & 0xF;
		now_cidx = (val >>> 4) & 0xF;
	}
	
	public boolean newBlock(){
		return sampleIndex % blockSize == 0;
	}
	
	public int decompressNextNybble(int raw_sample){
		//Always make sure samples are sign extended!
		int init = raw_sample;
		
		if (init >= 8) init -= 16;
		//int scale = 1 << (nowps & 0xF);
		//int cIndex = ((nowps >>> 4) & 0xF) << 1;
		//int scale = 1 << now_shamt;
		//int cIndex = now_cidx << 1;
		
		//outSample = (0x400 + (scale * outSample << 11) + (_coefs[cIndex.Clamp(0, 15)] * _cyn1) + (_coefs[(cIndex + 1).Clamp(0, 15)] * _cyn2)) >> 11;
		int out = 0x400;
		//out += (scale * init) << 11;
		out += (init << now_shamt) << 11;
		//out += init_table.getCoefficient(cIndex) * oldSample;
		//out += init_table.getCoefficient(Math.min(cIndex + 1, 15)) * olderSample;
		out += init_table.getCoefficient(now_cidx, 0) * oldSample;
		out += init_table.getCoefficient(now_cidx, 1) * olderSample;
		
		out = out >> 11;
		out = Math.min(0x7FFF, out);
		out = Math.max((int)Short.MIN_VALUE, out);
		
		olderSample = oldSample;
		oldSample = out;
		sampleIndex++;
		return out;
	}

}
