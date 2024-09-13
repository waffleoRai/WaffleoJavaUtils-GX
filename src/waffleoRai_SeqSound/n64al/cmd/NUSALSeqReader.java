package waffleoRai_SeqSound.n64al.cmd;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import waffleoRai_SeqSound.n64al.NUSALSeq;
import waffleoRai_SeqSound.n64al.NUSALSeqCmdType;
import waffleoRai_SeqSound.n64al.NUSALSeqCommand;
import waffleoRai_SeqSound.n64al.NUSALSeqCommandBook;
import waffleoRai_SeqSound.n64al.NUSALSeqCommandSource;
import waffleoRai_SeqSound.n64al.NUSALSeqCommands;
import waffleoRai_SeqSound.n64al.NUSALSeqDataType;
import waffleoRai_Utils.BufferReference;
import waffleoRai_DataContainers.CoverageMap1D;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;

public class NUSALSeqReader implements NUSALSeqCommandSource{
	
	//TODO Call tick calculation still not working quite right... (Seq CZLE031, ch 2)
	//TODO Update MML parser to recognize ptbl
	
	//TODO stps on voice_offset param genning buffer instead of pointing to instruction. (stps seems to always do this)
	//TODO still not parsing voice_offset mod params in layer context, except for first in table.
	//TODO having trouble with some envelopes (0xffa in seq 01)
	
	//TODO sts targets getting eaten? (Seq 46) Target was parsed and recognized as target, and linked to sts... then removed from cmd map???
	
	//TODO sts/stps reparser does not account for if the new command was referenced by another before being replaced
	
	/*--- Constants ---*/
	
	public static final boolean DEBUG_MODE = false;
	
	public static final int PARSEMODE_UNDEFINED = 0x0;
	public static final int PARSEMODE_SEQ = 0x1;
	public static final int PARSEMODE_CHANNEL = 0x2;
	public static final int PARSEMODE_LAYER = 0x4;
	public static final int PARSEMODE_SUB = 0x8000;
	
	public static final int BRANCHEND_UNK = -1;
	public static final int BRANCHEND_END = 0;
	public static final int BRANCHEND_ZEROES = 1;
	public static final int BRANCHEND_ALREADY_PARSED = 2;
	public static final int BRANCHEND_DATA_END = 3; //Abrupt. Not a good thing.
	public static final int BRANCHEND_ERROR = 4;
	public static final int BRANCHEND_JUMP = 5; //Unconditional jump
	
	public static final int LAYER_COUNT = NUSALSeq.MAX_LAYERS_PER_CHANNEL;
	
	/*--- Instance Variables ---*/
	
	private FileBuffer data;
	private Map<Integer, NUSALSeqCommand> cmdmap;
	private Map<String, NUSALSeqCommand> lblmap;
	private NUSALSeqCommandBook cmdBook;
	//private Map<Integer, Integer> tickmap; //Address -> tick (first tick it can appear at)
	
	//Bookkeeping during parsing TODO (new parsing method Oct 2022)
	private List<ParseError> errors;
	private LinkedList<ParseLater> branch_queue;
	private Map<Integer, DataLater> data_parse;
	private Map<Integer, ParseResult> subroutines;
	private int[] ch_shorton_tick;
	
	private LabelCounter seq_lbls;
	private LabelCounter[] ch_lbls;
	private LabelCounter[][] ly_lbls;
	private int data_lbl_count;
	private int ecmd_lbl_count;
	
	//Linking
	private Set<Integer> rchecked;
	private Set<Integer> rskip;
	private boolean requeue_data = false;
	private boolean linkagain_flag = false; //If set, try running another link round
	
	private CoverageMap1D cmd_coverage;
	private CoverageMap1D[] ch_shortnotes;
	
	//private StackStack<Integer> branch_stack;
	
	private List<MMLBlock> rdr_blocks;
	private Map<String, MMLBlock> rdr_lbl_map;
	
	/*--- Initialization ---*/
	
	public NUSALSeqReader(){
		this(SysCommandBook.ZELDA64);
	}
	
	public NUSALSeqReader(SysCommandBook sysBook){
		if(sysBook == null) throw new IllegalArgumentException("System command book enum cannot be null!");
		try {
			cmdBook = sysBook.loadBook();
		} catch (IOException e) {
			e.printStackTrace();
			throw new IllegalArgumentException("Command book load failed!");
		}
		initInternal();
	}
	
	public NUSALSeqReader(NUSALSeqCommandBook commandBook){
		cmdBook = commandBook;
		if(cmdBook == null) {
			//Use Zelda defualt
			try {
				cmdBook = SysCommandBook.ZELDA64.loadBook();
			} catch (IOException e) {
				e.printStackTrace();
				throw new IllegalArgumentException("Tried to load default command book, but failed!");
			}
		}
		initInternal();
	}
	
 	public NUSALSeqReader(FileBuffer seqData){
		this(seqData, SysCommandBook.ZELDA64);
	}
 	
 	public NUSALSeqReader(FileBuffer seqData, SysCommandBook sysBook){
 		if(seqData == null) throw new IllegalArgumentException("Data source cannot be null!");
 		if(sysBook == null) throw new IllegalArgumentException("System command book enum cannot be null!");
		data = seqData;
		try {
			cmdBook = sysBook.loadBook();
		} catch (IOException e) {
			e.printStackTrace();
			throw new IllegalArgumentException("Command book load failed!");
		}
		initInternal();
	}
 	
 	public NUSALSeqReader(FileBuffer seqData, NUSALSeqCommandBook commandBook){
		if(seqData == null) throw new IllegalArgumentException("Data source cannot be null!");
		data = seqData;
		cmdBook = commandBook;
		if(cmdBook == null) {
			//Use Zelda defualt
			try {
				cmdBook = SysCommandBook.ZELDA64.loadBook();
			} catch (IOException e) {
				e.printStackTrace();
				throw new IllegalArgumentException("Tried to load default command book, but failed!");
			}
		}
		initInternal();
	}
 	
 	private void initInternal(){
 		cmdmap = new TreeMap<Integer, NUSALSeqCommand>();
		lblmap = new HashMap<String, NUSALSeqCommand>();
		//tickmap = new TreeMap<Integer, Integer>();
		errors = new LinkedList<ParseError>();
		ch_shortnotes = new CoverageMap1D[16];
		//branch_stack = new StackStack<Integer>();
		rchecked = new TreeSet<Integer>();
		rskip = new TreeSet<Integer>();
 	}
 	
 	/*--- Inner Structs ---*/
 	
 	public static class LabelCounter{
 		public int blocks = 0;
 		public int subs = 0;
 	}
 	
 	public static class ParseContext{
 		private int value = 0;
 		private NUSALSeqDataType dattype = null;
 		
 		private int maxBranchWhile = -1; //Max number of while loop runs in parseCodeBranch before giving up.
 		
 		public boolean isSeq(){
 			return (value & 0x80000000) != 0;
 		}
 		
 		public boolean isChannel(){
 			return (value & 0x40000000) != 0;
 		}
 		
 		public boolean isLayer(){
 			return (value & 0x20000000) != 0;
 		}
 		
 		public boolean isData(){
 			return (value & 0x10000000) != 0;
 		}
 		
 		public int getChannel(){
 			return value & 0xFF; //If > 15, then any channel.
 		}
 		
 		public int getLayer(){
 			return (value & 0xF00) >>> 8; //If highest bit is set, then any layer.
 		}
 		
 		public NUSALSeqDataType getDataType(){
 			return dattype;
 		}
 		
 		public void clear(){value = 0;}
 		public void setAsSeq(){value = 0x80000000;}
 		public void setAnyChannel(){value = 0x400000ff;}
 		public void setChannel(int ch){value = 0x40000000 | (ch & 0xf);}
 		public void setAnyLayer(){value = 0x20000f00;}
 		public void flagAsData(){value |= 0x10000000;}
 		public void setDataType(NUSALSeqDataType dtype){flagAsData(); dattype = dtype;}
 		
