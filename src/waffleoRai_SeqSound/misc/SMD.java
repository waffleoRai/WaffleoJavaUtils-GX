package waffleoRai_SeqSound.misc;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

import waffleoRai_Files.Converter;
import waffleoRai_SeqSound.MIDI;
import waffleoRai_SeqSound.SoundSeqDef;
import waffleoRai_SeqSound.misc.smd.*;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;
import waffleoRai_Files.tree.FileNode;

/*
 * UPDATES
 * 
 * 2020.04.27 | 1.0.0
 * 		Initial Documentation (class has been around since at least late 2017)
 * 2020.04.27 | 2.0.0
 * 		Structure overhaul to add playback interface (was geared toward MIDI conversion)
 * 
 */

/**
 * File/sequence wrapper class for ChunSoft SMD (music sequence) files
 * found in Pokemon Mystery Dungeon 2 (Explorers of Time/Darkness)
 * @version 2.0.0
 * @since April 27, 2020
 * @author Blythe Hospelhorn
 *
 */
public class SMD 
{
	
	public static final int TYPE_ID = 0x736d646c;
	
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
	
	public SMDTrack getTrack(int idx){
		return tracks[idx];
	}
	
	/*--- Setters ---*/
	
	/*--- Internal Structures ---*/
	
	private static SMDEvent readEvent(FileBuffer src, long eOff)
	{
		if (eOff < 0 || eOff >= src.getFileSize()) return null;
		int op = Byte.toUnsignedInt(src.getByte(eOff));
		if (op <= 0x7F)
		{
			byte keyFlags = src.getByte(eOff + 1);
			SMDNoteEvent n = new SMDNoteEvent((byte)op, keyFlags);
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
			SMDDeltaTimeEvent d = new SMDDeltaTimeEvent((byte)op);
			return d;
		}
		else
		{
			SMDMiscType mt = SMDMiscType.getType((byte)op);
			//System.out.println("op = 0x" + Integer.toHexString(op));
			if (mt == null) return null;
			SMDMiscEvent m = new SMDMiscEvent(mt);
			for (int i = 0; i < mt.getParameterCount(); i++)
			{
				int p = Byte.toUnsignedInt(src.getByte(eOff + i + 1));
				m.setParameter(i, p);
			}
			return m;
		}

	}
	
	/*--- Instruction Parse ---*/
	
	private int getMidiNote(int relNote, int octave)
	{
		int mNote = octave * 12;
		mNote += relNote;
		//Right now just clips values. Probably should have a more elegant way to handle.
		if (mNote < 0) return 0;
		if (mNote > 127) return 127;
		return mNote;
	}
	
	private boolean readNote(SMDNoteEvent n, Track t, CState state)
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
	
	private boolean readDeltaTime(SMDDeltaTimeEvent d, CState state)
	{
		if (d == null) return false;
		if (state == null) state = new CState();
		state.tickCurrent += Integer.toUnsignedLong(d.getDelayTime());
		return true;
	}
	
	private boolean readWaitEvent(SMDMiscEvent e, CState state)
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
	
