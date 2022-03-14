package waffleoRai_SeqSound.n64al.cmd;

import waffleoRai_SeqSound.n64al.NUSALSeqCmdType;
import waffleoRai_SeqSound.n64al.NUSALSeqCommand;
import waffleoRai_SeqSound.n64al.NUSALSeqLayer;

public class NUSALSeqNoteCommand extends NUSALSeqCommand{
	private byte tru_midi;
	private int p_shamt;
	private int idx_time;
	private int idx_vel;
	private int idx_gate;
	
	private NUSALSeqNoteCommand(){
		super(NUSALSeqCmdType.PLAY_NOTE_NTVG, (byte)0x27);
	}
	
	private NUSALSeqNoteCommand(NUSALSeqCmdType type, byte cmdbyte){
		super(type,cmdbyte);
	}
	
	public NUSALSeqNoteCommand(byte midi_note, int pitch_shift, int time, byte velocity, byte gate){
		super(NUSALSeqCmdType.PLAY_NOTE_NTVG, (byte)(((int)midi_note + pitch_shift) - 0x15));
		super.setParam(0, Byte.toUnsignedInt(super.getCommandByte()));
		super.setParam(1, time); idx_time = 1;
		super.setParam(2, velocity); idx_vel = 2;
		super.setParam(3, Byte.toUnsignedInt(gate)); idx_gate = 3;
		tru_midi = midi_note;
		p_shamt = pitch_shift;
	}
	
	public NUSALSeqNoteCommand(byte midi_note, int pitch_shift, int time, byte velocity){
		super(NUSALSeqCmdType.PLAY_NOTE_NTV, (byte)(((int)midi_note + pitch_shift) - 0x15));
		super.setParam(0, Byte.toUnsignedInt(super.getCommandByte()));
		super.setParam(1, time); idx_time = 1;
		super.setParam(2, velocity); idx_vel = 2;
		idx_gate = -1;
		tru_midi = midi_note;
		p_shamt = pitch_shift;
	}
	
	public NUSALSeqNoteCommand(byte midi_note, int pitch_shift, byte velocity, byte gate){
		super(NUSALSeqCmdType.PLAY_NOTE_NVG, (byte)(((int)midi_note + pitch_shift) - 0x15));
		super.setParam(0, Byte.toUnsignedInt(super.getCommandByte()));
		idx_time = -1;
		super.setParam(1, velocity); idx_vel = 1;
		super.setParam(2, Byte.toUnsignedInt(gate)); idx_gate = 2;
		tru_midi = midi_note;
		p_shamt = pitch_shift;
	}
	
	public static NUSALSeqNoteCommand fromMidiNote(byte midi_note, int pitch_shift, int time, byte velocity, byte gate){
		int nusal_note = (int)midi_note + pitch_shift;
		nusal_note -= 0x15;
		NUSALSeqNoteCommand cmd = new NUSALSeqNoteCommand(NUSALSeqCmdType.PLAY_NOTE_NTVG, (byte)nusal_note);
		cmd.setParam(0, nusal_note);
		cmd.setParam(1, time); cmd.idx_time = 1;
		cmd.setParam(2, velocity); cmd.idx_vel = 2;
		cmd.setParam(3, Byte.toUnsignedInt(gate)); cmd.idx_gate = 3;
		cmd.tru_midi = midi_note;
		cmd.p_shamt = pitch_shift;
		
		return cmd;
	}
	
	public static NUSALSeqNoteCommand fromMidiNote(byte midi_note, int pitch_shift, int time, byte velocity){
		int nusal_note = (int)midi_note + pitch_shift;
		nusal_note -= 0x15;
		NUSALSeqNoteCommand cmd = new NUSALSeqNoteCommand(NUSALSeqCmdType.PLAY_NOTE_NTV, (byte)nusal_note);
		cmd.setParam(0, nusal_note);
		cmd.setParam(1, time); cmd.idx_time = 1;
		cmd.setParam(2, velocity); cmd.idx_vel = 2;
		cmd.idx_gate = -1;
		cmd.tru_midi = midi_note;
		cmd.p_shamt = pitch_shift;
		
		return cmd;
	}
	
	public static NUSALSeqNoteCommand fromMidiNote(byte midi_note, int pitch_shift, byte velocity, byte gate){
		int nusal_note = (int)midi_note + pitch_shift;
		nusal_note -= 0x15;
		NUSALSeqNoteCommand cmd = new NUSALSeqNoteCommand(NUSALSeqCmdType.PLAY_NOTE_NVG, (byte)nusal_note);
		cmd.setParam(0, nusal_note);
		cmd.idx_time = -1;
		cmd.setParam(1, velocity); cmd.idx_vel = 1;
		cmd.setParam(2, Byte.toUnsignedInt(gate)); cmd.idx_gate = 2;
		cmd.tru_midi = midi_note;
		cmd.p_shamt = pitch_shift;
		
		return cmd;
	}
	
