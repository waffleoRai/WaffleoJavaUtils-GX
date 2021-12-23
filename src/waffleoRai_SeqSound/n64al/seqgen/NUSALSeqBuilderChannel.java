package waffleoRai_SeqSound.n64al.seqgen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import waffleoRai_SeqSound.n64al.NUSALSeq;
import waffleoRai_SeqSound.n64al.NUSALSeqCmdType;
import waffleoRai_Utils.CoverageMap1D;

public class NUSALSeqBuilderChannel {

	/*----- Instance Variables -----*/
	
	private int ch_idx;
	private int tick;
	
	private Map<Integer, List<BuilderCommand>> cmd_map;
	private NoteMap in_notes;
	private Map<Byte, int[]> active_notes; //[vel, start_tick]
	
	private int defo_priority;
	
	//Output
	private int seq_len_ticks;
	private TimeBlock[] ser_blocks;
	private Map<Integer, List<BuilderCommand>> vox_cmd_map;
	
	//Temporary builder fields...
	private PhraseBuilder phraser;
	private MusicEventMap phrasedMap;
	private ArrayList<Map<Integer, MusicEvent>> voice_notes;
	private CoverageMap1D ch_coverage;
	private CoverageMap1D[] voice_coverage;
	private Map<Integer, CommandChunk> phrase_chunks; //Serialized phrases.
	
	/*----- Initialization -----*/
	
	public NUSALSeqBuilderChannel(int index){
		ch_idx = index;
		cmd_map = new TreeMap<Integer, List<BuilderCommand>>();
		active_notes = new TreeMap<Byte, int[]>();
		in_notes = new NoteMap();
		//block_starts = new TreeSet<Integer>();
		//block_starts.add(0);
		//block_starts_no_init = new TreeSet<Integer>();
	}
	
	/*----- Inner Classes -----*/
	
	private static class TimeBlock{
		
		public int startTick;
		public int endTick;
		public boolean isEntry;
		
		public CommandChunk chChunk;
		public CommandChunk[] voiceChunks;
		public CommandChunk[] phraseChunks;
		
		public TimeBlock(int start, int end){
			startTick = start;
			endTick = end;
			chChunk = new CommandChunk();
			voiceChunks = new CommandChunk[4];
			phraseChunks = new CommandChunk[4];
			isEntry = false;
		}
		
	}
	
	/*----- Getters -----*/
	
	public boolean isEmpty(){
		return cmd_map.isEmpty() && in_notes.isEmpty();
	}
	
	public CommandChunk getChannelChunk(int timeblock_idx){
		if(ser_blocks == null) return null;
		if(timeblock_idx < 0 || timeblock_idx >= ser_blocks.length) return null;
		return ser_blocks[timeblock_idx].chChunk;
	}
	
	public CommandChunk[] getVoiceChunks(int timeblock_idx){
		if(ser_blocks == null) return null;
		if(timeblock_idx < 0 || timeblock_idx >= ser_blocks.length) return null;
		return ser_blocks[timeblock_idx].voiceChunks;
	}
	
	public CommandChunk[] getPhraseChunks(int timeblock_idx){
		if(ser_blocks == null) return null;
		if(timeblock_idx < 0 || timeblock_idx >= ser_blocks.length) return null;
		return ser_blocks[timeblock_idx].phraseChunks;
	}
	
	/*----- Setters -----*/
	
	public void allocateBlocks(int[] block_start_ticks){
		if(block_start_ticks == null) return;
		int bcount = block_start_ticks.length;
		ser_blocks = new TimeBlock[bcount];
		for(int b = 0; b < bcount; b++){
			int start = block_start_ticks[b];
			int end = seq_len_ticks;
			if(b+1 < bcount) end = block_start_ticks[b+1];
			ser_blocks[b] = new TimeBlock(start, end);
		}
	}
	
	public void setBlockAsEntrypoint(int block_idx, boolean b){
		if(ser_blocks == null) return;
		if(block_idx < 0 || block_idx >= ser_blocks.length) return;
		ser_blocks[block_idx].isEntry = b;
	}
	
	public void setDefaultPriority(byte value){
		defo_priority = (int)value;
	}
	
	protected void setExpectedSequenceLength(int ticks){
		this.seq_len_ticks = ticks;
		if(ser_blocks != null){
			ser_blocks[ser_blocks.length - 1].endTick = seq_len_ticks;
		}
	}
	
	/*----- Sequence Control -----*/
	
	protected void addToCmdMap(int time, BuilderCommand cmd){
		if(time < 0) return;
		List<BuilderCommand> list = cmd_map.get(time);
		if(list == null){
			list = new LinkedList<BuilderCommand>();
			cmd_map.put(time, list);
		}
		list.add(cmd);
	}
	
