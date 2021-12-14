package waffleoRai_SeqSound.n64al.cmd;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import waffleoRai_SeqSound.n64al.NUSALSeq;
import waffleoRai_SeqSound.n64al.NUSALSeq.UnrecognizedCommandException;
import waffleoRai_SeqSound.n64al.NUSALSeqCmdType;
import waffleoRai_SeqSound.n64al.NUSALSeqCommand;
import waffleoRai_SeqSound.n64al.NUSALSeqCommandSource;
import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.CoverageMap1D;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.StackStack;

public class NUSALSeqReader implements NUSALSeqCommandSource{
	
	/*--- Constants ---*/
	
	public static final int PARSEMODE_SEQ = 0;
	public static final int PARSEMODE_CHANNEL = 1;
	public static final int PARSEMODE_LAYER = 2;
	
	/*--- Instance Variables ---*/
	
	private FileBuffer data;
	private Map<Integer, NUSALSeqCommand> cmdmap;
	private Map<String, NUSALSeqCommand> lblmap;
	private Map<Integer, Integer> tickmap; //Address -> tick (first tick it can appear at)
	
	private CoverageMap1D cmd_coverage;
	private CoverageMap1D[] ch_shortnotes;
	
	private StackStack<Integer> branch_stack;
	
	/*--- Initialization ---*/
	
 	public NUSALSeqReader(FileBuffer seqData){
		if(seqData == null) throw new IllegalArgumentException("Data source cannot be null!");
		data = seqData;
		cmdmap = new TreeMap<Integer, NUSALSeqCommand>();
		lblmap = new HashMap<String, NUSALSeqCommand>();
		tickmap = new TreeMap<Integer, Integer>();
		ch_shortnotes = new CoverageMap1D[16];
		branch_stack = new StackStack<Integer>();
	}
	
	/*--- Read ---*/
	
	public static NUSALSeqReader readMMLScript(Reader input){
		//TODO
		return null;
	}
	
	private int maxDataSize(int pos, boolean arr16){
		int max = arr16?512:256;
		
		//Look for known commands that would mark the data end. Probably.
		if(cmd_coverage != null){
			for(int i = 0; i < max; i++){
				if(cmd_coverage.isCovered(pos+i)){
					//New end.
					if(arr16 && (i % 2 != 0)) i &= ~1;
					return i;
				}
			}
		}
		return max;
	}
	
