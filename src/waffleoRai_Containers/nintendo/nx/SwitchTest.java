package waffleoRai_Containers.nintendo.nx;


import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.OutputStreamWriter;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.List;

import waffleoRai_Containers.nintendo.nx.NXPatcher.PatchedInfo;
import waffleoRai_Encryption.FileCryptRecord;
import waffleoRai_Encryption.StaticDecryption;
import waffleoRai_Encryption.nintendo.NinCryptTable;
import waffleoRai_Encryption.nintendo.NinCrypto;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_fdefs.nintendo.NXSysDefs;
import waffleoRai_Files.NodeMatchCallback;
import waffleoRai_Files.tree.DirectoryNode;
import waffleoRai_Files.tree.FileNode;
import waffleoRai_Files.tree.FileTreeSaver;
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
		String dlcnsp = "E:\\Library\\Games\\Console\\data\\HAC_ADENB_USA\\0100E95004039004.nsp\\00";
		String patchnca = "d55fd2ab47a76211c612729d42e3a5b5.nca"; //Partition 1
		String mainnca = "6292a2e13c10968ac2b89aeac26d8453.nca"; //Partition 1 (in /secure)

		try{
			
			//Crypto
			NXCrypt crypto = new NXCrypt();
			crypto.importCommonKeys(keypath_prod);
			crypto.importTitleKeys(keypath_title);
			
			//Save test dir
			String stestdir = testdir + "\\savetest";
			
			//Try base tree...
			System.err.println("Reading base FS...");
			String ctbl_path = stestdir + "\\ctbl.bin";
			String base_tree_path = stestdir + "\\basetree.bin";
			NXCartImage xci = NXCartImage.readXCI(xci_path);
			xci.unlock(crypto);
			DirectoryNode tree = xci.getFileTree(NXUtils.TREEBUILD_COMPLEXITY_MERGED);
			int tsize = tree.getAllDescendants(true).size();
			System.err.println("Saving base tree - Node count: " + tsize);
			FileTreeSaver.saveTree(tree, base_tree_path, false, false);
			
			//Try patch...
			String patch_tree_path = stestdir + "\\patchtree.bin";
			System.err.println("Reading patch...");
			PatchedInfo patched = NXPatcher.patchXCI(xci_path, patchnsp, crypto, true);
			//patched.newroot.printMeToStdErr(0);
			tree = patched.newroot;
			tsize = tree.getAllDescendants(true).size();
			System.err.println("Saving patched tree - Node count: " + tsize);
			FileTreeSaver.saveTree(tree, patch_tree_path, false, false);
			NinCryptTable ctbl = patched.crypt_table;
			/*NXUtils.setActiveCryptTable(ctbl);
			String[] test = NXUtils.getControlStrings(tree, NXUtils.LANIDX_AMENG);
			for(String s : test) System.err.println(s);*/
			
			//Try DLC...
			String dlc_tree_path = stestdir + "\\dlctree.bin";
			System.err.println("Reading DLC...");
			NXPatcher.mountDLC(tree, ctbl, dlcnsp, crypto, "dlc4");
			tsize = tree.getAllDescendants(true).size();
			System.err.println("Saving patched tree w/ DLC - Node count: " + tsize);
			FileTreeSaver.saveTree(tree, dlc_tree_path, false, false);
			System.err.println("Unmounting DLC nodes...");
			Collection<FileNode> dnodes = NXUtils.unmountDLCNodes(tree, "dlc4");
			System.err.println("New tree size: " + tree.getAllDescendants(true).size());
			System.err.println("DLC nodes: " + dnodes.size());
			for(FileNode fn : dnodes){
				//System.err.println("\t" + fn.getFileName());
				fn.restoreToParent();
			}
			
			//Try saving the DLC only
			String dlc_nodes_path = stestdir + "\\dlcnodes.bin";
			FileTreeSaver.saveNodes(dnodes, dlc_nodes_path, false);
			
			for(FileNode fn : dnodes) fn.setParent(null);
			
			//Try to read back in
			dnodes = FileTreeSaver.loadNodes(dlc_nodes_path, tree);
			tsize = tree.getAllDescendants(true).size();
			System.err.println("DLC Remounted, Tree Size: " + tsize);
			
			//Save Crypt table
			//System.err.println("Saving crypt table...");
			//ctbl.exportToFile(ctbl_path);
			
			
			//Now try loading them back in.
			//ctbl = new NinCryptTable();
			//ctbl.importFromFile(ctbl_path);
			//Set crypto state
			//NXUtils.setActiveCryptTable(ctbl);
			
			//Base tree
			//tree = FileTreeSaver.loadTree(base_tree_path);
			//tree.printMeToStdErr(0);
			//Try reading the NACP
			/*String[] test = NXUtils.getControlStrings(tree, NXUtils.LANIDX_AMENG);
			for(String s : test) System.err.println(s);*/
			//tsize = tree.getAllDescendants(true).size();
			//System.err.println("Base tree loaded - Node count: " + tsize);
			
			//Patch
			//tree = FileTreeSaver.loadTree(patch_tree_path);
			//tsize = tree.getAllDescendants(true).size();
			//System.err.println("Patch tree loaded - Node count: " + tsize);
			//tree.printMeToStdErr(0);
			//String[] test = NXUtils.getControlStrings(tree, NXUtils.LANIDX_AMENG);
			//for(String s : test) System.err.println(s);
			
			//Test RomFS
			/*String ppath = testdir + "\\patchtest\\ADENB_patched.bin";
			FileBuffer pdat = FileBuffer.createBuffer(ppath, false);
			long datoff = 0x1a34000;
			NXRomFS romfs = NXRomFS.readNXRomFSHeader(pdat, datoff);
			romfs.readTree(pdat, datoff, 0L);
			romfs.getFileTree().printMeToStdErr(0);*/
			
			//Load cart image & get base NCA
			/*NXCartImage xci = NXCartImage.readXCI(xci_path);
			xci.unlock(crypto);
			
			//Dump all cnmt files...
			DirectoryNode xciroot = xci.getFileTree(NXCartImage.TREEBUILD_COMPLEXITY_ALL);
			NinCryptTable ctbl = xci.generateCryptTable();
			ctbl.printMe();
			NXDecryptor decer = new NXDecryptor(ctbl, testdir + "\\ADENB");
			StaticDecryption.setDecryptorState(NXSysDefs.getCTRCryptoDef().getID(), decer);
			String cnmt_dir = testdir + "\\ADENB";
			
			Collection<FileNode> cnmts = xciroot.getNodesThat(new NodeMatchCallback(){

				public boolean meetsCondition(FileNode n) {
					if(n.isDirectory()) return false;
					return n.getFileName().endsWith(".cnmt");
				}
				
			});
			for(FileNode cnmtnode : cnmts){
				System.err.println("CNMT Found: " + cnmtnode.getFullPath());
				System.err.println("Location: " + cnmtnode.getLocationString());
				System.err.println("File Size: 0x" + Long.toHexString(cnmtnode.getLength()));
				System.err.println("Crypt Group: " + cnmtnode.getMetadataValue(NinCrypto.METAKEY_CRYPTGROUPUID));
				String cnmt_path = cnmt_dir + "\\" + cnmtnode.getFileName() + ".txt";
				
				BufferedWriter bw = new BufferedWriter(new FileWriter(cnmt_path));
				//Note the tree path
				bw.write(cnmtnode.getFullPath() + "\n");
				FileBuffer dat = cnmtnode.loadData();
				dat.writeFile(cnmt_dir + "\\" + cnmtnode.getFileName());
				NXContentMeta cnmt = NXContentMeta.readCMNT(dat, 0);
				cnmt.printMeTo(bw);
				bw.close();
			}*/
			
			/*SwitchNCA nca_base = xci.getNCAByName(mainnca);
			NXNCAPart base_part = nca_base.getPartition(1);
			FileNode nca_node_base = xci.getNCANodeByName(mainnca);
			nca_node_base.addEncryption(NXSysDefs.getCTRCryptoDef());
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
			nca_node_patch.addEncryption(NXSysDefs.getCTRCryptoDef());
			System.err.println("Patch NCA Node: " + nca_node_patch.getSourcePath() + " @ " + nca_node_patch.getLocationString());
			
			//Make an info writer directed to stderr
			//BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(System.err));
			
			//Nab correct partitions and parse patch info
			NXNCAPart patch_part = nca_patch.getPartition(1);
			long partoff = nca_node_patch.getOffset() + patch_part.getOffset();
			patch_part.readPatchInfo(nca_node_patch.getSourcePath(), partoff);
			NXPatchInfo pinfo = patch_part.getPatchData();
			//pinfo.printMe(bw);
			
			//Do patch
			NinCryptTable ctbl = xci.generateCryptTable();
			System.err.println("Patch Key: " + NXCrypt.printHash(patch_part.getKey()));
			Collection<FileCryptRecord> crecs = pinfo.addEncryptionInfo(patch_part.getKey(), patch_part.genCTR(0), ctbl);
			//ctbl.printMe();
			
			FileNode part_node_base = nca_node_base.getSubFile(base_part.getOffset(), base_part.getSize());
			FileNode part_node_patch = nca_node_patch.getSubFile(patch_part.getOffset(), patch_part.getSize());
			for(FileCryptRecord cr : crecs) cr.setCryptOffset(cr.getCryptOffset() + part_node_patch.getOffset());
			PatchworkFileNode pfn = pinfo.generatePatchedImage(part_node_base, part_node_patch);
			System.err.println("Node pieces: " + pfn.getBlocks().size());
			System.err.println("Node size: 0x" + Long.toHexString(pfn.getLength()));
			//pfn.printDetailedTo(bw);
			
			//bw.close();
			
			//Prepare decryptor
			NXDecryptor decer = new NXDecryptor(ctbl, testdir + "\\patchtest");
			StaticDecryption.setDecryptorState(NXSysDefs.getCTRCryptoDef().getID(), decer);
			StaticDecryption.setDecryptorState(NXSysDefs.getXTSCryptoDef().getID(), decer);
			
			//Try to load node
			FileBuffer dat = pfn.loadData();
			System.err.println("File size: 0x" + Long.toHexString(dat.getFileSize()));*/
			//while(true); //Hold to check memory burden
			
			/*String outpath = testdir + "\\patchtest\\ADENB_patched.bin";
			dat.writeFile(outpath);
			
			//Load back in...
			FileBuffer outfile = FileBuffer.createBuffer(outpath, false);
			
			//Hash check <3
			int hbsz_shamt = 14;
			long[] htbl_offs = {0x0, 0x4000, 0x8000, 0xc000, 0x1c000, 0x1a34000};
			long[] htbl_sz = {0x4000, 0x4000, 0x4000, 0x10000, 0x1a18000, 0x342f2ec84L};
			boolean okay = true;
			for(int l = 0; l < 5; l++){
				if(!okay) break;
				System.err.println("Checking hash level " + l);
				int bcount = (int)((htbl_sz[l+1] + 0x3FFF) >>> hbsz_shamt);
				System.err.println("Level blocks " + bcount);
				
				long hoff = htbl_offs[l];
				long boff = htbl_offs[l+1];
				
				for(int b = 0; b < bcount; b++){
					//System.err.println("Checking block " + b);
					byte[] hash = outfile.getBytes(hoff, hoff+32); hoff+=32;
					byte[] block = outfile.getBytes(boff, boff + 0x4000); boff += 0x4000;
					
					byte[] bhash = NXCrypt.getSHA256(block);
					if(!MessageDigest.isEqual(hash, bhash)){
						System.err.println("Bad block found - level " + l + " @ 0x" + Long.toHexString(boff-0x4000) + " (block " + b + ")");
						okay = false;
						break;
					}
				}
				
			}*/
			
			//Dump partition 0 to see what's in it...
			/*NXNCAPart patch_part_0 = nca_patch.getPartition(0);
			patch_part_0.buildFileTree(patchdat, nca_node_patch.getOffset() + patch_part_0.getOffset(), true);
			//DirectoryNode part0 = patch_part_0.getFileTree();
			//part0.printMeToStdErr(0);
			
			flist = patch.getFileList();
			for(FileNode f : flist){
				if(f.getFileName().endsWith(".nca")){
					SwitchNCA mynca = SwitchNCA.readNCA(patchdat, f.getOffset());
					mynca.unlock(crypto);
					crecs = mynca.addEncryptionInfo(ctbl);
					for(FileCryptRecord cr : crecs) cr.setCryptOffset(cr.getCryptOffset() + f.getOffset());
					
					String outdir = testdir + "\\patchtest\\patchcontents\\" + f.getFileName();
					mynca.buildFileTree(patchdat, f.getOffset(), NXCartImage.TREEBUILD_COMPLEXITY_ALL);
					DirectoryNode root = mynca.getFileTree();
					root.incrementTreeOffsetsBy(f.getOffset());
					root.setSourcePathForTree(patchnsp);
					root.printMeToStdErr(0);
					root.dumpTo(outdir);
				}
			}*/
			
			/*String cnmt_path = testdir + "\\patchtest\\patchcontents\\537768169091e4f4ccb6be368fab83e9.cnmt.nca\\p00\\PFS0\\Patch_0100e95004038800.cnmt";
			String nacp_path = testdir + "\\patchtest\\patchcontents\\66d0c70ed55cb34299409a14b0e62628.nca\\p00\\RomFS\\control.nacp";
			
			NXContentMeta cnmt = NXContentMeta.readCMNT(FileBuffer.createBuffer(cnmt_path, false), 0);
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(System.out));
			cnmt.printMeTo(bw);
			bw.close();*/
			
			/*NXNACP nacp = NXNACP.readNACP(FileBuffer.createBuffer(nacp_path, false), 0);
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(System.out));
			nacp.printMeTo(bw);
			bw.close();*/
			
		}
		catch(Exception x){
			x.printStackTrace();
			System.exit(1);
		}

	}

}
