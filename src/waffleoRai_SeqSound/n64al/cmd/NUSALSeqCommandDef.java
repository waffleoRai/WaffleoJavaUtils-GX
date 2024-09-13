package waffleoRai_SeqSound.n64al.cmd;

import waffleoRai_SeqSound.n64al.NUSALSeq;
import waffleoRai_SeqSound.n64al.NUSALSeqCmdType;
import waffleoRai_SeqSound.n64al.NUSALSeqCommands;

public class NUSALSeqCommandDef {
	
	private byte cmdMin;
	private byte cmdMax;
	
	private int context = NUSALSeqCommands.CMDCTX_UNK; //Pseudoenum
	private int serialType; //Pseudoenum
	private int paramCount;
	private int minSize;
	private int flags;
	
	private String mneZeqer;
	private String mneSeq64;
	private String mneZeldaRet;
	
	private NUSALSeqCmdType enValue = NUSALSeqCmdType.DATA_ONLY;
	
	public NUSALSeqCommandDef() {}
	
	/*----- Getters -----*/
	
	public byte getMinCommand() {return cmdMin;}
	public byte getMaxCommand() {return cmdMax;}
	public int getContext() {return context;}
	public int getSerialType() {return serialType;}
	public int getParamCount() {return paramCount;}
	public int getMinSize() {return minSize;}
	public int getFlags() {return flags;}
	
	public String getMnemonicZeqer() {return mneZeqer;}
	public String getMnemonicSeq64() {return mneSeq64;}
	public String getMnemonicZeldaRet() {return mneZeldaRet;}
	
	public String getMnemonic(int syntax) {
		switch(syntax) {
		case NUSALSeq.SYNTAX_SET_ZEQER: return mneZeqer;
		case NUSALSeq.SYNTAX_SET_SEQ64: return mneSeq64;
		case NUSALSeq.SYNTAX_SET_ZELDARET: return mneZeldaRet;
		}
		return null;
	}
	
	public NUSALSeqCmdType getFunctionalType() {return enValue;}
	
	public boolean flagsSet(int flagMask) {
		return (flags & flagMask) != 0;
	}
	
	public boolean hasVariableLength() {
		return (flags & NUSALSeqCommands.FLAG_HASVLQ) != 0;
	}
	
	public int getVLQIndex() {
		int val = (flags >>> 4) & 0x3;
		return val;
	}
	
	public int getNoteType() {
		if(context == NUSALSeqCommands.CMDCTX_LAYER) {
			int min = Byte.toUnsignedInt(cmdMin);
			if(min == 0x00) return NUSALSeqCommands.NOTETYPE_DVG;
			if(min == 0x40) return NUSALSeqCommands.NOTETYPE_DV;
			if(min == 0x80) return NUSALSeqCommands.NOTETYPE_VG;
		}
		else if(context == NUSALSeqCommands.CMDCTX_SHORTNOTES) {
			int min = Byte.toUnsignedInt(cmdMin);
			if(min == 0x00) return NUSALSeqCommands.NOTETYPE_DVG_SHORT;
			if(min == 0x40) return NUSALSeqCommands.NOTETYPE_DV_SHORT;
			if(min == 0x80) return NUSALSeqCommands.NOTETYPE_VG_SHORT;
		}
		return NUSALSeqCommands.NOTETYPE_NONE;
	}
	
	
	
	/*----- Setters -----*/
	
	public void setMinCommand(byte val) {cmdMin = val;}
	public void setMaxCommand(byte val) {cmdMax = val;}
	public void setContext(int val) {context = val;}
	public void setSerialType(int val) {serialType = val;}
	public void setParamCount(int val) {paramCount = val;}
	public void setMinSize(int val) {minSize = val;}
	public void setFlags(int val) {flags = val;}
	
	public void setMnemonicZeqer(String val) {mneZeqer = val;}
	public void setMnemonicSeq64(String val) {mneSeq64 = val;}
	public void setMnemonicZeldaRet(String val) {mneZeldaRet = val;}
	
	public void setFunctionalType(NUSALSeqCmdType val) {enValue = val;}
	
	/*----- Misc. -----*/
	
	private static NUSALSeqCommandDef staticChunkDummy;
	private static NUSALSeqCommandDef staticDataDummy;
	private static NUSALSeqCommandDef staticUnresolvedDummy;
	
	public static NUSALSeqCommandDef getChunkDummyDef() {
		if(staticChunkDummy == null) {
			staticChunkDummy = new NUSALSeqCommandDef();
			staticChunkDummy.enValue = NUSALSeqCmdType.MULTI_EVENT_CHUNK;
		}
		return staticChunkDummy;
	}
	
	public static NUSALSeqCommandDef getDataDummyDef() {
		if(staticDataDummy == null) {
			staticDataDummy = new NUSALSeqCommandDef();
			staticDataDummy.enValue = NUSALSeqCmdType.DATA_ONLY;
		}
		return staticDataDummy;
	}
	
	public static NUSALSeqCommandDef getUnresolvedDummyDef() {
		if(staticUnresolvedDummy == null) {
			staticUnresolvedDummy = new NUSALSeqCommandDef();
			staticUnresolvedDummy.enValue = NUSALSeqCmdType.UNRESOLVED_LINK;
		}
		return staticUnresolvedDummy;
	}

}
