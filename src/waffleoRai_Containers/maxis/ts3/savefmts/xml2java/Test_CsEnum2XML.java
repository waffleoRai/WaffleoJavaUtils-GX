package waffleoRai_Containers.maxis.ts3.savefmts.xml2java;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;

import waffleoRai_Utils.StringUtils;

public class Test_CsEnum2XML {

	public static void main(String[] args) {

		String inpath = args[0];
		String outpath = args[1];
		
		final boolean READ_AS_FLAGS = true;
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
				line = line.replace(",", "");
				line = line.trim();
				if(line.endsWith("uL")) line = line.substring(0, line.length()-2);
				if(line.endsWith("L")) line = line.substring(0, line.length()-1);
				if(line.contains("=")) {
					String[] spl = line.split("=");
					myName = spl[0].trim();
					myVal = StringUtils.parseUnsignedLong(spl[1].trim());
				}
				else{
					myVal = lastVal + 1;
					myName = line;
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
