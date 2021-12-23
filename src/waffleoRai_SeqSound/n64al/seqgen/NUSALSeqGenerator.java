package waffleoRai_SeqSound.n64al.seqgen;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.sound.midi.Sequence;

import waffleoRai_SeqSound.MIDI;
import waffleoRai_SeqSound.MIDIInterpreter;
import waffleoRai_SeqSound.n64al.NUSALSeq;
import waffleoRai_SeqSound.n64al.NUSALSeqCmdType;
import waffleoRai_SeqSound.n64al.NUSALSeqCommand;
import waffleoRai_SoundSynth.SequenceController;

public class NUSALSeqGenerator implements SequenceController{
	
	/*
	 * Effects1 or Reverb NRPN interpreted as reverb/effect
	 * Effects2, vibrato NRPN, or mod wheel interpreted as vibrato 
	 * Priority NRPN can be read as priority
	 * LoopStart NRPN interpreted as loop point
	 */
	
	/*----- Constants -----*/
	
	protected static final int[] DEFO_CH_PRIS = {10, 11, 12, 9, 8, 15, 13, 4,
												  0, 1, 2, 3, 4, 5, 6, 7};
	
	/*----- Instance Variables -----*/
	
	private boolean enable_seqProgramChange; //Disabled by default.
	
	private int tick;
	
	private int loop_tick; //Loop start.
	//private boolean mastervol_set; //Was master vol ever set by seq?
	private int output_pb_range = 700; //Cents
	
	private Map<Integer, List<BuilderCommand>> seqcmds;
	private NUSALSeqBuilderChannel[] channels;
	
	private int[][] delay_tracks; //[n][0] is channel it is repeating. [n][1] is delay in ticks.
	private int[] ch_pri; //Starting priorities for each channel if not overridden by seq
	private boolean[][] ch_enable; //First col is enable, second col is whether a note-on has been seen
	private int[] input_pb_range; //Cents. Per channel.
	private PhraseBuilder phrase_builder;
	
	private Map<Integer, Integer> alt_entries; //Key = trigger, val = tick
	private int loop_end; //Only used in case of outro
	private int outro_trigger; //Variable value used to trigger outro
	
	/*----- Inner Classes -----*/
	
	/*----- Init -----*/
	
	public NUSALSeqGenerator(){
		seqcmds = new TreeMap<Integer, List<BuilderCommand>>();
		channels = new NUSALSeqBuilderChannel[16];
		for(int i = 0; i < 16; i++){
			channels[i] = new NUSALSeqBuilderChannel(i);
		}
		loop_tick = -1;
		delay_tracks = new int[16][2];
		ch_pri = new int[16];
		ch_enable = new boolean[16][2];
		input_pb_range = new int[16];
		for(int i = 0; i < 16; i++){
			delay_tracks[i][0] = -1;
			//ch_pri[i] = -1;
			ch_enable[i][0] = true;
			ch_enable[i][1] = false;
			input_pb_range[i] = 200;
		}
		phrase_builder = new PhraseBuilder();
		refreshPriorities();
		
		loop_end = -1;
		alt_entries = new TreeMap<Integer, Integer>();
		//ch_used = new boolean[16];
	}
	
	private void refreshPriorities(){
		int p = 2;
		for(int i = 0; i < 16; i++){
			int ch = DEFO_CH_PRIS[i];
			if(ch_enable[ch][0]) ch_pri[ch] = p++;
			else ch_pri[ch] = -1;
		}
	}
	
	/*----- Getters -----*/
	
	public boolean channelEnabled(int idx){
		if(idx < 0 || idx >= 16) return false;
		return ch_enable[idx][0];
	}
	
	public int getCurrentLengthInTicks(){
		//TODO
		return 0;
	}
	
	/*----- Setters -----*/
	
	public void setLoopPoint(int tick){
		loop_tick = tick;
		if(loop_tick >= 0) phrase_builder.addSegmentBorder(loop_tick);
		else{
			phrase_builder.clearSegmentBorders();
			if(loop_end >= 0) phrase_builder.addSegmentBorder(loop_end);
			for(Integer val : alt_entries.values()) phrase_builder.addSegmentBorder(val);
		}
	}
	
	public void setDelayTrack(int delay_track, int ref_track, int delay_ticks){
		delay_tracks[delay_track][0] = ref_track;
		delay_tracks[delay_track][1] = delay_ticks;
	}
	
