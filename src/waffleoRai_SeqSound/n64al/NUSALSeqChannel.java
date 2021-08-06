package waffleoRai_SeqSound.n64al;

import java.util.Deque;
import java.util.LinkedList;

import waffleoRai_SoundSynth.SequenceController;

public class NUSALSeqChannel {
	
	/*----- Instance Variables -----*/

	private int pos;
	private Deque<Integer> return_stack;
	private int ch_idx;
	
	private int var;
	private int transpose;
	private boolean init_flag;
	private boolean term_flag;
	private int wait_remain;
	
	private int err_addr;
	//private NUSALSeqCommand bad_cmd;
	private int err_layer;
	
	private SequenceController target;
	private NUSALSeqCommandSource source;
	
	private NUSALSeqLayer[] layers;
	
	private int program;
	private byte pitchbend;
	private byte eff;
	private byte vibrato;
	private byte pan;
	private byte volume;
	private byte priority;
	
	private boolean loop_enabled;
	private int lcounter; //Keeps track of how many times loop has occured
	private int loopst;
	private int looped;
	private int loopct;
	
	/*----- Initialization -----*/
	
	public NUSALSeqChannel(int index){
		//pos = startpos;
		ch_idx = index;
		//pending_notes = new TreeMap<Byte, Integer>();
		layers = new NUSALSeqLayer[4];
		return_stack = new LinkedList<Integer>();
		reset();
	}
	
	public void reset(){
		return_stack.clear();
		wait_remain = 0;
		term_flag = true;
		//err_flag = false;
		err_addr = -1; err_layer = -1;
		for(int i = 0; i < 4; i++){
			layers[i] = new NUSALSeqLayer(this, i);
		}
		init_flag = false;
		lcounter = 0;
		pos = -1;
		
		program = 0;
		pitchbend = 0;
		eff = 0;
		vibrato = 0;
		pan = 0x3f;
		volume = 0x7f;
		
		loop_enabled = false;
		loopst = -1;
		looped = -1;
		loopct = -1;
	}
	
	/*----- Getters -----*/
	
	public int getTranspose(){return transpose;}
	public int getIndex(){return ch_idx;}
	public int getCurrentPosition(){return pos;}
	public int getVar(){return var;}
	public boolean initFlag(){return init_flag;}
	public boolean endFlag(){return term_flag;}
	public int getErrorAddress(){return err_addr;}
	public int getErrorLayer(){return err_layer;}
	public int getCurrentProgram(){return program;}
	public byte getPitchBend(){return pitchbend;}
	public byte getEffectLevel(){return eff;}
	public byte getVibratoLevel(){return vibrato;}
	public byte getPan(){return pan;}
	public byte getVolume(){return volume;}
	public byte getPriority(){return priority;}
	public int getLoopStart(){return loopst;}
	public int getLoopEnd(){return looped;}
	public int getLoopCount(){return loopct;}
	public boolean loopEnabled(){return loop_enabled;}
	
	/*----- Setters -----*/
	
	public void setLoopEnabled(boolean b){loop_enabled = b;}
	public void setVar(int value){var = value;}
	
	public void setCommandSource(NUSALSeqCommandSource src){
		source = src;
		for(int i = 0; i < 4; i++) {
			if(layers[i] != null) layers[i].setCommandSource(source);
		}
	}
	
	public void setListener(SequenceController listener){
		target = listener;
		for(int i = 0; i < 4; i++){
			if(layers[i] != null) layers[i].setListener(target);
		}
	}
	
	public void clearTerminatorFlags(){
		term_flag = false;
		//Layers work different
	}
	
	public void setTranspose(int semis){transpose = semis;}
	
	public void setPitchbend(byte val, int time){
		pitchbend = val;
		if(time > 0) wait_remain = NUSALSeq.scaleTicks(time);
		
		if(target != null){
			//Scale to MIDI standard (14 bit unsigned)
			double r = (double)val/127.0;
			double v = r * ((double)0x1FFF);
			v += 0x2000;
			short mpb = (short)(Math.round(v));
			target.setPitchWheel(ch_idx, mpb);
		}
	}
	
