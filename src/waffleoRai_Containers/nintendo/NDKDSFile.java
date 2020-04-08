package waffleoRai_Containers.nintendo;

import java.io.IOException;

import waffleoRai_Utils.FileBuffer;

public class NDKDSFile extends NDKFile{
	
	protected NDKDSFile(FileBuffer src)
	{
		data = src;
		readHeader(false);
	}
	
	protected NDKDSFile(FileBuffer src, boolean tbl)
	{
		data = src;
		readHeader(tbl);
	}
	
	protected boolean readHeader(boolean hasTbl)
	{
		if(data == null) return false;
		data.setEndian(true);
		long cpos = 0;
		String fileid = data.getASCII_string(cpos, 4); cpos += 4;
		//System.err.println("Magic: " + fileid);
		
		//Byte order mark
		int bom = Short.toUnsignedInt(data.shortFromFile(cpos)); cpos += 2;
		if(bom == 0xFFFE) data.setEndian(true);
		else data.setEndian(false);
		//System.err.println("Big Endian: " + data.isBigEndian());
		
		int ver_minor = 0;
		int ver_major = 0;
		if(data.isBigEndian())
		{
			ver_major = Byte.toUnsignedInt(data.getByte(cpos)); cpos++;
			ver_minor = Byte.toUnsignedInt(data.getByte(cpos)); cpos++;
		}
		else
		{
			ver_minor = Byte.toUnsignedInt(data.getByte(cpos)); cpos++;
			ver_major = Byte.toUnsignedInt(data.getByte(cpos)); cpos++;
		}
		
		long fileSize = Integer.toUnsignedLong(data.intFromFile(cpos)); cpos += 4;
		//Skip header length
		cpos += 2;
		int chunkCount = Short.toUnsignedInt(data.shortFromFile(cpos)); cpos += 2;
		//System.err.println("Chunk count: 0x" + Integer.toHexString(chunkCount));
		
		//NDKDSFile dsf = new NDKDSFile(src, chunkCount);
		sections = new NDKHeaderEntry[chunkCount];
		fileMagic = fileid;
		version_major = ver_major;
		version_minor = ver_minor;
		file_size = fileSize;
		
		if(!hasTbl)
		{
			for(int i = 0; i < chunkCount; i++)
			{
				String secMag = data.getASCII_string(cpos, 4);
				//System.err.println("Chunk " + i + " magic number: " + secMag);
				long rawOffset = cpos;
				long rawSize = Integer.toUnsignedLong(data.intFromFile(cpos+4));
				NDKHeaderEntry he = new NDKHeaderEntry();
				
				he.setIdentifier(secMag);
				he.setOffset(rawOffset);
				he.setLength(rawSize);
				he.setType(NDKSectionType.getSectionType(secMag));
				
				cpos += rawSize;
				sections[i] = he;
			}	
		}
		else
		{
			for(int i = 0; i < chunkCount; i++)
			{
				long rawOffset = Integer.toUnsignedLong(data.intFromFile(cpos)); cpos+=4;
				long rawSize = Integer.toUnsignedLong(data.intFromFile(cpos)); cpos+=4;
				NDKHeaderEntry he = new NDKHeaderEntry();
				
				String secMag = data.getASCII_string(rawOffset, 4);
				
				he.setIdentifier(secMag);
				he.setOffset(rawOffset);
				he.setLength(rawSize);
				he.setType(NDKSectionType.getSectionType(secMag));
				
				sections[i] = he;
			}
		}
		
		return true;
	}
	
	public static NDKDSFile readDSFile(String path) throws IOException
	{
		FileBuffer file = FileBuffer.createBuffer(path, true);
		return readDSFile(file);
	}
	
	public static NDKDSFile readDSFile(FileBuffer src)
	{
		if(src == null) return null;
		NDKDSFile dsf = new NDKDSFile(src);
		
		return dsf;
	}
	
}
