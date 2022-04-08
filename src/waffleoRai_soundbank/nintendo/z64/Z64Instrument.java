package waffleoRai_soundbank.nintendo.z64;

import java.util.Random;

import waffleoRai_Sound.nintendo.Z64WaveInfo;
import waffleoRai_soundbank.nintendo.z64.Z64Bank.Labelable;

public class Z64Instrument extends Labelable{
	
	private byte note_lo = 0;
	private byte note_hi = 127;
	private byte decay;
	
	private float tune_lo = 1.0f;
	private float tune_med = 1.0f;
	private float tune_hi = 1.0f;
	
	private Z64Envelope envelope;
	private Z64WaveInfo wave_lo;
	private Z64WaveInfo wave_med;
	private Z64WaveInfo wave_hi;
	
	protected int id;
	
	public Z64Instrument(){}
	
	public byte getLowRangeTop(){return note_lo;}
	public byte getHighRangeBottom(){return note_hi;}
	public byte getDecay(){return decay;}
	
	public float getTuningLow(){return tune_lo;}
	public float getTuningMiddle(){return tune_med;}
	public float getTuningHigh(){return tune_hi;}
	
	public Z64Envelope getEnvelope(){return envelope;}
	public Z64WaveInfo getSampleLow(){return wave_lo;}
	public Z64WaveInfo getSampleMiddle(){return wave_med;}
	public Z64WaveInfo getSampleHigh(){return wave_hi;}
	
	public void setLowRangeTop(byte val){
		if(val < 0 || val > 127) return;
		note_lo = val;
	}
	
	public void setHighRangeBottom(byte val){
		if(val < 0 || val > 127) return;
		note_hi = val;
	}
	
	public void setDecay(byte val){decay = val;}
	public void setTuningLow(float val){tune_lo = val;}
	public void setTuningMiddle(float val){tune_med = val;}
	public void setTuningHigh(float val){tune_hi = val;}
	public void setEnvelope(Z64Envelope val){envelope = val;}
	public void setSampleLow(Z64WaveInfo val){wave_lo = val;}
	public void setSampleMiddle(Z64WaveInfo val){wave_med = val;}
	public void setSampleHigh(Z64WaveInfo val){wave_hi = val;}
	public void setID(int val){id = val;}
	
	public void setIDRandom(){
		Random r = new Random();
		id = r.nextInt();
	}
	
	public boolean instEquals(Z64Instrument other){
		if(other == null) return false;
		if(other == this) return true;
		
		if(this.note_lo != other.note_lo) return false;
		if(this.note_hi != other.note_hi) return false;
		if(this.decay != other.decay) return false;
		if(this.tune_lo != other.tune_lo) return false;
		if(this.tune_med != other.tune_med) return false;
		if(this.tune_hi != other.tune_hi) return false;
		
		if(this.envelope == null){
			if(other.envelope != null) return false;
		}
		else{
			if(!this.envelope.envEquals(other.envelope)) return false;
		}
		
		//For now sample *refs* must be equal. Might change.
		if(this.wave_lo != other.wave_lo) return false;
		if(this.wave_med != other.wave_med) return false;
		if(this.wave_hi != other.wave_hi) return false;
		
		return true;
	}
	
}
