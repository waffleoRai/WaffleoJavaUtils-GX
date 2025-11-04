package waffleoRai_Containers.maxis;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.FileBuffer;

//Sims3.SimIFace.PropertyStreamWriter

public class MaxisPropertyStream {
	
	/*----- Constants -----*/
	
	public static final int KEYXOR_BOOL = 0x68FE5F59;
	public static final int KEYXOR_S16 = 0x021560C5;
	public static final int KEYXOR_S32 = 0x0415642B;
	public static final int KEYXOR_S64 = 0x071568E6;
	public static final int KEYXOR_U16 = 0xF328896C;
	public static final int KEYXOR_U32 = 0xF1288606;
	public static final int KEYXOR_U64 = 0xEE28814F;
	public static final int KEYXOR_F32 = 0x4EDCD7A9;
	public static final int KEYXOR_U8 = 0x6236014F;
	public static final int KEYXOR_STRING = 0x15196597;
	public static final int KEYXOR_CHILD = 0xFFF75A95;

	public static final int KEYXOR_ARRAY = 0x555CCDF4;
	
	/*----- Instance Variables -----*/
	
	private boolean readOnly = false;
	
	private boolean byteOrder = false;
	private int versionFieldSize = 2;
	
	private int version = 2;
	//private FileBuffer data;
	//private Map<Integer, Integer> propertyMap;
	
	private List<PropertyNode> propertyList; //To preserve order
	private Map<Integer, FileBuffer> propertyData;
	
	/*----- Classes -----*/
	
	private static class PropertyNode{
		public int propId;
		public FileBuffer data;
		
		public PropertyNode(int key, FileBuffer val) {
			propId = key;
			data = val;
		}
	}
	
	/*----- Init -----*/
	
	private MaxisPropertyStream(){
		//propertyMap = new TreeMap<Integer, Integer>();
		propertyData = new HashMap<Integer, FileBuffer>();
		propertyList = new LinkedList<PropertyNode>();
	}
	
	/*----- Getters -----*/
	
	public boolean isReadOnly(){return readOnly;}
	public boolean getByteOrder(){return byteOrder;}
	public int getVersionFieldSize(){return versionFieldSize;}
	public int getVersion(){return version;}
	
	public boolean hasField(int key){
		//return propertyMap.containsKey(key);
		return propertyData.containsKey(key);
	}
	
	public boolean getFieldAsBool(int key) {
		key ^= KEYXOR_BOOL;
		FileBuffer dat = propertyData.get(key);
		if(dat == null) return false;
		if(dat.isEmpty()) return false;
		return dat.getByte(0L) != 0;
	}
	
	public boolean[] getFieldAsBoolArray(int key) {
		key ^= KEYXOR_BOOL ^ KEYXOR_ARRAY;
		FileBuffer dat = propertyData.get(key);
		if(dat == null) return null;
		if(dat.isEmpty()) return null;
		int ecount = (int)dat.getFileSize();
		dat.setCurrentPosition(0L);
		boolean[] arr = new boolean[ecount];
		for(int i = 0; i < ecount; i++) {
			arr[i] = (dat.nextByte() != 0);
		}
		return arr;
	}
	
	public byte getFieldAsByte(int key){
		key ^= KEYXOR_U8;
		FileBuffer dat = propertyData.get(key);
		if(dat == null) return 0;
		if(dat.isEmpty()) return 0;
		return dat.getByte(0L);
	}
	
	public byte[] getFieldAsByteArray(int key) {
		key ^= KEYXOR_U8 ^ KEYXOR_ARRAY;
		FileBuffer dat = propertyData.get(key);
		if(dat == null) return null;
		if(dat.isEmpty()) return null;
		int ecount = (int)dat.getFileSize();
		dat.setCurrentPosition(0L);
		byte[] arr = new byte[ecount];
		for(int i = 0; i < ecount; i++) {
			arr[i] = dat.nextByte();
		}
		return arr;
	}
	
