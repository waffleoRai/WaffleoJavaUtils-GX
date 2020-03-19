package waffleoRai_Containers.nintendo.sar;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import waffleoRai_Containers.nintendo.NDKRevolutionFile;
import waffleoRai_Containers.nintendo.NDKSectionType;
import waffleoRai_Containers.nintendo.sar.rev.PanCurve;
import waffleoRai_Containers.nintendo.sar.rev.PanMode;
import waffleoRai_Containers.nintendo.sar.rev.PlayerInfoNode;
import waffleoRai_Containers.nintendo.sar.rev.RevColNode;
import waffleoRai_Containers.nintendo.sar.rev.RevGroup;
import waffleoRai_Containers.nintendo.sar.rev.RevGroupMember;
import waffleoRai_Containers.nintendo.sar.rev.RevSoundNode;
import waffleoRai_Containers.nintendo.sar.rev.SeqNode;
import waffleoRai_Containers.nintendo.sar.rev.SoundDataNode;
import waffleoRai_Containers.nintendo.sar.rev.SoundType;
import waffleoRai_Containers.nintendo.sar.rev.SoundbankNode;
import waffleoRai_Containers.nintendo.sar.rev.StreamNode;
import waffleoRai_Containers.nintendo.sar.rev.WaveNode;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;

public class RevSoundArchive extends NinSoundArchive{
	
	/* ----- Constants ----- */
	
	public static final String MAGIC = "RSAR";
	
	public static final String SYMB_MAGIC = "SYMB";
	public static final String INFO_MAGIC = "INFO";
	public static final String FILE_MAGIC = "FILE";
	
	public static final String MAGIC_SEQ = "RSEQ";
	public static final String MAGIC_WSD = "RWSD";
	public static final String MAGIC_STM = "RSTM";
	public static final String MAGIC_BNK = "RBNK";
	public static final String MAGIC_WAR = "RWAR";
	public static final String MAGIC_WAV = "RWAV";
	
	/* ----- Static Functions ----- */
	
	public static String getFileExtension(String magic)
	{
		if(magic.equals(MAGIC_SEQ)) return "brseq";
		if(magic.equals(MAGIC_WSD)) return "brwsd";
		if(magic.equals(MAGIC_STM)) return "brstm";
		if(magic.equals(MAGIC_BNK)) return "brbnk";
		if(magic.equals(MAGIC_WAR)) return "brwar";
		if(magic.equals(MAGIC_WAV)) return "brwav";
		return "bin";
	}
	
	/* ----- Instance Variables ----- */
	
	private List<RevColNode> collections;
	private List<RevGroup> groups;
	
	/* ----- Construction/Parsing ----- */
	
	public RevSoundArchive(int initCols, int initGroups)
	{
		collections = new ArrayList<RevColNode>(initCols + 1);
		groups = new ArrayList<RevGroup>(initGroups + 1);
	}
	
	private RevSoundArchive(){}
	
