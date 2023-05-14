package waffleoRai_soundbank.nintendo.z64;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import waffleoRai_Files.WriterPrintable;
import waffleoRai_Sound.nintendo.Z64Sound;
import waffleoRai_Sound.nintendo.Z64WaveInfo;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.MultiFileBuffer;
import waffleoRai_soundbank.nintendo.z64.Z64BankBlocks.*;
import waffleoRai_soundbank.nintendo.z64.Z64DrumPool.DrumRegionInfo;

/**
 * A container for data specifying a soundfont for an EAD Nintendo64
 * game. Also used to read and write the raw binary form of the soundfont
 * format.
 * @author Blythe Hospelhorn
 * @version 3.0.0
 * @since May 6, 2023
 */
public class Z64Bank implements WriterPrintable{
	
	/*----- Constants -----*/
	
	public static final String SOUND_KEY_STEM = "wave64_";
	
	public static final String FNMETAKEY_I0COUNT = "INSTCOUNT";
	public static final String FNMETAKEY_I1COUNT = "PERCCOUNT";
	public static final String FNMETAKEY_I2COUNT = "SFXCOUNT";
	public static final String FNMETAKEY_BNKIDKEY = "U64BNKID";
	
	public static final int MAPORDER_UID = 0;
	public static final int MAPORDER_OFFSET_GLOBAL = 1;
	public static final int MAPORDER_OFFSET_LOCAL = 2;
	
	public static final int REFTYPE_UID = 0;
	public static final int REFTYPE_OFFSET_GLOBAL = 1;
	public static final int REFTYPE_OFFSET_LOCAL = 2;
	
	public static final int STDRANGE_BOTTOM = Z64Sound.STDRANGE_BOTTOM;
	public static final int STDRANGE_SIZE = Z64Sound.STDRANGE_SIZE;
	public static final int MIDDLE_C = Z64Sound.MIDDLE_C;
	
	//Lowest two bits of options are the ordering
	public static final int SEROP_ORDERING_NONE = 0x0000;
	public static final int SEROP_ORDERING_OFFLOCAL = 0x0001;
	public static final int SEROP_ORDERING_OFFWARC = 0x0002;
	public static final int SEROP_ORDERING_UID = 0x0003;
	
	public static final int SEROP_LITTLE_ENDIAN = 0x0004;
	public static final int SEROP_64BIT = 0x0008;
	public static final int SEROP_REF_WAV_UIDS = 0x0010;
	
	public static final int SEROP_DEFAULT = 0;

	/*----- Private Classes -----*/
	
	protected static abstract class Labelable{
		protected String name;
		public String getName(){return name;}
		public void setName(String s){name = s;}
	}

	public static class Z64ReadOptions{
		public int instCount = -1;
		public int percCount = -1;
		public int sfxCount = -1;
		public boolean src64 = false;
		public boolean srcLE = false;
	}
	
	/*----- Instance Variables -----*/
	
	// -- Contents
	private Z64WavePool wavePool;
	private Z64EnvPool envPool;
	
	private Z64InstPool instPool;
	private Z64DrumPool drumPool;
	private Z64SFXPool sfxPool;
	
	// -- Metadata
	private int medium = Z64Sound.MEDIUM_CART;
	private int cachePolicy = Z64Sound.CACHE_TEMPORARY;
	private int warc1 = 0;
	private int warc2 = -1;
	
	private int uid = 0;
	
	/*----- Init -----*/
	
	public Z64Bank(){this(4);}
	
	public Z64Bank(int sfxAlloc){
		wavePool = new Z64WavePool();
		envPool = new Z64EnvPool();
		instPool = new Z64InstPool();
		drumPool = new Z64DrumPool();
		sfxPool = new Z64SFXPool(sfxAlloc);
	}
	
	/*----- Read -----*/
	
