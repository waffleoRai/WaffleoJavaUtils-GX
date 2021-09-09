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
import waffleoRai_SeqSound.n64al.NUSALSeqCommand;
import waffleoRai_SeqSound.n64al.cmd.CMD_IgnoredCommand;
import waffleoRai_Utils.CoverageMap1D;

public class NUSALSeqBuilderChannel {

	private int ch_idx;
	private int tick;
	
	private Map<Integer, List<NUSALSeqCommand>> cmd_map;
	private NoteMap in_notes;
	private Map<Byte, int[]> active_notes; //[vel, start_tick]
	
	//Temporary builder fields...
	
	public NUSALSeqBuilderChannel(int index){
		ch_idx = index;
		cmd_map = new TreeMap<Integer, List<NUSALSeqCommand>>();
		active_notes = new TreeMap<Byte, int[]>();
		in_notes = new NoteMap();
	}
	
	public boolean isEmpty(){
		return cmd_map.isEmpty() && in_notes.isEmpty();
	}
	
	protected void addToCmdMap(int time, NUSALSeqCommand cmd){
		if(time < 0) return;
		List<NUSALSeqCommand> list = cmd_map.get(time);
		if(list == null){
			list = new LinkedList<NUSALSeqCommand>();
			cmd_map.put(time, list);
		}
		list.add(cmd);
	}
	
	public void setVolume(byte value){
		NUSALSeqCmdType type = NUSALSeqCmdType.CH_VOLUME;
		NUSALSeqCommand cmd = new CMD_IgnoredCommand(type, type.getBaseCommand(), 2);
		cmd.setParam(0, Byte.toUnsignedInt(value));
		addToCmdMap(tick, cmd);
	}
	
	public void setPan(byte value){
		NUSALSeqCmdType type = NUSALSeqCmdType.CH_PAN;
		NUSALSeqCommand cmd = new CMD_IgnoredCommand(type, type.getBaseCommand(), 2);
		cmd.setParam(0, Byte.toUnsignedInt(value));
		addToCmdMap(tick, cmd);
	}
	
	public void setPriority(byte value){
		NUSALSeqCmdType type = NUSALSeqCmdType.CH_PRIORITY;
		NUSALSeqCommand cmd = new CMD_IgnoredCommand(type, type.getBaseCommand(), 2);
		cmd.setParam(0, Byte.toUnsignedInt(value));
		addToCmdMap(tick, cmd);
	}
	
	public void setVibrato(byte value){
		NUSALSeqCmdType type = NUSALSeqCmdType.CH_VIBRATO;
		NUSALSeqCommand cmd = new CMD_IgnoredCommand(type, type.getBaseCommand(), 2);
		cmd.setParam(0, (int)value);
		addToCmdMap(tick, cmd);
	}
	
	public void setEffectsLevel(byte value){
		NUSALSeqCmdType type = NUSALSeqCmdType.CH_REVERB;
		NUSALSeqCommand cmd = new CMD_IgnoredCommand(type, type.getBaseCommand(), 2);
		cmd.setParam(0, (int)value);
		addToCmdMap(tick, cmd);
	}
	
	public void setPitchWheel(byte value){
		NUSALSeqCmdType type = NUSALSeqCmdType.CH_PITCHBEND;
		NUSALSeqCommand cmd = new CMD_IgnoredCommand(type, type.getBaseCommand(), 2);
		cmd.setParam(0, (int)value);
		addToCmdMap(tick, cmd);
	}
	