	private NUSALSeqDataCommand tryReadData(NUSALSeqDataRefCommand cmd){
		//Most data structs are fixed size, but some are indexed w/ Q
		//And some also contain jump addresses, so that complicates things too.
		if(cmd == null) return null;
		
		NUSALSeqCmdType ecmd = cmd.getCommand();
		NUSALSeqDataCommand dcmd = null;
		int dsz = 0;
		int daddr = cmd.getBranchAddress();
		if(ecmd == NUSALSeqCmdType.CALL_TABLE){
			//Indexed by Q, contains seq jump targets
			dsz = maxDataSize(daddr, true);
			//Parse all of these as seq branches... (yuck)
			int caddr = cmd.getAddress();
			System.err.println("NUSALSeqReader.tryReadData || tblcall command @ 0x" + Integer.toHexString(caddr) + " | Attempting to read table...");
			int maxentries = dsz >>> 1;
			data.setCurrentPosition(daddr);
			List<int[]> branches = new LinkedList<int[]>();
			int dtick = tickmap.get(caddr);
			for(int e = 0; e < maxentries; e++){
				int val = (int)data.nextShort();
				//Is it a valid offset?
				if(val >= 0 && val < data.getFileSize()){
					branches.add(new int[]{val, dtick});
				}
				else{
					dsz = e << 1;
					break;
				}
			}
			if(!branches.isEmpty()){
				try{
					readTrack(branches, new ArrayList<List<int[]>>(16), null, PARSEMODE_SEQ, -1, -1);}
				catch(UnrecognizedCommandException ex){
					//Probably just not a real jump address.
					//Figure out which address triggered it...
					int badaddr = branch_stack.peekBottom();
					data.setCurrentPosition(daddr);
					for(int e = 0; e < maxentries; e++){
						int val = (int)data.nextShort();
						if(val == badaddr || val < 0 || val >= data.getFileSize()){
							dsz = e << 1;
							break;
						}
					}
				}
			}
		}
		else if(ecmd == NUSALSeqCmdType.SET_DYNTABLE){
			//Indexed by Q, contains layer jump targets
			dsz = maxDataSize(daddr, true);
			int caddr = cmd.getAddress();
			System.err.println("NUSALSeqReader.tryReadData || dyntable command @ 0x" + Integer.toHexString(caddr) + " | Attempting to read table...");
			//Guess channel.
			int ch = -1;
			for(int i = 0; i < 16; i++){
				if(cmd.channelUsed(i)){
					ch = i;
					break;
				}
			}
			
			if(ch >= 0){
				int maxentries = dsz >>> 1;
				data.setCurrentPosition(daddr);
				List<int[]> branches = new LinkedList<int[]>();
				int dtick = tickmap.get(caddr);	
				for(int e = 0; e < maxentries; e++){
					int val = (int)data.nextShort();
					//Is it a valid offset?
					if(val >= 0 && val < data.getFileSize()){
						branches.add(new int[]{val, dtick});
					}
					else{
						dsz = e << 1;
						break;
					}
				}
				if(!branches.isEmpty()){
					try{
						readTrack(branches, null, ch_shortnotes[ch], PARSEMODE_LAYER, ch, 4);}
					catch(UnrecognizedCommandException ex){
						int badaddr = branch_stack.peekBottom();
						data.setCurrentPosition(daddr);
						for(int e = 0; e < maxentries; e++){
							int val = (int)data.nextShort();
							if(val == badaddr || val < 0 || val >= data.getFileSize()){
								dsz = e << 1;
								break;
							}
						}
					}
				}
			}

		}
		else if(ecmd == NUSALSeqCmdType.LOAD_P_TABLE || ecmd == NUSALSeqCmdType.STORE_TO_SELF_P){
			//Indexed by Q, u16 array
			dsz = maxDataSize(daddr, true);
		}
		else if(ecmd == NUSALSeqCmdType.STORE_TO_SELF || ecmd == NUSALSeqCmdType.LOAD_FROM_SELF){
			//Indexed by Q, u8 array
			dsz = maxDataSize(daddr, false);
		}
		else{
			//Fixed size, data
			dsz = cmd.getExpectedDataSize();
		}
		if(dsz <= 0) return null;
		dcmd = new NUSALSeqDataCommand(dsz);
		dcmd.setAddress(daddr);
		byte[] darr = dcmd.getDataArray();
		for(int i = 0; i < dsz; i++){
			darr[i] = data.getByte(daddr+i);
		}
		if(cmd_coverage != null){
			cmd_coverage.addBlock(daddr, daddr+dsz);
		}
		
		return dcmd;
	}
	
