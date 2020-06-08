package waffleoRai_soundbank.procyon;

import waffleoRai_Utils.FileBuffer;
import waffleoRai_soundbank.Region;
import waffleoRai_soundbank.SoundbankNode;
import waffleoRai_soundbank.adsr.Attack;
import waffleoRai_soundbank.adsr.Decay;
import waffleoRai_soundbank.adsr.Release;
import waffleoRai_soundbank.adsr.Sustain;

public class SWDRegion {
	
	public static final int REC_SIZE = 48;
	private static final double LOG10_2 = 0.301029996;
	
	private int index;
	
	private int bendRange;
	private byte minKey;
	private byte maxKey;
	private byte minVel;
	private byte maxVel;
	
	private int wavi_idx;
	
	private byte fineTune;
	private byte coarseTune;
	private byte unityKey;
	
	private byte volume;
	private byte pan;
	private byte keygroup;
	
	private boolean envFlag;
	private byte envMult;
	private byte attVol;
	private byte att;
	private byte dec;
	private byte sus;
	private byte hold;
	private byte dec2;
	private byte rel;
	
	public SWDRegion(int idx){
		index = idx;
		bendRange = 2;
		minKey = 0; maxKey = 127;
		minVel = 0; maxVel = 127;
		wavi_idx = 0;
		unityKey = 60;
		volume = 127;
		pan = 0x40;
		h = -1;
	}
	
	public static SWDRegion readFromSWD(FileBuffer data, long offset){
	
		long cpos = offset + 1;
		int idx = Byte.toUnsignedInt(data.getByte(cpos++));
		
		SWDRegion reg = new SWDRegion(idx);
		reg.bendRange = Byte.toUnsignedInt(data.getByte(cpos++)); cpos++; //Unknown
		reg.minKey = data.getByte(cpos++);
		reg.maxKey = data.getByte(cpos++);
		cpos += 2;
		reg.minVel = data.getByte(cpos++);
		reg.maxVel = data.getByte(cpos++);
		cpos += 8;
		reg.wavi_idx = Short.toUnsignedInt(data.shortFromFile(cpos)); cpos+=2;
		reg.fineTune = data.getByte(cpos++);
		reg.coarseTune = data.getByte(cpos++);
		reg.unityKey = data.getByte(cpos++); cpos++; //Skip key transpose
		reg.volume = data.getByte(cpos++);
		reg.pan = data.getByte(cpos++);
		reg.keygroup = data.getByte(cpos++);
		
		//Skip 5
		cpos += 5;
		
		reg.envFlag = (data.getByte(cpos++) != 0);
		reg.envMult = data.getByte(cpos++);
		cpos += 6; //Skip 6 bytes unknown
		reg.attVol = data.getByte(cpos++);
		reg.att = data.getByte(cpos++);
		reg.dec = data.getByte(cpos++);
		reg.sus = data.getByte(cpos++);
		reg.hold = data.getByte(cpos++);
		reg.dec2 = data.getByte(cpos++);
		reg.rel = data.getByte(cpos++);
		
		return reg;
	}

	public int getIndex(){return index;}
	public int getWAVIIndex(){return wavi_idx;}
	public int getBendRange(){return bendRange;}
	public byte getMinKey(){return minKey;}
	public byte getMaxKey(){return maxKey;}
	public byte getMinVelocity(){return minVel;}
	public byte getMaxVelocity(){return maxVel;}
	public byte getUnityKey(){return unityKey;}
	public int getRawCoarseTune(){return (int)coarseTune;}
	public int getRawFineTune(){return (int)fineTune;}
	public byte getVolume(){return volume;}
	public byte getPan(){return pan;}
	public int getKeyGroup(){return (int)keygroup;}
	public boolean getEnvelopeFlag(){return envFlag;}
	public byte getEnvelopeMultiplier(){return envMult;}
	public byte getAttackVolume(){return attVol;}
	public byte getRawAttack(){return att;}
	public byte getRawDecay(){return dec;}
	public byte getRawSustain(){return sus;}
	public byte getRawHold(){return hold;}
	public byte getRawDecay2(){return dec2;}
	public byte getRawRelease(){return rel;}
	
	private int scaled_coarse;
	private int scaled_fine;
	
	public void calculateScaledTuning(int samplerate){
		//int raw = ((int)coarseTune * 100) + (Byte.toUnsignedInt(fineTune) & 0x7F);
		int raw = ((int)coarseTune * 100) + (int)fineTune;
		double srratio = (double)SWD.SAMPLERATE_SCALE/(double)samplerate;
		double val = 1200.0 * (Math.log10(srratio)/LOG10_2);
		
		int srtune = (int)Math.round(val);
		int net = srtune + raw;
		scaled_coarse = net/100;
		scaled_fine = net - (scaled_coarse*100);
		//System.err.println("Wave SR: " + samplerate + " || Input: " + coarseTune + "st, " + fineTune + " cents | Output: " + scaled_coarse + "st, " + scaled_fine + "cents");
	}
	
	public int getSRScaledCoarseTune(){
		return scaled_coarse;
	}
	
	public int getSRScaledFineTune(){
		return scaled_fine;
	}
	
	private Attack a;
	private Decay d;
	private Sustain s;
	private Release r;
	private int h;
	
	public Attack getAttack(){
		if(a != null) return a;
		a = SWDADSR.getAttack(attVol, att, envMult);
		return a;
	}
	
	public Decay getDecay(){
		if(d != null) return d;
		d = SWDADSR.getDecay(dec, envMult);
		return d;
	}
	
