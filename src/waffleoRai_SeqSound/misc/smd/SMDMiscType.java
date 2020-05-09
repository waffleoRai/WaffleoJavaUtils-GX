package waffleoRai_SeqSound.misc.smd;

public enum SMDMiscType {
	
	WAIT_AGAIN(0x90, 0, "WAIT_AGAIN"),
	WAIT_ADD(0x91, 1, "WAIT_ADD"),
	WAIT_1BYTE(0x92, 1, "WAIT_1BYTE"),
	WAIT_2BYTE(0x93, 2, "WAIT_2BYTE"), //LE
	TRACK_END(0x98, 0, "TRACK_END"),
	LOOP_POINT(0x99, 0, "LOOP_POINT"),
	SET_OCTAVE(0xA0, 1, "SET_OCTAVE"),
	SET_TEMPO(0xA4, 1, "SET_TEMPO"),
	SET_SAMPLE(0xAC, 1, "SET_SAMPLE"),
	SET_MODU(0xBE, 1, "SET_MODU"),
	SET_BEND(0xD7, 2, "SET_BEND"),
	SET_VOLUME(0xE0, 1, "SET_VOLUME"),
	SET_XPRESS(0xE3, 1, "SET_EXPRESS"),
	SET_PAN(0xE8, 1, "SET_PAN"),
	NA_NOTE(0x00, -1, "PLAY_NOTE"),
	NA_DELTATIME(0x80, 1, "DELTATIME"),
	UNK_9C(0x9C, 1, "UNK_9C"),
	UNK_9D(0x9D, 0, "UNK_9D"),
	UNK_A8(0xA8, 2, "UNK_A8"),
	UNK_A9(0xA9, 1, "UNK_A9"),
	UNK_AA(0xAA, 1, "UNK_AA"),
	UNK_B2(0xB2, 1, "UNK_B2"),
	UNK_B4(0xB4, 2, "UNK_B4"),
	UNK_B5(0xB5, 1, "UNK_B5"),
	UNK_BF(0xBF, 1, "UNK_BF"),
	UNK_C0(0xC0, 0, "UNK_C0"),
	UNK_D0(0xD0, 1, "UNK_D0"),
	UNK_D1(0xD1, 1, "UNK_D1"),
	UNK_D2(0xD2, 1, "UNK_D2"),
	UNK_D4(0xD4, 3, "UNK_D4"),
	UNK_D6(0xD6, 2, "UNK_D6"),
	UNK_DB(0xDB, 1, "UNK_DB"),
	UNK_DC(0xDC, 5, "UNK_DC"),
	UNK_E2(0xE2, 3, "UNK_E2"),
	UNK_EA(0xEA, 3, "UNK_EA"),
	UNK_F6(0xF6, 1, "UNK_F6");
	
	private int op;
	private int numParam;
	private String sRep;
	
	private SMDMiscType(int opcode, int nParam, String s)
	{
		this.op = opcode;
		this.numParam = nParam;
		this.sRep = s;
	}
	
	public int getOpCode()
	{
		return this.op;
	}
	
	public int getParameterCount()
	{
		return this.numParam;
	}
	
	public static SMDMiscType getType(byte opcode)
	{
		int oci = Byte.toUnsignedInt(opcode);
		switch(oci)
		{
		case(0x90): return WAIT_AGAIN;
		case(0x91): return WAIT_ADD;
		case(0x92): return WAIT_1BYTE;
		case(0x93): return WAIT_2BYTE;
		case(0x98): return TRACK_END;
		case(0x99): return LOOP_POINT;
		case(0xA0): return SET_OCTAVE;
		case(0xA4): return SET_TEMPO;
		case(0xAC): return SET_SAMPLE;
		case(0xBE): return SET_MODU;
		case(0xD7): return SET_BEND;
		case(0xE0): return SET_VOLUME;
		case(0xE3): return SET_XPRESS;
		case(0xE8): return SET_PAN;
		case(0x9C): return UNK_9C;
		case(0x9D): return UNK_9D;
		case(0xA8): return UNK_A8;
		case(0xA9): return UNK_A9;
		case(0xAA): return UNK_AA;
		case(0xB2): return UNK_B2;
		case(0xB4): return UNK_B4;
		case(0xB5): return UNK_B5;
		case(0xBF): return UNK_BF;
		case(0xC0): return UNK_C0;
		case(0xD0): return UNK_D0;
		case(0xD1): return UNK_D1;
		case(0xD2): return UNK_D2;
		case(0xD4): return UNK_D4;
		case(0xD6): return UNK_D6;
		case(0xDB): return UNK_DB;
		case(0xDC): return UNK_DC;
		case(0xE2): return UNK_E2;
		case(0xEA): return UNK_EA;
		case(0xF6): return UNK_F6;
		default: return null;
		}
	}
	
	public String toString()
	{
		return sRep;
	}
	

}
