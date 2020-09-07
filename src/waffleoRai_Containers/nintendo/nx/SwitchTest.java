package waffleoRai_Containers.nintendo.nx;


import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.util.List;

import waffleoRai_Encryption.FileCryptRecord;
import waffleoRai_Encryption.nintendo.NinCryptTable;
import waffleoRai_Encryption.nintendo.NinCrypto;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Files.tree.DirectoryNode;
import waffleoRai_Files.tree.FileNode;
import waffleoRai_Files.tree.PatchworkFileNode;

public class SwitchTest {

	public static void main(String[] args) {
		
		String lib_path = "E:\\Library\\Games\\Console";
		String gamecode = "HAC_ADENB_USA";
		String testdir = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\nxtest";
		
		String xci_path = lib_path + "\\" + gamecode + ".xci";
		String dec_dir = lib_path + "\\decbuff\\" + gamecode;
		
		String nca_path = dec_dir + "\\secure\\a3795598fdd4ad13101a8d79fd9b5fa8.nca";
		String hdr_test_path = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\nxtest\\ncahdr.bin";
		
		String keypath = testdir + "\\hac_ncahdr.bin";
		String keypath_prod = testdir + "\\prod.keys";
		String keypath_title = testdir + "\\title.keys";
		
		//Patch test
		String patchnsp = "E:\\Library\\Games\\Console\\data\\HAC_ADENB_USA\\0100E95004038800.nsp\\00";
		String patchnca = "d55fd2ab47a76211c612729d42e3a5b5.nca"; //Partition 1
		String mainnca = "6292a2e13c10968ac2b89aeac26d8453.nca"; //Partition 1 (in /secure)

		try{
			
			//Crypto
			NXCrypt crypto = new NXCrypt();
			crypto.importCommonKeys(keypath_prod);
			crypto.importTitleKeys(keypath_title);
			
			//Load cart image & get base NCA
			NXCartImage xci = NXCartImage.readXCI(xci_path);
			xci.unlock(crypto);
			SwitchNCA nca_base = xci.getNCAByName(mainnca);
			NXNCAPart base_part = nca_base.getPartition(1);
			FileNode nca_node_base = xci.getNCANodeByName(mainnca);
			nca_node_base.setMetadataValue(NinCrypto.METAKEY_CRYPTGROUPUID, Long.toHexString(base_part.updateCryptRegUID()));
			System.err.println("Base NCA Node: " + nca_node_base.getSourcePath() + " @ " + nca_node_base.getLocationString());
			
			//Load patch
			FileBuffer patchdat = FileBuffer.createBuffer(patchnsp, false);
			NXPFS patch = NXPFS.readPFS(patchdat, 0L);
			SwitchNCA nca_patch = null;
			FileNode nca_node_patch = null;
			List<FileNode> flist = patch.getFileList();
			for(FileNode f : flist){
				if(f.getFileName().equals(patchnca)){
					nca_node_patch = f;
					nca_patch = SwitchNCA.readNCA(patchdat, f.getOffset());
					nca_patch.unlock(crypto);
				}
			}
			nca_node_patch.setSourcePath(patchnsp);
			System.err.println("Patch NCA Node: " + nca_node_patch.getSourcePath() + " @ " + nca_node_patch.getLocationString());
			
			//Make an info writer directed to stderr
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(System.err));
			
			//Nab correct partitions and parse patch info
			NXNCAPart patch_part = nca_patch.getPartition(1);
			long partoff = nca_node_patch.getOffset() + patch_part.getOffset();
			patch_part.readPatchInfo(nca_node_patch.getSourcePath(), partoff);
			NXPatchInfo pinfo = patch_part.getPatchData();
			//pinfo.printMe(bw);
			
			//Do patch
			NinCryptTable ctbl = xci.generateCryptTable();
			System.err.println("Patch Key: " + NXCrypt.printHash(patch_part.getKey()));
			pinfo.addEncryptionInfo(patch_part.getKey(), patch_part.genCTR(0), ctbl);
			//ctbl.printMe();
			
			PatchworkFileNode pfn = pinfo.generatePatchedImage(nca_node_base, nca_node_patch);
			pfn.printDetailedTo(bw);
			
			bw.close();
		}
		catch(Exception x){
			x.printStackTrace();
			System.exit(1);
		}

	}

}
