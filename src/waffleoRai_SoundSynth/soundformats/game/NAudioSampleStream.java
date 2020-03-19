package waffleoRai_SoundSynth.soundformats.game;

import waffleoRai_Sound.nintendo.NinADPCM;
import waffleoRai_Sound.nintendo.NinIMAADPCM;
import waffleoRai_Sound.nintendo.NinSound;
import waffleoRai_Sound.nintendo.NinStream;
import waffleoRai_Sound.nintendo.NinStreamableSound;
import waffleoRai_SoundSynth.AudioSampleStream;

public class NAudioSampleStream implements AudioSampleStream{
	
	/*----- Constants -----*/
	
	/*----- InstanceVariables -----*/
	
	private int encodingType;
	private NinStreamableSound source;
	
	private int pos;
	
	private int loopStart;
	private int loopEnd;
	
	private int bitDepth;
	
	//Only need one or the other of these
	private NinADPCM[] dsp_states; //Per channel
	private NinIMAADPCM[] ima_states;
	private NinStream.IMATable ima_block_tbl;
	
	private SampleGetterCallback callback;
	
	/*----- Construction -----*/
	
	public NAudioSampleStream(NinStreamableSound src)
	{
		source = src;
		encodingType = source.getEncodingType();
		int ccount = src.totalChannels();
		if(encodingType == NinSound.ENCODING_TYPE_DSP_ADPCM)
		{
			dsp_states = new NinADPCM[ccount];
			for(int i = 0; i < ccount; i++)
			{
				dsp_states[i] = source.generateNewADPCMState(i);
			}	
			bitDepth = 16;
			callback = new DSPCallback();
		}
		else if(encodingType == NinSound.ENCODING_TYPE_IMA_ADPCM){
			ima_states = new NinIMAADPCM[ccount];
			for(int i = 0; i < ccount; i++) ima_states[i] = new NinIMAADPCM(source.getIMAStartSample(i), source.getIMAStartIndex(i));
			bitDepth = 16;
			callback = new IMACallback();
		}
		else if(encodingType == NinSound.ENCODING_TYPE_PCM8){
			bitDepth = 16;
			callback = new PCM8Callback();
		}
		else if(encodingType == NinSound.ENCODING_TYPE_PCM16){
			bitDepth = 16;
			callback = new PCM16Callback();
		}
		else bitDepth = 16; //May change if 24 bit encodings come around in WiiU+
		
		
		if(source.loops()){
			loopStart = source.getLoopFrame();
			loopEnd = source.getLoopEndFrame();
		}
		else{
			loopStart = -1;
			loopEnd = source.totalFrames();
		}
		
		
	}
	
	public void addIMATable(NinStream.IMATable table){
		ima_block_tbl = table;
	}
	
	/*----- Inner Classes -----*/
	
	protected interface SampleGetterCallback{
		public int[] nextSample(int[] target);
	}
	
	protected class PCM8Callback implements SampleGetterCallback{

		public int[] nextSample(int[] target) {
			//Scales to signed 16 bit (from unsigned 8)
			int ccount = target.length;
			for(int i = 0; i < ccount; i++){
				int raw = source.getRawDataPoint(i, pos);
				raw -= 0x80;
				double rat = (double)raw/127.0;
				target[i] = (int)Math.round(rat * (double)0x7FFF);
			}
			pos++;
			return target;
		}
		
	}
	
	protected class PCM16Callback implements SampleGetterCallback{

		@Override
		public int[] nextSample(int[] target) {
			//Return as is!
			int ccount = target.length;
			for(int i = 0; i < ccount; i++){
				target[i] = source.getRawDataPoint(i, pos);
			}
			pos++;
			return target;
		}
		
	}
	
	protected class DSPCallback implements SampleGetterCallback{

		@Override
		public int[] nextSample(int[] target) {
			int ccount = target.length;
			int addpos = 1;
			for(int c = 0; c < ccount; c++)
			{
				NinADPCM state = dsp_states[c];
				if(state.newBlock())
				{
					addpos = 3;
					int n0 = source.getRawDataPoint(c, pos);
					int n1 = source.getRawDataPoint(c, pos+1);
					int samp = source.getRawDataPoint(c, pos+2);
					state.setPS((n0 << 4) | n1);
					target[c] = state.decompressNextNybble(samp);
				}
				else
				{
					target[c] = state.decompressNextNybble(source.getRawDataPoint(c, pos));
				}
			}
			pos += addpos;
			
			return target;
		}
	}

	protected class IMACallback implements SampleGetterCallback{

			@Override
			public int[] nextSample(int[] target) {
				
				int ccount = target.length;
				
				if(ima_block_tbl != null && ((pos % ima_block_tbl.samples_per_block) == 0)){
					for(int c = 0; c < ccount; c++){
						int block = pos/ima_block_tbl.samples_per_block;
						//System.err.println("Pos 0x" + Integer.toHexString(pos) + " starts block " + block);
						int idx = ima_block_tbl.init_idxs[c][block];
						//int samp = ima_block_tbl.init_samps[c][block];
						//ima_states[c].setState(samp, idx);
						ima_states[c].setState(idx);
					}
				}
				
				for(int c = 0; c < ccount; c++){
					NinIMAADPCM state = ima_states[c];
					int s = source.getRawDataPoint(c, pos);
					target[c] = state.decompressNybble(s, (pos == loopStart));
				}
				
				pos++;
				return target;
			}
			
		}
	
	/*----- Getters -----*/
	
	@Override
	public float getSampleRate() 
	{
		return source.getSampleRate();
	}

	@Override
	public int getBitDepth() {
		return bitDepth;
	}

	@Override
	public int getChannelCount() {
		return source.totalChannels();
	}
	
	/*----- Setters -----*/
	
	/*----- Stream -----*/
	
	private void loopMe()
	{
		pos = loopStart;
		if(dsp_states != null)
		{
			for(NinADPCM state : dsp_states)
			{
				state.setToLoop((loopStart % NinSound.DSP_ADPCM_UNIT_SIZE_NYBBLES) - 2);
			}
		}
		else if(ima_states != null){
			for(NinIMAADPCM state : ima_states) state.resetToLoop();
		}
	}
	
	@Override
	public int[] nextSample() throws InterruptedException {
		
		int ccount = source.totalChannels();
		if(pos >= loopEnd)
		{
			if(loopStart >= 0) loopMe();
			else return new int[ccount];
		}
		
		int[] out = new int[ccount];
		out = callback.nextSample(out);
		
		return out;
	}

	@Override
	public void close() {
		// TODO Auto-generated method stub
		
	}

}
