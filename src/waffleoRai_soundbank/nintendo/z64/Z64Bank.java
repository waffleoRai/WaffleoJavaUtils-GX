package waffleoRai_soundbank.nintendo.z64;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import waffleoRai_Files.ConverterAdapter;
import waffleoRai_Files.NodeMatchCallback;
import waffleoRai_Files.WriterPrintable;
import waffleoRai_Files.tree.DirectoryNode;
import waffleoRai_Files.tree.FileNode;
import waffleoRai_Sound.nintendo.N64ADPCMTable;
import waffleoRai_Sound.nintendo.Z64Sound;
import waffleoRai_Sound.nintendo.Z64Wave;
import waffleoRai_Sound.nintendo.Z64WaveInfo;
import waffleoRai_SoundSynth.SynthBank;
import waffleoRai_SoundSynth.SynthMath;
import waffleoRai_Utils.BinFieldSize;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;
import waffleoRai_Utils.FileUtils;
import waffleoRai_Utils.SerializedString;
import waffleoRai_soundbank.SimpleBank;
import waffleoRai_soundbank.SimpleInstrument;
import waffleoRai_soundbank.SimpleInstrument.InstRegion;
import waffleoRai_soundbank.SimplePreset;
import waffleoRai_soundbank.SimplePreset.PresetRegion;
import waffleoRai_soundbank.sf2.SF2;
import waffleoRai_soundbank.SingleBank;
import waffleoRai_soundbank.SoundbankDef;
import waffleoRai_soundbank.nintendo.z64.Z64BankBlocks.*;

public class Z64Bank implements WriterPrintable{
	
	/*----- Constants -----*/
	
	public static final String SOUND_KEY_STEM = "wave64_";
	
	public static final String UBNK_MAGIC = "UBNK";
	public static final int UBNK_VERSION_MAJOR = 1;
	public static final int UBNK_VERSION_MINOR = 0;
	public static final String UWSD_MAGIC = "UWSD";
	public static final int UWSD_VERSION_MAJOR = 1;
	public static final int UWSD_VERSION_MINOR = 0;
	
	public static final String FNMETAKEY_I0COUNT = "INSTCOUNT";
	public static final String FNMETAKEY_I1COUNT = "PERCCOUNT";
	public static final String FNMETAKEY_I2COUNT = "SFXCOUNT";
	public static final String FNMETAKEY_BNKIDKEY = "U64BNKID";
	
	public static final int STDRANGE_BOTTOM = Z64Sound.STDRANGE_BOTTOM;
	public static final int STDRANGE_SIZE = Z64Sound.STDRANGE_SIZE;
	public static final int MIDDLE_C = Z64Sound.MIDDLE_C;
	
	/*----- Private Classes -----*/
	
	protected static abstract class Labelable{
		protected String name;
		public String getName(){return name;}
		public void setName(String s){name = s;}
	}
	
	/*----- Instance Variables -----*/

	private Map<Integer, Z64Instrument> inst_pool;
	private Map<Integer, Z64Drum> perc_pool;
	private Map<Integer, Z64SoundEffect> sfx_pool;
	private Map<Integer, WaveInfoBlock> sound_samples; //Mapped by *sample* offset in warc (for ordering)
	
	private InstBlock[] inst_presets;
	private PercBlock[] perc_slots;
	private SFXBlock[] sfx_slots;
	
	//For tracking slots used (for easy serialization)
	private int icount = 0;
	private int pcount = 0;
	private int xcount = 0;
	
	private int medium = 2;
	private int cachePolicy = 2;
	private int warc1 = 0;
	private int warc2 = -1;
	
	//If set, then sound_samples are mapped (and serialized) using UID in place of warc offset
	private boolean samples_by_uid = false;
	private int uid = 0;
	
	//Reference fields for parsing
	private int wsd_ref;
	
	/*----- Initialization -----*/
	
	public Z64Bank(){this(0);}
	
	public Z64Bank(int sfx_alloc){
		/*instruments = new ArrayList<InstBlock>(128);
		percussion = new ArrayList<PercBlock>(perc_alloc);
		sound_effects = new ArrayList<SFXBlock>(sfx_alloc);*/
		inst_pool = new TreeMap<Integer, Z64Instrument>();
		perc_pool = new TreeMap<Integer, Z64Drum>();
		sfx_pool = new TreeMap<Integer, Z64SoundEffect>();
		sound_samples = new TreeMap<Integer, WaveInfoBlock>();
		
		inst_presets = new InstBlock[126];
		perc_slots = new PercBlock[64];
		if(sfx_alloc > 0){
			sfx_slots = new SFXBlock[sfx_alloc];
		}
	}
		
	/*----- Parsing -----*/
	
	public static Z64Bank readBank(FileBuffer data){
		return readBank(data, -1, -1, -1);
	}
	
	public static Z64Bank readBank(FileBuffer data, int i_count, int p_count, int e_count){
		//TODO Read ununsed envelopes too
		if(data == null) return null;
		//Determine alloc amount
		int e_alloc = (e_count>0)?e_count:4;
		Z64Bank bank = new Z64Bank(e_alloc);
		
		//Alloc parsing maps...
		Map<Integer, WaveInfoBlock> waveMap = new HashMap<Integer, WaveInfoBlock>();
		Map<Integer, LoopBlock> loopMap = new HashMap<Integer, LoopBlock>();
		Map<Integer, Predictor> predMap = new HashMap<Integer, Predictor>();
		Map<Integer, EnvelopeBlock> envMap = new HashMap<Integer, EnvelopeBlock>();
		Map<Integer, InstBlock> instMap = new HashMap<Integer, InstBlock>();
		Map<Integer, PercBlock> drumMap = new HashMap<Integer, PercBlock>();
		
		//Get Inst/Perc/SFX table offsets...
		data.setCurrentPosition(0);
		long filesize = data.getFileSize();
		long off_ptbl = Integer.toUnsignedLong(data.nextInt());
		long off_etbl = Integer.toUnsignedLong(data.nextInt());
		List<Integer> i_offs = new ArrayList<Integer>(128);
		
		//Try to estimate patch counts if not provided...
		if(e_count < 0){
			if(off_etbl > 0) e_count = (int)((filesize - off_etbl) >>> 3);
			else e_count = 0;
		}
		if(p_count < 0){
			if(off_ptbl > 0) p_count = (int)((off_etbl - off_ptbl) >>> 2);
			else p_count = 0;
		}
		
		//Read instrument table while estimate i count if needed
		if(i_count < 0){
			while(true){
				if(i_offs.size() >= 126) break;
				int val = data.nextInt();
				if((val & 0xf) != 0) break;
				if(val >= filesize) break;
				i_offs.add(val);
			}
			i_count = i_offs.size();
		}
		else{
			for(int i = 0; i < i_count; i++) i_offs.add(data.nextInt());
		}
		
		//Read Instruments
		int idx = 0;
		for(int i_off : i_offs){
			if(waveMap.containsKey(i_off)) break; //In case i_count is an estimate
			if(i_off == 0){
				idx++; continue;
			}
			
			//Check if already read. (eg. assigned to multiple indices)
			if(instMap.containsKey(i_off)){
				bank.inst_presets[idx++] = instMap.get(i_off);
				continue;
			}
			
			//New inst
			InstBlock inst = InstBlock.readFrom(data.getReferenceAt(i_off));
			inst.addr = i_off;
			inst.data.id = i_off;
			bank.inst_presets[idx++] = inst;
			instMap.put(i_off, inst);
			bank.inst_pool.put(i_off, inst.data);
			//System.err.println("Inst read @: 0x" + Integer.toHexString(i_off));

			//Read child blocks
			if(inst.off_env > 0){
				EnvelopeBlock env = envMap.get(inst.off_env);
				if(env != null){
					inst.envelope = env;
				}
				else{
					//Read
					//System.err.println("Reading envelope at " + Integer.toHexString(inst.off_env));
					env = EnvelopeBlock.readFrom(data.getReferenceAt(inst.off_env));
					inst.envelope = env;
					env.addr = inst.off_env;
					env.data.setName("ENV_" + String.format("%08x", env.addr));
					envMap.put(inst.off_env, env);
				}
			}
			inst.data.setName("inst_" + String.format("%08x", inst.addr));
			
			//If reading new block, add tuning from this inst
			WaveInfoBlock winfo = waveMap.get(inst.off_snd_med);
			if(winfo != null) inst.snd_med = winfo;
			else{
				winfo = WaveInfoBlock.readFrom(data.getReferenceAt(inst.off_snd_med));
				winfo.addr = inst.off_snd_med;
				waveMap.put(inst.off_snd_med, winfo);
				inst.snd_med = winfo;
				winfo.wave_info.setTuning(inst.data.getTuningMiddle());
			}
			
			if(inst.off_snd_lo > 0){
				winfo = waveMap.get(inst.off_snd_lo);
				if(winfo != null) inst.snd_lo = winfo;
				else{
					winfo = WaveInfoBlock.readFrom(data.getReferenceAt(inst.off_snd_lo));
					winfo.addr = inst.off_snd_lo;
					waveMap.put(inst.off_snd_lo, winfo);
					inst.snd_lo = winfo;
					winfo.wave_info.setTuning(inst.data.getTuningLow());
				}
			}
			
			if(inst.off_snd_hi > 0){
				winfo = waveMap.get(inst.off_snd_hi);
				if(winfo != null) inst.snd_hi = winfo;
				else{
					winfo = WaveInfoBlock.readFrom(data.getReferenceAt(inst.off_snd_hi));
					winfo.addr = inst.off_snd_hi;
					waveMap.put(inst.off_snd_hi, winfo);
					inst.snd_hi = winfo;
					winfo.wave_info.setTuning(inst.data.getTuningHigh());
				}
			}
		}
		bank.icount = idx;
		
		//Read Drums
		long cpos = off_ptbl;
		for(int i = 0; i < p_count; i++){
			int offset = data.intFromFile(cpos);
			cpos+=4;
			
			if(offset <= 0){
				//Dummy
				bank.perc_slots[i] = null;
				continue;
			}
			
			PercBlock perc = drumMap.get(offset);
			if(perc == null){
				perc = PercBlock.readFrom(data.getReferenceAt(offset), i);
				perc.addr = offset;
				drumMap.put(perc.addr, perc);
				
				if(perc.off_env > 0){
					EnvelopeBlock env = envMap.get(perc.off_env);
					if(env != null){
						perc.envelope = env;
					}
					else{
						//Read
						//System.err.println("Reading envelope at " + Integer.toHexString(perc.off_env));
						env = EnvelopeBlock.readFrom(data.getReferenceAt(perc.off_env));
						perc.envelope = env;
						env.addr = perc.off_env;
						env.data.setName("ENV_" + String.format("%08x", env.addr));
						envMap.put(perc.off_env, env);
					}
				}
				
				WaveInfoBlock winfo = waveMap.get(perc.off_snd);
				if(winfo != null){
					perc.sample = winfo;
				}
				else{
					winfo = WaveInfoBlock.readFrom(data.getReferenceAt(perc.off_snd));
					winfo.addr = perc.off_snd;
					waveMap.put(perc.off_snd, winfo);
					perc.sample = winfo;
					winfo.wave_info.setTuning(perc.data.getTuning());
				}	
				
				perc.data.id = offset;
				perc.data.setName("drum_" + String.format("%08x", perc.addr));
				bank.perc_pool.put(offset, perc.data);
			}
			bank.perc_slots[i] = perc;
		}
		bank.pcount = p_count;
		
		//Read SFX
		cpos = off_etbl;
		if(e_count > 0){
			if(bank.sfx_slots == null || bank.sfx_slots.length < e_count){
				//Reallocate
			}
		}
		for(int i = 0; i < e_count; i++){
			SFXBlock sfx = SFXBlock.readFrom(data.getReferenceAt(cpos));
			sfx.data.id = sfx.addr = (int)cpos;
			sfx.data.setName("sfx_" + String.format("%08x", sfx.addr));
			cpos+=8;
			if(sfx.off_snd > 0){
				WaveInfoBlock winfo = waveMap.get(sfx.off_snd);
				if(winfo != null) sfx.sample = winfo;
				else{
					winfo = WaveInfoBlock.readFrom(data.getReferenceAt(sfx.off_snd));
					winfo.addr = sfx.off_snd;
					waveMap.put(sfx.off_snd, winfo);
					sfx.sample = winfo;
					winfo.wave_info.setTuning(sfx.data.getTuning());
				}
				bank.sfx_slots[i] = sfx;
				bank.sfx_pool.put(sfx.addr, sfx.data);
			}
		}
		bank.xcount = e_count;
		
		//Read/Link loops and predictors
		List<Integer> ilist = new ArrayList<Integer>(waveMap.size()+1);
		ilist.addAll(waveMap.keySet());
		Collections.sort(ilist);
		for(Integer k : ilist){
			WaveInfoBlock winfo = waveMap.get(k);
			bank.sound_samples.put(winfo.wave_info.getWaveOffset(), winfo);
			if(winfo.loop_offset > 0){
				winfo.loop = loopMap.get(winfo.loop_offset);
				if(winfo.loop == null){
					winfo.loop = LoopBlock.readFrom(data.getReferenceAt(winfo.loop_offset));
					winfo.loop.addr = winfo.loop_offset;
					loopMap.put(winfo.loop_offset, winfo.loop);
				}
			}
			
			if(winfo.pred_offset > 0){
				winfo.pred = predMap.get(winfo.pred_offset);
				if(winfo.pred == null){
					winfo.pred = Predictor.readFrom(data.getReferenceAt(winfo.pred_offset));
					winfo.pred.addr = winfo.pred_offset;
					predMap.put(winfo.pred_offset, winfo.pred);
				}
			}
			
			//Name it
			winfo.wave_info.setName(SOUND_KEY_STEM + String.format("%08x", winfo.wave_info.getWaveOffset()));
		}
		
		bank.updateAllReferences();
		return bank;
	}
	
