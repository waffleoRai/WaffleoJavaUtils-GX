package waffleoRai_Encryption.nintendo;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.StreamWrapper;

//Taken from nocash's GBATEK

public class DSModcrypt {

	public static final int[] COMBINE_ADDEND = {0xFF, 0xFE, 0xFB, 0x4E,
												0x29, 0x59, 0x02, 0x58,
												0x2A, 0x68, 0x0F, 0x5F,
												0x1A, 0x4F, 0x3E, 0x79};
	
	private static int[] pow_tbl;
	private static int[] log_tbl;
	private static int[] sbox_f;
	private static int[] sbox_r;
	private static int[] tbl_f;
	private static int[] tbl_r;
	
	private int[] RK;
	private int nr;
	
	private static int rol8(int val, int amt){
		
		val &= 0xFF;
		int initval = val;
		int mask = ((~0) << amt) & 0xFF;
		val = (val << amt) & 0xFF;
		int bits = initval & mask;
		val |= (bits >>> (8-amt));
		
		return val;
	}
	
	private static int rol32(int val, int amt){
		
		int initval = val;
		int mask = (~0) << amt;
		val = val << amt;
		int bits = initval & mask;
		val |= (bits >>> (32-amt));
		
		return val;
	}
	
	private static int ror32(int val, int amt){
		
		int initval = val;
		val = val >>> amt;
		val &= ~((~0) << (32-amt)); //Mask out any higher remaining bits
		val |= (initval << (32-amt));
		
		return val;
	}
	
	private static int scatter8(int[] table, int a, int b, int c, int d){

		int idx = a & 0xFF;
		int val = table[idx] & 0xFF;
		
		idx = (b >>> 8) & 0xFF;
		val |= (table[idx] & 0xFF) << 8;
		
		idx = (c >>> 16) & 0xFF;
		val |= (table[idx] & 0xFF) << 16;
		
		idx = (d >>> 24) & 0xFF;
		val |= (table[idx] & 0xFF) << 24;
		
		return val;
	}
	
	private static int scatter32(int[] table, int a, int b, int c, int d){

		int idx = a & 0xFF;
		int val = ror32(table[idx], 24);
		
		idx = (b >>> 8) & 0xFF;
		val ^= ror32(table[idx], 16);
		
		idx = (c >>> 16) & 0xFF;
		val ^= ror32(table[idx], 8);
		
		idx = (d >>> 24) & 0xFF;
		val ^= table[idx];
		
		return val;
	}
	
	private static void aes_generate_tables(){
		pow_tbl = new int[256];
		log_tbl = new int[256];
		
		int x = 1;
		for(int i = 0; i < 256; i++){
			if(i != 0){
				x = x ^ (x << 1);
			}
			if(x > 255) x = x ^ 0x11B;
			pow_tbl[i] = x;
			log_tbl[x] = i;
		}
		
		sbox_f = new int[256];
		sbox_r = new int[256];
		for(int i = 0; i < 256; i++){
			x = pow_tbl[255 - log_tbl[i]];
			if(i == 0) x = 0x63;
			else{
				x = x ^ rol8(x,1) ^ rol8(x,2) ^ rol8(x,3) ^ rol8(x,4) ^ 0x63;
			}
			sbox_f[i] = x;
			sbox_r[x] = i;
		}
		
		tbl_f = new int[256];
		tbl_r = new int[256];
		for(int i = 0; i < 256; i++){
			x = sbox_f[i] << 1;
			if(x > 255) x = x ^ 0x11B;
			tbl_f[i] = (sbox_f[i] * 0x00010101) ^ (x * 0x01000001);
			
			int w = 0;
			x = sbox_r[i];
			if(x != 0){
				w += pow_tbl[(log_tbl[x] + log_tbl[0xE]) % 0xFF] << 24;
				w += pow_tbl[(log_tbl[x] + log_tbl[0x9]) % 0xFF] << 16;
				w += pow_tbl[(log_tbl[x] + log_tbl[0xD]) % 0xFF] << 8;
				w += pow_tbl[(log_tbl[x] + log_tbl[0xB]) % 0xFF];
			}
			tbl_r[i] = w;
		}
	}
	
