package waffleoRai_SeqSound.misc;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

import waffleoRai_SeqSound.MIDI;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;

public class SMD 
{
	
	public static final String MAGIC = "smdl";
	public static final String SONG_MAGIC = "song";
	public static final String TRACK_MAGIC = "trk ";

	private SMDTrack[] tracks;
	
	private int FileSize; //TOTAL file size
	private String FileName;
	
	private int TrackNum;
	private int ChannelNum;
	private int InstGroup;
	private int TicksPerQNote;
	
	public SMD(FileBuffer src, long stOff) throws UnsupportedFileTypeException
	{
		this.readSMD(src, stOff);
	}
	
	/*--- Getters ---*/
	
	public int getFileSize()
	{
		return this.FileSize;
	}
	
	public String getInternalName()
	{
		return this.FileName;
	}
	
	public int getNumberTracks()
	{
		return this.TrackNum;
	}
	
	public int getNumberChannels()
	{
		return this.ChannelNum;
	}
	
	public int getInstrumentGroup()
	{
		return this.InstGroup;
	}
	
	public int getTicksPerQuartNote()
	{
		return this.TicksPerQNote;
	}
	
	/*--- Setters ---*/
	
	/*--- Internal Structures ---*/
	
	private static class SMDTrack
	{
		
		private List<SMDEvent> eventList;
		private int trackID;
		private int chanID;
		
		public SMDTrack()
		{
			this.eventList = new LinkedList<SMDEvent>();
		}
		
		public void addEvent(SMDEvent e)
		{
			eventList.add(e);
		}
		
		public SMDEvent getEvent(int i)
		{
			return eventList.get(i);
		}
		
		public void setTrackID(int tID)
		{
			this.trackID = tID;
		}
		
		public void setChannelID(int cID)
		{
			this.chanID = cID;
		}
		
		public int getNumberEvents()
		{
			return this.eventList.size();
		}
		
	}
	
	private static SMDEvent readEvent(FileBuffer src, long eOff)
	{
		if (eOff < 0 || eOff >= src.getFileSize()) return null;
		int op = Byte.toUnsignedInt(src.getByte(eOff));
		if (op <= 0x7F)
		{
			byte keyFlags = src.getByte(eOff + 1);
			NoteEvent n = new NoteEvent((byte)op, keyFlags);
			int nBytes = n.getByteLength();
			if (nBytes < 3) return n;
			if (nBytes == 3)
			{
				int len = Byte.toUnsignedInt(src.getByte(eOff + 2));
				n.setLength(len);
				return n;
			}
			else if(nBytes == 4)
			{
				src.setEndian(true);
				int len = Short.toUnsignedInt(src.shortFromFile(eOff + 2));
				n.setLength(len);
				src.setEndian(false);
				return n;
			}
			else if(nBytes == 5)
			{
				src.setEndian(true);
				int len = src.shortishFromFile(eOff + 2);
				n.setLength(len);
				src.setEndian(false);
				return n;
			}
			return null;
		}
		else if (op < 0x90)
		{
			DeltaTimeEvent d = new DeltaTimeEvent((byte)op);
			return d;
		}
		else
		{
			MiscType mt = MiscType.getType((byte)op);
			//System.out.println("op = 0x" + Integer.toHexString(op));
			if (mt == null) return null;
			MiscEvent m = new MiscEvent(mt);
			for (int i = 0; i < mt.getParameterCount(); i++)
			{
				int p = Byte.toUnsignedInt(src.getByte(eOff + i + 1));
				m.setParameter(i, p);
			}
			return m;
		}

	}
	
	private static interface SMDEvent
	{
		public int getByteLength();
		public MiscType getType();
		public int getChannel();
		public void setChannel(int c);
	}
	
	private static class NoteEvent implements SMDEvent
	{
		private int velocity;
		
		private int lBytes;
		private int length;
		
		private int note; //(0 - F, not yet converted to midi scale)
		private int octaveChange; //Signed value is how many octaves to move up or down if any
		
		private int channel;
		
		public NoteEvent(byte velocity, byte keyFlags)
		{
			this.velocity = Byte.toUnsignedInt(velocity);
			this.readLenFlags(keyFlags);
			this.readOctFlag(keyFlags);
			this.readNote(keyFlags);
		}
		
		private void readLenFlags(byte keyFlags)
		{
			int lb = (Byte.toUnsignedInt(keyFlags) >> 6) & 0x3;
			this.lBytes = lb;
			this.length = -1;
		}
		
