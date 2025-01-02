/*-----------------------------------------------------
 * Autogenerated Java class from XML definition.
 * Created Wed, 1 Jan 2025 13:25:33 -0600
 *-----------------------------------------------------*/

package waffleoRai_Containers.maxis.ts3.savefmts.travel;

import java.util.Map;
import java.util.HashMap;

public class CollectableRelicType {
//Game Script: 
//Enum type: u32

	public static final int ConopicJars = 0x00000000;
	public static final int GoldFigurines = 0x00000001;
	public static final int ZodiacAnimals = 0x00000002;
	public static final int ChineseVases = 0x00000003;
	public static final int DangerousCreatures = 0x00000004;
	public static final int DropaStones = 0x00000005;
	public static final int EgyptianTomb = 0x00000006;
	public static final int ChineseTomb = 0x00000007;
	public static final int FrenchTomb = 0x00000008;

	public static final int[] ALL_VALUES = {
		0x00000000, 0x00000001, 0x00000002, 0x00000003, 0x00000004, 0x00000005, 0x00000006, 0x00000007, 
		0x00000008
	};

	public static final String[] ALL_NAMES = {
		"ConopicJars", "GoldFigurines", "ZodiacAnimals", "ChineseVases", 
		"DangerousCreatures", "DropaStones", "EgyptianTomb", "ChineseTomb", 
		"FrenchTomb"
	};

	private static Map<String, Integer> nameMap;
	private static Map<Integer, String> valMap;

	public static String stringFromValue(int value){
		if(valMap == null){
			valMap = new HashMap<Integer, String>();
			for(int i = 0; i < ALL_VALUES.length; i++){
				valMap.put(ALL_VALUES[i], ALL_NAMES[i]);
			}
		}
		return valMap.get(value);
	}

	public static int valueFromString(String str){
		if(nameMap == null){
			nameMap = new HashMap<String, Integer>();
			for(int i = 0; i < ALL_NAMES.length; i++){
				nameMap.put(ALL_NAMES[i], ALL_VALUES[i]);
			}
		}
		Integer mappedVal = nameMap.get(str);
		if(mappedVal == null) return -1;
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
