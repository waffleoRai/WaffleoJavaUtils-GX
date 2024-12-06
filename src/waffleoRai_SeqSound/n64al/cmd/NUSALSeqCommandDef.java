package waffleoRai_SeqSound.n64al.cmd;

import waffleoRai_SeqSound.n64al.NUSALSeq;
import waffleoRai_SeqSound.n64al.NUSALSeqCmdType;
import waffleoRai_SeqSound.n64al.NUSALSeqCommand;
import waffleoRai_SeqSound.n64al.NUSALSeqCommands;

public class NUSALSeqCommandDef {
	
	private static final int FMTTYPE_STR = 0;
	private static final int FMTTYPE_REF = 1; //aka symbol
	private static final int FMTTYPE_DECNUM = 2;
	private static final int FMTTYPE_HEXNUM = 3;
	
	private byte cmdMin;
	private byte cmdMax;
	
	private int context = NUSALSeqCommands.CMDCTX_UNK; //Pseudoenum
	private int serialType; //Pseudoenum
	private int paramCount;
	private int minSize;
	private int flags;
	private String mmlArgFmt;
	
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
	public String getMMLArgFormat() {return mmlArgFmt;}
	
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
	public void setMMLArgFormat(String val) {mmlArgFmt = val;}
	
	public void setMnemonicZeqer(String val) {mneZeqer = val;}
	public void setMnemonicSeq64(String val) {mneSeq64 = val;}
	public void setMnemonicZeldaRet(String val) {mneZeldaRet = val;}
	
	public void setFunctionalType(NUSALSeqCmdType val) {enValue = val;}
	
	/*----- Parsing/Serialization -----*/
	
	private static class FmtPlaceholder{
		public int minDig = -1;
		public boolean zeroPad = false;
		public boolean isList = false;
		public int type = 0;
		
		public int lenCh = 0; //Including % sign
	}
	
	private static class VarParseResults{
		public int[] results;
		public int len;
	}
	
	private FmtPlaceholder parsePlaceholder(String s, int charPos) {
		char ch = s.charAt(charPos++);
		if(ch != '%') return null;
		
		int slen = s.length();
		FmtPlaceholder ph = new FmtPlaceholder();
		ph.lenCh++;
		boolean term = false;
		boolean nzdig = false;
		for(int i = charPos; i < slen; i++) {
			if(term) break;
			ch = s.charAt(i);
			if(ch > '0' && ch <= '9') {
				ph.minDig *= 10;
				ph.minDig += (int)(ch - '0');
				nzdig = true;
			}
			else {
				switch(ch) {
				case '0':
					if(!nzdig) ph.zeroPad = true;
					else ph.minDig *= 10;
					break;
				case 'd':
					ph.type = FMTTYPE_DECNUM;
					if(!ph.isList) term = true;
					break;
				case 'x':
				case 'X':
					ph.type = FMTTYPE_HEXNUM;
					if(!ph.isList) term = true;
					break;
				case 'r':
					ph.type = FMTTYPE_REF;
					if(!ph.isList) term = true;
					break;
				case 's':
					ph.type = FMTTYPE_STR;
					if(!ph.isList) term = true;
					break;
				case '{':
					ph.isList = true;
					break;
				case '}':
					term = true;
					break;
				case '%':
				default:
					//Assumed to be literal or start of next placeholder...
					return ph;
				}
			}
			ph.lenCh++;
		}
		
		return ph;
	}
	
	private int readDecVar(String s, int charPos, VarParseResults res) {
		int ival = 0;
		char ch = s.charAt(charPos);
		boolean sign = false;
		int slen = s.length();
		
		if(ch == '-') {
			sign = true;
			charPos++;
			res.len++;
		}
		while(charPos < slen) {
			ch = s.charAt(charPos++);
			if(ch >= '0' && ch <= '9') {
				res.len++;
				ival *= 10;
				ival += (int)(ch - '0');
			}
			else break;
		}
		if(sign) ival *= -1;
		return ival;
	}
	
