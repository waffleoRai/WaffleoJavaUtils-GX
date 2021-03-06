package waffleoRai_Containers.nintendo.cafe;

import java.io.ByteArrayInputStream;
import java.io.File;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import waffleoRai_Containers.nintendo.citrus.CitrusNCC;
import waffleoRai_Containers.nintendo.wiidisc.WiiTMD;
import waffleoRai_Containers.nintendo.wiidisc.WiiTicket;
import waffleoRai_Encryption.AES;
import waffleoRai_Encryption.FileCryptRecord;
import waffleoRai_Encryption.nintendo.NinCBCCryptRecord;
import waffleoRai_Encryption.nintendo.NinCryptTable;
import waffleoRai_Encryption.nintendo.NinCrypto;
import waffleoRai_Files.FileTypeDefNode;
import waffleoRai_Files.tree.DirectoryNode;
import waffleoRai_Files.tree.FileNode;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;
import waffleoRai_fdefs.nintendo.PowerGCSysFileDefs;

public class WiiUDisc {
	
	/*----- Constants -----*/
	
	public static final int SECTOR_SIZE = 0x8000;
	public static final long PART_TABLE_SECTOR = 3;
	
	public static final int MAGIC_PT = 0xcca6e67b;
	public static final int MAGIC_PART = 0xcc93a4f5;
	
	public static final String SI_PART_NAME = "SI";
	
	/*----- Static Variables -----*/

	private static byte[] common_key;
	
	/*----- Instance Variables -----*/
	
	private String src_path;
	private String dec_dir;
	
	private String gamecode_long;
	private byte[] gamekey;
	
	//Raw partition table
	private String[] part_names;
	private long[] part_offs;
	
	private WudPartition[] parts;
	private int si_part_idx; //Index of SI partition
	
	/*----- Construction/Parsing -----*/
	
	private WiiUDisc(){
		si_part_idx = -1;
	}
	
	public static WiiUDisc readWUD(String wud_path, byte[] game_key) throws UnsupportedFileTypeException, IOException{

		//Initial checks
		if(game_key == null) throw new UnsupportedFileTypeException("WUD Read Failed: Game key is required to read partition table.");
		
		//Instantiate
		WiiUDisc mydisc = new WiiUDisc();
		mydisc.src_path = wud_path;
		mydisc.gamekey = game_key;
		
		FileBuffer strbuff = FileBuffer.createBuffer(wud_path, 0, 0x20, true);
		mydisc.gamecode_long = strbuff.getASCII_string(0, 0x16);
		
		//Load decrypted partition table
		FileBuffer ptable = mydisc.loadPartitionTable();
		int pcount = ptable.intFromFile(28);
		mydisc.parts = new WudPartition[pcount];
		mydisc.part_names = new String[pcount];
		mydisc.part_offs = new long[pcount];
		ptable.setCurrentPosition(0x800);
		for(int i = 0; i < pcount; i++){
			mydisc.part_names[i] = ptable.getASCII_string(ptable.getCurrentPosition(), 0x1f);
			ptable.skipBytes(0x20);
			mydisc.part_offs[i] = Integer.toUnsignedLong(ptable.nextInt()) << 15;
			ptable.skipBytes(0x5c);
		}
		
		//Find and read SI
		for(int i = 0; i < pcount; i++){
			if(mydisc.part_names[i].equals(SI_PART_NAME)){
				mydisc.si_part_idx = i;
				break;
			}
		}
		if(mydisc.si_part_idx == -1) throw new UnsupportedFileTypeException("SI partition could not be found!");
		mydisc.loadSIPartition(false);
		//Decrypt SI to a temporary buffer...
		String temppath = FileBuffer.generateTemporaryPath("wud_si_dec");
		WudPartition si = mydisc.parts[mydisc.si_part_idx];
		si.setDecryptBufferPathStem(temppath);
		si.decryptToBuffer(null);
		DirectoryNode si_tree = si.getTree();
		//si_tree.printMeToStdErr(0);
		
		//Set keys and TMDs for the other partitions...
		for(int i = 0; i < pcount; i++){
			//System.err.println("Doing partition " + i);
			if(i == mydisc.si_part_idx) continue;
			
			//Temporary measure
			if(!mydisc.part_names[i].startsWith("GM")) continue;
			
			String pnum = String.format("%02d", i);
			FileNode tmd_node = si_tree.getNodeAt("/" + pnum + "/title.tmd");
			FileNode tik_node = si_tree.getNodeAt("/" + pnum + "/title.tik");
			
			if(tik_node != null){
				//Reject if common key is not present...
				if(common_key == null) continue; //Leave this partition null.
				System.err.println("Ticket found...");
			
				WiiTicket tik = new WiiTicket(tik_node.loadData(), 0);
				byte[] key = tik.decryptTitleKey(true);
				WiiTMD tmd = null;
				if(tmd_node != null) tmd = new WiiTMD(tmd_node.loadData(), 0, true);
				
				WudPartition part = 
						WudPartition.readWUDPartition(mydisc.src_path, mydisc.part_offs[i], 
								key, tmd);
				part.setPartitionName(mydisc.part_names[i]);
				mydisc.parts[i] = part;
			}
			else{
				//Assume game key?
				WudPartition part = 
						WudPartition.readWUDPartition(mydisc.src_path, mydisc.part_offs[i],
								mydisc.gamekey, null);
				part.setPartitionName(mydisc.part_names[i]);
				mydisc.parts[i] = part;
			}
		}
		
		//Delete SI fst temp....
		Files.deleteIfExists(Paths.get(temppath));
		
		return mydisc;
	}
	
