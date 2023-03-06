package waffleoRai_Compression.lz77;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.TreeSet;

import waffleoRai_Image.psx.QXImage;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBufferStreamer;
import waffleoRai_Utils.StreamWrapper;

public class TestLzMu {
	
	public static int scanForThings(Path dir, Set<Integer> short_off, Set<Integer> long_off, Set<Integer> long_rl) throws IOException
	{
		/*I'm going to try scanning for...
		 * 	1. Ctrl characters ending in 0 preceded by another reference instruction
		 * 	2. Range of backsets for short references
		 * 	3. Range of backsets and runs for long references
		*/
		
		int count = 0;
		DirectoryStream<Path> dirstream = Files.newDirectoryStream(dir);
		
		for(Path path : dirstream)
		{
			String pstr = path.toAbsolutePath().toString();
			if(FileBuffer.directoryExists(pstr))
			{
				System.err.println("Scanning " + pstr + "...");
				count += scanForThings(path, short_off, long_off, long_rl);
			}
			else
			{
				if(pstr.contains("fdat")) continue;
				if(pstr.endsWith(".lz"))
				{
					System.err.println("Scanning " + pstr + "...");
					LZMu lz = new LZMu();
					FileBuffer file = FileBuffer.createBuffer(pstr, false);
					int decsz = file.intFromFile(0);
					lz.decode(new FileBufferStreamer(file.createReadOnlyCopy(4, file.getFileSize())), decsz);
					
					/*count += lz.getDebugCounter();
					short_off.addAll(lz.getOffsetsShort());
					long_off.addAll(lz.getOffsetsLong());
					long_rl.addAll(lz.getRunsLong());*/
				}
			}
		}
		
		return count;
	}
	
	public static void main(String[] args) 
	{
		try
		{
			//String inpath = "C:\\Users\\Blythe\\Documents\\Game Stuff\\PSX\\GameData\\SLPM87176\\FIELD\\FIELD_000.fdat.lz";
			//String outpath = "C:\\Users\\Blythe\\Documents\\Game Stuff\\PSX\\GameData\\SLPM87176_conv\\FIELD\\raw\\FIELD_000.fdat";
			
			//String inpath = "C:\\Users\\Blythe\\Documents\\Game Stuff\\PSX\\GameData\\SLPM87176_conv\\FACE\\raw\\FACE_003.qxp";
			//String outpath = "C:\\Users\\Blythe\\Documents\\Desktop\\Notes\\jolly good time\\FACE_003_lztest.qx.lz";
			//String testpath = "C:\\Users\\Blythe\\Documents\\Desktop\\Notes\\jolly good time\\FACE_003_lztest.qx";
			
			//String inpath = "C:\\Users\\Blythe\\Documents\\Desktop\\Notes\\jolly good time\\face_replace\\cface_normalwah.qx.lz";
			String outpath = "C:\\Users\\Blythe\\Documents\\Desktop\\Notes\\jolly good time\\face_replace\\cface_normalwah.qx";
			String testpath = "C:\\Users\\Blythe\\Documents\\Desktop\\Notes\\jolly good time\\face_replace\\cface_normalwah.png";
			
			//String inpath = "C:\\Users\\Blythe\\Documents\\Game Stuff\\PSX\\GameData\\SLPM87176\\FACE\\FACE_003.qxp.lz";
			
			//FileBuffer test = FileBuffer.createBuffer(outpath, false);
			//test.createReadOnlyCopy(0x218, test.getFileSize()).writeFile(outpath + ".part");
			//System.exit(1);
			
			
			String inpath = "C:\\Users\\Blythe\\Documents\\Game Stuff\\PSX\\GameData\\SLPM87176";
			Set<Integer> short_off = new TreeSet<Integer>();
			Set<Integer> long_off = new TreeSet<Integer>();
			Set<Integer> long_rl = new TreeSet<Integer>();
			int ct = scanForThings(Paths.get(inpath), short_off, long_off, long_rl);
			System.err.println("Cases where ctrl byte ended in 0 preceded by ref instruction: " + ct);
			int min = 0x7FFFFFFF;
			int max = 0;
			for(int i : short_off)
			{
				if(i < min) min = i;
				if(i > max) max = i;
			}
			int notmin = ~min & 0xFF;
			int notmax = ~max & 0xFF;
			System.err.println("Short Reference backset range: " + min + " - " + max + " (0x" + String.format("%02x", notmin) + " - 0x" + String.format("%02x", notmax) + ")");
			
			min = 0x7FFFFFFF;
			max = 0;
			for(int i : long_off)
			{
				if(i < min) min = i;
				if(i > max) max = i;
			}
			notmin = ~min & 0x1FFF;
			notmax = ~max & 0x1FFF;
			System.err.println("Long Reference backset range: " + min + " - " + max + " (0x" + String.format("%02x", notmin) + " - 0x" + String.format("%02x", notmax) + ")");
			
			
			min = 0x7FFFFFFF;
			max = 0;
			for(int i : long_rl)
			{
				if(i < min) min = i;
				if(i > max) max = i;
			}
			notmin = ~min & 0xFF;
			notmax = ~max & 0xFF;
			System.err.println("Long Reference run length range: " + min + " - " + max + " (0x" + String.format("%02x", notmin) + " - 0x" + String.format("%02x", notmax) + ")");
			

			System.exit(2);
			
			FileBuffer in = FileBuffer.createBuffer(inpath, false);
			//Get size
			int sz = in.intFromFile(0);
			LZMu lz = new LZMu();
			StreamWrapper out = lz.decode(new FileBufferStreamer(in.createReadOnlyCopy(4, in.getFileSize())), sz);
			//StreamWrapper out = lz.encode(new FileBufferStreamer(in), (int)in.getFileSize());
			System.exit(2);
			
			BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outpath));
			//FileBuffer sz = new FileBuffer(4, false);
			//sz.addToFile(0xb18);
			//bos.write(sz.getBytes());
			while(!out.isEmpty()) bos.write(out.getFull());
			bos.close();
			
			/*LZMu dec = new LZMu();
			out = dec.decode(new FileBufferStreamer(FileBuffer.createBuffer(outpath, 4)), (int)in.getFileSize());
			
			bos = new BufferedOutputStream(new FileOutputStream(testpath));
			while(!out.isEmpty()) bos.write(out.getFull());
			bos.close();*/
			
			QXImage qx = QXImage.readImageData(outpath, false);
			qx.writeToPNG(testpath, false);
			
		}
		catch(Exception e)
		{
			e.printStackTrace();
			System.exit(1);
		}
	}

}
