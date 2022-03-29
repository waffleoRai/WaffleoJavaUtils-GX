package waffleoRai_SeqSound.jaiseq.tools;

import java.util.LinkedList;
import java.util.List;

import waffleoRai_SeqSound.jaiseq.Jaiseq;
import waffleoRai_SeqSound.jaiseq.JaiseqCommand;
import waffleoRai_SeqSound.jaiseq.cmd.JaiseqCommands.*;
import waffleoRai_SeqSound.n64al.NUSALSeqCmdType;
import waffleoRai_SeqSound.n64al.NUSALSeqCommand;
import waffleoRai_SeqSound.n64al.NUSALSeqCommandMap;
import waffleoRai_SeqSound.n64al.cmd.NUSALSeqNoteCommand;
import waffleoRai_SeqSound.n64al.cmd.NUSALSeqWaitCommand;
import waffleoRai_SeqSound.n64al.seqgen.NUSALSeqBuilder;
import waffleoRai_SeqSound.n64al.cmd.SeqCommands.*;
import waffleoRai_SeqSound.n64al.cmd.ChannelCommands.*;
import waffleoRai_SeqSound.n64al.cmd.FCommands.*;

class JaiUConvertCommandBlock {
	
	protected JaiUConvertContext context;
	
	protected double block_tick;
	protected double len_ticks;
	
	protected int cmd_count;
	protected int head_addr = -1;
	
	protected JaiseqCommand[] commands;
	protected double[] cmd_ticks;

	protected int transpose = 0;
	protected List<NUSALSeqNoteCommand> allnotes;
	protected JaiUConvertCommandBlock[] voices = null;
	
	protected NUSALSeqCommand ch_head;
	protected NUSALSeqCommand[] layer_heads;
	protected double[] layer_head_times;
	
	public JaiUConvertCommandBlock(int alloc, JaiUConvertContext ctxt){
		block_tick = -1.0;
		cmd_count = 0;
		commands = new JaiseqCommand[alloc];
		cmd_ticks = new double[alloc];
		len_ticks = 0.0;
		voices = new JaiUConvertCommandBlock[7];
		layer_heads = new NUSALSeqCommand[4];
		layer_head_times = new double[4];
		
		context = ctxt;
	}
	
	public int getHeadAddress(){
		if(head_addr >= 0) return head_addr;
		if(cmd_count < 1) return -1;
		return commands[0].getAddress();
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
			NUSALSeqNoteCommand notecmd = 
					context.getNoteCommand(oncmd, cmd_ticks[cmd_count-1], len_ticks);
			if(allnotes == null) {allnotes = new LinkedList<NUSALSeqNoteCommand>();}
			allnotes.add(notecmd);
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
			map.addCommand((int)Math.round(d_t), context.getSeqCommand(commands[i]));
		}
		
