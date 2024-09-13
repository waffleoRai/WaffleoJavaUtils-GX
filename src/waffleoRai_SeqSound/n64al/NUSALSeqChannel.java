package waffleoRai_SeqSound.n64al;

import java.util.Deque;
import java.util.LinkedList;

import waffleoRai_SeqSound.n64al.cmd.STSResult;
import waffleoRai_SoundSynth.SequenceController;
import waffleoRai_Threads.SyncedInt;

public class NUSALSeqChannel {
	
	public static final int LAYER_COUNT = NUSALSeq.MAX_LAYERS_PER_CHANNEL;
	public static final int MAX_LAYER_IDX = LAYER_COUNT - 1;
	
	/*----- Instance Variables -----*/

	private int pos;
	private Deque<Integer> return_stack;
	private int ch_idx;
	
	private SyncedInt q;
	private SyncedInt p;
	private SyncedInt[] seqIO;
	private int dyntable_addr = -1;
	private int filter_addr = -1;
	
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
	
	private NUSALSeqCommandMap ch_cmds;
	
	private NUSALSeq parent_seq;
	private NUSALSeqLayer[] layers;
	
	private int bank;
	private int program;
	
	private byte pitchbend;
	private byte eff;
	private byte vibrato;
	private byte pan;
	private byte volume;
	private byte expression;
	private byte priority;
	
	private byte filter_gain;
	
	private boolean loop_enabled;
	private int lcounter; //Keeps track of how many times loop has occured
	private int loopst;
	private int looped;
	private int loopct;
	
	private int max_lyr_used = -1;
	
	/*----- Initialization -----*/
	
	public NUSALSeqChannel(int index, NUSALSeq parent){
		//pos = startpos;
		ch_idx = index;
		//pending_notes = new TreeMap<Byte, Integer>();
		layers = new NUSALSeqLayer[LAYER_COUNT];
		return_stack = new LinkedList<Integer>();
		parent_seq = parent;
		seqIO = new SyncedInt[8];
		for(int i = 0; i < 8; i++) seqIO[i] = new SyncedInt(0);
		q = new SyncedInt(0);
		p = new SyncedInt(0);
		reset();
	}
	
