package waffleoRai_Containers.nintendo.nx;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import waffleoRai_Encryption.AES;
import waffleoRai_Encryption.AESXTS;
import waffleoRai_Encryption.FileCryptRecord;
import waffleoRai_Encryption.nintendo.NinCrypto;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileNode;

/*
 * Key file formats...
 * 
 * -- hac_common.bin
 * 	NCA Header Common [0x20]
 * 	KAEKs [0x10 * 11 * 3]
 * 	Title KEKs [0x10 * 11]
 * 
 * -- hac_title.bin
 * 	Binary Key/Val table...
 * 		Key (RightsID) [0x10]
 * 		Value (Key Seed) [0x10]
 */

public class NXCrypt {
	//Reference: hactool

	/* ----- Constants ----- */
	
	public static final String DUMP_KEYSTR_NCAHDR = "header_key";
	public static final String DUMP_KEYSTR_TITLEKEK = "titlekek_";
	public static final String DUMP_KEYSTR_KAK_APP = "key_area_key_application_";
	public static final String DUMP_KEYSTR_KAK_OCEAN = "key_area_key_ocean_";
	public static final String DUMP_KEYSTR_KAK_SYS = "key_area_key_system_";
	
	/* ----- Static Variables ----- */
	
	private static String temp_dir;
	
	/* ----- Instance Variables ----- */
	
	private byte[][] title_KEK;
	private byte[][][] kaek;
	
	private Map<String, byte[]> titlekey_map; //Raw title keys?
	
	/* ----- Construction ----- */
	
	public NXCrypt(){
		titlekey_map = new HashMap<String, byte[]>();
	}
	
	/* ----- Getters ----- */
	
	public static String getTempDir(){
		return temp_dir;
	}
	
	/* ----- Setters ----- */
	
	public static void setTempDir(String dir){
		temp_dir = dir;
	}
	
	/* ----- Load/Save ----- */
	
	public void loadCommonKeys(String path) throws IOException{
		FileBuffer buff = FileBuffer.createBuffer(path, false);
		
		long cpos = 0;
		SwitchNCA.setCommonHeaderKey(buff.getBytes(cpos, cpos+0x10)); cpos += 0x10;
		
		kaek = new byte[3][11][16];
		for(int i = 0; i < 3; i++){
			for(int j = 0; j < 11; j++){
				for(int k = 0; k < 16; k++){
					kaek[i][j][k] = buff.getByte(cpos++);
				}
			}
		}
		
		title_KEK = new byte[11][16];
		for(int i = 0; i < 11; i++){
			for(int j = 0; j < 16; j++){
				title_KEK[i][j] = buff.getByte(cpos++);
			}
		}
		
	}
	
	public void loadTitleKeys(String path) throws IOException{
		FileBuffer buff = FileBuffer.createBuffer(path, false);
		long cpos = 0;
		long fsz = buff.getFileSize();
		
		while(cpos < fsz){
			byte[] key = buff.getBytes(cpos, cpos+0x10); cpos += 0x10;
			byte[] val = buff.getBytes(cpos, cpos+0x10); cpos += 0x10;
			titlekey_map.put(printHash(key), val);
		}
	}
	
	public void saveCommonKeys(String path) throws IOException{
		BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(path));
		byte[] key = SwitchNCA.getCommonHeaderKey();
		if(key == null) key = new byte[32];
		bos.write(key);
		
		for(int i = 0; i < 3; i++){
			for(int j = 0; j < 11; j++){
				bos.write(kaek[i][j]);
			}
		}
		
		for(int i = 0; i < 11; i++){
			bos.write(title_KEK[i]);
		}
		
