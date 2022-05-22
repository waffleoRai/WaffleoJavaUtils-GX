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

	private LinkedList<NUSALSeqCommand> commands;
	
	public NUSALSeqCommandChunk() {
		super(NUSALSeqCmdType.MULTI_EVENT_CHUNK, (byte)0x00);
		commands = new LinkedList<NUSALSeqCommand>();
	}

	public boolean isChunk(){return true;}
	public boolean isEmpty(){return commands.isEmpty();}
	
	public NUSALSeqCommand getChunkHead(){
		if(commands.isEmpty()) return null;
		return commands.getFirst();
	}
	
	public NUSALSeqCommand getChunkTail(){
		if(commands.isEmpty()) return null;
		return commands.getLast();
	}

	public void setAddress(int addr){
		super.setAddress(addr);
		int pos = addr;
		for(NUSALSeqCommand cmd : commands){
			cmd.setAddress(pos);
			pos += cmd.getSizeInBytes();
		}
	}

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
		for(NUSALSeqCommand cmd : commands) total += cmd.getSizeInBytes();
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
	
	public void linearizeTo(List<NUSALSeqCommand> cmdlist){
		if(cmdlist == null) return;
		//Expands calls and chunks
		for(NUSALSeqCommand cmd : commands){
			if(cmd instanceof NUSALSeqCommandChunk){
				((NUSALSeqCommandChunk)cmd).linearizeTo(cmdlist);
			}
			else if(cmd.getCommand().flagSet(NUSALSeqCommands.FLAG_CALL)){
				//TODO Maybe I'll do this eventually, but it would require duplication and eh.
				cmdlist.add(cmd);
			}
			else cmdlist.add(cmd);
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
			ctype = cmd.getCommand();
			if(ctype.flagSet(NUSALSeqCommands.FLAG_ISWAIT) || cmd instanceof NUSALSeqNoteCommand){
				if(cmd.getSizeInTicks() > 0) commands.add(cmd);
			}
			else{
				commands.add(cmd);
			}
		}
		linkSequentialCommands();
	}
	
	public void mapByAddress(Map<Integer, NUSALSeqCommand> map){
		for(NUSALSeqCommand cmd : commands) cmd.mapByAddress(map);
	}
	
	public String toMMLCommand(boolean comment_addr){
		String out = "";
		for(NUSALSeqCommand cmd : commands){
			out += cmd.toMMLCommand(comment_addr) + "\n";
		}
		return out;
	}
	
	public byte[] serializeMe(){
		if(this.isEmpty()) return null;
		byte[] barr = new byte[getSizeInBytes()];
		int i = 0;
		for(NUSALSeqCommand cmd : commands){
			byte[] scmd = cmd.serializeMe();
			for(int j = 0; j < scmd.length; j++){
				barr[i++] = scmd[j];
			}
		}
		return barr;
	}
	
	public int serializeTo(OutputStream stream) throws IOException{
		int total = 0;
		for(NUSALSeqCommand cmd : commands){
			total += cmd.serializeTo(stream);
		}
		return total;
	}
	
	public int serializeTo(FileBuffer buffer){
		int total = 0;
		for(NUSALSeqCommand cmd : commands){
			total += cmd.serializeTo(buffer);
		}
		return total;
	}
	
	public String toString(){
		if(isEmpty()) return "";
		return "block @ 0x" + Integer.toHexString(super.getAddress());
	}
	
}
