package waffleoRai_SeqSound.jaiseq.cmd;

import waffleoRai_SeqSound.jaiseq.Jaiseq;
import waffleoRai_SeqSound.jaiseq.JaiseqCommand;
import waffleoRai_SeqSound.jaiseq.JaiseqTrack;

public class JaiseqCommands {
	
	public static class JSC_OpenTrack extends JaiseqCommand{

		public JSC_OpenTrack() {super(EJaiseqCmd.OPEN_TRACK);}
		public boolean doAction(JaiseqTrack track) {return false;}

		public boolean doAction(Jaiseq seq) {
			if(ref != null){
				seq.openTrack(args[0], ref.getAddress());
			}
			else seq.openTrack(args[0], args[1]);
			return true;
		}
		
		public byte[] serializeMe(){
			if(ref != null){args[1] = ref.getAddress();}
			return super.serializeMe();
		}
		
		public String toMML(){
			StringBuilder sb = new StringBuilder(128);
			sb.append(cmd_enum.toString());
			sb.append(' '); sb.append(args[0]);
			sb.append(", ");
			if(ref != null && ref.getLabel() != null){
				sb.append(ref.getLabel());	
			}
			else{
				sb.append("0x" + Integer.toHexString(args[1]));	
			}
			return sb.toString();
		}
		
		public String toString(){
			StringBuilder sb = new StringBuilder(128);
			sb.append(cmd_enum.toString());
			sb.append(' '); sb.append(args[0]);
			sb.append(", "); sb.append("0x" + Integer.toHexString(args[1]));
			return sb.toString();
		}
		
	}
	
	public static class JSC_EndTrack extends JaiseqCommand{

		public JSC_EndTrack() {super(EJaiseqCmd.END_TRACK);}

		public boolean doAction(Jaiseq seq) {
			seq.signalEndTrack();
			return true;
		}

		public boolean doAction(JaiseqTrack track) {
			track.signalEndTrack();
			return true;
		}
		
	}
	
	public static class JSC_NoteOn extends JaiseqCommand{

		public JSC_NoteOn() {super(EJaiseqCmd.NOTE_ON);}
		public boolean doAction(Jaiseq seq) {return false;}

		public boolean doAction(JaiseqTrack track) {
			return track.noteOn((byte)args[0], args[1], (byte)args[2]);
		}
	}
	
	public static class JSC_NoteOff extends JaiseqCommand{

		public JSC_NoteOff() {super(EJaiseqCmd.NOTE_OFF);}
		public boolean doAction(Jaiseq seq) {return false;}

		public boolean doAction(JaiseqTrack track) {
			return track.noteOff(args[0]);
		}
	}
	
	public static class JSC_Wait extends JaiseqCommand{

		public JSC_Wait(EJaiseqCmd ecmd) {super(ecmd);}

		public boolean doAction(Jaiseq seq) {
			seq.setWait(args[0]);
			return true;
		}

		public boolean doAction(JaiseqTrack track) {
			track.setWait(args[0]);
			return true;
		}
		
		public String toMML(){
			StringBuilder sb = new StringBuilder(128);
			sb.append("delay ");
			sb.append(args[0]);
			return sb.toString();
		}
	}
	
	public static class JSC_Tempo extends JaiseqCommand{

		public JSC_Tempo() {
			super(EJaiseqCmd.TEMPO);
		}
		
		public JSC_Tempo(boolean rvl) {
			super(rvl?EJaiseqCmd.TEMPO_RVL:EJaiseqCmd.TEMPO);
		}
		
		public boolean doAction(JaiseqTrack track) {return false;}

		public boolean doAction(Jaiseq seq) {
			seq.setTempo(args[0]);
			return true;
		}

		public String toMML(){
			StringBuilder sb = new StringBuilder(128);
			sb.append("tempo ");
			sb.append(args[0]);
			return sb.toString();
		}
	}
	
	public static class JSC_Timebase extends JaiseqCommand{
		public JSC_Timebase() {
			super(EJaiseqCmd.TIMEBASE);
		}