	public static RevSoundArchive readRSAR(String path) throws UnsupportedFileTypeException, IOException
	{
		FileBuffer src = FileBuffer.createBuffer(path, true);
		
		if(src == null) return null;
		NDKRevolutionFile rev_file = NDKRevolutionFile.readRevolutionFile(src);
		if(rev_file == null) return null;
		if(!MAGIC.equals(rev_file.getFileIdentifier())) throw new FileBuffer.UnsupportedFileTypeException("RevSoundArchive.readRSAR || Source data does not begin with RSAR magic number!");
		
		//SYMB
		FileBuffer symb_sec = rev_file.getSectionData(NDKSectionType.SYMB);
		List<String> nameList = readSYMBNames(symb_sec);
		//Dunno what to do about the mask tables
		
		//INFO
		//Get table offsets...
		FileBuffer info_sec = rev_file.getSectionData(NDKSectionType.INFO);
		//long info_off = rev_file.getOffsetToSection(NDKSectionType.INFO);
		//Doesn't interpret type markers (dunno what variations there are)
		long cpos = 0x0C;
		long sdat_tbl_off = Integer.toUnsignedLong(info_sec.intFromFile(cpos)); cpos+=8;
		long sbnk_tbl_off = Integer.toUnsignedLong(info_sec.intFromFile(cpos)); cpos+=8;
		long pi_tbl_off = Integer.toUnsignedLong(info_sec.intFromFile(cpos)); cpos+=8;
		long col_tbl_off = Integer.toUnsignedLong(info_sec.intFromFile(cpos)); cpos+=8;
		long grp_tbl_off = Integer.toUnsignedLong(info_sec.intFromFile(cpos)); cpos+=8;
		//long sc_tbl_off = Integer.toUnsignedLong(info_sec.intFromFile(cpos)); cpos+=8;
		//I have a doc for the sound count table, but I don't know what the fields mean.
		
		//Read sdat and sbnk tables
		Map<Integer, List<RevSoundNode>> sdat_map = readSDatTable(info_sec, sdat_tbl_off, nameList);
		readBankTable(info_sec, sbnk_tbl_off, nameList, sdat_map);
		readPlayerTable(info_sec, pi_tbl_off, nameList, sdat_map);
		
		//Read collections
		List<RevColNode> rawColList = readColTable(path, info_sec, col_tbl_off);
		
		//Copy sound nodes into collections
		for(int i = 0; i < rawColList.size(); i++)
		{
			List<RevSoundNode> nodes = sdat_map.get(i);
			if(nodes != null)
			{
				RevColNode cnode = rawColList.get(i);
				for(RevSoundNode s : nodes) cnode.addSoundNode(s);
			}
		}
		
		//Split collections by type and load into separate lists
		RevSoundArchive arc = new RevSoundArchive();
		arc.collections = rawColList;
		
		//Read group table
		arc.readGroupTable(path, info_sec, grp_tbl_off, nameList);
		
		return arc;
	}
	
	private static List<String> readSYMBNames(FileBuffer symb_sec)
	{
		List<String> nameList;
		long nt_off = Integer.toUnsignedLong(symb_sec.intFromFile(0x08));
		nt_off += 8;
		
		long cpos = nt_off;
		int nentries = symb_sec.intFromFile(cpos); cpos += 4;
		nameList = new ArrayList<String>(nentries + 1);
		for(int i = 0; i < nentries; i++)
		{
			long noff = Integer.toUnsignedLong(symb_sec.intFromFile(cpos)); cpos+=4;
			noff += 8;
			
			String n = symb_sec.getASCII_string(noff, '\0');
			nameList.add(n);
		}
		return nameList;
	}

	private static List<RevColNode> readColTable(String filepath, FileBuffer info_sec, long col_tbl_off)
	{
		String pathstem = filepath;
		int lastslash = filepath.lastIndexOf(File.separator);
		if(lastslash >= 0) pathstem = filepath.substring(0, lastslash);
		
		long cpos = col_tbl_off + 8;
		int nentries = info_sec.intFromFile(cpos); cpos += 8;
		List<RevColNode> collist = new ArrayList<RevColNode>(nentries+1);
		for(int i = 0; i < nentries; i++)
		{
			long epos = Integer.toUnsignedLong(info_sec.intFromFile(cpos)); cpos += 8;
			epos += 8;
			
			long dsz = Integer.toUnsignedLong(info_sec.intFromFile(epos)); epos+=4;
			long asz = Integer.toUnsignedLong(info_sec.intFromFile(epos)); epos+=8;
			
			int marker = info_sec.intFromFile(epos); epos+=4;
			boolean external = (marker != 0);
			
			RevColNode node = new RevColNode("c" + String.format("%04d", i));
			node.setFileSize(dsz);
			node.setAudioSize(asz);
			node.setExternal(external);
			
			if(external)
			{
				long fnoff = Integer.toUnsignedLong(info_sec.intFromFile(epos)); epos+=4;
				fnoff+=4;
				String fname = info_sec.getASCII_string(fnoff, '\0');
				String fpath = pathstem + File.separator + fname;
				fpath.replace('\\', File.separatorChar);
				fpath.replace('/', File.separatorChar);
				
				node.setFileLocation(fpath, 0, dsz);
			}
			/*else
			{
				//For the internals, it looks like it's just the table of groups?
				 I think this should be covered when reading the groups?
			}*/
			
			collist.add(node);
		}
		
		
		return collist;
	}
	
