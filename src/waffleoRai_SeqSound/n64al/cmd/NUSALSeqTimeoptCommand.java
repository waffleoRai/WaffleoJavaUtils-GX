package waffleoRai_SeqSound.n64al.cmd;

import waffleoRai_SeqSound.n64al.NUSALSeqChannel;
import waffleoRai_SeqSound.n64al.NUSALSeqCmdType;
import waffleoRai_SeqSound.n64al.NUSALSeqCommand;

public abstract class NUSALSeqTimeoptCommand extends NUSALSeqCommand{
	
	public NUSALSeqTimeoptCommand(NUSALSeqCmdType cmd, int value) {
		super(cmd, cmd.getBaseCommand());
		super.setParam(0, value);
	}
	
	public int getParamCount(){
		if(super.getParam(1) == 0) return 1;
		return 2;
	}
	
	public boolean isTimeExtendable(){return true;}
	
	public int getSizeInTicks(){
		return super.getParam(1);
	}
	
	public void setOptionalTime(int ticks){}
	
	public int getSizeInBytes(){
		if(super.getParam(1) == 0) return 2;
		return 3;
	}
	
	protected String paramsToString(){
		//Defaults to all decimal
		StringBuilder sb = new StringBuilder(64);
		sb.append(super.getParam(0));
		int param = super.getParam(1);
		if(param != 0) sb.append(" " + param);
		return sb.toString();
	};

	protected abstract void doChannelAction(NUSALSeqChannel channel);
	
	public boolean doCommand(NUSALSeqChannel channel){
		if(channel == null) return false;
		super.flagChannelUsed(channel.getIndex());
		doChannelAction(channel);
		if(super.getParam(1) != 0) channel.setWait(super.getParam(1));
		return true;
	}

}
