package waffleoRai_soundbank.nintendo.z64;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_soundbank.nintendo.z64.Z64BankBlocks.EnvelopeBlock;
import waffleoRai_soundbank.nintendo.z64.Z64BankBlocks.InstBlock;
import waffleoRai_soundbank.nintendo.z64.Z64BankBlocks.WaveInfoBlock;

class Z64InstPool {

	/*----- Instance Variables -----*/
	
	private List<InstBlock> pool;
	private Map<Integer, InstBlock> mapLocalOff;
	
	private InstBlock[] slots;
	private String[] enumStrings;
	
	/*----- Init -----*/
	
	public Z64InstPool(){
		pool = new LinkedList<InstBlock>();
		mapLocalOff = new HashMap<Integer, InstBlock>();
		slots = new InstBlock[126];
		enumStrings = new String[126];
	}
	
	/*----- Read -----*/
	
	public InstBlock importInstFromBin(BufferReference input, int slot, boolean src64, boolean srcLE){
		//Will reject if local address is already here.
		//Takes care of all merging.
		if(input == null) return null;
		
		int pos = (int)input.getBufferPosition();
		if((pos > 0) && mapLocalOff.containsKey(pos)) return null;
		InstBlock block = null;
		if(src64) block = InstBlock.readFrom64(input);
		else block = InstBlock.readFrom(input);
		block.addr = pos;
		
		return this.assignToSlot(block, slot);
	}
	
	/*----- Write -----*/
	
	public void cleanUp(){
		pool.clear();
		
		for(int i = 0; i < 126; i++){
			if(slots[i] != null){
				assignToSlot(slots[i], i);
			}
		}
		
		updateMaps();
	}
	
	private WaveInfoBlock getWaveToLink(Z64WavePool wavePool, int ref, int waveRefType){
		switch(waveRefType){
		case Z64Bank.REFTYPE_UID:
			if((ref == 0) || (ref == -1)) return null;
			break;
		case Z64Bank.MAPORDER_OFFSET_LOCAL:
			if(ref <= 0) return null;
			break;
		case Z64Bank.MAPORDER_OFFSET_GLOBAL:
			if(ref <= 0) return null;
			break;
		}
		
		WaveInfoBlock wblock = null;
		switch(waveRefType){
		case Z64Bank.REFTYPE_UID:
			wblock = wavePool.getByUID(ref);
			break;
		case Z64Bank.MAPORDER_OFFSET_LOCAL:
			wblock = wavePool.getByLocalOffset(ref);
			break;
		case Z64Bank.MAPORDER_OFFSET_GLOBAL:
			wblock = wavePool.getByWaveArcOffset(ref);
			break;
		}
		
		return wblock;
	}
	
	public void relink(Z64WavePool wavePool, Z64EnvPool envPool, int waveRefType){
		if(wavePool != null){
			for(InstBlock block : pool){
				WaveInfoBlock wblock = null;
				
				wblock = getWaveToLink(wavePool, block.off_snd_med, waveRefType);
				if(wblock != null){
					block.snd_med = wblock;
					block.data.setSampleMiddle(wblock.getWaveInfo());
				}
				else{
					block.snd_med = null;
					block.data.setSampleMiddle(null);
				}
				
				wblock = getWaveToLink(wavePool, block.off_snd_lo, waveRefType);
				if(wblock != null){
					block.snd_lo = wblock;
					block.data.setSampleLow(wblock.getWaveInfo());
				}
				else{
					block.snd_lo = null;
					block.data.setSampleLow(null);
				}
				
				wblock = getWaveToLink(wavePool, block.off_snd_hi, waveRefType);
				if(wblock != null){
					block.snd_hi = wblock;
					block.data.setSampleHigh(wblock.getWaveInfo());
				}
				else{
					block.snd_hi = null;
					block.data.setSampleHigh(null);
				}
			}
		}
		
		if(envPool != null){
			for(InstBlock block : pool){
				if(block.off_env > 0){
					EnvelopeBlock eblock = envPool.getByLocalOffset(block.off_env);
					block.envelope = eblock;
					block.data.setEnvelope(eblock.data);
				}
			}
		}
		
	}
	
	public boolean repackSerial(int section_start, boolean target_64){
		//Uses pool order.
		int pos = section_start;
		for(InstBlock block : pool){
			block.addr = pos;
			pos += target_64 ? Z64BankBlocks.BLOCKSIZE_INST_64 : Z64BankBlocks.BLOCKSIZE_INST;
		}
		
		return true;
	}
	
	public FileBuffer serializePool(boolean target_64, boolean little_endian){
		if(pool.isEmpty()) return null;
		int alloc = pool.size() << 5;
		if(target_64) alloc <<= 1;
		
		FileBuffer buff = new FileBuffer(alloc, !little_endian);
		for(InstBlock block : pool){
			block.serializeTo(buff, false, target_64, little_endian);
		}
		
		return buff;
	}
	
	/*----- Getters -----*/
	
	public int getUniqueInstCount(){
		return pool.size();
	}
	
	public int getSerializedSlotCount(){
		for(int i = slots.length - 1; i >= 0; i--){
			if(slots[i] != null) return i+1;
		}
		return 0;
	}
	
	public int getTotalSerializedSize(boolean target_64){
		int icount = pool.size();
		if(target_64) return icount << 6;
		return icount << 5;
	}
	
	public InstBlock[] getSlots(){
		InstBlock[] copy = new InstBlock[126];
		for(int i = 0; i < 126; i++) copy[i] = slots[i];
		return copy;
	}
	
	public InstBlock getSlot(int slot){
		if(slot < 0 || slot >= slots.length) return null;
		return slots[slot];
	}
	
	public List<InstBlock> getAllInstBlocks(){
		if(pool.isEmpty()) return new LinkedList<InstBlock>();
		List<InstBlock> copy = new ArrayList<InstBlock>(pool.size());
		copy.addAll(pool);
		return copy;
	}
	
	public String getSlotEnumString(int slot){
		if(slot < 0 || slot >= enumStrings.length) return null;
		return enumStrings[slot];
	}
	
	/*----- Setters -----*/
	
	public InstBlock addToPool(InstBlock block){
		if(block == null) return null;
		for(InstBlock other : pool){
			if(block == other) return other;
			if(block.data.instEquals(other.data)) return other;
		}
		
		pool.add(block);
		return block;
	}
	
	public InstBlock assignToSlot(InstBlock block, int slot){
		//Calls addToPool for instblock to check for redundancies.
		if(block == null) return null;
		addToPool(block);
		
		if(slot < 0 || slot >= slots.length) return null;
		slots[slot] = block;
		
		return slots[slot];
	}
	
	public void setSlotEnumString(String value, int slot){
		if(slot < 0 || slot >= enumStrings.length) return;
		enumStrings[slot] = value;
	}
	
	public void updateMaps(){
		mapLocalOff.clear();
		
		int i = 0;
		for(InstBlock block : pool){
			block.pool_id = i;
			if(block.addr > 0){
				mapLocalOff.put(block.addr, block);
			}
		}
	}
	
}
