package waffleoRai_SeqSound.n64al.seqgen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import waffleoRai_SeqSound.n64al.NUSALSeq;
import waffleoRai_SeqSound.n64al.NUSALSeqCmdType;
import waffleoRai_SeqSound.n64al.NUSALSeqCommand;
import waffleoRai_SeqSound.n64al.NUSALSeqCommandMap;
import waffleoRai_SeqSound.n64al.NUSALSeqCommands;
import waffleoRai_SeqSound.n64al.cmd.NUSALSeqCommandChunk;
import waffleoRai_SeqSound.n64al.cmd.NUSALSeqCopyCommand;
import waffleoRai_SeqSound.n64al.cmd.NUSALSeqNoteCommand;
import waffleoRai_SeqSound.n64al.cmd.NUSALSeqWaitCommand;
import waffleoRai_SeqSound.n64al.cmd.ChannelCommands.*;
import waffleoRai_SeqSound.n64al.cmd.FCommands.*;
import waffleoRai_SeqSound.n64al.seqgen.TimeBlock.TimeBlockList;

public class NUSALSeqBuilderChannel {
	
	//TODO adjust channel transpose to most extreme val in all layers before compression to aid in pattern recognition?
	
	/*--- Constants ---*/
	
	private static final int TPEVENT_IDX_AMT = 0;
	private static final int TPEVENT_IDX_TICK = 1;
	private static final int TPEVENT_IDX_MINNOTE = 2;
	private static final int TPEVENT_IDX_MAXNOTE = 3;

	/*--- Instance Variables ---*/
	
	private int index;
	
	private NUSALSeqCommandMap channelmap;
	private Map<NUSALSeqCmdType, NUSALSeqCommand> default_params;
	
	private NUSALSeqBuilder parent;
	private NUSALSeqBuilderLayer[] layers;
	private int[] last_gates;
	
	private int transpose = 0;
	private LinkedList<int[]> transpose_events; //amt,tick,min note since, max note since
	private Deque<UnassignedNote> extraNotes; //If all layers are full, this is wait queue.
	
	/*--- Init ---*/	

	public NUSALSeqBuilderChannel(int ch_idx, NUSALSeqBuilder parent){
		index = ch_idx;
		this.parent = parent;
		layers = new NUSALSeqBuilderLayer[4]; //Allocates them when they are used.
		channelmap = new NUSALSeqCommandMap();
		default_params = new HashMap<NUSALSeqCmdType, NUSALSeqCommand>();
		last_gates = new int[4]; Arrays.fill(last_gates, 100);
		transpose_events = new LinkedList<int[]>();
		extraNotes = new LinkedList<UnassignedNote>();
	}
	
	public void reset(){
		channelmap.clearValues();
		for(int i = 0; i < 4; i++){layers[i].reset(); layers[i] = null;}
	}
	
	/*--- Getters ---*/
	
	public int getIndex(){return index;}
	public NUSALSeqBuilder getParent(){return parent;}
	
	public int voicesOn(){
		int c = 0;
		for(int i = 0; i < 4; i++){
			if(layers[i] == null) continue;
			if(layers[i].noteActive()) c++;
		}
		return c;
	}
	
	/*--- Setters ---*/
	
	public void addChannelCommandAtTick(int tick, NUSALSeqCommand cmd){
		if(cmd == null || tick < 0) return;
		channelmap.addCommand(tick, cmd);
	}
	
	public void addLayerCommandAtTick(int tick, int ly, NUSALSeqCommand cmd){
		if(cmd == null || tick < 0) return;
		if(ly < 0 || ly >= 4) return;
		if(layers[ly] == null){
			layers[ly] = new NUSALSeqBuilderLayer(ly, this);
		}
		layers[ly].addCommand(tick, cmd);
	}
	
	public void addChannelCommands(NUSALSeqCommandMap tickmap){
		if(tickmap == null) return;
		channelmap.addCommands(tickmap);
	}
	
	public void addLayerCommands(int ly, NUSALSeqCommandMap tickmap){
		if(tickmap == null) return;
		if(ly < 0 || ly >= 4) return;
		if(layers[ly] == null){
			layers[ly] = new NUSALSeqBuilderLayer(ly, this);
		}
		layers[ly].addCommands(tickmap);
	}
	
	public void addDefaultParam(NUSALSeqCommand cmd){
		if(cmd == null) return;
		default_params.put(cmd.getCommand(), cmd);
	}
	
