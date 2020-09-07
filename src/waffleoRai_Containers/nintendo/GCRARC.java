package waffleoRai_Containers.nintendo;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import waffleoRai_Files.tree.DirectoryNode;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;
import waffleoRai_Files.tree.FileNode;

public class GCRARC {
	
	/*----- Format Notes -----*/
	
	//I am using slightly different terminology than MKWiiki, because
	//	theirs is a little confusing.
	
	//Namely, I switched "directory" and "node"
	
	/*
	 * ---- Header
	 * Magic "RARC" [4]
	 * File Size	[4]
	 * Header Size	[4]
	 * Offset to file data [4]
	 * 		(This is relative to end of header)
	 * Length of file data	[4]
	 * Unknown (Always same as previous)	[4]
	 * Unknown (Always 0)	[4]
	 * Unknown (Always 0)	[4]
	 * 
	 * ---- Info Block
	 * ->Offset values are relative to end of header
	 * D Node Count	[4]
	 * Offset to first D node	[4]
	 * F/D Node count	[4]
	 * Offset to first F/D node	[4]
	 * Length of str table	[4]
	 * Offset to str table	[4]
	 * Number of F nodes	[4]
	 * Unknown (Always 0)	[4]
	 * Unknown (Always 0)	[4]
	 * 
	 * ---- Directory Node (D Node)
	 * Identifier	[4]
	 * Name offset (strtbl)	[4]
	 * 		Relative to start of strtbl
	 * Name Hash	[2]
	 * Node count	[2]
	 * Index of first node	[4]
	 * 
	 * 
	 * ----	F/D Node
	 * Node index	[2]
	 * 		This value is 0xFFFF for directory
	 * Name Hash	[2]
	 * Flags	[2]
	 * 		Not really known. Seems to be 0x200 for dirs, and 0x1100 for files
	 * Name offset	[2]
	 * 		Relative to start of strtbl
	 * Location	[4]
	 * 		If File: Offset to file data
	 * 		If Dir: Index of D Node
	 * File Size	[4]
	 * 		Unused for dir
	 * Reserved ZERO	[4]
	 * 
	 */
	
	/*----- Constants -----*/
	
	public static final int MAGIC = 0x52415243;
	public static final String MAGIC_STR = "RARC";
	
	/*----- Instance Variables -----*/
	
	private DirectoryNode root;
	
	/*----- Construction -----*/
	
	private GCRARC()
	{
		//Nothing
	}
	
	public static GCRARC openRARC(String filePath) throws UnsupportedFileTypeException, IOException
	{
		if(filePath == null || filePath.isEmpty()) return null;
		FileBuffer file = FileBuffer.createBuffer(filePath, true);
		GCRARC arc = new GCRARC();
		arc.parseRARC(file, filePath);
		
		return arc;
	}
	
	public static GCRARC createRARC(String rootDirName)
	{
		GCRARC arc = new GCRARC();
		arc.root = new DirectoryNode(null, rootDirName);
		return arc;
	}
	
	/*----- Getters -----*/
	
	public DirectoryNode getRoot()
	{
		return root;
	}
	
	/*----- Setters -----*/
	
	/*----- Utility -----*/
	
	public static int getStringHash(String str)
	{
		int hash = 0;
		int strlen = str.length();
		for(int i = 0; i < strlen; i++)
		{
			hash *= 3;
			String s = str.substring(i, i+1);
			byte[] ascii = null;
			try{ascii = s.getBytes("ASCII");}
			catch(Exception e){e.printStackTrace(); return 0;}
			hash += Byte.toUnsignedInt(ascii[0]);
		}
		return hash;
	}
	
	private class DNode
	{
		public String ID;
		public String name;
		public int fdnode_count;
		public int first_node_idx;
		
		public int strtbl_off;
	}
	
	private class FDNode
	{
		public boolean isDir;
		public int index;
		public String name;
		public long offset;
		public long len;
		
		public int strtbl_off;
		public FileNode link;
	}

	/*----- Parsing -----*/

