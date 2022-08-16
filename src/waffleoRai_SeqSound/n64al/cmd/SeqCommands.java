package waffleoRai_SeqSound.n64al.cmd;

import waffleoRai_SeqSound.n64al.NUSALSeq;
import waffleoRai_SeqSound.n64al.NUSALSeqCmdType;
import waffleoRai_SeqSound.n64al.NUSALSeqCommand;
import waffleoRai_Utils.BufferReference;

import waffleoRai_SeqSound.n64al.cmd.FCommands.*;

public class SeqCommands {
	
	/*--- Parser ---*/
	public static NUSALSeqCommand parseSequenceCommand(BufferReference dat){
		int cmdi = Byte.toUnsignedInt(dat.getByte());
		int cmdhi = (cmdi & 0xf0) >>> 4;
		int cmdlo = cmdi & 0xf;
		
		BufferReference ptr = dat.copy();
		int i,j;
		
		switch(cmdhi){
		case 0x0:
			return new C_S_TestChannel(cmdlo);
		case 0x4:
			return new C_S_StopChannel(cmdlo);
		case 0x5:
			return new C_S_SubIO(cmdlo);
		case 0x6:
			ptr.increment(); i = (int)ptr.getByte();
			ptr.increment(); j = (int)ptr.getByte();
			return new C_S_LoadBank(cmdlo, i, j);
		case 0x7:
			return new C_S_StoreIO(cmdlo);
		case 0x8:
			return new C_S_LoadIO(cmdlo);
		case 0x9:
			ptr.increment(); i = Short.toUnsignedInt(ptr.getShort());
			return new C_S_StartChannel(cmdlo, i);
		case 0xa:
			ptr.increment(); i = (int)ptr.getShort();
			return new C_S_StartChannelRel(cmdlo, i);
		case 0xb:
			ptr.increment(); i = (int)ptr.getByte();
			ptr.increment(); j = Short.toUnsignedInt(ptr.getShort());
			return new C_S_LoadSeq(cmdlo, i, j);
	/* 0xcn */
		case 0xc:
			switch(cmdlo){
			case 0x4:
				ptr.increment(); i = (int)ptr.getByte();
				ptr.increment(); j = (int)ptr.getByte();
				return new CMD_IgnoredCommand(NUSALSeqCmdType.S_UNK_C4){
					protected void onInit(){
						setParam(0, i); setParam(1,j);
					}
				};
			case 0x5:
				ptr.increment(); i = Short.toUnsignedInt(ptr.getShort());
				return new CMD_IgnoredCommand(NUSALSeqCmdType.S_UNK_C5){
					protected void onInit(){setParam(0, i);}
				};
			case 0x6:
				return new CMD_IgnoredCommand(NUSALSeqCmdType.S_UNK_C6);
			case 0x7:
				ptr.increment(); i = (int)ptr.getByte();
				ptr.increment(); j = Short.toUnsignedInt(ptr.getShort());
				return new C_S_StoreToSelf(i, j);
			case 0x8:
				ptr.increment(); i = (int)ptr.getByte();
				return new C_S_SubImm(i);
			case 0x9:
				ptr.increment(); i = (int)ptr.getByte();
				return new C_S_AndImm(i);
			case 0xc:
				ptr.increment(); i = (int)ptr.getByte();
				return new C_S_LoadImm(i);
			case 0xd:
				ptr.increment(); i = Short.toUnsignedInt(ptr.getShort());
				return new C_S_TblCall(i);
			case 0xe:
				ptr.increment(); i = Byte.toUnsignedInt(ptr.getByte());
				return new C_S_LoadRandom(i);
			}
			break;
	/* 0xdn */
		case 0xd:
			switch(cmdlo){
			case 0x0:
				ptr.increment(); i = Byte.toUnsignedInt(ptr.getByte());
				return new C_S_NoteAllocPolicy(i);
			case 0x1:
				ptr.increment(); i = Short.toUnsignedInt(ptr.getShort());
				return new C_S_SetGateTable(i);
			case 0x2:
				ptr.increment(); i = Short.toUnsignedInt(ptr.getShort());
				return new C_S_SetVelTable(i);
			case 0x3:
				ptr.increment(); i = Byte.toUnsignedInt(ptr.getByte());
				return new C_S_MuteBehavior(i);
			case 0x4:
				return new C_S_Mute();
			case 0x5:
				ptr.increment(); i = (int)ptr.getByte();
				return new C_S_MuteScale(i);
			case 0x6:
				ptr.increment(); i = Short.toUnsignedInt(ptr.getShort());
				return new C_S_DisableChannels(i);
			case 0x7:
				ptr.increment(); i = Short.toUnsignedInt(ptr.getShort());
				return new C_S_InitChannels(i);
			case 0x9:
				ptr.increment(); i = Byte.toUnsignedInt(ptr.getByte());
				return new C_S_SetMasterExpression(i);
			case 0xa:
				ptr.increment(); i = Byte.toUnsignedInt(ptr.getByte());
				ptr.increment(); j = Short.toUnsignedInt(ptr.getShort());
				return new C_S_SetMasterFade(i,j);
			case 0xb:
				ptr.increment(); i = Byte.toUnsignedInt(ptr.getByte());
				return new C_S_SetMasterVolume(i);
			case 0xc:
				ptr.increment(); i = (int)ptr.getByte();
				return new C_S_SetTempoVar(i);
			case 0xd:
				ptr.increment(); i = Byte.toUnsignedInt(ptr.getByte());
				return new C_S_SetTempo(i);
			case 0xe:
				ptr.increment(); i = (int)ptr.getByte();
				return new C_S_SetDeltaTranspose(i);
			case 0xf:
				ptr.increment(); i = (int)ptr.getByte();
				return new C_S_SetTranspose(i);
			}
			break;
	/* 0xen */
		case 0xe:
			switch(cmdlo){
			case 0xf:
				ptr.increment(); i = Short.toUnsignedInt(ptr.getShort());
				ptr.increment(); j = (int)ptr.getByte();
				return new C_S_Print(i,j);
			}
			break;
	/* 0xfn */
		case 0xf:
			switch(cmdlo){
			case 0x0: return new C_UnreserveNotes();
			case 0x1:
				ptr.increment(); i = (int)ptr.getByte();
				return new C_ReserveNotes(i);
			case 0x2:
				ptr.increment(); i = (int)ptr.getByte();
				return new C_rbltz(i);
			case 0x3:
				ptr.increment(); i = (int)ptr.getByte();
				return new C_rbeqz(i);
			case 0x4:
				ptr.increment(); i = (int)ptr.getByte();
				return new C_rjump(i);
			case 0x5:
				ptr.increment(); i = Short.toUnsignedInt(ptr.getShort());
				return new C_bgez(i);
			case 0x6: return new C_Break();
			case 0x7: return new C_LoopEnd();
			case 0x8:
				ptr.increment(); i = (int)ptr.getByte();
				return new C_LoopStart(i);
			case 0x9:
				ptr.increment(); i = Short.toUnsignedInt(ptr.getShort());
				return new C_bltz(i);
			case 0xa:
				ptr.increment(); i = Short.toUnsignedInt(ptr.getShort());
				return new C_beqz(i);
			case 0xb:
				ptr.increment(); i = Short.toUnsignedInt(ptr.getShort());
				return new C_Jump(i);
			case 0xc:
				ptr.increment(); i = Short.toUnsignedInt(ptr.getShort());
				return new C_Call(i);
			case 0xd:
				ptr.increment();
				i = NUSALSeqReader.readVLQ(ptr);
				return new C_Wait(i);
			case 0xe: return new C_Yield();
			case 0xf: return new C_EndRead();
			}
			break;
		default:
			break;
		}
		
		return null;
	}
	
