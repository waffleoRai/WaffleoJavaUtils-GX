package waffleoRai_SeqSound.n64al.cmd;

import waffleoRai_SeqSound.n64al.NUSALSeqCmdType;
import waffleoRai_SeqSound.n64al.NUSALSeqCommand;
import waffleoRai_SeqSound.n64al.NUSALSeqCommandBook;
import waffleoRai_SeqSound.n64al.NUSALSeqLayer;
import waffleoRai_SeqSound.n64al.cmd.FCommands.*;
import waffleoRai_Utils.BufferReference;

public class LayerCommands {
	
	/*--- Parser ---*/
	public static NUSALSeqCommand parseLayerCommandOld(BufferReference dat, boolean shortMode){
		//TODO
		int cmdi = Byte.toUnsignedInt(dat.getByte());
		int cmdhi = (cmdi & 0xf0) >>> 4;
		int cmdlo = cmdi & 0xf;
		
		if(cmdhi < 0x4){
			if(shortMode){
				//TODO
			}
			else{
				dat.increment();
				int time = NUSALSeqReader.readVLQ(dat);
				dat.decrement();
				int os = time>0x7f?3:2;
				byte v = dat.getByte(os++);
				byte g = dat.getByte(os++);
				return NUSALSeqNoteCommand.fromRawCommand((byte)cmdi, time, v, g);	
			}
		}
		else if(cmdhi < 0x8){
			if(shortMode){
				//TODO
			}
			else{
				dat.increment();
				int time = NUSALSeqReader.readVLQ(dat);
				dat.decrement();
				int os = time>0x7f?3:2;
				byte v = dat.getByte(os++);
				return NUSALSeqNoteCommand.fromRawCommand((byte)cmdi, time, v);	
			}
		}
		else if(cmdhi < 0xc){
			if(shortMode){
				//TODO
			}
			else{
				byte v = dat.getByte(1);
				byte g = dat.getByte(2);
				return NUSALSeqNoteCommand.fromRawCommand((byte)cmdi, v, g);	
			}
		}
		else if(cmdhi == 0xc){
			//TODO
			int i,j,k;
			switch(cmdlo){
			case 0x0:
				dat.increment();
				i = NUSALSeqReader.readVLQ(dat);
				dat.decrement();
				return new C_L_Rest(i);
			case 0x1:
				i = Byte.toUnsignedInt(dat.getByte(1));
				return new C_L_ShortVel(i);
			case 0x2:
				i = (int)dat.getByte(1);
				return new C_L_Transpose(i);
			case 0x3:
				dat.increment();
				i = NUSALSeqReader.readVLQ(dat);
				dat.decrement();
				return new C_L_ShortDelay(i);
			case 0x4: return new C_L_LegatoOn();
			case 0x5: return new C_L_LegatoOff();
			case 0x6:
				i = Byte.toUnsignedInt(dat.getByte(1));
				return new C_L_SetProgram(i);
			case 0x7:
				i = Byte.toUnsignedInt(dat.getByte(1));
				j = Byte.toUnsignedInt(dat.getByte(2));
				k = Byte.toUnsignedInt(dat.getByte(3));
				return new C_L_Portamento(i,j,k);
			case 0x8: return new C_L_PortamentoOff();
			case 0x9:
				i = Byte.toUnsignedInt(dat.getByte(1));
				return new C_L_ShortGate(i);
			case 0xa:
				i = Byte.toUnsignedInt(dat.getByte(1));
				return new C_L_Pan(i);
			case 0xb:
				i = Short.toUnsignedInt(dat.getShort(1));
				j = Byte.toUnsignedInt(dat.getByte(3));
				return new C_L_Envelope(i,j);
			case 0xc: return new C_L_DrumPanOff();
			case 0xd:
				i = Byte.toUnsignedInt(dat.getByte(1));
				return new C_L_ReverbPhase(i);
			case 0xe:
				i = (int)dat.getByte(1);
				return new C_L_PitchBendAlt(i);
			case 0xf:
				i = Byte.toUnsignedInt(dat.getByte(1));
				return new C_L_Release(i);
			}
		}
		else if(cmdhi == 0xd){
			return new C_L_ShortVelTbl(cmdlo);
		}
		else if(cmdhi == 0xe){
			return new C_L_ShortGateTbl(cmdlo);
		}
		else if(cmdhi == 0xf){
			int i;
			switch(cmdlo){
			case 0x0: return new C_UnreserveNotes();
			case 0x1:
				i = (int)dat.getByte(1);
				return new C_ReserveNotes(i);
			case 0x2:
				i = (int)dat.getByte(1);
				return new C_rbltz(i);
			case 0x3:
				i = (int)dat.getByte(1);
				return new C_rbeqz(i);
			case 0x4:
				i = (int)dat.getByte(1);
				return new C_rjump(i);
			case 0x5:
				i = Short.toUnsignedInt(dat.getShort(1));
				return new C_bgez(i);
			case 0x6: return new C_Break();
			case 0x7: return new C_LoopEnd();
			case 0x8:
				i = (int)dat.getByte(1);
				return new C_LoopStart(i);
			case 0x9:
				i = Short.toUnsignedInt(dat.getShort(1));
				return new C_bltz(i);
			case 0xa:
				i = Short.toUnsignedInt(dat.getShort(1));
				return new C_beqz(i);
			case 0xb:
				i = Short.toUnsignedInt(dat.getShort(1));
				return new C_Jump(i);
			case 0xc:
				i = Short.toUnsignedInt(dat.getShort(1));
				return new C_Call(i);
			case 0xd:
				dat.increment();
				i = NUSALSeqReader.readVLQ(dat);
				dat.decrement();
				return new C_Wait(i);
			case 0xe: return new C_Yield();
			case 0xf: return new C_EndRead();
			}
		}
		
		return null;
	}
	
