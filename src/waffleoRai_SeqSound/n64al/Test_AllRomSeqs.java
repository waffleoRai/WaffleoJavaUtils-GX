package waffleoRai_SeqSound.n64al;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;

import waffleoRai_Compression.nintendo.YazDecodeStream;
import waffleoRai_Containers.nintendo.nus.N64ROMImage;
import waffleoRai_Containers.nintendo.nus.N64ZFileTable;
import waffleoRai_Containers.nintendo.nus.N64ZFileTable.Entry;
import waffleoRai_SeqSound.MIDI;
import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.FileBuffer;

public class Test_AllRomSeqs {

	private static class TimeoutParser implements Runnable{

		private FileBuffer rawdat = null;
		private volatile NUSALSeq result = null;
		private volatile Exception error = null;
		
		public TimeoutParser(FileBuffer input){
			rawdat = input;
		}
		
		public void run() {
			try{
				synchronized(this){result = NUSALSeq.readNUSALSeq(rawdat);}
			}
			catch(InterruptedException ex){
				System.err.println("Test_AllRomSeqs.TimeoutParser.run || Parser timed out and was interrupted.");
				synchronized(this){
					result = null;
					error = null;
				}
			}
			catch(Exception ex){
				synchronized(this){
					error = ex;
					result = null;
				}
			}
		}
		
		public synchronized NUSALSeq getResult(){return result;}
		public synchronized Exception getError(){return error;}
		
	}
	
	public static class SeqInfoEntry{
		private int offset = -1;
		private int size = 0;
		private int medium = 0;
		private int cachePolicy = 0;
		
		public static SeqInfoEntry readEntry(BufferReference ptr){
			SeqInfoEntry entry = new SeqInfoEntry();
			entry.offset = ptr.nextInt();
			entry.size = ptr.nextInt();
			entry.medium = (int)ptr.nextByte();
			entry.cachePolicy = (int)ptr.nextByte();
			ptr.add(6);
			return entry;
		}
		
		public int getOffset(){return offset;}
		public int getSize(){return size;}
		public int getMedium(){return medium;}
		public int getCachePolicy(){return cachePolicy;}
		
		public void setOffset(int val){offset = val;}
		public void setSize(int val){size = val;}
		public void setMedium(int val){medium = val;}
		public void setCachePolicy(int val){cachePolicy = val;}
		
		public void serializeTo(FileBuffer buffer){
			buffer.addToFile(offset);
			buffer.addToFile(size);
			buffer.addToFile((byte)medium);
			buffer.addToFile((byte)cachePolicy);
			buffer.addToFile((short)0);
			buffer.addToFile(0);
		}
	}
	
	public static NUSALSeq parseSeqWithTimeout(FileBuffer rawdata, int timeout_millis){
		TimeoutParser runner = new TimeoutParser(rawdata);
		Thread th = new Thread(runner);
		int wait_count = 0;
		final int SLEEP_TIME = 50;
		
		th.setDaemon(true);
		th.start();
		while((th.isAlive()) && (wait_count < timeout_millis)){
			try {Thread.sleep(SLEEP_TIME);} 
			catch (InterruptedException e) {
				if(th.isAlive()){
					synchronized(th){th.interrupt();}
					return null;
				}
				break;
			}
			wait_count += SLEEP_TIME;
		}
		
		if(th.isAlive()){
			//Time out and kill
			synchronized(th){th.interrupt();}
			return null;
		}
		
		//Wait for it to finish.
		while(th.isAlive()){
			try {Thread.sleep(10);} 
			catch (InterruptedException e) {
				return null;
			}
		}
		
		NUSALSeq seq = runner.getResult();
		Exception ex = runner.getError();
		if(seq != null && ex == null) return seq;
		
		return null;
	}
	
