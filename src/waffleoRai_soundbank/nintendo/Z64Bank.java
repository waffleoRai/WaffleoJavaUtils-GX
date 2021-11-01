package waffleoRai_soundbank.nintendo;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import waffleoRai_Files.ConverterAdapter;
import waffleoRai_Files.FileClass;
import waffleoRai_Files.NodeMatchCallback;
import waffleoRai_Files.WriterPrintable;
import waffleoRai_Files.tree.DirectoryNode;
import waffleoRai_Files.tree.FileNode;
import waffleoRai_Sound.Sound;
import waffleoRai_Sound.SoundFileDefinition;
import waffleoRai_Sound.WAV;
import waffleoRai_Sound.WAV.LoopType;
import waffleoRai_Sound.nintendo.Z64Wave;
import waffleoRai_Sound.nintendo.Z64Wave.NUSALADPCMWave;
import waffleoRai_Sound.nintendo.Z64Wave.NUSWave2WAVConverter;
import waffleoRai_SoundSynth.SynthBank;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;
import waffleoRai_soundbank.SimpleBank;
import waffleoRai_soundbank.SimpleInstrument;
import waffleoRai_soundbank.SimpleInstrument.InstRegion;
import waffleoRai_soundbank.SimplePreset;
import waffleoRai_soundbank.SimplePreset.PresetRegion;
import waffleoRai_soundbank.sf2.SF2;
import waffleoRai_soundbank.SingleBank;
import waffleoRai_soundbank.SoundbankDef;

public class Z64Bank implements WriterPrintable{
	
	/*----- Constants -----*/
	
	public static final String SOUND_KEY_STEM = "wave_";
	
	public static final String FNMETAKEY_I0COUNT = "I0COUNT";
	public static final String FNMETAKEY_I1COUNT = "I1COUNT";
	public static final String FNMETAKEY_I2COUNT = "I2COUNT";
	public static final String FNMETAKEY_BNKIDKEY = "U64BNKID";
	
	/*----- Inner Classes -----*/
	
	public static class LoopBlock{
		private int addr;
		
		private int start;
		private int end;
		private int count;
		private int unk_1;
		private short[] vals;
	}
	
	public static class Predictor{
		private int addr;
		
		private int order;
		private int count;
		
		private short[] table;
	}
	
	public static class WaveInfoBlock{
		
		private int addr;
		
		private int flags;
		private int length;
		private int base; //From WARC start, that is
		
		private int loop_offset;
		private int pred_offset;
		
		private LoopBlock loop;
		private Predictor pred;
		
		public int getFlags(){return flags;}
		public int getBankOffset(){return addr;}
		public int getOffset(){return base;}
		public int getLength(){return length;}
	
		public boolean loops(){
			if(loop == null) return false;
			if(loop.count == 0) return false;
			return true;
		}
		
		public int getPredCount(){return pred.count;}
		public int getPredOrder(){return pred.order;}
		
		public short[] getPredictorTable(){
			if(pred == null) return null;
			return pred.table;
		}
		
		public int getLoopStart(){
			if(loop == null) return -1;
			return loop.start;
		}
		
		public int getLoopEnd(){
			if(loop == null) return -1;
			return loop.end;
		}
		
		public int getLoopCount(){
			if(loop == null) return -1;
			if(loop.count == 0) return -1;
			if(loop.count == -1) return 0;
			return loop.count;
		}
		
		public boolean isTwoBit(){
			return (flags & 0x30) != 0;
		}
		
	}
	
	public static class InstrBlock{
		
		private boolean valid;
		private int addr; //debug field, really
		private int lvl;
		
		private short decay;
		private short attack;
		
		//Unk_1 looks like an offset? And these occur between the wave offset fields.
		//They might have to do with each wave - either be an offset or some region data?
		private int unk_1;
		private int unk_2;
		private int unk_3;
		
		private int off_unktbl;
		private float unk_f;
		
		private int off_wave_prev;
		private int off_wave_primary;
		private int off_wave_secondary;
		
		public InstrBlock(boolean empty){
			valid = !empty;
			lvl = 0;
		}
		
	}
	
	/*----- Instance Variables -----*/
	
