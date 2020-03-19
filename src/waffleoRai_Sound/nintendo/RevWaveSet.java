package waffleoRai_Sound.nintendo;

import java.util.ArrayList;
import java.util.List;

import waffleoRai_Containers.nintendo.NDKRevolutionFile;
import waffleoRai_Containers.nintendo.NDKSectionType;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;
import waffleoRai_soundbank.nintendo.NinTone;

public class RevWaveSet extends NinWaveSet{
	
	/*--- Constants ---*/
	
	public static final String MAGIC = "RWSD";
	
	/*--- Construction/Parsing ---*/
	
	private RevWaveSet(int initEntries)
	{
		entries = new ArrayList<WaveInfo>(initEntries+1);
	}
	
	public static RevWaveSet createEmptyRWSD(int initEntries)
	{
		return new RevWaveSet(initEntries);
	}
	
	public static RevWaveSet readRWSD(FileBuffer src, long stoff) throws UnsupportedFileTypeException
	{
		if(src == null) return null;
		NDKRevolutionFile rev_file = NDKRevolutionFile.readRevolutionFile(src);
		if(rev_file == null) return null;
		if(!MAGIC.equals(rev_file.getFileIdentifier())) throw new FileBuffer.UnsupportedFileTypeException("RevWaveSet.readRWSD || Source data does not begin with RWSD magic number!");
		
		FileBuffer data_sec = rev_file.getSectionData(NDKSectionType.DATA);
		
		//Initialize
		long cpos = 8L;
		int wi_entries = data_sec.intFromFile(cpos); cpos += 4;
		//System.err.println("Wave Information Entries: " + wi_entries);
		RevWaveSet waveset = new RevWaveSet(wi_entries);
		for(int i = 0; i < wi_entries; i++)
		{
			//System.err.println("Wave Information Entry " + i);
			cpos+=4; //Marker
			long wi_off = Integer.toUnsignedLong(data_sec.intFromFile(cpos)); cpos += 4;
			//System.err.println("Wave Info Offset: 0x" + Long.toHexString(wi_off));
			long woff = wi_off + 8L;
			woff += 4; //First marker
			long wie_off = Integer.toUnsignedLong(data_sec.intFromFile(woff)); woff+=8;
			long net_off = Integer.toUnsignedLong(data_sec.intFromFile(woff)); woff+=8;
			long nit_off = Integer.toUnsignedLong(data_sec.intFromFile(woff));
			//System.err.println("Wave Info Entry Offset: 0x" + Long.toHexString(wie_off));
			//System.err.println("Note Event Table Offset: 0x" + Long.toHexString(net_off));
			//System.err.println("Note Info Table Offset: 0x" + Long.toHexString(nit_off));
			
			wie_off += 8L;
			net_off += 8L;
			nit_off += 8L;
			
			int neg_count = data_sec.intFromFile(net_off); net_off+=4;
			int ni_count = data_sec.intFromFile(nit_off); nit_off+=4;
			//System.err.println("Note Event Count: " + neg_count);
			//System.err.println("Note Info Count: " + ni_count);
			
			WaveInfo winfo = new WaveInfo(neg_count, ni_count);
			
			//Read wi table
			winfo.pitch = Float.intBitsToFloat(data_sec.intFromFile(wie_off)); wie_off += 4;
			winfo.pan = Byte.toUnsignedInt(data_sec.getByte(wie_off)); wie_off++;
			winfo.surroundPan = Byte.toUnsignedInt(data_sec.getByte(wie_off)); wie_off++;
			winfo.fxSendA = Byte.toUnsignedInt(data_sec.getByte(wie_off)); wie_off++;
			winfo.fxSendB = Byte.toUnsignedInt(data_sec.getByte(wie_off)); wie_off++;
			winfo.fxSendC = Byte.toUnsignedInt(data_sec.getByte(wie_off)); wie_off++;
			winfo.mainSend = (int)data_sec.getByte(wie_off); wie_off++;
			//System.err.println("Pitch: " + winfo.pitch);
			//System.err.println("Pan: 0x" + String.format("%02x", winfo.pan));
			//System.err.println("Surround Pan: 0x" + String.format("%02x", winfo.surroundPan));
			//System.err.println("FX Send A: 0x" + String.format("%02x", winfo.fxSendA));
			//System.err.println("FX Send B: 0x" + String.format("%02x", winfo.fxSendB));
			//System.err.println("FX Send C: 0x" + String.format("%02x", winfo.fxSendC));
			//System.err.println("Main Send: 0x" + String.format("%02x", winfo.mainSend));
			
			//Read ne table
			for(int j = 0; j < neg_count; j++)
			{
				net_off+=4;
				long epos = Integer.toUnsignedLong(data_sec.intFromFile(net_off)); net_off+=4;
				
				epos += 8L + 4;
				epos = Integer.toUnsignedLong(data_sec.intFromFile(epos));
				
				int ecount = data_sec.intFromFile(epos); epos += 4;
				List<NoteEvent> elist = new ArrayList<NoteEvent>(ecount+1);
				for(int k = 0; k < ecount; k++)
				{
					epos += 4;
					long nepos = Integer.toUnsignedLong(data_sec.intFromFile(epos)); epos+=4;
					nepos += 8L;
					
					NoteEvent ne = new NoteEvent();
					ne.position = Float.intBitsToFloat(data_sec.intFromFile(nepos)); nepos+=4;
					ne.length = Float.intBitsToFloat(data_sec.intFromFile(nepos)); nepos+=4;
					ne.decay = data_sec.intFromFile(nepos);
					elist.add(ne);
				}
				winfo.noteEvents.add(elist);
			}
			
			//Read ni table
			for(int j = 0; j < ni_count; j++)
			{
				nit_off+=4;
				long eoff = Integer.toUnsignedLong(data_sec.intFromFile(nit_off)); nit_off+=4;
				eoff += 8L;
				NinTone t = NinTone.readRTone(data_sec, eoff);
				winfo.noteInfo.add(t);
			}
			
			waveset.entries.add(winfo);
		}
		
		return waveset;
	}
	

}
