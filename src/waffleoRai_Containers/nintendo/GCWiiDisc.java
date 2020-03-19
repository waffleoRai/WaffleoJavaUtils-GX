package waffleoRai_Containers.nintendo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.tree.TreeNode;

import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.StreamBuffer;
import waffleoRai_Utils.Treenumeration;

public class GCWiiDisc {
	
	/* ----- Constants ----- */
	
	public static final int WII_HEADER_MAGIC = 0x5D1C9EA3;
	public static final int GCN_HEADER_MAGIC = 0xC2339F3D;
	
	public static final long HEADER_SIZE = 0x440L;
	public static final long BI2_SIZE = 0x2000L;
	
	public static final long OFFSET_DOL_ADDR = 0x420L;
	public static final long OFFSET_FST_ADDR = 0x424L;
	public static final long OFFSET_FST_SIZE = 0x428L;
	public static final long OFFSET_APPL_ADDR = 0x2440L;
	
	public static final String GCM_EXT = "gcm";
	
	/* ----- Inner Classes ----- */
	
	public static class FileNode implements TreeNode, Comparable<FileNode>
	{
		/* --- Instance Variables --- */
		
		protected DirectoryNode parent;
		
		private String fileName;
		private long offset;
		private long length;
		
		/* --- Construction --- */
		
		public FileNode(DirectoryNode parent, String name)
		{
			this.parent = parent;
			fileName = name;
			offset = -1;
			length = 0;
			if(parent != null) parent.addChild(this);
		}
		
		/* --- Getters --- */
		
		public String getFileName(){return fileName;}
		public long getOffset(){return offset;}
		public long getLength(){return length;}
		public DirectoryNode getParent(){return parent;}
		
		/* --- Setters --- */
		
		public void setFileName(String name){fileName = name;}
		public void setOffset(long off){offset = off;}
		public void setLength(long len){length = len;}
		public void setParent(DirectoryNode p){parent = p; if(p != null) p.addChild(this);}
	
		/* --- Comparable --- */
		
		public boolean isDirectory()
		{
			return false;
		}
		
		public boolean equals(Object o)
		{
			if(o == this) return true;
			if(o == null) return false;
			if(!(o instanceof FileNode)) return false;
			FileNode fn = (FileNode)o;
			if(this.isDirectory() != fn.isDirectory()) return false;
			return fileName.equals(fn.fileName);
		}
		
		public int hashCode()
		{
			return fileName.hashCode() ^ (int)offset;
		}
		
		public int compareTo(FileNode other)
		{
			if(other == this) return 0;
			if(other == null) return 1;
			
			if(this.isDirectory() && !other.isDirectory()) return -1;
			if(!this.isDirectory() && other.isDirectory()) return 1;
			
			return this.fileName.compareTo(other.fileName);
		}
		
		/* --- TreeNode --- */
		
		@Override
		public TreeNode getChildAt(int childIndex) {return null;}

		@Override
		public int getChildCount() {return 0;}

		@Override
		public int getIndex(TreeNode node) {return -1;}

		@Override
		public boolean getAllowsChildren() {return false;}

		@Override
		public boolean isLeaf() {return true;}

		@Override
		public Enumeration<TreeNode> children() 
		{
			TreeNode[] n = null;
			return new Treenumeration(n);
		}
		
	}
	
	public static class DirectoryNode extends FileNode
	{
		/* --- Instance Variables --- */
		
		private Set<FileNode> children;
		private int endIndex;
		
		/* --- Construction --- */
		
		public DirectoryNode(DirectoryNode parent, String name)
		{
			super(parent, name);
			children = new HashSet<FileNode>();
			endIndex = -1;
		}
		
		/* --- Getters --- */
		
		public int getEndIndex(){return endIndex;}
		
		public List<FileNode> getChildren()
		{
			List<FileNode> list = new ArrayList<FileNode>(children.size() + 1);
			list.addAll(children);
			Collections.sort(list);
			return list;
		}
		
		public boolean isDirectory()
		{
			return true;
		}
		
		/* --- Setters --- */
		
		protected void addChild(FileNode node){children.add(node);}
		public void clearChildren(){children.clear();}
		public void setEndIndex(int i){endIndex = i;}
		
		/* --- TreeNode --- */
		
		@Override
		public TreeNode getChildAt(int childIndex) 
		{
			List<FileNode> childlist = this.getChildren();
			return childlist.get(childIndex);
		}

		@Override
		public int getChildCount() {return children.size();}

		@Override
		public int getIndex(TreeNode node) 
		{
			if(children.contains(node))
			{
				List<FileNode> clist = this.getChildren();
				int ccount = clist.size();
				for(int i = 0; i < ccount; i++)
				{
					if(clist.get(i).equals(node)) return i;
				}
			}
			return -1;
		}

