package waffleoRai_SeqSound.n64al.cmd;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
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
import waffleoRai_SeqSound.n64al.NUSALSeqCommandSource;
import waffleoRai_SeqSound.n64al.NUSALSeqCommands;
import waffleoRai_SeqSound.n64al.NUSALSeqDataType;
import waffleoRai_Utils.BufferReference;
import waffleoRai_DataContainers.CoverageMap1D;
import waffleoRai_DataContainers.MultiValMap;
import waffleoRai_DataContainers.TallyMap;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;
import waffleoRai_Utils.StackStack;

public class NUSALSeqReader implements NUSALSeqCommandSource{
	
	//TODO Call tick calculation still not working quite right... (Seq CZLE031, ch 2)
	//TODO Update MML parser to recognize ptbl
	
	//TODO stps on voice_offset param genning buffer instead of pointing to instruction. (stps seems to always do this)
	//TODO still not parsing voice_offset mod params in layer context, except for first in table.
	//TODO having trouble with some envelopes (0xffa in seq 01)
	
	/*--- Constants ---*/
	
	public static final int PARSEMODE_UNDEFINED = 0x0;
	public static final int PARSEMODE_SEQ = 0x1;
	public static final int PARSEMODE_CHANNEL = 0x2;
	public static final int PARSEMODE_LAYER = 0x4;
	public static final int PARSEMODE_SUB = 0x8000;
	
	public static final int LAYER_COUNT = NUSALSeq.MAX_LAYERS_PER_CHANNEL;
	
	/*--- Instance Variables ---*/
	
	private FileBuffer data;
	private Map<Integer, NUSALSeqCommand> cmdmap;
	private Map<String, NUSALSeqCommand> lblmap;
	private Map<Integer, Integer> tickmap; //Address -> tick (first tick it can appear at)
	private MultiValMap<Integer, NUSALSeqCommand> datarefs;
	private LinkedList<ParseRequest> ptr_parse_queue;
	private TallyMap sub_ticklen;
	
	//Labeling
	private int seq_subs;
	private int[] ch_subs;
	private int[][] ly_subs;
	
	private int seq_tbl_subs;
	private int[] ch_tbl_subs;
	private int[] ch_startchan;
	
	//Linking
	private Set<Integer> rchecked;
	private Set<Integer> rskip;
	
	private CoverageMap1D cmd_coverage;
	private CoverageMap1D[] ch_shortnotes;
	
	private StackStack<Integer> branch_stack;
	
	private List<MMLBlock> rdr_blocks;
	private Map<String, MMLBlock> rdr_lbl_map;
	
	/*--- Initialization ---*/
	
	public NUSALSeqReader(){
		initInternal();
	}
	
 	public NUSALSeqReader(FileBuffer seqData){
		if(seqData == null) throw new IllegalArgumentException("Data source cannot be null!");
		data = seqData;
		initInternal();
	}
 	
 	private void initInternal(){
 		cmdmap = new TreeMap<Integer, NUSALSeqCommand>();
		lblmap = new HashMap<String, NUSALSeqCommand>();
		tickmap = new TreeMap<Integer, Integer>();
		datarefs = new MultiValMap<Integer, NUSALSeqCommand>();
		ch_shortnotes = new CoverageMap1D[16];
		branch_stack = new StackStack<Integer>();
		sub_ticklen = new TallyMap();
		rchecked = new TreeSet<Integer>();
		rskip = new TreeSet<Integer>();
		ptr_parse_queue = new LinkedList<ParseRequest>();
 	}
 	
 	/*--- Inner Structs ---*/
 	
 	public static class ParseRequest{
 		public int channel;
 		public int layer;
 		public int address;
 		public int tick;
 		public String label;
 		
 		public ParseRequest(int ch, int addr, String lbl, int tick_coord){
 			channel = ch; address = addr; label = lbl;
 			tick = tick_coord; layer = -1;
 		}
 		
 		public ParseRequest(int ch, int lyr, int addr, String lbl, int tick_coord){
 			channel = ch; address = addr; label = lbl;
 			tick = tick_coord; layer = lyr;
 		}
 		
