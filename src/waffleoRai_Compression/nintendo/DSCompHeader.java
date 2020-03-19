package waffleoRai_Compression.nintendo;

import waffleoRai_Utils.FileBuffer;

public class DSCompHeader {
	
	public static final int TYPE_LZ77 = 1;
	public static final int TYPE_HUFFMAN = 2;
	public static final int TYPE_RLE = 3;
	
	private int type;
	private int decomp_size;
	private int huff_val;
	
	private DSCompHeader()
	{
		type = TYPE_LZ77;
		decomp_size = -1;
		huff_val = 0;
	}
	
	public static DSCompHeader read(FileBuffer buff, long stpos)
	{
		DSCompHeader header = new DSCompHeader();
		int b0 = Byte.toUnsignedInt(buff.getByte(stpos));
		
		//Higher nybble is the type
		//Lower nybble is huff value
		
		header.type = (b0 >>> 4) & 0xF;
		header.huff_val = b0 & 0xF;
		
		header.decomp_size = buff.shortishFromFile(stpos+1);
		
		return header;
	}
	
	public int getType(){return type;}
	public int getDecompressedSize(){return decomp_size;}
	public int getHuffmanValue(){return huff_val;}

}