	public void setVolume(byte value){
		NUSALSeqCmdType type = NUSALSeqCmdType.CH_VOLUME;
		BuilderCommand cmd = new BuilderTimeoptCommand(type, Byte.toUnsignedInt(value));
		addToCmdMap(tick, cmd);
	}
	
	public void setPan(byte value){
		NUSALSeqCmdType type = NUSALSeqCmdType.CH_PAN;
		BuilderCommand cmd = new BuilderTimeoptCommand(type, Byte.toUnsignedInt(value));
		addToCmdMap(tick, cmd);
	}
	
	public void setPriority(byte value){
		NUSALSeqCmdType type = NUSALSeqCmdType.CH_PRIORITY;
		BuilderCommand cmd = new BuilderTimeoptCommand(type, (int)value);
		addToCmdMap(tick, cmd);
	}
	
	public void setVibrato(byte value){
		/*NUSALSeqCmdType type = NUSALSeqCmdType.CH_VIBRATO;
		BuilderCommand cmd = new BuilderTimeoptCommand(type, (int)value);
		addToCmdMap(tick, cmd);*/
	}
	
	public void setEffectsLevel(byte value){
		NUSALSeqCmdType type = NUSALSeqCmdType.CH_REVERB;
		BuilderCommand cmd = new BuilderTimeoptCommand(type, (int)value);
		addToCmdMap(tick, cmd);
	}
	
	public void setPitchWheel(byte value){
		NUSALSeqCmdType type = NUSALSeqCmdType.CH_PITCHBEND;
		BuilderCommand cmd = new BuilderTimeoptCommand(type, (int)value);
		addToCmdMap(tick, cmd);
	}
	
	public void setProgram(byte value){
		NUSALSeqCmdType type = NUSALSeqCmdType.SET_PROGRAM;
		BuilderCommand cmd = new BuilderGenericCommand(type, 2);
		cmd.setParam(0, Byte.toUnsignedInt(value));
		addToCmdMap(tick, cmd);
	}
	
	public void noteOn(byte note, byte vel){
		//Need to make sure that there are less than 4 voices going.
		//If that is not the case, throw exception
		if(active_notes.size() >= 4){
			throw new NUSALSeq.TooManyVoicesException(
					"NUS AL sequence cannot have more than 4 voices in one channel."
					+ " (Channel: " + ch_idx + ", Tick: " + tick + ")");
		}
		
		//If the note is already on, this note overrides it.
		//Really should not be happening in a proper sequence but just in case.
		if(active_notes.containsKey(note)) noteOff(note);
		active_notes.put(note, new int[]{(int)vel, tick});
	}
	
	public void noteOff(byte note){
		//Pull note from active notes (return if null)
		// and put in the NOTE MAP
		int[] vals = active_notes.remove(note);
		if(vals == null) return;
		int len = tick - vals[1];
		NUSALSeqBuilderNote n = new NUSALSeqBuilderNote(note, (byte)vals[0]);
		n.setLength(len);
		in_notes.addNote(vals[1], n);
	}
	
	public int advanceTick(){
		return ++tick;
	}

	/* --- Optimization --- */
	
	private static class EventTimeInfo implements Comparable<EventTimeInfo>{
		//This is a (sortable) temporary container for voice assignment
		//Describes available voices for an event
		
		public MusicEvent event;
		public Set<Integer> av_vox;
		public int combo;
		
		public boolean equals(Object o){
			if(o == null) return false;
			if(o == this) return true;
			if(!(o instanceof EventTimeInfo)) return false;
			
			EventTimeInfo other = (EventTimeInfo)o;
			return this.event == other.event;
		}
		
		public int hashCode(){
			return event.hashCode();
		}
		
		public int compareTo(EventTimeInfo o) {
			if(o == null) return 1;
			int tsz = this.av_vox.size();
			int osz = o.av_vox.size();
			
			if(tsz != osz){
				return tsz - osz;
			}
			
			return this.combo - o.combo;
		}
		
		public void setSet(Set<Integer> set){
			av_vox = set;
			combo = 0;
			for(int i = 0; i < 4; i++){
				if(av_vox.contains(i)){
					combo |= (1 << i);
				}
			}
		}
		
	}
	
	private static void assignToVoice(CoverageMap1D voice_coverage, Map<Integer, MusicEvent> map, MusicEvent event, int tick){
		if(event.isPhrase()){
			NUSALSeqBuilderPhrase phrase = (NUSALSeqBuilderPhrase)event;
			int[] coords = phrase.getPhraseCoords();
			int plen = phrase.getLengthInTicks();
			for(int i = 0; i < coords.length; i++){
				int t = coords[i];
				map.put(t, event);
				voice_coverage.addBlock(t, t + plen);
			}
			//CommandChunk chunk = phrase.toVoiceCommandChunk();
			//chunk.setScratchField(value);
			//phrase_chunks.put(phrase.getID(), chunk);
		}
		else{
			map.put(tick, event);
			voice_coverage.addBlock(tick, tick + event.getLengthInTicks());
		}
	}
	
