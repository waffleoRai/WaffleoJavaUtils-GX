package waffleoRai_Containers.nintendo.cafe;

import waffleoRai_Containers.nintendo.citrus.CitrusNCC;
import waffleoRai_Encryption.AES;

public class TestAES {
	
	private static byte[] strto128(String val){

		byte[] barr = new byte[16];
		for(int i = 0; i < 16; i++){
			int cidx = i << 1;
			String two = val.substring(cidx, cidx+2);
			int b = Integer.parseUnsignedInt(two, 16);
			barr[i] = (byte)b;
		}
		
		return barr;
	}

	public static void main(String[] args) {
		// TODO Auto-generated method stub

		String key_s = "";
		String iv_s = "4a9b66603d1b6446899a1e7fcbbd2bd8";
		String dat_s = "335b7cf7f1f28adfb4855ce69b19376e"; //0x400
		
		try{
			byte[] key = strto128(key_s);
			byte[] iv = strto128(iv_s);
			byte[] dat = strto128(dat_s);
			
			System.err.println("Key: " + CitrusNCC.printHash(key));
			System.err.println("IV: " + CitrusNCC.printHash(iv));
			System.err.println("Input: " + CitrusNCC.printHash(dat));
			
			AES aes = new AES(key);
			byte[] dec = aes.decrypt(iv, dat);
			System.err.println("Output: " + CitrusNCC.printHash(dec));
			
			/*String path = "E:\\Library\\Games\\Console\\WUP_AX5E_USZ.wud";
			long pos = 0xe24b8000L;
			FileBuffer encdat = new FileBuffer(path, pos, pos + 0x8000, true);
			for(int i = 0; i < 0x400; i++){
				iv = encdat.getBytes(i, i+16);
				dec = aes.decrypt(iv, dat);
				System.err.println("Off 0x" + Integer.toHexString(i) + " Output: " + CitrusNCC.printHash(dec));
			}*/
			
		}
		catch(Exception x){
			x.printStackTrace();
			System.exit(1);
		}
		
	}

}
