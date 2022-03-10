package waffleoRai_SeqSound.jaiseq;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.LinkedList;
import java.util.List;

import waffleoRai_SeqSound.jaiseq.cmd.EJaiseqCmd;

public class JaiseqWriter {
	
	/*----- Constants -----*/
	
	/*----- Instance Variables -----*/
	
	private JaiseqCommand head;
	private boolean readdress = false;
	
	/*----- Init -----*/
	
	public JaiseqWriter(JaiseqCommand cmdhead){
		head = cmdhead;
	}
	
	/*----- Getters -----*/
	
	public boolean readdressOnLinearize(){return readdress;}
	
	/*----- Setters -----*/
	
	public void setReaddressOnLinearize(boolean b){readdress = b;}
	
	/*----- Serialization -----*/
	
	private boolean inList(List<JaiseqCommand> list, JaiseqCommand item){
		if(item == null || list == null) return false;
		for(JaiseqCommand cmd : list){
			if(cmd == item) return true;
		}
		return false;
	}
	
	public List<JaiseqCommand> linearize(){
		//TODO: It's writing some branches more than once?
		LinkedList<JaiseqCommand> outlist = new LinkedList<JaiseqCommand>();
		LinkedList<JaiseqCommand> branches = new LinkedList<JaiseqCommand>();
		LinkedList<JaiseqCommand> newtracks = new LinkedList<JaiseqCommand>();
		
		JaiseqCommand cmd = head, ref = null;
		int addr = 0;
		int track = -1;
		while(cmd != null){
			if(readdress){cmd.setAddress(addr); addr += cmd.getSize();}
			outlist.add(cmd);
			cmd.setTrack(track);
			
			ref = cmd.getReferencedCommand();
			if(ref != null){
				//Make sure ref isn't already added
				boolean addref = true;
				for(JaiseqCommand other : outlist){
					if(other == ref){addref = false; break;}
				}
				
				if(addref){
					if(cmd.getCommandEnum() == EJaiseqCmd.OPEN_TRACK){
						newtracks.add(ref);
						ref.setTrack(cmd.getArg(0));
					}
					else{
						branches.add(ref);
					}	
				}
			}
			
			cmd = cmd.getNextCommand();
			if(cmd == null){
				//Check branch lists.
				if(!branches.isEmpty()){
					cmd = branches.pop();
					while(cmd != null && inList(outlist,cmd)){
						if(!branches.isEmpty()) cmd = branches.pop();
						else cmd = null;
					}
				}
				
				if(cmd == null && !newtracks.isEmpty()){
					cmd = newtracks.pop();
					track = cmd.getTrack();
				}
			}
		}
		
		return outlist;
	}
	
	public void writeBinary(OutputStream out) throws IOException{
		if(out == null || head == null) return;
		List<JaiseqCommand> clist = linearize();
		for(JaiseqCommand cmd : clist){
			out.write(cmd.serializeMe());
		}
	}
	
	public void writeMML(Writer out) throws IOException{
		if(out == null || head == null) return;
		out.write("; Nintendo Gamecube/Revolution Jaiseq MML\n\n");
		
		List<JaiseqCommand> clist = linearize();
		for(JaiseqCommand cmd : clist){
			String lbl = cmd.getLabel();
			if(lbl != null) out.write(lbl + ":\n");
			out.write("\t");
			out.write(cmd.toMML());
			out.write("\n");
		}
	}

}
