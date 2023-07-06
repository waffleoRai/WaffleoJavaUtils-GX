package waffleoRai_soundbank.nintendo.z64;

import waffleoRai_Utils.FileBuffer;
import waffleoRai_soundbank.nintendo.z64.Z64Bank.Z64ReadOptions;

public class Test_Z64Bank {

	public static void main(String[] args) {
		
		String inpath = args[0];
		String outpath = args[1];
		
		try{
			FileBuffer buff = FileBuffer.createBuffer(inpath, true);
			Z64Bank bank = Z64Bank.readRaw(buff, new Z64ReadOptions());
			buff = bank.serializeMe(Z64Bank.SEROP_DEFAULT);
			buff.writeFile(outpath);
			
			//UltraBankFile
			String ubnk_path = outpath + ".bubnk";
			UltraBankFile.writeUBNK(bank, ubnk_path, 0);
			
			buff = FileBuffer.createBuffer(ubnk_path, true);
			UltraBankFile ubnk = UltraBankFile.open(buff);
			Z64Bank bank2 = ubnk.read();
			ubnk.close();
		}
		catch(Exception ex){
			ex.printStackTrace();
			System.exit(1);
		}
		

	}

}
