package waffleoRai_Containers.nintendo.nx;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import waffleoRai_Encryption.AES;
import waffleoRai_Encryption.DecryptorMethod;
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
	
	private Map<byte[], AES> aes_map;
	
	private Random rng;
	
	public NXDecryptor(NinCryptTable table, String tempdir){
		crypt_table = table;
		temp_dir = tempdir;
		NXCrypt.setTempDir(tempdir);
		temp_paths = new LinkedList<String>();
		rng = new Random();
		
		aes_map = new HashMap<byte[], AES>();
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
	
	public DecryptorMethod generateDecryptor(FileNode node){
		
		String ckey = node.getMetadataValue(NinCrypto.METAKEY_CRYPTGROUPUID);
		
		long cuid = 0L;
		try{cuid = Long.parseUnsignedLong(ckey, 16);}
		catch(NumberFormatException x){x.printStackTrace(); return null;}
		
		FileCryptRecord crec = crypt_table.getRecord(cuid);
		if(crec == null) return null;
		
		//Get key
		byte[] key = crypt_table.getKey(crec.getKeyType(), crec.getKeyIndex());
		
		if(crec.getCryptType() == NinCrypto.CRYPTTYPE_AESCTR){
			//Get AES engine object
			AES engine = aes_map.get(key);
			if(engine == null){
				engine = new AES(key);
				engine.setCTR();
				aes_map.put(key, engine);
			}
			
			//Derive file relative base CTR
			byte[] sec_ctr = crec.getIV();
			long diff = node.getOffset() - crec.getCryptOffset();
			byte[] file_ctr = NXCrypt.adjustCTR(sec_ctr, diff);
			
			//Debug
			/*System.err.println("NXDecryptor.generateDecryptor || Crypt Record UID: " + Long.toHexString(cuid));
			System.err.println("\tRecord Offset: 0x" + Long.toHexString(crec.getCryptOffset()));
			System.err.println("\tNode Offset: 0x" + Long.toHexString(node.getOffset()));
			System.err.println("\tKey: " + NXCrypt.printHash(key));
			System.err.println("\tRecord CTR: " + NXCrypt.printHash(sec_ctr));
			System.err.println("\tNode CTR: " + NXCrypt.printHash(file_ctr));*/
			
			//Put together
			return new NXCrypt.NXCTRDecMethod(engine, file_ctr);
		}
		else if(crec.getCryptType() == NinCrypto.CRYPTTYPE_AESXTS){
			//Derive file base sector
			long sec_sec = 0L;
			byte[] s = crec.getIV();
			for(int i = 0; i < 8; i++){
				sec_sec |= Byte.toUnsignedLong(s[i]);
				sec_sec = sec_sec << 8;
			}
			
			long base_sec = ((node.getOffset() - crec.getCryptOffset()) >>> 9) + sec_sec;
			return new NXCrypt.NXXTSDecMethod(key, base_sec, 9);
		}
		
		return null;
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
