package waffleoRai_SeqSound.n64al.seqgen;

import waffleoRai_SeqSound.n64al.NUSALSeqCmdType;

class BuilderNoteCommand extends BuilderCommand{
	
	private byte tru_midi;
	private int p_shamt;
	private int idx_time;
	private int idx_vel;
	private int idx_gate;
	
	public BuilderNoteCommand(byte midi_note, int pitch_shift, int time, byte velocity, byte gate){
		super(NUSALSeqCmdType.PLAY_NOTE_NTVG, (byte)(((int)midi_note + pitch_shift) - 0x15));
		super.setParam(0, Byte.toUnsignedInt(super.getCommandByte()));
		super.setParam(1, time); idx_time = 1;
		super.setParam(2, velocity); idx_vel = 2;
		super.setParam(3, gate); idx_gate = 3;
		tru_midi = midi_note;
		p_shamt = pitch_shift;
	}
	
	public BuilderNoteCommand(byte midi_note, int pitch_shift, int time, byte velocity){
		super(NUSALSeqCmdType.PLAY_NOTE_NTV, (byte)(((int)midi_note + pitch_shift) - 0x15));
		super.setParam(0, Byte.toUnsignedInt(super.getCommandByte()));
		super.setParam(1, time); idx_time = 1;
		super.setParam(2, velocity); idx_vel = 2;
		idx_gate = -1;
		tru_midi = midi_note;
		p_shamt = pitch_shift;
	}
	
	public BuilderNoteCommand(byte midi_note, int pitch_shift, byte velocity, byte gate){
		super(NUSALSeqCmdType.PLAY_NOTE_NVG, (byte)(((int)midi_note + pitch_shift) - 0x15));
		super.setParam(0, Byte.toUnsignedInt(super.getCommandByte()));
		idx_time = -1;
		super.setParam(1, velocity); idx_vel = 1;
		super.setParam(2, gate); idx_gate = 2;
		tru_midi = midi_note;
		p_shamt = pitch_shift;
	}
	
	public byte getMidiNote(){return tru_midi;}
	public int getSetPitchShift(){return p_shamt;}
	public byte getVelocity(){return (byte)super.getParam(idx_vel);}
	
	public int getTime(){
		if(idx_time < 0) return -1;
		return super.getParam(idx_time);
	}
	
	public byte getGate(){return (byte)super.getParam(idx_gate);}

	public int getSizeInBytes() {
		if(idx_time < 0) return 3; //NVG
		int time = super.getParam(idx_time);
		if(time < 128){
			if(idx_gate < 0) return 3; //NTV
			return 4;
		}
		if(idx_gate < 0) return 4; //NTV
		return 5;
	}

}