	private static byte[] addValueLE(byte[] addend, int imm){

		byte[] copy = new byte[addend.length];
		for(int i = 0; i < addend.length; i++)copy[i] = addend[i];
		boolean carry = false;
		int temp = Byte.toUnsignedInt(addend[0]);
		temp += imm;
		int b = temp & 0xFF;
		copy[0] = (byte)b;
		
		if(b != temp){
			//Carry
			carry = true;
			int idx = 1;
			while(carry && (idx < addend.length)){
				temp = Byte.toUnsignedInt(addend[idx]);
				temp += imm;
				b = temp & 0xFF;
				copy[idx] = (byte)b;
				idx++;
				carry = (temp != b);
			}
		}
		
		
		return copy;
	}
	
	public static void generateAndPrintTables(){
		//Debug
		
		aes_generate_tables();
		
		System.err.println("POW: ");
		for(int i = 0; i < 16; i++){
			System.err.print(String.format("%02x ", pow_tbl[i]));
		}
		System.err.print("...");
		System.err.println();
		for(int i = 240; i < 256; i++){
			System.err.print(String.format("%02x ", pow_tbl[i]));
		}
		System.err.println();
		
		System.err.println("LOG: ");
		for(int i = 0; i < 16; i++){
			System.err.print(String.format("%02x ", log_tbl[i]));
		}
		System.err.print("...");
		System.err.println();
		for(int i = 240; i < 256; i++){
			System.err.print(String.format("%02x ", log_tbl[i]));
		}
		System.err.println();
		
		System.err.println("SBOX (F): ");
		for(int i = 0; i < 16; i++){
			System.err.print(String.format("%02x ", sbox_f[i]));
		}
		System.err.print("...");
		System.err.println();
		for(int i = 240; i < 256; i++){
			System.err.print(String.format("%02x ", sbox_f[i]));
		}
		System.err.println();
		
		System.err.println("SBOX (R): ");
		for(int i = 0; i < 16; i++){
			System.err.print(String.format("%02x ", sbox_r[i]));
		}
		System.err.print("...");
		System.err.println();
		for(int i = 240; i < 256; i++){
			System.err.print(String.format("%02x ", sbox_r[i]));
		}
		System.err.println();
		
		System.err.println("TABLE (F): ");
		for(int i = 0; i < 16; i++){
			System.err.print(String.format("%08x ", tbl_f[i]));
		}
		System.err.print("...");
		System.err.println();
		for(int i = 240; i < 256; i++){
			System.err.print(String.format("%08x ", tbl_f[i]));
		}
		System.err.println();
		
		System.err.println("TABLE (R): ");
		for(int i = 0; i < 16; i++){
			System.err.print(String.format("%08x ", tbl_r[i]));
		}
		System.err.print("...");
		System.err.println();
		for(int i = 240; i < 256; i++){
			System.err.print(String.format("%08x ", tbl_r[i]));
		}
		System.err.println();
		
	}
	
	public DSModcrypt(){
		if(tbl_f == null) aes_generate_tables();
	}
	
