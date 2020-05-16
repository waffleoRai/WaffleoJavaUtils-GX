package waffleoRai_SeqSound.misc.smd;

public interface SMDEvent {
	
	public int getByteLength();
	public SMDMiscType getType();
	public int getChannel();
	public void setChannel(int c);
	
	public long getWait();
	public void execute(SMDTrack t) throws InterruptedException;
	
	public byte[] serializeMe();

}
