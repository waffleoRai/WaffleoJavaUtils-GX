package waffleoRai_SeqSound.psx;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.Sequence;
import javax.sound.midi.Track;
import javax.sound.sampled.SourceDataLine;

import waffleoRai_SoundSynth.PlayerTrack;
import waffleoRai_SoundSynth.SequencePlayer;
import waffleoRai_SoundSynth.SynthBank;
import waffleoRai_SoundSynth.SynthProgram;
import waffleoRai_SoundSynth.general.DefaultSynthChannel;

public class SeqpPlayer extends SequencePlayer{
	
	/*--- Constants ---*/
	
	public static final int CONTROLLER_BANKSELECT = 0;
	public static final int CONTROLLER_MODWHEEL = 1;
	public static final int CONTROLLER_DATA_ENTRY = 6;
	public static final int CONTROLLER_VOLUME = 7;
	public static final int CONTROLLER_PAN = 10;
	public static final int CONTROLLER_EXPRESSION = 11;
	
	public static final int CONTROLLER_BANKSELECT_LO = 32;
	public static final int CONTROLLER_MODWHEEL_LO = 33;
	public static final int CONTROLLER_DATA_ENTRY_LO = 38;
	public static final int CONTROLLER_VOLUME_LO = 39;
	public static final int CONTROLLER_PAN_LO = 42;
	public static final int CONTROLLER_EXPRESSION_LO = 43;
	
	public static final int CONTROLLER_DAMPER_PEDAL = 64;
	
	public static final int CONTROLLER_NRPN = 99; //Used for loops in SEQp
	
	public static final int NRPN_VAL_STARTLOOP = 0x14;
	public static final int NRPN_VAL_ENDLOOP = 0x1E;
	
	public static final int META_TYPE_TRACKEND = 0x2F;
	public static final int META_TYPE_TEMPO = 0x51;
	
	public static final int PS1_SAMPLERATE = 44100;
	public static final int PS1_BITDEPTH = 16;
	public static final int DEFO_TEMPO = 500000; //120 bpm
	
	/*--- Instance Variables ---*/
		
	private Sequence seq;
	private SynthBank bank;
	
	private String seqName;
	private String bankName;
	
	/*--- Inner Classes ---*/
	
	protected class SeqpPlayerTrack implements PlayerTrack
	{
		
		private int idx;
		private boolean trackend;
		private Map<Long, List<MidiMessage>> events;
		
		private boolean muted;
				
		public SeqpPlayerTrack(Track source, int index)
		{
			//src = source;
			idx = index;
			events = new HashMap<Long, List<MidiMessage>>();
			int ecount = source.size();
			for(int i = 0; i < ecount; i++)
			{
				MidiEvent e = source.get(i);
				List<MidiMessage> msglist = events.get(e.getTick());
				if(msglist == null)
				{
					msglist = new LinkedList<MidiMessage>();
					events.put(e.getTick(), msglist);
				}
				msglist.add(e.getMessage());
			}
		}
		
		public int getIndex()
		{
			return idx;
		}
		