	public static void main(String[] args) {
		String inpath = args[0];
		String outpath = args[1];
		
		//NZLP
		long off_dmadata = 0x12f70;
		int dmadata_idx_seq = 4;
		int dmadata_idx_code = 28;
		long off_seqtable = 0x1386a0;
		
		try {
			N64ROMImage rominfo = N64ROMImage.readROMHeader(inpath);
			if(rominfo == null){
				System.err.println("Input file not recognized as N64 ROM image.");
				System.exit(1);
			}
			
			FileBuffer romdat = N64ROMImage.loadROMasZ64(inpath);
			N64ZFileTable dmadata = N64ZFileTable.readTable(romdat, off_dmadata);
			
			//Read audioseq
			System.err.println("Reading in audioseq...");
			Entry file_entry = dmadata.getEntry(dmadata_idx_seq);
			long mystart = file_entry.getROMAddress();
			int mysize = (int)file_entry.getSizeOnROM();
			FileBuffer audioseq = romdat.createCopy(mystart, mystart + mysize);
			
			FileBuffer codeFile = null;
			file_entry = dmadata.getEntry(dmadata_idx_code);
			mystart = file_entry.getROMAddress();
			if(file_entry.isCompressed()) {
				System.err.println("Decompressing code...");
				int uc_size = (int)file_entry.getSize();
				int c_size = (int)file_entry.getSizeOnROM();
				FileBuffer yazFile = romdat.createReadOnlyCopy(mystart, mystart + c_size);
				codeFile = new FileBuffer(uc_size, true);
				
				YazDecodeStream decstr = YazDecodeStream.getDecoderStream(yazFile);
				for(int j = 0; j < uc_size; j++){
					codeFile.addToFile((byte)decstr.read());
				}
				yazFile.dispose();
			}
			else {
				System.err.println("Reading in code...");
				mysize = (int)file_entry.getSizeOnROM();
				codeFile = romdat.createCopy(mystart, mystart + mysize);
			}
			
			//Read seq table from code
			System.err.println("Parsing sequence table...");
			BufferReference ptr = codeFile.getReferenceAt(off_seqtable);
			int seqcount = Short.toUnsignedInt(ptr.nextShort());
			ptr.add(14);
			
			SeqInfoEntry[] tbl = new SeqInfoEntry[seqcount];
			for(int i = 0; i < seqcount; i++){
				tbl[i] = SeqInfoEntry.readEntry(ptr);
			}
			
			//Iterate through and try to extract and read.
			for(int i = 0; i < seqcount; i++){
				if(tbl[i].size < 1) continue;
				String seqname = String.format("audioseq_%03d", i);
				System.err.println("Working on " + seqname + "...");
				FileBuffer bin = audioseq.createReadOnlyCopy(tbl[i].offset, tbl[i].offset + tbl[i].size);
				String foutpath = outpath + File.separator + seqname + ".bin";
				bin.writeFile(foutpath);
				
				//Try to read.
				System.err.println("\t> Attempting parse...");
				NUSALSeq mySeq = parseSeqWithTimeout(bin, 10000);
				if(mySeq != null) {
					System.err.println("\t> Parse succeeded(?) Exporting MML script...");
					foutpath = outpath + File.separator + seqname + ".mus";
					BufferedWriter bw = new BufferedWriter(new FileWriter(foutpath));
					mySeq.exportMMLScript(bw, true, NUSALSeq.SYNTAX_SET_ZEQER);
					bw.close();
					
					//Try mid
					System.err.println("\t> Exporting MIDI...");
					foutpath = outpath + File.separator + seqname + ".mid";
					try{
						MIDI midi = mySeq.toMidi();
						midi.writeMIDI(foutpath);
					}
					catch(Exception ex) {
						System.err.println("\t> lol nope");
						ex.printStackTrace();
					}
				}
				else {
					System.err.println("\t> Parse failed :(");
				}
				
				bin.dispose();
			}
			
		}
		catch(Exception ex) {
			ex.printStackTrace();
			System.exit(1);
		}
		
		
	}

}
