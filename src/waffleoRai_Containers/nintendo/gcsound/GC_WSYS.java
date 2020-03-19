package waffleoRai_Containers.nintendo.gcsound;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;

public class GC_WSYS {

	/*--- Constants ---*/
	
	public static final String MAGIC_WSYS = "WSYS";
	
	public static final String MAGIC_WINF = "WINF";
	public static final String MAGIC_WBCT = "WBCT";
	
	public static final String MAGIC_SCNE = "SCNE";
	public static final String MAGIC_CDF = "C-DF";
	public static final String MAGIC_CST = "C-ST";
	public static final String MAGIC_CEX = "C-EX";
	
	public static final byte ZERO = 0x00;
	public static final byte BMAX = (byte)0xFF;
	
	/*--- Instance Variables ---*/
	
	private String file_path;
	
	private int wsys_id;
	private int sound_count;
	
	private List<AWRecord> winf;
	private List<SCNE> wbct;
	
	//private FileBuffer coverage;
	
	/*--- Structures ---*/
		
	public static class WaveRecord
	{
		public static final int UNK_BYTES = 16;
		public static final int REC_SIZE = 0x2C;
		
		public byte type;
		public byte unityKey;
		public int flags_1;
		public int sampleRate;
		public int flags_2;
		public int offset;
		public int size;
		public boolean loop_flag;
		public int loop_start;
		public int loop_end;
		public byte[] unknown;
		
		public int read_ptr;
		
		public WaveRecord()
		{
			type = -1;
			unityKey = 0x3C;
			flags_1 = 0x23;
			sampleRate = 44100;
			flags_2 = 0;
			offset = 0;
			size = 0;
			loop_flag = false;
			loop_start = 0;
			loop_end = 0;
			unknown = new byte[UNK_BYTES];
		}
		
		public WaveRecord(FileBuffer src, long stpos)
		{
			long cpos = stpos;
			cpos++; //Skip first byte, always seems to be 0xff (Sometimes 0xcc!!!)
			type = src.getByte(cpos); cpos++;
			unityKey = src.getByte(cpos); cpos++;
			//System.err.println("UnityKey: " + unityKey);
			cpos++; //Skip byte, always 0x00
			int flags = src.intFromFile(cpos); cpos+=4;
			flags_1 = (flags >>> 25) & 0xFF;
			flags_2 = flags & 0x1FF;
			sampleRate = (flags >>> 9) & 0xFFFF;
			offset = src.intFromFile(cpos); cpos+=4;
			size = src.intFromFile(cpos); cpos+=4;
			flags = src.intFromFile(cpos); cpos += 4;
			loop_flag = (flags != 0);
			loop_start = src.intFromFile(cpos); cpos+=4;
			loop_end = src.intFromFile(cpos); cpos+=4;
			
			unknown = new byte[UNK_BYTES];
			for(int i = 0; i < UNK_BYTES; i++)
			{
				unknown[i] = src.getByte(cpos); cpos++;
			}
		}
		
		public FileBuffer serializeMe()
		{
			FileBuffer mybytes = new FileBuffer(REC_SIZE, true);
			
			mybytes.addToFile(BMAX);
			mybytes.addToFile(type);
			mybytes.addToFile(unityKey);
			mybytes.addToFile(ZERO);
			
			int flags = flags_1 << 25;
			flags |= sampleRate << 9;
			flags |= flags_2 & 0x1FF;
			mybytes.addToFile(flags);
			
			mybytes.addToFile(offset);
			mybytes.addToFile(size);
			if(loop_flag) mybytes.addToFile(0xFFFFFFFF);
			else mybytes.addToFile(0x00000000);
			mybytes.addToFile(loop_start);
			mybytes.addToFile(loop_end);
			
			for(int i = 0; i < unknown.length; i++) mybytes.addToFile(unknown[i]);
			
			return mybytes;
		}
		
		public boolean isEmpty()
		{
			return (type == -1);
		}
		
	}
	
	public static class AWRecord
	{
		private boolean used_flag;
		
		public String fileName;
		public List<WaveRecord> waves;
		
		public int read_ptr;
		
		protected AWRecord()
		{
			used_flag = false;
		}
		
		public AWRecord(String name, int alloc)
		{
			used_flag = true;
			fileName = name;
			waves = new ArrayList<WaveRecord>(alloc);
		}
		
