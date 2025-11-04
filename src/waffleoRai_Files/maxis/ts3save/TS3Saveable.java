package waffleoRai_Files.maxis.ts3save;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;

import org.w3c.dom.Element;

import waffleoRai_Containers.maxis.MaxisPropertyStream;
import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.FileBuffer;

public abstract class TS3Saveable{
	
	protected String xmlNodeName = null;
	protected int baseSize = 0;

	/*----- Read -----*/
	protected abstract boolean readBinary_internal(BufferReference dat);
	protected abstract boolean readXMLNode_internal(Element xml_element);
	
	protected boolean readPropertyStream_internal(BufferReference dat, boolean byteOrder, int verFieldSize) {
		if(dat == null) return false;
		MaxisPropertyStream stream = MaxisPropertyStream.openForRead(dat, byteOrder, verFieldSize);
		return readPropertyStream_internal(stream);
	}
	
	protected abstract boolean readPropertyStream_internal(MaxisPropertyStream stream);
	
	/*----- Write -----*/
	
	public int getBinarySize() {return baseSize;}
	public abstract int writeBinaryTo(FileBuffer target);
	public abstract void writeXMLNode(Writer out, String indent) throws IOException;
	
	public FileBuffer writeBinary(boolean byteOrder) {
		FileBuffer buff = new FileBuffer(getBinarySize(), byteOrder);
		writeBinaryTo(buff);
		return buff;
	}
	
	public MaxisPropertyStream toPropertyStream(boolean byte_order) {
		return toPropertyStream(byte_order, 2);
	}
	
	public abstract void addToPropertyStream(MaxisPropertyStream ps);
	
	public long writeAsPropertyStream(OutputStream target, boolean byte_order) throws IOException {
		if(target == null) return 0L;
		MaxisPropertyStream ps = toPropertyStream(byte_order);
		return ps.writeTo(target);
	}
	
	public long writeAsPropertyStream(FileBuffer target) {
		if(target == null) return 0L;
		MaxisPropertyStream ps = toPropertyStream(target.isBigEndian());
		return ps.serializeTo(target);
	}
	
	public MaxisPropertyStream toPropertyStream(boolean byte_order, int verFieldSize) {
		MaxisPropertyStream ps = MaxisPropertyStream.openForWrite(byte_order, verFieldSize);
		addToPropertyStream(ps);
		return ps;
	}

	
}
