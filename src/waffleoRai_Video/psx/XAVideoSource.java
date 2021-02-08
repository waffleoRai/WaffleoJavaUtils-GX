package waffleoRai_Video.psx;

import java.io.IOException;
import java.util.LinkedList;

import waffleoRai_Files.psx.XADataStream;
import waffleoRai_Files.psx.XAStreamFile;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Video.IVideoSource;
import waffleoRai_Video.VideoFrameStream;
import waffleoRai_Video.VideoIO;

public class XAVideoSource implements IVideoSource{
	
	/*--- Constants ---*/
	
	public static final int MILLIS_PER_VCYCLE = 200; //3 frames at 66 67 67 - 1/5 of a second
	
	public static final long FRAMENO_OFFSET = 0x20; //4 bytes
	
	/*--- Instance Variables ---*/
	
	private XAStreamFile str_source;
	private int v_ch; //Channel in stream source
	
	private int frames; //Total frames
	private int width;
	private int height;
	
	//Index for seek. Every 15 frames (1 sec)
	private int[] index; //Sector of first frame of each second.
	
	/*--- Initialization ---*/
	
	public XAVideoSource(XAStreamFile src, int src_channel) throws IOException{
		if(src == null) throw new NullPointerException("Cannot initialize with null stream source!");
		
		str_source = src;
		v_ch = src_channel;
		
		index();
	}
	
	private void index() throws IOException{
		//Also detects frame size.
		
		XADataStream datstr = this.openDataStream();
		if(datstr == null) throw new IOException("Channel " + v_ch + " could not be opened in source stream!");
		
		int s = 0; //Overall sector
		int c = 0; //Frame in second
		LinkedList<Integer> idx = new LinkedList<Integer>();
		
		//First sector for dimensions
		idx.add(0);
		FileBuffer sec = datstr.nextSectorBuffer(false);
		sec.setEndian(false);
		width = Short.toUnsignedInt(sec.shortFromFile(0x28));
		height = Short.toUnsignedInt(sec.shortFromFile(0x2a));
		sec.dispose();
		s++;
		
		while(!datstr.isDone()){
			//System.err.println("sector: " + s);
			sec = datstr.nextSectorBuffer(false);
			if(sec == null) break; //Done flag set after last sector...
			sec.setEndian(false);
			
			//See if it's a "chunk 0" - in which case, new frame.
			int cno = Short.toUnsignedInt(sec.shortFromFile(0x1c));
			if(cno == 0){
				if(++c >= 15){
					//New second
					idx.add(s);
					c = 0;
				}
			}
			
			sec.dispose();
			s++;
		}
		datstr.dispose();
		
		//Move index to array
		int seconds = idx.size();
		index = new int[seconds];
		for(int i = 0; i < seconds; i++) index[i] = idx.pop();
		
		//Determine total number of frames
		frames = seconds * 15;
		frames += c;
	}
	
	/*--- Getters ---*/
	
	public int getLength() {
		int val = frames/3; //Full groups
		int mod = frames%3;
		int millis = val*MILLIS_PER_VCYCLE;
		millis += mod*67;
		
		return millis;
	}

	public int getFrameCount() {return frames;}
	public int getHeight() {return height;}
	public int getWidth() {return width;}

	public double getFrameRate() {return 15.0;}
	public int millisPerFrame() {return 67;}
	public int getRawDataFormat(){return VideoIO.CLRFMT_YUV420;}
	public int getRawDataColorspace(){return VideoIO.CLRSPACE_YUV_SD;}
	
	/*--- Setters ---*/

	/*--- Stream ---*/
	
	public XADataStream openDataStream() throws IOException{
		//Check both video and data channels (video can be marked as "data")
		//(DataStream gives FULL sectors)
		
		XADataStream dat = null;
		dat = str_source.openStream(XADataStream.STYPE_VIDEO, v_ch);
		if(dat == null) dat = str_source.openStream(XADataStream.STYPE_DATA, v_ch);
		
		return dat;
	}

	public VideoFrameStream openStream() throws IOException {
		XADataStream datstr = openDataStream();
		return new XAVideoFrameStream(datstr);
	}

	public VideoFrameStream openStreamAt(int min, int sec, int frame) throws IOException{
		XADataStream datstr = openDataStream();
		int s = (min*60)+sec;
		
		//Fastforward datastream to this point.
		int sector = index[s];
		datstr.skipSectors(sector);
		
		//Fastforward to desired frame.
		int f = -1;
		for(int i = 0; i < frame; i++){
			
			//Go until frameno is not the same as f.
			FileBuffer peek = datstr.peekSector(false);
			if(peek == null) break;
			int fno = peek.intFromFile(FRAMENO_OFFSET);
			
			while(f == fno){
				peek.dispose();
				
				datstr.skipSectors(1);
				peek = datstr.peekSector(false);
				if(peek == null) break;
				fno = peek.intFromFile(FRAMENO_OFFSET);
			}
			if(peek != null) peek.dispose();
			
			f = fno;
		}
		
		return new XAVideoFrameStream(datstr);
	}

	public VideoFrameStream openStreamAt(int frame) throws IOException{
		//Determine minute and second...
		int v = frame;
		int f = v%15; v/=15;
		int s = v%60;
		int m = v/60;
		
		return openStreamAt(m,s,f);
	}

}
