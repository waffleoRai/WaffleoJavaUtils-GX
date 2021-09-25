package waffleoRai_SeqSound.n64al.seqgen;

import waffleoRai_SeqSound.n64al.NUSALSeqCmdType;

class BuilderTimeoptCommand extends BuilderCommand{

	public BuilderTimeoptCommand(NUSALSeqCmdType cmd, int value) {
		super(cmd, cmd.getBaseCommand());
		super.setParam(0, value);
	}
	
	public int getSizeInTicks(){return super.getParam(1);}
	
	public void limitTicksTo(int ticks){
		super.setParam(1, Math.min(ticks, super.getParam(1)));
	}
	
	public boolean isTimeExtendable(){return true;}
	public void setOptionalTime(int ticks){super.setParam(1, ticks);}

	public int getSizeInBytes() {
		if(super.getParam(1) == 0) return 2;
		return 3;
	}

}
