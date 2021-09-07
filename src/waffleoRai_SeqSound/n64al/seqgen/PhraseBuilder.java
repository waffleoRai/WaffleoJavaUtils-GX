package waffleoRai_SeqSound.n64al.seqgen;

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
	
	//Phrases cannot straddle these!
	private int loop_start;
	private int loop_end;
	
	//Temporary
	private NoteMap chdat;
	private Map<Integer, NUSALSeqBuilderNote[]> nowmap;
	private int[][] event_tbl; //[n][0] is tick value, [n][1] is active voices, [n][2] is event count
	
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
		resetDefaults();
	}
	
	public void resetDefaults(){
		time_leeway = 4;
		vel_leeway = 5;
		one_voice_match = true;
		min_time = 48;
		min_events = 4;
		optimization = OPT_LEVEL_1;
	}
	
	/*----- Getters -----*/
	
	public int getTimeLeeway(){return time_leeway;}
	public int getVelocityLeeway(){return vel_leeway;}
	public boolean voiceMatchMode(){return one_voice_match;}
	public int getOptimizationLevel(){return optimization;}
	public int getPhraseMinTime(){return min_time;}
	public int getPhraseMinEvents(){return min_events;}
	
	/*----- Setters -----*/
	
	public void setTimeLeeway(int ticks){time_leeway = ticks;}
	public void setVelocityLeeway(int vel_amt){vel_leeway = vel_amt;}
	public void setVoiceMatchMode(boolean onevoice){one_voice_match = onevoice;}
	public void setPhraseMinTime(int ticks){min_time = ticks;}
	public void setPhraseMinEvents(int events){min_events = events;}
	public void setLoopStart(int ticks){loop_start = ticks;}
	public void setLoopEnd(int ticks){loop_end = ticks;}
	
	public void setOptimizationLevel(int lvl){
		if(lvl < 0) lvl = 0;
		if(lvl > 2) lvl = 2;
		optimization = lvl;
	}
	
	/*----- Compression Functions -----*/
	
	private void buildEventTable(NoteMap notemap){
		chdat = notemap;
		nowmap = new TreeMap<Integer, NUSALSeqBuilderNote[]>();
		int[] times = notemap.getTimeCoords();
		
		int ecount = times.length;
		event_tbl = new int[ecount][3];
		for(int i = 0; i < ecount; i++) event_tbl[i][0] = times[i];
		
		for(int i = 0; i < ecount; i++){
			int tick = event_tbl[i][0];
			
			NUSALSeqBuilderNote[] arr = notemap.getNoteGroupAt(tick);
			nowmap.put(tick, arr);
			event_tbl[i][2] = arr.length;
			event_tbl[i][1] += event_tbl[i][2];
			for(NUSALSeqBuilderNote n : arr){
				//Add this voice to all subsequent note on ticks it covers
				int end_tick = n.getLengthInTicks() + tick;
				for(int j = i; j < ecount; j++){
					if(event_tbl[j][0] >= end_tick) break;
					event_tbl[j][1]++;
				}
			}
		}
	}
	
	private void clearBuilderTables(){
		nowmap = null;
		event_tbl = null;
		if(chdat != null) chdat.clearPhraseAnnotations();
		chdat = null;
	}
	
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
	
	protected boolean groupsMatch(NUSALSeqBuilderNote[] group1, NUSALSeqBuilderNote[] group2){
		if(group1 == null || group2 == null) return false;
		if(!one_voice_match){
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
			return true;
		}
		else{
			//Only one voice needs to be in common.
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
		}
		return false;
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
	
	protected void findPhrase(NUSALSeqBuilderPhrase phrase, boolean flagNotes){
		//Finds the phrase in the channel and annotates it with start points
		int[] no_coords = phrase.getNoteonCoords();
		if(no_coords == null) return;
		if(!one_voice_match){
			//Perfect matches
			for(int i = 0; i < event_tbl.length; i++){
				int sttick = event_tbl[i][0];
				boolean match = true;
				for(int j = 0; j < no_coords.length; j++){
					if(i+j >= event_tbl.length){match = false; break;}
					
					int t1 = event_tbl[i+j][0];
					
					int d1 = t1 - sttick;
					int d2 = no_coords[j];
					int dd = Math.abs(d1 - d2);
					if(dd > time_leeway){match = false; break;}
					
					NUSALSeqBuilderNote[] group1 = nowmap.get(t1);
					NUSALSeqBuilderNote[] group2 = phrase.getNotesAt(d2);
					
					if(!groupsMatch(group1, group2)){match = false; break;}
					else{
						//Mark!
						if(flagNotes){
							for(NUSALSeqBuilderNote n : group1) n.linkPhrase(phrase, false);
							if(j == 0){
								group1[0].flagFirstInPhrase(true);
							}
						}
					}
				}
				if(match){
					phrase.addStartCoord(sttick);
				}
			}
		}
		else{
			for(int e = 0; e < event_tbl.length; e++){
				int sttick = event_tbl[e][0];
				
				//Check 0-0 match
				NUSALSeqBuilderNote[] group1 = nowmap.get(sttick);
				NUSALSeqBuilderNote[] group2 = phrase.getNotesAt(0);
				if(!groupsMatch(group1, group2)) continue;
				if(flagNotes){
					boolean first = true;
					for(NUSALSeqBuilderNote n : group1){
						for(NUSALSeqBuilderNote ref : group2){
							if(ref.equals(n)){
								n.linkPhrase(phrase, first);
								first = false;
								break;
							}
						}
					}
				}
				
				int i = 1; int j = 1;
				int mcount = 0;
				while(j < no_coords.length){
					if(e+i >= event_tbl.length) break;
					//Check times at i and j
					//Try to line up start times by tweaking i and j.
					int ti = event_tbl[e+i][0];
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
						
						if(flagNotes){
							//Mark common notes.
							for(NUSALSeqBuilderNote n : group1){
								for(NUSALSeqBuilderNote ref : group2){
									if(ref.equals(n)){
										n.linkPhrase(phrase, false);
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
				}
			}
		}
	}

	protected boolean phraseClearsLoop(NUSALSeqBuilderPhrase phrase){
		if(loop_start < 0 && loop_end < 0) return true;
		findPhrase(phrase, false);
		int[] starts = phrase.getPhraseCoords();
		if(starts == null) return true;
		int plen = phrase.getLengthInTicks();
		for(int i = 0; i < starts.length; i++){
			int st = starts[i];
			int ed = st + plen;
			if(loop_start > st && loop_start < ed) return false;
			if(loop_end > st && loop_end < ed) return false;
		}
		return true;
	}
	
	protected List<NUSALSeqBuilderPhrase> getPhrasesAt(int ontick_idx, boolean breakAtOne){
		if(ontick_idx < 0 || ontick_idx > event_tbl.length) return null;
		List<NUSALSeqBuilderPhrase> list = new LinkedList<NUSALSeqBuilderPhrase>();
		
		int mytick = event_tbl[ontick_idx][0];
		//Scan for start (must be at least min_time AND min_events forward)
		int next_idx = findScanStartIndex(ontick_idx);
		
		while(next_idx < event_tbl.length){
			//See if there's a common music phrase starting at this idx and that idx
			int elen = Math.min(next_idx - ontick_idx, event_tbl.length - next_idx); //Max phrase size in note-on groups
			int nexttick = event_tbl[next_idx][0];
			if(!one_voice_match){
				//Must be perfect match
				NUSALSeqBuilderPhrase phrase = null;
				for(int i = 0; i < elen; i++){
					int t1 = event_tbl[ontick_idx+i][0];
					int t2 = event_tbl[next_idx+i][0];
					
					//Are start times relative to putative phrase start close enough?
					int d1 = mytick - t1;
					int d2 = nexttick - t2;
					int dd = Math.abs(d2 - d1);
					if(dd > time_leeway) break;
					
					NUSALSeqBuilderNote[] group1 = nowmap.get(t1);
					NUSALSeqBuilderNote[] group2 = nowmap.get(t2);
					if(groupsMatch(group1, group2)){
						//Add to phrase (or create if not instantiated)
						if(phrase == null) phrase = new NUSALSeqBuilderPhrase();
						for(int j = 0; j < group1.length; j++){
							phrase.addNote(d1, group1[j]);
						}
					}
					else break;
				}
				//Add to list if long enough. And see where else it occurs.
				if(phrase != null){
					if(phrase.getLengthInEvents() >= min_events && phrase.getLengthInTicks() >= min_time){
						//findPhrase(phrase);
						if(phraseClearsLoop(phrase)){
							list.add(phrase);
							if(breakAtOne) return list;	
						}
					}
				}
			}
			else{
				//Ughhhhhhhhhhhhhhhh
				//First events must match for this combo. If not, continue.
				int t1 = event_tbl[ontick_idx][0];
				int t2 = event_tbl[next_idx][0];
				NUSALSeqBuilderNote[] group1 = nowmap.get(t1);
				NUSALSeqBuilderNote[] group2 = nowmap.get(t2);
				if(!groupsMatch(group1, group2)) {next_idx++; continue;}
				
				NUSALSeqBuilderPhrase phrase = new NUSALSeqBuilderPhrase();
				//Put common notes from first event in phrase.
				addCommonToPhrase(0, group1, group2, phrase);
				
				int i = 1;
				int j = 1;
				while(j < elen){
					if((ontick_idx + i) >= next_idx) break;
					if((next_idx + j) >= event_tbl.length) break;
					t1 = event_tbl[ontick_idx+i][0];
					t2 = event_tbl[next_idx+j][0];
					
					int d1 = mytick - t1;
					int d2 = nexttick - t2;
					int dd = Math.abs(d2 - d1);
					if(dd > time_leeway){
						if(d2 > d1) i++;
						else if (d1 > d2) j++;
						continue;
					}
					
					group1 = nowmap.get(t1);
					group2 = nowmap.get(t2);
					if(!groupsMatch(group1, group2)){
						if(d2 > d1) i++;
						else if (d1 > d2) j++;
					}
					else{
						addCommonToPhrase(d1, group1, group2, phrase);
						i++; j++;
					}
				}
				if(phrase.getLengthInEvents() >= min_events && phrase.getLengthInTicks() >= min_time){
					//findPhrase(phrase);
					if(phraseClearsLoop(phrase)){
						list.add(phrase);
						if(breakAtOne) return list;	
					}
				}
			}
			next_idx++;
		}
		return list;
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
					findPhrase(p, false);
					
					//Add to biglist
					biglist.add(p);
				}
			}
		}
		return biglist;
	}
	
	protected MusicEventMap compressMusicEvents0(){
		chdat.clearPhraseAnnotations();
		//Store these
		NoteMap chdat_st = chdat;
		Map<Integer, NUSALSeqBuilderNote[]> nowmap_st = nowmap;
		int[][] event_tbl_st = event_tbl;
		
		int ecount = event_tbl.length;
		for(int e = 0; e < ecount; e++){
			if(chdat.isEmpty()) break; //Everything has been assigned already.
			int etick = event_tbl_st[e][0];
			if(!chdat.hasNotesAt(etick)) continue;
			
			List<NUSALSeqBuilderPhrase> plist = getPhrasesAt(etick, true);
			if(plist == null || plist.isEmpty()) continue;
			
			NUSALSeqBuilderPhrase phrase = plist.get(0);
			findPhrase(phrase, true);
			buildEventTable(chdat.copyWithoutPhrasedNotes());
		}
		
		//Restore temp instance variables
		chdat = chdat_st;
		nowmap = nowmap_st;
		event_tbl = event_tbl_st;
		return chdat.condense();
	}
	
	protected MusicEventMap compressMusicEvents1(){
		chdat.clearPhraseAnnotations();
		//Store these
		NoteMap chdat_st = chdat;
		Map<Integer, NUSALSeqBuilderNote[]> nowmap_st = nowmap;
		int[][] event_tbl_st = event_tbl;
		
		int ecount = event_tbl.length;
		for(int e = 0; e < ecount; e++){
			if(chdat.isEmpty()) break; //Everything has been assigned already.
			int etick = event_tbl_st[e][0];
			if(!chdat.hasNotesAt(etick)) continue;
			
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
			
			findPhrase(phrase, true);
			buildEventTable(chdat.copyWithoutPhrasedNotes());
		}
		
		//Restore temp instance variables
		chdat = chdat_st;
		nowmap = nowmap_st;
		event_tbl = event_tbl_st;
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
		chdat.clearPhraseAnnotations();
		List<NUSALSeqBuilderPhrase> allphrase = getPossiblePhrases();
		if(allphrase.isEmpty()) return chdat.condense();
		
		int pcount = allphrase.size();
		NUSALSeqBuilderPhrase[] parr = new NUSALSeqBuilderPhrase[pcount];
		int i = 0;
		for(NUSALSeqBuilderPhrase p : allphrase) parr[i++] = p;
		PhraseStack stack = new PhraseStack(parr);
		findBestCombo(stack, 0);
		Set<Integer> best = stack.getBestSet();
		for(Integer idx : best){
			findPhrase(parr[idx], true);
		}
		
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
	
	public MusicEventMap compressMusicEvents(NoteMap notemap){
		if(notemap == null) return null;
		buildEventTable(notemap);
		MusicEventMap out = null;
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
