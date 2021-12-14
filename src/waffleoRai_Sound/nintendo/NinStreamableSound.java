package waffleoRai_Sound.nintendo;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;

import waffleoRai_Sound.ADPCMTable;
import waffleoRai_Sound.BitDepth;
import waffleoRai_Sound.PCM16Sound;
import waffleoRai_Sound.PCM24Sound;
import waffleoRai_Sound.PCMSound;
import waffleoRai_Sound.SampleChannel;
import waffleoRai_Sound.Sound;
import waffleoRai_Sound.WAV;
import waffleoRai_Sound.WAV.LoopType;
import waffleoRai_SoundSynth.AudioSampleStream;
import waffleoRai_SoundSynth.soundformats.game.NAudioSampleStream;

public abstract class NinStreamableSound implements Sound{
	
	/*--- Instance Variables ---*/
	
	protected int encodingType;
	protected boolean loops;
	protected int channelCount;
	protected int sampleRate;

	protected int loopStart;
	//private int sampleCount; //Counts all data, not just samples

	//protected int adpcm_block_size;
	//protected int adpcm_samples_per_block;
	protected ADPCMTable[] channel_adpcm_info; //For DSP ADPCM
	
	//For IMA ADPCM
	protected int[] IMA_samp_init;
	protected int[] IMA_idx_init;
	
	protected SampleChannel[] rawSamples;
	
	//Some optional fields for tweaking, usually contained in other files
	protected int unityNote;
	protected int fineTune;
	
	//Sound interface playback crap
	protected List<Iterator<Integer>> activeIterators;
	protected NinADPCM[] decomps; //DSP ADPCM only
	protected NinIMAADPCM[] ima_state; //IMA ADPCM only
	protected boolean playEnd;
	
	/*--- Construction/Parsing ---*/
	
	protected void setDefaults(){
		encodingType = NinSound.ENCODING_TYPE_PCM16;
		loops = false;
		channelCount = 1;
		sampleRate = 44100;
		loopStart = 0;
		//sampleCount = 0;
		rawSamples = new SampleChannel[1];
		playEnd = false;
		unityNote = 60;
		fineTune = 0;
		//adpcm_block_size = WiiBRWAV.ADPCM_BLOCK_SIZE;
		//adpcm_samples_per_block = adpcm_block_size-2;
	}

	/*--- Getters ---*/
	
	public BitDepth getBitDepth(){
		if(encodingType == NinSound.ENCODING_TYPE_PCM8) return BitDepth.EIGHT_BIT_UNSIGNED;
		else return BitDepth.SIXTEEN_BIT_SIGNED;
	}
	
	public ADPCMTable getADPCMTableForChannel(int channel){
		if(encodingType != NinSound.ENCODING_TYPE_DSP_ADPCM) return null;
		if(channel < 0 || channel >= channel_adpcm_info.length) return null;
		return channel_adpcm_info[channel];
	}
	
	public int getEncodingType(){return this.encodingType;}
	public int getSampleRate(){return this.sampleRate;}
	public boolean loops(){return loops;}
	public int getLoopFrame(){return this.loopStart;}
	public int getLoopEndFrame(){return totalFrames();}
	//public int getADPCM_SamplesPerBlock(){return this.adpcm_samples_per_block;}
	
	public int getIMAStartSample(int channel){
		if(IMA_samp_init != null) return IMA_samp_init[channel]; return 0;}
	
	public int getIMAStartIndex(int channel){
		if(IMA_idx_init != null) return IMA_idx_init[channel]; return 0;}
	
	/*--- Setters ---*/
	
	public void setUnityNote(int n){unityNote = n;}
	public void setFineTune(int n){fineTune = n;}

	protected void initializeIMAStateTables(){
		if(this.IMA_samp_init != null){
			ima_state = new NinIMAADPCM[channelCount];
			for(int c = 0; c < channelCount; c++){
				ima_state[c] = new NinIMAADPCM(IMA_samp_init[c], IMA_idx_init[c]);
			}
		}
	}
	
	/*--- Sound ---*/
	
	private int estimateFrames(){
		if(totalChannels() < 1) return 0;
		if(encodingType == NinSound.ENCODING_TYPE_DSP_ADPCM)
		{
			if(rawSamples[0] == null) return 0;
			int nybbles = rawSamples[0].countSamples();
			//int nybbles_per_block = adpcm_block_size << 1;
			int fullblocks = nybbles/NinSound.DSP_ADPCM_UNIT_SIZE_NYBBLES;
			int samps = fullblocks * NinSound.DSP_ADPCM_UNIT_SAMPLES;

			if(nybbles % NinSound.DSP_ADPCM_UNIT_SIZE_NYBBLES != 0) 
			{
				samps += (nybbles % NinSound.DSP_ADPCM_UNIT_SIZE_NYBBLES)
						- (NinSound.DSP_ADPCM_UNIT_SIZE_NYBBLES - NinSound.DSP_ADPCM_UNIT_SAMPLES);
			}

			return samps;
		}
		else return rawSamples[0].countSamples();
	}
	
