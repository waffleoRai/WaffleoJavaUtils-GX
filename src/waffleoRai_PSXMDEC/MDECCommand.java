package waffleoRai_PSXMDEC;

public interface MDECCommand {
	
	public void execute(PSXMDEC target) throws InterruptedException;
	public int getDataWordCount();
}