		private void readOctFlag(byte keyFlags)
		{
			int oc = (Byte.toUnsignedInt(keyFlags) >> 4) & 0x3;
			switch(oc)
			{
			case 0: this.octaveChange = -2; break;
			case 1: this.octaveChange = -1; break;
			case 2: this.octaveChange = 0; break;
			case 3: this.octaveChange = 1; break;
			}
		}
		
		private void readNote(byte keyFlags)
		{
			this.note = Byte.toUnsignedInt(keyFlags) & 0xF;
		}
		
		public int getByteLength()
		{
			return 2 + lBytes;
		}
		
		public void setLength(int ticks)
		{
			this.length = ticks;
		}
		
		public MiscType getType()
		{
			return MiscType.NA_NOTE;
		}
		
		public int getVelocity()
		{
			return velocity;
		}
		
		public int getLength()
		{
			return length;
		}
		
		public int getNote()
		{
			return note;
		}
		
		public int getOctaveChange()
		{
			return this.octaveChange;
		}
		
		public int getChannel()
		{
			return this.channel;
		}
		
		public void setChannel(int c)
		{
			this.channel = c;
		}
		
	}
	
	private static class DeltaTimeEvent implements SMDEvent
	{
		private int delayTime;
		private int channel;
		
		public DeltaTimeEvent(byte opcode)
		{
			this.interpretDelayTime(opcode);
		}
		
		private void interpretDelayTime(byte opcode)
		{
			int opi = Byte.toUnsignedInt(opcode);
			switch(opi)
			{
			case 0x80: delayTime = 96; return;
			case 0x81: delayTime = 72; return;
			case 0x82: delayTime = 64; return;
			case 0x83: delayTime = 48; return;
			case 0x84: delayTime = 36; return;
			case 0x85: delayTime = 32; return;
			case 0x86: delayTime = 24; return;
			case 0x87: delayTime = 18; return;
			case 0x88: delayTime = 16; return;
			case 0x89: delayTime = 12; return;
			case 0x8A: delayTime = 9; return;
			case 0x8B: delayTime = 8; return;
			case 0x8C: delayTime = 6; return;
			case 0x8D: delayTime = 4; return;
			case 0x8E: delayTime = 3; return;
			case 0x8F: delayTime = 2; return;
			default: delayTime = -1; return;
			}
		}
		
		public int getByteLength()
		{
			return 1;
		}
		
		public int getDelayTime()
		{
			return this.delayTime;
		}
	
		public MiscType getType()
		{
			return MiscType.NA_DELTATIME;
		}
	
		public int getChannel()
		{
			return this.channel;
		}
		
		public void setChannel(int c)
		{
			this.channel = c;
		}
		
	}
	
	private static enum MiscType
	{
		WAIT_AGAIN(0x90, 0, "WAIT_AGAIN"),
		WAIT_ADD(0x91, 1, "WAIT_ADD"),
		WAIT_1BYTE(0x92, 1, "WAIT_1BYTE"),
		WAIT_2BYTE(0x93, 2, "WAIT_2BYTE"), //LE
		TRACK_END(0x98, 0, "TRACK_END"),
		LOOP_POINT(0x99, 0, "LOOP_POINT"),
		SET_OCTAVE(0xA0, 1, "SET_OCTAVE"),
		SET_TEMPO(0xA4, 1, "SET_TEMPO"),
		SET_SAMPLE(0xAC, 1, "SET_SAMPLE"),
		SET_MODU(0xBE, 1, "SET_MODU"),
		SET_BEND(0xD7, 2, "SET_BEND"),
		SET_VOLUME(0xE0, 1, "SET_VOLUME"),
		SET_XPRESS(0xE3, 1, "SET_EXPRESS"),
		SET_PAN(0xE8, 1, "SET_PAN"),
		NA_NOTE(0x00, -1, "PLAY_NOTE"),
		NA_DELTATIME(0x80, 1, "DELTATIME"),
		UNK_9C(0x9C, 1, "UNK_9C"),
		UNK_9D(0x9D, 0, "UNK_9D"),
		UNK_A8(0xA8, 2, "UNK_A8"),
		UNK_A9(0xA9, 1, "UNK_A9"),
		UNK_AA(0xAA, 1, "UNK_AA"),
		UNK_B2(0xB2, 1, "UNK_B2"),
		UNK_B4(0xB4, 2, "UNK_B4"),
		UNK_B5(0xB5, 1, "UNK_B5"),
		UNK_BF(0xBF, 1, "UNK_BF"),
		UNK_C0(0xC0, 0, "UNK_C0"),
		UNK_D0(0xD0, 1, "UNK_D0"),
		UNK_D1(0xD1, 1, "UNK_D1"),
		UNK_D2(0xD2, 1, "UNK_D2"),
		UNK_D4(0xD4, 3, "UNK_D4"),
		UNK_D6(0xD6, 2, "UNK_D6"),
		UNK_DB(0xDB, 1, "UNK_DB"),
		UNK_DC(0xDC, 5, "UNK_DC"),
		UNK_E2(0xE2, 3, "UNK_E2"),
		UNK_EA(0xEA, 3, "UNK_EA"),
		UNK_F6(0xF6, 1, "UNK_F6");
		
