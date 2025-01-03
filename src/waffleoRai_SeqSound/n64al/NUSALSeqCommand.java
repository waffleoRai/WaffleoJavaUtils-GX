package waffleoRai_SeqSound.n64al;

import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import waffleoRai_SeqSound.n64al.NUSALSeq.InvalidCommandBookException;
import waffleoRai_SeqSound.n64al.cmd.NUSALSeqCommandDef;
import waffleoRai_SeqSound.n64al.cmd.NUSALSeqReader;
import waffleoRai_SeqSound.n64al.cmd.STSResult;
import waffleoRai_SeqSound.n64al.cmd.SysCommandBook;
import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.FileBuffer;

public abstract class NUSALSeqCommand {
	
	/*----- Constants -----*/
	
	protected static final STSResult[] STS_RES_PRI = 
		{STSResult.FAIL, STSResult.REPARSE, STSResult.INVALID, STSResult.RELINK, STSResult.OKAY, STSResult.OUTSIDE};
	
	public static final int SERIALFMT_DATA = -2;
	public static final int SERIALFMT_DUMMY = -1;
	public static final int SERIALFMT_ = 0; //Just the command byte
	public static final int SERIALFMT_L = 1; //Command byte only - arg0 is low nyb
	public static final int SERIALFMT_1 = 2; //Command byte, 1 one-byte arg
	public static final int SERIALFMT_L1 = 3; //Cmdbyte lonyb + 1 byte
	public static final int SERIALFMT_2 = 4;
	public static final int SERIALFMT_L2 = 5;
	public static final int SERIALFMT_11 = 6;
	public static final int SERIALFMT_12 = 7;
	public static final int SERIALFMT_21 = 8;
	public static final int SERIALFMT_L11 = 9;
	public static final int SERIALFMT_L12 = 10;
	public static final int SERIALFMT_111 = 11;
	public static final int SERIALFMT_V = 12;
	public static final int SERIALFMT_NOTE = 13;
	public static final int SERIALFMT_CPARAMS = 14;
	public static final int SERIALFMT_COPYFILTER = 15;
	public static final int SERIALFMT_SNOTE = 16;
	public static final int SERIALFMT_Lp8 = 17;
	public static final int SERIALFMT_Lp81 = 18;
	public static final int SERIALFMT_Lp82 = 19;
	
	/*----- Instance Variables -----*/
	
	//private NUSALSeqCmdType command;
	private NUSALSeqCommandDef def;
	private byte cmdbyte;
	private int[] params;
	
	private int address;
	private String label;
	private int tick_addr = -1; //Optional
	
	private boolean seq_ctx; //Has this command been called by the seq?
	private Set<Integer> ch_ctx; //What channel/voices have called this command?
	//These should be read as bytes - high nyb is channel, low nyb is voice.
	//If channel itself, the low nyb is 0xf
	
	//Linked list
	private NUSALSeqCommand next_cmd = null;
	private NUSALSeqCommand prev_cmd = null;
	private List<NUSALSeqCommand> referees;
	
	/*----- Init -----*/
	
	public NUSALSeqCommand(NUSALSeqCommandDef cmdDef) {
		this(cmdDef, cmdDef.getMinCommand());
	}
	
	public NUSALSeqCommand(NUSALSeqCommandDef cmdDef, byte cmd_byte){
		cmdbyte = cmd_byte;
		def = cmdDef;
		if(cmdDef.getParamCount() > 0) params = new int[cmdDef.getParamCount()];
		ch_ctx = new TreeSet<Integer>();
		seq_ctx = false;
		onInit();
	}
	
	protected NUSALSeqCommand(NUSALSeqCmdType funcCmd, byte cmd_byte) {
		this(funcCmd, null, cmd_byte);
	}

	protected NUSALSeqCommand(NUSALSeqCmdType funcCmd, NUSALSeqCommandBook book) {
		if(book == null) book = SysCommandBook.getDefaultBook();
		if(book == null) throw new InvalidCommandBookException("NUSALSeqCommand.<init> || Could not retrieve valid command book");
		
		def = book.getCommand(funcCmd);
		if(def == null ) throw new InvalidCommandBookException("NUSALSeqCommand.<init> || Could not retrieve valid command definition for " + funcCmd.name());
		
		if(def.getParamCount() > 0) params = new int[def.getParamCount()];
		ch_ctx = new TreeSet<Integer>();
		seq_ctx = false;
		onInit();	
	}
	
	protected NUSALSeqCommand(NUSALSeqCmdType funcCmd, NUSALSeqCommandBook book, byte cmd_byte) {
		this(funcCmd, book);
		cmdbyte = cmd_byte;
	}
	
