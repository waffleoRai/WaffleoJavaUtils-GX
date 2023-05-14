package waffleoRai_soundbank.nintendo.z64;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_soundbank.nintendo.z64.Z64BankBlocks.EnvelopeBlock;
import waffleoRai_soundbank.nintendo.z64.Z64BankBlocks.PercBlock;
import waffleoRai_soundbank.nintendo.z64.Z64BankBlocks.WaveInfoBlock;

class Z64DrumPool {
	
	/*----- Instance Variables -----*/
	
	private List<Z64Drum> pool;
	private Map<Integer, PercBlock> mapLocalOff;
	
	private PercBlock[] slots;
	private String[] enumStrings;
	
	/*----- Inner Classes -----*/
	
	public static class DrumRegionInfo{
		public Z64Drum drum;
		public byte minNote;
		public byte maxNote;
	}
	
	/*----- Init -----*/
	
	public Z64DrumPool(){
		pool = new LinkedList<Z64Drum>();
		slots = new PercBlock[64];
		enumStrings = new String[64];
		mapLocalOff = new HashMap<Integer, PercBlock>();
	}
	
	/*----- Read -----*/
	
	public PercBlock importDrumFromBin(BufferReference input, int slot, boolean src64, boolean srcLE){
		if(input == null) return null;
		int pos = (int)input.getBufferPosition();
		
		if(mapLocalOff.containsKey(pos)) return null;
		if(slot < 0 || slot >= slots.length) return null;
		
		PercBlock block = null;
		if(src64) block = PercBlock.readFrom64(input, slot);
		else block = PercBlock.readFrom(input, slot);
		
		return setSlot(block, slot);
	}
	
	/*----- Write -----*/
	
	public void cleanUp(){
		pool.clear();
		
		for(int i = 0; i < 64; i++){
			//This takes care of merging.
			setSlot(slots[i], i);
		}
		
		updateMaps();
	}
	
	public void relink(Z64WavePool wavePool, Z64EnvPool envPool, int waveRefType){
		//Envpool can be null. If that is the case, don't mess with the env links.
		if(wavePool != null){
			//Clear links
			for(Z64Drum drum : pool){
				drum.setSample(null);
			}
			
			for(int i = 0; i < slots.length; i++){
				if(slots[i] == null) continue;
				
				boolean nullref = false;
				switch(waveRefType){
				case Z64Bank.REFTYPE_UID:
					nullref = (slots[i].off_snd == 0) || (slots[i].off_snd == -1);
					break;
				case Z64Bank.MAPORDER_OFFSET_LOCAL:
					nullref = (slots[i].off_snd <= 0);
					break;
				case Z64Bank.MAPORDER_OFFSET_GLOBAL:
					nullref = (slots[i].off_snd < 0);
					break;
				}
				
				if(!nullref){
					WaveInfoBlock wblock = null;
					switch(waveRefType){
					case Z64Bank.REFTYPE_UID:
						wblock = wavePool.getByUID(slots[i].off_snd);
						break;
					case Z64Bank.MAPORDER_OFFSET_LOCAL:
						wblock = wavePool.getByLocalOffset(slots[i].off_snd);
						break;
					case Z64Bank.MAPORDER_OFFSET_GLOBAL:
						wblock = wavePool.getByWaveArcOffset(slots[i].off_snd);
						break;
					}
					
					if(wblock != null){
						slots[i].sample = wblock;
						slots[i].data.setSample(wblock.getWaveInfo());
					}
					else{
						slots[i].sample = null;
						slots[i].data.setSample(null);
					}
				}
				else{
					slots[i].sample = null;
					slots[i].data.setSample(null);
				}
				
			}
		}
		
		if(envPool != null){
			for(Z64Drum drum : pool){
				drum.setEnvelope(null);
			}
			
			for(int i = 0; i < slots.length; i++){
				if(slots[i] == null) continue;
				
				if(slots[i].off_env > 0){
					EnvelopeBlock eblock = envPool.getByLocalOffset(slots[i].off_env);
					slots[i].envelope = eblock;
					slots[i].data.setEnvelope(eblock.data);
				}
			}
		}
	}
	
	public boolean repackSerial(int section_start, boolean target_64){
		int serCount = getSerializedSlotCount();
		if(serCount < 1) return false;
		
		int pos = section_start;
		for(int i = 0; i < serCount; i++){
			if(slots[i] != null){
				slots[i].addr = pos;
				pos += target_64 ? Z64BankBlocks.BLOCKSIZE_PERC_64 : Z64BankBlocks.BLOCKSIZE_PERC;
			}
		}
		
		updateMaps();
		return true;
	}
	
	public FileBuffer serializePool(boolean target_64, boolean little_endian){
		//Includes table.
		int alloc = getDataSerializedSize(target_64) + getTableSerializedSize(target_64);
		if(alloc < 1) return null;
		
		FileBuffer buff = new FileBuffer(alloc, !little_endian);
		for(int i = 0; i < 64; i++){
			if(slots[i] != null){
				slots[i].serializeTo(buff, false, target_64, little_endian);
			}
		}
		
		int sslots = getSerializedSlotCount();
		for(int i = 0; i < sslots; i++){
			if(slots[i] != null){
				if(!target_64){
					buff.addToFile(slots[i].addr);
				}
				else{
					buff.addToFile((long)slots[i].addr);
				}
			}
		}
		while(buff.getFileSize() < alloc) buff.addToFile(4);
		
		return buff;
	}
	
