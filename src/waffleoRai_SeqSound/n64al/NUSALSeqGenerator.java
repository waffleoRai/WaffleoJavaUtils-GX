package waffleoRai_SeqSound.n64al;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.sound.midi.Sequence;

import waffleoRai_SeqSound.MIDI;
import waffleoRai_SeqSound.MIDIInterpreter;
import waffleoRai_SeqSound.n64al.cmd.CMD_IgnoredCommand;
import waffleoRai_SeqSound.n64al.seqgen.NUSALSeqBuilderChannel;
import waffleoRai_SeqSound.n64al.seqgen.PhraseBuilder;
import waffleoRai_SoundSynth.SequenceController;

public class NUSALSeqGenerator implements SequenceController{
	
	/*
	 * Effects1 or Reverb NRPN interpreted as reverb/effect
	 * Effects2, vibrato NRPN, or mod wheel interpreted as vibrato 
	 * Priority NRPN can be read as priority
	 * LoopStart NRPN interpreted as loop point
	 */

	/*
	 * TODO
	 * 	Add delay track settability
	 * 	Phrasebuilder tuning parameters
	 *  Loop point setter (direct from program as opposed to marked in seq)
	 *  Manual channel priority setters (default priorities if not marked in seq)
	 */
	
	/*----- Constants -----*/
	
	protected static final int[] DEFO_CH_PRIS = {10, 11, 12, 9, 8, 15, 13, 4,
												  0, 1, 2, 3, 4, 5, 6, 7};
	
	/*----- Instance Variables -----*/
	
	private boolean enable_seqProgramChange; //Disabled by default.
	
	private int tick;
	
	private int loop_tick; //Loop start.
	//private boolean mastervol_set; //Was master vol ever set by seq?
	private int output_pb_range = 700; //Cents
	
	private Map<Integer, List<NUSALSeqCommand>> seqcmds;
	private NUSALSeqBuilderChannel[] channels;
	
	private int[][] delay_tracks; //[n][0] is channel it is repeating. [n][1] is delay in ticks.
	private int[] ch_pri; //Starting priorities for each channel if not overridden by seq
	private boolean[] ch_enable;
	private int[] input_pb_range; //Cents. Per channel.
	private PhraseBuilder phrase_builder;
	
	private Map<Integer, Integer> alt_entries; //Key = trigger, val = tick
	private int loop_end; //Only used in case of outro
	private int outro_trigger; //Variable value used to trigger outro
	
	/*----- Inner Classes -----*/
	
	/*----- Init -----*/
	
	public NUSALSeqGenerator(){
		seqcmds = new TreeMap<Integer, List<NUSALSeqCommand>>();
		channels = new NUSALSeqBuilderChannel[16];
		for(int i = 0; i < 16; i++){
			channels[i] = new NUSALSeqBuilderChannel(i);
		}
		loop_tick = -1;
		delay_tracks = new int[16][2];
		ch_pri = new int[16];
		ch_enable = new boolean[16];
		input_pb_range = new int[16];
		for(int i = 0; i < 16; i++){
			delay_tracks[i][0] = -1;
			//ch_pri[i] = -1;
			ch_enable[i] = true;
			input_pb_range[i] = 200;
		}
		phrase_builder = new PhraseBuilder();
		refreshPriorities();
		
		loop_end = -1;
		alt_entries = new TreeMap<Integer, Integer>();
	}
	
	private void refreshPriorities(){
		int p = 2;
		for(int i = 0; i < 16; i++){
			int ch = DEFO_CH_PRIS[i];
			if(ch_enable[ch]) ch_pri[ch] = p++;
			else ch_pri[ch] = -1;
		}
	}
	
	/*----- Getters -----*/
	
	/*----- Setters -----*/
	
	public void setLoopPoint(int tick){
		loop_tick = tick;
		phrase_builder.setLoopStart(loop_tick);
	}
	
