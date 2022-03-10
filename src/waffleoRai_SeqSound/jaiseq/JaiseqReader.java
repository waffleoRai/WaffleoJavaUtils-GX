package waffleoRai_SeqSound.jaiseq;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_SeqSound.MIDI;
import waffleoRai_SeqSound.jaiseq.cmd.DummyJaiseqCommand;
import waffleoRai_SeqSound.jaiseq.cmd.EJaiseqCmd;
import waffleoRai_SeqSound.jaiseq.cmd.JaiseqCommands.*;

public class JaiseqReader {
	
	/*----- Constants -----*/
	
	/*----- Instance Variables -----*/
	
	private FileBuffer seqdata;
	
	private Map<Integer, JaiseqCommand> addr_map;
	private JaiseqCommand head;
	
	/*----- Init -----*/
	
	private JaiseqReader(){
		addr_map = new HashMap<Integer, JaiseqCommand>();
	}
	
	public static JaiseqReader readJaiseq(FileBuffer data){
		JaiseqReader jrdr = new JaiseqReader();
		jrdr.seqdata = data;
		if(!jrdr.preParse()) return null;
		
		return jrdr;
	}
	
	public static JaiseqReader readJaiseqMML(Reader input){
		//TODO
		return null;
	}
	
	/*----- Getters -----*/
	
	public JaiseqCommand getCommandAt(int addr){
		return addr_map.get(addr);
	}
	
	public JaiseqCommand getCommandTreeHead(){
		return head;
	}
	
	public List<JaiseqCommand> getOrderedCommands(){
		List<JaiseqCommand> cmdlist = new ArrayList<JaiseqCommand>(addr_map.size()+1);
		List<Integer> addrlist = new ArrayList<Integer>(addr_map.size()+1);
		addrlist.addAll(addr_map.keySet());
		Collections.sort(addrlist);
		for(Integer addr : addrlist){
			cmdlist.add(addr_map.get(addr));
		}
		return cmdlist;
	}
	
	/*----- Setters -----*/
	
	/*----- Parsing -----*/
	
