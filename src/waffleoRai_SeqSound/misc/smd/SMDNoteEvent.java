package waffleoRai_SeqSound.misc.smd;

public class SMDNoteEvent implements SMDEvent{
	
	private int velocity;
	
	private int lBytes;
	private int length;
	
	private int note; //(0 - F, not yet converted to midi scale)
	private int octaveChange; //Signed value is how many octaves to move up or down if any
	
	private int channel;
	
	public SMDNoteEvent(byte velocity, byte keyFlags){
		this.velocity = Byte.toUnsignedInt(velocity);
		this.readLenFlags(keyFlags);
		this.readOctFlag(keyFlags);
		this.readNote(keyFlags);
	}
	
	private void readLenFlags(byte keyFlags){
		int lb = (Byte.toUnsignedInt(keyFlags) >> 6) & 0x3;
		this.lBytes = lb;
		this.length = -1;
	}
	
	private void readOctFlag(byte keyFlags){
		int oc = (Byte.toUnsignedInt(keyFlags) >> 4) & 0x3;
		switch(oc)
		{
		case 0: this.octaveChange = -2; break;
		case 1: this.octaveChange = -1; break;
		case 2: this.octaveChange = 0; break;
		case 3: this.octaveChange = 1; break;
		}
	}
	
	private void readNote(byte keyFlags){
		this.note = Byte.toUnsignedInt(keyFlags) & 0xF;
	}
	
	public int getByteLength(){return 2 + lBytes;}
	public void setLength(int ticks){this.length = ticks;}
	public SMDMiscType getType(){return SMDMiscType.NA_NOTE;}
	public int getVelocity(){return velocity;}
	public int getLength(){return length;	}
	public int getNote(){return note;}
	public int getOctaveChange(){return this.octaveChange;}
	public int getChannel(){return this.channel;}
	public void setChannel(int c){this.channel = c;}
	
	public long getWait() {return length;}
	public void execute(SMDTrack t) throws InterruptedException {
		t.smdNoteOn(octaveChange, note, velocity, length);
		
	}
	
	public String toString(){
		return "PLAY_NOTE: " + note + " oct " + octaveChange + " at v" + velocity + " for " + length;
	}

	public byte[] serializeMe(){

		int blen = getByteLength();
		byte[] b = new byte[blen];
		b[0] = (byte)velocity;
		int keyflag = 0;
		switch(lBytes){
		case 1: keyflag = 0x40; break;
		case 2: keyflag = 0x80; break;
		case 3: keyflag = 0xC0; break;
		}
		int oshift = octaveChange + 2;
		keyflag |= (oshift & 0x3) << 4; 
		keyflag |= (note & 0xF);
		b[1] = (byte)keyflag;
		
		switch(lBytes){
		case 1: b[2] = (byte)length; break;
		case 2:
			b[2] = (byte)((length >>> 8) & 0xFF);
			b[3] = (byte)(length & 0xFF);
			break;
		case 3:
			b[2] = (byte)((length >>> 16) & 0xFF);
			b[3] = (byte)((length >>> 8) & 0xFF);
			b[4] = (byte)(length & 0xFF);
			break;
		}
		
		return b;
	}
	
}