	public void setDelayTrack(int delay_track, int ref_track, int delay_ticks){
		delay_tracks[delay_track][0] = ref_track;
		delay_tracks[delay_track][1] = delay_ticks;
	}
	
	public void clearDelayTrack(int delay_track){
		delay_tracks[delay_track][0] = -1;
		delay_tracks[delay_track][1] = 0;
	}
	
	private void pushPriorities(int hold_idx){
		int i = hold_idx;
		int v = ch_pri[i];
		for(int j = 0; j < 16; j++){
			if(j == i) continue;
			if(ch_pri[j] == v){
				//Increment this one.
				ch_pri[j]++;
			}
		}
		
		for(int j = 0; j < 16; j++){
			if(j == i) continue;
			pushPriorities(j);
		}
	}
	
	public void increaseChannelPriority(int ch_idx){
		if(ch_idx < 0 || ch_idx >= 16) return;
		if(ch_enable[ch_idx]){
			if(ch_pri[ch_idx] <= 2) ch_pri[ch_idx] = 2;
			else{
				ch_pri[ch_idx]--;
				pushPriorities(ch_idx);
			}
		}
	}
	
	public void decreaseChannelPriority(int ch_idx){
		if(ch_idx < 0 || ch_idx >= 16) return;
		if(ch_enable[ch_idx]){
			if(ch_pri[ch_idx] <= 2) ch_pri[ch_idx] = 2;
			else{
				ch_pri[ch_idx]++;
				pushPriorities(ch_idx);
			}
		}
	}
	
	public void setChannelBasePriority(int ch_idx, int val){
		//This method DOES NOT push other priorities!
		//It also DOES NOT value check. It is for direct access.
		if(ch_idx < 0 || ch_idx >= 16) return;
		ch_pri[ch_idx] = val;
	}
	
	public void setNoteMatchTimeLeeway(int ticks){
		if(ticks < 0) ticks = 0;
		phrase_builder.setTimeLeeway(ticks);
	}
	
	public void setNoteMatchVelocityLeeway(int leeway){
		if(leeway < 0) leeway = 0;
		if(leeway > 127) leeway = 127;
		phrase_builder.setVelocityLeeway(leeway);
	}
	
	public void setAllVoiceMatch(boolean on){
		phrase_builder.setVoiceMatchMode(!on);
	}
	
	public void setNoteCompressionOptimizationLevel(int lvl){
		if(lvl < PhraseBuilder.OPT_LEVEL_0) lvl = PhraseBuilder.OPT_LEVEL_0;
		if(lvl > PhraseBuilder.OPT_LEVEL_2) lvl = PhraseBuilder.OPT_LEVEL_2;
		phrase_builder.setOptimizationLevel(lvl);
	}
	
	public void setMusicPhraseMinTime(int ticks){
		if(ticks < 0) ticks = 0;
		phrase_builder.setPhraseMinTime(ticks);
	}
	
	public void setMusicPhraseMinEvents(int count){
		if(count < 0) count = 0;
		phrase_builder.setPhraseMinEvents(count);
	}
	
	public void setOutro(int loopEnd, byte trigger_val){
		if(loop_end < 0) loop_end = 0;
		loop_end = loopEnd;
		outro_trigger = (int)trigger_val;
		phrase_builder.setLoopEnd(loop_end);
	}
	
	public void clearOutro(){
		loop_end = -1;
		outro_trigger = 0;
		phrase_builder.setLoopEnd(loop_end);
	}
	
	public void addAlternateEntry(int tick, byte trigger_val){
		alt_entries.put((int)trigger_val, tick);
	}
	
	public void clearAlternateEntries(){
		alt_entries.clear();
	}
	
	/*----- Utils -----*/
	
	protected void addToCmdMap(int time, NUSALSeqCommand cmd){
		if(time < 0) return;
		List<NUSALSeqCommand> list = seqcmds.get(time);
		if(list == null){
			list = new LinkedList<NUSALSeqCommand>();
			seqcmds.put(time, list);
		}
		list.add(cmd);
	}
	
