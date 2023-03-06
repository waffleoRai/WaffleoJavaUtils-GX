package waffleoRai_Compression.lz77;

import java.io.IOException;
import java.io.Writer;

public class LZCompressionEvent {

	private boolean is_ref = false;
	private int copy_amt = 0;
	
	private long ref_pos = 0L;
	private long enc_pos = 0L;
	
	public boolean isReference(){return is_ref;}
	public int getCopyAmount(){return copy_amt;}
	public long getRefPosition(){return ref_pos;}
	public long getPlaintextPosition(){return enc_pos;}
	
	public void setIsReference(boolean b){is_ref = b;}
	public void setCopyAmount(int val){copy_amt = val;}
	public void setRefPosition(long val){ref_pos = val;}
	public void setPlaintextPosition(long val){enc_pos = val;}
	
	public void printTo(Writer writer) throws IOException{
		writer.write(String.format("[0x%08x]\t", enc_pos));
		if(is_ref){
			writer.write("BCPY " + copy_amt + " from 0x" + Long.toHexString(ref_pos));
		}
		else{
			writer.write("LIT " + copy_amt);
		}
	}
	
	
}
