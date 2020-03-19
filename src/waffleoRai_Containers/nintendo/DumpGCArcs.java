package waffleoRai_Containers.nintendo;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import waffleoRai_Compression.nintendo.Yaz;
import waffleoRai_Compression.nintendo.YazDecodeStream;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;

public class DumpGCArcs {
	
	private static void scanFile(Path f, String name, Path dirPath) throws IOException, UnsupportedFileTypeException
	{
		//Pop open and scan first 16 bytes to see what kind of file it is
		FileBuffer filetop = new FileBuffer(f.toString(), 0, 0x10, true);
		
		String tpath = null;
		//if(mirrorPath != null) tpath = mirrorPath + File.separator + name;
		tpath = FileBuffer.getTempDir() + File.separator + name;
		
		//We're looking for RARC, U8, and Yaz
		int magic = filetop.intFromFile(0);
		try{
		if(magic == GCRARC.MAGIC)
		{
			System.out.println("RARC Found: " + f.toString());
			//Move to temp or mirror
			if(FileBuffer.fileExists(tpath)) Files.delete(Paths.get(tpath));
			Files.move(f, Paths.get(tpath));
			
			extractRARC(tpath, dirPath.toString());
			
			//If temp, delete
			Files.deleteIfExists(Paths.get(tpath));
		}
		else if(magic == Yaz.MAGIC)
		{
			System.out.println("Yaz Found: " + f.toString());
			
			if(FileBuffer.fileExists(tpath)) Files.delete(Paths.get(tpath));
			Files.move(f, Paths.get(tpath));
			extractYAZ(tpath, f.toString());
			
			Files.deleteIfExists(Paths.get(tpath));
		}
		else if(magic == U8Arc.MAGIC)
		{
			System.out.println("U8 Archive Found: " + f.toString());
			
			if(FileBuffer.fileExists(tpath)) Files.delete(Paths.get(tpath));
			Files.move(f, Paths.get(tpath));
			extractU8(tpath, dirPath.toString());
			
			Files.deleteIfExists(Paths.get(tpath));
		}
		}
		catch(Exception e)
		{
			e.printStackTrace();
			System.err.println("ERROR - There was an issue extracting " + f.toString() + " | Skipping...");
		}
	}
	
	private static void extractRARC(String rarcpath, String targpath) throws IOException, UnsupportedFileTypeException
	{
		//Get archive name
		String arc_name = null;
		int lastslash = rarcpath.lastIndexOf(File.separator);
		if(lastslash < 0) arc_name = rarcpath;
		else arc_name = rarcpath.substring(lastslash + 1);
		
		String arcdir = targpath + File.separator + arc_name;
		Files.createDirectories(Paths.get(arcdir));
		
		//Read & Extract
		GCRARC arc = GCRARC.openRARC(rarcpath);
		arc.extractTo(arcdir);
		
		//Now we have an internal directory to scan
		scanDirectory(Paths.get(arcdir));
	}
	
	private static void extractYAZ(String yazpath, String targpath) throws IOException, UnsupportedFileTypeException
	{
		BufferedInputStream bis = new BufferedInputStream(new FileInputStream(yazpath));
		YazDecodeStream decoder = YazDecodeStream.getDecoderStream(bis);
		
		BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(targpath));
		int b = -1;
		while((b = decoder.read()) != -1)
		{
			bos.write(b);
		}
		
		bos.close();
		decoder.close();
		
		//Scan the resulting file to see if anything of interest
		String name = targpath;
		String dir = targpath;
		int lastslash = name.lastIndexOf(File.separator);
		if(lastslash >= 0)
		{
			name = name.substring(lastslash+1);
			dir = dir.substring(0, lastslash);
		}
		scanFile(Paths.get(targpath), name, Paths.get(dir));
	}

	private static void extractU8(String arcpath, String targpath) throws IOException, UnsupportedFileTypeException
	{
		//Get archive name
		String arc_name = null;
		int lastslash = arcpath.lastIndexOf(File.separator);
		if(lastslash < 0) arc_name = arcpath;
		else arc_name = arcpath.substring(lastslash + 1);
				
		String arcdir = targpath + File.separator + arc_name;
		Files.createDirectories(Paths.get(arcdir));
				
		//Read & Extract
		U8Arc arc = new U8Arc(arcpath, 0);
		arc.dumpArchive(arcdir);
				
		//Now we have an internal directory to scan
		scanDirectory(Paths.get(arcdir));
	}
	
	private static void scanDirectory(Path dirPath) throws IOException, UnsupportedFileTypeException
	{
		DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath);
		for (Path f : stream)
		{
			String name = f.toString();
			int lastslash = name.lastIndexOf(File.separator);
			if(lastslash >= 0) name = name.substring(lastslash+1);
			
			if (FileBuffer.fileExists(f.toString()))
			{
				scanFile(f, name, dirPath);
			}
			else if (FileBuffer.directoryExists(f.toString()))
			{
				//String mdir = null;
				//if(mirrorPath  != null) mdir = mirrorPath + File.separator + name;
				scanDirectory(f);
			}
		}
		stream.close();
	}

	public static void main(String[] args) 
	{
		//Un-comment for command line run
		/*String inpath = null;
		
		if(args.length < 1)
		{
			System.err.println("");
			System.exit(1);
		}
		
		inpath = args[0];*/
		
		//Un-comment for IDE run
		//String inpath = "C:\\Users\\Blythe\\Documents\\Game Stuff\\GCN\\GameData\\TwilightPrincess\\decomp\\The Legend of Zelda Twilight Princess [GZ2E01]";
		String inpath = "C:\\Users\\Blythe\\Documents\\Game Stuff\\GCN\\GameData\\WindWaker\\decomp\\THE LEGEND OF ZELDA The Wind Waker for USA [GZLE01]";
		
		//String mirrorpath = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\TwilightPrincessMirror";
		
		
		Path dpath = Paths.get(inpath);
		try
		{
			//if(mirrorpath != null) Files.createDirectories(Paths.get(mirrorpath));
			scanDirectory(dpath);
		}
		catch(Exception e)
		{
			e.printStackTrace();
			System.exit(1);
		}

	}

}
