/*-----------------------------------------------------
 * Autogenerated Java class from XML definition.
 * Created Wed, 1 Jan 2025 17:49:10 -0600
 *-----------------------------------------------------*/

package waffleoRai_Containers.maxis.ts3.savefmts.sim;

import waffleoRai_DataContainers.EnumStringMapper32;

public class CASAgeGenderFlags {
//Game Script: Sims3.SimIFace.CAS.CASAgeGenderFlags
//Enum type: u32

	public static final int None = 0x00000000;
	public static final int Baby = 0x00000001;
	public static final int Toddler = 0x00000002;
	public static final int Child = 0x00000004;
	public static final int Teen = 0x00000008;
	public static final int YoungAdult = 0x00000010;
	public static final int Adult = 0x00000020;
	public static final int Elder = 0x00000040;
	public static final int AgeMask = 0x0000007F;
	public static final int Male = 0x00001000;
	public static final int Female = 0x00002000;
	public static final int GenderMask = 0x00003000;
	public static final int Human = 0x00000100;
	public static final int Horse = 0x00000200;
	public static final int Cat = 0x00000300;
	public static final int Dog = 0x00000400;
	public static final int LittleDog = 0x00000500;
	public static final int Deer = 0x00000600;
	public static final int Raccoon = 0x00000700;
	public static final int LargeBird = 0x00000800;
	public static final int SimWalkingDog = 0x00000900;
	public static final int SimWalkingLittleDog = 0x00000A00;
	public static final int SimLeadingHorse = 0x00000B00;
	public static final int Paddleboat = 0x00000C00;
	public static final int WaterScooter = 0x00000D00;
	public static final int Speedboat = 0x00000E00;
	public static final int Rowboat = 0x00000F00;
	public static final int HouseboatSmall = 0x00004100;
	public static final int HouseboatMedium = 0x00004200;
	public static final int HouseboatLarge = 0x00004300;
	public static final int Shark = 0x00004400;
	public static final int Sailboat = 0x00004500;
	public static final int WindsurfBoard = 0x00004600;
	public static final int SpeciesMask = 0x0000CF00;
	public static final int LeftHanded = 0x00100000;
	public static final int RightHanded = 0x00200000;
	public static final int HandednessMask = 0x00300000;

	public static final int[] ALL_VALUES = {
		0x00000000, 0x00000001, 0x00000002, 0x00000004, 0x00000008, 0x00000010, 0x00000020, 0x00000040, 
		0x0000007F, 0x00001000, 0x00002000, 0x00003000, 0x00000100, 0x00000200, 0x00000300, 0x00000400, 
		0x00000500, 0x00000600, 0x00000700, 0x00000800, 0x00000900, 0x00000A00, 0x00000B00, 0x00000C00, 
		0x00000D00, 0x00000E00, 0x00000F00, 0x00004100, 0x00004200, 0x00004300, 0x00004400, 0x00004500, 
		0x00004600, 0x0000CF00, 0x00100000, 0x00200000, 0x00300000
	};

	public static final String[] ALL_NAMES = {
		"None", "Baby", "Toddler", "Child", 
		"Teen", "YoungAdult", "Adult", "Elder", 
		"AgeMask", "Male", "Female", "GenderMask", 
		"Human", "Horse", "Cat", "Dog", 
		"LittleDog", "Deer", "Raccoon", "LargeBird", 
		"SimWalkingDog", "SimWalkingLittleDog", "SimLeadingHorse", "Paddleboat", 
		"WaterScooter", "Speedboat", "Rowboat", "HouseboatSmall", 
		"HouseboatMedium", "HouseboatLarge", "Shark", "Sailboat", 
		"WindsurfBoard", "SpeciesMask", "LeftHanded", "RightHanded", 
		"HandednessMask"
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
