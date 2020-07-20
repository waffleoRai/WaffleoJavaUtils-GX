package waffleoRai_Containers.nintendo.cafe;

public interface CafeCryptListener {
	
	public void setPartitionCount(int count);
	public void onPartitionComplete(int idx);
	
	public void setFileCount(int count);
	public void onFileStart(String fname);
	public void onFileComplete(int idx);
	
	public void setPartitionSize(long size);
	public void setCurrentPosition(long cpos);

	public int getUpdateInterval();
	
}
