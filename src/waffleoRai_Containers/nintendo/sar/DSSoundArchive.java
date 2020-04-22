package waffleoRai_Containers.nintendo.sar;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import waffleoRai_Containers.SoundArchiveDef;
import waffleoRai_Containers.nintendo.NDKDSFile;
import waffleoRai_Files.Converter;
import waffleoRai_SeqSound.ninseq.DSSeq;
import waffleoRai_Sound.nintendo.DSStream;
import waffleoRai_Sound.nintendo.DSWarc;
import waffleoRai_Utils.DirectoryNode;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileNode;
import waffleoRai_soundbank.nintendo.DSBank;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;

public class DSSoundArchive extends NDKDSFile{
	
	/* ----- Constants ----- */
	
	public static final int TYPE_ID = 0x73646174;
	public static final String DEFO_ENG_STR = "Nitro (DS) Sound Archive";
	
	public static final String MAGIC = "SDAT";
	
	public static final String SYMB_MAGIC = "SYMB";
	public static final String INFO_MAGIC = "INFO";
	public static final String FAT_MAGIC = "FAT ";
	public static final String FILE_MAGIC = "FILE";
	
	public static final String MAGIC_SEQ = "SSEQ";
	public static final String MAGIC_SAR = "SSAR";
	public static final String MAGIC_STM = "STRM";
	public static final String MAGIC_BNK = "SBNK";
	public static final String MAGIC_WAR = "SWAR";
	
	public static final int TYPE_SEQ = 0x0700;
	public static final int TYPE_SEQARC = 0x0803;
	public static final int TYPE_BANK = 0x0601;
	public static final int TYPE_WAVEARC = 0x0402;
	
	public static final String FNMETAKEY_INDEX = "SDATIDX";
	public static final String FNMETAKEY_BANKID = "BANKID";
	public static final String FNMETAKEY_VOLUME = "VOL";
	public static final String FNMETAKEY_SSARNAMES = "MEMNAMES";
	public static final String FNMETAKEY_WARC_STEM = "SWAR";
	public static final String FNMETAKEY_SDATTYPE = "SDATTYPE";
	
	public static final String FNMETAKEY_BANKLINK = "BANKPATH";
	public static final String FNMETAKEY_WARCLINK_STEM = "WARCPATH";
	
	/*----- Instance Variables -----*/
	
	private String src_path;
	private long src_off;
	
	private ArrayList<SEQEntry> seq;
	private ArrayList<SEQArcEntry> seqarc;
	private ArrayList<BANKEntry> bank;
	private ArrayList<FileEntry> wavearc;
	private ArrayList<FileEntry> player;
	private ArrayList<FileEntry> player2;
	private ArrayList<STRMEntry> strm;
	
	private ArrayList<Group> groups;
	
	/*----- Internal Structs -----*/
	
	public static class FileEntry
	{
		public int fileID;
		public String symbol;
		public long offset;
		public long size;
		
		public String ext;
		public int myidx;
		
		public FileEntry(){fileID = -1;}
		
		public void addMetadata(FileNode fn)
		{
			fn.setMetadataValue(FNMETAKEY_INDEX, Integer.toString(myidx));
			if(ext.contains("war")) fn.setMetadataValue(FNMETAKEY_SDATTYPE, MAGIC_WAR);
		}
		
	}
	
	public static class SEQEntry extends FileEntry
	{
		public int bankID;
		public int vol;
		public int cpr;
		public int ppr;
		public int ply;
		
		public void addMetadata(FileNode fn)
		{
			super.addMetadata(fn);
			fn.setMetadataValue(FNMETAKEY_BANKID, Integer.toString(bankID));
			fn.setMetadataValue(FNMETAKEY_VOLUME, Integer.toString(vol));
			fn.setMetadataValue("CPR", Integer.toString(cpr));
			fn.setMetadataValue("PPR", Integer.toString(ppr));
			fn.setMetadataValue("PLY", Integer.toString(ply));
			fn.setMetadataValue(FNMETAKEY_SDATTYPE, MAGIC_SEQ);
		}
	}
	
	public static class SEQArcEntry extends FileEntry
	{
		public String[] inner_names;
		
		public SEQArcEntry(int count)
		{
			if(count <= 0)return;
			inner_names = new String[count];
		}
		
		public void addMetadata(FileNode fn)
		{
			super.addMetadata(fn);
			if(inner_names == null) return;
			StringBuilder sb = new StringBuilder(1024);
			for(int i = 0; i < inner_names.length; i++)
			{
				if(i != 0) sb.append(',');
				sb.append(inner_names[i]);
			}
			fn.setMetadataValue(FNMETAKEY_SSARNAMES, sb.toString());
			fn.setMetadataValue(FNMETAKEY_SDATTYPE, MAGIC_SAR);
		}
	}
	
	public static class BANKEntry extends FileEntry
	{
		public int[] wavearcs;
		
		public BANKEntry(){wavearcs = new int[4];}
		
		public void addMetadata(FileNode fn)
		{
			super.addMetadata(fn);
			if(wavearcs[0] != 0xFFFF) fn.setMetadataValue(FNMETAKEY_WARC_STEM + "0", Integer.toString(wavearcs[0]));
			if(wavearcs[1] != 0xFFFF) fn.setMetadataValue(FNMETAKEY_WARC_STEM + "1", Integer.toString(wavearcs[1]));
			if(wavearcs[2] != 0xFFFF) fn.setMetadataValue(FNMETAKEY_WARC_STEM + "2", Integer.toString(wavearcs[2]));
			if(wavearcs[3] != 0xFFFF) fn.setMetadataValue(FNMETAKEY_WARC_STEM + "3", Integer.toString(wavearcs[3]));
			fn.setMetadataValue(FNMETAKEY_SDATTYPE, MAGIC_BNK);
		}
	}
	
	public static class STRMEntry extends FileEntry
	{
		public int vol;
		public int pri;
		public int ply;
		
		public void addMetadata(FileNode fn)
		{
			super.addMetadata(fn);
			fn.setMetadataValue(FNMETAKEY_VOLUME, Integer.toString(vol));
			fn.setMetadataValue("PRI", Integer.toString(pri));
			fn.setMetadataValue("PLY", Integer.toString(ply));
			fn.setMetadataValue(FNMETAKEY_SDATTYPE, MAGIC_STM);
		}
	}
	
	public static class Group
	{
		public String symbol;
		public List<FileEntry> members;
		
		public Group(int initsize)
		{
			members = new ArrayList<FileEntry>(initsize+1);
		}
	}
	
	/*----- Construction -----*/
	
	protected DSSoundArchive(FileBuffer src) 
	{
		super(src, true);
	}
	
	/*----- Parsing -----*/
	
