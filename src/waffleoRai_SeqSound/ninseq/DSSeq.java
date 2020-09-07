package waffleoRai_SeqSound.ninseq;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import javax.sound.midi.Sequence;

import waffleoRai_Containers.nintendo.NDKDSFile;
import waffleoRai_Containers.nintendo.NDKSectionType;
import waffleoRai_Files.Converter;
import waffleoRai_SeqSound.MIDI;
import waffleoRai_SeqSound.SoundSeqDef;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;
import waffleoRai_Files.tree.FileNode;

/*
 * Yes, the name is based off of VGMTrans' DSSeq
 * It's a swell name
 */

public class DSSeq implements NinSeq{
	
	/*--- Constants ---*/
	
	public static final int TYPE_ID = 0x53534551;
	public static final String MAGIC = "SSEQ";
	public static final String MAGIC_DATA = "DATA";
	
	/*--- Instance Variables ---*/
	
	//private NinSeq seq;
	private NinSeqDataSource seq;
	private short[] playerInitVars;
	
	private String name;
	
	/*--- Construction/Parsing ---*/
	
	private DSSeq(){playerInitVars = new short[256];}
	
	public static DSSeq readSSEQ(FileBuffer src) throws UnsupportedFileTypeException, IOException
	{
		if(src == null) return null;
		NDKDSFile ds_file = NDKDSFile.readDSFile(src);
		if(ds_file == null) return null;
		if(!MAGIC.equals(ds_file.getFileIdentifier())) throw new FileBuffer.UnsupportedFileTypeException("DSSeq.readSSEQ || Source data does not begin with SSEQ magic number!");

		DSSeq seq = new DSSeq();
		FileBuffer data_sec = ds_file.getSectionData(NDKSectionType.DATA);
		FileBuffer seqdat = data_sec.createCopy(0xCL, data_sec.getFileSize());
		seq.seq = new NinSeqDataSource(0, seqdat, NinSeq.PLAYER_MODE_DS);
		
		return seq;
	}
	
	/*--- Getters ---*/
	
	public NinSeqDataSource getSequenceData()
	{
		return seq;
	}
	
	public short getVariable(int idx){
		if(idx < 0) return 0;
		if(idx >= 256) return 0;
		return playerInitVars[idx];
	}
	
	public String getName(){return name;}
	
	public int getLabelCount(){return 0;}
	public NinSeqLabel getLabel(int idx){return null;}
	public List<NinSeqLabel> getLabels(){return new LinkedList<NinSeqLabel>();}
	
	/*--- Setters ---*/
	
	public void setVariable(int idx, short val){
		if(idx < 0) return;
		if(idx >= 256) return;
		playerInitVars[idx] = val;
	}
	
	public void setName(String s){s = name;}
	
	/*--- Conversion ---*/
	
	public NinSeqMidiConverter toMIDI(boolean verbose)
	{
		return toMIDI(true, NinSeqMidiConverter.MIDI_IF_MODE_CHECK, NinSeqMidiConverter.MIDI_RANDOM_MODE_RANDOM, NinSeqMidiConverter.MIDI_VAR_MODE_USEVAR, verbose);
	}
	
	public NinSeqMidiConverter toMIDI(boolean allowJumps, int ifMode, int randMode, int varMode, boolean verbose)
	{
		NinSeqMidiConverter converter = new NinSeqMidiConverter(seq, 0);
		
		//Set preferences
		converter.setMidiJumpIgnore(!allowJumps);
		converter.setMidiIfMode(ifMode);
		converter.setMidiRandomMode(randMode);
		converter.setMidiVarMode(varMode);
		for(int i = 0; i < 255; i++) converter.setVariableValue(i, playerInitVars[i]);
		
		//Initialize
		converter.rewind();
		
		//Play and wait for it to finish...
		converter.play();
		
		while(converter.isPlaying())
		{
			try 
			{
				Thread.sleep(5);
			} 
			catch (InterruptedException e) 
			{
				//Assumed to be a cancel signal
				if(verbose) System.err.println("Cancel signal received. MIDI conversion cancelled!");
				converter.pause();
				return converter;
			}
		}
		
		//Print errors found, if verbose
		if(verbose)
		{
			Collection<Exception> errors = converter.getErrors();
			System.err.println("Conversion finished with " + errors.size() + " errors!");
			int i = 0;
			for(Exception e : errors)
			{
				System.err.println("------> ERROR " + i);
				e.printStackTrace();
				i++;
			}
		}
		
		return converter;
	}
	
