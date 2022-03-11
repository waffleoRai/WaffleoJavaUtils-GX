package waffleoRai_SeqSound.jaiseq;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import waffleoRai_SeqSound.jaiseq.cmd.EJaiseqCmd;
import waffleoRai_SeqSound.jaiseq.cmd.JaiseqCommands.*;
import waffleoRai_SeqSound.n64al.NUSALSeq;
import waffleoRai_SeqSound.n64al.NUSALSeqCommand;
import waffleoRai_SeqSound.n64al.NUSALSeqCommandMap;
import waffleoRai_SeqSound.n64al.cmd.FCommands.*;
import waffleoRai_SeqSound.n64al.cmd.SeqCommands.*;
import waffleoRai_SeqSound.n64al.seqgen.NUSALSeqBuilder;

public class Jseq2Useq {
	
	public static final int N64_TIMEBASE = 48;
	public static final int DEFO_BLOCK_ALLOC = 128;
	
	/*--- Instance Variables ---*/
	
	private Jaiseq src;
	private JaiseqCommand head;
	
	private ArrayList<LinkedList<JaiseqCommand>> ch_heads;
	private LinkedList<JaiseqCommand> branches;
	private Set<Integer> handled; //Addresses of commands that have been assigned to a block.
	private int[] programmap; //0x0000bbpp (notes gc bank/prog in each slot)
	private Map<Integer, NUSALSeqCommand> ncommands; //For linking. Mapped by JAISEQ address
	private Map<Integer, CommandBlock> subroutines; //Lengths of subroutines. Address of first cmd -> ticklen
	private LinkedList<JaiseqCommand> dfs_stack;
	
	private NUSALSeqBuilder target;
	private int src_timebase = 48;
	private double time_factor = 1.0; //Default 48 ppqn
	private double current_tick = 0.0;
	
	/*--- Init ---*/
	
	private Jseq2Useq(){}
	
	private Jseq2Useq(Jaiseq input){
		src = input;
		head = src.getHead();
		branches = new LinkedList<JaiseqCommand>();
		ch_heads = new ArrayList<LinkedList<JaiseqCommand>>(16);
		for(int i = 0; i < 16; i++) ch_heads.add(new LinkedList<JaiseqCommand>());
		programmap = new int[32];
		Arrays.fill(programmap, -1);
		handled = new TreeSet<Integer>();
		ncommands = new HashMap<Integer, NUSALSeqCommand>();
		subroutines = new HashMap<Integer, CommandBlock>();
		dfs_stack = new LinkedList<JaiseqCommand>();
	}
	
	public static NUSALSeq convertJaiseq(Jaiseq input){
		if(input == null) return null;
		Jseq2Useq conv = new Jseq2Useq(input);
		return conv.convert();
	}
	
	/*--- Inner Classes ---*/
	
	protected class CommandBlock{
		//Used for quantizing to N64 ticks from higher tick res.
		
		public double block_tick; //In N64 ticks
		public double len_ticks;
		
		public int cmd_count;
		
		public JaiseqCommand[] commands;
		public double[] cmd_ticks;
		//public NUSALSeqCommand[] n64cmds;
		
		//public int ch = -1;
		//public int ly = -1;		
		
		public CommandBlock[] voices = null;
		
		public CommandBlock(int alloc){
			block_tick = -1.0;
			cmd_count = 0;
			commands = new JaiseqCommand[alloc];
			cmd_ticks = new double[alloc];
			//n64cmds = new NUSALSeqCommand[alloc];
			len_ticks = 0.0;
			voices = new CommandBlock[7];
		}
		
		public void addCommand(JaiseqCommand cmd){
			if(cmd_count >= commands.length){
				int newalloc = commands.length + commands.length/2;
				JaiseqCommand[] temp = commands;
				commands = new JaiseqCommand[newalloc];
				for(int i = 0; i < temp.length; i++) commands[i] = temp[i];
				
				double[] dtemp = cmd_ticks;
				cmd_ticks = new double[newalloc];
				for(int i = 0; i < dtemp.length; i++) cmd_ticks[i] = dtemp[i];
				
				/*NUSALSeqCommand[] ntemp = n64cmds;
				n64cmds = new NUSALSeqCommand[newalloc];
				for(int i = 0; i < ntemp.length; i++) n64cmds[i] = ntemp[i];*/
			}
			
			commands[cmd_count] = cmd;
			cmd_ticks[cmd_count] = len_ticks;

			cmd_count++;
		}
		
		public void addDelay(double val){
			len_ticks += val;
		}
		
		public NUSALSeqCommandMap toSeqCommands(){
			NUSALSeqCommandMap map = new NUSALSeqCommandMap();
			
			double d_t = 0.0;
			for(int i = 0; i < cmd_count; i++){
				if(commands[i] == null) continue;
				d_t = block_tick + cmd_ticks[i];
				map.addCommand((int)Math.round(d_t), getSeqCommand(commands[i]));
			}
			
			return map;
		}
		
	}
	
