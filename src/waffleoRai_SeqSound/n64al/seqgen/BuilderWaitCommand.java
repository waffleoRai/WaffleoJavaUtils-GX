package waffleoRai_SeqSound.n64al.seqgen;

import waffleoRai_SeqSound.n64al.NUSALSeqCmdType;

class BuilderWaitCommand extends BuilderCommand{
	
	public BuilderWaitCommand(int ticks, boolean vox_lvl){
		super(vox_lvl?NUSALSeqCmdType.REST:NUSALSeqCmdType.WAIT, 
				vox_lvl?NUSALSeqCmdType.REST.getBaseCommand():NUSALSeqCmdType.WAIT.getBaseCommand());
		super.setParam(0, ticks);
	}

	public int getTime(){return super.getParam(0);}
	
	public int getSizeInBytes() {
		if(getTime() < 128) return 2;
		return 3;
	}

}
