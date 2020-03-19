package waffleoRai_Containers.nintendo;

import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import waffleoRai_Utils.CacheFileBuffer;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;

/*
 * Format
 * 
 * (0x0000)	Magic "WUci" [4]
 * (0x0004) Version [2]
 * (0x0006) Flags [2]
 * 		15 - Decrypted
 * 		14 - Game key at 0x20?
 * 		4 - Has icon?
 * 		3-0 - Image Type (Enum)
 * 			0: Wii U Disc
 * 			1: NAND
 * (0x0008) HD Sector Size (Factor/Shift) [1]
 * (0x0009) WUci Sector Size (Factor/Shift) [1]
 * (0x000A) Header End (In WUci Sectors) [2]
 * (0x000C) WUci Sector Count (Decomp) [4]
 * (0x0010) WUci Sector Count (This File) [4]
 * (0x0014) Game Code [4]
 * (0x0018) Icon Offset [4]
 * (0x001C) (Reserved) [4]
 * (0x0020) Game key [16]
 * (0x0030) LBA Table (pad to nearest 0x10)
 * (Variable) uIco file (if present)
 * 
 * Everything in the header through the LBA table must fit
 * into the first WUci sector.
 * 
 * 
 * uIco format (Icon)
 * (0x0000) Magic [4] "uIco"
 * (0x0004) Version [2]
 * (0x0006) Image Count [1]
 * (0x0007) Anim Event Count [1]
 * (0x0008) Image data offset [4]
 * (0x000C) Anim Data [2n] (pad to nearest 0x10)
 * 		Event[2]
 * 			llllllll lllliiii (l = frame count, i = img index)
 * (Variable) Image Data [0x10000 m]
 * 	(128x128 32bit)
 * 
 * 
 * 
 */

/*
 * UPDATES
 * 2020.02.15 | 1.0.0
 * 	Initial
 * 
 */

/**
 * Class for reading and writing WUci Wii U image files.
 * <br>WUci (.wuci) (Wii U Condensed Image) is a format 
 * I made in the vein of WBFS as a means of trimming empty sectors 
 * from the image while preserving offsets.
 * @author Blythe Hospelhorn
 * @version 1.0.0
 * @since February 15, 2020
 */
public class WUCImage {
	
	/*----- Constants -----*/
	
	public static final short WUCI_VERSION = 1;
	public static final short UICO_VERSION = 1;
	
	public static final String WUCI_MAGIC = "WUci";
	public static final String UICO_MAGIC = "uIco";
	
	public static final int MAX_WUCI_SECTORS = 65535;
	
	public static final int MIN_WUCI_SEC_SZ_S = 19; //512kb
	public static final int DEFO_HD_SEC_SZ_S = 9;
	
	public static final int IMAGE_TYPE_DISC = 0;
	public static final int IMAGE_TYPE_NAND = 1;
	
	public static final int STREAMBUFFER_SUBBUF_SIZE = 0x80000;
	public static final int STREAMBUFFER_SUBBUF_NUMBER = 1024;
	//Total: 512 MB
	
	/*----- Instance Variables -----*/
	
	private String wuci_path;
	
	private int decomp_wuci_sec_count;
	
	private int wuci_sec_sz;
	private int wuci_sec_shift;
	private int wuci_sec_count;
	private int header_secs;
	
	private int hd_sec_sz;
	private int hd_sec_shift;
	private int hd_sec_count;
	
	private boolean isDecrypted;
	private String gameCode;
	private int[] lba_table;
	private byte[] gameKey;
	private int imageType;
	
	private UIcoDat icon;
	
	/*----- Inner Classes -----*/
	
	public static class UIcoDat
	{
		private BufferedImage[] images;
		private int[][] anim_dat;
		
		public UIcoDat(int img_count, int anim_events)
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
		
		public long calculateSerializedSize()
		{
			long img_size = 0x10000;
			long sz = 0xc; //Header
			sz += (img_size * images.length);
			if(anim_dat != null)
			{
				sz += (anim_dat.length << 1);
			}
			while((sz % 0x10) != 0)sz++;
			return sz;
		}
		