	protected void onInit(){}
		
	/*----- Internal Utils -----*/
	
	protected void reallocParamArray(int size){params = new int[size];}
	
	protected int[] restructureCommand(NUSALSeqCommandDef cmdDef, byte cmd_byte){
		int[] oldparams = params;
		cmdbyte = cmd_byte;
		//command = newtype;
		//if(newtype.getParameterCount() > 0) params = new int[newtype.getParameterCount()];
		def = cmdDef;
		if(cmdDef.getParamCount() > 0) params = new int[cmdDef.getParamCount()];
		else params = null;
		return oldparams;
	}
	
	/*----- Getters -----*/
	
	public NUSALSeqCommandDef getCommandDef(){return def;}
	public byte getCommandByte(){return cmdbyte;}
	public NUSALSeqCommand getSubsequentCommand(){return next_cmd;}
	public NUSALSeqCommand getPreviousCommand(){return prev_cmd;}
	public boolean seqUsed(){return seq_ctx;}
	public int getAddress(){return address;}
	public int getTickAddress(){return tick_addr;}
	public String getLabel(){return label;}
	public boolean isBranch(){return false;}
	public boolean isRelativeBranch(){return false;}
	public int getBranchAddress(){return -1;}
	public NUSALSeqCommand getBranchTarget(){return null;}
	public boolean isChunk(){return false;}
	public NUSALSeqCommand getChunkHead(){return this;}
	public boolean isEndCommand(){return false;}
	public int getSizeInTicks(){return 0;}
	
	public NUSALSeqCmdType getFunctionalType() {
		if(def == null) return null;
		return def.getFunctionalType();
	}
	
	public List<NUSALSeqCommand> getReferees(){
		if(referees == null) return null;
		List<NUSALSeqCommand> list = new LinkedList<NUSALSeqCommand>();
		list.addAll(referees);
		return list;
	}
	
	public boolean channelUsed(int ch){
		int check = (ch << 4) | 0xf;
		return ch_ctx.contains(check);
	}
	
	public boolean layerUsed(int ch, int layer){
		int check = (ch << 4) | (layer & 0xf);
		return ch_ctx.contains(check);
	}
	
	public int getParamCount(){
		if(params == null) return 0;
		return params.length;
	}
	
	public int getParam(int idx){
		if(params == null || idx < 0 || idx >= params.length){
			throw new IndexOutOfBoundsException("Parameter at index " + idx + " does not exist");
		}
		return params[idx];
	}
	
	public int getFirstChannelUsed(){
		for(int i = 0; i < 16; i++){
			if(channelUsed(i)) return i;
		}
		return -1;
	}
	
	public int[] getFirstUsed(){
		int[] out = new int[]{-1,-1};
		if(seq_ctx) return out;
		out[0] = getFirstChannelUsed();
		if(out[0] < 0){
			for(int i = 0; i < 16; i++){
				for(int j = 0; j < NUSALSeq.MAX_LAYERS_PER_CHANNEL; j++){
					if(layerUsed(i,j)) {
						out[0] = i;
						out[1] = j;
						break;
					}
				}	
			}
		}
		return out;
	}
	
	public boolean isSeqCommand(){
		return this.seqUsed();
	}
	
	public boolean isChannelCommand(){
		return this.getFirstChannelUsed() >= 0;
	}
	
	public boolean isLayerCommand(){
		for(int i = 0; i < 16; i++){
			for(int j = 0; j < NUSALSeq.MAX_LAYERS_PER_CHANNEL; j++){
				if(this.layerUsed(i, j)) return true;
			}
		}
		return false;
	}
	
	public int getSizeInBytes(){
		int sz = def.getMinSize();
		if(def.hasVariableLength()){
			//Check if need one more byte.
			int varg = params[def.getVLQIndex()];
			if(varg > 0x7F) sz++;
		}
		return sz;
	}
	
	/*----- Setters -----*/
	
	protected void setCommandByte(byte b){cmdbyte = b;}
	public void flagSeqUsed(){seq_ctx = true;}
	public void setReference(NUSALSeqCommand target){}
	public void removeReference(NUSALSeqCommand target) {}
	public void setAddress(int addr){address = addr;}
	public void setTickAddress(int t){tick_addr = t;}
	public void setLabel(String s){label = s;}
	
	protected void setCommandDef(NUSALSeqCommandDef cmdDef){
		def = cmdDef;
		if(def != null) cmdbyte = def.getMinCommand();
	}
		