 		public void printDebug(){
 			System.err.print("ParseRequest -- CH");
 			System.err.print(String.format("%02d", channel));
 			if(layer >= 0){
 				System.err.print(String.format("-%02d", layer));
 			}
 			System.err.print(" -> 0x");
 			System.err.print(String.format("%04x", address));
 			System.err.println();
 		}
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
 	
	/*--- Read ---*/
 	
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
	
	private NUSALSeqCommand searchForModifiedInstruction_ldptbl(NUSALSeqCommand referee){
		if(referee.getCommand() != NUSALSeqCmdType.LOAD_P_TABLE) return null;
		//Scan for an stps in the same context...
		NUSALSeqCommand stps = null;
		NUSALSeqCommand current = referee.getSubsequentCommand();
		while(current != null){
			if(current.getCommand() == NUSALSeqCmdType.STORE_TO_SELF_P){
				stps = current;
				break;
			}
			else current = current.getSubsequentCommand();
		}
		if(stps == null) return null;
		
		//Check stps target.
		NUSALSeqCommand stps_trg = null;
		int addr = stps.getBranchAddress();
		for(int a = addr; a >= 0; a--){
			stps_trg = cmdmap.get(a);
			if(stps_trg != null) break;
		}
		return stps_trg;
	}
	
	private int checkPTBLTargetLayer(NUSALSeqCommand referee){
		if(referee.getCommand() != NUSALSeqCmdType.LOAD_P_TABLE) return -1;
		//Scan for an stps in the same context...
		NUSALSeqCommand stps = null;
		NUSALSeqCommand current = referee.getSubsequentCommand();
		while(current != null){
			if(current.getCommand() == NUSALSeqCmdType.STORE_TO_SELF_P){
				stps = current;
				break;
			}
			else current = current.getSubsequentCommand();
		}
		if(stps == null) return -1;
		
		//Check stps target.
		NUSALSeqCommand stps_trg = null;
		int addr = stps.getBranchAddress();
		for(int a = addr; a >= 0; a--){
			stps_trg = cmdmap.get(a);
			if(stps_trg != null) break;
		}
		
		if(stps_trg == null) return -1;
		NUSALSeqCmdType tcmd = stps_trg.getCommand();
		if(tcmd == NUSALSeqCmdType.VOICE_OFFSET){
			return stps_trg.getParam(0);
		}
		
		return -1;
	}
	
	private String genPointerTargetLabel(NUSALSeqCmdType referee_type, int ch){
		if(referee_type == NUSALSeqCmdType.CALL_TABLE){
			return "tblsub_seq_" + String.format("%03d", seq_tbl_subs++);
		}
		else if(referee_type == NUSALSeqCmdType.SET_DYNTABLE){
			return String.format("tblsub_ch%X_%03d", ch, ch_tbl_subs[ch]++);
		}
		else{
			if(ch < 0) return "sub_seq_" + String.format("%03d", seq_subs++);
			else return String.format("sub_ch%X_%03d", ch, ch_subs[ch]++);
		}
	}
	
	private boolean checkTablePointer(int value, int ch){
		if(value == 0) return true; //Assume it's a NULL
		if(value >= data.getFileSize()) return false;
		
		//See if it's part of an existing instruction or datablock
		NUSALSeqCommand prev = null;
		for(int addr = value; addr >= 0; addr--){
			prev = cmdmap.get(addr);
			if(prev != null) break;
		}
		
		if(prev != null){
			int endaddr = prev.getAddress() + prev.getSizeInBytes();
			if(value < endaddr){
				//Covered. Return whether track matches.
				int[] used = prev.getFirstUsed();
				if(ch < 0) return used[0] == ch; //-1 is seq
				else return used[0] >= 0; //If it's valid for any channel, it's okay.
			}
		}
		
		return true;
	}
	
	private int nextScanStart(){
		int datend = (int)data.getFileSize();
		int cvrend = cmd_coverage.getMaxCoveredValue();
		int pos = cvrend;
		//Make sure we don't run into any data (not included in coverage map)
		while(pos < datend){
			if(cmdmap.containsKey(pos)) return -1;
			if(data.getByte(pos) != 0) return pos;
			pos++;
		}
		return -1;
	}
	
