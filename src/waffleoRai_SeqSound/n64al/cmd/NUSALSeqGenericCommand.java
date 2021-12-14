package waffleoRai_SeqSound.n64al.cmd;

import waffleoRai_SeqSound.n64al.NUSALSeqCmdType;
import waffleoRai_SeqSound.n64al.NUSALSeqCommand;

public class NUSALSeqGenericCommand extends NUSALSeqCommand{

	private boolean str_hex;
	
	public NUSALSeqGenericCommand(NUSALSeqCmdType cmd) {
		super(cmd, cmd.getBaseCommand());
		//byte_size = cmd.getMinimumSizeInBytes();
	}
	
	public NUSALSeqGenericCommand(NUSALSeqCmdType cmd, int idx) {
		super(cmd, (byte)(cmd.getBaseCommand() + idx));
		super.setParam(0, idx);
		//byte_size = cmd.getMinimumSizeInBytes();
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
			if(super.getCommand().getMinimumSizeInBytes() > 2) sb.append(String.format("%04x", super.getParam(i)));
			else sb.append(String.format("%02x", super.getParam(i)));
		}
		return sb.toString();
	}
	
}