		private int op;
		private int numParam;
		private String sRep;
		
		private MiscType(int opcode, int nParam, String s)
		{
			this.op = opcode;
			this.numParam = nParam;
			this.sRep = s;
		}
		
		public int getOpCode()
		{
			return this.op;
		}
		
		public int getParameterCount()
		{
			return this.numParam;
		}
		
		public static MiscType getType(byte opcode)
		{
			int oci = Byte.toUnsignedInt(opcode);
			switch(oci)
			{
			case(0x90): return WAIT_AGAIN;
			case(0x91): return WAIT_ADD;
			case(0x92): return WAIT_1BYTE;
			case(0x93): return WAIT_2BYTE;
			case(0x98): return TRACK_END;
			case(0x99): return LOOP_POINT;
			case(0xA0): return SET_OCTAVE;
			case(0xA4): return SET_TEMPO;
			case(0xAC): return SET_SAMPLE;
			case(0xBE): return SET_MODU;
			case(0xD7): return SET_BEND;
			case(0xE0): return SET_VOLUME;
			case(0xE3): return SET_XPRESS;
			case(0xE8): return SET_PAN;
			case(0x9C): return UNK_9C;
			case(0x9D): return UNK_9D;
			case(0xA8): return UNK_A8;
			case(0xA9): return UNK_A9;
			case(0xAA): return UNK_AA;
			case(0xB2): return UNK_B2;
			case(0xB4): return UNK_B4;
			case(0xB5): return UNK_B5;
			case(0xBF): return UNK_BF;
			case(0xC0): return UNK_C0;
			case(0xD0): return UNK_D0;
			case(0xD1): return UNK_D1;
			case(0xD2): return UNK_D2;
			case(0xD4): return UNK_D4;
			case(0xD6): return UNK_D6;
			case(0xDB): return UNK_DB;
			case(0xDC): return UNK_DC;
			case(0xE2): return UNK_E2;
			case(0xEA): return UNK_EA;
			case(0xF6): return UNK_F6;
			default: return null;
			}
		}
		
		public String toString()
		{
			return sRep;
		}
		
	}
	
	private static class MiscEvent implements SMDEvent
	{
		private MiscType type;
		private int[] parameters;
		private int channel;
		
		public MiscEvent(MiscType t)
		{
			type = t;
			parameters = new int[type.getParameterCount()];
		}
		
		public int getByteLength()
		{
			if (type != null)
			{
				return 1 + type.getParameterCount();
			}
			return -1;
		}
		
		public MiscType getType()
		{
			return this.type;
		}
		
		public int getParameter(int i)
		{
			if (i < 0) return -1;
			if (i >= parameters.length) return -1;
			return parameters[i];
		}
		
		public void setParameter(int i, int p)
		{
			if (i < 0) return;
			if (i >= parameters.length) return;
			parameters[i] = p;
		}
		
		public int getChannel()
		{
			return this.channel;
		}
		
		public void setChannel(int c)
		{
			this.channel = c;
		}
		
	}
	
	/*--- Instruction Interpretation ---*/
	
	private int getMidiNote(int relNote, int octave)
	{
		int mNote = octave * 12;
		mNote += relNote;
		//Right now just clips values. Probably should have a more elegant way to handle.
		if (mNote < 0) return 0;
		if (mNote > 127) return 127;
		return mNote;
	}
	