	public void clearDelayTrack(int delay_track){
		delay_tracks[delay_track][0] = -1;
		delay_tracks[delay_track][1] = 0;
	}
	
	private void pushPriorities(int hold_idx){
		int i = hold_idx;
		int v = ch_pri[i];
		for(int j = 0; j < 16; j++){
			if(j == i) continue;
			if(ch_pri[j] == v){
				//Increment this one.
				ch_pri[j]++;
			}
		}
		
		for(int j = 0; j < 16; j++){
			if(j == i) continue;
			pushPriorities(j);
		}
	}
	
	public void increaseChannelPriority(int ch_idx){
		if(ch_idx < 0 || ch_idx >= 16) return;
		if(ch_enable[ch_idx][0]){
			if(ch_pri[ch_idx] <= 2) ch_pri[ch_idx] = 2;
			else{
				ch_pri[ch_idx]--;
				pushPriorities(ch_idx);
			}
		}
	}
	
	public void decreaseChannelPriority(int ch_idx){
		if(ch_idx < 0 || ch_idx >= 16) return;
		if(ch_enable[ch_idx][0]){
			if(ch_pri[ch_idx] <= 2) ch_pri[ch_idx] = 2;
			else{
				ch_pri[ch_idx]++;
				pushPriorities(ch_idx);
			}
		}
	}
	
	public void setChannelBasePriority(int ch_idx, int val){
		//This method DOES NOT push other priorities!
		//It also DOES NOT value check. It is for direct access.
		if(ch_idx < 0 || ch_idx >= 16) return;
		ch_pri[ch_idx] = val;
	}
	
	public void setNoteMatchTimeLeeway(int ticks){
		if(ticks < 0) ticks = 0;
		phrase_builder.setTimeLeeway(ticks);
	}
	
	public void setNoteMatchVelocityLeeway(int leeway){
		if(leeway < 0) leeway = 0;
		if(leeway > 127) leeway = 127;
		phrase_builder.setVelocityLeeway(leeway);
	}
	
	public void setAllVoiceMatch(boolean on){
		phrase_builder.setVoiceMatchMode(!on);
	}
	
	public void setNoteCompressionOptimizationLevel(int lvl){
		if(lvl < PhraseBuilder.OPT_LEVEL_0) lvl = PhraseBuilder.OPT_LEVEL_0;
		if(lvl > PhraseBuilder.OPT_LEVEL_2) lvl = PhraseBuilder.OPT_LEVEL_2;
		phrase_builder.setOptimizationLevel(lvl);
	}
	
	public void setMusicPhraseMinTime(int ticks){
		if(ticks < 0) ticks = 0;
		phrase_builder.setPhraseMinTime(ticks);
	}
	
	public void setMusicPhraseMinEvents(int count){
		if(count < 0) count = 0;
		phrase_builder.setPhraseMinEvents(count);
	}
	
	public void setOutro(int loopEnd, byte trigger_val){
		if(loop_end < 0) loop_end = 0;
		loop_end = loopEnd;
		outro_trigger = (int)trigger_val;
		//phrase_builder.setLoopEnd(loop_end);
		phrase_builder.addSegmentBorder(loop_end);
	}
	
	public void clearOutro(){
		loop_end = -1;
		outro_trigger = 0;
		//phrase_builder.setLoopEnd(loop_end);
		//Clear phrase_builder and put other values back...
		phrase_builder.clearSegmentBorders();
		if(loop_tick >= 0) phrase_builder.addSegmentBorder(loop_tick);
		for(Integer val : alt_entries.values()) phrase_builder.addSegmentBorder(val);
	}
	
	public void addAlternateEntry(int tick, byte trigger_val){
		alt_entries.put((int)trigger_val, tick);
		phrase_builder.addSegmentBorder(tick);
	}
	
	public void clearAlternateEntries(){
		alt_entries.clear();
		phrase_builder.clearSegmentBorders();
		if(loop_tick >= 0) phrase_builder.addSegmentBorder(loop_tick);
		if(loop_end >= 0) phrase_builder.addSegmentBorder(loop_end);
	}
	
	public void disableChannel(int idx){
		if(idx < 0 || idx >= 16) return;
		ch_enable[idx][0] = false;
	}
	
	public void enableChannel(int idx){
		if(idx < 0 || idx >= 16) return;
		ch_enable[idx][0] = true;
	}

