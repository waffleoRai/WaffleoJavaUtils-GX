package waffleoRai_SeqSound.ninseq;

public class NinSeqLabel {
	
	private String name;
	private long address;
	
	private int bankid;
	private byte vol;
	private byte cpr;
	private byte ppr;
	private byte player;
	
	public NinSeqLabel(String symb, long adr){
		name = symb;
		address = adr;
	}
	
	public String getName(){return name;}
	public long getAddress(){return address;}
	public int getBankID(){return bankid;}
	public byte getVolume(){return vol;}
	public byte getChannelPressure(){return cpr;}
	public byte getPolyPressure(){return ppr;}
	public byte getPlayer(){return player;}
	
	public void setName(String s){name = s;}
	public void setAddress(long adr){address = adr;}
	public void setBankID(int id){bankid = id;}
	public void setVolume(byte val){vol = val;}
	public void setChannelPressure(byte val){cpr = val;}
	public void setPolyPressure(byte val){ppr = val;}
	public void setPlayer(byte val){player = val;}

}
