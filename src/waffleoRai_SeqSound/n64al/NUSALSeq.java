package waffleoRai_SeqSound.n64al;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.Sequence;

import waffleoRai_Files.ConverterAdapter;
import waffleoRai_Files.WriterPrintable;
import waffleoRai_Files.tree.FileNode;
import waffleoRai_SeqSound.BasicMIDIGenerator;
import waffleoRai_SeqSound.MIDI;
import waffleoRai_SeqSound.SeqVoiceCounter;
import waffleoRai_SeqSound.SoundSeqDef;
import waffleoRai_SeqSound.n64al.cmd.NUSALSeqReader;
import waffleoRai_SoundSynth.SequenceController;
import waffleoRai_Threads.SyncedInt;
import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.BufferWriteListener;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;

public class NUSALSeq implements WriterPrintable{
	
	/*----- Constants -----*/
	
	public static final int NUS_TPQN = 48; //240
	
	public static final int EFFECT_ID_REVERB = 1;
	public static final int EFFECT_ID_VIBRATO = 2;
	
	public static final int MIDI_NRPN_ID_REVERB = 0x06d4;
	public static final int MIDI_NRPN_ID_VIBRATO = 0x06d8;
	public static final int MIDI_NRPN_ID_PRIORITY = 0x06e9;
	public static final int MIDI_NRPN_ID_LOOPSTART = 0x06f8;
	public static final int MIDI_NRPN_ID_LOOPEND = 0x06f7;
	//Write vibrato to the mod wheel
	
	public static final int MIN_NOTE_MIDI = 0x15;
	public static final int MAX_NOTE_MIDI = 0x15 + 0x3F;
	
	public static final int MIDI_CTRLR_ID_LEGATO = 68;
	
	public static final int MIDI_NRPN_ID_GENERAL_HI = 0x0600;
	
	public static final byte[] DEFO_SHORTTBL_VEL = 
		{12, 25, 38, 51, 57, 64, 71, 76, 83, 89, 96, 102, 109, 115, 121, 127};
	public static final byte[] DEFO_SHORTTBL_GATE = 
		{(byte)229, (byte)203, (byte)177, (byte)151, (byte)139, 126, 
				113, 100, 87, 74, 61, 48, 36, 23, 10, 0};
	
	/*----- Static Variables -----*/
	
	protected static boolean dbg_w_mode;
	
	/*----- Instance Variables -----*/
	
	private FileBuffer data;
	private NUSALSeqCommandSource source;
	private SequenceController target;
	
	private int master_pos;
	private Deque<Integer> return_stack;
	
	private NUSALSeqCommandMap seq_cmds; //This is filled at first play. Used for viewing commands
	
	private long wait_remain;
	private long current_tick;
	private boolean term_flag;
	
	private Random rng;
	private SyncedInt[] seqIO;
	private SyncedInt var_p = new SyncedInt(0);
	private SyncedInt var_q = new SyncedInt(0);
	
	private int addr_shortgate_tbl = -1;
	private int addr_shortvel_tbl = -1;
	
	private long tick_len = 0;
	
	private boolean error_flag;
	private int error_addr;
	private String error_msg;
	private int error_ch;
	
	private int tempo; //bpm
	private byte master_vol;
	private byte master_exp;
	
	private boolean[] ch_enable;
	private NUSALSeqChannel[] channels;
	
	private boolean loop_enabled;
	private int lcounter; //Keeps track of how many times loop has occured
	private int loopst;
	private int looped;
	private int loopct;
	private boolean jumpLoop; //Set if the loop uses a branch/jump instead of loop commands
	
	private boolean anno_enable;
	
	private Writer[] dbg_ch_w;
	
	/*----- Init -----*/
	
	private NUSALSeq(){
		//data = srcdat.createCopy(0, srcdat.getFileSize());
		//data.setEndian(true);
		ch_enable = new boolean[16];
		channels = new NUSALSeqChannel[16];
		return_stack = new LinkedList<Integer>();
		seqIO = new SyncedInt[16];
		for(int i = 0; i < 16; i++) seqIO[i] = new SyncedInt(0);
		rng = new Random();
		reset();
		//initialize();
	}
		