	private Map<Integer, WaveInfoBlock> waveMap;
	private Map<Integer, LoopBlock> loopMap;
	private Map<Integer, Predictor> predMap;
	private Map<Integer, short[]> itblMap;
	
	private ArrayList<InstrBlock> instruments;
	
	//private Map<Integer, Integer> posmap; //DEBUG
	
	/*----- Initialization -----*/
	
	protected Z64Bank(int instr_alloc){
		waveMap = new TreeMap<Integer, WaveInfoBlock>();
		loopMap = new TreeMap<Integer, LoopBlock>();
		predMap = new TreeMap<Integer, Predictor>();
		itblMap = new TreeMap<Integer, short[]>();
		instruments = new ArrayList<InstrBlock>(instr_alloc);
		//posmap = new HashMap<Integer, Integer>();
	}
	
	/*----- Parsing -----*/
	
	private WaveInfoBlock readWaveBlock(FileBuffer data, int pos, Set<Integer> posset){
		WaveInfoBlock wb = new WaveInfoBlock();
		wb.addr = pos;
		wb.flags = Byte.toUnsignedInt(data.getByte(pos));
		wb.length = data.shortishFromFile(pos+1);
		wb.base = data.intFromFile(pos + 4);
		wb.loop_offset = data.intFromFile(pos + 8);
		wb.pred_offset = data.intFromFile(pos + 12);
		//System.err.println("Reading wave block at 0x" + Integer.toHexString(wb.addr));
		
		//Loop
		int os = wb.loop_offset;
		if(os != 0){
			if(!loopMap.containsKey(os)){
				posset.add(os);
				wb.loop = new LoopBlock();
				wb.loop.addr = os;
				wb.loop.start = data.intFromFile(os);
				wb.loop.end = data.intFromFile(os+4);
				wb.loop.count = data.intFromFile(os+8);
				wb.loop.unk_1 = data.intFromFile(os+12);
				loopMap.put(os, wb.loop);
			}
			else{
				wb.loop = loopMap.get(os);
			}
		}
		
		//Predictor
		os = wb.pred_offset;
		posset.add(os);
		if(os != 0){
			if(!predMap.containsKey(os)){
				wb.pred = new Predictor();
				wb.pred.addr = os;
				wb.pred.order = data.intFromFile(os);
				wb.pred.count = data.intFromFile(os+4);
				
				int n = wb.pred.order * wb.pred.count * 8;
				wb.pred.table = new short[n];
				os += 8;
				for(int i = 0; i < n; i++){
					wb.pred.table[i] = data.shortFromFile(os);
					os+=2;
				}
				predMap.put(os, wb.pred);
			}
			else wb.pred = predMap.get(os);
		}
		
		return wb;
	}
	
	public static Z64Bank readBank(FileBuffer data){
		return readBank(data, -1, -1, -1);
	}
	
