package waffleoRai_SeqSound.ninseq;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.Sequence;
import javax.sound.midi.Track;

import waffleoRai_SeqSound.MidiMessageGenerator;

public class NinSeqMidiConverter implements NinSeqPlayer{
	
	/* ----- Constants ----- */
	
	public static final int MIDI_IF_MODE_CHECK = 0;
	public static final int MIDI_IF_MODE_ALWAYS_TRUE = 1;
	public static final int MIDI_IF_MODE_ALWAYS_FALSE = 2;
	
	public static final int MIDI_RANDOM_MODE_RANDOM = 0;
	public static final int MIDI_RANDOM_MODE_USEDEFO = 1;
	
	public static final int MIDI_VAR_MODE_USEVAR = 0;
	public static final int MIDI_VAR_MODE_USEDEFO = 1;
	
	public static final int MIDI_DEFO_OFF_VEL = 0x7F;
	
	public static final int NRPN_MIDI_IDX_MOD_DELAY = 0x2510;
	public static final int NRPN_MIDI_IDX_MOD_DEPTH = 0x2511;
	public static final int NRPN_MIDI_IDX_MOD_RANGE = 0x2512;
	public static final int NRPN_MIDI_IDX_MOD_SPEED = 0x2513;
	public static final int NRPN_MIDI_IDX_MOD_TYPE = 0x2514;
	
	public static final int NRPN_MIDI_IDX_ATTACK = 0x2521;
	public static final int NRPN_MIDI_IDX_DECAY = 0x2522;
	public static final int NRPN_MIDI_IDX_SUSTAIN = 0x2523;
	public static final int NRPN_MIDI_IDX_RELEASE = 0x2524;
	
	public static final int NRPN_MIDI_IDX_MONOPHONIC = 0x252B;
	public static final int NRPN_MIDI_IDX_MUTE = 0x252C;
	public static final int NRPN_MIDI_IDX_MAINVOL = 0x252D;
	public static final int NRPN_MIDI_IDX_ENV_HOLD = 0x252E;
	public static final int NRPN_MIDI_IDX_ENV_RESET = 0x252F;
	
	public static final int NRPN_MIDI_IDX_BIQUAD_VAL = 0x2530;
	public static final int NRPN_MIDI_IDX_BIQUAD_TYPE = 0x2531;
	public static final int NRPN_MIDI_IDX_TIMEBASE = 0x2532;
	
	public static final int NRPN_MIDI_IDX_SEND_MAIN = 0x2540;
	public static final int NRPN_MIDI_IDX_SEND_A = 0x2541;
	public static final int NRPN_MIDI_IDX_SEND_B = 0x2542;
	public static final int NRPN_MIDI_IDX_SEND_C = 0x2543;
	public static final int NRPN_MIDI_IDX_INITPAN = 0x2544;
	public static final int NRPN_MIDI_IDX_SURROUND_PAN = 0x2545;
	public static final int NRPN_MIDI_IDX_VELOCITY_RANGE = 0x2546;
	
	public static final int NRPN_MIDI_IDX_PITCH_SWEEP = 0x255E;
	public static final int NRPN_MIDI_IDX_LPF = 0x255F;
	
	public static final int NRPN_MIDI_IDX_PRIORITY = 0x25FF;
	
	/* ----- Inner Classes ----- */
	
	private class ShortRegister
	{
		private volatile short value;
		
		public ShortRegister(){value = 0;}
		public short read(){return value;}
		public synchronized void write(short val){value = val;}
	}
	
	/* ----- Instance Variables ----- */
	
	private long startPos; //For rewinding or referencing
	private NinSeqDataSource source;
	
	private ShortRegister[] registers;
	private Random rng;
	
	private Set<NSCommand> include_meta_notes;
	private boolean ignore_jumps;
	private int if_branch_mode;
	private int var_use_mode;
	private int rand_use_mode;
	
	private NinTrack[] tracks;
	private Thread playerThread;
	
