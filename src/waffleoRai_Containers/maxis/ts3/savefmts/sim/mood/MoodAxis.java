/*-----------------------------------------------------
 * Autogenerated Java class from XML definition.
 * Created Tue, 31 Dec 2024 20:54:59 -0600
 *-----------------------------------------------------*/

package waffleoRai_Containers.maxis.ts3.savefmts.sim.mood;

import java.util.Map;
import java.util.HashMap;

public class MoodAxis {
//Game Script: Sims3.Gameplay.ActorSystems.MoodAxis
//Enum type: s32

	public static final int None = 0x00000000;
	public static final int Happy = 0x00000001;
	public static final int Stressed = 0x00000002;
	public static final int Uncomfortable = 0x00000003;
	public static final int Angry = 0x00000004;
	public static final int Fulfilled = 0x00000005;

	public static final int[] ALL_VALUES = {
		0x00000000, 0x00000001, 0x00000002, 0x00000003, 0x00000004, 0x00000005
	};

	public static final String[] ALL_NAMES = {
		"None", "Happy", "Stressed", "Uncomfortable", 
		"Angry", "Fulfilled"
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
