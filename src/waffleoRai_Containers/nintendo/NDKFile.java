package waffleoRai_Containers.nintendo;

import waffleoRai_Utils.FileBuffer;

public abstract class NDKFile {
	
	
	protected FileBuffer data;
	
	protected String fileMagic;
	protected int version_major;
	protected int version_minor;
	protected long file_size;

	protected NDKHeaderEntry[] sections;

	public FileBuffer getFileData()
	{
		return data;
	}
	
	public FileBuffer getSectionData(int index)
	{
		if(data == null) return null;
		if(index < 0 || index >= sections.length) return null;
		NDKHeaderEntry he = sections[index];
		return data.createReadOnlyCopy(he.getOffset(), he.getOffset() + he.getLength());
	}
	
	public FileBuffer getSectionData(String type)
	{
		if(data == null) return null;
		//Returns the first match
		for(int i = 0; i < sections.length; i++)
		{
			if(sections[i] != null)
			{
				String m = sections[i].getIdentifier();
				if(m != null)
				{
					if(m.equalsIgnoreCase(type))
					{
						return data.createReadOnlyCopy(sections[i].getOffset(), sections[i].getOffset() + sections[i].getLength());
					}
				}
			}
		}
		return null;
	}
	
	public FileBuffer getSectionData(NDKSectionType type)
	{
		if(data == null) return null;
		//Returns the first match
		for(int i = 0; i < sections.length; i++)
		{
			if(sections[i] != null)
			{
				if(sections[i].getType() == type)
				{
					return data.createReadOnlyCopy(sections[i].getOffset(), sections[i].getOffset() + sections[i].getLength());
				}
			}
		}
		return null;
	}
	
	public String getFileIdentifier()
	{
		return this.fileMagic;
	}
	
	public boolean isBigEndian()
	{
		return data.isBigEndian();
	}
	
	public int getMajorVersion()
	{
		return this.version_major;
	}
	
	public int getMinorVersion()
	{
		return this.version_minor;
	}
	
	public long getFileLength()
	{
		return this.file_size;
	}
	
	public int getSectionCount()
	{
		return this.sections.length;
	}
	
	public void freeSourceData()
	{
		data = null;
	}
	
	public long getOffsetToSection(NDKSectionType type)
	{
		for(int i = 0; i < sections.length; i++)
		{
			if(sections[i] != null)
			{
				if(sections[i].getType() == type)
				{
					return sections[i].getOffset();
				}
			}
		}
		return -1;
	}
	
	public long getOffsetToSection(String type)
	{
		if(data == null) return -1;
		//Returns the first match
		for(int i = 0; i < sections.length; i++)
		{
			if(sections[i] != null)
			{
				String m = sections[i].getIdentifier();
				if(m != null && m.equals(type))
				{
					return sections[i].getOffset();
				}
			}
		}
		return -1;
	}
	
	
}
