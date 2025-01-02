package waffleoRai_soundbank.nintendo.z64;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import waffleoRai_Sound.nintendo.N64ADPCMTable;
import waffleoRai_Sound.nintendo.Z64WaveInfo;
import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.FileBuffer;

class Z64BankBlocks {
	
	public static final int BLOCKSIZE_WAVE = 16;
	public static final int BLOCKSIZE_WAVE_64 = 32;
	
	public static final int BLOCKSIZE_INST = 32;
	public static final int BLOCKSIZE_INST_64 = 64;
	public static final int BLOCKSIZE_PERC = 16;
	public static final int BLOCKSIZE_PERC_64 = 32;
	public static final int BLOCKSIZE_SFX = 8;
	public static final int BLOCKSIZE_SFX_64 = 16;
	
	public static final int BLOCKSIZE_LOOP_ONESHOT = 16;
	public static final int BLOCKSIZE_LOOP_STD = 48;
	
	protected static abstract class BankBlock{
		protected int addr;
		protected boolean flag = false;
		protected String enm_str;
		protected int pool_id = -1;
		
		public abstract int serialSize();
		public abstract int serialSize(boolean target64);
		public abstract int serializeTo(FileBuffer buffer, boolean uidMode);
		public abstract int serializeTo(FileBuffer buffer, boolean uidMode, boolean target64, boolean targetLE);
		
		public boolean equals(Object o){return this == o;}
		
		public void copyTo(BankBlock copy) {
			copy.addr = this.addr;
			copy.flag = this.flag;
			copy.enm_str = this.enm_str;
			copy.pool_id = this.pool_id;
		}
	}
	
	protected static class LoopBlock extends BankBlock{
		protected int start = -1;
		protected int end = -1;
		protected int count = 0;
		protected short[] state_vals;
		
		public static LoopBlock readFrom(BufferReference ptr){
			LoopBlock block = new LoopBlock();
			block.start = ptr.nextInt();
			block.end = ptr.nextInt();
			block.count = ptr.nextInt();
			ptr.add(4L);
			
			if(block.count != 0){
				block.state_vals = new short[16];
				//short[] state_vals = new short[16];
				for(int i = 0; i < 16; i++){
					block.state_vals[i] = ptr.nextShort();
					//state_vals[i] = ptr.nextShort();
				}
			}
			
			return block;
		}
		
		public int serialSize(){return (count!=0) ? BLOCKSIZE_LOOP_STD : BLOCKSIZE_LOOP_ONESHOT;}
		public int serialSize(boolean target64){return serialSize();}
		
		public int serializeTo(FileBuffer buffer, boolean uidMode){
			buffer.addToFile(start);
			buffer.addToFile(end);
			
			if(state_vals == null) count = 0;
			buffer.addToFile(count);
			buffer.addToFile(0);
			
			if(count != 0){
				for(int i = 0; i < 16; i++){
					buffer.addToFile(state_vals[i]);
				}
				return 48;
			}
			
			return 16;
		}
		
		public int serializeTo(FileBuffer buffer, boolean uidMode, boolean target64, boolean targetLE){
			return serializeTo(buffer, uidMode);
		}

		public LoopBlock copy() {
			LoopBlock copy = new LoopBlock();
			super.copyTo(copy);
			copy.start = this.start;
			copy.end = this.end;
			copy.count = this.count;
			if(this.state_vals != null) {
				copy.state_vals = Arrays.copyOf(state_vals, state_vals.length);
			}
			return copy;
		}
		
	}
	
	protected static class Predictor extends BankBlock{
		protected int order;
		protected int count;
		
		protected short[] table;
		
		public static Predictor readFrom(BufferReference ptr){
			Predictor block = new Predictor();
			block.order = ptr.nextInt();
			block.count = ptr.nextInt();
			
			int tsz = (block.order * block.count) << 3;
			block.table = new short[tsz];
			for(int i = 0; i < tsz; i++) block.table[i] = ptr.nextShort();
			
			return block;
		}
		
		public int serialSize(){return 8+(order*count*8*2);}
		public int serialSize(boolean target64){return serialSize();}
		
