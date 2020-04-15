package waffleoRai_soundbank.nintendo;

import waffleoRai_SeqSound.ninseq.NinSeqADSR;
import waffleoRai_Sound.nintendo.NinSound;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_soundbank.SimpleInstrument;
import waffleoRai_soundbank.SimplePreset;
import waffleoRai_soundbank.SoundbankNode;
import waffleoRai_soundbank.SimpleInstrument.InstRegion;
import waffleoRai_soundbank.adsr.ADSRMode;
import waffleoRai_soundbank.adsr.Attack;
import waffleoRai_soundbank.adsr.Decay;
import waffleoRai_soundbank.adsr.Release;
import waffleoRai_soundbank.adsr.Sustain;

//ADSR scaling based off the DS scaling from VGMTrans (NDSInstrSet.cpp)
//Have no idea if it even begins to apply to the Wii

public class NinTone {
	
	private int warNumber;
	private int waveNumber;
	
	private int attack; //Sign extend from byte!
	private int decay; //Sign extend from byte!
	private int sustain; //Sign extend from byte!
	private int release; //Sign extend from byte!
	private int hold; //Sign extend from byte!
	
	private int dataLocationType; //Pseudo enum
	private int noteOffType; //Pseudo enum
	
	private int altAssign;
	private int originalKey;
	private int volume;
	private int pan;
	private int surroundPan;
	
	private float pitch; //Fine tune?
	
	private byte minNote;
	private byte maxNote;
	
	public NinTone()
	{
		warNumber = 0;
		waveNumber = -1;
		attack = 0;
		decay = 0;
		sustain = 0;
		release = 0;
		hold = 0;
		dataLocationType = NinSound.WAVELOC_TYPE_INDEX;
		noteOffType = NinSound.NOTEOFF_TYPE_RELEASE;
		altAssign = 0;
		originalKey = 60;
		volume = 0x7F;
		pan = 0x40;
		surroundPan = 0x40;
		pitch = 0;
		minNote = 0x00;
		maxNote = 0x7F;
	}
	
	public static NinTone readRTone(FileBuffer src, long stoff)
	{
		if(src == null) return null;
		NinTone t = new NinTone();
		//System.err.println("Input Offset 0x" + Long.toHexString(stoff));
		long cpos = stoff;
		t.waveNumber = src.intFromFile(cpos); cpos+=4;
		t.attack = (int)src.getByte(cpos); cpos++;
		t.decay = (int)src.getByte(cpos); cpos++;
		t.sustain = (int)src.getByte(cpos); cpos++;
		t.release = (int)src.getByte(cpos); cpos++;
		t.hold = (int)src.getByte(cpos); cpos++;
		t.dataLocationType = Byte.toUnsignedInt(src.getByte(cpos)); cpos++;
		t.noteOffType = Byte.toUnsignedInt(src.getByte(cpos)); cpos++;
		t.altAssign = Byte.toUnsignedInt(src.getByte(cpos)); cpos++;
		t.originalKey = Byte.toUnsignedInt(src.getByte(cpos)); cpos++;
		t.volume = (int)src.getByte(cpos); cpos++;
		t.pan = (int)src.getByte(cpos); cpos++;
		t.surroundPan = (int)src.getByte(cpos); cpos++;
		t.pitch = Float.intBitsToFloat(src.intFromFile(cpos));
			
		return t;
	}

	public static NinTone readSTone(FileBuffer src, long stoff)
	{
		if(src == null) return null;
		NinTone t = new NinTone();
		long cpos = stoff;
		
		t.waveNumber = Short.toUnsignedInt(src.shortFromFile(cpos)); cpos += 2;
		t.warNumber = Short.toUnsignedInt(src.shortFromFile(cpos)); cpos += 2;
		t.originalKey = Byte.toUnsignedInt(src.getByte(cpos)); cpos++;
		t.attack = (int)src.getByte(cpos); cpos++;
		t.decay = (int)src.getByte(cpos); cpos++;
		t.sustain = (int)src.getByte(cpos); cpos++;
		t.release = (int)src.getByte(cpos); cpos++;
		t.pan = (int)src.getByte(cpos); cpos++;
		
		return t;
	}
	
