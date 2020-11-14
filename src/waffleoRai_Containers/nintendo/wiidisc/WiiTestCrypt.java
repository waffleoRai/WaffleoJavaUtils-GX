package waffleoRai_Containers.nintendo.wiidisc;

import waffleoRai_Containers.nintendo.WiiDisc;
import waffleoRai_Encryption.AES;
import waffleoRai_Encryption.nintendo.NinCryptTable;
import waffleoRai_Files.NodeMatchCallback;
import waffleoRai_Files.tree.DirectoryNode;
import waffleoRai_Files.tree.FileNode;
import waffleoRai_Files.tree.FileTreeSaver;
import waffleoRai_Utils.FileBuffer;

public class WiiTestCrypt {

	public static void main(String[] args) {
		
		//Used to test loading encrypted data directly from image
		
		String imgpath = "E:\\Library\\Games\\Console\\RVL_SOUE_USA.wii"; //Use SOUE
		String testdir = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\wiitest";
		
		try{
			
			String keypath = testdir + "\\rvl_common.bin";
			String treepath = testdir + "\\soue_test_tree.bin";
			String ctblpath = testdir + "\\soue_ctbl.bin";
			
			WiiDisc.setCommonKey(keypath, 0L);
			
			//Try to read (and unlock just in case)
			System.err.println("Reading Disc Structure...");
			WiiDisc img = WiiDisc.parseFromData(FileBuffer.createBuffer(imgpath, true), null, false);
			img.unlock();
			
			//Generate table
			System.err.println("Generating crypt table...");
			NinCryptTable ctbl = img.generateCryptTable();
			ctbl.printMe();
			
			//Generate tree
			System.err.println("Reading file tree...");
			DirectoryNode tree = img.buildDirectTree(imgpath, false);
			//tree.printMeToStdErr(0);
			
			//Save tree
			System.err.println("Saving file tree...");
			FileTreeSaver.saveTree(tree, treepath, false, false);
			
			//Save crypt table (remember to setup crypto state to read back in tree)
			System.err.println("Saving crypto table...");
			ctbl.exportToFile(ctblpath);
			WiiCrypt.initializeDecryptorState(ctbl);
			
			//Read back in file tree
			System.err.println("Reading back file tree...");
			tree = FileTreeSaver.loadTree(treepath);
			tree.printMeToStdErr(0);
			
			//Extract a test file
			System.err.println("Extracting a test file...");
			String testfilepath = testdir + "\\WZSound.brsar";
			String testnodepath = tree.findNodeThat(new NodeMatchCallback(){

				@Override
				public boolean meetsCondition(FileNode n) {
					if(n.isDirectory()) return false;
					return n.getFileName().equalsIgnoreCase("WZSound.brsar");
				}
				
			});
			FileNode node = tree.getNodeAt(testnodepath);
			System.err.println(node.getVirtualSource().getLocationString());
			node.loadData().writeFile(testfilepath);
			//Maybe it's getByte()?
			FileBuffer tdat = node.loadData();
			System.err.print("testing getByte(): ");
			for(int i = 0; i < 24; i++) System.err.print(String.format("%02x", tdat.getByte(i)));
			System.err.println();
			System.err.println("testing getBytes(): " + AES.bytes2str(tdat.getBytes(0, 24)));
			
			//Clean up
			WiiCrypt.clearDecryptorState();
			
		}
		catch(Exception x){
			x.printStackTrace();
			System.exit(1);
		}
		
	}

}
