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
		return src.isDone();
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
	
	public BufferedImage getNextFrame() {
		if(nowsec == null && src.isDone()) return null;
		
		//Get header info on frame
		SectorHeader shead = readCurrentSectorHeader();
		BufferedImage img = new BufferedImage(shead.fwidth, shead.fheight, BufferedImage.TYPE_INT_ARGB);
		//System.err.println("XAVideoFrameStream.getNextFrame || --DEBUG-- CHECK 1");
		
		//Ready translator
		translator.setFrameQuantScale(shead.quant_scale);
		int remaining = shead.frame_bytes - 8; //Skip first two words.
		long spos = 0x40;
		//System.err.println("XAVideoFrameStream.getNextFrame || --DEBUG-- CHECK 2");
		
		while(remaining > 0){
			if(spos >= SECDATA_END){
				//Next sector.
				try {nowsec.dispose();} 
				catch (IOException e) {e.printStackTrace();}
				
				if(src.isDone()) break;
				nowsec = src.nextSectorBuffer(false);
				//shead = readCurrentSectorHeader();
				spos = 0x38;
			}
			
			int hw = Short.toUnsignedInt(nowsec.shortFromFile(spos)); 
			spos += 2; remaining -= 2;
			translator.feedHalfword(hw);
			
			//DEBUG
			/*try {
				if(os1 != null){
					os1.write(hw & 0xFF);
					os1.write((hw >>> 8) & 0xFF);
				}
			} 
			catch (IOException e) {e.printStackTrace();}*/
		}
		//System.err.println("XAVideoFrameStream.getNextFrame || --DEBUG-- CHECK 3");
		
		//Translate to MDEC codes
		int codes = translator.processInputBuffer();
		//System.err.println("XAVideoFrameStream.getNextFrame || --DEBUG-- MDEC Codes Generated: " + codes + "(0x" + Integer.toHexString(codes) + ")");
		translator.flushInput();
		
		/*if(os2 != null){
			try {
				while(translator.outputWordsAvailable() > 0){
					int w = translator.nextOutputWord();
					os2.write(w & 0xFF);
					os2.write((w >>> 8) & 0xFF);
					//os2.write((w >>> 16) & 0xFF);
					//os2.write((w >>> 24) & 0xFF);
				}
			} 
			catch (IOException e) {e.printStackTrace();}
		}*/
		
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
		int fno = shead.frame_no;
		if(framecount < fno) framecount = fno;
		while(shead.frame_no == fno){
			try {nowsec.dispose();} 
			catch (IOException e) {e.printStackTrace();}
			
			if(src.isDone()) break;
			nowsec = src.nextSectorBuffer(false);
			shead = readCurrentSectorHeader();
		}
		
		return img;
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
