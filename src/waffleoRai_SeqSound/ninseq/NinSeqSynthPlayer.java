package waffleoRai_SeqSound.ninseq;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

import waffleoRai_SoundSynth.PlayerTrack;
import waffleoRai_SoundSynth.SequencePlayer;
import waffleoRai_SoundSynth.SynthBank;
import waffleoRai_SoundSynth.SynthProgram;
import waffleoRai_SoundSynth.general.DefaultSynthChannel;

public class NinSeqSynthPlayer extends SequencePlayer implements NinSeqPlayer{

	/*--- Constants ---*/
	
	public static final int SRC_SSEQ = 1; //DS SSEQ
	public static final int SRC_RSEQ = 2; //Wii RSEQ
	public static final int SRC_CSEQ = 3; //3DS CSEQ
	public static final int SRC_FSEQ = 4; //Wii U FSEQ
	
	public static final String[] INFO_STRS = {"Nintendo Sound Sequence",
											  "Binary Nitro Sound Sequence (SSEQ)",
											  "Binary Revolution Sound Sequence (RSEQ)",
			                                  "Binary CTR Sound Sequence (CSEQ)",
			                                  "Binary CafeOS Sound Sequence (FSEQ)"};
	
	public static final int OUTPUT_SAMPLERATE = 44100;
	public static final int OUTPUT_SAMPLERATE_CAFE = 48000;
	
	/*--- Instance Variables ---*/
	
	private String seq_name;
	private String bnk_name;
	private int src_type; //For info string or other uses
	
	private int samplerate;
	private int bitdepth;
	
	private long startPos; //For rewinding or referencing
	private NinSeqDataSource source;
	private SynthBank bnk;
	
	private ShortRegister[] registers;
	private Random rng;
	
	private NinTrack[] nin_tracks;
	
	private List<NinSeqPlayerValueChangeListener> val_listeners;
	
	/*--- Inner Classes ---*/
	
	private class ShortRegister
	{
		private volatile short value;
		
		public ShortRegister(){value = 0;}
		public short read(){return value;}
		public synchronized void write(short val){value = val;}
	}
	
	public class NinSeqSynthTrack implements PlayerTrack{

		private NinTrack track;
		
		public NinSeqSynthTrack(NinTrack t){
			track = t;
		}
		
		public void onTick(long tick) throws InterruptedException {
			//Change to just onTick if getting lag??? Probably won't help much.
			track.onTickFlagLoop();
		}

		public void resetTo(long tick) {
			//I'm feeling lazy - maybe I'll fill this in proper later...
			track.rewind();
		}

		public boolean trackEnd() {
			return track.trackEnded();
		}

		public void setMute(boolean b) {
			track.setInternalMute(b);
		}

		public boolean isMuted() {
			return track.getInternalMute();
		}
		
	}
	
	/*--- Construction ---*/
	
	public NinSeqSynthPlayer(NinSeqDataSource seq, SynthBank bank, long staddr){
		
		val_listeners = new LinkedList<NinSeqPlayerValueChangeListener>();
		
		super.allocateChannels(16);
		super.setTickResolution(NinSeq.TICKS_PER_QNOTE);
		
		bnk_name = "Anonymous Soundbank";
		
		source = seq;
		bnk = bank;
		startPos = staddr; //Track 0
		
		//Initialize for specific mode...
		int t = seq.getPlayerMode();
		switch(t){
		case NinSeq.PLAYER_MODE_DS:
			samplerate = OUTPUT_SAMPLERATE;
			bitdepth = 16;
			src_type = SRC_SSEQ;
			seq_name = "Anonymous DS Sequence";
			break;
		case NinSeq.PLAYER_MODE_WII:
			samplerate = OUTPUT_SAMPLERATE;
			bitdepth = 16;
			src_type = SRC_RSEQ;
			seq_name = "Anonymous Wii Sequence";
			break;
		case NinSeq.PLAYER_MODE_3DS:
			samplerate = OUTPUT_SAMPLERATE;
			bitdepth = 16;
			src_type = SRC_CSEQ;
			seq_name = "Anonymous 3DS Sequence";
			break;
		case NinSeq.PLAYER_MODE_CAFE:
			samplerate = OUTPUT_SAMPLERATE_CAFE;
			bitdepth = 16;
			src_type = SRC_FSEQ;
			seq_name = "Anonymous Wii U Sequence";
			break;
		}
		
		for(int i = 0; i < 16; i++){super.channels[i] = new DefaultSynthChannel(samplerate, bitdepth);}
		
		registers = new ShortRegister[256];
		for(int i = 0; i < 256; i++) registers[i] = new ShortRegister();
		rng = new Random();
		
		super.tracks = new PlayerTrack[16]; //Allocated by openTrack()
		nin_tracks = new NinTrack[16];
		
		//Open track 0
		openTrack(0, (int)startPos);
	}
	
