package waffleoRai_Encryption.nintendo.nitro;

import waffleoRai_Utils.FileBuffer;

public class PokeDPRandom {

	private int last = 0;
	
	public PokeDPRandom(short seed) {
		last = Short.toUnsignedInt(seed);
	}
	
	public short next() {
		//Not 100% sure if uses full value for next or the 16 bits it returned...
		int n = last * 0x41C64E6D;
		n += 0x6073;
		last = n;
		return (short)(n & 0xffff);
	}
	
	public static FileBuffer decryptSaveBlock(FileBuffer input, short seed) {
		if(input == null) return null;
		PokeDPRandom rand = new PokeDPRandom(seed);
		int wordCount = (int)input.getFileSize() >>> 1;
		FileBuffer out = new FileBuffer((int)input.getFileSize(), false);
		input.setCurrentPosition(0L);
		
		//TODO Byte order???
		for(int i = 0; i < wordCount; i++) {
			int word = Short.toUnsignedInt(input.nextShort());
			word ^= Short.toUnsignedInt(rand.next());
			out.addToFile((short)word);
		}
		
		return out;
	}
	
	public static FileBuffer decryptSpriteData(FileBuffer input) {
		if(input == null) return null;
		int fsize = (int)input.getFileSize();
		int wordCount = fsize >>> 1;
		FileBuffer out = new FileBuffer(fsize, false);
		for(int i = 0; i < fsize; i++) out.addToFile((byte)0);
		
		//Seed is the last word?
		long cpos = fsize - 2;
		short seed = input.shortFromFile(cpos); cpos -= 2;
		PokeDPRandom rand = new PokeDPRandom(seed);
		for(int i = 0; i < wordCount-1; i++) {
			int word = Short.toUnsignedInt(input.shortFromFile(cpos));
			word ^= Short.toUnsignedInt(rand.next());
			out.replaceShort((short)word, cpos);
			cpos -= 2;
		}
		
		return out;
	}
	
}