	/*--- Control ---*/
	
	private void checkTranspose(int tick, byte midinote){
		byte minmid = (byte)(NUSALSeq.MIN_NOTE_MIDI + transpose);
		byte maxmid = (byte)(NUSALSeq.MAX_NOTE_MIDI + transpose);
		int[] lasttp = transpose_events.peekLast();
		if(midinote < minmid){
			//Get last event.
			int diff = (int)midinote - NUSALSeq.MIN_NOTE_MIDI;
			if(diff > 0) diff = 0; //It's in range of default.
			if(lasttp == null || lasttp[TPEVENT_IDX_AMT] > 0){
				int[] tp = new int[]{diff, tick, (int)midinote, (int)midinote};
				transpose_events.add(tp);
			}
			else{
				//Check if this event can be pulled down to cover this note.
				//If so, update.
				//If not, new event.
				int lastmax = lasttp[TPEVENT_IDX_MAXNOTE];
				if(lastmax > (NUSALSeq.MAX_NOTE_MIDI + diff)){
					//No, lastmax will not work in this range
					int[] tp = new int[]{diff, tick, (int)midinote, (int)midinote};
					transpose_events.add(tp);
				}
				else{
					//Push down existing event.
					lasttp[TPEVENT_IDX_AMT] = diff;
					lasttp[TPEVENT_IDX_MINNOTE] = (int)midinote;
				}
			}
			transpose = diff;
		}
		else if(midinote > maxmid){
			int diff = (int)midinote - NUSALSeq.MAX_NOTE_MIDI;
			if(diff < 0) diff = 0; //It's in range of default.
			if(lasttp == null || lasttp[TPEVENT_IDX_AMT] < 0){
				int[] tp = new int[]{diff, tick, (int)midinote, (int)midinote};
				transpose_events.add(tp);
			}
			else{
				//Check if this event can be pulled up to cover this note.
				//If so, update.
				//If not, new event.
				int lastmin = lasttp[TPEVENT_IDX_MINNOTE];
				if(lastmin > (NUSALSeq.MIN_NOTE_MIDI + diff)){
					//No, lastmax will not work in this range
					int[] tp = new int[]{diff, tick, (int)midinote, (int)midinote};
					transpose_events.add(tp);
				}
				else{
					//Push up existing event.
					lasttp[TPEVENT_IDX_AMT] = diff;
					lasttp[TPEVENT_IDX_MAXNOTE] = (int)midinote;
				}
			}
			transpose = diff;
		}
		else{
			//Just mark in last event.
			if(lasttp != null){
				int imid = (int)midinote;
				if(imid < lasttp[TPEVENT_IDX_MINNOTE]) lasttp[TPEVENT_IDX_MINNOTE] = imid;
				else if(imid > lasttp[TPEVENT_IDX_MAXNOTE]) lasttp[TPEVENT_IDX_MAXNOTE] = imid;
				transpose = lasttp[TPEVENT_IDX_AMT];	
			}
		}
	}
	
	private int nextOpenVoice(){
		//Returns -1 if none available
		for(int i = 0; i < 4; i++){
			if(layers[i] != null){
				if(!layers[i].noteActive()) return i;
			}
			else{
				//Generate it, then it's available.
				layers[i] = new NUSALSeqBuilderLayer(i, this);
				return i;
			}
		}
		return -1;
	}
	
	private void sendNoteOn_internal(int tick, byte midinote, byte velocity, int gate, int tp){
		int ly = nextOpenVoice();
		if(ly >= 0){
			//Okay! We can assign it.
			//Check gate, if needed.
			if(gate < 0) gate = last_gates[ly];
			//Add to layer
			layers[ly].noteOn(tick, midinote, velocity, (byte)gate, tp);
			//Save gate.
			last_gates[ly] = gate;
		}
		else{
			System.err.println("NUSALSeqBuilderChannel.sendNoteOn || WARNING -- "
					+ "Channel " + index + " >4 simultaneous voices @ tick " + tick);
			System.err.println("\tNext note to end will be shortened to accommodate extra note.");
			//Put in unassigned queue
			UnassignedNote un = new UnassignedNote();
			un.midinote = midinote;
			un.transpose = tp;
			un.velocity = velocity;
			un.gate = gate;
			un.start_tick = tick;
			extraNotes.add(un);
		}
	}
	
