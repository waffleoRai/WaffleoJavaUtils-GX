package waffleoRai_Containers.psx;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import waffleoRai_Image.Animation;
import waffleoRai_Image.FourBitPalette;
import waffleoRai_Image.Palette;
import waffleoRai_Image.PaletteRaster;
import waffleoRai_Image.SimpleAnimation;
import waffleoRai_Image.psx.PSXImages;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.MultiFileBuffer;
import waffleoRai_Utils.Optcombinator;

/*UPDATES
 * 
 * 2017.06.25 | 1.2.0
 * 
 * 2017.11.05 | 1.2.0 -> 1.2.1
 * 	Javadoc
 * 
 * 2017.11.11 | 1.2.1 -> 2.0.0
 * 	Improved deletion and undeletion mechanisms and interfaces
 * 	Improved FileBuffer management:
 * 		- Direct references to internal buffers will never be returned. 
 * 			Only read-only locked buffers or complete copies.
 * 		- Serialization does not utilize fresh copies, but CompositeBuffers
 * 	Added some methods to slightly improve access interface to object.
 * 
 * 2020.06.16 | 2.0.0 -> 2.1.0
 * 	Updated to use improved image/animation interfaces & classes (eg. swap Picture for BufferedImage)
 * 	Removed use of deprecated CompositeBuffer
 */

/**
 * PSX Memory Card simulation class.
 * <br>Everything is parsed and stored in objects, except Block 0.
 * The directory is parsed, but the rest of the block is just left serialized
 * as the "blockZero" FileBuffer object.
 * @author Blythe Hospelhorn
 * @version 2.1.0
 * @since June 16, 2020
 */
public class PSXMemoryCard {
	
	/**
	 * A 2-byte ASCII code that should be found at the beginning of a raw PSX memory card image.
	 */
	public static final String MEMID = "MC";
	
	/**
	 * A 2-byte ASCII code that should be found at the beginning of
	 * every block (except block 0) on a PSX memory card image.
	 */
	public static final String BLOCKID = "SC";
	
	/**
	 * Number of blocks on a PSX memory card.
	 */
	public static final int CARD_BLOCKS = 16;
	
	/**
	 * Number of frames (or "sectors") in a single block of a PSX memory card.
	 */
	public static final int FRAMES_PER_BLOCK = 64;
	
	/**
	 * The size, in bytes, of a single block on a PSX memory card. (8 kb)
	 */
	public static final int BLOCKSIZE = 0x2000;
	
	/**
	 * The size, in bytes, of a single frame/sector on a PSX memory card. (128 bytes)
	 */
	public static final int FRAMESIZE = 0x80;
	
	/**
	 * The String the Java CharacterSet class uses to reference the Shift-JIS encoding scheme.
	 * <br>User visible file names (game names) on a memory card are encoded using Shift-JIS 
	 * when serialized.
	 */
	public static final String SHIFT_JIS_CHARSET_NAME = "SJIS";
	
	/**
	 * A zero byte.
	 */
	public static final byte ZERO = 0x00;
	
	/**
	 * The 0xFF byte.
	 */
	public static final byte FILLER = (byte)0xFF;
	
	/**
	 * Internal directory allowing quick lookup of files
	 * by file name.
	 */
	private Map<String, MCFile> directory;
	
	/**
	 * A raw copy of block 0. In PSX memory cards, the frames 1-15 of block 0 is the directory.
	 * Because block 0 is not a file, it is kept as a raw copy for the purpose of direct
	 * access from the PSX OS.
	 * <br>Block 0 also contains information relating to corruption and backup of later block data,
	 * though this should in theory not be an issue with emulators. These data are not parsed
	 * by this class, but can be obtained by reading raw information out of the buffer referenced
	 * by this instance variable.
	 */
	private FileBuffer blockZero;
	
	/**
	 * A simple array with each element correlating to each of the memory card's 16 blocks.
	 * Each element refers to a String denoting the name of the file on the card that occupies
	 * that block.
	 * <br>null and empty elements indicate that the block is free.
	 */
	private String[] blockDir;
	
	/* --- Subclasses --- */
	
	/**
	 * A wrapper for a file on a memory card. Because a single file on a memory card
	 * can be split over non-sequential blocks, something is required to mediate accurate
	 * accession of data from a given file. Direct access to data by block and frame must
	 * also be allowed. 
	 * @author Blythe Hospelhorn
	 * @version 1.0.1
	 * @since November 5, 2017
	 *
	 */
 	private static class MCFile
	{
 		
 		/* --- Instance Variables --- */
 		
 		/**
 		 * The number of blocks occupied by this file.
 		 */
		private int numBlocks;
		
		/**
		 * The indices on the card of each block.
		 * <br>In this array, index corresponds to the sequential index of the block within the file,
		 * and the element corresponds to the index of the block on the memory card.
		 * <br>(ie. If blockIndices[1] = 7, then the second block of this file resides in block 7 on
		 * the memory card.)
		 */
		private int[] blockIndices;
		
		/**
		 * The ASCII name of the file the OS uses for accession.
		 */
		private String fileName;
		
		/**
		 * The number of animation frames in the icon that graphically represents this file.
		 */
		private int iconFrames;
		
		/**
		 * The actual frames of the icon.
		 */
		private PaletteRaster[] icon;
		
		/**
		 * The palette used to render the icon.
		 */
		private Palette iconPalette;
		
		/**
		 * The Unicode user visible name of the file.
		 */
		private String name;
		
		/**
		 * The full file pieced together without block breaks.
		 */
		private FileBuffer compositeFile;
		
		/* --- Constructors --- */
		
		/**
		 * Construct an empty Memory Card file with the given system name.
		 * @param fName System (ASCII) name of the file.
		 */
		public MCFile(String fName)
		{
			this.fileName = fName;
			this.setNumBlocks(1);
			this.setIconFrames(1);
			this.name = null;
		}
		
		/* --- Getters --- */
		
		/**
		 * Get the number of blocks occupied by this file.
		 * @return The number of blocks this file occupies.
		 */
		public int getNumBlocks()
		{
			return this.numBlocks;
		}
		
		/**
		 * Get the index of a block on the memory card of a block in the file given
		 * the file relative sequential index of the block.
		 * <br>For example, if the file is 3 non-sequential blocks where the first block has
		 * been placed in memory card block 4, the second has been placed in memory card block 6,
		 * and the third has been placed in memory card block 7, and you wish to know where the second
		 * block (index 1) is on the memory card, passing this function the integer "1" will return "6".
		 * @param blockNumber The file relative index of the desired block.
		 * @return The card relative index of the desired block.
		 */
		public int getBlockIndex(int blockNumber)
		{
			if (blockNumber < 0 || blockNumber > numBlocks) return -1;
			return this.blockIndices[blockNumber];
		}
		
		/**
		 * Get the ASCII system accession name of this file.
		 * @return ASCII String representing the file name.
		 */
		public String getFileName()
		{
			return this.fileName;
		}
		
		/**
		 * Get the palette (in a Palette object) used to render the icon associated
		 * with this file.
		 * @return File icon Palette.
		 */
		public Palette getIconPalette()
		{
			return this.iconPalette;
		}
		
		/**
		 * Get the name of the file as presented to a user in the PlayStation interface.
		 * @return String representing the (Unicode enabled) visible file name.
		 */
		public String getGameName()
		{
			return this.name;
		}
		
		/**
		 * Get the actual file data. This method returns a Read-only locked reference.
		 * The proper methods must be called to modify file data.
		 * @return A FileBuffer containing the composite file contents.
		 */
		public FileBuffer getContents()
		{
			FileBuffer contents = compositeFile.createReadOnlyCopy(0, compositeFile.getFileSize());
			return contents;
		}
		
		/**
		 * Get a specific frame of the animated icon representing the file.
		 * @param frameNumber Index of the desired frame. This value must be between 0 (inclusive)
		 * and the number of frames (exclusive).
		 * @return The frame as a Picture if index is valid.
		 * <br>null if the index is not valid.
		 */
		public PaletteRaster getIconFrame(int frameNumber)
		{
			if (this.icon == null) return null;
			if (frameNumber < 0) frameNumber = 0;
			if (frameNumber >= this.iconFrames) frameNumber = this.iconFrames - 1;
			return this.icon[frameNumber];
		}
		
		/**
		 * Get the number of frames in the animated icon representing the file.
		 * @return The number of frames in the icon.
		 */
		public int getNumIconFrames()
		{
			return this.iconFrames;
		}
		
		/* --- Setters --- */
		
		/**
		 * Set or reset the number of blocks occupied by this file.
		 * This will reallocate the block index correlation array.
		 * @param nBlocks Number of blocks occupied by this file.
		 */
		public void setNumBlocks(int nBlocks)
		{
			this.numBlocks = nBlocks;
			this.blockIndices = new int[this.numBlocks];
		}
		
		/**
		 * Set the block index of the file block relative to the memory card.
		 * @param blockNumber Index of the block relative to the file.
		 * @param blockindex Index of the block on the memory card where the file block is located.
		 */
		public void setBlockIndex(int blockNumber, int blockindex)
		{
			if (blockNumber < 0 || blockNumber > numBlocks) return;
			this.blockIndices[blockNumber] = blockindex;
		}
		
		/**
		 * Set or reset the number of frames in the animated file icon.
		 * This will erase and reallocate the frame array.
		 * @param iFrames Desired number of icon frames.
		 */
		public void setIconFrames(int iFrames)
		{
			this.iconFrames = iFrames;
			this.icon = new PaletteRaster[iFrames];
		}
		
		/**
		 * Set the user visible name of the file.
		 * @param name The new name to use.
		 */
		public void setGameName(String name)
		{
			this.name = name;
		}
		
		/**
		 * Set the palette to be used for rendering the animated file icon.
		 * @param myPalette Palette to use.
		 */
		public void setPalette(Palette myPalette)
		{
			this.iconPalette = myPalette;
		}
		
		/**
		 * Directly set a frame of the animated file icon.
		 * @param frame Frame to use.
		 * @param frameNumber Index in icon animation to set new frame at.
		 * Must be between 0 (inclusive) and the number of frames in the icon (exclusive).
		 */
		public void setIconFrame(PaletteRaster frame, int frameNumber)
		{
			if (frameNumber < 0 || frameNumber >= this.iconFrames) return;
			this.icon[frameNumber] = frame;
		}
		