	private void readGroupTable(String filepath, FileBuffer info_sec, long grp_tbl_off, List<String> names)
	{
		long cpos = grp_tbl_off + 8;
		int nentries = info_sec.intFromFile(cpos); cpos += 8;
		groups = new ArrayList<RevGroup>(nentries + 1);
		for(int i = 0; i < nentries; i++)
		{
			long eoff = Integer.toUnsignedLong(info_sec.intFromFile(cpos)); cpos += 8;
			eoff+=8;
			
			int fnind = info_sec.intFromFile(eoff); eoff += 4+4+8; //Skip unknown fields
			long doff = Integer.toUnsignedLong(info_sec.intFromFile(eoff)); eoff += 4;
			long dsz = Integer.toUnsignedLong(info_sec.intFromFile(eoff)); eoff += 4;
			long aoff = Integer.toUnsignedLong(info_sec.intFromFile(eoff)); eoff += 4;
			long asz = Integer.toUnsignedLong(info_sec.intFromFile(eoff)); eoff += 8;
			
			int toff = info_sec.intFromFile(eoff); eoff += 4;
			eoff = Integer.toUnsignedLong(toff) + 8;
			
			int ecount = info_sec.intFromFile(eoff); eoff += 8;
			
			RevGroup g = new RevGroup(ecount);
			g.setName(names.get(fnind));
			g.setDataLocation(filepath, doff, dsz, aoff, asz);
			
			long a_st = 0;
			for(int j = 0; j < ecount; j++)
			{
				long moff = Integer.toUnsignedLong(info_sec.intFromFile(eoff)); eoff+=8;
				moff+=8;
				
				int cind = info_sec.intFromFile(moff); moff+=4;
				int d_off = info_sec.intFromFile(moff); moff+=4;
				int d_sz = info_sec.intFromFile(moff); moff+=4;
				//int a_off = info_sec.intFromFile(moff); moff+=4;
				//int a_sz = info_sec.intFromFile(moff); moff+=4;
				
				RevColNode cnode = collections.get(cind);
				g.addNewMember(cnode);
				RevGroupMember mem = g.getMember(j);
				long data_off = doff + Integer.toUnsignedLong(d_off);
				mem.setFileLocation(filepath, data_off, Integer.toUnsignedLong(d_sz));
				long audSize = cnode.getAudioSize();
				if(audSize > 0)
				{
					mem.setAudioLocation(filepath, a_st + data_off, audSize);
					a_st += audSize;
				}
			}
			
			groups.add(g);
		}
	}
	
