package waffleoRai_Containers.nintendo.nus;

import java.util.ArrayList;

import waffleoRai_Compression.definitions.CompDefNode;
import waffleoRai_Compression.nintendo.Yaz;
import waffleoRai_Files.FileClass;
import waffleoRai_Files.GenericSystemDef;
import waffleoRai_Files.tree.DirectoryNode;
import waffleoRai_Files.tree.FileNode;
import waffleoRai_Utils.FileBuffer;

public class N64ZFileTable {
	
	/*----- Constants -----*/
	
	public static final String FNMETAKEY_DECOMPSIZE = "DECOMPSIZE";
	public static final int DEFAULT_ALLOC_SIZE = 1024;
	protected static final long DIR_GROUP_LEN = 0x80000;
	
	/*----- Instance Variables -----*/
	
	private ArrayList<Entry> entries;
	
	/*----- Inner Classes -----*/
	
	public static class Entry{
		
		private long v_start;
		private long v_end;
		private long offset;
		
		private long comp_size;
		private long size;
		
		public Entry(){}
		
		public long getVirtualStart(){return v_start;}
		public long getVirtualEnd(){return v_end;}
		public long getROMAddress(){return offset;}
		public boolean isCompressed(){return comp_size < size;}
		public long getSizeOnROM(){return comp_size;}
		public long getSize(){return size;}
		
	}
	
	/*----- Initialization -----*/
	
	private N64ZFileTable(){this(DEFAULT_ALLOC_SIZE);}
	private N64ZFileTable(int alloc){entries = new ArrayList<Entry>(alloc);}
	
	/*----- Parsing -----*/

	public static long findTableStart(FileBuffer rom){
		//Detect ordering
		rom = N64ROMImage.adjustByteOrderToZ64(rom);
		
		//Doing what Aegh decompressor does and looking for a candidate row.
		//Four words: 0, 0x10??, 0, 0
		long cpos = 0;
		long end = rom.getFileSize()-12L;
		rom.setEndian(true);
		while(cpos < end){
			//Four words.
			int word = rom.intFromFile(cpos);
			if(word != 0){cpos += 4; continue;}
			
			word = rom.intFromFile(cpos+4L) & 0xFFFFFF00;
			if(word != 0x00001000){cpos += 4; continue;}
			
			word = rom.intFromFile(cpos+8L);
			if(word != 0){cpos += 4; continue;}
			
			word = rom.intFromFile(cpos+12L);
			if(word != 0){cpos += 4; continue;}

			return cpos;
		}
		
		return -1L;
	}
	
	public static long findTableSize(FileBuffer rom, long tbl_start){
		rom = N64ROMImage.adjustByteOrderToZ64(rom);
		
		long cpos = tbl_start;
		long end = rom.getFileSize() - 12L;
		//Look at every third word until find a match to table start.
		while(cpos < end){
			long lword = Integer.toUnsignedLong(rom.intFromFile(cpos+8L));
			if(lword == tbl_start){
				lword = Integer.toUnsignedLong(rom.intFromFile(cpos+4L)); //End
				return lword - tbl_start;
			}
			cpos += 16;
		}
		return -1L;
	}
	
	public static N64ZFileTable readTable(FileBuffer table){
		if(table == null) return null;
		long ftsize = table.getFileSize();
		int ecount = (int)(ftsize >>> 4);
		
		N64ZFileTable ft = new N64ZFileTable(ecount+1);
		table.setEndian(true);
		table.setCurrentPosition(0L);
		for(int i = 0; i < ecount; i++){
			Entry e = new Entry();
			ft.entries.add(e);
			
			e.v_start = Integer.toUnsignedLong(table.nextInt());
			e.v_end = Integer.toUnsignedLong(table.nextInt());
			e.offset = Integer.toUnsignedLong(table.nextInt());
			long cend = Integer.toUnsignedLong(table.nextInt());
			if(e.offset == 0xFFFFFFFFL){e.size = 0L; continue;}
			if(e.v_start == 0L && e.v_end == 0L){e.size = 0L; continue;}
			
			e.size = e.v_end - e.v_start;
			if(cend == 0L){
				//Uncompressed.
				e.comp_size = e.size;
			}
			else{
				//Compressed (probably Yaz0)
				e.comp_size = cend - e.offset;
			}
		}
		
		return ft;
	}
	
	public static N64ZFileTable readTable(FileBuffer data, long offset){
		if(data == null) return null;
		FileBuffer sub = data.createReadOnlyCopy(offset, data.getFileSize());
		N64ZFileTable ft = readTable(sub);
		try{sub.dispose();}
		catch(Exception ex){ex.printStackTrace();}
		return ft;
	}
	
