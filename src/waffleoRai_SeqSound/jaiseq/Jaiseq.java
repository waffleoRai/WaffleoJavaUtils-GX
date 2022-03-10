package waffleoRai_SeqSound.jaiseq;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import javax.sound.midi.InvalidMidiDataException;

import waffleoRai_SeqSound.BasicMIDIGenerator;
import waffleoRai_SeqSound.MIDI;
import waffleoRai_SoundSynth.SequenceController;
import waffleoRai_Utils.FileBuffer;

public class Jaiseq {
	
	//Note: For now I'm assuming that VLQs are read like MIDI ones.
	//TODO: Something odd going on with pitchbend I think?

	/*----- Constants -----*/
	
	public static final int DEFAULT_PPQN = 48;
	
	public static final int PERF_TYPE_VOL = 0;
	public static final int PERF_TYPE_PITCHBEND = 1;
	public static final int PERF_TYPE_RVB = 2;
	public static final int PERF_TYPE_PAN = 3;
	
	public static final int PARAM_TYPE_BANK = 0x20;
	public static final int PARAM_TYPE_PROG = 0x21;
	
	public static final int ARTIC_TYPE_TIMEBASE = 0x62;
	
	protected static final boolean PERFTIME_MODE_LINEARINC = true;
	
	/*----- Instance Variables -----*/
	
	private boolean cflag;
	
	private boolean block_back_jumps = true;
	private int loop_pos = -1;
	private int loop_end = -1;
	
	private int pos;
	private int tick;
	
	private int wait_pending;
	private LinkedList<Integer> stack;
	private boolean end_flag = true;
	
	private double vol;
	private double pan;
	private double reverb;
	
	private int init_ppqn = DEFAULT_PPQN;
	private int tempo;
	private int timebase;
	private boolean tempo_flag;
	
	private int bank_idx;
	
	private JaiseqReader src;
	private SequenceController targ;
	
	private boolean error_flag = false;
	private boolean warning_flag = false;
	private int error_addr = -1;
	private int warning_addr = -1;
	
	private ArrayList<JaiseqTrack> tracks;
	private boolean seqonly_mode = false;
	
	/*----- Init -----*/
	
	public Jaiseq(JaiseqReader source){
		src = source;
		stack = new LinkedList<Integer>();
		tracks = new ArrayList<JaiseqTrack>(16);
		reset();
	}
	
	public static Jaiseq readJaiseq(FileBuffer data){
		JaiseqReader jsreader = JaiseqReader.readJaiseq(data);
		if(jsreader == null) return null;
		return new Jaiseq(jsreader);
	}
	
	/*----- Getters -----*/
	
	public boolean isOpen(){return !end_flag;}
	public boolean errorFlag(){return error_flag;}
	public int getErrorAddress(){return error_addr;}
	public boolean warningFlag(){return warning_flag;}
	public int getWarningAddress(){return warning_addr;}
	public boolean getConditionFlag(){return cflag;}
	public boolean backjumpsBlocked(){return block_back_jumps;}
	public double getMasterReverb(){return reverb;}
	public JaiseqCommand getHead(){return src.getCommandTreeHead();}
	
	public List<JaiseqCommand> linearize(){
		JaiseqWriter w = new JaiseqWriter(src.getCommandTreeHead());
		return w.linearize();
	}
	
	/*----- Setters -----*/
	
	public void setConditionFlag(boolean b){cflag = b;}
	public void setBackjumpsBlock(boolean b){block_back_jumps = b;}
	public void setTarget(SequenceController target){targ = target;}
	
	/*----- Playback -----*/
	
	public void reset(){
		pos = 0;
		tick = 0;
		wait_pending = 0;
		stack.clear();
		end_flag = true;
		vol = 1.0;
		pan = 0.0;
		reverb = 0.0;
		bank_idx = 0;
		//init_ppqn = DEFAULT_PPQN;
		timebase = init_ppqn;
		tempo = 120;
		
		error_flag = false;
		warning_flag = false;
		error_addr = -1;
		warning_addr = -1;
		tracks.clear();
	}
	