		/**
		 * Directly set the data. Checks will be run to ensure that data is small
		 * enough to fit within the number of blocks specified by the parameters in this
		 * file instance.
		 * <br><br>WARNING: This does NOT update ANY other aspect of the file. At best, it will
		 * reject or trim the incoming data. However, it is up to memory card management methods to
		 * ensure that there is a place for the data on the card.
		 * @param dat The data to set as the contents for this file. This buffer SHOULD include
		 * the memory card file header information for the sake of easier offset calculation
		 * for system retrieval. Other methods must be called to keep header information synced
		 * between object and serialized copy.
		 */
		public void setData(FileBuffer dat)
		{
			this.compositeFile = dat;
		}
		
		/* --- Object/Serial Synchronization --- */
		
		private void updateFrameChecksum(int block, int frame)
		{
			int bOffset = block * PSXMemoryCard.BLOCKSIZE;
			int fOffset = frame * PSXMemoryCard.FRAMESIZE;
			int offset = bOffset + fOffset;
			int xored = Byte.toUnsignedInt(this.compositeFile.getByte(offset));
			for (int i = 1; i < PSXMemoryCard.FRAMESIZE - 1; i++)
			{
				int aByte = Byte.toUnsignedInt(this.compositeFile.getByte(offset + i));
				xored ^= aByte;
			}
			offset = offset + PSXMemoryCard.FRAMESIZE - 1;
			compositeFile.replaceByte((byte)xored, offset);
		}
		
		/**
		 * Update the checksum bytes for every frame in a given block in this file.
		 * @param block Index of block relative to file (not card) to update checksums for.
		 */
		public void updateChecksums(int block)
		{
			if (block < 0) return;
			if (block >= numBlocks) return;
			for (int i = 0; i < 64; i++) updateFrameChecksum(block, i);
		}
		
		/**
		 * Update the serialized header within the file contents to accurately reflect any information
		 * altered in the object instance variables.
		 */
		public void refreshDatHeaderSerialization()
		{
			if (this.compositeFile == null)
			{
				this.compositeFile = new FileBuffer(this.numBlocks * BLOCKSIZE, false);
				this.compositeFile.printASCIIToFile(BLOCKID);
			}
			this.compositeFile.setEndian(false);
			while (this.compositeFile.getFileSize() < this.numBlocks * BLOCKSIZE) this.compositeFile.addToFile((byte)0x00);
			this.compositeFile.replaceByte((byte)BLOCKID.charAt(0), 0x00);
			this.compositeFile.replaceByte((byte)BLOCKID.charAt(1), 0x01);
			byte ifFlag = (byte)(this.iconFrames + 0x10);
			this.compositeFile.replaceByte(ifFlag, 0x02);
			this.compositeFile.replaceByte((byte)0x01, 0x03);
			if (this.name.length() > 32) this.name = this.name.substring(0, 32);
			this.compositeFile.addEncoded_string(SHIFT_JIS_CHARSET_NAME, this.name, 0x04);
			FileBuffer ps = PSXImages.serialize16ColorPalette((FourBitPalette)this.iconPalette);
			for (int i = 0; i < ps.getFileSize(); i++)
			{
				this.compositeFile.replaceByte(ps.getByte(i), 0x60 + i);
			}
			for (int f = 0; f < this.iconFrames; f++)
			{
				FileBuffer iFrame = PSXImages.serializeBitMap4(this.icon[f]);
				for (int i = 0; i < iFrame.getFileSize(); i++)
				{
					this.compositeFile.replaceByte(iFrame.getByte(i), ((f + 1)*FRAMESIZE) + i);
				}
			}
			
			//Update checksum at the end of affected frames...
			updateChecksums(0);
		}
		
		/**
		 * Updates object info in MC file to correspond to altered serialized information.
		 */
		/*public boolean refreshDatHeaderInfo()
		{
			boolean b = true;
			b = b && refreshDatHeaderInfo_Frame0();
			for (int i = 0; i < this.iconFrames; i++)
			{
				b = b && refreshDatHeaderInfo_IconFrames(i);
			}
			return b;
		}*/
		
		/**
		 * Update the instance variables to reflect the data in the first frame of the serialized
		 * file. This information includes the user visible name, number of frames in the icon, and
		 * the icon palette. It also checks for the file ID ("SC")
		 * @return True - If the file appears to be valid (ID was found).
		 * <br>False - If file appears to be corrupt or deleted.
		 */
		private boolean refreshDatHeaderInfo_Frame0()
		{
			//Check for ID
			if (compositeFile.findString(0, 0x10, BLOCKID) == 0)
			{
				int fNum = Byte.toUnsignedInt(compositeFile.getByte(0x02)) - 0x10;
				this.setIconFrames(fNum);
				String gameTitle = compositeFile.readEncoded_string(SHIFT_JIS_CHARSET_NAME, 0x04, 0x43);
				this.setGameName(gameTitle);
				Palette plt = PSXImages.parse16ColorPalette(compositeFile, 0x60, false);
				this.setPalette(plt);
				return true;
			}
			return false;
		}
		
		/**
		 * Update the instance variable animation frames to reflect the data in the serialized
		 * copy of the file.
		 * @param fframeNumber Number of frames as found earlier in the header.
		 * @return True - If icon frames could be parsed.
		 * <br>False - If there was an error reading the frames, such as there being no palette set
		 * or the frame number being impossible.
		 */
		private boolean refreshDatHeaderInfo_IconFrames(int fframeNumber)
		{
			if (this.iconPalette != null && fframeNumber < this.iconFrames)
			{
				PaletteRaster p = PSXImages.parseBitMap4(compositeFile, this.iconPalette, (fframeNumber * FRAMESIZE), 16, 16);
				this.setIconFrame(p, fframeNumber - 1);
				return true;
			}
			return false;
		}

		/* --- Parsing --- */
		
		/* --- Serialization --- */
		
		/**
		 * Given a memory card block index, check to see if this file covers that MC block,
		 * and if it does, return the index of that block relative to the file.
		 * <br>In essence, this does the reverse of getBlockNumber.
		 * <br>For example, if you want to see if MC block 7 contains a piece of this file,
		 * and the 2nd block of this file is stored in MC block 7, this function will return 1
		 * (index of 2nd file relative block).
		 * @param blockNumber Index of MC block to look up.
		 * @return Index of file relative block if the MC block is used by this file.
		 * <br>-1 if the MC block is not used by this file.
		 */
		private int translateBlockIndex(int blockNumber)
		{
			if (blockNumber < 0) return -1;
			if (blockNumber > 16) return -1;
			for (int i = 0; i < this.numBlocks; i++)
			{
				if (this.blockIndices[i] == blockNumber) return i;
			}
			return -1;
		}
		
		/**
		 * Generate a directory entry frame for a block in this file given the MC block index.
		 * This method will only work if the MC block given is used by this file.
		 * @param blockNumber Index of memory card block.
		 * @return Frame that can be saved to MC Block 0 directory for a block in this file.
		 * <br>null if this file does not use the block at the provided index.
		 */
		public FileBuffer generateDirEntry(int blockNumber, boolean deleted)
		{
			int checksum = 0;
			int myi = this.translateBlockIndex(blockNumber);
			if (myi < 0) return null;
			FileBuffer DE = new FileBuffer(FRAMESIZE, false);
			int ocode = 0x51;
			if (!deleted)
			{
				if (myi > 0 && myi < this.numBlocks - 1) ocode = 0x52;
				else if (myi != 0 && myi == this.numBlocks - 1) ocode = 0x53;
			}
			else
			{
				if (myi > 0 && myi < this.numBlocks - 1) ocode = 0xA2;
				else if (myi != 0 && myi == this.numBlocks - 1) ocode = 0xA3;
				else if (myi == 0) ocode = 0xA1;
			}
			int nextB = 0xFFFF;
			if (ocode == 0x51 || ocode == 0x52 || ocode == 0xA1 || ocode == 0xA2)
			{
				if (myi + 1 < this.numBlocks)
				{
					nextB = this.blockIndices[myi + 1];
					nextB--; //Encoded with MC block 1 as "0" and block 15 as "14".
				}
			}
			int fSize = numBlocks * BLOCKSIZE;
			DE.addToFile(ocode);
			DE.addToFile(fSize);
			DE.addToFile((short)nextB);
			DE.printASCIIToFile(this.fileName);
			while(DE.getFileSize() < 0x1D) DE.addToFile(ZERO);
			while(DE.getFileSize() < FRAMESIZE - 1) DE.addToFile(ZERO);
			for (int i = 0; i < FRAMESIZE - 1; i++)
			{
				checksum = checksum ^ Byte.toUnsignedInt(DE.getByte(i));
			}
			DE.addToFile((byte)checksum);
			return DE;
		}
	
		/**
		 * Get a block in this file given the index of a block on the Memory Card. This method
		 * will do nothing if the block with the provided MC index is not used by this file.
		 * <br>This method returns a read-only reference copy. Do not attempt to write to it.
		 * @param blockNumber Index of block on Memory Card to retrieve.
		 * @return A FileBuffer containing the block data, if found.
		 * <br>null if the MC block is not used by this file or there was an error extracting
		 * the block data.
		 */
		public FileBuffer serializeBlock(int blockNumber)
		{
			int myi = this.translateBlockIndex(blockNumber);
			if (myi < 0) return null;
			if (myi == 0) this.refreshDatHeaderSerialization();
			//FileBuffer block = new FileBuffer(BLOCKSIZE, false);
			int sti = myi * BLOCKSIZE;
			FileBuffer block = compositeFile.createReadOnlyCopy(sti, sti + BLOCKSIZE);
			return block;
		}
	
