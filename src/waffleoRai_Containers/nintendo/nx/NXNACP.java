package waffleoRai_Containers.nintendo.nx;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import waffleoRai_Encryption.AES;
import waffleoRai_Files.GenericSystemDef;
import waffleoRai_Utils.FileBuffer;

public class NXNACP {
	
	/*----- Constants -----*/
	
	public static final String STR_ENCODING = "UTF8";
	
	/*----- Instance Variables -----*/
	
	private String[] titles;
	private String[] publishers;
	
	private byte[] isbn;
	private byte startup_user_acct;
	private byte usr_acct_switch_lock;
	private byte addon_ctnt_reg_type;
	
	private int attr_flag;
	private int lan_flag;
	private int parental_control_flag;
	
	private byte screenshot;
	private byte vidcap;
	private byte dataloss;
	private byte playlog;
	
	private long presence_group_id;
	private byte[] age_ratings;
	private String disp_version;
	private long addon_ctnt_base_id;
	
	private long savedata_owner_id;
	private long usracct_savedata_size;
	private long usracct_journal_savedata_size;
	private long device_savedata_size;
	private long device_journal_savedata_size;
	
	private long bcat_del_cache_size;
	private long app_error_code_cat;
	private String app_error_code_cat_str;
	private long[] local_comm_id;
	
	private byte logo_type;
	private byte logo_handling;
	private byte runtime_addon_content_install;
	private byte runtime_param_delivery;
	private byte crash_report;
	private byte hdcp;
	private long seed_pseudo_device_id;
	private byte[] bcat_passphrase;
	
	private byte startup_usr_acct_op;
	private long usracct_savedata_size_max;
	private long usracct_journal_savedata_size_max;
	private long device_savedata_size_max;
	private long device_journal_savedata_size_max;
	
	private long temp_storage_size;
	private long cache_storage_size;
	private long cache_storage_journal_size;
	private long cache_storage_data_journal_size;
	private short cache_storage_idx_max;
	
	private byte[][] playlog_query_appid;
	private byte playlog_query_capability;
	private byte repairflag;
	private byte program_idx;
	private byte rnsll_flag;
	
	private byte[] ndc_config; //It's a struct, but I'm not gonna bother with it for now
	private long jit_flags;
	private long jit_memsize;
	
	/*----- Construction/Parsing -----*/
	
	private NXNACP(){}
	
