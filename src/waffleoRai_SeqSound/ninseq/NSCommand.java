package waffleoRai_SeqSound.ninseq;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public enum NSCommand 
{
	
	//We're assuming all Wii commands are RCF until shown otherwise?

	//Notes
	PLAY_NOTE(0x00, NinSeq.TICKS_PER_QNOTE, NinSeq.COMMAND_TYPE_ANY),
	
	//Variable Length
	WAIT(0x80, NinSeq.TICKS_PER_QNOTE, NinSeq.COMMAND_TYPE_ANY),
	CHANGE_PRG(0x81, 0, NinSeq.COMMAND_TYPE_ANY),
	
	//Control
	OPEN_TRACK_WII(0x88, NinSeq.NO_DEFAULT_VALUE, NinSeq.COMMAND_TYPE_RCF_ONLY),
	JUMP_WII(0x89, NinSeq.NO_DEFAULT_VALUE, NinSeq.COMMAND_TYPE_RCF_ONLY),
	CALL_WII(0x8a, NinSeq.NO_DEFAULT_VALUE, NinSeq.COMMAND_TYPE_RCF_ONLY),
	
	OPEN_TRACK_DS(0x93, NinSeq.NO_DEFAULT_VALUE, NinSeq.COMMAND_TYPE_DS_ONLY),
	JUMP_DS(0x94, NinSeq.NO_DEFAULT_VALUE, NinSeq.COMMAND_TYPE_DS_ONLY),
	CALL_DS(0x95, NinSeq.NO_DEFAULT_VALUE, NinSeq.COMMAND_TYPE_DS_ONLY),
	
	//Prefix Commands
	PREFIX_RANDOM(0xa0, 0, NinSeq.COMMAND_TYPE_ANY), 
	PREFIX_VARIABLE(0xa1, 0, NinSeq.COMMAND_TYPE_ANY),
	PREFIX_IF(0xa2, NinSeq.NO_DEFAULT_VALUE, NinSeq.COMMAND_TYPE_ANY),
	PREFIX_TIME(0xa3, 0, NinSeq.COMMAND_TYPE_RCF_ONLY),
	PREFIX_TIME_RANDOM(0xa4, 0, NinSeq.COMMAND_TYPE_RCF_ONLY),
	PREFIX_TIME_VARIABLE(0xa5, 0, NinSeq.COMMAND_TYPE_RCF_ONLY),
	
	//U8 Commands (Special)
	TIMEBASE(0xb0, NinSeq.TICKS_PER_QNOTE, NinSeq.COMMAND_TYPE_RCF_ONLY),
	ENV_HOLD(0xb1, NinSeq.TICKS_PER_QNOTE, NinSeq.COMMAND_TYPE_RCF_ONLY),
    MONOPHONIC(0xb2, 0, NinSeq.COMMAND_TYPE_RCF_ONLY),
    VELOCITY_RANGE(0xb3, 0x7F, NinSeq.COMMAND_TYPE_RCF_ONLY),
    BIQUAD_TYPE(0xb4, 0, NinSeq.COMMAND_TYPE_RCF_ONLY),
    BIQUAD_VALUE(0xb5, 0, NinSeq.COMMAND_TYPE_RCF_ONLY),
    
    //U8 Commands (Common)
    PAN(0xc0, 0x40, NinSeq.COMMAND_TYPE_ANY),
    VOLUME(0xc1, 0x7F, NinSeq.COMMAND_TYPE_ANY),
    MAIN_VOLUME(0xc2, 0x7F, NinSeq.COMMAND_TYPE_ANY),
    TRANSPOSE(0xc3, 0, NinSeq.COMMAND_TYPE_ANY),
    PITCH_BEND(0xc4, 0, NinSeq.COMMAND_TYPE_ANY),
    BEND_RANGE(0xc5, 2, NinSeq.COMMAND_TYPE_ANY),
    PRIORITY(0xc6, 0, NinSeq.COMMAND_TYPE_ANY),
    NOTE_WAIT(0xc7, 0, NinSeq.COMMAND_TYPE_ANY),
    TIE(0xc8, 0, NinSeq.COMMAND_TYPE_ANY),
    PORTA(0xc9, 0, NinSeq.COMMAND_TYPE_ANY),
    MOD_DEPTH(0xca, 0, NinSeq.COMMAND_TYPE_ANY),
    MOD_SPEED(0xcb, 0, NinSeq.COMMAND_TYPE_ANY),
    MOD_TYPE(0xcc, 1, NinSeq.COMMAND_TYPE_ANY),
    MOD_RANGE(0xcd, 0x7F, NinSeq.COMMAND_TYPE_ANY),
    PORTA_SW(0xce, 0, NinSeq.COMMAND_TYPE_ANY),
    PORTA_TIME(0xcf, 0, NinSeq.COMMAND_TYPE_ANY),

    ATTACK(0xd0, 0, NinSeq.COMMAND_TYPE_ANY),
    DECAY(0xd1, 0, NinSeq.COMMAND_TYPE_ANY),
    SUSTAIN(0xd2, 0x7F, NinSeq.COMMAND_TYPE_ANY),
    RELEASE(0xd3, 0, NinSeq.COMMAND_TYPE_ANY),
    LOOP_START(0xd4, -1, NinSeq.COMMAND_TYPE_ANY), //Is -1 infinite loop?
    EXPRESSION(0xd5, 0, NinSeq.COMMAND_TYPE_ANY),
    PRINTVAR(0xd6, 0, NinSeq.COMMAND_TYPE_ANY),
    SURROUND_PAN(0xd7, 0x40, NinSeq.COMMAND_TYPE_RCF_ONLY),
    LPF_CUTOFF(0xd8, 0x7F, NinSeq.COMMAND_TYPE_RCF_ONLY), //I don't know the max for this command
    FXSEND_A(0xd9, 0, NinSeq.COMMAND_TYPE_RCF_ONLY),
    FXSEND_B(0xda, 0, NinSeq.COMMAND_TYPE_RCF_ONLY),
    MAINSEND(0xdb, 0x7F, NinSeq.COMMAND_TYPE_RCF_ONLY),
    INIT_PAN(0xdc, 0x40, NinSeq.COMMAND_TYPE_RCF_ONLY),
    MUTE(0xdd, 0, NinSeq.COMMAND_TYPE_RCF_ONLY),
    FXSEND_C(0xde, 0, NinSeq.COMMAND_TYPE_RCF_ONLY),
    DAMPER(0xdf, 0, NinSeq.COMMAND_TYPE_RCF_ONLY),

    //S16 Commands
    MOD_DELAY(0xe0, 0, NinSeq.COMMAND_TYPE_ANY),
    TEMPO(0xe1, 120, NinSeq.COMMAND_TYPE_ANY), //Don't know the scale
    SWEEP_PITCH(0xe3, 0, NinSeq.COMMAND_TYPE_ANY),
    
    //Other Control
    EX_COMMAND(0xf0, NinSeq.NO_DEFAULT_VALUE, NinSeq.COMMAND_TYPE_RCF_ONLY),
    ENV_RESET(0xfb, NinSeq.NO_DEFAULT_VALUE, NinSeq.COMMAND_TYPE_RCF_ONLY), //Don't know for sure that it has no params
	LOOP_END(0xfc, NinSeq.NO_DEFAULT_VALUE, NinSeq.COMMAND_TYPE_ANY),
	RETURN(0xfd, NinSeq.NO_DEFAULT_VALUE, NinSeq.COMMAND_TYPE_ANY),
	ALLOC_TRACK(0xfe, 0xFFFF, NinSeq.COMMAND_TYPE_ANY),
	TRACK_END(0xff, NinSeq.NO_DEFAULT_VALUE, NinSeq.COMMAND_TYPE_ANY),
	
	//DS Extended Commands
	SET_VAR_DS(0xb0, 0, NinSeq.COMMAND_TYPE_DS_ONLY),
	ADD_VAR_DS(0xb1, 0, NinSeq.COMMAND_TYPE_DS_ONLY),
	SUB_VAR_DS(0xb2, 0, NinSeq.COMMAND_TYPE_DS_ONLY),
	MUL_VAR_DS(0xb3, 0, NinSeq.COMMAND_TYPE_DS_ONLY),
	DIV_VAR_DS(0xb4, 0, NinSeq.COMMAND_TYPE_DS_ONLY),
	SHIFT_VAR_DS(0xb5, 0, NinSeq.COMMAND_TYPE_DS_ONLY),
	RAND_VAR_DS(0xb6, 0, NinSeq.COMMAND_TYPE_DS_ONLY),
	
	CMP_EQ_DS(0xb8, 0, NinSeq.COMMAND_TYPE_DS_ONLY),
	CMP_GE_DS(0xb9, 0, NinSeq.COMMAND_TYPE_DS_ONLY),
	CMP_GT_DS(0xba, 0, NinSeq.COMMAND_TYPE_DS_ONLY),
	CMP_LE_DS(0xbb, 0, NinSeq.COMMAND_TYPE_DS_ONLY),
	CMP_LT_DS(0xbc, 0, NinSeq.COMMAND_TYPE_DS_ONLY),
	CMP_NE_DS(0xbd, 0, NinSeq.COMMAND_TYPE_DS_ONLY),

	;
	
	private int command;
	//private CommandParamType cpt;
	private int defo_val;
	private int valid_modes;
	
	private NSCommand(int cmd, int defaultValue, int modes)
	{
		command = cmd;
		//cpt = pt;
		valid_modes = modes;
		defo_val = defaultValue;
	}
	
	public byte getCommandByte(){return (byte)command;}
	public int getDefaultValue(){return defo_val;}
	public int getCommandHigherNybble(){return (command >>> 4) & 0xF;}
	//public CommandParamType getParameterType(){return cpt;}
	
	private static ConcurrentMap<Integer, NSCommand> cmdMap_wii;
	private static ConcurrentMap<Integer, NSCommand> cmdMap_ds;
	private static ConcurrentMap<Integer, NSCommand> cmdMap_ctr;
	private static ConcurrentMap<Integer, NSCommand> cmdMap_cafe;
	
	public static NSCommand getCommand(int mode, byte value)
	{
		int ival = Byte.toUnsignedInt(value);
		//System.err.println("NinSeq.Command.getCommand || Command byte: 0x" + Integer.toHexString(ival));
		if(ival >= 0 && ival <= 0x7F) return NSCommand.PLAY_NOTE;
		switch(mode)
		{
		case NinSeq.PLAYER_MODE_DS:
			if(cmdMap_ds == null)
			{
				cmdMap_ds = new ConcurrentHashMap<Integer, NSCommand>();
				for(NSCommand c : NSCommand.values()){
					if((c.valid_modes & NinSeq.COMMAND_TYPE_DS_ONLY) != 0){
						//System.err.println("NinSeq.Command.getCommand || Command added to DS set: " + c);
						cmdMap_ds.put(c.command, c);
					}
				}
			}
			return cmdMap_ds.get(ival);
		case NinSeq.PLAYER_MODE_WII:
			if(cmdMap_wii == null)
			{
				cmdMap_wii = new ConcurrentHashMap<Integer, NSCommand>();
				for(NSCommand c : NSCommand.values()){
					if((c.valid_modes & NinSeq.COMMAND_TYPE_WII_ONLY) != 0) cmdMap_wii.put(c.command, c);
				}
			}
			return cmdMap_wii.get(ival);
		case NinSeq.PLAYER_MODE_3DS:
			if(cmdMap_ctr == null)
			{
				cmdMap_ctr = new ConcurrentHashMap<Integer, NSCommand>();
				for(NSCommand c : NSCommand.values()){
					if((c.valid_modes & NinSeq.COMMAND_TYPE_3DS_ONLY) != 0) cmdMap_ctr.put(c.command, c);
				}
			}
			return cmdMap_ctr.get(ival);
		case NinSeq.PLAYER_MODE_CAFE:
			if(cmdMap_cafe == null)
			{
				cmdMap_cafe = new ConcurrentHashMap<Integer, NSCommand>();
				for(NSCommand c : NSCommand.values()){
					if((c.valid_modes & NinSeq.COMMAND_TYPE_CAFE_ONLY) != 0) cmdMap_cafe.put(c.command, c);
				}
			}
			return cmdMap_cafe.get(ival);
		}

		return null;
	}
	

}
