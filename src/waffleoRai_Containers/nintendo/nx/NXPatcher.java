package waffleoRai_Containers.nintendo.nx;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import waffleoRai_Encryption.AES;
import waffleoRai_Encryption.FileCryptRecord;
import waffleoRai_Encryption.StaticDecryption;
import waffleoRai_Encryption.nintendo.NinCryptTable;
import waffleoRai_Files.FileNodeModifierCallback;
import waffleoRai_Files.NodeMatchCallback;
import waffleoRai_Files.tree.DirectoryNode;
import waffleoRai_Files.tree.FileNode;
import waffleoRai_Files.tree.PatchworkFileNode;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;
import waffleoRai_fdefs.nintendo.NXSysDefs;

public class NXPatcher {
	
	public static class PatchedInfo{
		
		public String patched_ver;
		public List<PatchworkFileNode> patched_romfs;
		public NinCryptTable crypt_table;
		public DirectoryNode newroot;
		
	}
	
	private static void mountToDir(DirectoryNode src, DirectoryNode targ, String dirname){
		DirectoryNode dir = null;
		FileNode find = targ.getNodeAt("/" + dirname);
		if(find == null) dir = new DirectoryNode(targ, dirname);
		else{
			if(find instanceof DirectoryNode) dir = (DirectoryNode)find;
			else dir = new DirectoryNode(targ, dirname); //Overwrite the found node
		}
		List<FileNode> children = src.getChildren();
		for(FileNode child : children) child.setParent(dir);
	}
	