	public static WiiUDisc loadPredecedWUD(String wud_path, byte[] game_key, String decdir) throws IOException, UnsupportedFileTypeException{

		if(game_key == null) throw new UnsupportedFileTypeException("WUD Read Failed: Game key is required to read partition table.");
		
		//Instantiate
		WiiUDisc mydisc = new WiiUDisc();
		mydisc.src_path = wud_path;
		mydisc.gamekey = game_key;
		mydisc.dec_dir = decdir;
		
		FileBuffer strbuff = FileBuffer.createBuffer(wud_path, 0, 0x20, true);
		mydisc.gamecode_long = strbuff.getASCII_string(0, 0x16);
		
		//Load decrypted partition table
		FileBuffer ptable = mydisc.loadPartitionTable();
		int pcount = ptable.intFromFile(28);
		mydisc.parts = new WudPartition[pcount];
		mydisc.part_names = new String[pcount];
		mydisc.part_offs = new long[pcount];
		ptable.setCurrentPosition(0x800);
		for(int i = 0; i < pcount; i++){
			mydisc.part_names[i] = ptable.getASCII_string(ptable.getCurrentPosition(), 0x1f);
			ptable.skipBytes(0x20);
			mydisc.part_offs[i] = Integer.toUnsignedLong(ptable.nextInt()) << 15;
			ptable.skipBytes(0x5c);
		}
		
		//Read in SI to get tickets and TMDs
		for(int i = 0; i < pcount; i++){
			if(mydisc.part_names[i].equals(SI_PART_NAME)){
				mydisc.si_part_idx = i;
				break;
			}
		}
		if(mydisc.si_part_idx == -1) throw new UnsupportedFileTypeException("SI partition could not be found!");
		mydisc.loadSIPartition(true);
		WudPartition si = mydisc.parts[mydisc.si_part_idx];
		DirectoryNode si_tree = si.getTree();
		
		for(int i = 0; i < pcount; i++){
			if(i == mydisc.si_part_idx) continue; //Already loaded.
			
			//Temporary measure
			if(!mydisc.part_names[i].startsWith("GM")) continue;
			
			String pnum = String.format("%02d", i);
			FileNode tmd_node = si_tree.getNodeAt("/" + pnum + "/title.tmd");
			FileNode tik_node = si_tree.getNodeAt("/" + pnum + "/title.tik");
			
			if(tik_node != null){
				//Reject if common key is not present...
				if(common_key == null) continue; //Leave this partition null.
				//System.err.println("Ticket found...");
			
				WiiTicket tik = new WiiTicket(tik_node.loadData(), 0);
				byte[] key = tik.decryptTitleKey(true);
				WiiTMD tmd = null;
				if(tmd_node != null) tmd = new WiiTMD(tmd_node.loadData(), 0, true);
				
				WudPartition part = 
						WudPartition.loadPredecedPartition(wud_path, mydisc.part_offs[i], key, tmd, mydisc.part_names[i], decdir);
				mydisc.parts[i] = part;
			}
			else{
				//Assume game key?
				WudPartition part = 
						WudPartition.loadPredecedPartition(mydisc.src_path, mydisc.part_offs[i],
								mydisc.gamekey, null, mydisc.part_names[i], decdir);
				mydisc.parts[i] = part;
			}
		}
		
		return mydisc;
	}
	
	/*----- Getters -----*/
	
	public static byte[] getCommonKey(){return common_key;}
	