	public static NUSALSeqCommand parseSequenceCommand(String cmd, String[] args){
		NUSALSeqCommand command = FCommands.parseFCommand(cmd, args);
		if(command != null) return command;
		
		/*
		 * The following commands are not currently supported:
		 * 	loadseq
		 * 	unk_C4
		 * 	unk_C5
		 * 	unk_C6
		 * 	sts
		 * 	tblcall
		 *  ldshorttablegate
		 *  ldshorttablevel
		 */
		
		//References resolved by caller.
		cmd = cmd.toLowerCase();
		if(cmd.equals("startchan")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_S_StartChannel(n,-1);
		}
		else if(cmd.equals("rstartchan")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_S_StartChannelRel(n,-1);
		}
		else if(cmd.equals("svol")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_S_SetMasterVolume(n);
		}
		else if(cmd.equals("tempo")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_S_SetTempo(n);
		}
		else if(cmd.equals("disablechan")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_S_DisableChannels(n);
		}
		else if(cmd.equals("initchan")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_S_InitChannels(n);
		}
		else if(cmd.equals("sub")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_S_SubImm(n);
		}
		else if(cmd.equals("ldi")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_S_LoadImm(n);
		}
		else if(cmd.equals("mutebhv")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_S_MuteBehavior(n);
		}
		else if(cmd.equals("sexp")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_S_SetMasterExpression(n);
		}
		else if(cmd.equals("tempovar")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_S_SetTempoVar(n);
		}
		else if(cmd.equals("sfade")){
			int[] n = NUSALSeqReader.readNumbers(args);
			if(n == null) return null;
			return new C_S_SetMasterFade(n[0],n[1]);
		}
		else if(cmd.equals("stio")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_S_StoreIO(n);
		}
		else if(cmd.equals("ldio")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_S_LoadIO(n);
		}
		else if(cmd.equals("subio")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_S_SubIO(n);
		}
		else if(cmd.equals("stprel")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_S_SetDeltaTranspose(n);
		}
		else if(cmd.equals("stp")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_S_SetTranspose(n);
		}
		else if(cmd.equals("and")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_S_AndImm(n);
		}
		else if(cmd.equals("rand")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_S_LoadRandom(n);
		}
		else if(cmd.equals("testchan")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_S_TestChannel(n);
		}
		else if(cmd.equals("stopchan")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_S_StopChannel(n);
		}
		else if(cmd.equals("loadbank")){
			int[] n = NUSALSeqReader.readNumbers(args);
			if(n == null) return null;
			return new C_S_LoadBank(n[0],n[1],n[2]);
		}
		else if(cmd.equals("mute")){return new C_S_Mute();}
		else if(cmd.equals("mutescale")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_S_MuteScale(n);
		}
		else if(cmd.equals("noteallocpolicy")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_S_NoteAllocPolicy(n);
		}
		else if(cmd.equals("print")){
			int[] n = NUSALSeqReader.readNumbers(args);
			if(n == null) return null;
			return new C_S_Print(n[0],n[1]);
		}
		
		return null;
	}
	
