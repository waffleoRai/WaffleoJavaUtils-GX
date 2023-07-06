package waffleoRai_soundbank.nintendo.z64;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import waffleoRai_Sound.nintendo.Z64Sound;
import waffleoRai_Sound.nintendo.Z64Sound.Z64Tuning;
import waffleoRai_Sound.nintendo.Z64WaveInfo;
import waffleoRai_Utils.BinFieldSize;
import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;
import waffleoRai_Utils.MultiFileBuffer;
import waffleoRai_soundbank.nintendo.z64.Z64BankBlocks.EnvelopeBlock;
import waffleoRai_soundbank.nintendo.z64.Z64BankBlocks.InstBlock;
import waffleoRai_soundbank.nintendo.z64.Z64BankBlocks.PercBlock;
import waffleoRai_soundbank.nintendo.z64.Z64BankBlocks.SFXBlock;
import waffleoRai_soundbank.nintendo.z64.Z64BankBlocks.WaveInfoBlock;
import waffleoRai_soundbank.nintendo.z64.Z64DrumPool.DrumRegionInfo;

public class UltraBankFile {
	
	/*----- Constants -----*/
	
	public static final String UBNK_MAGIC = "UBNK";
	public static final int UBNK_VERSION_MAJOR = 2;
	public static final int UBNK_VERSION_MINOR = 3;
	public static final String UWSD_MAGIC = "UWSD";
	public static final int UWSD_VERSION_MAJOR = 2;
	public static final int UWSD_VERSION_MINOR = 0;
	
	public static final int OP_LINK_WAVES_UID = 0x1;
	
	/*----- Instance Variables -----*/
	
	private String magic_main = null;
	private int ver_major = 0;
	private int ver_minor = 0;
	
	private int wsd_id = 0;
	private int meta_flags = 0;
	private EnvelopeBlock[] envs = null;
	private InstBlock[] allinst = null; //Stored for label application
	private Z64Drum[] allperc = null; //Stored for label application
	
	private Map<String, FileBuffer> chunks;
	
	/*----- Init -----*/
	
	private UltraBankFile(){
		chunks = new HashMap<String, FileBuffer>();
	}
	
	/*----- Read -----*/
	
