package waffleoRai_Containers.nintendo;

import java.io.IOException;

import waffleoRai_Utils.FileBuffer;

public class NDKRevolutionFile extends NDKFile{

	private NDKRevolutionFile(FileBuffer src, int secCount)
	{
		data = src;
		sections = new NDKHeaderEntry[secCount];
	}
	
	public static NDKRevolutionFile readRevolutionFile(String path) throws IOException
	{
		FileBuffer file = FileBuffer.createBuffer(path, true);
		return readRevolutionFile(file);
	}
	
	public static NDKRevolutionFile readRevolutionFile(FileBuffer src)
	{
		if(src == null) return null;
		long cpos = 0;
		String fileid = src.getASCII_string(cpos, 4); cpos += 4;
		
		//Byte order mark
		int bom = Short.toUnsignedInt(src.shortFromFile(cpos)); cpos += 2;
		if(bom == 0xFFFE) src.setEndian(false);
		else src.setEndian(true);
		
		int ver_minor = 0;
		int ver_major = 0;
		if(src.isBigEndian())
		{
			ver_major = Byte.toUnsignedInt(src.getByte(cpos)); cpos++;
			ver_minor = Byte.toUnsignedInt(src.getByte(cpos)); cpos++;
		}
		else
		{
			ver_minor = Byte.toUnsignedInt(src.getByte(cpos)); cpos++;
			ver_major = Byte.toUnsignedInt(src.getByte(cpos)); cpos++;
		}
		
		long fileSize = Integer.toUnsignedLong(src.intFromFile(cpos)); cpos += 4;
		//Skip header length
		cpos += 2;
		int chunkCount = Short.toUnsignedInt(src.shortFromFile(cpos)); cpos += 2;
		
		NDKRevolutionFile rev = new NDKRevolutionFile(src, chunkCount);
		rev.fileMagic = fileid;
		rev.version_major = ver_major;
		rev.version_minor = ver_minor;
		rev.file_size = fileSize;
		
		for(int i = 0; i < chunkCount; i++)
		{
			int rawOffset = src.intFromFile(cpos); cpos+=4;
			int rawSize = src.intFromFile(cpos); cpos+=4;
			
			String secMag = src.getASCII_string(rawOffset, 4);
			NDKHeaderEntry he = new NDKHeaderEntry();
			he.setIdentifier(secMag);
			he.setOffset(Integer.toUnsignedLong(rawOffset));
			he.setLength(Integer.toUnsignedLong(rawSize));
			he.setType(NDKSectionType.getSectionType(secMag));
			
			rev.sections[i] = he;
		}
		
		return rev;
	}
	
}
