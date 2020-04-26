package waffleoRai_Sound.nintendo;

import waffleoRai_Containers.nintendo.NDKRevolutionFile;
import waffleoRai_Containers.nintendo.NDKSectionType;
import waffleoRai_Sound.SampleChannel;
import waffleoRai_Sound.SampleChannel16;
import waffleoRai_Sound.SampleChannel4;
import waffleoRai_Sound.SampleChannel8;
import waffleoRai_Sound.Sound;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;

public class RevWave extends NinWave{
	
	/*--- Constants ---*/
	
	public static final String MAGIC = "RWAV";
	public static final String MAGIC_INFO = "INFO";
	public static final String MAGIC_DATA = "DATA";
	
	public static final int ADPCM_BLOCK_SIZE = 8;
	
	/*--- Instance Variables ---*/
	
	private int dataLocType;
	private int dataLocation;
	
	/*--- Construction/Parsing ---*/
	
	public RevWave()
	{
		super.setDefaults();
	}

	private RevWave(NinWave src, int channel)
	{
		NinWave.copyChannel(src, this, channel);
	}
	
	public static RevWave readRWAV(FileBuffer src) throws UnsupportedFileTypeException
	{
		//TODO
		//1. Not sure what to do with dataloc fields
		//2. In RSTM files, channel data is actually interleaved.
		//		But, it's interleaved by ADPCM block.
		//		There is no block size specified by RWAV.
		//		Is it constant? Is there no interleaving?
		
		//Let's get that header crap out of the way...
		if(src == null) return null;
		NDKRevolutionFile rev_file = NDKRevolutionFile.readRevolutionFile(src);
		if(rev_file == null) return null;
		if(!MAGIC.equals(rev_file.getFileIdentifier())) throw new FileBuffer.UnsupportedFileTypeException("RevWave.readRWAV || Source data does not begin with RWAV magic number!");
		
		//Initialize wav
		RevWave wav = new RevWave();
		
		//Read INFO
		FileBuffer info_sec = rev_file.getSectionData(NDKSectionType.INFO);
		long cpos = 0;
		//Skip section header
		cpos += 8;
		wav.encodingType = Byte.toUnsignedInt(info_sec.getByte(cpos)); cpos++;
		if(info_sec.getByte(cpos) != 0) wav.loops = true;
		else wav.loops = false;
		cpos++;
		wav.channelCount = Byte.toUnsignedInt(info_sec.getByte(cpos)); cpos++;
		wav.rawSamples = new SampleChannel[wav.channelCount];
		if(wav.encodingType == NinSound.ENCODING_TYPE_DSP_ADPCM) wav.channel_adpcm_info = new ADPCMTable[wav.channelCount];
		//System.err.println("RWAV Channel Count: " + wav.channelCount);
		
		cpos++; //Padding
		wav.sampleRate = Short.toUnsignedInt(info_sec.shortFromFile(cpos)); cpos += 2;
		wav.dataLocType = Byte.toUnsignedInt(info_sec.getByte(cpos)); cpos++;
		cpos++; //Padding
		
		wav.loopStart = info_sec.intFromFile(cpos); cpos += 4;
		int nybble_count = info_sec.intFromFile(cpos); cpos += 4;
		long chinfo_tbl_off = Integer.toUnsignedLong(info_sec.intFromFile(cpos)); cpos+=4;
		wav.dataLocation = info_sec.intFromFile(cpos); cpos += 4;
		//System.err.println("Encoding Type: " + wav.encodingType);
		//System.err.println("Loops? " + wav.loops);
		//System.err.println("Channel Count: " + wav.channelCount);
		//System.err.println("Sample Rate: " + wav.sampleRate);
		//System.err.println("Loop Start: " + wav.loopStart);
		//System.err.println("Nybble Count: " + nybble_count);
		//System.err.println("Channel Info Table Offset: 0x" + Long.toHexString(chinfo_tbl_off));
		//System.err.println("Data Location: 0x" + Integer.toHexString(wav.dataLocation));
		
		//Channel Info & DATA
		chinfo_tbl_off += 8L;
		for(int i = 0; i < wav.channelCount; i++)
		{
			//System.err.println("Channel " + i);
			long chinfo_off = Integer.toUnsignedLong(info_sec.intFromFile(chinfo_tbl_off)); chinfo_tbl_off+=4;
			//System.err.println("Channel Info Offset: 0x" + Long.toHexString(chinfo_off));
			chinfo_off += 8L;
			long chdat_off = Integer.toUnsignedLong(info_sec.intFromFile(chinfo_off)); chinfo_off+=4;
			long adpcm_off = Integer.toUnsignedLong(info_sec.intFromFile(chinfo_off)); chinfo_off+=4;
			//System.err.println("Channel Data Offset: 0x" + Long.toHexString(chdat_off));
			//System.err.println("Channel ADPCM Offset: 0x" + Long.toHexString(adpcm_off));
			
			//Read ADPCM table, if applicable
			if(wav.encodingType == NinSound.ENCODING_TYPE_DSP_ADPCM)
			{
				adpcm_off += 8L;
				wav.channel_adpcm_info[i] = ADPCMTable.readFromFile(info_sec, adpcm_off);
				//wav.channel_adpcm_info[i].printInfo();
			}
			
			//Read data
			FileBuffer data_sec = rev_file.getSectionData(NDKSectionType.DATA);
			chdat_off = 8L;
			long dpos = chdat_off;
			//SampleChannel ch = SampleChannel.createArrayChannel(nybble_count);
			SampleChannel ch = null;
			switch(wav.encodingType)
			{
			case NinSound.ENCODING_TYPE_DSP_ADPCM:
				SampleChannel4 ch4 = new SampleChannel4(nybble_count);
				ch4.setNybbleOrder(true);
				ch = ch4;
				for(int j = 0; j < nybble_count; j+=2)
				{
					int bi = Byte.toUnsignedInt(data_sec.getByte(dpos)); dpos++;
					int s1 = (bi >>> 4) & 0xF;
					int s2 = bi & 0xF;
					ch.addSample(s1);
					if(j+1 < nybble_count) ch.addSample(s2);
				}
				break;
			case NinSound.ENCODING_TYPE_PCM8:
				ch = new SampleChannel8(nybble_count);
				for(int j = 0; j < nybble_count; j++)
				{
					int bi = Byte.toUnsignedInt(data_sec.getByte(dpos)); dpos++;
					ch.addSample(bi & 0xFF);
				}
				break;
			case NinSound.ENCODING_TYPE_PCM16:
				ch = new SampleChannel16(nybble_count);
				for(int j = 0; j < nybble_count; j++)
				{
					int si = Short.toUnsignedInt(data_sec.shortFromFile(dpos)); dpos+=2;
					ch.addSample(si & 0xFFFF);
				}
				break;
			}
			wav.rawSamples[i] = ch;
		}
		
		return wav;
	}
	
	/*--- Getters ---*/

	public int getDataLocationType(){return this.dataLocType;}
	public int getDataLocation(){return this.dataLocation;}
	
	/*--- Sound ---*/
	
	public Sound getSingleChannel(int channel) 
	{
		return new RevWave(this, channel);
	}
	
}
