package waffleoRai_SeqSound.n64al;

import java.util.List;
import java.util.Map;

import waffleoRai_Files.WriterPrintable;

public interface NUSALSeqCommandSource extends WriterPrintable{

	public NUSALSeqCommand getCommandAt(int address);
	public Map<Integer, NUSALSeqCommand> getSeqLevelCommands();
	public List<Integer> getAllAddresses();
	public boolean reparseRegion(int pos, int len);
	List<NUSALSeqCommand> getOrderedCommands();
	
}