		public boolean doAction(Jaiseq seq) {
			seq.setTimebase(args[0]);
			return true;
		}

		public boolean doAction(JaiseqTrack track) {return false;}
	}
	
	public static class JSC_SetBank extends JaiseqCommand{

		public JSC_SetBank() {super(EJaiseqCmd.SET_BANK);}

		public boolean doAction(Jaiseq seq) {
			seq.setBank(args[0]);
			return true;
		}

		public boolean doAction(JaiseqTrack track) {
			track.setBank(args[0]);
			return true;
		}
		
	}
	
	public static class JSC_SetProgram extends JaiseqCommand{

		public JSC_SetProgram() {super(EJaiseqCmd.SET_PROGRAM);}
		public boolean doAction(Jaiseq seq) {return false;}

		public boolean doAction(JaiseqTrack track) {
			track.setProgram(args[0]);
			return true;
		}
	}
	
	public static class JSC_VibWidth extends JaiseqCommand{

		public JSC_VibWidth() {super(EJaiseqCmd.VIB_WIDTH);}
		public boolean doAction(Jaiseq seq) {return false;}

		public boolean doAction(JaiseqTrack track) {
			track.setVibFreq((double)args[0]/32767.0);
			return true;
		}
	}
	
	public static class JSC_VibDepth extends JaiseqCommand{

		public JSC_VibDepth() {super(EJaiseqCmd.VIB_DEPTH);}
		public boolean doAction(Jaiseq seq) {return false;}

		public boolean doAction(JaiseqTrack track) {
			track.setVibDepth((double)args[0]/127.0);
			return true;
		}
		
	}

	public static class JSC_Jump extends JaiseqCommand{
		
		private boolean c = false;
		public JSC_Jump(boolean conditional) {
			super(conditional?EJaiseqCmd.JUMP_COND:EJaiseqCmd.JUMP);
			c = conditional;
		}

		public boolean doAction(Jaiseq seq) {
			if(!c || (c && seq.getConditionFlag())){
				int addr = 0;
				if(ref != null) addr = ref.getAddress();
				else{
					if(c) addr = args[1];
					else addr = args[0];
				}
				seq.jumpTo(addr);
			}
			return true;
		}

		public boolean doAction(JaiseqTrack track) {
			if(!c || (c && track.getConditionFlag())){
				int addr = 0;
				if(ref != null) addr = ref.getAddress();
				else{
					if(c) addr = args[1];
					else addr = args[0];
				}
				track.jumpTo(addr);
			}
			return true;
		}
		
		public byte[] serializeMe(){
			if(ref != null){
				if(c) args[1] = ref.getAddress();
				else args[0] = ref.getAddress();
			}
			return super.serializeMe();
		}
		
		public String toMML(){
			StringBuilder sb = new StringBuilder(128);
			sb.append(cmd_enum.toString());
			int addr = c?args[1]:args[0];
			sb.append(' '); 
			if(c){
				sb.append(args[0]);
				sb.append(", ");	
			}
			if(ref != null && ref.getLabel() != null){
				sb.append(ref.getLabel());	
			}
			else{
				sb.append("0x" + Integer.toHexString(addr));	
			}
			return sb.toString();
		}
		
		public String toString(){
			StringBuilder sb = new StringBuilder(128);
			sb.append(cmd_enum.toString());
			sb.append(' ');
			if(c) {
				sb.append(args[0]);
				sb.append(", "); sb.append("0x" + Integer.toHexString(args[1]));
			}
			else{
				sb.append("0x" + Integer.toHexString(args[0]));
			}
			return sb.toString();
		}
		
	}
	
	public static class JSC_Call extends JaiseqCommand{
		
		private boolean c = false;
		public JSC_Call(boolean conditional) {
			super(conditional?EJaiseqCmd.CALL_COND:EJaiseqCmd.CALL);
			c = conditional;
		}

		public boolean doAction(Jaiseq seq) {
			if(!c || (c && seq.getConditionFlag())){
				int addr = 0;
				if(ref != null) addr = ref.getAddress();
				else{
					if(c) addr = args[1];
					else addr = args[0];
				}
				seq.call(addr);
			}
			return true;
		}

