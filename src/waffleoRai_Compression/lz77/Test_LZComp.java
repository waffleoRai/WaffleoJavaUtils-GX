package waffleoRai_Compression.lz77;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import waffleoRai_Compression.lz77.LZCompCore.RunMatch;
import waffleoRai_Compression.lz77.LZMu.MuRunMatch;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBufferStreamer;
import waffleoRai_Utils.FileUtils;
import waffleoRai_Utils.MultiFileBuffer;
import waffleoRai_Utils.StreamWrapper;

public class Test_LZComp {
	
	private static long[] lzmu_read_offset_table(FileBuffer arcdata) {
		arcdata.setEndian(false);
		int fcount = arcdata.intFromFile(0L) >>> 2;
		long[] tbl = new long[fcount];
		
		long cpos = 0L;
		for(int i = 0; i < fcount; i++) {
			tbl[i] = arcdata.intFromFile(cpos) + cpos;
			cpos += 4;
		}
		
		return tbl;
	}
	
	private static void lzmu_match_test(String inpath, String outdir) throws IOException {
		FileBuffer arcdata = FileBuffer.createBuffer(inpath, false);
		long[] offtbl = lzmu_read_offset_table(arcdata);
		int fcount = offtbl.length;
		long fend = arcdata.getFileSize();
		
		//Extract
		System.err.println("-> Extracting files from archive...");
		String outdir_a = outdir + File.separator + "a";
		String compHashesPath = outdir + File.separator + "chashes.txt";
		String[] compHashes = new String[fcount];
		if(!FileBuffer.directoryExists(outdir_a)) {
			Files.createDirectories(Paths.get(outdir_a));
		}
		BufferedOutputStream bos = null;
		BufferedWriter bw = new BufferedWriter(new FileWriter(compHashesPath));
		for(int i = 0; i < fcount; i++) {
			//Check if empty file
			long stpos = offtbl[i];
			long edpos = fend;
			if(i < (fcount-1)) edpos = offtbl[i+1]; 
			if(edpos < stpos) break;
			if(edpos == stpos) {
				bw.write("-\n");
				continue;
			}
			
			//Grab data
			byte[] dbytes = arcdata.getBytes(stpos, edpos);
			String hashstr = FileUtils.bytes2str(FileUtils.getSHA256Hash(dbytes));
			compHashes[i] = hashstr;
			bw.write(hashstr + "\n");
			
			String outpath = outdir_a + File.separator + String.format("%03d.bin.lz", i);
			bos = new BufferedOutputStream(new FileOutputStream(outpath));
			bos.write(dbytes);
			bos.close();
		}
		bw.close();

		//Decompress
		System.err.println("-> Decompressing files...");
		String outdir_b = outdir + File.separator + "b";
		String decHashesPath = outdir + File.separator + "dhashes.txt";
		String[] decHashes = new String[fcount];
		byte[][] overflow = new byte[fcount][];
		if(!FileBuffer.directoryExists(outdir_b)) {
			Files.createDirectories(Paths.get(outdir_b));
		}
		bw = new BufferedWriter(new FileWriter(decHashesPath));
		for(int i = 0; i < fcount; i++) {
			long stpos = offtbl[i];
			long edpos = fend;
			if(i < (fcount-1)) edpos = offtbl[i+1]; 
			if(edpos < stpos) break;
			if(edpos == stpos) {
				bw.write("-\n");
				continue;
			}
			
			String outpath = outdir_b + File.separator + String.format("%03d.bin", i);
			String logpath = outdir_b + File.separator + String.format("%03d.log", i);
			FileBuffer subfile = arcdata.createReadOnlyCopy(stpos+4, edpos);
			int decsize = arcdata.intFromFile(stpos);
			LZMu lz = new LZMu();
			
			FileBufferStreamer instr = new FileBufferStreamer(subfile);
			StreamWrapper outstr = lz.decode(instr, decsize);
			overflow[i] = lz.getOverflowContents();
			subfile.dispose();
			
			lz.debug_dumpLoggedEvents(logpath);
			
			//Copy output data to byte array
			byte[] outdat = new byte[decsize];
			int j = 0;
			while(!outstr.isEmpty()) outdat[j++] = outstr.get();
			
			//Hash and save output
			String hashstr = FileUtils.bytes2str(FileUtils.getSHA256Hash(outdat));
			decHashes[i] = hashstr;
			bw.write(hashstr + "\n");
			bos = new BufferedOutputStream(new FileOutputStream(outpath));
			bos.write(outdat);
			bos.close();
		}
		bw.close();
		
		//Recompress
		System.err.println("-> Attempting recompression of files...");
		String outdir_c = outdir + File.separator + "c";
		if(!FileBuffer.directoryExists(outdir_c)) {
			Files.createDirectories(Paths.get(outdir_c));
		}
		
		int total_files = 0; //Not fcount, should not include empty slots.
		int matched_files = 0;
		List<Integer> nonmatches = new ArrayList<Integer>(fcount);
		for(int i = 0; i < fcount; i++) {
			System.err.println("\tCompressing " + i);
			String ipath = outdir_b + File.separator + String.format("%03d.bin", i);
			if(!FileBuffer.fileExists(ipath)) continue;
			total_files++;
			
			String opath = outdir_c + File.separator + String.format("%03d.bin.lz", i);
			String logpath = outdir_c + File.separator + String.format("%03d.log", i);
			
			int overflowamt = 0;
			FileBuffer indata = FileBuffer.createBuffer(ipath, false);
			if(overflow[i] != null){
				MultiFileBuffer glob = new MultiFileBuffer(2);
				glob.addToFile(indata);
				glob.addToFile(FileBuffer.wrap(overflow[i]));
				overflowamt = overflow[i].length;
				indata = glob;
			}
			LZMu lz = new LZMu();
			//lz.setCompressionStrategy(LZMu.COMP_LOOKAHEAD_REC);
			lz.setCompressionStrategy(LZMu.COMP_LOOKAHEAD_QUICK);
			//lz.setCompressionStrategy(LZMu.COMP_LOOKAHEAD_SCANALL_GREEDY);
			//lz.setCompressionLookahead(0x1000);
			FileBuffer encdata = lz.encode(indata, overflowamt);
			lz.debug_dumpLoggedEvents(logpath);
			
			encdata.writeFile(opath);
			String ehash = FileUtils.bytes2str(FileUtils.getSHA256Hash(encdata.getBytes(0,encdata.getFileSize())));
			if(ehash.equals(compHashes[i])) matched_files++;
			else nonmatches.add(i);
		}
		
		//Output comp matching results to stdout
		double perc = (double)matched_files / (double)total_files;
		perc *= 100.0;
		System.out.println("-> Recompression Done");
		System.out.print("Files Matched: " + matched_files + "/" + total_files);
		System.out.print(" (" + perc + "%)\n");
		if(matched_files < total_files) {
			System.out.println("Unmatched files:");
			int umcount = nonmatches.size();
			int i = 0; int j = 0;
			while(i < umcount) {
				if(j > 7) {
					j = 0;
					System.out.print("\n");
				}
				if(j++ == 0) System.out.print("\t");
				
				System.out.print(String.format("%03d ", nonmatches.get(i++)));
			}
			System.out.print("\n");
		}
		else {
			System.out.println("Full match successful!");
		}
		nonmatches.clear();
		matched_files = 0;
		
		//Redecompress
		System.err.println("-> Decompressing re-compressed files to check compression validity...");
		String outdir_d = outdir + File.separator + "d";
		if(!FileBuffer.directoryExists(outdir_d)) {
			Files.createDirectories(Paths.get(outdir_d));
		}
		for(int i = 0; i < fcount; i++) {
			String ipath = outdir_c + File.separator + String.format("%03d.bin.lz", i);
			if(!FileBuffer.fileExists(ipath)) continue;
			
			String opath = outdir_d + File.separator + String.format("%03d.bin", i);
			String logpath = outdir_d + File.separator + String.format("%03d.log", i);
			
			FileBuffer indata = FileBuffer.createBuffer(ipath, false);
			int decsize = indata.intFromFile(0L);
			LZMu lz = new LZMu();
			StreamWrapper decstr = lz.decode(new FileBufferStreamer(indata.createReadOnlyCopy(4, indata.getFileSize())), decsize);
			lz.debug_dumpLoggedEvents(logpath);
			
			byte[] decdata = new byte[decsize];
			int j = 0;
			while(!decstr.isEmpty()) decdata[j++] = decstr.get();
			
			bos = new BufferedOutputStream(new FileOutputStream(opath));
			bos.write(decdata);
			bos.close();
			
			String dhash = FileUtils.bytes2str(FileUtils.getSHA256Hash(decdata));
			if(dhash.equals(decHashes[i])) matched_files++;
			else nonmatches.add(i);
		}
		
		
		//Output redec results to stdout
		System.out.println();
		perc = (double)matched_files / (double)total_files;
		perc *= 100.0;
		System.out.println("-> Redecompression Done");
		System.out.print("Files Matched: " + matched_files + "/" + total_files);
		System.out.print(" (" + perc + "%)\n");
		if(matched_files < total_files) {
			System.out.println("Unmatched files:");
			int umcount = nonmatches.size();
			int i = 0; int j = 0;
			while(i < umcount) {
				if(j > 7) {
					j = 0;
					System.out.print("\n");
				}
				if(j++ == 0) System.out.print("\t");
				
				System.out.print(String.format("%03d ", nonmatches.get(i++)));
			}
			System.out.print("\n");
		}
		else {
			System.out.println("Compression valid for all files!");
		}
		nonmatches.clear();
		
		System.out.println("Examining diffs...");
		lzmu_dumpFirstDiffs(outdir, fcount);
		
	}

