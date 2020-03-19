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
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;
import waffleoRai_Utils.Treenumeration;

public class U8Arc 
{
	
	/* ----- Constant ----- */
	
	public static final int MAGIC = 0x55AA382D;
	
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
	
	private String sourceFile;
	private DirectoryNode root;
	
	private FileBuffer openFile;
	
	/* ----- Construction/Parsing ----- */
	
	public U8Arc(String filepath, long stpos) throws IOException, UnsupportedFileTypeException
	{
		sourceFile = filepath;
		parseArchive(stpos);
	}
	
	private void parseArchive(long stpos) throws IOException, UnsupportedFileTypeException
	{
		System.err.println("U8Arc.parseArchive || Archive file exists: " + FileBuffer.fileExists(sourceFile));
		FileBuffer arc = FileBuffer.createBuffer(sourceFile, true);
		long cpos = stpos;
		
		//Header
		int magic = arc.intFromFile(cpos); cpos += 4;
		if(magic != MAGIC) throw new FileBuffer.UnsupportedFileTypeException("Mismatching magic numbers for Wii U8 archive!");
		long rootOff = Integer.toUnsignedLong(arc.intFromFile(cpos)); cpos+=4;
		//long headerSize = Integer.toUnsignedLong(arc.intFromFile(cpos)); cpos+=4;
		//long dataOff = Integer.toUnsignedLong(arc.intFromFile(cpos)); cpos+=4;
		
		//Otherwise, it reads the same as a GCN/Wii fst
		cpos = rootOff + 8;
		int num_entries = arc.intFromFile(cpos);
		long stbl_off = rootOff + (0xC * (num_entries));
		
		root = new DirectoryNode(null, "root");
		DirectoryNode activeDir = root;
		root.setEndIndex(num_entries);
		
		cpos = rootOff + 0xC;
		for(int i = 1; i < num_entries; i++)
		{
			int node_index = i+1;
			//System.err.println("GCWiiDisc.readFileSystem || -DEBUG- Reading Node: " + node_index);
			
			byte type = arc.getByte(cpos); cpos++;
			int nameOff = arc.shortishFromFile(cpos); cpos += 3;
			int offsetRaw = arc.intFromFile(cpos); cpos += 4;
			int sizeRaw = arc.intFromFile(cpos); cpos += 4;
			//Get name
			String node_name = arc.getASCII_string((int)stbl_off + nameOff, '\0');
			
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
		
	}
	
	/* ----- Writing ----- */
	
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
		System.err.println("U8Arc.dumpDirectoryTo || Dumping " + mypath);
		System.err.println("U8Arc.dumpDirectoryTo || Directory Offset: 0x" + Long.toHexString(dn.getOffset()));
		
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
		System.err.println("U8Arc.dumpFileTo || Dumping " + mypath);
		if(fn.length < 1){
			Files.createFile(Paths.get(mypath));
			System.err.println("U8Arc.dumpFileTo || (Empty file!)");
			return true; //It's just an empty file.
		}
		//System.err.println("GCWiiDisc.dumpFileTo || File Offset: 0x" + Long.toHexString(fn.getOffset()));
		
		/*long offsector = fn.getOffset()/0x7C00L;
		long offoff = fn.getOffset()%0x7C00L;
		long offset = (offsector << WBFSImage.WII_SEC_SZ_S) + 0x400L + offoff;*/
		//long offset = fn.getOffset() << 2; //Is offset shifted?
		long offset = fn.getOffset();
		//System.err.println("GCWiiDisc.dumpFileTo || File Offset: 0x" + Long.toHexString(offset));
		//System.err.println("GCWiiDisc.dumpFileTo || File Size: 0x" + Long.toHexString(fn.getLength()));
		FileBuffer myfile = openFile.createReadOnlyCopy(offset, offset + fn.getLength());
		myfile.writeFile(mypath);
		
		return true;
	}
	
	public boolean dumpArchive(String directory) throws IOException
	{
		//Reopen file
		openFile = FileBuffer.createBuffer(sourceFile, true);
		
		//I dunno what the offsets are relative to, so try a few guesses
		//I also dunno if the offsets need to be left shifted
		if(!FileBuffer.directoryExists(directory))
		{
			Files.createDirectories(Paths.get(directory));
		}

		boolean b = dumpRootDirectoryTo(directory, root);
		openFile = null;
		return b;
	}
	
	
}
