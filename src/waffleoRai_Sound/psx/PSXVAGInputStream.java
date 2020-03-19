package waffleoRai_Sound.psx;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.Random;

import waffleoRai_Sound.psx.PSXVAG.Chunk;

public class PSXVAGInputStream extends InputStream{

	/* ----- Instance Variables ----- */
	
	private PSXVAG iSource;
	
	private int iChunkIndex;
	private int iFrameIndex;
	private int iByteIndex;
	
	private boolean bIsOpen;
	
	private boolean bEndReached;
	
	/* ----- Construction ----- */
	
	public PSXVAGInputStream(PSXVAG source, int bufferedChunks)
	{
		iSource = source;
		
		iChunkIndex = 0;
		iFrameIndex = 0;
		iByteIndex = 0;
		
		bIsOpen = false;
		bEndReached = false;
		
		if (iSource == null) return;
		
		iMarkedChunkIndex = 0;
		iMarkedFrameIndex = 0;
		iMarkedByteIndex = 0;
		//iBytesReadSinceMark = 0;
		iBytesToReadLimit = -1;
		iMarkedChunk = null;
		bMarkSet = false;
		
		iBufferLoaderChunkIndex = 0;
		iBuffer = new ChunkQueue();
		iCurrentChunk = null;
		
		iLastSample = 0;
		iSampleBeforeLast = 0;
		
		bLoops = iSource.loops();
		iLoopChunkIndex = iSource.getLoopPointChunkIndex();
		iLoopChunk = null;
		
		loadLoopChunk();
		
		if (bufferedChunks < 2) bufferedChunks = 2;
		nBufferedChunks = bufferedChunks;
		bBufferLoaderEndReached = false;
		
		iBufferThread = new BufferThread();
		iBufferThread.start();
		bIsOpen = true;
	}
	
	/* ----- Buffer ----- */
	
	private boolean bLoops;
	private int iLoopChunkIndex;
	private int[] iLoopChunk;
	
	private int iLastSample;
	private int iSampleBeforeLast;
	
	private byte[] iCurrentChunk;
	private ChunkQueue iBuffer;
	private int nBufferedChunks;
	private BufferThread iBufferThread;
	
	private int iBufferLoaderChunkIndex;
	private boolean bBufferLoaderEndReached;
	
	private class ChunkQueue
	{
		private LinkedList<byte[]> queue;
		
		public ChunkQueue()
		{
			queue = new LinkedList<byte[]>();
		}
		
		public synchronized byte[] pop()
		{
			try
			{
				return queue.pop();
			}
			catch (Exception e)
			{
				return null;
			}
		}
		
		public synchronized void add(byte[] chunk)
		{
			queue.addLast(chunk);
		}
		
		public synchronized int size()
		{
			return queue.size();
		}
		
		public synchronized void clearMe()
		{
			queue.clear();
		}
		
	}
	
	private class BufferThread extends Thread
	{
		private boolean kill;
		private boolean pausedRequested;
		private boolean paused;
		
		public BufferThread()
		{
			kill = false;
			pausedRequested = false;
			paused = false;
			Random r = new Random();
			this.setName("PSXVAGStream_BufferDaemon_" + Long.toHexString(r.nextLong()));
			this.setDaemon(true);
		}
		
		public void run()
		{
			while (!killSet())
			{
				Thread.interrupted();
				//Check for pause requests
				if (pauseRequested())
				{
					paused = true;
					pausedRequested = false;
				}
				//Check for pause
				if (!isPaused())
				{
					//Check to see if buffer is full
					int nChunks = iBuffer.size();
					while (nChunks < nBufferedChunks)
					{
						if (iBufferLoaderChunkIndex >= iSource.countChunks())
						{
							if (!bLoops)
							{
								bBufferLoaderEndReached = true;
								//Do nothing - wait for any index move later on...
								break;
							}
							else
							{
								iBufferLoaderChunkIndex = iLoopChunkIndex;
								iLastSample = iLoopChunk[27];
								iSampleBeforeLast = iLoopChunk[26];
								iBuffer.add(splitSamples_16LE(iLoopChunk));
							}
						}
						else
						{
							Chunk c = iSource.getChunk(iBufferLoaderChunkIndex);
							int[] decsamps = c.decompressSamples(iLastSample, iSampleBeforeLast);
							iLastSample = decsamps[27];
							iSampleBeforeLast = decsamps[26];
							iBuffer.add(splitSamples_16LE(decsamps));	
						}
						iBufferLoaderChunkIndex++;
						nChunks = iBuffer.size();
					}	
				}
				//Sleep
				try 
				{
					Thread.sleep(1000);
				} 
				catch (InterruptedException e) {
					Thread.interrupted();
				}
			}
		}
		
