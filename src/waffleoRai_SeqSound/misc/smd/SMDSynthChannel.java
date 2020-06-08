package waffleoRai_SeqSound.misc.smd;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import waffleoRai_SoundSynth.SynthChannel;
import waffleoRai_SoundSynth.general.DefaultSynthChannel;

/*
 * UPDATES
 * 
 * 2020.04.27 | 1.0.0
 * 	Initial documentation
 * 
 * 2020.05.03 | 1.1.0
 * 	Debugging + player link (for listeners)
 * 
 * 2020.05.22 | 1.1.1
 * 	I think the set exp/vol ratios need to be squared?
 * 
 * 2020.05.22 | 1.2.0
 * 	Note on and note off return success values
 * 	Track as note on success listener
 */

/**
 * DefaultSynthChannel subclass for SMD sequence playback that
 * holds octave value.
 * <br>SMD play note instructions contain octave shift and relative
 * semitone components instead of raw MIDI note values.
 * @author Blythe Hospelhorn
 * @version 1.2.0
 * @since May 22, 2020
 */
public class SMDSynthChannel extends DefaultSynthChannel{
	
	private int octave;
	private Map<Long, Byte> note_map;
	
	private List<SMDTrack> linked_tracks;
	
	private int index;
	private SMDPlayer player;
	
	/**
	 * Create an SMD specific synth channel.
	 * @param outSampleRate Output sample rate in Hz.
	 * @param bitDepth Output bit depth in bits.
	 * @since 1.0.0
	 */
	public SMDSynthChannel(int outSampleRate, int bitDepth) {
		super(outSampleRate, bitDepth);
		octave = 0;
		note_map = new HashMap<Long, Byte>();
		super.setSquareVol(false);
		linked_tracks = new LinkedList<SMDTrack>();
	}
	
	/**
	 * Add the given amount to the octave value set for the channel.
	 * MIDI notes derived from the simple formula [(octave*12) + semis]
	 * @param amt Amount to shift current channel octave setting. This can be negative
	 * or zero.
	 * @since 1.0.0
	 */
	public void incrementOctave(int amt){
		octave += amt;
	}
	
	/**
	 * Directly set channel octave value.
	 * @param val Value to set for octave.
	 * @since 1.0.0
	 */
	public void setOctave(int val){
		octave = val;
	}
	
	/**
	 * Command the channel to switch a note on by assigning a UID to the note on call and
	 * providing the parameters for the played note.
	 * @param note_id UID to assign to note so that it can be found again when turned off.
	 * @param note_rel Note in semitones relative to beginning (C) of current channel octave.
	 * @param velocity Note on velocity
	 * @throws InterruptedException If the thread receives an unexpected interruption when
	 * trying to retrieve sound sample stream from bank.
	 * @return Value from super class noteOn call describing operation success. (See
	 * SynthChannel)
	 * @since 1.2.0
	 */
	public int smdNoteOn(long note_id, int note_rel, byte velocity) throws InterruptedException{
		int note = note_rel + (octave * 12);
		int val = super.noteOn((byte)note, velocity);
		if(player != null && val == SynthChannel.OP_RESULT_SUCCESS)player.forwardNoteOnToListeners(index, (byte)note);
		if(val == SynthChannel.OP_RESULT_NOTEON_OVERLAP){
			//Let linked tracks know that the previous note has been turned off.
			//Pull the UID
			long nuid = -1;
			for(Entry<Long,Byte> e:note_map.entrySet()){
				if(e.getValue() == note){
					nuid = e.getKey();
				}
			}
			if(nuid != -1){
				note_map.remove(nuid);
				for(SMDTrack t : linked_tracks)t.onNoteReplaced(nuid);
			}
		}
		//else if(val == SynthChannel.OP_RESULT_NOPROG) System.err.println("SMDSynthChannel.smdNoteOn || Empty Program!");
		//else if(val == SynthChannel.OP_RESULT_NOREG) System.err.println("SMDSynthChannel.smdNoteOn || Empty Region!");
		note_map.put(note_id, (byte)note);
		return val;
	}
	
	/**
	 * Command the channel to switch off a note with the provided UID. In case of failure, stderr message
	 * is printed.
	 * @param note_id UID of note to turn off (previously turned on using smdNoteOn()).
	 * @return Value from super class noteOff call describing operation success. (See
	 * SynthChannel)
	 * @since 1.2.0
	 */
	public int smdNoteOff(long note_id){
		Byte mid = note_map.remove(note_id);
		if(mid == null){
			System.err.println("SMDSynthChannel.smdNoteOff || WARNING: Note off requested for note that is not on!");
			return SynthChannel.OP_RESULT_NOTEOFF_NOTON;
		}
		//System.err.println("SMDSynthChannel.smdNoteOff || Note Off: " + mid + " ID = 0x" + Long.toHexString(note_id));
		int val = super.noteOff(mid, (byte)100);
		if(player != null)player.forwardNoteOffToListeners(index, mid);
		return val;
	}

	/**
	 * Link back the player using this channel so that it can receive note on/note off notifications
	 * from this channel and forward them to listeners.
	 * @param p Player to link.
	 * @param chidx Index of this channel in the player.
	 * @since 1.1.0
	 */
	public void linkPlayer(SMDPlayer p, int chidx){
		player = p;
		index = chidx;
	}
	
	/**
	 * Link an SMDTrack to listen to note on overlap events so that tracks sending
	 * notes to this channel know to stop holding a note event that the channel has terminated.
	 * @param t Track to link as a listener
	 * @since 1.2.0
	 */
	public void linkListenerTrack(SMDTrack t){
		linked_tracks.add(t);
	}
	
}
