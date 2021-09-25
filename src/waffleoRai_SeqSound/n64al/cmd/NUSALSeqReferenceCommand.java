package waffleoRai_SeqSound.n64al.cmd;

import waffleoRai_SeqSound.n64al.NUSALSeqCmdType;
import waffleoRai_SeqSound.n64al.NUSALSeqCommand;

public class NUSALSeqReferenceCommand extends NUSALSeqCommand{
	
	private NUSALSeqCommand reference;
	private boolean is_relative;
	private int p_idx_addr;

	public NUSALSeqReferenceCommand(NUSALSeqCmdType cmd, int value) {
		super(cmd, cmd.getBaseCommand());
		reference = null;
		super.setParam(0, value);
		detectRelative();
	}
	
	public NUSALSeqReferenceCommand(NUSALSeqCmdType cmd, int idx, int value) {
		super(cmd, cmd.getBaseCommand());
		reference = null;
		super.setParam(0, idx);
		super.setParam(1, value);
		detectRelative();
	}
	
	private void detectRelative(){
		switch(super.getCommand()){
		case BRANCH_ALWAYS:
		case BRANCH_IF_EQZ:
		case BRANCH_IF_GTEZ:
		case BRANCH_IF_LTZ:
		case BRANCH_TO_SEQSTART:
		case CALL:
			p_idx_addr = 0;
			is_relative = false;
			break;
		case CHANNEL_OFFSET:
		case VOICE_OFFSET:
			p_idx_addr = 1;
			is_relative = false;
			break;
		case BRANCH_ALWAYS_REL:
		case BRANCH_IF_EQZ_REL:
		case BRANCH_IF_LTZ_REL:
			p_idx_addr = 0;
			is_relative = true;
			break;
		case CHANNEL_OFFSET_REL:
		case VOICE_OFFSET_REL:
			p_idx_addr = 1;
			is_relative = true;
			break;
		default:
			break;
		}
	}

	public void setReference(NUSALSeqCommand target){
		reference = target;
		//Update param 1
		updateAddressParameter();
	}
	
	public boolean isBranch(){return true;}
	public boolean isRelativeBranch(){return is_relative;}
	public NUSALSeqCommand getBranchTarget(){return reference;}
	
	//I'm switching this to be absolute
	public int getBranchAddress(){
		if(is_relative){
			int taddr = super.getParam(p_idx_addr);
			int maddr = getAddress() - 1;
			return taddr + maddr;
		}
		return super.getParam(p_idx_addr);
	}
	
	public void updateAddressParameter(){
		if(reference != null){
			if(is_relative){
				//The parameter offset is always relative to the byte BEFORE the command for some reason
				int taddr = reference.getAddress();
				int maddr = getAddress() - 1;
				super.setParam(p_idx_addr, taddr - maddr);
			}
			else{
				super.setParam(p_idx_addr, reference.getAddress());
			}
		}
	}
	
	public int getSizeInBytes() {
		if(is_relative) return 2;
		return 3;
	}
	
	protected String paramsToString(){
		if(is_relative){
			return super.getParam(p_idx_addr) + " [-> " + String.format("0x%04x", getBranchAddress()) + "]";
		}
		else{
			return String.format("0x%04x", getBranchAddress());
		}
	}
	
}
