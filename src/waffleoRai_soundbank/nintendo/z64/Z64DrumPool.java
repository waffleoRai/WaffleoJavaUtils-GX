package waffleoRai_soundbank.nintendo.z64;

import java.util.List;

import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_soundbank.nintendo.z64.Z64BankBlocks.PercBlock;

class Z64DrumPool {
	
	/*----- Instance Variables -----*/
	
	/*----- Init -----*/
	
	/*----- Read -----*/
	
	public PercBlock importDrumFromBin(BufferReference input, int slot, boolean src64, boolean srcLE){
		//TODO
		//Will reject if local address is already here.
		//Takes care of all merging.
		
		return null;
	}
	
	/*----- Write -----*/
	
	public void relink(Z64WavePool wavePool, Z64EnvPool envPool, int waveRefType){
		//TODO
	}
	
	public boolean repackSerial(int section_start, boolean target_64){
		//TODO
		return false;
	}
	
	public FileBuffer serializePool(boolean target_64, boolean little_endian){
		//TODO
		//Includes table.
		return null;
	}
	
	/*----- Getters -----*/
	
	public int getUniqueDrumCount(){
		//TODO
		return 0;
	}
	
	public int getSerializedSlotCount(){
		//TODO
		return 0;
	}
	
	public int getDataSerializedSize(boolean target_64){
		//TODO
		return 0;
	}
	
	public int getTableSerializedSize(boolean target_64){
		//TODO
		return 0;
	}
	
	public int[][] getDrumRegionBoundaries(){
		//TODO
		return null;
	}
	
	public List<Z64Drum> getAllDrums(){
		//TODO
		return null;
	}
	
	public List<PercBlock> getAllPercBlocks(){
		//TODO
		return null;
	}
	
	public String getSlotEnumString(int slot){
		//TODO
		return null;
	}
	
	/*----- Setters -----*/
	
	public void updateMaps(){
		//TODO
	}

	public Z64Drum addToPool(Z64Drum drum){
		//TODO
		return null;
	}
	
	public PercBlock setSlot(PercBlock block, int slot){
		//TODO
		return null;
	}
	
	public PercBlock[] assignDrumToSlots(Z64Drum drum, int slotMin, int slotMax){
		//TODO
		//Provided drum ref must be EXACT INSTANCE of one in pool, otherwise will not work.
		return null;
	}
	
	public void setSlotEnumString(String value, int slot){
		//TODO
	}
	
}
