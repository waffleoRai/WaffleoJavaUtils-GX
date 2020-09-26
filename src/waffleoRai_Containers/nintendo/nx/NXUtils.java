package waffleoRai_Containers.nintendo.nx;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collection;

import javax.imageio.ImageIO;

import waffleoRai_Encryption.StaticDecryption;
import waffleoRai_Encryption.StaticDecryptor;
import waffleoRai_Encryption.nintendo.NinCryptTable;
import waffleoRai_Files.EncryptionDefinitions;
import waffleoRai_Files.NodeMatchCallback;
import waffleoRai_Files.tree.DirectoryNode;
import waffleoRai_Files.tree.FileNode;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_fdefs.nintendo.NXSysDefs;

public class NXUtils {
	
	public static final int LANIDX_AMENG = 0; //American English
	public static final int LANIDX_BRENG = 1; //British English
	public static final int LANIDX_JPN = 2; //Japanese
	public static final int LANIDX_EUFRN = 3; //European French
	public static final int LANIDX_GMN = 4; //German
	public static final int LANIDX_AMESP = 5; //Latin American Spanish
	public static final int LANIDX_EUESP = 6; //European Spanish
	public static final int LANIDX_ITL = 7; //Italian
	public static final int LANIDX_DCH = 8; //Dutch
	public static final int LANIDX_CNFRN = 9; //Canadian French
	public static final int LANIDX_PRT = 10; //Portugese
	public static final int LANIDX_RUS = 11; //Russian
	public static final int LANIDX_KOR = 12; //Korean
	public static final int LANIDX_TRCHN = 13; //Traditional Chinese
	public static final int LANIDX_SMCHN = 14; //Simplified Chinese
	public static final int LAN_SLOT_COUNT = 15;
	
	public static final int[][] LAN_SCAN_PRIORITY = {{1}, //Am. Eng
													 {0}, //Br. Eng
													 {}, //Japanese
													 {9}, //French
													 {}, //German
													 {6}, //Am. Spanish
													 {5}, //Eu. Spanish
													 {}, //Italian
													 {}, //Dutch
													 {3}, //Cn. French
													 {}, //Eu. Portugese
													 {}, //Russian
													 {}, //Korean
													 {14}, //T. Chinese
													 {13}, //S. Chinese
													 {}}; 
	
	public static final String[] LANNAMES_ENG = {"American English", "British English", "Japanese", "French",
												 "German", "Latin American Spanish", "Spanish", "Italian",
												 "Dutch", "Canadian French", "Portugese", "Russian",
												 "Korean", "Traditional Chinese", "Simplified Chinese", "UNKNOWN"};
	
	public static final int CTRLSTR_IDX_TITLE = 0;
	public static final int CTRLSTR_IDX_PUBLISHER = 1;
	public static final int CTRLSTR_IDX_VERSION = 2;
	public static final int CTRLSTR_IDX_ERRCODE = 3;
	
	public static final int TREEBUILD_COMPLEXITY_ALL = 0;
	public static final int TREEBUILD_COMPLEXITY_NORMAL = 1;
	public static final int TREEBUILD_COMPLEXITY_MERGED = 2;
	
	public static final String METAKEY_DLCID = "NXDLCID";
	public static final String METAKEY_PARENTID = "MOUNTDIRID";
	
	private static String dec_temp_dir;
	
	public static void setActiveCryptTable(NinCryptTable ctbl){
		clearActiveCryptTable();
		NXDecryptor decer = new NXDecryptor(ctbl, getDecryptTempDir());
		
		int ctr_id = NXSysDefs.getCTRCryptoDef().getID();
		StaticDecryption.setDecryptorState(ctr_id, decer);
		StaticDecryption.setDecryptorState(NXSysDefs.getXTSCryptoDef().getID(), decer);
		
		//Register defs statically, if not done...
		if(EncryptionDefinitions.getByID(ctr_id) == null){
			EncryptionDefinitions.registerDefinition(NXSysDefs.getCTRCryptoDef());
			EncryptionDefinitions.registerDefinition(NXSysDefs.getXTSCryptoDef());
		}
	}
	
