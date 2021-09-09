package waffleoRai_SeqSound.n64al.seqgen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import waffleoRai_SeqSound.n64al.NUSALSeq;
import waffleoRai_SeqSound.n64al.NUSALSeqCmdType;

public class NUSALSeqBuilderPhrase implements MusicEvent{

	private int id;
	private NoteMap notes;
	
	//Builder bookkeeping
	private Set<Integer> coordinates;
	
	//Internal bookkeeping
	private boolean dirty = false;
	private int len = 0;
	private int events = 0;
	private int max_voices = 0;
	private int[] voices_free;
	private int[] active_voices_at;
	
	private int isect_tick;
	private Map<Byte, Integer> isectsim_active;
	
	//Voice Assignment
	private ArrayList<Map<Integer, NUSALSeqBuilderNote>> voice_notes;
	
  	public NUSALSeqBuilderPhrase(){
		notes = new NoteMap();
		coordinates = new TreeSet<Integer>();
		voices_free = new int[4];
		id = new Random().nextInt();
	}
	
	private void refreshBookkeeping(){
		len = 0; events = 0;
		max_voices = 0;
		Arrays.fill(voices_free, 0);
		int[] time_coords = notes.getTimeCoords();
		active_voices_at = new int[time_coords.length];
		for(int i = 0; i < time_coords.length; i++){
			NUSALSeqBuilderNote[] notegroup = notes.getNoteGroupAt(time_coords[i]);
			active_voices_at[i] += notegroup.length;
			if(active_voices_at[i] > max_voices) max_voices = active_voices_at[i];
			events += notegroup.length; //Don't forget to count rests
			ArrayList<Integer> lenlist = new ArrayList<Integer>(4);
			for(int j = 0; j < notegroup.length; j++){
				NUSALSeqBuilderNote n = notegroup[j];
				int endtick = time_coords[i] + n.getLengthInTicks();
				if(endtick > len) len = endtick;
				lenlist.add(endtick);
				//Check if likely rest.
				if(i+1 < time_coords.length){
					if(endtick < time_coords[i+1]) events++;
				}
				//Increment subsequent events if covered
				for(int k = i+1; k < time_coords.length; k++){
					if(endtick > time_coords[k]) active_voices_at[k]++;
					else break;
				}
			}
			Collections.sort(lenlist);
			Collections.reverse(lenlist);
			for(int j = 0; j < notegroup.length; j++){
				int lval = lenlist.get(j);
				if(lval > voices_free[j]) voices_free[j] = lval;
			}
		}
		dirty = false;
	}
	
	public boolean isPhrase(){return true;}
	public int getID(){return id;}
	
	public int[] getNoteonCoords(){
		return notes.getTimeCoords();
	}
	
	public int[] getPhraseCoords(){
		if(coordinates.isEmpty()) return null;
		int count = coordinates.size();
		List<Integer> ilist = new ArrayList<Integer>(count);
		ilist.addAll(coordinates);
		Collections.sort(ilist);
		int[] arr = new int[count];
		int i = 0;
		for(Integer n : ilist) arr[i++] = n;
		return arr;
	}
	
	public NUSALSeqBuilderNote[] getNotesAt(int tick_coord){
		return notes.getNoteGroupAt(tick_coord);
	}
	
	public boolean hasAnnotatedCoordinates(){
		return !coordinates.isEmpty();
	}
	
	public boolean startsAtTime(int tick_coord){
		return coordinates.contains(tick_coord);
	}
	
	public byte getFirstNotePitch(){
		if(notes.isEmpty()) return -1;
		int time = notes.getLowestTimeCoord();
		List<NUSALSeqBuilderNote> events = notes.getNotesAt(time);
		if(events.isEmpty()) return -1;
		return events.get(0).getFirstNotePitch();
	}
	
	public int getLengthInTicks(){
		if(dirty) refreshBookkeeping();
		return len;
	}
	
	public int getLengthInEvents(){
		if(dirty) refreshBookkeeping();
		return events;
	}
	
	public int eventsSaved(){
		//Calculates appr. event reduction if this phrase is used for compression
		int appears = coordinates.size();
		if(appears < 1) appears = 1;
		//Each appearance is a call/return event pair in exchange for length.
		int ecount = getLengthInEvents();
		int decomp_count = ecount * appears;
		int comp_count = ecount + (appears*2);
		return decomp_count - comp_count;
	}
	
