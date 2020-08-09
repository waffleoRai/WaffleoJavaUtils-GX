package waffleoRai_Containers.nintendo.nx;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;

import waffleoRai_Encryption.FileCryptRecord;
import waffleoRai_Encryption.nintendo.NinCryptTable;
import waffleoRai_Encryption.nintendo.NinCrypto;
import waffleoRai_Utils.DirectoryNode;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileNode;

public class SwitchTest {

	public static void main(String[] args) {
		
		String lib_path = "E:\\Library\\Games\\Console";
		String gamecode = "HAC_AUBQA_USA";
		String testdir = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\nxtest";
		
		String xci_path = lib_path + "\\" + gamecode + ".xci";
		String dec_dir = lib_path + "\\decbuff\\" + gamecode;
		
		String nca_path = dec_dir + "\\secure\\a3795598fdd4ad13101a8d79fd9b5fa8.nca";
		String hdr_test_path = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\nxtest\\ncahdr.bin";
		
		String keypath = testdir + "\\hac_ncahdr.bin";
		String keypath_prod = testdir + "\\prod.keys";
		String keypath_title = testdir + "\\title.keys";

		try{
			
			//NXCartImage xci = NXCartImage.readXCI(xci_path);
			//xci.dumpRawNCAs(dec_dir);
			
			//Try the first 0x200 sector...
			//byte[] key = NXCrypt.str2Key(keystr);

			//Reverse?
			/*byte[] temp = new byte[key.length];
			for(int i = 0; i < key.length; i++) temp[i] = key[key.length-i-1];
			key = temp;*/
			//System.err.println("Key: " + NXCrypt.printHash(key));
			/*FileBuffer hdr_raw = FileBuffer.createBuffer(nca_path, 0, 0xc00, false);
			
			BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(hdr_test_path));
			for(long s = 0; s < 6; s++){
				long st = 0x200 * s;
				byte[] hdr_enc = hdr_raw.getBytes(st, st+0x200);
				byte[] hdr_dec = NXCrypt.decrypt_AESXTS_sector(key, hdr_enc, s);
				bos.write(hdr_dec);
			}	
			bos.close();*/
			
			//byte[] ncakey = FileBuffer.createBuffer(keypath).getBytes();
			//SwitchNCA.setCommonHeaderKey(ncakey);
			
			//Try a pre-decrypted NCA header
			/*FileBuffer ncabuff = FileBuffer.createBuffer(hdr_test_path, false);
			SwitchNCA nca = SwitchNCA.readNCA(ncabuff, 0);
			nca.printMe();*/
			
			//Try to extract first 1MB of partitions...
			//FileBuffer ncabuff = FileBuffer.createBuffer(nca_path, false);
			//SwitchNCA nca = SwitchNCA.readNCA(ncabuff, 0);
			//nca.printMe();
			
			//NXCrypt crypto = new NXCrypt();
			//crypto.importCommonKeys(keypath_prod);
			//crypto.importTitleKeys(keypath_title);
			//nca.extractPartitionsTo(testdir, crypto, 0x2000000);
			//nca.decryptPartKeys(crypto);
			//nca.extractPartitionDataTo(1, 0x368b36c64L, 0xf96a0L, testdir + "\\part1_tables.bin");
			//nca.decryptPartKeys(crypto);
			//nca.extractPartitionDataTo(1, 0x368b36c64L, 0x100000L, testdir + "\\part1_tables.bin");
			
			/*NXRomFS romfs = NXRomFS.readNXRomFSHeader(FileBuffer.createBuffer(testdir + "\\partition01.bin", false), 0x1b58000L);
			romfs.readTree(FileBuffer.createBuffer(testdir + "\\part1_tables.bin", false), 0x0, 0x366fdec64L);
			romfs.getFileTree().printMeToStdErr(0);*/
			
			//Try the whole card...
			NXCrypt crypto = new NXCrypt();
			crypto.importCommonKeys(keypath_prod);
			crypto.importTitleKeys(keypath_title);
			
			NXCartImage xci = NXCartImage.readXCI(xci_path);
			xci.unlock(crypto);
			DirectoryNode tree = xci.getFileTree(NXCartImage.TREEBUILD_COMPLEXITY_MERGED);
			NinCryptTable ctbl = xci.generateCryptTable();
			
			tree.printMeToStdErr(0);
			//ctbl.printMe();
			
			//Try extracting a file
			/*String lpath = "/XCIROOT/HFS/secure/HFS/a3795598fdd4ad13101a8d79fd9b5fa8.nca/p02/PFS0";
			String p1 = lpath + "/NintendoLogo.png";
			String p2 = lpath + "/StartupMovie.gif";
			
			FileNode fn = tree.getNodeAt(p1);
			if(fn != null){
				System.err.println("Found it!");
				FileBuffer dat = FileBuffer.createBuffer(xci_path, fn.getOffset(), fn.getOffset() + fn.getLength(), false);
				dat.writeFile(testdir + "\\NintendoLogo.png");
			}
			
			fn = tree.getNodeAt(p2);
			if(fn != null){
				System.err.println("Found it!");
				FileBuffer dat = FileBuffer.createBuffer(xci_path, fn.getOffset(), fn.getOffset() + fn.getLength(), false);
				dat.writeFile(testdir + "\\StartupMovie.gif");
			}*/
			
			String lpath = "/secure";
			String p1 = lpath + "/icon_AmericanEnglish.dat";
			FileNode fn = tree.getNodeAt(p1);
			if(fn != null){
				System.err.println("Found it!");
				String ckey = fn.getMetadataValue(NinCrypto.METAKEY_CRYPTGROUPUID);
				FileBuffer dat = FileBuffer.createBuffer(xci_path, fn.getOffset(), fn.getOffset() + fn.getLength(), false);
				if(ckey != null){
					long uid = Long.parseUnsignedLong(ckey, 16);
					System.err.println("Crypt UID: 0x" + Long.toHexString(uid));
					FileCryptRecord crec = ctbl.getRecord(uid);
					
					//Get key
					byte[] key = ctbl.getKey(crec.getKeyType(), crec.getKeyIndex());
					NXCrypt.setTempDir(testdir);
					dat = NXCrypt.decryptData(fn, crec, key);
					
				}
				
				dat.writeFile(testdir + "\\icon_AmericanEnglish.jpg");
			}
			
		}
		catch(Exception x){
			x.printStackTrace();
			System.exit(1);
		}

	}

}
