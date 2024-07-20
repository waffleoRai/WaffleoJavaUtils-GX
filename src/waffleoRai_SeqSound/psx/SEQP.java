package waffleoRai_SeqSound.psx;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

import waffleoRai_DataContainers.MultiValMap;
import waffleoRai_Files.Converter;
import waffleoRai_Files.FileTypeDefinition;
import waffleoRai_Files.tree.FileNode;
import waffleoRai_SeqSound.MIDI;
import waffleoRai_SeqSound.MIDIMetaCommands;
import waffleoRai_SeqSound.MidiMessageGenerator;
import waffleoRai_SeqSound.SortableMidiEvent;
import waffleoRai_SeqSound.SoundSeqDef;
import waffleoRai_SeqSound.MIDI.MessageType;
import waffleoRai_SeqSound.MIDIControllers;
import waffleoRai_Utils.BitStreamer;
import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.MultiFileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;

public class SEQP {
	
	/*--- Constants ---*/
	
	public static final String MAGIC = "pQES";
	public static final String EXTENSION = "seq";
	
	//Note: the 0x00 is the delta time, you dolt
	public static final byte[] INFINITE_LOOP_ST = {(byte)0xB0, 0x63, 0x14, 0x00, 0x06, 0x7F};
	public static final byte[] INFINITE_LOOP_ED = {(byte)0xB0, 0x63, 0x1E};
	
	public static final int NRPN_LOOP_START = 0x14;
	public static final int NRPN_LOOP_END = 0x1e;
	
	public static final String FNMETAKEY_BANKUID = "PSX_BNK_ID";
	public static final String FNMETAKEY_BANKPATH = "PSX_BNK_PATH";
	
	/*--- Instance Variables ---*/
	
	private int version;
	
	private int TicksPerQNote;
	private int MicrosecondsPerQNote; //tempo
	private int timeSigNumerator;
	private int timeSigDenominator; //True time sig denom is 2^(this value)
	
	private long loopStart = -1;
	private long loopEnd = -1;
	private int loopCount = -1;
	
	//private Sequence contents;
	
	private MultiValMap<Long, MidiEvent> eventMap;
	
	/*--- Construction/Parsing ---*/

	public SEQP() throws InvalidMidiDataException {
		version = 1;
		TicksPerQNote = 0x1E0;
		//Default tempo: 120 bpm
		MicrosecondsPerQNote = 5000000;
		timeSigNumerator = 4;
		timeSigDenominator = 2; //(2^2)
		//contents = new Sequence(Sequence.PPQ, TicksPerQNote, 16);
		eventMap = new MultiValMap<Long, MidiEvent>();
	}
	
	public SEQP(String filepath) throws IOException, UnsupportedFileTypeException, InvalidMidiDataException {
		this(filepath, 0);
	}
	
	public SEQP(String filepath, long stpos) throws IOException, UnsupportedFileTypeException, InvalidMidiDataException {
		FileBuffer seq = FileBuffer.createBuffer(filepath, true);
		eventMap = new MultiValMap<Long, MidiEvent>();
		parseSEQ(seq.getReferenceAt(stpos));
	}
	
	public SEQP(FileBuffer file, long stpos) throws UnsupportedFileTypeException, InvalidMidiDataException {
		this(file.getReferenceAt(stpos));
	}
	
	public SEQP(BufferReference data) throws UnsupportedFileTypeException, InvalidMidiDataException {
		eventMap = new MultiValMap<Long, MidiEvent>();
		parseSEQ(data);
	}
	
