package waffleoRai_Containers.maxis;

import java.util.List;
import java.util.Map;

import waffleoRai_Files.tree.FileNode;
import waffleoRai_Utils.FileBuffer;

public class MaxisDBPFPackage {
	
	/*
	 * Needs to handle...
	 * - Regular DBPFs
	 * - DBPPs
	 * - Deltas
	 */
	
	/*----- Constants -----*/
	
	public static final String MAGIC_DBPF = "DBPF";
	public static final String MAGIC_DBPP = "DBPP";
	
	/*----- Instance Variables -----*/
	
	private Map<MaxisResKey, MaxisResourceEntry> index;
	
	/*----- Init -----*/
	
	/*----- Getters -----*/
	
	/*----- Setters -----*/
	
	/*----- Read -----*/
	
	private static int findDBPPIndex(FileBuffer data){
		//TODO
		//"Bruteforce" method. Just looks for streaks of 0x1ffff and 0x10000 spaced evenly apart.
		
		return -1;
	}
	
	private static int readIndex(FileBuffer data, int entry_count){
		//TODO
		return 0;
	}
	
	public static MaxisDBPFPackage read(FileBuffer data){
		//TODO
		return null;
	}
	
	public boolean loadDelta(FileBuffer delta_data){
		//TODO
		return false;
	}
	
	/*----- Write -----*/
	
	public boolean writeTo(String outpath){
		//TODO
		return false;
	}
	
	/*----- Debug -----*/
	

}
