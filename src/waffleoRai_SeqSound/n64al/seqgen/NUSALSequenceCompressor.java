package waffleoRai_SeqSound.n64al.seqgen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

import waffleoRai_DataContainers.CoverageMap1D;
import waffleoRai_DataContainers.MultiValMap;
import waffleoRai_SeqSound.n64al.NUSALSeqCmdType;
import waffleoRai_SeqSound.n64al.NUSALSeqCommand;
import waffleoRai_SeqSound.n64al.cmd.FCommands.*;
import waffleoRai_SeqSound.n64al.cmd.NUSALSeqCommandChunk;
import waffleoRai_SeqSound.n64al.cmd.NUSALSeqNoteCommand;

public class NUSALSequenceCompressor {

	/*----- Instance Variables -----*/
	
	// --> Options
	private int leeway_gate = -1; //-1 means ignore gate entirely
	private int leeway_vel = 5;
	private int leeway_time = 2;
	
	private int min_sub_cmds = 4; //Minimum number of commands to consider possible subroutine
	
	// --> Structures
	private List<TimeBlock> input;
	private ArrayList<NUSALSeqCommand> cmdlist;
	private int[][] og_locs; //[tb, chunk] indices of commands, matching idxs in cmdlist
	private Set<Integer> timebreaks;
	private Set<Integer> breakidxs;
	
	private LinkedList<CompOption> cands_list;
	private MultiValMap<Integer, CompOption> cands_map;
	private CoverageMap1D total_cov;
	
	private TimeBlock[] output;
	private Set<Integer> tosub; //Indices of commands that were moved to subroutines (so not copied to main output track)
	private Map<Integer, NUSALSeqCommand> calls;
	
	private int replaces = 0;
	private int total_saved = 0;
	
	/*----- Init -----*/
	
	/*----- Getters -----*/
	
	public int getGateLeeway(){return leeway_gate;}
	public int getVelocityLeeway(){return leeway_vel;}
	public int getTimeLeeway(){return leeway_time;}
	public int getMinimumCommandsPerSub(){return min_sub_cmds;}
	public int getLastReplacementsCount(){return replaces;}
	public int getLastBytesSavedCount(){return total_saved;}
	
	/*----- Setters -----*/
	
	public void setGateLeeway(int value){leeway_gate = value;}
	public void setVelocityLeeway(int value){leeway_vel = value;}
	public void setTimeLeeway(int value){leeway_time = value;}
	public void setMinimumCommandsPerSub(int value){min_sub_cmds = value;}
	
	public void setCheckGate(boolean b){
		if(b) leeway_gate = 5;
		else leeway_gate = -1;
	}
	
	/*----- Inner Classes -----*/
	
	protected static class CompInstance implements Comparable<CompInstance>{

		public static boolean REVSORT_OVERLAP = true;
		
		public int start;
		public int end;
		public int overlap; //Count
		
		public CompInstance(int start, int len){
			this.start = start;
			end = start + len;
			overlap = 0;
		}
		
		public boolean equals(Object o){
			return this == o;
		}
		
		public int addsubOverlapWith(CompInstance other, boolean add){
			if(other == null) return -1; //Other is lower, no overlap
			if(end <= other.start) return 1; //Other is higher, no overlap
			if(start >= other.end) return -1; //Other is lower, no overlap
			
			int amt = 0;
			int ost = other.start>start?other.start:start;
			int oed = other.end<end?other.end:end;
			amt = oed - ost;
			
			if(add){
				overlap += amt;
				other.overlap += amt;
			}
			else{
				overlap -= amt;
				other.overlap -= amt;
			}
			
			return 0;
		}
		
		public int compareTo(CompInstance o) {
			if(o == null) return 1;
			if(this.overlap != o.overlap){
				if(REVSORT_OVERLAP) return o.overlap - this.overlap;
				return this.overlap - o.overlap;
			}
			if(this.start != o.start) return this.start - o.start;
			if(this.end != o.end) return this.end - o.end;
			
			return 0;
		}
		
	}
	
	protected static class CompOption implements Comparable<CompOption>{
		
		public static boolean REVSORT_BYTESAVE = true;
		
		public List<Integer> starts;
		public int len; //In commnands
		
		public int sub_size; //In bytes
		public int bytes_saved;
		
