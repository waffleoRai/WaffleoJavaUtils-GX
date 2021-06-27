package waffleoRai_Video.psx;

import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import waffleoRai_Files.psx.XADataStream;
import waffleoRai_Files.psx.XAStreamFile;
import waffleoRai_Files.tree.FileNode;
import waffleoRai_PSXMDEC.JavaMDECIO;
import waffleoRai_PSXMDEC.PSXMDEC;
import waffleoRai_Sound.psx.PSXXAStream;
import waffleoRai_Sound.psx.XAAudioStream;
import waffleoRai_SoundSynth.AudioSampleStream;
import waffleoRai_SoundSynth.soundformats.WAVWriter;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Video.VideoFrameStream;

public class XAVideoFrameStream implements VideoFrameStream{
	
	/*--- Constants ---*/
	
	public static final long SECDATA_END = 0x818;
	
	/*--- Instance Variables ---*/
	
	private XADataStream src;
	
	private FileBuffer nowsec; //So can check parameters ahead of time.
	
	private int width;
	private int height;
	
	//private boolean fc_set; //If manually set.
	private int framecount; //Can be set directly by source, otherwise just counts frames as it passes.
	
	//Decoders
	private XA2MDECTranslator translator;
	private JavaMDECIO mdec;
	
	private boolean dataout_16 = true; //Output data at 16 bits/pix, or saturate to 8
	
	/*--- Initialization ---*/
	
	public XAVideoFrameStream(XADataStream source){
		this(source, true);
	}
	
	public XAVideoFrameStream(XADataStream source, boolean data16){
		//System.err.println("XAVideoFrameStream.<init> || Called!");
		dataout_16 = data16;
		src = source;
		nowsec = src.nextSectorBuffer(false);
		
		//Get dimensions
		framecount = 1;
		width = Short.toUnsignedInt(nowsec.shortFromFile(0x28));
		height = Short.toUnsignedInt(nowsec.shortFromFile(0x2a));
		
		//Check version to generate translator
		int version = Short.toUnsignedInt(nowsec.shortFromFile(0x32));
		switch(version){
		case 2: 
			translator = new StdXAV2Translator();
			break;
		case 3: 
			translator = new StdXAV3Translator();
			break;
		}
		
		mdec = new JavaMDECIO();
		new PSXMDEC(mdec);
	}
	
	/*--- Getters ---*/
	
	public double getFrameRate() {return 15.0;}

	public int getFrameWidth() {return width;}
	public int getFrameHeight() {return height;}
	public int getFrameCount() {return framecount;}
	
	public boolean done() {
		return (nowsec == null && src.isDone());
	}

	public boolean rewindEnabled() {return true;}
	
	/*--- Setters ---*/
	
	public void setDataOutput(boolean bit16){dataout_16 = bit16;}
	
	public void setFrameCount(int count){
		//fc_set = true;
		framecount = count;
	}
	
	public void setXATranslator(XA2MDECTranslator obj){
		translator = obj;
	}
	
	public boolean rewind() {
		if(src == null) return false;
		src.reset();
		return true;
	}
	
	public void close() {
		try{nowsec.dispose();}
		catch(IOException x){x.printStackTrace();}
		src.dispose();
		
		nowsec = null;
		src = null;
	}
	
	/*--- Rendering ---*/
	
	public static class SectorHeader{
		
		public int chunk_idx;
		public int chunk_count;
		public int frame_no;
		public int frame_bytes;
		public int fwidth;
		public int fheight;
		public int code_count; //#MDEC codes
		public int quant_scale;
		public int fver; //Frame version
		
	}
	
