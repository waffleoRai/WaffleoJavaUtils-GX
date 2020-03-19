package waffleoRai_soundbank.vab;

import waffleoRai_soundbank.adsr.ADSRMode;
import waffleoRai_soundbank.adsr.Decay;
import waffleoRai_soundbank.adsr.EnvelopeStreamer;
import waffleoRai_soundbank.sf2.SF2;

public class VABDecay extends Decay{
	
	private int shift;
	
	private int[] levels;
	
	public VABDecay(int Dh)
	{
		shift = Dh;
		double s = ADSR.getRelease_seconds(true, Dh);
		int millis = (int)Math.round(s * 1000.0);
		super.setMode(ADSRMode.PSX_EXPONENTIAL_DECAY);
		super.setTime(millis);
	}
	
	public int getShift()
	{
		return shift;
	}
	
	public int millisForCentibelDecrease(int cB)
	{
		double ratio = SF2.centibelsToEnvelopeRatio(cB);
		double level = (double)0x7FFF / ratio;
		int lv = (int)Math.round(level);
		return millisUntilEnvelopeLevel(lv);
	}
	
	public int millisUntilEnvelopeLevel(int level)
	{
		if (level > 0x7FFF) return -1;
		long smplCount = ADSR.samplesToDecrease_exponential(shift, 8, 0x7FFF, level);
		
		double seconds = ((double)smplCount/ADSR.PSX_SAMPLERATE_DBL);
		double millis = seconds * 1000.0;
		
		return (int)Math.round(millis);
	}

	public int[] getLevelTable(int sampleRate)
	{
		if(levels != null) return levels;
		levels = ADSR.genTable_Decrease_Exponential(shift, 8, 0x7FFF, 0);
		
		return levels;
	}
	
	public EnvelopeStreamer openStream(int sampleRate, double suslev)
	{
		int s = (int)Math.round(suslev * (double)0x7FFF);
		return new VABExpDecreaseRamper(shift, 8, 0x7FFF, s);
	}
	
	public EnvelopeStreamer openStream(int sampleRate)
	{
		return new VABExpDecreaseRamper(shift, 8, 0x7FFF, 0x0000);
	}
	
}