	private void linkReferences(){
		if(cmdmap.isEmpty()) return;
		if(cmd_coverage != null) cmd_coverage.mergeBlocks();
		
		List<Integer> alist = new ArrayList<Integer>(cmdmap.size());
		alist.addAll(cmdmap.keySet());
		//Collections.sort(alist);
		
		lblmap.clear();
		for(Integer addr : alist){
			NUSALSeqCommand cmd = cmdmap.get(addr);
			if(cmd.getLabel() != null) lblmap.put(cmd.getLabel(), cmd);
		}
		Set<Integer> checked = new TreeSet<Integer>();
		while(checked.size() < cmdmap.size()){
			for(Integer addr : alist){
				if(checked.contains(addr)) continue;
				NUSALSeqCommand cmd = cmdmap.get(addr);
				if(cmd instanceof NUSALSeqReferenceCommand){
					int refaddr = cmd.getBranchAddress();
					NUSALSeqCommand trg = cmdmap.get(refaddr);
					if(trg != null){
						cmd.setReference(trg);
						//Give the referenced command a label, if it doesn't already have one.
						if(trg.getLabel() == null){
							int n = 0;
							String l = trg.genCtxLabelSuggestion() + String.format("%03d", n);
							while(lblmap.containsKey(l)){
								n++;
								l = trg.genCtxLabelSuggestion() + String.format("%03d", n);
							}
							trg.setLabel(l);
							lblmap.put(l, trg);
						}
					}
					else {
						if(cmd instanceof NUSALSeqDataRefCommand){
							//try to read in data.
							//	Data shouldn't be caught by initial parse cycle, so have to be caught here.
							NUSALSeqDataRefCommand drcmd = (NUSALSeqDataRefCommand)cmd;
							NUSALSeqDataCommand dcmd = tryReadData(drcmd);
							if(dcmd == null){
								System.err.println("NUSALSeqReader.linkReferences || WARNING: "
										+ "The following reference could not be resolved: 0x" + Integer.toHexString(refaddr));
							}
							else{
								drcmd.setReference(dcmd);
								
								//Label
								if(dcmd.getLabel() == null){
									int n = 0;
									String l = dcmd.genCtxLabelSuggestion() + String.format("%03d", n);
									while(lblmap.containsKey(l)){
										n++;
										l = dcmd.genCtxLabelSuggestion() + String.format("%03d", n);
									}
									dcmd.setLabel(l);
									lblmap.put(l, dcmd);
								}
								
								//Add
								cmdmap.put(refaddr, drcmd);
								checked.add(refaddr);
							}
						}
						else{
							System.err.println("NUSALSeqReader.linkReferences || WARNING: "
									+ "The following reference could not be resolved: 0x" + Integer.toHexString(refaddr));	
						}
					}
				}
				checked.add(addr);
			}	
			alist.clear();
			alist.addAll(cmdmap.keySet());
		}
		
	}
	
	private boolean listHasAddr(List<int[]> list, int addr){
		if(list == null) return false;
		for(int[] iarr : list){
			if(iarr[0] == addr) return true;
		}
		return false;
	}
	
