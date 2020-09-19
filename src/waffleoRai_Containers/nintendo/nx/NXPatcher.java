package waffleoRai_Containers.nintendo.nx;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import waffleoRai_Containers.nintendo.nx.SwitchNCA.IVFCHashInfo;
import waffleoRai_Encryption.AES;
import waffleoRai_Encryption.FileCryptRecord;
import waffleoRai_Encryption.StaticDecryption;
import waffleoRai_Encryption.nintendo.NinCryptTable;
import waffleoRai_Encryption.nintendo.NinCrypto;
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
	
	private static class PatcherContext{

		public static final String META_KEY_MOUNTPART = "mountncato";
		
		String xci_path;
		String patch_pfs_path;
		
		NXCrypt crypto;
		NXDecryptor decer;
		
		NXPFS patch;
		FileBuffer patchdat;
		DirectoryNode patch_other;
		
		Map<String, SwitchNCA> base_ncas;
		Map<String, FileNode> base_nca_nodes;
		Map<String, SwitchNCA> patch_ncas;
		Map<String, FileNode> patch_nca_nodes;
		
		List<String> cmnt_ids;
		Map<String, NXContentMeta> base_meta_map;
		
		NXCartImage xci;
		FileBuffer xcidat;
		
		NinCryptTable ctbl;
		DirectoryNode root;
		
		List<DirectoryNode> patch_add;
		Map<String, String> merge_map;
		List<DirectoryNode> ow_add;
		
		FileNode cnmtnode;
		NXContentMeta cnmt;
		
		public PatcherContext(String bpath, String ppath, NXCrypt crypt){
			
			xci_path = bpath;
			patch_pfs_path = ppath;
			crypto = crypt;
			
			//Maps
			//This is sloppy and I don't like it but I'm too lazy to think up something better
			base_ncas = new HashMap<String, SwitchNCA>();
			patch_ncas = new HashMap<String, SwitchNCA>();
			base_nca_nodes = new HashMap<String, FileNode>();
			patch_nca_nodes = new HashMap<String, FileNode>();
			
			cmnt_ids = new LinkedList<String>();
			base_meta_map = new HashMap<String, NXContentMeta>();
			
			patch_add = new LinkedList<DirectoryNode>(); //Denovo additions to root (create mode)
			merge_map = new HashMap<String, String>(); //NCA ID -> NCA ID of NCAs to merge
			ow_add = new LinkedList<DirectoryNode>(); //NCA trees to mount for overwrites. Meta field indicates partition.
		}
		
	}
	
	private static void mountToDir(DirectoryNode src, DirectoryNode targ, String dirname){
		//System.err.println("Mounting new directory to " + dirname);
		
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
	
	private static void openBaseImage(PatcherContext ctx, boolean verbose) throws IOException, UnsupportedFileTypeException{
		ctx.xci = NXCartImage.readXCI(ctx.xci_path);
		ctx.xci.unlock(ctx.crypto);
		ctx.ctbl = ctx.xci.generateCryptTable();
		ctx.root = ctx.xci.getFileTree(NXUtils.TREEBUILD_COMPLEXITY_MERGED);
		ctx.xci.mapNCAs(ctx.base_ncas);
		ctx.xci.mapNCANodes(ctx.base_nca_nodes);
		if(verbose) System.err.println("NXPatcher.patchXCI || XCI NCAs mapped. NCAs found: " + ctx.base_ncas.size());
	}
	
	private static void openPatchImage(PatcherContext ctx, boolean verbose) throws IOException, UnsupportedFileTypeException{
		ctx.patchdat = FileBuffer.createBuffer(ctx.patch_pfs_path, false);
		ctx.patch = NXPFS.readPFS(ctx.patchdat, 0L);
		//Read patch ncas...
		List<FileNode> flist = ctx.patch.getFileList();
		ctx.patch_other = new DirectoryNode(null, "");
		for(FileNode f : flist){
			//If it doesn't end in .nca, it will be mounted elsewhere (later)
			String fname = f.getFileName();
			f.setSourcePath(ctx.patch_pfs_path);
			if(fname.endsWith(".nca")){
				String id = fname.substring(0, fname.indexOf('.'));	
				SwitchNCA nca_patch = SwitchNCA.readNCA(ctx.patchdat, f.getOffset());
				nca_patch.unlock(ctx.crypto);
				ctx.patch_ncas.put(id, nca_patch);
				ctx.patch_nca_nodes.put(id, f);
				if(fname.contains(".cnmt")) ctx.cmnt_ids.add(id);
				
				//Add general patch partition records to crypt table
				//Note that the PFS getFileList() methods takes care of adding PFS filedata offset
				Collection<FileCryptRecord> crecs = nca_patch.addEncryptionInfo(ctx.ctbl);
				for(FileCryptRecord crec : crecs) crec.setCryptOffset(crec.getCryptOffset() + f.getOffset());
			}
			else f.setParent(ctx.patch_other);
		}
		if(verbose) System.err.println("NXPatcher.patchXCI || Patch NCAs mapped. NCAs found: " + ctx.patch_ncas.size());
		if(verbose) System.err.println("NXPatcher.patchXCI || Content Metadata NCAs found: " + ctx.cmnt_ids.size());
	}
	
	private static void mapBaseMetas(PatcherContext ctx, boolean verbose) throws IOException{
		Collection<FileNode> cnmt_nodes = ctx.root.getNodesThat(new NodeMatchCallback(){

			public boolean meetsCondition(FileNode n) {
				if(n.isDirectory()) return false;
				return n.getFileName().endsWith(".cnmt");
			}
			
		});
		for(FileNode cnmtnode : cnmt_nodes){
			if(verbose) System.err.println("NXPatcher.patchXCI || Base CNMT Found: " + cnmtnode.getFullPath());
			FileBuffer dat = cnmtnode.loadData();
			NXContentMeta cnmt = NXContentMeta.readCMNT(dat, 0);
			ctx.base_meta_map.put(Long.toHexString(cnmt.getID()), cnmt);
		}
	}
	
	private static void processUpdateFragment(PatcherContext ctx, NXContentMeta.FragmentSet fs, String myid, String mount_dir, boolean verbose){
		
		//Match target ID to base application ID and find CNMT from base
		String appid_str = Long.toHexString(ctx.cnmt.getAppID());
		NXContentMeta base_meta = ctx.base_meta_map.get(appid_str);
		if(base_meta == null){
			if(verbose) {System.err.println("NXPatcher.patchXCI || Warning: AppID from cnmt " + ctx.cnmtnode.getFileName() + 
							" (" + appid_str + ") could not be matched to an AppID in base archive. Skipping...");}
				return;
			}
		List<NXContentMeta.ContentInfo> base_contents = base_meta.getContents();
		if(verbose) System.err.println("NXPatcher.patchXCI || AppID Match Found - AppID: " + appid_str);
		
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
			long tsize = ctx.cnmt.getPatchTargetOriginalSize(myid);
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
		
		FileNode pnca_node = ctx.patch_nca_nodes.get(myid); //We'll need the NCA offset...
		SwitchNCA p_nca = ctx.patch_ncas.get(myid);
		
		//If still no match, just mount denovo
		if(match == null){
			if(verbose) System.err.println("NXPatcher.patchXCI || No patch target was found. Mounting de novo to " + mount_dir);
			
			//Get NCA and load tree
			p_nca.buildFileTree(ctx.patchdat, pnca_node.getOffset(), NXUtils.TREEBUILD_COMPLEXITY_MERGED);
			DirectoryNode mytree = p_nca.getFileTree();
			//Increment offset of tree (and note path)
			mytree.incrementTreeOffsetsBy(pnca_node.getOffset());
			mytree.setSourcePathForTree(ctx.patch_pfs_path);
			mytree.setMetadataValue(PatcherContext.META_KEY_MOUNTPART, mount_dir);	
			//Put in list
			ctx.patch_add.add(mytree);
		}
		else{
			if(verbose) System.err.println("NXPatcher.patchXCI || Patch target found: " + match);
			
			//Dismount target
			Collection<SwitchNCA> b_ncas = ctx.xci.removeNCA(match);
			
			//Note in appropriate struct
			if(fs.update_type == 0){
				//Merge
				ctx.merge_map.put(myid, match);
			}
			else{
				p_nca.buildFileTree(ctx.patchdat, pnca_node.getOffset(), NXUtils.TREEBUILD_COMPLEXITY_MERGED);
				DirectoryNode mytree = p_nca.getFileTree();
				//System.err.println("pnca offset: 0x" + Long.toHexString(pnca_node.getOffset()));
				//mytree.printMeToStdErr(0);
				//Increment offset of tree (and note path)
				mytree.incrementTreeOffsetsBy(pnca_node.getOffset());
				mytree.setSourcePathForTree(ctx.patch_pfs_path);
				
				//Note partition
				Set<String> cnames = new HashSet<String>();
				for(SwitchNCA nca : b_ncas) cnames.add(nca.getContainerName());
				if(cnames.size() == 1){
					for(String cn : cnames) mytree.setMetadataValue(PatcherContext.META_KEY_MOUNTPART, cn);
				}
				else{
					//Prioritize specific part names
					cnames.remove("normal");
					cnames.remove("logo");
					if(cnames.isEmpty()){
						//Was just normal and logo (unlikely). Put in normal.
						mytree.setMetadataValue(PatcherContext.META_KEY_MOUNTPART, "normal");
					}
					else{
						//Just pick the first one.
						for(String cn : cnames) {mytree.setMetadataValue(PatcherContext.META_KEY_MOUNTPART, cn); break;}
					}
				}
				
				//Add to list
				ctx.ow_add.add(mytree);
			}
		}
	}
	
	private static void processCreateFragment(PatcherContext ctx, String myid, String mount_dir, boolean verbose){
		if(verbose) System.err.println("NXPatcher.patchXCI || New patch content. Mounting to: " + mount_dir);
		FileNode pnca_node2 = ctx.patch_nca_nodes.get(myid);
		SwitchNCA p_nca2 = ctx.patch_ncas.get(myid);
		p_nca2.buildFileTree(ctx.patchdat, pnca_node2.getOffset(), NXUtils.TREEBUILD_COMPLEXITY_MERGED);
		DirectoryNode mytree = p_nca2.getFileTree();
		//Increment offset of tree (and note path)
		mytree.incrementTreeOffsetsBy(pnca_node2.getOffset());
		mytree.setSourcePathForTree(ctx.patch_pfs_path);
		mytree.setMetadataValue(PatcherContext.META_KEY_MOUNTPART, mount_dir);						
		//Put in list
		ctx.patch_add.add(mytree);
	}
	
	private static String findCNMTTargets(PatcherContext ctx, boolean verbose) throws IOException{
		if(verbose) System.err.println("NXPatcher.patchXCI || Patch CNMT Found: " + ctx.cnmtnode.getFileName());
		
		//Load and parse the CNMT file
		FileBuffer dat = ctx.cnmtnode.loadData();
		ctx.cnmt = NXContentMeta.readCMNT(dat, 0);
		
		//If not patch, skip.
		if(ctx.cnmt.getMetaType() != NXContentMeta.CNMT_TYPE_PATCH){
			if(verbose) System.err.println("NXPatcher.patchXCI || Warning: CNMT is not a patch CNMT. Skipping...");
			return null;
		}
		
		String mount_dir = "patch_" + String.format("%016x", ctx.cnmt.getID());
		
		//Iterate through the fragment sets...
		List<NXContentMeta.FragmentSet> fragsets = ctx.cnmt.getFragmentSets();
		for(NXContentMeta.FragmentSet fs : fragsets){
			//Note the ID of the patch content NCA...
			String myid = AES.bytes2str(fs.dest_content_id);
			if(verbose) System.err.println("NXPatcher.patchXCI || Patch Content Found: " + myid);
			
			switch(fs.update_type){
			case 0: //Merge
			case 1: //Overwrite
				processUpdateFragment(ctx, fs, myid, mount_dir, verbose);
				break;
			case 2: //Create
				processCreateFragment(ctx, myid, mount_dir, verbose);
				break;
			}
		}
		
		return String.format("%016x", ctx.cnmt.getID());
	}
	
	private static void determinePatchTargets(PatcherContext ctx, boolean verbose) throws IOException{
		for(String s : ctx.cmnt_ids){
			//For each CMNT in the patch...
			if(verbose) System.err.println("NXPatcher.patchXCI || Examining patch meta NCA: " + s);
			
			//Get the NCA and its tree from the patch
			FileNode nca_node = ctx.patch_nca_nodes.get(s); //We'll need the NCA offset...
			SwitchNCA cmnt_nca = ctx.patch_ncas.get(s);
			cmnt_nca.buildFileTree(ctx.patchdat, nca_node.getOffset(), NXUtils.TREEBUILD_COMPLEXITY_MERGED);
			DirectoryNode cmnt_nca_tree = cmnt_nca.getFileTree();
			cmnt_nca_tree.setSourcePathForTree(ctx.patch_pfs_path);
			cmnt_nca_tree.incrementTreeOffsetsBy(nca_node.getOffset());
				
			//It SHOULD only contain one CNMT, but we'll do "all" just because it's not any harder
			Collection<FileNode> cnmt_nodes = cmnt_nca_tree.getNodesThat(new NodeMatchCallback(){

				public boolean meetsCondition(FileNode n) {
					if(n.isDirectory()) return false;
					return n.getFileName().endsWith(".cnmt");
				}
				
			});

			for(FileNode cnmtnode : cnmt_nodes){
				ctx.cnmtnode = cnmtnode;
				String cid = findCNMTTargets(ctx, verbose);
				
				//Add the cmnt NCA itself to the add queue
				String mount_dir = "patch_" + cid;
				cmnt_nca_tree.setMetadataValue(PatcherContext.META_KEY_MOUNTPART, mount_dir);	
				ctx.patch_add.add(cmnt_nca_tree);
			}
			
			ctx.cnmtnode = null;
			ctx.cnmt = null;
		}
	}
	
	private static void handleMerges(PatcherContext ctx, List<PatchworkFileNode> patchwork_list, boolean verbose) throws IOException, UnsupportedFileTypeException{
		if(verbose) System.err.println("NXPatcher.patchXCI || Handling merges: " + ctx.merge_map.size() + " archives found");
		ctx.xcidat = FileBuffer.createBuffer(ctx.xci_path, false);
		for(String p_id : ctx.merge_map.keySet()){
			String b_id = ctx.merge_map.get(p_id);
			
			if(verbose) System.err.println("NXPatcher.patchXCI || Merging " + p_id + " to " + b_id);
			FileNode p_node = ctx.patch_nca_nodes.get(p_id);
			FileNode b_node = ctx.base_nca_nodes.get(b_id);
			SwitchNCA p_nca = ctx.patch_ncas.get(p_id);
			SwitchNCA b_nca = ctx.base_ncas.get(b_id);
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
					
					part.readPatchInfo(ctx.patch_pfs_path, p_off);
					NXPatchInfo pinfo = part.getPatchData();
					Collection<FileCryptRecord> crecs = pinfo.addEncryptionInfo(part.getKey(), part.genCTR(0L), ctx.ctbl);
					for(FileCryptRecord crec : crecs) crec.setCryptOffset(crec.getCryptOffset() + p_off);
					
					FileNode pp_node = p_node.getSubFile(part.getOffset(), part.getSize());
					FileNode bp_node = b_node.getSubFile(bpart.getOffset(), bpart.getSize());
					bp_node.setMetadataValue(NinCrypto.METAKEY_CRYPTGROUPUID, Long.toHexString(bpart.updateCryptRegUID()));
					pp_node.addEncryption(NXSysDefs.getCTRCryptoDef());
					bp_node.addEncryption(NXSysDefs.getCTRCryptoDef());
					
					//System.err.println("--DEBUG-- Patch Part Location: " + pp_node.getSourcePath() + " @ " + pp_node.getLocationString());
					//System.err.println("--DEBUG-- Base Part Location: " + bp_node.getSourcePath() + " @ " + bp_node.getLocationString());
					
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
					
					mountToDir(patched_tree, ctx.root, mdir);
					
					//DEBUG!!!
					/*System.err.println("--DEBUG-- Validating patched image integrity (this may take a while)...");
					FSHashInfo hinfo = part.getHashInfo();
					if(hinfo instanceof IVFCHashInfo){
						checkPatchedRomFSIntegrity(pfn, (IVFCHashInfo)hinfo);
					}
					else System.err.println("--DEBUG-- Hash table is not IVFC. Check failed.");*/
					
				}
				else{
					if(verbose) System.err.println("NXPatcher.patchXCI || Partition " + i + " is not a BKTR partition. Merging/overwriting base partition....");
					//Mount patch files...
					part.buildFileTree(ctx.patchdat, p_off, false);
					DirectoryNode parttree = part.getFileTree();
					parttree.incrementTreeOffsetsBy(p_off);
					parttree.setSourcePathForTree(ctx.patch_pfs_path);
					
					//Add any files in base but not in patch
					NXNCAPart bpart = b_nca.getPartition(i);
					if(bpart != null){
						long b_off = b_node.getOffset() + bpart.getOffset();
						bpart.buildFileTree(ctx.xcidat, b_off, false);
						DirectoryNode btree = bpart.getFileTree();
						btree.incrementTreeOffsetsBy(b_off);
						btree.setSourcePathForTree(ctx.xci_path);
						
						Collection<FileNode> allb = btree.getAllDescendants(false);
						for(FileNode bfile : allb){
							if(parttree.getNodeAt(bfile.getFullPath()) == null){
								parttree.addChildAt(bfile.getFullPath(), bfile);
							}
						}
					}
					
					//Mount merged dir back onto tree
					mountToDir(parttree, ctx.root, mdir);
				}
			}
			
		}
	}
	
	public static boolean checkPatchedRomFSIntegrity(PatchworkFileNode pfn, IVFCHashInfo hashinfo) throws IOException{
		return hashinfo.validateHashes(pfn.loadData(), 0, true);
	}
	
	public static PatchedInfo patchXCI(String xci_path, String patch_pfs_path, NXCrypt crypto, boolean verbose) throws IOException, UnsupportedFileTypeException{
		
		//Initialize
		PatcherContext ctx = new PatcherContext(xci_path, patch_pfs_path, crypto);
		
		//Open base image & map NCAs...
		openBaseImage(ctx, verbose);
		
		//Open patch...
		openPatchImage(ctx, verbose);
		
		//Load decryptor into memory so internal files can be read...
		ctx.decer = new NXDecryptor(ctx.ctbl, FileBuffer.getTempDir());
		StaticDecryption.setDecryptorState(NXSysDefs.getCTRCryptoDef().getID(), ctx.decer);
		
		//Load CNMTs from base XCI and map by content ID...
		mapBaseMetas(ctx, verbose);
		
		//Go through the patch metas and determine which NCAs go where...
		determinePatchTargets(ctx, verbose);
		
		//Regenerate xci tree now that nca's to replace have been dismounted
		if(verbose) System.err.println("NXPatcher.patchXCI || Regenerating base tree without dismounted NCAs...");
		ctx.root = ctx.xci.getFileTree(NXUtils.TREEBUILD_COMPLEXITY_MERGED);
		//root.printMeToStdErr(0);
		
		//Mount the novels and overwrites...
		if(verbose) System.err.println("NXPatcher.patchXCI || Mounting new content: " + ctx.patch_add.size() + " archives found");
		for(DirectoryNode dn : ctx.patch_add){
			String dirname = dn.getMetadataValue(PatcherContext.META_KEY_MOUNTPART);
			mountToDir(dn, ctx.root, dirname);
		}
		if(verbose) System.err.println("NXPatcher.patchXCI || Mounting overwritten content: " + ctx.ow_add.size() + " archives found");
		for(DirectoryNode dn : ctx.ow_add){
			String dirname = dn.getMetadataValue(PatcherContext.META_KEY_MOUNTPART);
			mountToDir(dn, ctx.root, dirname);
		}
		
		//Handle the merges
		List<PatchworkFileNode> patchwork_list = new LinkedList<PatchworkFileNode>();
		handleMerges(ctx, patchwork_list, verbose);
		
		//Mount files in the patch PFS that AREN'T NCAs (like the tickets or whatever)
		String pid = null;
		String apath = patch_pfs_path.replace('\\', '/');
		String[] pathsplit = apath.split("/");
		if(pathsplit != null){
			for(String s : pathsplit){
				if(s.endsWith(".nsp")){
					pid = s.substring(0, s.lastIndexOf('.'));
					break;
				}
			}
		}
		String mdir = "patch";
		if(pid != null) mdir += "_" + pid.toLowerCase();
		mountToDir(ctx.patch_other, ctx.root, mdir);
		if(verbose) System.err.println("NXPatcher.patchXCI || Loose patch files saved to /" + mdir);
			
		//Find the version string (in a control.nacp)
		String pver = null;
		String npath = ctx.root.findNodeThat(new NodeMatchCallback(){

			public boolean meetsCondition(FileNode n) {
				if(n.isDirectory()) return false;
				return n.getFileName().equals("control.nacp");
			}
			
		});
		if(npath != null){
			FileNode nacpnode = ctx.root.getNodeAt(npath);
			//System.err.println("NACP at " + nacpnode.getSourcePath() + " @ " + nacpnode.getLocationString());
			//System.err.println("Crypt ID: " + nacpnode.getMetadataValue(NinCrypto.METAKEY_CRYPTGROUPUID));
			if(nacpnode != null){
				//nacpnode.loadData().writeFile("C:\\Users\\Blythe\\Documents\\Desktop\\out\\nxtest\\control_test.nacp");
				NXNACP nacp = NXNACP.readNACP(nacpnode.loadData(), 0);
				pver = nacp.getVersionString();
			}
		}
		if(pver != null && verbose) System.err.println("NXPatcher.patchXCI || Version string found: " + pver);
		
		//Some cleanup
		ctx.decer.clearTempFiles();
		StaticDecryption.setDecryptorState(NXSysDefs.getCTRCryptoDef().getID(), null);
		
		//Put it all together
		PatchedInfo pi = new PatchedInfo();
		pi.crypt_table = ctx.ctbl;
		pi.newroot = ctx.root;
		pi.patched_ver = pver;
		pi.patched_romfs = patchwork_list;
		//ctbl.printMe();
		
		return pi;
	}
	
	public static PatchedInfo patchXCI_lowfs(String xci_path, String patch_pfs_path, NXCrypt crypto) throws IOException, UnsupportedFileTypeException{
		//This one just mounts the patch to a new directory on the tree...
		
		//Open base image...
		NXCartImage xci = NXCartImage.readXCI(xci_path);
		xci.unlock(crypto);
		DirectoryNode root = xci.getFileTree(NXUtils.TREEBUILD_COMPLEXITY_ALL);
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
				
				nca_patch.buildFileTree(patchdat, f.getOffset(), NXUtils.TREEBUILD_COMPLEXITY_ALL);
				DirectoryNode ncatree = nca_patch.getFileTree();
				ncatree.setSourcePath(patch_pfs_path);
				ncatree.incrementTreeOffsetsBy(f.getOffset());
				ncatree.setFileName(fname);
				ncatree.setParent(patchroot);
			}
		}
		
		//Get the patch package name to rename the directory...
		String pid = null;
		String apath = patch_pfs_path.replace('\\', '/');
		String[] pathsplit = apath.split("/");
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
	
	public static DirectoryNode mountDLC(DirectoryNode root, NinCryptTable ctbl, String dlc_pfs_path, NXCrypt crypto, String dlc_node_tag) throws IOException, UnsupportedFileTypeException{

		//Determine the default content ID
		String defo_id = null;
		String apath = dlc_pfs_path.replace('\\', '/');
		String[] pathsplit = apath.split("/");
		if(pathsplit != null){
			for(String s : pathsplit){
				if(s.endsWith(".nsp")){
					defo_id = s.substring(0, s.lastIndexOf('.')).toLowerCase();
					break;
				}
			}
		}
		if(defo_id == null) defo_id = "";
		
		//Open DLC package
		FileBuffer patchdat = FileBuffer.createBuffer(dlc_pfs_path, false);
		NXPFS patch = NXPFS.readPFS(patchdat, 0L);
		//Read patch ncas...
		List<FileNode> flist = patch.getFileList();
		Map<String, SwitchNCA> patch_ncas = new HashMap<String, SwitchNCA>();
		Map<String, FileNode> patch_nca_nodes = new HashMap<String, FileNode>();
		List<String> cmnt_ids = new LinkedList<String>();
		DirectoryNode patch_other = new DirectoryNode(null, "");
		for(FileNode f : flist){
			String fname = f.getFileName();
			f.setSourcePath(dlc_pfs_path);
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
			else{
				f.setParent(patch_other);
				f.setMetadataValue(NXUtils.METAKEY_DLCID, dlc_node_tag);
			}
			//f.setMetadataValue(NXUtils.METAKEY_DLCID, defo_id);
		}
		
		NXDecryptor decer = new NXDecryptor(ctbl, FileBuffer.getTempDir());
		StaticDecryption.setDecryptorState(NXSysDefs.getCTRCryptoDef().getID(), decer);
		
		//Look for CNMTs
		for(String cnmt_nca_id : cmnt_ids){
			FileNode cnmt_nca_node = patch_nca_nodes.get(cnmt_nca_id);
			SwitchNCA cnmt_nca = patch_ncas.get(cnmt_nca_id);
			cnmt_nca.buildFileTree(patchdat, cnmt_nca_node.getOffset(), NXUtils.TREEBUILD_COMPLEXITY_MERGED);
			DirectoryNode cmnt_nca_tree = cnmt_nca.getFileTree();
			cmnt_nca_tree.setSourcePathForTree(dlc_pfs_path);
			cmnt_nca_tree.incrementTreeOffsetsBy(cnmt_nca_node.getOffset());
			
			Collection<FileNode> cnmt_nodes = cmnt_nca_tree.getNodesThat(new NodeMatchCallback(){

				public boolean meetsCondition(FileNode n) {
					if(n.isDirectory()) return false;
					return n.getFileName().endsWith(".cnmt");
				}
				
			});

			for(FileNode cnmtnode : cnmt_nodes){
				NXContentMeta cnmt = NXContentMeta.readCMNT(cnmtnode.loadData(), 0);
				String cid = String.format("%016x", cnmt.getID());
				if(defo_id == null) defo_id = cid;
				String tdir = "dlc_" + cid;
				
				//Mount all NCAs associated with this meta
				List<NXContentMeta.ContentInfo> cilist = cnmt.getContents();
				for(NXContentMeta.ContentInfo ci : cilist){
					String strid = AES.bytes2str(ci.contentID);
					//Search for an NCA by that name
					SwitchNCA thisnca = patch_ncas.get(strid);
					FileNode thisncanode = patch_nca_nodes.get(strid);
					thisnca.buildFileTree(thisncanode.loadData(), 0, NXUtils.TREEBUILD_COMPLEXITY_MERGED);
					DirectoryNode ncatree = thisnca.getFileTree();
					ncatree.setSourcePathForTree(dlc_pfs_path);
					ncatree.incrementTreeOffsetsBy(thisncanode.getOffset());
					
					ncatree.setMetaValueForTree(NXUtils.METAKEY_DLCID, dlc_node_tag);
					mountToDir(ncatree, root, tdir);
				}
			}
			
		}
		
		mountToDir(patch_other, root, "dlc_" + defo_id);
		
		decer.clearTempFiles();
		StaticDecryption.setDecryptorState(NXSysDefs.getCTRCryptoDef().getID(), null);
		
		return root;
	}

}
