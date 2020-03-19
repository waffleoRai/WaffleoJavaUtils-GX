package waffleoRai_soundbank.vab;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedList;

public class VABAnalyzer {
	
	public static final int PSX_SAMPLERATE = 44100;
	
	public static int[] generateRateTable()
	{
		int r = 3;
		int rs = 1;
		int rd = 0;
		
		int[] tbl = new int[160];
		
		for (int i = 0; i < 32; i++) tbl[i] = 0;
		
		for (int i = 32; i < 160; i++)
		{
			if (r < 0x3FFFFFFF)
			{
				r += rs;
				rd++;
				if (rd == 5)
				{
					rd = 1;
					rs *= 2;
				}
			}
			else if (r > 0x3FFFFFFF) r = 0x3FFFFFFF;
			
			tbl[i] = r;
		}
		
		return tbl;
	}
	
	public static double calcAtt_V1(boolean Am, int Ah, int As, int[] rateTable)
	{
		//Returns result in microseconds
		int Ar = ((Ah & 0x1F) << 2) | (As & 0x3);

		double samples = 0.0;
		int rate = 0;
		int remainder = 0;
		int ArXOR = 0;
		
		//In a convoluted way, this appears to serve to weed out values that are above some max?
		//ie. Ar cannot be above 0x6F, and Ah above 0x1B
		//Weird
		if ((Ar ^ 0x7F) < 0x10) Ar = 0; 
		
		if (!Am)
		{
			//Linear
			ArXOR = (Ar ^ 0x7F) - 0x10;
		    if (ArXOR < 0) ArXOR = 0;
		    rate = rateTable[ArXOR + 32];
		    samples = Math.ceil((double)0x7FFFFFFF / (double)rate);
		}
		else
		{
			//Exponential
			ArXOR = (Ar ^ 0x7F) - 0x10;
		    if (ArXOR < 0) ArXOR = 0;
		    rate = rateTable[ArXOR + 32];
		    samples = 0x60000000 / rate;
		    remainder = 0x60000000 % rate;
		    ArXOR = (Ar ^ 0x7F) - 0x18;
		    if (ArXOR < 0) ArXOR = 0;
		    rate = rateTable[ArXOR + 32];
		    samples = Math.ceil(Math.max(0.0, ((double)0x1FFFFFFF) - ((double)remainder)) / (double)rate);
		}
		double timeIn_us = (samples / (double)PSX_SAMPLERATE) * 1000000.0;
		
		return timeIn_us;
	}
	
	public static double calcAtt_V2(boolean Am, int Ah, int As)
	{
		long durationInSamples = 0;
		double durationInMicroseconds = 0.0;
		int level = 0;
		int cycles = 0;
		int step = 0;
		int shifter = 0;
		int aStep = 0;
		final int maxAmp = 0x8000;
		
		aStep = As + 4;
        while (level < maxAmp)
        {
          shifter = Ah - 11;
          if (shifter < 0) shifter = 0;
          cycles = 1 << shifter;

          shifter = 11 - Ah;
          if (shifter < 0) shifter = 0;
          step = aStep << shifter;

          if (Am && level > 0x6000)
          {
            /*Make sure it only breaks this while...*/
            break;
          }

          durationInSamples += cycles;
          level += step;
        }
        durationInMicroseconds = ((double)durationInSamples / (double)PSX_SAMPLERATE) * 1000000.0;
		
		return durationInMicroseconds;
	}

