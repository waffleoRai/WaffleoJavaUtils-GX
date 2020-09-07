package waffleoRai_Containers.nintendo.citrus;

import java.io.File;
import java.io.IOException;

import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;
import waffleoRai_Files.tree.DirectoryNode;
import waffleoRai_Files.tree.FileNode;

//https://www.3dbrew.org/wiki/NCSD

public class CitrusNCSD {
	
	//TODO Add NCSD system files to tree?
	
	/*----- Constants -----*/
	
	public static final String MAGIC = "NCSD";
	
	public static final int MEDIA_UNIT_SIZE = 0x200;
	
	public static final int PART_FS_TYPE_NONE = 0;
	public static final int PART_FS_TYPE_NORMAL = 1;
	public static final int PART_FS_TYPE_FIRM = 2;
	public static final int PART_FS_TYPE_AGB_FIRM = 3;
	
	/*----- Instance Variables -----*/
	
	private byte[] rsa_sig; //Main RSA
	private long ncsd_size;
	
	private long media_id;
	private int[] part_fs_types;
	private int[] part_crypt_types;
	private long[][] part_locs;
	
	//CCI only
	private boolean isCCI;
	private byte[] exheader_hash;
	private int exheader_size;
	private long sec0_offset;
	private int[] part_flags;
	private long[] part_ids;
	
	private int card_info_bitmask;
	private int title_ver;
	private int card_rev;
	private byte[] seed_keyY;
	private byte[] seed_enccard;
	private byte[] seed_aesmac;
	private byte[] seed_nonce;
	
	//Partition Locations
	private CitrusNCC[] partitions;
	
	/*----- Construction/Parsing -----*/
	
	private CitrusNCSD(){}
	
	public static CitrusNCSD readNCSD(FileBuffer data, long offset, boolean isCart) throws UnsupportedFileTypeException, IOException{
		//Set byte order
		data.setEndian(false);
		
		//Instantiate
		CitrusNCSD ncsd = new CitrusNCSD();
		
		//Header
		long cpos = offset;
		ncsd.rsa_sig = data.getBytes(cpos, cpos + 0x100); cpos += 0x100;
		long moff = data.findString(cpos, cpos + 16, MAGIC);
		if(moff != cpos) throw new FileBuffer.UnsupportedFileTypeException("NCSD magic number was not found!");
		cpos += 4;
		ncsd.ncsd_size = Integer.toUnsignedLong(data.intFromFile(cpos)) << 9; cpos += 4;
		ncsd.media_id = data.longFromFile(cpos); cpos+=8;
		
		ncsd.part_fs_types = new int[8];
		for(int i = 0; i < 8; i++) ncsd.part_fs_types[i] = Byte.toUnsignedInt(data.getByte(cpos++));
		ncsd.part_crypt_types = new int[8];
		for(int i = 0; i < 8; i++) ncsd.part_crypt_types[i] = Byte.toUnsignedInt(data.getByte(cpos++));
		ncsd.part_locs = new long[8][2];
		for(int i = 0; i < 8; i++){
			ncsd.part_locs[i][0] = Integer.toUnsignedLong(data.intFromFile(cpos)) << 9; cpos+=4;
			ncsd.part_locs[i][1] = Integer.toUnsignedLong(data.intFromFile(cpos)) << 9; cpos+=4;
		}
		
		//CCI Extended Header
		if(isCart){
			ncsd.isCCI = true;
			ncsd.exheader_hash = data.getBytes(cpos, cpos+0x20); cpos += 0x20;
			ncsd.exheader_size = data.intFromFile(cpos); cpos+=4;
			ncsd.sec0_offset = Integer.toUnsignedLong(data.intFromFile(cpos)); cpos+=4;
			ncsd.part_flags = new int[8];
			for(int i = 0; i < 8; i++) ncsd.part_flags[i] = Byte.toUnsignedInt(data.getByte(cpos++));
			ncsd.part_ids = new long[8];
			for(int i = 0; i < 8; i++) {ncsd.part_ids[i] = data.longFromFile(cpos); cpos+=8;}

			cpos = offset + 0x204;
			ncsd.card_info_bitmask = data.intFromFile(cpos); cpos+=4;
			cpos += 0x108;
			ncsd.title_ver = Short.toUnsignedInt(data.shortFromFile(cpos)); cpos+=2;
			ncsd.card_rev = Short.toUnsignedInt(data.shortFromFile(cpos)); cpos+=2;
			
			cpos = offset + 0x1000;
			ncsd.seed_keyY = data.getBytes(cpos, cpos + 0x10); cpos+=0x10;
			ncsd.seed_enccard = data.getBytes(cpos, cpos + 0x10); cpos+=0x10;
			ncsd.seed_aesmac = data.getBytes(cpos, cpos + 0x10); cpos+=0x10;
			ncsd.seed_nonce = data.getBytes(cpos, cpos + 0xc); cpos+=0x10;
		}
		
		//Partitions
		ncsd.partitions = new CitrusNCC[8];
		for(int i = 0; i < 8; i++){
			if(ncsd.part_locs[i][1] > 0){
				FileNode loc = new FileNode(null, "");
				loc.setSourcePath(data.getPath());
				loc.setOffset(ncsd.part_locs[i][0]);
				loc.setLength(ncsd.part_locs[i][1]);
				
				//TODO pass it a FileNode instead.
				ncsd.partitions[i] = CitrusNCC.readNCC(data, ncsd.part_locs[i][0]);
				ncsd.partitions[i].setSource(loc);
				//System.err.println("Partition source set to: " + loc.getSourcePath() + " " + loc.getLocationString());
			}
		}
		
		return ncsd;
	}
	
