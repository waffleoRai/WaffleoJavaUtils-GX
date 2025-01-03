package waffleoRai_SeqSound.n64al.cmd;

import waffleoRai_SeqSound.n64al.NUSALSeqCmdType;
import waffleoRai_SeqSound.n64al.NUSALSeqCommand;
import waffleoRai_SeqSound.n64al.NUSALSeqCommandBook;
import waffleoRai_SeqSound.n64al.NUSALSeqLayer;
import waffleoRai_Utils.StringUtils;

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
	
	private NUSALSeqNoteCommand(NUSALSeqCommandDef def){
		super(def);
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
	
	public static NUSALSeqNoteCommand fromMidiNote(NUSALSeqCommandBook cmdBook, byte midi_note, int pitch_shift, int time, byte velocity, byte gate){
		int nusal_note = (int)midi_note - pitch_shift;
		nusal_note -= 0x15;
		NUSALSeqNoteCommand cmd = null;
		if(cmdBook != null) {
			cmd = new NUSALSeqNoteCommand(cmdBook.getLayerCommand(NUSALSeqCmdType.PLAY_NOTE_NTVG));
		}
		else cmd = new NUSALSeqNoteCommand(NUSALSeqCmdType.PLAY_NOTE_NTVG, (byte)nusal_note);
		cmd.setParam(0, nusal_note);
		cmd.setParam(1, time); cmd.idx_time = 1;
		cmd.setParam(2, velocity); cmd.idx_vel = 2;
		cmd.setParam(3, Byte.toUnsignedInt(gate)); cmd.idx_gate = 3;
		cmd.tru_midi = midi_note;
		cmd.p_shamt = pitch_shift;
		
		return cmd;
	}
	
	public static NUSALSeqNoteCommand fromMidiNote(byte midi_note, int pitch_shift, int time, byte velocity){
		int nusal_note = (int)midi_note - pitch_shift;
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
		int nusal_note = (int)midi_note - pitch_shift;
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
	
	public static NUSALSeqNoteCommand fromBinRead(NUSALSeqCommandDef def, int[] params) {
		NUSALSeqNoteCommand cmd = new NUSALSeqNoteCommand(def);
		cmd.setParam(0, params[0]);
		switch(def.getFunctionalType()) {
			case PLAY_NOTE_NTVG:
				cmd.idx_time = 1; cmd.setParam(1, params[1]);
				cmd.idx_vel = 2; cmd.setParam(2, params[2]);
				cmd.idx_gate = 3; cmd.setParam(3, params[3]);
				break;
			case PLAY_NOTE_NTV:
				cmd.idx_time = 1; cmd.setParam(1, params[1]);
				cmd.idx_vel = 2; cmd.setParam(2, params[2]);
				cmd.idx_gate = -1;
				break;
			case PLAY_NOTE_NVG:
				cmd.idx_time = -1;
				cmd.idx_vel = 1; cmd.setParam(1, params[1]);
				cmd.idx_gate = 2; cmd.setParam(2, params[2]);
				break;
			case SHORT_NOTE_NTVG:
				cmd.idx_time = 1; cmd.setParam(1, params[1]);
				cmd.idx_vel = -2;
				cmd.idx_gate = -2;
				break;
			case SHORT_NOTE_NTV:
				cmd.idx_time = -2; //Layer value
				cmd.idx_vel = -2;
				cmd.idx_gate = -1; //Last value
				break;
			case SHORT_NOTE_NVG:
				cmd.idx_time = -1;
				cmd.idx_vel = -2;
				cmd.idx_gate = -2;
				break;
			default: return null;
		}

		return cmd;
	}
	
	public static NUSALSeqNoteCommand fromMMLRead(NUSALSeqCommandDef def, int[][] preParsedArgs) {
		int acount = preParsedArgs.length;
		int[] args = new int[acount];
		for(int i = 0; i < acount; i++) {
			args[i] = preParsedArgs[i][0];
		}
		return fromBinRead(def, args);
	}
	
	public static NUSALSeqNoteCommand fromMMLRead(NUSALSeqCommandDef def, String[] args) {
		int acount = args.length;
		int[] iargs = new int[acount];
		for(int i = 0; i < acount; i++) {
			iargs[i] = StringUtils.parseSignedInt(args[i]);
		}
		return fromBinRead(def, iargs);
	}
	
	public byte getMidiNote(){return tru_midi;}
	public int getSetPitchShift(){return p_shamt;}
	public int getVelocity(){return (idx_vel < 0) ? -1 : super.getParam(idx_vel);}
	
	public void setEnvironmentalPitchShift(int val){
		//Update the NUSAL note & command byte
		p_shamt = val;
		int nusal_note = (int)tru_midi - p_shamt;
		nusal_note -= 0x15;
		super.setParam(0, nusal_note);
		super.setCommandByte((byte)nusal_note);
	}
	
	public byte getCommandNote(){
		return (byte)(super.getParam(0));
	}
	
	public int getTime(){
		int itr = 0; //Break if it gets ridiculous
		if(idx_time < 0){
			if(idx_time == -2) return -1;
			NUSALSeqCommand prev = getPreviousCommand();
			while(prev != null){
				if(prev instanceof NUSALSeqNoteCommand){
					return ((NUSALSeqNoteCommand) prev).getTime();
				}
				//System.err.println("prev = " + prev.toMMLCommand());
				prev = prev.getPreviousCommand();
				if(++itr >= 100000){
					System.err.println("NUSALSeqNoteCommand.getTime || WARNING! Could not find time for notevg - "
							+ "either time command is too far back or there's circular references in the command linked list!");
					return 0;
				}
			}
			//System.err.println("???");
			return 0;
		}
		return super.getParam(idx_time);
	}
	
	public int getGate(){return (idx_gate < 0) ? -1 : super.getParam(idx_gate);}
	
	public boolean ntvg2ntv(NUSALSeqCommandBook cmdbook){
		NUSALSeqCommandDef d = super.getCommandDef();
		if(d == null) return false;
		NUSALSeqCmdType t = d.getFunctionalType();
		if(t != NUSALSeqCmdType.PLAY_NOTE_NTVG) return false;
		
		if(cmdbook == null) cmdbook = SysCommandBook.ZELDA64.getBook();
		if(cmdbook == null) return false;
		d = cmdbook.getLayerCommand(NUSALSeqCmdType.PLAY_NOTE_NTV);
		if(d == null) return false;
		
		int[] oldargs = super.restructureCommand(d, (byte)(super.getCommandByte() + 0x40));
		for(int i = 0; i < 3; i++) super.setParam(i, oldargs[i]);
		idx_gate = -1;
		return true;
	}
	
	public boolean ntvg2nvg(NUSALSeqCommandBook cmdbook){
		NUSALSeqCommandDef d = super.getCommandDef();
		if(d == null) return false;
		NUSALSeqCmdType t = d.getFunctionalType();
		if(t != NUSALSeqCmdType.PLAY_NOTE_NTVG) return false;
		
		if(cmdbook == null) cmdbook = SysCommandBook.ZELDA64.getBook();
		if(cmdbook == null) return false;
		d = cmdbook.getLayerCommand(NUSALSeqCmdType.PLAY_NOTE_NVG);
		if(d == null) return false;
		
		int[] oldargs = super.restructureCommand(d, (byte)(super.getCommandByte() + 0x80));
		super.setParam(0, oldargs[0]);
		super.setParam(1, oldargs[2]);
		super.setParam(2, oldargs[3]);
		idx_time = -1; idx_vel = 1; idx_gate = 2;
		return true;
	}
	
	public boolean nvg2ntvg(NUSALSeqCommandBook cmdbook){
		NUSALSeqCommandDef d = super.getCommandDef();
		if(d == null) return false;
		NUSALSeqCmdType t = d.getFunctionalType();
		if(t != NUSALSeqCmdType.PLAY_NOTE_NVG) return false;
		
		if(cmdbook == null) cmdbook = SysCommandBook.ZELDA64.getBook();
		if(cmdbook == null) return false;
		d = cmdbook.getLayerCommand(NUSALSeqCmdType.PLAY_NOTE_NTVG);
		if(d == null) return false;
		
		int time = getTime(); //Try to get time
		int[] oldargs = super.restructureCommand(d, (byte)(super.getCommandByte() & 0x3F));
		super.setParam(0, oldargs[0]);
		super.setParam(1, time);
		super.setParam(2, oldargs[1]);
		super.setParam(3, oldargs[2]);
		idx_time = 1; idx_vel = 2; idx_gate = 3;
		return true;
	}
	
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
		if(idx_vel == -2 || idx_time == -2 || idx_gate == -2) {
			//Short note
			voice.playNote(getCommandNote(), getVelocity(), getGate(), getTime());
		}
		else {
			if(idx_time < 0){
				voice.playNoteNVG(getCommandNote(), getVelocity(), getGate());
			}
			else if(idx_gate < 0){
				voice.playNoteNDV(getCommandNote(), getVelocity(), getTime());
			}
			else{
				voice.playNote(getCommandNote(), getVelocity(), getGate(), getTime());
			}	
		}
		super.flagLayerUsed(voice.getChannelIndex(), voice.getLayerIndex());
		return true;
	}
	
	protected StringBuilder toMMLCommand_child(int syntax){
		StringBuilder sb = new StringBuilder(256);
		NUSALSeqCommandDef def = super.getCommandDef();
		if(def != null) sb.append(def.getMnemonic(syntax));
		else sb.append("<NULL DEF>");
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
			sb.append(getGate());
		}
		return sb;
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
