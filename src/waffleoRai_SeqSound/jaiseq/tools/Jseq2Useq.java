package waffleoRai_SeqSound.jaiseq.tools;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import waffleoRai_SeqSound.MIDI;
import waffleoRai_SeqSound.SeqEventLister;
import waffleoRai_SeqSound.jaiseq.Jaiseq;
import waffleoRai_SeqSound.jaiseq.JaiseqCommand;
import waffleoRai_SeqSound.jaiseq.cmd.EJaiseqCmd;
import waffleoRai_SeqSound.jaiseq.cmd.JaiseqCommands.*;
import waffleoRai_SeqSound.n64al.NUSALSeq;
import waffleoRai_SeqSound.n64al.NUSALSeqCommand;
import waffleoRai_SeqSound.n64al.NUSALSeqCommandMap;
import waffleoRai_SeqSound.n64al.seqgen.NUSALSeqBuilder;
import waffleoRai_SeqSound.n64al.seqgen.NUSALSeqGenerator;
import waffleoRai_Utils.FileBuffer;

public class Jseq2Useq {
	
	public static final double N64_TIMEBASE = 48.0;
	public static final int DEFO_BLOCK_ALLOC = 128;
	
	/*--- Instance Variables ---*/
	
	private Jaiseq src;
	private JaiseqCommand head;
	
	private JaiUConvertContext context;
	
	private ArrayList<LinkedList<JaiseqCommand>> ch_heads;
	private LinkedList<JaiseqCommand> branches;

	/*--- Init ---*/
	
	private Jseq2Useq(){}
	
	private Jseq2Useq(Jaiseq input){
		src = input;
		head = src.getHead();
		branches = new LinkedList<JaiseqCommand>();
		ch_heads = new ArrayList<LinkedList<JaiseqCommand>>(16);
		for(int i = 0; i < 16; i++) ch_heads.add(new LinkedList<JaiseqCommand>());
		context = new JaiUConvertContext();
	}
	
	public static NUSALSeq convertJaiseq(Jaiseq input){
		if(input == null) return null;
		Jseq2Useq conv = new Jseq2Useq(input);
		return conv.convert();
	}
	
	/*--- Inner Classes ---*/
	
	//I'm gonna try a typedef workaround?
	private static class CommandBlock extends JaiUConvertCommandBlock{
		public CommandBlock(int alloc, JaiUConvertContext ctxt) {
			super(alloc, ctxt);
		}}
	
	/*--- Utility Methods ---*/
	
	private static boolean isDelay(JaiseqCommand cmd){
		if(cmd == null) return false;
		return (cmd instanceof JSC_Wait);
	}
	
 	protected static boolean blockIsEnd(CommandBlock block){
 		if(block == null) return false;
 		JaiseqCommand last = block.commands[block.cmd_count-1];
 		//System.err.println("Jseq2Useq.blockIsEnd || DEBUG -- Block last command: " + last.toMML());
 		if(last instanceof JSC_Return) return true;
 		if(last instanceof JSC_EndTrack) return true;
 		//System.err.println("Jseq2Useq.blockIsEnd || DEBUG -- end not detected");
 		return false;
 	}
 	
 	/*--- Branch Handlers ---*/
 	
 	//Handle subroutines DFS and jumps BFS?
	
 	protected void newBranch(JaiseqCommand cmd){
		if(context.handled.contains(cmd.getAddress())) return;
		branches.add(cmd);
	}
	
	protected JaiUConvertCommandBlock handleCall(JaiseqCommand cmd, int channel){
		JaiUConvertCommandBlock sub = context.subroutines.get(cmd.getAddress());
		if(sub != null){
			//current_tick += sub.len_ticks;
			return sub;
		}
		
		JaiseqCommand parent_head = head;
		double parent_tick = context.current_tick;
		
		head = cmd;
		context.current_tick = 0.0;
		
		sub = nextBlock(channel);
		sub.block_tick = 0.0;
		sub.addToTarget(channel, true);
		context.subroutines.put(cmd.getAddress(), sub);
		context.ncommands.put(cmd.getAddress(), sub.ch_head);
		//System.err.println("Subroutine found: 0x" + Integer.toHexString(cmd.getAddress()) + ", " + sub.len_ticks + " ticks");
		
		head = parent_head;
		//current_tick = parent_tick + sub.len_ticks;
		context.current_tick = parent_tick;
		
		return sub;
	}
	