	/*--- Command 2 Command ---*/
	
	protected NUSALSeqCommand getSeqCommand(JaiseqCommand in){
		if(in == null) return null;
		
		//Already there? (ie. if referenced previously)
		NUSALSeqCommand ncmd = ncommands.get(in.getAddress());
		JaiseqCommand jref = null;
		if(ncmd != null) return ncmd;
		
		if(in instanceof JSC_Tempo){
			ncmd = new C_S_SetTempo(in.getArg(0));
		}
		else if(in instanceof JSC_EndTrack){
			ncmd = new C_EndRead();
		}
		else if(in instanceof JSC_Timebase){
			//Throw warning
			System.err.println("WARNING: Unexpected timebase change at 0x" + Integer.toHexString(in.getAddress()) + "! Review manually!");
		}
		else if(in instanceof JSC_Jump){
			ncmd = new C_Jump(-1);
			jref = in.getReferencedCommand();
		}
		else if(in instanceof JSC_Call){
			ncmd = new C_Call(-1);
			jref = in.getReferencedCommand();
		}
		else if(in instanceof JSC_Return){
			ncmd = new C_EndRead();
		}
		else if(in instanceof JSC_SetPerf){
			JSC_SetPerf pcmd = (JSC_SetPerf)in;
			switch(in.getArg(0)){
			case Jaiseq.PERF_TYPE_VOL:
				ncmd = new C_S_SetMasterVolume((int)Math.round(pcmd.getValue() * 127.0));
				break;
			default:
				System.err.println("WARNING: Unknown perf command at 0x" + Integer.toHexString(in.getAddress()) + "! Review manually!");
				break;
			}
		}
		
		if(ncmd == null) return null;
		
		ncmd.setLabel(in.getLabel());
		
		//Handle reference
		if(jref != null){
			NUSALSeqCommand rcmd = getSeqCommand(jref);
			if(rcmd != null){
				ncmd.setReference(rcmd);
				//Put as branch.
				newBranch(jref);
			}
		}
		
		//Map
		ncommands.put(in.getAddress(), ncmd);
		
		return ncmd;
	}
	
	protected NUSALSeqCommand getChannelCommand(JaiseqCommand in){
		//TODO
		return null;
	}
	
	protected NUSALSeqCommand getLayerCommand(JaiseqCommand in){
		//TODO
		return null;
	}
	
	/*--- Internal Conversion Methods ---*/
	
 	private static boolean isDelay(JaiseqCommand cmd){
		if(cmd == null) return false;
		return (cmd instanceof JSC_Wait);
	}
	
 	protected boolean blockIsEnd(CommandBlock block){
 		if(block == null) return false;
 		JaiseqCommand last = block.commands[block.cmd_count-1];
 		if(last instanceof JSC_Return) return true;
 		if(last instanceof JSC_EndTrack) return true;
 		return false;
 	}
 	
	private double tickScale(int inTicks){
		double delay = (double)inTicks;
		delay /= time_factor;
		return delay;
	}
	
	private static boolean isInteger(double value){
		long l = (long)value;
		return (value == (double)l);
	}
	
	protected void newBranch(JaiseqCommand cmd){
		if(handled.contains(cmd.getAddress())) return;
		/*CommandBlock block = new CommandBlock(DEFO_BLOCK_ALLOC);
		block.addCommand(cmd);
		block.block_tick = current_tick;
		branches.add(block);*/
		branches.add(cmd);
		//handled.add(cmd.getAddress());
	}
	
	protected CommandBlock handleCall(JaiseqCommand cmd){
		CommandBlock sub = subroutines.get(cmd.getAddress());
		if(sub != null){
			//current_tick += sub.len_ticks;
			return sub;
		}
		
		JaiseqCommand parent_head = head;
		double parent_tick = current_tick;
		
		head = cmd;
		current_tick = 0.0;
		
		sub = nextBlock();
		subroutines.put(cmd.getAddress(), sub);
		
		head = parent_head;
		//current_tick = parent_tick + sub.len_ticks;
		current_tick = parent_tick;
		
		return sub;
	}
	
	protected CommandBlock handleBranch(JaiseqCommand src_command){
		if(head instanceof JSC_OpenTrack){
			ch_heads.get(src_command.getArg(0)).add(src_command.getReferencedCommand());
		}
		else if(head instanceof JSC_Jump){
			newBranch(src_command.getReferencedCommand());
		}
		else if(head instanceof JSC_Call){
			return handleCall(src_command.getReferencedCommand());
		}
		
		return null;
	}
	
	protected void seqtickzero(){
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
				src_timebase = head.getArg(0);
				time_factor = 48.0 / (double)src_timebase;
			}
			else if(ecmd == EJaiseqCmd.PERF_S8_NODUR){
				//Volume?
				if(head.getArg(0) == Jaiseq.PERF_TYPE_VOL){
					target.setInitVol(head.getArg(1));
				}
			}
			
			JaiseqCommand ref = head.getReferencedCommand();
			if(ref != null){
				newBranch(ref);
			}
			
