package waffleoRai_SoundSynth.soundformats.game;

import waffleoRai_SoundSynth.AudioSampleStream;
import waffleoRai_SoundSynth.Oscillator;
import waffleoRai_SoundSynth.PanUtils;
import waffleoRai_SoundSynth.SynthSampleStream;
import waffleoRai_SoundSynth.general.Amp;
import waffleoRai_SoundSynth.general.UnbufferedWindowedSincInterpolator;
import waffleoRai_soundbank.adsr.Attack;
import waffleoRai_soundbank.adsr.Decay;
import waffleoRai_soundbank.adsr.EnvelopeStreamer;
import waffleoRai_soundbank.adsr.Release;
import waffleoRai_soundbank.adsr.Sustain;
import waffleoRai_soundbank.vab.PSXVAB;
import waffleoRai_soundbank.vab.PSXVAB.Program;
import waffleoRai_soundbank.vab.PSXVAB.Tone;

public class VABSynthSampleStream extends SynthSampleStream{

	/* ----- Constants ----- */
	
	public static final int ADSR_PHASE_ATTACK = 0;
	public static final int ADSR_PHASE_DECAY = 1;
	public static final int ADSR_PHASE_SUSTAIN = 2;
	public static final int ADSR_PHASE_RELEASE = 3;
	
	/* ----- Instance Variables ----- */
	
	private boolean closed;
	
	//Pipe
	//private AudioSampleStream source;
	
	private UnbufferedWindowedSincInterpolator pitch_interpolator;
	private Amp volfilter;
	private UnbufferedWindowedSincInterpolator sr_interpolator;
	
	//Settings
	private int unity; //Unity key in cents
	private int finetune;
	private int playkey; //Scaled to cents
	private int pitchbend_range; //Max in cents
	private int pitchbend_cents;
	private Oscillator lfo;
	
	private double vol_channel;
	private double vol_net_prog; //Bank, Program, Tone
	private double vol_vel;
	
	//private int pan_bank;
	//private int pan_program;
	//private int pan_tone;
	private double ampratio_L;
	private double ampratio_R;
	
	private boolean reverb;
	
	//Not doing portamento for now
	
	//ADSR State
	private Attack att;
	private Decay dec;
	private Sustain sus;
	private Release rel;
	
	private int adsr_phase;
	private double env_lvl;
	private EnvelopeStreamer env_state;
	private double sl; //16-bit sustain level
	//private boolean sus_end; //Sustain is ramped, and it has reached end of ramp.
	private boolean rel_end; //Flag end of release to return only 0s
	
	/* ----- Construction ----- */
	
	public VABSynthSampleStream(AudioSampleStream src, PSXVAB bank, Program prog, Tone tone, byte key, byte vel, float outSampleRate) throws InterruptedException
	{
		//TODO
		//source = src;
		super(src);
		//Program p = bank.getProgram(progIdx);
		//Tone t = p.getTone(toneIdx);
		
		final double maxvol = (double)0x7F;
		double v_bank = (double)bank.getVolume()/maxvol;
		double v_prog = (double)prog.getVolume()/maxvol;
		double v_tone = (double)tone.getVolume()/maxvol;
		double vrat = (double)vel/maxvol;
		double v_vel = (vrat * vrat);
		//vol_net_prog = (byte)Math.round(maxvol * v_bank * v_prog * v_tone * v_vel);
		vol_net_prog = v_bank * v_prog * v_tone;
		vol_vel = v_vel;
		vol_channel = 1.0;
		
		//pan_bank = bank.getPan();
		//pan_program = p.getPan();
		//pan_tone = t.getPan();
		reverb = tone.hasReverb();
		
		//Pitch
		unity = tone.getUnityKey() * 100;
		finetune += tone.getFineTune();
		pitchbend_range = tone.getPitchBendMax() * 100;
		playkey = (int)key * 100;
		
		//ADSR
		att = tone.getAttack();
		dec = tone.getDecay();
		sus = tone.getSustain();
		rel = tone.getRelease();
		sl = (double)sus.getLevel32()/(double)0x7FFFFFFF;
		
		calculateAmpRatios(bank.getPan(), prog.getPan(), tone.getPan());
		
		//Setup pipeline
		enterAttack(); //Initialize envelope
		pitch_interpolator = new UnbufferedWindowedSincInterpolator(source, 2, calculateNetCentChange());
		volfilter = new Amp(pitch_interpolator, 0.0);
		
		if(outSampleRate != source.getSampleRate())
		{
			sr_interpolator = new UnbufferedWindowedSincInterpolator(volfilter, 2);
			sr_interpolator.setOutputSampleRate(outSampleRate);
			//sr_interpolator.setUseTargetSampleRate(true);
		}
	}
	
	/* ----- Getters ----- */
	
	public boolean hasReverb()
	{
		return reverb;
	}
	
	public float getSampleRate()
	{
		if(sr_interpolator != null) return sr_interpolator.getSampleRate();
		else return source.getSampleRate();
	}
	
	public int getADSRPhase()
	{
		return this.adsr_phase;
	}
	
	/* ----- Setters ----- */
	
	/* ----- Calculation ----- */
	
	private double calculateNetVolume()
	{
		double raw = vol_net_prog * vol_channel * env_lvl * vol_vel;
		return raw;
	}
	
