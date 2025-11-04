package waffleoRai_Files.maxis.ts3save;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.List;

import waffleoRai_Containers.maxis.MaxisPropertyStream;
import waffleoRai_Files.maxis.ts3save.household.Household;
import waffleoRai_Files.maxis.ts3save.sim.SimDescription;
import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.FileBuffer;

public class Test_DumpFAMD {

	public static void main(String[] args) {
		String inpath = args[0];
		String outpath = args[1];
		
		try {
			FileBuffer inBin = FileBuffer.createBuffer(inpath, false);
			//Looks like the household PS is inside another PS
			//	And the file starts with an int value of 2. Save file versoin number I wonder?
			
			BufferReference householdStart = inBin.getReferenceAt(0x0aL);
			MaxisPropertyStream hhStream = MaxisPropertyStream.openForRead(householdStart, false, 2);
			Household household = Household.readPropertyStream(hhStream);
			household.setWriteXmlMembersExternal(true);
			
			String hhXmlPath = outpath + File.separator + "HH_" + household.name + ".xml";
			BufferedWriter bw = new BufferedWriter(new FileWriter(hhXmlPath));
			household.writeXMLNode(bw, "");
			bw.close();
			
			List<SimDescription> sims = household.mMembers.allSimDescriptionList;
			for(SimDescription sim : sims) {
				String simXmlPath = outpath + File.separator + sim.genXmlFileName() + ".xml";
				bw = new BufferedWriter(new FileWriter(simXmlPath));
				sim.writeXMLNode(bw, "");
				bw.close();
			}
			
			System.err.println("Hold");
		}
		catch(Exception ex) {
			ex.printStackTrace();
			System.exit(1);
		}

	}

}
