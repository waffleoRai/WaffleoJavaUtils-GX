package waffleoRai_SeqSound.n64al.seqgen;

import waffleoRai_SeqSound.n64al.NUSALSeqCommand;
import waffleoRai_SeqSound.n64al.NUSALSeqCommandMap;

public class NUSALSeqBuilder {

	/*--- Constants ---*/
	
	/*--- Instance Variables ---*/
	
	private NUSALSeqInitValues init_val;
	
	private NUSALSeqBuilderChannel[] channels;
	
	/*--- Init ---*/
	
	public NUSALSeqBuilder(){
		init_val = new NUSALSeqInitValues();
		channels = new NUSALSeqBuilderChannel[16];
		for(int i = 0 ; i <16; i++) channels[i] = new NUSALSeqBuilderChannel();
	}
	
	/*--- Getters ---*/
	
	/*--- Setters ---*/
	
	public void setInitTempo(int bpm){init_val.tempo = bpm;}
	public void setInitVol(int val){init_val.volume = val;}
	
	/*--- Add Commands ---*/
	
	public void addSeqCommandAtTick(int tick, NUSALSeqCommand cmd){
		//TODO
	}
	
	public void addChannelCommandAtTick(int tick, int ch, NUSALSeqCommand cmd){
		//TODO
	}
	
	public void addLayerCommandAtTick(int tick, int ch, int ly, NUSALSeqCommand cmd){
		//TODO
	}
	
	public void addLayerNoteAtTick(int tick, int ch, int ly, byte note, byte vel, int len){
		//TODO
	}
	
	public void addSequenceSubroutine(NUSALSeqCommandMap tickmap){
		//TODO
	}
	
	/*--- Serialize ---*/
	
	
}
