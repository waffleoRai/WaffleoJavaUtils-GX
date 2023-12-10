package waffleoRai_Compression.lz77;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import waffleoRai_Utils.FileBuffer;

public abstract class LZCompCore {
	//Some code for handling common LZ77/LZSS compression functions and algorithm
	//	variation for attempted matching. 
	
	public static final int LAST_PICK_NONE = -1;
	public static final int LAST_PICK_MINE = 0;
	public static final int LAST_PICK_NEXT = 1;
	
	/*--- Helper Classes ---*/
	
	protected static boolean matchSortOrder = false; //false is run len, true is position
	
	public static class RunMatch implements Comparable<RunMatch>{
		public int match_run;
		public int match_pos;
		public int copy_amt;
		public long encoder_pos;
		
		public int hashCode(){
			return match_run ^ match_pos ^ (int)encoder_pos;
		}
		
		public boolean equals(Object o){
			return (o == this);
		}
		
		public int compareTo(RunMatch o) {
			if(o == null) return -1;
			
			if(!matchSortOrder){
				if(this.match_run != o.match_run){
					return this.match_run - o.match_run;
				}
				
				int t_pdiff = (int)(this.encoder_pos - this.match_pos);
				int o_pdiff = (int)(o.encoder_pos - o.match_pos);
				if(t_pdiff != o_pdiff){
					//Smaller is better, so larger number should go first.
					return o_pdiff - t_pdiff;
				}
				
				//Smaller copy amount is better
				if(this.copy_amt != o.copy_amt){
					return o.copy_amt - this.copy_amt;
				}
				
				if(this.encoder_pos != o.encoder_pos){
					return (int)(o.encoder_pos - this.encoder_pos);
				}
				if(this.match_pos != o.match_pos){
					return (int)(o.match_pos - this.match_pos);
				}
			}
			else{
				//Position
				if(this.encoder_pos != o.encoder_pos){
					return (int)(this.encoder_pos - o.encoder_pos);
				}
			}
			
			return 0;
		}
	
		public boolean conflictsWith(RunMatch o){
			if(o == null) return false;
			if(this.encoder_pos == o.encoder_pos) return true;
			long t_ed = encoder_pos + match_run;
			long o_ed = o.encoder_pos + o.match_run;
			if(this.encoder_pos < o.encoder_pos){
				if(t_ed > o.encoder_pos) return true;
			}
			else{
				if(o_ed > this.encoder_pos) return true;
			}
			return false;
		}
	
		public int sizeAsAllLiteral(){
			return copy_amt + match_run;
		}
		
	}
	
	/*--- Instance Variables ---*/
	
	protected int min_run_len = 2;
	protected int max_run_len = 0;
	protected int max_back_len = 0;
	
	protected int match_run = 0; //RLE length
	protected int match_pos = 0; //RLE back start position. Relative to ENTIRE data buffer (not current position)
	protected int copy_amt = 0; //Bytes to copy directly (before RLE instruction)
	
	protected FileBuffer input_data = null;
	protected long input_len = 0L;
	protected long current_pos = 0L;
	
	protected int lastPick = LAST_PICK_NONE;
	protected boolean skipNext = false;
	
	/*--- Init ---*/
	
	public void setData(FileBuffer data){
		input_data = data;
		current_pos = 0L;
		input_len = data.getFileSize();
		
		match_run = 0;
		match_pos = 0;
		copy_amt = 0;
	}
	
	/*--- Getters ---*/
	
	public int getMatchRun(){return match_run;}
	public long getMatchPos(){return match_pos;}
	public long getCurrentPos(){return current_pos;}
	
	/*--- Methods ---*/
	
	protected abstract boolean currentMatchEncodable();
	protected abstract boolean matchEncodable(RunMatch match);
	protected abstract int scoreRun(RunMatch match);
	protected abstract int bytesToEncode(RunMatch match);
	
	protected RunMatch newRunMatch(){return new RunMatch();}
	
	public void firstMeetsReq(){
		/*
		 * Find the first match that meets the requirements for a match.
		 * Only checks current position.
		 * "Fast" variation, I guess
		 */
		match_run = 0;
		match_pos = 0;
		
		if(current_pos <= 0) return;
		long fpos = current_pos;
		long check_bpos = current_pos - 1;
		long bpos = check_bpos;
		long fmax = current_pos + max_run_len;
		long bmin = current_pos - max_back_len;
		if(bmin < 0) bmin = 0;
		if(fmax > input_len) fmax = input_len;
		
		while(check_bpos >= bmin){
			fpos = current_pos;
			bpos = check_bpos;
			while(fpos < fmax){
				if(input_data.getByte(fpos) != input_data.getByte(bpos)){
					break;
				}
				fpos++;
				bpos++;
			}
			
			//Check length...
			int runlen = (int)(fpos - current_pos);
			if(runlen >= min_run_len){
				//Check if valid
				match_run = runlen;
				match_pos = (int)check_bpos;
				if(currentMatchEncodable()){
					return;
				}
			}
			check_bpos--;
		}
		
	}
	
