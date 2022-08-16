package waffleoRai_Sound.nintendo;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import waffleoRai_Files.FileClass;
import waffleoRai_Files.tree.FileNode;
import waffleoRai_Sound.ADPCMTable;
import waffleoRai_Sound.SampleChannel;
import waffleoRai_Sound.SampleChannel16;
import waffleoRai_Sound.SampleChannel4;
import waffleoRai_Sound.SampleChannel8;
import waffleoRai_Sound.Sound;
import waffleoRai_Sound.SoundFileDefinition;
import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;

public class CitrusCafeStream extends NinStream{
	
	//TODO Loop points? Do the later cafe versions use the "original loop" values?
	//TODO Getting a lot of empty data at the end. Is it reading last block correctly? Frame calculation off?
	
	public static final int TYPE_ID_CSTM = 0x4353544d;
	public static final int TYPE_ID_FSTM = 0x4653544d;
	public static final String MAGIC_CSTM = "CSTM";
	public static final String MAGIC_FSTM = "FSTM";
	
	public static final short STRUCTTYPE_TBL8 = 	0x0100;
	public static final short STRUCTTYPE_TBLREF = 	0x0101;
	public static final short STRUCTTYPE_DSPTBL = 	0x0300;
	public static final short STRUCTTYPE_IMATBL = 	0x0301;
	public static final short STRUCTTYPE_AUDIODAT = 0x1f00;
	public static final short STRUCTTYPE_INFOC = 	0x4000;
	public static final short STRUCTTYPE_SEEKC = 	0x4001;
	public static final short STRUCTTYPE_DATAC = 	0x4002;
	public static final short STRUCTTYPE_STRINFO = 	0x4100;
	public static final short STRUCTTYPE_TRKINFO = 	0x4101;
	public static final short STRUCTTYPE_CHNINFO = 	0x4102;
	
	protected static final int FILETYPE_CSTM = 1;
	protected static final int FILETYPE_FSTM = 2;
	protected static final int FILETYPE_FSTM_04 = 3;
	
	/*--- Internal Structs ---*/
	
	protected static class Ref{
		public short typeid;
		public int offset;
		
		public static Ref read(BufferReference ref){
			Ref r = new Ref();
			r.typeid = ref.nextShort();
			ref.add(2L); //Padding
			r.offset = ref.nextInt();
			return r;
		}
		
		public Ref copy(){
			Ref copy = new Ref();
			copy.typeid = this.typeid;
			copy.offset = this.offset;
			return copy;
		}
	}
	
	protected static class ChunkRef{
		public Ref offset;
		public int size;
		
		public static ChunkRef read(BufferReference ref){
			ChunkRef r = new ChunkRef();
			r.offset = Ref.read(ref);
			r.size = ref.nextInt();
			return r;
		}
	}
	
	protected static class StreamInfo{
		public int codec;
		public boolean loops;
		public int ch_count;
		public int reg_count;
		public int sample_rate;
		public int loop_start;
		public int frame_count;
		public int block_count;
		public int block_size;
		public int block_frames;
		public int lastblock_size;
		public int lastblock_frames;
		public int lastblock_paddedsize;
		public int seekdata_size;
		public int seek_interval_frames;
		public Ref sample_data_ref;
		public int reginfo_size;
		public Ref reginfo_ref;
		public int oloop_start;
		public int oloop_end;
		
		public StreamInfo copy(){
			StreamInfo copy = new StreamInfo();
			copy.codec = this.codec;
			copy.loops = this.loops;
			copy.ch_count = this.ch_count;
			copy.block_count = this.block_count;
			copy.block_frames = this.block_frames;
			copy.block_size = this.block_size;
			copy.loop_start = this.loop_start;
			copy.frame_count = this.frame_count;
			copy.sample_rate = this.sample_rate;
			copy.lastblock_frames = this.lastblock_frames;
			copy.lastblock_paddedsize = this.lastblock_paddedsize;
			copy.lastblock_size = this.lastblock_size;
			copy.seek_interval_frames = this.seek_interval_frames;
			copy.seekdata_size = this.seekdata_size;
			copy.reginfo_size = this.reginfo_size;
			copy.oloop_start = this.oloop_start;
			copy.oloop_end = this.oloop_end;
			copy.sample_data_ref = this.sample_data_ref.copy();
			copy.reginfo_ref = this.reginfo_ref.copy();
			
			return copy;
		}
		
