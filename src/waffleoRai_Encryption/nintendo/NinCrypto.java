package waffleoRai_Encryption.nintendo;

public class NinCrypto {
	
	public static final String METAKEY_FILEUID = "fileGUID";
	public static final String METAKEY_CRYPTGROUPUID = "cryptgrpGUID";
	
	public static final int KEYTYPE_128 = 0;
	public static final int KEYTYPE_192 = 1;
	public static final int KEYTYPE_256 = 2;

	public static final int CRYPTTYPE_NONE = 0;
	public static final int CRYPTTYPE_AESCBC = 1;
	public static final int CRYPTTYPE_AESECM = 2;
	public static final int CRYPTTYPE_AESCTR = 3;
	public static final int CRYPTTYPE_AESXTS = 4;
	public static final int CRYPTTYPE_DSBLOWFISH = 5;
	public static final int CRYPTTYPE_TWLMODCRYPT = 6;
	
}