	public int findMatchLookaheadRecursive(LinkedList<RunMatch> chain){
		//1. Find best match at current position.
		RunMatch mymatch = findMatchAt(current_pos);
		
		//2. If no run, just return since it's literal either way.
		if(mymatch.match_run < 2){
			lastPick = LAST_PICK_NONE;
			return 0;
		}
		
		long nxtpos = current_pos + 1;
		if(nxtpos >= input_len){
			lastPick = LAST_PICK_MINE;
			chain.push(mymatch);
			return scoreRun(mymatch);
		}
		
		//3. See if there is a better match a little ways (how far?)
		//	down if we keep this one literal instead.
		RunMatch nextmatch = findMatchAt(nxtpos);
		if(nextmatch == null || nextmatch.match_run < 2){
			lastPick = LAST_PICK_MINE;
			chain.push(mymatch);
			return scoreRun(mymatch);
		}
		nextmatch.copy_amt = 1;
		nextmatch.encoder_pos--;
		
		int score1 = mymatch.match_run;
		int score2 = nextmatch.match_run;
		if(score1 <= score2){
			int sz1 = score1;
			int sz2 = score2+1;
			
			long pos = current_pos;
			LinkedList<RunMatch> l0 = new LinkedList<RunMatch>();
			LinkedList<RunMatch> l1 = new LinkedList<RunMatch>();
			
			current_pos = pos + sz1;
			score1 = scoreRun(mymatch) + findMatchLookaheadRecursive(l0);
			current_pos = pos + sz2;
			score2 = scoreRun(nextmatch) + findMatchLookaheadRecursive(l1);
			current_pos = pos;

			if(score2 < score1){
				chain.addAll(l1);
				
				lastPick = LAST_PICK_NEXT;
				chain.push(nextmatch);
				return score2;
			}
			else{
				chain.addAll(l0);
				
				lastPick = LAST_PICK_MINE;
				chain.push(mymatch);
				return score1;
			}
		}
		else if(score1 > score2){
			score1 = scoreRun(mymatch);
			score2 = scoreRun(nextmatch);
			if(score2 < score1){
				lastPick = LAST_PICK_NEXT;
				chain.push(nextmatch);
				return score2;
			}
		}
		
		lastPick = LAST_PICK_MINE;
		chain.push(mymatch);
		return score1;
	}
	
	public RunMatch findMatchLookaheadQuick(){
		//Matches LZMu. Don't mess with it.
		
		//1. Find best match at current position.
		RunMatch mymatch = findMatchAt(current_pos);
		
		//2. If no run, just return since it's literal either way.
		if(mymatch.match_run < 2){
			lastPick = LAST_PICK_NONE;
			skipNext = false;
			return null;
		}
		
		//3. If skipNext flag, just return this match...
		if(skipNext){
			lastPick = LAST_PICK_MINE | 0x1000;
			skipNext = false;
			return mymatch;
		}
		
		//4. Check best match for next position
		long nxtpos = current_pos + 1;
		if(nxtpos >= input_len){
			//Right up at the end, so no next to check.
			lastPick = LAST_PICK_MINE | 0x800;
			return mymatch;
		}
		
		//5. Go ahead and find next match
		RunMatch nextmatch = findMatchAt(nxtpos);
		if(nextmatch == null || nextmatch.match_run < 2){
			//No match at next position. Just return current.
			lastPick = LAST_PICK_MINE | 0x400;
			return mymatch;
		}
		nextmatch.copy_amt = 1;
		nextmatch.encoder_pos--;
		
		//6. Compare
		int score1 = scoreRun(mymatch);
		int score2 = scoreRun(nextmatch);
		if(mymatch.match_run <= nextmatch.match_run){
			RunMatch m1a = findMatchAt(current_pos + mymatch.match_run);
			if(m1a != null && m1a.match_run >= 2){
				score1 += scoreRun(m1a);
				
				if(score2 < score1){
					lastPick = LAST_PICK_NEXT | 0x20;
					return nextmatch;
				}
				else{
					skipNext = true;
					lastPick = LAST_PICK_MINE | 0x20;
					return mymatch;
				}
			}
			else{
				if(score2 < score1){
					lastPick = LAST_PICK_NEXT | 0x10;
					return nextmatch;
				}
				else{
					skipNext = true;
					lastPick = LAST_PICK_MINE | 0x10;
					return mymatch;
				}
			}
		}
		else{
			//mymatch.match_run > nextmatch.match_run
			if(score2 < score1){
				lastPick = LAST_PICK_NEXT | 0x100;
				return nextmatch;
			}
			else{
				lastPick = LAST_PICK_MINE | 0x100;
				return mymatch;
			}
		}
	}
	
	public RunMatch findMatchAt(long pos){
		long cpos = current_pos;
		current_pos = pos;
		findMatchCurrentPosOnly();
		
		RunMatch match = newRunMatch();
		match.encoder_pos = pos;
		match.copy_amt = copy_amt;
		match.match_pos = match_pos;
		match.match_run = match_run;
		
		current_pos = cpos;
		match_run = 0;
		match_pos = 0;
		copy_amt = 0;
		return match;
	}
	