	public static double calcDec_V1(int Dh, int Sl, int[] rateTable)
	{
		int l = 0;
		int envelope_level = 0x7FFFFFFF;
		//boolean susLevFnd = false;
		//int trueSusLv = 0;
		int DhXOR = 0;
		int f = 0;

		double samples = 0.0;
		double time = 0.0;

		for (l = 0; envelope_level > 0; l++)
		{
		  if ((4 * (Dh ^ 0x1F)) < 0x18) Dh = 0;
		  int esw = ((envelope_level >> 28) & 0x7);
		  
		  switch (esw)
		  {
		    case 0: f = 0; break;
		    case 1: f = 4; break;
		    case 2: f = 6; break;
		    case 3: f = 8; break;
		    case 4: f = 9; break;
		    case 5: f = 10; break;
		    case 6: f = 11; break;
		    case 7: f = 12; break;
		  }
		  DhXOR = ((4* (Dh ^ 0x1F)) - 0x18) + f;
		  if (DhXOR < 0) DhXOR = 0;
		  envelope_level -= rateTable[DhXOR + 32];

		 // if (!susLevFnd && (((envelope_level >> 27) & 0xF) <= Sl))
		 // {
		    //susLevFnd = true;
		   // trueSusLv = envelope_level;
		 // }
		}

		samples = (double)l;
		time = (samples / (double)PSX_SAMPLERATE) * 1000000.0;
		
		return time;
	}
	
	public static double calcDec_V2(int Dh, int Sl)
	{
		double sustainLevel = 0.0;
		double durationInSeconds = 0.0;
		int step = 0;
		int shift = 0;
		int shifter = 0;
		double timeStep = 0.0;
		double ampShift = 0.0;
		double target = 0.0;
		//int slScaled = 0;
		final double expMinAmplitude = 0.1;
		final int maxAmp = 0x7FFF;
		
	    sustainLevel = calcSusLevel_V2(Sl);

	    shifter = (int)Dh - 11;
	    if (shifter < 0) shifter = 0;
	    step = 1 << shifter;

	    shifter = 11 - (int)Dh;
	    if (shifter < 0) shifter = 0;
	    shift = 8 << shifter;

	    timeStep = (double)step / 44100.0;
	    ampShift = (double)(1.0 * shift) / (double)maxAmp;
	    if (sustainLevel >= expMinAmplitude) target = sustainLevel;
	    else target = expMinAmplitude;

	    durationInSeconds = (Math.log10(target) * -1.0) / (ampShift/timeStep);
	    if (durationInSeconds < 0) durationInSeconds = 0;
	   // fprintf(prnt, "%d\t%.6f\n", Sl, durationInSeconds);
	    //fwrite(&durationInSeconds, sizeof(double), 1, out);
		
		return durationInSeconds * 1000000.0;
	}
	
	public static double calcSusLevel_V2(int Sl)
	{
		double sustainLevel = 0;
		final int maxAmp = 0x7FFF;
		final int maxSL = 0xF;
		
		sustainLevel = ((double)(maxAmp * Sl))/ (double)maxSL;
		
		return sustainLevel;
	}
	
	public static double calcRel_V1(boolean Rm, int Rh, int[] rateTable)
	{
		int l = 0;
		int envelope_level = 0x7FFFFFFF;
		int f = 0;
		int RhXOR = 0;
		long rate = 0;
		double samples = 0.0;
		double time = 0.0;
		//double relTime = 0.0;
		
		if (!Rm)
		{
		  RhXOR = (4 * (Rh ^ 0x1F)) - 0x0C;
		  if (RhXOR < 0) RhXOR = 0;
		  rate = rateTable[RhXOR + 32];
		  
		  if (rate != 0) samples = Math.ceil((double)envelope_level / (double)rate);
		  else samples = 0;
		  
		}
		else
		{
		  RhXOR = 4 * (Rh ^ 0x1F);
		  if (RhXOR < 0x18) Rh = 0;
		  for (l = 0; envelope_level > 0; l++)
		  {
		    switch ((envelope_level >> 28) & 0x7)
		    {
		      case 0: f = 0; break;
		      case 1: f = 4; break;
		      case 2: f = 6; break;
		      case 3: f = 8; break;
		      case 4: f = 9; break;
		      case 5: f = 10; break;
		      case 6: f = 11; break;
		      case 7: f = 12; break;
		    }
		    RhXOR -= 0x18;
		    RhXOR += f;
		    if (RhXOR < 0) RhXOR = 0;
		    envelope_level -= rateTable[RhXOR + 32];
		  }
		  samples = (double)l;
		}
		time = (samples / (double)PSX_SAMPLERATE) * 1000000.0;
		//relTime = amp2dBdecayTime(timeInSecs, 0x800);
		
		return time;
	}
	
