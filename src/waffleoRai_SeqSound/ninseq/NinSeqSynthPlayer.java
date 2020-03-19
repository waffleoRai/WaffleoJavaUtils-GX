package waffleoRai_SeqSound.ninseq;

import javax.sound.sampled.SourceDataLine;

import waffleoRai_SoundSynth.PlayerTrack;
import waffleoRai_SoundSynth.SequencePlayer;

public class NinSeqSynthPlayer extends SequencePlayer implements NinSeqPlayer{

	/*--- Constants ---*/
	
	public static final int SRC_SSEQ = 1; //DS SSEQ
	public static final int SRC_RSEQ = 2; //Wii RSEQ
	public static final int SRC_CSEQ = 3; //3DS CSEQ
	public static final int SRC_FSEQ = 4; //Wii U FSEQ
	
	public static final String[] INFO_STRS = {"Nintendo Sound Sequence",
											  "Binary Nitro Sound Sequence (SSEQ)",
											  "Binary Revolution Sound Sequence (RSEQ)",
			                                  "Binary CTR Sound Sequence (CSEQ)",
			                                  "Binary CafeOS Sound Sequence (FSEQ)"};
	
	public static final int OUTPUT_SAMPLERATE = 44100;
	public static final int OUTPUT_SAMPLERATE_CAFE = 48000;
	
	/*--- Instance Variables ---*/
	
	private String seq_name;
	private String bnk_name;
	private int src_type; //For info string or other uses
	
	private int samplerate;
	private int bitdepth;
	
	
	/*--- Inner Classes ---*/
	
	public class NinSeqSynthTrack implements PlayerTrack{

		@Override
		public void onTick(long tick) throws InterruptedException {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void resetTo(long tick) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public boolean trackEnd() {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public void setMute(boolean b) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public boolean isMuted() {
			// TODO Auto-generated method stub
			return false;
		}
		
	}
	
	/*--- Construction ---*/
	
	/*--- Getters ---*/
	
	public String getSequenceName() {return seq_name;}
	public String getBankName() {return bnk_name;}
	public String getTypeInfoString() {return INFO_STRS[src_type];}
	public float getSampleRate() {return samplerate;}
	public int getBitDepth() {return bitdepth;}
	public int getChannelCount() {return 2;} //Right now, only stereo supported, though the files have support for 5.1 surround
	public boolean jumpsAllowed() {return true;} //This is real-time, not captured, so won't bother turning off jumps
	
	@Override
	public boolean isPlaying() {
		// TODO Auto-generated method stub
		return false;
	}
	
	/*--- Setters ---*/
	
	public void setSequenceName(String name){this.seq_name = name;}
	public void setBankName(String name){this.bnk_name = name;}
	
	/*--- NinSeq Control ---*/
	
	public void play() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void stopTrack(int trackIndex) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void noteOn(int tidx, int note, int velocity) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void noteOff(int tidx, int note) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void mute(int tidx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void unmute(int tidx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void openTrack(int tidx, int addr) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public int getRandomNumber() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public short getVariableValue(int vidx) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void setVariableValue(int vidx, short value) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setVelocityRange(int tidx, int min, int max) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void updateTrackPan(int tidx, int pan) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setTrackInitPan(int tidx, int value) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void updateTrackSurroundPan(int tidx, int value) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void updateTrackVolume(int tidx, int vol) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setMasterVolume(int vol) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void updatePitchBend(int tidx, int cents) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setTrackPriority(int tidx, int pri) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setExpression(int tidx, int value) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setDamper(int tidx, boolean on) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setMonophony(int tidx, boolean on) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setTimebase(int tidx, int value) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setBiquadValue(int tidx, int value) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setBiquadType(int tidx, int value) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setPitchSweep(int tidx, int value) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void changeTrackBank(int tidx, int bankIndex) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void changeTrackProgram(int tidx, int program) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setPortamentoControl(int tidx, int note) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setPortamento(int tidx, boolean on) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setPortamentoTime(int tidx, int time) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setModulationDepth(int tidx, int value) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setModulationSpeed(int tidx, int value) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setModulationType(int tidx, int value) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setModulationRange(int tidx, int value) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setModulationDelay(int tidx, int millis) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setAttackOverride(int tidx, int millis) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setDecayOverride(int tidx, int millis) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setSustainOverride(int tidx, int level) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setReleaseOverride(int tidx, int millis) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setEnvelopeHold(int tidx, int millis) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void envelopeReset(int tidx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void printVariable(int vidx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setLPFCutoff(int tidx, int frequency) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setFXSendLevel(int tidx, int send, int level) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setMainSendLevel(int tidx, int level) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void addImmediate(int vidx, short imm) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void subtractImmediate(int vidx, short imm) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void multiplyImmediate(int vidx, short imm) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void divideImmediate(int vidx, short imm) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void modImmediate(int vidx, short imm) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void andImmediate(int vidx, int imm) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void orImmediate(int vidx, int imm) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void xorImmediate(int vidx, int imm) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void notImmediate(int vidx, int imm) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void shiftImmediate(int vidx, int imm) {
		// TODO Auto-generated method stub
		
	}
	
	/*--- External Control ---*/
	
	@Override
	protected int saturate(int in) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	protected void putNextSample(SourceDataLine target) throws InterruptedException {
		// TODO Auto-generated method stub
		
	}

}