		/**
		 * Get a frame from this file given the indices of the Memory Card block and frame.
		 * This method will return null if the frame with the provided indices is not used by this file.
		 * @param blockNumber Index of block on Memory Card of frame to retrieve.
		 * @param frameNumber Index of frame within block to retrieve.
		 * @return A FileBuffer containing the frame data, if found.
		 * <br>null if the MC frame is not used by this file or there was an error extracting
		 * the frame data.
		 */
		public FileBuffer serializeFrame(int blockNumber, int frameNumber)
		{
			if (frameNumber < 0 || frameNumber >= 64) return null;
			int myi = translateBlockIndex(blockNumber);
			if (myi < 0) return null;
			int bOffset = myi * BLOCKSIZE;
			int fOffset = frameNumber * FRAMESIZE;
			int offset = bOffset + fOffset;
			
			/*FileBuffer f = new FileBuffer(FRAMESIZE, false);
			for (int i = 0; i < FRAMESIZE; i++)
			{
				f.addToFile(this.compositeFile.getByte(offset + i));
			}
			return f;*/
			
			FileBuffer p = this.compositeFile.createReadOnlyCopy(offset, offset + FRAMESIZE);
			return p;
		}
		
		/* --- Writing --- */
		
		/**
		 * Replace a frame in this file with a new frame.
		 * <br>If frame is within the header region, object header information will be
		 * updated as well.
		 * @param f Buffer containing new frame data.
		 * @param frOff Offset within buffer f where new frame data starts. Next 0x80 bytes
		 * read from this (replacing with 0x00 if not enough) will become the new frame.
		 * @param blockNumber Index of block on memory card where target frame is located.
		 * @param frameNumber Index of frame relative to block to replace.
		 * @return True - If replacement was successful.
		 * <br>False - Otherwise.
		 */
		public boolean replaceFrame(FileBuffer f, int frOff, int blockNumber, int frameNumber)
		{
			if (frameNumber < 0 || frameNumber >= 64) return false;
			int myi = translateBlockIndex(blockNumber);
			if (myi < 0) return false;
			int bOffset = myi * BLOCKSIZE;
			int fOffset = frameNumber * FRAMESIZE;
			int offset = bOffset + fOffset;
			
			for (int i = 0; i < FRAMESIZE; i++)
			{
				if (i + frOff < f.getFileSize())
				{
					this.compositeFile.replaceByte(f.getByte(i + frOff), offset + i);
				}
				else
				{
					this.compositeFile.replaceByte(ZERO, offset + i);
				}
			}
			
			if (myi == 0)
			{
				if (frameNumber == 0)
				{
					if (f.findString(0, 0x10, BLOCKID) == 0)
					{
						refreshDatHeaderInfo_Frame0();
					}
				}
				else if (frameNumber >= 1 && frameNumber <= this.iconFrames)
				{
					refreshDatHeaderInfo_IconFrames(frameNumber);
				}
			}
			
			return true;
		}
		
		/**
		 * Write data to this file from another buffer.
		 * <br>If header frames are written to, object instance variables will be updated
		 * to reflect new information.
		 * @param f The source buffer.
		 * @param readOff The offset within the source buffer to start reading data from.
		 * @param len The number of bytes to write to this file.
		 * @param writeOff The offset within this file to start writing data.
		 * @return The number of bytes successfully written.
		 * <br>-1 if there was an error.
		 */
		public int writeBytes(FileBuffer f, int readOff, int len, int writeOff)
		{
			if (writeOff < 0 || readOff < 0) return -1;
			if (f == null) return -1;
			int stBlock = writeOff / BLOCKSIZE;
			int stFrame = (writeOff % BLOCKSIZE) / FRAMESIZE;
			int ltBlock = (writeOff + len) / BLOCKSIZE;
			int ltFrame = ((writeOff + len) % BLOCKSIZE) / FRAMESIZE;
			int bCount = 0;
			
			if (ltBlock >= this.numBlocks)
			{
				ltBlock = this.numBlocks - 1;
				ltFrame = FRAMES_PER_BLOCK - 1;
			}
			
			for (int i = 0; i < len; i++)
			{
				if (i + writeOff < compositeFile.getFileSize())
				{
					if (i + readOff < f.getFileSize())
					{
						compositeFile.replaceByte(f.getByte(readOff + i), writeOff + i);
					}
					else compositeFile.replaceByte(ZERO, i + writeOff);
					bCount++;
				}
				else break;
			}
			
			//Check if need to refresh header info
			if (stBlock == 0)
			{
				if (stFrame == 0)
				{
					this.refreshDatHeaderInfo_Frame0();
				}
				for (int i = 0; i < this.iconFrames; i++)
				{
					if (stFrame <= i + 1 && (ltBlock > 0 || ltFrame >= i + 1))
					{
						this.refreshDatHeaderInfo_IconFrames(i + 1);
					}	
				}
			}
			
			return bCount;
		}

	}

 	/**
 	 * A simple structure to represent the data in a directory frame.
 	 * @author Blythe Hospelhorn
 	 * @version 1.0.1
 	 * @since November 5, 2017
 	 */
	private static class DirFrame
	{
		public int posIndicator;
		public int fileSize;
		public short nextBlock;
		public String fileName;
		
		/**
		 * Construct an empty DirFrame. Instance variables are public and can
		 * be accessed and changed directly within the scope of the Memory Card class.
		 */
		public DirFrame()
		{
			this.posIndicator = -1;
			this.fileSize = -1;
			this.nextBlock = -1;
			this.fileName = null;
		}
	
	}
	
	/**
	 * A checked exception for a fatal error reading a PSX memory card.
	 * @author Blythe Hospelhorn
	 * @version 1.0.0
	 * @since June 25, 2017
	 */
	public static class PSXMemoryCardReadErrorException extends Exception
	{
		private static final long serialVersionUID = -8675248075537781964L;
	}
	
	/**
	 * A checked exception for a fatal error writing to PSX memory card.
	 * @author Blythe Hospelhorn
	 * @version 1.0.0
	 * @since June 25, 2017
	 */
	public static class PSXMemoryCardWriteErrorException extends Exception
	{
		private static final long serialVersionUID = 4176345060194731633L;	
	}
	
	/**
	 * A checked exception to throw when a memory card has no space for writing new files.
	 * @author Blythe Hospelhorn
	 * @version 1.0.0
	 * @since June 25, 2017
	 */
	public static class PSXMemoryCardInsufficientSpaceException extends Exception
	{
		private static final long serialVersionUID = 2617700930367806309L;
	}
	
	/* --- Constructors --- */
	
	/**
	 * Construct a fresh, empty memory card.
	 * Instantiate the internal objects so that data addition may be immediately possible.
	 */
	public PSXMemoryCard()
	{
		this.directory = new HashMap<String, MCFile>();
		this.blockDir = new String[16];
		this.blockDir[0] = "HEADER";
		for (int i = 1; i < 16; i++) this.blockDir[i] = null;
		this.blockZero = new FileBuffer(BLOCKSIZE, false);
	}
	
	/**
	 * Construct a memory card from a raw memory card data file on disk.
	 * <br>Such files may have the .mcr extension.
	 * @param filePath Path to memory card file to parse.
	 * @throws PSXMemoryCardReadErrorException If there is an error reading or parsing the file at the
	 * provided path.
	 * @throws IOException If there is an error reading from disk.
	 */
	public PSXMemoryCard(String filePath) throws PSXMemoryCardReadErrorException, IOException
	{
		this.directory = new HashMap<String, MCFile>();
		FileBuffer cardFile = new FileBuffer(filePath, false);
		this.blockDir = new String[16];
		this.blockDir[0] = "HEADER";
		for (int i = 1; i < 16; i++) this.blockDir[i] = null;
		this.blockZero = new FileBuffer(BLOCKSIZE, false);
		this.parseMemoryCard(cardFile);
	}
	
	/* --- Calculations --- */
	
	/**
	 * Calculate the raw offset within a memory card given a block index, a frame index,
	 * and the offset within the frame.
	 * @param blockNumber Index of block. Acceptable values are 0-15.
	 * @param frameNumber Index of frame. Acceptable values are 0-63.
	 * @param foffset Offset within frame. Must be between 0 (inclusive) and the size of a
	 * memory card frame (exclusive).
	 * @return Offset relative to entire memory card of byte at specified coordinate.
	 */
	public int calculateOffset(int blockNumber, int frameNumber, int foffset)
	{
		if (blockNumber < 0 || blockNumber >= CARD_BLOCKS) return -1;
		if (frameNumber < 0 || frameNumber >= FRAMES_PER_BLOCK) return -1;
		if (foffset < 0 || foffset >= FRAMESIZE) return -1;
		int os = blockNumber * BLOCKSIZE;
		os += frameNumber * FRAMESIZE;
		os += foffset;
		return os;
	}
	
	/**
	 * Get whether the specified memory card block contains data for a file.
	 * @param blockNumber Index of the block on the card to check for data. Valid values: 0-15.
	 * @return True - If the block contains data for a live file or is block 0.
	 * <br>False - If the block is empty or only contains data for a deleted file.
	 */
	public boolean blockContainsData(int blockNumber)
	{
		if (blockNumber < 0 || blockNumber >= 16) return false;
		if (blockNumber == 0) return true;
		String s = this.blockDir[blockNumber];
		if (s == null) return false;
		if (s.isEmpty()) return false;
		if (s.charAt(0) == '*') return false;
		return true;
	}
	
	/* --- Parsers --- */
	
	/**
	 * Check if the provided FileBuffer has the PSX memory card ASCII ID ("MC") at the beginning,
	 * indicating that it is potentially a raw memory card file (such as ePSXe .mcr).
	 * <br>At the moment, this class only handles raw memory card files.
	 * @param cardfile Buffer containing file to check.
	 * @return True - If ASCII ID was found at the beginning.
	 * <br>False - If ASCII ID was not found or not found in the correct position.
	 */
	private boolean checkMCID(FileBuffer cardfile)
	{
		int check = cardfile.findString(0, 0x10, MEMID);
		return check == 0;
	}
	
	/**
	 * Check if the provided FileBuffer has the ASCII block ID ("SC") at the beginning of the 
	 * given block.
	 * @param cardfile Buffer containing file to check. Should be a raw memory card file.
	 * @param blockIndex Index of block to check. Valid values: 0-15. (0 forwards to checkMCID)
	 * @return True - If block ASCII ID was found.
	 * <br>False - If block ASCII ID was not found or not in the correct position. If block index
	 * was invalid.
	 */
	private boolean checkBlockID(FileBuffer cardfile, int blockIndex)
	{
		if (blockIndex == 0) return this.checkMCID(cardfile);
		if (blockIndex < 0 || blockIndex >= 16) return false;
		int bStart = blockIndex * BLOCKSIZE;
		int check = cardfile.findString(bStart, bStart + 0x10, BLOCKID);
		return check == bStart;
	}
	
