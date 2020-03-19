package waffleoRai_soundbank.vab;

import java.util.ArrayList;

import waffleoRai_soundbank.adsr.Attack;
import waffleoRai_soundbank.adsr.Decay;
import waffleoRai_soundbank.adsr.Release;
import waffleoRai_soundbank.adsr.Sustain;

public class ADSR {
	
	public static final int PSX_SAMPLERATE = 44100;
	public static final double PSX_SAMPLERATE_DBL = 44100.0;

	public static int[] genTable_Increase_Linear(int shift, int step, int initLv, int targetLv)
	{
		ArrayList<Integer> templist = new ArrayList<Integer>(44100);
		
		int stepFactor = 11 - shift;
		if(stepFactor < 0) stepFactor = 0;
		int timeFactor = shift - 11;
		if(timeFactor < 0) timeFactor = 0;
		
		int cycles = 1 << timeFactor; //Cycles per step
		int truStep = step << stepFactor; //Step size
		
		int level = initLv;
		
		//long sampleCount = 0;
		//System.err.println("cycles = " + cycles);
		//System.err.println("truStep = " + truStep);
		while (level < targetLv)
		{
			//sampleCount += cycles;
			if(level <= 0x7FFF){
				for(int c = 0; c < cycles; c++){templist.add(level);}
			}
			level += truStep;
			//System.err.println("level = 0x" + Integer.toHexString(level));
		}
		int[] out = new int[templist.size()];
		for(int i = 0; i < out.length; i++)out[i] = templist.get(i);
		
		return out;
	}
	
	public static int[] genTable_Increase_Exponential(int shift, int step, int initLv, int targetLv)
	{
		ArrayList<Integer> templist = new ArrayList<Integer>(44100);
		
		int stepFactor = 11 - shift;
		if(stepFactor < 0) stepFactor = 0;
		int timeFactor = shift - 11;
		if(timeFactor < 0) timeFactor = 0;
		
		final int threshold = 0x6000;
		
		int cycles = 1 << timeFactor; //Cycles per step
		int hicycles = cycles << 2; //Above 0x6000, it slows down by a factor of 4
		int truStep = step << stepFactor; //Step size
		
		//double stepsPerCycle = (double)truStep/(double)cycles;
		//double stepsPerCycleHi = (double)truStep/(double)hicycles;
		
		int level = initLv;
		
		while (level < targetLv){
			if (level > threshold) {
				if(level <= 0x7FFF){
					for(int c = 0; c < hicycles; c++) templist.add(level);
				}
			}
			else{
				if(level <= 0x7FFF){
					for(int c = 0; c < cycles; c++) templist.add(level);
				}
			}
			level += truStep;
		}
		
		int[] out = new int[templist.size()];
		for(int i = 0; i < out.length; i++)out[i] = templist.get(i);
		
		return out;
	}
	
	public static int[] genTable_Decrease_Linear(int shift, int step, int initLv, int targetLv)
	{
		ArrayList<Integer> templist = new ArrayList<Integer>(44100);
		
		int stepFactor = 11 - shift;
		if(stepFactor < 0) stepFactor = 0;
		int timeFactor = shift - 11;
		if(timeFactor < 0) timeFactor = 0;
		
		int cycles = 1 << timeFactor; //Cycles per step
		int truStep = step << stepFactor; //Step size
		
		//double stepsPerCycle = (double)truStep/(double)cycles;
		
		int level = initLv;
		
		while (level > targetLv)
		{
			if(level >= 0x0000){
				for(int c = 0; c < cycles; c++){templist.add(level);}
			}
			level -= truStep;
		}
		
		int[] out = new int[templist.size()];
		for(int i = 0; i < out.length; i++)out[i] = templist.get(i);
		
		return out;
	}
	
