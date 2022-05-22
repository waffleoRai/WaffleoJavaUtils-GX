package waffleoRai_SeqSound.n64al.cmd;

import waffleoRai_SeqSound.n64al.NUSALSeq;
import waffleoRai_SeqSound.n64al.NUSALSeqChannel;
import waffleoRai_SeqSound.n64al.NUSALSeqCmdType;
import waffleoRai_SeqSound.n64al.NUSALSeqCommand;
import waffleoRai_SeqSound.n64al.NUSALSeqCommands;
import waffleoRai_SeqSound.n64al.NUSALSeqDataType;
import waffleoRai_SeqSound.n64al.NUSALSeqLayer;

public class NUSALSeqDataCommand extends NUSALSeqCommand{

	protected byte[] data;
	private NUSALSeqDataType dtype;
	
	public NUSALSeqDataCommand(int size) {
		super(NUSALSeqCmdType.DATA_ONLY, (byte)0x00);
		dtype = NUSALSeqDataType.BINARY;
		data = new byte[size];
	}
	
	public NUSALSeqDataCommand(NUSALSeqDataType data_type) {
		super(NUSALSeqCmdType.DATA_ONLY, (byte)0x00);
		if(data_type == null) data_type = NUSALSeqDataType.BINARY;
		dtype = data_type;
		int size = dtype.getTotalSize();
		if(size <= 0) size = 16;
		data = new byte[size];
	}
	
	public NUSALSeqDataCommand(NUSALSeqDataType data_type, int units) {
		super(NUSALSeqCmdType.DATA_ONLY, (byte)0x00);
		if(data_type == null) data_type = NUSALSeqDataType.BINARY;
		dtype = data_type;
		int size = dtype.getTotalSize();
		if(size > 0) data = new byte[size];
		else{
			data = new byte[units * dtype.getUnitSize()];
		}
	}
	
	public boolean doCommand(NUSALSeq sequence){
		throw new UnsupportedOperationException("Target does not contain executable data.");
	}
	
	public boolean doCommand(NUSALSeqChannel channel){
		throw new UnsupportedOperationException("Target does not contain executable data.");
	}
	
	public boolean doCommand(NUSALSeqLayer voice){
		throw new UnsupportedOperationException("Target does not contain executable data.");
	}
	
	public byte[] getDataArray(){return data;}
	
	public int getDataValue(int u_pos, boolean sign_extend){
		int usize = dtype.getUnitSize();
		int bpos = u_pos * usize;
		if(bpos >= data.length || bpos < 0) return -1;
		
		int val = 0;
		for(int i = 0; i < usize; i++){
			val <<= 8;
			if(bpos < data.length){
				val |= Byte.toUnsignedInt(data[bpos]);
			}
			bpos++;
		}
		
		if(sign_extend){
			int extend_shamt = (4 - usize) << 3;
			if(extend_shamt > 0){
				val <<= extend_shamt;
				val >>= extend_shamt;
			}	
		}
		
		return val;
	}
	
	public void setDataByte(byte b, int pos){
		if(pos < 0 || pos >= data.length) return;
		data[pos] = b;
	}
	
	public void setDataValue(int val, int u_pos){
		int usize = dtype.getUnitSize();
		int bpos = ((u_pos+1) * usize) - 1;
		if(bpos >= data.length || bpos < 0) return;
		
		for(int i = 0; i < usize; i++){
			if(bpos < 0) break;
			data[bpos--] = (byte)(val & 0xff);
			val >>= 8;
		}
	}
	
	public int getSizeInBytes(){
		return data.length;
	}
	
	public byte[] serializeMe(){
		return data;
	}
	
	protected StringBuilder toMMLCommand_child(){
		StringBuilder sb = new StringBuilder(128 + (data.length << 1));
		sb.append("data ");
		sb.append(dtype.getMMLString());
		int ptype = dtype.getParamPrintType();
		if(ptype == NUSALSeqCommands.MML_DATAPARAM_TYPE__BUFFER){
			sb.append(data.length);
		}
		else{
			sb.append(" {");
			int count = dtype.getUnitCount();
			int usize = dtype.getUnitSize();
			String fmtstr = "%0" + (usize << 1) + "x";
			if(count < 0) count = data.length/usize;
			for(int i = 0; i < count; i++){
				if(i > 0) sb.append(", ");
				switch(ptype){
				case NUSALSeqCommands.MML_DATAPARAM_TYPE__DECSIGNED:
					sb.append(getDataValue(i, true));
					break;
				case NUSALSeqCommands.MML_DATAPARAM_TYPE__DECUNSIGNED:
					sb.append(getDataValue(i, false));
					break;
				case NUSALSeqCommands.MML_DATAPARAM_TYPE__HEXUNSIGNED:
				default:
					int val = getDataValue(i, false);
					sb.append(String.format(fmtstr, val));
					break;
				}
			}
			sb.append("}");
		}
		return sb;
	}
	
	public String toString(){
		StringBuilder sb = new StringBuilder(16+(data.length << 1));
		sb.append("_DATA ");
		for(int i = 0; i < data.length; i++){
			sb.append(String.format("%02x ", data[i]));
		}
		return sb.toString();
	}

}
