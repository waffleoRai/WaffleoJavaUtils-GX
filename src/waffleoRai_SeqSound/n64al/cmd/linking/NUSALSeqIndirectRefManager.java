package waffleoRai_SeqSound.n64al.cmd.linking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import waffleoRai_SeqSound.n64al.NUSALSeqCmdType;
import waffleoRai_SeqSound.n64al.NUSALSeqCommand;
import waffleoRai_SeqSound.n64al.cmd.DataCommands;
import waffleoRai_SeqSound.n64al.cmd.NUSALSeqDataCommand;
import waffleoRai_SeqSound.n64al.cmd.NUSALSeqDataRefCommand;
import waffleoRai_SeqSound.n64al.cmd.NUSALSeqPtrTableData;
import waffleoRai_SeqSound.n64al.cmd.NUSALSeqReferenceCommand;
import waffleoRai_SeqSound.n64al.cmd.NUSALSeqReader.DataLater;
import waffleoRai_SeqSound.n64al.cmd.NUSALSeqReader.ParseContext;
import waffleoRai_SeqSound.n64al.cmd.NUSALSeqReader.ParseLater;
import waffleoRai_Utils.Ref;

public class NUSALSeqIndirectRefManager {
	
	/*--- Instance Variables ---*/
	
	private NUSALSeqReadContext readerState;
	
	private int maxAddress = 0; //File end. For quick lookup.
	
	private Map<Integer, IndirectLink> indirLinks;
	private Map<Integer, PTableLink> pTables;
	
	/*--- Init ---*/
	
	public NUSALSeqIndirectRefManager(NUSALSeqReadContext ctx) {
		indirLinks = new HashMap<Integer, IndirectLink>();
		pTables = new HashMap<Integer, PTableLink>();
		readerState = ctx;
		if(readerState != null) {
			if(readerState.data != null) maxAddress = (int)readerState.data.getFileSize();
		}
	}
	
	/*--- Setters ---*/
	
	public void clear() {
		indirLinks.clear();
		pTables.clear();
	}
	
	/*--- Queues ---*/
	
	private boolean addPTable(int ptblAddr, NUSALSeqCommand referee) {
		//Also do initial scan upon loading and add missed targets to indirLinks
		PTableLink ptlink = pTables.get(ptblAddr);
		if(ptlink != null) {
			ptlink.addReferee(referee);
		}
		else {
			ptlink = new PTableLink(readerState, ptblAddr, referee);
			ptlink.getPTable().setLabel(String.format(".data%03d", readerState.data_lbl_count++));
			pTables.put(ptblAddr, ptlink);
			scanPTable(ptlink);
		}
		
		return true;
	}
	
	private boolean handleKnownPTable(int ptblAddr, int type, NUSALSeqCommand referee) {
		//Also do initial scan upon loading and add missed targets to indirLinks
		PTableLink ptlink = pTables.get(ptblAddr);
		if(ptlink != null) {
			ptlink.addReferee(referee);
		}
		else {
			ptlink = new PTableLink(readerState, ptblAddr, referee);
		}
		
		ptlink.getPTable().setLabel(String.format(".data%03d", readerState.data_lbl_count++));
		ptlink.setTableType(type);
		resolvePTable(ptlink);
		
		return true;
	}
	
	private boolean addIndirectLink(int addr, NUSALSeqCommand referee) {
		IndirectLink ilink = indirLinks.get(addr);
		if(ilink == null) {
			ilink = new IndirectLink();
			ilink.setAddress(addr);
			indirLinks.put(addr, ilink);
		}
		ilink.addReferee(referee);
		
		return true;
	}
	
	private boolean addToBranchQueue(int addr, int tick, ParseContext ctx) {
 		//Reject if address is nonsense.
 		if(addr < 0) return false;
 		if(addr >= maxAddress) return false;
 		//if(getCommandOver(addr) != null) return false;
 		
 		readerState.branch_queue.add(new ParseLater(addr, tick, ctx));
 		return true;
 	}
	