	public void sendNoteOn(int tick, byte midinote, byte velocity){
		//This version uses previous gate from assigned layer.
		sendNoteOn(tick, midinote, velocity, -1);
	}
	
	public void sendNoteOn(int tick, byte midinote, byte velocity, int gate){
		//Check if MIDI note is out of current note range and adjust transpose...
		checkTranspose(tick, midinote);
		sendNoteOn_internal(tick, midinote, velocity, gate, transpose);
	}
	
	public void sendNoteOff(int tick, byte midinote){
		//Don't forget to check unassigned queue
		//1. Find layer that is playing this note.
		int ly = -1;
		for(int i = 0; i < 4; i++){
			if(layers[i] != null){
				if(layers[i].currentOnNote() == midinote){
					ly = i;
					break;
				}
			}
		}
		
		if(ly >= 0){
			//2-1. Note off if existing layer.
			layers[ly].noteOff(tick);
			
			//2-2. If unassigned queue has pending notes, forward to voice and shorten just ended note
			if(extraNotes != null && !extraNotes.isEmpty()){
				NUSALSeqNoteCommand lastnote = layers[ly].getLastNote();
				UnassignedNote un = extraNotes.pop();
				int shortamt = tick - un.start_tick;
				lastnote.shortenTimeBy(shortamt);
				sendNoteOn_internal(un.start_tick, un.midinote, un.velocity, un.gate, un.transpose);
				if(un.end_tick >= 0) sendNoteOff(un.end_tick, un.midinote);
			}
		}
		else{
			//3. Check unassigned queue if not
			if(extraNotes != null){
				for(UnassignedNote un : extraNotes){
					if(un.midinote == midinote){
						un.end_tick = tick;
					}
				}	
			}
		}
		
	}
	
	/*--- Inner Classes ---*/
	
	private static class UnassignedNote{
		public byte midinote;
		public int transpose;
		public byte velocity;
		public int gate; //-1 means take previous gate value when it is assigned
		public int start_tick;
		public int end_tick = -1; //Only used if note ends before it is assigned.
	}
	
	private static class ChannelBuildContext{
		
		public NUSALSeqCommandChunk chunk;
		public Set<NUSALSeqCmdType> usedcmds;
		public Map<NUSALSeqCmdType, NUSALSeqCommand> param_state;
		
		public LinkedList<Integer> sorted_entries;
		public int lyr_tsec_count = 0; //Max tsecs used by layers
		
		public NUSALSeqCommandMap channelmap_out;
		
		public NUSALSeqCommand last = null;
		public NUSALSeqCommand elapser = null;
		public NUSALSeqCommand cur = null;
		public int last_tick = 0;
		public int tlen = 0;
		public boolean endflag = false;
		public boolean rwaitflag = false;
		public LinkedList<NUSALSeqCommand> keepcmds;
		
		public boolean ch_auto_init = true;
		public boolean init_mode = false;
		
		public Set<NUSALSeqCommandChunk> allsubs;
		public TimeBlock current_block;
		
		public void loadParamStateToChunk(){
			if(param_state != null && !param_state.isEmpty()){
				List<NUSALSeqCmdType> keys = new ArrayList<NUSALSeqCmdType>(param_state.size());
				keys.addAll(param_state.keySet());
				Collections.sort(keys); //This just keeps the order consistent.
				for(NUSALSeqCmdType t : keys){
					if(usedcmds.contains(t)) continue;
					cur = new NUSALSeqCopyCommand(param_state.get(t));
					addCur();
				}
			}
		}
		
		public void addCur(){
			if(cur.isChunk()){
				if(cur.getChunkHead() == null) return;
				if(last != null){
					last.setSubsequentCommand(cur.getChunkHead());
				}
			}
			else{
				if(last != null){
					last.setSubsequentCommand(cur);
				}
			}
			chunk.addCommand(cur);
			cur.setTickAddress(last_tick);
			channelmap_out.addValue(last_tick, cur);
			last = cur;
			
			if(ch_auto_init && !init_mode){
				//Push label of THIS command to front of chunk.
				String lbl = cur.getLabel();
				if(lbl != null){
					cur.setLabel(null);
					chunk.setLabel(lbl);
					chunk.getChunkHead().setLabel(lbl);
				}
				ch_auto_init = false;
			}
		}
		