	public static NUSALSeqCommand parseLayerCommandOld(String cmd, String[] args){
		/*
		 * The following commands are not currently supported:
		 * 	lenvelope
		 */
		
		//References resolved by caller.
		cmd = cmd.toLowerCase();
		if(cmd.equals("ldelay")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_L_Rest(n);
		}
		else if(cmd.equals("notedvg")){
			int[] n = NUSALSeqReader.readNumbers(args);
			if(n == null) return null;
			return NUSALSeqNoteCommand.fromRawCommand((byte)n[0], n[1], (byte)n[2], (byte)n[3]);
		}
		else if(cmd.equals("notedv")){
			int[] n = NUSALSeqReader.readNumbers(args);
			if(n == null) return null;
			return NUSALSeqNoteCommand.fromRawCommand((byte)n[0], n[1], (byte)n[2]);
		}
		else if(cmd.equals("notevg")){
			int[] n = NUSALSeqReader.readNumbers(args);
			if(n == null) return null;
			return NUSALSeqNoteCommand.fromRawCommand((byte)n[0], (byte)n[1], (byte)n[2]);
		}
		else if(cmd.equals("shortvel")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_L_ShortVel(n);
		}
		else if(cmd.equals("shortdelay")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_L_ShortDelay(n);
		}
		else if(cmd.equals("shortgate")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_L_ShortGate(n);
		}
		else if(cmd.equals("ltp")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_L_Transpose(n);
		}
		else if(cmd.equals("lpan")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_L_Pan(n);
		}
		else if(cmd.equals("lbend2")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_L_PitchBendAlt(n);
		}
		else if(cmd.equals("legatoon")){return new C_L_LegatoOn();}
		else if(cmd.equals("legatooff")){return new C_L_LegatoOff();}
		else if(cmd.equals("portamento")){
			int[] n = NUSALSeqReader.readNumbers(args);
			if(n == null) return null;
			return new C_L_Portamento(n[0],n[1],n[2]);
		}
		else if(cmd.equals("portamentooff")){return new C_L_PortamentoOff();}
		else if(cmd.equals("lrelease")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_L_Release(n);
		}
		else if(cmd.equals("linst")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_L_SetProgram(n);
		}
		else if(cmd.equals("drumpanoff")){return new C_L_DrumPanOff();}
		else if(cmd.equals("reverbphase")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_L_ReverbPhase(n);
		}
		else if(cmd.equals("shorttablevel")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_L_ShortVelTbl(n);
		}
		else if(cmd.equals("shorttablegate")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_L_ShortGateTbl(n);
		}
		
		NUSALSeqCommand command = FCommands.parseFCommandOld(cmd, args);
		return command;
	}
	
