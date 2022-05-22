package waffleoRai_SeqSound.n64al;

import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import waffleoRai_Utils.FileBuffer;

public abstract class NUSALSeqCommand {
	
	public static final int SERIALFMT_DUMMY = -1;
	public static final int SERIALFMT_ = 0; //Just the command byte
	public static final int SERIALFMT_L = 1; //Command byte only - arg0 is low nyb
	public static final int SERIALFMT_1 = 2; //Command byte, 1 one-byte arg
	public static final int SERIALFMT_L1 = 3; //Cmdbyte lonyb + 1 byte
	public static final int SERIALFMT_2 = 4;
	public static final int SERIALFMT_L2 = 5;
	public static final int SERIALFMT_11 = 6;
	public static final int SERIALFMT_12 = 7;
	public static final int SERIALFMT_21 = 8;
	public static final int SERIALFMT_L11 = 9;
	public static final int SERIALFMT_L12 = 10;
	public static final int SERIALFMT_111 = 11;
	public static final int SERIALFMT_V = 12;
	public static final int SERIALFMT_NOTE = 13;
	public static final int SERIALFMT_CPARAMS = 14;
	public static final int SERIALFMT_COPYFILTER = 15;
	
	private NUSALSeqCmdType command;
	private byte cmdbyte;
	private int[] params;
	
	private int address;
	private String label;
	private int tick_addr = -1; //Optional
	
	private boolean seq_ctx; //Has this command been called by the seq?
	private Set<Integer> ch_ctx; //What channel/voices have called this command?
	//These should be read as bytes - high nyb is channel, low nyb is voice.
	//If channel itself, the low nyb is 0xf
	
	//Linked list
	private NUSALSeqCommand next_cmd = null;
	private NUSALSeqCommand prev_cmd = null;
	private List<NUSALSeqCommand> referees;
	
	public NUSALSeqCommand(NUSALSeqCmdType cmd, byte cmd_byte){
		cmdbyte = cmd_byte;
		command = cmd;
		if(cmd.getParameterCount() > 0) params = new int[cmd.getParameterCount()];
		ch_ctx = new TreeSet<Integer>();
		seq_ctx = false;
		onInit();
	}
	
	protected void onInit(){}
	
	protected int[] restructureCommand(NUSALSeqCmdType newtype, byte cmd_byte){
		int[] oldparams = params;
		cmdbyte = cmd_byte;
		command = newtype;
		if(newtype.getParameterCount() > 0) params = new int[newtype.getParameterCount()];
		else params = null;
		return oldparams;
	}
	
	public NUSALSeqCmdType getCommand(){return command;}
	public byte getCommandByte(){return cmdbyte;}
	protected void setCommand(NUSALSeqCmdType cmd){command = cmd;}
	protected void setCommandByte(byte b){cmdbyte = b;}
	
	public NUSALSeqCommand getSubsequentCommand(){return next_cmd;}
	public NUSALSeqCommand getPreviousCommand(){return prev_cmd;}
	public void setReference(NUSALSeqCommand target){}
	
	public List<NUSALSeqCommand> getReferees(){
		if(referees == null) return null;
		List<NUSALSeqCommand> list = new LinkedList<NUSALSeqCommand>();
		list.addAll(referees);
		return list;
	}
	
	public void addReferee(NUSALSeqCommand ref){
		if(referees == null){
			referees = new LinkedList<NUSALSeqCommand>();
		}
		referees.add(ref);
	}
	
	public void setSubsequentCommand(NUSALSeqCommand next){
		next_cmd = next;
		if(next_cmd != null) next_cmd.prev_cmd = this;
	}
	
	public boolean seqUsed(){return seq_ctx;}
	public void flagSeqUsed(){seq_ctx = true;}
	
	public boolean channelUsed(int ch){
		int check = (ch << 4) | 0xf;
		return ch_ctx.contains(check);
	}
	