	public byte[] getGameKey(){return gamekey;}
	
	public String getLongGamecode(){return this.gamecode_long;}
	
	public int getPartitionCount(){
		if(parts == null) return 0;
		return parts.length;
	}
	
	public DirectoryNode getFileTree(){
		
		DirectoryNode root = new DirectoryNode(null, "");
		
		//Partition table.
		boolean dec1 = false;
		FileNode fn = null;
		if(dec_dir != null){
			String pt_path = getPartblBufferPath(dec_dir);
			if(FileBuffer.fileExists(pt_path)){
				fn = new FileNode(root, "part_table.bin");
				fn.setSourcePath(pt_path);
				fn.setOffset(0);
				fn.setLength(FileBuffer.fileSize(pt_path));
				fn.setTypeChainHead(new FileTypeDefNode(PowerGCSysFileDefs.getWiiUPartTableDef()));
				dec1 = true;
			}
		}
		
		if(!dec1){
			//Table
			fn = new FileNode(root, "part_table.aes");
			fn.setOffset(PART_TABLE_SECTOR * SECTOR_SIZE);
			fn.setLength(SECTOR_SIZE);
			fn.setSourcePath(src_path);
			
			//Data
			fn = new FileNode(root, "wud.aes");
			long off = (PART_TABLE_SECTOR + 1) * SECTOR_SIZE;
			fn.setOffset(off);
			fn.setLength(FileBuffer.fileSize(src_path) - off);
			fn.setSourcePath(src_path);
			
			return root;
		}
		
		//Directory for each partition...
		if(parts == null) return root;
		for(int i = 0; i < parts.length; i++){
			if(parts[i] != null){
				DirectoryNode dir = parts[i].getTree();
				if(dir == null){
					//Just make an aes node...
					String pname = parts[i].getName();
					if(pname == null || pname.isEmpty()) pname = "part" + i;
					fn = new FileNode(root, pname + ".aes");
					fn.setOffset(parts[i].getWUDOffset());
					fn.setSourcePath(src_path);
					
					//Determine length
					long noff = FileBuffer.fileSize(src_path);
					for(int j = 0; j < parts.length; j++){
						long offj = part_offs[j];
						if((offj > parts[i].getWUDOffset()) && (offj < noff)){
							noff = offj;
						}
					}
					fn.setLength(noff - parts[i].getWUDOffset());
				}
				else{
					//Mount to root
					String pname = parts[i].getName();
					if(pname == null || pname.isEmpty()) pname = "part" + i;
					dir.setFileName(pname);
					dir.setParent(root);
					dir.setMetadataValue(CafeFST.METAKEY_PARTIDX, Integer.toString(i));
				}
			}
			else{
				//Partition probably just couldn't be decrypted.
				String pname = part_names[i];
				if(pname == null || pname.isEmpty()) pname = "part" + i;
				long poff = part_offs[i];
				//Get size from partition header...
				try{
					FileBuffer phdr = FileBuffer.createBuffer(src_path, poff, poff+16, true);
					long len = Integer.toUnsignedLong(phdr.intFromFile(0x8)) << 15;	
					fn = new FileNode(root, pname + ".aes");
					fn.setSourcePath(src_path);
					fn.setOffset(poff);
					fn.setLength(len);
				}
				catch(IOException x){
					x.printStackTrace();
				}
			}
		}
		
		return root;
	}
	
	public String getPartitionName(int idx){
		return part_names[idx];
	}
	
	public long getPartitionOffset(int idx){
		return part_offs[idx];
	}
	
	public long getPartitionSize(int idx) throws IOException{
		if(part_offs == null) return 0;
		if(idx < 0 || idx >= part_offs.length) return 0;
		long offset = part_offs[idx];
		
		FileBuffer phdr = new FileBuffer(src_path, offset, offset + 0x10, true);
		long sz = Integer.toUnsignedLong(phdr.intFromFile(0x8L)) << 15;
		
		return sz;
	}
	
	public WudPartition getPartition(int idx){
		return parts[idx];
	}
	
	public String getIconPath(){
		//Pull from first GM partition
		
		if(parts == null) return null;
		for(int i = 0; i < parts.length; i++){
			if(parts[i] == null) continue;
			if(parts[i].getName().startsWith("GM")){
				return "/" + parts[i].getName() + "/meta/iconTex.tga";
			}
		}
		
		return null;
	}
	