	/*--- 0x00:0x0f testchan ---*/
	public static class C_S_TestChannel extends NUSALSeqGenericCommand{
		public C_S_TestChannel(int channel) {
			super(NUSALSeqCmdType.TEST_CHANNEL);
			super.setParam(0, channel);
		}
		public boolean doCommand(NUSALSeq sequence){
			flagSeqUsed();
			boolean chen = sequence.channelEnabled(getParam(0));
			if(chen) sequence.setVarQ(0);
			else sequence.setVarQ(1);
			return true;
		}
	}
	
	/*--- 0x40:0x4f stopchan ---*/
	public static class C_S_StopChannel extends NUSALSeqGenericCommand{
		public C_S_StopChannel(int channel) {
			super(NUSALSeqCmdType.STOP_CHANNEL);
			super.setParam(0, channel);
		}
		public boolean doCommand(NUSALSeq sequence){
			flagSeqUsed();
			sequence.stopChannel(getParam(0));
			return true;
		}
	}
	
	/*--- 0x50:0x5f subio ---*/
	public static class C_S_SubIO extends NUSALSeqGenericCommand{
		public C_S_SubIO(int idx) {
			super(NUSALSeqCmdType.SUBTRACT_IO);
			super.setParam(0, idx);
		}
		public boolean doCommand(NUSALSeq sequence){
			flagSeqUsed();
			int val = sequence.getSeqIOValue(getParam(0));
			sequence.setVarQ(sequence.getVarQ() - val);
			return true;
		}
	}
	
	/*--- 0x60:0x6f loadbank ---*/
	public static class C_S_LoadBank extends CMD_IgnoredCommand{
		public C_S_LoadBank(int io_idx, int bank_idx, int bank_slot) {
			super(NUSALSeqCmdType.LOAD_BANK);
			super.setParam(0, io_idx);
			super.setParam(1, bank_idx);
			super.setParam(2, bank_slot);
		}
	}
	
	/*--- 0x70:0x7f stio ---*/
	public static class C_S_StoreIO extends NUSALSeqGenericCommand{
		public C_S_StoreIO(int idx) {
			super(NUSALSeqCmdType.STORE_IO);
			super.setParam(0, idx);
		}
		public boolean doCommand(NUSALSeq sequence){
			flagSeqUsed();
			sequence.setSeqIOValue(getParam(0), sequence.getVarQ());
			return true;
		}
	}
	
