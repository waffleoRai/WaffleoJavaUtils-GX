package waffleoRai_fdefs.nintendo;

import java.io.OutputStream;
import java.util.List;

import waffleoRai_Containers.nintendo.nx.NXCrypt;
import waffleoRai_Encryption.AES;
import waffleoRai_Files.EncryptionDefinition;
import waffleoRai_Files.GenericSystemDef;
import waffleoRai_Utils.StreamWrapper;

public class NXSysDefs {
	
	private static NXNCAHeaderDef ncah_def;
	private static NXRomFSHeaderDef romfsh_def;
	private static NXRomFSTDef romfst_def;
	
	private static NXPFSHeaderDef pfsh_def;
	private static NXHFSHeaderDef hfsh_def;
	private static NXXCIHeaderDef xcih_def;
	
	private static NXAESCTRDef aesctr_def;
	private static NXAESXTSDef aesxts_def;
	
	public static NXNCAHeaderDef getNCAHeaderDef(){
		if(ncah_def == null) ncah_def = new NXNCAHeaderDef();
		return ncah_def;
	}
	
	public static NXRomFSHeaderDef getRomFSHeaderDef(){
		if(romfsh_def == null) romfsh_def = new NXRomFSHeaderDef();
		return romfsh_def;
	}
	
	public static NXRomFSTDef getRomFSTableDef(){
		if(romfst_def == null) romfst_def = new NXRomFSTDef();
		return romfst_def;
	}
	
	public static NXPFSHeaderDef getPFSHeaderDef(){
		if(pfsh_def == null) pfsh_def = new NXPFSHeaderDef();
		return pfsh_def;
	}
	
	public static NXHFSHeaderDef getHFSHeaderDef(){
		if(hfsh_def == null) hfsh_def = new NXHFSHeaderDef();
		return hfsh_def;
	}
	
	public static NXXCIHeaderDef getXCIHeaderDef(){
		if(xcih_def == null) xcih_def = new NXXCIHeaderDef();
		return xcih_def;
	}

	public static NXAESCTRDef getCTRCryptoDef(){
		if(aesctr_def == null) aesctr_def = new NXAESCTRDef();
		return aesctr_def;
	}
	
	public static NXAESXTSDef getXTSCryptoDef(){
		if(aesxts_def == null) aesxts_def = new NXAESXTSDef();
		return aesxts_def;
	}

	public static class NXNCAHeaderDef extends GenericSystemDef{
		
		private static String DEFO_ENG_DESC = "Nintendo Switch NCA Header";
		public static int TYPE_ID = 0x4e434168;
		
		public NXNCAHeaderDef(){
			super(DEFO_ENG_DESC, TYPE_ID);
		}
		
	}
	
	public static class NXRomFSHeaderDef extends GenericSystemDef{
		
		private static String DEFO_ENG_DESC = "Nintendo Switch RomFS Header";
		public static int TYPE_ID = 0x6e78524d;
		
		public NXRomFSHeaderDef(){
			super(DEFO_ENG_DESC, TYPE_ID);
		}
		
	}
	
	public static class NXRomFSTDef extends GenericSystemDef{
		
		private static String DEFO_ENG_DESC = "Nintendo Switch RomFS File System Table";
		public static int TYPE_ID = 0x58526673;
		
		public NXRomFSTDef(){
			super(DEFO_ENG_DESC, TYPE_ID);
		}
		
	}
	
	public static class NXPFSHeaderDef extends GenericSystemDef{
		
		private static String DEFO_ENG_DESC = "Nintendo Switch Partition File System Header";
		public static int TYPE_ID = 0x50465330;
		
		public NXPFSHeaderDef(){
			super(DEFO_ENG_DESC, TYPE_ID);
		}
		
	}
	
	public static class NXHFSHeaderDef extends GenericSystemDef{
		
		private static String DEFO_ENG_DESC = "Nintendo Switch HFS Header";
		public static int TYPE_ID = 0x48465330;
		
		public NXHFSHeaderDef(){
			super(DEFO_ENG_DESC, TYPE_ID);
		}
		
	}
	
