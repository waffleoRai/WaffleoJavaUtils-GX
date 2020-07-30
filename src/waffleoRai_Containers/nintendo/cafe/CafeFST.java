package waffleoRai_Containers.nintendo.cafe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import waffleoRai_Utils.DirectoryNode;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;
import waffleoRai_Utils.FileNode;

public class CafeFST {
	
	/*----- Constants -----*/
	
	public static final int MAGIC = 0x46535400;
	public static final String METAKEY_IVLO = "WUDPART_IVLO";
	public static final String METAKEY_PARTIDX = "WUDPART_IDX";
	
	/*----- Instance Variables -----*/
	
	private int miniblock_size;
	
	private ArrayList<ClusterInfo> clusters;
	private List<FileTableEntry> file_entries;
	
	/*----- Structs -----*/
	
	public static class ClusterInfo{
		
		public long offset;
		public long size;
		public long ownerID;
		public int groupID;
		
	}
	
	public static class FileTableEntry{
		
		public boolean isDir;
		public String name;
		public long offset;
		public long size;
		public int flags;
		public int cluster_idx;
		
	}
	
	/*----- Construction/Parsing -----*/
	
	private CafeFST(){
		miniblock_size = 0x20;
		clusters = new ArrayList<ClusterInfo>();
		file_entries = new LinkedList<FileTableEntry>();
	}
	
	public static CafeFST readWiiUFST(FileBuffer data, long stpos) throws UnsupportedFileTypeException{
		//Dummy checks
		if(data == null || stpos < 0) return null;
		
		//Check magic
		data.setEndian(true);
		int magic = data.intFromFile(stpos);
		if(magic != MAGIC) throw new UnsupportedFileTypeException("Wii U FST magic number not found!");
		
		//Instantiate
		CafeFST fst = new CafeFST();
		data.setCurrentPosition(stpos+4);
		fst.miniblock_size = data.nextInt();
		int ccount = data.nextInt();
		
		//Read secondary headers...
		data.setCurrentPosition(stpos + 0x20);
		for(int i = 0; i < ccount; i++){
			ClusterInfo ci = new ClusterInfo();
			fst.clusters.add(ci);
			
			ci.offset = Integer.toUnsignedLong(data.nextInt());
			ci.size = Integer.toUnsignedLong(data.nextInt());
			ci.ownerID = data.nextLong();
			ci.groupID = data.nextInt();
			data.skipBytes(12);
		}
		
		//Read dir/file table (raw)
		//Get the entry count from the root record...
		int ecount = data.intFromFile(data.getCurrentPosition() + 8);
		long ntoff = data.getCurrentPosition() + (ecount << 4);
		for(int i = 0; i < ecount; i++){
			FileTableEntry fe = new FileTableEntry();
			fst.file_entries.add(fe);
			
			if(data.nextByte() != 0) fe.isDir = true;
			long noff = Integer.toUnsignedLong(data.nextShortish());
			fe.name = data.getASCII_string(ntoff + noff, '\0');
			fe.offset = Integer.toUnsignedLong(data.nextInt()); //This is RAW, NOT scaled!
			fe.size = Integer.toUnsignedLong(data.nextInt());
			fe.flags = Short.toUnsignedInt(data.nextShort());
			fe.cluster_idx = Short.toUnsignedInt(data.nextShort());
			
			//System.err.println("F/D Entry found: " + fe.name);
		}
		
		return fst;
	}
	
	/*----- Getters -----*/
	
	public long getClusterOffset(int cidx){
		if(clusters == null) return -1;
		if(cidx >= clusters.size()) return -1;
		return clusters.get(cidx).offset << 15;
	}
	
	public long getClusterSize(int cidx){
		if(clusters == null) return -1;
		if(cidx >= clusters.size()) return -1;
		return clusters.get(cidx).size << 15;
	}
	
	public int getClusterCount(){
		return clusters.size();
	}
	
	private DirectoryNode buildDirectory(DirectoryNode parent, int didx, FileTableEntry[] arr){

		FileTableEntry dentry = arr[didx];
		int children = (int)(dentry.size-didx-1);
		DirectoryNode dir = new DirectoryNode(parent, dentry.name);
		
		int remain = children;
		int idx = didx+1;
		while(remain > 0){
			//System.err.println(dentry.name + " reading child @ " + idx);
			FileTableEntry centry = arr[idx];
			if(centry.isDir){
				buildDirectory(dir, idx, arr);
				int gchildren = (int)(centry.size-idx);
				idx += gchildren;
				remain -= gchildren;
			}
			else{
				FileNode fn = new FileNode(dir, centry.name);
				fn.setUID(Integer.toUnsignedLong(idx));
				long offset = centry.offset * miniblock_size;
				String ivlo = Long.toHexString(offset >>> 16);
				fn.setMetadataValue(METAKEY_IVLO, ivlo);
				
				//ClusterInfo c = clusters.get(centry.cluster_idx);
				//offset += c.offset * 0x8000;
				fn.setSourcePath(String.format("%04d", centry.cluster_idx));
				fn.setOffset(offset);
				fn.setLength(centry.size);
				idx++; remain--;
			}
		}
		
		return dir;
	}
	