	private boolean readNote(NoteEvent n, Track t, CState state)
	{
		if (n == null) return false;
		if (t == null) return false;
		if (state == null) state = new CState();
		int relNote = n.getNote();
		int relOct = n.getOctaveChange();
		int velocity = n.getVelocity();
		int nLength = n.getLength();
		if (nLength < 0) nLength = state.lastNoteLen;
		
		int octave = state.octCurrent + relOct;
		
		int midiNote = getMidiNote(relNote, octave);
		int channel = n.getChannel();
		//System.out.println("readNote | octave = " + octave + ", relNote = " + relNote + ", midiNote = " + midiNote);
		
		int statOn = (0x90) | (channel & 0xF);
		int statOff = (0x80) | (channel & 0xF);
		
		long offTime = state.tickCurrent + Integer.toUnsignedLong(nLength);
		try 
		{
			MidiEvent mevOn = new MidiEvent(new ShortMessage(statOn, midiNote, velocity), state.tickCurrent);
			MidiEvent mevOff = new MidiEvent(new ShortMessage(statOff, midiNote, velocity), offTime);
			t.add(mevOn);
			t.add(mevOff);
		} 
		catch (InvalidMidiDataException e) 
		{
			e.printStackTrace();
			return false;
		}
		
		state.octCurrent = octave;
		state.lastNoteLen = nLength;
		//state.tickCurrent = offTime; //Reinstate if need. Otherwise nothing can occur during note!
		
		return true;
	}
	
	private boolean readDeltaTime(DeltaTimeEvent d, CState state)
	{
		if (d == null) return false;
		if (state == null) state = new CState();
		state.tickCurrent += Integer.toUnsignedLong(d.getDelayTime());
		return true;
	}
	
	private boolean readWaitEvent(MiscEvent e, CState state)
	{
		if (e == null) return false;
		if (state == null) state = new CState();
		int waitTime = 0;
		switch(e.getType())
		{
		case WAIT_1BYTE:
			waitTime = e.getParameter(0);
			break;
		case WAIT_2BYTE:
			//Little-Endian order
			waitTime = e.getParameter(1);
			waitTime = waitTime << 8;
			waitTime = waitTime | (e.getParameter(0) & 0xFF);
			break;
		case WAIT_ADD:
			waitTime = state.lastWaitTime + e.getParameter(0);
			break;
		case WAIT_AGAIN:
			waitTime = state.lastWaitTime;
			break;
		default:
			return false;
		}
		
		state.tickCurrent += Integer.toUnsignedLong(waitTime);
		state.lastWaitTime = waitTime;
		return true;
	}
	
	private boolean readTrackEndEvent(MiscEvent e, Track t, CState state)
	{
		if (e == null) return false;
		if (e.getType() != MiscType.TRACK_END) return false;
		if (t == null) return false;
		if (state == null) state = new CState();
		try 
		{
			MidiEvent tEnd = new MidiEvent(new MetaMessage(0x2F, null, 0), state.tickCurrent);
			t.add(tEnd);
		} 
		catch (InvalidMidiDataException e1) 
		{
			e1.printStackTrace();
			return false;
		}
		
		return true;
	}
	
	private boolean readLoopPointEvent(MiscEvent e, Track t, CState state)
	{
		//Saved as just a marker event
		if (e == null) return false;
		if (e.getType() != MiscType.LOOP_POINT) return false;
		if (t == null) return false;
		if (state == null) state = new CState();
		final String lp = "Loop Point";
		try 
		{
			MidiEvent loopMarker = new MidiEvent(new MetaMessage(0x06, lp.getBytes(), lp.length()), state.tickCurrent);
			t.add(loopMarker);
		} 
		catch (InvalidMidiDataException e1) 
		{
			e1.printStackTrace();
			return false;
		}
		
		return true;
	}
	
	private boolean readOctaveSet(MiscEvent e, CState state)
	{
		if (e == null) return false;
		if (e.getType() != MiscType.SET_OCTAVE) return false;
		if (state == null) state = new CState();
		state.octCurrent = e.getParameter(0);
		return true;
	}
	
	private boolean readTempoSet(MiscEvent e, Track t, CState state)
	{
		if (e == null) return false;
		if (e.getType() != MiscType.SET_TEMPO) return false;
		if (t == null) return false;
		if (state == null) state = new CState();
		int bpm = e.getParameter(0);
		int mspq = 60000000 / bpm;
		byte[] byStr = FileBuffer.num24ToByStr(mspq);
		byte MSB = byStr[2];
		byStr[2] = byStr[0];
		byStr[0] = MSB;
		try 
		{
			MidiEvent tempoSet = new MidiEvent(new MetaMessage(0x51, byStr, 3), state.tickCurrent);
			t.add(tempoSet);
		} 
		catch (InvalidMidiDataException e1) 
		{
			e1.printStackTrace();
			return false;
		}
		
		return true;
	}
	