	public void onTick(){
		if(end_flag || error_flag) return;
		
		if(--wait_pending <= 0){
			while(!end_flag && !error_flag && wait_pending <= 0){
				//Do seq commands
				JaiseqCommand cmd = src.getCommandAt(pos);
				if(cmd == null){
					error_flag = true;
					error_addr = pos;
					return;
				}
				
				if(targ != null){
					if(pos == loop_pos) targ.addMarkerNote("loop");
				}
				
				int cpos = pos;
				pos += cmd.getSize();
				if(!cmd.doAction(this)){
					warning_flag = true;
					warning_addr = cpos;
				}
				
				if(targ != null){
					if(cpos == loop_end) targ.addMarkerNote("loop end");
				}
			}	
			
			//Check flags for things to resolve.
			if(tempo_flag){
				tempo_flag = false;
				if(targ != null){
					targ.setTempo(MIDI.bpm2uspqn(tempo, timebase));
				}
			}
		}
		
		//Do track commands
		if(error_flag) return;
		if(!seqonly_mode){
			for(JaiseqTrack track : tracks){
				track.onTick();
				if(track.errorFlag()){
					error_flag = true;
					error_addr = track.getErrorAddress();
					return;
				}
			}	
		}
		
		tick++;
		if(targ != null)targ.advanceTick();
	}
	
	public void scanMasterTrack(){
		//Looks for loop points and initial timebase.
		boolean back = block_back_jumps;
		block_back_jumps = true;
		SequenceController t = targ;
		seqonly_mode = true;
		
		reset();
		playThroughTo(null);
		
		block_back_jumps = back;
		targ = t;
		seqonly_mode = false;
		reset();
	}
	
	public void playThroughTo(SequenceController target){
		targ = target;
		end_flag = false;
		
		while(!end_flag && !error_flag){
			onTick();
		}
		
		if(error_flag){
			System.err.println("Jaiseq.playThroughTo || Playback error at 0x" + Integer.toHexString(error_addr));
		}
	}
	
	/*----- Command Interface -----*/
	
	public void openTrack(int idx, int addr){
		if(seqonly_mode) return;
		JaiseqTrack track = new JaiseqTrack(idx, src);
		if(!track.openAt(addr)){
			error_flag = true;
			error_addr = pos;
		}
		track.setTarget(targ);
		track.setConditionFlag(cflag);
		tracks.add(track);
		//System.err.println("Opened track " + idx + " @ 0x" + Integer.toHexString(addr));
	}
	
	public void jumpTo(int addr){
		if(block_back_jumps){
			if(addr <= pos){
				loop_end = pos;
				loop_pos = addr;
				//signalEndTrack();
				return;
			}
		}
		pos = addr;
	}
	
	public void call(int addr){
		stack.push(pos);
		pos = addr;
	}
	
	public boolean returnFromSubroutine(){
		if(stack.isEmpty()) return false;
		pos = stack.pop();
		return true;
	}
	
	public void signalEndTrack(){
		end_flag = true;
	}
	
	public void setWait(int ticks){
		wait_pending = ticks;
	}
	
	public void setTempo(int bpm){
		tempo = bpm;
		tempo_flag = true; //So only one tempo command sent for the tick (resolved in onTick)
	}
	
	public void setTimebase(int ppqn){
		timebase = ppqn;
		if(tick == 0){
			init_ppqn = timebase;
		}
		tempo_flag = true;//So only one tempo command sent for the tick (resolved in onTick)
	}
	
	public void setVolume(double val){
		setVolume(val, -1);
	}
	
	public void setPan(double val){
		setPan(val, -1);
	}
	
	public void setReverb(double val){
		setReverb(val, -1);
	}
	