	public String getMetaXMLPath(){
		//Pull from first GM partition
		
		if(parts == null) return null;
		for(int i = 0; i < parts.length; i++){
			if(parts[i] == null) continue;
			if(parts[i].getName().startsWith("GM")){
				return "/" + parts[i].getName() + "/meta/meta.xml";
			}
		}
		
		return null;
	}
	
	/*----- Setters -----*/
	
	public static void setCommonKey(byte[] key){common_key = key;}
	
	public void setDecryptionBufferDir(String dirpath){
		dec_dir = dirpath;
		if(parts == null) return;
		for(int i = 0; i < parts.length; i++){
			if(parts[i] != null){
				String decpath = dirpath + File.separator + parts[i].getName();
				parts[i].setDecryptBufferPathStem(decpath);
			}
		}
	}
	
	/*----- Crypto -----*/
	
	public long getPartTableCryptID(){
		long val = Integer.toUnsignedLong(gamecode_long.hashCode());
		val = val << 32;
		val |= Integer.toUnsignedLong(gamecode_long.substring(0, 10).hashCode());
		return val;
	}
	
	public NinCryptTable genCryptTable(){
		//Spawn
		NinCryptTable ctbl = new NinCryptTable();
		
		//Partition table on main. Otherwise retrieve from partitions...
		long cid = getPartTableCryptID();
		FileCryptRecord r = new NinCBCCryptRecord(cid);
		r.setCryptOffset(0x18000);
		r.setIV(new byte[16]);
		int kidx = ctbl.addKey(NinCrypto.KEYTYPE_128, gamekey);
		r.setKeyType(NinCrypto.KEYTYPE_128);
		r.setKeyIndex(kidx);
		
		ctbl.addRecord(cid, r);
		
		//Partitions
		int pcount = part_offs.length;
		for(int i = 0; i < pcount; i++){
			if(parts[i] == null) continue;
			System.err.println("WiiUDisc.genCryptTable || Partition Added: " + part_names[i] + " @ 0x" + Long.toHexString(part_offs[i]));
			List<FileCryptRecord> rlist = parts[i].addToCryptTable(ctbl);
			for(FileCryptRecord rec : rlist) rec.setCryptOffset(rec.getCryptOffset() + part_offs[i]);
		}
		
		return ctbl;
	}
	
	public DirectoryNode getDirectFileTree(boolean low_fs){

		DirectoryNode root = new DirectoryNode(null, gamecode_long);
		root.generateGUID();
		
		//Header and part table if low_fs
		if(low_fs){
			FileNode fn = new FileNode(root, "discheader.bin");
			fn.setOffset(0x0);
			fn.setLength(0x200);
			fn.addTypeChainNode(new FileTypeDefNode(PowerGCSysFileDefs.getWiiUDiscHeaderDef()));
			
			fn = new FileNode(root, "ptbl.bin");
			fn.setOffset(0x18000);
			fn.setLength(0x8000);
			fn.addTypeChainNode(new FileTypeDefNode(PowerGCSysFileDefs.getWiiUPartTableDef()));
			fn.addEncryption(CafeCrypt.getStandardAESDef());
			fn.setMetadataValue(NinCrypto.METAKEY_CRYPTGROUPUID, Long.toHexString(getPartTableCryptID()));
			
		}
		
		//Add partitions...
		int pcount = part_offs.length;
		for(int i = 0; i < pcount; i++){
			if(parts[i] == null){
				System.err.println("WiiUDisc.getDirectFileTree || Warning: Partition " + part_names[i] + " @ 0x" + Long.toHexString(part_offs[i]) + " not decrypted!");
				//Find the size, then mount as a .aes node.
				try{
					FileBuffer phdr = FileBuffer.createBuffer(src_path, part_offs[i], part_offs[i] + 0x10, true);	
					long sz = Integer.toUnsignedLong(phdr.intFromFile(0x08)) << 15;
					FileNode fn = new FileNode(root, part_names[i] + ".aes");
					fn.setOffset(part_offs[i]);
					fn.setLength(sz);
				}
				catch(IOException x){x.printStackTrace();}
				continue;
			}
			DirectoryNode proot = parts[i].getDirectFileTree(part_offs[i], low_fs);
			proot.setFileName(part_names[i]);
			proot.setParent(root);
		}
		
		//Set tree path
		root.setSourcePathForTree(src_path);
		
		return root;
	}
	
	//----- Old stuff
	
	private String getPartblBufferPath(String dir){
		return dir + File.separator + "partbl_" + gamecode_long.replace("-", "") + ".bin";
	}
	