	private static Set<Integer> determineAvailableVoices(CoverageMap1D[] voice_coverage, MusicEvent event, int tick){
		Set<Integer> set = new TreeSet<Integer>();
		if(event.isPhrase()){
			NUSALSeqBuilderPhrase phrase = (NUSALSeqBuilderPhrase)event;
			//Needs to make sure voice is available for EVERY occurrence of this phrase.
			int[] coords = phrase.getPhraseCoords();
			int plen = phrase.getLengthInTicks();
			for(int i = 0; i < 4; i++){
				boolean pass = true;
				for(int j = 0; j < coords.length; j++){
					if(voice_coverage[i].isCovered(coords[j], coords[j]+plen)) {
						pass = false; break;
					}
				}
				if(pass) set.add(i);
			}
		}
		else{
			//Only needs to make sure voice is available for this note.
			for(int i = 0; i < 4; i++){
				if(!voice_coverage[i].isCovered(tick, tick+event.getLengthInTicks())) set.add(i);
			}
		}
		
		return set;
	}
	
	private void assignEventsToVoices(){
		//(Uses instance variables for input and output)
		
		phrasedMap = phraser.compressMusicEvents(in_notes); //PhraseBuilder output.
		voice_notes = new ArrayList<Map<Integer, MusicEvent>>(4); //Prep output as phrase/note map for each voice
		
		//Phrases that have been assigned to a voice (so don't get reassigned)
		Set<Integer> assigned_phrases = new TreeSet<Integer>(); 
		voice_coverage = new CoverageMap1D[4]; //1D map representing time where voices are busy
		
		int[] lastnotes = new int[4];
		MusicEvent[] tickevents = new MusicEvent[4]; //Container for events assigned to current tick
		int[] tallies = new int[4]; //Used for counting voices available for a given event
		int j = 0; //Counter for events at a timepoint
		
		//Instantiate array members.
		for(int i = 0; i < 4; i++){
			voice_coverage[i] = new CoverageMap1D();
			voice_notes.add(new TreeMap<Integer, MusicEvent>());
			lastnotes[i] = -1;
		}
		
		int[] etimes = phrasedMap.getTimeCoords();
		for(int e = 0; e < etimes.length; e++){
			//For each event group in the PhraseBuilder output map...
			int t = etimes[e]; //Get the time coordinate of event group
			
			//Split the event voices and store in array
			j = 0; //Reset event counter
			for(int i = 0; i < 4; i++) tickevents[i] = null; //Clear music events temp container
			List<MusicEvent> events = phrasedMap.getEventsAt(t); //Get events mapped to this timepoint
			for(MusicEvent me : events){
				//Iterate through events at timepoint
				if(me.isPhrase()){
					//Event is a phrase
					NUSALSeqBuilderPhrase phrase = (NUSALSeqBuilderPhrase)me;
					
					//Has it already been assigned?
					if(assigned_phrases.contains(phrase.getID())) continue;
					assigned_phrases.add(phrase.getID());
					
					//Extract voices from phrase.
					NUSALSeqBuilderPhrase[] pvoices = phrase.getVoiceAssignments();
					for(int k = 0; k < pvoices.length; k++){
						if(j >= 4){
							//Already 4 or more events at this time point
							throw new NUSALSeq.TooManyVoicesException("NUSALSeqBuilderChannel.compressMusic ||"
									+ " Requested voices at tick " + t + " exceeds available (4)!");
						}
						tickevents[j++] = pvoices[k];
					}
				}
				else{
					//Note
					if(j >= 4){
						throw new NUSALSeq.TooManyVoicesException("NUSALSeqBuilderChannel.compressMusic ||"
								+ " Requested voices at tick " + t + " exceeds available (4)!");
					}
					tickevents[j++] = me;
				}
			}//FOR Event at timepoint
			
			//Prioritize by event length and voice availability...
			LinkedList<EventTimeInfo> prilist = new LinkedList<EventTimeInfo>(); //List for event sorting
			for(int k = 0; k < j; k++){
				//Determine available voices for each individual event
				Set<Integer> avox = determineAvailableVoices(voice_coverage, tickevents[k], t);
				EventTimeInfo eti = new EventTimeInfo();
				eti.event = tickevents[k];
				eti.setSet(avox);
			}
			
			//Clear tally array (tallies how many events are compatible with each voice)
			Arrays.fill(tallies, 0);
			for(EventTimeInfo eti : prilist){
				for(int i = 0; i < 4; i++){
					if(eti.av_vox.contains(i)) tallies[i]++;
				}
			}
			
			//Pop events one-by-one (resorting each time an event is assigned)
			//	and assign to voice
			while(!prilist.isEmpty()){
				Collections.sort(prilist);
				EventTimeInfo eti_0 = prilist.pop();
				if(eti_0.av_vox.isEmpty()){
					throw new NUSALSeq.TooManyVoicesException("NUSALSeqBuilderChannel.compressMusic ||"
							+ " Tick " + t + " -- no available voices for note event...");
				}
				if(eti_0.av_vox.size() == 1){
					//No choice - assign to that voice.
					int v = -1;
					for(Integer n : eti_0.av_vox){
						v = n;
						break;
					}
					//Mark in voice
					assignToVoice(voice_coverage[v], voice_notes.get(v), eti_0.event, t);
					//Remove that voice from subsequent events
					for(EventTimeInfo eti : prilist) eti.av_vox.remove(v);
				}
				else{
					//Take lowest index voice with lowest # possible events
					int v = -1;
					int min = Integer.MAX_VALUE;
					for(int i = 0; i < 4; i++){
						if(eti_0.av_vox.contains(i)){
							if(tallies[i] < min){
								v = i;
								min = tallies[i];
							}
						}
					}
					if(v < 0){
						throw new NUSALSeq.TooManyVoicesException("NUSALSeqBuilderChannel.compressMusic ||"
								+ " Tick " + t + " -- no available voices for note event...");
					}
					//Mark in voice
					assignToVoice(voice_coverage[v], voice_notes.get(v), eti_0.event, t);
					//Remove that voice from subsequent events
					for(EventTimeInfo eti : prilist) eti.av_vox.remove(v);
				}
			}// WHILE unassigned events remain
		}//FOR event coord in channel
		
	}
	