		public int serializeTo(FileBuffer buffer, boolean uidMode){
			int tsz = (order * count) << 3;
			
			buffer.addToFile(order);
			buffer.addToFile(count);
			for(int i = 0; i < tsz; i++) buffer.addToFile(table[i]);
			
			return 8 + (tsz << 1);
		}
		
		public int serializeTo(FileBuffer buffer, boolean uidMode, boolean target64, boolean targetLE){
			return serializeTo(buffer, uidMode);
		}
		
		public N64ADPCMTable loadToTable(){
			return N64ADPCMTable.fromRaw(order, count, table);
		}
		
		public boolean isEquivalent(Predictor other){
			if(other == null) return false;
			if(this.order != other.order) return false;
			if(this.count != other.count) return false;
			for(int i = 0; i < table.length; i++){
				if(this.table[i] != other.table[i]) return false;
			}
			return true;
		}
		
		public Predictor copy() {
			Predictor copy = new Predictor();
			super.copyTo(copy);
			copy.order = this.order;
			copy.count = this.count;
			copy.table = Arrays.copyOf(table, table.length);
			return copy;
		}
		
	}
	
	protected static class EnvelopeBlock extends BankBlock{
		//TODO Force pad to 16 per envelope?
		
		protected Z64Envelope data;
		
		public EnvelopeBlock(){data = new Z64Envelope();}
		public EnvelopeBlock(Z64Envelope env){data = env;}
		
		public static EnvelopeBlock readFrom(BufferReference ptr){
			EnvelopeBlock block = new EnvelopeBlock();
			while(true){
				short cmd = ptr.nextShort();
				short val = ptr.nextShort();
				if(!block.data.addEvent(cmd, val)) break;
				if(block.data.hasTerminal()) break;
			}
			
			return block;
		}
		
		public int serialSize(){
			data.cleanup();
			return (data.eventCount() << 2);
		}
		public int serialSize(boolean target64){return serialSize();}
		
		public int serializeTo(FileBuffer buffer, boolean uidMode){
			data.cleanup();
			int sz = 0;
			for(short[] event : data.events){
				short cmd = event[0];
				buffer.addToFile(cmd);
				buffer.addToFile(event[1]);
				sz += 4;
			}
			if(data.hasTerminal()){
				buffer.addToFile((short)data.terminator);
				buffer.addToFile((short)0);
			}
			return sz;
		}
		
		public int serializeTo(FileBuffer buffer, boolean uidMode, boolean target64, boolean targetLE){
			return serializeTo(buffer, uidMode);
		}
		
		public EnvelopeBlock copy() {
			EnvelopeBlock copy = new EnvelopeBlock();
			super.copyTo(copy);
			if(data != null) copy.data = this.data.copy();
			return copy;
		}
		
	}
	
	protected static class WaveInfoBlock extends BankBlock{

		protected Z64WaveInfo wave_info;
		
		protected int loop_offset;
		protected int pred_offset;
		
		protected LoopBlock loop;
		protected Predictor pred;
		
		public WaveInfoBlock(){
			wave_info = new Z64WaveInfo();
		}
		
		public WaveInfoBlock(Z64WaveInfo winfo){
			wave_info = winfo;
			loop_offset = -1;
			pred_offset = -1;
			loop = new LoopBlock();
			loop.start = winfo.getLoopStart();
			loop.end = winfo.getLoopEnd();
			loop.count = winfo.getLoopCount();
			loop.state_vals = winfo.getLoopState();
			pred = new Predictor();
			N64ADPCMTable tbl = winfo.getADPCMBook();
			pred.count = tbl.getPredictorCount();
			pred.order = tbl.getOrder();
			pred.table = tbl.getAsRaw();
		}
		
		public static WaveInfoBlock readFrom(BufferReference ptr){
			WaveInfoBlock block = new WaveInfoBlock();
			
			int flags = Byte.toUnsignedInt(ptr.nextByte());
			block.wave_info.setCodec((flags >>> 4) & 0xf);
			block.wave_info.setMedium((flags >>> 2) & 0x3);
			block.wave_info.setU2(ptr.nextByte());
			block.wave_info.setWaveSize(Short.toUnsignedInt(ptr.nextShort()));
			block.wave_info.setWaveOffset(ptr.nextInt());
			block.loop_offset = ptr.nextInt();
			block.pred_offset = ptr.nextInt();
			
			return block;
		}
		
