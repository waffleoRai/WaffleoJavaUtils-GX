package waffleoRai_Sound.nintendo;

import waffleoRai_Sound.SampleChannel;

public abstract class NinWave extends NinStreamableSound{
	
	/*--- Copy ---*/
	
	public static void copyChannel(NinWave src, NinWave trg, int channel)
	{
		trg.encodingType = src.encodingType;
		trg.loops = src.loops;
		trg.channelCount = 1;
		trg.sampleRate = src.sampleRate;
		trg.loopStart = src.loopStart;
		trg.unityNote = src.unityNote;
		trg.fineTune = src.fineTune;
		trg.playEnd = false;
		
		if(trg.encodingType == NinSound.ENCODING_TYPE_DSP_ADPCM)
		{
			trg.channel_adpcm_info = new ADPCMTable[1];
			trg.channel_adpcm_info[0] = src.channel_adpcm_info[channel];
		}
		trg.rawSamples = new SampleChannel[1];
		trg.rawSamples[0] = new SampleChannel(src.rawSamples[channel]);
	}
	
	/*--- Sound ---*/
	

}
