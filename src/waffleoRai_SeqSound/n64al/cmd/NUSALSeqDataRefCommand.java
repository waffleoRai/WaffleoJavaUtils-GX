
package waffleoRai_SeqSound.n64al.cmd;

import waffleoRai_SeqSound.n64al.NUSALSeqCmdType;

public abstract class NUSALSeqDataRefCommand extends NUSALSeqReferenceCommand{

	private String lbl_prefix;
	private int data_size;
	
	protected NUSALSeqDataRefCommand(NUSALSeqCmdType cmd, int addr, int exp_datsz) {
		super(cmd, addr, false);
		data_size = exp_datsz;
	}
	
	protected NUSALSeqDataRefCommand(NUSALSeqCmdType cmd, int value, int addr, int exp_datsz) {
		super(cmd, value, addr, false);
		data_size = exp_datsz;
	}
	
	protected void setLabelPrefix(String s){lbl_prefix = s;}

	public boolean isBranch(){return false;}
	public NUSALSeqCmdType getRelativeCommand() {return null;}
	public NUSALSeqCmdType getAbsoluteCommand() {return super.getCommand();}
	
	public String getLabelPrefix(){return lbl_prefix;}
	public int getExpectedDataSize(){return data_size;}
	
}