	private long tick;
	private volatile boolean end;
	private volatile boolean running;
	
	private int timebase;
	private int ticksPerTick;
	private MidiMessageGenerator mmg;
	private Sequence output;
	private Track[] outTracks;
	
	private List<Exception> errors;
	
	/* ----- Construction ----- */
	
	public NinSeqMidiConverter(NinSeqDataSource src, long start)
	{
		tick = 0;
		source = src;
		startPos = start;
		registers = new ShortRegister[255];
		for(int i = 0; i < 255; i++) registers[i] = new ShortRegister();
		rng = new Random();
		include_meta_notes = new HashSet<NSCommand>();
		ignore_jumps = false;
		if_branch_mode = MIDI_IF_MODE_CHECK;
		var_use_mode = MIDI_VAR_MODE_USEVAR;
		rand_use_mode = MIDI_RANDOM_MODE_USEDEFO;
		tracks = new NinTrack[16];
		mmg = new MidiMessageGenerator();
		errors = new LinkedList<Exception>();
		end = false;
		timebase = NinSeq.TICKS_PER_QNOTE;
		ticksPerTick = 1;
	}
	
	/* ----- Getters ----- */
	
	public boolean jumpsAllowed()
	{
		return !ignore_jumps;
	}
	
	public Sequence getOutput()
	{
		return output;
	}
	
	public int getRandomNumber()
	{
		return rng.nextInt();
	}
	
	public short getVariableValue(int vidx)
	{
		if(vidx > 255 || vidx < 0) return 0;
		return registers[vidx].read();
	}
	
	public void setVariableValue(int vidx, short value)
	{
		if(vidx > 255 || vidx < 0) return;
		registers[vidx].write(value);
	}

	public Collection<Exception> getErrors()
	{
		return this.errors;
	}
	
	/* ----- Setters ----- */
	
	public void setMidiMetaMessageInclusion(NSCommand cmd, boolean b)
	{
		if(b) include_meta_notes.add(cmd);
		else include_meta_notes.remove(cmd);
	}
	
	public void setMidiJumpIgnore(boolean b)
	{
		this.ignore_jumps = b;
	}
	
	public void setMidiRandomMode(int mode)
	{
		this.rand_use_mode = mode;
	}
	
	public void setMidiVarMode(int mode)
	{
		this.var_use_mode = mode;
	}
	
	public void setMidiIfMode(int mode)
	{
		this.if_branch_mode = mode;
	}
	
	/* ----- Complex Command Query ----- */
	/*
	 * Wanna be able to check whether we have prefix commands.
	 * Just ahead of time, in case the caller wants to tweak settings
	 * or not proceed with conversion attempt.
	 */
	
	/* ----- Loop Issues ----- */
	/*So, here's an annoying thing about these Nintendo SEQs.
	 * Although they have "Loop Start" and "Loop End" commands, sometimes
	 * they don't use them for looping.
	 * Instead, they like to insert a "Jump" command near the end of the track
	 * (at the would-be loop end) to the would-be loop start.
	 * 
	 * The converter has to take that into account if it's following jumps and calls, 
	 * lest it attempt to produce an infinite sequence.
	 */
	
	/* ----- Control ----- */
	
	public void rewind()
	{
		if(running) return;
		
		synchronized(this){end = false;}
		timebase = NinSeq.TICKS_PER_QNOTE;
		ticksPerTick = 1;
		
		tick = 0;
		playerThread = null;
		tracks = new NinTrack[16];
		clearOutput();
		mmg = new MidiMessageGenerator();
		errors.clear();
	}
	
	public void stopTrack(int trackIndex)
	{
		//TODO: Does nothing?
	}
	
	public void clearOutput()
	{
		output = null;
		outTracks = null;
	}
	
	public boolean atEnd()
	{
		return end;
	}
	
