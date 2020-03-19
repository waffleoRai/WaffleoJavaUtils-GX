package waffleoRai_SeqSound.ninseq;

public class LabelSeq implements Comparable<LabelSeq>{
	
	private long raw_offset; //Relative to end of DATA header
	private String label;
	
	//private NinSeq data;
	
	public LabelSeq(String name, long off)
	{
		raw_offset = off;
		label = name;
	}
	
	public long getRawOffset(){return raw_offset;}
	public String getName(){return label;}
	//public NinSeq getData(){return data;}
	
	@Override
	public int compareTo(LabelSeq o) 
	{
		if(o == null) return 1;
		return (int)(this.raw_offset - o.raw_offset);
	}
	
	public boolean equals(Object o)
	{
		if(o == null) return false;
		return this == o;
	}
	
	public int hashCode()
	{
		return label.hashCode();
	}

}