	public static Z64Bank readBank(FileBuffer data, int s0count, int s1count, int s2count){
		if(data == null) return null;
		Z64Bank bank = new Z64Bank(128);
		int s1_offset = data.intFromFile(0L);
		int s2_offset = data.intFromFile(4L);
		
		int cpos = 8;
		Set<Integer> posset = new TreeSet<Integer>();
		posset.add(s1_offset); 
		posset.add(s2_offset);
		posset.add((int)data.getFileSize());
		int n = 0;
		if(s0count < 0) s0count = Integer.MAX_VALUE;
		while(!posset.contains(cpos) && (n++ < s0count)){
			
			int s0_off = data.intFromFile(cpos); cpos += 4;
			if(s0_off == 0){
				bank.instruments.add(new InstrBlock(true));
				continue;
			}
			if(s0_off < 0 || s0_off >= data.getFileSize()) break;
			posset.add(s0_off);
			
			InstrBlock inst = new InstrBlock(false);
			inst.addr = s0_off;
			inst.decay = data.shortFromFile(s0_off);
			inst.attack = data.shortFromFile(s0_off+2);
			inst.unk_1 = data.intFromFile(s0_off+4);
			inst.off_wave_prev = data.intFromFile(s0_off+8);
			inst.unk_2 = data.intFromFile(s0_off+12);
			inst.off_wave_primary = data.intFromFile(s0_off+16);
			inst.unk_3 = data.intFromFile(s0_off+20);
			inst.off_wave_secondary = data.intFromFile(s0_off+24);
			bank.instruments.add(inst);
			inst.unk_f = Float.intBitsToFloat(inst.unk_3);
			
			inst.off_unktbl = inst.unk_1;
			if(inst.off_unktbl > 0){
				if(!bank.itblMap.containsKey(inst.off_unktbl)){
					short[] vals = new short[8];
					for(int i = 0; i < 8; i++){
						vals[i] = data.shortFromFile(inst.off_unktbl + (i << 1));
					}
					bank.itblMap.put(inst.off_unktbl, vals);
				}
			}
			
			//bank.posmap.put(inst.addr, 0); //0 is sec0 instrument
			
			//Read wave blocks
			WaveInfoBlock wb = null;
			int os = inst.off_wave_prev;
			if(os > 0){
				wb = bank.waveMap.get(os);
				if(wb == null){
					wb = bank.readWaveBlock(data, os, posset);
					bank.waveMap.put(os, wb);
					posset.add(os);
					
					/*bank.posmap.put(os, 3);
					if(wb.loop != null){
						bank.posmap.put(wb.loop.addr, 4);
					}
					if(wb.pred != null){
						bank.posmap.put(wb.pred.addr, 5);
					}*/
				}
			}
			
			os = inst.off_wave_primary;
			if(os > 0){
				wb = bank.waveMap.get(os);
				if(wb == null){
					wb = bank.readWaveBlock(data, os, posset);
					bank.waveMap.put(os, wb);
					posset.add(os);
					
					/*bank.posmap.put(os, 3);
					if(wb.loop != null){
						bank.posmap.put(wb.loop.addr, 4);
					}
					if(wb.pred != null){
						bank.posmap.put(wb.pred.addr, 5);
					}*/
				}
			}
			
			os = inst.off_wave_secondary;
			if(os > 0){
				wb = bank.waveMap.get(os);
				if(wb == null){
					wb = bank.readWaveBlock(data, os, posset);
					bank.waveMap.put(os, wb);
					posset.add(os);
					
					/*bank.posmap.put(os, 3);
					if(wb.loop != null){
						bank.posmap.put(wb.loop.addr, 4);
					}
					if(wb.pred != null){
						bank.posmap.put(wb.pred.addr, 5);
					}*/
				}
			}
		}
		
		//Section 1
		if(s1_offset > 0){
			cpos = s1_offset;
			//s2 offset is in set, so this catches that as well as any other block.
			posset.remove(cpos);
			n = 0;
			if(s1count < 0) s1count = Integer.MAX_VALUE;
			while(!posset.contains(cpos) && (n++ < s1count)){ 
				int ioff = data.intFromFile(cpos); cpos += 4;
				if(ioff <= 0){
					bank.instruments.add(new InstrBlock(true));
					continue;
				}
				
				posset.add(ioff);
				//bank.posmap.put(ioff, 1);
				
				InstrBlock inst = new InstrBlock(false);
				inst.addr = ioff;
				inst.lvl = 1;
				inst.decay = data.shortFromFile(ioff);
				inst.attack = data.shortFromFile(ioff+2);
				inst.off_wave_primary = data.intFromFile(ioff+4);
				inst.unk_1 = data.intFromFile(ioff+8); //Looks like a float
				inst.unk_2 = data.intFromFile(ioff+12);
				inst.unk_f = Float.intBitsToFloat(inst.unk_1);
				
				bank.instruments.add(inst);
				
				inst.off_unktbl = inst.unk_2;
				if(inst.off_unktbl > 0){
					if(!bank.itblMap.containsKey(inst.off_unktbl)){
						short[] vals = new short[8];
						for(int i = 0; i < 8; i++){
							vals[i] = data.shortFromFile(inst.off_unktbl + (i << 1));
						}
						bank.itblMap.put(inst.off_unktbl, vals);
					}
				}
				
				int os = inst.off_wave_primary;
				if(os > 0){
					WaveInfoBlock wb = bank.waveMap.get(os);
					if(wb == null){
						wb = bank.readWaveBlock(data, os, posset);
						bank.waveMap.put(os, wb);
						posset.add(os);
						
						/*bank.posmap.put(os, 3);
						if(wb.loop != null){
							bank.posmap.put(wb.loop.addr, 4);
						}
						if(wb.pred != null){
							bank.posmap.put(wb.pred.addr, 5);
						}*/
					}
				}
			}	
		}
		
		
		//Section 2
		if(s2_offset > 0){
			cpos = s2_offset;
			posset.remove(cpos);
			n = 0;
			if(s2count < 0) s2count = Integer.MAX_VALUE;
			while(!posset.contains(cpos) && (n++ < s2count)){ 
				int ioff = data.intFromFile(cpos); cpos += 4;
				if(ioff <= 0){
					bank.instruments.add(new InstrBlock(true));
					cpos+=4;
					continue;
				}
				int val = data.intFromFile(cpos); cpos+=4;
				
				posset.add(ioff);
				//bank.posmap.put(ioff, 2);
				
				InstrBlock inst = new InstrBlock(false);
				inst.addr = ioff;
				inst.lvl = 2;
				//Level 2 instruments appear to just be a float value and a wave block?
				inst.unk_1 = val;
				inst.unk_f = Float.intBitsToFloat(inst.unk_1);
				inst.off_wave_primary = ioff;
				
				bank.instruments.add(inst);
				
				int os = inst.off_wave_primary;
				if(os > 0){
					WaveInfoBlock wb = bank.waveMap.get(os);
					if(wb == null){
						wb = bank.readWaveBlock(data, os, posset);
						bank.waveMap.put(os, wb);
						posset.add(os);
						
						/*bank.posmap.put(os, 3);
						if(wb.loop != null){
							bank.posmap.put(wb.loop.addr, 4);
						}
						if(wb.pred != null){
							bank.posmap.put(wb.pred.addr, 5);
						}*/
					}
				}
			}	
		}
		
		return bank;
	}
	
