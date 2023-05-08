package waffleoRai_soundbank.nintendo.z64;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import waffleoRai_Sound.nintendo.Z64WaveInfo;
import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_soundbank.nintendo.z64.Z64BankBlocks.LoopBlock;
import waffleoRai_soundbank.nintendo.z64.Z64BankBlocks.Predictor;
import waffleoRai_soundbank.nintendo.z64.Z64BankBlocks.WaveInfoBlock;

/**
 * Class for managing all wave and child blocks in a N64 (Zelda-style, but may be modified
 * to be compatible with other games) soundfont.
 * <br>Manages wave blocks, predictors (ADPCM codec info), and loop blocks.
 * @author Blythe Hospelhorn
 * @version 1.0.1
 * @since May 7, 2023
 */
class Z64WavePool {
	
	/*----- Instance Variables -----*/
	
	private List<WaveInfoBlock> pool;
	
	private Map<Integer, WaveInfoBlock> mapUID;
	private Map<Integer, WaveInfoBlock> mapLocalOff;
	private Map<Integer, WaveInfoBlock> mapWarcOff;
	
	private List<Predictor> predPool;
	private Map<Integer, Predictor> pmapLocalOff;
	
	private List<LoopBlock> loopPool;
	private Map<Integer, LoopBlock> lmapLocalOff;
	
	/*----- Init -----*/
	
	/**
	 * Construct an empty wave pool. Internal lists and maps are initialized,
	 * but do not have any contents.
	 * @since 1.0.0
	 */
	public Z64WavePool(){
		pool = new LinkedList<WaveInfoBlock>();
		mapUID = new HashMap<Integer, WaveInfoBlock>();
		mapLocalOff = new HashMap<Integer, WaveInfoBlock>();
		mapWarcOff = new HashMap<Integer, WaveInfoBlock>();
		
		predPool = new LinkedList<Predictor>();
		pmapLocalOff = new HashMap<Integer, Predictor>();
		
		loopPool = new LinkedList<LoopBlock>();
		lmapLocalOff = new HashMap<Integer, LoopBlock>();
	}
	
	/*----- Getters -----*/
	
	/**
	 * Get the number of wave info blocks stored in this pool.
	 * This method does not look for redundant blocks. It only counts
	 * the number of info blocks that are stored at this time.
	 * @return Wave block count
	 * @since 1.0.0
	 */
	public int getWaveCount(){
		return pool.size();
	}
	
	/**
	 * Get the number of ADPCM predictor blocks stored in this pool.
	 * This method does not look for redundant blocks. It only counts
	 * the number of info blocks that are stored at this time.
	 * @return Predictor block count
	 * @since 1.0.0
	 */
	public int getPredictorCount(){
		return predPool.size();
	}
	
	/**
	 * Get the number of loop blocks stored in this pool.
	 * This method does not look for redundant blocks. It only counts
	 * the number of info blocks that are stored at this time.
	 * @return Loop block count
	 * @since 1.0.0
	 */
	public int getLoopBlockCount(){
		return loopPool.size();
	}
	
	/**
	 * Get the total serialized size of this wave pool in its
	 * current state.
	 * @param target_64 Whether to use 64-bit offset fields (true)
	 * or 32-bit offset fields (false). The N64 native form is 32-bit.
	 * @return The calculated size of all wave, predictor, and loop blocks
	 * contained in this pool once serialized.
	 * @since 1.0.0
	 */
	public int getTotalSerializedSize(boolean target_64){
		//INCLUDES loop and predictor blocks
		//Does not check uniqueness. Call mergeRedundantBlocks first if you're worried about that.
		int size = 0;
		int wsize = target_64 ? Z64BankBlocks.BLOCKSIZE_WAVE_64 : Z64BankBlocks.BLOCKSIZE_WAVE;
		size += wsize * (pool.size());
		
		for(Predictor p : predPool){
			size += p.serialSize(); //Not dependent on target ptr size.
		}
		
		for(LoopBlock l : loopPool){
			size += l.serialSize(); //Not dependent on target ptr size.
		}
		
		return size;
	}
	
