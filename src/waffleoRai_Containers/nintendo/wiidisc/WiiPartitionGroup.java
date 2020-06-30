package waffleoRai_Containers.nintendo.wiidisc;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import waffleoRai_Containers.nintendo.GCWiiDisc;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;

public class WiiPartitionGroup {

	/* --- Instance Variables --- */
	
	private WiiPartition[] oPartitions;
	
	/* --- Construction --- */
	
	public WiiPartitionGroup(int subCount){
		oPartitions = new WiiPartition[subCount];
	}
	
	public void addPartition(int idx, int type, long staddr) throws IOException{
		oPartitions[idx] = new WiiPartition(type, staddr);
	}
	
	/* --- Parsing --- */
	
	public void readPartion(FileBuffer discData) throws IOException, UnsupportedFileTypeException{
		for(int i = 0; i < oPartitions.length; i++){
			if (oPartitions[i] != null) oPartitions[i].readFromDisc(discData);
		}
	}

	/* --- Writing --- */
	
	public boolean dumpRaw(String directory) throws IOException{
		boolean b = true;
		if(!FileBuffer.directoryExists(directory)) Files.createDirectories(Paths.get(directory));
		for(int i = 0; i < oPartitions.length; i++)
		{
			if(oPartitions[i] != null)
			{
				String name = oPartitions[i].getGameID();
				String target = directory + File.separator + name + "." + GCWiiDisc.GCM_EXT;
				b = b && oPartitions[i].writeDecryptedRaw(target);
			}
		}
		return b;
	}
	
	public boolean dumpPartition(String directory) throws IOException{
		boolean b = true;
		for(int i = 0; i < oPartitions.length; i++){
			if(oPartitions[i] != null){
				b = b && (oPartitions[i].dumpPartition(directory));
			}
		}
		return b;
	}
	
	/* --- Getters --- */
	
	public List<WiiPartition> getSubPartitions(){
		List<WiiPartition> plist = new ArrayList<WiiPartition>(oPartitions.length + 1);
		for(int i = 0; i < oPartitions.length; i++){
			if(oPartitions[i] != null) plist.add(oPartitions[i]);
		}
		return plist;
	}
	
	/* --- Cleanup --- */
	
	public void deleteTempFiles() throws IOException{
		for(int i = 0; i < oPartitions.length; i++){
			if(oPartitions[i] != null) oPartitions[i].deleteTempFile();
		}
	}
	
}
