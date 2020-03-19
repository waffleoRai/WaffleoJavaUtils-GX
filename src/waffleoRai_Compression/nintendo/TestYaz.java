package waffleoRai_Compression.nintendo;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;

import waffleoRai_Containers.nintendo.GCRARC;

public class TestYaz {

	public static void main(String[] args) 
	{
		String in_path = "C:\\Users\\Blythe\\Documents\\Desktop\\Z2SoundSeqs.arc";
		String temp_path = "C:\\Users\\Blythe\\Documents\\Desktop\\Z2SoundSeqs_decomp.arc";
		String out_path = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\Z2SoundSeqs";
		
		String log_path = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\Z2SoundSeqs.out";
		
		try
		{
			//Decompress YAZ
			BufferedInputStream bis = new BufferedInputStream(new FileInputStream(in_path));
			YazDecodeStream ydec = YazDecodeStream.getDecoderStream(bis);
			BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(temp_path));
			int b = -1;
			while((b = ydec.read()) != -1)
			{
				bos.write(b);
			}
			bos.close();
			ydec.close();
			
			//Read back in archive
			GCRARC arc = GCRARC.openRARC(temp_path);
			
			//Write tree
			BufferedWriter bw = new BufferedWriter(new FileWriter(log_path));
			arc.printMe(bw);
			bw.close();
			
			//Write arc
			arc.extractTo(out_path);
			
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}

	}

}
