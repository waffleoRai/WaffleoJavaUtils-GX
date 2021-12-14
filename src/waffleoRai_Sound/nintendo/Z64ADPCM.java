package waffleoRai_Sound.nintendo;

import waffleoRai_Sound.ADPCMCoder;
import waffleoRai_SoundSynth.AudioSampleStream;
import waffleoRai_Utils.FileBuffer;

public class Z64ADPCM implements ADPCMCoder{
	
	//This is from N64SoundListTool's N64AIFCAudio.cpp 
	//  & the oot decomp audio branch by engineer124, simonlindholm, and MNGoldenEagle
	
	public static final double ENC_DEFO_THRESH = 10.0;
	public static final int ENC_DEFO_ITERS = 2;
	
	/*----- Instance Variables -----*/
	
	private boolean twobit;
	private N64ADPCMTable table;
	
	private int[] lastBlock;
	//private int[] lastBlock_loop;
	
	private int[] thisBlock;
	private int[] thisBlock_raw;
	private int sampleIndex;
	
	private int pred_idx;
	private int shamt;
	
	/*----- Init -----*/
	
	public Z64ADPCM(N64ADPCMTable adpcm_table){
		table = adpcm_table;
		//s0 = 0; s1 = 0;
		lastBlock = new int[8];
		thisBlock = new int[8];
		thisBlock_raw = new int[8];
		sampleIndex = 0;
		pred_idx = 0; shamt = 0;
	}
	
	/*----- Getters -----*/
	
	public N64ADPCMTable getTable() {return table;}
	public int getSamplesPerBlock() {return 16;}
	public boolean getHiNybbleFirst() {return true;}
	public int getBytesPerBlock() {return twobit?5:9;}
	
	/*----- Setters -----*/
	
	public void setTwoBit(){twobit = true;}
	public void setFourBit(){twobit = false;}
	
	/*----- Decoding -----*/
	
	public void reset() {
		for(int i = 0; i < 8; i++){
			lastBlock[i] = 0;
			thisBlock[i] = 0;
			thisBlock_raw[i] = 0;
		}
		sampleIndex = 0;
		pred_idx = 0; shamt = 0;
	}

	public void setToLoop(int loopIndex) {
		reset();
		sampleIndex = loopIndex;
		shamt = table.getLoopShift();
		pred_idx = table.getLoopCoeffIndex();
		for(int i = 0; i < table.getOrder(); i++){
			lastBlock[7-i] = table.getLoopBackSample(i);
		}
	}

	@Override
	public void setControlByte(int val) {
		//shamt is high
		shamt = (val >>> 4) & 0xF;
		pred_idx = val & 0xF;
	}

	public boolean newBlock() {
		return (sampleIndex>= 16);
	}

	@Override
	public int decompressNextNybble(int raw_sample) {
		//Sign extend (actually, force sign extend BEFORE passing in -- easier to handle both 2&4 bit samps)
		//int in = raw_sample;
		//if(raw_sample >= 8) in = raw_sample - 16;
		
		int in = raw_sample;
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
		
		in <<= shamt;
		thisBlock_raw[subidx] = in;
		
		int ip = 0; int order = table.getOrder();
		//Factor in (order) samples from previous subblock
		for(int i = 0; i < order; i++){
			ip += lastBlock[7-i] * table.getCoefficient(pred_idx, i, subidx);
		}
		
		int omax = order-1;

		for(int i = subidx; i > 0; i--){
			int j = i-1;
			ip += (thisBlock_raw[subidx-1-j] * table.getCoefficient(pred_idx, omax, j));
		}
		
		//We'll div the ip before adding like in decomp.
		ip >>>= 11;
		int out = in + ip;
		
		//Alt for adding ip before dividing
		/*int out = in << 11;
		out += ip;
		out >>= 11;*/
		
		//Clamp
		out = Math.max((int)Short.MIN_VALUE, out);
		out = Math.min((int)Short.MAX_VALUE, out);
		
		//Save sample
		thisBlock[subidx] = out;
		sampleIndex++;
		
		return out;
	}

	/*----- Encoding -----*/
	
	public static N64ADPCMTable buildTable(AudioSampleStream input, int samples, boolean smallSamples){
		return buildTable(input, 0, samples, smallSamples);
	}
	
	public static N64ADPCMTable buildTable(AudioSampleStream input, int channel, int samples, boolean smallSamples){
		VADPCM encoder = new VADPCM();
		encoder.setOrder(2);
		encoder.setRefineIterations(ENC_DEFO_ITERS);
		encoder.setThreshold(ENC_DEFO_THRESH);
		encoder.setSamplesPerPackage(16);
		if(smallSamples)encoder.setTargetBitDepth(2);
		else encoder.setTargetBitDepth(4);
		
		return encoder.buildTable(input, samples, channel);
	}
	
	public FileBuffer encode(AudioSampleStream input, int channel, int samples) throws InterruptedException{
		VADPCM encoder = new VADPCM();
		encoder.setOrder(2);
		encoder.setSamplesPerPackage(16);
		if(twobit)encoder.setTargetBitDepth(2);
		else encoder.setTargetBitDepth(4);
		
		FileBuffer buff = encoder.encodeAudio(input, table, samples, channel);
		
		return buff;
	}
	
}
