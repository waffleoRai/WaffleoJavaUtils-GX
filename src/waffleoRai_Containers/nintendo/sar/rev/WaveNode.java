package waffleoRai_Containers.nintendo.sar.rev;

import java.io.BufferedWriter;
import java.io.IOException;

public class WaveNode extends SoundDataNode{
	
	private int soundDataNode;
	private int allocTrack;
	private int channelPriority;
	
	public int getSDN(){return this.soundDataNode;}
	public void setSDN(int i){this.soundDataNode = i;}
	public int getAllocTrack(){return allocTrack;}
	public void setAllocTrack(int i){allocTrack = i;}
	public int getChannelPriority(){return channelPriority;}
	public void setChannelPriority(int i){channelPriority = i;}
	
	protected void writeMyParams(BufferedWriter bw) throws IOException
	{
		//Super class writes nothing
		bw.write(soundDataNode + ",0");
	}

}