	public static void writeAttackTable(String path) throws IOException
	{
		int[] rt = generateRateTable();
		
		int Ah = 0;
		int As = 0;
		
		FileWriter fw = new FileWriter(path);
		BufferedWriter bw = new BufferedWriter(fw);
		
		bw.write("ATTACK\n\n");
		bw.write("---------------------------------------------------\n");
		bw.write("Mode = Linear\n");
		bw.write("---------------------------------------------------\n");
		bw.write("Shift\tStep\t\tMethod1(s)\tMethod2(s)\n");
		bw.write("-----\t----\t\t----------\t----------\n");
		
		for (Ah = 0; Ah < 32; Ah++)
		{
			for (As = 0; As < 4; As++)
			{
				double A1 = calcAtt_V1(false, Ah, As, rt);
				double A2 = ADSR.getAttack_seconds(false, Ah, As);
				
				bw.write(String.format("%02X", Ah) + "\t");
				bw.write(String.format("%01X", As) + "\t\t");
				bw.write(String.format("%.3f", (A1/1000000.0)) + "\t");
				bw.write(String.format("%.3f", A2) + "\n");
			}
		}
		
		bw.write("\n");
		bw.write("---------------------------------------------------\n");
		bw.write("Mode = Exponential\n");
		bw.write("---------------------------------------------------\n");
		bw.write("Shift\tStep\t\tMethod1(s)\tMethod2(s)\n");
		bw.write("-----\t----\t\t----------\t----------\n");
		
		for (Ah = 0; Ah < 32; Ah++)
		{
			for (As = 0; As < 4; As++)
			{
				double A1 = calcAtt_V1(true, Ah, As, rt);
				double A2 = ADSR.getAttack_seconds(true, Ah, As);
				
				bw.write(String.format("%02X", Ah) + "\t");
				bw.write(String.format("%01X", As) + "\t\t");
				bw.write(String.format("%.3f", (A1/1000000.0)) + "\t");
				bw.write(String.format("%.3f", A2) + "\n");
			}
		}
		
		bw.close();
		fw.close();
	}
	
	public static void writeDecayTable(String path) throws IOException
	{
		int[] rt = generateRateTable();
		
		int Dh = 0;
		int Sl = 0;
		
		FileWriter fw = new FileWriter(path);
		BufferedWriter bw = new BufferedWriter(fw);
		
		bw.write("DECAY\n\n");
		bw.write("---------------------------------------------------\n");
		bw.write("Shift\tSusLev\t\tMethod1(s)\tMethod2(s)\n");
		bw.write("-----\t------\t\t----------\t----------\n");
		
		for (Dh = 0; Dh < 16; Dh++)
		{
			for (Sl = 0; Sl < 16; Sl++)
			{
				double D1 = calcDec_V1(Dh, Sl, rt);
				double D2 = ADSR.getDecay_seconds(Dh, Sl);
				
				bw.write(String.format("%01X", Dh) + "\t");
				bw.write(String.format("%01X", Sl) + "\t\t");
				bw.write(String.format("%.3f", (D1/1000000.0)) + "\t");
				bw.write(String.format("%.3f", D2) + "\n");
			}
		}
		
		bw.close();
		fw.close();
	}
	
	public static void writeSuslevTable(String path) throws IOException
	{
		int Sl = 0;
		
		FileWriter fw = new FileWriter(path);
		BufferedWriter bw = new BufferedWriter(fw);
		
		bw.write("SUSTAIN LEVEL\n\n");
		bw.write("---------------------------------------------------\n");
		bw.write("SusLev\t\tMethod2(RAW)\n");
		bw.write("------\t\t------------\n");
		
		for (Sl = 0; Sl < 16; Sl++)
		{
			double S = calcSusLevel_V2(Sl);
				
			bw.write(String.format("%01X", Sl) + "\t\t");
			bw.write(String.format("%04X", Math.round(S)) + "\n");
		}
		
		bw.close();
		fw.close();
	}
	
