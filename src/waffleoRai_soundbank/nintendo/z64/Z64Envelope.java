package waffleoRai_soundbank.nintendo.z64;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import waffleoRai_Sound.nintendo.Z64Sound;
import waffleoRai_soundbank.nintendo.z64.Z64Bank.Labelable;

public class Z64Envelope extends Labelable{
	
	protected LinkedList<short[]> events;
	protected int terminator = Z64Sound.ENVCMD__UNSET;
	protected int id = -1;
	
	public Z64Envelope(){events = new LinkedList<short[]>();}
	
	public static Z64Envelope newDefaultEnvelope(){
		Z64Envelope env = new Z64Envelope();
		env.addEvent((short)1, (short)32700);
		env.addEvent((short)1000, (short)32700);
		env.addEvent((short)Z64Sound.ENVCMD__ADSR_HANG, (short)0);
		env.addEvent((short)Z64Sound.ENVCMD__ADSR_DISABLE, (short)0);
		return env;
	}
	
	public static float eventAsFloat(short[] event){
		if(event == null || event.length != 2) return Float.NaN;
		int i = 0;
		i = Short.toUnsignedInt(event[0]) << 16;
		i |= Short.toUnsignedInt(event[1]);
		return Float.intBitsToFloat(i);
	}
	
	public static boolean eventValid(short[] event){
		if(event == null || event.length != 2) return false;
		short cmd = event[0];
		short val = event[1];
		//System.err.println("Z64Envelope.eventValid -- Checking validity of " + cmd + ":" + val);
		
		switch(cmd){
		case Z64Sound.ENVCMD__ADSR_DISABLE:
		case Z64Sound.ENVCMD__ADSR_HANG:
		case Z64Sound.ENVCMD__ADSR_RESTART:
			return val == 0;
		case Z64Sound.ENVCMD__ADSR_GOTO:
			return val != 0;
		}
		
		if(cmd > 0){
			if(val < 0) return false;
		}
		//System.err.println("Z64Envelope.eventValid -- Pass1");
		
		//float f = eventAsFloat(event);
		//if(f > -100000.0f && f < 100000.0f ) return false;
		
		//System.err.println("Z64Envelope.eventValid -- Pass2");
		return true;
	}
	
	public static boolean eventTerminal(short[] event){
		if(event == null || event.length != 2) return true;
		short cmd = event[0];
		
		switch(cmd){
		case Z64Sound.ENVCMD__ADSR_DISABLE:
		case Z64Sound.ENVCMD__ADSR_HANG:
		case Z64Sound.ENVCMD__ADSR_RESTART:
			return true;
		}
		
		return false;
	}
	
	public void cleanup(){
		//Removes terminal events not at the end.
		//Removes invalid events
		LinkedList<short[]> list = new LinkedList<short[]>();
		for(short[] event : events){
			if(!eventValid(event)) continue;
			if(eventTerminal(event)) continue;
			list.add(event);
		}
		events = list;
		if(!hasTerminal()) terminator = Z64Sound.ENVCMD__ADSR_HANG;
	}
	
	public boolean envEquals(Z64Envelope other){
		if(other == null) return false;
		if(other == this) return true;
		if(this.terminator != other.terminator) return false;
		if(this.events.size() != other.events.size()) return false;
		
		Iterator<short[]> oitr = other.events.iterator();
		for(short[] event : events){
			short[] oe = oitr.next();
			if(event == null || oe == null) return false;
			if(event.length < 2 || oe.length < 2) return false;
			if(event[0] != oe[0]) return false;
			if(event[1] != oe[1]) return false;
		}
		return true;
	}
	
	public List<short[]> getEvents(){
		//Returns COPY!
		LinkedList<short[]> list = new LinkedList<short[]>();
		list.addAll(events);
		if(hasTerminal()) list.add(new short[]{(short)terminator, 0});
		return list;
	}
	
	public boolean hasTerminal(){
		if(terminator == Z64Sound.ENVCMD__ADSR_DISABLE) return true;
		if(terminator == Z64Sound.ENVCMD__ADSR_HANG) return true;
		if(terminator == Z64Sound.ENVCMD__ADSR_RESTART) return true;
		return false;
	}
	
	public boolean addEvent(short command, short value){
		short[] event = new short[]{command, value};
		if(!eventValid(event)) return false;
		if(eventTerminal(event)){
			//If there already is a "terminal", move it up and replace.
			if(hasTerminal()){
				events.add(new short[]{(short)terminator, 0});
			}
			terminator = (int)command;
			return true;
		}
		
		events.add(event);
		return true;
	}
	
	public void clearEvents(){
		events.clear();
	}
	
	public int eventCount(){
		if(hasTerminal()) return events.size()+1;
		return events.size();
	}

	public Z64Envelope copy(){
		Z64Envelope copy = new Z64Envelope();
		copy.terminator = this.terminator;
		copy.id = this.id;
		for(short[] event : this.events){
			short[] ecpy = new short[2];
			ecpy[0] = event[0];
			ecpy[1] = event[1];
			copy.events.add(ecpy);
		}
		return copy;
	}
	
	public int getID(){return id;}
	public void setID(int val){id = val;}
	
	public void setIDRandom(){
		Random r = new Random();
		id = r.nextInt();
	}
	
}