	/*----- Serialization -----*/
	
	/*----- Getters -----*/
	
	public List<WaveInfoBlock> getAllWaveInfoBlocks(){
		int wcount = waveMap.size();
		List<WaveInfoBlock> list = new ArrayList<WaveInfoBlock>(wcount+1);
		List<Integer> orderlist = new ArrayList<Integer>(wcount+1);
		orderlist.addAll(waveMap.keySet());
		Collections.sort(orderlist);
		for(Integer k : orderlist){
			list.add(waveMap.get(k));
		}
		return list;
	}
	
	/*----- Setters -----*/

	/*----- Conversion -----*/
	
	public String getSoundKey(int winfo_off){
		WaveInfoBlock wb = waveMap.get(winfo_off);
		return SOUND_KEY_STEM + String.format("%08x", wb.base);
	}
	
	public boolean soundLoops(int winfo_off){
		WaveInfoBlock wb = waveMap.get(winfo_off);
		if(wb == null) return false;
		return wb.loops();
	}
	
	public Map<Integer, Z64Wave> extractSounds(FileBuffer sound_data){
		Map<Integer, Z64Wave> map = new TreeMap<Integer, Z64Wave>();
		List<Integer> keys = new ArrayList<Integer>(waveMap.size()+1);
		keys.addAll(waveMap.keySet());
		for(Integer k : keys){
			WaveInfoBlock winfo = waveMap.get(k);
			long wst = Integer.toUnsignedLong(winfo.getOffset());
			long wed = wst + winfo.getLength();
			FileBuffer wdat = sound_data.createReadOnlyCopy(wst, wed);
			short[] tbl = winfo.getPredictorTable();
			int loopst = winfo.getLoopStart();
			int looped = winfo.getLoopEnd();
			if(!winfo.loops()){
				loopst = -1; looped = -1;
			}
			boolean flag = (winfo.getFlags() & 0x30) != 0;
			Z64Wave wave = Z64Wave.readZ64Wave(wdat, tbl, loopst, looped, flag);
			map.put(k, wave);
		}
		return map;
	}
	
