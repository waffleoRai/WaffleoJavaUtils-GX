package waffleoRai_Containers.nintendo;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import waffleoRai_Files.tree.DirectoryNode;
import waffleoRai_Files.tree.FileNode;
import waffleoRai_Utils.FileBuffer;

public class NDSTest {

	public static void writeDirNode(DirectoryNode n, String pathstem) throws IOException
	{
		System.err.println("Writing directory " + pathstem + " ...");
		if(!FileBuffer.directoryExists(pathstem)) Files.createDirectories(Paths.get(pathstem));
		
		List<FileNode> children = n.getChildren();
		
		for(FileNode child : children)
		{
			String path = pathstem + File.separator + child.getFileName();
			if(child.isDirectory())
			{
				writeDirNode((DirectoryNode)child, path);
			}
			else
			{
				String src = child.getSourcePath();
				long off = child.getOffset();
				long edoff = off + child.getLength();
				System.err.println("Writing file " + path + " ...");
				FileBuffer data = FileBuffer.createBuffer(src, off, edoff, false);
				data.writeFile(path);
			}
		}
		
	}
	
	public static void main(String[] args) 
	{
		//String path = "C:\\Users\\Blythe\\Documents\\Game Stuff\\dumps\\NTR_CPUE_USA.nds";
		//String path = "C:\\Users\\Blythe\\Documents\\Game Stuff\\dumps\\NTR_AMCE_USA.nds";
		//String path = "C:\\Users\\Blythe\\Documents\\Game Stuff\\dumps\\TWL_IRAO_USA.nds";
		//String inpath = "C:\\Users\\Blythe\\Documents\\Game Stuff\\dumps";
		String inpath = "C:\\Users\\Blythe\\Documents\\Game Stuff\\DS\\Games";
		String outpath = "C:\\Users\\Blythe\\Documents\\Game Stuff\\DS\\Games\\filedumps";
		
		try
		{
			//FileBuffer file = FileBuffer.createBuffer(path);
			//NDS nds = NDS.readROM(path, 0);
			Path dirPath = Paths.get(inpath);
			DirectoryStream<Path> dstream = Files.newDirectoryStream(dirPath);
			for (Path f : dstream)
			{
				String fpath = f.toString();
				if (FileBuffer.fileExists(fpath) && (fpath.endsWith(".nds")))
				{
					System.err.println("Dumping " + fpath + " ...");
					NDS nds = NDS.readROM(fpath, 0);
					String rootname = nds.getGameCode() + "__" + nds.getLongGameCode().replace(" ", "_");
					
					//Make folder
					String gamedir = outpath + File.separator + rootname;
					if(!FileBuffer.directoryExists(gamedir)) Files.createDirectory(Paths.get(gamedir));
					
					/*
					//Dump the ARM binaries & raw header...
					FileBuffer header = FileBuffer.createBuffer(fpath, 0, nds.getHeaderSize(), false);
					header.writeFile(gamedir + File.separator + "header.bin");
					DSExeData arm9 = nds.getARM9_data();
					arm9.getData().writeFile(gamedir + File.separator + "main.arm9");
					DSExeData arm7 = nds.getARM7_data();
					arm7.getData().writeFile(gamedir + File.separator + "main.arm7");
					
					//Dump the file system...
					String fsdir = gamedir + File.separator + nds.getGameCode();
					if(!FileBuffer.directoryExists(gamedir)) Files.createDirectory(Paths.get(fsdir));
					
					writeDirNode(nds.getRootNode(), fsdir);*/
					
					nds.dumpToDisk(gamedir);
				}
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
			System.exit(1);
		}

	}

}
