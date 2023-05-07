package waffleoRai_soundbank.nintendo.z64;

import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_soundbank.nintendo.z64.Z64BankBlocks.EnvelopeBlock;

class Z64EnvPool {
	
	/*----- Instance Variables -----*/
	
	/*----- Init -----*/
	
	/*----- Read -----*/
	
	public EnvelopeBlock importInstFromBin(BufferReference input, boolean src64, boolean srcLE){
		//TODO
		//Will reject if local address is already here.
		//Takes care of all merging.
		
		return null;
	}
	
	/*----- Write -----*/
	
	public boolean repackSerial(int section_start){
		//TODO
		return false;
	}
	
	public FileBuffer serializePool(boolean little_endian){
		//TODO
		return null;
	}
	
	/*----- Getters -----*/
	
	public int getTotalSerializedSize(){
		//TODO
		return 0;
	}
	
	public EnvelopeBlock getByLocalOffset(int offset){
		//TODO
		return null;
	}
	
	/*----- Setters -----*/
	
	public void updateMaps(){
		//TODO
	}

}