		public AWRecord(FileBuffer src, long wsys_off, long aw_off)
		{
			read_ptr = (int)(aw_off - wsys_off);
			
			used_flag = true;
			//Start with AW block
			long cpos = aw_off;
			fileName = src.getASCII_string(cpos, 112); cpos += 112;
			int w_count = src.intFromFile(cpos); cpos+=4;
			waves = new ArrayList<WaveRecord>(w_count);
			
			for(int i = 0; i < w_count; i++)
			{
				int ptr = src.intFromFile(cpos); cpos+=4;
				if(ptr == 0)
				{
					WaveRecord rec = new WaveRecord();
					waves.add(rec);
					continue;
				}
				long rpos = wsys_off + Integer.toUnsignedLong(ptr);
				WaveRecord rec = new WaveRecord(src, rpos);
				waves.add(rec);
				rec.read_ptr = ptr;
			}
		}
		
		public FileBuffer serializeAWRecord(long wavdat_block_offset)
		{
			//Estimate size
			int w_count = waves.size();
			int sz = 112+4;
			sz += 4 * w_count;
			int padding = (sz % 0x10);
			if(padding == 0) padding = 0x10;
			sz += padding;
			
			FileBuffer aw = new FileBuffer(sz, true);
			
			//File name
			if(fileName == null) fileName = "null";
			int slen = fileName.length();
			if(slen > 111)
			{
				fileName = fileName.substring(0, 111);
				slen = fileName.length();
			}
			aw.printASCIIToFile(fileName);
			while(aw.getFileSize() < 112) aw.addToFile(ZERO);
			
			aw.addToFile(w_count);
			int ptr = (int)wavdat_block_offset;
			for(int i = 0; i < w_count; i++)
			{
				if(waves.get(i).isEmpty()) aw.addToFile(0);
				else {aw.addToFile(ptr); ptr += WaveRecord.REC_SIZE;}
			}
			
			for(int i = 0; i < padding; i++) aw.addToFile(ZERO);
			
			return aw;
		}
		
		public boolean isEmpty()
		{
			return !used_flag;
		}
	
		public void cover(GC_WSYS wsys)
		{
			int w_count = waves.size();
			int aw_size = 112 + 4 + (4 * w_count);
			wsys.cover(read_ptr, aw_size, 0x20);
			
			for(WaveRecord w : waves)
			{
				wsys.cover(w.read_ptr, WaveRecord.REC_SIZE, 0x10);
			}
		}
		
	}
	
	public static class CDFRecord
	{
		public static final int UNK_WORDS = 13;
		public static final int REC_SIZE = 0x38;
		
		public int scne_idx;
		public int cdf_idx;
		
		public int aw_idx;
		public int sound_id;
		public int[] unk_words;
		
		public int read_ptr;
		
 		public CDFRecord()
		{
			aw_idx = -1;
			sound_id = -1;
			unk_words = new int[13];
			unk_words[12] = 0xFFFFFFFF;
		}
		
		public CDFRecord(FileBuffer src, long stpos, int scne_index, int cdf_index)
		{
			this();
			scne_idx = scne_index;
			cdf_idx = cdf_index;
			long cpos = stpos;
			
			aw_idx = Short.toUnsignedInt(src.shortFromFile(cpos)); cpos+=2;
			sound_id = Short.toUnsignedInt(src.shortFromFile(cpos)); cpos+=2;
			
			for(int i = 0; i < UNK_WORDS; i++)
			{
				unk_words[i] = src.intFromFile(cpos); cpos+=4;
			}
		}
		
		public FileBuffer serializeMe()
		{
			FileBuffer rec = new FileBuffer(REC_SIZE, true);
			rec.addToFile((short)aw_idx);
			rec.addToFile((short)sound_id);
			
			for(int i = 0; i < UNK_WORDS; i++) rec.addToFile(unk_words[i]);
			
			return rec;
		}
		
		public boolean isEmpty()
		{
			return (aw_idx < 0);
		}
		
	}
	
	public static class SCNE
	{
		public static final int CEX_SIZE = 0x20;
		public static final int CST_SIZE = 0x20;
		
		private boolean used_flag;
		
		public int scne_idx;
		
		public int read_ptr;
		public int cdf_ptr;
		public int cex_ptr;
		public int cst_ptr;
		
