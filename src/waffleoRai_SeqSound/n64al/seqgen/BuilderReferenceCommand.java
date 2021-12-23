package waffleoRai_SeqSound.n64al.seqgen;

import waffleoRai_SeqSound.n64al.NUSALSeqCmdType;

class BuilderReferenceCommand extends BuilderCommand{
	
	//Relative addresses (in target format) appear to be
	//	the address of the byte BEFORE the jump rel command

	private BuilderCommand reference;
	private boolean is_relative;
	private int p_idx_addr;
	
	public BuilderReferenceCommand(NUSALSeqCmdType cmd, BuilderCommand ref) {
		super(cmd, cmd.getBaseCommand());
		reference = ref;
		detectRelative();
	}
	
	public BuilderReferenceCommand(NUSALSeqCmdType cmd, int index, BuilderCommand ref) {
		super(cmd, (byte)(cmd.getBaseCommand() + index));
		reference = ref;
		super.setParam(0, index);
		detectRelative();
	}
	
	private void detectRelative(){
		switch(super.getCommand()){
		case BRANCH_ALWAYS:
		case BRANCH_IF_EQZ:
		case BRANCH_IF_GTEZ:
		case BRANCH_IF_LTZ:
		//case BRANCH_TO_SEQSTART:
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
	
	public BuilderCommand getReference(){return reference;}
	
	public boolean isBranch(){return true;}
	public boolean isRelativeBranch(){return is_relative;}
	
	public int getBranchAddress(){
		updateParameters();
		return super.getParam(p_idx_addr);
	}
	
	public boolean setToRelative(){
		NUSALSeqCmdType cmd = super.getCommand();
		switch(cmd){
		case BRANCH_ALWAYS_REL:
		case BRANCH_IF_EQZ_REL:
		case BRANCH_IF_LTZ_REL:
		case CHANNEL_OFFSET_REL:
		case VOICE_OFFSET_REL:
			return true;
		case BRANCH_ALWAYS:
		case BRANCH_IF_EQZ:
		case BRANCH_IF_LTZ:
		case CHANNEL_OFFSET:
		case VOICE_OFFSET:
			//Is distance small enough?
			int diff = reference.getAddress() - this.getAddress() + 1; //It's relative to byte before
			if(diff > 127 || diff < -128) return false;
			super.setParam(p_idx_addr, diff);
			switch(cmd){
			case BRANCH_ALWAYS:
				super.setCommand(NUSALSeqCmdType.BRANCH_ALWAYS_REL);
				super.setCommandByte(super.getCommand().getBaseCommand());
				break;
			case BRANCH_IF_EQZ:
				super.setCommand(NUSALSeqCmdType.BRANCH_IF_EQZ_REL);
				super.setCommandByte(super.getCommand().getBaseCommand());
				break;
			case BRANCH_IF_LTZ:
				super.setCommand(NUSALSeqCmdType.BRANCH_IF_LTZ_REL);
				super.setCommandByte(super.getCommand().getBaseCommand());
				break;
			case CHANNEL_OFFSET:
				super.setCommand(NUSALSeqCmdType.CHANNEL_OFFSET_REL);
				super.setCommandByte((byte)(super.getCommand().getBaseCommand() + super.getParam(0)));
				break;
			case VOICE_OFFSET:
				super.setCommand(NUSALSeqCmdType.CHANNEL_OFFSET_REL);
				super.setCommandByte((byte)(super.getCommand().getBaseCommand() + super.getParam(0)));
				break;
			default: break;
			}
			is_relative = true;
			return true;
		case BRANCH_IF_GTEZ:
		//case BRANCH_TO_SEQSTART:
		case CALL:
			return false;
		default:
			return false;
		}
	}
	
	public boolean setToAbsolute(){
		NUSALSeqCmdType cmd = super.getCommand();
		switch(cmd){
		case BRANCH_ALWAYS_REL:
		case BRANCH_IF_EQZ_REL:
		case BRANCH_IF_LTZ_REL:
		case CHANNEL_OFFSET_REL:
		case VOICE_OFFSET_REL:
			int absaddr = this.getAddress() + super.getParam(p_idx_addr) - 1;
			super.setParam(p_idx_addr, absaddr);
			switch(cmd){
			case BRANCH_ALWAYS_REL:
				super.setCommand(NUSALSeqCmdType.BRANCH_ALWAYS);
				super.setCommandByte(getCommand().getBaseCommand());
				break;
			case BRANCH_IF_EQZ_REL:
				super.setCommand(NUSALSeqCmdType.BRANCH_IF_EQZ);
				super.setCommandByte(getCommand().getBaseCommand());
				break;
			case BRANCH_IF_LTZ_REL:
				super.setCommand(NUSALSeqCmdType.BRANCH_IF_LTZ);
				super.setCommandByte(getCommand().getBaseCommand());
				break;
			case CHANNEL_OFFSET_REL:
				super.setCommand(NUSALSeqCmdType.CHANNEL_OFFSET);
				super.setCommandByte((byte)(getCommand().getBaseCommand() + super.getParam(0)));
				break;
			case VOICE_OFFSET_REL:
				super.setCommand(NUSALSeqCmdType.CHANNEL_OFFSET);
				super.setCommandByte((byte)(getCommand().getBaseCommand() + super.getParam(0)));
				break;
			default: break;
			}
			is_relative = false;
			return true;
		case BRANCH_ALWAYS:
		case BRANCH_IF_EQZ:
		case BRANCH_IF_LTZ:
		case CHANNEL_OFFSET:
		case VOICE_OFFSET:
		case BRANCH_IF_GTEZ:
		//case BRANCH_TO_SEQSTART:
		case CALL:
			return true;
		default:
			return false;
		}
	}
	
	public void updateParameters(){
		//Updates the address parameters.
		if(is_relative){
			int rel_addr = reference.getAddress() - this.getAddress() + 1;
			super.setParam(p_idx_addr, rel_addr);
		}
		else{
			super.setParam(p_idx_addr, reference.getAddress());
		}
	}

	public int getSizeInBytes() {
		if(this.is_relative && (p_idx_addr == 0)) return 2;
		return 3;
	}

}
