package waffleoRai_Files.psx;

import java.io.IOException;

import waffleoRai_Files.tree.FileNode;
import waffleoRai_Sound.psx.PSXXAStream;
import waffleoRai_Utils.FileBuffer;

public class XAStreamFile {

	private int st_sec;
	private int ed_sec;
	
	private int[] v_channels; //Start sector for each channel
	private int[] a_channels; //Start sector for each channel
	private int[] d_channels; //Start sector for each channel
	
	//private FileBuffer src;
	private FileNode src;
	
	public XAStreamFile(int stsec, int edsec){
		st_sec = stsec;
		ed_sec = edsec;
		v_channels = new int[PSXXAStream.MAX_CHANNELS];
		a_channels = new int[PSXXAStream.MAX_CHANNELS];
		d_channels = new int[PSXXAStream.MAX_CHANNELS];
		
		for(int i = 0; i < PSXXAStream.MAX_CHANNELS; i++){
			v_channels[i] = -1;
			a_channels[i] = -1;
			d_channels[i] = -1;
		}
		
	}
	
	public void scanStartPoints(FileNode xasrc) throws IOException{
		src = xasrc;
		FileBuffer xastr = xasrc.loadData();
		scanStartPoints(xastr);
	}
	
	public void scanStartPoints(FileBuffer xastr){
		xastr.setEndian(false);

		long stoff = (long)st_sec * (long)PSXXAStream.SEC_SIZE;
		long cpos = stoff;
		int s = st_sec;
		do{
			int ch = (int)xastr.getByte(cpos + 0x11);
			int flags = Byte.toUnsignedInt(xastr.getByte(cpos + 0x12));
			
			flags &= 0x0e;
			switch(flags){
			case 0x8: 
				if(d_channels[ch] < 0) d_channels[ch] = s;
				break;
			case 0x4:
				if(a_channels[ch] < 0) a_channels[ch] = s;
				break;
			case 0x2: 
				if(v_channels[ch] < 0) v_channels[ch] = s;
				break;
			}
			
			cpos += PSXXAStream.SEC_SIZE;
		}while(++s < ed_sec);
		
	}
	
	public int getStartSector(){return st_sec;}
	public int getEndSector(){return ed_sec;}
	
	public FileNode getAsFileNode(String name){
		long stoff = (long)st_sec * (long)XADataStream.SEC_SIZE;
		long edoff = (long)ed_sec * (long)XADataStream.SEC_SIZE;
		
		FileNode fn = src.getSubFile(stoff, edoff-stoff);
		fn.setFileName(name);
		fn.generateGUID();
		
		return fn;
	}
	
	public boolean hasVideo(){
		for(int i = 0; i < v_channels.length; i++){
			if(v_channels[i] >= 0) return true;
		}
		return false;
	}
	
	public boolean hasAudio(){
		for(int i = 0; i < a_channels.length; i++){
			if(a_channels[i] >= 0) return true;
		}
		return false;
	}
	
	public boolean hasData(){
		for(int i = 0; i < d_channels.length; i++){
			if(d_channels[i] >= 0) return true;
		}
		return false;
	}
	
	public int countVideoChannels(){
		int c = 0;
		for(int i = 0; i < v_channels.length; i++){
			if(v_channels[i] >= 0) c++;
		}
		return c;	
	}
	
	public int countAudioChannels(){
		int c = 0;
		for(int i = 0; i < a_channels.length; i++){
			if(a_channels[i] >= 0) c++;
		}
		return c;	
	}
	
	public int countDataChannels(){
		int c = 0;
		for(int i = 0; i < d_channels.length; i++){
			if(d_channels[i] >= 0) c++;
		}
		return c;	
	}
	
	public boolean streamExists(int type, int channel){
		if(channel < 0 || channel >= PSXXAStream.MAX_CHANNELS) return false;
		
		switch(type){
		case PSXXAStream.STYPE_VIDEO: 
			return v_channels[channel] >= 0;
		case PSXXAStream.STYPE_AUDIO: 
			return a_channels[channel] >= 0;
		case PSXXAStream.STYPE_DATA: 
			return d_channels[channel] >= 0;
		}
		
		return false;
	}
	
	public FileNode getSource(){return src;}
	
	public void setSource(FileNode node){src = node;}
	
	public XADataStream openStream(int type, int channel) throws IOException{
		if(channel < 0 || channel >= PSXXAStream.MAX_CHANNELS) return null;
		
		int sec = 0;
		long start = 0;
		switch(type){
		case PSXXAStream.STYPE_VIDEO: 
			sec = v_channels[channel];
			if(sec < 0) return null;
			start = (long)sec * (long)PSXXAStream.SEC_SIZE;
			return new XADataStream(src.loadData(), start, type, channel);
		case PSXXAStream.STYPE_AUDIO: 
			sec = a_channels[channel];
			if(sec < 0) return null;
			//System.err.println("XAStreamFile.openStream || Start Sector: " + sec);
			start = (long)sec * (long)PSXXAStream.SEC_SIZE;
			return new XADataStream(src.loadData(), start, type, channel);
		case PSXXAStream.STYPE_DATA: 
			sec = d_channels[channel];
			if(sec < 0) return null;
			start = (long)sec * (long)PSXXAStream.SEC_SIZE;
			return new XADataStream(src.loadData(), start, type, channel);
		}
		
		return null;
	}
	
	public int streamSectorCount(int type, int channel){

		try {
			XADataStream str = openStream(type, channel);
			if(str == null) return -1;
			int ct = 0;
			while(str.skipSector()) ct++;
			return ct;
		} 
		catch (IOException e) {
			e.printStackTrace();
			return -1;
		}
		
	}
	
}
