package waffleoRai_Containers.nintendo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import waffleoRai_Compression.nintendo.NinLZ;
import waffleoRai_Containers.ArchiveDef;
import waffleoRai_Files.Converter;
import waffleoRai_Utils.DirectoryNode;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;
import waffleoRai_Utils.FileNode;


//TODO LZ77 inside the content area...

public class NARC extends NDKDSFile{
	
	/*----- Constants -----*/
	
	public static final int TYPE_ID = 0x4e415243;
	public static final String DEFO_ENG_STR = "Nintendo Nitro Data Archive";
	
	public static final String MAGIC = "NARC";
	
	public static final String MAGIC_BTAF = "BTAF";
	public static final String MAGIC_BTNF = "BTNF";
	public static final String MAGIC_GMIF = "GMIF";

	/*----- Instance Variables -----*/
	
	private boolean lz_gmif; //GMIF is LZ77 (Yaz) compressed
	
	private DirectoryNode root;
	private List<FileNode> raw;
	
	/*----- Construction -----*/
	
	private NARC(){super(null);}
	
	private NARC(FileBuffer src){super(src);}
	
	/*----- Parsing -----*/
	
	public static NARC readNARC(String filepath) throws IOException, UnsupportedFileTypeException
	{
		return readNARC(filepath, 0);
	}
	
	public static NARC readNARC(String filepath, long stpos) throws IOException, UnsupportedFileTypeException
	{
		FileBuffer in = FileBuffer.createBuffer(filepath, stpos, false);
		return readNARC(in, stpos);
	}
	
	public static NARC readNARC(FileBuffer file, long stpos) throws UnsupportedFileTypeException
	{
		//System.err.println("File size: 0x" + Long.toHexString(file.getFileSize()));
		NARC arc = new NARC(file); //This takes care of the DS header
		
		//Check GMIF for compression tag
		FileBuffer gmif = arc.getSectionData(MAGIC_GMIF);
		long cpos = gmif.findString(0, 0x10, MAGIC_GMIF);
		if(cpos != 0) throw new FileBuffer.UnsupportedFileTypeException("NARC.readNARC || GMIF magic number not found!");
		cpos += 8; //Would be offset of tag
		int ctag = gmif.intFromFile(cpos);
		if(ctag == 0x37375A4C){
			arc.lz_gmif = true;
		}
		
		//BTAF
		FileBuffer btaf = arc.getSectionData(MAGIC_BTAF);
		cpos = btaf.findString(0, 0x10, MAGIC_BTAF);
		if(cpos != 0) throw new FileBuffer.UnsupportedFileTypeException("NARC.readNARC || BTAF magic number not found!");
		
		cpos += 8;
		int ecount = btaf.intFromFile(cpos); cpos += 4;
		long[][] fat = new long[ecount][2];
		for(int i = 0; i < ecount; i++)
		{
			fat[i][0] = Integer.toUnsignedLong(btaf.intFromFile(cpos)); cpos+=4;
			fat[i][1] = Integer.toUnsignedLong(btaf.intFromFile(cpos)); cpos+=4;
		}
		
		//BTNF
		FileBuffer btnf = arc.getSectionData(MAGIC_BTNF);
		cpos = btnf.findString(0, 0x10, MAGIC_BTNF);
		if(cpos != 0) throw new FileBuffer.UnsupportedFileTypeException("NARC.readNARC || BTNF magic number not found!");
		long name_off = cpos + 8;
		
		cpos+=8;
		//Get # of directories (from root dir record)
		int dcount = Short.toUnsignedInt(btnf.shortFromFile(cpos+6));
		//System.err.println("Dir Count: " + dcount);
		
		int[][] dirtbl = new int[dcount][3];
		for(int i = 0; i < dcount; i++)
		{
			dirtbl[i][0] = btnf.intFromFile(cpos); cpos+=4; //Offset
			dirtbl[i][1] = Short.toUnsignedInt(btnf.shortFromFile(cpos)); cpos+=2; //First file
			dirtbl[i][2] = Short.toUnsignedInt(btnf.shortFromFile(cpos)); cpos+=2; //Parent
		}
		
		//Spawn dir nodes...
		DirectoryNode[] dirs = new DirectoryNode[dcount];
		for(int i = 0; i < dcount; i++) dirs[i] = new DirectoryNode(null, "");
		
		//Prepare node map
		Map<Integer, FileNode> nmap = new TreeMap<Integer, FileNode>();
		
		//Now fill in the branches...
		for(int i = 0; i < dcount; i++)
		{
			//Mmm children!
			long noff = Integer.toUnsignedLong(dirtbl[i][0]) + name_off;
			//System.err.println("Name Offset: 0x" + Long.toHexString(noff));
			int node_idx = dirtbl[i][1];
			//System.err.println("Node Index: " + node_idx);
			while(true)
			{
				int strlen = Byte.toUnsignedInt(btnf.getByte(noff)); noff++;
				if(strlen == 0) break;
				
				if((strlen & 0x80) != 0)
				{
					//Is dir
					strlen &= 0x7F;
					String name = btnf.getASCII_string(noff, strlen); noff+=strlen;
					int didx = Short.toUnsignedInt(btnf.shortFromFile(noff)); noff+=2;
					didx &= 0xFFF; //That first F always there?
					
					dirs[didx].setFileName(name);
					dirs[didx].setParent(dirs[i]);
				}
				else
				{
					String name = btnf.getASCII_string(noff, strlen); noff+=strlen;
					FileNode fn = new FileNode(dirs[i], name);
					fn.setOffset(node_idx);
					nmap.put(node_idx, fn);
					node_idx++;
				}
			}
		}
		
		//Dir 0 is the root node...
		arc.root = dirs[0];
		if(arc.root.getFileName().isEmpty()) arc.root.setFileName("fsroot");
		arc.raw = new LinkedList<FileNode>();
		
		//Fill in pointers & note raw files...
		//Pointers rescaled to be relative to ENTIRE NARC FILE
		long gmif_off = arc.getOffsetToSection(MAGIC_GMIF);
		long gmif_len = gmif.getFileSize();
		for(int i = 0; i < fat.length; i++)
		{
			long off = fat[i][0] + gmif_off + 8;
			long len = fat[i][1] - fat[i][0];
			
			FileNode fn = nmap.get(i);
			if(fn == null)
			{
				//Save raw
				String rawname = "NARC_RAWFILE_" + String.format("%04x", i) + ".bin";
				fn = new FileNode(null, rawname);
				arc.raw.add(fn);
			}
			
			if(arc.lz_gmif){
				//Offset is different and node needs to be flagged as compressed
				//Offset is relative to start of decompressed GMIF (?)
				System.err.println("NARC.readNARC || -DEBUG- LZ compressed GMIF detected!");
				off = fat[i][0] - 12;
				fn.addCompressionChainNode(NinLZ.getDefinition(), gmif_off+12, gmif_len-12);
			}
			
			fn.setOffset(off);
			fn.setLength(len);
		}
		
		return arc;
	}
	
