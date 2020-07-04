package waffleoRai_Containers.nintendo;

import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import waffleoRai_Containers.nintendo.wiidisc.WiiCryptListener;
import waffleoRai_Image.nintendo.DolGraphics;
import waffleoRai_Utils.CacheFileBuffer;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;
import waffleoRai_Utils.MappedBlockBuffer;

//WiiDisc handles the actual Wii image stuff.
//This class needs to read the WBFS wrapper info,
//	and basically wrap the file so that the offsets are redirected
//	as if the file was being read straight off the wii disc!

/**
 * 
 * @author Blythe
 *
 */
public class WBFSImage {
	
	/*
	 * HD = "Hard Drive". Sectors are usually 512 bytes
	 * 
	 * Format ------
	 * 
	 * {0x0000}	Magic "WBFS" [4]
	 * {0x0004}	#HD Sectors [4] (In this file, not full image)
	 * {0x0008} HD Sector Size [1] - Factor (power of 2, or left shift amt), Usually 9
	 * {0x0009} WBFS Sector Size [1] - Factor (power of 2, or left shift amt), Usually 21
	 * {0x000A} Disc Count? [1]
	 * {0x000B} ??? [1]
	 * {0x000C} Disc Table [0x1F4 (or HD sec size - 0xC)]
	 * 		Disc Start (WBFS Sector) [1]
	 * {0x0200} Copy of first 0x100 bytes of Wii header
	 * {0x0300} LBA Table
	 * 		Disc LBA Table [2 * #WBFS sectors/disc]
	 * 			Sector Mapping [2]
	 */
	
	//TODO WBFS-ICO?
	
	/* ----- Constants ----- */
	
	public static final String WBFS_MAGIC = "WBFS";
	public static final String WICO_MAGIC = "wIco";
	
	public static final int WICO_VERSION = 1;
	
	public static final int WII_SEC_SZ = 0x8000;
	public static final int WII_SEC_PER_DISC = 143432*2;
	public static final int WII_SEC_SZ_S = 15;
	
	public static final int DEFO_HD_SEC_SZ_S = 9;
	public static final int DEFO_WBFS_SEC_SZ_S = 21;
	
	public static final int DISC_TABLE_OFFSET = 0xC;
	public static final int LBA_TABLE_OFFSET = 0x300;
	
	public static final int STREAMBUFFER_SUBBUF_SIZE = 0x8000; //Wii sector (32768 or 32kB)
	public static final int STREAMBUFFER_SUBBUF_NUMBER = 0x4000; //16kB
	//This creates a 512MB cache
	
	/* ----- Instance Variables ----- */
	
	private String wbfs_path;
	
	private int hd_sec_shift;
	private int hd_sec_size;
	private int hd_sec_count;
	
	private int wbfs_sec_shift;
	private int wbfs_sec_size;
	private int wbfs_sec_count;
	
	private int[] disc_table;
	private List<int[]> lba_table;
	
	/* ----- Objects ----- */
	
	public static class WIcoDat
	{
		private BufferedImage[] images;
		private int[][] anim_dat;
		
		public WIcoDat(int img_count, int anim_events)
		{
			images = new BufferedImage[img_count];
			if(anim_events > 0) anim_dat = new int[anim_events][2];
		}
		
		public BufferedImage getImage(int idx)
		{
			return images[idx];
		}
		
		public int getAnimImgIndex(int event_idx)
		{
			if(anim_dat == null) return -1;
			return anim_dat[event_idx][0];
		}
		
		public int getAnimFrameCount(int event_idx)
		{
			if(anim_dat == null) return 0;
			return anim_dat[event_idx][1];
		}
		
		public void setImage(BufferedImage img, int idx)
		{
			images[idx] = img;
		}
		
		public boolean setAnimImgIndex(int event_idx, int value)
		{
			if(anim_dat == null) return false;
			anim_dat[event_idx][0] = value;
			return true;
		}
		
		public boolean setAnimFrameCount(int event_idx, int value)
		{
			if(anim_dat == null) return false;
			anim_dat[event_idx][1] = value;
			return true;
		}
		
		public int getImageCount()
		{
			return images.length;
		}
		
