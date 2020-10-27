package waffleoRai_Containers.nintendo.wiidisc;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import waffleoRai_Containers.nintendo.GCWiiDisc;
import waffleoRai_Containers.nintendo.WiiDisc;
import waffleoRai_Containers.nintendo.wiidisc.WiiCrypt.WiiCBCDecMethod;
import waffleoRai_Encryption.AES;
import waffleoRai_Encryption.FileCryptRecord;
import waffleoRai_Encryption.nintendo.NinCBCCryptRecord;
import waffleoRai_Encryption.nintendo.NinCryptTable;
import waffleoRai_Encryption.nintendo.NinCrypto;
import waffleoRai_Files.FileNodeModifierCallback;
import waffleoRai_Files.FileTypeDefNode;
import waffleoRai_Utils.EncryptedFileBuffer;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;
import waffleoRai_fdefs.nintendo.PowerGCSysFileDefs;
import waffleoRai_fdefs.nintendo.WiiAESDef;
import waffleoRai_Utils.MultiFileBuffer;
import waffleoRai_Files.tree.DirectoryNode;
import waffleoRai_Files.tree.FileNode;

public class WiiPartition {
	
	//public static final int DECRYPT_THREADS = 4;
	
	/* --- Instance Variables --- */
	
	private String pTempFile; //Don't want to hold all that data in mem!
	
	private int ePartType;
	private long iAddress;
	
	private WiiTicket oTicket;
	private WiiTMD oTMD;
	private byte[][] aH3Table;
	
	private long iTMDSize;
	private long iTMDOff;
	private long iCCSize;
	private long iCCOff;
	private long iDataSize;
	private long iDataOff;
	private long iH3Off;
	
	private byte[] aTitleKey;
	private AES oDecryptor;
	private List<WiiCertificate> lCertChain;
	
	//private volatile long sectors_read_counter;
	
	private GCWiiDisc decryptedPart;
	
	/* --- Construction/Parsing --- */
	
	public WiiPartition(int type, long staddr) throws IOException{
		ePartType = type;
		iAddress = staddr;
		Random r = new Random();
		pTempFile = FileBuffer.getTempDir() + File.separator + "wiidisc_partition_" + Integer.toHexString(r.nextInt()) + ".tmp";
		lCertChain = new LinkedList<WiiCertificate>();
		aH3Table = new byte[WiiDisc.H3_TABLE_ENTRIES][20];
	}
	
