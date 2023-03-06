package waffleoRai_Compression.lz77;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import waffleoRai_Utils.FileBuffer;

public abstract class LZCompCore {
	//Some code for handling common LZ77/LZSS compression functions and algorithm
	//	variation for attempted matching. 
	
	/*--- Helper Classes ---*/
	
	private static boolean matchSortOrder = false; //false is run len, true is position
	
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
		RunMatch match = new RunMatch();
		
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
						match_run = match.match_run;
						match_pos = match.match_pos;
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
				
				int runlen = (int)(fpos - current_pos);
				if(runlen >= min_run_len){
					RunMatch match = new RunMatch();
					match.match_run = runlen;
					match.match_pos = (int)check_bpos;
					//Check if valid
					if(matchEncodable(match)){
						match.encoder_pos = cpos;
						match.copy_amt = 0;
						all_matches.add(match);
					}
				}
				check_bpos--;
			}
			
			cpos++;
		}
		
		if(all_matches.isEmpty()) return null;
		matchSortOrder = false;
		Collections.sort(all_matches);
		Collections.reverse(all_matches);
		
		//Pop until none left. Only keep when don't conflict
		List<RunMatch> okay_matches = new LinkedList<RunMatch>();
		int len = all_matches.size();
		for(int i = 0; i < len; i++){
			boolean noconflict = true;
			RunMatch my_match = okay_matches.get(i);
			for(RunMatch other_match : okay_matches){
				if(my_match.conflictsWith(other_match)){
					noconflict = false;
					break;
				}
			}
			if(noconflict) okay_matches.add(my_match);
		}
		all_matches.clear();
		
		//Resort by order instructions should be issued in and fill in copy amounts.
		matchSortOrder = true;
		Collections.sort(okay_matches);
		cpos = current_pos;
		long last_end = 0;
		for(RunMatch run : okay_matches){
			run.copy_amt = (int)(run.encoder_pos - cpos);
			cpos = run.encoder_pos;
			last_end = run.encoder_pos + run.match_run;
		}
		//Add another copy node if okay_matches doesn't go to the end of requested region
		if(last_end < cend){
			RunMatch cpy = new RunMatch();
			cpy.copy_amt = (int)(cend - last_end);
			cpy.encoder_pos = cpos;
			cpy.match_run = 0;
			okay_matches.add(cpy);
		}
		
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