	private void buildVoiceChunks(){
		
		//Instantiate structures to hold command blocks
		//ser_vox = new CommandChunk[4]; //Main voice blocks. Contains unphrased notes and call commands to phrases in voice phrase blocks.
		//ser_vox_phrases = new CommandChunk[4]; //Blocks containing the phrase data - usually appear after main voice blocks.
		vox_cmd_map = new TreeMap<Integer, List<BuilderCommand>>(); //Channel level commands. This method just adds the commands telling ch where to jump to each voice
		
		//Grab hard boundaries for blocking channel. (want voice jumps at each boundary)
		//List<Integer> boundaries = phraser.getBoundaryTimes();
		int block_count = ser_blocks.length;
		
		//Check for voices with no assignments.
		//Move up on index 
		int usedvoices = 0;
		ArrayList<Map<Integer, MusicEvent>> list = new ArrayList<Map<Integer, MusicEvent>>(4);
		for(int v = 0; v < 4; v++){
			Map<Integer, MusicEvent> voxevents = voice_notes.get(v);
			if(!voxevents.isEmpty()){
				list.add(voxevents);
				usedvoices++;
			}
		}
		
		//For each voice slot...
		for(int v = 0; v < usedvoices; v++){
			//Events assigned to this voice
			Map<Integer, MusicEvent> voxevents = voice_notes.get(v);
			if(voxevents.isEmpty()) continue;
			
			//Get the time coordinates for events assigned to this voice
			LinkedList<Integer> voxtimes = new LinkedList<Integer>();
			voxtimes.addAll(voxevents.keySet());
			Collections.sort(voxtimes);
			
			//so here's where we change it to block by loop/entry points
			//Iterate thru blocks
			for(int b = 0; b < block_count; b++){
				//Per time block!
				TimeBlock tb = ser_blocks[b];
				
				//Generate chunks for this time block and jump command from channel (to link to block)
				CommandChunk chunk = new CommandChunk(); //We're gonna put the direct voice commands here
				CommandChunk phrase_chunk = new CommandChunk(); //Phrase definitions go here
				BuilderReferenceCommand voxjump = 
						new BuilderReferenceCommand(NUSALSeqCmdType.VOICE_OFFSET, v, chunk);
				
				//Link the new jump command to the channel command map
				List<BuilderCommand> cmdlist = vox_cmd_map.get(tb.startTick);
				if(cmdlist == null){
					cmdlist = new LinkedList<BuilderCommand>();
					vox_cmd_map.put(tb.startTick, cmdlist);
				}
				cmdlist.add(voxjump);
				
				int timeval = -1; //Current time coordinate
				int lastend = -1; //End of previous command, for rests
				int transpose = 0;
				int lasttime = -1; //Last note time
				byte lastgate = -1; //Last note gate
				while(!voxtimes.isEmpty()){
					//Iterate through time coordinates assigned to voice
					//	until hit one that is after the end of this block.
					timeval = voxtimes.pop();
					if(timeval >= tb.endTick){
						voxtimes.push(timeval);
						break;
					}
					
					//Add rest command to vox chunk if needed
					if(timeval > lastend){
						BuilderCommand waitcmd = new BuilderWaitCommand(timeval-lastend, true);
						chunk.addCommand(waitcmd);
						//Since this command is not a note, reset the time and gate
						lasttime = -1;
						lastgate = -1; 
					}
					
					MusicEvent me = voxevents.get(timeval);
					if(me.isPhrase()){
						//Check if already serialized.
						NUSALSeqBuilderPhrase phrase = (NUSALSeqBuilderPhrase)me;
						CommandChunk sphrase = phrase_chunks.get(phrase.getID());
						
						//If not, serialize and add to phrase chunk
						if(sphrase == null){
							sphrase = phrase.toVoiceCommandChunk();
							phrase_chunks.put(phrase.getID(), sphrase);
							phrase_chunk.addCommand(sphrase);
						}
						
						//Add call command to vox chunk
						BuilderReferenceCommand call_cmd = 
								new BuilderReferenceCommand(NUSALSeqCmdType.CALL, sphrase);
						chunk.addCommand(call_cmd);
						
						lasttime = -1;
						lastgate = -1;
					}
					else{
						//Determine whether transpose is needed
						NUSALSeqBuilderNote mynote = (NUSALSeqBuilderNote)me;
						int min = 0x15 + transpose;
						int max = 0x54 + transpose;
						if(mynote.getNote() < min || mynote.getNote() > max){
							if(mynote.getNote() < min) transpose -= min - (int)mynote.getNote();
							else transpose += mynote.getNote() - max;
							/*BuilderCommand tcmd = new BuilderGenericCommand(NUSALSeqCmdType.TRANSPOSE, 2);
							tcmd.setParam(0, transpose);
							chunk.addCommand(tcmd);*/
						}
						
						//Convert to command note and add to phrase chunk
						BuilderNoteCommand notecmd = null;
						if(mynote.getLengthInTicks() == lasttime){
							notecmd = new BuilderNoteCommand(mynote.getNote(), transpose, mynote.getVelocity(), mynote.getGate());
						}
						else if(mynote.getGate() == lastgate){
							notecmd = new BuilderNoteCommand(mynote.getNote(), transpose, mynote.getLengthInTicks(), mynote.getVelocity());
						}
						else{
							notecmd = new BuilderNoteCommand(mynote.getNote(), transpose, 
									mynote.getLengthInTicks(), mynote.getVelocity(), mynote.getGate());
						}
						chunk.addCommand(notecmd);
						
						lasttime = mynote.getLengthInTicks();
						lastgate = mynote.getGate();
					}
					//Note where this event ends...
					lastend = timeval + me.getLengthInTicks();
				}
				
				if(transpose != 0){
					/*BuilderCommand tcmd = new BuilderGenericCommand(NUSALSeqCmdType.TRANSPOSE, 2);
					tcmd.setParam(0, 0);
					chunk.addCommand(tcmd);*/
				}
				
				//Add "end" command to vox block.
				BuilderCommand ecmd = new BuilderGenericCommand(NUSALSeqCmdType.END_READ, 1);
				chunk.addCommand(ecmd);
				
				//Put our new chunks in the time block if not empty.
				if(!chunk.isEmpty()) tb.voiceChunks[v] = chunk;
				if(!phrase_chunk.isEmpty()) tb.phraseChunks[v] = phrase_chunk;
				
			} //Per time block
		}//Per voice
	}
	
