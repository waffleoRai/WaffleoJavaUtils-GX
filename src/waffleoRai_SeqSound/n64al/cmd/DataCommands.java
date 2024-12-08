package waffleoRai_SeqSound.n64al.cmd;

import java.util.LinkedList;
import java.util.List;

import waffleoRai_SeqSound.n64al.NUSALSeqCmdType;
import waffleoRai_SeqSound.n64al.NUSALSeqCommand;
import waffleoRai_SeqSound.n64al.NUSALSeqCommands;
import waffleoRai_SeqSound.n64al.NUSALSeqDataType;
import waffleoRai_SeqSound.n64al.cmd.linking.NUSALSeqLinking;
import waffleoRai_Sound.nintendo.Z64Sound;
import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.Ref;

public class DataCommands {
	
	public static final int TARGET_TYPE_DATA = 0;
	public static final int TARGET_TYPE_CODE = 1;
	public static final int TARGET_TYPE_LITERAL = 2;
	public static final int TARGET_TYPE_UNK = -1;

	/*--- Command Reader ---*/
	
	public static NUSALSeqDataCommand parseData(NUSALSeqCmdType parent_cmd, BufferReference dat, int upper_addr){
		if(parent_cmd == null || dat == null) return null;
		
		/*if(dat.getBufferPosition() == 0x5ee5) {
			System.err.println("DataCommands.parseData || Debug Triggered");
		}*/
		
		//System.err.println("upper_addr = 0x" + Integer.toHexString(upper_addr));
		NUSALSeqDataType dtype = cmdDataType(parent_cmd);
		if(dtype == NUSALSeqDataType.ENVELOPE){
			//Look for terminal command (0, -1, or -3)
			boolean endcmd = false;
			LinkedList<short[]> ecmds = new LinkedList<short[]>();
			while(!endcmd){
				short cmd = dat.nextShort();
				short val = dat.nextShort();
				switch(cmd){
				case Z64Sound.ENVCMD__ADSR_DISABLE:
				case Z64Sound.ENVCMD__ADSR_HANG:
				case Z64Sound.ENVCMD__ADSR_RESTART:
					endcmd = true;
					break;
				}
				ecmds.add(new short[]{cmd, val});
			}
			
			NUSALSeqDataCommand dcmd = new NUSALSeqDataCommand(dtype, ecmds.size() << 1);
			int i = 0;
			for(short[] ecmd : ecmds){
				dcmd.setDataValue(ecmd[0], i++);
				dcmd.setDataValue(ecmd[1], i++);
			}
			
			return dcmd;
		}
		
		
		int exsz = dtype.getTotalSize();
		int usize = dtype.getUnitSize();
		if(exsz <= 0){
			//Check if there is a max unit count.
			int maxsz = upper_addr - (int)dat.getBufferPosition();
			int maxu = dtype.getMaxUnits();
			if(maxu > 0){
				exsz = dtype.getUnitSize() * maxu;
				if(maxsz < exsz) exsz = maxsz;
			}
			else{
				//Assume it runs to end address (aligned to unit size)
				exsz = maxsz;
			}
			//Align
			//Round DOWN not up, genius
			if(usize > 1){
				if(usize == 2){
					//exsz += 1;
					exsz &= ~0x1;
				}
				else if(usize == 4){
					//exsz += 3;
					exsz &= ~0x3;
				}
				else if(usize == 8){
					//exsz += 7;
					exsz &= ~0x7;
				}
				else{
					//exsz += (usize - 1);
					exsz /= usize;
					exsz *= usize;	
				}
			}
		}
		
		NUSALSeqDataCommand dcmd = null;
		if(dtype == NUSALSeqDataType.P_TABLE || dtype == NUSALSeqDataType.CALLTABLE){
			dcmd = new NUSALSeqPtrTableData(dtype, exsz >> 1);
		}
		else dcmd = new NUSALSeqDataCommand(dtype, exsz/usize);
		byte[] darray = dcmd.getDataArray();
		for(int i = 0; i < darray.length; i++){
			darray[i] = dat.nextByte();
		}
		
		return dcmd;
	}
	
