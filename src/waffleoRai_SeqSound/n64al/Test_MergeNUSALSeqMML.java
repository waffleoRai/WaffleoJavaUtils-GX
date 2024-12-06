package waffleoRai_SeqSound.n64al;

import java.io.BufferedWriter;
import java.io.FileWriter;

import waffleoRai_SeqSound.n64al.cmd.SysCommandBook;

public class Test_MergeNUSALSeqMML {

	public static void main(String[] args) {
		if(args.length < 2){
			System.err.println("Two required arguments: <input_stem> <output_file>");
			System.exit(1);
		}
		
		String instem = args[0];
		String outpath = args[1];

		try{
			NUSALSeq seq = NUSMMLSplitter.mergeSeq(instem, SysCommandBook.ZELDA64.getBook());
			if(seq == null){
				System.err.println("ERROR: Returned seq is null! See stderr for details.");
				System.exit(1);
			}
			
			BufferedWriter bw = new BufferedWriter(new FileWriter(outpath));
			seq.exportMMLScript(bw);
			bw.close();
		}
		catch(Exception ex){
			ex.printStackTrace();
			System.exit(1);
		}
		
	}

}
