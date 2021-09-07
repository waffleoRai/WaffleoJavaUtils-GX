package waffleoRai_SeqSound.n64al.seqgen;

import waffleoRai_SeqSound.n64al.NUSALSeqCmdType;
import waffleoRai_SeqSound.n64al.NUSALSeqCommand;

abstract class BuilderCommand extends NUSALSeqCommand{

	private int address;
	private int scratch;
	
	public BuilderCommand(NUSALSeqCmdType cmd, byte cmd_byte) {
		super(cmd, cmd_byte);
	}
	
	public void setAddress(int addr){address = addr;}
	public int getAddress(){return address;}
	
	public boolean isChunk(){return false;}
	
	public void setScratchField(int value){scratch = value;}
	public int getScratchField(){return scratch;}
	
}