	/*--- Getters ---*/
	
	public String getSequenceName() {return seq_name;}
	public String getBankName() {return bnk_name;}
	public String getTypeInfoString() {return INFO_STRS[src_type];}
	public float getSampleRate() {return samplerate;}
	public int getBitDepth() {return bitdepth;}
	public int getChannelCount() {return 2;} //Right now, only stereo supported, though the files have support for 5.1 surround
	public boolean jumpsAllowed() {return true;} //This is real-time, not captured, so won't bother turning off jumps
	
	public boolean isPlaying() {
		return super.isRunning();
	}
	
	/*--- Setters ---*/
	
	public void setSequenceName(String name){this.seq_name = name;}
	public void setBankName(String name){this.bnk_name = name;}
	
	public void addValueChangeListener(NinSeqPlayerValueChangeListener l){
		val_listeners.add(l);
	}
	
	public void clearValueChangeListeners(){val_listeners.clear();}
	
	/*--- NinSeq Control ---*/
	
	//-- Play
	
	public void play() {
		try {startAsyncPlaybackToDefaultOutputDevice();} 
		catch (LineUnavailableException e) 
		{e.printStackTrace();}
	}

	@Override
	public void stopTrack(int trackIndex) {
		// TODO Auto-generated method stub
		
	}

	public void openTrack(int tidx, int addr) {
		//if(tidx != 0) return; //Debug
		//if(tidx != 0 && tidx != 10) return; //Debug
		NinTrack t = new NinTrack(source, this, addr, tidx);
		super.tracks[tidx] = new NinSeqSynthTrack(t);
		nin_tracks[tidx] = t;
		//System.err.println("Open Track: " + tidx);
	}
	
	//-- Note Play & Pitch

	public void noteOn(int tidx, int note, int velocity) {
		
		try {super.channels[tidx].noteOn((byte)note, (byte)velocity);} 
		catch (InterruptedException e) {
			e.printStackTrace();
		}
		super.sendNoteOnToListeners(tidx, (byte)note);
	}

	public void noteOff(int tidx, int note) {
		super.channels[tidx].noteOff((byte)note, (byte)0);
		super.sendNoteOffToListeners(tidx, (byte)note);
	}

	public void updatePitchBend(int tidx, int cents) {
		channels[tidx].setPitchBendDirect(cents);
		super.sendPitchWheelToListeners(tidx, nin_tracks[tidx].getPitchWheel(cents));
	}
	
	public void setMonophony(int tidx, boolean on) {
		nin_tracks[tidx].setInternalMonophony(on);
	}
	
	//-- Volume
	
	public void mute(int tidx) {
		nin_tracks[tidx].setInternalMute(true);
	}

	public void unmute(int tidx) {
		nin_tracks[tidx].setInternalMute(false);
	}
	
	public void changeTrackProgram(int tidx, int program) {
		int nowbank = nin_tracks[tidx].getBankIndex();
		SynthProgram p = bnk.getProgram(nowbank, program);
		channels[tidx].setProgram(p);
		super.sendProgramChangeToListeners(tidx, nowbank, program);
	}

	public void changeTrackProgram(int tidx, int bank, int program) {
		SynthProgram p = bnk.getProgram(bank, program);
		channels[tidx].setProgram(p);
		super.sendProgramChangeToListeners(tidx, bank, program);
	}

	public void printVariable(int vidx) {
		short var = registers[vidx].read();
		System.err.println("$" + vidx + " = 0x" + String.format("%04x", var));
	}
	
	//-- Pan
	
	public void updateTrackPan(int tidx, int pan) {
		channels[tidx].setPan((byte)pan);
		super.sendPanToListeners(tidx, (byte)pan);
	}

