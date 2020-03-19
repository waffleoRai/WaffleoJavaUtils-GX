package waffleoRai_Sound.nintendo;

import java.util.ArrayList;

import waffleoRai_Containers.nintendo.NDKRevolutionFile;
import waffleoRai_Containers.nintendo.NDKSectionType;
import waffleoRai_Sound.SampleChannel;
import waffleoRai_Sound.Sound;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;

public class RevStream extends NinStream {
	
	/*--- Constants ---*/
	
	public static final String MAGIC = "RSTM";
	public static final String MAGIC_HEAD = "HEAD";
	public static final String MAGIC_ADPC = "ADPC";
	public static final String MAGIC_DATA = "DATA";

	/*--- Construction/ Parsing ---*/
	
	public RevStream()
	{
		super.setDefaults();
		tracks = new ArrayList<Track>(16);
	}
	
	public RevStream(RevStream src, int channel)
	{
		NinStream.copyChannel(src, this, channel);
	}
	
	public static RevStream readRSTM(FileBuffer src) throws UnsupportedFileTypeException
	{
		if(src == null) return null;
		NDKRevolutionFile rev_file = NDKRevolutionFile.readRevolutionFile(src);
		if(rev_file == null) return null;
		if(!MAGIC.equals(rev_file.getFileIdentifier())) throw new FileBuffer.UnsupportedFileTypeException("RevStream.readRSTM || Source data does not begin with RSTM magic number!");
		
		FileBuffer head_sec = rev_file.getSectionData(NDKSectionType.HEAD);
		//FileBuffer data_sec = rev_file.getSectionData(NDKSectionType.DATA);
		FileBuffer adpc_sec = rev_file.getSectionData(NDKSectionType.ADPC);
		
		//Instantiate
		RevStream wav = new RevStream();
		
		//HEAD
		long cpos = 0xC; //HEAD
		long h1_off = Integer.toUnsignedLong(head_sec.intFromFile(cpos)); cpos += 8;
		long h2_off = Integer.toUnsignedLong(head_sec.intFromFile(cpos)); cpos += 8;
		long h3_off = Integer.toUnsignedLong(head_sec.intFromFile(cpos)); cpos += 8;
		//System.err.println("h1 offset: 0x" + Long.toHexString(h1_off));
		//System.err.println("h2 offset: 0x" + Long.toHexString(h2_off));
		//System.err.println("h3 offset: 0x" + Long.toHexString(h3_off));
		
		h1_off += 8L;
		cpos = h1_off;
		wav.encodingType = Byte.toUnsignedInt(head_sec.getByte(cpos)); cpos++;
		if(head_sec.getByte(cpos) != 0) wav.loops = true;
		else wav.loops = false;
		cpos++;
		//System.err.println("Encoding Type: " + wav.encodingType);
		//System.err.println("Loops?: " + wav.loops);
		
		wav.channelCount = Byte.toUnsignedInt(head_sec.getByte(cpos)); cpos++;
		wav.rawSamples = new SampleChannel[wav.channelCount];
		if(wav.encodingType == NinSound.ENCODING_TYPE_DSP_ADPCM) wav.channel_adpcm_info = new ADPCMTable[wav.channelCount];
		cpos += 1; //Padding?
		//System.err.println("Channel Count: " + wav.channelCount);
		
		wav.sampleRate = Short.toUnsignedInt(head_sec.shortFromFile(cpos)); cpos += 2;
		cpos += 2; //Padding?
		wav.loopStart = head_sec.intFromFile(cpos); cpos += 4;
		int sampCount = head_sec.intFromFile(cpos); cpos += 4;
		long abs_data_off = Integer.toUnsignedLong(head_sec.intFromFile(cpos)); cpos += 4;
		int blockCount = head_sec.intFromFile(cpos); cpos += 4;
		int adpcm_block_size = head_sec.intFromFile(cpos); cpos += 4;
		wav.block_size = adpcm_block_size;
		//int adpcm_samples_per_block = src.intFromFile(cpos); cpos += 4;
		cpos+=4;
		int finalBlockSize = head_sec.intFromFile(cpos); cpos += 4;
		//int finalBlockSamples = src.intFromFile(cpos); cpos += 4;
		cpos += 4;
		int finalBlockSize_withPad = head_sec.intFromFile(cpos); cpos += 4;
		//int adpc_samplesPerEntry = src.intFromFile(cpos); cpos += 4;
		cpos += 4;
		int adpc_bytesPerEntry = head_sec.intFromFile(cpos); cpos += 4;
		//System.err.println("Sample Rate: " + wav.sampleRate);
		//System.err.println("Loop Start: " + wav.loopStart);
		//System.err.println("Sample Count: " + sampCount);
		//System.err.println("Absolute Data Offset: 0x" + Long.toHexString(abs_data_off));
		//System.err.println("Block Count: " + blockCount);
		//System.err.println("Block Size: 0x" + Integer.toHexString(wav.block_size));
		//System.err.println("Final Block Size: 0x" + Integer.toHexString(finalBlockSize));
		
		h2_off += 8L;
		cpos = h2_off;
		int trackCount = Byte.toUnsignedInt(head_sec.getByte(cpos)); cpos++;
		int trackDescType = Byte.toUnsignedInt(head_sec.getByte(cpos)); cpos++;
		cpos += 2; //Padding?
		//System.err.println("Track Count: " + trackCount);
		//System.err.println("Track Desc Type: " + trackDescType);
		
		wav.tracks = new ArrayList<Track>(trackCount+1);
		for(int i = 0; i < trackCount; i++)
		{
			//System.err.println("Track " + i);
			cpos++;
			trackDescType = Byte.toUnsignedInt(head_sec.getByte(cpos)); cpos++;
			cpos += 2;
			long td_off = Integer.toUnsignedLong(head_sec.intFromFile(cpos)); cpos += 4;
			//System.err.println("Track Desc Type: " + trackDescType);
			//System.err.println("Track Desc Offset: 0x" + Long.toHexString(td_off));
			
			td_off += 8L;
			long doff = td_off;
			Track t = new Track();
			if(trackDescType == 1)
			{
				t.volume = (int)head_sec.getByte(doff); doff++;
				t.pan = (int)head_sec.getByte(doff); doff++;
				doff += 6;
				//System.err.println("Track Volume: 0x" + Integer.toHexString(t.volume));
				//System.err.println("Track Pan: 0x" + Long.toHexString(t.pan));
			}
			t.chCount = (int)head_sec.getByte(doff); doff++;
			t.leftChannelID = Byte.toUnsignedInt(head_sec.getByte(doff)); doff++;
			if (t.chCount > 1) t.rightChannelID = Byte.toUnsignedInt(head_sec.getByte(doff)); doff++;
			//System.err.println("Track Channel Count: " + t.chCount);
			//System.err.println("Track Left Channel ID: " + t.leftChannelID);
			//System.err.println("Track Right Channel ID: " + t.rightChannelID);
			
			wav.tracks.add(t);
		}
		
		h3_off += 8L;
		cpos = h3_off;
		int nch = Byte.toUnsignedInt(head_sec.getByte(cpos)); cpos+=4;
		//System.out.println("Channel Info Entries: " + nch);
		for(int i = 0; i < nch; i++)
		{
			//System.out.println("Channel " + i);
			cpos += 4; //Marker
			long chinfo_off = Integer.toUnsignedLong(head_sec.intFromFile(cpos)); cpos += 4;
			//System.out.println("Info Offset 0x" + Long.toHexString(chinfo_off));
			
			chinfo_off += 8L;
			//Coeff seems to start at 0x8?
			ADPCMTable tbl = ADPCMTable.readFromFile(head_sec, chinfo_off + 8L);
			wav.channel_adpcm_info[i] = tbl;
			//System.out.println("ADPCM Table --- ");
			//tbl.printInfo();
		}
		
		//ADPC chunk seems to be for skipping? Let's copy it anyway.
		cpos = 4L;
		long adpc_size = Integer.toUnsignedLong(adpc_sec.intFromFile(cpos)); cpos+=4;
		int entries = (int)((adpc_size/(long)adpc_bytesPerEntry)/(long)wav.channelCount);
		wav.adpc_table = new ADPCTable(wav.channelCount, entries);
		for(int i = 0; i < entries; i++)
		{
			for(int j = 0; j < wav.channelCount; j++)
			{
				wav.adpc_table.old[j][i] = (int)adpc_sec.shortFromFile(cpos); cpos += 2;
				wav.adpc_table.older[j][i] = (int)adpc_sec.shortFromFile(cpos); cpos += 2;
			}
		}
		
		//Data
		cpos = abs_data_off;
		for(int c = 0; c < wav.channelCount; c++) wav.rawSamples[c] = SampleChannel.createArrayChannel(sampCount);
		for(int b = 0; b < blockCount-1; b++)
		{
			for(int c = 0; c < wav.channelCount; c++)
			{
				//Read a block
				for(int i = 0; i < adpcm_block_size; i++)
				{
					int bi = Byte.toUnsignedInt(src.getByte(cpos)); cpos++;
					wav.rawSamples[c].addSample((bi >>> 4) & 0xF);
					wav.rawSamples[c].addSample(bi & 0xF);
				}
			}
		}
		
		//And now let's print the first block of channel 0...
		/*SampleChannel debug = wav.rawSamples[0];
		System.err.println("DEBUG -- First Block of Channel 0:");
		for(int i = 0; i < 0x2000; i++)
		{
			System.err.print(Integer.toHexString(debug.getSample(i)) + " ");
			if(i % 32 == 31) System.err.println();
		}*/
		
		//Final block
		for(int c = 0; c < wav.channelCount; c++)
		{
			//Read a block
			for(int i = 0; i < finalBlockSize_withPad; i++)
			{
				int bi = Byte.toUnsignedInt(src.getByte(cpos)); cpos++;
				if(i < finalBlockSize)
				{
					wav.rawSamples[c].addSample((bi >>> 4) & 0xF);
					wav.rawSamples[c].addSample(bi & 0xF);	
				}
			}
		}
		
		return wav;
	}

	/*--- Sound ---*/
	
	@Override
	public Sound getSingleChannel(int channel) 
	{
		return new RevStream(this, channel);
	}

	
}
