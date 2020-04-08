package waffleoRai_SeqSound.ninseq;

/*
 * UPDATES
 * 
 * 2020.03.19 | 1.0.0
 * 		Initial Documentation
 * 
 */

/**
 * A player for a NinSeq that can receive NinSeq commands and alter its state
 * accordingly.
 * Implementations of NinSeqPlayer are linked to a NinTrack which sends it commands
 * from raw sequence data.
 * @author Blythe Hospelhorn
 * @version 1.0.0
 * @since March 19, 2020
 */
public interface NinSeqPlayer {
	
	public static final int VAR_OP_TYPE_ADD = 0;
	public static final int VAR_OP_TYPE_SUBTRACT = 1;
	public static final int VAR_OP_TYPE_MULT = 2;
	public static final int VAR_OP_TYPE_DIV = 3;
	public static final int VAR_OP_TYPE_MOD = 4;
	public static final int VAR_OP_TYPE_AND = 5;
	public static final int VAR_OP_TYPE_OR = 6;
	public static final int VAR_OP_TYPE_XOR = 7;
	public static final int VAR_OP_TYPE_NOT = 8;
	public static final int VAR_OP_TYPE_SHIFT = 9;
	
	/**
	 * Because NinSeqs are more machine-language like than MIDI, they can
	 * (and often do) contain function calls and jump commands that tell the player
	 * to move to the address of a seq command elsewhere in the file.
	 * <br>If a NinSeq player implementation disables these, it's important to know.
	 * Therefore, this method queries whether these jump and function calls are
	 * followed or ignored.
	 * @return True if jump commands are followed, false if ignored.
	 * @since 1.0.0
	 */
	public boolean jumpsAllowed();
	
	/**
	 * Start playback/activity of NinSeq player. If player implementation is also
	 * an implementation of SynthPlayer, this method should be synonymous with 
	 * startAsyncPlaybackToDefaultOutputDevice().
	 * @since 1.0.0
	 */
	public void play();
	
	/**
	 * Pause playback/activity of NinSeq player. This method should not terminate
	 * the playback completely or close any open lines. It should also maintain the 
	 * state of the player should playback be resumed.
	 * @since 1.0.0
	 */
	public void pause();
	
	/**
	 * Stop playback/activity of NinSeq player and reset it to its initial state.
	 * This should terminate any background threads and close any audio lines without
	 * rendering the player itself unusable in the future.
	 * @since 1.0.0
	 */
	public void stop();
	
	/**
	 * Stop playback/activity of only a single track.
	 * @param trackIndex Index of track to stop.
	 * @since 1.0.0
	 */
	public void stopTrack(int trackIndex);
	
	/**
	 * Check whether this player is active.
	 * @return True if player is running, false otherwise.
	 * @since 1.0.0
	 */
	public boolean isPlaying();
	
	/**
	 * Send signal requesting player turn note on for specified channel/track.
	 * @param tidx Index of track/channel command is targeted to. These are synonymous
	 * to NinSeq commands.
	 * @param note MIDI note played (B*SEQ uses the same values)
	 * @param velocity Note on velocity (0-127)
	 * @since 1.0.0
	 */
	public void noteOn(int tidx, int note, int velocity);
	
	/**
	 * Send signal requesting player turn note off for specified channel/track.
	 * @param tidx Index of track/channel command is targeted to. These are synonymous
	 * to NinSeq commands.
	 * @param note MIDI note released (B*SEQ uses the same values)
	 * @since 1.0.0
	 */
	public void noteOff(int tidx, int note);
	
	/**
	 * Send signal requesting player mute specified channel/track.
	 * This should only silence the audio output, track events should 
	 * still be advancing to stay synced with other tracks.
	 * @param tidx Index of track/channel command is targeted to. These are synonymous
	 * to NinSeq commands.
	 * @since 1.0.0
	 */
	public void mute(int tidx);
	
	/**
	 * Send signal requesting player unmute specified channel/track.
	 * This does nothing if track is not muted.
	 * @param tidx Index of track/channel command is targeted to. These are synonymous
	 * to NinSeq commands.
	 * @since 1.0.0
	 */
	public void unmute(int tidx);
	
	/**
	 * Request that player "open" a new track starting at the specified
	 * address relative to the SEQ start. 
	 * @param tidx Index of new track/channel.
	 * @param addr Address in SEQ of command new track should start at.
	 * @since 1.0.0
	 */
	public void openTrack(int tidx, int addr);
	
	/**
	 * Request a tempo change from the player (specified in beats per minute).
	 * @param bpm New tempo in beats per minute (note this is different from the MIDI
	 * standard of microseconds per beat).
	 * @since 1.0.0
	 */
	public void setTempoBPM(int bpm);
	
	/**
	 * Some variations of Nintendo SEQ involve (P)RNG. This method calls on the 
	 * player to generate and return a sufficiently random integer.
	 * @return Any possible 32-bit value to be used by the requesting command.
	 * @since 1.0.0
	 */
	public int getRandomNumber();
	
