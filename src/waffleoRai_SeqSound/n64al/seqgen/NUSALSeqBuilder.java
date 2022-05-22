package waffleoRai_SeqSound.n64al.seqgen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
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
import waffleoRai_SeqSound.n64al.cmd.SeqCommands.*;
import waffleoRai_SeqSound.n64al.cmd.ChannelCommands.*;
import waffleoRai_SeqSound.n64al.cmd.BasicCommandMap;
import waffleoRai_SeqSound.n64al.cmd.FCommands.*;
import waffleoRai_SeqSound.n64al.seqgen.TimeBlock.TimeBlockList;

public class NUSALSeqBuilder {

	/*--- Constants ---*/
	
	//TODO
	public static final int[] DEFO_CH_PRI = {9, 10, 11, 8,
											 12, 13, 14, 7,
											 5, 15, 16, 17,
											 6, 4, 3, 2};
	
	/*--- Instance Variables ---*/
	
	private NUSALSeqInitValues init_val;
	
	//Optimization options...
	private NUSALSequenceCompressor compressor;
	private int ldelay_max = 384; //Defaults to two measures
	private int[] defo_pri;
	
	private Set<Integer> time_breaks;
	private Map<NUSALSeqCmdType, NUSALSeqCommand> default_params;
	
	private int loop_st = -1;
	private int loop_ed = -1;
	
	private boolean use_gate = false; //If off, gate is always 0
	private Random gate_randomizer;
	private int gate_min = 20;
	private int gate_max = 240;
	private double samegate_chance = 0.1; 
	
	private NUSALSeqCommandMap seqmap;
	private NUSALSeqBuilderChannel[] channels;
	
	/*--- Init ---*/
	
	public NUSALSeqBuilder(){
		init_val = new NUSALSeqInitValues();
		channels = new NUSALSeqBuilderChannel[16];
		seqmap = new NUSALSeqCommandMap();
		gate_randomizer = new Random();
		time_breaks = new HashSet<Integer>();
		default_params = new HashMap<NUSALSeqCmdType, NUSALSeqCommand>();
		defo_pri = new int[16];
		for(int i = 0; i < 16; i++) defo_pri[i] = DEFO_CH_PRI[i];
	}
	
	public void reset(){
		seqmap.clearValues();
		for(int i = 0; i < 16; i++){
			if(channels[i] != null){
				channels[i].reset();
				channels[i] = null;
			}
		}
		time_breaks.clear();
	}
	
	private void openChannel(int i){
		channels[i] = new NUSALSeqBuilderChannel(i, this);
		//Set default vol, pan, priority, reverb
		channels[i].addDefaultParam(new C_C_Volume(127));
		channels[i].addDefaultParam(new C_C_Pan(64));
		channels[i].addDefaultParam(new C_C_Priority(defo_pri[i]));
		channels[i].addDefaultParam(new C_C_Reverb(0));
	}
	
	/*--- Getters ---*/
	
	public int voicesOn(){
		int c = 0;
		for(int i = 0; i < 16; i++){
			if(channels[i] == null) continue;
			c += channels[i].voicesOn();
		}
		return c;
	}
	
	public NUSALSequenceCompressor getCompressor(){return compressor;}
	public int getMaximumLayerDelay(){return ldelay_max;}
	public int getMinRandomGateBoundary(){return gate_min;}
	public int getMaxRandomGateBoundary(){return gate_max;}
	public double getSamegateChance(){return samegate_chance;}
	
	public int getDefaultChannelPriority(int ch){
		if(ch < 0 || ch >= 16) return -1;
		return defo_pri[ch];
	}
	
	/*--- Setters ---*/
	
	public void setInitTempo(int bpm){init_val.tempo = bpm;}
	public void setInitVol(int val){init_val.volume = val;}
	public void clearTimeBreaks(){time_breaks.clear();}
	public void addTimeBreak(int tick){time_breaks.add(tick);}
	public void setMaxLayerDelay(int ticks){ldelay_max = ticks;}
	