		public List<CDFRecord> cdf;
		public byte[] cex;
		public byte[] cst;
		
		public SCNE()
		{
			used_flag = false;
			cdf = new ArrayList<CDFRecord>(4);
			cex = new byte[CEX_SIZE];
			cst = new byte[CST_SIZE];
		}
		
		public SCNE(int init_cdf_count)
		{
			used_flag = true;
			cdf = new ArrayList<CDFRecord>(init_cdf_count);
			cex = new byte[CEX_SIZE];
			cst = new byte[CST_SIZE];
		}
		
		public SCNE(FileBuffer src, long wsys_off, long scne_off, int index) throws UnsupportedFileTypeException
		{
			long cpos = src.findString(scne_off, scne_off+0x10, MAGIC_SCNE);
			if(cpos != scne_off) throw new FileBuffer.UnsupportedFileTypeException("GC_WSYS.SCNE || SCNE Chunk Magic not found! Offset = 0x" + Long.toHexString(scne_off));
			cpos += 4; //Skip back over magic
			cpos += 8; //Seem to be all zeroes
			
			scne_idx = index;
			read_ptr = (int)(scne_off - wsys_off);
			
			//C-DF
			int ptr = src.intFromFile(cpos); cpos += 4;
			cdf_ptr = ptr;
			long cdf_off = wsys_off + Integer.toUnsignedLong(ptr);
			cdf_off = src.findString(cdf_off, cdf_off+0x10, MAGIC_CDF);
			if(cdf_off < 0) throw new FileBuffer.UnsupportedFileTypeException("GC_WSYS.SCNE || C-DF Chunk Magic not found! Offset = 0x" + Long.toHexString(cdf_off));
			cdf_off += 4; //Skip back over magic
			int r_count = src.intFromFile(cdf_off); cdf_off+=4;
			cdf = new ArrayList<CDFRecord>(r_count+1);
			for(int i = 0; i < r_count; i++)
			{
				ptr = src.intFromFile(cdf_off); cdf_off+=4;
				if(ptr == 0)
				{
					CDFRecord r = new CDFRecord();
					cdf.add(r);
					continue;
				}
				long rpos = wsys_off + Integer.toUnsignedLong(ptr);
				CDFRecord r = new CDFRecord(src, rpos, scne_idx, i);
				cdf.add(r);
				r.read_ptr = ptr;
			}
			
			//C-EX
			cex = new byte[CEX_SIZE];
			ptr = src.intFromFile(cpos); cpos += 4;
			cex_ptr = ptr;
			long cex_off = wsys_off + Integer.toUnsignedLong(ptr);
			cex_off = src.findString(cex_off, cex_off+0x10, MAGIC_CEX);
			if(cdf_off < 0) throw new FileBuffer.UnsupportedFileTypeException("GC_WSYS.SCNE || C-EX Chunk Magic not found! Offset = 0x" + Long.toHexString(cex_off));
			for(int i = 0; i < CEX_SIZE; i++) cex[i] = src.getByte(cex_off+i);
			
			
			//C-ST
			cst = new byte[CST_SIZE];
			ptr = src.intFromFile(cpos); cpos += 4;
			cst_ptr = ptr;
			long cst_off = wsys_off + Integer.toUnsignedLong(ptr);
			cst_off = src.findString(cst_off, cst_off+0x10, MAGIC_CST);
			if(cdf_off < 0) throw new FileBuffer.UnsupportedFileTypeException("GC_WSYS.SCNE || C-ST Chunk Magic not found! Offset = 0x" + Long.toHexString(cst_off));
			for(int i = 0; i < CST_SIZE; i++) cst[i] = src.getByte(cst_off+i);
			
			used_flag = true;
		}
		
		public boolean isEmpty()
		{
			return !used_flag;
		}
	
		public boolean isCEXEmpty()
		{
			if (cex == null || cex.length < 4) return true;
			for(int i = 4; i < cex.length; i++)
			{
				if(cex[i] != 0) return false;
			}
			return true;
		}
		
		public boolean isCSTEmpty()
		{
			if (cst == null || cst.length < 4) return true;
			for(int i = 4; i < cst.length; i++)
			{
				if(cst[i] != 0) return false;
			}
			return true;
		}
	
