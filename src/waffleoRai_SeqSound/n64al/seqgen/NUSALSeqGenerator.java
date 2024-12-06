package waffleoRai_SeqSound.n64al.seqgen;

import waffleoRai_SoundSynth.SequenceController;

import java.util.ArrayList;

import waffleoRai_SeqSound.MIDI;
import waffleoRai_SeqSound.MIDIControllers;
import waffleoRai_SeqSound.n64al.cmd.SeqCommands.*;
import waffleoRai_SeqSound.n64al.NUSALSeq;
import waffleoRai_SeqSound.n64al.NUSALSeqCommandBook;
import waffleoRai_SeqSound.n64al.cmd.ChannelCommands.*;

public class NUSALSeqGenerator implements SequenceController{
	
	private static final int VCT_DEFO_ALLOC = 0x8000;
	
	/*----- Instance Variables -----*/
	
	private int input_timebase = 48;
	private double timebase_factor = 1.0;
	private boolean cap_halve_tempo = false;
	
	private int tick_in = 0;
	private double tick_out = 0.0;
	
	private NUSALSeqBuilder builder;
	private NUSALSeq output;
	private NUSALSeqCommandBook cmdBook;
	
	private int pb_range = 800; //In cents
	private int loop_st = -1;
	private int loop_ed = -1; //Note so that can insert jump...
	
	private boolean remap_prog = false; 
	private ArrayList<Integer> program_map;
	private int last_bank = -1;
	//private int last_prog = -1;
	
	//Counting simultaneous voices across whole sequence
	//Used to see if there are too many at once
	private boolean auto_cap_total_vox = false;
	private byte[] voice_counts;
	private byte[][] voice_counts_ch;
	private int vct_len = 0;
	private int max_vox = 17;
	
	//TODO Error/warning flags
	private boolean vox_over_flag = false;
	private boolean tempo_cap_flag = false;
	private boolean chvox_over_flag = false;
	private boolean lost_ctrl_event_flag = false;
	private boolean lost_nrpn_event_flag = false;
	
	/*----- Init -----*/
	
	public NUSALSeqGenerator(){
		this(null);
	}
	
	public NUSALSeqGenerator(NUSALSeqCommandBook cmdBook){
		builder = new NUSALSeqBuilder(cmdBook);
		builder.setCompression(true);
		cmdBook = builder.getCommandBook();
	}
	
	/*----- Getters -----*/
	
	public int getLoopStart(){return loop_st;}
	public int getLoopEnd(){return loop_ed;}
	public int getPitchbendRange(){return pb_range;}
	public int getMaximumLayerDelay(){return builder.getMaximumLayerDelay();}
	public int getMinRandomGateBoundary(){return builder.getMinRandomGateBoundary();}
	public int getMaxRandomGateBoundary(){return builder.getMaxRandomGateBoundary();}
	public double getSamegateChance(){return builder.getSamegateChance();}
	public int getDefaultChannelPriority(int ch){return builder.getDefaultChannelPriority(ch);}
	public NUSALSeq getOutput(){return output;}
	public boolean getVoxOverFlag() {return vox_over_flag;}
	public boolean getTempoCapFlag() {return tempo_cap_flag;}
	public boolean getChannelVoxOverFlag() {return chvox_over_flag;}
	public boolean getLostControllerEventFlag() {return lost_ctrl_event_flag;}
	public boolean getLostNRPNEventFlag() {return lost_nrpn_event_flag;}
	
	public byte[] getVoicesAtTickTable(){
		return voice_counts;
	}
	
	public byte[] getVoicesAtTickTable(int channel){
		if(voice_counts_ch == null) return null;
		return voice_counts_ch[channel];
	}
	
	public int getGateLeeway(){
		NUSALSequenceCompressor comp = builder.getCompressor();
		if(comp == null) return -1;
		return comp.getGateLeeway();
	}
	
	public int getVelocityLeeway(){
		NUSALSequenceCompressor comp = builder.getCompressor();
		if(comp == null) return -1;
		return comp.getVelocityLeeway();
	}
	
	public int getTimeLeeway(){
		NUSALSequenceCompressor comp = builder.getCompressor();
		if(comp == null) return -1;
		return comp.getTimeLeeway();
	}
	