	/**
	 * Parse one frame of a presumed memory card file as if it were a Block 0 directory entry.
	 * @param cardfile File to parse information from.
	 * @param frameIndex Index of frame where directory of interest is. Valid values: 1 - 15.
	 * @return A DirFrame structure if frame index was valid and information could be read.
	 * <br>null if frame index was invalid.
	 * <br>Returned DirFrame may contain nonsense if cardfile is not properly formatted.
	 */
	private DirFrame parseOneDirFrame(FileBuffer cardfile, int frameIndex)
	{
		if (frameIndex < 1 || frameIndex >= 16) return null;
		DirFrame myFrame = new DirFrame();
		int ppos = frameIndex * FRAMESIZE;
		myFrame.posIndicator = cardfile.intFromFile(ppos);
		ppos += 4;
		myFrame.fileSize = cardfile.intFromFile(ppos);
		ppos += 4;
		myFrame.nextBlock = cardfile.shortFromFile(ppos);
		myFrame.nextBlock++;
		ppos += 2;
		myFrame.fileName = cardfile.getASCII_string(ppos, '\0');
		return myFrame;
	}
	
	/**
	 * Parse the Block 0 directory of a presumed raw memory card file.
	 * <br>This method only parses the directory to determine what files are present and in which
	 * blocks they are stored. This method does not parse the files themselves.
	 * @param cardfile Raw memory card file data to parse.
	 * @throws PSXMemoryCardReadErrorException If the file is not formatted correctly.
	 */
	private void parseDirectoryFrames(FileBuffer cardfile) throws PSXMemoryCardReadErrorException
	{
		//System.out.println("MemoryCard.parseDirectoryFrames || Called");
		for (int f = 1; f < 16; f++)
		{
			//System.out.println("MemoryCard.parseDirectoryFrames || Frame " + f);
			DirFrame frame = parseOneDirFrame(cardfile, f);
			//System.out.println("MemoryCard.parseDirectoryFrames || DirFrame read... ");
			//System.out.println("\tMemoryCard.parseDirectoryFrames || posIndicator = " + Integer.toHexString(frame.posIndicator));
			//System.out.println("\tMemoryCard.parseDirectoryFrames || fileSize = " + frame.fileSize);
			//System.out.println("\tMemoryCard.parseDirectoryFrames || nextBlock = " + frame.nextBlock);
			//System.out.println("\tMemoryCard.parseDirectoryFrames || fileName = " + frame.fileName);
		/*Ignore any frames that refer to middle or end blocks - only look at first blocks
		 * Everything else in the file will get parsed with the first block
		 */
			//Here, only the indices of blocks and size need to be set for parser to be able to find things
			switch(frame.posIndicator)
			{
			case 0x51:
				MCFile memFile = new MCFile(frame.fileName);
				memFile.setNumBlocks(frame.fileSize/ PSXMemoryCard.BLOCKSIZE);
				//System.out.println("MemoryCard.parseDirectoryFrames || Set num blocks: " + (frame.fileSize/ MemoryCard.BLOCKSIZE));
				//Set 0
				blockDir[f] = frame.fileName;
				memFile.setBlockIndex(0, f);
				int nextBlock = (int)frame.nextBlock;
				if (memFile.getNumBlocks() > 1)
				{
					for (int i = 1; i < memFile.getNumBlocks(); i++)
					{
						DirFrame nextFrame = parseOneDirFrame(cardfile, nextBlock);
						blockDir[nextBlock] = frame.fileName;
						memFile.setBlockIndex(i, nextBlock);
						nextBlock = (int)nextFrame.nextBlock;
					}	
				}
				directory.put(frame.fileName, memFile);
				break;
			case 0xA1:
				String delName = getDeletedFileName(frame.fileName);
				MCFile delFile = new MCFile(frame.fileName); //Internal name is original name
				delFile.setNumBlocks(frame.fileSize/ PSXMemoryCard.BLOCKSIZE);
				//Set 0
				blockDir[f] = delName;
				delFile.setBlockIndex(0, f);
				int nxtBlock = (int)frame.nextBlock;
				if (delFile.getNumBlocks() > 1)
				{
					for (int i = 1; i < delFile.getNumBlocks(); i++)
					{
						DirFrame nextFrame = parseOneDirFrame(cardfile, nxtBlock);
						blockDir[nxtBlock] = delName;
						delFile.setBlockIndex(i, nxtBlock);
						nxtBlock = (int)nextFrame.nextBlock;
					}	
				}
				directory.put(delName, delFile);
				break;
			case 0xA0:
				blockDir[f] = null;
				break;
			default:
				//Do nothing
				break;
			}
			
		}
	}

	/**
	 * Find and parse the actual contents of a file in the memory card Block 0 directory.
	 * <br>This function simply looks up the String in the directory. Therefore, if the 
	 * starred deleted file name is given, the deleted file can be found.
	 * @param cardfile FileBuffer containing the memory card.
	 * @param fileName Name of the file to parse.
	 * @throws PSXMemoryCardReadErrorException If the file could not be found, is in a corrupted or unreadable block, or
	 * itself could not be read.
	 */
	private void parseMemFile(FileBuffer cardfile, String fileName) throws PSXMemoryCardReadErrorException
	{
		//The buffer created from the MC data is a COPY (so that it can be written to).
		//System.out.println("MemoryCard.parseMemFile || Called : fileName = " + fileName);
		if (!this.directory.containsKey(fileName)) return;
		//System.out.println("MemoryCard.parseMemFile || Passed initial check");
		MCFile memFile = this.directory.get(fileName);
		//System.out.println("MemoryCard.parseMemFile || retrieved MCFile is null: " + (memFile == null));
		FileBuffer memData = new FileBuffer(memFile.getNumBlocks() * BLOCKSIZE);
		//System.out.println("MemoryCard.parseMemFile || memdata buffer created");
		for (int b = 0; b < memFile.getNumBlocks(); b++)
		{
			//System.out.println("MemoryCard.parseMemFile || Parsing file block: " + b);
			int i = memFile.getBlockIndex(b);
			//System.out.println("MemoryCard.parseMemFile || Card block: " + i);
			int ppos = i * BLOCKSIZE;
			//System.out.println("MemoryCard.parseMemFile || Card file offset: 0x" + Integer.toHexString(ppos));
			if (b == 0)
			{
				//System.out.println("MemoryCard.parseMemFile || First file block!");
				if (!this.checkBlockID(cardfile, i)) throw new PSXMemoryCardReadErrorException();
				//System.out.println("MemoryCard.parseMemFile || Passed SC magic check");
				byte iconFlag = cardfile.getByte(ppos + 2);
				//System.out.println("MemoryCard.parseMemFile || iconFlag = 0x" + String.format("%02x", iconFlag));
				int iFrames = Byte.toUnsignedInt(iconFlag) - 0x10;
				memFile.setIconFrames(iFrames);
				//System.out.println("MemoryCard.parseMemFile || Number of icon frames: " + iFrames);
				memFile.setGameName(cardfile.readEncoded_string(PSXMemoryCard.SHIFT_JIS_CHARSET_NAME, ppos + 4, ppos + 0x44));
				//System.out.println("MemoryCard.parseMemFile || Game name read: " + memFile.getGameName());
				memFile.setPalette(PSXImages.parse16ColorPalette(cardfile, ppos + 0x60, false));
				//System.out.println("MemoryCard.parseMemFile || Palette read... ");
				//memFile.getIconPalette().printMe();
				for (int f = 0; f < iFrames; f++)
				{
					//System.out.println("MemoryCard.parseMemFile || Reading icon frame " + f);
					int ifst = ppos + ((f+1) * FRAMESIZE);
					//System.out.println("MemoryCard.parseMemFile || Image offset = 0x" + Integer.toHexString(ifst));
					PaletteRaster p = PSXImages.parseBitMap4(cardfile, memFile.getIconPalette(), ifst, 16, 16);
					//System.out.println("MemoryCard.parseMemFile || Frame in text...");
					//p.printMe();
					memFile.setIconFrame(p, f);
				}
			}
			//System.out.println("MemoryCard.parseMemFile || Copying file block...");
			for (int j = 0; j < BLOCKSIZE; j++)
			{
				memData.addToFile(cardfile.getByte(ppos + j));
			}
			//System.out.println("MemoryCard.parseMemFile || File block copied...");
		}
		memFile.setData(memData);
	}

	/**
	 * Parse all of the blocks after 0 by looking at the set of file names and one-by-one
	 * finding and parsing each of those on the card.
	 * @param cardfile The raw memory card data file to parse.
	 * @throws PSXMemoryCardReadErrorException If there is an error parsing or a corrupt block.
	 */
	private void parseBlocks(FileBuffer cardfile) throws PSXMemoryCardReadErrorException
	{
		Set<String> keys = this.directory.keySet();
		for (String s : keys)
		{
			this.parseMemFile(cardfile, s);
		}
	}
	
	/**
	 * Parse a raw memory card file by first reading the Block 0 information (such as the
	 * directory) and proceeding to read the files from the later blocks by using the directory
	 * information.
	 * @param cardfile Raw memory card file to parse.
	 * @throws PSXMemoryCardReadErrorException If there is an error parsing caused by improper formatting,
	 * the memory card file not being a memory card file, or data corruption.
	 */
	private void parseMemoryCard(FileBuffer cardfile) throws PSXMemoryCardReadErrorException
	{
		if (!this.checkMCID(cardfile)) throw new PSXMemoryCardReadErrorException();
		//System.out.println("MemoryCard.parseMemoryCard || Initial check passed.");
		this.parseDirectoryFrames(cardfile);
		//System.out.println("MemoryCard.parseMemoryCard || Directory parse passed.");
		this.parseBlocks(cardfile);
		//System.out.println("MemoryCard.parseMemoryCard || Blocks parse passed.");
		for (int i = 0; i < BLOCKSIZE; i++){
			//System.out.println("MemoryCard.parseMemoryCard || before -- i = " + i);
			this.blockZero.addToFile(cardfile.getByte(i));
			//System.out.println("MemoryCard.parseMemoryCard || after -- i = " + i);
		}
		//System.out.println("MemoryCard.parseMemoryCard || Block 0 parse passed.");
	}
	
