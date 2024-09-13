package waffleoRai_SeqSound.n64al.cmd;

import waffleoRai_SeqSound.n64al.NUSALSeq;
import waffleoRai_SeqSound.n64al.NUSALSeqChannel;
import waffleoRai_SeqSound.n64al.NUSALSeqCmdType;
import waffleoRai_SeqSound.n64al.NUSALSeqCommand;
import waffleoRai_SeqSound.n64al.NUSALSeqCommandBook;
import waffleoRai_SeqSound.n64al.NUSALSeqLayer;

public class NUSALSeqWaitCommand extends NUSALSeqCommand{
	
	public NUSALSeqWaitCommand(NUSALSeqCmdType funcCmd, int ticks){
		super(funcCmd, null);
		NUSALSeqCmdType cmd = super.getFunctionalType();
		if(cmd == NUSALSeqCmdType.YIELD) super.reallocParamArray(1);
		super.setParam(0, ticks);
	}
	
	public NUSALSeqWaitCommand(NUSALSeqCmdType funcCmd, NUSALSeqCommandBook book, int ticks){
		super(funcCmd, book);
		NUSALSeqCmdType cmd = super.getFunctionalType();
		if(cmd == NUSALSeqCmdType.YIELD) super.reallocParamArray(1);
		super.setParam(0, ticks);
	}
	
	public NUSALSeqWaitCommand(NUSALSeqCommandDef def, int ticks){
		super(def, def.getMinCommand());
		//super(vox_lvl?NUSALSeqCmdType.REST:NUSALSeqCmdType.WAIT, 
		//		vox_lvl?NUSALSeqCmdType.REST.getBaseCommand():NUSALSeqCmdType.WAIT.getBaseCommand());
		NUSALSeqCmdType cmd = def.getFunctionalType();
		if(cmd == NUSALSeqCmdType.YIELD) super.reallocParamArray(1);
		super.setParam(0, ticks);
	}

	public int getTime(){return super.getParam(0);}

	public int getSizeInTicks(){return super.getParam(0);}
		
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
	
	public boolean toChannelDelta(NUSALSeqCommandBook cmdbook){
		if(cmdbook == null) return false;
		//super.setCommand(NUSALSeqCmdType.CH_DELTA_TIME);
		NUSALSeqCommandDef def = cmdbook.getChannelCommand(NUSALSeqCmdType.CH_DELTA_TIME);
		if(def != null) {
			super.setCommandDef(def);
		}
		else return false;
		return true;
	}

}
