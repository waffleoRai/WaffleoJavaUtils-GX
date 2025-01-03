/*-----------------------------------------------------
 * Autogenerated Java class from XML definition.
 * Created Wed, 1 Jan 2025 17:45:42 -0600
 *-----------------------------------------------------*/

package waffleoRai_Containers.maxis.ts3.savefmts.occult;

import waffleoRai_DataContainers.EnumStringMapper32;

public class OccultTypes {
//Game Script: 
//Enum type: u32

	public static final int None = 0x00000000;
	public static final int Mummy = 0x00000001;
	public static final int Frankenstein = 0x00000002;
	public static final int Vampire = 0x00000004;
	public static final int ImaginaryFriend = 0x00000008;
	public static final int Unicorn = 0x00000010;
	public static final int Genie = 0x00000020;
	public static final int Werewolf = 0x00000040;
	public static final int Ghost = 0x00000080;
	public static final int Fairy = 0x00000100;
	public static final int Witch = 0x00000200;
	public static final int PlantSim = 0x00000400;
	public static final int Mermaid = 0x00000800;
	public static final int Robot = 0x00001000;
	public static final int TimeTraveler = 0x00002000;

	public static final int[] ALL_VALUES = {
		0x00000000, 0x00000001, 0x00000002, 0x00000004, 0x00000008, 0x00000010, 0x00000020, 0x00000040, 
		0x00000080, 0x00000100, 0x00000200, 0x00000400, 0x00000800, 0x00001000, 0x00002000
	};

	public static final String[] ALL_NAMES = {
		"None", "Mummy", "Frankenstein", "Vampire", 
		"ImaginaryFriend", "Unicorn", "Genie", "Werewolf", 
		"Ghost", "Fairy", "Witch", "PlantSim", 
		"Mermaid", "Robot", "TimeTraveler"
	};

	private static EnumStringMapper32 mapper;

	public static String stringFromValue(int value){
		if(mapper == null){
			mapper = new EnumStringMapper32(ALL_VALUES, ALL_NAMES);
		}
		return mapper.stringFromValueFlags(value);
	}

	public static int valueFromString(String str){
		if(mapper == null){
			mapper = new EnumStringMapper32(ALL_VALUES, ALL_NAMES);
		}
		return mapper.valueFromStringFlags(str);
	}

	public static void disposeMaps(){
		if(mapper != null){
			mapper.dispose();
			mapper = null;
		}
	}
}