		public void addEndRead(){
			cur = new C_EndRead();
			if(last != null) last.setSubsequentCommand(cur);
			chunk.addCommand(cur);
			last = null;
		}
		
		public void nextChunk(int tick){
			addEndRead();
			chunk = new NUSALSeqCommandChunk();
			ch_auto_init = true;
			if(current_block != null){
				current_block.addChunk(tick, chunk);
			}
		}
		
	}
	
	/*--- Build ---*/
	
	private void applyTransposition(){
		//We'll just edit the notes from the maps directly.
		NUSALSeqCommandMap[] lmaps = new NUSALSeqCommandMap[4];
		for(int i = 0; i < 4; i++){
			if(layers[i] != null) lmaps[i] = layers[i].getTickMap(); 
		}
				
		//Extract the tick lists to poppable queues...
		ArrayList<LinkedList<Integer>> lticks = new ArrayList<LinkedList<Integer>>(4);
		for(int i = 0; i < 4; i++){
			LinkedList<Integer> llist = new LinkedList<Integer>();
			lticks.add(llist);
			if(lmaps[i] != null){
				llist.addAll(lmaps[i].getOrderedKeys());
			}
		}
		
		if(transpose_events == null || transpose_events.isEmpty()){
			//Just scan all.
			for(int i = 0; i < 4; i++){
				if(lmaps[i] != null){
					LinkedList<Integer> llist = lticks.get(i);
					for(Integer t : llist){
						List<NUSALSeqCommand> cmdlist = lmaps[i].getCommandsAt(t);
						for(NUSALSeqCommand cmd : cmdlist){
							if(cmd instanceof NUSALSeqNoteCommand){
								NUSALSeqNoteCommand ncmd = (NUSALSeqNoteCommand)cmd;
								ncmd.setEnvironmentalPitchShift(0);
							}
						}
					}
				}
			}
			return;
		}

		//int tp = 0;
		//int tick = 0;
		int next_tick = -1; //Tick of next tp event
		while(!transpose_events.isEmpty()){
			int[] tevent = transpose_events.pop();
			if(transpose_events.isEmpty()) next_tick = -1;
			else next_tick = transpose_events.peek()[0];
			
			channelmap.addCommand(tevent[TPEVENT_IDX_TICK], new C_C_Transpose(tevent[TPEVENT_IDX_AMT]));
			
			for(int i = 0; i < 4; i++){
				if(lmaps[i] != null){
					LinkedList<Integer> llist = lticks.get(i);
					while(!llist.isEmpty() && ((next_tick < -1)||(llist.peek() < next_tick))){
						int t = llist.pop();
						List<NUSALSeqCommand> cmdlist = lmaps[i].getCommandsAt(t);
						for(NUSALSeqCommand cmd : cmdlist){
							if(cmd instanceof NUSALSeqNoteCommand){
								NUSALSeqNoteCommand ncmd = (NUSALSeqNoteCommand)cmd;
								ncmd.setEnvironmentalPitchShift(tevent[TPEVENT_IDX_AMT]);
							}
						}
					}
				}
			}
		}
	}
	
