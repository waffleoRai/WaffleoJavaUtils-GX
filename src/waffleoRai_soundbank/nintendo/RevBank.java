package waffleoRai_soundbank.nintendo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;

import waffleoRai_Containers.nintendo.NDKRevolutionFile;
import waffleoRai_Containers.nintendo.NDKSectionType;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;

public class RevBank extends NinBank{

	/*--- Constants ---*/
	
	public static final String MAGIC = "RBNK";
	
	public static final int TYPE_FLAG_NULL = 0x00000000;
	public static final int TYPE_FLAG_TONE = 0x01010000;
	public static final int TYPE_FLAG_INST = 0x01020000;
	public static final int TYPE_FLAG_PERC = 0x01030000;
	public static final int TYPE_FLAG_LINK = 0x01040000;

	/*--- Construction/Parsing ---*/
	
	private RevBank(int initNodes)
	{
		topNodes = new ArrayList<NinBankNode>(initNodes+1);
		nodeMap = new HashMap<Long, NinBankNode>();
		linkNodes = new HashSet<NinBankNode>();
	}
	
	public static RevBank readRBNK(FileBuffer src, long stoff) throws UnsupportedFileTypeException
	{
		if(src == null) return null;
		NDKRevolutionFile rev_file = NDKRevolutionFile.readRevolutionFile(src);
		if(rev_file == null) return null;
		if(!MAGIC.equals(rev_file.getFileIdentifier())) throw new FileBuffer.UnsupportedFileTypeException("RevBank.readRBNK || Source data does not begin with RBNK magic number!");
		
		//DATA
		FileBuffer data_sec = rev_file.getSectionData(NDKSectionType.DATA);
		
		//Initialize
		long cpos = 8L;
		int entryCount = data_sec.intFromFile(cpos); cpos+=4;
		//System.err.println("Top Level Entry Count: " + entryCount);
		RevBank bank = new RevBank(entryCount);
		for(int i = 0; i < entryCount; i++)
		{
			//System.err.println("Entry " + i);
			int etype = data_sec.intFromFile(cpos); cpos+=4;
			etype = getType(etype);
			long ioff = Integer.toUnsignedLong(data_sec.intFromFile(cpos)); cpos+=4;
			long iaddr = ioff;
			//System.err.println("Inst Offset 0x" + Long.toHexString(ioff));
			if(ioff == 0)
			{
				//bank.instruments.add(RInst.createNewRInst()); //Empty
				bank.topNodes.add(NinBankNode.generateEmptyNode(null));
			}
			else
			{
				//ioff +=  data_off + 8L;	
				NinBankNode node = readRBNKNode(data_sec, etype, iaddr, 8L);
				bank.topNodes.add(node);
				if(!node.isLink()) bank.nodeMap.put(iaddr, node);
				else bank.linkNodes.add(node);
				
				//Map all inner nodes
				Collection<NinBankNode> inner = node.getAllNonNullDescendants();
				for(NinBankNode n : inner){
					if(!n.isLink()) bank.nodeMap.put(n.getLocalAddress(), n);
					else bank.linkNodes.add(n);
				}
			}
		}
		
		//Resolve any links
		bank.resolveLinks();
		
		return bank;
	}
	
	public static RevBank createEmptyRBNK(int initPresets)
	{
		return new RevBank(initPresets);
	}
	
	public static NinBankNode readRBNKNode(FileBuffer src, int flag, long rawAddress, long baseAddress) throws UnsupportedFileTypeException
	{
		return readRBNKNode(src, flag, rawAddress, baseAddress, null);
	}
	
	public static NinBankNode readRBNKNode(FileBuffer src, int flag, long rawAddress, long baseAddress, NinBankNode parentNode) throws UnsupportedFileTypeException
	{
		if(src == null) return null;
		//Determine start position
		long cpos = rawAddress + baseAddress;
		
		NinBankNode node = new NinBankNode(flag, rawAddress, parentNode);
		switch(flag)
		{
		case NinBankNode.TYPE_TONE: parseType1R(src, cpos, node); break;
		case NinBankNode.TYPE_INST: parseType2R(src, cpos, baseAddress, node); break;
		case NinBankNode.TYPE_PERC: parseType3R(src, cpos, baseAddress, node); break;
		case NinBankNode.TYPE_LINK: 
			node.setLinkAddress(rawAddress);
			break;
		}
		
		return node;
	}
	
	private static void parseType1R(FileBuffer src, long stpos, NinBankNode node) throws UnsupportedFileTypeException
	{
		NinTone t = NinTone.readRTone(src, stpos);
		node.setTone(t);
	}
	
	private static void parseType2R(FileBuffer src, long stpos, long baseAddr, NinBankNode node) throws UnsupportedFileTypeException
	{
		//System.err.println("Instrument is type 2");
		long cpos = stpos;
		int rcount = Byte.toUnsignedInt(src.getByte(cpos)); cpos++;

		byte[] maxs = new byte[rcount];
		for(int i = 0; i < rcount; i++)
		{
			maxs[i] = src.getByte(cpos); cpos++;
		}
		//System.err.println("cpos: 0x" + Long.toHexString(cpos));
		int mod = (rcount+1) % 4;
		if(mod != 0) cpos += 4 - mod; //Skip padding
		//System.err.println("Region Count: " + rcount);
		//System.err.println("cpos: 0x" + Long.toHexString(cpos));
	
		for(int i = 0; i < rcount; i++)
		{
			//System.err.println("Tone " + i);
			int rtype = src.intFromFile(cpos); cpos += 4;
			
			long toff = Integer.toUnsignedLong(src.intFromFile(cpos)); cpos += 4;
			//System.err.println("Tone Offset: 0x" + Long.toHexString(toff));
			
			NinBankNode cnode = readRBNKNode(src, rtype, toff, baseAddr, node);
			
			//Set range
			byte min = 0;
			if(i > 0) min = (byte) (maxs[i-1] + 1);
			byte max = maxs[i];
			cnode.setRange(min, max);

		}

	}
	
	private static void parseType3R(FileBuffer src, long stpos, long baseAddr, NinBankNode node) throws UnsupportedFileTypeException
	{
		//System.err.println("Instrument is type 3");
		long cpos = stpos;
		int min = Byte.toUnsignedInt(src.getByte(cpos)); cpos++;
		int max = Byte.toUnsignedInt(src.getByte(cpos)); cpos++;
		cpos += 2; //Unknown
		int rcount = max - min + 1;

		for(int i = 0; i < rcount; i++)
		{
			int rtype = src.intFromFile(cpos); cpos += 4;
			long toff = Integer.toUnsignedLong(src.intFromFile(cpos)); cpos += 4;
			NinBankNode cnode = readRBNKNode(src, rtype, toff, baseAddr, node);
			
			//Set range
			int note = min + i;
			cnode.setRange((byte)note, (byte)note);

		}
	}
	
	private static int getType(int flag)
	{
		switch(flag)
		{
		case TYPE_FLAG_NULL: return NinBankNode.TYPE_NULL;
		case TYPE_FLAG_TONE: return NinBankNode.TYPE_TONE;
		case TYPE_FLAG_INST: return NinBankNode.TYPE_INST;
		case TYPE_FLAG_PERC: return NinBankNode.TYPE_PERC;
		case TYPE_FLAG_LINK: return NinBankNode.TYPE_LINK;
		}
		return -1;
	}
	
}
