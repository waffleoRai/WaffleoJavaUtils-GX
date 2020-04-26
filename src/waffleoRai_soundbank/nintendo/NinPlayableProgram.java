package waffleoRai_soundbank.nintendo;

import waffleoRai_SoundSynth.AudioSampleStream;
import waffleoRai_SoundSynth.SoundSampleMap;
import waffleoRai_SoundSynth.SynthProgram;
import waffleoRai_SoundSynth.SynthSampleStream;
import waffleoRai_SoundSynth.soundformats.game.NinSynthSampleStream;

public class NinPlayableProgram implements SynthProgram{
	
	private NinBankNode art_data;
	private SoundSampleMap sample_map;
	
	public NinPlayableProgram(NinBankNode data, SoundSampleMap samples){
		
		art_data = data;
		sample_map = samples;
		
	}

	@Override
	public SynthSampleStream getSampleStream(byte pitch, byte velocity) throws InterruptedException {
		NinTone tone = art_data.getToneForKey(pitch);
		if(tone == null) return null;
		
		int war = tone.getWARNumber();
		int wave = tone.getWaveNumber();
		
		AudioSampleStream str = sample_map.openSampleStream(war, wave);
		NinSynthSampleStream sstr = new NinSynthSampleStream(str, tone, pitch, velocity, str.getSampleRate());
		
		return sstr;
	}

	@Override
	public SynthSampleStream getSampleStream(byte pitch, byte velocity, float targetSampleRate)
			throws InterruptedException {
		NinTone tone = art_data.getToneForKey(pitch);
		if(tone == null) return null;
		//System.err.println("Hi :)");
		
		int war = tone.getWARNumber();
		int wave = tone.getWaveNumber();
		//System.err.println("Tone -- SWAR: " + war + " SWAV: " + wave);
		
		AudioSampleStream str = sample_map.openSampleStream(war, wave);
		NinSynthSampleStream sstr = new NinSynthSampleStream(str, tone, pitch, velocity, targetSampleRate);
		
		return sstr;
	}

	public void free(){
		art_data.clearKeyMapping();
		//sample_map.free();
		sample_map = null;
	}
	
}