	public static class NXXCIHeaderDef extends GenericSystemDef{
		
		private static String DEFO_ENG_DESC = "NX Cartridge Image Header";
		public static int TYPE_ID = 0x6e784354;
		
		public NXXCIHeaderDef(){
			super(DEFO_ENG_DESC, TYPE_ID);
		}
		
	}
	
	public static class NXAESCTRDef implements EncryptionDefinition{
		
		public static final int DEF_ID = 0xcc857293;
		public static final String DEFO_ENG_DESC = "NX AES-CTR";
		
		private String desc;
		
		public NXAESCTRDef(){
			desc = DEFO_ENG_DESC;
		}

		public int getID() {return DEF_ID;}
		public String getDescription() {return desc;}
		public void setDescription(String s) {desc = s;}

		public void setStateValue(int key, int value) {}
		public int getStateValue(int key) {return 0;}

		public boolean decrypt(StreamWrapper input, OutputStream output, List<byte[]> keydata) {
			if(keydata == null || keydata.size() < 1) return false;
			AES aes = new AES(keydata.get(0));
			aes.setCTR();
			aes.initDecrypt(keydata.get(1));
			
			while(!input.isEmpty()){
				byte[] enc = new byte[16];
				for(int i = 0; i < 16; i++){
					if(input.isEmpty()) break;
					enc[i] = input.get();
				}
				
				byte[] dec = aes.decryptBlock(enc, input.isEmpty());
				try{output.write(dec);}
				catch(Exception x){x.printStackTrace(); return false;}
			}
			
			return true;
		}

		public boolean encrypt(StreamWrapper input, OutputStream stream, List<byte[]> keydata) {
			if(keydata == null || keydata.size() < 1) return false;
			AES aes = new AES(keydata.get(0));
			aes.setCTR();
			aes.initEncrypt(keydata.get(1));
			
			while(!input.isEmpty()){
				byte[] pt = new byte[16];
				for(int i = 0; i < 16; i++){
					if(input.isEmpty()) break;
					pt[i] = input.get();
				}
				
				byte[] enc = aes.encryptBlock(pt, input.isEmpty());
				try{stream.write(enc);}
				catch(Exception x){x.printStackTrace(); return false;}
			}
			
			return true;
		}

		public int[] getExpectedKeydataSizes() {
			return new int[]{16};
		}
		
	}
	
	public static class NXAESXTSDef implements EncryptionDefinition{
		
		public static final int DEF_ID = 0x823bad90;
		public static final String DEFO_ENG_DESC = "NX XTS-AES";
		
		private String desc;
		
		public NXAESXTSDef(){
			desc = DEFO_ENG_DESC;
		}

		public int getID() {return DEF_ID;}
		public String getDescription() {return desc;}
		public void setDescription(String s) {desc = s;}

		public void setStateValue(int key, int value) {}
		public int getStateValue(int key) {return 0;}

		public boolean decrypt(StreamWrapper input, OutputStream output, List<byte[]> keydata) {
			if(keydata == null || keydata.size() < 1) return false;
			byte[] key = keydata.get(0);
			byte[] sraw = keydata.get(1);
			long sec = 0L;
			int shamt = 56;
			for(int i = 0; i < 8; i++){
				sec |= Byte.toUnsignedLong(sraw[i]) << shamt;
				shamt -= 8;
			}
			while(!input.isEmpty()){
				byte[] enc = new byte[0x200];
				for(int i = 0; i < 0x200; i++){
					if(input.isEmpty()) break;
					enc[i] = input.get();
				}
				
				byte[] dec = NXCrypt.decrypt_AESXTS_sector(key, enc, sec);
				try{output.write(dec);}
				catch(Exception x){x.printStackTrace(); return false;}
			}
			
			return true;
		}

		public boolean encrypt(StreamWrapper input, OutputStream stream, List<byte[]> keydata) {
			//TODO
			//Too lazy
			return false;
		}

		public int[] getExpectedKeydataSizes() {
			return new int[]{32, 8};
		}
		
	}
	
}
