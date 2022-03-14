package waffleoRai_SeqSound.jaiseq;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import waffleoRai_DataContainers.CoverageMap1D;
import waffleoRai_SeqSound.jaiseq.cmd.EJaiseqCmd;
import waffleoRai_SeqSound.jaiseq.cmd.JaiseqCommands.*;
import waffleoRai_SeqSound.n64al.NUSALSeq;
import waffleoRai_SeqSound.n64al.NUSALSeqCommand;
import waffleoRai_SeqSound.n64al.NUSALSeqCommandMap;
import waffleoRai_SeqSound.n64al.cmd.FCommands.*;
import waffleoRai_SeqSound.n64al.cmd.NUSALSeqNoteCommand;
import waffleoRai_SeqSound.n64al.cmd.SeqCommands.*;
import waffleoRai_SeqSound.n64al.cmd.ChannelCommands.*;
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
	private Map<Integer, NUSALSeqCommand> ncommands; //For linking. Mapped by JAISEQ address
	private Map<Integer, CommandBlock> subroutines; //Lengths of subroutines. Address of first cmd -> ticklen
	//private LinkedList<JaiseqCommand> dfs_stack;
	
	private NUSALSeqBuilder target;
	private int src_timebase = 48;
	private double time_factor = 1.0; //Default 48 ppqn
	private double current_tick = 0.0;
	
	private int[] programmap; //0x0000bbpp (notes gc bank/prog in each slot)
	private int pmap_used = 0;
	private int cur_bank = 0;
	private int cur_prog = 0;
	
	private boolean warn_vibwidth = false;
	private boolean warn_vibdepth = false;
	
	/*--- Init ---*/
	
	private Jseq2Useq(){}
	
	private Jseq2Useq(Jaiseq input){
		src = input;
		head = src.getHead();
		branches = new LinkedList<JaiseqCommand>();
		ch_heads = new ArrayList<LinkedList<JaiseqCommand>>(16);
		for(int i = 0; i < 16; i++) ch_heads.add(new LinkedList<JaiseqCommand>());
		programmap = new int[126];
		Arrays.fill(programmap, -1);
		handled = new TreeSet<Integer>();
		ncommands = new HashMap<Integer, NUSALSeqCommand>();
		subroutines = new HashMap<Integer, CommandBlock>();
		//dfs_stack = new LinkedList<JaiseqCommand>();
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
		public NUSALSeqCommand[] layer_heads;
		public double[] layer_head_times;
		
		public CommandBlock(int alloc){
			block_tick = -1.0;
			cmd_count = 0;
			commands = new JaiseqCommand[alloc];
			cmd_ticks = new double[alloc];
			//n64cmds = new NUSALSeqCommand[alloc];
			len_ticks = 0.0;
			voices = new CommandBlock[7];
			layer_heads = new NUSALSeqCommand[4];
			layer_head_times = new double[4];
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
			
			//If note off, go ahead and convert it...
			if(cmd instanceof JSC_NoteOff) {
				JaiseqCommand oncmd = commands[cmd_count-1];
				getNoteCommand(oncmd, cmd_ticks[cmd_count-1], len_ticks);
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
		
		public CoverageMap1D genCoverageMap() {
			//TODO
			CoverageMap1D map = new CoverageMap1D();
			
			int i = 0;
			while(i < cmd_count) {
				JaiseqCommand cmd = commands[i];
				//Only checks note on/off
				
				
				i++;
			}
			
			return null;
		}
		
		public int addVoices(int ch, NUSALSeqCommandMap[] voxmap) {
			//Needs to handle >4 simultaneous voices
			//+also determine how many are used
			
			int ucount = 0;
			for(int i = 0; i < 7; i++) {
				if(voices[i] != null) ucount++;
			}
			
			if(ucount > 4) {
				System.err.println("Warning: Track block starting at 0x" + Integer.toHexString(commands[0].getAddress())
							+ " uses more than 4 voices. Trying to merge...");	
			}
			
			//Distribute into layers in target...
			int[] vpos = new int[7]; //Idx position in source blocks
			NUSALSeqNoteCommand[] lastnotes = new NUSALSeqNoteCommand[4]; //Last note obj added to each layer
			int[] lpos = new int[4]; //Tick position for target layers
			
			//This is gross but idc - fix later...
			while(true) {
				
				//Pick voice to look at next.
				int svox = -1; 
				double mintick = Double.MAX_VALUE;
				double vtick = 0.0;
				for(int i = 0; i < 7; i++) {
					if(voices[i] == null) continue;
					if(vpos[i] >= voices[i].cmd_count) continue;
					vtick = voices[i].cmd_ticks[vpos[i]];
					if(vtick < mintick) {
						mintick = vtick;
						svox = i;
					}
				}
				if(svox < 0) break; //All done.
				
				//Grab next note event from chosen voice.
				JaiseqCommand oncmd = voices[svox].commands[vpos[svox]];
				double ontick = voices[svox].cmd_ticks[vpos[svox]];
				double offtick = voices[svox].cmd_ticks[vpos[svox]+1];
				NUSALSeqNoteCommand ncmd = getNoteCommand(oncmd, ontick, offtick);
				int ontick_i = (int)Math.floor(ontick + block_tick);
				
				//Decide where to put it. Find minimum pos.
				int tvox = -1;
				int mttick = Integer.MAX_VALUE;
				for(int i = 0; i < 4; i++) {
					if(lpos[i] < mttick) {
						tvox = i;
						mttick = lpos[i];
					}
				}
				
				if(mttick > ontick_i) {
					//Need to shorten previous note.
					NUSALSeqNoteCommand vlast = lastnotes[tvox];
					vlast.shortenTimeBy(mttick - ontick_i);
				}
				
				//Add to target
				if(voxmap != null) {
					voxmap[tvox].addCommand(ontick_i, ncmd);
				}
				else {
					target.addLayerCommandAtTick(ontick_i, ch, tvox, ncmd);
				}
				
				if(layer_heads[tvox] == null) {
					layer_heads[tvox] = ncmd;
					layer_head_times[tvox] = vpos[svox];
				}
				
				//Save note, advance positions
				lastnotes[tvox] = ncmd;
				lpos[tvox] += ncmd.getTime();
				vpos[svox] += 2;
			}
			
			return ucount;
		}
		
		public void addToTarget(int channel) {
			//TODO
			//TODO Add timed perf command support
			
			int voxused = addVoices(channel, null);
			
			//Channel level...
			JaiseqCommand cmd = null;
			NUSALSeqCommand ncmd = null;
			for(int i = 0; i < cmd_count; i++) {
				//For jumps and calls, remember to add to layers too.
				cmd = commands[i];
				int tick = (int)Math.floor(cmd_ticks[i]);
				if(cmd instanceof JSC_Jump || cmd instanceof JSC_Call) {
					ncmd = getChannelCommand(cmd);
					//Need to find voice jump targets...
				}
				else if(cmd instanceof JSC_Return || cmd instanceof JSC_EndTrack) {
	
				}
				else if(cmd instanceof JSC_SetPerf) {
					//May need to add some more if over time...
				}
				else {
					
				}
			}
			
		}
		
		public void addAsSubroutine(int channel) {
			//TODO
			//TODO Add timed perf command support
			
			//Since we go DFS for subroutines, it assumes any inner calls
			// are already handled?
			
			NUSALSeqCommandMap chmap = new NUSALSeqCommandMap();
			NUSALSeqCommandMap[] voxmap = new NUSALSeqCommandMap[4];
			for(int i = 0; i < 4; i++) voxmap[i] = new NUSALSeqCommandMap();
			double vol = 1.0;
			double pan = 0.0;
			double rvb = 0.0;
			double psh = 0.0;
			
			int voxcount = addVoices(channel, voxmap);
			
			//Channel commands...
			JaiseqCommand cmd = null;
			NUSALSeqCommand ncmd = null;
			for(int i = 0; i < cmd_count; i++) {
				//For jumps and calls, remember to add to layers too.
				cmd = commands[i];
				int tick = (int)Math.floor(cmd_ticks[i]);
				if(cmd instanceof JSC_Jump || cmd instanceof JSC_Call) {
					ncmd = getChannelCommand(cmd); //This already handles channel link
					//Need to find voice jump targets...
					JaiseqCommand ref = cmd.getReferencedCommand();
					CommandBlock refblock = subroutines.get(ref.getAddress());
					for(int j = 0; j < voxcount; j++) {
						NUSALSeqCommand ljump = null;
						if(cmd instanceof JSC_Call) {
							ljump = new C_Call(-1);
						}
						else {
							ljump = new C_Jump(-1);
						}
						ljump.setReference(refblock.layer_heads[j]);
						voxmap[j].addCommand((int)Math.floor(refblock.layer_head_times[j]), ljump);
					}
					
					chmap.addCommand(tick, ncmd);
				}
				else if(cmd instanceof JSC_Return || cmd instanceof JSC_EndTrack) {
					ncmd = getChannelCommand(cmd);
					chmap.addCommand(tick, ncmd);
					for(int j = 0; j < voxcount; j++) {
						NUSALSeqCommand vcmd = new C_EndRead();
						voxmap[j].addCommand(tick, vcmd);
					}
				}
				else if(cmd instanceof JSC_SetPerf) {
					//May need to add some more if over time...
					JSC_SetPerf pcmd = (JSC_SetPerf)cmd;
					if(cmd.args.length < 3) {
						//Normal.
						ncmd = getChannelCommand(cmd);
						chmap.addCommand(tick, ncmd);
						
						switch(cmd.getArg(0)) {
						case Jaiseq.PERF_TYPE_PAN:
							pan = pcmd.getValue(); break;
						case Jaiseq.PERF_TYPE_PITCHBEND:
							psh = pcmd.getValue(); break;
						case Jaiseq.PERF_TYPE_RVB:
							rvb = pcmd.getValue(); break;
						case Jaiseq.PERF_TYPE_VOL:
							vol = pcmd.getValue(); break;
						}
					}
					else {
						//Has time.
						int ctime = (int)Math.floor(tickScale(cmd.getArg(0)));
						NUSALSeqCommand ocmd = null;
						if(Jaiseq.PERFTIME_MODE_LINEARINC) {
							//TODO
							//Last command
							ncmd = getChannelCommand(cmd);
							
							if(ncmd != null) {
								chmap.addCommand(tick+ctime, ncmd);
								
								//Leadup commands TODO
								double last = 0.0; double v = 0.0;
								byte last_out = 0; byte v_out = 0;
								double amtpertick = 0.0;
								double targ = pcmd.getValue();
								switch(cmd.getArg(0)) {
								case Jaiseq.PERF_TYPE_PAN:
									last = pan;
									last_out = Jaiseq.toMIDIPan(last);
									amtpertick = (targ - pan)/(double)ctime;
									for(int k = 0; k < ctime-1; k++) {
										v = last + amtpertick;
										v_out = Jaiseq.toMIDIPan(v);
										if(v_out != last_out) {
											//New event
											ocmd = new C_C_Pan(v_out);
											chmap.addCommand(tick+k, ocmd);
										}
										last = v; last_out = v_out;
									}
									break;
								case Jaiseq.PERF_TYPE_PITCHBEND:
									last = psh;
									last_out = Jaiseq.to8BitPitchbend(psh);
									amtpertick = (targ - psh)/(double)ctime;
									for(int k = 0; k < ctime-1; k++) {
										v = last + amtpertick;
										v_out = Jaiseq.to8BitPitchbend(v);
										if(v_out != last_out) {
											//New event
											ocmd = new C_C_PitchBend(v_out);
											chmap.addCommand(tick+k, ocmd);
										}
										last = v; last_out = v_out;
									}
									break;
								case Jaiseq.PERF_TYPE_RVB:
									last = rvb;
									last_out = Jaiseq.to8BitReverb(rvb);
									amtpertick = (targ - rvb)/(double)ctime;
									for(int k = 0; k < ctime-1; k++) {
										v = last + amtpertick;
										v_out = Jaiseq.to8BitReverb(v);
										if(v_out != last_out) {
											//New event
											ocmd = new C_C_Reverb(v_out);
											chmap.addCommand(tick+k, ocmd);
										}
										last = v; last_out = v_out;
									}
									break;
								case Jaiseq.PERF_TYPE_VOL:
									last = vol;
									last_out = Jaiseq.toMIDIVolume(last);
									amtpertick = (targ - vol)/(double)ctime;
									for(int k = 0; k < ctime-1; k++) {
										v = last + amtpertick;
										v_out = Jaiseq.toMIDIVolume(v);
										if(v_out != last_out) {
											//New event
											ocmd = new C_C_Volume(v_out);
											chmap.addCommand(tick+k, ocmd);
										}
										last = v; last_out = v_out;
									}
									break;
								}
								
								//Update param val
								switch(cmd.getArg(0)) {
								case Jaiseq.PERF_TYPE_PAN:
									pan = targ; break;
								case Jaiseq.PERF_TYPE_PITCHBEND:
									psh = targ; break;
								case Jaiseq.PERF_TYPE_RVB:
									rvb = targ; break;
								case Jaiseq.PERF_TYPE_VOL:
									vol = targ; break;
								}
							}
						}
						else {
							//First command
							ncmd = getChannelCommand(cmd);
							if(ncmd != null) {
								chmap.addCommand(tick, ncmd);
								
								//Second command
								switch(cmd.getArg(0)) {
								case Jaiseq.PERF_TYPE_PAN:
									ocmd = new C_C_Pan(Jaiseq.toMIDIPan(pan));
									break;
								case Jaiseq.PERF_TYPE_PITCHBEND:
									ocmd = new C_C_PitchBend(Jaiseq.toMIDIPan(psh));
									break;
								case Jaiseq.PERF_TYPE_RVB:
									ocmd = new C_C_Reverb(Jaiseq.toMIDIPan(rvb));
									break;
								case Jaiseq.PERF_TYPE_VOL:
									ocmd = new C_C_Volume(Jaiseq.toMIDIPan(vol)); 
									break;
								}
								
								if(ocmd != null) {
									chmap.addCommand(tick+ctime, ocmd);
								}	
							}
						}
					}
				}
				else {
					ncmd = getChannelCommand(cmd);
					if(ncmd != null) chmap.addCommand(tick, ncmd);
				}
			}
			
		}
		
	}
	
	/*--- Command 2 Command ---*/
	
	protected NUSALSeqNoteCommand getNoteCommand(JaiseqCommand oncmd, double ontime, double offtime) {
		NUSALSeqCommand ncmd = ncommands.get(oncmd.getAddress());
		if(ncmd == null) {
			int notelen = (int)(Math.floor(offtime) - Math.floor(ontime));
			ncmd = target.generateLayerNote((byte)oncmd.getArg(0), (byte)oncmd.getArg(2), notelen);
			ncommands.put(oncmd.getAddress(), ncmd);	
		}
		if(ncmd instanceof NUSALSeqNoteCommand) {
			//It better be.
			return (NUSALSeqNoteCommand)ncmd;
		}
		return null;
	}
	
	protected int getTargetProgramIndex() {
		int lookup = (cur_bank & 0xff) << 8;
		lookup |= (cur_prog & 0xFF);
		for(int i = 0; i < pmap_used; i++) {
			if(programmap[i] == lookup) return i;
		}
		//Not found. Add.
		programmap[pmap_used] = lookup;
		return pmap_used++;
	}
	
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
		if(in == null) return null;
		NUSALSeqCommand ncmd = ncommands.get(in.getAddress());
		if(ncmd != null) return ncmd;
		
		JaiseqCommand jref = null;
		if(in instanceof JSC_SetBank){
			cur_bank = in.getArg(0);
			//Sets converter state, but returns null.
		}
		else if(in instanceof JSC_SetProgram){
			int prog = in.getArg(0);
			if(prog != cur_prog) {
				cur_prog = prog;
				prog = getTargetProgramIndex();
				ncmd = new C_C_ChangeProgram(prog);
			}
		}
		else if(in instanceof JSC_VibWidth){
			if(!warn_vibwidth) {
				System.err.println("WARNING: vibwidth command found - scaling to N64 may not be accurate.");
				warn_vibwidth = true;
			}
			ncmd = new C_C_VibratoFreq(in.getArg(0));
		}
		else if(in instanceof JSC_VibDepth){
			if(!warn_vibdepth) {
				System.err.println("WARNING: vibdepth command found - scaling to N64 may not be accurate.");
				warn_vibdepth = true;
			}
			ncmd = new C_C_VibratoDepth(in.getArg(0));
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
			JSC_SetPerf perfcmd = (JSC_SetPerf)in;
			switch(in.getArg(0)) {
			case Jaiseq.PERF_TYPE_PAN:
				ncmd = new C_C_Pan(Byte.toUnsignedInt(Jaiseq.toMIDIPan((perfcmd.getValue()))));
				break;
			case Jaiseq.PERF_TYPE_PITCHBEND:
				ncmd = new C_C_PitchBend(Jaiseq.to8BitPitchbend(perfcmd.getValue()));
				break;
			case Jaiseq.PERF_TYPE_RVB:
				ncmd = new C_C_Reverb(Jaiseq.to8BitReverb(perfcmd.getValue()));
				break;
			case Jaiseq.PERF_TYPE_VOL:
				ncmd = new C_C_Volume(Jaiseq.toMIDIVolume(perfcmd.getValue()));
				break;
			default:
				System.err.println("WARNING: Unknown perf command at 0x" + Integer.toHexString(in.getAddress()) + "! Review manually!");
				break;
			}
		}
		else if(in instanceof JSC_SetParam){
			switch(in.getArg(0)) {
			case Jaiseq.PARAM_TYPE_BANK:
				cur_bank = in.getArg(0);
				break;
			case Jaiseq.PARAM_TYPE_PROG:
				int prog = in.getArg(0);
				if(prog != cur_prog) {
					cur_prog = prog;
					prog = getTargetProgramIndex();
					ncmd = new C_C_ChangeProgram(prog);
				}
				break;
			default:
				System.err.println("WARNING: Unknown param command at 0x" + Integer.toHexString(in.getAddress()) + "! Review manually!");
				break;	
			}
		}
		else if(in instanceof JSC_EndTrack){
			ncmd = new C_EndRead();
		}
		
		if(ncmd == null) return null;
		
		ncmd.setLabel(in.getLabel());
		
		//Handle reference
		if(jref != null){
			NUSALSeqCommand rcmd = getChannelCommand(jref);
			if(rcmd != null){
				ncmd.setReference(rcmd);
				//Put as branch.
				//newBranch(jref);
			}
		}
		
		//Map
		ncommands.put(in.getAddress(), ncmd);
		
		return ncmd;
	}
	
	protected NUSALSeqCommand getLayerCommand(JaiseqCommand in){
		//TODO
		if(in == null) return null;
		NUSALSeqCommand ncmd = ncommands.get(in.getAddress());
		if(ncmd != null) return ncmd;
		
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
	
	protected CommandBlock handleCall(JaiseqCommand cmd, int channel){
		CommandBlock sub = subroutines.get(cmd.getAddress());
		if(sub != null){
			//current_tick += sub.len_ticks;
			return sub;
		}
		
		JaiseqCommand parent_head = head;
		double parent_tick = current_tick;
		
		head = cmd;
		current_tick = 0.0;
		
		sub = nextBlock(channel);
		sub.addAsSubroutine(channel);
		subroutines.put(cmd.getAddress(), sub);
		
		head = parent_head;
		//current_tick = parent_tick + sub.len_ticks;
		current_tick = parent_tick;
		
		return sub;
	}
	
	protected CommandBlock handleBranch(JaiseqCommand src_command, int channel){
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
		if(handled.contains(head.getAddress())) return null;
		
		//Get commands until block end.
		LinkedList<JaiseqCommand> cmds = new LinkedList<JaiseqCommand>();
		int dcount = 0;
		
		cmds.add(head); handled.add(head.getAddress());
		if(head instanceof JSC_Wait) dcount++;
		if(head.getReferencedCommand() != null){
			handleBranch(head, channel);
		}
		head = head.getNextCommand();
		
		while(head != null && head.getLabel() == null && !handled.contains(head.getAddress())){
			EJaiseqCmd ecmd = head.getCommandEnum();	
			cmds.add(head);
			handled.add(head.getAddress());
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
		//I think subroutine discovery will have to be DFS
		//	since the tick lengths matter...
		LinkedList<JaiseqCommand> headlist = ch_heads.get(index);
		if(headlist.isEmpty()) return;
		current_tick = 0.0;

		while(!headlist.isEmpty()){
			head = headlist.pop();
			CommandBlock block = nextBlock(index);
			while(block != null){
				//Add block
				block.addToTarget(index);
				
				//Increment time
				current_tick += block.len_ticks;
				
				//Add branches to headlist
				while(!branches.isEmpty()) {
					headlist.add(branches.pop());
				}
				
				//Next
				block = nextBlock(index);
			}	
		}
		
		//Subroutines
		for(CommandBlock sub : subroutines.values()) {
			sub.addAsSubroutine(index);
		}
		subroutines.clear();
		
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
		CommandBlock block = nextBlock(-1);
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
			if(!blockIsEnd(block)) block = nextBlock(-1);
			else block = null;
		}
		//Seq Branches
		while(!branches.isEmpty()){
			JaiseqCommand jcmd = branches.pop();
			if(handled.contains(jcmd.getAddress())) continue; //In previous block.
			head = jcmd;
			block = nextBlock(-1);
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
