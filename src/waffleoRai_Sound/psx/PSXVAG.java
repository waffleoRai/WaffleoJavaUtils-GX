package waffleoRai_Sound.psx;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;

import waffleoRai_Files.Converter;
import waffleoRai_Files.FileClass;
import waffleoRai_Files.FileTypeDefinition;
import waffleoRai_Files.tree.FileNode;
import waffleoRai_Sound.BitDepth;
import waffleoRai_Sound.Sound;
import waffleoRai_Sound.SoundFileDefinition;
import waffleoRai_Sound.WAV;
import waffleoRai_SoundSynth.AudioSampleStream;
import waffleoRai_SoundSynth.soundformats.game.PSXVAGSampleStream;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;

public class PSXVAG implements Sound{
	
	/* ----- Constants ----- */
	
	public static final String MAGIC_LE = "pGAV";
	
	//This seems to be what AIFF2VAG from SDK 4.4 writes?
	public static final int WRITE_VER = 0x20; //Looks like 0x20000000 in LE
	
	/*public static final double[][] COEFF_TABLE = {{0.0, 0.0}, 
												  {60.0 / 64.0, 0.0}, 
												  {115.0 / 64.0, 52.0 / 64.0}, 
												  {98.0 / 64.0, 55.0 / 64.0}, 
												  {122.0 / 64.0, 60.0 / 64.0}};*/
	
	 /** One of the two sets of filter values for the PSX ADPCM algorithm. Required
	 * for audio decompression.
	 */
	public static final int[] FILTER_TABLE_1 = {0, 60, 115, 98, 122};
	
	/**
	 * One of the two sets of filter values for the PSX ADPCM algorithm. Required
	 * for audio decompression.
	 */
	public static final int[] FILTER_TABLE_2 = {0, 0, -52, -55, -60};
	
	
	public static final int CHUNK_SIZE = 16;
	
	public static final int AUDIO_STREAM_BUFFERED_CHUNKS = 256;
	
	public static final int DECOMPRESSED_SAMPLE_BITS = 16;
	public static final int CHANNELS = 1;
	public static final int DECOMPRESSED_BYTES_PER_FRAME = 2;
	
	public static final int ENCMODE_NORMAL = 1;
	public static final int ENCMODE_HIGH = 2;
	public static final int ENCMODE_LOW = 3;
	public static final int ENCMODE_4BIT = 4;
	
	public static final int BLOCK_ATTR_1_SHOT = 0;
	public static final int BLOCK_ATTR_1_SHOT_END = 1;
	public static final int BLOCK_ATTR_START = 2;
	public static final int BLOCK_ATTR_BODY = 3;
	public static final int BLOCK_ATTR_END = 4;
	
	/* ----- Instance Variables ----- */
	
	private String name;
	
	private int version;
	private int sampleRate;
	
	private Chunk[] soundData;
	
	private int currentFrame;
	private int[] decompressedBuffer;
	
	private int iloop;
	
	/* ----- Internal Classes ----- */
	
	public static class Chunk {
		private int range;
		private int filter;
		
		private boolean loops;
		private boolean loopPoint;
		private boolean isend;
		
		private int[] samples;
		
		public Chunk(){
			samples = new int[28];
		}
		
		public boolean loops(){return loops;}
		public boolean isLoopPoint(){return loopPoint;}
		public boolean isEnd(){return isend;}
		public void setRange(int n){range = n;}
		public void setFilter(int n){filter = n;}
		public void setLoops(boolean b){loops = b;}
		public void setLoopPoint(boolean b){loopPoint = b;}
		public void setEnd(boolean b){isend = b;}
		public void setSample(int i, int s){samples[i] = s;}
		