 		public void setLayer(int ch, int ly){
 			value = 0x20000000 | (ch & 0xf) | ((ly & 0x7) << 8);
 		}
 		
 		public static ParseContext fromCommand(NUSALSeqCommand cmd){
 			ParseContext ctx = new ParseContext();
 			if(cmd instanceof NUSALSeqDataCommand){
 				ctx.flagAsData();
 			}
 			int[] usetags = cmd.getFirstUsed();
 			if(usetags[0] < 0) ctx.setAsSeq();
 			else{
 				if(usetags[1] < 0){
 					//Channel
 					ctx.setChannel(usetags[0]);
 				}
 				else{
 					//Layer
 					ctx.setLayer(usetags[0], usetags[1]);
 				}
 			}
 			return ctx;
 		}
 		
 		public String toString(){
 			StringBuilder sb = new StringBuilder(512);
 			if(this.isData()) sb.append("[DATA]");
 			if(isSeq()) sb.append("SEQ");
 			if(isChannel()){
 				sb.append("CH");
 				int ch = getChannel();
 				if(ch < 0 || ch > 15) sb.append("ANY");
 				else sb.append(String.format("%02d", ch));
 			}
 			if(isLayer()){
 				sb.append("LYR");
 				int ch = getChannel();
 				int ly = getLayer();
 				if(ch >= 0 && ch < 16) sb.append(String.format("%02d", ch));
 				if(ly < 0 || ly > 7) sb.append("-ANY");
 				else sb.append("-" + ly);
 			}
 			return sb.toString();
 		}
 	}
 	
 	public static class ParseError{
 		public byte errorCommand = 0;
 		public int address = -1;
 		public ParseContext context = null;
 		
 		public String toString(){
 			StringBuilder sb = new StringBuilder(512);
 			sb.append("64SEQ PARSE ERROR @ 0x");
 			sb.append(Integer.toHexString(address));
 			sb.append(" | BAD COMMAND: ");
 			sb.append(String.format("%02x ", errorCommand));
 			sb.append("| CONTEXT: ");
 			sb.append(context);
 			return sb.toString();
 		}
 	}
 	
 	public static class ParseResult{
 		public int start_addr = -1;
 		public int current_addr = -1;
 		public int start_tick = -1;
 		public int tick_len = -1;
 		public int end_type = BRANCHEND_UNK;
 		public boolean timedout = false;
 		public ParseContext ctxt = null;
 		public NUSALSeqCommand head = null;
 	}
 	
 	public static class ParseLater{
 		public int address = -1;
 		public int tick = -1;
 		public ParseContext context = null;
 		public ParseLater(){}
 		public ParseLater(int addr, int t, ParseContext ctxt){
 			address = addr; tick = t;
 			context = ctxt;
 		}
 	}
 	
 	public static class DataLater{
 		public int data_address = -1;
 		public ParseContext ctx_guess = null;
 		public List<NUSALSeqCommand> referees = new LinkedList<NUSALSeqCommand>();
 	}
 	
 	public static class MMLLine{
 		public int line_number;
 		public String command;
 		public String[] args;
 	}
 	
 	public static class MMLBlock{
 		public String label;
 		//public int line_number = -1; //From text file
 		public int parse_mode = PARSEMODE_SEQ;
 		public int channel = -1;
 		public int layer = -1;
 		
 		public NUSALSeqCommandChunk chunk;
 		public LinkedList<MMLLine> lines;
 		
 		public MMLBlock(String lbl){
 			label = lbl;
 			chunk = new NUSALSeqCommandChunk();
 			chunk.setLabel(label);
 			lines = new LinkedList<MMLLine>();
 		}
 	}
 	
 	/*--- Command Linking (Bin) ---*/
 	
 	private void reflink_LabelSeqBlock(NUSALSeqCommand target, boolean isSub){
 		if(target == null) return;
 		String lbl = null;
 		if(isSub){
 			lbl = String.format("sub_seq_%03d", seq_lbls.subs++);
 		}
 		else{
 			lbl = String.format("seq_block_%03d", seq_lbls.blocks++);
 		}
 		target.setLabel(lbl);
 	}
 	
 	private void reflink_LabelChanBlock(NUSALSeqCommand target, int ch, boolean isSub){
 		if(target == null) return;
 		String lbl = null;
 		if(isSub){
 			lbl = String.format("sub_ch%X_%03d", ch, ch_lbls[ch].subs++);
 		}
 		else{
 			lbl = String.format("ch%X_block_%03d", ch, ch_lbls[ch].blocks++);
 		}
 		target.setLabel(lbl);
 	}
 	
 	private void reflink_LabelLyrBlock(NUSALSeqCommand target, int ch, int lyr, boolean isSub){
 		if(target == null) return;
 		String lbl = null;
 		if(isSub){
 			lbl = String.format("sub_ch%X-ly%d_%03d", ch, lyr, ly_lbls[ch][lyr].subs++);
 		}
 		else{
 			lbl = String.format("ch%X-ly%d_block_%03d", ch, lyr, ly_lbls[ch][lyr].blocks++);
 		}
 		target.setLabel(lbl);
 	}
 	
 	private void addNewDataAndLink(DataLater ndat, int branchTimeout){
 		//First find upper boundary of data size.
 		int upper_addr = ndat.data_address + 1;
 		int max_addr = (int)data.getFileSize();
 		
 		while(upper_addr < max_addr){
 			if(cmdmap.containsKey(upper_addr)) break;
 			upper_addr++;
 		}
 		
 		NUSALSeqCommand referee = ndat.referees.get(0);
 		NUSALSeqDataCommand dcmd = DataCommands.parseData(referee.getCommand(), data.getReferenceAt(ndat.data_address), upper_addr);
 		if(dcmd != null){
 			if(dcmd instanceof NUSALSeqPtrTableData){
 				//Look for tick from a referee?
 				for(NUSALSeqCommand ref : ndat.referees){
 					if(ref.getTickAddress() >= 0){
 						dcmd.setTickAddress(ref.getTickAddress());
 						break;
 					}
 				}
 				//Also look for invalid pointers to make a better guess at table end.
 				int max_units = dcmd.getUnitCount();
 				int newmax = max_units;
 				for(int i = 0; i < max_units; i++){
 					int ptr = dcmd.getDataValue(i, false);
 					if(ptr >= data.getFileSize()){
 						newmax = i; break;
 					}
 				}
 				if (newmax < max_units){
 					dcmd.reallocate(newmax << 1);
 				}
 				
 				rskip.add(ndat.data_address);
 			}
 			else{
 				rchecked.add(ndat.data_address);
 			}
 			
 			dcmd.setAddress(ndat.data_address);
 			dcmd.setLabel(String.format(".data%03d", data_lbl_count++));
 			lblmap.put(dcmd.getLabel(), dcmd);
 			cmdmap.put(ndat.data_address, dcmd);
 			
 			//Copy context flags too.
 			for(NUSALSeqCommand ref : ndat.referees){
 				if(ref.getBranchTarget() == null){
 					ref.setReference(dcmd);
 					dcmd.addContextFlagsFrom(ref);
 				}
 			}
 			
 			if(dcmd instanceof NUSALSeqPtrTableData){
 				reflinkScanPtbl((NUSALSeqPtrTableData)dcmd, branchTimeout);
 			}
 			
 		}
 		else{
 			ParseError error = new ParseError();
 			error.address = ndat.data_address;
 			error.context = ndat.ctx_guess;
 			error.errorCommand = 0;
 			errors.add(error);
 		}
 	}
 	
