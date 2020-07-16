package waffleoRai_Containers.nintendo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;

import waffleoRai_Containers.nintendo.wiidisc.WiiCryptListener;
import waffleoRai_Containers.nintendo.wiidisc.WiiPartition;
import waffleoRai_Containers.nintendo.wiidisc.WiiPartitionGroup;
import waffleoRai_Files.FileTypeDefNode;
import waffleoRai_Utils.DirectoryNode;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;
import waffleoRai_fdefs.nintendo.PowerGCSysFileDefs;
import waffleoRai_Utils.FileNode;

public class WiiDisc{
	
	//Documentation from: http://wiibrew.org/wiki/Wii_Disc
	
	/* ----- Constants ----- */
		
	public static final long OS_PARTITION_INFO_TBL = 0x40000;
	
	public static final int SIGTYPE_RSA4096 = 0x10000;
	public static final int SIGTYPE_RSA2048 = 0x10001;
	public static final int SIGTYPE_ECCB223 = 0x10002;
	
	public static final int SIGLEN_RSA4096 = 0x200;
	public static final int SIGLEN_RSA2048 = 0x100;
	public static final int SIGLEN_ECCB223 = 0x40;
	
	public static final int KEYTYPE_RSA4096 = 0;
	public static final int KEYTYPE_RSA2048 = 1;
	public static final int KEYTYPE_ECCB223 = 2;
	
	public static final int KEYLEN_RSA4096 = 0x200;// + 0x4 + 0x38;
	public static final int KEYLEN_RSA2048 = 0x100;// + 0x4 + 0x38;
	public static final int KEYLEN_ECCB223 = 60;// + 0 + 60;
	
	public static final int CONTENT_TYPE_NORMAL = 0x0001;
	public static final int CONTENT_TYPE_SHARED = 0x8001;
	
	public static final int PART_TYPE_DATA = 0;
	public static final int PART_TYPE_UPDATE = 1;
	public static final int PART_TYPE_CHANNEL = 2;
	
	public static final int PART_TICKET_SIZE = 0x2A4;
	public static final int H3_TABLE_SIZE = 0x18000;
	public static final int H3_TABLE_ENTRIES = H3_TABLE_SIZE/20;
	
	public static final int DATACLUSTER_IV_OFFSET = 0x3D0;
	public static final int DATACLUSTER_DATA_OFFSET = 0x400;
	public static final int DATACLUSTER_DATA_SIZE = 0x7C00;
	public static final int DATACLUSTER_SIZE = 0x8000;
	
	//public static final int[] TITLE_COMMON_KEY = {};
	
	public static final int KEY_SIZE_BYTES = 0x10;
	private static int[] TITLE_COMMON_KEY;
	
	/* ----- Instance Variables ----- */
	
	private GCWiiHeader oHeader;
	private WiiPartitionGroup[] oParts;
	
	private FileBuffer rawRegionInfo;
	
	/* ----- Construction ----- */
	
	private WiiDisc(){
		//oHeader = new Header();
		oParts = new WiiPartitionGroup[4];
	}
	
	/* ----- Parsing ----- */
	
	public static WiiDisc parseFromData(FileBuffer src, WiiCryptListener observer) throws IOException, UnsupportedFileTypeException{
		return parseFromData(src, observer, true);
	}
	
