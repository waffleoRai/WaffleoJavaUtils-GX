package waffleoRai_SeqSound.n64al;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import waffleoRai_SeqSound.n64al.cmd.BasicCommandMap;
import waffleoRai_SeqSound.n64al.cmd.NUSALSeqCommandChunk;
import waffleoRai_SeqSound.n64al.cmd.NUSALSeqReader;
import waffleoRai_SeqSound.n64al.cmd.NUSALSeqReader.MMLBlock;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;
import waffleoRai_Utils.StackStack;

public class NUSMMLSplitter {
	
	public static final String SUFFIX_SEQ = "_master";
	public static final String SUFFIX_CHANNEL = "_ch";
	public static final String SUFFIX_LAYER = "-ly";
	
	private NUSALSeqCommandSource cmdsrc;
	private StackStack<int[]> stack;
	
	private NUSALSeqCommandChunk cch_seq;
	private NUSALSeqCommandChunk[] cch_ch;
	private NUSALSeqCommandChunk[][] cch_ly;
	
	public NUSMMLSplitter(){
		cch_ch = new NUSALSeqCommandChunk[16];
		cch_ly = new NUSALSeqCommandChunk[16][8];
		stack = new StackStack<int[]>();
	}

	public static class MMLTsec{
		public List<MMLBlock> maintrack;
		public List<MMLBlock> subs;
		public MMLTsec[] children;
		
		public MMLTsec(){
			maintrack = new LinkedList<MMLBlock>();
			subs = new LinkedList<MMLBlock>();
		}
	}

	public static String genMasterScriptPath(String stem){
		return stem + SUFFIX_SEQ + ".mus";
	}
	
	public static String genChannelScriptPath(String stem, int ch){
		return stem + SUFFIX_CHANNEL + String.format("%02d.mus", ch);
	}
	
	public static String genLayerScriptPath(String stem, int ch, int ly){
		return stem + SUFFIX_CHANNEL + String.format("%02d%s%d.mus", ch, SUFFIX_LAYER, ly);
	}

	private static int getStateTag(int ch, int ly){
		int stateval = -1;
		if(ch >= 0){
			stateval = ch << 8;
			if(ly >= 0) stateval |= ly;
			else stateval |= 0xff;
		}
		return stateval;
	}
	
