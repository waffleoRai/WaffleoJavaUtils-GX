package waffleoRai_Containers.maxis.ts3.savefmts.sim.traits;

import java.util.HashMap;
import java.util.Map;

public class TraitReinforcementAxis {
//Game Script: Sims3.Gameplay.ActorSystems.TraitReinforcementAxis
//Enum type: u8

	public static final byte Undefined = (byte)0x00;
	public static final byte Loyal = (byte)0x01;
	public static final byte Hunter = (byte)0x02;
	public static final byte Playful = (byte)0x03;
	public static final byte Adventerous_Skittish = (byte)0x04;
	public static final byte Aggressive_Friendly = (byte)0x05;
	public static final byte Hyper_Lazy = (byte)0x06;
	public static final byte Destructive_NonDestructive = (byte)0x07;
	public static final byte Piggy_Neat = (byte)0x08;
	public static final byte LikesSwimming_Hydrophobic = (byte)0x09;
	public static final byte Noisy_Quiet = (byte)0x0A;
	public static final byte Ornery_Gentle = (byte)0x0B;
	public static final byte Agile_HatesJumping = (byte)0x0C;
	public static final byte Nervous_Brave = (byte)0x0D;
	public static final byte Fast_Lazy = (byte)0x0E;
	public static final byte Gentle_Mean = (byte)0x0F;

	public static final byte[] ALL_VALUES = {
		(byte)0x00, (byte)0x01, (byte)0x02, (byte)0x03, (byte)0x04, (byte)0x05, (byte)0x06, (byte)0x07, 
		(byte)0x08, (byte)0x09, (byte)0x0A, (byte)0x0B, (byte)0x0C, (byte)0x0D, (byte)0x0E, (byte)0x0F
	};

	public static final String[] ALL_NAMES = {
		"Undefined", "Loyal", "Hunter", "Playful", 
		"Adventerous_Skittish", "Aggressive_Friendly", "Hyper_Lazy", "Destructive_NonDestructive", 
		"Piggy_Neat", "LikesSwimming_Hydrophobic", "Noisy_Quiet", "Ornery_Gentle", 
		"Agile_HatesJumping", "Nervous_Brave", "Fast_Lazy", "Gentle_Mean"
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