		public void cover(GC_WSYS wsys)
		{
			wsys.cover(read_ptr, 24, 0x08);
			
			int c_count = cdf.size();
			int cdf_size = 8 + (c_count * 4);
			wsys.cover(cdf_ptr, cdf_size, 0x04);
			for(CDFRecord r : cdf)
			{
				wsys.cover(r.read_ptr, 4*14, 0x40);
			}
			
			wsys.cover(cex_ptr, CEX_SIZE, 0x02);
			wsys.cover(cst_ptr, CST_SIZE, 0x01);
		}
		
		
	}
	
	/*--- Construction ---*/
	
	private GC_WSYS()
	{
		try{file_path = FileBuffer.generateTemporaryPath("gcwsys");}
		catch(Exception e){file_path = "." + File.separator + "gcwsys.wsys";}
		
		wsys_id = -1;
		
	}
	
	public static GC_WSYS readWSYS(String filepath, long offset) throws IOException, UnsupportedFileTypeException
	{
		FileBuffer file = FileBuffer.createBuffer(filepath, true);
		return readWSYS(file, offset);
	}
	
	public static GC_WSYS readWSYS(FileBuffer file, long offset) throws UnsupportedFileTypeException
	{
		long cpos = offset;
		cpos = file.findString(cpos, cpos+0x10, MAGIC_WSYS);
		if(cpos < 0) throw new FileBuffer.UnsupportedFileTypeException("GC_WSYS.readWSYS || WSYS Magic not found! Offset: 0x" + Long.toHexString(offset));
		
		long stoff = cpos;
		cpos += 4; //Skip back over "WSYS"
		cpos += 4; //Skip size
		//int size = file.intFromFile(cpos); cpos+=4;
		
		GC_WSYS wsys = new GC_WSYS();
		wsys.file_path = file.getPath();
		wsys.wsys_id = file.intFromFile(cpos); cpos += 4;
		wsys.sound_count = file.intFromFile(cpos); cpos += 4; //Note: This field is always zero in WW for some reason
		//wsys.coverage = new FileBuffer(size, true);
		//for(int i = 0; i < size; i++) wsys.coverage.addToFile(ZERO);
		//wsys.cover(0, 0x20, 0x80);
		
		int winf_ptr = file.intFromFile(cpos); cpos += 4;
		int wbct_ptr = file.intFromFile(cpos); cpos += 4;
		
		//WINF
		long winf_off = stoff + Integer.toUnsignedLong(winf_ptr);
		winf_off = file.findString(winf_off, winf_off+0x10, MAGIC_WINF);
		if(winf_off < 0) throw new FileBuffer.UnsupportedFileTypeException("GC_WSYS.readWSYS || WINF Magic not found! Offset: 0x" + Long.toHexString(winf_off));
		winf_off += 4;
		int aw_count = file.intFromFile(winf_off); winf_off+=4;
		wsys.winf = new ArrayList<AWRecord>(aw_count+1);
		//wsys.cover(winf_ptr, 8+(4*aw_count), 0x80);
		for(int i = 0; i < aw_count; i++)
		{
			int ptr = file.intFromFile(winf_off); winf_off+=4;
			if(ptr == 0)
			{
				wsys.winf.add(new AWRecord());
				continue;
			}
			long aw_off = stoff + Integer.toUnsignedLong(ptr);
			AWRecord rec = new AWRecord(file, stoff, aw_off);
			wsys.winf.add(rec);
			//rec.cover(wsys);
		}
		
		//WBCT
		long wbct_off = stoff + Integer.toUnsignedLong(wbct_ptr);
		wbct_off = file.findString(wbct_off, wbct_off+0x10, MAGIC_WBCT);
		if(wbct_off < 0) throw new FileBuffer.UnsupportedFileTypeException("GC_WSYS.readWSYS || WBCT Magic not found! Offset: 0x" + Long.toHexString(wbct_off));
		wbct_off += 4; //Skip over magic again
		wbct_off += 4; //This always seems to be 0xffffffff ?
		int scne_count = file.intFromFile(wbct_off); wbct_off+=4;
		wsys.wbct = new ArrayList<SCNE>(scne_count+1);
		//wsys.cover(wbct_ptr, 12+(4*scne_count), 0x80);
		for(int i = 0; i < scne_count; i++)
		{
			int ptr = file.intFromFile(wbct_off); wbct_off+=4;
			if(ptr == 0)
			{
				wsys.wbct.add(new SCNE());
			}
			long scne_off = stoff + Integer.toUnsignedLong(ptr);
			SCNE rec = new SCNE(file, stoff, scne_off, i);
			wsys.wbct.add(rec);
			//rec.cover(wsys);
			
			//Map
			/*for(CDFRecord r : rec.cdf)
			{
				System.err.println("Sound ID: " + r.sound_id);
				if(wsys.soundMap.containsKey(r.sound_id)) throw new FileBuffer.UnsupportedFileTypeException("GC_WSYS.readWSYS || Conflicting sound IDs found! Couldn't add CDF Rec: " + r.scne_idx + "-" + r.cdf_idx + " (SID: " + r.sound_id + ")");
				wsys.soundMap.put(r.sound_id, r);
			}*/
		}
		
		return wsys;
	}
	
