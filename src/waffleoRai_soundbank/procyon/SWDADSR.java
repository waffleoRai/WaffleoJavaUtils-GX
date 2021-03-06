package waffleoRai_soundbank.procyon;

import waffleoRai_soundbank.adsr.ADSRMode;
import waffleoRai_soundbank.adsr.Attack;
import waffleoRai_soundbank.adsr.Decay;
import waffleoRai_soundbank.adsr.Release;
import waffleoRai_soundbank.adsr.Sustain;

//Again, https://projectpokemon.org/docs/mystery-dungeon-nds/dse-swdl-format-r14/

public class SWDADSR {
	
	public static final int LINEAR_ENV_DIV_FACTOR = 2;
	
	public static final int[] TABLE_16 = {
			0x0000, 0x0001, 0x0002, 0x0003, 0x0004, 0x0005, 0x0006, 0x0007, 
			0x0008, 0x0009, 0x000A, 0x000B, 0x000C, 0x000D, 0x000E, 0x000F, 
			0x0010, 0x0011, 0x0012, 0x0013, 0x0014, 0x0015, 0x0016, 0x0017, 
			0x0018, 0x0019, 0x001A, 0x001B, 0x001C, 0x001D, 0x001E, 0x001F, 
			0x0020, 0x0023, 0x0028, 0x002D, 0x0033, 0x0039, 0x0040, 0x0048, 
			0x0050, 0x0058, 0x0062, 0x006D, 0x0078, 0x0083, 0x0090, 0x009E, 
			0x00AC, 0x00BC, 0x00CC, 0x00DE, 0x00F0, 0x0104, 0x0119, 0x012F, 
			0x0147, 0x0160, 0x017A, 0x0196, 0x01B3, 0x01D2, 0x01F2, 0x0214, 
			0x0238, 0x025E, 0x0285, 0x02AE, 0x02D9, 0x0307, 0x0336, 0x0367, 
			0x039B, 0x03D1, 0x0406, 0x0442, 0x047E, 0x04C4, 0x0500, 0x0546, 
			0x058C, 0x0622, 0x0672, 0x06CC, 0x071C, 0x0776, 0x07DA, 0x0834, 
			0x0898, 0x0906, 0x096A, 0x09D8, 0x0A50, 0x0ABE, 0x0B40, 0x0BB8, 
			0x0C3A, 0x0CBC, 0x0D48, 0x0DDE, 0x0E6A, 0x0F00, 0x0FA0, 0x1040, 
			0x10EA, 0x1194, 0x123E, 0x12F2, 0x13B0, 0x146E, 0x1536, 0x15FE, 
			0x16D0, 0x17A2, 0x187E, 0x195A, 0x1A40, 0x1B30, 0x1C20, 0x1D1A, 
			0x1E1E, 0x1F22, 0x2030, 0x2148, 0x2260, 0x2382, 0x2710, 0x7FFF};
	
