package waffleoRai_soundbank.vab;

import java.io.BufferedWriter;
import java.io.IOException;

import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_soundbank.SimpleInstrument;
import waffleoRai_soundbank.SoundbankNode;
import waffleoRai_soundbank.SimpleInstrument.InstRegion;
import waffleoRai_soundbank.adsr.Attack;
import waffleoRai_soundbank.adsr.Decay;
import waffleoRai_soundbank.adsr.Release;
import waffleoRai_soundbank.adsr.Sustain;
import waffleoRai_soundbank.generators.PortamentoDelay;
import waffleoRai_soundbank.generators.PortamentoGenerator;
import waffleoRai_soundbank.generators.ReverbSend;
import waffleoRai_soundbank.generators.VibratoGenerator;

public class VABTone {
	
	public static final int TONE_RECORD_SIZE = 32;
	
	private int index;
	
	private int priority;
	private boolean reverb;
	private int volume;
	private int pan;
	
	private int unityKey;
	private int tune;
	private int keyRangeBot;
	private int keyRangeTop;
	
	private int vibWidth;
	private int vibTime;
	private int pmWidth;
	private int pmTime;
	
	private int pBendMin;
	private int pBendMax;
	
	private int parentProgram;
	private int sampleIndex;
	
	private boolean Am;
	private int Ah;
	private int As;
	private int Dh;
	private int Sl;
	private boolean Sm;
	private boolean Sd;
	private int Sh;
	private int Ss;
	private boolean Rm;
	private int Rh;
	
	private VABAttack att;
	private VABDecay dec;
	private VABSustain sus;
	private VABRelease rel;
	
	/* ----- Construction ----- */
	
	public VABTone(int i) {
		index = i;
		resetDefaults();
	}
	
	public void resetDefaults() {
		priority = 0;
		reverb = false;
		volume = 0;
		pan = 0;
		unityKey = 0;
		tune = 0;
		keyRangeBot = 0;
		keyRangeTop = 0;
		
		vibWidth = 0;
		vibTime = 0;
		pmWidth = 0;
		pmTime = 0;
		
		pBendMin = 0;
		pBendMax = 0;
		
		sampleIndex = 0;
		
		Am = true;
		Ah = 0;
		As = 0;
		
		Dh = 0xF;
		Sl = 0xF;
		Sm = false;
		Sh = 0;
		Ss = 0;
		Rm = false;
		Rh = 0;
		setADSR2((short)0x5fc0);
	}

	public static VABTone readTone(BufferReference data, int i) {
		VABTone tone = new VABTone(i);
		tone.priority = Byte.toUnsignedInt(data.nextByte());
		tone.reverb = Byte.toUnsignedInt(data.nextByte()) != 0;
		tone.volume = Byte.toUnsignedInt(data.nextByte());
		tone.pan = Byte.toUnsignedInt(data.nextByte());
		
		tone.unityKey = Byte.toUnsignedInt(data.nextByte());
		tone.tune = Byte.toUnsignedInt(data.nextByte());
		tone.keyRangeBot = Byte.toUnsignedInt(data.nextByte());
		tone.keyRangeTop = Byte.toUnsignedInt(data.nextByte());
		
		tone.vibWidth = Byte.toUnsignedInt(data.nextByte());
		tone.vibTime = Byte.toUnsignedInt(data.nextByte());
		tone.pmWidth = Byte.toUnsignedInt(data.nextByte());
		tone.pmTime = Byte.toUnsignedInt(data.nextByte());
		
		tone.pBendMin = Byte.toUnsignedInt(data.nextByte());
		tone.pBendMax = Byte.toUnsignedInt(data.nextByte());
		data.add(2L); //Skip reserved
		
		short ADSR1 = data.nextShort();
		short ADSR2 = data.nextShort();
		tone.parentProgram = (int)data.nextShort();
		tone.sampleIndex = (int)data.nextShort();
		
		data.add(8L); //Skip reserved
		
		tone.setADSR1(ADSR1);
		tone.setADSR2(ADSR2);
		
		return tone;
	}
	