	private boolean addToDataQueue(int datAddr, ParseContext ctxGuess, NUSALSeqCommand referee) {
 		if(datAddr < 0) return false;
 		if(datAddr >= maxAddress) return false;
 		
 		//If already in queue, add referee
 		DataLater dat = readerState.data_parse.get(datAddr);
 		if(dat == null) {
 			dat = new DataLater();
 			dat.data_address = datAddr;
 			dat.ctx_guess = ctxGuess;
 			readerState.data_parse.put(datAddr, dat);
 		}

		if(referee != null) dat.referees.add(referee);
 		return true;
 	}
	
	/*--- Simple References ---*/
	
	private void initialProcessRefCommand(NUSALSeqReferenceCommand cmd) {
		//Send commands here that have never been ref checked.
		
		//First, see if already found.
		int refAddr = cmd.getBranchAddress();
		if(isValidAddress(refAddr)) {
			NUSALSeqCommand target = getCommandOver(refAddr);
			if(target != null) {
				int taddr = target.getAddress();
				int offset = refAddr - taddr;
				cmd.setReference(target);
				if(offset > 0) {
					if(cmd instanceof NUSALSeqDataRefCommand) {
						NUSALSeqDataRefCommand drcmd = (NUSALSeqDataRefCommand)cmd;
						drcmd.setDataOffset(offset);
					}
				}

				if(target.getLabel() == null){
					if(offset > 0) {
						target.setLabel(String.format(".dyncmd%03d", readerState.ecmd_lbl_count++));
						readerState.lblmap.put(target.getLabel(), target);
					}
					else {
						int[] ctxt = target.getFirstUsed();
						boolean isSub = (cmd.getFunctionalType()== NUSALSeqCmdType.CALL);
						if(ctxt[0] < 0) reflink_LabelSeqBlock(target, isSub);
						else{
							if(ctxt[1] < 0) reflink_LabelChanBlock(target, ctxt[0], isSub);
							else reflink_LabelLyrBlock(target, ctxt[0], ctxt[1], isSub);
						}	
					}
				}
				readerState.rchecked.add(cmd.getAddress());
			}
			else {
				//Deal with the easy targets. If it's a weird one, store internally.
				ParseContext pctx = null;
				switch(cmd.getFunctionalType()) {
	 			//1. Easy code indicators
	 			case BRANCH_IF_LTZ_REL:
	 			case BRANCH_IF_EQZ_REL:
	 			case BRANCH_ALWAYS_REL:
	 			case BRANCH_IF_GTEZ:
	 			case BRANCH_IF_LTZ:
	 			case BRANCH_IF_EQZ:
	 			case BRANCH_ALWAYS:
	 				addToBranchQueue(refAddr, cmd.getTickAddress(), ParseContext.fromCommand(cmd));
	 				//readerState.rchecked.add(cmd.getAddress());
	 				return;
	 				
	 			case CHANNEL_OFFSET:
	 			case CHANNEL_OFFSET_REL:
	 			case CHANNEL_OFFSET_C:
	 				pctx = ParseContext.fromCommand(cmd);
	 				pctx.setChannel(cmd.getParam(0));
	 				addToBranchQueue(refAddr, cmd.getTickAddress(), pctx);
	 				//readerState.rchecked.add(cmd.getAddress());
	 				return;
	 				
	 			case VOICE_OFFSET:
	 			case VOICE_OFFSET_REL:
	 				pctx = ParseContext.fromCommand(cmd);
	 				pctx.setLayer(pctx.getChannel(), cmd.getParam(0));
	 				addToBranchQueue(refAddr, cmd.getTickAddress(), pctx);
	 				//readerState.rchecked.add(cmd.getAddress());
	 				return;
	 				
	 			//2. Easy data indicators
	 			case LOAD_SEQ:
	 			case LOAD_SHORTTBL_GATE:
	 			case LOAD_SHORTTBL_VEL:
	 			case SET_CH_FILTER:
	 			case CH_ENVELOPE:
	 			case CH_LOAD_PARAMS:
	 			case L_ENVELOPE:
	 				addToDataQueue(refAddr, ParseContext.fromCommand(cmd), cmd);
	 				readerState.rchecked.add(cmd.getAddress());
	 				return;
	 				
	 			//3. Kinda tricky ones
	 			case STORE_TO_SELF_S:
	 			case STORE_TO_SELF_C:
	 			case LOAD_FROM_SELF:
	 				//Will usually have a default immediate target to regular data,
	 				// but it is not uncommon for target address to be overwritten.
	 				addToDataQueue(refAddr, ParseContext.fromCommand(cmd), cmd);
	 				readerState.rchecked.add(cmd.getAddress());
	 				break;
	 				
	 			case CALL_TABLE: //CALL_TABLE command itself - can automatically type and resolve
	 				handleKnownPTable(refAddr, NUSALSeqLinking.P_TYPE_SEQ_CODE, cmd);
	 				readerState.rchecked.add(cmd.getAddress());
	 				break;
	 				
	 			case LOAD_P_TABLE:
	 				//TODO What about tables of two-byte literals?
	 				//Target is probably a ptable. These are a pain.
	 				addPTable(refAddr, cmd);
	 				readerState.rchecked.add(cmd.getAddress());
	 				break;
	 				
	 			//4. Very tricky ones
	 			case STORE_TO_SELF_P:
	 			case LOAD_IMM_P:
	 			case SET_DYNTABLE:
	 				addIndirectLink(refAddr, cmd);
	 				readerState.rskip.add(cmd.getAddress()); //Not resolved, but don't need to run THAT command again.
	 				break;
	 				
	 			default:
	 				//No idea. Save as indirect.
	 				addIndirectLink(refAddr, cmd);
	 				readerState.rskip.add(cmd.getAddress());
	 				break;
	 			}
			}
		}
		else {
			//Supposed to be a ref command, but target is not a valid address.
			//Assume literal or data?
			readerState.rchecked.add(cmd.getAddress());
		}
		
	}
	