 	private ParseContext reflink_GuessPtblContext(NUSALSeqPtrTableData ptbl, int branchTimeout){
 		//This tries to figure out the context of what lies on the other
 		//	side of the pointers in the ptbl. ie. what the ptbl is used for.
 		//This may lead back a few levels of instructions...
 		ParseContext ret = new ParseContext();
 		ret.maxBranchWhile = branchTimeout;
 		NUSALSeqCommand target = DataCommands.guessPTableUsage(ptbl);
 		if(DEBUG_MODE){
 			System.err.println("[DEBUG] reflink_GuessPtblContext || DataCommands returned " + target);
 		}
 	
 		if(target == null){
 			if(DEBUG_MODE){
 	 			System.err.println("[DEBUG] reflink_GuessPtblContext || Couldn't work out context. Requeuing...");
 	 		}
 			this.linkagain_flag = true;
 			return null;
 		}
 		NUSALSeqCmdType ctype = target.getCommand();
 		
 		if(DEBUG_MODE){
 			System.err.println("\tTarget context flags: " + target.printContextFlags());
 		}
 		
 		switch(ctype){
 		case CHANNEL_OFFSET:
 		case CHANNEL_OFFSET_REL:
 		case CHANNEL_OFFSET_C:
 			ret.setChannel(target.getParam(0));
 			break;
 		case LOAD_SAMPLE_P:
 			ret.setDataType(NUSALSeqDataType.BUFFER);
 			break;
 		case VOICE_OFFSET:
 		case VOICE_OFFSET_REL:
 		case VOICE_OFFSET_TABLE:
 			ret = ParseContext.fromCommand(target);
 			int ch = ret.getChannel();
 			ret.clear();
 			ret.setLayer(ch, target.getParam(0));
 			break;
 		case SET_CH_FILTER:
 			ret.setDataType(NUSALSeqDataType.FILTER);
 			break;
 		case CH_ENVELOPE:
 		case L_ENVELOPE:
 			ret.setDataType(NUSALSeqDataType.ENVELOPE);
 			break;
 		case CH_LOAD_PARAMS:
 			ret.setDataType(NUSALSeqDataType.CH_PARAMS);
 			break;
 		case LOAD_SHORTTBL_GATE:
 			ret.setDataType(NUSALSeqDataType.GATE_TABLE);
 			break;
 		case LOAD_SHORTTBL_VEL:
 			ret.setDataType(NUSALSeqDataType.VEL_TABLE);
 			break;
 		case RANDP:
 		case ADD_IMM_P:
 		case ADD_RAND_IMM_P:
 			//Not sure, but leaving this here.
 			ret = ParseContext.fromCommand(ptbl);
 			break;
 		default:
 			//Assume same context as ptbl
 			ret = ParseContext.fromCommand(ptbl);
 			break;
 		}
 		
 		return ret;
 	}
 	
 	private void reflinkScanRefCommand(NUSALSeqReferenceCommand cmd){
 		int refaddr = cmd.getBranchAddress();
		NUSALSeqCommand trg = cmdmap.get(refaddr);
		if(trg != null){
			cmd.setReference(trg);
			if(trg.getLabel() == null){
				int[] ctxt = trg.getFirstUsed();
				boolean isSub = (cmd.getCommand() == NUSALSeqCmdType.CALL);
				if(ctxt[0] < 0) reflink_LabelSeqBlock(trg, isSub);
				else{
					if(ctxt[1] < 0) reflink_LabelChanBlock(trg, ctxt[0], isSub);
					else reflink_LabelLyrBlock(trg, ctxt[0], ctxt[1], isSub);
				}
			}
		}
		else {
			//Note reference
			//datarefs.addValue(refaddr, cmd);
			DataLater dat = data_parse.get(refaddr);
			if(dat != null){
				dat.referees.add(cmd);
			}
			else{
				dat = new DataLater();
				dat.data_address = refaddr;
				dat.ctx_guess = null;
				dat.referees.add(cmd);
				data_parse.put(refaddr, dat);
			}
		}
 	}
 	
 	private void reflinkScanPtbl(NUSALSeqPtrTableData ptbl, int branchTimeout){
 		int ucount = ptbl.getUnitCount();
 		if (DEBUG_MODE){
 			System.err.println("[DEBUG] reflinkScanPtbl || Scanning PTable @ 0x" + Integer.toHexString(ptbl.getAddress()));
 			System.err.println("\tCurrent Size Estimate: " + ucount);
 		}
 		
 		ParseContext tblctxt = null;
 		int trimto = -1;
 		for(int i = 0; i < ucount; i++){
 			int p = ptbl.getDataValue(i, false);
 			if(p == 0) continue; //Considered NULL
 			NUSALSeqCommand trg = cmdmap.get(p); //TODO What if table references STS targets?
 			if(trg != null){
 				//See if context is compatible
 				if(tblctxt != null){
 					if(trg.isSeqCommand()){
 						if(!tblctxt.isSeq()) {trimto = i; break;}
 					}
 					if(trg.isChannelCommand()){
 						if(!tblctxt.isChannel()){trimto = i; break;}
 					}
 				}
 				
 				//Link
 				ptbl.setReference(i, trg);
 				if(trg.getLabel() == null){
 					int[] ctxt = trg.getFirstUsed();
 					if(ctxt[0] < 0) reflink_LabelSeqBlock(trg, false);
 					else{
 						if(ctxt[1] < 0) reflink_LabelChanBlock(trg, ctxt[0], false);
 						else reflink_LabelLyrBlock(trg, ctxt[0], ctxt[1], false);
 					}
 					lblmap.put(trg.getLabel(), trg);
 				}
 			}
 			else{
 				if(DEBUG_MODE){
 					System.err.println("[DEBUG] reflinkScanPtbl || Nothing was found @ " + String.format("0x%04x", p));
 				}
 				
 				//Queue branch.
 				//Will need to work out context...
 				if (tblctxt == null) tblctxt = reflink_GuessPtblContext(ptbl, branchTimeout);
 				if(tblctxt == null){
 					//Error?
 					ParseError error = new ParseError();
 					error.address = ptbl.getAddress() + (i << 1);
 					error.errorCommand = -1;
 					error.context = null;
 					errors.add(error);
 					continue;
 				}
 				if(DEBUG_MODE){
 					System.err.println("[DEBUG] reflinkScanPtbl || Guessed context for @0x" + Integer.toHexString(p) + ": " + tblctxt.toString());
 				}
 				
 				//See if context is compatible (check if over cmd)
 				NUSALSeqCommand overcmd = getCommandOver(p);
 				if(overcmd != null){
 					if(overcmd.isSeqCommand()){
 						if(!tblctxt.isSeq()) {trimto = i; break;}
 					}
 					if(overcmd.isChannelCommand()){
 						if(!tblctxt.isChannel()){trimto = i; break;}
 					}
 				}
 				
 				if(tblctxt.isData()){
 					DataLater dat = new DataLater();
 					dat.data_address = p;
 					dat.ctx_guess = tblctxt;
 					dat.referees.add(ptbl);
 					data_parse.put(p, dat);
 				}
 				else{
 					branch_queue.add(new ParseLater(p, ptbl.getTickAddress(), tblctxt));
 				}
 			}
 		}
 		
 		if(trimto > 0){
 			ptbl.reallocate(trimto << 1);
 		}
 	}
 	
 	private void referenceLinkCycleScanCommand(int addr, int branchTimeout){
 		NUSALSeqCommand cmd = cmdmap.get(addr);
 		if(cmd == null) return;
 		if(cmd instanceof NUSALSeqReferenceCommand){
 			reflinkScanRefCommand((NUSALSeqReferenceCommand)cmd);
 			rchecked.add(addr);
 		}
 		else if (cmd instanceof NUSALSeqPtrTableData){
 			reflinkScanPtbl((NUSALSeqPtrTableData)cmd, branchTimeout);
 		}
 		else rchecked.add(addr);
 	}
 	
 	private boolean refCycleCont(boolean check_rskip){
 		//Can't contain all if smaller.
 		if(rchecked.size() < cmdmap.size()) return true;
 		
 		//All commands have definitely been checked.
 		if(rchecked.containsAll(cmdmap.keySet())) return false;
 		
 		//What is in cmdmap, but not rchecked? Is it in rskip?
 		if(!check_rskip) return true;
 		Collection<Integer> cmdkeys = cmdmap.keySet();
 		for(Integer addr : cmdkeys){
 			if(!rchecked.contains(addr)){
 				//If in neither, then we need to continue.
 				if(!rskip.contains(addr)) return true;
 			}
 		}
 		
 		return false;
 	}
 	
