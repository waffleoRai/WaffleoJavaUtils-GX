package waffleoRai_PSXMDEC;

public interface IMDECIO {
	
	public void linkMDEC(PSXMDEC mdec);
	
	public void startDisassembler();
	public void stopDisassembler();
	public void interruptDisassembler();
	
	//public void feedSingleWord(int word);
	public void setCurrentInputBlock(int n);
	
	public void setDataOutputDepth(int bdEnum);
	public void setDataOutputSigned(boolean b);
	public void setDataOutputSet15(boolean b);
	
	public HalfwordStream getInputStream(int inputBlock);
	public void queueWordForOutput(int word);
	
	public void signalMacroblockOutputQueueStart(int bdEnum, boolean isSigned, boolean set15);
	public void signalMacroblockOutputQueueEnd();
	
	public static MDECCommand disassembleMDECCommand(int word)
	{
		int cmd = (word >>> 29) & 0x7; //First three bits are the code
		switch(cmd)
		{
		case 1:
			//Get the parameters...
			int bdenum = (word >>> 27) & 0x3;
			boolean signed = (word & 0x04000000) != 0;
			boolean set15 = (word & 0x02000000) != 0;
			int wordCount = (word & 0xFFFF);
			/*if(bdenum > 1) {
				inputMonochrome = false;
				currentBlockIN = 0;
			}
			else {
				inputMonochrome = true;
				currentBlockIN = 4;
			}
			updateCurrentBlock();*/
			//bitDepthEnum = bdenum; //To make sure block is accurate
			return new CMD_DecodeMacroblock(bdenum, signed, set15, wordCount);
		case 2:
			boolean setColor = (word & 0x1) != 0;
			//currentBlockIN = -1;
			return new CMD_SetQuantTable(setColor);
		case 3:
			//currentBlockIN = -1;
			return new CMD_SetScaleTable();
		default:
			return null;//EAT
		}
	}

}
