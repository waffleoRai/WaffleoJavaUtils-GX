package waffleoRai_PSXMDEC;

import java.util.Deque;
import java.util.LinkedList;

public class JavaMDECIO implements IMDECIO{
	
	/* ----- Constants ----- */
	
	/* ----- Instance Variables ----- */
	
	private PSXMDEC mdec;
	
	private Deque<MDECCommand> cmd_queue;
	private HalfwordStream instream;
	
	private Deque<Integer> out_queue;
	
	/* ----- Construction ----- */
	
	public JavaMDECIO(){
		instream = new HalfwordStream();
		cmd_queue = new LinkedList<MDECCommand>();
		out_queue = new LinkedList<Integer>();
	}
	
	/* ----- Word IO ----- */
	
	public void sendMacroblockInstruction(int param_words){
		CMD_DecodeMacroblock cmd = new CMD_DecodeMacroblock(PSXMDEC.OUTPUT_DEPTH_24BIT, false, false, param_words);
		cmd_queue.add(cmd);
	}
	
	public int feedInstructionWord(int word){
		//Returns how many more words it needs to execute this instruction
		MDECCommand cmd = IMDECIO.disassembleMDECCommand(word);
		if(cmd == null) throw new IllegalArgumentException("Not a valid MDEC command!");
		cmd_queue.add(cmd);
		
		return cmd.getDataWordCount();
	}
	
	public void feedDataWord(int word){
		instream.addWord(word);
	}
	
	public void feedDataHalfword(int hw){
		instream.addHalfword(hw);
	}
	
	public int instructionsQueued(){
		return cmd_queue.size();
	}
	
	public boolean executeNextInstruction(){
		if(mdec == null) return false;
		try{
			MDECCommand cmd = cmd_queue.pop();
			//System.err.println("JavaMDECIO.executeNextInstruction || MDEC Instruction: " + cmd.toString());
			cmd.execute(mdec);
			return true;
		}
		catch(Exception x) {x.printStackTrace(); return false;}
	}
	
	public HalfwordStream getInputStream(int inputBlock) {return instream;}

	public void queueWordForOutput(int word) {
		out_queue.add(word);
	}
	
	public int outputWordsQueued(){
		return out_queue.size();
	}
	
	public int nextOutputWord(){
		return out_queue.pop();
	}
	
	/* ----- Status Setters ----- */
	
	public void linkMDEC(PSXMDEC mdec){
		this.mdec = mdec;
		mdec.setAsync(false);
		mdec.setRGBOutput(true);
	}
	
	public void setCurrentInputBlock(int n) {}
	public void setDataOutputDepth(int bdEnum) {}
	public void setDataOutputSigned(boolean b) {}
	public void setDataOutputSet15(boolean b) {}
	
	public void setYUVOutput(boolean b){mdec.setYUVOutput(b);}
	
	/* ----- MDEC Control ----- */
	
	public void signalMacroblockOutputQueueStart(int bdEnum, boolean isSigned, boolean set15) {}
	public void signalMacroblockOutputQueueEnd() {}
	
	/* ----- Output ----- */

	//Not used - this IO module is synchronous
	public void startDisassembler() {}
	public void stopDisassembler() {}
	public void interruptDisassembler() {}


}