	public static Z64Bank readRaw(FileBuffer data, Z64ReadOptions options){
		if(data == null) return null;
		if(options == null) options = new Z64ReadOptions();
		
		//If instrument counts aren't known, will have to guess.
		//Start with SFX, then perc, then inst.
		//	Makes it easier to find unknown counts that way.
		
		//We'll just cache references for now. Also makes it easier to keep
		//	things in order in case of matching.
		Set<Integer> wavRefCache = new HashSet<Integer>();
		Set<Integer> envRefCache = new HashSet<Integer>();
		Map<Integer, Float> tuningCache = new HashMap<Integer, Float>();
		
		Z64Bank myBank = null;
		data.setEndian(!options.srcLE);
		data.setCurrentPosition(0L);
		int ptrSize = options.src64 ? 8 : 4;
		long percTblPos = options.src64 ? data.nextLong() : Integer.toUnsignedLong(data.nextInt());
		long sfxTblPos = options.src64 ? data.nextLong() : Integer.toUnsignedLong(data.nextInt());
		long fsize = data.getFileSize();
		
		//>> SFX Table, if present. <<
		if(sfxTblPos > 0){
			int sfxBlockSize = options.src64 ? Z64BankBlocks.BLOCKSIZE_SFX_64 : Z64BankBlocks.BLOCKSIZE_SFX;
			if(options.sfxCount < 0){
				options.sfxCount = (int)((fsize - sfxTblPos) / sfxBlockSize);
			}
			myBank = new Z64Bank(options.sfxCount);
			
			for(int i = 0; i < options.sfxCount; i++){
				long cpos = sfxTblPos;
				//Check if slot is just empty
				if(options.src64){
					if(data.longFromFile(cpos + 8L) == 0L){
						cpos += sfxBlockSize;
						continue;
					}
				}
				else{
					if(data.longFromFile(cpos) == 0L){
						cpos += sfxBlockSize;
						continue;
					}
				}
				
				//Else...
				SFXBlock block = myBank.sfxPool.importSFXFromBin(
						data.getReferenceAt(cpos), i, options.src64, options.srcLE);
				
				if(block != null){
					wavRefCache.add(block.off_snd);
					if(!tuningCache.containsKey(block.off_snd)){
						tuningCache.put(block.off_snd, block.data.getTuning());
					}
				}
				cpos += sfxBlockSize;
				if(cpos >= fsize) break;
			}
		}
		else myBank = new Z64Bank();
		
		//>> Percussion <<
		long imaxpos = percTblPos;
		if(percTblPos > 0){
			//Estimate perc count, if unknown
			if(options.percCount < 0){
				options.percCount = (int)((sfxTblPos - percTblPos) / ptrSize);
				if(options.percCount > 64) options.percCount = 64;
			}
			
			long cpos = percTblPos;
			for(int i = 0; i < options.percCount; i++){
				long drumOff = options.src64 ? data.longFromFile(cpos) : Integer.toUnsignedLong(data.intFromFile(cpos));
				if(drumOff == 0){cpos += ptrSize; continue;}
				
				if(drumOff < imaxpos){
					imaxpos = drumOff;
				}
				
				PercBlock block = myBank.drumPool.importDrumFromBin(
						data.getReferenceAt(drumOff), i, options.src64, options.srcLE);
				
				if(block != null){
					wavRefCache.add(block.off_snd);
					envRefCache.add(block.off_env);
				}
				
				cpos += ptrSize;
				if(cpos >= sfxTblPos) break;
			}
			
			//Look for tuning values to assign
			List<DrumRegionInfo> drumreg = myBank.drumPool.getDrumRegions();
			for(DrumRegionInfo reg : drumreg){
				int midnote = (int)reg.minNote + (int)((reg.maxNote - reg.minNote)/2);
				PercBlock pblock = myBank.drumPool.getSlot(midnote);
				if(pblock != null){
					int wid = pblock.off_snd;
					if((wid > 0) && (!tuningCache.containsKey(wid))){
						tuningCache.put(wid, pblock.tune);
					}
				}
			}
		}
		
		//>> Instruments <<
		long itblend = fsize;
		if(options.instCount < 0){
			//Start with the lowest value in the wav and env caches
			for(Integer i : wavRefCache){
				if(i < itblend) itblend = i;
			}
			for(Integer i : envRefCache){
				if(i < itblend) itblend = i;
			}
		}
		else itblend = (options.instCount + 2) * ptrSize;
		long cpos = (long)ptrSize << 1;
		int i = 0;
		while(cpos < itblend){
			long instOff = options.src64 ? data.longFromFile(cpos) : Integer.toUnsignedLong(data.intFromFile(cpos));
			if(instOff == 0){cpos += ptrSize; i++; continue;}
			if(instOff < itblend) itblend = instOff;
			if(instOff >= imaxpos) break; //Probably not real
			
			InstBlock block = myBank.instPool.importInstFromBin(
					data.getReferenceAt(instOff), i, options.src64, options.srcLE);
			
			if(block != null){
				envRefCache.add(block.off_env);
				wavRefCache.add(block.off_snd_med);
				
				if(block.off_env < itblend) itblend = block.off_env;
				if(block.off_snd_med < itblend) itblend = block.off_snd_med;
				
				if(block.off_snd_lo > 0){
					wavRefCache.add(block.off_snd_lo);
					if(block.off_snd_lo < itblend) itblend = block.off_snd_lo;
					if(!tuningCache.containsKey(block.off_snd_lo)){
						float tune = Z64Sound.suggestWaveTuningFromInstRegion(0, block.data.getLowRangeTop()-1, block.data.getTuningLow());
						tuningCache.put(block.off_snd_lo, tune);
					}
				}
				
				if(block.off_snd_hi > 0){
					wavRefCache.add(block.off_snd_hi);
					if(block.off_snd_hi < itblend) itblend = block.off_snd_hi;
					if(!tuningCache.containsKey(block.off_snd_hi)){
						float tune = Z64Sound.suggestWaveTuningFromInstRegion(block.data.getHighRangeBottom()+1, 127, block.data.getTuningHigh());
						tuningCache.put(block.off_snd_hi, tune);
					}
				}
				
				if(!tuningCache.containsKey(block.off_snd_med)){
					float tune = Z64Sound.suggestWaveTuningFromInstRegion(
							block.data.getLowRangeTop(), block.data.getHighRangeBottom(), block.data.getTuningMiddle());
					tuningCache.put(block.off_snd_med, tune);
				}
			}
			
			cpos += ptrSize; i++;
		}
		
		//>> Waves <<
		List<Integer> intlist = new LinkedList<Integer>();
		intlist.addAll(wavRefCache);
		Collections.sort(intlist);
		for(Integer ref : intlist){
			WaveInfoBlock block = myBank.wavePool.importWaveData(
					data.getReferenceAt(ref), options.src64, options.srcLE);
			
			//Match tuning...
			if(tuningCache.containsKey(ref)){
				float tune = tuningCache.get(ref);
				block.wave_info.setTuning(tune);
			}
		}
		myBank.wavePool.updateMaps();
		
		//>> Envelopes <<
		intlist.clear();
		intlist.addAll(wavRefCache);
		Collections.sort(intlist);
		for(Integer ref : intlist){
			myBank.envPool.importFromBin(
					data.getReferenceAt(ref));
		}
		myBank.envPool.updateMaps();
		
		
		//>> Link references <<
		List<InstBlock> iblocks = myBank.instPool.getAllInstBlocks();
		for(InstBlock block : iblocks){
			WaveInfoBlock wblock = myBank.wavePool.getByLocalOffset(block.off_snd_med);
			if(wblock != null){
				block.snd_med = wblock;
				if(block.data != null) block.data.setSampleMiddle(wblock.getWaveInfo());
			}
			
			wblock = myBank.wavePool.getByLocalOffset(block.off_snd_lo);
			if(wblock != null){
				block.snd_lo = wblock;
				if(block.data != null) block.data.setSampleLow(wblock.getWaveInfo());
			}
			
			wblock = myBank.wavePool.getByLocalOffset(block.off_snd_hi);
			if(wblock != null){
				block.snd_hi = wblock;
				if(block.data != null) block.data.setSampleHigh(wblock.getWaveInfo());
			}
			
			EnvelopeBlock eblock = myBank.envPool.getByLocalOffset(block.off_env);
			if(eblock != null){
				block.envelope = eblock;
				if(block.data != null) block.data.setEnvelope(block.envelope.data);
			}
		}
		myBank.instPool.updateMaps();
		
		List<PercBlock> pblocks = myBank.drumPool.getAllPercBlocks();
		for(PercBlock block : pblocks){
			WaveInfoBlock wblock = myBank.wavePool.getByLocalOffset(block.off_snd);
			if(wblock != null){
				block.sample = wblock;
				if(block.data != null) block.data.setSample(wblock.getWaveInfo());
			}
			
			EnvelopeBlock eblock = myBank.envPool.getByLocalOffset(block.off_env);
			if(eblock != null){
				block.envelope = eblock;
				if(block.data != null) block.data.setEnvelope(block.envelope.data);
			}
		}
		myBank.drumPool.updateMaps();
		
		List<SFXBlock> xblocks = myBank.sfxPool.getAllSFXBlocks();
		for(SFXBlock block : xblocks){
			WaveInfoBlock wblock = myBank.wavePool.getByLocalOffset(block.off_snd);
			if(wblock != null){
				block.sample = wblock;
				if(block.data != null) block.data.setSample(wblock.getWaveInfo());
			}
		}
		myBank.sfxPool.updateMaps();
		
		return myBank;
	}
	