		public byte[] serializeMe(){
			byte[] cbytes = new byte[16];
			filter &= 0xF;
			range &= 0xF;
			cbytes[0] = (byte)((filter << 4) | range);
			
			int flags = 0;
			if (isend) flags |= 0x01;
			if (loops) flags |= 0x02;
			if (loopPoint) flags |= 0x04;
			cbytes[1] = (byte)flags;
			
			for (int i = 0; i < 14; i++){
				int si = (i*2);
				int s0 = samples[si] & 0xF;
				int s1 = samples[si + 1] & 0xF;
				int ds = (s1 << 4) | s0;
				cbytes[i+2] = (byte)ds;
			}
			
			return cbytes;
		}
		
		public int[] decompressSamples(int s_last, int s_beforelast){
			int[] sdecomp = new int[28];
			int f0 = FILTER_TABLE_1[filter];
			int f1 = FILTER_TABLE_2[filter];
			for (int i = 0; i < 28; i++){
				//x(n) = c(n) + [ f1*x(n-1) - f2*x(n-2)]
				/*int s = samples[i] << 12;
				s = s >>> range;
				double f1 = COEFF_TABLE[filter][0];
				double f2 = COEFF_TABLE[filter][1];
				int us = s + (int)Math.round((f1 * (double)s_last) - (f2 * (double)s_beforelast));
				sdecomp[i] = us;
				s_beforelast = s_last;
				s_last = us;*/
				int s = samples[i] << 28;
				s = s >> 16; //Double shift takes care of sign extension
				s = s >> range;
				//int us = s + (int)((f1 * (double)s_last) - (f2 * (double)s_beforelast));
				int us = s + (((s_last * f0) + (s_beforelast * f1)) >> 6);
				//int us = s + (int)Math.round((double)((s_last * f0) + (s_beforelast * f1))/64.0);
				
				sdecomp[i] = us;
				s_beforelast = s_last;
				s_last = us;
			}
			
			return sdecomp;
		}
		
	}
	
	/* ----- Construction ----- */
	
	public PSXVAG(String filepath) throws IOException, UnsupportedFileTypeException {
		currentFrame = 0;
		decompressedBuffer = null;
		FileBuffer myfile = FileBuffer.createBuffer(filepath, false);
		parseVAG(myfile);
	}
	
	public PSXVAG(FileBuffer VAGFile) throws UnsupportedFileTypeException {
		iloop = -1;
		currentFrame = 0;
		decompressedBuffer = null;
		if (VAGFile != null) {
			VAGFile.setEndian(false);
			parseVAG(VAGFile);
		}
		else throw new FileBuffer.UnsupportedFileTypeException();
	}
	
	/* ----- Parsing ----- */
	
	private void parseVAG(FileBuffer mysound) throws UnsupportedFileTypeException {
		//Quick rejection - size must be a multiple of 16
		long fsz = mysound.getFileSize();
		if (fsz % 16 != 0) throw new FileBuffer.UnsupportedFileTypeException();
		
		//Check for a header
		long mpos = mysound.findString(0, 0x10, MAGIC_LE);
		long cpos = 0;
		int nchunks = 0;
		
		if (mpos == 0)
		{
			//If there, parse header
			cpos = 4;
			version = mysound.intFromFile(cpos); cpos += 4;
			cpos += 8;
			sampleRate = mysound.intFromFile(cpos); cpos += 16;
			name = mysound.getASCII_string(cpos, 16); cpos += 16;
			nchunks = (int)((fsz - 48)/16);
		}
		else
		{
			//If not there, just assume it's legit
			version = 1;
			sampleRate = 44100;
			Random rand = new Random();
			int vnum = rand.nextInt();
			name = "VAGp_" + String.format("%08X", vnum);
			nchunks = (int)(fsz/16);
		}

		//If not there, just assume it's legit
		soundData = new Chunk[nchunks];
		
		for (int c = 0; c < nchunks; c++)
		{
			soundData[c] = new Chunk();
			
			//Get filter/range
			int b0 = Byte.toUnsignedInt(mysound.getByte(cpos)); cpos++;
			int f = (b0 >>> 4) & 0xF;
			int r = b0 & 0xF;
			soundData[c].setFilter(f);
			soundData[c].setRange(r);
			
			//Get flags
			int b1 = Byte.toUnsignedInt(mysound.getByte(cpos)); cpos++;
			boolean f0 = (b1 & 0x01) != 0;
			boolean f1 = (b1 & 0x02) != 0;
			boolean f2 = (b1 & 0x04) != 0;
			soundData[c].setEnd(f0);
			soundData[c].setLoops(f1);
			soundData[c].setLoopPoint(f2);
			
			//Get samples
			for (int i = 0; i < 14; i++)
			{
				int si = (i*2);
				int b = Byte.toUnsignedInt(mysound.getByte(cpos)); cpos++;
				int s1 = (b >>> 4) & 0xF;
				int s0 = b & 0xF;
				soundData[c].setSample(si, s0);
				soundData[c].setSample(si + 1, s1);
			}
			
		}
		
	}
	
