package waffleoRai_SeqSound.n64al.cmd.linking;

import java.util.List;

import waffleoRai_SeqSound.n64al.NUSALSeqCmdType;
import waffleoRai_SeqSound.n64al.NUSALSeqCommand;
import waffleoRai_SeqSound.n64al.cmd.DataCommands;
import waffleoRai_SeqSound.n64al.cmd.NUSALSeqDataCommand;
import waffleoRai_SeqSound.n64al.cmd.NUSALSeqPtrTableData;
import waffleoRai_SeqSound.n64al.cmd.NUSALSeqReader.ParseContext;
import waffleoRai_Utils.BufferReference;

public class PTableLink {
	
	private NUSALSeqPtrTableData ptable;
	private IndirectLink[] contents;
	private int tableType = NUSALSeqLinking.P_TYPE_UNK;
	
	private int sourceMaxAddr = -1; //Source data size
	
	private ParseContext codeCtx = null; //Null if not used for code
	private NUSALSeqCommand stsTarget = null; //Null if not used for sts/stps
	
	public PTableLink(NUSALSeqReadContext readerState, int ptblAddr, NUSALSeqCommand referee) {
		sourceMaxAddr = (int)readerState.data.getFileSize();
		
		NUSALSeqCmdType refereeType = referee.getFunctionalType();
		BufferReference datRead = readerState.data.getReferenceAt(ptblAddr);
		int maxAddr = ptblAddr + 1;
		while(maxAddr < sourceMaxAddr) {
			if(readerState.cmdmap.containsKey(maxAddr)) {
				break;
			}
			else maxAddr++;
		}
		
		NUSALSeqDataCommand dcmd = DataCommands.parseData(refereeType, datRead, maxAddr);
		if(!(dcmd instanceof NUSALSeqPtrTableData)) return;
		ptable = (NUSALSeqPtrTableData)dcmd;
		ptable.addReferee(referee);
		ptable.setAddress(ptblAddr);
		
		//Refine size by trimming to only valid pointers.
		int nowSize = ptable.getUnitCount();
		int newSize = nowSize;
		for(int i = 0; i < nowSize; i++) {
			int p = ptable.getDataValue(i, false);
			if(p < 0 || p >= sourceMaxAddr) {
				newSize = i;
				break;
			}
		}
		if(newSize < nowSize) {
			ptable.resize(newSize);
		}
		
		contents = new IndirectLink[newSize];
	}

	public NUSALSeqPtrTableData getPTable() {return ptable;}
	public int getTableType() {return tableType;}
	public ParseContext getCodeContext() {return codeCtx;}
	public NUSALSeqCommand getSTSTarget() {return stsTarget;}
	public IndirectLink[] getElementArray() {return contents;}
	
	public NUSALSeqCommand getFirstReferee() {
		if(ptable == null) return null;
		List<NUSALSeqCommand> referees = ptable.getReferees();
		if(referees == null) return null;
		return referees.get(0);
	}
	
	public void addReferee(NUSALSeqCommand ref) {
		ptable.addReferee(ref);
	}
	
	public void setReference(int index, NUSALSeqCommand trg) {
		//TODO
		ptable.setReference(index, trg);
		
		//Can we determine a type from this?
		
	}
	
	public void setTableType(int value) {tableType = value;}
	public void setSTSTarget(NUSALSeqCommand value) {stsTarget = value;}
	
	public void reassessType() {
		if(tableType != NUSALSeqLinking.P_TYPE_UNK) {
			//Table type to elements
			if(contents != null) {
				for(int i = 0; i < contents.length; i++) {
					if(contents[i] != null) {
						contents[i].setLinkType(tableType);
					}
				}
			}
		}
		else {
			//Elements to table type
			if(contents != null) {
				for(int i = 0; i < contents.length; i++) {
					if(contents[i] != null) {
						int ctype = contents[i].getLinkType();
						if(ctype != NUSALSeqLinking.P_TYPE_UNK) {
							tableType = ctype;
							reassessType(); //To update other contents
							break;
						}
					}
				}
			}
		}
	}
	
}