	/* ----- Getters ----- */
	
	public int getPriority(){return priority;}
	public boolean hasReverb(){return reverb;}
	public int getVolume(){return volume;}
	public int getPan(){return pan;}
	public int getUnityKey(){return unityKey;}
	public int getFineTune(){return tune;}
	public int getKeyRangeBottom(){return keyRangeBot;}
	public int getKeyRangeTop(){return keyRangeTop;}
	public int getVibratoWidth(){return vibWidth;}
	public int getVibratoTime(){return vibTime;}
	public int getPortamentoWidth(){return pmWidth;}
	public int getPortamentoTime(){return pmTime;}
	public int getPitchBendMin(){return pBendMin;}
	public int getPitchBendMax(){return pBendMax;}
	public int getSampleIndex(){return sampleIndex;}
	public boolean getAttackMode(){return Am;}
	public int getAttackShift(){return Ah;}
	public int getAttackStep(){return As;}
	public int getDecayShift(){return Dh;}
	public boolean getSustainMode(){return Sm;}
	public int getSustainShift(){return Sh;}
	public int getSustainStep(){return Ss;}
	public int getSustainLevel(){return Sl;}
	public boolean getSustainDirection(){return Sd;}
	public boolean getReleaseMode(){return Rm;}
	public int getReleaseShift(){return Rh;}
	public int getInternalIndex(){return index;}
	public int getParentProgram(){return parentProgram;}
	
	public Attack getAttack(){
		if(att != null) return att;
		att = new VABAttack(Am, Ah, As);
		return att;
	}
	
	public Decay getDecay(){
		if(dec != null) return dec;
		dec = new VABDecay(Dh);
		return dec;
	}
	
	public Sustain getSustain(){
		if(sus != null) return sus;
		sus = new VABSustain(Sm,Sd,Sh,Ss,ADSR.getSustain_level_32(Sl));
		return sus;
	}
	
	public Release getRelease(){
		if(rel != null) return rel;
		rel = new VABRelease(Rm, Rh);
		return rel;
	}
	
	/* ----- Setters ----- */
	
	public void setParentProgram(int id) {parentProgram = id;}
	
	public void setPriority(int i){
		if (i < 0) i = 0;
		if (i > 24) i = 24;
		priority = i;
	}
	
	public void setReverb(boolean b){
		reverb = b;
	}
	
	public void setVolume(int i){
		if (i < 0) i = 0;
		if (i > 127) i = 127;
		volume = i;
	}
	
	public void setPan(int i){
		if (i < 0) i = 0;
		if (i > 0x7F) i = 0x7F;
		pan = i;
	}
	
	public void setUnityKey(int i){
		if (i < 0) i = 0;
		if (i > 0x7F) i = 0x7F;
		unityKey = i;
	}
	
	public void setFineTune(int i){
		if (i < 0) i = 0;
		if (i > 99) i = 99;
		tune = i;
	}
	
	public void setFineTune256(int i){
		if (i < 0) i = 0;
		if (i > 255) i = 255;
		tune = i;
	}
	
	public void setKeyRange(int bot, int top){
		if (bot < 0) bot = 0;
		if (bot > 127) bot = 127;
		if (top < 0) top = 0;
		if (top > 127) top = 127;
		keyRangeBot = bot;
		keyRangeTop = top;
	}
	
	public void setVibrato(int width, int time){
		vibWidth = width;
		vibTime = time;
	}
	
	public void removeVibrato(){
		vibWidth = 0;
		vibTime = 0;
	}
	
	public void setPortamento(int width, int time){
		pmWidth = width;
		pmTime = time;
	}
	
	public void removePortamento(){
		pmWidth = 0;
		pmTime = 0;
	}
	
	public void setPitchBend(int min, int max){
		if (min < 0) min = 0;
		if (min > 127) min = 127;
		pBendMin = min;
		if (max < 0) max = 0;
		if (max > 127) max = 127;
		pBendMax = max;
	}
	