	public static void clearActiveCryptTable(){
		StaticDecryptor s_decer = StaticDecryption.getDecryptorState(NXSysDefs.getCTRCryptoDef().getID());
		if(s_decer != null){
			if(s_decer instanceof NXDecryptor){
				((NXDecryptor)s_decer).clearTempFiles();
			}
		}
		
		StaticDecryption.setDecryptorState(NXSysDefs.getCTRCryptoDef().getID(), null);
		StaticDecryption.setDecryptorState(NXSysDefs.getXTSCryptoDef().getID(), null);
	}
	
	private static Collection<FileNode> getNodesWithName(DirectoryNode pkg_tree, String name){
		Collection<FileNode> nodes = pkg_tree.getNodesThat(new NodeMatchCallback(){

			public boolean meetsCondition(FileNode n) {
				if(n.isDirectory()) return false;
				return n.getFileName().equals(name);
			}
			
		});
		return nodes;
	}
	
	public static String[] getControlStrings(DirectoryNode pkg_tree, int preferred_lan) throws IOException{
		//Look for "control.nacp"
		Collection<FileNode> nodes = getNodesWithName(pkg_tree, "control.nacp");
		
		//Adjust lan idx
		int lan = preferred_lan;
		if(lan < 0) lan = 0;
		if(lan >= LAN_SLOT_COUNT) lan = LAN_SLOT_COUNT-1;
		boolean[] lan_checked = new boolean[LAN_SLOT_COUNT];
		
		//Just use the first one that matches.
		if(nodes.isEmpty()) return null;
		for(FileNode n : nodes){
			/*System.err.println("NACP Node found: " + n.getFullPath());
			System.err.print("Source: ");
			if(n.hasVirtualSource()) System.err.print("<Virtual> @ ");
			else System.err.print(n.getSourcePath() + " @ ");
			System.err.print(n.getLocationString() + "\n");*/
			
			String[] strs = new String[4];
			NXNACP nacp = NXNACP.readNACP(n.loadData(), 0);
			//Get version and error code strings...
			strs[CTRLSTR_IDX_VERSION] = nacp.getVersionString();
			strs[CTRLSTR_IDX_ERRCODE] = nacp.getAppErrorString();
			
			//Check banner for preferred language
			String t = nacp.getTitleString(lan);
			if(t != null && !t.isEmpty()){
				strs[CTRLSTR_IDX_TITLE] = t;
				strs[CTRLSTR_IDX_PUBLISHER] = nacp.getPublisherString(lan);
				return strs;
			}
			lan_checked[lan] = true;
			
			//Check banner for "nearby" languages
			int[] nlan = LAN_SCAN_PRIORITY[lan];
			if(nlan != null && nlan.length > 0){
				for(int i = 0; i < nlan.length; i++){
					t = nacp.getTitleString(nlan[i]);
					if(t != null && !t.isEmpty()){
						strs[CTRLSTR_IDX_TITLE] = t;
						strs[CTRLSTR_IDX_PUBLISHER] = nacp.getPublisherString(nlan[i]);
						return strs;
					}
					lan_checked[nlan[i]] = true;
				}
			}
			
			//Check banner for other languages
			for(int i = 0; i < LAN_SLOT_COUNT; i++){
				if(lan_checked[i]) continue;
				t = nacp.getTitleString(i);
				if(t != null && !t.isEmpty()){
					strs[CTRLSTR_IDX_TITLE] = t;
					strs[CTRLSTR_IDX_PUBLISHER] = nacp.getPublisherString(i);
					return strs;
				}
				lan_checked[i] = true;
			}
			
			return strs;
		}
		
		return null;
	}
	
