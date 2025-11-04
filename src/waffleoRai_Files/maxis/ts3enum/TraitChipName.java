package waffleoRai_Files.maxis.ts3enum;

import java.util.HashMap;
import java.util.Map;

public class TraitChipName {
//Game Script: Sims3.Gameplay.ActorSystems.TraitChipName
//Enum type: u64

	public static final long Undefined = 0x00000000L;
	public static final long ArtisticAlgorithms = 0x00000001L;
	public static final long Chef = 0x00000002L;
	public static final long Gardener = 0x00000003L;
	public static final long HandiBot = 0x00000004L;
	public static final long Musician = 0x00000005L;
	public static final long FisherBot = 0x00000006L;
	public static final long AbilityToLearn = 0x00000007L;
	public static final long Cleaner = 0x00000008L;
	public static final long Friendly = 0x00000009L;
	public static final long RoboNanny = 0x0000000AL;
	public static final long Sentience = 0x0000000BL;
	public static final long Professional = 0x0000000CL;
	public static final long HumanEmotion = 0x0000000DL;
	public static final long Evil = 0x0000000EL;
	public static final long SolarPowered = 0x0000000FL;
	public static final long CapacityToLove = 0x00000010L;
	public static final long MoodAdjuster = 0x00000011L;
	public static final long Efficient = 0x00000012L;
	public static final long HoloProjector = 0x00000013L;
	public static final long Humor = 0x00000014L;
	public static final long FearOfHumans = 0x00000015L;
	public static final long MaxTraitChipName = 0x00000016L;

	public static final long[] ALL_VALUES = {
		0x00000000L, 0x00000001L, 0x00000002L, 0x00000003L, 
		0x00000004L, 0x00000005L, 0x00000006L, 0x00000007L, 
		0x00000008L, 0x00000009L, 0x0000000AL, 0x0000000BL, 
		0x0000000CL, 0x0000000DL, 0x0000000EL, 0x0000000FL, 
		0x00000010L, 0x00000011L, 0x00000012L, 0x00000013L, 
		0x00000014L, 0x00000015L, 0x00000016L
	};

	public static final String[] ALL_NAMES = {
		"Undefined", "ArtisticAlgorithms", "Chef", "Gardener", 
		"HandiBot", "Musician", "FisherBot", "AbilityToLearn", 
		"Cleaner", "Friendly", "RoboNanny", "Sentience", 
		"Professional", "HumanEmotion", "Evil", "SolarPowered", 
		"CapacityToLove", "MoodAdjuster", "Efficient", "HoloProjector", 
		"Humor", "FearOfHumans", "MaxTraitChipName"
	};

	private static Map<String, Long> nameMap;
	private static Map<Long, String> valMap;

	public static String stringFromValue(long value){
		if(valMap == null){
			valMap = new HashMap<Long, String>();
			for(int i = 0; i < ALL_VALUES.length; i++){
				valMap.put(ALL_VALUES[i], ALL_NAMES[i]);
			}
		}
		return valMap.get(value);
	}

	public static long valueFromString(String str){
		if(nameMap == null){
			nameMap = new HashMap<String, Long>();
			for(int i = 0; i < ALL_NAMES.length; i++){
				nameMap.put(ALL_NAMES[i], ALL_VALUES[i]);
			}
		}
		Long mappedVal = nameMap.get(str);
		if(mappedVal == null) return -1L;
		return mappedVal;
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
