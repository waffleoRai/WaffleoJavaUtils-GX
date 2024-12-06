package waffleoRai_SeqSound.n64al.seqgen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import waffleoRai_SeqSound.n64al.NUSALSeq;
import waffleoRai_SeqSound.n64al.NUSALSeqCmdType;
import waffleoRai_SeqSound.n64al.NUSALSeqCommand;
import waffleoRai_SeqSound.n64al.NUSALSeqCommandBook;
import waffleoRai_SeqSound.n64al.NUSALSeqCommandMap;
import waffleoRai_SeqSound.n64al.NUSALSeqCommands;
import waffleoRai_SeqSound.n64al.cmd.NUSALSeqCommandChunk;
import waffleoRai_SeqSound.n64al.cmd.NUSALSeqCopyCommand;
import waffleoRai_SeqSound.n64al.cmd.NUSALSeqNoteCommand;
import waffleoRai_SeqSound.n64al.cmd.NUSALSeqWaitCommand;
import waffleoRai_SeqSound.n64al.cmd.SysCommandBook;
import waffleoRai_SeqSound.n64al.cmd.LayerCommands.*;
import waffleoRai_SeqSound.n64al.cmd.FCommands.*;

public class NUSALSeqBuilderLayer {
	
	//TODO Add part to clean up 0 length notes (can be generated from having too many voices)
	
	/*--- Constants ---*/
	
	/*--- Instance Variables ---*/
	
	private int index;
	private NUSALSeqBuilderChannel parent;
	private NUSALSeqCommandBook cmdBook;
	
	private NUSALSeqCommandMap layermap;
	
	private int[] on_note; //Note, vel, gate, start tick, channel transpose
	private NUSALSeqNoteCommand last_note; //Held here in case it needs to be shortened.
	private boolean portamento = false;
	private boolean legato = false;
	
	/*--- Init ---*/

	public NUSALSeqBuilderLayer(int layer, NUSALSeqBuilderChannel parent_channel){
		index = layer;
		parent = parent_channel;
		//subroutines = new LinkedList<NUSALSeqCommandMap>();
		layermap = new NUSALSeqCommandMap();
		if(parent != null) {
			cmdBook = parent.getCommandBook();
		}
		if(cmdBook == null) cmdBook = SysCommandBook.getDefaultBook();
	}
	
	public void reset(){
		//subroutines.clear();
		layermap.clearValues();
	}
	
	/*--- Getters ---*/
	
	public int getIndex(){return index;}
	public NUSALSeqBuilderChannel getParentChannel(){return parent;}
	
	public boolean noteActive(){
		if(on_note == null) return false;
		return (on_note[0] >= 0);
	}
	
	public NUSALSeqNoteCommand getLastNote(){return last_note;}
	public int getCurrentNoteStart() {return on_note[3];}
	
	protected NUSALSeqCommandMap getTickMap(){return layermap;}
	
	/*--- Setters ---*/
	
	public void addCommand(int tick, NUSALSeqCommand cmd){
		layermap.addCommand(tick, cmd);
	}
	
	public void addCommands(NUSALSeqCommandMap commands){
		layermap.addCommands(commands);
	}
	
	/*--- Controller ---*/
	
	public byte currentOnNote(){
		if(on_note == null) return -1;
		return (byte)on_note[0];
	}
	
	public void noteOn(int tick, byte midinote, byte vel, int gate, int ch_transpose){
		if(on_note == null) on_note = new int[5];
		on_note[0] = midinote;
		on_note[1] = vel;
		on_note[2] = gate;
		on_note[3] = tick;
		on_note[4] = ch_transpose;
	}
	
	public void noteOff(double tick){
		//Set gate here.
		if(on_note == null || on_note[0] < 0) return; //Nothing to turn off!
		double dur = tick - (double)on_note[3];
		if(on_note[2] < 0) {
			//Calculate gate
			on_note[2] = NUSALSeq.calculateGate(dur);
			dur = Math.ceil(dur);
		}
		else dur = Math.round(dur);

		int notedur = (int)dur;
		last_note = NUSALSeqNoteCommand.fromMidiNote(cmdBook, (byte)on_note[0], on_note[4], 
				notedur, (byte)on_note[1], (byte)on_note[2]);
		layermap.addValue(on_note[3], last_note);
		on_note[0] = -1;
	}
	