	public static NUSALSeqCommand parseLayerCommand(BufferReference dat, NUSALSeqCommandBook book, boolean shortMode){
		int[] params = new int[4];
		int bb = Byte.toUnsignedInt(dat.nextByte());
		NUSALSeqCommandDef def = book.getLayerCommand((byte)bb, shortMode);
		NUSALSeqCommand.readBinArgs(params, dat, def, bb);
		switch(def.getFunctionalType()) {
		case PLAY_NOTE_NTVG:
		case PLAY_NOTE_NTV:
		case PLAY_NOTE_NVG:
		case SHORT_NOTE_NTVG:
		case SHORT_NOTE_NTV:
		case SHORT_NOTE_NVG:
			return NUSALSeqNoteCommand.fromBinRead(def, params);
		case REST: return new C_L_Rest(def, params[0]);
		case L_SHORTVEL: return null; //TODO
		case L_TRANSPOSE: return null; //TODO
		case L_SHORTTIME: return null; //TODO
		case LEGATO_ON: return null; //TODO
		case LEGATO_OFF: return null; //TODO
		case L_SET_PROGRAM: return null; //TODO
		case PORTAMENTO_ON: return null; //TODO
		case PORTAMENTO_OFF: return null; //TODO
		case L_SHORTGATE: return null; //TODO
		case L_PAN: return null; //TODO
		case L_ENVELOPE: return null; //TODO
		case DRUMPAN_OFF: return null; //TODO
		case L_REVERB_PHASE: return null; //TODO
		case L_PITCHBEND_ALT: return null; //TODO
		case L_RELEASE: return null; //TODO
		case SHORTTBL_VEL: return null; //TODO
		case SHORTTBL_GATE: return null; //TODO
		
		case UNRESERVE_NOTES: return new C_UnreserveNotes(def);
		case RESERVE_NOTES: return new C_ReserveNotes(def, params[0]);
		case BRANCH_IF_LTZ_REL: return new C_rbltz(params[0], def);
		case BRANCH_IF_EQZ_REL: return new C_rbeqz(params[0], def);
		case BRANCH_ALWAYS_REL: return new C_rjump(params[0], def);
		case BRANCH_IF_GTEZ: return new C_bgez(params[0], def);
		case BREAK: return new C_Break(def);
		case LOOP_END: return new C_LoopEnd(def);
		case LOOP_START: return new C_LoopStart(params[0], def);
		case BRANCH_IF_LTZ: return new C_bltz(params[0], def);
		case BRANCH_IF_EQZ: return new C_beqz(params[0], def);
		case BRANCH_ALWAYS: return new C_Jump(params[0], def);
		case CALL: return new C_Call(params[0], def);
		case WAIT: return new C_Wait(def, params[0]);
		case YIELD: return new C_Yield(def);
		case END_READ: return new C_EndRead(def);
		 default: return null;
		}
	}
	
	/*--- 0x00:0x3f notedvg ---*/
	/*--- 0x40:0x7f notedv ---*/
	/*--- 0x80:0xbf notevg ---*/

	/*--- 0xc0 ldelay ---*/
	public static class C_L_Rest extends NUSALSeqWaitCommand{
		public C_L_Rest(int ticks) {
			super(NUSALSeqCmdType.REST, ticks);
		}
		public C_L_Rest(NUSALSeqCommandBook book, int ticks) {
			super(NUSALSeqCmdType.REST, book, ticks);
		}
		public C_L_Rest(NUSALSeqCommandDef def, int ticks) {
			super(def, ticks);
		}
	}
	