	public static DSSoundArchive readSDAT(String path) throws IOException
	{
		FileBuffer file = FileBuffer.createBuffer(path);
		return readSDAT(file);
	}
	
	public static DSSoundArchive readSDAT(FileBuffer file) throws IOException
	{
		DSSoundArchive arc = new DSSoundArchive(file);
		
		//Read SYMB
		Map<String, String[]> namemap = new HashMap<String, String[]>();
		List<String[]> seqarc_names  = null;
		FileBuffer symb = arc.getSectionData("SYMB");
		if(symb != null)
		{
			symb.setCurrentPosition(8); //Get offsets to records
			long[] recoff = new long[8];
			//long recoff = Integer.toUnsignedLong(symb.nextInt());
			for(int i = 0; i < 8; i++) recoff[i] = Integer.toUnsignedLong(symb.nextInt());
			symb.setCurrentPosition(recoff[0]);
			
			//--SEQ
			int count = symb.nextInt();
			//System.err.println("SEQ symbols: " + count);
			if(count > 0)
			{
				String[] arr = new String[count];
				for(int i = 0; i < count; i++)
				{
					int iptr = symb.nextInt();
					if(iptr == 0) continue;
					arr[i] = symb.getASCII_string(iptr, '\0');
				}
				namemap.put("SEQ", arr);
			}
			
			//--SEQARC
			symb.setCurrentPosition(recoff[1]);
			count = symb.nextInt();
			seqarc_names = new ArrayList<String[]>(count+1);
			//System.err.println("SEQARC symbols: " + count);
			if(count > 0)
			{
				String[] arr = new String[count];
				for(int i = 0; i < count; i++)
				{
					int iptr = symb.nextInt();
					if(iptr == 0) continue;
					arr[i] = symb.getASCII_string(iptr, '\0');
					
					//Now do sub records
					int sptr = symb.nextInt();
					int scount = symb.intFromFile(sptr); sptr+=4;
					String[] subnames = new String[scount];
					for(int j = 0; j < scount; j++)
					{
						iptr = symb.intFromFile(sptr); sptr+=4;
						if(iptr == 0) continue;
						subnames[j] = symb.getASCII_string(iptr, '\0');
					}
					seqarc_names.set(i, subnames);
				}
				namemap.put("SEQARC", arr);	
			}
			
			//--Others
			String[] other_recs = {"BANK", "WAVEARC", "PLAYER", "GROUP", "PLAYER2", "STRM"};
			for(int k = 0; k < 6; k++)
			{
				symb.setCurrentPosition(recoff[k+2]);
				count = symb.nextInt();
				//System.err.println(other_recs[k] + " symbols: " + count);
				if(count > 0)
				{
					String[] arr = new String[count];
					for(int i = 0; i < count; i++)
					{
						int iptr = symb.nextInt();
						if(iptr == 0) continue;
						arr[i] = symb.getASCII_string(iptr, '\0');
					}
					namemap.put(other_recs[k], arr);
				}
			}
		} //If SYMB != null
		
		//Read FAT
		FileBuffer fat = arc.getSectionData("FAT ");
		fat.setCurrentPosition(8); //Start at record count
		int fat_records = fat.nextInt();
		long[][] file_alloc_table = new long[fat_records][2];
		for(int i = 0; i < fat_records; i++)
		{
			file_alloc_table[i][0] = Integer.toUnsignedLong(fat.nextInt()); //Offset
			file_alloc_table[i][1] = Integer.toUnsignedLong(fat.nextInt()); //Size
			fat.skipBytes(8);
		}
		
		//Read INFO
		FileBuffer info = arc.getSectionData("INFO");
		info.setCurrentPosition(8); //Get offset to records
		long[] recoff = new long[8];
		for(int i = 0; i < 8; i++) recoff[i] = Integer.toUnsignedLong(info.nextInt());
		info.setCurrentPosition(recoff[0]);
		
		//--SEQ
		info.setCurrentPosition(recoff[0]);
		int count = info.nextInt();
		if(count > 0)
		{
			//System.err.println("SEQ INFO entries: " + count);
			arc.seq = new ArrayList<SEQEntry>(count);
			String[] names = namemap.get("SEQ");
			for(int i = 0; i < count; i++)
			{
				int iptr = info.nextInt();
				SEQEntry e = new SEQEntry();
				arc.seq.add(e);
				e.ext = "sseq";
				e.myidx = i;
				if(names != null) e.symbol = names[i];
				if(e.symbol == null) e.symbol = "SDAT_SSEQ_" + String.format("%04d", i);
				if(iptr == 0) continue;
				
				long pos = Integer.toUnsignedLong(iptr);
				e.fileID = Short.toUnsignedInt(info.shortFromFile(pos)); pos+=4;
				e.bankID = Short.toUnsignedInt(info.shortFromFile(pos)); pos+=2;
				e.vol = Byte.toUnsignedInt(info.getByte(pos)); pos++;
				e.cpr = Byte.toUnsignedInt(info.getByte(pos)); pos++;
				e.ppr = Byte.toUnsignedInt(info.getByte(pos)); pos++;
				e.ply = Byte.toUnsignedInt(info.getByte(pos)); pos++;
				
				e.offset = file_alloc_table[e.fileID][0];
				e.size = file_alloc_table[e.fileID][1];
				//System.err.println("SEQ Read: " + e.symbol);
			}
		}
		else arc.seq = new ArrayList<SEQEntry>();
		
		//--SEQARC
		info.setCurrentPosition(recoff[1]);
		count = info.nextInt();
		if(count > 0)
		{
			//System.err.println("SEQARC INFO entries: " + count);
			arc.seqarc = new ArrayList<SEQArcEntry>(count);
			String[] names = namemap.get("SEQARC");
			for(int i = 0; i < count; i++)
			{
				String[] inames = null;
				int nlen = 0;
				if(seqarc_names != null) {inames = seqarc_names.get(i); nlen = inames.length;}
				
				int iptr = info.nextInt();
				SEQArcEntry e = new SEQArcEntry(nlen);
				arc.seqarc.add(e);
				e.ext = "ssar";
				e.myidx = i;
				if(names != null) e.symbol = names[i];
				if(e.symbol == null) e.symbol = "SDAT_SSAR_" + String.format("%04d", i);
				e.inner_names = inames;
				if(iptr == 0) continue;
				
				long pos = Integer.toUnsignedLong(iptr);
				e.fileID = Short.toUnsignedInt(info.shortFromFile(pos));
				
				e.offset = file_alloc_table[e.fileID][0];
				e.size = file_alloc_table[e.fileID][1];
			}
		}
		else arc.seqarc = new ArrayList<SEQArcEntry>(16);
		
		//--BANK
		info.setCurrentPosition(recoff[2]);
		count = info.nextInt();
		if(count > 0)
		{
			//System.err.println("BANK INFO entries: " + count);
			arc.bank = new ArrayList<BANKEntry>(count);
			String[] names = namemap.get("BANK");
			for(int i = 0; i < count; i++)
			{
				int iptr = info.nextInt();
				BANKEntry e = new BANKEntry();
				arc.bank.add(e);
				e.ext = "sbnk";
				e.myidx = i;
				if(names != null) e.symbol = names[i];
				if(e.symbol == null) e.symbol = "SDAT_SBNK_" + String.format("%04d", i);
				if(iptr == 0) continue;
				
				long pos = Integer.toUnsignedLong(iptr);
				e.fileID = Short.toUnsignedInt(info.shortFromFile(pos)); pos+=4;
				for(int j = 0; j < 4; j++)
				{
					e.wavearcs[j] = Short.toUnsignedInt(info.shortFromFile(pos)); pos+=2;
				}
				
				e.offset = file_alloc_table[e.fileID][0];
				e.size = file_alloc_table[e.fileID][1];
			}
		}
		else arc.bank = new ArrayList<BANKEntry>(16);
		
		//--WAVEARC
		info.setCurrentPosition(recoff[3]);
		count = info.nextInt();
		if(count > 0)
		{
			arc.wavearc = new ArrayList<FileEntry>(count);
			String[] names = namemap.get("WAVEARC");
			for(int i = 0; i < count; i++)
			{
				int iptr = info.nextInt();
				FileEntry e = new FileEntry();
				arc.wavearc.add(e);
				e.ext = "swar";
				e.myidx = i;
				if(names != null) e.symbol = names[i];
				if(e.symbol == null) e.symbol = "SDAT_SWAR_" + String.format("%04d", i);
				if(iptr == 0) continue;
				
				long pos = Integer.toUnsignedLong(iptr);
				e.fileID = Short.toUnsignedInt(info.shortFromFile(pos));
				
				e.offset = file_alloc_table[e.fileID][0];
				e.size = file_alloc_table[e.fileID][1];
			}
		}
		else arc.wavearc = new ArrayList<FileEntry>();
		
		//--PLAYER
		info.setCurrentPosition(recoff[4]);
		count = info.nextInt();
		if(count > 0)
		{
			arc.player = new ArrayList<FileEntry>(count);
			String[] names = namemap.get("PLAYER");
			for(int i = 0; i < count; i++)
			{
				//int iptr = info.nextInt();
				info.nextInt();
				FileEntry e = new FileEntry();
				arc.player.add(e);
				e.myidx = i;
				e.ext = "plyr";
				e.fileID = -2;
				if(names != null) e.symbol = names[i];
				if(e.symbol == null) e.symbol = "SDAT_PLYR_" + String.format("%04d", i);
				//if(iptr == 0) continue;
			}
		}
		else arc.player = new ArrayList<FileEntry>();
		
		//--GROUP
		info.setCurrentPosition(recoff[5]);
		count = info.nextInt();
		if(count > 0)
		{
			arc.groups = new ArrayList<Group>(count);
			String[] names = namemap.get("GROUP");
			for(int i = 0; i < count; i++)
			{
				int iptr = info.nextInt();
				if(iptr == 0){Group g = new Group(0); arc.groups.add(g); continue;}
				
				long pos = Integer.toUnsignedLong(iptr);
				int mcount = info.intFromFile(pos); pos+=4;
				
				Group g = new Group(mcount);
				if(names != null) g.symbol = names[i];
				if(g.symbol == null) g.symbol = "SDAT_GROUP_" + String.format("%04d", i);
				arc.groups.add(g);
				
				//Read group members
				for(int j = 0; j < mcount; j++)
				{
					int type = info.intFromFile(pos); pos += 4;
					int idx = info.intFromFile(pos); pos += 4;
					if(type == 0) continue;
					
					FileEntry member = null;
					switch(type)
					{
					case TYPE_SEQ:
						member = arc.seq.get(idx);
						break;
					case TYPE_SEQARC:
						member = arc.seqarc.get(idx);
						break;
					case TYPE_BANK:
						member = arc.bank.get(idx);
						break;
					case TYPE_WAVEARC:
						member = arc.wavearc.get(idx);
						break;
					}
					if(member != null) g.members.add(member);
				}
			}
		}
		else arc.groups = new ArrayList<Group>();
		
		//--PLAYER2
		info.setCurrentPosition(recoff[6]);
		count = info.nextInt();
		if(count > 0)
		{
			arc.player2 = new ArrayList<FileEntry>(count);
			String[] names = namemap.get("PLAYER2");
			for(int i = 0; i < count; i++)
			{
				info.nextInt();
				FileEntry e = new FileEntry();
				e.myidx = i;
				e.ext = "ply2";
				e.fileID = -2;
				if(names != null) e.symbol = names[i];
				if(e.symbol == null) e.symbol = "SDAT_PLY2_" + String.format("%04d", i);
				arc.player2.add(e);
			}
		}
		else arc.player2 = new ArrayList<FileEntry>();
		
		//--STRM
		info.setCurrentPosition(recoff[7]);
		count = info.nextInt();
		if(count > 0)
		{
			arc.strm = new ArrayList<STRMEntry>(count);
			String[] names = namemap.get("STRM");
			for(int i = 0; i < count; i++)
			{
				int iptr = info.nextInt();
				STRMEntry e = new STRMEntry();
				e.ext = "strm";
				e.myidx = i;
				if(names != null) e.symbol = names[i];
				if(e.symbol == null) e.symbol = "SDAT_STRM_" + String.format("%04d", i);
				arc.strm.add(e);
				if(iptr == 0) continue;
				
				long pos = Integer.toUnsignedLong(iptr);
				e.fileID = Short.toUnsignedInt(info.shortFromFile(pos)); pos += 4;
				e.vol = Byte.toUnsignedInt(info.getByte(pos)); pos++;
				e.pri = Byte.toUnsignedInt(info.getByte(pos)); pos++;
				e.ply = Byte.toUnsignedInt(info.getByte(pos));
				
				e.offset = file_alloc_table[e.fileID][0];
				e.size = file_alloc_table[e.fileID][1];
			}
		}
		else arc.strm = new ArrayList<STRMEntry>();
		
		arc.freeSourceData();
		
		return arc;
	}
	
