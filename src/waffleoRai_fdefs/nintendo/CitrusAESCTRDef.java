package waffleoRai_fdefs.nintendo;

import java.io.OutputStream;
import java.util.List;

import waffleoRai_Files.EncryptionDefinition;
import waffleoRai_Utils.StreamWrapper;

public class CitrusAESCTRDef implements EncryptionDefinition{
	
	public static final int DEF_ID = 0xcc857294;
	public static final String DEFO_ENG_DESC = "3DS AES-128-CTR";
	
	private static CitrusAESCTRDef static_inst;
	
	private String desc;
	
	public CitrusAESCTRDef(){
		desc = DEFO_ENG_DESC;
	}

	public int getID() {return DEF_ID;}
	public String getDescription() {return desc;}
	public void setDescription(String s) {desc = s;}

	public void setStateValue(int key, int value) {}
	public int getStateValue(int key) {return 0;}

	public boolean decrypt(StreamWrapper input, OutputStream output, List<byte[]> keydata) {
		//TODO
		//Eh do later.
		//None of that keygen, assumes you've already derived it
		return false;
	}

	public boolean encrypt(StreamWrapper input, OutputStream stream, List<byte[]> keydata) {
		//TODO
		//Eh do later.
		return false;
	}

	public int[] getExpectedKeydataSizes() {
		return new int[]{16, 16}; //Key, IV
	}

	public static CitrusAESCTRDef getDefinition(){
		if(static_inst != null) return static_inst;
		static_inst = new CitrusAESCTRDef();
		return static_inst;
	}

}
