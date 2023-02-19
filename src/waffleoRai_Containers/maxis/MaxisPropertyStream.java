package waffleoRai_Containers.maxis;

import java.io.OutputStream;
import java.util.Map;
import java.util.TreeMap;

import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.FileBuffer;

public class MaxisPropertyStream {
	
	/*----- Constants -----*/
	
	/*----- Instance Variables -----*/
	
	private boolean readOnly = false;
	
	private boolean byteOrder = false;
	private int versionFieldSize = 2;
	
	private int version = 2;
	private FileBuffer data;
	private Map<Integer, Integer> propertyMap;
	
	/*----- Init -----*/
	
	private MaxisPropertyStream(){
		propertyMap = new TreeMap<Integer, Integer>();
	}
	
	/*----- Getters -----*/
	
	public boolean isReadOnly(){return readOnly;}
	public boolean getByteOrder(){return byteOrder;}
	public int getVersionFieldSize(){return versionFieldSize;}
	public int getVersion(){return version;}
	
	public boolean hasField(int key){
		return propertyMap.containsKey(key);
	}
	
	public byte getFieldAsByte(int key){
		Integer offset = propertyMap.get(key);
		if(offset == null) return -1;
		return data.getByte(offset);
	}
	
	public short getFieldAsShort(int key){
		Integer offset = propertyMap.get(key);
		if(offset == null) return -1;
		return data.shortFromFile(offset);
	}
	
	public int getFieldAsShortish(int key){
		Integer offset = propertyMap.get(key);
		if(offset == null) return -1;
		return data.shortishFromFile(offset);
	}
	
	public int getFieldAsInt(int key){
		Integer offset = propertyMap.get(key);
		if(offset == null) return -1;
		return data.intFromFile(offset);
	}
	
	public long getFieldAsLong(int key){
		Integer offset = propertyMap.get(key);
		if(offset == null) return -1;
		return data.longFromFile(offset);
	}
	
	public float getFieldAsFloat(int key){
		Integer offset = propertyMap.get(key);
		if(offset == null) return -1;
		int ival = data.intFromFile(offset);
		return Float.intBitsToFloat(ival);
	}
	
	public double getFieldAsDouble(int key){
		Integer offset = propertyMap.get(key);
		if(offset == null) return -1;
		long ival = data.longFromFile(offset);
		return Double.longBitsToDouble(ival);
	}
	
	public String getFieldAsString(int key){
		Integer offset = propertyMap.get(key);
		if(offset == null) return null;
		BufferReference strref = data.getReferenceAt(offset);
		return MaxisTypes.readMaxisString(strref);
	}
	
	public MaxisPropertyStream getChildStream(int key){
		Integer offset = propertyMap.get(key);
		if(offset == null) return null;
		BufferReference ref = data.getReferenceAt(offset);
		return openForRead(ref, byteOrder, versionFieldSize);
	}
	
	/*----- Setters -----*/
	
	public void setByteOrder(boolean val){
		if(readOnly) return;
		byteOrder = val;
		if(data != null) data.setEndian(val);
	}
	
	public void setVersionFieldShort(){
		if(readOnly) return;
		versionFieldSize = 2;
	}
	
	public void setVersionFieldInt(){
		if(readOnly) return;
		versionFieldSize = 4;
	}
	
	public void setVersion(int val){
		if(readOnly) return;
		version = val;
	}
	
	public void allocateWriteBuffer(int size){
		if(readOnly) return;
		data = new FileBuffer(size, byteOrder);
		propertyMap.clear();
	}
	
	public void clearContents(){
		if(readOnly) return;
		try{data.dispose();}
		catch(Exception ex){ex.printStackTrace();}
		data = null;
		propertyMap.clear();
	}
	
	public boolean addByte(byte value, int key){
		if(readOnly) return false;
		if(propertyMap.containsKey(key)) return false;
		if(data == null) return false;
		int offset = (int)data.getFileSize();
		data.addToFile(value);
		propertyMap.put(key, offset);
		return true;
	}
	
	public boolean addShort(short value, int key){
		if(readOnly) return false;
		if(propertyMap.containsKey(key)) return false;
		if(data == null) return false;
		int offset = (int)data.getFileSize();
		data.addToFile(value);
		propertyMap.put(key, offset);
		return true;
	}
	
	public boolean addShortish(int value, int key){
		if(readOnly) return false;
		if(propertyMap.containsKey(key)) return false;
		if(data == null) return false;
		int offset = (int)data.getFileSize();
		data.add24ToFile(value);
		propertyMap.put(key, offset);
		return true;
	}
	
	public boolean addInt(int value, int key){
		if(readOnly) return false;
		if(propertyMap.containsKey(key)) return false;
		if(data == null) return false;
		int offset = (int)data.getFileSize();
		data.addToFile(value);
		propertyMap.put(key, offset);
		return true;
	}
	
	public boolean addLong(long value, int key){
		if(readOnly) return false;
		if(propertyMap.containsKey(key)) return false;
		if(data == null) return false;
		int offset = (int)data.getFileSize();
		data.addToFile(value);
		propertyMap.put(key, offset);
		return true;
	}
	
	public boolean addFloat(float value, int key){
		if(readOnly) return false;
		if(propertyMap.containsKey(key)) return false;
		if(data == null) return false;
		int offset = (int)data.getFileSize();
		data.addToFile(Float.floatToRawIntBits(value));
		propertyMap.put(key, offset);
		return true;
	}
	
	public boolean addDouble(double value, int key){
		if(readOnly) return false;
		if(propertyMap.containsKey(key)) return false;
		if(data == null) return false;
		int offset = (int)data.getFileSize();
		data.addToFile(Double.doubleToRawLongBits(value));
		propertyMap.put(key, offset);
		return true;
	}
	
	public boolean addString(String value, int key){
		//TODO
		if(readOnly) return false;
		return true;
	}
	
	public boolean addChildStream(MaxisPropertyStream value, int key){
		//TODO
		if(readOnly) return false;
		return true;
	}
	
	/*----- Reading -----*/
	
	public static MaxisPropertyStream openForRead(BufferReference input, boolean byte_order, int verFieldSize){
		MaxisPropertyStream ps = new MaxisPropertyStream();
		ps.byteOrder = byte_order;
		ps.readOnly = true;
		ps.versionFieldSize = verFieldSize;
		input.setByteOrder(byte_order);
		
		//This parser is based on version 2 for TS3. Will update when more variations are known.
		switch(ps.versionFieldSize){
		case 2:
			ps.version = Short.toUnsignedInt(input.nextShort());
			break;
		case 4:
			ps.version = input.nextInt();
			break;
		}
		
		//Copy data into new buffer
		int data_size = input.nextInt();
		ps.data = new FileBuffer(data_size, ps.byteOrder);
		for(int i = 0; i < data_size; i++) ps.data.addToFile(input.nextByte()); //memcpy
		
		//Adjust offsets in map to reflect offset from data start, not ps start.
		int entry_count = Short.toUnsignedInt(input.nextShort());
		for(int i = 0; i < entry_count; i++){
			int key = input.nextInt();
			int offset = input.nextInt();
			offset -= (ps.versionFieldSize + 4);
			ps.propertyMap.put(key, offset);
		}
		
		return ps;
	}

	/*----- Writing -----*/
	
	public static MaxisPropertyStream openForWrite(int buffer_alloc){
		//TODO
		return null;
	}
	
	public long writeTo(OutputStream output){
		//TODO
		return 0L;
	}
	
}