	public int addSoundsToBank(SimpleBank bank, FileBuffer sound_data){
		if(bank == null || sound_data == null) return 0;
		
		Map<Integer, Z64Wave> waves = extractSounds(sound_data);
		List<Integer> list = new ArrayList<Integer>(waves.size()+1);
		list.addAll(waves.keySet());
		int c = 0;
		for(Integer k : list){
			String nkey = getSoundKey(k);
			if(bank.getSample(nkey) != null) continue;
			bank.addSample(nkey, waves.get(k));
			c++;
		}
		return c;
	}
	
	public SimpleBank toBank(FileBuffer sound_data, String bankName, String versionString, String vendor){
		//Bank count determined from patch count.
		int pcount = instruments.size();
		SimpleBank mainBank = new SimpleBank(bankName, versionString, vendor, pcount+1);
		
		addToBank(mainBank, 0, sound_data);
		
		return mainBank;
	}

	public SingleBank addToBank(SimpleBank bank, int idx, FileBuffer sound_data){
		//TODO
		if(bank == null) return null;
		int bidx = bank.newBank(idx, "bank_" + idx);
		if(bidx < 0) return null;
		addSoundsToBank(bank, sound_data);
		
		SingleBank myBank = bank.getBank(bidx);
		//I think what I'll do is make the sec1 and sec2 instruments "instruments"
		//And the sec0 instruments "presets"
		//To fit it into one bank.
		int iidx = 0; int lasttype = 0;
		String bprefix = "bnk" + String.format("%02d_", idx);
		for(InstrBlock inst : instruments){
			if(inst.lvl != 0){
				if(inst.lvl != lasttype) iidx = 0;
				//Instrument
				if(!inst.valid) {iidx++; continue;}
				String iname = bprefix +
						"sec" + inst.lvl + "_" +
						String.format("%03d", iidx++);
				System.err.println("Made inst: " + iname);
				SimpleInstrument si = bank.newInstrument(iname, 2);
				int ridx = si.newRegion(getSoundKey(inst.off_wave_primary));
				InstRegion reg = si.getRegion(ridx);
				
				//TODO add articulation data when you figure it out
			}
			else{
				//Preset
				if(!inst.valid){iidx++; continue;}
				String pname = "sec0_" + String.format("%03d", iidx);
				int pidx = myBank.newPreset(iidx++, pname, 3);
				
				SimplePreset preset = myBank.getPreset(pidx);
				int ridx = -1; PresetRegion pr = null;
				SimpleInstrument si = null; InstRegion reg = null;
				if(inst.off_wave_prev > 0){
					ridx = preset.newInstrument(bprefix + pname + "_0", 1);
					pr = preset.getRegion(ridx);
					pr.setMinKey((byte)0); pr.setMaxKey((byte)42);
					si = pr.getInstrument();
					ridx = si.newRegion(getSoundKey(inst.off_wave_prev));
					reg = si.getRegion(ridx);
					//TODO Articulation to reg here
				}
				if(inst.off_wave_secondary > 0){
					ridx = preset.newInstrument(bprefix + pname + "_2", 1);
					pr = preset.getRegion(ridx);
					if(inst.off_wave_prev > 0){
						pr.setMinKey((byte)84); pr.setMaxKey((byte)127);
					}
					else{
						pr.setMinKey((byte)64); pr.setMaxKey((byte)127);
					}
					si = pr.getInstrument();
					ridx = si.newRegion(getSoundKey(inst.off_wave_secondary));
					reg = si.getRegion(ridx);
					//TODO Articulation to reg here
				}
				if(inst.off_wave_primary > 0){
					ridx = preset.newInstrument(bprefix + pname + "_1", 1);
					pr = preset.getRegion(ridx);
					if(inst.off_wave_secondary > 0){
						if(inst.off_wave_prev > 0){
							//Middle
							pr.setMinKey((byte)43); pr.setMaxKey((byte)83);
						}
						else{
							//Low
							pr.setMinKey((byte)0); pr.setMaxKey((byte)63);
						}
					}
					si = pr.getInstrument();
					ridx = si.newRegion(getSoundKey(inst.off_wave_primary));
					reg = si.getRegion(ridx);
					//TODO Articulation to reg here
				}
			}
			lasttype = inst.lvl;
		}
		
		return myBank;
	}
	
	/*----- Type Definition -----*/
	
	private static NUSALBankDefinition bnk_def;
	private static NUSBank2SF2Converter conv_sf2;
	