	public void setGateGen(boolean on){
		use_gate = on;
	}
	
	public void setMinRandomGateBoundary(int value){
		if(value < 0) value = 0;
		if(value > 255) value = 255;
		gate_min = value;
		if(gate_min > gate_max) gate_min = gate_max;
	}
	
	public void setMaxRandomGateBoundary(int value){
		if(value < 0) value = 0;
		if(value > 255) value = 255;
		gate_max = value;
		if(gate_min > gate_max) gate_max = gate_min;
	}
	
	public void setSamegateChance(double value){
		if(value < 0.0) value = 0.0;
		if(value > 1.0) value = 1.0;
		samegate_chance = value;
	}
	
	public void setDefaultChannelPriority(int ch, int val){
		if(ch < 0 || ch >= 16) return;
		defo_pri[ch] = val;
	}
	
	public void setCompression(boolean b){
		if(b) compressor = new NUSALSequenceCompressor();
		else compressor = null;
	}
	
	/*--- Add Commands ---*/
	
	public void addLoop(int loop_start, int loop_end){
		loop_st = loop_start;
		loop_ed = loop_end;
		if(loop_st > 0) addTimeBreak(loop_st);
		if(loop_ed > 0) addTimeBreak(loop_ed);
	}
	
	public void addSeqCommandAtTick(int tick, NUSALSeqCommand cmd){
		if(cmd == null || tick < 0) return;
		seqmap.addCommand(tick, cmd);
	}
	
	public void addSeqCommands(NUSALSeqCommandMap tickmap){
		if(tickmap == null) return;
		seqmap.addCommands(tickmap);
	}
	
	public void addChannelCommandAtTick(int tick, int ch, NUSALSeqCommand cmd){
		if(ch < 0 || ch >= 16) return;
		if(channels[ch] == null){
			openChannel(ch);
		}
		channels[ch].addChannelCommandAtTick(tick, cmd);
	}
	
	public void addLayerCommandAtTick(int tick, int ch, int ly, NUSALSeqCommand cmd){
		if(ch < 0 || ch >= 16) return;
		if(channels[ch] == null){
			channels[ch] = new NUSALSeqBuilderChannel(ch, this);
		}
		channels[ch].addLayerCommandAtTick(tick, ly, cmd);
	}
	
	public void addChannelCommands(int ch, NUSALSeqCommandMap tickmap){
		if(ch < 0 || ch >= 16) return;
		if(channels[ch] == null){
			openChannel(ch);
		}
		channels[ch].addChannelCommands(tickmap);
	}
	
	public void addLayerCommands(int ch, int ly, NUSALSeqCommandMap tickmap){
		if(ch < 0 || ch >= 16) return;
		if(channels[ch] == null){
			channels[ch] = new NUSALSeqBuilderChannel(ch, this);
		}
		channels[ch].addLayerCommands(ly, tickmap);
	}
	
	public NUSALSeqNoteCommand generateLayerNote(byte midinote, byte vel, int len) {
		return generateLayerNote(midinote, vel, len, 0);
	}
	
	public NUSALSeqNoteCommand generateLayerNote(byte midinote, byte vel, int len, int transpose) {
		//What to do with gate? No idea.
		byte gate = 0;
		if(use_gate){
			int random_gate = this.gate_randomizer.nextInt(gate_max - gate_min) + gate_min;
			gate = (byte)random_gate;
		}
		NUSALSeqNoteCommand cmd = NUSALSeqNoteCommand.fromMidiNote(midinote, transpose, len, vel, gate);
		return cmd;
	}
	
	public NUSALSeqCommandChunk genSequenceSubroutine(NUSALSeqCommandMap tickmap){
		if(tickmap == null) return null;
		return buildSequenceSubroutine(tickmap);
	}
	