	public void flagChannelUsed(int ch){
		int check = (ch << 4) | 0xf;
		ch_ctx.add(check);
	}
	
	public boolean layerUsed(int ch, int layer){
		int check = (ch << 4) | (layer & 0x3);
		return ch_ctx.contains(check);
	}
	
	public void flagLayerUsed(int ch, int layer){
		int check = (ch << 4) | (layer & 0x3);
		ch_ctx.add(check);
	}
	
	public int getParamCount(){
		if(params == null) return 0;
		return params.length;
	}
	
	public int getParam(int idx){
		if(params == null || idx < 0 || idx >= params.length){
			throw new IndexOutOfBoundsException("Parameter at index " + idx + " does not exist");
		}
		return params[idx];
	}
	
	public void setParam(int idx, int val){
		if(params == null || idx < 0 || idx >= params.length){
			throw new IndexOutOfBoundsException("Parameter at index " + idx + " does not exist");
		}
		params[idx] = val;
	}
	
	public void setParam(int idx, byte val, boolean signExtend){
		if(params == null || idx < 0 || idx >= params.length){
			throw new IndexOutOfBoundsException("Parameter at index " + idx + " does not exist");
		}
		if(signExtend) params[idx] = (int)val;
		else params[idx] = Byte.toUnsignedInt(val);
	}
	
	public void setParam(int idx, short val, boolean signExtend){
		if(params == null || idx < 0 || idx >= params.length){
			throw new IndexOutOfBoundsException("Parameter at index " + idx + " does not exist");
		}
		if(signExtend) params[idx] = (int)val;
		else params[idx] = Short.toUnsignedInt(val);
	}
	
	public void setAddress(int addr){address = addr;}
	public int getAddress(){return address;}
	
	public int getTickAddress(){return tick_addr;}
	public void setTickAddress(int t){tick_addr = t;}
	
	public String getLabel(){return label;}
	public void setLabel(String s){label = s;}
	
	public String genCtxLabelSuggestion(){
		if(seq_ctx) return "seq_block";
		for(int i = 0; i < 16; i++){
			if(channelUsed(i)) return "ch" + String.format("%X", i) + "_block";
		}
		for(int i = 0; i < 16; i++){
			for(int j = 0; j < 4; j++){
				if(layerUsed(i,j)) {
					return "ch" + String.format("%X", i) +
							"l" + j + "_block";	
				}
			}
		}
		return "lbl";
	}
	
	public boolean doCommand(NUSALSeq sequence){
		throw new UnsupportedOperationException("Command 0x" + String.format("%02x", cmdbyte)
			+ " not supported in sequence context");
	}
	
	public boolean doCommand(NUSALSeqChannel channel){
		throw new UnsupportedOperationException("Command 0x" + String.format("%02x", cmdbyte)
			+ " not supported in channel context");
	}
	
	public boolean doCommand(NUSALSeqLayer voice){
		throw new UnsupportedOperationException("Command 0x" + String.format("%02x", cmdbyte)
			+ " not supported in layer context");
	}
	
	public boolean isBranch(){return false;}
	public boolean isRelativeBranch(){return false;}
	public int getBranchAddress(){return -1;}
	public NUSALSeqCommand getBranchTarget(){return null;}
	
	public boolean isChunk(){return false;}
	public NUSALSeqCommand getChunkHead(){return this;}
	public boolean isEndCommand(){return false;}
	//public boolean isTimeExtendable(){return false;}
	public int getSizeInTicks(){return 0;}
	//public void setOptionalTime(int ticks){}
	
	public int getSizeInBytes(){
		int sz = command.getMinimumSizeInBytes();
		if(command.hasVariableLength()){
			//Check if need one more byte.
			int varg = params[command.getVLQIndex()];
			if(varg > 0x7F) sz++;
		}
		return sz;
	}
	
	public void mapByAddress(Map<Integer, NUSALSeqCommand> map){
		if(map == null) return;
		map.put(this.getAddress(), this);
	}

