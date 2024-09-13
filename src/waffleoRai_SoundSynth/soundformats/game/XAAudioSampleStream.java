package waffleoRai_SoundSynth.soundformats.game;

import java.io.IOException;

import waffleoRai_Files.psx.IXAAudioDataSource;
import waffleoRai_Files.psx.XADataStream;
import waffleoRai_Files.psx.XAStreamFile;
import waffleoRai_Files.tree.FileNode;
import waffleoRai_Sound.JavaSoundPlayer;
import waffleoRai_Sound.JavaSoundPlayer_16LE;
import waffleoRai_Sound.psx.PSXVAG;
import waffleoRai_Sound.psx.PSXXAAudio;
import waffleoRai_Sound.psx.PSXXAStream;
import waffleoRai_SoundSynth.AudioSampleStream;
import waffleoRai_Utils.FileBuffer;

public class XAAudioSampleStream implements AudioSampleStream{
	
	/*----- Constants -----*/
	
	public static final int SAMPLES_4BIT_ADPCM = 1;
	public static final int SAMPLES_8BIT_ADPCM = 2;
	public static final int SAMPLES_16BIT_PCM = 3;
	
	public static final int CHUNK_SIZE = 128;
	public static final int SEC_SIZE = PSXXAStream.SEC_SIZE;
	public static final int SEC_END = 2328; //End of audio data (there's another empty 20 bytes, then 4 byte footer)
	
	/*----- Instance Variables -----*/
	
	private int ch_count; //Mono/Stereo - not track channels
	private float sample_rate;
	private int bit_mode; //Input bit depth
	
	private int[] s_2;
	private int[] s_1; //For storing last two samples (for each channel)
	
	private IXAAudioDataSource src;
	private boolean dataOnly;
	private boolean done;
	
	private int s_pos; //Position within sector
	private int sec_end = SEC_END;
	private FileBuffer sector; //current sector
	
	private int samp_pos; //Next sample index in sample buffer
	private int[][] samp_buff; //Last decomped samples
	
	private boolean one_channel; //Filter to only one channel if stereo
	private boolean lr_select; //If false, only output left, if true right. Output both if one_channel not set.

	/*----- Construction -----*/
	
	public XAAudioSampleStream(IXAAudioDataSource source){
		src = source;
		dataOnly = src.audioDataOnly();
		fetchAudioData(); //Gets sample rate, playback info etc.
	}
	
	private void fetchAudioData(){
		
		sector = src.nextSectorBuffer(); //Don't forget to dispose old ones!
		
		if(!dataOnly) {
			//Only reads from first sector...
			int cibyte = (int)sector.getByte(0x13);
			
			if((cibyte & 0x10) != 0) bit_mode = SAMPLES_8BIT_ADPCM;
			else bit_mode = SAMPLES_4BIT_ADPCM;
			
			if((cibyte & 0x04) != 0) sample_rate = 18900;
			else sample_rate = 37800;
			
			if((cibyte & 0x01) != 0) ch_count = 2;
			else ch_count = 1;
			
			//System.err.println("XAAudioSampleStream.fetchAudioData || Sample Rate: " + sample_rate);
			//System.err.println("XAAudioSampleStream.fetchAudioData || Bit Mode Enum: " + bit_mode);
			//System.err.println("XAAudioSampleStream.fetchAudioData || Channels: " + ch_count);
			
			s_pos = 0x18;
			sec_end = SEC_END;
		}
		else {
			//Can query directly
			sample_rate = src.getSampleRate();
			int bd = src.getBitDepth();
			if(bd == 4) bit_mode = SAMPLES_4BIT_ADPCM;
			else bit_mode = SAMPLES_8BIT_ADPCM;
			ch_count = src.getChannelCount();
			s_pos = 0x0;
			sec_end = PSXXAAudio.BLOCKS_PER_SEC << 7;
		}
		
		s_1 = new int[ch_count];
		s_2 = new int[ch_count];
		
		nextChunk();
	}
	
	/*----- Setters -----*/
	
	public void setOneChannel(boolean ch_right){
		one_channel = true;
		lr_select = ch_right;
	}
	
	public void unsetOneChannel(){one_channel = false;}
	
	/*----- ADPCM -----*/
	