	/*--- 0x80:0x8f ldio ---*/
	public static class C_S_LoadIO extends NUSALSeqGenericCommand{
		public C_S_LoadIO(int idx) {
			super(NUSALSeqCmdType.LOAD_IO_S);
			super.setParam(0, idx);
		}
		public boolean doCommand(NUSALSeq sequence){
			flagSeqUsed();
			sequence.setVarQ(sequence.getSeqIOValue(getParam(0)));
			return true;
		}
	}
	
	/*--- 0x90:0x9f startchan ---*/
	public static class C_S_StartChannel extends NUSALSeqReferenceCommand{
		public C_S_StartChannel(int channel, int addr) {
			super(NUSALSeqCmdType.CHANNEL_OFFSET, channel, addr, false);
		}
		public NUSALSeqCmdType getRelativeCommand(){return NUSALSeqCmdType.CHANNEL_OFFSET_REL;}
		public NUSALSeqCmdType getAbsoluteCommand(){return NUSALSeqCmdType.CHANNEL_OFFSET;}
		public boolean doCommand(NUSALSeq sequence){
			flagSeqUsed();
			sequence.pointChannelTo(getParam(0), getBranchAddress());
			return true;
		}
	}
	
	/*--- 0xa0:0xaf rstartchan ---*/
	public static class C_S_StartChannelRel extends NUSALSeqReferenceCommand{
		public C_S_StartChannelRel(int channel, int offset) {
			super(NUSALSeqCmdType.CHANNEL_OFFSET_REL, channel, offset, true);
		}
		public NUSALSeqCmdType getRelativeCommand(){return NUSALSeqCmdType.CHANNEL_OFFSET_REL;}
		public NUSALSeqCmdType getAbsoluteCommand(){return NUSALSeqCmdType.CHANNEL_OFFSET;}
		public boolean doCommand(NUSALSeq sequence){
			flagSeqUsed();
			sequence.pointChannelTo(getParam(0), getBranchAddress());
			return true;
		}
	}
	
	/*--- 0xb0:0xbf loadseq ---*/
	public static class C_S_LoadSeq extends CMD_IgnoredCommand{
		public C_S_LoadSeq(int io_idx, int seq_idx, int addr) {
			super(NUSALSeqCmdType.LOAD_SEQ);
			super.setParam(0, io_idx);
			super.setParam(1, seq_idx);
			super.setParam(2, addr);
		}
		protected StringBuilder toMMLCommand_child(){
			StringBuilder sb = new StringBuilder(256);
			sb.append("loadseq ");
			sb.append(super.getParam(0) + " ");
			sb.append(super.getParam(1) + " ");
			sb.append(String.format("0x%04x", super.getParam(2)));
			return sb;
		}
	}
	
	/*--- 0xc7 sts ---*/
	public static class C_S_StoreToSelf extends NUSALSeqDataRefCommand{
		public C_S_StoreToSelf(int imm, int address) {
			super(NUSALSeqCmdType.STORE_TO_SELF, imm, address, -1);
		}
		public boolean doCommand(NUSALSeq sequence){
			flagSeqUsed();
			BufferReference targ = sequence.getSeqDataReference(getParam(1));
			targ.writeByte((byte)(getParam(0) + sequence.getVarQ()));
			return true;
		}
	}
	
	/*--- 0xc8 sub ---*/
	public static class C_S_SubImm extends NUSALSeqGenericCommand{
		public C_S_SubImm(int imm) {
			super(NUSALSeqCmdType.SUBTRACT_IMM);
			super.setParam(0, imm);
		}
		public boolean doCommand(NUSALSeq sequence){
			flagSeqUsed();
			sequence.setVarQ(sequence.getVarQ() - getParam(0));
			return true;
		}
	}
	
	/*--- 0xc9 and ---*/
	public static class C_S_AndImm extends NUSALSeqGenericCommand{
		public C_S_AndImm(int imm) {
			super(NUSALSeqCmdType.AND_IMM);
			super.setParam(0, imm);
		}
		public boolean doCommand(NUSALSeq sequence){
			flagSeqUsed();
			sequence.setVarQ(sequence.getVarQ() & getParam(0));
			return true;
		}
		protected StringBuilder toMMLCommand_child(){
			StringBuilder sb = new StringBuilder(256);
			sb.append("and 0x");
			sb.append(Integer.toHexString(getParam(0) & 0xff));
			return sb;
		}
	}
	
