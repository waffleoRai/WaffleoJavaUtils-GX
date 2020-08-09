package waffleoRai_Containers.nintendo.nx;

import waffleoRai_Utils.DirectoryNode;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;
import waffleoRai_Utils.FileNode;

public class NXPFS{
	
	/*----- Constants -----*/
	
	public static final String MAGIC = "PFS0";
	
	/*----- Instance Variables -----*/
	
	private long[] offsets;
	private long[] sizes;
	private String[] names;
	
	private long fdat_off;
	
	/*----- Construction/Parsing -----*/
	
	private NXPFS(){}
	
	public static NXPFS readPFS(FileBuffer src, long offset) throws UnsupportedFileTypeException{
		
		//Check magic
		long mpos = src.findString(offset, offset+0x10, MAGIC);
		if(mpos != offset) throw new UnsupportedFileTypeException("PFS magic number not found!");
		
		NXPFS pfs = new NXPFS();
		src.setEndian(false);
		src.setCurrentPosition(offset+4L);
		int fcount = src.nextInt();
		int stbl_sz = src.nextInt();
		src.skipBytes(4);
		
		pfs.offsets = new long[fcount];
		pfs.sizes = new long[fcount];
		pfs.names = new String[fcount];
		long stbl_off = offset + 0x10 + (fcount * 0x18);
		pfs.fdat_off = stbl_off + stbl_sz;
		
		for(int i = 0; i < fcount; i++){
			pfs.offsets[i] = src.nextLong();
			pfs.sizes[i] = src.nextLong();
			int soff = src.nextInt();
			src.skipBytes(4L);
			
			//Get name
			pfs.names[i] = src.getASCII_string(stbl_off + soff, '\0');
		}
		
		return pfs;
	}
	
	/*----- Getters -----*/
	
	public DirectoryNode getFileTree(){

		//All top level. Just iterate through and put in root.
		DirectoryNode root = new DirectoryNode(null, "");
		int fcount = offsets.length;
		for(int i = 0; i < fcount; i++){
			FileNode fn = new FileNode(root, names[i]);
			fn.setOffset(fdat_off + offsets[i]);
			fn.setLength(sizes[i]);
		}
		
		return root;
	}
	
	
	/*----- Setters -----*/

}