		public static StreamInfo read(BufferReference ref, int filetype){
			StreamInfo sinfo = new StreamInfo();
			sinfo.codec = Byte.toUnsignedInt(ref.nextByte());
			sinfo.loops = ref.nextByte() != 0;
			sinfo.ch_count = Byte.toUnsignedInt(ref.nextByte());
			sinfo.reg_count = Byte.toUnsignedInt(ref.nextByte());
			
			sinfo.sample_rate = ref.nextInt();
			sinfo.loop_start = ref.nextInt();
			sinfo.frame_count = ref.nextInt();
			sinfo.block_count = ref.nextInt();
			sinfo.block_size = ref.nextInt();
			sinfo.block_frames = ref.nextInt();
			sinfo.lastblock_size = ref.nextInt();
			sinfo.lastblock_frames = ref.nextInt();
			sinfo.lastblock_paddedsize = ref.nextInt();
			sinfo.seekdata_size = ref.nextInt();
			sinfo.seek_interval_frames = ref.nextInt();
			
			sinfo.sample_data_ref = Ref.read(ref);
			
			if(filetype == FILETYPE_FSTM || filetype == FILETYPE_FSTM_04){
				sinfo.reginfo_size = (int)ref.nextShort();
				ref.add(2L);
				sinfo.reginfo_ref = Ref.read(ref);
			}
			
			if(filetype == FILETYPE_FSTM_04){
				sinfo.oloop_start = ref.nextInt();
				sinfo.oloop_end = ref.nextInt();
			}
			
			return sinfo;
		}
	}
	
	protected static class TrackInfo{
		public byte volume;
		public byte pan;
		public byte span;
		public int flags;
		public Ref chidx_tbl_ref;
		public byte[] chidx_tbl;
		
		public static TrackInfo read(BufferReference ref){
			TrackInfo tinfo = new TrackInfo();
			tinfo.volume = ref.nextByte();
			tinfo.pan = ref.nextByte();
			tinfo.span = ref.nextByte(); //surround pan?
			tinfo.flags = Byte.toUnsignedInt(ref.nextByte());
			tinfo.chidx_tbl_ref = Ref.read(ref);
			//DOES NOT READ CHIDX TABLE
			return tinfo;
		}
	}
	
	protected static class ChannelInfo{
		public Ref adpcm_tbl_ref;
		public DSPTable adpcm_tbl_dsp;
		public IMATable adpcm_tbl_ima;
	}
	
	protected static class DSPTable{
		public int[][] coeff;
		public int init_ps;
		public int init_s1;
		public int init_s2;
		public int loop_ps;
		public int loop_s1;
		public int loop_s2;
		
		public static DSPTable read(BufferReference ref){
			DSPTable tbl = new DSPTable();
			tbl.coeff = new int[8][2];
			for(int i = 0; i < 8; i++){
				for(int j = 0; j < 2; j++){
					tbl.coeff[i][j] = (int)ref.nextShort();
				}
			}
			tbl.init_ps = Short.toUnsignedInt(ref.nextShort());
			tbl.init_s1 = (int)ref.nextShort();
			tbl.init_s2 = (int)ref.nextShort();
			tbl.loop_ps = Short.toUnsignedInt(ref.nextShort());
			tbl.loop_s1 = (int)ref.nextShort();
			tbl.loop_s2 = (int)ref.nextShort();
			ref.add(2L);
			return tbl;
		}
	
		public ADPCMTable toADPCMTable(){
			NinDSPADPCMTable tbl = new NinDSPADPCMTable(8);
			for(int p = 0; p < 8; p++){
				for(int o = 0; o < 2; o++){
					tbl.setCoefficient(p, o, coeff[p][o]);
				}
			}
			tbl.setInitBacksample(init_s1, 0);
			tbl.setInitBacksample(init_s2, 1);
			tbl.setLoopBacksample(loop_s1, 0);
			tbl.setLoopBacksample(loop_s2, 1);
			tbl.setInitPredictor((init_ps >>> 4) & 0xf);
			tbl.setLoopPredictor((loop_ps >>> 4) & 0xf);
			tbl.setInitShift(init_ps & 0xf);
			tbl.setLoopShift(loop_ps & 0xf);
			
			return tbl;
		}
	}
	
	protected static class IMATable{
		public int init_val;
		public int init_idx;
		public int loop_val;
		public int loop_idx;
		
