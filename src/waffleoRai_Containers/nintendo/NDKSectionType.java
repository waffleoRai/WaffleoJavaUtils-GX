package waffleoRai_Containers.nintendo;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public enum NDKSectionType {
	
	DATA("DATA"),
	LABL("LABL"),
	INFO("INFO"),
	TABL("TABL"),
	ADPC("ADPC"),
	HEAD("HEAD"),
	SYMB("SYMB"),
	FILE("FILE"),;
	
	//private int cfCode;
	private String magic;
	
	private NDKSectionType(String id)
	{
		//cfCode = code;
		magic = id;
	}
	
	//public int getCFCode(){return cfCode;}
	public String getIdentifier(){return magic;}
	
	
	private static ConcurrentMap<String, NDKSectionType> smap;
	public static NDKSectionType getSectionType(String magic)
	{
		if(smap == null)
		{
			smap = new ConcurrentHashMap<String, NDKSectionType>();
			for(NDKSectionType t : NDKSectionType.values()) smap.put(t.getIdentifier(), t);
		}
		return smap.get(magic);
	}

}
