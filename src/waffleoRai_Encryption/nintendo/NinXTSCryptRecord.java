package waffleoRai_Encryption.nintendo;

import waffleoRai_Encryption.FileCryptRecord;

public class NinXTSCryptRecord extends FileCryptRecord{
	
	private long sector; //For tweak

	public NinXTSCryptRecord(long fileuid) {
		super(fileuid);
	}

	public int getCryptType() {return NinCrypto.CRYPTTYPE_AESXTS;}

	public void setCryptType(int type) {}

	public boolean hasIV() {return true;}

	public byte[] getIV() {
		byte[] bytes = new byte[8];
		int shamt = 56;
		for(int i = 0; i < 8; i++){
			bytes[i] = (byte)((sector >>> shamt) & 0xFF);
			shamt -= 8;
		}
		
		return bytes;
	}

	public void setIV(byte[] iv) {
		sector = 0L;
		for(int i = 0; i < 8; i++){
			sector |= Byte.toUnsignedLong(iv[i]);
			sector = sector << 8;
		}
	}
	
	public long getSector(){return sector;}
	public void setSector(long s){sector = s;}


}
