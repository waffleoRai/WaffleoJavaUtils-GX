package waffleoRai_Containers.nintendo.nx;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import waffleoRai_Files.tree.DirectoryNode;
import waffleoRai_Files.tree.FileNode;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;

public class NXCInfo {
	
	public static final String OP_INFILE = "file";
	public static final String OP_CKEYS = "ckeys";
	public static final String OP_TKEYS = "tkeys";
	public static final String OP_CKEYS_BIN = "ckeysbin";
	public static final String OP_TKEYS_BIN = "tkeysbin";
	public static final String OP_OUTPUT = "out";
	public static final String OP_LOWTREE = "lowtree";
	
	private static void printUsage(){
		//TODO
	}
	
	private static void info_xci(String inpath, NXCrypt crypto, Writer out, boolean lowtree) throws IOException, UnsupportedFileTypeException{

		NXCartImage xci = NXCartImage.readXCI(inpath);
		xci.unlock(crypto);
		xci.printInfo(out);
		
		//Tree
		DirectoryNode tree = null;
		if(lowtree) tree = xci.getFileTree(NXCartImage.TREEBUILD_COMPLEXITY_ALL);
		else tree = xci.getFileTree(NXCartImage.TREEBUILD_COMPLEXITY_MERGED);
		
		out.write("\n\n ~~~~~~~~ File System Tree ~~~~~~~~");
		tree.printMeTo(out, 0);
	}
	
	private static void info_nca(String inpath, NXCrypt crypto, Writer out, boolean lowtree) throws UnsupportedFileTypeException, IOException{
		FileBuffer filedat = FileBuffer.createBuffer(inpath, false);
		SwitchNCA nca = SwitchNCA.readNCA(filedat, 0L);
		nca.unlock(crypto);
		nca.printInfo(out);
		
		if(lowtree) nca.buildFileTree(filedat, 0L, NXCartImage.TREEBUILD_COMPLEXITY_ALL);
		else nca.buildFileTree(filedat, 0L, NXCartImage.TREEBUILD_COMPLEXITY_MERGED);
		
		DirectoryNode tree = nca.getFileTree();
		out.write("\n\n ~~~~~~~~ File System Tree ~~~~~~~~");
		tree.printMeTo(out, 0);
	}
	
	private static void info_pfs(String inpath, NXCrypt crypto, Writer out, boolean lowtree) throws IOException, UnsupportedFileTypeException{

		FileBuffer filedat = FileBuffer.createBuffer(inpath, false);
		NXPFS pfs = NXPFS.readPFS(filedat, 0L);
		pfs.printInfo(out, true);
		
		//Extract NCAs from PFS
		List<FileNode> flist = pfs.getFileList();
		for(FileNode f : flist){
			if(f.getFileName().endsWith(".nca")){
				out.write("\nNCA Found: " + f.getFileName() + "\n");
				long stoff = f.getOffset();
				//long edoff = stoff + f.getLength();
				
				SwitchNCA nca = SwitchNCA.readNCA(filedat, stoff);
				nca.unlock(crypto);
				nca.printInfo(out);
				
				/*if(lowtree) nca.buildFileTree(filedat, 0L, NXCartImage.TREEBUILD_COMPLEXITY_ALL);
				else nca.buildFileTree(filedat, 0L, NXCartImage.TREEBUILD_COMPLEXITY_MERGED);
				
				DirectoryNode tree = nca.getFileTree();
				out.write("\n~~~NCA Tree~~~");
				tree.printMeTo(out, 0);*/
			}
		}
		
	}
	
	private static Map<String, String> parseArgs(String[] args){
		Map<String, String> argsmap = new HashMap<String, String>();
		if(args == null) return argsmap;
		for(String s : args){
			String[] split = s.split("=");
			if(split.length > 1) argsmap.put(split[0], split[1]);
			else argsmap.put(split[0], "true");
		}
		return argsmap;
	}

	public static void main(String[] args) {

		Map<String, String> argsmap = parseArgs(args);
		
		String inpath = argsmap.get(OP_INFILE);
		NXCrypt crypto = new NXCrypt();
		String outpath = argsmap.get(OP_OUTPUT);
		boolean lowtree = argsmap.containsKey(OP_LOWTREE);
		
		//Load crypto state
		try{
			String kpath = argsmap.get(OP_CKEYS_BIN);
			if(kpath != null) crypto.loadCommonKeys(kpath);
			else{
				kpath = argsmap.get(OP_CKEYS);
				if(kpath == null){
					System.err.println("Path to common NX Keys required!");
					printUsage();
					System.exit(1);
				}
				crypto.importCommonKeys(kpath);
			}	
			
			kpath = argsmap.get(OP_TKEYS_BIN);
			if(kpath != null) crypto.loadTitleKeys(kpath);
			else{
				kpath = argsmap.get(OP_TKEYS);
				if(kpath == null){
					System.err.println("WARNING: Title keys file not provided. Some parts of file may be undecryptable!");
				}
				else crypto.importTitleKeys(kpath);
			}
			
			//Prepare output writer
			BufferedWriter bw = null;
			if(outpath != null) bw = new BufferedWriter(new FileWriter(outpath));
			else bw = new BufferedWriter(new OutputStreamWriter(System.out));
			
			//Determine which file type we're working with from extension...
			//(Yes, clumsy, but I'll fix it later if I have to)
			if(inpath.endsWith(".xci")){
				info_xci(inpath, crypto, bw, lowtree);
			}
			else if (inpath.endsWith(".nca")){
				info_nca(inpath, crypto, bw, lowtree);
			}
			else{
				//Assumed an update/DLC nsp or whatever, which is a PFS
				info_pfs(inpath, crypto, bw, lowtree);
			}
			
			//Close output stream
			bw.close();
		}
		catch(Exception x){
			x.printStackTrace();
			System.exit(1);
		}
		
	}

}
