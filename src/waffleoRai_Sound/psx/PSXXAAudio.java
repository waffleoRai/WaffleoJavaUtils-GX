package waffleoRai_Sound.psx;

import java.io.IOException;

import waffleoRai_Files.psx.XADataStream;
import waffleoRai_Sound.AiffFile;
import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;

public class PSXXAAudio {
	
	public static final int AIFC_ID_XAAUD = 0x70414158;
	public static final String AIFC_IDSTR_XAAUD = "Sony XA ADPCM Audio";
	
	public static final int BLOCK_SIZE = 128;
	public static final int BLOCKS_PER_SEC = 18;
	
	public static XAAudioDataOnlyStream readAifc(BufferReference inputData) throws UnsupportedFileTypeException, IOException {
		AiffFile aiff = AiffFile.readAiff(inputData);
		int comprId = aiff.getCompressionId();
		if(comprId != AIFC_ID_XAAUD) throw new UnsupportedFileTypeException("PSXXAAudio.readAifc | Compression format not recognized!");
		
		XAAudioDataOnlyStream str = new XAAudioDataOnlyStream();
		str.setChannelCount(aiff.getChannelCount());
		str.setBitDepth(aiff.getRawBitDepth());
		str.setSampleRate((int)Math.round(aiff.getSampleRate()));
		
		str.setAudioData(aiff.getAifcRawSndData());
		
		return str;
	}
	
	public static boolean writeAifc(byte[] rawAudioData, String outputPath, int sampleRate, int bitDepth, int chCount) throws IOException {
		if(rawAudioData == null) return false;
		
		AiffFile aiff = AiffFile.emptyAiffFile(chCount);
		aiff.setSampleRate(sampleRate);
		aiff.setBitDepth((short)bitDepth);
		aiff.setCompressionId(AIFC_ID_XAAUD);
		aiff.setCompressionName(AIFC_IDSTR_XAAUD);
		
		//Calculate frames from data size and other parameters
		int frameCount = rawAudioData.length >> 7; //Number of 128 byte blocks
		frameCount *= (28 << 2); //Samples per block.
		if(bitDepth == 4) frameCount <<= 1;
		frameCount /= chCount;
		
		aiff.setFrameCountDirect(frameCount);
		
		aiff.setAifcRawSndData(rawAudioData);
		FileBuffer ser = aiff.serializeAiff();
		ser.writeFile(outputPath);
		ser.dispose();
		
		return true;
	}
	
	public static boolean writeAifc(XAAudioStream stream, int start, int secCount, String outputPath) throws IOException {
		if(stream == null) return false;
		XADataStream datStr = stream.openDataStream(0);
		
		//Forward datStr to start
		if(start > 0) {
			datStr.skipSectors(start);
		}
		
		int chCount = stream.totalChannels();
		int bd = stream.getSourceBitDepth();
		
		AiffFile aiff = AiffFile.emptyAiffFile(chCount);
		aiff.setSampleRate(stream.getSampleRate());
		aiff.setBitDepth((short)bd);
		aiff.setCompressionId(AIFC_ID_XAAUD);
		aiff.setCompressionName(AIFC_IDSTR_XAAUD);
		
		int blocks = secCount * BLOCKS_PER_SEC;
		int frames = blocks * (28 << 3); //Mono 4-bit
		if(bd == 8) frames >>>= 1;
		if(chCount > 1) frames /= chCount;
		aiff.setFrameCountDirect(frames);
		
		int bytesPerSec = (BLOCKS_PER_SEC << 7);
		int bytes = secCount * bytesPerSec;
		byte[] soundData = new byte[bytes];
		
		int i = 0;
		final int dataOffset = 0x18;
		for(int s = 0; s < secCount; s++) {
			byte[] secdat = datStr.nextSectorBytes();
			if(secdat == null) {
				return false;
			}
			for(int j = 0; j < bytesPerSec; j++) {
				soundData[i++] = secdat[dataOffset + j];
			}
		}
		
		aiff.setAifcRawSndData(soundData);
		FileBuffer ser = aiff.serializeAiff();
		ser.writeFile(outputPath);
		ser.dispose();
		
		return true;
	}