	/**
	 * Retrieve a <code>WaveInfoBlock</code> in pool by its unique ID, if
	 * assigned. 
	 * <br>NOTE: If <code>updateMaps()</code> has not been called since a block
	 * was added, it will likely not be mapped properly and this method will
	 * probably return <code>null</code>.
	 * @param uid 32-bit unique identifier assigned to wave to retrieve. Values of
	 * 0 and -1 (<code>0xffffffff</code>) are reserved as "unset", and providing 
	 * either of these will return <code>null</code>.
	 * @return <code>WaveInfoBlock</code> containing information about wave, or
	 * <code>null</code> if ID could not be matched.
	 * @since 1.0.0
	 */
	public WaveInfoBlock getByUID(int uid){
		return mapUID.get(uid);
	}
	
	/**
	 * Retrieve a <code>WaveInfoBlock</code> in pool by its local offset, if
	 * known. 
	 * <br>NOTE: If <code>updateMaps()</code> has not been called since a block
	 * was added, it will likely not be mapped properly and this method will
	 * probably return <code>null</code>.
	 * @param offset Offset of the wave info block of interest relative to the
	 * start of its original containing soundfont file or struct.
	 * @return <code>WaveInfoBlock</code> containing information about wave, or
	 * <code>null</code> if offset could not be matched.
	 * @since 1.0.0
	 */
	public WaveInfoBlock getByLocalOffset(int offset){
		return mapLocalOff.get(offset);
	}
	
	/**
	 * Retrieve a <code>WaveInfoBlock</code> in pool by its position within
	 * its containing wave archive/samplebank, if known.
	 * <br>NOTE: If <code>updateMaps()</code> has not been called since a block
	 * was added, it will likely not be mapped properly and this method will
	 * probably return <code>null</code>.
	 * @param offset Offset of the wave info block of interest relative to the
	 * start of the containing wave archive/samplebank binary data.
	 * @return <code>WaveInfoBlock</code> containing information about wave, or
	 * <code>null</code> if offset could not be matched.
	 * @since 1.0.0
	 */
	public WaveInfoBlock getByWaveArcOffset(int offset){
		return mapWarcOff.get(offset);
	}
	
	/**
	 * Get the wave contents of the pool as an ordered list of <code>WaveInfoBlock</code>s.
	 * These structures cannot be used directly outside this package, but they contain
	 * <code>Z64WaveInfo</code> structures that can. The order reflects the order they
	 * are stored in the pool, and the order they would be re-serialized in.
	 * @return List containing all <code>WaveInfoBlock</code>s in the pool, in the order they
	 * are stored. List is a copy, this method does not return a reference to any internal
	 * structures within the pool. If the pool does not have any wave, an empty list is returned.
	 * @since 1.0.0
	 */
	public List<WaveInfoBlock> getAllWaveBlocks(){
		int wcount = pool.size();
		if(wcount < 1) return new LinkedList<WaveInfoBlock>();
		List<WaveInfoBlock> list = new ArrayList<WaveInfoBlock>(wcount);
		list.addAll(pool);
		return list;
	}
	
	/**
	 * Get all wave info structures contained within this pool, in the order
	 * they are stored. This returns the wave infos directly, not wrapped in their
	 * blocks. This is useful for accessing data externally.
	 * @return List containing wave info for all waves in this pool. If the pool
	 * has no waves, this method returns an empty list.
	 * @since 1.0.0
	 */
	public List<Z64WaveInfo> getAllWaves(){
		int wcount = pool.size();
		if(wcount < 1) return new LinkedList<Z64WaveInfo>();
		List<Z64WaveInfo> list = new ArrayList<Z64WaveInfo>(wcount);
		for(WaveInfoBlock block : pool){
			if(block.wave_info != null) list.add(block.wave_info);
		}
		return list;
	}
	
	/*----- Setters -----*/
	
	/**
	 * Refresh the internal offset and UID maps to reflect
	 * the current contents' state.
	 * <br>Pool map lookups should be considered undefined if this method
	 * has not been called since an external modification has been made to the pool
	 * or any of its contents.
	 * @since 1.0.0
	 */
	public void updateMaps(){
		//Waves
		mapUID.clear();
		mapLocalOff.clear();
		mapWarcOff.clear();
		
		int i = 0;
		for(WaveInfoBlock block : pool){
			Z64WaveInfo winfo = block.getWaveInfo();
			if(winfo != null){
				int uid = winfo.getUID();
				if(uid != 0 && uid != -1){
					mapUID.put(uid, block);
				}
				
				int woff = winfo.getWaveOffset();
				if(woff >= 0){
					mapWarcOff.put(woff, block);
				}
				winfo.setPoolID(i);
			}
			
			int loff = block.addr;
			if(loff > 0){
				mapLocalOff.put(loff, block);
			}
			block.pool_id = i++;
		}
		
		//Predictors
		i = 0;
		pmapLocalOff.clear();
		for(Predictor pred : predPool){
			int loff = pred.addr;
			if(loff > 0){
				pmapLocalOff.put(loff, pred);
			}
			pred.pool_id = i++;
		}
		
		//Loops
		i = 0;
		lmapLocalOff.clear();
		for(LoopBlock block : loopPool){
			int loff = block.addr;
			if(loff > 0){
				lmapLocalOff.put(loff, block);
			}
			block.pool_id = i++;
		}
	}
	