	public void setSampleIndex(int i){
		if (i < 0) i = 0;
		if (i > 0xFFFF) i = 0xFFFF;
		sampleIndex = i;
	}
	
	public void setADSR1(short ADSR1){
		int i = Short.toUnsignedInt(ADSR1);
		Am = (i & 0x8000) != 0;
		Ah = (i >>> 10) & 0x1F;
		As = (i >>> 8) & 0x3;
		Dh = (i >>> 4) & 0xF;
		Sl = i & 0xF;
	}
	
	public void setADSR2(short ADSR2){
		int i = Short.toUnsignedInt(ADSR2);
		Sm = (i & 0x8000) != 0;
		Sd = (i & 0x4000) != 0;
		Sh = (i >>> 8) & 0x1F;
		Ss = (i >>> 6) & 0x3;
		Rm = (i & 0x0020) != 0;
		Rh = i & 0x1F;
	}
	
	public void setAttack(boolean mode, int shift, int step){
		Am = mode;
		Ah = shift & 0x1F;
		As = step & 0x3;
	}
	
	public void setDecay(int shift){
		Dh = shift & 0x1F;
	}
	
	public void setSustainLevel(int level){
		Sl = level & 0xF;
	}
	
	public void setSustainRate(boolean mode, int shift, int step, boolean dir){
		Sm = mode;
		Sh = shift & 0x1F;
		Ss = step & 0x3;
		Sd = dir;
	}
	
	public void setRelease(boolean mode, int shift){
		Rm = mode;
		Rh = shift & 0x1F;
	}
	
	/* ----- Serialization ----- */
	
	public short getADSR1(){
		int i = 0;
		if (Am) i = 0x8000;
		i |= (Ah << 10);
		i |= (As << 8);
		i |= (Dh << 4);
		i |= Sl;
		
		return (short)i;
	}
	
	public short getADSR2(){
		int i = 0;
		if (Sm) i = 0x8000;
		if (Sd) i |= 0x4000;
		if (Rm) i |= 0x0020;
		i |= (Sh << 8);
		i |= (Ss << 6);
		i |= Rh;
		
		return (short)i;
	}
	
	public FileBuffer serializeMe(){
		FileBuffer tone = new FileBuffer(32, false);
		
		tone.addToFile((byte)priority);
		if(reverb) tone.addToFile((byte)0x04);
		else tone.addToFile((byte)0x00);
		tone.addToFile((byte)volume);
		tone.addToFile((byte)pan);

		tone.addToFile((byte)unityKey);
		tone.addToFile((byte)tune);
		tone.addToFile((byte)keyRangeBot);
		tone.addToFile((byte)keyRangeTop);
		
		tone.addToFile((byte)vibWidth);
		tone.addToFile((byte)vibTime);
		tone.addToFile((byte)pmWidth);
		tone.addToFile((byte)pmTime);
		
		tone.addToFile((byte)pBendMin);
		tone.addToFile((byte)pBendMax);
		tone.addToFile((byte)0xB1);
		tone.addToFile((byte)0xB2);
		
		tone.addToFile(getADSR1());
		tone.addToFile(getADSR2());
		
		tone.addToFile((short)parentProgram);
		tone.addToFile((short)sampleIndex);
		
		tone.addToFile((short)0x00C0);
		tone.addToFile((short)0x00C1);
		tone.addToFile((short)0x00C2);
		tone.addToFile((short)0x00C3);
		
		return tone;
	}

	public static FileBuffer generateEmptyTone(int i, int parentProgram){
		VABTone empty = new VABTone(i);
		empty.parentProgram = parentProgram;
		return empty.serializeMe();
	}
	
	/* ----- Conversion ----- */
	
