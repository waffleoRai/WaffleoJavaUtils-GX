package waffleoRai_Sound.nintendo;

import java.util.ArrayList;
import java.util.List;

import waffleoRai_soundbank.nintendo.NinTone;

public abstract class NinWaveSet {
	
	/*--- Inner Classes ---*/
	
	public static class WaveInfo
	{
		
		//WSD Info
		public float pitch;
		public int pan;
		public int surroundPan;
		public int fxSendA;
		public int fxSendB;
		public int fxSendC;
		public int mainSend; //Signed
		
		//Note Events
		public List<List<NoteEvent>> noteEvents;
		
		//Note Info
		public List<NinTone> noteInfo;
		
		public WaveInfo(int noteEventGroups, int tones)
		{
			noteEvents = new ArrayList<List<NoteEvent>>(noteEventGroups+1);
			noteInfo = new ArrayList<NinTone>(tones+1);
		}
		
	}
	
	public static class NoteEvent
	{
		public float position;
		public float length;
		public int decay;
	}
	
	/*--- Instance Variables ---*/
	
	protected List<WaveInfo> entries;
	
	/*--- Getters ---*/
	
	public int countEntries()
	{
		return entries.size();
	}
	
	public WaveInfo getEntry(int index)
	{
		return entries.get(index);
	}
	
	/*--- Extraction ---*/
	
	public boolean writeWAV(int wavIndex, WaveArchive soundArc, String pathStem)
	{
		if(soundArc == null) return false;
		WaveInfo info = entries.get(wavIndex);
		if(info.noteInfo.size() == 1)
		{
			String path = pathStem + ".wav";
			NinTone ni = info.noteInfo.get(0);
			if(ni == null) return false;
			int wavnum = ni.getWaveNumber();
			NinWave wav = soundArc.getWave(wavnum);
			if(wav == null) return false;
			wav.setUnityNote(ni.getOriginalKey());
			wav.setFineTune(ni.getFineTune());
			return wav.writeWAV(path);
		}
		else if(info.noteInfo.isEmpty()) return false;
		
		int i = 0;
		boolean allgood = true;
		for(NinTone ni : info.noteInfo)
		{
			String iStr = String.format("%03d", i);
			String path = pathStem + "_NOTE" + iStr + ".wav";
			int wavnum = ni.getWaveNumber();
			NinWave wav = soundArc.getWave(wavnum);
			if(wav == null) return false;
			wav.setUnityNote(ni.getOriginalKey());
			wav.setFineTune(ni.getFineTune());
			allgood = allgood && wav.writeWAV(path);
		}
		
		return allgood;
	}
		

}
