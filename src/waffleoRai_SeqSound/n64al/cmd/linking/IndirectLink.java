package waffleoRai_SeqSound.n64al.cmd.linking;

import java.util.HashSet;
import java.util.Set;

import waffleoRai_SeqSound.n64al.NUSALSeqCommand;
import waffleoRai_SeqSound.n64al.cmd.DataCommands;
import waffleoRai_Utils.Ref;

public class IndirectLink {
	
	private int addr = -1;
	private Set<NUSALSeqCommand> referees;
	
	//What is this a pointer to? If it is a pointer?
	private int linkType = NUSALSeqLinking.P_TYPE_UNK;
	private NUSALSeqCommand traceTarget = null;
	
	public IndirectLink() {
		referees = new HashSet<NUSALSeqCommand>();
	}
	
	public NUSALSeqCommand getTraceTarget() {return traceTarget;}
	public int getAddress() {return addr;}
	public int getLinkType() {return linkType;}
	public boolean typeResolved(){return (linkType != NUSALSeqLinking.P_TYPE_UNK);}
	public Set<NUSALSeqCommand> getRefereeSet(){return referees;}
	
	public void setAddress(int val) {addr = val;}
	public void setLinkType(int val) {linkType = val;}
	
	public void addReferee(NUSALSeqCommand ref) {
		referees.add(ref);
	}
	
	public NUSALSeqCommand getSingleReferee() {
		NUSALSeqCommand ret = null;
		for(NUSALSeqCommand ref : referees) {
			if(ret != null) {
				if(ref.getAddress() < ret.getAddress()) {
					ret = ref;
				}
			}
			else ret = ref;
		}
		return ret;
	}
	
	public int getRefereeTick() {
		int tick = -1;
		for(NUSALSeqCommand ref : referees) {
			int rtick = ref.getTickAddress();
			if(rtick >= 0) {
				if((tick < 0) || (rtick < tick)) tick = rtick;
			}
		}
		return tick;
	}
	
	public boolean tryResolve() {
		/*Referees to indirect references should be one of:
		 * stps
		 * ldpi
		 * dyntbl (set dyntable)
		 */
		
		Ref<NUSALSeqCommand> trace = new Ref<NUSALSeqCommand>();
		for(NUSALSeqCommand referee : referees) {
			int type = DataCommands.guessPUsageType(referee, trace);
			if(type != NUSALSeqLinking.P_TYPE_UNK) {
				linkType = type;
				traceTarget = trace.data;
				break;
			}
		}
		
		return linkType != NUSALSeqLinking.P_TYPE_UNK;
	}

}
