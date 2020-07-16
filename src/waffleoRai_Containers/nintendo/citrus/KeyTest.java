package waffleoRai_Containers.nintendo.citrus;

import java.math.BigInteger;

public class KeyTest {
	
	public static void main(String[] args) {
	
		String keyx_str = "";
		String keyy_str = "";
		String addend_str = "1FF9E9AAC5FE0408024591DC5D52768A";
		String mask128_str = "ffffffffffffffffffffffffffffffff";
		
		BigInteger keyx = new BigInteger(keyx_str, 16);
		BigInteger keyy = new BigInteger(keyy_str, 16);
		BigInteger addend = new BigInteger(addend_str, 16);
		BigInteger mask128 = new BigInteger(mask128_str, 16);
		
		System.err.println("Key X: " + keyx.toString(16));
		System.err.println("Key Y: " + keyy.toString(16));
		System.err.println("Addend: " + addend.toString(16));
		
		BigInteger hi = keyx.and(new BigInteger("c0000000000000000000000000000000", 16)).shiftRight(126);
		BigInteger shl = keyx.shiftLeft(2).and(mask128);
		BigInteger rol = shl.or(hi);
		
		System.err.println("hi: " + hi.toString(16));
		System.err.println("shl: " + shl.toString(16));
		System.err.println("rol: " + rol.toString(16));
		
		BigInteger xor = rol.xor(keyy);
		System.err.println("xor: " + xor.toString(16));
		
		BigInteger add = xor.add(addend).and(mask128);
		System.err.println("add: " + add.toString(16));
		
		String rormask_str = "1ffffffffff";
		BigInteger rormask = new BigInteger(rormask_str, 16);
		BigInteger lo = add.and(rormask);
		BigInteger shr = add.shiftRight(41);
		BigInteger out = shr.or(lo.shiftLeft(87));
		
		System.err.println("lo: " + lo.toString(16));
		System.err.println("shr: " + shr.toString(16));
		System.err.println("out: " + out.toString(16));
		
	}

}