		public void onTick(long tick) throws InterruptedException
		{
			if(trackend || muted) return;
			List<MidiMessage> msglist = events.get(tick);
			if(msglist == null) return; //Nothing to do here.
			
			//For the 0xBn instructions that come in groups
			int last_b_ctrlr = -1;
			int last_b_val = -1;
			//int msb = -1;
			//int lsb = -1; //For pairing
			
			for(MidiMessage msg : msglist)
			{
				//OH DEAR THIS IS THE FUN PART
				if(trackend) return;
				int stat = msg.getStatus();
				int statfam = (stat & 0xF0) >>> 4;
				int chan = stat & 0xF;
				byte[] bytes = msg.getMessage();
				switch(statfam)
				{
				case 0x8:
					//Note off
					//System.err.println("Track " + idx + ": Note off");
					channels[chan].noteOff(bytes[1], bytes[2]);
					sendNoteOffToListeners(chan, bytes[1]);
					break;
				case 0x9:
					//Note on
					//System.err.println("Track " + idx + ": Note on");
					if(bytes[2] == 0) {channels[chan].noteOff(bytes[1], bytes[2]); sendNoteOffToListeners(chan, bytes[1]);}
					else {channels[chan].noteOn(bytes[1], bytes[2]); sendNoteOnToListeners(chan, bytes[1]);}
					break;
				case 0xA:
					//Polyphonic key pressure
					//System.err.println("Track " + idx + ": Polyphonic key pressure");
					System.err.println("0xAn instruction skipped!");
					break;
				case 0xB:
					//Control change
					int ctrlr = (int)bytes[1];
					switch (ctrlr)
					{
					case CONTROLLER_BANKSELECT:
						//System.err.println("Track " + idx + ": Bank select");
						channels[chan].setBankIndex((int)bytes[2]);
						break;
					case CONTROLLER_MODWHEEL:
						//Right now, not implemented
						System.err.println("Modwheel (MSB) instruction skipped!");
						break;
					case CONTROLLER_MODWHEEL_LO:
						//Right now, not implemented
						System.err.println("Modwheel (LSB) instruction skipped!");
						break;
					case CONTROLLER_DATA_ENTRY:
						//System.err.println("Track " + idx + ": Controller data");
						if(last_b_ctrlr == CONTROLLER_NRPN)
						{
							if(last_b_val == NRPN_VAL_STARTLOOP)
							{
								//This is loop count
								if(bytes[2] == 0x7F) setLoopCount(0);
								else setLoopCount((int)bytes[2]);
							}
						}
						break;
					case CONTROLLER_VOLUME:
						//System.err.println("Track " + idx + ": Volume Change -- 0x" + String.format("%02x", bytes[2]));
						channels[chan].setVolume(bytes[2]);
						sendVolumeToListeners(chan, (double)bytes[2]/(double)0x7F);
						break;
					case CONTROLLER_PAN:
						//System.err.println("Track " + idx + ": Pan Change -- 0x" + String.format("%02x", bytes[2]));
						channels[chan].setPan(bytes[2]);
						sendPanToListeners(chan, bytes[2]);
						break;
					case CONTROLLER_EXPRESSION:
						//System.err.println("Expression (MSB) instruction skipped!");
						//System.err.println("Track " + idx + ": Expression Change -- 0x" + String.format("%02x", bytes[2]));
						channels[chan].setExpression(bytes[2]);
						break;
					case CONTROLLER_NRPN:
						//Loop in SEQp
						if(bytes[2] == NRPN_VAL_STARTLOOP){
							setLoopTick(tick);
							System.err.println("Track " + idx + ": Loop Start");
						}
						else if(bytes[2] == NRPN_VAL_ENDLOOP){
							setLoopFlag(true);
							System.err.println("Track " + idx + ": Loop End");
						}
						break;
					default:
						System.err.println("Unimplemented 0xBn instruction skipped! Ctrlr: 0x" + String.format("%02x", ctrlr));
						break;
					}
					last_b_ctrlr = ctrlr;
					last_b_val = (int)bytes[1];
					break;
				case 0xC:
					//Program change
					//System.err.println("Track " + idx + ": Program change -- " + (int)bytes[1]);
					int pidx = (int)bytes[1];
					SynthProgram prog = bank.getProgram(channels[chan].getCurrentBankIndex(), pidx);
					channels[chan].setProgram(prog);
					sendProgramChangeToListeners(chan, 0, pidx);
					break;
				case 0xD:
					//Channel pressure
					//System.err.println("Track " + idx + ": Channel pressure");
					System.err.println("0xDn instruction skipped!");
					break;
				case 0xE:
					//Pitch wheel
					//Scale to 16-bit signed from 14-bit unsigned
					//System.err.println("Track " + idx + ": Pitch wheel");
					int raw = (int)bytes[2] << 7 | (int)bytes[1];
					raw -= 0x2000;
					double val = (raw/(double)0x1FFF) * (double)0x7FFF;
					short pw = (short)(Math.round(val));
					channels[chan].setPitchWheelLevel(pw);
					sendPitchWheelToListeners(chan, pw);
					break;
				case 0xF:
					//System or meta
					//System.err.println("Track " + idx + ": System message");
					if(stat == 0xFF)
					{
						//System.err.print("System message: ");
						//for(int i = 0; i < bytes.length; i++) System.err.print(String.format("%02x ", bytes[i]));
						//System.err.println();
						int metatype = (int)bytes[1];
						switch(metatype)
						{
						case META_TYPE_TEMPO:
							int t = (Byte.toUnsignedInt(bytes[3]) << 16) | (Byte.toUnsignedInt(bytes[4]) << 8) | Byte.toUnsignedInt(bytes[5]);
							//System.err.println("Tempo to set: 0x" + Integer.toHexString(t));
							setTempo(t);
							break;
						case META_TYPE_TRACKEND:
							trackend = true;
							break;
						}
					}
					else System.err.println("Unknown sysex status byte: 0x"+ String.format("%02x", stat));
					break;
				default:
					System.err.println("Unknown status byte: 0x" + String.format("%02x", stat));
					break;
				}
			}
			
		}