	public void addToInstrument(SimpleInstrument inst){
		String skey = PSXVAB.generateSampleKey(sampleIndex);
		int idx = inst.newRegion(skey);
		if (idx < 0) throw new IndexOutOfBoundsException();
		InstRegion reg = inst.getRegion(idx);
		
		if (reverb) reg.addGenerator(new ReverbSend(PSXVAB.REVERB_AMOUNT));
		if (vibWidth != 0 || vibTime != 0) reg.addGenerator(new VibratoGenerator(PSXVAB.scaleVibratoWidth(vibWidth), PSXVAB.scaleVibratoTime(vibTime)));
		//if (vibTime != 0) reg.addGenerator(new VibratoDelay(PSXVAB.scaleVibratoTime(vibTime)));
		if (pmWidth != 0) reg.addGenerator(new PortamentoGenerator(PSXVAB.scalePortamentoWidth(pmWidth)));
		if (pmTime != 0) reg.addGenerator(new PortamentoDelay(PSXVAB.scalePortamentoTime(pmTime)));
	
		reg.setVolume(PSXVAB.scaleVolume(volume));
		reg.setPan(PSXVAB.scalePan(pan));
		
		reg.setUnityKey((byte)unityKey);
		int tcents = tune;
		if (tune > 127) tcents = 127;
		if (tune < -128) tcents = -128;
		reg.setFineTune((byte)tcents);
		reg.setMinKey((byte)keyRangeBot);
		reg.setMaxKey((byte)keyRangeTop);
		reg.setPitchBend(pBendMin, pBendMax);
		
		reg.setAttack(ADSR.getAttack(Am, Ah, As));
		//reg.setDecay(ADSR.getDecay(Dh, Sl));
		reg.setDecay(ADSR.getDecay(Dh));
		reg.setSustain(ADSR.getSustain(Sm, Sd, Sh, Ss, Sl));
		reg.setRelease(ADSR.getRelease(Rm, Rh));
	}
	
	public SoundbankNode toSoundbankNode(SoundbankNode parentProgram){
		SoundbankNode node = new SoundbankNode(parentProgram, toString(), SoundbankNode.NODETYPE_TONE);
		
		//Add metadata
		node.addMetadataEntry("Priority", Integer.toString(priority));
		node.addMetadataEntry("Reverb", Boolean.toString(reverb));
		node.addMetadataEntry("Volume", String.format("0x%02x", volume));
		node.addMetadataEntry("Pan", String.format("0x%02x", pan));
		node.addMetadataEntry("Unity Key", Integer.toString(unityKey));
		node.addMetadataEntry("Fine Tune", tune + " cents");
		node.addMetadataEntry("Key Range", keyRangeBot + " - " + keyRangeTop);
		node.addMetadataEntry("Pitch Bend", pBendMin + " - " + pBendMin);
		node.addMetadataEntry("Sample Index", Integer.toString(sampleIndex));
		
		//ADSR
		Sustain sus = getSustain();
		node.addMetadataEntry("Attack Time", getAttack().getTime() + " ms");
		node.addMetadataEntry("Decay Time", getDecay().getTime() + " ms");
		node.addMetadataEntry("Sustain Time", sus.getTime() + " ms");
		node.addMetadataEntry("Release Time", getRelease().getTime() + " ms");
		
		if(Am) node.addMetadataEntry("Attack Mode", "Pseudo-Exponential");
		else node.addMetadataEntry("Attack Mode", "Linear");
		if(Sm) node.addMetadataEntry("Sustain Mode", "Exponential");
		else node.addMetadataEntry("Sustain Mode", "Linear");
		if(Rm) node.addMetadataEntry("Release Mode", "Exponential");
		else node.addMetadataEntry("Release Mode", "Linear");
		
		node.addMetadataEntry("Attack Shift", String.format("0x%03x", Ah));
		node.addMetadataEntry("Decay Shift", String.format("0x%03x", Dh));
		node.addMetadataEntry("Sustain Shift", String.format("0x%03x", Sh));
		node.addMetadataEntry("Release Shift", String.format("0x%03x", Rh));
		
		node.addMetadataEntry("Attack Step", String.format("0x%02x", As));
		node.addMetadataEntry("Sustain Step", String.format("0x%02x", Ss));
		
		if(Sd) node.addMetadataEntry("Sustain Direction", "Increase");
		else node.addMetadataEntry("Sustain Direction", "Decrease");
		
		node.addMetadataEntry("Sustain Level Raw", String.format("0x%01x", Sl));
		node.addMetadataEntry("Sustain Level 16-Bit", String.format("0x%04x", sus.getLevel16()));
		
		return node;
	}
	