	public boolean aes_setkey(boolean enc, byte[] key){

		if(key == null) return false;
		int keysize = key.length << 3;
		if(!(keysize == 128 || keysize == 192 || keysize == 256)) return false;
		//System.err.println("keysize = " + keysize);
		
		RK = new int[64]; //??
		int rc = 1;
		int j = 0;
		int jj = keysize >>> 5; //keysize/32
		nr = jj + 6;
		int led = ((nr+1)<<2);
		int w = 0;
		for(int i = 0; i < led; i++){
			if(i < jj){
				w = 0;
				int kidx = (jj-1-i) << 2;
				for(int n = 3; n >= 0; n--){
					w = w << 8;
					w |= Byte.toUnsignedInt(key[kidx+n]);
				}
			}
			else w = w ^ RK[(i-jj)^3];
			RK[i^3] = w; j++;
			if(j == jj){
				w = scatter8(sbox_f, w, w, w, w);
				w = rol32(w, 8) ^ (rc << 24);
				j = 0;
				rc = rc << 1;
				if(rc > 255) rc = rc ^ 0x11B;
			}
			if(j == 4 && jj == 8) w = scatter8(sbox_f, w, w, w, w);
		}
		
		if(!enc){
			led = nr >>> 1;
			for(int i = 0; i < led; i++){
				for(j = 0; j < 4; j++){
					int idx1 = (i << 2) + j;
					int idx2 = (nr << 2) - (i << 2) + j;
					//System.err.println("Switch pair: " + idx1 + ", " + idx2);
					w = RK[idx1];
					int v = RK[idx2];
					RK[idx1] = v;
					RK[idx2] = w;
				}
			}
			led = nr << 2;
			for(int i = 4; i < led; i++){
				w = RK[i];
				w = scatter8(sbox_f, w, w, w, w);
				RK[i] = scatter32(tbl_r, w, w, w, w);
			}
		}
		
		return true;
	}
	
	public int[] getRK(){
		//Debug
		return RK;
	}

	public byte[] aes_crypt_block(boolean enc, byte[] input){

		//for(int j = 3; j >= 0; j--){
		//for(int j = 0; j < 4; j++){
		
		int[] words = new int[4];
		for(int i = 0; i < 4; i++){
			for(int j = 3; j >= 0; j--){
				int bidx = (i << 2) + j;
				words[i] = words[i] << 8;
				if(bidx < input.length) words[i] |= Byte.toUnsignedInt(input[bidx]);
			}
		}
		
		/*System.err.println("Block Words: ");
		for(int i = 0; i < 4; i++){
			System.err.print(String.format("%08x ", words[i]));
		}
		System.err.println();*/
		
		int[] Y = new int[4];
		for(int i = 0; i < 4; i++){
			Y[i] = RK[i] ^ words[i];
		}
		
		//System.err.println("nr = " + nr);
		int[] outwords = new int[4];
		int[] X = new int[4];
		if(enc){
			for(int i = 1; i < nr; i++){
				int idx = i<<2;
				X[0] = RK[idx + 0] ^ scatter32(tbl_f, Y[1], Y[2], Y[3], Y[0]);
				X[1] = RK[idx + 1] ^ scatter32(tbl_f, Y[2], Y[3], Y[0], Y[1]);
				X[2] = RK[idx + 2] ^ scatter32(tbl_f, Y[3], Y[0], Y[1], Y[2]);
				X[3] = RK[idx + 3] ^ scatter32(tbl_f, Y[0], Y[1], Y[2], Y[3]);
				Y = X;
				X = new int[4];
			}
			
			int idx = nr<<2;
			outwords[0] = RK[idx + 0] ^ scatter8(sbox_f, Y[1], Y[2], Y[3], Y[0]);
			outwords[1] = RK[idx + 1] ^ scatter8(sbox_f, Y[2], Y[3], Y[0], Y[1]);
			outwords[2] = RK[idx + 2] ^ scatter8(sbox_f, Y[3], Y[0], Y[1], Y[2]);
			outwords[3] = RK[idx + 3] ^ scatter8(sbox_f, Y[0], Y[1], Y[2], Y[3]);
		}
		else{
			for(int i = 1; i < nr; i++){
				int idx = i<<2;
				X[0] = RK[idx + 0] ^ scatter32(tbl_r, Y[3], Y[2], Y[1], Y[0]);
				X[1] = RK[idx + 1] ^ scatter32(tbl_r, Y[0], Y[3], Y[2], Y[1]);
				X[2] = RK[idx + 2] ^ scatter32(tbl_r, Y[1], Y[0], Y[3], Y[2]);
				X[3] = RK[idx + 3] ^ scatter32(tbl_r, Y[2], Y[1], Y[0], Y[3]);
				Y = X;
				X = new int[4];
			}
			
			int idx = nr<<2;
			outwords[0] = RK[idx + 0] ^ scatter8(sbox_r, Y[3], Y[2], Y[1], Y[0]);
			outwords[1] = RK[idx + 1] ^ scatter8(sbox_r, Y[0], Y[3], Y[2], Y[1]);
			outwords[2] = RK[idx + 2] ^ scatter8(sbox_r, Y[1], Y[0], Y[3], Y[2]);
			outwords[3] = RK[idx + 3] ^ scatter8(sbox_r, Y[2], Y[1], Y[0], Y[3]);
		}
		
		//Outwords back to byte[]
		FileBuffer outfb = new FileBuffer(16, false);
		for(int i = 0; i < 4; i++) outfb.addToFile(outwords[i]);
		
		/*System.err.println("Block Words: ");
		for(int i = 0; i < 4; i++){
			System.err.print(String.format("%08x ", outwords[i]));
		}
		System.err.println();*/
		
		return outfb.getBytes();
	}
	