		public static IMATable read(BufferReference ref){
			IMATable tbl = new IMATable();
			tbl.init_val = (int)ref.nextShort();
			tbl.init_idx = (int)ref.nextByte();
			ref.increment();
			tbl.loop_val = (int)ref.nextShort();
			tbl.loop_idx = (int)ref.nextByte();
			ref.increment();
			return tbl;
		}
	}
	
	/*--- Instance Variables ---*/
	
	private StreamInfo stream_info;
	private short ver_major;
	private short ver_minor;
	
	/*--- Parse ---*/
	
	public static CitrusCafeStream readCFStream(FileBuffer data) throws UnsupportedFileTypeException, IOException{
		if(data == null) return null;
		boolean is_fstm = false;
		
		String magic = data.getASCII_string(0L, 4);
		if(magic.equals(MAGIC_CSTM)) is_fstm = false;
		else if(magic.equals(MAGIC_FSTM)) is_fstm = true;
		else throw new UnsupportedFileTypeException("CitrusCafeStream.readCFStream || Valid magic number not found!");
		
		//BOM (Actually need to check this since byte order is variable)
		data.setEndian(true);
		int bom = Short.toUnsignedInt(data.shortFromFile(0x04L));
		if(bom == 0xfffe) data.setEndian(false);
		else if (bom == 0xfeff) data.setEndian(true);
		else throw new UnsupportedFileTypeException("CitrusCafeStream.readCFStream || Byte-Order Mark not valid!");
		
		data.setCurrentPosition(8L);
		short ver_maj = data.nextShort();
		short ver_min = data.nextShort();
		data.skipBytes(4L); //File Size
		
		//Read Chunk Table
		int chunk_count = (int)data.nextShort();
		data.skipBytes(2L);
		ChunkRef[] chunk_tbl = new ChunkRef[chunk_count];
		BufferReference datref = data.getReferenceAt(data.getCurrentPosition());
		for(int c = 0; c < chunk_count; c++) chunk_tbl[c] = ChunkRef.read(datref);
		
		//Read INFO Chunk
		ChunkRef cref = null;
		for(int i = 0; i < chunk_count; i++){
			if(chunk_tbl[i].offset.typeid == STRUCTTYPE_INFOC){
				cref = chunk_tbl[i];
				break;
			}
		}
		if(cref == null) throw new UnsupportedFileTypeException("CitrusCafeStream.readCFStream || INFO chunk could not be found!");
		long cst = cref.offset.offset;
		long ced = cst + cref.size;
		FileBuffer cdat = data.createReadOnlyCopy(cst, ced);
		Ref[] infotbl = new Ref[3];
		datref = cdat.getReferenceAt(8L);
		for(int i = 0; i < 3; i++) infotbl[i] = Ref.read(datref);
		
		//Stream info
		if(infotbl[0].offset == -1) throw new UnsupportedFileTypeException("CitrusCafeStream.readCFStream || Stream Info block not found!");
		datref = cdat.getReferenceAt(8L + infotbl[0].offset);
		int ftype = is_fstm?FILETYPE_FSTM:FILETYPE_CSTM;
		if(is_fstm && ver_maj > 4) ftype = FILETYPE_FSTM_04;
		StreamInfo sinfo = StreamInfo.read(datref, ftype);
		
		//Track info
		Ref[] tinfo_tbl = null;
		if(infotbl[1].offset > 0){
			//We have a track table.
			datref = cdat.getReferenceAt(8L + infotbl[1].offset);
			int tcount = datref.nextInt();
			tinfo_tbl = new Ref[tcount];
			for(int i = 0; i < tcount; i++) tinfo_tbl[i] = Ref.read(datref);
		}
		TrackInfo[] tracks = null;
		if(tinfo_tbl != null){
			long tbase = infotbl[1].offset + 8;
			long tstart = -1L;
			tracks = new TrackInfo[tinfo_tbl.length];
			for(int i = 0; i < tinfo_tbl.length; i++){
				tstart = tbase + tinfo_tbl[i].offset;
				datref = cdat.getReferenceAt(tstart);
				tracks[i] = TrackInfo.read(datref);
				if(tracks[i].chidx_tbl_ref.offset > 0){
					datref = cdat.getReferenceAt(tstart + tracks[i].chidx_tbl_ref.offset);
					int ccount = datref.nextInt();
					tracks[i].chidx_tbl = new byte[ccount];
					for(int j = 0; j < ccount; j++) tracks[i].chidx_tbl[j] = datref.nextByte();
				}
			}
		}
		
		//Channel Info
		Ref[] cinfo_tbl = null;
		if(infotbl[2].offset > 0){
			datref = cdat.getReferenceAt(8L + infotbl[2].offset);
			int ccount = datref.nextInt();
			cinfo_tbl = new Ref[ccount];
			for(int i = 0; i < ccount; i++) cinfo_tbl[i] = Ref.read(datref);
		}
		ChannelInfo[] channels = null;
		if(cinfo_tbl != null){
			long cbase = infotbl[2].offset + 8;
			long cstart = -1L, tstart = -1L;
			channels = new ChannelInfo[cinfo_tbl.length];
			for(int i = 0; i < cinfo_tbl.length; i++){
				cstart = cbase + cinfo_tbl[i].offset;
				datref = cdat.getReferenceAt(cstart);
				channels[i] = new ChannelInfo();
				channels[i].adpcm_tbl_ref = Ref.read(datref);
				tstart = cstart + channels[i].adpcm_tbl_ref.offset;
				datref = cdat.getReferenceAt(tstart);
				if(sinfo.codec == NinSound.ENCODING_TYPE_DSP_ADPCM){
					channels[i].adpcm_tbl_dsp = DSPTable.read(datref);
				}
				else if(sinfo.codec == NinSound.ENCODING_TYPE_IMA_ADPCM){
					channels[i].adpcm_tbl_ima = IMATable.read(datref);
				}
			}
		}
		cdat.dispose();

		//Read SEEK Chunk (If applicable)
		short[][][] seek_table = null;
		cref = null;
		for(int i = 0; i < chunk_count; i++){
			if(chunk_tbl[i].offset.typeid == STRUCTTYPE_SEEKC){
				cref = chunk_tbl[i];
				break;
			}
		}
		if(cref != null && cref.offset.offset > 0){
			seek_table = new short[sinfo.block_count][sinfo.ch_count][2];
			data.setCurrentPosition(cref.offset.offset + 8L);
			for(int b = 0; b < sinfo.block_count; b++){
				for(int c = 0; c < sinfo.ch_count; c++){
					for(int i = 0; i < 2; i++){
						seek_table[b][c][i] = data.nextShort();
					}
				}
			}
		}
		
		//Initialize Stream object
		CitrusCafeStream stream = new CitrusCafeStream();
		stream.initialize(sinfo, tracks, channels);
		if(seek_table != null) stream.loadSeekTable(seek_table);
		stream.ver_major = ver_maj;
		stream.ver_minor = ver_min;
		
		//Read DATA Chunk
		cref = null;
		for(int i = 0; i < chunk_count; i++){
			if(chunk_tbl[i].offset.typeid == STRUCTTYPE_DATAC){
				cref = chunk_tbl[i];
				break;
			}
		}
		if(cref == null) throw new UnsupportedFileTypeException("CitrusCafeStream.readCFStream || DATA chunk could not be found!");
		long dstart = cref.offset.offset + 8L + sinfo.sample_data_ref.offset;
		datref = data.getReferenceAt(dstart);
		//System.err.println("DEBUG: dstart = 0x" + Long.toHexString(dstart));
		
		//Initialize channels.
		int lastfull = sinfo.block_count - 1;
		int fcount = (lastfull * sinfo.block_frames) + sinfo.lastblock_frames;
		stream.rawSamples = new SampleChannel[sinfo.ch_count];
		for(int c = 0; c < sinfo.ch_count; c++){
			switch(sinfo.codec){
			case NinSound.ENCODING_TYPE_PCM8:
				stream.rawSamples[c] = new SampleChannel8(fcount);
				break;
			case NinSound.ENCODING_TYPE_PCM16:
				stream.rawSamples[c] = new SampleChannel16(fcount);
				break;
			case NinSound.ENCODING_TYPE_DSP_ADPCM:
				int packet_count = (sinfo.block_size * lastfull) + sinfo.lastblock_size;
				packet_count >>>= 3;
				fcount = packet_count << 4; //P/S stored as samples.
			case NinSound.ENCODING_TYPE_IMA_ADPCM:
				//Fallthrough
				SampleChannel4 chan = new SampleChannel4(fcount);
				chan.setNybbleOrder(true); //TODO Nybble order fixed? System?
				stream.rawSamples[c] = chan;
				break;
			}
		}
		
		int exp_frames = sinfo.block_frames;
		for(int b = 0; b < lastfull; b++){
			for(int c = 0; c < sinfo.ch_count; c++){
				switch(sinfo.codec){
				case NinSound.ENCODING_TYPE_PCM8:
					for(int f = 0; f < exp_frames; f++) stream.rawSamples[c].addSample((int)datref.nextByte());
					break;
				case NinSound.ENCODING_TYPE_PCM16:
					for(int f = 0; f < exp_frames; f++) stream.rawSamples[c].addSample((int)datref.nextShort());
					break;
				case NinSound.ENCODING_TYPE_IMA_ADPCM:
					for(int f = 0; f < exp_frames; f+=2){
						int by = Byte.toUnsignedInt(datref.nextByte());
						int s2 = by & 0xF;
						int s1 = (by >>> 4) & 0xF;
						stream.rawSamples[c].addSample(s1);
						stream.rawSamples[c].addSample(s2);
					}
					break;
				case NinSound.ENCODING_TYPE_DSP_ADPCM:
					int f = 0;
					while(f < exp_frames){
						for(int j = 0; j < 8; j++){
							int by = Byte.toUnsignedInt(datref.nextByte());
							int s2 = by & 0xF;
							int s1 = (by >>> 4) & 0xF;
							stream.rawSamples[c].addSample(s1);
							stream.rawSamples[c].addSample(s2);
							//System.err.print(String.format("%x%x", s1, s2));
						}
						f += 14;
					}
					break;
				}
			}
			//System.err.println();
		}
		
		//Last block.
		int bcount = 0;
		exp_frames = sinfo.lastblock_frames;
		for(int c = 0; c < sinfo.ch_count; c++){
			switch(sinfo.codec){
			case NinSound.ENCODING_TYPE_PCM8:
				for(int f = 0; f < exp_frames; f++) {
					stream.rawSamples[c].addSample((int)datref.nextByte());
					bcount++;
				}
				break;
			case NinSound.ENCODING_TYPE_PCM16:
				for(int f = 0; f < exp_frames; f++){
					stream.rawSamples[c].addSample((int)datref.nextShort());
					bcount += 2;
				}
				break;
			case NinSound.ENCODING_TYPE_IMA_ADPCM:
				for(int f = 0; f < exp_frames; f+=2){
					int by = Byte.toUnsignedInt(datref.nextByte());
					int s2 = by & 0xF;
					int s1 = (by >>> 4) & 0xF;
					stream.rawSamples[c].addSample(s1);
					stream.rawSamples[c].addSample(s2);
					bcount++;
				}
				break;
			case NinSound.ENCODING_TYPE_DSP_ADPCM:
				int f = 0;
				while(f < exp_frames){
					for(int j = 0; j < 8; j++){
						int by = Byte.toUnsignedInt(datref.nextByte());
						int s2 = by & 0xF;
						int s1 = (by >>> 4) & 0xF;
						stream.rawSamples[c].addSample(s1);
						stream.rawSamples[c].addSample(s2);
						//System.err.print(String.format("%x%x", s1, s2));
					}
					f += 14;
					bcount += 8;
				}
				break;
			}
			datref.add(sinfo.lastblock_paddedsize - bcount);
			//System.err.println();
		}
		//System.err.println("Raw samples: " + (stream.rawSamples[0].countSamples() >> 4) * 14);
		
		return stream;
	}
	
