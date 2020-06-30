package waffleoRai_Containers.nintendo.wiidisc;

import waffleoRai_Containers.nintendo.WiiDisc;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.MultiFileBuffer;

public class WiiTMD {

	/* --- Instance Variables --- */
	
	private int eSigType;
	private byte[] aSignature;
	private String sIssuer;
	
	private int iVersion;
	private int iVersion_ca_crl;
	private int iVersion_signer_crl;
	
	private long iSystemVersion;
	private long iTitleID;
	private int eTitleType;
	private int iGroupID;
	
	private int eRegion;
	private byte[] aRatings;
	private byte[] aIPCMask;
	private int mAccessRights;
	
	private int iTitleVersion;
	private int iContentCount;
	private int iBootIndex;
	
	public WiiContent[] aContents;
	
	/* --- Construction/Parsing --- */
	
	public WiiTMD(){
		eSigType = WiiDisc.SIGTYPE_RSA2048;
		aRatings = new byte[16];
		aIPCMask = new byte[12];
	}
	
	public WiiTMD(FileBuffer src, long stpos){
		this();
		long cpos = stpos;
		
		eSigType = src.intFromFile(cpos); cpos += 4;
		int siglen = WiiCertificate.getSignatureLength(eSigType);
		aSignature = new byte[siglen];
		for(int i = 0; i < siglen; i++){aSignature[i] = src.getByte(cpos); cpos++;}
		
		cpos += 60; //Padding
		sIssuer = src.getASCII_string(cpos, 64); cpos += 64;
		
		iVersion = Byte.toUnsignedInt(src.getByte(cpos)); cpos++;
		iVersion_ca_crl = Byte.toUnsignedInt(src.getByte(cpos)); cpos++;
		iVersion_signer_crl = Byte.toUnsignedInt(src.getByte(cpos)); cpos++;
		cpos++; //Padding
		
		iSystemVersion = src.longFromFile(cpos); cpos += 8;
		//sTitleID = src.getASCII_string(cpos, 8); cpos += 8;
		iTitleID = src.longFromFile(cpos); cpos += 8;
		eTitleType = src.intFromFile(cpos); cpos += 4;
		iGroupID = Short.toUnsignedInt(src.shortFromFile(cpos)); cpos += 2;
		cpos += 2; //Padding
		eRegion = Short.toUnsignedInt(src.shortFromFile(cpos)); cpos += 2;
		
		for(int i = 0; i < 16; i++){aRatings[i] = src.getByte(cpos); cpos++;}
		cpos += 12; //Padding
		for(int i = 0; i < 12; i++){aIPCMask[i] = src.getByte(cpos); cpos++;}
		cpos += 18; //Padding
		mAccessRights = src.intFromFile(cpos); cpos += 4;
		
		iTitleVersion = Short.toUnsignedInt(src.shortFromFile(cpos)); cpos += 2;
		iContentCount = Short.toUnsignedInt(src.shortFromFile(cpos)); cpos += 2;
		iBootIndex = Short.toUnsignedInt(src.shortFromFile(cpos)); cpos += 2;
		cpos += 2; //Padding
		
		//Read the "Content" records
		aContents = new WiiContent[iContentCount];
		for(int i = 0; i < iContentCount; i++){
			aContents[i] = new WiiContent(src, cpos);
			cpos += WiiContent.SIZE_LONG;
		}
	}
	
	/* --- Getters --- */
	
	public int getSigType(){return eSigType;}
	public byte[] getSignature(){return aSignature;}
	public String getSignatureIssuer(){return sIssuer;}
	public int getVersion(){return iVersion;}
	public int getCAVersion(){return iVersion_ca_crl;}
	public int getSignerVersion(){return iVersion_signer_crl;}
	public long getSystemVersion(){return iSystemVersion;}
	public long getTitleID(){return iTitleID;}
	public int getTitleType(){return eTitleType;}
	public int getGroupID(){return iGroupID;}
	public int getRegion(){return eRegion;}
	public byte[] getRatings(){return aRatings;}
	public byte[] getIPCMask(){return aIPCMask;}
	public int getAccessMask(){return mAccessRights;}
	public int getTitleVersion(){return iTitleVersion;}
	public int getContentCount(){return iContentCount;}
	public int getBootIndex(){return iBootIndex;}
	public WiiContent getContent(int index){return aContents[index];}
	
	/* --- Serialization --- */
	