	/*----- Serialization -----*/
	
	/*----- Metadata Table -----*/
	
	private boolean writeSeqMeta(String path) throws IOException
	{
		//Order alphabetically
		Map<String, Integer> imap = new HashMap<String, Integer>();
		List<String> nlist = new LinkedList<String>();
		for(SEQEntry e : seq)
		{
			nlist.add(e.symbol);
			imap.put(e.symbol, e.myidx);
		}
		Collections.sort(nlist);
		
		//Open Writer
		BufferedWriter bw = new BufferedWriter(new FileWriter(path));
		
		//Write TOC
		bw.write("============================================\n");
		bw.write("=======         INDEX LOOKUP         =======\n");
		bw.write("============================================\n");
		for(String n : nlist)
		{
			bw.write(n + "\t");
			Integer idx = imap.get(n);
			if(idx == null) bw.write("N/A\n");
			else bw.write(idx + "\n");
		}
		bw.write("\n\n");
		
		//Write metadata
		bw.write("============================================\n");
		bw.write("=======        SEQ METADATA          =======\n");
		bw.write("============================================\n");
		int i = 0;
		for(SEQEntry e : seq)
		{
			bw.write("----> SEQ " + i + "\n");
			bw.write("\t" + e.symbol + "." + e.ext + "\n");
			bw.write("\t\tBank ID: " + e.bankID + "\n");
			bw.write("\t\tVolume: " + e.vol + "\n");
			bw.write("\t\tChannel Pressure: " + e.cpr + "\n");
			bw.write("\t\tPolyphony Pressure: " + e.ppr + "\n");
			bw.write("\t\tPlayer: " + e.ply + "\n");
			i++;
		}
		
		//Close Writer
		bw.close();
		return true;
	}
	