	public short getFieldAsShort(int key, boolean signed){
		if(signed) key ^= KEYXOR_S16;
		else key ^= KEYXOR_U16;
		FileBuffer dat = propertyData.get(key);
		if(dat == null) return 0;
		if(dat.isEmpty()) return 0;
		return dat.shortFromFile(0L);
	}
	
	public short[] getFieldAsShortArray(int key, boolean signed) {
		if(signed) key ^= KEYXOR_S16 ^ KEYXOR_ARRAY;
		else key ^= KEYXOR_U16 ^ KEYXOR_ARRAY;
		FileBuffer dat = propertyData.get(key);
		if(dat == null) return null;
		if(dat.isEmpty()) return null;
		int ecount = (int)dat.getFileSize() >>> 1;
		dat.setCurrentPosition(0L);
		short[] arr = new short[ecount];
		for(int i = 0; i < ecount; i++) {
			arr[i] = dat.nextShort();
		}
		return arr;
	}
	
	public int getFieldAsShortish(int key){
		FileBuffer dat = propertyData.get(key);
		if(dat == null) return 0;
		if(dat.isEmpty()) return 0;
		return dat.shortishFromFile(0L);
	}
	
	public int getFieldAsInt(int key, boolean signed){
		if(signed) key ^= KEYXOR_S32;
		else key ^= KEYXOR_U32;
		FileBuffer dat = propertyData.get(key);
		if(dat == null) return 0;
		if(dat.isEmpty()) return 0;
		return dat.intFromFile(0L);
	}
	
	public int[] getFieldAsIntArray(int key, boolean signed) {
		if(signed) key ^= KEYXOR_S32 ^ KEYXOR_ARRAY;
		else key ^= KEYXOR_U32 ^ KEYXOR_ARRAY;
		FileBuffer dat = propertyData.get(key);
		if(dat == null) return null;
		if(dat.isEmpty()) return null;
		int ecount = (int)dat.getFileSize() >>> 2;
		dat.setCurrentPosition(0L);
		int[] arr = new int[ecount];
		for(int i = 0; i < ecount; i++) {
			arr[i] = dat.nextInt();
		}
		return arr;
	}
	
	public long getFieldAsLong(int key, boolean signed){
		if(signed) key ^= KEYXOR_S64;
		else key ^= KEYXOR_U64;
		FileBuffer dat = propertyData.get(key);
		if(dat == null) return 0L;
		if(dat.isEmpty()) return 0L;
		return dat.longFromFile(0L);
	}
	
	public long[] getFieldAsLongArray(int key, boolean signed) {
		if(signed) key ^= KEYXOR_S64 ^ KEYXOR_ARRAY;
		else key ^= KEYXOR_U64 ^ KEYXOR_ARRAY;
		FileBuffer dat = propertyData.get(key);
		if(dat == null) return null;
		if(dat.isEmpty()) return null;
		int ecount = (int)dat.getFileSize() >>> 3;
		dat.setCurrentPosition(0L);
		long[] arr = new long[ecount];
		for(int i = 0; i < ecount; i++) {
			arr[i] = dat.nextLong();
		}
		return arr;
	}
	
	public float getFieldAsFloat(int key){
		key ^= KEYXOR_F32;
		FileBuffer dat = propertyData.get(key);
		if(dat == null) return 0;
		if(dat.isEmpty()) return 0;
		return Float.intBitsToFloat(dat.intFromFile(0L));
	}
	
	public float[] getFieldAsFloatArray(int key) {
		key ^= KEYXOR_F32 ^ KEYXOR_ARRAY;
		FileBuffer dat = propertyData.get(key);
		if(dat == null) return null;
		if(dat.isEmpty()) return null;
		int ecount = (int)dat.getFileSize() >>> 2;
		dat.setCurrentPosition(0L);
		float[] arr = new float[ecount];
		for(int i = 0; i < ecount; i++) {
			arr[i] = Float.intBitsToFloat(dat.nextInt());
		}
		return arr;
	}
	