	private List<int[]> linkReferences(){
		if(cmdmap.isEmpty()) return null;
		if(cmd_coverage != null) cmd_coverage.mergeBlocks();
		List<int[]> newstarts = new LinkedList<int[]>();
		
		List<Integer> alist = new ArrayList<Integer>(cmdmap.size());
		alist.addAll(cmdmap.keySet());
		//Collections.sort(alist);
		
		//Reset label map, then add existing labels.
		lblmap.clear(); datarefs.clearValues();
		for(Integer addr : alist){
			NUSALSeqCommand cmd = cmdmap.get(addr);
			if(cmd.getLabel() != null) lblmap.put(cmd.getLabel(), cmd);
		}
		while((rchecked.size() + rskip.size()) < cmdmap.size()){
			for(Integer addr : alist){
				if(rchecked.contains(addr)) continue;
				NUSALSeqCommand cmd = cmdmap.get(addr);
				if(cmd instanceof NUSALSeqReferenceCommand){
					int refaddr = cmd.getBranchAddress();
					NUSALSeqCommand trg = cmdmap.get(refaddr);
					if(trg != null){
						cmd.setReference(trg);
						//Give the referenced command a label, if it doesn't already have one.
						int[] trgctx = trg.getFirstUsed();
						if(trg.getLabel() == null){
							if(cmd.getCommand() == NUSALSeqCmdType.CALL){
								String l = "sub";
								if(trgctx[0] < 0){
									//Seq
									l += "_seq_" + String.format("%03d", seq_subs++);
								}
								else{
									//Ch or ly
									int c = trgctx[0];
									if(trgctx[1] < 0){
										//Ch
										l += String.format("_ch%X_%03d", c, ch_subs[c]++);
									}
									else{
										//Ly
										int ly = trgctx[1];
										l += String.format("_ch%X-ly%d_%03d", c, ly, ly_subs[c][ly]++);
									}
								}
								trg.setLabel(l);
								lblmap.put(l, trg);	
							}
							else{
								int n = 0;
								String l = null;
								if(trgctx[0] < 0){
									//Seq
									l = "seq_block";
								}
								else{
									//Ch or ly
									int c = trgctx[0];
									if(trgctx[1] < 0){
										//Ch
										l = String.format("ch%X_block", c);
									}
									else{
										//Ly
										int ly = trgctx[1];
										l = String.format("ch%X-ly%d_block", c, ly);
									}
								}
								String testlbl = l + String.format("%03d", n);
								while(lblmap.containsKey(testlbl)){
									n++;
									testlbl = l + String.format("%03d", n);
								}
								trg.setLabel(testlbl);
								lblmap.put(testlbl, trg);	
							}
						}
					}
					else {
						//Note reference
						datarefs.addValue(refaddr, cmd);
					}
					rchecked.add(addr);
				}
				else if (cmd instanceof NUSALSeqPtrTableData){
					//Scan table again to see if there are...
					//	A. Any yet unlinked refs
					//	B. Any invalid ptrs that should trigger a shrinking of the table
					NUSALSeqPtrTableData dcmd = (NUSALSeqPtrTableData)cmd;
					int tsize = dcmd.getUnitCount();
					int resize = 0;
					int usech = dcmd.getFirstChannelUsed();
					for(int i = 0; i < tsize; i++){
						if(dcmd.getReference(i) == null){
							int raddr = dcmd.getDataValue(i, false);
							NUSALSeqCommand trg = cmdmap.get(raddr);
							if(trg != null){
								dcmd.setReference(i, trg);
							}
							else{
								if(checkTablePointer(raddr, usech)){
									if(dcmd.readAsSubPointers() && raddr != 0 && !cmdmap.containsKey(raddr)){
										ptr_parse_queue.add(new ParseRequest(usech, dcmd.getLayer(), raddr, genPointerTargetLabel(null, usech), dcmd.getTickAddress()));
										System.err.println(String.format("Table @ 0x%04x added parse request for 0x%04x (point A)", addr, raddr));
									}
								}
								else break; //Invalid pointer	
							}
							resize++;
						}
					}
					if(resize < tsize) dcmd.resize(resize);
				}
				else rchecked.add(addr);
				//rchecked.add(addr); //We don't want this flagged for pointer tables because we want it to scan each time.
			}	
			alist.clear();
			alist.addAll(cmdmap.keySet());
		}
		
		//Process data
		if(!datarefs.isEmpty()){
			alist.clear();
			alist.addAll(datarefs.getOrderedKeys());
			Collections.reverse(alist);
			int max_addr = (int)data.getFileSize();
			
			int n = 0; int m = 0;
			for(int addr : alist){
				//First, is there anything already there? Can this overlap things?
				List<NUSALSeqCommand> cmdlist = datarefs.getValues(addr);
				if(cmdlist == null || cmdlist.isEmpty()) continue;
				NUSALSeqCommand referee = cmdlist.get(0);
				NUSALSeqCmdType ctype = referee.getCommand();
				if(ctype.flagSet(NUSALSeqCommands.FLAG_REFOVERLAP)){
					if(cmd_coverage.isCovered(addr)){
						//Referencing something already existing...
						int ovrl = -1; int pos = addr;
						NUSALSeqCommand ovl_obj = null;
						while(pos > 0 && ovl_obj == null){
							ovl_obj = cmdmap.get(pos--);
							ovrl++;
						}
						if(ovl_obj == null){
							System.err.println("NUSALSeqReader.linkReferences || WARNING: "
									+ "The following reference could not be resolved: 0x" + Integer.toHexString(addr));
							continue;
						}
						
						String lbl = ovl_obj.getLabel();
						if(ovl_obj.getLabel() == null){
							lbl = String.format(".dataref%03d", m++);
							while(lblmap.containsKey(lbl)){
								m++;
								lbl = String.format(".dataref%03d", m++);
							}
							ovl_obj.setLabel(lbl);
							lblmap.put(lbl, ovl_obj);
						}
						
						for(NUSALSeqCommand cmd : cmdlist){
							if(cmd instanceof NUSALSeqDataRefCommand){
								NUSALSeqDataRefCommand drcmd = (NUSALSeqDataRefCommand)cmd;
								drcmd.setDataOffset(ovrl);
								drcmd.setReference(ovl_obj);
							}
						}
						
						continue;
					}
				}
				
				int end_addr = addr+1;
				while(end_addr < max_addr && (!cmd_coverage.isCovered(end_addr))){
					end_addr++;
				}
				
				NUSALSeqDataCommand dcmd = DataCommands.parseData(ctype, data.getReferenceAt(addr), end_addr);
				if(dcmd == null){
					System.err.println("NUSALSeqReader.linkReferences || WARNING: "
							+ "The following reference could not be resolved: 0x" + Integer.toHexString(addr));
					continue;
				}
				
				NUSALSeqDataType dtype = dcmd.getDataType();
				if(dtype == NUSALSeqDataType.CALLTABLE || dtype == NUSALSeqDataType.P_TABLE){
					//DataCommands.parseData will read into table as far as it can (til it hits table type max, covered block, or end of seq)
					//	This should take a look at the contents of the table to further shorten it and add suspected jump targets to parse queue
					int usech = -1;
					int usely = -1;
					boolean data_likely = false;
					if(dtype == NUSALSeqDataType.P_TABLE){
						//This will be channel context, BUT it can point to a table
						//	of values to use for startlayer/dynstartlayer
						//If referee type is ldptbl check next stps target?
						//usely = this.checkPTBLTargetLayer(referee);
						NUSALSeqCommand modtrg = searchForModifiedInstruction_ldptbl(referee);
						if(modtrg != null){
							NUSALSeqCmdType mcmd = modtrg.getCommand();
							if(mcmd == NUSALSeqCmdType.VOICE_OFFSET){
								usely = modtrg.getParam(0);
							}
							else{
								//TODO Check if data only?
								//rn default to data only...
								data_likely = true;
							}
						}
						if(usely < 0){
							//Regular channel command
							usech = referee.getFirstChannelUsed();
						}
						else{
							//Layer context. 
							usech = referee.getFirstUsed()[0];
						}
					}
					
					NUSALSeqPtrTableData pcmd;
					int resize = 0;
					int unitsmax = dcmd.getUnitCount();
					for(int j = 0; j < unitsmax; j++){
						int p = dcmd.getDataValue(j, false);
						if(!checkTablePointer(p, usech)) break;
						if(p != 0 && !cmdmap.containsKey(p) && !data_likely){
							ptr_parse_queue.add(new ParseRequest(usech, usely, p, genPointerTargetLabel(ctype, usech), referee.getTickAddress()));	
							System.err.println(String.format("Table @ 0x%04x added parse request for 0x%04x (point B)", addr, p));
						}
						resize++;
					}
					pcmd = new NUSALSeqPtrTableData(dtype, resize);
					pcmd.data = dcmd.data;
					pcmd.setReadAsSubPointers(!data_likely);
					if(usech >= 0) pcmd.flagChannelUsed(usech);
					dcmd = pcmd;
					pcmd.setLayer(usely);
					rskip.add(addr);
				}
				else rchecked.add(addr);
				
				dcmd.setAddress(addr);
				dcmd.setTickAddress(referee.getTickAddress());
				cmdmap.put(addr, dcmd);
				//cmd_coverage.addBlock(addr, addr + dcmd.getSizeInBytes());
				String lbl = String.format(".data%03d", n++);
				while(lblmap.containsKey(lbl)){
					n++;
					lbl = String.format(".data%03d", n++);
				}
				dcmd.setLabel(lbl);
				lblmap.put(lbl, dcmd);
				//rchecked.add(addr);
				
				for(NUSALSeqCommand cmd : cmdlist) cmd.setReference(dcmd);
			}
		}
		
		//Set _entry label for 0x0000
		NUSALSeqCommand cmd = cmdmap.get(0);
		if(cmd != null) {
			cmd.setLabel("_entry");
			lblmap.put(cmd.getLabel(), cmd);
		}
		
		return newstarts;
	}
	