	public static boolean aes_crypt_ctr(InputStream input, OutputStream output, byte[] key, byte[] iv, int len) throws IOException{

		if(iv == null || key == null || input == null) return false;
		
		DSModcrypt mc = new DSModcrypt();
		mc.aes_setkey(true, key);
		byte[] ctr = new byte[16];
		for(int i = 0; i < 16; i++){
			if(i < iv.length) ctr[i] = iv[i];
		}
		/*System.err.println("RK: ");
		for(int i = 0; i < 44; i++){
			System.err.print(String.format("%08x ", mc.RK[i]));
			if(i%8 == 7) System.err.println();
		}
		System.err.println();*/

		/*System.err.println("Init CTR: ");
		for(int i = 0; i < 16; i++){
			System.err.print(String.format("%02x ", ctr[i]));
		}
		System.err.println();*/
		
		int n = 0;
		byte[] tmp = null;
		int remain = len;
		while(remain > 0){
			if(n == 0){
				tmp = mc.aes_crypt_block(true, ctr);
				ctr = addValueLE(ctr, 1);
				/*System.err.println("New CTR: ");
				for(int i = 0; i < 16; i++){
					System.err.print(String.format("%02x ", tmp[i]));
				}
				System.err.println();*/
				//System.exit(2);
			}
			int b = (input.read() ^ Byte.toUnsignedInt(tmp[n])) & 0xFF; remain--;
			output.write(b);
			n = (n+1) & 0xF;
			
		}

		return true;
	}
	
	public static boolean aes_crypt_ctr(StreamWrapper input, OutputStream output, byte[] key, byte[] iv) throws IOException{

		if(iv == null || key == null || input == null) return false;
		
		DSModcrypt mc = new DSModcrypt();
		mc.aes_setkey(true, key);
		byte[] ctr = new byte[16];
		for(int i = 0; i < 16; i++){
			if(i < iv.length) ctr[i] = iv[i];
		}
		
		int n = 0;
		byte[] tmp = null;
		while(!input.isEmpty()){
			if(n == 0){
				tmp = mc.aes_crypt_block(true, ctr);
				ctr = addValueLE(ctr, 1);
				/*System.err.println("New CTR: ");
				for(int i = 0; i < 16; i++){
					System.err.print(String.format("%02x ", tmp[i]));
				}
				System.err.println();*/
				//System.exit(2);
			}
			int b = (input.getFull() ^ Byte.toUnsignedInt(tmp[n])) & 0xFF;
			output.write(b);
			n = (n+1) & 0xF;
			
		}

		return true;
	}

	public static byte[] combine_pair(byte[] keyX, byte[] keyY){
		byte[] longkey = new byte[keyX.length + keyY.length];
		
		for(int i = 0; i < keyX.length; i++) longkey[i] = keyX[i];
		for(int i = 0; i < keyY.length; i++) longkey[i+keyX.length] = keyY[i];
		
		return longkey;
	}
	
