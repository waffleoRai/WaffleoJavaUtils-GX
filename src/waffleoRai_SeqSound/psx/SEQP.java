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

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

import waffleoRai_Files.Converter;
import waffleoRai_Files.FileTypeDefinition;
import waffleoRai_Files.tree.FileNode;
import waffleoRai_SeqSound.MIDI;
import waffleoRai_SeqSound.SortableMidiEvent;
import waffleoRai_SeqSound.SoundSeqDef;
import waffleoRai_SeqSound.MIDI.MessageType;
import waffleoRai_Utils.BitStreamer;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;

public class SEQP {
	
	/*--- Constants ---*/
	
	public static final String MAGIC = "pQES";
	public static final String EXTENSION = "seq";
	
	//Note: the 0x00 is the delta time, you dolt
	public static final byte[] INFINITE_LOOP_ST = {(byte)0xB0, 0x63, 0x14, 0x00, 0x06, 0x7F};
	public static final byte[] INFINITE_LOOP_ED = {(byte)0xB0, 0x63, 0x1E};
	
	public static final String FNMETAKEY_BANKUID = "PSX_BNK_ID";
	public static final String FNMETAKEY_BANKPATH = "PSX_BNK_PATH";
	
	/*--- Instance Variables ---*/
	
	private int version;
	
	private int TicksPerQNote;
	private int MicrosecondsPerQNote; //tempo
	private int timeSigNumerator;
	private int timeSigDenominator; //True time sig denom is 2^(this value)
	
	private Sequence contents;
	
	/*--- Construction/Parsing ---*/
	
	public SEQP() throws InvalidMidiDataException
	{
		version = 1;
		TicksPerQNote = 0x1E0;
		//Default tempo: 120 bpm
		MicrosecondsPerQNote = 5000000;
		timeSigNumerator = 4;
		timeSigDenominator = 2; //(2^2)
		contents = new Sequence(Sequence.PPQ, TicksPerQNote, 16);
	}
	
	public SEQP(String filepath) throws IOException, UnsupportedFileTypeException, InvalidMidiDataException
	{
		this(filepath, 0);
	}
	
	public SEQP(String filepath, long stpos) throws IOException, UnsupportedFileTypeException, InvalidMidiDataException
	{
		FileBuffer seq = FileBuffer.createBuffer(filepath, true);
		parseSEQ(seq, stpos);
	}
	
	public SEQP(FileBuffer file, long stpos) throws UnsupportedFileTypeException, InvalidMidiDataException
	{
		parseSEQ(file, stpos);
	}
	
	public static MessageType getMessageType(byte stat)
	{
		int istat = Byte.toUnsignedInt(stat);
		if (istat == 0xFF) return MessageType.META;
		else if(istat == 0xF0 || istat == 0xF7) return MessageType.SYSEX;
		else
		{
			if (BitStreamer.readABit(stat, 7) && (((istat >> 4) & 0xF) != 0xF))
			{
				return MessageType.MIDI;
			}
			else if (!BitStreamer.readABit(stat, 7)) return MessageType.RUNNING;
		}
		
		return null;
	}
	
