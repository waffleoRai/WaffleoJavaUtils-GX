package waffleoRai_SeqSound.n64al.cmd;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import waffleoRai_SeqSound.n64al.NUSALSeqCommand;
import waffleoRai_SeqSound.n64al.NUSALSeqCommandSource;

public class BasicCommandMap implements NUSALSeqCommandSource{

	private ConcurrentMap<Integer, NUSALSeqCommand> map;
	
	public BasicCommandMap(){
		map = new ConcurrentHashMap<Integer, NUSALSeqCommand>();
	}
	
	public void addCommand(int addr, NUSALSeqCommand cmd){
		map.put(addr, cmd);
	}
	
	public void clear(){map.clear();}
	
	public NUSALSeqCommand getCommandAt(int address) {
		return map.get(address);
	}
	
	public List<Integer> getAllAddresses(){
		List<Integer> list = new ArrayList<Integer>(map.size()+1);
		list.addAll(map.keySet());
		Collections.sort(list);
		return list;
	}
	
	public Map<Integer, NUSALSeqCommand> getSeqLevelCommands(){
		Map<Integer, NUSALSeqCommand> smap = new HashMap<Integer, NUSALSeqCommand>();
		List<Integer> addrs = getAllAddresses();
		for(Integer k : addrs){
			NUSALSeqCommand val = map.get(k);
			if(val == null) continue;
			if(val.seqUsed()) smap.put(k, val);
		}
		return smap;
	}

}
