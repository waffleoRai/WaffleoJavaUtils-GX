/*-----------------------------------------------------
 * Autogenerated Java class from XML definition.
 * Created Wed, 1 Jan 2025 17:27:44 -0600
 *-----------------------------------------------------*/

package waffleoRai_Containers.maxis.ts3.savefmts.social;

import waffleoRai_DataContainers.EnumStringMapper16;

public class CommodityTypes {
//Game Script: 
//Enum type: s16

	public static final short Undefined = (short)0x0000;
	public static final short Neutral = (short)0x0001;
	public static final short Boring = (short)0x0002;
	public static final short Creepy = (short)0x0003;
	public static final short Insulting = (short)0x0004;
	public static final short Friendly = (short)0x0005;
	public static final short Funny = (short)0x0006;
	public static final short Amorous = (short)0x0007;
	public static final short Awkward = (short)0x0008;
	public static final short Steamed = (short)0x0009;

	public static final short[] ALL_VALUES = {
		(short)0x0000, (short)0x0001, (short)0x0002, (short)0x0003, (short)0x0004, (short)0x0005, (short)0x0006, (short)0x0007, 
		(short)0x0008, (short)0x0009
	};

	public static final String[] ALL_NAMES = {
		"Undefined", "Neutral", "Boring", "Creepy", 
		"Insulting", "Friendly", "Funny", "Amorous", 
		"Awkward", "Steamed"
	};

	private static EnumStringMapper16 mapper;

	public static String stringFromValue(short value){
		if(mapper == null){
			mapper = new EnumStringMapper16(ALL_VALUES, ALL_NAMES);
		}
		return mapper.stringFromValue(value);
	}

	public static short valueFromString(String str){
		if(mapper == null){
			mapper = new EnumStringMapper16(ALL_VALUES, ALL_NAMES);
		}
		return mapper.valueFromString(str);
	}

	public static void disposeMaps(){
		if(mapper != null){
			mapper.dispose();
			mapper = null;
		}
	}
}