	public void addReferee(NUSALSeqCommand ref){
		if(referees == null){
			referees = new LinkedList<NUSALSeqCommand>();
		}
		referees.add(ref);
	}
	
	public boolean removeReferee(NUSALSeqCommand ref){
		return referees.remove(ref);
	}
	
	public void setSubsequentCommand(NUSALSeqCommand next){
		next_cmd = next;
		if(next_cmd != null) next_cmd.prev_cmd = this;
	}
		
	public void flagChannelUsed(int ch){
		int check = (ch << 4) | 0xf;
		ch_ctx.add(check);
	}
		
	public void flagLayerUsed(int ch, int layer){
		int check = (ch << 4) | (layer & 0xf);
		ch_ctx.add(check);
	}
	
	public void setParam(int idx, int val){
		if(params == null || idx < 0 || idx >= params.length){
			throw new IndexOutOfBoundsException("Parameter at index " + idx + " does not exist");
		}
		params[idx] = val;
	}
	
	public void setParam(int idx, byte val, boolean signExtend){
		if(params == null || idx < 0 || idx >= params.length){
			throw new IndexOutOfBoundsException("Parameter at index " + idx + " does not exist");
		}
		if(signExtend) params[idx] = (int)val;
		else params[idx] = Byte.toUnsignedInt(val);
	}
	
	public void setParam(int idx, short val, boolean signExtend){
		if(params == null || idx < 0 || idx >= params.length){
			throw new IndexOutOfBoundsException("Parameter at index " + idx + " does not exist");
		}
		if(signExtend) params[idx] = (int)val;
		else params[idx] = Short.toUnsignedInt(val);
	}
		
	public void addContextFlagsFrom(NUSALSeqCommand other){
		if(other == null) return;
		if(other.seq_ctx) this.seq_ctx = true;
		for(int i = 0; i < 16; i++){
			if(other.channelUsed(i)) this.flagChannelUsed(i);
			for(int j = 0; j < 8; j++){
				if(other.layerUsed(i, j)) this.flagLayerUsed(i, j);
			}
		}
	}
	
	/*----- Misc. -----*/
	
	public String genCtxLabelSuggestion(){
		if(seq_ctx) return "seq_block";
		int[] chly = getFirstUsed();
		if(chly[0] >= 0){
			if(chly[1] >= 0){
				return "ch" + String.format("%X", chly[0]) +
						"ly" + chly[1] + "_block";	
			}
			else{
				return "ch" + String.format("%X", chly[0]) + "_block";
			}
		}
		return "lbl";
	}
	
	public boolean doCommand(NUSALSeq sequence){
		throw new UnsupportedOperationException("Command 0x" + String.format("%02x", cmdbyte)
			+ " not supported in sequence context");
	}
	
	public boolean doCommand(NUSALSeqChannel channel){
		throw new UnsupportedOperationException("Command 0x" + String.format("%02x", cmdbyte)
			+ " not supported in channel context");
	}
	
	public boolean doCommand(NUSALSeqLayer voice){
		throw new UnsupportedOperationException("Command 0x" + String.format("%02x", cmdbyte)
			+ " not supported in layer context");
	}
	
	public void mapByAddress(Map<Integer, NUSALSeqCommand> map){
		if(map == null) return;
		map.put(this.getAddress(), this);
	}

	public void dechunkReference(){
		NUSALSeqCommand ref = getBranchTarget();
		if(ref == null) return;
		if(ref.isChunk()){
			NUSALSeqCommand head = ref.getChunkHead();
			if(head == null) return; //Weird? Empty chunk?
			setReference(head);
			if(head.getLabel() == null) head.setLabel(ref.getLabel());
		}
	}
	