	/* --- Getters --- */
	
	/**
	 * Get a List of all the names (system names) of all of the files
	 * on this card. This does not include deleted files or Block 0 artifacts.
	 * @return A List of the system names of all standard files on this memory card. 
	 * <br> An empty list will be returned if there are no files.
	 */
	public List<String> getAllFilenames()
	{
		Set<String> allKeys = directory.keySet();
		List<String> files = new ArrayList<String>(allKeys.size());
		for (String k : allKeys)
		{
			//System.out.println("MemoryCard.getAllFilenames || k = " + k);
			if (k.charAt(0) == '*') continue;
			if (k.equals("HEADER")) continue;
			files.add(k);
		}
		Collections.sort(files);
		return files;
	}
	
	/**
	 * Get a List of all of the names (system names) of all of the files
	 * on this card that have been deleted, but have not had their data cleared
	 * or overwritten.
	 * @return A List of the system names of all remaining deleted files on this
	 * memory card.
	 * <br> An empty list will be returned if there are no deleted files.
	 */
	public List<String> getAllDeletedFilenames()
	{
		Set<String> allKeys = directory.keySet();
		List<String> files = new ArrayList<String>(allKeys.size());
		for (String k : allKeys)
		{
			if (k.charAt(0) != '*') continue;
			k = k.substring(1);
			files.add(k);
		}
		Collections.sort(files);
		return files;
	}
	
	/* --- Serialization --- */
	
	/**
	 * Retrieve a raw frame of Block 0.
	 * <br>The returned buffer is a read-only reference to what is currently
	 * stored in BlockZero.
	 * @param frameNo Index of the frame to get (valid values: 0 - 63)
	 * @return FileBuffer containing the requested frame.
	 */	
	private FileBuffer getB0Frame(int frameNo)
	{
		/*FileBuffer f = new FileBuffer(FRAMESIZE, false);
		for (int i = 0; i < FRAMESIZE; i++)
		{
			f.addToFile(this.blockZero.getByte((frameNo * FRAMESIZE) + i));
		}*/
		int fStart = frameNo * FRAMESIZE;
		FileBuffer f = blockZero.createReadOnlyCopy(fStart, fStart + FRAMESIZE);
		return f;
	}
	
	/**
	 * Serialize Frame 0 of Block 0 (the Memory Card header).
	 * Includes object update and checksum.
	 * @return A FileBuffer containing the serialized version of Frame 0.
	 */
	private FileBuffer serializeBlock0_Header()
	{
		FileBuffer f = new FileBuffer(FRAMESIZE, false);
		int checksum = 0;
		f.printASCIIToFile(MEMID);
		while (f.getFileSize() < (FRAMESIZE - 1)) f.addToFile(ZERO);
		for (int i = 0; i < f.getFileSize(); i++)
		{
			checksum = checksum ^ Byte.toUnsignedInt(f.getByte(i));
		}
		f.addToFile((byte)checksum);
		return f;
	}
	
	/**
	 * Serialize the directory entry for the information in a given block.
	 * @param bNo The block number of the desired entry, which correlates to the frame
	 * number in block 0 of the entry. (Valid Values: 1 - 15)
	 * @return A FileBuffer containing the Frame with the directory entry.
	 */
	private FileBuffer serializeDirEntry(int bNo)
	{
		//These create new buffers
		FileBuffer f = null;
		//int checksum = 0;
		String fName = this.blockDir[bNo];
		if (fName == null)
		{
			f = new FileBuffer(FRAMESIZE, false);
			//Empty block
			f.addToFile(0xA0L);
			f.addToFile(0xFFFFL);
			while (f.getFileSize() < FRAMESIZE - 1) f.addToFile(ZERO);
			f.addToFile((byte)0xA0);
		}
		else
		{
			//Has data
			MCFile memFile = directory.get(fName);
			if (fName.charAt(0) == '*') f = memFile.generateDirEntry(bNo, true);
			else f = memFile.generateDirEntry(bNo, false);
		}
		return f;
	}
	
	/**
	 * Serialize the Block 0 directory.
	 * @return A serialized version of the block 0 directory (15 frames)
	 */
	private FileBuffer serializeBlock0_Directory()
	{
		//FileBuffer f = new FileBuffer(FRAMESIZE * 15, false);
		//CompositeBuffer f = new CompositeBuffer(15);
		MultiFileBuffer f = new MultiFileBuffer(15);
		for (int b = 1; b < 16; b++)
		{
			f.addToFile(this.serializeDirEntry(b));
		}
		return f;
	}
	
	/**
	 * Serialize a single empty frame for the Broken Sector region of Block 0.
	 * <br>Broken Sector frames are not zero filled when empty.
	 * @return The data for an empty Broken Sector frame.
	 */
	private FileBuffer generateEmptyBSframe()
	{
		int checksum = 0;
		FileBuffer f = new FileBuffer(FRAMESIZE, false);
		f.addToFile(0xFFFFFFFFL);
		f.addToFile((short)0xFFFF);
		while (f.getFileSize() < (FRAMESIZE) - 1) f.addToFile(ZERO);
		for (int i = 0; i < f.getFileSize(); i++)
		{
			int b = Byte.toUnsignedInt(f.getByte(i));
			checksum = checksum ^ b;
		}
		f.addToFile((byte)checksum);
		return f;
	}
	
	/**
	 * Serialize the Broken Sector region of Block 0 (Frames 16 - 35).
	 * <br> Although these frames are used to mark broken sectors on a standard
	 * hardware card, cards written by this class will generally ignore these frames
	 * and serialize them empty.
	 * @return The Broken Sector section of Block 0 (20 frames).
	 */
	private FileBuffer serializeBlock0_BrokenSectors()
	{
		FileBuffer f = new FileBuffer(FRAMESIZE * (35 - 16 + 1), false);
		for (int i = 16; i < 36; i++) f.addToFile(this.generateEmptyBSframe());
		return f;
	}

	/**
	 * Generate a zero-filled (completely empty) frame. Can be used for
	 * empty file Block data frames.
	 * @return Zero-filled frame.
	 */
	private FileBuffer generateEmptyFrame()
	{
		FileBuffer f = new FileBuffer(FRAMESIZE, false);
		while (f.getFileSize() < FRAMESIZE) f.addToFile(ZERO);
		return f;
	}
	
	/**
	 * Generate a 0xFF-filled (completely empty) frame. For use in certain sectors, such 
	 * as unused Block 0 frames.
	 * @return 0xFF filled frame.
	 */
	private FileBuffer generateFillerFrame()
	{
		FileBuffer f = new FileBuffer(FRAMESIZE, false);
		while (f.getFileSize() < FRAMESIZE) f.addToFile(FILLER);
		return f;
	}
	
	/**
	 * Generate the 20 frame Broken Sector Replacement Data chunk of Block 0 (Frames 36 - 55).
	 * <br>Because this class does not handle sector corruption at the moment, the resulting
	 * data should be 0xFF filled.
	 * @return Empty BSRD section (20 frames).
	 */
	private FileBuffer serializeBlock0_BrokenReplace()
	{
		FileBuffer f = new FileBuffer(FRAMESIZE * (55 - 36 + 1), false);
		for (int i = 36; i < 56; i++) f.addToFile(this.generateFillerFrame());
		return f;
	}
	
	/**
	 * Serialize Block 0 of this memory card.
	 * @param copy Whether to simply copy what is in the Block 0 copy this object keeps
	 * or to generate a fresh Block 0.
	 * @return Block 0 of this memory card.
	 */
	private FileBuffer serializeBlock0(boolean copy)
	{
		//FileBuffer b0 = new FileBuffer(BLOCKSIZE, false);
		//FileBuffer b0 = new CompositeBuffer();
		if (copy)
		{
			//CompositeBuffer b0 = new CompositeBuffer(64);
			MultiFileBuffer b0 = new MultiFileBuffer(64);
			b0.addToFile(this.getB0Frame(0));
			b0.addToFile(this.serializeBlock0_Directory());
			for (int i = 16; i < 64; i++)
			{
				b0.addToFile(this.getB0Frame(i));
			}
			return b0;
		}
		else
		{
			//CompositeBuffer b0 = new CompositeBuffer(5);
			MultiFileBuffer b0 = new MultiFileBuffer(5);
			b0.addToFile(this.serializeBlock0_Header());
			b0.addToFile(this.serializeBlock0_Directory());
			b0.addToFile(this.serializeBlock0_BrokenSectors());
			b0.addToFile(this.serializeBlock0_BrokenReplace());
			b0.addToFile(this.serializeBlock0_Header());	
			return b0;
		}
	}
	
	/**
	 * Serialize a specific block of this memory card.
	 * @param blockIndex Index of block to serialize (0 - 15).
	 * @return FileBuffer containing the serialized Block data.
	 * @throws IllegalArgumentException If blockIndex is invalid.
	 */
	private FileBuffer serializeBlock(int blockIndex)
	{
		if (blockIndex == 0) return serializeBlock0(true);
		if (blockIndex > 15 || blockIndex < 0) throw new IllegalArgumentException();
		if (this.blockContainsData(blockIndex))
		{
			String fName = this.blockDir[blockIndex];
			MCFile memFile = this.directory.get(fName);
			if (memFile != null)
			{
				FileBuffer bl = memFile.serializeBlock(blockIndex);
				if (bl != null) return bl;
			}
		}
		FileBuffer eBl = new FileBuffer(BLOCKSIZE, false);
		for (int i = 0; i < BLOCKSIZE; i++) eBl.addToFile(ZERO);
		return eBl;
	}
	
