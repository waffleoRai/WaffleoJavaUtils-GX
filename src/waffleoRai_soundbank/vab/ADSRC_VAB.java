package waffleoRai_soundbank.vab;

import waffleoRai_soundbank.Region;
import waffleoRai_soundbank.adsr.ADSRMode;
import waffleoRai_soundbank.adsr.Attack;
import waffleoRai_soundbank.adsr.Decay;
import waffleoRai_soundbank.adsr.Release;
import waffleoRai_soundbank.adsr.Sustain;
import waffleoRai_soundbank.sf2.SF2;
import waffleoRai_soundbank.sf2.ADSR.SF2ADSRConverter;

public class ADSRC_VAB implements SF2ADSRConverter{

	//private Attack att;
	//private Decay dec;
	private Sustain sus;
	//private Release rel;
	
	private int i_cB;
	private int d_cB_lin;
	private int d_cB_ex;
	
	private boolean use_decaying_sus;
	
	public ADSRC_VAB()
	{
		//att = null;
		//dec = null;
		sus = null;
		//rel = null;
		
		i_cB = 120;
		d_cB_lin = 120;
		d_cB_ex = 120;
		
		use_decaying_sus = true;
	}
	
	public ADSRC_VAB(int increaseMatch_centibels, int decreaseMatch_centibels_l, int decreaseMatch_centibels_e, boolean ignore_decaying_sustains)
	{
		//att = null;
		//dec = null;
		sus = null;
		//rel = null;
		
		i_cB = increaseMatch_centibels;
		d_cB_lin = decreaseMatch_centibels_l;
		d_cB_ex = decreaseMatch_centibels_e;
		
		use_decaying_sus = !ignore_decaying_sustains;
	}
	
	@Override
	public void calibrate(Region r) 
	{
		//att = r.getAttack();
		//dec = r.getDecay();
		sus = r.getSustain();
		//rel = r.getRelease();
	}

	@Override
	public int getAttack(Attack a)
	{
		if (a.getMode() != ADSRMode.PSX_PSEUDOEXPONENTIAL) SF2.millisecondsToTimecents(a.getTime());
	
		if (a instanceof VABAttack) 
		{
			//Get reference envelope level
			double ratio = SF2.centibelsToEnvelopeRatio(i_cB);
			int level32 = (int)Math.round((double)0x7FFFFFFF/ratio);
			int millisToRef = 0;
			int level16 = level32 >>> 16; //Floored, not rounded. No real reason, just lazy. 
			VABAttack va = (VABAttack) a;
			millisToRef = va.millisUntilEnvelopeLevel(level16);
			//Create a new linear model from this.
			double tmax = ((double)0x7FFF * (double)millisToRef)/(double)level16;
			int t = (int)Math.round(tmax);
			return SF2.millisecondsToTimecents(t);
		}
		else
		{
			//If we don't have the attack shift/step, we'll just have to return a wild(er) guess.
			//Just return 3/4 the time.
			int t = (int)Math.round((double)a.getTime() * 0.75);
			return SF2.millisecondsToTimecents(t);
		}
	}