	public int getMinimumCommandsPerSub(){
		NUSALSequenceCompressor comp = builder.getCompressor();
		if(comp == null) return -1;
		return comp.getMinimumCommandsPerSub();
	}
	
	public int getCompressionReplacementCount(){
		NUSALSequenceCompressor comp = builder.getCompressor();
		if(comp == null) return 0;
		return comp.getLastReplacementsCount();
	}
	
	public int getCompressionBytesSaved(){
		NUSALSequenceCompressor comp = builder.getCompressor();
		if(comp == null) return 0;
		return comp.getLastBytesSavedCount();
	}
	
	public int getOutTick(){
		return (int)Math.floor(tick_out);
	}
	
	/*----- Setters -----*/
	
	public void setTimebase(int ppqn){
		input_timebase = ppqn;
		timebase_factor = 48.0/(double)input_timebase;
	}
	
	public void setLoop(int st_tick, int ed_tick){
		loop_st = st_tick;
		loop_ed = ed_tick;
		if(loop_st > 0) loop_st = (int)Math.floor((double)loop_st * timebase_factor);
		if(loop_ed > 0) loop_ed = (int)Math.floor((double)loop_ed * timebase_factor);
		builder.addLoop(loop_st, loop_ed);
		//if(loop_st > 0) builder.addTimeBreak(loop_st);
		//if(loop_ed > 0) builder.addTimeBreak(loop_ed);
	}
	
	public void addEntryPoint(int tick){
		//NOT for loops.
		if(tick <= 0) return;
		tick = (int)Math.floor((double)tick*timebase_factor);
		builder.addTimeBreak(tick);
	}
	
	public void setProgramRemap(boolean b){
		remap_prog = b;
		if(remap_prog && program_map == null){
			program_map = new ArrayList<Integer>(128);
		}
	}
	
	public void setPitchBendRange(int ch, int cents) {
		pb_range = cents;
	}
	
	public void setGateLeeway(int value){
		NUSALSequenceCompressor comp = builder.getCompressor();
		if(comp == null) return;
		comp.setGateLeeway(value);
	}
	
	public void setVelocityLeeway(int value){
		NUSALSequenceCompressor comp = builder.getCompressor();
		if(comp == null) return;
		comp.setVelocityLeeway(value);
	}
	
	public void setTimeLeeway(int value){
		NUSALSequenceCompressor comp = builder.getCompressor();
		if(comp == null) return;
		comp.setTimeLeeway(value);
	}
	
	public void setMinimumCommandsPerSub(int value){
		NUSALSequenceCompressor comp = builder.getCompressor();
		if(comp == null) return;
		comp.setMinimumCommandsPerSub(value);
	}
	
	public void setMaxLayerDelay(int ticks){builder.setMaxLayerDelay(ticks);}
	public void setMinRandomGateBoundary(int value){builder.setMinRandomGateBoundary(value);}
	public void setMaxRandomGateBoundary(int value){builder.setMaxRandomGateBoundary(value);}
	public void setSamegateChance(double value){builder.setSamegateChance(value);}
	public void setDefaultChannelPriority(int ch, int val){builder.setDefaultChannelPriority(ch, val);}
	public void disableCompression(){builder.setCompression(false);}
	public void enableCompression(){builder.setCompression(true);}
	public void resetVoxOverFlag() {vox_over_flag = false;}
	
	public void setMaxTotalVox(int val) {max_vox = val;}
	public void setAutoCapVox(boolean flag) {auto_cap_total_vox = flag;}
	public void setTempoCapHalve(boolean flag) {cap_halve_tempo = flag;}
	
	/*----- Seq Control -----*/
	
	// ---> Player
	
