package waffleoRai_SeqSound.n64al;

import java.io.OutputStreamWriter;

import waffleoRai_SeqSound.SeqVoiceCounter;
import waffleoRai_Utils.FileBuffer;

public class Test_SeqDiagnostics {

	public static void main(String[] args){
		
		String inpath = args[0];
		
		try{
			System.err.println("Reading Sequence...");
			NUSALSeq nseq = NUSALSeq.readNUSALSeq(FileBuffer.createBuffer(inpath, true));
			
			System.err.println("Running through voice counter (playthrough)");
			SeqVoiceCounter vctr = new SeqVoiceCounter();
			nseq.playTo(vctr, false);
			System.err.println("Voice load found: " + vctr.getMaxTotalVoiceCount());
			
			System.err.println("Counting max layers per channel...");
			int lyrch = nseq.getMaxLayersPerChannel();
			System.err.println("Layer count: " + lyrch);
			
			System.err.println("Testing serialization...");
			FileBuffer seqdat = nseq.getSerializedData();
			if(seqdat != null){
				System.err.println("Reserialized size: 0x" + Long.toHexString(seqdat.getFileSize()));
			}
			else{
				System.err.println("Serialization failed! (Returned null)");
			}		
			
			//Try to output MML
			System.out.println();
			OutputStreamWriter writer = new OutputStreamWriter(System.out);
			//nseq.exportMMLScript(writer, true);
			writer.flush();
			
		}catch(Exception ex){
			ex.printStackTrace();
			System.exit(1);
		}
		
	}
	
}