		public void writeToFileBuffer(FileBuffer buff)
		{
			buff.printASCIIToFile(UICO_MAGIC);
			buff.addToFile(UICO_VERSION);
			buff.addToFile((byte)images.length);
			int acount = getAnimEventCount();
			buff.addToFile((byte)acount);
			
			//Calculate anim data size...
			int asize = 0;
			if(acount > 0)
			{
				asize = acount << 1;
				while(asize % 0x10 != 0) asize+=2;
			}
			
			buff.addToFile(asize + 12);
			
			//Anim data
			if(acount > 0)
			{
				for(int i = 0; i < acount; i++)
				{
					int frames = this.getAnimFrameCount(i);
					int iidx = this.getAnimImgIndex(i);
					int val = (frames & 0xFFF) << 4;
					val |= (iidx & 0xF);
					buff.addToFile((short)val);
				}
			}
			while(buff.getFileSize() % 0x10 != 0) buff.addToFile((byte)FileBuffer.ZERO_BYTE);
			
			//Image data
			for(int i = 0; i < this.getImageCount(); i++)
			{
				BufferedImage img = getImage(i);
				for(int y = 0; y < 128; y++)
				{
					for(int x = 0; x < 128; x++)
					{
						buff.addToFile(img.getRGB(x, y));
					}
				}
			}
			
		}
		
	}
	
	/*----- Construction -----*/
	
	private WUCImage(String path)
	{
		wuci_path = path;
	}
	
	/*----- Reading -----*/
	
	public static WUCImage readWUci(String path) throws IOException, UnsupportedFileTypeException
	{
		WUCImage wuci = new WUCImage(path);
		FileBuffer file = FileBuffer.createBuffer(path, true);
		
		//Search for magic number
		long cpos = file.findString(0, 0x10, WUCI_MAGIC);
		if(cpos != 0) throw new FileBuffer.UnsupportedFileTypeException("WUci magic number was not found!");
		
		file.skipBytes(6); //Skip version as well, for now.
		int flags = Short.toUnsignedInt(file.nextShort());
		wuci.hd_sec_shift = Byte.toUnsignedInt(file.nextByte());
		wuci.wuci_sec_shift = Byte.toUnsignedInt(file.nextByte());
		wuci.header_secs = Short.toUnsignedInt(file.nextShort());
		wuci.decomp_wuci_sec_count = file.nextInt();
		wuci.wuci_sec_count = file.nextInt();
		cpos = file.getCurrentPosition();
		wuci.gameCode = file.getASCII_string(cpos, 4); cpos += 4;
		long ico_off = Integer.toUnsignedLong(file.intFromFile(cpos)); cpos += 8;
		wuci.gameKey = new byte[16];
		for(int i = 0; i < 16; i++){wuci.gameKey[i] = file.getByte(cpos); cpos++;}
		
		//Set flags
		wuci.imageType = flags & 0xF;
		wuci.isDecrypted = (flags & 0x8000) != 0;
		boolean hasico = (flags & 0x0010) != 0;
		if((flags & 0x4000) == 0) wuci.gameKey = null;
		
		//Read LBA table
		int secs = wuci.decomp_wuci_sec_count;
		wuci.lba_table = new int[secs];
		file.setCurrentPosition(0x30);
		for(int i = 0; i < secs; i++)
		{
			wuci.lba_table[i] = Short.toUnsignedInt(file.nextShort());
		}
		
		//Read Icon
		if(hasico)
		{
			file.setCurrentPosition(ico_off);
			if(file.findString(ico_off, ico_off+0x10, UICO_MAGIC) != ico_off)
			{
				System.err.println("WUCImage.readWUci || Icon read failed: uIco magic number not found!");
			}
			else
			{
				file.skipBytes(6); //Skip version for now;
				int img_count = Byte.toUnsignedInt(file.nextByte());
				int aevent_count = Byte.toUnsignedInt(file.nextByte());
				long img_off = ico_off + Integer.toUnsignedLong(file.nextInt());
				UIcoDat icodat = new UIcoDat(img_count, aevent_count);
				wuci.icon = icodat;
				for(int i = 0; i < aevent_count;i++)
				{
					int val = Short.toUnsignedInt(file.nextShort());
					int len = (val >>> 4) & 0x0FFF;
					int iidx = val & 0xF;
					icodat.setAnimFrameCount(i, len);
					icodat.setAnimImgIndex(i, iidx);
				}
				file.setCurrentPosition(img_off);
				for(int i = 0; i < img_count; i++)
				{
					BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
					for(int y = 0; y < 128; y++)
					{
						for(int x = 0; x < 128; x++)
						{
							int argb = file.nextInt();
							img.setRGB(x, y, argb);
						}
					}
					icodat.setImage(img, i);
				}
			}
		}
		
		return wuci;
	}
	