	/* ----- Serialization ----- */
	
	public FileBuffer serializeVAGData(){
		int datSz = soundData.length * 16;
		FileBuffer mysound = new FileBuffer(datSz);
		
		for (int c = 0; c < soundData.length; c++){
			if (soundData[c] != null){
				byte[] row = soundData[c].serializeMe();
				for (int i = 0; i < row.length; i++) mysound.addToFile(row[i]);
			}
		}
		
		return mysound;
	}
	
	public void writeVAG(String outpath) throws IOException {
		int datSz = soundData.length * 16;
		
		FileBuffer mysound = new FileBuffer(datSz + 48);
		
		mysound.printASCIIToFile(MAGIC_LE);
		mysound.addToFile(version);
		//Don't know what reserved is...
		mysound.addToFile(0);
		mysound.addToFile(datSz);
		mysound.addToFile(sampleRate);
		mysound.addToFile(0);
		mysound.addToFile(0);
		mysound.addToFile(0);
		int nlen = name.length();
		if (nlen > 16) {
			name = name.substring(0, 16);
			nlen = name.length();
		}
		mysound.printASCIIToFile(name);
		if (nlen < 16){
			for (int i = nlen; i < 16; i++) mysound.addToFile((byte)0x00);
		}
		
		//Data
		for (int c = 0; c < soundData.length; c++){
			if (soundData[c] != null){
				byte[] row = soundData[c].serializeMe();
				for (int i = 0; i < row.length; i++) mysound.addToFile(row[i]);
			}
		}
		
		mysound.writeFile(outpath);
		
	}

	public static void writeVAGFromRawData(OutputStream out, byte[] soundData) throws IOException{
		writeVAGFromRawData(out, 44100, soundData);
	}
	
	public static void writeVAGFromRawData(OutputStream out, int sampleRate, byte[] soundData) throws IOException{
		FileBuffer header = new FileBuffer(0x30, false);
		header.printASCIIToFile(PSXVAG.MAGIC_LE);
		header.addToFile(PSXVAG.WRITE_VER);
		header.addToFile(0);
		header.addToFile(soundData.length);
		header.addToFile(sampleRate);
		for(int i = 0; i < 3; i++) header.addToFile(0);
		for(int i = 0; i < 4; i++) header.addToFile(0);
		
		header.writeToStream(out);
		out.write(soundData);
	}
	
	/* ----- Getters ----- */
	
	public String getName(){
		return name;
	}
	
	public int getVersion(){
		return version;
	}
	
	public int getSampleRate(){
		return sampleRate;
	}
	
	/* ----- Conversion ----- */
	
	public boolean loops(){
		if (soundData == null) return false;
		if(iloop >= 0) return (iloop != 0);
		iloop = 0;
		for (Chunk c : soundData) {
			if (c != null){
				if(c.isEnd()) return (iloop != 0);
				if (c.loops()){
					iloop = 1;
					return true;
				}
			}
		}
		return (iloop != 0);
	}
	
	public int getLoopPointChunkIndex(){
		if (soundData == null) return -1;
		for (int i = 0; i < soundData.length; i++){
			Chunk c = soundData[i];
			if (c != null){
				if (c.isLoopPoint()) return i;
			}
		}
		return -1;
	}
	