	/**
	 * Serialize this memory card for writing onto disk.
	 * @param copy Whether to use the stored Block 0 data for broken sector data (true)
	 * or regenerate broken sector data (false).
	 * @return A write-ready serialized version of this memory card's data.
	 */
	public FileBuffer serializeMemoryCard(boolean copy)
	{
		//FileBuffer mc = new FileBuffer(BLOCKSIZE * CARD_BLOCKS, false);
		//CompositeBuffer mc = new CompositeBuffer(16);
		MultiFileBuffer mc = new MultiFileBuffer(16);
		mc.addToFile(this.serializeBlock0(copy));
		for (int i = 1; i < 16; i++)
		{
			mc.addToFile(this.serializeBlock(i));
		}
		return mc;
	}
	
	/**
	 * Write this memory card (raw, serialized) to a file on disk.
	 * @param path Path of file to write to.
	 * @param copy Whether to copy stored late Block 0 data (true) or regenerate
	 * late Block 0 data from scratch (false). The former choice is faster.
	 * @throws IOException If there is an error writing to disk.
	 */
	public void writeMemoryCard(String path, boolean copy) throws IOException
	{
		FileBuffer MCout = this.serializeMemoryCard(copy);
		MCout.writeFile(path);
	}
	
	/* --- de Novo Game File Creation --- */
	
	/**
	 * Generate a (serialized) empty memory card. This can be modified and/or
	 * written to disk once generated.
	 * @return The unparsed, serialized copy of a fresh card. You will need
	 * to construct a new MemoryCard object from this buffer in order to parse
	 * and easily modify its contents.
	 */
	public static FileBuffer createFreshCard()
	{
		//Don't make this one composite. Should be editable without problems.
		FileBuffer emptyMC = new FileBuffer(CARD_BLOCKS * BLOCKSIZE, false);
		//Block 0, Frame 0
		emptyMC.printASCIIToFile(MEMID);
		while (emptyMC.getFileSize() < FRAMESIZE) emptyMC.addToFile((byte)0x00);
		//Block 0, Frame 1-15
		for (int i = 1; i < 16; i++)
		{
			emptyMC.addToFile(0x00000000000000A0L);
			emptyMC.addToFile((short)0xFFFF);
			while (emptyMC.getFileSize() < (FRAMESIZE * (i + 1)) - 1) emptyMC.addToFile((byte)0x00);
			emptyMC.addToFile((byte)0xa0);
		}
		//Block 0, Frame 16-35
		for (int i = 16; i < 32; i++)
		{
			emptyMC.addToFile(0x00000000FFFFFFFFL);
			emptyMC.addToFile((short)0xFFFF);
			while (emptyMC.getFileSize() < (FRAMESIZE * (i + 1))) emptyMC.addToFile((byte)0x00);
		}
		//Block 0, Frame 36-55
		//Block 0, Frame 56-62
		while (emptyMC.getFileSize() < FRAMESIZE * 63) emptyMC.addToFile((byte)0x00);
		//Block 0, Frame 63
		emptyMC.printASCIIToFile(MEMID);
		//Everything else (all 0)
		while (emptyMC.getFileSize() < CARD_BLOCKS * BLOCKSIZE) emptyMC.addToFile((byte)0x00);
		return emptyMC;	
	}

	/**
	 * Create a new file on this card knowing the name of the desired file and the number of blocks needed.
	 * <br>The smallest a memory card file can be is one block.
	 * @param fileName The name of the file to create.
	 * @param numberBlocks The number of blocks to reserve and use for the file.
	 * @throws PSXMemoryCardInsufficientSpaceException If there isn't enough space on the memory card
	 * for the file to be created, even if deleted data is cleared.
	 */
	public void createNewMCFile(String fileName, int numberBlocks) throws PSXMemoryCardInsufficientSpaceException
	{
		if (fileName == null || numberBlocks <= 0) return;
		int avail = this.freeBlocks();
		if (numberBlocks > avail){
			int del = this.cleanBlocks(numberBlocks - avail);
			if (del < 0) throw new PSXMemoryCardInsufficientSpaceException();
			if (this.freeBlocks() < numberBlocks) throw new PSXMemoryCardInsufficientSpaceException();
		}
		if (fileName.length() > 19) fileName = fileName.substring(0, 20);
		MCFile memFile = new MCFile(fileName);
		memFile.setNumBlocks(numberBlocks);
		for (int b = 0; b < numberBlocks; b++)
		{
			int f = this.firstFreeBlock();
			memFile.setBlockIndex(b, f);
			this.blockDir[f] = fileName;
		}
		FileBuffer data = new FileBuffer(numberBlocks * BLOCKSIZE, false);
		data.printASCIIToFile(BLOCKID);
		memFile.setData(data);
	}
	
	/**
	 * Add a game name (file name visible to PlayStation user) to a file on the memory card.
	 * Cannot modify deleted files.
	 * @param fileName Name of file to modify.
	 * @param gameName Name to set file to. Can be unicode in memory, will be encoded using SHIFT-JIS for serialization. 
	 * @throws IllegalArgumentException If file name string is empty, null, or file could not be found.
	 */
	public void addGameNameMCF(String fileName, String gameName)
	{
		if (fileName == null) throw new IllegalArgumentException();
		if (fileName.charAt(0) == '*') throw new IllegalArgumentException();
		MCFile memFile = this.directory.get(fileName);
		if (memFile == null) throw new IllegalArgumentException();
		memFile.setGameName(gameName);
		memFile.refreshDatHeaderSerialization();
	}
	
	/**
	 * Add an icon (a 1-3 frame Animation) to a file on the memory card.
	 * File must exist and not be deleted.
	 * @param fileName Name of file to modify.
	 * @param icon Icon to add to file or that will replace existing icon.
	 * @throws IllegalArgumentException If file name string is empty, null, or file could not be found.
	 */
	@Deprecated
	public void addIconMCF(String fileName, Animation icon)
	{
		if (fileName == null) throw new IllegalArgumentException();
		if (fileName.charAt(0) == '*') throw new IllegalArgumentException();
		MCFile memFile = this.directory.get(fileName);
		if (memFile == null) throw new IllegalArgumentException();
		memFile.setIconFrames(icon.getNumberFrames());
		for (int i = 0; i < icon.getNumberFrames(); i++)
		{
			//memFile.setIconFrame(icon.getFramePicture(i), i);
		}
		memFile.refreshDatHeaderSerialization();
	}

	/**
	 * Add a 4-bit color palette to a memory card file that will be used to draw the 
	 * icon representing the file.
	 * @param fileName Name of file to modify.
	 * @param plt Palette to add to file or that will replace existing palette.
	 * @throws IllegalArgumentException If file name string is empty, null, or file could not be found.
	 */
	public void addIconPaletteMCF(String fileName, FourBitPalette plt)
	{
		if (fileName == null) throw new IllegalArgumentException();
		if (fileName.charAt(0) == '*') throw new IllegalArgumentException();
		MCFile memFile = this.directory.get(fileName);
		if (memFile == null) throw new IllegalArgumentException();
		memFile.setPalette(plt);
		memFile.refreshDatHeaderSerialization();
	}
	
	/* --- Space and Deletion Management --- */
	
	/**
	 * Get the string this class would use to denote a file by the provided name as deleted,
	 * were it deleted.
	 * @param fileName System name of hypothetical file.
	 * @return System name of deleted hypothetical file.
	 */
	private static String getDeletedFileName(String fileName)
	{
		return "*" + fileName;
	}
	
	/**
	 * Delete a file on the memory card. Note that this will not automatically clear
	 * its data - it will only mark it as deleted on the card, a way of opening the sectors
	 * this file occupies if no free sectors remain. As long as its data is not overwritten,
	 * it can in theory be retrieved again ("undeleted") later.
	 * @param fileName System name of file to delete.
	 * @return True - If operation succeeded and file is now deleted.
	 * <br>False - If file does not exist, is already deleted, or otherwise could not be deleted.
	 */
	public boolean deleteFile(String fileName)
	{
		/*Deletion is marked by putting an asterisk in front of the file name.
		 * When the card is re-serialized, the directory entry will be updated, but
		 * as long as the data isn't cleared, the file will be re-serialized as usual.
		 * The deleted file will be found in both the block directory and file directory in
		 * this object under its system file name with an asterisk preceding it.
		 */
		if (fileName == null) return false;
		MCFile memFile = this.directory.get(fileName);
		if (memFile == null) return false;
		String delName = getDeletedFileName(fileName);
		this.directory.remove(fileName);
		for (int i = 0; i < memFile.getNumBlocks(); i++)
		{
			int b = memFile.getBlockIndex(i);
			/*if (i == 0) this.blockDir[b] = "*DEL1";
			else if (i > 0 && i < memFile.getNumBlocks() - 1) this.blockDir[b] = "*DEL2";
			else if (i > 0 && i == memFile.getNumBlocks() - 1) this.blockDir[b] = "*DEL3";*/
			blockDir[b] = delName;
		}
		directory.put(delName, memFile);
		return true;
	}
	
	/**
	 * Restore a deleted file, if the file's data have not yet been cleared or overwritten.
	 * @param fileName Original system name of file to restore.
	 * @return True - If restoration was successful.
	 * <br>False - If file could not be restored.
	 */
	public boolean undeleteFile(String fileName)
	{
		String delName = getDeletedFileName(fileName);
		MCFile memFile = directory.get(delName);
		if (memFile == null) return false;
		directory.remove(delName);
		int fBlocks = memFile.getNumBlocks();
		for (int i = 0; i < fBlocks; i++)
		{
			int b = memFile.getBlockIndex(i);
			blockDir[b] = fileName;
		}
		directory.put(fileName, memFile);
		return true;
	}
	
	/**
	 * Permanently clear the data for the file with the provided name.
	 * @param fileName Original system name of file to permanently erase.
	 * @return Number of blocks cleaned, if operation was successful.
	 * <br>-1 - If error occurred (such as file not existing or not having been deleted.)
	 */
	private int clearFile(String fileName)
	{
		if (!isFileDeleted(fileName)) return -1;
		String delName = getDeletedFileName(fileName);
		MCFile memFile = directory.remove(delName);
		for (int i = 0; i < memFile.getNumBlocks(); i++)
		{
			int b = memFile.getBlockIndex(i);
			blockDir[b] = null;
		}
		return memFile.getNumBlocks();
	}
	
