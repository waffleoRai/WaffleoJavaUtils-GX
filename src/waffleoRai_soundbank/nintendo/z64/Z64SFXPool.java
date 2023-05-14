package waffleoRai_soundbank.nintendo.z64;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_soundbank.nintendo.z64.Z64BankBlocks.SFXBlock;
import waffleoRai_soundbank.nintendo.z64.Z64BankBlocks.WaveInfoBlock;

class Z64SFXPool {
	
	/*----- Instance Variables -----*/
	
	private List<SFXBlock> pool;
	private Map<Integer, SFXBlock> mapLocalOff;
	
	private SFXBlock[] slots;
	
	/*----- Init -----*/
	
	public Z64SFXPool(int slotAlloc){
		if(slotAlloc < 4) slotAlloc = 4;
		//pool = new LinkedList<SFXBlock>();
		slots = new SFXBlock[slotAlloc];
		mapLocalOff = new HashMap<Integer, SFXBlock>();
	}
	
	/*----- Read -----*/
	
	public SFXBlock importSFXFromBin(BufferReference input, int slot, boolean src64, boolean srcLE){
		if(input == null) return null;
		int pos = (int)input.getBufferPosition();
		
		SFXBlock block = null;
		if(src64) block = SFXBlock.readFrom64(input);
		else block = SFXBlock.readFrom(input);
		if(block == null) return null;
		
		block.addr = pos;
		return setToSlot(block,slot);
	}
	
	/*----- Write -----*/
	
	public void relink(Z64WavePool wavePool, int waveRefType){
		//Takes the value in off_snd and uses this to replace all wave references
		
		for(SFXBlock block : pool){
			boolean nullref = false;
			switch(waveRefType){
			case Z64Bank.REFTYPE_UID:
				nullref = (block.off_snd == 0) || (block.off_snd == -1);
				break;
			case Z64Bank.MAPORDER_OFFSET_LOCAL:
				nullref = (block.off_snd <= 0);
				break;
			case Z64Bank.MAPORDER_OFFSET_GLOBAL:
				nullref = (block.off_snd < 0);
				break;
			}
			
			if(!nullref){
				WaveInfoBlock wblock = null;
				switch(waveRefType){
				case Z64Bank.REFTYPE_UID:
					wblock = wavePool.getByUID(block.off_snd);
					break;
				case Z64Bank.MAPORDER_OFFSET_LOCAL:
					wblock = wavePool.getByLocalOffset(block.off_snd);
					break;
				case Z64Bank.MAPORDER_OFFSET_GLOBAL:
					wblock = wavePool.getByWaveArcOffset(block.off_snd);
					break;
				}
				
				if(wblock != null){
					block.sample = wblock;
					block.data.setSample(wblock.getWaveInfo());
				}
				else{
					block.sample = null;
					block.data.setSample(null);
				}
			}
			else{
				block.sample = null;
				block.data.setSample(null);
			}
		}
		
	}
	
	public boolean repackSerial(int section_start, boolean target_64){
		if(slots == null) return false;
		int pos = section_start;
		for(SFXBlock block : pool) block.addr = 0; //So unslotted blocks have no address
		
		//By slot
		for(int i = 0; i < slots.length; i++){
			if(slots[i] != null) slots[i].addr = pos;
			pos += target_64 ? Z64BankBlocks.BLOCKSIZE_SFX_64 : Z64BankBlocks.BLOCKSIZE_SFX;
		}
		
		updateMaps();
		return true;
	}
	
	public FileBuffer serializePool(boolean target_64, boolean little_endian){
		if(slots == null) return null;
		int used_slots = getSerializedSlotCount();
		if(used_slots < 1) return null;
		
		int alloc = used_slots << 3;
		if(target_64) used_slots <<= 1;
		if((alloc & 0xf) != 0) alloc += 8;
		
		FileBuffer buff = new FileBuffer(alloc, !little_endian);
		for(int i = 0; i < used_slots; i++){
			if(slots[i] != null){
				slots[i].serializeTo(buff, false, target_64, little_endian);
			}
			else{
				buff.addToFile(0L);
				if(target_64) buff.addToFile(0L);
			}
		}
		
		if(buff.getFileSize() < alloc) buff.addToFile(0L);
		
		return buff;
	}
	
	/*----- Getters -----*/
	
	public int getSerializedSlotCount(){
		if(slots == null) return 0;
		for(int i = slots.length-1; i >= 0; i--){
			if(slots[i] != null) return (i+1);
		}
		return 0;
	}
	
	public int getTotalSerializedSize(boolean target_64){
		int count = getSerializedSlotCount();
		if(target_64) return count << 4;
		
		//Pad to 16...
		int size = count << 3;
		if((size & 0xf) != 0) size += 8;
		return size;
	}
	
	public SFXBlock getSlot(int slot){
		if(slots == null) return null;
		if((slot < 0) || (slot >= slots.length)) return null;
		return slots[slot];
	}
	
	public List<SFXBlock> getAllSFXBlocks(){
		if(pool.isEmpty()) return new LinkedList<SFXBlock>();
		List<SFXBlock> copy = new ArrayList<SFXBlock>(pool.size());
		copy.addAll(pool);
		return copy;
	}
	
	/*----- Setters -----*/
	
	public SFXBlock setToSlot(SFXBlock block, int slot){
		if(block == null) return null;
		
		if(slot < slots.length){
			if(slots[slot] == block){
				//It's already there...
				return block;
			}
		}
		
		//If slot < 0, only import to pool. Weirdo.
		boolean addme = true;
		for(SFXBlock other : pool){
			if(block == other){
				//Behavior depends on what's in slot...
				if(slot < 0){
					//Just for pool. Already in pool, so no point.
					addme = false; break;
				}
				else{
					//If same block already in the slot, no point.
					//If not, needs to be reinstantiated.
					block = new SFXBlock(block.data);
					block.enm_str = other.enm_str;
					block.off_snd = other.off_snd;
					block.sample = other.sample;
				}
			}
			else if(block.data.sfxEquals(other.data)){
				if(slot < 0){
					addme = false; 
					break;
				}
			}
		}
		
		if(addme) pool.add(block);
		
		if(slot >= 0){
			if(slot >= slots.length){
				//Reallocate.
				SFXBlock[] temp = slots;
				slots = new SFXBlock[slot+1];
				for(int i = 0; i < temp.length; i++) slots[i] = temp[i];
			}
			slots[slot] = block;
		}
		
		return block;
	}
	
	public void updateMaps(){
		mapLocalOff.clear();
		int i = 0;
		for(SFXBlock block : pool){
			block.pool_id = i++;
			if(block.addr > 0){
				mapLocalOff.put(block.addr, block);
			}
		}
	}

}