	public static UltraBankFile open(FileBuffer data) throws UnsupportedFileTypeException{
		if(data == null) return null;
		if(data.getFileSize() < 4L) throw new UnsupportedFileTypeException("UltraBankFile.open || File is too small!");
		
		UltraBankFile ubnk = new UltraBankFile();
		
		ubnk.magic_main = data.getASCII_string(0L, 4);
		if((!ubnk.magic_main.equals(UBNK_MAGIC)) && (!ubnk.magic_main.equals(UWSD_MAGIC))) {
			throw new UnsupportedFileTypeException("UltraBankFile.open || Valid magic number not found.");
		}

		data.setEndian(true);
		data.setCurrentPosition(4L);
		int bom = Short.toUnsignedInt(data.nextShort());
		if(bom == 0xfffe) data.setEndian(false);
		
		ubnk.ver_major = Byte.toUnsignedInt(data.nextByte());
		ubnk.ver_minor = Byte.toUnsignedInt(data.nextByte());
		
		//Skip file size and header size.
		data.skipBytes(6L);
		int chunk_count = data.nextShort();
		
		long cpos = data.getCurrentPosition();
		for(int i = 0; i < chunk_count; i++){
			String cmagic = data.getASCII_string(cpos, 4); cpos += 4;
			int csize = data.intFromFile(cpos); cpos += 4;

			try {
				FileBuffer chunkdata = data.createCopy(cpos, cpos + csize);
				ubnk.chunks.put(cmagic, chunkdata);
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			cpos += csize;
		}
		
		return ubnk;
	}
	
	private boolean readBNK_META(Z64Bank bank){
		FileBuffer data = chunks.get("META");
		if(data == null) return false;
		
		data.setCurrentPosition(0L);
		bank.setUID(data.nextInt());
		bank.setMedium(Byte.toUnsignedInt(data.nextByte()));
		bank.setCachePolicy(Byte.toUnsignedInt(data.nextByte()));
		meta_flags = Byte.toUnsignedInt(data.nextByte());
		
		int warc_count = Byte.toUnsignedInt(data.nextByte());
		if(warc_count >= 1) bank.setPrimaryWaveArcIndex(Byte.toUnsignedInt(data.nextByte()));
		if(warc_count >= 2) bank.setSecondaryWaveArcIndex(Byte.toUnsignedInt(data.nextByte()));
		
		long cpos = data.getCurrentPosition();
		while((cpos & 0x3) != 0) cpos++;
		wsd_id = data.intFromFile(cpos);
		
		return true;
	}
	
	private boolean readENVL(Z64Bank bank){
		FileBuffer data = chunks.get("ENVL");
		if(data == null) return false;
		
		data.setCurrentPosition(0L);
		int ecount = Short.toUnsignedInt(data.nextShort());
		if(ecount < 1){
			envs = null;
			return true;
		}
		
		envs = new EnvelopeBlock[ecount];
		int[] eoffs = new int[ecount];
		for(int i = 0; i < ecount; i++){
			eoffs[i] = Short.toUnsignedInt(data.nextShort()) - 8;
		}
		
		for(int i = 0; i < ecount; i++){
			EnvelopeBlock block = EnvelopeBlock.readFrom(data.getReferenceAt(eoffs[i]));
			block.addr = eoffs[i]; //Just to give it an orderable identifier
			envs[i] = bank.addEnvelope(block);
		}
		
		return true;
	}
	
	private boolean readINST(Z64Bank bank){
		if(bank == null) return false;
		FileBuffer data = chunks.get("INST");
		if(data == null) return false;
		
		Z64InstPool instPool = bank.getInstPool();
		if(instPool == null) return false;
		
		data.setCurrentPosition(0L);
		int uniqueCount = data.nextInt();
		int[] slots = new int[126];
		
		for(int i = 0; i < 126; i++){
			slots[i] = Short.toUnsignedInt(data.nextShort());
		}
		data.skipBytes(4L);
		
		int istart = (int)data.getCurrentPosition();
		allinst = new InstBlock[uniqueCount];
		for(int i = 0; i < uniqueCount; i++){
			int pos = (int)data.getCurrentPosition();
			allinst[i] = InstBlock.readFrom(data.getReferenceAt(pos));
			data.skipBytes(32L);
			
			//Sync data in block
			allinst[i].addr = pos;
			if(allinst[i].off_env >= 0){
				allinst[i].envelope = envs[allinst[i].off_env];
				allinst[i].data.setEnvelope(allinst[i].envelope.data);
			}
			
			//Okay, so wave ids are not stored in Z64Instruments
			//So that linking needs to be done outside this parser.
			
			allinst[i] = instPool.addToPool(allinst[i]);
		}
		
		//Assign slots in pool
		for(int i = 0; i < 126; i++){
			if(slots[i] <= 0) continue;
			int instidx = (slots[i] - istart) >>> 5;
			instPool.assignToSlot(allinst[instidx], i);
		}
		
		return true;
	}
	
	private boolean readPERC(Z64Bank bank){
		if(bank == null) return false;
		FileBuffer data = chunks.get("PERC");
		if(data == null) return false;
		
		Z64DrumPool drumPool = bank.getDrumPool();
		if(drumPool == null) return false;
		
		data.setCurrentPosition(0L);
		int uniqueCount = data.nextInt();
		allperc = new Z64Drum[uniqueCount];
		
		int[] sampleRefs = new int[uniqueCount];
		for(int i = 0; i < uniqueCount; i++){
			Z64Drum drum = new Z64Drum();
			drum.setDecay(data.nextByte());
			drum.setPan(data.nextByte());
			
			int regBot = Byte.toUnsignedInt(data.nextByte());
			int regTop = Byte.toUnsignedInt(data.nextByte());
			
			sampleRefs[i] = data.nextInt();
			
			int envidx = -1;
			if((ver_major < 2) || ((ver_major == 2) && (ver_minor < 1))){
				float tuneRaw = Float.intBitsToFloat(data.nextInt());
				envidx = data.nextInt();
				
				Z64Tuning tuning = Z64Drum.localToCommonTuning(Z64Sound.MIDDLE_C - Z64Sound.STDRANGE_BOTTOM, tuneRaw);
				drum.setTuning(tuning);
				
				if(envidx >= 0){
					drum.setEnvelope(envs[envidx].data);
				}
			}
			else{
				Z64Tuning tuning = new Z64Tuning();
				tuning.root_key = data.nextByte();
				tuning.fine_tune = data.nextByte();
				drum.setTuning(tuning);
				
				envidx = Short.toUnsignedInt(data.nextShort());
				if(envidx >= 0){
					drum.setEnvelope(envs[envidx].data);
				}
			}
			
			//Add to pool.
			allperc[i] = drum;
			drumPool.addToPool(drum);
			
			for(int j = regBot; j <= regTop; j++){
				PercBlock block = new PercBlock(drum, j);
				block.updateLocalTuning();
				block.off_env = envidx;
				block.addr = (i << 8) | j;
				if(envidx >= 0){
					block.envelope = envs[envidx];
				}
				block.off_snd = sampleRefs[i];
				drumPool.setSlot(block, j);
			}
		}
		
		return true;
	}
	
	private boolean readDATA(Z64Bank bank){
		if(bank == null) return false;
		FileBuffer data = chunks.get("DATA");
		if(data == null) return false;
		
		Z64SFXPool sfxPool = bank.getSFXPool();
		if(sfxPool == null) return false;
		
		data.setCurrentPosition(0L);
		int sfxCount = data.nextInt();
		sfxPool.expandSlotCapacity(sfxCount);
		for(int i = 0; i < sfxCount; i++){
			int pos = (int)data.getCurrentPosition();
			int word1 = data.nextInt();
			int word2 = data.nextInt();
			if(word1 == 0 && word2 == 0) continue;
			
			SFXBlock block = new SFXBlock();
			block.addr = pos;
			
			block.off_snd = word1;
			block.data.setTuning(Float.intBitsToFloat(word2));
			sfxPool.setToSlot(block, i);
		}
		
		return true;
	}
	
	private boolean readLABL(Z64Bank bank){
		if(bank == null) return false;
		FileBuffer data = chunks.get("LABL");
		if(data == null) return false;
		
		if(allinst == null && allperc == null) return false;
		
		BufferReference ref = data.getReferenceAt(0L);
		if(allinst != null){
			for(int i = 0; i < allinst.length; i++){
				String lbl = ref.nextVariableLengthString("UTF8", BinFieldSize.WORD, 2);
				if(allinst[i] != null) allinst[i].data.setName(lbl);
			}
		}
		
		if(allperc != null){
			for(int i = 0; i < allperc.length; i++){
				String lbl = ref.nextVariableLengthString("UTF8", BinFieldSize.WORD, 2);
				if(allperc[i] != null) allperc[i].setName(lbl);
			}
		}
		
		return true;
	}
	
	private boolean readIENM(Z64Bank bank){
		if(bank == null) return false;
		FileBuffer data = chunks.get("IENM");
		if(data == null) return false;
		
		Z64InstPool instPool = bank.getInstPool();
		if(instPool == null) return false;

		BufferReference ref = data.getReferenceAt(0L);
		for(int i = 0; i < 126; i++){
			String lbl = ref.nextVariableLengthString("UTF8", BinFieldSize.WORD, 2);
			instPool.setSlotEnumString(lbl, i);
		}
		
		return true;
	}
	
	private boolean readPENM(Z64Bank bank){
		if(bank == null) return false;
		FileBuffer data = chunks.get("PENM");
		if(data == null) return false;
		
		Z64DrumPool drumPool = bank.getDrumPool();
		if(drumPool == null) return false;
		
		BufferReference ref = data.getReferenceAt(0L);
		for(int i = 0; i < 64; i++){
			String lbl = ref.nextVariableLengthString("UTF8", BinFieldSize.WORD, 2);
			drumPool.setSlotEnumString(lbl, i);
		}
		
		return true;
	}
	
	private boolean readENUM(Z64Bank bank){
		if(bank == null) return false;
		FileBuffer data = chunks.get("ENUM");
		if(data == null) return false;
		
		Z64SFXPool sfxPool = bank.getSFXPool();
		if(sfxPool == null) return false;
		
		int sfxCount = sfxPool.getSerializedSlotCount();
		BufferReference ref = data.getReferenceAt(0L);
		for(int i = 0; i < sfxCount; i++){
			String lbl = ref.nextVariableLengthString("UTF8", BinFieldSize.WORD, 2);
			SFXBlock block = sfxPool.getSlot(i);
			if(block != null){
				block.enm_str = lbl;
			}
		}
		
		return true;
	}
	
	private boolean readBNKTo(Z64Bank bank) throws UnsupportedFileTypeException{
		readBNK_META(bank);
		readENVL(bank);
		readINST(bank);
		readPERC(bank);
		readLABL(bank);
		
		if(ver_major >= 2){
			readIENM(bank);
			readPENM(bank);
		}
		
		return true;
	}
	
	private boolean readWSDTo(Z64Bank bank) throws UnsupportedFileTypeException{
		//readWSD_META(bank); //Don't really need?
		if(!readDATA(bank)) return false;
		
		if(ver_major >= 2) readENUM(bank);
		
		return true;
	}
	
	public Z64Bank read() throws UnsupportedFileTypeException{
		Z64Bank bank = new Z64Bank();
		if(!readTo(bank)) return null;
		return bank;
	}
	
	public boolean readTo(Z64Bank bank) throws UnsupportedFileTypeException{
		if(this.isUBNK()) return readBNKTo(bank);
		if(this.isUWSD()) return readWSDTo(bank);
		return false;
	}
	
	public void linkWaves(Z64Bank bank, Map<Integer, Z64WaveInfo> waveIdentMap){
		if(bank == null || waveIdentMap == null) return;
		Set<Integer> usedwaves = new HashSet<Integer>();
		
		Z64SFXPool sfxPool = bank.getSFXPool();
		if(sfxPool != null){
			List<SFXBlock> blocks = sfxPool.getAllSFXBlocks();
			for(SFXBlock block : blocks){
				int wid = block.off_snd;
				if(!waveRefsByUID()){
					if(wid <= 0) continue;
				}
				usedwaves.add(wid);
			}
		}
		
		Z64DrumPool drumPool = bank.getDrumPool();
		if(drumPool != null){
			List<PercBlock> blocks = drumPool.getAllPercBlocks();
			for(PercBlock block : blocks){
				int wid = block.off_snd;
				if(!waveRefsByUID()){
					if(wid <= 0) continue;
				}
				usedwaves.add(wid);
			}
		}
		
		Z64InstPool instPool = bank.getInstPool();
		if(instPool != null){
			List<InstBlock> blocks = instPool.getAllInstBlocks();
			for(InstBlock block : blocks){
				int wid = block.off_snd_med;
				if(wid != 0){
					if(waveRefsByUID()){
						if(wid != -1) usedwaves.add(wid);
					}
					else{
						if(wid > 0) usedwaves.add(wid);
					}
				}
				
				wid = block.off_snd_lo;
				if(wid != 0){
					if(waveRefsByUID()){
						if(wid != -1) usedwaves.add(wid);
					}
					else{
						if(wid > 0) usedwaves.add(wid);
					}
				}
				
				wid = block.off_snd_hi;
				if(wid != 0){
					if(waveRefsByUID()){
						if(wid != -1) usedwaves.add(wid);
					}
					else{
						if(wid > 0) usedwaves.add(wid);
					}
				}
			}
		}
		
		usedwaves.remove(0);
		if(waveRefsByUID()){
			usedwaves.remove(-1);
		}
		
		if(usedwaves.isEmpty()) return;
		
		//Match ids to map
		List<Z64WaveInfo> waves = new ArrayList<Z64WaveInfo>(usedwaves.size());
		for(Integer id : usedwaves){
			Z64WaveInfo winfo = waveIdentMap.get(id);
			if(winfo != null) waves.add(winfo);
		}

		//Call other linkWaves overload
		linkWaves(bank, waves);
	}
	
	public void linkWaves(Z64Bank bank, Collection<Z64WaveInfo> waves){
		if(bank == null || waves == null) return;
		
		//Build wave pool
		Z64WavePool wavePool = bank.getWavePool();
		if(wavePool == null) return;
		for(Z64WaveInfo winfo : waves){
			wavePool.addToPool(winfo);
		}
		wavePool.updateMaps();
		
		int waveRefType = waveRefsByUID() ? Z64Bank.REFTYPE_UID : Z64Bank.REFTYPE_OFFSET_GLOBAL;
		
		//Link sfx...
		Z64SFXPool sfxPool = bank.getSFXPool();
		if(sfxPool != null){
			sfxPool.relink(wavePool, waveRefType);
		}
		
		//Link drums...
		Z64DrumPool drumPool = bank.getDrumPool();
		if(drumPool != null){
			drumPool.relink(wavePool, null, waveRefType);
		}
		
		//Link inst...
		Z64InstPool instPool = bank.getInstPool();
		if(instPool != null){
			instPool.relink(wavePool, null, waveRefType);
		}
	}
	
	public void close(){
		//Dispose of all values and clear map.
		try{
			for(FileBuffer chunk : chunks.values()) chunk.dispose();
			chunks.clear();
		}
		catch(Exception ex){
			ex.printStackTrace();
		}
	}
	
	/*----- Write -----*/
	
	private static int ubnkWriter_resolveWaveRef(InstBlock iblock, int which, int flags){
		if(iblock == null) return 0;
		
		boolean useuid = (flags & OP_LINK_WAVES_UID) != 0;
		
		//Prioritizes finding the winfo.
		//Then the wave block
		//Then whatever is stored in the inst block
		WaveInfoBlock wblock = null;
		Z64WaveInfo winfo = null;
		
		switch(which){
		case 0:
			//low
			if(iblock.data != null){
				winfo = iblock.data.getSampleLow();
			}
			wblock = iblock.snd_lo;
			break;
		case 1:
			//mid
			if(iblock.data != null){
				winfo = iblock.data.getSampleMiddle();
			}
			wblock = iblock.snd_med;
			break;
		case 2:
			//high
			if(iblock.data != null){
				winfo = iblock.data.getSampleHigh();
			}
			wblock = iblock.snd_hi;
			break;
		}
		
		if(winfo == null && wblock != null){
			winfo = wblock.getWaveInfo();
		}
		
		if(winfo != null){
			if(useuid) return winfo.getUID();
			else return winfo.getWaveOffset();
		}
		
		switch(which){
		case 0: return iblock.off_snd_lo;
		case 1: return iblock.off_snd_med;
		case 2: return iblock.off_snd_hi;
		}
		
		return 0;
	}
	
	private static FileBuffer writeUBNK_META(Z64Bank bank, int flags){
		FileBuffer buff = new FileBuffer(32, true);
		buff.printASCIIToFile("META");
		buff.addToFile(0); //Size placeholder
		buff.addToFile(bank.getUID());
		buff.addToFile((byte)bank.getMedium());
		buff.addToFile((byte)bank.getCachePolicy());
		buff.addToFile((byte)flags);
		buff.addToFile((byte)2);
		buff.addToFile((byte)bank.getPrimaryWaveArcIndex());
		buff.addToFile((byte)bank.getSecondaryWaveArcIndex());
		while((buff.getFileSize() & 0x3) != 0) buff.addToFile((byte)0);
		buff.addToFile(0); //WSD reference, too lazy to handle now.
		
		buff.replaceInt((int)(buff.getFileSize() - 8L), 4L);
		
		return buff;
	}
	
	private static FileBuffer writeUBNK_ENVL(Z64Bank bank){
		Z64EnvPool envPool = bank.getEnvPool();
		int ecount = envPool.getEnvelopeCount();
		int sizeest = envPool.getTotalSerializedSize();
		
		FileBuffer buff = new MultiFileBuffer(2);
		FileBuffer head = new FileBuffer(32 + (ecount << 1), true);
		head.printASCIIToFile("ENVL");
		head.addToFile(0); //Size placeholder
		head.addToFile((short)ecount);
		int datstart = (int)head.getFileSize() + (ecount << 1);
		
		int i = 0;
		FileBuffer edat = new FileBuffer(sizeest + 16, true);
		List<EnvelopeBlock> eblocks = envPool.getAllEnvBlocks();
		for(EnvelopeBlock block : eblocks){
			block.pool_id = i++;
			block.data.id = block.pool_id;
			int chunk_pos = datstart + (int)edat.getFileSize();
			head.addToFile((short)chunk_pos);
			block.serializeTo(edat, false);
		}
		
		
		//Pad edat to a multiple of 4.
		int totalsize = (int)(head.getFileSize() + edat.getFileSize());
		int pad = 4 - (totalsize & 0x3);
		for(int j = 0; j < pad; j++) edat.addToFile((byte)0);
		
		head.replaceInt((int)(head.getFileSize() + edat.getFileSize() - 8L), 4L);
		
		buff.addToFile(head);
		buff.addToFile(edat);
		return buff;
	}
	
	private static FileBuffer writeUBNK_INST(Z64Bank bank, int flags){
		Z64InstPool instPool = bank.getInstPool();
		if(instPool == null) return null;
		int i_slot_count = instPool.getSerializedSlotCount();
		if(i_slot_count < 1) return null;
		int icount = instPool.getUniqueInstCount();
		
		final int idatstart = 12 + 256;
		final long otblstart = 12;
		
		FileBuffer buff = new FileBuffer(idatstart + (icount << 5) + 16, true);
		buff.printASCIIToFile("INST");
		buff.addToFile(0); //Size placeholder
		buff.addToFile(icount);
		for(int i = 0; i < 64; i++) buff.addToFile(0); //Inst slot table placeholders
		
		//Instrument section
		List<InstBlock> instlist = instPool.getAllInstBlocks();
		int i = 0;
		for(InstBlock block : instlist){
			block.pool_id = i++;
			buff.addToFile((byte)0);
			buff.addToFile(block.data.getLowRangeTop());
			buff.addToFile(block.data.getHighRangeBottom());
			buff.addToFile(block.data.getDecay());
			
			if(block.envelope != null) buff.addToFile(block.envelope.pool_id);
			else buff.addToFile(-1);
			
			int wref = ubnkWriter_resolveWaveRef(block, 0, flags);
			buff.addToFile(wref);
			if(wref != 0 && wref != -1) buff.addToFile(Float.floatToRawIntBits(block.data.getTuningLow()));
			else buff.addToFile(0);
			
			wref = ubnkWriter_resolveWaveRef(block, 1, flags);
			buff.addToFile(wref);
			if(wref != 0 && wref != -1) buff.addToFile(Float.floatToRawIntBits(block.data.getTuningMiddle()));
			else buff.addToFile(0);
			
			wref = ubnkWriter_resolveWaveRef(block, 2, flags);
			buff.addToFile(wref);
			if(wref != 0 && wref != -1) buff.addToFile(Float.floatToRawIntBits(block.data.getTuningHigh()));
			else buff.addToFile(0);
		}
		
		//Update slot table
		InstBlock[] slots = instPool.getSlots();
		for(i = 0; i < slots.length; i++){
			if(slots[i] != null){
				long tpos = otblstart + (i << 1);
				int datpos = idatstart + (slots[i].pool_id << 5);
				buff.replaceShort((short)datpos, tpos);
			}
		}
		
		buff.replaceInt((int)(buff.getFileSize() - 8L), 4L);
		return buff;
	}
	
	private static FileBuffer writeUBNK_PERC(Z64Bank bank, int flags){
		Z64DrumPool drumPool = bank.getDrumPool();
		if(drumPool == null) return null;
		if(drumPool.getSerializedSlotCount() < 1) return null;
		int pcount = drumPool.getUniqueDrumCount();
		boolean useuid = (flags & OP_LINK_WAVES_UID) != 0;
		
		List<DrumRegionInfo> drumreg = drumPool.getDrumRegions();
		
		FileBuffer buff = new FileBuffer(12 + (12 * pcount) + 16, true);
		buff.printASCIIToFile("PERC");
		buff.addToFile(0); //Size placeholder
		buff.addToFile(pcount);
		
		for(DrumRegionInfo reg : drumreg){
			Z64Drum drum = reg.drum;
			buff.addToFile(drum.getDecay());
			buff.addToFile(drum.getPan());
			
			buff.addToFile(reg.minNote);
			buff.addToFile(reg.maxNote);
			
			Z64WaveInfo winfo = drum.getSample();
			if(winfo != null){
				if(useuid) buff.addToFile(winfo.getUID());
				else buff.addToFile(winfo.getWaveOffset());
			}
			else buff.addToFile(0);
			
			//Fergot the last word here...
			buff.addToFile(drum.getTuning().root_key);
			buff.addToFile(drum.getTuning().fine_tune);
			
			Z64Envelope env = drum.getEnvelope();
			if(env != null) buff.addToFile((short)env.id);
			else buff.addToFile((short)-1);
			
		}
		
		buff.replaceInt((int)(buff.getFileSize() - 8L), 4L);
		return buff;
	}
	
	private static FileBuffer writeUBNK_LABL(Z64Bank bank){
		int alloc = 0;
		List<String> lbls = new LinkedList<String>();
		
		Z64InstPool instPool = bank.getInstPool();
		if(instPool != null){
			List<InstBlock> instlist = instPool.getAllInstBlocks();
			for(InstBlock block : instlist){
				if(block.data != null){
					String iname = block.data.getName();
					if(iname != null){
						alloc += 3 + iname.length() * 3;
						lbls.add(iname);
					}
					else{
						alloc += 2;
						lbls.add("");
					}
				}
				else{
					alloc += 2;
					lbls.add("");
				}
			}
		}
		
		Z64DrumPool drumPool = bank.getDrumPool();
		if(drumPool != null){
			List<Z64Drum> drums = drumPool.getAllDrums();
			for(Z64Drum drum : drums){
				String pname = drum.getName();
				if(pname != null){
					alloc += 3 + pname.length() * 3;
					lbls.add(pname);
				}
				else{
					alloc += 2;
					lbls.add("");
				}
			}
		}
		
		FileBuffer buff = new FileBuffer(alloc + 16, true);
		buff.printASCIIToFile("LABL");
		buff.addToFile(0); //Size placeholder
		for(String s : lbls){
			if(s.isEmpty()) buff.addToFile((short)0);
			else{
				buff.addVariableLengthString("UTF8", s, BinFieldSize.WORD, 2);
			}
		}
		
		while((buff.getFileSize() & 0x3) != 0) buff.addToFile((byte)0);
		
		buff.replaceInt((int)(buff.getFileSize() - 8L), 4L);
		return buff;
	}
	
	private static FileBuffer writeUBNK_IENM(Z64Bank bank){
		Z64InstPool instPool = bank.getInstPool();
		if(instPool == null) return null;
		
		int alloc = 0;
		String[] estr = new String[126];
		
		for(int i = 0; i < 126; i++){
			estr[i] = instPool.getSlotEnumString(i);
			if(estr[i] != null){
				alloc += 3 + estr[i].length();
			}
			else alloc += 2;
		}
		
		FileBuffer buff = new FileBuffer(alloc + 16, true);
		buff.printASCIIToFile("IENM");
		buff.addToFile(0); //Size placeholder
		for(int i = 0; i < 126; i++){
			if(estr[i] != null){
				buff.addVariableLengthString(estr[i], BinFieldSize.WORD, 2);
			}
			else buff.addToFile((short)0);
		}
		while((buff.getFileSize() & 0x3) != 0) buff.addToFile((byte)0);
		
		buff.replaceInt((int)(buff.getFileSize() - 8L), 4L);
		return buff;
	}
	
	private static FileBuffer writeUBNK_PENM(Z64Bank bank){
		Z64DrumPool drumPool = bank.getDrumPool();
		if(drumPool == null) return null;
		
		int alloc = 0;
		String[] estr = new String[64];
		
		for(int i = 0; i < 64; i++){
			estr[i] = drumPool.getSlotEnumString(i);
			if(estr[i] != null){
				alloc += 3 + estr[i].length();
			}
			else alloc += 2;
		}
		
		FileBuffer buff = new FileBuffer(alloc + 16, true);
		buff.printASCIIToFile("PENM");
		buff.addToFile(0); //Size placeholder
		for(int i = 0; i < 64; i++){
			if(estr[i] != null){
				buff.addVariableLengthString(estr[i], BinFieldSize.WORD, 2);
			}
			else buff.addToFile((short)0);
		}
		while((buff.getFileSize() & 0x3) != 0) buff.addToFile((byte)0);
		
		buff.replaceInt((int)(buff.getFileSize() - 8L), 4L);
		return buff;
	}
	
	public static void writeUBNK(Z64Bank bank, String path, int flags) throws IOException{
		if(bank == null) return;
		BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(path));
		writeUBNK(bank, bos, flags);
		bos.close();
	}
	