	private void initializeOutput() throws InvalidMidiDataException
	{
		output = new Sequence(Sequence.PPQ, timebaseScan());
		for(int i = 0; i < 16; i++) output.createTrack();
		outTracks = output.getTracks();
		for(int i = 0; i < 16; i++) outTracks[i].add(new MidiEvent(mmg.genTrackName(String.format("Track %02d", i)), 0));
		
		ticksPerTick = output.getResolution() / timebase;
	}
	
	public void openTrack(int tidx, int addr)
	{
		//Create nintrack object
		MidiConvNinTrack t = new MidiConvNinTrack(source, this, addr, tidx);
		tracks[tidx] = t;
		tracks[tidx].demarcateLoop();
		tracks[tidx].setLoopOn(false);
		t.setMidiIfMode(this.if_branch_mode);
		t.setMidiRandomMode(this.rand_use_mode);
		t.setMidiVarMode(this.var_use_mode);
	}
	
	public void play()
	{
		synchronized(this){running = true;}
		try{if(output == null) initializeOutput();}
		catch(Exception e){e.printStackTrace(); return;}
		Runnable player = new Runnable(){

			@Override
			public void run() {
				while(!end && running)
				{
					//If this is tick 0, initialize track 0!
					if(tick == 0)
					{
						openTrack(0, (int)startPos);
					}
					//Do everything in each track for the tick...
					for(int i = 0; i < 16; i++)
					{
						if(tracks[i] != null && !tracks[i].trackEnded()) markLoopStart(i);
						{
							tracks[i].onTickFlagLoop();
							if(tracks[i].loopStartOnLastTick()) markLoopStart(i);
							if(tracks[i].loopedOnLastTick()) markLoopEnd(i);
							if(tracks[i].trackEnded()) markTrackEnd(i);
						}
						//Check for interrupt...
						if(Thread.currentThread().isInterrupted()) break;
					}
					//Check for end
					end = true;
					for(int i = 0; i < 16; i++)
					{
						if(tracks[i] != null && !tracks[i].trackEnded())
						{
							end = false;
							break;
						}
					}
					//Advance # ticks determined by timebase...
					tick+=ticksPerTick;
				}
				synchronized(this){running = false;} //In case
			}
			
		};
		
		playerThread = new Thread(player);		
		playerThread.start();
	}
	
	public void pause()
	{
		synchronized(this){running = false;}
		if(playerThread != null && playerThread.isAlive()) synchronized(playerThread){playerThread.interrupt();}
	}
	
	public void stop()
	{
		pause();
		rewind();
	}
	
	public void setTempoBPM(int bpm)
	{
		try 
		{
			MidiMessage msg = mmg.genTempoSet(bpm, NinSeq.TICKS_PER_QNOTE);
			addMessage(msg, 0);
		}
		catch (InvalidMidiDataException e) {handleInvalidMidiDataException(e);}
	}
	
	public void setTimebase(int tidx, int value)
	{
		timebase = value;
		try 
		{
			//List<MidiMessage> msgs = mmg.genNRPN(tidx, NRPN_MIDI_IDX_TIMEBASE, value << 7, true);
			//for(MidiMessage msg : msgs) addMessage(msg, 0);
			
			MidiMessage msg = mmg.genMarker("Set timebase to " + value + "ppq");
			addMessage(msg, tidx);
		}
		catch (InvalidMidiDataException e) {handleInvalidMidiDataException(e);}
		
		ticksPerTick = output.getResolution() / timebase;
	}
	
	public void setTrackPriority(int tidx, int pri)
	{
		//TODO
		//The midi converter always executes events in track index order...
		//This may need to change if it causes problems
		try 
		{
			List<MidiMessage> msgs = mmg.genNRPN(tidx, NRPN_MIDI_IDX_PRIORITY, pri, true);
			for(MidiMessage msg : msgs) addMessage(msg, tidx);
		}
		catch (InvalidMidiDataException e) {handleInvalidMidiDataException(e);}
	}
	
	public boolean isPlaying()
	{
		return(playerThread != null && playerThread.isAlive());
	}
	
	/* ----- Variables ----- */
	
