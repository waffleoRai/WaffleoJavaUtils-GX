package waffleoRai_Containers.nintendo;

import java.io.BufferedWriter;
import java.io.FileWriter;

public class TestRarc {

	public static void main(String[] args) 
	{
		String inpath = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\a_iwaato.arc";
		String logpath = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\a_iwaato.txt";
		
		try
		{
			GCRARC arc = GCRARC.openRARC(inpath);
			
			BufferedWriter bw = new BufferedWriter(new FileWriter(logpath));
			arc.printMe(bw);
			bw.close();
		}
		catch(Exception e)
		{
			e.printStackTrace();
			System.exit(1);
		}
		
	}

}
