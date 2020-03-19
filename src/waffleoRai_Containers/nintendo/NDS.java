package waffleoRai_Containers.nintendo;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import waffleoRai_Encryption.AES;
import waffleoRai_Files.EncryptionDefinition;
import waffleoRai_Image.Palette;
import waffleoRai_Image.PaletteRaster;
import waffleoRai_Image.Picture;
import waffleoRai_Image.nintendo.NDSGraphics;
import waffleoRai_Utils.DirectoryNode;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileNode;
import waffleoRai_Utils.StreamWrapper;

public class NDS {
	
	//https://dsibrew.org/wiki/DSi_Cartridge_Header
	//https://dsibrew.org/wiki/Icon.bin

	/*----- Constants -----*/
	
	public static final int TITLE_LANGUAGE_JAPANESE = 0;
	public static final int TITLE_LANGUAGE_ENGLISH = 1;
	public static final int TITLE_LANGUAGE_FRENCH = 2;
	public static final int TITLE_LANGUAGE_GERMAN = 3;
	public static final int TITLE_LANGUAGE_ITALIAN = 4;
	public static final int TITLE_LANGUAGE_SPANISH = 5;
	public static final int TITLE_LANGUAGE_CHINESE = 6;
	public static final int TITLE_LANGUAGE_KOREAN = 7;
	
	public static final String BANNER_ENCODING = "UnicodeLittleUnmarked";
	
	/*----- Instance Variables -----*/
	
	//
	private boolean temp_file;
	private String rom_path;
	
	//Header Junk - NTR
	private String game_name;
	private String game_code;
	private short maker_code;
	
	private boolean ds_flag;
	private boolean dsi_flag;
	
	private byte seed_select;
	private byte device_capacity;
	
	private short game_revision;
	private byte rom_version;
	private int flags;
	private long banner_offset;
	
	private int regset_normal;
	private int regset_secure;
	private short secure_crc;
	private short secure_timeout;
	private long secure_disable;
	
	private int arm9_autoload;
	private int arm7_autoload;
	
	private FileBuffer nintendo_logo;
	
	//Header - TWL
	private int[][] mbk_settings; //0 is ARM9, 1 is ARM7
	private int regionFlags; //Dunno how to read.
	private int access_control;
	private int arm7_ext_mask;
	private int twl_flags;
	private long total_rom_size;
	
	private long modcrypt1_off;
	private long modcrypt1_size;
	private long modcrypt2_off;
	private long modcrypt2_size;
	
	private String titleID_dsi;
	private long pubsav_size;
	private long privsav_size;
	
	//TWL Hashes (SHA1 HMAC)
	private byte[] hash_arm9_sec;
	private byte[] hash_arm9_nosec;
	private byte[] hash_arm7;
	private byte[] hash_digestmaster;
	private byte[] hash_banner;
	private byte[] hash_arm9i_dec;
	private byte[] hash_arm7i_dec;
	
	//Data - NTR
	private DSExeData arm9;
	private FileBuffer arm9_overlay;
	private DSExeData arm7;
	private FileBuffer arm7_overlay;
	
	private FileBuffer icon_banner;
	private long ntr_rom_size;
	private long header_size;
	
	//Data - TWL
	private DSExeData arm9i; //Should be at least partially encrypted
	private DSExeData arm7i; //Encrypted for some apps
	private FileBuffer rsa; //Only in DSi
	
	//File System
	private DirectoryNode root;
	private List<FileNode> raw_files;
	
	/*----- SubStructs -----*/
	
	public static class DSExeData
	{
		private long offset;
		private long size;
		
		private long loadAddr;
		private long entryAddr;
		
		private long passAddr;
		
		private FileBuffer data;
		
		public long getROMOffset(){return offset;}
		public long getSize(){return size;}
		public long getLoadAddress(){return loadAddr;}
		public long getEntryAddress(){return entryAddr;}
		public long getPassAddress(){return passAddr;}
		public FileBuffer getData(){return data;}
		
		public void setROMOffset(long addr){offset = addr;}
		public void setLoadAddress(long addr){loadAddr = addr;}
		public void setEntryAddress(long addr){entryAddr = addr;}
		public void setPassAddress(long addr){passAddr = addr;}
		public void setData(FileBuffer rawData)
		{
			data = rawData;
			if(rawData != null) size = rawData.getFileSize();
			else size = 0;
		}
		
		public FileNode addFileNode(DirectoryNode parent)
		{
			FileNode fn = new FileNode(parent, "");
			fn.setOffset(offset);
			fn.setLength(size);
			return fn;
		}
		
	}
	
	protected static class BranchNode
	{
		private int index;
		
		private int fnt_offset;
		private int fat_start_index;
		
		private int val1; //I think # children for root, parent idx for other?
		private byte root_flag; //????
		
		private List<FNTNode> children;
		
		public BranchNode()
		{
			children = new LinkedList<FNTNode>();
		}
		
		public void reallocateChildrenToArray()
		{
			ArrayList<FNTNode> list = new ArrayList<FNTNode>(children.size() + 1);
			list.addAll(children);
			children = list;
		}
		
		public void printMeToErr(int indent)
		{
			StringBuilder sb = new StringBuilder(256);
			for(int i = 0; i < indent; i++) sb.append("\t");
			String tabs = sb.toString();
			
			System.err.println(tabs + ">>BRANCH 0x" + Integer.toHexString(index));
			System.err.println(tabs + "(FAT Start Idx: 0x" + Integer.toHexString(fat_start_index) + ")");
			System.err.println(tabs + "(Mystery Fields: " + String.format("%02x %02x", val1, root_flag) + ")");
			
			for(FNTNode child : children) child.printMeToErr(indent + 1);
		}
	
