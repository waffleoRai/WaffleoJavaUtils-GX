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
import waffleoRai_SeqSound.n64al.cmd.*;
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
		
		int[] iarr = null;
		
		switch(hi){
			case 0x0:
				//Set seq var to ch var
				//No parameters (besides channel)
				return new NUSALSeqGenericCommand(NUSALSeqCmdType.LOAD_CH_VAR, lo){
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
				return new NUSALSeqGenericCommand(NUSALSeqCmdType.SEQ_UNK_4x, lo);
			case 0x5:
				//seq var -= ch var
				return new NUSALSeqGenericCommand(NUSALSeqCmdType.SUBTRACT_CH, lo){
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
				return new NUSALSeqGenericCommand(NUSALSeqCmdType.LOAD_CH_DMA, lo){
					protected void onInit(){
						super.setParam(1, (int)data.getByte(pos+1));
						super.setParam(2, (int)data.getByte(pos+2));
					}
				};
			case 0x7:
				//Set ch var
				return new NUSALSeqGenericCommand(NUSALSeqCmdType.SET_CH_VAR, lo){
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
				return new NUSALSeqGenericCommand(NUSALSeqCmdType.SEQ_UNK_8x, lo){
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
				return new NUSALSeqReferenceCommand(NUSALSeqCmdType.CHANNEL_OFFSET, lo, Short.toUnsignedInt(data.shortFromFile(pos+1))){
					public boolean doCommand(NUSALSeq sequence){
						if(sequence == null) return false;
						flagSeqUsed();
						return sequence.pointChannelTo(super.getParam(0), super.getParam(1));
					}
				};
			case 0xa:
				//Specify channel start (rel addr)
				return new NUSALSeqReferenceCommand(NUSALSeqCmdType.CHANNEL_OFFSET_REL, lo, (int)data.getByte(pos+1)){
					public boolean doCommand(NUSALSeq sequence){
						if(sequence == null) return false;
						flagSeqUsed();
						return sequence.pointChannelTo(super.getParam(0), getBranchAddress());
					}
				};
			case 0xb:
				//"New sequence from" using channel??? 
				return new NUSALSeqGenericCommand(NUSALSeqCmdType.NEW_SEQ_FROM, lo){
					protected void onInit(){
						super.setParam(1, (int)data.getByte(pos+1));
						super.setParam(2, (int)data.shortFromFile(pos+2));
					}
				};
			case 0xc:
				switch(lo){
				case 0x4:
					return new NUSALSeqGenericCommand(NUSALSeqCmdType.SEQ_UNK_C4){
						protected void onInit(){
							super.setParam(0, (int)data.getByte(pos+1));
							super.setParam(1, (int)data.getByte(pos+2));
						}
					};
				case 0x5:
					return new NUSALSeqGenericCommand(NUSALSeqCmdType.SEQ_UNK_C5){
						protected void onInit(){
							super.setParam(0, (int)data.shortFromFile(pos+1));
						}
					};
				case 0x6:
					return new NUSALSeqGenericCommand(NUSALSeqCmdType.RETURN_FROM_SEQ);
				case 0x7:
					return new NUSALSeqGenericCommand(NUSALSeqCmdType.SEQ_UNK_C7){
						protected void onInit(){
							super.setParam(0, (int)data.getByte(pos+1));
							super.setParam(1, (int)data.getByte(pos+2));
						}
					};
				case 0x8:
					return new NUSALSeqGenericCommand(NUSALSeqCmdType.SUBTRACT_IMM){

						protected void onInit(){
							super.setParam(0, (int)data.getByte(pos+1));}

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
					return new NUSALSeqGenericCommand(NUSALSeqCmdType.AND_IMM){
						protected void onInit(){
							super.setDisplayStringHex(true);
							super.setParam(0, Byte.toUnsignedInt(data.getByte(pos+1)));
						}
						public boolean doCommand(NUSALSeq sequence){
							if(sequence == null) return false;
							int var = sequence.getSeqVar();
							var &= super.getParam(0);
							sequence.setSeqVar(var);
							flagSeqUsed();
							return true;
						}
					};
				case 0xa:
					return new NUSALSeqGenericCommand(NUSALSeqCmdType.SEQ_UNK_CA);
				case 0xb:
					return new NUSALSeqGenericCommand(NUSALSeqCmdType.SEQ_UNK_CB);
				case 0xc:
					return new NUSALSeqGenericCommand(NUSALSeqCmdType.SET_VAR){

						protected void onInit(){ 
							super.setParam(0, (int)data.getByte(pos+1));}

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
					return new NUSALSeqGenericCommand(NUSALSeqCmdType.SET_FORMAT){
						protected void onInit(){
							super.setDisplayStringHex(true);
							super.setParam(0, Byte.toUnsignedInt(data.getByte(pos+1)));
						}
						public boolean doCommand(NUSALSeq sequence){
							if(sequence == null) return false;
							sequence.setFormat((byte)super.getParam(0));
							flagSeqUsed();
							return true;
						}
					};
				case 0x5:
					return new NUSALSeqGenericCommand(NUSALSeqCmdType.SET_TYPE){
						protected void onInit(){
							super.setDisplayStringHex(true);
							super.setParam(0, Byte.toUnsignedInt(data.getByte(pos+1)));
						}
						public boolean doCommand(NUSALSeq sequence){
							if(sequence == null) return false;
							sequence.setType((byte)super.getParam(0));
							flagSeqUsed();
							return true;
						}
					};
				case 0x6:
					return new NUSALSeqGenericCommand(NUSALSeqCmdType.DISABLE_CHANNELS){
						protected void onInit(){
							super.setDisplayStringHex(true);
							super.setParam(0, Short.toUnsignedInt(data.shortFromFile(pos+1)));
						}
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
					};
				case 0x7:
					return new NUSALSeqGenericCommand(NUSALSeqCmdType.ENABLE_CHANNELS){
						protected void onInit(){
							super.setDisplayStringHex(true);
							super.setParam(0, Short.toUnsignedInt(data.shortFromFile(pos+1)));
						}
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
					};
				case 0xb:
					return new NUSALSeqGenericCommand(NUSALSeqCmdType.MASTER_VOLUME){
						protected void onInit(){
							super.setParam(0, Byte.toUnsignedInt(data.getByte(pos+1)));}
						public boolean doCommand(NUSALSeq sequence){
							if(sequence == null) return false;
							sequence.setMasterVolume((byte)super.getParam(0));
							flagSeqUsed();
							return true;
						}
					};
				case 0xd:
					return new NUSALSeqGenericCommand(NUSALSeqCmdType.SET_TEMPO){
						protected void onInit(){
							super.setParam(0, Byte.toUnsignedInt(data.getByte(pos+1)));}
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
					return new NUSALSeqGenericCommand(NUSALSeqCmdType.SEQ_UNK_DF){
						protected void onInit(){
							super.setParam(0, Byte.toUnsignedInt(data.getByte(pos+1)));}
					};
				}
				break;
			case 0xe:
				switch(lo){
				case 0xf:
					return new NUSALSeqGenericCommand(NUSALSeqCmdType.SEQ_UNK_EF){
						protected void onInit(){
							super.setParam(0, (int)data.shortFromFile(pos+1));
							super.setParam(1, Byte.toUnsignedInt(data.getByte(pos+3)));
						}
					};
				}
				break;
			case 0xf:
				switch(lo){
				case 0x2:
					return new NUSALSeqReferenceCommand(NUSALSeqCmdType.BRANCH_IF_LTZ_REL, (int)data.getByte(pos+1)){
						public boolean doCommand(NUSALSeq sequence){
							if(sequence == null) return false;
							if(sequence.getSeqVar() < 0){
								/*int nowpos = sequence.getCurrentPosition();
								sequence.jumpTo(nowpos + getBranchAddress(), false);*/
								sequence.jumpTo(this.getBranchAddress(), false);
							}
							flagSeqUsed();
							return true;
						}
					};
				case 0x3:
					return new NUSALSeqReferenceCommand(NUSALSeqCmdType.BRANCH_IF_EQZ_REL, (int)data.getByte(pos+1)){
						public boolean doCommand(NUSALSeq sequence){
							if(sequence == null) return false;
							if(sequence.getSeqVar() == 0){
								/*int nowpos = sequence.getCurrentPosition();
								sequence.jumpTo(nowpos + getBranchAddress(), false);	*/
								sequence.jumpTo(this.getBranchAddress(), false);
							}
							flagSeqUsed();
							return true;
						}
					};
				case 0x4:
					return new NUSALSeqReferenceCommand(NUSALSeqCmdType.BRANCH_ALWAYS_REL, (int)data.getByte(pos+1)){
						public boolean doCommand(NUSALSeq sequence){
							if(sequence == null) return false;
							/*int nowpos = sequence.getCurrentPosition();
							sequence.jumpTo(nowpos + getBranchAddress(), false);*/	
							sequence.jumpTo(this.getBranchAddress(), false);
							flagSeqUsed();
							return true;
						}
					};
				case 0x5:
					return new NUSALSeqReferenceCommand(NUSALSeqCmdType.BRANCH_IF_GTEZ, Short.toUnsignedInt(data.shortFromFile(pos+1))){
						public boolean doCommand(NUSALSeq sequence){
							if(sequence == null) return false;
							if(sequence.getSeqVar() >= 0){
								sequence.jumpTo(super.getParam(0), false);	
							}
							flagSeqUsed();
							return true;
						}
					};
				case 0x6:
					return new NUSALSeqReferenceCommand(NUSALSeqCmdType.BRANCH_TO_SEQSTART, 0){
						public int getSizeInBytes() {return 1;}
						public boolean doCommand(NUSALSeq sequence){
							if(sequence == null) return false;
							sequence.jumpTo(0, false);
							flagSeqUsed();
							return true;
						}
					};
				case 0x7:
					return new NUSALSeqGenericCommand(NUSALSeqCmdType.LOOP_END){
						public boolean doCommand(NUSALSeq sequence){
							if(sequence == null) return false;
							sequence.signalLoopEnd();
							flagSeqUsed();
							return true;
						}
					};
				case 0x8:
					return new NUSALSeqGenericCommand(NUSALSeqCmdType.LOOP_START){
						protected void onInit(){
							super.setParam(0, Byte.toUnsignedInt(data.getByte(pos+1)));}
						public boolean doCommand(NUSALSeq sequence){
							if(sequence == null) return false;
							sequence.signalLoopStart(super.getParam(0));
							flagSeqUsed();
							return true;
						}
					};
				case 0x9:
					return new NUSALSeqReferenceCommand(NUSALSeqCmdType.BRANCH_IF_LTZ, Short.toUnsignedInt(data.shortFromFile(pos+1))){
						public boolean doCommand(NUSALSeq sequence){
							if(sequence == null) return false;
							if(sequence.getSeqVar() < 0){
								sequence.jumpTo(super.getParam(0), false);	
							}
							flagSeqUsed();
							return true;
						}
					};
				case 0xa:
					return new NUSALSeqReferenceCommand(NUSALSeqCmdType.BRANCH_IF_EQZ, Short.toUnsignedInt(data.shortFromFile(pos+1))){
						public boolean doCommand(NUSALSeq sequence){
							if(sequence == null) return false;
							if(sequence.getSeqVar() == 0){
								sequence.jumpTo(super.getParam(0), false);	
							}
							flagSeqUsed();
							return true;
						}
					};
				case 0xb:
					return new NUSALSeqReferenceCommand(NUSALSeqCmdType.BRANCH_ALWAYS, Short.toUnsignedInt(data.shortFromFile(pos+1))){
						public boolean doCommand(NUSALSeq sequence){
							if(sequence == null) return false;
							sequence.jumpTo(super.getParam(0), false);	
							flagSeqUsed();
							return true;
						}
					};
				case 0xc:
					return new NUSALSeqReferenceCommand(NUSALSeqCmdType.CALL, Short.toUnsignedInt(data.shortFromFile(pos+1))){
						public boolean doCommand(NUSALSeq sequence){
							if(sequence == null) return false;
							sequence.jumpTo(super.getParam(0), true);	
							flagSeqUsed();
							return true;
						}
					};
				case 0xd:
					iarr = readVLQ(pos+1);
					return new NUSALSeqWaitCommand(iarr[0] ,false);
				case 0xe:
					return new NUSALSeqGenericCommand(NUSALSeqCmdType.RETURN){
						public boolean doCommand(NUSALSeq sequence){
							if(sequence == null) return false;
							sequence.returnFromCall();
							flagSeqUsed();
							return true;
						}
					};
				case 0xf:
					return new NUSALSeqGenericCommand(NUSALSeqCmdType.END_READ){
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
				return new NUSALSeqReferenceCommand(NUSALSeqCmdType.VOICE_OFFSET_REL, layer, (int)data.getByte(pos+1)){
					public boolean doCommand(NUSALSeqChannel channel){
						if(channel == null) return false;
						super.flagChannelUsed(channel.getIndex());
						//int pos = channel.getCurrentPosition();	
						return channel.pointLayerTo(super.getParam(0), 
								super.getBranchAddress());
					}
				};
			}
			break;
		case 0x8: 
			if(lo >= 0x8 && lo <= 0xb){
				int layer = lo - 8;
				return new NUSALSeqReferenceCommand(NUSALSeqCmdType.VOICE_OFFSET, layer, Short.toUnsignedInt(data.shortFromFile(pos+1))){
					public boolean doCommand(NUSALSeqChannel channel){
						if(channel == null) return false;
						super.flagChannelUsed(channel.getIndex());
						return channel.pointLayerTo(super.getParam(0), 
								super.getParam(1));
					}
				};
			}
			break;
		case 0xc: 
			switch(lo){
			case 0x1:
				return new NUSALSeqGenericCommand(NUSALSeqCmdType.SET_PROGRAM){
					protected void onInit(){super.setParam(0, Byte.toUnsignedInt(data.getByte(pos+1)));}
					public boolean doCommand(NUSALSeqChannel channel){
						if(channel == null) return false;
						super.flagChannelUsed(channel.getIndex());
						return channel.changeProgram(super.getParam(0));
					}
				};
			case 0x2:
				return new NUSALSeqGenericCommand(NUSALSeqCmdType.TRANSPOSE){
					protected void onInit(){super.setParam(0, (int)data.getByte(pos+1));}
					public boolean doCommand(NUSALSeqChannel channel){
						if(channel == null) return false;
						super.flagChannelUsed(channel.getIndex());
						channel.setTranspose(super.getParam(0));
						return true;
					}
				};
			case 0x4:
				return new NUSALSeqGenericCommand(NUSALSeqCmdType.INIT_CHANNEL){
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
				return new NUSALSeqTimeoptCommand(NUSALSeqCmdType.CH_PITCHBEND, (int)data.getByte(pos+1)){
					protected void onInit(){
						int nb = Byte.toUnsignedInt(data.getByte(pos+2));
						if(nb < 0x78){
							//Set
							super.setParam(1, nb);
						}
					}
					
					protected void doChannelAction(NUSALSeqChannel channel){
						channel.setPitchbend((byte)super.getParam(0));
					}
				};
			case 0x4:
				return new NUSALSeqTimeoptCommand(NUSALSeqCmdType.CH_REVERB, (int)data.getByte(pos+1)){

					protected void onInit(){
						int nb = Byte.toUnsignedInt(data.getByte(pos+2));
						if(nb < 0x78){
							//Set
							super.setParam(1, nb);
						}
					}
					
					protected void doChannelAction(NUSALSeqChannel channel){
						channel.setEffectsLevel((byte)super.getParam(0));
					}
					
				};
			case 0x8:
				return new NUSALSeqTimeoptCommand(NUSALSeqCmdType.CH_VIBRATO, (int)data.getByte(pos+1)){
					protected void onInit(){
						int nb = Byte.toUnsignedInt(data.getByte(pos+2));
						if(nb < 0x78){
							//Set
							super.setParam(1, nb);
						}
					}
					protected void doChannelAction(NUSALSeqChannel channel){
						channel.setVibrato((byte)super.getParam(0));
					}	
				};
			case 0xa:
				return new NUSALSeqGenericCommand(NUSALSeqCmdType.CH_UNK_DA){
					protected void onInit(){
						super.setParam(0, (int)data.getByte(pos+1));
					}
				};
			case 0xc:
				return new NUSALSeqGenericCommand(NUSALSeqCmdType.CH_DRUMSET){
					protected void onInit(){
						super.setParam(0, (int)data.getByte(pos+1));
					}
				};
			case 0xd:
				return new NUSALSeqTimeoptCommand(NUSALSeqCmdType.CH_PAN, (int)data.getByte(pos+1)){
					protected void onInit(){
						int nb = Byte.toUnsignedInt(data.getByte(pos+2));
						if(nb < 0x78){
							//Set
							super.setParam(1, nb);
						}
					}
					protected void doChannelAction(NUSALSeqChannel channel){
						channel.setPan((byte)super.getParam(0));
					}	
				};
			case 0xf:
				return new NUSALSeqTimeoptCommand(NUSALSeqCmdType.CH_VOLUME, (int)data.getByte(pos+1)){
					protected void onInit(){
						int nb = Byte.toUnsignedInt(data.getByte(pos+2));
						if(nb < 0x78){
							//Set
							super.setParam(1, nb);
						}
					}
					protected void doChannelAction(NUSALSeqChannel channel){
						channel.setVolume((byte)super.getParam(0));
					}	
				};
			}
			break;
		case 0xe: 
			switch(lo){
			case 0x0:
				return new NUSALSeqGenericCommand(NUSALSeqCmdType.CH_UNK_E0){
					protected void onInit(){
						super.setParam(0, (int)data.getByte(pos+1));
					}
				};
			case 0x9:
				return new NUSALSeqGenericCommand(NUSALSeqCmdType.CH_PRIORITY){
					protected void onInit(){super.setParam(0, Byte.toUnsignedInt(data.getByte(pos+1)));}
					public boolean doCommand(NUSALSeqChannel channel){
						if(channel == null) return false;
						super.flagChannelUsed(channel.getIndex());
						channel.setPriority((byte)super.getParam(0));
						return true;
					}
				};
			}
			break;
		case 0xf: 
			switch(lo){
			case 0x2:
				return new NUSALSeqReferenceCommand(NUSALSeqCmdType.BRANCH_IF_LTZ_REL, (int)data.getByte(pos+1)){
					public boolean doCommand(NUSALSeqChannel channel){
						if(channel == null) return false;
						if(channel.getVar() < 0){
							/*int nowpos = channel.getCurrentPosition();
							channel.jumpTo(nowpos + getBranchAddress(), false);	*/
							channel.jumpTo(this.getBranchAddress(), false);
						}
						flagChannelUsed(channel.getIndex());
						return true;
					}
				};
			case 0x3:
				return new NUSALSeqReferenceCommand(NUSALSeqCmdType.BRANCH_IF_EQZ_REL, (int)data.getByte(pos+1)){
					public boolean doCommand(NUSALSeqChannel channel){
						if(channel == null) return false;
						if(channel.getVar() == 0){
							/*int nowpos = channel.getCurrentPosition();
							channel.jumpTo(nowpos + getBranchAddress(), false);	*/
							channel.jumpTo(this.getBranchAddress(), false);
						}
						flagChannelUsed(channel.getIndex());
						return true;
					}
				};
			case 0x4:
				return new NUSALSeqReferenceCommand(NUSALSeqCmdType.BRANCH_ALWAYS_REL, (int)data.getByte(pos+1)){
					public boolean doCommand(NUSALSeqChannel channel){
						if(channel == null) return false;
						/*int nowpos = channel.getCurrentPosition();
						channel.jumpTo(nowpos + getBranchAddress(), false);*/
						channel.jumpTo(this.getBranchAddress(), false);
						flagChannelUsed(channel.getIndex());
						return true;
					}
				};
			case 0x5:
				return new NUSALSeqReferenceCommand(NUSALSeqCmdType.BRANCH_IF_GTEZ, Short.toUnsignedInt(data.shortFromFile(pos+1))){
					public boolean doCommand(NUSALSeqChannel channel){
						if(channel == null) return false;
						if(channel.getVar() >= 0){
							channel.jumpTo(super.getParam(0), false);
						}
						flagChannelUsed(channel.getIndex());
						return true;
					}
				};
			case 0x7:
				return new NUSALSeqGenericCommand(NUSALSeqCmdType.LOOP_END){
					public boolean doCommand(NUSALSeqChannel channel){
						if(channel == null) return false;
						channel.signalLoopEnd();
						flagChannelUsed(channel.getIndex());
						return true;
					}
				};
			case 0x8:
				return new NUSALSeqGenericCommand(NUSALSeqCmdType.LOOP_START){
					protected void onInit(){
						super.setParam(0, Byte.toUnsignedInt(data.getByte(pos+1)));}
					public boolean doCommand(NUSALSeqChannel channel){
						if(channel == null) return false;
						channel.signalLoopStart(super.getParam(0));
						flagChannelUsed(channel.getIndex());
						return true;
					}
				};
			case 0x9:
				return new NUSALSeqReferenceCommand(NUSALSeqCmdType.BRANCH_IF_LTZ, Short.toUnsignedInt(data.shortFromFile(pos+1))){
					public boolean doCommand(NUSALSeqChannel channel){
						if(channel == null) return false;
						if(channel.getVar() < 0){
							channel.jumpTo(super.getParam(0), false);
						}
						flagChannelUsed(channel.getIndex());
						return true;
					}
				};
			case 0xa:
				return new NUSALSeqReferenceCommand(NUSALSeqCmdType.BRANCH_IF_EQZ, Short.toUnsignedInt(data.shortFromFile(pos+1))){
					public boolean doCommand(NUSALSeqChannel channel){
						if(channel == null) return false;
						if(channel.getVar() == 0){
							channel.jumpTo(super.getParam(0), false);
						}
						flagChannelUsed(channel.getIndex());
						return true;
					}
				};
			case 0xb:
				return new NUSALSeqReferenceCommand(NUSALSeqCmdType.BRANCH_ALWAYS, Short.toUnsignedInt(data.shortFromFile(pos+1))){
					public boolean doCommand(NUSALSeqChannel channel){
						if(channel == null) return false;
						channel.jumpTo(super.getParam(0), false);
						flagChannelUsed(channel.getIndex());
						return true;
					}
				};
			case 0xc:
				return new NUSALSeqReferenceCommand(NUSALSeqCmdType.CALL, Short.toUnsignedInt(data.shortFromFile(pos+1))){
					public boolean doCommand(NUSALSeqChannel channel){
						if(channel == null) return false;
						channel.jumpTo(super.getParam(0), true);
						flagChannelUsed(channel.getIndex());
						return true;
					}
				};
			case 0xd:
				int[] iarr = readVLQ(pos+1);
				return new NUSALSeqWaitCommand(iarr[0] ,false);
			case 0xe:
				return new NUSALSeqGenericCommand(NUSALSeqCmdType.RETURN){
					public boolean doCommand(NUSALSeqChannel channel){
						if(channel == null) return false;
						channel.returnFromCall();
						flagChannelUsed(channel.getIndex());
						return true;
					}
				};
			case 0xf:
				//System.err.println("debug -- end found");
				return new NUSALSeqGenericCommand(NUSALSeqCmdType.END_READ){
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
			byte g = data.getByte(pos+2+vlq[1]);
			return NUSALSeqNoteCommand.fromRawCommand(cmdb, vlq[0], v, g);
		}
		else if(hi >= 0x4 && hi < 0x8){
			int[] vlq = readVLQ(pos+1);
			//byte n = (byte)(cmdi - 0x40);
			byte v = data.getByte(pos+1+vlq[1]);
			return NUSALSeqNoteCommand.fromRawCommand(cmdb, vlq[0], v);
		}
		else if(hi >= 0x8 && hi < 0xc){
			//byte n = (byte)(cmdi - 0x80);
			byte v = data.getByte(pos+1);
			byte g = data.getByte(pos+2);
			return NUSALSeqNoteCommand.fromRawCommand(cmdb, v, g);
		}
		else if(hi == 0xc){
			switch(lo){
			case 0x0:
				//Rest
				int[] vlq = readVLQ(pos+1);
				return new NUSALSeqWaitCommand(vlq[0] ,true);
			case 0x2:
				//Transpose
				int amt = (int)data.getByte(pos+1);
				return new NUSALSeqGenericCommand(NUSALSeqCmdType.TRANSPOSE){
					protected void onInit(){
						super.setParam(0, amt);
					}; 
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
				return new NUSALSeqReferenceCommand(NUSALSeqCmdType.BRANCH_IF_LTZ_REL, (int)data.getByte(pos+1)){
					public boolean doCommand(NUSALSeqLayer voice){
						if(voice == null) return false;
						if(voice.getChannelVar() < 0){
							/*int nowpos = voice.getCurrentPosition();
							voice.jumpTo(nowpos + getBranchAddress(), false);*/	
							voice.jumpTo(getBranchAddress(), false);	
						}
						flagLayerUsed(voice.getChannelIndex(), voice.getLayerIndex());
						return true;
					}
				};
			case 0x3:
				return new NUSALSeqReferenceCommand(NUSALSeqCmdType.BRANCH_IF_EQZ_REL, (int)data.getByte(pos+1)){
					public boolean doCommand(NUSALSeqLayer voice){
						if(voice == null) return false;
						if(voice.getChannelVar() == 0){
							/*int nowpos = voice.getCurrentPosition();
							voice.jumpTo(nowpos + getBranchAddress(), false);*/
							voice.jumpTo(getBranchAddress(), false);
						}
						flagLayerUsed(voice.getChannelIndex(), voice.getLayerIndex());
						return true;
					}
				};
			case 0x4:
				return new NUSALSeqReferenceCommand(NUSALSeqCmdType.BRANCH_ALWAYS_REL, (int)data.getByte(pos+1)){
					public boolean doCommand(NUSALSeqLayer voice){
						if(voice == null) return false;
						/*int nowpos = voice.getCurrentPosition();
						voice.jumpTo(nowpos + getBranchAddress(), false);	*/
						voice.jumpTo(getBranchAddress(), false);
						flagLayerUsed(voice.getChannelIndex(), voice.getLayerIndex());
						return true;
					}
				};
			case 0x5:
				return new NUSALSeqReferenceCommand(NUSALSeqCmdType.BRANCH_IF_GTEZ, Short.toUnsignedInt(data.shortFromFile(pos+1))){
					public boolean doCommand(NUSALSeqLayer voice){
						if(voice == null) return false;
						if(voice.getChannelVar() >= 0){
							voice.jumpTo(super.getParam(0), false);	
						}
						flagLayerUsed(voice.getChannelIndex(), voice.getLayerIndex());
						return true;
					}
				};
			case 0x7:
				return new NUSALSeqGenericCommand(NUSALSeqCmdType.LOOP_END){
					public boolean doCommand(NUSALSeqLayer voice){
						if(voice == null) return false;
						voice.signalLoopEnd();
						flagLayerUsed(voice.getChannelIndex(), voice.getLayerIndex());
						return true;
					}
				};
			case 0x8:
				return new NUSALSeqGenericCommand(NUSALSeqCmdType.LOOP_START){
					protected void onInit(){
						super.setParam(0, Byte.toUnsignedInt(data.getByte(pos+1)));}
					public boolean doCommand(NUSALSeqLayer voice){
						if(voice == null) return false;
						voice.signalLoopStart(super.getParam(0));
						flagLayerUsed(voice.getChannelIndex(), voice.getLayerIndex());
						return true;
					}
				};
			case 0x9:
				return new NUSALSeqReferenceCommand(NUSALSeqCmdType.BRANCH_IF_LTZ, Short.toUnsignedInt(data.shortFromFile(pos+1))){
					public boolean doCommand(NUSALSeqLayer voice){
						if(voice == null) return false;
						if(voice.getChannelVar() < 0){
							voice.jumpTo(super.getParam(0), false);	
						}
						flagLayerUsed(voice.getChannelIndex(), voice.getLayerIndex());
						return true;
					}
				};
			case 0xa:
				return new NUSALSeqReferenceCommand(NUSALSeqCmdType.BRANCH_IF_EQZ, Short.toUnsignedInt(data.shortFromFile(pos+1))){
					public boolean doCommand(NUSALSeqLayer voice){
						if(voice == null) return false;
						if(voice.getChannelVar() == 0){
							voice.jumpTo(super.getParam(0), false);	
						}
						flagLayerUsed(voice.getChannelIndex(), voice.getLayerIndex());
						return true;
					}
				};
			case 0xb:
				return new NUSALSeqReferenceCommand(NUSALSeqCmdType.BRANCH_ALWAYS, Short.toUnsignedInt(data.shortFromFile(pos+1))){
					public boolean doCommand(NUSALSeqLayer voice){
						if(voice == null) return false;
						voice.jumpTo(super.getParam(0), false);
						flagLayerUsed(voice.getChannelIndex(), voice.getLayerIndex());
						return true;
					}
				};
			case 0xc:
				return new NUSALSeqReferenceCommand(NUSALSeqCmdType.CALL, Short.toUnsignedInt(data.shortFromFile(pos+1))){
					public boolean doCommand(NUSALSeqLayer voice){
						if(voice == null) return false;
						voice.jumpTo(super.getParam(0), true);
						flagLayerUsed(voice.getChannelIndex(), voice.getLayerIndex());
						return true;
					}
				};
			case 0xd:
				int[] vlq = readVLQ(pos+1);
				return new NUSALSeqWaitCommand(vlq[0], false);
			case 0xe:
				return new NUSALSeqGenericCommand(NUSALSeqCmdType.RETURN){
					public boolean doCommand(NUSALSeqLayer voice){
						if(voice == null) return false;
						voice.returnFromCall();
						flagLayerUsed(voice.getChannelIndex(), voice.getLayerIndex());
						return true;
					}
				};
			case 0xf:
				return new NUSALSeqGenericCommand(NUSALSeqCmdType.END_READ){
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
		NUSALSeqCommand prev_cmd = null;
		while(seq_pos >= 0){
			//System.err.println("NUSALSeqParser.parse || -DEBUG- POSITION 0x" + Integer.toHexString(seq_pos));
			if(cmdmap.containsKey(seq_pos)){
				//Check for branches. If none, break.
				if(branches.isEmpty()) break;
				if(prev_cmd != null && (prev_cmd.getSubsequentCommand() != null)){
					prev_cmd = null;
				}
				seq_pos = branches.pop(); continue;
			}
			NUSALSeqCommand cmd = readSeqCommand(seq_pos);
			if(cmd == null){
				System.err.println("NUSALSeqParser.parse || Read error - unrecognized seq command at 0x" + Integer.toHexString(seq_pos));
				return false;
			}
			cmdmap.put(seq_pos, cmd);
			cmd.setAddress(seq_pos);
			cmd.flagSeqUsed();
			if(prev_cmd != null) prev_cmd.setSubsequentCommand(cmd);
			prev_cmd = cmd; 
			
			//Determine if need to save for later
			if(cmd.isBranch()){
				if(cmd.getCommand() == NUSALSeqCmdType.BRANCH_ALWAYS){
					seq_pos = cmd.getBranchAddress();
					continue;
				}
				else if(cmd.getCommand() == NUSALSeqCmdType.BRANCH_ALWAYS_REL){
					seq_pos = cmd.getBranchAddress();
					continue;
				}
				else if(cmd.getCommand() == NUSALSeqCmdType.BRANCH_TO_SEQSTART){
					seq_pos = 0;
					continue;
				}
				else if(cmd.getCommand() == NUSALSeqCmdType.RETURN){
					//Treat like end
					if(branches.isEmpty()) break;
					prev_cmd = null;
					seq_pos = branches.pop(); continue;
				}
				else if(cmd.getCommand() == NUSALSeqCmdType.RETURN_FROM_SEQ){
					//Treat like end
					if(branches.isEmpty()) break;
					prev_cmd = null;
					seq_pos = branches.pop(); continue;
				}
				else{
					if(cmd.getCommand() != NUSALSeqCmdType.CHANNEL_OFFSET && cmd.getCommand() != NUSALSeqCmdType.CHANNEL_OFFSET_REL){
						//System.err.println("NUSALSeqParser.parse || Adding as branch: 0x" + Integer.toHexString(cmd.getBranchAddress()) + " (via " + cmd.toString() +" )");
						branches.add(cmd.getBranchAddress());
					}
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
				prev_cmd = null;
				seq_pos = branches.pop(); continue;
			}
			
			seq_pos += cmd.getSizeInBytes();
			if(seq_pos >= data.getFileSize()){
				//Bad end, but break anyway.
				if(branches.isEmpty()) break;
				if(prev_cmd != null && (prev_cmd.getSubsequentCommand() != null)){
					prev_cmd = null;
				}
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
			prev_cmd = null;
			for(int j = 0; j < 4; j++) vlist.get(j).clear();
			branches.addAll(list.get(i));
			while(!branches.isEmpty()){
				int ch_pos = branches.pop();
				if(prev_cmd != null && (prev_cmd.getSubsequentCommand() != null)){
					prev_cmd = null;
				}
				while(ch_pos >= 0){
					//System.err.println("NUSALSeqParser.parse || -DEBUG- POSITION 0x" + Integer.toHexString(ch_pos));
					NUSALSeqCommand cmd = null;
					if(cmdmap.containsKey(ch_pos)){
						//If this channel has already been here, break.
						//If not, just mark as being in this channel and continue
						cmd = cmdmap.get(ch_pos);
						if(cmd.channelUsed(i)) break;
						cmd.flagChannelUsed(i);
						prev_cmd = cmd;
					}
					else{
						cmd = readChCommand(ch_pos);
						if(cmd == null){
							System.err.println("NUSALSeqParser.parse || Read error - unrecognized channel control command at 0x" + Integer.toHexString(ch_pos));
							return false;
						}
						cmd.flagChannelUsed(i);
						cmd.setAddress(ch_pos);
						cmdmap.put(ch_pos, cmd);
						if(prev_cmd != null){
							prev_cmd.setSubsequentCommand(cmd);
						}
						prev_cmd = cmd;
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
							prev_cmd = null;
							ch_pos = branches.pop(); continue;
						}
						else{
							if(cmd.getCommand() != NUSALSeqCmdType.VOICE_OFFSET && cmd.getCommand() != NUSALSeqCmdType.VOICE_OFFSET_REL){
								//System.err.println("NUSALSeqParser.parse || Adding as branch: 0x" + Integer.toHexString(cmd.getBranchAddress()) + " (via " + cmd.toString() +" )");
								branches.add(cmd.getBranchAddress());
							}
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
				prev_cmd = null;
				branches.addAll(vlist.get(j));
				while(!branches.isEmpty()){
					int vpos = branches.pop();
					if(prev_cmd != null && (prev_cmd.getSubsequentCommand() != null)){
						prev_cmd = null;
					}
					while(vpos >= 0){
						//System.err.println("NUSALSeqParser.parse || -DEBUG- POSITION 0x" + Integer.toHexString(vpos));
						NUSALSeqCommand cmd = null;
						if(cmdmap.containsKey(vpos)){
							cmd = cmdmap.get(vpos);
							if(cmd.layerUsed(i, j)) break;
							cmd.flagLayerUsed(i, j);
							prev_cmd = cmd;
						}
						else{
							cmd = readVoiceCommand(vpos);
							if(cmd == null){
								System.err.println("NUSALSeqParser.parse || Read error - unrecognized voice command at 0x" + Integer.toHexString(vpos));
								return false;
							}
							cmd.setAddress(vpos);
							cmd.flagLayerUsed(i,j);
							cmdmap.put(vpos, cmd);
							if(prev_cmd != null){
								prev_cmd.setSubsequentCommand(cmd);
							}
							prev_cmd = cmd;
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
								prev_cmd = null;
								vpos = branches.pop(); continue;
							}
							else{
								/*if(cmd.isRelativeBranch()){branches.add(cmd.getBranchAddress() + vpos);}
								else{branches.add(cmd.getBranchAddress());}	*/
								branches.add(cmd.getBranchAddress());
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
		updateReferenceLinks();
		return true;
	}
	
	public void updateReferenceLinks(){
		List<Integer> keys = new ArrayList<Integer>(cmdmap.size()+1);
		keys.addAll(cmdmap.keySet());
		Collections.sort(keys);
		for(Integer k : keys){
			NUSALSeqCommand cmd = cmdmap.get(k);
			if(cmd == null) continue;
			if(cmd.isBranch()){
				NUSALSeqCommand targ = cmdmap.get(cmd.getBranchAddress());
				cmd.setReference(targ);
			}
		}
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
