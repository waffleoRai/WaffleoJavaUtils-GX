package waffleoRai_SeqSound.misc.smd;

import javax.sound.sampled.SourceDataLine;

import waffleoRai_SeqSound.misc.SMD;
import waffleoRai_SoundSynth.PlayerTrack;
import waffleoRai_SoundSynth.SequencePlayer;
import waffleoRai_SoundSynth.SynthChannel;
import waffleoRai_SoundSynth.SynthProgram;
import waffleoRai_soundbank.procyon.SWD;

/*
 * UPDATES
 * 
 * 2020.05.03 | 1.0.0
 * 	Initial documentation
 * 
 */

/**
 * A SequencePlayer implementation for ChunSoft/Procyon SMD/SWD music/sound
 * files.
 * <br>Most modifications are geared towards the SMD format's unusual means of
 * determining which pitch is to be played - whereas MIDI and most common
 * sequence formats simply provide an 8-bit value correlating to a musical note
 * (where 60 is middle C), SMD instead sends a four-bit octave-relative
 * pitch value and an octave shift. The channel's current octave and relative
 * note must be combined to determine the MIDI equivalent.
 * @author Blythe Hospelhorn
 * @version 1.0.0
 * @since May 3, 2020
 */
public class SMDPlayer extends SequencePlayer{
	
	public static final int SAMPLE_RATE = 44100;
	public static final int BIT_DEPTH = 16;
	
	private SMD sequence;
	private SWD bank;
	
	private SMDSynthChannel[] smd_ch;
	
	public SMDPlayer(SMD seq, SWD bnk){
		super.setTickResolution(seq.getTicksPerQuartNote());
		
		sequence = seq;
		bank = bnk;
		
		channels = new SynthChannel[16];
		smd_ch = new SMDSynthChannel[16];
		for(int i = 0; i < 16; i++){
			smd_ch[i] = new SMDSynthChannel(SAMPLE_RATE,BIT_DEPTH);
			channels[i] = smd_ch[i];
			smd_ch[i].linkPlayer(this, i);
		}
		int tcount = sequence.getNumberTracks();
		tracks = new PlayerTrack[tcount];
		for(int i = 0; i < tcount; i++){
			if(i != 0 && i != 1) continue; //DEBUG
			SMDTrack t = sequence.getTrack(i);
			tracks[i] = t;
			if(t != null) t.setPlayer(this);
		}
	}

	public String getSequenceName() {return sequence.getInternalName();}
	public String getBankName() {return bank.getName();}
	public String getTypeInfoString() {return "Procyon SMD Sequence";}
	public float getSampleRate() {return SAMPLE_RATE;}
	public int getBitDepth() {return BIT_DEPTH;}
	public int getChannelCount() {return 2;}

	protected int saturate(int in) {
		if(in > 0x7FFF) return 0x7FFF;
		if(in < -0x7FFF) return -0x7FFF;
		return in;
	}

	protected void putNextSample(SourceDataLine target) throws InterruptedException {
		//System.err.println("Hi!");
		int[] samps = this.nextSample();
		
		if(samps == null) return;
		int bcount = samps.length << 1;
		byte[] bytes = new byte[bcount];
		int i = 0;
		for(int c = 0; c < samps.length; c++){
			bytes[i++] = (byte)(samps[c] & 0xFF);
			bytes[i++] = (byte)((samps[c] >>> 8) & 0xFF);
		}
		
		target.write(bytes, 0, bcount);
	}
	
	/**
	 * Directly obtain the channel assigned to the provided index.
	 * @param ch Index of channel to get. Valid values depend upon player but
	 * are almost always between 0-15 inclusive.
	 * @return SMD specific implementation of channel object (reference, changes made
	 * to returned object are reflected in player channel state), or null
	 * if index is invalid for this player.
	 * @since 1.0.0
	 */
	public SMDSynthChannel getChannel(int ch){
		return smd_ch[ch];
	}
	
	/**
	 * Induce program change for the specified channel.
	 * @param ch Index of channel to change program of. Valid values are usually 0-15 inclusive.
	 * @param val Index of program in soundbank (SWD) to set.
	 * @since 1.0.0
	 */
	public void programChange(int ch, int val){
		SynthProgram prog = bank.getProgram(0, val);
		smd_ch[ch].setProgram(prog);
		super.sendProgramChangeToListeners(ch, 0, val);
	}
	
	/**
	 * For sequence track use - set loop flag in player superclass
	 * so that playback thread knows to loop.
	 * @since 1.0.0
	 */
	protected void flagLoop(){
		super.setLoopFlag(true);
	}
	
	/**
	 * For sequence track use - mark loop start point in 
	 * superclass player so that playback thread knows where to loop back to.
	 * @param tick
	 * @since 1.0.0
	 */
	protected void setLoop(long tick){
		super.setLoopTick(tick);
	}

	/**
	 * Access method for SMDSynthChannel. Tells player to notify any ChannelStateListeners
	 * connected to this player of a note on event.
	 * @param ch_idx Index of channel note on event has occurred in.
	 * @param note MIDI note that has been turned on.
	 * @since 1.0.0
	 */
	protected void forwardNoteOnToListeners(int ch_idx, byte note){
		super.sendNoteOnToListeners(ch_idx, note);
	}
	
	/**
	 * Access method for SMDSynthChannel. Tells player to notify any ChannelStateListeners
	 * connected to this player of a note off event.
	 * @param ch_idx Index of channel note off event has occurred in.
	 * @param note MIDI note that has been turned off.
	 * @since 1.0.0
	 */
	protected void forwardNoteOffToListeners(int ch_idx, byte note){
		super.sendNoteOffToListeners(ch_idx, note);
	}

}
