package waffleoRai_SeqSound.ninseq;


public class NinSeqADSR {
	
	public static final long ADSR_SCALER = 0x16980;
	public static final int UPDATES_PER_SECOND = 192; //DS Value! Dunno if it works for Wii!
	public static final int[] ATTACK_TABLE = {0x00, 0x01, 0x05, 0x0E, 
											  0x1A, 0x26, 0x33, 0x3F, 
											  0x49, 0x54, 0x5C, 0x64, 
											  0x6D, 0x74, 0x7B, 0x7F, 
											  0x84, 0x89, 0x8F}; //DS!
	public static final int[] SUSTAIN_TABLE = 
			{0xFD2D, 0xFD2E, 0xFD2F, 0xFD75, 0xFDA7, 0xFDCE, 0xFDEE, 0xFE09, 
			 0xFE20, 0xFE34, 0xFE46, 0xFE57, 0xFE66, 0xFE74, 0xFE81, 0xFE8D, 
			 0xFE98, 0xFEA3, 0xFEAD, 0xFEB6, 0xFEBF, 0xFEC7, 0xFECF, 0xFED7, 
			 0xFEDF, 0xFEE6, 0xFEEC, 0xFEF3, 0xFEF9, 0xFEFF, 0xFF05, 0xFF0B, 
			 0xFF11, 0xFF16, 0xFF1B, 0xFF20, 0xFF25, 0xFF2A, 0xFF2E, 0xFF33, 
			 0xFF37, 0xFF3C, 0xFF40, 0xFF44, 0xFF48, 0xFF4C, 0xFF50, 0xFF53, 
			 0xFF57, 0xFF5B, 0xFF5E, 0xFF62, 0xFF65, 0xFF68, 0xFF6B, 0xFF6F,
		     0xFF72, 0xFF75, 0xFF78, 0xFF7B, 0xFF7E, 0xFF81, 0xFF83, 0xFF86, 
		     0xFF89, 0xFF8C, 0xFF8E, 0xFF91, 0xFF93, 0xFF96, 0xFF99, 0xFF9B, 
		     0xFF9D, 0xFFA0, 0xFFA2, 0xFFA5, 0xFFA7, 0xFFA9, 0xFFAB, 0xFFAE, 
		     0xFFB0, 0xFFB2, 0xFFB4, 0xFFB6, 0xFFB8, 0xFFBA, 0xFFBC, 0xFFBE, 
		     0xFFC0, 0xFFC2, 0xFFC4, 0xFFC6, 0xFFC8, 0xFFCA, 0xFFCC, 0xFFCE, 
		     0xFFCF, 0xFFD1, 0xFFD3, 0xFFD5, 0xFFD6, 0xFFD8, 0xFFDA, 0xFFDC, 
		     0xFFDD, 0xFFDF, 0xFFE1, 0xFFE2, 0xFFE4, 0xFFE5, 0xFFE7, 0xFFE9,
		     0xFFEA, 0xFFEC, 0xFFED, 0xFFEF, 0xFFF0, 0xFFF2, 0xFFF3, 0xFFF5, 
		     0xFFF6, 0xFFF8, 0xFFF9, 0xFFFA, 0xFFFC, 0xFFFD, 0xFFFF, 0x0000}; //DS!
	
	private static int adjustDR(int raw)
	{
		//Copypaste from VGMTrans NDSInstrSet.cpp 
		int adjFall = raw;
		if (raw == 0x7F) adjFall = 0xFFFF;
		else if (raw == 0x7E) adjFall = 0x3C00;
		else if (raw < 0x32) 
		{
			adjFall = raw * 2;
			adjFall++;
			adjFall &= 0xFFFF;
		}
		else 
		{
			adjFall = 0x1E00;
			raw = 0x7E - raw;
			adjFall /= raw;
			adjFall &= 0xFFFF;
		}
		return adjFall & 0xFFFF;
	}
	
	public static int scaleAttackToMillis(int rawAttack)
	{
		//Signed input!

		//A lot of copypaste from VGMTrans NDSInstrSet.cpp
		//This is a guess based on the DS scaling
		int adjAtt = rawAttack;
		if(rawAttack >= 0x6D) adjAtt = ATTACK_TABLE[0x7F - rawAttack];
		else adjAtt = 0xFF - rawAttack;
		
		int count = 0;
		for (long i = ADSR_SCALER; i != 0; i = (i * adjAtt) >> 8) count++;
		//0000 0001 0110 1001 1000 0000
		//How many cycles does it take to reach the envelope top
		double millisd = ((double)count * (1.0/(double)UPDATES_PER_SECOND)) * 1000.0;
		int millis = (int)Math.round(millisd);
		
		//Looks like it can work as linear db? Can save as is?
		return millis;
	}

	public static int scaleHoldToMillis(int rawHold)
	{
		//This is a guess
		//I really have no idea how to scale this
		//I'm just gonna scale it linearly?
		int max = UPDATES_PER_SECOND * 10; //10s
		int cycles = (int)Math.round((double)max * ((double)rawHold/(double)0x7F));
		double sec = (double)cycles * (1.0/(double)UPDATES_PER_SECOND);
		int millis = (int)Math.round(sec * 1000.0);
		
		return millis;
	}
	
	public static int scaleDecayToMillis(int rawDecay)
	{
		//This is a guess
		//Taken from VGMTrans NDSInstrSet.cpp 
		if(rawDecay == 0x7F) return 1;
		int adjDecay = adjustDR(rawDecay);
		int count = (int)ADSR_SCALER/adjDecay;
		double sec = (double)count * (1.0/(double)UPDATES_PER_SECOND);
		int millis = (int)Math.round(sec * 1000.0);
		//Linear envelope
		return millis;
	}
	
	public static int scaleSustain(int rawSustain)
	{
		//This is a guess
		//Taken from VGMTrans NDSInstrSet.cpp 
		int susFactor = 0;
		if(rawSustain < 0x7F)
		{
			susFactor = (0x10000 - SUSTAIN_TABLE[rawSustain]) << 7;
		}
		
		double s = ((double)(ADSR_SCALER - (long)susFactor))/ (double)ADSR_SCALER;
		int slvl = (int)Math.round(s * (double)0x7FFFFFFF);
		
		return slvl;
	}
	
	public static int scaleReleaseToMillis(int rawRelease)
	{
		//This is a guess
		//Taken from VGMTrans NDSInstrSet.cpp 
		if(rawRelease == 0x7F) return 1;
		int adjRel = adjustDR(rawRelease);
		int count = (int)ADSR_SCALER/adjRel;
		double sec = (double)count * (1.0/(double)UPDATES_PER_SECOND);
		int millis = (int)Math.round(sec * 1000.0);
		return millis;
		//Linear envelope?
	}
	
}
