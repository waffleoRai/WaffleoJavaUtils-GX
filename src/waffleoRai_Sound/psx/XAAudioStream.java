package waffleoRai_Sound.psx;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioFormat.Encoding;
import javax.sound.sampled.AudioInputStream;

import waffleoRai_Files.Converter;
import waffleoRai_Files.psx.XADataStream;
import waffleoRai_Files.psx.XAStreamFile;
import waffleoRai_Files.tree.FileNode;
import waffleoRai_Sound.BitDepth;
import waffleoRai_Sound.Sound;
import waffleoRai_SoundSynth.AudioSampleStream;
import waffleoRai_SoundSynth.soundformats.WAVWriter;
import waffleoRai_SoundSynth.soundformats.game.XAAudioSampleStream;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;

public class XAAudioStream implements Sound{
	
	/*--- Constants ---*/
	
	/*--- Instance Variables ---*/
	
	//Can do multiple channels in same file as "tracks", but only if specs match.
	
	private boolean isStereo;
	private int sampleRate;
	private int total_frames;
	private boolean bit8;
	
	private XAStreamFile src;
	
	private int active_track;
	private int[] track_channels;
	
	private boolean one_channel; //Filter to only one channel if stereo
	private boolean lr_select; //If false, only output left, if true right. Output both if one_channel not set.
	
	/*--- Initialization ---*/
	
	private XAAudioStream(){}
	
	public XAAudioStream(XAStreamFile source, int ch) throws IOException{
		src = source;
		LinkedList<Integer> chlist = new LinkedList<Integer>();
		chlist.add(ch);
		scan(chlist);
	}
	
	public XAAudioStream(XAStreamFile source, int[] ch) throws IOException{
		src = source;
		LinkedList<Integer> chlist = new LinkedList<Integer>();
		for(int i : ch) chlist.add(i);
		scan(chlist);
	}
	
	private void scan(LinkedList<Integer> chlist) throws IOException{
		//Gets parameters for stream
		//Including SR and frame count
		
		if(chlist.isEmpty()) return;
		Collections.sort(chlist); //Get them in order.
		LinkedList<Integer> keeplist = new LinkedList<Integer>();
		
		//Lowest # channel determines parameters. Those that don't match are tossed.
		int ch = chlist.pop();
		while(ch < 0) {
			if(chlist.isEmpty()) return;
			ch = chlist.pop();
		}
		
		//Do lowest channel.
		XADataStream str = null;
		while(str == null){
			str = src.openStream(PSXXAStream.STYPE_AUDIO, ch);
			if(str != null) break;
			if(chlist.isEmpty()) return;
			ch = chlist.pop();
		}
		
		keeplist.add(ch);
		FileBuffer sec0 = str.peekSector(false);
		//Check the last byte of the subheader
		int flags = Byte.toUnsignedInt(sec0.getByte(0x13));
		sec0.dispose();
		
		boolean srflag = (flags & 0x4) != 0;
		boolean stereoflag = (flags & 0x1) != 0;
		boolean bit8flag = (flags & 0x10) != 0;
		bit8 = bit8flag;
		
		if(srflag) sampleRate = 18900;
		else sampleRate = 37800;
		isStereo = stereoflag;
		
		int frames_per_sec = 4032; //Constant. Nybble samps per sector.
		if(bit8flag) frames_per_sec = frames_per_sec >>> 1;
		if(stereoflag) frames_per_sec = frames_per_sec >>> 1;
		
		//Get length.
		while(str.skipSector()) total_frames += frames_per_sec;
		//str.dispose();
		
		//Do other tracks (if applicable)
		while(!chlist.isEmpty()){
			ch = chlist.pop();
			str = src.openStream(PSXXAStream.STYPE_AUDIO, ch);
			if(str == null) continue;
			
			sec0 = str.peekSector(false);
			flags = Byte.toUnsignedInt(sec0.getByte(0x13));
			sec0.dispose();
			//str.dispose();
			
			//Examine format to determine whether to keep.
			boolean srflag_ = (flags & 0x4) != 0;
			boolean stereoflag_ = (flags & 0x1) != 0;
			boolean bit8flag_ = (flags & 0x10) != 0;
			
			if(srflag_ != srflag) continue;
			if(stereoflag_ != stereoflag) continue;
			if(bit8flag_ != bit8flag) continue;
			
			keeplist.add(ch);
		}
		
		//Copy track list
		int tkeep = keeplist.size();
		if(tkeep < 1) return;
		track_channels = new int[tkeep];
		
		for(int i = 0; i < tkeep; i++){
			track_channels[i] = keeplist.pop();
		}
		
	}
	