	/**
	 * Get the value of a stored variable in the player. Nintendo SEQs can utilize
	 * stored variables to determine how they are played. Thus, the player must
	 * implement some way of storing variables as requested by the SEQ.
	 * @param vidx Variable index (0-255)
	 * @return Current value of the variable specified by the given index.
	 * @since 1.0.0
	 */
	public short getVariableValue(int vidx);
	
	/**
	 * Store a variable value in the player. Nintendo SEQs can utilize
	 * stored variables to determine how they are played. Thus, the player must
	 * implement some way of storing variables as requested by the SEQ.
	 * @param vidx Variable index (0-255)
	 * @param value Value to set to specified variable.
	 * @since 1.0.0
	 */
	public void setVariableValue(int vidx, short value);
	
	/**
	 * Set the velocity range for the specified track/channel.
	 * @param tidx Index of track/channel command is targeted to. These are synonymous
	 * to NinSeq commands.
	 * @param min Bottom of velocity range (inclusive)
	 * @param max Top of velocity range (inclusive)
	 * @since 1.0.0
	 */
	//public void setVelocityRange(int tidx, int min, int max);
	
	/**
	 * Set the (stereo) pan for the specified track/channel.
	 * @param tidx Index of track/channel command is targeted to. These are synonymous
	 * to NinSeq commands.
	 * @param pan MIDI standard value to set for pan (8-bit signed, centered around 0x40)
	 * @since 1.0.0
	 */
	public void updateTrackPan(int tidx, int pan);
	
	/**
	 * Set the (stereo) initial pan for the specified track/channel
	 * <br><i>Note: This is currently mostly unimplemented</i>
	 * @param tidx Index of track/channel command is targeted to. These are synonymous
	 * to NinSeq commands.
	 * @param value MIDI standard value to set for pan (8-bit signed, centered around 0x40)
	 * @since 1.0.0
	 */
	public void setTrackInitPan(int tidx, int value);
	
	/**
	 * Set the surround pan for the specified track/channel.
	 * <br><i>Note 1: Don't currently know how value scales</i>
	 * <br><i>Note 2: This is currently mostly unimplemented</i>
	 * @param tidx Index of track/channel command is targeted to. These are synonymous
	 * to NinSeq commands.
	 * @param value (Surround Pan) 8 bits, scaling unknown.
	 * @since 1.0.0
	 */
	public void updateTrackSurroundPan(int tidx, int value);
	
	/**
	 * Set the master volume for the specified track/channel.
	 * @param tidx Index of track/channel command is targeted to. These are synonymous
	 * to NinSeq commands.
	 * @param vol MIDI standard volume level 0x00-0x7F 
	 * @since 1.0.0
	 */
	public void updateTrackVolume(int tidx, int vol);
	
	/**
	 * Set the master volume of the player using a standard MIDI value.
	 * @param vol MIDI standard volume level 0x00-0x7F 
	 * @since 1.0.0
	 */
	public void setMasterVolume(int vol);
	
	/**
	 * Set the pitch bend value in cents for the specified track/channel.
	 * @param tidx Index of track/channel command is targeted to. These are synonymous
	 * to NinSeq commands.
	 * @param cents Pitch bend value in cents. The track usually does
	 * the conversion considering raw pitch wheel value and current range
	 * beforehand. Value in cents needs to be converted back to pitch wheel
	 * level/ range for use in some functions in the SynthPlayer API
	 * @since 1.0.0
	 */
	public void updatePitchBend(int tidx, int cents);
	
	/**
	 * Set the track priority in the player for the specified track/channel.
	 * <br><i>Note 1: Don't currently know how value scales</i>
	 * <br><i>Note 2: This is currently mostly unimplemented</i>
	 * @param tidx Index of track/channel command is targeted to. These are synonymous
	 * to NinSeq commands.
	 * @param pri (Priority) 8 bit, presumably used for player to determine
	 * which tracks to mute if voices are needed for other audio?
	 * @since 1.0.0
	 */
	public void setTrackPriority(int tidx, int pri);
	
	/**
	 * Set expression value for the specified track/channel. 
	 * Expression is generally synonymous with track volume from an
	 * implementation perspective, but is considered a separate variable
	 * from the perspective of the composer/sound designer.
	 * @param tidx Index of track/channel command is targeted to. These are synonymous
	 * to NinSeq commands.
	 * @param value MIDI standard volume level 0x00-0x7F 
	 * @since 1.0.0
	 */
	public void setExpression(int tidx, int value);
	
	/**
	 * Switch on/off damper for specified track/channel.
	 * <br><i>Note: This is currently mostly unimplemented. Assumption is
	 * made that non-zero values mean on and zero means off.</i>
	 * @param tidx Index of track/channel command is targeted to. These are synonymous
	 * to NinSeq commands.
	 * @param on True to set damper on, false to set damper off.
	 * @since 1.0.0
	 */
	public void setDamper(int tidx, boolean on);
	