	public static NUSALSeqNoteCommand fromRawCommand(byte cmd_byte, int time, byte velocity, byte gate){
		NUSALSeqNoteCommand cmd = new NUSALSeqNoteCommand(NUSALSeqCmdType.PLAY_NOTE_NTVG, cmd_byte);
		cmd.setParam(0, (int)cmd_byte);
		cmd.setParam(1, time); cmd.idx_time = 1;
		cmd.setParam(2, velocity); cmd.idx_vel = 2;
		cmd.setParam(3, Byte.toUnsignedInt(gate)); cmd.idx_gate = 3;
		cmd.getMidiNote();
		return cmd;
	}
	
	public static NUSALSeqNoteCommand fromRawCommand(byte cmd_byte, int time, byte velocity){
		int rawcmd = Byte.toUnsignedInt(cmd_byte) | 0x40; //Turning that on just in case.
		int cmdnote = rawcmd & 0x3f;
		NUSALSeqNoteCommand cmd = new NUSALSeqNoteCommand(NUSALSeqCmdType.PLAY_NOTE_NTV, (byte)rawcmd);
		cmd.setParam(0, cmdnote);
		cmd.setParam(1, time); cmd.idx_time = 1;
		cmd.setParam(2, velocity); cmd.idx_vel = 2;
		cmd.idx_gate = -1;
		cmd.getMidiNote();
		return cmd;
	}
	
	public static NUSALSeqNoteCommand fromRawCommand(byte cmd_byte, byte velocity, byte gate){
		int rawcmd = Byte.toUnsignedInt(cmd_byte) | 0x80; //Turning that on just in case.
		int cmdnote = rawcmd & 0x3f;
		NUSALSeqNoteCommand cmd = new NUSALSeqNoteCommand(NUSALSeqCmdType.PLAY_NOTE_NVG, (byte)rawcmd);
		cmd.setParam(0, cmdnote);
		cmd.idx_time = -1;
		cmd.setParam(1, velocity); cmd.idx_vel = 1;
		cmd.setParam(2, Byte.toUnsignedInt(gate)); cmd.idx_gate = 2;
		cmd.getMidiNote();
		return cmd;
	}
	
	public byte getMidiNote(){return tru_midi;}
	public int getSetPitchShift(){return p_shamt;}
	public byte getVelocity(){return (byte)super.getParam(idx_vel);}
	
	public byte getCommandNote(){
		return (byte)(super.getParam(0));
	}
	
	public int getTime(){
		if(idx_time < 0) return -1;
		return super.getParam(idx_time);
	}
	
	public byte getGate(){return (byte)super.getParam(idx_gate);}
	
	public int getSizeInTicks(){
		return getTime();
	}
	
	public void limitTicksTo(int ticks){
		if(ticks < 1) return;
		if(getTime() > ticks){
			super.setParam(idx_time, ticks);
		}
	}

	public int getSizeInBytes() {
		if(idx_time < 0) return 3; //NVG
		int time = super.getParam(idx_time);
		if(time < 128){
			if(idx_gate < 0) return 3; //NTV
			return 4; //NTVG
		}
		if(idx_gate < 0) return 4; //NTV
		return 5; //NTVG
	}
	
	public byte updateMidiValue(int pitch_shift){
		int cmdnote = super.getParam(0);
		int midnote = cmdnote + 0x15;
		midnote -= pitch_shift;
		tru_midi = (byte)midnote;
		p_shamt = pitch_shift;
		
		return tru_midi;
	}
	
	public void setTime(int ticks) {
		if(idx_time < 0) return;
		super.setParam(idx_time, ticks);
	}
	
	public void shortenTimeBy(int ticks) {
		if(idx_time < 0) return;
		int nowtime = super.getParam(idx_time);
		super.setParam(idx_time, nowtime - ticks);
	}
	
	public boolean doCommand(NUSALSeqLayer voice){
		if(voice == null) return false;
		if(idx_time < 0){
			voice.playNote(getCommandNote(), getVelocity(), getGate());
		}
		else if(idx_gate < 0){
			voice.playNote(getCommandNote(), getVelocity(), getTime());
		}
		else{
			voice.playNote(getCommandNote(), getVelocity(), getGate(), getTime());
		}
		super.flagLayerUsed(voice.getChannelIndex(), voice.getLayerIndex());
		return true;
	}
	
	public String toMMLCommand(){
		StringBuilder sb = new StringBuilder(64);
		sb.append(super.getCommand().toString());
		sb.append(' ');
		sb.append(super.getParam(0));
		
		if(idx_time >= 0){
			sb.append(", ");
			sb.append(getTime());
		}
		
		sb.append(", ");
		sb.append(getVelocity());
		
		if(idx_gate >= 0){
			sb.append(", ");
			sb.append(Byte.toUnsignedInt(getGate()));
		}
		
		return sb.toString();
	}
	
	protected String paramsToString(){
		StringBuilder sb = new StringBuilder(64);
		sb.append("0x");
		sb.append(String.format("%02x, ", super.getParam(0)));
		
		//If have time, mark time.
		if(idx_time >= 0){
			sb.append(getTime());
			sb.append(" ticks, ");
		}
		
		//Velocity
		sb.append("v");
		sb.append(getVelocity());
		
		//Gate, if applicable
		if(idx_gate >= 0){
			sb.append(", g");
			sb.append(getGate());
		}
		
		return sb.toString();
	}

}