	public static SEQP fromMIDI(MIDI mid, boolean verbose) throws InvalidMidiDataException{
		SEQP seqp = new SEQP();
		seqp.TicksPerQNote = mid.getTPQN();

		//Need to scan all tracks and events as they come in.
		Sequence mseq = mid.getSequence();
		Track[] tracks = mseq.getTracks();
		MidiMessage lastmsg = null;
		for(int i = 0; i < tracks.length; i++) {
			if(tracks[i] == null) continue;
			int ecount = tracks[i].size();
			for(int j = 0; j < ecount; j++) {
				MidiEvent ev = tracks[i].get(j);
				long tick = ev.getTick();

				MidiMessage mmsg = ev.getMessage();
				List<MidiEvent> existingEvents = seqp.eventMap.getValues(tick);
				byte[] bytes = mmsg.getMessage();
				//Throw away anything redundant.
				if(existingEvents != null && !existingEvents.isEmpty()) {
					boolean match = false;
					for(MidiEvent other : existingEvents) {
						byte[] obytes = other.getMessage().getMessage();
						if(bytes.length != obytes.length) continue;
						boolean mismatch = false;
						for(int k = 0; k < bytes.length; k++) {
							if(obytes[k] != bytes[k]) {
								mismatch = true;
								break;
							}
						}
						if(!mismatch) {
							match = true;
							break;
						}
					}
					if(match) continue;
				}
				
				int stat = mmsg.getStatus() & 0xff;
				MessageType mtype = getMessageType((byte)stat);
				
				switch(mtype) {
				case META:
					//Any meta events not on track 1 are ignored.
					if(i != 0) {
						if(verbose) System.err.println("SEQP.fromMIDI || Meta event found on track " + (i+1) + ", ignoring...");
						continue;
					}
					
					byte metaType = bytes[1];
					if(tick == 0) {
						if(metaType == MIDIMetaCommands.TEMPO) {
							seqp.MicrosecondsPerQNote = 0;
							for(int k = 0; k < MIDIMetaCommands.LEN_TEMPO; k++) {
								seqp.MicrosecondsPerQNote <<= 8;
								seqp.MicrosecondsPerQNote |= Byte.toUnsignedInt(bytes[2+k]);
							}
						}
						else if(metaType == MIDIMetaCommands.END) {
							seqp.eventMap.addValue(tick, ev);
						}
						else if(metaType == MIDIMetaCommands.TIMESIG) {
							seqp.timeSigNumerator = Byte.toUnsignedInt(bytes[2]);
							seqp.timeSigDenominator = Byte.toUnsignedInt(bytes[3]);
						}
						else {
							if(verbose) { System.err.println("SEQP.fromMIDI || Incompatible meta event will be skipped (Track " + (i+1) + "): " +
									String.format("%02x", metaType));}
						}
					}
					else {
						if(metaType == MIDIMetaCommands.TEMPO) {
							seqp.eventMap.addValue(tick, ev);
						}
						else if(metaType == MIDIMetaCommands.END) {
							seqp.eventMap.addValue(tick, ev);
						}
						else {
							if(verbose) { System.err.println("SEQP.fromMIDI || Incompatible meta event will be skipped (Track " + (i+1) + "): " +
									String.format("%02x", metaType));}
						}
					}
					break;
				case SYSEX:
					if(verbose) {
						System.err.println("SEQP.fromMIDI || Sysex events are not supported. Event skipped. (Track " + (i+1) + ")");
					}
					break;
				case MIDI:
					if((stat & 0xf0) == 0xb0) {
						//Check for loop info.
						boolean noAdd = false;
						if(bytes[1] == MIDIControllers.NRPN_MSB) {
							if(bytes[2] == NRPN_LOOP_START) {
								seqp.loopStart = tick;
								noAdd = true;
							}
							else if(bytes[2] == NRPN_LOOP_END) {
								seqp.loopEnd = tick;
								noAdd = true;
							}
						}
						else if(bytes[1] == MIDIControllers.DATA_ENTRY) {
							if(lastmsg != null && lastmsg.getStatus() == stat) {
								byte[] lbytes = lastmsg.getMessage();
								if(lbytes[1] == MIDIControllers.NRPN_MSB && lbytes[2] == NRPN_LOOP_START) {
									//Loop count.
									seqp.loopCount = Byte.toUnsignedInt(lbytes[2]);
									noAdd = true;
								}
							}
						}
						if(!noAdd) seqp.eventMap.addValue(tick, ev);
					}
					else {
						seqp.eventMap.addValue(tick, ev);
					}
					break;
				default: 
					if(verbose) {
						System.err.println("SEQP.fromMIDI || Unknown event type. Event skipped. (Track " + (i+1) + "): " + 
								String.format("%02x", stat));
					}
					break;
				}
				lastmsg = mmsg;
			}
		}
		return seqp;
	}
	