	private void readTrack(List<int[]> starts, List<List<int[]>> childlists, CoverageMap1D parentmap, int mode, int chidx, int lidx){
		int pos = 0;
		int tick = 0;
		NUSALSeqCommand cmd = null, prev_cmd = null;
		LinkedList<int[]> branches = new LinkedList<int[]>();
		if(starts != null && !starts.isEmpty()) branches.addAll(starts);
		int[] pp = null;
		if(!branches.isEmpty()){
			pp = branches.pop();
			pos = pp[0]; tick = pp[1];
		}
		
		String modestr = "sequence";
		if(mode == PARSEMODE_CHANNEL){
			modestr = "channel";
			parentmap = new CoverageMap1D();
		}
		else if(mode == PARSEMODE_LAYER) modestr = "layer";
		
		//Finish implementing long/short read switch. Channel maps when in short mode, passes map to voices
		//	voices then need to check mode.
		boolean l_shortnotes = false;
		int sn_starttick = -1; 
		if(!branch_stack.loadState(pos)) branch_stack.clear();
		branch_stack.push(pos);
		while(true){
			//Has this command already been seen?
			if(cmdmap.containsKey(pos)){
				if(!branches.isEmpty()){
					//Pop branch
					prev_cmd = null;
					pp = branches.pop();
					pos = pp[0]; tick = pp[1];
					
					//Does the branch stack have a stack for this one already?
					if(!branch_stack.loadState(pos)) branch_stack.clear();
					branch_stack.push(pos);
					
					continue;
				}
				else break;
			}
			
			//Parse
			switch(mode){
			case PARSEMODE_SEQ:
				cmd = SeqCommands.parseSequenceCommand(data.getReferenceAt(pos));
				cmd.flagSeqUsed();
				break;
			case PARSEMODE_CHANNEL:
				cmd = ChannelCommands.parseChannelCommand(data.getReferenceAt(pos));
				cmd.flagChannelUsed(chidx);
				break;
			case PARSEMODE_LAYER:
				l_shortnotes = (parentmap != null) && parentmap.isCovered(tick);
				cmd = LayerCommands.parseLayerCommand(data.getReferenceAt(pos), l_shortnotes);
				cmd.flagLayerUsed(chidx, lidx);
				break;
			}
			if(cmd == null) throw new NUSALSeq.UnrecognizedCommandException(data.getByte(pos), pos, modestr);
			cmdmap.put(pos, cmd);
			cmd_coverage.addBlock(pos, pos + cmd.getSizeInBytes());
			cmd.setAddress(pos);
			tickmap.put(pos, tick);
			
			//Analyze logic
			if(prev_cmd != null) prev_cmd.setSubsequentCommand(cmd);
			prev_cmd = cmd;
			if(cmd.isBranch()){
				//Is it a channel start command, or seq branch?
				NUSALSeqCmdType e_cmd = cmd.getCommand();
				if(e_cmd == NUSALSeqCmdType.CHANNEL_OFFSET || e_cmd == NUSALSeqCmdType.CHANNEL_OFFSET_REL){
					int ch = cmd.getParam(0);
					int addr = cmd.getBranchAddress();
					List<int[]> chlist = childlists.get(ch);
					if(!listHasAddr(chlist, addr)) chlist.add(new int[]{addr, tick});
					pos += cmd.getSizeInBytes();
					tick += cmd.getSizeInTicks();
					
					branch_stack.saveState(addr);
				}
				else if(e_cmd == NUSALSeqCmdType.VOICE_OFFSET || e_cmd == NUSALSeqCmdType.VOICE_OFFSET_REL){
					int ch = cmd.getParam(0);
					int addr = cmd.getBranchAddress();
					List<int[]> chlist = childlists.get(ch);
					if(!listHasAddr(chlist, addr)) chlist.add(new int[]{addr, tick});
					pos += cmd.getSizeInBytes();
					tick += cmd.getSizeInTicks();
					
					branch_stack.saveState(addr);
				}
				else{
					int addr = cmd.getBranchAddress();
					
					//If jump is conditional, continue to next command.
					if(e_cmd != NUSALSeqCmdType.BRANCH_ALWAYS && e_cmd != NUSALSeqCmdType.BRANCH_ALWAYS_REL){
						if(!listHasAddr(branches, addr)){
							branches.add(new int[]{addr, tick});
							branch_stack.saveState(addr);
						}
						pos += cmd.getSizeInBytes();
					}
					else{
						//Take jump.
						pos = addr;
					}
					tick += cmd.getSizeInTicks();
				}
			}
			else{
				//If it is an end, then pop branch or break
				if(cmd.getCommand() == NUSALSeqCmdType.END_READ){
					if(!branches.isEmpty()){
						//Pop branch
						prev_cmd = null;
						pp = branches.pop();
						pos = pp[0]; tick = pp[1];
						
						if(!branch_stack.loadState(pos)) branch_stack.clear();
						branch_stack.push(pos);
						
						continue;
					}
					else break;
				}
				if(cmd.getCommand() == NUSALSeqCmdType.SHORTNOTE_ON){
					if(sn_starttick < 0){
						sn_starttick = tick;
						if(ch_shortnotes[chidx] == null) ch_shortnotes[chidx] = new CoverageMap1D();
					}
				}
				else if(cmd.getCommand() == NUSALSeqCmdType.SHORTNOTE_OFF){
					if(sn_starttick >= 0){
						ch_shortnotes[chidx].addBlock(sn_starttick, tick);
						sn_starttick = -1;
					}
				}
				//Otherwise, just advance to next command.
				pos += cmd.getSizeInBytes();
				tick += cmd.getSizeInTicks();
			}
		}
		if(sn_starttick >= 0){
			ch_shortnotes[chidx].addBlock(sn_starttick, tick);
			sn_starttick = -1;
		}
		
		//If in seq or channel mode, do children
		if(mode == PARSEMODE_SEQ){
			List<List<int[]>> vlists = new ArrayList<List<int[]>>(4);
			for(int i = 0; i < 4; i++)vlists.add(new LinkedList<int[]>());
			for(int i = 0; i < 16; i++){
				readTrack(childlists.get(i), vlists, null, PARSEMODE_CHANNEL, i, -1);
				
				//Clear voice lists for next channel.
				for(int j = 0; j < 4; j++) vlists.get(j).clear();
			}
		}
		else if(mode == PARSEMODE_CHANNEL){
			//Just use the parentmap local variable to fill for a channel.
			for(int i = 0; i < 4; i++){
				readTrack(childlists.get(i), null, ch_shortnotes[chidx], PARSEMODE_LAYER, chidx, i);
			}
		}
		
		
	}
	
