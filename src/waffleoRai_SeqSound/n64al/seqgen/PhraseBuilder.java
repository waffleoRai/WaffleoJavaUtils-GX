package waffleoRai_SeqSound.n64al.seqgen;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.TreeMap;
import java.util.TreeSet;

public class PhraseBuilder {
	
	/*----- Constants -----*/
	
	public static final int OPT_LEVEL_0 = 0; //Just builds phrases based on first it sees
	public static final int OPT_LEVEL_1 = 1; //Start to end, optimizes earlier phrases then works with that
	public static final int OPT_LEVEL_2 = 2; //Slowest - tries to find optimal combination. May be impractical for long seqs
	
	/*----- Instance Variables -----*/
	
	private int time_leeway; //In ticks
	private int vel_leeway;
	private boolean one_voice_match; //Otherwise all voices much match to create phrase.
	private int optimization;
	
	private int min_time; //Min ticks to create phrase
	private int min_events; //Min events to create phrase
	private int max_rest;
	
	//Phrases cannot straddle these!
	private Set<Integer> seg_times;
	private boolean seg_remove_all; 
	//If a phrase sits on a segment border, remove the phrase altogether. 
	// Otherwise, only remove instances of the phrase where it bridges a border
	
	private Writer log_stream;
	
	//Temporary
	private NoteMap chdat;
	private Map<Integer, NUSALSeqBuilderNote[]> nowmap;
	private int[][] event_tbl; 
	//[n][0] is tick value, [n][1] is active voices, [n][2] is event count
	
	/*----- Inner Classes -----*/
	
	protected static class PhraseStack{
	
		private NUSALSeqBuilderPhrase[] phrases;
		private int[][] isect_tbl;
		
		private Stack<Integer> stack;
		private int val;
		
		private Set<Integer> bestset;
		private int bestval;
		
		public PhraseStack(NUSALSeqBuilderPhrase[] parr){
			phrases = parr;
			stack = new Stack<Integer>();
			val = 0;
			bestset = new TreeSet<Integer>();
			bestval = 0;
			
			isect_tbl = new int[parr.length][parr.length];
			for(int i = 0; i < parr.length; i++){
				for(int j = 0; j < parr.length; j++){
					isect_tbl[i][j] = -1;
				}
			}
		}
		
		public int getCurrentValue(){return val;}
		public boolean isEmpty(){return stack.isEmpty();}
		public NUSALSeqBuilderPhrase[] getPhraseArray(){return phrases;}
		
		public void push(int i){
			if(i < 0 || i >= phrases.length) return;
			stack.push(i);
			val += phrases[i].eventsSaved();
		}
		
		public int pop(){
			if(stack.isEmpty()) return -1;
			int i = stack.pop();
			val -= phrases[i].eventsSaved();
			return i;
		}
		
		public Set<Integer> getBestSet(){return bestset;}
		public int getBestSetValue(){return bestval;}
		
		public boolean checkIntersect(int phrase_index){
			if(phrase_index < 0 || phrase_index >= phrases.length) return false;
			
			for(Integer idx : stack){
				//See isect already mapped.
				int isect_val = isect_tbl[idx][phrase_index];
				switch(isect_val){
				case 0: continue; //No intersect. Continue.
				case 1: return true;
				case -1:
					boolean b = phrases[idx].intersects(phrases[phrase_index]);
					if(b){
						isect_tbl[idx][phrase_index] = 1;
						isect_tbl[phrase_index][idx] = 1;
						return true;
					}
					else{
						isect_tbl[idx][phrase_index] = 0;
						isect_tbl[phrase_index][idx] = 0;
					}
					break;
				}
			}
			
			return false;
		}
			
		public void setCurrentAsBest(){
			bestset.clear();
			bestset.addAll(stack);
			bestval = val;
		}
		
		public int getPhraseCount(){
			return phrases.length;
		}
		
	}
	
	/*----- Init -----*/
	
 	public PhraseBuilder(){
 		seg_times = new TreeSet<Integer>();
		resetDefaults();
	}
	
	public void resetDefaults(){
		time_leeway = 4;
		vel_leeway = 5;
		one_voice_match = true;
		min_time = 48;
		min_events = 4;
		optimization = OPT_LEVEL_1;
		seg_times.clear();
		seg_remove_all = false;
		max_rest = 127;
	}
	
	/*----- Getters -----*/
	
	public int getTimeLeeway(){return time_leeway;}
	public int getVelocityLeeway(){return vel_leeway;}
	public boolean voiceMatchMode(){return one_voice_match;}
	public int getOptimizationLevel(){return optimization;}
	public int getPhraseMinTime(){return min_time;}
	public int getPhraseMinEvents(){return min_events;}
	public int getMaxRestTime(){return max_rest;}
	