	private boolean listHasAddr(List<int[]> list, int addr){
		if(list == null) return false;
		for(int[] iarr : list){
			if(iarr[0] == addr) return true;
		}
		return false;
	}
	
	private void readTrack(List<int[]> starts, List<List<int[]>> childlists, CoverageMap1D parentmap, int mode, int chidx, int lidx){
		int pos = 0;
		int tick = 0;
		int sub_addr = -1; //Current subroutine
		NUSALSeqCommand cmd = null, prev_cmd = null;
		LinkedList<int[]> branches = new LinkedList<int[]>();
		LinkedList<Integer> opbranches = new LinkedList<Integer>();
		LinkedList<Integer> callstack = new LinkedList<Integer>();
		if(starts != null && !starts.isEmpty()) branches.addAll(starts);
		int[] pp = null;
		if(!branches.isEmpty()){
			pp = branches.pop();
			pos = pp[0]; tick = pp[1];
		}
		
		String modestr = "sequence";
		if(mode == PARSEMODE_CHANNEL){
			modestr = "channel";
			parentmap = new CoverageMap1D();
		}
		else if(mode == PARSEMODE_LAYER) modestr = "layer";
		
		//Finish implementing long/short read switch. Channel maps when in short mode, passes map to voices
		//	voices then need to check mode.
		boolean l_shortnotes = false;
		int sn_starttick = -1; 
		boolean suppress_nullcheck = false;
		boolean popflag = false;
		if(!branch_stack.loadState(pos)) branch_stack.clear();
		branch_stack.push(pos);
		while(true){
			//Has this command already been seen?
			if(cmdmap.containsKey(pos) || popflag){
				if(!branches.isEmpty()){
					//Pop branch
					prev_cmd = null;
					pp = branches.pop();
					pos = pp[0]; tick = pp[1];
					
					//Does the branch stack have a stack for this one already?
					if(!branch_stack.loadState(pos)) branch_stack.clear();
					branch_stack.push(pos);
					popflag = false;
					suppress_nullcheck = false;
					
					continue;
				}
				else if(!opbranches.isEmpty()){
					suppress_nullcheck = true;
					prev_cmd = null;
					pos = opbranches.pop();
					tick = -1;
					
					if(!branch_stack.loadState(pos)) branch_stack.clear();
					branch_stack.push(pos);
					popflag = false;
					
					continue;
				}
				else break;
			}
			
			//Check to see if empty/buffer
			int zerocount = 0;
			for(int p = pos; p < data.getFileSize(); p++){
				if(data.getByte(p) != 0) break;
				zerocount++;
			}
			
			//Parse
			switch(mode){
			case PARSEMODE_SEQ:
				if(zerocount >= 2){
					cmd = new NUSALSeqDataCommand(NUSALSeqDataType.BUFFER, 1);
					popflag = true;
				}
				else cmd = SeqCommands.parseSequenceCommand(data.getReferenceAt(pos));
				if(cmd != null) cmd.flagSeqUsed();
				break;
			case PARSEMODE_CHANNEL:
				if(zerocount >= 1){
					//00 would be cdelay 0 ticks. I guess you could use it, but seems silly.
					cmd = new NUSALSeqDataCommand(NUSALSeqDataType.BUFFER, 1);
					popflag = true;
				}
				else cmd = ChannelCommands.parseChannelCommand(data.getReferenceAt(pos));
				if(cmd != null) cmd.flagChannelUsed(chidx);
				break;
			case PARSEMODE_LAYER:
				if(zerocount >= 2){
					//00 00... would be note 0 for zero ticks. Doesn't make sense.
					cmd = new NUSALSeqDataCommand(NUSALSeqDataType.BUFFER, 1);
					popflag = true;
				}
				else{
					l_shortnotes = (parentmap != null) && parentmap.isCovered(tick);
					cmd = LayerCommands.parseLayerCommand(data.getReferenceAt(pos), l_shortnotes);	
				}
				if(cmd != null) cmd.flagLayerUsed(chidx, lidx);
				break;
			}	
			if(cmd == null){
				if(!suppress_nullcheck) throw new NUSALSeq.UnrecognizedCommandException(data.getByte(pos), pos, modestr);
				else{
					popflag = true;
					continue;
				}
			}
			cmd.setAddress(pos);
			cmdmap.put(pos, cmd);
			cmd_coverage.addBlock(pos, pos + cmd.getSizeInBytes());
			if(tick >= 0){
				tickmap.put(pos, tick);
				cmd.setTickAddress(tick);
			}
			if(sub_addr >= 0){
				sub_ticklen.increment(sub_addr, cmd.getSizeInTicks());
			}
			//DEBUG PRINT
			//System.err.println("added to map: 0x" + Integer.toHexString(pos) + " -- " + cmd.toString());
			System.err.print("NUSALSeqReader.readTrack || ");
			System.err.print(String.format("0x%04x ", cmd.getAddress()));
			System.err.print("tick=" + cmd.getTickAddress() + " ");
			switch(mode){
			case PARSEMODE_SEQ:
				System.err.print("SEQ ");
				break;
			case PARSEMODE_LAYER:
				System.err.print("LYR" + lidx + " ");
			case PARSEMODE_CHANNEL:
				System.err.print("CHN" + chidx + " ");
				break;
			}
			System.err.print(cmd.toMMLCommand());
			System.err.print("\n");
			
			//Analyze logic
			if(prev_cmd != null) prev_cmd.setSubsequentCommand(cmd);
			prev_cmd = cmd;
			if(cmd.isBranch()){
				//Is it a channel start command, or seq branch?
				NUSALSeqCmdType e_cmd = cmd.getCommand();
				if(e_cmd == NUSALSeqCmdType.CHANNEL_OFFSET || e_cmd == NUSALSeqCmdType.CHANNEL_OFFSET_REL){
					int ch = cmd.getParam(0);
					int addr = cmd.getBranchAddress();
					List<int[]> chlist = childlists.get(ch);
					if(!listHasAddr(chlist, addr)) chlist.add(new int[]{addr, tick});
					pos += cmd.getSizeInBytes();
					tick += cmd.getSizeInTicks();
					
					branch_stack.saveState(addr);
				}
				else if(e_cmd == NUSALSeqCmdType.CHANNEL_OFFSET_C){
					int ch = cmd.getParam(0);
					int addr = cmd.getBranchAddress();
					ptr_parse_queue.add(new ParseRequest(ch, addr, String.format("ch%X_startchan_%03d", ch, ch_startchan[ch]++), tick));
					
					pos += cmd.getSizeInBytes();
					tick += cmd.getSizeInTicks();
					
					branch_stack.saveState(addr);
				}
				else if(e_cmd == NUSALSeqCmdType.VOICE_OFFSET || e_cmd == NUSALSeqCmdType.VOICE_OFFSET_REL){
					int ch = cmd.getParam(0);
					int addr = cmd.getBranchAddress();
					//System.err.println("pos = 0x" + Integer.toHexString(pos));
					List<int[]> chlist = childlists.get(ch);
					if(!listHasAddr(chlist, addr)) chlist.add(new int[]{addr, tick});
					pos += cmd.getSizeInBytes();
					tick += cmd.getSizeInTicks();
					
					branch_stack.saveState(addr);
				}
				else if(e_cmd == NUSALSeqCmdType.CALL){
					//Has this call been seen before?
					int call_addr = cmd.getBranchAddress();
					if(sub_ticklen.hasEntry(call_addr)){
						tick += sub_ticklen.getCount(call_addr);
						pos += cmd.getSizeInBytes();
					}
					else{
						if(sub_addr >= 0) callstack.push(sub_addr);
						sub_addr = call_addr;
						pos += cmd.getSizeInBytes();
						if(!opbranches.contains(pos)){
							opbranches.add(pos);
							branch_stack.saveState(pos);
						}
						
						pos = call_addr;
						sub_ticklen.setZero(call_addr);
					}
				}
				else{
					int addr = cmd.getBranchAddress();
					
					//If jump is conditional, continue to next command.
					if(e_cmd != NUSALSeqCmdType.BRANCH_ALWAYS && e_cmd != NUSALSeqCmdType.BRANCH_ALWAYS_REL){
						if(!listHasAddr(branches, addr)){
							branches.add(new int[]{addr, tick});
							branch_stack.saveState(addr);
						}
						pos += cmd.getSizeInBytes();
					}
					else{
						//Take jump
						//Commented out region saves current position.
						//	I have removed this as it seems to be doing more harm than good.
						/*pos += cmd.getSizeInBytes();
						if(!opbranches.contains(pos)){
							if(data.getByte(pos) != 0){
								opbranches.add(pos);
								branch_stack.saveState(pos);	
							}
						}*/
						pos = addr;
					}
					tick += cmd.getSizeInTicks();
				}
			}
			else{
				//If it is an end, then pop branch or break
				if(cmd.getCommand() == NUSALSeqCmdType.END_READ){
					if(!branches.isEmpty()){
						//Pop branch
						prev_cmd = null;
						pp = branches.pop();
						pos = pp[0]; tick = pp[1];
						
						//Does the branch stack have a stack for this one already?
						if(!branch_stack.loadState(pos)) branch_stack.clear();
						branch_stack.push(pos);
						popflag = false;
						suppress_nullcheck = false;
						
						continue;
					}
					else if(!opbranches.isEmpty()){
						suppress_nullcheck = true;
						prev_cmd = null;
						pos = opbranches.pop();
						tick = -1; 
						
						if(sub_addr >= 0){
							if(callstack.isEmpty()) sub_addr = -1;
							else sub_addr = callstack.pop();
						}
						
						if(!branch_stack.loadState(pos)) branch_stack.clear();
						branch_stack.push(pos);
						popflag = false;
						
						continue;
					}
					else break;
				}
				if(cmd.getCommand() == NUSALSeqCmdType.SHORTNOTE_ON){
					if(sn_starttick < 0){
						sn_starttick = tick;
						if(ch_shortnotes[chidx] == null) ch_shortnotes[chidx] = new CoverageMap1D();
					}
				}
				else if(cmd.getCommand() == NUSALSeqCmdType.SHORTNOTE_OFF){
					if(sn_starttick >= 0){
						ch_shortnotes[chidx].addBlock(sn_starttick, tick);
						sn_starttick = -1;
					}
				}
				//Otherwise, just advance to next command.
				pos += cmd.getSizeInBytes();
				tick += cmd.getSizeInTicks();
			}
		}
		if(sn_starttick >= 0){
			ch_shortnotes[chidx].addBlock(sn_starttick, tick);
			sn_starttick = -1;
		}
		
		//If in seq or channel mode, do children
		if(mode == PARSEMODE_SEQ){
			List<List<int[]>> vlists = new ArrayList<List<int[]>>(LAYER_COUNT);
			for(int i = 0; i < LAYER_COUNT; i++)vlists.add(new LinkedList<int[]>());
			for(int i = 0; i < 16; i++){
				readTrack(childlists.get(i), vlists, null, PARSEMODE_CHANNEL, i, -1);
				
				//Clear voice lists for next channel.
				for(int j = 0; j < LAYER_COUNT; j++) vlists.get(j).clear();
			}
		}
		else if(mode == PARSEMODE_CHANNEL){
			//Just use the parentmap local variable to fill for a channel.
			for(int i = 0; i < LAYER_COUNT; i++){
				readTrack(childlists.get(i), null, ch_shortnotes[chidx], PARSEMODE_LAYER, chidx, i);
			}
		}
		
	}
	
