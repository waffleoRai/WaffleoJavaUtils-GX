package waffleoRai_soundbank.nintendo.z64;

import waffleoRai_Sound.nintendo.Z64Sound;
import waffleoRai_Sound.nintendo.Z64WaveInfo;
import waffleoRai_SoundSynth.SynthMath;
import waffleoRai_soundbank.nintendo.z64.Z64Bank.Labelable;

public class Z64Drum extends Labelable{
	private byte decay;
	private byte pan;
	
	//private float tune = 1.0f;
	private float common_tune = 1.0f;
	
	private Z64Envelope envelope;
	private Z64WaveInfo sample;
	
	protected int id;
	
	public byte getDecay(){return decay;}
	public byte getPan(){return pan;}
	public float getTuning(){return common_tune;}
	public Z64Envelope getEnvelope(){return envelope;}
	public Z64WaveInfo getSample(){return sample;}
	
	public void setDecay(byte val){decay = val;}
	public void setPan(byte val){pan = val;}
	public void setTuning(float val){common_tune = val;}
	public void setEnvelope(Z64Envelope val){envelope = val;}
	public void setSample(Z64WaveInfo val){sample = val;}
	
	public static float commonToLocalTuning(int slot_idx, float value){
		int midi_note = slot_idx + Z64Sound.STDRANGE_BOTTOM;
		if(midi_note != Z64Sound.MIDDLE_C){
			int cents = 100 * (midi_note - Z64Sound.MIDDLE_C);
			float ratio = (float)(SynthMath.cents2FreqRatio(cents));
			return ratio/value;
		}
		else return value;
	}
	
	public static float localToCommonTuning(int slot_idx, float value){
		int midi_note = slot_idx + Z64Sound.STDRANGE_BOTTOM;
		if(midi_note != Z64Sound.MIDDLE_C){
			int cents = 100 * (midi_note - Z64Sound.MIDDLE_C);
			float ratio = (float)(SynthMath.cents2FreqRatio(cents));
			return value*ratio;
		}
		else return value;
	}
	
	public boolean drumEquals(Z64Drum other){
		if(other == null) return false;
		if(other == this) return true;
		
		if(this.decay != other.decay) return false;
		if(this.common_tune != other.common_tune) return false;
		if(this.pan != other.pan) return false;
		
		if(this.envelope == null){
			if(other.envelope != null) return false;
		}
		else{
			if(!this.envelope.envEquals(other.envelope)) return false;
		}
		
		//For now sample *refs* must be equal. Might change.
		if(this.sample != other.sample) return false;
		
		return true;
	}
	
}
