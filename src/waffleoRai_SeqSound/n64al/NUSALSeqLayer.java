package waffleoRai_SeqSound.n64al;

import java.util.Deque;
import java.util.LinkedList;

import waffleoRai_SoundSynth.SequenceController;

public class NUSALSeqLayer {
	
	private NUSALSeqChannel channel;
	private int my_idx;
	
	private int nowpos;
	private Deque<Integer> return_stack;
	
	private NUSALSeqCommandMap vox_cmds;
	
	private boolean noteon;
	private int transpose;
	private int time_remain;
	private boolean endflag;
	
	private boolean legato;
	private int program = -1;
	private byte pan = 0x40;
	
	//Short notes
	private byte sn_gate = 0;
	private byte sn_vel = 0;
	private int sn_delay = 0;
	
	private int err_addr = -1;
	//private NUSALSeqCommand bad_cmd;
	
	private SequenceController target;
	private NUSALSeqCommandSource source;
	
	private int loop_start = -1;
	private int loop_end = -1;
	private int loop_count = -1;
	
	private byte note; //MIDI value - gets converted on call
	private byte velocity;
	private byte last_gate;
	private int last_time;
	
	public NUSALSeqLayer(NUSALSeqChannel parent, int layer_idx){
		channel = parent;
		my_idx = layer_idx;
		return_stack = new LinkedList<Integer>();
		nowpos = 0;
		endflag = true;
		
		//var_p = new SyncedInt(0);
		//var_q = new SyncedInt(0);
	}
	
	public int getChannelIndex(){
		return channel.getIndex();
	}
	
	public int getLayerIndex(){
		return my_idx;
	}
	
	public int getChannelVar(){
		return channel.getVar();
	}
	
	public int getCurrentPosition(){
		return nowpos;
	}
	
	public int getVarQ(){
		//Not sure if this means layer has its own, or use the sequence's...
		return channel.getVarQ();
	}
	
	public int getVarP(){
		//Not sure if this means layer has its own, or use the sequence's...
		return channel.getVarP();
	}
	
	public int getLoopStart(){return loop_start;}
	public int getLoopEnd(){return loop_end;}
	public int getLoopCount(){return loop_count;}
	
	public boolean isActive(){return !endflag;}
	
	public void clearError(){err_addr = -1;}
	public int getErrorAddress(){return err_addr;}
	//public NUSALSeqCommand getErrorCommand(){return bad_cmd;}
	
	public int getTranspose(){return this.transpose;}
	
	public NUSALSeqCommandMap getCommandTickMap(){
		return vox_cmds;
	}
	
	public void mapCommandToTick(int tick, NUSALSeqCommand cmd){
		if(vox_cmds == null) vox_cmds = new NUSALSeqCommandMap();
		vox_cmds.addCommand(tick, cmd);
	}
	
	public void clearSavedCommands(){
		if(vox_cmds != null) vox_cmds.clearCommands();
	}
	
	public boolean jumpTo(int pos, boolean push_return){
		if(push_return) return_stack.push(nowpos+3);
		nowpos = pos;
		endflag = false;
		return true;
	}
	
	public boolean returnFromCall(){
		if(return_stack.isEmpty()) return false;
		nowpos = return_stack.pop();
		endflag = false;
		return true;
	}
	
	public void breakLoop(){
		//According to Saurean, pops the return stack basically so that if a jump comes next, it doesn't jump there
		//He also says it's only valid on channels, but I'll just put this here in case
		if(return_stack.isEmpty()) return;
		return_stack.pop();
	}
	
	public void setShortVel(byte value){
		sn_vel = value;
	}
	
	public void setShortVelFromTable(int idx){
		sn_vel = channel.getParent().getShortTableVelocityAt(idx);
	}
	
	public void setShortGate(byte value){
		sn_gate = value;
	}
	
	public void setShortGateFromTable(int idx){
		sn_gate = channel.getParent().getShortTableGateAt(idx);
	}
	
	public void setShortDelay(int value){
		sn_delay = value;
	}
	
	public void setLegato(boolean b){
		legato = b;
		if(target != null){
			System.err.println("NUSALSeqLayer.setLegato || WARNING: Setting legato " + b + " for whole channel " + 
					channel.getIndex() + " on behalf of layer " + my_idx);
			target.setControllerValue(
					channel.getIndex(), NUSALSeq.MIDI_CTRLR_ID_LEGATO, legato?127:0, true);
		}
	}
	
	public void changeProgram(int idx){
		program = idx;
		if(target != null){
			System.err.println("NUSALSeqLayer.changeProgram || WARNING: Setting program " + program + " for whole channel " + 
					channel.getIndex() + " on behalf of layer " + my_idx);
			target.setProgram(channel.getIndex(), program);
		}
	}
	
	public void setPortamento(int mode, int trg, int time){
		if(target != null){
			System.err.println("NUSALSeqLayer.setPortamento || WARNING: Setting portamento on for whole channel " + 
					channel.getIndex() + " on behalf of layer " + my_idx);
			target.setPortamentoTime(channel.getIndex(), (short)time);
			target.setPortamentoAmount(channel.getIndex(), (byte)trg);
			target.setPortamentoOn(channel.getIndex(), true);
		}
	}
	
