package waffleoRai_Sound.nintendo;

import java.util.Iterator;
import java.util.List;

public abstract class WaveArchive implements Iterable<NinWave>{

	/*--- Instance Variables ---*/
	
	protected List<NinWave> sounds;
	
	/*--- Getters ---*/
	
	public NinWave getWave(int index)
	{
		return sounds.get(index);
	}
	
	public int countSounds()
	{
		return sounds.size();
	}
	
	public Iterator<NinWave> iterator() 
	{
		return sounds.iterator();
	}
	
	/*--- Setters ---*/
	
	public void addSound(NinWave sound)
	{
		sounds.add(sound);
	}
	
	public void clearSounds()
	{
		sounds.clear();
	}

}