	public List<Integer> getBoundaryTimes(){
		List<Integer> list = new ArrayList<Integer>(seg_times.size()+1);
		list.addAll(seg_times);
		Collections.sort(list);
		return list;
	}
	
	/*----- Setters -----*/
	
	public void setTimeLeeway(int ticks){time_leeway = ticks;}
	public void setVelocityLeeway(int vel_amt){vel_leeway = vel_amt;}
	public void setVoiceMatchMode(boolean onevoice){one_voice_match = onevoice;}
	public void setPhraseMinTime(int ticks){min_time = ticks;}
	public void setPhraseMinEvents(int events){min_events = events;}
	//public void setLoopStart(int ticks){loop_start = ticks;}
	//public void setLoopEnd(int ticks){loop_end = ticks;}
	public void addSegmentBorder(int tick){seg_times.add(tick);}
	public void clearSegmentBorders(){seg_times.clear();}
	public void setIgnoreAllPhraseInstancesOnBorders(boolean on){seg_remove_all = on;}
	public void setMaxRestTime(int ticks){max_rest = ticks;}
	
	public void setOptimizationLevel(int lvl){
		if(lvl < 0) lvl = 0;
		if(lvl > 2) lvl = 2;
		optimization = lvl;
	}
	
	/*----- Logging -----*/
	
	public Writer getLogWriter(){return log_stream;}
	public void setLogWriter(Writer writer){log_stream = writer;}
	
	public void setLogStream(OutputStream stream){
		log_stream = new BufferedWriter(new OutputStreamWriter(stream));
	}
	
	public void openLogWriter(String path) throws IOException{
		log_stream = new BufferedWriter(new FileWriter(path));
	}
	
	public void closeLogWriter() throws IOException {
		if(log_stream != null) log_stream.close(); 
		log_stream = null;
	}
	
	protected void writeLogLine(String message){
		writeLogLine(null, message, false);
	}
	
