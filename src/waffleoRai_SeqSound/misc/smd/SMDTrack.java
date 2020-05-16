package waffleoRai_SeqSound.misc.smd;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import waffleoRai_SoundSynth.PlayerTrack;

public class SMDTrack implements PlayerTrack{
	
	/*--- Instance Variables ---*/
	
	//Storage
	private List<SMDEvent> eventList;
	private int trackID;
	private int chanID;
	
	//Playback
	private long mytick;
	private long ctr; //For id-ing notes
	private boolean iloop; //Loop start found
	
	private SMDPlayer player;
	private Map<Long, List<SMDEvent>> tickmap;
	private boolean muted;
	private boolean track_end;
	
	private Map<Long, Counter> active_notes;

	/*--- Construction ---*/
	
	public SMDTrack(){
		this.eventList = new LinkedList<SMDEvent>();
	}
	
	/*--- Getters ---*/
	
	public SMDEvent getEvent(int i){return eventList.get(i);}
	public int getTrackID(){return trackID;}
	public int getChannelID(){return chanID;}
	public int getNumberEvents(){return this.eventList.size();}
	public SMDPlayer getPlayer(){return player;}
	
	/*--- Setters ---*/
	
	public void addEvent(SMDEvent e){eventList.add(e);}
	public void setTrackID(int tID){this.trackID = tID;}
	public void setChannelID(int cID){this.chanID = cID;}
	public void setPlayer(SMDPlayer p){player = p; mapEvents();}
	
	/*--- Player Init ---*/
	
	private void mapEvents(){

		active_notes = new HashMap<Long, Counter>();
		tickmap = new HashMap<Long, List<SMDEvent>>();
		
		long tick = 0;
		long lastwait = 0;
		int lastnotelen = 0;
		for(SMDEvent e : eventList){
			switch(e.getType()){
			case NA_DELTATIME: tick += e.getWait(); break;
			case WAIT_AGAIN:
				tick += lastwait; break;
			case WAIT_ADD:
				long add = e.getWait();
				lastwait += add;
				tick += lastwait;
				break;
			case WAIT_1BYTE:
			case WAIT_2BYTE:
				lastwait = e.getWait();
				tick += lastwait;
				break;
			default:
				//Map to current tick
				List<SMDEvent> list = tickmap.get(tick);
				if(list == null){
					list = new LinkedList<SMDEvent>();
					tickmap.put(tick, list);
				}
				list.add(e);
				if(e instanceof SMDNoteEvent){
					SMDNoteEvent ne = (SMDNoteEvent)e;
					if(ne.getLength() < 0){
						//Presumably length of previous
						ne.setLength(lastnotelen);
					}
					lastnotelen = ne.getLength();
				}
				break;
			}
		}
		
	}
	
	/*--- Track Control ---*/
	
	private static class Counter{
		public int value;
		
		public Counter(int i){value = i;}
	}
	
	public void onTick(long tick) throws InterruptedException{
		//System.err.println("Track " + this.trackID + ": tick = " + tick);
		
		mytick = tick;
		
		if(!active_notes.isEmpty()){
			SMDSynthChannel c = player.getChannel(chanID);
			List<Long> offnotes = new LinkedList<Long>();
			for(Entry<Long, Counter> e : active_notes.entrySet()){
				if(e.getValue().value-- <= 0){
					offnotes.add(e.getKey());
				}
			}	
			
			for(Long n : offnotes){
				//System.err.println("Track " + trackID + ": Note off -- 0x" + Long.toHexString(n) + " | Tick = " + mytick);
				c.smdNoteOff(n);
				active_notes.remove(n);
			}
		}
		
		if(!muted){
			//Do events
			List<SMDEvent> elist = tickmap.get(mytick);
			if(elist != null){
				for(SMDEvent e : elist){
					//System.err.println("\t" + e.toString());
					//System.err.println("Track " + trackID + ": " + e.toString() + " | tick = " + mytick);
					e.execute(this);
				}
			}
		}
		
		//Check for notes to turn off.
		//System.err.println("\t-->Turning off notes...");
		//System.err.println("\t-->Tick Done!");
	}
	
	public void resetTo(long tick, boolean loop){
		mytick = tick;
		track_end = false; 
		if(!loop)active_notes.clear();
		//if(active_notes != null) active_notes.clear();
	}
	
	public boolean trackEnd(){return track_end;}
	
	public void setMute(boolean b){muted = b;}
	
	public boolean isMuted(){return muted;}
	
	public void gc(){
		mytick = 0; ctr = 0; iloop = false;
		player = null;
		tickmap.clear(); tickmap = null;
		muted = false; track_end = false;
		active_notes.clear(); active_notes = null;
	}
	
	public void clearPlaybackResources(){
		gc();
	}
	
	/*--- Commands ---*/

	public void smdNoteOn(int oct_rel, int note_rel, int velocity, int len) throws InterruptedException{
		if(player == null) return;
		//System.err.println("smdNoteOn || Called!");
		long noteid = ctr++;
		active_notes.put(noteid, new Counter(len-1));
		//System.err.println("smdNoteOn || Note ID: 0x" + Long.toHexString(noteid));
		
		SMDSynthChannel c = player.getChannel(chanID);
		//System.err.println("smdNoteOn || Channel retrieved!");
		c.incrementOctave(oct_rel);
		//System.err.println("smdNoteOn || Octave Shifted: " + oct_rel);
		c.smdNoteOn(noteid, (byte)note_rel, (byte)velocity);
		//System.err.println("smdNoteOn || Note on complete!");
	}
	
	public void onTrackEnd(){
		//TODO There might be issues if tracks loop at different points!
		if(player == null) return;
		if(iloop) player.flagLoop();
		else track_end = true;
	}
	
	public void markLoopPoint(){
		if(player == null) return;
		iloop = true;
		player.setLoop(mytick);
	}
	
	public void setOctave(int value){
		if(player == null) return;
		
		SMDSynthChannel c = player.getChannel(chanID);
		c.setOctave(value);
	}
	
	public void setTempo(int value){
		if(player == null) return;
		//Tempo is in bpm. 
		double micros = 60000000.0/(double)value;
		player.setTempo((int)Math.round(micros));
	}
	
	public void setProgram(int value){
		if(player == null) return;
		player.programChange(chanID, value);
	}
	
	public void setModWheel(int value){
		if(player == null) return;
		//Scale to 16 bits signed
		value -= 128;
		value = (int)Math.round(((double)value/127.0) * (double)0x7FFF);
		
		System.err.println("SMDTrack " + trackID + ": Unimplemented command: Set modulation");
		
		//TODO implement
		//SMDSynthChannel c = player.getChannel(ch);
	}
	
	public void setPitchWheel(int value){
		if(player == null) return;
		
		//Kind enough to put in cents already!
		SMDSynthChannel c = player.getChannel(chanID);
		c.setPitchBendDirect(value);
	}
	
	public void setVolume(int value){
		if(player == null) return;
		
		SMDSynthChannel c = player.getChannel(chanID);
		c.setVolume((byte)value);
	}
	
	public void setExpression(int value){
		if(player == null) return;
		
		SMDSynthChannel c = player.getChannel(chanID);
		c.setExpression((byte)value);
	}
	
	public void setPan(int value){
		if(player == null) return;
		
		SMDSynthChannel c = player.getChannel(chanID);
		c.setPan((byte)value);
	}
	
}