 	private void referenceLinkCycle(int branchTimeout) throws InterruptedException{
 		//Because this may dig up some new "ParseLaters" from branches
 		//	discovered in pointer tables, may need to be called multiple times.
 		if(cmdmap.isEmpty()) return;
		if(cmd_coverage != null) cmd_coverage.mergeBlocks();
		
		List<Integer> alist = new ArrayList<Integer>(cmdmap.size());
		alist.addAll(cmdmap.keySet());
		
		lblmap.clear(); data_parse.clear();
		for(Integer addr : alist){
			NUSALSeqCommand cmd = cmdmap.get(addr);
			if(cmd.getLabel() != null) lblmap.put(cmd.getLabel(), cmd);
		}
		
		/*
		 * I think ( don't remember what I did...)
		 * 	rchecked marks addresses that have been evaluated and should not be again
		 * 	rskip marks those that have been evaluated once, and should be every cycle
		 * 
		 *  Basically, code pointer p tables are rskip and everything else is rchecked
		 */
		if (DEBUG_MODE){
 			System.err.println("referenceLinkCycle || Entering code linking cycle");
 			System.err.println("\trchecked: " + rchecked.size());
 			System.err.println("\trskip: " + rskip.size());
 			System.err.println("\tcmdmap: " + cmdmap.size());
 		}
		boolean cont = refCycleCont(false); //Never check rskip the first round.
		while(cont){
			for(Integer addr : alist){
				if(rchecked.contains(addr)) continue;
				referenceLinkCycleScanCommand(addr, branchTimeout);
			}
			alist.clear();
			alist.addAll(cmdmap.keySet());
			
			cont = refCycleCont(true);
			if(Thread.interrupted()) throw new InterruptedException("NUSALSeqReader.referenceLinkCycle || Parser thread interrupted. Parse attempt terminated.");
		}
		
		//Datarefs.
		while(!data_parse.isEmpty()){
			if (DEBUG_MODE){
	 			System.err.println("referenceLinkCycle || Entering data linking cycle");
	 			System.err.println("\tUnhandled data links: " + data_parse.size());
	 		}
			
			alist.clear();
			alist.addAll(data_parse.keySet());
			Collections.sort(alist);
			Collections.reverse(alist);
			int max_addr = (int)data.getFileSize();
			int upper_addr = -1;
			
			for(Integer addr : alist){
				//Already handled?
				//If so, should it be shortened?
				//If not, try to read.
				//Also note that some overlaps are allowed (like an STS)
				requeue_data = false;
				
				NUSALSeqCommand dcmd = cmdmap.get(addr);
				DataLater ndat = data_parse.remove(addr);
				if (DEBUG_MODE){
		 			System.err.println("[DEBUG] referenceLinkCycle || Now processing data request @0x" + Integer.toHexString(addr));
		 			System.err.println("\tPre-existing command found: " + (dcmd != null));
		 		}
				if(dcmd != null){
					if(dcmd instanceof NUSALSeqDataCommand){
						NUSALSeqDataCommand datacmd = (NUSALSeqDataCommand)dcmd;
						upper_addr = addr+1;
						while((upper_addr < max_addr) & !cmdmap.containsKey(upper_addr)){
							upper_addr++;
						}
						if(upper_addr < (addr + datacmd.getSizeInBytes())){
							datacmd.reallocate(upper_addr - addr);
						}
					}
					else{
						//If not a data command (eg. an sts target), leave it alone except for label
						if(dcmd.getLabel() == null){
							dcmd.setLabel(String.format(".dyncmd%03d", ecmd_lbl_count++));
							lblmap.put(dcmd.getLabel(), dcmd);
						}
					}

					//Link to referees...
					for(NUSALSeqCommand referee : ndat.referees){
						if(referee.getBranchTarget() == null){
							referee.setReference(dcmd);
						}
					}
				}
				else{
					//Also see if this address has already been handled as an overlap
					//	of a different command (like an sts target)
					NUSALSeqCommand isect = getCommandOver(addr);
					if(isect != null){
						if (DEBUG_MODE){
				 			System.err.println("\tIntersecting command found: " + (isect.toMMLCommand(true)));
				 		}
						
						//If data command, shorten existing one and insert new one.
						//If not data command, make reference to offset.
						if(isect instanceof NUSALSeqDataCommand){
							NUSALSeqDataCommand datacmd = (NUSALSeqDataCommand)isect;
							datacmd.reallocate(addr - datacmd.getAddress());
							
							addNewDataAndLink(ndat, branchTimeout);
						}
						else{
							int offset = addr - isect.getAddress();
							if(isect.getLabel() == null){
								isect.setLabel(String.format(".dyncmd%03d", ecmd_lbl_count++));
								lblmap.put(isect.getLabel(), isect);
							}
							for(NUSALSeqCommand referee : ndat.referees){
								if(referee instanceof NUSALSeqDataRefCommand){
									NUSALSeqDataRefCommand drcmd = (NUSALSeqDataRefCommand)referee;
									drcmd.setReference(isect);
									drcmd.setDataOffset(offset);
								}
								else{
									//TODO
									//We have an error. Should probably mark in some way.
								}
							}
							rchecked.add(addr);
						}
					}
					else{
						//New data command.
						addNewDataAndLink(ndat, branchTimeout);
					}
				}
				
				if(requeue_data){
					data_parse.put(addr, ndat);
				}
			}
			if(Thread.interrupted()) throw new InterruptedException("NUSALSeqReader.referenceLinkCycle || Parser thread interrupted. Parse attempt terminated.");
		}
 	}
 	
 	private NUSALSeqCommand processUnusedData(int startAddr, int gapSize, int branchTimeout) throws InterruptedException{
 		//Check if nonzero
 		//Try to parse as a command (context by checking previous instruction)
 		//If that doesn't work, mark as unused data
 		byte b = data.getByte(startAddr);
 		if(b != 0){
 			//Find previous command
 			NUSALSeqCommand prev = null;
 			for(int i = startAddr-1; i >= 0; i--){
 				prev = cmdmap.get(i);
 				if(prev != null) break;
 			}
 			
 			if(prev != null){
 				int prevEnd = prev.getAddress() + prev.getSizeInBytes();
 				if(prevEnd == startAddr){
 					//Try to parse as a command
 					ParseContext pctx = ParseContext.fromCommand(prev);
 					pctx.maxBranchWhile = branchTimeout;
 					int tick = prev.getTickAddress();
 					if(tick >= 0){
 						tick += prev.getSizeInTicks();
 					}
 					
 					branch_queue.add(new ParseLater(startAddr, tick, pctx));
 					processParseQueue(true, branchTimeout, branchTimeout);
 					
 					NUSALSeqCommand cmd = cmdmap.get(startAddr);
 					if(cmd != null){
 						int addr = cmd.getAddress() + cmd.getSizeInBytes();
 						NUSALSeqCommand next = null;
 						int gapend = startAddr + gapSize;
 						tick = cmd.getTickAddress();
 	 					if(tick >= 0){
 	 						tick += cmd.getSizeInTicks();
 	 					}
 	 					
 						while(addr < gapend){
 							//1. Scan to end of current parse.
 							while(addr < gapend){
 								next = cmdmap.get(addr);
 								if(next == null){
 									break;
 								}
 								addr += next.getSizeInBytes();
 								int tt = next.getTickAddress();
 								if(tt < 0){
 									tick = -1;
 								}
 								else tick += next.getSizeInTicks();
 							}
 							
 							if(addr >= gapend) break;
 							
 							//Still space before gap end. So...
 							//2. Parse starting there
 							branch_queue.add(new ParseLater(addr, tick, pctx));
 		 					processParseQueue(true, branchTimeout, branchTimeout);
 						}
 						return cmd;
 					}
 				}
 			}
 			
 			//Just make unused data
 			NUSALSeqDataCommand gap = new NUSALSeqDataCommand(gapSize);
 			for(int i = 0; i < gapSize; i++){
				b = data.getByte(startAddr + i);
				gap.setDataByte(b, i);
			}
 			
 			gap.setLabel(String.format(".unuseddat%04x", startAddr));
			gap.setAddress(startAddr);
			cmdmap.put(startAddr, gap);	
			
			return gap;
 		}
 		
 		return null;
 	}
 	
