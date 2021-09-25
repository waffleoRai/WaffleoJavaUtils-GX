package waffleoRai_SeqSound.n64al.seqgen;

import waffleoRai_SeqSound.n64al.NUSALSeqCmdType;
import waffleoRai_SeqSound.n64al.NUSALSeqCommand;

abstract class BuilderCommand extends NUSALSeqCommand{

	private int scratch;
	
	public BuilderCommand(NUSALSeqCmdType cmd, byte cmd_byte) {
		super(cmd, cmd_byte);
	}
	
	public void setSizeInBytes(int val){}
	public void updateParameters(){}
	//public abstract int getSizeInBytes();
	
	public void setScratchField(int value){scratch = value;}
	public int getScratchField(){return scratch;}
	
	public BuilderCommand getReference(){return null;}
	
	public void limitTicksTo(int ticks){}
	
}
