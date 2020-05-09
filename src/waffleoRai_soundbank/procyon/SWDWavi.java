package waffleoRai_soundbank.procyon;

import java.io.BufferedWriter;
import java.io.IOException;

import waffleoRai_Sound.nintendo.NinSound;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;
import waffleoRai_soundbank.adsr.Attack;
import waffleoRai_soundbank.adsr.Decay;
import waffleoRai_soundbank.adsr.Release;
import waffleoRai_soundbank.adsr.Sustain;

public class SWDWavi {
	
	public static final int REC_SIZE = 64;
	
	private int index;
	
	private byte fineTune;
	private byte coarseTune;
	private byte unityKey;
	private byte volume;
	private byte pan;
	private byte keygroup;
	
	private byte encoding;
	private boolean loopflag;
	
	private int sampleRate;
	private int pcmd_off;
	private int loopSt;
	private int loopLen;
	
	private boolean envFlag;
	private byte envMult;
	
	private byte attVol;
	private byte att;
	private byte dec;
	private byte sus;
	private byte hold;
	private byte dec2;
	private byte rel;
	
	public SWDWavi(int idx){
		index = idx;
	}
	
	public static SWDWavi readFromSWD(FileBuffer data, long offset) throws UnsupportedFileTypeException{
		
		long cpos = offset;
		short marker = data.shortFromFile(cpos); cpos += 2;
		//System.err.println("Offset = 0x" + Long.toHexString(offset));
		//System.err.println("Marker = 0x" + String.format("%04x", marker));
		if(marker != (short)0xAA01) throw new FileBuffer.UnsupportedFileTypeException("WAVI entry marker not found! (0xAA01)");
		
		int idx = Short.toUnsignedInt(data.shortFromFile(cpos)); cpos+=2;
		SWDWavi wavi = new SWDWavi(idx);
		
		wavi.fineTune = data.getByte(cpos++);
		wavi.coarseTune = data.getByte(cpos++);
		wavi.unityKey = data.getByte(cpos++); cpos++; //Skip key transpose
		wavi.volume = data.getByte(cpos++);
		wavi.pan = data.getByte(cpos++);
		wavi.keygroup = data.getByte(cpos++);
		cpos += 8; //Skip 6 bytes unknown/padding
		wavi.encoding = data.getByte(cpos++); cpos++; //Unknown
		wavi.loopflag = (data.getByte(cpos++) != 0);
		cpos += 10; //Unknown
		wavi.sampleRate = data.intFromFile(cpos); cpos += 4;
		wavi.pcmd_off = data.intFromFile(cpos); cpos += 4;
		wavi.loopSt = data.intFromFile(cpos); cpos += 4;
		wavi.loopLen = data.intFromFile(cpos); cpos += 4;
		wavi.envFlag = (data.getByte(cpos++) != 0);
		wavi.envMult = data.getByte(cpos++);
		cpos +=6; //Skip 6 bytes unknown
		wavi.attVol = data.getByte(cpos++);
		wavi.att = data.getByte(cpos++);
		wavi.dec = data.getByte(cpos++);
		wavi.sus = data.getByte(cpos++);
		wavi.hold = data.getByte(cpos++);
		wavi.dec2 = data.getByte(cpos++);
		wavi.rel = data.getByte(cpos++);
		
		return wavi;
	}
	
	public int getIndex(){return index;}
	public byte getUnityKey(){return unityKey;}
	public int getCoarseTune(){return (int)coarseTune;}
	public int getFineTune(){return (int)fineTune;}
	public byte getVolume(){return volume;}
	public byte getPan(){return pan;}
	public int getKeyGroup(){return (int)keygroup;}
	public boolean getLoopFlag(){return loopflag;}
	public int getSampleRate(){return sampleRate;}
	public long getPCMDOffset(){return Integer.toUnsignedLong(pcmd_off);}
	public long getLoopStart(){return (loopSt << 2);}
	public long getLoopLength(){return (loopLen << 2);}
	public boolean getEnvelopeFlag(){return envFlag;}
	public byte getEnvelopeMultiplier(){return envMult;}
	public byte getAttackVolume(){return attVol;}
	public byte getRawAttack(){return att;}
	public byte getRawDecay(){return dec;}
	public byte getRawSustain(){return sus;}
	public byte getRawHold(){return hold;}
	public byte getRawDecay2(){return dec2;}
	public byte getRawRelease(){return rel;}
	
	private Attack a;
	private Decay d;
	private Sustain s;
	private Release r;
	private int h;
	
	public Attack getAttack(){
		if(a != null) return a;
		a = SWDADSR.getAttack(attVol, att, envMult);
		return a;
	}
	
	public Decay getDecay(){
		if(d != null) return d;
		d = SWDADSR.getDecay(dec, envMult);
		return d;
	}
	
	public int getHold(){
		if(h >= 0) return h;
		h = SWDADSR.getDuration_ms(hold, envMult);
		return h;
	}
	
