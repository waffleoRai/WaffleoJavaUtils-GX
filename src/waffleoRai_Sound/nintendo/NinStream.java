package waffleoRai_Sound.nintendo;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import waffleoRai_Sound.SampleChannel;
import waffleoRai_Sound.WAV;
import waffleoRai_Sound.WAV.LoopType;
import waffleoRai_SoundSynth.AudioSampleStream;
import waffleoRai_SoundSynth.soundformats.game.NAudioSampleStream;

public abstract class NinStream extends NinStreamableSound{
	
	/*--- Inner Classes ---*/
	
	public static class Track
	{
		public int volume;
		public int pan;
		
		public int chCount;
		public int leftChannelID;
		public int rightChannelID;
	}
	
	public static class ADPCTable
	{
		public int[][] older;
		public int[][] old;
		
		public ADPCTable(int channels, int entries)
		{
			older = new int[channels][entries];
			old = new int[channels][entries];
		}
		
		public ADPCTable copy(int channel)
		{
			int entries = older[0].length;
			ADPCTable copy = new ADPCTable(1, entries);
			for(int i = 0; i < entries; i++)
			{
				copy.old[0][i] = old[channel][i];
				copy.older[0][i] = older[channel][i];
			}
			
			return copy;
		}
	}
	
	public static class IMATable
	{
		public int samples_per_block;
		public int[][] init_samps;
		public int[][] init_idxs;
		
		public IMATable(int channels, int blocks, int samps_per_block){
			samples_per_block = samps_per_block;
			init_samps = new int[channels][blocks];
			init_idxs = new int[channels][blocks];
		}
		
		public int getBlockCount(){
			return init_samps[0].length;
		}
		
	}
	
	/*--- Instance Variables ---*/
	
	protected int block_size;
	protected List<Track> tracks;
	protected ADPCTable adpc_table; //For DSP ADPCM
	protected IMATable ima_table; //For IMA ADPCM
	
	protected int active_track;
	
	/*--- Data Copy ---*/
	
	public static void copyChannel(NinStream src, NinStream trg, int channel)
	{
		trg.encodingType = src.encodingType;
		trg.loops = src.loops;
		trg.channelCount = 1;
		trg.sampleRate = src.sampleRate;
		trg.loopStart = src.loopStart;
		trg.unityNote = src.unityNote;
		trg.fineTune = src.fineTune;
		trg.playEnd = false;
		//adpcm_block_size = src.adpcm_block_size;
		//adpcm_samples_per_block = src.adpcm_samples_per_block;
		
		if(trg.encodingType == NinSound.ENCODING_TYPE_DSP_ADPCM)
		{
			trg.channel_adpcm_info = new ADPCMTable[1];
			trg.channel_adpcm_info[0] = src.channel_adpcm_info[channel];
		}
		trg.rawSamples = new SampleChannel[1];
		//trg.rawSamples[0] = new SampleChannel(src.rawSamples[channel]);
		trg.rawSamples[0] = src.rawSamples[channel].copy();
		
		trg.tracks = new ArrayList<Track>(src.tracks.size() + 1);
		trg.tracks.addAll(src.tracks);
		trg.adpc_table = src.adpc_table.copy(channel);
	}
	
	/*--- Info ---*/
	
	public int getTrackCount()
	{
		return tracks.size();
	}
	