	public static BufferedImage getBannerIcon(DirectoryNode pkg_tree, int preferred_lan) throws IOException{

		int lan = preferred_lan;
		if(lan < 0) lan = 0;
		if(lan >= LAN_SLOT_COUNT) lan = LAN_SLOT_COUNT-1;
		boolean[] lan_checked = new boolean[LAN_SLOT_COUNT];
		Collection<FileNode> found = null;
		
		//Try preferred language
		String i_name = "icon_" + LANNAMES_ENG[lan].replace(" ", "") + ".dat";
		found = getNodesWithName(pkg_tree, i_name);
		lan_checked[lan] = true;
		
		//Try "nearby" languages
		if(found.isEmpty()){
			int[] olans = LAN_SCAN_PRIORITY[lan];
			if(olans != null && olans.length > 0){
				for(int l = 0; l < olans.length; l++){
					i_name = "icon_" + LANNAMES_ENG[olans[l]].replace(" ", "") + ".dat";
					found = getNodesWithName(pkg_tree, i_name);
					lan_checked[olans[l]] = true;
					if(!found.isEmpty()) break;
				}
			}
		}
		
		//Try all languages
		if(found.isEmpty()){
			for(int l = 0; l < LAN_SLOT_COUNT; l++){
				if(lan_checked[l]) continue;
				i_name = "icon_" + LANNAMES_ENG[l].replace(" ", "") + ".dat";
				found = getNodesWithName(pkg_tree, i_name);
				lan_checked[l] = true;
				if(!found.isEmpty()) break;
			}
		}
		
		if(found.isEmpty()) return null;
		
		//Read in (it's a jpeg)
		for(FileNode n : found){
			FileBuffer dat = n.loadData();
			ByteArrayInputStream is = new ByteArrayInputStream(dat.getBytes());
			BufferedImage img = ImageIO.read(is);
			return img;
		}
		
		return null;
	}
	
	public static Collection<FileNode> unmountDLCNodes(DirectoryNode pkg_tree, String dlc_id){

		Collection<FileNode> nodes = pkg_tree.getNodesThat(new NodeMatchCallback(){

			public boolean meetsCondition(FileNode n) {
				if(n.isDirectory()) return false; //Do dirs next...
				
				String dlcid = n.getMetadataValue(METAKEY_DLCID);
				if(dlcid == null) return false;
				if(!dlcid.equals(dlc_id)) return false;
				
				//This one we keep
				//Note the parent before dismounting
				DirectoryNode p = n.getParent();
				if(p != null){
					long id = p.getGUID();
					if(id == -1L) {p.generateGUID(); id = p.getGUID();}
					n.setMetadataValue(METAKEY_PARENTID, Long.toHexString(id));
				}
				n.setParent(null);
				
				return true;
			}
			
		});
		
		Collection<FileNode> dnodes = pkg_tree.getNodesThat(new NodeMatchCallback(){

			public boolean meetsCondition(FileNode n) {
				if(!n.isDirectory()) return false;
				if(n.getChildCount() != 0) return false;
				
				String dlcid = n.getMetadataValue(METAKEY_DLCID);
				if(dlcid == null) return false;
				if(!dlcid.equals(dlc_id)) return false;
				
				//This one we keep
				//Note the parent before dismounting
				DirectoryNode p = n.getParent();
				if(p != null){
					long id = p.getGUID();
					if(id == -1L) {p.generateGUID(); id = p.getGUID();}
					n.setMetadataValue(METAKEY_PARENTID, Long.toHexString(id));
				}
				n.setParent(null);
				
				return true;
			}
			
		});
		
		nodes.addAll(dnodes);
		return nodes;
	}
	
	public static String getDecryptTempDir(){
		if(dec_temp_dir == null){
			try{dec_temp_dir = FileBuffer.getTempDir();}
			catch(IOException x){x.printStackTrace();}
		}
		return dec_temp_dir;
	}
	
	public static void setDecryptTempDir(String dirpath){
		dec_temp_dir = dirpath;
	}

}
