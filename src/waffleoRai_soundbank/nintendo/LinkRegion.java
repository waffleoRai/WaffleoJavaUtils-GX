package waffleoRai_soundbank.nintendo;

public class LinkRegion implements BnkReg{

	private byte rmin;
	private byte rmax;
	private long link;
	
	public LinkRegion(long addr)
	{
		link = addr;
		rmin = 0x00;
		rmax = 0x7F;
	}
	
	@Override
	public boolean hasTone() {
		return false;
	}

	@Override
	public NinTone getTone() {
		return null;
	}

	@Override
	public long getLinkAddress() {
		return link;
	}
	
	public void setRange(byte min, byte max)
	{
		rmin = min;
		rmax = max;
	}
	
	public byte getRangeMin(){return rmin;}
	public byte getRangeMax(){return rmax;}

}