		@Override
		public boolean getAllowsChildren() {return true;}

		@Override
		public boolean isLeaf() 
		{
			return (children.isEmpty());
		}

		@Override
		public Enumeration<TreeNode> children() 
		{
			List<TreeNode> list = new ArrayList<TreeNode>(children.size()+1);
			list.addAll(getChildren());
			return new Treenumeration(list);
		}
	
	}
	
	/* ----- Instance Variables ----- */
	
	private String filePath;
	private StreamBuffer openFile;
	
	private GCWiiHeader header;
	private DirectoryNode root;
	
	private long fst_offset;
	private long dol_offset;
	private long dol_size;
	//private long appl_offset;
	private long appl_size;
	
	/* ----- Construction ----- */
	
	public GCWiiDisc(String image_path) throws IOException
	{
		filePath = image_path;
		readDiskInfo();
	}
	
	/* ----- Parsing ----- */
	
	private void readDiskInfo() throws IOException
	{
		//Header
		openFile = new StreamBuffer(filePath, true);
		header = GCWiiHeader.readHeader(openFile, 0);
		fst_offset = Integer.toUnsignedLong(openFile.intFromFile(OFFSET_FST_ADDR)) << 2;
		root = readFileSystem();
		dol_offset = Integer.toUnsignedLong(openFile.intFromFile(OFFSET_DOL_ADDR)) << 2;
		//appl_offset = Integer.toUnsignedLong(openFile.intFromFile(OFFSET_DOL_ADDR));
		dol_size = calculateDOLSize(dol_offset);
		appl_size = getAppLoaderSize(OFFSET_APPL_ADDR);
	}
	
	private long calculateDOLSize(long dolOff)
	{
		//long[] offsets = new long[18];
		//long[] sizes = new long[18];
		long opos = dolOff;
		long spos = dolOff + 0x90;
		long max = 0;
		for(int i = 0; i < 18; i++)
		{
			long offset = Integer.toUnsignedLong(openFile.intFromFile(opos)); opos+=4;
			long size = Integer.toUnsignedLong(openFile.intFromFile(spos)); spos+=4;
			long sum = offset + size;
			if(sum > max) max = sum;
		}
		
		return max;
	}
	
	private long getAppLoaderSize(long alOff)
	{
		//Should be the 4-byte value at 0x14?
		//I think this is the full file size?
		long sz = Integer.toUnsignedLong(openFile.intFromFile(alOff + 0x14));
		//I think add the next value?
		sz += Integer.toUnsignedLong(openFile.intFromFile(alOff + 0x18) + 0x18);
		return sz;
	}
	
	private FileBuffer extractFST() throws IOException
	{
		//final long FSTOFF_OFF = 0x424;
		//final long FSTSIZE_OFF = 0x428;
		
		if(openFile == null) openFile = new StreamBuffer(filePath);
		//long offset = Integer.toUnsignedLong(openFile.intFromFile(OFFSET_FST_ADDR)) << 2;
		long offset = fst_offset;
		long size = Integer.toUnsignedLong(openFile.intFromFile(OFFSET_FST_SIZE)) << 2;
		
		System.err.println("GCWiiDisc.extractFST || -DEBUG- FST Offset: 0x" + Long.toHexString(offset));
		System.err.println("GCWiiDisc.extractFST || -DEBUG- FST Size: 0x" + Long.toHexString(size));
		
		FileBuffer fst = openFile.createReadOnlyCopy(offset, offset+size);
		
		return fst;
	}
	
	public DirectoryNode readFileSystem() throws IOException
	{
		//Only concerned with the "fst"
		FileBuffer fst = extractFST();
		if (fst == null) return null;
		return readFileSystem(fst);
	}
	
