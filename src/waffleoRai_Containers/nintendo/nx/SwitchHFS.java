package waffleoRai_Containers.nintendo.nx;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import waffleoRai_Encryption.FileCryptRecord;
import waffleoRai_Encryption.FileCryptTable;
import waffleoRai_Files.FileTypeDefNode;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;
import waffleoRai_fdefs.nintendo.NXSysDefs;
import waffleoRai_Files.tree.DirectoryNode;
import waffleoRai_Files.tree.FileNode;

public class SwitchHFS implements NXContainer{

	/*----- Constants -----*/
	
	public static final String MAGIC = "HFS0";
	
	/*----- Structs -----*/
	
	protected static class FileEntry{
		
		public long offset;
		public long size;
		public String name;
		public long hashSize;
		public byte[] hash; //SHA-256
		
	}
	
	/*----- Instance Variables -----*/
	
	private String src_path;
	
	private long base_addr; //Relative to source file
	private long fdat_off; //Relative to HFS start
	private List<FileEntry> filelist;
	
	private NXContainer[] contents;
	private DirectoryNode root;
	
	/*----- Construction/Parsing -----*/
	
	public SwitchHFS(long addr, int entry_count){
		base_addr = addr;
		filelist = new ArrayList<FileEntry>(entry_count+1);
		//contents = new HashMap<String, NXContainer>();
		contents = new NXContainer[entry_count];
	}

	public static SwitchHFS readHFS(FileBuffer data, long offset) throws UnsupportedFileTypeException, IOException{
		if(data == null) throw new IOException("Provided data reference is null!");
		if(data.findString(offset, offset+0x10, MAGIC) != offset) throw new UnsupportedFileTypeException("HFS0 magic number not found!");
		//System.err.println("SwitchHFS.readHFS || HFS0 Offset: 0x" + Long.toHexString(offset));
		
		data.setCurrentPosition(offset+4L);
		int fcount = data.nextInt();
		long strtbl_sz = Integer.toUnsignedLong(data.nextInt());
		data.skipBytes(4);
		
		long soff = offset + 0x10 + (fcount * 0x40);
		long base = soff + strtbl_sz; //Base address of files...
		SwitchHFS hfs = new SwitchHFS(base, fcount);
		hfs.base_addr = base;
		hfs.fdat_off = 0x10 + (fcount * 0x40) + strtbl_sz;
		hfs.src_path = data.getPath();
		//System.err.println("SwitchHFS.readHFS || HFS0 File Data Offset: 0x" + Long.toHexString(hfs.base_addr));
		//System.err.println("SwitchHFS.readHFS || HFS0 Source Path: " + hfs.src_path);
		//System.err.println("SwitchHFS.readHFS || HFS0 Strtbl Offset: 0x" + Long.toHexString(soff));
		
		for(int i = 0; i < fcount; i++){
			FileEntry fe = new FileEntry();
			fe.offset = data.nextLong();
			fe.size = data.nextLong();
			long stroff = Integer.toUnsignedLong(data.nextInt());
			fe.hashSize = Integer.toUnsignedLong(data.nextInt());
			data.skipBytes(8);
			fe.hash = new byte[32];
			for(int j = 0; j < 32; j++) fe.hash[j] = data.nextByte();
			
			//Get name
			fe.name = data.getASCII_string(soff + stroff, '\0');
			
			hfs.filelist.add(fe);
			
			//System.err.println("\tFile Entry Found: " + fe.name);
			//System.err.println("\tLocation: 0x" + Long.toHexString(fe.offset) + " - 0x" + 
				//		Long.toHexString(fe.offset + fe.size));
			//System.err.println("\tName string offset: 0x" + Long.toHexString(soff + stroff));
		}
		if(fcount > 0){
			hfs.contents = new NXContainer[fcount];
			
			//Go ahead and read any internal HFS, since fs is plaintext
			int i = -1;
			for(FileEntry fe : hfs.filelist){
				i++;
				//System.err.println("SwitchHFS.readHFS || Now reading " + fe.name);
				if(fe.name.endsWith(".nca")){
					//Try to read header.
					//System.err.println("SwitchHFS.readHFS || NCA found @ 0x" + Long.toHexString(fe.offset));
					try{
						SwitchNCA nca = SwitchNCA.readNCA(data, base + fe.offset);
						if(nca != null){
							hfs.contents[i] = nca;
						}
					}
					catch(UnsupportedFileTypeException x){
						//Can't read at this time (probably decryption failed)
						continue;
					}
				}
				try{
					SwitchHFS hfs_inner = SwitchHFS.readHFS(data, base + fe.offset);
					hfs.contents[i] = hfs_inner;
					//System.err.println("SwitchHFS.readHFS || HFS read @ 0x" + Long.toHexString(fe.offset));
				}
				catch(UnsupportedFileTypeException x){
					//Probably not an HFS
					continue;
				}
			}	
		}
		
		return hfs;
	}
	