	private static void scanTickCommands(ChannelBuildContext ctxt, List<NUSALSeqCommand> tickcmds, boolean sub){
		ctxt.usedcmds.clear(); //ctxt.keepcmds.clear();
		ctxt.elapser = null; ctxt.endflag = false;
		ctxt.rwaitflag = false;
		
		for(NUSALSeqCommand cmd : tickcmds){
			NUSALSeqCmdType ctype = cmd.getCommand();
			if(!ctype.flagSet(NUSALSeqCommands.FLAG_CHVALID)) continue; //Invalid. Skip.
			if(ctype.flagSet(NUSALSeqCommands.FLAG_ONCEPERTICK) && ctxt.usedcmds.contains(ctype)) continue;
			
			if(ctype.flagSet(NUSALSeqCommands.FLAG_ISWAIT)){
				//If the wait is 0, it's probably a dummy and something refs it.
				if(cmd.getParam(0) <= 0){
					ctxt.elapser = cmd;
					ctxt.tlen = 0;
					ctxt.rwaitflag = true;
				}
				else continue;
			}
			else if(ctype.flagSet(NUSALSeqCommands.FLAG_TAKESTIME)){
				if(ctxt.elapser == null){
					ctxt.elapser = cmd;
					ctxt.tlen = ctxt.elapser.getSizeInTicks();
				}
			}
			else if(ctype.flagSet(NUSALSeqCommands.FLAG_CALL)){
				//Find reference and add as sub.
				//Also check if takes time.
				NUSALSeqCommand calltarg = cmd.getBranchTarget();
				if(calltarg != null){
					//Determine whether target takes time and is a chunk.
					if(calltarg instanceof NUSALSeqCommandChunk){
						NUSALSeqCommandChunk tchunk = (NUSALSeqCommandChunk)calltarg;
						if(tchunk.getChildCount() < 3){
							//I'll keep it simple for now?
							NUSALSeqCommand cmdone = tchunk.getChunkHead();
							NUSALSeqCmdType ctype2 = cmdone.getCommand();
							if(!ctype2.flagSet(NUSALSeqCommands.FLAG_ISWAIT)) ctxt.keepcmds.add(cmd);
						}
						else{
							if(ctxt.allsubs != null && ctxt.current_block != null){
								if(!ctxt.allsubs.contains(tchunk)){
									ctxt.allsubs.add(tchunk);
									ctxt.current_block.subroutines.add(tchunk);
								}
							}
							if(calltarg.getSizeInTicks() > 0){
								if(ctxt.elapser == null){
									ctxt.elapser = cmd;
									ctxt.tlen = ctxt.elapser.getSizeInTicks();
								}
							}
							else ctxt.keepcmds.add(cmd);	
						}
					}
				}
				else{
					//Add as normal, I guess...
					//A broken link isn't good though.
					System.err.println("NUSALSeqBuilderChannel.scanTickCommands || WARNING: Call command has broken link!");
					ctxt.keepcmds.add(cmd);
				}
			}
			else if(ctype == NUSALSeqCmdType.END_READ){
				ctxt.endflag = true;
			}
			else if(ctype.flagSet(NUSALSeqCommands.FLAG_PARAMSET)){
				//Here, it has already passed the used check.
				//Put here to save the state.
				if(ctxt.param_state != null){
					ctxt.param_state.put(ctype, cmd);
				}
				ctxt.keepcmds.add(cmd);
			}
			else{
				ctxt.keepcmds.add(cmd);
			}
			
			if(ctype.flagSet(NUSALSeqCommands.FLAG_ONCEPERTICK)) ctxt.usedcmds.add(ctype);
			if(ctxt.endflag) break;
		}
	}
	
	public static NUSALSeqCommandChunk buildChannelSubroutine(NUSALSeqCommandMap tickmap){
		/*
		 * The following assumptions are made:
		 * 	-> All events for this channel have already been assigned as-is. That is,
		 * 		compression has been done and subroutines defined.
		 *  -> All jumps/calls/branches already have their target referenced. Subroutines
		 *  	are pre-built with another method.
		 *  -> The above includes layer starts.
		 *  -> Call targets are chunks of commands - this way the sub's tick length can be gauged.
		 * 
		 */
		
		if(tickmap == null) return null;
		ChannelBuildContext ctxt = new ChannelBuildContext();
		ctxt.chunk = new NUSALSeqCommandChunk();
		ctxt.usedcmds = new HashSet<NUSALSeqCmdType>();
		ctxt.channelmap_out = new NUSALSeqCommandMap();
		List<Integer> ticklist = tickmap.getOrderedKeys();
		
		ctxt.keepcmds = new LinkedList<NUSALSeqCommand>();
		for(Integer k : ticklist){
			if(ctxt.rwaitflag){
				//Adjust the time for the elapser delay.
				int tdiff = k - ctxt.last_tick;
				if(tdiff > 0){
					if(ctxt.elapser instanceof NUSALSeqWaitCommand){
						NUSALSeqWaitCommand wcmd = (NUSALSeqWaitCommand)ctxt.elapser;
						if(tdiff < 16) wcmd.toChannelDelta();
						wcmd.setParam(0, tdiff);
						ctxt.last_tick = k;
					}
				}
			}
			if(ctxt.endflag) break;
			List<NUSALSeqCommand> tickcmds = tickmap.getCommandsAt(k);
			ctxt.keepcmds.clear();
			
			//Determine which commands to keep (eliminate invalid or redundant ones)
			scanTickCommands(ctxt, tickcmds, true);
			
			if(ctxt.keepcmds.isEmpty() && ctxt.elapser == null) continue; //All commands for this tick have been pruned.
			
			//Add delay from last tick
			int tdiff = k - ctxt.last_tick;
			if(tdiff > 0){
				if(tdiff < 16) ctxt.cur = new C_C_DeltaTime(tdiff);
				else ctxt.cur = new C_Wait(tdiff);
				ctxt.addCur();
			}
			
			//List of on-tick commands
			ctxt.last_tick = k;
			for(NUSALSeqCommand cmd : ctxt.keepcmds){
				ctxt.cur = cmd;
				ctxt.addCur();
			}
			
			//Multi-tick command
			if(ctxt.elapser != null){
				ctxt.cur = ctxt.elapser;
				ctxt.addCur();
				ctxt.last_tick += ctxt.tlen;
			}
		}
		
		ctxt.addEndRead();
		
		return ctxt.chunk;
	}

