package waffleoRai_SeqSound.n64al;

import javax.sound.midi.Sequence;

import waffleoRai_SoundSynth.SequenceController;

public class NUSALSeqGenerator implements SequenceController{

	/*----- Constants -----*/
	
	/*----- Instance Variables -----*/
	
	private long tick;
	
	private int loop_tick; //Loop start.
	
	/*----- Inner Classes -----*/
	
	/*----- Init -----*/
	
	public NUSALSeqGenerator(){
		//TODO
	}
	
	public void loadMidiSequence(Sequence seq){
		//TODO
		//Resorts events by channel, not track.
	}
	
	/*----- Getters -----*/
	
	/*----- Setters -----*/
	
	/*----- Seq Controls -----*/
	
	public void setMasterVolume(byte value){}
	public void setMasterPan(byte value){}
	public void setTempo(int tempo_uspqn){}
	public void addMarkerNote(String note){}
	
	//Channel settings
	public void setChannelVolume(int ch, byte value){}
	public void setChannelExpression(int ch, byte value){}
	public void setChannelPan(int ch, byte value){}
	public void setChannelPriority(int ch, byte value){}
	public void setModWheel(int ch, short value){}
	public void setPitchWheel(int ch, short value){}
	public void setPitchBend(int ch, int cents){}
	public void setPitchBendRange(int ch, int cents){}
	public void setProgram(int ch, int bank, int program){}
	public void setProgram(int ch, int program){}
	public void addNRPNEvent(int ch, int index, int value, boolean omitFine){}
	
	//Music control
	public void noteOn(int ch, byte note, byte vel){}
	public void noteOff(int ch, byte note, byte vel){}
	public void noteOff(int ch, byte note){}
	
	public long advanceTick(){return 0;}
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
