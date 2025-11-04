package waffleoRai_Files.maxis.res;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;

import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.FileBuffer;

public class XMLInteriorFile {
	
	private static final String CHARSET_LE = "UnicodeLittleUnmarked";

	public char[] contents;
	
	protected boolean readBinary_internal(BufferReference dat, int charCount) {
		if(dat == null) return false;
		
		contents = new char[charCount];
		int bytesToRead = charCount << 1;
		ByteBuffer textdat = ByteBuffer.allocate(bytesToRead);
		for(int i = 0; i < bytesToRead; i++){
			textdat.put(dat.nextByte());
		}
		textdat.rewind();
		Charset mySet = Charset.forName(CHARSET_LE);
  		CharBuffer cb = mySet.decode(textdat);
  		cb.rewind();
  		cb.get(contents);
  		
		return true;
	}
	
	public static XMLInteriorFile readBinary(BufferReference dat, int charCount) {
		if(dat == null) return null;
		XMLInteriorFile str = new XMLInteriorFile();
		if(!str.readBinary_internal(dat, charCount)) return null;
		return str;
	}
	
	public static XMLInteriorFile importFile(String path) throws IOException {
		int cCount = 0;
		FileInputStream fis = new FileInputStream(path); 
		LinkedList<String> buffer = new LinkedList<String>();
		BufferedReader br = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8));
		String line = null;
		while((line = br.readLine()) != null) {
			buffer.add(line);
			cCount += line.length() + 1; //1 for newline
		}
		br.close();

		XMLInteriorFile xmlf = new XMLInteriorFile();
		xmlf.contents = new char[cCount];
		int i = 0;
		while(!buffer.isEmpty()) {
			line = buffer.pop();
			int strlen = line.length();
			for(int j = 0; j < strlen; j++) {
				xmlf.contents[i++] = line.charAt(j);
			}
			xmlf.contents[i++] = '\n';
		}
		
		return xmlf;
	}
	
	/*----- Write -----*/
	
	public int getBinarySize() {
		if(contents == null) return 0;
		return contents.length << 1;
	}
	
	public int getCharCount() {
		if(contents == null) return 0;
		return contents.length;
	}
	
	public FileBuffer writeBinary(boolean byteOrder) {
		FileBuffer buff = new FileBuffer(getBinarySize(), byteOrder);
		writeBinaryTo(buff);
		return buff;
	}
	
	public int writeBinaryTo(FileBuffer target) {
		if(target == null) return 0;
		long stPos = target.getFileSize();
		
		CharBuffer cb = CharBuffer.wrap(contents);
		cb.rewind();
		
		Charset mySet = Charset.forName(CHARSET_LE);
  		ByteBuffer bb = mySet.encode(cb);
  		bb.rewind();
  		
  		while(bb.hasRemaining()) target.addToFile(bb.get());

		return (int)(target.getFileSize() - stPos);
	}
	
	public boolean exportFile(String path) throws IOException {
		if(contents == null) return false;
		FileOutputStream fos = new FileOutputStream(path);
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos, StandardCharsets.UTF_8));
		bw.write(contents);
		bw.close();
		return true;
	}

}
