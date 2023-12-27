package waffleoRai_soundbank.nintendo.z64;

import java.util.Random;

import waffleoRai_Sound.nintendo.Z64Sound;
import waffleoRai_Sound.nintendo.Z64Sound.Z64Tuning;
import waffleoRai_Sound.nintendo.Z64WaveInfo;
import waffleoRai_soundbank.nintendo.z64.Z64Bank.Labelable;

public class Z64Drum extends Labelable{
	private byte decay;
	private byte pan;
	
	//private float tune = 1.0f;
	//private float common_tune = 1.0f;
	private Z64Tuning common_tune;
	
	private Z64Envelope envelope;
	private Z64WaveInfo sample;
	
	protected int pool_id;
	
	public Z64Drum(){
		common_tune = new Z64Tuning();
		envelope = Z64Envelope.newDefaultEnvelope();
	}
	
	public byte getDecay(){return decay;}
	public byte getPan(){return pan;}
	public Z64Tuning getTuning(){return common_tune;}
	public Z64Envelope getEnvelope(){return envelope;}
	public Z64WaveInfo getSample(){return sample;}
	
	public byte getRootKey(){
		if(common_tune == null){
			common_tune = new Z64Tuning();
		}
		return common_tune.root_key;
	}
	
	public byte getFineTune(){
		if(common_tune == null){
			common_tune = new Z64Tuning();
		}
		return common_tune.fine_tune;
	}
	
	public void setDecay(byte val){decay = val;}
	public void setPan(byte val){pan = val;}
	public void setEnvelope(Z64Envelope val){envelope = val;}
	public void setSample(Z64WaveInfo val){sample = val;}
	public void setPoolID(int val){pool_id = val;}
	
	public void setTuning(Z64Tuning val){
		if(val == null) return;
		common_tune = val;
	}
	
	public void setRootKey(byte val){
		if(common_tune == null) common_tune = new Z64Tuning();
		common_tune.root_key = val;
	}
	
	public void setFineTune(byte val){
		if(common_tune == null) common_tune = new Z64Tuning();
		common_tune.fine_tune = val;
	}
	
	public void setPoolIDRandom(){
		Random r = new Random();
		pool_id = r.nextInt();
	}
	
	public static float commonToLocalTuning(int slot_idx, Z64Tuning value){
		/*int midi_note = slot_idx + Z64Sound.STDRANGE_BOTTOM;
		if(midi_note != Z64Sound.MIDDLE_C){
			int cents = 100 * (midi_note - Z64Sound.MIDDLE_C);
			float ratio = (float)(SynthMath.cents2FreqRatio(cents));
			return ratio/value;
		}
		else return value;*/
		int midi_note = slot_idx + Z64Sound.STDRANGE_BOTTOM;
		return Z64Sound.calculateTuning((byte)midi_note, value);
	}
	
	public static Z64Tuning localToCommonTuning(int slot_idx, float value){
		/*int midi_note = slot_idx + Z64Sound.STDRANGE_BOTTOM;
		if(midi_note != Z64Sound.MIDDLE_C){
			int cents = 100 * (midi_note - Z64Sound.MIDDLE_C);
			float ratio = (float)(SynthMath.cents2FreqRatio(cents));
			value *= ratio;
			
			//Round...
			value *= 100000f;
			value = (float)Math.round(value);
			value /= 100000f;
			
			return value;
		}
		else return value;*/
		int midi_note = slot_idx + Z64Sound.STDRANGE_BOTTOM;
		return Z64Sound.calculateTuning((byte)midi_note, value);
	}
	
	public boolean drumEquals(Z64Drum other){
		if(other == null) return false;
		if(other == this) return true;
		
		if(this.decay != other.decay) return false;
		if(this.pan != other.pan) return false;
		
		//Check tuning.
		if(this.common_tune == null) this.common_tune = new Z64Tuning();
		if(other.common_tune == null) other.common_tune = new Z64Tuning();
		if(!this.common_tune.tuningIsEquivalent(other.common_tune, 5)) return false;
		
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
	
	public Z64Drum copy(){
		Z64Drum copy = new Z64Drum();
		copy.decay = this.decay;
		copy.pan = this.pan;
		copy.setRootKey(this.getRootKey());
		copy.setFineTune(this.getFineTune());
		copy.envelope = this.envelope.copy();
		copy.sample = this.sample;
		copy.pool_id = this.pool_id;
		copy.name = this.name;
		return copy;
	}
	
}