	public static final int[] TABLE_32 = {
		       0x00000000, 0x00000004, 0x00000007, 0x0000000A, 
		       0x0000000F, 0x00000015, 0x0000001C, 0x00000024, 
		       0x0000002E, 0x0000003A, 0x00000048, 0x00000057, 
		       0x00000068, 0x0000007B, 0x00000091, 0x000000A8, 
		       0x00000185, 0x000001BE, 0x000001FC, 0x0000023F, 
		       0x00000288, 0x000002D6, 0x0000032A, 0x00000385, 
		       0x000003E5, 0x0000044C, 0x000004BA, 0x0000052E, 
		       0x000005A9, 0x0000062C, 0x000006B5, 0x00000746, 
		       0x00000BCF, 0x00000CC0, 0x00000DBD, 0x00000EC6, 
		       0x00000FDC, 0x000010FF, 0x0000122F, 0x0000136C, 
		       0x000014B6, 0x0000160F, 0x00001775, 0x000018EA, 
		       0x00001A6D, 0x00001BFF, 0x00001DA0, 0x00001F51, 
		       0x00002C16, 0x00002E80, 0x00003100, 0x00003395, 
		       0x00003641, 0x00003902, 0x00003BDB, 0x00003ECA, 
		       0x000041D0, 0x000044EE, 0x00004824, 0x00004B73, 
		       0x00004ED9, 0x00005259, 0x000055F2, 0x000059A4, 
		       0x000074CC, 0x000079AB, 0x00007EAC, 0x000083CE, 
		       0x00008911, 0x00008E77, 0x000093FF, 0x000099AA, 
		       0x00009F78, 0x0000A56A, 0x0000AB80, 0x0000B1BB, 
		       0x0000B81A, 0x0000BE9E, 0x0000C547, 0x0000CC17, 
		       0x0000FD42, 0x000105CB, 0x00010E82, 0x00011768, 
		       0x0001207E, 0x000129C4, 0x0001333B, 0x00013CE2, 
		       0x000146BB, 0x000150C5, 0x00015B02, 0x00016572, 
		       0x00017015, 0x00017AEB, 0x000185F5, 0x00019133, 
		       0x0001E16D, 0x0001EF07, 0x0001FCE0, 0x00020AF7, 
		       0x0002194F, 0x000227E6, 0x000236BE, 0x000245D7, 
		       0x00025532, 0x000264CF, 0x000274AE, 0x000284D0, 
		       0x00029536, 0x0002A5E0, 0x0002B6CE, 0x0002C802, 
		       0x000341B0, 0x000355F8, 0x00036A90, 0x00037F79, 
		       0x000394B4, 0x0003AA41, 0x0003C021, 0x0003D654, 
		       0x0003ECDA, 0x000403B5, 0x00041AE5, 0x0004326A, 
		       0x00044A45, 0x00046277, 0x00047B00, 0x7FFFFFFF
		   };
	
	public static int getDuration_ms(byte raw, byte envmult){
		//I have no idea what I'm doing
		if(raw == 0x7F) return -1; //Infinite/disabled
		else{
			int val = 0;
			if(envmult != 0){
				val = TABLE_16[raw];
				val *= envmult;
			}
			else{
				val = TABLE_32[raw];
			}
			//val *= 1000;
			//val /= 10000;
			//val *= 2;
			return val;
		}
	}
	
	public static Attack getAttack(byte att_lvl, byte att_val, byte envmult){
		int ms = getDuration_ms(att_val, envmult);
		double lvl = (double)att_lvl/127.0;
		if(ms == 0) ms = 1;
		
		Attack att = new Attack(ms/LINEAR_ENV_DIV_FACTOR, ADSRMode.LINEAR_ENVELOPE); //Dunno the mode
		att.setStartLevel(lvl);
		
		return att;
	}
	
	public static Decay getDecay(byte dec_val, byte envmult){
		int ms = getDuration_ms(dec_val, envmult);
		if(ms == 0) ms = 1;
		
		Decay d = new Decay(ms/LINEAR_ENV_DIV_FACTOR, ADSRMode.LINEAR_ENVELOPE); //Dunno the mode

		return d;
	}
	
	public static Sustain getSustain(byte suslev, byte dec2, byte envmult){
		double lvl = (double)suslev/127.0;
		int slvl = (int)Math.round(lvl * (double)0x7FFFFFFF);
		if(dec2 == 0x7F){
			return new Sustain(slvl);
		}
		int ms = getDuration_ms(dec2, envmult);
		Sustain s = new Sustain(slvl, false, ms/LINEAR_ENV_DIV_FACTOR, ADSRMode.LINEAR_ENVELOPE);
		return s;
	}
	
	public static Release getRelease(byte rel_val, byte envmult){
		int ms = getDuration_ms(rel_val, envmult);
		if(ms == 0) ms = 1;
		
		Release r = new Release(ms/LINEAR_ENV_DIV_FACTOR, ADSRMode.LINEAR_ENVELOPE); //Dunno the mode
		
		return r;
	}

}