	public static NUSALSeqCommand parseData(String cmd, String[] args){
		if(cmd == null) return null;
		if(!cmd.equalsIgnoreCase("data")) return null;
		if(args == null || args.length < 2) return null;
		
		/*for(int i = 0; i < args.length; i++){
			args[i] = args[i].replace("{", "").replace("}", "");
		}*/
		String dtype_str = args[0];
		NUSALSeqDataType dtype = NUSALSeqDataType.readMML(dtype_str);
		if(dtype == null) return null;
		
		//Parse actual data.
		NUSALSeqDataCommand dcmd = null;
		if(dtype == NUSALSeqDataType.BUFFER){
			//Just takes number of bytes
			try{
				int size = Integer.parseInt(args[1]);
				dcmd = new NUSALSeqDataCommand(dtype, size);
			}
			catch(NumberFormatException ex){
				ex.printStackTrace();
				return null;
			}
		}
		else{
			//Read as array.
			try{
				String[] dataArgs = args;
				if(args.length == 2) {
					dataArgs = args[1].split(",");
				}
				int size = dataArgs.length-1;
				dcmd = new NUSALSeqDataCommand(dtype, size);
				for(int i = 0; i < size; i++){
					int val = -1;
					switch(dtype.getParamPrintType()){
					case NUSALSeqCommands.MML_DATAPARAM_TYPE__DECSIGNED:
					case NUSALSeqCommands.MML_DATAPARAM_TYPE__DECUNSIGNED:
						val = Integer.parseInt(dataArgs[i+1]);
						break;
					case NUSALSeqCommands.MML_DATAPARAM_TYPE__HEXUNSIGNED:
						val = Integer.parseInt(dataArgs[i+1],16);
						break;
					}
					dcmd.setDataValue(val, i);
				}
			}
			catch(NumberFormatException ex){
				ex.printStackTrace();
				return null;
			}
		}
		
		return dcmd;
	}
	
	/*--- Parsing Helpers ---*/
	
	public static int validPointersFrom(BufferReference scanStart, int totalDataSize) {
		if(scanStart == null) return 0;
		int ct = 0;
		while(scanStart.hasRemaining()) {
			int val = Short.toUnsignedInt(scanStart.nextShort());
			if(val < 0 || val >= totalDataSize) break;
			else ct++;
		}
		return ct;
	}
	
	public static NUSALSeqDataType cmdDataType(NUSALSeqCmdType cmdtype){
		if(cmdtype == NUSALSeqCmdType.CALL_TABLE){
			return NUSALSeqDataType.CALLTABLE;
		}	
		else if(cmdtype == NUSALSeqCmdType.LOAD_SEQ){
			return NUSALSeqDataType.BUFFER;
		}
		else if(cmdtype == NUSALSeqCmdType.LOAD_SHORTTBL_GATE){
			return NUSALSeqDataType.GATE_TABLE;
		}
		else if(cmdtype == NUSALSeqCmdType.LOAD_SHORTTBL_VEL){
			return NUSALSeqDataType.VEL_TABLE;
		}
		else if(cmdtype == NUSALSeqCmdType.STORE_TO_SELF_S){
			//Not necessarily - could be self-modifying table
			//return NUSALSeqDataType.BUFFER;
			return NUSALSeqDataType.BINARY;
		}
		else if(cmdtype == NUSALSeqCmdType.STORE_TO_SELF_C){
			//\Not necessarily - could be self-modifying table
			//return NUSALSeqDataType.BUFFER;
			return NUSALSeqDataType.BINARY;
		}
		else if(cmdtype == NUSALSeqCmdType.SET_CH_FILTER){
			return NUSALSeqDataType.FILTER;
		}
		else if(cmdtype == NUSALSeqCmdType.LOAD_P_TABLE){
			return NUSALSeqDataType.P_TABLE;
		}
		else if(cmdtype == NUSALSeqCmdType.SET_DYNTABLE){
			return NUSALSeqDataType.P_TABLE;
		}
		else if(cmdtype == NUSALSeqCmdType.LOAD_FROM_SELF){
			return NUSALSeqDataType.Q_TABLE;
		}
		else if(cmdtype == NUSALSeqCmdType.DYNTABLE_LOAD){
			return NUSALSeqDataType.Q_TABLE;
		}
		else if(cmdtype == NUSALSeqCmdType.LOAD_IMM_P){
			//return NUSALSeqDataType.BUFFER;
			return NUSALSeqDataType.BINARY;
		}
		else if(cmdtype == NUSALSeqCmdType.STORE_TO_SELF_P){
			//Not necessarily - could be self-modifying table
			//return NUSALSeqDataType.BUFFER;
			return NUSALSeqDataType.BINARY;
		}
		else if(cmdtype == NUSALSeqCmdType.CH_ENVELOPE){
			return NUSALSeqDataType.ENVELOPE;
		}
		else if(cmdtype == NUSALSeqCmdType.CH_LOAD_PARAMS){
			return NUSALSeqDataType.CH_PARAMS;
		}
		else if(cmdtype == NUSALSeqCmdType.L_ENVELOPE){
			return NUSALSeqDataType.ENVELOPE;
		}
		
		return null;
	}
	