	protected void buildChannelBlockMap(){
		ch_coverage = new CoverageMap1D();
		for(int i = 0; i < voice_coverage.length; i++){
			if(voice_coverage[i] == null) continue;
			ch_coverage.add(voice_coverage[i]);
		}
		
		//Add channel level events to coverage map.
		//These commands should all be one-tick
		List<Integer> ilist = new ArrayList<Integer>(cmd_map.size()+1);
		ilist.addAll(cmd_map.keySet());
		Collections.sort(ilist);
		for(Integer time : ilist){
			ch_coverage.addBlock(time, time+1);
		}
		ch_coverage.mergeBlocks();
		ilist = phraser.getBoundaryTimes();
		for(Integer b : ilist) ch_coverage.splitBlockAt(b);
	}
	
	private void buildChannelCommandBlocks(int delay){
		int block_count = ser_blocks.length;
		
		//Get timecoords for all events in channel.
		LinkedList<Integer> timelist = new LinkedList<Integer>();
		Set<Integer> iset = new TreeSet<Integer>();
		iset.addAll(cmd_map.keySet()); //Misc channel control commands issued from MIDI read
		iset.addAll(vox_cmd_map.keySet()); //Voice jump commands. Should only be at each block start, really.
		timelist.addAll(iset);
		Collections.sort(timelist);
		
		//Define interblock variables
		int chvol = 127;
		int chpan = 0x40;
		int chpri = defo_priority;
		int chprog = ch_idx;
		boolean[] checklist = new boolean[4];
		int ch_pb = 0;
		int ch_vib = 0;
		int ch_eff = 0;
		BuilderCommand ext_cmd = null; //Ref to command to append time diff to if needed
		
		//Iterate through blocks
		int last_time = -1;
		for(int b = 0; b < block_count; b++){
			if(timelist.isEmpty()) break; //Shouldn't happen if mapping correct but here in case.
			TimeBlock tb = ser_blocks[b];
			int start = tb.startTick;
			int end = tb.endTick;
			
			CommandChunk chunk = new CommandChunk();
			tb.chChunk = chunk;
			ext_cmd = null; //Gets reset since each block ends with READ_END
			
			//Check if entry point
			last_time = start;
			int t = timelist.pop();

			//Add channel init command (If appl.)
			if(tb.isEntry){
				//chunk.addCommand(new BuilderGenericCommand(NUSALSeqCmdType.INIT_CHANNEL, 1));	
			}
			//Add voice jump commands
			List<BuilderCommand> voxjumplist = vox_cmd_map.get(start);
			if(voxjumplist != null){
				//Shouldn't be null but again... 
				if(delay > 0){
					for(BuilderCommand cmd : voxjumplist){
						//Build a new mini chunk that waits the delay time, then jumps to other track's chunk
						//Also shorten notes/waits at the end if go over end of block.
						if(cmd.getCommand() == NUSALSeqCmdType.VOICE_OFFSET){
							int v = cmd.getParam(0);
							CommandChunk vchunk = tb.voiceChunks[v];
							if(vchunk == null){
								vchunk = new CommandChunk();
								tb.voiceChunks[v] = vchunk;
							}
							
							//Add jump command within voice
							BuilderCommand ref = cmd.getReference();
							BuilderCommand vjumpcmd = new BuilderReferenceCommand(NUSALSeqCmdType.CALL, ref);
							BuilderCommand waitcmd = new BuilderWaitCommand(delay, true);
							vchunk.addCommand(waitcmd);
							vchunk.addCommand(vjumpcmd);
							
							//Add jump command from channel (vox offset command)
							BuilderCommand jumpcmd = new BuilderReferenceCommand(NUSALSeqCmdType.VOICE_OFFSET, v, waitcmd);
							chunk.addCommand(jumpcmd);
							
							//Shorten referenced chunk if needed.
							ref.limitTicksTo(end-start-delay);
						}
					}
				}
				else{
					for(BuilderCommand cmd : voxjumplist) chunk.addCommand(cmd);	
				}
			}
			//Add commands mapped to this point.
			Arrays.fill(checklist, false);
			List<BuilderCommand> cmdlist = cmd_map.get(start);
			for(BuilderCommand cmd : cmdlist){
				switch(cmd.getCommand()){
					case CH_VOLUME:
						if(!checklist[0]){
							chunk.addCommand(cmd);
							checklist[0] = true;
							chvol = cmd.getParam(0);
							ext_cmd = cmd;
						}
						break;
					case CH_PAN:
						if(!checklist[1]){
							chunk.addCommand(cmd);
							checklist[1] = true;
							chpan = cmd.getParam(0);
							ext_cmd = cmd;
						}
						break;
					case CH_PRIORITY:
						if(!checklist[2]){
							chunk.addCommand(cmd);
							checklist[2] = true;
							chpri = cmd.getParam(0);
							ext_cmd = cmd;
						}
						break;
					case SET_PROGRAM:
						if(!checklist[3]){
							chunk.addCommand(cmd);
							checklist[3] = true;
							chprog = cmd.getParam(0);
							ext_cmd = null;
						}
						break;
					/*case CH_VIBRATO:
						chunk.addCommand(cmd);
						ext_cmd = cmd;
						ch_vib = cmd.getParam(0);
						break;*/
					case CH_REVERB:
						chunk.addCommand(cmd);
						ext_cmd = cmd;
						ch_eff = cmd.getParam(0);
						break;
					case CH_PITCHBEND:
						chunk.addCommand(cmd);
						ext_cmd = cmd;
						ch_pb = cmd.getParam(0);
						break;
					default: break;
				}
				
				//Add any other channel state commands if needed (Vol, pan, priority, program)	
				BuilderCommand bcmd = null;
				if(!checklist[0]){
					bcmd = new BuilderTimeoptCommand(NUSALSeqCmdType.CH_VOLUME, chvol);
					chunk.addCommand(bcmd);
					ext_cmd = bcmd;
				}
				if(!checklist[1]){
					bcmd = new BuilderTimeoptCommand(NUSALSeqCmdType.CH_PAN, chpan);
					chunk.addCommand(bcmd);
					ext_cmd = bcmd;
				}
				if(!checklist[2]){
					bcmd = new BuilderTimeoptCommand(NUSALSeqCmdType.CH_PRIORITY, chpri);
					chunk.addCommand(bcmd);
					ext_cmd = bcmd;
				}
				if(!checklist[3]){
					/*bcmd = new BuilderGenericCommand(NUSALSeqCmdType.SET_PROGRAM, 2);
					bcmd.setParam(0, chprog);*/
					bcmd = new BuilderTimeoptCommand(NUSALSeqCmdType.CH_PRIORITY, chprog);
					chunk.addCommand(bcmd);
					ext_cmd = null;
				}
				
				if(ch_pb != 0){
					bcmd = new BuilderTimeoptCommand(NUSALSeqCmdType.CH_PITCHBEND, ch_pb);
					chunk.addCommand(bcmd);
					ext_cmd = bcmd;
				}
				/*if(ch_vib != 0){
					bcmd = new BuilderTimeoptCommand(NUSALSeqCmdType.CH_VIBRATO, ch_vib);
					chunk.addCommand(bcmd);
					ext_cmd = bcmd;
				}*/
				if(ch_eff != 0){
					bcmd = new BuilderTimeoptCommand(NUSALSeqCmdType.CH_REVERB, ch_eff);
					chunk.addCommand(bcmd);
					ext_cmd = bcmd;
				}
				
				//Push time value back on list if not what was expected
				if(t > start) timelist.push(t);
			}
			
			//Pop from timelist until time goes outside block
			while(t < end || end == -1){
				
				//Check incoming commands to see if one is the same type as ext_cmd (if applicable)
				cmdlist = cmd_map.get(t);
				BuilderCommand nxt_cmd = null;
				if(ext_cmd != null){
					List<BuilderCommand> cpylist = new LinkedList<BuilderCommand>();
					for(BuilderCommand cmd : cmdlist){
						if(cmd.getCommand() == ext_cmd.getCommand()){
							nxt_cmd = cmd;
						}
						else cpylist.add(cmd);
					}
					cmdlist = cpylist;
				}
				
				//Add needed wait (or append to previous command)
				int wait_time = t - last_time;
				if(wait_time > 0){
					if(wait_time < 0x78 && nxt_cmd != null){
						//ext_cmd.setOptionalTime(wait_time);
					}
					else{
						//Not appended to previous command
						//Instead use a wait command
						chunk.addCommand(new BuilderWaitCommand(wait_time, false));
					}
				}
				
				if(nxt_cmd != null){
					chunk.addCommand(nxt_cmd);
					ext_cmd = nxt_cmd;
					switch(nxt_cmd.getCommand()){
					case CH_VOLUME:
						chvol = nxt_cmd.getParam(0);
						break;
					case CH_PAN:
						chpan = nxt_cmd.getParam(0);
						break;
					case CH_PRIORITY:
						chpri = nxt_cmd.getParam(0);
						break;
					/*case CH_VIBRATO:
						ch_vib = nxt_cmd.getParam(0);
						break;*/
					case CH_REVERB:
						ch_eff = nxt_cmd.getParam(0);
						break;
					case CH_PITCHBEND:
						ch_pb = nxt_cmd.getParam(0);
						break;
					default: break;
					}
				}
						
				//Get other commands
				//(Note if there aren't any compatible, set ext_cmd to null)
				if(cmdlist.isEmpty()){
					if(nxt_cmd == null) ext_cmd = null;
				}
				for(BuilderCommand cmd : cmdlist){
					chunk.addCommand(cmd);
					//if(cmd.isTimeExtendable()) ext_cmd = cmd;
					//else ext_cmd = null;
					switch(cmd.getCommand()){
					case CH_VOLUME:
						chvol = cmd.getParam(0);
						break;
					case CH_PAN:
						chpan = cmd.getParam(0);
						break;
					case CH_PRIORITY:
						chpri = cmd.getParam(0);
						break;
					case SET_PROGRAM:
						chprog = cmd.getParam(0);
						break;
					/*case CH_VIBRATO:
						ch_vib = cmd.getParam(0);
						break;*/
					case CH_REVERB:
						ch_eff = cmd.getParam(0);
						break;
					case CH_PITCHBEND:
						ch_pb = cmd.getParam(0);
						break;
					default: break;
					}
				}
				
				//Next
				last_time = t;
				if(timelist.isEmpty()){t = -1; break;}
				t = timelist.pop();
			}
			if(t >= 0) timelist.push(t);
			
			//Add any needed rests to end of block
			int wait_time = end - last_time;
			if(wait_time > 0){
				chunk.addCommand(new BuilderWaitCommand(wait_time, false));
			}
			
			//Put read end command.
			chunk.addCommand(new BuilderGenericCommand(NUSALSeqCmdType.END_READ, 1));
		}//Per block
		
	}
	
