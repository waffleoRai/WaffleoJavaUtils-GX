package waffleoRai_Containers.nintendo.sar.rev;

import java.io.BufferedWriter;
import java.io.IOException;

public class StreamNode extends SoundDataNode{
	
	private int startPosition;
	private int allocChannelCount;
	private int allocTrack;
	
	public int getStartPosition(){return startPosition;}
	public void setStartPosition(int i){startPosition = i;}
	public int getAllocChannelCount(){return allocChannelCount;}
	public void setAllocChannelCount(int i){allocChannelCount = i;}
	public int getAllocTrack(){return allocTrack;}
	public void setAllocTrack(int i){allocTrack = i;}

	protected void writeMyParams(BufferedWriter bw) throws IOException
	{
		//Super class writes nothing
		bw.write("0x" + Integer.toHexString(startPosition) + ",0");
	}
	
}
