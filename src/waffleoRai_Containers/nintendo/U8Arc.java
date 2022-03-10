package waffleoRai_Containers.nintendo;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import waffleoRai_Containers.ArchiveDef;
import waffleoRai_Files.tree.DirectoryNode;
import waffleoRai_Files.tree.FileNode;
import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;

public class U8Arc {
	
	/* ----- Constant ----- */
	
	public static final int MAGIC = 0x55AA382D;
	
	/* ----- Instance Variables ----- */
	
	private FileNode source;
	private DirectoryNode root;
	
	//private FileBuffer openFile;
	
	/* ----- Construction/Parsing ----- */
	
	private U8Arc(){}

	public static U8Arc readArchive(String filepath) throws IOException, UnsupportedFileTypeException{
		return readArchive(filepath, 0L, FileBuffer.fileSize(filepath));
	}
	
	public static U8Arc readArchive(String filepath, long stoff, long len) throws IOException, UnsupportedFileTypeException{
		String fn = filepath.substring(filepath.lastIndexOf(File.separator));
		FileNode srcnode = new FileNode(null, fn);
		srcnode.setSourcePath(filepath);
		srcnode.setOffset(stoff);
		srcnode.setLength(len);
		return readArchive(srcnode);
	}
	
	public static U8Arc readArchive(FileNode src) throws IOException, UnsupportedFileTypeException{
		FileBuffer dat = src.loadDecompressedData();
		U8Arc arc = readArchive(dat.getReferenceAt(0L));
		arc.setSource(src);
		return arc;
	}
	
	public static U8Arc readArchive(FileBuffer src, long stoff) throws UnsupportedFileTypeException{
		return readArchive(src.getReferenceAt(stoff));
	}
	
	public static U8Arc readArchive(BufferReference data) throws UnsupportedFileTypeException{
		//Header
		int magic = data.nextInt();
		if(magic != MAGIC) throw new FileBuffer.UnsupportedFileTypeException("U8Arc.readArchive || Mismatching magic numbers for Wii U8 archive!");
		long rootOff = Integer.toUnsignedLong(data.nextInt());
		
		//Otherwise, it reads the same as a GCN/Wii fst
		data = data.getBuffer().getReferenceAt(rootOff + 8);
		int num_entries = data.nextInt();
		long stbl_off = rootOff + (0xC * (num_entries));
		
		U8Arc arc = new U8Arc();
		arc.root = new DirectoryNode(null, "root");
		DirectoryNode activeDir = arc.root;
		//root.setEndIndex(num_entries);
		arc.root.setScratchValue(num_entries);
		
		for(int i = 1; i < num_entries; i++){
			int node_index = i+1;
			//System.err.println("GCWiiDisc.readFileSystem || -DEBUG- Reading Node: " + node_index);
			
			byte type = data.nextByte();
			int nameOff = data.next24Bits();
			int offsetRaw = data.nextInt();
			int sizeRaw = data.nextInt();
			//Get name
			String node_name = data.getBuffer().getASCII_string(stbl_off + nameOff, '\0');
			//String node_name = arc.getASCII_string((int)stbl_off + nameOff, '\0');
			
			//Interpret according to type
			if(type == 0){
				//It's a file
				//System.err.println("\tNode is a file!");
				FileNode node = new FileNode(activeDir, node_name);
				node.setOffset(Integer.toUnsignedLong(offsetRaw)); //Not sure if shifted!!
				node.setLength(Integer.toUnsignedLong(sizeRaw));
				//System.err.println(node_index + "\tF\t" + node_name + "\t0x" + Long.toHexString(offsetRaw) + "\t0x" + Long.toHexString(sizeRaw));
			}
			else{
				//It's a directory
				DirectoryNode node = new DirectoryNode(activeDir, node_name);
				//node.setEndIndex(sizeRaw);
				node.setScratchValue(sizeRaw);
				node.setOffset(offsetRaw);
				//System.err.println("\tNode is a directory! End Node: " + sizeRaw);
				activeDir = node;
				//System.err.println(node_index + "\tD\t" + node_name + "\t0x" + Long.toHexString(offsetRaw) + "\t" + sizeRaw);
			}
			
			//Back up a directory if this directory is done
			while(node_index >= activeDir.getScratchValue()){
				activeDir = activeDir.getParent();
				if(activeDir == null) break;
			}
		}
		
		return arc;
	}
	
	/* ----- Getters ----- */
	
	public FileNode getSource(){
		return source;
	}
	
	public DirectoryNode getRoot(){
		return root;
	}
	
	/* ----- Setters ----- */
	
	public void setSource(FileNode src){
		source = src;
		if(src != null) root.setSourcePathForTree(src.getSourcePath());
	}
	
	/* ----- Writing ----- */
	
	/* ----- File Def ----- */
	
	private static U8ArchiveDef static_def;
	
	public static U8ArchiveDef getDefinition(){
		static_def = new U8ArchiveDef();
		return static_def;
	}
	
	public static class U8ArchiveDef extends ArchiveDef{
		
		private static String DEFO_ENG_DESC = "Nintendo Wii Standard Archive";
		public static int TYPE_ID = 0x55aa382d;

		private String desc;
		
		public U8ArchiveDef(){
			desc = DEFO_ENG_DESC;
		}

		public Collection<String> getExtensions() {
			List<String> list = new ArrayList<String>(2);
			list.add("arc");
			list.add("rarc");
			return list;
		}

		public String getDescription() {return desc;}
		public void setDescriptionString(String s) {desc = s;}
		public int getTypeID() {return TYPE_ID;}
		public String getDefaultExtension() {return "arc";}

		public DirectoryNode getContents(FileNode node) throws IOException, UnsupportedFileTypeException {
			if(node == null) return null;
			U8Arc arc = U8Arc.readArchive(node);
			return arc.getRoot();
		}
		
	}
	
}
