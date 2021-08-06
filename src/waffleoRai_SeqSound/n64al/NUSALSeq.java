package waffleoRai_SeqSound.n64al;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.Sequence;

import waffleoRai_Files.ConverterAdapter;
import waffleoRai_Files.WriterPrintable;
import waffleoRai_Files.tree.FileNode;
import waffleoRai_SeqSound.BasicMIDIGenerator;
import waffleoRai_SeqSound.MIDI;
import waffleoRai_SeqSound.SoundSeqDef;
import waffleoRai_SoundSynth.SequenceController;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;

public class NUSALSeq implements WriterPrintable{
	
	/*----- Constants -----*/
	
	public static final int NUS_TPQN = 48; //240
	
	public static final int EFFECT_ID_REVERB = 1;
	public static final int EFFECT_ID_VIBRATO = 2;
	
	public static final int MIDI_NRPN_ID_REVERB = 0x06d4;
	public static final int MIDI_NRPN_ID_LOOPSTART = 0x06f8;
	public static final int MIDI_NRPN_ID_LOOPEND = 0x06f7;
	//Write vibrato to the mod wheel
	
	/*----- Static Variables -----*/
	

	/*----- Instance Variables -----*/
	
	private FileBuffer data;
	private NUSALSeqCommandSource source;
	private SequenceController target;
	
	private int master_pos;
	private Deque<Integer> return_stack;
	
	private long wait_remain;
	private int var;
	private boolean term_flag;
	
	private boolean error_flag;
	private int error_addr;
	private String error_msg;
	private int error_ch;
	
	private byte format;
	private byte type;
	private int tempo; //bpm
	private byte master_vol;
	
	private boolean[] ch_enable;
	private NUSALSeqChannel[] channels;
	
	private boolean loop_enabled;
	private int lcounter; //Keeps track of how many times loop has occured
	private int loopst;
	private int looped;
	private int loopct;
	private boolean jumpLoop; //Set if the loop uses a branch/jump instead of loop commands
	
	private boolean anno_enable;
	
	/*----- Init -----*/
	
	public NUSALSeq(FileBuffer srcdat){
		try{
			data = srcdat.createCopy(0, srcdat.getFileSize());
			data.setEndian(true);
			ch_enable = new boolean[16];
			channels = new NUSALSeqChannel[16];
			return_stack = new LinkedList<Integer>();
			reset();
		}
		catch(IOException ex){
			ex.printStackTrace();
		}
	}
	
	public void reset(){
		master_pos = 0;
		return_stack.clear();
		wait_remain = 0;
		var = 0;
		term_flag = false;
		error_flag = false;
		format = (byte)0xFF;
		type = (byte)0xFF;
		tempo = 120;
		master_vol = 0x7f;
		error_msg = null; error_addr = -1; error_ch = -1;
		
		for(int i = 0; i < 16; i++){
			ch_enable[i] = false;
			//channels[i] = new NUSALSeqChannel(i);
		}
		
		loop_enabled = false;
		lcounter = 0;
		loopst = -1;
		looped = -1;
		loopct = -1;
		jumpLoop = false;
	}
	
	public void initialize(){
		//Creates channel objects and parses commands
		NUSALSeqParser parser = new NUSALSeqParser(data);
		parser.parse();
		source = parser.getCommandSource();
		for(int i = 0; i < 16; i++){
			channels[i] = new NUSALSeqChannel(i);
			channels[i].setCommandSource(source);
		}
		master_pos = 0;
	}
	
	/*----- Getters -----*/
	
	public byte getFormat(){return format;}
	public byte getType(){return type;}
	public int getCurrentPosition(){return master_pos;}
	public int getSeqVar(){return var;}
	
	public boolean hasJumpLoop(){return jumpLoop;}
	
	public NUSALSeqChannel getChannel(int idx){
		if(channels == null || idx < 0 || idx >= channels.length) return null;
		return channels[idx];
	}
	
	/*----- Setters -----*/
	
	public void setSeqVar(int value){var = value;}
	public void setFormat(byte value){format = value;}
	public void setType(byte value){type = value;}
	public void setLoopEnabled(boolean b){loop_enabled = b;}
	public void setAnnotationsEnabled(boolean b){anno_enable = b;}
	
