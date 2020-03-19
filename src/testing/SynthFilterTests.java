package testing;

import waffleoRai_Sound.WAV;
import waffleoRai_SoundSynth.general.UnbufferedWindowedSincInterpolator;
import waffleoRai_SoundSynth.soundformats.PCMSampleStream;
import waffleoRai_SoundSynth.soundformats.WAVWriter;

public class SynthFilterTests {

	public static void main(String[] args) {

		String inpath1 = "C:\\Users\\Blythe\\Music\\OSTs\\Midi Rips\\Majora's Mask\\Labeled\\Music\\03_MainOrchestra\\Samples\\Bank03_Track01_S1.wav";
		String inpath2 = "C:\\Users\\Blythe\\Music\\OSTs\\Midi Rips\\Majora's Mask\\Labeled\\Music\\03_MainOrchestra\\Samples\\Bank03_Track11_S1.wav";
	
		String outdir = "C:\\Users\\Blythe\\Documents\\Desktop\\out";
		//String outpath1 = outdir + "\\MMSample01_copy_looptwice.wav";
		String outpath1 = outdir + "\\MMSample01_down6_looptwice.wav";
		
		try
		{
			WAV inwav = new WAV(inpath1);
			PCMSampleStream instream = new PCMSampleStream(inwav);
			UnbufferedWindowedSincInterpolator interpol = new UnbufferedWindowedSincInterpolator(instream, 2);
			interpol.setPitchShift(-600);
			WAVWriter writer = new WAVWriter(interpol, outpath1);
			writer.write(instream.framesForLoops(2) * 2);
			//writer.write(100);
			//WAVWriter writer = new WAVWriter(instream, outpath1);
			//writer.write(instream.framesForLoops(2));
			writer.complete();
		}
		catch(Exception e)
		{
			e.printStackTrace();
			System.exit(1);
		}
		
		
	
	}

}