	public int getHold(){
		if(h >= 0) return h;
		h = SWDADSR.getDuration_ms(hold, envMult);
		return h;
	}
	
	public Sustain getSustain(){
		if(s != null) return s;
		s = SWDADSR.getSustain(sus, dec2, envMult);
		return s;
	}
	
	public Release getRelease(){
		if(r != null) return r;
		r = SWDADSR.getRelease(rel, envMult);
		return r;
	}
	
	/*----- Conversion -----*/
	
	public void copyToRegion(Region r){
		r.setPitchBend(-1 * bendRange, bendRange);
		r.setMinKey(minKey); r.setMaxKey(maxKey);
		r.setMinVelocity(minVel); r.setMaxVelocity(maxVel);
		
		//r.setUnityKey((byte)(unityKey - scaled_coarse));
		//r.setFineTune((byte)scaled_fine);
		r.setUnityKey(unityKey);
		
		int val = (int)Math.round(((double)volume/127.0) * (double)0x7FFFFFFF);
		r.setVolume(val);
		
		val = (int)pan - 0x40;
		val = (int)Math.round(((double)pan/64.0) * (double)0x7FFF);
		r.setPan((short)val);
		
		r.setAttack(getAttack());
		r.setDecay(getDecay());
		r.setSustain(getSustain());
		r.setRelease(getRelease());
		r.setHold(getHold());
	}
	
	public void toSoundbankNode(SoundbankNode parent){
		SoundbankNode reg = new SoundbankNode(parent, "SWDREG_" + String.format("%03d", index), SoundbankNode.NODETYPE_TONE);
		
		reg.addMetadataEntry("Bend Range", bendRange + " semitones");
		reg.addMetadataEntry("Key Range", minKey + " - " + maxKey);
		reg.addMetadataEntry("Velocity Range", minVel + " - " + maxVel);
		reg.addMetadataEntry("WAVI Index", Integer.toString(wavi_idx));
		reg.addMetadataEntry("Root Key", Integer.toString(unityKey));
		reg.addMetadataEntry("Volume", Integer.toString(volume));
		reg.addMetadataEntry("Pan", String.format("0x%02x", pan));
		reg.addMetadataEntry("Keygroup", Integer.toString(keygroup));
		
		if(envFlag)reg.addMetadataEntry("Env Flag", "Set");
		else reg.addMetadataEntry("Env Flag", "Unset");
		
		reg.addMetadataEntry("Envelope Multiplier", Integer.toString(envMult));
		reg.addMetadataEntry("Attack Level", "0x" + String.format("%02x", this.attVol));
		reg.addMetadataEntry("Attack", "0x" + String.format("%02x", att) + " (" + getAttack().getTime() + " ms)");
		reg.addMetadataEntry("Decay", "0x" + String.format("%02x", dec) + " (" + getDecay().getTime() + " ms)");
		reg.addMetadataEntry("Hold", "0x" + String.format("%02x", hold) + " (" + getHold() + " ms)");
		reg.addMetadataEntry("Sustain Level", "0x" + String.format("%02x", this.sus));
		reg.addMetadataEntry("Release", "0x" + String.format("%02x", rel) + " (" + getRelease().getTime() + " ms)");
		//reg.addMetadataEntry("Archdecay", "0x" + String.format("%02x", this.dec2));
		if(dec2 != 0x7F){
			reg.addMetadataEntry("Sustain Time", "0x" + String.format("%02x", this.dec2) + " (" + getSustain().getTime() + " ms)");
		}
	}
	
	/*----- Debug -----*/
	
	public void debugPrint(int tabs){
		StringBuilder sb = new StringBuilder(16);
		for(int i = 0; i < tabs; i++) sb.append('\t');
		String tabstr = sb.toString();
		
		System.err.println(tabstr + "Internal Index: " + this.index);
		System.err.println(tabstr + "Bend Range: " + this.bendRange + " semitones");
		System.err.println(tabstr + "Key Range: " + this.minKey + " - " + this.maxKey);
		System.err.println(tabstr + "Velocity Range: " + this.minVel + " - " + this.maxVel);
		System.err.println(tabstr + "Root Key: " + this.unityKey);
		System.err.println(tabstr + "Coarse Tune: " + this.coarseTune + " semitones");
		System.err.println(tabstr + "Fine Tune: " + this.fineTune + " cents");
		System.err.println(tabstr + "Volume: " + this.volume);
		System.err.println(tabstr + "Pan: 0x" + String.format("%02x", pan));
		System.err.println(tabstr + "wavi Index: " + this.wavi_idx);
		System.err.println(tabstr + "Keygroup: " + this.keygroup);
		System.err.println(tabstr + "Envelope Flag: " + this.envFlag);
		System.err.println(tabstr + "Envelope Multiplier: " + this.envMult);
		System.err.println(tabstr + "Attack Level: 0x" + String.format("%02x", this.attVol));
		System.err.println(tabstr + "Attack: 0x" + String.format("%02x", att) + " (" + getAttack().getTime() + " ms)");
		System.err.println(tabstr + "Hold: 0x" + String.format("%02x", hold) + " (" + getHold() + " ms)");
		System.err.println(tabstr + "Decay: 0x" + String.format("%02x", dec) + " (" + getDecay().getTime() + " ms)");
		System.err.println(tabstr + "Sustain: 0x" + String.format("%02x", this.sus));
		System.err.println(tabstr + "Release: 0x" + String.format("%02x", rel) + " (" + getRelease().getTime() + " ms)");
		System.err.println(tabstr + "Archdecay: 0x" + String.format("%02x", this.dec2));
	}
	
}
