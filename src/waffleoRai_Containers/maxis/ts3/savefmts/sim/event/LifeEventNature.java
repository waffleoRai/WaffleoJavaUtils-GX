/*-----------------------------------------------------
 * Autogenerated Java class from XML definition.
 * Created Wed, 1 Jan 2025 12:41:02 -0600
 *-----------------------------------------------------*/

package waffleoRai_Containers.maxis.ts3.savefmts.sim.event;

import java.util.Map;
import java.util.HashMap;

public class LifeEventNature {
//Game Script: 
//Enum type: s32

	public static final int Neutral = 0x00000000;
	public static final int Positive = 0x00000001;
	public static final int Negative = 0x00000002;

	public static final int[] ALL_VALUES = {
		0x00000000, 0x00000001, 0x00000002
	};

	public static final String[] ALL_NAMES = {
		"Neutral", "Positive", "Negative"
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