	/*----- Channels -----*/
	
	public void setChannelEnabled(int ch, boolean enable){
		if(ch < 0 || ch >= 16) return;
		ch_enable[ch] = enable;
	}
	
	public boolean pointChannelTo(int ch, int pos){
		if(ch < 0 || ch >= 16) return false;
		if(channels[ch] == null) return false;
		return channels[ch].jumpTo(pos, false);
	}
	
	/*----- Playback -----*/
	
	public static int scaleTicks(int nus_ticks){
		/*int mid_ticks = (nus_ticks * 128) / 24;
		return mid_ticks;*/
		return nus_ticks;
	}

	public void signalLoopEnd(){
		looped = master_pos;
		if(target != null){
			target.addMarkerNote("Loop End [SEQ]");
			target.addNRPNEvent(0, MIDI_NRPN_ID_LOOPEND, 0, true);
		}
		
		//Loop if enabled
		if(loop_enabled && loopst >= 0){
			if(lcounter++ < loopct) master_pos = loopst;
		}
	}
	
	public void signalLoopStart(int loopcount){
		loopst = master_pos;
		loopct = loopcount;
		if(target != null){
			target.addMarkerNote("Loop Start [SEQ]");
			target.addNRPNEvent(0, MIDI_NRPN_ID_LOOPSTART, loopct, true);
		}
	}
	
	public void detectLoop(){
		/*
		 * So this is here because even though there are supposed commands
		 * for defining the loop points, it seems more common (like in later
		 * Nintendo sequences) to use a jump command to loop.
		 * 
		 * Which means the loop isn't explicitly marked, meaning if only one
		 * loop of the sequence is desired like in MIDI conversion, the loop
		 * point needs to be found and marked so that it doesn't continue
		 * indefinitely.
		 */
		
		jumpLoop = false;
		loopst = -1; loopct = -1; looped = -1;
		if(source == null) return;
		
		//Get all seq commands
		Map<Integer, NUSALSeqCommand> cmap = source.getSeqLevelCommands();
		if(cmap.isEmpty()) return;
		
		//Look for LOOP_END and LOOP_START commands
		List<Integer> keys = new ArrayList<Integer>(cmap.size()+1);
		keys.addAll(cmap.keySet());
		Collections.sort(keys);
		for(Integer k : keys){
			NUSALSeqCommand cmd = cmap.get(k);
			if(cmd.getCommand() == NUSALSeqCmdType.LOOP_START){
				loopst = k; loopct = cmd.getParam(0);
			}
			else if(cmd.getCommand() == NUSALSeqCmdType.LOOP_END){
				looped = k;
			}
		}
		
		if(loopst >= 0 && looped >= 0) return;
		
		//Scan for backward jumps
		int stcand = -1;
		int edcand = -1;
		for(Integer k : keys){
			NUSALSeqCommand cmd = cmap.get(k);
			if(cmd.getCommand() == NUSALSeqCmdType.BRANCH_ALWAYS){
				int btarg = cmd.getBranchAddress();
				if(btarg < k){
					int diff = k - btarg;
					int cdiff = edcand - stcand;
					if(diff > cdiff){
						stcand = btarg;
						edcand = k;
					}
				}
			}
		}
		
		if(stcand >= 0 && edcand >= 0){
			jumpLoop = true;
			loopst = stcand;
			looped = edcand;
		}
		
	}
	
	/*----- Control -----*/
	
	public void addAnnotation(String anno){
		if(!anno_enable) return;
		if(target != null){
			target.addMarkerNote(anno);
		}
	}
	
	public void setMasterVolume(byte vol){
		master_vol = vol;
		if(target != null){
			target.setMasterVolume(master_vol);
		}
	}
	
	public void setTempo(int bpm){
		tempo = bpm;
		if(target != null){
			target.setTempo(MIDI.bpm2uspqn(tempo, NUS_TPQN));
		}
	}
	
