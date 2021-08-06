package waffleoRai_SeqSound.n64al;

import java.util.Set;
import java.util.TreeSet;

public abstract class NUSALSeqCommand {

	private NUSALSeqCmdType command;
	private byte cmdbyte;
	private int[] params;
	
	private boolean seq_ctx; //Has this command been called by the seq?
	private Set<Integer> ch_ctx; //What channel/voices have called this command?
	//These should be read as bytes - high nyb is channel, low nyb is voice.
	//If channel itself, the low nyb is 0xf
	
	public NUSALSeqCommand(NUSALSeqCmdType cmd, byte cmd_byte){
		cmdbyte = cmd_byte;
		command = cmd;
		if(cmd.getParameterCount() > 0) params = new int[cmd.getParameterCount()];
		ch_ctx = new TreeSet<Integer>();
		seq_ctx = false;
		onInit();
	}
	
	protected void onInit(){}
	
	public NUSALSeqCmdType getCommand(){return command;}
	public byte getCommandByte(){return cmdbyte;}
	
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
	
	public abstract int getSizeInBytes();
	public boolean isBranch(){return false;}
	public boolean isRelativeBranch(){return false;}
	public int getBranchAddress(){return -1;}
	
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
	
}