	public static Z64Bank readUBNK(FileBuffer data) throws UnsupportedFileTypeException{
		if(data == null) return null;
		data.setEndian(true);
		//Read header block
		long mpos = data.findString(0, 0x4, UBNK_MAGIC);
		if(mpos != 0) throw new FileBuffer.UnsupportedFileTypeException("Z64Bank.readUBNK -- Expecting UBNK magic number at offset 0!");
		//Don't need to read the rest of the chunk til version update
		data.setCurrentPosition(0x10L);
		long fsize = data.getFileSize();
		
		//Alloc some places to put things
		Z64Bank bank = new Z64Bank();
		EnvelopeBlock[] envs = null;
		int[] inst_offset_tbl = new int[126];
		Map<Integer, InstBlock> instmap = new HashMap<Integer, InstBlock>();
		List<Labelable> labelq = new LinkedList<Labelable>();
		
		//Read data blocks
		while(data.getCurrentPosition() < fsize){
			String cmagic = data.getASCII_string(data.getCurrentPosition(), 4);
			data.skipBytes(4L);
			int csz = data.nextInt();
			if(cmagic.equals("META")){
				bank.uid = data.nextInt();
				bank.medium = Byte.toUnsignedInt(data.nextByte());
				bank.cachePolicy = Byte.toUnsignedInt(data.nextByte());
				int ival = Byte.toUnsignedInt(data.nextByte());
				if((ival & 0x0) != 0) bank.samples_by_uid = true;
				ival = Byte.toUnsignedInt(data.nextByte());
				if(ival > 0){
					bank.warc1 = Byte.toUnsignedInt(data.nextByte());
					if(ival > 1){
						bank.warc2 = Byte.toUnsignedInt(data.nextByte());
						data.skipBytes(2L);
						if(ival > 4){
							int skip = ival - 4;
							int mod4 = ival % 4;
							if(mod4 != 0) skip += (4-mod4);
							data.skipBytes(skip);
						}
					}
					else data.skipBytes(3L);
				}
				bank.wsd_ref = data.nextInt();
			}
			else if(cmagic.equals("ENVL")){
				long basepos = data.getCurrentPosition();
				int ecount = Short.toUnsignedInt(data.nextShort());
				if(ecount > 0){
					long tpos = data.getCurrentPosition();
					long epos = -1;
					envs = new EnvelopeBlock[ecount];
					for(int i = 0; i < ecount; i++){
						epos = Short.toUnsignedLong(data.shortFromFile(tpos)) + basepos;
						tpos += 2;
						envs[i] = EnvelopeBlock.readFrom(data.getReferenceAt(epos));
						envs[i].addr = (int)epos;
					}	
				}		
				data.skipBytes(csz-2);
			}
			else if(cmagic.equals("INST")){
				bank.icount = data.nextInt();
				for(int i = 0; i < 126; i++){
					inst_offset_tbl[i] = Short.toUnsignedInt(data.nextShort());
				}
				data.skipBytes(4L);
				//Instruments
				for(int i = 0; i < bank.icount; i++){
					int addr = (int)data.getCurrentPosition();
					InstBlock ib = InstBlock.readFrom(data.getReferenceAt(addr));
					ib.addr = addr;
					ib.data.id = addr;
					instmap.put(addr, ib);
					bank.inst_pool.put(ib.addr, ib.data);
					data.skipBytes(32L);
					labelq.add(ib.data);
				}
			}
			else if(cmagic.equals("PERC")){
				bank.pcount = data.nextInt();
				for(int i = 0; i < bank.pcount; i++){
					int addr = (int)data.getCurrentPosition();
					Z64Drum drum = new Z64Drum();
					drum.id = addr;
					drum.setDecay(data.nextByte());
					drum.setPan(data.nextByte());
					int min = Byte.toUnsignedInt(data.nextByte());
					int max = Byte.toUnsignedInt(data.nextByte());
					int waveref = data.nextInt();
					drum.setTuning(Float.intBitsToFloat(data.nextInt()));
					int envref = data.nextInt();
					
					bank.perc_pool.put(drum.id, drum);
					for(int j = min; j <= max; j++){
						PercBlock pblock = new PercBlock(j);
						pblock.data = drum;
						pblock.off_env = envref;
						pblock.off_snd = waveref;
						bank.perc_slots[j] = pblock;
					}
					labelq.add(drum);
				}
			}
			else if(cmagic.equals("LABL")){
				long cpos = data.getCurrentPosition();
				for(Labelable l : labelq){
					SerializedString ss = data.readVariableLengthString("UTF8", cpos, BinFieldSize.WORD, 2);
					l.setName(ss.getString());
					cpos += ss.getSizeOnDisk();
				}
				data.skipBytes(csz);
			}
			else data.skipBytes(csz);
		}
		
		//Link things (need to make dummy wave infos as well)
		for(int i = 0; i < 126; i++){
			if(inst_offset_tbl[i] <= 0) continue;
			if(i >= bank.icount) bank.icount = i;
			InstBlock ib = instmap.get(inst_offset_tbl[i]);
			bank.inst_presets[i] = ib;
			if(ib != null && ib.envelope == null && ib.snd_med == null){
				if(ib.off_env > 0){
					ib.envelope = envs[ib.off_env];
					if(ib.envelope != null) ib.data.setEnvelope(ib.envelope.data);
				}
				if(ib.off_snd_lo > 0){
					int val = ib.off_snd_lo;
					WaveInfoBlock wblock = bank.sound_samples.get(val);
					if(wblock == null){
						wblock = new WaveInfoBlock();
						wblock.addr = val;
						if(bank.samples_by_uid) wblock.wave_info.setUID(val);
						else wblock.wave_info.setWaveOffset(val);
						bank.sound_samples.put(val, wblock);
					}
					ib.snd_lo = wblock;
					ib.data.setSampleLow(wblock.wave_info);
				}
				if(ib.off_snd_med > 0){
					int val = ib.off_snd_med;
					WaveInfoBlock wblock = bank.sound_samples.get(val);
					if(wblock == null){
						wblock = new WaveInfoBlock();
						wblock.addr = val;
						if(bank.samples_by_uid) wblock.wave_info.setUID(val);
						else wblock.wave_info.setWaveOffset(val);
						bank.sound_samples.put(val, wblock);
					}
					ib.snd_med = wblock;
					ib.data.setSampleMiddle(wblock.wave_info);
				}
				if(ib.off_snd_hi > 0){
					int val = ib.off_snd_hi;
					WaveInfoBlock wblock = bank.sound_samples.get(val);
					if(wblock == null){
						wblock = new WaveInfoBlock();
						wblock.addr = val;
						if(bank.samples_by_uid) wblock.wave_info.setUID(val);
						else wblock.wave_info.setWaveOffset(val);
						bank.sound_samples.put(val, wblock);
					}
					ib.snd_hi = wblock;
					ib.data.setSampleHigh(wblock.wave_info);
				}
			}
		}
		
		for(int i = 0; i < STDRANGE_SIZE; i++){
			if(bank.perc_slots[i] == null) continue;
			if(i >= bank.pcount) bank.pcount = i;
			PercBlock pblock = bank.perc_slots[i];
			if(pblock.off_env > 0){
				pblock.envelope = envs[pblock.off_env];
				if(pblock.envelope != null) pblock.data.setEnvelope(pblock.envelope.data);
			}
			if(pblock.off_snd > 0){
				int val = pblock.off_snd;
				WaveInfoBlock wblock = bank.sound_samples.get(val);
				if(wblock == null){
					wblock = new WaveInfoBlock();
					wblock.addr = val;
					if(bank.samples_by_uid) wblock.wave_info.setUID(val);
					else wblock.wave_info.setWaveOffset(val);
					bank.sound_samples.put(val, wblock);
				}
				pblock.sample = wblock;
				if(pblock.data.getSample() == null){
					pblock.data.setSample(wblock.wave_info);
				}
			}
		}

		return bank;
	}
	