	/*----- Getters -----*/
	
	public byte[] getRSASig(){return this.rsa_sig;}
	public byte[] getExtendedHeaderHash(){return this.exheader_hash;}
	
	public int getPartitionCount(){return partitions.length;}
	
	public CitrusNCC getPartition(int idx){
		return partitions[idx];
	}
		
	public long getPartitionOffset(int idx){
		return part_locs[idx][0];
	}
	
	public DirectoryNode getFileTree(){
		DirectoryNode root = new DirectoryNode(null, "");
		
		for(int i = 0; i < 8; i++){
			if(partitions[i] != null){
				DirectoryNode partroot = partitions[i].getFileTree();
				partroot.setFileName(Long.toHexString(partitions[i].getPartitionID()));
				partroot.setParent(root);
				//updateRawOffsets(partroot, part_locs[i][0]);
			}
		}
				
		return root;
	}
	
	/*----- Setters -----*/
	
	public void setSourcePath(String path){
		for(int i = 0; i < 8; i++){
			if(partitions[i] != null){
				FileNode loc = new FileNode(null, "");
				loc.setSourcePath(path);
				loc.setOffset(part_locs[i][0]);
				loc.setLength(part_locs[i][1]);
				
				partitions[i].setSource(loc);
			}
		}
	}

	public void setDecryptBufferDir(String path) throws IOException{
		for(int i = 0; i < 8; i++){
			if(partitions[i] != null){
				String part_buff_path = path + File.separator + "NCCH_" + Long.toHexString(partitions[i].getPartitionID()) + "_dec.bin";
				partitions[i].setDecBufferLocation(part_buff_path);
			}
		}
	}
	
	/*----- Crypto -----*/
	
	public boolean generateDecryptionBuffers(String bufferdir, CitrusCrypt crypto, boolean verbose) throws IOException, UnsupportedFileTypeException{
		boolean b = true;
		
		//Prepare. Set outpaths and KeyY
		setDecryptBufferDir(bufferdir);
		for(int i = 0; i < 8; i++){
			if(partitions[i] != null) partitions[i].setNCSD_keyY_seed(seed_keyY);
		}
		
		for(int i = 0; i < 8; i++){
			if(partitions[i] != null){
				CitrusNCC part = partitions[i];
				if(verbose){
					System.err.println("NCSD Partition Found: " + Long.toHexString(part.getPartitionID()));
					System.err.println("\tNow processing...");
				}
				
				b = part.refreshDecBuffer(crypto, verbose) && b;
			}
		}
		
		return b;
	}
	
	/*----- Definition -----*/
	
	/*----- Debug -----*/
	
	public void printToStdErr(){
		System.err.println("===== Citrus NCSD Container =====");
		System.err.println("Is CCI: " + isCCI);
		System.err.println("Media ID: " + Long.toHexString(media_id));
		System.err.println("Size: 0x" + Long.toHexString(ncsd_size));
		
		if(isCCI){
			System.err.println("Sector 0 Offset: 0x" + Long.toHexString(sec0_offset));
			System.err.println("ExHeader Size: 0x" + Long.toHexString(exheader_size));
			System.err.println("Card Info Bitmask: 0x" + String.format("%08x", card_info_bitmask));
			System.err.println("Title Version: " + title_ver);
			System.err.println("Card Revision: " + card_rev);
			
			System.err.print("Key Y Seed: ");
			for(int i = 0; i < seed_keyY.length; i++){
				System.err.print(String.format("%02x ", seed_keyY[i]));
			}
			System.err.println();
			
			System.err.print("Enc Card Seed: ");
			for(int i = 0; i < seed_enccard.length; i++){
				System.err.print(String.format("%02x ", seed_enccard[i]));
			}
			System.err.println();
			
			System.err.print("AES MAC Seed: ");
			for(int i = 0; i < seed_aesmac.length; i++){
				System.err.print(String.format("%02x ", seed_aesmac[i]));
			}
			System.err.println();
			
			System.err.print("Nonce Seed: ");
			for(int i = 0; i < seed_nonce.length; i++){
				System.err.print(String.format("%02x ", seed_nonce[i]));
			}
			System.err.println();
		}
		
		//Partition Info
		for(int i = 0; i < 8; i++){
			System.err.println();
			System.err.println("---- Partition " + i + " ----");
			System.err.println("FS Type: " + part_fs_types[i]);
			System.err.println("Crypto Type: " + part_crypt_types[i]);
			System.err.println("Offset: 0x" + Long.toHexString(part_locs[i][0]));
			System.err.println("Size: 0x" + Long.toHexString(part_locs[i][1]));
			
			if(isCCI){
				System.err.println("Flags: 0x" + String.format("%02x", part_flags[i]));
				System.err.println("ID: 0x" + Long.toHexString(part_ids[i]));
			}
			
			if(partitions[i] != null){
				partitions[i].printToStdErr();
			}
			else System.err.println("<Unused Partition>");
		}
		
	}
	
}
