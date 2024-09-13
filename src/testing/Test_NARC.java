package testing;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import waffleoRai_Containers.nintendo.NARC;
import waffleoRai_Containers.nintendo.NDS;
import waffleoRai_Files.FileNodeModifierCallback;
import waffleoRai_Files.tree.DirectoryNode;
import waffleoRai_Files.tree.FileNode;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;

public class Test_NARC {
	
	private static final boolean EXPLICIT_FILE_LOAD = true;
	
	private static void dumpNode(DirectoryNode d, String dir, String srcfilePath) throws IOException {
		List<FileNode> children = d.getChildren();
		for(FileNode child : children) {
			String target = dir + File.separator + child.getFileName();
			if(child.isDirectory()) {
				dumpNode((DirectoryNode)child, target, srcfilePath);
			}
			else {
				if(EXPLICIT_FILE_LOAD) {
					//These SHOULD be regular file nodes.
					long offset = child.getOffset();
					long size = child.getLength();
					FileBuffer dat = FileBuffer.createBuffer(srcfilePath, offset, offset + size, false);
					dat.writeFile(target);
					dat.dispose();
				}
				else {
					//This way, we can check issues with the FileNode loader.
					FileBuffer dat = child.loadDecompressedData();
					dat.writeFile(target);
					dat.dispose();
				}
			}
		}
	}
	
	private static void scanDir(String dir) throws IOException, UnsupportedFileTypeException {
		DirectoryStream<Path> dstr = Files.newDirectoryStream(Paths.get(dir));
		for(Path p : dstr) {
			String pstr = p.toAbsolutePath().toString();
			if(Files.isDirectory(p)) {
				scanDir(pstr);
			}
			else {
				if(pstr.endsWith(".narc")) {
					System.err.println("Dumping " + pstr + "...");
					
					if(pstr.endsWith("pms.narc")) {
						//System.err.println("---DEBUG HOLD---");
						continue; //That one is broken???
					}
					
					NARC narc = NARC.readNARC(pstr);
					String fname = p.getFileName().toString();
					String narcdir = dir + File.separator + "d_" + fname;
					if(!FileBuffer.directoryExists(narcdir)) {
						Files.createDirectory(Paths.get(narcdir));
					}
					else {
						//Clean out that directory
						DirectoryStream<Path> ndir = Files.newDirectoryStream(Paths.get(narcdir));
						for(Path np : ndir) {
							Files.delete(np);
						}
						ndir.close();
					}
					DirectoryNode root = narc.getArchiveTree();
					root.doForTree(new FileNodeModifierCallback() {
						public void doToNode(FileNode node) {
							node.setSourcePath(pstr);
						}});
					dumpNode(root, narcdir, pstr);
				}
			}
		}
		dstr.close();
	}

	public static void main(String[] args) {
		//String nds_path = "F:\\Library\\Games\\Console\\NTR_ADAE_USA.nds";
		String dump_path = "C:\\Users\\Blythe\\Documents\\out\\dstest";
		
		try {
			//NDS nds = NDS.readROM(nds_path, 0L);
			//DirectoryNode root = nds.getArchiveTree();
			//nds.dumpToDisk(dump_path + "\\ADAE");
			
			scanDir(dump_path + "\\ADAE");
			
			System.err.println("dbghold");
		}
		catch(Exception ex) {
			ex.printStackTrace();
			System.exit(1);
		}
	}

}