	/*--- Getters ---*/
	
	public int getWSYSID()
	{
		return this.wsys_id;
	}
	
	public int getSoundCount()
	{
		return this.sound_count;
	}
	
	public List<SCNE> getSCNE_withNonEmptyCEX()
	{
		List<SCNE> list = new LinkedList<SCNE>();
		for(SCNE s : wbct)
		{
			if(!s.isCEXEmpty()) list.add(s);
		}
		return list;
	}
	
	public List<SCNE> getSCNE_withNonEmptyCST()
	{
		List<SCNE> list = new LinkedList<SCNE>();
		for(SCNE s : wbct)
		{
			if(!s.isCSTEmpty()) list.add(s);
		}
		return list;
	}
	
	/*--- Setters ---*/
	
	/*--- Parsers ---*/
	
	/*--- Serialization ---*/
	
	/*--- Coverage ---*/
	
	public void cover(int start, int len, int flag)
	{
		for(int i = 0; i < len; i++)
		{
			//int b = Byte.toUnsignedInt(coverage.getByte(start+i));
			//b |= flag;
			//coverage.replaceByte((byte)b, start+i);
		}
	}
	
	public void writeCoverageFile(String filepath) throws IOException
	{
		//coverage.writeFile(filepath);
	}
	
	/*--- Info ---*/
	
