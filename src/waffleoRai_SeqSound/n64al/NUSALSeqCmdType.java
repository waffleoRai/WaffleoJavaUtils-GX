package waffleoRai_SeqSound.n64al;

public enum NUSALSeqCmdType {
	
	/*
	 * Seq64 xml uses "Q" to refer to the shared seq variable?
	 * - and "var[C]" to refer to channel variables
	 */

	//Common
	
	BRANCH_IF_LTZ_REL(0xf2, 1, 2), //Relative addr
	BRANCH_IF_EQZ_REL(0xf3, 1, 2), //Relative addr
	BRANCH_ALWAYS_REL(0xf4, 1, 2), //Relative addr
	BRANCH_IF_GTEZ(0xf5, 1, 3), //Abs addr
	BRANCH_TO_SEQSTART(0xf6, 1, 1), //Abs addr
	LOOP_END(0xf7, 0, 1),
	LOOP_START(0xf8, 1, 2),
	BRANCH_IF_LTZ(0xf9, 1, 3),//Abs addr
	BRANCH_IF_EQZ(0xfa, 1, 3), //Abs addr
	BRANCH_ALWAYS(0xfb, 1, 3), //Abs addr
	CALL(0xfc, 1, 3), //Abs addr
	WAIT(0xfd, 1, 2),
	RETURN(0xfe, 0, 1), //Maybe?
	END_READ(0xff, 0, 1),
	
	//Sequence Commands
	
	CHANNEL_OFFSET(0x90, 2, 3), //90 - 9f (counting channel # as param)
	CHANNEL_OFFSET_REL(0xa0, 2, 2), //a0 - af (counting channel # as param)
	NEW_SEQ_FROM(0xb0, 3, 4), //b0 - bf (uses channel var to start new seq??)
	RETURN_FROM_SEQ(0xc6, 0, 1),
	SUBTRACT_IMM(0xc8, 1, 2),
	AND_IMM(0xc9, 1, 2),
	SET_VAR(0xcc, 1, 2),
	SET_FORMAT(0xd3, 1, 2),
	SET_TYPE(0xd5, 1, 2),
	DISABLE_CHANNELS(0xd6, 1, 3),
	ENABLE_CHANNELS(0xd7, 1, 3),
	MASTER_VOLUME (0xdb, 1, 2),
	SET_TEMPO(0xdd, 1, 2),
	
	SEQ_UNK_10(0x10, 0, 1), //In seq64, but ignored
	SEQ_UNK_4x(0x40, 1, 1), //40 - 4f: Does something unknown with channel
	SEQ_UNK_8x(0x80, 1, 1), //80 - 8f: Does some conditional variable thing with channel
	SEQ_UNK_C4(0xc4, 2, 3), //seq64 thinks "return from parsing" ?
	SEQ_UNK_C5(0xc5, 1, 3), //seq64: "Store to SeqParams[E0]"
	SEQ_UNK_C7(0xc7, 2, 3),
	SEQ_UNK_CA(0xca, 0, 1),
	SEQ_UNK_CB(0xcb, 0, 1),
	SEQ_UNK_DF(0xdf, 1, 2),
	SEQ_UNK_EF(0xef, 2, 4),
	
	LOAD_CH_VAR(0x00, 1, 1), //00 - 0f not sure what it does
	SUBTRACT_CH(0x50, 1, 1), //50 - 5f Subtract ch variable from seq variable?
	LOAD_CH_DMA(0x60, 3, 3), //60 - 6f
	SET_CH_VAR(0x70, 1, 1), //70 - 7f
	
	//Channel Commands
	
	CH_DELTA_TIME(0x00, 1, 1), //"Command" for cases where I didn't build a time param into the previous command
	VOICE_OFFSET_REL(0x78, 2, 2), //0x78 - 0x7b (rel)
	VOICE_OFFSET(0x88, 2, 3), //0x88 - 0x8b (abs)
	SET_PROGRAM(0xc1, 2, 2),
	TRANSPOSE(0xc2, 2, 2),
	INIT_CHANNEL(0xc4, 1, 1),
	CH_PITCHBEND(0xd3, 2, 2), //Can take 2 param, but not sure when
	CH_REVERB(0xd4, 2, 2),
	CH_VIBRATO(0xd8, 2, 2),
	CH_DRUMSET(0xdc, 2, 2),
	CH_PAN(0xdd, 2, 2),
	CH_VOLUME(0xdf, 2, 2), //Can take 2 param, but not sure when
	CH_PRIORITY(0xe9, 2, 2),
	
	//Voice Commands
	PLAY_NOTE_NTVG(0x00, 4, 4), //0x00 - 0x3f
	PLAY_NOTE_NTV(0x40, 3, 3), //0x40 - 0x7f
	PLAY_NOTE_NVG(0x80, 3, 3), //0x80 - 0xbf
	REST(0xc0, 1, 2),
	
	CH_UNK_B0(0xb0, 2, 3),
	CH_UNK_B3(0xb3, 2, 2),
	CH_UNK_DA(0xda, 2, 2),
	CH_UNK_E0(0xe0, 2, 2),
	
	//Conversion Only
	PSEUDO_MIDILIKE_NOTE(0x00, 4, 3), //Holds all NTVG. 
	/*These are used initially for conversion TO an AL seq then during additional optimization
	 * converted to the actually AL seq commands (with transposition if needed)
	*/
	MULTI_EVENT_CHUNK(0x00, 1, 1);
	
	;
	
	private byte base_cmd;
	private int param_count;
	private int min_bytes;
	
	private NUSALSeqCmdType(int cmd, int params, int minbytes){
		base_cmd = (byte)cmd;
		param_count = params;
		min_bytes = minbytes;
	}
	
	public byte getBaseCommand(){return base_cmd;}
	public int getParameterCount(){return param_count;}
	public int getMinimumSizeInBytes(){return min_bytes;}
	
}