	public static void writeSustainTable(String path) throws IOException
	{
		FileWriter fw = new FileWriter(path);
		BufferedWriter bw = new BufferedWriter(fw);
		
		bw.write("Sustain\n\n");
		bw.write("---------------------------------------------------\n");
		bw.write("Mode = Linear\n");
		bw.write("---------------------------------------------------\n");
		bw.write("Direction: Increase\n\n");
		bw.write("Shift\tStep\tLevel\t\tMethod2(s)\n");
		bw.write("-----\t----\t-----\t\t----------\n");
		
		for (int Sh = 0; Sh < 32; Sh++)
		{
			for (int St = 0; St < 4; St++)
			{
				for (int Sl = 0; Sl < 16; Sl++)
				{
					double S1 = ADSR.getSustain_seconds(false, false, Sh, St, Sl);
							
					bw.write(String.format("%02X", Sh) + "\t");
					bw.write(String.format("%01X", St) + "\t");
					bw.write(String.format("%01X", Sl) + "\t\t");
					bw.write(String.format("%.3f", S1) + "\n");
				}
			}
		}
		
		bw.write("\n");
		bw.write("Direction: Decrease\n\n");
		bw.write("Shift\tStep\tLevel\t\tMethod2(s)\n");
		bw.write("-----\t----\t-----\t\t----------\n");
		
		for (int Sh = 0; Sh < 32; Sh++)
		{
			for (int St = 0; St < 4; St++)
			{
				for (int Sl = 0; Sl < 16; Sl++)
				{
					double S1 = ADSR.getSustain_seconds(false, true, Sh, St, Sl);
							
					bw.write(String.format("%02X", Sh) + "\t");
					bw.write(String.format("%01X", St) + "\t");
					bw.write(String.format("%01X", Sl) + "\t\t");
					bw.write(String.format("%.3f", S1) + "\n");
				}
			}
		}
		
		bw.write("---------------------------------------------------\n");
		bw.write("Mode = Exponential\n");
		bw.write("---------------------------------------------------\n");
		bw.write("Direction: Increase\n\n");
		bw.write("Shift\tStep\tLevel\t\tMethod2(s)\n");
		bw.write("-----\t----\t-----\t\t----------\n");
		
		for (int Sh = 0; Sh < 32; Sh++)
		{
			for (int St = 0; St < 4; St++)
			{
				for (int Sl = 0; Sl < 16; Sl++)
				{
					double S1 = ADSR.getSustain_seconds(true, false, Sh, St, Sl);
							
					bw.write(String.format("%02X", Sh) + "\t");
					bw.write(String.format("%01X", St) + "\t");
					bw.write(String.format("%01X", Sl) + "\t\t");
					bw.write(String.format("%.3f", S1) + "\n");
				}
			}
		}
		
		bw.write("\n");
		bw.write("Direction: Decrease\n\n");
		bw.write("Shift\tStep\tLevel\t\tMethod2(s)\n");
		bw.write("-----\t----\t-----\t\t----------\n");
		
		for (int Sh = 0; Sh < 32; Sh++)
		{
			for (int St = 0; St < 4; St++)
			{
				for (int Sl = 0; Sl < 16; Sl++)
				{
					double S1 = ADSR.getSustain_seconds(true, true, Sh, St, Sl);
							
					bw.write(String.format("%02X", Sh) + "\t");
					bw.write(String.format("%01X", St) + "\t");
					bw.write(String.format("%01X", Sl) + "\t\t");
					bw.write(String.format("%.3f", S1) + "\n");
				}
			}
		}
		
		bw.close();
		fw.close();
	}
	