	public boolean buildFileTree(FileBuffer data, long offset, int complexity_level){

		root = new DirectoryNode(null, "");
		DirectoryNode localroot = root;
		if(complexity_level == 0){
			//Do header
			FileNode fn = new FileNode(root, "hfsheader.bin");
			fn.setOffset(0L);
			fn.setLength(fdat_off);
			fn.setTypeChainHead(new FileTypeDefNode(NXSysDefs.getHFSHeaderDef()));
			
			localroot = new DirectoryNode(root, "HFS");
		}
		
		int i = -1;
		boolean b = true;
		long fbase = offset + fdat_off;
		for(FileEntry fe : filelist){
			i++;
			
			//System.err.println("SwitchHFS.buildFileTree || Now parsing " + fe.name);
			if(contents[i] != null){
				//It's a container.
				boolean s = contents[i].buildFileTree(data, fbase + fe.offset, complexity_level);
				if(s){
					DirectoryNode croot = contents[i].getFileTree();
					croot.incrementTreeOffsetsBy(fdat_off + fe.offset);
					
					if(complexity_level == NXCartImage.TREEBUILD_COMPLEXITY_MERGED){
						if(contents[i] instanceof SwitchHFS){
							//Mount as is
							croot.setFileName(fe.name);
							croot.setParent(localroot);
						}
						else{
							//Strip children and mount them
							List<FileNode> children = croot.getChildren();
							for(FileNode child : children){
								child.setParent(localroot);
							}
						}
					}
					else{
						croot.setFileName(fe.name);
						croot.setParent(localroot);		
					}
				}
				else{
					b = false;
					FileNode fn = new FileNode(localroot, fe.name);
					fn.setOffset(fdat_off + fe.offset);
					fn.setLength(fe.size);
				}
			}
			else{
				FileNode fn = new FileNode(localroot, fe.name);
				fn.setOffset(fe.offset);
				fn.setLength(fe.size);
			}
			
		}
		
		return b;
	}
	
	/*----- Getters -----*/
	
	public DirectoryNode getFileTree(){
		return root;
	}
	
	public Collection<FileCryptRecord> addEncryptionInfo(FileCryptTable table){

		List<FileCryptRecord> rlist = new LinkedList<FileCryptRecord>();
		
		int i = -1;
		for(FileEntry fe : filelist){
			i++;
			
			if(contents[i] != null){
				//System.err.println("Doing " + fe.name);
				Collection<FileCryptRecord> clist = contents[i].addEncryptionInfo(table);
				if(clist != null){
					rlist.addAll(clist);
					for(FileCryptRecord r : clist){
						r.setCryptOffset(r.getCryptOffset() + fe.offset + fdat_off);
					}
				}
			}
		}
		
		return rlist;
	}
	
	public SwitchNCA getNCAByName(String nca_name){
		//Scans full file system for correct NCA
		int i = 0;
		for(FileEntry fe : filelist){
			//System.err.println("FE Name: " + fe.name + " | Search Name: " + nca_name);
			if(fe.name.equals(nca_name)){
				//Nab container and return
				//System.err.println("Match!");
				if(contents[i] == null) return null;
				else return (SwitchNCA)contents[i];
			}
			else if(contents[i] != null && (contents[i] instanceof SwitchHFS)){
				//Recursively search
				SwitchNCA hit = ((SwitchHFS)contents[i]).getNCAByName(nca_name);
				if(hit != null) return hit;
			}
			i++;
		}
		
		return null;
	}
	
