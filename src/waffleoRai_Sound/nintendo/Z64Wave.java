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
import waffleoRai_soundbank.nintendo.Z64Bank.WaveInfoBlock;

public class Z64Wave extends SoundAdapter{

	/*----- Constants -----*/
	
	public static final String FN_METAKEY_SMALLSAMPS = "TWOBIT";
	public static final String FN_METAKEY_ADPCMPRED = "ADPCMPRED";
	public static final String FN_METAKEY_LOOPST = "LOOPST";
	public static final String FN_METAKEY_LOOPED = "LOOPED";
	
	/*----- Instance Variables -----*/
	
	private ADPCMTable adpcm_table;
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
						int s = (b << ((k << 1) + 24)); //Push to the left
						s >>= 30; //Push to the right (now sign-extended)
						outBlock[j++] = state.decompressNextNybble(s);
					}
				}
			}
			else{
				for(int i = 0; i < 8; i++){
					int b = Byte.toUnsignedInt(raw_data[bytepos++]);
					int nybble = (b >>> 4) & 0xF;
					outBlock[j++] = state.decompressNextNybble(nybble);
					nybble = b & 0xF;
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
	
 	private Z64Wave(short[] pred_table){
		super(22050, 16, 1);
		
		if(pred_table != null){
			adpcm_table = new ADPCMTable();
			adpcm_table.reallocCoefficientTable(pred_table.length);
			for(int i = 0; i < pred_table.length; i++){
				adpcm_table.setCoefficient(i, (int)pred_table[i]);
			}
			//adpcm = new Z64ADPCM(adpcm_table);
		}
	}
	
 	public static Z64Wave readZ64Wave(FileBuffer data, short[] predictors){
 		return readZ64Wave(data, predictors, -1, -1, false);
 	}
 	
	public static Z64Wave readZ64Wave(FileBuffer data, short[] predictors, int loopSt, int loopEd, boolean smallSamps){
		Z64Wave wav = new Z64Wave(predictors);
		wav.tinySamps = smallSamps;
		wav.loopCount = 0; //I use 0 to mean infinite and -1 to mean none. Z64 format is the opposite
		wav.loopStart = loopSt;
		wav.loopEnd = loopEd;
		
		//Read
		int bytelen = (int)data.getFileSize();
		if(smallSamps) bytelen = (bytelen/5) * 5;
		else bytelen = (bytelen/9) * 9; //Snap to block
		//wav.raw_data = new byte[bytelen];
		wav.raw_data = data.getBytes(0, bytelen);
		
		//Mark states in ADPCM table at start and loop point
		if(loopSt >= 0){
			if(wav.adpcm_table != null){
				AudioSampleStream str = wav.createSampleStream(false);
				try{
					for(int i = 0; i < loopSt-2; i++) str.nextSample();
					wav.adpcm_table.setLoopHistorySample2(str.nextSample()[0]);
					wav.adpcm_table.setLoopHistorySample1(str.nextSample()[0]);
					str.close();
				}
				catch(Exception e){e.printStackTrace();};
			}
		}
		else wav.loopCount = -1;
		
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
		
		boolean smallsamps = (datanode.getMetadataValue(FN_METAKEY_SMALLSAMPS) != null);
		int loopSt = -1; int loopEd = -1;
		String val = datanode.getMetadataValue(FN_METAKEY_LOOPST);
		if(val != null) loopSt = Integer.parseInt(val);
		val = datanode.getMetadataValue(FN_METAKEY_LOOPED);
		if(val != null) loopEd = Integer.parseInt(val);
		
		return Z64Wave.readZ64Wave(datanode.loadDecompressedData(), ptbl, loopSt, loopEd, smallsamps);
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
		String inpath = "C:\\Users\\Blythe\\Documents\\Game Stuff\\N64\\Games\\Legend of Zelda, The - Majora's Mask (U) [!].z64";
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
				List<WaveInfoBlock> waves = bank.getAllWaveInfoBlocks();
				for(WaveInfoBlock winfo: waves){
					//Get data.
					long wst = Integer.toUnsignedLong(warcoff + winfo.getOffset());
					long wed = wst + winfo.getLength();
					FileBuffer wdat = rom.createReadOnlyCopy(wst, wed);
					short[] tbl = winfo.getPredictorTable();
					int loopst = winfo.getLoopStart();
					int looped = winfo.getLoopEnd();
					boolean flag = (winfo.getFlags() & 0x30) != 0;
					Z64Wave wave = Z64Wave.readZ64Wave(wdat, tbl, loopst, looped, flag);
					wave.writeToWav(outdir + "\\majorawav_" + String.format("%08x", winfo.getOffset()) + ".wav");
				}
			}
		}
		catch(Exception ex){
			ex.printStackTrace();
			System.exit(1);
		}
	}
	

	

}
