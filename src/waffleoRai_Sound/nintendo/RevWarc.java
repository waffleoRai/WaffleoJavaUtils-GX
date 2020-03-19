package waffleoRai_Sound.nintendo;

import java.io.IOException;
import java.util.ArrayList;

import waffleoRai_Containers.nintendo.NDKRevolutionFile;
import waffleoRai_Containers.nintendo.NDKSectionType;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;

public class RevWarc extends WaveArchive{
	
	/*--- Constants ---*/
	
	public static final String MAGIC = "RWAR";
	
	/*--- Construction/Parsing ---*/
	
	private RevWarc(int initSize)
	{
		sounds = new ArrayList<NinWave>(initSize + 1);
	}
	
	public static RevWarc readRWAR(FileBuffer src, long stoff) throws UnsupportedFileTypeException, IOException
	{
		if(src == null) return null;
		NDKRevolutionFile rev_file = NDKRevolutionFile.readRevolutionFile(src);
		if(rev_file == null) return null;
		if(!MAGIC.equals(rev_file.getFileIdentifier())) throw new FileBuffer.UnsupportedFileTypeException("RevWarc.readRWAR || Source data does not begin with RWAR magic number!");
		
		FileBuffer tabl_sec = rev_file.getSectionData(NDKSectionType.TABL);
		FileBuffer data_sec = rev_file.getSectionData(NDKSectionType.DATA);
		long data_off = rev_file.getOffsetToSection(NDKSectionType.DATA);
		
		long cpos = 8L; //In table
		int entries = tabl_sec.intFromFile(cpos); cpos += 4;
		RevWarc arc = new RevWarc(entries);
		//System.err.println("RWAR Entry Count: " + entries);
		for(int i = 0; i < entries; i++)
		{
			cpos+=4; //Marker
			//System.err.println("Sound " + i);
			long off = Integer.toUnsignedLong(tabl_sec.intFromFile(cpos)); cpos += 4;
			long sz = Integer.toUnsignedLong(tabl_sec.intFromFile(cpos)); cpos += 4;
			//System.err.println("Offset: 0x" + Long.toHexString(off));
			//System.err.println("Size: 0x" + Long.toHexString(sz));
			off += data_off;
			FileBuffer rwav = data_sec.createReadOnlyCopy(off, off+sz);
			RevWave wav = RevWave.readRWAV(rwav);
			arc.sounds.add(wav);
		}
		
		return arc;
	}
	
	public static RevWarc createEmptyRWAR(int initSize)
	{
		return new RevWarc(initSize);
	}


}
