package waffleoRai_SeqSound.n64al;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import waffleoRai_Files.WriterPrintable;
import waffleoRai_SeqSound.n64al.cmd.BasicCommandMap;
import waffleoRai_SeqSound.n64al.cmd.CMD_IgnoredCommand;
import waffleoRai_Utils.FileBuffer;

public class NUSALSeqParser implements WriterPrintable{

	private FileBuffer data;
	
	private int seq_pos;
	//private int[][] ch_pos;
	
	//private NUSALSeqCommand last_ch_cmd;
	private Map<Integer, NUSALSeqCommand> cmdmap;
	
	public NUSALSeqParser(FileBuffer dat){
		data = dat;
		seq_pos = 0;
		/*ch_pos = new int[16][5];
		for(int i = 0; i < 16; i++){
			for(int j = 0; j < 5; j++) ch_pos[i][j] = -1;
		}*/
		cmdmap = new TreeMap<Integer, NUSALSeqCommand>();
	}
	
	public void reset(){
		seq_pos = 0;
		/*for(int i = 0; i < 16; i++){
			for(int j = 0; j < 5; j++) ch_pos[i][j] = -1;
		}*/
		cmdmap.clear();
	}
	
	private int[] readVLQ(int pos){
		//Know how to read length, but not sure how value scales.
		int[] out = new int[2];
		int b0 = Byte.toUnsignedInt(data.getByte(pos));
		if((b0 & 0x80) != 0){
			out[1] = 2;
			out[0] = (b0 & 0x7F) << 8;
			int b1 = Byte.toUnsignedInt(data.getByte(pos+1));
			out[0] |= b1;
		}
		else{
			out[0] = b0;
			out[1] = 1;
		}
		
		return out;
	}
	
