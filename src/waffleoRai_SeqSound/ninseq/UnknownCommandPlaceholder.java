package waffleoRai_SeqSound.ninseq;

public class UnknownCommandPlaceholder extends NSEvent{
	
	private byte badcmd;
	private Exception ex;

	public UnknownCommandPlaceholder(byte cmd)
	{
		badcmd = cmd;
	}
	
	public UnknownCommandPlaceholder(byte cmd, Exception exception)
	{
		badcmd = cmd;
		ex = exception;
	}
	
	@Override
	public boolean hasFirstParameter() {return false;}

	@Override
	public boolean hasSecondParameter() {return false;}

	public void execute(NinTrack track){return;} //Does nothing
	
	@Override
	public byte[] serializeEvent(boolean endian) 
	{
		byte[] event = {badcmd};
		return event;
	}
	
	public String toString()
	{
		return "Unknown Command: 0x" + String.format("%02x", badcmd);
	}
	
	public Exception getException()
	{
		return ex;
	}

}