	/*----- Utils -----*/
	
	protected void addToCmdMap(int time, BuilderCommand cmd){
		if(time < 0) return;
		List<BuilderCommand> list = seqcmds.get(time);
		if(list == null){
			list = new LinkedList<BuilderCommand>();
			seqcmds.put(time, list);
		}
		list.add(cmd);
	}
	
	public boolean compressMIDI(Sequence seq, String outpath){
		return false;
	/*	if(seq == null) return false;
		for(int i = 0; i < 16; i++) ch_enable[i][1] = false;
		
		//Read MIDI
		MIDIInterpreter reader = new MIDIInterpreter(seq);
		reader.readMIDITo(this);
		
		pushPriorities(0); //straighten priorities
		
		//Alloc some containers for the chunks
		int chunk_count = alt_entries.size() + 1;
		if(loop_tick >= 0 && !alt_entries.containsValue(loop_tick)) chunk_count++;
		if(loop_end >= 0 && !alt_entries.containsValue(loop_end)) chunk_count++;
		//Chunk table
		int[] ct_ticks = new int[chunk_count];
		boolean[] ct_isEntry = new boolean[chunk_count];
		CommandChunk[] ct_chunks = new CommandChunk[chunk_count];
		List<Integer> cpoints = new ArrayList<Integer>(chunk_count+1);
		cpoints.add(0);
		cpoints.addAll(alt_entries.values());
		if(loop_tick >= 0 && !alt_entries.containsValue(loop_tick)) cpoints.add(loop_tick);
		if(loop_end >= 0 && !alt_entries.containsValue(loop_end)) cpoints.add(loop_end);
		Collections.sort(cpoints);
		int i = 0;
		int ct_loop_idx = -1; //Index in chunk table of loop start
		for(Integer point : cpoints){
			ct_ticks[i] = point;
			ct_isEntry[i] = (point == 0) || (alt_entries.containsValue(point));
			ct_chunks[i] = new CommandChunk();
			if(point == loop_tick) ct_loop_idx = i;
			i++;
		}

		//Optimize channels 
		// (need to detect empty channels too and set defo priorities)
		int ticklen = getCurrentLengthInTicks();
		for(i = 0; i < 16; i++){
			channels[i].setExpectedSequenceLength(ticklen);
			channels[i].setDefaultPriority((byte)ch_pri[i]);
			channels[i].allocateBlocks(ct_ticks);
			for(int j = 0; j < chunk_count; j++){
				if(ct_ticks[j] == 0 || alt_entries.containsValue(ct_ticks[j])) channels[i].setBlockAsEntrypoint(j, true);
			}
		}
		
		//Compress regular channels
		for(i = 0; i < 16; i++){
			if(ch_enable[i][0] && ch_enable[i][1]){
				if(delay_tracks[i][0] < 0){
					channels[i].compressMusic(phrase_builder);
				}
			}
		}
		
		//Compress delay channels
		for(i = 0; i < 16; i++){
			if(ch_enable[i][0]){
				if(delay_tracks[i][0] >= 0){
					channels[i].compressAsDelayChannel(phrase_builder, 
							channels[delay_tracks[i][0]], delay_tracks[i][1]);
				}
			}
		}
		
		CommandChunk seq_chunk = new CommandChunk();
		//Seq start commands
		BuilderCommand bcmd = new BuilderGenericCommand(NUSALSeqCmdType.SET_FORMAT, 2);
		bcmd.setParam(0, 0x20); 
		seq_chunk.addCommand(bcmd);
		 
		bcmd = new BuilderGenericCommand(NUSALSeqCmdType.SET_TYPE, 2);
		bcmd.setParam(0, 0x32); 
		seq_chunk.addCommand(bcmd);
		
		bcmd = new BuilderGenericCommand(NUSALSeqCmdType.ENABLE_CHANNELS, 3);
		bcmd.setParam(0, 0xffff); 
		seq_chunk.addCommand(bcmd);*/
		
		
		//Multi-entrypoint jump table (if applicable)
				/*
				 * So what a lot of the multi-entry zelda seqs appear to do here is...
				 * 
				 * First, this little exchange...
				 * Q = C[7] (87)
				 * C[6] = Q (76)
				 * Q = -1 (cc ff)
				 * C[7] = Q (77)
				 * Q = C[6] (86)
				 * 
				 * This seems to set C[7] to -1, and C[6] and Q to C[7]
				 * In other words, the incoming C[7] determines which entrypoint to use.
				 * 
				 * ...Then it runs the variable to determine entrypoint
				 * If the current variable is LTEZ, first entrypoint
				 * Then, alternate between a decrement to Q (SUBTRACT_IMM) and EQZ check to see if branch off for higher values
				 * Then if after the highest entry value is tested, we still haven't branched, insert a JUMP to the last entry
				 */
		//Additionally, Gerudo Valley seems to use C[3] to track the point in the song it is at.
		//	This is probably so it can save its rough place if you go into certain loading zones then return to GV
		//	Honestly, that's kinda cool.
		//TODO might implement that here as well so that the game can read it if it wishes?
		
		/*
		if(!alt_entries.isEmpty()){
			//put in variable movement blurb
			bcmd = new BuilderGenericCommand(NUSALSeqCmdType.SEQ_UNK_8x, 1);
			bcmd.setParam(0, 7); 
			seq_chunk.addCommand(bcmd);
			bcmd = new BuilderGenericCommand(NUSALSeqCmdType.SET_CH_VAR, 1);
			bcmd.setParam(0, 6); 
			seq_chunk.addCommand(bcmd);
			bcmd = new BuilderGenericCommand(NUSALSeqCmdType.SET_VAR, 1);
			bcmd.setParam(0, -1); 
			seq_chunk.addCommand(bcmd);
			bcmd = new BuilderGenericCommand(NUSALSeqCmdType.SET_CH_VAR, 1);
			bcmd.setParam(0, 7); 
			seq_chunk.addCommand(bcmd);
			bcmd = new BuilderGenericCommand(NUSALSeqCmdType.SEQ_UNK_8x, 1);
			bcmd.setParam(0, 6); 
			seq_chunk.addCommand(bcmd);
			
			//Direct to tick 0 if <= 0
			bcmd = new BuilderReferenceCommand(NUSALSeqCmdType.BRANCH_IF_EQZ, ct_chunks[0]);
			seq_chunk.addCommand(bcmd);
			bcmd = new BuilderReferenceCommand(NUSALSeqCmdType.BRANCH_IF_LTZ, ct_chunks[0]);
			seq_chunk.addCommand(bcmd);
			
			//Sort trigger values and map to targets.
			Map<Integer, CommandChunk> tchunkmap = new TreeMap<Integer, CommandChunk>();
			for(i = 0; i < chunk_count; i++) tchunkmap.put(ct_ticks[i], ct_chunks[i]);
			List<Integer> triggerlist = new ArrayList<Integer>(alt_entries.size() + 1);
			triggerlist.addAll(alt_entries.keySet());
			Collections.sort(triggerlist);
			int tcount = triggerlist.size();
			int lastval = 0;
			for(i = 0; i < tcount; i++){
				int tval = triggerlist.get(i);
				int diff = tval - lastval;
				if(diff < 1) continue;
				bcmd = new BuilderGenericCommand(NUSALSeqCmdType.SUBTRACT_IMM, 2);
				bcmd.setParam(0, diff); 
				seq_chunk.addCommand(bcmd);
				lastval = tval;
				int tick = alt_entries.get(tval);
				CommandChunk ref = tchunkmap.get(tick);
				if(ref != null){
					bcmd = new BuilderReferenceCommand(NUSALSeqCmdType.BRANCH_IF_EQZ, ref);
					seq_chunk.addCommand(bcmd);
				}
			}
		}
		
		//Flag used channels for simplicity
		boolean[] chused = new boolean[16];
		int chflags = 0;
		for(i = 15; i >= 0; i++){
			if(ch_enable[i][0]){
				if(ch_enable[i][1] || delay_tracks[i][0] >= 0){
					//It is enabled
					chflags |= 0x1;
					chused[i] = true;
				}
			}
			chflags <<= 1;
		}
		
		//Now build the blocks...
		int now_tempo = 120;
		int now_vol = 0x7f;
		LinkedList<Integer> seq_event_times = new LinkedList<Integer>();
		seq_event_times.addAll(seqcmds.keySet());
		Collections.sort(seq_event_times);
		for(int b = 0; b < chunk_count; b++){
			CommandChunk block_chunk = ct_chunks[b];
			if(ct_isEntry[b]){
				//Need a channel enable command
				//(indices of channels in bits goes lo -> hi)
				//(ie. lowest bit is channel 0)
				bcmd = new BuilderGenericCommand(NUSALSeqCmdType.ENABLE_CHANNELS, 3);
				bcmd.setParam(0, chflags); 
				block_chunk.addCommand(bcmd);
			}
			//Channel positioning commands
			for(i = 0; i < 16; i++){
				if(chused[i]){
					CommandChunk ch_chunk = channels[i].getChannelChunk(b);
					if(ch_chunk != null){
						bcmd = new BuilderReferenceCommand(NUSALSeqCmdType.CHANNEL_OFFSET, i, ch_chunk);
						block_chunk.addCommand(bcmd);	
					}
				}
			}
			
			//Tempo & master volume updates
			List<BuilderCommand> cmdlist = this.seqcmds.get(ct_ticks[b]);
			boolean temposet = false;
			boolean volset = false;
			if(cmdlist != null){
				for(BuilderCommand cmd : cmdlist){
					switch(cmd.getCommand()){
					case SET_TEMPO:
						temposet = true;
						now_tempo = cmd.getParam(0);
						break;
					case MASTER_VOLUME:
						volset = true;
						now_vol = cmd.getParam(0);
						break;
					default: break;
					}
					block_chunk.addCommand(cmd);
				}
			}
			if(!temposet){
				bcmd = new BuilderGenericCommand(NUSALSeqCmdType.SET_TEMPO, 2);
				bcmd.setParam(0, now_tempo); 
				block_chunk.addCommand(bcmd);
			}
			if(!volset){
				bcmd = new BuilderGenericCommand(NUSALSeqCmdType.MASTER_VOLUME, 2);
				bcmd.setParam(0, now_vol); 
				block_chunk.addCommand(bcmd);
			}
			
			//Loop thru seq for anything else.
			int lasttime = ct_ticks[b];
			int endtime = ticklen;
			if(b+1 < chunk_count) endtime = ct_ticks[b+1];
			while(!seq_event_times.isEmpty()){
				int mytime = seq_event_times.pop();
				if(mytime == ct_ticks[b]) continue; //Already did it.
				if(mytime >= endtime){
					seq_event_times.push(mytime);
					break;
				}
				
				//Add wait
				if(mytime > lasttime){
					bcmd = new BuilderWaitCommand(mytime-lasttime, false);
					block_chunk.addCommand(bcmd);
				}
				
				//Put in commands
				cmdlist = this.seqcmds.get(mytime);
				for(BuilderCommand cmd : cmdlist){
					switch(cmd.getCommand()){
					case SET_TEMPO:
						now_tempo = cmd.getParam(0);
						break;
					case MASTER_VOLUME:
						now_vol = cmd.getParam(0);
						break;
					default: break;
					}
					block_chunk.addCommand(cmd);
				}
				
				lasttime = mytime;
			}
			
			//And add a wait until the next block
			if(lasttime < endtime){
				bcmd = new BuilderWaitCommand(endtime-lasttime, false);
				block_chunk.addCommand(bcmd);	
			}
		}
		
		//Add loop/outro info, and read end to sequence end
		if(loop_end >= 0){
			//Outro expected. Loop normally at second to last block.
			//TODO this needs to be way more complex
			//	rn it just checks if Q <= 0 and loops if so.
			//	It SHOULD be checking one of the C variables against the outro value.
			//	But which one? And which one used to temporarily save Q?
			if(ct_loop_idx >= 0){
				CommandChunk block_chunk = ct_chunks[chunk_count-2];
				bcmd = new BuilderReferenceCommand(NUSALSeqCmdType.BRANCH_IF_EQZ, ct_chunks[ct_loop_idx]);
				block_chunk.addCommand(bcmd);
				bcmd = new BuilderReferenceCommand(NUSALSeqCmdType.BRANCH_IF_LTZ, ct_chunks[ct_loop_idx]);
				block_chunk.addCommand(bcmd);
			}
		}
		else{
			//No outro. Put a jump command to the loop start block at end of last block.
			if(ct_loop_idx >= 0){
				bcmd = new BuilderReferenceCommand(NUSALSeqCmdType.BRANCH_ALWAYS, ct_chunks[ct_loop_idx]);
				ct_chunks[chunk_count-1].addCommand(bcmd);	
			}
		}
		bcmd = new BuilderGenericCommand(NUSALSeqCmdType.END_READ, 1);
		ct_chunks[chunk_count-1].addCommand(bcmd);	
		
		//Load everything into the main seq chunk...
		for(i = 0; i < chunk_count; i++) seq_chunk.addCommand(ct_chunks[i]); //Sequence
		//Channel control
		for(i = 0; i < chunk_count; i++){
			for(int j = 0; j < 16; j++){
				if(chused[j]){
					seq_chunk.addCommand(channels[j].getChannelChunk(i));
				}
			}
		}
		//Voice tracks
		for(i = 0; i < chunk_count; i++){
			for(int j = 0; j < 16; j++){
				if(chused[j]){
					CommandChunk[] charray = channels[j].getVoiceChunks(i);
					for(int k = 0; k < 4; k++){
						if(charray[k] != null && !charray[k].isEmpty()) seq_chunk.addCommand(charray[k]);
					}
				}
			}
		}
		//Voice phrases
		for(i = 0; i < chunk_count; i++){
			for(int j = 0; j < 16; j++){
				if(chused[j]){
					CommandChunk[] charray = channels[j].getPhraseChunks(i);
					for(int k = 0; k < 4; k++){
						if(charray[k] != null && !charray[k].isEmpty()) seq_chunk.addCommand(charray[k]);
					}
				}
			}
		}
		
		//Everything should be linked up. Now assign addresses...
		seq_chunk.setAddress(0);
		
		//And update references...
		seq_chunk.updateParameters();
		
		//And serialize!
		try{
			BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outpath));
			seq_chunk.serializeTo(bos);
			bos.close();
		}
		catch(IOException ex){
			ex.printStackTrace();
			return false;
		}
		
		return true;*/
	}
	
