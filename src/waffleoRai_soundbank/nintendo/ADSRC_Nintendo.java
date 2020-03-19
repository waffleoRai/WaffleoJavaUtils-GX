package waffleoRai_soundbank.nintendo;

import waffleoRai_soundbank.Region;
import waffleoRai_soundbank.adsr.Attack;
import waffleoRai_soundbank.adsr.Decay;
import waffleoRai_soundbank.adsr.Release;
import waffleoRai_soundbank.adsr.Sustain;
import waffleoRai_soundbank.sf2.SF2;
import waffleoRai_soundbank.sf2.ADSR.SF2ADSRConverter;

public class ADSRC_Nintendo implements SF2ADSRConverter{

	@Override
	public void calibrate(Region r) 
	{
		// TODO
	}

	@Override
	public int getAttack(Attack a) 
	{
		return SF2.millisecondsToTimecents(a.getTime());
	}

	private int scaleFall(int raw)
	{
		//Linear envelope to linear dB
		//By matching the time it takes to get to a specified dB
		
		//Match at -20 dB
		//Level at 20 dB
		//dB = 10 * log10(end/start)
		final int dbMatch = -20;
		double r = Math.pow(10.0, dbMatch/10.0);
		//int envLvl = (int)Math.round(r * (double)0x16980);
		int t = (int)Math.round((double)raw * r);
		double f = 100.0/((-1.0)*dbMatch);
		int dbmils = (int)Math.round((double)t * f);
		
		return dbmils;
	}
	
	@Override
	public int getDecay(Decay d) 
	{
		return SF2.millisecondsToTimecents(scaleFall(d.getTime()));
	}

	@Override
	public int getRelease(Release r) 
	{
		return SF2.millisecondsToTimecents(scaleFall(r.getTime()));
	}

	@Override
	public int getSustain(Sustain s) 
	{
		return s.getLevel32();
	}

	@Override
	public int getDelay(int ms_d) 
	{
		return SF2.millisecondsToTimecents(ms_d);
	}

	@Override
	public int getHold(int ms_h) 
	{
		return SF2.millisecondsToTimecents(ms_h);
	}

}