	private NUSALSeqCommand readSeqCommand(int pos){
		byte cmdb = data.getByte(pos);
		int cmdi = Byte.toUnsignedInt(cmdb);
		int hi = (cmdi >>> 4) & 0xf;
		int lo = cmdi & 0xf;
		
		switch(hi){
			case 0x0:
				//Set seq var to ch var
				//No parameters (besides channel)
				return new NUSALSeqCommand(NUSALSeqCmdType.LOAD_CH_VAR, cmdb){

					protected void onInit(){super.setParam(0, lo);}
					public int getSizeInBytes() {return 1;}
					
					public boolean doCommand(NUSALSeq sequence){
						if(sequence == null) return false;
						NUSALSeqChannel chan = sequence.getChannel(super.getParam(0));
						if(chan == null) return false;
						sequence.setSeqVar(chan.getVar());
						flagSeqUsed();
						return true;
					}
				};
			case 0x1:
				//0x10 has no params and is unknown
				//Ignore others
				return new CMD_IgnoredCommand(NUSALSeqCmdType.SEQ_UNK_10, cmdb, 1);
			case 0x2:
			case 0x3:
				//Unknown
				break;
			case 0x4:
				//Some kind of channel control, but unknown
				return new CMD_IgnoredCommand(NUSALSeqCmdType.SEQ_UNK_4x, cmdb, 1);
			case 0x5:
				//seq var -= ch var
				return new NUSALSeqCommand(NUSALSeqCmdType.SUBTRACT_CH, cmdb){

					protected void onInit(){super.setParam(0, lo);}
					public int getSizeInBytes() {return 1;}
					
					public boolean doCommand(NUSALSeq sequence){
						if(sequence == null) return false;
						NUSALSeqChannel chan = sequence.getChannel(super.getParam(0));
						if(chan == null) return false;
						sequence.setSeqVar(sequence.getSeqVar() - chan.getVar());
						flagSeqUsed();
						return true;
					}
				};
			case 0x6:
				//Channel op unknown, but it has 3 params including channel
				//seq64 reckons it has something to do with a DMA/channel var transfer?
				return new CMD_IgnoredCommand(NUSALSeqCmdType.LOAD_CH_DMA, cmdb, 3);
			case 0x7:
				//Set ch var
				return new NUSALSeqCommand(NUSALSeqCmdType.SET_CH_VAR, cmdb){

					protected void onInit(){super.setParam(0, lo);}
					public int getSizeInBytes() {return 1;}
					
					public boolean doCommand(NUSALSeq sequence){
						if(sequence == null) return false;
						NUSALSeqChannel chan = sequence.getChannel(super.getParam(0));
						if(chan == null) return false;
						chan.setVar(sequence.getSeqVar());
						flagSeqUsed();
						return true;
					}
				};
			case 0x8:
				//Sets seq var to ch var, and if ch < 2, sets ch var to -1?
				return new NUSALSeqCommand(NUSALSeqCmdType.SEQ_UNK_8x, cmdb){

					protected void onInit(){super.setParam(0, lo);}
					public int getSizeInBytes() {return 1;}
					
					public boolean doCommand(NUSALSeq sequence){
						if(sequence == null) return false;
						NUSALSeqChannel chan = sequence.getChannel(super.getParam(0));
						if(chan == null) return false;
						sequence.setSeqVar(chan.getVar());
						if(super.getParam(0) < 2) chan.setVar(-1);
						flagSeqUsed();
						return true;
					}
				};
			case 0x9:
				//Specify channel start (abs addr)
				return new NUSALSeqCommand(NUSALSeqCmdType.CHANNEL_OFFSET, cmdb){

					protected void onInit(){super.setParam(0, lo); 
					super.setParam(1, Short.toUnsignedInt(data.shortFromFile(pos+1)));}
					
					public int getSizeInBytes() {return 3;}
					
					public boolean doCommand(NUSALSeq sequence){
						if(sequence == null) return false;
						flagSeqUsed();
						return sequence.pointChannelTo(super.getParam(0), super.getParam(1));
					}
					
					protected String paramsToString(){
						return super.getParam(0) + " 0x" + String.format("%04x", super.getParam(1)); 
					}
				};
			case 0xa:
				//Specify channel start (rel addr)
				return new NUSALSeqCommand(NUSALSeqCmdType.CHANNEL_OFFSET_REL, cmdb){

					protected void onInit(){super.setParam(0, lo); 
					super.setParam(1, Short.toUnsignedInt(data.shortFromFile(pos+1)));}
					
					public int getSizeInBytes() {return 3;}
					
					public boolean doCommand(NUSALSeq sequence){
						if(sequence == null) return false;
						flagSeqUsed();
						return sequence.pointChannelTo(super.getParam(0), super.getParam(1) + sequence.getCurrentPosition());
					}
					protected String paramsToString(){
						return super.getParam(0) + " 0x" + String.format("%04x", super.getParam(1)); 
					}
				};
			case 0xb:
				//"New sequence from" using channel??? 
				return new CMD_IgnoredCommand(NUSALSeqCmdType.NEW_SEQ_FROM, cmdb, 4);
			case 0xc:
				switch(lo){
				case 0x4:
					return new CMD_IgnoredCommand(NUSALSeqCmdType.SEQ_UNK_C4, cmdb, 3);
				case 0x5:
					return new CMD_IgnoredCommand(NUSALSeqCmdType.SEQ_UNK_C5, cmdb, 3);
				case 0x6:
					return new CMD_IgnoredCommand(NUSALSeqCmdType.RETURN_FROM_SEQ, cmdb, 1);
				case 0x7:
					return new CMD_IgnoredCommand(NUSALSeqCmdType.SEQ_UNK_C7, cmdb, 4);
				case 0x8:
					return new NUSALSeqCommand(NUSALSeqCmdType.SUBTRACT_IMM, cmdb){

						protected void onInit(){
							super.setParam(0, (int)data.getByte(pos+1));}
						
						public int getSizeInBytes() {return 2;}
						
						public boolean doCommand(NUSALSeq sequence){
							if(sequence == null) return false;
							int var = sequence.getSeqVar();
							var -= super.getParam(0);
							sequence.setSeqVar(var);
							flagSeqUsed();
							return true;
						}
					};
				case 0x9:
					return new NUSALSeqCommand(NUSALSeqCmdType.AND_IMM, cmdb){

						protected void onInit(){
							super.setParam(0, Byte.toUnsignedInt(data.getByte(pos+1)));}
						
						public int getSizeInBytes() {return 2;}
						
						public boolean doCommand(NUSALSeq sequence){
							if(sequence == null) return false;
							int var = sequence.getSeqVar();
							var &= super.getParam(0);
							sequence.setSeqVar(var);
							flagSeqUsed();
							return true;
						}
						protected String paramsToString(){
							return "0x" + String.format("%02x", super.getParam(0)); 
						}
					};
				case 0xa:
					return new CMD_IgnoredCommand(NUSALSeqCmdType.SEQ_UNK_CA, cmdb, 1);
				case 0xb:
					return new CMD_IgnoredCommand(NUSALSeqCmdType.SEQ_UNK_CB, cmdb, 1);
				case 0xc:
					return new NUSALSeqCommand(NUSALSeqCmdType.SET_VAR, cmdb){

						protected void onInit(){ 
							super.setParam(0, (int)data.getByte(pos+1));}
						
						public int getSizeInBytes() {return 2;}
						
						public boolean doCommand(NUSALSeq sequence){
							if(sequence == null) return false;
							sequence.setSeqVar(super.getParam(0));
							flagSeqUsed();
							return true;
						}
					};
				}
				break;
			case 0xd:
				switch(lo){
				case 0x3:
					return new NUSALSeqCommand(NUSALSeqCmdType.SET_FORMAT, cmdb){
						protected void onInit(){
							super.setParam(0, Byte.toUnsignedInt(data.getByte(pos+1)));}
						public int getSizeInBytes() {return 2;}
						public boolean doCommand(NUSALSeq sequence){
							if(sequence == null) return false;
							sequence.setFormat((byte)super.getParam(0));
							flagSeqUsed();
							return true;
						}
						protected String paramsToString(){
							return "0x" + String.format("%02x", super.getParam(0)); 
						}
					};
				case 0x5:
					return new NUSALSeqCommand(NUSALSeqCmdType.SET_TYPE, cmdb){
						protected void onInit(){
							super.setParam(0, Byte.toUnsignedInt(data.getByte(pos+1)));}
						public int getSizeInBytes() {return 2;}
						public boolean doCommand(NUSALSeq sequence){
							if(sequence == null) return false;
							sequence.setType((byte)super.getParam(0));
							flagSeqUsed();
							return true;
						}
						protected String paramsToString(){
							return "0x" + String.format("%02x", super.getParam(0)); 
						}
					};
				case 0x6:
					return new NUSALSeqCommand(NUSALSeqCmdType.DISABLE_CHANNELS, cmdb){
						protected void onInit(){
							super.setParam(0, Short.toUnsignedInt(data.shortFromFile(pos+1)));}
						public int getSizeInBytes() {return 3;}
						public boolean doCommand(NUSALSeq sequence){
							if(sequence == null) return false;
							int mask = 1; int param = super.getParam(0);
							for(int i = 0; i < 16; i++){
								if((mask & param) != 0){
									sequence.setChannelEnabled(i, false);
								}
								mask <<= 1;
							}
							flagSeqUsed();
							return true;
						}
						protected String paramsToString(){
							return "0x" + String.format("%04x", super.getParam(0)); 
						}
					};
				case 0x7:
					return new NUSALSeqCommand(NUSALSeqCmdType.ENABLE_CHANNELS, cmdb){
						protected void onInit(){
							super.setParam(0, Short.toUnsignedInt(data.shortFromFile(pos+1)));}
						public int getSizeInBytes() {return 3;}
						public boolean doCommand(NUSALSeq sequence){
							if(sequence == null) return false;
							int mask = 1; int param = super.getParam(0);
							for(int i = 0; i < 16; i++){
								if((mask & param) != 0){
									sequence.setChannelEnabled(i, true);
								}
								mask <<= 1;
							}
							flagSeqUsed();
							return true;
						}
						protected String paramsToString(){
							return "0x" + String.format("%04x", super.getParam(0)); 
						}
					};
				case 0xb:
					return new NUSALSeqCommand(NUSALSeqCmdType.MASTER_VOLUME, cmdb){
						protected void onInit(){
							super.setParam(0, Byte.toUnsignedInt(data.getByte(pos+1)));}
						public int getSizeInBytes() {return 2;}
						public boolean doCommand(NUSALSeq sequence){
							if(sequence == null) return false;
							sequence.setMasterVolume((byte)super.getParam(0));
							flagSeqUsed();
							return true;
						}
					};
				case 0xd:
					return new NUSALSeqCommand(NUSALSeqCmdType.SET_TEMPO, cmdb){
						protected void onInit(){
							super.setParam(0, Byte.toUnsignedInt(data.getByte(pos+1)));}
						public int getSizeInBytes() {return 2;}
						public boolean doCommand(NUSALSeq sequence){
							if(sequence == null) return false;
							sequence.setTempo(super.getParam(0));
							flagSeqUsed();
							return true;
						}
						protected String paramsToString(){
							return super.getParam(0) + "bpm"; 
						}
					};
				case 0xf:
					return new CMD_IgnoredCommand(NUSALSeqCmdType.SEQ_UNK_DF, cmdb, 2);
				}
				break;
			case 0xe:
				switch(lo){
				case 0xf:
					return new CMD_IgnoredCommand(NUSALSeqCmdType.SEQ_UNK_EF, cmdb, 4);
				}
				break;
			case 0xf:
				switch(lo){
				case 0x2:
					return new NUSALSeqCommand(NUSALSeqCmdType.BRANCH_IF_LTZ_REL, cmdb){
						protected void onInit(){
							super.setParam(0, (int)data.getByte(pos+1));}
						public int getSizeInBytes() {return 2;}
						public boolean doCommand(NUSALSeq sequence){
							if(sequence == null) return false;
							if(sequence.getSeqVar() < 0){
								int nowpos = sequence.getCurrentPosition();
								sequence.jumpTo(nowpos + getBranchAddress(), false);	
							}
							flagSeqUsed();
							return true;
						}
						public boolean isBranch(){return true;};
						public boolean isRelativeBranch(){return true;}
						public int getBranchAddress(){return super.getParam(0)-1;}
						protected String paramsToString(){
							return String.format("0x%02x", super.getParam(0));  
						}
					};
				case 0x3:
					return new NUSALSeqCommand(NUSALSeqCmdType.BRANCH_IF_EQZ_REL, cmdb){
						protected void onInit(){
							super.setParam(0, (int)data.getByte(pos+1));}
						public int getSizeInBytes() {return 2;}
						public boolean doCommand(NUSALSeq sequence){
							if(sequence == null) return false;
							if(sequence.getSeqVar() == 0){
								int nowpos = sequence.getCurrentPosition();
								sequence.jumpTo(nowpos + getBranchAddress(), false);	
							}
							flagSeqUsed();
							return true;
						}
						public boolean isBranch(){return true;}
						public boolean isRelativeBranch(){return true;}
						public int getBranchAddress(){return super.getParam(0)-1;}
						protected String paramsToString(){
							return String.format("0x%02x", super.getParam(0)); 
						}
					};
				case 0x4:
					return new NUSALSeqCommand(NUSALSeqCmdType.BRANCH_ALWAYS_REL, cmdb){
						protected void onInit(){
							super.setParam(0, (int)data.getByte(pos+1));}
						public int getSizeInBytes() {return 2;}
						public boolean doCommand(NUSALSeq sequence){
							if(sequence == null) return false;
							int nowpos = sequence.getCurrentPosition();
							sequence.jumpTo(nowpos + getBranchAddress(), false);	
							flagSeqUsed();
							return true;
						}
						public boolean isBranch(){return true;}
						public boolean isRelativeBranch(){return true;}
						public int getBranchAddress(){return super.getParam(0)-1;}
						protected String paramsToString(){
							return String.format("0x%02x", super.getParam(0)); 
						}
					};
				case 0x5:
					return new NUSALSeqCommand(NUSALSeqCmdType.BRANCH_IF_GTEZ, cmdb){
						protected void onInit(){
							super.setParam(0, Short.toUnsignedInt(data.shortFromFile(pos+1)));}
						public int getSizeInBytes() {return 3;}
						public boolean doCommand(NUSALSeq sequence){
							if(sequence == null) return false;
							if(sequence.getSeqVar() >= 0){
								sequence.jumpTo(super.getParam(0), false);	
							}
							flagSeqUsed();
							return true;
						}
						public boolean isBranch(){return true;};
						public int getBranchAddress(){return super.getParam(0);}
						protected String paramsToString(){
							return String.format("0x%04x", super.getParam(0)); 
						}
					};
				case 0x6:
					return new NUSALSeqCommand(NUSALSeqCmdType.BRANCH_TO_SEQSTART, cmdb){
						public int getSizeInBytes() {return 1;}
						public boolean doCommand(NUSALSeq sequence){
							if(sequence == null) return false;
							sequence.jumpTo(0, false);
							flagSeqUsed();
							return true;
						}
						public boolean isBranch(){return true;};
						public int getBranchAddress(){return 0;}
					};
				case 0x7:
					return new NUSALSeqCommand(NUSALSeqCmdType.LOOP_END, cmdb){
						public int getSizeInBytes() {return 1;}
						public boolean doCommand(NUSALSeq sequence){
							if(sequence == null) return false;
							sequence.signalLoopEnd();
							flagSeqUsed();
							return true;
						}
					};
				case 0x8:
					return new NUSALSeqCommand(NUSALSeqCmdType.LOOP_START, cmdb){
						protected void onInit(){
							super.setParam(0, Byte.toUnsignedInt(data.getByte(pos+1)));}
						public int getSizeInBytes() {return 2;}
						public boolean doCommand(NUSALSeq sequence){
							if(sequence == null) return false;
							sequence.signalLoopStart(super.getParam(0));
							flagSeqUsed();
							return true;
						}
					};
				case 0x9:
					return new NUSALSeqCommand(NUSALSeqCmdType.BRANCH_IF_LTZ, cmdb){
						protected void onInit(){
							super.setParam(0, Short.toUnsignedInt(data.shortFromFile(pos+1)));}
						public int getSizeInBytes() {return 3;}
						public boolean doCommand(NUSALSeq sequence){
							if(sequence == null) return false;
							if(sequence.getSeqVar() < 0){
								sequence.jumpTo(super.getParam(0), false);	
							}
							flagSeqUsed();
							return true;
						}
						public boolean isBranch(){return true;};
						public int getBranchAddress(){return super.getParam(0);}
						protected String paramsToString(){
							return String.format("0x%04x", super.getParam(0)); 
						}
					};
				case 0xa:
					return new NUSALSeqCommand(NUSALSeqCmdType.BRANCH_IF_EQZ, cmdb){
						protected void onInit(){
							super.setParam(0, Short.toUnsignedInt(data.shortFromFile(pos+1)));}
						public int getSizeInBytes() {return 3;}
						public boolean doCommand(NUSALSeq sequence){
							if(sequence == null) return false;
							if(sequence.getSeqVar() == 0){
								sequence.jumpTo(super.getParam(0), false);	
							}
							flagSeqUsed();
							return true;
						}
						public boolean isBranch(){return true;};
						public int getBranchAddress(){return super.getParam(0);}
						protected String paramsToString(){
							return String.format("0x%04x", super.getParam(0)); 
						}
					};
				case 0xb:
					return new NUSALSeqCommand(NUSALSeqCmdType.BRANCH_ALWAYS, cmdb){
						protected void onInit(){
							super.setParam(0, Short.toUnsignedInt(data.shortFromFile(pos+1)));}
						public int getSizeInBytes() {return 3;}
						public boolean doCommand(NUSALSeq sequence){
							if(sequence == null) return false;
							sequence.jumpTo(super.getParam(0), false);	
							flagSeqUsed();
							return true;
						}
						public boolean isBranch(){return true;};
						public int getBranchAddress(){return super.getParam(0);}
						protected String paramsToString(){
							return String.format("0x%04x", super.getParam(0)); 
						}
					};
				case 0xc:
					return new NUSALSeqCommand(NUSALSeqCmdType.CALL, cmdb){
						protected void onInit(){
							super.setParam(0, Short.toUnsignedInt(data.shortFromFile(pos+1)));}
						public int getSizeInBytes() {return 3;}
						public boolean doCommand(NUSALSeq sequence){
							if(sequence == null) return false;
							sequence.jumpTo(super.getParam(0), true);	
							flagSeqUsed();
							return true;
						}
						public boolean isBranch(){return true;};
						public int getBranchAddress(){return super.getParam(0);}
						protected String paramsToString(){
							return String.format("0x%04x", super.getParam(0)); 
						}
					};
				case 0xd:
					return new NUSALSeqCommand(NUSALSeqCmdType.WAIT, cmdb){
						private int bcount;
						protected void onInit(){
							int[] vlq = readVLQ(pos+1);
							super.setParam(0, vlq[0]);
							bcount = vlq[1]+1;
						}
						public int getSizeInBytes() {return bcount;}
						public boolean doCommand(NUSALSeq sequence){
							if(sequence == null) return false;	
							flagSeqUsed();
							return sequence.setSeqWait(super.getParam(0));
						}
					};
				case 0xe:
					return new NUSALSeqCommand(NUSALSeqCmdType.RETURN, cmdb){
						public int getSizeInBytes() {return 1;}
						public boolean doCommand(NUSALSeq sequence){
							if(sequence == null) return false;
							sequence.returnFromCall();
							flagSeqUsed();
							return true;
						}
					};
				case 0xf:
					return new NUSALSeqCommand(NUSALSeqCmdType.END_READ, cmdb){
						public int getSizeInBytes() {return 1;}
						public boolean doCommand(NUSALSeq sequence){
							if(sequence == null) return false;
							sequence.signalSeqEnd();
							flagSeqUsed();
							return true;
						}
					};
				}
				break;
		}
		
		return null;
	}
	