	public void readFromDisc(FileBuffer discData, WiiCryptListener observer, boolean auto_decrypt) throws IOException, UnsupportedFileTypeException{
		//If they are relative to something like the partition, this needs to be changed!
		//sectors_read_counter = 0;
		
		//Read the partition header
		long cpos = iAddress;
		oTicket = new WiiTicket(discData, cpos); cpos += WiiDisc.PART_TICKET_SIZE;
		//oTicket.printInfo();
		aTitleKey = oTicket.decryptTitleKey();
		//System.err.println("WiiDisc.readFromDisc || -DEBUG- Decrypted Key:");
		//for(byte b : aTitleKey) System.err.print(String.format("%02x ", b));
		//System.exit(2);
		
		if(aTitleKey != null) oDecryptor = new AES(aTitleKey);
		
		//Get the offsets and sizes to the various parts
		iTMDSize = Integer.toUnsignedLong(discData.intFromFile(cpos)); cpos += 4;
		iTMDOff = Integer.toUnsignedLong(discData.intFromFile(cpos)) << 2; cpos += 4;
		iCCSize = Integer.toUnsignedLong(discData.intFromFile(cpos)); cpos += 4;
		iCCOff = Integer.toUnsignedLong(discData.intFromFile(cpos)) << 2; cpos += 4;
		iH3Off = Integer.toUnsignedLong(discData.intFromFile(cpos)) << 2; cpos += 4;
		iDataOff = Integer.toUnsignedLong(discData.intFromFile(cpos)) << 2; cpos += 4;
		iDataSize = Integer.toUnsignedLong(discData.intFromFile(cpos)) << 2; cpos += 4;
		
		//System.err.println("WiiDisc.SubPartition.readFromDisc || Partition Offset: 0x" + Long.toHexString(iAddress));
		//System.err.println("WiiDisc.SubPartition.readFromDisc || TMD Offset: 0x" + Long.toHexString(iTMDOff));
		//System.err.println("WiiDisc.SubPartition.readFromDisc || TMD Size: 0x" + Long.toHexString(iTMDSize));
		//System.err.println("WiiDisc.SubPartition.readFromDisc || Cert Chain Offset: 0x" + Long.toHexString(iCCOff));
		//System.err.println("WiiDisc.SubPartition.readFromDisc || Cert Chain Size: 0x" + Long.toHexString(iCCSize));
		//System.err.println("WiiDisc.SubPartition.readFromDisc || H3 Table Offset: 0x" + Long.toHexString(iH3Off));
		//System.err.println("WiiDisc.SubPartition.readFromDisc || Data Offset: 0x" + Long.toHexString(iDataOff));
		//System.err.println("WiiDisc.SubPartition.readFromDisc || Data Size: 0x" + Long.toHexString(iDataSize));
		
		//Read the TMD
		long tmdoff = iAddress + iTMDOff;
		oTMD = new WiiTMD(discData, tmdoff);
		//oTMD.printInfo();
		
		//Read the Cert Chain
		cpos = iCCOff + iAddress;
		long ccend = cpos + iCCSize;
		while(cpos < ccend){
			WiiCertificate c = new WiiCertificate(discData, cpos);
			lCertChain.add(c);
			cpos += c.getSize();
		}
		//for(Certificate c : lCertChain) c.printInfo();
		
		//Read the H3 table
		//System.err.println("WiiDisc.SubPartition.readFromDisc || H3 Table Size: 0x" + Long.toHexString(H3_TABLE_SIZE));
		//System.err.println("WiiDisc.SubPartition.readFromDisc || H3 Table Entries: " + H3_TABLE_ENTRIES);
		cpos = iAddress + iH3Off;
		byte[] table = discData.getBytes(cpos, cpos+WiiDisc.H3_TABLE_SIZE);
		int k = 0;
		for(int i = 0; i < WiiDisc.H3_TABLE_ENTRIES; i++){
			for(int j = 0; j < 20; j++){aH3Table[i][j] = table[k]; k++;}
		}
		//System.err.println("WiiDisc.SubPartition.readFromDisc || H3 Table read!");
		//System.exit(2);

		if(auto_decrypt && WiiDisc.getCommonKey() != null) decryptData(discData, observer, true);
		
	}
	
	public void decryptData(FileBuffer discData, WiiCryptListener observer, boolean checkHash) throws IOException{
		
		//Read the data (Copy to temp file)
		//long cpos = iDataOff + iAddress;
		int sectorCount = (int)(iDataSize/(long)WiiDisc.DATACLUSTER_SIZE);
		Files.deleteIfExists(Paths.get(pTempFile));
		
		WiiCrypt crypto = new WiiCrypt(oDecryptor);
		crypto.setHashConfirm(checkHash);
		crypto.decryptPartitionData(discData, iAddress+iDataOff, sectorCount, observer, pTempFile);
		
		decryptedPart = new GCWiiDisc(pTempFile);
	}

	/* --- Getters --- */
	
	public int getPartitionType(){return ePartType;}
	public long getAddress(){return iAddress;}
	public WiiTicket getTicket(){return oTicket;}
	public WiiTMD getTMD(){return oTMD;}
	public byte[][] getH3Table(){return aH3Table;}
	
	public long getTMDOffset(){return iTMDOff;}
	public long getTMDSize(){return iTMDSize;}
	public long getCertChainOffset(){return iCCOff;}
	public long getCertChainSize(){return iCCSize;}
	public long getDataOffset(){return iDataOff;}
	public long getDataSize(){return iDataSize;}
	
	public byte[] getTitleKey(){return aTitleKey;}
	public AES getDecryptor(){return oDecryptor;}
	public List<WiiCertificate> getCertChain(){return lCertChain;}
	
	public boolean isDecrypted(){
		return (decryptedPart != null);
	}
	
	public String getGameTitle(){
		if(decryptedPart != null) return decryptedPart.getGameTitle();
		return "untitled";
	}
	
	public String getGameID(){
		if(decryptedPart != null) return decryptedPart.getGameID();
		return "UNKNOWNID";
	}
	
	/* --- Setters --- */
	
	/* --- Crypto --- */
	
	public void unlock() throws UnsupportedFileTypeException{
		aTitleKey = oTicket.decryptTitleKey();
		if(aTitleKey != null) oDecryptor = new AES(aTitleKey);
	}
	
