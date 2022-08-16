package waffleoRai_Containers.nintendo.citrus;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;

import waffleoRai_Encryption.AES;
import waffleoRai_Encryption.DecryptorMethod;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileUtils;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;

//https://www.3dbrew.org/wiki/AES_Registers
//https://github.com/yellows8/boot9_tools

public class CitrusCrypt {

	/*----- Constants -----*/
	
	public static final int KEYSLOT_TWL_0 = 0;
	public static final int KEYSLOT_TWL_1 = 1;
	public static final int KEYSLOT_TWL_2 = 2;
	public static final int KEYSLOT_TWL_3 = 3;
	public static final int KEYSLOT_NAND_0 = 4; //otp (X)
	public static final int KEYSLOT_NAND_1 = 5; //otp (X)
	public static final int KEYSLOT_NAND_2 = 6; //otp (X)
	public static final int KEYSLOT_NAND_3 = 7; //otp (X)
	
	public static final int KEYSLOT_DSi_EXP = 0xA; //otp (X)
	public static final int KEYSLOT_AESCMAC = 0xB; //otp (X)
	public static final int KEYSLOT_SSL = 0xD; //otp (X)
	public static final int KEYSLOT_TEMP = 0x11; 
	public static final int KEYSLOT_FIRM5 = 0x14; //otp (X)
	public static final int KEYSLOT_NEW9LOADER_0 = 0x15; //otp (X)
	public static final int KEYSLOT_NEW9LOADER_1 = 0x16; //otp (X)
	
	public static final int KEYSLOT_NEW3DS_NCC_A = 0x18; //Firmware (X)
	public static final int KEYSLOT_NEW3DS_AESCMAC = 0x19; //otp (X)
	public static final int KEYSLOT_NEW3DS_SAVEKEY = 0x1A; //otp (X)
	public static final int KEYSLOT_NEW3DS_NCC_B = 0x1B; //Firmware (X)
	
	public static final int KEYSLOT_AGBFIRM_AESCMAC = 0x24; //otp (X)
	public static final int KEYSLOT_NCC_1 = 0x25; //Firmware (X)
	public static final int KEYSLOT_NCC_0 = 0x2C;
	
	public static final int KEYSLOT_WLANCCMP = 0x2D;
	public static final int KEYSLOT_STREETPASS = 0x2E;
	public static final int KEYSLOT_SAVEKEY = 0x2F;
	
	public static final int KEYSLOT_SDNAND_CMAC = 0x30; //movable.sed (Y)
	public static final int KEYSLOT_APTWRAP = 0x31;
	public static final int KEYSLOT_GAMECARD_SAVE_CMAC = 0x33;
	public static final int KEYSLOT_SDKEY = 0x34; //movable.sed (Y)
	public static final int KEYSLOT_MOVEABLE_SED = 0x35;
	public static final int KEYSLOT_GAMECARD_SAVE = 0x37;
	public static final int KEYSLOT_BOSSKEY = 0x38;
	public static final int KEYSLOT_DLPLAY = 0x39;
	
	public static final int KEYSLOT_DSiWARE_SDEXP = 0x3A; //movable.sed (Y)
	public static final int KEYSLOT_CTRCARD_HARDSEED = 0x3B;
	public static final int KEYSLOT_CTR_COMMON = 0x3D;
	public static final int KEYSLOT_BOOT9USE = 0x3F;
	
	public static final int[] C1 = {0x1F, 0xF9, 0xE9, 0xAA,
								    0xC5, 0xFE, 0x04, 0x08,
								    0x02, 0x45, 0x91, 0xDC,
								    0x5D, 0x52, 0x76, 0x8A}; //3DS
	public static final int[] C2 = {0xFF, 0xFE, 0xFB, 0x4E,
									0x29, 0x59, 0x02, 0x58,
									0x2A, 0x68, 0x0F, 0x5F,
									0x1A, 0x4F, 0x3E, 0x79}; //DSi
	
