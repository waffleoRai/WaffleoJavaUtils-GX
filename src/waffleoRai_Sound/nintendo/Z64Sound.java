package waffleoRai_Sound.nintendo;

import waffleoRai_SoundSynth.SynthMath;

public class Z64Sound {
	
	public static final int DEFO_SAMPLERATE = 32000;
	
	public static final int CODEC_ADPCM = 0;
	public static final int CODEC_S8 = 1;
	public static final int CODEC_S16_INMEMORY = 2;
	public static final int CODEC_SMALL_ADPCM = 3;
	public static final int CODEC_REVERB = 4;
	public static final int CODEC_S16 = 5;
	
	public static final int MEDIUM_RAM = 0;
	public static final int MEDIUM_UNK = 1;
	public static final int MEDIUM_CART = 2;
	public static final int MEDIUM_DISK_DRIVE = 3;
	
	public static final int CACHE_PERMANENT = 0;
	public static final int CACHE_PERSISTENT = 1;
	public static final int CACHE_TEMPORARY = 2;
	public static final int CACHE_ANY = 3;
	public static final int CACHE_ANYNOSYNCLOAD = 4;
	
	public static final int STDRANGE_BOTTOM = 0x15;
	public static final int STDRANGE_SIZE = 0x40;
	public static final int MIDDLE_C = 60;
	
	public static final int ENVCMD__ADSR_DISABLE = 0;
	public static final int ENVCMD__ADSR_HANG = -1;
	public static final int ENVCMD__ADSR_GOTO = -2;
	public static final int ENVCMD__ADSR_RESTART = -3;
	
	public static final int ENVCMD__UNSET = Short.MIN_VALUE;
	
	public static class Z64Tuning{
		//Tuning for 32kHz
		public byte root_key = 60;
		public byte fine_tune = 0;
		
		public boolean tuningIsEquivalent(Z64Tuning other, int leeway_cents){
			if(other == null) return false;
			int mytune = ((int)root_key * 100) - (int)fine_tune;
			int otune = ((int)other.root_key * 100) - (int)other.fine_tune;
			
			int diff = Math.abs(mytune - otune);
			if(diff <= leeway_cents) return true;
			
			return false;
		}
	}
	
	public static Z64Tuning calculateTuning(byte midi_note, float tune_value){
		int centdiff = SynthMath.freqRatio2Cents(1.0f/tune_value);
		int semis = centdiff / 100;
		int cents = centdiff % 100;
		
		Z64Tuning tune = new Z64Tuning();
		tune.root_key = (byte)(midi_note - semis);
		tune.fine_tune = (byte)cents;
		
		return tune;
	}
	
	public static float calculateTuning(byte midi_note, Z64Tuning tune){
		if(tune == null) return 1.0f;
		int mytune = ((int)tune.root_key * 100) - (int)tune.fine_tune;
		int centdiff = mytune - ((int)midi_note*100);
		//return (float)(1.0/SynthMath.cents2FreqRatio(centdiff));
		return (float)SynthMath.cents2FreqRatio(centdiff);
	}

	public static String getCodecString(int codec, boolean shortcode){
		switch(codec){
		case CODEC_ADPCM:
			if(shortcode) return "ADP9";
			return "4-bit V-ADPCM";
		case CODEC_S8:
			if(shortcode) return "PCM8";
			return "8-bit Signed PCM";
		case CODEC_S16_INMEMORY:
			if(shortcode) return "MPCM";
			return "16-bit Signed PCM (In Memory)";
		case CODEC_SMALL_ADPCM:
			if(shortcode) return "ADP5";
			return "2-bit V-ADPCM";
		case CODEC_REVERB:
			if(shortcode) return "RVRB";
			return "Reverb";
		case CODEC_S16:
			if(shortcode) return "PCMS";
			return "16-bit Signed PCM";
		}
		
		if(shortcode) return "UNKN";
		return "Unknown";
	}
	
	public static String getCacheTypeString(int val){
		switch(val){
		case CACHE_PERMANENT:
			return "Permanent";
		case CACHE_PERSISTENT:
			return "Persistent";
		case CACHE_TEMPORARY:
			return "Temporary";
		case CACHE_ANY:
			return "Any";
		case CACHE_ANYNOSYNCLOAD:
			return "Any (No Sync)";
		}
		return "Unknown";
	}
	
	public static String getMediumTypeString(int val){
		switch(val){
		case MEDIUM_RAM:
			return "RAM";
		case MEDIUM_CART:
			return "Cartridge";
		case MEDIUM_DISK_DRIVE:
			return "64DD";
		}
		return "Unknown";
	}
	
}
