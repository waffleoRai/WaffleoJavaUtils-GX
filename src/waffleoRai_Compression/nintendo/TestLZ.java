package waffleoRai_Compression.nintendo;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.util.LinkedList;
import java.util.List;

import waffleoRai_Containers.nintendo.NARC;
import waffleoRai_Utils.DirectoryNode;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.StreamWrapper;

@SuppressWarnings("unused")
public class TestLZ {
	
	private static void lzassess(FileBuffer file)
	{
		long cpos = 4;
		List<Long> ch_pos = new LinkedList<Long>();
		
		int size = 0;
		int j = 0;
		int bczero_ct = 0;
		int comps = 0;
		while(cpos < file.getFileSize())
		{
			//System.err.print("cpos: 0x" + Long.toHexString(cpos));
			ch_pos.add(cpos);
			int cheader = Byte.toUnsignedInt(file.getByte(cpos)); cpos++;
			//cheader = ((cheader & 0xF) << 4) | ((cheader >>> 4) & 0xF);
			int mask = 0x80;
			//int mask = 0x1;
			int setbits = 0;
			for(int i = 0; i < 8; i++)
			{
				if((cheader & mask) != 0)
				{
					//compressed
					int b0 = Byte.toUnsignedInt(file.getByte(cpos)); cpos++;
					int b1 = Byte.toUnsignedInt(file.getByte(cpos)); cpos++;
					//We just want size
					int bcount = ((b0 >>> 4) & 0xF);
					if(bcount == 0) bczero_ct++;
					size += bcount + 3;
					setbits++;
					comps++;
				}
				else
				{
					//Copy byte
					if(cpos < file.getFileSize()){
						cpos++;
						size++;
					}
				}
				mask = mask >>> 1;
				//mask = mask << 1;
			}
			//System.err.print(" | setbits: " + setbits + "\n");
			/*if(j < 5)
			{
				System.err.println("cheader: 0x" + Integer.toHexString(cheader) + " | setbits: " + setbits);
				if(setbits > 0) j++;
			}*/
		}
		
		System.err.println("Predicted Blocks: " + ch_pos.size());
		System.err.println("Predicted Size: 0x" + Integer.toHexString(size));
		System.err.println("Compressed Sub-Blocks: " + comps);
		System.err.println("Min Size Sub-Blocks: " + bczero_ct);
	}

	public static void main(String[] args) {
		
		String testpath = "C:\\Users\\Blythe\\Documents\\Game Stuff\\DS\\Games\\filedumps\\AMCE__MARIOKARTDS\\AMCE\\data\\KartModelMenu.carc";
		String decpath = "C:\\Users\\Blythe\\Documents\\Game Stuff\\DS\\test\\KartModelMenu.narc";
		
		
		try
		{
			FileBuffer buff = FileBuffer.createBuffer(testpath, false);
			//Read DS Header
			DSCompHeader compdat = DSCompHeader.read(buff, 0);
			
			//Get decomp size.
			int decsz = compdat.getDecompressedSize();
			System.err.println("Expected decomp size: 0x" + Integer.toHexString(decsz));
			//lzassess(buff);
			
			
			//Try running NinLZ
			//NinLZ lz = new NinLZ(false);
			//StreamWrapper dec = lz.decode(buff.createReadOnlyCopy(4, buff.getFileSize()), decsz);
			/*Yaz lz = new Yaz();
			lz.setAllow3Bytes(false);
			lz.setReverseFlags(true);
			StreamWrapper dec = lz.decode(buff.createReadOnlyCopy(4, buff.getFileSize()), decsz);
			dec.rewind();
			
			//Write decompressed stream
			BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(decpath));
			while(!dec.isEmpty()) bos.write(dec.getFull());
			bos.close();*/
			
			//Try reading result as NARC
			NARC arc = NARC.readNARC(decpath);
			DirectoryNode root = arc.getArchiveTree();
			root.printMeToStdErr(0);
		}
		catch(Exception x)
		{
			x.printStackTrace();
			System.exit(1);
		}
		
	}

}
