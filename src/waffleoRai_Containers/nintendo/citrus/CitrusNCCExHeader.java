package waffleoRai_Containers.nintendo.citrus;

import java.util.ArrayList;
import java.util.List;

import waffleoRai_Utils.FileBuffer;

public class CitrusNCCExHeader {

	//https://www.3dbrew.org/wiki/NCCH/Extended_Header
	
	/*----- Constants -----*/
	
	public static final int STRUCT_SIZE = 0x800;
	
	/*----- Instance Variables -----*/
	
	//---SCI (System Control Info)
	private String app_title;
	
	private boolean compress_code;
	private boolean sd_app;
	
	private int remaster_ver;
	
	private long txt_codeset_addr;
	private int txt_codeset_phys_pages;
	private long txt_codeset_size;
	
	private long ro_codeset_addr;
	private int ro_codeset_phys_pages;
	private long ro_codeset_size;
	
	private long dat_codeset_addr;
	private int dat_codeset_phys_pages;
	private long dat_codeset_size;
	
	private long stack_size;
	private long bss_size;
	
	private long[] dep_list;
	
	private long save_size;
	private long jump_id;
	
	//--ACI (Access Control Info)
	
	//Other
	private byte[] access_desc_sig;
	private byte[] hdr_rsa_key;
	
	/*----- Construction/Parsing -----*/
	
	private CitrusNCCExHeader(){}
	
	public static CitrusNCCExHeader readExtendedHeader(FileBuffer data, long stpos){
		data.setEndian(false);
		data.setCurrentPosition(stpos+13); //Skip title and reserved after
		
		CitrusNCCExHeader exhead = new CitrusNCCExHeader();
		exhead.app_title = data.getASCII_string(stpos, 8);
		int flag = Byte.toUnsignedInt(data.nextByte());
		exhead.compress_code = (flag & 0x1) != 0;
		exhead.sd_app = (flag & 0x2) != 0;
		exhead.remaster_ver = Short.toUnsignedInt(data.nextShort());
		
		exhead.txt_codeset_addr = Integer.toUnsignedLong(data.nextInt());
		exhead.txt_codeset_phys_pages = data.nextInt();
		exhead.txt_codeset_size = Integer.toUnsignedLong(data.nextInt());
		exhead.stack_size = Integer.toUnsignedLong(data.nextInt());
		exhead.ro_codeset_addr = Integer.toUnsignedLong(data.nextInt());
		exhead.ro_codeset_phys_pages = data.nextInt();
		exhead.ro_codeset_size = Integer.toUnsignedLong(data.nextInt());
		data.skipBytes(4);
		exhead.dat_codeset_addr = Integer.toUnsignedLong(data.nextInt());
		exhead.dat_codeset_phys_pages = data.nextInt();
		exhead.dat_codeset_size = Integer.toUnsignedLong(data.nextInt());
		exhead.bss_size = Integer.toUnsignedLong(data.nextInt());
		
		exhead.dep_list = new long[48];
		for(int i = 0; i < 48; i++) exhead.dep_list[i] = data.nextLong();
		
		exhead.save_size = data.nextLong();
		exhead.jump_id = data.nextLong();
		
		//Skip ACI for now
		data.skipBytes(0x200);
		
		//Return if the accessdesc is not included.
		if(data.getCurrentPosition() >= data.getFileSize()){
			//This is a 0x400 ex header. Return.
			return exhead;
		}
		
		//Copy AccessDesc Sig
		/*exhead.access_desc_sig = new byte[0x100];
		for(int i = 0; i < 0x100; i++) exhead.access_desc_sig[i] = data.nextByte();
		
		//Copy RSA Key
		exhead.hdr_rsa_key = new byte[0x100];
		for(int i = 0; i < 0x100; i++) exhead.hdr_rsa_key[i] = data.nextByte();*/
		
		return exhead;
	}
	
	/*----- Getters -----*/
	
	//SCI
	public String getAppTitle(){return this.app_title;}
	public boolean getCodeCompressedFlag(){return this.compress_code;}
	public boolean getSDAppFlag(){return this.sd_app;}
	public int getRemasterVerion(){return this.remaster_ver;}
	
	public long getCodesetAddress_text(){return this.txt_codeset_addr;}
	public int getCodesetPages_text(){return this.txt_codeset_phys_pages;}
	public long getCodesetSize_text(){return this.txt_codeset_size;}
	public long getCodesetAddress_readonly(){return this.ro_codeset_addr;}
	public int getCodesetPages_readonly(){return this.ro_codeset_phys_pages;}
	public long getCodesetSize_readonly(){return this.ro_codeset_size;}
	public long getCodesetAddress_data(){return this.dat_codeset_addr;}
	public int getCodesetPages_data(){return this.dat_codeset_phys_pages;}
	public long getCodesetSize_data(){return this.dat_codeset_size;}
	
	public long getStackSize(){return this.stack_size;}
	public long getBSSSize(){return this.bss_size;}
	public long getSaveSize(){return this.save_size;}
	public long getJumpID(){return this.jump_id;}
	
	public List<Long> getDependencyIDs(){
		List<Long> list = new ArrayList<Long>(dep_list.length);
		for(int i = 0; i < dep_list.length; i++){
			if(dep_list[i] != 0) list.add(dep_list[i]);
			else break;
		}
	
		return list;
	}
	
	//Misc
	
	public byte[] getAccessDescSig(){return this.access_desc_sig;}
	public byte[] getPublicRSAKey(){return this.hdr_rsa_key;}
	
	/*----- Setters -----*/
	
	public void setAccessDescSig(byte[] sig){this.access_desc_sig = sig;}
	public void setPublicRSAKey(byte[] key){this.hdr_rsa_key = key;}

}