	public void reset(){
		master_pos = 0;
		return_stack.clear();
		wait_remain = 0;
		term_flag = false;
		error_flag = false;
		tempo = 120;
		master_vol = 0x7f;
		master_exp = 0x7f;
		error_msg = null; error_addr = -1; error_ch = -1;
		tick_len = 0;
		
		for(int i = 0; i < 16; i++){
			ch_enable[i] = false;
			//channels[i] = new NUSALSeqChannel(i);
			if(channels[i] != null) channels[i].reset();
			seqIO[i].set(0);
		}
		
		var_p.set(0);
		var_q.set(0);
		loop_enabled = false;
		lcounter = 0;
		loopst = -1;
		looped = -1;
		loopct = -1;
		jumpLoop = false;
	}
	
	public void initialize(){
		//Creates channel objects and parses commands
		seq_cmds = null;
		for(int i = 0; i < 16; i++){
			if(channels[i] == null) channels[i] = new NUSALSeqChannel(i, this);
			else channels[i].reset();
			channels[i].setCommandSource(source);
		}
		master_pos = 0;
		detectLoop();
	}
	
	private void parseDataForCommands(){
		NUSALSeqReader parser = new NUSALSeqReader(data);
		parser.preParse();
		source = parser;
	}
	
	private void serializeCommandsToData(){
		if(source == null) return;
		data = new FileBuffer(source.getMinimumSizeInBytes() + 64, true);
		List<NUSALSeqCommand> commands = source.getOrderedCommands();
		for(NUSALSeqCommand cmd : commands){
			cmd.serializeTo(data);
		}
		int mod = (int)data.getFileSize() & 0xf;
		if(mod != 0){
			int pad = 16 - mod;
			for(int i = 0; i < pad; i++) data.addToFile(FileBuffer.ZERO_BYTE);
		}
	}
	
	public static NUSALSeq readNUSALSeq(FileBuffer srcdat){
		NUSALSeq seq = new NUSALSeq();
		try{
			seq.data = srcdat.createCopy(0, srcdat.getFileSize());
			seq.data.setEndian(true);
		}
		catch(IOException ex){
			ex.printStackTrace();
			return null;
		}
		seq.parseDataForCommands();
		seq.initialize();
		return seq;
	}
	
	public static NUSALSeq readMMLScript(BufferedReader reader) throws UnsupportedFileTypeException, IOException{
		NUSALSeq seq = new NUSALSeq();
		NUSALSeqReader rdr = NUSALSeqReader.readMMLScript(reader);
		seq.data = rdr.getData();
		seq.source = rdr;
		seq.initialize();
		return seq;
	}
	
	public static NUSALSeq newNUSALSeq(NUSALSeqCommandSource cmdsrc){
		NUSALSeq seq = new NUSALSeq();
		seq.source = cmdsrc;
		seq.serializeCommandsToData();
		seq.initialize();
		
		return seq;
	}
	
	/*----- Getters -----*/
	
	public BufferReference getSeqDataReference(int abs_addr){
		BufferReference ref = data.getReferenceAt(abs_addr);
		ref.addWriteListener(new BufferWriteListener(){
			public void onBufferWrite(long pos, int bytes) {
				onDataModification((int)pos, bytes);
			}});
		return ref;
	}
	
	public int getCurrentPosition(){return master_pos;}
	
	public int getVarQ(){return var_q.get();}
	
	public int getVarP(){return var_p.get();}
	
	public int getSeqIOValue(int idx){return seqIO[idx].get();}
	
	public boolean hasJumpLoop(){return jumpLoop;}
	
	public NUSALSeqChannel getChannel(int idx){
		if(channels == null || idx < 0 || idx >= channels.length) return null;
		return channels[idx];
	}
	