	public long getCryptGroupID(){
		byte[] somedata = null;
		if(oTMD != null) somedata = oTMD.getSignature();
		else somedata = aH3Table[0];
		
		long id = 0;
		for(int i = 0; i < 8; i++){
			id = id << 8;
			id |= Byte.toUnsignedLong(somedata[i]);
		}
		
		return id;
	}
	
	public FileCryptRecord loadCryptTable(NinCryptTable tbl){
		//Add key
		if(aTitleKey == null) return null;
		int kidx = tbl.getIndexOfKey(NinCrypto.KEYTYPE_128, aTitleKey);
		if(kidx == -1) kidx = tbl.addKey(NinCrypto.KEYTYPE_128, aTitleKey);
		
		long cid = getCryptGroupID();
		NinCBCCryptRecord rec = new NinCBCCryptRecord(cid);
		rec.setKeyType(NinCrypto.KEYTYPE_128);
		rec.setKeyIndex(kidx);
		rec.setIV(new byte[16]);
		rec.setCryptOffset(iAddress + iDataOff);
		tbl.addRecord(cid, rec);
		
		return rec;
	}
	
	public DirectoryNode buildDirectTree(String wiiimg_path, boolean low_fs) throws IOException{
		
		String name = "";
		if(oTMD != null) name = Long.toHexString(oTMD.getTitleID());
		DirectoryNode partroot = new DirectoryNode(null, name);
		
		if(low_fs){
			FileNode fn = new FileNode(partroot, "cert.bin");
			fn.setSourcePath(wiiimg_path); fn.setOffset(iAddress+ iCCOff); fn.setLength(iCCSize);
			fn.setTypeChainHead(new FileTypeDefNode(PowerGCSysFileDefs.getRSADef()));
			fn = new FileNode(partroot, "h3.bin");
			fn.setSourcePath(wiiimg_path); fn.setOffset(iAddress+iH3Off); fn.setLength(WiiDisc.H3_TABLE_SIZE);
			fn.setTypeChainHead(new FileTypeDefNode(PowerGCSysFileDefs.getWiiH3Def()));
			fn = new FileNode(partroot, "ticket.bin");
			fn.setSourcePath(wiiimg_path); fn.setOffset(iAddress); fn.setLength(WiiDisc.PART_TICKET_SIZE);
			fn.setTypeChainHead(new FileTypeDefNode(PowerGCSysFileDefs.getWiiTicketDef()));
			fn = new FileNode(partroot, "tmd.bin");
			fn.setSourcePath(wiiimg_path); fn.setOffset(iAddress+iTMDOff); fn.setLength(iTMDSize);
			fn.setTypeChainHead(new FileTypeDefNode(PowerGCSysFileDefs.getWiiTMDDef()));
		}
		
		FileNode rawdat = new FileNode(null, "data.aes");
		rawdat.setBlockSize(0x8000);
		rawdat.setSourcePath(wiiimg_path);
		rawdat.setOffset(iAddress + iDataOff);
		rawdat.setLength(iDataSize);
		if(aTitleKey != null){
			long cid = this.getCryptGroupID();
			rawdat.setMetadataValue(NinCrypto.METAKEY_CRYPTGROUPUID, Long.toHexString(cid));
			
			WiiCBCDecMethod decm = new WiiCBCDecMethod(aTitleKey);
			FileBuffer rawpart = rawdat.loadData();
			EncryptedFileBuffer pdat = new EncryptedFileBuffer(rawpart, decm);
			GCWiiDisc fs = new GCWiiDisc(pdat);
			DirectoryNode gcroot = fs.getDiscTree();
			gcroot.doForTree(new FileNodeModifierCallback(){

				@Override
				public void doToNode(FileNode node) {
					if(node.isDirectory()) return;
					node.setSourcePath(wiiimg_path);
					node.setUseVirtualSource(true);
					node.setVirtualSourceNode(rawdat);
				}});
			
			if(low_fs){
				//Mount directly
				gcroot.setFileName("data");
				gcroot.setParent(partroot);
			}
			else{
				//Mount children
				List<FileNode> children = gcroot.getChildren();
				for(FileNode child : children){
					child.setParent(partroot);
				}
			}
		}
		else rawdat.setParent(partroot);
		rawdat.addEncryption(WiiAESDef.getDefinition());
		
		return partroot;
	}
	
	/* --- Extraction --- */
	