	private boolean parseRARC(FileBuffer src, String path) throws UnsupportedFileTypeException, IOException
	{
		if(src == null) return false;
		src.setEndian(true);
		
		//Check magic number
		long cpos = src.findString(0, 0x10, MAGIC_STR);
		if(cpos != 0) throw new FileBuffer.UnsupportedFileTypeException("GCRARC.parseRARC || Magic number not found!");
		
		//Header
		cpos += 8; //Skip file size
		int header_size = src.intFromFile(cpos); cpos += 4;
		long fdat_off = Integer.toUnsignedLong(src.intFromFile(cpos)); cpos += 4;
		fdat_off += header_size;
		//System.err.println("header_size = 0x" + Integer.toHexString(header_size));
		//System.err.println("fdat_off = 0x" + Long.toHexString(fdat_off));
		//cpos += 4; //Skip fdat length
		
		//Info
		cpos = (long)header_size;
		int dcount = src.intFromFile(cpos); cpos+=4;
		long d_off = Integer.toUnsignedLong(src.intFromFile(cpos)); cpos += 4;
		int ncount = src.intFromFile(cpos); cpos+=4;
		long n_off = Integer.toUnsignedLong(src.intFromFile(cpos)); cpos += 4;
		long stbl_len = Integer.toUnsignedLong(src.intFromFile(cpos)); cpos += 4;
		long stbl_off = Integer.toUnsignedLong(src.intFromFile(cpos)); cpos += 4;
		stbl_off += header_size;
		cpos += 4; //Skip file count
		//System.err.println("d_off = 0x" + Long.toHexString(d_off));
		//System.err.println("n_off = 0x" + Long.toHexString(n_off));
		//System.err.println("stbl_off = 0x" + Long.toHexString(stbl_off));
		//System.err.println("dcount = " + dcount);
		//System.err.println("ncount = " + ncount);
		//System.err.println("stbl_len = 0x" + Long.toHexString(stbl_len));
		
		//Copy string table
		FileBuffer strtbl = src.createReadOnlyCopy(stbl_off, stbl_off + stbl_len);
		
		//Read d nodes
		List<DNode> d_list = new ArrayList<DNode>(dcount+1);
		cpos = d_off + header_size;
		for(int i = 0; i < dcount; i++)
		{
			DNode node = new DNode();
			
			node.ID = src.getASCII_string(cpos, 4); cpos += 4;
			long soff = Integer.toUnsignedLong(src.intFromFile(cpos)); cpos += 4;
			cpos += 2; //Skip string hash
			node.fdnode_count = Short.toUnsignedInt(src.shortFromFile(cpos)); cpos += 2;
			node.first_node_idx = src.intFromFile(cpos); cpos += 4;
			node.name = strtbl.getASCII_string(soff, '\0');
			
			d_list.add(node);
			
			//System.err.println("\nDirectory Record Read!");
			//System.err.println("ID: " + node.ID);
			//System.err.println("soff: 0x" + Long.toHexString(soff));
			//System.err.println("Nodes: " + node.fdnode_count);
			//System.err.println("Node 0 Idx: " + node.first_node_idx);
			//System.err.println("Name: " + node.name);
		}
		
		//Read fd nodes
		List<FDNode> fd_list = new ArrayList<FDNode>(ncount+1);
		cpos = n_off + header_size;
		for(int i = 0; i < ncount; i++)
		{
			FDNode node = new FDNode();
			
			node.index = Short.toUnsignedInt(src.shortFromFile(cpos)); cpos += 2;
			cpos += 2; //Skip string hash
			short flags = src.shortFromFile(cpos); cpos += 2;
			node.isDir = (flags == 0x200);
			long soff = Short.toUnsignedLong(src.shortFromFile(cpos)); cpos += 2;
			node.offset = Integer.toUnsignedLong(src.intFromFile(cpos)); cpos += 4;
			node.len = Integer.toUnsignedLong(src.intFromFile(cpos)); cpos += 4;
			cpos += 4; //Padding
			node.name = strtbl.getASCII_string(soff, '\0');
			
			fd_list.add(node);
			
			//System.err.println("\nFile/Directory Record Read!");
			//System.err.println("Index: " + node.index + " (0x" + Integer.toHexString(node.index) + ")");
			//System.err.println("soff: 0x" + Long.toHexString(soff));
			//System.err.println("flags: 0x" + String.format("%04x", flags));
			//System.err.println("Is Dir?: " + node.isDir);
			//System.err.println("Offset/Index: 0x" + Long.toHexString(node.offset) + " (" + node.offset + ")");
			//System.err.println("Length: 0x" + Long.toHexString(node.len));
			//System.err.println("Name: " + node.name);
		}
		
		//Convert to tree
		//Find ROOT
		DNode droot = null;
		for(int i = 0; i < dcount; i++)
		{
			DNode node = d_list.get(i);
			if(node.ID.equals("ROOT"))
			{
				droot = node;
				break;
			}
		}
		if(droot == null) droot = d_list.get(0);
		
		root = new DirectoryNode(null, droot.name);
		readDirectory(droot, root, d_list, fd_list, fdat_off, path);
		
		return true;
	}
	