	public STSResult storeToSelf(int offset, byte value){
		int mysize = getSizeInBytes();
		if(offset >= mysize) return STSResult.OUTSIDE;
		
		int cmdi = Byte.toUnsignedInt(def.getMinCommand());
		int vali = Byte.toUnsignedInt(value);
		switch(def.getSerialType()){
		case SERIALFMT_DUMMY: return STSResult.FAIL;
		case SERIALFMT_: return STSResult.REPARSE;
		case SERIALFMT_L:
			if((cmdi & 0xf0) == (vali & 0xf0)){
				params[0] = vali & 0xf;
				cmdbyte = value;
				return STSResult.OKAY;
			}
			else return STSResult.REPARSE;
		case SERIALFMT_1:
			if(offset == 0) return STSResult.REPARSE;
			params[0] = (int)value;
			return STSResult.OKAY;
		case SERIALFMT_L1:
			if(offset == 0){
				if((cmdi & 0xf0) == (vali & 0xf0)){
					params[0] = vali & 0xf;
					cmdbyte = value;
					return STSResult.OKAY;
				}
				else return STSResult.REPARSE;
			}
			else{
				params[1] = (int)value;
				return STSResult.OKAY;
			}
		case SERIALFMT_2:
			if(offset == 0) return STSResult.REPARSE;
			if(offset == 1){
				params[0] = (params[0] & 0xff) | (vali << 8);
			}
			else{
				params[0] = (params[0] & 0xff00) | vali;
			}
			return STSResult.OKAY;
		case SERIALFMT_L2:
			if(offset == 0){
				if((cmdi & 0xf0) == (vali & 0xf0)){
					params[0] = vali & 0xf;
					cmdbyte = value;
					return STSResult.OKAY;
				}
				else return STSResult.REPARSE;
			}
			if(offset == 1){
				params[1] = (params[1] & 0xff) | (vali << 8);
			}
			else{
				params[1] = (params[1] & 0xff00) | vali;
			}
			return STSResult.OKAY;
		case SERIALFMT_11:
			if(offset == 0) return STSResult.REPARSE;
			params[offset-1] = vali;
			return STSResult.OKAY;
		case SERIALFMT_12:
			switch(offset){
			case 0: return STSResult.REPARSE;
			case 1:
				params[0] = (int)value;
				return STSResult.OKAY;
			case 2:
				params[1] = (params[1] & 0xff) | (vali << 8);
				return STSResult.OKAY;
			case 3:
				params[1] = (params[1] & 0xff00) | vali;
				return STSResult.OKAY;
			}
		case SERIALFMT_21:
			switch(offset){
			case 0: return STSResult.REPARSE;
			case 1:
				params[0] = (params[0] & 0xff) | (vali << 8);
				return STSResult.OKAY;
			case 2:
				params[0] = (params[0] & 0xff00) | vali;
				return STSResult.OKAY;
			case 3:
				params[1] = (int)value;
				return STSResult.OKAY;
			}
		case SERIALFMT_L11:
			switch(offset){
			case 0: 
				if((cmdi & 0xf0) == (vali & 0xf0)){
					params[0] = vali & 0xf;
					cmdbyte = value;
					return STSResult.OKAY;
				}
				else return STSResult.REPARSE;
			case 1:
				params[1] = (int)value;
				return STSResult.OKAY;
			case 2:
				params[2] = (int)value;
				return STSResult.OKAY;
			}
		case SERIALFMT_L12:
			switch(offset){
			case 0: 
				if((cmdi & 0xf0) == (vali & 0xf0)){
					params[0] = vali & 0xf;
					cmdbyte = value;
					return STSResult.OKAY;
				}
				else return STSResult.REPARSE;
			case 1:
				params[1] = (int)value;
				return STSResult.OKAY;
			case 2:
				params[2] = (params[2] & 0xff) | (vali << 8);
				return STSResult.OKAY;
			case 3:
				params[2] = (params[2] & 0xff00) | vali;
				return STSResult.OKAY;
			}
		case SERIALFMT_111:
			if(offset == 0) return STSResult.REPARSE;
			params[offset-1] = vali;
			return STSResult.OKAY;
		case SERIALFMT_V:
			if(offset == 0) return STSResult.REPARSE;
			if(params[0] > 0x7f){
				//Two byte
				if(offset == 1){
					if(vali < 0x80) return STSResult.INVALID; //Expects high bit to be set.
					params[0] = (params[0] & 0xff) | ((vali & 0x7f) << 8);
					return STSResult.OKAY;
				}
				else if(offset == 2){
					params[0] = (params[0] & 0xff00) | vali;
					return STSResult.OKAY;
				}
				return STSResult.OUTSIDE;
			}
			else{
				//One byte
				if(offset != 1) return STSResult.OUTSIDE;
				if(vali > 0x7f) return STSResult.INVALID;
				params[0] = vali;
				return STSResult.OKAY;
			}
		case SERIALFMT_NOTE:
			if(offset == 0){
				//Can swap as long as same type.
				if(cmdi < 0x40){
					if(vali >= 0x40) return STSResult.INVALID;
				}
				else if(cmdi < 0x80){
					if(vali < 0x40 || vali >= 0x80) return STSResult.INVALID;
				}
				else{
					if(vali < 0x40 || vali >= 0x80) return STSResult.INVALID;
				}
				cmdbyte = value;
				params[0] = vali & 0x3f;
				return STSResult.OKAY;
			}
			else{
				//Need to figure out cmd format...
				if(cmdi < 0x40){
					//ndvg
					if(params[1] > 0x7f){
						if(offset > 2) params[offset-1] = vali;
						else{
							switch(offset){
							case 1:
								if(vali < 0x80) return STSResult.INVALID;
								params[1] = (params[1] & 0xff) | ((vali & 0x7f) << 8);
								break;
							case 2:
								params[1] = (params[1] & 0xff00) | vali;
								break;
							}
						}
					}
					else {
						if(offset == 1){
							if(vali > 0x7f) return STSResult.INVALID;
						}
						params[offset] = vali;
					}
				}
				else if(cmdi < 0x80){
					//ndv
					if(params[1] > 0x7f){
						if(offset > 2) params[offset-1] = vali;
						else{
							switch(offset){
							case 1:
								if(vali < 0x80) return STSResult.INVALID;
								params[1] = (params[1] & 0xff) | ((vali & 0x7f) << 8);
								break;
							case 2:
								params[1] = (params[1] & 0xff00) | vali;
								break;
							}
						}
					}
					else {
						if(offset == 1){
							if(vali > 0x7f) return STSResult.INVALID;
						}
						params[offset] = vali;
					}
				}
				else{
					//nvg
					params[offset] = vali;
				}
				return STSResult.OKAY;
			}
		case SERIALFMT_CPARAMS:
			if(offset == 0) return STSResult.REPARSE;
			params[offset-1] = vali;
			return STSResult.OKAY;
		case SERIALFMT_COPYFILTER:
			if(offset == 0) return STSResult.REPARSE;
			params[0] = (vali >>> 4) & 0xf;
			params[1] = vali & 0xf;
			return STSResult.OKAY;
		}
		
		return STSResult.FAIL;
	}
	
