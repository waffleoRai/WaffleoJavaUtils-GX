package waffleoRai_SeqSound.n64al;

import java.io.BufferedReader;
import java.io.FileReader;

import waffleoRai_SeqSound.MIDI;
import waffleoRai_SeqSound.SeqVoiceCounter;

public class Test_CompileNUSALSeqMML {

	public static void main(String[] args) {
		if(args.length < 2){
			System.err.println("Need at least two arguments: input.mus output_stem");
			System.exit(1);
		}
		
		try{
			String inpath = args[0];
			String outstem = args[1];
			
			System.err.println("Input: " + inpath);
			
			String outbin = outstem + ".bin";
			String outmid = outstem + ".mid";
			
			BufferedReader reader = new BufferedReader(new FileReader(inpath));
			NUSALSeq seq = NUSALSeq.readMMLScript(reader);
			reader.close();
			
			seq.writeTo(outbin);
			System.err.println("Z64 Seq Bin Written to: " + outbin);
			
			MIDI midi = seq.toMidi();
			midi.writeMIDI(outmid);
			System.err.println("MIDI Translation Written to: " + outmid);
			
			//Count max simultaneous voices
			SeqVoiceCounter vc = new SeqVoiceCounter();
			seq.playTo(vc, false);
			int vcount = vc.getMaxTotalVoiceCount();
			System.err.println("Maximum simultaneous voices: " + vcount);
		}
		catch(Exception ex){
			ex.printStackTrace();
			System.exit(1);
		}

	}

}
