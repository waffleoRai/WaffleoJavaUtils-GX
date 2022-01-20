package waffleoRai_Sound.nintendo;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.sound.sampled.AudioInputStream;

import waffleoRai_Files.ConverterAdapter;
import waffleoRai_Files.FileClass;
import waffleoRai_Files.tree.FileNode;
import waffleoRai_Sound.Sound;
import waffleoRai_Sound.SoundAdapter;
import waffleoRai_Sound.SoundFileDefinition;
import waffleoRai_Sound.WAV;
import waffleoRai_Sound.WAV.LoopType;
import waffleoRai_SoundSynth.AudioSampleStream;
import waffleoRai_SoundSynth.soundformats.WAVWriter;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;

public class Z64Wave extends SoundAdapter{

	/*----- Constants -----*/
	
	public static final String FN_METAKEY_SMALLSAMPS = "TWOBIT";
	public static final String FN_METAKEY_ADPCMPRED = "ADPCMPRED";
	public static final String FN_METAKEY_ADPCMORDER = "ADPCMORDER";
	public static final String FN_METAKEY_ADPCMPCOUNT = "ADPCMPCOUNT";
	public static final String FN_METAKEY_LOOPST = "LOOPST";
	public static final String FN_METAKEY_LOOPED = "LOOPED";
	
	/*----- Instance Variables -----*/
	
	private N64ADPCMTable adpcm_table;
	private int codec;
	
	private int loopCount;
	private int loopStart;
	private int loopEnd;
	
	private float tuning;
	
	private byte[] raw_data;
	
	/*----- Inner Classes -----*/
	
	public class Z64PCMSampleStream implements AudioSampleStream{
		private boolean loopMe;
		
		private int pos;
		private int loopCt;
		private int endpos;
		private boolean doneflag;
		private boolean loopedflag;
		
		public Z64PCMSampleStream(boolean loops){
			loopMe = loops;
			pos = 0; loopCt = 0;
			endpos = totalFrames();
		}
		
		public float getSampleRate() {return (float)sampleRate;}
		public int getBitDepth() {return bitDepth;}
		public int getChannelCount() {return chCount;}
		
		public int[] nextSample() throws InterruptedException {
			if(doneflag) return new int[]{0};
			if(loopMe) {
				//Check for loop end
				if(pos >= loopEnd){
					if(!loopedflag){
						loopCt++;
						if(loopCount > 0 && loopCt >= loopCount)loopedflag = true;
						pos = loopStart;
					}
					else{
						//Last loop, check for end.
						if(pos >= endpos){
							doneflag = true;
							return new int[]{0};
						}
					}
				}
			}
			else{
				//Check for end
				if(pos >= endpos){
					doneflag = true;
					return new int[]{0};
				}
			}
			
			int samp = 0;
			if(codec == Z64Sound.CODEC_S8){
				//8 bit
				samp = Byte.toUnsignedInt(raw_data[pos++]);
				samp -= 128;
				float sf = (float)samp;
				if(samp >= 0) sf/=127.0f;
				else sf/=128.0f;
				samp = Math.round(sf * 65535.0f);
			}
			else{
				//16 bit
				int datpos = pos << 1;
				samp = (int)raw_data[datpos] << 8;
				samp |= Byte.toUnsignedInt(raw_data[datpos+1]);
				pos += 2;
			}
			
			return new int[]{samp};
		}
		
		public void close() {doneflag = true;}
		public boolean done() {return doneflag;}
	}
	
	public class Z64ADPCMSampleStream implements AudioSampleStream{

		private boolean loopMe;
		private Z64ADPCM state;
		
		private int pos;
		private int loopCt;
		private int endpos;
		private boolean doneflag;
		private boolean loopedflag;
		private boolean tinySamps;
		
		private int[] outBlock;
		
		public Z64ADPCMSampleStream(boolean loops){
			loopMe = loops;
			state = new Z64ADPCM(adpcm_table);
			pos = 0; loopCt = 0;
			endpos = totalFrames();
			tinySamps = (codec == Z64Sound.CODEC_SMALL_ADPCM);
		}
		
