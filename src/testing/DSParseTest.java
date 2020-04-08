package testing;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import waffleoRai_Containers.nintendo.NDS;
import waffleoRai_Encryption.nintendo.DSModcrypt;
import waffleoRai_Utils.FileBuffer;

public class DSParseTest {
	
	public static void main(String[] args) {
		
		//Test modcrypt
		String testpath = "E:\\Library\\Games\\Console\\TWL_IRAO_USA.nds";
		
		String testout1 = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\ds_test\\TWL_IRAO_USA_mcr1.bin";
		String testout2 = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\ds_test\\TWL_IRAO_USA_mcr1.bin";
		String testout = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\ds_test\\testmc.bin";
		String tempout = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\ds_test\\tempmc.bin";
		String testdec = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\ds_test\\tdecmc.bin";
		
		try{
			
			NDS ds = NDS.readROM(testpath, 0);
			//ds.getSecureKey();
			
			BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(testout1));
			ds.decryptSecureRegion1(bos);
			bos.close();
			
			//ds.printMeToErr();
			//Calculate digest (lol)
			
			/*DSModcrypt.generateAndPrintTables();
			
			String keystr = "AES-Test-Key-Str";
			String ivstr = "Nonce/InitVector";
			byte[] key = keystr.getBytes();
			byte[] iv = ivstr.getBytes();
			
			String s1 = "Unencrypted-Data";
			String s2 = "TestPadding";
			FileBuffer testdat = new FileBuffer(0x1400);
			testdat.printASCIIToFile(s1);
			for(int i = 0; i < 0x190; i++) testdat.printASCIIToFile(s2);
			System.err.println("Test size: 0x" + Long.toHexString(testdat.getFileSize()));
			
			BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(tempout));
			testdat.writeToStream(bos);
			bos.close();
			
			BufferedInputStream bis = new BufferedInputStream(new FileInputStream(tempout));
			
			
			bos = new BufferedOutputStream(new FileOutputStream(testout));
			DSModcrypt.aes_crypt_ctr(bis, bos, key, iv, 0x1140);
			bos.close();
			bis.close();
			
			//Now decrypt...
			bis = new BufferedInputStream(new FileInputStream(testout));
			bos = new BufferedOutputStream(new FileOutputStream(testdec));
			DSModcrypt.aes_crypt_ctr(bis, bos, key, iv, 0x1140);
			bos.close();
			bis.close();*/
		}
		catch(Exception e){
			e.printStackTrace();
			System.exit(1);
		}
		
	}

}