	public static void writeReleaseTable(String path) throws IOException
	{
		int[] rt = generateRateTable();
		
		int Rh = 0;
		
		FileWriter fw = new FileWriter(path);
		BufferedWriter bw = new BufferedWriter(fw);
		
		bw.write("RELEASE\n\n");
		bw.write("---------------------------------------------------\n");
		bw.write("Mode = Linear\n");
		bw.write("---------------------------------------------------\n");
		bw.write("Shift\t\tMethod1(s)\tMethod2(s)\n");
		bw.write("-----\t\t----------\t----------\n");
		
		for (Rh = 0; Rh < 32; Rh++)
		{
			double R1 = calcRel_V1(false, Rh, rt);
			double R2 = ADSR.getRelease_seconds(false, Rh);
				
			bw.write(String.format("%02X", Rh) + "\t\t");
			bw.write(String.format("%.3f", (R1/1000000.0)) + "\t");
			bw.write(String.format("%.3f", R2) + "\n");
		}
		
		bw.write("\n");
		bw.write("---------------------------------------------------\n");
		bw.write("Mode = Exponential\n");
		bw.write("---------------------------------------------------\n");
		bw.write("Shift\t\tMethod1(s)\tMethod2(s)\n");
		bw.write("-----\t\t----------\t----------\n");
		
		for (Rh = 0; Rh < 32; Rh++)
		{
			double R1 = calcRel_V1(true, Rh, rt);
			double R2 = ADSR.getRelease_seconds(true, Rh);
				
			bw.write(String.format("%02X", Rh) + "\t\t");
			bw.write(String.format("%.3f", (R1/1000000.0)) + "\t");
			bw.write(String.format("%.3f", R2) + "\n");
		}
		
		bw.close();
		fw.close();
	}
	
	private static class Record
	{
		protected double start;
		protected double end;
		protected double db_delta;
		protected int time;
	}
	
	public static void writeAttackTimeTable(String path, int seed) throws IOException
	{
		final int threshold = 0x6000;
		final int max = 0x7FFF;
		double min = (double)max * (Math.pow(10.0, -10.0));
		
		LinkedList<Record> exp = new LinkedList<Record>();
		LinkedList<Record> lin = new LinkedList<Record>();
		
		//Calculate pseudo-exponential curve
		double now = min;
		double inc = (double)max / (double)seed;
		while (now <= threshold)
		{
			Record r = new Record();
			r.start = now;
			r.end = now + inc;
			r.db_delta = 10.0 * Math.log10(r.end/r.start);
			exp.add(r);
			now = r.end;
		}
		inc = inc / 4.0;
		while (now <= max)
		{
			Record r = new Record();
			r.start = now;
			r.end = now + inc;
			r.db_delta = 10.0 * Math.log10(r.end/r.start);
			exp.add(r);
			now = r.end;
		}
		
		//Count the samples in the exp curve...
		int exSamp = exp.size();
		inc = (double)max / (double)exSamp;
		now = min;
		while (now <= max)
		{
			Record r = new Record();
			r.start = now;
			r.end = now + inc;
			r.db_delta = 10.0 * Math.log10(r.end/r.start);
			lin.add(r);
			now = r.end;
		}
		
		FileWriter fw = new FileWriter(path);
		BufferedWriter bw = new BufferedWriter(fw);
		
		bw.write("Cycle\t");
		bw.write("Start[l]\t");
		bw.write("End[l]\t");
		bw.write("deltaDB[l]\t");
		bw.write("dB[l]\t");
		bw.write("Start[e]\t");
		bw.write("End[e]\t");
		bw.write("deltaDB[e]\t");
		bw.write("dB[e]\n");
		
		double ldb = 0.0;
		double edb = 0.0;
		for (int i = 0; i < exSamp; i++)
		{
			Record lr = lin.pop();
			Record er = exp.pop();
			
			bw.write(i + "\t");
			bw.write(String.format("%.12f", lr.start) + "\t");
			bw.write(String.format("%.12f", lr.end) + "\t");
			bw.write(String.format("%.12f", lr.db_delta) + "\t");
			ldb += lr.db_delta;
			bw.write(String.format("%.12f", ldb) + "\t");
			
			bw.write(String.format("%.12f", er.start) + "\t");
			bw.write(String.format("%.12f", er.end) + "\t");
			bw.write(String.format("%.12f", er.db_delta) + "\t");
			edb += er.db_delta;
			bw.write(String.format("%.12f", edb) + "\n");
		}
		
		bw.close();
		fw.close();
		
	}
	