	public boolean preParse(){
		addr_map.clear();
		head = null;
		
		JaiseqCommand cmd = null, prev = null;
		LinkedList<JaiseqCommand> branches = new LinkedList<JaiseqCommand>();
		long nowpos = 0, targaddr = 0;
		long maxpos = seqdata.getFileSize();
		boolean bbflag = false;
		EJaiseqCmd ecmd = null;
		
		//TODO: Are some subroutines getting lost?? Counter values in labels are WEIRD
		//For labels
		int[] subcount = new int[16];
		int[] loopcount = new int[16];
		int[] jumpcount = new int[16];
		int subc = 0;
		int loopc = 0;
		int jumpc = 0;
		
		while(true){
			//Check position to see if we should break or branch.
			bbflag = false;
			if(nowpos >= maxpos) bbflag = true;
			if(!bbflag && addr_map.containsKey((int)nowpos)) bbflag = true;
			if(!bbflag && (prev != null && prev.getCommandEnum() == EJaiseqCmd.END_TRACK)) bbflag = true;
			
			if(!bbflag && ((int)seqdata.getByte(nowpos) == 0)){
				//Check for padding
				if((nowpos + 2) >= maxpos) bbflag = true;
			}
			
			if(bbflag){
				//Break or branch
				if(branches.isEmpty()) break;
				prev = branches.pop();
				nowpos = prev.getAddress() + prev.getSize();
				continue;
			}
			
			cmd = readBinaryCommand(seqdata.getReferenceAt(nowpos));
			if(cmd == null){
				System.err.println("JaiseqReader.preParse || ERROR -- Invalid command found at 0x" + Long.toHexString(nowpos));
				return false;
			}
			
			cmd.setAddress((int)nowpos);
			addr_map.put(cmd.getAddress(), cmd);
			//System.err.println("added: 0x" + String.format("%06x ", cmd.getAddress()) + cmd.toString());
			if(prev != null){
				prev.setNextCommand(cmd);
				cmd.setTrack(prev.getTrack());
			}
			if(head == null){
				head = cmd;
				cmd.setLabel("begin_seq");
			}
			
			//Handle branching
			ecmd = cmd.getCommandEnum();
			prev = cmd;
			if(ecmd == EJaiseqCmd.OPEN_TRACK){
				targaddr = Integer.toUnsignedLong(prev.getArg(1));
				//See if already read.
				cmd = addr_map.get((int)targaddr);
				
				if(cmd == null){
					cmd = readBinaryCommand(seqdata.getReferenceAt(targaddr));
					if(cmd == null){
						System.err.println("JaiseqReader.preParse || ERROR -- Invalid command found at 0x" + Long.toHexString(targaddr));
						return false;
					}
					branches.add(cmd);
					cmd.setTrack(prev.getArg(0));
					cmd.setAddress((int)targaddr);
					addr_map.put(cmd.getAddress(), cmd);
					//System.err.println("added: 0x" + String.format("%06x ", cmd.getAddress()) + cmd.toString());
				}

				prev.setReferencedCommand(cmd);
				cmd.setLabel("begin_track" + String.format("%02d", cmd.getTrack()));
			}
			else if(ecmd == EJaiseqCmd.JUMP){
				targaddr = Integer.toUnsignedLong(prev.getArg(0));
				cmd = addr_map.get((int)targaddr);
				
				if(cmd == null){
					cmd = readBinaryCommand(seqdata.getReferenceAt(targaddr));
					if(cmd == null){
						System.err.println("JaiseqReader.preParse || ERROR -- Invalid command found at 0x" + Long.toHexString(targaddr));
						return false;
					}	
					branches.add(cmd);
					cmd.setAddress((int)targaddr);
					cmd.setTrack(prev.getTrack());
					addr_map.put(cmd.getAddress(), cmd);
					//System.err.println("added: 0x" + String.format("%06x ", cmd.getAddress()) + cmd.toString());
				}
				
				prev.setReferencedCommand(cmd);
				int t = cmd.getTrack();
				
				if(targaddr <= nowpos){
					if(t < 0){
						cmd.setLabel("loop_seq_" + String.format("%03d", loopc++));
					}
					else{
						cmd.setLabel("loop_track" + String.format("%02d_%03d", t, loopcount[t]++));
					}
				}
				else{
					if(t < 0){
						cmd.setLabel("jump_seq_" + String.format("%03d", jumpc++));
					}
					else{
						cmd.setLabel("jump_track" + String.format("%02d_%03d", t, jumpcount[t]++));
					}
				}
			}
			else if(ecmd == EJaiseqCmd.JUMP_COND){
				targaddr = Integer.toUnsignedLong(prev.getArg(1));
				//System.err.println("jump targ: 0x" + Long.toHexString(targaddr));
				//System.err.println("nowpos: 0x" + Long.toHexString(nowpos));
				cmd = addr_map.get((int)targaddr);
				
				if(cmd == null){
					cmd = readBinaryCommand(seqdata.getReferenceAt(targaddr));
					if(cmd == null){
						System.err.println("JaiseqReader.preParse || ERROR -- Invalid command found at 0x" + Long.toHexString(targaddr));
						return false;
					}	
					branches.add(cmd);
					cmd.setAddress((int)targaddr);
					cmd.setTrack(prev.getTrack());
					addr_map.put(cmd.getAddress(), cmd);
					//System.err.println("added: 0x" + String.format("%06x ", cmd.getAddress()) + cmd.toString());
				}
				
				prev.setReferencedCommand(cmd);
				int t = cmd.getTrack();
				
				if(targaddr <= nowpos){
					if(t < 0){
						cmd.setLabel("loop_seq_" + String.format("%03d", loopc++));
					}
					else{
						cmd.setLabel("loop_track" + String.format("%02d_%03d", t, loopcount[t]++));
					}
				}
				else{
					if(t < 0){
						cmd.setLabel("jump_seq_" + String.format("%03d", jumpc++));
					}
					else{
						cmd.setLabel("jump_track" + String.format("%02d_%03d", t, jumpcount[t]++));
					}
				}
			}
			else if(ecmd == EJaiseqCmd.CALL){
				targaddr = Integer.toUnsignedLong(prev.getArg(0));
				cmd = addr_map.get((int)targaddr);
				
				if(cmd == null){
					cmd = readBinaryCommand(seqdata.getReferenceAt(targaddr));
					if(cmd == null){
						System.err.println("JaiseqReader.preParse || ERROR -- Invalid command found at 0x" + Long.toHexString(targaddr));
						return false;
					}
					branches.add(cmd);
					cmd.setAddress((int)targaddr);
					cmd.setTrack(prev.getTrack());
					addr_map.put(cmd.getAddress(), cmd);
					//System.err.println("added: 0x" + String.format("%06x ", cmd.getAddress()) + cmd.toString());
				}
				
				prev.setReferencedCommand(cmd);
				int t = cmd.getTrack();
				
				if(t < 0){
					cmd.setLabel("sub_seq_" + String.format("%03d", subc++));
				}
				else{
					cmd.setLabel("sub_track" + String.format("%02d_%03d", t, subcount[t]++));
				}
			}
			else if(ecmd == EJaiseqCmd.CALL_COND){
				targaddr = Integer.toUnsignedLong(prev.getArg(1));
				cmd = addr_map.get((int)targaddr);
				
				if(cmd == null){
					cmd = readBinaryCommand(seqdata.getReferenceAt(targaddr));
					if(cmd == null){
						System.err.println("JaiseqReader.preParse || ERROR -- Invalid command found at 0x" + Long.toHexString(targaddr));
						return false;
					}
					branches.add(cmd);
					cmd.setAddress((int)targaddr);
					cmd.setTrack(prev.getTrack());
					addr_map.put(cmd.getAddress(), cmd);
					//System.err.println("added: 0x" + String.format("%06x ", cmd.getAddress()) + cmd.toString());
				}
				
				prev.setReferencedCommand(cmd);
				int t = cmd.getTrack();
				
				if(t < 0){
					cmd.setLabel("sub_seq_" + String.format("%03d", subc++));
				}
				else{
					cmd.setLabel("sub_track" + String.format("%02d_%03d", t, subcount[t]++));
				}
			}

			nowpos += prev.getSize();
		}
		
		return true;
	}
	