	public void printVariable(int vidx)
	{
		if(vidx < 0 || vidx > 255) return;
		System.err.println("Variable Print Command: $" + vidx + " = " + registers[vidx]);
	}
	
	public void addImmediate(int vidx, short imm)
	{
		short v = registers[vidx].read();
		registers[vidx].write((short)(v + imm));
	}
	
	public void subtractImmediate(int vidx, short imm)
	{
		short v = registers[vidx].read();
		registers[vidx].write((short)(v - imm));
	}
	
	public void multiplyImmediate(int vidx, short imm)
	{
		short v = registers[vidx].read();
		registers[vidx].write((short)(v * imm));
	}
	
	public void divideImmediate(int vidx, short imm)
	{
		if(imm == 0) return;
		short v = registers[vidx].read();
		registers[vidx].write((short)(v / imm));
	}
	
	public void modImmediate(int vidx, short imm)
	{
		if(imm == 0) return;
		short v = registers[vidx].read();
		registers[vidx].write((short)(v % imm));
	}
	
	public void andImmediate(int vidx, int imm)
	{
		int v = Short.toUnsignedInt(registers[vidx].read());
		registers[vidx].write((short)(v & imm));
	}
	
	public void orImmediate(int vidx, int imm)
	{
		int v = Short.toUnsignedInt(registers[vidx].read());
		registers[vidx].write((short)(v | imm));
	}
	
	public void xorImmediate(int vidx, int imm)
	{
		int v = Short.toUnsignedInt(registers[vidx].read());
		registers[vidx].write((short)(v ^ imm));
	}
	
	public void notImmediate(int vidx, int imm)
	{
		registers[vidx].write((short)(~imm));
	}
	
	public void shiftImmediate(int vidx, int imm)
	{
		int v = Short.toUnsignedInt(registers[vidx].read());
		if(imm >= 0) registers[vidx].write((short)(v << imm));
		else registers[vidx].write((short)(v >> imm));
	}
	
	/* ----- Conversion ----- */
	
	/* ~~ common ~~ */
	
	private void addMessage(MidiMessage msg, int tidx)
	{
		MidiEvent event = new MidiEvent(msg, tick);
		outTracks[tidx].add(event);
	}
	
	private void handleInvalidMidiDataException(InvalidMidiDataException e)
	{
		e.printStackTrace();
		errors.add(e);
	}
	
	public boolean outputEmpty()
	{
		if(output == null) return true;
		if(outTracks == null) return true;
		for(Track t : outTracks)
		{
			if(t.size() > 0) return false;
		}
		return true;
	}
	
	private int gcd(int a, int b)
	{
		if(b == 0) return a;
		return gcd(b, a % b);
	}
	
	private int lcm(int a, int b)
	{
		return ((a*b) / gcd(a,b));
	}
	
	private int timebaseScan()
	{
		final NSCommand[] time_commands = {NSCommand.PLAY_NOTE, NSCommand.WAIT,
										   NSCommand.PREFIX_TIME, NSCommand.PREFIX_TIME_RANDOM,
										   NSCommand.PREFIX_TIME_VARIABLE};
		
		Set<Integer> usedtimes = new HashSet<Integer>();
		List<Long> addrlist = source.getEventAddresses();
		int tb = NinSeq.TICKS_PER_QNOTE;
		for(Long addr : addrlist)
		{
			NSEvent e = source.getEventAt(addr);
			if(e == null) continue;
			if(e.getCommand() == NSCommand.TIMEBASE) tb = e.getParam1();
			else
			{
				for(NSCommand cs : time_commands)
				{
					if(cs == e.getCommand()) 
					{
						usedtimes.add(tb);
						break;
					}
				}
			}
		}
		
		//If multiple options, LCM
		if(usedtimes.size() > 1)
		{
			int lcm = -1;
			for(Integer i : usedtimes)
			{
				if(lcm == -1) {lcm = i; continue;}
				lcm = lcm(lcm, i);
			}
			return lcm;
		}
		else
		{
			if(!usedtimes.isEmpty())
			{
				for(Integer i : usedtimes) return i;
			}
		}
		
		return NinSeq.TICKS_PER_QNOTE;
	}
	
