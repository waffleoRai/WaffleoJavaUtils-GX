package waffleoRai_Containers.nintendo.citrus;

import java.io.IOException;

import waffleoRai_Encryption.StaticDecryption;
import waffleoRai_Encryption.nintendo.NinCryptTable;
import waffleoRai_Files.EncryptionDefinitions;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_fdefs.nintendo.CitrusAESCTRDef;

public class CitrusUtil {
	
	private static String dec_temp_dir;
	
	public static void setActiveCryptTable(NinCryptTable ctbl){
		clearActiveCryptTable();
		CitrusDecryptor decer = new CitrusDecryptor(ctbl);
		
		int ctr_id = CitrusAESCTRDef.getDefinition().getID();
		StaticDecryption.setDecryptorState(ctr_id, decer);
		
		//Register defs statically, if not done...
		if(EncryptionDefinitions.getByID(ctr_id) == null){
			EncryptionDefinitions.registerDefinition(CitrusAESCTRDef.getDefinition());
		}
	}
	
	public static void clearActiveCryptTable(){
		StaticDecryption.setDecryptorState(CitrusAESCTRDef.getDefinition().getID(), null);
	}
	
	public static String getDecryptTempDir(){
		if(dec_temp_dir == null){
			try{dec_temp_dir = FileBuffer.getTempDir();}
			catch(IOException x){x.printStackTrace();}
		}
		return dec_temp_dir;
	}
	
	public static void setDecryptTempDir(String dirpath){
		dec_temp_dir = dirpath;
	}


}
