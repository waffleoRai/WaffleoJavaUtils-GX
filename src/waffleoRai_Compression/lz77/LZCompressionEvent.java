package waffleoRai_Compression.lz77;

import java.io.IOException;
import java.io.Writer;

public class LZCompressionEvent {

	private boolean is_ref = false;
	private int copy_amt = 0;
	
	private long ref_pos = 0L;
	private long enc_pos = 0L;
	private long comp_pos = -1L;
	
	private int ctrlBytePos = -1;
	
	public boolean isReference(){return is_ref;}
	public int getCopyAmount(){return copy_amt;}
	public long getRefPosition(){return ref_pos;}
	public long getPlaintextPosition(){return enc_pos;}
	public long getCompressedPosition(){return comp_pos;}
	public int getControlBytePos(){return ctrlBytePos;}
	
	public void setIsReference(boolean b){is_ref = b;}
	public void setCopyAmount(int val){copy_amt = val;}
	public void setRefPosition(long val){ref_pos = val;}
	public void setPlaintextPosition(long val){enc_pos = val;}
	public void setCompressedPosition(long val){comp_pos = val;}
	public void setControlBytePos(int val) {ctrlBytePos = val;}
	
	public void printTo(Writer writer) throws IOException{
		if(comp_pos >= 0){
			writer.write(String.format("[0x%08x -> 0x%08x]\t", comp_pos, enc_pos));
		}
		else{
			writer.write(String.format("[0x%08x]\t", enc_pos));
		}

		if(is_ref){
			writer.write("BCPY " + copy_amt + " from 0x" + Long.toHexString(ref_pos) + " (Back 0x" + Long.toHexString(enc_pos - ref_pos) + ")");
		}
		else{
			writer.write("LIT " + copy_amt);
		}
	}
	
	public void printToNoOffsets(Writer writer) throws IOException{
		if(is_ref){
			writer.write("BCPY " + copy_amt + " from 0x" + Long.toHexString(ref_pos) + " (Back 0x" + Long.toHexString(enc_pos - ref_pos) + ")");
		}
		else{
			writer.write("LIT " + copy_amt);
		}
	}
	
	public boolean equals(Object o) {
		if(o == null) return false;
		if(o == this) return true;
		if(!(o instanceof LZCompressionEvent)) return false;
 		
		LZCompressionEvent other = (LZCompressionEvent)o;
		if(other.is_ref != is_ref) return false;
		if(other.copy_amt != copy_amt) return false;
		if(other.ref_pos != ref_pos) return false;
		if(other.enc_pos != enc_pos) return false;
		
		return true;
	}
	
	
}