	public static MessageType getMessageType(byte stat) {
		int istat = Byte.toUnsignedInt(stat);
		if (istat == 0xFF) return MessageType.META;
		else if(istat == 0xF0 || istat == 0xF7) return MessageType.SYSEX;
		else {
			if (BitStreamer.readABit(stat, 7) && (((istat >> 4) & 0xF) != 0xF)) {
				return MessageType.MIDI;
			}
			else if (!BitStreamer.readABit(stat, 7)) return MessageType.RUNNING;
		}
		
		return null;
	}
	
	private void parseSEQ(BufferReference data) throws UnsupportedFileTypeException, InvalidMidiDataException{
		data.setByteOrder(true);
		
		String mCheck = data.nextASCIIString(4);
		if(!MAGIC.equals(mCheck)) throw new UnsupportedFileTypeException("SEQP.parseSEQ || SEQP magic number not found!");
		
		version = data.nextInt();
		TicksPerQNote = Short.toUnsignedInt(data.nextShort());
		MicrosecondsPerQNote = data.next24Bits();
		timeSigNumerator = Byte.toUnsignedInt(data.nextByte());
		timeSigDenominator = Byte.toUnsignedInt(data.nextByte());
		
		//End is noted by track end message
		boolean trackend = false;
		long tickPos = 0;
		MidiMessage lastmsg = null;
		
		while(!trackend) {
			//1. Get delta time
			int delta = MIDI.getVLQ(data);
			
			//2. Interpret delta time
			tickPos += Integer.toUnsignedLong(delta);
			
			//3. Figure out message type
			byte stat = data.nextByte();
			MessageType type = getMessageType(stat);
			if (type == null) throw new UnsupportedFileTypeException("SEQP.parseSEQ || Could not determine message type for command: " +
					String.format("0x%02x", stat));
			
			switch(type) {
			case META:
				//Get type
				byte metatype = data.nextByte();
				
				//SEQp files do NOT have a length field!
				//I guess it knows depending on the type. 
				// SEQp files should only have 1 of 2 types: 
				//		2F (track end) - 1 Byte (0x00)
				//		51 (tempo change) - 3 bytes
				if (metatype == MIDIMetaCommands.END){
					trackend = true;
					for (int i = 0; i < 16; i++){
						byte[] mdata = {0x00};
						MetaMessage msg = new MetaMessage(metatype, mdata, 1);
						eventMap.addValue(tickPos, new MidiEvent(msg, tickPos));
					}
				}
				else if (metatype == MIDIMetaCommands.TEMPO){
					//Next three bytes are the data.
					byte[] mdata = new byte[MIDIMetaCommands.LEN_TEMPO];
					for (int i = 0; i < MIDIMetaCommands.LEN_TEMPO; i++){
						mdata[i] = data.nextByte();
					}
					//Add to track 0 only?
					MetaMessage msg = new MetaMessage(metatype, mdata, MIDIMetaCommands.LEN_TEMPO);
					eventMap.addValue(tickPos, new MidiEvent(msg, tickPos));
					lastmsg = msg;
				}
				else{
					throw new UnsupportedFileTypeException("SEQP.parseSEQ || Illegal Meta Event Encountered -- 0x" + String.format("%02x", metatype));
				}
				break;
			case MIDI:
				//Get channel
				int sint = Byte.toUnsignedInt(stat);
				//int ch = sint & 0xF;
				int stype = (sint & 0xF0) >>> 4;

				//Determine whether we are looking at one or two data bytes
				if (stype == 0xC || stype == 0xD){
					//One byte
					int dByte = Byte.toUnsignedInt(data.nextByte());
					ShortMessage msg = new ShortMessage(sint, dByte, 0);
					eventMap.addValue(tickPos, new MidiEvent(msg, tickPos));
					lastmsg = msg;
				}
				else{
					//Two bytes
					int d1 = Byte.toUnsignedInt(data.nextByte());
					int d2 = Byte.toUnsignedInt(data.nextByte());
					
					//Note if it's a potential loop point
					boolean noAdd = false;
					if (stype == 0xB && d1 == MIDIControllers.NRPN_MSB){
						if (d2 == NRPN_LOOP_START){
							//It's loop start
							//Skip ahead to look for the loop value
							
							/*int val = 0x7F;
							byte nxt = data.getByte(1);
							if (nxt == MIDIControllers.DATA_ENTRY) val = Byte.toUnsignedInt(data.getByte(2));
							loopCount = val;*/
							
							loopStart = tickPos;
							noAdd = true;
						}
						else if (d2 == NRPN_LOOP_END){
							//It's loop end
							loopEnd = tickPos;
							noAdd = true;
						}
					}
					if (stype == 0xB && d1 == MIDIControllers.DATA_ENTRY){
						if(lastmsg != null) {
							byte[] lastBytes = lastmsg.getMessage();
							if(lastBytes[1] == MIDIControllers.NRPN_MSB && lastBytes[2] == NRPN_LOOP_START) {
								loopCount = d2;
								noAdd = true;
							}
						}
					}
					
					ShortMessage msg = new ShortMessage(sint, d1, d2);
					if(!noAdd) {
						eventMap.addValue(tickPos, new MidiEvent(msg, tickPos));
					}
					lastmsg = msg;
				}
				break;
			case RUNNING:
				if (lastmsg == null) throw new UnsupportedFileTypeException("SEQP.parseSEQ || Assumed running message, but no previous message to run off of!");
				
				//"stat" is actually the first data byte of this message
				int istat = lastmsg.getStatus();
				MessageType lasttype = getMessageType((byte)istat);
				switch(lasttype) {
					case META:
						//Only type this should be is 0x51
						if (stat == MIDIMetaCommands.TEMPO) {
							//Next three bytes are the data.
							byte[] mdata = new byte[3];
							for (int i = 0; i < 3; i++) {
								mdata[i] = data.nextByte();
							}
							//Add to track 0 only?
							MetaMessage msg = new MetaMessage(MIDIMetaCommands.TEMPO, mdata, 3);
							eventMap.addValue(tickPos, new MidiEvent(msg, tickPos));
							lastmsg = msg;
						}
						else {
							throw new UnsupportedFileTypeException("SEQP.parseSEQ || Illegal Meta Event Encountered -- " + "Type: 0x" + String.format("%02X", stat));
						}
						break;
					case MIDI:
						//It seems that note-on/note-off can be run together if it's the same note
						int statType = (istat >>> 4) & 0xF;
						//int chan = istat & 0xF;
						if (statType == 0xC || statType == 0xD) {
							//One byte
							int dByte = Byte.toUnsignedInt(stat);
							ShortMessage msg = new ShortMessage(istat, dByte, 0);
							eventMap.addValue(tickPos, new MidiEvent(msg, tickPos));
							lastmsg = msg;
						}
						else {
							int d1 = Byte.toUnsignedInt(stat);
							int d2 = Byte.toUnsignedInt(data.nextByte());
							
							//Don't add if loop count
							boolean noAdd = false;
							if(statType == 0xB && d1 == MIDIControllers.DATA_ENTRY) {
								byte[] lastBytes = lastmsg.getMessage();
								if(lastBytes[1] == MIDIControllers.NRPN_MSB && lastBytes[2] == NRPN_LOOP_START) {
									noAdd = true;
								}
							}
							
							ShortMessage msg = new ShortMessage(istat, d1, d2);
							if(!noAdd) eventMap.addValue(tickPos, new MidiEvent(msg, tickPos));
							lastmsg = msg;
						}
						break;
					case RUNNING:
						//Should not be possible
						System.err.println(Thread.currentThread().getName() + " || SEQP.parseSEQ || Parsing Error! Previous message should contain status, even if running status!");
						throw new FileBuffer.UnsupportedFileTypeException();
					case SYSEX:
						//Should not be possible
						System.err.println(Thread.currentThread().getName() + " || SEQP.parseSEQ || Illegal Sysex Event Encountered -- ");
						throw new FileBuffer.UnsupportedFileTypeException();
					}
					break;
				case SYSEX:
					//SEQp does not appear to use SYSEX messages
					throw new UnsupportedFileTypeException("SEQP.parseSEQ || Illegal Sysex Event Encountered -- " + "Type: 0x" + String.format("%02X", stat));
			}
		}
	}
	
