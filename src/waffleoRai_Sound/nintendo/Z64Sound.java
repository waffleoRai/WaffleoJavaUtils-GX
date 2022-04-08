package waffleoRai_Sound.nintendo;

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

}