	/**
	 * Add a new wave directly to the pool. This function will attempt to wrap
	 * the wave data into a <code>WaveInfoBlock</code>, and return that block.
	 * Any data already in the pool will not be added again - instead a 
	 * reference to the existing record will be returned.
	 * @param waveinfo Wave record to add.
	 * @return <code>WaveInfoBlock</code> that is part of the pool and contains
	 * the requested wave data, or <code>null</code> if addition failed.
	 * @since 1.0.0
	 */
	public WaveInfoBlock addToPool(Z64WaveInfo waveinfo){
		if(waveinfo == null) return null;
		
		//First, see if already in the pool.
		for(WaveInfoBlock block : pool){
			if(waveinfo.wavesEqual(block.wave_info, false)){
				return block;
			}
		}
		
		//Generate new block struct
		//The WaveInfoBlock constructor also constructs the loop and pred blocks,
		//	and adds the data from the winfo
		WaveInfoBlock block = new WaveInfoBlock(waveinfo);
		if(!addToPool(block, false)) return null;
		
		return block;
	}
	
	/**
	 * Add a <code>WaveInfoBlock</code> as well as its ADPCM predictor block
	 * and loop block if present, to this pool. If identical ADPCM predictor
	 * block is already present, the reference will be replaced with the instance
	 * already in the pool.
	 * @param block <code>WaveInfoBlock</code> to add.
	 * @param rejectSameLocalAddr Whether or not to reject addition if there
	 * is already a <code>WaveInfoBlock</code> with the same local offset
	 * (offset relative to bank struct start) in the pool.
	 * @return <code>true</code> if addition was successful. 
	 * <code>false</code> if addition was rejected or failed.
	 * @since 1.0.0
	 */
	public boolean addToPool(WaveInfoBlock block, boolean rejectSameLocalAddr){
		//Does NOT automatically map. Need to remember to update manually.
		//Also adds predictor and loop, if applicable
		if(block == null) return false;
		
		//If rejectSameLocalAddr, will refuse to add new block(s) if there is already one with same local addr.
		if(rejectSameLocalAddr && (block.addr > 0)){
			for(WaveInfoBlock wib : pool){
				if(wib.addr <= 0) continue; //Unassigned. Ignore.
				if(wib.addr == block.addr){
					return false;
				}
			}
		}
		pool.add(block);
		
		//Add predictor and loop.
		if(block.loop != null){
			addLoop(block.loop, true, rejectSameLocalAddr);
		}
		
		if(block.pred != null){
			Predictor addedpred = addPredictor(block.pred, true, rejectSameLocalAddr);
			if(addedpred != null) block.pred = addedpred;
		}
		
		return true; //Only indicates if main wave was added.
	}
	
	/**
	 * Add a <code>Predictor</code> block to the pool. 
	 * @param block <code>Predictor</code> to add.
	 * @param mapImmediately Whether or not to immediately map the added block
	 * by its local offset so it can be easily looked up.
	 * @param rejectSameLocalAddr Whether or not to reject addition if there
	 * is already a <code>Predictor</code> with the same local offset
	 * (offset relative to bank struct start) in the pool.
	 * @return Reference to <code>Predictor</code> in pool that matches
	 * the one provided. If the <code>Predictor</code> wasn't already in the pool,
	 * return is the same. If <code>Predictor</code> was in the pool, return value
	 * is the match. If addition is rejected, <code>null</code> is returned.
	 * @since 1.0.0
	 */
	public Predictor addPredictor(Predictor block, boolean mapImmediately, boolean rejectSameLocalAddr){
		if(block == null) return null;
		
		for(Predictor other : predPool){
			if(block == other) return block;
			
			if(block.isEquivalent(other)){
				//Should be merged. Don't add and offer up match.
				return other;
			}
			
			if(rejectSameLocalAddr && (block.addr > 0) && (other.addr > 0)){
				if(block.addr == other.addr){
					//Rejection.
					return null;
				}
			}
		}
		
		//If all cleared, then need to add new one.
		predPool.add(block);
		if(mapImmediately && block.addr > 0) {
			this.pmapLocalOff.put(block.addr, block);
		}
		
		return block;
	}
	