	public FileBuffer serializeTMD(){
		final int TMD_MAIN_SIZE = 0x1E4;
		final byte ZERO = 0x00;
		
		//FileBuffer tmd = new CompositeBuffer(1 + iContentCount);
		FileBuffer tmd = new MultiFileBuffer(1 + iContentCount);
		FileBuffer tmd_main = new FileBuffer(TMD_MAIN_SIZE, true);
		
		tmd_main.addToFile(eSigType);
		for(int i = 0; i < 0x100; i++){tmd_main.addToFile(aSignature[i]);}
		for(int i = 0; i < 60; i++){tmd_main.addToFile(ZERO);} //Padding
		
		if(sIssuer.length() > 64) sIssuer = sIssuer.substring(0, 64);
		tmd_main.printASCIIToFile(sIssuer);
		int slen = sIssuer.length();
		while(slen < 64){tmd_main.addToFile(ZERO); slen++;}
		
		tmd_main.addToFile((byte)iVersion);
		tmd_main.addToFile((byte)iVersion_ca_crl);
		tmd_main.addToFile((byte)iVersion_signer_crl);
		tmd_main.addToFile(ZERO); //Padding
		
		tmd_main.addToFile(iSystemVersion);
		tmd_main.addToFile(iTitleID);
		tmd_main.addToFile(eTitleType);
		tmd_main.addToFile((short)iGroupID);
		tmd_main.addToFile((short)0); //Padding
		tmd_main.addToFile((short)eRegion);
		for(int i = 0; i < 16; i++){tmd_main.addToFile(aRatings[i]);}
		for(int i = 0; i < 12; i++){tmd_main.addToFile(ZERO);} //Reserved
		for(int i = 0; i < 12; i++){tmd_main.addToFile(aIPCMask[i]);}
		for(int i = 0; i < 18; i++){tmd_main.addToFile(ZERO);} //Reserved
		tmd_main.addToFile(mAccessRights);
		tmd_main.addToFile((short)iTitleVersion);
		tmd_main.addToFile((short)iContentCount);
		tmd_main.addToFile((short)iBootIndex);
		tmd_main.addToFile((short)0); //Padding
		
		tmd.addToFile(tmd_main);
		
		//Contents
		for(int i = 0; i < aContents.length; i++) tmd.addToFile(aContents[i].serializeContent());

		return tmd;
	}
	
	public void printInfo(){
		System.out.println("----- Wii Partition TMD -----");
		System.out.println("Sig Type: 0x" + Integer.toHexString(eSigType));
		System.out.println("Sig Key: ");
		for(int i = 0; i < aSignature.length; i++)
		{
			System.out.print(String.format("%02x ", aSignature[i]));
			if(i%16 == 15) System.out.println();
		}
		if(aSignature.length % 16 != 0) System.out.println();
		System.out.println("Sig Issuer: " + sIssuer);
		System.out.println("Version: " + iVersion);
		System.out.println("Version (CA CRL): " + iVersion_ca_crl);
		System.out.println("Version (SIGNER CRL): " + iVersion_signer_crl);
		System.out.println("System Version: 0x" + Long.toHexString(iSystemVersion));
		System.out.println("Title ID: 0x" + Long.toHexString(iTitleID));
		System.out.println("Title Type: " + eTitleType);
		System.out.println("Group ID: 0x" + Integer.toHexString(iGroupID));
		System.out.println("Region: " + eRegion);
		System.out.println("Ratings: ");
		for(int i = 0; i < aRatings.length; i++)
		{
			System.out.print(String.format("%02x ", aRatings[i]));
			if(i%16 == 15) System.out.println();
		}
		if(aRatings.length % 16 != 0) System.out.println();
		System.out.println("IPC Mask: ");
		for(int i = 0; i < aIPCMask.length; i++)
		{
			System.out.print(String.format("%02x ", aIPCMask[i]));
			if(i%16 == 15) System.out.println();
		}
		if(aIPCMask.length % 16 != 0) System.out.println();
		System.out.println("Access Rights: 0x" + Integer.toHexString(mAccessRights));
		System.out.println("Title Version: " + iTitleVersion);
		System.out.println("Content Count: " + iContentCount);
		System.out.println("Boot Index: " + iBootIndex);
		for(int i = 0; i < aContents.length; i++)
		{
			System.out.println("Content " + i + ":");
			if(aContents[i] != null) aContents[i].printInfo();
		}
	}
	
}
