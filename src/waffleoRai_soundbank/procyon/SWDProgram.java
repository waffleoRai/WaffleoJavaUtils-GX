package waffleoRai_soundbank.procyon;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import waffleoRai_SoundSynth.AudioSampleStream;
import waffleoRai_SoundSynth.SoundSampleMap;
import waffleoRai_SoundSynth.SynthProgram;
import waffleoRai_SoundSynth.SynthSampleStream;
import waffleoRai_SoundSynth.soundformats.game.SWDSynthStream;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_soundbank.Region;
import waffleoRai_soundbank.SimpleInstrument;

//Toss osc if destination is none!

public class SWDProgram implements SynthProgram{

	/*----- Instance Variables -----*/
	
	private int index;
	private byte volume;
	private byte pan;
	
	private SWDOscillator[] osc;
	private SWDRegion[] regions;
	
	private SoundSampleMap sounds;
	private Map<Byte, Map<Byte, SWDRegion>> regcache;
	
	/*----- Construction -----*/
	
	public SWDProgram(int idx, int region_alloc){
		index = idx;
		volume = 0x7F;
		pan = 0x40;
		osc = new SWDOscillator[4];
		regions = new SWDRegion[region_alloc];
	}
	
	/*----- Parse -----*/
	
	public static SWDProgram readFromSWD(FileBuffer data, long offset){
		
		long cpos = offset;
		int idx = Short.toUnsignedInt(data.shortFromFile(cpos)); cpos+=2;
		int rcount = Short.toUnsignedInt(data.shortFromFile(cpos)); cpos+=2;
		
		SWDProgram prog = new SWDProgram(idx, rcount);
		
		prog.volume = data.getByte(cpos++);
		prog.pan = data.getByte(cpos++);
		cpos += 5; //Unknown
		int lfo_ct = Byte.toUnsignedInt(data.getByte(cpos++));
		prog.osc = new SWDOscillator[lfo_ct];
		cpos += 4; //Unknown/padding
		
		//LFO table
		for(int i = 0; i < lfo_ct; i++){
			SWDOscillator o = SWDOscillator.readSWDRecord(data, cpos);
			if(o != null && o.getDestination() != SWD.OSC_DEST_NONE) prog.osc[i] = o;
			cpos += SWDOscillator.RECORD_SIZE;
		}
		
		cpos+=16; //Delimiter
		
		//Regions
		for(int i = 0; i < rcount; i++){
			SWDRegion reg = SWDRegion.readFromSWD(data, cpos);
			cpos += SWDRegion.REC_SIZE;
			prog.regions[i] = reg;
		}
		
		return prog;
	}
	
	/*----- Getters -----*/
	
	public int getIndex(){return index;}
	public byte getVolume(){return volume;}
	public byte getPan(){return pan;}
	public int countRegions(){return regions.length;}
	
	public List<SWDOscillator> getOscillators(){
		List<SWDOscillator> list = new ArrayList<SWDOscillator>(4);
		if(osc == null) return list;
		
		for(int i = 0; i < osc.length; i++){
			if(osc[i] != null) list.add(osc[i]);
		}
		
		return list;
	}
	
	/*----- Setters -----*/
	
	protected void scaleTuning(SWDWave[] sounds){
		for(int i = 0; i < regions.length; i++){
			if(regions[i] != null){
				regions[i].calculateScaledTuning(sounds[regions[i].getWAVIIndex()].getSampleRate());
			}
		}
	}
	
	public void linkSoundMap(SoundSampleMap map){
		sounds = map;
	}
	
	public void clearSoundMapLink(){
		sounds = null;
	}
	
	/*----- Playback -----*/
	