	/**
	 * Add a <code>LoopBlock</code> block to the pool. 
	 * @param block <code>LoopBlock</code> to add.
	 * @param mapImmediately Whether or not to immediately map the added block
	 * by its local offset so it can be easily looked up.
	 * @param rejectSameLocalAddr Whether or not to reject addition if there
	 * is already a <code>LoopBlock</code> with the same local offset
	 * (offset relative to bank struct start) in the pool.
	 * @return <code>true</code> if addition was successful. 
	 * <code>false</code> if addition was rejected or failed.
	 * @since 1.0.0
	 */
	public boolean addLoop(LoopBlock block, boolean mapImmediately, boolean rejectSameLocalAddr){
		if(block == null) return false;
		for(LoopBlock b : loopPool){
			//Don't re-add same instances.
			if(b == block) return false;
			
			if(rejectSameLocalAddr && (block.addr > 0) && (b.addr > 0)){
				if(b.addr == block.addr) return false;
			}
		}
		loopPool.add(block);
		if(mapImmediately && block.addr > 0) {
			this.lmapLocalOff.put(block.addr, block);
		}
		
		return true;
	}
	
	/**
	 * Clear all blocks from the pool, including the main wave blocks,
	 * loop blocks, and ADPCM predictor blocks. This completely empties
	 * the pool.
	 * @since 1.0.0
	 */
	public void clearPool(){
		pool.clear();
		mapUID.clear();
		mapLocalOff.clear();
		mapWarcOff.clear();
		predPool.clear();
		pmapLocalOff.clear();
		loopPool.clear();
		lmapLocalOff.clear();
	}
	
	/*----- Read -----*/
	
	/**
	 * Read wave info from serialized binary data into the pool. This adds
	 * the wave block along with its ADPCM predictor block and loop block
	 * (if applicable) to the pool.
	 * New blocks are merged into blocks that are already in the pool, if
	 * equivalent.
	 * @param data Reference to start of wave block data to import.
	 * @param target_64 Whether references in input data are 64-bits or 32-bits.
	 * N64 natively uses 32-bit pointers.
	 * @param little_endian Whether the input uses Little-Endian byte order (true)
	 * or Big-Endian (false). N64 itself uses Big-Endian ordering.
	 * @return The <code>WaveInfoBlock</code> that was read and added.
	 * @since 1.0.0
	 */
	public WaveInfoBlock importWaveData(BufferReference data, boolean target_64, boolean little_endian){
		//This wraps the block reader BUT it also allows for more efficient loop/pred lookup and merging.
		if(data == null) return null;
		
		long addr = data.getBufferPosition();
		
		//Already loaded?
		WaveInfoBlock block = mapLocalOff.get((int)addr);
		if(block != null) return block;
		
		//New...
		data.setByteOrder(!little_endian);
		if(!target_64){
			block = WaveInfoBlock.readFrom(data);
		}
		else{
			//Modified reader...
			block = WaveInfoBlock.read64(data);
		}
		block.addr = (int)addr;
		
		//Find the child blocks...
		// Also need to update structs within Pred/Loop blocks??
		if(block.loop_offset > 0){
			LoopBlock loop = lmapLocalOff.get(block.loop_offset);
			if(loop == null){
				FileBuffer src = data.getBuffer();
				loop = LoopBlock.readFrom(src.getReferenceAt(block.loop_offset));
			}
			block.loop = loop;
			if(block.wave_info != null && loop != null){
				block.wave_info.setLoopStart(loop.start);
				block.wave_info.setLoopEnd(loop.end);
				block.wave_info.setLoopCount(loop.count);
				block.wave_info.setLoopState(loop.state_vals);
			}
		}
		
		if(block.pred_offset > 0){
			Predictor pred = pmapLocalOff.get(block.pred_offset);
			if(pred == null){
				FileBuffer src = data.getBuffer();
				pred = Predictor.readFrom(src.getReferenceAt(block.pred_offset));
			}
			block.pred = pred;
			if(block.wave_info != null && pred != null){
				block.wave_info.setADPCMBook(block.pred.loadToTable());
			}
		}
		
		
		//Add using add func...
		if(!addToPool(block, true)) return null;
		
		//Map to local addr for easy parser lookup...
		mapLocalOff.put(block.addr, block);
		
		return block;
	}
	