	public static NXNACP readNACP(FileBuffer data, long stpos){
		if(data == null) return null;
		
		NXNACP nacp = new NXNACP();
		data.setEndian(false);
		long cpos = stpos;
		
		nacp.titles = new String[16];
		nacp.publishers = new String[16];
		for(int i = 0; i < 16; i++){
			nacp.titles[i] = data.readEncoded_string(STR_ENCODING, cpos, cpos + 0x200); cpos += 0x200;
			nacp.publishers[i] = data.readEncoded_string(STR_ENCODING, cpos, cpos + 0x100); cpos += 0x100;
		}
		
		data.setCurrentPosition(cpos);
		nacp.isbn = new byte[0x25];
		for(int i = 0; i < 0x25; i++) nacp.isbn[i] = data.nextByte();
		nacp.startup_user_acct = data.nextByte();
		nacp.usr_acct_switch_lock = data.nextByte();
		nacp.addon_ctnt_reg_type = data.nextByte();
		nacp.attr_flag = data.nextInt();
		nacp.lan_flag = data.nextInt();
		nacp.parental_control_flag = data.nextInt();
		nacp.screenshot = data.nextByte();
		nacp.vidcap = data.nextByte();
		nacp.dataloss = data.nextByte();
		nacp.playlog = data.nextByte();
		nacp.presence_group_id = data.nextLong();
		nacp.age_ratings = new byte[0x20];
		for(int i = 0; i < 0x20; i++) nacp.age_ratings[i] = data.nextByte();
		cpos = data.getCurrentPosition();
		nacp.disp_version = data.readEncoded_string(STR_ENCODING, cpos, cpos+0x10);
		data.skipBytes(16);
		nacp.addon_ctnt_base_id = data.nextLong();
		nacp.savedata_owner_id = data.nextLong();
		nacp.usracct_savedata_size = data.nextLong();
		nacp.usracct_journal_savedata_size = data.nextLong();
		nacp.device_savedata_size = data.nextLong();
		nacp.device_journal_savedata_size = data.nextLong();
		nacp.bcat_del_cache_size = data.nextLong();
		cpos = data.getCurrentPosition();
		nacp.app_error_code_cat_str = data.readEncoded_string(STR_ENCODING, cpos, cpos+8);
		nacp.app_error_code_cat = data.nextLong();
		nacp.local_comm_id = new long[8];
		for(int i = 0; i < 8; i++) nacp.local_comm_id[i] = data.nextLong();
		nacp.logo_type = data.nextByte();
		nacp.logo_handling = data.nextByte();
		nacp.runtime_addon_content_install = data.nextByte();
		nacp.runtime_param_delivery = data.nextByte();
		data.skipBytes(2);
		nacp.crash_report = data.nextByte();
		nacp.hdcp = data.nextByte();
		nacp.seed_pseudo_device_id = data.nextLong();
		nacp.bcat_passphrase = new byte[0x41];
		for(int i = 0; i < 0x41; i++) nacp.bcat_passphrase[i] = data.nextByte();
		nacp.startup_usr_acct_op = data.nextByte();
		data.skipBytes(6);
		nacp.usracct_savedata_size_max = data.nextLong();
		nacp.usracct_journal_savedata_size_max = data.nextLong();
		nacp.device_savedata_size_max = data.nextLong();
		nacp.device_journal_savedata_size_max = data.nextLong();
		nacp.temp_storage_size = data.nextLong();
		nacp.cache_storage_size = data.nextLong();
		nacp.cache_storage_journal_size = data.nextLong();
		nacp.cache_storage_data_journal_size = data.nextLong();
		nacp.cache_storage_idx_max = data.nextShort();
		data.skipBytes(6);
		nacp.playlog_query_appid = new byte[8][16];
		for(int i = 0; i < 8; i++){
			for(int j = 0; j < 16; j++){
				nacp.playlog_query_appid[i][j] = data.nextByte();
			}
		}
		nacp.playlog_query_capability = data.nextByte();
		nacp.repairflag = data.nextByte();
		nacp.program_idx = data.nextByte();
		nacp.rnsll_flag = data.nextByte();
		data.skipBytes(4);
		nacp.ndc_config = new byte[0x198];
		for(int i = 0; i < 0x198; i++) nacp.ndc_config[i] = data.nextByte();
		nacp.jit_flags = data.nextLong();
		nacp.jit_memsize = data.nextLong();
		
		return nacp;
	}
	
	/*----- Getters -----*/
	
	public String getTitleString(int lanidx){
		if(titles == null) return null;
		if(lanidx < 0 || lanidx >= titles.length) return null;
		return titles[lanidx];
	}
	
	public String getPublisherString(int lanidx){
		if(publishers == null) return null;
		if(lanidx < 0 || lanidx >= publishers.length) return null;
		return publishers[lanidx];
	}
	
	public String getVersionString(){
		return disp_version;
	}
	
	public String getAppErrorString(){
		return app_error_code_cat_str;
	}
	
	/*----- Setters -----*/
	
	/*----- Definition -----*/
	
	private static NXNACPDef staticdef;
	
	public static NXNACPDef getDefinition(){
		if(staticdef == null) staticdef = new NXNACPDef();
		return staticdef;
	}
	
	public static class NXNACPDef extends GenericSystemDef{
		
		private static String DEFO_ENG_DESC = "Nintendo Switch Title Info";
		public static int TYPE_ID = 0x6e616370;
		
		public NXNACPDef(){
			super(DEFO_ENG_DESC, TYPE_ID);
		}
		
		public Collection<String> getExtensions() {
			List<String> slist = new LinkedList<String>();
			slist.add("nacp");
			return slist;
		}
		
		public String getDefaultExtension(){return "nacp";}
		
	}
	
	
	/*----- Debug -----*/

