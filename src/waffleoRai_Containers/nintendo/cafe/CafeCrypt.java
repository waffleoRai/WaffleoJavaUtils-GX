package waffleoRai_Containers.nintendo.cafe;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

import waffleoRai_Encryption.AES;
import waffleoRai_Encryption.DecryptorMethod;
import waffleoRai_Encryption.StaticDecryption;
import waffleoRai_Encryption.StaticDecryptor;
import waffleoRai_Encryption.nintendo.NinCryptTable;
import waffleoRai_Files.EncryptionDefinition;
import waffleoRai_Files.EncryptionDefinitions;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.StreamWrapper;

public class CafeCrypt {
	
	private static CafeAESDef plain_def;
	private static CafeSectorAESDef sec_def;
	
	public static class CafeAESDef implements EncryptionDefinition{

		public static final int DEF_ID = 0xc11ae502;
		public static final String DEFO_ENG_DESC = "Wii Disc AES-128-CBC";
		
		private static CafeAESDef static_inst;
		
		private String desc;
		
		public CafeAESDef(){
			desc = DEFO_ENG_DESC;
		}

		public int getID() {return DEF_ID;}
		public String getDescription() {return desc;}
		public void setDescription(String s) {desc = s;}

		public void setStateValue(int key, int value) {}
		public int getStateValue(int key) {return 0;}
		
		//Wheeeew I need to properly deprecate these
		public boolean decrypt(StreamWrapper input, OutputStream output, List<byte[]> keydata) {return false;}
		public boolean encrypt(StreamWrapper input, OutputStream stream, List<byte[]> keydata) {return false;}
		
		public int[] getExpectedKeydataSizes() {
			return new int[]{16};
		}

		public static CafeAESDef getDefinition(){
			if(static_inst != null) return static_inst;
			static_inst = new CafeAESDef();
			return static_inst;
		}
		
		public boolean unevenIOBlocks() {return true;}
		
	}
	
	public static class CafeSectorAESDef implements EncryptionDefinition{

		public static final int DEF_ID = 0xc11ae503;
		public static final String DEFO_ENG_DESC = "Wii Disc AES-128-CBC";
		
		private static CafeAESDef static_inst;
		
		private String desc;
		
		public CafeSectorAESDef(){
			desc = DEFO_ENG_DESC;
		}

		public int getID() {return DEF_ID;}
		public String getDescription() {return desc;}
		public void setDescription(String s) {desc = s;}

		public void setStateValue(int key, int value) {}
		public int getStateValue(int key) {return 0;}
		
		//Wheeeew I need to properly deprecate these
		public boolean decrypt(StreamWrapper input, OutputStream output, List<byte[]> keydata) {return false;}
		public boolean encrypt(StreamWrapper input, OutputStream stream, List<byte[]> keydata) {return false;}
		
		public int[] getExpectedKeydataSizes() {
			return new int[]{16};
		}

		public static CafeAESDef getDefinition(){
			if(static_inst != null) return static_inst;
			static_inst = new CafeAESDef();
			return static_inst;
		}
		
		public boolean unevenIOBlocks() {return true;}
		
	}
	
	public static class CafeCBCDecMethod implements DecryptorMethod{

		private AES aes;
		
		private byte[] zero_iv;
		private byte[] current_iv;
		
		public CafeCBCDecMethod(byte[] key){
			aes = new AES(key);
			//is_blocked = blocked;
			zero_iv = new byte[16];
			current_iv = new byte[16];
		}
		
		public CafeCBCDecMethod(AES engine){
			aes = engine;
			//is_blocked = blocked;
			zero_iv = new byte[16];
			current_iv = new byte[16];
		}
		
		public byte[] decrypt(byte[] input, long offval) {
			if(offval >>> 4 == 0L) return aes.decrypt(zero_iv, input);
			return aes.decrypt(current_iv, input);
		}
		
		public void adjustOffsetBy(long value){
			//TODO
		}
		
		public void setZeroIV(byte[] dat){zero_iv = dat;}
		
		public int getInputBlockSize(){return 0x10;}
		
		public int getOutputBlockSize(){return 0x10;}
		
		public int getPreferredBufferSizeBlocks(){return 0x800;}
		