	public static void writeUBNK(Z64Bank bank, OutputStream output, int flags) throws IOException{
		if(bank == null) return;
		if(output == null) return;
		bank.tidy();
		
		FileBuffer c_meta = writeUBNK_META(bank, flags);
		FileBuffer c_envl = writeUBNK_ENVL(bank);
		FileBuffer c_inst = writeUBNK_INST(bank, flags);
		FileBuffer c_perc = writeUBNK_PERC(bank, flags);
		FileBuffer c_labl = writeUBNK_LABL(bank);
		FileBuffer c_ienm = writeUBNK_IENM(bank);
		FileBuffer c_penm = writeUBNK_PENM(bank);
		
		//Go through and calculate size.
		int totalSize = 0;
		int chunkCount = 0;
		if(c_meta != null){totalSize += (int)c_meta.getFileSize(); chunkCount++;}
		if(c_envl != null){totalSize += (int)c_envl.getFileSize(); chunkCount++;}
		if(c_inst != null){totalSize += (int)c_inst.getFileSize(); chunkCount++;}
		if(c_perc != null){totalSize += (int)c_perc.getFileSize(); chunkCount++;}
		if(c_labl != null){totalSize += (int)c_labl.getFileSize(); chunkCount++;}
		if(c_ienm != null){totalSize += (int)c_ienm.getFileSize(); chunkCount++;}
		if(c_penm != null){totalSize += (int)c_penm.getFileSize(); chunkCount++;}
		
		FileBuffer header = new FileBuffer(32, true);
		header.printASCIIToFile(UBNK_MAGIC);
		header.addToFile((short)0xfeff);
		header.addToFile((byte)UBNK_VERSION_MAJOR);
		header.addToFile((byte)UBNK_VERSION_MINOR);
		header.addToFile(0);
		header.addToFile((short)0);
		header.addToFile((short)chunkCount);
		
		int hdrsize = (int)header.getFileSize();
		header.replaceInt(hdrsize + totalSize, 8L);
		header.replaceShort((short)hdrsize, 12L);
		
		header.writeToStream(output);
		if(c_meta != null) c_meta.writeToStream(output);
		if(c_envl != null) c_envl.writeToStream(output);
		if(c_inst != null) c_inst.writeToStream(output);
		if(c_perc != null) c_perc.writeToStream(output);
		if(c_labl != null) c_labl.writeToStream(output);
		if(c_ienm != null) c_ienm.writeToStream(output);
		if(c_penm != null) c_penm.writeToStream(output);
	}
	
