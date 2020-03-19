package waffleoRai_Containers.nintendo.sar.rev;

import java.io.BufferedWriter;
import java.io.IOException;

public class SeqNode extends SoundDataNode{

	private int seqLabelEntry;
	private int soundbankIndex;
	private int rseqAllocTrack;
	private int seqChannelPriority;
	
	public int getSeqLabelEntry(){return seqLabelEntry;}
	public void setSeqLableEntry(int i){this.seqLabelEntry = i;}
	public int getSoundbankIndex(){return soundbankIndex;}
	public void setSoundbankIndex(int i){this.soundbankIndex = i;}
	public int getAllocTrack(){return rseqAllocTrack;}
	public void setAllocTrack(int i){this.rseqAllocTrack = i;}
	public int getChannelPriority(){return seqChannelPriority;}
	public void setChannelPriority(int i){this.seqChannelPriority = i;}
	
	protected void writeMyParams(BufferedWriter bw) throws IOException
	{
		//Super class writes nothing
		bw.write(seqLabelEntry + "," + soundbankIndex);
	}
}
