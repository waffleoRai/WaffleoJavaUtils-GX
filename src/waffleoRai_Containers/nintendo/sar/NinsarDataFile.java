package waffleoRai_Containers.nintendo.sar;

import java.util.Collection;

public interface NinsarDataFile {
	
	public String getName();
	//public int getIndex();
	public NinsarSound getSound(int index);
	public Collection<NinsarSound> getAllSounds();
	public int countSounds();
	public boolean writeToDisk(String directory);

}