	public DirectoryNode getTree(){
		
		int ecount = file_entries.size();
		FileTableEntry[] earr = new FileTableEntry[ecount];
		int i = 0;
		for(FileTableEntry e : file_entries){earr[i++] = e;}
		DirectoryNode root = buildDirectory(null, 0, earr);

		return root;
	}
	
	public DirectoryNode getTree(boolean dec_optimize){
		
		int ecount = file_entries.size();
		FileTableEntry[] earr = new FileTableEntry[ecount];
		int i = 0;
		for(FileTableEntry e : file_entries){earr[i++] = e;}
		DirectoryNode root = buildDirectory(null, 0, earr);
		
		if(dec_optimize){
			Map<Long, Long> omap = new HashMap<Long, Long>();
			List<FileNode> offlist = getList(true);
			for(FileNode fn : offlist){
				omap.put(fn.getUID(), fn.getOffset());
				//System.err.println("UID: " + fn.getUID() + ", Offset: 0x" + Long.toHexString(fn.getOffset()));
			}
			Collection<FileNode> allfiles = root.getAllDescendants(false);
			for(FileNode fn : allfiles){
				Long noff = omap.get(fn.getUID());
				if(noff == null) continue;
				fn.setOffset(noff);
			}
		}
		
		return root;
	}
	
	public List<FileNode> getList(boolean dec_optimize){
		//Convert FileTableEntries to FileNodes
		List<FileNode> list = new LinkedList<FileNode>();
		long idx = 0;
		for(FileTableEntry e : file_entries){
			if(e.isDir){
				idx++;
				continue;
			}
			FileNode fn = new FileNode(null, e.name);
			long offset = e.offset * miniblock_size;
			fn.setMetadataValue(METAKEY_IVLO, Long.toHexString(offset >>> 16));
			
			//Get cluster offset
			ClusterInfo c = clusters.get(e.cluster_idx);
			offset += c.offset << 15;
			
			fn.setOffset(offset);
			fn.setLength(e.size);
			fn.setUID(idx++);
			list.add(fn);
		}
		
		//Sort by offset
		FileNode.setSortOrder(FileNode.SORT_ORDER_LOCATION);
		Collections.sort(list);
		FileNode.setSortOrder(FileNode.SORT_ORDER_NORMAL);
		
		//Move nodes closer together if optimization is requested.
		if(dec_optimize){
			long cpos = 0;
			for(FileNode fn : list){
				//System.err.println(fn.getFullPath() + " part offset: 0x" + Long.toHexString(fn.getOffset()));
				fn.setOffset(cpos);
				cpos += fn.getLength();
				//Pad to 0x10
				while((cpos % 0x10) != 0) cpos++;
			}
		}
		
		return list;
	}
	
	/*----- Setters -----*/
	
	/*----- Debug -----*/
	
	public void printClustersToStderr(){
		System.err.println("===== CafeOS FST =====");
		System.err.println("-- Clusters -- ");
		int i = 0;
		for(ClusterInfo c : clusters){
			System.err.print("Cluster " + i++ + "\t0x" + Long.toHexString(c.offset) + "\t0x" + Long.toHexString(c.size));
			System.err.print("\t0x" + Long.toHexString(c.offset << 15) + "\t0x" + Long.toHexString(c.size << 15));
			System.err.println();
		}
	}
	
	public void printToStderr(){

		System.err.println("===== CafeOS FST =====");
		System.err.println("-- Clusters -- ");
		int i = 0;
		for(ClusterInfo c : clusters){
			System.err.println("Cluster " + i++ + "\t0x" + Long.toHexString(c.offset) + "\t0x" + Long.toHexString(c.size));
		}
		System.err.println("");
		System.err.println("-- Nodes -- ");
		for(FileTableEntry fe : file_entries){
			System.err.print(fe.name + "\t");
			System.err.print(fe.isDir + "\t");
			System.err.print("0x" + Long.toHexString(fe.offset) + "\t");
			System.err.print("0x" + Long.toHexString(fe.offset << 5) + "\t");
			System.err.print((fe.offset << 5) + "\t");
			System.err.print("0x" + Long.toHexString(fe.size) + "\t");
			System.err.print(fe.cluster_idx + "\n");
		}
		
	}

}