	public void dechunkReference(){
		NUSALSeqCommand ref = getBranchTarget();
		if(ref == null) return;
		if(ref.isChunk()){
			NUSALSeqCommand head = ref.getChunkHead();
			if(head == null) return; //Weird? Empty chunk?
			setReference(head);
			if(head.getLabel() == null) head.setLabel(ref.getLabel());
		}
	}
	
	protected String paramsToString(){
		//Defaults to all decimal
		if(params == null) return null;
		if(params.length < 1) return null;
		String str = "";
		for(int i = 0; i < params.length; i++){
			if(i != 0) str += " ";
			str += Integer.toString(params[i]);
		}
		return str;
	};
	
	public String toString(){
		String cmdname = "<NULL>";
		if(command != null) cmdname = command.name();
		String params = paramsToString();
		if(params != null && !params.isEmpty()) return cmdname + " " + params;
		return cmdname;
	}
	
	protected StringBuilder toMMLCommand_child(){
		StringBuilder sb = new StringBuilder(256);
		String cmdname = "<NULL>";
		if(command != null) cmdname = command.toString();
		String params = paramsToString();
		sb.append(cmdname);
		if(params != null && !params.isEmpty()){
			sb.append(' ');
			sb.append(params);
		}
		return sb;
	}
	
	public String toMMLCommand(){
		return toMMLCommand(false);
	}
	
	public String toMMLCommand(boolean comment_addr){
		StringBuilder sb = toMMLCommand_child();
		if(comment_addr){
			sb.append(" ; 0x");
			sb.append(String.format("%04x", getAddress()));
			if(tick_addr >= 0){
				sb.append(", tick ");
				sb.append(tick_addr);
			}
		}
		return sb.toString();
	}
	
