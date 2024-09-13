package waffleoRai_SeqSound.n64al.cmd;

import waffleoRai_SeqSound.n64al.NUSALSeqCmdType;
import waffleoRai_SeqSound.n64al.NUSALSeqCommand;
import waffleoRai_SeqSound.n64al.NUSALSeqCommandBook;

public class NUSALSeqGenericCommand extends NUSALSeqCommand{

	private boolean str_hex;
	
	public NUSALSeqGenericCommand(NUSALSeqCommandDef def) {
		super(def, def.getMinCommand());
		//byte_size = cmd.getMinimumSizeInBytes();
	}
	
	public NUSALSeqGenericCommand(NUSALSeqCommandDef def, int idx) {
		super(def, (byte)(def.getMinCommand() + idx));
		super.setParam(0, idx);
		//byte_size = cmd.getMinimumSizeInBytes();
	}
	
	public NUSALSeqGenericCommand(NUSALSeqCmdType funcCmd) {
		super(funcCmd, null);
	}
	
	public NUSALSeqGenericCommand(NUSALSeqCmdType funcCmd, NUSALSeqCommandBook book) {
		super(funcCmd, book);
	}
	
	public void setDisplayStringHex(boolean b){str_hex = b;}
	
	protected String paramsToString(){
		if(!str_hex) return super.paramsToString();
		int pcount = super.getParamCount();
		if(pcount < 1) return null;
		StringBuilder sb = new StringBuilder(512);
		for(int i = 0; i < pcount; i++){
			if(i != 0) sb.append(" ");
			sb.append("0x");
			NUSALSeqCommandDef def = getCommandDef();
			if(def != null) {
				if(def.getMinSize() > 2) {
					sb.append(String.format("%04x", super.getParam(i)));
				}
				else sb.append(String.format("%02x", super.getParam(i)));
			}
			else sb.append(String.format("%02x", super.getParam(i)));
		}
		return sb.toString();
	}
	
}