		public int tick_len;
		public CoverageMap1D coverage; //Command indices
		
		public CompOption(){
			starts = new LinkedList<Integer>();
			len = 0;
			bytes_saved = 0;
			coverage = new CoverageMap1D();
		}
		
		public void updateByteSavings(){
			int uncomp_size = sub_size * starts.size();
			int comp_size = sub_size + 1 + (starts.size() * 3); //A call is 3 bytes, an end is 1
			bytes_saved = uncomp_size - comp_size;
		}
		
		public void updateCoverage(){
			coverage = new CoverageMap1D();
			for(Integer s : starts){
				coverage.addBlock(s, s+len);
			}
			coverage.mergeBlocks();
		}
		
		public void removeInstancesOverlapping(CoverageMap1D cov){
			coverage = new CoverageMap1D();
			List<Integer> newstarts = new LinkedList<Integer>();
			for(Integer s : starts){
				int ed = s + len;
				boolean flag = true;
				for(int i = s; i < ed; i++){
					if(cov.isCovered(i)){
						flag = false;
						break;
					}
				}
				if(flag){
					newstarts.add(s);
					coverage.addBlock(s, ed);
				}
			}
			//Collections.sort(newstarts);
			starts.clear();
			starts = newstarts;
			updateByteSavings();
		}

		public boolean equals(Object o){
			return this == o;
		}

		public int compareTo(CompOption o) {
			if(o == null) return 1;
			if(this.bytes_saved != o.bytes_saved){
				if(REVSORT_BYTESAVE) return o.bytes_saved - this.bytes_saved;
				return this.bytes_saved - o.bytes_saved;
			}
			if(this.sub_size != o.sub_size) return this.sub_size - o.sub_size;
			if(this.len != o.len) return this.len - o.len;
			if(this.tick_len != o.tick_len) return this.tick_len - o.tick_len;
			
			int[] this_starr = new int[starts.size()];
			int[] oth_starr = new int[o.starts.size()];
			
			int i = 0;
			for(Integer s : starts) this_starr[i++] = s;
			i = 0;
			for(Integer s : o.starts) oth_starr[i++] = s;
			
			for(i = 0; i < this_starr.length; i++){
				if(i >= oth_starr.length) return 1;
				if(this_starr[i] != oth_starr[i]) return this_starr[i] - oth_starr[i];
			}
			if(this_starr.length < oth_starr.length) return -1;
			
			return 0;
		}
		
		public void debug_printerr(){
			System.err.print("BYTESAVE: " + bytes_saved + " | ");
			System.err.print("@{");
			boolean first = true;
			for(Integer s : starts){
				if(!first) System.err.print(", ");
				System.err.print(s);
				first = false;
			}
			System.err.print("} | LEN: " + len);
			System.err.print(" | ICOUNT: " + starts.size());
			System.err.print(" | BYTELEN: " + sub_size);
			System.err.print("\n");
		}
		
	}
	
	/*----- Compression -----*/
	
	private boolean commandsEquivalent(NUSALSeqCommand cmd1, NUSALSeqCommand cmd2, int b1tick, int b2tick){
		//Also returns false if either is an END_READ
		if(cmd1 == null || cmd2 == null) return false;
		if(cmd1.isEndCommand() || cmd2.isEndCommand()) return false;
		
		//Check start time
		int st1 = cmd1.getTickAddress() - b1tick;
		int st2 = cmd2.getTickAddress() - b2tick;
		int tdiff = Math.abs(st1-st2);
		if(tdiff > leeway_time) return false;
		
		//Check end time
		int ed1 = st1 + cmd1.getSizeInTicks();
		int ed2 = st2 + cmd2.getSizeInTicks();
		tdiff = Math.abs(ed1-ed2);
		if(tdiff > leeway_time) return false;
		
		if(cmd1 instanceof NUSALSeqNoteCommand){
			//Notes handled a bit different.
			if(!(cmd2 instanceof NUSALSeqNoteCommand)) return false;
			NUSALSeqNoteCommand note1 = (NUSALSeqNoteCommand)cmd1;
			NUSALSeqNoteCommand note2 = (NUSALSeqNoteCommand)cmd2;
			
			//Check note values (uses COMMAND note, meaning transpose better be adjusted beforehand...)
			if(note1.getCommandByte() != note2.getCommandByte()) return false;
			
			//Check velocities
			int vel1 = (int)note1.getVelocity();
			int vel2 = (int)note2.getVelocity();
			int vdiff = Math.abs(vel1 - vel2);
			if(vdiff > leeway_vel) return false;
			
			//Check gates (if applicable)
			if(leeway_gate >= 0){
				int gate1 = (int)note1.getGate();
				int gate2 = (int)note2.getGate();
				int gdiff = Math.abs(gate1 - gate2);
				if(gdiff > leeway_gate) return false;
			}
		}
		else{
			//Everything else
			if(cmd1.getFunctionalType() != cmd2.getFunctionalType()) return false;
			
			if(cmd1.isBranch()){
				//Reference must match (be same ptr)
				if(cmd1.getBranchTarget() != cmd2.getBranchTarget()) return false;
			}
			else{
				//All args must match
				int argct = cmd1.getParamCount();
				if(cmd2.getParamCount() != argct) return false;
				for(int i = 0; i < argct; i++){
					if(cmd1.getParam(i) != cmd2.getParam(i)) return false;
				}
			}
		}
		return true;
	}
	