	public NUSALSeqCommandMap getCommandTickMap(){
		if(seq_cmds == null) playToMapCommands();
		return seq_cmds;
	}
	
	public NUSALSeqCommandSource getAllCommands(){
		if(source == null) initialize();
		return source;
	}
	
	public boolean channelEnabled(int idx){
		return ch_enable[idx];
	}
	
	public long getLengthInTicks(){return tick_len;}
	
	public Random getRNG(){
		if(rng == null) rng = new Random();
		return rng;
	}
	
	public byte getShortTableVelocityAt(int idx){
		if(addr_shortvel_tbl <= 0) return DEFO_SHORTTBL_VEL[idx];
		return data.getByte(addr_shortvel_tbl + idx);
	}
	
	public byte getShortTableGateAt(int idx){
		if(addr_shortgate_tbl <= 0) return DEFO_SHORTTBL_GATE[idx];
		return data.getByte(addr_shortgate_tbl + idx);
	}
	
	public byte[] generateVoiceUsageTable(){
		SeqVoiceCounter vc = new SeqVoiceCounter();
		playTo(vc, false);
		return vc.getVoiceUsageTotal();
	}
	
	public void generateVoiceUsageTable(Writer csv_writer) throws IOException{
		byte[] usage_tbl = generateVoiceUsageTable();
		csv_writer.write("TICK,VOXCOUNT\n");
		for(int i = 0; i < usage_tbl.length; i++){
			csv_writer.write(Integer.toString(i));
			csv_writer.write(",");
			csv_writer.write(Integer.toString(Byte.toUnsignedInt(usage_tbl[i])));
			csv_writer.write("\n");
		}
	}
	
	public NUSALSeqCommandSource getCommandSource(){return this.source;}
	
	/*----- Setters -----*/
	
	public void setTarget(SequenceController t){target = t;}
	public void setLoopEnabled(boolean b){loop_enabled = b;}
	public void setAnnotationsEnabled(boolean b){anno_enable = b;}
	
	public void setVarQ(int value){
		//Clamp
		value = value>127?127:value;
		value = value<-128?-128:value;
		var_q.set(value);
	}
	
	public void setVarP(int value){
		//Clamp
		value = value>65536?65536:value;
		value = value<0?0:value;
		var_p.set(value);;
	}
	
	public void setSeqIOValue(int idx, int value){
		seqIO[idx].set(value);
	}
	
	public void setShortTable_Gate(int address){
		addr_shortgate_tbl = address;
	}
	
	public void setShortTable_Velocity(int address){
		addr_shortvel_tbl = address;
	}
	
	public void mapCommandToTick(int tick, NUSALSeqCommand cmd){
		if(seq_cmds == null) seq_cmds = new NUSALSeqCommandMap();
		seq_cmds.addCommand(tick, cmd);
	}
	
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
	
	private void onDataModification(int pos, int len){
		//Reparse that region.
		source.reparseRegion(pos, len);
	}
	
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
	
	public void setMasterExpression(byte vol){
		master_exp = vol;
		if(target != null){
			target.setMasterExpression(master_exp);
		}
	}
	
	public void setMasterFade(int mode, int time){
		//TODO
	}
	
	public void setTempo(int bpm){
		tempo = bpm;
		if(target != null){
			target.setTempo(MIDI.bpm2uspqn(tempo, NUS_TPQN));
		}
	}
	
	public void setTempoVariance(int value){
		//TODO
	}
	
	public void breakLoop(){
		if(!return_stack.isEmpty()){
			return_stack.pop();
		}
	}
	
	public void stopChannel(int channel){
		if(channels[channel] != null){
			channels[channel].signalReadEnd();
		}
	}
	
	public void transposeBy(int semis_change){
		//TODO
		//Transpose delta
	}
	
	public void setTranspose(int semis){
		//TODO
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
			return true;
		}
		
		if(push_return) return_stack.push(master_pos+3);
		master_pos = pos;
		//System.err.println("seq jumped to 0x" + Integer.toHexString(master_pos));
		
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
	