	private boolean readProgramChange(MiscEvent e, Track t, CState state)
	{
		if (e == null) return false;
		if (e.getType() != MiscType.SET_SAMPLE) return false;
		if (t == null) return false;
		if (state == null) state = new CState();
		int chan = e.getChannel();
		try 
		{
			MidiEvent pc = new MidiEvent(new ShortMessage(ShortMessage.PROGRAM_CHANGE, chan, e.getParameter(0), -1), state.tickCurrent);
			t.add(pc);
		} 
		catch (InvalidMidiDataException ex) 
		{
			ex.printStackTrace();
			return false;
		}
		return true;
	}
	
	private boolean readModWheelChange(MiscEvent e, Track t, CState state)
	{
		if (e == null) return false;
		if (e.getType() != MiscType.SET_MODU) return false;
		if (t == null) return false;
		if (state == null) state = new CState();
		int chan = e.getChannel();
		try 
		{
			MidiEvent pc = new MidiEvent(new ShortMessage(ShortMessage.CONTROL_CHANGE, chan, 0x01, e.getParameter(0)), state.tickCurrent);
			t.add(pc);
		} 
		catch (InvalidMidiDataException ex) 
		{
			ex.printStackTrace();
			return false;
		}
		return true;
	}
	
	private boolean readVolumeSet(MiscEvent e, Track t, CState state)
	{
		if (e == null) return false;
		if (e.getType() != MiscType.SET_VOLUME) return false;
		if (t == null) return false;
		if (state == null) state = new CState();
		int chan = e.getChannel();
		try 
		{
			MidiEvent pc = new MidiEvent(new ShortMessage(ShortMessage.CONTROL_CHANGE, chan, 0x07, e.getParameter(0)), state.tickCurrent);
			t.add(pc);
		} 
		catch (InvalidMidiDataException ex) 
		{
			ex.printStackTrace();
			return false;
		}
		return true;
	}
	
	private boolean readPitchBendSet(MiscEvent e, Track t, CState state)
	{
		if (e == null) return false;
		if (e.getType() != MiscType.SET_BEND) return false;
		if (t == null) return false;
		if (state == null) state = new CState();
		int chan = e.getChannel();
		int sh = e.getParameter(0);
		int sl = e.getParameter(1);
		int bend = ((sh << 8) | sl) >> 2;
		int msb = (bend >> 7) & 0x7F;
		int lsb = bend & 0x7F;
		try 
		{
			MidiEvent pb = new MidiEvent(new ShortMessage(ShortMessage.PITCH_BEND, chan, lsb, msb), state.tickCurrent);
			t.add(pb);
		} 
		catch (InvalidMidiDataException ex) 
		{
			ex.printStackTrace();
			return false;
		}
		return true;
	}
	
	private boolean readExpressionSet(MiscEvent e, Track t, CState state)
	{
		if (e == null) return false;
		if (e.getType() != MiscType.SET_XPRESS) return false;
		if (t == null) return false;
		if (state == null) state = new CState();
		int chan = e.getChannel();
		try 
		{
			MidiEvent pc = new MidiEvent(new ShortMessage(ShortMessage.CONTROL_CHANGE, chan, 0x0B, e.getParameter(0)), state.tickCurrent);
			t.add(pc);
		} 
		catch (InvalidMidiDataException ex) 
		{
			ex.printStackTrace();
			return false;
		}
		return true;
	}
	
	private boolean readPanChange(MiscEvent e, Track t, CState state)
	{
		if (e == null) return false;
		if (e.getType() != MiscType.SET_PAN) return false;
		if (t == null) return false;
		if (state == null) state = new CState();
		int chan = e.getChannel();
		try 
		{
			MidiEvent pc = new MidiEvent(new ShortMessage(ShortMessage.CONTROL_CHANGE, chan, 0x0A, e.getParameter(0)), state.tickCurrent);
			t.add(pc);
		} 
		catch (InvalidMidiDataException ex) 
		{
			ex.printStackTrace();
			return false;
		}
		return true;
	}
	
	private boolean readUnknownEvent(MiscEvent e, Track t, CState state)
	{
		//Saved as just a marker event
		if (e == null) return false;
		if (t == null) return false;
		if (state == null) state = new CState();
		String mark = "UNK 0x";
		mark += Integer.toHexString(e.getType().getOpCode());
		mark += " ";
		for (int i = 0; i < e.getType().getParameterCount(); i++)
		{
			mark += Integer.toHexString(e.getParameter(0)) + " ";
		}
		try 
		{
			MidiEvent unkMarker = new MidiEvent(new MetaMessage(0x06, mark.getBytes(), mark.length()), state.tickCurrent);
			t.add(unkMarker);
		} 
		catch (InvalidMidiDataException e1) 
		{
			e1.printStackTrace();
			return false;
		}
		
		return true;
	}
	