	public void preParse(){
		cmdmap.clear();
		cmd_coverage = new CoverageMap1D();
		branch_stack.clear();
		datarefs.clearValues();
		tickmap.clear();
		rchecked.clear();
		
		seq_subs = 0;
		ch_subs = new int[16];
		ly_subs = new int[16][NUSALSeq.MAX_LAYERS_PER_CHANNEL];
		seq_tbl_subs = 0;
		ch_tbl_subs = new int[16];
		ch_startchan = new int[16];
		
		List<List<int[]>> clists = new ArrayList<List<int[]>>(16);
		for(int i = 0; i < 16; i++) clists.add(new LinkedList<int[]>());
		
		List<List<int[]>> vlists = new ArrayList<List<int[]>>(LAYER_COUNT);
		for(int i = 0; i < LAYER_COUNT; i++) vlists.add(new LinkedList<int[]>());
		List<int[]> pstarts = new LinkedList<int[]>();
		
		List<int[]> remaining = null;
		do{
			if(!cmdmap.containsKey(0) || (remaining != null && !remaining.isEmpty())){
				readTrack(remaining, clists, null, PARSEMODE_SEQ, -1, -1);	
			}
			while(!ptr_parse_queue.isEmpty()){
				pstarts.clear();
				ParseRequest req = ptr_parse_queue.pop();
				//System.err.println("Request found: ch = " + req.channel + ", raddr = 0x" + Integer.toHexString(req.address));
				req.printDebug();
				pstarts.add(new int[]{req.address, req.tick});
				if(req.channel < 0){
					readTrack(pstarts, clists, null, PARSEMODE_SEQ, -1, -1);
				}
				else if(req.layer >= 0){
					readTrack(pstarts, null, ch_shortnotes[req.channel], PARSEMODE_LAYER, req.channel, req.layer);
				}
				else{
					readTrack(pstarts, vlists, null, PARSEMODE_CHANNEL, req.channel, -1);
				}
				//Label result
				NUSALSeqCommand cmd = cmdmap.get(req.address);
				if(cmd != null && req.label != null) cmd.setLabel(req.label);
			}
			remaining = linkReferences();
			
			//Now check coverage to see if large non-zero chunk uncovered.
			if(ptr_parse_queue.isEmpty()){
				int nextstart = nextScanStart();
				if(nextstart > 0){
					if(remaining == null) remaining = new LinkedList<int[]>();
					remaining.add(new int[]{nextstart, 0});
				}	
			}
			
		} while((remaining != null && !remaining.isEmpty()) || !ptr_parse_queue.isEmpty());
		
		//Find dalloc buffers and extend them to next command
		List<Integer> addrlist = new ArrayList<Integer>(cmdmap.size()+1);
		addrlist.addAll(cmdmap.keySet());
		Collections.sort(addrlist);
		Collections.reverse(addrlist);
		int lastaddr = (int)data.getFileSize();
		for(Integer addr : addrlist){
			NUSALSeqCommand cmd = cmdmap.get(addr);
			if(cmd instanceof NUSALSeqDataCommand){
				NUSALSeqDataCommand dcmd = (NUSALSeqDataCommand)cmd;
				if(dcmd.getDataType() == NUSALSeqDataType.BUFFER){
					dcmd.reallocate(lastaddr - addr);
				}
				else if((addr + dcmd.getSizeInBytes()) > lastaddr){
					//Shrink.
					dcmd.reallocate(lastaddr - addr);
				}
			}
			lastaddr = addr;
		}
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