		public float getSampleRate() {return (float)sampleRate;}
		public int getBitDepth() {return bitDepth;}
		public int getChannelCount() {return chCount;}

		private boolean nextBlock(){
			//Loop or done?
			if(loopMe){
				//Check for loop end
				if(pos >= loopEnd){
					if(!loopedflag){
						//Loop.
						//Set loopedflag if last loop
						loopCt++;
						if(loopCount > 0 && loopCt >= loopCount)loopedflag = true;
						pos = loopStart;
						state.setToLoop(0);
					}
					else{
						//Last loop, check for end.
						if(pos >= endpos){
							doneflag = true;
							return false;
						}
					}
				}
			}
			else{
				//Check for end
				if(pos >= endpos){
					doneflag = true;
					return false;
				}
			}
			
			//If good, return next block (if not, just return false)
			if(outBlock == null) outBlock = new int[16];
			
			int bytepos = pos >>> 4;
			bytepos *= tinySamps?5:9;
			state.setControlByte(Byte.toUnsignedInt(raw_data[bytepos++]));
			int j = 0;
			if(tinySamps){
				for(int i = 0; i < 4; i++){
					int b = Byte.toUnsignedInt(raw_data[bytepos++]);
					//int[] minisamps = new int[4]; //Are these signed?
					for(int k = 0; k < 4; k++){
						int h = (3-k) << 1;
						int s = ((0x3 << h) & b) >>> h;
						if(s >= 2) s -= 4;
						outBlock[j++] = state.decompressNextNybble(s);
					}
				}
			}
			else{
				for(int i = 0; i < 8; i++){
					int b = Byte.toUnsignedInt(raw_data[bytepos++]);
					int nybble = (b >>> 4) & 0xF;
					if(nybble >= 8) nybble -= 16;
					outBlock[j++] = state.decompressNextNybble(nybble);
					nybble = b & 0xF;
					if(nybble >= 8) nybble -= 16;
					outBlock[j++] = state.decompressNextNybble(nybble);
				}	
			}
			
			return true;
		}
		
		public int[] nextSample() throws InterruptedException {
			if(outBlock == null) {
				if(!nextBlock()){
					doneflag = true;
					return new int[]{0};
				}
			}
			int samp = outBlock[pos & 0xF];
			if((++pos & 0xF) == 0) outBlock = null;
			return new int[]{samp};
		}

		public void close() {doneflag = true;}
		public boolean done() {return doneflag;}
		
	}
	
	public class Z64WaveInputStream extends InputStream{

		private AudioSampleStream sstr;
		private int lastSamp;
		private boolean hilo;
		
		public Z64WaveInputStream() {
			hilo = false; //lo
			lastSamp = 0;
			sstr = createSampleStream(false);
		}

		@Override
		public int read() throws IOException {
			if(!hilo){
				try{
					if(sstr.done()) return -1;
					lastSamp = sstr.nextSample()[0];
					hilo = true;
					return lastSamp & 0xFF;
				}
				catch(Exception ex){
					ex.printStackTrace();
					return 0;
				}
			}
			
			hilo = false;
			return (lastSamp >>> 8) & 0xFF;
		}
		
		public void close() throws IOException{
			super.close();
			sstr.close();
		}
		
	}
	
	/*----- Initialize -----*/
	
 	private Z64Wave(Z64WaveInfo info){
		super(Z64Sound.DEFO_SAMPLERATE, 16, 1);
		adpcm_table = info.getADPCMBook();
		
		codec = info.getCodec();
		loopCount = info.getLoopCount(); //0 means none, -1 means infinite
		loopStart = info.getLoopStart();
		loopEnd = info.getLoopEnd();
		setTuning(info.getTuning());
	}
	
