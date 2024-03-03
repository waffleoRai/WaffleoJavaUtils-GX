package waffleoRai_soundbank.nintendo.z64;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_soundbank.nintendo.z64.Z64BankBlocks.EnvelopeBlock;

class Z64EnvPool {
	
	/*----- Instance Variables -----*/
	
	private List<EnvelopeBlock> pool;
	private Map<Integer, EnvelopeBlock> mapLocalOff;
	
	/*----- Init -----*/
	
	public Z64EnvPool(){
		pool = new LinkedList<EnvelopeBlock>();
		mapLocalOff = new HashMap<Integer, EnvelopeBlock>();
	}
	
	/*----- Read -----*/
	
	public EnvelopeBlock importFromBin(BufferReference input){
		if(input == null) return null;
		//Will reject if local address is already here.
		//Takes care of all merging.
		int addr = (int)input.getBufferPosition();
		EnvelopeBlock block = EnvelopeBlock.readFrom(input);
		for(EnvelopeBlock env : pool){
			if(env.addr == addr) return env;
			if(env.data.envEquals(block.data)) return env;
		}
		pool.add(block);
		block.addr = addr;
		
		return block;
	}
	
	/*----- Write -----*/
	
	public void mergeRedundantBlocks(){
		List<EnvelopeBlock> old = pool;
		pool = new LinkedList<EnvelopeBlock>();
		for(EnvelopeBlock e : old){
			boolean add = true;
			for(EnvelopeBlock other : pool){
				if(e == other){
					add = false;
					break;
				}
				if(e.data.envEquals(other.data)){
					add = false;
					break;
				}
			}
			if(add) pool.add(e);
		}
		
		updateMaps();
	}
	
	public boolean repackSerial(int section_start){
		int pos = section_start;
		for(EnvelopeBlock e : pool){
			e.addr = pos;
			pos += e.serialSize();
			pos = (pos + 0xf) & ~0xf;
		}
		return true;
	}
	
	public FileBuffer serializePool(boolean little_endian){
		int alloc = getTotalSerializedSize();
		FileBuffer buff = new FileBuffer(alloc, !little_endian);
		for(EnvelopeBlock e : pool){
			e.serializeTo(buff, false, false, little_endian);
			while((buff.getFileSize() & 0xf) != 0) buff.addToFile((byte)0);
		}
		return buff;
	}
	
	/*----- Getters -----*/
	
	public int getEnvelopeCount(){return pool.size();}
	
	public int getTotalSerializedSize(){
		int size = 0;
		for(EnvelopeBlock env : pool){
			size += env.serialSize();
			size = (size + 0xf) & ~0xf;
		}
		return size;
	}
	
	public EnvelopeBlock getByLocalOffset(int offset){
		return mapLocalOff.get(offset);
	}
	
	public EnvelopeBlock findMatchInPool(Z64Envelope env){
		if(env == null) return null;
		for(EnvelopeBlock other : pool){
			if(other.data == null) continue;
			if(env == other.data) return other;
			if(env.envEquals(other.data)) return other;
		}
		return null;
	}
	
	public List<EnvelopeBlock> getAllEnvBlocks(){
		if(pool.isEmpty()) return new LinkedList<EnvelopeBlock>();
		List<EnvelopeBlock> list = new ArrayList<EnvelopeBlock>(pool.size());
		list.addAll(pool);
		return list;
	}
	
	public Z64EnvPool copy() {
		Z64EnvPool copy = new Z64EnvPool();
		for(EnvelopeBlock b : this.pool) {
			EnvelopeBlock bcpy = b.copy();
			copy.addToPool(bcpy);
		}
		
		copy.updateMaps();
		return copy;
	}
	
	/*----- Setters -----*/
	
 	public EnvelopeBlock addToPool(EnvelopeBlock block){
		if(block == null) return null;
		//Handles merging
		//Check if identical envelope is already in pool.
		for(EnvelopeBlock other : pool){
			if(other == block) return other;
			if(other.data.envEquals(block.data)) return other;
		}
		pool.add(block);
		
		return block;
	}
	
	public EnvelopeBlock addToPool(Z64Envelope env){
		if(env == null) return null;
		//Handles merging
		EnvelopeBlock eblock = new EnvelopeBlock(env);
		return addToPool(eblock);
	}
	
	public void updateMaps(){
		mapLocalOff.clear();
		int i = 0;
		for(EnvelopeBlock block : pool){
			block.pool_id = i++;
			if(block.addr > 0){
				mapLocalOff.put(block.addr, block);
			}
		}
	}
	
	public void clearAll(){
		mapLocalOff.clear();
		pool.clear();
	}

}
