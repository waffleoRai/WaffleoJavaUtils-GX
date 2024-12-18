package waffleoRai_SeqSound.n64al.cmd;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import waffleoRai_SeqSound.n64al.NUSALSeqCmdType;
import waffleoRai_SeqSound.n64al.NUSALSeqCommand;
import waffleoRai_SeqSound.n64al.NUSALSeqCommands;
import waffleoRai_Utils.FileBuffer;

public class NUSALSeqCommandChunk extends NUSALSeqCommand{
	
	/*----- Instance Variables -----*/

	private LinkedList<NUSALSeqCommand> commands;
	
	/*----- Init -----*/
	
	public NUSALSeqCommandChunk() {
		super(NUSALSeqCommandDef.getChunkDummyDef(), (byte)0x00);
		commands = new LinkedList<NUSALSeqCommand>();
	}

	/*----- Getters -----*/
	
	public boolean isChunk(){return true;}
	public boolean isEmpty(){return commands.isEmpty();}
	
	public boolean isNoteless() {
		for(NUSALSeqCommand cmd : commands) {
			if(cmd instanceof NUSALSeqCommandChunk) {
				NUSALSeqCommandChunk ccmd = (NUSALSeqCommandChunk)cmd;
				if(!ccmd.isNoteless()) return false;
			}
			else {
				if(cmd instanceof NUSALSeqNoteCommand) return false;
				NUSALSeqCmdType cc = cmd.getFunctionalType();
				if(cc == NUSALSeqCmdType.PLAY_NOTE_NTVG) return false;
				else if(cc == NUSALSeqCmdType.PLAY_NOTE_NTV) return false;
				else if(cc == NUSALSeqCmdType.PLAY_NOTE_NVG) return false;
				else if(cc == NUSALSeqCmdType.SHORT_NOTE_NTV) return false;
				else if(cc == NUSALSeqCmdType.SHORT_NOTE_NTVG) return false;
				else if(cc == NUSALSeqCmdType.SHORT_NOTE_NVG) return false;
			}
		}
		return true;
	}
	
	public NUSALSeqCommand getChunkHead(){
		if(commands.isEmpty()) return null;
		return commands.getFirst();
	}
	
	public NUSALSeqCommand getChunkTail(){
		if(commands.isEmpty()) return null;
		return commands.getLast();
	}
	
	public int getChildCount(){
		return commands.size();
	}
	
	public int getTotalCommandCount(){
		//Counts commands within chunk children recursively
		int count = 0;
		for(NUSALSeqCommand cmd : commands){
			if(cmd instanceof NUSALSeqCommandChunk){
				count += ((NUSALSeqCommandChunk)cmd).getTotalCommandCount();
			}
			else count++;
		}
		return count;
	}
	
	public int getSizeInBytes() {
		int total = 0;
		int last_end = -1;
		for(NUSALSeqCommand cmd : commands){
			int caddr = cmd.getAddress();
			int csize = cmd.getSizeInBytes();
			int cend = caddr + csize;
			if(last_end >= 0){
				csize += caddr - last_end;
			}
			total += csize;
			last_end = cend;
		}
		return total;
	}
	
	public int getSizeInTicks(){
		int total = 0;
		//System.err.println("NUSALSeqCommandChunk.getSizeInTicks || Called");
		for(NUSALSeqCommand cmd : commands){
			//System.err.println("NUSALSeqCommandChunk.getSizeInTicks || Command: " + cmd.toMMLCommand());
			total += cmd.getSizeInTicks();
		}
		//System.err.println("NUSALSeqCommandChunk.getSizeInTicks || Returning " + total);
		return total;
	}
		
	public List<NUSALSeqCommand> getCommands(){
		List<NUSALSeqCommand> copy = new ArrayList<NUSALSeqCommand>(commands.size()+1);
		copy.addAll(commands);
		return copy;
	}
	
	/*----- Setters -----*/
	
	public void setAddress(int addr){
		super.setAddress(addr);
		int pos = addr;
		for(NUSALSeqCommand cmd : commands){
			cmd.setAddress(pos);
			pos = cmd.getAddress() + cmd.getSizeInBytes(); //setAddress may adjust address if command needs alignment!
		}
	}
	
	public void addCommand(NUSALSeqCommand cmd){
		if(cmd == null) return;
		if(commands.isEmpty()){
			String clbl = cmd.getLabel();
			if(clbl != null){super.setLabel(clbl);}
			else{
				if(super.getLabel() != null){
					cmd.setLabel(super.getLabel());
				}
				/*else{
					//Gen new label for both.
					Random r = new Random();
					clbl = "lbl_" + Integer.toHexString(r.nextInt());
					super.setLabel(clbl);
					cmd.setLabel(clbl);
				}*/
			}
		}
		commands.add(cmd);
	}
	
	/*-----  Organization -----*/
	
