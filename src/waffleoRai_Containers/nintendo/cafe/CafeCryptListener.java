package waffleoRai_Containers.nintendo.cafe;

public interface CafeCryptListener {
	
	public void setPartitionCount(int count);
	public void onPartitionComplete(int idx);
	
	//public void setFileCount(int count);
	//public void onFileStart(String fname);
	//public void onFileComplete(int idx);
	
	//public void setPartitionSize(long size);
	//public void setCurrentPosition(long cpos);
	
	public void setClusterSize(long size);
	public void setClusterPosition(long cpos);
	
	public void setClusterCount(int count);
	public void onClusterStart(int idx);
	
	public int getUpdateInterval();
	
}