	public Sustain getSustain(){
		if(s != null) return s;
		s = SWDADSR.getSustain(sus);
		return s;
	}
	
	public Release getRelease(){
		if(r != null) return r;
		r = SWDADSR.getRelease(rel, envMult);
		return r;
	}
	
	
	public int getEncoding(){
		switch(encoding){
		case 0: return NinSound.ENCODING_TYPE_PCM8;
		case 1: return NinSound.ENCODING_TYPE_PCM16;
		case 2: return NinSound.ENCODING_TYPE_IMA_ADPCM;
		}
		return -1;
	}
	
	public void debugPrint(int tabs){
		StringBuilder sb = new StringBuilder(16);
		for(int i = 0; i < tabs; i++) sb.append('\t');
		String tabstr = sb.toString();
		
		System.err.println(tabstr + "Internal Index: " + this.index);
		
		String str = "";
		switch(encoding){
		case 0: str = "8 bit PCM"; break;
		case 1: str = "16 bit PCM"; break;
		case 2: str = "IMA-ADPCM"; break;
		default: str = "[Unknown](" + encoding + ")";
		}
		
		System.err.println(tabstr + "Encoding: " + str);
		System.err.println(tabstr + "Sample Rate: " + sampleRate + " hz");
		System.err.println(tabstr + "PCMD Offset: 0x" + Integer.toHexString(pcmd_off));
		System.err.println(tabstr + "Loops?: " + loopflag);
		System.err.println(tabstr + "Loop Start: " + this.loopSt);
		System.err.println(tabstr + "Loop Length: " + this.loopLen);
		
		System.err.println(tabstr + "Root Key: " + this.unityKey);
		System.err.println(tabstr + "Coarse Tune: " + this.coarseTune + " semitones");
		System.err.println(tabstr + "Fine Tune: " + this.fineTune + " cents");
		System.err.println(tabstr + "Volume: " + this.volume);
		System.err.println(tabstr + "Pan: 0x" + String.format("%02x", pan));
		System.err.println(tabstr + "Keygroup: " + this.keygroup);
		System.err.println(tabstr + "Envelope Flag: " + this.envFlag);
		System.err.println(tabstr + "Envelope Multiplier: " + this.envMult);
		System.err.println(tabstr + "Attack Level: 0x" + String.format("%02x", this.attVol));
		System.err.println(tabstr + "Attack: 0x" + String.format("%02x", att) + " (" + getAttack().getTime() + " ms)");
		System.err.println(tabstr + "Hold: 0x" + String.format("%02x", hold) + " (" + getHold() + " ms)");
		System.err.println(tabstr + "Decay: 0x" + String.format("%02x", dec) + " (" + getDecay().getTime() + " ms)");
		System.err.println(tabstr + "Sustain: 0x" + String.format("%02x", this.sus));
		System.err.println(tabstr + "Release: 0x" + String.format("%02x", rel) + " (" + getRelease().getTime() + " ms)");
		System.err.println(tabstr + "Archdecay: 0x" + String.format("%02x", this.dec2));
	}
	
	public void debugPrintRecord(BufferedWriter out) throws IOException{
		out.write(this.index + "\t");
		
		switch(encoding){
		case 0: out.write("8 bit PCM\t"); break;
		case 1: out.write("16 bit PCM\t");break;
		case 2: out.write("IMA-ADPCM\t"); break;
		default: out.write("[Unknown]("+ encoding + ")\t"); break;
		}
		
		out.write(sampleRate + "\t");
		out.write("0x" + Long.toHexString(pcmd_off) + "\t");
		if(this.loopflag) out.write("Y\t");
		else out.write("N\t");
		out.write(this.loopSt + "\t");
		out.write(this.loopLen + "\t");
		
		out.write(this.unityKey + "\t");
		int tune = (Byte.toUnsignedInt(fineTune) | (Byte.toUnsignedInt(coarseTune) << 8));
		out.write(String.format("0x%04x", tune) + "\t");
		out.write((short)tune + "\t");
		
		out.write(volume + "\t");
		out.write(String.format("0x%02x", pan) + "\t");
		out.write(keygroup + "\t");
		
		if(this.envFlag) out.write("Y\t");
		else out.write("N\t");
		out.write(this.envMult + "\t");
		out.write(String.format("0x%02x", this.attVol) + "\t");
		out.write(String.format("0x%02x", this.att) + "\t");
		out.write(String.format("0x%02x", this.hold) + "\t");
		out.write(String.format("0x%02x", this.dec) + "\t");
		out.write(String.format("0x%02x", this.sus) + "\t");
		out.write(String.format("0x%02x", this.rel) + "\t");
		out.write(String.format("0x%02x", this.dec2));
	}

	public void printPCMDPos(){
		//Calculate end
		long edpos = pcmd_off + (loopSt << 2) + (loopLen << 2);
		System.err.println(index + "\t" + pcmd_off + "\t0x" + Long.toHexString(pcmd_off) + "\t0x" + Long.toHexString(edpos));
	}
	
}
