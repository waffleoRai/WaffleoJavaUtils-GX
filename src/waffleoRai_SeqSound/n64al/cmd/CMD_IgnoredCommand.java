package waffleoRai_SeqSound.n64al.cmd;

import waffleoRai_SeqSound.n64al.NUSALSeq;
import waffleoRai_SeqSound.n64al.NUSALSeqChannel;
import waffleoRai_SeqSound.n64al.NUSALSeqCmdType;
import waffleoRai_SeqSound.n64al.NUSALSeqCommand;
import waffleoRai_SeqSound.n64al.NUSALSeqLayer;

public class CMD_IgnoredCommand extends NUSALSeqCommand{

	//private int size;
	
	public CMD_IgnoredCommand(NUSALSeqCmdType cmd, byte cmd_byte) {
		super(cmd, cmd_byte);
		//size = bytes;
	}
	
	public CMD_IgnoredCommand(NUSALSeqCmdType cmd) {
		super(cmd, cmd.getBaseCommand());
		//size = bytes;
	}

	//public int getSizeInBytes() {return size;}
	
	public boolean doCommand(NUSALSeq sequence){
		super.flagSeqUsed();
		return true;
	}
	
	public boolean doCommand(NUSALSeqChannel channel){
		if(channel == null) return false;
		super.flagChannelUsed(channel.getIndex());
		return true;
	}
	
	public boolean doCommand(NUSALSeqLayer voice){
		if(voice == null) return false;
		super.flagLayerUsed(voice.getChannelIndex(), voice.getLayerIndex());
		return true;
	}

}