	private void processTrack(NUSALSeqCommandChunk chunk, int ch, int ly, int state_val){
		//pos[0] = addr
		//pos[1] = tick
		
		//int stateval = getStateTag(ch, ly);
		//if(!stack.loadState(stateval)) return;
		int[] pos = stack.pop();
		if(pos == null) return;
		LinkedList<NUSALSeqCommandChunk> subroutines = new LinkedList<NUSALSeqCommandChunk>();
		Set<Integer> doneaddr = new HashSet<Integer>();
		Map<Integer, Integer> sublens = new HashMap<Integer, Integer>();
		//LinkedList<int[]> callstack = new LinkedList<int[]>();
		
		//Do this track until hit an end and no branches left.
		NUSALSeqCommand cmd = null;
		while(true){
			if(!doneaddr.contains(pos[0])){
				cmd = cmdsrc.getCommandAt(pos[0]);
			}
			else cmd = null;
			doneaddr.add(pos[0]);
			
			if(cmd != null){
				cmd.setTickAddress(pos[1]);
				chunk.addCommand(cmd);
				
				NUSALSeqCmdType ctype = cmd.getFunctionalType();
				switch(ctype){
				case BRANCH_IF_LTZ_REL:
				case BRANCH_IF_EQZ_REL:
				case BRANCH_IF_GTEZ:
				case BRANCH_IF_LTZ:
				case BRANCH_IF_EQZ:
					pos[0] += cmd.getSizeInBytes();
					stack.add(new int[]{cmd.getBranchTarget().getAddress(), pos[1]});
					break;
				case BRANCH_ALWAYS_REL:
				case BRANCH_ALWAYS:
					pos[0] = cmd.getBranchTarget().getAddress();
					break;
				case CALL:
					pos[0] += cmd.getSizeInBytes();
					int calltarg = cmd.getBranchTarget().getAddress();
					Integer sublen = sublens.get(calltarg);
					if(sublen == null){
						//Read this subroutine
						int substval = 0x80000000 | calltarg;
						stack.saveState(state_val); stack.clear();
						stack.add(new int[]{calltarg, 0});
						NUSALSeqCommandChunk subch = new NUSALSeqCommandChunk();
						processTrack(subch, ch, ly, substval);
						stack.loadState(state_val);
						subroutines.add(subch);
						sublen = subch.getSizeInTicks();
						sublens.put(calltarg, sublen);
					}
					pos[1] += sublen;
					break;
				case CHANNEL_OFFSET:
				case CHANNEL_OFFSET_REL:
					int chstate = getStateTag(cmd.getParam(0), -1);
					stack.saveState(state_val); stack.clear();
					stack.loadState(chstate);
					stack.add(new int[]{cmd.getBranchTarget().getAddress(), pos[1]});
					stack.saveState(chstate);
					stack.loadState(state_val);
					pos[0] += cmd.getSizeInBytes();
					break;
				case VOICE_OFFSET:
				case VOICE_OFFSET_REL:
					int lystate = getStateTag(ch, cmd.getParam(0));
					stack.saveState(state_val); stack.clear();
					stack.loadState(lystate);
					stack.add(new int[]{cmd.getBranchTarget().getAddress(), pos[1]});
					stack.saveState(lystate);
					stack.loadState(state_val);
					pos[0] += cmd.getSizeInBytes();
					break;
				default:
					pos[1] = cmd.getSizeInTicks();
					pos[0] += cmd.getSizeInBytes();
					break;
				}
			}
			if(cmd == null || cmd.isEndCommand()){
				//Pop branch.
				if(stack.currentEmpty()) break;
				pos = stack.pop();
				if(pos == null) break;
			}
		}
		
		//Add subroutines back to chunk.
		for(NUSALSeqCommandChunk subch : subroutines) chunk.addCommand(subch);
	
		//Do children
		if((state_val & 0x80000000) == 0){
			//Not a subroutine
			if(ch < 0){
				//Seq
				for(int i = 0; i < 16; i++){
					int chval = getStateTag(i, -1);
					stack.saveState(state_val);
					stack.loadState(chval);
					if(!stack.currentEmpty()){
						if (cch_ch[i] == null) cch_ch[i] = new NUSALSeqCommandChunk();
						processTrack(cch_ch[i], i, -1, chval);	
					}
					stack.loadState(state_val);
				}
			}
			else if(ly < 0){
				//Channel
				for(int i = 0; i < 8; i++){
					int lval = getStateTag(ch, i);
					stack.saveState(state_val);
					stack.loadState(lval);
					if(!stack.currentEmpty()){
						NUSALSeqCommandChunk tchunk = null;
						if (cch_ly[ch][i] == null) cch_ly[ch][i] = new NUSALSeqCommandChunk();
						tchunk = cch_ly[ch][i];
						processTrack(tchunk, ch, i, lval);	
					}
					stack.loadState(state_val);
				}
			}
		}
		
	}
	
	private static void writeMMLCommand(Writer writer, NUSALSeqCommand cmd) throws IOException{
		if(cmd.isChunk()){
			NUSALSeqCommandChunk chunk = (NUSALSeqCommandChunk)cmd;
			List<NUSALSeqCommand> commands = chunk.getCommands();
			for(NUSALSeqCommand cmd2 : commands) writeMMLCommand(writer, cmd2);
		}
		else{
			String lbl = cmd.getLabel();
			if(lbl != null){
				writer.write(lbl);
				writer.write(":\n");
			}
			writer.write("\t");
			writer.write(cmd.toMMLCommand());
			writer.write("\n");
		}
	}
	
