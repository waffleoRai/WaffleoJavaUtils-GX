package waffleoRai_Video.psx;

import waffleoRai_Utils.FileBuffer;

public interface XA2MDECTranslator {

	public int feedBytes(byte[] data);
	public int feedBytes(FileBuffer data);
	public boolean feedHalfword(int hw);
	public void setFrameQuantScale(int value);
	
	public int processInputBuffer();
	
	public int outputWordsAvailable();
	public int nextOutputWord();
	
	public void flushInput();
	
}