			handled.add(head.getAddress());
			head = head.getNextCommand();
		}
		
	}
	
	protected CommandBlock nextBlock(){
		/*
		 * Pop commands until encounter...
		 * A. A new label
		 * B. An end track command
		 * C. A return command
		 * D. No "next" command
		 * E. A previously handled command
		 */
		if(head == null) return null;
		if(handled.contains(head.getAddress())) return null;
		
		//Get commands until block end.
		LinkedList<JaiseqCommand> cmds = new LinkedList<JaiseqCommand>();
		int dcount = 0;
		
		cmds.add(head); handled.add(head.getAddress());
		if(head instanceof JSC_Wait) dcount++;
		if(head.getReferencedCommand() != null){
			handleBranch(head);
		}
		head = head.getNextCommand();
		
		while(head != null && head.getLabel() == null && !handled.contains(head.getAddress())){
			EJaiseqCmd ecmd = head.getCommandEnum();	
			cmds.add(head);
			handled.add(head.getAddress());
			if(head instanceof JSC_Wait) dcount++;
			if(head.getReferencedCommand() != null) {
				handleBranch(head);
			}
			head = head.getNextCommand();
			
			if(ecmd == EJaiseqCmd.END_TRACK || ecmd == EJaiseqCmd.RETURN || ecmd == EJaiseqCmd.RETURN_COND){
				//End of block.
				break;	
			}
		}
		
		//Generate block.
		//Split by layer for tracks
		CommandBlock block = new CommandBlock(cmds.size() - dcount + 4);
		block.block_tick = current_tick;
		
		for(JaiseqCommand cmd : cmds){
			if(cmd instanceof JSC_Wait){
				block.addDelay(tickScale(cmd.getArg(0)));
			}
			else if(cmd instanceof JSC_NoteOn){
				int vox = cmd.getArg(1) - 1;
				if(block.voices[vox] == null){
					block.voices[vox] = new CommandBlock(cmds.size() - dcount);
					block.voices[vox].block_tick = block.len_ticks;
				}
				block.voices[vox].len_ticks = block.len_ticks - block.voices[vox].block_tick;
				block.voices[vox].addCommand(cmd);
			}
			else if(cmd instanceof JSC_NoteOff){
				int vox = cmd.getArg(0) - 1;
				if(block.voices[vox] != null){
					block.voices[vox].len_ticks = block.len_ticks - block.voices[vox].block_tick;
					block.voices[vox].addCommand(cmd);
				}
			}
			else{
				block.addCommand(cmd);
				if(cmd instanceof JSC_Call){
					CommandBlock sub = subroutines.get(cmd.getAddress());
					if(sub != null){
						block.addDelay(sub.len_ticks);
					}
				}
			}
		}
		
		return block;
	}
	
	protected void convertTrack(int index){
		//TODO
		//I think subroutine discovery will have to be DFS
		//	since the tick lengths matter...
		LinkedList<JaiseqCommand> headlist = ch_heads.get(index);
		if(headlist.isEmpty()) return;
		
		LinkedList<JaiseqCommand> stack = new LinkedList<JaiseqCommand>();
		while(!headlist.isEmpty()){
			head = headlist.pop();
			CommandBlock block = nextBlock();
			while(block != null){
				//TODO
				
				
				//Add block
				
				//Next
				block = nextBlock();
			}
			
			//Add branches to headlist
			
		}
		
		//Subroutines
		
	}
	
	protected NUSALSeq convert(){
		//TODO
		target = new NUSALSeqBuilder();
		
		//Do this in chunks
		//Seq tick 0
		seqtickzero();
		
		//Pop first delay
		if(isDelay(head)){
			current_tick = tickScale(head.getArg(0));
			head = head.getNextCommand();
		}
		
		//Handle remaining seq blocks (including branches...)
		CommandBlock block = nextBlock();
		//int tpos = 0;
		while(block != null){
			for(int i = 0; i < block.cmd_count; i++){
				//Convert this command.
				NUSALSeqCommand ncmd = getSeqCommand(block.commands[i]);
				if(ncmd == null) continue;
				
				//Snap to nearest tick.
				int tick = (int)Math.round(block.cmd_ticks[i]);
				
				//Add command to builder
				target.addSeqCommandAtTick(tick, ncmd);
			}
			
			current_tick += block.len_ticks;
			if(!blockIsEnd(block)) block = nextBlock();
			else block = null;
		}
		//Seq Branches
		while(!branches.isEmpty()){
			JaiseqCommand jcmd = branches.pop();
			if(handled.contains(jcmd.getAddress())) continue; //In previous block.
			head = jcmd;
			block = nextBlock();
			if(block != null){
				block.block_tick = 0.0;
				target.addSequenceSubroutine(block.toSeqCommands());
			}
		}
		
		//Handle tracks
		for(int i = 0; i < 16; i++){
			convertTrack(i);
		}
		
		//TODO
		
		return null;
	}
	
}
