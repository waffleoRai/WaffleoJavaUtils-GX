package waffleoRai_SeqSound.n64al;

import java.util.HashMap;
import java.util.Map;

public enum NUSALSeqCmdType {
	
	//basecmd, pcount, minsize, flags, sertype, name
	/*Flags are: 
	 * 
	 *  19	Data reference can overlap other data or command
	 * 	18	References an address in sequence (needs ref resolved)
	 * 	17	Open subtrack (layer/ch starts)
	 * 	16	Player/channel state modifying
	 * 
	 * 	15	Is conditional branch
	 * 	14	Is jump
	 *  13	Is call
	 *  12	Pops return stack
	 *  
	 * 	11	Is wait
	 * 	10	Consumes time (notes, rests, yield technically, etc.)
	 *  9	Once per tick (Pointless to use this cmd multiple times in same tick)
	 *  8	Param setting command
	 *  
	 *  7	Data only
	 * 	6	Seqdata modifying
	 * 	4-5	VLQ arg index
	 * 
	 * 	3	Has VLQ?
	 * 	2	Valid in Seq?
	 * 	1	Valid in Channel?
	 * 	0	Valid in Layer?
	 */
	
	/*
	 * Seq64 xml uses "Q" to refer to the shared seq variable?
	 * - and "var[C]" to refer to channel variables
	 */
	
	//Common
	
	UNRESERVE_NOTES(0xf0, 0, 1, 0x10007, NUSALSeqCommand.SERIALFMT_, "unreservenotes"),
	RESERVE_NOTES(0xf1, 1, 2, 0x10007, NUSALSeqCommand.SERIALFMT_1, "reservenotes"),
	BRANCH_IF_LTZ_REL(0xf2, 1, 2, 0x48007, NUSALSeqCommand.SERIALFMT_1, "rbltz"), //Relative addr
	BRANCH_IF_EQZ_REL(0xf3, 1, 2, 0x48007, NUSALSeqCommand.SERIALFMT_1, "rbeqz"), //Relative addr
	BRANCH_ALWAYS_REL(0xf4, 1, 2, 0x44007, NUSALSeqCommand.SERIALFMT_1, "rjump"), //Relative addr
	BRANCH_IF_GTEZ(0xf5, 1, 3, 0x48007, NUSALSeqCommand.SERIALFMT_2, "bgez"), //Abs addr
	BREAK(0xf6, 0, 1, 0x1007, NUSALSeqCommand.SERIALFMT_, "break"), //Abs addr
	LOOP_END(0xf7, 0, 1, 0x5007, NUSALSeqCommand.SERIALFMT_, "loopend"),
	LOOP_START(0xf8, 1, 2, 0x7, NUSALSeqCommand.SERIALFMT_1, "loop"),
	BRANCH_IF_LTZ(0xf9, 1, 3, 0x48007, NUSALSeqCommand.SERIALFMT_2, "bltz"),//Abs addr
	BRANCH_IF_EQZ(0xfa, 1, 3, 0x48007, NUSALSeqCommand.SERIALFMT_2, "beqz"), //Abs addr
	BRANCH_ALWAYS(0xfb, 1, 3, 0x44007, NUSALSeqCommand.SERIALFMT_2, "jump"), //Abs addr
	CALL(0xfc, 1, 3, 0x42007, NUSALSeqCommand.SERIALFMT_2, "call"), //Abs addr
	WAIT(0xfd, 1, 2, 0xe0f, NUSALSeqCommand.SERIALFMT_V, "delay"),
	YIELD(0xfe, 0, 1, 0xe07, NUSALSeqCommand.SERIALFMT_, "yield"),
	END_READ(0xff, 0, 1, 0x1007, NUSALSeqCommand.SERIALFMT_, "end"),
	
	//Sequence Commands
	