	public static int[] genTable_Decrease_Exponential(int shift, int step, int initLv, int targetLv)
	{
		ArrayList<Integer> templist = new ArrayList<Integer>(44100);
		
		int stepFactor = 11 - shift;
		if(stepFactor < 0) stepFactor = 0;
		int timeFactor = shift - 11;
		if(timeFactor < 0) timeFactor = 0;
		
		final int maxEnv = 0x8000;
		final int threshold = 0x888; //If it falls below this, make linear
		
		int cycles = 1 << timeFactor; //Cycles per step
		int truStep = step << stepFactor; //Step size
		
		double level = (double)initLv;
		double tlev_float = (double)targetLv;
		
		double nstep = 0.0;
		double step_float = (double)truStep;
		
		final double minStep = step_float * ((double)threshold/(double)maxEnv); //The minimum stepsize
		
		while ((level > tlev_float))
		{
			//WARNING: Asymptotic behavior around zero!!
			//This is fixed by making it decrease linearly if the stepsize gets too small
			nstep = step_float * (level/(double)maxEnv);
			if (nstep < minStep) nstep = minStep;
			if(level >= 0x0000){
				int ilvl = (int)Math.round(level);
				for(int c = 0; c < cycles; c++){templist.add(ilvl);}
			}
			level -= nstep;
		}
		
		int[] out = new int[templist.size()];
		for(int i = 0; i < out.length; i++)out[i] = templist.get(i);
		
		return out;
	}
	
	public static long samplesToIncrease_linear(int shift, int step, int initLv, int targetLv)
	{
		int stepFactor = 11 - shift;
		if(stepFactor < 0) stepFactor = 0;
		int timeFactor = shift - 11;
		if(timeFactor < 0) timeFactor = 0;
		
		int cycles = 1 << timeFactor; //Cycles per step
		int truStep = step << stepFactor; //Step size
		
		double stepsPerCycle = (double)truStep/(double)cycles;
		
		int level = initLv;
		
		long sampleCount = 0;
		
		while (level < targetLv)
		{
			sampleCount += cycles;
			level += truStep;
		}
		
		if (cycles > 1)
		{
			double lvDoub = (double)level;
			double tDoubl = (double)targetLv;
			while(lvDoub > tDoubl)
			{
				lvDoub -= stepsPerCycle;
				sampleCount--;
			}
			sampleCount++; //Assuming overshot
		}
		
		//System.err.println("ADSR.samplesToIncrease_linear(" + shift + "," + step + "," + initLv + "," + targetLv + "|| Sample count: " + sampleCount);
		return sampleCount;
	}
	
	public static long samplesToDecrease_linear(int shift, int step, int initLv, int targetLv)
	{
		int stepFactor = 11 - shift;
		if(stepFactor < 0) stepFactor = 0;
		int timeFactor = shift - 11;
		if(timeFactor < 0) timeFactor = 0;
		
		int cycles = 1 << timeFactor; //Cycles per step
		int truStep = step << stepFactor; //Step size
		
		double stepsPerCycle = (double)truStep/(double)cycles;
		
		int level = initLv;
		
		long sampleCount = 0;
		
		while (level > targetLv)
		{
			sampleCount += cycles;
			level -= truStep;
		}
		
		if (cycles > 1)
		{
			double lvDoub = (double)level;
			double tDoubl = (double)targetLv;
			while(lvDoub < tDoubl)
			{
				lvDoub += stepsPerCycle;
				sampleCount--;
			}
			sampleCount++; //Assuming overshot
		}
		
		//System.err.println("ADSR.samplesToDecrease_linear(" + shift + "," + step + "," + initLv + "," + targetLv + ") || Sample count: " + sampleCount);
		return sampleCount;
	}
		
