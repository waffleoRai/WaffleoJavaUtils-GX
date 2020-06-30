package waffleoRai_Containers.nintendo.wiidisc;

import waffleoRai_Containers.nintendo.WiiDisc;
import waffleoRai_Utils.FileBuffer;

public class WiiCertificate {

	public int eSigType;
	public byte[] sSignature;
	public String sIssuer;
	public int eKeyType;
	public String sName;
	public byte[] aKey;
	
	public static int getSignatureLength(int sigType){
		switch(sigType){
		case WiiDisc.SIGTYPE_RSA4096: return WiiDisc.SIGLEN_RSA4096;
		case WiiDisc.SIGTYPE_RSA2048: return WiiDisc.SIGLEN_RSA2048;
		case WiiDisc.SIGTYPE_ECCB223: return WiiDisc.SIGLEN_ECCB223;
		}
		return 256;
	}

	public static int getKeyLength(int keyType){
		switch(keyType){
		case WiiDisc.KEYTYPE_RSA4096: return WiiDisc.KEYLEN_RSA4096;
		case WiiDisc.KEYTYPE_RSA2048: return WiiDisc.KEYLEN_RSA2048;
		case WiiDisc.KEYTYPE_ECCB223: return WiiDisc.KEYLEN_ECCB223;
		}
		return 0;
	}

	public WiiCertificate(FileBuffer src, long stpos){
		long cpos = stpos;
		eSigType = src.intFromFile(cpos); cpos += 4;
		int siglen = getSignatureLength(eSigType);
		sSignature = new byte[siglen];
		for(int i = 0; i < siglen; i++){sSignature[i] = src.getByte(cpos); cpos++;}
		//Pad to 64 (?)
		cpos += 60;
		sIssuer = src.getASCII_string(cpos, 64); cpos += 64;
		eKeyType = src.intFromFile(cpos); cpos += 4;
		sName = src.getASCII_string(cpos, 64); cpos += 64;
		int keylen = getKeyLength(eKeyType);
		aKey = new byte[keylen];
		for(int i = 0; i < keylen; i++){aKey[i] = src.getByte(cpos); cpos++;}
	}
	
	public int getSize(){
		int sz = 4;
		sz += getSignatureLength(eSigType);
		sz += 60; //Padding 1
		sz += 64;
		sz += 4;
		sz += 64;
		sz += getKeyLength(eKeyType);
		sz += 64 - (sz % 64); //Padding 2
		return sz;
	}
	
	public FileBuffer serializeCert(){
		FileBuffer cert = new FileBuffer(getSize(), true);
		cert.addToFile(eSigType);
		for(int i = 0; i < sSignature.length; i++) cert.addToFile(sSignature[i]);
		//Padding
		for(int i = 0; i < 60; i++) cert.addToFile((byte)0x00);
		
		if(sIssuer.length() > 64) sIssuer = sIssuer.substring(0, 63);
		cert.printASCIIToFile(sIssuer);
		int ssz = sIssuer.length();
		while(ssz < 64){cert.addToFile((byte)0x00); ssz++;}
		cert.addToFile(eKeyType);
		if(sName.length() > 64) sName = sName.substring(0, 64);
		cert.printASCIIToFile(sName);
		ssz = sName.length();
		while(ssz < 64){cert.addToFile((byte)0x00); ssz++;}
		for(int i = 0; i < aKey.length; i++) cert.addToFile(aKey[i]);
		if((aKey.length + 4) % 64 != 0)
		{
			int padding = 64 - ((aKey.length + 4) % 64);
			for(int i = 0; i < padding; i++) cert.addToFile((byte)0x00);
		}
		
		return cert;
	}

	public void printInfo(){
		System.out.println("----- Wii Partition Certificate -----");
		System.out.println("Sig Type: 0x" + Integer.toHexString(eSigType));
		System.out.println("Sig Key: ");
		for(int i = 0; i < sSignature.length; i++)
		{
			System.out.print(String.format("%02x ", sSignature[i]));
			if(i%16 == 15) System.out.println();
		}
		if(sSignature.length % 16 != 0) System.out.println();
		System.out.println("Sig Issuer: " + sIssuer);
		
		System.out.println("Key Type: " + eKeyType);
		System.out.println("Name: " + sName);
		System.out.println("Key: ");
		for(int i = 0; i < aKey.length; i++)
		{
			System.out.print(String.format("%02x ", aKey[i]));
			if(i%16 == 15) System.out.println();
		}
		if(aKey.length % 16 != 0) System.out.println();
	}
	
}