	private void initialize(StreamInfo sinfo, TrackInfo[] tracks, ChannelInfo[] channels){
		//TODO Loop start is in frames (of audio only). If DSP, need to recalculate position
		//	since we store the P/S values in data.
		//TODO In fact, we should probably do that in the NinStreamableSound class...
		stream_info = sinfo;
		super.encodingType = sinfo.codec;
		super.loops = sinfo.loops;
		super.channelCount = sinfo.ch_count;
		super.sampleRate = sinfo.sample_rate;
		super.loopStart = sinfo.loop_start;
		super.block_size = sinfo.block_size;
		
		//Tracks
		super.active_track = 0;
		if(tracks != null && tracks.length > 0){
			super.tracks = new ArrayList<NinStream.Track>(tracks.length);
			for(int t = 0; t < tracks.length; t++){
				NinStream.Track track = new NinStream.Track();
				track.volume = tracks[t].volume;
				track.pan = tracks[t].pan;
				track.s_pan = tracks[t].span;
				track.flags = tracks[t].flags;
				if(tracks[t].chidx_tbl != null){
					track.chCount = tracks[t].chidx_tbl.length;
					if(track.chCount > 2){
						track.leftChannelID = tracks[t].chidx_tbl[0];
						track.rightChannelID = tracks[t].chidx_tbl[1];
						track.additional_ch = new ArrayList<Integer>(track.chCount);
						for(int c = 2; c < track.chCount; c++) track.additional_ch.add((int)tracks[t].chidx_tbl[c]);
					}
					else{
						track.leftChannelID = tracks[t].chidx_tbl[0];
						if(track.chCount > 1) track.rightChannelID = tracks[t].chidx_tbl[1];
					}
				}
				else{
					track.chCount = 1;
					track.leftChannelID = 0;
				}
				super.tracks.add(track);
			}
		}
		else{
			//No tracks. Shove all channels onto a track 0.
			super.tracks = new ArrayList<NinStream.Track>(1);
			NinStream.Track track = new NinStream.Track();
			track.volume = 127;
			track.pan = 0x40;
			track.s_pan = 0;
			track.flags = 0;
			track.chCount = sinfo.ch_count;
			if(track.chCount > 2){
				track.leftChannelID = 0;
				track.rightChannelID = 1;
				track.additional_ch = new ArrayList<Integer>(track.chCount);
				for(int c = 2; c < track.chCount; c++) track.additional_ch.add(c);
			}
			else{
				track.leftChannelID = 0;
				if(track.chCount > 1) track.rightChannelID = 1;
			}
			super.tracks.add(track);
		}
		
		//Channel ADPCM Info
		if(sinfo.codec == NinSound.ENCODING_TYPE_DSP_ADPCM){
			super.channel_adpcm_info = new ADPCMTable[channels.length];
			for(int c = 0; c < channels.length; c++){
				super.channel_adpcm_info[c] = channels[c].adpcm_tbl_dsp.toADPCMTable();
				//System.out.println("DEBUG: Channel " + c + " ADPCM Table --");
				//((NinDSPADPCMTable)super.channel_adpcm_info[c]).printInfo();
			}
		}
		else if(sinfo.codec == NinSound.ENCODING_TYPE_IMA_ADPCM){
			super.ima_table = new NinStream.IMATable(channels.length, sinfo.block_count, sinfo.block_frames);
			super.IMA_samp_init = new int[super.channelCount];
			super.IMA_idx_init = new int[super.channelCount];
			for(int c = 0; c < super.channelCount; c++){
				super.IMA_samp_init[c] = super.ima_table.init_samps[c][0] = channels[c].adpcm_tbl_ima.init_val;
				super.IMA_idx_init[c] = super.ima_table.init_idxs[c][0] = channels[c].adpcm_tbl_ima.init_idx;
			}
			super.initializeIMAStateTables();
		}
	}
	
