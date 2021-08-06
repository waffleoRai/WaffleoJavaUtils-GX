package waffleoRai_Sound.nintendo;

public class NinADPCM {
	
	private ADPCMTable init_table;
	
	private int nowps;
	private int olderSample;
	private int oldSample;
	
	private int sampleIndex;
	private int blockSize;
	
	//From: https://github.com/libertyernie/brawltools/blob/master/BrawlLib/Wii/Audio/ADPCMState.cs
	
	public NinADPCM(ADPCMTable table, int samplesPerBlock)
	{
		init_table = table;
		olderSample = init_table.getHistorySample2();
		oldSample = init_table.getHistorySample1();
		nowps = init_table.getPS();
		sampleIndex = 0;
		blockSize = samplesPerBlock;
	}
	
	public void reset()
	{
		olderSample = init_table.getHistorySample2();
		oldSample = init_table.getHistorySample1();
		nowps = init_table.getPS();
		sampleIndex = 0;
	}
	
	public void setToLoop(int loopIndex)
	{
		nowps = init_table.getLoopPS();
		olderSample = init_table.getLoopHistorySample2();
		oldSample = init_table.getLoopHistorySample1();
		sampleIndex = loopIndex;
	}
	
	public void setPS(int val)
	{
		nowps = val;
	}
	
	public boolean newBlock()
	{
		return sampleIndex % blockSize == 0;
	}
	
	public int decompressNextNybble(int raw_sample)
	{
		//Always make sure samples are sign extended!
		int init = raw_sample;
		
		if (init >= 8) init -= 16;
		int scale = 1 << (nowps & 0xF);
		int cIndex = ((nowps >>> 4) & 0xF) << 1;
		
		//outSample = (0x400 + (scale * outSample << 11) + (_coefs[cIndex.Clamp(0, 15)] * _cyn1) + (_coefs[(cIndex + 1).Clamp(0, 15)] * _cyn2)) >> 11;
		int out = 0x400;
		out += (scale * init) << 11;
		out += init_table.getCoefficient(cIndex) * oldSample;
		out += init_table.getCoefficient(Math.min(cIndex + 1, 15)) * olderSample;
		
		out = out >> 11;
		out = Math.min(0x7FFF, out);
		out = Math.max((int)Short.MIN_VALUE, out);
		
		olderSample = oldSample;
		oldSample = out;
		sampleIndex++;
		return out;
	}

}