	public STSResult storePToSelf(int offset, short value){
		//Maybe just call storeToSelf for each byte?
		//FAIL > REPARSE > INVALID > RELINK > OKAY > OUTSIDE
		
		byte hi = (byte)(Short.toUnsignedInt(value) >>> 8);
		byte lo = (byte)(Short.toUnsignedInt(value) & 0xff);
		
		STSResult res_hi = storeToSelf(offset, hi);
		if(res_hi == STSResult.FAIL) return res_hi;
		STSResult res_lo = storeToSelf(offset+1, lo);
		if(res_lo == STSResult.FAIL) return res_lo;
		
		for(int i = 0; i < STS_RES_PRI.length; i++){
			if(res_hi == STS_RES_PRI[i] || res_lo == STS_RES_PRI[i]){
				return STS_RES_PRI[i];
			}
		}
		
		return STSResult.FAIL;
	}
	
	public boolean equals(Object o){
		return this == o;
	}
	
	/*----- Debug -----*/
	
	/*----- Read -----*/
	
	public static int readBinArgs(int[] dst, BufferReference src, NUSALSeqCommandDef def, int cmdByte) {
		//1 byte params are read signed and 2 byte unsigned by default.
		if(def == null || dst == null || src == null) return 0;
		switch(def.getSerialType()) {
		case SERIALFMT_DUMMY:
		case SERIALFMT_:
			return 0;
		case SERIALFMT_L:
			dst[0] = cmdByte & 0xf;
			return 0;
		case SERIALFMT_1:
			//dst[0] = Byte.toUnsignedInt(src.nextByte());
			dst[0] = (int)src.nextByte(); //Treat signed
			return 1;
		case SERIALFMT_L1:
			dst[0] = cmdByte & 0xf;
			//dst[1] = Byte.toUnsignedInt(src.nextByte());
			dst[1] = (int)src.nextByte();
			return 1;
		case SERIALFMT_2:
			dst[0] = Short.toUnsignedInt(src.nextShort());
			return 2;
		case SERIALFMT_L2:
			dst[0] = cmdByte & 0xf;
			dst[1] = Short.toUnsignedInt(src.nextShort());
			return 2;
		case SERIALFMT_11:
			dst[0] = (int)src.nextByte();
			dst[1] = (int)src.nextByte();
			return 2;
		case SERIALFMT_12:
			//dst[0] = Byte.toUnsignedInt(src.nextByte());
			dst[0] = (int)src.nextByte();
			dst[1] = Short.toUnsignedInt(src.nextShort());
			return 3;
		case SERIALFMT_21:
			dst[0] = Short.toUnsignedInt(src.nextShort());
			//dst[1] = Byte.toUnsignedInt(src.nextByte());
			dst[1] = (int)src.nextByte();
			return 3;
		case SERIALFMT_L11:
			dst[0] = cmdByte & 0xf;
			dst[1] = (int)src.nextByte();
			dst[2] = (int)src.nextByte();
			return 2;
		case SERIALFMT_L12:
			dst[0] = cmdByte & 0xf;
			dst[1] = (int)src.nextByte();
			dst[2] = Short.toUnsignedInt(src.nextShort());
			return 3;
		case SERIALFMT_111:
			for(int i = 0; i < 3; i++) dst[i] = (int)src.nextByte();
			return 3;
		case SERIALFMT_V:
			dst[0] = NUSALSeqReader.readVLQ(src);
			return (dst[0] > 0x7f) ? 2:1;
		case SERIALFMT_NOTE:
			if(def.getNoteType() == NUSALSeqCommands.NOTETYPE_DVG){
				dst[0] = cmdByte;
				dst[1] = NUSALSeqReader.readVLQ(src);
				dst[2] = Byte.toUnsignedInt(src.nextByte());
				dst[3] = Byte.toUnsignedInt(src.nextByte());
				return (dst[1] > 0x7f) ? 4:3;
			}
			else if(def.getNoteType() == NUSALSeqCommands.NOTETYPE_DV){
				dst[0] = cmdByte - 0x40;
				dst[1] = NUSALSeqReader.readVLQ(src);
				dst[2] = Byte.toUnsignedInt(src.nextByte());
				return (dst[1] > 0x7f) ? 3:2;
			}
			else if(def.getNoteType() == NUSALSeqCommands.NOTETYPE_VG){
				dst[0] = cmdByte - 0x80;
				dst[1] = Byte.toUnsignedInt(src.nextByte());
				dst[2] = Byte.toUnsignedInt(src.nextByte());
				return 3;
			}
		case SERIALFMT_SNOTE:
			if(def.getNoteType() == NUSALSeqCommands.NOTETYPE_DVG_SHORT){
				dst[0] = cmdByte;
				dst[1] = NUSALSeqReader.readVLQ(src);
				return (dst[1] > 0x7f) ? 2:1;
			}
			else if(def.getNoteType() == NUSALSeqCommands.NOTETYPE_DV_SHORT){
				dst[0] = cmdByte - 0x40;
				return 0;
			}
			else if(def.getNoteType() == NUSALSeqCommands.NOTETYPE_VG_SHORT){
				dst[0] = cmdByte - 0x80;
				return 0;
			}
			break;
		case SERIALFMT_CPARAMS:
			for(int i = 0; i < 8; i++) dst[i] = (int)src.nextByte();
			return 8;
		case SERIALFMT_COPYFILTER:
			int bb = Byte.toUnsignedInt(src.nextByte());
			dst[0] = (bb >>> 4) & 0xf;
			dst[1] = bb & 0xf;
			return 1;
		case SERIALFMT_Lp8:
			dst[0] = (cmdByte & 0xf) - 8;
			return 0;
		case SERIALFMT_Lp81:
			dst[0] = (cmdByte & 0xf) - 8;
			dst[1] = (int)src.nextByte();
			return 1;
		case SERIALFMT_Lp82:
			dst[0] = (cmdByte & 0xf) - 8;
			dst[1] = Short.toUnsignedInt(src.nextShort());
			return 2;
		}
		
		return 0;
	}
	
