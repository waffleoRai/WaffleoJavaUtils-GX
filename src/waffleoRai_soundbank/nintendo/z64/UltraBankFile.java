package waffleoRai_soundbank.nintendo.z64;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;
import waffleoRai_soundbank.nintendo.z64.Z64BankBlocks.EnvelopeBlock;

public class UltraBankFile {
	
	/*----- Constants -----*/
	
	public static final String UBNK_MAGIC = "UBNK";
	public static final int UBNK_VERSION_MAJOR = 2;
	public static final int UBNK_VERSION_MINOR = 2;
	public static final String UWSD_MAGIC = "UWSD";
	public static final int UWSD_VERSION_MAJOR = 2;
	public static final int UWSD_VERSION_MINOR = 0;
	
	/*----- Instance Variables -----*/
	
	private String magic_main = null;
	private int ver_major = 0;
	private int ver_minor = 0;
	
	private int wsd_id = 0;
	private int meta_flags = 0;
	private EnvelopeBlock[] envs = null;
	
	private Map<String, FileBuffer> chunks;
	
	/*----- Init -----*/
	
	private UltraBankFile(){
		chunks = new HashMap<String, FileBuffer>();
	}
	
	/*----- Read -----*/
	
	public static UltraBankFile open(FileBuffer data) throws UnsupportedFileTypeException{
		if(data == null) return null;
		if(data.getFileSize() < 4L) throw new UnsupportedFileTypeException("UltraBankFile.open || File is too small!");
		
		UltraBankFile ubnk = new UltraBankFile();
		
		ubnk.magic_main = data.getASCII_string(0L, 4);
		if((!ubnk.magic_main.equals(UBNK_MAGIC)) && (!ubnk.magic_main.equals(UWSD_MAGIC))) {
			throw new UnsupportedFileTypeException("UltraBankFile.open || Valid magic number not found.");
		}

		data.setEndian(true);
		data.setCurrentPosition(4L);
		int bom = Short.toUnsignedInt(data.nextShort());
		if(bom == 0xfffe) data.setEndian(false);
		
		ubnk.ver_major = Byte.toUnsignedInt(data.nextByte());
		ubnk.ver_minor = Byte.toUnsignedInt(data.nextByte());
		
		//Skip file size and header size.
		data.skipBytes(6L);
		int chunk_count = data.nextShort();
		
		long cpos = data.getCurrentPosition();
		for(int i = 0; i < chunk_count; i++){
			String cmagic = data.getASCII_string(cpos, 4); cpos += 4;
			int csize = data.intFromFile(cpos); cpos += 4;

			try {
				FileBuffer chunkdata = data.createCopy(cpos, cpos + csize);
				ubnk.chunks.put(cmagic, chunkdata);
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			cpos += csize;
		}
		
		return ubnk;
	}
	
	private boolean readBNK_META(Z64Bank bank){
		FileBuffer data = chunks.get("META");
		if(data == null) return false;
		
		data.setCurrentPosition(0L);
		bank.setUID(data.nextInt());
		bank.setMedium(Byte.toUnsignedInt(data.nextByte()));
		bank.setCachePolicy(Byte.toUnsignedInt(data.nextByte()));
		meta_flags = Byte.toUnsignedInt(data.nextByte());
		
		int warc_count = Byte.toUnsignedInt(data.nextByte());
		if(warc_count >= 1) bank.setPrimaryWaveArcIndex(Byte.toUnsignedInt(data.nextByte()));
		if(warc_count >= 2) bank.setSecondaryWaveArcIndex(Byte.toUnsignedInt(data.nextByte()));
		
		long cpos = data.getCurrentPosition();
		while((cpos & 0x3) != 0) cpos++;
		wsd_id = data.intFromFile(cpos);
		
		return true;
	}
	
	private boolean readENVL(Z64Bank bank){
		FileBuffer data = chunks.get("ENVL");
		if(data == null) return false;
		
		data.setCurrentPosition(0L);
		int ecount = Short.toUnsignedInt(data.nextShort());
		if(ecount < 1){
			envs = null;
			return true;
		}
		
		envs = new EnvelopeBlock[ecount];
		int[] eoffs = new int[ecount];
		for(int i = 0; i < ecount; i++){
			eoffs[i] = Short.toUnsignedInt(data.nextShort()) - 8;
		}
		
		for(int i = 0; i < ecount; i++){
			EnvelopeBlock block = EnvelopeBlock.readFrom(data.getReferenceAt(eoffs[i]));
			block.addr = eoffs[i]; //Just to give it an orderable identifier
			envs[i] = bank.addEnvelope(block);
		}
		
		return true;
	}
	
	private boolean readINST(Z64Bank bank){
		//TODO
		FileBuffer data = chunks.get("INST");
		if(data == null) return false;
		
		
		
		return true;
	}
	
	private boolean readPERC(Z64Bank bank){
		//TODO
		FileBuffer data = chunks.get("PERC");
		if(data == null) return false;
		
		
		
		return true;
	}
	
	private boolean readDATA(Z64Bank bank){
		//TODO
		FileBuffer data = chunks.get("DATA");
		if(data == null) return false;
		
		
		
		return true;
	}
	
	private boolean readLABL(Z64Bank bank){
		//TODO
		FileBuffer data = chunks.get("LABL");
		if(data == null) return false;
		
		
		
		return true;
	}
	
	private boolean readIENM(Z64Bank bank){
		//TODO
		FileBuffer data = chunks.get("IENM");
		if(data == null) return false;
		
		
		
		return true;
	}
	
	private boolean readPENM(Z64Bank bank){
		//TODO
		FileBuffer data = chunks.get("PENM");
		if(data == null) return false;
		
		
		
		return true;
	}
	
	private boolean readENUM(Z64Bank bank){
		//TODO
		FileBuffer data = chunks.get("ENUM");
		if(data == null) return false;
		
		
		
		return true;
	}
	
	private boolean readBNKTo(Z64Bank bank) throws UnsupportedFileTypeException{
		readBNK_META(bank);
		readENVL(bank);
		readINST(bank);
		readPERC(bank);
		readLABL(bank);
		
		if(ver_major >= 2){
			readIENM(bank);
			readPENM(bank);
		}
		
		return true;
	}
	
	private boolean readWSDTo(Z64Bank bank) throws UnsupportedFileTypeException{
		//readWSD_META(bank); //Don't really need?
		if(!readDATA(bank)) return false;
		
		if(ver_major >= 2) readENUM(bank);
		
		return true;
	}
	
	public Z64Bank read() throws UnsupportedFileTypeException{
		Z64Bank bank = new Z64Bank();
		if(!readTo(bank)) return null;
		return bank;
	}
	
	public boolean readTo(Z64Bank bank) throws UnsupportedFileTypeException{
		if(this.isUBNK()) return readBNKTo(bank);
		if(this.isUWSD()) return readWSDTo(bank);
		return false;
	}
	
	public void close(){
		//Dispose of all values and clear map.
		try{
			for(FileBuffer chunk : chunks.values()) chunk.dispose();
			chunks.clear();
		}
		catch(Exception ex){
			ex.printStackTrace();
		}
	}
	
	/*----- Write -----*/
	
	public static void writeUBNK(Z64Bank bank, String path){
		//TODO
	}
	
	public static void writeUBNK(Z64Bank bank, OutputStream output){
		//TODO
	}
	
	public static void writeUWSD(Z64Bank bank, String path){
		//TODO
	}
	
	public static void writeUWSD(Z64Bank bank, OutputStream output){
		//TODO
	}
	
	/*----- Getters -----*/
	
	public boolean isUBNK(){
		if(magic_main == null) return false;
		return magic_main.equals(UBNK_MAGIC);
	}
	
	public boolean isUWSD(){
		if(magic_main == null) return false;
		return magic_main.equals(UWSD_MAGIC);
	}
	
	public int getMajorVersion(){return this.ver_major;}
	public int getMinorVersion(){return this.ver_minor;}
	
	public int getWSDID(){return wsd_id;}
	public boolean waveRefsByUID(){return (meta_flags & 0x1) != 0;}
	
	/*----- Setters -----*/

}
