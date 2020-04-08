package waffleoRai_SeqSound.ninseq;

import waffleoRai_SeqSound.MIDI;

public class NoteEvent extends NSEvent{
	
	public NoteEvent(int note, int velocity, int ticks)
	{
		super.setAddress(-1L);
		super.setCommand(NSCommand.PLAY_NOTE);
		super.setParam1(velocity);
		super.setParam2(ticks);
		super.setSubCommand(note);
		super.setSerialSize(1 + 1 + MIDI.VLQlength(ticks));
	}

	public int getNote(){return super.getSubCommand();}
	public int getVelocity(){return super.getParam1();}
	public int getTicks(){return super.getParam2();}
	
	public boolean hasFirstParameter(){return true;}
	public boolean hasSecondParameter(){return true;}
	
	public void execute(NinTrack track)
	{
		//System.err.println("hi :)");
		track.playNote(getNote(), getVelocity(), getTicks());
	}
	
	public byte[] serializeEvent(boolean endian)
	{
		byte[] ticks = MIDI.makeVLQ(this.getTicks());
		byte[] event = new byte[2+ticks.length];
		event[0] = (byte)this.getNote();
		event[1] = (byte)this.getVelocity();
		for(int i = 0; i < ticks.length; i++) event[i+2] = ticks[i];
		return event;
	}
	
	public String toString()
	{
		String name = MIDI.getNoteName(super.getSubCommand());
		return name + " at v" + super.getParam1() + " for " + super.getParam2() + " ticks";
	}

}