	/*----- Write -----*/
	
	public String printContextFlags(){
		StringBuilder sb = new StringBuilder(512);
		if(this.seqUsed()) sb.append("[SEQ]");
		for(int i = 0; i < 16; i++){
			if(this.channelUsed(i)) sb.append(String.format("[CH%02d]", i));
		}
		for(int i = 0; i < 16; i++){
			for(int j = 0; j < 8; j++){
				if(this.layerUsed(i, j)) sb.append(String.format("[C%02dL%d]", i, j));
			}
		}
		return sb.toString();
	}
	
	protected String paramsToString(){
		return paramsToString(NUSALSeq.SYNTAX_SET_ZEQER);
	};
	
	protected String paramsToString(int syntax){
		//Defaults to all decimal
		if(params == null) return null;
		if(params.length < 1) return null;
		String str = "";
		for(int i = 0; i < params.length; i++){
			if(i != 0) str += ", ";
			str += Integer.toString(params[i]);
		}
		return str;
	};
	
	public String[][] getParamStrings(){
		//First column is base strings. Second is alt. Alt is used by references.
		if(params == null) return null;
		if(params.length < 1) return null;
		
		String[][] pstr = new String[params.length][2];
		for(int i = 0; i < params.length; i++){
			pstr[i][0] = Integer.toString(params[i]);
		}
		
		return pstr;
	}
	