	/**
	 * Get whether a file exists on the card, but has been flagged as deleted. A deleted file
	 * is free to have its data overwritten unless it is restored.
	 * @param fileName Original system file name of file.
	 * @return True - If file was flagged as deleted and has not been cleared or overwritten.
	 * <br>False - If file never existed, file currently exists intact, or file has already been
	 * cleared for space.
	 */
	public boolean isFileDeleted(String fileName)
	{
		String delName = getDeletedFileName(fileName);
		return directory.containsKey(delName);
	}
	
	/**
	 * Get whether a memory card block contains data that has been flagged for deletion.
	 * @param mcBlock Index of block on memory card (1-15).
	 * @return True - If block contains data for a deleted file.
	 * <br>False - If block is empty or contains data in use.
	 */
	public boolean blockHasDeletedData(int mcBlock)
	{
		String bFile = blockDir[mcBlock];
		if (bFile == null) return false;
		if (bFile.isEmpty()) return false;
		char firstChar = bFile.charAt(0);
		if (firstChar != '*') return false;
		return true;
	}
	
	/**
	 * Completely erase data from sectors marked as deleted.
	 * <br>Data will no longer be recoverable as they have been erased and replaced
	 * with zero-filled sectors.
	 * @return Number of blocks cleaned.
	 */
	public int cleanAllBlocks()
	{
		int cleaned = 0;
		for (int i = 1; i < 16; i++)
		{
			if (blockHasDeletedData(i))
			{
				String name = blockDir[i].substring(1);
				cleaned += clearFile(name);
			}
		}
		return cleaned;
	}
	
	/**
	 * Search for blocks containing deleted data that can be cleaned and free.
	 * <br>This method will try to minimize the amount of data cleared while still clearing
	 * what was requested.
	 * <br>This method does not account for completely free blocks - it just deals with clearing
	 * deleted data. It is possible that even if this function returns -1 or 0, that there is enough
	 * space in already completely free blocks.
	 * @param neededBlocks Number of blocks that need to be cleared. This value must be between 
	 * 1 - 15 inclusive. 
	 * @return Number of blocks cleaned - If the requested number of blocks were successfully cleared.
	 * <br>-1 - If not enough space could possibly be cleared, or if the argument is invalid.
	 */
	public int cleanBlocks(int neededBlocks)
	{
		if (neededBlocks < 1 || neededBlocks > 15) return -1;
		if (deletedBlocks() < neededBlocks) return -1;
		if (neededBlocks == 15) return cleanAllBlocks();
		//Retrieve list of deleted files
		List<String> delNames = getAllDeletedFilenames();
		List<MCFile> delFiles = new ArrayList<MCFile>(delNames.size());
		for (String s : delNames) delFiles.add(directory.get(getDeletedFileName(s)));
		int nFiles = delFiles.size();
		
		//	If only file, delete it.
		if (delFiles.size() < 2) 
		{
			int cleaned = clearFile(delFiles.get(0).getFileName());
			return cleaned;
		}
		//	Look for deleted files of that precise size
		for (MCFile f : delFiles)
		{
			if (f.getNumBlocks() == neededBlocks)
			{
				return clearFile(f.getFileName());
			}
		}
		//Look for optimal combination.
		int[] sizes = new int[nFiles];
		for (int i = 0; i < nFiles; i++) sizes[i] = delFiles.get(i).getNumBlocks();
		Optcombinator combinator = new Optcombinator(sizes, Optcombinator.MODE_SUM);
		int[] bestCombo = combinator.getClosestOrOver(neededBlocks, true);
		if (bestCombo == null) return -1;
		if (bestCombo.length < 1) return -1;
		int tot = 0;
		for (int i : bestCombo)
		{
			int cleared = clearFile(delNames.get(i));
			if (cleared < 0) return tot;
			tot += cleared;
		}
		
		return -1;
	}
	
	/**
	 * Count the number of completely free blocks on the card.
	 * <br>This count DOES NOT included blocks with deleted data on, although
	 * deleted blocks may be overwritten if there are not enough completely
	 * free blocks for an operation.
	 * @return The number of completely free (that is, not counting deleted
	 * data) blocks on the card.
	 */
	public int freeBlocks()
	{
		int tot = 0;
		for (int i = 1; i < 16; i++)
		{
			if (this.blockDir[i] == null) tot++;
		}
		return tot;
	}

	/**
	 * Count the number of blocks containing deleted data.
	 * @return The number of blocks on this card containing data for files
	 * flagged for deletion.
	 */
	public int deletedBlocks()
	{
		int tot = 0;
		for (int i = 1; i < 16; i++)
		{
			if (blockHasDeletedData(i)) tot++;
		}
		return tot;
	}
	
	/**
	 * Get the index of the first completely free block on the card. 
	 * This does not include blocks with deleted data.
	 * @return The index (1-15) of the first completely free block, if there is one.
	 * <br>-1 if there are no completely free blocks.
	 */
	public int firstFreeBlock()
	{
		for (int i = 1; i < 16; i++)
		{
			if (this.blockDir[i] == null) return i;
		}
		return -1;
	}
	
	/* --- Browsing View --- */
	
	/**
	 * Get the User Visible name ("game name") of the file with the provided
	 * system name.
	 * <br>This function DOES NOT work with deleted files that have not been cleared.
	 * @param fileName System name of desired file.
	 * @return JVM Unicode user visible name of the file.
	 */
	public String getGameName(String fileName)
	{
		if (fileName == null) return null;
		if (fileName.isEmpty()) return null;
		if (fileName.charAt(0) == '*') return null;
		MCFile memFile = this.directory.get(fileName);
		if (memFile == null) return null;
		return memFile.getGameName();
	}

	/**
	 * Get the animated icon representing the file with the provided system name.
	 * <br>This function DOES NOT work with deleted files that have not been cleared.
	 * @param fileName System name of desired file.
	 * @return Animation containing information on the animated frame. This object
	 * or its component frames can be converted to a more standard Java object if needed
	 * or rendered pixel by pixel.
	 */
	public Animation getGameIcon(String fileName)
	{
		if (fileName == null) return null;
		if (fileName.isEmpty()) return null;
		if (fileName.charAt(0) == '*') return null;
		MCFile memFile = this.directory.get(fileName);
		if (memFile == null) return null;
		//Animation icon = new ImageAnimation(memFile.getNumIconFrames());
		Animation icon = new SimpleAnimation(memFile.getNumIconFrames());
		for (int i = 0; i < memFile.getNumIconFrames(); i++){
			//icon.setFrame(memFile.getIconFrame(i), i);
			icon.setFrame(memFile.getIconFrame(i).toImage(), i);
		}
		return icon;
	}
	
	/**
	 * Get the number of blocks the specified file takes up on the card.
	 * <br>This function DOES NOT work with deleted files that have not been cleared.
	 * @param fileName System name of desired file.
	 * @return Number of blocks used by the file.
	 */
	public int getFileBlocks(String fileName)
	{
		if (fileName == null) return -1;
		if (fileName.isEmpty()) return -1;
		if (fileName.charAt(0) == '*') return -1;
		MCFile memFile = this.directory.get(fileName);
		if (memFile == null) return 0;
		return memFile.getNumBlocks();
	}
	
	/**
	 * Get the User Visible name ("game name") of the deleted file with the provided
	 * system name.
	 * @param fileName Original system name of desired file.
	 * @return JVM Unicode user visible name of the file.
	 */
	public String getDeletedGameName(String fileName)
	{
		if (fileName == null) return null;
		if (fileName.isEmpty()) return null;
		if (fileName.charAt(0) == '*') return null;
		MCFile memFile = this.directory.get("*" + fileName);
		if (memFile == null) return null;
		return memFile.getGameName();
	}

	/**
	 * Get the animated icon representing the deleted file with the provided system name.
	 * @param fileName Original system name of desired file.
	 * @return Animation containing information on the animated frame. This object
	 * or its component frames can be converted to a more standard Java object if needed
	 * or rendered pixel by pixel.
	 */
	public Animation getDeletedGameIcon(String fileName)
	{
		if (fileName == null) return null;
		if (fileName.isEmpty()) return null;
		if (fileName.charAt(0) == '*') return null;
		MCFile memFile = this.directory.get("*" + fileName);
		if (memFile == null) return null;
		//Animation icon = new ImageAnimation(memFile.getNumIconFrames());
		Animation icon = new SimpleAnimation(memFile.getNumIconFrames());
		for (int i = 0; i < memFile.getNumIconFrames(); i++)
		{
			//icon.setFrame(memFile.getIconFrame(i), i);
			icon.setFrame(memFile.getIconFrame(i).toImage(), i);
		}
		return icon;
	}
	
	/**
	 * Get the number of blocks the specified deleted file takes up on the card.
	 * @param fileName Original system name of desired file.
	 * @return Number of blocks used by the file.
	 */
	public int getDeletedFileBlocks(String fileName)
	{
		if (fileName == null) return -1;
		if (fileName.isEmpty()) return -1;
		if (fileName.charAt(0) == '*') return -1;
		MCFile memFile = this.directory.get("*" + fileName);
		if (memFile == null) return 0;
		return memFile.getNumBlocks();
	}
	
	public String getRawFilenameAtBlock(int block)
	{
		if (block < 1) return null;
		if (block > 15) return null;
		return blockDir[block];
	}
	
	public int getBlockFileIndexAtBlock(int block)
	{
		String fName = getRawFilenameAtBlock(block);
		if (fName == null) return -1;
		MCFile memFile = this.directory.get(fName);
		if (memFile == null) return -1;
		return memFile.translateBlockIndex(block);
	}
	
	/* --- File Access --- */
	
	/**
	 * Get names of files on this memory card that might have been
	 * created by a game with a provided 9 character game code.
	 * <br>This looks for filenames that contain the game code (as they
	 * often do).
	 * @param gamecode 9 digit game code in the form AAAA00000 (no dash).
	 * @return Collection of filenames that match the provided gamecode.
	 * Returns an empty collection if no matches are found.
	 * @since 2.1.0
	 */
	public Collection<String> getPossibleFilenamesForGame(String gamecode){
		if(gamecode == null || gamecode.length() < 9) return new HashSet<String>();
		gamecode = gamecode.toUpperCase();
		gamecode = gamecode.substring(0,4) + "-" + gamecode.substring(4,7);
		Set<String> names = new HashSet<String>();
		//System.err.println("Gamecode: " + gamecode);
		for(String s : directory.keySet()){
			//System.err.println("File name: " + s);
			if(s.contains(gamecode)) names.add(s);
		}
		return names;
	}
	
