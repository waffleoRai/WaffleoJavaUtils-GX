package waffleoRai_SeqSound.n64al.seqgen;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import waffleoRai_SeqSound.n64al.NUSALSeqCmdType;
import waffleoRai_Utils.FileBuffer;

class CommandChunk extends BuilderCommand{

	private LinkedList<BuilderCommand> commands;
	
	public CommandChunk() {
		super(NUSALSeqCmdType.MULTI_EVENT_CHUNK, (byte)0x00);
		commands = new LinkedList<BuilderCommand>();
	}

	public boolean isChunk(){return true;}
	
	public boolean isEmpty(){return commands.isEmpty();}

	public void setAddress(int addr){
		super.setAddress(addr);
		int pos = addr;
		for(BuilderCommand cmd : commands){
			cmd.setAddress(pos);
			pos += cmd.getSizeInBytes();
		}
	}
	
	public void updateParameters(){
		for(BuilderCommand cmd : commands){
			cmd.updateParameters();
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
	
	public int getSizeInTicks(){
		int total = 0;
		for(BuilderCommand cmd : commands) total += cmd.getSizeInTicks();
		return total;
	}
	
	public void limitTicksTo(int ticks){
		if(ticks < 1) return;
		//Shave from end commands
		int remaining = ticks;
		LinkedList<BuilderCommand> limbo = new LinkedList<BuilderCommand>();
		BuilderCommand back = null;
		while(remaining > 0 && !commands.isEmpty()){
			//Pop off any length 0 commands and stick in limbo
			back = commands.removeLast();
			while(back.getSizeInTicks() <= 0 && !commands.isEmpty()){
				limbo.push(back);
				back = commands.removeLast();
			}
			int clen = back.getSizeInTicks();
			if(commands.isEmpty() && clen <= 0) break; //No timed commands left, nothing to work with.
			//At first time length command...			
			//Check to see if smaller than remaining. If so delete it and all in limbo.
			limbo.clear();
			if(clen <= remaining){
				//Just gonna get chopped along with everything after it
				remaining -= clen;
			}
			else{
				//Otherwise, just shorten it (and delete all in limbo that come after)
				back.limitTicksTo(clen-remaining);
				remaining = 0;
				commands.add(back);
			}
		}
		//If anything is in limbo, put it back.
		while(!limbo.isEmpty()) commands.addLast(limbo.pop());
	}
	
	public List<BuilderCommand> getCommands(){
		List<BuilderCommand> copy = new ArrayList<BuilderCommand>(commands.size()+1);
		copy.addAll(commands);
		return copy;
	}
	
	public int serializeTo(OutputStream stream) throws IOException{
		int total = 0;
		for(BuilderCommand cmd : commands){
			total += cmd.serializeTo(stream);
		}
		return total;
	}
	
	public int serializeTo(FileBuffer buffer){
		int total = 0;
		for(BuilderCommand cmd : commands){
			total += cmd.serializeTo(buffer);
		}
		return total;
	}

}