	/*----- Getters -----*/
	
	public int getUniqueDrumCount(){
		return pool.size();
	}
	
	public int getSerializedSlotCount(){
		for(int i = 63; i >= 0; i--){
			if(slots[i] != null) return (i+1);
		}
		return 0;
	}
	
	public int getDataSerializedSize(boolean target_64){
		int slotcount = 0;
		for(int i = 0; i < 64; i++){
			if(slots[i] != null) slotcount++;
		}

		if(target_64) return slotcount << 5;
		return slotcount << 4;
	}
	
	public int getTableSerializedSize(boolean target_64){
		int slotcount = getSerializedSlotCount();
		int tsize = target_64 ? (slotcount << 3) : (slotcount << 2);
		tsize = (tsize + 0xf) & ~0xf;
		return tsize;
	}
	
	public PercBlock getByLocalOffset(int offset){
		return mapLocalOff.get(offset);
	}
	
	public List<DrumRegionInfo> getDrumRegions(){
		List<DrumRegionInfo> list = new LinkedList<DrumRegionInfo>();
		
		int min = 0;
		Z64Drum lastDrum = null;
		for(int i = 0; i < 64; i++){
			if(slots[i] != null){
				if(lastDrum != null){
					if(lastDrum != slots[i].data){
						//Region end. Add.
						DrumRegionInfo reg = new DrumRegionInfo();
						reg.drum = lastDrum;
						reg.minNote = (byte)min;
						reg.maxNote = (byte)(i-1);
						min = i;
						lastDrum = slots[i].data;
						list.add(reg);
					}
					//Otherwise it's the same... do nothing.
				}
				else{
					lastDrum = slots[i].data;
					min = i;
				}
			}
			else{
				if(lastDrum != null){
					//Region end. Add.
					DrumRegionInfo reg = new DrumRegionInfo();
					reg.drum = lastDrum;
					reg.minNote = (byte)min;
					reg.maxNote = (byte)(i-1);
					min = i+1;
					lastDrum = null;
					list.add(reg);
				}
				else{
					//Just up min
					min = i+1;
				}
			}
		}
		
		//Add last region, if not added...
		if(lastDrum != null){
			DrumRegionInfo reg = new DrumRegionInfo();
			reg.drum = lastDrum;
			reg.minNote = (byte)min;
			reg.maxNote = (byte)63;
			list.add(reg);
		}
		
		return list;
	}
	
	public List<Z64Drum> getAllDrums(){
		if(pool.isEmpty()) return new LinkedList<Z64Drum>();
		List<Z64Drum> copy = new ArrayList<Z64Drum>(pool.size());
		copy.addAll(pool);
		return copy;
	}
	
	public List<PercBlock> getAllPercBlocks(){
		List<PercBlock> blocks = new ArrayList<PercBlock>(64);
		for(int i = 0; i < slots.length; i++){
			if(slots[i] != null) blocks.add(slots[i]);
		}
		return blocks;
	}
	
	public String getSlotEnumString(int slot){
		if(slot < 0 || slot >= enumStrings.length) return null;
		return enumStrings[slot];
	}
	
	public PercBlock getSlot(int slot){
		if(slot < 0) return null;
		if(slot >= slots.length) return null;
		return slots[slot];
	}
	
	/*----- Setters -----*/
	
	public void updateMaps(){
		mapLocalOff.clear();
		
		int i = 0;
		for(Z64Drum drum : pool){
			drum.setPoolID(i++);
		}
		
		i = 0;
		for(int j = 0; j < 64; j++){
			if(slots[j] != null){
				slots[j].pool_id = i++;
				if(slots[j].addr > 0){
					mapLocalOff.put(slots[j].addr, slots[j]);
				}
			}
		}
	}

	public Z64Drum addToPool(Z64Drum drum){
		if(drum == null) return null;
		for(Z64Drum other : pool){
			if(drum == other) return other;
			if(drum.drumEquals(other)) return other;
		}
		
		pool.add(drum);
		return drum;
	}
	
	public PercBlock setSlot(PercBlock block, int slot){
		if(block == null){
			slots[slot] = null;
			return null;
		}
		
		//Make sure backing drum is in pool...
		block.data = addToPool(block.data);
		
		if(slot < 0 || slot >= slots.length) return null;
		
		slots[slot] = block;
		block.index = slot;
		return slots[slot];
	}
	
	public PercBlock[] assignDrumToSlots(Z64Drum drum, int slotMin, int slotMax){
		drum = addToPool(drum);
		if(drum == null) return null;
		
		if(slotMin < 0) slotMin = 0;
		if(slotMax >= 64) slotMax = 63;
		
		int slotAdd = slotMax - slotMin + 1;
		if(slotAdd < 1) return null;
		
		PercBlock[] newBlocks = new PercBlock[slotAdd];
		for(int i = 0; i < slotAdd; i++){
			int slot = i + slotMin;
			PercBlock block = new PercBlock(drum, slot);
			slots[slot] = block;
			newBlocks[i] = block;
		}
		
		return newBlocks;
	}
	
	public void setSlotEnumString(String value, int slot){
		if(slot < 0 || slot >= enumStrings.length) return;
		enumStrings[slot] = value;
	}
	
}
