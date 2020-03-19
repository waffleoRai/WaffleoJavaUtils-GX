package waffleoRai_Sound.nintendo;

import java.util.ArrayList;

import waffleoRai_Containers.nintendo.NDKDSFile;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;

public class DSWarc extends WaveArchive{
	
	public static final String MAGIC = "SWAR";
	
	public static DSWarc readSWAR(FileBuffer file, long pos) throws UnsupportedFileTypeException{

		if(pos != 0) file = file.createReadOnlyCopy(pos, file.getFileSize());
		//Check for magic number
		long cpos = file.findString(0, 0x10, MAGIC);
		if(cpos != 0) throw new FileBuffer.UnsupportedFileTypeException("DSWarc.readSWAR || Magic number \"SWAR\" not found!");
		
		//Break into sections
		NDKDSFile dsfile = NDKDSFile.readDSFile(file);
		FileBuffer data = dsfile.getSectionData("DATA");
		if(data == null) throw new FileBuffer.UnsupportedFileTypeException("DSWarc.readSWAR || DATA section could not be found!");

		//Instantiate archive
		DSWarc swar = new DSWarc();
		
		//Read DATA
		data.setCurrentPosition(8 + (4 * 8)); //Skips section header & reserved
		int wavcount = data.nextInt();
		swar.sounds = new ArrayList<NinWave>(wavcount);
		
		//Read pointer table (offsets are relative to start of SWAR)
		long[] tbl = new long[wavcount];
		for(int i = 0; i < wavcount; i++) tbl[i] = Integer.toUnsignedLong(data.nextInt());
		
		//Read SWAVs
		long len = dsfile.getFileLength();
		for(int i = 0; i < wavcount; i++){
			
			long stoff = tbl[i];
			long edoff = len;
			if(i < tbl.length-1) edoff = tbl[i+1];
			//System.err.println("Reading wav " + i + " (0x" + Long.toHexString(stoff) + " - 0x" + Long.toHexString(edoff) + ")");
			
			FileBuffer wavfile = file.createReadOnlyCopy(stoff, edoff);
			DSWave wav = DSWave.readInternalSWAV(wavfile, 0);
			swar.addSound(wav);
		}
		
		return swar;
	}

}
