package waffleoRai_Containers.nintendo.sar.rev;

import java.util.HashMap;
import java.util.Map;

public enum SoundType {

		SEQ(1),
		STRM(2),
		WAVE(3);
		
		private int n;
		
		private SoundType(int value){n = value;}
		public int getValue(){return n;}
		
		private static Map<Integer, SoundType> intMap;
		public static SoundType getSoundType(int value)
		{
			if(intMap == null)
			{
				intMap = new HashMap<Integer, SoundType>();
				for(SoundType e : SoundType.values()) intMap.put(e.getValue(), e);
			}
			return intMap.get(value);
		}
		
}
