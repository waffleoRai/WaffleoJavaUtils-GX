package waffleoRai_Video.psx;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import waffleoRai_Files.psx.XADataStream;
import waffleoRai_Files.psx.XAStreamFile;
import waffleoRai_Files.tree.FileNode;
import waffleoRai_PSXMDEC.JavaMDECIO;
import waffleoRai_PSXMDEC.PSXMDEC;
import waffleoRai_Sound.psx.PSXXAStream;
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
	
	/*--- Initialization ---*/
	
	public XAVideoFrameStream(XADataStream source){
		//System.err.println("XAVideoFrameStream.<init> || Called!");
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

	public byte[] getNextFrameData(){
		//TODO
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
		final int BYTES_PER_MB = 384;
		int mb_rows = shead.fheight >>> 4;
		int mb_cols = shead.fwidth >>> 4;
		if(shead.fheight%16 != 0) mb_rows++;
		if(shead.fwidth%16 != 0) mb_cols++;
		int mbcount = mb_rows * mb_cols;
		int total_bytes = mbcount * BYTES_PER_MB;
		byte[] dest = new byte[total_bytes];
		int y_plane_sz = mbcount << 8; //* 256
		int c_plane_sz = y_plane_sz >>> 2; // /4
		
		int cb_off = y_plane_sz;
		int cr_off = cb_off + c_plane_sz;
		int w = mb_cols << 4;
		int wc = mb_cols << 3;
		
		int xy = 0; int xc = 0; int yy = 0; int yc = 0;
		for(int l = 0; l < mb_cols; l++){
			for(int r = 0; r < mb_rows; r++){
				//Per mb
				int yoff = (yy * w) + xy; //Offsets in dest array relative to plane
				int coff = (yc * wc) + xc;
				int x = 0; int y = 0;
				
				//Cr (16 words, 64 pixels)
				int base = cr_off + coff;
				for(int i = 0; i < 16; i++){
					int word = mdec.nextOutputWord();
					int idx = base + (y*wc) + x + 3;
					for(int xx = 3; xx >= 0; xx--){
						byte b = (byte)(word & 0xff);
						word = word >>> 8;
						dest[idx--] = b;
					}
					x += 4;
					if(x >= 8){y++; x = 0;}
				}
				
				//Cb (16 words, 64 pixels)
				x = 0; y = 0;
				base = cb_off + coff;
				for(int i = 0; i < 16; i++){
					int word = mdec.nextOutputWord();
					int idx = base + (y*wc) + x + 3;
					for(int xx = 3; xx >= 0; xx--){
						byte b = (byte)(word & 0xff);
						word = word >>> 8;
						dest[idx--] = b;
					}
					x += 4;
					if(x >= 8){y++; x = 0;}
				}
				
				//Y
				base = yoff;
				int yfact = w << 3;
				int[] boffs = {0, 8, yfact, yfact + 8};
				for(int j = 0; j < 4; j++){
					x = 0; y = 0;
					for(int i = 0; i < 16; i++){
						int word = mdec.nextOutputWord();
						int idx = base + boffs[j] + (y*w) + x + 3;
						for(int xx = 3; xx >= 0; xx--){
							byte b = (byte)(word & 0xff);
							word = word >>> 8;
							dest[idx--] = b;
						}
						x += 4;
						if(x >= 8){y++; x = 0;}
					}	
				}
				
				
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
		//String file1 = debug_dir + "\\xa_in.bin"; //For translator input
		//String file2 = debug_dir + "\\xa_out.bin"; //For translator output
		
		try{
			
			//PSXXAStream fullstr = PSXXAStream.readStream(FileBuffer.createBuffer(infile, false));
			PSXXAStream fullstr = PSXXAStream.readStream(FileNode.createFileNodeOf(infile));
			XAStreamFile strfile = fullstr.getFile(0); //Should only be one.

			//XADataStream datstr = strfile.openStream(PSXXAStream.STYPE_DATA, 1);
			XAVideoSource vidsrc = new XAVideoSource(strfile, 1);
			XADataStream datstr = vidsrc.openDataStream();
			
			XAVideoFrameStream vstr = new XAVideoFrameStream(datstr);
			//os1 = new BufferedOutputStream(new FileOutputStream(file1));
			//os2 = new BufferedOutputStream(new FileOutputStream(file2));
			
			//int frames = 15*60; //1 minute
			int frames = 520; //Good sampling
			String picdir = debug_dir + "\\video";
			
			for(int i = 0; i < frames; i++){
				//os1 = new BufferedOutputStream(new FileOutputStream(picdir + "\\in\\f_" + String.format("%03d", i) + ".bin"));
				//os2 = new BufferedOutputStream(new FileOutputStream(picdir + "\\out\\f_" + String.format("%03d", i) + ".bin"));
				
				String outpath = picdir + "\\f_" + String.format("%03d", i) + ".png";
				BufferedImage f = vstr.getNextFrame();
				ImageIO.write(f, "png", new File(outpath));
				//break;
				
				//os1.close();
				//os2.close();
			}
			
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
