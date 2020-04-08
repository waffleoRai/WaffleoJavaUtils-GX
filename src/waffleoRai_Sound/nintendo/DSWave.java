package waffleoRai_Sound.nintendo;

import waffleoRai_Containers.nintendo.NDKDSFile;
import waffleoRai_Sound.SampleChannel;
import waffleoRai_Sound.Sound;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;

public class DSWave extends NinWave{
	
	public static final String MAGIC = "SWAV";
	
	public static DSWave readSWAV(FileBuffer data, long offset) throws UnsupportedFileTypeException{
		if(offset != 0) data = data.createReadOnlyCopy(offset, data.getFileSize());
		
		//Break down into chunks...
		NDKDSFile file = NDKDSFile.readDSFile(data);
		
		//We just need the DATA chunk
		FileBuffer data_sec = file.getSectionData("DATA");
		if(data_sec == null) throw new FileBuffer.UnsupportedFileTypeException("DSWave.readSWAV || DATA section could not be found!");
		
		//Skip magic and size...
		
		return readInternalSWAV(data_sec, 8);
	}
	
	public static DSWave readInternalSWAV(FileBuffer data, long offset){
		//SWAVs in SWARs are UNHEADERED!
		//This method is also called by the standard SWAV reader
		
		//So first is the SWAVINFO struct (named by kiwids)
		/*
		 * Encoding [1]
		 * Loop Flag [1]
		 * Sample Rate [2]
		 * Time [2]
		 * Loop Offset (in 32-bit words) [2]
		 * Length (in 32-bit words) [4]
		 */
		data.setEndian(false);
		DSWave wave = new DSWave();
		
		data.setCurrentPosition(offset);
		byte enc = data.nextByte();
		
		switch(enc){
		case 0: wave.encodingType = NinSound.ENCODING_TYPE_PCM8; break;
		case 1: wave.encodingType = NinSound.ENCODING_TYPE_PCM16; break;
		case 2: wave.encodingType = NinSound.ENCODING_TYPE_IMA_ADPCM; break;
		}
		
		byte loopflag = data.nextByte();
		wave.loops = (loopflag != 0);
		
		wave.sampleRate = Short.toUnsignedInt(data.nextShort());
		data.skipBytes(2); //Skip time
		
		int looppoint = Short.toUnsignedInt(data.nextShort());
		int nonlooplen = data.nextInt();
		int totalwords = looppoint + nonlooplen;
		//System.err.println("Length in words: " + rawlen + "(0x" + Integer.toHexString(rawlen) + ")");
		
		//Read header word if ADPCM
		if(wave.encodingType == NinSound.ENCODING_TYPE_IMA_ADPCM){
			int imaword = data.nextInt();
			wave.IMA_idx_init = new int[1]; wave.IMA_samp_init = new int[1];
			wave.IMA_samp_init[0] = imaword & 0xFFFF;
			wave.IMA_idx_init[0] = (imaword >>> 16) & 0x7F;
			wave.initializeIMAStateTables();
		}
		
		//Read samples & mark loop points
		wave.rawSamples = new SampleChannel[1]; //Always mono
		int fcount = 0;
		switch(wave.encodingType){
		case NinSound.ENCODING_TYPE_PCM8:
			fcount = totalwords << 2; //Mult by 4 to get bytes
			wave.rawSamples[0] = SampleChannel.createArrayChannel(fcount);
			//PCM8 is unsigned
			for(int i = 0; i < fcount; i++) wave.rawSamples[0].addSample(Byte.toUnsignedInt(data.nextByte()));
			wave.loopStart = looppoint << 2;
			break;
		case NinSound.ENCODING_TYPE_PCM16:
			fcount = totalwords << 1; //Mult by 2 to get halfwords
			wave.rawSamples[0] = SampleChannel.createArrayChannel(fcount);
			for(int i = 0; i < fcount; i++) wave.rawSamples[0].addSample((int)data.nextShort());
			wave.loopStart = looppoint << 1;
			break;
		case NinSound.ENCODING_TYPE_IMA_ADPCM: 
			fcount = (totalwords-1) << 3; //Mult by 8 to get nybbles
			//System.err.println("\tFrame count: " + fcount + "(0x" + Integer.toHexString(fcount) + ")");
			//System.err.println("Current Position: " + "0x" + Long.toHexString(data.getCurrentPosition()) + ")");
			//LOWER nybble first!
			wave.rawSamples[0] = SampleChannel.createArrayChannel(fcount);
			for(int i = 0; i < fcount; i+=2){
				int b = Byte.toUnsignedInt(data.nextByte());
				wave.rawSamples[0].addSample(b & 0xF);
				wave.rawSamples[0].addSample((b >>> 4) & 0xF);
			}
			wave.loopStart = (looppoint << 3) - 8; //NOTE: assuming that the IMA init word is EXCLUDED
			//System.err.println("\tLoop: " + String.format("%02x", loopflag) + " (" + wave.loops + ") -- " + looppoint + " > " + wave.loopStart);
			break;
		}
		
		//Set other housekeeping things
		wave.channelCount = 1;
		
		return wave;
	}
	
	public Sound getSingleChannel(int channel) {
		if(channel != 0) throw new IndexOutOfBoundsException();
		return this;
	}
	
	public void setActiveTrack(int tidx){}
	public int countTracks(){return 1;}

}