	public static long samplesToIncrease_exponential(int shift, int step, int initLv, int targetLv)
	{
		//According to Nocash, the exponential increase isn't exactly exponential...
		//It just changes the linear slope at levels above 0x6000
		
		//IF exponential AND increase AND AdsrLevel>6000h THEN AdsrCycles=AdsrCycles*4
		
		int stepFactor = 11 - shift;
		if(stepFactor < 0) stepFactor = 0;
		int timeFactor = shift - 11;
		if(timeFactor < 0) timeFactor = 0;
		
		final int threshold = 0x6000;
		
		int cycles = 1 << timeFactor; //Cycles per step
		int hicycles = cycles << 2; //Above 0x6000, it slows down by a factor of 4
		int truStep = step << stepFactor; //Step size
		
		double stepsPerCycle = (double)truStep/(double)cycles;
		double stepsPerCycleHi = (double)truStep/(double)hicycles;
		
		int level = initLv;
		
		long sampleCount = 0;
		
		while (level < targetLv)
		{
			if (level > threshold) sampleCount += hicycles;
			else sampleCount += cycles;
			level += truStep;
		}
		
		if (cycles > 1)
		{
			double lvDoub = (double)level;
			double tDoubl = (double)targetLv;
			while(lvDoub > tDoubl)
			{
				if(level <= threshold) lvDoub -= stepsPerCycle;
				else lvDoub -= stepsPerCycleHi;
				sampleCount--;
			}
			sampleCount++; //Assuming overshot
		}
		//System.err.println("ADSR.samplesToIncrease_exponential(" + shift + "," + step + "," + initLv + "," + targetLv + ") || Sample count: " + sampleCount);
		return sampleCount;
	}
	
	public static long samplesToDecrease_exponential(int shift, int step, int initLv, int targetLv)
	{
		//IF exponential AND decrease THEN AdsrStep=AdsrStep*AdsrLevel/8000h [Nocash]
		
		//String debugString = "ADSR.samplesToDecrease_exponential(" + shift + "," + step + "," + initLv + "," + targetLv + ")";
		//System.err.println(debugString + " || Function Entered.");
		int stepFactor = 11 - shift;
		if(stepFactor < 0) stepFactor = 0;
		//System.err.println(debugString + " || stepFactor = " + stepFactor);
		int timeFactor = shift - 11;
		if(timeFactor < 0) timeFactor = 0;
		//System.err.println(debugString + " || timeFactor = " + timeFactor);
		
		final int maxEnv = 0x8000;
		final int threshold = 0x888; //If it falls below this, make linear
		
		int cycles = 1 << timeFactor; //Cycles per step
		int truStep = step << stepFactor; //Step size
		
		//System.err.println(debugString + " || cycles = " + cycles);
		//System.err.println(debugString + " || truStep = " + truStep);
		
		double level = (double)initLv;
		double tlev_float = (double)targetLv;
		
		long sampleCount = 0;
		
		double nstep = 0.0;
		double step_float = (double)truStep;
		
		//final double minStep = 1.0; //The minimum stepsize
		final double minStep = step_float * ((double)threshold/(double)maxEnv); //The minimum stepsize
		
		while ((level > tlev_float))
		{
			//WARNING: Asymptotic behavior around zero!!
			//This is fixed by making it decrease linearly if the stepsize gets too small
			nstep = step_float * (level/(double)maxEnv);
			if (nstep < minStep) nstep = minStep;
			sampleCount += cycles;
			level -= nstep;
		}
		//System.err.println(debugString + " || initial while loop finished ");
		
		if (cycles > 1)
		{
			double stepsPerCycle = nstep / (double)cycles;
			while(level < tlev_float)
			{
				level += stepsPerCycle;
				sampleCount--;
			}
			sampleCount++; //Assuming overshot
		}
		//System.err.println(debugString + " || backpedal finished ");
		
		//System.err.println("ADSR.samplesToDecrease_exponential(" + shift + "," + step + "," + initLv + "," + targetLv + ") || Sample count: " + sampleCount);
		return sampleCount;
	}
	
	public static double getAttack_seconds(boolean Am, int Ah, int At)
	{
		long samples = 0;
		int step = 7 - At;
		
		if(Am) samples = samplesToIncrease_exponential(Ah, step, 0, 0x7FFF);
		else samples = samplesToIncrease_linear(Ah, step, 0, 0x7FFF);

		return ((double)samples/PSX_SAMPLERATE_DBL);
	}
	
