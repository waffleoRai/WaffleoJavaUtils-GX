package waffleoRai_SeqSound.n64al;

import java.util.List;

import waffleoRai_Utils.MultiValMap;

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
	
}