	TEST_CHANNEL(0x00, 1, 1, 0x10004, NUSALSeqCommand.SERIALFMT_L, "testchan"),
	STOP_CHANNEL(0x40, 1, 1, 0x10004, NUSALSeqCommand.SERIALFMT_L, "stopchan"),
	LOAD_BANK(0x60, 3, 3, 0x10004, NUSALSeqCommand.SERIALFMT_L11, "loadbank"),
	LOAD_IO_S(0x80, 1, 1, 0x10004, NUSALSeqCommand.SERIALFMT_L, "ldio"),
	CHANNEL_OFFSET(0x90, 2, 3, 0x60004, NUSALSeqCommand.SERIALFMT_L2, "startchan"), //90 - 9f (counting channel # as param)
	CHANNEL_OFFSET_REL(0xa0, 2, 3, 0x60004, NUSALSeqCommand.SERIALFMT_L2, "rstartchan"), //a0 - af (counting channel # as param)
	LOAD_SEQ(0xb0, 3, 4, 0x50004, NUSALSeqCommand.SERIALFMT_L12, "loadseq"), //b0 - bf (uses channel var to start new seq??)
	S_UNK_C4(0xc4, 2, 3, 0x4, NUSALSeqCommand.SERIALFMT_11, "unk_C4"),
	S_UNK_C5(0xc5, 1, 3, 0x4, NUSALSeqCommand.SERIALFMT_2, "unk_C5"),
	S_UNK_C6(0xc6, 0, 1, 0x4, NUSALSeqCommand.SERIALFMT_, "unk_C6"),
	CALL_TABLE(0xcd, 1, 3, 0x42004, NUSALSeqCommand.SERIALFMT_2, "tblcall"),
	RAND_S(0xce, 1, 2, 0x10004, NUSALSeqCommand.SERIALFMT_1, "rand"),
	NOTEALLOC_POLICY_S(0xd0, 1, 2, 0x10004, NUSALSeqCommand.SERIALFMT_1, "noteallocpolicy"),
	LOAD_SHORTTBL_GATE(0xd1, 1, 3, 0x50004, NUSALSeqCommand.SERIALFMT_2, "ldshorttablegate"),
	LOAD_SHORTTBL_VEL(0xd2, 1, 3, 0x50004, NUSALSeqCommand.SERIALFMT_2, "ldshorttablevel"),
	MUTE_BEHAVIOR_S(0xd3, 1, 2, 0x10004, NUSALSeqCommand.SERIALFMT_1, "mutebhv"),
	MUTE_S(0xd4, 0, 1, 0x104, NUSALSeqCommand.SERIALFMT_, "mute"),
	MUTE_SCALE_S(0xd5, 1, 2, 0x104, NUSALSeqCommand.SERIALFMT_1, "mutescale"),
	DISABLE_CHANNELS(0xd6, 1, 3, 0x10004, NUSALSeqCommand.SERIALFMT_2, "disablechan"),
	ENABLE_CHANNELS(0xd7, 1, 3, 0x10004, NUSALSeqCommand.SERIALFMT_2, "initchan"),
	MASTER_EXP(0xd9, 1, 2, 0x304, NUSALSeqCommand.SERIALFMT_1, "sexp"),
	MASTER_FADE(0xda, 2, 4, 0x304, NUSALSeqCommand.SERIALFMT_12, "sfade"),
	MASTER_VOLUME(0xdb, 1, 2, 0x304, NUSALSeqCommand.SERIALFMT_1, "svol"),
	SET_TEMPO_VAR(0xdc, 1, 2, 0x304, NUSALSeqCommand.SERIALFMT_1, "tempovar"),
	SET_TEMPO(0xdd, 1, 2, 0x304, NUSALSeqCommand.SERIALFMT_1, "tempo"),
	SEQ_TRANSPOSE_REL(0xd3, 1, 2, 0x304, NUSALSeqCommand.SERIALFMT_1, "stprel"),
	SEQ_TRANSPOSE(0xd3, 1, 2, 0x304, NUSALSeqCommand.SERIALFMT_1, "stp"),
	PRINT(0xef, 2, 4, 0x4, NUSALSeqCommand.SERIALFMT_21, "print"),
	
