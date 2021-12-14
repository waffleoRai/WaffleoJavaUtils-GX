package waffleoRai_Sound.nintendo;

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
import waffleoRai_soundbank.nintendo.Z64Bank;

public class Z64Wave extends SoundAdapter{

	/*----- Constants -----*/
	
	public static final String FN_METAKEY_SMALLSAMPS = "TWOBIT";
	public static final String FN_METAKEY_ADPCMPRED = "ADPCMPRED";
	public static final String FN_METAKEY_ADPCMORDER = "ADPCMORDER";
	public static final String FN_METAKEY_ADPCMPCOUNT = "ADPCMPCOUNT";
	public static final String FN_METAKEY_LOOPST = "LOOPST";
	public static final String FN_METAKEY_LOOPED = "LOOPED";
	
	public static final int DEFO_SAMPLERATE = 22050;
	
	public static final int CODEC_ADPCM = 0;
	public static final int CODEC_S8 = 1;
	public static final int CODEC_S16_INMEMORY = 2;
	public static final int CODEC_SMALL_ADPCM = 3;
	public static final int CODEC_REVERB = 4;
	public static final int CODEC_S16 = 5;
	
	public static final int MEDIUM_RAM = 0;
	public static final int MEDIUM_UNK = 1;
	public static final int MEDIUM_CART = 2;
	public static final int MEDIUM_DISK_DRIVE = 3;
	
	/*----- Instance Variables -----*/
	
	private N64ADPCMTable adpcm_table;
	private boolean tinySamps; //2bit samps, 5 byte blocks
	//private Z64ADPCM adpcm;
	
	private int loopCount;
	private int loopStart;
	private int loopEnd;
	
	private byte[] raw_data;
	
	/*----- Inner Classes -----*/
	
	public class Z64WaveSampleStream implements AudioSampleStream{

		private boolean loopMe;
		private Z64ADPCM state;
		
		private int pos;
		private int loopCt;
		private int endpos;
		private boolean doneflag;
		private boolean loopedflag;
		
		private int[] outBlock;
		
		public Z64WaveSampleStream(boolean loops){
			loopMe = loops;
			state = new Z64ADPCM(adpcm_table);
			pos = 0; loopCt = 0;
			endpos = totalFrames();
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
		super(DEFO_SAMPLERATE, 16, 1);
		adpcm_table = info.getADPCMBook();
	}
	
	public static Z64Wave readZ64Wave(FileBuffer data, Z64WaveInfo info){
		if(info.getADPCMBook() == null){
			System.err.println("Z64Wave.readZ64Wave || Input is not an ADPCM wave!");
			return null;
		}
		
		Z64Wave wav = new Z64Wave(info);
		wav.tinySamps = (info.getCodec() == Z64Wave.CODEC_SMALL_ADPCM);
		wav.loopCount = info.getLoopCount(); //0 means none, -1 means infinite
		wav.loopStart = info.getLoopStart();
		wav.loopEnd = info.getLoopEnd();
		
		//Read
		int bytelen = (int)data.getFileSize();
		int packsize = 9;
		if(wav.tinySamps) packsize = 5;
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
		int div = tinySamps?5:9;
		return (raw_data.length/div) << 4;
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
		if(tinySamps){
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
		else{
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
	
	/*----- Stream -----*/
	
	public AudioInputStream getStream() {
		return new AudioInputStream(new Z64WaveInputStream(), getFormat(), totalFrames());
	}

	public AudioSampleStream createSampleStream(boolean loop) {
		return new Z64WaveSampleStream(loop);
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
		String inpath = "";
		String outdir = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\n64test\\mmsounds";
		
		//Soundbank offset definitions...
		//(Just the first 5)
		int[] bankoffs = {0x20700, 0x288C0, 0x2BF90, 0x2CC70, 0x2E240, 0x2EDA0};
		int warcoff = 0x97F70;
		
		/*
		 * Okay, so the flags definitely change the reading somehow.
		 * The wave at 0x55600 (ROM offset 0xed570) won't decomp - lookup coeff ends up
		 * 	out of bounds. Flags 0x30
		 * It's quite possible that the blocks are just different sizes.
		 * I'll check my GameCube notes, might have something similar.
		 * 
		 * GameCube has option for 5-byte blocks instead of 9-byte
		 * 	In this case, it looks like the samples are 2 bits, not 4!
		 * 	Rest of the math seems to be the same tho
		 * 	Let's try that...
		 * 
		 */
		
		try{
			FileBuffer rom = FileBuffer.createBuffer(inpath, true);
			for(int i = 0; i < 5; i++){
				System.err.println("Reading from bank " + i);
				FileBuffer sbnk = rom.createReadOnlyCopy(bankoffs[i], bankoffs[i+1]);
				Z64Bank bank = Z64Bank.readBank(sbnk);
				List<Z64WaveInfo> waves = bank.getAllWaveInfoBlocks();
				for(Z64WaveInfo winfo: waves){
					//Get data.
					long wst = Integer.toUnsignedLong(warcoff + winfo.getWaveOffset());
					long wed = wst + winfo.getWaveSize();
					FileBuffer wdat = rom.createReadOnlyCopy(wst, wed);
					Z64Wave wave = Z64Wave.readZ64Wave(wdat, winfo);
					wave.writeToWav(outdir + "\\majorawav_" + String.format("%08x", wst) + ".wav");
				}
			}
		}
		catch(Exception ex){
			ex.printStackTrace();
			System.exit(1);
		}
	}
	
}