	public static N64ZFileTable readTable(FileBuffer data, long offset, long size){
		if(data == null) return null;
		FileBuffer sub = data.createReadOnlyCopy(offset, offset+size);
		N64ZFileTable ft = readTable(sub);
		try{sub.dispose();}
		catch(Exception ex){ex.printStackTrace();}
		return ft;
	}
	
	/*----- Getters -----*/
	
	public int getEntryCount(){return entries.size();}
	
	public Entry getEntry(int index){
		if(index < 0) return null;
		if(index >= entries.size()) return null;
		return entries.get(index);
	}

	public DirectoryNode getFileTree(String rom_path, int byte_ordering, boolean group){
		DirectoryNode root = new DirectoryNode(null, "");
		DirectoryNode parent = root;
		if(group){
			parent = new DirectoryNode(root, String.format("0x%08x", 0L));
		}
		long group_off = 0L; 
		long group_end = group_off + DIR_GROUP_LEN;
		for(Entry e : entries){
			String name = String.format("f_%08x.bin", e.offset);
			if(group){
				//See if need to go to next parent.
				if(e.offset >= group_end){
					group_off = group_end;
					group_end = group_off + DIR_GROUP_LEN;
					parent = new DirectoryNode(root, String.format("0x%08x", group_off));
				}
			}
			FileNode fn = new FileNode(parent, name);
			fn.setSourcePath(rom_path);
			fn.setOffset(e.getROMAddress());
			fn.setLength(e.getSizeOnROM());
			
			//Compression
			if(e.isCompressed()){
				fn.setMetadataValue(FNMETAKEY_DECOMPSIZE, Long.toHexString(e.getSize()));
				fn.addTypeChainNode(new CompDefNode(Yaz.getDefinition()));
			}
			
			//Byte Ordering
			//Target is BE, so we're actually doing this a bit different.
			if(byte_ordering == N64ROMImage.ORDER_N64){
				fn.addEncryption(new NUSDescrambler.NUS_N64_2BE_ByteswapDef());
			}
			else if(byte_ordering == N64ROMImage.ORDER_P64){
				//The Z64 descrambler just reverses words, so we should be able to use
				//	it either way.
				fn.addEncryption(new NUSDescrambler.NUS_Z64_ByteswapDef());
			}
		}
		
		return root;
	}
	
	public FileNode getFileAsNode(String rom_path, int byte_ordering, int index){
		if(index < 0 || entries == null) return null;
		if(index >= entries.size()) return null;
		
		Entry e = entries.get(index);
		String name = String.format("f_%08x.bin", e.offset);
		
		FileNode fn = new FileNode(null, name);
		fn.setSourcePath(rom_path);
		fn.setOffset(e.getROMAddress());
		fn.setLength(e.getSizeOnROM());
		//System.err.println(String.format("DEBUG -- e.getROMAddress: %08x", e.getROMAddress()));
		//System.err.println(String.format("DEBUG -- e.getSizeOnROM: %08x", e.getSizeOnROM()));
		
		//Compression
		if(e.isCompressed()){
			fn.setMetadataValue(FNMETAKEY_DECOMPSIZE, Long.toHexString(e.getSize()));
			fn.addTypeChainNode(new CompDefNode(Yaz.getDefinition()));
		}
		
		//Byte Ordering
		//Target is BE, so we're actually doing this a bit different.
		if(byte_ordering == N64ROMImage.ORDER_N64){
			fn.addEncryption(new NUSDescrambler.NUS_N64_2BE_ByteswapDef());
		}
		else if(byte_ordering == N64ROMImage.ORDER_P64){
			//The Z64 descrambler just reverses words, so we should be able to use
			//	it either way.
			fn.addEncryption(new NUSDescrambler.NUS_Z64_ByteswapDef());
		}
		
		return fn;
	}
	
	/*----- Setters -----*/
	
	/*----- Type Definition -----*/
	
	private static NUSDMATableDef filetable_def;
	
	public static NUSDMATableDef getDefinition(){
		if(filetable_def == null) filetable_def = new NUSDMATableDef();
		return filetable_def;
	}
	
	public static class NUSDMATableDef extends GenericSystemDef{
		
		private static String DEFO_ENG_DESC = "Nintendo 64 ROM File DMA Table";
		public static int TYPE_ID = 0x64943EB0;
	
		public NUSDMATableDef(){
			super(DEFO_ENG_DESC, TYPE_ID);
		}
		
		public FileClass getFileClass(){
			return FileClass.DAT_TABLE;
		}
	
	}

}
