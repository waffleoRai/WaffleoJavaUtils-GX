package waffleoRai_SoundSynth.soundformats.game;

import java.util.LinkedList;
import java.util.List;

import waffleoRai_SoundSynth.AudioSampleStream;
import waffleoRai_SoundSynth.Oscillator;
import waffleoRai_SoundSynth.PanUtils;
import waffleoRai_SoundSynth.SynthSampleStream;
import waffleoRai_SoundSynth.general.UnbufferedWindowedSincInterpolator;
import waffleoRai_soundbank.adsr.Attack;
import waffleoRai_soundbank.adsr.Decay;
import waffleoRai_soundbank.adsr.EnvelopeStreamer;
import waffleoRai_soundbank.adsr.Release;
import waffleoRai_soundbank.adsr.Sustain;
import waffleoRai_soundbank.procyon.SWD;
import waffleoRai_soundbank.procyon.SWDOscillator;
import waffleoRai_soundbank.procyon.SWDProgram;
import waffleoRai_soundbank.procyon.SWDRegion;

//Pitch osc withheld for now - sound dreadful due to how interpolator functions

public class SWDSynthStream extends SynthSampleStream{

	/*----- Constants -----*/
	
	public static final int ADSR_PHASE_ATTACK = 0;
	public static final int ADSR_PHASE_HOLD = 1;
	public static final int ADSR_PHASE_DECAY = 2;
	public static final int ADSR_PHASE_SUSTAIN = 3;
	public static final int ADSR_PHASE_RELEASE = 4;
	
	public static final double PITCH_OSC_MAX = 100;
	
	/*----- InstanceVariables -----*/
	
	private int sampleRate;
	
	private int played; //In cents
	private int unitykey; //In cents
	private int tune; //In cents
	private int max_pb; //Max pb in cents
	private int pitchbend; //Pitch bend in cents
	
	private double vol_v; //Velocity
	private double vol_p; //Program
	private double vol_r; //Region
	private double vol_ch; //Channel
	private double volume; //Net Volume
	
	private List<Oscillator> osc_vol;
	private List<Oscillator> osc_pan;
	private List<Oscillator> osc_pitch;
	
	private UnbufferedWindowedSincInterpolator interpolator;
	
	private byte pan_p; //Program pan
	private byte pan_r; //Region pan
	private double ampratio_L;
	private double ampratio_R;
	
	private Attack att;
	private Decay dec;
	private Sustain sus;
	private Release rel;
	private int hold; //ms
	
	private int adsr_phase;
	private double env_lvl;
	private EnvelopeStreamer env_state;
	private int hold_remain; //Samples
	private double sl;
	private boolean rel_end;
	
	private int pitch_osc_ctr;
	
	//private boolean debugtag;
	
	/*----- Construction -----*/
	
	public SWDSynthStream(AudioSampleStream input, float outSampleRate) throws InterruptedException{
		super(input);
		osc_vol = new LinkedList<Oscillator>();
		osc_pan = new LinkedList<Oscillator>();
		osc_pitch = new LinkedList<Oscillator>();
		
		vol_ch = 1.0;
		
		interpolator = new UnbufferedWindowedSincInterpolator(source, 3);
		interpolator.setOutputSampleRate(outSampleRate);
		updatePitch(false);

		sampleRate = (int)outSampleRate;
	}
	
	public void setPlayData(byte key, byte velocity){
		double ratio = (double)velocity/127.0;
		vol_v = (ratio * ratio);
		//vol_v = ratio;
		volume = vol_ch * vol_p * vol_r * vol_v;
		
		played = 100 * (int)key;
		updatePitch(false);
	}
	
	public void setArticulationData(SWDRegion reg, SWDProgram prog){
		
		unitykey = (int)reg.getUnityKey() * 100;
		//tune = reg.getSRScaledCoarseTune() * 100;
		//tune += reg.getSRScaledFineTune();
		//System.err.println("tune = " + tune);
		max_pb = reg.getBendRange() * 100;
		updatePitch(false);
		
		double ratio = (double)prog.getVolume()/127.0;
		vol_p = ratio;
		ratio = (double)reg.getVolume()/127.0;
		vol_r = ratio;
		volume = vol_ch * vol_p * vol_r * vol_v;
		
		osc_vol.clear();
		osc_pan.clear();
		osc_pitch.clear();
		
		List<SWDOscillator> olist = prog.getOscillators();
		if(olist != null){
			for(SWDOscillator o : olist){
				if(o.getDestination() == SWD.OSC_DEST_VOLUME) osc_vol.add(o.spawnOscillator(sampleRate));
				else if(o.getDestination() == SWD.OSC_DEST_PAN) osc_pan.add(o.spawnOscillator(sampleRate));
				//else if(o.getDestination() == SWD.OSC_DEST_PITCH) osc_pitch.add(o.spawnOscillator(sampleRate));
			}
		}
		
		pan_p = prog.getPan();
		pan_r = reg.getPan();
		calculateAmpRatios(false);
		
		att = reg.getAttack();
		dec = reg.getDecay();
		sus = reg.getSustain();
		rel = reg.getRelease();
		hold = reg.getHold();
		sl = (double)sus.getLevel16() / (double)0x7FFF;
		
		enterAttack();
	}