	public static double getDecay_seconds(int Dh, int Sl)
	{
		//Assumes time calculated is time to sustain level.
		int suslev = getSustain_level(Sl);
		long samples = samplesToDecrease_exponential(Dh, 8, 0x7FFF, suslev);
		
		return ((double)samples/PSX_SAMPLERATE_DBL);
	}
	
	public static int getSustain_level(int Sl)
	{
		double sustainLevel = 0;
		final int maxAmp = 0x7FFF;
		final int maxSL = 0xF;
		
		sustainLevel = ((double)(maxAmp * Sl))/ (double)maxSL;
		
		return (int)Math.round(sustainLevel);
	}
	
	public static int getSustain_level_32(int Sl)
	{
		//System.err.println("Sl = " + Sl);
		double sustainLevel = 0;
		final int maxAmp = 0x7FFFFFFF;
		final int maxSL = 0xF;
		
		sustainLevel = ((double)Sl/(double)maxSL) * (double)maxAmp;
		
		return (int)Math.round(sustainLevel);
	}
	
	public static double getSustain_seconds(boolean Sm, boolean Sd, int Sh, int St, int Sl)
	{
		int suslev = getSustain_level(Sl);
		long samples = 0;
		
		int step = 0;
		
		if(Sd)
		{
			//Downwards
			step = 8 - St;
			if (Sm) samples = samplesToDecrease_exponential(Sh, step, suslev, 0);
			else samples = samplesToDecrease_linear(Sh, step, suslev, 0);
		}
		else
		{
			step = 7 - St;
			if (Sm) samples = samplesToIncrease_exponential(Sh, step, suslev, 0x7FFF);
			else samples = samplesToIncrease_linear(Sh, step, suslev, 0x7FFF);
		}
		
		return ((double)samples/PSX_SAMPLERATE_DBL);
	}
	
	public static double getRelease_seconds(boolean Rm, int Rh)
	{
		long samples = 0;

		if(Rm) samples = samplesToDecrease_exponential(Rh, 8, 0x7FFF, 0);
		else samples = samplesToDecrease_linear(Rh, 8, 0x7FFF, 0);

		return ((double)samples/PSX_SAMPLERATE_DBL);
	}
	
	public static Attack getAttack(boolean Am, int Ah, int At)
	{
		return new VABAttack(Am, Ah, At);
	}
	
	public static Decay getDecay(int Dh)
	{
		//Mode is always PSX_exponential
		return new VABDecay(Dh);
	}
	
	public static Sustain getSustain(boolean Sm, boolean Sd, int Sh, int St, int Sl)
	{
		int scaledLevel = getSustain_level_32(Sl);
		if (!Sd && (Sl == 0xF)) return new Sustain(scaledLevel); 
		if (Sd && (Sl == 0)) return new Sustain(scaledLevel); 
		if ((Sh == 0) && (St == 0)) return new Sustain(scaledLevel); 
		return new VABSustain(Sm, Sd, Sh, St, scaledLevel);
		
		/*double s = getSustain_seconds(Sm, Sd, Sh, St, Sl);
		int millis = (int)Math.round(s * 1000.0);
		if (Sm)
		{
			if (Sd)
			{
				return new Sustain(scaledLevel, false, millis, ADSRMode.PSX_EXPONENTIAL_DECAY);
			}
			else
			{
				return new Sustain(scaledLevel, true, millis, ADSRMode.PSX_PSEUDOEXPONENTIAL);
			}
		}
		else
		{
			return new Sustain(scaledLevel, !Sd, millis, ADSRMode.LINEAR_ENVELOPE);
		}*/
	}
	
	public static Release getRelease(boolean Rm, int Rh)
	{
		return new VABRelease(Rm, Rh);
	}
	
}
