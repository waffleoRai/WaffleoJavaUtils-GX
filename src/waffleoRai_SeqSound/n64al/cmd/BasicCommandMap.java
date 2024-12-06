package waffleoRai_SeqSound.n64al.cmd;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import waffleoRai_SeqSound.n64al.NUSALSeqCommand;
import waffleoRai_SeqSound.n64al.NUSALSeqCommandBook;
import waffleoRai_SeqSound.n64al.NUSALSeqCommandSource;
import waffleoRai_Utils.FileBuffer;

public class BasicCommandMap implements NUSALSeqCommandSource{

	private ConcurrentMap<Integer, NUSALSeqCommand> map;
	private NUSALSeqCommandBook cmdBook;
	
 	public BasicCommandMap(NUSALSeqCommandBook book){
		map = new ConcurrentHashMap<Integer, NUSALSeqCommand>();
		cmdBook = book;
		if(book == null) book = SysCommandBook.ZELDA64.getBook();
	}
	
	public void addCommand(int addr, NUSALSeqCommand cmd){
		map.put(addr, cmd);
	}
	
	public void clear(){map.clear();}
	
	public NUSALSeqCommand getCommandAt(int address) {
		return map.get(address);
	}
	
	public NUSALSeqCommand getCommandOver(int address){
		int checkAddr = address;
		NUSALSeqCommand cmd = null;
		while(checkAddr >= 0){
			cmd = map.get(checkAddr);
			if(cmd != null) return cmd;
			checkAddr--;
		}
		return null;
	}
	
	public List<Integer> getAllAddresses(){
		List<Integer> list = new ArrayList<Integer>(map.size()+1);
		list.addAll(map.keySet());
		Collections.sort(list);
		return list;
	}
	
	public List<NUSALSeqCommand> getOrderedCommands(){
		List<NUSALSeqCommand> list = new ArrayList<NUSALSeqCommand>(map.size()+1);
		List<Integer> alist = getAllAddresses();
		for(Integer k : alist){
			list.add(map.get(k));
		}
		return list;
	}
	
	public Map<Integer, NUSALSeqCommand> getSeqLevelCommands(){
		Map<Integer, NUSALSeqCommand> smap = new HashMap<Integer, NUSALSeqCommand>();
		List<Integer> addrs = getAllAddresses();
		for(Integer k : addrs){
			NUSALSeqCommand val = map.get(k);
			if(val == null) continue;
			if(val.seqUsed()) smap.put(k, val);
		}
		return smap;
	}
	
	public boolean reparseRegion(int pos, int len){return false;}

	public int getMinimumSizeInBytes(){
		if(map == null || map.isEmpty()) return 0;
		int maxaddr = Collections.max(map.keySet());
		NUSALSeqCommand lastcmd = map.get(maxaddr);
		return maxaddr + lastcmd.getSizeInBytes();
	}
	
	public void loadIntoMap(NUSALSeqCommand cmd){
		cmd.mapByAddress(map);
		cmd.dechunkReference();
	}
	
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
	
	private STSResult reparseCommand(int addr, int val, NUSALSeqCommand cmd, boolean p){
		int caddr = cmd.getAddress();
		map.remove(caddr);
		NUSALSeqCommand ncmd = null;
		
		FileBuffer data = FileBuffer.wrap(cmd.serializeMe());
		if(p) data.replaceShort((short)val, addr - caddr);
		else data.replaceByte((byte)val, addr - caddr);
		
		if(cmd.seqUsed()){
			ncmd = SeqCommands.parseSequenceCommand(data.getReferenceAt(0), cmdBook);
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
				ncmd = ChannelCommands.parseChannelCommand(data.getReferenceAt(0), cmdBook);
				if(ncmd == null) return STSResult.FAIL;
			}
			else{
				//layer
				ncmd = LayerCommands.parseLayerCommand(data.getReferenceAt(0), cmdBook, false);
				if(ncmd == null) return STSResult.FAIL;
			}
		}
		ncmd.setAddress(caddr);
		ncmd.setTickAddress(cmd.getTickAddress());
		ncmd.setSubsequentCommand(cmd.getSubsequentCommand());
		map.put(caddr, ncmd);
		
		if((ncmd instanceof NUSALSeqReferenceCommand) || (cmd instanceof NUSALSeqPtrTableData)){
			return relinkCommand(addr, ncmd);
		}
		
		return STSResult.OKAY;
	}
	
	public STSResult storeToSelf(int addr, byte value){
		NUSALSeqCommand cmd = getCommandOver(addr);
		if(cmd == null) return STSResult.FAIL;
		int coff = addr - cmd.getAddress();
		STSResult res = cmd.storeToSelf(coff, value);
		switch(res){
		case FAIL:
		case INVALID:
		case OUTSIDE:
		case OKAY:
			return res;
		case RELINK:
			return relinkCommand(addr, cmd);
		case REPARSE:
			return reparseCommand(addr, value, cmd, false);
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
			case OKAY:
				return res;
			case RELINK:
				return relinkCommand(addr, cmd);
			case REPARSE:
				return reparseCommand(addr, value, cmd, true);
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
	
	public void printMeTo(Writer out) throws IOException{
		if(map.isEmpty()){
			out.write("<Command map is empty>\n");
			return;
		}
		List<Integer> keys = new ArrayList<Integer>(map.size()+1);
		keys.addAll(map.keySet());
		Collections.sort(keys);
		
		for(Integer k : keys){
			out.write(String.format("%04x\t", k));
			NUSALSeqCommand cmd = map.get(k);
			out.write(cmd.toString() + "\t");
			int bcount = cmd.getSizeInBytes();
			byte[] ser = cmd.serializeMe();
			for(int i = 0; i < bcount; i++){
				out.write(String.format("%02x ", ser[i]));
			}
			out.write("\t");
			
			if(cmd.seqUsed()) out.write("seq ");
			for(int i = 0; i < 16; i++){
				if(cmd.channelUsed(i)) out.write("ch-" + Integer.toHexString(i) + " ");
				for(int j = 0; j < 4; j++){
					if(cmd.layerUsed(i, j)){
						out.write(Integer.toHexString(i) + "-" + j + " ");
					}
				}
			}
			out.write("\n");
		}
		
	}
	
}