	public int getWARNumber(){return this.warNumber;}
	public int getWaveNumber(){return this.waveNumber;}
	public WaveCoord getWaveCoordinate(){return new WaveCoord(this.warNumber, this.waveNumber);}
	public int getAttack(){return this.attack;}
	public int getDecay(){return this.decay;}
	public int getSustain(){return this.sustain;}
	public int getRelease(){return this.release;}
	public int getHold(){return this.hold;}
	public int getDataLocationType(){return this.dataLocationType;}
	public int getNoteOffType(){return this.noteOffType;}
	public int getAltAssign(){return this.altAssign;}
	public int getOriginalKey(){return this.originalKey;}
	public int getVolume(){return this.volume;}
	public int getPan(){return this.pan;}
	public int getSurroundPan(){return this.surroundPan;}
	public float getPitch(){return this.pitch;}
	public byte getMinNote(){return this.minNote;}
	public byte getMaxNote(){return this.maxNote;}
	
	public Attack getAttackData(){
		return DSADSR.getAttack((byte)attack);
	}
	
	public Decay getDecayData(){
		return DSADSR.getDecay((byte)decay);
	}
	
	public Sustain getSustainData(){
		return DSADSR.getSustain((byte)sustain);
	}
	
	public Release getReleaseData(){
		return DSADSR.getRelease((byte)release);
	}
	
	public void setNoteRange(byte min, byte max)
	{
		minNote = min;
		maxNote = max;
	}
	
	public boolean isEmpty(){return this.waveNumber < 0;}
	
	public Attack scaleAttackToMillis()
	{
		int millis = NinSeqADSR.scaleAttackToMillis(attack);
		
		//Looks like it can work as linear db? Can save as is?
		return new Attack(millis, ADSRMode.LINEAR_DB);
	}
	
	public Decay scaleDecayToMillis()
	{
		//This is a guess
		int millis = NinSeqADSR.scaleDecayToMillis(decay);
		return new Decay(millis, ADSRMode.LINEAR_ENVELOPE);
	}
	
	public Sustain scaleSustain()
	{
		//This is a guess
		return new Sustain(NinSeqADSR.scaleSustain(sustain));
	}
	
	public Release scaleReleaseToMillis()
	{
		//This is a guess
		int millis = NinSeqADSR.scaleReleaseToMillis(release);
		return new Release(millis, ADSRMode.LINEAR_ENVELOPE);
	}
	
	public int scaleHoldToMillis()
	{
		return NinSeqADSR.scaleHoldToMillis(hold);
	}
	
	public int getCoarseTune()
	{
		//This is a guess
		return (int)pitch;
		//return (int)pitch - 1;
	}
	
	public int getFineTune()
	{
		//This is a guess
		float part = pitch - (float)((int)pitch);
		part *= 100; //cents
		return (int)part;
	}
	
	public int scaleVolumeTo32()
	{
		double r = (double)volume/(double)0x7F;
		return (int)Math.round(r * (double)0x7FFFFFFF);
	}
	
	public short scalePanTo16()
	{
		int p = pan - 0x40;
		double r = (double)p/(double)0x7F;
		return (short)Math.round(r * (double)0x7FFF);
	}
	
	public boolean toPreset(SimplePreset preset, String soundKey, int presetIdx)
	{
		int i = preset.newInstrument("INST_" + String.format("%03d", presetIdx), 1);
		SimpleInstrument inst = preset.getRegion(i).getInstrument();
		if(inst == null) return false;
		i = inst.newRegion(soundKey);
		InstRegion reg = inst.getRegion(i);
		if(reg == null) return false;
		
		//Basic Settings
		reg.setVolume(scaleVolumeTo32());
		reg.setPan(this.scalePanTo16());
		reg.setUnityKey((byte)(originalKey + getCoarseTune()));
		reg.setFineTune((byte)getFineTune());
		
		//Envelope
		reg.setAttack(this.scaleAttackToMillis());
		reg.setDecay(this.scaleDecayToMillis());
		reg.setSustain(this.scaleSustain());
		reg.setRelease(this.scaleReleaseToMillis());
		reg.setHold(this.scaleHoldToMillis());
		
		return true;
	}
	