	public static boolean splitSeq(NUSALSeq seq, boolean split_layers, String outstem) throws IOException{
		if(seq == null || outstem == null) return false;
		NUSMMLSplitter splitter = new NUSMMLSplitter();
		splitter.cmdsrc = seq.getCommandSource();
		splitter.stack.add(new int[]{0,0});
		//splitter.stack.saveState(getStateTag(-1,-1));
		splitter.cch_seq = new NUSALSeqCommandChunk();
		splitter.processTrack(splitter.cch_seq, -1, -1, 0xffff);
		
		//Seq
		BufferedWriter bw = new BufferedWriter(new FileWriter(genMasterScriptPath(outstem)));
		bw.write(";Zelda 64 MML\n");
		bw.write(";Auto-generated by waffleoUtilsGX\n");
		bw.write(";Sequence Master Track\n\n");
		writeMMLCommand(bw, splitter.cch_seq);
		bw.close();
		
		//Channels & Layers
		for(int i = 0; i < 16; i++){
			if(splitter.cch_ch[i] == null) continue;
			bw = new BufferedWriter(new FileWriter(genChannelScriptPath(outstem, i)));
			bw.write(";Zelda 64 MML\n");
			bw.write(";Auto-generated by waffleoUtilsGX\n");
			if(split_layers) bw.write(";Channel " + i + " control\n\n");
			else bw.write(";Channel " + i + " track\n\n");
			writeMMLCommand(bw, splitter.cch_ch[i]);
			for(int j = 0; j < 8; j++){
				if(splitter.cch_ly[i][j] == null) continue;
				if(split_layers){
					BufferedWriter bw2 = new BufferedWriter(new FileWriter(genLayerScriptPath(outstem,i,j)));
					bw2.write(";Zelda 64 MML\n");
					bw2.write(";Auto-generated by waffleoUtilsGX\n");
					bw2.write(";Channel " + i + ", Layer " + j + " track\n\n");
					writeMMLCommand(bw2, splitter.cch_ly[i][j]);
					bw2.close();
				}
				else{
					writeMMLCommand(bw, splitter.cch_ly[i][j]);
				}
			}
			bw.close();
		}
		
		return true;
	}
	
