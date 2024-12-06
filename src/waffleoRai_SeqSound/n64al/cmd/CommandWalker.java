package waffleoRai_SeqSound.n64al.cmd;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import waffleoRai_SeqSound.n64al.NUSALSeqCmdType;
import waffleoRai_SeqSound.n64al.NUSALSeqCommand;

public class CommandWalker {
	
	private LinkedList<NUSALSeqCommand> branchQueue;
	private Set<Integer> triedAddr;
	
	private NUSALSeqCommand current;
	
	public CommandWalker() {
		branchQueue = new LinkedList<NUSALSeqCommand>();
		triedAddr = new HashSet<Integer>();
	}
	
	public void initializeWith(NUSALSeqCommand start) {
		branchQueue.clear();
		triedAddr.clear();
		current = start;
		if(current != null) {
			triedAddr.add(current.getAddress());
			switch(current.getFunctionalType()) {
			case BRANCH_IF_LTZ_REL:
			case BRANCH_IF_EQZ_REL:
			case BRANCH_ALWAYS_REL:
			case BRANCH_IF_GTEZ:
			case BRANCH_IF_LTZ:
			case BRANCH_IF_EQZ:
			case BRANCH_ALWAYS:
			case CALL:
				NUSALSeqCommand trg = current.getBranchTarget();
				if(trg != null) {
					branchQueue.add(trg);
				}
				break;
			default: break;
			}
		}
	}
	
	public NUSALSeqCommand next() {
		if(current == null) return null;
		
		NUSALSeqCommand ncmd = null;
		if(current.getFunctionalType() != NUSALSeqCmdType.END_READ) {
			ncmd = current.getSubsequentCommand();
			if(triedAddr.contains(ncmd.getAddress())) ncmd = null;
		}

		if(ncmd != null) {
			current = ncmd;
		}
		else {
			//Try popping
			while(!branchQueue.isEmpty() && ncmd == null) {
				ncmd = branchQueue.pop();
				if(triedAddr.contains(ncmd.getAddress())) ncmd = null;
			}
			current = ncmd;
		}
		
		//Check address and check for branches
		if(current == null) return null;
		triedAddr.add(current.getAddress());
		switch(current.getFunctionalType()) {
		case BRANCH_IF_LTZ_REL:
		case BRANCH_IF_EQZ_REL:
		case BRANCH_ALWAYS_REL:
		case BRANCH_IF_GTEZ:
		case BRANCH_IF_LTZ:
		case BRANCH_IF_EQZ:
		case BRANCH_ALWAYS:
		case CALL:
			NUSALSeqCommand trg = current.getBranchTarget();
			if(trg != null) {
				branchQueue.add(trg);
			}
			break;
		default: break;
		}

		return current;
	}

}
