package waffleoRai_SoundSynth.soundformats.game;

import waffleoRai_SoundSynth.AudioSampleStream;
import waffleoRai_SoundSynth.Filter;

public class PSXReverb implements Filter{
	
	public static final int REGIDX_dAPF1 = 0;
	public static final int REGIDX_dAPF2 = 1;
	public static final int REGIDX_vIIR = 2;
	public static final int REGIDX_vCOMB1 = 3;
	public static final int REGIDX_vCOMB2 = 4;
	public static final int REGIDX_vCOMB3 = 5;
	public static final int REGIDX_vCOMB4 = 6;
	public static final int REGIDX_vWALL = 7;
	public static final int REGIDX_vAPF1 = 8;
	public static final int REGIDX_vAPF2 = 9;
	public static final int REGIDX_mLSAME = 10;
	public static final int REGIDX_mRSAME = 11;
	public static final int REGIDX_mLCOMB1 = 12;
	public static final int REGIDX_mRCOMB1 = 13;
	public static final int REGIDX_mLCOMB2 = 14;
	public static final int REGIDX_mRCOMB2 = 15;
	public static final int REGIDX_dLSAME = 16;
	public static final int REGIDX_dRSAME = 17;
	public static final int REGIDX_mLDIFF = 18;
	public static final int REGIDX_mRDIFF = 19;
	public static final int REGIDX_mLCOMB3 = 20;
	public static final int REGIDX_mRCOMB3 = 21;
	public static final int REGIDX_mLCOMB4 = 22;
	public static final int REGIDX_mRCOMB4 = 23;
	public static final int REGIDX_dLDIFF = 24;
	public static final int REGIDX_dRDIFF = 25;
	public static final int REGIDX_mLAPF1 = 26;
	public static final int REGIDX_mRAPF1 = 27;
	public static final int REGIDX_mLAPF2 = 28;
	public static final int REGIDX_mRAPF2 = 29;
	public static final int REGIDX_vLIN = 30;
	public static final int REGIDX_vRIN = 31;
	
	private AudioSampleStream source;
	
	public PSXReverb(AudioSampleStream input)
	{
		this(input, PSXReverbPreset.HALL);
	}
	
	public PSXReverb(AudioSampleStream input, short[] preset)
	{
		//TODO
		
	}
	
	public float getSampleRate() {return source.getSampleRate();}
	public int getBitDepth() {return source.getBitDepth();}
	
	public int getChannelCount() {return 2;}
	
	public int[] nextSample() throws InterruptedException {
		//TODO
		return null;
	}
	
	
	public void close() {
		// TODO Auto-generated method stub
		
	}
	
	public void setInput(AudioSampleStream input) {
		// TODO Auto-generated method stub
		
	}
	
	public void reset() {
		// TODO Auto-generated method stub
		
	}

}