	/*--- Context Prediction - QTBL ---*/
	
	private static NUSALSeqCommand guessQUsage(NUSALSeqCommand cmd){
		//Scan forward to see what a loaded q is used for after cmd sets it
		NUSALSeqCommand next = cmd.getSubsequentCommand();
		NUSALSeqCommand ref = null;
		while(next != null){
			switch(next.getFunctionalType()){
			case LOAD_SAMPLE:
			case STORE_CHIO:
			case STORE_IO_S:
			case STORE_IO_C:
			case VOICE_OFFSET_TABLE:
			case DYNTABLE_READ:
			case DYNTABLE_LOAD:
			case SHIFT_DYNTABLE:
			case CALL_DYNTABLE:
			case CALL_TABLE:
				return next;
			case LOAD_FROM_SELF:
				return guessQUsage(next);
			case STORE_TO_SELF_S:
			case STORE_TO_SELF_C:
				ref = next.getBranchTarget();
				if(ref != null){
					return ref;
				}
				break;
			default: break;
			}
			next = next.getSubsequentCommand();
		}
		return null;
	}
	
	public static NUSALSeqCommand guessQTableUsage(NUSALSeqCommand qtbl){
		//TODO
		return null;
	}
	
	/*--- Context Prediction - PTBL ---*/
	
	public static NUSALSeqCommand guessPUsage(NUSALSeqCommand cmd){
		//Scan forward to see what a loaded p is used for after cmd sets it
		NUSALSeqCommand next = cmd.getSubsequentCommand();
		NUSALSeqCommand ref = null;
		while(next != null){
			switch(next.getFunctionalType()){
			case STORE_TO_SELF_P:
				//See what the stps is modifying.
				//Might be a command param
				//Might be data
				ref = next.getBranchTarget();
				if(ref != null) return ref;
				return null;
			case LOAD_SAMPLE_P:
				return next;
			case DYNTABLE_WRITE: //p2dyntable
				//Sets dyntbl address to the value in p
				return guessDyntableUsage(next);
			case BRANCH_ALWAYS:
			case BRANCH_ALWAYS_REL:
				next = next.getBranchTarget();
				continue;
			case BRANCH_IF_LTZ_REL:
			case BRANCH_IF_EQZ_REL:
			case BRANCH_IF_GTEZ:
			case BRANCH_IF_LTZ:
			case BRANCH_IF_EQZ:
				return cmd;
			default: break;
			}
			next = next.getSubsequentCommand();
		}
		return null;
	}
	
	private static NUSALSeqCommand guessDyntableUsage(NUSALSeqCommand cmd){
		//Scan forward to see what the dyntable is used for
		//dynstartlayer, dyncall, p2dyntable, dyntable2p, dynsetdyntable
		NUSALSeqCommand next = null;
		NUSALSeqCommand ret = null;
		next = cmd.getSubsequentCommand();
		while(next != null){
			switch(next.getFunctionalType()){
			case VOICE_OFFSET_TABLE:
				return next;
			case DYNTABLE_READ:
				//The dyntable is just another ptable WHY
				ret = guessPUsage(next);
				if (ret != null) return ret;
				break;
			case DYNTABLE_LOAD:
				//The dyntable is a Q table.
				//ret = guessQUsage(next);
				//if (ret != null) return ret;
				return next;
			case SHIFT_DYNTABLE:
				//I am not exactly sure what to do with this...
				//Keep going maybe? I'll leave the case here just in case.
				break;
			case CALL_DYNTABLE:
				//dyntable is table of pointers to channel code
				return next;
			case BRANCH_ALWAYS:
			case BRANCH_ALWAYS_REL:
				next = next.getBranchTarget();
				continue;
			case BRANCH_IF_LTZ_REL:
			case BRANCH_IF_EQZ_REL:
			case BRANCH_IF_GTEZ:
			case BRANCH_IF_LTZ:
			case BRANCH_IF_EQZ:
				return cmd;
			default: break;
			}
			next = next.getSubsequentCommand();
		}
		return cmd;
	}
	
