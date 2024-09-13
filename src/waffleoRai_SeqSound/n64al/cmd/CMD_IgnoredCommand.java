package waffleoRai_SeqSound.n64al.cmd;

import waffleoRai_SeqSound.n64al.NUSALSeq;
import waffleoRai_SeqSound.n64al.NUSALSeqChannel;
import waffleoRai_SeqSound.n64al.NUSALSeqCmdType;
import waffleoRai_SeqSound.n64al.NUSALSeqCommand;
import waffleoRai_SeqSound.n64al.NUSALSeqCommandBook;
import waffleoRai_SeqSound.n64al.NUSALSeqLayer;

public class CMD_IgnoredCommand extends NUSALSeqCommand{

	//private int size;
	
	protected CMD_IgnoredCommand(NUSALSeqCmdType en) {
		super(en, en.getBaseCommand());
	}
	
	protected CMD_IgnoredCommand(NUSALSeqCmdType en, byte cmd_byte) {
		super(en, cmd_byte);
	}
	
	protected CMD_IgnoredCommand(NUSALSeqCmdType en, NUSALSeqCommandBook book) {
		super(en, book);
	}
	
	public CMD_IgnoredCommand(NUSALSeqCommandDef cmdDef, byte cmd_byte) {
		super(cmdDef, cmd_byte);
	}
	
	public CMD_IgnoredCommand(NUSALSeqCommandDef cmdDef) {
		super(cmdDef, cmdDef.getMinCommand());
	}

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
