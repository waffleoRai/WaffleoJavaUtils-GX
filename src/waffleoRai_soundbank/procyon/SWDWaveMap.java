package waffleoRai_soundbank.procyon;

import waffleoRai_Sound.Sound;
import waffleoRai_SoundSynth.AudioSampleStream;
import waffleoRai_SoundSynth.SoundSampleMap;

public class SWDWaveMap implements SoundSampleMap{
	
	private Sound waves[];
	
	public SWDWaveMap(int count){
		waves = new Sound[count];
	}
	
	public void setWave(int idx, SWDWave wav){
		if(wav == null) return;
		//waves[idx] = wav.getAsNormalizedPCM();
		waves[idx] = wav.getAsPCM();
	}

	public AudioSampleStream openSampleStream(String samplekey) {
		// TODO?
		return null;
	}

	public AudioSampleStream openSampleStream(int index) {
		if(waves == null) return null;
		if(index < 0 || index >= waves.length) return null;
		Sound snd = waves[index];
		if(snd == null) return null;
		return snd.createSampleStream(snd.loops());
	}

	public AudioSampleStream openSampleStream(int index0, int index1) {
		return openSampleStream(index1);
	}

	public void free() {
		if(waves == null) return;
		for(int i = 0; i < waves.length; i++){
			waves[i] = null;
		}
		waves = null;
	}

}