	public void setProgram(byte value){
		NUSALSeqCmdType type = NUSALSeqCmdType.SET_PROGRAM;
		NUSALSeqCommand cmd = new CMD_IgnoredCommand(type, type.getBaseCommand(), 2);
		cmd.setParam(0, (int)value);
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
	
	public void compressMusic(PhraseBuilder phraser){
		//TODO
		if(in_notes.isEmpty()) return;
		MusicEventMap phrasedMap = phraser.compressMusicEvents(in_notes);
		
		ArrayList<Map<Integer, MusicEvent>> voice_notes = 
				new ArrayList<Map<Integer, MusicEvent>>(4);
		
		//Map<Integer, Integer> phrase_assignments = new TreeMap<Integer, Integer>();
		//Map<Integer, NUSALSeqBuilderPhrase> phrase_map = new TreeMap<Integer, NUSALSeqBuilderPhrase>();
		Set<Integer> assigned_phrases = new TreeSet<Integer>();
		Map<Integer, CommandChunk> phrase_chunks = new TreeMap<Integer, CommandChunk>();
		CoverageMap1D[] voice_coverage = new CoverageMap1D[4];
		int[] lastnotes = new int[4];
		//int[] last_local = new int[4];
		MusicEvent[] tickevents = new MusicEvent[4];
		int[] tallies = new int[4];
		int j = 0;
		
		for(int i = 0; i < 4; i++){
			voice_coverage[i] = new CoverageMap1D();
			voice_notes.add(new TreeMap<Integer, MusicEvent>());
			lastnotes[i] = -1;
		}
	
		//----- Channel voice assignment
		int[] etimes = phrasedMap.getTimeCoords();
		for(int e = 0; e < etimes.length; e++){
			int t = etimes[e];
			j = 0; //Arrays.fill(last_local, -1);
			for(int i = 0; i < 4; i++) tickevents[i] = null;
			List<MusicEvent> events = phrasedMap.getEventsAt(t);
			for(MusicEvent me : events){
				if(me.isPhrase()){
					//Phrase
					NUSALSeqBuilderPhrase phrase = (NUSALSeqBuilderPhrase)me;
					//Has it already been assigned?
					if(assigned_phrases.contains(phrase.getID())) continue;
					assigned_phrases.add(phrase.getID());
					//Extract voices from phrase.
					NUSALSeqBuilderPhrase[] pvoices = phrase.getVoiceAssignments();
					for(int k = 0; k < pvoices.length; k++){
						if(j >= 4){
							throw new NUSALSeq.TooManyVoicesException("NUSALSeqBuilderChannel.compressMusic ||"
									+ " Requested voices at tick " + t + " exceeds available (4)!");
						}
						tickevents[j++] = pvoices[k];
						//Also serialize.
						CommandChunk chunk = pvoices[k].toVoiceCommandChunk();
						phrase_chunks.put(pvoices[k].getID(), chunk);
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
			LinkedList<EventTimeInfo> prilist = new LinkedList<EventTimeInfo>();
			for(int k = 0; k < j; k++){
				Set<Integer> avox = determineAvailableVoices(voice_coverage, tickevents[k], t);
				EventTimeInfo eti = new EventTimeInfo();
				eti.event = tickevents[k];
				eti.setSet(avox);
			}
			Arrays.fill(tallies, 0);
			for(EventTimeInfo eti : prilist){
				for(int i = 0; i < 4; i++){
					if(eti.av_vox.contains(i)) tallies[i]++;
				}
			}
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
		
		//----- Build voice level command blocks
		Map<Integer, List<CommandChunk>> vox_cmd_map = new TreeMap<Integer, List<CommandChunk>>();
		List<Integer> boundaries = phraser.getBoundaryTimes();
		for(int v = 0; v < 4; v++){
			//TODO
			Map<Integer, MusicEvent> voxevents = voice_notes.get(v);
			
			//Block up the voice
			CoverageMap1D cmap = voice_coverage[v];
			cmap.mergeBlocks();
			cmap.fillGapsSmallerThan(phraser.getMaxRestTime());
			for(int i : boundaries) cmap.splitBlockAt(i);
			
			//Partially serialize each block
			int[][] vblocks = cmap.getBlocks();
			int bcount = vblocks.length;
			for(int b = 0; b < bcount; b++){
				int start = vblocks[b][0];
				int end = vblocks[b][1];
				
				//Save length in ticks to chunk scratch field
				//Save voice index to scratch field of jump command to chunk which is saved in vox_cmd_map
			}
		}
		
	}
	
	/* --- Voice Assignment --- */
	
	//TODO don't forget to clear these once opt run is complete
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
	
}