	public int readUWSD(FileBuffer data) throws UnsupportedFileTypeException{
		if(data == null) return 0;
		data.setEndian(true);
		
		//Read header block
		long mpos = data.findString(0, 0x4, UWSD_MAGIC);
		if(mpos != 0) throw new FileBuffer.UnsupportedFileTypeException("Z64Bank.readUWSD -- Expecting UWSD magic number at offset 0!");
		//Don't need to read the rest of the chunk til version update
		data.setCurrentPosition(0x10L);
		long fsize = data.getFileSize();
		
		int sfx_count = 0;
		while(data.getCurrentPosition() < fsize){
			String cmagic = data.getASCII_string(data.getCurrentPosition(), 4);
			data.skipBytes(4L);
			int csz = data.nextInt();
			if(cmagic.equals("DATA")){
				sfx_count = data.nextInt();
				if(sfx_count < 1) return 0;
				xcount = sfx_count;
				sfx_slots = new SFXBlock[sfx_count];
				for(int i = 0; i < sfx_count; i++){
					int loc = (int)data.getCurrentPosition();
					int waveref = data.nextInt();
					float tune = Float.intBitsToFloat(data.nextInt());
					if(waveref == 0) continue;
					
					SFXBlock block = new SFXBlock();
					sfx_slots[i] = block;
					sfx_pool.put(loc, block.data);
					block.addr = loc;
					block.off_snd = waveref;
					block.data.id = loc;
					block.data.setTuning(tune);
					
					//Link sample
					WaveInfoBlock wblock = sound_samples.get(waveref);
					int val = block.off_snd;
					if(wblock == null){
						wblock = new WaveInfoBlock();
						wblock.addr = val;
						if(samples_by_uid) wblock.wave_info.setUID(val);
						else wblock.wave_info.setWaveOffset(val);
						sound_samples.put(val, wblock);
					}
					block.sample = wblock;
					if(wblock != null) block.data.setSample(wblock.wave_info);
				}
			}
			else data.skipBytes(csz);
		}
		
		return sfx_count;
	}
	
	/*----- Serialization -----*/
	
	private static int padTo16(int offset){
		int mod16 = offset & 0xf;
		if(mod16 == 0) return offset;
		return offset + (16 - mod16);
	}
	
	public void serializeTo(FileBuffer target){
		if(target == null) return;
		updateAllReferences();
		
		//Figure out inst table size.
		int itbl_sz = 8 + (icount << 2);
		int current_pos = padTo16(itbl_sz);
		int[] itbl = new int[icount+2];
		
		//Add sounds, books, and loops.
		int wcount = sound_samples.size();
		LinkedList<BankBlock> snd_blocks = new LinkedList<BankBlock>();
		List<Integer> orderlist = new ArrayList<Integer>(wcount+1);
		orderlist.addAll(sound_samples.keySet());
		Collections.sort(orderlist);
		for(Integer k : orderlist){
			WaveInfoBlock b = sound_samples.get(k);
			if(!b.flag){
				snd_blocks.add(b);
				b.flag = true;
				b.addr = current_pos;
				current_pos = padTo16(current_pos+b.serialSize());
				
				if(b.pred != null){
					if(!b.pred.flag){
						snd_blocks.add(b.pred);
						b.pred.flag = true;
						b.pred.addr = current_pos;
						current_pos = padTo16(current_pos+b.pred.serialSize());
					}
				}
				
				if(b.loop != null){
					if(!b.loop.flag){
						snd_blocks.add(b.loop);
						b.loop.flag = true;
						b.loop.addr = current_pos;
						current_pos = padTo16(current_pos+b.loop.serialSize());
					}
				}
			}
		}
		
		//Add envelopes (w/ instruments, perc, and sfx)
		LinkedList<EnvelopeBlock> env_blocks = new LinkedList<EnvelopeBlock>();
		LinkedList<BankBlock> inst_blocks = new LinkedList<BankBlock>();
		LinkedList<BankBlock> drum_blocks = new LinkedList<BankBlock>();
		
		//Sort instruments :(
		Map<Integer, InstBlock> iblock_map = new HashMap<Integer, InstBlock>();
		for(int i = 0; i < icount; i++){
			if(inst_presets[i] == null) continue;
			int key = inst_presets[i].data.id;
			if(!iblock_map.containsKey(key)) iblock_map.put(key, inst_presets[i]);
		}
		List<Integer> ilist = new LinkedList<Integer>();
		ilist.addAll(iblock_map.keySet());
		Collections.sort(ilist);
		
		for(Integer k : ilist){
			InstBlock iblock = iblock_map.get(k);
			if(iblock.data == null) continue;
			if(!iblock.flag){
				inst_blocks.add(iblock);
				iblock.flag = true;
				
				if(iblock.envelope != null){
					if(!iblock.envelope.flag){
						EnvelopeBlock match = null;
						for(EnvelopeBlock other : env_blocks){
							if(iblock.envelope.data.envEquals(other.data)){
								match = other;
								break;
							}
						}
						if(match == null){
							env_blocks.add(iblock.envelope);
							iblock.envelope.flag = true;
							iblock.envelope.addr = current_pos;
							current_pos = padTo16(current_pos+iblock.envelope.serialSize());	
						}
						else{
							//Switch it out.
							iblock.envelope = match;
						}
					}
				}
			}
		}
		ilist.clear();
		iblock_map.clear();
		
		for(int i = 0; i < pcount; i++){
			PercBlock dblock = perc_slots[i];
			if(dblock == null || dblock.data == null) continue;
			if(!dblock.flag){
				drum_blocks.add(dblock);
				dblock.flag = true;
				
				if(dblock.envelope != null){
					if(!dblock.envelope.flag){
						EnvelopeBlock match = null;
						for(EnvelopeBlock other : env_blocks){
							if(dblock.envelope.data.envEquals(other.data)){
								match = other;
								break;
							}
						}
						if(match == null){
							env_blocks.add(dblock.envelope);
							dblock.envelope.flag = true;
							dblock.envelope.addr = current_pos;
							current_pos = padTo16(current_pos+dblock.envelope.serialSize());	
						}
						else{
							//Switch it out.
							dblock.envelope = match;
						}
					}
				}
			}
		}
		
		//for(SFXBlock xblock : sound_effects){}
		
		//Assign inst addresses & update inst table
		int ii = 2;
		for(BankBlock block : inst_blocks){
			block.addr = current_pos;
			current_pos = padTo16(current_pos+block.serialSize());
		}
		for(int i = 0; i < icount; i++){
			InstBlock iblock = inst_presets[i];
			if(iblock == null || iblock.data == null) itbl[ii++] = 0;
			else{
				itbl[ii++] = iblock.addr;
			}
		}
		
		//Assign perc addresses & update perc (and inst) table
		PercOffsetTableBlock ptbl = new PercOffsetTableBlock();
		for(BankBlock block : drum_blocks){
			block.addr = current_pos;
			current_pos = padTo16(current_pos+block.serialSize());
		}
		if(pcount > 0) {itbl[0] = ptbl.addr = current_pos;} //Drum table offset.
		else itbl[0] = 0;
		for(int i = 0; i < pcount; i++){
			PercBlock dblock = perc_slots[i];
			if(dblock == null || dblock.data == null) ptbl.offsets.add(0);
			else{
				ptbl.offsets.add(dblock.addr);
			}
		}
		current_pos = padTo16(current_pos+ptbl.serialSize());
		
		//Determine SFX table address and update inst table
		if(xcount > 0) itbl[1] = current_pos;
		else itbl[1] = 0;
		
		//Write inst table.
		for(int i = 0; i < itbl.length; i++){
			target.addToFile(itbl[i]);
		}
		while((target.getFileSize() & 0xf) != 0) target.addToFile(FileBuffer.ZERO_BYTE);
		
		//Write elements (& clear added flags)
		for(BankBlock block : snd_blocks){
			block.serializeTo(target, samples_by_uid);
			block.flag = false;
			while((target.getFileSize() & 0xf) != 0) target.addToFile(FileBuffer.ZERO_BYTE);
		}
		
		for(BankBlock block : env_blocks){
			block.serializeTo(target, samples_by_uid);
			block.flag = false;
			while((target.getFileSize() & 0xf) != 0) target.addToFile(FileBuffer.ZERO_BYTE);
		}
		
		for(BankBlock block : inst_blocks){
			block.serializeTo(target, samples_by_uid);
			block.flag = false;
			while((target.getFileSize() & 0xf) != 0) target.addToFile(FileBuffer.ZERO_BYTE);
		}
		
		for(BankBlock block : drum_blocks){
			block.serializeTo(target, samples_by_uid);
			block.flag = false;
			while((target.getFileSize() & 0xf) != 0) target.addToFile(FileBuffer.ZERO_BYTE);
		}
		
		ptbl.serializeTo(target, samples_by_uid); ptbl.flag = false;
		while((target.getFileSize() & 0xf) != 0) target.addToFile(FileBuffer.ZERO_BYTE);
		
		for(int i = 0; i < xcount; i++){
			SFXBlock xblock = sfx_slots[i];
			if(xblock == null || xblock.data == null) target.addToFile(0L);
			else{
				xblock.serializeTo(target, samples_by_uid); xblock.flag = false;
			}
		}
		while((target.getFileSize() & 0xf) != 0) target.addToFile(FileBuffer.ZERO_BYTE);
		
	}
	
	private void serializeDrumUBNK(Z64Drum drum, int min, int max, FileBuffer target){
		//System.err.println("drum is null: " + (drum == null));
		//System.err.println("target is null: " + (target == null));
		target.addToFile(drum.getDecay());
		target.addToFile(drum.getPan());
		target.addToFile((byte)min);
		target.addToFile((byte)max);
		Z64WaveInfo winfo = drum.getSample();
		if(winfo != null){
			if(samples_by_uid) target.addToFile(winfo.getUID());
			else target.addToFile(winfo.getWaveOffset());
			target.addToFile(Float.floatToRawIntBits(drum.getTuning()));
		}
		else target.addToFile(-1L);
		
		Z64Envelope env = drum.getEnvelope();
		if(env != null){
			target.addToFile(env.id);
		}
		else target.addToFile(-1);
	}
	
