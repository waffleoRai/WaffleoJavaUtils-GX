package waffleoRai_SeqSound.psx;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Sequence;
import javax.sound.midi.Track;

import waffleoRai_SoundSynth.PlayerTrack;
import waffleoRai_SoundSynth.SystemSeqPlayer;

public class SystemSeqpPlayer extends SystemSeqPlayer{

	//Uses the Java Synthesizer API to access system default synth
	//This is in case a VAB of soundfont isn't specified
	//Better synthesis, but less accurate to PSX. 
	
	private Sequence seq;
		
	/*--- Inner Classes ---*/
	
	protected class SeqpSysPlayerTrack implements PlayerTrack
	{
		
		private int idx;
		private boolean trackend;
		private Map<Long, List<MidiMessage>> events;
		
		private int last_b_ctrlr = -1;
		private int last_b_val = -1;
		
		private boolean mute;
				
		public SeqpSysPlayerTrack(Track source, int index)
		{
			//src = source;
			idx = index;
			events = new HashMap<Long, List<MidiMessage>>();
			int ecount = source.size();
			for(int i = 0; i < ecount; i++)
			{
				MidiEvent e = source.get(i);
				long tck = e.getTick()/getResolutionScalingFactor();
				List<MidiMessage> msglist = events.get(tck);
				if(msglist == null)
				{
					msglist = new LinkedList<MidiMessage>();
					events.put(tck, msglist);
				}
				msglist.add(e.getMessage());
			}
		}
		
		public int getIndex()
		{
			return idx;
		}
		
		public void onTick(long tick)
		{
			if(trackend) return;
			List<MidiMessage> msglist = events.get(tick);
			if(msglist == null) return; //Nothing to do here.
			
			//For the 0xBn instructions that come in groups
			//int last_b_ctrlr = -1;
			//int last_b_val = -1;
			//int msb = -1;
			//int lsb = -1; //For pairing
			
			for(MidiMessage msg : msglist)
			{
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
					break;
				case 0x9:
					//Note on
					//System.err.println("Track " + idx + ": Note on");
					if(!mute) channels[chan].noteOn(bytes[1], bytes[2]);
					break;
				case 0xA:
					//Polyphonic key pressure
					//System.err.println("Track " + idx + ": Polyphonic key pressure");
					//System.err.println("0xAn instruction skipped!");
					channels[chan].setPolyPressure(bytes[1], bytes[2]);
					break;
				case 0xB:
					//Control change
					int ctrlr = (int)bytes[1];
					switch (ctrlr)
					{
					case SeqpPlayer.CONTROLLER_DATA_ENTRY:
						//System.err.println("Data! Last ctrlr: 0x" + String.format("%02x", last_b_ctrlr));
						//System.err.println("Last val: 0x" + String.format("%02x", last_b_val));
						if(last_b_ctrlr == SeqpPlayer.CONTROLLER_NRPN)
						{
							if(last_b_val == SeqpPlayer.NRPN_VAL_STARTLOOP)
							{
								//This is loop count
								if(bytes[2] == 0x7F) setLoopCount(0); 
								else setLoopCount((int)bytes[2]);
								//System.err.println("Loop count: " + getLoopCount());
							}
						}
					break;
					case SeqpPlayer.CONTROLLER_NRPN:
						//Loop in SEQp
						if(bytes[2] == SeqpPlayer.NRPN_VAL_STARTLOOP){
							//looptick = tick;
							setLoopTick(tick);
							System.err.println("Track " + idx + ": Loop Start");
						}
						else if(bytes[2] == SeqpPlayer.NRPN_VAL_ENDLOOP){
							//loopme = true;
							setLoopFlag(true);
							System.err.println("Track " + idx + ": Loop End");
						}
						break;
					default:
						//System.err.println("Unknown 0xBn instruction-- Ctrlr: 0x" + String.format("%02x", ctrlr));
						channels[chan].controlChange(bytes[1], bytes[2]);
						break;
					}
					last_b_ctrlr = ctrlr;
					last_b_val = (int)bytes[2];
					break;
				case 0xC:
					//Program change
					//System.err.println("Track " + idx + ": Program change -- " + (int)bytes[1]);
					int pidx = (int)bytes[1];
					channels[chan].programChange(pidx);
					break;
				case 0xD:
					//Channel pressure
					//System.err.println("Track " + idx + ": Channel pressure");
					System.err.println("0xDn instruction skipped!");
					channels[chan].setChannelPressure(bytes[1]);
					break;
				case 0xE:
					//Pitch wheel
					//Scale to 16-bit signed from 14-bit unsigned
					//System.err.println("Track " + idx + ": Pitch wheel");
					int raw = (int)bytes[2] << 7 | (int)bytes[1];
					channels[chan].setPitchBend(raw);
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
						case SeqpPlayer.META_TYPE_TEMPO:
							int t = (Byte.toUnsignedInt(bytes[3]) << 16) | (Byte.toUnsignedInt(bytes[4]) << 8) | Byte.toUnsignedInt(bytes[5]);
							//System.err.println("Tempo to set: 0x" + Integer.toHexString(t));
							setTempo(t);
							break;
						case SeqpPlayer.META_TYPE_TRACKEND:
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

		public void reset()
		{
			trackend = false;
		}

		public void resetTo(long tick, boolean loop) {
			reset();
		}

		public boolean trackEnd() {
			return trackend;
		}

		public void setMute(boolean b) {
			mute = b;
		}

		public boolean isMuted() {
			return mute;
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
	
	public SystemSeqpPlayer(SEQP sequence) throws MidiUnavailableException
	{
		super.setupSynth();
		super.setSequenceName("Anonymous SEQp");
		
		seq = sequence.getSequence();
		super.setTickResolution(seq.getResolution());
		
		Track[] stracks = seq.getTracks();
		tracks = new PlayerTrack[stracks.length];
		for(int i = 0; i < stracks.length; i++)
		{
			tracks[i] = new SeqpSysPlayerTrack(stracks[i], i);
		}
		
		//tpqn = seq.getResolution();
		//mintempo = tpqn * 1000;
		//System.err.println("mintempo = " + mintempo);
		
		setTempo(SeqpPlayer.DEFO_TEMPO);
		
		setLoopCount(-1);
	}

	
	/*--- Getters ---*/
	

	@Override
	public String getTypeInfoString() {
		return "PlayStation 1 SEQp";
	}

	public boolean done(){
		//TODO
		return false;
	}


	@Override
	public void removeListener(Object o) {
		// TODO Auto-generated method stub
		
	}


	
}