		public List<FNTNode> getDirectories()
		{
			List<FNTNode> list = new LinkedList<FNTNode>();
			for(FNTNode child : children)
			{
				if(child.isDir) list.add(child);
			}
			return list;
		}
		
	}
	
	protected static class FNTNode
	{
		private String name;
		
		private int fat_idx;
		
		private boolean isDir;
		private int branch_idx;
		private byte root_flag; //??????
		
		//private BranchNode parent;
		
		public void printMeToErr(int indent)
		{
			StringBuilder sb = new StringBuilder(256);
			for(int i = 0; i < indent; i++) sb.append("\t");
			String tabs = sb.toString();
			
			if(isDir)
			{
				String v1 = String.format("%02x", branch_idx);
				String v2 = String.format("%02x", root_flag);
				System.err.println(tabs + "->[<DIR>" + v1 + "," + v2 + "]" + name);
			}
			else System.err.println(tabs + "->[0x" + Integer.toHexString(fat_idx) + "] " + name);
		}
	}
	
	/*----- Construction -----*/
	
	private NDS()
	{
		raw_files = new LinkedList<FileNode>();
	}
	
	private void allocateHashArrays()
	{
		this.hash_arm7 = new byte[20];
		this.hash_arm7i_dec = new byte[20];
		this.hash_arm9_nosec = new byte[20];
		this.hash_arm9_sec = new byte[20];
		this.hash_arm9i_dec = new byte[20];
		this.hash_banner = new byte[20];
		this.hash_digestmaster = new byte[20];
	}
	
	private static void readHash(byte[] arr, FileBuffer in, long cpos)
	{
		for(int i = 0; i < 20; i++)
		{
			arr[i] = in.getByte(cpos); cpos++;
		}
	}
	
	/*----- Parsing -----*/
	
	public static NDS readROM(InputStream in) throws IOException
	{
		//This one will copy it to local drive
		
		Random r = new Random();
		String temppath = FileBuffer.getTempDir() + File.separator + "dsrom_" + Long.toHexString(r.nextLong()) + ".nds";
		
		BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(temppath));
		int b = -1;
		while((b = in.read()) != -1) bos.write(b);
		bos.close();
		