	private boolean writeSeqArcMeta(String path) throws IOException
	{
		//Order alphabetically
		Map<String, Integer> imap = new HashMap<String, Integer>();
		List<String> nlist = new LinkedList<String>();
		for(SEQArcEntry e : seqarc)
		{
			nlist.add(e.symbol);
			imap.put(e.symbol, e.myidx);
		}
		Collections.sort(nlist);
		
		//Open Writer
		BufferedWriter bw = new BufferedWriter(new FileWriter(path));
		
		//Write TOC
		bw.write("============================================\n");
		bw.write("=======         INDEX LOOKUP         =======\n");
		bw.write("============================================\n");
		for(String n : nlist)
		{
			bw.write(n + "\t");
			Integer idx = imap.get(n);
			if(idx == null) bw.write("N/A\n");
			else bw.write(idx + "\n");
		}
		bw.write("\n\n");
		
		//Write metadata
		bw.write("============================================\n");
		bw.write("=======        SEQARC METADATA       =======\n");
		bw.write("============================================\n");
		int i = 0;
		for(SEQArcEntry e : seqarc)
		{
			bw.write("----> SEQARC " + i + "\n");
			bw.write("\t" + e.symbol + "." + e.ext + "\n");
			int seqcount = e.inner_names.length;
			bw.write("\t\tSeq Count: " + seqcount + "\n");
			for(int j = 0; j < seqcount; j++)
			{
				bw.write("\t\t\t" + e.inner_names[j] + "\n");
			}
			i++;
		}
		
		//Close Writer
		bw.close();
		return true;
	}
	
	private boolean writeBankMeta(String path) throws IOException
	{
		//Order alphabetically
		Map<String, Integer> imap = new HashMap<String, Integer>();
		List<String> nlist = new LinkedList<String>();
		for(BANKEntry e : bank)
		{
			nlist.add(e.symbol);
			imap.put(e.symbol, e.myidx);
		}
		Collections.sort(nlist);
		
		//Open Writer
		BufferedWriter bw = new BufferedWriter(new FileWriter(path));
		
		//Write TOC
		bw.write("============================================\n");
		bw.write("=======         INDEX LOOKUP         =======\n");
		bw.write("============================================\n");
		for(String n : nlist)
		{
			bw.write(n + "\t");
			Integer idx = imap.get(n);
			if(idx == null) bw.write("N/A\n");
			else bw.write(idx + "\n");
		}
		bw.write("\n\n");
		
		//Write metadata
		bw.write("============================================\n");
		bw.write("=======       BANK METADATA          =======\n");
		bw.write("============================================\n");
		int i = 0;
		for(BANKEntry e : bank)
		{
			bw.write("----> BANK " + i + "\n");
			bw.write("\t" + e.symbol + "." + e.ext + "\n");
			bw.write("\t\tWave Archives: [");
			if(e.wavearcs[0] != 0xFFFF) bw.write(e.wavearcs[0] + ", ");
			else bw.write("null, ");
			if(e.wavearcs[1] != 0xFFFF) bw.write(e.wavearcs[1] + ", ");
			else bw.write("null, ");
			if(e.wavearcs[2] != 0xFFFF) bw.write(e.wavearcs[2] + ", ");
			else bw.write("null, ");
			if(e.wavearcs[3] != 0xFFFF) bw.write(e.wavearcs[3] + "]\n");
			else bw.write("null]\n");
			i++;
		}
		
		//Close Writer
		bw.close();
		return true;
	}
	
	private boolean writeWaveArcMeta(String path) throws IOException
	{
		//Order alphabetically
		Map<String, Integer> imap = new HashMap<String, Integer>();
		List<String> nlist = new LinkedList<String>();
		for(FileEntry e : wavearc)
		{
			nlist.add(e.symbol);
			imap.put(e.symbol, e.myidx);
		}
		Collections.sort(nlist);
		
		//Open Writer
		BufferedWriter bw = new BufferedWriter(new FileWriter(path));
		
		//Write TOC
		bw.write("============================================\n");
		bw.write("=======         INDEX LOOKUP         =======\n");
		bw.write("============================================\n");
		for(String n : nlist)
		{
			bw.write(n + "\t");
			Integer idx = imap.get(n);
			if(idx == null) bw.write("N/A\n");
			else bw.write(idx + "\n");
		}
		bw.write("\n\n");
		
		//Write metadata
		bw.write("============================================\n");
		bw.write("=======       WAVEARC METADATA       =======\n");
		bw.write("============================================\n");
		int i = 0;
		for(FileEntry e : wavearc)
		{
			bw.write("----> WAVEARC " + i + "\n");
			bw.write("\t" + e.symbol + "." + e.ext + "\n");
			i++;
		}
		
		//Close Writer
		bw.close();
		return true;
	}
	
	private boolean writePlayerMeta(String path) throws IOException
	{
		//Order alphabetically
		Map<String, Integer> imap = new HashMap<String, Integer>();
		List<String> nlist = new LinkedList<String>();
		for(FileEntry e : player)
		{
			nlist.add(e.symbol);
			imap.put(e.symbol, e.myidx);
		}
		Collections.sort(nlist);
		
		//Open Writer
		BufferedWriter bw = new BufferedWriter(new FileWriter(path));
		
		//Write TOC
		bw.write("============================================\n");
		bw.write("=======         INDEX LOOKUP         =======\n");
		bw.write("============================================\n");
		for(String n : nlist)
		{
			bw.write(n + "\t");
			Integer idx = imap.get(n);
			if(idx == null) bw.write("N/A\n");
			else bw.write(idx + "\n");
		}
		bw.write("\n\n");
		
		//Close Writer
		bw.close();
		return true;
	}
	
