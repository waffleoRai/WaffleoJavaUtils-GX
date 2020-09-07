package waffleoRai_Containers.nintendo.nx;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import waffleoRai_Encryption.FileCryptRecord;
import waffleoRai_Encryption.StaticDecryptor;
import waffleoRai_Encryption.nintendo.NinCryptTable;
import waffleoRai_Encryption.nintendo.NinCrypto;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Files.tree.FileNode;

public class NXDecryptor implements StaticDecryptor{
	
	private NinCryptTable crypt_table;
	private String temp_dir;
	private List<String> temp_paths;
	
	private Random rng;
	
	public NXDecryptor(NinCryptTable table, String tempdir){
		crypt_table = table;
		temp_dir = tempdir;
		NXCrypt.setTempDir(tempdir);
		temp_paths = new LinkedList<String>();
		rng = new Random();
	}

	public FileNode decrypt(FileNode node) {
		//Returns info describing location it has been decrypted to.

		String ckey = node.getMetadataValue(NinCrypto.METAKEY_CRYPTGROUPUID);
		if(ckey == null) return node; //Same as before
		
		long cuid = 0L;
		try{cuid = Long.parseUnsignedLong(ckey, 16);}
		catch(NumberFormatException x){x.printStackTrace(); return node;}
		
		FileCryptRecord crec = crypt_table.getRecord(cuid);
		if(crec == null) return node;
		
		//Get key
		byte[] key = crypt_table.getKey(crec.getKeyType(), crec.getKeyIndex());
		
		try{
			FileBuffer dat = NXCrypt.decryptData(node, crec, key);
			String datpath = dat.getPath();
			if(FileBuffer.fileExists(datpath)){
				//Assume that's it
				temp_paths.add(datpath);
				dat.dispose();
				FileNode dnode = new FileNode(null, "");
				dnode.setSourcePath(datpath);
				dnode.setOffset(0L);
				dnode.setLength(node.getLength());
				return dnode;
			}
			else{
				//Save to disc and return node describing it
				datpath = temp_dir + File.separator + "nxdec_" + Long.toHexString(rng.nextLong()) + ".tmp";
				temp_paths.add(datpath);
				dat.writeFile(datpath);
				dat.dispose();
				
				FileNode dnode = new FileNode(null, "");
				dnode.setSourcePath(datpath);
				dnode.setOffset(0L);
				dnode.setLength(node.getLength());
				return dnode;
			}
		}
		catch(IOException x){
			x.printStackTrace();
			return node;
		}
	}
	
	public void clearTempFiles(){
		//Cleans up temp directory
		for(String p : temp_paths){
			try{
				Files.deleteIfExists(Paths.get(p));
			}
			catch(IOException x){
				System.err.println("NXDecryptor.clearTempFiles() || WARNING: " + p + " could not be deleted!");
				x.printStackTrace();
			}
		}
		temp_paths.clear();
	}

}
