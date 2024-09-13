package waffleoRai_Files.psx;

import java.io.IOException;

import waffleoRai_Sound.psx.PSXXAStream;
import waffleoRai_Utils.FileBuffer;

public class XADataStream implements IXAAudioDataSource{

	/*----- Constants -----*/
	
	public static final int SEC_SIZE = PSXXAStream.SEC_SIZE;
	
	public static final int STYPE_VIDEO = PSXXAStream.STYPE_VIDEO;
	public static final int STYPE_AUDIO = PSXXAStream.STYPE_AUDIO;
	public static final int STYPE_DATA = PSXXAStream.STYPE_DATA;
	
	/*----- Instance Variables -----*/
	
	private FileBuffer src;
	private long flen;
	private long stpos;
	
	private int ch_type;
	private int chn;
	
	private boolean done;
	//private int sec; //Current sector (so know where to jump to)
	//private int spos; //Position within sector (so know when to jump)
	private long cpos; //Current file position
	
	/*----- Construction -----*/
	
	public XADataStream(FileBuffer source, long startPos, int type, int channel){
		src = source;
		cpos = startPos;
		stpos = startPos;
		ch_type = type;
		chn = channel;
		
		flen = src.getFileSize();
		source.setEndian(false);
		
		//System.err.println("XADataStream.<init> || Start: 0x" + Long.toHexString(stpos));
		//sec = (int)(stpos/SEC_SIZE);
	}
	
	/*----- Data Filtering -----*/
	
	private int sectorMatches(long soff){
		//  0 - different channel
		//  1 - match, use this sector
		// -1 - EOF
		//System.err.println("Channel: " + ch_type + " - " + chn);
		if(soff >= flen) return -1;
		
		int chno = (int)src.getByte(soff + 0x11);
		int flags = Byte.toUnsignedInt(src.getByte(soff + 0x12));
		
		if((flags & 0x80) != 0) return -1;
		if(chno != chn) return 0;
		int t = flags & 0x0e;
		
		switch(t){
		case 2:
			if(ch_type == STYPE_VIDEO) return 1;
			return 0;
		case 4:
			if(ch_type == STYPE_AUDIO) return 1;
			return 0;
		case 8:
			if(ch_type == STYPE_DATA) return 1;
			return 0;
		}
		
		return 0;
	}
	
	private boolean nextSector(){
		if(done) return false;
		
		//Find the next sector
		int chk = 0;
		while(chk == 0){
			//Next sector...
			chk = sectorMatches(cpos);
			cpos += SEC_SIZE;
		}
		
		if(chk < 0){
			//EOF
			done = true;
			return false;
		}
		
		cpos -= SEC_SIZE;
		return true;
	}
	
	/*----- Getters -----*/
	
	public int getType(){return ch_type;}
	public int getChannel(){return chn;}
	
	public boolean isDone(){return done;}
	
	public FileBuffer peekSector(){
		return peekSector(false);
	}
	
	public FileBuffer peekSector(boolean copy){
		try {
			if(copy) return src.createCopy(cpos, cpos+SEC_SIZE);
			else return src.createReadOnlyCopy(cpos, cpos+SEC_SIZE);
		} 
		catch (IOException e) {
				e.printStackTrace();
		}
		
		return null;
	}
	
	public FileBuffer nextSectorBuffer(){
		return nextSectorBuffer(false);
	}
	
	public FileBuffer nextSectorBuffer(boolean copy){
		if(!nextSector()) return null;
		
		try {
			FileBuffer sec = null;
			if(copy){sec = src.createCopy(cpos, cpos+SEC_SIZE);}
			else{sec = src.createReadOnlyCopy(cpos, cpos+SEC_SIZE);}
			cpos += SEC_SIZE;
			return sec;
		} 
		catch (IOException e) {
				e.printStackTrace();
		}
		
		return null;
	}
	
	public byte[] nextSectorBytes(){
		if(done) return null;
		
		int chk = 0;
		while(chk == 0){
			//Next sector...
			chk = sectorMatches(cpos);
			cpos += SEC_SIZE;
		}
		
		if(chk < 0){
			//EOF
			done = true;
			return null;
		}
		
		return src.getBytes(cpos-SEC_SIZE, cpos);
	}
	
	/*----- Interface -----*/
	
	public boolean audioDataOnly() {return false;}
	public int getSampleRate() {return 0;}
	public int getBitDepth() {return 0;}
	public int getChannelCount() {return 0;}
	
	/*----- Setters -----*/
	
	public void reset(){
		cpos = stpos;
		done = false;
	}
	
	public int skipSectors(int secCount){
		if(done) return 0;
		int ct = 0;
		
		for(int s = 0; s < secCount; s++){	
			if(!nextSector()) return ct;
			ct++;
			cpos += SEC_SIZE;
		}
		
		return ct;
	}
	
	public boolean skipSector(){
		if(done) return false;
		if(!nextSector()) return false;
		cpos += SEC_SIZE;
		return true;
	}
	
	public void dispose(){
		try {
			src.dispose();
		} 
		catch (IOException e) {
			e.printStackTrace();
		}
		src = null;
	}
	
}