	private boolean writePlayer2Meta(String path) throws IOException
	{
		//Order alphabetically
		Map<String, Integer> imap = new HashMap<String, Integer>();
		List<String> nlist = new LinkedList<String>();
		for(FileEntry e : player2)
		{
			nlist.add(e.symbol);
			imap.put(e.symbol, e.myidx);
		}
		Collections.sort(nlist);
		
		//Open Writer
		BufferedWriter bw = new BufferedWriter(new FileWriter(path));
		
		//Write TOC
		bw.write("============================================\n");
		bw.write("=======         INDEX LOOKUP         =======\n");
		bw.write("============================================\n");
		for(String n : nlist)
		{
			bw.write(n + "\t");
			Integer idx = imap.get(n);
			if(idx == null) bw.write("N/A\n");
			else bw.write(idx + "\n");
		}
		bw.write("\n\n");
		
		//Close Writer
		bw.close();
		return true;
	}
	
	private boolean writeStrmMeta(String path) throws IOException
	{
		//Order alphabetically
		Map<String, Integer> imap = new HashMap<String, Integer>();
		List<String> nlist = new LinkedList<String>();
		for(STRMEntry e : strm)
		{
			nlist.add(e.symbol);
			imap.put(e.symbol, e.myidx);
		}
		Collections.sort(nlist);
		
		//Open Writer
		BufferedWriter bw = new BufferedWriter(new FileWriter(path));
		
		//Write TOC
		bw.write("============================================\n");
		bw.write("=======         INDEX LOOKUP         =======\n");
		bw.write("============================================\n");
		for(String n : nlist)
		{
			bw.write(n + "\t");
			Integer idx = imap.get(n);
			if(idx == null) bw.write("N/A\n");
			else bw.write(idx + "\n");
		}
		bw.write("\n\n");
		
		//Write metadata
		bw.write("============================================\n");
		bw.write("=======        STRM METADATA         =======\n");
		bw.write("============================================\n");
		int i = 0;
		for(STRMEntry e : strm)
		{
			bw.write("----> SEQ " + i + "\n");
			bw.write("\t" + e.symbol + "." + e.ext + "\n");
			bw.write("\t\tVolume: " + e.vol + "\n");
			bw.write("\t\tPriority: " + e.pri + "\n");
			bw.write("\t\tPlayer: " + e.ply + "\n");
			i++;
		}
		
		//Close Writer
		bw.close();
		return true;
	}
	
	public boolean writeMetaFiles(String dirpath) throws IOException
	{
		String metastem = dirpath + File.separator + "sdat_meta";
		String metafile = metastem + "_seq.txt";
		writeSeqMeta(metafile);
		
		metafile = metastem + "_seqarc.txt";
		writeSeqArcMeta(metafile);
		
		metafile = metastem + "_bank.txt";
		writeBankMeta(metafile);
		
		metafile = metastem + "_wavearc.txt";
		writeWaveArcMeta(metafile);
		
		metafile = metastem + "_player.txt";
		writePlayerMeta(metafile);
		
		metafile = metastem + "_player2.txt";
		writePlayer2Meta(metafile);
		
		metafile = metastem + "_strm.txt";
		writeStrmMeta(metafile);
		
		return true;
	}
	
	public boolean extractToDisk(String dirpath) throws IOException
	{
		//(Includes text file with metadata)
		
		DirectoryNode root = getArchiveView();
		root.dumpTo(dirpath);
		
		
		return writeMetaFiles(dirpath);
	}
	
	/*----- Getters -----*/
		
	public DirectoryNode getArchiveView()
	{
		DirectoryNode root = new DirectoryNode(null, "sdat_root");
		
		//Do groups first
		Set<String> ingroup = new TreeSet<String>();
		for(Group g : groups)
		{
			if(g.members.isEmpty()) continue;
			DirectoryNode gnode = new DirectoryNode(root, g.symbol);
			for(FileEntry m : g.members)
			{
				if(m.fileID == -1) continue;
				String fname = m.symbol + "." + m.ext;
				ingroup.add(fname);
				FileNode fnode = new FileNode(gnode, fname);
				m.addMetadata(fnode);
				fnode.setOffset(m.offset);
				fnode.setLength(m.size);
			}
		}
		
		//Everything else
		DirectoryNode dir = new DirectoryNode(root, "SEQ");
		for(SEQEntry e : seq)
		{
			if(e.fileID == -1) continue;
			String fname = e.symbol + "." + e.ext;
			if(ingroup.contains(fname)) continue;
			FileNode fnode = new FileNode(dir, fname);
			e.addMetadata(fnode);
			fnode.setOffset(e.offset);
			fnode.setLength(e.size);
		}
		if(dir.getChildCount() < 1) root.removeChild(dir);
		
		dir = new DirectoryNode(root, "SEQARC");
		for(SEQArcEntry e : seqarc)
		{
			if(e.fileID == -1) continue;
			String fname = e.symbol + "." + e.ext;
			if(ingroup.contains(fname)) continue;
			FileNode fnode = new FileNode(dir, fname);
			e.addMetadata(fnode);
			fnode.setOffset(e.offset);
			fnode.setLength(e.size);
		}
		if(dir.getChildCount() < 1) root.removeChild(dir);
		
		dir = new DirectoryNode(root, "BANK");
		for(BANKEntry e : bank)
		{
			if(e.fileID == -1) continue;
			String fname = e.symbol + "." + e.ext;
			if(ingroup.contains(fname)) continue;
			FileNode fnode = new FileNode(dir, fname);
			e.addMetadata(fnode);
			fnode.setOffset(e.offset);
			fnode.setLength(e.size);
		}
		if(dir.getChildCount() < 1) root.removeChild(dir);
		
		dir = new DirectoryNode(root, "WAVEARC");
		for(FileEntry e : wavearc)
		{
			if(e.fileID == -1) continue;
			String fname = e.symbol + "." + e.ext;
			if(ingroup.contains(fname)) continue;
			FileNode fnode = new FileNode(dir, fname);
			e.addMetadata(fnode);
			fnode.setOffset(e.offset);
			fnode.setLength(e.size);
		}
		if(dir.getChildCount() < 1) root.removeChild(dir);
		
		dir = new DirectoryNode(root, "PLAYER");
		for(FileEntry e : player)
		{
			String fname = e.symbol + "." + e.ext;
			if(ingroup.contains(fname)) continue;
			FileNode fnode = new FileNode(dir, fname);
			e.addMetadata(fnode);
			fnode.setOffset(e.offset);
			fnode.setLength(e.size);
		}
		if(dir.getChildCount() < 1) root.removeChild(dir);
		
		dir = new DirectoryNode(root, "PLAYER2");
		for(FileEntry e : player2)
		{
			String fname = e.symbol + "." + e.ext;
			if(ingroup.contains(fname)) continue;
			FileNode fnode = new FileNode(dir, fname);
			e.addMetadata(fnode);
			fnode.setOffset(e.offset);
			fnode.setLength(e.size);
		}
		if(dir.getChildCount() < 1) root.removeChild(dir);
		
		dir = new DirectoryNode(root, "STRM");
		for(STRMEntry e : strm)
		{
			if(e.fileID == -1) continue;
			String fname = e.symbol + "." + e.ext;
			if(ingroup.contains(fname)) continue;
			FileNode fnode = new FileNode(dir, fname);
			e.addMetadata(fnode);
			fnode.setOffset(e.offset);
			fnode.setLength(e.size);
		}
		if(dir.getChildCount() < 1) root.removeChild(dir);
		
		return root;
	}
	
