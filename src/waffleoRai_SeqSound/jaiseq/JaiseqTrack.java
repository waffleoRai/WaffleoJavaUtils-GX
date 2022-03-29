package waffleoRai_SeqSound.jaiseq;

import java.util.Arrays;
import java.util.LinkedList;

import waffleoRai_SeqSound.jaiseq.cmd.JaiseqPendingTimedEvent;
import waffleoRai_SoundSynth.SequenceController;

public class JaiseqTrack {
	
	/*----- Constants -----*/
	
	/*----- Instance Variables -----*/
	
	private int ch_idx;
	private boolean block_back_jumps = true;
	
	private int pos;
	private int wait_pending;
	private boolean c_flag;
	private LinkedList<Integer> stack;
	private boolean end_flag = true;
	
	private byte[] notes;
	private LinkedList<JaiseqPendingTimedEvent> time_events;
	
	private double vol;
	private double pan;
	private double pitchbend;
	private double v_freq;
	private double v_depth;
	private double reverb;
	
	private byte last_pan;
	private byte last_vol;
	
	private int bank_idx;
	private int prog_idx;
	
	private JaiseqReader src;
	private SequenceController targ;
	
	private boolean error_flag = false;
	private boolean warning_flag = false;
	private int error_addr = -1;
	private int warning_addr = -1;
	
	/*----- Init -----*/
	
	public JaiseqTrack(int channel, JaiseqReader source){
		ch_idx = channel;
		src = source;
		notes = new byte[8];
		stack = new LinkedList<Integer>();
		time_events = new LinkedList<JaiseqPendingTimedEvent>();
		reset();
	}
	
	public void reset(){
		pos = -1;
		wait_pending = 0;
		c_flag = false;
		Arrays.fill(notes, (byte)-1);
		vol = 1.0;
		pan = 0.0;
		last_pan = 0x40;
		last_vol = 0x7f;
		pitchbend = 0.0;
		v_freq = 0.0;
		v_depth = 0.0;
		reverb = 0.0;
		bank_idx = 0;
		prog_idx = 0;
		error_flag = false;
		error_addr = -1;
		warning_flag = false;
		warning_addr = -1;
		end_flag = true;
		stack.clear();
		time_events.clear();
	}
	
	/*----- Getters -----*/
	
	public boolean isOpen(){return !end_flag;}
	public boolean errorFlag(){return error_flag;}
	public int getErrorAddress(){return error_addr;}
	public boolean warningFlag(){return warning_flag;}
	public int getWarningAddress(){return warning_addr;}
	public boolean getConditionFlag(){return c_flag;}
	public boolean backjumpsBlocked(){return block_back_jumps;}
	
	/*----- Setters -----*/
	
	public void setConditionFlag(boolean b){c_flag = b;}
	public void setBackjumpsBlock(boolean b){block_back_jumps = b;}
	public void setTarget(SequenceController target){targ = target;}
	
	/*----- Playback -----*/
	
	public void onTick(){
		if(end_flag || error_flag) return;
		
		//Handle pending timed events
		int ecount = time_events.size();
		for(int i = 0; i < ecount; i++){
			JaiseqPendingTimedEvent event = time_events.pop();
			event.onTick(this);
			if(event.hasRemaining()) time_events.add(event);
		}
		
		if(--wait_pending >= 1) return;
		
		//do all commands for tick
		while(!end_flag && !error_flag && wait_pending <= 0){
			//Get next command.
			JaiseqCommand cmd = src.getCommandAt(pos);
			//System.err.println("Debug -- track " + ch_idx + " @ pos 0x" + Integer.toHexString(pos));
			if(cmd == null){
				error_flag = true;
				error_addr = pos;
				//System.exit(1);
				return;
			}
			
			int cpos = pos;
			pos += cmd.getSize();
			if(!cmd.doAction(this)){
				warning_flag = true;
				warning_addr = cpos;
			}
		}
	}
	
	/*----- Command Interface -----*/
	
	public boolean openAt(int addr){
		if(addr < 0) return false;
		if(!end_flag) return false; //Already open
		pos = addr;
		end_flag = false;
		return true;
	}
	
	public boolean noteOn(byte note, int slot, byte vel){
		if(slot < 0 || slot >= 8){
			error_flag = true;
			error_addr = pos;
			return false;
		}
		if(notes[slot] >= 0) noteOff(slot);
		notes[slot] = note;
		
		if(targ != null){
			targ.noteOn(ch_idx, note, vel);
		}
		
		return true;
	}
	
	public boolean noteOff(int slot){
		if(slot < 0 || slot >= 8){
			error_flag = true;
			error_addr = pos;
			return false;
		}
		
		if(notes[slot] < 0){
			//Already off.
			return false;
		}
		
		if(targ != null){
			targ.noteOff(ch_idx, notes[slot]);
		}
		notes[slot] = -1;
		
		return true;
	}
	
	public void jumpTo(int addr){
		if(block_back_jumps){
			if(addr <= pos){
				//signalEndTrack();
				return;
			}
		}
		pos = addr;
	}
	
	public void call(int addr){
		stack.push(pos);
		pos = addr;
	}
	
	public boolean returnFromSubroutine(){
		if(stack.isEmpty()) return false;
		pos = stack.pop();
		return true;
	}
	
	public void signalEndTrack(){
		end_flag = true;
		//I guess the controller doesn't have a track end signal?
	}
	