	/*--- Complex References ---*/
	
	private void resolveIndirLink(IndirectLink ilink) {
		ParseContext pctx = new ParseContext();
		NUSALSeqCommand ref1 = null;
		DataLater datl = null;
		Set<NUSALSeqCommand> referees = ilink.getRefereeSet(); //For linking back
		int addr = ilink.getAddress();
		boolean okay = false;
		switch(ilink.getLinkType()) {
		case NUSALSeqLinking.P_TYPE_SEQ_CODE:
			pctx.setAsSeq();
			okay = addToBranchQueue(addr, ilink.getRefereeTick(), pctx);
			break;
		case NUSALSeqLinking.P_TYPE_CH_CODE:
			pctx.setAnyChannel();
			okay = addToBranchQueue(addr, ilink.getRefereeTick(), pctx);
			break;
		case NUSALSeqLinking.P_TYPE_LYR_CODE:
			pctx.setAnyLayer();
			okay = addToBranchQueue(addr, ilink.getRefereeTick(), pctx);
			break;
		case NUSALSeqLinking.P_TYPE_GENERAL_DATA:
		case NUSALSeqLinking.P_TYPE_QDATA:
			ref1 = ilink.getSingleReferee();
			pctx = ParseContext.fromCommand(ref1);
			okay = addToDataQueue(addr, pctx, null);
			datl = readerState.data_parse.get(addr);
			datl.referees.addAll(referees);
			break;
		case NUSALSeqLinking.P_TYPE_PDATA:
			//TODO Check what it is.
			NUSALSeqCommand trace = ilink.getTraceTarget();
			if(trace != null) {
				if(trace.getFunctionalType() == NUSALSeqCmdType.CALL_DYNTABLE) {
					okay = handleKnownPTable(ilink.getAddress(), NUSALSeqLinking.P_TYPE_CH_CODE, ilink.getSingleReferee());
				}
			}
			break;
		case NUSALSeqLinking.P_TYPE_LITERAL:
			okay = true;
			break;
		case NUSALSeqLinking.P_TYPE_STS_TARG:
			//TODO Is this actually used?
			break;
		}
		if(okay) {
			indirLinks.remove(addr);
			for(NUSALSeqCommand ref : referees) {
				readerState.rchecked.remove(ref.getAddress());
			}
		}
	}
	