		public boolean doAction(JaiseqTrack track) {
			if(!c || (c && track.getConditionFlag())){
				int addr = 0;
				if(ref != null) addr = ref.getAddress();
				else{
					if(c) addr = args[1];
					else addr = args[0];
				}
				track.call(addr);
			}
			return true;
		}
		
		public byte[] serializeMe(){
			if(ref != null){
				if(c) args[1] = ref.getAddress();
				else args[0] = ref.getAddress();
			}
			return super.serializeMe();
		}
		
		public String toMML(){
			StringBuilder sb = new StringBuilder(128);
			sb.append(cmd_enum.toString());
			int addr = c?args[1]:args[0];
			sb.append(' '); 
			if(c){
				sb.append(args[0]);
				sb.append(", ");	
			}
			if(ref != null && ref.getLabel() != null){
				sb.append(ref.getLabel());	
			}
			else{
				sb.append("0x" + Integer.toHexString(addr));	
			}
			return sb.toString();
		}
		
		public String toString(){
			StringBuilder sb = new StringBuilder(128);
			sb.append(cmd_enum.toString());
			sb.append(' ');
			if(c) {
				sb.append(args[0]);
				sb.append(", "); sb.append("0x" + Integer.toHexString(args[1]));
			}
			else{
				sb.append("0x" + Integer.toHexString(args[0]));
			}
			return sb.toString();
		}
		
	}
	
	public static class JSC_Return extends JaiseqCommand{
		
		private boolean c = false;
		public JSC_Return(boolean conditional) {
			super(conditional?EJaiseqCmd.RETURN_COND:EJaiseqCmd.RETURN);
			c = conditional;
		}

		public boolean doAction(Jaiseq seq) {
			if(!c || (c && seq.getConditionFlag())){
				return seq.returnFromSubroutine();
			}
			return true;
		}

		public boolean doAction(JaiseqTrack track) {
			if(!c || (c && track.getConditionFlag())){
				return track.returnFromSubroutine();
			}
			return true;
		}
	}
	
	public static class JSC_SetPerf extends JaiseqCommand{
		
		private double value;
		private boolean update_flag = true;
		
		public JSC_SetPerf(EJaiseqCmd ecmd) {
			super(ecmd);
		}
		
		private void calculateValue(){
			switch(cmd_enum){
			case PERFRVL_S16:
			case PERF_S16_NODUR:
			case PERF_S16_U16:
			case PERF_S16_U8:
				value = (double)args[1] / (double)0x7fff;
				break;
			case PERFRVL_S8:
			case PERF_S8_NODUR:
			case PERF_S8_U16:
			case PERF_S8_U8:
				value = (double)args[1] / (double)0x7f;
				break;
			case PERF_U8_NODUR:
			case PERF_U8_U16:
			case PERF_U8_U8:
				if(args[0] == Jaiseq.PERF_TYPE_PAN){
					value = (double)args[1] / (double)0x7f;
				}
				else{
					value = (double)args[1] / (double)0xff;
				}
				break;
			default:
				break;
			}
			if(args[0] == Jaiseq.PERF_TYPE_PAN){
				//Rescale so that it ranges from -1 to 1
				if(args[1] == 0x40) value = 0.0;
				else{
					value *= 2.0;
					value -= 1.0;
				}
			}
			update_flag = false;
		}

		public boolean doAction(Jaiseq seq) {
			if(update_flag) calculateValue();
			boolean hastime = args.length > 2;
			switch(args[0]){
			case Jaiseq.PERF_TYPE_VOL:
				if(hastime)seq.setVolume(value, args[2]);
				else seq.setVolume(value);
				return true;
			case Jaiseq.PERF_TYPE_PAN:
				if(hastime)seq.setPan(value, args[2]);
				else seq.setPan(value);
				return true;
			case Jaiseq.PERF_TYPE_RVB:
				if(hastime)seq.setReverb(value, args[2]);
				else seq.setReverb(value);
				return true;
			}
			return false;
		}

