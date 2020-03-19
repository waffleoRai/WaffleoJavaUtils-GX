package waffleoRai_SoundSynth.soundformats.game;

import waffleoRai_Sound.psx.PSXVAG;
import waffleoRai_Sound.psx.PSXVAG.Chunk;
import waffleoRai_SoundSynth.AudioSampleStream;

public class PSXVAGSampleStream implements AudioSampleStream{

	/*----- Constants -----*/
	
	/*----- InstanceVariables -----*/
	
	private PSXVAG source;
	
	private int next_chunk;
	private int loop_chunk;
	//private int end_chunk;
	//private boolean oneshot_end;
	
	private int spos; //Sample in chunk
	
	private boolean firstpass;
	private int ls1;
	private int ls2;
	
	private int s2;
	private int s1;
	
	private int[] now_samps;
	
	/*----- Construction -----*/
	
	public PSXVAGSampleStream(PSXVAG src)
	{
		source = src;
		spos = 0;
		//end_chunk = src.countChunks();
		if(src.loops()) loop_chunk = src.getLoopPointChunkIndex();
		else loop_chunk = -1;
		Chunk c = source.getChunk(next_chunk++);
		now_samps = c.decompressSamples(s1, s2); //Decomp first chunk
		firstpass = true;
	}
	
	/*----- Getters -----*/

	public float getSampleRate() 
	{
		return 44100;
	}

	@Override
	public int getBitDepth() 
	{
		return 16;
	}

	@Override
	public int getChannelCount() 
	{
		return 1;
	}
	
	/*----- Setters -----*/
	
	/*----- Stream -----*/
	
	private void nextChunk()
	{
		if(next_chunk == -1)
		{
			//One shot end
			now_samps = new int[28];
			spos = 0;
			return;
		}
		
		s2 = now_samps[26];
		s1 = now_samps[27];
		
		Chunk c = source.getChunk(next_chunk++);
		now_samps = c.decompressSamples(s1, s2);
		spos = 0;
		
		//if(next_chunk >= end_chunk)
		if(c.isEnd()) //Prepare to loop or zero fill
		{
			if(!source.loops())
			{
				//oneshot_end = true;
				//System.err.println("I don't loop!");
				next_chunk = -1;
				return;
			}
			//Loop or 0 fill
			if(loop_chunk >= 0)
			{
				if(!firstpass)
				{
					s2 = ls2;
					s1 = ls1;
				}
				else
				{
					ls2 = s2;
					ls1 = s1;
				}
				next_chunk = loop_chunk;
			}
			else
			{
				now_samps = new int[28];
				return;
			}
			firstpass = false;
		}
	}

	@Override
	public int[] nextSample() throws InterruptedException 
	{
		int[] out = new int[1];
		if(spos >= 28) nextChunk();
		
		out[0] = now_samps[spos++];
		
		return out;
	}

	@Override
	public void close() {
		// TODO Auto-generated method stub
		
	}
	
	

}
