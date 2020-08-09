package waffleoRai_Encryption.nintendo;

import waffleoRai_Encryption.FileCryptRecord;

public class NinCTRCryptRecord extends FileCryptRecord{
	
	private byte[] ctr;

	public NinCTRCryptRecord(long fileuid) {
		super(fileuid);
	}

	public int getCryptType() {return NinCrypto.CRYPTTYPE_AESCTR;}

	public void setCryptType(int type) {}

	public boolean hasIV() {return true;}

	public byte[] getIV() {return ctr;}

	public void setIV(byte[] iv) {ctr = iv;}

}