	public FileEntry getSWARMetadata(int idx){
		if(idx < 0 || idx > wavearc.size()) return null;
		FileEntry e = wavearc.get(idx);
		return e;
	}
	
	public DSWarc getSWAR(int idx) throws UnsupportedFileTypeException, IOException{
		if(src_path == null) throw new UnsupportedOperationException("Path to SDAT required for data retrieval!");
		if(idx < 0 || idx > wavearc.size()) return null;
		FileEntry e = wavearc.get(idx); if(e == null) return null;
		long off = src_off + e.offset;
		FileBuffer swar = FileBuffer.createBuffer(src_path, off, off + e.size);
		DSWarc warc = DSWarc.readSWAR(swar, 0);
		
		return warc;
	}
	
	public SEQEntry getSSEQMetadata(int idx){
		if(idx < 0 || idx > seq.size()) return null;
		return seq.get(idx);
	}
	
	public DSSeq getSSEQ(int idx) throws IOException, UnsupportedFileTypeException{
		if(src_path == null) throw new UnsupportedOperationException("Path to SDAT required for data retrieval!");
		SEQEntry e = getSSEQMetadata(idx);
		if(e == null) return null;
		long off = src_off + e.offset;
		FileBuffer dat = FileBuffer.createBuffer(src_path, off, off + e.size);
		DSSeq seq = DSSeq.readSSEQ(dat);
		
		return seq;
	}
	
	public BANKEntry getSBNKMetadata(int idx){
		if(idx < 0 || idx > bank.size()) return null;
		return bank.get(idx);
	}
	
	public DSBank getSBNK(int idx) throws IOException, UnsupportedFileTypeException{
		if(src_path == null) throw new UnsupportedOperationException("Path to SDAT required for data retrieval!");
		BANKEntry e = getSBNKMetadata(idx);
		if(e == null) return null;
		long off = src_off + e.offset;
		FileBuffer dat = FileBuffer.createBuffer(src_path, off, off + e.size);
		DSBank bnk = DSBank.readSBNK(dat, 0);
		
		return bnk;
	}
	
	public STRMEntry getSTRMMetadata(int idx){
		if(idx < 0 || idx > strm.size()) return null;
		return strm.get(idx);
	}
	
	public DSStream getSTRM(int idx) throws IOException, UnsupportedFileTypeException{
		if(src_path == null) throw new UnsupportedOperationException("Path to SDAT required for data retrieval!");
		STRMEntry e = getSTRMMetadata(idx);
		if(e == null) return null;
		long off = src_off + e.offset;
		FileBuffer dat = FileBuffer.createBuffer(src_path, off, off + e.size);
		DSStream strm = DSStream.readSTRM(dat, 0);
		
		return strm;
	}
	
	/*----- Setters -----*/
	
	public void setSourceLocation(String filepath, long offset){
		this.src_path = filepath;
		this.src_off = offset;
	}
	
	/*----- Other -----*/
	
	/*----- Node Linking -----*/
	
	private static String searchForFile(DirectoryNode dir, String typetag, String fid, String path){
		//path += dir.getFileName() + "/";
		
		//System.err.println("Searching: " + dir.getFileName());
		List<DirectoryNode> cdirs = new LinkedList<DirectoryNode>();
		List<FileNode> children = dir.getChildren();
		
		//Search this level first
		for(FileNode child : children){
			if(child instanceof DirectoryNode){
				cdirs.add((DirectoryNode)child);
			}
			else{
				//Look for type tag
				//If has tag, look for matching ID
				//System.err.println("Checking: " + child.getFileName());
				String metaval = child.getMetadataValue(FNMETAKEY_SDATTYPE);
				//System.err.println("Meta Type: " + metaval);
				if(metaval == null) continue;
				if(!metaval.equals(typetag)) continue;
				metaval = child.getMetadataValue(FNMETAKEY_INDEX);
				//System.err.println("Index: " + metaval);
				if(metaval == null) continue;
				if(metaval.equals(fid)) return path + child.getFileName();
			}
		}
		
		//Search dirs if nothing found
		for(DirectoryNode child : cdirs){
			//System.err.println("Child: " + child.getFileName());
			String result = searchForFile(child, typetag, fid, path + child.getFileName() + "/");
			if(result != null) return result;
		}
		
		return null;
	}
	
	public static String findLinkedBank(FileNode seq, int id){
		//Returns relative path
		//Get metadata bank id
		String bankid = Integer.toString(id);
		if(bankid == null) return null;
		
		//Now look for a bank with that SDATIDX
		//Start with current directory, then move up
		String path = "";
		DirectoryNode dir = seq.getParent();
		while(dir != null){
			String match = searchForFile(dir, MAGIC_BNK, bankid, path);
			if(match != null) return match;
			path = "../" + path;
			dir = dir.getParent();
		}
		
		return null;
	}
		
	public static String findLinkedBank(FileNode seq){
		//Returns relative path
		//Get metadata bank id
		String bankid = seq.getMetadataValue(FNMETAKEY_BANKID);
		//System.err.println("Bank ID: " + bankid);
		if(bankid == null) return null;
		
		//Now look for a bank with that SDATIDX
		//Start with current directory, then move up
		String path = "";
		DirectoryNode dir = seq.getParent();
		while(dir != null){
			String match = searchForFile(dir, MAGIC_BNK, bankid, path);
			if(match != null) return match;
			path = "../" + path;
			dir = dir.getParent();
		}
		
		return null;
	}
	