	private static RunMatch lzmu_parseLogLine(String line){
		if(line == null) return null;
		
		MuRunMatch match = new MuRunMatch();
		String[] parts = line.split("\t");
		String enc = parts[0].substring(parts[0].lastIndexOf(' ') + 3);
		enc = enc.replace("]", "");
		match.encoder_pos = Long.parseUnsignedLong(enc, 16);
		
		if(parts[1].startsWith("BCPY")){
			String[] cpyparts = parts[1].split(" ");
			match.match_run = Integer.parseInt(cpyparts[1]);
			match.match_pos = (int)Long.parseUnsignedLong(cpyparts[3].substring(2), 16);
		}
		else if(parts[1].startsWith("LIT")){
			match.match_run = 0;
			match.match_pos = -1;
			match.copy_amt = Integer.parseInt(parts[1].substring(parts[1].lastIndexOf(' ')).trim());
		}
		else return null;
		
		return match;
	}
	
	private static void lzmu_dumpFirstDiffs(String outdir, int n) throws IOException{
		String logpath = outdir + File.separator + "diffs.log";
		BufferedWriter bw = new BufferedWriter(new FileWriter(logpath));
		
		final int BACK_AMT = 2;
		final int FWD_AMT = 3;
		final int TBL_LINES = 3;
		
		for(int i = 0; i < n; i++){
			String path_b = outdir + File.separator + "b" + File.separator + String.format("%03d.log", i);
			String path_d = outdir + File.separator + "d" + File.separator + String.format("%03d.log", i);
			
			if(!FileBuffer.fileExists(path_b)) continue;
			if(!FileBuffer.fileExists(path_d)) continue;
			
			BufferedReader b = new BufferedReader(new FileReader(path_b));
			BufferedReader d = new BufferedReader(new FileReader(path_d));
			
			String line_b = b.readLine();
			String line_d = d.readLine();
			LinkedList<String> last_b = new LinkedList<String>();
			LinkedList<String> last_d = new LinkedList<String>();
			while(line_b != null && line_d != null){
				if(!line_b.equals(line_d)){
					RunMatch[] bmatches = new RunMatch[TBL_LINES+1];
					RunMatch[] dmatches = new RunMatch[TBL_LINES+1];

					int shift = 0;
					if(line_b.contains("\tLIT ") && line_d.contains("\tLIT ")){
						RunMatch lit1 = lzmu_parseLogLine(line_b);
						RunMatch lit2 = lzmu_parseLogLine(line_d);
						if(lit1.copy_amt > lit2.copy_amt) shift = 1; //b is next
						else shift = -1; //d is next
					}
					
					bw.write("--------------- " + String.format("%03d", i) + " ---------------\n\n");
					bw.write("\tCONTROL\n");
					for(String l : last_b) bw.write(l + "\n");
					bw.write(line_b + "\n");
					bmatches[0] = lzmu_parseLogLine(line_b);
					for(int j = 0; j < FWD_AMT; j++){
						line_b = b.readLine();
						if(line_b == null){
							bw.write("LOG END\n");
							break;
						}
						bw.write(line_b + "\n");
						if(j + 1 < TBL_LINES+1){
							bmatches[j+1] = lzmu_parseLogLine(line_b);
						}
					}
					if(shift < 0){
						for(int j = 0; j < TBL_LINES; j++){
							bmatches[j] = bmatches[j+1];
						}
						bmatches[TBL_LINES] = null;
					}
					
					bw.write("\tOUTPUT\n");
					for(String l : last_d) bw.write(l + "\n");
					bw.write(line_d + "\n");
					dmatches[0] = lzmu_parseLogLine(line_d);
					for(int j = 0; j < FWD_AMT; j++){
						line_d = d.readLine();
						if(line_d == null){
							bw.write("LOG END\n");
							break;
						}
						bw.write(line_d + "\n");
						if(j + 1 < TBL_LINES+1){
							dmatches[j+1] = lzmu_parseLogLine(line_d);
						}
					}
					if(shift > 0){
						for(int j = 0; j < TBL_LINES; j++){
							dmatches[j] = dmatches[j+1];
						}
						dmatches[TBL_LINES] = null;
					}
					
					//Score tables
					RunMatch[] mmatches, nmatches;
					if(bmatches[0].match_run < 2){
						mmatches = dmatches;
						nmatches = bmatches;
					}
					else{
						mmatches = bmatches;
						nmatches = dmatches;
					}
					
					int[] mscores = new int[TBL_LINES];
					int[] nscores = new int[TBL_LINES];
					int mtotal = 0;
					int ntotal = 0;
					for(int j = 0; j < TBL_LINES; j++){
						if(mmatches[j] != null){
							mscores[j] = LZMu.MuLZCompCore.scoreRunStaticBits(mmatches[j]);
						}
						if(nmatches[j] != null){
							nscores[j] = LZMu.MuLZCompCore.scoreRunStaticBits(nmatches[j]);
						}
					}
					
					for(int j = 0; j < 2; j++){
						if(mmatches[j] != null){
							mtotal += mscores[j];
						}
						if(nmatches[j] != null){
							ntotal += nscores[j];
						}
					}
					
					String colspace = "\t\t";
					bw.write("\nPosition: 0x" + Long.toHexString(bmatches[0].encoder_pos) + "\n");
					
					bw.write("\n\t");
					if(bmatches[0].match_run >= 2) bw.write("> ");
					bw.write("MY [" + mtotal + "]" + colspace);
					if(bmatches[0].match_run < 2) bw.write("> ");
					bw.write("NEXT [" + ntotal + "]\n");
					
					for(int j = 0; j < TBL_LINES; j++){
						if(mmatches[j] != null){
							if(mmatches[j].match_run < 2){
								//Literal
								bw.write("\tLIT 1" + colspace + "\t");
							}
							else{
								//Backcopy
								long offamt = mmatches[j].match_pos - mmatches[j].encoder_pos;
								bw.write("\tBCPY " + mmatches[j].match_run);
								bw.write(" @ " + offamt);
								bw.write(" [" + mscores[j] + "]" + colspace);
							}	
						}
						
						if(nmatches[j] != null){
							if(nmatches[j].match_run < 2){
								//Literal
								bw.write("LIT 1\n");
							}
							else{
								//Backcopy
								long offamt = nmatches[j].match_pos - nmatches[j].encoder_pos;
								bw.write("BCPY " + nmatches[j].match_run);
								bw.write(" @ " + offamt);
								bw.write(" [" + nscores[j] + "]\n");
							}	
						}
					}
					
					bw.write("\n");
					break;
				}
				last_b.add(line_b);
				last_d.add(line_d);
				while(last_b.size() > BACK_AMT) last_b.pop();
				while(last_d.size() > BACK_AMT) last_d.pop();
				
				line_b = b.readLine();
				line_d = d.readLine();
			}
			
			b.close();
			d.close();
		}
		bw.close();
	}
	
	public static void main(String[] args) {
		if(args.length < 2) {
			System.err.println("Usage: [jar/class] (input file) (output dir)");
			System.exit(1);
		}
		String inpath = args[0];
		String outdir = args[1];
		
		
		try {
			lzmu_match_test(inpath, outdir);
			
		}catch(Exception ex) {
			ex.printStackTrace();
			System.exit(1);
		}
		
	}

}
