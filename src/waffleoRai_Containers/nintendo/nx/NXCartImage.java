package waffleoRai_Containers.nintendo.nx;

public class NXCartImage {
	
	/*----- Constants -----*/
	
	public static final String MAGIC_CARTHEAD = "HEAD";
	
	public static final int SECMODE_T1 = 1;
	public static final int SECMODE_T2 = 2;
	
	public static final int CARTSIZE_1GB = 0xFA;
	public static final int CARTSIZE_2GB = 0xF8;
	public static final int CARTSIZE_4GB = 0xF0;
	public static final int CARTSIZE_8GB = 0xE0;
	public static final int CARTSIZE_16GB = 0xE1;
	public static final int CARTSIZE_32GB = 0xE2;
	
	/*----- Instance Variables -----*/
	
	private String src_path;
	private String dec_dir;
	
	private byte[] rsa_sig; //For cart HEAD
	
	private long secure_addr;
	private long backup_addr;
	
	private int idx_tkd; //TitleKeyDec Index
	private int idx_kek;
	
	private int cart_size; //Enum
	private int header_ver;
	private int cart_flags;
	
	private long packageID;
	private long data_end_addr; //Valid data end address
	private long normal_end_addr;
	private byte[] gc_info_iv;
	
	private long hfs0_off;
	private long hfs0_headsize;
	
	private byte[] sha_hfshead;
	private byte[] sha_initdat;
	
	private int security_mode; //Enum
	private int idx_t1k; //T1 Key index
	private int idx_key; //Key Index
	
	//GC Info (Encrypted) -- Add fields if I feel like it
	
	/*----- Construction/Parsing -----*/
	
	/*----- Getters -----*/
	
	/*----- Setters -----*/
	
	/*----- Debug -----*/

}