	/*--- 0xcc ldi ---*/
	public static class C_S_LoadImm extends NUSALSeqGenericCommand{
		public C_S_LoadImm(int imm) {
			super(NUSALSeqCmdType.LOAD_IMM);
			super.setParam(0, imm);
		}
		public boolean doCommand(NUSALSeq sequence){
			flagSeqUsed();
			sequence.setVarQ(getParam(0));
			return true;
		}
	}
	
	/*--- 0xcd tblcall ---*/
	public static class C_S_TblCall extends NUSALSeqDataRefCommand{
		public C_S_TblCall(int address) {
			super(NUSALSeqCmdType.CALL_TABLE, address, -1);
			super.setLabelPrefix("calltbl");
		}
		public boolean doCommand(NUSALSeq sequence){
			flagSeqUsed();
			int q = sequence.getVarQ();
			if(q >= 0){
				int addr = getParam(0) + (q<<1);
				BufferReference ref = sequence.getSeqDataReference(addr);
				return sequence.jumpTo(Short.toUnsignedInt(ref.getShort()), true);
			}
			return true;
		}
	}
	
	/*--- 0xce rand ---*/
	public static class C_S_LoadRandom extends NUSALSeqGenericCommand{
		public C_S_LoadRandom(int max) {
			super(NUSALSeqCmdType.RAND_S);
			super.setParam(0, max);
		}
		public boolean doCommand(NUSALSeq sequence){
			flagSeqUsed();
			int max = getParam(0);
			if(max == 0) max = 256;
			sequence.setVarQ(sequence.getRNG().nextInt(max) & 0xFF);
			return true;
		}
	}
	
	/*--- 0xd0 noteallocpolicy ---*/
	public static class C_S_NoteAllocPolicy extends CMD_IgnoredCommand{
		public C_S_NoteAllocPolicy(int bitmask) {
			super(NUSALSeqCmdType.NOTEALLOC_POLICY_S);
			super.setParam(0, bitmask);
		}
	}
	
	/*--- 0xd1 ldshorttablegate ---*/
	public static class C_S_SetGateTable extends NUSALSeqDataRefCommand{
		public C_S_SetGateTable(int address) {
			super(NUSALSeqCmdType.LOAD_SHORTTBL_GATE, address, 16);
			super.setLabelPrefix("gatetbl");
		}
		public boolean doCommand(NUSALSeq sequence){
			flagSeqUsed();
			sequence.setShortTable_Gate(getBranchAddress());
			return true;
		}
	}
	
	/*--- 0xd2 ldshorttablevel ---*/
	public static class C_S_SetVelTable extends NUSALSeqDataRefCommand{
		public C_S_SetVelTable(int address) {
			super(NUSALSeqCmdType.LOAD_SHORTTBL_VEL, address, 16);
			super.setLabelPrefix("veltbl");
		}
		public boolean doCommand(NUSALSeq sequence){
			flagSeqUsed();
			sequence.setShortTable_Velocity(getBranchAddress());
			return true;
		}
	}
	
	/*--- 0xd3 mutebhv ---*/
	public static class C_S_MuteBehavior extends CMD_IgnoredCommand{
		public C_S_MuteBehavior(int bitmask) {
			super(NUSALSeqCmdType.MUTE_BEHAVIOR_S);
			super.setParam(0, bitmask);
		}
	}
	
	/*--- 0xd4 mute ---*/
	public static class C_S_Mute extends CMD_IgnoredCommand{
		public C_S_Mute() {
			super(NUSALSeqCmdType.MUTE_S);
		}
	}
	
	/*--- 0xd5 mutescale ---*/
	public static class C_S_MuteScale extends CMD_IgnoredCommand{
		public C_S_MuteScale(int scale) {
			super(NUSALSeqCmdType.MUTE_SCALE_S);
			super.setParam(0, scale);
		}
	}
	
	/*--- 0xd6 disablechan ---*/
	public static class C_S_DisableChannels extends NUSALSeqGenericCommand{
		public C_S_DisableChannels(int bitfield) {
			super(NUSALSeqCmdType.DISABLE_CHANNELS);
			super.setParam(0, bitfield);
		}
		public boolean doCommand(NUSALSeq sequence){
			flagSeqUsed();
			int bits = getParam(0);
			int mask = 0x1;
			for(int i = 0; i < 16; i++){
				if((mask & bits) != 0){
					sequence.setChannelEnabled(i, false);
				}
				mask <<= 1;
			}
			return true;
		}
		protected String paramsToString(){
			return String.format("0x%04x", super.getParam(0));
		};
	}
	