	public static WiiDisc parseFromData(FileBuffer src, WiiCryptListener observer, boolean auto_decrypt) throws IOException, UnsupportedFileTypeException
	{
		//It will start at 0x00 - if it's wrapped in a wbfs, must advance ptr!
		WiiDisc disc = new WiiDisc();
		disc.oHeader = GCWiiHeader.readHeader(src, 0);
		//disc.oHeader.printInfo();
		
		//Partition info - Right now reads up to 4.
		long cpos = OS_PARTITION_INFO_TBL;
		int[] pcounts = new int[4];
		int[] pptrs = new int[4];
		
		//General partition table
		//	Partition Table Entry [8]
		//		Total Sub-Partitions?? [4]
		//		Partition Info Table Offset (SRL 2) [4]
		for (int i = 0; i < 4; i++){
			pcounts[i] = src.intFromFile(cpos); cpos += 4;
			pptrs[i] = src.intFromFile(cpos) << 2; cpos += 4;
		}
		//for (int i = 0; i < 4; i++){System.err.println("Partition " + i + "|| Offset: 0x" + Integer.toHexString(pptrs[i]) + " | Count: " + pcounts[i]);}
		
		//Read partition info tables
		//	Partition Offset (SRL 2) [4]
		//	Partition Type Enum [4]
		for (int i = 0; i < 4; i++){
			if (pcounts[i] > 0){
				//System.err.println("WBFSImage.readWiiDisc || -DEBUG- Partition " + i + " has data!");
				WiiPartitionGroup part = new WiiPartitionGroup(pcounts[i]);
				disc.oParts[i] = part;
				cpos = pptrs[i];
				for(int j = 0; j < pcounts[i]; j++){
					long off = Integer.toUnsignedLong(src.intFromFile(cpos)) << 2; cpos += 4;
					int type = src.intFromFile(cpos); cpos += 4;
					part.addPartition(j, type, off);
				}
			}
		}
		//System.exit(2);
		
		//We'll just save the raw region info for dumping...
		final long REGION_INFO_OFFSET = 0x4E000;
		final int REGION_INFO_SIZE = 0x20;
		disc.rawRegionInfo = new FileBuffer(REGION_INFO_SIZE, true);
		cpos = REGION_INFO_OFFSET;
		for(int i = 0; i < REGION_INFO_SIZE; i++){disc.rawRegionInfo.addToFile(src.getByte(cpos)); cpos++;}
		
		//Count partitions for the observer
		if(observer != null){
			int pcount = 0;
			for(int i = 0; i < disc.oParts.length; i++){
				if (disc.oParts[i] != null){
					pcount += disc.oParts[i].getSubPartitions().size();
				}
			}
			observer.setPartitionCount(pcount);
		}
		
		//Now, parse the partitions...
		for(int i = 0; i < disc.oParts.length; i++){
			if (disc.oParts[i] != null) disc.oParts[i].readPartion(src, observer, auto_decrypt);
		}
		
		return disc;
	}
	
	public static void setCommonKey(String binpath, long stPos) throws UnsupportedFileTypeException, IOException
	{
		long fsz = FileBuffer.fileSize(binpath);
		if(fsz - stPos < KEY_SIZE_BYTES) throw new FileBuffer.UnsupportedFileTypeException("WiiDisc.setCommonKey || File not long enough to contain key.");
		FileBuffer buffer = new FileBuffer(binpath, stPos, stPos + KEY_SIZE_BYTES);
		TITLE_COMMON_KEY = new int[KEY_SIZE_BYTES];
		for(int i = 0; i < KEY_SIZE_BYTES; i++)
		{
			TITLE_COMMON_KEY[i] = Byte.toUnsignedInt(buffer.getByte(i));
		}
	}
	
	public static void setCommonKey(byte[] key){
		if(key.length < KEY_SIZE_BYTES) return;
		TITLE_COMMON_KEY = new int[KEY_SIZE_BYTES];
		for(int i = 0; i < KEY_SIZE_BYTES; i++){
			TITLE_COMMON_KEY[i] = Byte.toUnsignedInt(key[i]);
		}
	}
	
	/* ----- Info Dumping ----- */
	
	private boolean dumpRawDiscPieces(String directory) throws IOException
	{
		//header.bin, region.bin
		String apath = directory + File.separator + "header.bin";
		FileBuffer afile = oHeader.serializeHeader();
		afile.writeFile(apath);
		
		apath = directory + File.separator + "region.bin";
		rawRegionInfo.writeFile(apath);
		
		return true;
	}
	
	public boolean dumpRawDecrypted(String directory) throws IOException
	{
		String gamecode = oHeader.getFullGameCode();
		String base_dir = directory + File.separator + gamecode;
		Files.createDirectories(Paths.get(base_dir));
		
		String disc_dir = base_dir + File.separator + "disc";
		Files.createDirectories(Paths.get(disc_dir));
		if(!dumpRawDiscPieces(disc_dir)) return false;
		
		boolean b = true;
		for(int i = 0; i < oParts.length; i++)
		{
			if(oParts[i] != null)
			{
				String pdir = base_dir + File.separator + "PARTITION_" + i+1;
				b = b && oParts[i].dumpRaw(pdir);
			}
		}
		
		return b;
	}
	
