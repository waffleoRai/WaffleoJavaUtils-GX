package waffleoRai_SeqSound.n64al.cmd;

import java.util.LinkedList;
import java.util.List;

import waffleoRai_SeqSound.n64al.NUSALSeqCmdType;
import waffleoRai_SeqSound.n64al.NUSALSeqCommand;
import waffleoRai_SeqSound.n64al.NUSALSeqCommands;
import waffleoRai_SeqSound.n64al.NUSALSeqDataType;
import waffleoRai_Sound.nintendo.Z64Sound;
import waffleoRai_Utils.BufferReference;

public class DataCommands {

	/*--- Command Reader ---*/
	
	public static NUSALSeqDataCommand parseData(NUSALSeqCmdType parent_cmd, BufferReference dat, int upper_addr){
		if(parent_cmd == null || dat == null) return null;
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
		
		for(int i = 0; i < args.length; i++){
			args[i] = args[i].replace("{", "").replace("}", "");
		}
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
				int size = args.length-1;
				dcmd = new NUSALSeqDataCommand(dtype, size);
				for(int i = 0; i < size; i++){
					int val = -1;
					switch(dtype.getParamPrintType()){
					case NUSALSeqCommands.MML_DATAPARAM_TYPE__DECSIGNED:
					case NUSALSeqCommands.MML_DATAPARAM_TYPE__DECUNSIGNED:
						val = Integer.parseInt(args[i+1]);
						break;
					case NUSALSeqCommands.MML_DATAPARAM_TYPE__HEXUNSIGNED:
						val = Integer.parseInt(args[i+1],16);
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
			return NUSALSeqDataType.BUFFER;
		}
		else if(cmdtype == NUSALSeqCmdType.STORE_TO_SELF_C){
			return NUSALSeqDataType.BUFFER;
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
		else if(cmdtype == NUSALSeqCmdType.LOAD_IMM_P){
			return NUSALSeqDataType.BUFFER;
		}
		else if(cmdtype == NUSALSeqCmdType.STORE_TO_SELF_P){
			return NUSALSeqDataType.BUFFER;
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
	
	private static NUSALSeqCommand guessPUsage(NUSALSeqCommand cmd){
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
				break;
			case LOAD_SAMPLE_P:
				return next;
			case DYNTABLE_WRITE: //p2dyntable
				return guessDyntableUsage(next);
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
				ret = guessQUsage(next);
				if (ret != null) return ret;
				break;
			case SHIFT_DYNTABLE:
				//I am not exactly sure what to do with this...
				//Keep going maybe? I'll leave the case here just in case.
				break;
			case CALL_DYNTABLE:
				//dyntable is table of pointers to channel code
				return next;
			default: break;
			}
			next = next.getSubsequentCommand();
		}
		return null;
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
 				if(ret != null) return ret;
 				break;
 			case SET_DYNTABLE: 
 				ret = guessDyntableUsage(referee);
 				if(ret != null) return ret;
 				break;
 			default: return null;
 			}
 		}
		
		return null;
	}
	
}