		return readROM(temppath, 0, true);
	}
	
	public static NDS readROM(String filepath, long stpos) throws IOException
	{
		return readROM(filepath, stpos, false);
	}
	
	private static NDS readROM(String filepath, long stpos, boolean temp) throws IOException
	{
		FileBuffer file = FileBuffer.createBuffer(filepath, false);
		NDS image = new NDS();
		image.temp_file = temp;
		image.rom_path = filepath;
		
		long cpos = stpos;
		
		//Initial header
		image.game_name = file.getASCII_string(cpos, 12); cpos += 12;
		image.game_code = file.getASCII_string(cpos, 4); cpos += 4;
		image.maker_code = file.shortFromFile(cpos); cpos+=2;
		int flags = Byte.toUnsignedInt(file.getByte(cpos)); cpos++;
		if((flags & 0x1) == 0)image.ds_flag = true;
		if((flags & 0x2) != 0)image.dsi_flag = true;
		image.seed_select = file.getByte(cpos); cpos++;
		image.device_capacity = file.getByte(cpos); cpos++;
		cpos += 7; //Reserved
		image.game_revision = file.shortFromFile(cpos); cpos+=2;
		image.rom_version = file.getByte(cpos); cpos++;
		image.flags = Byte.toUnsignedInt(file.getByte(cpos)); cpos++;
		
		//The offsets of the sub-ROMs...
		long arm9_offset = Integer.toUnsignedLong(file.intFromFile(cpos)); cpos+=4;
		long arm9_entry = Integer.toUnsignedLong(file.intFromFile(cpos)); cpos+=4;
		long arm9_load = Integer.toUnsignedLong(file.intFromFile(cpos)); cpos+=4;
		long arm9_size = Integer.toUnsignedLong(file.intFromFile(cpos)); cpos+=4;
		
		long arm7_offset = Integer.toUnsignedLong(file.intFromFile(cpos)); cpos+=4;
		long arm7_entry = Integer.toUnsignedLong(file.intFromFile(cpos)); cpos+=4;
		long arm7_load = Integer.toUnsignedLong(file.intFromFile(cpos)); cpos+=4;
		long arm7_size = Integer.toUnsignedLong(file.intFromFile(cpos)); cpos+=4;
		
		long fnt_offset = Integer.toUnsignedLong(file.intFromFile(cpos)); cpos+=4;
		long fnt_size = Integer.toUnsignedLong(file.intFromFile(cpos)); cpos+=4;
		
		long fat_offset = Integer.toUnsignedLong(file.intFromFile(cpos)); cpos+=4;
		long fat_size = Integer.toUnsignedLong(file.intFromFile(cpos)); cpos+=4;
		
		long arm9over_offset = Integer.toUnsignedLong(file.intFromFile(cpos)); cpos+=4;
		long arm9over_size = Integer.toUnsignedLong(file.intFromFile(cpos)); cpos+=4;
		
		long arm7over_offset = Integer.toUnsignedLong(file.intFromFile(cpos)); cpos+=4;
		long arm7over_size = Integer.toUnsignedLong(file.intFromFile(cpos)); cpos+=4;
		
		image.regset_normal = file.intFromFile(cpos); cpos+=4;
		image.regset_secure = file.intFromFile(cpos); cpos+=4;
		
		long icon_banner_offset = Integer.toUnsignedLong(file.intFromFile(cpos)); cpos+=4;
		
		image.secure_crc = file.shortFromFile(cpos); cpos+=2; //Checksum
		image.secure_timeout = file.shortFromFile(cpos); cpos+=2;
		
		image.arm9_autoload = file.intFromFile(cpos); cpos += 4;
		image.arm7_autoload = file.intFromFile(cpos); cpos += 4;
		image.secure_disable = file.longFromFile(cpos); cpos += 8;
		
		image.ntr_rom_size = Integer.toUnsignedLong(file.intFromFile(cpos)); cpos+=4;
		image.header_size = Integer.toUnsignedLong(file.intFromFile(cpos)); cpos+=4;
		
		//Debug
		
		/*System.err.println("ARM9 ROM: 0x" + Long.toHexString(arm9_offset) + " - 0x" + Long.toHexString(arm9_offset + arm9_size));
		System.err.println("ARM7 ROM: 0x" + Long.toHexString(arm7_offset) + " - 0x" + Long.toHexString(arm7_offset + arm7_size));
		System.err.println("FNT: 0x" + Long.toHexString(fnt_offset) + " - 0x" + Long.toHexString(fnt_offset + fnt_size));
		System.err.println("FAT: 0x" + Long.toHexString(fat_offset) + " - 0x" + Long.toHexString(fat_offset + fat_size));
		System.err.println("ARM9 Overlay: 0x" + Long.toHexString(arm9over_offset) + " - 0x" + Long.toHexString(arm9over_offset + arm9over_size));
		System.err.println("ARM7 Overlay: 0x" + Long.toHexString(arm7over_offset) + " - 0x" + Long.toHexString(arm7over_offset + arm7over_size));
		System.err.println("Icon Banner Offset: 0x" + Long.toHexString(icon_banner_offset));
		System.err.println("NTR ROM Size: 0x" + Long.toHexString(ntr_rom_size));
		System.err.println("Header Size: 0x" + Long.toHexString(header_size));*/
		
		cpos += 56;
		image.nintendo_logo = file.createCopy(cpos, cpos+156); cpos += 156;
		//Won't bother with checksums...
		
		//Rip the ARM9 and ARM7 data
		image.arm9 = new DSExeData();
		image.arm9.offset = arm9_offset;
		image.arm9.size = arm9_size;
		image.arm9.loadAddr = arm9_load;
		image.arm9.entryAddr = arm9_entry;
		image.arm9.data = file.createCopy(arm9_offset, arm9_offset + arm9_size);
		image.arm9_overlay = file.createCopy(arm9over_offset, arm9over_offset + arm9over_size);
		
		image.arm7 = new DSExeData();
		image.arm7.offset = arm7_offset;
		image.arm7.size = arm7_size;
		image.arm7.loadAddr = arm7_load;
		image.arm7.entryAddr = arm7_entry;
		image.arm7.data = file.createCopy(arm7_offset, arm7_offset + arm7_size);
		if(arm7over_size > 0) image.arm7_overlay = file.createCopy(arm7over_offset, arm7over_offset + arm7over_size);
		
		//Read the file name table
		List<BranchNode> fnt = parseFNT(file.createReadOnlyCopy(fnt_offset, fnt_offset + fnt_size));
		
		//Read the FAT???
		long[][] fat = parseFAT(file.createReadOnlyCopy(fat_offset, fat_offset + fat_size));
		
		//Combine the FNT and FAT to create file system tree
		image.root = buildFileTree(fnt, fat, filepath);
		
		//Save data referenced by FAT nodes outside file tree...
		Map<Integer, FNTNode> nmap = new HashMap<Integer, FNTNode>();
		for(BranchNode branch : fnt)
		{
			for(FNTNode child : branch.children)
			{
				if(!child.isDir) nmap.put(child.fat_idx, child);
			}
		}
		for(int i = 0; i < fat.length; i++)
		{
			if(nmap.get(i) == null)
			{
				//Raw Node.
				String rawname = "NDS_RAWFILE_" + String.format("%04x", i) + ".bin";
				FileNode fn = new FileNode(null, rawname);
				image.raw_files.add(fn);
				
				fn.setOffset(fat[i][0]);
				fn.setLength(fat[i][1] - fat[i][0]);
			}
		}
		
		final long BANNER_SIZE_NOANIM = 0x840;
		long banner_size = BANNER_SIZE_NOANIM;
		
		//TWL header & data...
		if(image.hasTWL())
		{
			cpos = 0x180;
			image.mbk_settings = new int[10][2];
			for(int i = 1; i <= 5; i++)
			{
				int val = file.intFromFile(cpos); cpos+=4;
				image.mbk_settings[i][0] = val;
				image.mbk_settings[i][1] = val;
			}
			for(int i = 6; i <= 8; i++) {image.mbk_settings[i][0] = file.intFromFile(cpos);cpos+=4;}
			for(int i = 6; i <= 8; i++) {image.mbk_settings[i][1] = file.intFromFile(cpos);cpos+=4;}
			int val = file.intFromFile(cpos); cpos+=4;
			image.mbk_settings[9][0] = val;
			image.mbk_settings[9][1] = val;
			image.regionFlags = file.intFromFile(cpos); cpos+=4;
			image.access_control = file.intFromFile(cpos); cpos+=4;
			image.arm7_ext_mask = file.intFromFile(cpos); cpos+=4;
			image.twl_flags = file.intFromFile(cpos); cpos+=4;
			
			//The ARM9i and ARM7i data...
			image.arm9i = new DSExeData();
			image.arm9i.offset = Integer.toUnsignedLong(file.intFromFile(cpos)); cpos+=4;
			image.arm9i.passAddr = Integer.toUnsignedLong(file.intFromFile(cpos)); cpos+=4;
			image.arm9i.loadAddr = Integer.toUnsignedLong(file.intFromFile(cpos)); cpos+=4;
			image.arm9i.size = Integer.toUnsignedLong(file.intFromFile(cpos)); cpos+=4;
			image.arm9i.entryAddr = image.arm9i.loadAddr;
			image.arm9i.data = file.createCopy(image.arm9i.offset, image.arm9i.offset+image.arm9i.size);
			
			image.arm7i = new DSExeData();
			image.arm7i.offset = Integer.toUnsignedLong(file.intFromFile(cpos)); cpos+=4;
			image.arm7i.passAddr = Integer.toUnsignedLong(file.intFromFile(cpos)); cpos+=4;
			image.arm7i.loadAddr = Integer.toUnsignedLong(file.intFromFile(cpos)); cpos+=4;
			image.arm7i.size = Integer.toUnsignedLong(file.intFromFile(cpos)); cpos+=4;
			image.arm7i.entryAddr = image.arm7i.loadAddr;
			image.arm7i.data = file.createCopy(image.arm7i.offset, image.arm7i.offset+image.arm7i.size);
		
			//I'm just gonna skip down to icon banner size...
			cpos = 0x208;
			banner_size = Integer.toUnsignedLong(file.intFromFile(cpos)); cpos+=4;
			cpos += 4;
			image.total_rom_size = Integer.toUnsignedLong(file.intFromFile(cpos)); cpos+=4;
			cpos+=12;
			
			//Modcrypt offsets...
			image.modcrypt1_off = Integer.toUnsignedLong(file.intFromFile(cpos)); cpos+=4;
			image.modcrypt1_size = Integer.toUnsignedLong(file.intFromFile(cpos)); cpos+=4;
			image.modcrypt2_off = Integer.toUnsignedLong(file.intFromFile(cpos)); cpos+=4;
			image.modcrypt2_size = Integer.toUnsignedLong(file.intFromFile(cpos)); cpos+=4;
			
			image.titleID_dsi = file.getASCII_string(cpos, 8); cpos+=8;
			image.pubsav_size = Integer.toUnsignedLong(file.intFromFile(cpos)); cpos+=4;
			image.privsav_size = Integer.toUnsignedLong(file.intFromFile(cpos)); cpos+=4;
			
			//Hash fun
			cpos = 0x300;
			image.allocateHashArrays();
			NDS.readHash(image.hash_arm9_sec, file, cpos); cpos+=20;
			NDS.readHash(image.hash_arm7, file, cpos); cpos+=20;
			NDS.readHash(image.hash_digestmaster, file, cpos); cpos+=20;
			NDS.readHash(image.hash_banner, file, cpos); cpos+=20;
			NDS.readHash(image.hash_arm9i_dec, file, cpos); cpos+=20;
			NDS.readHash(image.hash_arm7i_dec, file, cpos); cpos+=20;
			cpos += 40;
			NDS.readHash(image.hash_arm9_nosec, file, cpos); cpos+=20;
			
			//Copy RSA sig
			cpos = 0xF80;
			image.rsa = file.createCopy(cpos, cpos + 0x80);
		}
		else
		{
			image.total_rom_size = image.ntr_rom_size;
		}
		
		//image.printMeToErr();
		//I think the banner size is fixed?
		//final long BANNER_SIZE_NOANIM = 0x840;
		//final long BANNER_SIZE_ANIM = 0x23c0;
		image.banner_offset = icon_banner_offset;
		image.icon_banner = file.createCopy(icon_banner_offset, icon_banner_offset + banner_size);
			
		//Mark encryption
		if(image.hasModcryptRegions()) image.scanForEncryption(image.root);
		
		return image;
	}
	
	private static List<BranchNode> parseFNT(FileBuffer in)
	{
		/*
		 * Format notes:
		 * Dir Table
		 * 	Entries...
		 * 		Offset to name of first entity [4]
		 * 		Index of first FAT node [2]
		 * 		Parent (Not root) or #children (root) [1] ?????
		 * 		Flag???? [1]
		 * 			0xf0 appears to mean it's not the root, and 0x00 is root?
		 * 			I think 0x00 here might be the signal to say how many children the root node has?
		 * Name Table
		 * 	Entries...
		 * 		Flag/Strlen [1]
		 * 			If the highest bit is set, then it's a directory
		 * 			The other bits are the length of the name string
		 * 		Dir Table Idx [1] (If Dir)
		 * 		Flag [1] ??? Always seems to be 0xf0?
		 */
		
		List<BranchNode> branches = new LinkedList<BranchNode>();
		
		long cpos = 0;
		long tbl_end = Integer.toUnsignedLong(in.intFromFile(0));
		int i = 0;
		while(cpos < tbl_end)
		{
			BranchNode branch = new BranchNode();
			branch.fnt_offset = in.intFromFile(cpos); cpos += 4;
			//System.err.println("Offset: 0x" + Long.toHexString(start_off));
			branch.fat_start_index = Short.toUnsignedInt(in.shortFromFile(cpos)); cpos += 2;
			branch.val1 = in.getByte(cpos); cpos++;
			branch.root_flag = in.getByte(cpos); cpos++;
			branch.index = i;
			
			//Read file/dir names...
			long bpos = Integer.toUnsignedLong(branch.fnt_offset);
			int fidx = branch.fat_start_index;
			while(true)
			{
				//Generate child node
				FNTNode node = new FNTNode();
				//node.parent = branch;
				
				//Get string length
				int strlen = Byte.toUnsignedInt(in.getByte(bpos)); bpos++;
				if(strlen == 0) break;
				if((strlen & 0x80) != 0)
				{
					strlen &= 0x7F;
					node.isDir = true;
				}
				branch.children.add(node);
				
				//Get string
				node.name = in.getASCII_string(bpos, strlen);
				bpos += strlen;
				
				//Get fields after string (if applicable)
				if(node.isDir)
				{
					node.branch_idx = Byte.toUnsignedInt(in.getByte(bpos)); bpos++;
					node.root_flag = in.getByte(bpos); bpos++;	
				}
				else
				{
					node.fat_idx = fidx;
					fidx++;
				}
			}
			
			branch.reallocateChildrenToArray();
			branches.add(branch);
			
			i++;
		}
		
		/*
		//Debug print
		for(BranchNode b : branches) b.printMeToErr(0);
		
		//Map dir nodes...
		Map<Integer, List<FNTNode>> map = new TreeMap<Integer, List<FNTNode>>();
		for(BranchNode b : branches)
		{
			List<FNTNode> dlist = b.getDirectories();
			for(FNTNode d : dlist)
			{
				List<FNTNode> l = map.get(d.branch_idx);
				if(l == null)
				{
					l = new LinkedList<FNTNode>();
					map.put(d.branch_idx, l);
				}
				l.add(d);
			}
		}
		
		List<Integer> keyset = new ArrayList<Integer>(map.size()+1);
		keyset.addAll(map.keySet());
		Collections.sort(keyset);
		System.err.println("SORTING DIR NODES BY VALUE 1 ---------");
		for(Integer k : keyset)
		{
			System.err.println("\t--> 0x" + String.format("%03x", k));
			List<FNTNode> l = map.get(k);
			for(FNTNode d : l)
			{
				System.err.println("\t\t[0x" + String.format("%03x]", d.parent.index) + " " + d.name);
			}
		}*/
		
		return branches;
	}
	
	private static long[][] parseFAT(FileBuffer in)
	{
		long sz = in.getFileSize();
		int recs = (int)(sz >>> 3);
		long[][] fat = new long[recs][2];
		
		//System.err.println("============ FAT Printout: ============");
		
		long cpos = 0;
		for(int i = 0; i < recs; i++)
		{
			fat[i][0] = Integer.toUnsignedLong(in.intFromFile(cpos)); cpos+=4;
			fat[i][1] = Integer.toUnsignedLong(in.intFromFile(cpos)); cpos+=4;
			
			//Debug print
			/*System.err.print("[0x" + String.format("%04x", i) + "]: ");
			System.err.print("0x" + Long.toHexString(fat[i][0]) + " - 0x" + Long.toHexString(fat[i][1]));
			System.err.println();*/
		}
		
		return fat;
	}
	
	private static DirectoryNode buildFileTree(List<BranchNode> fnt, long[][] fat, String datapath)
	{
		//Dump fnt list to array (for easier random access)
		int dcount = fnt.size();
		BranchNode[] barr = new BranchNode[dcount];
		int i = 0;
		for(BranchNode b : fnt)
		{
			barr[i] = b;
			i++;
		}
		
		//Find root
		int root_branch = 0;
		for(i = 0; i < dcount; i++)
		{
			if(barr[i].root_flag == 0x00)
			{
				root_branch = i;
				break;
			}
		}
		
		//Iterate through branches and generate an empty dir node for each...
		DirectoryNode[] darr = new DirectoryNode[dcount];
		for(i = 0; i < dcount; i++) darr[i] = new DirectoryNode(null, ""); //So linking can happen
		
		//Actually go through each dir record...
		for(i = 0; i < dcount; i++)
		{
			List<FNTNode> children = barr[i].children;
			for(FNTNode child : children)
			{
				if(child.isDir)
				{
					DirectoryNode c = darr[child.branch_idx];
					c.setFileName(child.name);
					c.setParent(darr[i]);
				}
				else
				{
					FileNode c = new FileNode(darr[i], child.name);
					c.setOffset(fat[child.fat_idx][0]);
					c.setLength(fat[child.fat_idx][1] - c.getOffset());
					c.setSourcePath(datapath);
				}
			}
		}
		
		return darr[root_branch];
	}
	
	private void scanForEncryption(DirectoryNode dir)
	{
		List<FileNode> children = dir.getChildren();
		for(FileNode child : children)
		{
			if(child instanceof DirectoryNode)
			{
				scanForEncryption((DirectoryNode)child);
			}
			else
			{
				if(child.isLeaf())
				{
					if(modcryptRegion(child.getOffset(), child.getLength()) != 0)
					{
						child.setEncryption(getModcryptDef());
					}
				}
			}
		}
		
	}
	
	/*----- Serialization -----*/
	
	/*----- Getters -----*/
	
	//Header Data
	public String getLongGameCode(){return this.game_name;}
	public String getGameCode(){return this.game_code;}
	public short getMakerCode(){return this.maker_code;}
	public boolean hasNTR(){return this.ds_flag;}
	public boolean hasTWL(){return this.dsi_flag;}
	
	public long getNTRROMsize(){return this.ntr_rom_size;}
	public long getHeaderSize(){return this.header_size;}
	
	public byte getSeedEnum(){return this.seed_select;}
	public byte getDeviceCapacity(){return this.device_capacity;}
	
	public short getROMVersion(){return this.rom_version;}
	public short getGameRevision(){return this.game_revision;}
	public int getFlags(){return this.flags;}
	public int getNormalRegisterSettings(){return this.regset_normal;}
	public int getSecureRegisterSettings(){return this.regset_secure;}
	public short getSecureRegionChecksum(){return this.secure_crc;}
	public short getSecureTimeout(){return this.secure_timeout;}
	public long getSecureDisableValue(){return this.secure_disable;}
	
	public String getMakerCodeAsASCII()
	{
		int mc = Short.toUnsignedInt(maker_code);
		char c1 = (char)((mc >>> 8) & 0xFF);
		char c2 = (char)(mc & 0xFF);
		String s = "";
		s += c1;
		s += c2;
		
		return s;
	}
	
	//TWL Header Data
	public int getRegionFlags(){return this.regionFlags;}
	public int getAccessControl(){return this.access_control;}
	public int getARM7_extMask(){return this.arm7_ext_mask;}
	public int getTWLFlags(){return this.twl_flags;}
	public long getNTRTWLROMSize(){return this.total_rom_size;}
	public String getTWLTitleID(){return this.titleID_dsi;}
	public long getPublicSavSize(){return this.pubsav_size;}
	public long getPrivateSavSize(){return this.privsav_size;}
	public FileBuffer getRSACert(){return this.rsa;}
	public long getMC1Offset(){return modcrypt1_off;}
	public long getMC1Size(){return modcrypt1_size;}
	public long getMC2Offset(){return modcrypt2_off;}
	public long getMC2Size(){return modcrypt2_size;}
	
	//Exe Data
	public DSExeData getARM9_data(){return this.arm9;}
	public FileBuffer getARM9_overlay(){return this.arm9_overlay;}
	public int getARM9_autoload(){return this.arm9_autoload;}
	public DSExeData getARM7_data(){return this.arm7;}
	public FileBuffer getARM7_overlay(){return this.arm7_overlay;}
	public int getARM7_autoload(){return this.arm7_autoload;}
	public DSExeData getARM9i_data(){return this.arm9i;}
	public DSExeData getARM7i_data(){return this.arm7i;}
	
	//Other Data
	public byte[] getNintendoLogoData(){return nintendo_logo.getBytes(0, this.nintendo_logo.getFileSize());}
	public byte[] getBannerData(){return icon_banner.getBytes(0, icon_banner.getFileSize());}
	
	//Banner Parse
	public BufferedImage[] getBannerIcon()
	{
		if(modcryptRegion(banner_offset, icon_banner.getFileSize()) != 0) return null;
		
		//Check if has animation...
		boolean animated = icon_banner.getByte(1) != 0;
		
		//If has animation, read animation data...
		List<Integer> f_data = new LinkedList<Integer>();
		if(animated)
		{
			final long ANIMATION_OFFSET = 0x2340;
			long cpos = ANIMATION_OFFSET;
			for(int i = 0; i < 64; i++)
			{
				int val = Short.toUnsignedInt(icon_banner.shortFromFile(cpos)); cpos+=2;
				if(val == 0) break;
				f_data.add(val);
			}
		}
		
		//Read palettes
		Palette p_main = NDSGraphics.readPalette(icon_banner, 0x220, false);
		Palette[] p_anim = new Palette[8];
		if(animated)
		{
			long cpos = 0x2240;
			for(int i = 0; i < 8; i++)
			{
				p_anim[i] = NDSGraphics.readPalette(icon_banner, cpos, false);
				cpos += 0x20;
			}
		}
		
		//Read bitmaps
		Picture bmp_main = NDSGraphics.readBitmap_4bit(icon_banner, 0x20, 32, 32, p_main);
		Picture[] bmp_anim = new Picture[8];
		if(animated)
		{
			long cpos = 0x1240;
			for(int i = 0; i < 8; i++)
			{
				bmp_anim[i] = NDSGraphics.readBitmap_4bit(icon_banner, cpos, 32, 32, p_main);
				cpos += 512;
			}
		}
		
		//Interpret
		if(!animated)
		{
			//Just the main one
			BufferedImage[] icon = new BufferedImage[1];
			icon[0] = bmp_main.toImage();
			return icon;
		}
		else
		{
			//I dunno how many frames yet, so linked list.
			List<BufferedImage> ilist = new LinkedList<BufferedImage>();
			for(Integer aval : f_data)
			{
				boolean v_flip = (aval & 0x8000) != 0;
				boolean h_flip = (aval & 0x4000) != 0;
				int p_idx = (aval >>> 11) & 0x7;
				int b_idx = (aval >>> 8) & 0x7;
				int frames = aval & 0xFF;
				
				PaletteRaster adj = new PaletteRaster(32, 32, p_anim[p_idx]);
				//Copy data
				for(int r = 0; r < 32; r++)
				{
					for(int l = 0; l < 32; l++)
					{
						int row = r;
						if(v_flip) row = 31-r;
						int col = l;
						if(h_flip) col = 31-l;
						
						int raw_val = bmp_anim[b_idx].getRawValueAt(row, col);
						adj.setPixelAt(r, l, raw_val);
					}
				}
				
				BufferedImage frame = adj.toImage();
				for(int j = 0; j < frames; j++) ilist.add(frame);
			}
			
			//Convert to area
			int f_count = ilist.size();
			BufferedImage[] out = new BufferedImage[f_count];
			int f = 0;
			for(BufferedImage frame : ilist)
			{
				out[f] = frame;
				f++;
			}
			return out;
		}
	}
	
	public String getBannerTitle(int lan_idx)
	{
		if(modcryptRegion(banner_offset, icon_banner.getFileSize()) != 0) return "?????";
		
		final int title_len = 256;
		final long base_offset = 0x240;
		
		long offset = base_offset + (title_len * lan_idx);
		
		String s = icon_banner.readEncoded_string(BANNER_ENCODING, offset, offset+title_len);
		int end = s.indexOf('\0');
		if(end >= 0) s = s.substring(0, end);
		
		return s;
	}
	
	//File System
	public String getROMPath()
	{
		return this.rom_path;
	}
	
	public DirectoryNode getRootNode(){return this.root;}
	
	public List<FileNode> getRawNodes()
	{
		List<FileNode> copy = new ArrayList<FileNode>(this.raw_files.size() + 1);
		copy.addAll(this.raw_files);
		return copy;
	}
	
	public DirectoryNode getArchiveTree()
	{
		DirectoryNode iroot = new DirectoryNode(null, game_code);
		root.setFileName(game_code + getMakerCodeAsASCII());
		root.copyAsDir(iroot);
		
		//Do the raws...
		DirectoryNode rawdat = new DirectoryNode(iroot, "rawdat");
		for(FileNode raw : raw_files) raw.copy(rawdat);
		
		//Generate nodes for the headers/ execs?
		FileNode fn = new FileNode(iroot, "header.bin");
		fn.setSourcePath(rom_path);
		fn.setOffset(0);
		fn.setLength(header_size);
		
		fn = new FileNode(iroot, "icon.bin");
		fn.setSourcePath(rom_path);
		fn.setOffset(banner_offset);
		fn.setLength(icon_banner.getFileSize());
		
		fn = arm9.addFileNode(iroot);
		fn.setFileName("main.arm9");
		fn.setSourcePath(rom_path);
		
		fn = arm7.addFileNode(iroot);
		fn.setFileName("main.arm7");
		fn.setSourcePath(rom_path);
		
		if(hasTWL())
		{
			fn = arm9i.addFileNode(iroot);
			fn.setFileName("main.arm9i");
			fn.setSourcePath(rom_path);
			
			fn = arm7i.addFileNode(iroot);
			fn.setFileName("main.arm7i");
			fn.setSourcePath(rom_path);
			
			fn = new FileNode(iroot, "rsa.bin");
			fn.setSourcePath(rom_path);
			fn.setOffset(0xF80);
			fn.setLength(0x80);
		}
		
		return iroot;
	}
	
	/*----- Setters -----*/
	
	
	/*----- Modcrypt -----*/
	
	public int modcryptRegion(long off, long size)
	{
		//0 - Not encrypted
		//1 - Region 1
		//2 - Region 2
		if(!hasTWL()) return 0;
		
		long edoff = off + size;
		
		long ed1 = modcrypt1_off + modcrypt1_size;
		if(off <= modcrypt1_off && edoff > modcrypt1_off) return 1;
		if(off < ed1 && edoff > modcrypt1_off) return 1;
		
		long ed2 = modcrypt2_off + modcrypt2_size;
		if(off <= modcrypt2_off && edoff > modcrypt2_off) return 2;
		if(off < ed2 && edoff > modcrypt2_off) return 2;
		
		return 0;
	}
	
	public boolean hasModcryptRegion1()
	{
		return this.modcrypt1_size > 0;
	}
	
	public boolean hasModcryptRegion2()
	{
		return this.modcrypt2_size > 0;
	}
	
	public boolean hasModcryptRegions()
	{
		if(!hasTWL()) return false;
		if(modcrypt1_size > 0) return true;
		if(modcrypt2_size > 0) return true;
		return false;
	}
	
	public boolean usesSecureKey()
	{
		if(!hasModcryptRegions()) return false; //No key
		
		//0x1C, or lower byte of the DSi GameRevision
		if ((Short.toUnsignedInt(game_revision) & 0x0004) != 0) return false; //Insecure key
		
		//0x1BF - Highest bit of the TWL flags
		if((this.twl_flags & 0x80000000) != 0) return false; //Insecure key
		
		return true;
	}
	
	public byte[] getModcryptCTR1()
	{
		byte[] ctr = new byte[16];
		if(this.modcrypt1_size <= 0) return ctr;
		
		//Otherwise, it's the first 16 bytes of the ARM9 hash
		for(int i = 0; i < 16; i++) ctr[i] = hash_arm9_sec[i];
		
		return ctr;
	}
	
	public byte[] getModcryptCTR2()
	{
		byte[] ctr = new byte[16];
		if(this.modcrypt2_size <= 0) return ctr;
		
		//Otherwise, it's the first 16 bytes of the ARM7 hash
		for(int i = 0; i < 16; i++) ctr[i] = hash_arm7[i];
		
		return ctr;
	}
	
	public byte[] getInsecureKey()
	{
		byte[] key = new byte[16];
		try
		{
			key = FileBuffer.createBuffer(rom_path, 0, 0x10, false).getBytes();
		}
		catch(Exception x){x.printStackTrace(); return null;}
		
		return key;
	}
	
	public byte[][] getSecureKeyPair(byte[] dsi_common)
	{
		//TODO: Dunno if this is how it rolls. Will need to check
		byte[][] pair = new byte[2][16];
		for(int i = 0; i < 8; i++) pair[0][i] = dsi_common[i];
		for(int i = 0; i < 8; i++) pair[0][i+8] = (byte)(titleID_dsi.charAt(i));
		
		for(int i = 0; i < 16; i++) pair[1][i] = hash_arm9i_dec[i];
		
		return pair;
	}
	
	public byte[] getSecureKey(byte[] dsi_common)
	{
		//TODO Still need to look up the scramble algorithm.
		//I'm gonna check 3DBrew because DSibrew is not helpful on this one
		return null;
	}
	
	private boolean decryptSecureRegion(byte[] dsi_common, OutputStream out, long offset, long size, byte[] ctr) throws IOException
	{
		boolean use_secure = usesSecureKey();
		byte[] aeskey = null;
		if(use_secure) aeskey = getSecureKey(dsi_common);
		else aeskey = getInsecureKey();
		
		AES aes = new AES(aeskey);
		aes.setCTR();
		aes.initDecrypt(ctr);
		
		BufferedInputStream bis = new BufferedInputStream(new FileInputStream(rom_path));
		bis.skip(offset);
		
		long remaining = size;
		while(remaining > 0)
		{
			boolean last = (remaining <= 512);
			byte[] in = null;
			if(last) in = new byte[(int)remaining];
			else in = new byte[512];
			
			bis.read(in);
			out.write(aes.decryptBlock(in, last));
		}
		
		bis.close();
		return true;
	}
	
	public boolean decryptSecureRegion1(byte[] dsi_common, OutputStream out) throws IOException
	{
		if(dsi_common == null || dsi_common.length != 8) return false;
		if(out == null) return false;
		if(modcrypt1_size <= 0) return false;
		
		byte[] ctr1 = getModcryptCTR1();

		return decryptSecureRegion(dsi_common, out, modcrypt1_off, modcrypt1_size, ctr1);
	}
	
	public boolean decryptSecureRegion2(byte[] dsi_common, OutputStream out) throws IOException
	{
		if(dsi_common == null || dsi_common.length != 8) return false;
		if(out == null) return false;
		if(modcrypt2_size <= 0) return false;
		
		byte[] ctr2 = getModcryptCTR2();

		return decryptSecureRegion(dsi_common, out, modcrypt2_off, modcrypt2_size, ctr2);
	}
	
	private static ModcryptDefinition cryptodef;
	
	public static EncryptionDefinition getModcryptDef()
	{
		if(cryptodef == null) cryptodef = new ModcryptDefinition();
		return cryptodef;
	}
	
	public static class ModcryptDefinition implements EncryptionDefinition
	{

		private static final int CRYPTO_ID = 0x987b0345;
		private static final String DEFO_ENG_DESC = "Nintendo DS Modcrypt";
		
		private String str;
		
		public ModcryptDefinition()
		{
			str = DEFO_ENG_DESC;
		}
		
		public int getID(){return CRYPTO_ID;}
		
		@Override
		public String getDescription() {return str;}

		@Override
		public void setDescription(String s) {str = s;}

		@Override
		public boolean decrypt(StreamWrapper input, OutputStream output, List<byte[]> keydata) 
		{
			if(keydata == null) return false;
			if(input == null) return false;
			if(output == null) return false;
			if(keydata.size() < 2) return false;
			
			//Key, IV
			byte[] aeskey = keydata.get(0);
			
			AES aes = new AES(aeskey);
			aes.setCTR();
			aes.initDecrypt(keydata.get(1));
			
			while(!input.isEmpty())
			{
				boolean last = false;
				byte[] buffer = new byte[512];
				for(int i = 0; i < 512; i++)
				{
					if(input.isEmpty())
					{
						last = true;
						//Recopy into new buffer
						byte[] temp = new byte[i];
						for(int j = 0; j < i; j++) temp[j] = buffer[j];
						buffer = temp;
					}
					else buffer[i] = input.get();
				}
				
				try{output.write(aes.decryptBlock(buffer, last));}
				catch(IOException x){x.printStackTrace(); return false;}
			}

			return true;
		}

		@Override
		public boolean encrypt(StreamWrapper input, OutputStream stream, List<byte[]> keydata) {
			// TODO Auto-generated method stub
			return false;
		}
		
	}
	
	/*----- Other -----*/
	
 	private static void writeDirNode(DirectoryNode n, String pathstem) throws IOException
	{
		//System.err.println("Writing directory " + pathstem + " ...");
		if(!FileBuffer.directoryExists(pathstem)) Files.createDirectories(Paths.get(pathstem));
		
		List<FileNode> children = n.getChildren();
		
		for(FileNode child : children)
		{
			String path = pathstem + File.separator + child.getFileName();
			if(child.isDirectory())
			{
				writeDirNode((DirectoryNode)child, path);
			}
			else
			{
				String src = child.getSourcePath();
				long off = child.getOffset();
				long edoff = off + child.getLength();
				//System.err.println("Writing file " + path + " ...");
				FileBuffer data = FileBuffer.createBuffer(src, off, edoff, false);
				data.writeFile(path);
			}
		}
		
	}
	
	public boolean dumpToDisk(String pathstem) throws IOException
	{
		//Dump raw files...
		for(FileNode fn : raw_files)
		{
			String fpath = pathstem + File.separator + fn.getFileName();
			long edoff = fn.getOffset() + fn.getLength();
			FileBuffer data = FileBuffer.createBuffer(rom_path, fn.getOffset(), edoff);
			data.writeFile(fpath);
		}
		
		//Dump bins
		FileBuffer header = FileBuffer.createBuffer(rom_path, 0, getHeaderSize(), false);
		header.writeFile(pathstem + File.separator + "header.bin");
		DSExeData arm9 = getARM9_data();
		arm9.getData().writeFile(pathstem + File.separator + "main.arm9");
		DSExeData arm7 = getARM7_data();
		arm7.getData().writeFile(pathstem + File.separator + "main.arm7");
		
		//Dump main FS
		String rpath = pathstem + File.separator + game_code;
		writeDirNode(root, rpath);
		
		return true;
	}

	public void closeROMFileLink() throws IOException
	{
		if(this.temp_file)
		{
			Files.deleteIfExists(Paths.get(this.rom_path));
		}
	}
	
	/*----- Debug -----*/
	
	public void printMeToErr()
	{
		root.printMeToStdErr(0);
	}
	
}