		public static WaveInfoBlock read64(BufferReference ptr){
			WaveInfoBlock block = new WaveInfoBlock();
			
			int flags = Byte.toUnsignedInt(ptr.nextByte());
			block.wave_info.setCodec((flags >>> 4) & 0xf);
			block.wave_info.setMedium((flags >>> 2) & 0x3);
			block.wave_info.setU2(ptr.nextByte());
			block.wave_info.setWaveSize(Short.toUnsignedInt(ptr.nextShort()));
			
			//Padding (4)
			ptr.add(4L);
			block.wave_info.setWaveOffset((int)ptr.nextLong());
			block.loop_offset = (int)ptr.nextLong();
			block.pred_offset = (int)ptr.nextLong();
			
			return block;
		}
		
		public int serialSize(){return serialSize(false);}
		public int serialSize(boolean target64){
			return target64 ? BLOCKSIZE_WAVE_64:BLOCKSIZE_WAVE;
		}
		
		public int serializeTo(FileBuffer buffer, boolean uidMode){
			return serializeTo(buffer, uidMode, false, false);
		}
		
		public int serializeTo(FileBuffer buffer, boolean uidMode, boolean target64, boolean targetLE){
			int flags = (wave_info.getCodec() & 0xf) << 4;
			flags |= (wave_info.getMedium() & 0x3) << 2;
			buffer.addToFile((byte)flags);
			buffer.addToFile(wave_info.getU2());
			buffer.addToFile((short)wave_info.getWaveSize());
			if(target64){
				buffer.addToFile(0);
				if(!uidMode) buffer.addToFile(Integer.toUnsignedLong(wave_info.getWaveOffset()));
				else {
					if(targetLE){
						buffer.addToFile(wave_info.getUID());
						buffer.addToFile(0);
					}
					else{
						buffer.addToFile(0);
						buffer.addToFile(wave_info.getUID());
					}
				}
			}
			else{
				if(!uidMode) buffer.addToFile(wave_info.getWaveOffset());
				else buffer.addToFile(wave_info.getUID());	
			}
			
			if(loop != null) loop_offset = loop.addr;
			if(pred != null) pred_offset = pred.addr;
			if(target64){
				buffer.addToFile((long)loop_offset);
				buffer.addToFile((long)pred_offset);
			}
			else{
				buffer.addToFile(loop_offset);
				buffer.addToFile(pred_offset);	
			}
			
			return serialSize(target64);
		}

		public Z64WaveInfo getWaveInfo(){
			//Also updates it!!
			wave_info.setAddressInBank(addr);
			if(pred != null){
				wave_info.setADPCMBook(pred.loadToTable());
			}
			if(loop != null){
				wave_info.setLoopStart(loop.start);
				wave_info.setLoopEnd(loop.end);
				wave_info.setLoopCount(loop.count);
				wave_info.setLoopState(loop.state_vals);
			}
			return wave_info;
		}
		
		public WaveInfoBlock copy(boolean keepWaveLink) {
			WaveInfoBlock copy = new WaveInfoBlock();
			super.copyTo(copy);
			copy.loop_offset = this.loop_offset;
			copy.pred_offset = this.pred_offset;
			if(loop != null) copy.loop = this.loop.copy();
			if(pred != null) copy.pred = this.pred.copy();
			if(keepWaveLink) {
				copy.wave_info = this.wave_info;
			}
			else {
				if(wave_info != null) {
					copy.wave_info = this.wave_info.copy();
				}
			}
			
			return copy;
		}
		
	}
	
	protected static class InstBlock extends BankBlock{
		//private boolean valid = true;
		
		protected int off_env;
		protected int off_snd_lo;
		protected int off_snd_med;
		protected int off_snd_hi;
		
		protected EnvelopeBlock envelope;
		protected WaveInfoBlock snd_lo;
		protected WaveInfoBlock snd_med;
		protected WaveInfoBlock snd_hi;
		
		protected Z64Instrument data;
		
		public InstBlock(){data = new Z64Instrument();}
		public InstBlock(Z64Instrument inst){
			data = inst;
		}
		
