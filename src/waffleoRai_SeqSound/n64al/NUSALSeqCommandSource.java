package waffleoRai_SeqSound.n64al;

import java.util.List;
import java.util.Map;

public interface NUSALSeqCommandSource {

	public NUSALSeqCommand getCommandAt(int address);
	public Map<Integer, NUSALSeqCommand> getSeqLevelCommands();
	public List<Integer> getAllAddresses();
	
}