	/*----- Write -----*/
	
	public void tidy(){
		wavePool.updateMaps();
		envPool.updateMaps();
		sfxPool.updateMaps();
		drumPool.updateMaps();
		instPool.updateMaps();
		
		wavePool.mergeRedundantBlocks();
		drumPool.cleanUp();
		instPool.cleanUp();
	}
	
	public int getSerializedSize(int options){
		int total = 0;
		boolean p64 = (options & SEROP_64BIT) != 0;
		int ptrSize = p64 ? 8 : 4;
		
		int iptrTblSize = (2 + getEffectiveInstCount()) * ptrSize;
		//Snap to 16
		iptrTblSize += 0xf;
		iptrTblSize &= ~0xf;
		total += iptrTblSize;
		
		total += wavePool.getTotalSerializedSize(p64);
		total += envPool.getTotalSerializedSize();
		total += instPool.getTotalSerializedSize(p64);
		total += drumPool.getDataSerializedSize(p64);
		total += drumPool.getTableSerializedSize(p64);
		total += sfxPool.getTotalSerializedSize(p64);
		
		return total;
	}
	
	public int serializeTo(FileBuffer target, int options){
		//TODO do inst perc and sfx blocks need to use wave uids too? 
		//Maybe not if they are in the wave block.
		long size = 0;
		boolean p64 = (options & SEROP_64BIT) != 0;
		boolean le = (options & SEROP_LITTLE_ENDIAN) != 0;
		boolean useUID = (options & SEROP_REF_WAV_UIDS) != 0;
		
		int ptrSize = p64 ? 8 : 4;
		int iptrTblSize = (2 + getEffectiveInstCount()) * ptrSize;
		//Snap to 16
		iptrTblSize += 0xf;
		iptrTblSize &= ~0xf;
		size += iptrTblSize;
		
		//>> Waves <<
		int reorder = options & 0x3;
		switch(reorder){
		case SEROP_ORDERING_OFFLOCAL:
			wavePool.reorderByLocalAddress();
			break;
		case SEROP_ORDERING_OFFWARC:
			wavePool.reorderByWaveArcAddress();
			break;
		case SEROP_ORDERING_UID:
			wavePool.reorderByUID();
			break;
		}
		wavePool.updateMaps();
		wavePool.repackSerial(iptrTblSize, p64);
		FileBuffer bWav = wavePool.serializePool(p64, le, useUID);
		size += bWav.getFileSize();
		
		//>> Envelopes <<
		envPool.updateMaps();
		envPool.repackSerial((int)size);
		FileBuffer bEnv = envPool.serializePool(le);
		if(bEnv != null) size += bEnv.getFileSize();
		
		//>> Inst Block <<
		instPool.updateMaps();
		instPool.repackSerial((int)size, p64);
		FileBuffer bIns = instPool.serializePool(p64, le);
		if(bIns != null) size += bIns.getFileSize();
		
		//>> Perc <<
		int drumPos = (int)size;
		drumPool.updateMaps();
		drumPool.repackSerial((int)size, p64);
		FileBuffer bPrc = drumPool.serializePool(p64, le);
		if(bPrc != null) size += bPrc.getFileSize();
		int drumDataSize = drumPool.getDataSerializedSize(p64);
		
		//>> SFX <<
		int sfxPos = (int)size;
		sfxPool.updateMaps();
		sfxPool.repackSerial((int)size, p64);
		FileBuffer bSfx = sfxPool.serializePool(p64, le);
		if(bSfx != null) size += bSfx.getFileSize();
		
		//>> Main offset table <<
		FileBuffer maintbl = new FileBuffer(iptrTblSize, !le);
		if(p64){
			if(bPrc != null) maintbl.addToFile((long)(drumPos + drumDataSize));
			else maintbl.addToFile(0L);
			if(bSfx != null) maintbl.addToFile((long)sfxPos);
			else maintbl.addToFile(0L);
		}
		else{
			if(bPrc != null) maintbl.addToFile(drumPos + drumDataSize);
			else maintbl.addToFile(0);
			if(bSfx != null) maintbl.addToFile(sfxPos);
			else maintbl.addToFile(0);
		}
		
		//Inst positions
		if(bIns != null){
			InstBlock[] islots = instPool.getSlots();
			int iused = instPool.getSerializedSlotCount();
			for(int i = 0; i < iused; i++){
				if(p64){
					if(islots[i] != null) maintbl.addToFile((long)islots[i].addr);
					else maintbl.addToFile(0L);
				}
				else{
					if(islots[i] != null) maintbl.addToFile(islots[i].addr);
					else maintbl.addToFile(0);
				}
			}
		}
		
		//Pad to 16
		while(maintbl.getFileSize() < iptrTblSize) maintbl.addToFile((byte)0);
		
		//>> Add to file <<
		target.addToFile(maintbl);
		target.addToFile(bWav);
		if(bEnv != null) target.addToFile(bEnv);
		if(bEnv != null) target.addToFile(bIns);
		if(bEnv != null) target.addToFile(bPrc);
		if(bEnv != null) target.addToFile(bSfx);
		
		return (int)size;
	}
	