	public int voicesRequired(){
		if(dirty) refreshBookkeeping();
		return max_voices;
	}
	
	public int voicesRequiredAfter(int rel_coord){
		if(dirty) refreshBookkeeping();
		int vcount = 0;
		for(int i = 0; i < 4; i++){
			if(voices_free[i] > rel_coord) vcount++;
		}
		return vcount;
	}
	
	public int voicesAvailable(){
		return 4-voicesRequired();
	}
	
	public int voicesAvailableAfter(int rel_coord){
		return 4 - voicesRequiredAfter(rel_coord);
	}
	
	private boolean startIsectSim(int start_tick, Set<Byte> noteset){
		isectsim_active = new TreeMap<Byte, Integer>();
		
		int[] times = notes.getTimeCoords();
		int tidx = times.length-1;
		for(int i = 0; i < times.length-1; i++){
			if(times[i+1] > start_tick){
				tidx = i;
				break;
			}
		}
		
		isect_tick = times[tidx];
		while(isect_tick < start_tick){
			if(!isectSimNextTick(noteset)) return false;
		}
		
		return true;
	}
	
	private boolean isectSimNextTick(Set<Byte> noteset){
		//Check current notes and turn off if needed.
		if(!isectsim_active.isEmpty()){
			List<Byte> on = new LinkedList<Byte>();
			on.addAll(isectsim_active.keySet());
			for(Byte key : on){
				int val = isectsim_active.get(key) - 1;
				if(val <= 0){
					isectsim_active.remove(key);
					noteset.remove(key);
				}
				else isectsim_active.put(key, val);
			}
		}
		
		NUSALSeqBuilderNote[] ngroup = notes.getNoteGroupAt(isect_tick++);
		if(ngroup == null) return true;
		
		for(NUSALSeqBuilderNote n : ngroup){
			if(noteset.contains(n.getNote())) return false;
			noteset.add(n.getNote());
			isectsim_active.put(n.getNote(), n.getLengthInTicks());
		}
		
		return true;
	}
	
	private void stopIsectSim(){
		isectsim_active.clear();
		isectsim_active = null;
		isect_tick = 0;
	}
	
	public boolean intersects(NUSALSeqBuilderPhrase other){
		if(other == null) return false;
		
		//1. Do times ever intersect at all?
		// (If not, return false)
		List<int[]> itimes = new LinkedList<int[]>(); //[mycoord, ocoord, start, end]
		int[] o_phr_starts = other.getPhraseCoords();
		int[] my_phr_starts = this.getPhraseCoords();
		int o_phr_len = other.getLengthInTicks();
		int my_phr_len = this.getLengthInTicks();
		for(int i = 0; i < o_phr_starts.length; i++){
			int ost = o_phr_starts[i]; 
			int oed = ost + o_phr_len;
			for(int j = 0; j < my_phr_starts.length; j++){
				int myst = my_phr_starts[j];
				int myed = myst + my_phr_len;
				
				//Check overlap.
				if(ost >= myed) continue;
				if(oed <= myst) continue;
				
				int start = Math.max(ost, myst);
				int end = Math.min(oed, myed);
				int[] region = new int[4];
				/*region[0] = start - myst;
				region[1] = end - myst;
				region[2] = start - ost;
				region[3] = end - ost;*/
				region[0] = myst;
				region[1] = ost;
				region[2] = start;
				region[3] = end;
				
				itimes.add(region);
			}
		}
		if(itimes.isEmpty()) return false;
		
		//2. For intersecting times, are there any cases where there are > 4 voices?
		//	(If so, return true)
		if(voicesAvailable() < other.voicesRequired()){
			//See if we can cram one in at the end of the other.
			for(int[] reg : itimes){
				if(reg[0] > reg[1]){
					//This phrase starts in middle of other phrase
					int needvox = voicesRequired();
					int pos = reg[0] - reg[1];
					if(other.voicesAvailableAfter(pos) < needvox) return true;
				}
				else if(reg[0] < reg[1]){
					//Other phrase starts in the middle of this phrase
					int needvox = other.voicesRequired();
					int pos = reg[1] - reg[0];
					if(voicesAvailableAfter(pos) < needvox) return true;
				}
				else return true; //They start at same time.
			}	
		}
		
		//3. For intersecting sections, are there any cases where the same note
		//	is on at the same time in both phrases?
		//	(If so, return true)
		for(int[] reg : itimes){
			Set<Byte> onnotes = new TreeSet<Byte>();
			int reglen = reg[3] - reg[2];
			int myst = reg[2] - reg[0];
			int ost = reg[2] - reg[1];
			this.startIsectSim(myst, onnotes);
			if(!other.startIsectSim(ost, onnotes)){
				this.stopIsectSim(); other.stopIsectSim();
				return true;
			}
			for(int t = 0; t < reglen; t++){
				if(!this.isectSimNextTick(onnotes) || !other.isectSimNextTick(onnotes)){
					this.stopIsectSim(); other.stopIsectSim();
					return true;
				}
			}
		}
		
		//No intersection detected.
		return false;
	}
	
