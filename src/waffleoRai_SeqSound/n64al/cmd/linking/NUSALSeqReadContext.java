package waffleoRai_SeqSound.n64al.cmd.linking;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import waffleoRai_DataContainers.CoverageMap1D;
import waffleoRai_SeqSound.n64al.NUSALSeqCommand;
import waffleoRai_SeqSound.n64al.NUSALSeqCommandBook;
import waffleoRai_SeqSound.n64al.cmd.NUSALSeqReader.DataLater;
import waffleoRai_SeqSound.n64al.cmd.NUSALSeqReader.LabelCounter;
import waffleoRai_SeqSound.n64al.cmd.NUSALSeqReader.ParseError;
import waffleoRai_SeqSound.n64al.cmd.NUSALSeqReader.ParseLater;
import waffleoRai_SeqSound.n64al.cmd.NUSALSeqReader.ParseResult;
import waffleoRai_Utils.FileBuffer;

public class NUSALSeqReadContext {
	
	public FileBuffer data;
	public Map<Integer, NUSALSeqCommand> cmdmap;
	public Map<String, NUSALSeqCommand> lblmap;
	public NUSALSeqCommandBook cmdBook;
	//private Map<Integer, Integer> tickmap; //Address -> tick (first tick it can appear at)
	
	//Bookkeeping during parsing TODO (new parsing method Oct 2022)
	public List<ParseError> errors;
	public LinkedList<ParseLater> branch_queue;
	public Map<Integer, DataLater> data_parse;
	//public Map<Integer, UnknownLater> unk_parse; //Added Nov 2024
	public Map<Integer, ParseResult> subroutines;
	public int[] ch_shorton_tick;
	public int dataStart = -1;
	
	public LabelCounter seq_lbls;
	public LabelCounter[] ch_lbls;
	public LabelCounter[][] ly_lbls;
	public int data_lbl_count;
	public int ecmd_lbl_count;
	
	//Linking
	public Set<Integer> rchecked;
	public Set<Integer> rskip;
	public boolean requeue_data = false;
	public boolean linkagain_flag = false; //If set, try running another link round
	
	public CoverageMap1D cmd_coverage;
	public CoverageMap1D[] ch_shortnotes;
	
	public NUSALSeqReadContext() {
		cmdmap = new TreeMap<Integer, NUSALSeqCommand>();
		lblmap = new HashMap<String, NUSALSeqCommand>();
		errors = new LinkedList<ParseError>();
		ch_shortnotes = new CoverageMap1D[16];
		rchecked = new TreeSet<Integer>();
		rskip = new TreeSet<Integer>();
	}

}