	public static NUSALSeq mergeSeq(String mml_pathstem, NUSALSeqCommandBook cmdBook) throws IOException, UnsupportedFileTypeException{
		String scriptpath = genMasterScriptPath(mml_pathstem);
		if(!FileBuffer.fileExists(scriptpath)){
			System.err.println("Could master track: " + scriptpath);
			return null;
		}
		
		//Read scripts into memory
		LinkedList<MMLTsec> timeblocks = new LinkedList<MMLTsec>();
		NUSALSeqReader reader = new NUSALSeqReader();
		BufferedReader br = new BufferedReader(new FileReader(scriptpath));
		reader.readInMMLScript(br);
		br.close();
		
		List<MMLBlock> rdrblocks = reader.getReaderMMLBlocks();
		for(MMLBlock rblock : rdrblocks){
			MMLTsec tsec = new MMLTsec();
			tsec.children = new MMLTsec[16];
			tsec.maintrack.add(rblock);
			timeblocks.add(tsec);
		}
		
		//Channel & layer scripts...
		for(int i = 0; i < 16; i++){
			scriptpath = genChannelScriptPath(mml_pathstem,i);
			if(!FileBuffer.fileExists(scriptpath)) continue;
			br = new BufferedReader(new FileReader(scriptpath));
			reader.readInMMLScript(br);
			br.close();
			
			for(int j = 0; j < 8; j++){
				scriptpath = genLayerScriptPath(mml_pathstem,i,j);
				if(!FileBuffer.fileExists(scriptpath)) continue;
				br = new BufferedReader(new FileReader(scriptpath));
				reader.readInMMLScript(br);
				br.close();
			}
		}
		
		//Parse seq level
		MMLBlock previous = null;
		for(MMLTsec tsec : timeblocks){
			for(MMLBlock mblock : tsec.maintrack){
				reader.parseMMLBlock(mblock, previous);
				previous = mblock;
			}
		}
		
		//Scan seq blocks for references
		Set<String> used_lbls = new HashSet<String>();
		Map<String, MMLBlock> rdr_lbl_map = reader.getReaderMMLMap();
		for(MMLTsec tsec : timeblocks){
			for(MMLBlock sblock : tsec.maintrack){
				List<NUSALSeqCommand> cmdlist = sblock.chunk.getCommands();
				for(NUSALSeqCommand cmd : cmdlist){
					NUSALSeqCmdType ctype = cmd.getFunctionalType();
					if(ctype.flagSet(NUSALSeqCommands.FLAG_OPENTRACK)){
						//Assumed channel open.
						//System.err.println("Open channel command found");
						int ch = cmd.getParam(0);
						NUSALSeqCommand targ = cmd.getBranchTarget();
						if(tsec.children[ch] == null) {
							tsec.children[ch] = new MMLTsec();
							tsec.children[ch].children = new MMLTsec[8];
						}
						MMLBlock chblock = rdr_lbl_map.get(targ.getLabel());
						reader.parseMMLBlock(chblock, null);
						tsec.children[ch].maintrack.add(chblock);
					}
				}
				
				//Do channels that have been added to that block
				//Look for layers as well as subs (and subs in layers)
				for(int i = 0; i < 16; i++){
					if(tsec.children[i] == null) continue;
					MMLTsec chsec = tsec.children[i];
					for(MMLBlock cblock : chsec.maintrack){
						cmdlist = cblock.chunk.getCommands();
						for(NUSALSeqCommand cmd : cmdlist){
							NUSALSeqCmdType ctype = cmd.getFunctionalType();
							if(ctype.flagSet(NUSALSeqCommands.FLAG_OPENTRACK)){
								//Make sure layer open. Ignore channel open.
								if(ctype == NUSALSeqCmdType.VOICE_OFFSET || ctype == NUSALSeqCmdType.VOICE_OFFSET_REL){
									int ly = cmd.getParam(0);
									NUSALSeqCommand targ = cmd.getBranchTarget();
									if(chsec.children[ly] == null) chsec.children[ly] = new MMLTsec();
									MMLBlock lblock = rdr_lbl_map.get(targ.getLabel());
									reader.parseMMLBlock(lblock, null);
									chsec.children[ly].maintrack.add(lblock);
								}
							}
							else if(ctype.flagSet(NUSALSeqCommands.FLAG_CALL)){
								//Sub.
								NUSALSeqCommand targ = cmd.getBranchTarget();
								String slbl = targ.getLabel();
								if(!used_lbls.contains(slbl)){
									MMLBlock sub = rdr_lbl_map.get(targ.getLabel());
									reader.parseMMLBlock(sub, null);
									chsec.subs.add(sub);
									used_lbls.add(slbl);
								}
							}
						}
						
						//Do layers.
						for(int j = 0; j < 8; j++){
							if(chsec.children[j] == null) continue;
							MMLTsec lsec = chsec.children[j];
							for(MMLBlock lblock : lsec.maintrack){
								cmdlist = lblock.chunk.getCommands();
								for(NUSALSeqCommand cmd : cmdlist){
									NUSALSeqCmdType ctype = cmd.getFunctionalType();
									if(ctype.flagSet(NUSALSeqCommands.FLAG_CALL)){
										//Sub.
										NUSALSeqCommand targ = cmd.getBranchTarget();
										String slbl = targ.getLabel();
										if(!used_lbls.contains(slbl)){
											MMLBlock sub = rdr_lbl_map.get(targ.getLabel());
											reader.parseMMLBlock(sub, null);
											lsec.subs.add(sub);
											used_lbls.add(slbl);
										}
									}
								}
							}
						}
					}
				}
			}
		}
		
		//Build master chunk & assign addresses
		//Seq blocks
		NUSALSeqCommandChunk master_chunk = new NUSALSeqCommandChunk();
		for(MMLTsec tsec : timeblocks){
			for(MMLBlock sblock : tsec.maintrack){
				master_chunk.addCommand(sblock.chunk);
			}
		}
		
		//Channels
		for(MMLTsec tsec : timeblocks){
			for(int i = 0; i < 16; i++){
				if(tsec.children[i] != null){
					for(MMLBlock cblock : tsec.children[i].maintrack){
						master_chunk.addCommand(cblock.chunk);
					}
				}
			}
		}
		
		//Channel subs
		for(MMLTsec tsec : timeblocks){
			for(int i = 0; i < 16; i++){
				if(tsec.children[i] != null){
					for(MMLBlock cblock : tsec.children[i].subs){
						master_chunk.addCommand(cblock.chunk);
					}
				}
			}
		}
		
		//Layers
		for(MMLTsec tsec : timeblocks){
			for(int i = 0; i < 16; i++){
				MMLTsec csec = tsec.children[i];
				if(csec != null){
					for(int j = 0; j < 8; j++){
						if(csec.children[j] != null){
							for(MMLBlock lblock : csec.children[j].maintrack){
								master_chunk.addCommand(lblock.chunk);
							}		
						}
					}
				}
			}
		}
		
		//Layer subs
		for(MMLTsec tsec : timeblocks){
			for(int i = 0; i < 16; i++){
				MMLTsec csec = tsec.children[i];
				if(csec != null){
					for(int j = 0; j < 8; j++){
						if(csec.children[j] != null){
							for(MMLBlock lblock : csec.children[j].subs){
								master_chunk.addCommand(lblock.chunk);
							}		
						}
					}
				}
			}
		}
		
		//Put together output
		master_chunk.setAddress(0);
		BasicCommandMap cmap = new BasicCommandMap(cmdBook);
		cmap.loadIntoMap(master_chunk);
		NUSALSeq seq = NUSALSeq.newNUSALSeq(cmap);
		
		return seq;
	}
	
}
