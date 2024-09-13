package waffleoRai_Files.psx;

import waffleoRai_Utils.FileBuffer;

public interface IXAAudioDataSource {
	
	public boolean audioDataOnly(); //ie. no sector header/footer - only audio blocks such as in aifc
	
	public FileBuffer peekSector();
	public FileBuffer nextSectorBuffer();
	public byte[] nextSectorBytes();
	
	public int getSampleRate();
	public int getBitDepth();
	public int getChannelCount();
	

}