	private FileBuffer loadPartitionTable() throws IOException, UnsupportedFileTypeException{
	
		final long ptoff = PART_TABLE_SECTOR * SECTOR_SIZE; //Should be 0x18000
		AES aes = new AES(gamekey);
		byte[] enc = FileBuffer.createBuffer(src_path, ptoff, ptoff + SECTOR_SIZE, true).getBytes();
		byte[] dec = aes.decrypt(new byte[16], enc);
		FileBuffer ptbl = FileBuffer.wrap(dec);
		
		//Check result...
		if(ptbl.intFromFile(0) != MAGIC_PT) throw new UnsupportedFileTypeException("Decryption failed: Part Table magic number doesn't match! Found: 0x" + Integer.toHexString(ptbl.intFromFile(0)));
		
		//Check hash...
		byte[] refhash = new byte[20];
		ptbl.setCurrentPosition(8);
		for(int i = 0; i < 20; i++) refhash[i] = ptbl.nextByte();
		
		try {
			MessageDigest sha = MessageDigest.getInstance("SHA1");
			sha.update(ptbl.getBytes(0x800, 0x8000));
			byte[] hashbuff = sha.digest();
			if(!MessageDigest.isEqual(refhash, hashbuff)){
				System.err.println("WiiUDisc.loadPartitionTable() || Hash check failed!");
				System.err.println("Reference hash: " + CitrusNCC.printHash(refhash));
				System.err.println("Data hash: " + CitrusNCC.printHash(hashbuff));
				throw new FileBuffer.UnsupportedFileTypeException("Hash check failed!");
			}
		} 
		catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			throw new FileBuffer.UnsupportedFileTypeException("Hash generation failed!");
		}

		return ptbl;
	}
	
	private void loadSIPartition(boolean predec) throws IOException, UnsupportedFileTypeException{
		WudPartition si_part = null;
		if(!predec) si_part = WudPartition.readWUDPartition(src_path, part_offs[si_part_idx], gamekey, null);
		else si_part = WudPartition.loadPredecedPartition(src_path, part_offs[si_part_idx], gamekey, null, "SI", dec_dir);
		si_part.setPartitionName(part_names[si_part_idx]);
		parts[si_part_idx] = si_part;
	}
	
	public boolean decryptPartitionsTo(String dir, CafeCryptListener l) throws IOException, UnsupportedFileTypeException{
		setDecryptionBufferDir(dir);
		if(parts == null) return false;
		
		//Save partition table...
		String ptbl_path = getPartblBufferPath(dir);
		loadPartitionTable().writeFile(ptbl_path);
		
		boolean good = true;
		if(l != null) l.setPartitionCount(getPartitionCount());
		
		for(int i = 0; i < parts.length; i++){
			if(parts[i] == null){
				if(l != null) l.onPartitionComplete(i);
				continue;
			}
			
			try{
				parts[i].decryptToBuffer(l);
			}
			catch(Exception x){
				x.printStackTrace();
				good = false;
			}
			if(l != null) l.onPartitionComplete(i);
		}

		return good;
	}
	
	/*----- Util -----*/
	
	public static Map<String, String[]> readMetaXML(FileNode node) throws IOException, XMLStreamException{
		if(node == null) return null;
		Map<String, String[]> map = new HashMap<String, String[]>();
		
		XMLInputFactory xif = XMLInputFactory.newInstance();
		XMLStreamReader reader = xif.createXMLStreamReader(new ByteArrayInputStream(node.loadDecompressedData().getBytes()));
		
		LinkedList<String> elements = new LinkedList<String>();
		while(reader.hasNext()){
			if(reader.getEventType() == XMLStreamReader.START_ELEMENT){
				String str = reader.getLocalName();
				elements.push(str);
			}
			else if(reader.getEventType() == XMLStreamReader.END_ELEMENT){
				elements.pop();
			}
			else if(reader.getEventType() == XMLStreamReader.CHARACTERS){
				String str = reader.getText();
				String key = elements.getFirst();
				
				String lan = null;
				int idx = -1;
				if(key.startsWith("shortname_")){
					idx = 0;
					lan = key.substring(key.indexOf('_'));
				}
				else if (key.startsWith("longname_")){
					idx = 1;
					lan = key.substring(key.indexOf('_'));
				}
				else if(key.startsWith("publisher_")){
					idx = 1;
					lan = key.substring(key.indexOf('_'));
				}
				
				if(idx >= 0){
					String[] bnr = map.get(lan);
					if(bnr == null){
						bnr = new String[3];
						map.put(lan, bnr);
					}
					bnr[idx] = str;
				}
			}
			reader.next();
		}

		return map;
	}
	
}