	public DirectoryNode readFileSystem(FileBuffer fst)
	{
		//Only concerned with the "fst"
		//Read root record
		long cpos = 8;
		int num_entries = fst.intFromFile(cpos);
		long stbl_off = 0xC * (num_entries);
		//System.err.println("GCWiiDisc.readFileSystem || -DEBUG- Number of Entries: " + num_entries);
		//System.err.println("GCWiiDisc.readFileSystem || -DEBUG- String Table Offset: 0x" + Long.toHexString(stbl_off));
		//System.err.println("GCWiiDisc.readFileSystem || -DEBUG- Input FST Size: 0x" + Long.toHexString(fst.getFileSize()));
		//I think num_entries includes root
		
		DirectoryNode root = new DirectoryNode(null, "root");
		DirectoryNode activeDir = root;
		root.setEndIndex(num_entries);
		
		cpos = 0xC;
		for(int i = 1; i < num_entries; i++)
		{
			int node_index = i+1;
			//System.err.println("GCWiiDisc.readFileSystem || -DEBUG- Reading Node: " + node_index);
			
			byte type = fst.getByte(cpos); cpos++;
			int nameOff = fst.shortishFromFile(cpos); cpos += 3;
			int offsetRaw = fst.intFromFile(cpos); cpos += 4;
			int sizeRaw = fst.intFromFile(cpos); cpos += 4;
			//System.err.println("\tName Offset: 0x" + Integer.toHexString(nameOff));
			//System.err.println("\tFile Offset: 0x" + Integer.toHexString(offsetRaw));
			//System.err.println("\tSize Offset: 0x" + Integer.toHexString(sizeRaw));
			
			//Get name
			//System.err.println("\tTrue Name Offset: 0x" + Integer.toHexString(sizeRaw));
			String node_name = fst.getASCII_string((int)stbl_off + nameOff, '\0');
			//System.err.println("\tNode Name: " + node_name);
			
			//Interpret according to type
			if(type == 0)
			{
				//It's a file
				//System.err.println("\tNode is a file!");
				FileNode node = new FileNode(activeDir, node_name);
				node.setOffset(Integer.toUnsignedLong(offsetRaw)); //Not sure if shifted!!
				node.setLength(Integer.toUnsignedLong(sizeRaw));
				//System.err.println(node_index + "\tF\t" + node_name + "\t0x" + Long.toHexString(offsetRaw) + "\t0x" + Long.toHexString(sizeRaw));
			}
			else
			{
				//It's a directory
				DirectoryNode node = new DirectoryNode(activeDir, node_name);
				node.setEndIndex(sizeRaw);
				node.setOffset(offsetRaw);
				//System.err.println("\tNode is a directory! End Node: " + sizeRaw);
				activeDir = node;
				//System.err.println(node_index + "\tD\t" + node_name + "\t0x" + Long.toHexString(offsetRaw) + "\t" + sizeRaw);
			}
			
			//Back up a directory if this directory is done
			while(node_index >= activeDir.getEndIndex())
			{
				activeDir = activeDir.getParent();
				if(activeDir == null) break;
			}
		}
		
		return root;
	}
	
	/* ----- Getters ----- */
	
	public GCWiiHeader getHeader()
	{
		return header;
	}
	
	public String getGameTitle()
	{
		return header.getGameTitle();
	}
	
	public String getGameID()
	{
		return header.getFullGameCode();
	}
	
	/* ----- Management ----- */
	
	public void closeDiscStream()
	{
		openFile = null;
	}

	/* ----- Dump ----- */
	
	private boolean dumpRootDirectoryTo(String parentPath, DirectoryNode root) throws IOException
	{
		if (root == null) return false;
		List<FileNode> children = root.getChildren();
		boolean b = true;
		for(FileNode child : children)
		{
			if(child instanceof DirectoryNode)
			{
				DirectoryNode dir = (DirectoryNode)child;
				b = b && dumpDirectoryTo(parentPath, dir);
			}
			else b = b && dumpFileTo(parentPath, child);
		}
		
		return b;
	}
	
	private boolean dumpDirectoryTo(String parentPath, DirectoryNode dn) throws IOException
	{
		if (dn == null) return false;
		//This version adds the dir name to the path!
		String mypath = parentPath + File.separator + dn.getFileName();
		if(!FileBuffer.directoryExists(mypath)) Files.createDirectory(Paths.get(mypath));
		System.err.println("GCWiiDisc.dumpDirectoryTo || Dumping " + mypath);
		System.err.println("GCWiiDisc.dumpDirectoryTo || Directory Offset: 0x" + Long.toHexString(dn.getOffset()));
		
		List<FileNode> children = dn.getChildren();
		boolean b = true;
		for(FileNode child : children)
		{
			if(child instanceof DirectoryNode)
			{
				DirectoryNode dir = (DirectoryNode)child;
				b = b && dumpDirectoryTo(mypath, dir);
			}
			else b = b && dumpFileTo(mypath, child);
		}
		
		return b;
	}
	
	private boolean dumpFileTo(String parentPath, FileNode fn) throws IOException
	{
		if(fn == null) return false;
		String mypath = parentPath + File.separator + fn.getFileName();
		System.err.println("GCWiiDisc.dumpFileTo || Dumping " + mypath);
		if(fn.length < 1){
			Files.createFile(Paths.get(mypath));
			System.err.println("GCWiiDisc.dumpFileTo || (Empty file!)");
			return true; //It's just an empty file.
		}
		//System.err.println("GCWiiDisc.dumpFileTo || File Offset: 0x" + Long.toHexString(fn.getOffset()));
		
		/*long offsector = fn.getOffset()/0x7C00L;
		long offoff = fn.getOffset()%0x7C00L;
		long offset = (offsector << WBFSImage.WII_SEC_SZ_S) + 0x400L + offoff;*/
		long offset = fn.getOffset() << 2;
		System.err.println("GCWiiDisc.dumpFileTo || File Offset: 0x" + Long.toHexString(offset));
		System.err.println("GCWiiDisc.dumpFileTo || File Size: 0x" + Long.toHexString(fn.getLength()));
		FileBuffer myfile = openFile.createReadOnlyCopy(offset, offset + fn.getLength());
		myfile.writeFile(mypath);
		
		return true;
	}
	
