package waffleoRai_Containers.nintendo.wiidisc;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import waffleoRai_Containers.nintendo.GCWiiDisc;
import waffleoRai_Containers.nintendo.WBFSImage;
import waffleoRai_Containers.nintendo.WiiDisc;
import waffleoRai_Encryption.AES;
import waffleoRai_Files.FileTypeDefNode;
import waffleoRai_Utils.DirectoryNode;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;
import waffleoRai_fdefs.nintendo.PowerGCSysFileDefs;
import waffleoRai_Utils.FileNode;
import waffleoRai_Utils.MultiFileBuffer;

public class WiiPartition {
	
	public static final int DECRYPT_THREADS = 64; //512
	
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
	
	private volatile long sectors_read_counter;
	
	private GCWiiDisc decryptedPart;
	
	/* --- Decryption Parallelization --- */
	
	private static class SectorBank{
		
		private WiiDataCluster[] clusters;
		private volatile int logCount;
					
		private int expectedLast;

		public SectorBank(){
			clusters = new WiiDataCluster[DECRYPT_THREADS];
			expectedLast = -1;
		}
		
		public synchronized void enterCluster(int index, WiiDataCluster cluster){
			clusters[index] = cluster;
			logCount++;
		}
		
		public synchronized List<WiiDataCluster> dump(){
			List<WiiDataCluster> clist = new ArrayList<WiiDataCluster>(DECRYPT_THREADS);
			for(int i = 0; i < clusters.length; i++){
				clist.add(clusters[i]);
				clusters[i] = null;
			}
			logCount = 0;

			return clist;
		}
		
		public synchronized boolean channelClear(int index){return clusters[index] == null;}
		
		public boolean readyForDump(){
			if(expectedLast < 0) return logCount >= DECRYPT_THREADS;
			else return logCount >= expectedLast;
		}
		
		public void setLast(int i){
			expectedLast = i;
		}
		
	}
	
	private synchronized void incrementSectorsRead(){
		sectors_read_counter++;
	}
	
	private class DRunnable implements Runnable{
		
		public static final int MAX_CLUSTERS_QUEUED = 5;
		
		private int index;
		private int maxSector;
		private FileBuffer dataLink;
		
		private Map<Long, WiiDataCluster> queuedMap;
		
		private SectorBank bank;

		public DRunnable(int i, int maxSec, FileBuffer data){
			index = i;
			maxSector = maxSec;
			dataLink = data;
			queuedMap = new HashMap<Long, WiiDataCluster>();
		}
		
		public synchronized void setBank(SectorBank b){
			bank = b;
		}
		
		public synchronized boolean bankHold(){
			if(bank == null){return true;}
			return !bank.channelClear(index);
		}
		
		public void run() {
			long nextSector = Integer.toUnsignedLong(index);
			long decSector = nextSector;
			
			while(nextSector < maxSector)
			{
				//Calculate offset
				long pos = iAddress + iDataOff + (nextSector << WBFSImage.WII_SEC_SZ_S);
				//long dpos = pos;
				//System.err.println("WiiDisc.SubPartition.DRunnable.run || Decryptor " + index + ": Ready to read sector " + nextSector);
				
				//Hold until ready...
				while(bankHold()){
					//Decrypt next sector if there's space
					if(queuedMap.size() < MAX_CLUSTERS_QUEUED){
						long dpos = iAddress + iDataOff + (decSector << WBFSImage.WII_SEC_SZ_S);
						WiiDataCluster precluster = new WiiDataCluster();
						precluster.readCluster(dataLink, dpos, oDecryptor);
						queuedMap.put(decSector, precluster);
						decSector += DECRYPT_THREADS;
					}
					else{
						//Else sleep
						try {
							Thread.sleep(10);
						} 
						catch (InterruptedException e) {
							e.printStackTrace();
						}	
					}
				}
				
				//See if the next sector is already decrypted
				WiiDataCluster cluster = null;
				cluster = queuedMap.remove(nextSector);
				//Decrypt and store
				//System.err.println("WiiDisc.SubPartition.DRunnable.run || Decryptor " + index + ": Clear to decrypt sector " + nextSector);
				if(cluster == null)
				{
					cluster = new WiiDataCluster();
					cluster.readCluster(dataLink, pos, oDecryptor);
				}	
				//System.err.println("WiiDisc.SubPartition.DRunnable.run || Decryptor " + index + ": Decrypted sector " + nextSector);
				bank.enterCluster(index, cluster);
				incrementSectorsRead();
				
				nextSector += DECRYPT_THREADS;
			}
			
		}
		
	}

	/* --- Construction/Parsing --- */
	
	public WiiPartition(int type, long staddr) throws IOException{
		ePartType = type;
		iAddress = staddr;
		Random r = new Random();
		pTempFile = FileBuffer.getTempDir() + File.separator + "wiidisc_partition_" + Integer.toHexString(r.nextInt()) + ".tmp";
		lCertChain = new LinkedList<WiiCertificate>();
		aH3Table = new byte[WiiDisc.H3_TABLE_ENTRIES][20];
	}
	
	public void readFromDisc(FileBuffer discData) throws IOException, UnsupportedFileTypeException{
		//If they are relative to something like the partition, this needs to be changed!
		sectors_read_counter = 0;
		
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
		
		if(WiiDisc.getCommonKey() != null) decryptData(discData);
		
	}
	
