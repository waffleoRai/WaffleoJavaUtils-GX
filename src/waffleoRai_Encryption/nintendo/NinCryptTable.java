package waffleoRai_Encryption.nintendo;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import waffleoRai_Encryption.AES;
import waffleoRai_Encryption.FileCryptRecord;
import waffleoRai_Encryption.FileCryptTable;
import waffleoRai_Utils.FileBuffer;

/*
 * Serialized Format
 * Big Endian
 * 
 * Outer layer...
 * Magic "NCTc"			[4]
 * Version				[4]
 * Uncompressed Size	[4]
 * Decomp Data Hash		[20]
 * 
 * Within deflated data...
 * 
 * Key table
 * 	# of 128-bit keys (type 0)	[2]
 * 	# of 192-bit keys (type 1)	[2]
 * 	# of 256-bit keys (type 2)	[2]
 * 	# padding (zeroes)			[2]
 * 	128-bit keys				[16 * n]
 *  192-bit keys				[24 * n]
 *  256-bit keys				[32 * n]
 *  
 * Record table (records are variable length)
 * 	Record format
 * 		Crypt Type/KeyType	[1]
 * 			Crypt Type is high nybble
 * 		IV Length			[1]
 * 		Key Index			[2]
 * 		File UID			[8]
 * 		Crypt Offset		[8]
 * 		IV (if present)		[Var]
 * 			No Crypt:	Not present
 *			ECM:		Not present 
 *			CBC:		16 byte AES IV
 *			CTR:		16 byte AES counter
 *			XTS:		8 byte sector index for tweak
 *
 */

public class NinCryptTable extends FileCryptTable{
	
	public static final String MAGIC = "NCTc";
	public static final int VERSION = 1;

	public boolean importFromFile(String filepath) throws IOException{

		FileBuffer file = FileBuffer.createBuffer(filepath, true);
		//Remember to grab version when need to update.
		
		int decompsize = file.intFromFile(8L);
		int compsize = (int)(file.getFileSize() - 32);
		byte[] hash = new byte[20];
		for(int i = 0; i < 20; i++) hash[i] = file.getByte(12+i);
		
		//Inflate
		byte[] compdat = file.getBytes(32, 32+compsize);
		Inflater dec = new Inflater();
		dec.setInput(compdat);
		byte[] result = new byte[decompsize];
		try{
			dec.inflate(result);
			dec.end();
		}
		catch(Exception x){
			x.printStackTrace();
			//throw new IOException("Data could not be inflated!");
			return false;
		}
		
		//Check hash
		try{
			MessageDigest sha = MessageDigest.getInstance("SHA-1");
			sha.update(result);
			byte[] ihash = sha.digest();
			if(!Arrays.equals(ihash, hash)){
				System.err.println("Data hash failed!");
				return false;
			}
		}
		catch(Exception x){
			System.err.println("Data hash failed!");
			x.printStackTrace();
			return false;
		}
		
		file = FileBuffer.wrap(result);
		
		//Key table
		file.setCurrentPosition(0L);
		int[] keyct = new int[3];
		for(int i = 0; i < 3; i++) keyct[i] = Short.toUnsignedInt(file.nextShort());
		file.skipBytes(2L);
		
		int keylen = 16;
		for(int i = 0; i < 3; i++){
			for(int j = 0; j < keyct[i]; j++){
				byte[] key = new byte[keylen];
				for(int k = 0; k < keylen; k++) key[k] = file.nextByte();
				super.addKey(i, key);
			}
			keylen += 8;
		}
		
		//Record table
		while(file.hasRemaining()){
			int types = Byte.toUnsignedInt(file.nextByte());
			int ctype = (types >>> 4) & 0xF;
			int ktype = types & 0xF;
			int ivlen = Byte.toUnsignedInt(file.nextByte());
			int kidx = Short.toUnsignedInt(file.nextShort());
			long fuid = file.nextLong();
			long coff = file.nextLong();
			byte[] iv = null;
			if(ivlen > 0){
				iv = new byte[ivlen];
				for(int j = 0; j < ivlen; j++) iv[j] = file.nextByte();
			}
			
			FileCryptRecord rec = null;
			switch(ctype){
			case NinCrypto.CRYPTTYPE_AESCBC: rec = new NinCBCCryptRecord(fuid); break;
			case NinCrypto.CRYPTTYPE_AESCTR: rec = new NinCTRCryptRecord(fuid); break;
			case NinCrypto.CRYPTTYPE_AESXTS: rec = new NinXTSCryptRecord(fuid); break;
			}
			
			rec.setKeyType(ktype);
			rec.setKeyIndex(kidx);
			rec.setCryptOffset(coff);
			if(iv != null) rec.setIV(iv);
			
			super.addRecord(fuid, rec);
		}
		
		return true;
	}