		public synchronized boolean killSet()
		{
			return kill;
		}
		
		public synchronized void requestKill()
		{
			kill = true;
			this.interrupt();
		}
		
		public synchronized void interruptMe()
		{
			this.interrupt();
		}
		
		private synchronized boolean pauseRequested()
		{
			return pausedRequested;
		}
		
		public synchronized boolean isPaused()
		{
			return paused;
		}
		
		public synchronized void pause()
		{
			pausedRequested = true;
		}
		
		public synchronized void unpause()
		{
			pausedRequested = false;
			paused = false;
			interruptMe();
		}
	}
	
	private void clearBuffer()
	{
		iBuffer.clearMe();
	}
	
	private void loadNextChunk()
	{
		iCurrentChunk = iBuffer.pop();
		iBufferThread.interruptMe();
	}
	
	/* ----- Data Retrieval/Decompression ----- */
	
	private void loadLoopChunk()
	{
		if (!bLoops) return;
		iLoopChunk = iSource.getDecompressedLoopChunk();
	}
	
	public byte[] splitSamples_16LE(int[] samples)
	{
		if (samples == null) return null;
		byte[] mybytes = new byte[samples.length * 2];
		for (int i = 0; i < samples.length; i++)
		{
			int samp = samples[i];
			int b0 = samp & 0xFF;
			int b1 = (samp >>> 8) & 0xFF;
			int j = i*2;
			mybytes[j] = (byte)b0;
			mybytes[j+1] = (byte)b1;
		}
		
		return mybytes;
	}
	
	public void setLastSamples(byte[] chunkBytes)
	{
		int n1b1 = Byte.toUnsignedInt(chunkBytes[55]);
		int n1b0 = Byte.toUnsignedInt(chunkBytes[54]);
		int n2b1 = Byte.toUnsignedInt(chunkBytes[53]);
		int n2b0 = Byte.toUnsignedInt(chunkBytes[52]);
		iLastSample = (n1b1 << 8) | n1b0;
		iSampleBeforeLast = (n2b1 << 8) | n2b0;
	}
	
	public int bytesLeft()
	{
		if (bLoops) return -1;
		int maxchunks = iSource.countChunks();
		int chunksleft = maxchunks - iChunkIndex;
		int framesleft = 27 - iFrameIndex;
		int bytesleft = 1 - iByteIndex;
		return (chunksleft * 56) + (framesleft * 2) + (bytesleft);
	}
	
	public int bytesLeftInChunk()
	{
		int b = ((28 - iFrameIndex) * 2) - iByteIndex;
		return b;
	}
	
	public int getChunkByteIndex()
	{
		return (iFrameIndex * 2) + iByteIndex;
	}
	
	public void advanceMarker()
	{
		if (bEndReached) return;
		iByteIndex++;
		if (iByteIndex > 1)
		{
			iFrameIndex++;
		}
		if (iFrameIndex >= 28)
		{
			iChunkIndex++;
			int maxchunks = iSource.countChunks();
			if (iChunkIndex >= maxchunks)
			{
				if (!bLoops)
				{
					bEndReached = true;
				}
				else
				{
					loadNextChunk();
					iChunkIndex = iLoopChunkIndex;
				}
			}
			else loadNextChunk();
		}
		if (bMarkSet)
		{
			//iBytesReadSinceMark++;
			iBytesToReadLimit--;
			if (iBytesToReadLimit < 0) bMarkSet = false;
		}
	}
	
	public void advanceMarkerToNextChunk()
	{
		if (bEndReached) return;
		
		int bRead = ((28 - iFrameIndex) * 2) - iByteIndex;
		
		iByteIndex = 0;
		iFrameIndex = 0;
		
		iChunkIndex++;
		int maxchunks = iSource.countChunks();
		if (iChunkIndex >= maxchunks)
		{
			if (!bLoops)
			{
				bEndReached = true;
			}
			else
			{
				loadNextChunk();
				iChunkIndex = iLoopChunkIndex;
			}
		}
		else loadNextChunk();
			
		if (bMarkSet)
		{
			//iBytesReadSinceMark += bRead;
			iBytesToReadLimit -= bRead;
			if (iBytesToReadLimit < 0) bMarkSet = false;
		}
	}
	
	/* ----- Mark ----- */
	
	private boolean bMarkSet;
	
	private int iMarkedChunkIndex;
	private int iMarkedFrameIndex;
	private int iMarkedByteIndex;
	
