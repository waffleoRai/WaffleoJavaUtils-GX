package waffleoRai_Containers.nintendo.sar.rev;

import waffleoRai_Utils.FileTag;

public class RevGroupMember {
	
	private RevColNode collection;
	
	private FileTag fileLocation;
	private FileTag audioLocation;
	
	public RevGroupMember(RevColNode col)
	{
		collection = col;
	}
	
	public RevGroupMember(){}
	
	public RevColNode getCollectionLink()
	{
		return collection;
	}
	
	public FileTag getFileLocation()
	{
		return fileLocation;
	}
	
	public FileTag getAudioLocation()
	{
		return audioLocation;
	}
	
	public void setCollectionLink(RevColNode col)
	{
		collection = col;
	}
	
	public void setFileLocation(String path, long off, long sz)
	{
		fileLocation = new FileTag(path, off, sz);
	}
	
	public void setAudioLocation(String path, long off, long sz)
	{
		audioLocation = new FileTag(path, off, sz);
	}

	public void setFileLocation(FileTag loc)
	{
		fileLocation = loc;
	}
	
	public void setAudioLocation(FileTag loc)
	{
		audioLocation = loc;
	}
	
	public boolean hasAudio()
	{
		if(audioLocation == null) return false;
		return audioLocation.getSize() > 0;
	}

	
}
