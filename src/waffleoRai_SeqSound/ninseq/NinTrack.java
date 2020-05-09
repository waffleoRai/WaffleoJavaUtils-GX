package waffleoRai_SeqSound.ninseq;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class NinTrack 
{
	/* ----- Constants ----- */
	
	public static final int STACK_NODE_TYPE_RA = 0; //Return address
	public static final int STACK_NODE_TYPE_LS = 1; //Loop start
	
	public static final int COMP_OP_TYPE_EQ = 0;
	public static final int COMP_OP_TYPE_NE = 1;
	public static final int COMP_OP_TYPE_GT = 2;
	public static final int COMP_OP_TYPE_GE = 3;
	public static final int COMP_OP_TYPE_LT = 4;
	public static final int COMP_OP_TYPE_LE = 5;

	/* ----- Inner Classes ----- */
	
	protected static class PendingNote
	{
		public int note;
		public int ticksLeft;
		
		public PendingNote(int n, int t)
		{
			note = n;
			ticksLeft = t;
		}
		
		public int hashCode()
		{
			return note ^ ticksLeft;
		}
		
		public boolean equals(Object o)
		{
			return (this == o);
		}
	}
	
	protected static class StackNode
	{
		public int node_type;
		public int value;
		
		public StackNode(int type, int val)
		{
			node_type = type;
			value = val;
		}
	}
	
	/* ----- Instance Variables ----- */
	
	private int trackIndex;
	private long startAddr;
	private NinSeqDataSource src;
	
	private boolean loopOn;
	private long loopStart;
	private long loopEnd;
	
	private long lastPos;
	private long trackPos;
	private int transpose;
	private boolean trackEnd;
	private boolean noteWait;
	private boolean tie;
	private int bendRange;
	private int bank;
	private int program;
	private Deque<StackNode> stack;
	
	private int pitchBendRaw;
	
	private boolean conditional;
	private NinSeqPlayer player;
	
	private Map<Integer, PendingNote> pendingNotes;
	private long remainingWait;
	
	private List<Integer> offNotes; //What the hell is this for???
	private List<NSEvent> lastEvents;
	
	private boolean iLooped;
	private boolean loopStartHit;
	private int loopsRemaining;
	
	private boolean internal_mute; //Prevents from sending noteon signals to player
	private boolean monophony;
	
	/* ----- Construction ----- */
	
	public NinTrack(NinSeqDataSource data, NinSeqPlayer seqPlayer, long startAddr, int index)
	{
		trackIndex = index;
		src = data;
		this.startAddr = startAddr;
		player = seqPlayer;
		loopOn = true;
		
		stack = new LinkedList<StackNode>();
		pendingNotes = new HashMap<Integer, PendingNote>();
		offNotes = new LinkedList<Integer>();
		lastEvents = new LinkedList<NSEvent>();
		resetDefaults();
		
		loopStart = -1L;
		loopEnd = -1L;
		//System.err.println("Track created " + index + " Start Address: 0x" + Long.toHexString(trackPos));
	}
	
	private void resetDefaults()
	{
		loopsRemaining = -2;
		trackPos = startAddr;
		lastPos = -1;
		
		transpose = 0;
		trackEnd = false;
		noteWait = false;
		tie = false;
		bendRange = 2;
		bank = 0;
		program = 0;
		stack.clear();
		
		conditional = false;
		pendingNotes.clear();
		remainingWait = 0;
		offNotes.clear();
		lastEvents.clear();
		iLooped = false;
		loopStartHit = false;
	}
	
	/* ----- Getters ----- */
	
	public int getTrackIndex()
	{
		return this.trackIndex;
	}
	
	public boolean loopSet()
	{
		return loopOn;
	}
	
	public boolean loopedOnLastTick()
	{
		return iLooped;
	}
	
	public boolean loopStartOnLastTick()
	{
		return loopStartHit;
	}
	
	public int getBankIndex()
	{
		return bank;
	}
	
	public int getProgramIndex()
	{
		return program;
	}
	
	public List<NSEvent> getEventsFromLastTick()
	{
		return this.lastEvents;
	}
	
	protected NinSeqPlayer getPlayer()
	{
		return player;
	}
	
	public boolean getConditionalFlag()
	{
		return conditional;
	}
	
	public int getPitchBendRangeSemitones()
	{
		return this.bendRange;
	}
	
	public short getPitchWheel(int cents){
		double v = (double)cents/((double)this.bendRange * 100.0);
		v = v * (double)0x7FFF;
		return (short)Math.round(v);
	}
	
	/* ----- Looping ----- */
	
	public void setLoopOn(boolean on)
	{
		loopOn = on;
	}
	
	public void demarcateLoop()
	{
		//Scans for LoopStart/LoopEnd pairs
		//Or TrackEnd commands that are unreachable due to a jump right before.
		//For some reason, the latter is more common.
		
		//Generate a map of jumps while scanning for loop commands
		rewind();
		Map<Long, Integer> jumpMap = new HashMap<Long, Integer>();
		Map<Long, Integer> cjumpMap = new HashMap<Long, Integer>();
		long lastpos = trackPos;
		//System.err.println("Calling nextEvent: demarcateLoop (1)");
		NSEvent e = nextEvent();
		long maxAddr = src.getMaxAddress();
		while((e != null) && (e.getCommand() != NSCommand.TRACK_END) && (trackPos < maxAddr))
		{
			NSCommand cmd = e.getCommand();
			switch(cmd)
			{
			case LOOP_START:
				loopStart = lastpos;
				break;
			case LOOP_END:
				loopEnd = lastpos;
				break;
			case JUMP_WII:
			case JUMP_DS:
				jumpMap.put(lastpos, e.getParam1());
				break;
			case PREFIX_IF:
				NSEvent s = ((PrefixEvent)e).getSubEvent();
				if(s.getCommand() == NSCommand.JUMP_WII || s.getCommand() == NSCommand.JUMP_DS) cjumpMap.put(lastpos, s.getParam1());
				break;
			default: break;
			}
			
			lastpos = trackPos;
			//System.err.println("Calling nextEvent: demarcateLoop (2)");
			e = nextEvent();
		}
		
		if(jumpMap.isEmpty()) return;
		
		//See if loops were set
		if(loopStart == -1 || loopEnd == -1)
		{
			//Look for jump loops
			long endpos = trackPos;
			
			//Sort jump keys
			ArrayList<Long> jumpKeys = new ArrayList<Long>(jumpMap.size() + 1);
			jumpKeys.addAll(jumpMap.keySet());
			Collections.sort(jumpKeys);
			
			//Start from the back and see what the last jump is
			int sz = jumpKeys.size();
			for(int i = sz-1; i >= 0; i--)
			{
				Long ekey = jumpKeys.get(i);
				Integer target = jumpMap.get(ekey);
				//Is it before?
				if(target < ekey)
				{
					//Jumps to an earlier point.
					//See if there is any way to get to any instructions
					//	at or between this jump and the track end.
					boolean match = false;
					for(Integer t : jumpMap.values())
					{
						if (t > ekey && t <= endpos) {match = true; break;}
					}
					if(match) continue;
					//Assume not.
					//Mark as loop and return.
					loopStart = Integer.toUnsignedLong(target);
					loopEnd = ekey;
					return;
				}
			}
		}
		//trackPos = 0;
	}
	
	/* ----- Play ----- */
	
	private void clearLastTickState()
	{
		offNotes.clear();
		lastEvents.clear();
		iLooped = false;
		loopStartHit = false;
	}
	
	public boolean trackEnded()
	{
		return trackEnd;
	}
	
	public void rewind()
	{
		player.stopTrack(trackIndex);
		resetDefaults();
	}
	
	public NSEvent nextEvent()
	{
		//System.err.println("Track " + trackIndex + " Address: 0x" + Long.toHexString(trackPos));
		NSEvent event = src.getEventAt(trackPos);
		lastPos = trackPos;
		trackPos += event.getSerialSize();
		//System.err.println("Next event: " + event.toString());
		return event;
	}
	
	public void onTick()
	{
		if(trackEnd) return;
		//Refresh queues (they only mark what happened in the last tick)
		clearLastTickState();
		
		--remainingWait;
		while(remainingWait <= 0 && !trackEnd)
		{
			//Execute events until the next wait is set!
			//System.err.println("Calling nextEvent: onTick");
			NSEvent e = nextEvent();
			e.execute(this);
			lastEvents.add(e);
		}
	
		//Check for notes turning off
		//This part also runs during wait periods
		checkForNotesOff();
	}
	
	public void onTickFlagLoop()
	{
		//System.err.println("Tick!");
		if(trackEnd) return;
		//Refresh queues (they only mark what happened in the last tick)
		clearLastTickState();
		
		--remainingWait;
		while(remainingWait <= 0 && !trackEnd)
		{
			//Execute events until the next wait is set!
			//System.err.println("Track " + this.trackIndex + " address: 0x" + Long.toHexString(trackPos));
			//System.err.println("Calling nextEvent: onTickFlagLoop");
			NSEvent e = nextEvent();
			e.execute(this);
			lastEvents.add(e);
			if(lastPos == loopStart) loopStartHit = true;
			if(lastPos == loopEnd) iLooped = true;
		}
	
		//Check for notes turning off
		//This part also runs during wait periods
		checkForNotesOff();
	}
	
	private void allNotesOff(){
		for(PendingNote note : pendingNotes.values())
		{
			player.noteOff(trackIndex, note.note);
			//offNotes.add(note.note);		
		}
		pendingNotes.clear();
	}
	
	private void checkForNotesOff()
	{
		//List<PendingNote> rset = new LinkedList<PendingNote>();
		for(PendingNote note : pendingNotes.values())
		{
			if(--note.ticksLeft <= 0)
			{
				//Issue a note off command
				player.noteOff(trackIndex, note.note);
				offNotes.add(note.note);
			}		
		}
		//for(PendingNote pn : rset) pendingNotes.remove(pn.note);
		for(Integer pn : offNotes) pendingNotes.remove(pn);
		offNotes.clear();
	}
	
	private int calculatePitchBendCents(int rawpb)
	{
		if(rawpb == 0) return 0;
		int signed = (int)(byte)rawpb; //sign-extend
		double ratio = 0.0;
		if(signed < 0) ratio = (double)signed/128.0;
		else ratio = (double)signed/127.0;
		double rangecents = (double)bendRange * 100.0;
		return (int)Math.round(ratio * rangecents);
	}
	
	public void setInternalMute(boolean b){
		internal_mute = b;
	}
	
	public boolean getInternalMute(){
		return internal_mute;
	}
	
	/* ----- Events ----- */
	
	/* ~~ note ~~ */
	
	protected void playNote(int rawnote, int vel, int ticks)
	{
		//System.err.println("Track: play note -- " + rawnote);
		int playnote = rawnote + transpose;
		if(noteWait) remainingWait = ticks;
		if(tie)
		{
			PendingNote anote = pendingNotes.get(playnote);
			if(anote != null) anote.ticksLeft += ticks;
			else
			{
				//If monophony is on, stop old note(s) and play this one instead
				if(monophony)allNotesOff();
				pendingNotes.put(playnote, new PendingNote(playnote, ticks+1));
				if(!internal_mute) player.noteOn(trackIndex, playnote, vel);	
			}
		}
		else
		{
			//First see if note is already playing. If it is, terminate and play new note.
			//(This has been an issue with seqs that terminate notes ON the same tick as the 
			// subsequent same note is to start)
			//Pokemon is VERY bad about this.
			PendingNote pn = pendingNotes.remove(playnote);
			if(pn != null){
				player.noteOff(trackIndex, pn.note);
			}
			
			//If monophony is on, stop old note(s) and play this one instead
			if(monophony)allNotesOff();
			pendingNotes.put(playnote, new PendingNote(playnote, ticks+1));
			if(!internal_mute) player.noteOn(trackIndex, playnote, vel);	
		}
	}
	
	/* ~~ standard ~~ */
	
	protected void signalLoopStart(int loopCount, long address)
	{
		//Note the address?
		//Special value for loop count before setting?
		if(loopStart == -1) loopStart = address;
		if(loopsRemaining < -1) loopsRemaining = loopCount;
		else loopCount--;
		loopStartHit = true;
	}
	
	protected void setWait(int ticks)
	{
		remainingWait = ticks;
	}
	
	protected void changeProgram(int rawval)
	{
		int pidx = rawval & 0x7F;
		int bidx = rawval >>> 7;
		program = pidx;
		if (bidx != bank)
		{
			bank = bidx;
			player.changeTrackProgram(trackIndex, bank, pidx);
		}
		else player.changeTrackProgram(trackIndex, pidx);
	}
	
	protected void setPan(int pan)
	{
		//this.pan = pan;
		player.updateTrackPan(trackIndex, pan);
	}
	
	protected void setTrackVolume(int vol)
	{
		player.updateTrackVolume(trackIndex, vol);
	}
	
	protected void setMasterVolume(int vol)
	{
		player.setMasterVolume(vol);
	}
	
	protected void setTransposition(int semitones)
	{
		this.transpose = semitones;
	}

	protected void setPitchBend(int rawpb)
	{
		this.pitchBendRaw = rawpb;
		//Calculate the pitchbend
		player.updatePitchBend(trackIndex, calculatePitchBendCents(rawpb));
	}
	
	protected void setPitchBendRange(int semitones)
	{
		this.bendRange = semitones;
		player.updatePitchBend(trackIndex, calculatePitchBendCents(pitchBendRaw));
	}
	
	protected void setTrackPriority(int pri)
	{
		player.setTrackPriority(trackIndex, pri);
	}
	
	protected void setNoteWait(boolean b)
	{
		noteWait = b;
	}
	
	protected void setTie(boolean b)
	{
		tie = b;
	}

	protected void setPortamentoControl(int value)
	{
		player.setPortamentoControl(trackIndex, value);
	}
	
	protected void setPortamentoOn(boolean b)
	{
		player.setPortamento(trackIndex, b);
	}
	
	protected void setPortamentoTime(int value)
	{
		//TODO No idea how this translates to millis
		player.setPortamentoTime(trackIndex, value);
	}

	protected void setModulationDepth(int value)
	{
		//TODO Dunno how it scales
		player.setModulationDepth(trackIndex, value);
	}
	
	protected void setModulationSpeed(int value)
	{
		//TODO Dunno how it scales
		player.setModulationSpeed(trackIndex, value);
	}
	
	protected void setModulationType(int type)
	{
		player.setModulationType(trackIndex, type);
	}
	
	protected void setModulationRange(int value)
	{
		//TODO Dunno how it scales
		player.setModulationRange(trackIndex, value);
	}
	
	protected void setModulationDelay(int value)
	{
		//TODO Dunno how it scales
		player.setModulationDelay(trackIndex, value);
	}

	protected void setAttack(int raw)
	{
		int a = NinSeqADSR.scaleAttackToMillis(raw);
		player.setAttackOverride(trackIndex, a);
	}
	
	protected void setHold(int raw)
	{
		int h = NinSeqADSR.scaleHoldToMillis(raw);
		player.setEnvelopeHold(trackIndex, h);
	}
	
	protected void setDecay(int raw)
	{
		int d = NinSeqADSR.scaleDecayToMillis(raw);
		player.setDecayOverride(trackIndex, d);
	}
	
	protected void setSustain(int raw)
	{
		int slvl = NinSeqADSR.scaleSustain(raw);
		player.setSustainOverride(trackIndex, slvl);
	}
	
	protected void setRelease(int raw)
	{
		int r = NinSeqADSR.scaleReleaseToMillis(raw);
		player.setReleaseOverride(trackIndex, r);
	}
	
	protected void setExpression(int value)
	{
		player.setExpression(trackIndex, value);
	}
	
	protected void setSurroundPan(int value)
	{
		player.updateTrackSurroundPan(trackIndex, value);
	}
	
	protected void setLPFCutoff(int rawvalue)
	{
		//TODO How to scale?
		player.setLPFCutoff(trackIndex, rawvalue);
	}
	
	protected void setFXSendLevel(int send, int value)
	{
		player.setFXSendLevel(trackIndex, send, value);
	}

	protected void setMainSendLevel(int value)
	{
		player.setMainSendLevel(trackIndex, value);
	}

	protected void setInitPan(int value)
	{
		player.setTrackInitPan(trackIndex, value);
	}

	protected void setMute(boolean b)
	{
		if(b)player.mute(trackIndex);
		else player.unmute(trackIndex);
	}
	
	protected void setDamper(boolean b)
	{
		player.setDamper(trackIndex, b);
	}
	
	protected void setTimebase(int value)
	{
		//TODO: No idea what this does!
		player.setTimebase(trackIndex, value);
	}
	
	protected void setMonophony(boolean b)
	{
		player.setMonophony(trackIndex, b); //This allows it to ask permission from player
	}

	public void setInternalMonophony(boolean b){
		monophony = b;
	}
	
	protected void setVelocityRange(int value)
	{
		//TODO: Dunno how it scales!!
		//TODO This should NOT be delegated to player!
		//player.setVelocityRange(trackIndex, 0, value);
	}
	
	protected void setBiquadType(int value)
	{
		//TODO: Scale? Meaning?
		player.setBiquadType(trackIndex, value);
	}
	
	protected void setBiquadValue(int value)
	{
		//TODO: Scale? Meaning?
		player.setBiquadValue(trackIndex, value);
	}
	
	protected void setTempo(int bpm)
	{
		player.setTempoBPM(bpm);
	}
	
	protected void setPitchSweep(int value)
	{
		//TODO: Scale? Meaning?
		player.setPitchSweep(trackIndex, value);
	}
	
	protected void printVariable(int vidx)
	{
		player.printVariable(vidx);
	}
	
	/* ~~ control ~~ */
	
	protected void call(int ja)
	{
		//System.err.println("Track " + this.trackIndex + " calling 0x" + ja);
		if(!player.jumpsAllowed()) return;
		//stack.push(new StackNode(STACK_NODE_TYPE_RA,(int)lastPos));
		stack.push(new StackNode(STACK_NODE_TYPE_RA,(int)trackPos));
		trackPos = Integer.toUnsignedLong(ja);
	}
	
	protected void jump(int ja)
	{
		//System.err.println("Track " + this.trackIndex + " jumping to 0x" + ja);
		if(!player.jumpsAllowed()) return;
		//Check if it's a loop point...
		if(lastPos == loopEnd) iLooped = true;
		if(!loopOn && iLooped) return;
		trackPos = Integer.toUnsignedLong(ja);
	}
	
	protected void resetEnvelope()
	{
		//TODO
		player.envelopeReset(this.trackIndex);
	}
	
	protected void executeLoopEnd()
	{
		if(!loopOn || loopStart == -1 || loopsRemaining == 0) return;
		trackPos = loopStart;
		if(loopsRemaining > 0) loopsRemaining--;
	}
	
	protected void openTrack(int idx, int addr)
	{
		player.openTrack(idx, addr);
	}
	
	protected void returnToCallAddr()
	{
		//System.err.println("Track " + this.trackIndex + " returning.");
		if(!player.jumpsAllowed()) return;
		StackNode node = stack.pop();
		if(node != null) trackPos = node.value;
	}
	
	protected void signalTrackEnd()
	{
		trackEnd = true;
	}
	
	/* ~~ prefix ~~ */
	
	protected void executeWithRandom(NSEvent event)
	{
		int r = player.getRandomNumber();
		if(event.hasSecondParameter()) event.setParam2(r);
		else event.setParam1(r);
		event.execute(this);
	}
	
	protected void executeWithVariable(int vidx, NSEvent event)
	{
		int v = Short.toUnsignedInt(player.getVariableValue(vidx));
		if(event.hasSecondParameter()) event.setParam2(v);
		else event.setParam1(v);
		event.execute(this);
	}
	
	protected void executeIf(NSEvent event)
	{
		if(conditional) event.execute(this);
	}
	
	/* ~~ state ~~ */
	
	protected void setIfEqual(int vidx, short imm){conditional = (player.getVariableValue(vidx) == imm);}
	protected void setIfNotEqual(int vidx, short imm){conditional = (player.getVariableValue(vidx) != imm);}
	protected void setIfGreaterOrEqual(int vidx, short imm){conditional = (player.getVariableValue(vidx) >= imm);}
	protected void setIfGreaterThan(int vidx, short imm){conditional = (player.getVariableValue(vidx) > imm);}
	protected void setIfLessThan(int vidx, short imm){conditional = (player.getVariableValue(vidx) < imm);}
	protected void setIfLessOrEqual(int vidx, short imm){conditional = (player.getVariableValue(vidx) <= imm);}
	
	public void addImmediate(int vidx, short imm){player.addImmediate(vidx, imm);}
	public void subtractImmediate(int vidx, short imm){player.subtractImmediate(vidx, imm);}
	public void multiplyImmediate(int vidx, short imm){player.multiplyImmediate(vidx, imm);}
	public void divideImmediate(int vidx, short imm){player.divideImmediate(vidx, imm);}
	public void modImmediate(int vidx, short imm){player.modImmediate(vidx, imm);}
	public void andImmediate(int vidx, int imm){player.andImmediate(vidx, imm);}
	public void orImmediate(int vidx, int imm){player.orImmediate(vidx, imm);}
	public void xorImmediate(int vidx, int imm){player.xorImmediate(vidx, imm);}
	public void notImmediate(int vidx, int imm){player.notImmediate(vidx, imm);}
	public void shiftImmediate(int vidx, int imm){player.shiftImmediate(vidx, imm);}
	
	public void setPlayerVariable(int vidx, short value){player.setVariableValue(vidx, value);}
	public void setPlayerVariableToRandom(int vidx){player.setVariableValue(vidx, (short)player.getRandomNumber());}
	
}