	/*----- Seq Controls -----*/
	
	public void setMasterVolume(byte value){
		NUSALSeqCmdType type = NUSALSeqCmdType.MASTER_VOLUME;
		BuilderCommand cmd = new BuilderGenericCommand(type, 2);
		cmd.setParam(0, Byte.toUnsignedInt(value));
		addToCmdMap(tick, cmd);
		//mastervol_set = true;
	}
	
	public void setTempo(int tempo_uspqn){
		NUSALSeqCmdType type = NUSALSeqCmdType.SET_TEMPO;
		BuilderCommand cmd = new BuilderGenericCommand(type, 2);
		cmd.setParam(0, MIDI.uspqn2bpm(tempo_uspqn, NUSALSeq.NUS_TPQN));
		addToCmdMap(tick, cmd);
	}
	
	public void setMasterPan(byte value){}
	public void addMarkerNote(String note){}
	
	//Channel settings
	public void setChannelVolume(int ch, byte value){setChannelExpression(ch, value);}
	
	public void setChannelExpression(int ch, byte value){
		channels[ch].setVolume(value);
	}
	
	public void setChannelPan(int ch, byte value){
		if(value == 0x40) value = 0x3F;
		channels[ch].setPan(value);
	}
	
	public void setChannelPriority(int ch, byte value){
		channels[ch].setPriority(value);
	}
	
