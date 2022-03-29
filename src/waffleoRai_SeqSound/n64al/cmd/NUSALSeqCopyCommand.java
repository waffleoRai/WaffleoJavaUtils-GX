package waffleoRai_SeqSound.n64al.cmd;

import waffleoRai_SeqSound.n64al.NUSALSeq;
import waffleoRai_SeqSound.n64al.NUSALSeqChannel;
import waffleoRai_SeqSound.n64al.NUSALSeqCommand;
import waffleoRai_SeqSound.n64al.NUSALSeqLayer;

public class NUSALSeqCopyCommand extends NUSALSeqCommand{

	private NUSALSeqCommand trg;
	
	public NUSALSeqCopyCommand(NUSALSeqCommand cmd) {
		super(cmd.getCommand(), cmd.getCommandByte());
		trg = cmd;
	}
	
	public int getParamCount(){return trg.getParamCount();}
	public int getParam(int idx){return trg.getParam(idx);}
	public void setParam(int idx, int val){trg.setParam(idx, val);}
	public void setParam(int idx, byte val, boolean signExtend){trg.setParam(idx, val, signExtend);}
	public void setParam(int idx, short val, boolean signExtend){trg.setParam(idx, val, signExtend);}
	
	public boolean doCommand(NUSALSeq sequence){return trg.doCommand(sequence);}
	public boolean doCommand(NUSALSeqChannel channel){return trg.doCommand(channel);}
	public boolean doCommand(NUSALSeqLayer voice){return trg.doCommand(voice);}
	
	public String toString(){return trg.toString();}
	public String toMMLCommand(){return trg.toMMLCommand();}
	public byte[] serializeMe(){return trg.serializeMe();}

}
