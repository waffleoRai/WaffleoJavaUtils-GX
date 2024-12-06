package waffleoRai_SeqSound.n64al;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.OutputStreamWriter;

import waffleoRai_Utils.FileBuffer;

public class Test_NUSALBin2MML {

	public static void main(String[] args) {
		
		if(args.length < 1){
			System.err.println("Expected Args: inpath (outpath)");
			System.exit(1);
		}
		
		//If no outpath, print to stdout
		try{
			String inpath = args[0];
			String outpath = (args.length >= 2)?args[1]:null;
			
			NUSALSeq nseq = NUSALSeq.readNUSALSeq(FileBuffer.createBuffer(inpath, true));
			BufferedWriter bw = null;
			if(outpath != null){
				bw = new BufferedWriter(new FileWriter(outpath));
			}
			else{
				bw = new BufferedWriter(new OutputStreamWriter(System.out));
			}
			nseq.exportMMLScript(bw, true, NUSALSeq.SYNTAX_SET_ZEQER);
			bw.close();
		}
		catch(Exception ex){
			ex.printStackTrace();
			System.exit(1);
		}

	}

}