		return map;
	}
	
	public int addVoices(int ch, NUSALSeqCommandMap[] voxmap) {
		//Needs to handle >4 simultaneous voices
		//+also determine how many are used
		
		if(ch < 0) return 0; //Seq
		
		int ucount = 0;
		for(int i = 0; i < 7; i++) {
			if(voices[i] != null){
				ucount++;
				if(voices[i].allnotes != null){
					//System.err.println("allnotes ------");
					for(NUSALSeqNoteCommand cmd : voices[i].allnotes){
						//System.err.println(cmd.toMMLCommand());
						cmd.setEnvironmentalPitchShift(transpose);
					}
				}
			}
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
		double tickbase = 0.0;
		ucount = 0;
		//System.err.println("debug -- block base tick: " + block_tick);
		while(true) {
			
			//Pick voice to look at next.
			int svox = -1; 
			double mintick = Double.MAX_VALUE;
			double vtick = 0.0;
			for(int i = 0; i < 7; i++) {
				if(voices[i] == null) continue;
				tickbase = voices[i].block_tick + block_tick;
				if(vpos[i] >= voices[i].cmd_count) continue;
				vtick = voices[i].cmd_ticks[vpos[i]] + tickbase;
				if(vtick < mintick) {
					mintick = vtick;
					svox = i;
				}
			}
			if(svox < 0) break; //All done.
			tickbase = voices[svox].block_tick + block_tick;
			//System.err.println("debug -- vox block base tick: " + voices[svox].block_tick);
			
			//Grab next note event from chosen voice.
			JaiseqCommand oncmd = voices[svox].commands[vpos[svox]];
			double ontick = voices[svox].cmd_ticks[vpos[svox]] + tickbase;
			double offtick = voices[svox].cmd_ticks[vpos[svox]+1] + tickbase;
			NUSALSeqNoteCommand ncmd = context.getNoteCommand(oncmd, ontick, offtick);
			int ontick_i = (int)Math.floor(ontick);
			
			//Decide where to put it. Find maximum pos that's open.
			int tvox = -1;
			int mttick = -1;
			for(int i = 0; i < 4; i++) {
				if(lpos[i] > mttick) {
					if(lpos[i] <= ontick_i){
						//Available
						tvox = i;
						mttick = lpos[i];
					}
				}
			}
			
			if(tvox < 0) {
				//None available. Find min instead.
				mttick = Integer.MAX_VALUE;
				for(int i = 0; i < 4; i++) {
					if(lpos[i] < mttick) {
						tvox = i;
						mttick = lpos[i];
					}
				}
				
				//Need to shorten previous note.
				NUSALSeqNoteCommand vlast = lastnotes[tvox];
				vlast.shortenTimeBy(mttick - ontick_i);
			}
			
			//Add to target
			if(voxmap != null) {
				voxmap[tvox].addCommand(ontick_i, ncmd);
			}
			else {
				context.getTarget().addLayerCommandAtTick(ontick_i, ch, tvox, ncmd);
			}
			//System.err.println("JaiConvertCommandBlock.addVoices || Added note event -- " + ncmd.toMMLCommand() + " @ " + ontick_i + " | Vox " + svox + " -> layer " + tvox);
			
			if(layer_heads[tvox] == null) {
				layer_heads[tvox] = ncmd;
				layer_head_times[tvox] = ontick;
			}
			
			if((tvox+1) > ucount) ucount = tvox+1;
			
			//Save note, advance positions
			lastnotes[tvox] = ncmd;
			lpos[tvox] = ontick_i + ncmd.getTime();
			vpos[svox] += 2;
		}
		
		return ucount;
	}
	
	private static byte convertPerfValue(int perf_type, double in){
		switch(perf_type){
		case Jaiseq.PERF_TYPE_PAN:
			return Jaiseq.toMIDIPan(in);
		case Jaiseq.PERF_TYPE_PITCHBEND:
			return Jaiseq.to8BitPitchbend(in);
		case Jaiseq.PERF_TYPE_RVB:
			return Jaiseq.to8BitReverb(in);
		case Jaiseq.PERF_TYPE_VOL:
			return Jaiseq.toMIDIVolume(in);
		}
		return 0;
	}
	
	private NUSALSeqCommand addTimedPerfCommand(JSC_SetPerf pcmd, int tick, NUSALSeqCommandMap chmap, double[] perf_vals, boolean seq){
		int ctime = (int)Math.floor(context.tickScale(pcmd.getArg(2)));
		NUSALSeqCommand ncmd = null, ocmd = null;
		if(Jaiseq.PERFTIME_MODE_LINEARINC) {
			//Last command
			ncmd = context.getChannelCommand(pcmd);
			int ptype = pcmd.getArg(0);
			
			if(ncmd != null) {
				chmap.addCommand(tick+ctime, ncmd);
				
				//Leadup commands
				double last = perf_vals[ptype]; double v = 0.0;
				byte last_out = convertPerfValue(ptype, last); byte v_out = 0;
				double targ = pcmd.getValue();
				double amtpertick = (targ - last)/(double)ctime;
				
				for(int k = 0; k < ctime-1; k++) {
					v = last + amtpertick;
					v_out = convertPerfValue(ptype, v);
					if(v_out != last_out) {
						//New event
						switch(ptype) {
						case Jaiseq.PERF_TYPE_PAN:
							if(!seq) ocmd = new C_C_Pan(v_out);
							break;
						case Jaiseq.PERF_TYPE_PITCHBEND:
							if(!seq) ocmd = new C_C_PitchBend(v_out);
							break;
						case Jaiseq.PERF_TYPE_RVB:
							if(!seq) ocmd = new C_C_Reverb(v_out);
							break;
						case Jaiseq.PERF_TYPE_VOL:
							if(!seq) ocmd = new C_C_Volume(v_out); 
							else ocmd = new C_S_SetMasterVolume(v_out);
							break;
						}
						//System.err.println("Add time cmd to tick: " + (tick+k));
						if(ocmd != null) chmap.addCommand(tick+k, ocmd);
					}
					last = v; last_out = v_out;
				}

				//Update param val
				perf_vals[pcmd.getArg(0)] = pcmd.getValue();
			}
		}
		else {
			//First command
			ncmd = context.getChannelCommand(pcmd);
			if(ncmd != null) {
				chmap.addCommand(tick, ncmd);
				
				//Second command
				switch(pcmd.getArg(0)) {
				case Jaiseq.PERF_TYPE_PAN:
					if(!seq) ocmd = new C_C_Pan(Jaiseq.toMIDIPan(perf_vals[Jaiseq.PERF_TYPE_PAN]));
					break;
				case Jaiseq.PERF_TYPE_PITCHBEND:
					if(!seq) ocmd = new C_C_PitchBend(Jaiseq.to8BitPitchbend(perf_vals[Jaiseq.PERF_TYPE_PITCHBEND]));
					break;
				case Jaiseq.PERF_TYPE_RVB:
					if(!seq) ocmd = new C_C_Reverb(Jaiseq.to8BitReverb(perf_vals[Jaiseq.PERF_TYPE_RVB]));
					break;
				case Jaiseq.PERF_TYPE_VOL:
					if(!seq) ocmd = new C_C_Volume(Jaiseq.toMIDIVolume(perf_vals[Jaiseq.PERF_TYPE_VOL])); 
					else ocmd = new C_S_SetMasterVolume(Jaiseq.toMIDIVolume(perf_vals[Jaiseq.PERF_TYPE_VOL]));
					break;
				}
				
				if(ocmd != null) {
					chmap.addCommand(tick+ctime, ocmd);
				}	
			}
		}
		return ncmd;
	}
	
	public void addToTargetMasterSeq(boolean asSub){
		//System.err.println("JaiUConvertCommandBlock.addToTargetMasterSeq || Adding block 0x" + Integer.toHexString(head_addr) + " to seq.");
		NUSALSeqCommandMap seqmap = new NUSALSeqCommandMap();
		double[] perf_vals = new double[4];
		perf_vals[Jaiseq.PERF_TYPE_VOL] = 1.0; 
		
		JaiseqCommand cmd = null;
		NUSALSeqCommand ncmd = null;
		
		for(int i = 0; i < cmd_count; i++) {
			cmd = commands[i];
			int tick = (int)Math.floor(block_tick + cmd_ticks[i]);
			if(cmd instanceof JSC_SetPerf) {
				//May need to add some more if over time...
				JSC_SetPerf pcmd = (JSC_SetPerf)cmd;
				if(cmd.getArgCount() < 3) {
					//Normal.
					ncmd = context.getSeqCommand(cmd);
					seqmap.addCommand(tick, ncmd);
					perf_vals[cmd.getArg(0)] = pcmd.getValue();
				}
				else {
					//Has time.
					ncmd = addTimedPerfCommand(pcmd, tick, seqmap, perf_vals, true);
				}
			}
			else {
				ncmd = context.getSeqCommand(cmd);
				if(ncmd != null) seqmap.addCommand(tick, ncmd);
			}
		}
		
		if(cmd_ticks[0] > block_tick){
			//Starts on delay. Look for a reference...
			ncmd = context.ncommands.get(head_addr);
			if(ncmd == null){
				//Let's make a new one in case something refs it.
				ncmd = new C_Wait(0);
				context.ncommands.put(head_addr, ncmd);
				ncmd.setLabel("jaiblock_" + Integer.toHexString(head_addr));
			}
			seqmap.addCommand((int)Math.floor(block_tick), ncmd);
		}
		
		NUSALSeqBuilder target = context.getTarget();
		if(asSub){
			ch_head = target.genSequenceSubroutine(seqmap);
		}
		else{
			target.addSeqCommands(seqmap);
		}
	}
	
	public void addToTarget(int channel, boolean asSub) {
		//Returns sub chunk or head command
		if(channel < 0){
			addToTargetMasterSeq(asSub);
			return;
		}
		
		//Since we go DFS for subroutines, it assumes any inner calls
		// are already handled?
		
		NUSALSeqCommandMap chmap = new NUSALSeqCommandMap();
		NUSALSeqCommandMap[] voxmap = new NUSALSeqCommandMap[4];
		for(int i = 0; i < 4; i++) voxmap[i] = new NUSALSeqCommandMap();
		double[] perf_vals = new double[4];
		perf_vals[Jaiseq.PERF_TYPE_VOL] = 1.0; 
		
		int voxcount = addVoices(channel, voxmap); //Note: voxmap coords are abs
		//int voxcount = 0;
		
		//Channel commands...
		JaiseqCommand cmd = null;
		NUSALSeqCommand ncmd = null;
		for(int i = 0; i < cmd_count; i++) {
			//For jumps and calls, remember to add to layers too.
			cmd = commands[i];
			int tick = (int)Math.floor(cmd_ticks[i] + block_tick);
			if(cmd instanceof JSC_Jump || cmd instanceof JSC_Call) {
				ncmd = context.getChannelCommand(cmd); //This already handles channel link
				//Need to find voice jump targets...
				JaiseqCommand ref = cmd.getReferencedCommand();
				JaiUConvertCommandBlock refblock = context.subroutines.get(ref.getAddress());
				if(refblock != null){
					for(int j = 0; j < voxcount; j++) {
						if(refblock.layer_heads[j] == null) continue;
						NUSALSeqCommand ljump = null;
						if(cmd instanceof JSC_Call) {
							ljump = new C_Call(-1);
						}
						else {
							ljump = new C_Jump(-1);
						}
						ljump.setReference(refblock.layer_heads[j]);
						voxmap[j].addCommand((int)Math.floor(refblock.layer_head_times[j]+cmd_ticks[i] + block_tick), ljump);
					}	
				}
				chmap.addCommand(tick, ncmd);
			}
			else if(cmd instanceof JSC_Return || cmd instanceof JSC_EndTrack) {
				ncmd = context.getChannelCommand(cmd);
				chmap.addCommand(tick, ncmd);
				for(int j = 0; j < voxcount; j++) {
					NUSALSeqCommand vcmd = new C_EndRead();
					voxmap[j].addCommand(tick, vcmd);
				}
			}
			else if(cmd instanceof JSC_SetPerf) {
				//May need to add some more if over time...
				JSC_SetPerf pcmd = (JSC_SetPerf)cmd;
				if(cmd.getArgCount() < 3) {
					//Normal.
					ncmd = context.getChannelCommand(cmd);
					chmap.addCommand(tick, ncmd);
					perf_vals[cmd.getArg(0)] = pcmd.getValue();
				}
				else {
					//Has time.
					ncmd = addTimedPerfCommand(pcmd, tick, chmap, perf_vals, false);
				}
			}
			else {
				ncmd = context.getChannelCommand(cmd);
				if(ncmd != null) chmap.addCommand(tick, ncmd);
			}
		}
		
		if(transpose != 0){
			//System.err.println("transpose: " + transpose);
			chmap.addCommand((int)block_tick, new C_C_Transpose(transpose));
		}
		
		//Handle case where first cmd is delay.
		if(cmd_ticks[0] > block_tick){
			//Starts on delay. Look for a reference...
			ncmd = context.ncommands.get(head_addr);
			if(ncmd == null){
				//Let's make a new one in case something refs it.
				ncmd = new NUSALSeqWaitCommand(NUSALSeqCmdType.WAIT, 0);
				context.ncommands.put(head_addr, ncmd);
				//ncmd.setLabel("jaiblock_" + Integer.toHexString(head_addr));
			}
			chmap.addCommand((int)Math.floor(block_tick), ncmd);
		}
		
		//Now add it to target as requested...
		NUSALSeqBuilder target = context.getTarget();
		if(asSub){
			ch_head = target.genChannelSubroutine(chmap);
			ch_head.getChunkHead().setLabel(String.format("jaisub_ch%02d_%x", channel, head_addr));
			context.ncommands.put(head_addr, ch_head);
			for(int j = 0; j < voxcount; j++) {
				layer_heads[j] = target.genLayerSubroutine(voxmap[j]);
				if(layer_heads[j] != null){
					layer_heads[j].getChunkHead().setLabel(String.format("jaisub_ch%02d_vox%d_%x", channel, j, head_addr));
				}
			}
		}
		else{
			target.addChannelCommands(channel, chmap);
			for(int j = 0; j < voxcount; j++) {
				if(voxmap[j] != null && !voxmap[j].isEmpty()){
					System.err.println("DEBUG Printing map for layer " + j);
					Jseq2Useq.printCommandMap(voxmap[j]);
					target.addLayerCommands(channel, j, voxmap[j]);
				}
			}
		}
	}

}