	public int getLoopPointSampleIndex(){
		if (soundData == null) return -1;
		for (int i = 0; i < soundData.length; i++){
			Chunk c = soundData[i];
			if (c != null){
				if (c.isLoopPoint()) return (i * 28);
			}
		}
		return -1;
	}
	
	public int countChunks(){
		return soundData.length;
	}
	
	public int countSamples(){
		if (soundData == null) return 0;
		int scount = 0;
		for (Chunk c : soundData){
			if (c != null) {
				scount += 28;
				if (c.isEnd()) break;
			}
		}
		return scount;
	}
	
	public int[] getDecompressedSamples(){
		//Remember not to read past the end!
		int[] decsamps = new int[countSamples()];
		int si = 0;
		int n1 = 0;
		int n2 = 0;
		for (Chunk c : soundData){
			if (c == null) break;
			int[] csamps = c.decompressSamples(n1, n2);
			for (int s : csamps) {
				decsamps[si] = s;
				si++;
			}
			n1 = csamps[27];
			n2 = csamps[26];
			if (c.isEnd()) break;
		}
		
		return decsamps;
	}

	public int[] getDecompressedLoopChunk(){
		if (!this.loops()) return null;
		int n1 = 0;
		int n2 = 0;
		for (Chunk c : soundData){
			if (c == null) break;
			int[] csamps = c.decompressSamples(n1, n2);
			n1 = csamps[27];
			n2 = csamps[26];
			if (c.isLoopPoint()) return csamps;
		}
		return null;
	}
	
	public WAV convertToWAV(){
		int[] dsamps = getDecompressedSamples(); //16 bit
		
		WAV mywav = new WAV(16, 1, dsamps.length);
		mywav.setSampleRate(sampleRate);
		
		//Copy samples
		mywav.copyData(0, dsamps);
		
		//Set loop data
		if(loops()){
			int st = getLoopPointSampleIndex();
			int ed = dsamps.length;
			mywav.setLoop(WAV.LoopType.Forward, st, ed);	
		}
		
		return mywav;
	}
	
	/* ----- Calculations ----- */
	
	public int getDataSize(){
		if (soundData == null) return 0;
		return soundData.length * CHUNK_SIZE;
	}
	
	/* ----- Java Interface ----- */
	
	public Chunk getChunk(int i){
		return soundData[i];
	}
	
