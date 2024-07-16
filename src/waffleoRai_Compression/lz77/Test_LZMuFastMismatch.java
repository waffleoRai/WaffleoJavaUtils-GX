package waffleoRai_Compression.lz77;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBufferStreamer;

public class Test_LZMuFastMismatch {
	
	static final String SEP = File.separator;
	
	public static void doFile(String dir, int fno, String outDir) throws IOException {
		String aPath = String.format("%s%sa%s%03d.bin.lz", dir, SEP, SEP, fno);
		String cPath = String.format("%s%scc%s%03d.bin.lz", dir, SEP, SEP, fno);
		String outPath1 = String.format("%s%s%03d_allmatch.tsv", outDir, SEP, fno);
		String outPath2 = String.format("%s%s%03d_mismatch.tsv", outDir, SEP, fno);
		String outPath3 = String.format("%s%s%03d_holdlit.bin", outDir, SEP, fno);
		
		if(!FileBuffer.fileExists(aPath)) return;
		
		Map<Long, LZCompressionEvent> aMap = new HashMap<Long, LZCompressionEvent>();
		Map<Long, LZCompressionEvent> cMap = new HashMap<Long, LZCompressionEvent>();
		
		FileBuffer buff = FileBuffer.createBuffer(aPath, false);
		FileBuffer readbuff = buff.createReadOnlyCopy(4L, buff.getFileSize());
		int decsize = buff.intFromFile(0L);
		
		LZMu lz = new LZMu();
		lz.decodeToBuffer(new FileBufferStreamer(readbuff), decsize);
		readbuff.dispose();
		buff.dispose();
		lz.debug_dumpLoggedEventsToMap(aMap);
		
		buff = FileBuffer.createBuffer(cPath, false);
		readbuff = buff.createReadOnlyCopy(4L, buff.getFileSize());
		decsize = buff.intFromFile(0L);
		
		lz = new LZMu();
		lz.decodeToBuffer(new FileBufferStreamer(readbuff), decsize);
		readbuff.dispose();
		buff.dispose();
		lz.debug_dumpLoggedEventsToMap(cMap);
		
		//Compare maps.
		Set<Long> allkeys = new HashSet<Long>();
		allkeys.addAll(aMap.keySet());
		allkeys.addAll(cMap.keySet());
		List<Long> keyList = new ArrayList<Long>(allkeys.size()+1);
		keyList.addAll(allkeys);
		Collections.sort(keyList);
		allkeys.clear();
		
		BufferedWriter bw1 = new BufferedWriter(new FileWriter(outPath1));
		BufferedWriter bw2 = new BufferedWriter(new FileWriter(outPath2));
		BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outPath3));
		bw1.write("#POS\tCBIT_CTRL\tCONTROL\tCBIT_OUT\tOUTPUT\n");
		bw2.write("#POS\tCBIT_CTRL\tCONTROL\tCBIT_OUT\tOUTPUT\n");
		
		boolean subseqFlag = false;
		boolean isDiff = false;
		boolean printFlag = false;
		for(Long key : keyList) {
			LZCompressionEvent aEvent = aMap.get(key);
			LZCompressionEvent cEvent = cMap.get(key);
			isDiff = false;
			
			if(subseqFlag) {
				//if(aEvent != null) aEvent.printToNoOffsets(bw2);
				//else cEvent.printToNoOffsets(bw2);
				bw2.write("\n");
				subseqFlag = false;
			}
			
			bw1.write(String.format("0x%06x\t", key));
			
			if(aEvent != null) {
				if(cEvent != null) {
					if(!aEvent.equals(cEvent)) {
						isDiff = true;
						//Check if should print.
						if(!cEvent.isReference() && !aEvent.isReference()) {
							if(aEvent.getCopyAmount() > cEvent.getCopyAmount()) {
								printFlag = true;
							}
						}
					}
					bw1.write(aEvent.getControlBytePos() + "\t");
					aEvent.printToNoOffsets(bw1);
					bw1.write("\t" + cEvent.getControlBytePos() + "\t");
					cEvent.printToNoOffsets(bw1);
				}
				else {
					isDiff = true;
					bw1.write(aEvent.getControlBytePos() + "\t");
					aEvent.printToNoOffsets(bw1);
					bw1.write("\tN/A\t<NONE>");
					if(!aEvent.isReference()) {
						printFlag = true;
					}
				}
			}
			else {
				isDiff = true;
				bw1.write("N/A\t<NONE>\t");
				bw1.write(cEvent.getControlBytePos() + "\t");
				cEvent.printToNoOffsets(bw1);
			}
			bw1.write("\n");
			
			if(isDiff) {
				//Also write to bw2
				bw2.write(String.format("0x%06x\t", key));
				if(aEvent != null) {
					bw2.write(aEvent.getControlBytePos() + "\t");
					aEvent.printToNoOffsets(bw2);
				}
				else bw2.write("N/A\t<NONE>");
				bw2.write("\t");
				
				if(cEvent != null) {
					bw2.write(cEvent.getControlBytePos() + "\t");
					cEvent.printToNoOffsets(bw2);
				}
				else bw2.write("N/A\t<NONE>");
				bw2.write("\t");
				
				subseqFlag = true;
				
				//If a backcopy for C and a null for A, print offset.
				if(printFlag) {
					//System.out.print(String.format("0x%06x\t", key + cEvent.getCopyAmount()));
					//System.out.print(String.format("%d\n", (aEvent.getControlBytePos() + cEvent.getCopyAmount()) & 0x7));
					printFlag = false;
					FileBuffer buffer = new FileBuffer(8, false);
					//buffer.addToFile((int)(key + cEvent.getCopyAmount()));
					buffer.addToFile(key.intValue());
					buffer.addToFile(aEvent.getCopyAmount());
					buffer.writeToStream(bos);
				}
			}
		}
		
		if(subseqFlag) bw2.write("<NONE>\n");
		
		bw1.close();
		bw2.close();
		bos.close();
	}

	public static void main(String[] args) {
		String dir = args[0];
		String outDir = args[1];
		
		try {
			for(int i = 0; i < 192; i++) {
				System.err.println("Working on " + i + "...");
				doFile(dir, i, outDir);
			}
		}
		catch(Exception ex) {
			ex.printStackTrace();
			System.exit(1);
		}
		
		
	}

}
