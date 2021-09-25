package waffleoRai_SeqSound.n64al.cmd;

import java.io.IOException;
import java.io.Writer;
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

	public void printMeTo(Writer out) throws IOException{
		if(map.isEmpty()){
			out.write("<Command map is empty>\n");
			return;
		}
		List<Integer> keys = new ArrayList<Integer>(map.size()+1);
		keys.addAll(map.keySet());
		Collections.sort(keys);
		
		for(Integer k : keys){
			out.write(String.format("%04x\t", k));
			NUSALSeqCommand cmd = map.get(k);
			out.write(cmd.toString() + "\t");
			int bcount = cmd.getSizeInBytes();
			byte[] ser = cmd.serializeMe();
			for(int i = 0; i < bcount; i++){
				out.write(String.format("%02x ", ser[i]));
			}
			out.write("\t");
			
			if(cmd.seqUsed()) out.write("seq ");
			for(int i = 0; i < 16; i++){
				if(cmd.channelUsed(i)) out.write("ch-" + Integer.toHexString(i) + " ");
				for(int j = 0; j < 4; j++){
					if(cmd.layerUsed(i, j)){
						out.write(Integer.toHexString(i) + "-" + j + " ");
					}
				}
			}
			out.write("\n");
		}
		
	}
	
}