	/*----- Write -----*/
	
	/**
	 * Merge together blocks in the pool that are identical or functionally
	 * equivalent.
	 * @since 1.0.0
	 */
	public void mergeRedundantBlocks(){
		//Also removes unused loops and predictors
		List<WaveInfoBlock> oldpool = pool;
		pool = new LinkedList<WaveInfoBlock>();
		
		predPool.clear();
		loopPool.clear();
		
		for(WaveInfoBlock block : oldpool){
			//Is an equivalent already in the new pool?
			boolean addme = true;
			for(WaveInfoBlock other : pool){
				if(other == block){
					addme = false;
					break;
				}
				if(block.wave_info != null && other.wave_info != null){
					if(block.wave_info.getWaveOffset() == other.wave_info.getWaveOffset()){
						if(block.wave_info.getWaveSize() == other.wave_info.getWaveSize()){
							addme = false;
							break;
						}
					}
				}
			}
			
			//Add the pred and loop back too, if not already there.
			if(addme){
				pool.add(block);
				
				if(block.pred != null){
					Predictor p = addPredictor(block.pred, false, true);
					if(p != null){
						block.pred = p;
						block.pred_offset = p.addr;
					}
					else block.pred_offset = -1;
				}
				
				if(block.loop != null){
					addLoop(block.loop, false, true);
				}
			}
		}
		
		updateMaps();
	}

	/**
	 * Rearrange the output order of the blocks to be in order
	 * of currently set local address (ie. so that they end up in the same
	 * order they were read in). Blocks with no local address set are discarded.
	 * @since 1.0.0
	 */
	public void reorderByLocalAddress(){
		//For matching, mostly. Does sorta the opposite of repackSerial()
		updateMaps();
		
		int wcount = pool.size();
		if(wcount < 1) return;
		
		List<Integer> keys = new ArrayList<Integer>(wcount);
		keys.addAll(mapLocalOff.keySet());
		Collections.sort(keys);
		
		pool.clear();
		predPool.clear();
		loopPool.clear();
		for(Integer key : keys){
			if(key <= 0) continue;
			
			WaveInfoBlock block = mapLocalOff.get(key);
			pool.add(block);
			
			if(block.pred != null){
				boolean addp = true;
				for(Predictor other : predPool){
					if(block.pred == other){
						addp = false;
						break;
					}
				}
				if(addp) predPool.add(block.pred);
			}
			
			if(block.loop != null) loopPool.add(block.loop);
		}
	}
	
	/**
	 * Rearrange the output order of the blocks to be in order
	 * of currently set wave archive offset. 
	 * Blocks with no wave archive offset set are discarded.
	 * @since 1.0.0
	 */
	public void reorderByWaveArcAddress(){
		updateMaps();
		
		int wcount = pool.size();
		if(wcount < 1) return;
		
		List<Integer> keys = new ArrayList<Integer>(wcount);
		keys.addAll(mapWarcOff.keySet());
		Collections.sort(keys);
		
		pool.clear();
		predPool.clear();
		loopPool.clear();
		for(Integer key : keys){
			if(key < 0) continue;
			
			WaveInfoBlock block = mapWarcOff.get(key);
			pool.add(block);
			
			if(block.pred != null){
				boolean addp = true;
				for(Predictor other : predPool){
					if(block.pred == other){
						addp = false;
						break;
					}
				}
				if(addp) predPool.add(block.pred);
			}
			
			if(block.loop != null) loopPool.add(block.loop);
		}
	}
	
	/**
	 * Rearrange the output order of the blocks to be in the
	 * same order as their UID. The UID sorting uses Java <code>int</code>s,
	 * so the UIDs are treated as though they were signed.
	 * Blocks with no UID set are discarded.
	 * @since 1.0.0
	 */
	public void reorderByUID(){
		updateMaps();
		
		int wcount = pool.size();
		if(wcount < 1) return;
		
		List<Integer> keys = new ArrayList<Integer>(wcount);
		keys.addAll(mapUID.keySet());
		Collections.sort(keys);
		
		pool.clear();
		predPool.clear();
		loopPool.clear();
		for(Integer key : keys){
			if(key == 0) continue;
			if(key == -1) continue;
			
			WaveInfoBlock block = mapUID.get(key);
			pool.add(block);
			
			if(block.pred != null){
				boolean addp = true;
				for(Predictor other : predPool){
					if(block.pred == other){
						addp = false;
						break;
					}
				}
				if(addp) predPool.add(block.pred);
			}
			
			if(block.loop != null) loopPool.add(block.loop);
		}
	}
	