	private void applyComp(CompOption compop){
		//Generate sub (from first instance of repeat)
		NUSALSeqCommandChunk sub = new NUSALSeqCommandChunk();
		int start = compop.starts.get(0);
		int end = start + compop.len;
		for(int i = start; i < end; i++){
			sub.addCommand(cmdlist.get(i));
		}
		sub.addCommand(new C_EndRead());
		
		//Mark removed commands & generate call commands
		for(Integer s : compop.starts){
			int ed = s + compop.len;
			for(int i = s; i < ed; i++) tosub.add(i);
			NUSALSeqCommand call = new C_Call(-1);
			call.setReference(sub);
			calls.put(s, call);
		}
		
		//Update coverage map
		total_cov.add(compop.coverage);

		//Determine which timeblock to put in, and add sub to that block
		int tb = og_locs[start][0];
		output[tb].subroutines.add(sub);
		
		replaces++;
		total_saved += compop.bytes_saved;
	}
	
	private void doComp_greedy_global(){
		System.err.println("NUSALSequenceCompressor.doComp_greedy_global || Initializing...");
		replaces = 0; total_saved = 0;
		cands_map = new MultiValMap<Integer, CompOption>();
		cands_list = new LinkedList<CompOption>();
		total_cov = new CoverageMap1D();
		tosub = new TreeSet<Integer>();
		calls = new HashMap<Integer, NUSALSeqCommand>();
		
		System.err.println("NUSALSequenceCompressor.doComp_greedy_global || Scanning for candidates... (this may take a bit)");
		int ccount = cmdlist.size();
		for(int i = 0; i < ccount; i++){
			//System.err.println("NUSALSequenceCompressor.doComp_greedy_global || Finding candidates for command " + i);
			findCandidatesFor(i);
		}
		System.err.println("NUSALSequenceCompressor.doComp_greedy_global || Candidate search complete.");
		CompOption.REVSORT_BYTESAVE = true;
		Collections.sort(cands_list);
		LinkedList<CompOption> q1 = null;
		LinkedList<CompOption> q2 = new LinkedList<CompOption>();
		System.err.println("NUSALSequenceCompressor.doComp_greedy_global || Candidates sorted");
		
		//Debug print candidates.
		//System.err.println("DEBUG -- Printing candidates -- ");
		//for(CompOption cand : cands_list) cand.debug_printerr();
		
		while(!cands_list.isEmpty()){
			CompOption compop = cands_list.pop();
			applyComp(compop);
			
			//Scan remaining.
			q2.clear();
			for(CompOption co : cands_list){
				co.removeInstancesOverlapping(total_cov);
				if(co.bytes_saved > 0){
					q2.add(co);
				}
			}
			Collections.sort(q2);
			q1 = cands_list;
			cands_list = q2;
			q2 = q1; q1 = null;
		}
		System.err.println("NUSALSequenceCompressor.doComp_greedy_global || Done.");
	}
	