	public double getFieldAsDouble(int key){
		FileBuffer dat = propertyData.get(key);
		if(dat == null) return 0;
		if(dat.isEmpty()) return 0;
		return Double.longBitsToDouble(dat.longFromFile(0L));
	}
	
	public double[] getFieldAsDoubleArray(int key) {
		FileBuffer dat = propertyData.get(key);
		if(dat == null) return null;
		if(dat.isEmpty()) return null;
		int ecount = (int)dat.getFileSize() >>> 3;
		dat.setCurrentPosition(0L);
		double[] arr = new double[ecount];
		for(int i = 0; i < ecount; i++) {
			arr[i] = Double.longBitsToDouble(dat.nextLong());
		}
		return arr;
	}
	
	public String getFieldAsString(int key){
		key ^= KEYXOR_STRING;
		FileBuffer dat = propertyData.get(key);
		if(dat == null) return null;
		if(dat.isEmpty()) return null;
		return MaxisTypes.readMaxisString(dat.getReferenceAt(0L));
	}
	
	public String[] getFieldAsStringArray(int key){
		key ^= KEYXOR_STRING ^ KEYXOR_ARRAY;
		FileBuffer dat = propertyData.get(key);
		if(dat == null) return null;
		if(dat.isEmpty()) return null;
		BufferReference pos = dat.getReferenceAt(0L);
		List<String> ll = new LinkedList<String>();
		while(pos.hasRemaining()) ll.add(MaxisTypes.readMaxisString(pos));
		if(ll.isEmpty()) return null;
		String[] arr = new String[ll.size()];
		int i = 0;
		for(String s : ll) {
			arr[i++] = s;
		}
		return arr;
	}
	
	public FileBuffer getFieldAsBlob(int key){
		FileBuffer dat = propertyData.get(key);
		return dat;
	}
	
	public MaxisPropertyStream getChildStream(int key){
		key ^= KEYXOR_CHILD;
		FileBuffer dat = propertyData.get(key);
		if(dat == null) return null;
		return openForRead(dat.getReferenceAt(0L), byteOrder, versionFieldSize);
	}
	
	public Set<Integer> getAllFieldKeys(){
		Set<Integer> set = new HashSet<Integer>();
		set.addAll(propertyData.keySet());
		return set;
	}
	
	/*----- Setters -----*/
	