	protected JaiUConvertCommandBlock handleBranch(JaiseqCommand src_command, int channel){
		if(head instanceof JSC_OpenTrack){
			ch_heads.get(src_command.getArg(0)).add(src_command.getReferencedCommand());
		}
		else if(head instanceof JSC_Jump){
			newBranch(src_command.getReferencedCommand());
		}
		else if(head instanceof JSC_Call){
			return handleCall(src_command.getReferencedCommand(), channel);
		}
		
		return null;
	}
 	
	/*--- Internal Conversion Methods ---*/
	
	protected void seqtickzero(){
		NUSALSeqBuilder target = context.getTarget();
		while(!(head instanceof JSC_Wait)){
			EJaiseqCmd ecmd = head.getCommandEnum();
			
			if(ecmd == EJaiseqCmd.OPEN_TRACK){
				int idx = head.getArg(0);
				ch_heads.get(idx).add(head.getReferencedCommand());
			}
			else if(ecmd == EJaiseqCmd.TEMPO || ecmd == EJaiseqCmd.TEMPO_RVL){
				target.setInitTempo(head.getArg(0));
			}
			else if(ecmd == EJaiseqCmd.TIMEBASE){
				context.setTimebase(head.getArg(0));
			}
			else if(ecmd == EJaiseqCmd.PERF_S8_NODUR){
				//Volume?
				if(head.getArg(0) == Jaiseq.PERF_TYPE_VOL){
					target.setInitVol(head.getArg(1));
				}
			}
			
			JaiseqCommand ref = head.getReferencedCommand();
			if(ref != null){
				handleBranch(ref, -1);
			}
			
			context.handled.add(head.getAddress());
			head = head.getNextCommand();
		}
		
	}
	
	protected CommandBlock nextBlock(int channel){
		/*
		 * Pop commands until encounter...
		 * A. A new label
		 * B. An end track command
		 * C. A return command
		 * D. No "next" command
		 * E. A previously handled command
		 */
		if(head == null) return null;
		if(context.handled.contains(head.getAddress())) return null;
		
		//Get commands until block end.
		LinkedList<JaiseqCommand> cmds = new LinkedList<JaiseqCommand>();
		int dcount = 0;
		
		cmds.add(head); context.handled.add(head.getAddress());
		if(head instanceof JSC_Wait) dcount++;
		if(head.getReferencedCommand() != null){
			handleBranch(head, channel);
		}
		head = head.getNextCommand();
		
		while(head != null && head.getLabel() == null && !context.handled.contains(head.getAddress())){
			EJaiseqCmd ecmd = head.getCommandEnum();	
			cmds.add(head);
			context.handled.add(head.getAddress());
			if(head instanceof JSC_Wait) dcount++;
			if(head.getReferencedCommand() != null) {
				handleBranch(head, channel);
			}
			head = head.getNextCommand();
			
			if(ecmd == EJaiseqCmd.END_TRACK || ecmd == EJaiseqCmd.RETURN || ecmd == EJaiseqCmd.RETURN_COND){
				//End of block.
				break;	
			}
		}
		
		//Generate block.
		//Split by layer for tracks
		CommandBlock block = new CommandBlock(cmds.size() - dcount + 4, context);
		block.block_tick = context.current_tick;
		//System.err.println("Jseq2Useq.nextBlock || Block created. Tick: " + block.block_tick);
		
		for(JaiseqCommand cmd : cmds){
			//System.err.println("Jseq2Useq.nextBlock || Processing command -- " + String.format("%06x", cmd.getAddress()) + ": " + cmd.toMML());
			if(cmd instanceof JSC_Wait){
				//System.err.println("Jseq2Useq.nextBlock || Command is a wait command. Adding " + context.tickScale(cmd.getArg(0)));
				block.addDelay(context.tickScale(cmd.getArg(0)));
				if(block.head_addr < 0 && block.cmd_count == 0){
					block.head_addr = cmd.getAddress();
					//System.err.println("Jseq2Useq.nextBlock || Delay command at block head. Label: " + cmd.getLabel());
				}
				context.last_ch_cmd = cmd;
			}
			else if(cmd instanceof JSC_NoteOn){
				int vox = cmd.getArg(1) - 1;
				if(block.voices[vox] == null){
					block.voices[vox] = new CommandBlock(cmds.size() - dcount, context);
					block.voices[vox].block_tick = block.len_ticks;
				}
				block.voices[vox].len_ticks = block.len_ticks - block.voices[vox].block_tick;
				block.voices[vox].addCommand(cmd);
				context.ly2ch_cmds.put(cmd.getAddress(), context.last_ch_cmd);
				int midinote = cmd.getArg(0);
				if(midinote < NUSALSeq.MIN_NOTE_MIDI){
					block.transpose = Math.min(block.transpose, (midinote - NUSALSeq.MIN_NOTE_MIDI));
				}
				else if(midinote > NUSALSeq.MAX_NOTE_MIDI){
					block.transpose = Math.max(block.transpose, (midinote - NUSALSeq.MAX_NOTE_MIDI));
					//System.err.println("transpose set to: " + block.transpose);
				}
			}
			else if(cmd instanceof JSC_NoteOff){
				int vox = cmd.getArg(0) - 1;
				if(block.voices[vox] != null){
					block.voices[vox].len_ticks = block.len_ticks - block.voices[vox].block_tick;
					block.voices[vox].addCommand(cmd);
				}
				context.ly2ch_cmds.put(cmd.getAddress(), context.last_ch_cmd);
			}
			else{
				block.addCommand(cmd);
				JaiseqCommand ref = cmd.getReferencedCommand();
				if(cmd instanceof JSC_Call){
					if(ref != null){
						JaiUConvertCommandBlock sub = context.subroutines.get(ref.getAddress());
						if(sub != null){
							block.addDelay(sub.len_ticks);
						}	
					}
				}
				context.last_ch_cmd = cmd;
				/*if(ref != null){
					//Make sure it's not a note
					if((ref instanceof JSC_NoteOn) || (ref instanceof JSC_NoteOff)){
						ref = context.ly2ch_cmds.get(ref.getAddress());
					}
					if(ref != null){
						cmd.setReferencedCommand(ref);
					}
				}*/
			}
		}
		
		return block;
	}
	
