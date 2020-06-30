package waffleoRai_Containers.nintendo.wiidisc;

import java.util.Random;

import waffleoRai_Containers.nintendo.WiiDisc;
import waffleoRai_Encryption.AES;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;

public class WiiTicket {

	/* --- Instance Variables --- */
	
	private int eSigType; //Should be 0x10001
	private byte[] aSigKey; //Length 0x100
	private String sSigIssuer;
	private byte[] aECDH;
	private byte[] aEncryptedTitleKey;
	private byte[] aTicketID; //At offset 0x1D0
	private int iConsoleID;
	private byte[] aInitVector; //At offset 0x1DC
	private short iTicketTitleVersion;
	private int iPermittedTitlesMask;
	private int iPermitMask;
	private boolean bTitleExport;
	private int eCommonKeyIndex;
	private byte[] aContentAccessPermission;

	public TimeLimit[] aTimeLimits;
	
	/* --- Inner Classes --- */
	
	public static class TimeLimit
	{
		public boolean bEnableTimeLimit;
		public int iTimeLimit;
		
		public TimeLimit(){bEnableTimeLimit = false; iTimeLimit = 0;}
	}
	
	/* --- Construction/Parsing --- */
	
	public WiiTicket(){
		eSigType = WiiDisc.SIGTYPE_RSA2048;
		aSigKey = new byte[0x100];
		sSigIssuer = "UNKNOWN";
		aECDH = new byte[0x3C];
		
		aEncryptedTitleKey = new byte[16];
		Random r = new Random();
		r.nextBytes(aEncryptedTitleKey);
		
		aTicketID = new byte[16];
		iConsoleID = 0;
		aInitVector = new byte[16];
		iTicketTitleVersion = 0;
		
		iPermittedTitlesMask = -1;
		iPermitMask = -1;
		bTitleExport = false;
		eCommonKeyIndex = 0;
		
		aContentAccessPermission = new byte[0x40];
		
		aTimeLimits = new TimeLimit[8];
		for(int i = 0; i < 8; i++) aTimeLimits[i] = new TimeLimit();
	}
	
	public WiiTicket(FileBuffer src, long stpos){
		this();
		//System.err.println("WiiDisc.Ticket.<init> || -DEBUG- Function called!");
		
		long cpos = stpos;
		src.setEndian(true);
		
		eSigType = src.intFromFile(cpos); cpos += 4;
		//System.err.println("WiiDisc.Ticket.<init> || -DEBUG- eSigType: 0x" + Integer.toHexString(eSigType));
		for(int i = 0; i < 0x100; i++) {aSigKey[i] = src.getByte(cpos); cpos++;}
		cpos += 0x3C; //Padding
		sSigIssuer = src.getASCII_string(cpos, 0x40); cpos += 0x40;
		for(int i = 0; i < 0x3C; i++) {aECDH[i] = src.getByte(cpos); cpos++;}
		cpos += 3; //Padding
		for(int i = 0; i < 0x10; i++) {aEncryptedTitleKey[i] = src.getByte(cpos); cpos++;}
		cpos++; //Unknown
		for(int i = 0; i < 0x08; i++) {aTicketID[i] = src.getByte(cpos); cpos++;}
		iConsoleID = src.intFromFile(cpos); cpos += 4;
		for(int i = 0; i < 0x08; i++) {aInitVector[i] = src.getByte(cpos); cpos++;}
		cpos += 2; //Unknown 0xFFFF
		iTicketTitleVersion = src.shortFromFile(cpos); cpos += 2;
		iPermittedTitlesMask = src.intFromFile(cpos); cpos += 4;
		iPermitMask = src.intFromFile(cpos); cpos += 4;
		
		byte b = src.getByte(cpos); cpos++;
		bTitleExport = (b != 0);
		eCommonKeyIndex = Byte.toUnsignedInt(src.getByte(cpos)); cpos++;
		cpos += 0x30; //Unknown
		for(int i = 0; i < 0x40; i++) {aContentAccessPermission[i] = src.getByte(cpos); cpos++;}
		cpos += 2; //Padding
		
		for(int j = 0; j < 8; j++)
		{
			aTimeLimits[j].bEnableTimeLimit = (src.intFromFile(cpos) != 0); cpos += 4;
			aTimeLimits[j].iTimeLimit = src.intFromFile(cpos); cpos += 4;
		}
	}
	
	/* --- Getters --- */
	
	public int getSigType(){return eSigType;}
	public byte[] getSigKey(){return aSigKey;}
	public String getSigIssuer(){return sSigIssuer;}
	public byte[] getECDH(){return aECDH;}
	public byte[] getEncryptedTitleKey(){return aEncryptedTitleKey;}
	public byte[] getTicketID(){return aTicketID;}
	public int getConsoleID(){return iConsoleID;}
	public byte[] getInitVector(){return aInitVector;}
	public short getTicketTitleVersion(){return iTicketTitleVersion;}
	public int getPermittedTitlesMask(){return iPermittedTitlesMask;}
	public int getPermitMask(){return iPermitMask;}
	public boolean allowsTitleExport(){return bTitleExport;}
	public int getCommonKeyIndex(){return eCommonKeyIndex;}
	public byte[] getContentAccessPermissions(){return aContentAccessPermission;}
	public TimeLimit getTimeLimit(int index){return aTimeLimits[index];}
	
	/* --- Utility --- */
	
