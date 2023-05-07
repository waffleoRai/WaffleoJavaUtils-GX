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
	
	public List<PercBlock> getAllPercBlocks(){
		//TODO
		return null;
	}
	
	/*----- Setters -----*/
	
	public void updateMaps(){
		//TODO
	}

}