	protected void convertTrack(int index){
		//I think subroutine discovery will have to be DFS
		//	since the tick lengths matter...
		LinkedList<JaiseqCommand> headlist = ch_heads.get(index);
		if(headlist.isEmpty()) return;
		context.current_tick = 0.0;
		context.last_ch_cmd = null;

		while(!headlist.isEmpty()){
			head = headlist.pop();
			CommandBlock block = nextBlock(index);
			while(block != null){
				//Add block
				block.addToTarget(index, false);
				
				//Increment time
				context.current_tick += block.len_ticks;
				
				//Add branches to headlist
				while(!branches.isEmpty()) {
					headlist.add(branches.pop());
				}
				
				//Next
				block = nextBlock(index);
			}	
		}
		
		//Already did subroutines.
	}
	
	protected NUSALSeq convert_direct(){
		context.reset();
		src.setConditionFlag(true);
		src.scanMasterTrack();
		NUSALSeqBuilder target = context.getTarget();
		
		//Do this in chunks
		//Seq tick 0
		seqtickzero();
		
		target.clearTimeBreaks();
		int loopst = src.getLoopStartAddr();
		int looped = src.getLoopEndAddr();
		//System.err.println("loopst = 0x" + Integer.toHexString(loopst));
		//System.err.println("looped = 0x" + Integer.toHexString(looped));
		
		//Pop first delay
		if(isDelay(head)){
			context.current_tick = context.tickScale(head.getArg(0));
			head = head.getNextCommand();
		}
		
		//Handle remaining seq blocks (including branches...)
		CommandBlock block = nextBlock(-1);
		//int tpos = 0;
		while(block != null){
			//Check for loop points...
			int headaddr = block.getHeadAddress();
			if(headaddr == loopst || headaddr == looped){
				target.addTimeBreak((int)Math.floor(block.block_tick));
				//System.err.println("Time break added");
			}
			//System.err.println("DEBUG: Seq block address: 0x" + Integer.toHexString(headaddr));
			//System.err.println("Tick: " + context.current_tick);
			
			/*for(int i = 0; i < block.cmd_count; i++){
				//Convert this command.
				NUSALSeqCommand ncmd = context.getSeqCommand(block.commands[i]);
				if(ncmd == null) continue;
				
				//Snap to nearest tick.
				int tick = (int)Math.floor(block.block_tick + block.cmd_ticks[i]);
				
				//Add command to builder
				target.addSeqCommandAtTick(tick, ncmd);
			}*/
			block.addToTargetMasterSeq(false);
			
			context.current_tick += block.len_ticks;
			if(!blockIsEnd(block)) block = nextBlock(-1);
			else block = null;
			
			if(block == null){
				//Check branches.
				while(!branches.isEmpty()){
					JaiseqCommand jcmd = branches.pop();
					//System.err.println("DEBUG: Seq branch found: 0x" + Integer.toHexString(jcmd.getAddress()));
					if(context.handled.contains(jcmd.getAddress())) continue;
					//System.err.println("DEBUG: Processing seq branch: 0x" + Integer.toHexString(jcmd.getAddress()));
					head = jcmd;
					block = nextBlock(-1);
					if(block != null) break;
				}
			}
		}
		
		//Handle tracks
		convertTrack(4);
		for(int i = 0; i < 16; i++){
			//convertTrack(i);
		}

		return target.buildSeq();
	}
	
