package waffleoRai_Containers.nintendo.nx;

import java.util.List;

import waffleoRai_Files.tree.FileNode;
import waffleoRai_Utils.FileBuffer;

public class NXCInfoTest {

	public static void main(String[] args) {
		
		String lib_path = "E:\\Library\\Games\\Console";
		String gamecode = "HAC_ADENB_USA";
		String testdir = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\nxtest";
		
		String xci_path = lib_path + "\\" + gamecode + ".xci";
		String dat_dir = lib_path + "\\data\\" + gamecode;
		
		String keypath_prod = testdir + "\\prod.keys";
		String keypath_title = testdir + "\\title.keys";
		
		String update_nsp = "0100E95004038800.nsp";
		String dlc_nsp = "0100E95004039003.nsp";
		
		try{
			
			NXCrypt crypto = new NXCrypt();
			crypto.importCommonKeys(keypath_prod);
			crypto.importTitleKeys(keypath_title);
			
			//Main XCI
			System.err.println("Now doing " + xci_path + "...");
			String outpath = testdir + "\\info_ADENB_xci.txt";
			NXCInfo.main(new String[]{NXCInfo.OP_INFILE+"="+xci_path,
									  NXCInfo.OP_CKEYS+"="+keypath_prod,
									  NXCInfo.OP_TKEYS+"="+keypath_title,
									  NXCInfo.OP_OUTPUT+"="+outpath,
									  NXCInfo.OP_LOWTREE});
			
			String inpath = dat_dir + "\\" + update_nsp + "\\00";
			outpath = testdir + "\\info_ADENB_update.txt";
			System.err.println("Now doing " + inpath + "...");
			NXCInfo.main(new String[]{NXCInfo.OP_INFILE+"="+inpath,
					  NXCInfo.OP_CKEYS+"="+keypath_prod,
					  NXCInfo.OP_TKEYS+"="+keypath_title,
					  NXCInfo.OP_OUTPUT+"="+outpath,
					  NXCInfo.OP_LOWTREE});
			
			//Extract NCAs from PFS
			FileBuffer filedat = FileBuffer.createBuffer(inpath, false);
			NXPFS pfs = NXPFS.readPFS(filedat, 0L);
			List<FileNode> flist = pfs.getFileList();
			for(FileNode f : flist){
				if(f.getFileName().endsWith(".nca")){
					System.err.println("Working on " + f.getFileName() + " @ 0x" + Long.toHexString(f.getOffset()));
					String ncaout = testdir + "\\ADENB_update\\" + f.getFileName();
					long stoff = f.getOffset();
					SwitchNCA nca = SwitchNCA.readNCA(filedat, stoff);
					nca.unlock(crypto);
					nca.setSourcePath(inpath);
					nca.extractDecryptedTo(ncaout, true);
					
					//Try copying it...
					/*ncaout = testdir + "\\ADENB_update\\" + f.getFileName() + ".aes";
					FileBuffer rawnca = FileBuffer.createBuffer(inpath, f.getOffset(), f.getOffset() + f.getLength(), false);
					rawnca.writeFile(ncaout);*/
				}
			}
			
			inpath = dat_dir + "\\" + dlc_nsp + "\\00";
			outpath = testdir + "\\info_ADENB_dlc3.txt";
			System.err.println("Now doing " + inpath + "...");
			NXCInfo.main(new String[]{NXCInfo.OP_INFILE+"="+inpath,
					  NXCInfo.OP_CKEYS+"="+keypath_prod,
					  NXCInfo.OP_TKEYS+"="+keypath_title,
					  NXCInfo.OP_OUTPUT+"="+outpath,
					  NXCInfo.OP_LOWTREE});
			
			
			//Extract NCAs from PFS
			filedat = FileBuffer.createBuffer(inpath, false);
			pfs = NXPFS.readPFS(filedat, 0L);
			flist = pfs.getFileList();
			for(FileNode f : flist){
				if(f.getFileName().endsWith(".nca")){
					System.err.println("Working on " + f.getFileName() + " @ 0x" + Long.toHexString(f.getOffset()));
					String ncaout = testdir + "\\ADENB_dlc3\\" + f.getFileName();
					long stoff = f.getOffset();
					SwitchNCA nca = SwitchNCA.readNCA(filedat, stoff);
					nca.unlock(crypto);
					nca.setSourcePath(inpath);
					nca.extractDecryptedTo(ncaout, true);
					
					//Try copying it...
					/*ncaout = testdir + "\\ADENB_dlc3\\" + f.getFileName() + ".aes";
					FileBuffer rawnca = FileBuffer.createBuffer(inpath, f.getOffset(), f.getOffset() + f.getLength(), false);
					rawnca.writeFile(ncaout);*/
				}
			}
			
		}
		catch(Exception x){
			x.printStackTrace();
			System.exit(1);
		}
	}

}