	public void setModWheel(int ch, short value){
		//Interpreted as vibrato.
		int vali = (int)value;
		double vald = vali/32767.0;
		vald *= 127.0;
		byte valb = (byte)(Math.round(vald));
		channels[ch].setVibrato(valb);
	}
	
	public void setPitchWheel(int ch, short value){
		//Scale to PB range!!
		//MIDI pitch wheel is 14 bit unsigned normally.
		//Get ratio to full wheel value
		value -= 0x2000;
		int vali = (int)value;
		double vald = (double)vali;
		if(vali >= 0) vald /= (double)0x1fff;
		else vald /= (double)0x2000;
		
		//Calculate cents.
		int cents = (int)Math.round(vald * (double)input_pb_range[ch]);
		setPitchBend(ch, cents);
	}
	
	public void setPitchBend(int ch, int cents){
		double pbratio = (double)cents/((double)output_pb_range);
		double vald = pbratio * 127.0;
		byte valb = (byte)(Math.round(vald));
		channels[ch].setPitchWheel(valb);
	}
	
	public void setPitchBendRange(int ch, int cents){
		//Sets INPUT range (implication is that this is from the MIDI)
		input_pb_range[ch] = cents;
	}
	
	public void setProgram(int ch, int bank, int program){setProgram(ch, program);}
	
