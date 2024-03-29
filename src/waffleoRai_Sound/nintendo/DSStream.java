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

public class DSStream extends NinStream{
	
	public static final int TYPE_ID = 0x4e53544d;
	public static final String MAGIC = "STRM";
	
	public static DSStream readSTRM(FileBuffer file, long pos) throws UnsupportedFileTypeException{

		if(pos != 0) file = file.createReadOnlyCopy(pos, file.getFileSize());
		//Check for magic number
		long cpos = file.findString(0, 0x10, MAGIC);
		if(cpos != 0) throw new FileBuffer.UnsupportedFileTypeException("DSStream.readSTRM || Magic number \"STRM\" not found!");
		
		//Instantiate
		DSStream strm = new DSStream();
		strm.tracks = new ArrayList<NinStream.Track>(1); //DS Stream doesn't seem to have tracks?
		
		//Split sections (read common header)
		NDKDSFile dsfile = NDKDSFile.readDSFile(file);
		
		//Read STRM header chunk
		FileBuffer head = dsfile.getSectionData("HEAD");
		if(head == null) throw new FileBuffer.UnsupportedFileTypeException("DSStream.readSTRM || HEAD section could not be found!");
		head.setCurrentPosition(8); //Skip section header
		byte enc = head.nextByte();
		switch(enc){
		case 0: strm.encodingType = NinSound.ENCODING_TYPE_PCM8; break;
		case 1: strm.encodingType = NinSound.ENCODING_TYPE_PCM16; break;
		case 2: strm.encodingType = NinSound.ENCODING_TYPE_IMA_ADPCM; break;
		}
		
		strm.loops = (head.nextByte() != 0);
		strm.channelCount = Byte.toUnsignedInt(head.nextByte()); head.skipBytes(1); //Skip reserved
		strm.sampleRate = Short.toUnsignedInt(head.nextShort()); head.skipBytes(2); //Skip time
		strm.loopStart = head.nextInt();
		int fcount = head.nextInt();
		head.skipBytes(4); //Skip data offset (always 0x68 - relative to STRM start)
		int bcount = head.nextInt();
		head.skipBytes(4); //int blocksize = head.nextInt();
		int blocksamps = head.nextInt();
		head.skipBytes(4); //int lastb_sz = head.nextInt();
		int lastb_samps = head.nextInt();
		
		//Read audio data
		FileBuffer data = dsfile.getSectionData("DATA");
		if(data == null) throw new FileBuffer.UnsupportedFileTypeException("DSStream.readSTRM || DATA section could not be found!");
		strm.ima_table = new IMATable(strm.channelCount, bcount, blocksamps);
		NinStream.Track t = new NinStream.Track();
		t.chCount = strm.channelCount;
		t.leftChannelID = 0;
		if(t.chCount > 1) t.rightChannelID = 1;
		else t.rightChannelID = 0;
		strm.tracks.add(t);
		
		strm.rawSamples = new SampleChannel[strm.channelCount];
		//for(int i = 0; i < strm.channelCount; i++) strm.rawSamples[i] = SampleChannel.createArrayChannel(fcount);
		switch(strm.encodingType){
		case NinSound.ENCODING_TYPE_PCM8:
			for(int i = 0; i < strm.channelCount; i++) strm.rawSamples[i] = new SampleChannel8(fcount);
			break;
		case NinSound.ENCODING_TYPE_PCM16:
			for(int i = 0; i < strm.channelCount; i++) strm.rawSamples[i] = new SampleChannel16(fcount);
			break;
		case NinSound.ENCODING_TYPE_IMA_ADPCM:
			for(int i = 0; i < strm.channelCount; i++){
				SampleChannel4 chan = new SampleChannel4(fcount);
				chan.setNybbleOrder(false);
				strm.rawSamples[i] = chan;
			}
			break;
		}
		
		//Main blocks
		data.skipBytes(8);
		int fullblocks = bcount - 1;
		for(int i = 0; i < fullblocks; i++){
			for(int c = 0; c < strm.channelCount; c++){
				//if(strm.rawSamples[c].countSamples() <= 16)System.err.println();
				//System.err.println("Block " + i + " Channel " + c + " offset: 0x" + Long.toHexString(data.getCurrentPosition()));
				//If ADPCM, get first word
				if(strm.encodingType == NinSound.ENCODING_TYPE_IMA_ADPCM){
					int imaword = data.nextInt();
					//int samp = imaword & 0xFFFF; //Sign extend, you numbskull
					int samp = (int)((short)imaword);
					int idx = (imaword >>> 16) & 0x7F;
					//System.err.println("Block " + i + " Channel " + c + " IMA Word: 0x" + String.format("%08x", imaword) + " | Sample 0x" + String.format("%08x", samp) + " | Index: " + idx);
					strm.ima_table.init_samps[c][i] = samp;
					strm.ima_table.init_idxs[c][i] = idx;
				}
				//Now read samples
				switch(strm.encodingType){
				case NinSound.ENCODING_TYPE_PCM8:
					for(int f = 0; f < blocksamps; f++){
						strm.rawSamples[c].addSample(Byte.toUnsignedInt(data.nextByte()));
					}
					break;
				case NinSound.ENCODING_TYPE_PCM16:
					for(int f = 0; f < blocksamps; f++){
						strm.rawSamples[c].addSample((int)data.nextShort());
					}
					break;
				case NinSound.ENCODING_TYPE_IMA_ADPCM:
					for(int f = 0; f < blocksamps; f+=2){
						int b = Byte.toUnsignedInt(data.nextByte());
						int s1 = b & 0xF;
						int s2 = (b >>> 4) & 0xF;
						strm.rawSamples[c].addSample(s1);
						strm.rawSamples[c].addSample(s2);
						//if(strm.rawSamples[c].countSamples() <= 16){ System.err.print(String.format(" %01x", s1)); System.err.print(String.format(" %01x", s2));}
					}
					break;
				}
			}
		}
		
		//Last block
		for(int c = 0; c < strm.channelCount; c++){
			//If ADPCM, get first word
			if(strm.encodingType == NinSound.ENCODING_TYPE_IMA_ADPCM){
				int imaword = data.nextInt();
				int samp = (int)((short)imaword);
				strm.ima_table.init_samps[c][fullblocks] = (samp); 
				strm.ima_table.init_idxs[c][fullblocks] = (imaword >>> 16) & 0x7F;
			}
			//Now read samples
			switch(strm.encodingType){
			case NinSound.ENCODING_TYPE_PCM8:
				for(int f = 0; f < lastb_samps; f++){
					strm.rawSamples[c].addSample(Byte.toUnsignedInt(data.nextByte()));
				}
				break;
			case NinSound.ENCODING_TYPE_PCM16:
				for(int f = 0; f < lastb_samps; f++){
					strm.rawSamples[c].addSample((int)data.nextShort());
				}
				break;
			case NinSound.ENCODING_TYPE_IMA_ADPCM:
				for(int f = 0; f < lastb_samps; f+=2){
					int b = Byte.toUnsignedInt(data.nextByte());
					int s1 = b & 0xF;
					int s2 = (b >>> 4) & 0xF;
					strm.rawSamples[c].addSample(s1);
					strm.rawSamples[c].addSample(s2);
				}
				break;
			}
		}
		
		//If has IMA table, set initial values from IMA table
		if(strm.encodingType == NinSound.ENCODING_TYPE_IMA_ADPCM){
			strm.IMA_samp_init = new int[strm.channelCount];
			strm.IMA_idx_init = new int[strm.channelCount];
			for(int c = 0; c < strm.channelCount; c++){
				strm.IMA_samp_init[c] = strm.ima_table.init_samps[c][0];
				strm.IMA_idx_init[c] = strm.ima_table.init_idxs[c][0];
				//System.err.println("Init for channel " + c + ": Sample = 0x" + String.format("%04x", strm.IMA_samp_init[c]) + " | idx: " + strm.IMA_idx_init[c]);
			}
			strm.initializeIMAStateTables();
		}
		
		return strm;
	}