	public void printInfo(BufferedWriter bw) throws IOException
	{
		bw.write("====== WSYS Info ======\n");
		bw.write("File Path: " + file_path + "\n");
		bw.write("wsys ID: " + wsys_id + "\n");
		
		bw.write("\n------ Wave Info (WINF) ------\n");
		int aw_count = winf.size();
		bw.write("AW Count: " + aw_count + "\n");
		for(int i = 0; i < aw_count; i++)
		{
			AWRecord r = winf.get(i);
			bw.write("-> AW Record " + i + "\n");
			if(r.isEmpty())
			{
				bw.write("\t(Empty)\n");
				continue;
			}
			bw.write("\tName: " + r.fileName + "\n");
			int w_count = r.waves.size();
			bw.write("\tWave Count: " + w_count + "\n");
			for(int j = 0; j < w_count; j++)
			{
				WaveRecord rec = r.waves.get(j);
				bw.write("\t->Wave Record " + j + "\n");
				if(rec.isEmpty())
				{
					bw.write("\t\t(Empty)\n");
					continue;
				}
				bw.write("\t\tType: 0x" + String.format("%02x", rec.type) + "\n");
				bw.write("\t\tUnity Key: 0x" + String.format("%02x", rec.unityKey) + "\n");
				bw.write("\t\tUnk Flags 1: 0x" + String.format("%02x", rec.flags_1) + "\n");
				bw.write("\t\tUnk Flags 2: 0x" + String.format("%03x", rec.flags_2) + "\n");
				bw.write("\t\tSample Rate: " + rec.sampleRate + "\n");
				bw.write("\t\tOffset: 0x" + Integer.toHexString(rec.offset) + "\n");
				bw.write("\t\tSize: 0x" + Integer.toHexString(rec.size) + "\n");
				bw.write("\t\tLoop Flag: " + rec.loop_flag + "\n");
				bw.write("\t\tLoop Start: " + rec.loop_start + "\n");
				bw.write("\t\tLoop End: " + rec.loop_end + "\n");
				bw.write("\t\tUnparsed Data: ");
				for(int k = 0; k < rec.unknown.length; k++) bw.write(String.format("%02x ", rec.unknown[k]));
				bw.write("\n");
			}
		}
		
		bw.write("\n------ WBCT Data ------\n");
		int s_count = wbct.size();
		bw.write("SCNE Count: " + s_count + "\n");
		for(int i = 0; i < s_count; i++)
		{
			SCNE r = wbct.get(i);
			bw.write("-> SCNE " + i + "\n");
			if(r.isEmpty())
			{
				bw.write("\t(Empty)\n");
				continue;
			}
			int cdf_ct = r.cdf.size();
			bw.write("\tC-DF ~~~~\n");
			bw.write("\tRecord Cound: " + cdf_ct + "\n");
			for(int j = 0; j < cdf_ct; j++)
			{
				CDFRecord rec = r.cdf.get(j);
				bw.write("\t-> C-DF Record " + j + "\n");
				if(rec.isEmpty())
				{
					bw.write("\t\t(Empty)\n");
					continue;
				}
				bw.write("\t\tAW Index: " + rec.aw_idx + "\n");
				bw.write("\t\tSound ID: " + rec.sound_id + " (0x" + Integer.toHexString(rec.sound_id) + ")\n");
				/*bw.write("\t\tUnparsed Data: \n");
				for(int k = 0; k < 3; k++)
				{
					bw.write("\t\t\t");
					for(int l = 0; l < 4; l++)
					{
						int idx = (k*4) + l;
						bw.write(String.format("%08x", rec.unk_words[idx]) + " ");
					}
					bw.write("\n");
				}
				bw.write("\t\t\t" + String.format("%08x", rec.unk_words[12]) + "\n");*/
			}
			
			bw.write("\tC-EX ~~~~\n");
			//See if empty
			boolean e = true;
			for(int j = 4; j < SCNE.CEX_SIZE; j++)
			{
				if(r.cex[j] != 0)
				{
					e = false;
					break;
				}
			}
			if(e) bw.write("\t\t(Empty)\n");
			else
			{
				//Copy
				int j = 0;
				while(j < r.cex.length)
				{
					if(j % 8 == 0) bw.write("\t\t");
					bw.write(String.format("%02x ", r.cex[j]));
					j++;
					if(j % 8 == 0) bw.write("\n");
				}
			}
			
			bw.write("\tC-ST ~~~~\n");
			e = true;
			for(int j = 4; j < SCNE.CST_SIZE; j++)
			{
				if(r.cst[j] != 0)
				{
					e = false;
					break;
				}
			}
			if(e) bw.write("\t\t(Empty)\n");
			else
			{
				//Copy
				int j = 0;
				while(j < r.cst.length)
				{
					if(j % 8 == 0) bw.write("\t\t");
					bw.write(String.format("%02x ", r.cst[j]));
					j++;
					if(j % 8 == 0) bw.write("\n");
				}
			}
		}
		
	}

	public void testprint()
	{
		int n = winf.size();
		if(wbct.size() > winf.size()) n = wbct.size();
		
		System.out.println("AW Count: " + winf.size());
		System.out.println("SCNE Count: " + wbct.size());
		
		int mismatched = 0;
		int r_id_ct = 0;
		int w_tot = 0;
		int c_tot = 0;
		
		for(int i = 0; i < n; i++)
		{
			System.out.print(String.format("%03d: ", i));
			AWRecord aw = winf.get(i);
			SCNE scne = wbct.get(i);
			Set<Integer> s_ids = new HashSet<Integer>();
			
			if(aw == null) System.out.print("AW - (null), ");
			else System.out.print("AW - " + aw.waves.size() + " waves, ");
			
			if(scne == null) System.out.print("SCNE - (null), ");
			else System.out.print("SCNE - " + scne.cdf.size() + " records, ");
			if(aw.waves.size() != scne.cdf.size()) mismatched++;
			w_tot += aw.waves.size();
			c_tot += scne.cdf.size();
			
			for(CDFRecord r : scne.cdf) s_ids.add(r.sound_id);
			System.out.print("Unique Sound IDs: " + s_ids.size() + "\n");
			if(s_ids.size() != scne.cdf.size()) r_id_ct++;
		}
		
		System.out.println("\nMismatch Count: " + mismatched);
		System.out.println("SCNEs with repeated sound IDs: " + r_id_ct);
		System.out.println("Total Waves: " + w_tot);
		System.out.println("Total C-DFs: " + c_tot);
	}
	
}