	public void compressMusic(PhraseBuilder phrase_builder){
		if(in_notes.isEmpty()) return;
		phraser = phrase_builder;
		
		assignEventsToVoices();
		buildVoiceChunks();
		
		//Add channel commands
		//Don't forget channel init commands
		//Build the channel chunks (need to block up)
		//Also don't forget channel init commands at start of each block
		
		//Form a channel-wide block map.
		//buildChannelBlockMap();
		
		//Build channel blocks
		buildChannelCommandBlocks(0);
	}
	
	public void compressAsDelayChannel(PhraseBuilder phrase_builder, NUSALSeqBuilderChannel source, int delay){
		phraser = phrase_builder;
		
		//ser_vox = new CommandChunk[4]; 
		//ser_vox_phrases = new CommandChunk[4]; 
		vox_cmd_map = source.vox_cmd_map;
		
		buildChannelCommandBlocks(delay);
	}
	
	/* --- Voice Assignment --- */
	
	private static List<int[]> combos_2vox;
	private static List<int[]> combos_3vox;
	private static List<int[]> combos_4vox;
	
	private static List<int[]> genOrderedCombos(Deque<Integer> set){
		int ssize = set.size();
		if(ssize == 0) return null;
		else if(ssize == 1){
			List<int[]> list = new LinkedList<int[]>();
			list.add(new int[]{set.getFirst()});
			return list;
		}
		else{
			List<int[]> list = new LinkedList<int[]>();
			for(int i = 0; i < ssize; i++){
				//Remove first.
				int me = set.pop();
				List<int[]> res = genOrderedCombos(set);
				if(res == null || res.isEmpty()) list.add(new int[]{me});
				else{
					for(int[] r : res){
						int l = r.length+1;
						int[] arr = new int[l];
						arr[0] = me;
						for(int j = 0; j < r.length; j++) arr[j+1] = r[j];
						list.add(arr);
					}
				}
				set.addLast(me);
			}
			return list;
		}
	}
	
