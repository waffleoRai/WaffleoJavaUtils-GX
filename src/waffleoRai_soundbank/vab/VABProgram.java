package waffleoRai_soundbank.vab;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_soundbank.SimpleInstrument;
import waffleoRai_soundbank.SimplePreset;
import waffleoRai_soundbank.SingleBank;
import waffleoRai_soundbank.SoundbankNode;
import waffleoRai_soundbank.SimplePreset.PresetRegion;

public class VABProgram {
	private int volume;
	private int priority;
	private int mode;
	private int pan;
	private int attribute;
	
	private int index = 0;

	private VABTone[] tones;
	
	//For playback. Only allocated when called.
	//Lookup: Key->Tone (Doesn't seem to have velocity mapping YES)
	private ConcurrentMap<Byte, VABTone> regionmap;
	
	/* ----- Construction ----- */
	
	public VABProgram() {
		tones = new VABTone[16];
		setDefaults();
	}
	
	public void setDefaults() {
		volume = 0x7F;
		priority = 0xFF;
		mode = 0xFF;
		pan = 0x40;
		attribute = 0;
	}
	
	public static VABProgram readProgram(BufferReference data) {
		int ptones = Byte.toUnsignedInt(data.nextByte());
		if (ptones == 0){
			data.add(15L);
			return null;
		}

		VABProgram p = new VABProgram();
		p.instantiateTones(ptones);
		p.setVolume((int)data.nextByte());
		p.priority = Byte.toUnsignedInt(data.nextByte());
		p.mode = Byte.toUnsignedInt(data.nextByte());
		p.setPan((int)data.nextByte());
		data.increment(); //Skip reserved
		p.setAttribute(Short.toUnsignedInt(data.nextShort()));
		data.add(8L); //Skip reserved
		
		return p;
	}
	
	/* ----- Getters ----- */
	
	public int getVolume(){return volume;}
	public int getPriority(){return priority;}
	public int getMode(){return mode;}
	public int getPan(){return pan;}
	public int getAttribute(){return attribute;}
	
	public int getToneCount(){
		int c = 0;
		for (int i = 0; i < 16; i++){
			if (tones[i] != null) c++;
		}
		return c;
	}
	
	public VABTone getTone(int i){
		if (i < 0) return null;
		if (i >= 16) return null;
		return tones[i];
	}
	
	public int getProgramIndex() {return index;}
	
	/* ----- Setters ----- */
	
	public void setVolume(int i){
		if (i < 0) i = 0;
		if (i > 0x7F) i = 0x7F;
		volume = i;
	}
	
	public void setPriority(int i){
		if (i < 0) i = 0;
		if (i > 0xFF) i = 0xFF;
		priority = i;
	}
	
	public void setMode(int i){
		if (i < 0) i = 0;
		if (i > 0xFF) i = 0xFF;
		mode = i;
	}
	
	public void setPan(int i){
		if (i < 0) i = 0;
		if (i > 0x7F) i = 0x7F;
		pan = i;
	}
	
	public void setAttribute(int i){
		if (i < 0) i = 0;
		if (i > 0xFFFF) i = 0xFFFF;
		attribute = i;
	}
	
	public void instantiateTones(int num){
		for (int i = 0; i < num; i++){
			tones[i] = new VABTone(i);
		}
	}
	
	public void setTone(VABTone tone, int i) {
		if(i < 0 || i >= tones.length) return;
		tones[i] = tone;
	}
	
	public void setProgramIndex(int val) {index = val;}
	
	/* ----- Serialization ----- */
	
	public FileBuffer serializePAT_entry(){
		FileBuffer program = new FileBuffer(16, false);
		
		program.addToFile((byte)getToneCount());
		program.addToFile((byte)volume);
		program.addToFile((byte)priority);
		program.addToFile((byte)mode);
		
		program.addToFile((byte)pan);
		program.addToFile((byte)0xFF);
		program.addToFile((short)attribute);
		
		for (int i = 0; i < 8; i++) program.addToFile((byte)0xFF);
		
		return program;
	}
	
	public FileBuffer serializeTAT_entry(){
		FileBuffer ptones = new FileBuffer((16 * VABTone.TONE_RECORD_SIZE), false);
		
		for (int i = 0; i < 16; i++){
			if(tones[i] == null) ptones.addToFile(VABTone.generateEmptyTone(i, index));
			else ptones.addToFile(tones[i].serializeMe());
		}
		
		return ptones;
	}
	
	public static FileBuffer generateEmptyPAT_entry(){
		FileBuffer program = new FileBuffer(16, false);
		
		program.addToFile((byte)0x00);
		program.addToFile((byte)0x00);
		program.addToFile((byte)0xFF);
		program.addToFile((byte)0xFF);
		
		program.addToFile((byte)0x00);
		program.addToFile((byte)0xFF);
		program.addToFile((short)0x0000);
		
		for (int i = 0; i < 8; i++) program.addToFile((byte)0xFF);
		
		return program;
	}
	