		bos.close();
	}
	
	public void saveTitleKeys(String path) throws IOException{
		if(titlekey_map == null) return;
		BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(path));
		for(Entry<String, byte[]> e : titlekey_map.entrySet()){
			byte[] k = str2Key(e.getKey());
			bos.write(k);
			bos.write(e.getValue());
		}
		bos.close();
	}
	
	public void importCommonKeys(String prod_keys_path) throws IOException{
		
		//Expected input, prod.keys or some text table
		kaek = new byte[3][11][16];
		title_KEK = new byte[11][16];
		Map<String, byte[]> map = new HashMap<String, byte[]>();
		
		BufferedReader br = new BufferedReader(new FileReader(prod_keys_path));
		String line;
		while((line = br.readLine()) != null){
			if(line.isEmpty()) continue;
			//Split on the = or ,
			String[] fields = null;
			if(line.contains(",")) fields = line.split(",");
			else fields = line.split("=");
			if(fields == null || fields.length != 2) continue;
			
			//Get key and value (and remove whitespace)
			String key = fields[0].trim();
			String val = fields[1].trim();
			map.put(key, str2Key(val));
		}
		br.close();
		
		//Grab the ones from map we know we need...
		byte[] k = map.get(DUMP_KEYSTR_NCAHDR);
		SwitchNCA.setCommonHeaderKey(k);
		
		for(int i = 0; i < 11; i++){
			String hexi = String.format("%02x", i);
			kaek[0][i] = map.get(DUMP_KEYSTR_KAK_APP + hexi);
			kaek[1][i] = map.get(DUMP_KEYSTR_KAK_OCEAN + hexi);
			kaek[2][i] = map.get(DUMP_KEYSTR_KAK_SYS + hexi);
			title_KEK[i] = map.get(DUMP_KEYSTR_TITLEKEK + hexi);
		}
		
	}
	
	public void importTitleKeys(String title_keys_path) throws IOException{
		BufferedReader br = new BufferedReader(new FileReader(title_keys_path));
		String line;
		while((line = br.readLine()) != null){
			if(line.isEmpty()) continue;
			//Split on the = or ,
			String[] fields = null;
			if(line.contains(",")) fields = line.split(",");
			else fields = line.split("=");
			if(fields == null || fields.length != 2) continue;
			
			//Get key and value (and remove whitespace)
			String key = fields[0].trim();
			String val = fields[1].trim();
			titlekey_map.put(key, str2Key(val));
		}
		br.close();
	}
	
	/* ----- Keys ----- */
	
	public byte[] getKeyAreaKey(int crypt_type, int kaek_idx){
		return kaek[kaek_idx][crypt_type];
	}
	
	public byte[] getTitleKek(int idx){
		return title_KEK[idx];
	}
	
	public byte[] getRawTitleKey(byte[] rightsID){
		if(titlekey_map == null) return null;
		if(rightsID == null) return null;
		String str = printHash(rightsID);
		
		return titlekey_map.get(str);
	}
	
	/* ----- Decrypt ----- */
	
	public static byte[] getTweak(long sector){
		byte[] tweak = new byte[16];
		return getTweak(tweak, sector);
	}
	
	public static byte[] getTweak(byte[] tweak, long sector){
		for(int i = 15; i >= 0; i--){
			tweak[i] = (byte)(sector & 0xFF);
			sector = sector >>> 8;
		}
		return tweak;
	}
	
	public static byte[] decrypt_AESXTS_sector(byte[] key256, byte[] input, long sector){
			
		byte[] tweak = getTweak(sector);
		AESXTS aes = new AESXTS(key256);
		byte[] dec = aes.decrypt(tweak, input);
		
		//System.err.println("Key: " + AES.bytes2str(key256));
		//System.err.println("Tweak: " + AES.bytes2str(tweak));
		//System.err.println("CT (First Block): " + AES.bytes2str(Arrays.copyOf(input, 16)));
		//System.err.println("PT (First Block): " + AES.bytes2str(Arrays.copyOf(dec, 16)));
		
		return dec;
	}
	
	public byte[] decrypt_keyArea(int key_idx, int kaek_idx, byte[] dat){
		byte[] keak = getKeyAreaKey(key_idx, kaek_idx);
		AES aes = new AES(keak);
		aes.setECB();
		byte[] dec = aes.decrypt(new byte[16], dat);
		
		return dec;
	}
	
	public byte[] getTitleKey(byte[] rightsid, int key_idx){
		byte[] raw = getRawTitleKey(rightsid);
		if(raw == null) return null;
		byte[] kek = title_KEK[key_idx];
		AES aes = new AES(kek);
		aes.setECB();
		byte[] dec = aes.decrypt(new byte[16], raw);
		
		return dec;
	}
	
	public static FileBuffer decryptData(FileNode node, FileCryptRecord crec, byte[] key) throws IOException{
		
		switch(crec.getCryptType()){
		case NinCrypto.CRYPTTYPE_AESCTR:
			return decryptCTRData(node, crec, key);
		case NinCrypto.CRYPTTYPE_AESXTS:
			return decryptXTSData(node, crec, key);
		}
		
		return null;
	}
	
	private static FileBuffer decryptCTRData(FileNode node, FileCryptRecord crec, byte[] key) throws IOException{

		//Derive CTR
		long myoff = node.getOffset();
		long myrow = myoff & ~0xF;
		long stdiff = myoff - myrow;
		
		long ctrdiff = myrow - crec.getCryptOffset();
		byte[] ctr = adjustCTR(crec.getIV(), ctrdiff);
		
		long size = node.getLength();
		long size_a = (size + stdiff + 1) & ~0xF;
		
		//If it's too large (>512 MB), write to temp dir instead of holding in memory
		FileBuffer rawdec = null;
		if(size_a <= 0x20000000){
			//Do all at once.
			byte[] enc = FileBuffer.createBuffer(node.getSourcePath(), myrow, myrow + size_a, false).getBytes();
			AES aes = new AES(key);
			aes.setCTR();
			rawdec = FileBuffer.wrap(aes.decrypt(ctr, enc));
			rawdec = rawdec.createCopy(stdiff, size + stdiff);
		}
		else{
			//Do in blocks and write to a temp file.
			if(temp_dir == null) temp_dir = FileBuffer.getTempDir();
			String temppath = temp_dir + File.separator + Long.toHexString(myoff) + ".tmp";
			final int blocksize = 0x1000;
			long remain = size_a; //So that whole row is read
			long diff = myoff - myrow;
			long written = 0L;
			AES aes = new AES(key);
			aes.setCTR();
			aes.initDecrypt(ctr);
			
			BufferedInputStream bis = new BufferedInputStream(new FileInputStream(node.getSourcePath()));
			bis.skip(myrow);
			BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(temppath));
			while(remain > 0){
				int bsz = remain >= blocksize?blocksize:(int)remain;
				byte[] enc = new byte[bsz];
				bis.read(enc);
				byte[] dec = aes.decryptBlock(enc, remain <= blocksize);
				
				int woff = 0;
				int wlen = bsz;
				if(written < diff){
					woff = (int)diff;
					wlen -= diff;
				}
				
				//Eh, no point in trimming off the end. It'll be reloaded anyway.
				bos.write(dec, woff, wlen);
				remain -= bsz;
				written += wlen;
			}
			bis.close();
			bos.close();
			
			rawdec = FileBuffer.createBuffer(temppath, 0, size, false);
		}
		
		return rawdec;
	}
	
	private static FileBuffer decryptXTSData(FileNode node, FileCryptRecord crec, byte[] key) throws IOException{
		long myoff = node.getOffset();
		long mysec = myoff & ~0x1FF;
		long stdiff = myoff - mysec;
		
		long secdiff = mysec - crec.getCryptOffset();
		long sector = secdiff >>> 9;
		
		long size = node.getLength();
		long size_a = (size + stdiff + 1) & ~0x1FF;
		
		long edpos = myoff + size;
		long edpos_a = mysec + size_a;
		long eddiff = edpos_a - edpos;
		
		//If it's too large (>512 MB), write to temp dir instead of holding in memory
		FileBuffer rawdec = null;
		if(size_a <= 0x20000000){
			//Do all at once.
			FileBuffer in = FileBuffer.createBuffer(node.getSourcePath(), mysec, mysec + size_a, false);
			FileBuffer out = new FileBuffer((int)size_a, false);
			int scount = (int)(size_a >>> 9);
			for(int s = 0; s < scount; s++){
				int pos = s << 9;
				byte[] enc = in.getBytes(pos, pos + 0x200);
				byte[] dec = decrypt_AESXTS_sector(key, enc, sector++);
				
				int off = 0;
				int len = 0;
				if(s == 0){
					off = (int)stdiff;
					len -= stdiff;
				}
				if(s == scount-1){
					len -= (int)eddiff;
				}
				
				for(int i = 0; i < len; i++)out.addToFile(dec[i + off]);
				rawdec = out;
			}
		}
		else{
			//Do in blocks and write to a temp file.
			if(temp_dir == null) temp_dir = FileBuffer.getTempDir();
			String temppath = temp_dir + File.separator + Long.toHexString(myoff) + ".tmp";

			BufferedInputStream bis = new BufferedInputStream(new FileInputStream(node.getSourcePath()));
			bis.skip(mysec);
			BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(temppath));
			int scount = (int)(size_a >>> 9);
			for(int s = 0; s < scount; s++){
				byte[] enc = new byte[0x200];
				bis.read(enc);
				byte[] dec = decrypt_AESXTS_sector(key, enc, sector++);
				
				int off = 0;
				int len = 0;
				if(s == 0){
					off = (int)stdiff;
					len -= stdiff;
				}
				if(s == scount-1){
					len -= (int)eddiff;
				}
				
				bos.write(dec, off, len);
			}
			bis.close();
			bos.close();
			
			rawdec = FileBuffer.createBuffer(temppath, 0, size, false);
		}
		
		return rawdec;
	}
	
	/* ----- Checksums ----- */
	
	public static byte[] getSHA256(byte[] in){
		if(in == null) return null;
		
		try{
			MessageDigest sha = MessageDigest.getInstance("SHA-256");
			sha.update(in);
			return sha.digest();
		}
		catch(Exception x){
			x.printStackTrace();
			return null;
		}

	}
	
	/* ----- Util ----- */
	
	public static byte[] adjustCTR(byte[] base_ctr, long offset){
		long row = offset >>> 4;
		byte[] add = new byte[16];
		byte[] out = new byte[16];
		
		long shamt = 56;
		for(int i = 0; i < 8; i++){
			add[i+8] = (byte)((row >>> shamt) & 0xFF);
			shamt -= 8;
		}
		
		boolean carry = false;
		for(int i = 15; i >= 0; i--){
			int sum = Byte.toUnsignedInt(add[i]) + Byte.toUnsignedInt(base_ctr[i]);
			if(carry) sum++;
			if(sum > 0xFF) carry = true;
			out[i] = (byte)sum;
		}
		
		return out;
	}
	
	public static byte[] str2Key(String s){
		byte[] arr = new byte[s.length() >>> 1];
		int cpos = 0;
		for(int i = 0; i < arr.length; i++){
			String bstr = s.substring(cpos, cpos+2);
			arr[i] = (byte)Integer.parseInt(bstr, 16);
			cpos+=2;
		}
		
		return arr;
	}
	
	public static String printHash(byte[] hash){
		if(hash == null) return "<NULL>";
		
		int chars = hash.length << 1;
		StringBuilder sb = new StringBuilder(chars+2);
		
		for(int i = 0; i < hash.length; i++) sb.append(String.format("%02x", hash[i]));
		
		return sb.toString();
	}
	
}