	public void portamentoOn(int tick, int mode, int target, int time){
		layermap.addCommand(tick, new C_L_Portamento(cmdBook, mode, target, time));
		portamento = true;
	}
	
	public void portamentoOff(int tick){
		if(!portamento) return;
		layermap.addCommand(tick, new C_L_PortamentoOff(cmdBook));
		portamento = false;
	}
	
	public void legatoOn(int tick) {
		if(legato) return;
		layermap.addCommand(tick, new C_L_LegatoOn(cmdBook));
		legato = true;
	}
	
	public void legatoOff(int tick) {
		if(!legato) return;
		layermap.addCommand(tick, new C_L_LegatoOff(cmdBook));
		legato = false;
	}
	
	/*--- Inner Classes ---*/
	
	private static class LayerBuildContext{
		
		public NUSALSeqCommandChunk chunk;
		public Set<NUSALSeqCmdType> usedcmds;
		public Map<NUSALSeqCmdType, NUSALSeqCommand> param_state;
		
		public NUSALSeqCommand last = null;
		public NUSALSeqCommand note = null;
		public NUSALSeqCommand cur = null;
		public int last_tick = 0;
		public int tlen = 0;
		public boolean endflag = false;
		public int ntime = -1; 
		public byte ngate = -1;
		public LinkedList<NUSALSeqCommand> keepcmds;
		
		public Set<NUSALSeqCommandChunk> allsubs;
		public TimeBlock current_block;
		
		public NUSALSeqCommandBook cmdBook;
		
		/*public void reset(){
			chunk = null; usedcmds = null;
			last = null; note = null; cur = null;
			last_tick = 0; tlen = 0;
			endflag = false; ntime = -1; ngate = -1;
			keepcmds = null;
			allsubs = null;
			current_block = null;
			param_state = null;
		}*/
		
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
			if(last != null) last.setSubsequentCommand(cur);
			chunk.addCommand(cur);
			last = cur;
			cur.setTickAddress(last_tick);
		}
		
		public void addEndRead(){
			cur = new C_EndRead(cmdBook);
			if(last != null) last.setSubsequentCommand(cur);
			chunk.addCommand(cur);
			last = null;
		}
		