		public int getAnimEventCount()
		{
			if(anim_dat == null) return 0;
			return anim_dat.length;
		}
		
	}
	
	/* ----- Parsers ----- */
	
	private WBFSImage(String path)
	{
		wbfs_path = path;
		hd_sec_shift = -1;
		hd_sec_size = -1;
		hd_sec_count = -1;
		wbfs_sec_shift = -1;
		wbfs_sec_size = -1;
		wbfs_sec_count = -1;
	}
	
	public static WBFSImage readWBFS(String path) throws IOException, UnsupportedFileTypeException
	{
		WBFSImage image = new WBFSImage(path);
		
		//StreamBuffer file = new StreamBuffer(image.wbfs_path, true);
		CacheFileBuffer file = CacheFileBuffer.getReadOnlyCacheBuffer(path, STREAMBUFFER_SUBBUF_SIZE, STREAMBUFFER_SUBBUF_NUMBER, true);
		
		//Test magic
		if(file.findString(0, 0x10, WBFS_MAGIC) != 0) throw new FileBuffer.UnsupportedFileTypeException("WBFS magic number not found!");
		file.skipBytes(4);
		
		image.hd_sec_count = file.nextInt();
		image.hd_sec_shift = Byte.toUnsignedInt(file.nextByte());
		image.wbfs_sec_shift = Byte.toUnsignedInt(file.nextByte());
		
		image.hd_sec_size = 1 << image.hd_sec_shift;
		image.wbfs_sec_size = 1 << image.wbfs_sec_shift;
		
		long disc_sz_bytes = (long)image.hd_sec_count * (long)image.hd_sec_size;
		image.wbfs_sec_count = (int)(disc_sz_bytes >>> image.wbfs_sec_shift);
		//long n_wii_sec = disc_sz_bytes >>> WII_SEC_SZ_S;
		
		//TODO: Some unknown value at 0x0A-0x0B?
		
		//Disc Table
		int dtsize = image.hd_sec_size - DISC_TABLE_OFFSET;
		image.disc_table = new int[dtsize];
		for(int i = 0; i < dtsize; i++)
		{
			image.disc_table[i] = Byte.toUnsignedInt(file.getByte(DISC_TABLE_OFFSET+i));
		}
		
		int discCount = 0;
		for(int i = 0; i < dtsize; i++)
		{
			if(image.disc_table[i] == 0) break;
			discCount++;
		}
		
		//LBA Table
		image.lba_table = new ArrayList<int[]>(discCount + 1);
		int wbfs_sec_per_disc = image.getWBFSSectorsPerDisc();
		file.setCurrentPosition(LBA_TABLE_OFFSET);
		for(int j = 0; j < discCount; j++)
		{
			int[] lbaTable = new int[wbfs_sec_per_disc];
			for(int i = 0; i < wbfs_sec_per_disc; i++) lbaTable[i] = Short.toUnsignedInt(file.nextShort());
			image.lba_table.add(lbaTable);
		}
		
		return image;
	}
	
	/* ----- Calculations ----- */
	
	public int getWBFSSectorsPerDisc()
	{
		int n_wbfs_sec_per_disc = WII_SEC_PER_DISC >> (wbfs_sec_shift - WII_SEC_SZ_S);
		return n_wbfs_sec_per_disc;
	}
	
	/* ----- Getters ----- */
	
	public int getHardDiskSectorSize(){return hd_sec_size;}
	public int getHardDiskSectorCount(){return hd_sec_count;}
	public int getWBFSSectorSize(){return wbfs_sec_size;}
	public int getWBFSSectorCount(){return wbfs_sec_count;}
	
	/* ----- Reader Generation ----- */
	