	/*----- Getters -----*/
	
	public byte getKeyPlayed() {
		return (byte)(played/100);
	}
	
	public int getDelay() {
		return 0;
	}

	public Attack getAttack() {
		return att;
	}

	public int getHold() {
		return hold;
	}

	public Decay getDecay() {
		return dec;
	}

	@Override
	public Sustain getSustain() {
		return sus;
	}

	@Override
	public Release getRelease() {
		return rel;
	}
	
	public float getSampleRate(){
		return interpolator.getSampleRate();
	}
	
	public int getADSRPhase(){
		return this.adsr_phase;
	}
	
	/*----- Setters -----*/
	
	public void setLFO(Oscillator osc) {}

	public void removeLFO() {
		osc_vol.clear();
		osc_pan.clear();
		osc_pitch.clear();
	}
	
	/*----- Volume -----*/
	
	public void setChannelVolume(byte vol) {
		vol_ch = (double)vol/127.0;
		volume = vol_ch * vol_p * vol_r * vol_v;
	}
	
	private void updateNetVolume(boolean includeOsc){
		volume = vol_ch * vol_p * vol_r * vol_v;
		/*if(debug_tag >= 0){
			System.err.println("Volume: " + vol_ch + " * " + vol_p + " * " + vol_r + " * " + vol_v + " = " + volume);
		}*/
		if(includeOsc){
			for(Oscillator o : osc_vol){
				double amt = o.getNextValue() * volume;
				//System.err.println("amt = " + amt);
				volume += amt;
				if(volume < 0.0) volume = 0.0;
				else if (volume > 1.0) volume = 1.0;
			}
		}
	}
	
	/*----- Pan -----*/
	
	private void calculateAmpRatios(boolean includeOsc)
	{
		//F it I'm gonna be lazy
		int sum = (int)pan_r + (int)pan_p;
		int c = 2;
		
		if(includeOsc && osc_pan != null){
			for(Oscillator o : osc_pan){
				sum += (int)Math.round(127.0 * o.getNextValue());
				c++;
			}
		}
		
		byte netpan = (byte)(sum/c);
		double[] tonepan = PanUtils.getLRAmpRatios_Mono2Stereo(netpan);
		
			
		ampratio_L = tonepan[0];
		ampratio_R = tonepan[1];
	}
	
	/*----- Pitch -----*/
	
	private int calculateNetCentChange()
	{
		//Before LFO
		int diff = played - unitykey;
		diff += tune;
		diff += pitchbend;
		//System.err.println("Pitch Bend: " + pitchbend + " cents");
		return diff;
	}

	private void updatePitch(boolean includeOsc)
	{
		int cents = calculateNetCentChange();
		//System.err.println("Pitch Set: " + cents + " cents || root: " + this.unitykey + " | played: " + played + " tune: " + tune);
		//LFOs
		if(includeOsc){
			for(Oscillator o : osc_pitch){
				double val = o.getNextValue();
				cents += (int)Math.round(PITCH_OSC_MAX * val);
			}
		}
		interpolator.setPitchShift(cents);
	}
	
	public void setPitchWheelLevel(int lvl) {
		if(lvl == 0)
		{
			pitchbend = 0;
			updatePitch(false);
			return;
		}
		
		double maxcents = (double)max_pb;
		double wheel = (double)Math.abs(lvl);
		pitchbend = (int)Math.round((wheel/(double)0x7FFF) * maxcents);
		if(lvl < 0) pitchbend *= -1;
		//System.err.println("Pitch bend set: " + pitchbend + " cents");
		updatePitch(false);
	}
	