		public void nextChunk(int tick){
			addEndRead();
			chunk = new NUSALSeqCommandChunk();
			ntime = -1; ngate = -1;
			if(current_block != null){
				current_block.cleanEmptyChunks();
				current_block.addChunk(tick, chunk);
				//Check for label?
			}
		}
	
	}
	
	/*--- Build ---*/
	
	private void cleanEmptyNotes(){
		List<Integer> ticks = layermap.getOrderedKeys();
		NUSALSeqCommandMap oldmap = layermap;
		layermap = new NUSALSeqCommandMap();
		
		for(int t : ticks){
			List<NUSALSeqCommand> cmdlist = oldmap.getCommandsAt(t);
			for(NUSALSeqCommand cmd : cmdlist){
				if(cmd instanceof NUSALSeqNoteCommand){
					if(cmd.getSizeInTicks() > 0) layermap.addCommand(t, cmd);
				}
				else layermap.addCommand(t, cmd);
			}
		}
	}
	
	private static void scanTickCommands(LayerBuildContext ctxt, List<NUSALSeqCommand> tickcmds, boolean sub){
		ctxt.usedcmds.clear(); ctxt.keepcmds.clear();
		ctxt.note = null; ctxt.endflag = false;
		
		//Go through tick cmds to decide which to keep.
		//Delay commands are always skipped, and note commands held until the end.
		//Only one note command will be used. Others skipped.
		//Chucks "end" commands too, as it will re-add its own
		for(NUSALSeqCommand cmd : tickcmds){
			//System.err.println("NUSALSeqBuilderLayer.scanTickCommands || Scanning -- " + cmd.toMMLCommand());
			NUSALSeqCmdType ctype = cmd.getFunctionalType();
			if(!ctype.flagSet(NUSALSeqCommands.FLAG_LYRVALID)) continue; //Invalid. Skip.
			if(ctype.flagSet(NUSALSeqCommands.FLAG_ONCEPERTICK) && ctxt.usedcmds.contains(ctype)) continue;
			if(ctype.flagSet(NUSALSeqCommands.FLAG_ISWAIT)) continue; //Skip
			else if(ctype.flagSet(NUSALSeqCommands.FLAG_TAKESTIME)){
				if(ctxt.note == null){
					ctxt.note = cmd;
					ctxt.tlen = ctxt.note.getSizeInTicks();
				}
			}
			else if(ctype.flagSet(NUSALSeqCommands.FLAG_CALL)){
				//Find reference and add as sub.
				//Also check if takes time.
				//System.err.println("NUSALSeqBuilderLayer.scanTickCommands || We got a call!");
				NUSALSeqCommand calltarg = cmd.getBranchTarget();
				if(calltarg != null){
					//Determine whether target takes time and is a chunk.
					if(calltarg instanceof NUSALSeqCommandChunk){
						//System.err.println("NUSALSeqBuilderLayer.scanTickCommands || It's a proper sub!");
						NUSALSeqCommandChunk tchunk = (NUSALSeqCommandChunk)calltarg;
						//If chunk is just one command, replace call with that.
						if(tchunk.getChildCount() < 3){
							//I'll keep it simple for now?
							NUSALSeqCommand cmdone = tchunk.getChunkHead();
							NUSALSeqCmdType ctype2 = cmdone.getFunctionalType();
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
								if(ctxt.note == null){
									ctxt.note = cmd;
									ctxt.tlen = ctxt.note.getSizeInTicks();
								}
							}
							else ctxt.keepcmds.add(cmd);	
						}
					}
					else ctxt.keepcmds.add(cmd);
				}
				else{
					//Add as normal, I guess...
					//A broken link isn't good though.
					System.err.println("NUSALSeqBuilderLayer.scanTickCommands || WARNING: Call command has broken link!");
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
		}
	}
	
	private static void handleMultitickCommand(LayerBuildContext ctxt){
		if(ctxt.note != null){
			ctxt.cur = ctxt.note;
			ctxt.addCur();
			
			ctxt.last_tick += ctxt.tlen;
			if(ctxt.note instanceof NUSALSeqNoteCommand){
				NUSALSeqNoteCommand nnote = (NUSALSeqNoteCommand)ctxt.note;
				if(nnote.getFunctionalType() == NUSALSeqCmdType.PLAY_NOTE_NTVG){
					//See if can collapse. Save time and gate.
					if(nnote.getTime() == ctxt.ntime){
						nnote.ntvg2nvg(ctxt.cmdBook);
						ctxt.ngate = (byte)nnote.getGate();
					}
					else if(nnote.getGate() == ctxt.ngate){
						nnote.ntvg2ntv(ctxt.cmdBook);
						ctxt.ntime = nnote.getTime();
					}
					else{
						ctxt.ntime = nnote.getTime();
						ctxt.ngate = (byte)nnote.getGate();
					}
				}
				else if(nnote.getFunctionalType() == NUSALSeqCmdType.PLAY_NOTE_NTV){
					//Save time
					ctxt.ntime = nnote.getTime();
				}
				else if(nnote.getFunctionalType() == NUSALSeqCmdType.PLAY_NOTE_NVG){
					//Save gate
					ctxt.ngate = (byte)nnote.getGate();
				}
			}
		}
	}

	public static NUSALSeqCommandChunk buildLayerSubroutine(NUSALSeqCommandMap tickmap, NUSALSeqCommandBook cmdBook){
		if(tickmap == null) return null;
		LayerBuildContext ctxt = new LayerBuildContext();
		ctxt.chunk = new NUSALSeqCommandChunk();
		ctxt.usedcmds = new HashSet<NUSALSeqCmdType>(); //Marks which commands have been used for this tick. For cleanup.
		ctxt.cmdBook = cmdBook;
		//ctxt.param_state = new HashMap<NUSALSeqCmdType, NUSALSeqCommand>();
		
		List<Integer> ticklist = tickmap.getOrderedKeys();
		
		ctxt.keepcmds = new LinkedList<NUSALSeqCommand>();
		for(Integer k : ticklist){
			if(ctxt.endflag) break;
			List<NUSALSeqCommand> tickcmds = tickmap.getCommandsAt(k);
			
			scanTickCommands(ctxt, tickcmds, true);
			
			if(ctxt.keepcmds.isEmpty() && ctxt.note == null) continue;
			
			//Add delay from last tick
			int tdiff = k - ctxt.last_tick;
			if(tdiff > 0){
				ctxt.cur = new C_L_Rest(cmdBook, tdiff);
				ctxt.addCur();
			}
			
			//List of on-tick commands
			ctxt.last_tick = k;
			for(NUSALSeqCommand cmd : ctxt.keepcmds){
				ctxt.cur = cmd; ctxt.addCur();
			}
			
			//Multi-tick command
			handleMultitickCommand(ctxt);
		}
		
		//Add end read.
		ctxt.addEndRead();
		
		return ctxt.chunk;
	}
	
	public List<TimeBlock> buildLayer(int max_rest, Set<Integer> entry_points, NUSALSequenceCompressor compressor){
		
		//Links commands together, but returns map with blocks for each startlayer to reference
		/*
		 * The following assumptions are made:
		 * 	-> All events for this layer have already been assigned as-is. That is,
		 * 		compression has been done and subroutines defined.
		 *  -> All jumps/calls/branches already have their target referenced. Subroutines
		 *  	are pre-built with another method.
		 *  -> Call targets are chunks of commands - this way the sub's tick length can be gauged.
		 * 
		 */
		if(layermap == null || layermap.isEmpty()) return null;
		cleanEmptyNotes();
		
		//NUSALSeqCommandMap lstartmap = new NUSALSeqCommandMap();
		List<TimeBlock> blocks = new LinkedList<TimeBlock>();
		LinkedList<Integer> sorted_entries = new LinkedList<Integer>();
		if(entry_points != null){
			sorted_entries.addAll(entry_points);
			Collections.sort(sorted_entries);
		}
		
		LayerBuildContext ctxt = new LayerBuildContext();
		ctxt.usedcmds = new HashSet<NUSALSeqCmdType>();
		ctxt.param_state = new HashMap<NUSALSeqCmdType, NUSALSeqCommand>();
		ctxt.cmdBook = cmdBook;
		
		List<Integer> ticklist = layermap.getOrderedKeys();
		//boolean newchunk = false;
		
		ctxt.current_block = new TimeBlock();
		blocks.add(ctxt.current_block);
		ctxt.allsubs = new HashSet<NUSALSeqCommandChunk>();
		
		ctxt.chunk = new NUSALSeqCommandChunk();
		ctxt.current_block.addChunk(0, ctxt.chunk);
		ctxt.keepcmds = new LinkedList<NUSALSeqCommand>();
		
		for(Integer k : ticklist){
			
			//See if need new chunk.
			//If exceed entrypoint, also need a new timeblock.
			//Otherwise, just new chunk.
			if((k > 0) && !sorted_entries.isEmpty() && (k >= sorted_entries.peekFirst())){
				//New time block.
				//Trim time-elapsing commands to not go past end of previous timeblock.
				int splitpt = sorted_entries.pop();
				int ci = 0;
				for(NUSALSeqCommandChunk ch : ctxt.current_block.main_track){
					int ctick = ctxt.current_block.tick_coords.get(ci);
					List<NUSALSeqCommand> chcmds = ch.getCommands();
					for(NUSALSeqCommand ccmd : chcmds){
						int tsz = ccmd.getSizeInTicks();
						int endtick = ctick + tsz;
						if(endtick > splitpt){
							int diff = endtick - splitpt;
							if(ccmd instanceof NUSALSeqNoteCommand){
								NUSALSeqNoteCommand ncmd = (NUSALSeqNoteCommand)ccmd;
								if(ccmd.getFunctionalType() == NUSALSeqCmdType.PLAY_NOTE_NVG){
									//Reset to NTVG
									ncmd.nvg2ntvg(cmdBook);
									ncmd.setTime(tsz - diff);
								}
								else{
									ncmd.shortenTimeBy(diff);
								}
							}
							else if(ccmd instanceof NUSALSeqWaitCommand){
								ccmd.setParam(0, tsz - diff);
							}
						}
						ctick = endtick;
					}
					//Clean empty commands...
					ch.removeEmptyNotesAndDelays();
					ci++;
				}
				
				//To ensure proper timeblock alignment, add blocks until in correct block.
				ctxt.current_block = new TimeBlock();
				ctxt.current_block.base_tick = splitpt;
				blocks.add(ctxt.current_block);
				while(!sorted_entries.isEmpty() && (k >= sorted_entries.peekFirst())){
					splitpt = sorted_entries.pop();
					ctxt.current_block = new TimeBlock();
					ctxt.current_block.base_tick = splitpt;
					blocks.add(ctxt.current_block);
				}
				
				ctxt.nextChunk(splitpt); //Delay will be added in this case.
				ctxt.loadParamStateToChunk();
				ctxt.last_tick = splitpt;
			}
			else if(ctxt.endflag || (k-ctxt.last_tick >= max_rest)){
				//Just new chunk.
				ctxt.nextChunk(k);
				
				//If this new chunk is not the start of the timeblock, catchup last_tick to k.
				//Otherwise, bump chunk start to timeblock start.
				if(ctxt.current_block.main_track.size() > 1){
					ctxt.last_tick = k;
				}
				else{
					ctxt.current_block.tick_coords.set(0, ctxt.current_block.base_tick);
				}
				
			}
			
			//Do commands for tick
			List<NUSALSeqCommand> tickcmds = layermap.getCommandsAt(k);
			scanTickCommands(ctxt, tickcmds, false);
			
			if(ctxt.keepcmds.isEmpty() && ctxt.note == null) continue;
			
			//Add delay from last tick
			int tdiff = k - ctxt.last_tick;
			if(tdiff > 0){
				ctxt.cur = new C_L_Rest(tdiff);
				//ctxt.cur.setTickAddress(k);
				ctxt.addCur();
			}
			
			//List of on-tick commands
			ctxt.last_tick = k;
			for(NUSALSeqCommand cmd : ctxt.keepcmds){
				cmd.setTickAddress(k);
				//ctxt.cur = cmd; ctxt.addCur();
			}
			
			//Multi-tick command
			//ctxt.last_tick = k;
			//ctxt.note.setTickAddress(k);
			handleMultitickCommand(ctxt);
		}
		ctxt.addEndRead();
		
		//Clean up indiv blocks before returning to delete empty chunks.
		//for(TimeBlock tb : blocks) tb.cleanEmptyChunks();
		for(TimeBlock tb : blocks) tb.cleanNotelessChunks();
		
		//Compress
		if(compressor != null){
			//DEBUG
			//System.err.println("NUSALSeqBuilderLayer.buildLayer || DEBUG -- Printing pre-compression build output for ch " + parent.getIndex() + " lyr " + index);
			//TimeBlock.debug_printTimeBlocks(blocks);
			System.err.println("NUSALSeqBuilderLayer.buildLayer || DEBUG -- Now compressing " + parent.getIndex() + " lyr " + index);
			blocks = compressor.compress(blocks);
			
			//Label subs.
			int tsec = 0;
			for(TimeBlock tb : blocks){
				int ii = 0;
				if(!tb.subroutines.isEmpty()){
					for(NUSALSeqCommandChunk chunk : tb.subroutines){
						String lbl = String.format("sub_ch%02d_lyr%02d_%02d-%03d", getParentChannel().getIndex(), index, tsec, ii++);
						chunk.getChunkHead().setLabel(lbl);
					}
				}
				tsec++;
			}
		}
		
		//DEBUG
		//System.err.println("NUSALSeqBuilderLayer.buildLayer || DEBUG -- Printing build output for ch " + parent.getIndex() + " lyr " + index);
		//TimeBlock.debug_printTimeBlocks(blocks);
		
		return blocks;
	}
	
}