	/*----- Writing -----*/
	
	public static int estimateMinSectorSizeFactor(long wud_size, UIcoDat icon, boolean forceHeaderToOne)
	{
		//WUci allows for header to be >1 wuci sector.
		//However, only the icon data can be in sectors beyond the first.
		//Everything through the LBA table must be in the first sector.
		
		long main_hsize = 0x30; //For the common stuff.
		long iconsize = 0;
		if(icon != null) iconsize = icon.calculateSerializedSize();
		
		int max_s = 28;
		for(int s = MIN_WUCI_SEC_SZ_S; s <= max_s; s++)
		{
			int sec_sz = 1 << s;
			int sec_count = (int)(wud_size >>> s);
			if((wud_size % sec_sz) != 0) sec_count++;
			if(sec_count > MAX_WUCI_SECTORS) continue;
			
			int lba_size = sec_count << 1;
			if((lba_size + main_hsize) > sec_sz) continue;
			if(forceHeaderToOne)
			{
				//Icon has to fit too
				long sz = lba_size + main_hsize + iconsize;
				if(sz > sec_sz) continue;
			}
			return s;
		}
		
		
		return 0;
	}
	
	public static boolean writeWUci(String wud_path, String outpath, byte[] key, boolean verbose) throws IOException
	{
		return writeWUci(wud_path, outpath, false, key, null, IMAGE_TYPE_DISC, verbose);
	}
	
	public static boolean writeWUci(String wud_path, String outpath, byte[] key, int imgtype, boolean verbose) throws IOException
	{
		return writeWUci(wud_path, outpath, false, key, null, imgtype, verbose);
	}
	