	public FileBuffer serializeMe(int options){
		FileBuffer output = new MultiFileBuffer(6);
		serializeTo(output, options);
		return output;
	}
	
	public int[] serializeToCommandStream(){
		//TODO
		//For transferring to native code.
		return null;
	}
	
	/*----- Getters -----*/
	
	public int getUID(){return uid;}
	public int getMedium(){return medium;}
	public int getCachePolicy(){return cachePolicy;}
	public int getPrimaryWaveArcIndex(){return warc1;}
	public int getSecondaryWaveArcIndex(){return warc2;}
	
	public int getEffectiveInstCount(){
		return instPool.getSerializedSlotCount();
	}
	
	public int getEffectivePercCount(){
		return drumPool.getSerializedSlotCount();
	}
	
	public int getEffectiveSFXCount(){
		return sfxPool.getSerializedSlotCount();
	}
	
	public Z64Instrument getInstrumentInSlot(int index){
		InstBlock iblock = instPool.getSlot(index);
		if(iblock == null) return null;
		return iblock.data;
	}
	
	public Z64Drum getDrumInSlot(int index){
		PercBlock block = drumPool.getSlot(index);
		if(block == null) return null;
		return block.data;
	}
	
	public Z64SoundEffect getSFXInSlot(int index){
		SFXBlock block = sfxPool.getSlot(index);
		if(block == null) return null;
		return block.data;
	}
	
