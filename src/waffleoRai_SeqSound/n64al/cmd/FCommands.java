package waffleoRai_SeqSound.n64al.cmd;

import waffleoRai_SeqSound.n64al.NUSALSeq;
import waffleoRai_SeqSound.n64al.NUSALSeqChannel;
import waffleoRai_SeqSound.n64al.NUSALSeqCmdType;
import waffleoRai_SeqSound.n64al.NUSALSeqCommand;
import waffleoRai_SeqSound.n64al.NUSALSeqLayer;

public class FCommands {
	
	/*--- Command Reader ---*/
	
	public static NUSALSeqCommand parseFCommand(String cmd, String[] args){
		//References handled by outer functions
		cmd = cmd.toLowerCase();
		if(cmd.equals("end")) return new C_EndRead();
		else if(cmd.equals("delay")){
			int n = 0;
			try{n = Integer.parseInt(args[0]);}
			catch(NumberFormatException ex){ex.printStackTrace(); return null;}
			return new C_Wait(n);
		}
		else if(cmd.equals("call")){return new C_Call(-1);}
		else if(cmd.equals("jump")){return new C_Jump(-1);}
		else if(cmd.equals("yield")){return new C_Yield();}
		else if(cmd.equals("bltz")){return new C_bltz(-1);}
		else if(cmd.equals("beqz")){return new C_beqz(-1);}
		else if(cmd.equals("bgez")){return new C_bgez(-1);}
		else if(cmd.equals("rbltz")){return new C_rbltz(-1);}
		else if(cmd.equals("rbeqz")){return new C_rbeqz(-1);}
		else if(cmd.equals("rjump")){return new C_rjump(-1);}
		else if(cmd.equals("break")){return new C_Break();}
		else if(cmd.equals("loop")){
			int n = 0;
			try{n = Integer.parseInt(args[0]);}
			catch(NumberFormatException ex){ex.printStackTrace(); return null;}
			return new C_LoopStart(n);
		}
		else if(cmd.equals("loopend")){return new C_LoopEnd();}
		else if(cmd.equals("unreservenotes")){return new C_UnreserveNotes();}
		else if(cmd.equals("reservenotes")){
			int n = 0;
			try{n = Integer.parseInt(args[0]);}
			catch(NumberFormatException ex){ex.printStackTrace(); return null;}
			return new C_ReserveNotes(n);
		}
		return null;
	}
	
	/*--- ABCs ---*/
	public static abstract class BranchCommand extends NUSALSeqReferenceCommand{
		protected BranchCommand(NUSALSeqCmdType cmd, int value, boolean rel) {
			super(cmd, value, rel);
		}
		
		protected BranchCommand(NUSALSeqCmdType cmd, int idx, int value, boolean rel) {
			super(cmd, idx, value, rel);
		}
		
		protected abstract boolean checkCondition(NUSALSeq sequence);
		protected abstract boolean checkCondition(NUSALSeqChannel channel);
		protected abstract boolean checkCondition(NUSALSeqLayer voice);

		public boolean doCommand(NUSALSeq sequence){
			flagSeqUsed();
			if(checkCondition(sequence)){
				return sequence.jumpTo(getBranchAddress(), false);
			}
			return true;
		}
		
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			if(checkCondition(channel)){
				return channel.jumpTo(getBranchAddress(), false);
			}
			return true;
		}
		
