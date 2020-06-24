package waffleoRai_Containers.nintendo;

import waffleoRai_Utils.FileBuffer;

public class GCWiiHeader {
	
	/* --- Instance Variables --- */
	
	private char cDiscID;
	private String sGameCode;
	private char cRegCode;
	private String sMakerCode;
	
	private int iDiscNumber;
	private int iDiscVersion;
	
	private int iAudioStreaming;
	private int iStreamingBufferSize;
	
	private boolean bWiiMagic;
	private boolean bGCNMagic;
	
	private String sGameTitle;
	
	/* --- Construction --- */
	
	public GCWiiHeader(){
		iDiscNumber = -1;
		iDiscVersion = -1;
	}
	
	/* --- Setters --- */
	
	public void setDiscID(char c){cDiscID = c;}
	public void setGameCode(String s){sGameCode = s;}
	public void setRegionCode(char c){cRegCode = c;}
	public void setMakerCode(String s){sMakerCode = s;}
	public void setDiscNumber(int i){iDiscNumber = i;}
	public void setDiscVersion(int i){iDiscVersion = i;}
	public void setAudioStreaming(int i){iAudioStreaming = i;}
	public void setStreamingBufferSize(int i){iStreamingBufferSize = i;}
	public void setGameTitle(String s){sGameTitle = s;}
	public void setWii(boolean b){bWiiMagic = b;}
	public void setGamecube(boolean b){bGCNMagic = b;}
	
	/* --- Getters --- */
	
	public String getFullGameCode(){return cDiscID + sGameCode + cRegCode + sMakerCode;}
	public int getDiscNumber(){return iDiscNumber;}
	public int getDiscVersion(){return iDiscVersion;}
	public String getGameTitle(){return sGameTitle;}
	public String getGameCode(){return sGameCode;}
	public String getMakerCode(){return sMakerCode;}
	public String get4DigitGameCode(){return cDiscID + sGameCode + cRegCode;}
	
	/* --- Parsing --- */
	
	public void readFromDisc(FileBuffer src, long stpos)
	{
		long cpos = stpos;
		setDiscID((char)src.getByte(cpos)); cpos++;
		setGameCode(src.getASCII_string(cpos, 2)); cpos += 2;
		setRegionCode((char)src.getByte(cpos)); cpos++;
		setMakerCode(src.getASCII_string(cpos, 2)); cpos += 2;
		
		setDiscNumber(Byte.toUnsignedInt(src.getByte(cpos))); cpos++;
		setDiscVersion(Byte.toUnsignedInt(src.getByte(cpos))); cpos++;
		
		setAudioStreaming(Byte.toUnsignedInt(src.getByte(cpos))); cpos++;
		setStreamingBufferSize(Byte.toUnsignedInt(src.getByte(cpos))); cpos++;
		
		cpos += 14;
		
		int i = src.intFromFile(cpos); cpos += 4;
		//System.err.println("WBFSImage.readWiiDisc || -DEBUG- Wii Word Found: 0x" + Integer.toHexString(i));
		setWii(i == GCWiiDisc.WII_HEADER_MAGIC);
		i = src.intFromFile(cpos); cpos += 4;
		setGamecube(i == GCWiiDisc.GCN_HEADER_MAGIC);
		
		setGameTitle(src.getASCII_string(cpos, 64));
	}
	
	public static GCWiiHeader readHeader(FileBuffer src, long stpos)
	{
		GCWiiHeader header = new GCWiiHeader();
		header.readFromDisc(src, stpos);
		return header;
	}
	
	/* --- Serialization --- */
	
	public FileBuffer serializeHeader()
	{
		final byte ZERO = 0x00;
		
		FileBuffer header = new FileBuffer(0x400, true);
		header.addToFile((byte)cDiscID);
		if(sGameCode != null && !sGameCode.isEmpty())
		{
			if(sGameCode.length() >= 1) header.addToFile((byte)sGameCode.charAt(0));
			else header.addToFile(ZERO);
			if(sGameCode.length() >= 2) header.addToFile((byte)sGameCode.charAt(1));
			else header.addToFile(ZERO);
		}
		else header.addToFile((short)0);
		header.addToFile((byte)cRegCode);
		if(sMakerCode != null && !sMakerCode.isEmpty())
		{
			if(sMakerCode.length() >= 1) header.addToFile((byte)sMakerCode.charAt(0));
			else header.addToFile(ZERO);
			if(sMakerCode.length() >= 2) header.addToFile((byte)sMakerCode.charAt(1));
			else header.addToFile(ZERO);
		}
		else header.addToFile((short)0);
		
		header.addToFile((byte)iDiscNumber);
		header.addToFile((byte)iDiscVersion);
		header.addToFile((byte)iAudioStreaming);
		header.addToFile((byte)iStreamingBufferSize);
		
		for(int i = 0; i < 14; i++) header.addToFile(ZERO);
		
		if(bWiiMagic) header.addToFile(GCWiiDisc.WII_HEADER_MAGIC);
		else header.addToFile(0);
		if(bGCNMagic) header.addToFile(GCWiiDisc.GCN_HEADER_MAGIC);
		else header.addToFile(0);
		
		if(sGameTitle.length() > 64) sGameTitle = sGameTitle.substring(0, 64);
		header.printASCIIToFile(sGameTitle);
		int slen = sGameTitle.length();
		while(slen < 64){header.addToFile(ZERO); slen++;}
		
		for(int i = 0; i < 382; i++) header.addToFile(ZERO);
		
		return header;
	}
	
	public void printInfo()
	{
		System.out.println("Disc ID: " + cDiscID);
		System.out.println("Game Code: " + sGameCode);
		System.out.println("Region Code: " + cRegCode);
		System.out.println("Maker Code: " + sMakerCode);
		System.out.println("Disc Number: " + iDiscNumber);
		System.out.println("Disc Version: " + iDiscVersion);
		System.out.println("Audio Streaming: " + iAudioStreaming);
		System.out.println("Streaming Buffer Size: " + iStreamingBufferSize);
		System.out.println("Wii Game: " + bWiiMagic);
		System.out.println("Gamecube Game: " + bGCNMagic);
		System.out.println("Game Title: " + sGameTitle);
	}
	

}