	public static NUSALSeqCommand guessPTableUsage(NUSALSeqCommand ptbl){
		List<NUSALSeqCommand> referees = ptbl.getReferees();
 		if(referees.isEmpty()) return null;
 		NUSALSeqCommand ret = null;
 		for(NUSALSeqCommand referee : referees){
 			//Look for one we can use to figure it out.
 			//These are commands likely to reference the table itself.
 			//	What the table is for might not be in that command, but a nearby one.
 			switch(referee.getFunctionalType()){
 			case CALL_TABLE: 
 				//Probably seq subroutines.
 				return referee;
 			case LOAD_IMM_P: 
 			case RANDP: 
 			case ADD_RAND_IMM_P: 
 			case LOAD_P_TABLE: 
 				//Scan forward to see where p is next applied.
 				// stps, loadsplp, p2dyntable, addp, 
 				ret = guessPUsage(referee);
 				if(ret != null) {
 					switch(ret.getFunctionalType()) {
 					case STORE_TO_SELF_C:
 					case STORE_TO_SELF_S:
 						//P is giving address of command to modify :)
 						//If target is non-null return that to get idea of what kind of commands.
 						NUSALSeqCommand stsTarg = ret.getBranchTarget();
 						if(stsTarg != null) {
 							return stsTarg;
 						}
 						break;
 					default:
 						break;
 					}
 					return ret;
 				}
 				return referee;
 			case SET_DYNTABLE: 
 				ret = guessDyntableUsage(referee);
 				if(ret != null) return ret;
 				break;
 			default: return null;
 			}
 		}
		
		return null;
	}
	
	public static int guessLDPITargetType(NUSALSeqCommand cmd) {
		//TODO
		if(cmd == null) return TARGET_TYPE_UNK;
		
		NUSALSeqCommand trg = guessPUsage(cmd);
		if(trg == null) return TARGET_TYPE_UNK;
		switch(trg.getFunctionalType()) {
		case LOAD_FROM_SELF:
		case DYNTABLE_LOAD:
			return TARGET_TYPE_DATA;
		default:
			return TARGET_TYPE_UNK;
		}
	}
	
	/*--- Context Prediction - PTBL Update ---*/
	
	public static int guessStoreTargetUsage(NUSALSeqCommand modTarget, Ref<NUSALSeqCommand> tracedCmd) {
		//Returns a type based on the command that is BEING MODIFIED
		//Uses a NUSALSeqLinking pseudo-enum instead.
		if(modTarget == null) return NUSALSeqLinking.P_TYPE_UNK;
		switch(modTarget.getFunctionalType()) {
		case CHANNEL_OFFSET:
		case CHANNEL_OFFSET_REL:
		case CHANNEL_OFFSET_C:
			if(tracedCmd != null) tracedCmd.data = modTarget;
			return NUSALSeqLinking.P_TYPE_CH_CODE;
		case CALL_TABLE: //seq
			if(tracedCmd != null) tracedCmd.data = modTarget;
			return NUSALSeqLinking.P_TYPE_SEQ_CODE;
		case VOICE_OFFSET:
		case VOICE_OFFSET_REL:
			if(tracedCmd != null) tracedCmd.data = modTarget;
			return NUSALSeqLinking.P_TYPE_LYR_CODE;
		case CH_ENVELOPE:
		case L_ENVELOPE:
		case LOAD_SHORTTBL_GATE:
		case LOAD_SHORTTBL_VEL:
		case CH_LOAD_PARAMS:
		case SET_CH_FILTER:
		case LOAD_SEQ:
			//The mod is probably an address to some kind of data array.
			if(tracedCmd != null) tracedCmd.data = modTarget;
			return NUSALSeqLinking.P_TYPE_GENERAL_DATA;
		case STORE_TO_SELF_S:
		case STORE_TO_SELF_C:
			//The mod is either the address (p) or offset (q)
			//Assuming it's p here... (change if this becomes a problem)
			if(tracedCmd != null) tracedCmd.data = modTarget;
			return NUSALSeqLinking.P_TYPE_PDATA;
		case LOAD_P_TABLE:
			//The mod is probably address to p table
			if(tracedCmd != null) tracedCmd.data = modTarget;
			return NUSALSeqLinking.P_TYPE_PDATA;
		case SET_DYNTABLE:
			//The mod is probably address of dyntable.
			//No idea what is in the dyntable though.
			return guessDynTableUsageType(modTarget, tracedCmd);
		case LOAD_FROM_SELF:
			//The mod is probably address to write Q to
			if(tracedCmd != null) tracedCmd.data = modTarget;
			return NUSALSeqLinking.P_TYPE_QDATA;
		case LOAD_IMM_P:
			//The mod is probably the immediate
			//But need to trace in case it is a pointer to something else
			return guessPUsageType(modTarget, tracedCmd);
		case STORE_TO_SELF_P:
			//The mod is probably the address to write P to
			//Recurse w/ target
			/*NUSALSeqCommand stsTarget = modTarget.getBranchTarget();
			if(stsTarget != null) {
				return guessStoreTargetUsage(stsTarget, tracedCmd);
			}
			break;*/ //Probably just not linked yet.
			if(tracedCmd != null) tracedCmd.data = modTarget;
			return NUSALSeqLinking.P_TYPE_PDATA;
		case BRANCH_IF_LTZ_REL:
		case BRANCH_IF_EQZ_REL:
		case BRANCH_ALWAYS_REL:
		case BRANCH_IF_GTEZ:
		case BRANCH_IF_LTZ:
		case BRANCH_IF_EQZ:
		case BRANCH_ALWAYS:
		case CALL:
			if(tracedCmd != null) tracedCmd.data = modTarget;
			if(modTarget.isSeqCommand()) return NUSALSeqLinking.P_TYPE_SEQ_CODE;
			else if(modTarget.isChannelCommand()) return NUSALSeqLinking.P_TYPE_CH_CODE;
			else if(modTarget.isLayerCommand()) return NUSALSeqLinking.P_TYPE_LYR_CODE;
			break;
		default:
			//Commonly used as table of parametesr for modifying commands
			if(tracedCmd != null) tracedCmd.data = modTarget;
			return NUSALSeqLinking.P_TYPE_QDATA;
		}
		
		return NUSALSeqLinking.P_TYPE_UNK;
	}
	