	private void loadSeekTable(short[][][] table){
		if(table == null) return;
		if(encodingType == NinSound.ENCODING_TYPE_DSP_ADPCM){
			super.adpc_table = new NinStream.ADPCTable(channelCount, table.length);
			for(int b = 0; b < table.length; b++){
				for(int c = 0; c < channelCount; c++){
					adpc_table.old[c][b] = (int)table[b][c][0];
					adpc_table.older[c][b] = (int)table[b][c][1];
				}
			}
		}
		else if(encodingType == NinSound.ENCODING_TYPE_IMA_ADPCM){
			for(int b = 1; b < table.length; b++){
				for(int c = 0; c < channelCount; c++){
					ima_table.init_samps[c][b] = (int)table[b][c][0];
					ima_table.init_idxs[c][b] = (int)table[b][c][1];
				}
			}
		}
	}
	
	/*--- Getters ---*/
	
	public short getMajorVersion(){return ver_major;}
	public short getMinorVersion(){return ver_minor;}
	
	public Sound getSingleChannel(int channel) {
		CitrusCafeStream chcopy = new CitrusCafeStream();
		chcopy.stream_info = stream_info.copy();
		chcopy.stream_info.ch_count = 1;
		NinStream.copyChannel(this, chcopy, channel);
		return chcopy;
	}
	