	/*--- Getters ---*/
	
	public AudioFormat getFormat() {
		int ch = isStereo?2:1;
		int fsize = ch*2;
		return new AudioFormat(Encoding.PCM_SIGNED, sampleRate, 16, 
				ch, fsize, sampleRate, false);
	}
	
	public int countTracks() {
		if(track_channels == null) return 0;
		return track_channels.length;
	}
	
	public int totalFrames() {return total_frames;}
	
	public int totalChannels() {return isStereo?2:1;}
	
	public BitDepth getBitDepth() {return BitDepth.SIXTEEN_BIT_SIGNED;}

	public int getSourceBitDepth(){return bit8?8:4;}
	
	public int getSampleRate() {return sampleRate;}

	public boolean loops() {return false;}

	public int getLoopFrame() {return 0;}

	public int getLoopEndFrame() {return total_frames;}

	public int getUnityNote() {return 60;}

	public int getFineTune() {return 0;}
	
	/*--- Setters ---*/
	
	public void setActiveTrack(int tidx) {
		if(tidx < 0 || track_channels == null || tidx >= track_channels.length){
			throw new IndexOutOfBoundsException("Track " + tidx + " does not exist!");}
		
		active_track = tidx;
	}
	
	/*--- Streams ---*/

	public XADataStream openDataStream(int track) throws IOException{
		if(src == null) return null;
		if(track < 0 || track_channels == null || track >= track_channels.length) return null;
		return src.openStream(PSXXAStream.STYPE_AUDIO, track_channels[track]);
	}
	
	public AudioInputStream getStream() {
		// TODO 
		//Er leave this null for now...
		//I'll write an adapter (AudioSampleStream -> InputStream) eventually.
		return null;
	}

	public AudioSampleStream createSampleStream() {
		try{
			XADataStream datstr = openDataStream(active_track);
			if(datstr == null) return null;
			XAAudioSampleStream astr = new XAAudioSampleStream(datstr);
			if(one_channel) astr.setOneChannel(lr_select);
			
			return astr;
		}
		catch(IOException x){
			x.printStackTrace();
		}
		
		return null;
	}

	public AudioSampleStream createSampleStream(boolean loop) {
		//It doesn't loop.
		return createSampleStream();
	}

	/*--- Interface ---*/

	public Sound getSingleChannel(int channel) {
		//Ugh why do I do this to myself.
		
		XAAudioStream copy = new XAAudioStream();
		copy.sampleRate = this.sampleRate;
		copy.total_frames = this.total_frames;
		copy.src = this.src;
		
		copy.track_channels = new int[]{track_channels[active_track]};
		
		if(isStereo){
			copy.one_channel = true;
			copy.lr_select = (channel != 0);
		}

		return copy;
	}

	public int[] getRawSamples(int channel) {
		//Oof. Why... do I have this?
		
		int opos = 0;
		int hdr_count = (total_frames/4032) << 3; //Overestimate
		int[] out = new int[total_frames+hdr_count];
		try{
			XADataStream str = openDataStream(active_track);
			if(str == null) return null;
			
			while(!str.isDone()){
				FileBuffer sec = str.nextSectorBuffer(false);
				int[][] adat = XAAudioSampleStream.rawSamplesFromSector(sec);
				for(int i = 0; i < adat[0].length; i++){
					out[opos++] = adat[channel][i];
				}
				sec.dispose();
			}
			str.dispose();
			return out;
		}
		catch(IOException x){
			x.printStackTrace();
			return null;
		}

	}