	private void addLayerStartCommands(ChannelBuildContext ctxt, TimeBlockList[] lblocks){
		for(int i = 0; i < 4; i++){
			if(lblocks[i] != null){
				List<TimeBlock> blist = lblocks[i].getBackingList();
				int tsec = 0;
				for(TimeBlock ltb : blist){
					if(ltb == null) System.err.println("NUSALSeqBuilderChannel.addLayerStartCommands || Unexpected null timeblock: Channel " + index + ", layer " + i);
					int chunk_ct = ltb.main_track.size();
					if(chunk_ct < 1) {
						tsec++; continue;
					}
					
					//First block
					int ctime = 0;
					NUSALSeqCommand lchunk = null;
					NUSALSeqCommand lstart = null;
					for(int j = 0; j < chunk_ct; j++){
						ctime = ltb.tick_coords.get(j);
						lchunk = ltb.main_track.get(j);
						lchunk.getChunkHead().setLabel(String.format("lyr%02d-%01d_tsec%02d_%03d", index, i, tsec, j));
						lstart = new C_C_StartLayer(i, -1);
						lstart.setReference(lchunk);
						
						if(j == 0){
							if(ctime == ltb.base_tick){
								//Skip for now. It will be added on tb init.
								continue;
							}
						}
						channelmap.addCommand(ctime, lstart);
					}
					tsec++;
				}
			}
		}
	}
	
	private void initChannelChunk(ChannelBuildContext ctxt, TimeBlockList[] lblocks, int tsec){
		ctxt.init_mode = true;
		NUSALSeqCommand cmd = new C_C_SetShortNotesOff();
		ctxt.chunk.addCommand(cmd); ctxt.last = cmd;
		
		//Layer addresses. (Also save blocks to time block)
		//Grab all chunks for this timeblock and map to channel map
		for(int i = 0; i < 4; i++){
			if(lblocks[i] != null){
				TimeBlock ctb = lblocks[i].getCurrent();
				if(ctb == null) continue;
				
				int chunk_ct = ctb.main_track.size();
				if(chunk_ct < 1) continue;
				
				//First block
				int ctime = ctb.tick_coords.get(0);
				NUSALSeqCommand lchunk = ctb.main_track.get(0);
				if(ctime == ctxt.last_tick){
					ctxt.cur = new C_C_StartLayer(i, -1);
					ctxt.cur.setReference(lchunk);
					ctxt.addCur();
					continue;
				}
			}
		}
		
		//Init params.
		ctxt.loadParamStateToChunk();
		ctxt.init_mode = false;
	}
	
	private void adjustMistrackedReferences(ChannelBuildContext ctxt, NUSALSeqCommandChunk chunk){
		//TODO
		List<NUSALSeqCommand> cmdlist = chunk.getCommands();
		for(NUSALSeqCommand cmd : cmdlist){
			if(!cmd.isChunk()){
				NUSALSeqCmdType ctype = cmd.getCommand();
				if(ctype.flagSet(NUSALSeqCommands.FLAG_JUMP)){
					NUSALSeqCommand ref = cmd.getBranchTarget();
					if(ref != null){
						ctype = ref.getCommand();
						if(!ctype.flagSet(NUSALSeqCommands.FLAG_CHVALID)){
							//Try to find alternative.
							int tick = ref.getTickAddress();
							while(tick >= 0){
								//Find channel command nearest to tick.
								if(ctxt.channelmap_out.containsKey(tick)){
									List<NUSALSeqCommand> tcmds = ctxt.channelmap_out.getCommandsAt(tick);
									if(!tcmds.isEmpty()){
										cmd.setReference(tcmds.get(0));
										break;
									}
								}
								tick--;
							}
						}
					}
					else System.err.println("WARNING: Jump command has broken link!");
				}
			}
			else{
				adjustMistrackedReferences(ctxt, (NUSALSeqCommandChunk)cmd);
			}
		}
	}
	