	private void updateChannelVoiceCounts(int outTick) {
		if(voice_counts_ch == null) {
			voice_counts_ch = new byte[16][];
		}
		
		for(int c = 0; c < 16; c++) {
			if(voice_counts_ch[c] == null || voice_counts_ch[c].length <= outTick){
				//Alloc or realloc
				byte[] vc_temp = voice_counts_ch[c];
				int alloc = VCT_DEFO_ALLOC;
				if(vc_temp != null) alloc += vc_temp.length;
				voice_counts_ch[c] = new byte[alloc];
				if(vc_temp != null){
					for(int i = 0; i < vc_temp.length; i++){
						voice_counts_ch[c][i] = vc_temp[i];
					}
				}
				vc_temp = null;
			}
			
			int onvox = builder.voicesOn(c);
			int temp = vct_len;
			while(temp < outTick){
				voice_counts_ch[c][temp++] = (byte)onvox;
			}
		}
	}
	
	public long advanceTick() {
		//Calculate next (output) tick value
		tick_in++;
		int last_out_tick = getOutTick();
		tick_out = (double)tick_in * timebase_factor;
		int this_out_tick = getOutTick();
		
		//If increased - count voices from previous tick
		if(this_out_tick > last_out_tick){
			updateChannelVoiceCounts(this_out_tick);
			if(voice_counts == null || voice_counts.length <= this_out_tick){
				//Alloc or realloc
				byte[] vc_temp = voice_counts;
				int alloc = VCT_DEFO_ALLOC;
				if(vc_temp != null) alloc += vc_temp.length;
				voice_counts = new byte[alloc];
				if(vc_temp != null){
					for(int i = 0; i < vc_temp.length; i++){
						voice_counts[i] = vc_temp[i];
					}
				}
				vc_temp = null;
			}
			
			int onvox = builder.voicesOn();
			while(vct_len < this_out_tick){
				voice_counts[vct_len++] = (byte)onvox;
			}
		}
		
		return tick_in;
	}

	public void complete() {
		//Add end commands and do compression/optimization!
		output = builder.buildSeq();
	}
	
	// ---> Seq Level
	public void setMasterVolume(byte value) {
		if(tick_in == 0){
			builder.setInitVol(Byte.toUnsignedInt(value));
			return;
		}
		builder.addSeqCommandAtTick(getOutTick(), new C_S_SetMasterVolume(cmdBook, Byte.toUnsignedInt(value)));
	}

	public void setMasterExpression(byte value) {
		builder.addSeqCommandAtTick(getOutTick(), new C_S_SetMasterExpression(cmdBook, Byte.toUnsignedInt(value)));
	}

	public void setMasterPan(byte value) {}

	public void setTempo(int tempo_uspqn) {
		//Cap
		int bpm = MIDI.uspqn2bpm(tempo_uspqn, 48.0);
		if(bpm > NUSALSeq.MAX_TEMPO) {
			tempo_cap_flag = true;
			if(cap_halve_tempo) {
				while(bpm > NUSALSeq.MAX_TEMPO) {
					bpm >>>= 1;
					setTimebase(input_timebase >>> 1);
				}
			}
			else bpm = NUSALSeq.MAX_TEMPO;
		}
		
		if(tick_in == 0){
			builder.setInitTempo(bpm);
			return;
		}
		builder.addSeqCommandAtTick(getOutTick(), new C_S_SetTempo(cmdBook, bpm));
	}

	public void addMarkerNote(String note) {}
	
	public void setTimeSignature(int beats, int div) {
		builder.setTimeSignature(beats, div);
	}
	
	// ---> Channel Level
	
	public void setChannelVolume(int ch, byte value) {
		builder.addChannelCommandAtTick(getOutTick(), ch, new C_C_Volume(cmdBook, Byte.toUnsignedInt(value)));
	}

	public void setChannelExpression(int ch, byte value) {
		builder.addChannelCommandAtTick(getOutTick(), ch, new C_C_Expression(cmdBook, Byte.toUnsignedInt(value)));
	}

	public void setChannelPan(int ch, byte value) {
		builder.addChannelCommandAtTick(getOutTick(), ch, new C_C_Pan(cmdBook, value));
	}

	public void setChannelPriority(int ch, byte value) {
		builder.addChannelCommandAtTick(getOutTick(), ch, new C_C_Priority(cmdBook, value));
	}
	
	public void setPitchWheel(int ch, short value) {
		//Derive from MIDI val
		int val = (int)value;
		val -= 0x2000;
		double ratio = (double)0x1fff * (double)val;
		ratio *= 127.0;
		builder.addChannelCommandAtTick(getOutTick(), ch, new C_C_PitchBend(cmdBook, (int)Math.round(ratio)));
	}