	public static Z64Wave readZ64Wave(FileBuffer data, Z64WaveInfo info){
		Z64Wave wav = new Z64Wave(info);
		
		//Read
		int bytelen = (int)data.getFileSize();
		if(wav.codec == Z64Sound.CODEC_ADPCM || wav.codec == Z64Sound.CODEC_SMALL_ADPCM){
			int packsize = 9;
			if(wav.codec == Z64Sound.CODEC_SMALL_ADPCM) packsize = 5;
			//wav.raw_data = new byte[bytelen];
			bytelen = (bytelen/packsize) * packsize; //Snap to block
			wav.raw_data = data.getBytes(0, bytelen); 
			
			//Mark states in ADPCM table at start and loop point
			if(wav.loopStart >= 0){
				if(wav.adpcm_table != null){
					AudioSampleStream str = wav.createSampleStream(false);
					try{
						int order = wav.adpcm_table.getOrder();
						for(int i = 0; i < (wav.loopStart - order); i++) str.nextSample();
						for(int i = 0; i < order; i++){
							wav.adpcm_table.setStartBacksample(order-1-i, str.nextSample()[0]);
						}
						str.close();
					}
					catch(Exception e){e.printStackTrace();};
				}
			}
		}
		else{
			//Assume PCM.
			if(wav.codec == Z64Sound.CODEC_S8){
				//8 bit (assume unsigned?)
				wav.raw_data = data.getBytes(0, bytelen); 
			}
			else{
				//Assume 16 bit
				if(bytelen % 2 != 0) bytelen--;
				wav.raw_data = data.getBytes(0, bytelen); 
			}
		}
		
		return wav;
	}
	
	public static Z64Wave readZ64Wave(FileNode datanode) throws IOException{
		String pred_str = datanode.getMetadataValue(FN_METAKEY_ADPCMPRED);
		if(pred_str == null) return null;
		String[] split = pred_str.split(".");
		short[] ptbl = new short[split.length];
		for(int i = 0; i < split.length; i++){
			ptbl[i] = Short.parseShort(split[i], 16);
		}
		
		int order = 2; int pcount = 0;
		String s = datanode.getMetadataValue(FN_METAKEY_ADPCMORDER);
		if(s != null){
			try{order = Integer.parseInt(s);}
			catch(NumberFormatException ex){ex.printStackTrace();}
		}
		pcount = (ptbl.length >>> 3) / order;
		
		N64ADPCMTable codebook = N64ADPCMTable.fromRaw(order, pcount, ptbl);
		
		boolean smallsamps = (datanode.getMetadataValue(FN_METAKEY_SMALLSAMPS) != null);
		int loopSt = -1; int loopEd = -1;
		String val = datanode.getMetadataValue(FN_METAKEY_LOOPST);
		if(val != null) loopSt = Integer.parseInt(val);
		val = datanode.getMetadataValue(FN_METAKEY_LOOPED);
		if(val != null) loopEd = Integer.parseInt(val);
		
		Z64WaveInfo info = new Z64WaveInfo(codebook, smallsamps);
		info.setLoopStart(loopSt);
		info.setLoopEnd(loopEd);
		
		return Z64Wave.readZ64Wave(datanode.loadDecompressedData(), info);
	}
	
	/*----- Getters -----*/
	
	public int totalFrames() {
		switch(codec){
		case Z64Sound.CODEC_ADPCM: return (raw_data.length/9) << 4;
		case Z64Sound.CODEC_SMALL_ADPCM: return (raw_data.length/5) << 4;
		case Z64Sound.CODEC_S8: return raw_data.length;
		case Z64Sound.CODEC_S16: return raw_data.length >>> 1;
		default: return 0;
		}
	}

	public Sound getSingleChannel(int channel) {
		if(channel != 0) return null;
		return this;
	}

