package waffleoRai_SeqSound.n64al.cmd;

import waffleoRai_SeqSound.n64al.NUSALSeq;
import waffleoRai_SeqSound.n64al.NUSALSeqChannel;
import waffleoRai_SeqSound.n64al.NUSALSeqCmdType;
import waffleoRai_SeqSound.n64al.NUSALSeqCommand;
import waffleoRai_SeqSound.n64al.NUSALSeqLayer;

public class NUSALSeqDataCommand extends NUSALSeqCommand{

	private byte[] data;
	
	public NUSALSeqDataCommand(int size) {
		super(NUSALSeqCmdType.DATA_ONLY, (byte)0x00);
		data = new byte[size];
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
	
	public int getSizeInBytes(){
		return data.length;
	}
	
	public byte[] serializeMe(){
		return data;
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
