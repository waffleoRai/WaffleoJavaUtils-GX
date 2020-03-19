package waffleoRai_SeqSound.ninseq;

public class MidiConvNinTrack extends NinTrack{
	
	private int if_branch_mode;
	private int var_use_mode;
	private int rand_use_mode;
	
	private NinSeqMidiConverter player;
	
	public MidiConvNinTrack(NinSeqDataSource data, NinSeqMidiConverter seqPlayer, long startAddr, int index) 
	{
		super(data, seqPlayer, startAddr, index);
		player = seqPlayer;
		if_branch_mode = NinSeqMidiConverter.MIDI_IF_MODE_CHECK;
		var_use_mode = NinSeqMidiConverter.MIDI_VAR_MODE_USEVAR;
		rand_use_mode = NinSeqMidiConverter.MIDI_RANDOM_MODE_RANDOM;
	}
	
	public void setMidiRandomMode(int mode)
	{
		this.rand_use_mode = mode;
	}
	
	public void setMidiVarMode(int mode)
	{
		this.var_use_mode = mode;
	}
	
	public void setMidiIfMode(int mode)
	{
		this.if_branch_mode = mode;
	}
	
	/* ~~ prefix ~~ */
	
	protected void executeWithRandom(NSEvent event)
	{
		int val = 0;
		
		if(rand_use_mode == NinSeqMidiConverter.MIDI_RANDOM_MODE_RANDOM)
		{
			val = super.getPlayer().getRandomNumber();
		}
		else if(rand_use_mode == NinSeqMidiConverter.MIDI_RANDOM_MODE_USEDEFO)
		{
			val = event.getCommand().getDefaultValue();
		}
		
		
		if(event.hasSecondParameter()) event.setParam2(val);
		else event.setParam1(val);
		event.execute(this);
	}
	
	protected void executeWithVariable(int vidx, NSEvent event)
	{
		int val = 0;
		
		if(var_use_mode == NinSeqMidiConverter.MIDI_VAR_MODE_USEVAR)
		{
			val = Short.toUnsignedInt(super.getPlayer().getVariableValue(vidx));
		}
		else if(var_use_mode == NinSeqMidiConverter.MIDI_VAR_MODE_USEDEFO)
		{
			val = event.getCommand().getDefaultValue();
		}
		
		if(event.hasSecondParameter()) event.setParam2(val);
		else event.setParam1(val);
		event.execute(this);
	}
	
	protected void executeIf(NSEvent event)
	{
		switch(if_branch_mode)
		{
		case NinSeqMidiConverter.MIDI_IF_MODE_CHECK: 
			if(super.getConditionalFlag()) event.execute(this);
			return;
		case NinSeqMidiConverter.MIDI_IF_MODE_ALWAYS_TRUE:
			event.execute(this);
			return;
		case NinSeqMidiConverter.MIDI_IF_MODE_ALWAYS_FALSE: return;
		}
	}
	
	/* ~~ other ~~ */
	
	protected void setPitchBendRange(int semitones)
	{
		super.setPitchBendRange(semitones);
		player.updatePitchBendRange(super.getTrackIndex(), semitones);
	}
}