	public boolean toInstRegion(InstRegion reg, boolean setKeyRange)
	{
		if(reg == null) return false;
		
		//Basic Settings
		reg.setVolume(scaleVolumeTo32());
		reg.setPan(this.scalePanTo16());
		reg.setUnityKey((byte)(originalKey + getCoarseTune()));
		reg.setFineTune((byte)getFineTune());
		if(setKeyRange) reg.setMinKey(minNote);
		if(setKeyRange) reg.setMaxKey(maxNote);
		
		//Envelope
		reg.setAttack(this.scaleAttackToMillis());
		reg.setDecay(this.scaleDecayToMillis());
		reg.setSustain(this.scaleSustain());
		reg.setRelease(this.scaleReleaseToMillis());
		reg.setHold(this.scaleHoldToMillis());
		
		return true;
	}

	public void addMetadataToNode(SoundbankNode node){

		node.addMetadataEntry("Wavearc Index", Integer.toString(warNumber));
		node.addMetadataEntry("Wave Index", Integer.toString(waveNumber));
		
		node.addMetadataEntry("Attack Raw", String.format("0x%02x", attack));
		node.addMetadataEntry("Hold Raw", String.format("0x%02x", hold));
		node.addMetadataEntry("Decay Raw", String.format("0x%02x", decay));
		node.addMetadataEntry("Sustain Raw", String.format("0x%02x", sustain));
		node.addMetadataEntry("Release Raw", String.format("0x%02x", release));
		
		node.addMetadataEntry("Note Off Type", Integer.toString(noteOffType));
		node.addMetadataEntry("Alt Assign", Integer.toString(altAssign));
		
		node.addMetadataEntry("Volume", String.format("0x%02x", volume));
		node.addMetadataEntry("Pan", String.format("0x%02x", pan));
		node.addMetadataEntry("Pan (Surround)", String.format("0x%02x", surroundPan));
		
		node.addMetadataEntry("Tune", Float.toString(pitch));
		node.addMetadataEntry("Key Range (Tone)", minNote + " - " + maxNote);
	}
	
	public void printInfo(int tabs)
	{
		StringBuilder tabsb = new StringBuilder(16);
		for(int i = 0; i < tabs; i++) tabsb.append('\t');
		String tabstr = tabsb.toString();
		
		System.out.println(tabstr + "WAR: " + warNumber);
		System.out.println(tabstr + "Wave: " + waveNumber);
		System.out.println(tabstr + "Wave (Hex): 0x" + Integer.toHexString(waveNumber));
		System.out.println(tabstr + "Attack: 0x" + Integer.toHexString(attack));
		System.out.println(tabstr + "Decay: 0x" + Integer.toHexString(decay));
		System.out.println(tabstr + "Sustain: 0x" + Integer.toHexString(sustain));
		System.out.println(tabstr + "Release: 0x" + Integer.toHexString(release));
		System.out.println(tabstr + "Hold: 0x" + Integer.toHexString(hold));
		System.out.println(tabstr + "Data Location Type: " + dataLocationType);
		System.out.println(tabstr + "Note Off Type: " + noteOffType);
		System.out.println(tabstr + "Alt Assign: " + altAssign);
		System.out.println(tabstr + "Volume: 0x" + Integer.toHexString(volume));
		System.out.println(tabstr + "Pan: 0x" + Integer.toHexString(pan));
		System.out.println(tabstr + "Surround Pan: 0x" + Integer.toHexString(surroundPan));
		System.out.println(tabstr + "Pitch: " + pitch);
		System.out.println(tabstr + "Min Note: 0x" + Integer.toHexString(minNote));
		System.out.println(tabstr + "Max Note: 0x" + Integer.toHexString(maxNote));
	}

	public static class WaveCoord{
		
		public int war_number;
		public int wav_number;
		
		public WaveCoord(int i_war, int i_wav){
			war_number = i_war;
			wav_number = i_wav;
		}
		
		public boolean equals(Object o){
			if(o == null) return false;
			if(!(o instanceof WaveCoord)) return false;
			
			WaveCoord other = (WaveCoord)o;
			if(this.war_number != other.war_number) return false;
			if(this.wav_number != other.wav_number) return false;
			
			return true;
		}
		
		public int hashCode(){
			return ~war_number ^ wav_number;
		}
		
	}

}
