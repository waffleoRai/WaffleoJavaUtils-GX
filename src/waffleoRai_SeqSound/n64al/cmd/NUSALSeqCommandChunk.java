package waffleoRai_SeqSound.n64al.cmd;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import waffleoRai_SeqSound.n64al.NUSALSeqCmdType;
import waffleoRai_SeqSound.n64al.NUSALSeqCommand;
import waffleoRai_Utils.FileBuffer;

public class NUSALSeqCommandChunk extends NUSALSeqCommand{

	private LinkedList<NUSALSeqCommand> commands;
	
	public NUSALSeqCommandChunk() {
		super(NUSALSeqCmdType.MULTI_EVENT_CHUNK, (byte)0x00);
		commands = new LinkedList<NUSALSeqCommand>();
	}

	public boolean isChunk(){return true;}
	public boolean isEmpty(){return commands.isEmpty();}

	public void setAddress(int addr){
		super.setAddress(addr);
		int pos = addr;
		for(NUSALSeqCommand cmd : commands){
			cmd.setAddress(pos);
			pos += cmd.getSizeInBytes();
		}
	}

	public void addCommand(NUSALSeqCommand cmd){
		commands.add(cmd);
	}
	
	public int getSizeInBytes() {
		int total = 0;
		for(NUSALSeqCommand cmd : commands) total += cmd.getSizeInBytes();
		return total;
	}
	
	public int getSizeInTicks(){
		int total = 0;
		for(NUSALSeqCommand cmd : commands) total += cmd.getSizeInTicks();
		return total;
	}
		
	public List<NUSALSeqCommand> getCommands(){
		List<NUSALSeqCommand> copy = new ArrayList<NUSALSeqCommand>(commands.size()+1);
		copy.addAll(commands);
		return copy;
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