	private NUSALSeqCommand readChCommand(int pos){
		byte cmdb = data.getByte(pos);
		int cmdi = Byte.toUnsignedInt(cmdb);
		int hi = (cmdi >>> 4) & 0xf;
		int lo = cmdi & 0xf;
		//System.err.println("debug -- cmdi = 0x" + String.format("%02x", cmdi));
		
		switch(hi){
		case 0x7: 
			if(lo >= 0x8 && lo <= 0xb){
				int layer = lo - 8;
				return new NUSALSeqCommand(NUSALSeqCmdType.VOICE_OFFSET_REL, cmdb){

					protected void onInit(){super.setParam(0, layer); 
					super.setParam(1, (int)data.shortFromFile(pos+1));}
					
					public int getSizeInBytes() {return 3;}
					
					public boolean doCommand(NUSALSeqChannel channel){
						if(channel == null) return false;
						super.flagChannelUsed(channel.getIndex());
						int pos = channel.getCurrentPosition();	
						return channel.pointLayerTo(super.getParam(0), 
								pos + super.getParam(1));
					}
					
					protected String paramsToString(){
						return super.getParam(0) + " 0x" + String.format("%04x", super.getParam(1)); 
					}
				};
			}
			break;
		case 0x8: 
			if(lo >= 0x8 && lo <= 0xb){
				int layer = lo - 8;
				return new NUSALSeqCommand(NUSALSeqCmdType.VOICE_OFFSET, cmdb){

					protected void onInit(){super.setParam(0, layer); 
					super.setParam(1, Short.toUnsignedInt(data.shortFromFile(pos+1)));}
					
					public int getSizeInBytes() {return 3;}
					
					public boolean doCommand(NUSALSeqChannel channel){
						if(channel == null) return false;
						super.flagChannelUsed(channel.getIndex());
						return channel.pointLayerTo(super.getParam(0), 
								super.getParam(1));
					}
					
					protected String paramsToString(){
						return super.getParam(0) + " 0x" + String.format("%04x", super.getParam(1)); 
					}
				};
			}
			break;
		case 0xc: 
			switch(lo){
			case 0x1:
				return new NUSALSeqCommand(NUSALSeqCmdType.SET_PROGRAM, cmdb){
					protected void onInit(){super.setParam(0, Byte.toUnsignedInt(data.getByte(pos+1)));}
					public int getSizeInBytes() {return 2;}
					public boolean doCommand(NUSALSeqChannel channel){
						if(channel == null) return false;
						super.flagChannelUsed(channel.getIndex());
						return channel.changeProgram(super.getParam(0));
					}
				};
			case 0x2:
				return new NUSALSeqCommand(NUSALSeqCmdType.TRANSPOSE, cmdb){
					protected void onInit(){super.setParam(0, (int)data.getByte(pos+1));}
					public int getSizeInBytes() {return 2;}
					public boolean doCommand(NUSALSeqChannel channel){
						if(channel == null) return false;
						super.flagChannelUsed(channel.getIndex());
						channel.setTranspose(super.getParam(0));
						return true;
					}
				};
			case 0x4:
				return new NUSALSeqCommand(NUSALSeqCmdType.INIT_CHANNEL, cmdb){
					public int getSizeInBytes() {return 1;}
					public boolean doCommand(NUSALSeqChannel channel){
						if(channel == null) return false;
						super.flagChannelUsed(channel.getIndex());
						channel.signalChannelInit();
						return true;
					}
				};
			}
			break;
		case 0xd: 
			switch(lo){
			case 0x3:
				return new NUSALSeqCommand(NUSALSeqCmdType.CH_PITCHBEND, cmdb){
					private boolean timeflag;
					protected void onInit(){
						super.setParam(0, (int)data.getByte(pos+1));
						int nb = Byte.toUnsignedInt(data.getByte(pos+2));
						if(nb < 0x78){
							//Set
							timeflag = true;
							super.setParam(1, nb);
						}
					}
					
					public int getSizeInBytes() {return timeflag?3:2;}
					
					public boolean doCommand(NUSALSeqChannel channel){
						if(channel == null) return false;
						super.flagChannelUsed(channel.getIndex());
						channel.setPitchbend((byte)super.getParam(0), super.getParam(1));
						return true;
					}
					
					protected String paramsToString(){
						String str = "0x" + String.format("%02x", super.getParam(0));
						if(super.getParam(1) > 0){
							str += " " + super.getParam(1);
						}
						return str;
					}
				};
			case 0x4:
				return new NUSALSeqCommand(NUSALSeqCmdType.CH_REVERB, cmdb){

					protected void onInit(){super.setParam(0, (int)data.getByte(pos+1));}
					
					public int getSizeInBytes() {return 2;}
					
					public boolean doCommand(NUSALSeqChannel channel){
						if(channel == null) return false;
						super.flagChannelUsed(channel.getIndex());
						channel.setEffectsLevel((byte)super.getParam(0));
						return true;
					}
					
					protected String paramsToString(){
						return super.getParam(0) + " 0x" + String.format("%02x", super.getParam(0)); 
					}
				};
			case 0x8:
				return new NUSALSeqCommand(NUSALSeqCmdType.CH_VIBRATO, cmdb){
					private boolean timeflag;
					protected void onInit(){
						super.setParam(0, (int)data.getByte(pos+1));
						//Check next byte
						int nb = Byte.toUnsignedInt(data.getByte(pos+2));
						if(nb < 0x78){
							//Set
							timeflag = true;
							super.setParam(1, nb);
						}
					}
					
					public int getSizeInBytes() {return timeflag?3:2;}
					
					public boolean doCommand(NUSALSeqChannel channel){
						if(channel == null) return false;
						super.flagChannelUsed(channel.getIndex());
						channel.setVibrato((byte)super.getParam(0), super.getParam(1));
						return true;
					}
					
					protected String paramsToString(){
						String str = "0x" + String.format("%02x", super.getParam(0));
						if(super.getParam(1) > 0){
							str += " " + super.getParam(1);
						}
						return str;
					}
				};
			case 0xa:
				return new CMD_IgnoredCommand(NUSALSeqCmdType.CH_UNK_DA, cmdb, 2);
			case 0xc:
				return new CMD_IgnoredCommand(NUSALSeqCmdType.CH_DRUMSET, cmdb, 2);
			case 0xd:
				return new NUSALSeqCommand(NUSALSeqCmdType.CH_PAN, cmdb){
					private boolean timeflag;
					protected void onInit(){
						super.setParam(0, Byte.toUnsignedInt(data.getByte(pos+1)));
						int nb = Byte.toUnsignedInt(data.getByte(pos+2));
						if(nb < 0x78){
							//Set
							timeflag = true;
							super.setParam(1, nb);
						}
					}
					public int getSizeInBytes() {return timeflag?3:2;}
					public boolean doCommand(NUSALSeqChannel channel){
						if(channel == null) return false;
						super.flagChannelUsed(channel.getIndex());
						channel.setPan((byte)super.getParam(0), super.getParam(1));
						return true;
					}
					protected String paramsToString(){
						String str = "0x" + String.format("%02x", super.getParam(0));
						if(super.getParam(1) > 0){
							str += " " + super.getParam(1);
						}
						return str;
					}
				};
			case 0xf:
				return new NUSALSeqCommand(NUSALSeqCmdType.CH_VOLUME, cmdb){
					private boolean timeflag;
					protected void onInit(){
						super.setParam(0, Byte.toUnsignedInt(data.getByte(pos+1)));
						int nb = Byte.toUnsignedInt(data.getByte(pos+2));
						if(nb < 0x78){
							//Set
							timeflag = true;
							super.setParam(1, nb);
						}
					}
					public int getSizeInBytes() {return timeflag?3:2;}
					public boolean doCommand(NUSALSeqChannel channel){
						if(channel == null) return false;
						super.flagChannelUsed(channel.getIndex());
						channel.setVolume((byte)super.getParam(0), super.getParam(1));
						return true;
					}
					protected String paramsToString(){
						String str = "0x" + String.format("%02x", super.getParam(0));
						if(super.getParam(1) > 0){
							str += " " + super.getParam(1);
						}
						return str;
					}
				};
			}
			break;
		case 0xe: 
			switch(lo){
			case 0x0:
				return new CMD_IgnoredCommand(NUSALSeqCmdType.CH_UNK_E0, cmdb, 2);
			case 0x9:
				return new NUSALSeqCommand(NUSALSeqCmdType.CH_PRIORITY, cmdb){
					protected void onInit(){super.setParam(0, Byte.toUnsignedInt(data.getByte(pos+1)));}
					public int getSizeInBytes() {return 2;}
					public boolean doCommand(NUSALSeqChannel channel){
						if(channel == null) return false;
						super.flagChannelUsed(channel.getIndex());
						channel.setPriority((byte)super.getParam(0));
						return true;
					}
					protected String paramsToString(){
						return super.getParam(0) + " 0x" + String.format("%02x", (byte)super.getParam(0)); 
					}
				};
			}
			break;
		case 0xf: 
			switch(lo){
			case 0x2:
				return new NUSALSeqCommand(NUSALSeqCmdType.BRANCH_IF_LTZ_REL, cmdb){
					protected void onInit(){
						super.setParam(0, (int)data.getByte(pos+1));}
					public int getSizeInBytes() {return 2;}
					public boolean doCommand(NUSALSeqChannel channel){
						if(channel == null) return false;
						if(channel.getVar() < 0){
							int nowpos = channel.getCurrentPosition();
							channel.jumpTo(nowpos + getBranchAddress(), false);	
						}
						flagChannelUsed(channel.getIndex());
						return true;
					}
					public boolean isBranch(){return true;};
					public boolean isRelativeBranch(){return true;}
					public int getBranchAddress(){return super.getParam(0)-1;}
					protected String paramsToString(){
						return String.format("0x%02x", super.getParam(0));  
					}
				};
			case 0x3:
				return new NUSALSeqCommand(NUSALSeqCmdType.BRANCH_IF_EQZ_REL, cmdb){
					protected void onInit(){
						super.setParam(0, (int)data.getByte(pos+1));}
					public int getSizeInBytes() {return 2;}
					public boolean doCommand(NUSALSeqChannel channel){
						if(channel == null) return false;
						if(channel.getVar() == 0){
							int nowpos = channel.getCurrentPosition();
							channel.jumpTo(nowpos + getBranchAddress(), false);	
						}
						flagChannelUsed(channel.getIndex());
						return true;
					}
					public boolean isBranch(){return true;};
					public boolean isRelativeBranch(){return true;}
					public int getBranchAddress(){return super.getParam(0)-1;}
					protected String paramsToString(){
						return String.format("0x%02x", super.getParam(0)); 
					}
				};
			case 0x4:
				return new NUSALSeqCommand(NUSALSeqCmdType.BRANCH_ALWAYS_REL, cmdb){
					protected void onInit(){
						super.setParam(0, (int)data.getByte(pos+1));}
					public int getSizeInBytes() {return 2;}
					public boolean doCommand(NUSALSeqChannel channel){
						if(channel == null) return false;
						int nowpos = channel.getCurrentPosition();
						channel.jumpTo(nowpos + getBranchAddress(), false);
						flagChannelUsed(channel.getIndex());
						return true;
					}
					public boolean isBranch(){return true;};
					public boolean isRelativeBranch(){return true;}
					public int getBranchAddress(){return super.getParam(0)-1;}
					protected String paramsToString(){
						return String.format("0x%02x", super.getParam(0)); 
					}
				};
			case 0x5:
				return new NUSALSeqCommand(NUSALSeqCmdType.BRANCH_IF_GTEZ, cmdb){
					protected void onInit(){
						super.setParam(0, Short.toUnsignedInt(data.shortFromFile(pos+1)));}
					public int getSizeInBytes() {return 3;}
					public boolean doCommand(NUSALSeqChannel channel){
						if(channel == null) return false;
						if(channel.getVar() >= 0){
							channel.jumpTo(super.getParam(0), false);
						}
						flagChannelUsed(channel.getIndex());
						return true;
					}
					public boolean isBranch(){return true;};
					public int getBranchAddress(){return super.getParam(0);}
					protected String paramsToString(){
						return String.format("0x%04x", super.getParam(0)); 
					}
				};
			case 0x7:
				return new NUSALSeqCommand(NUSALSeqCmdType.LOOP_END, cmdb){
					public int getSizeInBytes() {return 1;}
					public boolean doCommand(NUSALSeqChannel channel){
						if(channel == null) return false;
						channel.signalLoopEnd();
						flagChannelUsed(channel.getIndex());
						return true;
					}
				};
			case 0x8:
				return new NUSALSeqCommand(NUSALSeqCmdType.LOOP_START, cmdb){
					protected void onInit(){
						super.setParam(0, Byte.toUnsignedInt(data.getByte(pos+1)));}
					public int getSizeInBytes() {return 2;}
					public boolean doCommand(NUSALSeqChannel channel){
						if(channel == null) return false;
						channel.signalLoopStart(super.getParam(0));
						flagChannelUsed(channel.getIndex());
						return true;
					}
				};
			case 0x9:
				return new NUSALSeqCommand(NUSALSeqCmdType.BRANCH_IF_LTZ, cmdb){
					protected void onInit(){
						super.setParam(0, Short.toUnsignedInt(data.shortFromFile(pos+1)));}
					public int getSizeInBytes() {return 3;}
					public boolean doCommand(NUSALSeqChannel channel){
						if(channel == null) return false;
						if(channel.getVar() < 0){
							channel.jumpTo(super.getParam(0), false);
						}
						flagChannelUsed(channel.getIndex());
						return true;
					}
					public boolean isBranch(){return true;};
					public int getBranchAddress(){return super.getParam(0);}
					protected String paramsToString(){
						return String.format("0x%04x", super.getParam(0)); 
					}
				};
			case 0xa:
				return new NUSALSeqCommand(NUSALSeqCmdType.BRANCH_IF_EQZ, cmdb){
					protected void onInit(){
						super.setParam(0, Short.toUnsignedInt(data.shortFromFile(pos+1)));}
					public int getSizeInBytes() {return 3;}
					public boolean doCommand(NUSALSeqChannel channel){
						if(channel == null) return false;
						if(channel.getVar() == 0){
							channel.jumpTo(super.getParam(0), false);
						}
						flagChannelUsed(channel.getIndex());
						return true;
					}
					public boolean isBranch(){return true;};
					public int getBranchAddress(){return super.getParam(0);}
					protected String paramsToString(){
						return String.format("0x%04x", super.getParam(0)); 
					}
				};
			case 0xb:
				return new NUSALSeqCommand(NUSALSeqCmdType.BRANCH_ALWAYS, cmdb){
					protected void onInit(){
						super.setParam(0, Short.toUnsignedInt(data.shortFromFile(pos+1)));}
					public int getSizeInBytes() {return 3;}
					public boolean doCommand(NUSALSeqChannel channel){
						if(channel == null) return false;
						channel.jumpTo(super.getParam(0), false);
						flagChannelUsed(channel.getIndex());
						return true;
					}
					public boolean isBranch(){return true;};
					public int getBranchAddress(){return super.getParam(0);}
					protected String paramsToString(){
						return String.format("0x%04x", super.getParam(0)); 
					}
				};
			case 0xc:
				return new NUSALSeqCommand(NUSALSeqCmdType.CALL, cmdb){
					protected void onInit(){
						super.setParam(0, Short.toUnsignedInt(data.shortFromFile(pos+1)));}
					public int getSizeInBytes() {return 3;}
					public boolean doCommand(NUSALSeqChannel channel){
						if(channel == null) return false;
						channel.jumpTo(super.getParam(0), true);
						flagChannelUsed(channel.getIndex());
						return true;
					}
					public boolean isBranch(){return true;};
					public int getBranchAddress(){return super.getParam(0);}
					protected String paramsToString(){
						return String.format("0x%04x", super.getParam(0)); 
					}
				};
			case 0xd:
				return new NUSALSeqCommand(NUSALSeqCmdType.WAIT, cmdb){
					private int bcount;
					protected void onInit(){
						int[] vlq = readVLQ(pos+1);
						super.setParam(0, vlq[0]);
						bcount = vlq[1]+1;
					}
					public int getSizeInBytes() {return bcount;}
					public boolean doCommand(NUSALSeqChannel channel){
						if(channel == null) return false;	
						flagChannelUsed(channel.getIndex());
						return channel.setWait(super.getParam(0));
					}
				};
			case 0xe:
				return new NUSALSeqCommand(NUSALSeqCmdType.RETURN, cmdb){
					public int getSizeInBytes() {return 1;}
					public boolean doCommand(NUSALSeqChannel channel){
						if(channel == null) return false;
						channel.returnFromCall();
						flagChannelUsed(channel.getIndex());
						return true;
					}
				};
			case 0xf:
				//System.err.println("debug -- end found");
				return new NUSALSeqCommand(NUSALSeqCmdType.END_READ, cmdb){
					public int getSizeInBytes() {return 1;}
					public boolean doCommand(NUSALSeqChannel channel){
						if(channel == null) return false;
						channel.signalReadEnd();
						flagChannelUsed(channel.getIndex());
						return true;
					}
				};
			}
			break;
		}
		
		return null;
	}
	