 	private void findUnusedData(int branchTimeout) throws InterruptedException{
 		//int gapcount = 0; //For labeling.
 		List<Integer> alladdr = new ArrayList<Integer>(cmdmap.size()+1);
		alladdr.addAll(cmdmap.keySet());
		Collections.sort(alladdr);
		int last_addr = 0;
		for(Integer addr : alladdr){
			if(last_addr < addr){
				//Gap.
				int gap_size = addr - last_addr;
				processUnusedData(last_addr, gap_size, branchTimeout);
			}
			
			NUSALSeqCommand cmd = cmdmap.get(addr);
			last_addr = addr + cmd.getSizeInBytes();
		}
		
		int end_addr = (int)data.getFileSize();
		if(last_addr < end_addr){
			//Only add if not all zero
			int gap_size = end_addr - last_addr;
			processUnusedData(last_addr, gap_size, branchTimeout);
		}
 	}
 	
 	/*--- Code Parse Cycle (Bin) ---*/
 	
 	private boolean layerInShortModeAt(int channel, int tick){
 		if(tick < 0) return false; //Defaults to no.
 		return ch_shortnotes[channel].isCovered(tick);
 	}
 	
 	private boolean checkZeroTail(int addr, boolean channel_ctx){
 		int zerocount = 0;
		for(int p = addr; p < data.getFileSize(); p++){
			if(data.getByte(p) != 0) break;
			zerocount++;
		}
		if(zerocount >= 2) return true;
		if(channel_ctx && zerocount >= 1) return true;
		return false;
 	}
 	 	
 	private ParseResult handleCallCommand(int target_addr, ParseContext context) throws InterruptedException{
 		ParseResult sub = subroutines.get(target_addr);
 		if(sub != null) return sub;
 		
 		sub = parseCodeBranch(target_addr, 0, context);
 		subroutines.put(target_addr, sub);
 		
 		return sub;
 	}
 	
 	private NUSALSeqCommand parseCommandAt(int addr, int tick, ParseContext context){
 		NUSALSeqCommand cmd = null;
 		if(context.isSeq()){
			cmd = SeqCommands.parseSequenceCommand(data.getReferenceAt(addr), cmdBook);
			if(cmd != null) cmd.flagSeqUsed();
		}
		else if(context.isChannel()){
			cmd = ChannelCommands.parseChannelCommand(data.getReferenceAt(addr), cmdBook);
			if(cmd != null){
				int ch = context.getChannel();
				if(ch < 16) cmd.flagChannelUsed(ch);
			}
		}
		else if(context.isLayer()){
			int ch = context.getChannel();
			boolean shortMode = false;
			if(ch < 16) shortMode = this.layerInShortModeAt(ch, tick);
			cmd = LayerCommands.parseLayerCommand(data.getReferenceAt(addr), shortMode);
			if(cmd != null){
				int ly = context.getLayer();
				if(ch < 16 && ly < 8){
					cmd.flagLayerUsed(ch, ly);
				}
			}
		}
 		
 		if(cmd != null){
 			cmd.setAddress(addr);
 			cmd.setTickAddress(tick);
 			cmdmap.put(addr, cmd);
 		}
 		
 		if (DEBUG_MODE){
 			System.err.println("parseCommandAt || Address = 0x" + Integer.toHexString(addr) + ", Tick = " + tick + ", Command: " + cmd + ", Ctx: " + cmd.printContextFlags());
 		}
 		
 		return cmd;
 	}
 	
 	private ParseResult parseCodeBranch(int pos_start, int tick_start, ParseContext context) throws InterruptedException{
 		ParseResult res = new ParseResult();
 		NUSALSeqCommand tail = null;
 		NUSALSeqCommand ncmd = null;
 		int now_addr = pos_start;
 		int now_tick = tick_start;
 		int data_end = (int)data.getFileSize();
 		int wtries = 0;
 		
 		if (DEBUG_MODE){
 			System.err.println("parseCodeBranch || Address = 0x" + Integer.toHexString(pos_start) + ", Tick = " + tick_start + ", Context: " + context);
 		}
 		
 		res.ctxt = context;
 		res.start_tick = tick_start;
 		res.start_addr = pos_start;
 		
 		//Read until we either hit an end command, something already parsed, or a chain of zeroes.
 		while(now_addr < data_end){
 			if(context.maxBranchWhile >= 0){
 				if(wtries ++ >= context.maxBranchWhile){
 					res.timedout = true;
 					break;
 				}
 			}
 			
 			if(cmdmap.containsKey(now_addr)){
 				res.end_type = BRANCHEND_ALREADY_PARSED;
 				break;
 			}
 			
 			if(checkZeroTail(now_addr, context.isChannel())){
 				res.end_type = BRANCHEND_ZEROES;
 				break;
 			}
 			
 			ncmd = parseCommandAt(now_addr, now_tick, context);
 			if(ncmd == null){
 				res.end_type = BRANCHEND_ERROR;
 				ParseError err = new ParseError();
 				err.context = context;
 				err.address = now_addr;
 				err.errorCommand = data.getByte(now_addr);
 				break;
 			}
 			
 			if(res.head == null) res.head = ncmd;
 			if(tail != null) tail.setSubsequentCommand(ncmd);
 			tail = ncmd;
 			now_addr += ncmd.getSizeInBytes();
 			
 			//Check if end.
 			NUSALSeqCmdType ctype = ncmd.getFunctionalType();
 			if(ctype == NUSALSeqCmdType.END_READ){
 				res.end_type = BRANCHEND_END;
 				break;
 			}
 			
 			if(ctype == NUSALSeqCmdType.SHORTNOTE_ON){
 				int ch = context.getChannel();
 				if(ch < 16 && ch_shorton_tick[ch] < 0){
 					//Was off.
 					ch_shorton_tick[ch] = now_tick;
 				}
 			}
 			if(ctype == NUSALSeqCmdType.SHORTNOTE_OFF){
 				int ch = context.getChannel();
 				if(ch < 16 && ch_shorton_tick[ch] >= 0){
 					//Was on.
 					ch_shortnotes[ch].addBlock(ch_shorton_tick[ch], now_tick);
 					ch_shorton_tick[ch] = -1;
 				}
 			}
 			
 			//Check if branch.
 			//Call, conditional jump, unconditional jump, open channel/layer
 			if(ctype.flagSet(NUSALSeqCommands.FLAG_CALL)){
 				//DFS behavior since we need to get tick length of sub.
 				if(ctype == NUSALSeqCmdType.CALL_DYNTABLE || ctype == NUSALSeqCmdType.CALL_TABLE){
 					now_tick = -1; //Unpredictable
 				}
 				else{
 					ParseResult sub = this.handleCallCommand(ncmd.getBranchAddress(), context);
 	 				if(sub != null){
 	 					if(now_tick >= 0){
 	 	 	 				if(sub.tick_len >= 0) now_tick += sub.tick_len;
 	 	 	 				else now_tick = -1;
 	 	 	 			}	
 	 				}	
 				}
 			}
 			else if(ctype.flagSet(NUSALSeqCommands.FLAG_JUMP)){
 				//Unconditional jump.
 				//BFS behavior, though it will terminate this branch
 				branch_queue.add(new ParseLater(ncmd.getBranchAddress(), now_tick, context));
 				res.end_type = BRANCHEND_JUMP;
 				break;
 			}
 			else if(ctype.flagSet(NUSALSeqCommands.FLAG_BRANCH)){
 				//Conditional jump
 				//BFS behavior
 				branch_queue.add(new ParseLater(ncmd.getBranchAddress(), now_tick, context));
 			}
 			else if(ctype.flagSet(NUSALSeqCommands.FLAG_OPENTRACK)){
 				//BFS behavior because we may need info on parent track
 				//	to know how to read child track.
 				ParseContext child_ctxt = new ParseContext();
 				child_ctxt.maxBranchWhile = res.ctxt.maxBranchWhile;
 				if(ctype == NUSALSeqCmdType.CHANNEL_OFFSET || ctype == NUSALSeqCmdType.CHANNEL_OFFSET_REL){
 					child_ctxt.setChannel(ncmd.getParam(0));
 				}
 				else if(ctype == NUSALSeqCmdType.CHANNEL_OFFSET_C){
 					child_ctxt.setChannel(ncmd.getParam(0));
 				}
 				else if(ctype == NUSALSeqCmdType.VOICE_OFFSET || ctype == NUSALSeqCmdType.VOICE_OFFSET_REL){
 					int ch = context.getChannel();
 					child_ctxt.setLayer(ch, ncmd.getParam(0));
 				}
 				//Table jumps will be parsed later.
 				branch_queue.add(new ParseLater(ncmd.getBranchAddress(), now_tick, child_ctxt));
 			}
 			else{
 				if(now_tick >= 0){
 	 				int cticks = ncmd.getSizeInTicks();
 	 				if(cticks >= 0) now_tick += cticks;
 	 				else now_tick = -1;
 	 			}	
 			}
 			if(Thread.interrupted()) throw new InterruptedException("NUSALSeqReader.parseCodeBranch || Parser thread interrupted. Parse attempt terminated.");
 		}
 		
 		if((res.end_type == BRANCHEND_UNK) && (now_addr >= data_end)) res.end_type = BRANCHEND_DATA_END;
 		res.current_addr = now_addr;
 		if(now_tick >= 0){
 			res.tick_len = now_tick - res.start_tick;
 		}
 		else res.tick_len = -1;
 		
 		return res;
 	}
 	
