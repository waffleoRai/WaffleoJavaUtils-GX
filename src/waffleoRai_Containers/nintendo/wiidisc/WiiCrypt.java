package waffleoRai_Containers.nintendo.wiidisc;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;

import waffleoRai_Containers.nintendo.WiiDisc;
import waffleoRai_Encryption.AES;
import waffleoRai_Utils.FileBuffer;

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
						observer.onDecryptionComplete(false);
					}
				}
				observer.onDecryptionComplete(isgood);
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
	
	public static class WiiCryptHashFailedException extends Exception{
		private static final long serialVersionUID = -2560438678823788304L;
	}
	
}
