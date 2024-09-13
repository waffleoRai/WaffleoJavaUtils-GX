package waffleoRai_Sound.psx;

import waffleoRai_Files.psx.IXAAudioDataSource;
import waffleoRai_Utils.FileBuffer;

public class XAAudioDataOnlyStream implements IXAAudioDataSource{
	
	private static final int BLOCK_SIZE = 128;
	private static final int BLOCKS_PER_SEC = 18;
	private static final int BYTES_PER_SEC = BLOCK_SIZE * BLOCKS_PER_SEC;
	
	private int sampleRate;
	private int bitDepth; //Native, not output
	private int channelCount;
	
	private int cpos = 0;
	private byte[] audioData;
	
	public XAAudioDataOnlyStream() {}

	public boolean audioDataOnly() {return true;}
	public int getSampleRate() {return sampleRate;}
	public int getBitDepth() {return bitDepth;}
	public int getChannelCount() {return channelCount;}
	
	public void setAudioData(byte[] data) {audioData = data;}
	public void setSampleRate(int val) {sampleRate = val;}
	public void setBitDepth(int val) {bitDepth = val;}
	public void setChannelCount(int val) {channelCount = val;}

	public FileBuffer peekSector() {
		if(cpos >= audioData.length) return null;
		
		FileBuffer buff = new FileBuffer(BYTES_PER_SEC, false);
		for(int i = 0; i < BYTES_PER_SEC; i++) {
			buff.addToFile(audioData[cpos + i]);
		}
		
		return buff;
	}

	public FileBuffer nextSectorBuffer() {
		if(cpos >= audioData.length) return null;
		
		FileBuffer buff = new FileBuffer(BYTES_PER_SEC, false);
		for(int i = 0; i < BYTES_PER_SEC; i++) {
			buff.addToFile(audioData[cpos++]);
		}
		
		return buff;
	}

	public byte[] nextSectorBytes() {
		byte[] bb = new byte[BYTES_PER_SEC];
		for(int i = 0; i < BYTES_PER_SEC; i++) {
			bb[i] = audioData[cpos++];
		}
		
		return bb;
	}

	

}