	private SectorHeader readCurrentSectorHeader(){
		if(nowsec == null) return null;
		
		SectorHeader head = new SectorHeader();
		head.chunk_idx = Short.toUnsignedInt(nowsec.shortFromFile(0x1c));
		head.chunk_count = Short.toUnsignedInt(nowsec.shortFromFile(0x1e));
		head.frame_no = nowsec.intFromFile(0x20);
		head.frame_bytes = nowsec.intFromFile(0x24);
		head.fwidth = Short.toUnsignedInt(nowsec.shortFromFile(0x28));
		head.fheight = Short.toUnsignedInt(nowsec.shortFromFile(0x2a));
		head.code_count = Short.toUnsignedInt(nowsec.shortFromFile(0x2c));
		head.quant_scale = Short.toUnsignedInt(nowsec.shortFromFile(0x30));
		head.fver = Short.toUnsignedInt(nowsec.shortFromFile(0x32));
		
		return head;
	}
	
	private void nextSecToTranslator(SectorHeader shead){
		//Ready translator
		translator.setFrameQuantScale(shead.quant_scale);
		int remaining = shead.frame_bytes - 8; //Skip first two words.
		long spos = 0x40;
		
		while(remaining > 0){
			if(spos >= SECDATA_END){
				//Next sector.
				try {nowsec.dispose();} 
				catch (IOException e) {e.printStackTrace();}
				
				if(src.isDone()){
					System.err.println("XAVideoFrameStream.getNextFrame || --DEBUG-- Stream end reached - no new sectors");
					nowsec = null;
					break;
				}
				nowsec = src.nextSectorBuffer(false);
				spos = 0x38;
			}
			
			int hw = Short.toUnsignedInt(nowsec.shortFromFile(spos)); 
			spos += 2; remaining -= 2;
			translator.feedHalfword(hw);
		}
	}
	
	private void forwardStream(SectorHeader shead){
		int fno = shead.frame_no;
		if(framecount < fno) framecount = fno;
		while(shead.frame_no == fno){
			try {nowsec.dispose();} 
			catch (IOException e) {e.printStackTrace();}
			
			if(src.isDone()){
				System.err.println("XAVideoFrameStream.getNextFrame || --DEBUG-- Stream end reached - no new frames/sectors");
				nowsec = null;
				break;
			}
			nowsec = src.nextSectorBuffer(false);
			shead = readCurrentSectorHeader();
		}
	}
	
	public BufferedImage getNextFrame() {
		if(nowsec == null && src.isDone()){
			System.err.println("XAVideoFrameStream.getNextFrame || --DEBUG-- Stream end reached - no more frames to return");
			return null;
		}
		
		//Get header info on frame
		SectorHeader shead = readCurrentSectorHeader();
		BufferedImage img = new BufferedImage(shead.fwidth, shead.fheight, BufferedImage.TYPE_INT_ARGB);
		nextSecToTranslator(shead);
		
		//Translate to MDEC codes
		int codes = translator.processInputBuffer();
		//System.err.println("XAVideoFrameStream.getNextFrame || --DEBUG-- MDEC Codes Generated: " + codes + "(0x" + Integer.toHexString(codes) + ")");
		translator.flushInput();
		
		//Decode
		mdec.sendMacroblockInstruction(codes);
		while(translator.outputWordsAvailable() > 0){
			mdec.feedDataHalfword(translator.nextOutputWord());
		}
		mdec.executeNextInstruction();
		
		//Copy to output image
		int mb_rows = shead.fheight >>> 4;
		int mb_cols = shead.fwidth >>> 4;
		if(shead.fheight%16 != 0) mb_rows++;
		if(shead.fwidth%16 != 0) mb_cols++;
		
		int x0 = 0; int y0 = 0;
		for(int l = 0; l < mb_cols; l++){
			for(int r = 0; r < mb_rows; r++){
				for(int y = 0; y < 16; y++){
					int Y = y0+y;
					if(Y >= shead.fheight) break;
					for(int x = 0; x < 16; x++){
						int X = x0+x;
						if(X >= shead.fwidth) break;
						img.setRGB(x0+x, y0+y, mdec.nextOutputWord());
					}
				}
				y0+=16;
			}
			x0 += 16; y0 = 0;
		}
		
		//forward the stream to next frame
		forwardStream(shead);
		
		return img;
	}

