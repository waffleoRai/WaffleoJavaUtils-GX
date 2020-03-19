package waffleoRai_soundbank.vab;

import waffleoRai_soundbank.adsr.EnvelopeStreamer;

public class VABLinearDecreaseRamper implements EnvelopeStreamer{
	
	private static final double MAX = (double)0x7FFF;
	
	private int cycles;
	private int truStep;
	
	private int initLv;
	private int finalLv;
	private int currentLv;
	
	private int ctr;
	
	public VABLinearDecreaseRamper(int shift, int step, int initLevel)
	{
		int stepFactor = 11 - shift;
		if(stepFactor < 0) stepFactor = 0;
		int timeFactor = shift - 11;
		if(timeFactor < 0) timeFactor = 0;
		
		cycles = 1 << timeFactor; //Cycles per step
		truStep = step << stepFactor; //Step size
		
		initLv = initLevel;
		currentLv = initLv;
		finalLv = 0x0000;
		
		ctr = 0;
	}
	
	public double getNextAmpRatio()
	{
		if(currentLv <= finalLv) return 0.0;
		double ratio = (double)currentLv/MAX;
		
		if(++ctr >= cycles)
		{
			currentLv -= truStep;
			ctr = 0;
		}
		
		return ratio;
	}
	
	public boolean done()
	{
		return (currentLv <= finalLv);
	}

	
}
