package waffleoRai_soundbank.vab;

import waffleoRai_soundbank.adsr.ADSRMode;
import waffleoRai_soundbank.adsr.EnvelopeStreamer;
import waffleoRai_soundbank.adsr.Release;
import waffleoRai_soundbank.sf2.SF2;

public class VABRelease extends Release{
	
	private int shift;
	
	private int[] levels;
	
	public VABRelease(boolean Rm, int Rh)
	{
		shift = Rh;
		double s = ADSR.getRelease_seconds(Rm, Rh);
		int millis = (int)Math.round(s * 1000.0);
		if (Rm) super.setMode(ADSRMode.PSX_EXPONENTIAL_DECAY);
		else super.setMode(ADSRMode.LINEAR_ENVELOPE);
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
		long smplCount = 0;
		if (super.getMode() == ADSRMode.PSX_EXPONENTIAL_DECAY) smplCount = ADSR.samplesToDecrease_exponential(shift, 8, 0x7FFF, level);
		else if (super.getMode() == ADSRMode.LINEAR_ENVELOPE) smplCount = ADSR.samplesToDecrease_linear(shift, 8, 0x7FFF, level);
		else return -1;
		
		double seconds = ((double)smplCount/ADSR.PSX_SAMPLERATE_DBL);
		double millis = seconds * 1000.0;
		
		return (int)Math.round(millis);
	}

	public int[] getLevelTable(int sampleRate)
	{
		if(levels != null) return levels;
		if (this.getMode() == ADSRMode.LINEAR_ENVELOPE)
		{
			levels = ADSR.genTable_Decrease_Linear(shift, 8, 0x7FFF, 0);
		}
		else if (this.getMode() == ADSRMode.PSX_EXPONENTIAL_DECAY)
		{
			levels = ADSR.genTable_Decrease_Exponential(shift, 8, 0x7FFF, 0);
		}
		
		return levels;
	}
	
	public EnvelopeStreamer openStream(int sampleRate, double suslev)
	{
		int start = (int)Math.round(suslev * (double)0x7FFF);
		if (this.getMode() == ADSRMode.LINEAR_ENVELOPE)
		{
			return new VABLinearDecreaseRamper(shift, 8, start);
		}
		else if (this.getMode() == ADSRMode.PSX_EXPONENTIAL_DECAY)
		{
			return new VABExpDecreaseRamper(shift, 8, start, 0x0000);
		}
		return null;
	}
	
	public EnvelopeStreamer openStream(int sampleRate)
	{
		if (this.getMode() == ADSRMode.LINEAR_ENVELOPE)
		{
			return new VABLinearDecreaseRamper(shift, 8, 0x7FFF);
		}
		else if (this.getMode() == ADSRMode.PSX_EXPONENTIAL_DECAY)
		{
			return new VABExpDecreaseRamper(shift, 8, 0x7FFF, 0x0000);
		}
		return null;
	}
	
}
