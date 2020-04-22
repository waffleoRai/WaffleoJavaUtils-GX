package waffleoRai_soundbank.vab;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import waffleoRai_Sound.psx.PSXVAG;
import waffleoRai_SoundSynth.SynthBank;
import waffleoRai_SoundSynth.SynthProgram;
import waffleoRai_Utils.CompositeBuffer;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;
import waffleoRai_soundbank.SimpleBank;
import waffleoRai_soundbank.SimpleInstrument;
import waffleoRai_soundbank.SimplePreset;
import waffleoRai_soundbank.SingleBank;
import waffleoRai_soundbank.SoundbankNode;
import waffleoRai_soundbank.SimpleInstrument.InstRegion;
import waffleoRai_soundbank.SimplePreset.PresetRegion;
import waffleoRai_soundbank.adsr.Attack;
import waffleoRai_soundbank.adsr.Decay;
import waffleoRai_soundbank.adsr.Release;
import waffleoRai_soundbank.adsr.Sustain;
import waffleoRai_soundbank.generators.PortamentoDelay;
import waffleoRai_soundbank.generators.PortamentoGenerator;
import waffleoRai_soundbank.generators.ReverbSend;
import waffleoRai_soundbank.generators.VibratoGenerator;
import waffleoRai_soundbank.sf2.SF2;
import waffleoRai_soundbank.sf2.ADSR.ADSRC_RetainTime;

public class PSXVAB implements SynthBank{
	
	/* ----- Constants ----- */
	
	public static final String MAGIC_LE = "pBAV";
	
	public static final int MAX_PROGRAMS = 128;
	public static final int MAX_TONES_PER_PROGRAM = 16;
	
	public static final int HEADER_SIZE = 32;
	public static final int PTE_SIZE = 16;
	public static final int TTE_SIZE = 32;
	public static final int SPT_SIZE = 512;
	
	public static final int REVERB_AMOUNT = 1000; //Reverb send in 1/100 of a percent
	
	public static final String[] NOTES = {"C", "C#", "D", "Eb", 
										  "E", "F", "F#", "G",
										  "Ab", "A", "Bb", "B"};
	
	/* ----- Instance Variables ----- */
	
	private int version;
	private int ID;
	//private int waveformSize;
	
	private int masterVolume;
	private int masterPan;
	
	private int att1;
	private int att2;
	
	private Program[] programs;
	private List<PSXVAG> samples;
	
	private boolean i1;
	private VABSynthProgram[] playables;
	
	private String name;
	
	/* ----- Inner Classes ----- */
	
	public static class Program
	{
		private int volume;
		private int priority;
		private int mode;
		private int pan;
		private int attribute;
	
		private Tone[] tones;
		
		//For playback. Only allocated when called.
		//Lookup: Key->Tone (Doesn't seem to have velocity mapping YES)
		private ConcurrentMap<Byte, Tone> regionmap;
		
		/* ----- Construction ----- */
		
		public Program()
		{
			tones = new Tone[16];
			setDefaults();
		}
		
		public void setDefaults()
		{
			volume = 0x7F;
			priority = 0xFF;
			mode = 0xFF;
			pan = 0x40;
			attribute = 0;
		}
		
		/* ----- Getters ----- */
		
		public int getVolume()
		{
			return volume;
		}
		
		public int getPriority()
		{
			return priority;
		}
		
		public int getMode()
		{
			return mode;
		}
		
		public int getPan()
		{
			return pan;
		}
		
		public int getAttribute()
		{
			return attribute;
		}
		
		public int getToneCount()
		{
			int c = 0;
			for (int i = 0; i < 16; i++)
			{
				if (tones[i] != null) c++;
			}
			return c;
		}
		
		public Tone getTone(int i)
		{
			if (i < 0) return null;
			if (i >= 16) return null;
			return tones[i];
		}
		
		/* ----- Setters ----- */
		
		public void setVolume(int i)
		{
			if (i < 0) i = 0;
			if (i > 0x7F) i = 0x7F;
			volume = i;
		}
		
		public void setPriority(int i)
		{
			if (i < 0) i = 0;
			if (i > 0xFF) i = 0xFF;
			priority = i;
		}
		
		public void setMode(int i)
		{
			if (i < 0) i = 0;
			if (i > 0xFF) i = 0xFF;
			mode = i;
		}
		
		public void setPan(int i)
		{
			if (i < 0) i = 0;
			if (i > 0x7F) i = 0x7F;
			pan = i;
		}
		