	private byte[] iMarkedChunk;
	
	//private int iBytesReadSinceMark;
	private int iBytesToReadLimit;
	
	/* ----- InputStream Methods ----- */
	
	public int available()
	{
		if (!bIsOpen) return 0;
		if (bEndReached) return 0;
		int bchunks = iBuffer.size();
		return iCurrentChunk.length + (bchunks * 56);
	}
	
	public void close()
	{
		if (!bIsOpen) return;
		iBufferThread.requestKill();
		bIsOpen = false;
	}
	
	public void mark(int readlimit)
	{
		if (!bIsOpen) return;
		bMarkSet = true;
		iMarkedChunkIndex = iChunkIndex;
		iMarkedFrameIndex = iFrameIndex;
		iMarkedByteIndex = iByteIndex;
		
		iMarkedChunk = iCurrentChunk;
		//iBytesReadSinceMark = 0;
		iBytesToReadLimit = readlimit;
	}
	
	public boolean markSupported()
	{
		return (bIsOpen && !bEndReached);
	}
	
	@Override
	public int read() throws IOException 
	{
		if (!bIsOpen) return -1;
		if (bEndReached) return -1;
		int bi = iFrameIndex * 2;
		bi += iByteIndex;
		byte b = iCurrentChunk[bi];
		advanceMarker();
		return Byte.toUnsignedInt(b);
	}
	
	public int read(byte[] b)
	{
		if (!bIsOpen) return 0;
		if (b == null) return 0;
		if (b.length == 0) return 0;
		int len = b.length;
		int remaining = len;
		int read = 0;
		
		int bidx = getChunkByteIndex();
		while (remaining > 0 && !bEndReached)
		{
			byte by = iCurrentChunk[bidx];
			b[read] = by;
			bidx++;
			if (bidx >= 56)
			{
				advanceMarkerToNextChunk();
				bidx = 0;
			}
			read++;
			remaining--;
		}
		
		return read;
	}
	
	public int read(byte[] b, int off, int len)
	{
		if (!bIsOpen) return 0;
		if (b == null) return 0;
		if (b.length == 0) return 0;

		int remaining = len;
		int read = 0;
		
		int bidx = getChunkByteIndex();
		int tidx = off;
		while (remaining > 0 && !bEndReached && tidx < b.length)
		{
			byte by = iCurrentChunk[bidx];
			b[tidx] = by;
			bidx++;
			if (bidx >= 56)
			{
				advanceMarkerToNextChunk();
				bidx = 0;
			}
			read++;
			tidx++;
			remaining--;
		}
		
		return read;
	}
	
	public void reset()
	{
		if (!bIsOpen) return;
		if (!bMarkSet) return;
		if (iBytesToReadLimit < 0) return;
		iBufferThread.pause();
		iChunkIndex = iMarkedChunkIndex;
		iFrameIndex = iMarkedFrameIndex;
		iByteIndex = iMarkedByteIndex;
		iCurrentChunk = iMarkedChunk;
		bEndReached = false;
		//Make sure buffer thread is paused now...
		while (!iBufferThread.isPaused())
		{
			try 
			{
				Thread.sleep(10);
			} 
			catch (InterruptedException e) 
			{
				e.printStackTrace();
			}
		}
		setLastSamples(iCurrentChunk);
		iBufferLoaderChunkIndex = iChunkIndex + 1;
		if (bBufferLoaderEndReached) bBufferLoaderEndReached = false;
		clearBuffer();
		iBufferThread.unpause();
	}
	
	public long skip(long n)
	{
		if (!bIsOpen) return 0;
		if (n <= 0) return 0;
		
		int bidx = getChunkByteIndex();
		long remaining = n;
		long skipped = 0;
		
		//Advance to end of chunk
		if (remaining >= (56 - bidx))
		{
			advanceMarkerToNextChunk();
			skipped += (56 - bidx);
			remaining -= skipped;
		}
		
		//Take out full chunks
		if (remaining >= 56 && !bEndReached)
		{
			long skipchunks = remaining/56;
			while(skipchunks > 0 && !bEndReached)
			{
				advanceMarkerToNextChunk();
				remaining -= 56;
				skipped += 56;
				skipchunks--;
			}
		}
		
		//Advance remainder
		if (remaining > 0 && !bEndReached)
		{
			int frames = (int)remaining/2;
			int bytes = (int)remaining % 2;
			iFrameIndex += frames;
			iByteIndex += bytes;
			skipped += remaining;
			remaining = 0;
		}
		
		return skipped;
	}

}