	/* ----- Conversion ----- */
	
	public void addToSoundbank(SingleBank bank, int presetindex){
		int idx = bank.newPreset(presetindex, "VAB_Program_" + String.format("%03d", presetindex), 1);
		if (idx < 0) throw new IndexOutOfBoundsException();
		SimplePreset p = bank.getPreset(idx);
		idx = p.newInstrument("VAB_Inst_"+ String.format("%03d", presetindex), 16);
		if (idx < 0) throw new IndexOutOfBoundsException();
		PresetRegion preg = p.getRegion(idx);
		SimpleInstrument i = preg.getInstrument();
		
		//Set globals
		p.setMasterVolume(PSXVAB.scaleVolume(volume));
		p.setMasterPan(PSXVAB.scalePan(pan));
		
		//Set regions
		for (int r = 0; r < tones.length; r++){
			VABTone t = tones[r];
			if (t != null){
				t.addToInstrument(i);
			}
		}
	}
	
	public SoundbankNode toSoundbankNode(SoundbankNode parentBank, String name){
		SoundbankNode node = new SoundbankNode(parentBank, name, SoundbankNode.NODETYPE_PROGRAM);
		
		node.addMetadataEntry("Volume", String.format("0x%02x", volume));
		node.addMetadataEntry("Pan", String.format("0x%02x", pan));
		node.addMetadataEntry("Priority", Integer.toString(priority));
		node.addMetadataEntry("Mode", String.format("0x%02x", mode));
		node.addMetadataEntry("Attribute", String.format("0x%04x", attribute));
		
		//Do tones.
		int tcount = 0;
		for(int i = 0; i < tones.length; i++){
			if(tones[i] != null){
				tcount++;
				tones[i].toSoundbankNode(node);
			}
		}
		
		node.addMetadataEntry("Tone Count", Integer.toString(tcount));
		
		return node;
	}
	
	/* ----- Playback ----- */
	
	private void buildPlaybackMap(){
		regionmap = new ConcurrentHashMap<Byte, VABTone>();
		for(VABTone t : tones){
			if(t == null) break;
			int minkey = t.getKeyRangeBottom();
			int maxkey = t.getKeyRangeTop();
			for(int k = minkey; k <= maxkey; k++) regionmap.put((byte)k, t);
		}
	}
	
	public VABTone getTone(byte key){
		if(regionmap == null) buildPlaybackMap();
		return regionmap.get(key);
	}
	
	public void freePlaybackToneMap(){
		if(regionmap != null) regionmap.clear();
		regionmap = null;
	}
	
	/* ----- Information ----- */
	
	public void printInfoToBuffer(int tab_indents, BufferedWriter bw) throws IOException{
		if (bw == null) return;
		
		for (int t = 0; t < tab_indents; t++) bw.write("\t"); 
		bw.write("Tone Count: " + getToneCount() + "\n");
		
		for (int t = 0; t < tab_indents; t++) bw.write("\t"); 
		bw.write("Volume: 0x" + String.format("%02X", volume) + "\n");
		
		for (int t = 0; t < tab_indents; t++) bw.write("\t"); 
		bw.write("Priority: 0x" + String.format("%02X", priority) + "\n");
		
		for (int t = 0; t < tab_indents; t++) bw.write("\t"); 
		bw.write("Mode: 0x" + String.format("%02X", mode) + "\n");
		
		for (int t = 0; t < tab_indents; t++) bw.write("\t"); 
		bw.write("Pan: 0x" + String.format("%02X", pan) + "\n");
		
		for (int t = 0; t < tab_indents; t++) bw.write("\t"); 
		bw.write("Attribute: 0x" + String.format("%04X", attribute) + "\n");
		
		for (int t = 0; t < tab_indents; t++) bw.write("\t"); 
		bw.write("--- TONES ---\n");
		if (tones == null) {
			bw.write("[None]\n");
			return;
		}
		System.err.println("Tones: " + tones.length);
		for (int i = 0; i < tones.length; i++){
			System.err.println("\tWriting tone " + i);
			for (int t = 0; t < tab_indents; t++) bw.write("\t"); 
			bw.write("\tTone " + i + "\n");
			VABTone tone = tones[i];
			if (tone == null){
				System.err.println("\t(Tone null)");
				for (int t = 0; t < tab_indents; t++) bw.write("\t"); 
				bw.write("\t\t[NULL]\n");
				continue;
			}
			else{
				tone.printInfoToBuffer(tab_indents + 2, bw);
			}
			System.err.println("\t(Tone written)");
		}
	}

}