	public static JaiseqCommand readBinaryCommand(BufferReference ref){
		int status = Byte.toUnsignedInt(ref.getByte());
		
		JaiseqCommand cmd = null;
		if(status < 0x80){
			//Note on
			if(status == 0){
				//May be padding.
				long maxsz = ref.getBuffer().getFileSize();
				if(ref.getBufferPosition() >= (maxsz-2)) return null;
			}
			
			cmd = new JSC_NoteOn();
			cmd.args[0] = status;
			cmd.args[1] = Byte.toUnsignedInt(ref.getByte(1));
			cmd.args[2] = Byte.toUnsignedInt(ref.getByte(2));
		}
		else if(status == 0x80){
			//Wait (8-bit)
			cmd = new JSC_Wait(EJaiseqCmd.DELAY_8);
			cmd.args[0] = Byte.toUnsignedInt(ref.getByte(1));
		}
		else if(status > 0x80 && status < 0x88){
			//Note off
			cmd = new JSC_NoteOff();
			cmd.args[0] = status & 0xf;
		}
		else if(status == 0x88){
			//Wait (16-bit)
			cmd = new JSC_Wait(EJaiseqCmd.DELAY_16);
			cmd.args[0] = Short.toUnsignedInt(ref.getShort(1));
		}
		else if(status == 0x94){
			cmd = new JSC_SetPerf(EJaiseqCmd.PERF_U8_NODUR);
			cmd.args[0] = Byte.toUnsignedInt(ref.getByte(1));
			cmd.args[1] = Byte.toUnsignedInt(ref.getByte(2));
		}
		else if(status == 0x96){
			cmd = new JSC_SetPerf(EJaiseqCmd.PERF_U8_U8);
			cmd.args[0] = Byte.toUnsignedInt(ref.getByte(1));
			cmd.args[1] = Byte.toUnsignedInt(ref.getByte(2));
			cmd.args[2] = Byte.toUnsignedInt(ref.getByte(3));
		}
		else if(status == 0x97){
			cmd = new JSC_SetPerf(EJaiseqCmd.PERF_U8_U16);
			cmd.args[0] = Byte.toUnsignedInt(ref.getByte(1));
			cmd.args[1] = Byte.toUnsignedInt(ref.getByte(2));
			cmd.args[2] = Short.toUnsignedInt(ref.getShort(3));
		}
		else if(status == 0x98){
			cmd = new JSC_SetPerf(EJaiseqCmd.PERF_S8_NODUR);
			cmd.args[0] = Byte.toUnsignedInt(ref.getByte(1));
			cmd.args[1] = (int)ref.getByte(2);
		}
		else if(status == 0x9a){
			cmd = new JSC_SetPerf(EJaiseqCmd.PERF_S8_U8);
			cmd.args[0] = Byte.toUnsignedInt(ref.getByte(1));
			cmd.args[1] = (int)ref.getByte(2);
			cmd.args[2] = Byte.toUnsignedInt(ref.getByte(3));
		}
		else if(status == 0x9b){
			cmd = new JSC_SetPerf(EJaiseqCmd.PERF_S8_U16);
			cmd.args[0] = Byte.toUnsignedInt(ref.getByte(1));
			cmd.args[1] = (int)ref.getByte(2);
			cmd.args[2] = Short.toUnsignedInt(ref.getShort(3));
		}
		else if(status == 0x9c){
			cmd = new JSC_SetPerf(EJaiseqCmd.PERF_S16_NODUR);
			cmd.args[0] = Byte.toUnsignedInt(ref.getByte(1));
			cmd.args[1] = (int)ref.getShort(2);
		}
		else if(status == 0x9e){
			cmd = new JSC_SetPerf(EJaiseqCmd.PERF_S16_U8);
			cmd.args[0] = Byte.toUnsignedInt(ref.getByte(1));
			cmd.args[1] = (int)ref.getShort(2);
			cmd.args[2] = Byte.toUnsignedInt(ref.getByte(4));
		}
		else if(status == 0x9f){
			cmd = new JSC_SetPerf(EJaiseqCmd.PERF_S16_U16);
			cmd.args[0] = Byte.toUnsignedInt(ref.getByte(1));
			cmd.args[1] = (int)ref.getShort(2);
			cmd.args[2] = Short.toUnsignedInt(ref.getShort(4));
		}
		else if(status == 0xa4){
			cmd = new JSC_SetParam(EJaiseqCmd.PARAM_8);
			cmd.args[0] = Byte.toUnsignedInt(ref.getByte(1));
			cmd.args[1] = Byte.toUnsignedInt(ref.getByte(2));
		}
		else if(status == 0xac){
			cmd = new JSC_SetParam(EJaiseqCmd.PARAM_16);
			cmd.args[0] = Byte.toUnsignedInt(ref.getByte(1));
			cmd.args[1] = Short.toUnsignedInt(ref.getShort(2));
		}
		else if(status == 0xb8){
			cmd = new JSC_SetPerf(EJaiseqCmd.PERFRVL_S8);
			cmd.args[0] = Byte.toUnsignedInt(ref.getByte(1));
			cmd.args[1] = (int)ref.getByte(2);
		}
		else if(status == 0xb9){
			cmd = new JSC_SetPerf(EJaiseqCmd.PERFRVL_S16);
			cmd.args[0] = Byte.toUnsignedInt(ref.getByte(1));
			cmd.args[1] = (int)ref.getShort(2);
		}
		else if(status == 0xc1){
			cmd = new JSC_OpenTrack();
			cmd.args[0] = Byte.toUnsignedInt(ref.getByte(1));
			cmd.args[1] = ref.get24Bits(2);
		}
		else if(status == 0xc2){
			cmd = new DummyJaiseqCommand(EJaiseqCmd.OPEN_TRACK_SIB);
		}
		else if(status == 0xc3){
			cmd = new JSC_Call(false);
			cmd.args[0] = ref.get24Bits(1);
		}
		else if(status == 0xc4){
			cmd = new JSC_Call(true);
			cmd.args[0] = Byte.toUnsignedInt(ref.getByte(1));
			cmd.args[1] = ref.get24Bits(2);
		}
		else if(status == 0xc5){
			cmd = new JSC_Return(false);
		}
		else if(status == 0xc6){
			cmd = new JSC_Return(true);
			cmd.args[0] = Byte.toUnsignedInt(ref.getByte(1));
		}
		else if(status == 0xc7){
			cmd = new JSC_Jump(false);
			cmd.args[0] = ref.get24Bits(1);
		}
		else if(status == 0xc8){
			cmd = new JSC_Jump(true);
			cmd.args[0] = Byte.toUnsignedInt(ref.getByte(1));
			cmd.args[1] = ref.get24Bits(2);
		}
		else if(status == 0xcb){
			cmd = new DummyJaiseqCommand(EJaiseqCmd.UNK_CB);
			cmd.args[0] = (int)ref.getShort(1);
		}
		else if(status == 0xd8){
			cmd = new JSC_SetArtic(EJaiseqCmd.PARAM_16);
			cmd.args[0] = Byte.toUnsignedInt(ref.getByte(1));
			cmd.args[1] = Short.toUnsignedInt(ref.getShort(2));
		}
		else if(status == 0xe0){
			cmd = new JSC_Tempo(true);
			cmd.args[0] = Short.toUnsignedInt(ref.getShort(1));
		}
		else if(status == 0xe2){
			cmd = new JSC_SetBank();
			cmd.args[0] = Byte.toUnsignedInt(ref.getByte(1));
		}
		else if(status == 0xe3){
			cmd = new JSC_SetProgram();
			cmd.args[0] = Byte.toUnsignedInt(ref.getByte(1));
		}
		else if(status == 0xe6){
			cmd = new JSC_VibWidth();
			cmd.args[0] = (int)ref.getShort(1);
		}
		else if(status == 0xe7){
			cmd = new DummyJaiseqCommand(EJaiseqCmd.VSYNC);
			cmd.args[0] = (int)ref.getShort(1);
		}
		else if(status == 0xf0){
			cmd = new JSC_Wait(EJaiseqCmd.DELAY);
			int[] vlq = MIDI.getVLQ(ref.getBuffer(), ref.getBufferPosition()+1L);
			cmd.args[0] = vlq[0];
		}
		else if(status == 0xf4){
			cmd = new JSC_VibDepth();
			cmd.args[0] = (int)ref.getByte(1);
		}
		else if(status == 0xf9){
			cmd = new DummyJaiseqCommand(EJaiseqCmd.UNK_F9);
			cmd.args[0] = (int)ref.getShort(1);
		}
		else if(status == 0xfd){
			//cmd = new JSC_Timebase();
			cmd = new JSC_Tempo();
			cmd.args[0] = Short.toUnsignedInt(ref.getShort(1));
		}
		else if(status == 0xfe){
			//cmd = new JSC_Tempo();
			cmd = new JSC_Timebase();
			//cmd = new DummyJaiseqCommand(EJaiseqCmd.TIMEBASE);
			cmd.args[0] = Short.toUnsignedInt(ref.getShort(1));
		}
		else if(status == 0xff){
			cmd = new JSC_EndTrack();
		}
		
		return cmd;
	}
	
	/*----- Serialization -----*/

}