		public long getOutputBlockOffset(long inputBlockOffset){
			if(inputBlockOffset < 0x400) return 0;
			return inputBlockOffset - 0x400;
		}
		
		public int backbyteCount(){return 16;}
		
		public void putBackbytes(byte[] dat){current_iv = dat;}
		
	}

	public static class CafeSectorCBCDecMethod implements DecryptorMethod{

		private AES aes;
		
		//private long stpos;
		private int st_mod;
		
		public CafeSectorCBCDecMethod(byte[] key){
			aes = new AES(key);
		}
		
		public CafeSectorCBCDecMethod(AES engine){
			aes = engine;
		}
		
		public byte[] decrypt(byte[] input, long offval) {
			long val = offval >>> 16;
			int bmod = (int)(val & 0xf);
			bmod = (bmod + st_mod) & 0xf;
			return decryptBlock(input, aes, bmod);
		}
		
		public void adjustOffsetBy(long value){
			long stpos = value;
			stpos = stpos >>> 16;
			st_mod = (int)(stpos & 0xf);
		}
		
		public int getInputBlockSize(){return 0x10000;}
		
		public int getOutputBlockSize(){return 0xfc00;}
		
		public int getPreferredBufferSizeBlocks(){return 1;}
		
		public long getOutputBlockOffset(long inputBlockOffset){
			if(inputBlockOffset < 0x400) return 0;
			return inputBlockOffset - 0x400;
		}
		
		public int backbyteCount(){return 0;}
		public void putBackbytes(byte[] dat){}
		
	}
	
	public static byte[] decryptBlock(byte[] data, byte[] key, int bmod){
		//Initialize AES
		AES aes = new AES(key);
		return decryptBlock(data, aes, bmod);
	}
	
	public static byte[] decryptBlock(byte[] data, AES aes, int bmod){
		//Decrypt the first 0x400 bytes (hash data - need for IV)
		byte[] hashdat = aes.decrypt(new byte[16], Arrays.copyOf(data, 0x400));
		
		//Get hash offset from mod
		int h0off = 20 * bmod;
		byte[] iv = Arrays.copyOfRange(hashdat, h0off, h0off+16);
		
		return aes.decrypt(iv, Arrays.copyOfRange(data, 0x400, 0x10000));
	}

	public static void initCafeCryptState(NinCryptTable ctbl){
		clearCafeCryptState();
		String tdir = "";
		try{tdir = FileBuffer.getTempDir();}
		catch(IOException x){x.printStackTrace();}
		
		int defid = CafeCrypt.CafeAESDef.DEF_ID;
		if(EncryptionDefinitions.getByID(defid) == null) EncryptionDefinitions.registerDefinition(CafeCrypt.CafeAESDef.getDefinition());
		CafeDecryptor decr = new CafeDecryptor(ctbl, tdir);
		StaticDecryption.setDecryptorState(defid, decr);
		
		defid = CafeCrypt.CafeSectorAESDef.DEF_ID;
		if(EncryptionDefinitions.getByID(defid) == null) EncryptionDefinitions.registerDefinition(CafeCrypt.CafeSectorAESDef.getDefinition());
		CafeSectorDecryptor decrs = new CafeSectorDecryptor(ctbl, tdir);
		StaticDecryption.setDecryptorState(defid, decrs);
	}
	
	public static void clearCafeCryptState(){
		int defid = CafeCrypt.CafeAESDef.DEF_ID;
		StaticDecryptor decr = StaticDecryption.getDecryptorState(defid);
		if(decr != null){
			decr.dispose();
		}
		StaticDecryption.setDecryptorState(defid, null);
		
		defid = CafeCrypt.CafeSectorAESDef.DEF_ID;
		decr = StaticDecryption.getDecryptorState(defid);
		if(decr != null){
			decr.dispose();
		}
		StaticDecryption.setDecryptorState(defid, null);
	}
	
	public static CafeAESDef getStandardAESDef(){
		if(plain_def == null) plain_def = new CafeAESDef();
		return plain_def;
	}
	
	public static CafeSectorAESDef getSectoredAESDef(){
		if(sec_def == null) sec_def = new CafeSectorAESDef();
		return sec_def;
	}
	
}
