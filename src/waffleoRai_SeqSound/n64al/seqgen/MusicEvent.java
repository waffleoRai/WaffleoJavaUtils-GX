package waffleoRai_SeqSound.n64al.seqgen;

public interface MusicEvent {
	
	public int getLengthInTicks();
	public int getLengthInEvents();
	public int voicesRequired();
	public int voicesAvailable();
	
	public byte getFirstNotePitch();
	
	public boolean isPhrase();

}