	private void readDirectory(DNode dn, DirectoryNode node, List<DNode> d_list, List<FDNode> fd_list, long f_off, String p)
	{
		//Find first fdnode
		//System.err.println("Reading Directory: " + dn.name);
		int fn_idx = dn.first_node_idx;
		
		for(int i = 0; i < dn.fdnode_count; i++)
		{
			FDNode fdn = fd_list.get(fn_idx);
			//See if dir or file
			if(fdn.isDir)
			{
				//Get dir node
				//If it's . or .., treat different
				if(!(fdn.name.equals(".") || fdn.name.equals("..")))
				{
					int idx = (int)fdn.offset;
					DNode sdn = d_list.get(idx);
					//See if it's a circular reference
					if(sdn == dn)
					{
						System.err.println("GCRARC.readDirectory || Circular directory link found. This will be rerouted...");
						//Look for a directory node with the same name
						for(DNode other : d_list)
						{
							if(other.name.equals(fdn.name))
							{
								System.err.println("GCRARC.readDirectory || Linking to directory " + other.name);
								sdn = other;
								break;
							}
						}
					}
					
					DirectoryNode subdir = new DirectoryNode(node, sdn.name);
					readDirectory(sdn, subdir, d_list, fd_list, f_off, p);
				}
			}
			else
			{
				//File
				FileNode fn = new FileNode(node, fdn.name);
				fn.setOffset(fdn.offset + f_off);
				fn.setLength(fdn.len);
				fn.setSourcePath(p);
			}
			
			fn_idx++;
		}
	}

	/*----- Serialization -----*/
	
	//TODO add . and ..
	
	private static FileBuffer serializeDNode(DNode node)
	{
		FileBuffer fb = new FileBuffer(0x10, true);
		
		String id = node.ID;
		int slen = id.length();
		if(slen > 4) id = id.substring(0, 4);
		fb.printASCIIToFile(id);
		while(slen < 4)
		{
			fb.addToFile((byte)0x00);
			slen++;
		}
		
		fb.addToFile(node.strtbl_off);
		int hash = getStringHash(node.name);
		fb.addToFile((short)hash);
		fb.addToFile((short)node.fdnode_count);
		fb.addToFile(node.first_node_idx);
		
		return fb;
	}
	
	private static FileBuffer serializeFDNode(FDNode node)
	{
		FileBuffer fb = new FileBuffer(0x14, true);
		
		fb.addToFile((short)node.index);
		int hash = getStringHash(node.name);
		fb.addToFile((short)hash);
		if(node.isDir) fb.addToFile((short)0x200);
		else fb.addToFile((short)0x1100);
		
		fb.addToFile((short)node.strtbl_off);
		fb.addToFile((int)node.offset);
		fb.addToFile((int)node.len);
		fb.addToFile(0);
		
		return fb;
	}
	
	private boolean id_exists(List<DNode> d_list, String id)
	{
		for(DNode dn : d_list)
		{
			if(dn.ID.equals(id)) return true;
		}
		return false;
	}
	
	private String randomID()
	{
		Random r = new Random();
		StringBuilder id = new StringBuilder(4);
		for(int i = 0; i < 4; i++)
		{
			int l = r.nextInt(26);
			char c = (char)(l + 0x41);
			id.append(c);
		}
		return id.toString();
	}
	