	public boolean nextTick(boolean savecmd){
		if(term_flag || error_flag) return false;
		
		//Seq Itself
		if(wait_remain > 0) wait_remain--;
		while(wait_remain <= 0 && !term_flag){
			if(source == null) return false;
			if(anno_enable && jumpLoop && (master_pos == loopst) && (target != null)){
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
			if(savecmd) seq_cmds.addCommand((int)current_tick, cmd);
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
			if(!channels[i].nextTick(savecmd, (int)current_tick)){
				error_flag = true;
				error_addr = -1;
				error_ch = i;
				error_msg = "Error encountered on channel " + i;
				return false;
			}
		}
		
		return !term_flag;
	}

	private void playToMapCommands(){
		//Used to sort commands into channels/voices/seq etc. 
		//	mapped to tick
		reset();
		initialize();
		detectLoop();
		
		seq_cmds = new NUSALSeqCommandMap();
		
		final int MAX_TICKS = 10 * 255 * NUS_TPQN;
		current_tick = 0;
		while(nextTick(true) && (current_tick < MAX_TICKS)){
			current_tick++;
		}
		tick_len = current_tick;
	}
	
	public void playTo(SequenceController listener, boolean loop){
		reset();
		initialize();
		setLoopEnabled(loop);
		detectLoop();
		//System.err.println("loopst = " + Integer.toHexString(loopst));
		//System.err.println("looped = " + Integer.toHexString(looped));
		
		target = listener;
		for(int i = 0; i < 16; i++) channels[i].setListener(target);
		
		final int MAX_TICKS = 10 * 255 * NUS_TPQN; //10 min @ 255 bpm (max)
		current_tick = 0;
		while(nextTick(false) && (!loop || (current_tick < MAX_TICKS))){
			current_tick++;
			if(listener != null) listener.advanceTick();
			//System.err.println("ticks = " + ticks);
		}
		
		if(error_flag){
			//Print error message!
			System.err.println("NUSALSeq.playTo -- ERROR at tick " + current_tick);
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
		
		if(!loop && current_tick >= MAX_TICKS){
			 System.err.println("Warning -- Playback terminated after " + current_tick + " ticks due to possible undetected infinite loop.");
		}
	}
	
	/*----- Conversion -----*/

	public MIDI toMidi() throws InvalidMidiDataException{
		BasicMIDIGenerator conv = new BasicMIDIGenerator(NUS_TPQN);
		anno_enable = true;
		playTo(conv, false);
		conv.complete();
		Sequence seq = conv.getSequence();
		MIDI mid = new MIDI(seq);
		
		return mid;
	}
	
	public void exportMMLScript(Writer writer) throws IOException{
		exportMMLScript(writer, false);
	}
	
	public void exportMMLScript(Writer writer, boolean note_addr) throws IOException{
		if(writer == null) return;
		writer.write("; Nintendo 64 MML\n");
		writer.write("; Auto output by waffleoUtilsGX\n\n");
		
		List<NUSALSeqCommand> commands = source.getOrderedCommands();
		for(NUSALSeqCommand cmd : commands){
			if(cmd.getLabel() != null){
				writer.write(cmd.getLabel());
				writer.write(":\n");
			}
			writer.write("\t");
			writer.write(cmd.toMMLCommand(note_addr));
			writer.write("\n");
		}
	}
	
	public FileBuffer getSerializedData(){
		if(data == null) return null;
		int fsize = (int)data.getFileSize();
		int outsize = (fsize + 0xF) & 0xF;
		FileBuffer buff = new FileBuffer(outsize, true);
		for(int i = 0; i < fsize; i++) buff.addToFile(data.getByte(i));
		int padding = outsize - fsize;
		for(int i = 0; i < padding; i++) buff.addToFile(FileBuffer.ZERO_BYTE);
		return buff;
	}
	
	public void writeTo(OutputStream out) throws IOException{
		if(out == null) return;
		data.writeToStream(out);
		int mod = (int)data.getFileSize() & 0xF;
		if(mod != 0){
			int pad = 16 - mod;
			for(int i = 0; i < pad; i++) out.write(0);
		}
	}
	
	public void writeTo(String filepath) throws IOException{
		if(filepath == null) return;
		data.writeFile(filepath);
	}
	
	/*----- Exceptions -----*/
	
	public static class UnrecognizedCommandException extends RuntimeException{

		private static final long serialVersionUID = -901207269382175934L;
		
		public UnrecognizedCommandException(byte b, int pos, String ctx){
			super("Command 0x" + String.format("%02x", b) + 
					" @ 0x" + Integer.toHexString(pos) + " not recognized in " + ctx + " context!");
		}
	}
	
	public static class TooManyVoicesException extends RuntimeException{
		private static final long serialVersionUID = 4940386212055346600L;
		
		public TooManyVoicesException(String msg){
			super(msg);
		}
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
			NUSALSeq nusseq = NUSALSeq.readNUSALSeq(input);
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
	
	protected void dbgtt_writeln(int ch, String str){
		if(!NUSALSeq.dbg_w_mode) return;
		try{
			dbg_ch_w[ch].write(Long.toString(this.current_tick));
			dbg_ch_w[ch].write("\t");
			dbg_ch_w[ch].write(str);
			dbg_ch_w[ch].write("\n");
		}
		catch(Exception ex){
			ex.printStackTrace();
		}
	}
	
	protected void openDebugTickTables(String pathstem) throws IOException{
		dbg_ch_w = new Writer[16];
		for(int i = 0; i < 16; i++){
			dbg_ch_w[i] = new BufferedWriter(new FileWriter(pathstem + "_dbgtt_ch" + String.format("%02d", i) + ".tsv"));
		}
		NUSALSeq.dbg_w_mode = true;
	}
	
	protected void closeDebugTickTables() throws IOException{
		if(dbg_ch_w == null) return;
		for(int i = 0; i < 16; i++){
			if(dbg_ch_w[i] != null) dbg_ch_w[i].close();
		}
		dbg_ch_w = null;
		NUSALSeq.dbg_w_mode = false;
	}
	
	public void writeDebugTickTables(String pathstem) throws IOException{
		openDebugTickTables(pathstem);
		playTo(null, false);
		closeDebugTickTables();
	}
	
	public void printMeTo(Writer out) throws IOException{
		if(source == null) {out.write("<Seq empty>"); return;}
		source.printMeTo(out);
	}
	
	public static void main(String[] args){
		
		String inpath = args[0];
		String outstem = args[1];
		//String muspath = args[1];
		//String outpath = args[2];
		
		String muspath = outstem + ".mus";
		String midpath = outstem + ".mid";
		String checkpath = outstem + "_mmlcheck.useq";
		
		try{
			
			/*FileBuffer dat = FileBuffer.createBuffer(inpath, true);
			NUSALSeqParser parser = new NUSALSeqParser(dat);
			parser.parse();
			
			BufferedWriter bw = new BufferedWriter(new FileWriter(logpath));
			parser.printMeTo(bw);
			bw.close();*/
			
			FileBuffer dat = FileBuffer.createBuffer(inpath, true);
			NUSALSeq seq = NUSALSeq.readNUSALSeq(dat);
			
			BufferedWriter bw = new BufferedWriter(new FileWriter(muspath));
			//seq.printMeTo(bw);
			seq.exportMMLScript(bw);
			bw.close();
			
			MIDI m = seq.toMidi();
			m.writeMIDI(midpath);
			
			//Try reading MML script back in
			BufferedReader mmlrdr = new BufferedReader(new FileReader(muspath));
			seq = NUSALSeq.readMMLScript(mmlrdr);
			mmlrdr.close();
			seq.writeTo(checkpath);
			
			//Try reading midi back in?
		}
		catch(Exception ex){
			ex.printStackTrace();
			System.exit(1);
		}
	}
	
}