	private void readCData(byte[] dest, int poff, int mboff, int wc){
		int x = 0; int y = 0;
		
		int base = poff + mboff;
		//1 byte/pix
		/*for(int i = 0; i < 16; i++){
			int word = mdec.nextOutputWord();
			int idx = base + (y*wc) + x + 3;
			for(int xx = 3; xx >= 0; xx--){
				byte b = (byte)(word & 0xff);
				word = word >>> 8;
				dest[idx--] = (byte)((int)b + 128);
			}
			x += 4;
			if(x >= 8){y++; x = 0;}
		}*/
		
		if(dataout_16){
			//2 byte (signed)/pix
			//Output is LE!
			for(int i = 0; i < 32; i++){
				int word = mdec.nextOutputWord();
				//(y*wc) + x is the plane relative pixel index of this word.
				int idx = ((y*wc) + x) << 1; //Bytes from plane start
				idx += base + 3;
				for(int xx = 1; xx >= 0; xx--){
					int hw = word & 0xffff;
					word = word >>> 16;
					dest[idx--] = (byte)((hw >>> 8) & 0xff);
					dest[idx--] = (byte)(hw & 0xff);
				}
				x += 2;
				if(x >= 8){y++; x = 0;}
			}
		}
		else{
			//2 byte signed in/ 1 byte unsigned out
			for(int i = 0; i < 32; i++){
				int word = mdec.nextOutputWord();
				//(y*wc) + x is the plane relative pixel index of this word.
				int idx = base + (y*wc) + x + 1; //Bytes from plane start
				for(int xx = 1; xx >= 0; xx--){
					int hw = word & 0xffff;
					word = word >>> 16;
					
					hw = hw << 16 >> 16; //Sign extend
					hw += 128; //Unsign
					hw = hw<0?0:hw; //Saturate
					hw = hw>255?255:hw;
					
					dest[idx--] = (byte)(hw);
				}
				x += 2;
				if(x >= 8){y++; x = 0;}
			}
		}

	}
	
	private void readYData(byte[] dest, int mboff, int w){
		int x = 0; int y = 0;
				
		int base = mboff;
		int yfact = w << 3;
		int[] boffs = {0, 8, yfact, yfact + 8};
		
		//1 byte/pix
		/*for(int j = 0; j < 4; j++){
			x = 0; y = 0;
			for(int i = 0; i < 16; i++){
				int word = mdec.nextOutputWord();
				int idx = base + boffs[j] + (y*w) + x + 3;
				for(int xx = 3; xx >= 0; xx--){
					byte b = (byte)(word & 0xff);
					word = word >>> 8;
					dest[idx--] = b;
					//dest[idx--] = (byte)((int)b + 128);
				}
				x += 4;
				if(x >= 8){y++; x = 0;}
			}	
		}*/
		
		if(dataout_16){
			//2 byte/pix (LE)
			for(int j = 0; j < 4; j++){
				x = 0; y = 0;
				for(int i = 0; i < 32; i++){
					int word = mdec.nextOutputWord();
					int idx = (boffs[j] + (y*w) + x) << 1;
					idx += base + 3;
					for(int xx = 1; xx >= 0; xx--){
						int hw = word & 0xffff;
						word = word >>> 16;
						dest[idx--] = (byte)((hw >>> 8) & 0xff);
						dest[idx--] = (byte)(hw & 0xff);
					}
					x += 2;
					if(x >= 8){y++; x = 0;}
				}	
			}	
		}
		else{
			//2 byte signed in/ 1 byte unsigned out
			for(int j = 0; j < 4; j++){
				x = 0; y = 0;
				for(int i = 0; i < 32; i++){
					int word = mdec.nextOutputWord();
					int idx = base + boffs[j] + (y*w) + x + 1;
					for(int xx = 1; xx >= 0; xx--){
						int hw = word & 0xffff;
						word = word >>> 16;
						
						hw = hw << 16 >> 16; //Sign extend
						hw += 128; //Unsign
						hw = hw<0?0:hw; //Saturate
						hw = hw>255?255:hw;
						
						dest[idx--] = (byte)(hw);
					}
					x += 2;
					if(x >= 8){y++; x = 0;}
				}	
			}
		}

	}
	