		public boolean doAction(JaiseqTrack track) {
			if(update_flag) calculateValue();
			boolean hastime = args.length > 2;
			switch(args[0]){
			case Jaiseq.PERF_TYPE_VOL:
				if(hastime)track.setVolume(value, args[2]);
				else track.setVolume(value);
				return true;
			case Jaiseq.PERF_TYPE_PAN:
				if(hastime)track.setPan(value, args[2]);
				else track.setPan(value);
				return true;
			case Jaiseq.PERF_TYPE_RVB:
				if(hastime)track.setReverb(value, args[2]);
				else track.setReverb(value);
				return true;
			case Jaiseq.PERF_TYPE_PITCHBEND:
				if(hastime)track.setPitchbend(value, args[2]);
				else track.setPitchbend(value);
				return true;
			}
			return false;
		}
		
		public double getValue(){
			if(update_flag) calculateValue();
			return value;
		}
		
		public String toMML(){
			StringBuilder sb = new StringBuilder(128);
			if(update_flag) calculateValue();
			switch(args[0]){
			case Jaiseq.PERF_TYPE_VOL:
				sb.append("vol ");
				sb.append(args[1]);
				break;
			case Jaiseq.PERF_TYPE_PAN:
				sb.append("pan ");
				sb.append(args[1]);
				break;
			case Jaiseq.PERF_TYPE_RVB:
				sb.append("reverb ");
				sb.append(args[1]);
				break;
			case Jaiseq.PERF_TYPE_PITCHBEND:
				sb.append("pitchbend ");
				sb.append(args[1]);
				break;
			default:
				sb.append("setperf ");
				sb.append(String.format("0x%02x", args[0]));
				sb.append(", ");
				sb.append(args[1]);
				break;
			}
			if(args.length > 2){
				sb.append(", ");
				sb.append(args[2]);
			}
			return sb.toString();
		}
	}
	
	public static class JSC_SetParam extends JaiseqCommand{
		
		public JSC_SetParam(EJaiseqCmd ecmd) {
			super(ecmd);
		}

		public boolean doAction(Jaiseq seq) {
			switch(args[0]){
			case Jaiseq.PARAM_TYPE_BANK:
				seq.setBank(args[1]);
				return true;
			case Jaiseq.PARAM_TYPE_PROG:
				return false;
			}
			return false;
		}

		public boolean doAction(JaiseqTrack track) {
			switch(args[0]){
			case Jaiseq.PARAM_TYPE_BANK:
				track.setBank(args[1]);
				return true;
			case Jaiseq.PARAM_TYPE_PROG:
				track.setProgram(args[1]);
				return false;
			}
			return false;
		}
		
		public String toMML(){
			StringBuilder sb = new StringBuilder(128);
			switch(args[0]){
			case Jaiseq.PARAM_TYPE_BANK:
				sb.append("bank ");
				sb.append(args[1]);
				break;
			case Jaiseq.PARAM_TYPE_PROG:
				sb.append("instr ");
				sb.append(args[1]);
				break;
			default:
				sb.append("setparam ");
				sb.append(String.format("0x%02x", args[0]));
				sb.append(", ");
				sb.append(args[1]);
				break;
			}
			return sb.toString();
		}
	}
	
	public static class JSC_SetArtic extends JaiseqCommand{
		
		public JSC_SetArtic(EJaiseqCmd ecmd) {
			super(ecmd);
		}

		public boolean doAction(Jaiseq seq) {
			switch(args[0]){
			case Jaiseq.ARTIC_TYPE_TIMEBASE:
				seq.setTimebase(args[1]);
				return true;
			}
			return false;
		}

		public boolean doAction(JaiseqTrack track) {return false;}
		
		public String toMML(){
			StringBuilder sb = new StringBuilder(128);
			switch(args[0]){
			case Jaiseq.ARTIC_TYPE_TIMEBASE:
				sb.append("timebase ");
				sb.append(args[1]);
				break;
			default:
				sb.append("setartic ");
				sb.append(String.format("0x%02x", args[0]));
				sb.append(", ");
				sb.append(args[1]);
				break;
			}
			return sb.toString();
		}
		
	}
	
}
