package waffleoRai_Containers.nintendo.nus;

import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileUtils;

public enum NUSCICType {
	
	/*Using 
	 * https://github.com/N64RET/N64LoaderCollection/blob/master/src/main/java/n64/N64Cic.java
	for reference*/

	CIC_UNKNOWN(null),
	CIC_6101("900B4A5B68EDB71F4C7ED52ACD814FC5"),
	CIC_6102("E24DD796B2FA16511521139D28C8356B"),
	CIC_6103("319038097346E12C26C3C21B56F86F23"),
	CIC_6105("FF22A296E55D34AB0A077DC2BA5F5796"),
	CIC_6106("6460387749AC0BD925AA5430BC7864FE"),
	CIC_6101_MOD("955894C2E40A698BF98A67B78A4E28FA");
	
	private String md5_str;
	
	private NUSCICType(String md5sum_string){
		md5_str = md5sum_string;
	}
	
	public static NUSCICType determineCICType(FileBuffer rom_z64){
		if(rom_z64 == null) return null;
		
		byte[] md5sum = FileUtils.getMD5Sum(rom_z64.getBytes(0x40, 0x1000));
		if(md5sum == null) return CIC_UNKNOWN;
		String md5str = FileUtils.bytes2str(md5sum).toUpperCase();
		
		NUSCICType[] vals = NUSCICType.values();
		for(NUSCICType type : vals){
			if(md5str.equals(type.md5_str)) return type;
		}
		
		return CIC_UNKNOWN;
	}
	
}
