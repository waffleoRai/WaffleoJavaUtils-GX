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
		int bytesToRead = strlen << 1;
		ByteBuffer strdat = ByteBuffer.allocate(bytesToRead);
		for(int i = 0; i < bytesToRead; i++){
			strdat.put(input.nextByte());
		}
		//strdat.put((byte)0); strdat.put((byte)0);
		strdat.rewind();
		String charset_name = charByteOrder?CHARSET_BE:CHARSET_LE;
		Charset mySet = Charset.forName(charset_name);
  		CharBuffer cb = mySet.decode(strdat);
		return cb.toString();
	}
	
	public static int serializeMaxisStringTo(String input, FileBuffer target) {
		long stpos = target.getFileSize();
		int strlen = input.length();
		target.addToFile(strlen);
		
		String charset_name = target.isBigEndian()?CHARSET_BE:CHARSET_LE;
		Charset mySet = Charset.forName(charset_name);
  		ByteBuffer bb = mySet.encode(input);
  		bb.rewind();
  		
  		while(bb.hasRemaining()) target.addToFile(bb.get());
		
		return (int)(target.getFileSize() - stpos);
	}
	
	public static FileBuffer serializeMaxisString(String input, boolean byteOrder){
		int strlen = input.length();
		String charset_name = charByteOrder?CHARSET_BE:CHARSET_LE;
		Charset mySet = Charset.forName(charset_name);
  		ByteBuffer bb = mySet.encode(input);
  		bb.rewind();
  		
  		FileBuffer output = new FileBuffer(bb.remaining() + 4, byteOrder);
  		output.addToFile(strlen);
  		while(bb.hasRemaining()) output.addToFile(bb.get());
		
		return output;
	}
	
	public static String readMaxis7String(BufferReference input){
		int strlen = Byte.toUnsignedInt(input.nextByte());
		//int bytesToRead = strlen << 1;
		ByteBuffer strdat = ByteBuffer.allocate(strlen);
		for(int i = 0; i < strlen; i++){
			strdat.put(input.nextByte());
		}
		//strdat.put((byte)0); strdat.put((byte)0);
		strdat.rewind();
		String charset_name = CHARSET_BE;
		Charset mySet = Charset.forName(charset_name);
  		CharBuffer cb = mySet.decode(strdat);
		return cb.toString();
	}
	
	public static int serializeMaxis7StringTo(String input, FileBuffer target) {
		long stpos = target.getFileSize();
		byte strlen = (byte)(input.length() << 1);
		target.addToFile(strlen);
		
		String charset_name = CHARSET_BE;
		Charset mySet = Charset.forName(charset_name);
  		ByteBuffer bb = mySet.encode(input);
  		bb.rewind();
  		
  		while(bb.hasRemaining()) target.addToFile(bb.get());
		
		return (int)(target.getFileSize() - stpos);
	}
	
	public static FileBuffer serializeMaxis7String(String input, boolean byteOrder){
		byte strlen = (byte)input.length();
		String charset_name = CHARSET_BE;
		Charset mySet = Charset.forName(charset_name);
  		ByteBuffer bb = mySet.encode(input);
  		bb.rewind();
  		
  		FileBuffer output = new FileBuffer(bb.remaining() + 4, byteOrder);
  		output.addToFile(strlen);
  		while(bb.hasRemaining()) output.addToFile(bb.get());
		
		return output;
	}

	public static boolean readBinaryBool(BufferReference ref) {
		return (ref.nextByte() != 0);
	}
	
	public static int writeBinaryBool(FileBuffer target, boolean value) {
		if(target == null) return 0;
		byte bb = value?(byte)1:(byte)0;
		target.addToFile(bb);
		return 1;
	}
	
}
