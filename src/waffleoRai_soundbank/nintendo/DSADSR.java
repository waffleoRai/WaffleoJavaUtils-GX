package waffleoRai_soundbank.nintendo;

import waffleoRai_soundbank.adsr.ADSRMode;
import waffleoRai_soundbank.adsr.Attack;
import waffleoRai_soundbank.adsr.Decay;
import waffleoRai_soundbank.adsr.Release;
import waffleoRai_soundbank.adsr.Sustain;

//From VGMTrans (NDSInstrSet.cpp)
public class DSADSR {
	
	public static final long LVL_CONST = 0x16980;
	public static final double TIME_CONST = 1.0/192.0;
	
	public static final int[] AttackTimeTable = {0x00, 0x01, 0x05, 0x0E, 
											     0x1A, 0x26, 0x33, 0x3F, 
											     0x49, 0x54, 0x5C, 0x64, 
											     0x6D, 0x74, 0x7B, 0x7F, 
											     0x84, 0x89, 0x8F};
	
	public static final int sustainLevTable[] =
	      {0xFD2D, 0xFD2E, 0xFD2F, 0xFD75, 0xFDA7, 0xFDCE, 0xFDEE, 0xFE09, 0xFE20, 0xFE34, 0xFE46, 0xFE57, 0xFE66, 0xFE74,
	       0xFE81, 0xFE8D, 0xFE98, 0xFEA3, 0xFEAD, 0xFEB6, 0xFEBF, 0xFEC7, 0xFECF, 0xFED7, 0xFEDF, 0xFEE6, 0xFEEC, 0xFEF3,
	       0xFEF9, 0xFEFF, 0xFF05, 0xFF0B, 0xFF11, 0xFF16, 0xFF1B, 0xFF20, 0xFF25, 0xFF2A, 0xFF2E, 0xFF33, 0xFF37, 0xFF3C,
	       0xFF40, 0xFF44, 0xFF48, 0xFF4C, 0xFF50, 0xFF53, 0xFF57, 0xFF5B, 0xFF5E, 0xFF62, 0xFF65, 0xFF68, 0xFF6B, 0xFF6F,
	       0xFF72, 0xFF75, 0xFF78, 0xFF7B, 0xFF7E, 0xFF81, 0xFF83, 0xFF86, 0xFF89, 0xFF8C, 0xFF8E, 0xFF91, 0xFF93, 0xFF96,
	       0xFF99, 0xFF9B, 0xFF9D, 0xFFA0, 0xFFA2, 0xFFA5, 0xFFA7, 0xFFA9, 0xFFAB, 0xFFAE, 0xFFB0, 0xFFB2, 0xFFB4, 0xFFB6,
	       0xFFB8, 0xFFBA, 0xFFBC, 0xFFBE, 0xFFC0, 0xFFC2, 0xFFC4, 0xFFC6, 0xFFC8, 0xFFCA, 0xFFCC, 0xFFCE, 0xFFCF, 0xFFD1,
	       0xFFD3, 0xFFD5, 0xFFD6, 0xFFD8, 0xFFDA, 0xFFDC, 0xFFDD, 0xFFDF, 0xFFE1, 0xFFE2, 0xFFE4, 0xFFE5, 0xFFE7, 0xFFE9,
	       0xFFEA, 0xFFEC, 0xFFED, 0xFFEF, 0xFFF0, 0xFFF2, 0xFFF3, 0xFFF5, 0xFFF6, 0xFFF8, 0xFFF9, 0xFFFA, 0xFFFC, 0xFFFD,
	       0xFFFF, 0x0000};
	
	public static Attack getAttack(byte rawval){
		int scaled = Byte.toUnsignedInt(rawval);
		if(scaled >= 0x6D) scaled = AttackTimeTable[0x7F - scaled];
		else scaled = 0xFF - scaled;
		
		int count = 0;
		for(long i = LVL_CONST; i != 0; i = (i * scaled) >> 8) count++;
		int millis = (int)Math.round((double)count * TIME_CONST * 1000.0);
		
		return new Attack(millis, ADSRMode.LINEAR_ENVELOPE);
	}
	
	public static Sustain getSustain(byte rawval){

		int raw = Byte.toUnsignedInt(rawval);
		int scaled = 0;
		
		if(raw != 0x7F) {
			scaled = (0x10000 - sustainLevTable[raw]) << 7;
		}
		
		double ratio = 1.0;
		if(scaled != 0){
			ratio = (double)(LVL_CONST - scaled) / (double)LVL_CONST;
		}
		
		int lvl32 = (int)Math.round(ratio * (double)0x7FFFFFFF);
		
		return new Sustain(lvl32);
	}
	
	public static Decay getDecay(byte rawval){
		int millis = 1;
		if(rawval != 0x7F){
			int scaled = getFallingRate(rawval);
			int count = (int)(LVL_CONST / scaled);
			millis = (int)Math.round(TIME_CONST * 1000.0 * (double)count);
		}
		
		return new Decay(millis, ADSRMode.LINEAR_DB);
	}
	
	public static Release getRelease(byte rawval){
		int scaled = getFallingRate(rawval);
		int count = (int)(LVL_CONST / scaled);
		int millis = (int)Math.round(TIME_CONST * 1000.0 * (double)count);
		
		return new Release(millis, ADSRMode.LINEAR_DB);
	}
	
	public static int getFallingRate(byte rawval){
		int raw = Byte.toUnsignedInt(rawval);
		int scaled = 0;
		
		if(raw == 0x7F) scaled = 0xFFFF;
		else if(raw == 0x7E) scaled = 0x3C00;
		else if(raw < 0x32){
			scaled = raw << 1; 
			++scaled;
			scaled &= 0xFFFF;
		}
		else{
			scaled = 0x1E00;
			int div = 0x7E - raw;
			scaled /= div;
			scaled &= 0xFFFF;
		}
		
		return scaled;
	}

}
