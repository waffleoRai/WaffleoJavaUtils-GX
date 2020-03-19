package waffleoRai_soundbank.vab;

import waffleoRai_soundbank.adsr.ADSRMode;
import waffleoRai_soundbank.adsr.Attack;
import waffleoRai_soundbank.adsr.EnvelopeStreamer;

public class VABAttack extends Attack{

	private int shift;
	private int step;
	
	private int[] levels;
	
	public VABAttack(boolean Am, int Ah, int At)
	{
		double s = ADSR.getAttack_seconds(Am, Ah, At);
		int millis = (int)Math.round(s * 1000.0);
		ADSRMode mode = ADSRMode.LINEAR_ENVELOPE;
		if (Am) mode = ADSRMode.PSX_PSEUDOEXPONENTIAL;
		super.setMode(mode);
		super.setTime(millis);
		shift = Ah;
		step = 7 - At;
	}
	
	public int getShift()
	{
		return shift;
	}
	
	public int getStep()
	{
		return step;
	}
	
	public int millisUntilEnvelopeLevel(int level)
	{
		if (level < 0) return -1;
		long smplCount = 0;
		if (this.getMode() == ADSRMode.LINEAR_ENVELOPE)
		{
			smplCount = ADSR.samplesToIncrease_linear(shift, step, 0, level);
		}
		else if (this.getMode() == ADSRMode.PSX_PSEUDOEXPONENTIAL)
		{
			smplCount = ADSR.samplesToIncrease_exponential(shift, step, 0, level);
		}
		else return 0;
		double seconds = ((double)smplCount/ADSR.PSX_SAMPLERATE_DBL);
		double millis = seconds * 1000.0;
		
		return (int)Math.round(millis);
	}
	
	public int[] getLevelTable(int sampleRate)
	{
		if(levels != null) return levels;
		if (this.getMode() == ADSRMode.LINEAR_ENVELOPE)
		{
			levels = ADSR.genTable_Increase_Linear(shift, step, 0, 0x7FFF);
		}
		else if (this.getMode() == ADSRMode.PSX_PSEUDOEXPONENTIAL)
		{
			levels = ADSR.genTable_Increase_Exponential(shift, step, 0, 0x7FFF);
		}
		
		return levels;
	}
	
	public EnvelopeStreamer openStream(int sampleRate)
	{
		if (this.getMode() == ADSRMode.LINEAR_ENVELOPE)
		{
			return new VABLinearIncreaseRamper(shift, step, 0x0000);
		}
		else
		{
			return new VABPEIncreaseRamper(shift, step, 0x0000);
		}
	}
	
}