	private static Map<Integer, List<RevSoundNode>> readSDatTable(FileBuffer info_sec, long sdat_tbl_off, List<String> names)
	{
		Map<Integer, List<RevSoundNode>> sdat_map = new HashMap<Integer, List<RevSoundNode>>();
		
		long cpos = sdat_tbl_off + 8;
		int nentries = info_sec.intFromFile(cpos); cpos += 8;
		for(int i = 0; i < nentries; i++)
		{
			long epos = Integer.toUnsignedLong(info_sec.intFromFile(cpos)); cpos += 8;
			epos += 8;
			
			int nind = info_sec.intFromFile(epos); epos+=4;
			int cind = info_sec.intFromFile(epos); epos+=4;
			int pid = info_sec.intFromFile(epos); epos+=8;
			//int off3 = info_sec.intFromFile(epos); epos+=4;
			epos += 4; //We're bypassing the third section for now?
			byte vol = info_sec.getByte(epos); epos++;
			byte pri = info_sec.getByte(epos); epos++;
			byte type = info_sec.getByte(epos); epos++;
			byte rf = info_sec.getByte(epos); epos+=5;
			int off2 = info_sec.intFromFile(epos); epos+=4;
			int usr1 = info_sec.intFromFile(epos); epos+=4;
			int usr2 = info_sec.intFromFile(epos); epos+=4;
			byte pm = info_sec.getByte(epos); epos++;
			byte pc = info_sec.getByte(epos); epos++;
			byte apid = info_sec.getByte(epos); epos++;
			
			//Now we can instantiate
			SoundDataNode node = null;
			long sec2_off = Integer.toUnsignedLong(off2) + 8;
			long twopos = sec2_off;
			//Instantiate and load type-specific stuff
			switch(type)
			{
			case 0x1: 
				SeqNode snode = new SeqNode();
				snode.setSeqLableEntry(info_sec.intFromFile(twopos)); twopos+=4;
				snode.setSoundbankIndex(info_sec.intFromFile(twopos)); twopos+=7;
				snode.setAllocTrack(info_sec.getByte(twopos)); twopos++;
				snode.setChannelPriority(info_sec.getByte(twopos)); twopos++;
				snode.setSoundType(SoundType.SEQ);
				node = snode;
				break;
			case 0x2: 
				StreamNode tnode = new StreamNode();
				tnode.setStartPosition(info_sec.intFromFile(twopos)); twopos+=5;
				tnode.setAllocChannelCount(info_sec.getByte(twopos)); twopos++;
				tnode.setAllocTrack(info_sec.getByte(twopos));
				tnode.setSoundType(SoundType.STRM);
				node = tnode;
				break;
			case 0x3: 
				WaveNode wnode = new WaveNode();
				wnode.setSDN(info_sec.intFromFile(twopos)); twopos+=7;
				wnode.setAllocTrack(info_sec.getByte(twopos)); twopos++;
				wnode.setChannelPriority(info_sec.getByte(twopos));
				wnode.setSoundType(SoundType.WAVE);
				node = wnode;
				break;
			default: break;
			}
			if(node == null) continue;
			
			//Load common fields
			String node_name = names.get(nind);
			node.setName(node_name);
			node.setPlayerID(pid);
			node.setVolume(vol);
			node.setPlayerPriority(pri);
			node.setRemoteFilter(Byte.toUnsignedInt(rf));
			node.setUserParameter1(usr1);
			node.setUserParameter2(usr2);
			node.setActorPlayerID(Byte.toUnsignedInt(apid));
			
			//long threepos = Integer.toUnsignedLong(off3) + 8;
			PanMode panmode = PanMode.getPanMode(pm);
			PanCurve pancurve = PanCurve.getPanCurve(pc);
			node.setPanCurve(pancurve);
			node.setPanMode(panmode);
			
			//Map to collection index
			List<RevSoundNode> list = sdat_map.get(cind);
			if(list == null)
			{
				list = new LinkedList<RevSoundNode>();
				sdat_map.put(cind, list);
			}
			list.add(node);
		}
		
		return sdat_map;
	}
	
	private static void readBankTable(FileBuffer info_sec, long sbnk_tbl_off, List<String> names, Map<Integer, List<RevSoundNode>> map)
	{
		long cpos = sbnk_tbl_off + 8;
		int nentries = info_sec.intFromFile(cpos); cpos += 8;
		for(int i = 0; i < nentries; i++)
		{
			long epos = Integer.toUnsignedLong(info_sec.intFromFile(cpos)); cpos += 8;
			epos += 8;
			
			int nind = info_sec.intFromFile(epos); epos+=4;
			int cind = info_sec.intFromFile(epos); epos+=4;
			int bind = info_sec.intFromFile(epos); epos+=4;
			
			SoundbankNode node = new SoundbankNode();
			node.setName(names.get(nind));
			node.setBankIndex(bind);
			
			List<RevSoundNode> list = map.get(cind);
			if(list == null)
			{
				list = new LinkedList<RevSoundNode>();
				map.put(cind, list);
			}
			list.add(node);
		}
	}
	
	private static void readPlayerTable(FileBuffer info_sec, long pi_tbl_off, List<String> names, Map<Integer, List<RevSoundNode>> map)
	{
		long cpos = pi_tbl_off + 8;
		int nentries = info_sec.intFromFile(cpos); cpos += 8;
		for(int i = 0; i < nentries; i++)
		{
			long epos = Integer.toUnsignedLong(info_sec.intFromFile(cpos)); cpos += 8;
			epos += 8;
			
			int nind = info_sec.intFromFile(epos); epos+=4;
			int sc = info_sec.intFromFile(epos); epos+=4;
			
			PlayerInfoNode node = new PlayerInfoNode();
			node.setName(names.get(nind));
			node.setPlayableSoundCount(sc);
			
			List<RevSoundNode> list = map.get(-1);
			if(list == null)
			{
				list = new LinkedList<RevSoundNode>();
				map.put(-1, list);
			}
			list.add(node);
		}
	}

	/* ----- Getters ----- */
	
	/* ----- Setters ----- */
	
}