	public static byte[] normal128Key_from_128pair(byte[] keyX, byte[] keyY){
		//TODO
		//for(int j = 3; j >= 0; j--)
		//for(int j = 0; j < 4; j++)
		
		System.err.println("Key X: ");
		for(int i = 0; i < 16; i++){
			System.err.print(String.format("%02x ", keyX[i]));
		}
		System.err.println();
		System.err.println("Key Y: ");
		for(int i = 0; i < 16; i++){
			System.err.print(String.format("%02x ", keyY[i]));
		}
		System.err.println();
		
		//Turn into words...
		long[] wordsX = new long[4];
		long[] wordsY = new long[4];
			
		for(int i = 0; i < 4; i++){
			for(int j = 0; j < 4; j++){
				
				wordsX[i] = wordsX[i] << 8;
				wordsY[i] = wordsY[i] << 8;
				
				wordsX[i] |= Byte.toUnsignedLong(keyX[(i<<2) + j]);
				wordsY[i] |= Byte.toUnsignedLong(keyY[(i<<2) + j]);
			}
		}
		
		System.err.println("Key X (Words): ");
		for(int i = 0; i < 4; i++){
			System.err.print(String.format("%08x ", wordsX[i]));
		}
		System.err.println();
		System.err.println("Key Y (Words): ");
		for(int i = 0; i < 4; i++){
			System.err.print(String.format("%08x ", wordsY[i]));
		}
		System.err.println();
		
		//Xor them
		long[] xored = new long[4];
		for(int i = 0; i < 4; i++){
			xored[i] = wordsX[i] ^ wordsY[i];
		}
		
		System.err.println("Xor'd: ");
		for(int i = 0; i < 4; i++){
			System.err.print(String.format("%08x ", xored[i]));
		}
		System.err.println();
		
		//Add to ADDEND (Check endian sensitivity)
		long[] addwords = new long[4];
		for(int i = 0; i < 4; i++){
			for(int j = 0; j < 4; j++){
				
				addwords[i] = addwords[i] << 8;
				addwords[i] |= Integer.toUnsignedLong(COMBINE_ADDEND[(i<<2) + j]);
			}
		}
		System.err.println("Addwords: ");
		for(int i = 0; i < 4; i++){
			System.err.print(String.format("%08x ", addwords[i]));
		}
		System.err.println();
		
		boolean carry = false;
		long[] sum = new long[4];
		
		for(int i = 3; i >= 0; i--){
			long sum0 = addwords[i] + xored[i];
			if(carry) sum0++;
			long sum1 = sum0;
			sum0 &= 0xFFFFFFFFL;
			carry = (sum1 != sum0);

			sum[i] = sum0;	
		}
		System.err.println("Sum: ");
		for(int i = 0; i < 4; i++){
			System.err.print(String.format("%08x ", sum[i]));
		}
		System.err.println();
		
		//ROL 42 (Check endian sensitivity)
		//Rotate left 5 bytes and 2 bits
		long v1 = 0;
		long v2 = 0;
		for(int i = 3; i >= 0; i--){
			v2 = (sum[i] >>> 30) & 0x3;
			sum[i] = (sum[i] << 2) & 0xFFFFFFFFL;
			sum[i] |= v1;
			v1 = v2;
		}
		sum[3] |= v1;
		System.err.println("rol 2: ");
		for(int i = 0; i < 4; i++){
			System.err.print(String.format("%08x ", sum[i]));
		}
		System.err.println();
		
		byte[] barr = new byte[16];
		for(int i = 0; i < 4; i++){
			int idx = i << 2;
			idx -= 5;
			if(idx < 0) idx += 16;
			barr[idx++] = (byte)((sum[i] >>> 24) & 0xFF);
			if(idx >= 16) idx -= 16;
			barr[idx++] = (byte)((sum[i] >>> 16) & 0xFF);
			if(idx >= 16) idx -= 16;
			barr[idx++] = (byte)((sum[i] >>> 8) & 0xFF);
			if(idx >= 16) idx -= 16;
			barr[idx++] = (byte)(sum[i] & 0xFF);
		}
		
		System.err.println("Output: ");
		for(int i = 0; i < 16; i++){
			System.err.print(String.format("%02x ", barr[i]));
		}
		System.err.println();
		
		return barr;
	}
	
}
