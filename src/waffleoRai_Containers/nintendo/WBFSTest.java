package waffleoRai_Containers.nintendo;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

import waffleoRai_Utils.FileBuffer;

public class WBFSTest {
	
	public static final int WII_SEC_SZ = 0x8000;
	public static final int WII_SEC_PER_DISC = 143432*2;
	public static final int WII_SEC_SZ_S = 15;

	public static void main(String[] args) 
	{
		String testfile = "C:\\Users\\Blythe\\Documents\\Game Stuff\\Wii\\Games\\Legend of Zelda, The - Skyward Sword.wbfs";
		String logfile = "C:\\Users\\Blythe\\Documents\\Game Stuff\\Wii\\Games\\wbfsjavatest.log";
		
		int readin = 0x400000;
		
		FileBuffer wbfs = null;
		try {wbfs = new FileBuffer(testfile, 0, readin, true);} 
		catch (IOException e) {e.printStackTrace(); System.exit(1);}
		
		int n_hd_sec = wbfs.intFromFile(0x04);
		int hd_sec_sz_s = Byte.toUnsignedInt(wbfs.getByte(0x08));
		int wbfs_sec_sz_s = Byte.toUnsignedInt(wbfs.getByte(0x09));
		
		int hd_sec_sz = 1 << hd_sec_sz_s;
		int wbfs_sec_sz = 1 << wbfs_sec_sz_s;
		
		long disc_sz_bytes = (long)n_hd_sec * (long)hd_sec_sz;
		System.err.println("disc_sz_bytes: " + disc_sz_bytes);
		long n_wbfs_sec = disc_sz_bytes/(long)wbfs_sec_sz;
		long n_wii_sec = disc_sz_bytes/(long)WII_SEC_SZ;
		
		final int dt_start = 0xC;
		int dtsize = hd_sec_sz-dt_start;
		int[] disc_table = new int[dtsize];
		
		System.err.println("dtsize: " + dtsize);
		for(int i = 0; i < dtsize; i++)
		{
			disc_table[i] = Byte.toUnsignedInt(wbfs.getByte(dt_start+i));
		}
		
		final int lbatbl_start = 0x300;
		int n_wbfs_sec_per_disc = WII_SEC_PER_DISC >> (wbfs_sec_sz_s - WII_SEC_SZ_S);
		int[] lba_table = new int[n_wbfs_sec_per_disc];
		long cpos = lbatbl_start;
		for(int i = 0; i < n_wbfs_sec_per_disc; i++)
		{
			lba_table[i] = Short.toUnsignedInt(wbfs.shortFromFile(cpos));
			cpos += 2;
		}
		
		//Dump info...
		System.out.println("Summary ---");
		System.out.println("HD Sector Size: 0x" + Integer.toHexString(hd_sec_sz));
		System.out.println("HD Sector Count: " + n_hd_sec);
		System.out.println("Wii Sector Size: 0x" + Integer.toHexString(WII_SEC_SZ));
		System.out.println("Wii Sector Count: " + n_wii_sec);
		System.out.println("Wii Sectors per Disc: " + WII_SEC_PER_DISC);
		System.out.println("WBFS Sector Size: 0x" + Integer.toHexString(wbfs_sec_sz));
		System.out.println("WBFS Sector Count: " + n_wbfs_sec);
		System.out.println("WBFS Sectors per Disc: " + n_wbfs_sec_per_disc);
		System.out.println("\nDisc Table ---");
		for(int i = 0; i < disc_table.length; i++)
		{
			System.out.println(i + "\t0x" + String.format("%02x", disc_table[i]) + "\t" + disc_table[i]);
		}
		
		System.out.println("\nNow printing lba table...");
		try 
		{
			BufferedWriter bw = new BufferedWriter(new FileWriter(logfile));
			bw.write("#INDEX(DEC)\tVALUE(HEX)\tVALUE(DEC)\tHDSecAddr\tWBFSSecAddr\tWiiSecAddr\n");
			for(int i = 0; i < lba_table.length; i++)
			{
				int val = lba_table[i];
				bw.write(i + "\t");
				bw.write(String.format("0x%04x", val) + "\t");
				bw.write(val + "\t");
				int hd_addr = val * hd_sec_sz;
				long wbfs_addr = (long)val * (long)wbfs_sec_sz;
				long wii_addr = (long)val * (long)WII_SEC_SZ;
				bw.write("0x" + Integer.toHexString(hd_addr) + "\t");
				bw.write("0x" + Long.toHexString(wbfs_addr) + "\t");
				bw.write("0x" + Long.toHexString(wii_addr) + "\n");
			}
			bw.close();
		} 
		catch (IOException e) 
		{
			e.printStackTrace();
		}
	}

}
