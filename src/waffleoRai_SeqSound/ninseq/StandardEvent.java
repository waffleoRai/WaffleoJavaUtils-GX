package waffleoRai_SeqSound.ninseq;

import waffleoRai_SeqSound.MIDI;

public class StandardEvent extends NSEvent{
	
	public StandardEvent(NSCommand cmd, int p1)
	{
		super.setCommand(cmd);
		super.setParam1(p1);
		super.setSerialSize(calculateSize());
		//super.setAddress(addr);
	}
	
	public StandardEvent(NSCommand cmd, int p1, int serialSize)
	{
		super.setCommand(cmd);
		super.setParam1(p1);
		super.setSerialSize(calculateSize());
		super.setSerialSize(serialSize);
		//super.setAddress(addr);
	}
	
	private int calculateSize()
	{
		NSCommand cmd = super.getCommand();
		int cmdFam = cmd.getCommandHigherNybble();
		switch(cmdFam)
		{
		case 0x8: return 1 + MIDI.VLQlength(super.getParam1()); //Only 0x80 and 0x81 valid
		case 0xb: return 2; //Assumed Wii since DS extended cmds should not use this class.
		case 0xc:
		case 0xd: 
			return 2;
		case 0xe: return 3;
		default: return 1;
		}
	}

	public boolean hasFirstParameter(){return true;}
	public boolean hasSecondParameter(){return false;}
	
	public void execute(NinTrack track)
	{
		NSCommand cmd = super.getCommand();
		switch(cmd)
		{
		case WAIT: track.setWait(getParam1()); return;
		case CHANGE_PRG: track.changeProgram(getParam1()); return;
		case PAN: track.setPan(getParam1()); return;
		case VOLUME: track.setTrackVolume(getParam1()); return;
		case MAIN_VOLUME: track.setMasterVolume(getParam1()); return;
		case TRANSPOSE: track.setTransposition(getParam1()); return;
		case PITCH_BEND: track.setPitchBend(getParam1()); return;
		case BEND_RANGE: track.setPitchBendRange(getParam1()); return;
		case PRIORITY: track.setTrackPriority(getParam1()); return;
		case NOTE_WAIT: track.setNoteWait(getParam1() != 0); return;
		case TIE: track.setTie(getParam1() != 0); return;
		case PORTA: track.setPortamentoControl(getParam1()); return;
		case PORTA_SW: track.setPortamentoOn(getParam1() != 0); return;
		case PORTA_TIME: track.setPortamentoTime(getParam1()); return;
		case MOD_DEPTH: track.setModulationDepth(getParam1()); return;
		case MOD_SPEED: track.setModulationSpeed(getParam1()); return;
 		case MOD_TYPE: track.setModulationType(getParam1()); return;
		case MOD_RANGE: track.setModulationRange(getParam1()); return;
		case MOD_DELAY: track.setModulationDelay(getParam1()); return;
		case ATTACK: track.setAttack(getParam1()); return;
		case DECAY: track.setDecay(getParam1()); return;
		case SUSTAIN: track.setSustain(getParam1()); return;
		case RELEASE: track.setRelease(getParam1()); return;
		case ENV_HOLD: track.setHold(getParam1()); return; //???
		case LOOP_START: track.signalLoopStart(getParam1(), getAddress()); return;
		case EXPRESSION: track.setExpression(getParam1()); return;
		case PRINTVAR: track.printVariable(getParam1()); return;
		case SURROUND_PAN: track.setSurroundPan(getParam1()); return;
		case LPF_CUTOFF: track.setLPFCutoff(getParam1()); return;
		case FXSEND_A: track.setFXSendLevel(0, getParam1()); return;
		case FXSEND_B: track.setFXSendLevel(1, getParam1()); return;
		case FXSEND_C: track.setFXSendLevel(2, getParam1()); return;
		case MAINSEND: track.setMainSendLevel(getParam1()); return;
		case INIT_PAN: track.setInitPan(getParam1()); return;
		case MUTE: track.setMute(getParam1() != 0); return;
		case DAMPER: track.setDamper(getParam1() != 0); return;
		case TIMEBASE: track.setTimebase(getParam1()); return;
		case MONOPHONIC: track.setMonophony(getParam1() != 0); return;
		case VELOCITY_RANGE: track.setVelocityRange(getParam1()); return;
		case BIQUAD_TYPE: track.setBiquadType(getParam1()); return;
		case BIQUAD_VALUE: track.setBiquadValue(getParam1()); return;
		case TEMPO: track.setTempo(getParam1()); return;
		case SWEEP_PITCH: track.setPitchSweep(getParam1()); return;
		default: return;
		}
	}
	