 	private boolean processParseQueue(boolean stopOnError, int branchTimeout, int overallTimeout) throws InterruptedException{
 		int whileCount = 0;
		boolean killed = false;
		
		while(!branch_queue.isEmpty()){
			if(overallTimeout >= 0){
				if(whileCount++ >= overallTimeout){
					killed = true;
					break;
				}
			}
			
			ParseLater preq = branch_queue.pop();
			parseCodeBranch(preq.address, preq.tick, preq.context);
			if(stopOnError && !errors.isEmpty()){
				killed = true;
				break;
			}
			
			//Now inner pop until depleted before link
			//To get known branches...
			while(!branch_queue.isEmpty()){
				if(overallTimeout >= 0){
					if(whileCount++ >= overallTimeout){
						killed = true;
						break;
					}
				}
				
				preq = branch_queue.pop();
				parseCodeBranch(preq.address, preq.tick, preq.context);
				if(Thread.interrupted()) throw new InterruptedException("NUSALSeqReader.preParse || Parser thread interrupted. Parse attempt terminated.");
				
				if(stopOnError && !errors.isEmpty()){
					killed = true;
					break;
				}
			}
			
			//Try linkage...
			linkagain_flag = false;
			referenceLinkCycle(branchTimeout);
			if(linkagain_flag) referenceLinkCycle(branchTimeout);
			if(Thread.interrupted()) throw new InterruptedException("NUSALSeqReader.preParse || Parser thread interrupted. Parse attempt terminated.");
			
			if(stopOnError && !errors.isEmpty()){
				killed = true;
				break;
			}
		}
		
		return killed;
	}
 	
	/*--- Read ---*/
 	
 	private void initPreparse(){
 		cmdmap.clear();
		lblmap.clear();
		branch_queue = new LinkedList<ParseLater>();
		data_parse = new HashMap<Integer, DataLater>();
		subroutines = new HashMap<Integer, ParseResult>();
		ch_shorton_tick = new int[16];
		Arrays.fill(ch_shorton_tick, -1);
		seq_lbls = new LabelCounter();
		ch_lbls = new LabelCounter[16];
		ly_lbls = new LabelCounter[16][8];
		ch_shortnotes = new CoverageMap1D[16];
		for(int i = 0; i < 16; i++){
			ch_lbls[i] = new LabelCounter();
			ch_shortnotes[i] = new CoverageMap1D();
			for(int j = 0; j < 8; j++){
				ly_lbls[i][j] = new LabelCounter();
			}
		}
		data_lbl_count = 0;
		ecmd_lbl_count = 0;
		rchecked.clear(); rskip.clear();
		cmd_coverage = new CoverageMap1D();
 	}
 	
 	private void cleanupPreparse(){
 		//Throw it all to the glorious GC! (Maybe)
 		branch_queue = null;
 		data_parse = null;
 		subroutines = null;
 		ch_shorton_tick = null;
 		seq_lbls = null;
 		rchecked.clear(); rskip.clear();
 		cmd_coverage = null;
 		for(int i = 0; i < 16; i++){
			ch_lbls[i] = null;
			ch_shortnotes[i] = null;
			for(int j = 0; j < 8; j++){
				ly_lbls[i][j] = null;
			}
		}
 		ch_lbls = null;
		ly_lbls = null;
		ch_shortnotes = null;
 	}
 	
  	public void readInMMLScript(BufferedReader input) throws IOException{
 		if(rdr_blocks == null) rdr_blocks = new LinkedList<MMLBlock>();
 		if(rdr_lbl_map == null) rdr_lbl_map = new HashMap<String, MMLBlock>();
 		
 		String line = null;
		int lineno = -1; int charidx = -1;
		MMLBlock curblock = null;
		while((line = input.readLine()) != null){
			lineno++;
			if(line.startsWith(";")) continue;
			if(line.isEmpty()) continue;
			
			//Trim out any comment in line.
			charidx = line.indexOf(';');
			if(charidx >= 0){
				line = line.substring(0, charidx);
			}
			if(line.isEmpty()) continue;
			
			//Clean out whitespace from start/end
			line = line.trim();
			if(line.isEmpty()) continue;
			
			//Check if label. 
			if(line.endsWith(":")){
				//Label
				String lbl = line.substring(0, line.length()-1);
				curblock = new MMLBlock(lbl);
				//curblock.line_number = lineno;
				rdr_blocks.add(curblock);
				rdr_lbl_map.put(lbl, curblock);
				//System.err.println("Label line found: " + lbl);
			}
			else{
				//Command
				if(curblock == null){
					String lbl = "seqstart";
					curblock = new MMLBlock(lbl);
					//curblock.line_number = lineno;
					rdr_blocks.add(curblock);
					rdr_lbl_map.put(lbl, curblock);
				}
				String[] split = line.split(" ");
				if(split == null) continue;
				
				MMLLine mline = new MMLLine();
				curblock.lines.add(mline);
				mline.line_number = lineno;
				mline.command = split[0];
				int acount = split.length-1;
				if(acount >= 1){
					mline.args = new String[acount];
					for(int i = 0; i < acount; i++){
						mline.args[i] = split[i+1].replace(",", "");
					}
				}
			}
		}
 		
 	}
	
