package waffleoRai_SeqSound.n64al.cmd;

import waffleoRai_SeqSound.n64al.NUSALSeqCmdType;
import waffleoRai_SeqSound.n64al.NUSALSeqCommand;
import waffleoRai_SeqSound.n64al.NUSALSeqDataType;
import waffleoRai_Utils.BufferReference;

public class DataCommands {

	/*--- Command Reader ---*/
	
	public static NUSALSeqCommand parseData(NUSALSeqCmdType parent_cmd, BufferReference dat, int upper_addr){
		if(parent_cmd == null || dat == null) return null;
		NUSALSeqDataType dtype = cmdDataType(parent_cmd);
		int exsz = dtype.getTotalSize();
		int usize = dtype.getUnitSize();
		if(exsz <= 0){
			//Assume it runs to end address (aligned to unit size)
			exsz = upper_addr - (int)dat.getBufferPosition();
			if(usize > 1){
				if(usize == 2){
					exsz += 1;
					exsz &= ~0x1;
				}
				else if(usize == 4){
					exsz += 3;
					exsz &= ~0x3;
				}
				else if(usize == 8){
					exsz += 7;
					exsz &= ~0x7;
				}
				else{
					exsz += (usize - 1);
					exsz /= usize;
					exsz *= usize;	
				}
			}
		}
		
		NUSALSeqDataCommand dcmd = new NUSALSeqDataCommand(dtype, exsz/usize);
		byte[] darray = dcmd.getDataArray();
		for(int i = 0; i < darray.length; i++){
			darray[i] = dat.nextByte();
		}
		
		return dcmd;
	}
	
	public static NUSALSeqCommand parseData(String cmd, String[] args){
		//TODO
		return null;
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
		else if(cmdtype == NUSALSeqCmdType.STORE_TO_SELF){
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
			return NUSALSeqDataType.P_TABLE;
		}
		else if(cmdtype == NUSALSeqCmdType.LOAD_IMM_P){
			return NUSALSeqDataType.P_TABLE;
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
	
}
