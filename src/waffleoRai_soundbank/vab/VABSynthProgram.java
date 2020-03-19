package waffleoRai_soundbank.vab;

import waffleoRai_SoundSynth.SynthProgram;
import waffleoRai_SoundSynth.SynthSampleStream;
import waffleoRai_SoundSynth.soundformats.game.VABSynthSampleStream;
import waffleoRai_soundbank.vab.PSXVAB.Program;
import waffleoRai_soundbank.vab.PSXVAB.Tone;

public class VABSynthProgram implements SynthProgram{

	private PSXVAB bank;
	private VABSampleMap samples;
	
	private int progIndex;
	
	public VABSynthProgram(PSXVAB bank, VABSampleMap map, int pidx)
	{
		this.bank = bank;
		samples = map;
		progIndex = pidx;
	}
	
	public SynthSampleStream getSampleStream(byte pitch, byte velocity) throws InterruptedException
	{
		Program p = bank.getProgram(progIndex);
		Tone t = p.getTone(pitch);
		return new VABSynthSampleStream(samples.openSampleStream(t.getSampleIndex()), bank, p, t, pitch, velocity, 44100);
	}
	
	public SynthSampleStream getSampleStream(byte pitch, byte velocity, float targetSampleRate) throws InterruptedException
	{
		Program p = bank.getProgram(progIndex);
		if(p == null) System.err.println("Error: Program " + progIndex + " doesn't exist?");
		Tone t = p.getTone(pitch);
		return new VABSynthSampleStream(samples.openSampleStream(t.getSampleIndex()), bank, p, t, pitch, velocity, targetSampleRate);
	}
	
}