	/*----- Serialization -----*/
	
	/*----- Getters -----*/
	
	public DirectoryNode getRootNode(){return this.root;}
	
	public List<FileNode> getRawNodes()
	{
		List<FileNode> copy = new ArrayList<FileNode>(this.raw.size() + 1);
		copy.addAll(this.raw);
		return copy;
	}
	
	public DirectoryNode getArchiveTree()
	{
		DirectoryNode iroot = new DirectoryNode(null, "NARC");
		//Only copy root if not empty...
		if(!root.getChildren().isEmpty()){
			
			if(raw.isEmpty()){
				//Just this root - don't need to name fsroot
				List<FileNode> children = root.getChildren();
				for(FileNode child : children) child.copy(iroot);
			}
			else{
				root.setFileName("fsroot");
				root.copyAsDir(iroot);		
			}
		}
		
		//Do the raws...
		for(FileNode raw : raw) raw.copy(iroot);
		
		return iroot;
	}
	
	public boolean gmif_lz_compressed(){
		return this.lz_gmif;
	}
	
	/*----- Setters -----*/
	
	/*----- Other -----*/
	
	/*----- Definition -----*/
	
	private static TypeDef static_def;
	private static StdConverter static_conv;
	
	public static class TypeDef extends ArchiveDef
	{
		
		public static final String[] EXT_LIST = {"narc", "carc"};
		
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
			NARC arc = NARC.readNARC(buffer, 0);
			
			return arc.getArchiveTree();
		}
		
		public String getDefaultExtension() {return "narc";}
		
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
			to_str = "System Directory";
		}

		public String getFromFormatDescription() {return from_str;}
		public String getToFormatDescription() {return to_str;}
		public void setFromFormatDescription(String s) {from_str = s;}
		public void setToFormatDescription(String s) {to_str = s;}

		@Override
		public void writeAsTargetFormat(String inpath, String outpath) throws IOException, UnsupportedFileTypeException 
		{
			NARC arc = NARC.readNARC(inpath);
			arc.getArchiveTree().dumpTo(outpath);
		}
		
		public void writeAsTargetFormat(FileBuffer input, String outpath) throws IOException, UnsupportedFileTypeException
		{
			NARC arc = NARC.readNARC(input, 0);
			arc.getArchiveTree().dumpTo(outpath);
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
	
	
}