	public boolean dumpDisc(String directory) throws IOException
	{
		String gamecode = oHeader.getFullGameCode();
		String base_dir = directory + File.separator + gamecode;
		Files.createDirectories(Paths.get(base_dir));
		
		String disc_dir = base_dir + File.separator + "disc";
		Files.createDirectories(Paths.get(disc_dir));
		if(!dumpRawDiscPieces(disc_dir)) return false;
		
		boolean b = true;
		for(int i = 0; i < oParts.length; i++)
		{
			if(oParts[i] != null)
			{
				String pdir = base_dir + File.separator + "PARTITION_" + i+1;
				b = b && oParts[i].dumpPartition(pdir);
			}
		}
		
		return b;
	}
	
	public GCWiiHeader getHeader()
	{
		return oHeader;
	}
	
	public int getPartitionCount()
	{
		return oParts.length;
	}
	
	public WiiPartitionGroup getPartition(int index){
		return oParts[index];
	}
	
	public static int[] getCommonKey(){
		return WiiDisc.TITLE_COMMON_KEY;
	}
	
	public static String generateDecryptedPartitionPath(String stem, int pgroup, int p){
		return stem + String.format("%02d_%02d", pgroup, p) + ".wiip";
	}
	
	public DirectoryNode getDiscTree(String wiiimg_path, String dec_buff_stem) throws IOException{

		DirectoryNode root = new DirectoryNode(null, "");
		
		//Overarching sys files... which is the header and part table
		FileNode fn = new FileNode(root, "header.bin");
		fn.setSourcePath(wiiimg_path); fn.setOffset(0); fn.setLength(0x440);
		fn.setTypeChainHead(new FileTypeDefNode(PowerGCSysFileDefs.getHeaderDef()));
		fn = new FileNode(root, "parttable.bin");
		fn.setSourcePath(wiiimg_path); fn.setOffset(OS_PARTITION_INFO_TBL); fn.setLength(0xE000);
		fn.setTypeChainHead(new FileTypeDefNode(PowerGCSysFileDefs.getPartTableDef()));
		fn = new FileNode(root, "region.bin");
		fn.setSourcePath(wiiimg_path); fn.setOffset(0x4E000); fn.setLength(32);
		fn.setTypeChainHead(new FileTypeDefNode(PowerGCSysFileDefs.getRegInfoDef()));
		
		if(oParts == null) return root;
		
		for(int g = 0; g < oParts.length; g++){
			WiiPartitionGroup grp = oParts[g];
			if(grp != null){
				DirectoryNode gnode = new DirectoryNode(root, String.format("%02d", g));
				
				List<WiiPartition> parts = grp.getSubPartitions();
				int p = 0;
				for(WiiPartition part: parts){
					//DirectoryNode pnode = new DirectoryNode(gnode, String.format("part%02d", p));
					String decpath = generateDecryptedPartitionPath(dec_buff_stem, g, p);
					DirectoryNode pnode = part.getPartitionTree(wiiimg_path, decpath);
					pnode.setFileName(String.format("part%02d", p++));
					pnode.setParent(gnode);
				}
			}
		}
		
		return root;
	}
	
	public List<long[]> getEncryptedIntervals(){
		List<long[]> list = new LinkedList<long[]>();
		for(int i = 0; i < 4; i++){
			if(oParts[i] != null){
				List<WiiPartition> parts = oParts[i].getSubPartitions();
				for(WiiPartition part : parts){
					long off = part.getAddress() + part.getDataOffset();
					long edoff = off + part.getDataSize();
					list.add(new long[]{off, edoff});
				}
			}
		}
		
		return list;
	}
	
	/* ----- Cleanup ----- */
	
	public void deleteParsingTempFiles() throws IOException{
		for(int i = 0; i < oParts.length; i++){
			if (oParts[i] != null) oParts[i].deleteTempFiles();
		}
	}
	
}
