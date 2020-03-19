package waffleoRai_Containers.nintendo.sar.rev;

public class RevGroupLink {
	
	private int group;
	private int indexInGroup;
	
	private long fileOffset;
	private long fileSize;
	private long audioOffset;
	private long audioSize;
	
	public RevGroupLink(int idx_group, int idx_me)
	{
		group = idx_group;
		indexInGroup = idx_me;
	}
	
	public int getGroup(){return group;}
	public int getIndexInGroup(){return indexInGroup;}
	
	public long getFileOffset(){return fileOffset;}
	public long getFileSize(){return fileSize;}
	public long getAudioOffset(){return audioOffset;}
	public long getAudioSize(){return audioSize;}
	
	public void setFileOffset(long off){fileOffset = off;}
	public void setFileSize(long sz){fileSize = sz;}
	public void setAudioOffset(long off){audioOffset = off;}
	public void setAudioSize(long sz){audioSize = sz;}

}