	public void setProgram(int ch, int program){
		if(!enable_seqProgramChange) return;
		channels[ch].setProgram((byte)program);
	}
	
	public void setControllerValue(int ch, int controller, byte value){}
	public void setControllerValue(int ch, int controller, int value, boolean omitFine){}
	
	public void setEffect1(int ch, byte value){
		channels[ch].setEffectsLevel(value);
	}
	
	public void setEffect2(int ch, byte value){
		channels[ch].setVibrato(value);
	}
	
	public void addNRPNEvent(int ch, int index, int value, boolean omitFine){
		if(!omitFine) value >>>= 8;
		switch(index){
		case NUSALSeq.MIDI_NRPN_ID_LOOPSTART:
			loop_tick = tick;
			break;
		case NUSALSeq.MIDI_NRPN_ID_PRIORITY:
			channels[ch].setPriority((byte)value);
			break;
		case NUSALSeq.MIDI_NRPN_ID_REVERB:
			channels[ch].setEffectsLevel((byte)value);
			break;
		case NUSALSeq.MIDI_NRPN_ID_VIBRATO:
			channels[ch].setVibrato((byte)value);
			break;
		}
	}
	
	//Music control
	public void noteOn(int ch, byte note, byte vel){
		ch_enable[ch][1] = true;
		channels[ch].noteOn(note, vel);
	}
	