	public void reset(){
		return_stack.clear();
		wait_remain = 0;
		term_flag = true;
		//err_flag = false;
		err_addr = -1; err_layer = -1;
		for(int i = 0; i < LAYER_COUNT; i++){
			layers[i] = new NUSALSeqLayer(this, i);
		}
		for(int i = 0; i < 8; i++) seqIO[i].set(0);
		init_flag = false;
		lcounter = 0;
		pos = -1;
		
		bank = 0;
		program = 0;
		pitchbend = 0;
		eff = 0;
		vibrato = 0;
		pan = 0x40;
		volume = 0x7f;
		expression = 0x7f;
		filter_gain = 0x00;
		transpose = 0;
		
		dyntable_addr = -1;
		filter_addr = -1;
		
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
	public int getNumberLayersUsed(){return max_lyr_used;}
	
	public int getVarQ(){
		return q.get();
	}
	
	public int getVarP(){
		return p.get();
	}
	
	public int getSeqIOValue(int idx){
		return seqIO[idx].get();
	}
	
	public int getDynTableAddress(){
		return dyntable_addr;
	}
	
	public NUSALSeq getParent(){
		return parent_seq;
	}
	
	public int getLayerStatus(int idx){
		//Returns values used in the testlayer command
		if(layers[idx] == null) return -1;
		if(layers[idx].isActive()) return 0;
		return 1;
	}
	
	public NUSALSeqCommandMap getCommandTickMap(){
		return ch_cmds;
	}
	
	public NUSALSeqCommandMap getVoiceCommandTickMap(int layer){
		if(layers[layer] == null) return null;
		return layers[layer].getCommandTickMap();
	}
	
	/*----- Setters -----*/

	public void resetChannel(){
		reset();
	}
	
	public void setLoopEnabled(boolean b){loop_enabled = b;}
	public void setVar(int value){var = value;}
	
	public void setSeqIOValue(int idx, int value){
		seqIO[idx].set(value);
	}
	
	public void setQ(byte value){q.set((int)value);}
	public void setP(short value){p.set(Short.toUnsignedInt(value));}
	
	public void setDynTable(int addr){
		dyntable_addr = addr;
	}
	
	public void setFilterAddress(int addr){
		filter_addr = addr;
	}
	
	public void clearFilter(){
		filter_addr = 0;
	}
	
	public void setFilterGain(int value){
		filter_gain = (byte)value;
	}
	
	public void setFreqScale(int value){
		//TODO
	}
	
	public void setCommandSource(NUSALSeqCommandSource src){
		source = src;
		for(int i = 0; i < LAYER_COUNT; i++) {
			if(layers[i] != null) layers[i].setCommandSource(source);
		}
	}
	
	public void setListener(SequenceController listener){
		target = listener;
		for(int i = 0; i < LAYER_COUNT; i++){
			if(layers[i] != null) layers[i].setListener(target);
		}
	}
	
	public void clearTerminatorFlags(){
		term_flag = false;
		//Layers work different
	}
	
	public void setTranspose(int semis){
		transpose = semis;
		
		if(NUSALSeq.dbg_w_mode){
			parent_seq.dbgtt_writeln(ch_idx, "ctp " + transpose);
		}
	}
	
	public void setPitchbend(int val){
		pitchbend = (byte)val;
		//if(time > 0) wait_remain = NUSALSeq.scaleTicks(time);
		
		if(target != null){
			//Scale to MIDI standard (14 bit unsigned)
			double r = (double)val/127.0;
			double v = r * ((double)0x1FFF);
			v += 0x2000;
			short mpb = (short)(Math.round(v));
			target.setPitchWheel(ch_idx, mpb);
		}
	}
	
	public void setPitchbendAlt(int val){
		//TODO
	}
	
	public void setEffectsLevel(byte val){
		eff = val;
		if(target != null){
			//NRPN
			//target.addNRPNEvent(ch_idx, NUSALSeq.EFFECT_ID_REVERB, (int)val, true);
			//target.setEffect1(ch_idx, val);
			target.setReverbSend(ch_idx, val);
		}
	}
	
	//Deprecate
	public void setVibrato(byte val){
		vibrato = val;
		//if(time > 0) wait_remain = NUSALSeq.scaleTicks(time);
		
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
	
	public void setVibratoFrequency(int val){
		//TODO
	}
	
	public void setVibratoFrequencyEnvelope(int start, int target, int time){
		//TODO
	}
	
	public void setVibratoDepth(int val){
		//TODO
	}
	
	public void setVibratoDepthEnvelope(int start, int target, int time){
		//TODO
	}
	
	public void setVibratoDelay(int val){
		//TODO
	}
	
	public void setPanMix(int val){
		//TODO
	}
	
	public void setPan(byte val){
		pan = val;
		//if(time > 0) wait_remain = NUSALSeq.scaleTicks(time);
		
		if(target != null){
			byte mpan = pan;
			if(mpan == 0x3f) mpan = 0x40;
			target.setChannelPan(ch_idx, mpan);
		}
	}
	
	public void setVolume(byte val){
		volume = val;
		//if(time > 0) wait_remain = NUSALSeq.scaleTicks(time);
		
		if(target != null){
			target.setChannelVolume(ch_idx, volume);
		}
	}
	
	public void setExpression(byte val){
		expression = val;
		if(target != null){
			target.setChannelExpression(ch_idx, expression);
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
	
	public void setVelocityVariance(int val){
		//TODO
	}
	
	public void setGateVariance(int val){
		//TODO
	}
	
	public void setShortNotesMode(boolean b){
		//TODO
	}
	
	public void setSustain(int val){
		//TODO
	}
	
	public void setRelease(int val){
		//TODO
	}
	
	public void setEnvelopeAddress(int addr){
		//TODO
	}
	
	public void mapCommandToTick(int tick, NUSALSeqCommand cmd){
		if(ch_cmds == null) ch_cmds = new NUSALSeqCommandMap();
		ch_cmds.addCommand(tick, cmd);
	}
	
	public void mapVoiceCommandToTick(int layer, int tick, NUSALSeqCommand cmd){
		if(layers[layer] == null) return;
		layers[layer].mapCommandToTick(tick, cmd);
	}
	
	public void clearSavedCommands(){
		if(ch_cmds != null) ch_cmds.clearCommands();
		for(int i = 0; i < LAYER_COUNT; i++){
			if(layers[i] != null) layers[i].clearSavedCommands();
		}
	}
	
	/*----- Actions -----*/
	
	public STSResult storeToSelf(int addr, byte value){
		if(source == null) return STSResult.FAIL;
		return source.storeToSelf(addr, value);
	}
	
	public STSResult storePToSelf(int addr, short value){
		if(source == null) return STSResult.FAIL;
		return source.storePToSelf(addr, value);
	}
	
	public boolean jumpTo(int position, boolean push_return){
		if(push_return) return_stack.push(pos+3);
		pos = position;
		term_flag = false;
		return true;
	}
	
	public void breakLoop(){
		if(!return_stack.isEmpty()){
			return_stack.pop();
		}
	}
	
	public boolean returnFromCall(){
		if(return_stack.isEmpty()) return false;
		pos = return_stack.pop();
		return true;
	}
	
	public boolean pointLayerTo(int layer_idx, int position){
		if(layer_idx < 0 || layer_idx > MAX_LAYER_IDX) return false;
		if(layer_idx > max_lyr_used) max_lyr_used = layer_idx;
		return layers[layer_idx].jumpTo(position, false);
	}
	
	public boolean stopLayer(int layer_idx){
		if(layers[layer_idx] != null){
			layers[layer_idx].signalReadEnd();
			return true;
		}
		return false;
	}
	
	public boolean pointChannelTo(int ch_idx, int addr){
		return parent_seq.pointChannelTo(ch_idx, addr);
	}
	
	public boolean changeBank(int bank_idx){
		bank = bank_idx;
		if(target != null){
			target.setProgram(ch_idx, bank, program);
		}
		return true;
	}
	
	public boolean changeProgram(int bank_idx, int prog_idx){
		bank = bank_idx;
		program = prog_idx;
		if(target != null){
			target.setProgram(ch_idx, bank, program);
		}
		return true;
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
	
	public void signalUnkEvent(byte cmdbyte, int value){
		if(target == null) return;
		int nrpn_idx = NUSALSeq.MIDI_NRPN_ID_GENERAL_HI | Byte.toUnsignedInt(cmdbyte);
		target.addNRPNEvent(ch_idx, nrpn_idx, value, true);
	}

	public boolean stopChannel(int idx){
		parent_seq.stopChannel(idx);
		return true;
	}
	
	/*----- Playback -----*/
	
	public boolean nextTick(boolean savecmd, int tick){
		if(err_addr >= 0) return false;
		if(term_flag) return true;
		
		//Itself
		if(wait_remain > 0) wait_remain--;
		while(wait_remain <= 0 && !term_flag){
			if(source == null) return false;
			NUSALSeqCommand cmd = source.getCommandAt(pos);
			//if(this.ch_idx == 15) System.err.println("Doing command: " + cmd.toMMLCommand());
			if(cmd == null) {
				err_addr = pos;
				err_layer = -1;
				System.err.println("NUSALSeqChannel.nextTick || Command @ 0x" + Integer.toHexString(err_addr) + " -- not found.");
				return false;
			}
			int mypos = pos;
			if(!cmd.doCommand(this)){
				err_addr = mypos;
				//bad_cmd = cmd;
				err_layer = -1;
				System.err.println("NUSALSeqChannel.nextTick || Channel Command @ 0x" + Integer.toHexString(err_addr) + " -- " + cmd.toMMLCommand(NUSALSeq.SYNTAX_SET_ZEQER) + " returned error.");
				return false;
			}
			if(pos == mypos){
				//The command wasn't a jump, so advance manually.
				pos += cmd.getSizeInBytes();
			}
			if(savecmd) mapCommandToTick(tick, cmd);
		}
		
		//Its layers
		for(int i = 0; i < LAYER_COUNT; i++){
			if(layers[i] != null){
				if(!layers[i].isActive()) continue;
				if(!layers[i].nextTick(savecmd, tick)){
					err_addr = layers[i].getErrorAddress();
					//bad_cmd = layers[i].getErrorCommand();
					err_layer = i;
					System.err.println("NUSALSeqChannel.nextTick || Layer Command @ 0x" + Integer.toHexString(err_addr) + " -- returned error.");
					return false;
				}
			}
		}
		
		return true;
	}
	
}