	public static final long BOOT9_AES3F_OFF_RETAIL = 0xd6e0;
	public static final long BOOT9_AES3F_OFF_DEV = 0xd700;
	public static final long BOOT9_ROMSEED_OFF_RETAIL = 0xd860;
	public static final long BOOT9_ROMSEED_OFF_DEV = 0xdc60;
	public static final long MOVABLESED_KEY_OFFSET = 0x110;
	
	public static final int[][] FIXED_X_MAP = {{0x2C, 0x170}, {0x2D, 0x170}, {0x2E, 0x170}, {0x2F, 0x170},
											   {0x30, 0x180}, {0x31, 0x180}, {0x32, 0x180}, {0x33, 0x180},
											   {0x34, 0x190}, {0x35, 0x190}, {0x36, 0x190}, {0x37, 0x190},
											   {0x38, 0x1A0}, {0x39, 0x1A0}, {0x3A, 0x1A0}, {0x3B, 0x1A0},
											   {0x3C, 0x1B0}, {0x3D, 0x1C0}, {0x3E, 0x1D0}, {0x3F, 0x1E0}};
	public static final int[][] FIXED_Y_MAP = {{0x04, 0x1F0}, {0x05, 0x200}, {0x06, 0x210}, {0x07, 0x220},
			   								   {0x08, 0x230}, {0x09, 0x240}, {0x0A, 0x250}, {0x0B, 0x260}};
	public static final int[][] FIXED_NORM_MAP = {{0x0C, 0x270}, {0x0D, 0x270}, {0x0E, 0x270}, {0x0F, 0x270},
												  {0x10, 0x280}, {0x14, 0x290}, {0x15, 0x2A0}, {0x16, 0x2B0}, {0x17, 0x2C0},
												  {0x18, 0x2D0}, {0x19, 0x2D0}, {0x1A, 0x2D0}, {0x1B, 0x2D0},
												  {0x1C, 0x2E0}, {0x1D, 0x2E0}, {0x1E, 0x2E0}, {0x1F, 0x2E0},
												  {0x20, 0x2F0}, {0x21, 0x2F0}, {0x22, 0x2F0}, {0x23, 0x2F0},
												  {0x24, 0x300}, {0x28, 0x300}, {0x29, 0x310}, {0x2A, 0x320}, {0x2B, 0x330}, 
												  {0x2C, 0x340}, {0x2D, 0x340}, {0x2E, 0x340}, {0x2F, 0x340},
												  {0x30, 0x350}, {0x31, 0x350}, {0x32, 0x350}, {0x33, 0x350},
												  {0x34, 0x360}, {0x35, 0x360}, {0x36, 0x360}, {0x37, 0x360},
												  {0x38, 0x370}, {0x3C, 0x370}, {0x3D, 0x380}, {0x3E, 0x390}, {0x3F, 0x3A0}};
	
	/*----- Instance Variables -----*/
	
	private KeySlot[] keyslots;
	
	/*----- Structs -----*/
	
	private static class KeySlot{
		
		public byte[] normal;
		public byte[] keyX;
		public byte[] keyY;
		
		public KeySlot(){
			normal = new byte[16];
			keyX = new byte[16];
			keyY = new byte[16];
		}
		
	}
	
	public static class CitrusCTRDecMethod implements DecryptorMethod{

		private AES aes;
		private byte[] base_ctr; //For THIS FILE (not container!)
		
		public CitrusCTRDecMethod(AES engine, byte[] basectr){
			aes = engine;
			aes.setCTR();
			base_ctr = basectr;
		}
		
		public byte[] decrypt(byte[] input, long offval) {

			//Calculate offset CTR
			byte[] ctr = adjustCTR(base_ctr, offval);
			
			return aes.decrypt(ctr, input);
		}
		
		public void adjustOffsetBy(long value){
			base_ctr = adjustCTR(base_ctr, value);
		}
		
		public int getInputBlockSize(){return 0x10;}
		public int getOutputBlockSize(){return 0x10;}
		public int getPreferredBufferSizeBlocks(){return 0x40;}
		
