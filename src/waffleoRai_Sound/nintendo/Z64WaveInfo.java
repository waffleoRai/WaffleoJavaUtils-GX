package waffleoRai_Sound.nintendo;

public class Z64WaveInfo {

	/*--- Instance Variables ---*/
	
	private int codec;
	private int medium;
	private int unk_flags;
	
	private byte u2;
	private int wave_size;
	private int wave_offset; //Relative to WARC start
	
	private int loop_start;
	private int loop_end;
	private int loop_count;
	
	private N64ADPCMTable adpcm_book;
	
	/*--- Init ---*/
	
	public Z64WaveInfo(){
		setDefault();
	}
	
	public Z64WaveInfo(N64ADPCMTable adpcm_tbl, boolean smallSamps){
		setDefault();
		codec = smallSamps?Z64Wave.CODEC_SMALL_ADPCM:Z64Wave.CODEC_ADPCM;
		adpcm_book = adpcm_tbl;
	}
	
	private void setDefault(){
		codec = Z64Wave.CODEC_S16;
		medium = Z64Wave.MEDIUM_CART;
		wave_offset = -1;
		loop_start = -1;
		loop_end = -1;
		loop_count = 0;
	}
	
	/*--- Getters ---*/
	
	public int getCodec(){return codec;}
	public int getMedium(){return medium;}
	public int getUnkFlags(){return unk_flags;}
	public byte getU2(){return u2;}
	public int getWaveSize(){return wave_size;}
	public int getWaveOffset(){return wave_offset;}
	public int getLoopStart(){return loop_start;}
	public int getLoopEnd(){return loop_end;}
	public int getLoopCount(){return loop_count;}
	public N64ADPCMTable getADPCMBook(){return adpcm_book;}
	
	public byte getFlagsField(){
		int flags = 0;
		flags |= (codec & 0xf) << 4;
		flags |= (medium & 0x3) << 2;
		flags |= unk_flags & 0x3;
		return (byte)flags;
	}
	
	/*--- Setters ---*/
	
	public void setCodec(int val){codec = val;}
	public void setMedium(int val){medium = val;}
	public void setUnkFlags(int val){unk_flags = val;}
	public void setU2(byte val){u2 = val;}
	public void setWaveSize(int val){wave_size = val;}
	public void setWaveOffset(int val){wave_offset = val;}
	public void setLoopStart(int val){loop_start = val;}
	public void setLoopEnd(int val){loop_end = val;}
	public void setLoopCount(int val){loop_count = val;}
	public void setADPCMBook(N64ADPCMTable book){adpcm_book = book;}
	
	public void setFlagsField(byte val){
		int vali = Byte.toUnsignedInt(val);
		codec = vali >>> 4;
		medium = (vali >>> 2) & 0x3;
		unk_flags = vali & 0x3;
	}
	
}
