package waffleoRai_Containers.nintendo.sar;

import java.util.List;

public interface NinsarGroup {
	
	public String getName();
	//public int getIndex();
	public NinsarDataFile getFile(int index);
	public List<NinsarDataFile> getAllFiles();
	public int countMembers();

}