	private NUSALSeqCommand readVoiceCommand(int pos){
		byte cmdb = data.getByte(pos);
		int cmdi = Byte.toUnsignedInt(cmdb);
		int hi = (cmdi >>> 4) & 0xf;
		int lo = cmdi & 0xf;
		
		if(hi < 0x4){
			int[] vlq = readVLQ(pos+1);
			byte v = data.getByte(pos+1+vlq[1]);
			byte g = data.getByte(pos+1+vlq[1]);
			return new NUSALSeqCommand(NUSALSeqCmdType.PLAY_NOTE_NTVG, cmdb){

				private int vlq_sz;
				protected void onInit(){
					super.setParam(0, Byte.toUnsignedInt(cmdb));
					super.setParam(1, vlq[0]);
					super.setParam(2, Byte.toUnsignedInt(v));
					super.setParam(3, Byte.toUnsignedInt(g));
					vlq_sz = vlq[1];
				}; 
				
				public int getSizeInBytes() {return 3 + vlq_sz;}
				
				public boolean doCommand(NUSALSeqLayer voice){
					if(voice == null) return false;
					voice.playNote((byte)super.getParam(0), (byte)super.getParam(2), (byte)super.getParam(3), super.getParam(1));
					flagLayerUsed(voice.getChannelIndex(), voice.getLayerIndex());
					return true;
				}
				
				protected String paramsToString(){
					return String.format("%02x, ", super.getParam(0)) + 
							super.getParam(1) + " ticks, " +
							"v" + super.getParam(2) + 
							", g" + super.getParam(3); 
				}
			};
		}
		else if(hi >= 0x4 && hi < 0x8){
			int[] vlq = readVLQ(pos+1);
			byte n = (byte)(cmdi - 0x40);
			byte v = data.getByte(pos+1+vlq[1]);
			return new NUSALSeqCommand(NUSALSeqCmdType.PLAY_NOTE_NTV, cmdb){

				private int vlq_sz;
				protected void onInit(){
					super.setParam(0, Byte.toUnsignedInt(n));
					super.setParam(1, vlq[0]);
					super.setParam(2, Byte.toUnsignedInt(v));
					vlq_sz = vlq[1];
				}; 
				
				public int getSizeInBytes() {return 2 + vlq_sz;}
				
				public boolean doCommand(NUSALSeqLayer voice){
					if(voice == null) return false;
					voice.playNote((byte)super.getParam(0), (byte)super.getParam(2), super.getParam(1));
					flagLayerUsed(voice.getChannelIndex(), voice.getLayerIndex());
					return true;
				}
				
				protected String paramsToString(){
					return String.format("%02x, ", super.getParam(0)) + 
							super.getParam(1) + " ticks, " +
							"v" + super.getParam(2); 
				}
			};
		}
		else if(hi >= 0x8 && hi < 0xc){
			byte n = (byte)(cmdi - 0x80);
			byte v = data.getByte(pos+1);
			byte g = data.getByte(pos+2);
			return new NUSALSeqCommand(NUSALSeqCmdType.PLAY_NOTE_NVG, cmdb){
				protected void onInit(){
					super.setParam(0, Byte.toUnsignedInt(n));
					super.setParam(1, Byte.toUnsignedInt(v));
					super.setParam(2, Byte.toUnsignedInt(g));
				}; 
				
				public int getSizeInBytes() {return 3;}
				
				public boolean doCommand(NUSALSeqLayer voice){
					if(voice == null) return false;
					voice.playNote((byte)super.getParam(0), (byte)super.getParam(1), (byte)super.getParam(2));
					flagLayerUsed(voice.getChannelIndex(), voice.getLayerIndex());
					return true;
				}
				
				protected String paramsToString(){
					return String.format("%02x, ", super.getParam(0)) + 
							", v" + super.getParam(1) +
							", g" + super.getParam(2); 
				}
			};
		}
		else if(hi == 0xc){
			switch(lo){
			case 0x0:
				//Rest
				int[] vlq = readVLQ(pos+1);
				return new NUSALSeqCommand(NUSALSeqCmdType.REST, cmdb){

					private int vlq_sz;
					protected void onInit(){
						super.setParam(0, vlq[0]);
						vlq_sz = vlq[1];
					}; 
					
					public int getSizeInBytes() {return 1 + vlq_sz;}
					
					public boolean doCommand(NUSALSeqLayer voice){
						if(voice == null) return false;
						voice.rest(super.getParam(0));
						flagLayerUsed(voice.getChannelIndex(), voice.getLayerIndex());
						return true;
					}
				};
			case 0x2:
				//Transpose
				int amt = (int)data.getByte(pos+1);
				return new NUSALSeqCommand(NUSALSeqCmdType.TRANSPOSE, cmdb){
					protected void onInit(){
						super.setParam(0, amt);
					}; 
					public int getSizeInBytes() {return 2;}
					public boolean doCommand(NUSALSeqLayer voice){
						if(voice == null) return false;
						voice.setTranspose(super.getParam(0));
						flagLayerUsed(voice.getChannelIndex(), voice.getLayerIndex());
						return true;
					}
				};
			}
		}
		else if(hi == 0xf){
			//Branches -___-
			switch(lo){
			case 0x2:
				return new NUSALSeqCommand(NUSALSeqCmdType.BRANCH_IF_LTZ_REL, cmdb){
					protected void onInit(){
						super.setParam(0, (int)data.getByte(pos+1));}
					public int getSizeInBytes() {return 2;}
					public boolean doCommand(NUSALSeqLayer voice){
						if(voice == null) return false;
						if(voice.getChannelVar() < 0){
							int nowpos = voice.getCurrentPosition();
							voice.jumpTo(nowpos + getBranchAddress(), false);	
						}
						flagLayerUsed(voice.getChannelIndex(), voice.getLayerIndex());
						return true;
					}
					public boolean isBranch(){return true;};
					public boolean isRelativeBranch(){return true;}
					public int getBranchAddress(){return super.getParam(0)-1;}
					protected String paramsToString(){
						return String.format("0x%02x", super.getParam(0));  
					}
				};
			case 0x3:
				return new NUSALSeqCommand(NUSALSeqCmdType.BRANCH_IF_EQZ_REL, cmdb){
					protected void onInit(){
						super.setParam(0, (int)data.getByte(pos+1));}
					public int getSizeInBytes() {return 2;}
					public boolean doCommand(NUSALSeqLayer voice){
						if(voice == null) return false;
						if(voice.getChannelVar() == 0){
							int nowpos = voice.getCurrentPosition();
							voice.jumpTo(nowpos + getBranchAddress(), false);	
						}
						flagLayerUsed(voice.getChannelIndex(), voice.getLayerIndex());
						return true;
					}
					public boolean isBranch(){return true;};
					public boolean isRelativeBranch(){return true;}
					public int getBranchAddress(){return super.getParam(0)-1;}
					protected String paramsToString(){
						return String.format("0x%02x", super.getParam(0)); 
					}
				};
			case 0x4:
				return new NUSALSeqCommand(NUSALSeqCmdType.BRANCH_ALWAYS_REL, cmdb){
					protected void onInit(){
						super.setParam(0, (int)data.getByte(pos+1));}
					public int getSizeInBytes() {return 2;}
					public boolean doCommand(NUSALSeqLayer voice){
						if(voice == null) return false;
						int nowpos = voice.getCurrentPosition();
						voice.jumpTo(nowpos + getBranchAddress(), false);	
						flagLayerUsed(voice.getChannelIndex(), voice.getLayerIndex());
						return true;
					}
					public boolean isBranch(){return true;};
					public boolean isRelativeBranch(){return true;}
					public int getBranchAddress(){return super.getParam(0)-1;}
					protected String paramsToString(){
						return String.format("0x%02x", super.getParam(0)); 
					}
				};
			case 0x5:
				return new NUSALSeqCommand(NUSALSeqCmdType.BRANCH_IF_GTEZ, cmdb){
					protected void onInit(){
						super.setParam(0, Short.toUnsignedInt(data.shortFromFile(pos+1)));}
					public int getSizeInBytes() {return 3;}
					public boolean doCommand(NUSALSeqLayer voice){
						if(voice == null) return false;
						if(voice.getChannelVar() >= 0){
							voice.jumpTo(super.getParam(0), false);	
						}
						flagLayerUsed(voice.getChannelIndex(), voice.getLayerIndex());
						return true;
					}
					public boolean isBranch(){return true;};
					public int getBranchAddress(){return super.getParam(0);}
					protected String paramsToString(){
						return String.format("0x%04x", super.getParam(0)); 
					}
				};
			case 0x7:
				return new NUSALSeqCommand(NUSALSeqCmdType.LOOP_END, cmdb){
					public int getSizeInBytes() {return 1;}
					public boolean doCommand(NUSALSeqLayer voice){
						if(voice == null) return false;
						voice.signalLoopEnd();
						flagLayerUsed(voice.getChannelIndex(), voice.getLayerIndex());
						return true;
					}
				};
			case 0x8:
				return new NUSALSeqCommand(NUSALSeqCmdType.LOOP_START, cmdb){
					protected void onInit(){
						super.setParam(0, Byte.toUnsignedInt(data.getByte(pos+1)));}
					public int getSizeInBytes() {return 2;}
					public boolean doCommand(NUSALSeqLayer voice){
						if(voice == null) return false;
						voice.signalLoopStart(super.getParam(0));
						flagLayerUsed(voice.getChannelIndex(), voice.getLayerIndex());
						return true;
					}
				};
			case 0x9:
				return new NUSALSeqCommand(NUSALSeqCmdType.BRANCH_IF_LTZ, cmdb){
					protected void onInit(){
						super.setParam(0, Short.toUnsignedInt(data.shortFromFile(pos+1)));}
					public int getSizeInBytes() {return 3;}
					public boolean doCommand(NUSALSeqLayer voice){
						if(voice == null) return false;
						if(voice.getChannelVar() < 0){
							voice.jumpTo(super.getParam(0), false);	
						}
						flagLayerUsed(voice.getChannelIndex(), voice.getLayerIndex());
						return true;
					}
					public boolean isBranch(){return true;};
					public int getBranchAddress(){return super.getParam(0);}
					protected String paramsToString(){
						return String.format("0x%04x", super.getParam(0)); 
					}
				};
			case 0xa:
				return new NUSALSeqCommand(NUSALSeqCmdType.BRANCH_IF_EQZ, cmdb){
					protected void onInit(){
						super.setParam(0, Short.toUnsignedInt(data.shortFromFile(pos+1)));}
					public int getSizeInBytes() {return 3;}
					public boolean doCommand(NUSALSeqLayer voice){
						if(voice == null) return false;
						if(voice.getChannelVar() == 0){
							voice.jumpTo(super.getParam(0), false);	
						}
						flagLayerUsed(voice.getChannelIndex(), voice.getLayerIndex());
						return true;
					}
					public boolean isBranch(){return true;};
					public int getBranchAddress(){return super.getParam(0);}
					protected String paramsToString(){
						return String.format("0x%04x", super.getParam(0)); 
					}
				};
			case 0xb:
				return new NUSALSeqCommand(NUSALSeqCmdType.BRANCH_ALWAYS, cmdb){
					protected void onInit(){
						super.setParam(0, Short.toUnsignedInt(data.shortFromFile(pos+1)));}
					public int getSizeInBytes() {return 3;}
					public boolean doCommand(NUSALSeqLayer voice){
						if(voice == null) return false;
						voice.jumpTo(super.getParam(0), false);
						flagLayerUsed(voice.getChannelIndex(), voice.getLayerIndex());
						return true;
					}
					public boolean isBranch(){return true;};
					public int getBranchAddress(){return super.getParam(0);}
					protected String paramsToString(){
						return String.format("0x%04x", super.getParam(0)); 
					}
				};
			case 0xc:
				return new NUSALSeqCommand(NUSALSeqCmdType.CALL, cmdb){
					protected void onInit(){
						super.setParam(0, Short.toUnsignedInt(data.shortFromFile(pos+1)));}
					public int getSizeInBytes() {return 3;}
					public boolean doCommand(NUSALSeqLayer voice){
						if(voice == null) return false;
						voice.jumpTo(super.getParam(0), true);
						flagLayerUsed(voice.getChannelIndex(), voice.getLayerIndex());
						return true;
					}
					public boolean isBranch(){return true;};
					public int getBranchAddress(){return super.getParam(0);}
					protected String paramsToString(){
						return String.format("0x%04x", super.getParam(0)); 
					}
				};
			case 0xd:
				return new NUSALSeqCommand(NUSALSeqCmdType.WAIT, cmdb){
					private int bcount;
					protected void onInit(){
						int[] vlq = readVLQ(pos+1);
						super.setParam(0, vlq[0]);
						bcount = vlq[1]+1;
					}
					public int getSizeInBytes() {return bcount;}
					public boolean doCommand(NUSALSeqLayer voice){
						if(voice == null) return false;
						voice.setWait(super.getParam(0));
						flagLayerUsed(voice.getChannelIndex(), voice.getLayerIndex());
						return true;
					}
				};
			case 0xe:
				return new NUSALSeqCommand(NUSALSeqCmdType.RETURN, cmdb){
					public int getSizeInBytes() {return 1;}
					public boolean doCommand(NUSALSeqLayer voice){
						if(voice == null) return false;
						voice.returnFromCall();
						flagLayerUsed(voice.getChannelIndex(), voice.getLayerIndex());
						return true;
					}
				};
			case 0xf:
				return new NUSALSeqCommand(NUSALSeqCmdType.END_READ, cmdb){
					public int getSizeInBytes() {return 1;}
					public boolean doCommand(NUSALSeqLayer voice){
						if(voice == null) return false;
						voice.signalReadEnd();
						flagLayerUsed(voice.getChannelIndex(), voice.getLayerIndex());
						return true;
					}
				};
			}
		}
		
		return null;
	}
	
