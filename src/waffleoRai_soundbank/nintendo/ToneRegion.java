package waffleoRai_soundbank.nintendo;

public class ToneRegion implements BnkReg{

	private NinTone tone;
	
	public ToneRegion(NinTone t)
	{
		tone = t;
	}
	
	@Override
	public boolean hasTone() 
	{
		return true;
	}

	@Override
	public NinTone getTone() 
	{
		return tone;
	}

	@Override
	public long getLinkAddress() 
	{
		return -1;
	}
	
	public void setRange(byte min, byte max)
	{
		tone.setNoteRange(min, max);
	}
	
	public byte getRangeMin(){return tone.getMinNote();}
	public byte getRangeMax(){return tone.getMaxNote();}

}
