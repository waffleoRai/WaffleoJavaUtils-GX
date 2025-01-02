/*-----------------------------------------------------
 * Autogenerated Java class from XML definition.
 * Created Wed, 1 Jan 2025 00:03:46 -0600
 *-----------------------------------------------------*/

package waffleoRai_Containers.maxis.ts3.savefmts.sim.dreams;

import java.util.Map;
import java.util.HashMap;

public class DreamInputSubjectType {
//Game Script: 
//Enum type: u8
	
	public static final int IDGROUP_NONE = 0;
	public static final int IDGROUP_ENUM = 1;
	public static final int IDGROUP_STRKEY = 2;
	public static final int IDGROUP_OBJID = 3;
	public static final int IDGROUP_SIMID = 4;

	public static final byte None = (byte)0x00;
	public static final byte Career = (byte)0x01;
	public static final byte Fish = (byte)0x02;
	public static final byte Harvestable = (byte)0x03;
	public static final byte Meal = (byte)0x04;
	public static final byte Object = (byte)0x05;
	public static final byte ObjectCategory = (byte)0x06;
	public static final byte Plantable = (byte)0x07;
	public static final byte Skill = (byte)0x08;
	public static final byte Sim = (byte)0x09;
	public static final byte PhotoSubject = (byte)0x0A;
	public static final byte Pet = (byte)0x0B;
	public static final byte SimOrPet = (byte)0x0C;
	public static final byte Trick = (byte)0x0D;
	public static final byte Song = (byte)0x0E;
	public static final byte JobType = (byte)0x0F;
	public static final byte AlchemyObject = (byte)0x10;
	public static final byte MagicFateBallQuestion = (byte)0x11;
	public static final byte OccultType = (byte)0x12;
	public static final byte GemCutObject = (byte)0x13;
	public static final byte Quality = (byte)0x14;
	public static final byte ImprovedProtestType = (byte)0x15;
	public static final byte Soda = (byte)0x16;
	public static final byte CandyBar = (byte)0x17;
	public static final byte OutOfWorldSim = (byte)0x18;
	public static final byte TraitChip = (byte)0x19;
	public static final byte TimeStatue = (byte)0x1A;
	public static final byte DreamTheme = (byte)0x1B;

	public static final byte[] ALL_VALUES = {
		(byte)0x00, (byte)0x01, (byte)0x02, (byte)0x03, (byte)0x04, (byte)0x05, (byte)0x06, (byte)0x07, 
		(byte)0x08, (byte)0x09, (byte)0x0A, (byte)0x0B, (byte)0x0C, (byte)0x0D, (byte)0x0E, (byte)0x0F, 
		(byte)0x10, (byte)0x11, (byte)0x12, (byte)0x13, (byte)0x14, (byte)0x15, (byte)0x16, (byte)0x17, 
		(byte)0x18, (byte)0x19, (byte)0x1A, (byte)0x1B
	};
	
	public static final int[] ID_TYPES = {
		IDGROUP_NONE, IDGROUP_ENUM, IDGROUP_ENUM, IDGROUP_STRKEY, IDGROUP_STRKEY, IDGROUP_OBJID, IDGROUP_STRKEY, IDGROUP_STRKEY,
		IDGROUP_ENUM, IDGROUP_SIMID, IDGROUP_STRKEY, IDGROUP_SIMID, IDGROUP_SIMID, IDGROUP_ENUM, IDGROUP_ENUM, IDGROUP_ENUM,
		IDGROUP_STRKEY, IDGROUP_ENUM, IDGROUP_ENUM, IDGROUP_STRKEY, IDGROUP_ENUM, IDGROUP_ENUM, IDGROUP_ENUM, IDGROUP_ENUM,
		IDGROUP_SIMID, IDGROUP_ENUM, IDGROUP_ENUM, IDGROUP_STRKEY
	};

	public static final String[] ALL_NAMES = {
		"None", "Career", "Fish", "Harvestable", 
		"Meal", "Object", "ObjectCategory", "Plantable", 
		"Skill", "Sim", "PhotoSubject", "Pet", 
		"SimOrPet", "Trick", "Song", "JobType", 
		"AlchemyObject", "MagicFateBallQuestion", "OccultType", "GemCutObject", 
		"Quality", "ImprovedProtestType", "Soda", "CandyBar", 
		"OutOfWorldSim", "TraitChip", "TimeStatue", "DreamTheme"
	};

	private static Map<String, Byte> nameMap;
	private static Map<Byte, String> valMap;

	public static String stringFromValue(byte value){
		if(valMap == null){
			valMap = new HashMap<Byte, String>();
			for(int i = 0; i < ALL_VALUES.length; i++){
				valMap.put(ALL_VALUES[i], ALL_NAMES[i]);
			}
		}
		return valMap.get(value);
	}

	public static byte valueFromString(String str){
		if(nameMap == null){
			nameMap = new HashMap<String, Byte>();
			for(int i = 0; i < ALL_NAMES.length; i++){
				nameMap.put(ALL_NAMES[i], ALL_VALUES[i]);
			}
		}
		Byte mappedVal = nameMap.get(str);
		if(mappedVal == null) return (byte)-1;
		return mappedVal;
	}

	public static int getIdType(int value) {
		if(value < 0) return -1;
		if(value >= ID_TYPES.length) return -1;
		return ID_TYPES[value];
	}
	
	public static void disposeMaps(){
		if(nameMap != null){
			nameMap.clear();
			nameMap = null;
		}
		if(valMap != null){
			valMap.clear();
			valMap = null;
		}
	}
	
}