	public static int[][] rawSamplesFromSector(FileBuffer sec){
		//Includes header bytes. Blocks deinterlaced.
		//Sigh... read header...
		int flags = Byte.toUnsignedInt(sec.getByte(0x13));
		boolean stereo = (flags & 0x1) != 0;
		boolean eight = (flags & 0x10) != 0;
		
		final int hdr_bytes = 18 << 3; //18 blocks times 8 header bytes
		int scount = 4032; //Base sample count
		if(eight) scount = scount >>> 1; //Halve if 8-bit
		if(stereo) scount = scount >>> 1; //Halve if stereo
		
		int chcount = stereo?2:1;
		int[][] sarr = new int[chcount][scount+hdr_bytes];
		
		int bcount = eight?4:8;
		if(stereo) bcount = bcount >>> 1; //Blocks per chunk
		int frames_per_chunk = scount/18;
		scount = frames_per_chunk/bcount; //Frames per sub-block
		
		long spos = 0x1c;
		int apos = 0;
		for(int b = 0; b < 128; b++){
			//Read the 8 header bytes.
			byte[] hdrs = sec.getBytes(spos, spos + 8);
			
			//Skip 4
			spos += 12;
			
			//Write header bytes...
			if(eight){
				if(stereo){
					int p1 = apos+scount+1;
					sarr[0][apos] = Byte.toUnsignedInt(hdrs[0]);
					sarr[1][p1] = Byte.toUnsignedInt(hdrs[2]);
					sarr[0][apos] = Byte.toUnsignedInt(hdrs[1]);
					sarr[1][p1] = Byte.toUnsignedInt(hdrs[3]);
				}
				else{
					int p = apos;
					for(int j = 0; j < 4; j++){
						sarr[0][p] = Byte.toUnsignedInt(hdrs[j]);
						p += scount+1;
					}
				}
			}
			else{
				if(stereo){
					int p = apos;
					for(int j = 0; j < 4; j++){
						for(int c = 0; c < 2; c++){
							sarr[c][p] = Byte.toUnsignedInt(hdrs[j]);
							p += scount+1;	
						}
					}
				}
				else{
					int p = apos;
					for(int j = 0; j < 8; j++){
						sarr[0][p] = Byte.toUnsignedInt(hdrs[j]);
						p += scount+1;
					}
				}
			}
			
			int[] off = new int[bcount];
			for(int j = 0; j < bcount; j++){
				//Where to put first sample.
				off[j] = apos+1;
				apos += scount+1;
			}

			for(int w = 0; w < 28; w++){
				int word = sec.intFromFile(spos); spos += 4;
				
				//Break up the word and put it in the output array.
				if(eight){
					if(stereo){
						//L R (L+samples per block) (R+samps per block)
						sarr[0][off[0]+w] = (word << 24) >> 24;
						sarr[1][off[0]+w] = (word << 16) >> 24;
						sarr[0][off[1]+w] = (word << 8) >> 24;
						sarr[1][off[1]+w] = word >> 24;
					}
					else{
						sarr[0][off[0]+w] = (word << 24) >> 24;
						sarr[0][off[1]+w] = (word << 16) >> 24;
						sarr[0][off[2]+w] = (word << 8) >> 24;
						sarr[0][off[3]+w] = word >> 24;
					}
				}
				else{
					if(stereo){
						//L R (L+samples per block) (R+samps per block)
						sarr[0][off[0]+w] = (word << 28) >> 28;
						sarr[1][off[0]+w] = (word << 24) >> 28;
						sarr[0][off[1]+w] = (word << 20) >> 28;
						sarr[1][off[1]+w] = (word << 16) >> 28;
						sarr[0][off[2]+w] = (word << 12) >> 28;
						sarr[1][off[2]+w] = (word << 8) >> 28;
						sarr[0][off[3]+w] = (word << 4) >> 28;
						sarr[1][off[3]+w] = word >> 28;
					}
					else{
						sarr[0][off[0]+w] = (word << 28) >> 28;
						sarr[0][off[1]+w] = (word << 24) >> 28;
						sarr[0][off[2]+w] = (word << 20) >> 28;
						sarr[0][off[3]+w] = (word << 16) >> 28;
						sarr[0][off[4]+w] = (word << 12) >> 28;
						sarr[0][off[5]+w] = (word << 8) >> 28;
						sarr[0][off[6]+w] = (word << 4) >> 28;
						sarr[0][off[7]+w] = word >> 28;
					}
				}
				
			}
			
		}
		
		return sarr;
	}
	
