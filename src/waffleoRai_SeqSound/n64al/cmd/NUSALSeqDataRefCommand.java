
package waffleoRai_SeqSound.n64al.cmd;

import waffleoRai_SeqSound.n64al.NUSALSeqCmdType;

public abstract class NUSALSeqDataRefCommand extends NUSALSeqReferenceCommand{

	private String lbl_prefix;
	private int data_size;
	private int offset; //Offset of reference from start of linked command (if overlap)
	
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
	
	public int getDataOffset(){return offset;}
	public void setDataOffset(int value){offset = value;}
	
	public String getLabelPrefix(){return lbl_prefix;}
	public int getExpectedDataSize(){return data_size;}
	
	protected StringBuilder toMMLCommand_child(){
		StringBuilder sb = super.toMMLCommand_child();
		if(offset != 0){
			sb.append('[');
			sb.append(offset);
			sb.append(']');
		}
		return sb;
	}
	
}