	@Override
	public int getDecay(Decay d) 
	{
		if (d.getMode() != ADSRMode.PSX_EXPONENTIAL_DECAY) return SF2.millisecondsToTimecents(d.getTime());
		
		//This function takes sustain into account
		//If set to, it will include a decaying sustain into the decay (allowing the sustain to be a constant 0)
		int Dh = -1;
		//int decayMillis = -1;
		int susLev = sus.getLevel32();
		susLev = (int)Math.round(((double)susLev/(double)0x7FFFFFFF) * (double)0x7FFF);
		if (d instanceof VABDecay)
		{
			//We have the decay shift, and we can use it.
			Dh = ((VABDecay) d).getShift();
		}
		else
		{
			//We'll just have to make a wild guess from the time
			long mysamps = Math.round((double)d.getTime()/1000.0 * ADSR.PSX_SAMPLERATE_DBL);
			for (int i = 0; i < 12; i++)
			{
				long samps = ADSR.samplesToDecrease_exponential(i, 8, 0x7FFF, 0);
				if (samps >= mysamps-1000)
				{
					Dh = i;
					break;
				}
			}
		}
		
		if (Dh < 0) 
		{
			System.err.println("ADSRC_VAB.getDecay || CONVERSION FAILED. Returning stored time...");
			return SF2.millisecondsToTimecents(d.getTime());
		}
		
		
		//See if sustain is decaying
		if (use_decaying_sus && sus.getMode() != ADSRMode.STATIC && !sus.rampUp() && susLev > 0)
		{
			//Yes. Figure out how to add the sustain to the decay
			
			//1. Figure out whether the matchup point is in the decay phase or the sustain phase
			int env_match = (int)Math.round((double)0x7FFF/SF2.centibelsToEnvelopeRatio(d_cB_ex));
			
			boolean b = false;
			int cb = d_cB_ex;
			while (env_match >= susLev && cb < 970)
			{
				b = true;
				//Decay phase
				//Lower the envelope until it is well into the sustain phase
				cb += 30; //By 3dB
			}
			if (b) {
				cb += 30; //One more time
				env_match = (int)Math.round((double)0x7FFF/SF2.centibelsToEnvelopeRatio(cb));
			}
			
			//2. Figure out what the envelope level is at the desired point
				//2-1. First calculate samples it takes to decay
			long decSamps = ADSR.samplesToDecrease_exponential(Dh, 8, 0x7FFF, susLev);
				//2-2. Now the samples in sustain
			long susSamps = 0;
			if (sus instanceof VABSustain)
			{
				VABSustain vsus = (VABSustain)sus;
				if (vsus.getMode() == ADSRMode.LINEAR_ENVELOPE) susSamps = ADSR.samplesToDecrease_linear(vsus.getShift(), vsus.getStep(), susLev, env_match);
				else if (vsus.getMode() == ADSRMode.PSX_EXPONENTIAL_DECAY) susSamps = ADSR.samplesToDecrease_exponential(vsus.getShift(), vsus.getStep(), susLev, env_match);
				else
				{
					System.err.println("ADSRC_VAB.getDecay || CONVERSION FAILED. Returning stored time...");
					return SF2.millisecondsToTimecents(d.getTime());
				}
			}
			else
			{
				//Guess from time. Linear envelope model. This will come out sounding too slow if really exp.
				int susdiff = susLev - env_match;
				double susrat = (double)susdiff/(double)susLev;
				double smillis = susrat * (double)sus.getTime();
				susSamps = Math.round((smillis/1000.0) * ADSR.PSX_SAMPLERATE_DBL);
			}
			
			//Now we have time in samples
			long totalSamps = decSamps + susSamps;
			double ratio = (double)cb/(double)totalSamps;
			long timeSamps = Math.round(1000.0/ratio);
			int millis = (int)Math.round(((double)timeSamps/ADSR.PSX_SAMPLERATE_DBL)*1000.0);
			return SF2.millisecondsToTimecents(millis);
		}
		else
		{
			//Static or increasing.
			//Increasing is ignored in SF2.
			//Just calculate and return the decay time
			int env_match = (int)Math.round((double)0x7FFF/SF2.centibelsToEnvelopeRatio(d_cB_ex));
			long decSamps = ADSR.samplesToDecrease_exponential(Dh, 8, 0x7FFF, env_match);
			
			double ratio = (double)d_cB_ex/(double)decSamps;
			long timeSamps = Math.round(1000.0/ratio);
			int millis = (int)Math.round(((double)timeSamps/ADSR.PSX_SAMPLERATE_DBL)*1000.0);
			return SF2.millisecondsToTimecents(millis);
		}
	}

	@Override
	public int getRelease(Release r) 
	{
		if (r.getMode() != ADSRMode.PSX_EXPONENTIAL_DECAY && r.getMode() != ADSRMode.LINEAR_ENVELOPE) return SF2.millisecondsToTimecents(r.getTime());
		
		int ref_cb = d_cB_ex;
		if (r.getMode() == ADSRMode.LINEAR_ENVELOPE) ref_cb = d_cB_lin;
		
		if (r instanceof VABRelease)
		{
			VABRelease vr = (VABRelease)r;
			int millisToRef = vr.millisForCentibelDecrease(ref_cb);
			double ratio = 0.0;
			
			millisToRef = vr.millisForCentibelDecrease(ref_cb);
			ratio = (double)ref_cb/(double)millisToRef;
			
			int totaltime = (int)Math.round(1000.0/ratio);
			return SF2.millisecondsToTimecents(totaltime);
		}
		else
		{
			//Assumes linear envelope. Will come out a bit slow if not.
			double ratio = SF2.centibelsToEnvelopeRatio(ref_cb);
			
			int env = (int)Math.round((double)0x7FFF/ratio);
			int umil = r.getTime();
			double lmax = (double)0x7FFF;
			double n = lmax - (double)env;
			n *= (double)umil;
			n /= lmax;
			int env_time = (int)Math.round(n);
			
			//Now, look at using a linear centibel model
			n = (double)env_time * 1000.0; //1000 for 100 dB (1000 cB)
			n /= (double)ref_cb;
			int smil = (int)Math.round(n);
			return SF2.millisecondsToTimecents(smil);
		}

	}

	@Override
	public int getSustain(Sustain s)
	{
		//This function returns 0 if the sustain is decaying and the converter
		//	is set to move this sustain-decay to the decay phase
		
		if (use_decaying_sus && sus.getMode() != ADSRMode.STATIC && !sus.rampUp()) return 0;
		
		return s.getLevel32();
	}

	public int getDelay(int ms_d)
	{
		return SF2.millisecondsToTimecents(ms_d);
	}
	
	public int getHold(int ms_h)
	{
		return SF2.millisecondsToTimecents(ms_h);
	}

}