	public void writeUFormat(String pathstem) throws IOException{
		
		Z64Envelope e0 = null;
		Z64WaveInfo wi0 = null;
		
		//Do the UBNK -------------
		final int HEADER_SIZE = 16;
		FileBuffer header = new FileBuffer(HEADER_SIZE, true);
		header.printASCIIToFile(UBNK_MAGIC);
		header.addToFile((short)0xFEFF);
		header.addToFile((byte)UBNK_VERSION_MAJOR);
		header.addToFile((byte)UBNK_VERSION_MINOR);
		//Stop here until we calculate file size
		int file_sz = 0;
		int chunk_count = 0;
		
		//META
		final int META_SIZE = 24;
		FileBuffer ch_meta = new FileBuffer(META_SIZE, true);
		ch_meta.printASCIIToFile("META");
		ch_meta.addToFile(META_SIZE-8);
		ch_meta.addToFile(uid);
		ch_meta.addToFile((byte)medium);
		ch_meta.addToFile((byte)cachePolicy);
		int ival = samples_by_uid?1:0;
		ch_meta.addToFile((byte)ival);
		if(warc1 >= 0){
			if(warc2 >= 0){
				ch_meta.addToFile((byte)2);
				ch_meta.addToFile((byte)warc1);
				ch_meta.addToFile((byte)warc2);
				ch_meta.addToFile((short)0);
			}
			else {
				ch_meta.addToFile((byte)1);
				ch_meta.addToFile((byte)warc1);
				ch_meta.add24ToFile(0);
			}
		}
		else ch_meta.addToFile(FileBuffer.ZERO_BYTE);
		file_sz += ch_meta.getFileSize();
		chunk_count++;
		
		//Temp storage
		List<String> labels = new LinkedList<String>();
		List<Z64Envelope> envs = new LinkedList<Z64Envelope>();
		int ecount = 0;
		
		for(Z64Instrument item : inst_pool.values()){
			e0 = item.getEnvelope();
			if(e0 != null) e0.id = -1;
		}
		for(Z64Drum item : perc_pool.values()){
			e0 = item.getEnvelope();
			if(e0 != null) e0.id = -1;
		}
		
		//INST
		int icount_unique = inst_pool.size();
		FileBuffer ch_inst = new FileBuffer(256 + 12 + (icount*32), true);
		ch_inst.printASCIIToFile("INST");
		//Do a whole bunch of placeholders
		ch_inst.addToFile(0);
		ch_inst.addToFile(icount_unique);
		for(int i = 0; i < 256; i++) ch_inst.addToFile(FileBuffer.ZERO_BYTE);
		List<Integer> ilist = new ArrayList<Integer>(icount_unique+1);
		ilist.addAll(inst_pool.keySet());
		int pos = (int)ch_inst.getFileSize();
		Collections.sort(ilist);
		//System.err.println("inst_pool size before: " + inst_pool.size());
		Map<Integer, Z64Instrument> old_pool = inst_pool;
		inst_pool = new HashMap<Integer, Z64Instrument>();
		for(Integer k : ilist){
			Z64Instrument inst = old_pool.get(k);
			
			ch_inst.addToFile(FileBuffer.ZERO_BYTE);
			ch_inst.addToFile(inst.getLowRangeTop());
			ch_inst.addToFile(inst.getHighRangeBottom());
			ch_inst.addToFile(inst.getDecay());
			e0 = inst.getEnvelope();
			if(e0 != null){
				int e = 0;
				for(Z64Envelope env : envs){
					if(e0.envEquals(env)) break;
					e++;
				}
				if(e >= ecount){
					//New envelope.
					envs.add(e0); 
					e0.id = ecount++;
					ch_inst.addToFile(e0.id);
				}
				else{
					ch_inst.addToFile(e);
				}
			}
			else ch_inst.addToFile(-1);
			
			wi0 = inst.getSampleLow();
			if(wi0 != null){
				if(this.samples_by_uid) ch_inst.addToFile(wi0.getUID());
				else ch_inst.addToFile(wi0.getWaveOffset());
				ch_inst.addToFile(Float.floatToRawIntBits(inst.getTuningLow()));
			}
			else ch_inst.addToFile(-1L);
			
			wi0 = inst.getSampleMiddle();
			if(wi0 != null){
				if(this.samples_by_uid) ch_inst.addToFile(wi0.getUID());
				else ch_inst.addToFile(wi0.getWaveOffset());
				ch_inst.addToFile(Float.floatToRawIntBits(inst.getTuningMiddle()));
			}
			else ch_inst.addToFile(-1L);
			
			wi0 = inst.getSampleHigh();
			if(wi0 != null){
				if(this.samples_by_uid) ch_inst.addToFile(wi0.getUID());
				else ch_inst.addToFile(wi0.getWaveOffset());
				ch_inst.addToFile(Float.floatToRawIntBits(inst.getTuningHigh()));
			}
			else ch_inst.addToFile(-1L);
			
			labels.add(inst.getName());
			inst.id = pos;
			inst_pool.put(pos, inst);
			pos = (int)ch_inst.getFileSize();
		}
		old_pool.clear();
		//System.err.println("inst_pool size after: " + inst_pool.size());
		
		//	Add chunk size and offset table.
		ch_inst.replaceInt((int)ch_inst.getFileSize() - 8, 4L);
		long cpos = 12L;
		for(int i = 0; i < 126; i++){
			InstBlock iblock = inst_presets[i];
			if(iblock != null){
				ch_inst.replaceShort((short)iblock.data.id, cpos);
				cpos += 2;
			}
		}
		file_sz += ch_inst.getFileSize();
		chunk_count++;
		
		//PERC
		int pcount_unique = perc_pool.size();
		FileBuffer ch_perc = new FileBuffer(12 + (pcount_unique * 16), true);
		ch_perc.printASCIIToFile("PERC");
		ch_perc.addToFile(0);
		ch_perc.addToFile(pcount_unique);
		int r_min = 0;
		Z64Drum last_drum = null;
		for(int i = 0; i < STDRANGE_SIZE; i++){
			PercBlock block = perc_slots[i];
			if(block == null){
				//End of range. Put last block.
				if(last_drum != null){
					serializeDrumUBNK(last_drum, r_min, i-1, ch_perc);
					labels.add(last_drum.getName());
					last_drum = null;
				}
				r_min = i;
			}
			else{
				//Figure out envelope...
				if(block.data != null){
					e0 = block.data.getEnvelope();
					if(e0 != null){
						if(e0.id < 0){
							envs.add(e0); 
							e0.id = ecount++;
						}
					}
				}
				
				if(last_drum == null){
					r_min = i;
					last_drum = block.data;
				}
				else{
					if(!last_drum.drumEquals(block.data)){
						serializeDrumUBNK(last_drum, r_min, i-1, ch_perc);
						labels.add(last_drum.getName());
						r_min = i;
						last_drum = block.data;
					}
				}
			}
		}
		//	Last block
		if(last_drum != null){
			serializeDrumUBNK(last_drum, r_min, STDRANGE_SIZE-1, ch_perc);
			labels.add(last_drum.getName());
		}
		//	Replace size value
		ch_perc.replaceInt((int)ch_perc.getFileSize() - 8, 4L);
		file_sz += ch_perc.getFileSize();
		chunk_count++;
		
		//ENVL
		FileBuffer ch_envl = new FileBuffer(12 + (ecount * 48), true);
		ch_envl.printASCIIToFile("ENVL");
		ch_envl.addToFile(0);
		ch_envl.addToFile((short)ecount);
		ival = ecount << 1;
		for(int i = 0; i < ival; i++) ch_envl.addToFile(FileBuffer.ZERO_BYTE);
		long tpos = 8L;
		for(Z64Envelope env : envs){
			pos = (int)ch_envl.getFileSize();
			for(short[] event : env.events){
				ch_envl.addToFile(event[0]);
				ch_envl.addToFile(event[1]);
			}
			ch_envl.addToFile((short)env.terminator);
			ch_envl.addToFile((short)0);
			ch_envl.replaceShort((short)pos, tpos);
			tpos+=2;
		}
		ch_envl.replaceInt((int)ch_envl.getFileSize() - 8, 4L);
		if(ecount > 0){
			file_sz += ch_envl.getFileSize();
			chunk_count++;
		}
		
		//LABL
		FileBuffer ch_labl = null;
		if(!labels.isEmpty()){
			int estsz = 0;
			for(String lbl : labels){
				estsz += lbl.length() + 3;
			}
			ch_labl = new FileBuffer(8 + estsz, true);
			ch_labl.printASCIIToFile("LABL");
			ch_labl.addToFile(0);	
			for(String lbl : labels){
				ch_labl.addVariableLengthString("UTF8", lbl, BinFieldSize.WORD, 2);
			}
			while((ch_labl.getFileSize() % 4) != 0) ch_labl.addToFile(FileBuffer.ZERO_BYTE);
			ch_labl.replaceInt((int)ch_labl.getFileSize() - 8, 4L);
			file_sz += ch_labl.getFileSize();
			chunk_count++;
		}
		
		//Finish header
		header.addToFile(file_sz + HEADER_SIZE);
		header.addToFile((short)(HEADER_SIZE));
		header.addToFile((short)chunk_count);
		
		//Write to file
		BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(pathstem + ".bubnk"));
		header.writeToStream(bos);
		ch_meta.writeToStream(bos);
		if(ecount > 0) ch_envl.writeToStream(bos);
		ch_inst.writeToStream(bos);
		ch_perc.writeToStream(bos);
		if(ch_labl != null) ch_labl.writeToStream(bos);
		bos.close();
		
