package waffleoRai_Files.maxis.xml2java;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;

import waffleoRai_Utils.StringUtils;

public class Test_TableEnum2XML {
	
	public static void main(String[] args) {

		String inpath = args[0];
		String outpath = args[1];
		
		final boolean READ_AS_FLAGS = false;
		final int DIG_COUNT = 8;
		final String INDENT = "\t";
		
		try {
			long lastVal = -1;
			BufferedReader br = new BufferedReader(new FileReader(inpath));
			BufferedWriter bw = new BufferedWriter(new FileWriter(outpath));
			String line = null;
			while((line = br.readLine()) != null) {
				if(line.isEmpty()) continue;
				long myVal = 0;
				String myName = null;
				
				String[] spl = null;
				if(line.contains("\t")) {
					spl = line.split("\t");
					myVal = Integer.toUnsignedLong(StringUtils.parseUnsignedInt(spl[0].trim()));
					myName = spl[1].trim();
				}
				else {
					spl = line.split(" ");
					myVal = Integer.toUnsignedLong(StringUtils.parseUnsignedInt(spl[0].trim()));
					myName = spl[spl.length-1].trim();
				}
				
				bw.write(INDENT + "<");
				if(READ_AS_FLAGS) bw.write("FlagValue");
				else bw.write("EnumValue");
				bw.write(String.format(" Name=\"%s\" Value=\"0x%0" + DIG_COUNT + "x\"/>\n", myName, myVal));
				lastVal = myVal;
			}
			bw.close();
			br.close();
			
		}
		catch(Exception ex) {
			ex.printStackTrace();
		}

	}

}