	protected NUSALSeq convert(){
		//Don't forget to translate the weird-ass program/bank numbers
		NUSALSeqGenerator target = new NUSALSeqGenerator();
		
		//Scan Jaiseq
		src.setBackjumpsBlock(true);
		src.setConditionFlag(true);
		src.scanMasterTrack();
		
		//Set parameters in generator
		//target.disableCompression(); //DEBUG
		target.setTimebase(src.getInitTimebase());
		int[] loop = src.getLoopPointsTicks();
		if(loop != null){
			target.setLoop(loop[0], loop[1]);	
		}
		target.setProgramRemap(true);
		
		//Play to generator
		src.playThroughTo(target);
		target.complete();
		
		//Voice graph (DEBUG)
		/*byte[] vgraph = target.getVoicesAtTickTable();
		int tcount = target.getOutTick();
		for(int i = 0; i < tcount; i++){
			System.out.println(vgraph[i]);
		}*/
		
		return target.getOutput();
	}
	
	/*--- Test ---*/
	
	public static void printCommandMap(NUSALSeqCommandMap map){
		if(map == null) return;
		System.err.println("Jseq2Useq.printCommandMap ------------------");
		List<Integer> keys = map.getOrderedKeys();
		for(Integer k : keys){
			List<NUSALSeqCommand> cmdlist = map.getValues(k);
			if(cmdlist != null && !cmdlist.isEmpty()){
				System.err.print(String.format("%06d", k));
				for(NUSALSeqCommand cmd : cmdlist){
					System.err.print("\t" + cmd.toMMLCommand());
				}
				System.err.print("\n");
			}
		}
	}
	
	public static void main(String[] args){
		//TODO
		if(args.length < 2){
			System.err.println("Please provide arguments: input.bms outstem");
			System.exit(1);
		}
		
		String inpath = args[0];
		String outstem = args[1];
		
		try{
			Jaiseq jaiseq = Jaiseq.readJaiseq(FileBuffer.createBuffer(inpath, true));
			System.err.println("Jaiseq read: " + inpath);
			
			//Try conversion.......
			NUSALSeq nseq = Jseq2Useq.convertJaiseq(jaiseq);
			System.err.println("Conversion completed with something...");
			
			String muspath = outstem + "_z64.mus";
			BufferedWriter bw = new BufferedWriter(new FileWriter(muspath));
			nseq.exportMMLScript(bw);
			bw.close();
			System.err.println("N64 MML script exported to: " + muspath);
			
			String binpath = outstem + "_z64.aseq";
			nseq.writeTo(binpath);
			System.err.println("N64 seq bin exported to: " + binpath);
			
			String midpath = outstem + "_z64.mid";
			MIDI mid = nseq.toMidi();
			mid.writeMIDI(midpath);
			System.err.println("N64 seq midi convert exported to: " + midpath);
			
			//Tick tables
			String tt_path = outstem + "_ttu";
			SeqEventLister el = new SeqEventLister(tt_path, jaiseq.getInitTimebase());
			//nseq.writeDebugTickTables(tt_path);
			nseq.playTo(el, false);
			el.complete();
			
			tt_path = outstem + "_ttj";
			el = new SeqEventLister(tt_path, jaiseq.getInitTimebase());
			jaiseq.playThroughTo(el);
			el.complete();
		}
		catch(Exception ex){
			ex.printStackTrace();
			System.exit(1);
		}
		
	}
	
}
