package waffleoRai_Encryption.nintendo;

import waffleoRai_Encryption.FileCryptRecord;

public class NinCBCCryptRecord extends FileCryptRecord{
	
	private byte[] init_vec;

	public NinCBCCryptRecord(long fileuid) {
		super(fileuid);
	}

	public int getCryptType() {return NinCrypto.CRYPTTYPE_AESCBC;}

	public void setCryptType(int type) {}

	public boolean hasIV() {return true;}

	public byte[] getIV() {return init_vec;}

	public void setIV(byte[] iv) {init_vec = iv;}

}