	/*--- Getters ---*/
	
	public int getVersion()
	{
		return version;
	}
	
	public int getTicksPerQuarterNote()
	{
		return TicksPerQNote;
	}
	
	public int getMicrosecondsPerQuarterNote()
	{
		return MicrosecondsPerQNote;
	}
	
	public int getTempoInBPM()
	{
		double sPerBeat = (double)MicrosecondsPerQNote / 1000000.0;
		double beatsPerSecond = 1.0/sPerBeat;
		double beatsPerMinute = beatsPerSecond/60.0;
		return (int)Math.round(beatsPerMinute);
	}
	
	public int getTimeSignatureNumerator()
	{
		return timeSigNumerator;
	}
	
	public int getTimeSignatureDenominator()
	{
		return (int)Math.pow(2.0, (double)timeSigDenominator);
	}
	
	public long getLoopStart() {return loopStart;}
	public long getLoopEnd() {return loopEnd;}
	public int getLoopCount() {return loopCount;}
	
	/*--- Setters ---*/
	
	public void setResolution(int ticksPerQuarterNote)
	{
		TicksPerQNote = ticksPerQuarterNote;
	}
	
	public void setTempo(int beatsPerMinute)
	{
		double n = 1.0/(double)beatsPerMinute;
		n *= 60000000.0;
		MicrosecondsPerQNote = (int)Math.round(n);
	}
	
