package waffleoRai_soundbank.procyon;

import waffleoRai_SoundSynth.Oscillator;
import waffleoRai_SoundSynth.general.BasicLFO;
import waffleoRai_SoundSynth.general.RandomLFO;
import waffleoRai_SoundSynth.general.SawLFO;
import waffleoRai_SoundSynth.general.SineLFO;
import waffleoRai_SoundSynth.general.SquareLFO;
import waffleoRai_SoundSynth.general.TriangleLFO;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_soundbank.Modulator;

public class SWDOscillator{
	
	public static final int RECORD_SIZE = 16;
	
	private int shape; //Pseudo enum -- see SWD class
	private int dest; //Pseudo enum -- see SWD class
	
	private int rate;
	private int amt;
	private int delay; //ms
	
	public SWDOscillator(){
		//Default values
		shape = SWD.OSC_TYPE_SINE;
		dest = SWD.OSC_DEST_NONE;
		rate = 20;
		amt = 0;
		delay = 0;
	}
	
	public static SWDOscillator readSWDRecord(FileBuffer data, long offset){
		
		SWDOscillator osc = new SWDOscillator();
		
		long cpos = offset + 2;
		osc.dest = Byte.toUnsignedInt(data.getByte(cpos++));
		osc.shape = Byte.toUnsignedInt(data.getByte(cpos++));
		osc.rate = Short.toUnsignedInt(data.shortFromFile(cpos)); cpos+=2;
		cpos+=2;
		osc.amt = Short.toUnsignedInt(data.shortFromFile(cpos)); cpos+=2;
		osc.delay = Short.toUnsignedInt(data.shortFromFile(cpos));
		
		return osc;
	}

	public int getShape(){return shape;}
	public int getDestination(){return dest;}
	public int getRate(){return rate;}
	public int getAmount(){return amt;}
	public int getDelay(){return delay;}
	
	public Oscillator spawnOscillator(int sampleRate){

		BasicLFO mylfo = null;
		switch(shape){
		case SWD.OSC_TYPE_NOISE:
		case SWD.OSC_TYPE_RANDOM:
			mylfo = new RandomLFO(sampleRate); break;
		case SWD.OSC_TYPE_SAW: mylfo = new SawLFO(sampleRate); break;
		case SWD.OSC_TYPE_SINE: mylfo = new SineLFO(sampleRate); break;
		case SWD.OSC_TYPE_SQUARE: mylfo = new SquareLFO(sampleRate); break;
		case SWD.OSC_TYPE_TRIANGLE: mylfo = new TriangleLFO(sampleRate); break;
		}
		
		//Load other parameters
		mylfo.setAmplitude((double)amt/(double)0x7FFF);
		mylfo.setDelay(delay);
		mylfo.setLFORate(rate);
		
		return mylfo;
	}
	
	/*----- Conversion -----*/
	
	public Modulator getModulator(){
		//TODO
		return null;
	}
	
	/*----- Debug -----*/
	
	public void debugPrint(int tabs){
		StringBuilder sb = new StringBuilder(16);
		for(int i = 0; i < tabs; i++) sb.append('\t');
		String tabstr = sb.toString();
		
		String str = "";
		switch(dest){
		case SWD.OSC_DEST_NONE: str = "[None]"; break;
		case SWD.OSC_DEST_VOLUME: str = "Volume"; break;
		case SWD.OSC_DEST_PAN: str = "Pan"; break;
		case SWD.OSC_DEST_PITCH: str = "Pitch"; break;
		case SWD.OSC_DEST_LPF: str = "LPF"; break;
		default: str = "[Unknown](" + dest + ")"; break;
		}
		System.err.println(tabstr + "Destination: " + str);
		
		switch(shape){
		case SWD.OSC_TYPE_SINE: str = "Sine"; break;
		case SWD.OSC_TYPE_SQUARE: str = "Square"; break;
		case SWD.OSC_TYPE_TRIANGLE: str = "Triangle"; break;
		case SWD.OSC_TYPE_SAW: str = "Saw"; break;
		case SWD.OSC_TYPE_NOISE: str = "Noise"; break;
		case SWD.OSC_TYPE_RANDOM: str = "Random"; break;
		default: str = "[Unknown](" + shape + ")"; break;
		}
		System.err.println(tabstr + "Type: " + str);
		
		System.err.println(tabstr + "Rate: " + rate + " hz");
		System.err.println(tabstr + "Amount: " + String.format("0x%04x", amt));
		System.err.println(tabstr + "Delay: " + delay + " ms");
	}
	

}
