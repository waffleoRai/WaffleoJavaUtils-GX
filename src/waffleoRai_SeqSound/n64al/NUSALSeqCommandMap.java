package waffleoRai_SeqSound.n64al;

import java.util.Collections;
import java.util.List;

import waffleoRai_DataContainers.MultiValMap;

public class NUSALSeqCommandMap extends MultiValMap<Integer, NUSALSeqCommand>{
	
	//Map by ticks
	
	public boolean addCommand(int tick, NUSALSeqCommand cmd){
		if(cmd == null || tick < 0) return false;
		return super.addValue(tick, cmd);
	}
	
	public void clearCommands(){super.clearValues();}

	public List<Integer> getTimeCoordinates(){
		return super.getOrderedKeys();
	}
	
	public List<NUSALSeqCommand> getCommandsAt(int tick){
		return super.getValues(tick);
	}
	
	public int addCommands(NUSALSeqCommandMap other){
		if(other == null) return 0;
		
		int ct = 0;
		List<Integer> ocoords = other.getTimeCoordinates();
		for(Integer time : ocoords){
			List<NUSALSeqCommand> list = other.getCommandsAt(time);
			for(NUSALSeqCommand cmd : list){
				super.addValue(time, cmd);
				//System.err.println("NUSALSeqCommandMap.addCommands || Added " + cmd.toMMLCommand() + " @ " + time);
				ct++;
			}
		}
		
		return ct;
	}
	
	public int getLastEventTime(){
		return Collections.max(super.getBackingMap().keySet());
	}
	
}
