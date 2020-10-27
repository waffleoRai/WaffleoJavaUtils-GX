package waffleoRai_Containers.nintendo.wiidisc;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import waffleoRai_Containers.nintendo.GCWiiDisc;
import waffleoRai_Encryption.FileCryptRecord;
import waffleoRai_Encryption.nintendo.NinCryptTable;
import waffleoRai_Files.tree.DirectoryNode;
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
	
	public void readPartion(FileBuffer discData, WiiCryptListener observer, boolean auto_decrypt) throws IOException, UnsupportedFileTypeException{
		for(int i = 0; i < oPartitions.length; i++){
			if (oPartitions[i] != null) oPartitions[i].readFromDisc(discData, observer, auto_decrypt);
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
	
	/* --- Crypto --- */
	
	public void unlock() throws UnsupportedFileTypeException{
		if(oPartitions == null) return;
		for(int i = 0; i < oPartitions.length; i++){
			if(oPartitions[i] != null) oPartitions[i].unlock();
		}
	}
	
	public List<FileCryptRecord> loadCryptTable(NinCryptTable tbl){
		List<FileCryptRecord> rlist = new LinkedList<FileCryptRecord>();
		if(oPartitions == null) return rlist;
		for(int i = 0; i < oPartitions.length; i++){
			if(oPartitions[i] != null) rlist.add(oPartitions[i].loadCryptTable(tbl));
		}
		return rlist;
	}
	
	public DirectoryNode buildDirectTree(String wiiimg_path, boolean low_fs) throws IOException{
		DirectoryNode root = new DirectoryNode(null, "");
		if(oPartitions == null) return root;
		for(int i = 0; i < oPartitions.length; i++){
			if(oPartitions[i] != null){
				DirectoryNode proot = oPartitions[i].buildDirectTree(wiiimg_path, low_fs);
				if(proot.getFileName() == null || proot.getFileName().isEmpty()) proot.setFileName("part" + i);
				proot.setParent(root);
			}
		}
		return root;
	}
	
	/* --- Cleanup --- */
	
	public void deleteTempFiles() throws IOException{
		for(int i = 0; i < oPartitions.length; i++){
			if(oPartitions[i] != null) oPartitions[i].deleteTempFile();
		}
	}
	
}