	public static boolean writeWUci(String wud_path, String outpath, boolean isDecrypted, byte[] key, UIcoDat icon, int imgtype, boolean verbose) throws IOException
	{
		if(!FileBuffer.fileExists(wud_path)){
			if(verbose) System.err.println("File at " + wud_path + " doesn't exist!");
			return false;
		}
		
		//Choose sector size
		long raw_size = FileBuffer.fileSize(wud_path);
		int sfac = estimateMinSectorSizeFactor(raw_size, icon, false);
		
		if(sfac < 19){
			if(verbose) System.err.println("WUci Size Factor " + sfac + " is too low!");
			return false;
		}
		int wuci_sec_sz = 1 << sfac;
		if(verbose)
		{
			System.err.println("WUci Sector Size Chosen: 0x" + Integer.toHexString(wuci_sec_sz));
		}

		//Map sectors...
		CacheFileBuffer raw = CacheFileBuffer.getReadOnlyCacheBuffer(wud_path, STREAMBUFFER_SUBBUF_SIZE, STREAMBUFFER_SUBBUF_NUMBER, true);
		long sec_count = raw_size >>> sfac;
		boolean partial = false;
		if(sec_count << sfac != raw_size){partial = true; sec_count++;}
		if(verbose)
		{
			System.err.println("Input raw image size: 0x" + Long.toHexString(raw_size) + " (" + raw_size + " bytes)");
			System.err.println("WUci Sectors: 0x" + Long.toHexString(sec_count) + " (" + sec_count + ")");
			System.err.println("Partial Sectors?: " + partial);
		}
		int scount = (int)sec_count;
		int[] lba_tbl = new int[scount];
		boolean[] keep_tbl = new boolean[scount];
		
		long cpos = 0;
		for(int i = 0; i < scount; i++)
		{
			if(verbose && (i%1000 == 0)) System.err.println("Scanning sector " + (i+1) + " of " + scount + "...");
			long epos = cpos + wuci_sec_sz;
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
		

		//Check size of LBA table & Icon size
		long lba_tbl_sz = (long)(lba_tbl.length << 1);
		int lba_tbl_end = (int)(lba_tbl_sz + 0x30);
		while(lba_tbl_end % 0x10 != 0) lba_tbl_end++;
		int icon_size = 0;
		if(icon != null) icon_size = (int)icon.calculateSerializedSize();
		int hsz = lba_tbl_end + icon_size;
		while(hsz > (wuci_sec_sz * initsec)) initsec++;
		if(initsec > 1)
		{
			int diff = initsec - 1;
			System.err.println("WUci header will require " + initsec + " sectors.");
			for(int i = 0; i < scount; i++)
			{
				if(keep_tbl[i]) lba_tbl[i] += diff;
			}
		}
		
		//Prepare header
		int headersize = initsec * wuci_sec_sz;
		FileBuffer header = new FileBuffer(headersize, true);
		header.printASCIIToFile(WUCI_MAGIC);
		header.addToFile(WUCI_VERSION);
		int flags = 0;
		flags |= (imgtype & 0xF);
		if(icon != null) flags |= 0x10;
		if(isDecrypted) flags |= 0x8000;
		if(key != null) flags |= 0x4000;
		header.addToFile((short)flags);
		header.addToFile((byte)DEFO_HD_SEC_SZ_S);
		header.addToFile((byte)sfac);
		header.addToFile((short)initsec);
		header.addToFile(scount);
		int keepsec = (int)(keepsz >>> sfac);
		header.addToFile(keepsec + initsec);
		String gamecode = raw.getASCII_string(0x6, 4);
		header.printASCIIToFile(gamecode);
		header.addToFile(lba_tbl_end);
		header.addToFile(0);
		if(key != null)
		{
			for(int i = 0; i < 16; i++) header.addToFile(key[i]);
		}
		else
		{
			for(int i = 0; i < 16; i++) header.addToFile(FileBuffer.ZERO_BYTE);
		}
		
		//LBA table.
		for(int i = 0; i < scount; i++) header.addToFile((short)lba_tbl[i]);
		while((header.getFileSize() % 0x10) != 0) header.addToFile(FileBuffer.ZERO_BYTE);

		//Icon
		if(icon != null) icon.writeToFileBuffer(header);
		
		//Zero fill header
		while(header.getFileSize() < headersize) header.addToFile(FileBuffer.ZERO_BYTE);
		
		//Write to file...
		long written = 0;
		BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outpath));
		header.writeToStream(bos);
		written += header.getFileSize();
		
		//Copy data
		cpos = 0;
		for(int i = 0; i < scount; i++)
		{
			if(verbose && (i%1000 == 0)) System.err.println("Copying sector " + (i+1) + " of " + scount + "...");
			long epos = cpos + wuci_sec_sz;
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
				if(lastsz < wuci_sec_sz)
				{
					long padding = wuci_sec_sz - lastsz;
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
	
	/*----- Getters -----*/
	
	public String getReadPath(){return this.wuci_path;}
	public long getImageSize(){return (long)this.decomp_wuci_sec_count << wuci_sec_shift;}
	public int getWUciSectorSize(){return this.wuci_sec_sz;}
	public int getWUciSectorCount(){return this.wuci_sec_count;}
	public int getHeaderSectorCount(){return this.header_secs;}
	public int getHDSectorSize(){return this.hd_sec_sz;}
	public int getHDSectorFactor(){return this.hd_sec_shift;}
	public int getHDSectorCount(){return this.hd_sec_count;}
	public boolean isDecrypted(){return this.isDecrypted;}
	public String getGameCode(){return this.gameCode;}
	public int getImageType(){return this.imageType;}
	public UIcoDat getIconData(){return this.icon;}


}
