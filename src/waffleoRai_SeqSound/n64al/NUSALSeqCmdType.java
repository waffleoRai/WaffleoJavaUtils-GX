package waffleoRai_SeqSound.n64al;

public enum NUSALSeqCmdType {
	
	/*
	 * Seq64 xml uses "Q" to refer to the shared seq variable?
	 * - and "var[C]" to refer to channel variables
	 */

	//Common
	
	BRANCH_IF_LTZ_REL(0xf2, 1), //Relative addr
	BRANCH_IF_EQZ_REL(0xf3, 1), //Relative addr
	BRANCH_ALWAYS_REL(0xf4, 1), //Relative addr
	BRANCH_IF_GTEZ(0xf5, 1), //Abs addr
	BRANCH_TO_SEQSTART(0xf6, 1), //Abs addr
	LOOP_END(0xf7, 0),
	LOOP_START(0xf8, 1),
	BRANCH_IF_LTZ(0xf9, 1),//Abs addr
	BRANCH_IF_EQZ(0xfa, 1), //Abs addr
	BRANCH_ALWAYS(0xfb, 1), //Abs addr
	CALL(0xfc, 1), //Abs addr
	WAIT(0xfd, 1),
	RETURN(0xfe, 0), //Maybe?
	END_READ(0xff, 0),
	
	//Sequence Commands
	
	CHANNEL_OFFSET(0x90, 2), //90 - 9f (counting channel # as param)
	CHANNEL_OFFSET_REL(0xa0, 2), //a0 - af (counting channel # as param)
	NEW_SEQ_FROM(0xb0, 3), //b0 - bf (uses channel var to start new seq??)
	RETURN_FROM_SEQ(0xc6, 0),
	SUBTRACT_IMM(0xc8, 1),
	AND_IMM(0xc9, 1),
	SET_VAR(0xcc, 1),
	SET_FORMAT(0xd3, 1),
	SET_TYPE(0xd5, 1),
	DISABLE_CHANNELS(0xd6, 1),
	ENABLE_CHANNELS(0xd7, 1),
	MASTER_VOLUME (0xdb, 1),
	SET_TEMPO(0xdd, 1),
	
	SEQ_UNK_10(0x10, 0), //In seq64, but ignored
	SEQ_UNK_4x(0x40, 1), //40 - 4f: Does something unknown with channel
	SEQ_UNK_8x(0x80, 1), //80 - 8f: Does some conditional variable thing with channel
	SEQ_UNK_C4(0xc4, 2), //seq64 thinks "return from parsing" ?
	SEQ_UNK_C5(0xc5, 1), //seq64: "Store to SeqParams[E0]"
	SEQ_UNK_C7(0xc7, 2),
	SEQ_UNK_CA(0xca, 0),
	SEQ_UNK_CB(0xcb, 0),
	SEQ_UNK_DF(0xdf, 1),
	SEQ_UNK_EF(0xef, 2),
	
	LOAD_CH_VAR(0x00, 1), //00 - 0f not sure what it does
	SUBTRACT_CH(0x50, 1), //50 - 5f Subtract ch variable from seq variable?
	LOAD_CH_DMA(0x60, 3), //60 - 6f
	SET_CH_VAR(0x70, 1), //70 - 7f
	
	//Channel Commands
	
	VOICE_OFFSET_REL(0x78, 2), //0x78 - 0x7b (rel)
	VOICE_OFFSET(0x88, 2), //0x88 - 0x8b (abs)
	SET_PROGRAM(0xc1, 1),
	TRANSPOSE(0xc2, 1),
	INIT_CHANNEL(0xc4, 0),
	CH_PITCHBEND(0xd3, 2), //Can take 2 param, but not sure when
	CH_REVERB(0xd4, 1),
	CH_VIBRATO(0xd8, 2),
	CH_DRUMSET(0xdc, 1),
	CH_PAN(0xdd, 2),
	CH_VOLUME(0xdf, 2), //Can take 2 param, but not sure when
	CH_PRIORITY(0xe9, 1),
	
	//Voice Commands
	PLAY_NOTE_NTVG(0x00, 4), //0x00 - 0x3f
	PLAY_NOTE_NTV(0x40, 3), //0x40 - 0x7f
	PLAY_NOTE_NVG(0x80, 3), //0x80 - 0xbf
	REST(0xc0, 1),
	
	CH_UNK_DA(0xda, 1),
	CH_UNK_E0(0xe0, 1),
	
	//Conversion Only
	PSEUDO_MIDILIKE_NOTE(0x00, 4), //Holds all NTVG. 
	/*These are used initially for conversion TO an AL seq then during additional optimization
	 * converted to the actualy AL seq commands (with transposition if needed)
	*/
	MULTI_EVENT_CHUNK(0x00, 1);
	
	;
	
	private byte base_cmd;
	private int param_count;
	
	private NUSALSeqCmdType(int cmd, int params){
		base_cmd = (byte)cmd;
		param_count = params;
	}
	
	public byte getBaseCommand(){return base_cmd;}
	public int getParameterCount(){return param_count;}
	
}
