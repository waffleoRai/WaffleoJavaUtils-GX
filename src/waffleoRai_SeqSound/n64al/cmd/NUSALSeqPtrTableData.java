package waffleoRai_SeqSound.n64al.cmd;

import waffleoRai_SeqSound.n64al.NUSALSeqCommand;
import waffleoRai_SeqSound.n64al.NUSALSeqCommands;
import waffleoRai_SeqSound.n64al.NUSALSeqDataType;

public class NUSALSeqPtrTableData extends NUSALSeqDataCommand{

	private NUSALSeqCommand[] references;
	private int[] offsets; //Offsets from references.
	private int lyr_target = -1;
	private boolean try_parse_targets = true; //Will read as instruction pointers by default.
	
	public NUSALSeqPtrTableData(NUSALSeqDataType data_type, int units) {
		super(data_type, units);
		references = new NUSALSeqCommand[units];
		offsets = new int[units];
	}
	
	public NUSALSeqCommand getReference(int tbl_idx){
		if(tbl_idx < 0 || tbl_idx >= references.length) return null;
		return references[tbl_idx];
	}
	
	public int getReferenceOffset(int tbl_idx){
		if(tbl_idx < 0 || tbl_idx >= references.length) return -1;
		return offsets[tbl_idx];
	}
	
	public void setReference(int tbl_idx, NUSALSeqCommand cmd){
		if(tbl_idx < 0 || tbl_idx >= references.length) return;
		if(references[tbl_idx] != null){
			references[tbl_idx].removeReferee(this);
		}
		references[tbl_idx] = cmd;
		if(cmd != null) {
			cmd.addReferee(this);
			offsets[tbl_idx] = this.getDataValue(tbl_idx, false) - cmd.getAddress();
		}
		else offsets[tbl_idx] = 0;
	}
	
	public void removeReference(NUSALSeqCommand target) {
		if(references == null) return;
		for(int i = 0; i < references.length; i++) {
			if(references[i] == target) {
				references[i] = null;
				offsets[i] = 0;
			}
		}
	}
	
	public boolean isLayerContext(){return lyr_target >= 0;}
	
	public void setLayer(int value){lyr_target = value;}
	public int getLayer(){return lyr_target;}
	
	public boolean readAsSubPointers(){return this.try_parse_targets;}
	public void setReadAsSubPointers(boolean b){this.try_parse_targets = b;}
	
	public STSResult storeToSelf(int offset, byte value){
		if(data == null) return STSResult.INVALID;
		if(offset < 0 || offset >= data.length) return STSResult.OUTSIDE;
		data[offset] = value;
		return STSResult.RELINK;
	}
	
	public STSResult storePToSelf(int offset, short value){
		if(data == null) return STSResult.INVALID;
		if(offset < 0 || offset >= data.length) return STSResult.OUTSIDE;
		if((offset & 0x1) != 0) return STSResult.INVALID;
		int vali = Short.toUnsignedInt(value);
		data[offset] = (byte)((vali >>> 8) & 0xff);
		data[offset + 1] = (byte)(vali & 0xff);
		return STSResult.RELINK;
	}
	
	public void reallocate(int new_size_bytes){
		resize(new_size_bytes >>> 1);
	}
	
	public void resize(int target_size){
		if(target_size < 1) return;
		int current_size = references.length;
		if(target_size == current_size) return;
		NUSALSeqCommand[] rtemp = new NUSALSeqCommand[target_size];
		byte[] dtemp = new byte[target_size << 1];
		
		int cpysize = target_size < current_size?target_size:current_size;
		int j = 0;
		for(int i = 0; i < cpysize; i++){
			rtemp[i] = references[i];
			dtemp[j] = data[j++]; dtemp[j] = data[j++];
		}
		references = rtemp;
		data = dtemp;
	}
	
	public int getUnitCount(){
		return references.length;
	}
	
	public int getSizeInBytes(){
		return references.length << 1;
	}
	
	public byte[] serializeMe(){
		int j = 0;
		for(int i = 0; i < references.length; i++){
			if(references[i] != null){
				int addr = references[i].getAddress();
				data[j++] = (byte)((addr >>> 8) & 0xff);
				data[j++] = (byte)(addr & 0xff);
			}
			else{
				data[j++] = 0;
				data[j++] = 0;
			}
		}
		return data;
	}
	
	protected String paramsToString(int syntax){
		//TODO Eventually update to account for other syntaxes
		StringBuilder sb = new StringBuilder(128 + (data.length << 1));
		sb.append(dtype.getMMLString());
		int ptype = dtype.getParamPrintType();
		if(ptype == NUSALSeqCommands.MML_DATAPARAM_TYPE__BUFFER){
			sb.append(" ");
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
				if((references != null) && (i < references.length) && (references[i] != null)) {
					sb.append(references[i].getLabel());
					if(offsets[i] > 0) {
						sb.append("[" + offsets[i] + "]");
					}
					continue;
				}
				
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
		return sb.toString();
	};
	
}