	public boolean dumpDiscContentsTo(String directory) throws IOException
	{
		//System.err.println("GCWiiDisc.dumpDiscContentsTo || -DEBUG- Method called!");
		if(!FileBuffer.fileExists(filePath)) return false;
		//System.err.println("GCWiiDisc.dumpDiscContentsTo || -DEBUG- Source file exists!");
		
		//Generate a directory name from the header (use the first six bytes as ASCII)
		if(openFile == null) openFile = new StreamBuffer(filePath, true);
		/*String rootName = openFile.getASCII_string(0, 6);
		if(rootName == null || rootName.isEmpty())
		{
			Random r = new Random();
			rootName = "GCWIIDISC_" + Integer.toHexString(r.nextInt());
		}*/
		
		//Prepare directories
		if(!FileBuffer.directoryExists(directory))
		{
			Files.createDirectories(Paths.get(directory));
		}
		//String part_dir = directory + File.separator + rootName;
		//String sys_dir = part_dir + File.separator + "sys";
		//String fs_dir = part_dir + File.separator + "files";
		String sys_dir = directory + File.separator + "sys";
		String fs_dir = directory + File.separator + "files";
		//Files.createDirectory(Paths.get(part_dir));
		if(!FileBuffer.directoryExists(sys_dir))Files.createDirectory(Paths.get(sys_dir));
		if(!FileBuffer.directoryExists(fs_dir))Files.createDirectory(Paths.get(fs_dir));
		
		//Write the raw partition data
		/*NOTE - this is all Wii shit - I'll just comment it out until I fix the Wii class too
		 * cert, h3, ticket, tmd, setup
		 */
		/*String apath = part_dir + File.separator + "cert.bin";
		//dumpCertChain(apath);
		FileBuffer afile = serializeCertChain();
		afile.writeFile(apath);
		apath = part_dir + File.separator + "h3.bin";
		//dumpH3Table(apath);
		afile = serializeH3Table();
		afile.writeFile(apath);
		apath = part_dir + File.separator + "ticket.bin";
		//dumpTicket(apath);
		afile = oTicket.serializeTicket();
		afile.writeFile(apath);
		apath = part_dir + File.separator + "tmd.bin";
		//dumpTMD(apath);
		afile = oTMD.serializeTMD();
		afile.writeFile(apath);
		
		apath = part_dir + File.separator + "setup.txt";
		BufferedWriter bw = new BufferedWriter(new FileWriter(apath));
		bw.write("# setup.txt : scanned by wit+wwt while composing a disc.\n");
		bw.write("# remove the '!' before name to activate the parameter.\n\n");
		String id = getPartitionID();
		bw.write("!part-id = " + id + "\n");
		String name = getGameName();
		bw.write("!part-name = " + name + "\n");
		bw.write("!part-offset = 0x" + Long.toHexString(iAddress) + "\n");
		bw.close();*/
		
		//Copy the "sys" files
		//boot, bi2, fst, main.dol, apploader.img
		String apath = sys_dir + File.separator + "boot.bin";
		FileBuffer afile = openFile.createReadOnlyCopy(0, HEADER_SIZE);
		afile.writeFile(apath);
		apath = sys_dir + File.separator + "bi2.bin";
		afile = openFile.createReadOnlyCopy(HEADER_SIZE, HEADER_SIZE+BI2_SIZE);
		afile.writeFile(apath);
		apath = sys_dir + File.separator + "fst.bin";
		FileBuffer fst = extractFST(); //We'll need this to extract the files...
		fst.writeFile(apath);
		apath = sys_dir + File.separator + "main.dol";
		afile = openFile.createReadOnlyCopy(dol_offset, dol_offset + dol_size);
		afile.writeFile(apath);
		
		//long apploaderOff = Integer.toUnsignedLong(openFile.intFromFile(0x2440));
		//long apploaderSize = this.getAppLoaderSize(apploaderOff);
		apath = sys_dir + File.separator + "apploader.img";
		afile = openFile.createReadOnlyCopy(OFFSET_APPL_ADDR, OFFSET_APPL_ADDR + appl_size);
		afile.writeFile(apath);
		
		//Dump the good stuff to "files"
		//DirectoryNode root = readFileSystem(fst);
		return dumpRootDirectoryTo(fs_dir, root);
	}
	
}
