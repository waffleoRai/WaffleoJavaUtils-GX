package waffleoRai_Containers.nintendo.wiidisc;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.concurrent.ConcurrentLinkedQueue;

import waffleoRai_Containers.nintendo.WiiDisc;
import waffleoRai_Encryption.AES;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.StreamWrapper;

public class WiiCrypt {

	//0x8000 byte sectors
	//0x400 bytes of hash data followed by 0x7c00 bytes data
	//Hash table is also encrypted
	
	//private byte[] part_key;
	//private int threads = 4;
	private AES aes;
	private boolean confirm_hash = true;
	
	private ConcurrentLinkedQueue<byte[]> queue;
	private volatile boolean isgood = true;
	private volatile boolean done;
	private volatile int dec_count;
	private volatile int wrt_count;
	
	//private volatile boolean failed_hash_flag;
	
	public WiiCrypt(byte[] partitionKey){
		aes = new AES(partitionKey);
		confirm_hash = true;
	}
	
	public WiiCrypt(AES crypto){
		aes = crypto;
		confirm_hash = true;
	}
	
	public void setHashConfirm(boolean b){confirm_hash = b;}
	
	public byte[] decryptBlock(FileBuffer data, long offset) throws WiiCryptHashFailedException{
		WiiDataCluster cluster = new WiiDataCluster();
		cluster.readCluster(data, offset, aes);
		
		//Debug
		//System.err.println("Offset = 0x" + Long.toHexString(offset));
		/*String testpath = "";
		try {
			cluster.dumpHashTables(testpath + "\\hash_" + Long.toHexString(offset) + ".bin");
			cluster.dumpData(testpath + "\\data_" + Long.toHexString(offset) + ".bin");
		} catch (IOException e) {
			e.printStackTrace();
		}*/
		
		if(confirm_hash){
			if(cluster.isZeroBlock()){
				System.err.println("WiiCrypt.decryptBlock || Block at 0x" + Long.toHexString(offset) + " appears to be unused.");
			}
			if(!cluster.checkClusterHash()) throw new WiiCryptHashFailedException();
		}
		
		byte[] dec = cluster.getDecryptedData();
		
		return dec;
	}
	
	public String decryptPartitionData(FileBuffer data, long offset, int sector_count, WiiCryptListener observer) throws IOException{
		String temppath = FileBuffer.generateTemporaryPath("rvl_decrypt_buffer");
		if(decryptPartitionData(data, offset, sector_count, observer, temppath)) return temppath;
		return null;
	}
	
	public boolean decryptPartitionData(FileBuffer data, long offset, int sector_count, WiiCryptListener observer, String temppath) throws IOException{
		//Decryptor, Monitor (if applicable), Writer
		queue = new ConcurrentLinkedQueue<byte[]>();
		isgood = true;
		dec_count = 0;
		wrt_count = 0;
		done = false;
		
		if(observer != null) observer.setSectorCount(sector_count);
		
		Runnable crypter = new Runnable(){
			public void run() {
				long cpos = offset;
				for(int s = 0; s < sector_count; s++){
					try {
						byte[] block = decryptBlock(data, cpos);
						queue.add(block);
						dec_count++;
						cpos += WiiDisc.DATACLUSTER_SIZE;
					} 
					catch (WiiCryptHashFailedException e) {
						System.err.println("Block " + s + " failed hash check!");
						e.printStackTrace();
						isgood = false;
						return;
					}
				}
			}	
		};
		
		Runnable monitor = new Runnable(){

			public void run() {
				if(observer == null) return;
				observer.onPartitionStart();
				int sleeptime = observer.getUpdateFrequencyMillis();
				while(!done && isgood){
					if(dec_count < sector_count){
						observer.onSectorDecrypted(dec_count);
					}
					else observer.onSectorWrittenToBuffer(wrt_count);
					try {Thread.sleep(sleeptime);} 
					catch (InterruptedException e) {
						System.err.println("Unexpected interruption to decryption monitor thread! Terminating...");
						e.printStackTrace();
						observer.onPartitionDecryptionComplete(false);
					}
				}
				//observer.onDecryptionComplete(isgood);
				observer.onPartitionDecryptionComplete(isgood);
			}
			
		};
		
		//Start these two threads...
		Thread dec_thread = new Thread(crypter);
		dec_thread.setName("WiiCrypt_DecryptorThread");
		dec_thread.setDaemon(true);
		dec_thread.start();
		
		if(observer != null){
			Thread obs_thread = new Thread(monitor);
			obs_thread.setName("WiiCrypt_DecryptionMonitorThread");
			obs_thread.setDaemon(true);
			obs_thread.start();
		}
		
		//This behaves as writer until done...
		BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(temppath));
		while(isgood && (dec_count < sector_count)){
			while(!queue.isEmpty()){
				bos.write(queue.poll());
				wrt_count++;
			}
		}
		done = true;
		bos.close();
		