	private void removeOverlappingInstances(CompOption compop){
		if(compop == null) return;
		LinkedList<CompInstance> cinst = new LinkedList<CompInstance>();
		
		int icount = compop.starts.size();
		CompInstance[] instarr = new CompInstance[icount];
		int i = 0;
		Collections.sort(compop.starts);
		for(Integer st : compop.starts){
			CompInstance ci = new CompInstance(st, compop.len);
			instarr[i] = ci;
			cinst.add(ci);
			
			//Calculate overlaps with all previously added...
			for(int j = i-1; j >= 0; j--){
				if(ci.addsubOverlapWith(instarr[j], true) != 0) break;
			}
			i++;
		}
		
		compop.starts.clear();
		Collections.sort(cinst);
		
		//Remove top instance until list is empty
		CompInstance rmv = null;
		while(!cinst.isEmpty()){
			rmv = cinst.pop();
			if(rmv.overlap <= 0) {
				cinst.push(rmv);
				break;
			}
			
			for(CompInstance ci : cinst){
				ci.addsubOverlapWith(rmv, false);
			}
			Collections.sort(cinst);
		}
		
		for(CompInstance ci : cinst){
			compop.starts.add(ci.start);
		}
		
		Collections.sort(compop.starts);
		compop.updateByteSavings();
		compop.updateCoverage();
	}
	
	private void findAllInstances(CompOption compop){
		if(compop.starts.isEmpty()) return;
		if(compop.len < min_sub_cmds) return;
		
		//Also calculate byte size and savings
		int baseidx = compop.starts.get(0);
		compop.starts.clear();
		//compop.coverage = new CoverageMap1D();
		//compop.coverage.addBlock(baseidx, baseidx+compop.len);
		
		NUSALSeqCommand cmd = cmdlist.get(baseidx);
		int basetick = cmd.getTickAddress();
		int maxidx = cmdlist.size() - compop.len;
		
		for(int i = 0; i < maxidx; i++){
			if(compop.starts.contains(i)) continue;
			if(i == baseidx){compop.starts.add(i); continue;}
			cmd = cmdlist.get(i);
			if(cmd.isEndCommand()) continue;
			int ctick = cmd.getTickAddress();
			
			boolean pass = true;
			for(int j = 0; j < compop.len; j++){
				int i1 = baseidx + j;
				int i2 = i+j;
				NUSALSeqCommand cmd1 = cmdlist.get(i1);
				//cmd1 should already pass basic tests
				
				NUSALSeqCommand cmd2 = cmdlist.get(i2);
				if(cmd2.isEndCommand()){pass = false; break;}
				if(breakidxs.contains(i2)){pass = false; break;}
				//if(compop.coverage.isCovered(i2)){pass = false; break;}
				
				if(!commandsEquivalent(cmd1, cmd2, basetick, ctick)){pass = false; break;}
			}
			
			if(pass){
				compop.starts.add(i);
				//compop.coverage.addBlock(i, i+compop.len);
			}
		}
		//Sort starts
		Collections.sort(compop.starts);
		
		//Scan commands again to get byte length
		compop.sub_size = 0;
		compop.tick_len = 0;
		for(int i = 0; i < compop.len; i++){
			cmd = cmdlist.get(baseidx+i);
			compop.sub_size += cmd.getSizeInBytes();
			compop.tick_len += cmd.getSizeInTicks();
		}
		
		//Coverage map
		/*compop.coverage = new CoverageMap1D();
		for(Integer s : compop.starts){
			cmd = cmdlist.get(s);
			//int tick = cmd.getTickAddress();
			//compop.coverage.addBlock(tick, tick + compop.tick_len);
			compop.coverage.addBlock(s, s+compop.len);
		}*/
		//compop.coverage.mergeBlocks();
		
		//Calculate byte savings
		compop.updateByteSavings();
	}
	
