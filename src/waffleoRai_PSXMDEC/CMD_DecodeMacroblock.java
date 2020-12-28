package waffleoRai_PSXMDEC;

public class CMD_DecodeMacroblock implements MDECCommand 
{
	private int eBitDepth;
	private boolean bIsSigned;
	private boolean bSet15;
	private int iWordCount;
	
	public CMD_DecodeMacroblock(int bDepthEnum, boolean signed, boolean set15, int words)
	{
		eBitDepth = bDepthEnum;
		bIsSigned = signed;
		bSet15 = set15;
		iWordCount = words;
	}
	
	public void execute(PSXMDEC target) throws InterruptedException
	{
		target.decodeMacroblocks(eBitDepth, bIsSigned, bSet15, iWordCount);
	}

	public int getDataWordCount()
	{
		return iWordCount;
	}
	
}
