package waffleoRai_Containers.nintendo.citrus;

import java.util.HashMap;
import java.util.Map;

import waffleoRai_Encryption.AES;
import waffleoRai_Encryption.DecryptorMethod;
import waffleoRai_Encryption.FileCryptRecord;
import waffleoRai_Encryption.StaticDecryptor;
import waffleoRai_Encryption.nintendo.NinCryptTable;
import waffleoRai_Encryption.nintendo.NinCrypto;
import waffleoRai_Files.tree.FileNode;

public class CitrusDecryptor implements StaticDecryptor{
	
	private NinCryptTable crypt_table;
	private Map<byte[], AES> aes_map;
	
	public CitrusDecryptor(NinCryptTable table){
		crypt_table = table;
		aes_map = new HashMap<byte[], AES>();
	}

	public FileNode decrypt(FileNode node) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DecryptorMethod generateDecryptor(FileNode node) {
		String ckey = node.getMetadataValue(NinCrypto.METAKEY_CRYPTGROUPUID);
		
		long cuid = 0L;
		try{cuid = Long.parseUnsignedLong(ckey, 16);}
		catch(NumberFormatException x){x.printStackTrace(); return null;}
		
		FileCryptRecord crec = crypt_table.getRecord(cuid);
		if(crec == null) return null;
		
		//Get key
		byte[] key = crypt_table.getKey(crec.getKeyType(), crec.getKeyIndex());
		
		if(crec.getCryptType() == NinCrypto.CRYPTTYPE_AESCTR){
			//Get AES engine object
			AES engine = aes_map.get(key);
			if(engine == null){
				engine = new AES(key);
				engine.setCTR();
				aes_map.put(key, engine);
			}
			
			//Derive file relative base CTR
			byte[] sec_ctr = crec.getIV();
			long diff = node.getOffset() - crec.getCryptOffset();
			byte[] file_ctr = CitrusCrypt.adjustCTR(sec_ctr, diff);
			
			//Debug
			/*System.err.println("CitrusDecryptor.generateDecryptor || Crypt Record UID: " + Long.toHexString(cuid));
			System.err.println("\tRecord Offset: 0x" + Long.toHexString(crec.getCryptOffset()));
			System.err.println("\tNode Offset: 0x" + Long.toHexString(node.getOffset()));
			System.err.println("\tKey: " + AES.bytes2str(key));
			System.err.println("\tRecord CTR: " + AES.bytes2str(sec_ctr));
			System.err.println("\tNode CTR: " + AES.bytes2str(file_ctr));*/
			
			//Put together
			return new CitrusCrypt.CitrusCTRDecMethod(engine, file_ctr);
		}
		else if(crec.getCryptType() == NinCrypto.CRYPTTYPE_AESCBC){
			//TODO
		}
		
		return null;
	}
	
	public void dispose(){
		//clearTempFiles();
		aes_map.clear();
	}

}