	private void findCandidatesFor(int cmdidx){
		//Stops scans at chunk ends as well - if tick hits
		//	a time break or an end command
		//Also scan thru existing candidates to make sure not adding those already found.
		if(breakidxs.contains(cmdidx)) return; //Falls on a breakpoint.
		int minlen = min_sub_cmds;
		int mined = cmdidx + minlen;
		int ccount = cmdlist.size();
		if(mined > ccount) return;
		for(int i = cmdidx+1; i < mined; i++){
			//Minimum phrase crosses a breakpoint.
			if(breakidxs.contains(i)) return;
		}
		
		//There's enough room after this command for abs minimum.
		//Fetch previously found cands and update min len
		List<CompOption> prevfound = cands_map.getValues(cmdidx);
		Set<Integer> donelens = new HashSet<Integer>();
		for(CompOption co : prevfound) donelens.add(co.len);
		//System.err.println("NUSALSequenceCompressor.findCandidatesFor || Previously found cands for tick: " + donelens.size());
		while(donelens.contains(minlen)) minlen++;
		
		//If new minimum length is not possible, return.
		mined = cmdidx + minlen;
		if(mined > ccount) return;
		for(int i = cmdidx+1; i < mined; i++){
			//Minimum phrase crosses a breakpoint.
			if(breakidxs.contains(i)) return;
		}
		
		//Find possible start points.
		Set<Integer> match_sts = new HashSet<Integer>();
		CompOption compop = new CompOption();
		compop.starts.add(cmdidx);
		compop.len = minlen;
		findAllInstances(compop);
		match_sts.addAll(compop.starts);
		//Remove any values <= cmdidx
		match_sts.removeIf(new Predicate<Integer>(){
			public boolean test(Integer t) {
				return t <= cmdidx;
			}
		});
		if(match_sts.isEmpty()) return; //No matches even at minimum.
		
		//Otherwise, add the minimum as a candidate
		int blen = compop.sub_size;
		removeOverlappingInstances(compop);
		if(compop.bytes_saved > 0){
			cands_list.add(compop);
			for(Integer cstart : compop.starts) cands_map.addValue(cstart, compop);	
			//System.err.println("*Candidate added -- cmdidx = " + cmdidx + ", len = " + compop.len + ", blen = " + blen);
			//compop.debug_printerr();
		}
		
		//Find maximum possible length
		int maxlen = minlen;
		for(int i = mined; i < ccount; i++){
			if(breakidxs.contains(i)) break;
			if(match_sts.contains(i)) break;
			maxlen++;
		}
		
		//Iterate thru all possible lengths...
		Set<Integer> temp = new HashSet<Integer>();
		NUSALSeqCommand basecmd = cmdlist.get(cmdidx);
		int basetick = basecmd.getTickAddress();
		for(int l = minlen+1; l <= maxlen; l++){
			if(match_sts.isEmpty()) return; //Depleted all possible matches.
			if(donelens.contains(l)){
				//add command size...
				NUSALSeqCommand cmd1 = cmdlist.get(cmdidx + l - 1);
				blen += cmd1.getSizeInBytes();
				continue; //Already checked this one.
			}
			temp.clear();
			temp.addAll(match_sts);
			match_sts.clear();
			
			//Get the next command to look at (for reference)
			int i1 = cmdidx + l - 1;
			if(breakidxs.contains(i1)) return; //Shouldn't happen thanks to maxlen, but just to double check.
			NUSALSeqCommand cmd1 = cmdlist.get(i1);
			blen += cmd1.getSizeInBytes();
			
			for(Integer st : temp){
				//See if it still matches for this length 
				int i2 = st + l - 1;
				if(i2 >= ccount) continue; //Continues past end of sequence.
				if(breakidxs.contains(i2)) continue; //Hits a breakpoint.
				//if(match_sts.contains(i2)) continue; //Intersects instance previously found.
				NUSALSeqCommand ocmd = cmdlist.get(st);
				NUSALSeqCommand cmd2 = cmdlist.get(i2);
				int comptick = ocmd.getTickAddress();
				if(commandsEquivalent(cmd1, cmd2, basetick, comptick)){
					match_sts.add(st);
				}
			}
			
			if(!match_sts.isEmpty()){
				compop = new CompOption();
				compop.starts.add(cmdidx);
				compop.starts.addAll(match_sts);
				compop.len = l;

				compop.sub_size = blen;
				removeOverlappingInstances(compop);
				//compop.updateByteSavings();
				//compop.updateCoverage();
				
				//findAllInstances(compop); NOOOOOOOO
				if(compop.bytes_saved > 0){
					cands_list.add(compop);
					for(Integer cstart : compop.starts) cands_map.addValue(cstart, compop);	
					//System.err.println("Candidate added -- cmdidx = " + cmdidx + ", len = " + compop.len + ", blen = " + blen);
					//compop.debug_printerr();
				}
			}
		}
	}
	