	/**
	 * Switch on/off monophony for specified track/channel.
	 * If monophony is on, only one voice can sound at a time from
	 * a channel.
	 * @param tidx Index of track/channel command is targeted to. These are synonymous
	 * to NinSeq commands.
	 * @param on True to set monophony on (polyphony off), false to set monophony off
	 * (polyphony on).
	 * @since 1.0.0
	 */
	public void setMonophony(int tidx, boolean on);
	
	/**
	 * Set the timebase or tick resolution for the specified track/channel.
	 * @param tidx Index of track/channel command is targeted to. These are synonymous
	 * to NinSeq commands.
	 * @param value New tick resolution in TPQN (ticks per quarter note).
	 * @since 1.0.0
	 */
	public void setTimebase(int tidx, int value);
	
	/**
	 * Set the biquad value for the specified track/channel. Presumably
	 * used for filter definitions?
	 * <br><i>Note 1: Don't currently know how value scales</i>
	 * <br><i>Note 2: This is currently mostly unimplemented</i>
	 * @param tidx Index of track/channel command is targeted to. These are synonymous
	 * to NinSeq commands.
	 * @param value (Biquad Value) 8 bits, scaling/meaning unknown
	 * @since 1.0.0
	 */
	public void setBiquadValue(int tidx, int value);
	
	/**
	 * Set the biquad type for the specified track/channel. Presumably
	 * used for filter definitions?
	 * <br><i>Note 1: Don't currently know how value scales</i>
	 * <br><i>Note 2: This is currently mostly unimplemented</i>
	 * @param tidx Index of track/channel command is targeted to. These are synonymous
	 * to NinSeq commands.
	 * @param value (Biquad Type) 8 bits, scaling/meaning unknown
	 * @since 1.0.0
	 */
	public void setBiquadType(int tidx, int value);
	
	/**
	 * Set pitch sweep value for the specified track/channel.
	 * <br><i>Note 1: Don't currently know how value scales</i>
	 * <br><i>Note 2: This is currently mostly unimplemented</i>
	 * @param tidx Index of track/channel command is targeted to. These are synonymous
	 * to NinSeq commands.
	 * @param value (Pitch Sweep) Signed 16 bits
	 * @since 1.0.0
	 */
	public void setPitchSweep(int tidx, int value);
	
	/**
	 * Set the bank index for the program track/channel is using. This
	 * functions as a program change.
	 * @param tidx Index of track/channel command is targeted to. These are synonymous
	 * to NinSeq commands.
	 * @param bankIndex Index of bank to change to.
	 * @since 1.0.0
	 */
	//public void changeTrackBank(int tidx, int bankIndex);
	
	/**
	 * Change program for the specified track/channel.
	 * @param tidx Index of track/channel command is targeted to. These are synonymous
	 * to NinSeq commands.
	 * @param bank Index of bank to change to.
	 * @param program Index of program to change to.
	 * @since 1.0.0
	 */
	public void changeTrackProgram(int tidx, int program);
	
	/**
	 * Change program for the specified track/channel.
	 * @param tidx Index of track/channel command is targeted to. These are synonymous
	 * to NinSeq commands.
	 * @param program Index of program to change to.
	 * @since 1.0.0
	 */
	public void changeTrackProgram(int tidx, int bank, int program);
	
	public void setPortamentoControl(int tidx, int note);
	public void setPortamento(int tidx, boolean on);
	public void setPortamentoTime(int tidx, int time);
	
	public void setModulationDepth(int tidx, int value);
	public void setModulationSpeed(int tidx, int value);
	public void setModulationType(int tidx, int value);
	public void setModulationRange(int tidx, int value);
	public void setModulationDelay(int tidx, int millis);
	
	public void setAttackOverride(int tidx, int millis);
	public void setDecayOverride(int tidx, int millis);
	public void setSustainOverride(int tidx, int level);
	public void setReleaseOverride(int tidx, int millis);
	public void setEnvelopeHold(int tidx, int millis); //Not sure if on/off or millis
	public void envelopeReset(int tidx);
	
	public void printVariable(int vidx);
	
	public void setLPFCutoff(int tidx, int frequency);
	public void setFXSendLevel(int tidx, int send, int level);
	public void setMainSendLevel(int tidx, int level);
	
	public void addImmediate(int vidx, short imm);
	public void subtractImmediate(int vidx, short imm);
	public void multiplyImmediate(int vidx, short imm);
	public void divideImmediate(int vidx, short imm);
	public void modImmediate(int vidx, short imm);
	public void andImmediate(int vidx, int imm);
	public void orImmediate(int vidx, int imm);
	public void xorImmediate(int vidx, int imm);
	public void notImmediate(int vidx, int imm);
	public void shiftImmediate(int vidx, int imm);
	
}