	public static void writeUWSD(Z64Bank bank, String path, int flags) throws IOException{
		if(bank == null) return;
		BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(path));
		writeUWSD(bank, bos, flags);
		bos.close();
	}
	
	public static void writeUWSD(Z64Bank bank, OutputStream output, int flags) throws IOException{
		if(bank == null) return;
		if(output == null) return;
		
		Z64SFXPool sfxPool = bank.getSFXPool();
		if(sfxPool == null) return;
		int totalSize = 0;
		int chunkCount = 0;
		boolean waveUIDs = (flags & OP_LINK_WAVES_UID) != 0;
		
		//META
		FileBuffer meta = new FileBuffer(24, true);
		meta.printASCIIToFile("META");
		meta.addToFile(0); //Chunk size placeholder
		meta.addToFile(bank.getUID());
		meta.addToFile((byte)flags);
		meta.addToFile((byte)2);
		meta.addToFile((byte)bank.getPrimaryWaveArcIndex());
		meta.addToFile((byte)bank.getSecondaryWaveArcIndex());
		meta.replaceInt((int)(meta.getFileSize() - 8L), 4L);
		totalSize += (int)meta.getFileSize();
		chunkCount++;
		
		//DATA
		int sfxCount = sfxPool.getSerializedSlotCount();
		String[] enumStr = new String[sfxCount];
		boolean needEnumChunk = false;
		FileBuffer data = new FileBuffer(32 + (8 * sfxCount), true);
		data.printASCIIToFile("DATA");
		data.addToFile(0); //Chunk size placeholder
		data.addToFile(sfxCount);
		for(int i = 0; i < sfxCount; i++){
			SFXBlock block = sfxPool.getSlot(i);
			if(block != null){
				Z64WaveInfo winfo = block.data.getSample();
				if(winfo != null){
					if(waveUIDs) data.addToFile(winfo.getUID());
					else data.addToFile(winfo.getWaveOffset());
				}
				else{
					//Just have to take whatever is in the block...
					if(block.sample != null){
						winfo = block.sample.getWaveInfo();
						if(winfo != null){
							if(waveUIDs) data.addToFile(winfo.getUID());
							else data.addToFile(winfo.getWaveOffset());
						}
						else data.addToFile(block.sample.addr); //Gross
					}
					else data.addToFile(block.off_snd);
				}
				data.addToFile(Float.floatToRawIntBits(block.data.getTuning()));
				if(block.enm_str != null){
					needEnumChunk = true;
					enumStr[i] = block.enm_str;
				}
			}
			else data.addToFile(0L);
		}
		data.replaceInt((int)(data.getFileSize() - 8L), 4L);
		totalSize += (int)data.getFileSize();
		chunkCount++;
		
		//ENUM (If applicable)
		FileBuffer xenm = null;
		if(needEnumChunk){
			int alloc = 0;
			for(int i = 0; i < enumStr.length; i++){
				alloc += 2;
				if(enumStr[i] != null){
					alloc += 1 + (enumStr[i].length());
				}
			}
			xenm = new FileBuffer(alloc, true);
			xenm.printASCIIToFile("ENUM");
			xenm.addToFile(0); //Chunk size placeholder
			for(int i = 0; i < sfxCount; i++){
				if(enumStr[i] != null){
					xenm.addVariableLengthString(enumStr[i], BinFieldSize.WORD, 2);
				}
				else xenm.addToFile((short)0);
			}
			xenm.replaceInt((int)(xenm.getFileSize() - 8L), 4L);
			totalSize += (int)xenm.getFileSize();
			chunkCount++;
		}
		
		//Header
		FileBuffer header = new FileBuffer(24, true);
		header.printASCIIToFile(UWSD_MAGIC);
		header.addToFile((short)0xfeff);
		header.addToFile((byte)UWSD_VERSION_MAJOR);
		header.addToFile((byte)UWSD_VERSION_MINOR);
		header.addToFile(0); //Size placeholder
		header.addToFile((short)0); //Size placeholder
		header.addToFile((short)chunkCount);
		totalSize += (int)header.getFileSize();
		header.replaceInt(totalSize, 8L);
		header.replaceShort((short)header.getFileSize(), 12L);
		
		//Write
		header.writeToStream(output);
		meta.writeToStream(output);
		data.writeToStream(output);
		if(xenm != null) xenm.writeToStream(output);
	}
	
	/*----- Getters -----*/
	
	public boolean isUBNK(){
		if(magic_main == null) return false;
		return magic_main.equals(UBNK_MAGIC);
	}
	
	public boolean isUWSD(){
		if(magic_main == null) return false;
		return magic_main.equals(UWSD_MAGIC);
	}
	
	public int getMajorVersion(){return this.ver_major;}
	public int getMinorVersion(){return this.ver_minor;}
	
	public int getWSDID(){return wsd_id;}
	public boolean waveRefsByUID(){return (meta_flags & OP_LINK_WAVES_UID) != 0;}
	
	/*----- Setters -----*/

}
