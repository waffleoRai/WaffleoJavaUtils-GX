package waffleoRai_soundbank.nintendo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import waffleoRai_Containers.nintendo.NDKDSFile;
import waffleoRai_Containers.nintendo.NDKSectionType;
import waffleoRai_Containers.nintendo.sar.DSSoundArchive;
import waffleoRai_Files.Converter;
import waffleoRai_Sound.nintendo.DSWarc;
import waffleoRai_SoundSynth.SynthBank;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileNode;
import waffleoRai_soundbank.SimpleBank;
import waffleoRai_soundbank.SoundbankDef;
import waffleoRai_soundbank.sf2.SF2;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;

public class DSBank extends NinBank{
	
	/*--- Constants ---*/
	
	public static final int TYPE_ID = 0x4e424e4b;
	public static final String MAGIC = "SBNK";

	/*--- Construction/Parsing ---*/
	
	private DSBank(int initNodes)
	{
		topNodes = new ArrayList<NinBankNode>(initNodes+1);
		nodeMap = new HashMap<Long, NinBankNode>();
		linkNodes = new HashSet<NinBankNode>();
	}
	
	public static DSBank readSBNK(FileBuffer src, long stoff) throws UnsupportedFileTypeException
	{
		if(src == null) return null;
		NDKDSFile ds_file = NDKDSFile.readDSFile(src);
		if(ds_file == null) return null;
		if(!MAGIC.equals(ds_file.getFileIdentifier())) throw new FileBuffer.UnsupportedFileTypeException("DSBank.readSBNK || Source data does not begin with SBNK magic number!");
		
		//DATA
		long data_off = ds_file.getOffsetToSection(NDKSectionType.DATA);
		FileBuffer data_sec = ds_file.getSectionData(NDKSectionType.DATA);
		long cpos = 40L; //4 magic, 4 size, 32 reserved?
		int ncount = data_sec.intFromFile(cpos); cpos+=4;
		
		//Create bank
		DSBank bank = new DSBank(ncount);
		
		//Instrument pointer table
		//& Instrument table
		for(int i = 0; i < ncount; i++)
		{
			byte frecord = data_sec.getByte(cpos); cpos++;
			int type = getType(frecord);
			int offset = Short.toUnsignedInt(data_sec.shortFromFile(cpos)); cpos += 2;
			cpos++; //Padding
			
			if(type == 0)
			{
				bank.topNodes.add(NinBankNode.generateEmptyNode(null));
			}
			else
			{
				long rpos = Integer.toUnsignedLong(offset) - data_off;
				NinBankNode node = readSBNKNode(data_sec, type, data_off, rpos);
				bank.topNodes.add(node);	
				
				if(!node.isLink()) bank.nodeMap.put(node.getLocalAddress(), node);
				else bank.linkNodes.add(node);
				
				Collection<NinBankNode> inner = node.getAllNonNullDescendants();
				for(NinBankNode n : inner){
					if(!n.isLink()) bank.nodeMap.put(n.getLocalAddress(), n);
					else bank.linkNodes.add(n);
				}
			}
		}
		
		bank.resolveLinks();
		return bank;
	}
	
	public static NinBankNode readSBNKNode(FileBuffer data_sec, int flag, long data_off, long offset) throws UnsupportedFileTypeException
	{
		return readSBNKNode(data_sec, flag, data_off, offset, null);
	}
	
	public static NinBankNode readSBNKNode(FileBuffer data_sec, int flag, long data_off, long offset, NinBankNode parentNode) throws UnsupportedFileTypeException
	{
		if(data_sec == null) return null;
		//Determine start position
		
		long rawAddress = data_off + offset;
		NinBankNode node = new NinBankNode(flag, rawAddress, parentNode);
		switch(flag)
		{
		case NinBankNode.TYPE_TONE: parseType1S(data_sec, offset, node); break;
		case NinBankNode.TYPE_INST: parseType2S(data_sec, offset, data_off, node); break;
		case NinBankNode.TYPE_PERC: parseType3S(data_sec, offset, data_off, node); break;
		case NinBankNode.TYPE_LINK: 
			node.setLinkAddress(rawAddress);
			break;
		}
		
		return node;
	}
	
	private static void parseType1S(FileBuffer src, long stpos, NinBankNode node) throws UnsupportedFileTypeException
	{
		NinTone t = NinTone.readSTone(src, stpos);
		node.setTone(t);
	}
	
	private static void parseType2S(FileBuffer src, long stpos, long data_off, NinBankNode node) throws UnsupportedFileTypeException
	{
		//System.err.println("Instrument is type 2");
		long cpos = stpos;
		
		//Get range maxs. Always 8 slots in SBNK, though not all need be used.
		byte[] maxs = new byte[8];
		for(int i = 0; i < 8; i++)
		{
			maxs[i] = src.getByte(cpos); cpos++;
		}
	
		for(int i = 0; i < 8; i++)
		{
			if((i != 0) && (maxs[i] == 0)) break;
			//Skip type only because I don't know what to do with it...
			cpos += 2;
			
			NinBankNode cnode = new NinBankNode(NinBankNode.TYPE_TONE, data_off + stpos, node);
			parseType1S(src, cpos, cnode);
			cpos += 10;
			
			//Set range
			byte min = 0;
			if(i > 0) min = (byte) (maxs[i-1] + 1);
			byte max = maxs[i];
			cnode.setRange(min, max);
		}

	}
	
