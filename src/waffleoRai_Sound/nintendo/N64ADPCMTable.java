package waffleoRai_Sound.nintendo;

import waffleoRai_Sound.ADPCMTable;
import waffleoRai_Utils.BufferReference;

public class N64ADPCMTable extends ADPCMTable{

	private int[][][] coeff_table;
	
	public N64ADPCMTable(int order, int pcount) {
		super(order);
		coeff_table = new int[pcount][order][8];
	}
	
	public static N64ADPCMTable readTable(BufferReference ref){
		//Get the order and pred count
		int o = ref.getInt(); ref.add(4);
		int pcount = ref.getInt(); ref.add(4);
		
		N64ADPCMTable tbl = new N64ADPCMTable(o, pcount);
		for(int p = 0; p < pcount; p++){
			for(o = 0; o < tbl.order; o++){
				for(int i = 0; i < 8; i++){
					tbl.coeff_table[p][o][i] = (int)ref.getShort(); ref.add(2);
				}
			}
		}
		
		return tbl;
	}
	
	public static N64ADPCMTable fromRaw(int order, int pcount, short[] table){
		int c = 0;
		N64ADPCMTable tbl = new N64ADPCMTable(order, pcount);
		for(int p = 0; p < pcount; p++){
			for(int o = 0; o < order; o++){
				for(int i = 0; i < 8; i++){
					tbl.coeff_table[p][o][i] = (int)table[c++];
				}
			}
		}
		return tbl;
	}
	
	public static N64ADPCMTable fromRaw(int order, int pcount, short[][] table){
		int c = 0;
		N64ADPCMTable tbl = new N64ADPCMTable(order, pcount);
		for(int p = 0; p < pcount; p++){
			c = 0;
			for(int o = 0; o < order; o++){
				for(int i = 0; i < 8; i++){
					tbl.coeff_table[p][o][i] = (int)table[p][c++];
				}
			}
		}
		return tbl;
	}

	public int getPredictorCount(){
		if(coeff_table == null) return 0;
		return coeff_table.length;
	}
	
	public int getOrder(){
		if(coeff_table == null) return 0;
		return coeff_table[0].length;
	}
	
	public int getCoefficient(int idx_1d) {
		int i = idx_1d & 0x7;
		idx_1d >>>= 3;
		int o = idx_1d % order;
		int p = idx_1d / order;
		
		return coeff_table[p][o][i];
	}
	
	public int getCoefficient(int p, int o, int i) {
		return coeff_table[p][o][i];
	}

}
