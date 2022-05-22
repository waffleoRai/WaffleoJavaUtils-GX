package waffleoRai_SeqSound.n64al;

import java.io.BufferedWriter;
import java.io.FileWriter;

import waffleoRai_SeqSound.MIDI;
import waffleoRai_SeqSound.MIDIInterpreter;
import waffleoRai_SeqSound.n64al.seqgen.NUSALSeqGenerator;
import waffleoRai_Utils.FileBuffer;

public class Test_Midi2NUSMML {

	public static void main(String[] args) {
		if(args.length < 2){
			System.err.println("Usage: <input.mid> <outputpath> (-bin) (-splitmml) (-loop <sttick>,<endtick>)");
			System.exit(1);
		}
		
		String inpath = args[0];
		String outpath = args[1];
		boolean outputbin = false;
		boolean splitmml = false;
		int loopst = -1; int looped = -1;
		for(int i = 2; i < args.length; i++){
			String arg = args[i];
			if(arg.equals("-bin")) outputbin = true;
			else if(arg.equals("-splitmml")) splitmml = true;
			else if(arg.equals("-loop")){
				if(i+1 >= args.length){
					System.err.println("-loop flag requires parameter!");
					System.exit(1);
				}
				String val = args[i+1];
				i++;
				
				String[] split = val.split(",");
				try{
					loopst = Integer.parseInt(split[0]);
					if(split.length > 1) looped = Integer.parseInt(split[1]);
				}
				catch(NumberFormatException ex){
					System.err.println("-loop parameter must be valid integer or pair of valid integers!");
					System.err.println("Usage: <input.mid> <outputpath> (-bin) (-splitmml) (-loop <sttick>,<endtick>)");
					System.exit(1);
				}
			}
		}
		
		try{
			MIDI midi = new MIDI(FileBuffer.createBuffer(inpath, true));
			if(loopst >= 0 && looped < 0){
				looped = (int)midi.getSequence().getTickLength();
			}
			
			NUSALSeqGenerator target = new NUSALSeqGenerator();
			target.setTimebase(midi.getSequence().getResolution());
			target.setLoop(loopst, looped);
			
			MIDIInterpreter midirdr = new MIDIInterpreter(midi.getSequence());
			midirdr.readMIDITo(target);
			target.complete();
			
			NUSALSeq nusseq = target.getOutput();
			if(outputbin){
				nusseq.writeTo(outpath);
			}
			else{
				if(splitmml){
					String outstem = outpath;
					int lastdot = outpath.lastIndexOf('.');
					if(lastdot > 0){
						outstem = outpath.substring(0, lastdot);
					}
					NUSMMLSplitter.splitSeq(nusseq, false, outstem);
				}
				else{
					BufferedWriter bw = new BufferedWriter(new FileWriter(outpath));
					nusseq.exportMMLScript(bw);
					bw.close();
				}
			}
		}
		catch(Exception ex){
			ex.printStackTrace();
			System.exit(1);
		}

	}

}