	public boolean writeTrackInformation(BufferedWriter writer)
	{
		if(writer == null) return false;
		int i = 0;
		try{
		for(Track t : tracks)
		{
			writer.write("---- TRACK " + i + "\n");
			writer.write("Volume: 0x" + Integer.toHexString(t.volume) + "\n");
			writer.write("Pan: 0x" + Integer.toHexString(t.pan) + "\n");
			writer.write("Channel Count: " + t.chCount + "\n");
			if(t.chCount < 2)
			{
				writer.write("Channel ID: " + t.leftChannelID + "\n");
			}
			else
			{
				writer.write("Left Channel ID: " + t.leftChannelID + "\n");
				writer.write("Right Channel ID: " + t.rightChannelID + "\n");
			}
			writer.write("\n");
		}
		}
		catch(Exception e)
		{
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	/*--- Convert ---*/
	
	public int getFrameAtOffset(long offset)
	{
		int blockidx = (int)(offset/(long)block_size);
		int blockoff = (int)(offset%(long)block_size);
		
		int badj = blockidx/totalChannels();
		int f_per_block = (block_size * 14) >>> 3;
		
		int frame = (f_per_block) * badj;
		frame += (blockoff * 14) >>> 3;
		
		return frame;
	}
	
	public boolean writeTrackAsWAV(String path, int track)
	{
		Track t = null;
		try{t = tracks.get(track);}
		catch(Exception e){return false;}
		if(t == null) return false;
		
		WAV wav = new WAV(16, t.chCount, totalFrames());
		wav.setSampleRate(sampleRate);
		if(loops) wav.setLoop(LoopType.Forward, loopStart, totalFrames());
		wav.setSMPL_tune(unityNote, fineTune);
		
		//Copy data
		//Instead of looping through all channels, it's just the channels we want
		wav.copyData(0, getSamples_16Signed(t.leftChannelID));
		if(t.chCount > 1) wav.copyData(1, getSamples_16Signed(t.rightChannelID));
		
		try 
		{
			wav.writeFile(path);
		} 
		catch (IOException e) 
		{
			e.printStackTrace();
			return false;
		}
		
		return true;
	}
	
	public boolean writeTrackAsWAV(String path, int track, int stFrame, int frameLen)
	{
		Track t = null;
		try{t = tracks.get(track);}
		catch(Exception e){return false;}
		if(t == null) return false;
		
		int totalFrames = this.totalFrames();
		if(stFrame >= totalFrames) return false;
		if(stFrame + frameLen > totalFrames) frameLen = totalFrames - stFrame;

		WAV wav = new WAV(16, t.chCount, frameLen);
		wav.setSampleRate(sampleRate);
		wav.setSMPL_tune(unityNote, fineTune);
		
		
		if(loops)
		{
			int ls = loopStart - stFrame;
			if(ls < 0) ls = 0;
			wav.setLoop(LoopType.Forward, ls, frameLen);
		}
		
		int[] fullChannel = getSamples_16Signed(t.leftChannelID);
		int[] cPart = new int[frameLen];
		for(int j = 0; j < frameLen; j++) cPart[j] = fullChannel[j+stFrame];
		wav.copyData(0, cPart);
		
		if(t.chCount > 1)
		{
			fullChannel = getSamples_16Signed(t.rightChannelID);
			cPart = new int[frameLen];
			for(int j = 0; j < frameLen; j++) cPart[j] = fullChannel[j+stFrame];
			wav.copyData(1, cPart);
		}
		
		try 
		{
			wav.writeFile(path);
		} 
		catch (IOException e) 
		{
			e.printStackTrace();
			return false;
		}
		
		return true;
	}
	
	public boolean writePartialTrackAsWAV(String path, int track, long datOff, long datEnd)
	{
		//Calculate which frame it would be...
		int sframe = this.getFrameAtOffset(datOff);
		int eframe = this.getFrameAtOffset(datEnd);
		int framediff = eframe - sframe;
		return writeTrackAsWAV(path ,track, sframe, framediff);
	}
	
	public boolean writePartialTrackAsWAV(String path, int track, long datOff)
	{
		int sframe = this.getFrameAtOffset(datOff);
		int eframe = this.totalFrames();
		int framediff = eframe - sframe;
		return writeTrackAsWAV(path ,track, sframe, framediff);
	}
	
	public int[] getActiveTrackChannels(){

		if(active_track == -1) return null;
		if(tracks == null || tracks.isEmpty()) return null;
		Track t = tracks.get(active_track);
		int[] chans = new int[2];
		chans[0] = t.leftChannelID;
		chans[1] = t.rightChannelID;
		
		return chans;
	}
	
	public AudioSampleStream createSampleStream()
	{
		int[] tchannels = getActiveTrackChannels();
		NAudioSampleStream str = null;
		if(tchannels != null) str = new NAudioSampleStream(this, tchannels);
		else str = new NAudioSampleStream(this);
		if(ima_table != null) str.addIMATable(ima_table);
		return str;
	}
	
	public AudioSampleStream createSampleStream(boolean loop)
	{
		int[] tchannels = getActiveTrackChannels();
		NAudioSampleStream str = null;
		if(tchannels != null) str = new NAudioSampleStream(this, tchannels, loop);
		else str = new NAudioSampleStream(this, loop);
		if(ima_table != null) str.addIMATable(ima_table);
		return str;
	}
	
	public void setActiveTrack(int tidx){
		if(tracks == null || tracks.isEmpty()) return;
		if(tidx >= tracks.size()) return;
		if(tidx < 0) return;
		
		active_track = tidx;
	}
	
	public int countTracks(){
		if(tracks == null || tracks.isEmpty()) return 1;
		return tracks.size();
	}
	
}