	private String generateDirID(DirectoryNode node, List<DNode> d_list)
	{
		String name = node.getFileName();
		String id = null;
		if(name.length() >= 4)
		{
			id = name.substring(0, 4);
			id = id.toUpperCase();	
			while(id_exists(d_list, id)) id = randomID();
		}
		else
		{
			id = name;
			while(id.length() < 4)
			{
				id += ' ';
			}
			while(id_exists(d_list, id)) id = randomID();	
		}
		
		
		return id;
	}
	
	private int addNode(DirectoryNode node, List<DNode> d_list, List<FDNode> fd_list, int stidx)
	{
		List<FileNode> children = node.getChildren();
		List<DirectoryNode> subdirs = new LinkedList<DirectoryNode>();
		
		DNode dn = new DNode();
		dn.name = node.getFileName();
		if(node.getParent() == null) dn.ID = "ROOT";
		else dn.ID = generateDirID(node, d_list);
		dn.fdnode_count = node.getChildCount();
		dn.first_node_idx = stidx;

		int idx = stidx;
		for(FileNode n : children)
		{
			if(n.isDirectory())
			{
				DirectoryNode dir = (DirectoryNode)n;
				subdirs.add(dir);
			}
			else
			{
				FDNode fdn = new FDNode();
				fdn.index = idx;
				idx++;
				fdn.isDir = false;
				fdn.len = n.getLength();
				fdn.offset = n.getOffset();
				fdn.name = n.getFileName();
				fd_list.add(fdn);
				fdn.link = n;
			}
		}
		
		for(DirectoryNode n : subdirs)
		{
			FDNode fdn = new FDNode();
			fdn.index = 0xFFFF;
			fdn.isDir = true;
			fdn.offset = d_list.size();
			fdn.name = n.getFileName();
			fd_list.add(fdn);
			
			idx = addNode(n, d_list, fd_list, idx);
		}
		return idx;
	}
	
	public boolean writeToFile(String path) throws IOException
	{
		List<DNode> d_list = new LinkedList<DNode>();
		List<FDNode> fd_list = new LinkedList<FDNode>();
		
		addNode(root, d_list, fd_list, 0);
		
		//Generate string table and update node values
		int stbl_off = 0;
		List<String> stbl = new LinkedList<String>();
		for(FDNode fdn : fd_list)
		{
			fdn.strtbl_off = stbl_off;
			stbl_off += fdn.name.length() + 1;
			stbl.add(fdn.name);
			
			if(fdn.isDir)
			{
				DNode dn = d_list.get((int)fdn.offset);
				dn.strtbl_off = stbl_off;
			}
		}
		
		//Update node offset values
		final int h_size = 0x20;
		final int i_size = 0x20;
		final int dl_size = d_list.size() * 0x10;
		final int fdl_size = fd_list.size() * 0x14;
		final int stbl_size = (stbl_off + (stbl_off % 0x10)); //Pad to 16
		
		long f_off = (long)h_size + (long)i_size + (long)dl_size + (long)fdl_size + (long)stbl_size;
		long f_st = f_off;
		int f_count = 0;
		for(FDNode fdn : fd_list)
		{
			if(!fdn.isDir)
			{
				f_count++;
				fdn.offset = f_off;
				f_off += (fdn.len + (fdn.len % 0x10)); //Pad to 16
			}
		}
		long total_size = f_st + f_off;
		
		String temppath = FileBuffer.generateTemporaryPath("GCRARC_write");
		
		BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(temppath));
		
		//Header
		FileBuffer header = new FileBuffer(h_size, true);
		header.printASCIIToFile(MAGIC_STR);
		header.addToFile((int)total_size);
		header.addToFile(h_size);
		header.addToFile((int)(f_st - h_size));
		header.addToFile((int)f_off);
		header.addToFile((int)f_off);
		header.addToFile(0);
		header.addToFile(0);
		bos.write(header.getBytes());
		
		//Info
		FileBuffer info = new FileBuffer(i_size, true);
		info.addToFile(d_list.size());
		info.addToFile(i_size);
		info.addToFile(fd_list.size());
		info.addToFile(i_size + dl_size);
		info.addToFile(stbl_size);
		info.addToFile(i_size + dl_size + fdl_size);
		info.addToFile(f_count);
		header.addToFile(0);
		header.addToFile(0);
		bos.write(info.getBytes());
		
