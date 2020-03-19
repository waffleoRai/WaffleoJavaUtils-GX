package waffleoRai_Containers.nintendo.sar.rev;

import java.util.HashMap;
import java.util.Map;

public enum PanCurve {
	
	SQRT(0),
	SQRT_0DB(1),
	SQRT_0DB_CLAMP(2),
	SINCOS(3),
	SINCOS_0DB(4),
	SINCOS_0DB_CLAMP(5),
	LINEAR(6),
	LINEAR_0DB(7),
	LINEAR_0DB_CLAMP(8),
	;
	
	private int n;
	
	private PanCurve(int value){n = value;}
	public int getValue(){return n;}
	
	private static Map<Integer, PanCurve> intMap;
	public static PanCurve getPanCurve(int value)
	{
		if(intMap == null)
		{
			intMap = new HashMap<Integer, PanCurve>();
			for(PanCurve e : PanCurve.values()) intMap.put(e.getValue(), e);
		}
		return intMap.get(value);
	}

}