	public String toString(){
		String cmdname = "<NULL>";
		if(def != null) cmdname = def.getMnemonicZeqer();
		String params = paramsToString();
		if(params != null && !params.isEmpty()) return cmdname + " " + params;
		return cmdname;
	}
	
	protected StringBuilder toMMLCommand_child(int syntax){
		StringBuilder sb = new StringBuilder(256);
		String cmdname = "<NULL>";
		if(def != null) {
			switch(syntax) {
			case NUSALSeq.SYNTAX_SET_ZEQER:
				cmdname = def.getMnemonicZeqer();
				break;
			case NUSALSeq.SYNTAX_SET_SEQ64:
				cmdname = def.getMnemonicSeq64();
				break;
			case NUSALSeq.SYNTAX_SET_ZELDARET:
				cmdname = def.getMnemonicZeldaRet();
				break;
			}
		}
		String params = paramsToString(syntax);
		sb.append(cmdname);
		if(params != null && !params.isEmpty()){
			sb.append(' ');
			sb.append(params);
		}
		return sb;
	}
	
	public String toMMLCommand() {
		return toMMLCommand(false, NUSALSeq.SYNTAX_SET_ZEQER);
	}
	
	public String toMMLCommand(int syntax){
		return toMMLCommand(false, syntax);
	}
	
	public String toMMLCommand(boolean comment_addr, int syntax){
		StringBuilder sb = toMMLCommand_child(syntax);
		if(comment_addr){
			sb.append(" ; 0x");
			sb.append(String.format("%04x", getAddress()));
			if(tick_addr >= 0){
				sb.append(", tick ");
				sb.append(tick_addr);
			}
		}
		return sb.toString();
	}
	