	public AudioFormat getFormat(){
		AudioFormat vagformat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, (float)sampleRate, 
				DECOMPRESSED_SAMPLE_BITS, 
				CHANNELS, DECOMPRESSED_BYTES_PER_FRAME,
				(float)sampleRate, false);
		return vagformat;
	}
	
	public AudioInputStream getStream(){
		PSXVAGInputStream rawis = new PSXVAGInputStream(this, AUDIO_STREAM_BUFFERED_CHUNKS);
		AudioInputStream ais = new AudioInputStream(rawis, getFormat(), countSamples());
		return ais;
	}
	
	/* ----- Local Interface ----- */
	
	public void jumpToFrame(int frame){
		currentFrame = frame;
	}
	
	public void rewind(){
		currentFrame = 0;
	}
	
	public int nextSample(int channel){
		if (decompressedBuffer == null) decompressedBuffer = getDecompressedSamples();
		if (currentFrame >= decompressedBuffer.length) return -1;
		return decompressedBuffer[currentFrame];
	}
	
	public int samplesLeft(int channel){
		if (decompressedBuffer == null) decompressedBuffer = getDecompressedSamples();
		return decompressedBuffer.length - currentFrame;
	}
	
	public boolean hasSamplesLeft(int channel){
		return samplesLeft(channel) > 0;
	}
	
	public void flushBuffer(){
		decompressedBuffer = null;
	}
	
	public int totalFrames(){
		return this.countSamples();
	}
	
	public int getLoopFrame(){
		return getLoopPointSampleIndex();
	}
	
	public int getLoopEndFrame(){
		return totalFrames();
	}

	public int[] getRawSamples(int channel){
		return this.getDecompressedSamples();
	}
	
	public BitDepth getBitDepth(){
		return BitDepth.SIXTEEN_BIT_SIGNED;
	}
	
	public int[] getSamples_16Signed(int channel){
		return getDecompressedSamples();
	}
	
	public int[] getSamples_24Signed(int channel){
		int[] rawsamps = getDecompressedSamples();
		int frames = rawsamps.length;
		int[] scaled = new int[frames];
		for (int i = 0; i < frames; i++){
			int samp = rawsamps[i];
			scaled[i] = Sound.scaleSampleUp8Bits(samp, BitDepth.SIXTEEN_BIT_SIGNED);
		}
		
		return scaled;
	}
	
	public int totalChannels(){return 1;}
	public int getUnityNote(){return 60;}
	public int getFineTune(){return 0;}
	public Sound getSingleChannel(int channel){return this;}
	
	public AudioSampleStream createSampleStream(){
		return new PSXVAGSampleStream(this);
	}
	
	public AudioSampleStream createSampleStream(boolean loop){
		return new PSXVAGSampleStream(this, loop);
	}
	
	public void setActiveTrack(int tidx){}
	public int countTracks(){return 1;}
	
	/*--- Definition ---*/
	
	public static final int DEF_ID = 0x70474156;
	private static final String DEFO_ENG_STR = "PlayStation 1 Audio Sample";
	
	private static VAGPDef stat_def;
	private static VAGP2WAVConv stat_conv;
	
	public static class VAGPDef extends SoundFileDefinition{
		
		private String desc = DEFO_ENG_STR;
		
		public Collection<String> getExtensions() {
			List<String> list = new ArrayList<String>(2);
			list.add("vag");
			list.add("vagp");
			return list;
		}

		public String getDescription() {return desc;}
		public FileClass getFileClass() {return FileClass.SOUND_WAVE;}

		public int getTypeID() {return DEF_ID;}
		public void setDescriptionString(String s) {desc = s;}
		public String getDefaultExtension() {return "vag";}

		public Sound readSound(FileNode file) {
			try{
				FileBuffer dat = file.loadDecompressedData();
				return new PSXVAG(dat);
			}
			catch(Exception x){
				x.printStackTrace();
				return null;
			}
		}
		
		public String toString(){return FileTypeDefinition.stringMe(this);}
		
	}
	
	public static VAGPDef getDefinition(){
		if(stat_def == null) stat_def = new VAGPDef();
		return stat_def;
	}
	
	public static class VAGP2WAVConv implements Converter{
		
		private String desc_from = DEFO_ENG_STR;
		private String desc_to = "RIFF Waveform Audio File (WAV)";

		public String getFromFormatDescription() {return desc_from;}
		public String getToFormatDescription() {return desc_to;}
		public void setFromFormatDescription(String s) {desc_from = s;}
		public void setToFormatDescription(String s) {desc_to = s;}

		public void writeAsTargetFormat(String inpath, String outpath)
				throws IOException, UnsupportedFileTypeException {
			FileBuffer dat = FileBuffer.createBuffer(inpath, false);
			writeAsTargetFormat(dat, outpath);
		}

		public void writeAsTargetFormat(FileBuffer input, String outpath)
				throws IOException, UnsupportedFileTypeException {
			//Use this one.
			PSXVAG sound = new PSXVAG(input);
			WAV wav = sound.convertToWAV();
			wav.writeFile(outpath);
		}

		public void writeAsTargetFormat(FileNode node, String outpath)
				throws IOException, UnsupportedFileTypeException {
			FileBuffer dat = node.loadDecompressedData();
			writeAsTargetFormat(dat, outpath);	
		}

		public String changeExtension(String path) {return path.replace(".vag", ".wav");}
		
	}
	
	public static VAGP2WAVConv getConverter(){
		if(stat_conv == null) stat_conv = new VAGP2WAVConv();
		return stat_conv;
	}
	
}
