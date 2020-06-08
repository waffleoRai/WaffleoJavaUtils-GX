package waffleoRai_SeqSound.misc.smd;

public class SMDMiscEvent implements SMDEvent{

	private SMDMiscType type;
	private int[] parameters;
	private int channel;
	
	private MiscEventCallback callback;
	
	public SMDMiscEvent(SMDMiscType t){
		type = t;
		parameters = new int[type.getParameterCount()];
		
		switch(t){
		case LOOP_POINT: callback = new Callback_LoopPoint(); break;
		case SET_BEND: callback = new Callback_SetPitchBend(); break; 
		case SET_MODU: callback = new Callback_SetMod(); break;
		case SET_OCTAVE: callback = new Callback_SetOctave(); break;
		case SET_PAN: callback = new Callback_SetPan(); break;
		case SET_SAMPLE: callback = new Callback_SetSample(); break;
		case SET_TEMPO: callback = new Callback_SetTempo(); break;
		case SET_VOLUME: callback = new Callback_SetVolume(); break;
		case SET_XPRESS: callback = new Callback_SetExpression(); break;
		case TRACK_END: callback = new Callback_TrackEnd(); break;
		default: break;
		}
		
	}
	
	public int getByteLength(){
		if (type != null)
		{
			return 1 + type.getParameterCount();
		}
		return -1;
	}
	
	public int getParameter(int i){
		if (i < 0) return -1;
		if (i >= parameters.length) return -1;
		return parameters[i];
	}
	
	public void setParameter(int i, int p){
		if (i < 0) return;
		if (i >= parameters.length) return;
		parameters[i] = p;
	}
	
	public SMDMiscType getType(){return this.type;}
	public int getChannel(){return this.channel;}
	public void setChannel(int c){this.channel = c;}
	
	public long getWait() {
		switch(type){
		case WAIT_ADD:
		case WAIT_1BYTE:
			return parameters[0];
		case WAIT_2BYTE:
			int val = parameters[0] & 0xFF;
			val |= (parameters[1] & 0xFF) << 8;
			return val;
		default: return 0;
		}
	}

	public void execute(SMDTrack t) {if(callback == null) return; callback.execute(t);}
	
	/*--- Misc. ---*/
	
	public byte[] serializeMe(){
		int pcount = 0;
		if(parameters != null) pcount = parameters.length;
		byte[] b = new byte[pcount+1];
		
		b[0] = (byte)type.getOpCode();
		if(parameters != null){
			for(int i = 0; i < pcount; i++){
				b[i+1] = (byte)parameters[i];
			}
		}
		
		return b;
	}
	
	/*--- Callbacks ---*/
	
	private static interface MiscEventCallback{
		public void execute(SMDTrack t);
	}
	
	private static class Callback_TrackEnd implements MiscEventCallback{

		public void execute(SMDTrack t) {t.onTrackEnd();}
		
	}
	
	private static class Callback_LoopPoint implements MiscEventCallback{

		public void execute(SMDTrack t) {t.markLoopPoint();}
		
	}
	
	private class Callback_SetOctave implements MiscEventCallback{

		public void execute(SMDTrack t) {t.setOctave(parameters[0]);}
		
	}
	
	private class Callback_SetSample implements MiscEventCallback{

		public void execute(SMDTrack t) {t.setProgram(parameters[0]);}
		
	}
	
	private class Callback_SetTempo implements MiscEventCallback{

		public void execute(SMDTrack t) {t.setTempo(parameters[0]);}
		
	}
	
	private class Callback_SetMod implements MiscEventCallback{

		public void execute(SMDTrack t) {t.setModWheel(parameters[0]);}
		
	}
	
	private class Callback_SetVolume implements MiscEventCallback{

		public void execute(SMDTrack t) {t.setVolume(parameters[0]);}
		
	}
	
	private class Callback_SetExpression implements MiscEventCallback{

		public void execute(SMDTrack t) {t.setExpression(parameters[0]);}
		
	}
	
	private class Callback_SetPan implements MiscEventCallback{

		public void execute(SMDTrack t) {t.setPan(parameters[0]);}
		
	}
	
	private class Callback_SetPitchBend implements MiscEventCallback{

		public void execute(SMDTrack t) {
			int val = parameters[0] & 0xFF;
			val |= (parameters[1] & 0xFF) << 8;
			val = (int)((short)val);
			t.setPitchWheel(val);
		}
		
	}
	
	public String toString(){
		int amt = 0;
		if(parameters != null && parameters.length >= 1) amt = parameters[0];
		switch(type){
		case LOOP_POINT: return "LOOP_POINT";
		case SET_BEND: 
			//amt = amt << 8;
			//amt |= (parameters[1] & 0xFF);
			amt |= ((parameters[1] & 0xFF) << 8);
			amt = (int)((short)amt);
			return "PITCH_BEND: " + amt + " cents";
		case SET_MODU: return "SET_MOD: " + amt;
		case SET_OCTAVE: return "SET_OCTAVE: " + amt;
		case SET_PAN: return "SET_PAN: " + String.format("0x%02x", amt);
		case SET_SAMPLE: return "CHANGE_PROGRAM: " + amt;
		case SET_TEMPO: return "CHANGE_TEMPO: " + amt + " bpm";
		case SET_VOLUME: return "SET_VOLUME: " + amt;
		case SET_XPRESS: return "SET_EXPRESSION: " + amt;
		case TRACK_END: return "TRACK_END";
		case WAIT_1BYTE: return "WAIT_1BYTE: " + amt + " ticks";
		case WAIT_2BYTE: 
			amt |= ((parameters[1] & 0xFF) << 8);
			return "WAIT_2BYTE: " + amt + " ticks";
		default: return type.name();
		}
	}
	
}
