package waffleoRai_SoundSynth.soundformats.game;

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
import waffleoRai_soundbank.nintendo.NinTone;

public class NinSynthSampleStream extends SynthSampleStream{
	
	/*----- Constants -----*/
	
	public static final int MODE_DOL = 1;
	public static final int MODE_NTR = 2;
	public static final int MODE_REV = 3;
	
	public static final int ADSR_PHASE_ATTACK = 0;
	public static final int ADSR_PHASE_DECAY = 1;
	public static final int ADSR_PHASE_SUSTAIN = 2;
	public static final int ADSR_PHASE_RELEASE = 3;
	
	/*----- InstanceVariables -----*/
	
	private int played; //In cents
	private int unitykey; //In cents
	private int tune; //In cents (this is a float on the Wii, so gotta work that out. Might be semis?)
	private int max_pb; //Max pb in cents
	private int pitchbend; //Pitch bend in cents
	
	private double volume; //Velocity & patch volume
	private double ch_vol;
	private byte pan;
	
	private Attack att;
	private Decay dec;
	private Sustain sus;
	private Release rel;
	private int hold;
	
	private boolean ignoreNoteOff;
	
	//Rev+ also has "Surround Pan" and "Alternate Assign"
	//	Ignoring for now
	
	//private UnbufferedWindowedSincInterpolator pitch_interpolator;
	//private UnbufferedWindowedSincInterpolator sr_interpolator;
	private UnbufferedWindowedSincInterpolator interpolator;
	
	private Oscillator lfo;
	
	private double ampratio_L;
	private double ampratio_R;
	
	private int adsr_phase;
	private double env_lvl;
	private EnvelopeStreamer env_state;
	private double sl;
	private boolean rel_end;
	
	private int sat_max;
	private int sat_min;
	
	/*----- Construction -----*/
	
