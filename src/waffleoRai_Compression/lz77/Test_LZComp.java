package waffleoRai_Compression.lz77;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBufferStreamer;
import waffleoRai_Utils.FileUtils;
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
		String outdir_b = outdir + File.separator + "b";
		String decHashesPath = outdir + File.separator + "dhashes.txt";
		String[] decHashes = new String[fcount];
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
		String outdir_c = outdir + File.separator + "c";
		if(!FileBuffer.directoryExists(outdir_c)) {
			Files.createDirectories(Paths.get(outdir_c));
		}
		
		int total_files = 0; //Not fcount, should not include empty slots.
		int matched_files = 0;
		List<Integer> nonmatches = new ArrayList<Integer>(fcount);
		for(int i = 0; i < fcount; i++) {
			String ipath = outdir_b + File.separator + String.format("%03d.bin", i);
			if(!FileBuffer.fileExists(ipath)) continue;
			total_files++;
			
			String opath = outdir_c + File.separator + String.format("%03d.bin.lz", i);
			String logpath = outdir_c + File.separator + String.format("%03d.log", i);
			
			FileBuffer indata = FileBuffer.createBuffer(ipath, false);
			LZMu lz = new LZMu();
			FileBuffer encdata = lz.encode(indata);
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
	}

	public static void main(String[] args) {
		if(args.length < 2) {
			System.err.println("Usage: [jar/class] (input file) (output dir)");
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