	public void signalSeqEnd(){
		//Called by both ff and c6 for now
		//I think for layers at least ff also works like a return
		//So I'll put that here just in case?
		if(!return_stack.isEmpty()){
			master_pos = return_stack.pop();
		}
		else term_flag = true;
	}
	
	public boolean jumpTo(int pos, boolean push_return){
		
		if(!loop_enabled && jumpLoop && (master_pos == looped)){
			term_flag = true;
			return false;
		}
		
		if(push_return) return_stack.push(master_pos+3);
		master_pos = pos;
		System.err.println("seq jumped to 0x" + Integer.toHexString(master_pos));
		
		return true;
	}
	
	public void returnFromCall(){
		if(return_stack.isEmpty()){
			error_flag = true;
			return;
		}
		master_pos = return_stack.pop();
	}
	
	public boolean setSeqWait(int ticks){
		//Scale to MIDI
		wait_remain = scaleTicks(ticks);
		return true;
	}
	
	public boolean nextTick(){
		if(term_flag || error_flag) return false;
		
		//Seq Itself
		if(wait_remain > 0) wait_remain--;
		while(wait_remain <= 0 && !term_flag){
			if(source == null) return false;
			if(anno_enable && jumpLoop && (master_pos == loopst)){
				//Mark loop point
				target.addMarkerNote("Loop Start [SEQ] (AUTOJUMP)");
				target.addNRPNEvent(0, MIDI_NRPN_ID_LOOPSTART, -1, true);
			}
			NUSALSeqCommand cmd = source.getCommandAt(master_pos);
			if(cmd == null){
				error_flag = true;
				error_addr = master_pos;
				error_ch = -1;
				error_msg = "Valid command not found at 0x" + Integer.toHexString(master_pos);
				return false;
			}
			int mypos = master_pos;
			if(!cmd.doCommand(this)){
				error_flag = true;
				error_addr = mypos;
				error_ch = -1;
				return false;
			}
			if(master_pos == mypos){
				//The command wasn't a jump, so advance manually.
				master_pos += cmd.getSizeInBytes();
			}
		}
		
		//Channels
		for(int i = 0; i < 16; i++){
			if(!ch_enable[i]) continue;
			if(channels[i] == null){
				error_flag = true;
				error_addr = -1;
				error_msg = "Channel " + i + " enabled, but not allocated";
				return false;
			}
			if(!channels[i].nextTick()){
				error_flag = true;
				error_addr = -1;
				error_ch = i;
				return false;
			}
		}
		
		return !term_flag;
	}
	
	public void play(SequenceController listener, boolean loop){
		reset();
		initialize();
		setLoopEnabled(loop);
		detectLoop();
		
		target = listener;
		for(int i = 0; i < 16; i++) channels[i].setListener(target);
		
		final int MAX_TICKS = 10 * 255 * NUS_TPQN; //10 min @ 255 bpm (max)
		int ticks = 0;
		while(nextTick() && (!loop || (ticks < MAX_TICKS))){
			ticks++;
			listener.advanceTick();
			//System.err.println("ticks = " + ticks);
		}
		
		if(error_flag){
			//Print error message!
			System.err.println("NUSALSeq.play -- ERROR at tick " + ticks);
			if(error_ch >= 0){
				if(channels[error_ch] != null){
					error_addr = channels[error_ch].getErrorAddress();
					int layer = channels[error_ch].getErrorLayer();
					if(layer >= 0) System.err.println("Encountered in channel " + error_ch + ", layer " + layer);
					else System.err.println("Encountered in channel " + error_ch);
				}
			}
			if(error_addr >= 0){
				//Print address as well
				System.err.println("Sequence Address: 0x" + Integer.toHexString(error_addr));
				if(source != null){
					NUSALSeqCommand errcmd = source.getCommandAt(error_addr);
					if(errcmd != null){
						System.err.println("Command: " + errcmd.toString());
					}
				}
			}
			if(error_msg != null) System.err.println("Error Message: " + error_msg);
			return;
		}
		
		if(!loop && ticks >= MAX_TICKS){
			 System.err.println("Warning -- Playback terminated after " + ticks + " ticks due to possible undetected infinite loop.");
		}
	}
	
	/*----- Conversion -----*/