	private boolean toMidiEvent(SMDEvent e, Track t, CState state)
	{
		if (e == null) return false;
		if (e instanceof NoteEvent)
		{
			NoteEvent n = (NoteEvent)e;
			return readNote(n, t, state);
		}
		else if (e instanceof DeltaTimeEvent)
		{
			DeltaTimeEvent d = (DeltaTimeEvent)e;
			return readDeltaTime(d, state);
		}
		else if (e instanceof MiscEvent)
		{
			MiscEvent m = (MiscEvent)e;
			switch(m.getType())
			{
			case LOOP_POINT: return readLoopPointEvent(m, t, state);
			case NA_DELTATIME: return false;
			case NA_NOTE: return false;
			case SET_BEND: return readPitchBendSet(m, t, state);
			case SET_MODU: return readModWheelChange(m, t, state);
			case SET_OCTAVE: return readOctaveSet(m, state);
			case SET_PAN: return readPanChange(m, t, state);
			case SET_SAMPLE: return readProgramChange(m, t, state);
			case SET_TEMPO: return readTempoSet(m, t, state);
			case SET_VOLUME: return readVolumeSet(m, t, state);
			case SET_XPRESS: return readExpressionSet(m, t, state);
			case TRACK_END: return readTrackEndEvent(m, t, state);
			case UNK_9C: return readUnknownEvent(m, t, state);
			case UNK_9D: return readUnknownEvent(m, t, state);
			case UNK_A8: return readUnknownEvent(m, t, state);
			case UNK_A9: return readUnknownEvent(m, t, state);
			case UNK_AA: return readUnknownEvent(m, t, state);
			case UNK_B2: return readUnknownEvent(m, t, state);
			case UNK_B4: return readUnknownEvent(m, t, state);
			case UNK_B5: return readUnknownEvent(m, t, state);
			case UNK_BF: return readUnknownEvent(m, t, state);
			case UNK_C0: return readUnknownEvent(m, t, state);
			case UNK_D0: return readUnknownEvent(m, t, state);
			case UNK_D1: return readUnknownEvent(m, t, state);
			case UNK_D2: return readUnknownEvent(m, t, state);
			case UNK_D4: return readUnknownEvent(m, t, state);
			case UNK_D6: return readUnknownEvent(m, t, state);
			case UNK_DB: return readUnknownEvent(m, t, state);
			case UNK_DC: return readUnknownEvent(m, t, state);
			case UNK_E2: return readUnknownEvent(m, t, state);
			case UNK_EA: return readUnknownEvent(m, t, state);
			case UNK_F6: return readUnknownEvent(m, t, state);
			case WAIT_1BYTE: return readWaitEvent(m, state);
			case WAIT_2BYTE: return readWaitEvent(m, state);
			case WAIT_ADD: return readWaitEvent(m, state);
			case WAIT_AGAIN: return readWaitEvent(m, state);
			default: return false;
			}
		}
		return false;
	}
	
	/*--- Parsing ---*/
	
	private long readMainHeader(FileBuffer src, long stOff)
	{
		//Returns offset where it found "smdl"
		long magLoc = src.findString(stOff, src.getFileSize(), MAGIC);
		//System.out.println("readMainHeader | magLoc = 0x" + Long.toHexString(magLoc));
		if (magLoc < 0) return -1;
		if (src.isBigEndian()) src.setEndian(false);
		long cPos = magLoc + 8;
		this.FileSize = src.intFromFile(cPos);
		cPos = magLoc + 0x0E;
		this.InstGroup = Byte.toUnsignedInt(src.getByte(cPos));
		cPos = magLoc + 0x20;
		this.FileName = src.getASCII_string(cPos, '\0');	
		return magLoc;
	}
	
	private long readSongHeader(FileBuffer src, long smdlOff)
	{
		long sngLoc = src.findString(smdlOff + 64, smdlOff + 64 + 32, SONG_MAGIC);
		if (sngLoc < 0) return -1;
		if(src.isBigEndian()) src.setEndian(false);
		short ticks = src.shortFromFile(sngLoc + 0x12);
		this.TicksPerQNote = Short.toUnsignedInt(ticks);
		this.TrackNum = Byte.toUnsignedInt(src.getByte(sngLoc + 0x16));
		this.ChannelNum = Byte.toUnsignedInt(src.getByte(sngLoc + 0x17));
		return sngLoc;
	}
	
