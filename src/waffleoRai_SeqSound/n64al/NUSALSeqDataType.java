package waffleoRai_SeqSound.n64al;

import java.util.HashMap;
import java.util.Map;

public enum NUSALSeqDataType {
	
	BINARY("bin",1,-1, -1, 1,NUSALSeqCommands.MML_DATAPARAM_TYPE__HEXUNSIGNED),
	BUFFER("dalloc",1,-1, -1, 8, NUSALSeqCommands.MML_DATAPARAM_TYPE__BUFFER),
	FILTER("filter", 2, 8, 8, 16, NUSALSeqCommands.MML_DATAPARAM_TYPE__DECSIGNED),
	ENVELOPE("envelope", 2, -1, -1, 2, NUSALSeqCommands.MML_DATAPARAM_TYPE__HEXUNSIGNED),
	CALLTABLE("calltbl", 2, -1, 128, 2, NUSALSeqCommands.MML_DATAPARAM_TYPE__HEXUNSIGNED),
	P_TABLE("ptbl", 2, -1, 128, 2, NUSALSeqCommands.MML_DATAPARAM_TYPE__HEXUNSIGNED),
	Q_TABLE("qtbl", 1, -1, 128, 1, NUSALSeqCommands.MML_DATAPARAM_TYPE__DECSIGNED),
	GATE_TABLE("gatetbl", 1, 16, 16, 1, NUSALSeqCommands.MML_DATAPARAM_TYPE__DECUNSIGNED),
	VEL_TABLE("veltbl", 1, 16, 16, 1, NUSALSeqCommands.MML_DATAPARAM_TYPE__DECSIGNED),
	CH_PARAMS("cparam", 1, 8, 8, 1, NUSALSeqCommands.MML_DATAPARAM_TYPE__HEXUNSIGNED),
	;
	
	private String str_mml;
	private int unit_size;
	private int unit_count;
	private int max_units;
	private int param_print_type;
	private int alignment;
	
	private NUSALSeqDataType(String mml, int d_size, int count, int max, int align, int printtype){
		str_mml = mml;
		unit_size = d_size;
		unit_count = count;
		param_print_type = printtype;
		alignment = align;
		max_units = max;
	}
	
	public int getParamPrintType(){return param_print_type;}
	public String getMMLString(){return str_mml;}
	public String toString(){return str_mml;}
	
	public int getUnitSize(){return unit_size;}
	public int getUnitCount(){return unit_count;}
	public int getMaxUnits(){return max_units;}
	public int getTotalSize(){return unit_size * unit_count;}
	public int getAlignment(){return alignment;}
	
	private static Map<String, NUSALSeqDataType> strmap;
	
	public static NUSALSeqDataType readMML(String mml_type){
		if(mml_type == null) return null;
		mml_type = mml_type.toLowerCase();
		if(strmap == null){
			strmap = new HashMap<String, NUSALSeqDataType>();
			for(NUSALSeqDataType dt : NUSALSeqDataType.values()){
				strmap.put(dt.str_mml, dt);
			}
		}
		return strmap.get(mml_type);
	}
	
}