	/*--- 0xc1 shortvel ---*/
	public static class C_L_ShortVel extends NUSALSeqGenericCommand{
		public C_L_ShortVel(int value) {
			super(NUSALSeqCmdType.L_SHORTVEL); 
			setParam(0, value);
		}
		public C_L_ShortVel(NUSALSeqCommandBook book, int value) {
			super(NUSALSeqCmdType.L_SHORTVEL, book); 
			setParam(0, value);
		}
		public boolean doCommand(NUSALSeqLayer voice){
			flagLayerUsed(voice.getChannelIndex(), voice.getLayerIndex());
			voice.setShortVel((byte)getParam(0));
			return true;
		}
	}
	
	/*--- 0xc2 ltp ---*/
	public static class C_L_Transpose extends NUSALSeqGenericCommand{
		public C_L_Transpose(int value) {
			super(NUSALSeqCmdType.L_TRANSPOSE); 
			setParam(0, value);
		}
		public C_L_Transpose(NUSALSeqCommandBook book, int value) {
			super(NUSALSeqCmdType.L_TRANSPOSE, book); 
			setParam(0, value);
		}
		public boolean doCommand(NUSALSeqLayer voice){
			flagLayerUsed(voice.getChannelIndex(), voice.getLayerIndex());
			voice.setTranspose(getParam(0));
			return true;
		}
	}
	
	/*--- 0xc3 shortdelay ---*/
	public static class C_L_ShortDelay extends NUSALSeqGenericCommand{
		public C_L_ShortDelay(int value) {
			super(NUSALSeqCmdType.L_SHORTTIME); 
			setParam(0, value);
		}
		public C_L_ShortDelay(NUSALSeqCommandBook book, int value) {
			super(NUSALSeqCmdType.L_SHORTTIME, book); 
			setParam(0, value);
		}
		public boolean doCommand(NUSALSeqLayer voice){
			flagLayerUsed(voice.getChannelIndex(), voice.getLayerIndex());
			voice.setShortDelay(getParam(0));
			return true;
		}
	}
	
	/*--- 0xc4 legatoon ---*/
	public static class C_L_LegatoOn extends NUSALSeqGenericCommand{
		public C_L_LegatoOn() {
			super(NUSALSeqCmdType.LEGATO_ON); 
		}
		public C_L_LegatoOn(NUSALSeqCommandBook book) {
			super(NUSALSeqCmdType.LEGATO_ON, book); 
		}
		public boolean doCommand(NUSALSeqLayer voice){
			flagLayerUsed(voice.getChannelIndex(), voice.getLayerIndex());
			voice.setLegato(true);
			return true;
		}
	}
	
	/*--- 0xc5 legatooff ---*/
	public static class C_L_LegatoOff extends NUSALSeqGenericCommand{
		public C_L_LegatoOff() {
			super(NUSALSeqCmdType.LEGATO_OFF); 
		}
		public C_L_LegatoOff(NUSALSeqCommandBook book) {
			super(NUSALSeqCmdType.LEGATO_OFF, book); 
		}
		public boolean doCommand(NUSALSeqLayer voice){
			flagLayerUsed(voice.getChannelIndex(), voice.getLayerIndex());
			voice.setLegato(false);
			return true;
		}
	}
	
	/*--- 0xc6 linst ---*/
	public static class C_L_SetProgram extends NUSALSeqGenericCommand{
		public C_L_SetProgram(int value) {
			super(NUSALSeqCmdType.L_SET_PROGRAM); 
			setParam(0, value);
		}
		public C_L_SetProgram(NUSALSeqCommandBook book, int value) {
			super(NUSALSeqCmdType.L_SET_PROGRAM, book); 
			setParam(0, value);
		}
		public boolean doCommand(NUSALSeqLayer voice){
			flagLayerUsed(voice.getChannelIndex(), voice.getLayerIndex());
			voice.changeProgram(getParam(0));
			return true;
		}
	}
	