		public long getOutputBlockOffset(long inputBlockOffset){
			return inputBlockOffset;
		}
		
		public long getInputBlockOffset(long outputBlockOffset){
			return outputBlockOffset;
		}
		
		public long getOutputCoordinate(long inputCoord){
			return inputCoord;
		}
		
		public long getInputCoordinate(long outputCoord){
			return outputCoord;
		}
		
		public int backbyteCount(){return 0;}	
		public void putBackbytes(byte[] dat){}
		
		public DecryptorMethod createCopy(){
			CitrusCTRDecMethod copy = new CitrusCTRDecMethod(aes, base_ctr);
			return copy;
		}
	}
	
	/*----- Construction -----*/
	
	public CitrusCrypt(){
		keyslots = new KeySlot[0x40];
		for(int i = 0; i < 0x40; i++) keyslots[i] = new KeySlot();
	}
	
	public static CitrusCrypt initFromBoot9(FileBuffer boot9){
		CitrusCrypt crypto = new CitrusCrypt();
		crypto.loadFixedXKeys(boot9);
		crypto.loadFixedYKeys(boot9);
		crypto.loadFixedNormalKeys(boot9);
		
		return crypto;
	}
	
	public static CitrusCrypt initFromConsoleData(FileBuffer boot9, FileBuffer otp, FileBuffer movesed) throws UnsupportedFileTypeException{
		CitrusCrypt crypto = new CitrusCrypt();
		
		//Decrypt OTP
		FileBuffer otp_dec = crypto.decryptOTP(boot9, otp, true);
		crypto.generateKeysFromOTP(boot9, otp_dec);
		
		//Load common
		crypto.loadFixedXKeys(boot9);
		crypto.loadFixedYKeys(boot9);
		crypto.loadFixedNormalKeys(boot9);
		
		//Add movable.sed
		crypto.loadFromMovable(movesed);
		
		return crypto;
	}
	
	public static CitrusCrypt loadCitrusCrypt(FileBuffer savedata){
		CitrusCrypt crypto = new CitrusCrypt();
		long cpos = 0;
		
		for(int i = 0; i < 0x40; i++){
			crypto.setKeyX(i, savedata.getBytes(cpos, cpos+0x10)); cpos += 0x10;
			crypto.setKeyY(i, savedata.getBytes(cpos, cpos+0x10)); cpos += 0x10;
			crypto.setNormalKey(i, savedata.getBytes(cpos, cpos+0x10)); cpos += 0x10;
		}
		
		return crypto;
	}
	