	private void resolvePTable(PTableLink table) {
		ParseContext pctx = table.determineTargetContext();
		NUSALSeqPtrTableData ptbl = table.getPTable();
		int addr = ptbl.getAddress();
		List<NUSALSeqCommand> referees = ptbl.getReferees();
		
		int tblCount = ptbl.getUnitCount();
		boolean okay = false;
		switch(table.getTableType()) {
		case NUSALSeqLinking.P_TYPE_SEQ_CODE:
			for(int i = 0; i < tblCount; i++) {
				int p = ptbl.getDataValue(i, false);
				if(p > 0) {
					okay = addToBranchQueue(p, ptbl.getTickAddress(), pctx);
				}
				else okay = true;
			}
			readerState.rskip.add(addr);
			break;
		case NUSALSeqLinking.P_TYPE_CH_CODE:
			for(int i = 0; i < tblCount; i++) {
				int p = ptbl.getDataValue(i, false);
				if(p > 0) {
					NUSALSeqCommand over = this.getCommandOver(p);
					if(over == null) {
						okay = addToBranchQueue(p, ptbl.getTickAddress(), pctx);
					}
					else {
						//Check if existing command is valid.
						//If not, shrink table.
						if(p != over.getAddress() || !over.isChannelCommand()) {
							ptbl.resize(i);
							break;
						}
					}
				}
				else okay = true;
			}
			readerState.rskip.add(addr);
			break;
		case NUSALSeqLinking.P_TYPE_LYR_CODE:
			for(int i = 0; i < tblCount; i++) {
				int p = ptbl.getDataValue(i, false);
				if(p > 0) {
					okay = addToBranchQueue(p, ptbl.getTickAddress(), pctx);
				}
				else okay = true;
			}
			readerState.rskip.add(addr);
			break;
		case NUSALSeqLinking.P_TYPE_GENERAL_DATA:
		case NUSALSeqLinking.P_TYPE_QDATA:
		case NUSALSeqLinking.P_TYPE_PDATA:
			for(int i = 0; i < tblCount; i++) {
				int p = ptbl.getDataValue(i, false);
				if(p > 0) {
					okay = addToDataQueue(addr, pctx, ptbl);
				}
				else okay = true;
			}
			readerState.rskip.add(addr);
			break;
		case NUSALSeqLinking.P_TYPE_LITERAL:
			okay = true;
			readerState.rchecked.add(addr); //Doesn't link to anything, so don't need rescan.
			break;
		case NUSALSeqLinking.P_TYPE_STS_TARG:
			//TODO Is this used?
			break;
		}
		
		if(okay) {
			pTables.remove(addr);
			for(NUSALSeqCommand ref : referees) {
				ref.setReference(ptbl);
			}
			readerState.cmdmap.put(addr, ptbl);
			
			//Remove all indirect links too.
			IndirectLink[] tlinks = table.getElementArray();
			if(tlinks != null) {
				for(int i = 0; i < tlinks.length; i++) {
					if(tlinks[i] != null) indirLinks.remove(tlinks[i].getAddress());
				}
			}
		}
	}
	
	private void resizePTable(PTableLink table) {
		//Adjust maximum address.
		int myAddr = table.getPTable().getAddress();
		int tryAddr = myAddr;
		int limAddr = table.getEndLimitAddress();
		while(++tryAddr < limAddr) {
			if(pTables.containsKey(tryAddr)) {
				//Another pending reference
				table.setEndLimitAddress(tryAddr);
				break;
			}
		}
		table.adjustSize(readerState);
	}
	