	/* ~~ play notes ~~ */
	
	public void noteOn(int tidx, int note, int velocity)
	{
		try 
		{
			MidiMessage msg = mmg.genNoteOn(tidx, note, velocity); 
			addMessage(msg, tidx);
		}
		catch (InvalidMidiDataException e) {handleInvalidMidiDataException(e);}
	}
	
	public void noteOff(int tidx, int note)
	{
		try 
		{
			MidiMessage msg = mmg.genNoteOff(tidx, note); 
			addMessage(msg, tidx);
		}
		catch (InvalidMidiDataException e) {handleInvalidMidiDataException(e);}
	}
	
	/* ~~ instrument ~~ */
	
	public void changeTrackBank(int tidx, int bankIndex)
	{
		try 
		{
			List<MidiMessage> msgs = mmg.genBankSelect(tidx, bankIndex);
			for(MidiMessage msg : msgs) addMessage(msg, 0);
		}
		catch (InvalidMidiDataException e) {handleInvalidMidiDataException(e);}
	}
	
	public void changeTrackProgram(int tidx, int bankIndex, int program)
	{
		changeTrackBank(tidx, bankIndex);
		try 
		{
			MidiMessage msg = mmg.genProgramChange(tidx, program);
			addMessage(msg, tidx);
		}
		catch (InvalidMidiDataException e) {handleInvalidMidiDataException(e);}
	}
	
	public void changeTrackProgram(int tidx, int program)
	{
		try 
		{
			MidiMessage msg = mmg.genProgramChange(tidx, program);
			addMessage(msg, tidx);
		}
		catch (InvalidMidiDataException e) {handleInvalidMidiDataException(e);}
	}
	
	/* ~~ volume ~~ */
	
	public void mute(int tidx)
	{
		//We're just gonna add a marker
		try 
		{
			MidiMessage msg = mmg.genMarker("Mute Track " + tidx);
			addMessage(msg, tidx);
		}
		catch (InvalidMidiDataException e) {handleInvalidMidiDataException(e);}
	}
	
	public void unmute(int tidx)
	{
		//We're just gonna add a marker
		try 
		{
			MidiMessage msg = mmg.genMarker("Unmute Track " + tidx);
			addMessage(msg, tidx);
		}
		catch (InvalidMidiDataException e) {handleInvalidMidiDataException(e);}
	}

	public void updateTrackVolume(int tidx, int vol)
	{
		try 
		{
			List<MidiMessage> msgs = mmg.genVolumeChange(tidx, (byte)vol);
			for(MidiMessage msg : msgs) addMessage(msg, tidx);
		}
		catch (InvalidMidiDataException e) {handleInvalidMidiDataException(e);}
	}
	
	public void setMasterVolume(int vol)
	{
		try 
		{
			List<MidiMessage> msgs = mmg.genNRPN(0, NRPN_MIDI_IDX_MAINVOL, vol, true);
			for(MidiMessage msg : msgs) addMessage(msg, 0);
		}
		catch (InvalidMidiDataException e) {handleInvalidMidiDataException(e);}
	}
	
	public void setFXSendLevel(int tidx, int send, int level)
	{
		int nrpn = 0 ;
		switch(send)
		{
		case 0: nrpn = NRPN_MIDI_IDX_SEND_A; break;
		case 1: nrpn = NRPN_MIDI_IDX_SEND_B; break;
		case 2: nrpn = NRPN_MIDI_IDX_SEND_C; break;
		}
		
		try 
		{
			List<MidiMessage> msgs = mmg.genNRPN(tidx, nrpn, level << 7, true);
			for(MidiMessage msg : msgs) addMessage(msg, 0);
		}
		catch (InvalidMidiDataException e) {handleInvalidMidiDataException(e);}
	}
	