	public FileBuffer generateDiscView(int discIndex) throws IOException
	{
		//Check disc - if it passes, get offsets
		//System.err.println("WBFSImage.generateDiscView || -DEBUG- function called!");
		if(discIndex < 0 || discIndex >= disc_table.length) return null;
		int discSector = disc_table[discIndex];
		if(discSector == 0) return null; //There isn't a disc by that index
		//System.err.println("WBFSImage.generateDiscView || -DEBUG- Disc Sector: " + discSector);
		
		int[] discLBA = lba_table.get(discIndex);
		//int wbfs_sec_per_disc = getWBFSSectorsPerDisc();
		//System.err.println("WBFSImage.generateDiscView || -DEBUG- Sectors per Disc: " + wbfs_sec_per_disc);
		long dsLong = Integer.toUnsignedLong(discSector);
		int index = discLBA.length-1;
		long esLong = (long)discLBA[index];
		while(esLong == 0){index--; esLong = (long)discLBA[index];}
		esLong++;
		//System.err.println("WBFSImage.generateDiscView || -DEBUG- End Sector: " + esLong);
		long doffset = dsLong << wbfs_sec_shift; 
		long eoffset = esLong << wbfs_sec_shift;
		//System.err.println("WBFSImage.generateDiscView || -DEBUG- Disc Start: 0x" + Long.toHexString(doffset));
		//System.err.println("WBFSImage.generateDiscView || -DEBUG- Disc End: 0x" + Long.toHexString(eoffset));
		//System.exit(2);
		
		//Open file into Cached Buffer
		//FileBuffer wbfs = new StreamBuffer(wbfs_path, STREAMBUFFER_SUBBUF_SIZE, STREAMBUFFER_SUBBUF_NUMBER);
		//wbfs.setEndian(true);
		FileBuffer wbfs = CacheFileBuffer.getReadOnlyCacheBuffer(wbfs_path, STREAMBUFFER_SUBBUF_SIZE, STREAMBUFFER_SUBBUF_NUMBER, true);
		
		//Create RO buffer
		FileBuffer myDisc = wbfs.createReadOnlyCopy(doffset, eoffset);
		
		//Generate the mapped buffer by reading the lba table for that disc
		MappedBlockBuffer discView = new MappedBlockBuffer(myDisc, wbfs_sec_shift);
		for(int i = 0; i < discLBA.length; i++)
		{
			int local = discLBA[i];
			if(local <= 0) continue;
			local -= discSector; //Since anything before disc is chopped off by the RO view
			discView.addBlockMapping(i, local);
		}
		
		return discView;
	}

	public static WiiDisc readWiiDisc(String path, int discIndex, WiiCryptListener observer) throws IOException, UnsupportedFileTypeException
	{
		//System.err.println("WBFSImage.readWiiDisc || -DEBUG- Function called!");
		WBFSImage wbfs = readWBFS(path);
		FileBuffer discView = wbfs.generateDiscView(discIndex);
		WiiDisc image = WiiDisc.parseFromData(discView, observer);

		return image;
	}
	
	/* ----- Conversions ----- */
	
	public static int estimateBestSectorSizeFactor(String raw_image_path)
	{
		return estimateBestSectorSizeFactor(raw_image_path, 0, 0);
	}
	
	public static int estimateBestSectorSizeFactor(String raw_image_path, int wico_img_count, int wico_anim_count)
	{
		int wico_sz = 0;
		if(wico_img_count > 0)
		{
			wico_sz = 4608 * wico_img_count;
			wico_sz += 16 + wico_anim_count;
			while(wico_sz % 0x10 != 0)wico_sz++;	
		}
		
		int wbfs_header_size = 0x300;
		
		int min_s = 15; //Wii sector size (32kb)
		int max_s = 26; //64MB
		
		//Now we do a scan to see what fits the LBA the best.
		//Smallest we can get away with while still fitting everything in one WBFS sector!
		long img_size = FileBuffer.fileSize(raw_image_path);
		for(int s = min_s; s <= max_s; s++)
		{
			long sec_size = 1L << s;
			int sec_count = (int)(img_size >>> s);
			if((img_size % sec_size) != 0) sec_count++;
			int lba_tbl_size = sec_count << 1;
			
			long hsize = (long)wbfs_header_size;
			hsize += lba_tbl_size;
			if(wico_sz > 0)
			{
				while((hsize % 0x100) != 0)hsize++;
				hsize += wico_sz;	
			}
			if(hsize <= sec_size) return s;
		}
		
		return -1;
	}
	