	public static byte[] bitReorder_str2smpl(byte[] data, int channel, int chCount) {
		//4 bit only
		int inBlocks = data.length >>> 7;
		int adpcmBlocks = (inBlocks << 3) / chCount;
		int outSize = adpcmBlocks << 4;
		byte[] out = new byte[outSize + 0x10]; //Add padding block.
		int opos = 0;
		int ipos = 0; //128-byte block start
		for(int i = 0; i < inBlocks; i++) {
			//i counts 128 byte in-blocks
			for(int j = 0; j < 8; j++) {
				if(chCount > 1) {
					if(chCount % j != chCount) {
						continue;
					}
				}
				
				int joff = j >>> 1; //byte within word
				int wordpos = ipos + 0x10; //Position of word in block
				out[opos++] = data[ipos + 4 + j]; //Control byte
				out[opos++] = (byte)PSXVAG.BLOCK_ATTR_1_SHOT;
				for(int k = 0; k < 28; k += 2) {
					int n1 = Byte.toUnsignedInt(data[wordpos + joff]);
					int n2 = Byte.toUnsignedInt(data[wordpos + 4 + joff]);
					
					int outb = 0;
					if((j & 0x1) != 0) {
						//High nibble
						outb = (n2 & 0xf0) | (n1 >>> 4);
					}
					else {
						//Low nibble
						outb = ((n2 << 4) & 0xf0) | (n1 & 0xf);
					}
					out[opos++] = (byte)outb;
					
					wordpos += 8;
				}
			}
			ipos += 128;
		}
		//Set end flag
		out[opos - 14] = (byte)PSXVAG.BLOCK_ATTR_1_SHOT_END;
		
		//Add one-shot padding block?
		out[opos++] = 0x00;
		out[opos++] = 0x07;
		for(int i = 0; i < 14; i++) out[opos++] = 0x77;
		
		return out;
	}
	
	public static byte[] bitReorder_smpl2str(byte[][] data) {
		//4 bit only
		int totalBytes = 0;
		for(int c = 0; c < data.length; c++) totalBytes += data[c].length;
		int adpcmBlocks = totalBytes >>> 4;
		int outBlocks = (adpcmBlocks + 7) >>> 3;
		int outSize = outBlocks << 7;
		byte[] out = new byte[outSize];
		int opos = 0;
		int ipos = 0;
		
		for(int i = 0; i < outBlocks; i++) {
			int c = 0;
			for(int j = 0; j < 8; j++) {
				if(data[c][ipos + 1] == 0x7) break; //Probably a filler end block.
				int joff = j >>> 1;
				for(int k = 0; k < 14; k++) {
					int b0 = Byte.toUnsignedInt(data[c][ipos + k + 2]);
					int n1 = b0 & 0xf;
					int n2 = b0 >>> 4;
					out[opos + j + 4] = data[c][ipos];
					if(j < 4) out[opos + j] = data[c][ipos];
					else out[opos + j + 8] = data[c][ipos];
					
					//k * 4 (word size) * 2 (two nybbles at a time)
					int woff = opos + 0x10 + (k << 3) + joff;
					if((j & 0x1) != 0) {
						//Encode high
						b0 = Byte.toUnsignedInt(out[woff]);
						int b1 = Byte.toUnsignedInt(out[woff + 4]);
						b0 |= (n1 << 4);
						b1 |= (n2 << 4);
						out[woff] = (byte)b0;
						out[woff+4] = (byte)b1;
					}
					else {
						//Encode low
						out[woff] = (byte)n1;
						out[woff+4] = (byte)n2;
					}
				}
				
				if(++c >= data.length) {
					c = 0;
					ipos += 0x10;
				}
			}
			opos += 128;
		}
						
		return out;
	}
	
}
