package waffleoRai_soundbank.vab;

import waffleoRai_soundbank.adsr.EnvelopeStreamer;

public class VABExpDecreaseRamper implements EnvelopeStreamer{
	
	private static final double MAX = (double)0x8000;
	private static final int THRESHOLD = 0x888;
	
	private int cycles;
	private double truStep;
	private double minStep;
	
	private int initLv;
	private int finalLv;
	private double currentLv;
	
	private int ctr;
	
	public VABExpDecreaseRamper(int shift, int step, int initLevel, int finalLevel)
	{
		int stepFactor = 11 - shift;
		if(stepFactor < 0) stepFactor = 0;
		int timeFactor = shift - 11;
		if(timeFactor < 0) timeFactor = 0;
		
		cycles = 1 << timeFactor; //Cycles per step
		truStep = step << stepFactor; //Step size
		
		initLv = initLevel;
		currentLv = (double)initLv;
		finalLv = finalLevel;
		
		//final int maxEnv = 0x8000;
		//double tlev_float = (double)finalLevel;
		//double nstep = 0.0;
		
		minStep = truStep * ((double)THRESHOLD/MAX);
		
		ctr = 0;
	}
	
	public double getNextAmpRatio()
	{
		if(currentLv <= finalLv) return 0.0;
		double ratio = currentLv/MAX;
		
		if(++ctr >= cycles)
		{
			double nstep = truStep * (currentLv/MAX);
			if (nstep < minStep) nstep = minStep;
			currentLv -= nstep;
			ctr=0;
		}
		
		return ratio;
	}
	
	public boolean done()
	{
		return (currentLv <= finalLv);
	}


}