	public static void writeReleaseTimeTable(String path, int shiftseed) throws IOException
	{
		final int max = 0x7FFF;
		final int threshold = 0x888;
		double min = (double)max * (Math.pow(10.0, -10.0));
		
		LinkedList<Record> exp = new LinkedList<Record>();
		LinkedList<Record> lin = new LinkedList<Record>();
		
		double now = (double)max;
		final int stepRaw = 8;
		
		int stepFactor = 11 - shiftseed;
		if(stepFactor < 0) stepFactor = 0;
		int timeFactor = shiftseed - 11;
		if(timeFactor < 0) timeFactor = 0;
		
		int cycles = 1 << timeFactor; //Cycles per step
		int truStep = stepRaw << stepFactor; //Step size
		
		double nstep = 0.0;
		double step_float = (double)truStep;
		final double minStep = step_float * ((double)threshold/(double)max);
		
		int time = 0;
		
		while (now > min)
		{
			double last = now;
			nstep = step_float * (now/(double)max);
			if (nstep < minStep) nstep = minStep;
			time += cycles;
			now -= nstep;
			if (now < min) now = min;
			Record r = new Record();
			r.start = last;
			r.end = now;
			r.db_delta = 10.0 * (Math.log10(r.end/r.start));
			r.time = time;
			exp.add(r);
		}
		
		now = (double)max;
		int pts = exp.size();
		time = 0; 
		double dec = max/(double)pts;
		while (now > min)
		{
			double last = now;
			time += cycles;
			now -= dec;
			if (now < min) now = min;
			Record r = new Record();
			r.start = last;
			r.end = now;
			r.db_delta = 10.0 * (Math.log10(r.end/r.start));
			r.time = time;
			lin.add(r);
		}
		
		FileWriter fw = new FileWriter(path);
		BufferedWriter bw = new BufferedWriter(fw);
		
		bw.write("Cycle\t");
		bw.write("Start[l]\t");
		bw.write("End[l]\t");
		bw.write("deltaDB[l]\t");
		bw.write("dB[l]\t");
		bw.write("Start[e]\t");
		bw.write("End[e]\t");
		bw.write("deltaDB[e]\t");
		bw.write("dB[e]\n");
		
		double ldb = 0.0;
		double edb = 0.0;
		for (int i = 0; i < pts; i++)
		{
			Record lr = lin.pop();
			Record er = exp.pop();
			
			bw.write(lr.time + "\t");
			bw.write(String.format("%.16f", lr.start) + "\t");
			bw.write(String.format("%.16f", lr.end) + "\t");
			bw.write(String.format("%.16f", lr.db_delta) + "\t");
			ldb += lr.db_delta;
			bw.write(String.format("%.16f", ldb) + "\t");
		
			bw.write(String.format("%.16f", er.start) + "\t");
			bw.write(String.format("%.16f", er.end) + "\t");
			bw.write(String.format("%.16f", er.db_delta) + "\t");
			edb += er.db_delta;
			bw.write(String.format("%.16f", edb) + "\n");
		}
		
		bw.close();
		fw.close();
		
		
	}
	
	
	public static void main(String[] args) {
		
		//String attpath = "C:\\Users\\Blythe\\Documents\\Game Stuff\\PSX\\Tool Testing\\VAB\\VAB_att_tbl.out";
		//String decpath = "C:\\Users\\Blythe\\Documents\\Game Stuff\\PSX\\Tool Testing\\VAB\\VAB_dec_tbl.out";
		//String suslpath = "C:\\Users\\Blythe\\Documents\\Game Stuff\\PSX\\Tool Testing\\VAB\\VAB_susl_tbl.out";
		//String susrpath = "C:\\Users\\Blythe\\Documents\\Game Stuff\\PSX\\Tool Testing\\VAB\\VAB_susr_tbl.out";
		//String relpath = "C:\\Users\\Blythe\\Documents\\Game Stuff\\PSX\\Tool Testing\\VAB\\VAB_rel_tbl.out";
		
		String timtblpath = "C:\\Users\\Blythe\\Documents\\Game Stuff\\PSX\\Tool Testing\\VAB\\VAB_relTime_tbl.tsv";
		
		try {
			writeReleaseTimeTable(timtblpath, 15);
		} catch (IOException e) {
			
			e.printStackTrace();
		}

	}

}
