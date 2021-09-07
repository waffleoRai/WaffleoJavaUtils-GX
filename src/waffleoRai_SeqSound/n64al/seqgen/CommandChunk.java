package waffleoRai_SeqSound.n64al.seqgen;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import waffleoRai_SeqSound.n64al.NUSALSeqCmdType;

class CommandChunk extends BuilderCommand{

	private List<BuilderCommand> commands;
	
	public CommandChunk() {
		super(NUSALSeqCmdType.MULTI_EVENT_CHUNK, (byte)0x00);
		commands = new LinkedList<BuilderCommand>();
	}

	public boolean isChunk(){return true;}

	public void setAddress(int addr){
		super.setAddress(addr);
		int pos = addr;
		for(BuilderCommand cmd : commands){
			cmd.setAddress(pos);
			pos += cmd.getSizeInBytes();
		}
	}
	
	public void addCommand(BuilderCommand cmd){
		commands.add(cmd);
	}
	
	public int getSizeInBytes() {
		int total = 0;
		for(BuilderCommand cmd : commands) total += cmd.getSizeInBytes();
		return total;
	}
	
	public List<BuilderCommand> getCommands(){
		List<BuilderCommand> copy = new ArrayList<BuilderCommand>(commands.size()+1);
		copy.addAll(commands);
		return copy;
	}

}