	public MIDI toMidi() throws InvalidMidiDataException{
		BasicMIDIGenerator conv = new BasicMIDIGenerator(NUS_TPQN);
		anno_enable = true;
		play(conv, false);
		conv.complete();
		Sequence seq = conv.getSequence();
		MIDI mid = new MIDI(seq);
		
		return mid;
	}
	
	/*--- Type Definition ---*/
	
	private static NUSALSeqDef seq_def;
	private static NUSSeq2MidConverter stat_conv;
	
	public static NUSALSeqDef getDefinition(){
		if(seq_def == null) seq_def = new NUSALSeqDef();
		return seq_def;
	}
	
	public static NUSSeq2MidConverter getMIDIConverter(){
		if(stat_conv == null) stat_conv = new NUSSeq2MidConverter();
		return stat_conv;
	}
	
	public static class NUSALSeqDef extends SoundSeqDef{

		private static String DEFO_ENG_DESC = "Nintendo 64 Audio Library Sound Sequence";
		public static int TYPE_ID = 0x6458a04f;

		private String desc = DEFO_ENG_DESC;
		
		public Collection<String> getExtensions() {
			//I'll just make one up to match later consoles since there isn't one
			List<String> list = new ArrayList<String>(1);
			list.add("buseq");
			return list;
		}

		public String getDescription() {return desc;}
		public int getTypeID() {return TYPE_ID;}
		public void setDescriptionString(String s) {desc = s;}
		public String getDefaultExtension() {return "buseq";}

	}
	
	public static class NUSSeq2MidConverter extends ConverterAdapter{
		
		private static final String DEFO_FROMSTR_ENG = "N64 Standard Audio Library Sound Sequence";
		private static final String DEFO_TOSTR_ENG = "MIDI Sequence (.mid)";
		
		public NUSSeq2MidConverter(){
			super("buseq", DEFO_FROMSTR_ENG, "mid", DEFO_TOSTR_ENG);
		}

		public void writeAsTargetFormat(String inpath, String outpath)
				throws IOException, UnsupportedFileTypeException {
			writeAsTargetFormat(FileBuffer.createBuffer(inpath, true), outpath);
		}

		public void writeAsTargetFormat(FileBuffer input, String outpath)
				throws IOException, UnsupportedFileTypeException {
			if (input == null) throw new UnsupportedFileTypeException("Input is null");
			input.setEndian(true);
			NUSALSeq nusseq = new NUSALSeq(input);
			try{
				MIDI mid = nusseq.toMidi();
				mid.writeMIDI(outpath);
			}
			catch(InvalidMidiDataException ex){
				ex.printStackTrace();
				throw new UnsupportedFileTypeException("Attempt to convert to midi resulted in invalid midi data");
			}
		}

		public void writeAsTargetFormat(FileNode node, String outpath)
				throws IOException, UnsupportedFileTypeException {
			writeAsTargetFormat(node.loadData(), outpath);
		}
	}
	
	/*--- DEBUG ---*/
	
	public void printMeTo(Writer out) throws IOException{
		//TODO
	}
	
	public static void main(String[] args){
		String inpath = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\n64test\\seq_095.buseq";
		//String logpath = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\n64test\\seq_095_parsetest.out";
		String outpath = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\n64test\\seq_095.mid";
		
		try{
			
			/*FileBuffer dat = FileBuffer.createBuffer(inpath, true);
			NUSALSeqParser parser = new NUSALSeqParser(dat);
			parser.parse();
			
			BufferedWriter bw = new BufferedWriter(new FileWriter(logpath));
			parser.printMeTo(bw);
			bw.close();*/
			
			FileBuffer dat = FileBuffer.createBuffer(inpath, true);
			NUSALSeq seq = new NUSALSeq(dat);
			/*seq.initialize();
			seq.detectLoop();
			System.err.println("DEBUG || Loop: 0x" + Integer.toHexString(seq.loopst) + " - 0x" + Integer.toHexString(seq.looped));*/
			MIDI m = seq.toMidi();
			m.writeMIDI(outpath);
		}
		catch(Exception ex){
			ex.printStackTrace();
			System.exit(1);
		}
	}
	
}
