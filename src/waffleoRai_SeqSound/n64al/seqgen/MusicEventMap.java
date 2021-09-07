package waffleoRai_SeqSound.n64al.seqgen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class MusicEventMap {

	private Map<Integer, List<MusicEvent>> map;
	
	public MusicEventMap(){
		map = new TreeMap<Integer, List<MusicEvent>>();
	}
	
	public List<MusicEvent> getEventsAt(int coord){
		return map.get(coord);
	}
	
	public int[] getTimeCoords(){
		if(map.isEmpty()) return null;
		int sz = map.size();
		int[] arr = new int[sz];
		List<Integer> list = new ArrayList<Integer>(sz);
		list.addAll(map.keySet());
		Collections.sort(list);
		int i = 0;
		for(Integer n : list) arr[i++] = n;
		return arr;
	}

	public boolean addEvent(int coord, MusicEvent me){
		if(coord < 0) return false;
		List<MusicEvent> list = map.get(coord);
		if(list == null){
			list = new ArrayList<MusicEvent>(4);
			map.put(coord, list);
		}
		if(list.size() >= 4) return false; //4 voices max!
		list.add(me);
		return true;
	}
	
	public void clear(){
		map.clear();
	}
	
}
