package waffleoRai_PSXMDEC;

public class CMD_SetScaleTable implements MDECCommand{
	
	public void execute(PSXMDEC target) throws InterruptedException
	{
		target.setScaleTable();
	}

	public int getDataWordCount()
	{
		return 32; //64 halfwords = 32 words
	}

}
