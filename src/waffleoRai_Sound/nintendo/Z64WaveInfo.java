package waffleoRai_Sound.nintendo;

public class Z64WaveInfo {

	/*--- Instance Variables ---*/
	
	private int codec;
	private int medium;
	private int unk_flags;
	
	private byte u2;
	private int wave_size;
	private int wave_offset; //Relative to WARC start
	private int frame_count;
	
	private int loop_start;
	private int loop_end;
	private int loop_count;
	private short[] loop_state;
	
	private N64ADPCMTable adpcm_book;
	
	private boolean is_used = true;
	private boolean in_inst = false;
	private boolean in_perc = false;
	private boolean in_sfx = false;
	private boolean flag_sfx = false;
	private boolean flag_actor = false;
	private boolean flag_env = false;
	private boolean flag_music = false;
	
	private int bank_addr = -1;
	private float tuning = 1.0f;
	private String name;
	private int uid;
	private int pool_id = -1;
	
	/*--- Init ---*/
	
	public Z64WaveInfo(){
		setDefault();
	}
	
	public Z64WaveInfo(N64ADPCMTable adpcm_tbl, boolean smallSamps){
		setDefault();
		codec = smallSamps?Z64Sound.CODEC_SMALL_ADPCM:Z64Sound.CODEC_ADPCM;
		adpcm_book = adpcm_tbl;
	}
	
	private void setDefault(){
		codec = Z64Sound.CODEC_S16;
		medium = Z64Sound.MEDIUM_CART;
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
	public short[] getLoopState(){return loop_state;}
	
	public int getAddressInBank(){return bank_addr;}
	public float getTuning(){return tuning;}
	public String getName(){return name;}
	public int getUID(){return uid;}
	public int getFrameCount(){return frame_count;}
	public int getPoolID(){return pool_id;}
	
	public byte getFlagsField(){
		int flags = 0;
		flags |= (codec & 0xf) << 4;
		flags |= (medium & 0x3) << 2;
		flags |= unk_flags & 0x3;
		return (byte)flags;
	}
	
	public boolean usedFlag(){return is_used;}
	public boolean instUseFlag(){return in_inst;}
	public boolean percUseFlag(){return in_perc;}
	public boolean sfxUseFlag(){return in_sfx;}
	public boolean sfxFlag(){return flag_sfx;}
	public boolean actorFlag(){return flag_actor;}
	public boolean envFlag(){return flag_env;}
	public boolean musicFlag(){return flag_music;}
	
	/*--- Setters ---*/
	
	public void setCodec(int val){codec = val;}
	public void setMedium(int val){medium = val;}
	public void setUnkFlags(int val){unk_flags = val;}
	public void setU2(byte val){u2 = val;}
	public void setWaveOffset(int val){wave_offset = val;}
	public void setLoopStart(int val){loop_start = val;}
	public void setLoopEnd(int val){loop_end = val;}
	public void setLoopCount(int val){loop_count = val;}
	public void setADPCMBook(N64ADPCMTable book){adpcm_book = book;}
	public void setLoopState(short[] state){loop_state = state;}
	
	public void setPoolID(int val){pool_id = val;}
	
	public void setFrameCount(int val){
		frame_count = val;
		switch(codec){
		case Z64Sound.CODEC_ADPCM:
			wave_size = ((frame_count + 0xf) >>> 4) * 9;
			break;
		case Z64Sound.CODEC_SMALL_ADPCM:
			wave_size = ((frame_count + 0xf) >>> 4) * 5;
			break;
		case Z64Sound.CODEC_S8:
			wave_size = frame_count;
			break;
		default:
			wave_size = frame_count << 1;
			break;
		}
	}
	
	public void setWaveSize(int val){
		wave_size = val;
		switch(codec){
		case Z64Sound.CODEC_ADPCM:
			frame_count = (wave_size/9) << 4;
			break;
		case Z64Sound.CODEC_SMALL_ADPCM:
			frame_count = (wave_size/5) << 4;
			break;
		case Z64Sound.CODEC_S8:
			frame_count = wave_size;
			break;
		default:
			frame_count = wave_size >>> 1;
			break;
		}
	}
	
	public void setAddressInBank(int val){bank_addr = val;}
	public void setTuning(float val){tuning = val;}
	public void setName(String s){name = s;}
	public void setUID(int val){uid = val;}
	
	public void setFlagsField(byte val){
		int vali = Byte.toUnsignedInt(val);
		codec = vali >>> 4;
		medium = (vali >>> 2) & 0x3;
		unk_flags = vali & 0x3;
	}
	
	public void flagUsed(boolean b){is_used = b;}
	public void flagInstUse(boolean b){in_inst = b;}
	public void flagPercUse(boolean b){in_perc = b;}
	public void flagSFXUse(boolean b){in_sfx = b;}
	public void flagAsSFX(boolean b){flag_sfx = b;}
	public void flagAsActor(boolean b){flag_actor = b;}
	public void flagAsEnv(boolean b){flag_env = b;}
	public void flagAsMusic(boolean b){flag_music = b;}
	
	/*--- Compare ---*/
	
	public boolean wavesEqual(Z64WaveInfo other, boolean check_names){
		if(other == null) return false;
		if(other == this) return true;
		
		if(this.wave_size != other.wave_size) return false;
		if(this.wave_offset != other.wave_offset) return false;
		if(this.loop_end != other.loop_end) return false;
		if(this.loop_start != other.loop_start) return false;
		if(this.loop_count != other.loop_count) return false;
		
		if(this.codec != other.codec) return false;
		if(this.medium != other.medium) return false;
		
		//Check ADPCM book...
		if(this.adpcm_book == null){
			if(other.adpcm_book != null) return false;
		}
		else{
			if(other.adpcm_book == null) return false;
			if(!this.adpcm_book.bookEquals(other.adpcm_book)) return false;
		}
		
		if(check_names){
			if(name == null){
				if(other.name != null) return false;
			}
			return this.name.equals(other.name);
		}
		
		return true;
	}
	
	/*--- Misc ---*/
	
	public Z64WaveInfo copy(){
		Z64WaveInfo copy = new Z64WaveInfo();
		copy.adpcm_book = this.adpcm_book;
		copy.bank_addr = this.bank_addr;
		copy.codec = this.codec;
		copy.flag_actor = this.flag_actor;
		copy.flag_env = this.flag_env;
		copy.flag_music = this.flag_music;
		copy.flag_sfx = this.flag_sfx;
		copy.frame_count = this.frame_count;
		copy.in_inst = this.in_inst;
		copy.in_perc = this.in_perc;
		copy.in_sfx = this.in_sfx;
		copy.is_used = this.is_used;
		copy.loop_count = this.loop_count;
		copy.loop_end = this.loop_end;
		copy.loop_start = this.loop_start;
		copy.medium = this.medium;
		copy.name = this.name;
		copy.tuning = this.tuning;
		copy.u2 = this.u2;
		copy.uid = this.uid;
		copy.unk_flags = this.unk_flags;
		copy.wave_offset = this.wave_offset;
		copy.wave_size = this.wave_size;
		copy.setLoopState(this.loop_state);
		return copy;
	}
	
}