	public boolean compressMIDI(Sequence seq, String outpath){
		//TODO
		if(seq == null) return false;
		
		//Ready generator
		pushPriorities(0); //straighten priorities
		
		//Read MIDI
		MIDIInterpreter reader = new MIDIInterpreter(seq);
		reader.readMIDITo(this);
		
		//Optimize channels
		
		return false;
	}
	
	/*----- Seq Controls -----*/
	
	public void setMasterVolume(byte value){
		NUSALSeqCmdType type = NUSALSeqCmdType.MASTER_VOLUME;
		NUSALSeqCommand cmd = new CMD_IgnoredCommand(type, type.getBaseCommand(), 2);
		cmd.setParam(0, Byte.toUnsignedInt(value));
		addToCmdMap(tick, cmd);
		//mastervol_set = true;
	}
	
	public void setTempo(int tempo_uspqn){
		NUSALSeqCmdType type = NUSALSeqCmdType.SET_TEMPO;
		NUSALSeqCommand cmd = new CMD_IgnoredCommand(type, type.getBaseCommand(), 2);
		cmd.setParam(0, MIDI.uspqn2bpm(tempo_uspqn, NUSALSeq.NUS_TPQN));
		addToCmdMap(tick, cmd);
	}
	
	public void setMasterPan(byte value){}
	public void addMarkerNote(String note){}
	
	//Channel settings
	public void setChannelVolume(int ch, byte value){setChannelExpression(ch, value);}
	
	public void setChannelExpression(int ch, byte value){
		channels[ch].setVolume(value);
	}
	
	public void setChannelPan(int ch, byte value){
		if(value == 0x40) value = 0x3F;
		channels[ch].setPan(value);
	}
	
	public void setChannelPriority(int ch, byte value){
		channels[ch].setPriority(value);
	}
	
	public void setModWheel(int ch, short value){
		//Interpreted as vibrato.
		int vali = (int)value;
		double vald = vali/32767.0;
		vald *= 127.0;
		byte valb = (byte)(Math.round(vald));
		channels[ch].setVibrato(valb);
	}
	
	public void setPitchWheel(int ch, short value){
		//Scale to PB range!!
		//MIDI pitch wheel is 14 bit unsigned normally.
		//Get ratio to full wheel value
		value -= 0x2000;
		int vali = (int)value;
		double vald = (double)vali;
		if(vali >= 0) vald /= (double)0x1fff;
		else vald /= (double)0x2000;
		
		//Calculate cents.
		int cents = (int)Math.round(vald * (double)input_pb_range[ch]);
		setPitchBend(ch, cents);
	}
	
	public void setPitchBend(int ch, int cents){
		double pbratio = (double)cents/((double)output_pb_range);
		double vald = pbratio * 127.0;
		byte valb = (byte)(Math.round(vald));
		channels[ch].setPitchWheel(valb);
	}
	
	public void setPitchBendRange(int ch, int cents){
		//Sets INPUT range (implication is that this is from the MIDI)
		input_pb_range[ch] = cents;
	}
	
	public void setProgram(int ch, int bank, int program){setProgram(ch, program);}
	
	public void setProgram(int ch, int program){
		if(!enable_seqProgramChange) return;
		channels[ch].setProgram((byte)program);
	}
	
	public void setControllerValue(int ch, int controller, byte value){}
	public void setControllerValue(int ch, int controller, int value, boolean omitFine){}
	
	public void setEffect1(int ch, byte value){
		channels[ch].setEffectsLevel(value);
	}
	
	public void setEffect2(int ch, byte value){
		channels[ch].setVibrato(value);
	}
	