	public void setPitchBend(int ch, int cents) {
		double amt = (double)cents/(double)pb_range;
		amt *= 127.0;
		builder.addChannelCommandAtTick(getOutTick(), ch, new C_C_PitchBend(cmdBook, (int)Math.round(amt)));
	}
	
	public void setReverbSend(int ch, byte value) {
		builder.addChannelCommandAtTick(getOutTick(), ch, new C_C_Reverb(cmdBook, value));
	}

	public void setTremoloSend(int ch, byte value) {} //Doesn't have tremolo. I guess I could replace it with vibrato?
	
	public void setChorusSend(int ch, byte value) {
		//TODO Unknown what the parameters are?
		builder.addChannelCommandAtTick(getOutTick(), ch, new C_C_Chorus(cmdBook, value, 0));
	}
	
	public void setVibratoSpeed(int ch, double value) {
		value *= 255.0;
		builder.addChannelCommandAtTick(getOutTick(), ch, new C_C_VibratoFreq(cmdBook, (int)Math.round(value)));
	}

	public void setVibratoAmount(int ch, byte value) {
		builder.addChannelCommandAtTick(getOutTick(), ch, new C_C_VibratoDepth(cmdBook, value));
	}
	
	public void setProgram(int ch, int bank, int program) {
		//System.err.println("NUSALSeqGenerator.setProgram (w/bank) || Called -- ch = "  + ch + ", bank = " + bank + ", program = " + program);
		if(remap_prog){
			if(program_map == null) program_map = new ArrayList<Integer>(128);
			int lookup = (bank & 0xffff) << 8;
			lookup |= (program & 0xff);
			//System.err.println("NUSALSeqGenerator.setProgram (w/bank) || lookup = 0x" + String.format("%08x", lookup));
			int p = -1; int sz = program_map.size();
			for(int i = 0; i < sz; i++){
				if(lookup == program_map.get(i)){
					p = i;
					break;
				}
			}
			if(p < 0){
				p = sz;
				program_map.add(lookup);
			}
			builder.addChannelCommandAtTick(getOutTick(), ch, new C_C_ChangeProgram(cmdBook, p));
			last_bank = bank;
			//last_prog = program;
		}
		else{
			builder.addChannelCommandAtTick(getOutTick(), ch, new C_C_ChangeBank(cmdBook, bank));
			builder.addChannelCommandAtTick(getOutTick(), ch, new C_C_ChangeProgram(cmdBook, program));
		}
	}

	public void setProgram(int ch, int program) {
		//System.err.println("NUSALSeqGenerator.setProgram || Called -- ch = "  + ch + ", program = " + program);
		if(remap_prog){
			if(program_map == null) program_map = new ArrayList<Integer>(128);
			int lookup = (last_bank & 0xffff) << 8;
			lookup |= (program & 0xff);
			//System.err.println("NUSALSeqGenerator.setProgram (w/bank) || lookup = 0x" + String.format("%08x", lookup));
			int p = -1; int sz = program_map.size();
			for(int i = 0; i < sz; i++){
				if(lookup == program_map.get(i)){
					p = i;
					break;
				}
			}
			if(p < 0){
				p = sz;
				program_map.add(lookup);
			}
			builder.addChannelCommandAtTick(getOutTick(), ch, new C_C_ChangeProgram(cmdBook, p));
			//last_prog = program;
		}
		else{
			builder.addChannelCommandAtTick(getOutTick(), ch, new C_C_ChangeProgram(cmdBook, program));
		}
	}
	
	// ---> Layer Level
	
	public void setLegato(int ch, boolean on) {
		builder.setLegatoOn(getOutTick(), ch, on);
	}
	
	public void setPortamentoTime(int ch, short value) {
		builder.setPortamentoTime(getOutTick(), ch, value);
	}

	public void setPortamentoAmount(int ch, byte value) {
		builder.setPortamentoAmount(getOutTick(), ch, value);
	}

	public void setPortamentoOn(int ch, boolean on) {
		builder.setPortamentoOn(getOutTick(), ch, on);
	}
	