	private void scanPTable(PTableLink table) {
		//1. Syncs types between table and elements and resolves if applicable
		//2. Links any element references if they have since been found elsewhere
		//		(This triggers a type sync)
		//3. Generates an ilink for any unresolved elements that don't have one yet.
		//4. Resolves table if table type was discovered from step 2
		
		//Does not make any attempt to determine type from reference tree tracing.
		//	That is done elsewhere
		
		resizePTable(table);
		table.reassessType();
		if(table.getTableType() != NUSALSeqLinking.P_TYPE_UNK) {
			resolvePTable(table);
			return;
		}
		
		NUSALSeqPtrTableData ptbl = table.getPTable();
		int eCount = ptbl.getUnitCount();
		IndirectLink[] ilinks = table.getElementArray();
		
		//Targets
		for(int i = 0; i < eCount; i++) {
			//Already linked?
			if(ptbl.getReference(i) != null) continue;
			
			int p = ptbl.getDataValue(i, false);
			if(p == 0) continue; //Null pointer
			
			//1. Is it in the command map now?
			NUSALSeqCommand trg = getCommandOver(p);
			if(trg != null) {
				//Resolve.
				table.setReference(i, trg);
			}
			else {
				if(ilinks[i] == null) {
					addIndirectLink(p, ptbl);
					ilinks[i] = indirLinks.get(p);
				}
			}
		}
		
		//If any updated references identified the ptable, resolve it.
		if(table.getTableType() != NUSALSeqLinking.P_TYPE_UNK) {
			resolvePTable(table);
		}
		
	}
	
	public int countUnresolvedLinks() {
		int count = indirLinks.size();
		for(PTableLink tbl : pTables.values()) {
			IndirectLink[] il = tbl.getElementArray();
			if(il != null) {
				for(int i = 0; i < il.length; i++) {
					if(il[i] == null) count++;
				}
			}
		}
		
		return count;
	}
	
	public int scanUnresolvedLinks() {
		//The main cycle function
		//PTables first (so may get out of doing redundant scans on table members)
		if(!pTables.isEmpty()) {
			List<Integer> addrs = new ArrayList<Integer>(pTables.size());
			addrs.addAll(pTables.keySet());
			Collections.sort(addrs);
			
			for(Integer a : addrs) {
				PTableLink ptlink = pTables.get(a);
				scanPTable(ptlink); //This checks to see if contents have been assigned targets/types
				if(ptlink.getTableType() == NUSALSeqLinking.P_TYPE_UNK) {
					//Still not known, so try backtracing
					NUSALSeqCommand referee = ptlink.getFirstReferee();
					if(referee != null) {
						Ref<NUSALSeqCommand> trace = new Ref<NUSALSeqCommand>();
						int tt = DataCommands.guessPUsageType(referee, trace);
						if(tt != NUSALSeqLinking.P_TYPE_UNK) {
							ptlink.setTableType(tt);
							ptlink.setSTSTarget(trace.data);
							scanPTable(ptlink);
						}
					}
				}
			}
		}
		
		
		//Indirects
		if(!indirLinks.isEmpty()) {
			List<Integer> addrs = new ArrayList<Integer>(indirLinks.size());
			addrs.addAll(indirLinks.keySet());
			Collections.sort(addrs);
			
			for(Integer a : addrs) {
				IndirectLink ilink = indirLinks.get(a);
				if(ilink.tryResolve()) {
					resolveIndirLink(ilink);
				}
			}
		}
		
		return countUnresolvedLinks(); //Returns count of still unresolved links
	}
	
	/*--- Utility ---*/
	