	public void setUSPQN(int microsecondsPerQuarterNode) {
		this.MicrosecondsPerQNote = microsecondsPerQuarterNode;
	}
	
	public void setTimeSignature(int num, int denom)
	{
		timeSigNumerator = num;
		int log = 0;
		int n = denom;
		while (n > 1)
		{
			n = n >>> 1;
			log++;
		}
		timeSigDenominator = log;
	}
	
	public void setLoopStart(long val) {loopStart = val;}
	public void setLoopEnd(long val) {loopEnd = val;}
	public void setLoopCount(int val) {loopCount = val;}
	
	/*--- Serialization ---*/
	
	private FileBuffer serializeData() throws IOException, InvalidMidiDataException{
		//Alloc amount
		int alloc = 9; //For loops, if needed
		Collection<MidiEvent> allEvents = eventMap.allValues();
		for(MidiEvent ev : allEvents) {
			alloc += 4; //Max delta time
			MidiMessage mmsg = ev.getMessage();
			alloc += mmsg.getLength();
		}
		
		long tickPos = 0;
		FileBuffer buffer = new FileBuffer(alloc, true);
		Set<Long> keyset = eventMap.getBackingMap().keySet();
		if(loopStart >= 0) keyset.add(loopStart);
		if(loopEnd >= 0) keyset.add(loopEnd);
		
		List<Long> keys = new ArrayList<Long>(keyset.size()+1);
		keys.addAll(keyset);
		Collections.sort(keys);
		for(Long tickVal : keys) {
			int delta = (int)(tickVal - tickPos);
			byte[] vlq = MIDI.makeVLQ(delta);
			for(int i = 0; i < vlq.length; i++) buffer.addToFile(vlq[i]);
			
			boolean encodeDelta = false;
			int lastStat = -1;
			if(tickVal == loopStart) {
				buffer.addToFile((byte) 0xb0);
				buffer.addToFile((byte) MIDIControllers.NRPN_MSB);
				buffer.addToFile((byte) NRPN_LOOP_START);
				
				buffer.addToFile((byte) 0x00); //Delta
				buffer.addToFile((byte) MIDIControllers.DATA_ENTRY);
				buffer.addToFile((byte) loopCount);
				encodeDelta = true;
				lastStat = 0xb0;
			}
			else if(tickVal == loopEnd) {
				
				buffer.addToFile((byte) 0xb0);
				buffer.addToFile((byte) MIDIControllers.NRPN_MSB);
				buffer.addToFile((byte) NRPN_LOOP_END);
				encodeDelta = true;
				lastStat = 0xb0;
			}
			
			List<MidiEvent> events = eventMap.getValues(tickVal);
			if(events != null && !events.isEmpty()) {
				for(MidiEvent event : events) {
					if(encodeDelta) buffer.addToFile((byte) 0x00);
					
					MidiMessage msg = event.getMessage();
					int stat = msg.getStatus();
					if(stat != lastStat) {
						//Encode stat.
						//SEQP allows for running status on meta events, though vanilla midi does not. I think.
						buffer.addToFile((byte) stat);
						lastStat = stat;
					}
					
					byte[] mbytes = msg.getMessage();
					if(stat == 0xff) {
						//Don't want length field.
						for(int i = 2; i < mbytes.length; i++) buffer.addToFile(mbytes[i]);
					}
					else {
						//Just copy.
						for(int i = 1; i < mbytes.length; i++) buffer.addToFile(mbytes[i]);
					}
					
					encodeDelta = true;
				}
			}
			tickPos = tickVal;
		}
		
		return buffer;
	}
	