	private long[] parseTrack(FileBuffer src, long trOff, SMDTrack t)
	{
		//System.out.println("parseTrack | trOff = 0x" + Long.toHexString(trOff));
		if (trOff < 0 || trOff >= src.getFileSize()) return null;
		//System.out.println("parseTrack | given track is null: " + (t==null));
		if (t == null) return null;
		//System.out.println("parseTrack | Checkpoint 2");
		long magLoc = src.findString(trOff, src.getFileSize(), TRACK_MAGIC);
		//System.out.println("parseTrack | track magLoc = 0x" + Long.toHexString(magLoc));
		if (magLoc < 0) return null;
		if (src.isBigEndian()) src.setEndian(false);
		long[] StLen = new long[2];
		StLen[0] = magLoc;
		
		long chunkLen = Integer.toUnsignedLong(src.intFromFile(magLoc + 0x0C));
		//System.out.println("parseTrack | chunkLen = 0x" + Long.toHexString(chunkLen));
		StLen[1] = chunkLen;
		int trID = Byte.toUnsignedInt(src.getByte(magLoc + 0x10));
		int chanID = Byte.toUnsignedInt(src.getByte(magLoc + 0x11));
		t.setTrackID(trID);
		t.setChannelID(chanID);
		
		long cPos = magLoc + 0x14;
		boolean EOT = false;
		
		while (!EOT && cPos < magLoc + 16 + chunkLen)
		{
			SMDEvent e = readEvent(src, cPos);
			//System.out.println("parseTrack | Event at 0x" + Long.toHexString(cPos) + " is null: " + (e==null));
			if (e == null) return null;
			e.setChannel(t.chanID);
			t.addEvent(e);
			if (e.getType() == MiscType.TRACK_END) EOT = true;
			long elen = Integer.toUnsignedLong(e.getByteLength());
			cPos += elen;
		}
		
		return StLen;
	}
	
	private void readSMD(FileBuffer src, long stOff) throws UnsupportedFileTypeException
	{
		long hOff = readMainHeader(src, stOff);
		long sngOff = readSongHeader(src, hOff);
		//System.out.println("readSMD checkpoint 1");
		if (this.TrackNum <= 0) throw new UnsupportedFileTypeException();
		//System.out.println("readSMD checkpoint 2");
		long cPos = sngOff + 64;
		this.tracks = new SMDTrack[this.TrackNum];
		//System.out.println("readSMD | Number of tracks: " + TrackNum);
		if(this.FileName.charAt(FileName.length() - 1) == '.')
		{
			this.FileName = this.FileName.substring(0, FileName.length() - 1);
		}
		
		for (int i = 0; i < this.TrackNum; i++)
		{
			SMDTrack t = new SMDTrack();
			long[] tInfo = parseTrack(src, cPos, t);
			//System.out.println("readSMD checkpoint 3 | i = " + i);
			if (tInfo == null) throw new UnsupportedFileTypeException();
			//System.out.println("readSMD checkpoint 4 | i = " + i);
			cPos = tInfo[0] + 0x10 + tInfo[1];
			tracks[i] = t;
		}
		
	}
	
	/*--- Serialization ---*/
	

	/*--- Conversion ---*/
	
	private class CState
	{
		public long tickCurrent;
		public int octCurrent;
		public int lastWaitTime;
		public int lastNoteLen;
		
		public CState()
		{
			this.tickCurrent = 0;
			this.octCurrent = 0;
			this.lastNoteLen = 0;
			this.lastWaitTime = 0;
		}
	}
	
	private boolean trackToMidi(SMDTrack st, Track mt)
	{
		if (st == null) return false;
		if (mt == null) return false;
		CState s = new CState();
		for (int i = 0; i < st.getNumberEvents(); i++)
		{
			if (!toMidiEvent(st.getEvent(i), mt, s)) return false;
		}
				
		return true;
	}
	
	public Sequence toSequence() throws InvalidMidiDataException
	{
		if (this.TrackNum <= 0) throw new InvalidMidiDataException();
		if (this.tracks == null) throw new InvalidMidiDataException();
		Sequence seq = new Sequence(Sequence.PPQ, this.TicksPerQNote, this.TrackNum);
		Track[] mTracks = seq.getTracks();
		for (int i = 0; i < this.TrackNum; i++)
		{
			if (!trackToMidi(this.tracks[i], mTracks[i])) throw new InvalidMidiDataException();
		}
		return seq;
	}
	