	private void linearize(){
		//Annotate ticks too.
		int c_count = 0;
		for(TimeBlock tb : input){
			timebreaks.add(tb.base_tick);
			timebreaks.addAll(tb.tick_coords);
			for(NUSALSeqCommandChunk cc : tb.main_track){
				c_count += cc.getTotalCommandCount();
			}
		}
		
		int tick = 0; int i = 0;
		int st = 0;
		cmdlist = new ArrayList<NUSALSeqCommand>(c_count);
		og_locs = new int[c_count][2];
		breakidxs = new HashSet<Integer>();
		int b = 0, c = 0, k = 0;
		for(TimeBlock tb : input){
			i = 0; c = 0;
			breakidxs.add(cmdlist.size());
			for(NUSALSeqCommandChunk cc : tb.main_track){
				breakidxs.add(cmdlist.size());
				tick = tb.tick_coords.get(i++);
				cc.linearizeTo(cmdlist);
				int sz = cmdlist.size();
				for(int j = st; j < sz; j++){
					NUSALSeqCommand cmd = cmdlist.get(j);
					cmd.setTickAddress(tick);
					tick += cmd.getSizeInTicks(); //Duh
					og_locs[k][0] = b;
					og_locs[k++][1] = c;
					if(cmd.getFunctionalType() == NUSALSeqCmdType.END_READ){
						breakidxs.add(j);
					}
				}
				st = sz;
				c++;
			}
			b++;
		}
		//System.err.println("Compressor -- estimated commands: " + c_count);
		//System.err.println("Compressor -- actual commands: " + cmdlist.size());
		breakidxs.remove(0);
		
		//System.err.println("--DEBUG-- Linearized Track:");
		//for(int j = 0; j < c_count; j++) System.err.println(j + "\t" + cmdlist.get(j).toMMLCommand());
	}
	
	private void rebuildTimeblocks(){
		int c = 0; //chunk pos
		int i = 0; //global cmd pos
		NUSALSeqCommandChunk chunk = null;
		NUSALSeqCommand cmd = null;
		int cmax = cmdlist.size();
		//System.err.println("cmax = " + cmax + ", og_locs.length = " + og_locs.length);
		for(int t = 0; t < output.length; t++){
			if(i >= cmax) break;
			c = -1;
			while(og_locs[i][0] == t){
				cmd = cmdlist.get(i);
				if(og_locs[i][1] != c){
					//New chunk
					if(chunk != null) chunk.linkSequentialCommands();
					chunk = new NUSALSeqCommandChunk();
					output[t].addChunk(cmd.getTickAddress(), chunk);
					c = og_locs[i][1];
				}
				//Check calls
				NUSALSeqCommand call = calls.get(i);
				if(call != null) chunk.addCommand(call);
				
				//Check tosub
				if(!tosub.contains(i)) chunk.addCommand(cmd);
				
				i++;
				if(i >= cmax) break;
			}
			output[t].cleanEmptyChunks();
		}
	}
	
	private void genOutputTimeblocks(){
		int tbcount = input.size();
		output = new TimeBlock[tbcount];
		int i = 0;
		for(TimeBlock tbin : input){
			TimeBlock tbout = new TimeBlock();
			output[i++] = tbout;
			tbout.base_tick = tbin.base_tick;
			tbout.sublevels = tbin.sublevels;
		}
	}
	
	private void cleanup(){
		cmdlist.clear(); cmdlist = null;
		input = null;
		og_locs = null;
		timebreaks.clear(); timebreaks = null;
		cands_list.clear(); cands_list = null;
		cands_map.clearValues(); cands_map = null;
		total_cov = null;
		tosub.clear(); tosub = null;
		calls.clear(); calls = null;
		output = null;
	}
	
	public List<TimeBlock> compress(List<TimeBlock> in){
		if(in == null) return null;
		input = in;
		timebreaks = new HashSet<Integer>();
		
		linearize();
		genOutputTimeblocks();
		doComp_greedy_global(); //Only option I have so far.
		rebuildTimeblocks();

		List<TimeBlock> out = new ArrayList<TimeBlock>(in.size());
		for(int i = 0; i < output.length; i++){
			out.add(output[i]);
		}
		
		cleanup();
		return out;
	}
	
}