	private void generateKeysFromOTP(FileBuffer boot9, FileBuffer otp_dec) throws UnsupportedFileTypeException{
		
		final long rom_seed_offset = BOOT9_ROMSEED_OFF_RETAIL;
		final long otp_seed_offset = 0x90;
		
		byte[] tempbuff = new byte[0x40];
		byte[] hashbuff = null;
		for(int i = 0; i < 0x1C; i++) tempbuff[i] = otp_dec.getByte(otp_seed_offset + i);
		for(int i = 0; i < 0x24; i++) tempbuff[i+0x1C] = boot9.getByte(rom_seed_offset + i);
		try{
			MessageDigest sha = MessageDigest.getInstance("SHA-256");
			sha.update(tempbuff);
			hashbuff = sha.digest();
		}
		catch(Exception x){
			x.printStackTrace();
			throw new FileBuffer.UnsupportedFileTypeException("SHA-256 hash failed");
		}
		
		//Set key XY from that crazy hash... (I think 0x3F?)
		byte[] barr = new byte[16];
		for(int i = 0; i < 16; i++) barr[i] = hashbuff[i];
		setKeyX(KEYSLOT_BOOT9USE, barr);
		barr = new byte[16];
		for(int i = 0; i < 16; i++) barr[i] = hashbuff[i+0x10];
		setKeyY(KEYSLOT_BOOT9USE, barr);
		
		//Use that key to encrypt some more garbage from boot9
		//Round 1
		//(Slots 0x04 - 0x10 from ROM Seed 0x24 - 0x74)
		byte[] iv = boot9.getBytes(rom_seed_offset+0x24, rom_seed_offset+0x34);
		byte[] bootdat = boot9.getBytes(rom_seed_offset+0x34, rom_seed_offset+0x74);
		AES aes = new AES(getNormalKey(KEYSLOT_BOOT9USE));
		byte[] enc = aes.encrypt(iv, bootdat);
		//Take parts from this enc and copy to keyslots x for slots 4-16
		for(int i = 4; i < 8; i++){
			byte[] keyx = new byte[16];
			for(int j = 0; j < 16; j++) keyx[j] = enc[j];
			setKeyX(i, keyx);
		}
		for(int i = 8; i <0xC; i++){
			byte[] keyx = new byte[16];
			for(int j = 0; j < 16; j++) keyx[j] = enc[j+0x10];
			setKeyX(i, keyx);
		}
		for(int i = 0xC; i <0x10; i++){
			byte[] keyx = new byte[16];
			for(int j = 0; j < 16; j++) keyx[j] = enc[j+0x20];
			setKeyX(i, keyx);
		}
		byte[] keyx = new byte[16];
		for(int j = 0; j < 16; j++) keyx[j] = enc[j+0x30];
		setKeyX(0x10, keyx);
		
		//Repeat a few more times for other keyslots
		//Round 2
		//(Slots 0x14 - 0x17 from ROM Seed 0x98 - 0xE8)
		iv = boot9.getBytes(rom_seed_offset+0x98, rom_seed_offset+0xA8);
		bootdat = boot9.getBytes(rom_seed_offset+0xA8, rom_seed_offset+0xE8);
		enc = aes.encrypt(iv, bootdat);
		for(int i = 0; i < 4; i++){
			keyx = new byte[16];
			for(int j = 0; j < 16; j++) keyx[j] = enc[j+(0x10*i)];
			setKeyX(i+0x14, keyx);
		}
		
		//Round 3
		//(Slots 0x18 - 0x24 from ROM Seed 0xDC - 0x12C)
		iv = boot9.getBytes(rom_seed_offset+0xDC, rom_seed_offset+0xEC);
		bootdat = boot9.getBytes(rom_seed_offset+0xEC, rom_seed_offset+0x12C);
		enc = aes.encrypt(iv, bootdat);
		for(int i = 0; i < 4; i++){
			keyx = new byte[16];
			for(int j = 0; j < 16; j++) keyx[j] = enc[j];
			setKeyX(i+0x18, keyx);
		}
		for(int i = 0; i < 4; i++){
			keyx = new byte[16];
			for(int j = 0; j < 16; j++) keyx[j] = enc[j+0x10];
			setKeyX(i+0x1C, keyx);
		}
		for(int i = 0; i < 4; i++){
			keyx = new byte[16];
			for(int j = 0; j < 16; j++) keyx[j] = enc[j+0x20];
			setKeyX(i+0x20, keyx);
		}
		keyx = new byte[16];
		for(int j = 0; j < 16; j++) keyx[j] = enc[j+0x30];
		setKeyX(0x24, keyx);
		
		//Round 4
		//(Slots 0x28 - 0x2B from ROM Seed 0x150 - 0x1A0)
		iv = boot9.getBytes(rom_seed_offset+0x150, rom_seed_offset+0x160);
		bootdat = boot9.getBytes(rom_seed_offset+0x160, rom_seed_offset+0x1A0);
		enc = aes.encrypt(iv, bootdat);
		for(int i = 0; i < 4; i++){
			keyx = new byte[16];
			for(int j = 0; j < 16; j++) keyx[j] = enc[j+(0x10*i)];
			setKeyX(i+0x28, keyx);
		}
		
	}
	