		public void reset(){
			trackend = false;
		}
	
		public void resetTo(long tic, boolean loop) {
			trackend = false;
		}

		public boolean trackEnd() {
			return trackend;
		}

		public void setMute(boolean b) {
			muted = b;
		}

		public boolean isMuted() {
			return muted;
		}
		
		public void clearPlaybackResources(){
			if(events != null){
				for(List<MidiMessage> l : events.values()){
					l.clear();
				}
				events.clear();
			}
		}
		
	}
	
	/*--- Construction ---*/
	
	public SeqpPlayer(SEQP myseq, SynthBank soundbank)
	{
		//setChannelAttenuation(1.0);
		setMasterAttenuation(0.5); //-6dB

		seq = myseq.getSequence();
		bank = soundbank;
		setTickResolution(seq.getResolution());
		
		seqName = "Anonymous SEQ";
		bankName = "Anonymous Bank";
		
		allocateChannels(16);
		for(int i = 0; i < channels.length; i++) channels[i] = new DefaultSynthChannel(PS1_SAMPLERATE, PS1_BITDEPTH);
		
		Track[] ts = seq.getTracks();
		tracks = new PlayerTrack[ts.length];
		for(int i = 0; i < tracks.length; i++)
		{
			tracks[i] = new SeqpPlayerTrack(ts[i], i);
		}
		
		super.setTempo(DEFO_TEMPO);
		super.rewind();
	}
	
	/*--- Util ---*/
	
	protected int saturate(int in)
	{
		if(in > 0x7FFF) return 0x7FFF;
		if(in < -32768) return (int)Short.MIN_VALUE;
		return in;
	}
	
	protected void putNextSample(SourceDataLine target) throws InterruptedException
	{
		int[] samps = nextSample();
		byte[] dat = new byte[4];
		
		dat[0] = (byte)(samps[0] & 0x7F);
		dat[1] = (byte)(samps[0] >>> 8);
		dat[2] = (byte)(samps[1] & 0x7F);
		dat[3] = (byte)(samps[1] >>> 8);
		
		target.write(dat, 0, 4);
	}
	
	/*--- Getters ---*/
	
	public float getSampleRate()
	{
		return PS1_SAMPLERATE;
	}
	
	public int getBitDepth()
	{
		return PS1_BITDEPTH;
	}
	
	public int getChannelCount()
	{
		return 2;
	}
	
	public String getSequenceName(){
		return seqName;
	}
	
	public String getBankName(){
		return bankName;
	}
	
	public String getTypeInfoString(){
		return "PlayStation 1 SEQp Player";
	}
	
	/*--- Setters ---*/
	
	public void setBankName(String s){bankName = s;}
	public void setSequenceName(String s){seqName = s;}
	
	public void setChannelProgram(int ch_idx, int bank, int program){
		//TODO
	}
	
}