	public void setMainSendLevel(int tidx, int level)
	{
		try 
		{
			List<MidiMessage> msgs = mmg.genNRPN(tidx, NRPN_MIDI_IDX_SEND_MAIN, level << 7, true);
			for(MidiMessage msg : msgs) addMessage(msg, 0);
		}
		catch (InvalidMidiDataException e) {handleInvalidMidiDataException(e);}
	}
	
	/* ~~ pan ~~ */
	
	public void updateTrackPan(int tidx, int pan)
	{
		try 
		{
			List<MidiMessage> msgs = mmg.genPanChange(tidx, (byte)pan);
			for(MidiMessage msg : msgs) addMessage(msg, 0);
		}
		catch (InvalidMidiDataException e) {handleInvalidMidiDataException(e);}
	}
	
	public void setTrackInitPan(int tidx, int value)
	{
		try 
		{
			List<MidiMessage> msgs = mmg.genNRPN(0, NRPN_MIDI_IDX_INITPAN, value, true);
			for(MidiMessage msg : msgs) addMessage(msg, 0);
		}
		catch (InvalidMidiDataException e) {handleInvalidMidiDataException(e);}
	}
	
	public void updateTrackSurroundPan(int tidx, int value)
	{
		try 
		{
			List<MidiMessage> msgs = mmg.genNRPN(0, NRPN_MIDI_IDX_SURROUND_PAN, value, true);
			for(MidiMessage msg : msgs) addMessage(msg, 0);
		}
		catch (InvalidMidiDataException e) {handleInvalidMidiDataException(e);}
	}
	
	/* ~~ articulation ~~ */
	
	public void setVelocityRange(int tidx, int min, int max)
	{
		int value = min | (max << 7);
		try 
		{
			List<MidiMessage> msgs = mmg.genNRPN(0, NRPN_MIDI_IDX_VELOCITY_RANGE, value, false);
			for(MidiMessage msg : msgs) addMessage(msg, 0);
		}
		catch (InvalidMidiDataException e) {handleInvalidMidiDataException(e);}
	}
	
	public void updatePitchBendRange(int tidx, int semitones)
	{
		try 
		{
			List<MidiMessage> msgs = mmg.genPitchBendRangeSet(tidx, semitones);
			for(MidiMessage msg : msgs) addMessage(msg, 0);
		}
		catch (InvalidMidiDataException e) {handleInvalidMidiDataException(e);}
	}
	
	public void updatePitchBend(int tidx, int cents)
	{
		try 
		{
			MidiMessage msg = mmg.genPitchBend(tidx, cents, tracks[tidx].getPitchBendRangeSemitones());
			addMessage(msg, tidx);
		}
		catch (InvalidMidiDataException e) {handleInvalidMidiDataException(e);}
	}
	
	public void setExpression(int tidx, int value)
	{
		try 
		{
			List<MidiMessage> msgs = mmg.genExpressionChange(tidx, (byte)value);
			for(MidiMessage msg : msgs) addMessage(msg, 0);
		}
		catch (InvalidMidiDataException e) {handleInvalidMidiDataException(e);}
	}
	
	public void setDamper(int tidx, boolean on)
	{
		try 
		{
			MidiMessage msg = mmg.genDamperChange(tidx, on);
			addMessage(msg, tidx);
		}
		catch (InvalidMidiDataException e) {handleInvalidMidiDataException(e);}
	}
	
	public void setMonophony(int tidx, boolean on)
	{
		int value = 0;
		if(on) value = 1 << 7;
		try 
		{
			List<MidiMessage> msgs = mmg.genNRPN(tidx, NRPN_MIDI_IDX_MONOPHONIC, value, true);
			for(MidiMessage msg : msgs) addMessage(msg, 0);
		}
		catch (InvalidMidiDataException e) {handleInvalidMidiDataException(e);}
	}
	
