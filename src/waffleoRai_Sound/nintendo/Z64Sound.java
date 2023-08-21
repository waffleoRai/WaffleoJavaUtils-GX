package waffleoRai_Sound.nintendo;

import waffleoRai_SoundSynth.SynthMath;

public class Z64Sound {
	
	public static final int DEFO_SAMPLERATE = 32000;
	
	public static final int REFRESH_RATE_NTSC = 60;
	public static final int REFRESH_RATE_PAL = 50;
	
	public static final int SAMPLES_PER_FRAME_NTSC = ((DEFO_SAMPLERATE/REFRESH_RATE_NTSC) + 0xf) & ~0xf;
	public static final int SAMPLES_PER_FRAME_PAL = ((DEFO_SAMPLERATE/REFRESH_RATE_PAL) + 0xf) & ~0xf;
	
	public static final int UPDATES_PER_FRAME_NTSC = ((SAMPLES_PER_FRAME_NTSC + 0x10)/0xd0) + 1;
	public static final int UPDATES_PER_FRAME_PAL = ((SAMPLES_PER_FRAME_PAL + 0x10)/0xd0) + 1;
	
	public static final double UPDATES_PER_FRAME_SCALED_NTSC = (double)UPDATES_PER_FRAME_NTSC/4.0;
	public static final double UPDATES_PER_FRAME_SCALED_PAL = (double)UPDATES_PER_FRAME_PAL/4.0;
	
	public static final double MS_PER_UPDATE_NTSC = 1000.0 / ((double)REFRESH_RATE_NTSC * (double)UPDATES_PER_FRAME_NTSC);
	public static final double MS_PER_UPDATE_PAL = 1000.0 / ((double)REFRESH_RATE_PAL * (double)UPDATES_PER_FRAME_PAL);
	
	public static final double UPDATES_PER_SEC_NTSC = UPDATES_PER_FRAME_NTSC * REFRESH_RATE_NTSC;
	public static final double UPDATES_PER_SEC_PAL = UPDATES_PER_FRAME_PAL * REFRESH_RATE_PAL;
	
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
	public static final byte MIDDLE_C_S8 = 60;
	
	public static final int ENVCMD__ADSR_DISABLE = 0;
	public static final int ENVCMD__ADSR_HANG = -1;
	public static final int ENVCMD__ADSR_GOTO = -2;
	public static final int ENVCMD__ADSR_RESTART = -3;
	
	public static final int ENVCMD__UNSET = Short.MIN_VALUE;
	public static final int ENV_MAX_DELTA_MS = 32700 * (int)MS_PER_UPDATE_PAL;
	
	//Should only be minor difference, but here anyway
	private static int[] DECAY_TABLE_MS_PAL = null;
	private static int[] DECAY_TABLE_MS_NTSC = null;
	
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
	
	public static float calculateTuning(byte midi_note, byte rootKey, byte fineTune){
		Z64Tuning tune = new Z64Tuning();
		tune.root_key = rootKey;
		tune.fine_tune = fineTune;
		return calculateTuning(midi_note, tune);
	}

	public static float suggestWaveTuningFromInstRegion(int minNote, int maxNote, float rawTune){
		//Calculate tuning value at note in middle of range instead of at Middle C
		int note = minNote + ((maxNote - minNote)/2);
		Z64Tuning tuning = calculateTuning((byte)MIDDLE_C, rawTune);
		return calculateTuning((byte)note, tuning);
	}
	
	public static float clampWaveTuningValue(float input){
		//Keeps from recording extremely high or low sample rates
		final float MIN = 0.25f;
		final float MAX = 2.0f;
		if(input < MIN) input = MIN;
		if(input > MAX) input = MAX;
		return input;
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
	
	public static int envelopeDeltaToMillis(int val){
		//See Audio_AdsrUpdate (in audio_effects.c)
		//I THINK the delta counts PAL audio thread updates
		//There are 200 per second (4 per vsync @ 50hz) so this would
		//	put delta values in units of 5ms?
		return val * (int)MS_PER_UPDATE_PAL;
	}
	
	public static int envelopeMillisToDelta(int val){
		double divraw = (double)val / MS_PER_UPDATE_PAL;
		return (int)Math.round(divraw);
	}
	
	private static void genDecayTable(){
		DECAY_TABLE_MS_PAL = new int[256];
		DECAY_TABLE_MS_NTSC = new int[256];
		double[] seed_table = new double[256];
		
		final double[] HIGH_VAL_SCALERS = {0.25, 0.33, 0.5, 0.66, 0.75};
		
		for(int i = 0; i < 5; i++){
			seed_table[255 - i] = HIGH_VAL_SCALERS[i];
		}
		
		for (int i = 128; i < 251; i++) {
			seed_table[i] = 251.0 - (double)i;
	    }

	    for (int i = 16; i < 128; i++) {
	    	seed_table[i] = (143.0 - (double)i) * 4.0;
	    }

	    for (int i = 1; i < 16; i++) {
	    	seed_table[i] = (23.0 - (double)i) * 60.0;
	    }
	    
	    for(int i = 1; i < 256; i++){
	    	double upd_to_full = Math.ceil(seed_table[i] * (double)UPDATES_PER_FRAME_NTSC);
	    	DECAY_TABLE_MS_NTSC[i] = (int)Math.ceil((upd_to_full / UPDATES_PER_SEC_NTSC) * 1000.0);
	    	
	    	upd_to_full = Math.ceil(seed_table[i] * (double)UPDATES_PER_FRAME_PAL);
	    	DECAY_TABLE_MS_PAL[i] = (int)Math.ceil((upd_to_full / UPDATES_PER_SEC_PAL) * 1000.0);
	    }
	    
	}
	
	//Defaults to NTSC
	public static int releaseValueToMillis(int val){
		return releaseValueToMillis_NTSC(val);
	}
	
	public static int releaseValueToMillis_NTSC(int val){
		if(val < 0 || val > 255) return -1;
		if(DECAY_TABLE_MS_NTSC == null) genDecayTable();
		return DECAY_TABLE_MS_NTSC[val];
	}
	
	public static int releaseValueToMillis_PAL(int val){
		if(val < 0 || val > 255) return -1;
		if(DECAY_TABLE_MS_PAL == null) genDecayTable();
		return DECAY_TABLE_MS_PAL[val];
	}
	
	public static int releaseMillisToValue(int val){
		//Don't use this.
		//Use the table directly.
		//But I put this func here anyway.
		if(DECAY_TABLE_MS_NTSC == null) genDecayTable();
		int minval = Integer.MAX_VALUE;
		int minidx = -1;
		for(int i = 0; i < 256; i++){
			int diff = Math.abs(val - DECAY_TABLE_MS_NTSC[i]);
			if(diff < minval){
				minidx = 1;
				minval = diff;
			}
		}
		
		return DECAY_TABLE_MS_NTSC[minidx];
	}
	
}