		public static InstBlock readFrom(BufferReference ptr){
			InstBlock block = new InstBlock();
			ptr.increment(); //First field is "loaded" byte
			
			block.data.setLowRangeTop(ptr.nextByte());
			block.data.setHighRangeBottom(ptr.nextByte());
			block.data.setDecay(ptr.nextByte());
			block.off_env = ptr.nextInt();
			
			block.off_snd_lo = ptr.nextInt();
			if(block.off_snd_lo != 0){
				block.data.setTuningLow(Float.intBitsToFloat(ptr.nextInt()));
			}
			else ptr.add(4L);
			
			block.off_snd_med = ptr.nextInt();
			block.data.setTuningMiddle(Float.intBitsToFloat(ptr.nextInt()));
			
			block.off_snd_hi = ptr.nextInt();
			if(block.off_snd_hi != 0){
				block.data.setTuningHigh(Float.intBitsToFloat(ptr.nextInt()));
			}
			else ptr.add(4L);
			
			return block;
		}
		
		public static InstBlock readFrom64(BufferReference ptr){
			InstBlock block = new InstBlock();
			ptr.increment();
			block.data.setLowRangeTop(ptr.nextByte());
			block.data.setHighRangeBottom(ptr.nextByte());
			block.data.setDecay(ptr.nextByte());
			ptr.add(4L);
			block.off_env = (int)ptr.nextLong();
			
			block.off_snd_lo = (int)ptr.nextLong();
			if(block.off_snd_lo != 0){
				block.data.setTuningLow(Float.intBitsToFloat(ptr.nextInt()));
				ptr.add(4L);
			}
			else ptr.add(8L);
			
			block.off_snd_med = (int)ptr.nextLong();
			block.data.setTuningMiddle(Float.intBitsToFloat(ptr.nextInt()));
			ptr.add(4L);
			
			block.off_snd_hi = (int)ptr.nextLong();
			if(block.off_snd_hi != 0){
				block.data.setTuningHigh(Float.intBitsToFloat(ptr.nextInt()));
				ptr.add(4L);
			}
			else ptr.add(8L);
			
			return block;
		}
		
		public int serialSize(){return serialSize(false);}
		public int serialSize(boolean target64){
			return (data == null) ? 0:(target64 ? BLOCKSIZE_INST_64 : BLOCKSIZE_INST);
		}
		
		public int serializeTo(FileBuffer buffer, boolean uidMode){
			return serializeTo(buffer, uidMode, false, false);
		}
		
		public int serializeTo(FileBuffer buffer, boolean uidMode, boolean target64, boolean targetLE){
			if(data == null) return 0;
			buffer.addToFile(FileBuffer.ZERO_BYTE);
			buffer.addToFile(data.getLowRangeTop());
			buffer.addToFile(data.getHighRangeBottom());
			buffer.addToFile(data.getDecay());
			
			if(target64) buffer.addToFile(0);
			
			if(envelope != null) off_env = envelope.addr;
			if(snd_med != null) off_snd_med = snd_med.addr;
			if(snd_lo != null) off_snd_lo = snd_lo.addr;
			else off_snd_lo = 0;
			if(snd_hi != null) off_snd_hi = snd_hi.addr;
			else off_snd_hi = 0;
			
			if(target64){
				buffer.addToFile((long)off_env);
				buffer.addToFile((long)off_snd_lo);
				if(off_snd_lo != 0) {buffer.addToFile(Float.floatToRawIntBits(data.getTuningLow())); buffer.addToFile(0);}
				else buffer.addToFile(0L);
				
				buffer.addToFile((long)off_snd_med);
				buffer.addToFile(Float.floatToRawIntBits(data.getTuningMiddle()));
				buffer.addToFile(0);
				
				buffer.addToFile((long)off_snd_hi);
				if(off_snd_hi != 0) {buffer.addToFile(Float.floatToRawIntBits(data.getTuningHigh())); buffer.addToFile(0);}
				else buffer.addToFile(0L);	
			}
			else{
				buffer.addToFile(off_env);
				
				buffer.addToFile(off_snd_lo);
				if(off_snd_lo != 0) buffer.addToFile(Float.floatToRawIntBits(data.getTuningLow()));
				else buffer.addToFile(0);
				
				buffer.addToFile(off_snd_med);
				buffer.addToFile(Float.floatToRawIntBits(data.getTuningMiddle()));
				
				buffer.addToFile(off_snd_hi);
				if(off_snd_hi != 0) buffer.addToFile(Float.floatToRawIntBits(data.getTuningHigh()));
				else buffer.addToFile(0);	
			}
			
			return serialSize();
		}
		