	public void setPitchBendDirect(int cents){
		//System.err.println("Pitch bend set: " + cents + " cents");
		int abcents = Math.abs(cents);
		if(abcents > this.max_pb){
			max_pb = abcents;
		}
		
		pitchbend = cents;
		updatePitch(false);
	}

	/*----- ADSR -----*/
	
	public void enterAttack(){
		adsr_phase = ADSR_PHASE_ATTACK;
		env_state = att.openStream(sampleRate);
		env_lvl = att.getStartLevel();
	}
	
	public void enterHold(){
		adsr_phase = ADSR_PHASE_HOLD;
		hold_remain = (int)Math.round((double)sampleRate/1000.0 * (double)hold);
		env_lvl = 1.0;
	}
	
	private void enterDecay(){
		adsr_phase = ADSR_PHASE_DECAY;
		env_state = dec.openStream((int)getSampleRate(), sl);
		env_lvl = 1.0;
	}
	
	private void enterSustain(){
		adsr_phase = ADSR_PHASE_SUSTAIN;
		env_state = sus.openStream((int)getSampleRate());
		env_lvl = sl;
	}
	
	private void enterRelease(){
		adsr_phase = ADSR_PHASE_RELEASE;
		env_state = rel.openStream((int)getSampleRate(), env_lvl);
	}
	
	private void advanceEnvelope(){
		// -> If in decay phase, don't forget to check envelope level to see if sustain time
		if(rel_end) return;
		switch(adsr_phase)
		{
		case ADSR_PHASE_ATTACK:
			//if((++phase_pos) >= adsr_val_tbl.length) enterDecay();
			env_lvl = env_state.getNextAmpRatio();
			if(env_state.done()){
				if(hold > 0) enterHold();
				else{
					if(sl < 1.0) enterDecay();
					else enterSustain();	
				}
			}
			break;
		case ADSR_PHASE_HOLD:
			if(hold_remain-- <= 0){
				if(sl < 1.0) enterDecay();
				else enterSustain();
			}
			break;
		case ADSR_PHASE_DECAY:
			env_lvl = env_state.getNextAmpRatio();
			if(env_state.done() || env_lvl <= sl) enterSustain();
			break;
		case ADSR_PHASE_SUSTAIN:
			env_lvl = env_state.getNextAmpRatio();
			//if(env_state.done()) sus_end = true;
			break;
		case ADSR_PHASE_RELEASE:
			if(env_state.done()) {
				rel_end = true; env_lvl = 0.0;
				//if(super.debug_tag >= 0){System.err.println("VoiceTag " + debug_tag + ": Release phase complete!");}
			}
			else{
				env_lvl = env_state.getNextAmpRatio();
			}
			break;
		}
	}
	
	public void releaseMe(){
		enterRelease();
	}
	
	public boolean releaseSamplesRemaining(){
		if(adsr_phase != ADSR_PHASE_RELEASE) return true;
		return !rel_end;
	}
	
	/*----- Control -----*/

	@Override
	public double[] getInternalPanAmpRatios() {
		double[] arr = new double[2];
		arr[0] = ampratio_L;
		arr[1] = ampratio_R;
		//System.err.println("Pan Ratios: " + arr[0] + " | " + arr[1]);
		return arr;
	}
	
	private int saturate(int in){
		if(in > 0x7FFF) return 0x7FFF;
		if(in < -0x7FFF) return -0x7FFF;
		return in;
	}
	
	@Override
	public int[] nextSample() throws InterruptedException {

		//Count channels, declare array
		int[] raw = null;
		int ccount = this.getChannelCount();
		
		//If end of release, return silence
		if(rel_end) return new int[ccount];

		//Get interpolated sample
		if(!osc_pitch.isEmpty()){
			if(pitch_osc_ctr++ < 10){
				for(Oscillator o : osc_pitch) o.getNextValue();
			}
			else{
				updatePitch(true);
				pitch_osc_ctr = 0;
			}
		}
		raw = interpolator.nextSample();
		if(raw == null) return new int[ccount];
		
		//Scale to volume
		int[] out = new int[ccount];
		if(!osc_vol.isEmpty()) updateNetVolume(true);
		for(int i = 0; i < ccount; i++)
		{
			double mult = (double)raw[i] * volume * env_lvl;
			out[i] = saturate((int)Math.round(mult));
		}
		
		advanceEnvelope();
		
		//Update Pan (If applicable)
		if(!osc_pan.isEmpty()) calculateAmpRatios(true);
		
		return out;
	}

	@Override
	public void close() {
		// TODO Auto-generated method stub
	}

	
}
