package waffleoRai_SeqSound.n64al.seqgen;

public class NUSALSeqInitValues {
	
	public byte mutebhv = 0x20;
	public byte mutescale = 50;
	public boolean[] track_used;
	
	public int tempo = 120;
	public int volume = 127;
	
	public NUSALSeqInitValues(){
		track_used = new boolean[16];
	}

}
