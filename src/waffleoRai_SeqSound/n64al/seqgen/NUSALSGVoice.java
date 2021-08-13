package waffleoRai_SeqSound.n64al.seqgen;

import java.util.Map;

import waffleoRai_SeqSound.n64al.NUSALSeqCommand;

public class NUSALSGVoice {

	private Map<Integer, NUSALSeqCommand> cmdmap;
	
	private int tick;
	
	private boolean active_note;
	private byte midi_note;
	private byte velocity;
	private int start_tick;
	
}