	public Z64Instrument[] getInstrumentPresets(){
		InstBlock[] blocks = instPool.getSlots();
		Z64Instrument[] islots = new Z64Instrument[126];
		if(blocks != null){
			for(int i = 0; i < blocks.length; i++){
				islots[i] = blocks[i].data;
			}
		}
		return islots;
	}
	
	public Z64Drum[] getPercussionSet(){
		Z64Drum[] pslots = new Z64Drum[64];
		for(int i = 0; i < 64; i++){
			PercBlock block = drumPool.getSlot(i);
			if(block != null) pslots[i] = block.data;
		}
		
		return pslots;
	}
	
	public Z64SoundEffect[] getSFXSet(){
		int slotcount = sfxPool.getSerializedSlotCount();
		if(slotcount < 1) return null;
		Z64SoundEffect[] slots = new Z64SoundEffect[slotcount];
		for(int i = 0; i < slots.length; i++){
			SFXBlock block = sfxPool.getSlot(i);
			if(block != null){
				slots[i] = block.data;
			}
		}
		return slots;
	}
	
	public List<Z64Instrument> getAllUniqueInstruments(){
		List<InstBlock> blocks = instPool.getAllInstBlocks();
		if(blocks.isEmpty()) return new LinkedList<Z64Instrument>();
		List<Z64Instrument> list = new ArrayList<Z64Instrument>(blocks.size());
		for(InstBlock block : blocks){
			if(block.data == null) continue;
			list.add(block.data);
		}
		return list;
	}
	