	public boolean serializeSEQ(OutputStream out) throws IOException, InvalidMidiDataException
	{
		FileBuffer header = new FileBuffer(15, true);
		header.printASCIIToFile(MAGIC);
		header.addToFile(version);
		header.addToFile((short)TicksPerQNote);
		header.add24ToFile(MicrosecondsPerQNote);
		header.addToFile((byte)timeSigNumerator);
		header.addToFile((byte)timeSigDenominator);
		
		/*FileBuffer seqp = new CompositeBuffer(2);
		seqp.addToFile(header);
		seqp.addToFile(serializeData());*/
		header.writeToStream(out);
		serializeData().writeToStream(out);
		
		return true;
	}
	
	public FileBuffer serializeSEQ() throws IOException, InvalidMidiDataException{
		FileBuffer header = new FileBuffer(15, true);
		header.printASCIIToFile(MAGIC);
		header.addToFile(version);
		header.addToFile((short)TicksPerQNote);
		header.add24ToFile(MicrosecondsPerQNote);
		header.addToFile((byte)timeSigNumerator);
		header.addToFile((byte)timeSigDenominator);
		
		MultiFileBuffer out = new MultiFileBuffer(2);
		out.addToFile(header);
		out.addToFile(serializeData());
		
		return out;
	}
	
