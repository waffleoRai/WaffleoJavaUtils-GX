package waffleoRai_SeqSound.ninseq;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;

public abstract class NSEvent {

	private long address;
	
	private NSCommand cmd;
	private int subCommand;
	private int param1;
	private int param2;
	
	private int serialSize = 1;
	
	public NSCommand getCommand(){return cmd;}
	public int getSubCommand(){return subCommand;}
	public int getParam1(){return param1;}
	public int getParam2(){return param2;}
	public int getSerialSize(){return serialSize;}
	public long getAddress(){return address;}
	
	public void setCommand(NSCommand command){cmd = command;}
	public void setSubCommand(int sub){subCommand = sub;}
	public void setParam1(int p){param1 = p;}
	public void setParam2(int p){param2 = p;}
	protected void setSerialSize(int sz){serialSize = sz;}
	public void setAddress(long addr){address = addr;}
	
	public abstract void execute(NinTrack track);
	
	public abstract boolean hasFirstParameter();
	public abstract boolean hasSecondParameter();
	
	public abstract byte[] serializeEvent(boolean endian);
	
	public static MetaMessage generateMidiMetaFlag(NSEvent e) throws InvalidMidiDataException
	{
		String estring = e.toString();
		return new MetaMessage(0x01, estring.getBytes(), estring.length());
	}
	
	
}
