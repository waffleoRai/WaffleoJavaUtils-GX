package waffleoRai_Containers.maxis;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;

import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.FileBuffer;

public class MaxisTypes {
	
	private static final String CHARSET_LE = "UnicodeLittleUnmarked";
	private static final String CHARSET_BE = "UnicodeBigUnmarked";
	
	private static boolean charByteOrder = false;
	
	public static void setUTFByteOrder(boolean val){
		charByteOrder = val;
	}
	
	public static String readMaxisString(BufferReference input){
		int strlen = input.nextInt();
		ByteBuffer strdat = ByteBuffer.allocate((strlen+1) << 1);
		for(int i = 0; i < (strlen << 1); i++){
			strdat.put(input.nextByte());
		}
		strdat.rewind();
		String charset_name = charByteOrder?CHARSET_BE:CHARSET_LE;
		Charset mySet = Charset.forName(charset_name);
  		CharBuffer cb = mySet.decode(strdat);
		return cb.toString();
	}
	
	public static FileBuffer serializeMaxisString(String input, boolean byteOrder){
		int strlen = input.length();
		String charset_name = charByteOrder?CHARSET_BE:CHARSET_LE;
		Charset mySet = Charset.forName(charset_name);
  		ByteBuffer bb = mySet.encode(input);
  		
  		FileBuffer output = new FileBuffer(bb.remaining() + 4, byteOrder);
  		output.addToFile(strlen);
  		while(bb.hasRemaining()) output.addToFile(bb.get());
		
		return output;
	}

}