	private void tieupTimeblock(ChannelBuildContext ctxt, TimeBlockList[] lblocks, int tsec){
		if(ctxt.current_block.isMaintrackEmpty()){
			//Just layers - no ch commands
			//Add an init?
			initChannelChunk(ctxt, lblocks, tsec-1);
			ctxt.chunk.getChunkHead().setLabel(String.format("ch%02d_tsec%02d", index, tsec));
		}
		
		//Add delay if needed.
		int cend = ctxt.current_block.base_tick;
		int lend = 0;
		for(TimeBlock ltb : ctxt.current_block.sublevels){
			lend = ltb.getEndTick();
			if(lend > cend) cend = lend;
		}
		
		int tdiff = cend - ctxt.last_tick;
		if(tdiff > 0){
			if(tdiff < 16) ctxt.cur = new C_C_DeltaTime(tdiff);
			else ctxt.cur = new C_Wait(tdiff);
			ctxt.addCur();
		}
	}
	
	private void nextTimeblock(ChannelBuildContext ctxt, TimeBlockList[] lblocks, List<TimeBlock> blocks, int k_tick){
		int tsec = blocks.size();
		while((!ctxt.sorted_entries.isEmpty()) && ((k_tick < 0)||(k_tick >= ctxt.sorted_entries.peekFirst()))){
			//Tie up previous time block
			if(ctxt.current_block != null){
				if(!ctxt.current_block.isEmpty()){
					tieupTimeblock(ctxt, lblocks, tsec-1);
				}
			}
			
			if(k_tick < 0 && tsec >= ctxt.lyr_tsec_count) return;
			
			int splitpt = ctxt.sorted_entries.pop();
			ctxt.current_block = new TimeBlock();
			ctxt.current_block.base_tick = splitpt;
			blocks.add(ctxt.current_block);
			ctxt.nextChunk(splitpt);
		
			//Add layer blocks
			TimeBlock ltb = null;
			for(int v = 0; v < 4; v++){
				//System.err.println("ch " + index + " ly " + v);
				if(lblocks[v] != null){
					if(lblocks[v].pop()){
						ltb = lblocks[v].getCurrent();
						if(!ltb.isEmpty()) ctxt.current_block.sublevels.add(ltb);
						//System.err.println("ch " + index + " ly " + v + " tb " + lblocks[v].getCurrentIndex());
					}
				}
			}
			ctxt.last_tick = splitpt;
			tsec++;
		}
		if(k_tick < 0) tieupTimeblock(ctxt, lblocks, tsec-1);
	}
	
