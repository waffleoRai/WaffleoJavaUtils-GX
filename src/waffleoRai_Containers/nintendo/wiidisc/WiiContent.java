package waffleoRai_Containers.nintendo.wiidisc;

import waffleoRai_Utils.FileBuffer;

public class WiiContent {

	/* --- Constants --- */
	
	public static final int SIZE = 0x24;
	public static final long SIZE_LONG = 0x24L;
	
	/* --- Instance Variables --- */
	
	private int iContentID;
	private int iIndex;
	private int eType;
	private long iSize;
	private byte[] aSHA;
	
	/* --- Construction/Parsing --- */
	
	public WiiContent(){
		iContentID = 0;
		iIndex = 0;
		eType = 0;
		iSize = 0;
		aSHA = new byte[20];
	}
	
	public WiiContent(FileBuffer src, long stpos){
		this();
		long cpos = stpos;
		
		iContentID = src.intFromFile(cpos); cpos += 4;
		iIndex = Short.toUnsignedInt(src.shortFromFile(cpos)); cpos += 2;
		eType = Short.toUnsignedInt(src.shortFromFile(cpos)); cpos += 2;
		iSize = src.longFromFile(cpos); cpos += 8;
		for(int i = 0; i < 20; i++){aSHA[i] = src.getByte(cpos); cpos++;}
	}

	/* --- Getters --- */
	
	public int getContentID(){return iContentID;}
	public int getIndex(){return iIndex;}
	public int getType(){return eType;}
	public long getSize(){return iSize;}
	public byte[] getSHAHash(){return aSHA;}
	
	/* --- Serialization --- */
	
	public FileBuffer serializeContent(){
		FileBuffer content = new FileBuffer(SIZE, true);
		content.addToFile(iContentID);
		content.addToFile((short)iIndex);
		content.addToFile((short)eType);
		content.addToFile(iSize);
		for(int i = 0; i < 20; i++){content.addToFile(aSHA[i]);}
		
		return content;
	}
	
	public void printInfo(){
		System.out.println("\tContent ID: " + iContentID);
		System.out.println("\tIndex: " + iIndex);
		System.out.println("\tType: " + eType);
		System.out.println("\tSize: 0x" + Long.toHexString(iSize));
		System.out.println("\tSHA Checksum: ");
		System.out.print("\t\t");
		for(int i = 0; i < aSHA.length; i++)
		{
			System.out.print(String.format("%02x ", aSHA[i]));
			if(i%10 == 9){
				System.out.println();
				System.out.print("\t\t");
			}
		}
	}

}