	//Sequence + Channel Commands
	SUBTRACT_IO(0x50, 1, 1, 0x10006, NUSALSeqCommand.SERIALFMT_L, "subio"),
	STORE_IO(0x70, 1, 1, 0x10006, NUSALSeqCommand.SERIALFMT_L, "stio"),
	STORE_TO_SELF(0xc7, 2, 4, 0xd0046, NUSALSeqCommand.SERIALFMT_12, "sts"),
	SUBTRACT_IMM(0xc8, 1, 2, 0x10006, NUSALSeqCommand.SERIALFMT_1, "sub"),
	AND_IMM(0xc9, 1, 2, 0x10006, NUSALSeqCommand.SERIALFMT_1, "and"),
	LOAD_IMM(0xcc, 1, 2, 0x10006, NUSALSeqCommand.SERIALFMT_1, "ldi"),
	
	//Channel Commands
	
	CH_DELTA_TIME(0x00, 1, 1, 0xe02, NUSALSeqCommand.SERIALFMT_L, "cdelay"), 
	LOAD_SAMPLE(0x10, 1, 1, 0x10002, NUSALSeqCommand.SERIALFMT_L, "loadspl"), //0x10 - 0x17
	LOAD_SAMPLE_P(0x18, 1, 1, 0x10002, NUSALSeqCommand.SERIALFMT_L, "loadsplp"), //0x18 - 0x1f
	CHANNEL_OFFSET_C(0x20, 2, 3, 0x70002, NUSALSeqCommand.SERIALFMT_L2, "startchan"),
	STORE_CHIO(0x30, 2, 2, 0x10002, NUSALSeqCommand.SERIALFMT_L1, "stcio"),
	LOAD_CHIO(0x40, 2, 2, 0x10002, NUSALSeqCommand.SERIALFMT_L1, "ldcio"),
	LOAD_IO_C(0x60, 1, 1, 0x10002, NUSALSeqCommand.SERIALFMT_L, "ldio"),
	VOICE_OFFSET_REL(0x78, 2, 3, 0x60002, NUSALSeqCommand.SERIALFMT_L2, "rstartlayer"), //0x78 - 0x7f (rel)
	TEST_VOICE(0x80, 1, 1, 0x10002, NUSALSeqCommand.SERIALFMT_L, "testlayer"), //0x80 - 0x87
	VOICE_OFFSET(0x88, 2, 3, 0x60002, NUSALSeqCommand.SERIALFMT_L2, "startlayer"), //0x88 - 0x8f (abs)
	STOP_VOICE(0x90, 1, 1, 0x10002, NUSALSeqCommand.SERIALFMT_L, "stoplayer"), //0x90 - 0x97
	VOICE_OFFSET_TABLE(0x98, 1, 1, 0x20002, NUSALSeqCommand.SERIALFMT_L, "dynstartlayer"), //0x98 - 0x9f
	SET_CH_FILTER(0xb0, 1, 3, 0x40302, NUSALSeqCommand.SERIALFMT_2, "setfilter"),
	CLEAR_CH_FILTER(0xb1, 0, 1, 0x302, NUSALSeqCommand.SERIALFMT_, "clearfilter"),
	LOAD_P_TABLE(0xb2, 1, 3, 0x50002, NUSALSeqCommand.SERIALFMT_2, "ldptbl"),
	COPY_CH_FILTER(0xb3, 2, 2, 0x42, NUSALSeqCommand.SERIALFMT_COPYFILTER, "copyfilter"),
	DYNTABLE_WRITE(0xb4, 0, 1, 0x10002, NUSALSeqCommand.SERIALFMT_, "p2dyntable"),
	DYNTABLE_READ(0xb5, 0, 1, 0x10002, NUSALSeqCommand.SERIALFMT_, "dyntable2p"),
	DYNTABLE_LOAD(0xb6, 0, 1, 0x10002, NUSALSeqCommand.SERIALFMT_, "lddyn"),
	RANDP(0xb7, 1, 2, 0x10002, NUSALSeqCommand.SERIALFMT_1, "randp"),
	RAND_C(0xb8, 1, 2, 0x10002, NUSALSeqCommand.SERIALFMT_1, "rand"),
	VELRAND(0xb9, 1, 2, 0x302, NUSALSeqCommand.SERIALFMT_1, "velrand"),
	GATERAND(0xba, 1, 2, 0x302, NUSALSeqCommand.SERIALFMT_1, "gaterand"),
	C_UNK_BB(0xbb, 2, 4, 0x2, NUSALSeqCommand.SERIALFMT_12, "unk_BB"),
	ADD_IMM_P(0xbc, 1, 3, 0x10002, NUSALSeqCommand.SERIALFMT_2, "addp"),
	ADD_RAND_IMM_P(0xbd, 2, 4, 0x10002, NUSALSeqCommand.SERIALFMT_12, "randaddp"),
	C_UNK_C0(0xc0, 1, 2, 0x2, NUSALSeqCommand.SERIALFMT_1, "unk_C0"),
	SET_PROGRAM(0xc1, 1, 2, 0x302, NUSALSeqCommand.SERIALFMT_1, "instr"),
	SET_DYNTABLE(0xc2, 2, 3, 0x50002, NUSALSeqCommand.SERIALFMT_2, "dyntable"),
	SHORTNOTE_ON(0xc3, 0, 1, 0x202, NUSALSeqCommand.SERIALFMT_, "shorton"),
	SHORTNOTE_OFF(0xc4, 0, 1, 0x202, NUSALSeqCommand.SERIALFMT_, "shortoff"),
	SHIFT_DYNTABLE(0xc5, 0, 1, 0x10002, NUSALSeqCommand.SERIALFMT_, "dynsetdyntable"),
	SET_BANK(0xc6, 1, 2, 0x302, NUSALSeqCommand.SERIALFMT_1, "bank"),
	MUTE_BEHAVIOR_C(0xca, 1, 2, 0x10002, NUSALSeqCommand.SERIALFMT_1, "mutebhv"),
	LOAD_FROM_SELF(0xcb, 1, 3, 0x50002, NUSALSeqCommand.SERIALFMT_2, "lds"),
	STOP_CHANNEL_C(0xcd, 1, 2, 0x10002, NUSALSeqCommand.SERIALFMT_1, "stopchan"),
	LOAD_IMM_P(0xce, 1, 3, 0x50002, NUSALSeqCommand.SERIALFMT_2, "ldpi"),
	STORE_TO_SELF_P(0xce, 1, 3, 0x50042, NUSALSeqCommand.SERIALFMT_2, "stps"),
	CH_STEREO_EFF(0xd0, 1, 2, 0x302, NUSALSeqCommand.SERIALFMT_1, "stereoheadseteffects"),
	CH_NOTEALLOC_POLICY(0xd1, 1, 2, 0x10002, NUSALSeqCommand.SERIALFMT_1, "noteallocpolicy"),
	CH_SUSTAIN(0xd2, 1, 2, 0x302, NUSALSeqCommand.SERIALFMT_1, "sustain"),
	CH_PITCHBEND(0xd3, 1, 2, 0x302, NUSALSeqCommand.SERIALFMT_1, "pitchbend"),
	CH_REVERB(0xd4, 1, 2, 0x302, NUSALSeqCommand.SERIALFMT_1, "reverb"),
	CH_VIBRATO_FREQ(0xd8, 1, 2, 0x302, NUSALSeqCommand.SERIALFMT_1, "vibfreq"),
	CH_VIBRATO_DEPTH(0xd8, 1, 2, 0x302, NUSALSeqCommand.SERIALFMT_1, "vibdepth"),
	CH_RELEASE(0xd9, 1, 2, 0x302, NUSALSeqCommand.SERIALFMT_1, "release"),
	CH_ENVELOPE(0xda, 1, 3, 0x40302, NUSALSeqCommand.SERIALFMT_2, "cenvelope"),
	CH_TRANSPOSE(0xdb, 1, 2, 0x302, NUSALSeqCommand.SERIALFMT_1, "ctp"),
	CH_PANMIX(0xdc, 1, 2, 0x302, NUSALSeqCommand.SERIALFMT_1, "panmix"),
	CH_PAN(0xdd, 1, 2, 0x302, NUSALSeqCommand.SERIALFMT_1, "cpan"),
	CH_FREQSCALE(0xde, 1, 3, 0x302, NUSALSeqCommand.SERIALFMT_2, "freqscale"),
	CH_VOLUME(0xdf, 1, 2, 0x302, NUSALSeqCommand.SERIALFMT_1, "cvol"),
	CH_EXP(0xe0, 1, 2, 0x302, NUSALSeqCommand.SERIALFMT_1, "cexp"),
	CH_VIBRATO_FREQENV(0xe1, 3, 4, 0x302, NUSALSeqCommand.SERIALFMT_111, "vibfreqenv"),
	CH_VIBRATO_DEPTHENV(0xe2, 3, 4, 0x302, NUSALSeqCommand.SERIALFMT_111, "vibdepthenv"),
	CH_VIBRATO_DELAY(0xe3, 1, 2, 0x302, NUSALSeqCommand.SERIALFMT_1, "vibdelay"),
	CALL_DYNTABLE(0xe4, 0, 1, 0x2002, NUSALSeqCommand.SERIALFMT_, "dyncall"),
	CH_REVERB_IDX(0xe5, 1, 2, 0x302, NUSALSeqCommand.SERIALFMT_1, "reverbidx"),
	CH_SAMPLE_VARIATION(0xe6, 1, 2, 0x10302, NUSALSeqCommand.SERIALFMT_1, "splvari"),
	CH_LOAD_PARAMS(0xe7, 1, 3, 0x40302, NUSALSeqCommand.SERIALFMT_2, "ldcparams"),
	CH_SET_PARAMS(0xe8, 8, 9, 0x302, NUSALSeqCommand.SERIALFMT_CPARAMS, "cparams"),
	CH_PRIORITY(0xe9, 1, 2, 0x302, NUSALSeqCommand.SERIALFMT_1, "notepriority"),
	CH_HALT(0xea, 0, 1, 0x2, NUSALSeqCommand.SERIALFMT_, "halt"),
	SET_BANK_AND_PROGRAM(0xeb, 2, 3, 0x302, NUSALSeqCommand.SERIALFMT_11, "bankinstr"),
	CH_RESET(0xec, 0, 1, 0x2, NUSALSeqCommand.SERIALFMT_, "chanreset"),
	CH_FILTER_GAIN(0xed, 1, 2, 0x302, NUSALSeqCommand.SERIALFMT_1, "filgain"),
	CH_PITCHBEND_ALT(0xee, 1, 2, 0x302, NUSALSeqCommand.SERIALFMT_1, "cbend2"),
	
