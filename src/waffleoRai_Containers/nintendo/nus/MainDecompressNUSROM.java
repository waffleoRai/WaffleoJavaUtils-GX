package waffleoRai_Containers.nintendo.nus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import waffleoRai_Compression.nintendo.YazDecodeStream;
import waffleoRai_Containers.nintendo.nus.N64ZFileTable.Entry;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileUtils;
import waffleoRai_Utils.MultiFileBuffer;

public class MainDecompressNUSROM {
	
	public static final double MB_SIZE = 0x100000;
	
	public static void printUsage(){
		System.out.println("[JAR] [input_path] [output_path] (dmadata_offset)");
	}
	
	public static FileBuffer generatePadFile(int size){
		FileBuffer pad = new FileBuffer(size, true);
		for(int i = 0; i < size; i++) pad.addToFile(FileBuffer.ZERO_BYTE);
		return pad;
	}

	public static void main(String[] args) {
		
		if(args.length < 2){
			printUsage();
			System.exit(1);
		}
		
		String inpath = args[0];
		String outpath = args[1];
		String dma_offset_raw = null;
		if(args.length > 2) dma_offset_raw = args[2];
		
		try{
			//Load ROM Header
			N64ROMImage rominfo = N64ROMImage.readROMHeader(inpath);
			if(rominfo == null){
				System.err.println("Input file not recognized as N64 ROM image.");
				System.exit(1);
			}
			System.out.println("Input ROM Head CRC: " + String.format("%08x%08x", rominfo.getCRC1(), rominfo.getCRC2()));
			
			FileBuffer romdat = N64ROMImage.loadROMasZ64(inpath);
			byte[] md5 = FileUtils.getMD5Sum(romdat.getBytes());
			System.out.println("Input ROM MD5: " + FileUtils.bytes2str(md5));
			
			long insize = romdat.getFileSize();
			double size_mb = (double)insize / MB_SIZE;
			System.out.println("Input ROM Size: 0x" + Long.toHexString(romdat.getFileSize()) + String.format(" (%.2f MiB)", size_mb));
			
			//Find dmadata
			long dmadata_offset = 0L;
			if(dma_offset_raw != null){
				if(dma_offset_raw.startsWith("0x")){
					dmadata_offset = Long.parseLong(dma_offset_raw.substring(2), 16);
				}
				else{
					//Assumed decimal
					dmadata_offset = Long.parseLong(dma_offset_raw);
				}
			}
			else{
				dmadata_offset = N64ZFileTable.findTableStart(romdat);
				System.err.println("Guessed dmadata location: 0x" + Long.toHexString(dmadata_offset));
			}
			N64ZFileTable dmadata = N64ZFileTable.readTable(romdat, dmadata_offset);
			
			Map<Integer, Integer> addrmap = new HashMap<Integer, Integer>();
			//Decompress
			int filecount = dmadata.getEntryCount();
			FileBuffer[] uc_files = new FileBuffer[filecount];
			int[][] dmadata_new = new int[filecount][4];
			int dmaidx = -1;
			int dc_count = 0;
			for(int i = 0; i < filecount; i++){
				Entry file_entry = dmadata.getEntry(i);
				long mystart = file_entry.getROMAddress();
				if(mystart == dmadata_offset) dmaidx = i;
				if(file_entry.isCompressed()){
					int uc_size = (int)file_entry.getSize();
					int c_size = (int)file_entry.getSizeOnROM();
					FileBuffer src = romdat.createReadOnlyCopy(mystart, mystart + c_size);
					FileBuffer myfile = new FileBuffer(uc_size, true);
					uc_files[i] = myfile;
					
					YazDecodeStream decstr = YazDecodeStream.getDecoderStream(src);
					for(int j = 0; j < uc_size; j++){
						myfile.addToFile((byte)decstr.read());
					}
					
					int vaddr = (int)file_entry.getVirtualStart();
					dmadata_new[i][0] = vaddr;
					dmadata_new[i][1] = vaddr + uc_size;
					dmadata_new[i][2] = vaddr;
					dmadata_new[i][3] = 0;
					addrmap.put(vaddr, i);
					
					dc_count++;
					
					try{src.dispose();}
					catch(Exception ex){ex.printStackTrace();}
				}
				else{
					//Copy as is.
					int mysize = (int)file_entry.getSizeOnROM();
					if(mysize > 0){
						FileBuffer myfile = romdat.createCopy(mystart, mystart + mysize);
						uc_files[i] = myfile;
						
						int vaddr = (int)file_entry.getVirtualStart();
						dmadata_new[i][0] = vaddr;
						dmadata_new[i][1] = vaddr + mysize;
						dmadata_new[i][2] = vaddr;
						dmadata_new[i][3] = 0;
						addrmap.put(vaddr, i);
					}
					else{
						int vaddr = (int)file_entry.getVirtualStart();
						dmadata_new[i][0] = vaddr;
						dmadata_new[i][1] = (int)file_entry.getVirtualEnd();
						dmadata_new[i][2] = -1;
						dmadata_new[i][3] = -1;
					}
					
				}
			}
			
			//Update dmadata...
			int dmadata_size = (int)uc_files[dmaidx].getFileSize();
			FileBuffer file_dmadata = new FileBuffer(dmadata_size, true);
			for(int i = 0; i < filecount; i++){
				for(int j = 0; j < 4; j++){
					file_dmadata.addToFile(dmadata_new[i][j]);
				}
			}
			while(file_dmadata.getFileSize() < dmadata_size) file_dmadata.addToFile(FileBuffer.ZERO_BYTE);
			uc_files[dmaidx] = file_dmadata;
			
			System.out.println(filecount + " files total");
			System.out.println(dc_count + " files decompressed");
			
			//Update makerom (CRCs)...
			int rompos = 0;
			MultiFileBuffer outrom = new MultiFileBuffer((filecount*2)+1);
			List<Integer> fileorder = new ArrayList<Integer>(filecount+1);
			fileorder.addAll(addrmap.keySet());
			Collections.sort(fileorder);
			int i = 0, j = 0;
			for(Integer vaddr : fileorder){
				i = addrmap.get(vaddr);
				if(rompos < vaddr){
					int padsize = vaddr - rompos;
					outrom.addToFile(generatePadFile(padsize));
					rompos += padsize;
				}
				outrom.addToFile(uc_files[i]);
				rompos += (int)uc_files[i].getFileSize();
			}
			int[] newcrcs = N64ROMImage.calculateCRCs(outrom);
			//uc_files[0].replaceInt(newcrcs[0], 0x10L);
			//uc_files[0].replaceInt(newcrcs[1], 0x14L);
			System.out.println("Output ROM Head CRC: " + String.format("%08x%08x", newcrcs[0], newcrcs[1]));
			
			//Output uc rom (padded to 16 bytes)
			long total_size = outrom.getFileSize();
			if((total_size & 0xf) != 0){
				//Pad
				FileBuffer padding = new FileBuffer(16, true);
				int padamt = 0x10 - (int)(total_size & 0xf);
				for(j = 0; j < padamt; j++){
					padding.addToFile(FileBuffer.ZERO_BYTE);
					total_size++;
				}
				outrom.addToFile(padding);
			}
			outrom.writeFile(outpath);
			size_mb = (double)total_size / MB_SIZE;
			System.out.println("Output ROM Size: 0x" + Long.toHexString(total_size) + String.format(" (%.2f MiB)", size_mb));
			
			//Print UC MD5
			FileBuffer readback = new FileBuffer(outpath, true);
			readback.replaceInt(newcrcs[0], 0x10L);
			readback.replaceInt(newcrcs[1], 0x14L);
			readback.writeFile(outpath);
			md5 = FileUtils.getMD5Sum(readback.getBytes());
			System.out.println("Output ROM MD5: " + FileUtils.bytes2str(md5));
			
			//Compare.
			//DEBUG
			/*long cpos = 0x20;
			long maxsz = insize > total_size?total_size:insize;
			while(cpos < maxsz){
				byte b1 = romdat.getByte(cpos);
				byte b2 = readback.getByte(cpos);
				if(b1 != b2){
					System.err.println("First difference detected at 0x" + Long.toHexString(cpos));
					break;
				}
				cpos++;
			}*/
			
		}
		catch(Exception ex){
			ex.printStackTrace();
			System.exit(1);
		}

	}

}