	@Override
	public void setTrackInitPan(int tidx, int value) {
		// TODO Unimplemented
		System.err.println("Unimplemented command from track " + tidx + ": Track Init Pan - 0x" + String.format("%02x", value));
	}

	@Override
	public void updateTrackSurroundPan(int tidx, int value) {
		// TODO Unimplemented
		System.err.println("Unimplemented command from track " + tidx + ": Track Surround Pan - 0x" + String.format("%02x", value));
	}

	public void updateTrackVolume(int tidx, int vol) {
		double lvl = (double)vol/127.0;
		channels[tidx].setVolume((byte)vol);
		super.sendVolumeToListeners(tidx, lvl);
	}

	public void setMasterVolume(int vol) {
		double lvl = (double)vol/127.0;
		setMasterAttenuation(lvl);
	}
	
	public void setExpression(int tidx, int value) {
		double lvl = (double)value/127.0;
		channels[tidx].setExpression((byte)value);
		super.sendVolumeToListeners(tidx, lvl);
	}

	public void setDamper(int tidx, boolean on) {
		// TODO Unimplemented
		System.err.println("Unimplemented command from track " + tidx + ": Set Damper - " + on);
	}

	//-- Time
	
	public void setTempoBPM(int bpm)
	{
		//Convert bpm to microseconds
		int us = (int)Math.round(60000000.0 / (double)bpm);
		super.setTempo(us);
		//System.err.println("Set Tempo: " + bpm);
	}
	
	public void setTimebase(int tidx, int value) {
		//not 100% sure...
		super.setTickResolution(value);
	}

	//-- Modulation
	
	public void setModulationDepth(int tidx, int value) {
		// TODO Unimplemented
		System.err.println("Unimplemented command from track " + tidx + ": Mod Depth - 0x" + String.format("%02x", value));
	}

	@Override
	public void setModulationSpeed(int tidx, int value) {
		// TODO Unimplemented
		System.err.println("Unimplemented command from track " + tidx + ": Mod Speed - 0x" + String.format("%02x", value));
	}

	@Override
	public void setModulationType(int tidx, int value) {
		// TODO Unimplemented
		System.err.println("Unimplemented command from track " + tidx + ": Mod Type - 0x" + String.format("%02x", value));	// TODO Auto-generated method stub
	}

	@Override
	public void setModulationRange(int tidx, int value) {
		// TODO Unimplemented
		System.err.println("Unimplemented command from track " + tidx + ": Mod Range - 0x" + String.format("%02x", value));
	}

	public void setModulationDelay(int tidx, int millis) {
		// TODO Unimplemented
		System.err.println("Unimplemented command from track " + tidx + ": Mod Delay - 0x" + String.format("%04x", millis));	
	}

	//-- Envelope
	
	public void setAttackOverride(int tidx, int millis) {
		// TODO Unimplemented
		System.err.println("Unimplemented command from track " + tidx + ": Set Attack");
	}

	@Override
	public void setDecayOverride(int tidx, int millis) {
		// TODO Unimplemented
		System.err.println("Unimplemented command from track " + tidx + ": Set Decay");
	}

	@Override
	public void setSustainOverride(int tidx, int level) {
		// TODO Unimplemented
		System.err.println("Unimplemented command from track " + tidx + ": Set Sustain");
	}

	@Override
	public void setReleaseOverride(int tidx, int millis) {
		// TODO Unimplemented
		System.err.println("Unimplemented command from track " + tidx + ": Set Release");
	}

	@Override
	public void setEnvelopeHold(int tidx, int millis) {
		// TODO Unimplemented
		System.err.println("Unimplemented command from track " + tidx + ": Set Hold");
	}

	@Override
	public void envelopeReset(int tidx) {
		// TODO Unimplemented
		System.err.println("Unimplemented command from track " + tidx + ": Envelope Reset");
	}
	
	//-- Effects
	
	public void setBiquadValue(int tidx, int value) {
		// TODO Unimplemented
		System.err.println("Unimplemented command from track " + tidx + ": Biquad Value - 0x" + String.format("%02x", value));
	}

	@Override
	public void setBiquadType(int tidx, int value) {
		// TODO Unimplemented
		System.err.println("Unimplemented command from track " + tidx + ": Biquad Type - 0x" + String.format("%02x", value));	
	}