	public byte[] serializeEvent(boolean endian)
	{
		//8 : cmd + VLQ
		//b,c,d : cmd + 1
		//e : cmd + 2
		byte[] event = null;
		NSCommand cmd = super.getCommand();
		int cmdFam = cmd.getCommandHigherNybble();
		switch(cmdFam)
		{
		case 0x8:
			byte[] vlq = MIDI.makeVLQ(super.getParam1());
			event = new byte[1 + vlq.length];
			event[0] = cmd.getCommandByte();
			for(int i = 0; i < vlq.length; i++) event[1+i] = vlq[i];
			break;
		case 0xb:
		case 0xc:
		case 0xd:
			event = new byte[2];
			event[0] = cmd.getCommandByte();
			event[1] = (byte)super.getParam1();
			break;
		case 0xe:
			event = new byte[3];
			event[0] = cmd.getCommandByte();
			if(endian)
			{
				event[1] = (byte)(super.getParam1() >>> 8); //MSB
				event[2] = (byte)(super.getParam1() & 0xFF); //LSB	
			}
			else
			{
				event[2] = (byte)(super.getParam1() >>> 8); //MSB
				event[1] = (byte)(super.getParam1() & 0xFF); //LSB
			}
			break;
		}
		return event;
	}
	
	public String toString()
	{
		NSCommand cmd = super.getCommand();
		switch(cmd)
		{
		case ATTACK: return "Attack: " + String.format("0x%02x", super.getParam1());
		case BEND_RANGE: return "Bend Range: " + super.getParam1() + " semitones";
		case BIQUAD_TYPE: return "Biquad Type: " + String.format("0x%02x", super.getParam1());
		case BIQUAD_VALUE: return "Biquad Value: " + String.format("0x%02x", super.getParam1());
		case CHANGE_PRG: return "Change Patch -- Bank " + (super.getParam1() >>> 7) + ", Program " + (super.getParam1() & 0x7F);
		case DAMPER: return "Damper: " + String.format("0x%02x", super.getParam1());
		case DECAY: return "Decay: " + String.format("0x%02x", super.getParam1());
		case ENV_HOLD: return "Envelope Hold: " + String.format("0x%02x", super.getParam1());
		case EXPRESSION: return "Expression: " + String.format("0x%02x", super.getParam1());
		case FXSEND_A: return "FX Send A: " + String.format("0x%02x", super.getParam1());
		case FXSEND_B: return "FX Send B: " + String.format("0x%02x", super.getParam1());
		case FXSEND_C: return "FX Send C: " + String.format("0x%02x", super.getParam1());
		case INIT_PAN: return "Init Pan: " + String.format("0x%02x", super.getParam1());
		case LOOP_START: return "Loop Start - Loops: " + super.getParam1(); 
		case LPF_CUTOFF: return "LPF Cutoff: " + String.format("0x%02x", super.getParam1());
		case MAINSEND: return "Main Send: " + String.format("0x%02x", super.getParam1());
		case MAIN_VOLUME: return "Master Volume: " + super.getParam1();
		case MOD_DELAY: return "Mod Delay: " + String.format("0x%04x", super.getParam1());
		case MOD_DEPTH: return "Mod Depth: " + String.format("0x%02x", super.getParam1());
		case MOD_RANGE: return "Mod Range: " + String.format("0x%02x", super.getParam1());
		case MOD_SPEED: return "Mod Speed: " + String.format("0x%02x", super.getParam1());
		case MOD_TYPE: return "Mod Type: " + super.getParam1();
		case MONOPHONIC: return "Monophony: " + String.format("0x%02x", super.getParam1());
		case MUTE: return "Mute: " + (super.getParam1() != 0);
		case NOTE_WAIT: return "Note Wait: " + (super.getParam1() != 0);
		case PAN: return "Pan: " + String.format("0x%02x", super.getParam1());
		case PITCH_BEND: return "Pitch Bend: " + super.getParam1();
		case PORTA: return "Portamento Control: " + String.format("0x%02x", super.getParam1());
		case PORTA_SW: return "Portamento: " + (super.getParam1() != 0);
		case PORTA_TIME: return "Portamento Time: " + String.format("0x%02x", super.getParam1());
		case PRINTVAR: return "Print Variable: $" + super.getParam1();
		case PRIORITY: return "Track Priority: " + super.getParam1();
		case RELEASE: return "Release: " + super.getParam1();
		case SURROUND_PAN: return "Surround Pan: " + String.format("0x%02x", super.getParam1());
		case SUSTAIN: return "Sustain: " + super.getParam1();
		case SWEEP_PITCH: return "Pitch Sweep: " + super.getParam1();
		case TEMPO: return "Tempo: " + super.getParam1() + " bpm";
		case TIE: return "Tie: " + (super.getParam1() != 0);
		case TIMEBASE: return "Timebase: " + super.getParam1() + "ppq";
		case TRANSPOSE: return "Transpose track " + super.getParam1() + " semitones";
		case VELOCITY_RANGE: return "Velocity Range: " + super.getParam1();
		case VOLUME: return "Track Volume: " + super.getParam1();
		case WAIT: return "Wait for " + super.getParam1() + " ticks";
		default: return "INVALID STD COMMAND";
		}

	}

}
