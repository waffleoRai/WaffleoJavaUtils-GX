package waffleoRai_SeqSound.ninseq;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.sound.midi.Sequence;

import waffleoRai_Containers.nintendo.NDKDSFile;
import waffleoRai_Containers.nintendo.sar.DSSoundArchive;
import waffleoRai_Files.Converter;
import waffleoRai_SeqSound.MIDI;
import waffleoRai_SeqSound.SoundSeqDef;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileNode;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;

public class DSMultiSeq {
	
	/*--- Constants ---*/
	
	public static final int TYPE_ID = 0x4e534551;
	public static final String MAGIC = "SSAR";
	public static final String MAGIC_DATA = "DATA";
	
	/*--- Instance Variables ---*/
	
	private NinSeqDataSource seq;
	private ArrayList<NinSeqLabel> labels;
	
	/*--- Construction/Parsing ---*/
	
	private DSMultiSeq(){}
	
	public static DSMultiSeq readSSAR(FileBuffer dat, long offset) throws UnsupportedFileTypeException, IOException{

		dat.setEndian(true);
		dat.setCurrentPosition(offset);
		
		//Check for magic number
		long mpos = dat.findString(offset, offset+0x10, MAGIC);
		if(mpos != offset) throw new FileBuffer.UnsupportedFileTypeException("DSMultiSeq.readSSAR || SSAR magic number not found!");
		
		//Read as DSFile
		NDKDSFile file = NDKDSFile.readDSFile(dat.createReadOnlyCopy(offset, dat.getFileSize()));
		FileBuffer data = file.getSectionData(MAGIC_DATA);
		data.setCurrentPosition(8); //Data offset
		long datstart = Integer.toUnsignedLong(data.nextInt());
		int lblcount = data.nextInt();
		DSMultiSeq ssar = new DSMultiSeq();
		ssar.labels = new ArrayList<NinSeqLabel>(lblcount+1);
		
		//Label data
		for(int i = 0; i < lblcount; i++){
			String name = "seq_" + String.format("%03d", i);
			NinSeqLabel lbl = new NinSeqLabel(name, 0);
			lbl.setAddress(Integer.toUnsignedLong(data.nextInt()));
			lbl.setBankID(Short.toUnsignedInt(data.nextShort()));
			lbl.setVolume(data.nextByte());
			lbl.setChannelPressure(data.nextByte());
			lbl.setPolyPressure(data.nextByte());
			lbl.setPlayer(data.nextByte());
			data.skipBytes(2); //Reserved
			ssar.labels.add(lbl);
		}
		
		//Seq data
		FileBuffer seqdat = data.createCopy(datstart, data.getFileSize());
		ssar.seq = new NinSeqDataSource(0, seqdat, NinSeq.PLAYER_MODE_DS);
		
		return ssar;
	}
	
	
	/*--- Getters ---*/
	
	public NinSeqDataSource getSequenceData(){
		return seq;
	}
	
	public int getLabelCount(){
		return labels.size();
	}
	
	public NinSeqLabel getLabel(int idx){
		if(idx < 0) return null;
		if(idx >= labels.size()) return null;
		return labels.get(idx);
	}
	
	public List<NinSeqLabel> getLabels(){
		List<NinSeqLabel> copy = new ArrayList<NinSeqLabel>(labels.size());
		copy.addAll(labels);
		return copy;
	}
	
	/*--- Conversion ---*/
	
	public NinSeqMidiConverter toMIDI(int lblidx, boolean verbose)
	{
		return toMIDI(lblidx, true, NinSeqMidiConverter.MIDI_IF_MODE_CHECK, NinSeqMidiConverter.MIDI_RANDOM_MODE_RANDOM, NinSeqMidiConverter.MIDI_VAR_MODE_USEVAR, verbose);
	}
	
	public NinSeqMidiConverter toMIDI(int lblidx, boolean allowJumps, int ifMode, int randMode, int varMode, boolean verbose)
	{
		
		//Get label
		NinSeqLabel lbl = getLabel(lblidx);
		if(lbl == null){
			if(verbose) System.err.println("Label with index " + lblidx + " could not be found!");
			return null;
		}
		
		NinSeqMidiConverter converter = new NinSeqMidiConverter(seq, lbl.getAddress());
		
		//Set preferences
		converter.setMidiJumpIgnore(!allowJumps);
		converter.setMidiIfMode(ifMode);
		converter.setMidiRandomMode(randMode);
		converter.setMidiVarMode(varMode);
		//for(int i = 0; i < 255; i++) converter.setVariableValue(i, playerInitVars[i]);
		
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
	
	public boolean writeMIDI(int lblidx, String path, boolean verbose)
	{
		NinSeqMidiConverter converter = toMIDI(lblidx, verbose);
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
	
	public boolean writeAllToMIDI(String dirpath, boolean verbose) throws IOException{

		if(!FileBuffer.directoryExists(dirpath)){
			Files.createDirectories(Paths.get(dirpath));
		}
		
		boolean b = true;
		int lcount = getLabelCount();
		for(int i = 0; i < lcount; i++){
			NinSeqLabel lbl = getLabel(i);
			if(lbl == null){
				b = false;
				continue;
			}
			
			String outname = dirpath + File.separator + lbl.getName() + ".mid";
			b = b && writeMIDI(i, outname, verbose);
		}
		
		return b;
	}
	
	/*--- Definition ---*/
	
	private static NitroMultiSeqDef static_def;
	
	public static NitroMultiSeqDef getDefinition(){
		if(static_def == null) static_def = new NitroMultiSeqDef();
		return static_def;
	}
	
	public static class NitroMultiSeqDef extends SoundSeqDef{

		private static final String DEFO_ENG_STR = "Nitro Sound/Music Multi-Sequence";
		private static final String[] EXT_LIST = {"ssar", "SSAR", "nseq", "bnseq"};
		
		private String str;
		
		public NitroMultiSeqDef(){
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
		public String getDefaultExtension() {return "ssar";}

	}
	
	/*--- Converter ---*/
	
	private static SSARConverter cdef;
	
	public static SSARConverter getDefaultConverter(){
		if(cdef == null) cdef = new SSARConverter();
		return cdef;
	}
	
	public static class SSARConverter implements Converter{

		public static final String DEFO_ENG_FROM = "Nitro Sound/Music Multi-Sequence (.ssar)";
		public static final String DEFO_ENG_TO = "MIDI Archive";
		
		private String from_desc;
		private String to_desc;
		
		public SSARConverter(){
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
			DSMultiSeq ssar = DSMultiSeq.readSSAR(input, 0);
			ssar.writeAllToMIDI(outpath, true);
		}
		
		public void writeAsTargetFormat(FileNode node, String outpath) 
				throws IOException, UnsupportedFileTypeException{
			FileBuffer dat = node.loadDecompressedData();
			DSMultiSeq ssar = DSMultiSeq.readSSAR(dat, 0);
			//Add label data
			String nraw = node.getMetadataValue(DSSoundArchive.FNMETAKEY_SSARNAMES);
			if(nraw != null){
				String[] subnames = nraw.split(",");
				for(int i = 0; i < subnames.length; i++){
					NinSeqLabel lbl = ssar.getLabel(i);
					if(lbl != null)lbl.setName(subnames[i]);
				}
			}
			ssar.writeAllToMIDI(outpath, true);
		}

		public String changeExtension(String path) {
			if(path == null) return null;
			if(path.isEmpty()) return path;
			
			int lastdot = path.lastIndexOf('.');
			if(lastdot < 0) return path;
			return path.substring(0, lastdot);
		}
		
	}
	
	
	
}
