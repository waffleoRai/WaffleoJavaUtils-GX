package waffleoRai_SeqSound.ninseq;

public class UnimplementedCommandPlaceholder extends NSEvent{
	
	private byte badcmd;

	public UnimplementedCommandPlaceholder(byte cmd)
	{
		badcmd = cmd;
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
		return "Unimplemented Command: 0x" + String.format("%02x", badcmd);
	}

}