	private int readHexVar(String s, int charPos, VarParseResults res) {
		int ival = 0;
		char ch = s.charAt(charPos);
		boolean sign = false;
		int slen = s.length();
		
		if(ch == '-') {
			sign = true;
			charPos++;
			res.len++;
		}
		while(charPos < slen) {
			ch = s.charAt(charPos++);
			if(ch >= '0' && ch <= '9') {
				res.len++;
				ival <<= 4;
				ival += (int)(ch - '0');
			}
			else {
				ch = Character.toUpperCase(ch);
				if(ch >= 'A' && ch <= 'F') {
					res.len++;
					ival <<= 4;
					ival += (int)(ch - 'A' + 10);
				}
				else break;
			}
		}
		if(sign) ival *= -1;
		return ival;
	}
	
	private VarParseResults parsePlaceholderList(String s, int charPos, FmtPlaceholder fmt) {
		char ch = s.charAt(charPos);
		if(ch != '{') return null;
		s = s.substring(charPos+1);
		int listend = s.indexOf('}');
		if(listend < 0) return null;
		s = s.substring(0, listend);
		
		VarParseResults res = new VarParseResults();

		String[] spl = s.split(",");
		res.results = new int[spl.length];
		for(int i = 0; i < spl.length; i++) {
			String e = spl[i].trim();
			switch(fmt.type) {
			case FMTTYPE_STR:
			case FMTTYPE_REF:
				res.results[i] = -1;
				break;
			case FMTTYPE_DECNUM:
				res.results[i] = readDecVar(e, 0, res);
				break;
			case FMTTYPE_HEXNUM:
				res.results[i] = readHexVar(e, 0, res);
				break;
			}
		}
		
		//Override length
		res.len = s.length() + 2; //Include brackets
		return res;
	}
	
	private VarParseResults parsePlaceholderVar(String s, int charPos, FmtPlaceholder fmt) {
		if(fmt.isList) return parsePlaceholderList(s, charPos, fmt);
		VarParseResults res = new VarParseResults();
		res.results = new int[1];

		switch(fmt.type) {
		case FMTTYPE_STR:
		case FMTTYPE_REF:
			res.results[0] = -1;
			break;
		case FMTTYPE_DECNUM:
			res.results[0] = readDecVar(s, charPos, res);
			break;
		case FMTTYPE_HEXNUM:
			res.results[0] = readHexVar(s, charPos, res);
			break;
		}
		
		return res;
	}
	
	public int[][] parseMMLArgs(String[] args) {
		if(args == null) return null;
		if(mmlArgFmt == null || mmlArgFmt.isEmpty()) return null;
		if(mmlArgFmt.equals(";")) return null;
		
		String[] or = mmlArgFmt.split("|");
		for(int i = 0; i < or.length; i++) {
			String[] expFields = or[i].split(";");
			int[][] out = new int[expFields.length][];
			
			//Does it fit this format?
			boolean mismatch = false;
			for(int j = 0; j < out.length; j++) {
				int flen = expFields[j].length();
				int pr = 0; int pq = 0;
				FmtPlaceholder nowph = null;
				while(pr < flen) {
					char ch = expFields[j].charAt(pr);
					if(ch == '%') {
						nowph = parsePlaceholder(expFields[j], pr);
						if(nowph != null) {
							VarParseResults res = parsePlaceholderVar(args[j], pq, nowph);
							out[j] = res.results;
							pq += res.len;
							pr += nowph.lenCh;
						}
						else {
							mismatch = true;
							break;
						}
					}
					else {
						//Literal.
						char och = args[j].charAt(pq++);
						if(och != ch) {
							mismatch = true;
							break;
						}
						pr++;
					}
				}
				if(mismatch) break;
			}	
			if(!mismatch) return out;
		}
		
		return null;
	}
	
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
			staticDataDummy.context = NUSALSeqCommands.CMDCTX_ANY;
			staticDataDummy.minSize = 0;
			staticDataDummy.mmlArgFmt = "{%d}";
			staticDataDummy.mneSeq64 = "data";
			staticDataDummy.mneZeldaRet = "data";
			staticDataDummy.mneZeqer = "data";
			staticDataDummy.paramCount = 1;
			staticDataDummy.serialType = NUSALSeqCommand.SERIALFMT_DATA;
			staticDataDummy.flags = NUSALSeqCommands.FLAG_ADDRREF | NUSALSeqCommands.FLAG_DATAONLY | NUSALSeqCommands.FLAG_CHVALID | 
					NUSALSeqCommands.FLAG_LYRVALID | NUSALSeqCommands.FLAG_SEQVALID | NUSALSeqCommands.FLAG_REFOVERLAP;
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