		public InstBlock copy(Z64WavePool wavePool, Z64EnvPool envPool) {
			InstBlock copy = new InstBlock();
			super.copyTo(copy);
			copy.off_env = this.off_env;
			copy.off_snd_hi = this.off_snd_hi;
			copy.off_snd_lo = this.off_snd_lo;
			copy.off_snd_med = this.off_snd_med;
			if(this.data != null) copy.data = this.data.copy();
			if(this.envelope != null) {
				copy.envelope = envPool.findMatchInPool(this.envelope.data);
			}
			if(this.snd_med != null) {
				Z64WaveInfo winfo = this.snd_med.getWaveInfo();
				if(winfo != null) {
					copy.snd_med = wavePool.getByUID(winfo.getUID());
				}
			}
			if(this.snd_lo != null) {
				Z64WaveInfo winfo = this.snd_lo.getWaveInfo();
				if(winfo != null) {
					copy.snd_lo = wavePool.getByUID(winfo.getUID());
				}
			}
			if(this.snd_hi != null) {
				Z64WaveInfo winfo = this.snd_hi.getWaveInfo();
				if(winfo != null) {
					copy.snd_hi = wavePool.getByUID(winfo.getUID());
				}
			}
			return copy;
		}
		
	}
	
	protected static class PercBlock extends BankBlock{
		//private boolean valid = true;
		
		protected int off_env;
		protected int off_snd;
		protected float tune = 1.0f;
		protected int index = 0;
		
		protected EnvelopeBlock envelope;
		protected WaveInfoBlock sample;
		
		protected Z64Drum data;
		
		private PercBlock() {}
		public PercBlock(int note){data = new Z64Drum(); index = note;}
		public PercBlock(Z64Drum drum, int note){
			data = drum;
			index = note;
		}
		
		public static PercBlock readFrom(BufferReference ptr, int idx){
			PercBlock block = new PercBlock(idx);
			block.data.setDecay(ptr.nextByte());
			block.data.setPan(ptr.nextByte());
			ptr.add(2L);
			block.off_snd = ptr.nextInt();
			block.tune = Float.intBitsToFloat(ptr.nextInt());
			block.off_env = ptr.nextInt();
			block.updateCommonTuning();
			block.enm_str = String.format("PERC%02d", idx);
			return block;
		}
		
		public static PercBlock readFrom64(BufferReference ptr, int idx){
			PercBlock block = new PercBlock(idx);
			block.data.setDecay(ptr.nextByte());
			block.data.setPan(ptr.nextByte());
			ptr.add(6L);
			block.off_snd = (int)ptr.nextLong();
			block.tune = Float.intBitsToFloat(ptr.nextInt());
			ptr.add(4L);
			block.off_env = (int)ptr.nextLong();
			block.updateCommonTuning();
			block.enm_str = String.format("PERC%02d", idx);
			return block;
		}
		
		protected void updateCommonTuning(){
			if (data == null) return;
			data.setTuning(Z64Drum.localToCommonTuning(index, tune));
		}
		
		protected void updateLocalTuning(){
			if (data == null) return;
			tune = Z64Drum.commonToLocalTuning(index, data.getTuning());
		}
		
		public int serialSize(){return serialSize(false);}
		public int serialSize(boolean target64){
			return (data == null) ? 0:(target64 ? BLOCKSIZE_PERC_64 : BLOCKSIZE_PERC);
		}
		
		public int serializeTo(FileBuffer buffer, boolean uidMode){
			return serializeTo(buffer, uidMode, false, false);
		}
		
		public int serializeTo(FileBuffer buffer, boolean uidMode, boolean target64, boolean targetLE){
			if(data == null) return 0;
			buffer.addToFile(data.getDecay());
			buffer.addToFile(data.getPan());
			buffer.addToFile((short)0);
			if(target64) buffer.addToFile(0);
			
			if(envelope != null) off_env = envelope.addr;
			if(sample != null) off_snd = sample.addr;
			updateLocalTuning();
			
			if(target64){
				buffer.addToFile((long)off_snd);
				buffer.addToFile(Float.floatToRawIntBits(tune));
				buffer.addToFile(0);
				buffer.addToFile((long)off_env);
			}
			else{
				buffer.addToFile(off_snd);
				buffer.addToFile(Float.floatToRawIntBits(tune));
				buffer.addToFile(off_env);
			}
			
			return serialSize();
		}
		
