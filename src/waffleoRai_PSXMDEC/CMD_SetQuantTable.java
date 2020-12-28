package waffleoRai_PSXMDEC;

public class CMD_SetQuantTable implements MDECCommand{
	
	private boolean bColor;
	
	public CMD_SetQuantTable(boolean includeColorTbl)
	{
		bColor = includeColorTbl;
	}
	
	public void execute(PSXMDEC target) throws InterruptedException
	{
		target.setQuantTable(bColor);
	}

	public int getDataWordCount()
	{
		if(!bColor) return 16; //64 bytes = 16 words
		else return 32;// 128 bytes = 32 words
	}

}