	public boolean dumpPartition(String directory) throws IOException
	{
		if(!FileBuffer.directoryExists(directory)) return false;
		
		String rootName = getGameID();
		if(rootName == null || rootName.isEmpty())	{
			Random r = new Random();
			rootName = "GCWIIDISC_" + Integer.toHexString(r.nextInt());
		}
		
		String part_dir = directory + File.separator + rootName;
		
		String apath = part_dir + File.separator + "cert.bin";
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
		String id = getGameID();
		bw.write("!part-id = " + id + "\n");
		String name = getGameTitle();
		bw.write("!part-name = " + name + "\n");
		bw.write("!part-offset = 0x" + Long.toHexString(iAddress) + "\n");
		bw.close();
		
		return decryptedPart.dumpDiscContentsTo(part_dir);
	}
	
	public DirectoryNode getPartitionTree(String wiiimg_path, String decrypt_path) throws IOException{

		DirectoryNode root = new DirectoryNode(null, "");
		
		//System stuff
		FileNode fn = new FileNode(root, "cert.bin");
		fn.setSourcePath(wiiimg_path); fn.setOffset(iAddress+ iCCOff); fn.setLength(iCCSize);
		fn.setTypeChainHead(new FileTypeDefNode(PowerGCSysFileDefs.getRSADef()));
		fn = new FileNode(root, "h3.bin");
		fn.setSourcePath(wiiimg_path); fn.setOffset(iAddress+iH3Off); fn.setLength(WiiDisc.H3_TABLE_SIZE);
		fn.setTypeChainHead(new FileTypeDefNode(PowerGCSysFileDefs.getWiiH3Def()));
		fn = new FileNode(root, "ticket.bin");
		fn.setSourcePath(wiiimg_path); fn.setOffset(iAddress); fn.setLength(WiiDisc.PART_TICKET_SIZE);
		fn.setTypeChainHead(new FileTypeDefNode(PowerGCSysFileDefs.getWiiTicketDef()));
		fn = new FileNode(root, "tmd.bin");
		fn.setSourcePath(wiiimg_path); fn.setOffset(iAddress+iTMDOff); fn.setLength(iTMDSize);
		fn.setTypeChainHead(new FileTypeDefNode(PowerGCSysFileDefs.getWiiTMDDef()));
		
		//Gamecube FS
		if(decryptedPart != null){
			String name = decryptedPart.getGameID();
			DirectoryNode fdir = decryptedPart.getDiscTree();
			//Rig source paths...
			fdir.setSourcePathForTree(decrypt_path);
			fdir.setFileName(name);
			fdir.setParent(root);
		}
		else if(decrypt_path != null && FileBuffer.fileExists(decrypt_path)){
			//Reload from this path...
			decryptedPart = new GCWiiDisc(decrypt_path);
			String name = decryptedPart.getGameID();
			DirectoryNode fdir = decryptedPart.getDiscTree();
			//Rig source paths...
			fdir.setSourcePathForTree(decrypt_path);
			fdir.setFileName(name);
			fdir.setParent(root);
		}
		else{
			fn = new FileNode(root, "data.aes");
			fn.setSourcePath(wiiimg_path); fn.setOffset(iAddress+ iDataOff); fn.setLength(iDataSize);
		}
		
		return root;
	}
	
	/* --- Writing/Serialization --- */
	
	public boolean writeDecryptedRaw(String outpath) throws IOException{
		//See if outpath already exists. If it does, delete.
		if(FileBuffer.fileExists(outpath)) Files.delete(Paths.get(outpath));
		
		if(FileBuffer.fileExists(pTempFile)) Files.copy(Paths.get(pTempFile), Paths.get(outpath));
		else return false;
		return true;
	}
	
	public FileBuffer serializeCertChain(){
		int ccount = lCertChain.size();
		FileBuffer certs = new MultiFileBuffer(ccount+1);
		for(WiiCertificate c : lCertChain) certs.addToFile(c.serializeCert());
		return certs;
	}
	
	public FileBuffer serializeH3Table(){
		int hashCount = aH3Table.length;
		FileBuffer table = new FileBuffer(hashCount * 20, true);
		
		for(int i = 0; i < hashCount; i++){
			for(int j = 0; j < 20; j++) table.addToFile(aH3Table[i][j]);
		}
		
		return table;
	}

	/* --- Cleanup --- */
	
	public void deleteTempFile() throws IOException{
		//closeOpenTempFile();
		if(decryptedPart != null) decryptedPart.closeDiscStream();
		Files.deleteIfExists(Paths.get(pTempFile));
		decryptedPart = null;
	}
	
}
