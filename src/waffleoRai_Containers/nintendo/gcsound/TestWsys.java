package waffleoRai_Containers.nintendo.gcsound;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.List;

import waffleoRai_Containers.nintendo.gcsound.GC_WSYS.SCNE;

public abstract class TestWsys {

	public static void main(String[] args) 
	{
		String arc_path = "C:\\Users\\Blythe\\Documents\\Desktop\\Z2Sound.baa";
		long wsys_off = 0x2d114;
		//long wsys_off = 0x1931d4;
		//String arc_path = "C:\\Users\\Blythe\\Documents\\Desktop\\JaiInit.aaf";
		//long wsys_off = 0x829b0;
		
		String log_path = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\Z2Sound_wsys_check.log";
		String cex_path = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\Z2Sound_wsys_c-ex.log";
		String cst_path = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\Z2Sound_wsys_c-st.log";
		String coverage_map = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\Z2Sound_wsys_coverage.bin";
		//String tally_path = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\Z2Sound_wsys_sid.log";
		//String log_path = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\Z2Sound_wsys2_check.log";
		//String tally_path = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\Z2Sound_wsys2_sid.log";
		//String cex_path = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\Z2Sound_wsys2_c-ex.log";
		//String cst_path = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\Z2Sound_wsys2_c-st.log";
		//String log_path = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\JaiInit_wsys_check.log";
		//String part_path = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\Z2Sound_wsys_only.wsys";
		
		try
		{
			//Copy
			//FileBuffer file = new FileBuffer(arc_path, wsys_off, wsys_off + 0x1660c0, true);
			//file.writeFile(part_path);
			
			GC_WSYS wsys = GC_WSYS.readWSYS(arc_path, wsys_off);
			
			BufferedWriter bw = new BufferedWriter(new FileWriter(log_path));
			wsys.printInfo(bw);
			bw.close();
			
			List<SCNE> cex_list = wsys.getSCNE_withNonEmptyCEX();
			if(cex_list.isEmpty()) System.out.println("Sorry! All C-EX blocks are empty!");
			else
			{
				bw = new BufferedWriter(new FileWriter(cex_path));
				for(SCNE s : cex_list)
				{
					bw.write("--> SCNE " + s.scne_idx + "\n");
					int j = 0;
					while(j < SCNE.CEX_SIZE)
					{
						if(j % 8 == 0) bw.write("\t");
						bw.write(String.format("%02x ", s.cex[j]));
						j++;
						if(j % 8 == 0) bw.write("\n");
					}
				}
				
				bw.close();
			}
			
			List<SCNE> cst_list = wsys.getSCNE_withNonEmptyCST();
			if(cst_list.isEmpty()) System.out.println("Sorry! All C-ST blocks are empty!");
			else
			{
				bw = new BufferedWriter(new FileWriter(cst_path));
				for(SCNE s : cst_list)
				{
					bw.write("--> SCNE " + s.scne_idx + "\n");
					int j = 0;
					while(j < SCNE.CST_SIZE)
					{
						if(j % 8 == 0) bw.write("\t");
						bw.write(String.format("%02x ", s.cst[j]));
						j++;
						if(j % 8 == 0) bw.write("\n");
					}
				}
				
				bw.close();
			}
			
			//Map
			/*bw = new BufferedWriter(new FileWriter(tally_path));
			
			Map<Integer, List<CDFRecord>> soundidmap = wsys.mapBySoundId();
			ArrayList<Integer> keylist = new ArrayList<Integer>(soundidmap.size() + 1);
			keylist.addAll(soundidmap.keySet());
			Collections.sort(keylist);
			TallyMap tally = new TallyMap();
			
			//Get highest value
			int max = keylist.get(keylist.size()-1);
			bw.write("Max Value: " + max + "\n");
			bw.write("# Values: " + keylist.size() + "\n");
			for(int i = 0; i < max; i++)
			{
				List<CDFRecord> l = soundidmap.get(i);
				bw.write("Sound #" + String.format("%04d: ", i));
				if(l == null || l.isEmpty())
				{
					tally.increment(0);
					bw.write("(Empty)\n");
					continue;
				}
				if(l.size() == 1)
				{
					tally.increment(1);
					CDFRecord cdf = l.get(0);
					bw.write("SCNE " + cdf.scne_idx + ", CDF " + cdf.cdf_idx + ", AW Idx: " + cdf.aw_idx + "\n");
				}
				else
				{
					tally.increment(l.size());
					bw.write("\n");
					for(CDFRecord cdf : l)
					{
						bw.write("\tSCNE " + cdf.scne_idx + ", CDF " + cdf.cdf_idx + ", AW Idx: " + cdf.aw_idx + "\n");
					}
				}
			}
			
			bw.write("\n");
			
			//Print tallies
			bw.write("Tallies...\n");
			List<Integer> vals = tally.getAllValues();
			for(Integer i : vals) bw.write(i + "\t" + tally.getCount(i) + "\n");
			
			bw.close();*/
			
			wsys.testprint();
			
			wsys.writeCoverageFile(coverage_map);
			
		}
		catch(Exception e)
		{
			e.printStackTrace();
			System.exit(1);
		}
		
	}

}
