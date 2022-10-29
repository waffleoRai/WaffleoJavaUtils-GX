package waffleoRai_SeqSound.n64al.cmd;

import waffleoRai_SeqSound.n64al.NUSALSeqCmdType;
import waffleoRai_SeqSound.n64al.NUSALSeqCommand;

public abstract class NUSALSeqReferenceCommand extends NUSALSeqCommand{
	
	private NUSALSeqCommand reference;
	private boolean is_relative;
	protected int p_idx_addr;

	protected NUSALSeqReferenceCommand(NUSALSeqCmdType cmd, int value, boolean rel) {
		super(cmd, cmd.getBaseCommand());
		reference = null;
		super.setParam(0, value);
		p_idx_addr = 0;
		is_relative = rel;
	}
	
	protected NUSALSeqReferenceCommand(NUSALSeqCmdType cmd, int idx, int value, boolean rel) {
		super(cmd, cmd.getBaseCommand());
		reference = null;
		super.setParam(0, idx);
		super.setParam(1, value);
		p_idx_addr = 1;
		is_relative = rel;
	}
	
	public void setReference(NUSALSeqCommand target){
		if(reference != null){
			reference.removeReferee(this);
		}
		reference = target;
		//Update param 1
		updateAddressParameter();
		if(target != null) target.addReferee(this);
	}
	
	public abstract NUSALSeqCmdType getRelativeCommand();
	public abstract NUSALSeqCmdType getAbsoluteCommand();
	
	protected void setAddressParamIdx(int idx){
		p_idx_addr = idx;
	}
	
	public boolean isBranch(){return true;}
	public boolean isRelativeBranch(){return is_relative;}
	public NUSALSeqCommand getBranchTarget(){return reference;}
	
	public STSResult storeToSelf(int offset, byte value){
		int prev_addr = super.getParam(p_idx_addr);
		STSResult res = super.storeToSelf(offset, value);
		if(res == STSResult.OKAY){
			int now_addr = super.getParam(p_idx_addr);
			if(prev_addr != now_addr) return STSResult.RELINK;
		}
		return res;
	}
	
	//I'm switching this to be absolute
	public int getBranchAddress(){
		if(is_relative){
			int taddr = super.getParam(p_idx_addr);
			//int maddr = getAddress() - 1;
			int maddr = getAddress() + getSizeInBytes();
			if(p_idx_addr == 1){
				//
				maddr = getAddress() + 3;
			}
			return taddr + maddr;
		}
		return super.getParam(p_idx_addr);
	}
	
	public void updateAddressParameter(){
		if(reference != null){
			if(is_relative){
				//The parameter offset is always relative to the byte BEFORE the command for some reason
				int taddr = reference.getAddress();
				int maddr = getAddress() + 2;
				if(p_idx_addr == 1){
					//VOICE REL or CHANNEL REL
					maddr = getAddress() + 3;
				}
				super.setParam(p_idx_addr, taddr - maddr);
			}
			else{
				super.setParam(p_idx_addr, reference.getAddress());
			}
		}
	}

	public byte[] serializeMe(){
		updateAddressParameter();
		return super.serializeMe();
	}
	
	protected String paramsToString(){
		if(is_relative){
			return super.getParam(p_idx_addr) + " [-> " + String.format("0x%04x", getBranchAddress()) + "]";
		}
		else{
			return String.format("0x%04x", getBranchAddress());
		}
	}
	
	protected StringBuilder toMMLCommand_child(){
		StringBuilder sb = new StringBuilder(256);
		sb.append(super.getCommand().toString());
		sb.append(' ');
		if(p_idx_addr > 0){
			//Param 0
			sb.append(getParam(0));
			sb.append(' ');
		}
		if(reference != null){
			if(reference.getLabel() != null){
				sb.append(reference.getLabel());
			}
			else sb.append(paramsToString());
		}
		else sb.append(paramsToString());
		return sb;
	}
		
}
