package waffleoRai_soundbank.nintendo.z64;

import java.util.LinkedList;
import java.util.List;

import waffleoRai_Sound.nintendo.N64ADPCMTable;
import waffleoRai_Sound.nintendo.Z64WaveInfo;
import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.FileBuffer;

class Z64BankBlocks {
	
	protected static abstract class BankBlock{
		protected int addr;
		protected boolean flag = false;
		
		public abstract int serialSize();
		public abstract int serializeTo(FileBuffer buffer, boolean uidMode);
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
		
		public int serialSize(){return (count!=0)?48:16;}
		
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
		
		public int serializeTo(FileBuffer buffer, boolean uidMode){
			int tsz = (order * count) << 3;
			
			buffer.addToFile(order);
			buffer.addToFile(count);
			for(int i = 0; i < tsz; i++) buffer.addToFile(table[i]);
			
			return 8 + (tsz << 1);
		}
		
		public N64ADPCMTable loadToTable(){
			return N64ADPCMTable.fromRaw(order, count, table);
		}
		
	}
	
	protected static class EnvelopeBlock extends BankBlock{
		
		protected Z64Envelope data;
		
		public EnvelopeBlock(){data = new Z64Envelope();}
		
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
		
		public int serialSize(){return 16;}
		
		public int serializeTo(FileBuffer buffer, boolean uidMode){
			int flags = (wave_info.getCodec() & 0xf) << 4;
			flags |= (wave_info.getMedium() & 0x3) << 2;
			buffer.addToFile((byte)flags);
			buffer.addToFile(wave_info.getU2());
			buffer.addToFile((short)wave_info.getWaveSize());
			if(!uidMode) buffer.addToFile(wave_info.getWaveOffset());
			else buffer.addToFile(wave_info.getUID());
			
			if(loop != null) loop_offset = loop.addr;
			if(pred != null) pred_offset = pred.addr;
			buffer.addToFile(loop_offset);
			buffer.addToFile(pred_offset);
			
			return 16;
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
		
		public int serialSize(){return (data==null)?0:32;}
		
		public int serializeTo(FileBuffer buffer, boolean uidMode){
			if(data == null) return 0;
			buffer.addToFile(FileBuffer.ZERO_BYTE);
			buffer.addToFile(data.getLowRangeTop());
			buffer.addToFile(data.getHighRangeBottom());
			buffer.addToFile(data.getDecay());
			
			if(envelope != null) off_env = envelope.addr;
			if(snd_med != null) off_snd_med = snd_med.addr;
			if(snd_lo != null) off_snd_lo = snd_lo.addr;
			else off_snd_lo = 0;
			if(snd_hi != null) off_snd_hi = snd_hi.addr;
			else off_snd_hi = 0;
			
			buffer.addToFile(off_env);
			
			buffer.addToFile(off_snd_lo);
			if(off_snd_lo != 0) buffer.addToFile(Float.floatToRawIntBits(data.getTuningLow()));
			else buffer.addToFile(0);
			
			buffer.addToFile(off_snd_med);
			buffer.addToFile(Float.floatToRawIntBits(data.getTuningMiddle()));
			
			buffer.addToFile(off_snd_hi);
			if(off_snd_hi != 0) buffer.addToFile(Float.floatToRawIntBits(data.getTuningHigh()));
			else buffer.addToFile(0);
			
			return 32;
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
		
		public PercBlock(int note){data = new Z64Drum(); index = note;}
		
		public static PercBlock readFrom(BufferReference ptr, int idx){
			PercBlock block = new PercBlock(idx);
			block.data.setDecay(ptr.nextByte());
			block.data.setPan(ptr.nextByte());
			ptr.add(2L);
			block.off_snd = ptr.nextInt();
			block.tune = Float.intBitsToFloat(ptr.nextInt());
			block.off_env = ptr.nextInt();
			block.updateCommonTuning();
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
		
		public int serialSize(){return (data==null)?0:16;}
		
		public int serializeTo(FileBuffer buffer, boolean uidMode){
			if(data == null) return 0;
			buffer.addToFile(data.getDecay());
			buffer.addToFile(data.getPan());
			buffer.addToFile((short)0);
			
			if(envelope != null) off_env = envelope.addr;
			if(sample != null) off_snd = sample.addr;
			updateLocalTuning();
			
			buffer.addToFile(off_snd);
			buffer.addToFile(Float.floatToRawIntBits(tune));
			buffer.addToFile(off_env);
			
			return 16;
		}
		
	}

	protected static class SFXBlock extends BankBlock{
		//private boolean valid = true;
		
		protected int off_snd;
		protected WaveInfoBlock sample;
		
		protected Z64SoundEffect data;
		
		public SFXBlock(){data = new Z64SoundEffect();}
		
		public static SFXBlock readFrom(BufferReference ptr){
			SFXBlock block = new SFXBlock();
			block.off_snd = ptr.nextInt();
			block.data.setTuning(Float.intBitsToFloat(ptr.nextInt()));
			return block;
		}
		
		public int serialSize(){return (data==null)?0:8;}
		
		public int serializeTo(FileBuffer buffer, boolean uidMode){
			if(data == null) return 0;
			if(sample != null) off_snd = sample.addr;
			buffer.addToFile(off_snd);
			buffer.addToFile(Float.floatToRawIntBits(data.getTuning()));
			return 8;
		}
		
	}
	
	protected static class PercOffsetTableBlock extends BankBlock{

		protected List<Integer> offsets;
		
		public PercOffsetTableBlock(){
			offsets = new LinkedList<Integer>();
		}
		
		public int serialSize() {
			return offsets.size() << 2;
		}

		public int serializeTo(FileBuffer buffer, boolean uidMode) {
			int sz = 0;
			for(Integer off : offsets) {buffer.addToFile(off); sz+=4;}
			return sz;
		}
		
	}

}