	public static int[][] decodeADPCM4(byte[] rawdata_chunk, boolean isStereo, int[] s1, int[] s2){
		//Input: Raw 128-byte chunk
		//Output: PCM samples
		
		//First 16 bytes are shift/filter "header" bytes
		//Then samples are grouped by block in 32-bit LE words
		int[] hdrs = new int[8];
		for(int i = 0; i < 8; i++)hdrs[i] = Byte.toUnsignedInt(rawdata_chunk[4+i]);
		
		//System.err.print("Headers --");
		//printBytes(hdrs, 8);
		
		int[][] out = null;
		if(isStereo) out = new int[2][112];
		else out = new int[1][224];
		
		int nybble = 0; //0 for lo, 1 for hi
		int by = 0; //Luckily it's LE
		int ch = 0;
		int opos = 0;
		for(int i = 0; i < 8; i++){
			int[] samps = new int[28];
			int pos = 0x10;
			if(isStereo) ch = i%2;
			
			for(int j = 0; j < 28; j++){
				int raw = Byte.toUnsignedInt(rawdata_chunk[pos+by]);
				raw = (raw >>> (nybble*4)) & 0xF;
				
				//Sign extend
				raw = (raw << 28) >> 28;
				samps[j] = raw;
				
				//Increment pos
				pos += 4;
			}

			//System.err.print("Block " + i + "--");
			//printBytes(samps, 28);
			//printValues(samps, 28);
			
			//Decode block
			int[] pcm = decodeBlockADPCM(hdrs[i], samps, s1[ch], s2[ch], false);
			s1[ch] = pcm[27]; s2[ch] = pcm[26];
			
			//Place in output
			if(isStereo){
				for(int j = 0; j < 28; j++) out[ch][opos++] = pcm[j];
				if(ch == 0) opos -= 28; //Reset opos for other channel
			}
			else{
				for(int j = 0; j < 28; j++) out[0][opos++] = pcm[j];
			}
			
			//Increment block pos
			if(nybble != 0){by++; nybble = 0;}
			else nybble = 1;
			
		}
		
		return out;
	}
	
	protected static int[] decodeBlockADPCM(int blockhdr, int[] samples, int s_1, int s_2, boolean eight){
		//Input: Header byte and block's samples (sign-extended from nybble or byte)
		//Output: PCM samples
		
		int[] out = new int[28];
		
		int shift = blockhdr & 0xf;
		if(shift > 12) shift = 9; //Nocash doc says 13-15 treated like 9? Eh, let's try it.
		if(eight) shift = 8 - shift;
		else shift = 12 - shift;
		int filter = (blockhdr >>> 4) & 0x3;
		int f0 = PSXVAG.FILTER_TABLE_1[filter];
		int f1 = PSXVAG.FILTER_TABLE_2[filter];
		for(int i = 0; i < 28; i++){
			int s = samples[i]; //Assumed already sign-extended
			s = (s << shift) + ((s_1*f0 + s_2*f1+32)/64);
			s = Math.max(s, (int)Short.MIN_VALUE);
			s = Math.min(s, 0x7FFF);
			out[i] = s;
			s_2 = s_1;
			s_1 = s;
		}
		
		return out;
	}
	
	public static int[][] decodeADPCM8(byte[] rawdata_chunk, boolean isStereo, int[] s1, int[] s2){
		//Input: Raw 128-byte chunk
		//Output: PCM samples
		int[] hdrs = new int[4];
		for(int i = 0; i < 4; i++)hdrs[i] = Byte.toUnsignedInt(rawdata_chunk[4+i]);
		
		int[][] out = null;
		if(isStereo) out = new int[2][56];
		else out = new int[1][112];
		
		int ch = 0;
		int opos = 0;
		for(int i = 0; i < 4; i++){
			int[] samps = new int[28];
			int pos = 0x10;
			if(isStereo) ch = i%2;
			
			for(int j = 0; j < 28; j++){
				int raw = (int)rawdata_chunk[pos+i];
				samps[j] = raw;
				
				//Increment pos
				pos += 4;
			}
			
			//Decode block
			int[] pcm = decodeBlockADPCM(hdrs[i], samps, s1[ch], s2[ch], false);
			s1[ch] = pcm[27]; s2[ch] = pcm[26];
			
			//Place in output
			if(isStereo){
				for(int j = 0; j < 28; j++) out[ch][opos++] = pcm[j];
				if(ch == 0) opos -= 28; //Reset opos for other channel
			}
			else{
				for(int j = 0; j < 28; j++) out[0][opos++] = pcm[j];
			}

		}
		
		return out;
	}
	
	/*----- Data Retrieval -----*/
	