	public Iterator<Integer> getRawChannelIterator(int channel){
		if(channel < 0 || channel >= rawSamples.length) return null;
		SampleChannel ch = rawSamples[channel];
		if (ch == null) return null;
		return ch.iterator();
	}
	
	public AudioFormat getFormat(){
		int bitdepth = 16;
		if(this.encodingType == NinSound.ENCODING_TYPE_PCM8) bitdepth = 8;
		AudioFormat rwavformat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, (float)sampleRate, 
				bitdepth, 
				channelCount, (bitdepth/8) * channelCount,
				(float)sampleRate, true);
		return rwavformat;
	}
	
	public AudioInputStream getStream(){
		NinSoundStream rawis = new NinSoundStream(this);
		AudioInputStream ais = new AudioInputStream(rawis, getFormat(), totalFrames());
		return ais;
	}
	
	public int totalFrames(){return estimateFrames();}
	public int totalChannels(){return this.channelCount;}
	
	public int[] getRawSamples(int channel){
		return rawSamples[channel].toArray();
	}
	
	public int[] getSamples_16Signed(int channel){
		//Just use the AudioSampleStream since it works...
		AudioSampleStream me = createSampleStream();
		int fcount = estimateFrames();
		int[] samps = new int[fcount];
		
		for(int f = 0; f < fcount; f++){
			int[] s = null;
			try {s = me.nextSample();} 
			catch (InterruptedException e) {
				//Shouldn't happen, doesn't block.
				e.printStackTrace();
			}
			
			if(s != null) samps[f] = s[channel];
			
		}
		
		return samps;
	}
	
	public int[] getSamples_24Signed(int channel){
		int[] s16 = this.getSamples_16Signed(channel);
		if(s16 == null) return null;
		
		int[] s24 = new int[s16.length];
		for(int i = 0; i < s16.length; i++)
		{
			s24[i] = Sound.scaleSampleUp8Bits(s16[i], BitDepth.SIXTEEN_BIT_SIGNED);
		}
		
		return s24;
	}
	
	public int getUnityNote(){return this.unityNote;}
	public int getFineTune(){return this.fineTune;}
	
	public void flushBuffer(){} //Does nothing. Probably needs buffer, but I'm too lazy
	
	public int getRawDataPoint(int channel, int index){
		return rawSamples[channel].getSample(index);
	}
	
	public NinADPCM generateNewADPCMState(int channel){
		return new NinADPCM(channel_adpcm_info[channel], NinSound.DSP_ADPCM_UNIT_SAMPLES);
	}
	
	public AudioSampleStream createSampleStream(){
		return new NAudioSampleStream(this);
	}
	
	public AudioSampleStream createSampleStream(boolean loop){
		return new NAudioSampleStream(this, loop);
	}
	
	/*--- Conversion ---*/
	
	public PCMSound getAsPCM(){
		int frames = totalFrames();
		AudioSampleStream str = createSampleStream(false);
		
		PCMSound snd = null;
		if(str.getBitDepth() == 16){snd = PCM16Sound.createSound(str, frames);}
		else if(str.getBitDepth() == 24) snd = PCM24Sound.createSound(str, frames);
		
		if(loops) snd.setLoopPoints(loopStart, totalFrames());
		
		return snd;
	}
	
	public PCMSound getAsNormalizedPCM(){
		PCMSound snd = getAsPCM();
		if(snd == null) return null;
		snd.normalizeAmplitude();
		return snd;
	}
	
	public boolean writeWAV(String path){
		if(channelCount < 1) return false;
		int frames = totalFrames();
		if(frames < 1) return false;
		WAV wav = new WAV(16, channelCount, frames);
		wav.setSampleRate(sampleRate);
		if(loops) wav.setLoop(LoopType.Forward, loopStart, totalFrames());
		wav.setSMPL_tune(unityNote, fineTune);
		
		//Copy data
		for(int i = 0; i < channelCount; i++) wav.copyData(i, getSamples_16Signed(i));
		
		try {
			wav.writeFile(path);
		} 
		catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		
		return true;
	}
	
	public boolean writeWAV(String path, int stFrame, int frameLen){
		if(channelCount < 1) return false;
		int totalFrames = this.totalFrames();
		if(totalFrames < 1) return false;
		if(stFrame >= totalFrames) return false;
		if(stFrame + frameLen > totalFrames) frameLen = totalFrames - stFrame;

		WAV wav = new WAV(16, channelCount, frameLen);
		wav.setSampleRate(sampleRate);
		wav.setSMPL_tune(unityNote, fineTune);
		
		
		if(loops){
			int ls = loopStart - stFrame;
			if(ls < 0) ls = 0;
			wav.setLoop(LoopType.Forward, ls, frameLen);
		}
		
		for(int i = 0; i < channelCount; i++){
			int[] fullChannel = this.getSamples_16Signed(i);
			int[] cPart = new int[frameLen];
			for(int j = 0; j < frameLen; j++) cPart[j] = fullChannel[j+stFrame];
			wav.copyData(i, cPart);
		}
		
		try {
			wav.writeFile(path);
		} 
		catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		
		return true;
	}

	public void setActiveTrack(int tidx){}
	public int countTracks(){return 1;}
	
}