	public void preParse(){
		cmdmap.clear();
		cmd_coverage = new CoverageMap1D();
		branch_stack.clear();
		
		List<List<int[]>> clists = new ArrayList<List<int[]>>(16);
		readTrack(null, clists, null, PARSEMODE_SEQ, -1, -1);
		
		linkReferences();
	}
	
	/*--- Getters ---*/
	
	public BufferReference getDataReference(int addr){
		return data.getReferenceAt(addr);
	}
	
	public NUSALSeqCommand getCommandAt(int addr){
		//This version returns null if not already parsed as it needs context...
		return cmdmap.get(addr);
	}
	
	public NUSALSeqCommand getSeqCommandAt(int addr){
		NUSALSeqCommand cmd = cmdmap.get(addr);
		if(cmd != null) return cmd;
		
		//Else, parse from data
		cmd = SeqCommands.parseSequenceCommand(data.getReferenceAt(addr));
		if(cmd == null) return null;
		cmdmap.put(addr, cmd);
		
		return cmd;
	}
	
	public NUSALSeqCommand getChannelCommandAt(int addr){
		NUSALSeqCommand cmd = cmdmap.get(addr);
		if(cmd != null) return cmd;
		
		cmd = ChannelCommands.parseChannelCommand(data.getReferenceAt(addr));
		if(cmd == null) return null;
		cmdmap.put(addr, cmd);
		
		return cmd;
	}
	
	public NUSALSeqCommand getLayerCommandAt(int addr){
		return getLayerCommandAt(addr, false);
	}
	
	public NUSALSeqCommand getLayerCommandAt(int addr, boolean shortNotes){
		NUSALSeqCommand cmd = cmdmap.get(addr);
		if(cmd != null) return cmd;
		
		cmd = LayerCommands.parseLayerCommand(data.getReferenceAt(addr), shortNotes);
		if(cmd == null) return null;
		cmdmap.put(addr, cmd);
		
		return cmd;
	}
	
	public Map<Integer, NUSALSeqCommand> getSeqLevelCommands() {
		// TODO Auto-generated method stub
		return null;
	}
	
	public List<Integer> getAllAddresses() {
		List<Integer> list = new ArrayList<Integer>(cmdmap.size()+1);
		list.addAll(cmdmap.keySet());
		Collections.sort(list);
		return list;
	}
	
	/*--- Setters ---*/
	
	public void clearCachedCommandsBetween(int stAddr, int len){
		LinkedList<Integer> list = new LinkedList<Integer>();
		list.addAll(cmdmap.keySet());
		Collections.sort(list);
		
		int ed = stAddr + len;
		while(!list.isEmpty()){
			int pos = list.pop();
			if(pos < stAddr) continue;
			if(pos >= ed) return;
			cmdmap.remove(pos);
		}
		
	}
	
	public void clearCachedCommands(){cmdmap.clear();}
	
	/*--- Utils ---*/
	
	public static int readVLQ(BufferReference ptr){
		int b0 = Byte.toUnsignedInt(ptr.getByte());
		if((b0 & 0x80) != 0){
			//Two bytes
			int b1 = Byte.toUnsignedInt(ptr.getByte(1));
			return ((b0 & 0x7f) << 8) | b1;
		}
		return b0;
	}

	
	public void printMeTo(Writer out) throws IOException {
		// TODO Auto-generated method stub
		
	}

}