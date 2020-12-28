package waffleoRai_SoundSynth.soundformats.game;

import java.io.IOException;

import waffleoRai_Files.psx.XADataStream;
import waffleoRai_Sound.psx.PSXVAG;
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
	
	private XADataStream src;
	private boolean done;
	
	private int s_pos; //Position within sector
	private FileBuffer sector; //current sector
	
	private int samp_pos; //Next sample index in sample buffer
	private int[][] samp_buff; //Last decomped samples

	/*----- Construction -----*/
	
	public XAAudioSampleStream(XADataStream source){
		src = source;
		fetchAudioData(); //Gets sample rate, playback info etc.
	}
	
	private void fetchAudioData(){
		//Only reads from first sector...
		
		sector = src.nextSectorBuffer(false); //Don't forget to dispose old ones!
		int cibyte = (int)sector.getByte(0x13);
		
		if((cibyte & 0x10) != 0) bit_mode = SAMPLES_8BIT_ADPCM;
		else bit_mode = SAMPLES_4BIT_ADPCM;
		
		if((cibyte & 0x04) != 0) sample_rate = 18900;
		else sample_rate = 37800;
		
		if((cibyte & 0x01) != 0) ch_count = 2;
		else ch_count = 1;
		
		s_1 = new int[ch_count];
		s_2 = new int[ch_count];
		
		s_pos = 0x18;
		nextChunk();
	}
	
	/*----- ADPCM -----*/
	
	public static int[][] decodeADPCM4(byte[] rawdata_chunk, boolean isStereo, int[] s1, int[] s2){
		//Input: Raw 128-byte chunk
		//Output: PCM samples
		
		//First 16 bytes are shift/filter "header" bytes
		//Then samples are grouped by block in 32-bit LE words
		int[] hdrs = new int[8];
		for(int i = 0; i < 8; i++)hdrs[i] = Byte.toUnsignedInt(rawdata_chunk[4+i]);
		
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
				raw = (raw << 28) >>> 28;
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
		if(s_pos >= SEC_END){
			if(!nextSector()) return false;
		}
		
		byte[] raw = sector.getBytes(s_pos, s_pos+CHUNK_SIZE);
		s_pos += CHUNK_SIZE;
		
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
		
		s_pos = 0;
		sector = src.nextSectorBuffer(false);
		
		if(sector == null) return false;
		
		return true;
	}
	
	/*----- AudioSampleStream -----*/
	
	public float getSampleRate() {return sample_rate;}
	public int getBitDepth() {return 16;}
	public int getChannelCount() {return ch_count;}

	public int[] nextSample() throws InterruptedException {
		int[] out = new int[ch_count];
		if(done) return out;
		
		if(samp_pos >= samp_buff[0].length){
			if(!nextChunk()){
				//Assumed end.
				done = true;
				return out;
			}
		}
		
		for(int c = 0; c < ch_count; c++) out[c] = samp_buff[c][samp_pos];
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

}