	public int[] getRawSamples(int channel) {
		if(channel != 0) return null;
		int frames = this.totalFrames();
		int[] samps = new int[frames];
		int blocks = frames >>> 4;
		int f = 0; int p = 0;
		if(codec == Z64Sound.CODEC_SMALL_ADPCM){
			for(int b = 0; b < blocks; b++){
				p++;
				for(int i = 0; i < 4; i++){
					//NOT sign extended
					int by = Byte.toUnsignedInt(raw_data[p++]);
					samps[f++] = ((by >>> 6) & 0x3);
					samps[f++] = ((by >>> 4) & 0x3);
					samps[f++] = ((by >>> 2) & 0x3);
					samps[f++] = by & 0x3;
				}
			}
		}
		else if(codec == Z64Sound.CODEC_ADPCM){
			for(int b = 0; b < blocks; b++){
				p++;
				for(int i = 0; i < 8; i++){
					//NOT sign extended
					int by = Byte.toUnsignedInt(raw_data[p++]);
					samps[f++] = ((by >>> 4) & 0xF);
					samps[f++] = by & 0xF;
				}
			}	
		}
		else if(codec == Z64Sound.CODEC_S8){
			for(int i = 0; i < raw_data.length; i++){
				samps[i] = Byte.toUnsignedInt(raw_data[i]);
			}
		}
		else if(codec == Z64Sound.CODEC_S16){
			for(int i = 0; i < raw_data.length; i+=2){
				int val = (int)raw_data[i] << 8;
				val |= Byte.toUnsignedInt(raw_data[i+1]);
				samps[f++] = val;
			}
		}
		
		return samps;
	}

	public int[] getSamples_16Signed(int channel) {
		if(channel != 0) return null;
		int frames = this.totalFrames();
		int[] samps = new int[frames];
		AudioSampleStream str = createSampleStream(false);
		try{
			for(int f = 0; f < frames; f++){
				samps[f] = str.nextSample()[0];
			}
			str.close();
		}
		catch(Exception ex){ex.printStackTrace();}
		
		return samps;
	}

	public int[] getSamples_24Signed(int channel) {
		if(channel != 0) return null;
		int[] samps = getSamples_16Signed(channel);
		for(int f = 0; f < samps.length; f++){
			samps[f] <<= 8;
		}
		return samps;
	}

	public boolean loops() {return loopCount >= 0;}
	public int getLoopFrame() {return loopStart;}
	public int getLoopEndFrame() {return loopEnd;}
	
	/*----- Setters -----*/
	
	public void setTuning(float value){
		if(value < 0) return; //Leave it alone.
		tuning = value;
		super.sampleRate = Math.round(tuning * (float)Z64Sound.DEFO_SAMPLERATE);
	}
	
	/*----- Stream -----*/
	
	public AudioInputStream getStream() {
		return new AudioInputStream(new Z64WaveInputStream(), getFormat(), totalFrames());
	}

	public AudioSampleStream createSampleStream(boolean loop) {
		switch(codec){
		case Z64Sound.CODEC_ADPCM:
		case Z64Sound.CODEC_SMALL_ADPCM:
			return new Z64ADPCMSampleStream(loop);
		case Z64Sound.CODEC_S8:
		case Z64Sound.CODEC_S16:
			return new Z64PCMSampleStream(loop);
		}
		return null;
	}
	
	/*----- Write -----*/
	
	public void writeToWav(String path) throws IOException{
		AudioSampleStream sstr = createSampleStream(false);
		WAVWriter writer = new WAVWriter(sstr, path);
		try{writer.write(this.totalFrames());}
		catch(Exception ex){ex.printStackTrace(); throw new IOException("I/O Operation interrupted!");}
		writer.complete();
		sstr.close();
	}
	
	/*----- Type Definition -----*/
	
	private static NUSALADPCMWave wave_def;
	private static NUSWave2WAVConverter conv_wav;
	
	public static NUSALADPCMWave getDefinition(){
		if(wave_def == null) wave_def = new NUSALADPCMWave();
		return wave_def;
	}
	
	public static NUSWave2WAVConverter getConverter_wav(){
		if(conv_wav == null) conv_wav = new NUSWave2WAVConverter();
		return conv_wav;
	}
	