	private void loadFromMovable(FileBuffer movesed){
		//keyY of slots 0x30, 0x34, and 0x3A are taken from movable.sed
		if(movesed == null) return;
		
		long off = MOVABLESED_KEY_OFFSET;
		byte[] keyY = movesed.getBytes(off, off+0x10);
		setKeyY(0x30, keyY);
		setKeyY(0x34, keyY);
		setKeyY(0x3A, keyY);
	}
	
	private void loadFixedXKeys(FileBuffer boot9){
		for(int[] map : FIXED_X_MAP){
			int slot = map[0];
			int offset = map[1];
			long off = BOOT9_ROMSEED_OFF_RETAIL + offset;
			byte[] key = new byte[16];
			for(int j = 0; j < 16; j++) key[j] = boot9.getByte(off+j);
			this.setKeyX(slot, key);
		}	
	}
	
	private void loadFixedYKeys(FileBuffer boot9){
		for(int[] map : FIXED_Y_MAP){
			int slot = map[0];
			int offset = map[1];
			long off = BOOT9_ROMSEED_OFF_RETAIL + offset;
			byte[] key = new byte[16];
			for(int j = 0; j < 16; j++) key[j] = boot9.getByte(off+j);
			this.setKeyY(slot, key);
		}
	}
	
	private void loadFixedNormalKeys(FileBuffer boot9){
		for(int[] map : FIXED_NORM_MAP){
			int slot = map[0];
			int offset = map[1];
			long off = BOOT9_ROMSEED_OFF_RETAIL + offset;
			byte[] key = new byte[16];
			for(int j = 0; j < 16; j++) key[j] = boot9.getByte(off+j);
			this.setNormalKey(slot, key);
		}
	}
	
	/*----- Getters -----*/

	public byte[] getKeyX(int slot){
		return keyslots[slot].keyX;
	}
	
	public byte[] getKeyY(int slot){
		return keyslots[slot].keyY;
	}
	
	public byte[] getNormalKey(int slot){
		return keyslots[slot].normal;
	}
	
	public boolean slotXEmpty(int slot){
		if(slot < 0 | slot >= keyslots.length) return true;
		if(keyslots[slot] == null) return true;
		KeySlot s = keyslots[slot];
		if(s.keyX == null) return true;
		
		for(int i = 0; i < s.keyX.length; i++){
			if(s.keyX[i] != 0) return false;
		}
		
		return true;
	}
	
	/*----- Setters -----*/
	
	public void setKeyX(int slot, byte[] keyX){
		keyslots[slot].keyX = keyX;
	}
	
	public void setKeyY(int slot, byte[] keyY){
		keyslots[slot].keyY = keyY;
		if(slot < 4) keyslots[slot].normal = genNormalKey_DSi(keyslots[slot].keyX, keyslots[slot].keyY);
		else keyslots[slot].normal = genNormalKey_3DS(keyslots[slot].keyX, keyslots[slot].keyY);
	}
	
	public void setNormalKey(int slot, byte[] normal){
		keyslots[slot].normal = normal;
	}
	
	/*----- Save -----*/
	
	public boolean saveCitrusCrypt(String path) throws IOException{
		BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(path));
		for(int i = 0; i < 0x40; i++){
			bos.write(getKeyX(i));
			bos.write(getKeyY(i));
			bos.write(getNormalKey(i));
		}
		bos.close();
		