	public void addNRPNEvent(int ch, int index, int value, boolean omitFine){
		if(!omitFine) value >>>= 8;
		switch(index){
		case NUSALSeq.MIDI_NRPN_ID_LOOPSTART:
			loop_tick = tick;
			break;
		case NUSALSeq.MIDI_NRPN_ID_PRIORITY:
			channels[ch].setPriority((byte)value);
			break;
		case NUSALSeq.MIDI_NRPN_ID_REVERB:
			channels[ch].setEffectsLevel((byte)value);
			break;
		case NUSALSeq.MIDI_NRPN_ID_VIBRATO:
			channels[ch].setVibrato((byte)value);
			break;
		}
	}
	
	//Music control
	public void noteOn(int ch, byte note, byte vel){
		channels[ch].noteOn(note, vel);
	}
	
	public void noteOff(int ch, byte note, byte vel){noteOff(ch, note);}
	
	public void noteOff(int ch, byte note){
		channels[ch].noteOff(note);
	}
	
	public long advanceTick(){
		tick++;
		for(int i = 0; i < 16; i++) channels[i].advanceTick();
		return tick;
	}
	
	public void complete(){}
	
	/*----- Export -----*/
	
	public static byte[] serializeCommand(NUSALSeqCommand cmd){
		if(cmd == null) return null;
		byte[] out = null;
		byte cmdb = cmd.getCommand().getBaseCommand();
		int cmdi = Byte.toUnsignedInt(cmdb);
		int param = 0;
		switch(cmd.getCommand()){
		//Basic no param
		case BRANCH_TO_SEQSTART:
		case END_READ:
		case INIT_CHANNEL:
		case LOOP_END:
		case RETURN:
		case RETURN_FROM_SEQ:
		case SEQ_UNK_CA:
		case SEQ_UNK_CB:
			out = new byte[1];
			out[0] = cmdb;
			break;
		//Basic 1 byte param
		case AND_IMM:
		case BRANCH_ALWAYS_REL:
		case BRANCH_IF_EQZ_REL:
		case BRANCH_IF_LTZ_REL:
		case CH_DRUMSET:
		case CH_PRIORITY:
		case CH_REVERB:
		case CH_UNK_DA:
		case CH_UNK_E0:
		case LOOP_START:
		case MASTER_VOLUME:
		case SEQ_UNK_DF:
		case SET_FORMAT:
		case SET_PROGRAM:
		case SET_TEMPO:
		case SET_TYPE:
		case SET_VAR:
		case SUBTRACT_IMM:
		case TRANSPOSE:
		case VOICE_OFFSET_REL:
			out = new byte[2];
			out[0] = cmdb;
			out[1] = (byte)cmd.getParam(0);
			break;
		//Basic 2 byte param
		case BRANCH_ALWAYS:
		case BRANCH_IF_EQZ:
		case BRANCH_IF_GTEZ:
		case BRANCH_IF_LTZ:
		case CALL:
		case DISABLE_CHANNELS:
		case ENABLE_CHANNELS:
		case SEQ_UNK_C5:
		case VOICE_OFFSET:
			out = new byte[3];
			out[0] = cmdb;
			param = cmd.getParam(0);
			out[1] = (byte)((param >>> 8) & 0xFF);
			out[2] = (byte)(param & 0xFF);
			break;
		//Basic VLQ
		case REST:
		case WAIT:
			param = cmd.getParam(0);
			if(param < 128){
				out = new byte[2];
				out[0] = cmdb;
				out[1] = (byte)(param & 0xFF);
			}
			else{
				out = new byte[3];
				out[0] = cmdb;
				out[1] = (byte)(((param >>> 8) & 0xFF) | 0x80);
				out[2] = (byte)(param & 0xFF);
			}
			break;
		//2 1-byte params
		case SEQ_UNK_C4:
		case SEQ_UNK_C7:
			out = new byte[3];
			out[0] = cmdb;
			out[1] = (byte)cmd.getParam(0);
			out[2] = (byte)cmd.getParam(1);
			break;
		//2 params (2,1)
		case SEQ_UNK_EF:
			out = new byte[4];
			out[0] = cmdb;
			param = cmd.getParam(0);
			out[1] = (byte)((param >>> 8) & 0xFF);
			out[2] = (byte)(param & 0xFF);
			out[3] = (byte)cmd.getParam(1);
			break;
		//Channel lower nybble no param
		case LOAD_CH_VAR:
		case SEQ_UNK_4x:
		case SEQ_UNK_8x:
		case SET_CH_VAR:
		case SUBTRACT_CH:
			out = new byte[1];
			out[0] = (byte)((cmdi & 0xF0) | (cmd.getParam(0) & 0xF));
			break;
		//Channel lower nybble 1 byte param
		case CHANNEL_OFFSET_REL:
			out = new byte[2];
			out[0] = (byte)((cmdi & 0xF0) | (cmd.getParam(0) & 0xF));
			out[1] = (byte)cmd.getParam(1);
			break;
		//Channel lower nybble 2 byte param
		case CHANNEL_OFFSET:
			out = new byte[3];
			out[0] = (byte)((cmdi & 0xF0) | (cmd.getParam(0) & 0xF));
			param = cmd.getParam(1);
			out[1] = (byte)((param >>> 8) & 0xFF);
			out[2] = (byte)(param & 0xFF);
			break;
		//Channel lower nybble 2 1-byte params
		case LOAD_CH_DMA:
			out = new byte[3];
			out[0] = (byte)((cmdi & 0xF0) | (cmd.getParam(0) & 0xF));
			out[1] = (byte)cmd.getParam(1);
			out[2] = (byte)cmd.getParam(2);
			break;
		//Channel lower nybble 2 params (1,2)
		case NEW_SEQ_FROM:
			out = new byte[4];
			out[0] = (byte)((cmdi & 0xF0) | (cmd.getParam(0) & 0xF));
			out[1] = (byte)cmd.getParam(1);
			param = cmd.getParam(2);
			out[2] = (byte)((param >>> 8) & 0xFF);
			out[3] = (byte)(param & 0xFF);
			break;
		//1 byte param normal, 2 params if time
		case CH_PAN:
		case CH_PITCHBEND:
		case CH_VIBRATO:
		case CH_VOLUME:
			param = cmd.getParam(1);
			if(param > 0){
				out = new byte[3];
				out[2] = (byte)cmd.getParam(1);
			}
			else out = new byte[2];
			out[0] = cmdb;
			out[1] = (byte)cmd.getParam(0);
			break;
		//Note commands
		case PLAY_NOTE_NTVG:
			param = cmd.getParam(1);
			if(param < 128){
				out = new byte[4];
				out[1] = (byte)(param & 0xFF);
				out[2] = (byte)cmd.getParam(2);
				out[3] = (byte)cmd.getParam(3);
			}
			else {
				out = new byte[5];
				out[1] = (byte)(((param >>> 8) & 0xFF) | 0x80);
				out[2] = (byte)(param & 0xFF);
				out[3] = (byte)cmd.getParam(2);
				out[4] = (byte)cmd.getParam(3);
			}
			out[0] = (byte)cmd.getParam(0);
			break;
		case PLAY_NOTE_NTV:
			param = cmd.getParam(1);
			if(param < 128){
				out = new byte[3];
				out[1] = (byte)(param & 0xFF);
				out[2] = (byte)cmd.getParam(2);
			}
			else {
				out = new byte[4];
				out[1] = (byte)(((param >>> 8) & 0xFF) | 0x80);
				out[2] = (byte)(param & 0xFF);
				out[3] = (byte)cmd.getParam(2);
			}
			out[0] = (byte)(cmd.getParam(0) + 0x40);
			break;
		case PLAY_NOTE_NVG:
			out = new byte[3];
			out[0] = (byte)(cmd.getParam(0) + 0x80);
			out[1] = (byte)cmd.getParam(1);
			out[2] = (byte)cmd.getParam(2);
			break;
		default:
			break;
		}
		
		return out;
	}
	
}
