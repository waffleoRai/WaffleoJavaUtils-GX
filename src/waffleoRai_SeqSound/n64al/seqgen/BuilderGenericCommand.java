package waffleoRai_SeqSound.n64al.seqgen;

import waffleoRai_SeqSound.n64al.NUSALSeqCmdType;

class BuilderGenericCommand extends BuilderCommand{

	private int byte_size;
	
	public BuilderGenericCommand(NUSALSeqCmdType cmd, int size) {
		super(cmd, cmd.getBaseCommand());
		byte_size = size;
	}

	public int getSizeInBytes() {
		return byte_size;
	}

}