	public static boolean writeToWBFS(String raw_image_path, String outpath, boolean verbose) throws IOException
	{
		return writeToWBFS(raw_image_path, outpath, estimateBestSectorSizeFactor(raw_image_path), null, verbose);
	}
	
	private static boolean writeToWBFS(String raw_image_path, String outpath, int sfac, WIcoDat wico, boolean verbose) throws IOException
	{
		if(sfac < 15) return false;
		if(!FileBuffer.fileExists(raw_image_path)){
			if(verbose) System.err.println("File at " + raw_image_path + " doesn't exist!");
			return false;
		}
		int wbfs_sec_sz = 1 << sfac;
		if(verbose)
		{
			System.err.println("WBFS Sector Size Chosen: 0x" + Integer.toHexString(wbfs_sec_sz));
		}
		
		//Map sectors...
		CacheFileBuffer raw = CacheFileBuffer.getReadOnlyCacheBuffer(raw_image_path, STREAMBUFFER_SUBBUF_SIZE, STREAMBUFFER_SUBBUF_NUMBER, true);
		long raw_size = raw.getFileSize();
		long sec_count = raw_size >>> sfac;
		boolean partial = false;
		if(sec_count << sfac != raw_size){partial = true; sec_count++;}
		if(verbose)
		{
			System.err.println("Input raw image size: 0x" + Long.toHexString(raw_size) + " (" + raw_size + " bytes)");
			System.err.println("WBFS Sectors: 0x" + Long.toHexString(sec_count) + " (" + sec_count + ")");
			System.err.println("Partial Sectors?: " + partial);
		}
		int scount = (int)sec_count;
		int[] lba_tbl = new int[scount];
		boolean[] keep_tbl = new boolean[scount];
		
		long cpos = 0;
		for(int i = 0; i < scount; i++)
		{
			if(verbose && (i%1000 == 0)) System.err.println("Scanning sector " + (i+1) + " of " + scount + "...");
			long epos = cpos + wbfs_sec_sz;
			if(partial && (i == scount-1)) epos = raw_size;
			while(cpos < epos)
			{
				byte b = raw.getByte(cpos);
				if(b != 0)
				{
					keep_tbl[i] = true;
					break;
				}
				cpos++;
			}
			cpos = epos;
		}
		if(verbose)System.err.println("Initial scan complete!\n");
		
		//Scan keep_tbl
		int kcount = 0;
		long keepsz = 0;
		
		int initsec = 1;
		int nowsec = initsec;
		for(int i = 0; i < scount; i++)
		{
			if(keep_tbl[i])
			{
				kcount++;
				lba_tbl[i] = nowsec;
				nowsec++;
			}
		}
		keepsz = (long)kcount << sfac;
		if(verbose)
		{
			System.err.println("Sectors to keep: " + kcount);
			System.err.println("Sectors to condense: " + (scount-kcount));
		}
		
		//Check size of LBA table
		long lba_tbl_sz = (long)(lba_tbl.length << 1);
		if((lba_tbl_sz + LBA_TABLE_OFFSET) > wbfs_sec_sz)
		{
			if(verbose) System.err.println("WARNING: WBFS sector size set too low to accomodate LBA Table. "
					+ "Header will be written across multiple sectors. "
					+ "This might not be proper formatting, and some tools may be unable to read the resulting image!");
			
			long tot = lba_tbl_sz + LBA_TABLE_OFFSET;
			long hsz = wbfs_sec_sz;
			int extra = 0;
			while(hsz < tot) {hsz += wbfs_sec_sz; extra++;}
			if(verbose) System.err.println("Header will take up " + (extra+1) + " WBFS sectors.");
			
			initsec += extra;
			for(int i = 0; i < scount; i++)
			{
				if(keep_tbl[i])lba_tbl[i] += extra;
			}
		}
		
		//Prepare header
		int headersize = wbfs_sec_sz * initsec;
		FileBuffer wbfs_header = new FileBuffer(headersize, true);
		wbfs_header.printASCIIToFile(WBFS_MAGIC);
		int hd_secs = (int)((keepsz + headersize) >>> DEFO_HD_SEC_SZ_S);
		wbfs_header.addToFile(hd_secs);
		wbfs_header.addToFile((byte)DEFO_HD_SEC_SZ_S);
		wbfs_header.addToFile((byte)sfac);
		wbfs_header.addToFile((byte)1); //Disc count?
		wbfs_header.addToFile((byte)0); //Reserved?
		
		//Disc table
		wbfs_header.addToFile((byte)(initsec));
		int hd_sec_size = 1 << DEFO_HD_SEC_SZ_S;
		while(wbfs_header.getFileSize() < hd_sec_size) wbfs_header.addToFile(FileBuffer.ZERO_BYTE);
		
		//Wii header (Copy the first 0x100 bytes from raw image)
		for(long i = 0; i < 0x100; i++) wbfs_header.addToFile((raw.getByte(i)));
		
		//LBA table
		for(int i = 0; i < scount; i++) wbfs_header.addToFile((short)lba_tbl[i]);
		
		//Pad to next sector...
		if(wico != null)
		{
			int acount = wico.getAnimEventCount();
			while((wbfs_header.getFileSize() % 0x100) != 0) wbfs_header.addToFile(FileBuffer.ZERO_BYTE);
			wbfs_header.printASCIIToFile(WICO_MAGIC);
			wbfs_header.addToFile((short)WICO_VERSION);
			wbfs_header.addToFile((byte)wico.getImageCount());
			wbfs_header.addToFile((byte)acount);
			int animsize = acount;
			while((animsize % 0x10) != 0) animsize++;
			wbfs_header.addToFile(animsize+0x10);
			wbfs_header.addToFile(0);
			
			//Anim events
			for(int i = 0; i < acount; i++)
			{
				int iidx = wico.getAnimImgIndex(i);
				int len = wico.getAnimFrameCount(i);
				
				int val = len << 3;
				val |= (iidx & 0x7);
				wbfs_header.addToFile((byte)val);
			}
			while((wbfs_header.getFileSize() % 0x10)!= 0) wbfs_header.addToFile(FileBuffer.ZERO_BYTE);
			
			//Images
			for(int i = 0; i < wico.getImageCount(); i++)
			{
				BufferedImage img = wico.getImage(i);
				for(int y = 0; y < 48; y++)
				{
					for(int x = 0; x < 48; x++)
					{
						int argb = img.getRGB(x, y);
						wbfs_header.addToFile(DolGraphics.ARGB_to_RGB5A3(argb));
					}
				}
				
			}
			
		}
		
		while(wbfs_header.getFileSize() < headersize) wbfs_header.addToFile(FileBuffer.ZERO_BYTE);
		
		//Write to file...
		long written = 0;
		BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outpath));
		wbfs_header.writeToStream(bos);
		written += wbfs_header.getFileSize();
		
		//Copy data
		cpos = 0;
		for(int i = 0; i < scount; i++)
		{
			if(verbose && (i%1000 == 0)) System.err.println("Copying sector " + (i+1) + " of " + scount + "...");
			long epos = cpos + wbfs_sec_sz;
			if(epos >= raw_size) epos = raw_size;
			if(keep_tbl[i])
			{
				raw.writeToStream(bos, cpos, epos);
				written += epos-cpos;
			}
			
			if(i == scount - 1)
			{
				//Last sector
				long lastsz = epos - cpos;
				if(lastsz < wbfs_sec_sz)
				{
					long padding = wbfs_sec_sz - lastsz;
					for(long j = 0; j < padding; j++)
					{
						bos.write(0);
						written++;
					}
				}
			}
			cpos = epos;
		}
		
		bos.close();
		
		if(verbose)
		{
			System.err.println("Written: 0x" + Long.toHexString(written));
			double comp = ((double)written/(double)raw_size) * 100.0;
			System.err.println("Compression Ratio: " + comp + "%");
		}
		
		return true;
	}
	
	public static boolean writeToWBFSICO(String raw_image_path, String outpath, WIcoDat wico, boolean verbose) throws IOException
	{
		return writeToWBFS(raw_image_path, outpath, estimateBestSectorSizeFactor(raw_image_path), wico, verbose);
	}
	
	/* ----- Information ----- */
	
	public void printInfo()
	{
		//TODO
	}
	
}
