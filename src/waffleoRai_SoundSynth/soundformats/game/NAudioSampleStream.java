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
	private int[] src_channels; //Which channels to pull samples from
	
	private int pos;
	
	private int loopStart;
	private int loopEnd;
	//private boolean doneFlag = false;
	
	private int bitDepth;
	
	//Only need one or the other of these
	private NinADPCM[] dsp_states; //Per channel
	private NinIMAADPCM[] ima_states;
	private NinStream.IMATable ima_block_tbl;
	
	private SampleGetterCallback callback;
	
	/*----- Construction -----*/
	
	public NAudioSampleStream(NinStreamableSound src){
		//System.err.print("NAudioSample: Constructing");
		int ch = src.totalChannels();
		int[] use = new int[ch];
		for(int i = 0; i < ch; i++) use[i] = i;
		constructorCore(src, use, true);
	}
	
	public NAudioSampleStream(NinStreamableSound src, int[] usechannels){
		constructorCore(src, usechannels, true);
	}
	
	public NAudioSampleStream(NinStreamableSound src, boolean loopable){
		int ch = src.totalChannels();
		int[] use = new int[ch];
		for(int i = 0; i < ch; i++) use[i] = i;
		constructorCore(src, use, loopable);
	}
	
	public NAudioSampleStream(NinStreamableSound src, int[] usechannels, boolean loopable){
		constructorCore(src, usechannels, loopable);
	}
	
	private void constructorCore(NinStreamableSound src, int[] usechannels, boolean loopable){

		source = src;
		src_channels = usechannels;
		encodingType = source.getEncodingType();
		int ccount = src.totalChannels();
		if(encodingType == NinSound.ENCODING_TYPE_DSP_ADPCM){
			dsp_states = new NinADPCM[ccount];
			for(int i = 0; i < ccount; i++){
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
		
		
		if(source.loops() && loopable){
			loopStart = source.getLoopFrame();
			loopEnd = source.getLoopEndFrame();
		}
		else{
			loopStart = -1;
			loopEnd = source.totalFrames();
		}
		//System.err.println("loopEnd = " + loopEnd);
		
		if(encodingType == NinSound.ENCODING_TYPE_DSP_ADPCM){
			//Adjust loop points
			//Stream uses pos INCLUDING P/S, but frame coords are audio frames only.
			int pkg = 0; int pkg_pos = 0;
			if(loopStart > 0){
				pkg = loopStart / 14;
				pkg_pos = loopStart % 14;
				loopStart  = (pkg << 4) + pkg_pos;
			}
			if(loopEnd > 0){
				pkg = loopEnd / 14;
				pkg_pos = loopEnd % 14;
				loopEnd  = (pkg << 4) + pkg_pos;
			}
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
				int raw = source.getRawDataPoint(src_channels[i], pos);
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
				target[i] = source.getRawDataPoint(src_channels[i], pos);
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
			for(int i = 0; i < ccount; i++)
			{
				int c = src_channels[i];
				NinADPCM state = dsp_states[c];
				if(state.newBlock()){
					addpos = 3;
					int n0 = source.getRawDataPoint(c, pos);
					int n1 = source.getRawDataPoint(c, pos+1);
					int samp = source.getRawDataPoint(c, pos+2);
					//state.setPS((n0 << 4) | n1);
					state.setPredictor(n0);
					state.setShift(n1);
					//System.err.println("DEBUG || Pred = " + n0 + ", Shamt = " + n1 + ", pos = " + pos);
					target[i] = state.decompressNextNybble(samp);
				}
				else{
					target[i] = state.decompressNextNybble(source.getRawDataPoint(c, pos));
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
						int idx = ima_block_tbl.init_idxs[src_channels[c]][block];
						//int samp = ima_block_tbl.init_samps[c][block];
						//ima_states[c].setState(samp, idx);
						ima_states[c].setState(idx);
					}
				}
				
				for(int c = 0; c < ccount; c++){
					NinIMAADPCM state = ima_states[src_channels[c]];
					int s = source.getRawDataPoint(src_channels[c], pos);
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
		
		//int ccount = source.totalChannels();
		int ccount = src_channels.length;
		if(pos >= loopEnd){
			if(loopStart >= 0) loopMe();
			else{
				return new int[ccount];
			}
		}
		
		int[] out = new int[ccount];
		out = callback.nextSample(out);
		
		//System.err.print("NAudioSample: Returning");
		return out;
	}

	@Override
	public void close() {
		// TODO Auto-generated method stub
		
	}

	public boolean done(){
		if(loopStart >= 0) return false;
		return pos >= loopEnd;
	}
	
}
