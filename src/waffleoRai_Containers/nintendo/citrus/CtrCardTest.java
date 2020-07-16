package waffleoRai_Containers.nintendo.citrus;

import java.security.MessageDigest;

import waffleoRai_Utils.FileBuffer;

public class CtrCardTest {

	public static void main(String[] args) {

		String testdir = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\3ds_test";
		String keypath = testdir + "\\ctr_common9.bin";
		String boot9_path = testdir + "\\boot9.bin";
		String otp_path = testdir + "\\otp.mem";
		String inpath = "E:\\Library\\Games\\Console\\CTR_AJRE_USA.3ds";
		String buffdir = testdir + "\\AJRE";
		String icotest1 = testdir + "\\icoenc.bin";
		String icotest2 = testdir + "\\icodec.bin";
		
		String exefstest1 = testdir + "\\exefsenc.bin";
		String exefstest2 = testdir + "\\exefsdec.bin";
		
		String exefs_buff = testdir + "\\AJRE\\exefs_dec.tmp";
		
		String keypath25 = testdir + "\\ctr_25X.bin";
		
		try{
			
			//Try to generate the keyset...
			//FileBuffer boot9 = FileBuffer.createBuffer(boot9_path, false);
			//CitrusCrypt crypto = CitrusCrypt.initFromBoot9(boot9);
			//crypto.printToStdErr();
			//crypto.saveCitrusCrypt(keypath);
			
			/*FileBuffer otpenc = FileBuffer.createBuffer(otp_path, false);
			FileBuffer otpdec = crypto.decryptOTP(boot9, otpenc, false);
			otpdec.writeFile(testdir + "\\otpdectest.bin");*/
			
			//--------------------------------------
			
			CitrusCrypt crypto = CitrusCrypt.loadCitrusCrypt(FileBuffer.createBuffer(keypath, false));
			byte[] keyx25 = FileBuffer.createBuffer(keypath25, false).getBytes();
			crypto.setKeyX(0x25, keyx25);
			//System.err.println("KeyX 0x25: " + CitrusNCC.printHash(keyx25));
			//System.err.println("hi");
			
			CitrusNCSD ncsd = CitrusNCSD.readNCSD(FileBuffer.createBuffer(inpath), 0x0, true);
			ncsd.printToStdErr();
			
			//Alright, let's see what happens...
			ncsd.generateDecryptionBuffers(buffdir, crypto, true);
			
			//------------------
			//Try the entire exeFS??????
			/*long offset = 0x6c00;
			long end = 0x409e00;
			
			FileBuffer exe_enc = FileBuffer.createBuffer(inpath, offset, end, false);
			exe_enc.writeFile(exefstest1);
			CitrusNCC part0 = ncsd.getPartition(0);
			byte[] dec = part0.decryptData(exe_enc, crypto, CitrusNCC.IV_TYPE_EXEFS, false);
			FileBuffer.wrap(dec).writeFile(exefstest2);*/
			
			
			
		}
		catch(Exception x){
			x.printStackTrace();
			System.exit(1);
		}

	}

}
