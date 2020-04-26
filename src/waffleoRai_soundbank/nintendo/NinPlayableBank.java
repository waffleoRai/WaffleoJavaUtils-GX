package waffleoRai_soundbank.nintendo;

import waffleoRai_Sound.nintendo.WaveArchive;
import waffleoRai_SoundSynth.SynthBank;
import waffleoRai_SoundSynth.SynthProgram;

public class NinPlayableBank implements SynthBank{
	
	/*----- Constants -----*/
	
	/*----- InstanceVariables -----*/
	
	private boolean closed = false;
	
	private NinPlayableProgram[] programs;
	private SBNKSampleMap samples;
	
	private String name;
	
	/*----- Construction -----*/
	
	public NinPlayableBank(NinBank bnk, WaveArchive war){
		samples = new SBNKSampleMap(war, 0);
		int patches = bnk.countSurfacePatches();
		programs = new NinPlayableProgram[patches];
		
		for(int i = 0; i < patches; i++){
			NinBankNode node = bnk.getSurfaceNodeAt(i);
			if(node != null){
				programs[i] = new NinPlayableProgram(node, samples);
			}
		}
		
	}
	
	public NinPlayableBank(NinBank bnk, WaveArchive[] warcs){
		samples = new SBNKSampleMap(warcs[0], 0);
		for(int i = 0; i < warcs.length; i++){
			if(warcs[i] != null) samples.loadWaveArchive(warcs[i], i);
		}
		
		int patches = bnk.countSurfacePatches();
		programs = new NinPlayableProgram[patches];
		
		for(int i = 0; i < patches; i++){
			NinBankNode node = bnk.getSurfaceNodeAt(i);
			if(node != null){
				programs[i] = new NinPlayableProgram(node, samples);
			}
		}
		
	}
	
	/*----- Getters -----*/
	
	@Override
	public SynthProgram getProgram(int bankIndex, int programIndex) {
		int idx = programIndex + (bankIndex << 8);
		if(idx < 0 || idx >= programs.length) return null;
		//System.err.println("NinPlayableBank.getProgram || Bank = " + bankIndex + ", Prog = " + programIndex + " returning null? " + (programs[programIndex] == null));
		return programs[idx];
	}
	
	public String getName(){return name;}
	
	public String toString(){return name;}
	
	/*----- Setters -----*/
	
	public void addSWAR(WaveArchive warc, int idx){
		samples.loadWaveArchive(warc, idx);
	}
	
	public void setName(String s){name = s;}
	
	public void free(){
		if(closed) return;
		
		closed = true;
		samples.free();
		samples = null;
		
		for(int i = 0; i < programs.length; i++){
			if(programs[i] != null) programs[i].free();
			programs[i] = null;
		}
		
		programs = null;
	}
	
}