	public NinSynthSampleStream(AudioSampleStream input, NinTone art, byte key, byte vel, float outSampleRate) throws InterruptedException{
		super(input);
		
		ch_vol =0.75;
		
		unitykey = art.getOriginalKey() * 100;
		tune = (art.getCoarseTune() * 100) + art.getFineTune();
		
		double vrat = (double)art.getVolume()/127.0;
		volume = (vrat * vrat);
		//volume = vrat;
		pan = (byte)art.getPan();
		
		att = art.getAttackData();
		dec = art.getDecayData();
		sus = art.getSustainData();
		rel = art.getReleaseData();
		hold = art.getHold();
		
		sl = (double)sus.getLevel16()/(double)0x7FFF;
		
		//System.err.println("Attack: " + att.getTime());
		//System.err.println("Decay: " + dec.getTime());
		//System.err.println("Sustain: 0x" + String.format("%04x", sus.getLevel16()));
		//System.err.println("Release: " + rel.getTime());
		
		ignoreNoteOff = art.getNoteOffType() == 1;
		//System.err.println("key = 0x" + String.format("%02x", key) + " (" + key + ")");
		played = 100 * (int)key;
		//System.err.println("played = " + played);
		
		vrat = (double)vel/127.0;
		volume *= (vrat * vrat);
		//volume *= vrat;
		//System.err.println("velocity = " + vel);
		//System.err.println("volume multiplier = " + volume);
		
		/*if(outSampleRate != input.getSampleRate())
		{
			sr_interpolator = new UnbufferedWindowedSincInterpolator(source, 3);
			sr_interpolator.setUseTargetSampleRate(true);
			sr_interpolator.setOutputSampleRate(outSampleRate);
		}
		
		if(sr_interpolator != null) pitch_interpolator = new UnbufferedWindowedSincInterpolator(sr_interpolator, 2);
		else pitch_interpolator = new UnbufferedWindowedSincInterpolator(source, 2);*/
		interpolator = new UnbufferedWindowedSincInterpolator(source, 2);
		interpolator.setOutputSampleRate(outSampleRate);
		updatePitch();
		
		calculateAmpRatios();
		enterAttack();
		
		int bd = source.getBitDepth();
		if(bd == 8){sat_min = (int)Byte.MIN_VALUE; sat_max = 0x7F;}
		else if(bd == 16){sat_min = (int)Short.MIN_VALUE; sat_max = 0x7FFF;}
		else if(bd == 24){sat_min = 0xFF800000; sat_max = 0x7FFFFF;}
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
	
	public float getSampleRate()
	{
		/*if(sr_interpolator != null) return sr_interpolator.getSampleRate();
		else return source.getSampleRate();*/
		return interpolator.getSampleRate();
	}
	
	public int getADSRPhase()
	{
		return this.adsr_phase;
	}
	
	public boolean ignoresNoteOff(){
		return this.ignoreNoteOff;
	}
	
	/*----- Setters -----*/
	
	/*----- Pitch Control -----*/
	
	private int calculateNetCentChange()
	{
		//Before LFO
		int diff = played - unitykey;
		diff += tune;
		diff += pitchbend;
		return diff;
	}

	private void updatePitch()
	{
		//Add LFO, if present
		//System.err.println("Key played: " + played + " | Unity Key: " + unitykey + " | Bend: " + pitchbend + " | Tune: " + tune);
		int cents = calculateNetCentChange();
		//System.err.println("Pitch Shift (cents): " + cents);
		if(lfo != null)
		{
			cents += (int)Math.round(lfo.getNextValue());
		}
		//pitch_interpolator.setPitchShift(cents);
		interpolator.setPitchShift(cents);
	}
	
	@Override
	public void setPitchWheelLevel(int lvl) {
		if(lvl == 0)
		{
			pitchbend = 0;
			updatePitch();
			return;
		}
		
		double maxcents = (double)max_pb;
		double wheel = (double)Math.abs(lvl);
		pitchbend = (int)Math.round((wheel/(double)0x7FFF) * maxcents);
		if(lvl < 0) pitchbend *= -1;
		updatePitch();
	}
	
	public void setPitchBendDirect(int cents){
		int abcents = Math.abs(cents);
		if(abcents > this.max_pb){
			max_pb = abcents;
		}
		
		pitchbend = cents;
		updatePitch();
	}
	
	@Override
	public void setLFO(Oscillator osc) {
		lfo = osc;
		updatePitch();
	}

	@Override
	public void removeLFO() {
		lfo = null;
		updatePitch();
	}
	
	/*----- Volume Control -----*/
	
	@Override
	public void setChannelVolume(byte vol) {
		ch_vol = (double)vol/127.0;
	}
	
	private void calculateAmpRatios()
	{
		if(source.getChannelCount() == 1)
		{
			double[] tonepan = PanUtils.getLRAmpRatios_Mono2Stereo(pan);
			
			ampratio_L = tonepan[0];
			ampratio_R = tonepan[1];
			//System.err.println("Pan Ratios: " + ampratio_L + " | " + ampratio_R + " || Pan: 0x" + String.format("%02x", pan));
		}
		else
		{
			double[][] tonepan = PanUtils.getLRAmpRatios_Stereo2Stereo(pan);
			
			ampratio_L = tonepan[0][0] + tonepan[1][0];
			ampratio_R = tonepan[0][1] + tonepan[1][1];
		}
		
	}
	
	/*----- ADSR -----*/
	
	private void enterAttack()
	{
		/*if(super.debug_tag >= 0) {System.err.println("VoiceTag " + debug_tag + ": Attack phase entered! ATT -- " + 
								att.getTime() + "ms" + " / " + att.getMode());}*/
		adsr_phase = ADSR_PHASE_ATTACK;
		env_state = att.openStream((int)getSampleRate());
		env_lvl = 0.0;
	}
	
	private void enterDecay()
	{
		/*if(super.debug_tag >= 0) {System.err.println("VoiceTag " + debug_tag + ": Decay phase entered! DEC -- " + 
									dec.getTime() + "ms" + " / " + dec.getMode());}*/
		adsr_phase = ADSR_PHASE_DECAY;
		env_state = dec.openStream((int)getSampleRate(), sl);
		env_lvl = 1.0;
	}
	
	private void enterSustain()
	{
		/*if(super.debug_tag >= 0) {System.err.println("VoiceTag " + debug_tag + ": Sustain phase entered! SUS -- "  
				sus.getTime() + "ms" + " / " + sus.getMode() + " / " + String.format("0x%04x", sus.getLevel16()));}*/
		adsr_phase = ADSR_PHASE_SUSTAIN;
		env_state = sus.openStream((int)getSampleRate());
		env_lvl = sl;
	}
	
	private void enterRelease()
	{
		/*if(super.debug_tag >= 0) {System.err.println("VoiceTag " + debug_tag + ": Release phase entered! REL -- " + 
				dec.getTime() + "ms" + " / " + dec.getMode());}*/
		adsr_phase = ADSR_PHASE_RELEASE;
		env_state = rel.openStream((int)getSampleRate(), env_lvl);
		//System.err.println("Phase = " + adsr_phase);
	}
	
	private void advanceEnvelope()
	{
		// -> If in decay phase, don't forget to check envelope level to see if sustain time
		if(rel_end) return;
		switch(adsr_phase)
		{
		case ADSR_PHASE_ATTACK:
			//if((++phase_pos) >= adsr_val_tbl.length) enterDecay();
			env_lvl = env_state.getNextAmpRatio();
			if(env_state.done()){
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
	
	public void releaseMe()
	{
		//System.err.println("Released!");
		enterRelease();
		//rel_end = true;
	}
	
	public boolean releaseSamplesRemaining()
	{
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
		if(in > sat_max) return sat_max;
		if(in < sat_min) return sat_min;
		return in;
	}
	
	@Override
	public int[] nextSample() throws InterruptedException {

		int[] raw = null;
		int ccount = this.getChannelCount();
		if(rel_end) return new int[ccount];
		//if(sr_interpolator != null) raw = sr_interpolator.nextSample();
		//else raw = pitch_interpolator.nextSample();
		//raw = pitch_interpolator.nextSample();
		raw = interpolator.nextSample();
		if(raw == null) return new int[ccount];
		
		//Volume
		//int ccount = this.getChannelCount();
		int[] out = new int[ccount];
		for(int i = 0; i < ccount; i++)
		{
			double mult = (double)raw[i] * volume * ch_vol * env_lvl;
			//double mult = (double)raw[i] * volume * ch_vol;
			out[i] = saturate((int)Math.round(mult));
		}
		
		advanceEnvelope();
		//System.err.println(String.format("Played: " + played + " || Voice Output: %04x", out[0]));
		return out;
	}

	@Override
	public void close() {
		// TODO Auto-generated method stub
		
	}

}
