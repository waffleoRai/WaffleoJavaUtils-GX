package waffleoRai_SeqSound.n64al;

import java.io.BufferedReader;
import java.io.FileReader;

import waffleoRai_Utils.FileBuffer;

public class Test_SplitNUSALSeqMML {

	public static void main(String[] args) {
		if(args.length < 2){
			System.err.println("Two required arguments: <input_file> <output_stem>");
			System.exit(1);
		}
		
		//Can take either bin or mml script. Will assume bin if extension is not .mus or .mml
		String inpath = args[0];
		String outstem = args[1];
		boolean splitlyr = false;
		if(args.length > 2){
			for(String a : args){
				if(a.equals("-splitlayers")){
					splitlyr = true;
					break;
				}
			}
		}
		
		try{
			NUSALSeq seq = null;
			String incase = inpath.toLowerCase();
			if(incase.endsWith(".mus") || incase.endsWith(".mml")){
				System.err.println("Input path has MML script extension. Reading as MML script...");
				BufferedReader br = new BufferedReader(new FileReader(inpath));
				seq = NUSALSeq.readMMLScript(br);
				br.close();
			}
			else if(incase.endsWith(".bin") || incase.endsWith(".aseq") || incase.endsWith(".com") || incase.endsWith(".useq") || incase.endsWith(".buseq")){
				System.err.println("Input path has bin N64 seq extension. Reading as binary...");
				seq = NUSALSeq.readNUSALSeq(FileBuffer.createBuffer(inpath, true));
			}
			else{
				System.err.println("Input path extension not recognized. Attempting to read as binary...");
				seq = NUSALSeq.readNUSALSeq(FileBuffer.createBuffer(inpath, true));
			}
			
			if(!NUSMMLSplitter.splitSeq(seq, splitlyr, outstem)){
				System.err.println("Split failed. See stderr for details.");
			}
			else{
				System.err.println("Split succeeded (at least as far as the computer knows). Congrats!");
			}
			
		}
		catch(Exception ex){
			ex.printStackTrace();
			System.exit(1);
		}

	}

}