	public boolean parse(){
		reset();
		//Use pos -1 to flag end...
		
		//List of lists for holding channel start points
		List<List<Integer>> list = new ArrayList<List<Integer>>(16);
		for(int i = 0; i < 16; i++) list.add(new LinkedList<Integer>());
		LinkedList<Integer> branches = new LinkedList<Integer>();
		
		//Seq commands
		//System.err.println("NUSALSeqParser.parse || -DEBUG- Starting seq command parsing");
		while(seq_pos >= 0){
			//System.err.println("NUSALSeqParser.parse || -DEBUG- POSITION 0x" + Integer.toHexString(seq_pos));
			if(cmdmap.containsKey(seq_pos)){
				//Check for branches. If none, break.
				if(branches.isEmpty()) break;
				seq_pos = branches.pop(); continue;
			}
			NUSALSeqCommand cmd = readSeqCommand(seq_pos);
			if(cmd == null){
				System.err.println("NUSALSeqParser.parse || Read error - unrecognized seq command at 0x" + Integer.toHexString(seq_pos));
				return false;
			}
			cmdmap.put(seq_pos, cmd);
			cmd.flagSeqUsed();
			
			//Determine if need to save for later
			if(cmd.isBranch()){
				if(cmd.getCommand() == NUSALSeqCmdType.BRANCH_ALWAYS){
					seq_pos = cmd.getBranchAddress();
					continue;
				}
				else if(cmd.getCommand() == NUSALSeqCmdType.BRANCH_ALWAYS_REL){
					seq_pos += cmd.getBranchAddress();
					continue;
				}
				else if(cmd.getCommand() == NUSALSeqCmdType.BRANCH_TO_SEQSTART){
					seq_pos = 0;
					continue;
				}
				else if(cmd.getCommand() == NUSALSeqCmdType.RETURN){
					//Treat like end
					if(branches.isEmpty()) break;
					seq_pos = branches.pop(); continue;
				}
				else if(cmd.getCommand() == NUSALSeqCmdType.RETURN_FROM_SEQ){
					//Treat like end
					if(branches.isEmpty()) break;
					seq_pos = branches.pop(); continue;
				}
				else{
					if(cmd.isRelativeBranch()){branches.add(cmd.getBranchAddress() + seq_pos);}
					else{branches.add(cmd.getBranchAddress());}	
				}
			}
			if(cmd.getCommand() == NUSALSeqCmdType.CHANNEL_OFFSET){
				int ch = cmd.getParam(0);
				int off = cmd.getParam(1);
				list.get(ch).add(off);
			}
			else if(cmd.getCommand() == NUSALSeqCmdType.CHANNEL_OFFSET_REL){
				int ch = cmd.getParam(0);
				int off = cmd.getParam(1) + seq_pos;
				list.get(ch).add(off);
			}
			else if(cmd.getCommand() == NUSALSeqCmdType.END_READ){
				//Check for branches. If none, break.
				if(branches.isEmpty()) break;
				seq_pos = branches.pop(); continue;
			}
			
			seq_pos += cmd.getSizeInBytes();
			if(seq_pos >= data.getFileSize()){
				//Bad end, but break anyway.
				if(branches.isEmpty()) break;
				seq_pos = branches.pop();
			}
		}
		seq_pos = -1;
		
		//Channel commands
		//System.err.println("NUSALSeqParser.parse || -DEBUG- Starting channel command parsing");
		List<List<Integer>> vlist = new ArrayList<List<Integer>>(4);
		for(int i = 0; i < 4; i++) vlist.add(new LinkedList<Integer>());
		for(int i = 0; i < 16; i++){
			//System.err.println("NUSALSeqParser.parse || -DEBUG- Now parsing channel " + i);
			for(int j = 0; j < 4; j++) vlist.get(j).clear();
			branches.addAll(list.get(i));
			while(!branches.isEmpty()){
				int ch_pos = branches.pop();
				while(ch_pos >= 0){
					//System.err.println("NUSALSeqParser.parse || -DEBUG- POSITION 0x" + Integer.toHexString(ch_pos));
					NUSALSeqCommand cmd = null;
					if(cmdmap.containsKey(ch_pos)){
						//If this channel has already been here, break.
						//If not, just mark as being in this channel and continue
						cmd = cmdmap.get(ch_pos);
						if(cmd.channelUsed(i)) break;
						cmd.flagChannelUsed(i);
					}
					else{
						cmd = readChCommand(ch_pos);
						if(cmd == null){
							System.err.println("NUSALSeqParser.parse || Read error - unrecognized seq command at 0x" + Integer.toHexString(ch_pos));
							return false;
						}
						cmd.flagChannelUsed(i);
						cmdmap.put(ch_pos, cmd);
					}
					//Check for branch
					if(cmd.isBranch()){
						if(cmd.getCommand() == NUSALSeqCmdType.BRANCH_ALWAYS){
							ch_pos = cmd.getBranchAddress();
							continue;
						}
						else if(cmd.getCommand() == NUSALSeqCmdType.BRANCH_ALWAYS_REL){
							ch_pos += cmd.getBranchAddress();
							continue;
						}
						else if(cmd.getCommand() == NUSALSeqCmdType.RETURN){
							//Treat like end
							if(branches.isEmpty()) break;
							ch_pos = branches.pop(); continue;
						}
						else{
							if(cmd.isRelativeBranch()){branches.add(cmd.getBranchAddress() + ch_pos);}
							else{branches.add(cmd.getBranchAddress());}	
						}
					}
						
					//Check for voice ptr
					if(cmd.getCommand() == NUSALSeqCmdType.VOICE_OFFSET){
						int vox = cmd.getParam(0);
						int off = cmd.getParam(1);
						vlist.get(vox).add(off);
					}
					else if(cmd.getCommand() == NUSALSeqCmdType.VOICE_OFFSET_REL){
						int vox = cmd.getParam(0);
						int off = cmd.getParam(1) + ch_pos;
						vlist.get(vox).add(off);
					}
					else if(cmd.getCommand() == NUSALSeqCmdType.END_READ) break;
						
					ch_pos += cmd.getSizeInBytes();
					if(ch_pos >= data.getFileSize()) break;
				}
			}
			
			//Voices
			for(int j = 0; j < 4; j++){
				//System.err.println("NUSALSeqParser.parse || -DEBUG- Now parsing layer " + j + " from channel " + i);
				branches.addAll(vlist.get(j));
				while(!branches.isEmpty()){
					int vpos = branches.pop();
					while(vpos >= 0){
						//System.err.println("NUSALSeqParser.parse || -DEBUG- POSITION 0x" + Integer.toHexString(vpos));
						NUSALSeqCommand cmd = null;
						if(cmdmap.containsKey(vpos)){
							cmd = cmdmap.get(vpos);
							if(cmd.layerUsed(i, j)) break;
							cmd.flagLayerUsed(i, j);
						}
						else{
							cmd = readVoiceCommand(vpos);
							if(cmd == null){
								System.err.println("NUSALSeqParser.parse || Read error - unrecognized seq command at 0x" + Integer.toHexString(vpos));
								return false;
							}
							cmd.flagLayerUsed(i,j);
							cmdmap.put(vpos, cmd);
						}	
						if(cmd.isBranch()){
							if(cmd.getCommand() == NUSALSeqCmdType.BRANCH_ALWAYS){
								vpos = cmd.getBranchAddress();
								continue;
							}
							else if(cmd.getCommand() == NUSALSeqCmdType.BRANCH_ALWAYS_REL){
								vpos += cmd.getBranchAddress();
								continue;
							}
							else if(cmd.getCommand() == NUSALSeqCmdType.RETURN){
								//Treat like end
								if(branches.isEmpty()) break;
								vpos = branches.pop(); continue;
							}
							else{
								if(cmd.isRelativeBranch()){branches.add(cmd.getBranchAddress() + vpos);}
								else{branches.add(cmd.getBranchAddress());}	
							}
						}
							
						//Check for voice ptr
						if(cmd.getCommand() == NUSALSeqCmdType.END_READ) break;
							
						vpos += cmd.getSizeInBytes();
						if(vpos >= data.getFileSize()) break;
					} //while(vpos >= 0)
				} //while(!branches.isEmpty())
			} //for(int j = 0; j < 4; j++)
		} // for(int i = 0; i < 16; i++)
		
		return true;
	}
	