	public byte[] getNextFrameData(){
		//Need to modify MDEC to output the raw YCbCr data
		if(nowsec == null && src.isDone()){
			System.err.println("XAVideoFrameStream.getNextFrameData || --DEBUG-- Stream end reached - no more frames to return");
			return null;
		}
		
		//Get header info on frame
		SectorHeader shead = readCurrentSectorHeader();
		nextSecToTranslator(shead);
		
		//Translate to MDEC codes
		int codes = translator.processInputBuffer();
		translator.flushInput();
		
		//Decode
		mdec.setYUVOutput(true);
		mdec.sendMacroblockInstruction(codes);
		while(translator.outputWordsAvailable() > 0){
			mdec.feedDataHalfword(translator.nextOutputWord());
		}
		mdec.executeNextInstruction();
		
		//Rearrange data for output
		final int BYTES_PER_MB = dataout_16?768:384;
		int mb_rows = shead.fheight >>> 4;
		int mb_cols = shead.fwidth >>> 4;
		if(shead.fheight%16 != 0) mb_rows++;
		if(shead.fwidth%16 != 0) mb_cols++;
		int mbcount = mb_rows * mb_cols;
		int total_bytes = mbcount * BYTES_PER_MB;
		byte[] dest = new byte[total_bytes];
		int y_plane_sz = mbcount << 8; //* 256
		int c_plane_sz = y_plane_sz >>> 2; // /4
		
		int cb_off = dataout_16?(y_plane_sz << 1):y_plane_sz;
		int cr_off = dataout_16?(c_plane_sz << 1):c_plane_sz;
		cr_off += cb_off;
		int w = mb_cols << 4;
		int wc = mb_cols << 3;
		
		int xy = 0; int xc = 0; int yy = 0; int yc = 0;
		for(int l = 0; l < mb_cols; l++){
			for(int r = 0; r < mb_rows; r++){
				//Per mb
				int yoff = (yy * w) + xy; //Offsets in dest array relative to plane
				int coff = (yc * wc) + xc;
				if(dataout_16){yoff <<= 1; coff <<= 1;}
				
				//Cr (16(32) words, 64 pixels)
				readCData(dest, cr_off, coff, wc);
				
				//Cb (16(32) words, 64 pixels)
				readCData(dest, cb_off, coff, wc);
				
				//Y
				readYData(dest, yoff, w);
				
				yy+=16; yc+=8;
			}
			xy+=16; xc+=8;
			yy = 0; yc=0;
		}
		
		mdec.setYUVOutput(false);
		forwardStream(shead);
		return dest;
	}
	
	/*--- Debug ---*/
	
	//private static OutputStream os1;
	//private static OutputStream os2;
	