	public NUSALSeqCommandChunk genChannelSubroutine(NUSALSeqCommandMap tickmap){
		if(tickmap == null) return null;
		return NUSALSeqBuilderChannel.buildChannelSubroutine(tickmap);
	}
	
	public NUSALSeqCommandChunk genLayerSubroutine(NUSALSeqCommandMap tickmap){
		if(tickmap == null) return null;
		return NUSALSeqBuilderLayer.buildLayerSubroutine(tickmap);
	}
	
	/*--- Control (Generator) ---*/
	
	public void sendNoteOn(int tick, int ch, byte midinote, byte velocity){
		//Generate random gate.
		int gate = -1;
		//1. Do we put in a new gate, or tell channel to use last/default?
		if(use_gate){
			double r = gate_randomizer.nextDouble();
			if(r >= samegate_chance){
				gate = gate_min + gate_randomizer.nextInt(gate_max - gate_min);
			}	
		}
		else gate = 0;
		
		if(channels[ch] == null) openChannel(ch);
		channels[ch].sendNoteOn(tick, midinote, velocity, gate);
	}
	
	public void sendNoteOff(int tick, int ch, byte midinote){
		if(channels[ch] == null) openChannel(ch); //THIS SHOULD NOT HAPPEN, but just in case.
		channels[ch].sendNoteOff(tick, midinote);
	}
	
	/*--- Serialize ---*/
	
	/*--- Inner Class ---*/
	
	private static class SequenceBuildContext{
		
		public NUSALSeqCommandChunk chunk;
		public Set<NUSALSeqCmdType> usedcmds;
		public Map<NUSALSeqCmdType, NUSALSeqCommand> param_state;
		
		public NUSALSeqCommand last = null;
		public NUSALSeqCommand elapser = null;
		public NUSALSeqCommand cur = null;
		public int last_tick = 0;
		public int tlen = 0;
		public boolean endflag = false;
		public boolean rwaitflag = false;
		public LinkedList<NUSALSeqCommand> keepcmds;
		
