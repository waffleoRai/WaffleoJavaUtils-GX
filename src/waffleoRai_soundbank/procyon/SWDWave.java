package waffleoRai_soundbank.procyon;

import waffleoRai_Sound.SampleChannel;
import waffleoRai_Sound.SampleChannel16;
import waffleoRai_Sound.SampleChannel4;
import waffleoRai_Sound.SampleChannel8;
import waffleoRai_Sound.Sound;
import waffleoRai_Sound.nintendo.NinSound;
import waffleoRai_Sound.nintendo.NinWave;
import waffleoRai_Utils.FileBuffer;

public class SWDWave extends NinWave{

	private SWDWave(){}
	
	public static SWDWave readSWDWave(FileBuffer data, long offset, SWDWavi info){
		
		if(info == null) return null;
		//Determine wav size in bytes
		long looplen = info.getLoopLength();
		long loopst = info.getLoopStart();
		long wavsz = loopst + looplen;
		
		//Initialize
		SWDWave wav = new SWDWave();
		
		//Copy data from info
		wav.encodingType = info.getEncoding();
		wav.sampleRate = info.getSampleRate();
		wav.channelCount = 1;
		wav.loops = info.getLoopFlag();
		wav.unityNote = info.getUnityKey();
		wav.fineTune = info.getFineTune() + (info.getCoarseTune() * 100);
		
		//Read samples & Mark loops
		long cpos = offset;
		int framecount = 0;
		wav.rawSamples = new SampleChannel[1];
		switch(wav.encodingType){
		case NinSound.ENCODING_TYPE_PCM8:
			framecount = (int)wavsz;
			wav.rawSamples[0] = new SampleChannel8(framecount);
			for(int f = 0; f < framecount; f++){
				wav.rawSamples[0].addSample(Byte.toUnsignedInt(data.getByte(cpos++)));
			}
			wav.loopStart = (int)loopst;
			break;
		case NinSound.ENCODING_TYPE_PCM16:
			framecount = (int)(wavsz >>> 1);
			wav.rawSamples[0] = new SampleChannel16(framecount);
			for(int f = 0; f < framecount; f++){
				wav.rawSamples[0].addSample(data.shortFromFile(cpos));
				cpos += 2;
			}
			wav.loopStart = (int)(loopst >>> 1);
			break;
		case NinSound.ENCODING_TYPE_IMA_ADPCM:
			//We want to deduct the IMA word from the samples!
			framecount = ((int)wavsz - 4) << 1;
			SampleChannel4 ch4 = new SampleChannel4(framecount);
			ch4.setNybbleOrder(false);
			//Read IMA word
			int imaword = data.intFromFile(cpos); cpos += 4;
			//System.err.println("");
			wav.IMA_idx_init = new int[1]; wav.IMA_samp_init = new int[1];
			wav.IMA_samp_init[0] = imaword & 0xFFFF;
			wav.IMA_idx_init[0] = (imaword >>> 16) & 0x7F;
			wav.initializeIMAStateTables();
			//Read samples
			for(int f = 0; f < framecount; f+=2){
				ch4.addByte(data.getByte(cpos++));
			}
			//Save
			wav.rawSamples[0] = ch4;
			wav.loopStart = (((int)loopst - 4) << 1);
			break;
		}
		
		return wav;
	}
	
	public Sound getSingleChannel(int channel) {
		//Eh, we only have one channel anyway.
		return this;
	}

}