	public boolean writeMIDI(String path, boolean verbose)
	{
		NinSeqMidiConverter converter = toMIDI(verbose);
		if(converter == null) return false;
		Sequence s = converter.getOutput();
		if(s == null) return false;
		MIDI m = new MIDI(s);
		try 
		{
			m.writeMIDI(path);
		} 
		catch (IOException e) 
		{
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	public boolean writeMIDI(int lblidx, String path, boolean verbose){
		return writeMIDI(path, verbose);
	}
	
	/*--- View ---*/
	
	public boolean printInfo(BufferedWriter outstream)
	{
		try
		{
			seq.printInfo(outstream);
		}
		catch(Exception e)
		{
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	/*--- Definition ---*/
	
	private static NitroSeqDef static_def;
	
	public static NitroSeqDef getDefinition(){
		if(static_def == null) static_def = new NitroSeqDef();
		return static_def;
	}
	
	public static class NitroSeqDef extends SoundSeqDef{

		private static final String DEFO_ENG_STR = "Nitro Sound/Music Sequence";
		private static final String[] EXT_LIST = {"sseq", "SSEQ", "nseq", "bnseq"};
		
		private String str;
		
		public NitroSeqDef(){
			str = DEFO_ENG_STR;
		}
		
		public Collection<String> getExtensions() {
			List<String> list = new ArrayList<String>(EXT_LIST.length);
			for(String s : EXT_LIST)list.add(s);
			return list;
		}

		public String getDescription() {return str;}
		public int getTypeID() {return TYPE_ID;}
		public void setDescriptionString(String s) {str = s;}
		public String getDefaultExtension() {return "sseq";}

	}
	
	/*--- Converter ---*/
	
	private static SSEQConverter cdef;
	
	public static SSEQConverter getDefaultConverter(){
		if(cdef == null) cdef = new SSEQConverter();
		return cdef;
	}
	
	public static class SSEQConverter implements Converter{

		public static final String DEFO_ENG_FROM = "Nitro Sound/Music Sequence (.sseq)";
		public static final String DEFO_ENG_TO = "MIDI Sound Sequence (.mid)";
		
		private String from_desc;
		private String to_desc;
		
		public SSEQConverter(){
			from_desc = DEFO_ENG_FROM;
			to_desc = DEFO_ENG_TO;
		}
		
		public String getFromFormatDescription() {return from_desc;}
		public String getToFormatDescription() {return to_desc;}
		public void setFromFormatDescription(String s) {from_desc = s;}
		public void setToFormatDescription(String s) {to_desc = s;}

		public void writeAsTargetFormat(String inpath, String outpath)
				throws IOException, UnsupportedFileTypeException {
			writeAsTargetFormat(FileBuffer.createBuffer(inpath), outpath);
		}

		public void writeAsTargetFormat(FileBuffer input, String outpath)
				throws IOException, UnsupportedFileTypeException {
			DSSeq seq = DSSeq.readSSEQ(input);
			seq.writeMIDI(outpath, true);
		}
		
		public void writeAsTargetFormat(FileNode node, String outpath) 
				throws IOException, UnsupportedFileTypeException{
			FileBuffer dat = node.loadDecompressedData();
			writeAsTargetFormat(dat, outpath);
		}

		public String changeExtension(String path) {
			if(path == null) return null;
			if(path.isEmpty()) return path;
			
			int lastdot = path.lastIndexOf('.');
			if(lastdot < 0) return path + ".mid";
			return path.substring(0, lastdot) + ".mid";
		}
		
	}
	
	
}