		public boolean doCommand(NUSALSeqLayer voice){
			flagLayerUsed(voice.getChannelIndex(), voice.getLayerIndex());
			if(checkCondition(voice)){
				return voice.jumpTo(getBranchAddress(), false);
			}
			return true;
		}
	}

	/*--- f0: unreservenotes ---*/
	public static class C_UnreserveNotes extends CMD_IgnoredCommand{
		public C_UnreserveNotes() {
			super(NUSALSeqCmdType.UNRESERVE_NOTES);
		}
	}
	
	/*--- f1: reservenotes ---*/
	public static class C_ReserveNotes extends CMD_IgnoredCommand{
		public C_ReserveNotes(int notes) {
			super(NUSALSeqCmdType.RESERVE_NOTES);
			super.setParam(0, notes);
		}
	}
	
	/*--- f2: rbltz ---*/
	public static class C_rbltz extends BranchCommand{
		public C_rbltz(int offset) {super(NUSALSeqCmdType.BRANCH_IF_LTZ_REL, offset, true);}
		protected boolean checkCondition(NUSALSeq sequence){return sequence.getVarQ() < 0;}
		protected boolean checkCondition(NUSALSeqChannel channel){return channel.getVarQ() < 0;}
		protected boolean checkCondition(NUSALSeqLayer voice){return voice.getVarQ() < 0;}
		public NUSALSeqCmdType getRelativeCommand(){return NUSALSeqCmdType.BRANCH_IF_LTZ_REL;}
		public NUSALSeqCmdType getAbsoluteCommand(){return NUSALSeqCmdType.BRANCH_IF_LTZ;}
	}
	
	/*--- f3: rbeqz ---*/
	public static class C_rbeqz extends BranchCommand{
		public C_rbeqz(int offset) {super(NUSALSeqCmdType.BRANCH_IF_EQZ_REL, offset, true);}
		protected boolean checkCondition(NUSALSeq sequence){return sequence.getVarQ() == 0;}
		protected boolean checkCondition(NUSALSeqChannel channel){return channel.getVarQ() == 0;}
		protected boolean checkCondition(NUSALSeqLayer voice){return voice.getVarQ() == 0;}
		public NUSALSeqCmdType getRelativeCommand(){return NUSALSeqCmdType.BRANCH_IF_EQZ_REL;}
		public NUSALSeqCmdType getAbsoluteCommand(){return NUSALSeqCmdType.BRANCH_IF_EQZ;}
	}
	
	/*--- f4: rjump ---*/
	public static class C_rjump extends BranchCommand{
		public C_rjump(int offset) {super(NUSALSeqCmdType.BRANCH_ALWAYS_REL, offset, true);}
		protected boolean checkCondition(NUSALSeq sequence){return true;}
		protected boolean checkCondition(NUSALSeqChannel channel){return true;}
		protected boolean checkCondition(NUSALSeqLayer voice){return true;}
		public NUSALSeqCmdType getRelativeCommand(){return NUSALSeqCmdType.BRANCH_ALWAYS_REL;}
		public NUSALSeqCmdType getAbsoluteCommand(){return NUSALSeqCmdType.BRANCH_ALWAYS;}
	}
	
	/*--- f5: bgez ---*/
	public static class C_bgez extends BranchCommand{
		public C_bgez(int address) {super(NUSALSeqCmdType.BRANCH_IF_GTEZ, address, false);}
		protected boolean checkCondition(NUSALSeq sequence){return sequence.getVarQ() >= 0;}
		protected boolean checkCondition(NUSALSeqChannel channel){return channel.getVarQ() >= 0;}
		protected boolean checkCondition(NUSALSeqLayer voice){return voice.getVarQ() >= 0;}
		public NUSALSeqCmdType getRelativeCommand(){return null;}
		public NUSALSeqCmdType getAbsoluteCommand(){return NUSALSeqCmdType.BRANCH_IF_GTEZ;}
	}
	
	/*--- f6: break ---*/
	public static class C_Break extends NUSALSeqGenericCommand{
		public C_Break() {super(NUSALSeqCmdType.BREAK);}
		public boolean doCommand(NUSALSeq sequence){
			flagSeqUsed();
			sequence.breakLoop();
			return true;
		}
		
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			channel.breakLoop();
			return true;
		}
		
		public boolean doCommand(NUSALSeqLayer voice){
			flagLayerUsed(voice.getChannelIndex(), voice.getLayerIndex());
			voice.breakLoop();
			return true;
		}
	}
	
	/*--- f7: loopend ---*/
	public static class C_LoopEnd extends NUSALSeqGenericCommand{
		public C_LoopEnd() {super(NUSALSeqCmdType.LOOP_END);}
		public boolean doCommand(NUSALSeq sequence){
			flagSeqUsed();
			sequence.signalLoopEnd();
			return true;
		}
		
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			channel.signalLoopEnd();
			return true;
		}
		
		public boolean doCommand(NUSALSeqLayer voice){
			flagLayerUsed(voice.getChannelIndex(), voice.getLayerIndex());
			voice.signalLoopEnd();
			return true;
		}
	}
	
	/*--- f8: loop ---*/
	public static class C_LoopStart extends NUSALSeqGenericCommand{
		public C_LoopStart(int loopCount) {super(NUSALSeqCmdType.LOOP_START); setParam(0, loopCount);}
		public boolean doCommand(NUSALSeq sequence){
			flagSeqUsed();
			sequence.signalLoopStart(super.getParam(0));
			return true;
		}
		
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			channel.signalLoopStart(super.getParam(0));
			return true;
		}
		
		public boolean doCommand(NUSALSeqLayer voice){
			flagLayerUsed(voice.getChannelIndex(), voice.getLayerIndex());
			voice.signalLoopStart(super.getParam(0));
			return true;
		}
	}
	
	/*--- f9: bltz ---*/
	public static class C_bltz extends BranchCommand{
		public C_bltz(int address) {super(NUSALSeqCmdType.BRANCH_IF_LTZ, address, false);}
		protected boolean checkCondition(NUSALSeq sequence){return sequence.getVarQ() < 0;}
		protected boolean checkCondition(NUSALSeqChannel channel){return channel.getVarQ() < 0;}
		protected boolean checkCondition(NUSALSeqLayer voice){return voice.getVarQ() < 0;}
		public NUSALSeqCmdType getRelativeCommand(){return NUSALSeqCmdType.BRANCH_IF_LTZ_REL;}
		public NUSALSeqCmdType getAbsoluteCommand(){return NUSALSeqCmdType.BRANCH_IF_LTZ;}
	}
	
	/*--- fa: beqz ---*/
	public static class C_beqz extends BranchCommand{
		public C_beqz(int address) {super(NUSALSeqCmdType.BRANCH_IF_EQZ, address, false);}
		protected boolean checkCondition(NUSALSeq sequence){return sequence.getVarQ() == 0;}
		protected boolean checkCondition(NUSALSeqChannel channel){return channel.getVarQ() == 0;}
		protected boolean checkCondition(NUSALSeqLayer voice){return voice.getVarQ() == 0;}
		public NUSALSeqCmdType getRelativeCommand(){return NUSALSeqCmdType.BRANCH_IF_EQZ_REL;}
		public NUSALSeqCmdType getAbsoluteCommand(){return NUSALSeqCmdType.BRANCH_IF_EQZ;}
	}
	
	/*--- fb: jump ---*/
	public static class C_Jump extends BranchCommand{
		public C_Jump(int address) {super(NUSALSeqCmdType.BRANCH_ALWAYS, address, false);}
		protected boolean checkCondition(NUSALSeq sequence){return true;}
		protected boolean checkCondition(NUSALSeqChannel channel){return true;}
		protected boolean checkCondition(NUSALSeqLayer voice){return true;}
		public NUSALSeqCmdType getRelativeCommand(){return NUSALSeqCmdType.BRANCH_ALWAYS_REL;}
		public NUSALSeqCmdType getAbsoluteCommand(){return NUSALSeqCmdType.BRANCH_ALWAYS;}
	}
	
	/*--- fc: call ---*/
	public static class C_Call extends NUSALSeqReferenceCommand{
		public C_Call(int address) {
			super(NUSALSeqCmdType.CALL, address, false);
		}

		public boolean doCommand(NUSALSeq sequence){
			flagSeqUsed();
			return sequence.jumpTo(getBranchAddress(), true);
		}
		
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			return channel.jumpTo(getBranchAddress(), true);
		}
		
		public boolean doCommand(NUSALSeqLayer voice){
			flagLayerUsed(voice.getChannelIndex(), voice.getLayerIndex());
			return voice.jumpTo(getBranchAddress(), true);
		}
		
		public NUSALSeqCmdType getRelativeCommand(){return null;}
		public NUSALSeqCmdType getAbsoluteCommand(){return NUSALSeqCmdType.CALL;}
		
		public int getSizeInTicks(){
			NUSALSeqCommand ref = getBranchTarget();
			if(ref != null){
				//System.err.println("ref size: " + (ref.getSizeInTicks()));
				return ref.getSizeInTicks();
			}
			return 0;
		}
	}
	
	/*--- fd: delay ---*/
	public static class C_Wait extends NUSALSeqWaitCommand{
		public C_Wait(int ticks) {
			super(NUSALSeqCmdType.WAIT, ticks);
		}
	}
	
	/*--- fe: yield ---*/
	public static class C_Yield extends NUSALSeqWaitCommand{
		public C_Yield() {
			super(NUSALSeqCmdType.YIELD, 1);
		}
	}
	
	/*--- ff: end ---*/
	public static class C_EndRead extends NUSALSeqGenericCommand{
		public C_EndRead() {super(NUSALSeqCmdType.END_READ);}
		public boolean isEndCommand(){return true;}		
		public boolean doCommand(NUSALSeq sequence){
			flagSeqUsed();
			sequence.signalSeqEnd();
			return true;
		}
		
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			channel.signalReadEnd();
			return true;
		}
		
		public boolean doCommand(NUSALSeqLayer voice){
			flagLayerUsed(voice.getChannelIndex(), voice.getLayerIndex());
			voice.signalReadEnd();
			return true;
		}
	}
	
}