	public List<Z64Drum> getAllUniqueDrums(){
		return drumPool.getAllDrums();
	}
	
	public List<Z64SoundEffect> getAllUniqueSFX(){
		List<SFXBlock> blocks = sfxPool.getAllSFXBlocks();
		if(blocks.isEmpty()) return new LinkedList<Z64SoundEffect>();
		List<Z64SoundEffect> list = new ArrayList<Z64SoundEffect>(blocks.size());
		for(SFXBlock block : blocks){
			if(block.data == null) continue;
			list.add(block.data);
		}
		return list;
	}
	
	public List<Z64Envelope> getAllEnvelopes(){
		List<EnvelopeBlock> blocks = envPool.getAllEnvBlocks();
		if(blocks.isEmpty()) return new LinkedList<Z64Envelope>();
		List<Z64Envelope> list = new ArrayList<Z64Envelope>(blocks.size());
		for(EnvelopeBlock block : blocks){
			if(block.data == null) continue;
			list.add(block.data);
		}
		return list;
	}
	
	public List<Z64WaveInfo> getAllWaveBlocks(){
		return wavePool.getAllWaves();
	}
	
	public String getInstPresetEnumString(int idx){
		return instPool.getSlotEnumString(idx);
	}
	
	public String getDrumSlotEnumString(int idx){
		return drumPool.getSlotEnumString(idx);
	}
	
	public String getSFXSlotEnumString(int idx){
		SFXBlock block = sfxPool.getSlot(idx);
		if(block == null) return null;
		return block.enm_str;
	}
	
	/*----- Setters -----*/
	
	public void setUID(int val){uid = val;}
	public void setMedium(int val){medium = val;}
	public void setCachePolicy(int val){cachePolicy = val;}
	public void setPrimaryWaveArcIndex(int val){warc1 = val;}
	public void setSecondaryWaveArcIndex(int val){warc2 = val;}
	