	public NUSALSeqCommandSource getCommandSource(){
		BasicCommandMap outmap = new BasicCommandMap();
		for(Entry<Integer, NUSALSeqCommand> entry : cmdmap.entrySet()){
			outmap.addCommand(entry.getKey(), entry.getValue());
		}
		return outmap;
	}
	
	/*---- Debug ----*/
	
	public void printMeTo(Writer out) throws IOException{
		if(cmdmap.isEmpty()){
			out.write("<Command map is empty>\n");
			return;
		}
		List<Integer> keys = new ArrayList<Integer>(cmdmap.size()+1);
		keys.addAll(cmdmap.keySet());
		Collections.sort(keys);
		
		for(Integer k : keys){
			out.write(String.format("%04x\t", k));
			NUSALSeqCommand cmd = cmdmap.get(k);
			out.write(cmd.toString() + "\t");
			int bcount = cmd.getSizeInBytes();
			for(int i = 0; i < bcount; i++){
				out.write(String.format("%02x ", data.getByte(k+i)));
			}
			out.write("\t");
			
			if(cmd.seqUsed()) out.write("seq ");
			for(int i = 0; i < 16; i++){
				if(cmd.channelUsed(i)) out.write("ch-" + Integer.toHexString(i) + " ");
				for(int j = 0; j < 4; j++){
					if(cmd.layerUsed(i, j)){
						out.write(Integer.toHexString(i) + "-" + j + " ");
					}
				}
			}
			out.write("\n");
		}
		
	}
	
}