	/* ----- Information ----- */
	
	public void printInfoToBuffer(int tab_indents, BufferedWriter bw) throws IOException{
		if (bw == null) return;
		//System.err.println("---DEBUG: Tone.printInfoToBuffer || Entered ---");
		
		for (int t = 0; t < tab_indents; t++) bw.write("\t"); 
		bw.write("Priority: 0x" + String.format("%02X", priority) + "\n");
		
		for (int t = 0; t < tab_indents; t++) bw.write("\t"); 
		bw.write("Reverb: " + reverb + "\n");
		
		for (int t = 0; t < tab_indents; t++) bw.write("\t"); 
		bw.write("Volume: 0x" + String.format("%02X", volume) + "\n");
		
		for (int t = 0; t < tab_indents; t++) bw.write("\t"); 
		bw.write("Pan: 0x" + String.format("%02X", pan) + "\n");
		//System.err.println("---DEBUG: Tone.printInfoToBuffer || Check 1 ---");
		
		for (int t = 0; t < tab_indents; t++) bw.write("\t"); 
		String ukname = PSXVAB.NOTES[Math.abs(unityKey%12)];
		int ukoct = (unityKey/12) - 1;
		bw.write("Unity Key: " + ukname + ukoct + " (" + unityKey + ")\n");
		
		for (int t = 0; t < tab_indents; t++) bw.write("\t"); 
		bw.write("Fine Tune: " + tune + " cents\n");
		//System.err.println("---DEBUG: Tone.printInfoToBuffer || Check 2 ---");
		
		for (int t = 0; t < tab_indents; t++) bw.write("\t"); 
		String tkname = PSXVAB.NOTES[Math.abs(keyRangeTop%12)];
		int tkoct = (keyRangeTop/12) - 1;
		String bkname = PSXVAB.NOTES[Math.abs(keyRangeBot%12)];
		int bkoct = (keyRangeBot/12) - 1;
		bw.write("Key Range: " + bkname + bkoct + " (" + keyRangeBot + ") - ");
		bw.write(tkname + tkoct + " (" + keyRangeTop + ")\n");
		//System.err.println("---DEBUG: Tone.printInfoToBuffer || Check 3 ---");
		
		for (int t = 0; t < tab_indents; t++) bw.write("\t"); 
		bw.write("Vibrato (Width): 0x" + String.format("%04X", vibWidth) + "\n");
		
		for (int t = 0; t < tab_indents; t++) bw.write("\t"); 
		bw.write("Vibrato (Time): 0x" + String.format("%04X", vibTime) + "\n");
		
		for (int t = 0; t < tab_indents; t++) bw.write("\t"); 
		bw.write("Portamento (Width): 0x" + String.format("%04X", pmWidth) + "\n");
		
		for (int t = 0; t < tab_indents; t++) bw.write("\t"); 
		bw.write("Portamento (Time): 0x" + String.format("%04X", pmTime) + "\n");
		
		for (int t = 0; t < tab_indents; t++) bw.write("\t"); 
		bw.write("Pitch Bend: " + pBendMin + " - " + pBendMax + "\n");
		
		for (int t = 0; t < tab_indents; t++) bw.write("\t"); 
		bw.write("Sample Index: " + sampleIndex + "\n");
		//System.err.println("---DEBUG: Tone.printInfoToBuffer || Check 4 ---");
		
		//Attack
		for (int t = 0; t < tab_indents; t++) bw.write("\t"); 
		bw.write("ATTACK -- \n");
		
		for (int t = 0; t < tab_indents; t++) bw.write("\t"); 
		bw.write("\tMode: ");
		if (Am) bw.write("Pseudo-Exponential\n");
		else bw.write("Linear Envelope\n");
		
		for (int t = 0; t < tab_indents; t++) bw.write("\t"); 
		bw.write("\tShift: " + Ah + "\n");
		
		for (int t = 0; t < tab_indents; t++) bw.write("\t"); 
		bw.write("\tStep: " + As + "\n");
		
		Attack a = ADSR.getAttack(Am, Ah, As);
		for (int t = 0; t < tab_indents; t++) bw.write("\t"); 
		bw.write("\tTime: " + String.format("%d", a.getTime()) + " milliseconds\n");
		//System.err.println("---DEBUG: Tone.printInfoToBuffer || Check 5 ---");
		
		//Decay
		for (int t = 0; t < tab_indents; t++) bw.write("\t"); 
		bw.write("DECAY -- \n");
		
		for (int t = 0; t < tab_indents; t++) bw.write("\t"); 
		bw.write("\tShift: " + Dh + "\n");
		
		Decay d = ADSR.getDecay(Dh);
		for (int t = 0; t < tab_indents; t++) bw.write("\t"); 
		bw.write("\tTime: " + String.format("%d", d.getTime()) + " milliseconds\n");
		//System.err.println("---DEBUG: Tone.printInfoToBuffer || Check 6 ---");
		
		//Sustain
		for (int t = 0; t < tab_indents; t++) bw.write("\t"); 
		bw.write("SUSTAIN -- \n");
		
		for (int t = 0; t < tab_indents; t++) bw.write("\t"); 
		bw.write("\tDirection: ");
		if (Sd) bw.write("Decreasing\n");
		else bw.write("Increasing\n");
		
		for (int t = 0; t < tab_indents; t++) bw.write("\t"); 
		bw.write("\tMode: ");
		if (Sm && !Sd) bw.write("Pseudo-Exponential\n");
		if (Sm && Sd) bw.write("Exponential Decay\n");
		else bw.write("Linear Envelope\n");
		
		for (int t = 0; t < tab_indents; t++) bw.write("\t"); 
		bw.write("\tShift: " + Sh + "\n");
		
		for (int t = 0; t < tab_indents; t++) bw.write("\t"); 
		bw.write("\tStep: " + Ss + "\n");
		
		for (int t = 0; t < tab_indents; t++) bw.write("\t"); 
		bw.write("\tRaw Level: " + String.format("0x%01X", Sl) + "\n");
		
		Sustain s = ADSR.getSustain(Sm, Sd, Sh, Ss, Sl);

		for (int t = 0; t < tab_indents; t++) bw.write("\t"); 
		bw.write("\tLevel (16-bit): " + String.format("0x%04X", s.getLevel16()) + "\n");
		
		for (int t = 0; t < tab_indents; t++) bw.write("\t"); 
		bw.write("\tTime: " + String.format("%d", s.getTime()) + " milliseconds\n");
		//System.err.println("---DEBUG: Tone.printInfoToBuffer || Check 7 ---");
		
		//Release
		for (int t = 0; t < tab_indents; t++) bw.write("\t"); 
		bw.write("RELEASE -- \n");
		
		for (int t = 0; t < tab_indents; t++) bw.write("\t"); 
		bw.write("\tMode: ");
		if (Rm) bw.write("Exponential Decay\n");
		else bw.write("Linear Envelope\n");
		
		for (int t = 0; t < tab_indents; t++) bw.write("\t"); 
		bw.write("\tShift: " + Rh + "\n");
		
		Release r = ADSR.getRelease(Rm, Rh);
		for (int t = 0; t < tab_indents; t++) bw.write("\t"); 
		bw.write("\tTime: " + String.format("%d", r.getTime()) + " milliseconds\n");
	}
	
	public String toString(){return "Tone " + index;}

}