	public void printMeTo(Writer out) throws IOException{

		out.write("----- BANNERS -----\n");
		for(int i = 0; i < 15; i++){
			out.write("\n");
			out.write("->" + NXUtils.LANNAMES_ENG[i] + "\n");
			out.write(titles[i] + "\n");
			out.write(publishers[i] + "\n");
		}
		
		out.write("\nISBN: " + AES.bytes2str(isbn) + "\n");
		out.write("Startup User Account: " + startup_user_acct + "\n");
		out.write("User Account Switch Lock: " + usr_acct_switch_lock + "\n");
		out.write("Add-On Content Registration Type: " + addon_ctnt_reg_type + "\n");
		out.write("Attribute Flag: " + String.format("%08x", this.attr_flag) + "\n");
		out.write("Supported Language Flag: " + String.format("%08x", lan_flag) + "\n");
		out.write("Parental Control Flag: " + String.format("%08x", parental_control_flag) + "\n");
		out.write("Screenshots Enabled: " + (screenshot != 0) + "\n");
		out.write("Video Capture Enabled: ");
		switch(this.vidcap){
		case 0: out.write("No\n"); break;
		case 1: out.write("Yes\n"); break;
		case 2: out.write("Auto\n"); break;
		}
		out.write("Data Loss Confirmation: " + dataloss + "\n");
		out.write("Play Log Policy: " + playlog + "\n");
		out.write("Presence Group ID: " + Long.toHexString(this.presence_group_id) + "\n");
		out.write("Age Rating Array: " + AES.bytes2str(age_ratings) + "\n");
		out.write("Display Version: " + this.disp_version + "\n");
		out.write("Add-On Content Base ID: " + Long.toHexString(addon_ctnt_base_id) + "\n");
		out.write("Savedata Owner ID: " + Long.toHexString(savedata_owner_id) + "\n");
		out.write("User Account Savedata Size: 0x" + Long.toHexString(usracct_savedata_size) + "\n");
		out.write("User Account Savedata Journal Size: 0x" + Long.toHexString(usracct_journal_savedata_size) + "\n");
		out.write("Device Savedata Size: 0x" + Long.toHexString(device_savedata_size) + "\n");
		out.write("Device Savedata Journal Size: 0x" + Long.toHexString(device_journal_savedata_size) + "\n");
		out.write("Bcat Delivery Cache Storage Size: 0x" + Long.toHexString(bcat_del_cache_size) + "\n");
		out.write("Application Error Code Category: " + Long.toHexString(app_error_code_cat) + "\n");
		out.write("Application Error Code Category (String): " + this.app_error_code_cat_str + "\n");
		out.write("Local Communication ID: \n");
		for(int i = 0; i < 8; i++){
			out.write("\t" + Long.toHexString(this.local_comm_id[i]) + "\n");
		}
		out.write("Logo Type: " + logo_type + "\n");
		out.write("Logo Handling: " + logo_handling + "\n");
		out.write("Runtime Add-On Content Install: " + runtime_addon_content_install + "\n");
		out.write("Runtime Parameter Delivery: " + runtime_param_delivery + "\n");
		out.write("Crash Report: " + crash_report + "\n");
		out.write("Hdcp: " + hdcp + "\n");
		out.write("Seed for Pseudo Device ID: " + Long.toHexString(seed_pseudo_device_id) + "\n");
		out.write("Bcat Passphrase: " + AES.bytes2str(bcat_passphrase) + "\n");
		out.write("Startup User Account Option: " + startup_usr_acct_op + "\n");
		out.write("User Account Savedata Max Size: 0x" + Long.toHexString(usracct_savedata_size_max) + "\n");
		out.write("User Account Savedata Journal Max Size: 0x" + Long.toHexString(usracct_journal_savedata_size_max) + "\n");
		out.write("Device Savedata Max Size: 0x" + Long.toHexString(device_savedata_size_max) + "\n");
		out.write("Device Savedata Journal Max Size: 0x" + Long.toHexString(device_journal_savedata_size_max) + "\n");
		out.write("Temporary Storage Size: 0x" + Long.toHexString(temp_storage_size) + "\n");
		out.write("Cache Storage Size: 0x" + Long.toHexString(cache_storage_size) + "\n");
		out.write("Cache Storage Journal Size: 0x" + Long.toHexString(cache_storage_journal_size) + "\n");
		out.write("Cache Storage Data and Journal Size Max: 0x" + Long.toHexString(cache_storage_data_journal_size) + "\n");
		out.write("Cache Storage Index Max: 0x" + Long.toHexString(cache_storage_idx_max) + "\n");
		
		out.write("Play Log Query Application ID: \n");
		for(int i = 0; i < 8; i++){
			out.write("\t" + AES.bytes2str(playlog_query_appid[i]) + "\n");
		}
		out.write("Play Log Query Capability: " + playlog_query_capability + "\n");
		out.write("Repair Flag: " + repairflag + "\n");
		out.write("Program Index: " + program_idx + "\n");
		out.write("Required Network Service License On Launch Flag: " + rnsll_flag + "\n");
		
		out.write("JIT Flags: " + Long.toHexString(jit_flags) + "\n");
		out.write("JIT Memory Size: 0x" + Long.toHexString(jit_memsize) + "\n");
	}
	
}