	public static NUSALBankDefinition getDefinition(){
		if(bnk_def == null) bnk_def = new NUSALBankDefinition();
		return bnk_def;
	}
	
	public static NUSBank2SF2Converter getConverter_sf2(){
		if(conv_sf2 == null) conv_sf2 = new NUSBank2SF2Converter();
		return conv_sf2;
	}
	
	public static class NUSALBankDefinition extends SoundbankDef{
		
		private static String DEFO_ENG_DESC = "Nintendo 64 Audio Library Soundbank";
		public static int TYPE_ID = 0x6458a04e;

		private String desc;
		
		public NUSALBankDefinition(){
			desc = DEFO_ENG_DESC;
		}
		
		public Collection<String> getExtensions() {
			//I'll just make one up to match later consoles since there isn't one
			List<String> list = new ArrayList<String>(1);
			list.add("bubnk");
			return list;
		}

		public String getDescription() {return desc;}
		public int getTypeID() {return TYPE_ID;}
		public void setDescriptionString(String s) {desc = s;}
		public String getDefaultExtension() {return "bubnk";}

		@Override
		public SynthBank getPlayableBank(FileNode file) {
			// TODO Auto-generated method stub
			return null;
		}

		public String getBankIDKey(FileNode file) {return FNMETAKEY_BNKIDKEY;}

	}
	
	public static class NUSBank2SF2Converter extends ConverterAdapter{
		
		private static final String DEFO_FROMSTR_ENG = "N64 Standard Audio Library Soundbank";
		private static final String DEFO_TOSTR_ENG = "SoundFont 2 (.sf2)";
		
		public NUSBank2SF2Converter(){
			super("bubnk", DEFO_FROMSTR_ENG, "sf2", DEFO_TOSTR_ENG);
		}

		public void writeAsTargetFormat(String inpath, String outpath)
				throws IOException, UnsupportedFileTypeException {
			writeAsTargetFormat(FileBuffer.createBuffer(inpath, true), outpath);
		}

		public void writeAsTargetFormat(FileBuffer input, String outpath)
				throws IOException, UnsupportedFileTypeException {
			throw new UnsupportedFileTypeException("Sound data required for SF2 conversion");
		}

		public void writeAsTargetFormat(FileNode node, String outpath)
				throws IOException, UnsupportedFileTypeException {
			
			//Look for sound data
			//Get tree root
			DirectoryNode parent = node.getParent();
			while(parent.getParent() != null) parent = parent.getParent();
			FileNode warcnode = null;
			String checkpath = parent.findNodeThat(new NodeMatchCallback(){
				public boolean meetsCondition(FileNode n) {
					if(n.isDirectory()) return false;
					return n.getFileName().endsWith("Audiotable");
				}
			});
			
			if(checkpath == null) throw new UnsupportedFileTypeException("Sound data required for SF2 conversion");
			warcnode = parent.getNodeAt(checkpath);
			
			//Load bank
			int i0 = -1, i1 = -1, i2 = -1;
			try{
				String val = node.getMetadataValue(FNMETAKEY_I0COUNT);
				if(val != null) i0 = Integer.parseInt(val);
				val = node.getMetadataValue(FNMETAKEY_I1COUNT);
				if(val != null) i1 = Integer.parseInt(val);
				val = node.getMetadataValue(FNMETAKEY_I2COUNT);
				if(val != null) i2 = Integer.parseInt(val);
			}
			catch(NumberFormatException ex){
				System.err.println("Warning: Inst counts could not be parsed from node metadata");
			}
			FileBuffer bankdat = node.loadData();
			Z64Bank bank = Z64Bank.readBank(bankdat, i0, i1, i2);
			
			//Load WArc
			FileBuffer warcdat = warcnode.loadData();
			
			//Convert
			SimpleBank sbank = bank.toBank(warcdat, node.getFileName(), "version_unk", "Nintendo64 AL");
			SF2.writeSF2(sbank, "WaffleoJavaUtilsGX", false, outpath);
		}
	}
	
	/*----- Debug -----*/
	
