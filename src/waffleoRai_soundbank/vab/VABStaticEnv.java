package waffleoRai_soundbank.vab;

import waffleoRai_soundbank.adsr.EnvelopeStreamer;

public class VABStaticEnv implements EnvelopeStreamer{
	
	private double ratio;
	
	public VABStaticEnv(double amp_ratio)
	{
		ratio = amp_ratio;
	}
	
	public double getNextAmpRatio()
	{
		return ratio;
	}
	
	public boolean done()
	{
		return false;
	}


}