	public void setByteOrder(boolean val){
		if(readOnly) return;
		byteOrder = val;
		//if(data != null) data.setEndian(val);
		for(FileBuffer dat : propertyData.values()) {
			dat.setEndian(val);
		}
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
	
	public void clearContents(){
		if(readOnly) return;
		try {
			propertyList.clear();
			for(FileBuffer dat : propertyData.values()) {
				dat.dispose();
			}
			propertyData.clear();
		}
		catch(Exception ex) {ex.printStackTrace();}
	}
	
	public boolean addBool(boolean value, int key){
		if(readOnly) return false;
		key ^= KEYXOR_BOOL;
		FileBuffer buff = new FileBuffer(1, byteOrder);
		byte bb = value?(byte)1:(byte)0;
		buff.addToFile(bb);
		propertyData.put(key, buff);
		propertyList.add(new PropertyNode(key, buff));
		return true;
	}
	
	public boolean addBoolArray(boolean[] value, int key) {
		if(readOnly) return false;
		if(value == null) return false;
		key ^= KEYXOR_BOOL ^ KEYXOR_ARRAY;
		int datSize = value.length;
		FileBuffer dat = new FileBuffer(datSize, byteOrder);
		for(int i = 0; i < value.length; i++) {
			int bb = value[i]?1:0;
			dat.addToFile(bb);
		}
		return true;
	}
	
	public boolean addByte(byte value, int key){
		if(readOnly) return false;
		key ^= KEYXOR_U8;
		FileBuffer buff = new FileBuffer(1, byteOrder);
		buff.addToFile(value);
		propertyData.put(key, buff);
		propertyList.add(new PropertyNode(key, buff));
		return true;
	}
	
	public boolean addByteArray(byte[] value, int key) {
		if(readOnly) return false;
		if(value == null) return false;
		key ^= KEYXOR_U8 ^ KEYXOR_ARRAY;
		int datSize = value.length;
		FileBuffer dat = new FileBuffer(datSize, byteOrder);
		for(int i = 0; i < value.length; i++) {
			dat.addToFile(value[i]);
		}
		return true;
	}
	
	public boolean addShort(short value, int key, boolean signed){
		if(readOnly) return false;
		if(signed) key ^= KEYXOR_S16;
		else key ^= KEYXOR_U16;
		FileBuffer buff = new FileBuffer(2, byteOrder);
		buff.addToFile(value);
		propertyData.put(key, buff);
		propertyList.add(new PropertyNode(key, buff));
		return true;
	}
	
	public boolean addShortArray(short[] value, int key, boolean signed) {
		if(readOnly) return false;
		if(value == null) return false;
		if(signed) key ^= KEYXOR_S16 ^ KEYXOR_ARRAY;
		else key ^= KEYXOR_U16 ^ KEYXOR_ARRAY;
		int datSize = value.length << 1;
		FileBuffer dat = new FileBuffer(datSize, byteOrder);
		for(int i = 0; i < value.length; i++) {
			dat.addToFile(value[i]);
		}
		return true;
	}
	
	public boolean addShortish(int value, int key){
		if(readOnly) return false;
		FileBuffer buff = new FileBuffer(3, byteOrder);
		buff.add24ToFile(value);
		propertyData.put(key, buff);
		propertyList.add(new PropertyNode(key, buff));
		return true;
	}
	
	public boolean addInt(int value, int key, boolean signed){
		if(readOnly) return false;
		if(signed) key ^= KEYXOR_S32;
		else key ^= KEYXOR_U32;
		FileBuffer buff = new FileBuffer(4, byteOrder);
		buff.addToFile(value);
		propertyData.put(key, buff);
		propertyList.add(new PropertyNode(key, buff));
		return true;
	}
	
	public boolean addIntArray(int[] value, int key, boolean signed) {
		if(readOnly) return false;
		if(value == null) return false;
		if(signed) key ^= KEYXOR_S32 ^ KEYXOR_ARRAY;
		else key ^= KEYXOR_U32 ^ KEYXOR_ARRAY;
		int datSize = value.length << 2;
		FileBuffer dat = new FileBuffer(datSize, byteOrder);
		for(int i = 0; i < value.length; i++) {
			dat.addToFile(value[i]);
		}
		return true;
	}
	
	public boolean addLong(long value, int key, boolean signed){
		if(readOnly) return false;
		if(signed) key ^= KEYXOR_S64;
		else key ^= KEYXOR_U64;
		FileBuffer buff = new FileBuffer(8, byteOrder);
		buff.addToFile(value);
		propertyData.put(key, buff);
		propertyList.add(new PropertyNode(key, buff));
		return true;
	}
	
	public boolean addLongArray(long[] value, int key, boolean signed) {
		if(readOnly) return false;
		if(value == null) return false;
		if(signed) key ^= KEYXOR_S64 ^ KEYXOR_ARRAY;
		else key ^= KEYXOR_U64 ^ KEYXOR_ARRAY;
		int datSize = value.length << 3;
		FileBuffer dat = new FileBuffer(datSize, byteOrder);
		for(int i = 0; i < value.length; i++) {
			dat.addToFile(value[i]);
		}
		return true;
	}
	
	public boolean addFloat(float value, int key){
		if(readOnly) return false;
		key ^= KEYXOR_F32;
		FileBuffer buff = new FileBuffer(4, byteOrder);
		buff.addToFile(Float.floatToRawIntBits(value));
		propertyData.put(key, buff);
		propertyList.add(new PropertyNode(key, buff));
		return true;
	}
	
	public boolean addFloatArray(float[] value, int key) {
		if(readOnly) return false;
		if(value == null) return false;
		key ^= KEYXOR_F32 ^ KEYXOR_ARRAY;
		int datSize = value.length << 2;
		FileBuffer dat = new FileBuffer(datSize, byteOrder);
		for(int i = 0; i < value.length; i++) {
			dat.addToFile(Float.floatToRawIntBits(value[i]));
		}
		return true;
	}
	
	public boolean addDouble(double value, int key){
		if(readOnly) return false;
		FileBuffer buff = new FileBuffer(8, byteOrder);
		buff.addToFile(Double.doubleToRawLongBits(value));
		propertyData.put(key, buff);
		propertyList.add(new PropertyNode(key, buff));
		return true;
	}
	
	public boolean addDoubleArray(double[] value, int key) {
		if(readOnly) return false;
		if(value == null) return false;
		int datSize = value.length << 3;
		FileBuffer dat = new FileBuffer(datSize, byteOrder);
		for(int i = 0; i < value.length; i++) {
			dat.addToFile(Double.doubleToRawLongBits(value[i]));
		}
		return true;
	}
	
	public boolean addString(String value, int key){
		if(readOnly) return false;
		if(value == null) return false;
		key ^= KEYXOR_STRING;
		FileBuffer buff = MaxisTypes.serializeMaxisString(value, byteOrder);
		propertyData.put(key, buff);
		propertyList.add(new PropertyNode(key, buff));
		return true;
	}
	
	public boolean addStringArray(String[] value, int key) {
		if(readOnly) return false;
		if(value == null) return false;
		key ^= KEYXOR_STRING ^ KEYXOR_ARRAY;
		int datSize = value.length << 2;
		for(int i = 0; i < value.length; i++) {
			if(value[i] != null) datSize += value.length << 1;
		}
		FileBuffer dat = new FileBuffer(datSize, byteOrder);
		for(int i = 0; i < value.length; i++) {
			MaxisTypes.serializeMaxisStringTo(value[i], dat);
		}
		return true;
	}
	
	public boolean addBlob(byte[] value, int key){
		if(readOnly) return false;
		if(value == null) return false;
		FileBuffer buff = FileBuffer.wrap(value);
		buff.setEndian(byteOrder);
		propertyData.put(key, buff);
		propertyList.add(new PropertyNode(key, buff));
		return true;
	}
	
	public boolean addBlob(FileBuffer value, int key){
		if(readOnly) return false;
		if(value == null) return false;
		FileBuffer buff;
		try {
			buff = value.createCopy(0, value.getFileSize());
			buff.setEndian(byteOrder);
			propertyData.put(key, buff);
			propertyList.add(new PropertyNode(key, buff));
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	public boolean addChildStream(MaxisPropertyStream value, int key){
		if(readOnly) return false;
		if(value == null) return false;
		key ^= KEYXOR_CHILD;
		FileBuffer buff = value.serializeMe();
		propertyData.put(key, buff);
		propertyList.add(new PropertyNode(key, buff));
		return true;
	}
	
	/*----- Reading -----*/
	
	public static MaxisPropertyStream openForRead(BufferReference input, boolean byte_order, int verFieldSize){
		MaxisPropertyStream ps = new MaxisPropertyStream();
		ps.byteOrder = byte_order;
		ps.readOnly = true;
		ps.versionFieldSize = verFieldSize;
		input.setByteOrder(byte_order);
		int headerSize = 4 + ps.versionFieldSize;
		
		//This parser is based on version 2 for TS3. Will update when more variations are known.
		switch(ps.versionFieldSize){
		case 2:
			ps.version = Short.toUnsignedInt(input.nextShort());
			break;
		case 4:
			ps.version = input.nextInt();
			break;
		}
		
		//Read property table
		int data_size = input.nextInt() - headerSize; //This INCLUDES header.
		FileBuffer backingBuffer = input.getBuffer();
		long dataPos = input.getBufferPosition();
		FileBuffer dataBuffer = backingBuffer.createReadOnlyCopy(dataPos, dataPos + data_size);
		
		input.add(data_size);
		int entry_count = Short.toUnsignedInt(input.nextShort());
		//int entry_count = input.nextInt();
		
		List<Integer> keyOrder = new ArrayList<Integer>(entry_count+1);
		Map<Integer, Integer> off2KeyMap = new HashMap<Integer, Integer>();
		List<Integer> offsets = new ArrayList<Integer>(entry_count+1);
		
		//Adjust offsets in map to reflect offset from data start, not ps start.
		for(int i = 0; i < entry_count; i++){
			int key = input.nextInt();
			int offset = input.nextInt();
			offset -= (ps.versionFieldSize + 4);
			
			keyOrder.add(key);
			offsets.add(offset);
			off2KeyMap.put(offset, key);
		}
		
		//Split up data
		Collections.sort(offsets);
		for(int i = 0; i < entry_count; i++) {
			int offSt = offsets.get(i);
			int offEd = data_size;
			if(i < (entry_count - 1)) {
				offEd = offsets.get(i+1);
			}
			
			int key = off2KeyMap.get(offSt);
			
			try{
				FileBuffer field = dataBuffer.createCopy(offSt, offEd);
				field.setEndian(ps.byteOrder);
				ps.propertyData.put(key, field);
			}
			catch(Exception ex) {ex.printStackTrace();}
		}
		try{dataBuffer.dispose();}
		catch(Exception ex) {ex.printStackTrace();}
		
		//List order
		for(Integer key : keyOrder) {
			FileBuffer field = ps.propertyData.get(key);
			ps.propertyList.add(new PropertyNode(key, field));
		}
		
		return ps;
	}

	/*----- Writing -----*/
	
	public static MaxisPropertyStream openForWrite(boolean byte_order){
		return openForWrite(byte_order, 2);
	}
	
	public static MaxisPropertyStream openForWrite(boolean byte_order, int verFieldSize){
		MaxisPropertyStream ps = new MaxisPropertyStream();
		ps.byteOrder = byte_order;
		ps.versionFieldSize = verFieldSize;
		return ps;
	}
	
	public int calculateSerialSize() {
		int size = 4 + versionFieldSize + 2; //Data size, version, entry count
		for(PropertyNode node : propertyList) {
			if(node.data != null) {
				size += (int)node.data.getFileSize();
			}
			size += 8; //Table entry
		}
		return size;
	}
	
	public int calculateDataSize() {
		int size = 0;
		for(PropertyNode node : propertyList) {
			if(node.data != null) {
				size += (int)node.data.getFileSize();
			}
		}
		return size;
	}
	
	public FileBuffer serializeMe() {
		FileBuffer buff = new FileBuffer(calculateSerialSize() + 8, byteOrder);
		serializeTo(buff);
		return buff;
	}
	
	public long serializeTo(FileBuffer target) {
		int dataSize = calculateDataSize();
		int entryCount = propertyList.size();
		long stPos = target.getFileSize();
		
		target.addToFile(dataSize);
		if(versionFieldSize == 2) target.addToFile((short)version);
		else if(versionFieldSize == 4) target.addToFile(version);
		
		//Data
		for(PropertyNode node : propertyList) {
			if(node.data != null) {
				int datsize = (int)node.data.getFileSize();
				for(int i = 0; i < datsize; i++) target.addToFile(node.data.getByte(i));
			}
			else target.addToFile(0);
		}
		
		//Property table
		target.addToFile((short)entryCount);
		int offset = 4 + versionFieldSize;
		for(PropertyNode node : propertyList) {
			target.addToFile(node.propId);
			target.addToFile(offset);
			if(node.data != null) {
				offset += (int)node.data.getFileSize();
			}
			else offset += 4;
		}
		
		return target.getFileSize() - stPos;
	}
	
	public long writeTo(OutputStream output) throws IOException{
		if(output == null) return 0L;
		FileBuffer buff = serializeMe();
		if(buff == null) return 0L;
		buff.writeToStream(output);
		return buff.getFileSize();
	}
	
}