	public void printMeTo(Writer out) throws IOException{
		out.write("Instruments: " + instruments.size() + " ----------\n");
		for(int i = 0; i < instruments.size(); i++){
			out.write("\t->Inst " + i + "\n");
			InstrBlock inst = instruments.get(i);
			if(!inst.valid){
				out.write("\t\t(Empty)\n");
				continue;
			}
			if(inst.addr > 0){
				out.write("\t\tFound At: 0x" + Integer.toHexString(inst.addr) + "\n");
			}
			out.write("\t\tType: " + inst.lvl + "\n");
			if(inst.lvl != 2){
				out.write("\t\tDecay: 0x" + String.format("%04x", inst.decay) + "\n");
				out.write("\t\tAttack: 0x" + String.format("%04x", inst.attack) + "\n");
			}
			if(inst.lvl == 0){
				out.write("\t\t(Unk 1): 0x" + String.format("%08x", inst.unk_1) + "\n");
				out.write("\t\t(Unk 2): 0x" + String.format("%08x", inst.unk_2) + "\n");
				out.write("\t\t(Unk 3): 0x" + String.format("%08x", inst.unk_3) + "\n");
				out.write("\t\t(Unk Float): " + inst.unk_f + "\n");
				if(inst.off_wave_prev > 0){
					out.write("\t\tWave0: 0x" + Integer.toHexString(inst.off_wave_prev) + "\n");
				}
				else out.write("\t\tWave0: <N/A>\n");
				if(inst.off_wave_primary > 0){
					out.write("\t\tWave1: 0x" + Integer.toHexString(inst.off_wave_primary) + "\n");
				}
				else out.write("\t\tWave1: <N/A>\n");
				if(inst.off_wave_secondary > 0){
					out.write("\t\tWave2: 0x" + Integer.toHexString(inst.off_wave_secondary) + "\n");
				}
				else out.write("\t\tWave2: <N/A>\n");	
			}
			else if (inst.lvl == 1){
				out.write("\t\t(Unk 1): 0x" + String.format("%08x", inst.unk_1) + "\n");
				out.write("\t\t(Unk 2): 0x" + String.format("%08x", inst.unk_2) + "\n");
				out.write("\t\t(Unk Float): " + inst.unk_f + "\n");
				if(inst.off_wave_primary> 0){
					out.write("\t\tWave: 0x" + Integer.toHexString(inst.off_wave_primary) + "\n");
				}
				else out.write("\t\tWave: <N/A>\n");
			}
			else if (inst.lvl == 2){
				out.write("\t\t(Unk 1): 0x" + String.format("%08x", inst.unk_1) + "\n");
				out.write("\t\t(Unk Float): " + inst.unk_f + "\n");
				if(inst.off_wave_primary> 0){
					out.write("\t\tWave: 0x" + Integer.toHexString(inst.off_wave_primary) + "\n");
				}
				else out.write("\t\tWave: <N/A>\n");
			}
			
		}
		out.write("\n");
		
		out.write("Waves: " + waveMap.size() + " ----------\n");
		List<Integer> widx = new ArrayList<Integer>(waveMap.size()+1);
		widx.addAll(waveMap.keySet());
		Collections.sort(widx);
		int i = 0;
		for(Integer w : widx){
			WaveInfoBlock wb = waveMap.get(w);
			out.write("\t->Wave " + (i++) + "\n");
			if(wb.addr > 0){
				out.write("\t\tFound At: 0x" + Integer.toHexString(wb.addr) + "\n");
			}
			out.write("\t\tFlags: 0x" + String.format("%02x", wb.flags) + "\n");
			out.write("\t\tLength: 0x" + Integer.toHexString(wb.length) + "\n");
			out.write("\t\tBase: 0x" + Integer.toHexString(wb.base) + "\n");
			if(wb.loop != null){
				out.write("\t\tLoop Location: 0x" + Integer.toHexString(wb.loop_offset) + "\n");
				out.write("\t\t\tLoop Start: 0x" + Integer.toHexString(wb.loop.start) + "\n");
				out.write("\t\t\tLoop End: 0x" + Integer.toHexString(wb.loop.end) + "\n");
				out.write("\t\t\tLoop Count: " + wb.loop.count + "\n");
				out.write("\t\t\tLoop (Unk_1): 0x" + Integer.toHexString(wb.loop.unk_1) + "\n");
			}
			if(wb.pred != null){
				out.write("\t\tPred Location: 0x" + Integer.toHexString(wb.pred_offset) + "\n");
				out.write("\t\t\tPred Order: " + wb.pred.order + "\n");
				out.write("\t\t\tPred Entries: " + wb.pred.count + "\n");
			}
		}
		
		//DEBUG -----
		/*out.write("Blocks: " + posmap.size() + " ----------\n");
		widx = new ArrayList<Integer>(posmap.size()+1);
		widx.addAll(posmap.keySet());
		Collections.sort(widx);
		i = 0;
		for(Integer w : widx){
			out.write("\t0x" + Integer.toHexString(w));
			int val = posmap.get(w);
			switch(val){
			case 0: out.write("\tInstr (lvl 0)"); break;
			case 1: out.write("\tInstr (lvl 1)"); break;
			case 2: out.write("\tInstr (lvl 2)"); break;
			case 3: out.write("\tWave Info"); break;
			case 4: out.write("\tLoop Info"); break;
			case 5: out.write("\tADPCM Table"); break;
			default: out.write("\t<Unknown>"); break;
			}
			out.write("\n");
		}*/
		
	}
	
