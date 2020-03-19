package waffleoRai_Containers.nintendo.sar.rev;

import java.util.HashMap;
import java.util.Map;

public enum PanMode {

	DUAL(0),
	BALANCE(1);
	
	private int n;
	
	private PanMode(int value){n = value;}
	public int getValue(){return n;}
	
	private static Map<Integer, PanMode> intMap;
	public static PanMode getPanMode(int value)
	{
		if(intMap == null)
		{
			intMap = new HashMap<Integer, PanMode>();
			for(PanMode e : PanMode.values()) intMap.put(e.getValue(), e);
		}
		return intMap.get(value);
	}
	
}
