package waffleoRai_soundbank.nintendo;

public interface BnkReg {
	
	public boolean hasTone();
	public NinTone getTone();
	public long getLinkAddress();
	public void setRange(byte min, byte max);
	public byte getRangeMin();
	public byte getRangeMax();

}
