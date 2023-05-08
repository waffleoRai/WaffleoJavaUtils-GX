package waffleoRai_soundbank.nintendo.z64;

import java.util.List;

import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_soundbank.nintendo.z64.Z64BankBlocks.SFXBlock;

class Z64SFXPool {
	
	/*----- Instance Variables -----*/
	
	public Z64SFXPool(int slotAlloc){
		//TODO
	}
	
	/*----- Init -----*/
	
	/*----- Read -----*/
	
	public SFXBlock importSFXFromBin(BufferReference input, int slot, boolean src64, boolean srcLE){
		//TODO
		return null;
	}
	
	/*----- Write -----*/
	
	public void relink(Z64WavePool wavePool, int waveRefType){
		//TODO
	}
	
	public boolean repackSerial(int section_start, boolean target_64){
		//TODO
		return false;
	}
	
	public FileBuffer serializePool(boolean target_64, boolean little_endian){
		//TODO
		return null;
	}
	
	/*----- Getters -----*/
	
	public int getSerializedSlotCount(){
		//TODO
		return 0;
	}
	
	public int getTotalSerializedSize(boolean target_64){
		//TODO
		return 0;
	}
	
	public SFXBlock getSlot(int slot){
		//TODO
		return null;
	}
	
	public List<SFXBlock> getAllSFXBlocks(){
		//TODO
		return null;
	}
	
	/*----- Setters -----*/
	
	public SFXBlock setToSlot(SFXBlock block, int slot){
		//TODO
		return null;
	}
	
	public void updateMaps(){
		//TODO
	}

}
