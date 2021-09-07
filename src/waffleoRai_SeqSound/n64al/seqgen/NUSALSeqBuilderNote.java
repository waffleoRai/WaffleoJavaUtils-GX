package waffleoRai_SeqSound.n64al.seqgen;

public class NUSALSeqBuilderNote implements Comparable<NUSALSeqBuilderNote>, MusicEvent{

	private byte midi_note;
	private byte velocity;
	private int length;
	private byte gate;
	
	private NUSALSeqBuilderPhrase linked_phrase;
	private boolean phrase_starter;
	
	public NUSALSeqBuilderNote(byte note, byte v){
		midi_note = note;
		velocity = v;
		length = 0;
		gate = 0x3f; //No idea what this does still... haaaah
	}
	
	public byte getNote(){return midi_note;}
	public byte getVelocity(){return velocity;}
	public byte getGate(){return gate;}
	public boolean linkedToPhrase(){return (linked_phrase != null);}
	public NUSALSeqBuilderPhrase getLinkedPhrase(){return linked_phrase;}
	public boolean isPhraseStart(){return phrase_starter;}
	
	public void incrementLength(){length++;}
	public void setLength(int ticks){length = ticks;}
	public void linkPhrase(NUSALSeqBuilderPhrase phrase, boolean first){
			linked_phrase = phrase; phrase_starter = first;
	}
	public void flagFirstInPhrase(boolean b){phrase_starter = b;}
	
	public void clearLinkedPhrase(){linked_phrase = null; phrase_starter = false;}
	
	public byte getFirstNotePitch(){return midi_note;}
	
	public boolean equals(Object o){
		if(o == this) return true;
		if(o == null) return false;
		if(!(o instanceof NUSALSeqBuilderNote)) return false;
		NUSALSeqBuilderNote other = (NUSALSeqBuilderNote)o;
		
		if(this.midi_note != other.midi_note) return false;
		if(this.velocity != other.velocity) return false;
		if(this.length != other.length) return false;
		
		return true;
	}
	
	public int hashCode(){
		int hash = 0;
		hash |= Byte.toUnsignedInt(midi_note) << 24;
		hash |= Byte.toUnsignedInt(velocity);
		hash ^= length;
		return hash;
	}

	public int compareTo(NUSALSeqBuilderNote o) {
		if(o == null) return 1;
		if(this.midi_note != o.midi_note) return (int)this.midi_note - (int)o.midi_note;
		if(this.velocity != o.velocity) return (int)this.velocity - (int)o.velocity;
		if(this.length != o.length) return this.length - o.length;
		
		return 0;
	}
	
	public int getLengthInTicks(){return length;}
	public int getLengthInEvents(){return 1;}
	public int voicesRequired(){return 1;}
	public int voicesAvailable(){return 3;}
	
	public boolean isPhrase(){return false;}
	
	public NUSALSeqBuilderNote createUnlinkedCopy(){
		NUSALSeqBuilderNote copy = new NUSALSeqBuilderNote(midi_note, velocity);
		copy.length = length;
		return copy;
	}
	
}
