package waffleoRai_soundbank.vab;

import waffleoRai_soundbank.adsr.EnvelopeStreamer;

public class VABPEIncreaseRamper implements EnvelopeStreamer{

	private static final double MAX = (double)0x7FFF;
	private static final int THRESHOLD = 0x6000;
	
	private int cycles;
	private int hicycles;
	private int truStep;
	
	private int initLv;
	private int finalLv;
	private int currentLv;
	
	private int ctr;
	
	public VABPEIncreaseRamper(int shift, int step, int initLevel)
	{
		int stepFactor = 11 - shift;
		if(stepFactor < 0) stepFactor = 0;
		int timeFactor = shift - 11;
		if(timeFactor < 0) timeFactor = 0;
		
		cycles = 1 << timeFactor; //Cycles per step
		hicycles = cycles << 2;
		truStep = step << stepFactor; //Step size
		
		initLv = initLevel;
		currentLv = initLv;
		finalLv = 0x7FFF;
		
		ctr = 0;
	}
	
	public double getNextAmpRatio()
	{
		if(currentLv >= finalLv) return 1.0;
		double ratio = (double)currentLv/MAX;
		
		if(currentLv >= THRESHOLD)
		{
			if(++ctr >= hicycles)
			{
				currentLv += truStep;
				ctr = 0;
			}
		}
		else
		{
			if(++ctr >= cycles)
			{
				currentLv += truStep;
				ctr = 0;
			}	
		}
		
		return ratio;
	}
	
	public boolean done()
	{
		return (currentLv >= finalLv);
	}

	
}
