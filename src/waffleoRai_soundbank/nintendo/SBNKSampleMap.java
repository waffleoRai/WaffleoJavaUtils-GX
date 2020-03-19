package waffleoRai_soundbank.nintendo;

import waffleoRai_Sound.nintendo.WaveArchive;
import waffleoRai_SoundSynth.AudioSampleStream;
import waffleoRai_SoundSynth.SoundSampleMap;
import waffleoRai_soundbank.BasicSoundSampleMap;

public class SBNKSampleMap implements SoundSampleMap{
	
	//private DSSoundArchive swar_source;
	//private Map<Integer, Sound> defo_war;
	//private Map<Integer, Map<Integer, Sound>> map;
	
	private BasicSoundSampleMap[] maps;
	
	public SBNKSampleMap(WaveArchive swar, int swar_idx){
		//map = new ConcurrentHashMap<Integer, Map<Integer, Sound>>();
		maps = new BasicSoundSampleMap[4];
		loadWaveArchive(swar, swar_idx);
	}
	
	public void loadWaveArchive(WaveArchive war, int war_idx){
		BasicSoundSampleMap map = new BasicSoundSampleMap();
		
		maps[war_idx] = map;
		int wcount = war.countSounds();
		for(int i = 0; i < wcount; i++){
			map.mapSample(i, war.getWave(i));
		}
		
	}
	
	public void free(){
		maps = new BasicSoundSampleMap[4];
	}

	@Override
	public AudioSampleStream openSampleStream(String samplekey) {
		// TODO Auto-generated method stub
		//Right now doesn't recognize...
		return null;
	}

	@Override
	public AudioSampleStream openSampleStream(int index) {
		//Assumed 0
		BasicSoundSampleMap map = maps[0];
		if(map == null) return null;
		return map.openSampleStream(index);
	}

	@Override
	public AudioSampleStream openSampleStream(int index0, int index1) {
		BasicSoundSampleMap map = maps[index0];
		if(map == null) return null;
		return map.openSampleStream(index1);
	}

}