	public static String findLinkedWavearc(FileNode bnk, String warcid){
		String path = "";
		DirectoryNode dir = bnk.getParent();
		while(dir != null){
			String match = searchForFile(dir, MAGIC_WAR, warcid, path);
			if(match != null) return match;
			path = "../" + path;
			dir = dir.getParent();
		}
		return null;
	}
	
	public static String[] findLinkedWavearcs(FileNode bnk){

		String[] matches = new String[4];
		
		for(int i = 0; i < 4; i++){
			String warcid = bnk.getMetadataValue(FNMETAKEY_WARC_STEM + i);
			if(warcid == null) continue;
			
			//Now look for a bank with that SDATIDX
			//Start with current directory, then move up
			String path = "";
			DirectoryNode dir = bnk.getParent();
			while(dir != null){
				String match = searchForFile(dir, MAGIC_WAR, warcid, path);
				if(match != null) matches[i] = match;
				path = "../" + path;
				dir = dir.getParent();
			}
		}
		
		return matches;
	}
	
	public static DSBank loadLinkedBank(FileNode seq) throws IOException, UnsupportedFileTypeException{
		String path = seq.getMetadataValue(FNMETAKEY_BANKLINK);
		if(path == null){
			//Check for ID.
			String bnkid = seq.getMetadataValue(FNMETAKEY_BANKID);
			if(bnkid == null){
				System.err.println("DSSoundArchive.loadLinkedBank || No bank linked to provided node!");
				return null;
			}
			path = findLinkedBank(seq);
			if(path == null){
				System.err.println("DSSoundArchive.loadLinkedBank || Path couldn't be found for bank with ID " + bnkid);
				return null; //We're just not finding it...
			}
			//If path was found, mark in node
			seq.setMetadataValue(FNMETAKEY_BANKLINK, path);
		}
		//System.err.println("Bank path: " + path);
		
		DirectoryNode dir = seq.getParent();
		FileNode bnknode = dir.getNodeAt(path); //SHOULD be able to interpret the ../
		if(bnknode == null){
			System.err.println("DSSoundArchive.loadLinkedBank || Couldn't find sbnk: Node not found at path " + path);
			return null;
		}
		FileBuffer dat = bnknode.loadDecompressedData();
		DSBank bank = DSBank.readSBNK(dat, 0);
		
		return bank;
	}
	
	public static DSWarc[] loadLinkedWavearcs(FileNode bnk) throws IOException, UnsupportedFileTypeException{
		DSWarc[] warcs = new DSWarc[4];

		for(int i = 0; i < 4; i++){
			//Check if path is marked.
			String path = bnk.getMetadataValue(FNMETAKEY_WARCLINK_STEM + i);
			if(path == null){
				//Check for ID.
				String warcid = bnk.getMetadataValue(FNMETAKEY_WARC_STEM + i);
				if(warcid == null) continue; //There isn't one linked to this slot
				path = findLinkedWavearc(bnk, warcid);
				if(path == null){
					System.err.println("DSSoundArchive.loadLinkedWavearcs || Couldn't find warc #" + i + ": ID = " + warcid);
					continue; //We're just not finding it...
				}
				//If path was found, mark in node
				bnk.setMetadataValue(FNMETAKEY_WARCLINK_STEM + i, path);
			}
			//System.err.println("Warc path: " + path);
			//Try to load from path
			DirectoryNode dir = bnk.getParent();
			FileNode warcnode = dir.getNodeAt(path); //SHOULD be able to interpret the ../
			if(warcnode == null){
				System.err.println("DSSoundArchive.loadLinkedWavearcs || Couldn't find warc #" + i + ": Node not found at path " + path);
				continue;
			}
			FileBuffer dat = warcnode.loadDecompressedData();
			DSWarc warc = DSWarc.readSWAR(dat, 0);
			warcs[i] = warc;
		}
		
		return warcs;
	}
	
	/*----- Definition -----*/
	
	private static TypeDef static_def;
	private static StdConverter static_conv;
	
	public static class TypeDef extends SoundArchiveDef
	{
		
		public static final String[] EXT_LIST = {"sdat"};
		
		private String str;
		
		public TypeDef()
		{
			str = DEFO_ENG_STR;
		}

		@Override
		public Collection<String> getExtensions() 
		{
			List<String> extlist = new ArrayList<String>(EXT_LIST.length);
			for(String s : EXT_LIST)extlist.add(s);
			return extlist;
		}

		@Override
		public String getDescription() {return str;}

		@Override
		public int getTypeID() {return TYPE_ID;}

		@Override
		public void setDescriptionString(String s) {str = s;}
		
		public DirectoryNode getContents(FileNode node) throws IOException, UnsupportedFileTypeException
		{
			FileBuffer buffer = node.loadDecompressedData();
			DSSoundArchive arc = DSSoundArchive.readSDAT(buffer);
			
			return arc.getArchiveView();
		}
		
		public String getDefaultExtension() {return "sdat";}
	}
	
	public static TypeDef getTypeDef()
	{
		if(static_def != null) return static_def;
		static_def = new TypeDef();
		return static_def;
	}

	public static class StdConverter implements Converter
	{
		
		private String from_str;
		private String to_str;
		
		public StdConverter()
		{
			from_str = DEFO_ENG_STR;
			to_str = "System Directory + Metadata Files";
		}

		public String getFromFormatDescription() {return from_str;}
		public String getToFormatDescription() {return to_str;}
		public void setFromFormatDescription(String s) {from_str = s;}
		public void setToFormatDescription(String s) {to_str = s;}

		@Override
		public void writeAsTargetFormat(String inpath, String outpath) throws IOException, UnsupportedFileTypeException 
		{
			DSSoundArchive arc = DSSoundArchive.readSDAT(inpath);
			arc.extractToDisk(outpath);
		}
		
		public void writeAsTargetFormat(FileBuffer input, String outpath) throws IOException, UnsupportedFileTypeException
		{
			DSSoundArchive arc = DSSoundArchive.readSDAT(input);
			arc.extractToDisk(outpath);
		}
		
		public void writeAsTargetFormat(FileNode node, String outpath) 
				throws IOException, UnsupportedFileTypeException{
			FileBuffer dat = node.loadDecompressedData();
			writeAsTargetFormat(dat, outpath);
		}
		
		public String changeExtension(String path)
		{
			return path;
		}
		
	}
	
	public static StdConverter getConverter()
	{
		if(static_conv == null) static_conv = new StdConverter();
		return static_conv;
	}
	
	/*----- Debug -----*/
	