	private boolean readTrackEndEvent(SMDMiscEvent e, Track t, CState state)
	{
		if (e == null) return false;
		if (e.getType() != SMDMiscType.TRACK_END) return false;
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
	
	private boolean readLoopPointEvent(SMDMiscEvent e, Track t, CState state)
	{
		//Saved as just a marker event
		if (e == null) return false;
		if (e.getType() != SMDMiscType.LOOP_POINT) return false;
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
	
	private boolean readOctaveSet(SMDMiscEvent e, CState state)
	{
		if (e == null) return false;
		if (e.getType() != SMDMiscType.SET_OCTAVE) return false;
		if (state == null) state = new CState();
		state.octCurrent = e.getParameter(0);
		return true;
	}
	
	private boolean readTempoSet(SMDMiscEvent e, Track t, CState state)
	{
		if (e == null) return false;
		if (e.getType() != SMDMiscType.SET_TEMPO) return false;
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
	
	private boolean readProgramChange(SMDMiscEvent e, Track t, CState state)
	{
		if (e == null) return false;
		if (e.getType() != SMDMiscType.SET_SAMPLE) return false;
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
	
	private boolean readModWheelChange(SMDMiscEvent e, Track t, CState state)
	{
		if (e == null) return false;
		if (e.getType() != SMDMiscType.SET_MODU) return false;
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
	
	private boolean readVolumeSet(SMDMiscEvent e, Track t, CState state)
	{
		if (e == null) return false;
		if (e.getType() != SMDMiscType.SET_VOLUME) return false;
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
	
	private boolean readPitchBendSet(SMDMiscEvent e, Track t, CState state)
	{
		if (e == null) return false;
		if (e.getType() != SMDMiscType.SET_BEND) return false;
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
	
	private boolean readExpressionSet(SMDMiscEvent e, Track t, CState state)
	{
		if (e == null) return false;
		if (e.getType() != SMDMiscType.SET_XPRESS) return false;
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
	
	private boolean readPanChange(SMDMiscEvent e, Track t, CState state)
	{
		if (e == null) return false;
		if (e.getType() != SMDMiscType.SET_PAN) return false;
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
	
	private boolean readUnknownEvent(SMDMiscEvent e, Track t, CState state)
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
		if (e instanceof SMDNoteEvent)
		{
			SMDNoteEvent n = (SMDNoteEvent)e;
			return readNote(n, t, state);
		}
		else if (e instanceof SMDDeltaTimeEvent)
		{
			SMDDeltaTimeEvent d = (SMDDeltaTimeEvent)e;
			return readDeltaTime(d, state);
		}
		else if (e instanceof SMDMiscEvent)
		{
			SMDMiscEvent m = (SMDMiscEvent)e;
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
	
	public static boolean isSMD(FileBuffer in, long stOff)
	{
		long magOff = in.findString(stOff, stOff + 0x10, MAGIC);
		if (magOff != stOff) return false;
		return true;
	}
	
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
			e.setChannel(t.getChannelID());
			t.addEvent(e);
			if (e.getType() == SMDMiscType.TRACK_END) EOT = true;
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
	

	/*--- MIDI ---*/
	
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
		return SMD_2_MIDI(in, stPos, outDir);
	}

	public static long SMD_2_MIDI(FileBuffer in, long stPos, String outDir) throws IOException, UnsupportedFileTypeException, InvalidMidiDataException
	{
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

	public void printToStderr()
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
				s += "\tTrack ID: " + t.getTrackID() + "\n";
				s += "\tChannel ID: " + t.getChannelID() + "\n";
				for (int j = 0; j < nEv; j++)
				{
					SMDEvent e = t.getEvent(j);
					s += "\t\t";
					if (e instanceof SMDNoteEvent)
					{
						SMDNoteEvent n = (SMDNoteEvent)e;
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
					else if (e instanceof SMDDeltaTimeEvent)
					{
						SMDDeltaTimeEvent dte = (SMDDeltaTimeEvent)e;
						s += "DETLATIME_EVENT | ";
						s += "Time: " + dte.getDelayTime() + " ";
					}
					else if (e instanceof SMDMiscEvent)
					{
						SMDMiscEvent m = (SMDMiscEvent)e;
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
		//return s;
		System.err.println(s);
	}
	
	/*--- Definition ---*/

	private static SMDSeqDef static_def;
	
	public static SMDSeqDef getDefinition(){
		if(static_def == null) static_def = new SMDSeqDef();
		return static_def;
	}
	
	public static class SMDSeqDef extends SoundSeqDef{

		private static final String DEFO_ENG_STR = "Procyon SMD Sound/Music Sequence";
		private static final String[] EXT_LIST = {"smd", "SMD", "smdl"};
		
		private String str;
		
		public SMDSeqDef(){
			str = DEFO_ENG_STR;
		}
		
		public Collection<String> getExtensions() {
			List<String> list = new ArrayList<String>(EXT_LIST.length);
			for(String s : EXT_LIST)list.add(s);
			return list;
		}

		public String getDescription() {return str;}
		public int getTypeID() {return TYPE_ID;}
		public void setDescriptionString(String s) {str = s;}
		public String getDefaultExtension() {return "smd";}

	}
	
	/*--- Converter ---*/
	
	private static SMDConverter cdef;
	
	public static SMDConverter getDefaultConverter(){
		if(cdef == null) cdef = new SMDConverter();
		return cdef;
	}
	
	public static class SMDConverter implements Converter{

		public static final String DEFO_ENG_FROM = "Procyon Sound/Music Sequence (.smd)";
		public static final String DEFO_ENG_TO = "MIDI Sound Sequence (.mid)";
		
		private String from_desc;
		private String to_desc;
		
		public SMDConverter(){
			from_desc = DEFO_ENG_FROM;
			to_desc = DEFO_ENG_TO;
		}
		
		public String getFromFormatDescription() {return from_desc;}
		public String getToFormatDescription() {return to_desc;}
		public void setFromFormatDescription(String s) {from_desc = s;}
		public void setToFormatDescription(String s) {to_desc = s;}

		public void writeAsTargetFormat(String inpath, String outpath)
				throws IOException, UnsupportedFileTypeException {
			writeAsTargetFormat(FileBuffer.createBuffer(inpath), outpath);
		}

		public void writeAsTargetFormat(FileBuffer input, String outpath)
				throws IOException, UnsupportedFileTypeException {
			try {SMD_2_MIDI(input, 0, outpath);} 
			catch (InvalidMidiDataException e) {
				e.printStackTrace();
				throw new UnsupportedFileTypeException("MIDI Conversion Error");
			}
		}
		
		public void writeAsTargetFormat(FileNode node, String outpath) 
				throws IOException, UnsupportedFileTypeException{
			FileBuffer dat = node.loadDecompressedData();
			writeAsTargetFormat(dat, outpath);
		}

		public String changeExtension(String path) {
			if(path == null) return null;
			if(path.isEmpty()) return path;
			
			int lastdot = path.lastIndexOf('.');
			if(lastdot < 0) return path + ".mid";
			return path.substring(0, lastdot) + ".mid";
		}
		
	}
	
	
	
}