	public void addNote(int rel_tick, NUSALSeqBuilderNote note){
		//Make a copy of the note to add!
		NUSALSeqBuilderNote copy = note.createUnlinkedCopy();
		notes.addNote(rel_tick, copy);
		dirty = true;
		if(voice_notes != null) voice_notes.clear();
		voice_notes = null;
	}
	
	public void addStartCoord(int val){
		coordinates.add(val);
	}
	
	public void clearStartCoords(){coordinates.clear();}
	
	public void assignToVoices(){
		if(dirty) refreshBookkeeping();
		voice_notes = new ArrayList<Map<Integer, NUSALSeqBuilderNote>>(4);
		for(int i = 0; i < 4; i++)voice_notes.add(new HashMap<Integer, NUSALSeqBuilderNote>());
		int[] ncoords = notes.getTimeCoords();
		if(ncoords == null) return;
		int[] last = new int[4]; Arrays.fill(ncoords, -1);
		int[] ends = new int[4]; //Ending tick of previous note
		int[] last_local = new int[4];
		for(int e = 0; e < ncoords.length; e++){
			int t = ncoords[e];
			NUSALSeqBuilderNote[] group = notes.getNoteGroupAt(t);
			int[] gnotes = new int[group.length];
			for(int i = 0; i < group.length; i++){
				gnotes[i] = (int)group[i].getNote();
			}
			
			//Determine which voices are free and their last notes.
			Arrays.fill(last_local, 0);
			int j = 0;
			for(int i = 0; i < 4; i++){
				if(t >= ends[i]){
					last_local[j++] = last[i];
				}
			}
			
			int[] va = NUSALSeqBuilderChannel.assignNotes(last_local, gnotes);
			j = 0;
			for(int i = 0; i < group.length; i++){
				NUSALSeqBuilderNote note = group[va[i]];
				
				//Get the actual voice number and save to j
				while(j < 4 && t < ends[j]) j++;
				if(j >= 4){
					//Too many voices? This shouldn't happen.
					throw new NUSALSeq.TooManyVoicesException("NUSALSeqBuilderPhrase.assignToVoices || "
							+ "Tried to assign note to voice, but all voices are in use!");
				}
				
				Map<Integer, NUSALSeqBuilderNote> map = voice_notes.get(j);
				map.put(t, note);
				last[j] = (int)note.getNote();
				ends[j] = t + note.getLengthInTicks();
			}
		}
	}
	
	public Map<Integer, NUSALSeqBuilderNote> getVoiceAssignment(int vox_idx){
		if(voice_notes == null) assignToVoices();
		if(vox_idx < 0 || vox_idx >= 4) return null;
		return voice_notes.get(vox_idx);
	}
	
	public NUSALSeqBuilderPhrase[] getVoiceAssignments(){
		NUSALSeqBuilderPhrase[] out = new NUSALSeqBuilderPhrase[4];
		if(voice_notes == null) assignToVoices();
		for(int i = 0; i < 4; i++){
			Map<Integer, NUSALSeqBuilderNote> map = voice_notes.get(i);
			if(map == null || map.isEmpty()) continue;
			out[i] = new NUSALSeqBuilderPhrase();
			List<Integer> keys = new ArrayList<Integer>(map.size());
			keys.addAll(map.keySet());
			for(Integer k : keys){
				out[i].addNote(k, map.get(k));
			}
		}
		
		return out;
	}
	