	private static void parseType3S(FileBuffer src, long stpos, long data_off, NinBankNode node) throws UnsupportedFileTypeException
	{
		//System.err.println("Instrument is type 3");
		long cpos = stpos;
		int min = Byte.toUnsignedInt(src.getByte(cpos)); cpos++;
		int max = Byte.toUnsignedInt(src.getByte(cpos)); cpos++;
		cpos += 2; //Unknown
		int rcount = max - min + 1;

		for(int i = 0; i < rcount; i++)
		{
			//Skip type only because I don't know what to do with it...
			cpos += 2;
			
			NinBankNode cnode = new NinBankNode(NinBankNode.TYPE_TONE, data_off + stpos, node);
			parseType1S(src, cpos, cnode);
			cpos += 10;
			
			//Set range
			int note = min + i;
			cnode.setRange((byte)note, (byte)note);

		}
	}
	
	public static DSBank createEmptySBNK(int initPresets)
	{
		return new DSBank(initPresets);
	}
	
	private static int getType(byte flag)
	{
		if(flag == 0) return NinBankNode.TYPE_NULL;
		if(flag == 5) return NinBankNode.TYPE_LINK;
		if(flag < 16) return NinBankNode.TYPE_TONE;
		if(flag == 16) return NinBankNode.TYPE_PERC;
		if(flag == 17) return NinBankNode.TYPE_INST;
		
		return -1;
	}
	
	/*--- Definition ---*/
	
	private static NitroSoundbankDef static_def;
	
	public static NitroSoundbankDef getDefinition(){
		if(static_def == null) static_def = new NitroSoundbankDef();
		return static_def;
	}
	
	public static class NitroSoundbankDef extends SoundbankDef{

		private static final String DEFO_ENG_STR = "Nitro SoundBank";
		private static final String[] EXT_LIST = {"sbnk", "SBNK", "nbnk", "bnbnk"};
		
		private String str;
		
		public NitroSoundbankDef(){
			str = DEFO_ENG_STR;
		}
		
		public Collection<String> getExtensions() {
			List<String> list = new ArrayList<String>(EXT_LIST.length);
			for(String s : EXT_LIST)list.add(s);
			return list;
		}

		public String getDescription() {return str;}
		public int getTypeID() {return TYPE_ID;}
		public void setDescriptionString(String s) {str = s;}
		public String getDefaultExtension() {return "sbnk";}

		public SynthBank getPlayableBank(FileNode file) {
			//Needs to also load wavearcs...
			try {
				FileBuffer dat = file.loadDecompressedData();
				DSBank bank = DSBank.readSBNK(dat, 0);
				//Try to get warcs
				DSWarc[] warcs = DSSoundArchive.loadLinkedWavearcs(file);
				return bank.generatePlayableBank(warcs, 0);
			} 
			catch (IOException e) {
				e.printStackTrace();
				return null;
			} 
			catch (UnsupportedFileTypeException e) {
				e.printStackTrace();
				return null;
			}
		}
		
	}
	
	/*--- Converter ---*/
	
	private static SBNKConverter cdef;
	
	public static SBNKConverter getDefaultConverter(){
		if(cdef == null) cdef = new SBNKConverter();
		return cdef;
	}
	
	public static class SBNKConverter implements Converter{

		public static final String DEFO_ENG_FROM = "Nitro SoundBank (.sbnk)";
		public static final String DEFO_ENG_TO = "SoundFont 2 (.sf2)";
		
		private String from_desc;
		private String to_desc;
		
		public SBNKConverter(){
			from_desc = DEFO_ENG_FROM;
			to_desc = DEFO_ENG_TO;
		}
		
		public String getFromFormatDescription() {return from_desc;}
		public String getToFormatDescription() {return to_desc;}
		public void setFromFormatDescription(String s) {from_desc = s;}
		public void setToFormatDescription(String s) {to_desc = s;}

		public void writeAsTargetFormat(String inpath, String outpath)
				throws IOException, UnsupportedFileTypeException {
			writeAsTargetFormat(FileBuffer.createBuffer(inpath), outpath);
		}

		public void writeAsTargetFormat(FileBuffer input, String outpath)
				throws IOException, UnsupportedFileTypeException {
			throw new UnsupportedFileTypeException("SBNK to SF2 conversion requires SWAR data!");
		}
		
		public void writeAsTargetFormat(FileNode node, String outpath) 
				throws IOException, UnsupportedFileTypeException{
			
			FileBuffer input = node.loadDecompressedData();
			DSBank bnk = DSBank.readSBNK(input, 0);
			
			//Find WARCs
			DSWarc[] warcs = DSSoundArchive.loadLinkedWavearcs(node);
			
			//See if there's a name
			String name = node.getMetadataValue("TITLE");
			if(name == null) name = node.getFileName();
			
			SimpleBank bank = bnk.toSoundbank(warcs, 0, name);
			SF2.writeSF2(bank, "waffleoRai SBNK Converter", false, outpath);
		}

		public String changeExtension(String path) {
			if(path == null) return null;
			if(path.isEmpty()) return path;
			
			int lastdot = path.lastIndexOf('.');
			if(lastdot < 0) return path + ".sf2";
			return path.substring(0, lastdot) + ".sf2";
		}
		
	}
	

}
