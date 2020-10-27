package waffleoRai_Containers.nintendo.citrus;

import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

import waffleoRai_Encryption.nintendo.NinCryptTable;
import waffleoRai_Files.tree.DirectoryNode;
import waffleoRai_Utils.FileBuffer;

public class CtrCardTest {

	public static void main(String[] args) {

		String dir = "E:\\Library\\Games\\Console";
		String dec_path = dir + "\\decbuff\\CTR_AXCE_USA\\ctr_ncch_part40000000edf00.bin";
		String src_path = dir + "\\CTR_AJRE_USA.3ds";
		
		String key_path = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\3ds_test\\ctr_common9.bin";
		String key_path25 = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\3ds_test\\ctr_25X.bin";
		
		try{
			
			CitrusCrypt crypto = CitrusCrypt.loadCitrusCrypt(FileBuffer.createBuffer(key_path));
			byte[] k25x = FileBuffer.createBuffer(key_path25).getBytes();
			crypto.setKeyX(0x25, k25x);
			
			CitrusNCSD ncsd = CitrusNCSD.readNCSD(FileBuffer.createBuffer(src_path, false), 0, true);
			//ncsd.printToStdErr();
			ncsd.unlock(crypto);
			
			NinCryptTable ctbl = ncsd.generateCryptTable();
			//ctbl.printMe();
			DirectoryNode tree = ncsd.getFileTreeDirect(true);
			tree.printMeToStdErr(0);
			
			//Re-decrypt...
			/*CitrusNCSD ncsd = CitrusNCSD.readNCSD(FileBuffer.createBuffer(src_path, false), 0, true);
			ncsd.printToStdErr();
			//CitrusNCC part0 = ncsd.getPartition(0);
			//part0.setDecBufferLocation(dec_path);
			//part0.refreshDecBuffer(crypto, true);
			
			//AXCE has a weird ROMFS. Look into that...
			long romfs_off = 0x676200;
			long romfs_end = 0x46e82000;
			
			FileBuffer dat = FileBuffer.createBuffer(dec_path, romfs_off, romfs_end, false);*/
			//CitrusROMFS romfs = CitrusROMFS.readROMFS(dat, 0);
			//romfs.getFileTree().printMeToStdErr(0);
			
		}
		catch(Exception x){
			x.printStackTrace();
			System.exit(1);
		}

	}

}
