package waffleoRai_Containers.nintendo;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

@SuppressWarnings("unused")
public class MergerTest {
	
	private static void wiimerge(String indir, String outdir, String gamecode, String reg, int parts) throws IOException
	{
		String instem = indir + File.separator + gamecode + ".part";
		String outpath = outdir + File.separator + "RVL_" + gamecode.substring(0,4) + "_" + reg + ".iso";
		BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outpath));
		for(int i = 0; i < parts; i++)
		{
			String inpath = instem + i + ".iso";
			BufferedInputStream bis = new BufferedInputStream(new FileInputStream(inpath));
			int b = -1;
			while((b = bis.read()) != -1)
			{
				bos.write(b);
			}
			bis.close();
		}
		bos.close();
	}
	
	private static void wupmerge(String indir, String outdir, String gamecode, String reg, int parts) throws IOException
	{
		String instem = indir + File.separator + "WUP-P-" + gamecode + File.separator + "game_part";
		String outpath = outdir + File.separator + "WUP_" + gamecode + "_" + reg + ".wud";
		System.err.println("Output file: " + outpath);
		BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outpath));
		for(int i = 1; i <= parts; i++)
		{
			String inpath = instem + i + ".wud";
			System.err.println("Copying part " + i + " from " + inpath);
			BufferedInputStream bis = new BufferedInputStream(new FileInputStream(inpath));
			int b = -1;
			while((b = bis.read()) != -1)
			{
				bos.write(b);
			}
			bis.close();
		}
		bos.close();
	}

	public static void main(String[] args) 
	{
		//String instem = "G:\\SOUE01.part";
		//String outpath = "E:\\Library\\Games\\Console\\RVL_SOUE_USA.iso";

		try
		{
			//wiimerge("G:", "E:\\Library\\Games\\Console", "SOUE01", "USA", 2);
			wupmerge("G:\\wudump", "E:\\Library\\Games\\Console", "AZAE", "USZ", 12);
		}
		catch(Exception x)
		{
			x.printStackTrace();
			System.exit(1);
		}
		
	}

}
