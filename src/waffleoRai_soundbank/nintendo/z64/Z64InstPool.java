package waffleoRai_soundbank.nintendo.z64;

import java.util.List;

import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_soundbank.nintendo.z64.Z64BankBlocks.InstBlock;

class Z64InstPool {

	/*----- Instance Variables -----*/
	
	/*----- Init -----*/
	
	/*----- Read -----*/
	
	public InstBlock importInstFromBin(BufferReference input, int slot, boolean src64, boolean srcLE){
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
	
	public InstBlock[] getSlots(){
		//TODO
		return null;
	}
	
	public List<InstBlock> getAllInstBlocks(){
		//TODO
		return null;
	}
	
	/*----- Setters -----*/
	
	public void updateMaps(){
		//TODO
	}
	
}