	private boolean nextChunk(){
		if(s_pos >= sec_end){
			if(!nextSector()) return false;
		}
		
		byte[] raw = sector.getBytes(s_pos, s_pos+CHUNK_SIZE);
		s_pos += CHUNK_SIZE;
		
		//printBytes(raw, 32);
		
		samp_pos = 0;
		if(bit_mode == SAMPLES_8BIT_ADPCM) samp_buff = decodeADPCM8(raw, ch_count > 1, s_1, s_2);
		else samp_buff = decodeADPCM4(raw, ch_count > 1, s_1, s_2);
		if(samp_buff == null) return false;
		
		//Update s_1 and s_2
		int samps = samp_buff[0].length;
		for(int c = 0; c < ch_count; c++){
			s_1[c] = samp_buff[c][samps-1];
			s_2[c] = samp_buff[c][samps-2];
		}
		
		return true;
	}
	
	private boolean nextSector(){
		if(sector != null){
			try {sector.dispose();} 
			catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		s_pos = dataOnly?0:0x18;
		sector = src.nextSectorBuffer();
		
		if(sector == null) return false;
		
		return true;
	}
	
	/*----- AudioSampleStream -----*/
	
	public float getSampleRate() {return sample_rate;}
	public int getBitDepth() {return 16;}
	public int getChannelCount() {return ch_count;}

	public int[] nextSample() throws InterruptedException {
		int[] out = null;
		if(one_channel)out = new int[1];
		else out = new int[ch_count];
		if(done) return out;
		
		if(samp_pos >= samp_buff[0].length){
			if(!nextChunk()){
				//Assumed end.
				done = true;
				return out;
			}
		}
		
		if(one_channel && ch_count > 1){
			int ch = lr_select?1:0;
			out[0] = samp_buff[ch][samp_pos];
		}
		else {
			for(int c = 0; c < ch_count; c++) out[c] = samp_buff[c][samp_pos];
		}
		samp_pos++;
		
		return out;
	}

	public void close() {
		done = true;
		if(sector != null){
			try {sector.dispose();} 
			catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		samp_buff = null;
		s_1 = null;
		s_2 = null;
	}

	public boolean done() {return done;}

	/*----- Debug -----*/
	
	public static void printValues(int[] bytes, int num){
		if(bytes == null) System.err.println("<null>");
		
		for(int i = 0; i < num; i++){
			if(i % 16 == 0) System.err.println();
			if(i >= bytes.length) break;
			System.err.print(bytes[i] + " ");
		}
		
		System.err.println();
	}
	
	public static void printBytes(int[] bytes, int num){
		if(bytes == null) System.err.println("<null>");
		
		for(int i = 0; i < num; i++){
			if(i % 16 == 0) System.err.println();
			if(i >= bytes.length) break;
			System.err.print(String.format("%02x ", bytes[i]));
		}
		
		System.err.println();
	}
	
	public static void printBytes(byte[] bytes, int num){
		if(bytes == null) System.err.println("<null>");
		
		for(int i = 0; i < num; i++){
			if(i % 16 == 0) System.err.println();
			if(i >= bytes.length) break;
			System.err.print(String.format("%02x ", bytes[i]));
		}
		
		System.err.println();
	}
	
	public static void main(String[] args){
		//Test XAStream (by rendering to wave) and Audio Player
		
		String infile = "C:\\Users\\Blythe\\Documents\\Game Stuff\\PSX\\GameData\\MewMew\\MOVIE.BIN";
		
		try{
			
			//Load
			PSXXAStream fullstr = PSXXAStream.readStream(FileNode.createFileNodeOf(infile));
			XAStreamFile strfile = fullstr.getFile(0);
			int a_ch = 0;
			while(!strfile.streamExists(PSXXAStream.STYPE_AUDIO, a_ch)) a_ch++;
			System.err.println("Audio Channel Found: " + a_ch);
			
			XADataStream dstr = strfile.openStream(PSXXAStream.STYPE_AUDIO, a_ch);
			XAAudioSampleStream astr = new XAAudioSampleStream(dstr);
			
			//WAV test (write one minute to WAV)
			/*String testpath = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\psxtest\\xa_audio_test.wav";
			WAVWriter ww = new WAVWriter(astr, testpath);
			ww.write((int)astr.getSampleRate() * 60);
			//ww.write((int)astr.getSampleRate());
			ww.complete();
			astr.close();
			dstr.dispose();
			
			//Playback test
			dstr = strfile.openStream(PSXXAStream.STYPE_AUDIO, a_ch);
			astr = new XAAudioSampleStream(dstr);*/
			JavaSoundPlayer player = new JavaSoundPlayer_16LE(astr);
			player.open();
			//Thread.sleep(1000);
			player.play();
			
			Thread.sleep(10000);
			
		}
		catch(Exception e){
			e.printStackTrace();
			System.exit(1);
		}
		
	}
	
}