	protected void writeLogLine(String method_name, String message, boolean timestamp){
		if(log_stream == null) return;
		try{
			if(timestamp){
				ZonedDateTime time = ZonedDateTime.now();
				log_stream.write("[");
				log_stream.write(time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME).replace("T", " "));
				log_stream.write("] ");
			}
			if(method_name != null){
				log_stream.write(this.getClass().getName());
				log_stream.write(".");
				log_stream.write(method_name);
				log_stream.write(" || ");
			}
		
			log_stream.write(message);
			log_stream.write("\n");
		}
		catch(IOException ex){
			System.err.println("Log write failed. Message: " + message);
			ex.printStackTrace();
		}
	}
	
	public void writeSettingsToLog(){
		if(log_stream == null) return;
		writeLogLine(null, "Settings for PhraseBuilder " + this.toString(), true);
		writeLogLine("\tOptimization Level: " + this.optimization);
		writeLogLine("\tNote Match Time Leeway: " + this.time_leeway + " ticks");
		writeLogLine("\tNote Match Velocity Leeway: " + this.vel_leeway);
		if(one_voice_match) writeLogLine("\tVoice Match Mode: OR");
		else writeLogLine("\tVoice Match Mode: AND");
		writeLogLine("\tPhrase Minimum Time: " + this.min_time + " ticks");
		writeLogLine("\tPhrase Minimum Size: " + this.min_events + " events");
		writeLogLine("\tPhrase Maximum Rest: " + this.max_rest + " ticks");
		writeLogLine("\tConsider Phrases w/ Boundary-Cross Instance: " + !this.seg_remove_all);
		writeLogLine("\tSegment Boundary Times:");
		for(Integer n : seg_times) writeLogLine("\t\t" + n);
	}
	
	/*----- Compression Functions -----*/
	
	//	--> State Setting
	
	private void buildEventTable(NoteMap notemap){
		final String method_name = "buildEventTable";
		writeLogLine(method_name, "Analyzing input events...", true);
		
		chdat = notemap;
		nowmap = new TreeMap<Integer, NUSALSeqBuilderNote[]>();
		int[] times = notemap.getTimeCoords();
		
		int ecount = times.length;
		event_tbl = new int[ecount][3];
		for(int i = 0; i < ecount; i++) event_tbl[i][0] = times[i];
		
		int ch_end = 0;
		for(int i = 0; i < ecount; i++){
			int tick = event_tbl[i][0];
			
			NUSALSeqBuilderNote[] arr = notemap.getNoteGroupAt(tick);
			nowmap.put(tick, arr);
			event_tbl[i][2] = arr.length;
			event_tbl[i][1] += event_tbl[i][2];
			for(NUSALSeqBuilderNote n : arr){
				//Add this voice to all subsequent note on ticks it covers
				int end_tick = n.getLengthInTicks() + tick;
				if(end_tick > ch_end) ch_end = end_tick;
				for(int j = i; j < ecount; j++){
					if(event_tbl[j][0] >= end_tick) break;
					event_tbl[j][1]++;
				}
			}
		}
		
		if(log_stream != null){
			try{
				int evs = 0;
				int maxvox = 0;
				int timecount = event_tbl.length;
				for(int i = 0; i < timecount; i++){
					evs += event_tbl[i][2];
					if(event_tbl[i][1] > maxvox) maxvox = event_tbl[i][1];
				}
				
				log_stream.write("Found " + evs + " note events called from " + timecount + " time points\n");
				log_stream.write("Total time: " + ch_end + " ticks\n");
				log_stream.write("Max Simultaneous Voices: " + maxvox + "\n");
			}
			catch(IOException ex){ex.printStackTrace();}
		}
		
	}
	
	private void clearBuilderTables(){
		nowmap = null;
		event_tbl = null;
		if(chdat != null) chdat.clearPhraseAnnotations();
		chdat = null;
	}
	
	//	--> Common Utility
	
	private int findScanStartIndex(int idx){
		int mintick = event_tbl[idx][0] + min_time;
		int events = event_tbl[idx][2];
		for(int i = idx+1; i < event_tbl.length; i++){
			events += event_tbl[i][2];
			if(event_tbl[i][0] >= mintick){
				if(events >= min_events) return i;
			}
		}
		return -1;
	}
	
	private boolean groupsMatch_voxAND(NUSALSeqBuilderNote[] group1, NUSALSeqBuilderNote[] group2){
		if(group1.length != group2.length) return false;
		for(int j = 0; j < group1.length; j++){
			if(!group1[j].equals(group2[j])){
				//Check if close.
				//If not, return false
				if(group1[j].getNote() != group2[j].getNote()) return false;
				byte v1 = group1[j].getVelocity();
				byte v2 = group2[j].getVelocity();
				if(v1 > v2){
					byte temp = v1;
					v1 = v2; v2 = temp;
				}
				if(v2 > (v1 + vel_leeway)) return false;
				int t1 = group1[j].getLengthInTicks();
				int t2 = group2[j].getLengthInTicks();
				if(t1 > t2){
					int temp = t1;
					t1 = t2; t2 = temp;
				}
				if(t2 > (t1 + time_leeway)) return false;
			}
		}
		return false;
	}
	
	private boolean groupsMatch_voxOR(NUSALSeqBuilderNote[] group1, NUSALSeqBuilderNote[] group2){
		for(int i = 0; i < group1.length; i++){
			//Look for a common note in group 2
			for(int j = 0; j < group2.length; j++){
				if(group1[i].equals(group2[j])) return true;
				//See if similar enough
				if(group1[i].getNote() == group2[j].getNote()){
					byte v1 = group1[i].getVelocity();
					byte v2 = group2[j].getVelocity();
					if(v1 > v2){
						byte temp = v1;
						v1 = v2; v2 = temp;
					}
					if(v2 > (v1 + vel_leeway)) continue; //Try next note.
					
					int t1 = group1[i].getLengthInTicks();
					int t2 = group2[j].getLengthInTicks();
					if(t1 > t2){
						int temp = t1;
						t1 = t2; t2 = temp;
					}
					if(t2 > (t1 + time_leeway)) continue;
					return true;
				}
			}
		}
		return false;
	}
	
	protected boolean groupsMatch(NUSALSeqBuilderNote[] group1, NUSALSeqBuilderNote[] group2){
		if(group1 == null || group2 == null) return false;
		if(!one_voice_match){
			return groupsMatch_voxAND(group1, group2);
		}
		else{
			//Only one voice needs to be in common.
			return groupsMatch_voxOR(group1, group2);
		}
	}
	
	protected void addCommonToPhrase(int coord, NUSALSeqBuilderNote[] group1, NUSALSeqBuilderNote[] group2, NUSALSeqBuilderPhrase phrase){
		for(int i = 0; i < group1.length; i++){
			NUSALSeqBuilderNote n1 = group1[i];
			for(int j = 0; j < group2.length; j++){
				NUSALSeqBuilderNote n2 = group2[j];
				if(n1.equals(n2)){
					phrase.addNote(coord, n1);
					break;
				}
				else{
					//Check if similar
					if(n1.getNote() == n2.getNote()){
						int vdiff = Math.abs((int)n1.getVelocity() - (int)n2.getVelocity());
						if(vdiff <= vel_leeway){
							phrase.addNote(coord, n1);
							break;
						}
						int tdiff = Math.abs(n1.getLengthInTicks() - n2.getLengthInTicks());
						if(tdiff <= time_leeway){
							phrase.addNote(coord, n1);
							break;
						}
					}
				}
			}
		}
		
	}
	
	protected boolean phraseClearsBorders(NUSALSeqBuilderPhrase phrase){
		if(seg_times.isEmpty()) return true;
		if(!phrase.hasAnnotatedCoordinates()) findPhrase(phrase, false, false);
		int[] starts = phrase.getPhraseCoords();
		if(starts == null) return true;
		int plen = phrase.getLengthInTicks();
		for(int i = 0; i < starts.length; i++){
			int st = starts[i];
			int ed = st + plen;
			for(Integer border : seg_times){
				if(border > st && border < ed) return false;
			}
		}
		return true;
	}
	
	//	--> Find Phrase Instances
	
	private void findPhrase_voxAND(NUSALSeqBuilderPhrase phrase, boolean flagNotes, boolean removeBoundaryConflicts){
		
		//Get the phrase-relative note group coordinates
		int[] no_coords = phrase.getNoteonCoords();
		if(no_coords == null) return;
		
		//If we need to flag the notes, we have to re-find them anyway.
		if(flagNotes) phrase.clearStartCoords();
		
		//Allocate some variables for use in loop
		NUSALSeqBuilderNote first_note = null;
		List<NUSALSeqBuilderNote> flag_notes = new LinkedList<NUSALSeqBuilderNote>();
		
		//There's an extra check for this, but no point in going past the point where there isn't enough room left for phrase
		int max_start_idx = event_tbl.length - no_coords.length + 1;
		for(int i = 0; i < max_start_idx; i++){
			//For each event group in examining channel
			
			int sttick = event_tbl[i][0];
			boolean match = true;
			
			//Refresh flag queue
			if(flagNotes){
				first_note = null;
				flag_notes.clear();
			}
			
			//Scan j event groups forward from i up to length of phrase or end of channel
			for(int j = 0; j < no_coords.length; j++){
				int e = i+j;
				if(e >= event_tbl.length){match = false; break;} //No match if hit end of channel before match full phrase
				
				int t1 = event_tbl[e][0];
				
				//Check that starting points of notes relative to phrase start are close enough
				int d1 = t1 - sttick;
				int d2 = no_coords[j];
				int dd = Math.abs(d1 - d2);
				if(dd > time_leeway){match = false; break;}
				
				//Compare groups
				NUSALSeqBuilderNote[] group1 = nowmap.get(t1);
				NUSALSeqBuilderNote[] group2 = phrase.getNotesAt(d2);
				
				if(!groupsMatch(group1, group2)){match = false; break;}
				else{
					//Check for borders...
					if(removeBoundaryConflicts){
						for(Integer border : seg_times){
							if(border <= sttick) continue;
							if(border >= t1){match = false; break;}
						}
						if(!match)break;	
					}
					
					//Queue for flagging if phrase match occurs.
					if(flagNotes){
						for(NUSALSeqBuilderNote n : group1) flag_notes.add(n);
						if(j == 0) first_note = group1[0];
					}
				}
			}
			
			//It's passed. There is a valid phrase instance here.
			if(match){
				phrase.addStartCoord(sttick);
				
				//Properly flag the notes
				if(flagNotes){
					for(NUSALSeqBuilderNote note: flag_notes) note.linkPhrase(phrase, false);
					first_note.flagFirstInPhrase(true);
				}
			}
		}
	}
	
	private void findPhrase_voxOR(NUSALSeqBuilderPhrase phrase, boolean flagNotes, boolean removeBoundaryConflicts){
		
		//Get the phrase-relative note group coordinates
		int[] no_coords = phrase.getNoteonCoords();
		if(no_coords == null) return;
		
		//If we need to flag the notes, we have to re-find them anyway.
		if(flagNotes) phrase.clearStartCoords();
		
		//Allocate some variables for use in loop
		NUSALSeqBuilderNote first_note = null;
		List<NUSALSeqBuilderNote> flag_notes = new LinkedList<NUSALSeqBuilderNote>();
		
		//There's an extra check for this, but no point in going past the point where there isn't enough room left for phrase
		int max_start_idx = event_tbl.length - no_coords.length;
		for(int e = 0; e < max_start_idx; e++){
			
			int sttick = event_tbl[e][0];
			
			//Refresh flag queue
			if(flagNotes){
				first_note = null;
				flag_notes.clear();
			}

			//Check 0-0 match
			//If these do not match, no point in continuing with this test start
			NUSALSeqBuilderNote[] group1 = nowmap.get(sttick);
			NUSALSeqBuilderNote[] group2 = phrase.getNotesAt(0);
			if(!groupsMatch(group1, group2)) continue;
			if(flagNotes){
				boolean first = true;
				for(NUSALSeqBuilderNote n : group1){
					for(NUSALSeqBuilderNote ref : group2){
						if(ref.equals(n)){
							//n.linkPhrase(phrase, first);
							if(first) first_note = n;
							flag_notes.add(n);
							first = false;
							break;
						}
					}
				}
			}
			
			int i = e+1; //Groups from test start
			int j = 1; //Groups from phrase start
			int mcount = 0; //Matching groups. Must be equal to #groups in phrase at end to be full match.
			int emax = event_tbl.length - no_coords.length + 1;
			while(j < no_coords.length && i < emax){
				
				//Check times at i and j
				//Try to line up start times by tweaking i and j.
				int ti = event_tbl[i][0];
				int tj = no_coords[j];
				
				int di = ti - sttick;
				int dj = tj;
				int dd = Math.abs(di - dj);
				if(dd > time_leeway){
					//These events are too far apart.
					//Move up i or j (whichever is behind in time)
					if(di < dj) i++;
					else if(dj < di) j++;
					continue;
				}
				
				//Try to compare these groups.
				group1 = nowmap.get(ti);
				group2 = phrase.getNotesAt(tj);
				
				if(groupsMatch(group1, group2)){
					mcount++;
					i++; j++;
					
					//Check if this potential phrase instance straddles any hard boundaries
					if(removeBoundaryConflicts){
						boolean match = true;
						for(Integer border : seg_times){
							if(border <= sttick) continue;
							if(border >= ti){match = false; break;}
						}
						if(!match)break;
					}
					
					if(flagNotes){
						//Set aside notes for flagging if phrase match is successful.
						for(NUSALSeqBuilderNote n : group1){
							for(NUSALSeqBuilderNote ref : group2){
								if(ref.equals(n)){
									//n.linkPhrase(phrase, false);
									flag_notes.add(n);
									break;
								}
							}
						}
					}
				}
				else{
					//Increment only the lagging one.
					if(di < dj) i++;
					else if(dj < di) j++;
				}
				
			}
			
			if(mcount == no_coords.length){
				//Match.
				phrase.addStartCoord(sttick);
				
				//Properly flag the notes
				if(flagNotes){
					for(NUSALSeqBuilderNote note: flag_notes) note.linkPhrase(phrase, false);
					first_note.flagFirstInPhrase(true);
				}
			}
		}
	}
	
	protected void findPhrase(NUSALSeqBuilderPhrase phrase, boolean flagNotes, boolean removeBoundaryConflicts){
		//Finds the phrase in the channel and annotates it with start points
		int[] no_coords = phrase.getNoteonCoords();
		if(no_coords == null) return;
		if(!one_voice_match){
			//Perfect matches
			findPhrase_voxAND(phrase, flagNotes, removeBoundaryConflicts);
		}
		else{
			findPhrase_voxOR(phrase, flagNotes, removeBoundaryConflicts);
		}
	}

	//	--> Build Phrases
	
	private List<NUSALSeqBuilderPhrase> getPhrasesAt_voxAND(int ontick_idx, boolean breakAtOne){
		//Construct output container
		List<NUSALSeqBuilderPhrase> list = new LinkedList<NUSALSeqBuilderPhrase>();
		
		int mytick = event_tbl[ontick_idx][0];
		//Scan for start (must be at least min_time AND min_events forward)
		int next_idx = findScanStartIndex(ontick_idx);
		
		while(next_idx < event_tbl.length){
			//Is there a common phrase starting at mytick and this tick? If so, how long is it in common?
			int elen = Math.min(next_idx - ontick_idx, event_tbl.length - next_idx); 
			//Max phrase size in note-on groups. End of phrase cannot go beyond length of this later instance or end of seq.
			
			int nexttick = event_tbl[next_idx][0];
			
			NUSALSeqBuilderPhrase phrase = null;
			int p_end = 0;
			for(int i = 0; i < elen; i++){
				//Compare event groups i ticks from start.
				int t1 = event_tbl[ontick_idx+i][0];
				int t2 = event_tbl[next_idx+i][0];
				
				//Are start times relative to putative phrase start close enough?
				int d1 = mytick - t1;
				int d2 = nexttick - t2;
				int dd = Math.abs(d2 - d1);
				if(dd > time_leeway) break;
				
				//Check the rest time.
				//How long has it been since last note off?
				//If above max rest time, end the phrase.
				if((t1 - p_end) > max_rest) break;
				
				//Compare the actual groups
				NUSALSeqBuilderNote[] group1 = nowmap.get(t1);
				NUSALSeqBuilderNote[] group2 = nowmap.get(t2);
				if(groupsMatch(group1, group2)){
					//Add to phrase (or create if not instantiated)
					if(phrase == null) phrase = new NUSALSeqBuilderPhrase();
					for(int j = 0; j < group1.length; j++){
						//Add to phrase (coordinates are relative to phrase)
						phrase.addNote(d1, group1[j]);
						
						//Update coordinate of phrase end
						int noteend = t1 + group1[j].getLengthInTicks();
						if(noteend > p_end) p_end = noteend;
					}
				}
				else break;
			}
			
			//Add to output list if long enough.
			if(phrase != null){
				if(phrase.getLengthInEvents() >= min_events && phrase.getLengthInTicks() >= min_time){
					
					//Check for seq segmentation conflicts
					findPhrase(phrase, false, !seg_remove_all);
					if(seg_remove_all && !phraseClearsBorders(phrase)) phrase.clearStartCoords();
					
					//If phrase passes seg check (instances left without conflicts), add.
					if(phrase.hasAnnotatedCoordinates()){
						list.add(phrase);
						if(breakAtOne) return list;	
					}
				}
			}
			
			//And don't forget to increment!
			next_idx++;
		}
		
		return list;
	}
	
	private List<NUSALSeqBuilderPhrase> getPhrasesAt_voxOR(int ontick_idx, boolean breakAtOne){
		//Construct output container
		List<NUSALSeqBuilderPhrase> list = new LinkedList<NUSALSeqBuilderPhrase>();
		
		int mytick = event_tbl[ontick_idx][0];
		//Scan for start (must be at least min_time AND min_events forward)
		int next_idx = findScanStartIndex(ontick_idx);
		
		while(next_idx < event_tbl.length){
			//Is there a common phrase starting at mytick and this tick? If so, how long is it in common?
			
			//First, compare the starting groups.
			//If these don't match, no point in continuing with this combo.
			//(Don't need to compare start times since they would both be 0 phrase-relative)
			int t1 = event_tbl[ontick_idx][0];
			int t2 = event_tbl[next_idx][0];
			NUSALSeqBuilderNote[] group1 = nowmap.get(t1);
			NUSALSeqBuilderNote[] group2 = nowmap.get(t2);
			if(!groupsMatch(group1, group2)) {next_idx++; continue;}
			
			int nexttick = event_tbl[next_idx][0];
			
			//Determine ends of space we have to work with on each side
			int i_max = next_idx - ontick_idx; //Earlier instance cannot go beyond later's start
			int j_max = event_tbl.length - next_idx; //Later instance cannot go beyond seq end
			
			NUSALSeqBuilderPhrase phrase = new NUSALSeqBuilderPhrase();
			//Put common notes from first event in phrase.
			addCommonToPhrase(0, group1, group2, phrase);
			int p_len = phrase.getLengthInTicks();
			
			int i = 1; //Event groups from ref (earlier) time coordinate
			int j = 1; //Event groups from test (later) time coordinate
			while(j < j_max && i < i_max){
				//Commented these out because I think they are redundant to while condition
				//if((ontick_idx + i) >= next_idx) break;
				//if((next_idx + j) >= event_tbl.length) break;
				
				t1 = event_tbl[ontick_idx+i][0];
				t2 = event_tbl[next_idx+j][0];
				
				//See if these events are close enough.
				int d1 = mytick - t1;
				int d2 = nexttick - t2;
				int dd = Math.abs(d2 - d1);
				if(dd > time_leeway){
					//Because this is OR mode (only one voice need match)
					//	need to account for small mis-match inserts that can be put on a voice outside phrase
					if(d2 > d1) i++;
					else if (d1 > d2) j++;
					continue;
				}
				
				//Check rest length
				if((t1 - p_len) > max_rest) break;
				
				//Now actual note match check
				group1 = nowmap.get(t1);
				group2 = nowmap.get(t2);
				if(!groupsMatch(group1, group2)){
					if(d2 > d1) i++;
					else if (d1 > d2) j++;
				}
				else{
					addCommonToPhrase(d1, group1, group2, phrase);
					i++; j++;
					p_len = phrase.getLengthInTicks();
				}
			}
			
			//Add to output list if long enough.
			if(phrase.getLengthInEvents() >= min_events && phrase.getLengthInTicks() >= min_time){
				//Check for seq segmentation conflicts
				findPhrase(phrase, false, !seg_remove_all);
				if(seg_remove_all && !phraseClearsBorders(phrase)) phrase.clearStartCoords();
				
				//If phrase passes seg check (instances left without conflicts), add.
				if(phrase.hasAnnotatedCoordinates()){
					list.add(phrase);
					if(breakAtOne) return list;	
				}
			}
			
			//And don't forget to increment!
			next_idx++;
		}
		
		return list;
	}
	
	protected List<NUSALSeqBuilderPhrase> getPhrasesAt(int ontick_idx, boolean breakAtOne){
		if(ontick_idx < 0 || ontick_idx > event_tbl.length) return null;
		
		if(!one_voice_match){
			//Must be perfect match
			return getPhrasesAt_voxAND(ontick_idx, breakAtOne);
		}
		else{
			return getPhrasesAt_voxOR(ontick_idx, breakAtOne);
		}
	}
	
	protected List<NUSALSeqBuilderPhrase> getPossiblePhrases(){
		List<NUSALSeqBuilderPhrase> biglist = new LinkedList<NUSALSeqBuilderPhrase>();
		for(int i = 0; i < event_tbl.length; i++){
			List<NUSALSeqBuilderPhrase> list = getPhrasesAt(i, false);
			if(list != null){
				for(NUSALSeqBuilderPhrase p : list){
					//If already in biglist, then skip.
					boolean addme = true;
					for(NUSALSeqBuilderPhrase o : biglist){
						if(phrasesMatch(o, p)){addme = false; break;}
					}
					if(!addme) continue;
					
					//Call findPhrase (without anno)
					//findPhrase(p, false);
					//Update: don't need to do this - getPhrasesAt_vox# does this already to toss phrases across seg borders
					
					//Add to biglist
					biglist.add(p);
				}
			}
		}
		return biglist;
	}
	
	//	--> Compression Wrapper Methods
	
	protected MusicEventMap compressMusicEvents0(){
		final String method_name = "compressMusicEvents0";
		
		chdat.clearPhraseAnnotations();
		//Store these
		NoteMap chdat_st = chdat;
		Map<Integer, NUSALSeqBuilderNote[]> nowmap_st = nowmap;
		int[][] event_tbl_st = event_tbl;
		writeLogLine(method_name, "Builder backup state stored.", true);
		
		int ecount = event_tbl.length;
		for(int e = 0; e < ecount; e++){
			if(chdat.isEmpty()) break; //Everything has been assigned already.
			int etick = event_tbl_st[e][0];
			if(!chdat.hasNotesAt(etick)) continue;
			
			//Get the first phrase match available at this tick
			List<NUSALSeqBuilderPhrase> plist = getPhrasesAt(etick, true);
			if(plist == null || plist.isEmpty()) continue;
			NUSALSeqBuilderPhrase phrase = plist.get(0);
			
			//Mark notes in this phrase
			//Then remove from temporary table so invisible to next round.
			findPhrase(phrase, true, true);
			buildEventTable(chdat.copyWithoutPhrasedNotes());
		}
		
		//Restore temp instance variables
		chdat = chdat_st;
		nowmap = nowmap_st;
		event_tbl = event_tbl_st;
		
		//Because notes are persistent objects, this method should be able to condense them back into annotated phrases
		writeLogLine(method_name, "Pattern scan complete. Organizing output.", true);
		return chdat.condense();
	}
	
	protected MusicEventMap compressMusicEvents1(){
		final String method_name = "compressMusicEvents1";
		
		chdat.clearPhraseAnnotations();
		//Store these
		NoteMap chdat_st = chdat;
		Map<Integer, NUSALSeqBuilderNote[]> nowmap_st = nowmap;
		int[][] event_tbl_st = event_tbl;
		writeLogLine(method_name, "Builder backup state stored.", true);
		
		int ecount = event_tbl.length;
		for(int e = 0; e < ecount; e++){
			if(chdat.isEmpty()) break; //Everything has been assigned already.
			int etick = event_tbl_st[e][0];
			if(!chdat.hasNotesAt(etick)) continue;
			
			//Get all possible phrases starting at current time coordinate
			List<NUSALSeqBuilderPhrase> plist = getPhrasesAt(etick, false);
			if(plist == null || plist.isEmpty()) continue;
			
			NUSALSeqBuilderPhrase phrase = null;
			int esave = -1;
			//Find the one that saves the most events.
			for(NUSALSeqBuilderPhrase p : plist){
				int pesave = p.eventsSaved();
				if(pesave > esave){
					esave = pesave;
					phrase = p;
				}
			}
			if(phrase == null) continue;
			
			//Phrase passed. Annotate it and temporarily remove included notes.
			findPhrase(phrase, true, true);
			buildEventTable(chdat.copyWithoutPhrasedNotes());
		}
		
		//Restore temp instance variables
		chdat = chdat_st;
		nowmap = nowmap_st;
		event_tbl = event_tbl_st;
		
		writeLogLine(method_name, "Pattern scan complete. Organizing output.", true);
		return chdat.condense();
	}
	
	private void findBestCombo(PhraseStack stack, int stidx){
		int pcount = stack.getPhraseCount();
		Set<Integer> best = new TreeSet<Integer>();
		best.addAll(stack.stack);
		int bestval = stack.getCurrentValue();
		
		for(int i = stidx; i < pcount; i++){
			if(stack.checkIntersect(i)) continue;
			stack.push(i);
			findBestCombo(stack, i+1);
			if(stack.getBestSetValue() >= bestval){
				best.addAll(stack.getBestSet());
				bestval = stack.getBestSetValue();
			}
			stack.pop();
		}
		
		stack.bestset.clear();
		stack.bestset.addAll(best);
		stack.bestval = bestval;
	}
	
	protected MusicEventMap compressMusicEvents2(){
		final String method_name = "compressMusicEvents2";
		
		chdat.clearPhraseAnnotations();
		writeLogLine(method_name, "Gathering all possible phrases...", true);
		List<NUSALSeqBuilderPhrase> allphrase = getPossiblePhrases();
		if(allphrase.isEmpty()){
			writeLogLine(method_name, "No patterns found!", true);
			return chdat.condense();
		}
		
		//Reorganize phrases into an array
		int pcount = allphrase.size();
		NUSALSeqBuilderPhrase[] parr = new NUSALSeqBuilderPhrase[pcount];
		int i = 0;
		for(NUSALSeqBuilderPhrase p : allphrase) parr[i++] = p;
		writeLogLine(method_name, "Possible phrases found: " + pcount, true);
		
		//Called a recursive optimizer to try all combos.
		//It's a terrible idea but wth, this is the "high opt slow run" anyway
		writeLogLine(method_name, "Now trying all phrase combinations...", true);
		PhraseStack stack = new PhraseStack(parr);
		findBestCombo(stack, 0);
		Set<Integer> best = stack.getBestSet();
		for(Integer idx : best){
			findPhrase(parr[idx], true, true);
		}
		
		writeLogLine(method_name, "Pattern scan complete. Organizing output.", true);
		return chdat.condense();
	}
	
	public boolean phrasesMatch(NUSALSeqBuilderPhrase phrase1, NUSALSeqBuilderPhrase phrase2){
		if(phrase1 == null && phrase2 == null) return true;
		if(phrase1 == null || phrase2 == null) return false;
		
		int[] times1 = phrase1.getNoteonCoords();
		int[] times2 = phrase2.getNoteonCoords();
		
		//Check these arrays
		if(times1.length != times2.length) return false;
		
		for(int i = 0; i < times1.length; i++){
			//Check if times are close enough.
			int t1 = times1[i];
			int t2 = times2[i];
			int td = Math.abs(t2 - t1);
			if(td > time_leeway) return false;
			
			NUSALSeqBuilderNote[] g1 = phrase1.getNotesAt(t1);
			NUSALSeqBuilderNote[] g2 = phrase2.getNotesAt(t2);
			
			if(g1.length != g2.length) return false;
			for(int j = 0; j < g1.length; j++){
				NUSALSeqBuilderNote n1 = g1[j];
				NUSALSeqBuilderNote n2 = g2[j];
				
				if(n1.getNote() != n2.getNote()) return false;
				int vd = Math.abs((int)n1.getVelocity() - (int)n2.getVelocity());
				if(vd > vel_leeway) return false;
				td = Math.abs(n1.getLengthInTicks() - n2.getLengthInTicks());
				if(td > time_leeway) return false;
			}
		}
		
		return true;
	}
	
	//	--> Interface Method
	
	public MusicEventMap compressMusicEvents(NoteMap notemap){
		final String methodname = "compressMusicEvents";
		if(notemap == null){
			writeLogLine(methodname, "Provided note map is null. Terminating phrasebuilder.", true);
			return null;
		}
		
		buildEventTable(notemap);
		MusicEventMap out = null;
		writeLogLine(methodname, "Events analyzed. Now searching for musical patterns...", true);
		switch(optimization){
		case 0:
			out = compressMusicEvents0();
			break;
		case 1:
			out = compressMusicEvents1();
			break;
		case 2:
			out = compressMusicEvents2();
			break;
		default:
			break;
		}
		clearBuilderTables();
		return out;
	}

}