	//Voice Commands
	L_SHORTNOTE(0x00, 1, 1, 0x601, NUSALSeqCommand.SERIALFMT_NOTE, "note"),
	PLAY_NOTE_NTVG(0x00, 4, 4, 0x619, NUSALSeqCommand.SERIALFMT_NOTE, "notedvg"), //0x00 - 0x3f
	PLAY_NOTE_NTV(0x40, 3, 3, 0x619, NUSALSeqCommand.SERIALFMT_NOTE, "notedv"), //0x40 - 0x7f
	PLAY_NOTE_NVG(0x80, 3, 3, 0x601, NUSALSeqCommand.SERIALFMT_NOTE, "notevg"), //0x80 - 0xbf
	REST(0xc0, 1, 2, 0xe09, NUSALSeqCommand.SERIALFMT_V, "ldelay"),
	L_SHORTVEL(0xc1, 1, 2, 0x301, NUSALSeqCommand.SERIALFMT_1, "shortvel"),
	L_TRANSPOSE(0xc2, 1, 2, 0x301, NUSALSeqCommand.SERIALFMT_1, "ltp"),
	L_SHORTTIME(0xc3, 1, 2, 0x309, NUSALSeqCommand.SERIALFMT_V, "shortdelay"),
	LEGATO_ON(0xc4, 0, 1, 0x301, NUSALSeqCommand.SERIALFMT_, "legatoon"),
	LEGATO_OFF(0xc5, 0, 1, 0x301, NUSALSeqCommand.SERIALFMT_, "legatooff"),
	L_SET_PROGRAM(0xc6, 1, 2, 0x301, NUSALSeqCommand.SERIALFMT_1, "linst"),
	PORTAMENTO_ON(0xc7, 3, 4, 0x301, NUSALSeqCommand.SERIALFMT_111, "portamento"),
	PORTAMENTO_OFF(0xc8, 0, 1, 0x301, NUSALSeqCommand.SERIALFMT_, "portamentooff"),
	L_SHORTGATE(0xc9, 1, 2, 0x301, NUSALSeqCommand.SERIALFMT_1, "shortgate"),
	L_PAN(0xca, 1, 2, 0x301, NUSALSeqCommand.SERIALFMT_1, "lpan"),
	L_ENVELOPE(0xcb, 2, 4, 0x40301, NUSALSeqCommand.SERIALFMT_21, "lenvelope"),
	DRUMPAN_OFF(0xcc, 0, 1, 0x301, NUSALSeqCommand.SERIALFMT_, "drumpanoff"),
	L_REVERB_PHASE(0xcd, 1, 2, 0x301, NUSALSeqCommand.SERIALFMT_1, "reverbphase"),
	L_PITCHBEND_ALT(0xce, 1, 2, 0x301, NUSALSeqCommand.SERIALFMT_1, "lbend2"),
	L_RELEASE(0xcf, 1, 2, 0x301, NUSALSeqCommand.SERIALFMT_1, "lrelease"),
	SHORTTBL_VEL(0xd0, 1, 1, 0x301, NUSALSeqCommand.SERIALFMT_L, "shorttablevel"),
	SHORTTBL_GATE(0xe0, 1, 1, 0x301, NUSALSeqCommand.SERIALFMT_L, "shorttablegate"),
	