	public static void main(String[] args){
		String infile = "C:\\Users\\Blythe\\Documents\\Game Stuff\\PSX\\GameData\\MewMew\\MOVIE.BIN";
		String debug_dir = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\psxtest";
		String yuvout = debug_dir + "\\yuv_test.bin";
		String audout = debug_dir + "\\vid_test_aud.wav";
		//String file1 = debug_dir + "\\xa_in.bin"; //For translator input
		//String file2 = debug_dir + "\\xa_out.bin"; //For translator output
		
		try{
			int seconds =30;
			
			//PSXXAStream fullstr = PSXXAStream.readStream(FileBuffer.createBuffer(infile, false));
			PSXXAStream fullstr = PSXXAStream.readStream(FileNode.createFileNodeOf(infile));
			XAStreamFile strfile = fullstr.getFile(0); //Should only be one.
			
			/*XAAudioStream audstr = new XAAudioStream(strfile, 1);
			AudioSampleStream sstr = audstr.createSampleStream(false);
			int aframes = seconds * (int)sstr.getSampleRate();
			System.err.println("Audio sample rate: " + sstr.getSampleRate());
			WAVWriter writer = new WAVWriter(sstr, audout);
			writer.write(aframes);
			writer.complete();*/
			
			//System.exit(2);

			//XADataStream datstr = strfile.openStream(PSXXAStream.STYPE_DATA, 1);
			XAVideoSource vidsrc = new XAVideoSource(strfile, 1);
			vidsrc.setDataOutput(false);
			XADataStream datstr = vidsrc.openDataStream();
			
			XAVideoFrameStream vstr = new XAVideoFrameStream(datstr, false);
			//os1 = new BufferedOutputStream(new FileOutputStream(file1));
			//os2 = new BufferedOutputStream(new FileOutputStream(file2));
			
			int frames = 15*seconds; 
			//int frames = 520; //Good sampling
			String picdir = debug_dir + "\\video";
			System.err.println("Width: " + vstr.getFrameWidth());
			System.err.println("Height: "+ vstr.getFrameHeight());
			System.err.println("Frame count: "+ frames);
			
			System.exit(2);
			
			int f = 0;
			BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(yuvout));
			while(f < frames){
				byte[] dat = vstr.getNextFrameData();
				bos.write(dat);
				if (f >= 0 && f <= 300){
					int w = vstr.getFrameWidth();
					int h = vstr.getFrameHeight();
					int i = 0;
					
					String outpath = picdir + "\\Y\\f_" + String.format("%03d", f) + ".png";
					BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
					for(int y = 0; y < h; y++){
						for(int x = 0; x < w; x++){
							int yval = Byte.toUnsignedInt(dat[i++]);
							/*yval |= Byte.toUnsignedInt(dat[i++]) << 8;
							yval = yval << 16 >> 16; //sign extend
							yval += 128;
							yval = yval<0?0:yval;
							yval = yval>255?255:yval;*/
							yval &= 0xff;
							int argb = 0xff000000;
							argb |= yval; yval <<= 8;
							argb |= yval; yval <<= 8;
							argb |= yval;
							img.setRGB(x, y, argb);
						}
					}
					ImageIO.write(img, "png", new File(outpath));
					
					outpath = picdir + "\\Cb\\f_" + String.format("%03d", f) + ".png";
					img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
					for(int y = 0; y < h; y+=2){
						for(int x = 0; x < w; x+=2){
							int val = Byte.toUnsignedInt(dat[i++]);
							/*val |= Byte.toUnsignedInt(dat[i++]) << 8;
							val = val << 16 >> 16;
							val += 128;
							val = val<0?0:val;
							val = val>255?255:val;*/
							val &= 0xff;
							int argb = 0xff000000;
							argb |= val;
							img.setRGB(x, y, argb);
							img.setRGB(x+1, y, argb);
							img.setRGB(x, y+1, argb);
							img.setRGB(x+1, y+1, argb);
						}
					}
					ImageIO.write(img, "png", new File(outpath));
					
					outpath = picdir + "\\Cr\\f_" + String.format("%03d", f) + ".png";
					img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
					for(int y = 0; y < h; y+=2){
						for(int x = 0; x < w; x+=2){
							int val = Byte.toUnsignedInt(dat[i++]);
							/*val |= Byte.toUnsignedInt(dat[i++]) << 8;
							val = val << 16 >> 16;
							val += 128;
							val = val<0?0:val;
							val = val>255?255:val;*/
							val &= 0xff;
							int argb = 0xff000000;
							argb |= val << 16;
							img.setRGB(x, y, argb);
							img.setRGB(x+1, y, argb);
							img.setRGB(x, y+1, argb);
							img.setRGB(x+1, y+1, argb);
						}
					}
					ImageIO.write(img, "png", new File(outpath));
					
				}
				f++;
			}
			bos.close();
			
			
			//os1.close();
			//os2.close();
		}
		catch(Exception x){
			x.printStackTrace();
			
			/*try {
				os1.close();
				os2.close();
			} 
			catch (IOException e) {
				e.printStackTrace();
			}*/
			
			System.exit(1);
		}
		
	}

}