	private void decryptData(FileBuffer discData) throws IOException{
		//Read the data (Copy to temp file)
		//long cpos = iDataOff + iAddress;
		int sectorCount = (int)(iDataSize/(long)WiiDisc.DATACLUSTER_SIZE);
		Files.deleteIfExists(Paths.get(pTempFile));
		
		//DEBUG BLOCK - Read the first sector
		/*String debug_write_hash = "C:\\Users\\Blythe\\Documents\\Game Stuff\\Wii\\Gamedata\\SkywardSword\\test\\debug\\sector_1_hash.bin";
		String debug_write_data = "C:\\Users\\Blythe\\Documents\\Game Stuff\\Wii\\Gamedata\\SkywardSword\\test\\debug\\sector_1_test.bin";
		DataCluster debug_dc = new DataCluster();
		debug_dc.readCluster(discData, cpos, oDecryptor);
		debug_dc.dumpHashTables(debug_write_hash);
		debug_dc.dumpData(debug_write_data);
		System.exit(2);*/
		
		//Threads:
		//	512 for sector parsing
		//	1 for writing
		//	1 for progress updates
		
		//I am way too lazy to actually fix the AES algorithm (or even cut
		//	out the cumbersome parts
		//So let's just parallelize this.
		
		SectorBank bank1 = new SectorBank();
		SectorBank bank2 = new SectorBank();
		
		DRunnable[] decryptors = new DRunnable[DECRYPT_THREADS];
		for (int i = 0; i < DECRYPT_THREADS; i++){
			decryptors[i] = new DRunnable(i, sectorCount, discData);
			decryptors[i].setBank(bank1);
		}
		
		Runnable writer = new Runnable(){
			
			public void run() {
				int sectors_written = 0;
				SectorBank activeBank = bank1;
				SectorBank inactiveBank = bank2;
				
				try {
					BufferedOutputStream bw = new BufferedOutputStream(new FileOutputStream(pTempFile));
					
					//Loop
					boolean dzSwitch = true;
					while(sectors_written < sectorCount){
						//Wait for activeBank to be dump ready...
						//System.err.println("WiiDisc.SubPartition.readFromDisc.<Runnable>.run || Writer: Preparing to write sector " + sectors_written);
						while(!activeBank.readyForDump())
						{
							try {Thread.sleep(10);} 
							catch (InterruptedException e) {e.printStackTrace();}
						}
						//System.err.println("WiiDisc.SubPartition.readFromDisc.<Runnable>.run || Writer: Cleared to write sector " + sectors_written);
						//Switch banks
						SectorBank temp = activeBank;
						activeBank = inactiveBank;
						inactiveBank = temp;
						int sleft = sectorCount - (int)sectors_read_counter;
						if(sleft < DECRYPT_THREADS) activeBank.setLast(sleft);
						for (int i = 0; i < DECRYPT_THREADS; i++){decryptors[i].setBank(activeBank);}
						
						//Start writing from the inactive bank
						List<WiiDataCluster> datadump = inactiveBank.dump();
						for(WiiDataCluster dc : datadump){
							if(dc!=null){
								//DEBUG - demarcate zero/data blocks
								if(dzSwitch){
									if(dc.isZeroBlock()){
										dzSwitch = false;
										//System.err.println("WiiDisc.readFromDisc || -DEBUG- Entering chunk of zero-sectors! Sectors written: " + sectors_written);
									}
								}
								else{
									if(!dc.isZeroBlock()){
										dzSwitch = true;
										//System.err.println("WiiDisc.readFromDisc || -DEBUG- Entering chunk of data-sectors! Sectors written: " + sectors_written);
									}
								}
								//DEBUG - Check integrity
								if(!dc.checkClusterHash()){
									System.err.println("WiiDisc.readFromDisc || -DEBUG- Sector failed hash check! Sectors written: " + sectors_written);
								}
								//Write
								bw.write(dc.getDecryptedData());
							}
							else{
								System.err.println("WiiDisc.readFromDisc || -DEBUG- Null sector encountered! Sectors written: " + sectors_written);
								bw.close();
								return;
							}
							sectors_written++;
						}
					}
					
					bw.close();
				} 
				catch (IOException e){
					e.printStackTrace();
				}
			}
		};
		
		Runnable monitor = new Runnable(){
			
			public void run() {
				while(sectors_read_counter < sectorCount){
					double percent = ((double)sectors_read_counter/(double)sectorCount) * 100.0;
					System.err.println("WiiDisc.readFromDisc || -DEBUG- Sectors read: " + sectors_read_counter + "/" + sectorCount + "(" + String.format("%.2f", percent) + "%)");
					try {
						Thread.sleep(5000);
					} 
					catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		};
		
		for (int i = 0; i < DECRYPT_THREADS; i++){
			Thread t = new Thread(decryptors[i]);
			t.setName("WiiPartitionDecryptor_" + i);
			t.setDaemon(true);
			t.start();
		}
		
		Thread writerThread = new Thread(writer);
		writerThread.setName("WiiPartitionTempWriter");
		writerThread.setDaemon(true);
		writerThread.start();
		
		Thread mThread = new Thread(monitor);
		mThread.setName("WiiPartitionDecryptionMonitor");
		mThread.setDaemon(true);
		mThread.start();
		
		//Wait for all to finish...
		while(sectors_read_counter < sectorCount || writerThread.isAlive()){
			try {Thread.sleep(100);} 
			catch (InterruptedException e) {e.printStackTrace();}
		}
		
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
		else{
			fn = new FileNode(root, "data.aes");
			fn.setSourcePath(wiiimg_path); fn.setOffset(iAddress+ iDataOff); fn.setLength(iDataSize);
		}
		
		return root;
	}
	
	/* --- Writing/Serialization --- */
	
	public boolean writeDecryptedRaw(String outpath) throws IOException{
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
	}
	
}