 	public void parseMMLBlock(MMLBlock block, MMLBlock previous) throws UnsupportedFileTypeException{
 		NUSALSeqCommand cmd = null;
 		if(block.parse_mode == PARSEMODE_SEQ && (previous != null)){
			//Set to seq, reset default to last block...
			block.parse_mode = previous.parse_mode;
			block.channel = previous.channel;
			block.layer = previous.layer;
		}
		for(MMLLine mline : block.lines){
			if(mline.command.equals("data")){
				cmd = DataCommands.parseData(mline.command, mline.args);
			}
			else{
				switch(block.parse_mode){
				case PARSEMODE_SEQ:
					cmd = SeqCommands.parseSequenceCommand(mline.command, mline.args);
					break;
				case PARSEMODE_CHANNEL:
					cmd = ChannelCommands.parseChannelCommand(mline.command, mline.args);
					break;
				case PARSEMODE_LAYER:
					cmd = LayerCommands.parseLayerCommand(mline.command, mline.args);
					break;
				}	
			}
			
			if(cmd == null){
				throw new UnsupportedFileTypeException("Could not read command at line " + mline.line_number + ": " + mline.command);
			}
			
			switch(block.parse_mode){
			case PARSEMODE_SEQ:
				cmd.flagSeqUsed();
				break;
			case PARSEMODE_CHANNEL:
				cmd.flagChannelUsed(block.channel);
				break;
			case PARSEMODE_LAYER:
				cmd.flagLayerUsed(block.channel, block.layer);
				break;
			}
			
			block.chunk.addCommand(cmd);
			//Resolve reference, if present.
			NUSALSeqCmdType ctype = cmd.getCommand();
			if(ctype.flagSet(NUSALSeqCommands.FLAG_OPENTRACK)){
				int idx = cmd.getParam(0);
				String trglabel = mline.args[1];
				MMLBlock target = rdr_lbl_map.get(trglabel);
				if(target == null){
					throw new UnsupportedFileTypeException("Could not find label \"" + trglabel + "\"");
				}
				cmd.setReference(target.chunk);
				if(block.parse_mode == PARSEMODE_SEQ){
					//Channel
					target.channel = idx;
					target.layer = -1;
					target.parse_mode = PARSEMODE_CHANNEL;
				}
				else{
					if(ctype == NUSALSeqCmdType.CHANNEL_OFFSET_C){
						//Other channel
						target.channel = idx;
						target.layer = -1;
						target.parse_mode = PARSEMODE_CHANNEL;
					}
					else{
						//Layer
						target.channel = block.channel;
						target.layer = idx;
						target.parse_mode = PARSEMODE_LAYER;
					}
				}
			}
			else if(ctype.flagSet(NUSALSeqCommands.FLAG_ADDRREF)){
				String trglabel = mline.args[0];
				MMLBlock target = null;
				int offset = 0;
				if(trglabel.contains("[")){
					int i0 = trglabel.indexOf('[');
					int i1 = trglabel.indexOf(']');
					target = rdr_lbl_map.get(trglabel.substring(0, i0));
					offset = Integer.parseInt(trglabel.substring(i0+1, i1));
				}
				else{
					target = rdr_lbl_map.get(trglabel);
				}
				if(target == null){
					throw new UnsupportedFileTypeException("Could not find label \"" + trglabel + "\" (Line " + mline.line_number + ")");
				}
				cmd.setReference(target.chunk);
				target.channel = block.channel;
				target.parse_mode = block.parse_mode;
				target.layer = block.layer;
				
				if((cmd instanceof NUSALSeqDataRefCommand) && offset != 0){
					NUSALSeqDataRefCommand drcmd = (NUSALSeqDataRefCommand)cmd;
					drcmd.setDataOffset(offset);
				}
			}
		}
		block.chunk.linkSequentialCommands();
		block.chunk.getChunkHead().setLabel(block.label);
		//mainchunk.addCommand(block.chunk);
 		
 	}
 	
	public static NUSALSeqReader readMMLScript(BufferedReader input) throws IOException, UnsupportedFileTypeException{
		NUSALSeqReader reader = new NUSALSeqReader();
		reader.rdr_blocks = new LinkedList<MMLBlock>();
		reader.rdr_lbl_map = new HashMap<String, MMLBlock>();
		
		reader.readInMMLScript(input);
		
		//Now, parse commands.
		NUSALSeqCommandChunk mainchunk = new NUSALSeqCommandChunk();
		//NUSALSeqCommand cmd = null;
		MMLBlock lastblock = null;
		for(MMLBlock mblock : reader.rdr_blocks){
			reader.parseMMLBlock(mblock, lastblock);
			mainchunk.addCommand(mblock.chunk);
			lastblock = mblock;
		}
		
		//Clean up chunks
		mainchunk.dechunkReference();
		mainchunk.setAddress(0);
		
		//Map in reader
		for(MMLBlock mblock : reader.rdr_blocks){
			List<NUSALSeqCommand> clist = mblock.chunk.getCommands();
			for(NUSALSeqCommand c : clist){
				reader.cmdmap.put(c.getAddress(), c);
				if(c.getLabel() != null){
					reader.lblmap.put(c.getLabel(), c);
				}
			}
		}
		
		//Serialize for data block
		int size = mainchunk.getSizeInBytes();
		int mod = size & 0xf;
		int pad = 0;
		if(mod != 0) {pad = 16 - mod; size += pad;}
		reader.data = new FileBuffer(size, true);
		mainchunk.serializeTo(reader.data);
		for(int i = 0; i < pad; i++) reader.data.addToFile(FileBuffer.ZERO_BYTE);
		
		//Clean up
		reader.rdr_blocks.clear();
		reader.rdr_lbl_map.clear();
		reader.rdr_blocks = null;
		reader.rdr_lbl_map = null;
		
		return reader;
	}
			
	public void preParse() throws InterruptedException{
		preParse(false, -1, -1);
	}
	
	public void preParse(boolean stopOnError, int branchTimeout, int overallTimeout) throws InterruptedException{
		initPreparse();
		ParseContext pctx = new ParseContext();
		pctx.setAsSeq();
		pctx.maxBranchWhile = branchTimeout;
		branch_queue.add(new ParseLater(0, 0, pctx));
		
		boolean killed = processParseQueue(stopOnError, branchTimeout, overallTimeout);

		NUSALSeqCommand entrycmd = cmdmap.get(0);
		if(entrycmd != null){
			if(entrycmd.getLabel() == null){
				entrycmd.setLabel("_entry");
			}
		}
		
		//Scan for any unparsed regions (except for end pad) and map as bin data.
		if(!killed) findUnusedData(branchTimeout);
		
		if(DEBUG_MODE){
			System.err.println("PreParse Complete(?) Error Count: " + errors.size());
			if(!errors.isEmpty()){
				System.err.print("ERRORS ----");
				for(ParseError err : errors){
					System.err.println("\t" + err.toString());
				}
			}
		}
		
		cleanupPreparse();
	}
	
	/*--- Getters ---*/
	
	public BufferReference getDataReference(int addr){
		return data.getReferenceAt(addr);
	}
	
	public FileBuffer getData(){
		return this.data;
	}
	
	public NUSALSeqCommand getCommandAt(int addr){
		//This version returns null if not already parsed as it needs context...
		return cmdmap.get(addr);
	}
	
	public NUSALSeqCommand getCommandOver(int address){
		int checkAddr = address;
		NUSALSeqCommand cmd = null;
		while(checkAddr >= 0){
			cmd = cmdmap.get(checkAddr);
			if(cmd != null){
				int cend = cmd.getAddress() + cmd.getSizeInBytes();
				if(address < cend) return cmd;
				return null;
			}
			checkAddr--;
		}
		return null;
	}
	
	public NUSALSeqCommand getSeqCommandAt(int addr){
		NUSALSeqCommand cmd = cmdmap.get(addr);
		if(cmd != null) return cmd;
		
		//Else, parse from data
		cmd = SeqCommands.parseSequenceCommand(data.getReferenceAt(addr));
		if(cmd == null) return null;
		cmdmap.put(addr, cmd);
		
		return cmd;
	}
	
	public NUSALSeqCommand getChannelCommandAt(int addr){
		NUSALSeqCommand cmd = cmdmap.get(addr);
		if(cmd != null) return cmd;
		
		cmd = ChannelCommands.parseChannelCommand(data.getReferenceAt(addr));
		if(cmd == null) return null;
		cmdmap.put(addr, cmd);
		
		return cmd;
	}
	
	public NUSALSeqCommand getLayerCommandAt(int addr){
		return getLayerCommandAt(addr, false);
	}
	
	public NUSALSeqCommand getLayerCommandAt(int addr, boolean shortNotes){
		NUSALSeqCommand cmd = cmdmap.get(addr);
		if(cmd != null) return cmd;
		
		cmd = LayerCommands.parseLayerCommand(data.getReferenceAt(addr), shortNotes);
		if(cmd == null) return null;
		cmdmap.put(addr, cmd);
		
		return cmd;
	}
	
	public Map<Integer, NUSALSeqCommand> getSeqLevelCommands() {
		Map<Integer, NUSALSeqCommand> outmap = new HashMap<Integer, NUSALSeqCommand>();
		for(Entry<Integer, NUSALSeqCommand> e : cmdmap.entrySet()){
			if(e.getValue().seqUsed()){
				outmap.put(e.getKey(), e.getValue());
			}
		}
		return outmap;
	}
	