	public byte[] serializeMe(){
		byte[] bytes = null;
		int i = 0;
		switch(command.getSerializationType()){
		case SERIALFMT_DUMMY: return null;
		case SERIALFMT_: bytes = new byte[]{command.getBaseCommand()}; break;
		case SERIALFMT_L:
			i = Byte.toUnsignedInt(command.getBaseCommand());
			i += params[0];
			bytes = new byte[]{(byte)i};
			break;
		case SERIALFMT_1:
			bytes = new byte[]{command.getBaseCommand(), (byte)params[0]};
			break;
		case SERIALFMT_L1:
			i = Byte.toUnsignedInt(command.getBaseCommand());
			i += params[0];
			bytes = new byte[]{(byte)i, (byte)params[1]};
			break;
		case SERIALFMT_2:
			bytes = new byte[3];
			bytes[0] = command.getBaseCommand();
			bytes[1] = (byte)((params[0] >>> 8) & 0xFF);
			bytes[2] = (byte)(params[0] & 0xFF);
			break;
		case SERIALFMT_L2:
			bytes = new byte[3];
			i = Byte.toUnsignedInt(command.getBaseCommand());
			i += params[0];
			bytes[0] = (byte)i;
			bytes[1] = (byte)((params[1] >>> 8) & 0xFF);
			bytes[2] = (byte)(params[1] & 0xFF);
			break;
		case SERIALFMT_11:
			bytes = new byte[]{command.getBaseCommand(), (byte)params[0], (byte)params[1]};
			break;
		case SERIALFMT_12:
			bytes = new byte[4];
			bytes[0] = command.getBaseCommand();
			bytes[1] = (byte)params[0];
			bytes[2] = (byte)((params[1] >>> 8) & 0xFF);
			bytes[3] = (byte)(params[1] & 0xFF);
			break;
		case SERIALFMT_21:
			bytes = new byte[4];
			bytes[0] = command.getBaseCommand();
			bytes[1] = (byte)((params[0] >>> 8) & 0xFF);
			bytes[2] = (byte)(params[0] & 0xFF);
			bytes[3] = (byte)params[1];
			break;
		case SERIALFMT_L11:
			bytes = new byte[3];
			i = Byte.toUnsignedInt(command.getBaseCommand());
			i += params[0];
			bytes[0] = (byte)i;
			bytes[1] = (byte)params[1];
			bytes[2] = (byte)params[2];
			break;
		case SERIALFMT_L12:
			bytes = new byte[4];
			i = Byte.toUnsignedInt(command.getBaseCommand());
			i += params[0];
			bytes[0] = (byte)i;
			bytes[1] = (byte)params[1];
			bytes[2] = (byte)((params[2] >>> 8) & 0xFF);
			bytes[3] = (byte)(params[2] & 0xFF);
			break;
		case SERIALFMT_111:
			bytes = new byte[]{command.getBaseCommand(), (byte)params[0], (byte)params[1], (byte)params[2]};
			break;
		case SERIALFMT_V:
			if(params[0] > 0x7f){
				bytes = new byte[3];
				bytes[0] = command.getBaseCommand();
				bytes[1] = (byte)((params[0] >>> 8) | 0x80);
				bytes[2] = (byte)(params[0] & 0xFF);
			}
			else{
				bytes = new byte[]{command.getBaseCommand(), (byte)params[0]};
			}
			break;
		case SERIALFMT_NOTE:
			if(command == NUSALSeqCmdType.PLAY_NOTE_NTVG){
				if(params[1] > 0x7f){
					bytes = new byte[5];
					bytes[1] = (byte)((params[1] >>> 8) | 0x80);
					bytes[2] = (byte)(params[1] & 0xFF);
					bytes[3] = (byte)params[2];
					bytes[4] = (byte)params[3];
				}
				else{
					bytes = new byte[4];
					bytes[1] = (byte)params[1];
					bytes[2] = (byte)params[2];
					bytes[3] = (byte)params[3];
				}
				bytes[0] = (byte)params[0];
			}
			else if(command == NUSALSeqCmdType.PLAY_NOTE_NTV){
				if(params[1] > 0x7f){
					bytes = new byte[4];
					bytes[1] = (byte)((params[1] >>> 8) | 0x80);
					bytes[2] = (byte)(params[1] & 0xFF);
					bytes[3] = (byte)params[2];
				}
				else{
					bytes = new byte[3];
					bytes[1] = (byte)params[1];
					bytes[2] = (byte)params[2];
				}
				bytes[0] = (byte)(params[0] + 0x40);
			}
			else if(command == NUSALSeqCmdType.PLAY_NOTE_NVG){
				bytes = new byte[3];
				bytes[0] = (byte)(params[0] + 0x80);
				bytes[1] = (byte)params[1];
				bytes[2] = (byte)params[2];
			}
			else if(command == NUSALSeqCmdType.L_SHORTNOTE){
				bytes = new byte[1];
				bytes[0] = (byte)(params[0]);
			}
			break;
		case SERIALFMT_CPARAMS:
			bytes = new byte[9];
			bytes[0] = command.getBaseCommand();
			for(int j = 0; j < 8; j++){
				bytes[j+1] = (byte)params[j];
			}
			break;
		case SERIALFMT_COPYFILTER:
			bytes = new byte[2];
			bytes[0] = command.getBaseCommand();
			i = (params[0] & 0xf) << 4;
			i |= (params[1] & 0xf);
			bytes[1] = (byte)i;
			break;
		}
		return bytes;
	}
	
	public int serializeTo(OutputStream stream) throws IOException{
		byte[] bytes = serializeMe();
		if(bytes == null) return 0;
		stream.write(bytes);
		return bytes.length;
	}
	
	public int serializeTo(FileBuffer buffer){
		byte[] bytes = serializeMe();
		if(bytes == null) return 0;
		for(int i = 0; i < bytes.length; i++) buffer.addToFile(bytes[i]);
		return bytes.length;
	}
	
	public boolean equals(Object o){
		return this == o;
	}
	
}