	public void setPitchSweep(int tidx, int value)
	{
		try 
		{
			List<MidiMessage> msgs = mmg.genNRPN(tidx, NRPN_MIDI_IDX_PITCH_SWEEP, value, false);
			for(MidiMessage msg : msgs) addMessage(msg, 0);
		}
		catch (InvalidMidiDataException e) {handleInvalidMidiDataException(e);}
	}
	
	public void setLPFCutoff(int tidx, int frequency)
	{
		try 
		{
			List<MidiMessage> msgs = mmg.genNRPN(tidx, NRPN_MIDI_IDX_LPF, frequency, false);
			for(MidiMessage msg : msgs) addMessage(msg, 0);
		}
		catch (InvalidMidiDataException e) {handleInvalidMidiDataException(e);}
	}
	
	/* ~~ portamento ~~ */
	
	public void setPortamentoControl(int tidx, int note)
	{
		try 
		{
			MidiMessage msg = mmg.genPortamentoControl(tidx, note);
			addMessage(msg, tidx);
		}
		catch (InvalidMidiDataException e) {handleInvalidMidiDataException(e);}
	}
	
	public void setPortamento(int tidx, boolean on)
	{
		try 
		{
			MidiMessage msg = mmg.genPortamentoSet(tidx, on);
			addMessage(msg, tidx);
		}
		catch (InvalidMidiDataException e) {handleInvalidMidiDataException(e);}
	}
	
	public void setPortamentoTime(int tidx, int time)
	{
		try 
		{
			List<MidiMessage> msgs = mmg.genPortamentoTime(tidx, (byte)time);
			for(MidiMessage msg : msgs) addMessage(msg, 0);
		}
		catch (InvalidMidiDataException e) {handleInvalidMidiDataException(e);}
	}
	
	/* ~~ modulation ~~ */
	
	public void setModulationDepth(int tidx, int value)
	{
		try 
		{
			List<MidiMessage> msgs = mmg.genNRPN(tidx, NRPN_MIDI_IDX_MOD_DEPTH, value << 7, true);
			for(MidiMessage msg : msgs) addMessage(msg, 0);
		}
		catch (InvalidMidiDataException e) {handleInvalidMidiDataException(e);}
	}
	
	public void setModulationSpeed(int tidx, int value)
	{
		try 
		{
			List<MidiMessage> msgs = mmg.genNRPN(tidx, NRPN_MIDI_IDX_MOD_SPEED, value << 7, true);
			for(MidiMessage msg : msgs) addMessage(msg, 0);
		}
		catch (InvalidMidiDataException e) {handleInvalidMidiDataException(e);}
	}
	
	public void setModulationType(int tidx, int value)
	{
		try 
		{
			List<MidiMessage> msgs = mmg.genNRPN(tidx, NRPN_MIDI_IDX_MOD_TYPE, value << 7, true);
			for(MidiMessage msg : msgs) addMessage(msg, 0);
		}
		catch (InvalidMidiDataException e) {handleInvalidMidiDataException(e);}
	}
	
	public void setModulationRange(int tidx, int value)
	{
		try 
		{
			List<MidiMessage> msgs = mmg.genNRPN(tidx, NRPN_MIDI_IDX_MOD_RANGE, value << 7, true);
			for(MidiMessage msg : msgs) addMessage(msg, 0);
		}
		catch (InvalidMidiDataException e) {handleInvalidMidiDataException(e);}
	}
	
	public void setModulationDelay(int tidx, int millis)
	{
		try 
		{
			List<MidiMessage> msgs = mmg.genNRPN(tidx, NRPN_MIDI_IDX_MOD_DELAY, millis, false);
			for(MidiMessage msg : msgs) addMessage(msg, 0);
		}
		catch (InvalidMidiDataException e) {handleInvalidMidiDataException(e);}
	}
	
	/* ~~ envelope ~~ */
	