	public static int guessDynTableUsageType(NUSALSeqCommand dtReferee, Ref<NUSALSeqCommand> tracedCmd) {
		//TODO
		//Tries to guess usage from the dyntable referee
		//Uses a NUSALSeqLinking pseudo-enum instead.
		if(dtReferee == null) return NUSALSeqLinking.P_TYPE_UNK;
		
		//Walk down from this command to find next command that reads dyntbl ptr to do something
		NUSALSeqCommand trg = null;
		CommandWalker walker = new CommandWalker();
		walker.initializeWith(dtReferee);
		while((trg = walker.next()) != null) {
			switch(trg.getFunctionalType()) {
			case SHIFT_DYNTABLE:
				//TODO
				//idr what this does :) Will have to check decomp code.
				break;
			case DYNTABLE_READ:
				//dyntop
				return guessPUsageType(trg, tracedCmd);
			case DYNTABLE_LOAD:
				//lddyn
				if(tracedCmd != null) tracedCmd.data = trg;
				return NUSALSeqLinking.P_TYPE_QDATA;
			case CALL_DYNTABLE:
				//TODO The TABLE is code pointers. The pointer TO the table is not.
				if(tracedCmd != null) tracedCmd.data = trg;
				//return NUSALSeqLinking.P_TYPE_CH_CODE;
				return NUSALSeqLinking.P_TYPE_PDATA;
			default:
				break;
			}
		}	
		
		return NUSALSeqLinking.P_TYPE_UNK;
	}
	
	public static int guessPUsageType(NUSALSeqCommand pReferee, Ref<NUSALSeqCommand> tracedCmd) {
		//Tries to guess usage from the non-table referee
		//Uses a NUSALSeqLinking pseudo-enum instead.
		if(pReferee == null) return NUSALSeqLinking.P_TYPE_UNK;
		
		//If pReferee is SET_DYNTABLE, redirect to guessDynTableUsageType.
		if(pReferee.getFunctionalType() == NUSALSeqCmdType.SET_DYNTABLE) {
			return guessDynTableUsageType(pReferee, tracedCmd);
		}
		
		//Find next command that uses (reads) p
		NUSALSeqCommand trg = null;
		CommandWalker walker = new CommandWalker();
		walker.initializeWith(pReferee);
		while((trg = walker.next()) != null) {
			switch(trg.getFunctionalType()) {
			case DYNTABLE_WRITE:
				return guessDynTableUsageType(trg, tracedCmd);
			case STORE_TO_SELF_P:
				NUSALSeqCommand stsTrg = trg.getBranchTarget();
				if(stsTrg != null) {
					return guessStoreTargetUsage(stsTrg, tracedCmd);
				}
				return NUSALSeqLinking.P_TYPE_UNK; //Assumed target not found yet.
			default:
				break;
			}
		}	
		
		return NUSALSeqLinking.P_TYPE_UNK;
	}
	
}