	public void printTypeViewToStdOut()
	{
		System.out.println("======== SDAT Debug Print ========");
		System.out.println("--- SEQ ---");
		int count = seq.size();
		System.out.println("Entry count: " + count);
		for(int i = 0; i < count; i++)
		{
			System.out.println("\t---> SEQ " + i);
			SEQEntry e = seq.get(i);
			String name = e.symbol;
			if(name == null) name = "SDAT_SSEQ" + String.format("%04d", i);
			System.out.println("\t\t" + name + ".sseq");
			if(e.fileID == -1) System.out.println("\t\t<null>");
			else
			{
				System.out.println("\t\tFile ID: 0x" + Integer.toHexString(e.fileID) + " (" + e.fileID + ")");
				long edpos = e.offset + e.size;
				System.out.println("\t\tLocation: 0x" + Long.toHexString(e.offset) + " - 0x" + Long.toHexString(edpos));
				System.out.println("\t\tBank ID: " + e.bankID);
				System.out.println("\t\tVolume: " + e.vol);
				System.out.println("\t\tPolyphonic Pressure: " + e.ppr);
				System.out.println("\t\tChannel Pressure: " + e.cpr);
				System.out.println("\t\tPlayer: " + e.ply);	
			}
		}
		System.out.println();
		
		System.out.println("--- SEQARC ---");
		count = seqarc.size();
		System.out.println("Entry count: " + count);
		for(int i = 0; i < count; i++)
		{
			System.out.println("\t---> SEQARC " + i);
			SEQArcEntry e = seqarc.get(i);
			String name = e.symbol;
			if(name == null) name = "SDAT_SSAR" + String.format("%04d", i);
			System.out.println("\t\t" + name + ".ssar");
			if(e.fileID == -1) System.out.println("\t\t<null>");
			else{
				System.out.println("\t\tFile ID: 0x" + Integer.toHexString(e.fileID) + " (" + e.fileID + ")");
				long edpos = e.offset + e.size;
				System.out.println("\t\tLocation: 0x" + Long.toHexString(e.offset) + " - 0x" + Long.toHexString(edpos));
				System.out.print("\t\tMember SSEQs:");
				int inamecount = e.inner_names.length;
				for(int j = 0; j < inamecount; j++)
				{
					String iname = e.inner_names[j];
					if(iname == null) iname = "SSAR_" + String.format("%04d", i) + "_SSEQ_" + String.format("%04d", j);
					System.out.print("\t\t\t" + iname + ".sseq");
				}	
			}
		}
		System.out.println();
		
		System.out.println("--- BANK ---");
		count = bank.size();
		System.out.println("Entry count: " + count);
		for(int i = 0; i < count; i++)
		{
			System.out.println("\t---> BANK " + i);
			BANKEntry e = bank.get(i);
			String name = e.symbol;
			if(name == null) name = "SDAT_SBNK" + String.format("%04d", i);
			System.out.println("\t\t" + name + ".sbnk");
			if(e.fileID == -1) System.out.println("\t\t<null>");
			else
			{
				System.out.println("\t\tFile ID: 0x" + Integer.toHexString(e.fileID) + " (" + e.fileID + ")");
				long edpos = e.offset + e.size;
				System.out.println("\t\tLocation: 0x" + Long.toHexString(e.offset) + " - 0x" + Long.toHexString(edpos));
				System.out.println("\t\tWave Arc 0: " + e.wavearcs[0]);
				System.out.println("\t\tWave Arc 1: " + e.wavearcs[1]);
				System.out.println("\t\tWave Arc 2: " + e.wavearcs[2]);
				System.out.println("\t\tWave Arc 3: " + e.wavearcs[3]);	
			}
		}
		System.out.println();
		
		System.out.println("--- WAVEARC ---");
		count = wavearc.size();
		System.out.println("Entry count: " + count);
		for(int i = 0; i < count; i++)
		{
			System.out.println("\t---> WAVEARC " + i);
			FileEntry e = wavearc.get(i);
			String name = e.symbol;
			if(name == null) name = "SDAT_SWAR" + String.format("%04d", i);
			System.out.println("\t\t" + name + ".swar");
			if(e.fileID == -1) System.out.println("\t\t<null>");
			else
			{
				System.out.println("\t\tFile ID: 0x" + Integer.toHexString(e.fileID) + " (" + e.fileID + ")");
				long edpos = e.offset + e.size;
				System.out.println("\t\tLocation: 0x" + Long.toHexString(e.offset) + " - 0x" + Long.toHexString(edpos));	
			}
		}
		System.out.println();
		
		System.out.println("--- PLAYER ---");
		count = player.size();
		System.out.println("Entry count: " + count);
		for(int i = 0; i < count; i++)
		{
			System.out.println("\t---> PLAYER " + i);
			FileEntry e = player.get(i);
			String name = e.symbol;
			if(name == null) name = "SDAT_PLAYER_" + String.format("%04d", i);
			System.out.println("\t\t" + name);
		}
		System.out.println();
		
		System.out.println("--- PLAYER 2 ---");
		count = player2.size();
		System.out.println("Entry count: " + count);
		for(int i = 0; i < count; i++)
		{
			System.out.println("\t---> PLAYER 2 " + i);
			FileEntry e = player2.get(i);
			String name = e.symbol;
			if(name == null) name = "SDAT_PLAYER2_" + String.format("%04d", i);
			System.out.println("\t\t" + name);
		}
		System.out.println();
		
		System.out.println("--- STRM ---");
		count = strm.size();
		System.out.println("Entry count: " + count);
		for(int i = 0; i < count; i++)
		{
			System.out.println("\t---> STRM " + i);
			STRMEntry e = strm.get(i);
			String name = e.symbol;
			if(name == null) name = "SDAT_STRM" + String.format("%04d", i);
			System.out.println("\t\t" + name + ".strm");
			if(e.fileID == -1) System.out.println("\t\t<null>");
			else
			{
				System.out.println("\t\tFile ID: 0x" + Integer.toHexString(e.fileID) + " (" + e.fileID + ")");
				long edpos = e.offset + e.size;
				System.out.println("\t\tLocation: 0x" + Long.toHexString(e.offset) + " - 0x" + Long.toHexString(edpos));
				System.out.println("\t\tVolume: " + e.vol);
				System.out.println("\t\tPriority: " + e.pri);
				System.out.println("\t\tPlayer: " + e.ply);	
			}
		}
		System.out.println();
		
		System.out.println("--- GROUP ---");
		count = groups.size();
		System.out.println("Entry count: " + count);
		for(int i = 0; i < count; i++)
		{
			System.out.println("\t---> GROUP " + i);
			Group g = groups.get(i);
			String name = g.symbol;
			if(name == null) name = "GROUP_" + String.format("%04d", i);
			System.out.println("\t\t" + name);
			System.out.println("\t\tMembers: " + g.members.size());
			for(FileEntry m : g.members)
			{
				System.out.println("\t\t\t" + m.symbol);
			}
		}
		System.out.println();
	}
	
}