	//Conversion Only
	PSEUDO_MIDILIKE_NOTE(0x00, 4, 4, 0x0, NUSALSeqCommand.SERIALFMT_DUMMY, "pseudomid"), //Holds all NTVG. 
	/*These are used initially for conversion TO an AL seq then during additional optimization
	 * converted to the actually AL seq commands (with transposition if needed)
	*/
	DATA_ONLY(0x00, 1, 1, 0x80, NUSALSeqCommand.SERIALFMT_DUMMY, "DATA"),
	MULTI_EVENT_CHUNK(0x00, 1, 1, 0x0, NUSALSeqCommand.SERIALFMT_DUMMY, "BLOCK"),
	UNRESOLVED_LINK(0x00, 1, 1, 0x80, NUSALSeqCommand.SERIALFMT_DUMMY, "UNKLINK");
	
	;

	private byte base_cmd;
	private int param_count;
	private int min_bytes;
	private int flags;
	
	private boolean has_varlen;
	private boolean valid_seq;
	private boolean valid_ch;
	private boolean valid_vox;
	
	private int vlq_idx;
	private int serial_type;
	
	//private NUSALSeqCmdType[] tickmutex;
	
	private String name;
	
	private NUSALSeqCmdType(int cmd, int params, int minbytes, int flags, int sertype, String str){
		base_cmd = (byte)cmd;
		param_count = params;
		min_bytes = minbytes;
		serial_type = sertype;
		
		this.flags = flags;
		has_varlen = (flags & 0x8) != 0;
		valid_seq  = (flags & 0x4) != 0;
		valid_ch   = (flags & 0x2) != 0;
		valid_vox  = (flags & 0x1) != 0;
		if(has_varlen){
			vlq_idx = (flags & 0x20) >>> 4;
		}
		name = str;
	}
	
	public byte getBaseCommand(){return base_cmd;}
	public int getParameterCount(){return param_count;}
	public int getMinimumSizeInBytes(){return min_bytes;}
	public int getSerializationType(){return serial_type;}
	
	public boolean hasVariableLength(){return has_varlen;}
	public boolean validInSeq(){return valid_seq;}
	public boolean validInChannel(){return valid_ch;}
	public boolean validInLayer(){return valid_vox;}
	public int getVLQIndex(){return vlq_idx;}
	
	public boolean flagSet(int flagMask){
		return(flags & flagMask) != 0;
	}
	
	public String toString(){return name;}
	
	private static Map<String, NUSALSeqCmdType> strmap;
	
	public static NUSALSeqCmdType fromStringCommand(String s){
		if(strmap == null){
			strmap = new HashMap<String, NUSALSeqCmdType>();
			for(NUSALSeqCmdType t : values()){
				strmap.put(t.name, t);
			}
		}
		
		return strmap.get(s.toLowerCase());
	}
	
}
