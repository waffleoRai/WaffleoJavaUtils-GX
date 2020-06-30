package waffleoRai_Containers.nintendo.wiidisc;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import waffleoRai_Containers.nintendo.WiiDisc;
import waffleoRai_Encryption.AES;
import waffleoRai_Utils.FileBuffer;

public class WiiDataCluster {

	/* --- Instance Variables --- */
	
	private byte[][] cluster_hashTable;
	private byte[][] subgroup_hashTable;
	private byte[][] group_hashTable;
	
	private byte[] data_iv;
	
	private byte[] data;
	
	private boolean zero_sector;
	
	/* --- Construction/Parsing --- */
	
	public WiiDataCluster(){
		cluster_hashTable = new byte[31][20];
		subgroup_hashTable = new byte[8][20];
		group_hashTable = new byte[8][20];
		data_iv = new byte[16];
		zero_sector = false;
	}
	
	public void readCluster(FileBuffer src, long stpos, AES partitionCracker){
		long cpos = WiiDisc.DATACLUSTER_IV_OFFSET + stpos;
		for(int i = 0; i < 16; i++) {data_iv[i] = src.getByte(cpos); cpos++;}
		
		//Now read the hash tables
		byte[] nulliv = new byte[16];
		//Copy
		byte[] hashdata = src.getBytes(stpos, stpos+0x400);
		cpos = stpos + WiiDisc.DATACLUSTER_DATA_OFFSET;
		byte[] rawdata = src.getBytes(cpos, cpos + WiiDisc.DATACLUSTER_DATA_SIZE);
		//(DEBUG) See if it's a zero cluster
		if(isZeroBlock(hashdata, rawdata)){data = new byte[WiiDisc.DATACLUSTER_DATA_SIZE]; zero_sector = true; return;}
		//Decrypt hash data
		hashdata = partitionCracker.decrypt(nulliv, hashdata);

		//Copy back into this structure
		int k = 0;
		for(int i = 0; i < 31; i++){
			byte[] myhash = cluster_hashTable[i];
			for(int j = 0; j < 20; j++){myhash[j] = hashdata[k]; k++;}
		}
		
		//Skip 20 bytes of padding
		k += 20;
		
		for(int i = 0; i < 8; i++){
			byte[] myhash = subgroup_hashTable[i];
			for(int j = 0; j < 20; j++){myhash[j] = hashdata[k]; k++;}
		}
		
		//Skip 32 bytes of padding
		k += 32;
		
		for(int i = 0; i < 8; i++){
			byte[] myhash = group_hashTable[i];
			for(int j = 0; j < 20; j++){myhash[j] = hashdata[k]; k++;}
		}

		//Finally, the actual data
		data = partitionCracker.decrypt(data_iv, rawdata);
	}
	
	public byte[] getDecryptedData(){
		return data;
	}
	
	private boolean isZeroBlock(byte[] hashblock, byte[] datablock){
		for(int i = 0; i < WiiDisc.DATACLUSTER_DATA_OFFSET; i++)
		{
			if(hashblock[i] != 0) return false;
		}
		for(int i = 0; i < WiiDisc.DATACLUSTER_DATA_SIZE; i++)
		{
			if(datablock[i] != 0) return false;
		}
		return true;
	}
	
	public boolean checkClusterHash()
	{
		if(data == null) return false;
		if(zero_sector) return true;
		try {
			MessageDigest sha = MessageDigest.getInstance("SHA-1");
			int blockStart = 0;
			for(int i = 0; i < 31; i++){
				sha.update(data, blockStart, WiiDisc.DATACLUSTER_DATA_OFFSET);
				byte[] ref = cluster_hashTable[i];
				byte[] hash = sha.digest();
				if(!MessageDigest.isEqual(ref, hash)) return false;
				blockStart += WiiDisc.DATACLUSTER_DATA_OFFSET;
			}
			return true;
		} 
		catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	public boolean isZeroBlock(){
		return zero_sector;
	}
	
	/* --- Writing --- */
	
	public void dumpHashTables(String path) throws IOException{
		BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(path));
		for(int i = 0; i < 31; i++)
		{
			byte[] hash = cluster_hashTable[i];
			out.write(hash);
		}
		for(int i = 0; i < 20; i++){out.write(0x00);}
		for(int i = 0; i < 8; i++)
		{
			byte[] hash = subgroup_hashTable[i];
			out.write(hash);
		}
		for(int i = 0; i < 32; i++){out.write(0x00);}
		for(int i = 0; i < 8; i++)
		{
			byte[] hash = group_hashTable[i];
			out.write(hash);
		}
		for(int i = 0; i < 32; i++){out.write(0x00);}
		
		out.close();
	}
	
	public void dumpData(String path) throws IOException{
		BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(path));
		out.write(data);
		out.close();
	}

}