	public void setWait(int ticks){
		wait_pending = ticks;
	}
	
	public void setVolume(double val){
		setVolume(val, -1);
	}
	
	public void setPan(double val){
		setPan(val, -1);
	}
	
	public void setReverb(double val){
		setReverb(val, -1);
	}
	
	public void setPitchbend(double val){
		setPitchbend(val, -1);
	}
	
	public void setVolume(double val, int time){
		//TODO What does time do?
		if(time <= 0){
			if(vol == val) return;
			vol = val;
			if(targ != null){
				byte v = Jaiseq.toMIDIVolume(vol);
				if(v == last_vol) return; //No change in target
				targ.setChannelVolume(ch_idx, v);
				last_vol = v;
			}
		}
		else{
			JaiseqPendingTimedEvent ev = new JaiseqPendingTimedEvent(vol, val, time){
				public void onTick(JaiseqTrack track){
					super.onTick(track);
					if(Jaiseq.PERFTIME_MODE_LINEARINC){
						if(remaining_ticks <= 0) setVolume(target, 0);
						else setVolume(vol+change_per_tick,0);
					}
					else{
						if(remaining_ticks <= 0) setVolume(start,0);
					}
				}
			};
			if(!Jaiseq.PERFTIME_MODE_LINEARINC) setVolume(val,0);
			time_events.add(ev);
		}
	}
	
	public void setPan(double val, int time){
		//TODO What does time do?
		if(time <= 0){
			if(pan == val) return; //No need to make a change.
			pan = val;
			if(targ != null){
				byte p = Jaiseq.toMIDIPan(pan);
				if(p == last_pan) return; //No change in target.
				targ.setChannelPan(ch_idx, p);
				last_pan = p;
			}	
		}
		else{
			JaiseqPendingTimedEvent ev = new JaiseqPendingTimedEvent(pan, val, time){
				public void onTick(JaiseqTrack track){
					super.onTick(track);
					if(Jaiseq.PERFTIME_MODE_LINEARINC){
						if(remaining_ticks <= 0) setPan(target, 0);
						else setPan(pan+change_per_tick,0);
					}
					else{
						if(remaining_ticks <= 0) setPan(start,0);
					}
				}
			};
			if(!Jaiseq.PERFTIME_MODE_LINEARINC) setPan(val,0);
			time_events.add(ev);
		}
	}
	
	public void setReverb(double val, int time){
		//TODO What does time do?
		if(time <= 0){
			reverb = val;
			if(targ != null){
				byte v = Jaiseq.to8BitReverb(reverb);
				targ.setReverbSend(ch_idx, v);
			}	
		}
		else{
			JaiseqPendingTimedEvent ev = new JaiseqPendingTimedEvent(reverb, val, time){
				public void onTick(JaiseqTrack track){
					super.onTick(track);
					if(Jaiseq.PERFTIME_MODE_LINEARINC){
						setReverb(reverb+change_per_tick,0);
					}
					else{
						if(remaining_ticks <= 0) setReverb(start,0);
					}
				}
			};
			if(!Jaiseq.PERFTIME_MODE_LINEARINC) setReverb(val,0);
			time_events.add(ev);
		}
	}
	
	public void setPitchbend(double val, int time){
		//TODO What does time do?
		if(time <= 0){
			pitchbend = val;
			if(targ != null){
				short pb = Jaiseq.toMIDIPitchbend(pitchbend);
				targ.setPitchWheel(ch_idx, pb); //Expects a MIDI value!!!
				//System.err.println("pitchwheel set: ch " + ch_idx + " to " + String.format("0x%04x", pb));
			}
		}
		else{
			JaiseqPendingTimedEvent ev = new JaiseqPendingTimedEvent(pitchbend, val, time){
				public void onTick(JaiseqTrack track){
					super.onTick(track);
					if(Jaiseq.PERFTIME_MODE_LINEARINC){
						setPitchbend(pitchbend+change_per_tick,0);
					}
					else{
						if(remaining_ticks <= 0) setPitchbend(start,0);
					}
				}
			};
			if(!Jaiseq.PERFTIME_MODE_LINEARINC) setPitchbend(val,0);
			time_events.add(ev);
		}
	}
	
	public void setBank(int val){
		//System.err.println("JaiseqTrack.setBank || Setting track "  + ch_idx + " bank to: " + val);
		bank_idx = val;
		/*if(targ != null){
			if(prog_idx > 127){
				targ.setProgram(ch_idx, bank_idx, 127);
			}
			else targ.setProgram(ch_idx, bank_idx, prog_idx);
		}*/
	}
	
	public void setProgram(int val){
		//System.err.println("JaiseqTrack.setProgram || Setting track "  + ch_idx + " program to: " + val);
		prog_idx = val;
		if(targ != null){
			if(prog_idx > 127){
				targ.setProgram(ch_idx, bank_idx, 127);
			}
			else targ.setProgram(ch_idx, bank_idx, prog_idx);
		}
	}
	
	public void setVibFreq(double val){
		v_freq = val;
		if(targ != null){
			targ.setVibratoSpeed(ch_idx, v_freq);
		}
	}
	
	public void setVibDepth(double val){
		v_depth = val;
		if(targ != null){
			byte v = (byte)Math.round(v_depth * 127.0);
			targ.setVibratoAmount(ch_idx, v);
		}
	}
	
	/*----- Utilities -----*/
	

}
