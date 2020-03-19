package waffleoRai_Containers.nintendo;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import waffleoRai_Utils.CompositeBuffer;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;
import waffleoRai_Encryption.AES;

public class WiiDisc 
{
	
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
	
	/* ----- Inner Classes ----- */
	
	public static class Partition
	{
		/* --- Instance Variables --- */
		
		private SubPartition[] oPartitions;
		
		/* --- Construction --- */
		
		public Partition(int subCount)
		{
			oPartitions = new SubPartition[subCount];
		}
		
		public void addPartition(int idx, int type, long staddr) throws IOException
		{
			oPartitions[idx] = new SubPartition(type, staddr);
		}
		
		/* --- Parsing --- */
		
		public void readPartion(FileBuffer discData) throws IOException, UnsupportedFileTypeException
		{
			for(int i = 0; i < oPartitions.length; i++)
			{
				if (oPartitions[i] != null) oPartitions[i].readFromDisc(discData);
			}
		}
	
		/* --- Writing --- */
		
		public boolean dumpRaw(String directory) throws IOException
		{
			boolean b = true;
			if(!FileBuffer.directoryExists(directory)) Files.createDirectories(Paths.get(directory));
			for(int i = 0; i < oPartitions.length; i++)
			{
				if(oPartitions[i] != null)
				{
					String name = oPartitions[i].getGameID();
					String target = directory + File.separator + name + "." + GCWiiDisc.GCM_EXT;
					b = b && oPartitions[i].writeDecryptedRaw(target);
				}
			}
			return b;
		}
		
		public boolean dumpPartition(String directory) throws IOException
		{
			boolean b = true;
			for(int i = 0; i < oPartitions.length; i++)
			{
				if(oPartitions[i] != null)
				{
					b = b && (oPartitions[i].dumpPartition(directory));
				}
			}
			return b;
		}
		
		/* --- Getters --- */
		
		public List<SubPartition> getSubPartitions()
		{
			List<SubPartition> plist = new ArrayList<SubPartition>(oPartitions.length + 1);
			for(int i = 0; i < oPartitions.length; i++)
			{
				if(oPartitions[i] != null) plist.add(oPartitions[i]);
			}
			return plist;
		}
		
		/* --- Cleanup --- */
		
		public void deleteTempFiles() throws IOException
		{
			for(int i = 0; i < oPartitions.length; i++)
			{
				if(oPartitions[i] != null) oPartitions[i].deleteTempFile();
			}
		}
		
	}
	
	public static class SubPartition
	{
		public static final int DECRYPT_THREADS = 64; //512
		
		/* --- Instance Variables --- */
		
		private String pTempFile; //Don't want to hold all that data in mem!
		//private StreamBuffer oOpenFile;
		
		private int ePartType;
		private long iAddress;
		
		private Ticket oTicket;
		private TMD oTMD;
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
		private List<Certificate> lCertChain;
		
		private volatile long sectors_read_counter;
		
		private GCWiiDisc decryptedPart;
		
		/* --- Decryption Parallelization --- */
		
		private static class SectorBank
		{
			private DataCluster[] clusters;
			private volatile int logCount;
			//private volatile boolean release;
						
			private int expectedLast;
			
			//private String debugname;
			
			public SectorBank()
			{
				clusters = new DataCluster[DECRYPT_THREADS];
				//release = true;
				expectedLast = -1;
				
				//Random r = new Random();
				//debugname = "SectorBank_" + Integer.toHexString(r.nextInt());
				//System.err.println("WiiDisc.SubPartition.SectorBank.<init> || Bank " + debugname + " created!");
			}
			
			public synchronized void enterCluster(int index, DataCluster cluster)
			{
				//release = false;
				clusters[index] = cluster;
				logCount++;
				//System.err.println("WiiDisc.SubPartition.SectorBank.enterCluster || Bank " + debugname + ": Cluster entered for index " + index);
			}
			
			public synchronized List<DataCluster> dump()
			{
				//System.err.println("WiiDisc.SubPartition.SectorBank.dump || Bank " + debugname + ": Dump requested!");
				List<DataCluster> clist = new ArrayList<DataCluster>(DECRYPT_THREADS);
				for(int i = 0; i < clusters.length; i++){
					clist.add(clusters[i]);
					clusters[i] = null;
				}
				logCount = 0;
				//release = true;
				return clist;
			}
			
			//public boolean releaseSet(){return release;}
			
			public synchronized boolean channelClear(int index){return clusters[index] == null;}
			
			public boolean readyForDump()
			{
				if(expectedLast < 0) return logCount >= DECRYPT_THREADS;
				else return logCount >= expectedLast;
			}
			
			public void setLast(int i)
			{
				expectedLast = i;
			}
			
		}
		
		private synchronized void incrementSectorsRead()
		{
			sectors_read_counter++;
		}
		
		private class DRunnable implements Runnable
		{
			public static final int MAX_CLUSTERS_QUEUED = 5;
			
			private int index;
			private int maxSector;
			private FileBuffer dataLink;
			
			private Map<Long, DataCluster> queuedMap;
			
			private SectorBank bank;

			public DRunnable(int i, int maxSec, FileBuffer data)
			{
				index = i;
				maxSector = maxSec;
				dataLink = data;
				queuedMap = new HashMap<Long, DataCluster>();
			}
			
			public synchronized void setBank(SectorBank b)
			{
				bank = b;
			}
			
			public synchronized boolean bankHold()
			{
				if(bank == null){return true;}
				//return !bank.releaseSet();
				return !bank.channelClear(index);
			}
			
