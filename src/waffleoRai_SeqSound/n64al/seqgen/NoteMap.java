package waffleoRai_SeqSound.n64al.seqgen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class NoteMap {
	
	private Map<Integer, List<NUSALSeqBuilderNote>> map;
	
	public NoteMap(){
		map = new TreeMap<Integer, List<NUSALSeqBuilderNote>>();
	}
	
	public boolean isEmpty(){return map.isEmpty();}
	public boolean hasNotesAt(int coord){return map.containsKey(coord);}
	
	public List<NUSALSeqBuilderNote> getNotesAt(int coord){
		return map.get(coord);
	}
	
	public NUSALSeqBuilderNote[] getNoteGroupAt(int coord){
		List<NUSALSeqBuilderNote> list = map.get(coord);
		if(list != null){
			int sz = list.size();
			if(sz < 1) return null;
			NUSALSeqBuilderNote[] arr = new NUSALSeqBuilderNote[sz];
			int i = 0;
			for(NUSALSeqBuilderNote n : list) arr[i++] = n;
			return arr;
		}
		return null;
	}
	
	public int getLowestTimeCoord(){
		if(map.isEmpty()) return -1;
		List<Integer> list = new ArrayList<Integer>(map.size());
		list.addAll(map.keySet());
		Collections.sort(list);
		return list.get(0);
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
	
	public boolean addNote(int coord, NUSALSeqBuilderNote note){
		if(coord < 0) return false;
		List<NUSALSeqBuilderNote> list = map.get(coord);
		if(list == null){
			list = new ArrayList<NUSALSeqBuilderNote>(4);
			map.put(coord, list);
		}
		if(list.size() >= 4) return false; //4 voices max!
		list.add(note);
		Collections.sort(list);
		return true;
	}
	
	public void clear(){
		map.clear();
	}
	
	public void clearPhraseAnnotations(){
		int sz = map.size();
		if(sz < 1) return;
		
		List<Integer> list = new ArrayList<Integer>(sz);
		list.addAll(map.keySet());
		for(Integer k : list){
			List<NUSALSeqBuilderNote> notelist = map.get(k);
			if(notelist == null) continue;
			if(notelist.isEmpty()) continue;
			for(NUSALSeqBuilderNote note : notelist){
				note.clearLinkedPhrase();
			}
		}
	}
	
	public NoteMap copyWithoutPhrasedNotes(){
		NoteMap other = new NoteMap();
		int sz = map.size();
		if(sz < 1) return other;
		
		List<Integer> list = new ArrayList<Integer>(sz);
		list.addAll(map.keySet());
		for(Integer k : list){
			List<NUSALSeqBuilderNote> notelist = map.get(k);
			if(notelist == null) continue;
			if(notelist.isEmpty()) continue;
			for(NUSALSeqBuilderNote note : notelist){
				if(!note.linkedToPhrase()){
					other.addNote(k, note);
				}
			}
		}
		return other;
	}
	
	public MusicEventMap condense(){
		MusicEventMap outmap = new MusicEventMap();
		if(map.isEmpty()) return outmap;
		
		int sz = map.size();
		
		List<Integer> list = new ArrayList<Integer>(sz);
		list.addAll(map.keySet());
		Collections.sort(list);
		for(Integer k : list){
			List<NUSALSeqBuilderNote> notelist = map.get(k);
			if(notelist == null || notelist.isEmpty()) continue;
			
			for(NUSALSeqBuilderNote note : notelist){
				if(!note.linkedToPhrase()){
					outmap.addEvent(k, note);
				}
				else{
					if(note.isPhraseStart()){
						NUSALSeqBuilderPhrase phrase = note.getLinkedPhrase();
						outmap.addEvent(k, phrase);
					}
				}
			}
		}
		
		return outmap;
	}

	public Set<Byte> getAllPitches(){
		Set<Byte> list = new TreeSet<Byte>();
		List<Integer> keys = new ArrayList<Integer>(map.size()+1);
		keys.addAll(map.keySet());
		for(Integer k : keys){
			List<NUSALSeqBuilderNote> group = map.get(k);
			for(NUSALSeqBuilderNote note : group){
				list.add(note.getNote());
			}
		}
		return list;
	}
	
}