	public void setEffectsLevel(byte val){
		eff = val;
		if(target != null){
			//NRPN
			target.addNRPNEvent(ch_idx, NUSALSeq.EFFECT_ID_REVERB, (int)val, true);
		}
	}
	
	public void setVibrato(byte val, int time){
		vibrato = val;
		if(time > 0) wait_remain = NUSALSeq.scaleTicks(time);
		
		if(target != null){
			//Use mod wheel
			//Scale to MIDI standard
			//(I'm treating both unsigned, though I do not know if this is right)
			double r = (double)val/255.0;
			double v = r * ((double)0x3FFF);
			short midval = (short)(Math.round(v));
			target.setModWheel(ch_idx, midval);
		}
	}
	
	public void setPan(byte val, int time){
		pan = val;
		if(time > 0) wait_remain = NUSALSeq.scaleTicks(time);
		
		if(target != null){
			byte mpan = pan;
			if(mpan == 0x3f) mpan = 0x40;
			target.setChannelPan(ch_idx, mpan);
		}
	}
	
	public void setVolume(byte val, int time){
		volume = val;
		if(time > 0) wait_remain = NUSALSeq.scaleTicks(time);
		
		if(target != null){
			target.setChannelVolume(ch_idx, volume);
		}
	}
	
	public void setPriority(byte val){
		priority = val;
		if(target != null){
			target.setChannelPriority(ch_idx, priority);
		}
	}
	
	public boolean setWait(int ticks){
		wait_remain = NUSALSeq.scaleTicks(ticks);
		return true;
	}
	
	/*----- Actions -----*/
	
	public boolean jumpTo(int position, boolean push_return){
		if(push_return) return_stack.push(pos+3);
		pos = position;
		term_flag = false;
		return true;
	}
	
	public boolean returnFromCall(){
		if(return_stack.isEmpty()) return false;
		pos = return_stack.pop();
		return true;
	}
	
	public boolean pointLayerTo(int layer_idx, int position){
		if(layer_idx < 0 || layer_idx > 3) return false;
		return layers[layer_idx].jumpTo(position, false);
	}
	
	public boolean changeProgram(int program_idx){
		program = program_idx;
		if(target != null){
			target.setProgram(ch_idx, program);
		}
		return true;
	}
	
	public void signalChannelInit(){
		init_flag = true;
	}
	
	public void signalLoopStart(int loopcount){
		loopst = pos;
		loopct = loopcount;
		if(target != null){
			target.addNRPNEvent(ch_idx, NUSALSeq.MIDI_NRPN_ID_LOOPSTART, loopct, true);
		}
	}
	
	public void signalLoopEnd(){
		looped = pos;
		if(loop_enabled && loopst >= 0){
			//Loop
			if(lcounter++ < loopct) pos = loopst;
		}
		
		if(target != null){
			target.addNRPNEvent(ch_idx, NUSALSeq.MIDI_NRPN_ID_LOOPEND, 0, true);
		}
	}
	
	public void signalReadEnd(){
		//Seems to behave like return if there's a return address?
		if(!return_stack.isEmpty()){
			pos = return_stack.pop();
		}
		else term_flag = true;
	}
	
	/*----- Playback -----*/
	
	public boolean nextTick(){
		if(err_addr >= 0) return false;
		if(term_flag) return true;
		
		//Itself
		if(wait_remain > 0) wait_remain--;
		while(wait_remain <= 0 && !term_flag){
			if(source == null) return false;
			NUSALSeqCommand cmd = source.getCommandAt(pos);
			if(cmd == null) return false;
			int mypos = pos;
			if(!cmd.doCommand(this)){
				err_addr = mypos;
				//bad_cmd = cmd;
				err_layer = -1;
				return false;
			}
			if(pos == mypos){
				//The command wasn't a jump, so advance manually.
				pos += cmd.getSizeInBytes();
			}
		}
		
		//Its layers
		for(int i = 0; i < 4; i++){
			if(layers[i] != null){
				if(!layers[i].isActive()) continue;
				if(!layers[i].nextTick()){
					err_addr = layers[i].getErrorAddress();
					//bad_cmd = layers[i].getErrorCommand();
					err_layer = i;
					return false;
				}
			}
		}
		
		return true;
	}
	
}
