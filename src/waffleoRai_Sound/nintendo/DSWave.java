package waffleoRai_Sound.nintendo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import waffleoRai_Containers.nintendo.NDKDSFile;
import waffleoRai_Files.Converter;
import waffleoRai_Files.FileClass;
import waffleoRai_Sound.SampleChannel;
import waffleoRai_Sound.SampleChannel16;
import waffleoRai_Sound.SampleChannel4;
import waffleoRai_Sound.SampleChannel8;
import waffleoRai_Sound.Sound;
import waffleoRai_Sound.SoundFileDefinition;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;
import waffleoRai_Files.tree.FileNode;

public class DSWave extends NinWave{
	
	public static final int TYPE_ID = 0x4e574156;
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
			//wave.rawSamples[0] = SampleChannel.createArrayChannel(fcount);
			wave.rawSamples[0] = new SampleChannel8(fcount);
			//PCM8 is unsigned
			for(int i = 0; i < fcount; i++) wave.rawSamples[0].addSample(Byte.toUnsignedInt(data.nextByte()));
			wave.loopStart = looppoint << 2;
			break;
		case NinSound.ENCODING_TYPE_PCM16:
			fcount = totalwords << 1; //Mult by 2 to get halfwords
			//wave.rawSamples[0] = SampleChannel.createArrayChannel(fcount);
			wave.rawSamples[0] = new SampleChannel16(fcount);
			for(int i = 0; i < fcount; i++) wave.rawSamples[0].addSample((int)data.nextShort());
			wave.loopStart = looppoint << 1;
			break;
		case NinSound.ENCODING_TYPE_IMA_ADPCM: 
			fcount = (totalwords-1) << 3; //Mult by 8 to get nybbles
			//System.err.println("\tFrame count: " + fcount + "(0x" + Integer.toHexString(fcount) + ")");
			//System.err.println("Current Position: " + "0x" + Long.toHexString(data.getCurrentPosition()) + ")");
			//LOWER nybble first!
			//wave.rawSamples[0] = SampleChannel.createArrayChannel(fcount);
			SampleChannel4 ch = new SampleChannel4(fcount);
			ch.setNybbleOrder(false);
			wave.rawSamples[0] = ch;
			for(int i = 0; i < fcount; i+=2){
				//int b = Byte.toUnsignedInt(data.nextByte());
				//wave.rawSamples[0].addSample(b & 0xF);
				//wave.rawSamples[0].addSample((b >>> 4) & 0xF);
				ch.addByte(data.nextByte());
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

	public static FileBuffer generateSWAVHeader(int data_size){
		FileBuffer header = new FileBuffer(16, false);
		header.printASCIIToFile(MAGIC);
		header.addToFile(0x0100feff); //Version & BOM
		header.addToFile(data_size + 16 + 8); //Total file size w this header and DATA header
		header.addToFile((short)16); //Header size
		header.addToFile((short)1); //Chunks (just DATA)
		
		return header;
	}
	
	/*--- Definition ---*/
	
	private static SWAVDef static_def;
	
	public static SWAVDef getDefinition(){
		if(static_def == null) static_def = new SWAVDef();
		return static_def;
	}
	
	public static class SWAVDef extends SoundFileDefinition{

		private static final String DEFO_ENG_STR = "Nitro Soundwave";
		private static final String[] EXT_LIST = {"swav", "SWAV", "nwav", "bnwav"};
		
		private String str;
		
		public SWAVDef(){
			str = DEFO_ENG_STR;
		}
		
		public Collection<String> getExtensions() {
			List<String> list = new ArrayList<String>(EXT_LIST.length);
			for(String s : EXT_LIST)list.add(s);
			return list;
		}

		public String getDescription() {return str;}
		public FileClass getFileClass() {return FileClass.SOUND_WAVE;}
		public int getTypeID() {return TYPE_ID;}
		public void setDescriptionString(String s) {str = s;}
		public String getDefaultExtension() {return "swav";}

		public Sound readSound(FileNode file) {
			//First look for magic. If there, include header read
			//If not, assume internal
			
			try{
				FileBuffer data = file.loadDecompressedData();	
				
				long mpos = data.findString(0, 0x10, MAGIC);
				if(mpos == 0)return readSWAV(data, 0);
				else return readInternalSWAV(data, 0);
			}
			catch(IOException e){
				e.printStackTrace();
				return null;
			} 
			catch (UnsupportedFileTypeException e) {
				e.printStackTrace();
				return null;
			}
		}
		
	}
	
	/*--- Converter ---*/
	
	private static SWAVConverter cdef;
	
	public static SWAVConverter getDefaultConverter(){
		if(cdef == null) cdef = new SWAVConverter();
		return cdef;
	}
	
	public static class SWAVConverter implements Converter{

		public static final String DEFO_ENG_FROM = "Nitro Soundwave (.swav)";
		public static final String DEFO_ENG_TO = "Uncompressed PCM RIFF Wave File (.wav)";
		
		private String from_desc;
		private String to_desc;
		
		public SWAVConverter(){
			from_desc = DEFO_ENG_FROM;
			to_desc = DEFO_ENG_TO;
		}
		
		public String getFromFormatDescription() {return from_desc;}
		public String getToFormatDescription() {return to_desc;}
		public void setFromFormatDescription(String s) {from_desc = s;}
		public void setToFormatDescription(String s) {to_desc = s;}

		public void writeAsTargetFormat(String inpath, String outpath)
				throws IOException, UnsupportedFileTypeException {
			writeAsTargetFormat(FileBuffer.createBuffer(inpath), outpath);
		}

		public void writeAsTargetFormat(FileBuffer input, String outpath)
				throws IOException, UnsupportedFileTypeException {

			long mpos = input.findString(0, 0x10, MAGIC);
			DSWave me = null;
			if(mpos == 0) me = DSWave.readSWAV(input, 0);
			else me = DSWave.readInternalSWAV(input, 0);
			
			me.writeWAV(outpath);
		}

		public void writeAsTargetFormat(FileNode node, String outpath) 
				throws IOException, UnsupportedFileTypeException{
			FileBuffer dat = node.loadDecompressedData();
			writeAsTargetFormat(dat, outpath);
		}
		
		public String changeExtension(String path) {
			if(path == null) return null;
			if(path.isEmpty()) return path;
			
			int lastdot = path.lastIndexOf('.');
			if(lastdot < 0) return path + ".wav";
			return path.substring(0, lastdot) + ".wav";
		}
		
	}
	
}
