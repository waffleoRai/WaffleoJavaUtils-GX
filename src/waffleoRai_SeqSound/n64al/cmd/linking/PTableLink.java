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
	private int limitAddr = -1; //Highest possible address of table end.
	
	private ParseContext codeCtx = null; //Null if not used for code
	private NUSALSeqCommand stsTarget = null; //Null if not used for sts/stps
	
	public PTableLink(NUSALSeqReadContext readerState, int ptblAddr, NUSALSeqCommand referee) {
		sourceMaxAddr = (int)readerState.data.getFileSize();
		limitAddr = sourceMaxAddr;
		
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
		ptable.addContextFlagsFrom(referee);
		
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
	public int getEndLimitAddress() {return limitAddr;}
	
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
	public void setEndLimitAddress(int value) {limitAddr = value;}
	
	public ParseContext determineTargetContext() {
		ParseContext ctxt = ParseContext.fromCommand(ptable);
		
		switch(tableType) {
		case NUSALSeqLinking.P_TYPE_SEQ_CODE:
			ctxt.setAsSeq();
			break;
		case NUSALSeqLinking.P_TYPE_CH_CODE:
			if(ptable.isChannelCommand()) ctxt = ParseContext.fromCommand(ptable);
			else {
				//Is traced command a "set channel" override?
				//If not, just set to channel 0.
				if(stsTarget != null) {
					if((stsTarget.getFunctionalType() == NUSALSeqCmdType.CHANNEL_OFFSET_REL) ||
							(stsTarget.getFunctionalType() == NUSALSeqCmdType.CHANNEL_OFFSET)) {
						ctxt.setChannel(stsTarget.getParam(0));
					}
					else ctxt.setChannel(0);
				}
				else ctxt.setChannel(0);
			}
			break;
		case NUSALSeqLinking.P_TYPE_LYR_CODE:
			if(ptable.isLayerCommand()) ctxt = ParseContext.fromCommand(ptable);
			else {
				if(stsTarget != null) {
					if((stsTarget.getFunctionalType() == NUSALSeqCmdType.VOICE_OFFSET_REL) ||
							(stsTarget.getFunctionalType() == NUSALSeqCmdType.VOICE_OFFSET)) {
						ctxt.setLayer(ctxt.getChannel(), stsTarget.getParam(0));
					}
					else {
						ctxt.setLayer(ctxt.getChannel(), 0);
					}
				}
				else {
					ctxt.setLayer(ctxt.getChannel(), 0);
				}
			}
			break;
		}
		
		return ctxt;
	}
	
	public void adjustSize(NUSALSeqReadContext readerState) {
		int myAddr = ptable.getAddress();
		int maxAddr = limitAddr;
		
		NUSALSeqCommand next = null;
		int tryAddr = myAddr;
		while(++tryAddr < maxAddr) {
			next = readerState.cmdmap.get(tryAddr);
			if(next != null) {
				maxAddr = tryAddr;
				break;
			}
		}
		if(maxAddr < limitAddr) limitAddr = maxAddr;
		
		ptable.resize((maxAddr - myAddr) >> 1);
	}
	
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