	private static List<int[]> getCombos(int voxcount){
		
		if(voxcount == 2 && combos_2vox != null) return combos_2vox;
		if(voxcount == 3 && combos_3vox != null) return combos_3vox;
		if(voxcount == 4 && combos_4vox != null) return combos_4vox;
		
		LinkedList<Integer> iset = new LinkedList<Integer>();
		for(int i = 0; i < voxcount; i++) iset.add(i);
		List<int[]> combos = genOrderedCombos(iset);
		
		switch(voxcount){
		case 2:
			combos_2vox = combos;
			return combos_2vox;
		case 3:
			combos_3vox = combos;
			return combos_3vox;
		case 4:
			combos_4vox = combos;
			return combos_4vox;
		}
		
		return combos;
	}
	
	protected static int[] assignNotes(int[] lastnotes, int[] newnotes){
		if(lastnotes == null || newnotes == null) return null;
		int ncount = Math.min(lastnotes.length, newnotes.length);
		if(ncount == 1) return new int[]{0};
		
		List<int[]> combos = getCombos(ncount);
		int ccount = combos.size();
		int[][] counts = new int[ccount][2];
		int minmax = 128;
		
		int i = 0;
		for(int[] combo : combos){
			int maxdist = 0;
			int total = 0;
			for(int j = 0; j < combo.length; j++){
				int dist = 0;
				if(lastnotes[j] >= 0){
					dist = Math.abs(lastnotes[j] - newnotes[combo[j]]);
				}
				if(dist > maxdist) maxdist = dist;
				total += dist;
			}
			counts[i][0] = maxdist;
			counts[i][1] = total;
			if(maxdist < minmax) minmax = maxdist;
			i++;
		}
		
		//Of those with maxdist of minmax, choose one with lowest total.
		int idx = -1;
		int mintot = Integer.MAX_VALUE;
		for(i = 0; i < ccount; i++){
			if(counts[i][0] <= minmax){
				if(counts[i][1] < mintot){
					mintot = counts[i][1];
					idx = i;
				}
			}
		}
		
		if(idx < 0) return null;
		return combos.get(idx);
	}
	
	public static void cleanup(){
		combos_2vox.clear(); combos_2vox = null;
		combos_3vox.clear(); combos_3vox = null;
		combos_4vox.clear(); combos_4vox = null;
	}
	
}