		public void setAttribute(int i)
		{
			if (i < 0) i = 0;
			if (i > 0xFFFF) i = 0xFFFF;
			attribute = i;
		}
		
		public void instantiateTones(int num)
		{
			for (int i = 0; i < num; i++)
			{
				tones[i] = new Tone(i);
			}
		}
		
		/* ----- Serialization ----- */
		
		public FileBuffer serializePAT_entry()
		{
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
		
		public FileBuffer serializeTAT_entry(int programNumber)
		{
			FileBuffer ptones = new FileBuffer((16 * 32), false);
			
			for (int i = 0; i < 16; i++)
			{
				if(tones[i] == null) ptones.addToFile(Tone.generateEmptyTone(programNumber, i));
				else ptones.addToFile(tones[i].serializeMe(programNumber));
			}
			
			return ptones;
		}
		
		public static FileBuffer generateEmptyPAT_entry()
		{
			FileBuffer program = new FileBuffer(16, false);
			
			program.addToFile((byte)0x00);
			program.addToFile((byte)0x7F);
			program.addToFile((byte)0xFF);
			program.addToFile((byte)0xFF);
			
			program.addToFile((byte)0x40);
			program.addToFile((byte)0xFF);
			program.addToFile((short)0x0000);
			
			for (int i = 0; i < 8; i++) program.addToFile((byte)0xFF);
			
			return program;
		}
		
		/* ----- Conversion ----- */
		
		public void addToSoundbank(SingleBank bank, int presetindex)
		{
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
			for (int r = 0; r < tones.length; r++)
			{
				Tone t = tones[r];
				if (t != null)
				{
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
		
		private void buildPlaybackMap()
		{
			regionmap = new ConcurrentHashMap<Byte, Tone>();
			for(Tone t : tones)
			{
				if(t == null) break;
				int minkey = t.getKeyRangeBottom();
				int maxkey = t.getKeyRangeTop();
				for(int k = minkey; k <= maxkey; k++) regionmap.put((byte)k, t);
			}
		}
		
		public Tone getTone(byte key)
		{
			if(regionmap == null) buildPlaybackMap();
			return regionmap.get(key);
		}
		
		public void freePlaybackToneMap()
		{
			if(regionmap != null) regionmap.clear();
			regionmap = null;
		}
		
		/* ----- Information ----- */
		
		public void printInfoToBuffer(int tab_indents, BufferedWriter bw) throws IOException
		{
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
			for (int i = 0; i < tones.length; i++)
			{
				System.err.println("\tWriting tone " + i);
				for (int t = 0; t < tab_indents; t++) bw.write("\t"); 
				bw.write("\tTone " + i + "\n");
				Tone tone = tones[i];
				if (tone == null)
				{
					System.err.println("\t(Tone null)");
					for (int t = 0; t < tab_indents; t++) bw.write("\t"); 
					bw.write("\t\t[NULL]\n");
					continue;
				}
				else
				{
					tone.printInfoToBuffer(tab_indents + 2, bw);
				}
				System.err.println("\t(Tone written)");
			}
		}
		
	}
	
	public static class Tone
	{
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
		
		public Tone(int i)
		{
			index = i;
			resetDefaults();
		}
		
		public void resetDefaults()
		{
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
		}

		/* ----- Getters ----- */
		
		public int getPriority()
		{
			return priority;
		}
		
		public boolean hasReverb()
		{
			return reverb;
		}
		
		public int getVolume()
		{
			return volume;
		}
		
		public int getPan()
		{
			return pan;
		}
		
		public int getUnityKey()
		{
			return unityKey;
		}
		
		public int getFineTune()
		{
			return tune;
		}
		
		public int getKeyRangeBottom()
		{
			return keyRangeBot;
		}
		
		public int getKeyRangeTop()
		{
			return keyRangeTop;
		}
		
		public int getVibratoWidth()
		{
			return vibWidth;
		}
		
		public int getVibratoTime()
		{
			return vibTime;
		}
		
		public int getPortamentoWidth()
		{
			return pmWidth;
		}
		
		public int getPortamentoTime()
		{
			return pmTime;
		}
		
		public int getPitchBendMin()
		{
			return pBendMin;
		}
		
		public int getPitchBendMax()
		{
			return pBendMax;
		}
		
		public int getSampleIndex()
		{
			return sampleIndex;
		}
		
		public boolean getAttackMode()
		{
			return Am;
		}
		
		public int getAttackShift()
		{
			return Ah;
		}
		
		public int getAttackStep()
		{
			return As;
		}
		
		public int getDecayShift()
		{
			return Dh;
		}
		
		public boolean getSustainMode()
		{
			return Sm;
		}
		
		public int getSustainShift()
		{
			return Sh;
		}
		
		public int getSustainStep()
		{
			return Ss;
		}
		
		public int getSustainLevel()
		{
			return Sl;
		}
		
		public boolean getSustainDirection()
		{
			return Sd;
		}
		
		public boolean getReleaseMode()
		{
			return Rm;
		}
		
		public int getReleaseShift()
		{
			return Rh;
		}
		
		public int getInternalIndex()
		{
			return index;
		}
		
		public Attack getAttack()
		{
			if(att != null) return att;
			att = new VABAttack(Am, Ah, As);
			return att;
		}
		
		public Decay getDecay()
		{
			if(dec != null) return dec;
			dec = new VABDecay(Dh);
			return dec;
		}
		
		public Sustain getSustain()
		{
			if(sus != null) return sus;
			sus = new VABSustain(Sm,Sd,Sh,Ss,ADSR.getSustain_level_32(Sl));
			return sus;
		}
		
		public Release getRelease()
		{
			if(rel != null) return rel;
			rel = new VABRelease(Rm, Rh);
			return rel;
		}
		
		/* ----- Setters ----- */
		
		public void setPriority(int i)
		{
			if (i < 0) i = 0;
			if (i > 24) i = 24;
			priority = i;
		}
		
		public void setReverb(boolean b)
		{
			reverb = b;
		}
		
		public void setVolume(int i)
		{
			if (i < 0) i = 0;
			if (i > 127) i = 127;
			volume = i;
		}
		
		public void setPan(int i)
		{
			if (i < 0) i = 0;
			if (i > 0x7F) i = 0x7F;
			pan = i;
		}
		
		public void setUnityKey(int i)
		{
			if (i < 0) i = 0;
			if (i > 0x7F) i = 0x7F;
			unityKey = i;
		}
		
		public void setFineTune(int i)
		{
			if (i < 0) i = 0;
			if (i > 99) i = 99;
			tune = i;
		}
		
		public void setFineTune256(int i)
		{
			if (i < 0) i = 0;
			if (i > 255) i = 255;
			tune = i;
		}
		
		public void setKeyRange(int bot, int top)
		{
			if (bot < 0) bot = 0;
			if (bot > 127) bot = 127;
			if (top < 0) top = 0;
			if (top > 127) top = 127;
			keyRangeBot = bot;
			keyRangeTop = top;
		}
		
		public void setVibrato(int width, int time)
		{
			vibWidth = width;
			vibTime = time;
		}
		
		public void removeVibrato()
		{
			vibWidth = 0;
			vibTime = 0;
		}
		
		public void setPortamento(int width, int time)
		{
			pmWidth = width;
			pmTime = time;
		}
		
		public void removePortamento()
		{
			pmWidth = 0;
			pmTime = 0;
		}
		
		public void setPitchBend(int min, int max)
		{
			if (min < 0) min = 0;
			if (min > 127) min = 127;
			pBendMin = min;
			if (max < 0) max = 0;
			if (max > 127) max = 127;
			pBendMax = max;
		}
		
		public void setSampleIndex(int i)
		{
			if (i < 0) i = 0;
			if (i > 0xFFFF) i = 0xFFFF;
			sampleIndex = i;
		}
		
		public void setADSR1(short ADSR1)
		{
			int i = Short.toUnsignedInt(ADSR1);
			Am = (i & 0x8000) != 0;
			Ah = (i >>> 10) & 0x1F;
			As = (i >>> 8) & 0x3;
			Dh = (i >>> 4) & 0xF;
			Sl = i & 0xF;
		}
		
		public void setADSR2(short ADSR2)
		{
			int i = Short.toUnsignedInt(ADSR2);
			Sm = (i & 0x8000) != 0;
			Sd = (i & 0x4000) != 0;
			Sh = (i >>> 8) & 0x1F;
			Ss = (i >>> 6) & 0x3;
			Rm = (i & 0x0020) != 0;
			Rh = i & 0x1F;
		}
		
		public void setAttack(boolean mode, int shift, int step)
		{
			Am = mode;
			Ah = shift & 0x1F;
			As = step & 0x3;
		}
		
		public void setDecay(int shift)
		{
			Dh = shift & 0x1F;
		}
		
		public void setSustainLevel(int level)
		{
			Sl = level & 0xF;
		}
		
		public void setSustainRate(boolean mode, int shift, int step, boolean dir)
		{
			Sm = mode;
			Sh = shift & 0x1F;
			Ss = step & 0x3;
			Sd = dir;
		}
		
		public void setRelease(boolean mode, int shift)
		{
			Rm = mode;
			Rh = shift & 0x1F;
		}
		
		/* ----- Serialization ----- */
		
		public short getADSR1()
		{
			int i = 0;
			if (Am) i = 0x8000;
			i |= (Ah << 10);
			i |= (As << 8);
			i |= (Dh << 4);
			i |= Sl;
			
			return (short)i;
		}
		
		public short getADSR2()
		{
			int i = 0;
			if (Sm) i = 0x8000;
			if (Sd) i |= 0x4000;
			if (Rm) i |= 0x0020;
			i |= (Sh << 8);
			i |= (Ss << 6);
			i |= Rh;
			
			return (short)i;
		}
		
		public FileBuffer serializeMe(int parentProgram)
		{
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
	
		public static FileBuffer generateEmptyTone(int parentProgram, int i)
		{
			Tone empty = new Tone(i);
			return empty.serializeMe(parentProgram);
		}
		
		/* ----- Conversion ----- */
		
		public void addToInstrument(SimpleInstrument inst)
		{
			String skey = PSXVAB.generateSampleKey(sampleIndex);
			int idx = inst.newRegion(skey);
			if (idx < 0) throw new IndexOutOfBoundsException();
			InstRegion reg = inst.getRegion(idx);
			
			if (reverb) reg.addGenerator(new ReverbSend(REVERB_AMOUNT));
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
			node.addMetadataEntry("Sustain Time", sus + " ms");
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
		
		public void printInfoToBuffer(int tab_indents, BufferedWriter bw) throws IOException
		{
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
			String ukname = NOTES[Math.abs(unityKey%12)];
			int ukoct = (unityKey/12) - 1;
			bw.write("Unity Key: " + ukname + ukoct + " (" + unityKey + ")\n");
			
			for (int t = 0; t < tab_indents; t++) bw.write("\t"); 
			bw.write("Fine Tune: " + tune + " cents\n");
			//System.err.println("---DEBUG: Tone.printInfoToBuffer || Check 2 ---");
			
			for (int t = 0; t < tab_indents; t++) bw.write("\t"); 
			String tkname = NOTES[Math.abs(keyRangeTop%12)];
			int tkoct = (keyRangeTop/12) - 1;
			String bkname = NOTES[Math.abs(keyRangeBot%12)];
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
		
		public String toString()
		{
			return "Tone " + index;
		}
		
	}
	
	/* ----- Construction ----- */
	
	public PSXVAB(int bankID)
	{
		resetDefaults();
		ID = bankID;
		programs = new Program[MAX_PROGRAMS];
		samples = new ArrayList<PSXVAG>(255);
	}
	
	public PSXVAB(String path) throws IOException, UnsupportedFileTypeException
	{
		this(FileBuffer.createBuffer(path, false));
	}
	
	public PSXVAB(FileBuffer vab) throws UnsupportedFileTypeException, IOException
	{
		resetDefaults();
		programs = new Program[MAX_PROGRAMS];
		samples = new ArrayList<PSXVAG>(255);
		parseVAB(vab);
	}
	
	public PSXVAB(FileBuffer vab_head, FileBuffer vab_body) throws UnsupportedFileTypeException, IOException
	{
		resetDefaults();
		programs = new Program[MAX_PROGRAMS];
		samples = new ArrayList<PSXVAG>(255);
		parseVAB(vab_head, vab_body);
	}
	
	public void resetDefaults()
	{
		version = 7;
		ID = 0;
		masterVolume = 0x7F;
		masterPan = 0x40;
		att1 = 0;
		att2 = 0;
	}
	
	/* ----- Parsing ----- */
	
	private void parseVAB(FileBuffer vab) throws UnsupportedFileTypeException, IOException
	{
		//Need to know program count to know how big header is.
		//Offset of program count is 0x12 (field is 2 bytes)
		long cPos = vab.findString(0, 0x10, MAGIC_LE);
		if (cPos < 0) throw new FileBuffer.UnsupportedFileTypeException();
		int progcount = (int)vab.shortFromFile(cPos+0x12);
		
		//Calculate header size
		int headersize = 32 + 2048 + 512;
		headersize += (progcount * 16 * 32);
		
		FileBuffer header = vab.createReadOnlyCopy(0, headersize);
		FileBuffer body = vab.createReadOnlyCopy(headersize, vab.getFileSize());
		parseVAB(header, body);
	}
	
	private void parseVAB(FileBuffer vab_head, FileBuffer vab_body) throws UnsupportedFileTypeException, IOException
	{
		//Look for tag
		long cPos = vab_head.findString(0, 0x10, MAGIC_LE);
		if (cPos < 0) throw new FileBuffer.UnsupportedFileTypeException();
		cPos += 4;
		
		//boolean e = vab_head.isBigEndian();
		vab_head.setEndian(false);
		
		//VAB header
		version = vab_head.intFromFile(cPos); cPos += 4;
		ID = vab_head.intFromFile(cPos); cPos += 4;
		//int vabsize = mybank.intFromFile(cPos); 
		cPos += 4;
		cPos += 2; //Skip system reserved
		int pCount = (int)vab_head.shortFromFile(cPos); 
		cPos += 2;
		//int tCount = (int)vab_head.shortFromFile(cPos); cPos += 2;
		cPos += 2;
		int sCount = (int)vab_head.shortFromFile(cPos); cPos += 2;
		masterVolume = (int)vab_head.getByte(cPos); cPos++;
		masterPan = (int)vab_head.getByte(cPos); cPos++;
		att1 = Byte.toUnsignedInt(vab_head.getByte(cPos)); cPos++;
		att2 = Byte.toUnsignedInt(vab_head.getByte(cPos)); cPos++;
		cPos += 4; //Skip more system reserved
		
		//System.err.println("VAB Version: " + version);
		//System.err.println("VAB ID: " + ID);
		//System.err.println("Tones: " + tCount);
		//System.err.println("Samples: " + sCount);
		//System.err.println("Master Volume: " + masterVolume);
		//System.err.println("Master Pan: " + masterPan);
		
		//PAT - Program Attribute Table
		//Always 128 entries, some empty
		for (int i = 0; i < 128; i++)
		{
			//If 0 tones, then we assume empty program
			//System.err.println("Program " + i);
			int ptones = Byte.toUnsignedInt(vab_head.getByte(cPos)); cPos++;
			if (ptones == 0){cPos += 15; continue;}
			//System.err.println("Tones " + ptones);
			
			Program p = new Program();
			p.instantiateTones(ptones);
			p.setVolume((int)vab_head.getByte(cPos)); cPos++;
			p.setPriority((int)vab_head.getByte(cPos)); cPos++;
			p.setMode((int)vab_head.getByte(cPos)); cPos++;
			p.setPan((int)vab_head.getByte(cPos)); cPos++;
			cPos++; //Skip reserved
			p.setAttribute(Short.toUnsignedInt(vab_head.shortFromFile(cPos))); cPos += 2;
			cPos += 8; //Skip reserved
			
			programs[i] = p;
		}
		
		//TAT - Tone Attribute Table
		//System.err.println("TAT Offset: 0x" + Long.toHexString(cPos));
		int[] nxttone = new int[128];
		for (int i = 0; i < 128; i++) nxttone[i] = 0;
		int tCount = pCount << 4;
		for (int i = 0; i < tCount; i++)
		{
			byte tpri = vab_head.getByte(cPos); cPos++;
			byte tmode = vab_head.getByte(cPos); cPos++;
			byte tvol = vab_head.getByte(cPos); cPos++;
			byte tpan = vab_head.getByte(cPos); cPos++;
			
			byte tunity = vab_head.getByte(cPos); cPos++;
			byte ttune = vab_head.getByte(cPos); cPos++;
			byte tkrbot = vab_head.getByte(cPos); cPos++;
			byte tkrtop = vab_head.getByte(cPos); cPos++;
			
			byte tvw = vab_head.getByte(cPos); cPos++;
			byte tvt = vab_head.getByte(cPos); cPos++;
			byte tpw = vab_head.getByte(cPos); cPos++;
			byte tpt = vab_head.getByte(cPos); cPos++;
			
			byte tpbmin = vab_head.getByte(cPos); cPos++;
			byte tpbmax = vab_head.getByte(cPos); cPos++;
			cPos += 2; //Skip reserved
			
			short ADSR1 = vab_head.shortFromFile(cPos); cPos+=2;
			short ADSR2 = vab_head.shortFromFile(cPos); cPos+=2;
			short tProg = vab_head.shortFromFile(cPos); cPos+=2;
			short tSamp = vab_head.shortFromFile(cPos); cPos+=2;
			//System.err.println("tProg = " + tProg);
			//System.err.println("tSamp = 0x" + String.format("%04x", tSamp));
			
			cPos += 8; //Skip reserved
			
			//Get the tone object to load information into
			int pp = Short.toUnsignedInt(tProg);
			int ct = nxttone[pp]++;
			Tone t = programs[pp].getTone(ct);
			if(t == null) continue;
			
			t.setPriority(Byte.toUnsignedInt(tpri));
			t.setReverb(tmode != 0);
			t.setVolume(Byte.toUnsignedInt(tvol));
			t.setPan(Byte.toUnsignedInt(tpan));
			t.setUnityKey(Byte.toUnsignedInt(tunity));
			t.setFineTune256(Byte.toUnsignedInt(ttune));
			t.setKeyRange(Byte.toUnsignedInt(tkrbot), Byte.toUnsignedInt(tkrtop));
			t.setVibrato(Byte.toUnsignedInt(tvw), Byte.toUnsignedInt(tvt));
			t.setPortamento(Byte.toUnsignedInt(tpw), Byte.toUnsignedInt(tpt));
			t.setPitchBend(Byte.toUnsignedInt(tpbmin), Byte.toUnsignedInt(tpbmax));
			t.setADSR1(ADSR1);
			t.setADSR2(ADSR2);
			t.setSampleIndex(Short.toUnsignedInt(tSamp)-1);
		}
		
		//SPT - 256 slots
		//System.err.println("Reading sample ptr table: cPos = 0x" + Long.toHexString(cPos));
		int[] sptrs = new int[256];
		for (int i = 0; i < 256; i++)
		{
			short ptr = vab_head.shortFromFile(cPos); cPos += 2;
			sptrs[i] = Short.toUnsignedInt(ptr) << 3;
		}
		
		//Samples
		//Find the start of the audio data...
		/*byte b = mybank.getByte(cPos);
		while (b == (byte)0x99) //TODO: Not great since this might be unique to my BINs...
		{
			cPos++;
			b = mybank.getByte(cPos);
		}*/
		vab_body.setEndian(false);
		long spos = 0;
		for (int i = 0; i < sCount; i++)
		{
			//System.err.println("Sample " + i);
			int vaglen = sptrs[i+1];
			//System.err.println("VagLen: 0x" + Integer.toHexString(vaglen));
			FileBuffer rawsamp = vab_body.createCopy(spos, spos+vaglen);
			PSXVAG parsedsamp = new PSXVAG(rawsamp);
			samples.add(parsedsamp);
			spos += vaglen;
		}
	}
	
	/* ----- Serialization ----- */
	
	public int getSampleChunkSize()
	{
		int tot = 0;
		for (PSXVAG s : samples)
		{
			tot += s.getDataSize();
		}
		return tot;
	}
	
	private FileBuffer serializeHeader()
	{
		int pcount = countPrograms();
		int tcount = countTones();
		int scount = countSamples();
		int vbSize = getSampleChunkSize();
		FileBuffer header = new FileBuffer(HEADER_SIZE);
		header.printASCIIToFile(MAGIC_LE);
		header.addToFile(version);
		header.addToFile(ID);
		
		int waveformsize = HEADER_SIZE;
		waveformsize += (MAX_PROGRAMS * PTE_SIZE);
		waveformsize += (pcount * MAX_TONES_PER_PROGRAM * TTE_SIZE);
		waveformsize += vbSize;
		
		header.addToFile(waveformsize);
		header.addToFile((short)0xEEEE);
		header.addToFile((short)pcount);
		header.addToFile((short)tcount);
		header.addToFile((short)scount);
		
		header.addToFile((byte)masterVolume);
		header.addToFile((byte)masterPan);
		header.addToFile((byte)att1);
		header.addToFile((byte)att2);
	
		header.addToFile(0xFFFFFFFF);
		
		return header;
	}
	
	private FileBuffer serializeProgramTable()
	{
		if (programs == null) throw new IllegalStateException();
		FileBuffer PAT = new CompositeBuffer(MAX_PROGRAMS);
		for (int i = 0; i < MAX_PROGRAMS; i++)
		{
			if (programs[i] != null) PAT.addToFile(programs[i].serializePAT_entry());
			else PAT.addToFile(Program.generateEmptyPAT_entry());
		}
		
		return PAT;
	}
	
	private FileBuffer serializeToneTable()
	{
		if (programs == null) throw new IllegalStateException();
		int tcount = countTones();
		FileBuffer TAT = new CompositeBuffer(tcount);
		for (int i = 0; i < MAX_PROGRAMS; i++)
		{
			if (programs[i] != null) {
				TAT.addToFile(programs[i].serializeTAT_entry(i));
			}
		}
		
		return TAT;
	}
	
	private FileBuffer serializeSamplePointerTable()
	{
		FileBuffer SPT = new FileBuffer(SPT_SIZE);
		SPT.addToFile((short)0x0000);
		
		int i = 0;
		for (PSXVAG s : samples)
		{
			int ssz = s.getDataSize();
			ssz = ssz >>> 8;
			SPT.addToFile((short)ssz);
			i++;
		}
		
		while (i < 255)
		{
			SPT.addToFile((short)0x0000);
			i++;
		}
		
		return SPT;
	}
	
	private FileBuffer serializeSampleChunk()
	{
		int scount = countSamples();
		FileBuffer schunk = new CompositeBuffer(scount);
		
		for (PSXVAG s : samples)
		{
			schunk.addToFile(s.serializeVAGData());
		}
		
		return schunk;
	}
	
	public FileBuffer serializeMe()
	{
		FileBuffer myVAB = new CompositeBuffer(5);
		myVAB.addToFile(serializeHeader());
		myVAB.addToFile(serializeProgramTable());
		myVAB.addToFile(serializeToneTable());
		myVAB.addToFile(serializeSamplePointerTable());
		myVAB.addToFile(serializeSampleChunk());
		
		return myVAB;
	}
	
	public void writeVAB(String outpath) throws IOException
	{
		FileBuffer me = serializeMe();
		me.writeFile(outpath);
	}
	
	/* ----- Getters ----- */
	
	public int getVersion()
	{
		return version;
	}
	
	public int getID()
	{
		return ID;
	}
	
	public int getVolume()
	{
		return masterVolume;
	}
	
	public int getPan()
	{
		return masterPan;
	}
	
	public int getAttribute1()
	{
		return att1;
	}
	
	public int getAttribute2()
	{
		return att2;
	}

	public Program getProgram(int i)
	{
		if (i < 0) return null;
		if (i >= 128) return null;
		return programs[i];
	}
	
	public PSXVAG getSample(int i)
	{
		if (i < 0) return null;
		if (i >= samples.size()) return null;
		return samples.get(i);
	}
	
	public int countPrograms()
	{
		int tot = 0;
		if (programs == null) return 0;
		for (int i = 0; i < programs.length; i++)
		{
			if (programs[i] != null) tot++;
		}
		return tot;
	}
	
	public int countTones()
	{
		int tot = 0;
		if (programs == null) return 0;
		for (int i = 0; i < programs.length; i++)
		{
			if (programs[i] != null) {
				tot += programs[i].getToneCount();
			}
		}
		return tot;
	}
	
	public int countSamples()
	{
		return samples.size();
	}
	
	public String getName(){return name;}
	
	/* ----- Setters ----- */
	
	public void setName(String s){name = s;}
	
	/* ----- Playback ----- */
	
	public void setOneBasedIndexing(boolean b)
	{
		i1 = b;
	}
	
	public VABSampleMap generateSampleMap()
	{
		return new VABSampleMap(samples);
	}
	
	public SynthProgram getProgram(int bankIndex, int programIndex)
	{
		if(playables == null)
		{
			playables = new VABSynthProgram[128];
			VABSampleMap sm = generateSampleMap();
			for(int i = 0; i < 128; i++)
			{
				playables[i] = new VABSynthProgram(this, sm, i);
			}
		}
		
		if(i1) programIndex--;
		return playables[programIndex];
	}
	
	/* ----- Conversion ----- */
	
	public static int scaleVibratoWidth(int vibWidth)
	{
		return 0;
	}
	
	public static int scaleVibratoTime(int vibTime)
	{
		return 0;
	}
	
	public static int scalePortamentoWidth(int pWidth)
	{
		return 0;
	}
	
	public static int scalePortamentoTime(int pTime)
	{
		return 0;
	}
	
	public static int scaleVolume(int toneVolume)
	{
		double vol = (double)toneVolume / (double)0x7F;
		return (int)Math.round(vol * (double)0x7FFFFFFF);
	}
	
	public static short scalePan(int tonePan)
	{
		tonePan -= 0x40;
		double ratio = (double)tonePan / (double)0x3F;
		return (short)Math.round(ratio * (double)0x7FFF);
	}
	
	private static String generateSampleKey(int number)
	{
		return "PSX_VAG_" + Integer.toString(number);
	}
	
	public SimpleBank getSoundbank()
	{
		SimpleBank mybank = new SimpleBank("VABp_" + ID, Integer.toString(version), "Sony", 1);
		int idx = mybank.newBank(0, "VAB_Bank");
		
		SingleBank bank = mybank.getBank(idx);
		
		//Add samples
		int si = 0;
		for (PSXVAG s : samples)
		{
			mybank.addSample(generateSampleKey(si), s);
			si++;
		}
		
		//Add programs
		for (int i = 0; i < MAX_PROGRAMS; i++)
		{
			Program p = programs[i];
			if (p != null)
			{
				p.addToSoundbank(bank, i);
			}
		}
		
		return mybank;
	}

	public SoundbankNode getBankTree(String bankname){
		SoundbankNode root = new SoundbankNode(null, bankname, SoundbankNode.NODETYPE_BANK);
		
		root.addMetadataEntry("Volume", String.format("0x%02x", masterVolume));
		root.addMetadataEntry("Pan", String.format("0x%02x", masterPan));
		root.addMetadataEntry("Attribute 1", String.format("0x%04x", att1));
		root.addMetadataEntry("Attribute 2", String.format("0x%04x", att2));
		
		int pcount = 0;
		for(int i = 0; i < programs.length; i++){
			if(programs[i] != null){
				pcount++;
				programs[i].toSoundbankNode(root, String.format("Program %03d", i));
			}
		}
		
		root.addMetadataEntry("Program Count", Integer.toString(pcount));
		root.addMetadataEntry("Sound Sample Count", Integer.toString(samples.size()));
		
		return root;
	}
	
	public void dumpReport(String reportPath) throws IOException
	{
		if (reportPath != null && !reportPath.isEmpty())
		{
			FileWriter fw = new FileWriter(reportPath);
			BufferedWriter bw = new BufferedWriter(fw);
			printInfoToBuffer(bw);
			bw.close();
			fw.close();
		}
	}
	
	public SF2 getSF2(String toolname)
	{
		//Make soundbank
		SimpleBank sb = getSoundbank();
		
		//Set ADSR Converter
		SF2.setADSRConverter(new ADSRC_VAB(120, 150, 120, true));
		
		//Dump to SF2
		SF2 mysf = SF2.createSF2(sb, toolname, false);
		
		//Reset ADSR Converter
		SF2.setADSRConverter(new ADSRC_RetainTime());
		
		return mysf;
	}
	
	public void writeSoundFont2(String sf2path, String reportPath, String toolname) throws IOException, UnsupportedFileTypeException
	{
		//If reportpath is valid, write that first
		if (reportPath != null && !reportPath.isEmpty())
		{
			dumpReport(reportPath);
		}
		
		//Make soundbank
		SimpleBank sb = getSoundbank();
		
		//Set ADSR Converter
		SF2.setADSRConverter(new ADSRC_VAB(120, 150, 120, true));
		
		//Dump to SF2
		SF2.writeSF2(sb, toolname, false, sf2path);
		
		//Reset ADSR Converter
		SF2.setADSRConverter(new ADSRC_RetainTime());
	}
	
	public void printInfoToBuffer(BufferedWriter bw) throws IOException
	{
		if (bw == null) return;
		bw.write("=============================== PlayStation 1 VAB Soundbank ===============================\n");
		bw.write("Dump Date: " + OffsetDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME) + "\n");
		bw.write("VAB Version: " + version + "\n");
		bw.write("VAB ID: " + ID + "\n");
		bw.write("Master Volume: " + String.format("0x%02X", masterVolume) + "\n");
		bw.write("Master Pan: " + String.format("0x%02X", masterPan) + "\n");
		bw.write("Attribute 1: " + String.format("0x%02X", att1) + "\n");
		bw.write("Attribute 2: " + String.format("0x%02X", att2) + "\n");
		bw.write("Sound Sample Count: " + samples.size() + "\n");
		bw.write("\n");
		
		//Write the programs
		if (programs == null)
		{
			bw.write("[No Programs]\n");
			return;
		}
		for (int i = 0; i < programs.length; i++)
		{
			System.err.println("Writing program " + i);
			Program p = programs[i];
			bw.write("--------- PROGRAM " + i + " ---------\n");
			if (p == null) bw.write("\t[Null]\n");
			else
			{
				p.printInfoToBuffer(1, bw);
			}
			bw.write("\n");
		}
	}
	
}