	/**
	 * Update local block addresses as though the pool was reserialized
	 * in its current state.
	 * <br>Does NOT call <code>mergeRedundantBlocks()</code>.
	 * @param section_start Offset relative to the start of the bank struct
	 * of wave pool region.
	 * @param target_64 Whether to assume serialized output uses 64-bit
	 * references (true) or 32-bit references (false). N64 uses 32-bit.
	 * @return True if repacking was successful, false if there was an error.
	 * @since 1.0.0
	 */
	public boolean repackSerial(int section_start, boolean target_64){
		//Reassigns local (in-bank) offsets for all wave, predictor, and loop blocks.
		//Does NOT call mergeRedundantBlocks()
		
		//Clear all local addresses.
		mapLocalOff.clear();
		for(WaveInfoBlock block : pool) block.addr = -1;
		pmapLocalOff.clear();
		for(Predictor pred : predPool) pred.addr = -1;
		lmapLocalOff.clear();
		for(LoopBlock block : loopPool) block.addr = -1;
		
		//Generally, blocks go wave, adpcm, then loop
		//Assuming adpcm and loop are applicable.
		int pos = section_start;
		int wsize = target_64 ? Z64BankBlocks.BLOCKSIZE_WAVE_64 : Z64BankBlocks.BLOCKSIZE_WAVE;
		for(WaveInfoBlock block : pool){
			block.addr = pos;
			pos += wsize;
			
			if(block.pred != null){
				if(block.pred.addr == -1){
					block.pred.addr = pos;
					pos += block.pred.serialSize();
				}
			}
			
			if(block.loop != null){
				if(block.loop.addr == -1){
					block.loop.addr = pos;
					pos += block.loop.serialSize();
				}
			}
		}
		
		//Update pred/loop references in wave blocks
		for(WaveInfoBlock block : pool){
			if(block.loop != null) block.loop_offset = block.loop.addr;
			else block.loop_offset = -1;
			
			if(block.pred != null) block.pred_offset = block.pred.addr;
			else block.pred_offset = -1;
		}
		
		updateMaps();
		return true;
	}

	/**
	 * Serialize the wave pool into the data format required for the 
	 * standard data struct. If output is 32-bit Big-Endian, output
	 * should be directly usable on the N64.
	 * <br>NOTE: This will serialize whatever is in the pool in its current
	 * state. Thus if there are duplicates and/or broken links, these need to
	 * be fixed before this method is called. If you are not trying to match,
	 * it is recommended that you call <code>mergeRedundantBlocks</code> and
	 * <code>repackSerial</code> first.
	 * @param target_64 Whether to serialize with 64-bit references (true) or
	 * 32-bit references (false).
	 * @param little_endian Whether to serialize using Little-Endian byte order (true)
	 * or Big-Endian byte order (false).
	 * @param use_uids Whether to replace the offset field in the wave block with the
	 * wave's UID (true), or leave as wave archive offset (false).
	 * @return Serialized representation of the pool, contained within a 
	 * <code>FileBuffer</code>, or <code>null</code> if serialization failed.
	 * @since 1.0.0
	 */
	public FileBuffer serializePool(boolean target_64, boolean little_endian, boolean use_uids){
		int sersize = getTotalSerializedSize(target_64);
		if(sersize < 1) return null;
		
		for(Predictor p : predPool) p.flag = false;
		for(LoopBlock l : loopPool) l.flag = false;
		
		FileBuffer buff = new FileBuffer(sersize, !little_endian);
		for(WaveInfoBlock waveBlock : pool){
			waveBlock.serializeTo(buff, use_uids, target_64, little_endian);
			if(waveBlock.pred != null){
				if(!waveBlock.pred.flag){
					waveBlock.pred.serializeTo(buff, use_uids, target_64, little_endian);
					waveBlock.pred.flag = true;
				}
			}
			if(waveBlock.loop != null){
				if(!waveBlock.loop.flag){
					waveBlock.loop.serializeTo(buff, use_uids, target_64, little_endian);
					waveBlock.loop.flag = true;
				}
			}
		}
		
		for(Predictor p : predPool) p.flag = false;
		for(LoopBlock l : loopPool) l.flag = false;
		
		return buff;
	}
	
}