		public LinkedList<Integer> sorted_entries;
		public NUSALSeqCommandChunk loopchunk;
		
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
			cur.flagSeqUsed();
			chunk.addCommand(cur);
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
			//addEndRead();
			chunk = new NUSALSeqCommandChunk();
			ch_auto_init = true;
			if(current_block != null){
				current_block.addChunk(tick, chunk);
			}
		}
		
	}
	
	/*--- Build ---*/
	
	private static void scanTickCommands(SequenceBuildContext ctxt, List<NUSALSeqCommand> tickcmds, boolean sub){
		ctxt.usedcmds.clear(); //ctxt.keepcmds.clear();
		ctxt.elapser = null; ctxt.endflag = false;
		ctxt.rwaitflag = false;
		
		for(NUSALSeqCommand cmd : tickcmds){
			NUSALSeqCmdType ctype = cmd.getCommand();
			if(!ctype.flagSet(NUSALSeqCommands.FLAG_SEQVALID)) continue; //Invalid. Skip.
			if(ctype.flagSet(NUSALSeqCommands.FLAG_ONCEPERTICK) && ctxt.usedcmds.contains(ctype)) continue;
			
			if(ctype.flagSet(NUSALSeqCommands.FLAG_ISWAIT)){
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
					}
					ctxt.keepcmds.add(cmd);
				}
				else{
					//Add as normal, I guess...
					//A broken link isn't good though.
					System.err.println("NUSALSeqBuilder.scanTickCommands || WARNING: Call command has broken link!");
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
	
	private void addChannelStartCommands(SequenceBuildContext ctxt, TimeBlockList[] cblocks){
		for(int i = 0; i < 16; i++){
			if(cblocks[i] != null){
				List<TimeBlock> blist = cblocks[i].getBackingList();
				int tsec = 0;
				for(TimeBlock ctb : blist){
					int chunk_ct = ctb.main_track.size();
					if(chunk_ct < 1) {
						tsec++; continue;
					}
					
					//First block
					int ctime = 0;
					NUSALSeqCommand cchunk = null;
					NUSALSeqCommand cstart = null;
					for(int j = 0; j < chunk_ct; j++){
						ctime = ctb.tick_coords.get(j);
						cchunk = ctb.main_track.get(j);
						if(j == 0) cchunk.getChunkHead().setLabel(String.format("ch%02d_tsec%02d", i, tsec));
						else cchunk.getChunkHead().setLabel(String.format("ch%02d_tsec%02d_%03d", i, tsec,j));
						cstart = new C_S_StartChannel(i, -1);
						cstart.setReference(cchunk);
						
						if(j == 0){
							if(ctime == ctb.base_tick){
								//Skip for now. It will be added on tb init.
								continue;
							}
						}
						seqmap.addCommand(ctime, cstart);
					}
					tsec++;
				}
			}
		}
	}
	
	private void initSequenceChunk(SequenceBuildContext ctxt, TimeBlockList[] cblocks){
		//1. Channel points
		//2. Params
		ctxt.init_mode = true;
		
		if(ctxt.current_block.base_tick == loop_ed){
			if(ctxt.loopchunk != null){
				ctxt.cur = new C_Jump(-1);
				ctxt.cur.setReference(ctxt.loopchunk);
				ctxt.addCur();
			}
		}
		
		for(int c = 0; c < 16; c++){
			if(cblocks[c] != null){
				TimeBlock ctb = cblocks[c].getCurrent();
				if(ctb == null) continue;
				if(ctb.isMaintrackEmpty()) continue;
				
				//First block
				int ctime = ctb.tick_coords.get(0);
				NUSALSeqCommand cchunk = ctb.main_track.get(0);
				if(ctime == ctb.base_tick){
					ctxt.cur = new C_S_StartChannel(c, -1);
					ctxt.cur.setReference(cchunk);
					ctxt.addCur();
					continue;
				}
			}
		}
		
		ctxt.loadParamStateToChunk();
		ctxt.init_mode = false;
	}
	
	protected NUSALSeqCommandChunk buildSequenceSubroutine(NUSALSeqCommandMap tickmap){
		if(tickmap == null) return null;
		SequenceBuildContext ctxt = new SequenceBuildContext();
		ctxt.chunk = new NUSALSeqCommandChunk();
		ctxt.usedcmds = new HashSet<NUSALSeqCmdType>();
		List<Integer> ticklist = tickmap.getOrderedKeys();
		
		ctxt.keepcmds = new LinkedList<NUSALSeqCommand>();
		for(Integer k : ticklist){
			if(ctxt.rwaitflag){
				//Adjust the time for the elapser delay.
				int tdiff = k - ctxt.last_tick;
				if(tdiff > 0){
					if(ctxt.elapser instanceof NUSALSeqWaitCommand){
						NUSALSeqWaitCommand wcmd = (NUSALSeqWaitCommand)ctxt.elapser;
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
				ctxt.cur = new C_Wait(tdiff);
				ctxt.addCur();
			}
			
			//List of on-tick commands
			for(NUSALSeqCommand cmd : ctxt.keepcmds){
				ctxt.cur = cmd;
				ctxt.addCur();
			}
			
			//Multi-tick command
			ctxt.last_tick = k;
			if(ctxt.elapser != null){
				ctxt.cur = ctxt.elapser;
				ctxt.addCur();
				ctxt.last_tick += ctxt.tlen;
			}
			
		}
		ctxt.addEndRead();
		return ctxt.chunk;
	}
	
	private void tieupTimeblock(SequenceBuildContext ctxt, TimeBlockList[] cblocks, int tsec, int etick){
		//System.err.println("tieup tsec " + tsec);
		if(ctxt.current_block.isMaintrackEmpty()){
			//Add an init
			initSequenceChunk(ctxt, cblocks);
			ctxt.chunk.getChunkHead().setLabel(String.format("seq_tsec%02d", tsec));
		}
		
		//Add delay if needed.
		int send = etick;
		if(etick < 0){
			int cend = 0;
			for(TimeBlock ctb : ctxt.current_block.sublevels){
				cend = ctb.getEndTick();
				if(cend > send) send = cend;
			}	
		}
		
		int tdiff = send - ctxt.last_tick;
		if(tdiff > 0){
			ctxt.cur = new C_Wait(tdiff);
			ctxt.addCur();
		}
	}
	
	private void nextTimeblock(SequenceBuildContext ctxt, TimeBlockList[] cblocks, List<TimeBlock> blocks, int k_tick){
		//	ie. sorted_entries will be empty, but the block before that needs processing like the others
		int tsec = blocks.size();
		while((!ctxt.sorted_entries.isEmpty()) && ((k_tick < 0)||(k_tick >= ctxt.sorted_entries.peekFirst()))){
			//Tie up previous time block
			if(ctxt.current_block != null){
				if(!ctxt.current_block.isEmpty()){
					tieupTimeblock(ctxt, cblocks, tsec-1, ctxt.sorted_entries.peekFirst());
				}
			}
			
			int splitpt = ctxt.sorted_entries.pop();
			ctxt.current_block = new TimeBlock();
			ctxt.current_block.base_tick = splitpt;
			blocks.add(ctxt.current_block);
			ctxt.nextChunk(splitpt);
			
			if(splitpt == loop_st) ctxt.loopchunk = ctxt.chunk;
			/*else if(splitpt == loop_ed){
				if(ctxt.loopchunk != null){
					ctxt.cur = new C_Jump(-1);
					ctxt.cur.setReference(ctxt.loopchunk);
					ctxt.addCur();
				}
			}*/
		
			//Add channel blocks
			TimeBlock ctb = null;
			for(int c = 0; c < 16; c++){
				if(cblocks[c] != null){
					if(cblocks[c].pop()){
						ctb = cblocks[c].getCurrent();
						if(!ctb.isEmpty()) ctxt.current_block.sublevels.add(ctb);
					}
				}
			}
			ctxt.last_tick = splitpt;
			tsec++;
		}
		if(k_tick < 0) tieupTimeblock(ctxt, cblocks, tsec-1, -1);
	}
	
	protected List<TimeBlock> buildTimeblocks(){
		if(seqmap == null) return null;
		//System.err.println("NUSALSeqBuilder.buildTimeblocks || Check 1");
		
		List<TimeBlock> blocks = new LinkedList<TimeBlock>();
		SequenceBuildContext ctxt = new SequenceBuildContext();
		ctxt.sorted_entries = new LinkedList<Integer>();
		if(time_breaks != null){
			time_breaks.remove(0);
			ctxt.sorted_entries.addAll(time_breaks);
			Collections.sort(ctxt.sorted_entries);
		}
		/*System.err.print("TIMEBLOCK BOUNDARIES: ");
		for(Integer i : ctxt.sorted_entries) System.err.print(i + " ");
		System.err.println();*/
		
		//Build channels
		TimeBlockList[] cblocks = new TimeBlockList[16];
		for(int i = 0; i < 16; i++){
			if(channels[i] != null){
				cblocks[i] = new TimeBlockList();
				cblocks[i].setList(channels[i].buildChannel(ldelay_max, time_breaks, compressor));
			}
		}
		addChannelStartCommands(ctxt, cblocks);
		
		//Main seq
		if(!seqmap.containsKey(0)) seqmap.getBackingMap().put(0, new LinkedList<NUSALSeqCommand>()); //Dummy 0
		List<Integer> ticklist = seqmap.getOrderedKeys();
		
		ctxt.usedcmds = new HashSet<NUSALSeqCmdType>();
		ctxt.keepcmds = new LinkedList<NUSALSeqCommand>();
		ctxt.param_state = new HashMap<NUSALSeqCmdType, NUSALSeqCommand>();
		ctxt.param_state.putAll(default_params);
		ctxt.allsubs = new HashSet<NUSALSeqCommandChunk>();
		boolean newchunk = false;
		
		//Add loop jump
		ctxt.loopchunk = null;
		
		ctxt.last_tick = 0;
		ctxt.current_block = new TimeBlock();
		ctxt.current_block.base_tick = 0;
		blocks.add(ctxt.current_block);
		ctxt.nextChunk(0);
		for(int c = 0; c < 16; c++){
			if(cblocks[c] != null){
				TimeBlock ctb = cblocks[c].getCurrent();
				if(!ctb.isEmpty()) ctxt.current_block.sublevels.add(ctb);
			}
		}
		if(loop_st == 0) ctxt.loopchunk = ctxt.chunk;
		
		for(Integer k : ticklist){
			if(ctxt.rwaitflag){
				//Adjust the time for the elapser delay.
				int tdiff = k - ctxt.last_tick;
				if(tdiff > 0){
					if(ctxt.elapser instanceof NUSALSeqWaitCommand){
						//System.err.println("hi");
						NUSALSeqWaitCommand wcmd = (NUSALSeqWaitCommand)ctxt.elapser;
						wcmd.setParam(0, tdiff);
						ctxt.last_tick = k;
					}
				}
			}
			newchunk = false;
			if((k > 0) && !ctxt.sorted_entries.isEmpty() && (k >= ctxt.sorted_entries.peekFirst())) newchunk = true;
			if(newchunk){
				//System.err.println("Need new timeblock: " + blocks.size());
				nextTimeblock(ctxt, cblocks, blocks, k);
			}
			if(ctxt.endflag){
				//New chunk, but not timeblock
				ctxt.nextChunk(k);
			}
			
			ctxt.keepcmds.clear();
			
			List<NUSALSeqCommand> tickcmds = seqmap.getCommandsAt(k);
			scanTickCommands(ctxt, tickcmds, true);
			
			if(newchunk || k == 0){
				initSequenceChunk(ctxt, cblocks);
				//ctxt.chunk.getChunkHead().setLabel(String.format("seq_tsec%02d", blocks.size()-1));
			}
			
			if(ctxt.keepcmds.isEmpty() && ctxt.elapser == null) continue; //All commands for this tick have been pruned.
			
			//Add delay from last tick
			int tdiff = k - ctxt.last_tick;
			if(tdiff > 0){
				ctxt.cur = new C_Wait(tdiff);
				ctxt.addCur();
			}
			
			//List of on-tick commands
			for(NUSALSeqCommand cmd : ctxt.keepcmds){
				ctxt.cur = cmd;
				ctxt.addCur();
			}
			
			//Multi-tick command
			ctxt.last_tick = k;
			if(ctxt.elapser != null){
				ctxt.cur = ctxt.elapser;
				ctxt.addCur();
				ctxt.last_tick += ctxt.tlen;
			}
			
		}
		
		//Add required delay to ensure channels can play out for last time block.
		nextTimeblock(ctxt, cblocks, blocks, -1);
		
		//Add end command to last chunk.
		ctxt.addEndRead();
		
		//System.err.println("NUSALSeqBuilderLayer.buildTimeblocks || DEBUG -- Printing build output for seq.");
		//TimeBlock.debug_printTimeBlocks(blocks);
		
		return blocks;
	}
	
	public NUSALSeq buildSeq(){
		//Determine which channels are used...
		for(int i = 0; i < 16; i++){
			init_val.track_used[i] = (channels[i] != null);
		}
		
		//Seq beginning
		//TODO At some point we need the timeblock jump logic. But eh, I'll put it in eventually.
		SequenceBuildContext ctxt = new SequenceBuildContext();
		ctxt.chunk = new NUSALSeqCommandChunk();
		ctxt.cur = new C_S_MuteBehavior(init_val.mutebhv); ctxt.addCur();
		ctxt.cur.setLabel("seqstart");
		ctxt.cur = new C_S_MuteScale(init_val.mutescale); ctxt.addCur();
		ctxt.cur = new C_S_InitChannels(0xffff); ctxt.addCur();
		int usedch = 0;
		for(int i = 0; i < 16; i++){
			if(init_val.track_used[i]){
				usedch |= (1 << i);
			}
		}
		ctxt.cur = new C_S_InitChannels(usedch); ctxt.addCur();
		
		//Copy init values to default params
		default_params.put(NUSALSeqCmdType.MASTER_VOLUME, new C_S_SetMasterVolume(init_val.volume));
		default_params.put(NUSALSeqCmdType.SET_TEMPO, new C_S_SetTempo(init_val.tempo));
		
		//Create chunks for putting blocks in.
		NUSALSeqCommandChunk seq_block = new NUSALSeqCommandChunk();
		NUSALSeqCommandChunk seq_sub_block = new NUSALSeqCommandChunk();
		NUSALSeqCommandChunk ch_main_block = new NUSALSeqCommandChunk();
		NUSALSeqCommandChunk ly_main_block = new NUSALSeqCommandChunk();
		NUSALSeqCommandChunk ch_sub_block = new NUSALSeqCommandChunk();
		NUSALSeqCommandChunk ly_sub_block = new NUSALSeqCommandChunk();
		
		//Now the big blocks.
		List<TimeBlock> timeblocks = buildTimeblocks();
		for(TimeBlock tb : timeblocks){
			//Main chunks (seq)
			NUSALSeqCommand slast = null;
			for(NUSALSeqCommandChunk cc : tb.main_track){
				//Actually do connect these together...
				seq_block.addCommand(cc);
				if(slast != null){
					slast.setSubsequentCommand(cc.getChunkHead());
				}
				slast = cc.getChunkTail();
			}
			
			//Subs (seq)
			for(NUSALSeqCommandChunk sub : tb.subroutines){
				seq_sub_block.addCommand(sub);
			}
			
			//Channels...
			for(TimeBlock ctb : tb.sublevels){
				for(NUSALSeqCommandChunk cc : ctb.main_track){
					ch_main_block.addCommand(cc);
				}
				for(NUSALSeqCommandChunk sub : ctb.subroutines){
					ch_sub_block.addCommand(sub);
				}
				
				//Layers...
				for(TimeBlock ltb : ctb.sublevels){
					for(NUSALSeqCommandChunk cc : ltb.main_track){
						ly_main_block.addCommand(cc);
					}
					for(NUSALSeqCommandChunk sub : ltb.subroutines){
						ly_sub_block.addCommand(sub);
					}
				}
			}
		}
		
		//Assign addresses (ctxt.addCur automatically doesn't add if chunk is empty)
		ctxt.cur = seq_block; ctxt.addCur(); ctxt.last = null;
		ctxt.cur = ch_main_block; ctxt.addCur(); ctxt.last = null;
		ctxt.cur = ly_main_block; ctxt.addCur(); ctxt.last = null;
		ctxt.cur = seq_sub_block; ctxt.addCur(); ctxt.last = null;
		ctxt.cur = ch_sub_block; ctxt.addCur(); ctxt.last = null;
		ctxt.cur = ly_sub_block; ctxt.addCur(); ctxt.last = null;
		ctxt.chunk.setAddress(0); //This is recursive
		ctxt.chunk.slideReferencesToChunkHeads();
		
		//Generate a BasicCommandMap and add main chunk
		BasicCommandMap cmdsrc = new BasicCommandMap();
		cmdsrc.loadIntoMap(ctxt.chunk);
		
		//Wrap in a NUSALSeq
		NUSALSeq outseq = NUSALSeq.newNUSALSeq(cmdsrc);
		
		return outseq;
	}
	
	
}