		public PercBlock copy(Z64WavePool wavePool, Z64EnvPool envPool) {
			PercBlock copy = new PercBlock();
			super.copyTo(copy);
			copy.index = this.index;
			copy.tune = this.tune;
			copy.off_env = this.off_env;
			copy.off_snd = this.off_snd;
			if(this.data != null) copy.data = this.data.copy();
			if(this.envelope != null) {
				copy.envelope = envPool.findMatchInPool(this.envelope.data);
			}
			if(this.sample != null) {
				Z64WaveInfo winfo = this.sample.getWaveInfo();
				if(winfo != null) {
					copy.sample = wavePool.getByUID(winfo.getUID());
				}
			}
			return copy;
		}
		
	}

	protected static class SFXBlock extends BankBlock{
		//private boolean valid = true;
		
		protected int off_snd;
		protected WaveInfoBlock sample;
		
		protected Z64SoundEffect data;
		
		public SFXBlock(){data = new Z64SoundEffect();}
		public SFXBlock(Z64SoundEffect sfx){data = sfx;}
		
		public static SFXBlock readFrom(BufferReference ptr){
			SFXBlock block = new SFXBlock();
			block.off_snd = ptr.nextInt();
			block.data.setTuning(Float.intBitsToFloat(ptr.nextInt()));
			return block;
		}
		
		public static SFXBlock readFrom64(BufferReference ptr){
			SFXBlock block = new SFXBlock();
			block.off_snd = (int)ptr.nextLong();
			block.data.setTuning(Float.intBitsToFloat(ptr.nextInt()));
			ptr.add(4L);//Pad
			return block;
		}
		
		public int serialSize(){return serialSize(false);}
		public int serialSize(boolean target64){
			return (data == null) ? 0:(target64 ? BLOCKSIZE_SFX_64 : BLOCKSIZE_SFX);
		}
		
		public int serializeTo(FileBuffer buffer, boolean uidMode){
			return serializeTo(buffer, uidMode, false, false);
		}
		
		public int serializeTo(FileBuffer buffer, boolean uidMode, boolean target64, boolean targetLE){
			if(data == null) return 0;
			if(sample != null) off_snd = sample.addr;
			if(target64){
				buffer.addToFile((long)off_snd);
				buffer.addToFile(Float.floatToRawIntBits(data.getTuning()));
				buffer.addToFile(0);
			}
			else{
				buffer.addToFile(off_snd);
				buffer.addToFile(Float.floatToRawIntBits(data.getTuning()));	
			}
			return serialSize();
		}
		
		public SFXBlock copy(Z64WavePool wavePool) {
			SFXBlock copy = new SFXBlock();
			super.copyTo(copy);
			copy.off_snd = this.off_snd;
			if(this.data != null) copy.data = this.data.copy();
			if(this.sample != null) {
				Z64WaveInfo winfo = this.sample.getWaveInfo();
				if(winfo != null) {
					copy.sample = wavePool.getByUID(winfo.getUID());
				}
			}
			return copy;
		}
		
	}
	
	protected static class PercOffsetTableBlock extends BankBlock{

		protected List<Integer> offsets;
		
		public PercOffsetTableBlock(){
			offsets = new LinkedList<Integer>();
		}
		
		public int serialSize(){return serialSize(false);}
		public int serialSize(boolean target64) {
			if(target64) return offsets.size() << 3;
			return offsets.size() << 2;
		}
		
		public int serializeTo(FileBuffer buffer, boolean uidMode){
			return serializeTo(buffer, uidMode, false, false);
		}

		public int serializeTo(FileBuffer buffer, boolean uidMode, boolean target64, boolean targetLE) {
			int sz = 0;
			if(target64){
				for(Integer off : offsets) {buffer.addToFile(off); sz+=8;}
			}
			else{
				for(Integer off : offsets) {buffer.addToFile(off); sz+=4;}	
			}
			return sz;
		}
		
	}

}
