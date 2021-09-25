package waffleoRai_SeqSound.n64al.cmd;

import waffleoRai_SeqSound.n64al.NUSALSeq;
import waffleoRai_SeqSound.n64al.NUSALSeqChannel;
import waffleoRai_SeqSound.n64al.NUSALSeqCmdType;
import waffleoRai_SeqSound.n64al.NUSALSeqCommand;
import waffleoRai_SeqSound.n64al.NUSALSeqLayer;

public class NUSALSeqWaitCommand extends NUSALSeqCommand{
	
	public NUSALSeqWaitCommand(int ticks, boolean vox_lvl){
		super(vox_lvl?NUSALSeqCmdType.REST:NUSALSeqCmdType.WAIT, 
				vox_lvl?NUSALSeqCmdType.REST.getBaseCommand():NUSALSeqCmdType.WAIT.getBaseCommand());
		super.setParam(0, ticks);
	}

	public int getTime(){return super.getParam(0);}

	public int getSizeInTicks(){return super.getParam(0);}
	
	public int getSizeInBytes() {
		if(super.getParam(0) > 127) return 3;
		return 2;
	}
	
	public boolean doCommand(NUSALSeq sequence){
		if(sequence == null) return false;
		sequence.setSeqWait(super.getParam(0));
		super.flagSeqUsed();
		return true;
	}
	
	public boolean doCommand(NUSALSeqChannel channel){
		if(channel == null) return false;
		channel.setWait(super.getParam(0));
		super.flagChannelUsed(channel.getIndex());
		return true;
	}
	
	public boolean doCommand(NUSALSeqLayer voice){
		if(voice == null) return false;
		voice.setWait(super.getParam(0));
		super.flagLayerUsed(voice.getChannelIndex(), voice.getLayerIndex());
		return true;
	}

}
