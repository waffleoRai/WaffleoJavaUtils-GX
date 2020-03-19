package waffleoRai_soundbank.vab;

import waffleoRai_soundbank.adsr.ADSRMode;
import waffleoRai_soundbank.adsr.EnvelopeStreamer;
import waffleoRai_soundbank.adsr.Sustain;

public class VABSustain extends Sustain{
	
	private int shift;
	private int step;
	
	private int[] levels;
	
	public VABSustain(boolean Sd, int scaledLevel)
	{
		super(scaledLevel);
		super.setDirection(!Sd);
		super.setMode(ADSRMode.STATIC);
		shift = -1;
		step = -1;
	}
	
	public VABSustain(boolean Sm, boolean Sd, int Sh, int St, int scaledLevel)
	{
		super(scaledLevel);
		super.setDirection(!Sd);
		//System.err.println("Sustain!");
		//System.err.println("Sm = " + Sm);
		//System.err.println("Sd = " + Sd);
		//System.err.println("Sh = " + Sh);
		//System.err.println("St = " + St);
		//System.err.println("Scaled Level = 0x" + Integer.toHexString(scaledLevel));
		//Sd on means down. My sustain is the opposite.
		
		shift = Sh;
		step = 7 - St;
		if(Sh == 31 && St == 3)
		{
			super.setMode(ADSRMode.STATIC);
			super.setTime(0);
			return;
		}

		double smpls = 0;
		if (Sm && Sd) 
		{
			super.setMode(ADSRMode.PSX_EXPONENTIAL_DECAY);
			step = 8 - St;
			smpls = ADSR.samplesToDecrease_exponential(shift, step, getLevel16(), 0);
			//System.err.println("Level(16) " + getLevel16());
			//System.err.println("Samples to decrease: " + smpls);
		}
		else if (!Sm && Sd) 
		{
			super.setMode(ADSRMode.LINEAR_ENVELOPE);
			step = 8 - St;
			smpls = ADSR.samplesToDecrease_linear(shift, step, getLevel16(), 0);
		}
		else if (!Sm && !Sd)
		{
			super.setMode(ADSRMode.LINEAR_ENVELOPE);
			smpls = ADSR.samplesToIncrease_linear(shift, step, getLevel16(), 0x7FFF);
		}
		else if (Sm && !Sd) 
		{
			super.setMode(ADSRMode.PSX_PSEUDOEXPONENTIAL);
			smpls = ADSR.samplesToIncrease_exponential(shift, step, getLevel16(), 0x7FFF);
		}
			
		double seconds = ((double)smpls/ADSR.PSX_SAMPLERATE_DBL);
		double millis = seconds * 1000.0;
		super.setTime((int)Math.round(millis));	
		
	}

	public int getShift()
	{
		return shift;
	}
	
	public int getStep()
	{
		return step;
	}
	
	public boolean isStatic()
	{
		return this.getMode() == ADSRMode.STATIC;
	}
	
	/*public int millisUntilEnvelopeLevel(int level)
	{
		if (level == super.getLevel()) return 0;
		long smplCount = 0;
		if (super.rampUp())
		{
			if (level < super.getLevel()) return -1;
			if (super.getMode() == ADSRMode.LINEAR_ENVELOPE)
			{
				smplCount = ADSR.samplesToIncrease_linear(shift, step, super.getLevel(), level);
			}
			else if (super.getMode() == ADSRMode.PSX_PSEUDOEXPONENTIAL)
			{
				smplCount = ADSR.samplesToIncrease_exponential(shift, step, super.getLevel(), level);
			}
			else return -1;
		}
		else
		{
			if (level > super.getLevel()) return -1;
			if (super.getMode() == ADSRMode.LINEAR_ENVELOPE)
			{
				smplCount = ADSR.samplesToDecrease_linear(shift, step, super.getLevel(), level);
			}
			else if (super.getMode() == ADSRMode.PSX_EXPONENTIAL_DECAY)
			{
				smplCount = ADSR.samplesToDecrease_exponential(shift, step, super.getLevel(), level);
			}
			else return -1;
		}
		
		double seconds = ((double)smplCount/ADSR.PSX_SAMPLERATE_DBL);
		double millis = seconds * 1000.0;
		
		return (int)Math.round(millis);
	}*/
	
	public int[] getLevelTable(int sampleRate)
	{
		if(getMode() == ADSRMode.STATIC) return null;
		if(levels != null) return levels;
		if(rampUp())
		{
			if (this.getMode() == ADSRMode.LINEAR_ENVELOPE)
			{
				levels = ADSR.genTable_Increase_Linear(shift, step, (getLevel16()), 0x7FFF);
			}
			else if (this.getMode() == ADSRMode.PSX_PSEUDOEXPONENTIAL)
			{
				levels = ADSR.genTable_Increase_Exponential(shift, step, (getLevel16()), 0x7FFF);
			}	
		}
		else
		{
			if (getMode() == ADSRMode.LINEAR_ENVELOPE)
			{
				levels = ADSR.genTable_Decrease_Linear(shift, step, (getLevel16()), 0);
			}
			else if (getMode() == ADSRMode.PSX_PSEUDOEXPONENTIAL)
			{
				levels = ADSR.genTable_Decrease_Exponential(shift, step, (getLevel16()), 0);
			}
		}
		
		return levels;
	}
	
	public EnvelopeStreamer openStream(int sampleRate)
	{
		if(getMode() == ADSRMode.STATIC) return new VABStaticEnv((double)getLevel32()/(double)0x7FFFFFFF);
		
		int suslev = getLevel16();
		if(rampUp())
		{
			if (this.getMode() == ADSRMode.LINEAR_ENVELOPE)
			{
				return new VABLinearIncreaseRamper(shift, step, suslev);
			}
			else if (this.getMode() == ADSRMode.PSX_PSEUDOEXPONENTIAL)
			{
				return new VABPEIncreaseRamper(shift, step, suslev);
			}	
		}
		else
		{
			if (getMode() == ADSRMode.LINEAR_ENVELOPE)
			{
				return new VABLinearDecreaseRamper(shift, step, suslev);
			}
			else if (getMode() == ADSRMode.PSX_EXPONENTIAL_DECAY)
			{
				//System.err.println("suslev = " + suslev);
				return new VABExpDecreaseRamper(shift, step, suslev, 0);
			}
		}
		
		return null;
	}
	
}