	private int determinePitchOffset(){
		Set<Byte> pitches = notes.getAllPitches();
		LinkedList<Byte> sorted = new LinkedList<Byte>();
		sorted.addAll(pitches);
		Collections.sort(sorted);
		
		int shamt = 0;
		if(sorted.getFirst() < 0x15){
			shamt = (int)sorted.getFirst() - 0x15;
			//Make sure that didn't shift out the top notes.
			int newtop = 0x54 + shamt;
			if(sorted.getLast() > newtop) return Integer.MAX_VALUE;
			return shamt;
		}
		if(sorted.getLast() > 0x54){
			shamt = sorted.getLast() - 0x54;
			
			int newbot = 0x15 + shamt;
			if(sorted.getFirst() < newbot) return Integer.MAX_VALUE;
			return shamt;
		}
		
		return 0;
	}
	
	CommandChunk toVoiceCommandChunk(){
		//Because it's assuming phrase has been reduced to one voice, it will only take the first command of each event group
		int last_time = -1;
		byte last_gate = -1;
		
		int t_last = 0; //Time the last event ENDED on. Used to insert rests.
		int[] times = notes.getTimeCoords();
		CommandChunk chunk = new CommandChunk();
		
		//Detect if pitch offset is needed
		//(Basically need to shift down if there are notes below 21 and up if above 84)
		int pitch_off = determinePitchOffset();
		//If it's int max then it goes both above and below limit and must be changed dynamically.
		boolean single_pshift = (pitch_off != Integer.MAX_VALUE) && (pitch_off != 0);
		if(single_pshift){
			//Issue a pitch shift command
			BuilderCommand cmd = new BuilderGenericCommand(NUSALSeqCmdType.TRANSPOSE, 2);
			cmd.setParam(0, pitch_off);
			chunk.addCommand(cmd);
		}
		boolean dynamic_transpose = pitch_off == Integer.MAX_VALUE;
		if(dynamic_transpose) pitch_off = 0;
		int min_note = 0x15 + pitch_off;
		int max_note = 0x54 + pitch_off;
		
		//Go through events.
		for(int i = 0; i < times.length; i++){
			int t = times[i];
			if(t_last < t){
				//Insert rest.
				int rest_time = t_last - t;
				chunk.addCommand(new BuilderWaitCommand(rest_time, true));
			}
			//Get the note for this time.
			NUSALSeqBuilderNote mynote = notes.getNotesAt(t).get(0);
			BuilderNoteCommand notecmd = null;
			
			//Determine if need to change the transposition
			if(dynamic_transpose){
				boolean changed = false;
				if(mynote.getNote() < min_note){
					pitch_off = (int)mynote.getNote() - min_note;
					changed = true;
				}
				else if(mynote.getNote() > max_note){
					pitch_off = (int)mynote.getNote() - max_note;
					changed = true;
				}
				if(changed){
					min_note = 0x15 + pitch_off;
					max_note = 0x54 + pitch_off;
					BuilderCommand cmd = new BuilderGenericCommand(NUSALSeqCmdType.TRANSPOSE, 2);
					cmd.setParam(0, pitch_off);
					chunk.addCommand(cmd);
				}
			}
			
			//Determine if we can forego time or gate
			if(mynote.getLengthInTicks() == last_time){
				//NVG
				notecmd = new BuilderNoteCommand(mynote.getNote(), pitch_off, mynote.getVelocity(), mynote.getGate());
			}
			else if(mynote.getGate() == last_gate){
				//NVT
				notecmd = new BuilderNoteCommand(mynote.getNote(), pitch_off, mynote.getLengthInTicks(), mynote.getVelocity());
			}
			else{
				//Do all params
				notecmd = new BuilderNoteCommand(mynote.getNote(), pitch_off, 
						mynote.getLengthInTicks(), mynote.getVelocity(), mynote.getGate());
			}
			last_time = mynote.getLengthInTicks();
			last_gate = mynote.getGate();
			
			chunk.addCommand(notecmd);
		}
		
		//Handle end of phrase (do a transpose back to 0 if needed, then end/return command)
		if(pitch_off != 0){
			BuilderCommand cmd = new BuilderGenericCommand(NUSALSeqCmdType.TRANSPOSE, 2);
			cmd.setParam(0, 0);
			chunk.addCommand(cmd);
		}
		BuilderCommand cmd = new BuilderGenericCommand(NUSALSeqCmdType.END_READ, 1);
		chunk.addCommand(cmd);
		
		return chunk;
	}
	
}
