package waffleoRai_SeqSound.misc.smd;

public class SMDDeltaTimeEvent implements SMDEvent{
	
	private int delayTime;
	private int channel;
	
	public SMDDeltaTimeEvent(byte opcode)
	{
		this.interpretDelayTime(opcode);
	}
	
	private void interpretDelayTime(byte opcode)
	{
		int opi = Byte.toUnsignedInt(opcode);
		switch(opi)
		{
		case 0x80: delayTime = 96; return;
		case 0x81: delayTime = 72; return;
		case 0x82: delayTime = 64; return;
		case 0x83: delayTime = 48; return;
		case 0x84: delayTime = 36; return;
		case 0x85: delayTime = 32; return;
		case 0x86: delayTime = 24; return;
		case 0x87: delayTime = 18; return;
		case 0x88: delayTime = 16; return;
		case 0x89: delayTime = 12; return;
		case 0x8A: delayTime = 9; return;
		case 0x8B: delayTime = 8; return;
		case 0x8C: delayTime = 6; return;
		case 0x8D: delayTime = 4; return;
		case 0x8E: delayTime = 3; return;
		case 0x8F: delayTime = 2; return;
		default: delayTime = -1; return;
		}
	}
	
	public int getByteLength(){return 1;}
	public int getDelayTime(){return this.delayTime;}
	public SMDMiscType getType(){return SMDMiscType.NA_DELTATIME;}
	public int getChannel(){return this.channel;}
	public void setChannel(int c){this.channel = c;}
	
	public long getWait() {return delayTime;}
	public void execute(SMDTrack t) {}
	
	public String toString(){
		return "DELTA_TIME: " + delayTime + " ticks";
	}

	public byte[] serializeMe(){
		byte[] b = new byte[1];
		
		switch(delayTime){
		case 2: b[0] = (byte)0x8F; break;
		case 3: b[0] = (byte)0x8E; break;
		case 4: b[0] = (byte)0x8D; break;
		case 6: b[0] = (byte)0x8C; break;
		case 8: b[0] = (byte)0x8B; break;
		case 9: b[0] = (byte)0x8A; break;
		case 12: b[0] = (byte)0x89; break;
		case 16: b[0] = (byte)0x88; break;
		case 18: b[0] = (byte)0x87; break;
		case 24: b[0] = (byte)0x86; break;
		case 32: b[0] = (byte)0x85; break;
		case 36: b[0] = (byte)0x84; break;
		case 48: b[0] = (byte)0x83; break;
		case 64: b[0] = (byte)0x82; break;
		case 72: b[0] = (byte)0x81; break;
		case 96: b[0] = (byte)0x80; break;
		}
		
		return b;
	}
	
}
