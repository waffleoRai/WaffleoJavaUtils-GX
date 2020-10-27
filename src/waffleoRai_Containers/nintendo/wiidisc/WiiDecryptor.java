package waffleoRai_Containers.nintendo.wiidisc;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import waffleoRai_Containers.nintendo.wiidisc.WiiCrypt.WiiCBCDecMethod;
import waffleoRai_Encryption.AES;
import waffleoRai_Encryption.DecryptorMethod;
import waffleoRai_Encryption.FileCryptRecord;
import waffleoRai_Encryption.StaticDecryptor;
import waffleoRai_Encryption.nintendo.NinCryptTable;
import waffleoRai_Encryption.nintendo.NinCrypto;
import waffleoRai_Files.tree.FileNode;
import waffleoRai_Utils.FileBuffer;

public class WiiDecryptor implements StaticDecryptor{
	
	private NinCryptTable crypt_table;
	private String temp_dir;
	private List<String> temp_paths;
	
	private Map<byte[], AES> aes_map;
	
	private Random rng;
	
	public WiiDecryptor(NinCryptTable table, String tempdir){
		crypt_table = table;
		temp_dir = tempdir;
		temp_paths = new LinkedList<String>();
		rng = new Random();
		
		aes_map = new HashMap<byte[], AES>();
	}

	public FileNode decrypt(FileNode node) {
		//Returns info describing location it has been decrypted to.

		//Get node decryption info
		long cid = -1L;
		String cidstr = node.getMetadataValue(NinCrypto.METAKEY_CRYPTGROUPUID);
		if(cidstr == null) return node;
		try{cid = Long.parseUnsignedLong(cidstr, 16);}
		catch(NumberFormatException x){x.printStackTrace(); return node;}
		
		//Look up record.
		FileCryptRecord r = crypt_table.getRecord(cid);
		if(r == null) return node;
		
		//Get AES engine
		byte[] key = crypt_table.getKey(r.getKeyType(), r.getKeyIndex());
		if(key == null) return node;
		AES aes = aes_map.get(key);
		if(aes == null){
			aes = new AES(key);
			aes_map.put(key, aes);
		}
		
		//Block align.
		long ed = node.getOffset() + node.getLength();
		long b_st = (node.getOffset() >>> 15) << 15;
		long b_ed = (ed >>> 15) << 15;
		if(b_ed < ed) b_ed += 0x8000;
		
		//Generate temp file path
		String tpath = temp_dir + File.separator + "wiicrypt_tempfile_" + Long.toHexString(rng.nextLong()) + ".tmp";
		temp_paths.add(tpath);
		
		try{
			String srcpath = node.getSourcePath();
			long sz = b_ed - b_st;
			long pos = 0;
			
			BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(tpath));
			FileBuffer buff = FileBuffer.createBuffer(srcpath, b_st, b_ed, true);
			WiiCrypt crypto = new WiiCrypt(aes);
			while(pos < sz){
				byte[] block = crypto.decryptBlock(buff, pos);
				pos += 0x8000;
				bos.write(block);
			}
			
			bos.close();
			
		}
		catch(Exception x){
			x.printStackTrace();
		}
		
		return null;
	}
	
	public DecryptorMethod generateDecryptor(FileNode node){
		
		long cid = -1L;
		String cidstr = node.getMetadataValue(NinCrypto.METAKEY_CRYPTGROUPUID);
		if(cidstr == null) return null;
		try{cid = Long.parseUnsignedLong(cidstr, 16);}
		catch(NumberFormatException x){x.printStackTrace(); return null;}
		
		//Look up record.
		FileCryptRecord r = crypt_table.getRecord(cid);
		if(r == null) return null;
		
		//Get AES engine
		byte[] key = crypt_table.getKey(r.getKeyType(), r.getKeyIndex());
		if(key == null) return null;
		AES aes = aes_map.get(key);
		if(aes == null){
			aes = new AES(key);
			aes_map.put(key, aes);
		}
		
		WiiCBCDecMethod decm = new WiiCBCDecMethod(aes);
		return decm;
	}
	
	public void clearTempFiles(){
		//Cleans up temp directory
		for(String p : temp_paths){
			try{
				Files.deleteIfExists(Paths.get(p));
			}
			catch(IOException x){
				System.err.println("WiiDecryptor.clearTempFiles() || WARNING: " + p + " could not be deleted!");
				x.printStackTrace();
			}
		}
		temp_paths.clear();
	}

	public void dispose(){
		clearTempFiles();
		aes_map.clear();
	}

}