	@Override
	public void setPitchSweep(int tidx, int value) {
		// TODO Unimplemented
		System.err.println("Unimplemented command from track " + tidx + ": Pitch Sweep - 0x" + String.format("%04x", value));
	}

	public void setPortamentoControl(int tidx, int note) {
		// TODO Unimplemented
		System.err.println("Unimplemented command from track " + tidx + ": Portamento Control - 0x" + String.format("%02x", note));
	}

	public void setPortamento(int tidx, boolean on) {
		// TODO Unimplemented
		System.err.println("Unimplemented command from track " + tidx + ": Set Portamento - " + on);
	}

	public void setPortamentoTime(int tidx, int time) {
		// TODO Unimplemented
		System.err.println("Unimplemented command from track " + tidx + ": Portamento Time - 0x" + String.format("%02x", time));
	}
	
	public void setLPFCutoff(int tidx, int frequency) {
		// TODO Unimplemented
		System.err.println("Unimplemented command from track " + tidx + ": LPF Cutoff - 0x" + String.format("%02x", frequency));
	}

	public void setFXSendLevel(int tidx, int send, int level) {
		// TODO Unimplemented
		System.err.println("Unimplemented command from track " + tidx + ": FX Send - 0x" + String.format("%02x", level));
	}

	public void setMainSendLevel(int tidx, int level) {
		// TODO Unimplemented
		System.err.println("Unimplemented command from track " + tidx + ": Main Send - 0x" + String.format("%02x", level));
	}
	
	//-- Player State

	public int getRandomNumber() {
		return rng.nextInt();
	}

	public short getVariableValue(int vidx) {
		return registers[vidx].read();
	}

	public void setVariableValue(int vidx, short value) {
		System.err.println("$" + vidx + " = " + value);
		registers[vidx].write(value);
		for(NinSeqPlayerValueChangeListener l : val_listeners){
			l.onValueChanged(vidx, value);
		}
	}
	
	public void setTrackPriority(int tidx, int pri) {
		// TODO Unimplemented
		System.err.println("Unimplemented command from track " + tidx + ": Track Priority - 0x" + String.format("%02x", pri));
	}

	public void addImmediate(int vidx, short imm) {
		registers[vidx].write((short)(registers[vidx].read() + imm));
	}

	@Override
	public void subtractImmediate(int vidx, short imm) {
		registers[vidx].write((short)(registers[vidx].read() - imm));
	}

	@Override
	public void multiplyImmediate(int vidx, short imm) {
		registers[vidx].write((short)(registers[vidx].read() * imm));
	}

	public void divideImmediate(int vidx, short imm) {
		registers[vidx].write((short)(registers[vidx].read() / imm));
	}

	public void modImmediate(int vidx, short imm) {
		registers[vidx].write((short)(registers[vidx].read() % imm));
	}

	public void andImmediate(int vidx, int imm) {
		int current = (int)registers[vidx].read();
		registers[vidx].write((short)(current & imm));
	}

	public void orImmediate(int vidx, int imm) {
		int current = (int)registers[vidx].read();
		registers[vidx].write((short)(current | imm));
	}

	public void xorImmediate(int vidx, int imm) {
		int current = (int)registers[vidx].read();
		registers[vidx].write((short)(current ^ imm));
	}

	public void notImmediate(int vidx, int imm) {
		registers[vidx].write((short)(~imm));
	}

	public void shiftImmediate(int vidx, int imm) {
		int current = (int)registers[vidx].read();
		if(imm < 0) registers[vidx].write((short)(current << imm));
		else registers[vidx].write((short)(current >> imm));
	}
	
	/*--- External Control ---*/
	
	@Override
	protected int saturate(int in) {
		//For now just 16 bit
		if(in > 0x7FFF) return 0x7FFF;
		if(in < -0x7FFF) return -0x7FFF;
		return in;
	}

	protected void putNextSample(SourceDataLine target) throws InterruptedException {
		int[] samps = nextSample();
		byte[] dat = new byte[4];
		//System.err.println(String.format("%04x %04x", samps[0], samps[1]));
		
		dat[0] = (byte)(samps[0] & 0x7F);
		dat[1] = (byte)(samps[0] >>> 8);
		dat[2] = (byte)(samps[1] & 0x7F);
		dat[3] = (byte)(samps[1] >>> 8);
		
		target.write(dat, 0, 4);
	}

}