		//Do the UWSD (If needed) -------------
		if(xcount > 0){
			header = new FileBuffer(HEADER_SIZE, true);
			header.printASCIIToFile(UWSD_MAGIC);
			header.addToFile((short)0xFEFF);
			header.addToFile((byte)UWSD_VERSION_MAJOR);
			header.addToFile((byte)UWSD_VERSION_MINOR);
			file_sz = 0;
			
			ch_meta = new FileBuffer(META_SIZE, true);
			ch_meta.printASCIIToFile("META");
			ch_meta.addToFile(8);
			ch_meta.addToFile(wsd_ref);
			ival = samples_by_uid?1:0;
			ch_meta.addToFile((byte)ival);
			if(warc1 >= 0){
				if(warc2 >= 0){
					ch_meta.addToFile((byte)2);
					ch_meta.addToFile((byte)warc1);
					ch_meta.addToFile((byte)warc2);
				}
				else {
					ch_meta.addToFile((byte)1);
					ch_meta.addToFile((byte)warc1);
					ch_meta.addToFile(FileBuffer.ZERO_BYTE);
				}
			}
			else ch_meta.add24ToFile(0);
			file_sz += ch_meta.getFileSize();
			
			//Write DATA
			FileBuffer ch_data = new FileBuffer(12 + (xcount << 3), true);
			ch_data.printASCIIToFile("DATA");
			ch_data.addToFile(0);
			ch_data.addToFile(xcount);
			for(int i = 0; i < xcount; i++){
				SFXBlock block = sfx_slots[i];
				if(block == null) ch_data.addToFile(0L);
				else{
					wi0 = block.data.getSample();
					if(wi0 == null) ch_data.addToFile(0L);
					else{
						if(this.samples_by_uid) ch_data.addToFile(wi0.getUID());
						else ch_data.addToFile(wi0.getWaveOffset());
						ch_data.addToFile(Float.floatToRawIntBits(block.data.getTuning()));
					}
				}
			}
			ch_data.replaceInt((int)ch_data.getFileSize() - 8, 4L);
			file_sz += ch_data.getFileSize();
			
			//Output
			header.addToFile(file_sz + HEADER_SIZE);
			header.addToFile((short)HEADER_SIZE);
			header.addToFile((short)2);
			
			bos = new BufferedOutputStream(new FileOutputStream(pathstem + ".buwsd"));
			header.writeToStream(bos);
			ch_meta.writeToStream(bos);
			ch_data.writeToStream(bos);
			bos.close();
		}
	}
	
	/*----- Getters -----*/
	
	public int getMedium(){return medium;}
	public int getCachePolicy(){return cachePolicy;}
	public int getPrimaryWaveArchiveIndex(){return warc1;}
	public int getSecondaryWaveArchiveIndex(){return warc2;}
	public int getUID(){return uid;}
	public int getUWSDLinkID(){return this.wsd_ref;}
	
	public int getInstCount(){return icount;}
	public int getPercCount(){return pcount;}
	public int getSFXCount(){return xcount;}
	
	public boolean samplesOrderedByUID(){return this.samples_by_uid;}
	
	public List<Z64WaveInfo> getAllWaveInfoBlocks(){
		int wcount = sound_samples.size();
		List<Z64WaveInfo> list = new ArrayList<Z64WaveInfo>(wcount+1);
		List<Integer> orderlist = new ArrayList<Integer>(wcount+1);
		orderlist.addAll(sound_samples.keySet());
		Collections.sort(orderlist);
		for(Integer k : orderlist){
			list.add(sound_samples.get(k).getWaveInfo());
		}
		return list;
	}
	
	public Collection<Z64Envelope> getAllEnvelopes(){
		//It is assumed the envelopes are already merged.
		//	Merged in UBNK, native format already
		//	Make it so merges when add new instrument
		
		List<Z64Envelope> list = new LinkedList<Z64Envelope>();
		Z64Envelope e = null;
		boolean match = false;
		
		for(Z64Instrument inst : inst_pool.values()){
			e = inst.getEnvelope();
			match = false;
			if(e != null){
				for(Z64Envelope other : list){
					if(other.envEquals(e)){
						match = true;
						break;
					}
				}
				if(!match) list.add(e);
			}
		}
		
		for(Z64Drum drum : perc_pool.values()){
			e = drum.getEnvelope();
			match = false;
			if(e != null){
				for(Z64Envelope other : list){
					if(other.envEquals(e)){
						match = true;
						break;
					}
				}
				if(!match) list.add(e);
			}
		}
		
		return list;
	}
	
	public Collection<Integer> getAllWaveUIDs(){
		Set<Integer> col = new TreeSet<Integer>();
		col.addAll(sound_samples.keySet());
		return col;
	}
	
	public Collection<Z64Instrument> getAllInstruments(){
		List<Z64Instrument> list = new ArrayList<Z64Instrument>(inst_pool.size()+1);
		list.addAll(inst_pool.values());
		return list;
	}
	
	public Collection<Z64Drum> getAllDrums(){
		List<Z64Drum> list = new ArrayList<Z64Drum>(perc_pool.size()+1);
		list.addAll(perc_pool.values());
		return list;
	}
	
	public Z64Instrument[] getInstrumentPresets(){
		Z64Instrument[] presets = new Z64Instrument[126];
		for(int i = 0; i < inst_presets.length; i++){
			InstBlock block = inst_presets[i];
			if(block != null){
				presets[i] = block.data;
			}
		}
		return presets;
	}
	
	public Z64Drum[] getPercussionSet(){
		Z64Drum[] drums = new Z64Drum[64];
		for(int i = 0; i < 64; i++){
			PercBlock block = perc_slots[i];
			if(block != null){
				drums[i] = block.data;
			}
		}
		return drums;
	}
	
	public Z64SoundEffect[] getSFXSet(){
		if(sfx_slots == null) return null;
		Z64SoundEffect[] out = new Z64SoundEffect[sfx_slots.length];
		for(int i = 0; i < sfx_slots.length; i++){
			if(sfx_slots[i] != null){
				out[i] = sfx_slots[i].data;
			}
		}
		return out;
	}
	
	public int usedInstSlots(){
		if(inst_presets == null) return 0;
		int c = 0;
		for(int i = 0; i < inst_presets.length; i++){
			if(inst_presets[i] != null) c = i+1;
		}
		return c;
	}
	
	public int usedPercSlots(){
		if(perc_slots == null) return 0;
		int c = 0;
		for(int i = 0; i < perc_slots.length; i++){
			if(perc_slots[i] != null) c = i+1;
		}
		return c;
	}
	
	public int usedSFXSlots(){
		if(sfx_slots == null) return 0;
		int c = 0;
		for(int i = 0; i < sfx_slots.length; i++){
			if(sfx_slots[i] != null) c = i+1;
		}
		return c;
	}
	
	/*----- Setters -----*/
	
	public void setUID(int val){uid = val;}
	
	public void setSamplesOrderedByUID(boolean b){
		boolean prev_setting = samples_by_uid;
		samples_by_uid = b;
		
		//Reorder
		if(samples_by_uid && !prev_setting){
			if(sound_samples.isEmpty()) return;
			List<WaveInfoBlock> list = new ArrayList<WaveInfoBlock>(sound_samples.size());
			list.addAll(sound_samples.values());
			sound_samples.clear();
			for(WaveInfoBlock block : list){
				sound_samples.put(block.wave_info.getUID(), block);
			}
		}
		else if(prev_setting && !samples_by_uid){
			if(sound_samples.isEmpty()) return;
			List<WaveInfoBlock> list = new ArrayList<WaveInfoBlock>(sound_samples.size());
			list.addAll(sound_samples.values());
			sound_samples.clear();
			for(WaveInfoBlock block : list){
				sound_samples.put(block.wave_info.getWaveOffset(), block);
			}
		}
	}
	
	public void setMedium(int val){this.medium = val;}
	public void setCachePolicy(int val){this.cachePolicy = val;}
	public void setPrimaryWaveArchiveIndex(int val){warc1 = val;}
	public void setSecondaryWaveArchiveIndex(int val){warc2 = val;}
	
	public void setInstrument(Z64Instrument inst, int preset_index){
		if(inst == null) return;
		//See if instrument is already in this bank...
		Z64Instrument match = findInstrument(inst);
		if(match != null){
			//We can just reuse that one!
			for(int i = 0; i < inst_presets.length; i++){
				if(i == preset_index) continue;
				if(inst_presets[i] == null) continue;
				if(inst_presets[i].data == match){
					inst_presets[preset_index] = inst_presets[i];
					return;
				}
			}
		}
		//Need new instblock
		if(match == null){
			inst_pool.put(inst.id, inst);
			match = inst;
		}
		InstBlock block = new InstBlock(match);
		//if(inst_presets[preset_index] == null) icount++;
		inst_presets[preset_index] = block;
		
		//Find/create the wave info blocks
		Z64WaveInfo winfo = match.getSampleMiddle();
		WaveInfoBlock wblock = null;
		if(winfo != null){
			wblock = findSoundSample(winfo);
			if(wblock == null){
				wblock = new WaveInfoBlock(winfo);
				sound_samples.put(winfo.getUID(), wblock);
			}
			block.snd_med = wblock;
		}
		winfo = match.getSampleLow();
		if(winfo != null){
			wblock = findSoundSample(winfo);
			if(wblock == null){
				wblock = new WaveInfoBlock(winfo);
				sound_samples.put(winfo.getUID(), wblock);
			}
			block.snd_lo = wblock;
		}
		winfo = match.getSampleHigh();
		if(winfo != null){
			wblock = findSoundSample(winfo);
			if(wblock == null){
				wblock = new WaveInfoBlock(winfo);
				sound_samples.put(winfo.getUID(), wblock);
			}
			block.snd_hi = wblock;
		}

		EnvelopeBlock eblock = new EnvelopeBlock(match.getEnvelope());
		block.envelope = eblock;
		
		updateICount();
	}
	
	public void setDrum(Z64Drum drum, int min_slot, int max_slot){
		if(drum == null) return;
		Z64Drum match = findDrum(drum);
		if(match == null){
			perc_pool.put(drum.id, drum);
			match = drum;
		}
		
		//Get sound sample
		Z64WaveInfo winfo = match.getSample();
		WaveInfoBlock wblock = findSoundSample(winfo);
		if(wblock == null){
			wblock = new WaveInfoBlock(winfo);
			sound_samples.put(winfo.getUID(), wblock);
		}
		
		EnvelopeBlock eblock = new EnvelopeBlock(match.getEnvelope());
		
		min_slot = Math.max(min_slot, 0);
		max_slot = Math.min(max_slot, 63);
		for(int j = min_slot; j <= max_slot; j++){
			//if(perc_slots[j] == null) pcount++;
			perc_slots[j] = new PercBlock(match, j);
			perc_slots[j].sample = wblock;
			perc_slots[j].updateLocalTuning();
			perc_slots[j].envelope = eblock;
		}
		
		updatePCount();
	}
	
	public void setSoundEffect(Z64SoundEffect sfx, int slot){
		if(sfx == null) return;
		if(sfx_slots == null) return;
		if(slot < 0) return;
		if(slot >= sfx_slots.length) return;
		
		Z64SoundEffect match = findSoundEffect(sfx);
		if(match == null){
			sfx_pool.put(sfx.id, sfx);
			match = sfx;
		}
		
		Z64WaveInfo winfo = match.getSample();
		WaveInfoBlock wblock = findSoundSample(winfo);
		if(wblock == null){
			wblock = new WaveInfoBlock(winfo);
			sound_samples.put(winfo.getUID(), wblock);
		}
		
		//if(sfx_slots[slot] == null) xcount++;
		sfx_slots[slot] = new SFXBlock(match);
		sfx_slots[slot].sample = wblock;
		updateXCount();
	}
	
	/*----- Find/Match -----*/
	
	private void updateICount(){
		for(int i = inst_presets.length-1; i >= 0; i--){
			if(inst_presets[i] != null){
				icount = i+1;
				return;
			}
		}
		icount = 0;
	}
	
	private void updatePCount(){
		for(int i = perc_slots.length-1; i >= 0; i--){
			if(perc_slots[i] != null){
				pcount = i+1;
				return;
			}
		}
		pcount = 0;
	}
	
	private void updateXCount(){
		if(this.sfx_slots == null){
			xcount = 0;
			return;
		}
		for(int i = sfx_slots.length-1; i >= 0; i--){
			if(sfx_slots[i] != null){
				xcount = i+1;
				return;
			}
		}
		xcount = 0;
	}
	
	protected Z64Instrument findInstrument(Z64Instrument inst){
		if(inst == null) return null;
		for(Z64Instrument other : inst_pool.values()){
			if(inst.instEquals(other)){
				return other;
			}
		}
		return null;
	}
	
	protected Z64Drum findDrum(Z64Drum drum){
		if(drum == null) return null;
		for(Z64Drum other : perc_pool.values()){
			if(drum.drumEquals(other)){
				return other;
			}
		}
		return null;
	}
	
	protected Z64SoundEffect findSoundEffect(Z64SoundEffect sfx){
		if(sfx == null) return null;
		for(Z64SoundEffect other : sfx_pool.values()){
			if(sfx.sfxEquals(other)){
				return other;
			}
		}
		return null;
	}
	
	protected WaveInfoBlock findSoundSample(Z64WaveInfo wave){
		if(wave == null) return null;
		if(this.samples_by_uid){
			return sound_samples.get(wave.getUID());
		}
		for(WaveInfoBlock block : sound_samples.values()){
			if(wave.wavesEqual(block.wave_info, false)){
				return block;
			}
		}
		return null;
	}
	
	/*----- Linking -----*/
	
	private void updateAllReferences(){
		Set<Integer> iset = new TreeSet<Integer>();
		//System.err.println("Inst count before: " + inst_pool.size());
		for(int i = 0; i < icount; i++){
			InstBlock block = inst_presets[i];
			if(block == null) continue;
			
			if(block.snd_lo != null) block.data.setSampleLow(block.snd_lo.getWaveInfo());
			if(block.snd_med != null) block.data.setSampleMiddle(block.snd_med.getWaveInfo());
			if(block.snd_hi != null) block.data.setSampleHigh(block.snd_hi.getWaveInfo());
			if(block.envelope != null) block.data.setEnvelope(block.envelope.data);
			
			//Look for merges?
			for(int j = i-1; j >= 0; j--){
				InstBlock other = inst_presets[j];
				if(block == other) continue;
				if(other == null) continue;
				if(iset.contains(other.data.id)) continue;
				if(block.data.instEquals(other.data)){
					//System.err.println("Z64Bank DEBUG: Inst merge detected -- preset " + i + " to " + j);
					//System.err.println("Removing 0x" + Integer.toHexString(block.data.id) + " replacing with 0x" + Integer.toHexString(other.data.id));
					inst_pool.remove(block.data.id);
					inst_presets[i] = other;
					break;
				}
				else iset.add(other.data.id);
			}
			iset.clear();
		}
		//System.err.println("Inst count after: " + inst_pool.size());
		
		for(int i = 0; i < pcount; i++){
			PercBlock block = perc_slots[i];
			if(block == null) continue;
			
			if(block.sample != null) block.data.setSample(block.sample.wave_info);
			if(block.envelope != null) block.data.setEnvelope(block.envelope.data);
			
			for(int j = i-1; j >= 0; j--){
				PercBlock other = perc_slots[j];
				if(other == null) continue;
				if(block.data == other.data) continue;
				if(iset.contains(other.data.id)) continue;
				if(block.data.drumEquals(other.data)){
					//System.err.println("Z64Bank DEBUG: Drum merge detected -- slot " + i + " to " + j);
					perc_pool.remove(block.data.id);
					block.data = other.data;
					block.updateLocalTuning();
					break;
				}
				else iset.add(other.data.id);
			}
			iset.clear();
		}
		if(sfx_slots != null){
			for(int i = 0; i < xcount; i++){
				SFXBlock block = sfx_slots[i];
				if(block == null) continue;
				if(block.data == null) continue;
				if(block.sample != null) block.data.setSample(block.sample.wave_info);
			}	
		}
		
		for(WaveInfoBlock wb : sound_samples.values()){
			wb.getWaveInfo(); //Calling this links loop and preds properly
		}
	}
	
	/*----- Multibank -----*/
	
	private void mergeSamples(Z64Bank other){
		//Changes the Z64WaveInfo refs in these blocks to match "equal" ones in other bank's
		if(other == null) return;
		List<Z64WaveInfo> otherlist = other.getAllWaveInfoBlocks();
		ArrayList<WaveInfoBlock> mylist = new ArrayList<WaveInfoBlock>(sound_samples.size()+1);
		mylist.addAll(sound_samples.values());
		
		for(Z64WaveInfo owave : otherlist){
			for(WaveInfoBlock block : mylist){
				if(block.wave_info == null) continue; //Shouldn't happen, but just in case.
				if(owave.wavesEqual(block.wave_info, true)){
					block.wave_info = owave;
				}
			}
		}
		updateAllReferences();
	}
	
	private void mergeInstruments(Z64Bank other){
		if(other == null) return;
		
		//Inst
		for(int i = 0; i < icount; i++){
			InstBlock tinst = this.inst_presets[i];
			if(tinst.data == null) continue;
			for(int j = 0; j < other.icount; j++){
				InstBlock oinst = other.inst_presets[j];
				if(oinst.data == null) continue;
				if(tinst.data.instEquals(oinst.data)){
					//Replace oinst.data with tinst.data
					other.inst_pool.remove(oinst.data.id);
					oinst.data = tinst.data;
					int id = tinst.data.id;
					while(other.inst_pool.containsKey(tinst.data.id)){
						//Need to reassign ID
						tinst.data.id++;
					}
					if(tinst.data.id != id){
						inst_pool.remove(id);
						inst_pool.put(tinst.data.id, tinst.data);
					}
					other.inst_pool.put(oinst.data.id, oinst.data);
				}
			}
		}
		
		//Drum
		for(int i = 0; i < pcount; i++){
			PercBlock tinst = perc_slots[i];
			if(tinst == null || tinst.data == null) continue;
			for(int j = 0; j < other.pcount; j++){
				PercBlock oinst = other.perc_slots[j];
				if(oinst == null || oinst.data == null) continue;
				if(tinst.data.drumEquals(oinst.data)){
					//Replace oinst.data with tinst.data
					other.perc_pool.remove(oinst.data.id);
					oinst.data = tinst.data;
					int id = tinst.data.id;
					while(other.perc_pool.containsKey(tinst.data.id)){
						//Need to reassign ID
						tinst.data.id++;
					}
					if(tinst.data.id != id){
						perc_pool.remove(id);
						perc_pool.put(tinst.data.id, tinst.data);
					}
					other.perc_pool.put(oinst.data.id, oinst.data);
				}
			}
		}
		
		//SFX
		for(int i = 0; i < xcount; i++){
			SFXBlock tinst = sfx_slots[i];
			if(tinst.data == null) continue;
			for(int j = 0; j < other.xcount; j++){
				SFXBlock oinst = other.sfx_slots[j];
				if(oinst.data == null) continue;
				if(tinst.data.sfxEquals(oinst.data)){
					//Merge
					other.sfx_pool.remove(oinst.data.id);
					oinst.data = tinst.data;
					int id = tinst.data.id;
					while(other.sfx_pool.containsKey(tinst.data.id)){
						//Need to reassign ID
						tinst.data.id++;
					}
					if(tinst.data.id != id){
						sfx_pool.remove(id);
						sfx_pool.put(tinst.data.id, tinst.data);
					}
					other.sfx_pool.put(oinst.data.id, oinst.data);
				}
			}
		}
	}
	
	public void mergeElementReferences(Z64Bank other){
		mergeSamples(other);
		mergeInstruments(other);
	}

	/*----- Conversion -----*/
	
	private static void calculateTuning(float s_tune, float r_tune, InstRegion region){
		if(s_tune == r_tune){
			region.setUnityKey((byte)MIDDLE_C);
			region.setFineTune((byte)0);
		}
		else{
			double ratio = (double)s_tune/(double)r_tune;
			int cents = SynthMath.freqRatio2Cents(ratio);
			int semis = cents/100;
			region.setUnityKey((byte)(MIDDLE_C + semis));
			region.setFineTune((byte)(cents - (semis*100)));
		}
	}
	
	private static void convertEnvelope(Z64Envelope src, InstRegion target, byte decay){
		//TODO
		//I am clueless atm. Need to study decomp code more :)
	}
	
	private static void convertInst(Z64Instrument src, SimpleInstrument target){
		//Middle region
		int ridx = target.newRegion(src.getSampleMiddle().getName());
		InstRegion reg = target.getRegion(ridx);
		reg.setMinKey(src.getLowRangeTop());
		reg.setMaxKey(src.getHighRangeBottom());
		float s_tune = src.getSampleMiddle().getTuning();
		float r_tune = src.getTuningMiddle();
		calculateTuning(s_tune, r_tune, reg);
		Z64Envelope env = src.getEnvelope();
		if(env != null){
			convertEnvelope(env, reg, src.getDecay());
		}
		
		//Low region
		if(src.getSampleLow() != null){
			ridx = target.newRegion(src.getSampleLow().getName());
			reg = target.getRegion(ridx);
			reg.setMinKey((byte)0);
			reg.setMaxKey((byte)(src.getLowRangeTop() - 1));
			s_tune = src.getSampleLow().getTuning();
			r_tune = src.getTuningLow();
			calculateTuning(s_tune, r_tune, reg);
			if(env != null){
				convertEnvelope(env, reg, src.getDecay());
			}
		}
		
		//High region
		if(src.getSampleHigh() != null){
			ridx = target.newRegion(src.getSampleHigh().getName());
			reg = target.getRegion(ridx);
			reg.setMinKey((byte)(src.getHighRangeBottom() + 1));
			reg.setMaxKey((byte)127);
			s_tune = src.getSampleHigh().getTuning();
			r_tune = src.getTuningHigh();
			calculateTuning(s_tune, r_tune, reg);
			if(env != null){
				convertEnvelope(env, reg, src.getDecay());
			}
		}
	}
	
	private static void convertPerc(Z64Drum src, SimpleInstrument target){
		int ridx = target.newRegion(src.getSample().getName());
		InstRegion reg = target.getRegion(ridx);
		float s_tune = src.getSample().getTuning();
		float r_tune = src.getTuning();
		calculateTuning(s_tune, r_tune, reg);
		Z64Envelope env = src.getEnvelope();
		if(env != null){
			convertEnvelope(env, reg, src.getDecay());
		}
		reg.setPan(src.getPan()); //Wants a short? Don't remember how this scales...
	}
	
	private static void convertSFXRegion(Z64SoundEffect src, InstRegion target, int note){
		float s_tune = src.getSample().getTuning();
		float r_tune = src.getTuning();
		if(s_tune == r_tune){
			target.setUnityKey((byte)note);
			target.setFineTune((byte)0);
		}
		else{
			double ratio = (double)r_tune/(double)s_tune;
			int cents = SynthMath.freqRatio2Cents(ratio);
			int semis = cents/100;
			target.setUnityKey((byte)(note + semis));
			target.setFineTune((byte)(cents - (semis*100)));
		}
	}
	
	public SimpleBank toBank(FileBuffer sound_dat){
		if(sound_dat == null) return null;
		List<Z64Bank> list1 = new ArrayList<Z64Bank>(1);
		List<FileBuffer> list2 = new ArrayList<FileBuffer>(1);
		list1.add(this); list2.add(sound_dat);
		return toFontBank(list1, list2);
	}
	
	public static SimpleBank toFontBank(List<Z64Bank> banks, List<FileBuffer> wavearc_dat){
		if(banks == null || banks.isEmpty()) return null;
		if(wavearc_dat == null || wavearc_dat.isEmpty()) return null;
		int bnk_count = banks.size();
		int war_count = wavearc_dat.size();
		SimpleBank mainBank = new SimpleBank("AnonN64Bank", "0.0.0", "waffleoRaiUtilsGX", bnk_count+1);
		
		Map<String, String> md5map = new HashMap<String, String>();
		//Maps MD5sum to sample name
		Map<String, String> warc_off_map = new HashMap<String, String>();
		//Maps "bank:offset" to sample name
		
		int b = 0;
		int sfinst_count = 0;
		int sfdrum_count = 0;
		final int MAXSLOT = STDRANGE_BOTTOM + STDRANGE_SIZE;
		
		//TODO maybe use flag so don't have to erase names...
		ArrayList<Z64Bank> processed_banks = new ArrayList<Z64Bank>(bnk_count);
		for(Z64Bank bank : banks){
			//First, clear names.
			for(Z64Instrument item : bank.inst_pool.values()){
				if(item != null) item.setName(null);
			}
			for(Z64Drum item : bank.perc_pool.values()){
				if(item != null) item.setName(null);
			}
			for(Z64SoundEffect item : bank.sfx_pool.values()){
				if(item != null) item.setName(null);
			}
		}
		
		try{
		for(Z64Bank bank : banks){
			//Extract waves associated with this bank and MD5 them. (Or check to see if already MD5'd)
			int warc_idx = bank.getPrimaryWaveArchiveIndex();
			if(warc_idx < 0 || warc_idx >= war_count){
				System.err.println("Z64Bank.toFontBank || --WARNING-- Bank " + b + " wave archive index " + warc_idx + " is invalid!");
				b++; continue;
			}
			FileBuffer warc_data = wavearc_dat.get(warc_idx);
			while(warc_data == null && warc_idx > 0){
				warc_idx--;
				warc_data = wavearc_dat.get(warc_idx);
			}
			if(warc_data == null){
				warc_idx = bank.getPrimaryWaveArchiveIndex();
				System.err.println("Z64Bank.toFontBank || --WARNING-- Bank " + b + " points to empty wave archive: " + warc_idx);
				b++; continue;
			}
			
			List<Z64WaveInfo> waves = bank.getAllWaveInfoBlocks();
			for(Z64WaveInfo winfo : waves){
				int woff = winfo.getWaveOffset();
				String key = String.format("%02x:%08x", warc_idx, woff);
				String wname = warc_off_map.get(key);
				if(wname == null){
					//Hasn't been seen yet.
					//wname = String.format("UWAV_%02d_%08x", warc_idx, woff);
					FileBuffer sounddat = warc_data.createReadOnlyCopy(woff, woff+winfo.getWaveSize());
					String md5str = FileUtils.bytes2str(FileUtils.getMD5Sum(sounddat.getBytes()));
					sounddat.dispose();
					
					//See if this sound has been seen in another arc?
					wname = md5map.get(md5str);
					if(wname == null){
						//New sample!
						wname = "UWAV_" + md5str.substring(0,12);
						md5map.put(md5str, wname);
						
						//Load sample into bank...
						sounddat = warc_data.createCopy(woff, woff+winfo.getWaveSize());
						mainBank.addSample(wname, Z64Wave.readZ64Wave(sounddat, winfo));
					}
					warc_off_map.put(key, wname);
				}
				winfo.setName(wname);
			}
			
			//Merge refs with previous banks.
			for(Z64Bank prev : processed_banks){
				bank.mergeElementReferences(prev);
			}
			
			//Create SingleBank and add presets...
			int bidx = mainBank.newBank(b, String.format("bank_%03x", b));
			if(bidx < 0) {
				System.err.println("Z64Bank.toFontBank || --ERROR-- Bank " + b + " could not be added for some reason");
				return null;
			}
			SingleBank myBank = mainBank.getBank(bidx);
			
			//Add instruments (from inst pool)
			for(Z64Instrument inst : bank.inst_pool.values()){
				if(inst.getName() != null) continue;
				String iname = String.format("I%04d", sfinst_count++);
				inst.setName(iname);
				SimpleInstrument sfinst = mainBank.newInstrument(iname, 3);
				convertInst(inst, sfinst);
			}
			
			//Add perc regions (from pool)
			for(Z64Drum perc : bank.perc_pool.values()){
				if(perc.getName() != null) continue;
				String pname = String.format("P%04d", sfdrum_count++);
				perc.setName(pname);
				SimpleInstrument sfinst = mainBank.newInstrument(pname, 3);
				convertPerc(perc, sfinst);
			}
			
			//Generate presets
			int p = 0;
			for(int i = 0; i < bank.icount; i++){
				InstBlock iblock = bank.inst_presets[i];
				if(iblock == null){p++; continue;} //Unused index
				int pidx = myBank.newPreset(p, String.format("INST_%03x", p), 2);
				SimplePreset preset = myBank.getPreset(pidx);
				//Just wraps the instrument
				SimpleInstrument sfinst = mainBank.getLooseInstrument(iblock.data.getName());
				preset.newRegion(sfinst);
				p++;
			}
			
			//Do SFX (preset 126)
			//SFX are weird because there can be >128 - I think layer transpose is
				// used to select subset.
			//For these, if they appear, each subset for that bank will be its own inst.
			//The bank preset 126 will be the first subset.
			if(!bank.sfx_pool.isEmpty()){
				int slot = STDRANGE_BOTTOM;
				int group = 0;
				SimpleInstrument sfinst = mainBank.newInstrument(String.format("SFX_%02x_%02x", b, group), STDRANGE_SIZE);
				int pidx = myBank.newPreset(126, "SFX", 2);
				SimplePreset preset = myBank.getPreset(pidx);
				preset.newRegion(sfinst);
				for(int i = 0; i < bank.xcount; i++){
					SFXBlock block = bank.sfx_slots[i];
					if(block.data != null) {
						int ridx = sfinst.newRegion(block.data.getSample().getName());
						InstRegion r = sfinst.getRegion(ridx);
						convertSFXRegion(block.data, r, slot);
						r.setMaxKey((byte)slot);
						r.setMinKey((byte)slot);
					}
					slot++;
					if(slot >= MAXSLOT){
						slot = STDRANGE_BOTTOM;
						sfinst = mainBank.newInstrument(String.format("SFX_%02x_%02x", b, ++group), STDRANGE_SIZE);
					}
				}	
			}
			
			//Do percussion (preset 127)
			//Each drum (sample+articulation combo) is its own inst.
			//Preset is bank's drum map.
			if(!bank.perc_pool.isEmpty() && bank.pcount > 0){
				/*
				 * Again, need a smarter merge for when same drum sample...
				 * Why add like 20 different regions of drum A, when we can have
				 * a region of drum A across 20 notes?
				 */
				int pidx = myBank.newPreset(127, "DRUMS", STDRANGE_SIZE);
				SimplePreset preset = myBank.getPreset(pidx);
				String last_drum = null;
				int last_drum_idx = -1;
				for(int i = 0; i < bank.pcount; i++){
					PercBlock pblock = bank.perc_slots[i];
					if(pblock == null) continue;
					if(last_drum != null){
						String dname = pblock.data.getName();
						if(!last_drum.equals(dname)){
							//Add region
							int ridx = preset.newRegion(mainBank.getLooseInstrument(last_drum));
							PresetRegion reg = preset.getRegion(ridx);
							reg.setMinKey((byte)(last_drum_idx + STDRANGE_BOTTOM));
							reg.setMaxKey((byte)(i + STDRANGE_BOTTOM));
							last_drum = pblock.data.getName();
							last_drum_idx = i+1;
						}
					}
					else{
						last_drum = pblock.data.getName();
						last_drum_idx = i;
					}
				}
				//Add last region.
				int ridx = preset.newRegion(mainBank.getLooseInstrument(last_drum));
				PresetRegion reg = preset.getRegion(ridx);
				reg.setMinKey((byte)(last_drum_idx + STDRANGE_BOTTOM));
				reg.setMaxKey((byte)((bank.pcount-1) + STDRANGE_BOTTOM));
			}
			
			//Annnnd continue...
			processed_banks.add(bank);
			b++;
		}
		}
		catch(IOException ex){
			ex.printStackTrace();
			return null;
		}
		
		return mainBank;
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
			SimpleBank sbank = bank.toBank(warcdat);
			sbank.setName(node.getFileName());
			sbank.setVendor("Nintendo64 AL");
			sbank.setVersionString("version_unk");
			SF2.writeSF2(sbank, "WaffleoJavaUtilsGX", false, outpath);
		}
	}
	
	/*----- Debug -----*/
	
	public void debug_printWaveBlocks(Writer out) throws IOException{
		//Sort wave blocks by address in bank file
		Map<Integer, WaveInfoBlock> map2 = new HashMap<Integer, WaveInfoBlock>();
		for(WaveInfoBlock b : sound_samples.values()){
			map2.put(b.addr, b);
		}
		
		List<Integer> keys = new ArrayList<Integer>(map2.size()+1);
		keys.addAll(map2.keySet());
		Collections.sort(keys);
		
		for(Integer addr : keys){
			WaveInfoBlock b = map2.get(addr);
			out.write(String.format("%04x\t", addr));
			out.write(String.format("%02x ", b.getWaveInfo().getFlagsField()));
			out.write(String.format("%02x ", b.getWaveInfo().getU2()));
			out.write(String.format("%04x ", b.getWaveInfo().getWaveSize()));
			out.write(String.format("%08x ", b.getWaveInfo().getWaveOffset()));
			out.write(String.format("%04x ", b.loop_offset));
			out.write(String.format("%04x\n", b.pred_offset));
		}
	}
	
	public void printMeTo(Writer out) throws IOException{
		if(out == null) return;
		
		Map<String, Z64Envelope> envs = new HashMap<String, Z64Envelope>();
		out.write("================== N64 Soundbank (Soundfont) ==================\n");
		//Instrument slots
		out.write("-----> Presets <-----\n");
		for(int i = 0; i < 126; i++){
			InstBlock block = inst_presets[i];
			out.write(String.format("\t%03d: ", i));
			if(block != null){
				String n = block.data.getName();
				if(n == null){
					n = "INST_" + Integer.toHexString(block.data.id);
					block.data.setName(n);
				}
				out.write(n + "\n");
				Z64Envelope env = block.data.getEnvelope();
				n = env.getName();
				if(n == null){
					n = "ENV_" + String.format("%03d", envs.size());
					env.setName(n);
				}
				envs.put(n, env);
			}
			else out.write("<EMPTY>\n");
		}
		
		//Percussion slots
		out.write("-----> Percussion Map <-----\n");
		for(int i = 0; i < 64; i++){
			PercBlock block = perc_slots[i];
			out.write(String.format("\t%03d: ", i));
			if(block != null){
				String n = block.data.getName();
				if(n == null){
					n = "PERC_" + Integer.toHexString(block.data.id);
					block.data.setName(n);
				}
				out.write(n + "\n");
				Z64Envelope env = block.data.getEnvelope();
				n = env.getName();
				if(n == null){
					n = "ENV_" + String.format("%03d", envs.size());
					env.setName(n);
				}
				envs.put(n, env);
			}
			else out.write("<EMPTY>\n");
		}
		
		//SFX Slots
		if(sfx_slots != null){
			out.write("-----> SFX Map <-----\n");
			for(int i = 0; i < sfx_slots.length; i++){
				SFXBlock block = sfx_slots[i];
				out.write(String.format("\t%03d: ", i));
				if(block != null){
					String n = block.data.getName();
					if(n == null){
						n = "SFX_" + Integer.toHexString(block.data.id);
						block.data.setName(n);
					}
					out.write(n + "\n");
				}
				else out.write("<EMPTY>\n");
			}
		}
		
		//Wave Infos
		List<Z64WaveInfo> wlist = this.getAllWaveInfoBlocks();
		out.write("-----> Samples <-----\n");
		for(Z64WaveInfo winfo : wlist){
			if(winfo.getName() == null){
				int id = winfo.getWaveOffset();
				if(this.samples_by_uid) id = winfo.getUID();
				winfo.setName("WAVE_" + String.format("%08x", id));
			}
			out.write("\t-> " + winfo.getName() + "\n");
			out.write("\t\tCodec: ");
			switch(winfo.getCodec()){
			case Z64Sound.CODEC_ADPCM: out.write("ADP9\n"); break;
			case Z64Sound.CODEC_SMALL_ADPCM: out.write("ADP5\n"); break;
			case Z64Sound.CODEC_REVERB: out.write("RVRB\n"); break;
			case Z64Sound.CODEC_S16: out.write("PCMS\n"); break;
			case Z64Sound.CODEC_S16_INMEMORY: out.write("PCMM\n"); break;
			case Z64Sound.CODEC_S8: out.write("PCM8\n"); break;
			}
			out.write("\t\tMedium: ");
			switch(winfo.getCodec()){
			case Z64Sound.MEDIUM_CART: out.write("Cartridge\n"); break;
			case Z64Sound.MEDIUM_DISK_DRIVE: out.write("Disk Drive\n"); break;
			case Z64Sound.MEDIUM_RAM: out.write("RAM\n"); break;
			case Z64Sound.MEDIUM_UNK: out.write("(Unknown)\n"); break;
			}
			out.write("\t\tWave Archive Offset: 0x" + Long.toHexString(winfo.getWaveOffset()) + "\n");
			out.write("\t\tWave Size: 0x" + Long.toHexString(winfo.getWaveSize()) + "\n");
			out.write("\t\tLoop Start: " + winfo.getLoopStart() + "\n");
			out.write("\t\tLoop End: " + winfo.getLoopEnd() + "\n");
			out.write("\t\tLoop Count: " + winfo.getLoopCount() + "\n");
			N64ADPCMTable book = winfo.getADPCMBook();
			if(book != null){
				out.write("\t\tADPCM Order: " + book.getOrder() + "\n");
				out.write("\t\tADPCM Predictor Count: " + book.getPredictorCount() + "\n");
			}
		}
		
		//Envelopes
		out.write("-----> Envelopes <-----\n");
		List<String> keys = new ArrayList<String>(envs.size()+1);
		keys.addAll(envs.keySet());
		Collections.sort(keys);
		for(String key : keys){
			Z64Envelope env = envs.get(key);
			out.write("\t-> " + env.getName() + "\n");
			for(short[] event : env.events){
				switch(event[0]){
				case Z64Sound.ENVCMD__ADSR_DISABLE:
					out.write("\t\tADSR_DISABLE\n");
					break;
				case Z64Sound.ENVCMD__ADSR_GOTO:
					out.write("\t\tADSR_GOTO:" + event[1] + "\n");
					break;
				case Z64Sound.ENVCMD__ADSR_HANG:
					out.write("\t\tADSR_HANG\n");
					break;
				case Z64Sound.ENVCMD__ADSR_RESTART:
					out.write("\t\tADSR_RESTART\n");
					break;
				default:
					out.write("\t\t" + event[0] + ":" + event[1] + "\n");
					break;
				}
			}
			switch(env.terminator){
			case Z64Sound.ENVCMD__ADSR_DISABLE:
				out.write("\t\tADSR_DISABLE\n");
				break;
			case Z64Sound.ENVCMD__ADSR_HANG:
				out.write("\t\tADSR_HANG\n");
				break;
			case Z64Sound.ENVCMD__ADSR_RESTART:
				out.write("\t\tADSR_RESTART\n");
				break;
			}
		}
		
		//Instruments
		out.write("-----> Instruments <-----\n");
		List<Integer> ikeys = new ArrayList<Integer>(inst_pool.size()+1);
		ikeys.addAll(inst_pool.keySet());
		Collections.sort(ikeys);
		for(Integer key : ikeys){
			Z64Instrument inst = inst_pool.get(key);
			out.write("\t-> " + inst.getName() + "\n");
			out.write("\t\tLow Boundary: " + inst.getLowRangeTop() + "\n");
			out.write("\t\tHigh Boundary: " + inst.getHighRangeBottom() + "\n");
			out.write("\t\tDecay: " + inst.getDecay() + "\n");
			if(inst.getEnvelope() != null) out.write("\t\tEnvelope: " + inst.getEnvelope().getName() + "\n");
			if(inst.getSampleLow() != null){
				out.write("\t\tLow Region: ");
				out.write(inst.getSampleLow().getName());
				out.write(" (");
				out.write(Float.toString(inst.getTuningLow()));
				out.write(")\n");
			}
			if(inst.getSampleMiddle() != null){
				out.write("\t\tMain Region: ");
				out.write(inst.getSampleMiddle().getName());
				out.write(" (");
				out.write(Float.toString(inst.getTuningMiddle()));
				out.write(")\n");
			}
			if(inst.getSampleHigh() != null){
				out.write("\t\tHigh Region: ");
				out.write(inst.getSampleHigh().getName());
				out.write(" (");
				out.write(Float.toString(inst.getTuningHigh()));
				out.write(")\n");
			}
		}
		
		//Drums
		out.write("-----> Drums <-----\n");
		ikeys = new ArrayList<Integer>(perc_pool.size()+1);
		ikeys.addAll(perc_pool.keySet());
		Collections.sort(ikeys);
		for(Integer key : ikeys){
			Z64Drum drum = perc_pool.get(key);
			out.write("\t-> " + drum.getName() + "\n");
			out.write("\t\tDecay: " + drum.getDecay() + "\n");
			out.write("\t\tPan: " + drum.getPan() + "\n");
			if(drum.getEnvelope() != null) out.write("\t\tEnvelope: " + drum.getEnvelope().getName() + "\n");
			if(drum.getSample() != null){
				out.write("\t\tSample: ");
				out.write(drum.getSample().getName());
				out.write(" (");
				out.write(Float.toString(drum.getTuning()));
				out.write(")\n");
			}
		}
	}
	
	public static void main(String[] args){

		try{
			String inpath = args[0];
			int icount = Integer.parseInt(args[1]);
			int pcount = Integer.parseInt(args[2]);
			int xcount = Integer.parseInt(args[3]);
			String outpath = args[4];
			
			System.err.println("inpath = " + inpath);
			System.err.println("icount = " + icount);
			System.err.println("pcount = " + pcount);
			System.err.println("xcount = " + xcount);
			System.err.println("outpath = " + outpath);
			
			System.err.println("Reading bank...");
			FileBuffer dat = FileBuffer.createBuffer(inpath, true);
			Z64Bank mybank = Z64Bank.readBank(dat, icount, pcount, xcount);
			
			System.err.println("Dumping info...");
			BufferedWriter bw = new BufferedWriter(new FileWriter(outpath));
			mybank.printMeTo(bw);
			bw.close();
		}
		catch(Exception ex){
			ex.printStackTrace();
			System.exit(1);
		}
	}
	
}
