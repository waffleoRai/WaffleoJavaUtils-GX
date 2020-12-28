package waffleoRai_PSXVMEM;

public class MemWord {

	private volatile int word;
	
	public MemWord()
	{
		word = 0;
	}
	
	protected static int trimWord(int offset, int nBytes, boolean signExtend, int myWord)
	{
		int i = myWord;
		if (offset != 0) i = i >>> (offset * 8);
		//With the MIPS instruction set, it should be impossible to grab anything
		//across word boundaries in one go. I'm banking on that here... 
		//(Don't want to slow things down with an unnecessary sanity check)
		if (nBytes < 4)
		{
			if (nBytes == 1)
			{
				if (signExtend)
				{
					byte b = (byte)i;
					i = (int)b;
				}
				else i = i & 0xFF;
			}
			else if (nBytes == 2)
			{
				if (signExtend)
				{
					short s = (short)i;
					i = (int)s;
				}
				else i = i & 0xFFFF;
			}
			else if (nBytes == 3)
			{
				i = i & 0x00FFFFFF;
			}
		}
		return i;
	}
	
	public int read(int offset, int nBytes, boolean signExtend)
	{
		int i = trimWord(offset, nBytes, signExtend, word);
		return i;
	}
	
	public void write(int offset, int nBytes, int value)
	{
		if (nBytes < 3)
		{
			int i = word;
			int mask = 0xFFFFFFFF;
			int shifter = 8 * (4 - nBytes);
			int offBits = 8 * offset;
			
			mask = mask << shifter; //SLL [8 * (4 - nBytes)]
			mask = mask >>> (shifter - offBits); //SRL [8 * (4 - nBytes - offset)]
			
			value = value << offBits;
		
			value = value & mask;
			i = i & ~mask;
			value = i | value;
		}
		
		synchronized(this)
		{
			word = value;	
		}
	}
	
	public int read()
	{
		return word;
	}
	
	public synchronized void write(int value)
	{
		word = value;
	}

	protected int getWord()
	{
		return word;
	}
	
	protected void setWord(int w)
	{
		word = w;
	}
	
}
