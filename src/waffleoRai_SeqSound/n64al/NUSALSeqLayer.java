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
		//TODO
		return 0;
	}
	
	public int getVarP(){
		//TODO
		return 0;
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
		//TODO
	}
	
	public void setShortVel(byte value){
		//TODO
	}
	
	public void setShortVelFromTable(int idx){
		//TODO
	}
	
	public void setShortGate(byte value){
		//TODO
	}
	
	public void setShortGateFromTable(int idx){
		//TODO
	}
	
	public void setShortDelay(int value){
		//TODO
	}
	
	public void setLegato(boolean b){
		//TODO
	}
	
	public void changeProgram(int idx){
		//TODO
	}
	
	public void setPortamento(int mode, int target, int time){
		//TODO
	}
	
	public void setPortamentoOff(){
		//TODO
	}
	
	public void setPan(byte pan){
		//TODO
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
	}
	
	public boolean playNote(byte note, byte vel, byte gate, int time){
		//Adjust note to MIDI standard
		int notei = Byte.toUnsignedInt(note);
		notei += channel.getTranspose();
		notei += transpose;
		notei += 0x15;
		
		//I think these layers are monophonic
		if(noteon) noteOff();
		
		this.note = (byte)notei;
		velocity = vel;
		last_gate = gate;
		last_time = time;
		time_remain = NUSALSeq.scaleTicks(time);
		noteon = true;
		
		if(target != null){
			target.noteOn(channel.getIndex(), this.note, velocity);
		}
		
		return true;
	}
	
	public boolean playNote(byte note, byte vel, int time){
		return playNote(note, vel, last_gate, time);
	}
	
	public boolean playNote(byte note, byte vel, byte gate){
		return playNote(note, vel, gate, last_time);
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
			if(cmd == null) return false;
			int mypos = nowpos;
			if(!cmd.doCommand(this)){
				err_addr = mypos;
				//bad_cmd = cmd;
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
