package waffleoRai_Containers.nintendo;

public class NDKHeaderEntry {
	
	private NDKSectionType type;
	private long offset;
	private long length;
	private String magic;
	
	private short typeID;
	
	public NDKHeaderEntry()
	{
		type = null;
		offset = -1;
		length = 0;
		magic = null;
		typeID = -1;
	}
	
	public NDKSectionType getType(){return type;}
	public long getOffset(){return offset;}
	public long getLength(){return length;}
	public String getIdentifier(){return magic;}
	public short getTypeID(){return typeID;}
	
	public void setType(NDKSectionType t){type = t;}
	public void setOffset(long off){offset = off;}
	public void setLength(long size){length = size;}
	public void setIdentifier(String s){magic = s;}
	public void setTypeID(short id){typeID = id;}

}
