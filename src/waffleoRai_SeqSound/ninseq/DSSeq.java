package waffleoRai_SeqSound.ninseq;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Collection;

import javax.sound.midi.Sequence;

import waffleoRai_Containers.nintendo.NDKDSFile;
import waffleoRai_Containers.nintendo.NDKSectionType;
import waffleoRai_SeqSound.MIDI;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;

/*
 * Yes, the name is based off of VGMTrans' DSSeq
 * It's a swell name
 */

public class DSSeq 
{
	
	/*--- Constants ---*/
	
	public static final String MAGIC = "SSEQ";
	public static final String MAGIC_DATA = "DATA";
	
	/*--- Instance Variables ---*/
	
	//private NinSeq seq;
	private NinSeqDataSource seq;
	private short[] playerInitVars;
	
	/*--- Construction/Parsing ---*/
	
	private DSSeq(){playerInitVars = new short[255];}
	
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
	
}