	public FileNode getNCANodeByName(String nca_name){
		int i = 0;
		for(FileEntry fe : filelist){
			//System.err.println("FE Name: " + fe.name + " | Search Name: " + nca_name);
			if(fe.name.equals(nca_name)){
				//Determine location and create node...
				//System.err.println("Match!");
				FileNode ncanode = new FileNode(null, "ncanode_" + nca_name);
				ncanode.setSourcePath(src_path);
				ncanode.setOffset(fe.offset); //Relative to HFS!
				ncanode.setLength(fe.size);
				return ncanode;
			}
			else if(contents[i] != null && (contents[i] instanceof SwitchHFS)){
				//Recursively search
				FileNode fn = ((SwitchHFS)contents[i]).getNCANodeByName(nca_name);
				if(fn != null){
					fn.setOffset(fn.getOffset() + fe.offset);
					return fn;
				}
			}
			i++;
		}
		
		return null;
	}
	
	/*----- Setters -----*/
	
	/*----- Crypto -----*/
	
	public boolean unlock(NXCrypt cryptstate){

		boolean b = true;
		for(int i = 0; i < contents.length; i++){
			if(contents[i] != null){
				//System.err.println("Now unlocking: " + filelist.get(i).name);
				b = b && contents[i].unlock(cryptstate);
			}
		}
		
		return b;
	}
	
	/*----- Debug -----*/
	
	public void printInfo(Writer out) throws IOException{
		out.write("-------- HAC File System Container --------\n");
		out.write("File Data Offset: 0x" + Long.toHexString(fdat_off) +"\n");
		
		out.write("\n");
		int i = 0;
		for(FileEntry fe : filelist){
			out.write("~> " + fe.name + "\n");
			NXContainer c = contents[i++];
			if(c != null) c.printInfo(out);
			else out.write("<NULL HFS Slot>\n");
		}
	}
	
	public void extractRawNCAsTo(String dirpath) throws IOException{
		if(filelist == null) return;
		if(!FileBuffer.fileExists(dirpath)) Files.createDirectories(Paths.get(dirpath));
		
		String hashpath = dirpath + File.separator + "sha256.txt";
		BufferedWriter bw = new BufferedWriter(new FileWriter(hashpath));
		
		int i = 0;
		for(FileEntry e : filelist){
			
			if(e.name.endsWith(".nca")){
				String epath = dirpath + File.separator + e.name;
				
				long st = base_addr + e.offset;
				long ed = st + e.size;
				System.err.println("Extracting from 0x" + Long.toHexString(st) + " - 0x" + Long.toHexString(ed));
				System.err.println("Extracting to " + epath);
				
				StringBuilder sb = new StringBuilder(80);
				for(int j = 0; j < 32; j++) sb.append(String.format("%02x", e.hash[j]));
				bw.write(e.name + "\t" + sb.toString() + "\n");
				
				BufferedInputStream bis = new BufferedInputStream(new FileInputStream(src_path));
				bis.skip(base_addr + e.offset);
				BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(epath));
				
				long remaining = e.size;
				while(remaining-- > 0) bos.write(bis.read());

				bos.close();
				bis.close();	
			}
			else{
				//Check if hfs...
				NXContainer child = contents[i];
				
				long st = base_addr + e.offset;
				long ed = st + e.size;
				System.err.println("Non-NCA child: " + e.name);
				System.err.println("@ 0x" + Long.toHexString(st) + " - 0x" + Long.toHexString(ed));
				
				if(child != null && child instanceof SwitchHFS){
					((SwitchHFS)child).extractRawNCAsTo(dirpath + File.separator + e.name);
				}
			}
			i++;
		}
		bw.close();
	}
	
}
