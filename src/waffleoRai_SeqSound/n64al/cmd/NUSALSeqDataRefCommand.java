
package waffleoRai_SeqSound.n64al.cmd;

import waffleoRai_SeqSound.n64al.NUSALSeqCmdType;
import waffleoRai_SeqSound.n64al.NUSALSeqCommand;
import waffleoRai_SeqSound.n64al.NUSALSeqCommandBook;

public abstract class NUSALSeqDataRefCommand extends NUSALSeqReferenceCommand{

	private String lbl_prefix;
	private int data_size;
	private int offset; //Offset of reference from start of linked command (if overlap)
	
	protected NUSALSeqDataRefCommand(NUSALSeqCmdType funcCmd, NUSALSeqCommandBook book, int addr, int exp_datsz) {
		super(funcCmd, book, addr, false);
		data_size = exp_datsz;
	}
	
	protected NUSALSeqDataRefCommand(NUSALSeqCmdType funcCmd, NUSALSeqCommandBook book, int value, int addr, int exp_datsz) {
		super(funcCmd, book, value, addr, false);
		data_size = exp_datsz;
	}
	
	protected NUSALSeqDataRefCommand(NUSALSeqCommandDef def, int addr, int exp_datsz) {
		super(def, addr, false);
		data_size = exp_datsz;
	}
	
	protected NUSALSeqDataRefCommand(NUSALSeqCommandDef def, int value, int addr, int exp_datsz) {
		super(def, value, addr, false);
		data_size = exp_datsz;
	}
	
	protected void setLabelPrefix(String s){lbl_prefix = s;}
	
	public int getBranchAddress(){
		return super.getBranchAddress() + offset;
	}

	public boolean isBranch(){return false;}
	public NUSALSeqCmdType getRelativeCommand() {return null;}
	public NUSALSeqCmdType getAbsoluteCommand() {return super.getFunctionalType();}
	
	public int getDataOffset(){return offset;}
	public void setDataOffset(int value){offset = value;}
	
	public String getLabelPrefix(){return lbl_prefix;}
	public int getExpectedDataSize(){return data_size;}	
	
	public void removeReference(NUSALSeqCommand target) {
		if(target == super.getBranchTarget()) {
			offset = 0;
		}
		super.removeReference(target);
	}
	
	public void updateAddressParameter(){
		NUSALSeqCommand ref = super.getBranchTarget();
		if(ref != null){
			int newaddr = ref.getAddress() + offset;
			super.setParam(p_idx_addr, newaddr);
		}
	}
	
	public String[][] getParamStrings(){
		String[][] pstr = super.getParamStrings();
		if(offset != 0){
			if(pstr[p_idx_addr][0] != null){
				pstr[p_idx_addr][0] += "[" + offset + "]";
			}
		}
		return pstr;
	}
	
	protected StringBuilder toMMLCommand_child(int syntax){
		StringBuilder sb = super.toMMLCommand_child(syntax);
		if(offset != 0){
			sb.append('[');
			sb.append(offset);
			sb.append(']');
		}
		return sb;
	}
	
}