	private void parseSEQ(FileBuffer myfile, long stpos) throws UnsupportedFileTypeException, InvalidMidiDataException
	{
		//Run checks
		myfile.setEndian(true);
		
		//Read header
		long cpos = myfile.findString(stpos, stpos + 0x10, MAGIC);
		if (cpos != stpos) throw new FileBuffer.UnsupportedFileTypeException();
		cpos += 4;
		
		version = myfile.intFromFile(cpos); cpos += 4;
		TicksPerQNote = Short.toUnsignedInt(myfile.shortFromFile(cpos)); cpos += 2;
		MicrosecondsPerQNote = myfile.shortishFromFile(cpos); cpos += 3;
		timeSigNumerator = Byte.toUnsignedInt(myfile.getByte(cpos)); cpos++;
		timeSigDenominator = Byte.toUnsignedInt(myfile.getByte(cpos)); cpos++;
		//System.err.println("Tempo: 0x" + Integer.toHexString(MicrosecondsPerQNote));
		
		//Prepare sequence
		contents = new Sequence(Sequence.PPQ, TicksPerQNote, 16);
		Track[] tracks = contents.getTracks();
		
		//Add time sig and tempo metadata
		byte[] timesig = {(byte)timeSigNumerator, (byte)timeSigDenominator, 24, 8};
		MetaMessage mmsg = new MetaMessage(0x58, timesig, 4);
		//tracks[0].add(new MidiEvent(mmsg, 0));
		byte[] tempo = {(byte)((MicrosecondsPerQNote >>> 16) & 0xFF),
						(byte)((MicrosecondsPerQNote >>> 8) & 0xFF),
						(byte)(MicrosecondsPerQNote & 0xFF)};
		mmsg = new MetaMessage(0x51, tempo, 3);
		tracks[0].add(new MidiEvent(mmsg, 0));
		
		//Parse the messages. 
		//Note the channel of each message, and distribute to the track with the same index
		
		//End is noted by track end message
		boolean trackend = false;
		long tickPos = 0;
		MidiMessage lastmsg = null;
		
		while(!trackend)
		{
			//System.err.println();
			//1. Get delta time
			int[] delTime = MIDI.getVLQ(myfile, cpos);
			int delta = delTime[0];
			cpos += (long)delTime[1];
			//System.err.print("Delta: 0x" + Integer.toHexString(delta));
			
			//2. Interpret delta time
			tickPos += Integer.toUnsignedLong(delta);
			//System.err.print("\tTick: 0x" + Long.toHexString(tickPos));
			
			//3. Figure out message type
			byte stat = myfile.getByte(cpos);
			cpos++;
			MessageType type = getMessageType(stat);
			if (type == null) throw new FileBuffer.UnsupportedFileTypeException();
			//System.err.print("\tStat: 0x" + String.format("%02x", stat));
			//System.err.print("\tType: " + type.toString());
			
			switch(type)
			{
			case META:
				//Get type
				byte metatype = myfile.getByte(cpos); cpos++;
				//SEQp files do NOT have a length field!
				//I guess it knows depending on the type. 
				// SEQp files should only have 1 of 2 types: 
				//		2F (track end) - 1 Byte (0x00)
				//		51 (tempo change) - 3 bytes
				if (metatype == 0x2F){
					trackend = true;
					for (int i = 0; i < 16; i++)
					{
						byte[] data = {0x00};
						MetaMessage msg = new MetaMessage(0x2F, data, 1);
						tracks[i].add(new MidiEvent(msg, tickPos));
					}
				}
				else if (metatype == 0x51)
				{
					//Next three bytes are the data.
					byte[] data = new byte[3];
					for (int i = 0; i < 3; i++)
					{
						data[i] = myfile.getByte(cpos);
						cpos++;
					}
					//Add to track 0 only?
					MetaMessage msg = new MetaMessage(0x51, data, 3);
					tracks[0].add(new MidiEvent(msg, tickPos));
					lastmsg = msg;
				}
				else
				{
					System.err.println(Thread.currentThread().getName() + " || SEQP.parseSEQ || Illegal Meta Event Encountered -- ");
					System.err.println(Thread.currentThread().getName() + " || SEQP.parseSEQ || Type: 0x" + String.format("%02X", metatype));
					System.err.println(Thread.currentThread().getName() + " || SEQP.parseSEQ || File Offset: 0x" + Long.toHexString(cpos - 1));
					throw new FileBuffer.UnsupportedFileTypeException();
				}
				break;
			case MIDI:
				//Get channel
				int sint = Byte.toUnsignedInt(stat);
				int ch = sint & 0xF;
				int stype = (sint & 0xF0) >>> 4;
				//System.err.print("\tChannel: " + ch);
				//Determine whether we are looking at one or two data bytes
				if (stype == 0xC || stype == 0xD)
				{
					//One byte
					int dByte = Byte.toUnsignedInt(myfile.getByte(cpos)); cpos++;
					//if(stype == 0xC) dByte--; //Program number down?
					ShortMessage msg = new ShortMessage(sint, dByte, 0);
					tracks[ch].add(new MidiEvent(msg, tickPos));
					lastmsg = msg;
				}
				else
				{
					//Two bytes
					int d1 = Byte.toUnsignedInt(myfile.getByte(cpos)); cpos++;
					int d2 = Byte.toUnsignedInt(myfile.getByte(cpos)); cpos++;
					//Note if it's a potential loop point
					if (stype == 0xB && d1 == 0x63)
					{
						if (d2 == 0x14)
						{
							//It's loop start
							//Skip ahead to look for the loop value
							//System.err.println("Loop start found.");
							int val = 0x7F;
							byte nxt = myfile.getByte(cpos + 1);
							if (nxt == 0x06) val = Byte.toUnsignedInt(myfile.getByte(cpos + 2));
							//Drop a marker
							String marker = "SEQp Loop Start (0x" + String.format("%02X", val);
							MetaMessage msg = new MetaMessage(0x06, marker.getBytes(), marker.length());
							tracks[0].add(new MidiEvent(msg, tickPos));
						}
						else if (d2 == 0x1E)
						{
							//It's loop end
							//System.err.println("Loop end found.");
							String marker = "SEQp Loop End";
							MetaMessage msg = new MetaMessage(0x06, marker.getBytes(), marker.length());
							tracks[0].add(new MidiEvent(msg, tickPos));
						}
					}
					ShortMessage msg = new ShortMessage(sint, d1, d2);
					tracks[ch].add(new MidiEvent(msg, tickPos));
					lastmsg = msg;
				}
				break;
			case RUNNING:
				if (lastmsg == null) throw new FileBuffer.UnsupportedFileTypeException();
				//"stat" is actually the first data byte of this message
				int istat = lastmsg.getStatus();
				MessageType lasttype = getMessageType((byte)istat);
				switch(lasttype)
				{
				case META:
					//Only type this should be is 0x51
					if (stat == 0x51)
					{
						//Next three bytes are the data.
						byte[] data = new byte[3];
						for (int i = 0; i < 3; i++)
						{
							data[i] = myfile.getByte(cpos);
							cpos++;
						}
						//Add to track 0 only?
						MetaMessage msg = new MetaMessage(0x51, data, 3);
						tracks[0].add(new MidiEvent(msg, tickPos));
						lastmsg = msg;
					}
					else
					{
						System.err.println(Thread.currentThread().getName() + " || SEQP.parseSEQ || Illegal Meta Event Encountered -- ");
						System.err.println(Thread.currentThread().getName() + " || SEQP.parseSEQ || Type: 0x" + String.format("%02X", stat));
						System.err.println(Thread.currentThread().getName() + " || SEQP.parseSEQ || File Offset: 0x" + Long.toHexString(cpos - 1));
						throw new FileBuffer.UnsupportedFileTypeException();
					}
					break;
				case MIDI:
					//It seems that note-on/note-off can be run together if it's the same note
					int statType = (istat >>> 4) & 0xF;
					int chan = istat & 0xF;
					if (statType == 0xC || statType == 0xD)
					{
						//One byte
						int dByte = Byte.toUnsignedInt(stat);
						ShortMessage msg = new ShortMessage(istat, dByte, 0);
						tracks[chan].add(new MidiEvent(msg, tickPos));
						lastmsg = msg;
					}
					else
					{
						int d1 = Byte.toUnsignedInt(stat);
						int d2 = Byte.toUnsignedInt(myfile.getByte(cpos)); cpos++;
						//For the vel0 = note off trick.
						//Un-comment this if want/need to explicitly turn into note off.
						/*if (statType == 0x9)
						{
							if (d2 == 0)
							{
								//This is interpreted as a "note off vel = 0x40" signal
								istat = 0x90 | chan;
								d2 = 0x40;
							}
						}*/
						ShortMessage msg = new ShortMessage(istat, d1, d2);
						tracks[chan].add(new MidiEvent(msg, tickPos));
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
				System.err.println(Thread.currentThread().getName() + " || SEQP.parseSEQ || Illegal Sysex Event Encountered -- ");
				System.err.println(Thread.currentThread().getName() + " || SEQP.parseSEQ || Type: 0x" + String.format("%02X", stat));
				System.err.println(Thread.currentThread().getName() + " || SEQP.parseSEQ || File Offset: 0x" + Long.toHexString(cpos - 1));
				throw new FileBuffer.UnsupportedFileTypeException();
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
	
	/*--- Serialization ---*/
	
	private FileBuffer serializeData() throws IOException, InvalidMidiDataException
	{
		//There is only one track; no channels.
		//Just dump all midi events together
		List<SortableMidiEvent> allevents = new LinkedList<SortableMidiEvent>();
		Track[] tracks = contents.getTracks();
		if (tracks == null) return null;
		for (int i = 0; i < tracks.length; i++)
		{
			int ecount = tracks[i].size();
			for (int j = 0; j < ecount; j++)
			{
				allevents.add(new SortableMidiEvent(tracks[i].get(j)));
			}
		}
		Collections.sort(allevents);
		
		//Calculate max size needed. May be less due to running status signals
		int tsz = 0;
		for (SortableMidiEvent e : allevents)
		{
			tsz += e.getEvent().getMessage().getLength();
		}
		
		long tickpos = 0;
		MidiMessage lastmsg = null;
		FileBuffer data = FileBuffer.createWritableBuffer("seqp_serialize", tsz, true);
		
		for (SortableMidiEvent e : allevents)
		{
			long rawtime = e.getEvent().getTick();
			int delta = (int)(rawtime - tickpos);
			
			MidiMessage rawmsg = e.getEvent().getMessage();
			if (rawmsg instanceof MetaMessage)
			{
				MetaMessage mmsg = (MetaMessage)rawmsg;
				//See if type is legal
				if (mmsg.getType() != 0x51 && mmsg.getType() != 0x2F){
					System.err.println(Thread.currentThread().getName() + " || SEQP.serializeData || Illegal Meta Event Encountered -- ");
					System.err.println(Thread.currentThread().getName() + " || SEQP.serializeData || Type Code: 0x" + String.format("%02X", mmsg.getType()));
					System.err.println(Thread.currentThread().getName() + " || SEQP.serializeData || Event will not be included in output file. ");
					continue;
				}
				//Add delta time
				byte[] dtime = MIDI.makeVLQ(delta);
				for (byte b : dtime) data.addToFile(b);
				//Is it running? Only qualifier is if the previous status byte was also 0xFF
				boolean rs = false;
				if (lastmsg != null && lastmsg instanceof MetaMessage) rs = true;
				if(!rs)
				{
					//Add status byte
					data.addToFile((byte)0xFF);
				}
				//Add meta type & data - no length field in SEQ
				data.addToFile((byte)mmsg.getType());
				byte[] mdat = mmsg.getData();
				for (byte b : mdat) data.addToFile(b);
				
				//Note
				tickpos += delta;
				lastmsg = mmsg;
			}
			else if (rawmsg instanceof ShortMessage)
			{
				ShortMessage smsg = (ShortMessage)rawmsg;
				//See if running status
				boolean rs = false;
				if (lastmsg != null && lastmsg instanceof ShortMessage)
				{
					if (lastmsg.getStatus() == smsg.getStatus()) rs = true;
					else
					{
						//A note-on command with a velocity of 0 is treated as a note-off command
						if (smsg.getCommand() == ShortMessage.NOTE_OFF)
						{
							ShortMessage lmsg = (ShortMessage)lastmsg;
							if (lmsg.getChannel() == smsg.getChannel() && lmsg.getCommand() == ShortMessage.NOTE_ON)
							{
								//We can just change this to a vel 0 note on to save ever so much space
								rs = true;
								smsg.setMessage(lmsg.getStatus(), smsg.getData1(), 0);
							}
						}
					}
				}
				//Add delta time
				byte[] dtime = MIDI.makeVLQ(delta);
				for (byte b : dtime) data.addToFile(b);
				//Add status if needed
				if (!rs) data.addToFile((byte)smsg.getStatus());
				//Add data
				data.addToFile((byte)smsg.getData1());
				if (smsg.getLength() > 2) data.addToFile((byte)smsg.getData2());
				
				//Note
				tickpos += delta;
				lastmsg = smsg;
			}
			//Otherwise, it is ignored
		}
		
		return data;
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
	
	public static void writeAsSEQ(Sequence sequence, OutputStream output, long loopStartTick, long loopEndTick) throws IOException
	{
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
	
	public Sequence getSequence()
	{
		return contents;
	}
	
	public void writeMIDI(String path) throws IOException
	{
		MIDI midi = new MIDI(contents);
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