	public Z64Instrument setInstrument(int slot, Z64Instrument inst){
		InstBlock block = new InstBlock(inst);
		
		//Link env and samples to block. Add to pools if not already there.
		EnvelopeBlock eblock = envPool.addToPool(inst.getEnvelope());
		block.envelope = eblock;
		block.off_env = eblock.addr;
		inst.setEnvelope(eblock.data);
		
		Z64WaveInfo winfo = inst.getSampleMiddle();
		if(winfo != null){
			WaveInfoBlock wblock = wavePool.addToPool(winfo);
			if(wblock != null){
				block.snd_med = wblock;
				block.off_snd_med = wblock.addr;
				inst.setSampleMiddle(winfo);
			}
		}
		
		winfo = inst.getSampleLow();
		if(winfo != null){
			WaveInfoBlock wblock = wavePool.addToPool(winfo);
			if(wblock != null){
				block.snd_lo = wblock;
				block.off_snd_lo = wblock.addr;
				inst.setSampleLow(winfo);
			}
		}
		
		winfo = inst.getSampleHigh();
		if(winfo != null){
			WaveInfoBlock wblock = wavePool.addToPool(winfo);
			if(wblock != null){
				block.snd_hi = wblock;
				block.off_snd_hi = wblock.addr;
				inst.setSampleHigh(winfo);
			}
		}
		
		block = instPool.assignToSlot(block, slot);
		return block.data;
	}
	
	public Z64Drum setDrum(Z64Drum drum, int minSlot, int maxSlot){
		//Add env
		EnvelopeBlock eblock = envPool.addToPool(drum.getEnvelope());
		
		//Add wave
		WaveInfoBlock wblock = wavePool.addToPool(drum.getSample());
		
		PercBlock[] blocks = drumPool.assignDrumToSlots(drum, minSlot, maxSlot);
		if(blocks == null) return null;
		for(int i = 0; i < blocks.length; i++){
			if(blocks[i] != null){
				//Link env and wave
				blocks[i].envelope = eblock;
				if(eblock != null){
					blocks[i].off_env = eblock.addr;
				}
				
				blocks[i].sample = wblock;
				if(wblock != null) {
					blocks[i].off_snd = wblock.addr;
				}
				
				drum = blocks[i].data;
			}
		}
		
		if(eblock != null) drum.setEnvelope(eblock.data);
		else drum.setEnvelope(null);
		if(wblock != null) drum.setSample(wblock.getWaveInfo());
		else drum.setSample(null);
		return drum;
	}
	
	public Z64SoundEffect setSFX(int slot, Z64SoundEffect sfx){
		WaveInfoBlock wblock = wavePool.addToPool(sfx.getSample());
		
		SFXBlock block = new SFXBlock(sfx);
		block = sfxPool.setToSlot(block, slot);
		if(block == null) return null;
		
		block.sample = wblock;
		if(wblock != null){
			block.off_snd = wblock.addr;
			block.data.setSample(wblock.getWaveInfo());
		}
		else{
			block.off_snd = 0;
			block.data.setSample(null);
		}
		
		return block.data;
	}
	
	public void setInstPresetEnumString(int idx, String val){
		instPool.setSlotEnumString(val, idx);
	}
	
	public void setDrumPresetEnumString(int idx, String val){
		drumPool.setSlotEnumString(val, idx);
	}
	
	public void setSFXPresetEnumString(int idx, String val){
		SFXBlock block = sfxPool.getSlot(idx);
		if(block != null){
			block.enm_str = val;
		}
	}
	
	/*----- Backdoor -----*/
	
	protected EnvelopeBlock addEnvelope(EnvelopeBlock env){
		return envPool.addToPool(env);
	}
	
	protected Z64WavePool getWavePool(){return wavePool;}
	protected Z64EnvPool getEnvPool(){return envPool;}
	protected Z64InstPool getInstPool(){return instPool;}
	protected Z64DrumPool getDrumPool(){return drumPool;}
	protected Z64SFXPool getSFXPool(){return sfxPool;}
	
	/*----- Conversion -----*/
	
	/*----- Definition -----*/
	
	/*----- Debug -----*/
	
	public void printMeTo(Writer out) throws IOException {
		// TODO Auto-generated method stub
		
	}

}