		return done && isgood && dec_count == sector_count && wrt_count == sector_count;
	}
	
	/**
	 * Encrypt data as it would be encrypted on a Wii disc partition.
	 * @param input Input data.
	 * @param output Stream to write output data.
	 * @return FileBuffer containing the h3 hash table.
	 * @throws IOException If there is an exception thrown while writing to output stream.
	 */
	public FileBuffer encryptPartitionData(StreamWrapper input, OutputStream output) throws IOException{
		
		FileBuffer h3 = new FileBuffer(WiiDisc.H3_TABLE_SIZE, true);
		
		while(!input.isEmpty()){
			//Group of 64 0x7c00 chunks
			byte[][] rawblocks = new byte[64][0x7c00];
			
			//Copy data
			for(int i = 0; i < 64; i++){
				byte[] block = rawblocks[i];
				for(int j = 0; j < 0x7c00; j++){
					if(!input.isEmpty()) block[j] = input.get();
				}
			}
			
			//Generate h0 tables...
			byte[][] h0 = new byte[64][20*31];
			for(int i = 0; i < 64; i++){
				byte[] block = rawblocks[i];
				int pos = 0;
				for(int j = 0; j < 31; j++){
					byte[] cluster = new byte[0x400];
					for(int k = 0; k < 0x400; k++){
						cluster[k] = block[pos++];
					}
					
					MessageDigest sha = null;
					try{sha = MessageDigest.getInstance("SHA-1");}
					catch(Exception x){x.printStackTrace(); return null;}
					
					sha.update(cluster);
					byte[] hash = sha.digest();
					int off = j*20;
					for(int k = 0; k < 20; k++){
						h0[i][off + k] = hash[k];
					}
				}
			}
			
			//Generate h1 tables...
			byte[][] h0hashes = new byte[64][20];
			for(int i = 0; i < 64; i++){
				byte[] blockh0 = h0[i];
				MessageDigest sha = null;
				try{sha = MessageDigest.getInstance("SHA-1");}
				catch(Exception x){x.printStackTrace(); return null;}
				
				sha.update(blockh0);
				byte[] hash = sha.digest();
				for(int j = 0; j < 20; j++) h0hashes[i][j] = hash[j];
			}
			byte[][] h1 = new byte[8][20*8];
			for(int i = 0; i < 8; i++){
				int start = i << 3;
				byte[] myh1 = h1[i];
				
				for(int j = 0; j < 8; j++){
					int off = j*20;
					byte[] h0hash = h0hashes[start+j];
					for(int k = 0; k < 20; k++) myh1[off+k] = h0hash[k];
				}
			}
			
			//Generate h2 table...
			byte[] h2 = new byte[20*8];
			for(int i = 0; i < 8; i++){
				MessageDigest sha = null;
				try{sha = MessageDigest.getInstance("SHA-1");}
				catch(Exception x){x.printStackTrace(); return null;}
				
				sha.update(h1[i]);
				byte[] hash = sha.digest();
				
				int off = i*20;
				for(int j = 0; j < 20; j++) h2[off+j] = hash[j];
			}
			
			//Hash and add to h3 table...
			MessageDigest sha = null;
			try{sha = MessageDigest.getInstance("SHA-1");}
			catch(Exception x){x.printStackTrace(); return null;}
			
			sha.update(h2);
			byte[] hash = sha.digest();
			for(int j = 0; j < 20; j++) h3.addToFile(hash[j]);
			
			//Generate hash clusters & encrypt
			byte[] blankiv = new byte[16];
			byte[][] hash_clusters = new byte[64][0x400];
			byte[][] hash_clusters_enc = new byte[64][0x400];
			for(int i = 0; i < 64; i++){
				int pos = 0;
				
				//Copy h0...
				byte[] myh0 = h0[i];
				for(int j = 0; j < myh0.length; j++){
					hash_clusters[i][pos++] = myh0[j];
				}
				
				//Padding
				for(int j = 0; j < 20; j++) hash_clusters[i][pos++] = 0;
				
				//Copy h1...
				int h1idx = i >>> 3;
				byte[] myh1 = h1[h1idx];
				for(int j = 0; j < myh1.length; j++){
					hash_clusters[i][pos++] = myh1[j];
				}
				
				//Padding
				for(int j = 0; j < 32; j++) hash_clusters[i][pos++] = 0;
				
				//Copy h2...
				for(int j = 0; j < h2.length; j++){
					hash_clusters[i][pos++] = h2[j];
				}
				
				hash_clusters_enc[i] = aes.encrypt(blankiv, hash_clusters[i]);
			}
			
			//Encrypt data & write
			for(int i = 0; i < 64; i++){
				//Determine IV
				byte[] iv = new byte[16];
				byte[] hashclust = hash_clusters_enc[i];
				for(int j = 0; j < 16; j++){
					iv[j] = hashclust[0x3D0 + j];
				}
				
				byte[] encdat = aes.encrypt(iv, rawblocks[i]);
				
				//Write out...
				output.write(hashclust);
				output.write(encdat);
			}
		}
		
		return h3;
	}
	
	public static class WiiCryptHashFailedException extends Exception{
		private static final long serialVersionUID = -2560438678823788304L;
	}
	
	
	
}