	public void noteOn(int ch, byte note, byte vel) {
		int tt = getOutTick();
		builder.sendNoteOn(tt, ch, note, vel);
		int vCount = builder.voicesOn();
		if(vCount > max_vox) {
			vox_over_flag = true;
			if(auto_cap_total_vox) {
				builder.killOldestNote(tt);
			}
		}
		if(builder.anyChannelVoxOverflows()) chvox_over_flag = true;
	}

	public void noteOff(int ch, byte note, byte vel) {
		noteOff(ch, note);
	}

	public void noteOff(int ch, byte note) {
		builder.sendNoteOff(tick_out, ch, note);
	}
	
	// ---> Misc. Translate from MIDI style

	public void setModWheel(int ch, short value) {
		//Directs to vib freq
		double amt = (double)value / (double)0xffff;
		this.setVibratoSpeed(ch, amt);
	}

	public void setControllerValue(int ch, int controller, byte value) {
		setControllerValue(ch, controller, Byte.toUnsignedInt(value) << 8, true);
	}

	public void setControllerValue(int ch, int controller, int value, boolean omitFine) {
		switch(controller) {
		case MIDIControllers.BANK_SELECT:
			break;
		case MIDIControllers.CHORUS_SEND:
			setChorusSend(ch, (byte)(value >>> 8));
			break;
		case MIDIControllers.DATA_ENTRY:
		case MIDIControllers.DATA_ENTRY_LSB:
			break;
		case MIDIControllers.EFFECTS_1:
			setEffect1(ch, (byte)(value >>> 8));
			break;
		case MIDIControllers.EFFECTS_2:
			setEffect2(ch, (byte)(value >>> 8));
			break;
		case MIDIControllers.EXPRESSION:
			setChannelExpression(ch, (byte)(value >>> 8));
			break;
		case MIDIControllers.LEGATO_ON:
			setLegato(ch, value != 0);
			break;
		case MIDIControllers.MOD_WHEEL:
		case MIDIControllers.MOD_WHEEL_LSB:
			setModWheel(ch, (short)value);
			break;
		case MIDIControllers.NRPN_LSB:
		case MIDIControllers.NRPN_MSB:
			break;
		case MIDIControllers.PAN:
			setChannelPan(ch, (byte)(value >>> 8));
			break;
		case MIDIControllers.PORTAMENTO_CONTROL:
			setPortamentoAmount(ch, (byte)(value >>> 8));
			break;
		case MIDIControllers.PORTAMENTO_ON:
			setPortamentoOn(ch, value != 0);
			break;
		case MIDIControllers.PORTAMENTO_TIME:
		case MIDIControllers.PORTAMENTO_TIME_LSB:
			setPortamentoTime(ch, (short)value);
			break;
		case MIDIControllers.REVERB_SEND:
			setReverbSend(ch, (byte)(value >>> 8));
			break;
		case MIDIControllers.TREMOLO_SEND:
			setTremoloSend(ch, (byte)(value >>> 8));
			break;
		case MIDIControllers.VOLUME:
			setChannelVolume(ch, (byte)(value >>> 8));
			break;
		default:
			lost_ctrl_event_flag = true;
			break;
		}
	}

	public void setEffect1(int ch, byte value) {
		//Directs to reverb
		this.setReverbSend(ch, value);
	}

	public void setEffect2(int ch, byte value) {
		//Directs to vib amount
		this.setVibratoAmount(ch, value);
	}

	public void addNRPNEvent(int ch, int index, int value, boolean omitFine) {
		//Recognizes loop start, loop end, and priority (see NUSALSeq)
		switch(index) {
		case NUSALSeq.MIDI_NRPN_ID_LOOPSTART:
			builder.setLoopStart(getOutTick());
			break;
		case NUSALSeq.MIDI_NRPN_ID_LOOPEND:
			builder.setLoopEnd(getOutTick());
			break;
		case NUSALSeq.MIDI_NRPN_ID_PRIORITY:
			builder.addChannelCommandAtTick(getOutTick(), ch, new C_C_Priority(cmdBook, value));
			break;
		default:
			lost_nrpn_event_flag = true;
			break;
		}
	}

}
