package waffleoRai_SeqSound.n64al.cmd;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import waffleoRai_SeqSound.n64al.NUSALSeqCommandBook;

public enum SysCommandBook {
	
	ZELDA64("SeqCmd_Z64");
	
	private String filename;
	
	private SysCommandBook(String fname) {
		filename = fname;
	}
	
	public String getTableFileName() {return filename;}
	
	private static Map<SysCommandBook, NUSALSeqCommandBook> staticMap;
	
	public NUSALSeqCommandBook loadBook() throws IOException {
		InputStream str = SysCommandBook.class.getResourceAsStream("/waffleoRai_SeqSound/n64al/cmd/" + filename + ".tsv");
		if(str == null) return null;
		BufferedReader br = new BufferedReader(new InputStreamReader(str));
		NUSALSeqCommandBook book = NUSALSeqCommandBook.readTSV(br);
		br.close();
		str.close();
		
		if(staticMap == null) staticMap = new HashMap<SysCommandBook, NUSALSeqCommandBook>();
		staticMap.put(this, book);
		
		return book;
	}
	
	public NUSALSeqCommandBook getBook() {
		if(staticMap == null) staticMap = new HashMap<SysCommandBook, NUSALSeqCommandBook>();
		NUSALSeqCommandBook book = staticMap.get(this);
		if(book == null) {
			try {
				book = loadBook();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return book;
	}
	
	public static NUSALSeqCommandBook getDefaultBook() {
		return ZELDA64.getBook();
	}

}