		//Dir Nodes
		for(DNode dn : d_list)
		{
			FileBuffer f = serializeDNode(dn);
			bos.write(f.getBytes());
		}
		
		//F/D Nodes
		for(FDNode fdn : fd_list)
		{
			FileBuffer f = serializeFDNode(fdn);
			bos.write(f.getBytes());
		}
		
		//String Table
		FileBuffer s_tbl = new FileBuffer(stbl_size, true);
		int written = 0;
		for(String s : stbl)
		{
			s_tbl.printASCIIToFile(s);
			s_tbl.addToFile((byte)0x00);
			written += s.length() + 1;
		}
		while(written < stbl_size)
		{
			s_tbl.addToFile((byte)0x00);
			written++;
		}
		bos.write(s_tbl.getBytes());
		
		//Files
		for(FDNode fdn : fd_list)
		{
			int pad = (int)(fdn.len % 0x10);
			BufferedInputStream bis = new BufferedInputStream(new FileInputStream(fdn.link.getSourcePath()));
			bis.skip(fdn.link.getOffset());
			long copied = 0;
			for(int i = 0; i < fdn.len; i++)
			{
				int r = bis.read();
				if(r == -1) break;
				bos.write(r);
				copied++;
			}
			bis.close();
			while(copied < fdn.len) bos.write(0);
			for(int i = 0; i < pad; i++) bos.write(0);
		}
		bos.close();
		
		//Copy back to target
		Files.deleteIfExists(Paths.get(path));
		Files.move(Paths.get(temppath), Paths.get(path));
		
		return true;
	}
	
	/*----- Extract -----*/
	
	private boolean writeDir(DirectoryNode dn, String stem) throws IOException
	{
		String dirPath = stem + File.separator + dn.getFileName();
		if(!FileBuffer.directoryExists(dirPath)) Files.createDirectories(Paths.get(dirPath));
		
		List<FileNode> children = dn.getChildren();
		boolean allgood = true;
		for(FileNode child : children)
		{
			if(child.isDirectory()) writeDir((DirectoryNode)child, dirPath);
			else
			{
				//Check for file name. If it doesn't have one, assign one
				String fname = child.getFileName();
				if(fname == null || fname.isEmpty())
				{
					fname = "dat_0x" + Long.toHexString(child.getOffset());
					System.err.println("File has no name. Assigning descriptive name \"" + fname + "\"");
				}
				
				//Extract data
				String fpath = dirPath + File.separator + fname;
				BufferedInputStream bis = new BufferedInputStream(new FileInputStream(child.getSourcePath()));
				long copied = 0;
				bis.skip(child.getOffset());
				
				BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(fpath));
				while(copied < child.getLength())
				{
					int b = bis.read();
					if(b == -1)
					{
						allgood = false;
						break;
					}
					bos.write(b);
					copied++;
				}
				
				bos.close();
				bis.close();
			}
		}
		
		return allgood;
	}
	
	public boolean extractTo(String outDir) throws IOException
	{
		if(!FileBuffer.directoryExists(outDir)) Files.createDirectories(Paths.get(outDir));
		
		return writeDir(root, outDir);
	}
	
	/*----- Other -----*/
	
	private void printDir(DirectoryNode node, BufferedWriter stream, int tabs) throws IOException
	{
		StringBuilder sb = new StringBuilder(tabs);
		for(int i = 0; i < tabs; i++) sb.append('\t');
		String prefix = sb.toString();
		
		//stream.write(prefix + node.getFileName() + "\n");
		
		List<FileNode> children = node.getChildren();
		for(FileNode child : children)
		{
			stream.write(prefix + "\t-> " + child.getFileName());
			
			if(child.isDirectory())
			{
				stream.write("\n");
				printDir((DirectoryNode)child, stream, tabs+1);
			}
			else
			{
				stream.write("[" + child.getSourcePath() + " || ");
				stream.write("0x" + Long.toHexString(child.getOffset()) + " | ");
				stream.write("0x" + Long.toHexString(child.getLength()) + "]");
				stream.write("\n");
			}
			
		}
		
	}
	
	public void printMe(BufferedWriter stream) throws IOException
	{
		stream.write(root.getFileName() + "\n");
		printDir(root, stream, 0);
	}
	
	
}