	public void setVolume(double val, int time){
		//TODO Dunno what to do with time yet
		vol = val;
		if(targ != null){
			byte v = (byte)Math.round(vol * 127.0);
			targ.setMasterVolume(v);
		}
		//wait_pending = time;
	}
	
	public void setPan(double val, int time){
		//TODO Dunno what to do with time yet
		pan = val;
		if(targ != null){
			byte p = 0x40;
			if(pan < 0.0){
				val += 1.0;
				val *= 64.0;
				p = (byte)Math.round(val);
			}
			else if(pan > 0.0){
				val *= 63.0;
				val += 64.0;
				p = (byte)Math.round(val);
			}
			targ.setMasterPan(p);
		}
		//wait_pending = time;
	}
	
	public void setReverb(double val, int time){
		//TODO Dunno what to do with time yet
		//wait_pending = time;
	}
	
	public void setBank(int val){
		bank_idx = val;
		for(JaiseqTrack track : tracks){
			track.setBank(bank_idx);
		}
	}
	
	/*----- Output/Conversion -----*/
	
	public MIDI toMIDI() throws InvalidMidiDataException{
		//reset();
		scanMasterTrack();
		BasicMIDIGenerator mgen = new BasicMIDIGenerator(init_ppqn);
		block_back_jumps = true;
		
		playThroughTo(mgen);
		if(error_flag) return null;
		
		mgen.complete();
		return new MIDI(mgen.getSequence());
	}
	
	public void toMMLScript(Writer writer) throws IOException{
		JaiseqWriter jsw = new JaiseqWriter(src.getCommandTreeHead());
		jsw.writeMML(writer);
	}
	
	public void serializeTo(OutputStream out) throws IOException{
		JaiseqWriter jsw = new JaiseqWriter(src.getCommandTreeHead());
		jsw.writeBinary(out);
	}
	
	public void printLiteral(Writer writer) throws IOException{
		List<JaiseqCommand> clist = src.getOrderedCommands();
		for(JaiseqCommand cmd : clist){
			writer.write(String.format("%06x", cmd.getAddress()));
			writer.write("\t");
			writer.write(cmd.toString());
			writer.write("\n");
		}
	}
	
	/*----- Type Definition -----*/
	
	/*----- Test -----*/
	
	public static void main(String[] args){
		
		/*String inpath = args[0];
		String txtpath = args[1];
		String muspath = args[2];
		String midpath = args[3];*/
		
		String indir = args[0];
		String outdir = args[1];
		
		try{
			DirectoryStream<Path> dstr = Files.newDirectoryStream(Paths.get(indir));
			for(Path p : dstr){
				String inpath = p.toAbsolutePath().toString();
				String fname = p.getFileName().toString();
				fname = fname.substring(0, fname.lastIndexOf('.'));
				String txtpath = outdir + File.separator + fname + ".txt";
				String muspath = outdir + File.separator + fname + ".mus";
				String midpath = outdir + File.separator + fname + ".mid";
				
				if(fname.startsWith("~")) continue;
				
				System.err.println("Now reading: " + fname);
				
				Jaiseq seq = Jaiseq.readJaiseq(FileBuffer.createBuffer(inpath, true));
				if(seq == null){
					System.err.println("Failed to read jaiseq!");
					//System.exit(1);
					continue;
				}
				
				BufferedWriter bw = new BufferedWriter(new FileWriter(txtpath));
				seq.printLiteral(bw);
				bw.close();
				
				bw = new BufferedWriter(new FileWriter(muspath));
				seq.toMMLScript(bw);
				bw.close();
				
				seq.setConditionFlag(true);
				MIDI midi = seq.toMIDI();
				if(midi == null){
					System.err.println("MIDI conversion failed!");
					continue;
				}
				midi.writeMIDI(midpath);	
			}
			dstr.close();
		}
		catch(Exception ex){
			ex.printStackTrace();
			System.exit(1);
		}
		
	}
	
}