	private void initializeCache(){
		//System.err.println("SWDProgram.initializeCache || Called!");
		if(regcache != null) clearCache();
		regcache = new TreeMap<Byte, Map<Byte, SWDRegion>>();
		//System.err.println("SWDProgram.initializeCache || Cache allocated");
		
		if(regions == null) return;
		for(int i = 0; i < regions.length; i++){
			//System.err.println("SWDProgram.initializeCache || Mapping region " + i);
			if(regions[i] != null){
				byte mink = regions[i].getMinKey();
				byte maxk = regions[i].getMaxKey();
				byte minv = regions[i].getMinVelocity();
				byte maxv = regions[i].getMaxVelocity();
				//System.err.println("SWDProgram.initializeCache || Region " + i + " key range: " + mink + " - " + maxk);
				//System.err.println("SWDProgram.initializeCache || Region " + i + " vel range: " + minv + " - " + maxv);
				for(int k = mink; k <= maxk; k++){
					//System.err.println("SWDProgram.initializeCache || Key = " + k);
					byte bk = (byte)k;
					Map<Byte, SWDRegion> kmap = regcache.get(bk);
					if(kmap == null){
						kmap = new TreeMap<Byte, SWDRegion>();
						regcache.put(bk, kmap);
					}
					//System.err.println("SWDProgram.initializeCache || Key = " + k);
					for(int v = minv; v <= maxv; v++){
						//System.err.println("SWDProgram.initializeCache || Vel = " + v);
						kmap.put((byte)v, regions[i]);
					}
				}
			}
		}
	}
	
	private SWDRegion getRegion(byte key, byte vel){
		//System.err.println("SWDProgram.getRegion || Called!");
		if(regcache == null) initializeCache();
		//System.err.println("SWDProgram.getRegion || Cache initialized!");
		
		Map<Byte, SWDRegion> kmap = regcache.get(key);
		if(kmap != null){
			SWDRegion reg = kmap.get(vel);
			if(reg != null) return reg;
		}
		
		//Cache miss
		return null;
	}

	public SynthSampleStream getSampleStream(byte pitch, byte velocity) throws InterruptedException {
		return getSampleStream(pitch, velocity, 44100);
	}

	@Override
	public SynthSampleStream getSampleStream(byte pitch, byte velocity, float targetSampleRate)
			throws InterruptedException {

		//System.err.println("SWDProgram.getSampleStream || Called!");
		SWDRegion reg = getRegion(pitch, velocity);
		if(reg == null) return null;
		//System.err.println("SWDProgram.getSampleStream || Region found!");
		
		if(sounds == null) return null;
		AudioSampleStream rawstr = sounds.openSampleStream(reg.getWAVIIndex());
		//System.err.println("SWDProgram.getSampleStream || Audio stream opened!");
		
		SWDSynthStream str = new SWDSynthStream(rawstr, targetSampleRate);
		str.setPlayData(pitch, velocity);
		str.setArticulationData(reg, this);
		//System.err.println("SWDProgram.getSampleStream || Synth stream generated!");
		
		return str;
	}
	
	public void clearCache(){
		if(regcache == null) return;
		for(Map<Byte, SWDRegion> val : regcache.values()){
			val.clear();
		}
		regcache.clear();
		regcache = null;
	}

	/*----- Conversion -----*/
	
	public void copyToInstrument(SimpleInstrument inst){
		int val = (int)Math.round(((double)volume/127.0) * (double)0x7FFFFFFF);
		inst.setMasterVolume(val);
		
		val = (int)pan - 0x40;
		val = (int)Math.round(((double)pan/64.0) * (double)0x7FFF);
		inst.setMasterPan(val);
		
		//Won't do osc because it's a pain to represent in SF2
		
		//Regions
		for(int i = 0; i < regions.length; i++){
			if(regions[i] != null){
				int ridx = inst.newRegion(SWD.generateSoundKey(regions[i].getWAVIIndex()));
				Region r = inst.getRegion(ridx);
				regions[i].copyToRegion(r);
			}
		}
	}
	
	/*----- Debug -----*/
	
	public void debugPrint(int tabs){
		StringBuilder sb = new StringBuilder(16);
		for(int i = 0; i < tabs; i++) sb.append('\t');
		String tabstr = sb.toString();
		
		System.err.println(tabstr + "Internal Index: " + this.index);
		System.err.println(tabstr + "Volume: " + this.volume);
		System.err.println(tabstr + "Pan: 0x" + String.format("%02x", pan));
		System.err.println(tabstr + "LFOs ------- ");
		for(int i = 0; i < osc.length; i++){
			System.err.println(tabstr + "-> LFO " + i);
			if(osc[i] != null){
				osc[i].debugPrint(tabs+1);
			}
			else System.err.println(tabstr + "\t[Null]");
		}
		System.err.println(tabstr + "Regions ------- ");
		for(int i = 0; i < regions.length; i++){
			System.err.println(tabstr + "-> Region " + i);
			if(regions[i] != null){
				regions[i].debugPrint(tabs+1);
			}
			else System.err.println(tabstr + "\t[Null]");
		}
	}
	
}