	public static PatchedInfo patchXCI(String xci_path, String patch_pfs_path, NXCrypt crypto, boolean verbose) throws IOException, UnsupportedFileTypeException{
		//Maps
		//This is sloppy and I don't like it but I'm too lazy to think up something better
		Map<String, SwitchNCA> base_ncas = new HashMap<String, SwitchNCA>();
		Map<String, FileNode> base_nca_nodes = new HashMap<String, FileNode>();
		Map<String, SwitchNCA> patch_ncas = new HashMap<String, SwitchNCA>();
		Map<String, FileNode> patch_nca_nodes = new HashMap<String, FileNode>();
		
		//Open base image & map NCAs...
		NXCartImage xci = NXCartImage.readXCI(xci_path);
		xci.unlock(crypto);
		NinCryptTable ctbl = xci.generateCryptTable();
		DirectoryNode root = xci.getFileTree(NXCartImage.TREEBUILD_COMPLEXITY_MERGED);
		xci.mapNCAs(base_ncas);
		xci.mapNCANodes(base_nca_nodes);
		if(verbose) System.err.println("NXPatcher.patchXCI || XCI NCAs mapped. NCAs found: " + base_ncas.size());
		
		//Open patch...
		FileBuffer patchdat = FileBuffer.createBuffer(patch_pfs_path, false);
		NXPFS patch = NXPFS.readPFS(patchdat, 0L);
		//Read patch ncas...
		List<String> cmnt_ids = new LinkedList<String>();
		List<FileNode> flist = patch.getFileList();
		DirectoryNode patch_other = new DirectoryNode(null, "");
		for(FileNode f : flist){
			//If it doesn't end in .nca, it will be mounted elsewhere (later)
			String fname = f.getFileName();
			f.setSourcePath(patch_pfs_path);
			if(fname.endsWith(".nca")){
				String id = fname.substring(0, fname.indexOf('.'));	
				SwitchNCA nca_patch = SwitchNCA.readNCA(patchdat, f.getOffset());
				nca_patch.unlock(crypto);
				patch_ncas.put(id, nca_patch);
				patch_nca_nodes.put(id, f);
				if(fname.contains(".cnmt")) cmnt_ids.add(id);
				
				//Add general patch partition records to crypt table
				//Note that the PFS getFileList() methods takes care of adding PFS filedata offset
				Collection<FileCryptRecord> crecs = nca_patch.addEncryptionInfo(ctbl);
				for(FileCryptRecord crec : crecs) crec.setCryptOffset(crec.getCryptOffset() + f.getOffset());
			}
			else f.setParent(patch_other);
		}
		if(verbose) System.err.println("NXPatcher.patchXCI || Patch NCAs mapped. NCAs found: " + patch_ncas.size());
		if(verbose) System.err.println("NXPatcher.patchXCI || Content Metadata NCAs found: " + cmnt_ids.size());
		
		//Load decryptor into memory so internal files can be read...
		NXDecryptor decer = new NXDecryptor(ctbl, FileBuffer.getTempDir());
		StaticDecryption.setDecryptorState(NXSysDefs.getCTRCryptoDef().getID(), decer);
		
		//Load CNMTs from base XCI and map by content ID...
		Collection<FileNode> cnmt_nodes = root.getNodesThat(new NodeMatchCallback(){

			public boolean meetsCondition(FileNode n) {
				if(n.isDirectory()) return false;
				return n.getFileName().endsWith(".cnmt");
			}
			
		});
		Map<String, NXContentMeta> base_meta_map = new HashMap<String, NXContentMeta>();
		for(FileNode cnmtnode : cnmt_nodes){
			if(verbose) System.err.println("NXPatcher.patchXCI || Base CNMT Found: " + cnmtnode.getFullPath());
			FileBuffer dat = cnmtnode.loadData();
			NXContentMeta cnmt = NXContentMeta.readCMNT(dat, 0);
			base_meta_map.put(Long.toHexString(cnmt.getID()), cnmt);
		}
		
		final String META_KEY_MOUNTPART = "mountncato";
		//Go through the patch metas and determine which NCAs go where...
		List<DirectoryNode> patch_add = new LinkedList<DirectoryNode>(); //Denovo additions to root (create mode)
		Map<String, String> merge_map = new HashMap<String, String>(); //NCA ID -> NCA ID of NCAs to merge
		List<DirectoryNode> ow_add = new LinkedList<DirectoryNode>(); //NCA trees to mount for overwrites. Meta field indicates partition.
		for(String s : cmnt_ids){
			//For each CMNT in the patch...
			if(verbose) System.err.println("NXPatcher.patchXCI || Examining patch meta NCA: " + s);
			
			//Get the NCA and its tree from the patch
			FileNode nca_node = patch_nca_nodes.get(s); //We'll need the NCA offset...
			SwitchNCA cmnt_nca = patch_ncas.get(s);
			DirectoryNode cmnt_nca_tree = cmnt_nca.getFileTree();
			cmnt_nca_tree.incrementTreeOffsetsBy(nca_node.getOffset());
				
			//It SHOULD only contain one NCA, but we'll do "all" just because it's not any harder
			cnmt_nodes = cmnt_nca_tree.getNodesThat(new NodeMatchCallback(){

				public boolean meetsCondition(FileNode n) {
					if(n.isDirectory()) return false;
					return n.getFileName().endsWith(".cnmt");
				}
				
			});

			for(FileNode cnmtnode : cnmt_nodes){
				if(verbose) System.err.println("NXPatcher.patchXCI || Patch CNMT Found: " + cnmtnode.getFileName());
				
				//Load and parse the CNMT file
				FileBuffer dat = cnmtnode.loadData();
				NXContentMeta cnmt = NXContentMeta.readCMNT(dat, 0);
				
				//If not patch, skip.
				if(cnmt.getMetaType() != NXContentMeta.CNMT_TYPE_PATCH){
					if(verbose) System.err.println("NXPatcher.patchXCI || Warning: CNMT is not a patch CNMT. Skipping...");
					continue;
				}
				
				//Match target ID to base application ID and find CNMT from base
				String appid_str = Long.toHexString(cnmt.getAppID());
				NXContentMeta base_meta = base_meta_map.get(appid_str);
				if(base_meta == null){
					if(verbose) {System.err.println("NXPatcher.patchXCI || Warning: AppID from cnmt " + cnmtnode.getFileName() + 
							" (" + appid_str + ") could not be matched to an AppID in base archive. Skipping...");}
					continue;
				}
				List<NXContentMeta.ContentInfo> base_contents = base_meta.getContents();
				if(verbose) System.err.println("NXPatcher.patchXCI || AppID Match Found - AppID: " + appid_str);
				
				//Add the cmnt NCA itself to the add queue
				String mount_dir = "patch_" + Long.toHexString(cnmt.getID());
				cmnt_nca_tree.setMetadataValue(META_KEY_MOUNTPART, mount_dir);	
				patch_add.add(cmnt_nca_tree);
				
				//Iterate through the fragment sets...
				List<NXContentMeta.FragmentSet> fragsets = cnmt.getFragmentSets();
				for(NXContentMeta.FragmentSet fs : fragsets){
					//Note the ID of the patch content NCA...
					String myid = AES.bytes2str(fs.dest_content_id);
					if(verbose) System.err.println("NXPatcher.patchXCI || Patch Content Found: " + myid);
					
					switch(fs.update_type){
					case 0: //Merge
					case 1: //Overwrite
						//Try to find target by matching content type...
						String match = null;
						for(NXContentMeta.ContentInfo ci : base_contents){
							if(ci.content_type == fs.frag_targ_content_type){
								if(match != null){
									//Ambiguous
									match = null;
									break;
								}
								else match = AES.bytes2str(ci.contentID);
							}
						}
						if(match == null){
							//Try matching by size?
							long tsize = cnmt.getPatchTargetOriginalSize(myid);
							if(tsize > 0L){
								for(NXContentMeta.ContentInfo ci : base_contents){
									if(ci.content_type == fs.frag_targ_content_type){
										if(ci.size == tsize){
											match = AES.bytes2str(ci.contentID);
											break;
										}
									}
								}	
							}
						}
						
						FileNode pnca_node = patch_nca_nodes.get(s); //We'll need the NCA offset...
						SwitchNCA p_nca = patch_ncas.get(s);
						
						//If still no match, just mount denovo
						if(match == null){
							if(verbose) System.err.println("NXPatcher.patchXCI || No patch target was found. Mounting de novo to " + mount_dir);
							
							//Get NCA and load tree
							p_nca.buildFileTree(patchdat, pnca_node.getOffset(), NXUtils.TREEBUILD_COMPLEXITY_MERGED);
							DirectoryNode mytree = p_nca.getFileTree();
							//Increment offset of tree (and note path)
							mytree.incrementTreeOffsetsBy(pnca_node.getOffset());
							mytree.setSourcePathForTree(patch_pfs_path);
							mytree.setMetadataValue(META_KEY_MOUNTPART, mount_dir);	
							//Put in list
							patch_add.add(mytree);
						}
						else{
							if(verbose) System.err.println("NXPatcher.patchXCI || Patch target found: " + match);
							
							//Dismount target
							xci.removeNCA(match);
							
							//Note in appropriate struct
							if(fs.update_type == 0){
								//Merge
								merge_map.put(myid, match);
							}
							else{
								p_nca.buildFileTree(patchdat, pnca_node.getOffset(), NXUtils.TREEBUILD_COMPLEXITY_MERGED);
								DirectoryNode mytree = p_nca.getFileTree();
								//Increment offset of tree (and note path)
								mytree.incrementTreeOffsetsBy(pnca_node.getOffset());
								mytree.setSourcePathForTree(patch_pfs_path);
								
								//Note partition
								mytree.setMetadataValue(META_KEY_MOUNTPART, p_nca.getContainerName());
								
								//Add to list
								ow_add.add(mytree);
							}
						}
						
						break;
					case 2: //Create
						if(verbose) System.err.println("NXPatcher.patchXCI || New patch content. Mounting to: " + mount_dir);
						FileNode pnca_node2 = patch_nca_nodes.get(s);
						SwitchNCA p_nca2 = patch_ncas.get(s);
						p_nca2.buildFileTree(patchdat, pnca_node2.getOffset(), NXUtils.TREEBUILD_COMPLEXITY_MERGED);
						DirectoryNode mytree = p_nca2.getFileTree();
						//Increment offset of tree (and note path)
						mytree.incrementTreeOffsetsBy(pnca_node2.getOffset());
						mytree.setSourcePathForTree(patch_pfs_path);
						mytree.setMetadataValue(META_KEY_MOUNTPART, mount_dir);						
						//Put in list
						patch_add.add(mytree);
						break;
					}
				}
			}
			
		}
		
		//Regenerate xci tree now that nca's to replace have been dismounted
		if(verbose) System.err.println("NXPatcher.patchXCI || Regenerating base tree without dismounted NCAs...");
		root = xci.getFileTree(NXCartImage.TREEBUILD_COMPLEXITY_MERGED);
		
		//Mount the novels and overwrites...
		if(verbose) System.err.println("NXPatcher.patchXCI || Mounting new content: " + patch_add.size() + " archives found");
		for(DirectoryNode dn : patch_add){
			String dirname = dn.getMetadataValue(META_KEY_MOUNTPART);
			mountToDir(root, dn, dirname);
		}
		if(verbose) System.err.println("NXPatcher.patchXCI || Mounting overwritten content: " + ow_add.size() + " archives found");
		for(DirectoryNode dn : ow_add){
			String dirname = dn.getMetadataValue(META_KEY_MOUNTPART);
			mountToDir(root, dn, dirname);
		}
		
		//Handle the merges
		List<PatchworkFileNode> patchwork_list = new LinkedList<PatchworkFileNode>();
		if(verbose) System.err.println("NXPatcher.patchXCI || Handling merges: " + merge_map.size() + " archives found");
		FileBuffer xcidat = FileBuffer.createBuffer(xci_path, false);
		for(String p_id : merge_map.keySet()){
			String b_id = merge_map.get(p_id);
			
			if(verbose) System.err.println("NXPatcher.patchXCI || Merging " + p_id + " to " + b_id);
			FileNode p_node = patch_nca_nodes.get(p_id);
			FileNode b_node = base_nca_nodes.get(b_id);
			SwitchNCA p_nca = patch_ncas.get(p_id);
			SwitchNCA b_nca = base_ncas.get(b_id);
			String mdir = b_nca.getContainerName();
			
			for(int i = 0; i < 4; i++){
				//Check for patch partition
				NXNCAPart part = p_nca.getPartition(i);
				if(part == null) continue;
				long p_off = p_node.getOffset() + part.getOffset();
				
				if(part.isBKTRPartition()){
					//Oh boy! The FUN PART
					if(verbose) System.err.println("NXPatcher.patchXCI || Partition " + i + " is BKTR partition. Generating patchwork...");
					
					NXNCAPart bpart = b_nca.getPartition(i);
					if(bpart == null){
						System.err.println("NXPatcher.patchXCI || ERROR: Matching partition in base image not found! Partition patch failed...");
						continue;
					}
					
					part.readPatchInfo(patch_pfs_path, p_off);
					NXPatchInfo pinfo = part.getPatchData();
					Collection<FileCryptRecord> crecs = pinfo.addEncryptionInfo(part.getKey(), part.genCTR(0L), ctbl);
					for(FileCryptRecord crec : crecs) crec.setCryptOffset(crec.getCryptOffset() + p_off);
					
					FileNode pp_node = p_node.getSubFile(part.getOffset(), part.getSize());
					FileNode bp_node = b_node.getSubFile(bpart.getOffset(), bpart.getSize());
					
					PatchworkFileNode pfn = pinfo.generatePatchedImage(bp_node, pp_node);
					patchwork_list.add(pfn);
					FileBuffer pdat = pfn.loadData();
					
					//Parse back in...
					DirectoryNode patched_tree = null;
					long datoff = part.getDataOffset();
					//Determine if original image was RomFS or PFS
					if(bpart.isRomFS()){
						NXRomFS romfs = NXRomFS.readNXRomFSHeader(pdat, datoff);
						romfs.readTree(pdat, datoff, 0L);
						patched_tree = romfs.getFileTree();
					}
					else{
						NXPFS pfs = NXPFS.readPFS(pdat, datoff);
						patched_tree = pfs.getFileTree();
					}
					
					//Set source
					patched_tree.incrementTreeOffsetsBy(datoff);
					patched_tree.doForTree(new FileNodeModifierCallback(){

						public void doToNode(FileNode node) {
							node.setUseVirtualSource(true);
							node.setVirtualSourceNode(pfn);
						}
						
					});
					
					mountToDir(root, patched_tree, mdir);
				}
				else{
					if(verbose) System.err.println("NXPatcher.patchXCI || Partition " + i + " is not a BKTR partition. Merging/overwriting base partition....");
					//Mount patch files...
					part.buildFileTree(patchdat, p_off, false);
					DirectoryNode parttree = part.getFileTree();
					parttree.incrementTreeOffsetsBy(p_off);
					parttree.setSourcePathForTree(patch_pfs_path);
					
					//Add any files in base but not in patch
					NXNCAPart bpart = b_nca.getPartition(i);
					if(bpart != null){
						long b_off = b_node.getOffset() + bpart.getOffset();
						bpart.buildFileTree(xcidat, b_off, false);
						DirectoryNode btree = bpart.getFileTree();
						btree.incrementTreeOffsetsBy(b_off);
						btree.setSourcePathForTree(xci_path);
						
						Collection<FileNode> allb = btree.getAllDescendants(false);
						for(FileNode bfile : allb){
							if(parttree.getNodeAt(bfile.getFullPath()) == null){
								parttree.addChildAt(bfile.getFullPath(), bfile);
							}
						}
					}
					
					//Mount merged dir back onto tree
					mountToDir(root, parttree, mdir);
				}
			}
			
		}
		
		//Mount files in the patch PFS that AREN'T NCAs (like the tickets or whatever)
		String pid = null;
		String[] pathsplit = patch_pfs_path.split(File.separator);
		if(pathsplit != null){
			for(String s : pathsplit){
				if(s.endsWith(".nsp")){
					pid = s.substring(0, s.lastIndexOf('.'));
					break;
				}
			}
		}
		String mdir = "patch";
		if(pid != null) mdir += "_" + pid;
		mountToDir(root, patch_other, mdir);
		if(verbose) System.err.println("NXPatcher.patchXCI || Loose patch files saved to /" + mdir);
		
		//Some cleanup
		decer.clearTempFiles();
		StaticDecryption.setDecryptorState(NXSysDefs.getCTRCryptoDef().getID(), null);
		
		//Find the version string (in a control.nacp)
		String pver = null;
		String npath = root.findNodeThat(new NodeMatchCallback(){

			public boolean meetsCondition(FileNode n) {
				if(n.isDirectory()) return false;
				return n.getFileName().equals("control.nacp");
			}
			
		});
		if(npath != null){
			FileNode nacpnode = root.getNodeAt(npath);
			if(nacpnode != null){
				NXNACP nacp = NXNACP.readNACP(nacpnode.loadData(), 0);
				pver = nacp.getVersionString();
			}
		}
		if(pver != null && verbose) System.err.println("NXPatcher.patchXCI || Version string found: " + pver);
		
		//Put it all together
		PatchedInfo pi = new PatchedInfo();
		pi.crypt_table = ctbl;
		pi.newroot = root;
		pi.patched_ver = pver;
		pi.patched_romfs = patchwork_list;
		
		return pi;
	}
	