	@Override
	public Sound getSingleChannel(int channel) {
		if(channel < 0 || channel >= channelCount) throw new IndexOutOfBoundsException();
		if(channelCount == 1) return this;
		
		//Bugger
		DSStream copy = new DSStream();
		copy.channelCount = channelCount;
		copy.encodingType = encodingType;
		copy.loops = loops;
		copy.loopStart = loopStart;
		copy.sampleRate = sampleRate;
		
		//New track...
		copy.tracks = new ArrayList<NinStream.Track>(1);
		NinStream.Track t = new NinStream.Track();
		t.chCount = 1;
		t.leftChannelID = 0;
		t.rightChannelID = 0;
		copy.tracks.add(t);
		
		//Copy ADPCM stuff if applicable
		if(encodingType == NinSound.ENCODING_TYPE_IMA_ADPCM){
			copy.ima_state = new NinIMAADPCM[1];
			copy.ima_state[0] = ima_state[channel].createCopy();
			int bcount = ima_table.getBlockCount();
			copy.ima_table = new IMATable(1, bcount, ima_table.samples_per_block);
			for(int i = 0; i < bcount; i++){
				copy.ima_table.init_samps[0][i] = ima_table.init_samps[channel][i];
				copy.ima_table.init_idxs[0][i] = ima_table.init_idxs[channel][i];
			}
		}
		
		//Copy samples
		copy.rawSamples = new SampleChannel[1];
		SampleChannel mine = rawSamples[channel];
		//int fcount = mine.countSamples();
		//copy.rawSamples[0] = SampleChannel.createArrayChannel(fcount);
		copy.rawSamples[0] = mine.copy();
		//for(int i = 0; i < fcount; i++) copy.rawSamples[0].addSample(mine.getSample(i));
		
		return copy;
	}