	/*--- Definition ---*/
	
	private static CitrusStreamDef static_def_c;
	private static CafeStreamDef static_def_f;
	
	public static CitrusStreamDef getCitrusDefinition(){
		if(static_def_c == null) static_def_c = new CitrusStreamDef();
		return static_def_c;
	}
	
	public static CafeStreamDef getCafeDefinition(){
		if(static_def_f == null) static_def_f = new CafeStreamDef();
		return static_def_f;
	}
	
	private static abstract class CFStreamDef extends SoundFileDefinition{
		
		protected String str;
		protected String[] ext_list;
		
		protected CFStreamDef(String defostr, String[] extlist){
			str = defostr;
			ext_list = extlist;
		}
		
		public Collection<String> getExtensions() {
			List<String> list = new ArrayList<String>(ext_list.length);
			for(String s : ext_list)list.add(s);
			return list;
		}

		public String getDescription() {return str;}
		public FileClass getFileClass() {return FileClass.SOUND_STREAM;}
		public void setDescriptionString(String s) {str = s;}
		
		public Sound readSound(FileNode file) {
			try{
				FileBuffer data = file.loadDecompressedData();	
				return readCFStream(data);
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

	public static class CitrusStreamDef extends CFStreamDef{
		private static final String DEFO_ENG_STR = "Nintendo Citrus SDK Audio Stream";
		private static final String[] EXT_LIST = {"bcstm", "cstm", "CSTM"};
		
		public CitrusStreamDef(){
			super(DEFO_ENG_STR,EXT_LIST);
		}
		
		public int getTypeID() {return TYPE_ID_CSTM;}
		public String getDefaultExtension() {return "bcstm";}
	}
	
	public static class CafeStreamDef extends CFStreamDef{
		private static final String DEFO_ENG_STR = "Nintendo Cafe SDK Audio Stream";
		private static final String[] EXT_LIST = {"bfstm", "fstm", "FSTM"};
		
		public CafeStreamDef(){
			super(DEFO_ENG_STR,EXT_LIST);
		}
		
		public int getTypeID() {return TYPE_ID_FSTM;}
		public String getDefaultExtension() {return "bfstm";}
	}
	
	/*--- Converter ---*/
	
	private static CitrusStreamConverter static_conv_c;
	private static CafeStreamConverter static_conv_f;
	
	public static CitrusStreamConverter getCitrusConverter(){
		if(static_conv_c == null) static_conv_c = new CitrusStreamConverter();
		return static_conv_c;
	}
	
	public static CafeStreamConverter getCafeConverter(){
		if(static_conv_f == null) static_conv_f = new CafeStreamConverter();
		return static_conv_f;
	}
	
	public static class CitrusStreamConverter extends NinStreamConverter{

		public static final String DEFO_ENG_FROM = "Nintendo Citrus SDK Audio Stream (.bcstm)";
		
		protected CitrusStreamConverter() {super(DEFO_ENG_FROM);}

		protected NinStream readMe(FileBuffer data) {
			try{return CitrusCafeStream.readCFStream(data);}
			catch(Exception ex){
				ex.printStackTrace();
				return null;
			}
		}
	}
	
	public static class CafeStreamConverter extends NinStreamConverter{

		public static final String DEFO_ENG_FROM = "Nintendo Cafe SDK Audio Stream (.bfstm)";
		
		protected CafeStreamConverter() {super(DEFO_ENG_FROM);}

		protected NinStream readMe(FileBuffer data) {
			try{return CitrusCafeStream.readCFStream(data);}
			catch(Exception ex){
				ex.printStackTrace();
				return null;
			}
		}
	}

	/*--- Test ---*/
	
	public static void main(String[] args){
		if(args.length < 2){
			System.err.println("args: inpath outdir");
			System.exit(1);
		}
		
		String inpath = args[0];
		String outdir = args[1];
		
		try{
			//Try to get stream file name.
			String strname = "cfstream";
			int lastslash = inpath.lastIndexOf(File.separator);
			if(lastslash >= 0){
				strname = NinStreamConverter.removeExtensions(inpath.substring(lastslash+1));
			}
			
			//Read.
			CitrusCafeStream cfstr = CitrusCafeStream.readCFStream(FileBuffer.createBuffer(inpath));
			
			//Write.
			cfstr.dumpAllToWAV(outdir, strname);
			
		}
		catch(Exception ex){
			ex.printStackTrace();
			System.exit(1);
		}
		
	}
	
}