	public int[] getSamples_16Signed(int channel) {
		//We'll just have to run it. Fine. I really should trash these methods...
		
		int[] out = new int[total_frames];
		AudioSampleStream str = createSampleStream();
		for(int i = 0; i < total_frames; i++){
			if(str.done()) break;
			int[] s;
			try {
				s = str.nextSample();
				out[i] = s[channel];
			} 
			catch (InterruptedException e) {e.printStackTrace();}
		}
		str.close();
		
		return out;
	}

	public int[] getSamples_24Signed(int channel) {
		int[] out = new int[total_frames];
		AudioSampleStream str = createSampleStream();
		for(int i = 0; i < total_frames; i++){
			if(str.done()) break;
			int[] s;
			try {
				s = str.nextSample();
				out[i] = s[channel] << 8;
			} 
			catch (InterruptedException e) {e.printStackTrace();}
		}
		str.close();
		
		return out;
	}

	/*--- Converters ---*/
	
	private static XAAudio2WAVConverter wavconv;
	
	public static Converter getWavConv(){
		if(wavconv == null) wavconv = new XAAudio2WAVConverter();
		return wavconv;
	}
	
	public static class XAAudio2WAVConverter implements Converter{

		private String from_desc = "eXtended Architecture Audio Stream";
		private String to_desc = "RIFF Waveform File";
		
		public String getFromFormatDescription() {return from_desc;}
		public String getToFormatDescription() {return to_desc;}

		public void setFromFormatDescription(String s) {from_desc = s;}
		public void setToFormatDescription(String s) {to_desc = s;}

		public void writeAsTargetFormat(String inpath, String outpath)
				throws IOException, UnsupportedFileTypeException {
			FileNode dummy = new FileNode(null, inpath);
			dummy.setSourcePath(inpath);
			dummy.setLength(FileBuffer.fileSize(inpath));
			writeAsTargetFormat(dummy, outpath);
		}

		public void writeAsTargetFormat(FileBuffer input, String outpath)
				throws IOException, UnsupportedFileTypeException {
			//Does nothing at the moment.
			throw new UnsupportedFileTypeException("XAAudioStream.XAAudio2WAVConverter.writeAsTargetFormat "
					+ "|| FileBuffer not read by XA Stream parser at this time.");
		}

		public void writeAsTargetFormat(FileNode node, String outpath)
				throws IOException, UnsupportedFileTypeException {
			PSXXAStream str = PSXXAStream.readStream(node);
			
			//Outpath is the prefix...
			int fcount = str.countFiles();
			for(int f = 0; f < fcount; f++){
				XAStreamFile sfile = str.getFile(f);
				if(!sfile.hasAudio()) continue;
				int ach = sfile.countAudioChannels();
				int ch = 0;
				for(int j = 0; j < ach; j++){
					while(!sfile.streamExists(PSXXAStream.STYPE_AUDIO, ch)) ch++;
					String fulloutpath = outpath + "f" + String.format("%02d", f) + "_ch" + String.format("%02d", ch) + ".wav";
					XAAudioStream astr = new XAAudioStream(sfile, ch);
					AudioSampleStream sstr = astr.createSampleStream(false);
					WAVWriter writer = new WAVWriter(sstr, fulloutpath);
					try {
						writer.write(astr.totalFrames());
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					writer.complete();
				}
			}
			
		}

		public String changeExtension(String path) {
			if(path == null) return null;
			int lastdot = path.lastIndexOf('.');
			if(lastdot >= 0) path = path.substring(0, lastdot);
			
			return path + "_";
		}
		
	}

}