	public void setPortamentoOff(){
		if(target != null){
			System.err.println("NUSALSeqLayer.setPortamento || WARNING: Setting portamento off for whole channel " + 
					channel.getIndex() + " on behalf of layer " + my_idx);
			target.setPortamentoOn(channel.getIndex(), false);
		}
	}
	
	public void setPan(byte val){
		pan = val;
		if(target != null){
			System.err.println("NUSALSeqLayer.changeProgram || WARNING: Setting pan " + pan + " for whole channel " + 
					channel.getIndex() + " on behalf of layer " + my_idx);
			target.setChannelPan(channel.getIndex(), pan);
		}
	}
	
	public void setEnvelopeAddress(int addr){
		//TODO
	}
	
	public void setRelease(int val){
		//TODO
	}
	
	public void drumPanOff(){
		//TODO
	}
	
	public void setPitchbendAlt(int amt){
		//TODO
	}
	
	public void signalLoopStart(int loopcount){
		loop_start = nowpos;
		loop_count = loopcount;
		if(target != null){
			target.addMarkerNote("Loop Start - Count: " + loopcount);
			target.addNRPNEvent(channel.getIndex(), NUSALSeq.MIDI_NRPN_ID_LOOPSTART, loopcount, true);
		}
	}
	
	public void signalLoopEnd(){
		loop_end = nowpos;
		nowpos = loop_start;
		if(target != null){
			target.addMarkerNote("Loop End");
			target.addNRPNEvent(channel.getIndex(), NUSALSeq.MIDI_NRPN_ID_LOOPEND, 0, true);
		}
	}
	
	public void signalReadEnd(){
		//Seems to behave like return if there's something on the stack
		if(!return_stack.isEmpty()){
			nowpos = return_stack.pop();
		}
		else endflag = true;
	}
	
	private void noteOff(){	
		if(target != null){
			target.noteOff(channel.getIndex(), note);
		}
		time_remain = 0;
		
		noteon = false;
		
		if(NUSALSeq.dbg_w_mode){
			channel.getParent().dbgtt_writeln(channel.getIndex(), "noteoff ly" + my_idx);
		}
	}
	
	public boolean playNote(byte note, int vel, int gate, int time){
		//Negative param means use short note
		if(vel < 0) vel = sn_vel;
		if(gate < 0) gate = sn_gate;
		if(time < 0) time = sn_delay;
		
		//Adjust note to MIDI standard
		int notei = Byte.toUnsignedInt(note);
		notei += channel.getTranspose();
		notei += transpose;
		notei += 0x15;
		
		//I think these layers are monophonic
		if(noteon) noteOff();
		
		this.note = (byte)notei;
		velocity = (byte)vel;
		last_gate = (byte)gate;
		last_time = time;
		time_remain = NUSALSeq.scaleTicks(time);
		noteon = true;
		
		if(target != null){
			target.noteOn(channel.getIndex(), this.note, velocity);
		}
		
		if(NUSALSeq.dbg_w_mode){
			String notestr = note + "->" + notei;
			channel.getParent().dbgtt_writeln(channel.getIndex(), 
					"noteon " + notestr + " " + time + " " + vel + " " + gate + " ly" + my_idx);
		}
		
		return true;
	}
	
	public boolean playNoteNDV(byte note, int vel, int time){
		return playNote(note, vel, last_gate, time);
	}
	
	public boolean playNoteNVG(byte note, int vel, int gate){
		return playNote(note, vel, gate, last_time);
	}
	
	public boolean playNote(byte note){
		return playNote(note, sn_vel, sn_gate, sn_delay);
	}
	
	public boolean rest(int time){
		if(noteon) noteOff();
		time_remain = NUSALSeq.scaleTicks(time);
		return true;
	}
	
	public void setWait(int ticks){
		rest(ticks);
	}
	
	public void setTranspose(int semis){
		transpose = semis;
	}
	
	public void setCommandSource(NUSALSeqCommandSource src){
		source = src;
	}
	
	public void setListener(SequenceController listener){
		target = listener;
	}

	public boolean nextTick(boolean savecmd, int tick){
		if(err_addr >= 0) return false;
		if(endflag) return true;
		
		if(time_remain > 0) time_remain--;
		while(time_remain <= 0 && !endflag){
			//Next command(s)
			if(noteon) noteOff();
			if(source == null) return false;
			NUSALSeqCommand cmd = source.getCommandAt(nowpos);
			if(cmd == null){
				err_addr = nowpos;
				System.err.println("NUSALSeqLayer.nextTick || Command @ 0x" + Integer.toHexString(err_addr) + " -- not found.");
				return false;
			}
			int mypos = nowpos;
			if(!cmd.doCommand(this)){
				err_addr = mypos;
				//bad_cmd = cmd;
				System.err.println("NUSALSeqLayer.nextTick || Command @ 0x" + Integer.toHexString(err_addr) + " -- " + cmd.toMMLCommand(NUSALSeq.SYNTAX_SET_ZEQER) + " returned error.");
				return false;
			}
			if(nowpos == mypos){
				//The command wasn't a jump, so advance manually.
				nowpos += cmd.getSizeInBytes();
			}
			if(savecmd) mapCommandToTick(tick, cmd);
		}
		
		return true;
	}
	
}
