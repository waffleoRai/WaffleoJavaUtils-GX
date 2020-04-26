package waffleoRai_soundbank.vab;

import java.util.List;

import waffleoRai_Sound.psx.PSXVAG;
import waffleoRai_SoundSynth.AudioSampleStream;
import waffleoRai_SoundSynth.SoundSampleMap;

public class VABSampleMap implements SoundSampleMap{
	
	private PSXVAG[] sounds;
	
	public VABSampleMap(List<PSXVAG> src)
	{
		int scount = src.size();
		sounds = new PSXVAG[scount];
		int i = 0;
		for(PSXVAG s : src){sounds[i] = s; i++;}
	}

	@Override
	public AudioSampleStream openSampleStream(String samplekey) 
	{
		try
		{
			int i = Integer.parseInt(samplekey);
			return openSampleStream(i);
		}
		catch(Exception e){return null;}
	}

	@Override
	public AudioSampleStream openSampleStream(int index) 
	{
		if(index < 0) return null;
		if(index >= sounds.length) return null;
		
		PSXVAG sound = sounds[index];
		return sound.createSampleStream();
	}
	
	public AudioSampleStream openSampleStream(int index0, int index1){
		return openSampleStream(index1);
	}

	public void free(){
		//TODO
	}
	
}