	public void findMatchCurrentPosOnly(){
		/*
		 * Find the longest viable run matching from the current position.
		 */
		match_run = 0;
		match_pos = 0;
		
		if(current_pos <= 0) return;
		long fpos = current_pos;
		long check_bpos = current_pos - 1;
		long bpos = check_bpos;
		long fmax = current_pos + max_run_len;
		if(fmax > input_len) fmax = input_len;
		long bmin = current_pos - max_back_len;
		if(bmin < 0) bmin = 0;
		RunMatch match = newRunMatch();
		match.encoder_pos = current_pos;
		
		while(check_bpos >= bmin){
			fpos = current_pos;
			bpos = check_bpos;
			while(fpos < fmax){
				if(input_data.getByte(fpos) != input_data.getByte(bpos)){
					break;
				}
				fpos++;
				bpos++;
			}
			
			//Check length...
			int runlen = (int)(fpos - current_pos);
			if(runlen >= min_run_len){
				//Check if longer than current best...
				if(runlen > match_run){
					match.match_run = runlen;
					match.match_pos = (int)check_bpos;
					//Check if valid
					if(matchEncodable(match)){
						if(scoreRun(match) < 0){
							match_run = match.match_run;
							match_pos = match.match_pos;
						}
					}
				}
			}
			check_bpos--;
		}
	}
	
	public List<RunMatch> findMatchesLookaheadGreedy(int lookahead_max){
		/*
		 * Find all possible matches from current position to lookahead position
		 * uses a greedy selection algo to return a combination of non-conflicting 
		 * runs.
		 * Probably very slow
		 */
		match_run = 0;
		match_pos = 0;
		
		if(current_pos <= 0) return null;
		
		long cpos = current_pos;
		long cend = cpos + lookahead_max + 1;
		if(cend > input_len) cend = input_len;
		List<RunMatch> all_matches = new ArrayList<RunMatch>((lookahead_max + 1) * max_back_len);
		
		while(cpos < cend){
			//Find all matches for this "current" position
			long fpos = cpos;
			long check_bpos = cpos - 1;
			long bpos = check_bpos;
			long fmax = cpos + max_run_len;
			if(fmax > input_len) fmax = input_len;
			long bmin = cpos - max_back_len;
			if(bmin < 0) bmin = 0;
			while(check_bpos >= bmin){
				fpos = cpos;
				bpos = check_bpos;
				while(fpos < fmax){
					if(input_data.getByte(fpos) != input_data.getByte(bpos)){
						break;
					}
					fpos++;
					bpos++;
				}
				
				int runlen = (int)(fpos - cpos);
				if(runlen >= min_run_len){
					RunMatch match = newRunMatch();
					match.match_run = runlen;
					match.match_pos = (int)check_bpos;
					match.copy_amt = 0;
					match.encoder_pos = cpos;
					
					//Check if valid
					if(matchEncodable(match)){
						all_matches.add(match);
					}
				}
				check_bpos--;
			}
			
			cpos++;
		}
		
		if(all_matches.isEmpty()) return null;
		matchSortOrder = false;
		if(all_matches.size() > 1){
			Collections.sort(all_matches);
		}
		
		//Pop until none left. Only keep when don't conflict
		List<RunMatch> okay_matches = new LinkedList<RunMatch>();
		int len = all_matches.size();
		for(int i = 0; i < len; i++){
			boolean noconflict = true;
			RunMatch my_match = all_matches.get(i);
			
			//Toss if score is 0 or positive.
			int score = scoreRun(my_match);
			if(score >= 0) break; //Nothing left worth looking at.
			
			for(RunMatch other_match : okay_matches){
				if(my_match.conflictsWith(other_match)){
					noconflict = false;
					break;
				}
			}
			if(noconflict) okay_matches.add(my_match);
		}
		all_matches.clear();
		
		if(okay_matches.isEmpty()) return null;
		
		//Resort by order instructions should be issued in and fill in copy amounts.
		matchSortOrder = true;
		if(okay_matches.size() > 1){
			Collections.sort(okay_matches);	
		}
		cpos = current_pos;
		long last_end = 0;
		for(RunMatch run : okay_matches){
			run.copy_amt = (int)(run.encoder_pos - cpos);
			last_end = run.encoder_pos + run.match_run;
			cpos = last_end;
			run.encoder_pos -= run.copy_amt;
		}
		
		//Add another copy node if okay_matches doesn't go to the end of requested region
		/*if(last_end < cend){
			RunMatch cpy = newRunMatch();
			cpy.copy_amt = (int)(cend - last_end);
			cpy.encoder_pos = last_end;
			cpy.match_run = 0;
			okay_matches.add(cpy);
		}*/
		
		return okay_matches;
	}
	
	public boolean atEnd(){
		return current_pos >= input_len;
	}
	
	public boolean incrementPos(int amt){
		current_pos += amt;
		return current_pos >= input_len;
	}
	
}