	public static PatchedInfo patchXCI_lowfs(String xci_path, String patch_pfs_path, NXCrypt crypto) throws IOException, UnsupportedFileTypeException{
		//This one just mounts the patch to a new directory on the tree...
		
		//Open base image...
		NXCartImage xci = NXCartImage.readXCI(xci_path);
		xci.unlock(crypto);
		DirectoryNode root = xci.getFileTree(NXCartImage.TREEBUILD_COMPLEXITY_ALL);
		NinCryptTable ctbl = xci.generateCryptTable();
		
		//Open patch...
		FileBuffer patchdat = FileBuffer.createBuffer(patch_pfs_path, false);
		NXPFS patch = NXPFS.readPFS(patchdat, 0L);
		DirectoryNode patchroot = new DirectoryNode(root, "patch");
		
		//Read patch ncas...
		List<FileNode> flist = patch.getFileList();
		for(FileNode f : flist){
			//If it doesn't end in .nca, it will be mounted elsewhere (later)
			String fname = f.getFileName();
			f.setSourcePath(patch_pfs_path);
			if(fname.endsWith(".nca")){
				SwitchNCA nca_patch = SwitchNCA.readNCA(patchdat, f.getOffset());
				nca_patch.unlock(crypto);
				
				Collection<FileCryptRecord> crecs = nca_patch.addEncryptionInfo(ctbl);
				for(FileCryptRecord crec : crecs) crec.setCryptOffset(crec.getCryptOffset() + f.getOffset());
				
				nca_patch.buildFileTree(patchdat, f.getOffset(), NXCartImage.TREEBUILD_COMPLEXITY_ALL);
				DirectoryNode ncatree = nca_patch.getFileTree();
				ncatree.setSourcePath(patch_pfs_path);
				ncatree.incrementTreeOffsetsBy(f.getOffset());
				ncatree.setFileName(fname);
				ncatree.setParent(patchroot);
			}
		}
		
		//Get the patch package name to rename the directory...
		String pid = null;
		String[] pathsplit = patch_pfs_path.split(File.separator);
		if(pathsplit != null){
			for(String s : pathsplit){
				if(s.endsWith(".nsp")){
					pid = s.substring(0, s.lastIndexOf('.'));
					break;
				}
			}
		}
		if(pid != null) patchroot.setFileName("patch_" + pid);
		
		//Get the patch version string... (look for a control.nacp in the patch)
		String pver = null;
		String npath = patchroot.findNodeThat(new NodeMatchCallback(){

			public boolean meetsCondition(FileNode n) {
				if(n.isDirectory()) return false;
				return n.getFileName().equals("control.nacp");
			}
			
		});
		if(npath != null){
			FileNode nacpnode = patchroot.getNodeAt(npath);
			if(nacpnode != null){
				NXNACP nacp = NXNACP.readNACP(nacpnode.loadData(), 0);
				pver = nacp.getVersionString();
			}
		}
		
		PatchedInfo pi = new PatchedInfo();
		pi.crypt_table = ctbl;
		pi.newroot = root;
		pi.patched_ver = pver;
		
		return pi;
	}
	
	public static DirectoryNode mountDLC(String dlc_pfs_path, NXCrypt crypto) throws IOException, UnsupportedFileTypeException{
		//TODO
		return null;
	}

}