	public void slideReferencesToChunkHeads(){
		for(NUSALSeqCommand cmd : commands){
			if(cmd.isChunk()){
				((NUSALSeqCommandChunk)cmd).slideReferencesToChunkHeads();
			}
			else{
				NUSALSeqCommand ref = cmd.getBranchTarget();
				if(ref != null){
					//Look backwards for label.
					while(ref != null && (ref.getLabel() == null)){
						ref = ref.getPreviousCommand();
					}
					if(ref != null) cmd.setReference(ref);
				}
			}
		}
	}
	
	public void dechunkReference(){
		for(NUSALSeqCommand cmd : commands){
			cmd.dechunkReference();
		}
	}
	
	public void linearizeTo(List<NUSALSeqCommand> cmdlist){
		if(cmdlist == null) return;
		//Expands calls and chunks
		for(NUSALSeqCommand cmd : commands){
			if(cmd instanceof NUSALSeqCommandChunk){
				((NUSALSeqCommandChunk)cmd).linearizeTo(cmdlist);
			}
			else {
				NUSALSeqCommandDef def = cmd.getCommandDef();
				if(def != null && def.flagsSet(NUSALSeqCommands.FLAG_CALL)) {
					//TODO Maybe I'll do this eventually, but it would require duplication and eh.
					cmdlist.add(cmd);
				}
				else cmdlist.add(cmd);
			}
		}
	}
	
	public void linkSequentialCommands(){
		NUSALSeqCommand last = null;
		for(NUSALSeqCommand cmd : commands){
			if(last != null) last.setSubsequentCommand(cmd);
			last = cmd;
		}
	}
	
	public void removeEmptyNotesAndDelays(){
		LinkedList<NUSALSeqCommand> old = commands;
		commands = new LinkedList<NUSALSeqCommand>();
		NUSALSeqCmdType ctype = null;
		for(NUSALSeqCommand cmd : old){
			ctype = cmd.getFunctionalType();
			if(ctype != null) {
				if(ctype.flagSet(NUSALSeqCommands.FLAG_ISWAIT) || cmd instanceof NUSALSeqNoteCommand){
					if(cmd.getSizeInTicks() > 0) commands.add(cmd);
				}
				else commands.add(cmd);
			}
			else commands.add(cmd);
		}
		linkSequentialCommands();
	}
	
	public void mapByAddress(Map<Integer, NUSALSeqCommand> map){
		for(NUSALSeqCommand cmd : commands) cmd.mapByAddress(map);
	}
	
	/*----- Command Interactions -----*/
	
	public STSResult storeToSelf(int offset, byte value){
		//Find command that includes the target offset.
		int pos = 0; int end = -1;
		for(NUSALSeqCommand cmd : commands){
			end = pos + cmd.getSizeInBytes();
			if(offset >= pos && offset < end){
				return cmd.storeToSelf(offset - pos, value);
			}
		}
		return STSResult.OUTSIDE;
	}
	
	/*----- Serialization -----*/

	public String toMMLCommand(boolean comment_addr, int syntax){
		String out = "";
		for(NUSALSeqCommand cmd : commands){
			out += cmd.toMMLCommand(comment_addr, syntax) + "\n";
		}
		return out;
	}
	
	public byte[] serializeMe(){
		if(this.isEmpty()) return null;
		byte[] barr = new byte[getSizeInBytes()];
		int i = 0;
		int last_end = -1;
		for(NUSALSeqCommand cmd : commands){
			int caddr = cmd.getAddress();
			byte[] scmd = cmd.serializeMe();
			if(last_end >= 0){
				int dpad = caddr - last_end;
				if(dpad > 0){
					for(int j = 0; j < dpad; j++) barr[i++] = 0;
				}
			}
			for(int j = 0; j < scmd.length; j++){
				barr[i++] = scmd[j];
			}
			last_end = caddr + scmd.length;
		}
		return barr;
	}
	
	public int serializeTo(OutputStream stream) throws IOException{
		int total = 0;
		int last_end = -1;
		for(NUSALSeqCommand cmd : commands){
			int caddr = cmd.getAddress();
			int csize = cmd.getSizeInBytes();
			if(last_end >= 0){
				int dpad = caddr - last_end;
				for(int j = 0; j < dpad; j++) {
					stream.write(0); total++;
				}
			}
			total += cmd.serializeTo(stream);
			last_end = caddr + csize;
		}
		return total;
	}
	
	public int serializeTo(FileBuffer buffer){
		int total = 0;
		int last_end = -1;
		for(NUSALSeqCommand cmd : commands){
			int caddr = cmd.getAddress();
			int csize = cmd.getSizeInBytes();
			if(last_end >= 0){
				int dpad = caddr - last_end;
				for(int j = 0; j < dpad; j++) {
					buffer.addToFile(FileBuffer.ZERO_BYTE); total++;
				}
			}
			total += cmd.serializeTo(buffer);
			last_end = caddr + csize;
		}
		return total;
	}
	
	public String toString(){
		if(isEmpty()) return "";
		return "block @ 0x" + Integer.toHexString(super.getAddress());
	}
	
}
