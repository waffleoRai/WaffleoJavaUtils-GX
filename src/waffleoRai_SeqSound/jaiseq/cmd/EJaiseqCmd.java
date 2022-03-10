package waffleoRai_SeqSound.jaiseq.cmd;

public enum EJaiseqCmd {
	
	OPEN_TRACK(0xc1, "opentrack", EJaiSerialType.SERIALFMT_13),
	OPEN_TRACK_SIB(0xc1, "opensibtrack", EJaiSerialType.SERIALFMT_),
	END_TRACK(0xff, "end", EJaiSerialType.SERIALFMT_),
	
	NOTE_ON(0x00, "noteon", EJaiSerialType.SERIALFMT_NOTE),
	NOTE_OFF(0x81, "noteoff", EJaiSerialType.SERIALFMT_L),
	DELAY_8(0x80, "delay_8", EJaiSerialType.SERIALFMT_1),
	DELAY_16(0x88, "delay_16", EJaiSerialType.SERIALFMT_2),
	DELAY(0xf0, "delay", EJaiSerialType.SERIALFMT_V),
	
	TEMPO(0xfe, "tempo", EJaiSerialType.SERIALFMT_2),
	TEMPO_RVL(0xe0, "tempo2", EJaiSerialType.SERIALFMT_2),
	TIMEBASE(0xfd, "timebase", EJaiSerialType.SERIALFMT_2),
	
	SET_BANK(0xe2, "bank", EJaiSerialType.SERIALFMT_1),
	SET_PROGRAM(0xe3, "instr", EJaiSerialType.SERIALFMT_1),
	VIB_WIDTH(0xe6, "vibfreq", EJaiSerialType.SERIALFMT_2),
	VIB_DEPTH(0xf4, "vibdepth", EJaiSerialType.SERIALFMT_1),
	
	JUMP(0xc7, "jump", EJaiSerialType.SERIALFMT_3),
	JUMP_COND(0xc8, "jumpc", EJaiSerialType.SERIALFMT_13),
	CALL(0xc3, "call", EJaiSerialType.SERIALFMT_3),
	CALL_COND(0xc4, "callc", EJaiSerialType.SERIALFMT_13),
	RETURN(0xc5, "return", EJaiSerialType.SERIALFMT_),
	RETURN_COND(0xc6, "returnc", EJaiSerialType.SERIALFMT_1),
	
	SETPERF(0x90, "perf", EJaiSerialType.SERIALFMT_DUMMY),
	PERF_U8_NODUR(0x94, "perf_u8_nodur", EJaiSerialType.SERIALFMT_11),
	PERF_U8_U8(0x96, "perf_u8_u8", EJaiSerialType.SERIALFMT_111),
	PERF_U8_U16(0x97, "perf_u8_u16", EJaiSerialType.SERIALFMT_112),
	PERF_S8_NODUR(0x98, "perf_s8_nodur", EJaiSerialType.SERIALFMT_11),
	PERF_S8_U8(0x9a, "perf_s8_u8", EJaiSerialType.SERIALFMT_111),
	PERF_S8_U16(0x9b, "perf_s8_u16", EJaiSerialType.SERIALFMT_112),
	PERF_S16_NODUR(0x9c, "perf_s16_nodur", EJaiSerialType.SERIALFMT_12),
	PERF_S16_U8(0x9e, "perf_s16_u8", EJaiSerialType.SERIALFMT_121),
	PERF_S16_U16(0x9f, "perf_s16_u16", EJaiSerialType.SERIALFMT_122),
	
	SETPERF_RVL(0xb0, "perfrvl", EJaiSerialType.SERIALFMT_DUMMY),
	PERFRVL_S8(0xb8, "perf2_s8", EJaiSerialType.SERIALFMT_11),
	PERFRVL_S16(0xb9, "perf2_s16", EJaiSerialType.SERIALFMT_12),
	
	ARTIC(0xd8, "artic", EJaiSerialType.SERIALFMT_12),
	
	SETPARAM(0xa0, "perfrvl", EJaiSerialType.SERIALFMT_DUMMY),
	PARAM_8(0xa4, "param_8", EJaiSerialType.SERIALFMT_11),
	PARAM_16(0xac, "param_16", EJaiSerialType.SERIALFMT_12),
	
	VSYNC(0xe7, "vsync", EJaiSerialType.SERIALFMT_2),
	
	UNK_CB(0xcb, "unk_CB", EJaiSerialType.SERIALFMT_2),
	UNK_F9(0xf9, "unk_F9", EJaiSerialType.SERIALFMT_2),
	
	;
	
	private byte status_byte;
	private String mml_cmd;
	private EJaiSerialType serial_type;
	
	private EJaiseqCmd(int stat, String mml, EJaiSerialType st){
		status_byte = (byte)stat;
		mml_cmd = mml;
		serial_type = st;
	}
	
	public byte getStatusByte(){return status_byte;}
	public EJaiSerialType getSerialType(){return serial_type;}
	
	public String toString(){
		return mml_cmd;
	}

}
