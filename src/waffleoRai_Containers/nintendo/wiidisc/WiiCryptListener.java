package waffleoRai_Containers.nintendo.wiidisc;

public interface WiiCryptListener {

	public void setSectorCount(int count);
	public void setPartitionCount(int count);
	public void onSectorDecrypted(int sectorIndex);
	public void onSectorWrittenToBuffer(int sectorIndex);
	public int getUpdateFrequencyMillis();
	public void onDecryptionComplete(boolean isgood);
	
}