	/*--- 0xc7 portamento ---*/
	public static class C_L_Portamento extends NUSALSeqGenericCommand{
		public C_L_Portamento(int mode, int target, int time) {
			super(NUSALSeqCmdType.PORTAMENTO_ON); 
			setParam(0, mode);
			setParam(1, target);
			setParam(2, time);
		}
		public C_L_Portamento(NUSALSeqCommandBook book, int mode, int target, int time) {
			super(NUSALSeqCmdType.PORTAMENTO_ON, book); 
			setParam(0, mode);
			setParam(1, target);
			setParam(2, time);
		}
		public boolean doCommand(NUSALSeqLayer voice){
			flagLayerUsed(voice.getChannelIndex(), voice.getLayerIndex());
			voice.setPortamento(getParam(0), getParam(1), getParam(2));
			return true;
		}
	}
	
	/*--- 0xc8 portamentooff ---*/
	public static class C_L_PortamentoOff extends NUSALSeqGenericCommand{
		public C_L_PortamentoOff() {
			super(NUSALSeqCmdType.PORTAMENTO_OFF); 
		}
		public C_L_PortamentoOff(NUSALSeqCommandBook book) {
			super(NUSALSeqCmdType.PORTAMENTO_OFF); 
		}
		public boolean doCommand(NUSALSeqLayer voice){
			flagLayerUsed(voice.getChannelIndex(), voice.getLayerIndex());
			voice.setPortamentoOff();
			return true;
		}
	}
	
	/*--- 0xc9 shortgate ---*/
	public static class C_L_ShortGate extends NUSALSeqGenericCommand{
		public C_L_ShortGate(int value) {
			super(NUSALSeqCmdType.L_SHORTGATE); 
			setParam(0, value);
		}
		public C_L_ShortGate(NUSALSeqCommandBook book, int value) {
			super(NUSALSeqCmdType.L_SHORTGATE, book); 
			setParam(0, value);
		}
		public boolean doCommand(NUSALSeqLayer voice){
			flagLayerUsed(voice.getChannelIndex(), voice.getLayerIndex());
			voice.setShortGate((byte)getParam(0));
			return true;
		}
	}
	
	/*--- 0xca lpan ---*/
	public static class C_L_Pan extends NUSALSeqGenericCommand{
		public C_L_Pan(int value) {
			super(NUSALSeqCmdType.L_PAN); 
			setParam(0, value);
		}
		public C_L_Pan(NUSALSeqCommandBook book, int value) {
			super(NUSALSeqCmdType.L_PAN, book); 
			setParam(0, value);
		}
		public boolean doCommand(NUSALSeqLayer voice){
			flagLayerUsed(voice.getChannelIndex(), voice.getLayerIndex());
			voice.setPan((byte)getParam(0));
			return true;
		}
	}
	
	/*--- 0xcb lenvelope ---*/
	public static class C_L_Envelope extends NUSALSeqDataRefCommand{
		public C_L_Envelope(int addr, int release) {
			super(NUSALSeqCmdType.L_ENVELOPE, null, addr, 16); //I think it's 16? 
			//setParam(0, addr);
			setParam(1, release);
			super.setLabelPrefix("env");
		}
		public C_L_Envelope(NUSALSeqCommandBook book, int addr, int release) {
			super(NUSALSeqCmdType.L_ENVELOPE, book, addr, 16); 
			//setParam(0, addr);
			setParam(1, release);
			super.setLabelPrefix("env");
		}
		public boolean doCommand(NUSALSeqLayer voice){
			flagLayerUsed(voice.getChannelIndex(), voice.getLayerIndex());
			voice.setEnvelopeAddress(getParam(0));
			voice.setRelease(getParam(1));
			return true;
		}
	}
	