	public byte[] decryptTitleKey() throws UnsupportedFileTypeException{
		if(eCommonKeyIndex != 0) throw new FileBuffer.UnsupportedFileTypeException("Ticket requests unknown common key!");
		
		//If no common key, return null!
		int[] common = WiiDisc.getCommonKey();
		if(common == null) return null;
		
		AES wiiCrypt = new AES(common);
		byte[] decKey = wiiCrypt.decrypt(aInitVector, aEncryptedTitleKey);
		return decKey;
	}
	
	/* --- Serialization --- */
	
	public FileBuffer serializeTicket(){
		final int TICKET_SIZE = 0x2A4;
		final byte ZERO = 0x00;
		
		FileBuffer ticket = new FileBuffer(TICKET_SIZE, true);
		
		ticket.addToFile(eSigType);
		for(int i = 0; i < 0x100; i++) ticket.addToFile(aSigKey[i]);
		//0x3C of padding
		for(int i = 0; i < 0x3C; i++) ticket.addToFile(ZERO);
		
		//Issuer
		if(sSigIssuer.length() > 0x40) sSigIssuer = sSigIssuer.substring(0, 0x40);
		int slen = sSigIssuer.length();
		ticket.printASCIIToFile(sSigIssuer);
		while(slen < 0x40){ticket.addToFile(ZERO); slen++;}
		
		for(int i = 0; i < aECDH.length; i++) ticket.addToFile(aECDH[i]);
		ticket.add24ToFile(0); //3 bytes padding
		for(int i = 0; i < 0x10; i++) ticket.addToFile(aEncryptedTitleKey[i]);
		ticket.addToFile(ZERO); //Padding?
		
		for(int i = 0; i < 8; i++) ticket.addToFile(aTicketID[i]);
		ticket.addToFile(iConsoleID);
		for(int i = 0; i < 8; i++) ticket.addToFile(aInitVector[i]);
		ticket.addToFile((short)0xFFFF);
		ticket.addToFile(iTicketTitleVersion);
		ticket.addToFile(iPermittedTitlesMask);
		ticket.addToFile(iPermitMask);
		if(bTitleExport) ticket.addToFile((byte)0x01);
		else ticket.addToFile(ZERO);
		ticket.addToFile((byte)eCommonKeyIndex);
		//0x30 bytes of "unknown"
		//We'll put all 0 for now, though some titles have some non-zero bytes
		for(int i = 0; i < 0x30; i++) ticket.addToFile(ZERO);
		for(int i = 0; i < 0x40; i++) ticket.addToFile(aContentAccessPermission[i]);
		ticket.addToFile((short)0); //2 bytes padding
		
		//Time limits
		for(int i = 0; i < 8; i++)
		{
			TimeLimit tl = aTimeLimits[i];
			if(tl.bEnableTimeLimit) ticket.addToFile(1);
			else ticket.addToFile(0);
			ticket.addToFile(tl.iTimeLimit);
		}
		
		return ticket;
	}

	public void printInfo(){
		System.out.println("----- Wii Partition Ticket -----");
		System.out.println("Sig Type: 0x" + Integer.toHexString(eSigType));
		System.out.println("Sig Key: ");
		for(int i = 0; i < aSigKey.length; i++)
		{
			System.out.print(String.format("%02x ", aSigKey[i]));
			if(i%16 == 15) System.out.println();
		}
		if(aSigKey.length % 16 != 0) System.out.println();
		System.out.println("Sig Issuer: " + sSigIssuer);
		System.out.println("ECDH: ");
		for(int i = 0; i < aECDH.length; i++)
		{
			System.out.print(String.format("%02x ", aECDH[i]));
			if(i%16 == 15) System.out.println();
		}
		if(aECDH.length % 16 != 0) System.out.println();
		System.out.println("Encrypted Title Key: ");
		for(int i = 0; i < aEncryptedTitleKey.length; i++)
		{
			System.out.print(String.format("%02x ", aEncryptedTitleKey[i]));
			if(i%16 == 15) System.out.println();
		}
		if(aEncryptedTitleKey.length % 16 != 0) System.out.println();
		System.out.println("Ticket ID: ");
		for(int i = 0; i < aTicketID.length; i++)
		{
			System.out.print(String.format("%02x ", aTicketID[i]));
			if(i%16 == 15) System.out.println();
		}
		if(aTicketID.length % 16 != 0) System.out.println();
		System.out.println("Console ID: " + iConsoleID);
		System.out.println("Init Vector: ");
		for(int i = 0; i < aInitVector.length; i++)
		{
			System.out.print(String.format("%02x ", aInitVector[i]));
			if(i%16 == 15) System.out.println();
		}
		if(aInitVector.length % 16 != 0) System.out.println();
		System.out.println("Ticket Title Version: " + iTicketTitleVersion);
		System.out.println("Permitted Titles Mask: 0x" + Integer.toHexString(iPermittedTitlesMask));
		System.out.println("Permit Mask: 0x" + Integer.toHexString(iPermitMask));
		System.out.println("Title Export Allowed: " + this.bTitleExport);
		System.out.println("Common Key Index: " + eCommonKeyIndex);
		System.out.println("Content Access Permissions: ");
		for(int i = 0; i < aContentAccessPermission.length; i++)
		{
			System.out.print(String.format("%02x ", aContentAccessPermission[i]));
			if(i%16 == 15) System.out.println();
		}
		if(aContentAccessPermission.length % 16 != 0) System.out.println();
		for(int i = 0; i < aTimeLimits.length; i++)
		{
			System.out.println("Time Limit " + i + ":");
			TimeLimit tl = aTimeLimits[i];
			if(tl != null)
			{
				System.out.println("\tEnabled: " + tl.bEnableTimeLimit);
				System.out.println("\tSeconds: " + tl.iTimeLimit);
			}
		}
	}
	
}