	public byte[] serializeMe(){
		byte[] bytes = null;
		int i = 0;
		switch(def.getSerialType()){
		case SERIALFMT_DUMMY: return null;
		case SERIALFMT_: bytes = new byte[]{def.getMinCommand()}; break;
		case SERIALFMT_L:
			i = Byte.toUnsignedInt(def.getMinCommand());
			i += params[0];
			bytes = new byte[]{(byte)i};
			break;
		case SERIALFMT_1:
			bytes = new byte[]{def.getMinCommand(), (byte)params[0]};
			break;
		case SERIALFMT_L1:
			i = Byte.toUnsignedInt(def.getMinCommand());
			i += params[0];
			bytes = new byte[]{(byte)i, (byte)params[1]};
			break;
		case SERIALFMT_2:
			bytes = new byte[3];
			bytes[0] = def.getMinCommand();
			bytes[1] = (byte)((params[0] >>> 8) & 0xFF);
			bytes[2] = (byte)(params[0] & 0xFF);
			break;
		case SERIALFMT_L2:
			bytes = new byte[3];
			i = Byte.toUnsignedInt(def.getMinCommand());
			i += params[0];
			bytes[0] = (byte)i;
			bytes[1] = (byte)((params[1] >>> 8) & 0xFF);
			bytes[2] = (byte)(params[1] & 0xFF);
			break;
		case SERIALFMT_11:
			bytes = new byte[]{def.getMinCommand(), (byte)params[0], (byte)params[1]};
			break;
		case SERIALFMT_12:
			bytes = new byte[4];
			bytes[0] = def.getMinCommand();
			bytes[1] = (byte)params[0];
			bytes[2] = (byte)((params[1] >>> 8) & 0xFF);
			bytes[3] = (byte)(params[1] & 0xFF);
			break;
		case SERIALFMT_21:
			bytes = new byte[4];
			bytes[0] = def.getMinCommand();
			bytes[1] = (byte)((params[0] >>> 8) & 0xFF);
			bytes[2] = (byte)(params[0] & 0xFF);
			bytes[3] = (byte)params[1];
			break;
		case SERIALFMT_L11:
			bytes = new byte[3];
			i = Byte.toUnsignedInt(def.getMinCommand());
			i += params[0];
			bytes[0] = (byte)i;
			bytes[1] = (byte)params[1];
			bytes[2] = (byte)params[2];
			break;
		case SERIALFMT_L12:
			bytes = new byte[4];
			i = Byte.toUnsignedInt(def.getMinCommand());
			i += params[0];
			bytes[0] = (byte)i;
			bytes[1] = (byte)params[1];
			bytes[2] = (byte)((params[2] >>> 8) & 0xFF);
			bytes[3] = (byte)(params[2] & 0xFF);
			break;
		case SERIALFMT_111:
			bytes = new byte[]{def.getMinCommand(), (byte)params[0], (byte)params[1], (byte)params[2]};
			break;
		case SERIALFMT_V:
			if(params[0] > 0x7f){
				bytes = new byte[3];
				bytes[0] = def.getMinCommand();
				bytes[1] = (byte)((params[0] >>> 8) | 0x80);
				bytes[2] = (byte)(params[0] & 0xFF);
			}
			else{
				bytes = new byte[]{def.getMinCommand(), (byte)params[0]};
			}
			break;
		case SERIALFMT_NOTE:
			if(def.getNoteType() == NUSALSeqCommands.NOTETYPE_DVG){
				if(params[1] > 0x7f){
					bytes = new byte[5];
					bytes[1] = (byte)((params[1] >>> 8) | 0x80);
					bytes[2] = (byte)(params[1] & 0xFF);
					bytes[3] = (byte)params[2];
					bytes[4] = (byte)params[3];
				}
				else{
					bytes = new byte[4];
					bytes[1] = (byte)params[1];
					bytes[2] = (byte)params[2];
					bytes[3] = (byte)params[3];
				}
				bytes[0] = (byte)params[0];
			}
			else if(def.getNoteType() == NUSALSeqCommands.NOTETYPE_DV){
				if(params[1] > 0x7f){
					bytes = new byte[4];
					bytes[1] = (byte)((params[1] >>> 8) | 0x80);
					bytes[2] = (byte)(params[1] & 0xFF);
					bytes[3] = (byte)params[2];
				}
				else{
					bytes = new byte[3];
					bytes[1] = (byte)params[1];
					bytes[2] = (byte)params[2];
				}
				bytes[0] = (byte)(params[0] + 0x40);
			}
			else if(def.getNoteType() == NUSALSeqCommands.NOTETYPE_VG){
				bytes = new byte[3];
				bytes[0] = (byte)(params[0] + 0x80);
				bytes[1] = (byte)params[1];
				bytes[2] = (byte)params[2];
			}
		case SERIALFMT_SNOTE:
			if(def.getNoteType() == NUSALSeqCommands.NOTETYPE_DVG_SHORT){
				if(params[1] > 0x7f){
					bytes = new byte[3];
					bytes[1] = (byte)((params[1] >>> 8) | 0x80);
					bytes[2] = (byte)(params[1] & 0xFF);
				}
				else{
					bytes = new byte[2];
					bytes[1] = (byte)params[1];
				}
				bytes[0] = (byte)params[0];
			}
			else if(def.getNoteType() == NUSALSeqCommands.NOTETYPE_DV_SHORT){
				bytes = new byte[1];
				bytes[0] = (byte)(params[0] | 0x40);
			}
			else if(def.getNoteType() == NUSALSeqCommands.NOTETYPE_VG_SHORT){
				bytes = new byte[1];
				bytes[0] = (byte)(params[0] | 0x80);
			}
			break;
		case SERIALFMT_CPARAMS:
			bytes = new byte[9];
			bytes[0] = def.getMinCommand();
			for(int j = 0; j < 8; j++){
				bytes[j+1] = (byte)params[j];
			}
			break;
		case SERIALFMT_COPYFILTER:
			bytes = new byte[2];
			bytes[0] = def.getMinCommand();
			i = (params[0] & 0xf) << 4;
			i |= (params[1] & 0xf);
			bytes[1] = (byte)i;
			break;
		case SERIALFMT_Lp8:
			bytes = new byte[1];
			i = Byte.toUnsignedInt(def.getMinCommand());
			i += params[0];
			bytes[0] = (byte)(i+8);
			break;
		case SERIALFMT_Lp81:
			bytes = new byte[2];
			i = Byte.toUnsignedInt(def.getMinCommand());
			i += params[0];
			bytes[0] = (byte)(i+8);
			bytes[1] = (byte)params[1];
			break;
		case SERIALFMT_Lp82:
			bytes = new byte[3];
			i = Byte.toUnsignedInt(def.getMinCommand());
			i += params[0];
			bytes[0] = (byte)(i+8);
			bytes[1] = (byte)((params[1] >>> 8) & 0xFF);
			bytes[2] = (byte)(params[1] & 0xFF);
			break;
		}
		return bytes;
	}
	
	public int serializeTo(OutputStream stream) throws IOException{
		byte[] bytes = serializeMe();
		if(bytes == null) return 0;
		stream.write(bytes);
		return bytes.length;
	}
	
	public int serializeTo(FileBuffer buffer){
		byte[] bytes = serializeMe();
		if(bytes == null) return 0;
		for(int i = 0; i < bytes.length; i++) buffer.addToFile(bytes[i]);
		return bytes.length;
	}
		
}
