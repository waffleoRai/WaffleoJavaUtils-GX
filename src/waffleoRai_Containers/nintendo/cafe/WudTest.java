package waffleoRai_Containers.nintendo.cafe;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.List;
import java.util.Random;

import waffleoRai_Containers.nintendo.citrus.CitrusNCC;
import waffleoRai_Containers.nintendo.wiidisc.WiiContent;
import waffleoRai_Containers.nintendo.wiidisc.WiiCryptListener;
import waffleoRai_Containers.nintendo.wiidisc.WiiTMD;
import waffleoRai_Containers.nintendo.wiidisc.WiiTicket;
import waffleoRai_Encryption.AES;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileNode;

public class WudTest {

	public static void main(String[] args) {

		String wudpath = "E:\\Library\\Games\\Console\\WUP_AX5E_USZ.wud";
		
		String ckeypath = "E:\\Library\\Games\\Console\\data\\WUP_AX5E_USZ\\common.key";
		String gkeypath = "E:\\Library\\Games\\Console\\data\\WUP_AX5E_USZ\\game.key";
		
		String outdir = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\cafetest";
		String buffpath = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\cafetest\\AX5E_ptbl.bin";
		String bigbuffdir = "E:\\Library\\Games\\Console\\decbuff\\WUP_AX5E_USZ";
		
		long ptable_offset = 0x18000;
		long sec_size = 0x8000;
		
		try{
			Random rand = new Random();
			
			byte[] gkey = FileBuffer.createBuffer(gkeypath).getBytes();
			byte[] ckey = FileBuffer.createBuffer(ckeypath).getBytes();
			WiiUDisc.setCommonKey(ckey);
			
			WiiUDisc mydisc = WiiUDisc.readWUD(wudpath, gkey);
			System.err.println("Initial disc read complete.");
			WudPartition part3 = mydisc.getPartition(3);
			WiiTMD tmd3 = part3.getTMD();
			CafeFST fst3 = part3.getFST();
			
			
			
			/*mydisc.setDecryptionBufferDir(bigbuffdir);
			mydisc.decryptPartitionsTo(bigbuffdir, new CafeCryptListener(){

				private int pcount = 0;
				private int fcount = 0;
				private long psize = 0L;
				
				public void setPartitionCount(int count) {pcount = count;}

				public void onPartitionComplete(int idx) {
					System.err.println("Partition " + (idx+1) + " of " + pcount + " decrypted!");
				}

				public void setFileCount(int count) {fcount = count;}

				public void onFileStart(String fname) {
					System.err.println("Processing file " + fname);
				}

				public void onFileComplete(int idx) {
					int r = rand.nextInt(100);
					if(r == 1){
						System.err.println((idx+1) + " of " + fcount + " files processed");
					}
				}

				public void setPartitionSize(long size) {
					psize = size;
				}

				public void setCurrentPosition(long cpos) {
					double percent = ((double)cpos / (double)psize) * 100.0;
					System.err.println("0x" + Long.toHexString(cpos) + " bytes processed (" + String.format("%.2f", percent) + "%)");
				}

				public int getUpdateInterval() {
					return 2500;
				}

				
			});
			mydisc.getFileTree().printMeToStdErr(0);
			
			//Copy all tga files to file...
			Collection<FileNode> fnlist = mydisc.getFileTree().getAllDescendants(false);
			for(FileNode fn : fnlist){
				if(fn.getFileName().endsWith(".tga")){
					String outpath = outdir + "\\" + fn.getFullPath().replace("/", "-");
					fn.loadData().writeFile(outpath);
				}
			}*/
			
			
			//byte[] gkey = FileBuffer.createBuffer(gkeypath).getBytes();
			/*byte[] iv = new byte[16];
			AES aes = new AES(gkey);
			byte[] decdat = aes.decrypt(iv, FileBuffer.createBuffer(wudpath, 0x28000, 0x30000).getBytes());
			FileBuffer.wrap(decdat).writeFile(outdir + "\\AX5E_SI_fst.bin");*/
			
			//We'll try /03/title.tmd...
			/*long partoff = 0x20000;
			long clusteroff = 0x82 * 0x8000;
			long foff_raw = 0x1000;
			long fsize = 0x350;
			long foff = foff_raw << 5;
			long offset = partoff + clusteroff + foff;
			System.err.println("Disc offset: 0x" + Long.toHexString(offset));
			System.err.println("End offset: 0x" + Long.toHexString(offset + fsize));
			byte[] iv = new byte[16];
			long ivval = foff >>> 16;
			long mask = 0xFF00000000000000L;
			int shift = 56;
			for(int i = 0; i < 8; i++){
				iv[i+8] = (byte)((ivval & mask) >>> shift);
				shift -= 8;
				mask = mask >>> 8;
			}
			AES aes = new AES(gkey);
			byte[] decdat = aes.decrypt(iv, FileBuffer.createBuffer(wudpath, offset, offset+fsize).getBytes());
			FileBuffer.wrap(decdat).writeFile(outdir + "\\AX5E_SI_04_title.tik");*/
		
			/*MessageDigest sha = MessageDigest.getInstance("SHA1");
			sha.update(FileBuffer.createBuffer(buffpath, 0x800, 0x8000).getBytes());
			byte[] hashbuff = sha.digest();
			
			System.err.println(CitrusNCC.printHash(hashbuff));*/
			
			//Try partition 3 FST...
			/*long p3_off = 0xe0000000L;
			WiiTicket p3_tik = new WiiTicket(FileBuffer.createBuffer(outdir + "\\AX5E_SI_03_title.tik", true), 0L);
			WiiTMD p3_tmd = new WiiTMD(FileBuffer.createBuffer(outdir + "\\AX5E_SI_03_title.tmd", true), 0L, true);
			//p3_tik.printInfo();
			//p3_tmd.printInfo();
			
			byte[] ckey = FileBuffer.createBuffer(ckeypath).getBytes();
			WiiUDisc.setCommonKey(ckey);
			byte[] pkey = p3_tik.decryptTitleKey(true);
			byte[] iv = new byte[16];
			WiiContent cont0 = p3_tmd.getContent(0);
			int cidx = cont0.getIndex();
			iv[0] = (byte)((cidx >>> 8) & 0xFF);
			iv[1] = (byte)(cidx & 0xFF);
			AES aes = new AES(pkey);
			aes.initDecrypt(iv);
			
			//Do one block at a time.
			int blocks = (int)(cont0.getSize()/0x8000);
			BufferedInputStream bis = new BufferedInputStream(new FileInputStream(wudpath)); 
			long skip = bis.skip(p3_off + 0x8000);
			System.err.println("skipped: 0x" + Long.toHexString(skip));
			BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outdir + "\\AX5E_part3_content0.bin"));
			for(int b = 0; b < blocks; b++){
				byte[] enc = new byte[0x8000];
				bis.read(enc);
				byte[] dec = aes.decryptBlock(enc, b == (blocks-1));
				bos.write(dec);
			}
			bos.close();
			bis.close();*/
			
			//Try the whole thing?
			
			
		}
		catch(Exception x){
			x.printStackTrace();
			System.exit(1);
		}
		

	}

}