		return true;
	}
	
	/*----- Crypt -----*/
	
	public File decrypt_ctr(FileBuffer data, byte[] iv, int slotidx){
		//TODO
		//Returns path to temp file where data was put
		return null;
	}
	
	public File encrypt_ctr(FileBuffer data, byte[] iv, int slotidx){
		//TODO
		//Returns path to temp file where data was put
		return null;
	}
	
	public FileBuffer decryptOTP(FileBuffer boot9, FileBuffer otp_enc, boolean checkhash) throws UnsupportedFileTypeException{
		
		long off = BOOT9_AES3F_OFF_RETAIL;
		byte[] key = boot9.getBytes(off, off+0x10);
		byte[] iv = boot9.getBytes(off+0x10, off+0x20);
		
		AES aes = new AES(key);
		byte[] otp_dec = aes.decrypt(iv, otp_enc.getBytes());
		
		//Check hash
		if(!checkhash) return FileBuffer.wrap(otp_dec);
		try{
			MessageDigest sha = MessageDigest.getInstance("SHA-256");
			sha.update(otp_dec);
			byte[] hashbuff = sha.digest();
			byte[] ref = new byte[20];
			for(int i = 0; i < 20; i++) ref[i] = otp_dec[i+0xe0];
			if(!MessageDigest.isEqual(ref, hashbuff)) return null;
		}
		catch(Exception x){
			x.printStackTrace();
			throw new FileBuffer.UnsupportedFileTypeException("SHA-256 hash failed");
		}
		
		return FileBuffer.wrap(otp_dec);
	}
	
	/*----- KeyGen -----*/
	
	public byte[] genSaveKeyY_FM7(CitrusNCC ncc){
		//TODO
		return null;
	}
	
	private static byte[] rotateLeft(byte[] in, int amt){
		if(in == null) return null;
		if(amt == 0) return in;
		
		int rbytes = amt >>> 3;
		int rbits = amt % 8;
		int len = in.length;
		
		//Rotate bytes
		int[] out1 = new int[len];
		for(int i = 0; i < len; i++){
			int nidx = i - rbytes;
			while(nidx < 0) nidx += len;
			out1[nidx] = Byte.toUnsignedInt(in[i]);
		}
		
		//Rotate bits
		byte[] out2 = new byte[len];
		int mask = ~(0xFF >>> rbits);
		int rshift = 8 - rbits;
		int mycarry = 0;
		int lastcarry = 0;
		for(int i = len-1; i >= 0; i--){
			int val = out1[i];
			mycarry = (val & mask) >>> rshift;
			val = (val << rbits) & 0xFF;
			val |= lastcarry;
			
			lastcarry = mycarry;
			mycarry = 0;
			
			out2[i] = (byte)val;
		}
		//Copy highest bits from first byte to lowest bits of last byte
		int val = Byte.toUnsignedInt(out2[len-1]);
		val |= lastcarry;
		out2[len-1] = (byte)val;
		
		return out2;
	}
	
	private static byte[] rotateRight(byte[] in, int amt){
		if(in == null) return null;
		if(amt == 0) return in;
		
		int rbytes = amt >>> 3;
		int rbits = amt % 8;
		int len = in.length;
		
		//Rotate bytes
		int[] out1 = new int[len];
		for(int i = 0; i < len; i++){
			int nidx = i + rbytes;
			while(nidx >= len) nidx -= len;
			out1[nidx] = Byte.toUnsignedInt(in[i]);
		}
		
		//Rotate bits
		byte[] out2 = new byte[len];
		int mask = ~(~0 << rbits);
		int shiftleft = 8-rbits;
		int mycarry = 0;
		int lastcarry = 0;
		for(int i = 0; i < len; i++){
			//Save last rbits bits to mycarry
			int val = out1[i];
			mycarry = val & mask;
			mycarry = mycarry << shiftleft;
			
			//Shift right rbits
			val = val >>> rbits;
			
			//OR back in the previous carry
			val |= lastcarry;
			
			//Swap carry values
			lastcarry = mycarry;
			mycarry = 0;
			
			//Save to out array
			out2[i] = (byte)val;
		}
		//Copy carry bits from last byte to first byte.
		int val = Byte.toUnsignedInt(out2[0]);
		val |= lastcarry;
		out2[0] = (byte)val;
		
		return out2;
	}
	
	private static byte[] xorArr(byte[] x, byte[] y){
		if(x == null){
			if(y == null) return null;
			return y;
		}
		if(y == null){return x;}
		int len = x.length;
		if(y.length < len) len = y.length;
		
		byte[] out = new byte[len];
		for(int i = 0; i < len; i++){
			int bx = Byte.toUnsignedInt(x[i]);
			int by = Byte.toUnsignedInt(y[i]);
			int xor = bx ^ by;
			out[i] = (byte)xor;
		}
		
		return out;
	}
	
	private static byte[] addConstToArr(byte[] x, int[] const_val){
		if(x == null) return null;
		if(const_val == null) return x;
		
		int len = x.length;
		boolean carry = false;
		byte[] out = new byte[len];
		for(int i = 0; i < len; i++){
			int idx = len-i-1;
			int b = Byte.toUnsignedInt(x[idx]);
			int c = const_val[idx];
			b += c;
			if(carry) b++;
			if(b > 0xFF) carry = true;
			else carry = false;
			out[idx] = (byte)b;
		}
		
		return out;
	}
	
	public static byte[] genNormalKey_3DS(byte[] keyX, byte[] keyY){
		//NormalKey = (((KeyX ROL 2) XOR KeyY) + C1) ROR 41

		byte[] rolx = rotateLeft(keyX, 2);
		byte[] xor = xorArr(rolx, keyY);
		byte[] add = addConstToArr(xor, C1);
		byte[] norm = rotateRight(add, 41);
		
		return norm;
	}
	
	public static byte[] genNormalKey_DSi(byte[] keyX, byte[] keyY){
		//NormalKey = ((KeyX XOR KeyY) + C2) ROL 42
		byte[] xor = xorArr(keyX, keyY);
		byte[] add = addConstToArr(xor, C2);
		byte[] norm = rotateRight(add, 42);
		
		return norm;
	}
	
	public static byte[] adjustCTR(byte[] base_ctr, long offset){
		long row = offset >>> 4;
		byte[] add = new byte[16];
		byte[] out = new byte[16];
		
		long shamt = 56;
		for(int i = 0; i < 8; i++){
			add[i+8] = (byte)((row >>> shamt) & 0xFF);
			shamt -= 8;
		}
		
		boolean carry = false;
		for(int i = 15; i >= 0; i--){
			int sum = Byte.toUnsignedInt(add[i]) + Byte.toUnsignedInt(base_ctr[i]);
			if(carry) sum++;
			if(sum > 0xFF) carry = true;
			else carry = false;
			out[i] = (byte)sum;
		}
		
		return out;
	}
	
	/*----- Debug -----*/
	
	public void printToStdErr(){
		
		for(int i = 0; i < 0x40; i++){
			System.err.println("--- Keyslot 0x" + String.format("%02x", i));
			System.err.print("KeyX: ");
			byte[] k = getKeyX(i);
			for(int j = 0; j < 16; j++) System.err.print(String.format("%02x ", k[j]));
			System.err.println();
			
			System.err.print("KeyY: ");
			k = getKeyY(i);
			for(int j = 0; j < 16; j++) System.err.print(String.format("%02x ", k[j]));
			System.err.println();
			
			System.err.print("Normal: ");
			k = getNormalKey(i);
			for(int j = 0; j < 16; j++) System.err.print(String.format("%02x ", k[j]));
			System.err.println();
			System.err.println();
		}
		
	}
	
	public static void main(String[] args){
		
		try{
			String inpath = args[0];
			CitrusCrypt crypt = loadCitrusCrypt(FileBuffer.createBuffer(inpath));
			
			for(int i = 0; i < crypt.keyslots.length; i++){
				String fmtstr = String.format("slot0x%02xKey", i);
				System.out.println(fmtstr + "X=" + FileUtils.bytes2str(crypt.keyslots[i].keyX).toUpperCase());
				System.out.println(fmtstr + "Y=" + FileUtils.bytes2str(crypt.keyslots[i].keyY).toUpperCase());
				System.out.println(fmtstr + "N=" + FileUtils.bytes2str(crypt.keyslots[i].normal).toUpperCase());
			}
		}
		catch(Exception ex){
			ex.printStackTrace();
			System.exit(1);
		}
		
	}
	
}
