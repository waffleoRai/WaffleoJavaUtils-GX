package waffleoRai_Sound.nintendo;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import waffleoRai_Utils.Arunnable;

public class NinSoundStream extends InputStream{
	
	public static final int BUFFER_SIZE = 0x100000;

	private NinStreamableSound source;
	
	private ConcurrentLinkedQueue<Byte> buffer;
	private List<Iterator<Integer>> activeIterators;
	
	private NinADPCM[] dsp_state;
	private NinIMAADPCM[] ima_state;
	
	private Bufferer bufferRunner;
	private volatile boolean streamEnd;
	private boolean justLooped;
	
	protected NinSoundStream(NinStreamableSound src)
	{
		source = src;
		buffer = new ConcurrentLinkedQueue<Byte>();
		streamEnd = false;
		int channels = source.totalChannels();
		if(src.getEncodingType() == NinSound.ENCODING_TYPE_DSP_ADPCM)
		{
			dsp_state = new NinADPCM[channels];
			for(int i = 0; i < channels; i++)
			{
				dsp_state[i] = new NinADPCM(src.getADPCMTableForChannel(i), NinSound.DSP_ADPCM_UNIT_SAMPLES);
			}
		}
		else if(src.getEncodingType() == NinSound.ENCODING_TYPE_IMA_ADPCM){
			ima_state = new NinIMAADPCM[channels];
			for(int i = 0; i < channels; i++)
			{
				ima_state[i] = new NinIMAADPCM(src.getIMAStartSample(i), src.getIMAStartIndex(i));
			}
		}
		
		//Iterators
		activeIterators = new LinkedList<Iterator<Integer>>();
		for(int i = 0; i < channels; i++)
		{
			activeIterators.add(src.getRawChannelIterator(i));
		}
		open();
		justLooped = false;
	}
	
	/* ----- Buffer Thread ----- */
	
	private class Bufferer extends Arunnable
	{

		public Bufferer()
		{
			super.setRandomName();
			super.delay = 10;
			super.sleeps = true;
			super.sleeptime = 10;
		}
		
		@Override
		public void doSomething() 
		{
			//Grab samples and split them into bytes until buffer is approximately full
			while(buffer.size() < BUFFER_SIZE)
			{
				//Check for refresh
				if(iteratorsFinished())
				{
					if(source.loops()) loopIterators();
					else
					{
						streamEnd = true;
						return;
					}
				}
				//Buffer the next set of samples
				int i = 0;
				for(Iterator<Integer> it : activeIterators)
				{
					int s = it.next();
					switch(source.getEncodingType())
					{
					case NinSound.ENCODING_TYPE_PCM8:
						//Make it signed, then queue
						s -= 128;
						buffer.add((byte)s);
						break;
					case NinSound.ENCODING_TYPE_PCM16:
						//Add the lowest two bytes
						buffer.add((byte)((s >>> 8) & 0xF));
						buffer.add((byte)(s & 0xF));
						break;
					case NinSound.ENCODING_TYPE_DSP_ADPCM:
						//Decompress, then add the lowest two bytes
						NinADPCM decomp = dsp_state[i];
						if(justLooped) decomp.setToLoop(source.getLoopFrame());
						if(decomp.newBlock())
						{
							int n1 = s;
							int n2 = it.next();
							s = it.next();
							decomp.setPS((n1 << 4) | n2);
						}
						int us = s;
						us = decomp.decompressNextNybble(s);
						buffer.add((byte)((us >>> 8) & 0xF));
						buffer.add((byte)(us & 0xF));
						break;
					case NinSound.ENCODING_TYPE_IMA_ADPCM:
						NinIMAADPCM imastate = ima_state[i];
						if(justLooped) imastate.resetToLoop();
						us = imastate.decompressNybble(s, false);
						buffer.add((byte)((us >>> 8) & 0xF));
						buffer.add((byte)(us & 0xF));
						break;
					}
					i++;
					justLooped = false;
				}
				
			}
		}
		
	}
	
	private boolean iteratorsFinished()
	{
		Iterator<Integer> it = activeIterators.get(0);
		return !it.hasNext();
	}
	
	private void loopIterators()
	{
		//Just refresh and fastforward them to loop point
		activeIterators.clear();
		int channels = source.totalChannels();
		for(int i = 0; i < channels; i++)
		{
			Iterator<Integer> it = source.getRawChannelIterator(i);
			for(int j = 0; j < source.getLoopFrame(); j++) it.next();
			activeIterators.add(it);
		}
		justLooped = true;
	}
	
	/* ----- InputStream Methods ----- */
	
	private void open()
	{
		close();
		bufferRunner = new Bufferer();
		Thread bufferThread = new Thread(bufferRunner);
		bufferThread.setName("WiiSoundStream_BufferThread");
		bufferThread.setDaemon(true);
		bufferThread.start();
	}
	
	public boolean isOpen()
	{
		return bufferRunner != null;
	}
	
	public boolean endReached()
	{
		return streamEnd;
	}
	
 	public int available()
	{
		if (!isOpen()) return 0;
		//if (endReached()) return 0;
		return buffer.size();
	}
	
	public void close()
	{
		if (!isOpen()) return;
		bufferRunner.requestTermination();
		bufferRunner = null;
	}
	
	public boolean markSupported()
	{
		return false;
	}
		
	@Override
	public int read() throws IOException 
	{
		if(streamEnd) return -1;
		//Block if buffer isn't ready
		while(buffer.isEmpty())
		{
			try 
			{
				Thread.sleep(5);
			} 
			catch (InterruptedException e) 
			{
				e.printStackTrace();
				throw new IOException();
			}
		}
		return Byte.toUnsignedInt(buffer.poll());
	}

	public void reset()
	{
		close();
		buffer.clear();
		justLooped = false;
		streamEnd = false;
		int channels = source.totalChannels();
		if(source.getEncodingType() == NinSound.ENCODING_TYPE_DSP_ADPCM)
		{
			dsp_state = new NinADPCM[channels];
			for(int i = 0; i < channels; i++)
			{
				dsp_state[i] = new NinADPCM(source.getADPCMTableForChannel(i), NinSound.DSP_ADPCM_UNIT_SAMPLES);
			}
		}
		else if(source.getEncodingType() == NinSound.ENCODING_TYPE_IMA_ADPCM){
			ima_state = new NinIMAADPCM[channels];
			for(int i = 0; i < channels; i++)
			{
				ima_state[i].resetToStart();
			}
		}
		activeIterators.clear();
		for(int i = 0; i < channels; i++) activeIterators.add(source.getRawChannelIterator(i));
		open();
	}
	

}
