package waffleoRai_SeqSound.jaiseq.tools;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import waffleoRai_SeqSound.jaiseq.Jaiseq;
import waffleoRai_SeqSound.jaiseq.JaiseqCommand;
import waffleoRai_SeqSound.jaiseq.cmd.JaiseqCommands.*;
import waffleoRai_SeqSound.n64al.NUSALSeqCmdType;
import waffleoRai_SeqSound.n64al.NUSALSeqCommand;
import waffleoRai_SeqSound.n64al.cmd.NUSALSeqNoteCommand;
import waffleoRai_SeqSound.n64al.cmd.NUSALSeqWaitCommand;
import waffleoRai_SeqSound.n64al.cmd.ChannelCommands.*;
import waffleoRai_SeqSound.n64al.cmd.FCommands.*;
import waffleoRai_SeqSound.n64al.cmd.SeqCommands.*;
import waffleoRai_SeqSound.n64al.seqgen.NUSALSeqBuilder;

class JaiUConvertContext {
	
	public static final double N64_TIMEBASE = 48.0;

	protected Set<Integer> handled; //Addresses of commands that have been assigned to a block.
	protected Map<Integer, NUSALSeqCommand> ncommands; //For linking. Mapped by JAISEQ address
	protected Map<Integer, JaiUConvertCommandBlock> subroutines;
	protected Map<Integer, JaiseqCommand> ly2ch_cmds;
	
	private NUSALSeqBuilder target;
	private int src_timebase = 48;
	private double time_factor = 1.0; //Default 48 ppqn
	protected double current_tick = 0.0;
	
	protected JaiseqCommand last_ch_cmd;
	
	private int[] programmap; //0x0000bbpp (notes gc bank/prog in each slot)
	private int pmap_used = 0;
	private int cur_bank = 0;
	private int cur_prog = 0;
	
	private boolean warn_vibwidth = false;
	private boolean warn_vibdepth = false;
	
	/*---- Init ----*/
	
	public JaiUConvertContext(){
		programmap = new int[126];
		Arrays.fill(programmap, -1);
		handled = new TreeSet<Integer>();
		ncommands = new HashMap<Integer, NUSALSeqCommand>();
		subroutines = new HashMap<Integer, JaiUConvertCommandBlock>();
		target = new NUSALSeqBuilder();
		ly2ch_cmds = new HashMap<Integer, JaiseqCommand>();
	}
	
	public void reset(){
		//TODO
	}
	
	/*---- Getters ----*/
	
	public NUSALSeqBuilder getTarget(){return target;}
	
	/*---- Setters ----*/
	
	public void setTarget(NUSALSeqBuilder obj){target = obj;}
	
	public void setTimebase(int ppqn){
		src_timebase = ppqn;
		time_factor = N64_TIMEBASE / (double)src_timebase;
		System.err.println("Timebase set: " + src_timebase + " | Time factor: " + time_factor);
	}
	
	/*---- Utilities ----*/
	
	public double tickScale(int inTicks){
		double delay = (double)inTicks;
		delay *= time_factor;
		return delay;
	}
	
	public NUSALSeqNoteCommand getNoteCommand(JaiseqCommand oncmd, double ontime, double offtime) {
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
	
	public int getTargetProgramIndex() {
		int lookup = (cur_bank & 0xff) << 8;
		lookup |= (cur_prog & 0xFF);
		for(int i = 0; i < pmap_used; i++) {
			if(programmap[i] == lookup) return i;
		}
		//Not found. Add.
		programmap[pmap_used] = lookup;
		return pmap_used++;
	}
	
	public NUSALSeqCommand getSeqCommand(JaiseqCommand in){
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
		else if(in instanceof JSC_Wait){
			//Dummy Wait. Used only for references.
			ncmd = new C_Wait(0);
			if(in.getLabel() == null){
				//Make sure it has a label, so can be found again?
				in.setLabel("jaicmd_0x" + Integer.toHexString(in.getAddress()));
			}
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
			}
			while(rcmd == null){
				//Might be a non-convertable one like vsync
				//System.err.println("ref to 0x" + Integer.toHexString(jref.getAddress()) + " not converted. Trying next...");
				jref = jref.getNextCommand();
				if(jref == null) break;
				rcmd = getChannelCommand(jref);
				if(rcmd != null){
					ncmd.setReference(rcmd);
				}
			}
		}
		
		//Map
		ncommands.put(in.getAddress(), ncmd);
		
		return ncmd;
	}
	
	public NUSALSeqCommand getChannelCommand(JaiseqCommand in){
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
		else if(in instanceof JSC_Wait){
			//Dummy Wait. Used only for references.
			//Uses superclass for channels so can be easily switched if layer or < 16 ticks
			ncmd = new NUSALSeqWaitCommand(NUSALSeqCmdType.WAIT, 0);
			if(in.getLabel() == null){
				//Make sure it has a label, so can be found again?
				in.setLabel("jaicmd_0x" + Integer.toHexString(in.getAddress()));
			}
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
			}
			while(rcmd == null){
				//Might be a non-convertable one like vsync
				jref = jref.getNextCommand();
				if(jref == null) break;
				rcmd = getChannelCommand(jref);
				if(rcmd != null){
					ncmd.setReference(rcmd);
				}
			}
		}
		
		//Map
		ncommands.put(in.getAddress(), ncmd);
		
		return ncmd;
	}
	
}