	public List<Integer> getAllAddresses() {
		List<Integer> list = new ArrayList<Integer>(cmdmap.size()+1);
		list.addAll(cmdmap.keySet());
		Collections.sort(list);
		return list;
	}
	
	public List<NUSALSeqCommand> getOrderedCommands(){
		List<NUSALSeqCommand> list = new ArrayList<NUSALSeqCommand>(cmdmap.size()+1);
		List<Integer> alist = getAllAddresses();
		for(Integer k : alist){
			list.add(cmdmap.get(k));
		}
		return list;
	}
	
	public int getMinimumSizeInBytes(){
		return (int)data.getFileSize();
	}
	
	public List<MMLBlock> getReaderMMLBlocks(){
		return this.rdr_blocks;
	}
	
	public Map<String, MMLBlock> getReaderMMLMap(){
		return this.rdr_lbl_map;
	}
	
	/*--- Setters ---*/
	
	public boolean reparseRegion(int pos, int len){
		clearCachedCommandsBetween(pos, len);
		return true;
	}
	
	public void clearCachedCommandsBetween(int stAddr, int len){
		LinkedList<Integer> list = new LinkedList<Integer>();
		list.addAll(cmdmap.keySet());
		Collections.sort(list);
		
		int ed = stAddr + len;
		while(!list.isEmpty()){
			int pos = list.pop();
			if(pos < stAddr) continue;
			if(pos >= ed) return;
			cmdmap.remove(pos);
		}
		
	}
	
	public void clearCachedCommands(){cmdmap.clear();}
	
	private STSResult relinkCommand(int addr, NUSALSeqCommand cmd){
		int coff = addr - cmd.getAddress();
		if(cmd instanceof NUSALSeqReferenceCommand){
			NUSALSeqReferenceCommand rcmd = (NUSALSeqReferenceCommand)cmd;
			int taddr = rcmd.getBranchAddress();
			NUSALSeqCommand tcmd = getCommandOver(taddr);
			if(tcmd == null) return STSResult.INVALID;
			if(tcmd.getAddress() == taddr){
				rcmd.setReference(tcmd);
				return STSResult.OKAY;
			}
			else{
				if(cmd instanceof NUSALSeqDataRefCommand){
					NUSALSeqDataRefCommand drcmd = (NUSALSeqDataRefCommand)cmd;
					drcmd.setDataOffset(taddr - tcmd.getAddress());
					drcmd.setReference(tcmd);
					return STSResult.OKAY;
				}
				else return STSResult.INVALID;
			}
		}
		else if (cmd instanceof NUSALSeqPtrTableData){
			NUSALSeqPtrTableData dcmd = (NUSALSeqPtrTableData)cmd;
			int tidx = coff >> 1;
			int taddr = dcmd.getDataValue(tidx, false);
			NUSALSeqCommand tcmd = getCommandOver(taddr);
			if(tcmd == null) return STSResult.INVALID;
			dcmd.setReference(tidx, tcmd);
			return STSResult.OKAY;
		}
		return STSResult.FAIL;
	}
	
	private STSResult reparseCommand(int addr, NUSALSeqCommand cmd){
		int caddr = cmd.getAddress();
		cmdmap.remove(caddr);
		NUSALSeqCommand ncmd = null;
		if(cmd.seqUsed()){
			ncmd = SeqCommands.parseSequenceCommand(data.getReferenceAt(caddr));
			if(ncmd == null) return STSResult.FAIL;
			ncmd.flagSeqUsed();
		}
		else{
			boolean chuse = false;
			for(int i = 0; i < 16; i++){
				if(cmd.channelUsed(i)){
					chuse = true;
					break;
				}
			}
			if(chuse){
				ncmd = ChannelCommands.parseChannelCommand(data.getReferenceAt(caddr));
				if(ncmd == null) return STSResult.FAIL;
			}
			else{
				//layer
				ncmd = LayerCommands.parseLayerCommand(data.getReferenceAt(caddr), false);
				if(ncmd == null) return STSResult.FAIL;
			}
		}
		ncmd.setAddress(caddr);
		ncmd.setTickAddress(cmd.getTickAddress());
		ncmd.setSubsequentCommand(cmd.getSubsequentCommand());
		cmdmap.put(caddr, ncmd);
		
		if((ncmd instanceof NUSALSeqReferenceCommand) || (cmd instanceof NUSALSeqPtrTableData)){
			return relinkCommand(addr, ncmd);
		}
		
		return STSResult.OKAY;
	}
	
	public STSResult storeToSelf(int addr, byte value){
		NUSALSeqCommand cmd = getCommandOver(addr);
		//System.err.println("[DEBUG] NUSALSeqReader.storeToSelf || Command found: " + cmd);
		if(cmd == null) return STSResult.FAIL;
		int coff = addr - cmd.getAddress();
		//System.err.println("[DEBUG] NUSALSeqReader.storeToSelf || Addr: 0x" + Integer.toHexString(addr) + ", coff = " + coff);
		STSResult res = cmd.storeToSelf(coff, value);
		//System.err.println("[DEBUG] NUSALSeqReader.storeToSelf || Res: " + res.name());
		switch(res){
		case FAIL:
		case INVALID:
		case OUTSIDE:
			return res;
		case OKAY:
			data.replaceByte(value, addr);
			return res;
		case RELINK:
			res = relinkCommand(addr, cmd);
			if(res == STSResult.OKAY) data.replaceByte(value, addr);
			return res;
		case REPARSE:
			data.replaceByte(value, addr);
			return reparseCommand(addr, cmd);
		default: return null;
		}
	}
	
	public STSResult storePToSelf(int addr, short value){
		NUSALSeqCommand cmd = getCommandOver(addr);
		if(cmd == null) return STSResult.FAIL;
		int coff = addr - cmd.getAddress();
		int csz = cmd.getSizeInBytes();
		if((coff + 1) < csz){
			STSResult res = cmd.storePToSelf(coff, value);
			switch(res){
			case FAIL:
			case INVALID:
			case OUTSIDE:
				return res;
			case OKAY:
				data.replaceShort(value, addr);
				return res;
			case RELINK:
				res = relinkCommand(addr, cmd);
				if(res == STSResult.OKAY) data.replaceShort(value, addr);
				return res;
			case REPARSE:
				data.replaceShort(value, addr);
				return reparseCommand(addr, cmd);
			default: return null;
			}
		}
		else{
			//Two separate commands. Really shouldn't happen.
			//But just in case.
			int vali = Short.toUnsignedInt(value);
			STSResult res1 = this.storeToSelf(addr, (byte)(vali >> 8));
			switch(res1){
			case FAIL:
			case INVALID:
			case OUTSIDE:
				return res1;
			default: break;
			}
			STSResult res2 = this.storeToSelf(addr+1, (byte)(vali));
			return res2;
		}
	}
	
	/*--- Utils ---*/
	
	public static Integer readNumber(String in){
		try{
			if(in.startsWith("0x")){
				return Integer.parseInt(in.substring(2), 16);
			}
			else return Integer.parseInt(in);
		}
		catch(NumberFormatException ex){ex.printStackTrace();}
		
		return null;
	}
	
	public static int[] readNumbers(String[] in){
		if(in == null) return null;
		int[] out = new int[in.length];
		for(int i = 0; i < in.length; i++){
			Integer n = readNumber(in[i]);
			if(n == null) return null;
			out[i] = n;
		}
		return out;
	}
	
	public static int readVLQ(BufferReference ptr){
		int b0 = Byte.toUnsignedInt(ptr.getByte());
		if((b0 & 0x80) != 0){
			//Two bytes
			int b1 = Byte.toUnsignedInt(ptr.getByte(1));
			return ((b0 & 0x7f) << 8) | b1;
		}
		return b0;
	}

	public void printMeTo(Writer out) throws IOException {
		// TODO Auto-generated method stub
		
	}

}