	public static class NUSALADPCMWave extends SoundFileDefinition{
		
		private static String DEFO_ENG_DESC = "Nintendo 64 Audio Library Wave";
		public static int TYPE_ID = 0x6458a04d;

		private String desc;
		
		public NUSALADPCMWave(){
			desc = DEFO_ENG_DESC;
		}
		
		public Collection<String> getExtensions() {
			//I'll just make one up to match later consoles since there isn't one
			List<String> list = new ArrayList<String>(1);
			list.add("buwav");
			return list;
		}

		public String getDescription() {return desc;}
		public FileClass getFileClass() {return FileClass.SOUND_WAVE;}
		public int getTypeID() {return TYPE_ID;}
		public void setDescriptionString(String s) {desc = s;}
		public String getDefaultExtension() {return "buwav";}

		public Sound readSound(FileNode file) {
			try {
				return Z64Wave.readZ64Wave(file);
			} catch (IOException e) {
				e.printStackTrace();
			}
			return null;
		}
		
	}
	
	public static class NUSWave2WAVConverter extends ConverterAdapter{
		
		private static final String DEFO_FROMSTR_ENG = "N64 Standard Audio Library Wave";
		private static final String DEFO_TOSTR_ENG = "RIFF Wave Audio (.wav)";
		
		public NUSWave2WAVConverter(){
			super("buwav", DEFO_FROMSTR_ENG, "wav", DEFO_TOSTR_ENG);
		}

		public void writeAsTargetFormat(String inpath, String outpath)
				throws IOException, UnsupportedFileTypeException {
			throw new UnsupportedFileTypeException("ADPCM table required for decompression.");
		}

		public void writeAsTargetFormat(FileBuffer input, String outpath)
				throws IOException, UnsupportedFileTypeException {
			throw new UnsupportedFileTypeException("ADPCM table required for decompression.");
		}

		public void writeAsTargetFormat(FileNode node, String outpath)
				throws IOException, UnsupportedFileTypeException {
			Z64Wave wave = Z64Wave.readZ64Wave(node);
			
			WAV outwav = new WAV(16, 1, wave.totalFrames());
			outwav.copyData(0, wave.getSamples_16Signed(0));
			if(wave.loops()){
				outwav.setLoop(LoopType.Forward, wave.getLoopFrame(), wave.getLoopEndFrame());
			}
			outwav.writeFile(outpath);
		}
	}
	
	/*----- Debug -----*/

	public static void main(String[] args){
		String inpath = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\n64test\\nzlp_mq_dbg\\audiotable\\audiotable_4_4.bin";
		String outdir = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\n64test\\warc4";
		
		int[] guess_table = {-1850, -3250, -3900, -200, -50, 0, 0, 0,
							 3500, 4500, 2100, 125, 950, 10, -10, -40,
							 -1990, -3960, -5850, -7590, -9240, -10750, -12250, -13600,
							 3900, 5890, 7700, 9400, 11000, 12500, 13800, 15000};
		
		try{
			Z64WaveInfo info = new Z64WaveInfo();
			info.setCodec(Z64Sound.CODEC_ADPCM);
			
			//Determine length
			long filelen = FileBuffer.fileSize(inpath);
			filelen /= 9;
			filelen *= 9;
			FileBuffer dat = FileBuffer.createBuffer(inpath, 0, filelen);
			
			//Try various books...
			//All 0
			info.setADPCMBook(new N64ADPCMTable(2,2));
			
			//Read
			Z64Wave wave = Z64Wave.readZ64Wave(dat, info);
			wave.writeToWav(outdir + File.separator + "sample4_4__allzero.wav");
			
			info.setADPCMBook(N64ADPCMTable.fromRaw(2, 2, guess_table));
			wave = Z64Wave.readZ64Wave(dat, info);
			wave.writeToWav(outdir + File.separator + "sample4_4__guesstbl.wav");
			
		}
		catch(Exception ex){
			ex.printStackTrace();
			System.exit(1);
		}
	}
	
}