	public void setAttackOverride(int tidx, int millis)
	{
		try 
		{
			List<MidiMessage> msgs = mmg.genNRPN(tidx, NRPN_MIDI_IDX_ATTACK, millis, false);
			for(MidiMessage msg : msgs) addMessage(msg, 0);
		}
		catch (InvalidMidiDataException e) {handleInvalidMidiDataException(e);}
	}
	
	public void setDecayOverride(int tidx, int millis)
	{
		try 
		{
			List<MidiMessage> msgs = mmg.genNRPN(tidx, NRPN_MIDI_IDX_DECAY, millis, false);
			for(MidiMessage msg : msgs) addMessage(msg, 0);
		}
		catch (InvalidMidiDataException e) {handleInvalidMidiDataException(e);}
	}
	
	public void setSustainOverride(int tidx, int level)
	{
		try 
		{
			List<MidiMessage> msgs = mmg.genNRPN(tidx, NRPN_MIDI_IDX_SUSTAIN, level >> 18, false);
			for(MidiMessage msg : msgs) addMessage(msg, 0);
		}
		catch (InvalidMidiDataException e) {handleInvalidMidiDataException(e);}
	}
	
	public void setReleaseOverride(int tidx, int millis)
	{
		try 
		{
			List<MidiMessage> msgs = mmg.genNRPN(tidx, NRPN_MIDI_IDX_RELEASE, millis, false);
			for(MidiMessage msg : msgs) addMessage(msg, 0);
		}
		catch (InvalidMidiDataException e) {handleInvalidMidiDataException(e);}
	}
	
	public void setEnvelopeHold(int tidx, int millis)
	{
		try 
		{
			List<MidiMessage> msgs = mmg.genNRPN(tidx, NRPN_MIDI_IDX_ENV_HOLD, millis, false);
			for(MidiMessage msg : msgs) addMessage(msg, 0);
		}
		catch (InvalidMidiDataException e) {handleInvalidMidiDataException(e);}
	}
	
	public void envelopeReset(int tidx)
	{
		try 
		{
			List<MidiMessage> msgs = mmg.genNRPN(tidx, NRPN_MIDI_IDX_ENV_RESET, 1<<7, true);
			for(MidiMessage msg : msgs) addMessage(msg, 0);
		}
		catch (InvalidMidiDataException e) {handleInvalidMidiDataException(e);}
	}
	
	/* ~~ looping/jumping ~~ */
	
	public void markLoopStart(int tidx)
	{
		try 
		{
			MidiMessage msg = mmg.genMarker("Loop Start Track " + tidx);
			addMessage(msg, tidx);
		}
		catch (InvalidMidiDataException e) {handleInvalidMidiDataException(e);}
	}
	
	public void markLoopEnd(int tidx)
	{
		try 
		{
			MidiMessage msg = mmg.genMarker("Loop End Track " + tidx);
			addMessage(msg, tidx);
		}
		catch (InvalidMidiDataException e) {handleInvalidMidiDataException(e);}
	}

	public void markTrackEnd(int tidx)
	{
		try 
		{
			MidiMessage msg = mmg.genTrackEnd();
			addMessage(msg, tidx);
		}
		catch (InvalidMidiDataException e) {handleInvalidMidiDataException(e);}
	}
	
	/* ~~ unknown articulators ~~ */
	
	public void setBiquadValue(int tidx, int value)
	{
		try 
		{
			List<MidiMessage> msgs = mmg.genNRPN(tidx, NRPN_MIDI_IDX_BIQUAD_VAL, value << 7, true);
			for(MidiMessage msg : msgs) addMessage(msg, 0);
		}
		catch (InvalidMidiDataException e) {handleInvalidMidiDataException(e);}
	}
	
	public void setBiquadType(int tidx, int value)
	{
		try 
		{
			List<MidiMessage> msgs = mmg.genNRPN(tidx, NRPN_MIDI_IDX_BIQUAD_TYPE, value << 7, true);
			for(MidiMessage msg : msgs) addMessage(msg, 0);
		}
		catch (InvalidMidiDataException e) {handleInvalidMidiDataException(e);}
	}
	
}