	public static void main(String[] args){
		String dirpath = "C:\\Users\\Blythe\\Documents\\Desktop\\out";
		//String inpath = dirpath + "\\mm_soundbank_0.bin";
		String logpath = dirpath + "\\mm_soundbank_3_readtest.out";
		String tblpath = dirpath + "\\n64test\\majora_bank_loc.txt";
		String outpath = dirpath + "\\n64test\\majora_sounds.sf2";
		String rompath = "";
		
		try{
			/*FileBuffer rom = FileBuffer.createBuffer(rompath, true);
			FileBuffer bnkdat = rom.createReadOnlyCopy(0x2CC70, 0x2E240);
			//FileBuffer wardat = rom.createCopy(0x97F70, rom.getFileSize());
			Z64Bank bnk = Z64Bank.readBank(bnkdat);
			BufferedWriter bw = new BufferedWriter(new FileWriter(logpath));
			bnk.printMeTo(bw);
			bw.close();*/
			
			/*FileBuffer data = FileBuffer.createBuffer(inpath, true);
			Z64Bank bnk = Z64Bank.readBank(data);
			BufferedWriter bw = new BufferedWriter(new FileWriter(logpath));
			bnk.printMeTo(bw);
			bw.close();*/
			
			FileBuffer rom = FileBuffer.createBuffer(rompath, true);
			long[][] offtbl = new long[50][2];
			BufferedReader br = new BufferedReader(new FileReader(tblpath));
			String line = null; int i = 0;
			while((line = br.readLine()) != null){
				String[] fields = line.split(",");
				offtbl[i][0] = Long.parseUnsignedLong(fields[0], 16);
				offtbl[i][1] = Long.parseUnsignedLong(fields[1], 16);
				i++;
			}
			br.close();
			int bcount = i;
			
			//Make main bank
			SimpleBank bank = new SimpleBank("Majora's Mask Composite", "1.0.0", "Nintendo EAD", bcount);
			
			//Loop through ROM banks
			System.err.println("bcount = " + bcount);
			long end = 0x46AF0L;
			for(i = 0; i < bcount-3; i++){
				System.err.println("Reading bank " + i);
				long b_st = offtbl[i][0];
				long b_ed = end;
				if((i+1) < bcount) b_ed = offtbl[i+1][0];
				long warc_off = offtbl[i][1];
				
				FileBuffer bnkdat = rom.createReadOnlyCopy(b_st, b_ed);
				FileBuffer wardat = rom.createCopy(warc_off, rom.getFileSize());
				
				Z64Bank zbank = Z64Bank.readBank(bnkdat);
				zbank.addToBank(bank, i, wardat);
				
				bnkdat.dispose(); wardat.dispose();
			}
			
			//Write to SF2
			SF2.writeSF2(bank, "waffleoRaiUtilsGX", false, outpath);
		}
		catch(Exception ex){
			ex.printStackTrace();
			System.exit(1);
		}
	}
	
}