	public boolean exportToFile(String filepath) throws IOException{

		//Key table
		int[] kcounts = new int[4];
		for(int i = 0; i < 3; i++) kcounts[i] = super.keys.get(i).size();
		int ktblsize = 8;
		int klen = 16;
		for(int i = 0; i < 3; i++) {ktblsize += kcounts[i] * klen; klen += 8;}
		FileBuffer ktbl = new FileBuffer(ktblsize, true);
		for(int i = 0; i < 4; i++) ktbl.addToFile((short)kcounts[i]);
		for(int i = 0; i < 3; i++) {
			ArrayList<byte[]> klist = super.keys.get(i);
			for(byte[] key : klist){
				for(int j = 0; j < key.length; j++) ktbl.addToFile(key[j]);
			}
		}
		
		//Rec table
		Collection<FileCryptRecord> reclist = super.records.values();
		int rcount = reclist.size();
		int est = rcount * 36;
		FileBuffer rtbl = new FileBuffer(est, true);
		for(FileCryptRecord r : reclist){
			int ck = (r.getCryptType() << 4) | r.getKeyType();
			rtbl.addToFile((byte)ck);
			
			byte[] iv = r.getIV();
			if(iv == null) rtbl.addToFile((byte)0x00);
			else rtbl.addToFile((byte)iv.length);
			
			rtbl.addToFile((short)r.getKeyIndex());
			rtbl.addToFile(r.getFileUID());
			rtbl.addToFile(r.getCryptOffset());
			if(iv != null){
				for(int j = 0; j < iv.length; j++) rtbl.addToFile(iv[j]);
			}
		}
		
		//Combine, hash, and deflate
		int ksize = (int)ktbl.getFileSize();
		int rsize = (int)rtbl.getFileSize();
		int dsize = ksize + rsize;
		byte[] dat = new byte[dsize];
		for(int i = 0; i < ksize; i++) dat[i] = ktbl.getByte(i);
		for(int i = 0; i < rsize; i++) dat[i+ksize] = rtbl.getByte(i);
		
		byte[] hash = null;
		try{
			MessageDigest sha = MessageDigest.getInstance("SHA-1");
			sha.update(dat);
			hash = sha.digest();
		}
		catch(Exception x){
			System.err.println("Data hash failed!");
			x.printStackTrace();
			return false;
		}
		
		Deflater comp = new Deflater();
		comp.setInput(dat);
		comp.finish();
		byte[] compbytes = new byte[dsize];
		int complen = comp.deflate(compbytes);
		comp.end();
		
		FileBuffer header = new FileBuffer(12, true);
		header.printASCIIToFile(MAGIC);
		header.addToFile(VERSION);
		header.addToFile(dsize);
		
		BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filepath));
		header.writeToStream(bos);
		bos.write(hash);
		bos.write(compbytes, 0, complen);
		bos.close();
		
		return true;
	}

	public void printMe(){

		System.err.println("-------- NinCryptTable --------");
		System.err.println("~~~ Keys ~~~");
		
		ArrayList<byte[]> klist = super.keys.get(0);
		System.err.println("128-bit:");
		int i = 0;
		for(byte[] k : klist) System.err.println("\t" + i++ + "\t" + AES.bytes2str(k));
		System.err.println();
		
		klist = super.keys.get(1);
		System.err.println("192-bit:");
		i = 0;
		for(byte[] k : klist) System.err.println("\t" + i++ + "\t" + AES.bytes2str(k));
		System.err.println();
		
		klist = super.keys.get(2);
		System.err.println("256-bit:");
		i = 0;
		for(byte[] k : klist) System.err.println("\t" + i++ + "\t" + AES.bytes2str(k));
		System.err.println();
		
		System.err.println("~~~ Records ~~~");
		Collection<FileCryptRecord> records = super.records.values();
		for(FileCryptRecord r : records){
			System.err.println();
			System.err.println("-> CryptRecord");
			System.err.println("\tUID: " + Long.toHexString(r.getFileUID()));
			System.err.println("\tOffset: 0x" + Long.toHexString(r.getCryptOffset()));
			System.err.println("\tCrypt Type: " + r.getCryptType());
			System.err.println("\tKey Type: " + r.getKeyType());
			System.err.println("\tKey Index: " + r.getKeyIndex());
			if(r.getIV() != null){
				System.err.println("\tIV: " + AES.bytes2str(r.getIV()));
			}
		}
		
	}
	
}