	/**
	 * Get the raw data (contents) of a memory card file.
	 * <br>This method returns a read-only locked reference to the file. To modify its
	 * contents, proper channels must be used.
	 * <br>This method will not find deleted files.
	 * @param fileName System name of file to retrieve.
	 * @return FileBuffer containing the contents of a memory card file (composite).
	 * The buffer returned is a reference to the contents of the file. Modifying the contents
	 * of this buffer will modify the contents of the file.
	 */
	public FileBuffer getFile(String fileName)
	{
		MCFile memFile = this.directory.get(fileName);
		if (memFile == null) return null;
		return memFile.getContents();
	}
	
	/**
	 * Retrieve a single frame from the card at the specified block/frame coordinate.
	 * This method returns raw data regardless of what file the frame contains, if any.
	 * Therefore, the file name is not needed for this method.
	 * <br>The data this method returns are in the form of a read-only locked reference.
	 * The exception to this rule is directory entries, which are fresh copies.
	 * @param blockNumber Index of block (0-15) to retrieve frame data from.
	 * @param frameNumber Index of frame within block (0-63) to retrieve data from.
	 * @return 0x80 (128) byte file buffer containing the desired frame data.
	 * @throws IOException
	 */
	public FileBuffer readFrame(int blockNumber, int frameNumber) throws IOException
	{
		if (blockNumber < 0) return null;
		if (blockNumber >= 16) return null;
		if (frameNumber < 0) return null;
		if (frameNumber >= 64) return null;
		if (blockNumber == 0)
		{
			if (frameNumber == 0) return this.getB0Frame(frameNumber);
			if (frameNumber >= 1 && frameNumber < 16) return this.serializeDirEntry(frameNumber);
			return this.getB0Frame(frameNumber);
		}
		else if(!this.blockContainsData(blockNumber)) return this.generateEmptyFrame();
		
		String fname = this.blockDir[blockNumber];
		MCFile memFile = this.directory.get(fname);
		return memFile.serializeFrame(blockNumber, frameNumber);
	}
	
	/**
	 * A function for explicitly writing a frame to an existing file on the memory card.
	 * <br>It is worth NOTING that...
	 * <br>It will NOT write directory entry frames at the moment (as that would require messing
	 * with the file structure of the card, essentially making and deleting new files without
	 * going through the proper channels)
	 * <br>It will also NOT write to frames beyond block 0 that are not part of a file!
	 * <br>It will NOT write to frames containing parts of deleted files.
	 * @param frame FileBuffer containing the data to write
	 * @param frOff Offset in frame FileBuffer indicating the start of data to write.
	 * @param blockNumber MC relative block number to write frame to
	 * @param frameNumber Block relative frame number to replace
	 * @throws PSXMemoryCardWriteErrorException If an illegal operation is attempted.
	 */
	public void writeFrame(FileBuffer frame, int frOff, int blockNumber, int frameNumber) throws PSXMemoryCardWriteErrorException
	{
		//Deletion handled by rejecting at "blockContainsData"
		if (blockNumber < 0) throw new PSXMemoryCardWriteErrorException();
		if (blockNumber >= 16) throw new PSXMemoryCardWriteErrorException();
		if (frameNumber < 0) throw new PSXMemoryCardWriteErrorException();
		if (frameNumber >= 64) throw new PSXMemoryCardWriteErrorException();
		//boolean change = false;
		if (blockNumber == 0)
		{
			if (frameNumber >= 1 && frameNumber < 16)
			{
				//Directory frame
				//This will be dealt with eventually?
				//The directory is not in serialized form, so edits must be parsed
				//and the file structure adjusted likewise.
				throw new PSXMemoryCardWriteErrorException();
			}
			else
			{
				//Other block 0 frame
				int o = frameNumber * FRAMESIZE;
				for (int i = 0; i < FRAMESIZE; i++)
				{
					if (i + frOff < frame.getFileSize())
					{
						if (this.blockZero.getByte(o + i) != frame.getByte(i + frOff)) 
						{
							this.blockZero.replaceByte(frame.getByte(i + frOff), o + i);
						}
					}
					else 
					{
						this.blockZero.replaceByte(ZERO, o + i);
					}
				}
			}
		}
		else if(!this.blockContainsData(blockNumber)) throw new PSXMemoryCardWriteErrorException();
		else
		{
			//Writing to existing file
			String fname = this.blockDir[blockNumber];
			MCFile memFile = this.directory.get(fname);
			if (memFile == null) throw new PSXMemoryCardWriteErrorException();
			memFile.replaceFrame(frame, frOff, blockNumber, frameNumber);	
		}
	}
	
	/**
	 * Get a single byte from a specified file at the specified position.
	 * <br>This function does NOT retrieve data from deleted files.
	 * @param fileName System name of the file to retrieve byte from.
	 * @param position Position (in bytes) relative to the start of the file of desired byte.
	 * @return Byte in the given file at the given position, if there were no errors reading it.
	 * @throws IndexOutOfBoundsException If the position is invalid. (ie. Less than 0, higher than
	 * size of the file, etc.)
	 * @throws IllegalArgumentException If file could not be found.
	 */
	public byte getFileByte(String fileName, int position)
	{
		if (fileName == null) throw new IllegalArgumentException();
		if (fileName.isEmpty()) throw new IllegalArgumentException();
		if (fileName.charAt(0) == '*') throw new IllegalArgumentException();
		if (position < 0) throw new IndexOutOfBoundsException();
		MCFile memFile = this.directory.get(fileName);
		if (memFile == null) throw new IllegalArgumentException();
		if (position >= memFile.getNumBlocks() * BLOCKSIZE) throw new IndexOutOfBoundsException();
		return memFile.getContents().getByte(position);
	}
	
	/**
	 * Get multiple consecutive bytes from a file on this card wrapped in a read-only buffer.
	 * <br>This function does NOT retrieve data from deleted files.
	 * @param fileName System name of file to retrieve bytes from.
	 * @param stPos Position (inclusive), relative to file start, of first byte to retrieve.
	 * @param len Number of bytes to retrieve.
	 * @return Read-only locked FileBuffer with requested data, if possible.
	 * @throws IllegalArgumentException If a file with the provided name does not exist, or was deleted.
	 */
	public FileBuffer getFileBytes(String fileName, int stPos, int len)
	{
		if (fileName == null) throw new IllegalArgumentException();
		if (fileName.isEmpty()) throw new IllegalArgumentException();
		if (fileName.charAt(0) == '*') throw new IllegalArgumentException();
		MCFile memFile = this.directory.get(fileName);
		if (memFile == null) throw new IllegalArgumentException();
		int edPos = stPos + len;
		FileBuffer myBytes = memFile.getContents().createReadOnlyCopy(stPos, edPos);
		return myBytes;
	}
	
	/**
	 * Write a certain number of provided bytes to a file on the memory card.
	 * @param fileName System name of file on memory card to write to.
	 * @param pos Position, relative to file start, within memory card file to start
	 * writing bytes. Bytes will not be added - merely replaced.
	 * @param source Wrapped bytes that will be written to file.
	 * @param readOff Position, relative to start of source buffer, to begin copying bytes from.
	 * @param len Number of bytes to copy.
	 * @return Number of bytes that were successfully written.
	 * <br>-1 if there was an error.
	 */
	public int writeFileBytes(String fileName, int pos, FileBuffer source, int readOff, int len)
	{
		if (fileName == null) throw new IllegalArgumentException();
		if (fileName.isEmpty()) throw new IllegalArgumentException();
		if (fileName.charAt(0) == '*') throw new IllegalArgumentException();
		//Reconcile addition and replacement of bytes in target file? Is size fixed?
		MCFile memFile = this.directory.get(fileName);
		if (memFile == null) return -1;
		return memFile.writeBytes(source, readOff, len, pos);
	}
	
	/**
	 * Get whether this memory card has a file by the provided system name.
	 * @param fileName System name of file in question.
	 * @return True - If the file exists on card and hasn't been deleted.
	 * <br>False - If the file could not be found or has been deleted.
	 */
	public boolean fileExists(String fileName)
	{
		if (fileName == null) return false;
		if (fileName.isEmpty()) return false;
		if (fileName.charAt(0) == '*') return false;
		if (this.directory.get(fileName) != null) return true;
		else return false;
	}
	
	/**
	 * Get the size of a file on the card.
	 * <br>Deleted files will be considered non-existent.
	 * <br>Size will always be a multiple of the block size (0x2000)
	 * @param fileName System name of file to obtain size of.
	 * @return Size of the file, if found.
	 * <br>-1 if file was not found, or has been deleted.
	 */
	public int getFileSize(String fileName)
	{
		if (fileName == null) return -1;
		if (fileName.isEmpty()) return -1;
		if (fileName.charAt(0) == '*') return -1;
		MCFile memFile = this.directory.get(fileName);
		if (memFile == null) return -1;
		return memFile.getNumBlocks() * BLOCKSIZE;
	}

	/**
	 * Get the index relative to the memory card of a specified block in a specified file.
	 * <br>This function will NOT work for deleted or Block 0 files.
	 * @param fileName System name of file.
	 * @param fbI Index of the query block relative to the file. (eg. For the 2nd block in a file,
	 * this parameter should be 1.)
	 * @return The index of the specified block on the memory card.
	 */
	public int FileBlockInd_to_MCBlockInd(String fileName, int fbI)
	{
		if (fileName == null) return -1;
		if (fileName.isEmpty()) return -1;
		if (fileName.charAt(0) == '*') return -1;
		if (fbI < 0) return -1;
		MCFile mcf = this.directory.get(fileName);
		if (mcf == null) return -1;
		if (fbI >= mcf.getNumBlocks()) return -1;
		return mcf.getBlockIndex(fbI);
	}

	
}