	public void writeSEQ(String path) throws IOException, InvalidMidiDataException
	{
		//FileBuffer seq = serializeSEQ();
		//seq.writeFile(path);
		BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(path));
		serializeSEQ(bos);
		bos.close();
	}
	
	private static List<SortableMidiEvent> sortMidiEvents(Sequence sequence)
	{
		List<SortableMidiEvent> elist = new LinkedList<SortableMidiEvent>();
		if(sequence == null) return elist;
		
		Track[] tracks = sequence.getTracks();
		for(int i = 0; i < tracks.length; i++)
		{
			Track t = tracks[i];
			if(t == null) continue;
			int ecount = t.size();
			for(int j = 0; j < ecount; j++)
			{
				elist.add(new SortableMidiEvent(t.get(j)));
			}
		}
		
		Collections.sort(elist);
		
		return elist;
	}
	
	public static void writeAsSEQ(Sequence sequence, OutputStream output, long loopStartTick, long loopEndTick) throws IOException {
		//Events need to be rearranged into one track.
		List<SortableMidiEvent> elist = sortMidiEvents(sequence);
		
		//Extract header info...
		short timeSigNum = 4;
		short timeSigDen = 2;
		int initTempo = 0x07a120; //I think this is 120bpm
		int tpqn = sequence.getResolution();
		boolean sigfound = false;
		long tempoticks = -1;
		while(!sigfound || (tempoticks == -1))
		{
			//Scan events to see if there is tempo set or time sig set.
			for(SortableMidiEvent e : elist)
			{
				MidiEvent event = e.getEvent();
				MidiMessage msg = event.getMessage();
				if(msg.getStatus() != 0xFF) continue;
				byte[] mbytes = msg.getMessage();
				if(!sigfound && (mbytes[1] == 0x58))
				{
					//TODO
					sigfound = true;
				}
				if((tempoticks == -1) && (mbytes[1] == 0x51))
				{
					tempoticks = event.getTick();
					initTempo = 0;
					initTempo |= Byte.toUnsignedInt(mbytes[3]) << 16;
					initTempo |= Byte.toUnsignedInt(mbytes[4]) << 8;
					initTempo |= Byte.toUnsignedInt(mbytes[5]);
					System.err.println("Tempo detected: 0x" + Integer.toHexString(initTempo));
				}
			}
		}
		
		//Write header
		FileBuffer header = new FileBuffer(15, true);
		header.printASCIIToFile(MAGIC);
		header.addToFile(1); //Version
		header.addToFile((short)tpqn);
		header.add24ToFile(initTempo);
		header.addToFile(timeSigNum);
		header.addToFile(timeSigDen);
		
		output.write(header.getBytes());
		
		//Write data (adding loops when needed)
		//Removes all meta events that aren't tempo changes for now.
		long current_tick = 0;
		boolean ls_added = (loopStartTick < 0);
		boolean le_added = (loopEndTick < 0);
		int last_stat = 0x00;
		for(SortableMidiEvent e : elist)
		{
			MidiEvent event = e.getEvent();
			long tick = event.getTick();
			MidiMessage msg = event.getMessage();
			
			int delta = (int)(tick - current_tick);
			current_tick = tick;
			
			//First, see about inserting the loop messages...
			if(!ls_added && tick == loopStartTick)
			{
				output.write(MIDI.makeVLQ(delta));
				delta = 0;
				output.write(INFINITE_LOOP_ST);
			}
			if(!le_added && tick == loopEndTick)
			{
				output.write(MIDI.makeVLQ(delta));
				delta = 0;
				output.write(INFINITE_LOOP_ED);
			}
			
			//See what it is (and if need to skip...)
			if(msg.getStatus() == 0xFF)
			{
				//Will need to omit length...
				//See if it's our tempo mark...
				byte[] mbytes = msg.getMessage();
				if((mbytes[1] == 0x51) && (tick == tempoticks)) continue;
				
				output.write(MIDI.makeVLQ(delta));
				if(last_stat != 0xFF) output.write(mbytes[0]);
				last_stat = 0xFF;
				output.write(mbytes[1]);
				output.write(mbytes, 3, mbytes.length-3);
			}
			else
			{
				output.write(MIDI.makeVLQ(delta));
				
				if(msg.getStatus() == last_stat)
				{
					byte[] bytes = msg.getMessage();
					output.write(bytes, 1, bytes.length-1);
				}
				else output.write(msg.getMessage());
				last_stat = msg.getStatus();
			}
			
		}
		
	}
	
	/*--- Conversion ---*/
	
	public Sequence getSequence() {
		//Distributes events by channel into 16 tracks.
		//Also adds tempo marker at beginning.
		//Loop points are kept as NRPNs as-is
		
		Sequence mseq;
		try {
			mseq = new Sequence(Sequence.PPQ, TicksPerQNote);
			Track[] tracks = new Track[16];

			for(int i = 0; i < 16; i++) {
				tracks[i] = mseq.createTrack();
			}
			
			long tickPos = 0;
			MidiMessage mmsg = null;
			MidiMessageGenerator mgen = new MidiMessageGenerator();
			byte[] mbytes = null;
			
			//Add stuff at start for track 0
			mmsg = mgen.genTempoSet(MicrosecondsPerQNote);
			tracks[0].add(new MidiEvent(mmsg, 0L));
			
			//Do events (don't forget loop points)
			//Omit end track events (since there should only be one)
			Set<Long> keyset = eventMap.getBackingMap().keySet();
			if(loopStart >= 0) keyset.add(loopStart);
			if(loopEnd >= 0) keyset.add(loopEnd);
			
			List<Long> keys = new ArrayList<Long>(keyset.size()+1);
			keys.addAll(keyset);
			Collections.sort(keys);
			for(Long tick : keys) {
				if(tick == loopStart) {
					mmsg = new ShortMessage(0xb0, MIDIControllers.NRPN_MSB, NRPN_LOOP_START);
					tracks[0].add(new MidiEvent(mmsg, loopStart));
					
					mmsg = new ShortMessage(0xb0, MIDIControllers.DATA_ENTRY, loopCount);
					tracks[0].add(new MidiEvent(mmsg, loopStart));
				}
				else if(tick == loopEnd) {
					mmsg = new ShortMessage(0xb0, MIDIControllers.NRPN_MSB, NRPN_LOOP_END);
					tracks[0].add(new MidiEvent(mmsg, loopEnd));
				}
				
				List<MidiEvent> events = eventMap.getValues(tick);
				if(events != null && !events.isEmpty()) {
					for(MidiEvent event : events) {
						mmsg = event.getMessage();
						int stat = mmsg.getStatus();
						int statGroup = (stat >>> 4) & 0xf;
						int channel = stat & 0xf;
						switch(statGroup) {
						case 0x8:
						case 0x9:
						case 0xA:
						case 0xB:
						case 0xC:
						case 0xD:
						case 0xE:
							tracks[channel].add(event);
							break;
						case 0xF:
							if(stat == 0xff) {
								mbytes = mmsg.getMessage();
								if(mbytes[1] == MIDIMetaCommands.END) continue;
							}
							tracks[0].add(event);
							break;
						}
					}
				}
				
				tickPos = tick;
			}
			
			//Add track ends
			for(int i = 0; i < 16; i++) {
				mmsg = mgen.genTrackEnd();
				tracks[i].add(new MidiEvent(mmsg, tickPos));
			}
			
			return mseq;
		} 
		catch (InvalidMidiDataException e) {
			e.printStackTrace();
		}
		
		return null;
	}
	
	public void writeMIDI(OutputStream out) throws IOException{
		MIDI midi = new MIDI(getSequence());
		midi.serializeTo(out);
	}
	
	public void writeMIDI(String path) throws IOException {
		MIDI midi = new MIDI(getSequence());
		midi.writeMIDI(path);
	}

	private static SEQ2MidiConv stat_conv;
	
	public static class SEQ2MidiConv implements Converter{
		
		private String desc_from = DEFO_ENG_STR;
		private String desc_to = "MIDI Music Sequence (.mid)";

		public String getFromFormatDescription() {return desc_from;}
		public String getToFormatDescription() {return desc_to;}
		public void setFromFormatDescription(String s) {desc_from = s;}
		public void setToFormatDescription(String s) {desc_to = s;}

		public void writeAsTargetFormat(String inpath, String outpath)
				throws IOException, UnsupportedFileTypeException {
			FileBuffer dat = FileBuffer.createBuffer(inpath, false);
			writeAsTargetFormat(dat, outpath);
		}

		public void writeAsTargetFormat(FileBuffer input, String outpath)
				throws IOException, UnsupportedFileTypeException {
			//Use this one.
			try {
				SEQP seq = new SEQP(input, 0L);
				seq.writeMIDI(outpath);
			} 
			catch (InvalidMidiDataException e) {
				e.printStackTrace();
				throw new UnsupportedFileTypeException(e.getMessage());
			}
			
		}

		public void writeAsTargetFormat(FileNode node, String outpath)
				throws IOException, UnsupportedFileTypeException {
			FileBuffer dat = node.loadDecompressedData();
			writeAsTargetFormat(dat, outpath);	
		}

		public String changeExtension(String path) {
			if (path.endsWith(".seq")) return path.replace(".seq", ".mid");
			else return path.replace(".seqp", ".mid");
		}
		
	}
	
	public static SEQ2MidiConv getConverter(){
		if(stat_conv == null) stat_conv = new SEQ2MidiConv();
		return stat_conv;
	}
	
	/*--- Definition ---*/
	
	public static final int DEF_ID = 0x70514553;
	private static final String DEFO_ENG_STR = "PlayStation 1 Sound SEQuence";
	
	private static SEQpDef stat_def;
	
	public static SEQpDef getDefinition(){
		if(stat_def == null) stat_def = new SEQpDef();
		return stat_def;
	}
	
	public static class SEQpDef extends SoundSeqDef{

		private String desc = DEFO_ENG_STR;
		
		public Collection<String> getExtensions() {
			List<String> list = new ArrayList<String>(2);
			list.add("seq"); list.add("seqp");
			return list;
		}

		public String getDescription() {return desc;}
		public int getTypeID() {return DEF_ID;}
		public void setDescriptionString(String s) {desc = s;}
		public String getDefaultExtension() {return "seq";}
		public String toString(){return FileTypeDefinition.stringMe(this);}
	}
	
	/*--- Test ---*/
	
	public static void main(String[] args) 
	{
		try
		{
			String inpath = "C:\\Users\\Blythe\\Documents\\Game Stuff\\PSX\\GameData\\SLPM87176\\BGM\\BGM_022.seqp";
			String outpath = "C:\\Users\\Blythe\\Documents\\Game Stuff\\PSX\\GameData\\SLPM87176_conv\\BGM\\BGM_022.mid";
		
			SEQP seq = new SEQP(inpath);
			seq.writeMIDI(outpath);
			
		}
		catch(Exception e)
		{
			e.printStackTrace();
			System.exit(1);
		}
	}

	
}
