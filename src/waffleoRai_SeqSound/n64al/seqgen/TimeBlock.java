package waffleoRai_SeqSound.n64al.seqgen;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import waffleoRai_SeqSound.n64al.NUSALSeqCmdType;
import waffleoRai_SeqSound.n64al.NUSALSeqCommand;
import waffleoRai_SeqSound.n64al.cmd.NUSALSeqCommandChunk;

class TimeBlock {
	
	public int base_tick = -1;
	public ArrayList<NUSALSeqCommandChunk> main_track;
	public ArrayList<Integer> tick_coords;
	
	public List<NUSALSeqCommandChunk> subroutines;
	
	public ArrayList<TimeBlock> sublevels;

	public TimeBlock(){
		main_track = new ArrayList<NUSALSeqCommandChunk>();
		tick_coords = new ArrayList<Integer>();
		subroutines = new LinkedList<NUSALSeqCommandChunk>();
		sublevels = new ArrayList<TimeBlock>(16);
	}
	
	public void addChunk(int tick, NUSALSeqCommandChunk ch){
		main_track.add(ch);
		tick_coords.add(tick);
		if(base_tick < 0) base_tick = tick;
	}
	
	public int getLastEventTime(){
		if(tick_coords.isEmpty()) return 0;
		return tick_coords.get(tick_coords.size()-1);
	}
	
	public int getEndTick(){
		if(tick_coords.isEmpty()) return base_tick;
		NUSALSeqCommandChunk last = getLastChunk();
		int lasttime = getLastEventTime();
		return lasttime + last.getSizeInTicks();
	}
	
	public NUSALSeqCommandChunk getLastChunk(){
		if(main_track.isEmpty()) return null;
		return main_track.get(main_track.size()-1);
	}
	
	public NUSALSeqCommandChunk getChunkAtTick(int tick){
		int i = 0;
		for(Integer n : tick_coords){
			if(n == tick) break;
			i++;
		}
		if(i >= tick_coords.size()) return null;
		
		return main_track.get(i);
	}
	
	public boolean isEmpty(){
		if(!isMaintrackEmpty()) return false;
		if(sublevels != null){
			for(TimeBlock tb : sublevels){
				if(!tb.isEmpty()) return false;
			}
		}
		return true;
	}
	
	public boolean isMaintrackEmpty(){
		int count = main_track.size();
		for(int i = 0; i < count; i++){
			NUSALSeqCommandChunk chunk = main_track.get(i);
			if(!chunk.isEmpty()) return false;
		}
		return true;
	}
	
	public int cleanEmptyChunks(){
		if(main_track.isEmpty()) return 0;
		ArrayList<NUSALSeqCommandChunk> old_chunks = main_track;
		ArrayList<Integer> old_ticks = tick_coords;
		
		int count = main_track.size();
		main_track = new ArrayList<NUSALSeqCommandChunk>(count+1);
		tick_coords = new ArrayList<Integer>(count + 1);
		for(int i = 0; i < count; i++){
			NUSALSeqCommandChunk chunk = old_chunks.get(i);
			//Is it just an end command?
			if(chunk.isEmpty()) continue;
			NUSALSeqCommand chead = chunk.getChunkHead();
			if(chead.getCommand() == NUSALSeqCmdType.END_READ) continue;
			main_track.add(chunk); tick_coords.add(old_ticks.get(i));
		}
		return main_track.size();
	}
	
	static class TimeBlockList{
		private List<TimeBlock> list;
		
		private TimeBlock current;
		private LinkedList<TimeBlock> queue;
		
		private int current_idx = -1;
		
		public TimeBlockList(){
			queue = new LinkedList<TimeBlock>();
			current = null;
		}
		
		public void setList(List<TimeBlock> l){
			list = l;
			queue.addAll(list);
			current = null;
			pop();
		}
		
		public List<TimeBlock> getBackingList(){return list;}
		
		public TimeBlock getCurrent(){return current;}
		
		public int getCurrentIndex(){return current_idx;}
		
		public int listSize(){
			if(list.isEmpty())return 0;
			return list.size();
		}
		
		public int sizeInTicks(){
			if(list == null || list.isEmpty()) return 0;
			LinkedList<TimeBlock> l = new LinkedList<TimeBlock>();
			l.addAll(list);
			while(!l.isEmpty()){
				TimeBlock last = l.pollLast();
				if(!last.isEmpty()){
					//Grab last chunk.
					NUSALSeqCommandChunk lchunk = last.getLastChunk();
					return lchunk.getSizeInTicks() + last.tick_coords.get(last.tick_coords.size()-1);
				}
			}
			
			return 0;
		}
		
		public boolean pop(){
			if(queue.isEmpty()){
				current = null;
				return false;
			}
			current = queue.pop();
			current_idx++;
			return true;
		}
	}
	
	public static void debug_printTimeBlocks(List<TimeBlock> list){
		if(list == null) return;
		int i = 0;
		for(TimeBlock tb : list){
			System.err.println(String.format("tb%02d", i++));
			System.err.println("-> Subroutines: " + tb.subroutines.size());
			System.err.println("-> Sublevels: " + tb.sublevels.size());
			System.err.println("-> Command Chunks: " + tb.main_track.size());
			int j = 0;
			for(NUSALSeqCommandChunk chunk : tb.main_track){
				System.err.println("\tCommand Chunk @ " + tb.tick_coords.get(j++) + " | Length: " + chunk.getSizeInTicks());
				//System.err.println("\tCommand Chunk @ " + tb.tick_coords.get(j++));
				System.err.println(chunk.toMMLCommand());
			}
		}
	}
	
}