	private void reflink_LabelSeqBlock(NUSALSeqCommand target, boolean isSub){
 		if(target == null) return;
 		String lbl = null;
 		if(isSub){
 			lbl = String.format("sub_seq_%03d", readerState.seq_lbls.subs++);
 		}
 		else{
 			lbl = String.format("seq_block_%03d", readerState.seq_lbls.blocks++);
 		}
 		target.setLabel(lbl);
 	}
 	
 	private void reflink_LabelChanBlock(NUSALSeqCommand target, int ch, boolean isSub){
 		if(target == null) return;
 		String lbl = null;
 		if(isSub){
 			lbl = String.format("sub_ch%X_%03d", ch, readerState.ch_lbls[ch].subs++);
 		}
 		else{
 			lbl = String.format("ch%X_block_%03d", ch, readerState.ch_lbls[ch].blocks++);
 		}
 		target.setLabel(lbl);
 	}
 	
 	private void reflink_LabelLyrBlock(NUSALSeqCommand target, int ch, int lyr, boolean isSub){
 		if(target == null) return;
 		String lbl = null;
 		if(isSub){
 			lbl = String.format("sub_ch%X-ly%d_%03d", ch, lyr, readerState.ly_lbls[ch][lyr].subs++);
 		}
 		else{
 			lbl = String.format("ch%X-ly%d_block_%03d", ch, lyr, readerState.ly_lbls[ch][lyr].blocks++);
 		}
 		target.setLabel(lbl);
 	}
	
	public NUSALSeqCommand getCommandOver(int address){
		int checkAddr = address;
		NUSALSeqCommand cmd = null;
		while(checkAddr >= 0){
			cmd = readerState.cmdmap.get(checkAddr);
			if(cmd != null){
				int cend = cmd.getAddress() + cmd.getSizeInBytes();
				if(address < cend) return cmd;
				return null;
			}
			checkAddr--;
		}
		return null;
	}
	
	public boolean isValidAddress(int value) {
		if(value < 0) return false;
		if(value >= maxAddress) return false;
		return true;
	}
	
	public void processCommandReferences(NUSALSeqCommand cmd) {
		if(cmd == null) return;
 		if(cmd instanceof NUSALSeqReferenceCommand){
 			initialProcessRefCommand((NUSALSeqReferenceCommand)cmd);
 		}
 		else if (cmd instanceof NUSALSeqPtrTableData){
 			//If this is reached, it is presumably a ptable whose type has been resolved
 			//	and members have been queued.
 			//Here, just try to link table members.
 			NUSALSeqPtrTableData ptbl = (NUSALSeqPtrTableData)cmd;
 			int elements = ptbl.getUnitCount();
 			for(int i = 0; i < elements; i++) {
 				NUSALSeqCommand pref = ptbl.getReference(i);
 				if(pref != null) continue;
 				
 				int p = ptbl.getDataValue(i, false);
 				if(p > 0) {
 					pref = getCommandOver(p);
 					if(pref != null) {
 						ptbl.setReference(i, pref);
 						if(pref.getLabel() == null) {
 							if(pref instanceof NUSALSeqDataCommand) {
 								pref.setLabel(String.format(".data%03d", readerState.data_lbl_count++));
 	 						}
 							else {
 								if(pref.isSeqCommand()) {
 									reflink_LabelSeqBlock(pref, false);
 								}
 								else if(pref.isChannelCommand()) {
 									reflink_LabelChanBlock(pref, pref.getFirstChannelUsed(), false);
 								}
 								else {
 									reflink_LabelLyrBlock(pref, 0, 0, false);
 								}
 							}
 						}
 					}
 					else {
 						//Check data queue
 						/*DataLater dl = readerState.data_parse.get(p);
 						if(dl != null) dl.referees.add(ptbl);
 						else {
 							readerState.linkagain_flag = true;
 						}*/
 						readerState.linkagain_flag = true;
 					}
 				}
 				else ptbl.setReference(i, null);
 			}
 		}
 		else readerState.rchecked.add(cmd.getAddress());
		
	}
	

}