	/*--- 0xd7 initchan ---*/
	public static class C_S_InitChannels extends NUSALSeqGenericCommand{
		public C_S_InitChannels(int bitfield) {
			super(NUSALSeqCmdType.ENABLE_CHANNELS);
			super.setParam(0, bitfield);
		}
		public boolean doCommand(NUSALSeq sequence){
			flagSeqUsed();
			int bits = getParam(0);
			int mask = 0x1;
			for(int i = 0; i < 16; i++){
				if((mask & bits) != 0){
					sequence.setChannelEnabled(i, true);
				}
				else sequence.setChannelEnabled(i, false);
				mask <<= 1;
			}
			return true;
		}
		protected String paramsToString(){
			return String.format("0x%04x", super.getParam(0));
		};
	}
	
	/*--- 0xd9 sexp ---*/
	public static class C_S_SetMasterExpression extends NUSALSeqGenericCommand{
		public C_S_SetMasterExpression(int value) {
			super(NUSALSeqCmdType.MASTER_EXP);
			super.setParam(0, value);
		}
		public boolean doCommand(NUSALSeq sequence){
			flagSeqUsed();
			sequence.setMasterExpression((byte)getParam(0));
			return true;
		}
	}
	
	/*--- 0xda sfade ---*/
	public static class C_S_SetMasterFade extends NUSALSeqGenericCommand{
		public C_S_SetMasterFade(int mode, int time) {
			super(NUSALSeqCmdType.MASTER_FADE);
			super.setParam(0, mode);
			super.setParam(1, time);
		}
		public boolean doCommand(NUSALSeq sequence){
			flagSeqUsed();
			sequence.setMasterFade(getParam(0), getParam(1));
			return true;
		}
	}
	
	/*--- 0xdb svol ---*/
	public static class C_S_SetMasterVolume extends NUSALSeqGenericCommand{
		public C_S_SetMasterVolume(int value) {
			super(NUSALSeqCmdType.MASTER_VOLUME);
			super.setParam(0, value);
		}
		public boolean doCommand(NUSALSeq sequence){
			flagSeqUsed();
			sequence.setMasterVolume((byte)getParam(0));
			return true;
		}
	}
	
	/*--- 0xdc tempovar ---*/
	public static class C_S_SetTempoVar extends NUSALSeqGenericCommand{
		public C_S_SetTempoVar(int value) {
			super(NUSALSeqCmdType.SET_TEMPO_VAR);
			super.setParam(0, value);
		}
		public boolean doCommand(NUSALSeq sequence){
			flagSeqUsed();
			sequence.setTempoVariance(getParam(0));
			return true;
		}
	}
	
	/*--- 0xdd tempo ---*/
	public static class C_S_SetTempo extends NUSALSeqGenericCommand{
		public C_S_SetTempo(int bpm) {
			super(NUSALSeqCmdType.SET_TEMPO);
			super.setParam(0, bpm);
		}
		public boolean doCommand(NUSALSeq sequence){
			flagSeqUsed();
			sequence.setTempo(getParam(0));
			return true;
		}
	}
	
	/*--- 0xde stprel ---*/
	public static class C_S_SetDeltaTranspose extends NUSALSeqGenericCommand{
		public C_S_SetDeltaTranspose(int semis) {
			super(NUSALSeqCmdType.SEQ_TRANSPOSE_REL);
			super.setParam(0, semis);
		}
		public boolean doCommand(NUSALSeq sequence){
			flagSeqUsed();
			sequence.transposeBy(getParam(0));
			return true;
		}
	}
	
	/*--- 0xdf stp ---*/
	public static class C_S_SetTranspose extends NUSALSeqGenericCommand{
		public C_S_SetTranspose(int semis) {
			super(NUSALSeqCmdType.SEQ_TRANSPOSE);
			super.setParam(0, semis);
		}
		public boolean doCommand(NUSALSeq sequence){
			flagSeqUsed();
			sequence.setTranspose(getParam(0));
			return true;
		}
	}
	
	/*--- 0xef print ---*/
	public static class C_S_Print extends CMD_IgnoredCommand{
		public C_S_Print(int addr, int value) {
			super(NUSALSeqCmdType.PRINT);
			super.setParam(0, addr);
			super.setParam(1, value);
		}
	}

}