	/*--- 0xcc drumpanoff ---*/
	public static class C_L_DrumPanOff extends NUSALSeqGenericCommand{
		public C_L_DrumPanOff() {
			super(NUSALSeqCmdType.DRUMPAN_OFF); 
		}
		public C_L_DrumPanOff(NUSALSeqCommandBook book) {
			super(NUSALSeqCmdType.DRUMPAN_OFF, book); 
		}
		public boolean doCommand(NUSALSeqLayer voice){
			flagLayerUsed(voice.getChannelIndex(), voice.getLayerIndex());
			voice.drumPanOff();
			return true;
		}
	}
	
	/*--- 0xcd reverbphase ---*/
	public static class C_L_ReverbPhase extends CMD_IgnoredCommand{
		public C_L_ReverbPhase(int val) {
			super(NUSALSeqCmdType.L_REVERB_PHASE);
			super.setParam(0, val);
		}
		public C_L_ReverbPhase(NUSALSeqCommandBook book, int val) {
			super(NUSALSeqCmdType.L_REVERB_PHASE, book);
			super.setParam(0, val);
		}
	}
	
	/*--- 0xce lbend2 ---*/
	public static class C_L_PitchBendAlt extends NUSALSeqGenericCommand{
		public C_L_PitchBendAlt(int value) {
			super(NUSALSeqCmdType.L_PITCHBEND_ALT); 
			setParam(0, value);
		}
		public C_L_PitchBendAlt(NUSALSeqCommandBook book, int value) {
			super(NUSALSeqCmdType.L_PITCHBEND_ALT, book); 
			setParam(0, value);
		}
		public boolean doCommand(NUSALSeqLayer voice){
			flagLayerUsed(voice.getChannelIndex(), voice.getLayerIndex());
			voice.setPitchbendAlt(getParam(0));
			return true;
		}
	}
	
	/*--- 0xcf lrelease ---*/
	public static class C_L_Release extends NUSALSeqGenericCommand{
		public C_L_Release(int value) {
			super(NUSALSeqCmdType.L_RELEASE); 
			setParam(0, value);
		}
		public C_L_Release(NUSALSeqCommandBook book, int value) {
			super(NUSALSeqCmdType.L_RELEASE, book); 
			setParam(0, value);
		}
		public boolean doCommand(NUSALSeqLayer voice){
			flagLayerUsed(voice.getChannelIndex(), voice.getLayerIndex());
			voice.setRelease(getParam(0));
			return true;
		}
	}
	
	/*--- 0xd0:0xdf shorttablevel ---*/
	public static class C_L_ShortVelTbl extends NUSALSeqGenericCommand{
		public C_L_ShortVelTbl(int idx) {
			super(NUSALSeqCmdType.SHORTTBL_VEL); 
			setParam(0, idx);
		}
		public C_L_ShortVelTbl(NUSALSeqCommandBook book, int idx) {
			super(NUSALSeqCmdType.SHORTTBL_VEL, book); 
			setParam(0, idx);
		}
		public boolean doCommand(NUSALSeqLayer voice){
			flagLayerUsed(voice.getChannelIndex(), voice.getLayerIndex());
			voice.setShortVelFromTable(getParam(0));
			return true;
		}
	}
	
	/*--- 0xe0:0xef shorttablegate ---*/
	public static class C_L_ShortGateTbl extends NUSALSeqGenericCommand{
		public C_L_ShortGateTbl(int idx) {
			super(NUSALSeqCmdType.SHORTTBL_GATE); 
			setParam(0, idx);
		}
		public C_L_ShortGateTbl(NUSALSeqCommandBook book, int idx) {
			super(NUSALSeqCmdType.SHORTTBL_GATE, book); 
			setParam(0, idx);
		}
		public boolean doCommand(NUSALSeqLayer voice){
			flagLayerUsed(voice.getChannelIndex(), voice.getLayerIndex());
			voice.setShortGateFromTable(getParam(0));
			return true;
		}
	}
	
}