	/*--- Definition ---*/
	
	private static DSStreamDef static_def;
	
	public static DSStreamDef getDefinition(){
		if(static_def == null) static_def = new DSStreamDef();
		return static_def;
	}
	
	public static class DSStreamDef extends SoundFileDefinition{

		private static final String DEFO_ENG_STR = "Nintendo Nitro SDK Audio Stream";
		private static final String[] EXT_LIST = {"strm", "STRM", "nstm", "bnstm"};
		
		private String str;
		
		public DSStreamDef(){
			str = DEFO_ENG_STR;
		}
		
		public Collection<String> getExtensions() {
			List<String> list = new ArrayList<String>(EXT_LIST.length);
			for(String s : EXT_LIST)list.add(s);
			return list;
		}

		public String getDescription() {return str;}
		public FileClass getFileClass() {return FileClass.SOUND_STREAM;}
		public int getTypeID() {return TYPE_ID;}
		public void setDescriptionString(String s) {str = s;}
		public String getDefaultExtension() {return "strm";}

		public Sound readSound(FileNode file) {
			//First look for magic. If there, include header read
			//If not, assume internal
			
			try{
				FileBuffer data = file.loadDecompressedData();	
				return readSTRM(data, 0);
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
	
	private static STRMConverter cdef;
	
	public static STRMConverter getDefaultConverter(){
		if(cdef == null) cdef = new STRMConverter();
		return cdef;
	}
	
	public static class STRMConverter implements Converter{

		public static final String DEFO_ENG_FROM = "Nitro Audio Stream (.strm)";
		public static final String DEFO_ENG_TO = "Uncompressed PCM RIFF Wave File (.wav)";
		
		private String from_desc;
		private String to_desc;
		
		public STRMConverter(){
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

			DSStream stream = DSStream.readSTRM(input, 0);
			stream.writeTrackAsWAV(outpath, 0);
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
