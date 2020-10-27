package waffleoRai_fdefs.nintendo;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import waffleoRai_Containers.nintendo.wiidisc.WiiCrypt;
import waffleoRai_Containers.nintendo.wiidisc.WiiDataCluster;
import waffleoRai_Encryption.AES;
import waffleoRai_Files.EncryptionDefinition;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.StreamWrapper;

public class WiiAESDef implements EncryptionDefinition{
	
	public static final int DEF_ID = 0xc11ae500;
	public static final String DEFO_ENG_DESC = "Wii Disc AES-128-CBC";
	
	private static WiiAESDef static_inst;
	
	private String desc;
	
	public WiiAESDef(){
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
		
		while(!input.isEmpty()){
			FileBuffer buff = new FileBuffer(0x8000, true);
			
			for(int j = 0; j < 0x8000; j++){
				if(!input.isEmpty()) buff.addToFile(input.get());
				else buff.addToFile(FileBuffer.ZERO_BYTE);
			}
			
			WiiDataCluster block = new WiiDataCluster();
			block.readCluster(buff, 0, aes);
			
			try {
				output.write(block.getDecryptedData());
			} 
			catch (IOException e) {
				e.printStackTrace();
				return false;
			}
		}
		
		return true;
	}

	public boolean encrypt(StreamWrapper input, OutputStream stream, List<byte[]> keydata) {
		if(keydata == null || keydata.size() < 1) return false;
		AES aes = new AES(keydata.get(0));
		WiiCrypt crypto = new WiiCrypt(aes);
		
		try{
			FileBuffer h3 = crypto.encryptPartitionData(input, stream);
			if(h3 != null) return true;
		}
		catch(IOException x){
			x.printStackTrace();
			return false;
		}
		
		return false;
	}

	public int[] getExpectedKeydataSizes() {
		return new int[]{16};
	}

	public static WiiAESDef getDefinition(){
		if(static_inst != null) return static_inst;
		static_inst = new WiiAESDef();
		return static_inst;
	}
	
	public boolean unevenIOBlocks() {return true;}
	
}