			@Override
			public void run() 
			{
				long nextSector = Integer.toUnsignedLong(index);
				long decSector = nextSector;
				
				while(nextSector < maxSector)
				{
					//Calculate offset
					long pos = iAddress + iDataOff + (nextSector << WBFSImage.WII_SEC_SZ_S);
					//long dpos = pos;
					//System.err.println("WiiDisc.SubPartition.DRunnable.run || Decryptor " + index + ": Ready to read sector " + nextSector);
					
					//Hold until ready...
					while(bankHold())
					{
						//Decrypt next sector if there's space
						if(queuedMap.size() < MAX_CLUSTERS_QUEUED)
						{
							long dpos = iAddress + iDataOff + (decSector << WBFSImage.WII_SEC_SZ_S);
							DataCluster precluster = new DataCluster();
							precluster.readCluster(dataLink, dpos, oDecryptor);
							queuedMap.put(decSector, precluster);
							decSector += DECRYPT_THREADS;
						}
						else
						{
							//Else sleep
							try 
							{
								Thread.sleep(10);
							} 
							catch (InterruptedException e) 
							{
								e.printStackTrace();
							}	
						}
					}
					
					//See if the next sector is already decrypted
					DataCluster cluster = null;
					cluster = queuedMap.remove(nextSector);
					//Decrypt and store
					//System.err.println("WiiDisc.SubPartition.DRunnable.run || Decryptor " + index + ": Clear to decrypt sector " + nextSector);
					if(cluster == null)
					{
						cluster = new DataCluster();
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
		
		public SubPartition(int type, long staddr) throws IOException
		{
			ePartType = type;
			iAddress = staddr;
			Random r = new Random();
			pTempFile = FileBuffer.getTempDir() + File.separator + "wiidisc_partition_" + Integer.toHexString(r.nextInt()) + ".tmp";
			lCertChain = new LinkedList<Certificate>();
			aH3Table = new byte[H3_TABLE_ENTRIES][20];
		}
		
		public void readFromDisc(FileBuffer discData) throws IOException, UnsupportedFileTypeException
		{
			//If they are relative to something like the partition, this needs to be changed!
			//System.err.println("WiiDisc.readFromDisc || -DEBUG- Function called!");
			sectors_read_counter = 0;
			
			//Read the partition header
			long cpos = iAddress;
			oTicket = new Ticket(discData, cpos); cpos += PART_TICKET_SIZE;
			//oTicket.printInfo();
			aTitleKey = oTicket.decryptTitleKey();
			//System.err.println("WiiDisc.readFromDisc || -DEBUG- Decrypted Key:");
			//for(byte b : aTitleKey) System.err.print(String.format("%02x ", b));
			System.err.println();
			//System.exit(2);
			oDecryptor = new AES(aTitleKey);
			
			//Get the offsets and sizes to the various parts
			iTMDSize = Integer.toUnsignedLong(discData.intFromFile(cpos)); cpos += 4;
			iTMDOff = Integer.toUnsignedLong(discData.intFromFile(cpos)) << 2; cpos += 4;
			iCCSize = Integer.toUnsignedLong(discData.intFromFile(cpos)); cpos += 4;
			iCCOff = Integer.toUnsignedLong(discData.intFromFile(cpos)) << 2; cpos += 4;
			iH3Off = Integer.toUnsignedLong(discData.intFromFile(cpos)) << 2; cpos += 4;
			iDataOff = Integer.toUnsignedLong(discData.intFromFile(cpos)) << 2; cpos += 4;
			iDataSize = Integer.toUnsignedLong(discData.intFromFile(cpos)) << 2; cpos += 4;
			
			System.err.println("WiiDisc.SubPartition.readFromDisc || Partition Offset: 0x" + Long.toHexString(iAddress));
			System.err.println("WiiDisc.SubPartition.readFromDisc || TMD Offset: 0x" + Long.toHexString(iTMDOff));
			System.err.println("WiiDisc.SubPartition.readFromDisc || TMD Size: 0x" + Long.toHexString(iTMDSize));
			System.err.println("WiiDisc.SubPartition.readFromDisc || Cert Chain Offset: 0x" + Long.toHexString(iCCOff));
			System.err.println("WiiDisc.SubPartition.readFromDisc || Cert Chain Size: 0x" + Long.toHexString(iCCSize));
			System.err.println("WiiDisc.SubPartition.readFromDisc || H3 Table Offset: 0x" + Long.toHexString(iH3Off));
			System.err.println("WiiDisc.SubPartition.readFromDisc || Data Offset: 0x" + Long.toHexString(iDataOff));
			System.err.println("WiiDisc.SubPartition.readFromDisc || Data Size: 0x" + Long.toHexString(iDataSize));
			
			//Read the TMD
			long tmdoff = iAddress + iTMDOff;
			oTMD = new TMD(discData, tmdoff);
			//oTMD.printInfo();
			
			//Read the Cert Chain
			cpos = iCCOff + iAddress;
			long ccend = cpos + iCCSize;
			while(cpos < ccend)
			{
				Certificate c = new Certificate(discData, cpos);
				lCertChain.add(c);
				cpos += c.getSize();
			}
			//for(Certificate c : lCertChain) c.printInfo();
			
			//Read the H3 table
			//System.err.println("WiiDisc.SubPartition.readFromDisc || H3 Table Size: 0x" + Long.toHexString(H3_TABLE_SIZE));
			//System.err.println("WiiDisc.SubPartition.readFromDisc || H3 Table Entries: " + H3_TABLE_ENTRIES);
			cpos = iAddress + iH3Off;
			byte[] table = discData.getBytes(cpos, cpos+H3_TABLE_SIZE);
			int k = 0;
			for(int i = 0; i < H3_TABLE_ENTRIES; i++)
			{
				for(int j = 0; j < 20; j++){aH3Table[i][j] = table[k]; k++;}
			}
			System.err.println("WiiDisc.SubPartition.readFromDisc || H3 Table read!");
			//System.exit(2);
			
			//Read the data (Copy to temp file)
			cpos = iDataOff + iAddress;
			int sectorCount = (int)(iDataSize/(long)DATACLUSTER_SIZE);
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
				@Override
				public void run() 
				{
					int sectors_written = 0;
					SectorBank activeBank = bank1;
					SectorBank inactiveBank = bank2;
					
					try 
					{
						BufferedOutputStream bw = new BufferedOutputStream(new FileOutputStream(pTempFile));
						
						//Loop
						boolean dzSwitch = true;
						while(sectors_written < sectorCount)
						{
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
							List<DataCluster> datadump = inactiveBank.dump();
							for(DataCluster dc : datadump){
								if(dc!=null)
								{
									//DEBUG - demarcate zero/data blocks
									if(dzSwitch)
									{
										if(dc.isZeroBlock())
										{
											dzSwitch = false;
											System.err.println("WiiDisc.readFromDisc || -DEBUG- Entering chunk of zero-sectors! Sectors written: " + sectors_written);
										}
									}
									else
									{
										if(!dc.isZeroBlock())
										{
											dzSwitch = true;
											System.err.println("WiiDisc.readFromDisc || -DEBUG- Entering chunk of data-sectors! Sectors written: " + sectors_written);
										}
									}
									//DEBUG - Check integrity
									if(!dc.checkClusterHash())
									{
										System.err.println("WiiDisc.readFromDisc || -DEBUG- Sector failed hash check! Sectors written: " + sectors_written);
									}
									//Write
									bw.write(dc.getDecryptedData());
								}
								else
								{
									System.err.println("WiiDisc.readFromDisc || -DEBUG- Null sector encountered! Sectors written: " + sectors_written);
									bw.close();
									return;
								}
								sectors_written++;
							}
						}
						
						bw.close();
					} 
					catch (IOException e) 
					{
						e.printStackTrace();
					}
				}
			};
			
			Runnable monitor = new Runnable(){
				@Override
				public void run() {
					while(sectors_read_counter < sectorCount)
					{
						double percent = ((double)sectors_read_counter/(double)sectorCount) * 100.0;
						System.err.println("WiiDisc.readFromDisc || -DEBUG- Sectors read: " + sectors_read_counter + "/" + sectorCount + "(" + String.format("%.2f", percent) + "%)");
						try 
						{
							Thread.sleep(5000);
						} 
						catch (InterruptedException e) 
						{
							e.printStackTrace();
						}
					}
				}
			};
			
			/*BufferedOutputStream bw = new BufferedOutputStream(new FileOutputStream(pTempFile));
			for(int s = 0; s < sectorCount; s++)
			{
				DataCluster cluster = new DataCluster();
				cluster.readCluster(discData, cpos, oDecryptor);
				cpos += DATACLUSTER_SIZE;
				bw.write(cluster.getDecryptedData());
				sectors_read_counter++;
			}
			bw.close();*/
			
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
			while(sectors_read_counter < sectorCount || writerThread.isAlive())
			{
				try 
				{
					Thread.sleep(100);
				} 
				catch (InterruptedException e) 
				{
					e.printStackTrace();
				}
			}
			
			//System.exit(2);
			decryptedPart = new GCWiiDisc(pTempFile);
		}
		
		/* --- Getters --- */
		
		public int getPartitionType(){return ePartType;}
		public long getAddress(){return iAddress;}
		public Ticket getTicket(){return oTicket;}
		public TMD getTMD(){return oTMD;}
		public byte[][] getH3Table(){return aH3Table;}
		
		public long getTMDOffset(){return iTMDOff;}
		public long getTMDSize(){return iTMDSize;}
		public long getCertChainOffset(){return iCCOff;}
		public long getCertChainSize(){return iCCSize;}
		public long getDataOffset(){return iDataOff;}
		public long getDataSize(){return iDataSize;}
		
		public byte[] getTitleKey(){return aTitleKey;}
		public AES getDecryptor(){return oDecryptor;}
		public List<Certificate> getCertChain(){return lCertChain;}
		
		public String getGameTitle()
		{
			if(decryptedPart != null) return decryptedPart.getGameTitle();
			return "untitled";
		}
		
		public String getGameID()
		{
			if(decryptedPart != null) return decryptedPart.getGameID();
			return "UNKNOWNID";
		}
		
		/* --- Setters --- */
		
		/* --- Extraction --- */
		
		public boolean dumpPartition(String directory) throws IOException
		{
			if(!FileBuffer.directoryExists(directory)) return false;
			
			String rootName = getGameID();
			if(rootName == null || rootName.isEmpty())
			{
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
		
		/* --- Writing/Serialization --- */
		
		public boolean writeDecryptedRaw(String outpath) throws IOException
		{
			if(FileBuffer.fileExists(pTempFile)) Files.copy(Paths.get(pTempFile), Paths.get(outpath));
			else return false;
			return true;
		}
		
		public FileBuffer serializeCertChain()
		{
			int ccount = lCertChain.size();
			FileBuffer certs = new CompositeBuffer(ccount+1);
			for(Certificate c : lCertChain) certs.addToFile(c.serializeCert());
			return certs;
		}
		
		public FileBuffer serializeH3Table()
		{
			int hashCount = aH3Table.length;
			FileBuffer table = new FileBuffer(hashCount * 20, true);
			
			for(int i = 0; i < hashCount; i++)
			{
				for(int j = 0; j < 20; j++) table.addToFile(aH3Table[i][j]);
			}
			
			return table;
		}
		
		/*public void dumpCertChain(String path)
		{
			//TODO
		}
		
		public void dumpH3Table(String path)
		{
			//TODO
		}
		
		public void dumpTicket(String path)
		{
			//TODO
		}
		
		public void dumpTMD(String path)
		{
			//TODO
		}*/
		
		/* --- Cleanup --- */
		
		public void deleteTempFile() throws IOException
		{
			//closeOpenTempFile();
			if(decryptedPart != null) decryptedPart.closeDiscStream();
			Files.deleteIfExists(Paths.get(pTempFile));
		}
		
	}
	
	public static class DataCluster
	{
		/* --- Instance Variables --- */
		
		private byte[][] cluster_hashTable;
		private byte[][] subgroup_hashTable;
		private byte[][] group_hashTable;
		
		private byte[] data_iv;
		
		private byte[] data;
		
		private boolean zero_sector;
		
		/* --- Construction/Parsing --- */
		
		public DataCluster()
		{
			cluster_hashTable = new byte[31][20];
			subgroup_hashTable = new byte[8][20];
			group_hashTable = new byte[8][20];
			data_iv = new byte[16];
			zero_sector = false;
		}
		
		public void readCluster(FileBuffer src, long stpos, AES partitionCracker)
		{
			long cpos = DATACLUSTER_IV_OFFSET + stpos;
			for(int i = 0; i < 16; i++) {data_iv[i] = src.getByte(cpos); cpos++;}
			
			//Now read the hash tables
			byte[] nulliv = new byte[16];
			//Copy
			byte[] hashdata = src.getBytes(stpos, stpos+0x400);
			cpos = stpos + DATACLUSTER_DATA_OFFSET;
			byte[] rawdata = src.getBytes(cpos, cpos + DATACLUSTER_DATA_SIZE);
			//(DEBUG) See if it's a zero cluster
			if(isZeroBlock(hashdata, rawdata)){data = new byte[DATACLUSTER_DATA_SIZE]; zero_sector = true; return;}
			//Decrypt hash data
			hashdata = partitionCracker.decrypt(nulliv, hashdata);

			//Copy back into this structure
			int k = 0;
			for(int i = 0; i < 31; i++)
			{
				byte[] myhash = cluster_hashTable[i];
				for(int j = 0; j < 20; j++){myhash[j] = hashdata[k]; k++;}
			}
			
			//Skip 20 bytes of padding
			k += 20;
			
			for(int i = 0; i < 8; i++)
			{
				byte[] myhash = subgroup_hashTable[i];
				for(int j = 0; j < 20; j++){myhash[j] = hashdata[k]; k++;}
			}
			
			//Skip 32 bytes of padding
			k += 32;
			
			for(int i = 0; i < 8; i++)
			{
				byte[] myhash = group_hashTable[i];
				for(int j = 0; j < 20; j++){myhash[j] = hashdata[k]; k++;}
			}

			//Finally, the actual data
			data = partitionCracker.decrypt(data_iv, rawdata);
		}
		
		public byte[] getDecryptedData()
		{
			return data;
		}
		
		private boolean isZeroBlock(byte[] hashblock, byte[] datablock)
		{
			for(int i = 0; i < DATACLUSTER_DATA_OFFSET; i++)
			{
				if(hashblock[i] != 0) return false;
			}
			for(int i = 0; i < DATACLUSTER_DATA_SIZE; i++)
			{
				if(datablock[i] != 0) return false;
			}
			return true;
		}
		
		public boolean checkClusterHash()
		{
			if(data == null) return false;
			if(zero_sector) return true;
			try 
			{
				MessageDigest sha = MessageDigest.getInstance("SHA-1");
				int blockStart = 0;
				for(int i = 0; i < 31; i++)
				{
					sha.update(data, blockStart, DATACLUSTER_DATA_OFFSET);
					byte[] ref = cluster_hashTable[i];
					byte[] hash = sha.digest();
					if(!MessageDigest.isEqual(ref, hash)) return false;
					blockStart += DATACLUSTER_DATA_OFFSET;
				}
				return true;
			} 
			catch (NoSuchAlgorithmException e) 
			{
				e.printStackTrace();
				return false;
			}
		}
		
		public boolean isZeroBlock()
		{
			return zero_sector;
		}
		
		/* --- Writing --- */
		
		public void dumpHashTables(String path) throws IOException
		{
			BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(path));
			for(int i = 0; i < 31; i++)
			{
				byte[] hash = cluster_hashTable[i];
				out.write(hash);
			}
			for(int i = 0; i < 20; i++){out.write(0x00);}
			for(int i = 0; i < 8; i++)
			{
				byte[] hash = subgroup_hashTable[i];
				out.write(hash);
			}
			for(int i = 0; i < 32; i++){out.write(0x00);}
			for(int i = 0; i < 8; i++)
			{
				byte[] hash = group_hashTable[i];
				out.write(hash);
			}
			for(int i = 0; i < 32; i++){out.write(0x00);}
			
			out.close();
		}
		
		public void dumpData(String path) throws IOException
		{
			BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(path));
			out.write(data);
			out.close();
		}

	}
	
	public static class Ticket
	{
		/* --- Instance Variables --- */
		
		private int eSigType; //Should be 0x10001
		private byte[] aSigKey; //Length 0x100
		private String sSigIssuer;
		private byte[] aECDH;
		private byte[] aEncryptedTitleKey;
		private byte[] aTicketID; //At offset 0x1D0
		private int iConsoleID;
		private byte[] aInitVector; //At offset 0x1DC
		private short iTicketTitleVersion;
		private int iPermittedTitlesMask;
		private int iPermitMask;
		private boolean bTitleExport;
		private int eCommonKeyIndex;
		private byte[] aContentAccessPermission;

		public TimeLimit[] aTimeLimits;
		
		/* --- Inner Classes --- */
		
		public static class TimeLimit
		{
			public boolean bEnableTimeLimit;
			public int iTimeLimit;
			
			public TimeLimit(){bEnableTimeLimit = false; iTimeLimit = 0;}
		}
		
		/* --- Construction/Parsing --- */
		
		public Ticket()
		{
			eSigType = SIGTYPE_RSA2048;
			aSigKey = new byte[0x100];
			sSigIssuer = "UNKNOWN";
			aECDH = new byte[0x3C];
			
			aEncryptedTitleKey = new byte[16];
			Random r = new Random();
			r.nextBytes(aEncryptedTitleKey);
			
			aTicketID = new byte[16];
			iConsoleID = 0;
			aInitVector = new byte[16];
			iTicketTitleVersion = 0;
			
			iPermittedTitlesMask = -1;
			iPermitMask = -1;
			bTitleExport = false;
			eCommonKeyIndex = 0;
			
			aContentAccessPermission = new byte[0x40];
			
			aTimeLimits = new TimeLimit[8];
			for(int i = 0; i < 8; i++) aTimeLimits[i] = new TimeLimit();
		}
		
		public Ticket(FileBuffer src, long stpos)
		{
			this();
			//System.err.println("WiiDisc.Ticket.<init> || -DEBUG- Function called!");
			
			long cpos = stpos;
			src.setEndian(true);
			
			eSigType = src.intFromFile(cpos); cpos += 4;
			//System.err.println("WiiDisc.Ticket.<init> || -DEBUG- eSigType: 0x" + Integer.toHexString(eSigType));
			for(int i = 0; i < 0x100; i++) {aSigKey[i] = src.getByte(cpos); cpos++;}
			cpos += 0x3C; //Padding
			sSigIssuer = src.getASCII_string(cpos, 0x40); cpos += 0x40;
			for(int i = 0; i < 0x3C; i++) {aECDH[i] = src.getByte(cpos); cpos++;}
			cpos += 3; //Padding
			for(int i = 0; i < 0x10; i++) {aEncryptedTitleKey[i] = src.getByte(cpos); cpos++;}
			cpos++; //Unknown
			for(int i = 0; i < 0x08; i++) {aTicketID[i] = src.getByte(cpos); cpos++;}
			iConsoleID = src.intFromFile(cpos); cpos += 4;
			for(int i = 0; i < 0x08; i++) {aInitVector[i] = src.getByte(cpos); cpos++;}
			cpos += 2; //Unknown 0xFFFF
			iTicketTitleVersion = src.shortFromFile(cpos); cpos += 2;
			iPermittedTitlesMask = src.intFromFile(cpos); cpos += 4;
			iPermitMask = src.intFromFile(cpos); cpos += 4;
			
			byte b = src.getByte(cpos); cpos++;
			bTitleExport = (b != 0);
			eCommonKeyIndex = Byte.toUnsignedInt(src.getByte(cpos)); cpos++;
			cpos += 0x30; //Unknown
			for(int i = 0; i < 0x40; i++) {aContentAccessPermission[i] = src.getByte(cpos); cpos++;}
			cpos += 2; //Padding
			
			for(int j = 0; j < 8; j++)
			{
				aTimeLimits[j].bEnableTimeLimit = (src.intFromFile(cpos) != 0); cpos += 4;
				aTimeLimits[j].iTimeLimit = src.intFromFile(cpos); cpos += 4;
			}
		}
		
		/* --- Getters --- */
		
		public int getSigType(){return eSigType;}
		public byte[] getSigKey(){return aSigKey;}
		public String getSigIssuer(){return sSigIssuer;}
		public byte[] getECDH(){return aECDH;}
		public byte[] getEncryptedTitleKey(){return aEncryptedTitleKey;}
		public byte[] getTicketID(){return aTicketID;}
		public int getConsoleID(){return iConsoleID;}
		public byte[] getInitVector(){return aInitVector;}
		public short getTicketTitleVersion(){return iTicketTitleVersion;}
		public int getPermittedTitlesMask(){return iPermittedTitlesMask;}
		public int getPermitMask(){return iPermitMask;}
		public boolean allowsTitleExport(){return bTitleExport;}
		public int getCommonKeyIndex(){return eCommonKeyIndex;}
		public byte[] getContentAccessPermissions(){return aContentAccessPermission;}
		public TimeLimit getTimeLimit(int index){return aTimeLimits[index];}
		
		/* --- Utility --- */
		
		public byte[] decryptTitleKey() throws UnsupportedFileTypeException
		{
			if(eCommonKeyIndex != 0) throw new FileBuffer.UnsupportedFileTypeException("Ticket requests unknown common key!");
			AES wiiCrypt = new AES(TITLE_COMMON_KEY);
			byte[] decKey = wiiCrypt.decrypt(aInitVector, aEncryptedTitleKey);
			//byte[] decKey = wiiCrypt.decrypt(aTicketID, aEncryptedTitleKey);
			return decKey;
		}
		
		/* --- Serialization --- */
		
		public FileBuffer serializeTicket()
		{
			final int TICKET_SIZE = 0x2A4;
			final byte ZERO = 0x00;
			
			FileBuffer ticket = new FileBuffer(TICKET_SIZE, true);
			
			ticket.addToFile(eSigType);
			for(int i = 0; i < 0x100; i++) ticket.addToFile(aSigKey[i]);
			//0x3C of padding
			for(int i = 0; i < 0x3C; i++) ticket.addToFile(ZERO);
			
			//Issuer
			if(sSigIssuer.length() > 0x40) sSigIssuer = sSigIssuer.substring(0, 0x40);
			int slen = sSigIssuer.length();
			ticket.printASCIIToFile(sSigIssuer);
			while(slen < 0x40){ticket.addToFile(ZERO); slen++;}
			
			for(int i = 0; i < aECDH.length; i++) ticket.addToFile(aECDH[i]);
			ticket.add24ToFile(0); //3 bytes padding
			for(int i = 0; i < 0x10; i++) ticket.addToFile(aEncryptedTitleKey[i]);
			ticket.addToFile(ZERO); //Padding?
			
			for(int i = 0; i < 8; i++) ticket.addToFile(aTicketID[i]);
			ticket.addToFile(iConsoleID);
			for(int i = 0; i < 8; i++) ticket.addToFile(aInitVector[i]);
			ticket.addToFile((short)0xFFFF);
			ticket.addToFile(iTicketTitleVersion);
			ticket.addToFile(iPermittedTitlesMask);
			ticket.addToFile(iPermitMask);
			if(bTitleExport) ticket.addToFile((byte)0x01);
			else ticket.addToFile(ZERO);
			ticket.addToFile((byte)eCommonKeyIndex);
			//0x30 bytes of "unknown"
			//We'll put all 0 for now, though some titles have some non-zero bytes
			for(int i = 0; i < 0x30; i++) ticket.addToFile(ZERO);
			for(int i = 0; i < 0x40; i++) ticket.addToFile(aContentAccessPermission[i]);
			ticket.addToFile((short)0); //2 bytes padding
			
			//Time limits
			for(int i = 0; i < 8; i++)
			{
				TimeLimit tl = aTimeLimits[i];
				if(tl.bEnableTimeLimit) ticket.addToFile(1);
				else ticket.addToFile(0);
				ticket.addToFile(tl.iTimeLimit);
			}
			
			return ticket;
		}

		public void printInfo()
		{
			System.out.println("----- Wii Partition Ticket -----");
			System.out.println("Sig Type: 0x" + Integer.toHexString(eSigType));
			System.out.println("Sig Key: ");
			for(int i = 0; i < aSigKey.length; i++)
			{
				System.out.print(String.format("%02x ", aSigKey[i]));
				if(i%16 == 15) System.out.println();
			}
			if(aSigKey.length % 16 != 0) System.out.println();
			System.out.println("Sig Issuer: " + sSigIssuer);
			System.out.println("ECDH: ");
			for(int i = 0; i < aECDH.length; i++)
			{
				System.out.print(String.format("%02x ", aECDH[i]));
				if(i%16 == 15) System.out.println();
			}
			if(aECDH.length % 16 != 0) System.out.println();
			System.out.println("Encrypted Title Key: ");
			for(int i = 0; i < aEncryptedTitleKey.length; i++)
			{
				System.out.print(String.format("%02x ", aEncryptedTitleKey[i]));
				if(i%16 == 15) System.out.println();
			}
			if(aEncryptedTitleKey.length % 16 != 0) System.out.println();
			System.out.println("Ticket ID: ");
			for(int i = 0; i < aTicketID.length; i++)
			{
				System.out.print(String.format("%02x ", aTicketID[i]));
				if(i%16 == 15) System.out.println();
			}
			if(aTicketID.length % 16 != 0) System.out.println();
			System.out.println("Console ID: " + iConsoleID);
			System.out.println("Init Vector: ");
			for(int i = 0; i < aInitVector.length; i++)
			{
				System.out.print(String.format("%02x ", aInitVector[i]));
				if(i%16 == 15) System.out.println();
			}
			if(aInitVector.length % 16 != 0) System.out.println();
			System.out.println("Ticket Title Version: " + iTicketTitleVersion);
			System.out.println("Permitted Titles Mask: 0x" + Integer.toHexString(iPermittedTitlesMask));
			System.out.println("Permit Mask: 0x" + Integer.toHexString(iPermitMask));
			System.out.println("Title Export Allowed: " + this.bTitleExport);
			System.out.println("Common Key Index: " + eCommonKeyIndex);
			System.out.println("Content Access Permissions: ");
			for(int i = 0; i < aContentAccessPermission.length; i++)
			{
				System.out.print(String.format("%02x ", aContentAccessPermission[i]));
				if(i%16 == 15) System.out.println();
			}
			if(aContentAccessPermission.length % 16 != 0) System.out.println();
			for(int i = 0; i < aTimeLimits.length; i++)
			{
				System.out.println("Time Limit " + i + ":");
				TimeLimit tl = aTimeLimits[i];
				if(tl != null)
				{
					System.out.println("\tEnabled: " + tl.bEnableTimeLimit);
					System.out.println("\tSeconds: " + tl.iTimeLimit);
				}
			}
		}
		
	}
	
	public static class TMD
	{
		/* --- Instance Variables --- */
		
		private int eSigType;
		private byte[] aSignature;
		private String sIssuer;
		
		private int iVersion;
		private int iVersion_ca_crl;
		private int iVersion_signer_crl;
		
		private long iSystemVersion;
		private long iTitleID;
		private int eTitleType;
		private int iGroupID;
		
		private int eRegion;
		private byte[] aRatings;
		private byte[] aIPCMask;
		private int mAccessRights;
		
		private int iTitleVersion;
		private int iContentCount;
		private int iBootIndex;
		
		public Content[] aContents;
		
		/* --- Construction/Parsing --- */
		
		public TMD()
		{
			eSigType = SIGTYPE_RSA2048;
			aRatings = new byte[16];
			aIPCMask = new byte[12];
		}
		
		public TMD(FileBuffer src, long stpos)
		{
			this();
			long cpos = stpos;
			
			eSigType = src.intFromFile(cpos); cpos += 4;
			int siglen = Certificate.getSignatureLength(eSigType);
			aSignature = new byte[siglen];
			for(int i = 0; i < siglen; i++){aSignature[i] = src.getByte(cpos); cpos++;}
			
			cpos += 60; //Padding
			sIssuer = src.getASCII_string(cpos, 64); cpos += 64;
			
			iVersion = Byte.toUnsignedInt(src.getByte(cpos)); cpos++;
			iVersion_ca_crl = Byte.toUnsignedInt(src.getByte(cpos)); cpos++;
			iVersion_signer_crl = Byte.toUnsignedInt(src.getByte(cpos)); cpos++;
			cpos++; //Padding
			
			iSystemVersion = src.longFromFile(cpos); cpos += 8;
			//sTitleID = src.getASCII_string(cpos, 8); cpos += 8;
			iTitleID = src.longFromFile(cpos); cpos += 8;
			eTitleType = src.intFromFile(cpos); cpos += 4;
			iGroupID = Short.toUnsignedInt(src.shortFromFile(cpos)); cpos += 2;
			cpos += 2; //Padding
			eRegion = Short.toUnsignedInt(src.shortFromFile(cpos)); cpos += 2;
			
			for(int i = 0; i < 16; i++){aRatings[i] = src.getByte(cpos); cpos++;}
			cpos += 12; //Padding
			for(int i = 0; i < 12; i++){aIPCMask[i] = src.getByte(cpos); cpos++;}
			cpos += 18; //Padding
			mAccessRights = src.intFromFile(cpos); cpos += 4;
			
			iTitleVersion = Short.toUnsignedInt(src.shortFromFile(cpos)); cpos += 2;
			iContentCount = Short.toUnsignedInt(src.shortFromFile(cpos)); cpos += 2;
			iBootIndex = Short.toUnsignedInt(src.shortFromFile(cpos)); cpos += 2;
			cpos += 2; //Padding
			
			//Read the "Content" records
			aContents = new Content[iContentCount];
			for(int i = 0; i < iContentCount; i++)
			{
				aContents[i] = new Content(src, cpos);
				cpos += Content.SIZE_LONG;
			}
		}
		
		/* --- Getters --- */
		
		public int getSigType(){return eSigType;}
		public byte[] getSignature(){return aSignature;}
		public String getSignatureIssuer(){return sIssuer;}
		public int getVersion(){return iVersion;}
		public int getCAVersion(){return iVersion_ca_crl;}
		public int getSignerVersion(){return iVersion_signer_crl;}
		public long getSystemVersion(){return iSystemVersion;}
		public long getTitleID(){return iTitleID;}
		public int getTitleType(){return eTitleType;}
		public int getGroupID(){return iGroupID;}
		public int getRegion(){return eRegion;}
		public byte[] getRatings(){return aRatings;}
		public byte[] getIPCMask(){return aIPCMask;}
		public int getAccessMask(){return mAccessRights;}
		public int getTitleVersion(){return iTitleVersion;}
		public int getContentCount(){return iContentCount;}
		public int getBootIndex(){return iBootIndex;}
		public Content getContent(int index){return aContents[index];}
		
		/* --- Serialization --- */
		
		public FileBuffer serializeTMD()
		{
			final int TMD_MAIN_SIZE = 0x1E4;
			final byte ZERO = 0x00;
			
			FileBuffer tmd = new CompositeBuffer(1 + iContentCount);
			FileBuffer tmd_main = new FileBuffer(TMD_MAIN_SIZE, true);
			
			tmd_main.addToFile(eSigType);
			for(int i = 0; i < 0x100; i++){tmd_main.addToFile(aSignature[i]);}
			for(int i = 0; i < 60; i++){tmd_main.addToFile(ZERO);} //Padding
			
			if(sIssuer.length() > 64) sIssuer = sIssuer.substring(0, 64);
			tmd_main.printASCIIToFile(sIssuer);
			int slen = sIssuer.length();
			while(slen < 64){tmd_main.addToFile(ZERO); slen++;}
			
			tmd_main.addToFile((byte)iVersion);
			tmd_main.addToFile((byte)iVersion_ca_crl);
			tmd_main.addToFile((byte)iVersion_signer_crl);
			tmd_main.addToFile(ZERO); //Padding
			
			tmd_main.addToFile(iSystemVersion);
			tmd_main.addToFile(iTitleID);
			tmd_main.addToFile(eTitleType);
			tmd_main.addToFile((short)iGroupID);
			tmd_main.addToFile((short)0); //Padding
			tmd_main.addToFile((short)eRegion);
			for(int i = 0; i < 16; i++){tmd_main.addToFile(aRatings[i]);}
			for(int i = 0; i < 12; i++){tmd_main.addToFile(ZERO);} //Reserved
			for(int i = 0; i < 12; i++){tmd_main.addToFile(aIPCMask[i]);}
			for(int i = 0; i < 18; i++){tmd_main.addToFile(ZERO);} //Reserved
			tmd_main.addToFile(mAccessRights);
			tmd_main.addToFile((short)iTitleVersion);
			tmd_main.addToFile((short)iContentCount);
			tmd_main.addToFile((short)iBootIndex);
			tmd_main.addToFile((short)0); //Padding
			
			tmd.addToFile(tmd_main);
			
			//Contents
			for(int i = 0; i < aContents.length; i++) tmd.addToFile(aContents[i].serializeContent());

			return tmd;
		}
		
		public void printInfo()
		{
			System.out.println("----- Wii Partition TMD -----");
			System.out.println("Sig Type: 0x" + Integer.toHexString(eSigType));
			System.out.println("Sig Key: ");
			for(int i = 0; i < aSignature.length; i++)
			{
				System.out.print(String.format("%02x ", aSignature[i]));
				if(i%16 == 15) System.out.println();
			}
			if(aSignature.length % 16 != 0) System.out.println();
			System.out.println("Sig Issuer: " + sIssuer);
			System.out.println("Version: " + iVersion);
			System.out.println("Version (CA CRL): " + iVersion_ca_crl);
			System.out.println("Version (SIGNER CRL): " + iVersion_signer_crl);
			System.out.println("System Version: 0x" + Long.toHexString(iSystemVersion));
			System.out.println("Title ID: 0x" + Long.toHexString(iTitleID));
			System.out.println("Title Type: " + eTitleType);
			System.out.println("Group ID: 0x" + Integer.toHexString(iGroupID));
			System.out.println("Region: " + eRegion);
			System.out.println("Ratings: ");
			for(int i = 0; i < aRatings.length; i++)
			{
				System.out.print(String.format("%02x ", aRatings[i]));
				if(i%16 == 15) System.out.println();
			}
			if(aRatings.length % 16 != 0) System.out.println();
			System.out.println("IPC Mask: ");
			for(int i = 0; i < aIPCMask.length; i++)
			{
				System.out.print(String.format("%02x ", aIPCMask[i]));
				if(i%16 == 15) System.out.println();
			}
			if(aIPCMask.length % 16 != 0) System.out.println();
			System.out.println("Access Rights: 0x" + Integer.toHexString(mAccessRights));
			System.out.println("Title Version: " + iTitleVersion);
			System.out.println("Content Count: " + iContentCount);
			System.out.println("Boot Index: " + iBootIndex);
			for(int i = 0; i < aContents.length; i++)
			{
				System.out.println("Content " + i + ":");
				if(aContents[i] != null) aContents[i].printInfo();
			}
		}
		
	}
	
	public static class Content
	{
		/* --- Constants --- */
		
		public static final int SIZE = 0x24;
		public static final long SIZE_LONG = 0x24L;
		
		/* --- Instance Variables --- */
		
		private int iContentID;
		private int iIndex;
		private int eType;
		private long iSize;
		private byte[] aSHA;
		
		/* --- Construction/Parsing --- */
		
		public Content()
		{
			iContentID = 0;
			iIndex = 0;
			eType = 0;
			iSize = 0;
			aSHA = new byte[20];
		}
		
		public Content(FileBuffer src, long stpos)
		{
			this();
			long cpos = stpos;
			
			iContentID = src.intFromFile(cpos); cpos += 4;
			iIndex = Short.toUnsignedInt(src.shortFromFile(cpos)); cpos += 2;
			eType = Short.toUnsignedInt(src.shortFromFile(cpos)); cpos += 2;
			iSize = src.longFromFile(cpos); cpos += 8;
			for(int i = 0; i < 20; i++){aSHA[i] = src.getByte(cpos); cpos++;}
		}
	
		/* --- Getters --- */
		
		public int getContentID(){return iContentID;}
		public int getIndex(){return iIndex;}
		public int getType(){return eType;}
		public long getSize(){return iSize;}
		public byte[] getSHAHash(){return aSHA;}
		
		/* --- Serialization --- */
		
		public FileBuffer serializeContent()
		{
			FileBuffer content = new FileBuffer(SIZE, true);
			content.addToFile(iContentID);
			content.addToFile((short)iIndex);
			content.addToFile((short)eType);
			content.addToFile(iSize);
			for(int i = 0; i < 20; i++){content.addToFile(aSHA[i]);}
			
			return content;
		}
		
		public void printInfo()
		{
			System.out.println("\tContent ID: " + iContentID);
			System.out.println("\tIndex: " + iIndex);
			System.out.println("\tType: " + eType);
			System.out.println("\tSize: 0x" + Long.toHexString(iSize));
			System.out.println("\tSHA Checksum: ");
			System.out.print("\t\t");
			for(int i = 0; i < aSHA.length; i++)
			{
				System.out.print(String.format("%02x ", aSHA[i]));
				if(i%10 == 9){
					System.out.println();
					System.out.print("\t\t");
				}
			}
		}
	
	}
	
	public static class Certificate
	{
		public int eSigType;
		public byte[] sSignature;
		public String sIssuer;
		public int eKeyType;
		public String sName;
		public byte[] aKey;
		
		public static int getSignatureLength(int sigType)
		{
			switch(sigType)
			{
			case SIGTYPE_RSA4096: return SIGLEN_RSA4096;
			case SIGTYPE_RSA2048: return SIGLEN_RSA2048;
			case SIGTYPE_ECCB223: return SIGLEN_ECCB223;
			}
			return 256;
		}

		public static int getKeyLength(int keyType)
		{
			switch(keyType)
			{
			case KEYTYPE_RSA4096: return KEYLEN_RSA4096;
			case KEYTYPE_RSA2048: return KEYLEN_RSA2048;
			case KEYTYPE_ECCB223: return KEYLEN_ECCB223;
			}
			return 0;
		}

		public Certificate(FileBuffer src, long stpos)
		{
			long cpos = stpos;
			eSigType = src.intFromFile(cpos); cpos += 4;
			int siglen = getSignatureLength(eSigType);
			sSignature = new byte[siglen];
			for(int i = 0; i < siglen; i++){sSignature[i] = src.getByte(cpos); cpos++;}
			//Pad to 64 (?)
			cpos += 60;
			sIssuer = src.getASCII_string(cpos, 64); cpos += 64;
			eKeyType = src.intFromFile(cpos); cpos += 4;
			sName = src.getASCII_string(cpos, 64); cpos += 64;
			int keylen = getKeyLength(eKeyType);
			aKey = new byte[keylen];
			for(int i = 0; i < keylen; i++){aKey[i] = src.getByte(cpos); cpos++;}
		}
		
		public int getSize()
		{
			int sz = 4;
			sz += getSignatureLength(eSigType);
			sz += 60; //Padding 1
			sz += 64;
			sz += 4;
			sz += 64;
			sz += getKeyLength(eKeyType);
			sz += 64 - (sz % 64); //Padding 2
			return sz;
		}
		
		public FileBuffer serializeCert()
		{
			FileBuffer cert = new FileBuffer(getSize(), true);
			cert.addToFile(eSigType);
			for(int i = 0; i < sSignature.length; i++) cert.addToFile(sSignature[i]);
			//Padding
			for(int i = 0; i < 60; i++) cert.addToFile((byte)0x00);
			
			if(sIssuer.length() > 64) sIssuer = sIssuer.substring(0, 63);
			cert.printASCIIToFile(sIssuer);
			int ssz = sIssuer.length();
			while(ssz < 64){cert.addToFile((byte)0x00); ssz++;}
			cert.addToFile(eKeyType);
			if(sName.length() > 64) sName = sName.substring(0, 64);
			cert.printASCIIToFile(sName);
			ssz = sName.length();
			while(ssz < 64){cert.addToFile((byte)0x00); ssz++;}
			for(int i = 0; i < aKey.length; i++) cert.addToFile(aKey[i]);
			if((aKey.length + 4) % 64 != 0)
			{
				int padding = 64 - ((aKey.length + 4) % 64);
				for(int i = 0; i < padding; i++) cert.addToFile((byte)0x00);
			}
			
			return cert;
		}
	
		public void printInfo()
		{
			System.out.println("----- Wii Partition Certificate -----");
			System.out.println("Sig Type: 0x" + Integer.toHexString(eSigType));
			System.out.println("Sig Key: ");
			for(int i = 0; i < sSignature.length; i++)
			{
				System.out.print(String.format("%02x ", sSignature[i]));
				if(i%16 == 15) System.out.println();
			}
			if(sSignature.length % 16 != 0) System.out.println();
			System.out.println("Sig Issuer: " + sIssuer);
			
			System.out.println("Key Type: " + eKeyType);
			System.out.println("Name: " + sName);
			System.out.println("Key: ");
			for(int i = 0; i < aKey.length; i++)
			{
				System.out.print(String.format("%02x ", aKey[i]));
				if(i%16 == 15) System.out.println();
			}
			if(aKey.length % 16 != 0) System.out.println();
		}
		
	}
	
	/* ----- Instance Variables ----- */
	
	private GCWiiHeader oHeader;
	private Partition[] oParts;
	
	private FileBuffer rawRegionInfo;
	
	/* ----- Construction ----- */
	
	private WiiDisc()
	{
		//oHeader = new Header();
		oParts = new Partition[4];
	}
	
	/* ----- Parsing ----- */
	
	public static WiiDisc parseFromData(FileBuffer src) throws IOException, UnsupportedFileTypeException
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
		for (int i = 0; i < 4; i++)
		{
			pcounts[i] = src.intFromFile(cpos); cpos += 4;
			pptrs[i] = src.intFromFile(cpos) << 2; cpos += 4;
		}
		//for (int i = 0; i < 4; i++){System.err.println("Partition " + i + "|| Offset: 0x" + Integer.toHexString(pptrs[i]) + " | Count: " + pcounts[i]);}
		
		//Read partition info tables
		//	Partition Offset (SRL 2) [4]
		//	Partition Type Enum [4]
		for (int i = 0; i < 4; i++)
		{
			if (pcounts[i] > 0)
			{
				//System.err.println("WBFSImage.readWiiDisc || -DEBUG- Partition " + i + " has data!");
				Partition part = new Partition(pcounts[i]);
				disc.oParts[i] = part;
				cpos = pptrs[i];
				for(int j = 0; j < pcounts[i]; j++)
				{
					long off = Integer.toUnsignedLong(src.intFromFile(cpos)) << 2; cpos += 4;
					int type = src.intFromFile(cpos); cpos += 4;
					part.addPartition(j, type, off);
					//System.err.println("WBFSImage.readWiiDisc || -DEBUG- Partition " + i + "-" + j + " has data!");
					//System.err.println("WBFSImage.readWiiDisc || -DEBUG- Offset: 0x" + Long.toHexString(off));
					//System.err.println("WBFSImage.readWiiDisc || -DEBUG- Type: " + type);
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
		
		//Now, parse the partitions...
		for(int i = 0; i < disc.oParts.length; i++)
		{
			if (disc.oParts[i] != null) disc.oParts[i].readPartion(src);
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
	
	public Partition getPartition(int index)
	{
		return oParts[index];
	}
	
	/* ----- Cleanup ----- */
	
	public void deleteParsingTempFiles() throws IOException
	{
		for(int i = 0; i < oParts.length; i++)
		{
			if (oParts[i] != null) oParts[i].deleteTempFiles();
		}
	}
	
}