	public void noteOff(int ch, byte note, byte vel){noteOff(ch, note);}
	
	public void noteOff(int ch, byte note){
		channels[ch].noteOff(note);
	}
	
	public long advanceTick(){
		tick++;
		for(int i = 0; i < 16; i++) channels[i].advanceTick();
		return tick;
	}
	
	public void complete(){}
	
	/*----- Export -----*/
	
	public static byte[] serializeCommand(NUSALSeqCommand cmd){
		/*if(cmd == null) return null;
		byte[] out = null;
		byte cmdb = cmd.getCommand().getBaseCommand();
		int cmdi = Byte.toUnsignedInt(cmdb);
		int param = 0;
		switch(cmd.getCommand()){
		//Just command byte
		case CH_DELTA_TIME:
			out = new byte[1];
			out[0] = (byte)cmd.getParam(0);
			break;
		//Basic no param
		case BRANCH_TO_SEQSTART:
		case END_READ:
		case INIT_CHANNEL:
		case LOOP_END:
		case RETURN:
		case RETURN_FROM_SEQ:
		case SEQ_UNK_CA:
		case SEQ_UNK_CB:
			out = new byte[1];
			out[0] = cmdb;
			break;
		//Basic 1 byte param
		case AND_IMM:
		case BRANCH_ALWAYS_REL:
		case BRANCH_IF_EQZ_REL:
		case BRANCH_IF_LTZ_REL:
		case CH_DRUMSET:
		case CH_PRIORITY:
		case CH_REVERB:
		case CH_UNK_DA:
		case CH_UNK_E0:
		case LOOP_START:
		case MASTER_VOLUME:
		case SEQ_UNK_DF:
		case SET_FORMAT:
		case SET_PROGRAM:
		case SET_TEMPO:
		case SET_TYPE:
		case SET_VAR:
		case SUBTRACT_IMM:
		case TRANSPOSE:
			out = new byte[2];
			out[0] = cmdb;
			out[1] = (byte)cmd.getParam(0);
			break;
		//Basic 2 byte param
		case BRANCH_ALWAYS:
		case BRANCH_IF_EQZ:
		case BRANCH_IF_GTEZ:
		case BRANCH_IF_LTZ:
		case CALL:
		case DISABLE_CHANNELS:
		case ENABLE_CHANNELS:
		case SEQ_UNK_C5:
			out = new byte[3];
			out[0] = cmdb;
			param = cmd.getParam(0);
			out[1] = (byte)((param >>> 8) & 0xFF);
			out[2] = (byte)(param & 0xFF);
			break;
		//Basic VLQ
		case REST:
		case WAIT:
			param = cmd.getParam(0);
			if(param < 128){
				out = new byte[2];
				out[0] = cmdb;
				out[1] = (byte)(param & 0xFF);
			}
			else{
				out = new byte[3];
				out[0] = cmdb;
				out[1] = (byte)(((param >>> 8) & 0xFF) | 0x80);
				out[2] = (byte)(param & 0xFF);
			}
			break;
		//2 1-byte params
		case SEQ_UNK_C4:
		case SEQ_UNK_C7:
			out = new byte[3];
			out[0] = cmdb;
			out[1] = (byte)cmd.getParam(0);
			out[2] = (byte)cmd.getParam(1);
			break;
		//2 params (2,1)
		case SEQ_UNK_EF:
			out = new byte[4];
			out[0] = cmdb;
			param = cmd.getParam(0);
			out[1] = (byte)((param >>> 8) & 0xFF);
			out[2] = (byte)(param & 0xFF);
			out[3] = (byte)cmd.getParam(1);
			break;
		//Channel lower nybble no param
		case LOAD_CH_VAR:
		case SEQ_UNK_4x:
		case SEQ_UNK_8x:
		case SET_CH_VAR:
		case SUBTRACT_CH:
			out = new byte[1];
			out[0] = (byte)((cmdi & 0xF0) | (cmd.getParam(0) & 0xF));
			break;
		//Voice layer lower nybble 2 byte param
		case VOICE_OFFSET_REL:
		case VOICE_OFFSET:
			out = new byte[3];
			out[0] = (byte)((cmdi & 0xF0) | ((cmd.getParam(0) + 8) & 0xF));
			param = cmd.getParam(1);
			out[1] = (byte)((param >>> 8) & 0xFF);
			out[2] = (byte)(param & 0xFF);
			break;
		//Channel lower nybble 2 byte param
		case CHANNEL_OFFSET_REL:
		case CHANNEL_OFFSET:
			out = new byte[3];
			out[0] = (byte)((cmdi & 0xF0) | (cmd.getParam(0) & 0xF));
			param = cmd.getParam(1);
			out[1] = (byte)((param >>> 8) & 0xFF);
			out[2] = (byte)(param & 0xFF);
			break;
		//Channel lower nybble 2 1-byte params
		case LOAD_CH_DMA:
			out = new byte[3];
			out[0] = (byte)((cmdi & 0xF0) | (cmd.getParam(0) & 0xF));
			out[1] = (byte)cmd.getParam(1);
			out[2] = (byte)cmd.getParam(2);
			break;
		//Channel lower nybble 2 params (1,2)
		case NEW_SEQ_FROM:
			out = new byte[4];
			out[0] = (byte)((cmdi & 0xF0) | (cmd.getParam(0) & 0xF));
			out[1] = (byte)cmd.getParam(1);
			param = cmd.getParam(2);
			out[2] = (byte)((param >>> 8) & 0xFF);
			out[3] = (byte)(param & 0xFF);
			break;
		//1 byte param normal, 2 params if time
		case CH_PAN:
		case CH_PITCHBEND:
		case CH_VIBRATO:
		case CH_VOLUME:
			param = cmd.getParam(1);
			if(param > 0){
				out = new byte[3];
				out[2] = (byte)cmd.getParam(1);
			}
			else out = new byte[2];
			out[0] = cmdb;
			out[1] = (byte)cmd.getParam(0);
			break;
		//Note commands
		case PLAY_NOTE_NTVG:
			param = cmd.getParam(1);
			if(param < 128){
				out = new byte[4];
				out[1] = (byte)(param & 0xFF);
				out[2] = (byte)cmd.getParam(2);
				out[3] = (byte)cmd.getParam(3);
			}
			else {
				out = new byte[5];
				out[1] = (byte)(((param >>> 8) & 0xFF) | 0x80);
				out[2] = (byte)(param & 0xFF);
				out[3] = (byte)cmd.getParam(2);
				out[4] = (byte)cmd.getParam(3);
			}
			out[0] = (byte)cmd.getParam(0);
			break;
		case PLAY_NOTE_NTV:
			param = cmd.getParam(1);
			if(param < 128){
				out = new byte[3];
				out[1] = (byte)(param & 0xFF);
				out[2] = (byte)cmd.getParam(2);
			}
			else {
				out = new byte[4];
				out[1] = (byte)(((param >>> 8) & 0xFF) | 0x80);
				out[2] = (byte)(param & 0xFF);
				out[3] = (byte)cmd.getParam(2);
			}
			out[0] = (byte)(cmd.getParam(0) + 0x40);
			break;
		case PLAY_NOTE_NVG:
			out = new byte[3];
			out[0] = (byte)(cmd.getParam(0) + 0x80);
			out[1] = (byte)cmd.getParam(1);
			out[2] = (byte)cmd.getParam(2);
			break;
		default:
			break;
		}
		
		return out;*/
		return null;
	}
	
}