	public List<TimeBlock> buildChannel(int max_layer_rest, Set<Integer> entry_points, NUSALSequenceCompressor compressor){
		if(channelmap == null) return null;

		ChannelBuildContext ctxt = new ChannelBuildContext();
		List<TimeBlock> blocks = new LinkedList<TimeBlock>();
		ctxt.sorted_entries = new LinkedList<Integer>();
		if(entry_points != null){
			entry_points.remove(0);
			ctxt.sorted_entries.addAll(entry_points);
			Collections.sort(ctxt.sorted_entries);
		}
		
		//BEFORE layer build, adjust transposition.
		applyTransposition();
		
		//build layers...
		TimeBlockList[] lblocks = new TimeBlockList[4];
		for(int i = 0; i < 4; i++){
			if(layers[i] != null){
				lblocks[i] = new TimeBlockList();
				lblocks[i].setList(layers[i].buildLayer(max_layer_rest, entry_points, compressor));
				int lsz = lblocks[i].listSize();
				if(lsz > ctxt.lyr_tsec_count) ctxt.lyr_tsec_count = lsz;
			}
		}
		
		//Add layer start commands
		addLayerStartCommands(ctxt, lblocks);
		
		//Main channel
		List<Integer> ticklist = channelmap.getOrderedKeys();
		//Map<Byte, NUSALSeqCommand> cstate = new HashMap<Byte, NUSALSeqCommand>(); //For tracking state...
		//cstate.putAll(default_params);
		
		ctxt.usedcmds = new HashSet<NUSALSeqCmdType>();
		ctxt.keepcmds = new LinkedList<NUSALSeqCommand>();
		ctxt.param_state = new HashMap<NUSALSeqCmdType, NUSALSeqCommand>();
		ctxt.param_state.putAll(default_params);
		ctxt.allsubs = new HashSet<NUSALSeqCommandChunk>();
		ctxt.channelmap_out = new NUSALSeqCommandMap();
		boolean newchunk = false;
		
		ctxt.current_block = new TimeBlock();
		ctxt.current_block.base_tick = 0;
		blocks.add(ctxt.current_block);
		ctxt.chunk = new NUSALSeqCommandChunk();
		ctxt.current_block.addChunk(0, ctxt.chunk);
		for(int v = 0; v < 4; v++){
			if(lblocks[v] != null){
				//Don't need to pop first one - popped when list is added
				if(lblocks[v].getCurrent() != null){
					ctxt.current_block.sublevels.add(lblocks[v].getCurrent());	
				}
			}
		}
		
		//Go through tick groups
		for(Integer k : ticklist){
			if(ctxt.rwaitflag){
				//Adjust the time for the elapser delay.
				int tdiff = k - ctxt.last_tick;
				if(tdiff > 0){
					if(ctxt.elapser instanceof NUSALSeqWaitCommand){
						NUSALSeqWaitCommand wcmd = (NUSALSeqWaitCommand)ctxt.elapser;
						if(tdiff < 16) wcmd.toChannelDelta();
						wcmd.setParam(0, tdiff);
						ctxt.last_tick = k;
					}
				}
			}
			
			//Check if chunk end.
			newchunk = false;
			if((k > 0) && !ctxt.sorted_entries.isEmpty() && (k >= ctxt.sorted_entries.peekFirst())) newchunk = true;
			if(newchunk){
				nextTimeblock(ctxt, lblocks, blocks, k);
			}
			if(ctxt.endflag){
				//New chunk, but not timeblock
				ctxt.nextChunk(k);
			}
			
			ctxt.keepcmds.clear();
			
			List<NUSALSeqCommand> tickcmds = channelmap.getCommandsAt(k);
			scanTickCommands(ctxt, tickcmds, false);
			
			if(newchunk || k == 0){
				int tsec = blocks.size() - 1;
				initChannelChunk(ctxt, lblocks, tsec);
				//Label channel?
				ctxt.chunk.getChunkHead().setLabel(String.format("ch%02d_tsec%02d", index, tsec));
				//ctxt.chunk.setLabel(ctxt.chunk.getChunkHead().getLabel());
			}
			
			if(ctxt.keepcmds.isEmpty() && ctxt.elapser == null) continue; //All commands for this tick have been pruned.
			
			//Add delay from last tick
			int tdiff = k - ctxt.last_tick;
			if(tdiff > 0){
				if(tdiff < 16) ctxt.cur = new C_C_DeltaTime(tdiff);
				else ctxt.cur = new C_Wait(tdiff);
				ctxt.addCur();
			}
			
			//List of on-tick commands
			ctxt.last_tick = k;
			for(NUSALSeqCommand cmd : ctxt.keepcmds){
				ctxt.cur = cmd;
				ctxt.addCur();
			}
			
			//Multi-tick command
			if(ctxt.elapser != null){
				ctxt.cur = ctxt.elapser;
				ctxt.addCur();
				ctxt.last_tick += ctxt.tlen;
			}
		}
		
		//Make sure channel extends to ends of all layers (as channel level commands may not occur as late)
		nextTimeblock(ctxt, lblocks, blocks, -1);
		
		//Add end command to last chunk.
		ctxt.addEndRead();
		
		//Try a cleanup
		for(TimeBlock tb : blocks){
			tb.cleanEmptyChunks();
			for(NUSALSeqCommandChunk chunk : tb.main_track){
				adjustMistrackedReferences(ctxt, chunk);
			}
		}
		
		//Compress
		if(compressor != null){
			//blocks = compressor.compress(blocks);
		}
		
		//System.err.println("NUSALSeqBuilderLayer.buildChannel || DEBUG -- Printing build output for ch " + index);
		//TimeBlock.debug_printTimeBlocks(blocks);
		
		return blocks;
	}
	
}
