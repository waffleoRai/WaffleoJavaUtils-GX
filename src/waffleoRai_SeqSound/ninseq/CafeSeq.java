package waffleoRai_SeqSound.ninseq;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sound.midi.Sequence;

import waffleoRai_Containers.nintendo.NDKCafeFile;
import waffleoRai_Containers.nintendo.NDKSectionType;
import waffleoRai_SeqSound.MIDI;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;

public class CafeSeq {

	/*--- Constants ---*/
	
	public static final String MAGIC = "FSEQ";
	public static final String MAGIC_LABL = "LABL";
	public static final String MAGIC_DATA = "DATA";
	
	/*--- Instance Variables ---*/

	private NinSeqDataSource seqdata;
	private short[] playerInitVars;
	
	private Map<String, LabelSeq> subSeqs; //Where labels point to
	private String[] labels;
	
	/*--- Construction/Parsing ---*/
	
	private CafeSeq()
	{
		subSeqs = new HashMap<String, LabelSeq>();
		playerInitVars = new short[255];
	}
	
	public static CafeSeq readFSEQ(FileBuffer src) throws UnsupportedFileTypeException, IOException
	{
		if(src == null) return null;
		NDKCafeFile cfe_file = NDKCafeFile.readCafeFile(src);
		if(cfe_file == null) return null;
		if(!MAGIC.equals(cfe_file.getFileIdentifier())) throw new FileBuffer.UnsupportedFileTypeException("CafeSeq.readFSEQ || Source data does not begin with FSEQ magic number!");
		
		//DATA (Raw)
		FileBuffer data_sec = cfe_file.getSectionData(NDKSectionType.DATA);
		FileBuffer data = data_sec.createCopy(0xCL, data_sec.getFileSize());
		
		CafeSeq seq = new CafeSeq();
		seq.seqdata = new NinSeqDataSource(0, data, NinSeq.PLAYER_MODE_CAFE);
		//seq.seqdata.freeSourceData();
		
		//LABL
		FileBuffer labl_sec = cfe_file.getSectionData(NDKSectionType.LABL);
		long cpos = 8L;
		int lcount = labl_sec.intFromFile(cpos); cpos += 4;
		seq.labels = new String[lcount];
		for(int i = 0; i < lcount; i++)
		{
			long lpos = Integer.toUnsignedLong(labl_sec.intFromFile(cpos)); cpos += 4;
			lpos += 8L;
			long seqoff = Integer.toUnsignedLong(labl_sec.intFromFile(lpos)); lpos += 4;
			int slen = labl_sec.intFromFile(lpos); lpos += 4;
			String lname = labl_sec.getASCII_string(lpos, slen);
			LabelSeq lseq = new LabelSeq(lname, seqoff);
			seq.subSeqs.put(lname, lseq);
			seq.labels[i] = lname;
		}

		return seq;
	}
	
	/*--- Getters ---*/
	
	public NinSeqDataSource getSequenceData()
	{
		return this.seqdata;
	}
	
	public long getLabelOffset(String label)
	{
		LabelSeq sub = subSeqs.get(label);
		if(sub == null) return -1;
		return sub.getRawOffset();
	}

	public LabelSeq getRawLabelInfo(String label)
	{
		return subSeqs.get(label);
	}
	
	/*--- Sequence Manipulation ---*/
	
	public void setInitialRegisterValue(int index, short value)
	{
		if(index < 0 || index > 255) return;
		playerInitVars[index] = value;
	}
	
	public void clearInitialRegisterValues()
	{
		playerInitVars = new short[255];
	}

	public String getLabel(int labelIndex)
	{
		if(labelIndex < 0 || labelIndex >= labels.length) return null;
		return labels[labelIndex];
	}
	
	public String getLabelAtAddress(int labelAddr)
	{
		for(LabelSeq ls : subSeqs.values())
		{
			if (ls.getRawOffset() == Integer.toUnsignedLong(labelAddr)) return ls.getName();
		}
		return null;
	}	
	
	public List<String> getLabels()
	{
		List<String> slist = new ArrayList<String>(labels.length + 1);
		for(String l : labels) slist.add(l);
		return slist;
	}
	
	/*--- Conversion ---*/
	
	public boolean hasPrefixCommands()
	{
		return seqdata.hasPrefixCommands();
	}
	
	public boolean hasPrefixCommandsAfterLabel(String lbl)
	{
		LabelSeq ls = this.subSeqs.get(lbl);
		if(ls == null) return false;
		return seqdata.hasPrefixCommandsBetween(ls.getRawOffset(), seqdata.getMaxAddress());
	}
	
	public NinSeqMidiConverter toMIDI(boolean verbose)
	{
		return toMIDI(true, NinSeqMidiConverter.MIDI_IF_MODE_CHECK, NinSeqMidiConverter.MIDI_RANDOM_MODE_RANDOM, NinSeqMidiConverter.MIDI_VAR_MODE_USEVAR, verbose);
	}
	
	public NinSeqMidiConverter toMIDI(boolean allowJumps, int ifMode, int randMode, int varMode, boolean verbose)
	{
		NinSeqMidiConverter converter = new NinSeqMidiConverter(seqdata, 0);
		
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
	
	public NinSeqMidiConverter toMIDI(String label, boolean verbose)
	{
		return toMIDI(label, true, NinSeqMidiConverter.MIDI_IF_MODE_CHECK, NinSeqMidiConverter.MIDI_RANDOM_MODE_RANDOM, NinSeqMidiConverter.MIDI_VAR_MODE_USEVAR, verbose);
	}
	
	public NinSeqMidiConverter toMIDI(String label, boolean allowJumps, int ifMode, int randMode, int varMode, boolean verbose)
	{
		LabelSeq ls = this.getRawLabelInfo(label);
		if(ls == null) return null;
		NinSeqMidiConverter converter = new NinSeqMidiConverter(seqdata, ls.getRawOffset());
		
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
	
	public boolean writeMIDI(String label, String path, boolean verbose)
	{
		NinSeqMidiConverter converter = toMIDI(label, verbose);
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

	/*--- View ---*/
	
	public boolean printSeqInfo(BufferedWriter outstream)
	{
		try
		{
			seqdata.printInfo(outstream);
		}
		catch(Exception e)
		{
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	public void printLabels()
	{
		for(int i = 0; i < labels.length; i++)
		{
			if(labels[i] != null)
			{
				LabelSeq ls = this.subSeqs.get(labels[i]);
				if(ls != null)
				{
					System.out.println(i + "\t" + labels[i] + "\t0x" + Long.toHexString(ls.getRawOffset()));
				}
				else System.out.println(i + "\t" + labels[i] + "\t[NULL]");
			}
			else System.out.println(i + "\t[NULL]");
		}
	}
	
	public void printLabelsAddrOrder()
	{
		List<LabelSeq> subs = new ArrayList<LabelSeq>(subSeqs.size() + 1);
		subs.addAll(subSeqs.values());
		Collections.sort(subs);
		for(LabelSeq ls : subs)
		{
			System.out.println(ls.getName() + "\t0x" + Long.toHexString(ls.getRawOffset()));
		}
	}
	
}
