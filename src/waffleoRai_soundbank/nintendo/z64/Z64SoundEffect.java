package waffleoRai_soundbank.nintendo.z64;

import waffleoRai_Sound.nintendo.Z64WaveInfo;
import waffleoRai_soundbank.nintendo.z64.Z64Bank.Labelable;

public class Z64SoundEffect extends Labelable{

	private float tune = 1.0f;
	private Z64WaveInfo sample;
	protected int id;
	
	public float getTuning(){return tune;}
	public Z64WaveInfo getSample(){return sample;}
	
	public void setTuning(float val){tune = val;}
	public void setSample(Z64WaveInfo val){sample = val;}
	
	public boolean sfxEquals(Z64SoundEffect other){
		if(other == null) return false;
		if(other == this) return true;
		
		if(this.tune != other.tune) return false;
		
		//For now sample *refs* must be equal. Might change.
		if(this.sample != other.sample) return false;
		
		return true;
	}
	
}