	private int calculateNetCentChange()
	{
		//Before LFO
		int diff = playkey - unity;
		diff += finetune;
		diff += pitchbend_cents;
		return diff;
	}

	/* ----- ADSR Control ----- */
	
	public int getDelay(){return 0;}
	public Attack getAttack(){return att;}
	public int getHold(){return 0;}
	public Decay getDecay(){return dec;}
	public Sustain getSustain(){return sus;}
	public Release getRelease(){return rel;}
	
	private void enterAttack()
	{
		adsr_phase = ADSR_PHASE_ATTACK;
		env_state = att.openStream(44100);
		env_lvl = 0.0;
	}
	
	private void enterDecay()
	{
		adsr_phase = ADSR_PHASE_DECAY;
		env_state = dec.openStream(44100, sl);
		env_lvl = 1.0;
	}
	
	private void enterSustain()
	{
		adsr_phase = ADSR_PHASE_SUSTAIN;
		env_state = sus.openStream(44100);
		env_lvl = sl;
	}
	
	private void enterRelease()
	{
		adsr_phase = ADSR_PHASE_RELEASE;
		env_state = rel.openStream(44100, env_lvl);
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
			env_lvl = env_state.getNextAmpRatio();
			if(env_state.done()) rel_end = true;
			break;
		}
		updateVolume();
	}
	
	public void releaseMe()
	{
		//System.err.println("Released!");
		enterRelease();
	}
	
	public boolean releaseSamplesRemaining()
	{
		if(adsr_phase != ADSR_PHASE_RELEASE) return true;
		return !rel_end;
	}
	
	/* ----- Pitch Control ----- */
	
	public byte getKeyPlayed()
	{
		return (byte)(playkey/100);
	}
	
	private void updatePitch()
	{
		//Add LFO, if present
		int cents = calculateNetCentChange();
		if(lfo != null)
		{
			cents += (int)Math.round(lfo.getNextValue());
		}
		pitch_interpolator.setPitchShift(cents);
	}
	
	public void setPitchWheelLevel(int lvl)
	{
		//Level is treated as a signed 16-bit value?
		if(lvl == 0)
		{
			pitchbend_cents = 0;
			updatePitch();
			return;
		}
		
		double maxcents = (double)pitchbend_range;
		double wheel = (double)Math.abs(lvl);
		pitchbend_cents = (int)Math.round((wheel/(double)0x7FFF) * maxcents);
		if(lvl < 0) pitchbend_cents *= -1;
		updatePitch();
	}
	
	public void setPitchBendDirect(int cents){
		if(Math.abs(cents) > pitchbend_range){
			boolean n = cents < 0;
			cents = pitchbend_range;
			if(n) cents *= -1;
		}
		
		pitchbend_cents = cents;
		updatePitch();
	}
	
	public void setLFO(Oscillator osc)
	{
		lfo = osc;
		updatePitch();
	}
	
	public void removeLFO()
	{
		lfo = null;
		updatePitch();
	}
	
	/* ----- Volume Control ----- */
	
	private void updateVolume()
	{
		volfilter.setAmplitudeRatio(this.calculateNetVolume());
	}
	
	private void calculateAmpRatios(int panBank, int panProg, int panTone)
	{
		//Tone -> Program -> Bank
		double[] tonepan = PanUtils.getLRAmpRatios_Mono2Stereo((byte)panTone);
		double[][] progpan = PanUtils.getLRAmpRatios_Stereo2Stereo((byte)panProg);
		double[][] bankpan = PanUtils.getLRAmpRatios_Stereo2Stereo((byte)panBank);
		
		ampratio_L = tonepan[0];
		ampratio_R = tonepan[1];
		
		ampratio_L *= (progpan[0][0] + progpan[1][0]);
		ampratio_L *= (bankpan[0][0] + bankpan[1][0]);
		
		ampratio_R *= (progpan[0][1] + progpan[1][1]);
		ampratio_R *= (bankpan[0][1] + bankpan[1][1]);
	}
	
	public void setChannelVolume(byte vol)
	{
		vol_channel = (double)vol/(double)0x7F;
		updateVolume();
	}
	
	public double[] getInternalPanAmpRatios()
	{
		double[] out = new double[2];
		out[0] = ampratio_L;
		out[1] = ampratio_R;
		return out;
	}
	
	/* ----- Stream Control ----- */
	
	public int[] nextSample() throws InterruptedException
	{
		if(closed) return null;
		//return source.nextSample();
		//return this.pitch_interpolator.nextSample();
		int[] out = null;
		if(sr_interpolator != null) out = sr_interpolator.nextSample();
		else out = volfilter.nextSample();
		if(lfo != null) updatePitch();
		advanceEnvelope();
		return out;
	}
		
	public void close()
	{
		closed = true;
		removeLFO();
		this.pitch_interpolator.close();
		this.volfilter.close();
	}
	
	/* ----- Debug ----- */
	
	public double[] getVolumeValues()
	{
		//0 - BPT (Region intrinsic)
		//1 - Velocity
		//2 - Envelope Level
		//3 - Channel Setting
		//4 - Net
		double[] d = new double[5];
		d[0] = vol_net_prog;
		d[1] = vol_vel;
		d[2] = env_lvl;
		d[3] = vol_channel;
		d[4] = calculateNetVolume();
		
		return d;
	}
	
}