	public MIDI toMIDI() throws InvalidMidiDataException
	{
		Sequence seq = this.toSequence();
		MIDI myMid = new MIDI(seq);
		myMid.setInternalName(this.FileName);
		return myMid;
	}
	
	public static long SMD_2_MIDI(String inPath, String outDir) throws IOException, UnsupportedFileTypeException, InvalidMidiDataException
	{
		return SMD_2_MIDI(inPath, 0, outDir);
	}
	
	public static long SMD_2_MIDI(String inPath, long stPos, String outDir) throws IOException, UnsupportedFileTypeException, InvalidMidiDataException
	{
		FileBuffer in = FileBuffer.createBuffer(inPath, false);
		SMD mySMD = new SMD(in, stPos);
		long smdSz = mySMD.getFileSize();
		
		/*String inInfo = mySMD.toString();
		FileBuffer inLog = new FileBuffer(inInfo.length());
		inLog.printASCIIToFile(inInfo);
		inLog.writeFile(FileBuffer.chopExtFromPath(inPath) + ".log");*/
		
		MIDI myMIDI = mySMD.toMIDI();
		myMIDI.setWriteBufferDir(outDir);
		
		/*String outInfo = myMIDI.toString();
		FileBuffer outLog = new FileBuffer(outInfo.length());
		outLog.printASCIIToFile(outInfo);
		outLog.writeFile(outDir + slash + mySMD.getInternalName() + ".log");*/
		
		String outName = outDir + File.separatorChar + mySMD.getInternalName() + ".mid";
		if (FileBuffer.fileExists(outName))
		{
			outName = outDir + File.separator + mySMD.getInternalName() + "_0" + ".mid"; 
		}
		myMIDI.writeMIDI(outName);
		return smdSz;
	}
	
	public static boolean isSMD(FileBuffer in, long stOff)
	{
		long magOff = in.findString(stOff, stOff + 0x10, MAGIC);
		if (magOff != stOff) return false;
		return true;
	}
	
	public String toString()
	{
		String s = "";
		s += "SMD ChunSoft Sequence File Structure ===============\n";
		s += "Internal Name: " + this.FileName + "\n";
		s += "File Size: 0x" + Integer.toHexString(FileSize) + " (" + FileSize + " bytes)\n";
		s += "Number of Tracks: " + this.TrackNum + "\n";
		s += "Number of Channels: " + this.ChannelNum + "\n";
		s += "Instrument Group: 0x" + Integer.toHexString(InstGroup) + "\n";
		s += "Ticks per Quarter Note: " + this.TicksPerQNote + "\n";
		if (this.tracks != null)
		{
			s += "\n";
			for (int i = 0; i < TrackNum; i++)
			{
				s += "\t ----- Track " + (i + 1) + "\n";
				SMDTrack t = this.tracks[i];
				int nEv = t.getNumberEvents();
				s += "\tTrack ID: " + t.trackID + "\n";
				s += "\tChannel ID: " + t.chanID + "\n";
				for (int j = 0; j < nEv; j++)
				{
					SMDEvent e = t.getEvent(j);
					s += "\t\t";
					if (e instanceof NoteEvent)
					{
						NoteEvent n = (NoteEvent)e;
						String[] pitches = {"C", "C#", "D", "D#",
											"E", "F", "F#", "G",
											"G#", "A", "A#", "B",
											"C +1", "C# +1", "D +1", "E +1"};
						s += "NOTE_EVENT | ";
						s += "Vel: " + n.getVelocity() + " ";
						s += "Oct Shift: " + n.getOctaveChange() + " ";
						s += "Pitch: " + pitches[n.getNote()] + " ";
						s += "Len: " + n.getLength();	
					}
					else if (e instanceof DeltaTimeEvent)
					{
						DeltaTimeEvent dte = (DeltaTimeEvent)e;
						s += "DETLATIME_EVENT | ";
						s += "Time: " + dte.getDelayTime() + " ";
					}
					else if (e instanceof MiscEvent)
					{
						MiscEvent m = (MiscEvent)e;
						s += m.getType().toString() + " | ";
						for (int k = 0; k < m.getType().getParameterCount(); k++)
						{
							int p = m.getParameter(k);
							if (p < 0x10) s += "0";
							s += Integer.toHexString(p) + " ";
						}
					}
					s += "\n";
				}
				s += "\n";
			}
		}
		else
		{
			s += "SMD object contains no tracks.\n";
		}
		return s;
	}
	
}
